package dev.awaken.hearth;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.WardHooks;
import dev.awaken.hearth.net.HomeNet;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The carried flame (SPEC §28.2, PLAN 4.2-4.3): the Hearth Ember from the Wayfarers' fire, carried until you find your
 * ground, set down as a Tier 0 Hearthfire (radius 24: respawn, rest, calm land; no town, no Spire, no requests), lifted
 * again while no room stands, and raised to the Anchor in place by a Lord's Heart. One Ember per player, kept through
 * death; a lost one comes back from the camp. Server side.
 */
public final class Embers {
    private Embers() {}

    /** No Ember may take this close to a gate: the ground is restless there. */
    public static final int GATE_CLEARANCE = 64;
    /** Players this close to a fire that takes see and hear the moment. */
    static final int MOMENT_RANGE = 96;
    /** Ticks after the coal takes before the YOUR GROUND card opens (the warmth has run out by then). */
    static final int CARD_AFTER = 50;

    /** The errand that ends at the camp: its ilse step hands the first Ember over itself (chain ids are full ids). */
    static final String CHAIN = "awaken:the_last_delivery";
    static final String ILSE_LINE = "Ilse: Here, take an ember from our fire. Carry it with you, and when you find a spot you'd like to live, place it there. That'll be your home.";
    static final String ILSE_AGAIN = "Ilse: Lost your ember? That's fine, take another one.";
    static final String HETTIE_LINE = "Hettie: Take an ember from the fire before it gets dark. Place it wherever you want your home to be.";

