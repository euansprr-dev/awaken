package dev.awaken.hearth;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.guide.Cottage;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomScanner;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Scan;
import dev.awaken.hearth.player.RoomHere;
import dev.awaken.hearth.town.Town;
import dev.awaken.hearth.trophy.Trophies;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The Hearth's server side: Anchors, room scans, protection, respawn, Fracture healing, and the per-second upkeep
 * that drives the home and breaks near it. All of it is budgeted (SPEC performance header).
 */
public final class HearthService {
    private HearthService() {}

    public static final String OVERWORLD = Level.OVERWORLD.identifier().toString();
    /** Block reads a room scan may do per server tick (roughly 0.2-0.4 ms). */
    private static final int SCAN_BUDGET = 12_000;
    private static final int RESCAN_EVERY = 20 * 60 * 5;

    private static RoomScanner activeScan;
    private static final Deque<UUID> scanQueue = new ArrayDeque<>();

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(HearthService::onServerTick);
        bus.addListener(HearthService::onPlayerTick);
        bus.addListener(HearthService::onBreak);
        bus.addListener(HearthService::onPlace);
        bus.addListener(HearthService::onRespawnPosition);
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class, HearthService::onFall);
        bus.addListener(HearthService::onSetSpawn);
        bus.addListener(HearthService::onLogin);
        bus.addListener(HearthService::onRespawn);
        bus.addListener(HearthService::onWake);
        bus.addListener(HearthService::onGrief);
        bus.addListener(HearthService::onExplosion);
        bus.addListener(HearthService::onEntityJoin);
        bus.addListener(HearthService::onEntityLeave);
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            activeScan = null;
            scanQueue.clear();
            REST.clear();
            RESPAWNING.clear();
            BED_WAITING.clear();
            BED_ASKED.clear();
            forgetPeople();
        });
    }

    /**
     * Server stop (singleplayer reuses the JVM): what the module remembers about people of the world that stopped,
     * namely breaks near their Hearths, invitations, askings at Anchors and open offers.
     */
    static void forgetPeople() {
        Breaks.clear();
        Embers.forget();
        dev.awaken.hearth.settle.Arrival.clear();
        dev.awaken.hearth.settle.HomePeople.clear();
        dev.awaken.hearth.settle.MineHead.clear();
        Household.forgetAll();
        Offers.forgetAll();
    }

    /** The world's feature flag. Safe to call before the config has loaded (defaults to on). */
    public static boolean enabled() {
        try {
            return AwakenConfig.HEARTH.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static HearthData data(MinecraftServer server) {
        return HearthData.get(server);
    }

    /** The Hearth this player owns (only the owner may move it, invite to it or have Oren build there). */
    public static Optional<HearthRecord> hearthOf(ServerPlayer player) {
        return data(player.level().getServer()).of(player.getUUID());
    }

    /** The Hearth this player lives at: their own, else the first one they are a member of (SPEC "Together"). */
    public static Optional<HearthRecord> homeOf(ServerPlayer player) {
        return data(player.level().getServer()).home(player.getUUID());
    }

    /** The Hearth whose radius holds {@code pos}, if this player belongs to it (owner or member). */
    public static Optional<HearthRecord> sharedAt(ServerPlayer player, BlockPos pos) {
        return data(player.level().getServer()).containing(pos, OVERWORLD).filter(h -> h.belongs(player.getUUID()));
    }

    public static HearthPlayer player(Player p) {
        return p.getData(HearthContent.PLAYER.get());
    }

    public static Optional<ServerPlayer> owner(MinecraftServer server, HearthRecord r) {
        return Optional.ofNullable(server.getPlayerList().getPlayer(r.owner));
    }

    public static ServerLevel levelOf(MinecraftServer server, HearthRecord r) {
        return server.overworld();
    }

    public static void dirty(MinecraftServer server) {
        data(server).setDirty();
    }

    // ---- the Anchor -------------------------------------------------------------------------------------------------

    public static boolean canPlaceAnchor(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        if (!enabled()) {
            sp.sendOverlayMessage(Component.literal("The Hearth is turned off on this world."));
            return false;
        }
        if (level.dimension() != Level.OVERWORLD) {
            sp.sendOverlayMessage(Component.literal("The Anchor only takes root in the waking world."));
            return false;
        }
        if (dev.awaken.terrain.api.CityBasin.reserved(pos.getX(), pos.getZ())) {
            sp.sendOverlayMessage(Component.literal("The city keeps its own ground. Raise your Anchor out past the walls."));
            return false;
        }
        if (dev.awaken.village.api.Villages.reserved(level, pos, 8)) {
            sp.sendOverlayMessage(Component.literal("People live here. Raise your Anchor out past the last fence."));
            return false;
        }
        HearthData d = data(level.getServer());
        Optional<HearthRecord> own = d.of(sp.getUUID());
        if (own.isPresent()) {
            sp.sendOverlayMessage(Component.literal(own.get().hearthfire() ? "Use the Anchor on your Hearthfire: it rises there."
                    : "You already have a Hearth. Break your old Anchor to move home."));
            return false;
        }
        Optional<HearthRecord> other = d.containing(pos, OVERWORLD);
        if (other.isPresent()) {
            sp.sendOverlayMessage(Component.literal("This land is " + other.get().ownerName + "'s Hearth."));
            return false;
        }
        // a member's own Anchor founds a sister home of the Hearth they share, once its tier allows one
        Optional<String> refused = Household.refusesOwnAnchor(d, sp.getUUID());
        if (refused.isPresent()) {
            sp.sendOverlayMessage(Component.literal(refused.get()));
            return false;
        }
        return true;
    }

    public static void onAnchorPlaced(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        HearthData d = data(level.getServer());
        if (d.of(sp.getUUID()).isPresent() || level.dimension() != Level.OVERWORLD) return;
        // moving one's own Hearth never founds a sister home; otherwise a member's Anchor founds one where it may
        HearthRecord base = d.movingHome(sp.getUUID()) ? null : Household.sisterBase(d, sp.getUUID());
        HearthRecord r = new HearthRecord(sp.getUUID(), sp.getGameProfile().name(), pos.immutable(), OVERWORLD);
        r.createdDay = PlayDays.current(level.getServer());
        r.scanAt = level.getGameTime() + 20;
        r.announceFirstScan = true;
        r.placedAt = level.getGameTime();
        r.name = Embers.suggestName(level, pos, sp.getUUID());
        d.put(r);
        Household.restoreKept(level.getServer(), d, r);
        if (base != null) Household.founded(level.getServer(), r, base);
        player(sp).respawnAtHearth = true;
        level.playSound(null, pos, HearthContent.SND_ANCHOR.get(), SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.02);
        anchorTookRoot(sp, level, r, base != null, base != null ? "A second home" : "Home exists",
                "Within " + r.radius() + " blocks: yours" + (base != null ? " and your household's" : "")
                        + ". You wake here, you heal here, and people will come. Right-click the Anchor any time to open your Hearth's guide.");
    }

    /**
     * Tier I begins (an Anchor placed, or a Hearthfire raised by a Lord's Heart): the guide's toast, the furniture in the
     * recipe book, the Spire's Board and Monolith, the household told. A toast shows the title and the first sentence:
     * keep both short. The rest is in Recent messages.
     */
    static void anchorTookRoot(ServerPlayer sp, ServerLevel level, HearthRecord r, boolean sister, String title, String body) {
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title)).body(Component.literal(body))
                .ttl(20 * 8).priority(4).build());
        unlockFurnitureRecipes(sp);
        if (Progression.profile(sp).awakened()) Progression.markNewThing(sp, "hearth");
        DirectorLog.log(sp, "hearth", "anchor at " + r.pos.toShortString() + (sister ? " (a sister home)" : ""));
        Town.onHearthCreated(sp, level, r);
        Town.raiseSpire(level, r);
        HearthViews.refresh(sp);
        Household.refresh(level.getServer(), r);
    }

    /** The Anchor teaches the Hearth's furniture in the recipe book (Meditation Mat, Forge, Trophy Mount). */
    static void unlockFurnitureRecipes(ServerPlayer sp) {
        var recipes = sp.level().getServer().getRecipeManager();
        List<ResourceKey<Recipe<?>>> keys = new ArrayList<>();
        for (String id : Guide.FURNITURE_RECIPES) {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, dev.awaken.Awaken.id(id));
            if (recipes.byKey(key).isPresent()) keys.add(key);
        }
        if (!keys.isEmpty()) sp.awardRecipesByKey(keys);
    }

    public static void onAnchorUsed(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        HearthData d = data(level.getServer());
        Optional<HearthRecord> r = d.at(pos, level.dimension().identifier().toString());
        if (r.isEmpty()) {
            // An Anchor with no owner (placed by a command or left from an old world): the first homeless player claims it.
            if (canPlaceAnchor(sp, level, pos)) onAnchorPlaced(sp, level, pos);
            return;
        }
        if (!r.get().belongs(sp.getUUID())) {
            Household.askAtAnchor(sp, r.get());
            return;
        }
        player(sp).respawnAtHearth = true;
        player(sp).respawnHearth = r.get().owner;
        if (dev.awaken.hearth.request.Requests.onAnchorUsed(sp, level, r.get())) return; // carried a request home: deliver first
        Guide.open(sp, level, r.get());
    }

    public static void onAnchorRemoved(ServerLevel level, BlockPos pos) {
        HearthData d = data(level.getServer());
        Optional<HearthRecord> r = d.at(pos, level.dimension().identifier().toString());
        if (r.isEmpty()) return;
        HearthRecord h = r.get();
        List<ServerPlayer> household = Household.online(level.getServer(), h);
        Town.dissolve(level, h);
        dev.awaken.hearth.settle.HomePeople.stop(h);
        d.keep(h); // the members share it again when the owner places the Anchor anew
        d.remove(h.owner);
        if (activeScan != null && activeScan.hearth() == h) activeScan = null;
        owner(level.getServer(), h).ifPresent(sp -> {
            player(sp).respawnAtHearth = homeOf(sp).isPresent() && player(sp).respawnAtHearth;
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Your Anchor went cold."))
                    .body(Component.literal("Place it again wherever home should be. The town will follow.")).build());
        });
        for (ServerPlayer sp : household) {
            if (!sp.getUUID().equals(h.owner)) {
                if (homeOf(sp).isEmpty()) player(sp).respawnAtHearth = false;
                Signal.toast(sp, Component.literal(h.ownerName + "'s Anchor went cold. You'll share it again where it takes root."));
            }
            Trophies.applyHallBonus(sp, homeOf(sp).orElse(null));
            HearthViews.refresh(sp);
        }
    }

    // ---- room-relevant changes ----------------------------------------------------------------------------------------

    /** Something room-relevant changed at {@code pos}: rescan that Hearth soon (debounced). */
    public static void blockChanged(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return;
        data(level.getServer()).containing(pos, OVERWORLD).ifPresent(h -> h.scanAt = level.getGameTime() + 40);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (roomRelevant(level, e.getPlacedBlock(), e.getPos())) blockChanged(level, e.getPos());
    }

    /** Blocks whose change can make or break a room: furniture, full blocks and the thin things that close gaps. */
    private static boolean roomRelevant(ServerLevel level, BlockState s, BlockPos pos) {
        return RoomScanner.isFurniture(s) || RoomScanner.closesGap(s) || s.isCollisionShapeFullBlock(level, pos);
    }

    /** Armour stands are entities: placing or breaking one is a room change too (the Armory). */
    private static void onEntityJoin(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof ArmorStand && !e.loadedFromDisk() && e.getLevel() instanceof ServerLevel level) {
            blockChanged(level, e.getEntity().blockPosition());
        }
    }

    private static void onEntityLeave(EntityLeaveLevelEvent e) {
        if (e.getEntity() instanceof ArmorStand stand && stand.getRemovalReason() != null && stand.getRemovalReason().shouldDestroy()
                && e.getLevel() instanceof ServerLevel level) {
            blockChanged(level, stand.blockPosition());
        }
    }

    private static void onBreak(BreakBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        if (s.is(HearthContent.HEARTH_ANCHOR.get()) || s.is(HearthContent.HEARTHFIRE.get())) {
            Optional<HearthRecord> r = data(level.getServer()).at(e.getPos(), level.dimension().identifier().toString());
            Player p = e.getPlayer();
            boolean op = p instanceof ServerPlayer sp && sp.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
            if (r.isPresent() && !r.get().owner.equals(p.getUUID()) && !op) {
                e.setCanceled(true);
                p.sendOverlayMessage(Component.literal("Only " + r.get().ownerName + " can move this Hearth."));
                return;
            }
        }
        if (level.dimension() == Level.OVERWORLD && roomRelevant(level, s, e.getPos())) blockChanged(level, e.getPos());
    }

    // ---- Hearth Shield (Perks.HEARTH_SHIELD): during a break near home, nothing breaks your buildings -------------------

    private static boolean shielded(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return false;
        Optional<HearthRecord> h = data(level.getServer()).containing(pos, OVERWORLD);
        if (h.isEmpty() || !Breaks.active(h.get())) return false;
        // the shield is anyone's in the household who holds the perk and is online
        for (ServerPlayer p : Household.online(level.getServer(), h.get())) if (Progression.hasPerk(p, Perks.HEARTH_SHIELD)) return true;
        return false;
    }

    private static void onGrief(EntityMobGriefingEvent e) {
        Entity ent = e.getEntity();
        if (ent instanceof Player || !(ent.level() instanceof ServerLevel level)) return;
        if (shielded(level, ent.blockPosition())) e.setCanGrief(false);
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (e.getExplosion().getIndirectSourceEntity() instanceof Player) return;
        BlockPos centre = BlockPos.containing(e.getExplosion().center());
        if (!shielded(level, centre)) return;
        int before = e.getAffectedBlocks().size();
        e.getAffectedBlocks().removeIf(p -> data(level.getServer()).containing(p, OVERWORLD).isPresent());
        if (before > 0 && e.getAffectedBlocks().size() < before) {
            level.sendParticles(ParticleTypes.WAX_ON, centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5, 20, 1.5, 1, 1.5, 0.1);
        }
    }

    // ---- respawn ----------------------------------------------------------------------------------------------------
    //
    // Once you use your fire or Anchor you wake there after any fall, anywhere, until you choose another bed yourself
    // (SPEC §28.10). Sleeping in a bed (or using a respawn anchor) off your land doesn't move it: your Hearth stays your
    // spawn and, once you're up, a card asks whether to wake at that bed instead; only Y moves it. Nothing else does: not
    // the old body's bed copied onto the new one at every respawn, not a reset to nothing, not our own systems (an inn
    // room is lent for one fall, then given back), not /spawnpoint. With no bed elsewhere (none set, or gone) a player
    // with a Hearth wakes there rather than at world spawn.

    /** The server tick each player's respawn ran: their old bed, copied onto the new body in that tick, is no choice. */
    private static final Map<UUID, Integer> RESPAWNING = new HashMap<>();
    /** Set while one of our own systems moves a respawn point ({@link #systemRespawn}) or the player's Y does. */
    private static boolean systemSpawn;
    /** A bed slept in off your land: offered once you're up. */
    private record BedAsk(ServerPlayer.RespawnConfig bed, long day) {}
    /** Beds waiting to be offered, and the bed last offered to each player (asked once a play-day per bed). */
    private static final Map<UUID, BedAsk> BED_WAITING = new HashMap<>(), BED_ASKED = new HashMap<>();
    /** The offer's key in {@link Offers} and how long the card waits for Y. */
    static final String BED_OFFER = "bed";
    private static final int BED_OFFER_TTL = 20 * 45;

    private static void onSetSpawn(PlayerSetSpawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || systemSpawn || e.isForced() || e.getNewSpawn() == null) return;
        MinecraftServer server = sp.level().getServer();
        if (Integer.valueOf(server.getTickCount()).equals(RESPAWNING.get(sp.getUUID()))) return;
        ServerLevel level = server.getLevel(e.getSpawnLevel());
        BlockPos bed = e.getNewSpawn();
        if (level == null || !level.isLoaded(bed) || !isBed(level.getBlockState(bed))) return;
        HearthPlayer hp = player(sp);
        String dim = e.getSpawnLevel().identifier().toString();
        Optional<HearthRecord> home = data(server).hearthsOf(sp.getUUID()).stream()
                .filter(h -> h.dimension.equals(dim) && h.contains(bed)).findFirst();
        if (home.isPresent()) {
            // a bed at home: you wake at home (this one, of those you share)
            hp.lent = null;
            hp.respawnAtHearth = true;
            hp.respawnHearth = home.get().owner;
            return;
        }
        if (!hp.respawnAtHearth || wakeHearth(sp).isEmpty()) {
            hp.lent = null; // no Hearth to keep: the bed is your spawn, as in vanilla
            return;
        }
        // your Hearth stays your spawn (and no "Respawn point set"); the bed is offered once you're up
        e.setCanceled(true);
        ServerPlayer.RespawnConfig chosen = new ServerPlayer.RespawnConfig(
                net.minecraft.world.level.storage.LevelData.RespawnData.of(e.getSpawnLevel(), bed, sp.getYRot(), sp.getXRot()), false);
        long day = PlayDays.current(server);
        BedAsk asked = BED_ASKED.get(sp.getUUID());
        if (asked != null && asked.day == day && asked.bed.isSamePosition(chosen)) return; // asked about this bed today
        BED_WAITING.put(sp.getUUID(), new BedAsk(chosen, day));
    }

    /** Once a second: a bed waiting to be offered, once the player is up (asleep, the card would wait behind the bed). */
    static void offerWaitingBed(ServerPlayer sp) {
        if (sp.isSleeping()) return;
        BedAsk ask = BED_WAITING.remove(sp.getUUID());
        if (ask == null) return;
        Optional<HearthRecord> h = wakeHearth(sp);
        if (h.isEmpty() || !player(sp).respawnAtHearth) return;
        BED_ASKED.put(sp.getUUID(), ask);
        String home = h.get().displayName();
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Wake at this bed instead?"))
                .body(Component.literal(home + " is still where you wake if you fall. You can make this bed your spawn instead."))
                .choice(Offers.offer(sp, BED_OFFER, BED_OFFER_TTL, p -> chooseBed(p, ask.bed)), "Wake at this bed", "Keep " + home)
                .ttl(BED_OFFER_TTL).priority(4).build());
        DirectorLog.log(sp, "hearth", "offered the bed at " + ask.bed.respawnData().pos().toShortString() + " (" + home + " kept)");
    }

    /** Whether a bed is waiting to be offered to this player (tests). */
    static boolean bedWaiting(UUID player) {
        return BED_WAITING.containsKey(player);
    }

    /** Y on the bed's card: the bed becomes the player's spawn, and they're told how to come home again. */
    static void chooseBed(ServerPlayer sp, ServerPlayer.RespawnConfig bed) {
        MinecraftServer server = sp.level().getServer();
        ServerLevel level = server.getLevel(bed.respawnData().globalPos().dimension());
        BlockPos at = bed.respawnData().pos();
        if (level == null || level.isLoaded(at) && !isBed(level.getBlockState(at))) {
            sp.sendOverlayMessage(Component.literal("That bed is gone."));
            return;
        }
        Optional<HearthRecord> was = wakeHearth(sp);
        systemSpawn = true;
        try {
            sp.setRespawnPosition(bed, true);
        } finally {
            systemSpawn = false;
        }
        HearthPlayer hp = player(sp);
        hp.lent = null;
        hp.respawnAtHearth = false;
        String home = was.map(HearthRecord::displayName).orElse("home");
        String use = was.map(h -> h.hearthfire() ? "fire" : "Anchor").orElse("fire");
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("You'll wake at this bed now"))
                .body(Component.literal("If you fall, you wake here, not at " + home + ". Use your " + use + " there to wake at home again."))
                .ttl(20 * 8).priority(4).build());
        String dim = bed.respawnData().globalPos().dimension().identifier().toString();
        dev.awaken.Awaken.LOG.info("[hearth] {} chose a bed at {} in {}: they wake there now, not at {}", sp.getGameProfile().name(),
                at.toShortString(), dim, home);
        DirectorLog.log(sp, "hearth", "chose a bed at " + at.toShortString() + " in " + dim + ": wakes there now, not at " + home + " (told)");
        HearthViews.refresh(sp);
    }

    /** A bed or a respawn anchor: the blocks a player sets their own spawn at. */
    private static boolean isBed(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.AbstractBedBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.RespawnAnchorBlock;
    }

    /**
     * One of our own systems moves the player's respawn point ({@link dev.awaken.core.api.HearthHooks#lendRespawn}):
     * never read as the player choosing another bed. {@code lent}: a bed for the next fall only (an inn room), which a
     * player who wakes at their Hearth wakes at once; else the point is given back.
     */
    public static void systemRespawn(ServerPlayer sp, ServerPlayer.@Nullable RespawnConfig to, boolean lent) {
        systemSpawn = true;
        try {
            sp.setRespawnPosition(to, false);
        } finally {
            systemSpawn = false;
        }
        player(sp).lent = lent && to != null ? to.respawnData().globalPos() : null;
    }

    /**
     * The Hearth this player wakes at if they fall now: the one they chose (their fire or Anchor used last, or a bed at
     * home), or any home of theirs when they have no other bed (none set, or the one set is gone). Empty: a bed elsewhere.
     */
    public static Optional<HearthRecord> wakesAt(ServerPlayer sp) {
        Optional<HearthRecord> h = wakeHearth(sp);
        if (h.isEmpty() || player(sp).respawnAtHearth) return h;
        ServerPlayer.RespawnConfig bed = sp.getRespawnConfig();
        if (bed == null) return h;
        ServerLevel level = sp.level().getServer().getLevel(bed.respawnData().globalPos().dimension());
        BlockPos at = bed.respawnData().pos();
        return level == null || level.isLoaded(at) && !isBed(level.getBlockState(at)) ? h : Optional.empty();
    }

    /** The Hearth this player wakes at: the shared one whose Anchor they used last, else their home. */
    public static Optional<HearthRecord> wakeHearth(ServerPlayer sp) {
        UUID chosen = player(sp).respawnHearth;
        if (chosen != null) {
            Optional<HearthRecord> h = data(sp.level().getServer()).of(chosen).filter(r -> r.belongs(sp.getUUID()));
            if (h.isPresent()) return h;
        }
        return homeOf(sp);
    }

    /** A player who falls may wake at their Hearth: its land is made while they're down (SPEC §28.9, "Arrivals"). */
    private static void onFall(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !enabled()) return;
        wakeHearth(sp).ifPresent(h -> dev.awaken.core.api.Passage.warm(levelOf(sp.level().getServer(), h), h.pos.getX() + 0.5, h.pos.getZ() + 0.5));
    }

    private static void onRespawnPosition(PlayerRespawnPositionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.level().getServer();
        RESPAWNING.put(sp.getUUID(), server.getTickCount());
        if (e.isFromEndFight()) return;
        Optional<HearthRecord> h = wakeHearth(sp);
        if (h.isEmpty()) return;
        HearthPlayer hp = player(sp);
        TeleportTransition old = e.getTeleportTransition();
        ServerPlayer.RespawnConfig bed = sp.getRespawnConfig();
        // vanilla is sending them to world spawn: no bed set (and no other module chose a place), or the bed is gone
        boolean worldSpawn = old.missingRespawnBlock() || bed == null && old.newLevel() == server.findRespawnDimension();
        String who = sp.getGameProfile().name();
        if (!enabled()) {
            if (worldSpawn) dev.awaken.Awaken.LOG.info("[hearth] {} wakes at world spawn though they have a Hearth: the Hearth is off in the config", who);
            return;
        }
        GlobalPos lent = hp.lent;
        hp.lent = null;
        String why;
        if (hp.respawnAtHearth) {
            if (lent != null && bed != null && !worldSpawn && lent.equals(bed.respawnData().globalPos())) {
                dev.awaken.Awaken.LOG.info("[hearth] {} wakes at the bed lent for one fall at {} (an inn room); at {} after", who,
                        lent.pos().toShortString(), h.get().displayName());
                return;
            }
            why = "the Hearth they chose";
        } else if (worldSpawn) {
            why = bed == null ? "no bed elsewhere" : "their bed at " + bed.respawnData().pos().toShortString() + " is gone or blocked";
        } else {
            return; // a bed they chose elsewhere: vanilla wakes them there
        }
        ServerLevel level = levelOf(server, h.get());
        Vec3 spot = safeSpotNear(level, h.get().pos);
        e.setTeleportTransition(new TeleportTransition(level, spot, Vec3.ZERO, old.yRot(), old.xRot(), old.postTeleportTransition()));
        dev.awaken.Awaken.LOG.info("[hearth] {} wakes at {} ({}): {}", who, h.get().displayName(), h.get().pos.toShortString(), why);
        DirectorLog.log(sp, "hearth", "wakes at " + h.get().displayName() + ": " + why);
    }

    /** A standing spot next to the Anchor (two air blocks over something solid), or right on top of it. */
    public static Vec3 safeSpotNear(ServerLevel level, BlockPos anchor) {
        level.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        int[][] ring = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int dy = 0; dy >= -2; dy--) {
            for (int[] o : ring) {
                BlockPos feet = anchor.offset(o[0], dy, o[1]);
                if (level.getBlockState(feet.below()).isSolid() && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return Vec3.atBottomCenterOf(anchor.above());
    }

    // ---- login, respawn, waking -------------------------------------------------------------------------------------

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        hearthOf(sp).ifPresent(h -> {
            if (!h.ownerName.equals(sp.getGameProfile().name())) {
                h.ownerName = sp.getGameProfile().name();
                dirty(sp.level().getServer());
            }
        });
        Household.onLogin(sp);
        homeOf(sp).ifPresent(h -> Trophies.applyHallBonus(sp, h));
        Auras.refresh(sp);
        HearthViews.refresh(sp);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        homeOf(sp).ifPresent(h -> Trophies.applyHallBonus(sp, h));
        Auras.refresh(sp);
        HearthViews.refresh(sp);
    }

    private static void onWake(PlayerWakeUpEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.wakeImmediately() || !enabled()) return;
        if (!(sp.level() instanceof ServerLevel level)) return;
        // (W6) the bed you wake in at home is yours (never a settler's spare)
        sp.getSleepingPos().ifPresent(bed -> sharedAt(sp, bed).ifPresent(h -> dev.awaken.hearth.settle.Settlers.woke(sp, h, headOf(level, bed))));
        Rested.onWake(sp, level);
        if (!Hearth.isSanctuary(level, sp.blockPosition())) return;
        boolean fractured = Progression.profile(sp).fractureUntil() > level.getGameTime();
        if (fractured) Progression.clearFracture(sp);
        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 0, true, true));
        Signal.toast(sp, Component.literal(fractured ? "You slept in your Sanctuary. The Fracture is gone." : "Well rested in your Sanctuary."));
    }

    /** A bed's head (where a settler's bed is kept), from either half. */
    static BlockPos headOf(ServerLevel level, BlockPos bed) {
        BlockState s = level.getBlockState(bed);
        if (s.getBlock() instanceof net.minecraft.world.level.block.BedBlock && s.getValue(net.minecraft.world.level.block.BedBlock.PART)
                == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
            return bed.relative(s.getValue(net.minecraft.world.level.block.BedBlock.FACING));
        }
        return bed.immutable();
    }

    // ---- Fracture heals with rest at home -----------------------------------------------------------------------------

    private record Rest(int seconds, double x, double y, double z) {}

    private static final Map<UUID, Rest> REST = new HashMap<>();

    /** Seconds of quiet rest inside the radius that clear Fracture, by Anchor tier. */
    public static int restSeconds(int tier) {
        return Math.max(10, 30 - 4 * (Math.max(1, tier) - 1));
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0 || !enabled()) return;
        if (!BED_WAITING.isEmpty()) offerWaitingBed(sp);
        if (!(sp.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            REST.remove(sp.getUUID());
            if (sp.getData(HearthContent.HERE.get()).in()) sp.setData(HearthContent.HERE.get(), dev.awaken.hearth.player.RoomHere.NONE);
            return;
        }
        trackRoom(sp);
        long now = level.getGameTime();
        if (Progression.profile(sp).fractureUntil() <= now) {
            REST.remove(sp.getUUID());
            return;
        }
        Optional<HearthRecord> h = data(level.getServer()).containing(sp.blockPosition(), OVERWORLD);
        boolean recentlyHurt = sp.getLastHurtByMobTimestamp() > 0 && sp.tickCount - sp.getLastHurtByMobTimestamp() < 200;
        if (h.isEmpty() || recentlyHurt) {
            REST.remove(sp.getUUID());
            return;
        }
        Rest prev = REST.get(sp.getUUID());
        boolean still = prev != null && sp.distanceToSqr(prev.x, prev.y, prev.z) < 0.25;
        int secs = still || sp.isSleeping() ? prev.seconds + 1 : 0;
        REST.put(sp.getUUID(), new Rest(secs, sp.getX(), sp.getY(), sp.getZ()));
        int need = restSeconds(h.get().tier);
        if (h.get().roomAt(sp.blockPosition(), RoomType.SANCTUARY).isPresent()) need = need / 2;
        if (secs >= 3 && secs < need) {
            sp.sendOverlayMessage(Component.literal("Resting at your Hearth · " + (need - secs) + "s").withStyle(ChatFormatting.GOLD));
        } else if (secs >= need) {
            Progression.clearFracture(sp);
            REST.remove(sp.getUUID());
            level.sendParticles(HearthContent.HEARTH_EMBER.get(), sp.getX(), sp.getY() + 1, sp.getZ(), 24, 0.5, 0.8, 0.5, 0.01);
            Signal.toast(sp, Component.literal("The Hearth's warmth mends you. Fracture healed."));
        }
    }

    // ---- server tick: scans and upkeep --------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (!enabled()) return;
        ServerLevel overworld = server.overworld();
        HearthData d = data(server);
        long now = overworld.getGameTime();

        // 1. Room scans: one at a time, budgeted.
        if (activeScan == null) {
            if (server.getTickCount() % 10 == 0) {
                for (HearthRecord h : d.all()) {
                    // a reading loaded from the save stands; its home is read again some time in the next few minutes
                    if (h.lastScan < 0) h.lastScan = now - RESCAN_EVERY + 20 * 30 + Math.floorMod(h.owner.hashCode(), 20 * 240);
                    if (h.scanAt == 0 && now - h.lastScan > RESCAN_EVERY) h.scanAt = now;
                    if (h.scanAt != 0 && h.scanAt <= now && !scanQueue.contains(h.owner) && overworld.isLoaded(h.pos)) scanQueue.add(h.owner);
                }
            }
            UUID next = scanQueue.poll();
            if (next != null) {
                d.of(next).ifPresent(h -> {
                    h.scanAt = 0;
                    activeScan = new RoomScanner(overworld, h, people(server, h), scan -> onRoomsScanned(server, h, scan));
                });
            }
        }
        if (activeScan != null) {
            activeScan.step(SCAN_BUDGET);
            if (activeScan.done()) activeScan = null;
        }
        // (W6) a home whose people changed walks its ground (budgeted), and doors open for them
        dev.awaken.hearth.settle.HomePeople.tick(overworld);

        // 2. Upkeep, once a second, spread across Hearths by owner hash so they don't all run on the same tick.
        int tick = server.getTickCount();
        DUE.clear();
        for (HearthRecord h : d.all()) if ((tick + (h.owner.hashCode() & 15)) % 20 == 0) DUE.add(h);
        for (int i = 0; i < DUE.size(); i++) upkeep(server, overworld, DUE.get(i), now);
        DUE.clear();
    }

    /**
     * The Anchor's flame shows the tier (SPEC §28.8): its block state follows the record (an upgrade, an old world's Anchor
     * loaded at tier I's state). One state read a second; a change only when they differ.
     */
    public static void showTier(ServerLevel level, HearthRecord h) {
        if (h.hearthfire() || !level.isLoaded(h.pos)) return;
        BlockState s = level.getBlockState(h.pos);
        if (!(s.getBlock() instanceof dev.awaken.hearth.block.HearthAnchorBlock a)) return;
        int want = Math.max(1, Math.min(5, h.tier));
        if (s.getValue(dev.awaken.hearth.block.HearthAnchorBlock.TIER) != want) level.setBlock(h.pos, a.atTier(want), 3);
    }

    /** Reused every tick so upkeep never allocates (and Hearths removed during upkeep don't break the loop). */
    private static final List<HearthRecord> DUE = new ArrayList<>();

    private static void upkeep(MinecraftServer server, ServerLevel level, HearthRecord h, long now) {
        boolean loaded = level.isPositionEntityTicking(h.pos);
        if (loaded && !level.getBlockState(h.pos).is(h.hearthfire() ? HearthContent.HEARTHFIRE.get() : HearthContent.HEARTH_ANCHOR.get())) {
            // broken by something that skipped the block callbacks
            if (h.hearthfire()) Embers.onFireRemoved(level, h.pos);
            else onAnchorRemoved(level, h.pos);
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
        if (loaded) {
            if (!h.hearthfire()) showTier(level, h);
            if (!h.hearthfire()) Town.upkeep(level, h, owner, now);
            if (!h.hearthfire()) dev.awaken.hearth.settle.Work.tick(level, h, now);
            RoomEffects.bells(level, h);
        }
        if (server.getTickCount() % 100 < 20) {
            List<ServerPlayer> home = h.members.isEmpty() ? (owner == null ? List.of() : List.of(owner)) : Household.online(server, h);
            if (!home.isEmpty()) Breaks.check(home, level, h);
        }
    }

    /**
     * Called when a room scan finishes: store the rooms and their reading, Comfort, Style and the land; update trophy
     * bonuses; tell the owner about kinds of room new to this home.
     */
    static void onRoomsScanned(MinecraftServer server, HearthRecord h, Scan scan) {
        List<Room> rooms = scan.rooms();
        EnumSet<RoomType> before = EnumSet.noneOf(RoomType.class);
        for (Room r : h.rooms) before.addAll(r.types());
        h.rooms = rooms;
        h.misses = scan.misses();
        h.furniture = scan.furniture();
        h.lastScan = server.overworld().getGameTime();
        h.land = Comfort.land(scan.land());
        h.yard = scan.yard();
        h.comfort = Comfort.of(rooms, h.land);
        h.style = Comfort.style(scan.families());
        dirty(server);
        Set<String> trophies = new java.util.HashSet<>();
        EnumSet<RoomType> after = EnumSet.noneOf(RoomType.class);
        for (Room r : rooms) {
            after.addAll(r.types());
            if (r.types().contains(RoomType.TROPHY_HALL)) trophies.addAll(r.trophies());
        }
        h.hallTrophies = Set.copyOf(trophies);
        boolean announce = h.scannedOnce || h.announceFirstScan;
        h.scannedOnce = true;
        h.announceFirstScan = false;
        owner(server, h).ifPresent(sp -> {
            if (!announce) return;
            if (!before.contains(RoomType.SHELTER) && after.contains(RoomType.SHELTER)) {
                // the first night's answer: walls and a roof, and from now on everything you build is read
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("First shelter"))
                        .body(Component.literal("Rest heals here. From now on the Hearth reads everything you build."))
                        .topic("hearth_room").ttl(20 * 6).build());
            }
            for (RoomType t : after) {
                if (t == RoomType.SHELTER || before.contains(t)) continue;
                // toast: the title and its first sentence fit 144 px; the rest is kept in Recent messages
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(t.label + " recognised"))
                        .body(Component.literal(t.toast + " " + t.does + " The Hearth noticed " + t.needs + " in a closed room."))
                        .topic("hearth_room").ttl(20 * 6).build());
            }
        });
        // the Trophy Hall's bonus is everyone's who lives here; a new Lookout fills in the Atlas round home
        boolean lookout = after.contains(RoomType.LOOKOUT) && !before.contains(RoomType.LOOKOUT);
        for (ServerPlayer sp : Household.online(server, h)) {
            if (homeOf(sp).orElse(null) == h) Trophies.applyHallBonus(sp, h);
            if (lookout) RoomEffects.lookout(sp);
            HearthViews.refresh(sp);
            Guide.refresh(sp);
        }
    }

    /** Where the household stands in the radius: a room with nothing in it yet is still read (the first night's shelter). */
    static List<BlockPos> people(MinecraftServer server, HearthRecord h) {
        List<BlockPos> out = new ArrayList<>(2);
        for (ServerPlayer p : Household.online(server, h)) {
            if (p.level().dimension() == Level.OVERWORLD && h.contains(p.blockPosition())) out.add(p.blockPosition());
        }
        return out;
    }

    /** A Hearth went: its scan, if one is running, stops. */
    static void forgetScan(HearthRecord h) {
        if (activeScan != null && activeScan.hearth() == h) activeScan = null;
        scanQueue.remove(h.owner);
    }

    /** Starts a full scan right now (commands and tests). */
    public static List<Room> scanNow(ServerLevel level, HearthRecord h) {
        RoomScanner s = new RoomScanner(level, h, people(level.getServer(), h), scan -> onRoomsScanned(level.getServer(), h, scan));
        s.runToEnd();
        return h.rooms;
    }

    // ---- the room you stand in: the panel, and its name once as a caption ---------------------------------------------

    /** The room of a Hearth this player belongs to at their feet, if any. */
    public static Optional<Room> roomHere(ServerPlayer sp) {
        if (sp.level().dimension() != Level.OVERWORLD) return Optional.empty();
        BlockPos at = sp.blockPosition();
        Optional<HearthRecord> h = sharedAt(sp, at);
        if (h.isEmpty()) return Optional.empty();
        for (Room r : h.get().rooms) if (r.contains(at) || r.contains(at.above())) return Optional.of(r);
        return Optional.empty();
    }

    /**
     * Once a second: the room panel's data, and the first time you walk into a room after it's recognised or regraded,
     * its name as a quiet caption (AMBIENT, topic hearth_room: the director shows it when nothing else wants the screen).
     */
    static void trackRoom(ServerPlayer sp) {
        Optional<Room> here = roomHere(sp);
        Optional<HearthRecord> h = here.isEmpty() ? Optional.empty() : sharedAt(sp, sp.blockPosition());
        // (W6) a room someone lives in is theirs: "Ilse's room"
        String name = here.isPresent() && h.isPresent() ? dev.awaken.hearth.settle.Settlers.roomName(h.get(), here.get()) : "";
        dev.awaken.hearth.player.RoomHere view = here.map(r -> dev.awaken.hearth.player.RoomHere.of(r, name)).orElse(dev.awaken.hearth.player.RoomHere.NONE);
        if (!view.equals(sp.getData(HearthContent.HERE.get()))) sp.setData(HearthContent.HERE.get(), view);
        if (here.isEmpty()) return;
        tellRoom(sp, here.get(), name);
    }

    /** The room's name once as a quiet caption (again only when its grade or name changes), as the Hearth names any room. */
    public static void tellRoom(ServerPlayer sp, Room r, String name) {
        String n = name.isEmpty() ? r.reading().name() : name;
        HearthPlayer hp = player(sp);
        int as = r.reading().grade().ordinal() | (n.hashCode() << 2);
        Integer told = hp.roomsTold.get(r.key());
        if (told != null && told == as) return;
        hp.roomsTold.remove(r.key());
        hp.roomsTold.put(r.key(), as);
        while (hp.roomsTold.size() > HearthPlayer.TOLD_MAX) hp.roomsTold.remove(hp.roomsTold.keySet().iterator().next());
        dev.awaken.core.AwakenNet.send(sp, new dev.awaken.hearth.net.HomeNet.RoomCaption(n, r.reading().grade().ordinal(), r.reading().purpose().label));
    }

    static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
