package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.Marks;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.ForgeHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Upgrades;
import dev.awaken.kin.KinApi;
import dev.awaken.kin.KinBeast;
import dev.awaken.kin.KinKind;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The War Anvil's server side (SPEC §46, §46.7): the Armoury at its door, the start (the materials into the work), every
 * act judged by the server's own clock, what happens by itself when left too long, a helper's off-beats, and the finish
 * (the story, the Temper, the weapon stood in the slot) and the draw. The work only moves while its smith is at it.
 */
public final class WarAnvil {
    private WarAnvil() {}

    /** How near the Forge must be (blocks, 2 up or down). */
    public static final int REACH = 6;
    /** Stage 1's weapon (the dev command's and the tests' default), and the blade it takes. */
    public static final String WEAPON = "beastbound_longsword", BLADE = "beastbound_blade";
    /** A forging at a War Anvil standing in an Armoury room lasts this much longer (its uses), as the armour's. */
    public static final float ARMOURY_WEAR = 1.25F;

    /** Who is at which anvil now (not saved): the work moves only then. */
    private static final Map<UUID, BlockPos> AT = new HashMap<>();

    static void init() {}

    // ---- the list ----------------------------------------------------------------------------------------------------

    /**
     * Use: your own work opens where you left it; someone else's being drawn out takes a helper's strike, else says who; a
     * weapon in the slot says to draw it; else the Armoury at the anvil's door.
     */
    static void open(ServerPlayer p, WarAnvilBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        AnvilWork w = be.work();
        if (w != null) {
            if (w.forger.equals(p.getUUID())) {
                AT.put(p.getUUID(), be.getBlockPos());
                AwakenNet.send(p, new AnvilPayloads.Open(be.getBlockPos()));
            } else if (!help(level, be, w, p)) Signal.toast(p, Component.literal(name(level, w.forger) + " is forging at this anvil."));
            return;
        }
        ItemStack standing = be.slot();
        if (!standing.isEmpty()) {
            var n = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("War Anvil"));
            n.body(Component.literal("The " + standing.getHoverName().getString() + " stands in the stone. Sneak and use the anvil to draw it out."));
            Signal.send(p, n.ttl(20 * 10).build());
            return;
        }
        // any pickaxe in hand: the Hall opens on its Picks wall, and a forged or old pick's rows follow it, each to the
        // niche it makes (§49.4, §52). The view goes first: it is what opens the screen the rows are drawn on.
        if (PickAnvil.isPick(p.getMainHandItem())) {
            ArmsArmoury.openAnvil(p, be.getBlockPos());
            PickAnvil.open(p, level, be);
            return;
        }
        // one of Arms' weapons in hand: its window (temper, reforge, attune; §46.8), the Armoury a button away
        if (AnvilHeld.open(p, level, be)) return;
        ArmsArmoury.openAnvil(p, be.getBlockPos());
    }

    /** What the Armoury shows at an anvil for one style's tier: which of its buttons work, and one plain line for what's in the way. */
    public record Status(int flags, String line) {
        /** REBUILD: this tier needs the anvil rebuilt (§46.6), the door's button says so; REBUILD_OK: and you have what it takes. */
        public static final int SHOWN = 1, FORGE = 2, MEMORY = 4, MEMORY_OK = 8, BLADE = 16, REBUILD = 32, REBUILD_OK = 64, AGAIN = 128;
    }

    public static Status status(ServerPlayer p, BlockPos pos, String style, int tier) {
        if (!(p.level() instanceof ServerLevel level) || !(level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be) || tier < 0
                || tier >= ArmsArmoury.PREFIX.length) return new Status(0, "");
        int flags = Status.SHOWN;
        if (!be.slot().isEmpty()) return new Status(flags, "The " + be.slot().getHoverName().getString() + " stands in the stone. Draw it out first.");
        if (be.work() != null) return new Status(flags, name(level, be.work().forger) + " is forging at this anvil.");
        String line = missing(p, level, pos, ArmsArmoury.PREFIX[tier] + "_" + style, false);
        if (line == null) flags |= Status.FORGE;
        if (SmithMemory.of(p).remembers(style, tier)) flags |= Status.MEMORY | (line == null ? Status.MEMORY_OK : 0);
        if (dev.awaken.delve.forged.PickForm.ofStyle(style) != null) {
            if (PickAnvil.again(p, level, pos, ArmsArmoury.PREFIX[tier] + "_" + style)) flags |= Status.AGAIN;
        } else if (missing(p, level, pos, ArmsArmoury.PREFIX[tier] + "_" + AnvilWork.BLADE, false) == null) flags |= Status.BLADE;
        int build = build(be.getBlockState());
        if (build < buildFor(tier) && Progression.profile(p).level() >= ArmsArmoury.LEVEL[tier])
            flags |= Status.REBUILD | (p.isCreative() || afford(p, rebuildCost(p, build + 1)) ? Status.REBUILD_OK : 0);
        return new Status(flags, line == null ? "" : line);
    }

    /** What stands in the way of making this here, in plain words, or null. */
    static @Nullable String missing(ServerPlayer p, ServerLevel level, BlockPos anvil, String id, boolean memory) {
        if (AnvilCosts.of(id) == null) return "That isn't made at a War Anvil.";
        int tier = AnvilWork.tierOf(id);
        String style = AnvilWork.styleOf(id);
        if (tier < 0) return "That isn't made at a War Anvil.";
        if (dev.awaken.delve.forged.PickForm.ofStyle(style) != null) return PickAnvil.missing(p, level, anvil, id, memory, AnvilCosts.needs(p, id));
        if (Progression.profile(p).level() < ArmsArmoury.LEVEL[tier])
            return tierName(tier) + " weapons open at level " + ArmsArmoury.LEVEL[tier] + ".";
        if (tier > 0 && forge(level, anvil) == null) return "Metal is heated in a Forge. Set your Hearth's Forge within six blocks of this anvil.";
        String rebuild = unbuilt(tier, build(level.getBlockState(anvil)));
        if (rebuild != null) return rebuild;
        // from C a weapon forged in full has its edge ground (§46.9); from memory, or a blade alone, it hasn't
        if (tier >= 2 && !memory && !style.equals(AnvilWork.BLADE) && grindstone(level, anvil) == null)
            return "From C-rank up, a weapon's edge is ground on a grindstone. Set one within six blocks of this anvil.";
        if (!style.equals(AnvilWork.BLADE) && !ArmsApi.knows(p, style)) return "You don't know this pattern yet.";
        if (memory && !SmithMemory.of(p).remembers(style, tier)) return "You haven't forged this one well enough yet to make it from memory.";
        if (!p.isCreative() && !afford(p, AnvilCosts.needs(p, id))) return "You don't have everything it takes yet.";
        return null;
    }

    // ---- the smithy grows (SPEC §46.6) ---------------------------------------------------------------------------------

    /** The build a tier's weapons are forged on: C on Ironbound, B on the Deepglass horn, A and S on a Lord's anvil. */
    public static int buildFor(int tier) {
        return tier >= 4 ? 3 : Math.max(0, tier - 1);
    }

    public static int build(BlockState st) {
        return st.hasProperty(WarAnvilBlock.BUILD) ? st.getValue(WarAnvilBlock.BUILD) : 0;
    }

    /** What each rebuild takes (1 Ironbound, 2 the Deepglass horn, 3 the Lord's anvil). */
    static final String[][] REBUILD = {{}, {"minecraft:iron_block", "4", "minecraft:diamond", "2"},
            {"awaken:deepglass_shard", "4", "minecraft:amethyst_shard", "4"}, {"awaken:lord_heart", "1", "minecraft:netherite_ingot", "2"}};

    static List<NeedRow> rebuildCost(ServerPlayer p, int build) {
        List<NeedRow> out = new ArrayList<>();
        String[] c = REBUILD[Mth.clamp(build, 0, 3)];
        for (int i = 0; i < c.length; i += 2) out.add(new NeedRow("item", c[i], Integer.parseInt(c[i + 1]), ArmsArmoury.have(p, "item", c[i])));
        return out;
    }

    /** The step's name for the next rebuild: "Rebuild your War Anvil Ironbound". */
    static String rebuildName(int build) {
        return switch (build) {
            case 1 -> "Rebuild your War Anvil Ironbound";
            case 2 -> "Give your War Anvil a Deepglass horn";
            default -> "Rebuild your War Anvil on a Lord's Heart";
        };
    }

    /** Why this anvil can't make a tier's weapons yet, in plain words, or null when it's built for them. */
    static @Nullable String unbuilt(int tier, int build) {
        return unbuilt(tier, build, "weapons");
    }

    /** The same for what's being made: "weapons" or "picks". */
    static @Nullable String unbuilt(int tier, int build, String what) {
        int need = buildFor(tier);
        if (build >= need) return null;
        String why = switch (need) {
            case 1 -> "C-rank " + what + " are forged on an Ironbound anvil.";
            case 2 -> "B-rank " + what + (what.equals("picks") ? " are forged on a Deepglass horn." : " are balanced on a Deepglass horn.");
            default -> "A- and S-rank " + what + " are forged on a Lord's anvil.";
        };
        String how = switch (build + 1) {
            case 1 -> "Ironbound, with 4 iron blocks and 2 diamonds.";
            case 2 -> "with a Deepglass horn, from 4 Deepglass shards and 4 amethyst shards.";
            default -> "on a Lord's Heart and 2 netherite ingots.";
        };
        return why + (build + 1 < need ? " First rebuild this one " : " Rebuild this one ") + how;
    }

    /** The door's Rebuild button: the anvil a build up, its parts taken; the work and what stands in the slot stay. */
    public static boolean rebuild(ServerPlayer p, BlockPos pos) {
        if (!(p.level() instanceof ServerLevel level) || !(level.getBlockEntity(pos) instanceof WarAnvilBlockEntity)
                || p.distanceToSqr(Vec3.atCenterOf(pos)) > 10 * 10) return false;
        BlockState st = level.getBlockState(pos);
        int b = build(st);
        if (b >= 3) return false;
        List<NeedRow> cost = rebuildCost(p, b + 1);
        if (!p.isCreative()) {
            if (!afford(p, cost)) return false;
            take(p, cost);
        }
        level.setBlock(pos, st.setValue(WarAnvilBlock.BUILD, b + 1), Block.UPDATE_ALL);
        // the Lord's anvil is lit in the colour of the last Lord this smith killed (gold if none)
        if (b + 1 == 3 && level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be) be.setLord(SmithMemory.of(p).lord);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1F, 0.8F);
        level.playSound(null, pos, AnvilContent.RING.get(), SoundSource.BLOCKS, 0.9F, 0.7F + 0.15F * b);
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 24, 0.4, 0.2, 0.4, 0.2);
        Signal.toast(p, Component.literal(switch (b + 1) {
            case 1 -> "Your War Anvil is Ironbound. C-rank weapons are forged on it, their edges ground on a grindstone nearby.";
            case 2 -> "Your War Anvil has a Deepglass horn. B-rank weapons are balanced on it.";
            default -> "Your War Anvil stands on a Lord's Heart. A- and S-rank weapons are forged on it.";
        }));
        return true;
    }

    /** How near the Forge an Emberkit works its bellows. */
    static final int BELLOWS_REACH = 8;

    /**
     * Whether the Forge's bellows are worked for this smith's heat (§46.6): their home's Forge at Tier II or better, and an
     * Emberkit living with them within eight blocks of it.
     */
    static boolean bellows(ServerLevel level, ServerPlayer p, BlockPos forge) {
        var home = HearthService.homeOf(p);
        if (home.isEmpty() || home.get().forgeTier < 2) return false;
        for (KinBeast b : KinApi.loaded(level, home.get().pos, KinKind.EMBERKIT))
            if (b.blockPosition().closerThan(forge, BELLOWS_REACH)) return true;
        return false;
    }

    /** A Gate Lord killed: its smith remembers its colour (its Lord-forged weapon's accent) for a Lord's anvil (§46.6). */
    static void lordFell(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        MobRole role = MobRole.of(e.getEntity());
        if (role.role() != MobRole.Role.BOSS || role.bossId().isEmpty()) return;
        LordForged.Forged f = LordForged.byBoss(role.bossId().get());
        ArmsData.Weapon w = f == null ? null : ArmsItems.data(f.weapon());
        if (w != null) SmithMemory.of(p).lord = w.trail() & 0xFFFFFF;
    }

    /** The nearest grindstone within six blocks of the anvil (two up or down), or null. */
    static @Nullable BlockPos grindstone(Level level, BlockPos anvil) {
        BlockPos best = null;
        double d = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.betweenClosed(anvil.offset(-REACH, -2, -REACH), anvil.offset(REACH, 2, REACH))) {
            if (!level.getBlockState(b).is(Blocks.GRINDSTONE) || b.distSqr(anvil) >= d) continue;
            d = b.distSqr(anvil);
            best = b.immutable();
        }
        return best;
    }

    static String tierName(int tier) {
        String s = ArmsArmoury.PREFIX[tier];
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Component label(NeedRow n) {
        if (n.kind().equals("tag")) {
            Identifier id = Identifier.parse(n.id());
            return Component.translatable("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id()))).getHoverName();
    }

    // ---- what it costs -----------------------------------------------------------------------------------------------

    private static Predicate<ItemStack> matcher(NeedRow n) {
        if (n.kind().equals("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(n.id()));
            return s -> s.is(tag);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id()));
        return s -> s.is(item);
    }

    /** Whether the pack covers every part at once (a stack counted for one part isn't counted for the next). */
    static boolean afford(ServerPlayer p, List<NeedRow> cost) {
        List<ItemStack> inv = p.getInventory().getNonEquipmentItems();
        int[] left = new int[inv.size()];
        for (int i = 0; i < left.length; i++) left[i] = inv.get(i).getCount();
        for (NeedRow n : cost) {
            Predicate<ItemStack> m = matcher(n);
            int need = n.need();
            for (int i = 0; i < left.length && need > 0; i++) {
                if (left[i] <= 0 || !m.test(inv.get(i))) continue;
                int t = Math.min(need, left[i]);
                left[i] -= t;
                need -= t;
            }
            if (need > 0) return false;
        }
        return true;
    }

    /** Takes every part out of the pack, returning what it took (given back if the anvil is broken mid-work). */
    static List<ItemStack> take(ServerPlayer p, List<NeedRow> cost) {
        List<ItemStack> taken = new ArrayList<>();
        List<ItemStack> inv = p.getInventory().getNonEquipmentItems();
        for (NeedRow n : cost) {
            Predicate<ItemStack> m = matcher(n);
            int need = n.need();
            for (int i = 0; i < inv.size() && need > 0; i++) {
                ItemStack s = inv.get(i);
                if (s.isEmpty() || !m.test(s)) continue;
                taken.add(s.split(Math.min(need, s.getCount())));
                need -= taken.getLast().getCount();
            }
        }
        return taken;
    }

    // ---- the place ---------------------------------------------------------------------------------------------------

    /** The nearest Forge within reach of the anvil (a small cube, read at a use or a start). */
    public static @Nullable BlockPos forge(Level level, BlockPos anvil) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Identifier id = Awaken.id("forge");
        for (BlockPos q : BlockPos.betweenClosed(anvil.offset(-REACH, -2, -REACH), anvil.offset(REACH, 2, REACH))) {
            if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(q).getBlock()).equals(id)) continue;
            double d = q.distSqr(anvil);
            if (d < bestD) {
                bestD = d;
                best = q.immutable();
            }
        }
        return best;
    }

    // ---- the song ----------------------------------------------------------------------------------------------------

    /** The Armoury's Forge it, From memory or Forge the blade at this anvil: false if it can't begin (the screen says why). */
    public static boolean begin(ServerPlayer p, BlockPos pos, String id, boolean memory) {
        return p.level() instanceof ServerLevel level && dev.awaken.core.api.Ready.loaded(level, pos) && level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be
                && begin(p, level, be, id, memory);
    }

    static boolean begin(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be, String id, boolean memory) {
        if (be.work() != null || !be.slot().isEmpty() || p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 10 * 10) {
            open(p, be);
            return false;
        }
        if (missing(p, level, be.getBlockPos(), id, memory) != null) return false;
        boolean hand = AnvilWork.tierOf(id) == 0;
        BlockPos fire = hand ? be.getBlockPos() : forge(level, be.getBlockPos());
        if (fire == null) return false;
        List<ItemStack> taken = p.isCreative() ? List.of() : take(p, AnvilCosts.needs(p, id));
        AnvilWork w = AnvilWork.fresh(id, level.getGameTime(), taken, p.getUUID(), fire, memory ? AnvilWork.MEMORY : AnvilWork.FORGE);
        w.bellows = !hand && bellows(level, p, fire);
        AT.put(p.getUUID(), be.getBlockPos());
        SmithMemory.of(p).at(level.dimension(), be.getBlockPos());
        be.setWork(w);
        if (!hand) level.playSound(null, fire, AnvilContent.BELLOWS.get(), SoundSource.BLOCKS, 0.9F, 1F);
        AwakenNet.send(p, new AnvilPayloads.Open(be.getBlockPos()));
        return true;
    }

    /** Stage 1's start (the dev command and tests): the Beastbound Longsword, forged in full. */
    static void start(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be) {
        begin(p, level, be, WEAPON, false);
    }

    public static void onAct(ServerPlayer p, AnvilPayloads.Act a) {
        if (!(p.level() instanceof ServerLevel level) || !dev.awaken.core.api.Ready.loaded(level, a.pos())) return;
        if (!(level.getBlockEntity(a.pos()) instanceof WarAnvilBlockEntity be)) return;
        if (p.distanceToSqr(Vec3.atCenterOf(a.pos())) > 10 * 10) return;
        AnvilWork w = be.work();
        long now = level.getGameTime();
        switch (a.act()) {
            case AnvilPayloads.LEAVE -> AT.remove(p.getUUID());
            case AnvilPayloads.RESUME -> {
                if (w == null || !mine(p, w)) return;
                AT.put(p.getUUID(), a.pos());
                w.begin(w.stage, now); // the metal cooled while you were away: the stage counts you in again
                be.changed();
            }
            case AnvilPayloads.PULL -> {
                if (w == null || w.stage != AnvilSong.HEAT || !mine(p, w)) return;
                pull(level, be, w, w.heat(clamp(a.t(), now - w.stageStart)), p);
            }
            case AnvilPayloads.STRIKE, AnvilPayloads.TAP -> {
                if (w == null || !mine(p, w) || a.a() < w.beats().size()) return;
                // a pick's stages are struck the same way: the eye and the tips aimed, the hanging and quiet work tapped (§49.4)
                boolean aimed = a.act() == AnvilPayloads.STRIKE;
                int stage = w.stage;
                if (aimed ? stage != AnvilSong.SHAPE && stage != AnvilSong.EYE && stage != AnvilSong.TIPS
                        : stage != AnvilSong.HILT && stage != AnvilSong.HANG && stage != AnvilSong.TEND) return;
                float t = clamp(a.t(), now - w.stageStart);
                // beats the smith's clock saw pass unstruck, not yet missed here (the server waits out the lag first)
                while (w.stage == stage && be.work() == w && w.beats().size() < a.a() && t > w.nextDue() + AnvilSong.WINDOW) beat(level, be, w, 0F, null);
                if (be.work() != w || w.stage != stage || a.a() != w.beats().size()) return;
                float off = t - w.nextDue();
                if (off < -AnvilSong.WINDOW) return; // far ahead of its beat: not taken for it
                float score = aimed ? AnvilSong.strike(off, Mth.clamp(a.aim(), 0F, 9F)) : AnvilSong.timing(off);
                beat(level, be, w, score, p);
            }
            case AnvilPayloads.PLUNGE -> {
                // a pick's colours and seam are held and let go, as a quench is
                boolean held = w != null && (w.stage == AnvilSong.COLOURS || w.stage == AnvilSong.SEAM);
                if (w == null || w.stage != AnvilSong.QUENCH && !held || !mine(p, w) || w.plunge >= 0F) return;
                w.plunge = clamp(a.t(), now - w.stageStart);
                level.playSound(p, a.pos(), held ? PickAnvil.holdSound(w) : AnvilContent.QUENCH.get(), SoundSource.BLOCKS, 1F, 1F);
                be.changed();
            }
            case AnvilPayloads.LIFT -> {
                if (w == null || !mine(p, w) || w.plunge < 0F) return;
                float heldFor = Math.max(0F, clamp(a.t(), now - w.stageStart) - w.plunge);
                if (w.stage == AnvilSong.COLOURS || w.stage == AnvilSong.SEAM) PickAnvil.released(level, be, w, heldFor, p);
                else if (w.stage == AnvilSong.QUENCH) lift(level, be, w, heldFor, p);
            }
            case AnvilPayloads.KNAP -> {
                if (w == null || w.stage != AnvilSong.KNAP || !mine(p, w) || a.a() != w.scores.size()) return;
                float t = clamp(a.t(), now - w.stageStart);
                if (w.knapLast >= 0F && t - w.knapLast < AnvilSong.KNAP_GAP) return; // struck too soon after the last: not taken
                w.knapLast = t;
                knap(level, be, w, AnvilSong.knap(Mth.clamp(a.aim(), 0F, 9F)), p);
            }
            case AnvilPayloads.WIND -> {
                // a = 0 as the hold begins, then each turn done (1 to 4), at t ticks from the stage's start
                if (w == null || w.stage != AnvilSong.BIND || !mine(p, w) || a.a() != w.taps.size()) return;
                float t = clamp(a.t(), now - w.stageStart);
                w.taps.add(w.taps.isEmpty() ? t : Math.max(w.taps.getLast(), t));
                if (w.taps.size() > AnvilSong.TURNS) bind(level, be, w, p);
                else be.changed();
            }
            case AnvilPayloads.RELEASE -> {
                if (w == null || w.stage != AnvilSong.BIND && w.stage != AnvilSong.TWIST || !mine(p, w) || w.taps.isEmpty()) return;
                // let go before the four (the three): what was wound, or twisted
                if (w.stage == AnvilSong.BIND) bind(level, be, w, p);
                else twisted(level, be, w, p);
            }
            case AnvilPayloads.TWIST -> {
                // a = 0 as the hold begins, then each turn done (1 to 3)
                if (w == null || w.stage != AnvilSong.TWIST || !mine(p, w) || a.a() != w.taps.size()) return;
                float t = clamp(a.t(), now - w.stageStart);
                w.taps.add(w.taps.isEmpty() ? t : Math.max(w.taps.getLast(), t));
                if (w.taps.size() > AnvilSong.TWISTS) twisted(level, be, w, p);
                else be.changed();
            }
            case AnvilPayloads.GRIND -> {
                if (w == null || w.stage != AnvilSong.GRIND && w.stage != AnvilSong.SPINE || !mine(p, w) || a.a() != w.taps.size()) return;
                float t = clamp(a.t(), now - w.stageStart);
                if (w.grindLast >= 0F && t - w.grindLast < AnvilSong.GRIND_GAP - 1F) return; // faster than the wheel turns: not taken
                w.grindLast = t;
                ground(level, be, w, AnvilSong.grind(Mth.clamp(a.aim(), -2F, 2F)), p);
            }
            case AnvilPayloads.BALANCE -> {
                if (w == null || w.stage != AnvilSong.BALANCE || !mine(p, w)) return;
                w.balance = AnvilSong.balance(clamp(a.t(), now - w.stageStart), Mth.clamp(a.aim(), -1F, 1F));
                level.playSound(p, be.getBlockPos(), AnvilContent.RING.get(), SoundSource.BLOCKS, 0.8F, AnvilSong.truePitch(w.song));
                finish(level, be, w);
            }
            case AnvilPayloads.STAR -> {
                if (w == null || w.stage != AnvilSong.STAR || !mine(p, w)) return;
                // the light is its water: no hairline from holding it too long
                w.star = AnvilSong.star(clamp(a.t(), now - w.stageStart) - AnvilSong.STAR_AT);
                level.playSound(p, be.getBlockPos(), AnvilContent.STAR.get(), SoundSource.BLOCKS, 1F, 1F);
                if (w.pick) {
                    PickAnvil.advance(level, be, w);
                    return;
                }
                w.quench = w.star;
                w.held = -1F;
                quenched(level, be, w);
            }
            case AnvilPayloads.DRAW -> {
                if (w == null) draw(p, be);
            }
            case AnvilPayloads.HELD -> {
                // refused, the window comes again with its lines as they are now
                if (PickAnvil.holds(p.getMainHandItem())) {
                    if (!PickAnvil.begin(p, level, be, a.a()) && be.work() == null && be.slot().isEmpty()) PickAnvil.open(p, level, be);
                } else if (!AnvilHeld.begin(p, level, be, a.a()) && be.work() == null && be.slot().isEmpty()) AnvilHeld.open(p, level, be);
            }
            case AnvilPayloads.CANCEL -> {
                if (w != null && mine(p, w)) PickAnvil.cancel(p, be, w);
            }
            case AnvilPayloads.LIST -> {
                if (w == null && be.slot().isEmpty()) ArmsArmoury.openAnvil(p, a.pos());
            }
            default -> {}
        }
    }

    private static boolean mine(ServerPlayer p, AnvilWork w) {
        return w.forger.equals(p.getUUID());
    }

    /** A client's ticks from the stage's start, held to what the server's clock allows (a little behind, never ahead). */
    static float clamp(float client, long server) {
        return Mth.clamp(client, server - AnvilSong.LAG, server + 2F);
    }

    /** The smith hears their own pull, strikes and quench as they make them (their client plays them); everyone else from here. */
    static void pull(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float heat, @Nullable ServerPlayer smith) {
        w.heat = heat;
        if (w.pick) {
            level.playSound(smith, w.forge, AnvilContent.PULL.get(), SoundSource.BLOCKS, 1F, 1F);
            PickAnvil.advance(level, be, w);
            return;
        }
        // attuning, the hot steel goes straight to the stone's three taps in the guard; from A the bar is twisted first (§46.9)
        w.begin(w.kind == AnvilWork.ATTUNE ? AnvilSong.HILT : w.acts() && w.tier >= 4 ? AnvilSong.TWIST : AnvilSong.SHAPE, level.getGameTime());
        level.playSound(smith, w.forge, AnvilContent.PULL.get(), SoundSource.BLOCKS, 1F, 1F);
        be.changed();
    }

    /** A beat struck (the draw out's strikes, the hilt's taps) or missed (0). */
    static void beat(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float score, @Nullable ServerPlayer smith) {
        if (w.pick) {
            PickAnvil.beat(level, be, w, score, smith);
            return;
        }
        List<Float> l = w.beats();
        l.add(score);
        boolean hilt = w.stage == AnvilSong.HILT;
        BlockPos at = be.getBlockPos();
        if (score > 0F) {
            level.playSound(smith, at, hilt ? AnvilContent.TAP.get() : AnvilContent.strike(w.tier), SoundSource.BLOCKS, hilt ? 0.8F : 1F,
                    (hilt ? 1F : 0.95F) + level.getRandom().nextFloat() * 0.1F);
            if (!hilt && AnvilSong.clean(score))
                level.playSound(smith, at, AnvilContent.RING.get(), SoundSource.BLOCKS, 0.7F, AnvilSong.ringPitch(w.song, AnvilSong.cleanOf(l, l.size()), w.beatsNeeded()));
        } else level.playSound(null, at, AnvilContent.MISS.get(), SoundSource.BLOCKS, 0.6F, 1F);
        if (!hilt && !w.memory() && l.size() == w.song.flipAfter()) level.playSound(smith, at, AnvilContent.FLIP.get(), SoundSource.BLOCKS, 0.8F, 1F);
        if (l.size() >= w.beatsNeeded()) {
            // from B the hilt's on, then the balance on the horn; at S the light is its water (§46.9)
            if (hilt && w.acts() && w.tier >= 3) {
                w.begin(AnvilSong.BALANCE, level.getGameTime());
                level.playSound(smith, at, AnvilContent.HUM.get(), SoundSource.BLOCKS, 0.8F, 1F);
            }
            else if (hilt) {
                finish(level, be, w);
                return;
            } else w.begin(w.acts() && w.tier >= 5 ? AnvilSong.STAR : AnvilSong.QUENCH, level.getGameTime());
            be.changed();
            return;
        }
        be.changed();
    }

    /** Each clean strike rings the blade a step nearer its true note, by the last of all it takes (both sides use it). */
    /** A finished weapon's true note (its style's, §46.10), the pitch it rings at as it's drawn from the stone. */
    public static float truePitch(ItemStack weapon) {
        var pick = dev.awaken.delve.forged.ForgedPicks.item(weapon);
        if (pick != null) return AnvilSong.truePitch(AnvilSong.song(pick.form.style()));
        var data = ArmsItems.of(weapon.getItem());
        return AnvilSong.truePitch(AnvilSong.song(data == null ? "longsword" : data.style()));
    }

    static void lift(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float held, @Nullable ServerPlayer smith) {
        w.held = held;
        w.quench = AnvilSong.quench(held);
        level.playSound(smith, be.getBlockPos(), AnvilContent.LIFT.get(), SoundSource.BLOCKS, 0.9F, 1F);
        quenched(level, be, w);
    }

    /** Out of the trough: the hilt next, or from memory (or a blade alone, which has none, or a whole weapon) straight to the stone. */
    static void quenched(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        if (w.memory() || w.style.equals(AnvilWork.BLADE) || w.whole()) {
            finish(level, be, w);
            return;
        }
        // from C the edge is ground on the wheel before the hilt goes on (§46.9)
        w.taps.clear();
        w.begin(w.acts() && w.tier >= 2 ? AnvilSong.GRIND : AnvilSong.HILT, level.getGameTime());
        be.changed();
    }

    /** The bar's twist (A): how even the turns were of three, held long enough to be real; then it's drawn out. */
    static void twisted(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, @Nullable ServerPlayer smith) {
        int turns = Math.min(AnvilSong.TWISTS, w.taps.size() - 1);
        float[] each = new float[AnvilSong.TWISTS];
        for (int k = 1; k <= turns; k++) each[k - 1] = w.taps.get(k) - w.taps.get(k - 1);
        float held = turns > 0 ? w.taps.get(turns) - w.taps.getFirst() : 0F;
        w.twist = AnvilSong.twist(AnvilSong.evenness(each, turns), turns, held);
        level.playSound(smith, be.getBlockPos(), AnvilContent.TWIST.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
        w.taps.clear();
        w.begin(AnvilSong.SHAPE, level.getGameTime());
        be.changed();
    }

    /** A grind sample (C): the two passes' twenty, then the hilt. */
    static void ground(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float score, @Nullable ServerPlayer smith) {
        w.taps.add(score);
        if (w.stage == AnvilSong.SPINE) {
            PickAnvil.threaded(level, be, w, smith);
            return;
        }
        if (w.taps.size() % 2 == 0) level.playSound(smith, be.getBlockPos(), AnvilContent.WHEEL.get(), SoundSource.BLOCKS, 0.7F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        if (w.taps.size() >= AnvilSong.GRIND_SAMPLES) {
            w.grind = AnvilWork.mean(w.taps);
            w.taps.clear();
            w.begin(AnvilSong.HILT, level.getGameTime());
        }
        be.changed();
    }

    /** A flake struck off the edge (E): the knap's eight, then the bind (from memory, or a blade alone, one knap and done). */
    static void knap(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float score, @Nullable ServerPlayer smith) {
        w.scores.add(score);
        level.playSound(smith, be.getBlockPos(), AnvilContent.KNAP.get(), SoundSource.BLOCKS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        if (w.scores.size() >= w.beatsNeeded()) {
            if (w.memory() || w.style.equals(AnvilWork.BLADE)) finish(level, be, w);
            else {
                w.begin(AnvilSong.BIND, level.getGameTime());
                be.changed();
            }
            return;
        }
        be.changed();
    }

    /** The cord wound (E): how even the turns were, what share of the four, and whether the hold was long enough to be real. */
    static void bind(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, @Nullable ServerPlayer smith) {
        int turns = Math.min(AnvilSong.TURNS, w.taps.size() - 1);
        float[] each = new float[AnvilSong.TURNS];
        for (int k = 1; k <= turns; k++) each[k - 1] = w.taps.get(k) - w.taps.get(k - 1);
        float held = turns > 0 ? w.taps.get(turns) - w.taps.getFirst() : 0F;
        w.bind = AnvilSong.bind(AnvilSong.evenness(each, turns), turns, held);
        level.playSound(smith, be.getBlockPos(), AnvilContent.CORD.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
        if (w.pick) {
            PickAnvil.advance(level, be, w);
            return;
        }
        finish(level, be, w);
    }

    /**
     * Someone else uses the anvil while it's drawn out: a strike between the smith's beats (at least HELP_OFF from any,
     * HELP_GAP from the last) goes into the blade; on a beat it only clanks. True if it was taken as a helper's use.
     */
    static boolean help(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, ServerPlayer helper) {
        if (w.stage != AnvilSong.SHAPE || w.kind != AnvilWork.FORGE && w.kind != AnvilWork.REFORGE && w.kind != AnvilWork.RAISE && w.kind != AnvilWork.REDRAW) return false;
        long now = level.getGameTime();
        if (now - w.helpLast < AnvilSong.HELP_GAP) return true;
        AnvilSong.Rhythm r = w.rhythm();
        float t = now - w.stageStart;
        if (t < r.length()) return false; // still counting in
        int k = w.beats().size() - w.strikeBase;
        float next = r.due(k), prev = k > 0 ? r.due(k - 1) : r.countIn(r.beats() - 1);
        BlockPos at = be.getBlockPos();
        helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        if (Math.abs(t - next) < AnvilSong.HELP_OFF || Math.abs(t - prev) < AnvilSong.HELP_OFF) {
            level.playSound(null, at, AnvilContent.MISS.get(), SoundSource.BLOCKS, 0.5F, 1.2F);
            return true;
        }
        w.helpLast = now;
        w.help++;
        level.playSound(null, at, AnvilContent.strike(w.tier), SoundSource.BLOCKS, 0.8F, 1.15F);
        level.playSound(null, at, AnvilContent.RING.get(), SoundSource.BLOCKS, 0.5F, AnvilSong.truePitch(w.song) * 1.06F);
        if (w.help == AnvilSong.TWIN_STRIKES) Signal.toast(helper, Component.literal("Your strikes are in the blade. It will come out twin-struck."));
        be.changed();
        return true;
    }

    /** Each tick of an anvil with work on it: only while its smith is at it, anything left too long happens by itself. */
    static void tick(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        BlockPos at = AT.get(w.forger);
        ServerPlayer smith = level.getServer().getPlayerList().getPlayer(w.forger);
        if (at == null || !at.equals(be.getBlockPos()) || smith == null || smith.level() != level
                || smith.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 12 * 12) return;
        float t = level.getGameTime() - w.stageStart;
        switch (w.stage) {
            case AnvilSong.HEAT -> {
                if (w.heat(t) > AnvilSong.BURNT) pull(level, be, w, AnvilSong.BURNT, null);
            }
            case AnvilSong.SHAPE, AnvilSong.HILT, AnvilSong.EYE, AnvilSong.TIPS, AnvilSong.HANG, AnvilSong.TEND -> {
                if (t > w.nextDue() + AnvilSong.WINDOW + AnvilSong.LAG) beat(level, be, w, 0F, null);
            }
            case AnvilSong.QUENCH -> {
                if (w.plunge >= 0F && t - w.plunge > AnvilSong.HOLD_MAX + AnvilSong.LAG) lift(level, be, w, AnvilSong.HOLD_MAX, null);
                else if (w.plunge < 0F && t > AnvilSong.PLUNGE_LATE + AnvilSong.LAG) {
                    // never plunged: it cools in the air, soft but whole
                    w.quench = AnvilSong.AIR;
                    quenched(level, be, w);
                }
            }
            // the ranks' acts (§46.9) left alone settle themselves, poorly
            case AnvilSong.BALANCE -> {
                if (t > AnvilSong.BALANCE_ALONE + AnvilSong.LAG) {
                    w.balance = AnvilSong.balance(AnvilSong.BALANCE_ALONE, 1F);
                    finish(level, be, w);
                }
            }
            case AnvilSong.STAR -> {
                if (t > AnvilSong.STAR_ALONE + AnvilSong.LAG) {
                    w.star = AnvilSong.star(AnvilSong.STAR_ALONE - AnvilSong.STAR_AT);
                    if (w.pick) {
                        PickAnvil.advance(level, be, w);
                        return;
                    }
                    w.quench = w.star;
                    w.held = -1F;
                    quenched(level, be, w);
                }
            }
            case AnvilSong.COLOURS, AnvilSong.SEAM -> PickAnvil.waited(level, be, w, t);
            default -> {}
        }
    }

    // ---- the finished weapon -----------------------------------------------------------------------------------------

    /**
     * Done flags: a helper's strikes made it twin-struck, it was made from memory, it lasts longer for the Armoury room; a
     * weapon worked was tempered, attuned or reforged (§46.8).
     */
    public static final int TWIN = 1, FROM_MEMORY = 2, ARMOURY = 4, TEMPERED = 8, ATTUNED = 16, REFORGED = 32, RIPPLED = 64, STARLIT = 128;

    static void finish(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        ServerPlayer smith = level.getServer().getPlayerList().getPlayer(w.forger);
        if (w.pick) {
            PickAnvil.finish(level, be, w, smith);
            return;
        }
        if (w.whole()) {
            AnvilHeld.finish(level, be, w, smith);
            return;
        }
        boolean twin = w.help >= AnvilSong.TWIN_STRIKES;
        int flaw = AnvilSong.NO_FLAW;
        AnvilSong.Quality q;
        if (w.memory()) q = AnvilSong.Quality.FINE; // from memory: always Fine, never flawed
        else if (w.hand()) {
            float knap = AnvilWork.mean(w.scores), bound = w.style.equals(AnvilWork.BLADE) ? knap : Math.max(0F, w.bind); // a blade alone isn't bound
            q = AnvilSong.quality(AnvilSong.helped(AnvilSong.handScore(knap, bound), w.help), false);
        }
        else {
            float heat = Math.max(0F, w.heat);
            flaw = AnvilSong.burnt(heat) ? AnvilSong.BURNT_TIP : w.held >= 0F && AnvilSong.brittle(w.held) ? AnvilSong.BRITTLE : AnvilSong.NO_FLAW;
            float score = AnvilSong.withActs(AnvilSong.score(heat, AnvilWork.mean(w.scores), Math.max(0F, w.quench), AnvilWork.mean(w.taps)),
                    w.twist, w.grind, w.balance, w.star);
            q = AnvilSong.quality(AnvilSong.helped(score, w.help), flaw != AnvilSong.NO_FLAW);
        }
        ItemStack made = new ItemStack(item(w.weapon));
        be.setWork(null);
        if (made.isEmpty()) return;
        String maker = smith != null ? smith.getGameProfile().name() : "";
        // what the rank's acts leave in the steel (§46.9): a well-twisted A blade ripples, an S blade's runes wake in the starlight
        int acts = (w.twist >= AnvilSong.RIPPLES ? WeaponForged.RIPPLES : 0) | (w.star >= 0F ? WeaponForged.STARLIT : 0);
        made.set(AnvilContent.FORGED.get(), new WeaponForged(WeaponForged.SCHEMA, q.ordinal(), flaw, maker, twin, w.memory(), false, acts));
        int cap = smith != null ? HearthHooks.temperCap(smith, w.forge) : 1;
        int mark = smith != null ? (int) Progression.perk(smith, "awaken:temper_start") : 0;
        int temper = AnvilSong.temper(q, mark, cap);
        if (temper > 0) Upgrades.applyTemper(made, temper);
        boolean armoury = made.isDamageableItem() && HearthHooks.armoury(level, be.getBlockPos());
        if (armoury) made.set(DataComponents.MAX_DAMAGE, Math.round(made.getMaxDamage() * ARMOURY_WEAR));
        boolean blade = w.style.equals(AnvilWork.BLADE);
        if (smith != null) {
            if (!blade) {
                Progression.count(smith, "awaken:weapon_forged");
                SmithMemory.of(smith).forged(w.style, w.tier, q);
            }
            ForgeHooks.forged(smith, made, q.ordinal(), "war_anvil");
        }
        be.setSlot(made);
        // from the Deepglass horn up, a copy goes up in the anvil's rack (§46.6)
        if (!blade && build(be.getBlockState()) >= 2) be.racked(made);
        level.playSound(null, be.getBlockPos(), AnvilContent.FLIP.get(), SoundSource.BLOCKS, 1F, 0.7F);
        if (smith != null) AwakenNet.send(smith, new AnvilPayloads.Done(be.getBlockPos(), q.ordinal(), flaw, temper,
                (twin ? TWIN : 0) | (w.memory() ? FROM_MEMORY : 0) | (armoury ? ARMOURY : 0)
                        | ((acts & WeaponForged.RIPPLES) != 0 ? RIPPLED : 0) | ((acts & WeaponForged.STARLIT) != 0 ? STARLIT : 0)));
    }

    /**
     * A weapon of ours held with no story (made on the grid before the War Anvil, or Hale's issue) gains a Plain one, no
     * maker (SPEC §46.7); a found one (Marked, or Lord-forged) never does.
     */
    static void storyOnHold(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        if (s.isEmpty() || s.has(AnvilContent.FORGED.get())) return;
        var data = ArmsItems.of(s.getItem());
        if (data == null || LordForged.byWeapon(data.id()) != null || !Marks.of(s).marks().isEmpty()) return;
        s.set(AnvilContent.FORGED.get(), WeaponForged.PLAIN);
    }

    /** Draws what stands in the slot into the main hand (what was there goes to the pack, else at your feet). */
    static void draw(ServerPlayer p, WarAnvilBlockEntity be) {
        ItemStack s = be.slot();
        if (s.isEmpty() || be.work() != null) return;
        be.setSlot(ItemStack.EMPTY);
        ItemStack old = p.getMainHandItem();
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        if (!old.isEmpty() && !p.getInventory().add(old)) Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), old);
        p.level().playSound(p, be.getBlockPos(), AnvilContent.DRAWN.get(), SoundSource.BLOCKS, 1F, truePitch(s));
        AT.remove(p.getUUID());
    }

    // ---- shared ------------------------------------------------------------------------------------------------------

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Awaken.id(id));
    }

    private static String name(ServerLevel level, UUID id) {
        ServerPlayer o = level.getServer().getPlayerList().getPlayer(id);
        return o == null ? "Someone" : o.getGameProfile().name();
    }

    static void forget(UUID player) {
        AT.remove(player);
    }

    /** Whether a smith is at this anvil now (tests). */
    public static boolean at(UUID player, BlockPos anvil) {
        return anvil.equals(AT.get(player));
    }

    /** Puts a smith at an anvil (tests and the tour's start, as a use would). */
    static void arrive(UUID player, BlockPos anvil) {
        AT.put(player, anvil);
    }
}