    /** True while a fire is being raised to the Anchor (its removal is not a Hearth ending). */
    private static boolean raising;
    private static final Map<UUID, List<ItemStack>> HELD = new HashMap<>();

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Embers::onDrops);
        bus.addListener(Embers::onRespawn);
        bus.addListener(Embers::onInteract);
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> HELD.clear());
        Progression.onAwakened(Embers::onAwakened);
    }

    // ---- giving -------------------------------------------------------------------------------------------------------

    /** Whether this player should have an Ember now: no Hearth of their own and none carried. */
    public static boolean wants(ServerPlayer sp) {
        return HearthService.enabled() && HearthService.hearthOf(sp).isEmpty() && !carries(sp);
    }

    public static boolean carries(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(HearthContent.EMBER_ITEM.get())) return true;
        return false;
    }

    /** Gives an Ember if the player should have one. True when one was given. */
    public static boolean give(ServerPlayer sp) {
        if (!wants(sp)) return false;
        sp.getInventory().placeItemBackInInventory(new ItemStack(HearthContent.EMBER_ITEM.get()), net.minecraft.util.Prediction.SERVER_ONLY);
        HearthService.player(sp).emberGiven = true;
        sp.level().playSound(null, sp.blockPosition(), HearthContent.SND_LIFT.get(), SoundSource.PLAYERS, 0.7f, 1.1f);
        DirectorLog.log(sp, "hearth", "ember given");
        return true;
    }

    /**
     * Talking to Ilse or Hettie at the camp with no Ember and no Hearth gives one (a lost coal comes back). Not while
     * The Last Delivery still has its camp step to come: that step hands the first one over itself.
     */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getTarget() instanceof dev.awaken.living.wayfarer.Wayfarer w)) return;
        if (!Progression.profile(sp).awakened()) return;
        boolean classic = CityBasin.site(sp.level().getServer()).isEmpty();
        if (!classic && Progression.profile(sp).counter("awaken:carillon_registered") <= 0) return;
        boolean chainAhead = dev.awaken.living.api.Quests.active(sp).contains(CHAIN)
                && Progression.profile(sp).counter("awaken:claim/quest/" + CHAIN + "/ilse") <= 0;
        if (chainAhead || !campGives(sp, w.who(), false)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /**
     * A camp resident ({@code who}: ilse or hettie) hands over a coal if this player should have one, with their line.
     * {@code first}: say Ilse's first-night line whatever came before (tours). True when one was given.
     */
    public static boolean campGives(ServerPlayer sp, String who, boolean first) {
        if (!who.equals("ilse") && !who.equals("hettie")) return false;
        boolean again = HearthService.player(sp).emberGiven && !first;
        if (!give(sp)) return false;
        say(sp, who.equals("hettie") ? HETTIE_LINE : again ? ILSE_AGAIN : ILSE_LINE);
        return true;
    }

    /** Classic worlds (no Carillon, no camp errand): the Ember is part of the awakening's kit. */
    private static void onAwakened(ServerPlayer sp) {
        if (CityBasin.site(sp.level().getServer()).isPresent()) return;
        Later.run(sp.level().getServer(), 60, () -> {
            if (sp.isRemoved() || !give(sp)) return;
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Hearth Ember"))
                    .body(Component.literal("Carry it with you, and place it wherever you want your home to be.")).ttl(20 * 8).build());
        });
    }

    private static void say(ServerPlayer sp, String line) {
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line))
                .presentation(SignalWindow.Presentation.STORY).ttl(20 * 6).build());
    }

    /** Every two seconds while carried: one Ember a player, and none once their fire burns at home. */
    public static void checkCarried(ServerPlayer sp, ItemStack stack) {
        HearthPlayer hp = HearthService.player(sp);
        hp.emberGiven = true;
        boolean extra = HearthService.hearthOf(sp).isPresent();
        if (!extra) {
            var inv = sp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack other = inv.getItem(i);
                if (other != stack && other.is(HearthContent.EMBER_ITEM.get())) {
                    extra = true;
                    break;
                }
            }
        }
        if (!extra) return;
        stack.shrink(1);
        Signal.toast(sp, Component.literal(HearthService.hearthOf(sp).isPresent() ? "You already have a home fire, so the spare ember went out."
                : "You only need one ember, so the spare one went out."));
    }

    // ---- kept through death -------------------------------------------------------------------------------------------

    private static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = new ArrayList<>();
        e.getDrops().removeIf(ie -> {
            ItemStack st = ie.getItem();
            if (!st.is(HearthContent.EMBER_ITEM.get())) return false;
            kept.add(st.copy());
            return true;
        });
        if (!kept.isEmpty()) HELD.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).addAll(kept);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = HELD.remove(p.getUUID());
        if (kept == null) return;
        for (ItemStack st : kept) p.getInventory().placeItemBackInInventory(st, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- setting it down ----------------------------------------------------------------------------------------------

    /** Used on the top of a block: the coal goes into a ring of stones on it, or a plain reason why not. */
    public static void setDown(ServerPlayer sp, ServerLevel level, BlockPos clicked, InteractionHand hand) {
        BlockPos at = clicked.above();
        Optional<String> why = refusal(sp, level, clicked, at);
        if (why.isPresent()) {
            sp.sendOverlayMessage(Component.literal(why.get()));
            return;
        }
        ItemStack held = sp.getItemInHand(hand);
        if (!held.is(HearthContent.EMBER_ITEM.get())) return;
        held.shrink(1);
        found(sp, level, at);
    }

    /** Why the coal won't take here, in plain words, or empty when it will. Public for the tests. */
    public static Optional<String> refusal(ServerPlayer sp, ServerLevel level, BlockPos ground, BlockPos at) {
        if (!HearthService.enabled()) return Optional.of("The Hearth is turned off on this world.");
        if (level.dimension() != Level.OVERWORLD) return Optional.of("You can only place your ember in the Overworld.");
        HearthData d = HearthService.data(level.getServer());
        if (d.of(sp.getUUID()).isPresent()) return Optional.of("You already have a home fire.");
        BlockState g = level.getBlockState(ground), here = level.getBlockState(at);
        if (!g.isFaceSturdy(level, ground, Direction.UP) || !here.canBeReplaced() || !level.getFluidState(at).isEmpty()) {
            return Optional.of("Place it on solid, open ground.");
        }
        if (CityBasin.reserved(at.getX(), at.getZ())) return Optional.of("You can't make your home inside the city. Take it outside the walls.");
        if (dev.awaken.village.api.Villages.reserved(level, at, 8)) return Optional.of("You can't make your home inside a village. Take it past the last house.");
        if (WardHooks.at(level, at).isPresent()) return Optional.of("Old Precursor stone protects this ground, so your ember won't work here.");
        for (GateHooks.GateView g2 : GateHooks.near(level, at, GATE_CLEARANCE)) {
            if (!"RELEASED".equals(g2.state())) return Optional.of("There's a gate too close. Your home has to be at least " + GATE_CLEARANCE + " blocks from a gate.");
        }
        Optional<HearthRecord> other = d.containing(at, HearthService.OVERWORLD);
        if (other.isPresent()) return Optional.of("This is " + other.get().ownerName + "'s ground. Find your own.");
        Optional<String> household = Household.refusesOwnAnchor(d, sp.getUUID());
        return household;
    }

    /** The coal takes at {@code at}: a Tier 0 Hearth, the moment, then the YOUR GROUND card. Public for tests and commands. */
    public static HearthRecord found(ServerPlayer sp, ServerLevel level, BlockPos at) {
        HearthData d = HearthService.data(level.getServer());
        HearthRecord base = d.movingHome(sp.getUUID()) ? null : Household.sisterBase(d, sp.getUUID());
        level.setBlock(at, HearthContent.HEARTHFIRE.get().defaultBlockState(), 3);
        HearthRecord r = new HearthRecord(sp.getUUID(), sp.getGameProfile().name(), at.immutable(), HearthService.OVERWORLD);
        r.tier = 0;
        r.createdDay = PlayDays.current(level.getServer());
        r.scanAt = level.getGameTime() + 40;
        r.announceFirstScan = true;
        r.placedAt = level.getGameTime();
        r.name = suggestName(level, at, sp.getUUID());
        d.put(r);
        Household.restoreKept(level.getServer(), d, r);
        if (base != null) Household.founded(level.getServer(), r, base);
        HearthService.player(sp).respawnAtHearth = true;
        HearthService.player(sp).emberGiven = true;
        moment(level, at, r.radius(), false);
        if (Progression.profile(sp).awakened()) Progression.markNewThing(sp, "hearth_ember");
        DirectorLog.log(sp, "hearth", "ember set down at " + at.toShortString() + " (" + r.name + ")");
        HearthViews.refresh(sp);
        Household.refresh(level.getServer(), r);
        UUID owner = sp.getUUID();
        Later.run(level.getServer(), CARD_AFTER, () -> {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
            HearthRecord h = HearthService.data(level.getServer()).of(owner).orElse(null);
            if (p == null || h == null) return;
            if (AwakenNet.canReceive(p, HomeNet.NameCard.TYPE)) {
                AwakenNet.send(p, new HomeNet.NameCard(h.name));
            } else {
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Your ground"))
                        .body(Component.literal(h.name + " is your home now. You heal faster resting here, you wake up here if you die, and the land around it stays calm.")).ttl(20 * 8).build());
            }
        });
        return r;
    }

    /** The coal sinking into the ring, the warmth running out over the land, a low hearth tone: for everyone near. */
    static void moment(ServerLevel level, BlockPos at, int radius, boolean lifted) {
        level.playSound(null, at, lifted ? HearthContent.SND_LIFT.get() : HearthContent.SND_TAKE.get(), SoundSource.BLOCKS, 1f, 1f);
        HomeNet.Kindled msg = new HomeNet.Kindled(at, radius, lifted);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(at) <= (double) MOMENT_RANGE * MOMENT_RANGE) AwakenNet.send(p, msg);
        }
    }

    /** The owner named their ground (the card): letters, spaces, apostrophes and hyphens, up to 24. */
    public static void name(ServerPlayer sp, String raw) {
        Optional<HearthRecord> ho = HearthService.hearthOf(sp);
        if (ho.isEmpty()) return;
        String clean = cleanName(raw);
        if (clean.isEmpty()) return;
        ho.get().name = clean;
        HearthService.dirty(sp.level().getServer());
        HearthViews.refresh(sp);
        for (ServerPlayer p : Household.online(sp.level().getServer(), ho.get())) HearthViews.refresh(p);
    }

    /** A name as the card may set it ("" when nothing usable is left). Pure, for the tests. */
    public static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : raw.replace('—', '-').replace('–', '-').toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '\'' || c == '-') b.append(c);
        }
        String s = b.toString().strip().replaceAll(" {2,}", " ");
        return s.length() > 24 ? s.substring(0, 24).strip() : s;
    }

    // ---- the land's words for a name -------------------------------------------------------------------------------

    private static final String[] FOREST = {"Ash", "Elder", "Oak", "Holly", "Rowan", "Hazel", "Briar", "Fern", "Alder", "Linden", "Thorn"};
    private static final String[] PLAINS = {"Barley", "Clover", "Meadow", "Sorrel", "Lark", "Wheat", "Hay", "Fair", "Willow", "Oat"};
    private static final String[] SNOW = {"Rime", "Frost", "Hoar", "White", "Winter", "Pine", "Pale"};
    private static final String[] DRY = {"Sun", "Ochre", "Dune", "Amber", "Salt", "Rust", "Copper"};
    private static final String[] WET = {"Reed", "Sedge", "Heron", "Peat", "Lily", "Mire"};
    private static final String[] HIGH = {"Crag", "Stone", "High", "Kestrel", "Cloud", "Scree", "Eagle"};
    private static final String[] WATER = {"Tide", "Gull", "Pearl", "Brook", "Wade", "Shell"};
    private static final String[] JOINED = {"ford", "stead", "holm", "wick", "cote", "ley", "by", "worth", "combe", "field", "hearth", "burn"};
    private static final String[] APART = {"Rest", "Hollow", "End", "Green", "Hearth"};

    /** A name from the land's grammar round {@code at}: "Ashford", "Clover Rest". The same place gives the same name. */
    public static String suggestName(ServerLevel level, BlockPos at, UUID owner) {
        Holder<Biome> biome = level.getBiome(at);
        String[] firsts = biome.is(Tags.Biomes.IS_SNOWY) ? SNOW
                : biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA) ? DRY
                : biome.is(Tags.Biomes.IS_SWAMP) ? WET
                : biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) ? HIGH
                : biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) ? WATER
                : biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE) ? FOREST
                : PLAINS;
        long h = at.asLong() * 0x9E3779B97F4A7C15L ^ owner.getMostSignificantBits();
        h ^= h >>> 29;
        String first = firsts[(int) Math.floorMod(h, (long) firsts.length)];
        h = h * 0xBF58476D1CE4E5B9L + 7;
        h ^= h >>> 31;
        if (Math.floorMod(h, 10L) < 7) {
            String end = JOINED[(int) Math.floorMod(h >>> 8, (long) JOINED.length)];
            if (first.toLowerCase(java.util.Locale.ROOT).endsWith(end.substring(0, 1))) end = end.substring(1);
            return first + end;
        }
        return first + " " + APART[(int) Math.floorMod(h >>> 8, (long) APART.length)];
    }

    // ---- using the fire: lifting it, the guide ------------------------------------------------------------------------

    /** Empty-handed on a Hearthfire: lift the coal (owner, before the first room), else the guide, else ask to share. */
    public static void useFire(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        Optional<HearthRecord> ho = HearthService.data(level.getServer()).at(pos, HearthService.OVERWORLD);
        if (ho.isEmpty()) {
            sp.sendOverlayMessage(Component.literal("A cold ring of stones."));
            return;
        }
        HearthRecord h = ho.get();
        if (!h.belongs(sp.getUUID())) {
            Household.askAtAnchor(sp, h);
            return;
        }
        HearthService.player(sp).respawnAtHearth = true;
        HearthService.player(sp).respawnHearth = h.owner;
        if (h.owner.equals(sp.getUUID()) && h.hearthfire() && h.rooms.isEmpty()) {
            lift(sp, level, h);
            return;
        }
        dev.awaken.hearth.guide.Guide.open(sp, level, h);
    }

    /** The coal comes back out of the ring and the Hearth ends here: carry it on. */
    static void lift(ServerPlayer sp, ServerLevel level, HearthRecord h) {
        BlockPos at = h.pos;
        end(level, h);
        level.removeBlock(at, false);
        sp.getInventory().placeItemBackInInventory(new ItemStack(HearthContent.EMBER_ITEM.get()), net.minecraft.util.Prediction.SERVER_ONLY);
        moment(level, at, h.radius(), true);
        Signal.toast(sp, Component.literal("You picked your ember back up. Place it wherever you want your home."));
        DirectorLog.log(sp, "hearth", "ember lifted at " + at.toShortString());
        HearthViews.refresh(sp);
    }

    /** A Hearthfire block went (its owner broke it, or anything else): the Hearth ends and the coal is not lost. */
    public static void onFireRemoved(ServerLevel level, BlockPos pos) {
        if (raising) return;
        Optional<HearthRecord> ho = HearthService.data(level.getServer()).at(pos, HearthService.OVERWORLD);
        if (ho.isEmpty() || !ho.get().hearthfire()) return;
        HearthRecord h = ho.get();
        end(level, h);
        ItemStack coal = new ItemStack(HearthContent.EMBER_ITEM.get());
        Optional<ServerPlayer> owner = HearthService.owner(level.getServer(), h);
        if (owner.isPresent() && owner.get().level() == level && owner.get().blockPosition().closerThan(pos, 16)) {
            owner.get().getInventory().placeItemBackInInventory(coal, net.minecraft.util.Prediction.SERVER_ONLY);
            Signal.toast(owner.get(), Component.literal("Your ember came back to you. Place it wherever you want your home."));
        } else {
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, coal));
        }
        owner.ifPresent(HearthViews::refresh);
    }

    /** The Hearth record goes (the household is kept for when the coal takes again). */
    private static void end(ServerLevel level, HearthRecord h) {
        HearthData d = HearthService.data(level.getServer());
        List<ServerPlayer> household = Household.online(level.getServer(), h);
        d.keep(h);
        d.remove(h.owner);
        HearthService.forgetScan(h);
        for (ServerPlayer p : household) {
            if (HearthService.homeOf(p).isEmpty()) HearthService.player(p).respawnAtHearth = false;
            HearthViews.refresh(p);
        }
    }

    // ---- Tier I: a Lord's Heart on the fire -------------------------------------------------------------------------

    public static boolean raisesTheFire(ItemStack stack) {
        return stack.is(CoreApi.LORD_HEART.get()) || stack.is(HearthContent.HEARTH_ANCHOR_ITEM.get());
    }

    /** A Lord's Heart (or the Anchor) used on your Hearthfire: the fire takes it, and stands as the Anchor in place. */
    public static void raise(ServerPlayer sp, ServerLevel level, BlockPos pos, InteractionHand hand) {
        Optional<HearthRecord> ho = HearthService.data(level.getServer()).at(pos, HearthService.OVERWORLD);
        if (ho.isEmpty() || !ho.get().hearthfire()) return;
        HearthRecord h = ho.get();
        if (!h.owner.equals(sp.getUUID())) {
            sp.sendOverlayMessage(Component.literal("Only " + h.ownerName + " can feed this fire a Heart."));
            return;
        }
        ItemStack held = sp.getItemInHand(hand);
        if (!raisesTheFire(held)) return;
        if (!sp.hasInfiniteMaterials()) held.shrink(1);
        raise(sp, level, h);
    }

    /** The guide's button: the owner feeds the fire a Lord's Heart from their pack. */
    public static void raiseFromGuide(ServerPlayer sp, HearthRecord h) {
        if (!h.hearthfire() || !h.owner.equals(sp.getUUID())) return;
        if (!sp.hasInfiniteMaterials()) {
            if (Costs.countOf(sp, CoreApi.LORD_HEART.get()) < 1) return;
            Costs.pay(sp, List.of(new Costs.Part(CoreApi.LORD_HEART.get(), 1)));
        }
        raise(sp, HearthService.levelOf(sp.level().getServer(), h), h);
    }

    /** The fire becomes the Anchor (Tier I) where it stands. Public for the tests. */
    public static void raise(ServerPlayer sp, ServerLevel level, HearthRecord h) {
        raising = true;
        try {
            level.setBlock(h.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
        } finally {
            raising = false;
        }
        h.tier = 1;
        h.scanAt = level.getGameTime() + 20;
        HearthService.dirty(level.getServer());
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), h.pos.getX() + 0.5, h.pos.getY() + 1.2, h.pos.getZ() + 0.5, 60, 0.8, 0.8, 0.8, 0.03);
        HearthService.anchorTookRoot(sp, level, h, false, "The fire takes the Heart",
                "Your Hearthfire stands as an Anchor now. Within " + h.radius() + " blocks: yours, and people will come. Right-click the Anchor any time to open your Hearth's guide.");
    }

    /** Called from {@link HearthService#forgetPeople}: nothing held across worlds. */
    static void forget() {
        HELD.clear();
    }
}
