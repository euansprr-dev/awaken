package dev.awaken.kin;

import dev.awaken.delve.LodeHeartBlock;
import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The kin at home (SPEC §43.6): nooks (2 at the Ember, 4 at the Anchor, then 8, 12, 18, 24, and two more for
 * Pebblepups once there's an Ore Shed), following a player home and settling, and each kind's work. Beasts do the boring
 * work only: a Lampwing lights the yard at night and nothing from the dark spawns near its roost; a Burrowbun tends the
 * fields (harvests what's ripe, plants it again, carries the harvest to the field's chest); a Mossback makes crops near
 * it grow faster; Pebblepups sort and crush the ore and rubble you put in the Ore Shed's barrels, never making ore by
 * themselves. Also the first fire's Lampwing, the gentle lesson (JOURNEY part 3).
 */
public final class KinHome {
    private KinHome() {}

    static final int[] NOOKS = {2, 4, 8, 12, 18, 24};
    /** Pebblepups' own nooks, once the Ore Shed stands. */
    static final int SHED_NOOKS = 2;
    /** Fields a Burrowbun tends round its nook; a Mossback's reach. */
    static final int FIELD = 14, MOSS = 8;
    /** A Pebblepup works one item off the Ore Shed's barrels this often (ticks). */
    static final int SHED_EVERY = 40;
    /** Nuggets the sorting finds: one in this many rubble crushed, one in this many gravel sorted. */
    static final int NUGGET_PER = 32;

    // ---- nooks --------------------------------------------------------------------------------------------------------

    public static int nooks(ServerLevel level, Hearth.HearthInfo h) {
        int n = NOOKS[Math.max(0, Math.min(NOOKS.length - 1, h.tier()))];
        if (KinWorld.get(level.getServer()).oreHeart(h.anchor()) != null) n += SHED_NOOKS;
        return n;
    }

    /** Where at this home a kind would live, or null when nothing there suits it. */
    static @Nullable BlockPos nookFor(ServerLevel level, Hearth.HearthInfo h, KinKind k) {
        BlockPos a = h.anchor();
        int r = Math.min(h.radius(), 40);
        switch (k) {
            case LAMPWING -> {
                // the Hearthfire is a warm light: it roosts by it
                return a.offset(2, 1, 0);
            }
            case PEBBLEPUP -> {
                BlockPos heart = KinWorld.get(level.getServer()).oreHeart(a);
                if (heart != null) return heart.above();
                return scan(level, a, r, (s, top) -> s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE));
            }
            case BURROWBUN -> {
                return scan(level, a, r, (s, top) -> s.is(Blocks.FARMLAND));
            }
            case MOSSBACK -> {
                return scan(level, a, r, (s, top) -> s.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && level.canSeeSky(top.above()));
            }
            default -> {
                return null;
            }
        }
    }

    private interface Test {
        boolean ok(BlockState surface, BlockPos at);
    }

    /** The first surface column round the Anchor (every other column, rings outward) whose top passes. */
    private static @Nullable BlockPos scan(ServerLevel level, BlockPos a, int r, Test t) {
        for (int ring = 2; ring <= r; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = a.getX() + dx, z = a.getZ() + dz;
                    if (!level.hasChunkAt(new BlockPos(x, a.getY(), z))) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                    BlockPos at = new BlockPos(x, y, z);
                    if (t.ok(level.getBlockState(at), at)) return at.above();
                }
            }
        }
        return null;
    }

    // ---- going home -------------------------------------------------------------------------------------------------

    /** A trusted beast and its player: it follows them home if their Hearth has a nook it likes, else says why not. */
    static void offer(KinBeast b, ServerPlayer p) {
        if (b.home != null) return;
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) {
            KinWatch.say(p, "[It would come home with you, if you had a home out past the ridge.]", 8);
            return;
        }
        ServerLevel level = p.level();
        if (KinWorld.get(level.getServer()).at(h.get().anchor()).size() >= nooks(level, h.get())) {
            KinWatch.say(p, "[It would come, but every nook at your home is taken. A bigger Hearth has more.]", 8);
            return;
        }
        if (nookFor(level, h.get(), b.kind()) == null) {
            KinWatch.say(p, "[It would come, but there's nowhere at your home it would like to live. It needs " + b.kind().nook + ".]", 10);
            return;
        }
        b.follow = p.getUUID();
        KinWatch.say(p, "[It's going to follow you home.]", 6);
    }

    /** Following and come home: it looks round and settles in its nook. */
    static void arrive(KinBeast b, ServerPlayer p) {
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) return;
        BlockPos a = h.get().anchor();
        if (b.blockPosition().distSqr(a) > (double) h.get().radius() * h.get().radius() || !Hearth.atHome(p)) return;
        ServerLevel level = p.level();
        BlockPos nook = nookFor(level, h.get(), b.kind());
        if (nook == null) return;
        settle(level, b, a, nook, p);
        KinWatch.say(p, "[The " + b.kind().title + " looked round your home and settled in. It starts work in the morning.]", 10);
    }

    static void settle(ServerLevel level, KinBeast b, BlockPos anchor, BlockPos nook, ServerPlayer p) {
        b.settle(anchor, nook, p.getUUID());
        KinWorld.get(level.getServer()).home(anchor, new KinWorld.Homed(b.getUUID(), b.kind(), nook, level.getGameTime()));
        Progression.count(p, "awaken:kin_homed");
        Kinship ks = Kinship.of(p);
        if (b.kind() == KinKind.LAMPWING && ks.lesson == 1) {
            ks.lesson = 2;
            Kinship.save(p, ks);
        }
    }

    // ---- the first fire's Lampwing (the lesson) -----------------------------------------------------------------------

    /** Whether this is a first fire's Lampwing still teaching (it's gentle, quick to read, quick to trust). */
    static boolean lesson(KinBeast b) {
        return b.entityTags().contains("awaken_kin_lesson") && b.home == null;
    }

    /** Once, on a night at a new home: a Lampwing comes out of the trees to the light and sits on a stone by the fire. */
    static void lessonTick(ServerPlayer p) {
        Kinship ks = Kinship.of(p);
        if (ks.lesson != 0) return;
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD || !KinWatch.night(level)) return;
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty() || !h.get().owner().equals(p.getUUID())) return;
        BlockPos a = h.get().anchor();
        if (p.blockPosition().distSqr(a) > 20 * 20) return;
        KinBeast b = spawn(level, KinKind.LAMPWING, a.offset(3, 1, 2), EntitySpawnReason.EVENT);
        if (b == null) return;
        b.addTag("awaken_kin_lesson");
        b.snapTo(a.getX() + 12.5, a.getY() + 4, a.getZ() + 9.5, 0, 0);
        ks.lesson = 1;
        Kinship.save(p, ks);
        KinWatch.say(p, "[Something's been watching your fire from the trees. It's on your Hearth page now.]", 12);
    }

    static @Nullable KinBeast spawn(ServerLevel level, KinKind k, BlockPos spot, EntitySpawnReason why) {
        var type = KinContent.TYPES.get(k);
        if (type == null) return null;
        KinBeast b = type.get().create(level, why);
        if (b == null) return null;
        b.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
        b.wildAt(spot);
        level.addFreshEntity(b);
        return b;
    }

    // ---- the day's count -------------------------------------------------------------------------------------------------

    /** Once a day for each player: Kinship for each beast that lived with them. */
    static void daily(ServerPlayer p) {
        Kinship ks = Kinship.of(p);
        long day = p.level().getOverworldClockTime() / 24000L;
        if (ks.countedDay == day) return;
        ks.countedDay = day;
        Kinship.save(p, ks);
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) return;
        for (KinWorld.Homed m : KinWorld.get(p.level().getServer()).at(h.get().anchor())) Kinship.add(p, m.kind(), Kinship.DAY);
    }

    // ---- work ---------------------------------------------------------------------------------------------------------

    /** A beast at home, each goal tick: by day (by night for a Lampwing) its work, else asleep in its nook. */
    static void work(ServerLevel level, KinBeast b) {
        if (b.nook == null || b.home == null) return;
        if (every(b, 400) && !dev.awaken.core.api.HearthHooks.inside(level, b.home)) {
            // its Hearth is gone: back to the wild where it stands
            KinWorld.get(level.getServer()).leave(b.home, b.getUUID());
            if (b.kind() == KinKind.LAMPWING) lamp(level, b.nook, false);
            b.leaveHome();
            b.wildAt(b.blockPosition());
            return;
        }
        long day = level.getOverworldClockTime() / 24000L;
        if (b.madeDay != day) {
            b.madeDay = day;
            b.made = 0;
        }
        boolean awake = b.kind() == KinKind.LAMPWING ? KinWatch.night(level) : !KinWatch.night(level);
        if (b.kind() == KinKind.LAMPWING && every(b, 40)) lamp(level, b.nook, awake);
        if (dev.awaken.bigday.BigDays.uneasy(level, b.home)) {
            // a raid is coming (SPEC §43.8): Lampwings keep to their lit roosts, the rest carry things into their nooks
            if (b.kind() == KinKind.LAMPWING) {
                if (b.act() == KinBeast.WORK) b.act(KinBeast.IDLE);
                goTo(b, b.nook, 0.8);
                return;
            }
            if (awake && (b.tickCount / 200) % 2 == 0) {
                b.act(KinBeast.CARRY);
                goTo(b, b.nook, 1.0);
                return;
            }
        }
        if (!awake) {
            if (b.act() == KinBeast.WORK) b.act(KinBeast.IDLE); // still in its nook, it falls asleep
            goTo(b, b.nook, 0.8);
            return;
        }
        switch (b.kind()) {
            case LAMPWING -> {
                // roosts over its light: drifts round it, glowing
                if (b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(b.nook)) > 9) b.getNavigation().moveTo(b.nook.getX() + 0.5, b.nook.getY() + 1.5, b.nook.getZ() + 0.5, 0.8);
                b.act(KinBeast.WORK);
            }
            case BURROWBUN -> fields(level, b);
            case MOSSBACK -> {
                goTo(b, b.nook, 0.6);
                if (every(b, 100)) grow(level, b);
            }
            case PEBBLEPUP -> shed(level, b);
            default -> {}
        }
    }

    /** A raid at this home (SPEC §43.8): every beast that lives here runs for its nook and stays hidden until {@code until}. */
    public static void hide(ServerLevel level, BlockPos anchor, long until) {
        for (KinWorld.Homed m : KinWorld.get(level.getServer()).at(anchor))
            if (level.getEntity(m.id()) instanceof KinBeast b) b.hideUntil = Math.max(b.hideUntil, until);
    }

    /**
     * Dawn after a raid held: the beasts come out and check on whoever held it, a Pebblepup brings a shiny stone, and
     * each kind living here takes a step of Kinship with each of them (they saw you hold).
     */
    public static void checkOn(ServerLevel level, BlockPos anchor, List<ServerPlayer> held) {
        java.util.Set<KinKind> kinds = java.util.EnumSet.noneOf(KinKind.class);
        for (KinWorld.Homed m : KinWorld.get(level.getServer()).at(anchor)) {
            kinds.add(m.kind());
            if (!(level.getEntity(m.id()) instanceof KinBeast b)) continue;
            b.hideUntil = 0;
            ServerPlayer near = null;
            for (ServerPlayer p : held) if (near == null || p.distanceToSqr(b) < near.distanceToSqr(b)) near = p;
            if (near == null) continue;
            b.getNavigation().moveTo(near, 1.0);
            b.act(KinBeast.NUDGE);
            if (m.kind() == KinKind.PEBBLEPUP) {
                near.spawnAtLocation(level, new ItemStack(Items.AMETHYST_SHARD));
                KinWatch.say(near, "[The Pebblepup drops a shiny stone at your feet.]", 8);
            }
        }
        for (ServerPlayer p : held)
            for (KinKind k : kinds) Kinship.add(p, k, Kinship.HELP);
        if (!kinds.isEmpty()) for (ServerPlayer p : held) KinWatch.say(p, "[Your beasts are coming out to check on you. They saw you hold.]", 9);
    }

    /** Wild beasts running from what's coming: {@code n} of them from {@code from} toward {@code to}, gone once there. */
    public static List<java.util.UUID> stream(ServerLevel level, BlockPos from, BlockPos to, int n) {
        List<java.util.UUID> out = new java.util.ArrayList<>();
        KinKind[] runners = {KinKind.BURROWBUN, KinKind.MOSSBACK, KinKind.PEBBLEPUP};
        for (int i = 0; i < n; i++) {
            BlockPos at = KinHelp.ground(level, from.getX() + level.getRandom().nextInt(17) - 8, from.getZ() + level.getRandom().nextInt(17) - 8);
            KinBeast b = spawn(level, runners[i % runners.length], at, EntitySpawnReason.EVENT);
            if (b == null) continue;
            b.origin = to;
            b.hideUntil = level.getGameTime() + 20 * 120;
            b.addTag(STREAM);
            out.add(b.getUUID());
        }
        return out;
    }

    /**
     * The tower rings its song (SPEC §43.7): every beast living here takes it by how many bells the home holds. Under 20
     * they bolt for their nooks, 20 to 49 hunker where they are, 50 to 79 stop and listen, 80 to 99 gather under the
     * tower, and at 100 they sing. Wild beasts within 48 of a song with gaps in it scatter.
     */
    public static void song(ServerLevel level, BlockPos anchor, BlockPos tower, int bells, long until) {
        for (KinWorld.Homed m : KinWorld.get(level.getServer()).at(anchor)) {
            if (!(level.getEntity(m.id()) instanceof KinBeast b)) continue;
            if (bells < 20) {
                b.hideUntil = Math.max(b.hideUntil, until);
                continue;
            }
            b.songUntil = until;
            b.songAt = tower;
            b.songMode = bells < 50 ? KinBeast.SONG_HUNKER : bells < 80 ? KinBeast.SONG_LISTEN : bells < 100 ? KinBeast.SONG_GATHER : KinBeast.SONG_SING;
        }
        if (bells < 100) scare(level, tower, 48, 600);
    }

    /** A bell rung in the wild (SPEC §43.7): wild beasts within {@code reach} run from it for {@code ticks}. */
    public static void scare(ServerLevel level, BlockPos at, int reach, int ticks) {
        long until = level.getGameTime() + ticks;
        for (KinBeast b : level.getEntitiesOfClass(KinBeast.class, new net.minecraft.world.phys.AABB(at).inflate(reach), x -> x.home == null))
            b.hideUntil = Math.max(b.hideUntil, until);
    }

    /** The peal Gather (SPEC §43.7): every beast living here comes to the tower and waits there a while. */
    public static int gather(ServerLevel level, BlockPos anchor, BlockPos tower, int ticks) {
        int n = 0;
        for (KinWorld.Homed m : KinWorld.get(level.getServer()).at(anchor))
            if (level.getEntity(m.id()) instanceof KinBeast b) {
                b.hideUntil = 0;
                b.songUntil = level.getGameTime() + ticks;
                b.songAt = tower;
                b.songMode = KinBeast.SONG_GATHER;
                n++;
            }
        return n;
    }

    /** The tag on wild beasts running from a raid: they leave the world once they've run their course. */
    public static final String STREAM = "awaken_kin_stream";

    /** Once every {@code n} ticks of a goal that ticks every other tick (on odd or even ticks, by the entity's id). */
    static boolean every(KinBeast b, int n) {
        return (b.tickCount >> 1) % (n >> 1) == 0;
    }

    /** A Lampwing's roost is lit while it's up at night (a real light, level 13); dark again by day or once it's gone. */
    static void lamp(ServerLevel level, BlockPos nook, boolean on) {
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos at = nook.above(dy);
            BlockState s = level.getBlockState(at);
            if (s.is(Blocks.LIGHT)) {
                if (!on) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            if (on && s.isAir()) {
                level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 13), Block.UPDATE_ALL);
                return;
            }
        }
    }

    private static void goTo(KinBeast b, BlockPos at, double speed) {
        if (b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) > 4 && b.getNavigation().isDone()) b.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, speed);
    }

    /** Lampwings' roosts, so the dark's spawns keep away (checked against the few there are). */
    static void spawnCheck(MobSpawnEvent.PositionCheck e) {
        if (!(e.getEntity() instanceof Enemy) || !(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        if (e.getSpawnType() != EntitySpawnReason.NATURAL) return;
        KinWorld w = KinWorld.get(sl.getServer());
        if (w.homes.isEmpty()) return;
        double x = e.getX(), z = e.getZ();
        for (List<KinWorld.Homed> l : w.homes.values())
            for (KinWorld.Homed m : l)
                if (m.kind() == KinKind.LAMPWING) {
                    double dx = m.nook().getX() - x, dz = m.nook().getZ() - z;
                    if (dx * dx + dz * dz < 32 * 32) {
                        e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
                        return;
                    }
                }
    }

    // ---- fields (Burrowbuns) ----------------------------------------------------------------------------------------------

    private static void fields(ServerLevel level, KinBeast b) {
        BlockPos target = null;
        boolean harvest = false;
        // carrying a lot, or nothing left to do: to the field's chest
        int carried = b.carry.stream().mapToInt(ItemStack::getCount).sum();
        if (every(b, 20) || carried >= 32) {
            if (carried < 32) {
                BlockPos ripe = find(level, b.nook, FIELD, at -> level.getBlockState(at).getBlock() instanceof CropBlock c && c.isMaxAge(level.getBlockState(at)));
                if (ripe != null) {
                    target = ripe;
                    harvest = true;
                } else if (hasSeed(level, b) != null) {
                    target = find(level, b.nook, FIELD, at -> level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(Blocks.FARMLAND));
                }
            }
            if (target == null && carried > 0) {
                Container chest = chest(level, b.nook);
                if (chest != null) {
                    BlockPos cp = ((BlockEntity) chest).getBlockPos();
                    if (b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(cp)) < 6) {
                        for (int i = b.carry.size() - 1; i >= 0; i--) {
                            ItemStack left = put(chest, b.carry.get(i));
                            if (left.isEmpty()) b.carry.remove(i);
                            else b.carry.set(i, left);
                        }
                        b.act(KinBeast.WORK, true);
                    } else b.getNavigation().moveTo(cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, 1.0);
                    return;
                }
            }
            if (target == null) {
                goTo(b, b.nook, 0.8);
                return;
            }
        } else return;
        if (b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 4) {
            b.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        b.act(KinBeast.WORK, true);
        BlockState s = level.getBlockState(target);
        if (harvest) {
            List<ItemStack> drops = Block.getDrops(s, level, target, null);
            Item seed = s.getBlock().asItem();
            boolean replanted = false;
            for (ItemStack d : drops) {
                if (!replanted && d.is(seed)) {
                    d.shrink(1);
                    replanted = true;
                }
                if (!d.isEmpty()) {
                    b.made += d.getCount();
                    b.carry.add(d);
                }
            }
            level.setBlock(target, replanted ? s.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            ItemStack seed = hasSeed(level, b);
            if (seed == null) return;
            Block crop = Block.byItem(seed.getItem());
            if (crop instanceof CropBlock) {
                seed.shrink(1);
                level.setBlock(target, crop.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** A seed it can plant: one it carries, else one from the field's chest. */
    private static @Nullable ItemStack hasSeed(ServerLevel level, KinBeast b) {
        for (ItemStack s : b.carry) if (!s.isEmpty() && Block.byItem(s.getItem()) instanceof CropBlock) return s;
        Container chest = chest(level, b.nook);
        if (chest == null) return null;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && Block.byItem(s.getItem()) instanceof CropBlock) return s;
        }
        return null;
    }

    private interface At {
        boolean ok(BlockPos at);
    }

    /** The nearest block round {@code c} (a few layers up and down) that passes. */
    private static @Nullable BlockPos find(ServerLevel level, BlockPos c, int r, At t) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d >= bd || !level.hasChunkAt(m) || !t.ok(m)) continue;
                    bd = d;
                    best = m.immutable();
                }
        return best;
    }

    /** The nearest chest or barrel to a nook (within 8). */
    static @Nullable Container chest(ServerLevel level, BlockPos c) {
        Container best = null;
        double bd = 65;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 3; dy++)
            for (int dx = -8; dx <= 8; dx++)
                for (int dz = -8; dz <= 8; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d >= bd) continue;
                    BlockState s = level.getBlockState(m);
                    if (!s.is(Blocks.CHEST) && !s.is(Blocks.BARREL) && !s.is(Blocks.TRAPPED_CHEST)) continue;
                    if (level.getBlockEntity(m) instanceof Container ct) {
                        best = ct;
                        bd = d;
                    }
                }
        return best;
    }

    /** Puts what fits into a container; the rest comes back. */
    static ItemStack put(Container c, ItemStack in) {
        ItemStack s = in.copy();
        for (int i = 0; i < c.getContainerSize() && !s.isEmpty(); i++) {
            ItemStack there = c.getItem(i);
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, s) && there.getCount() < there.getMaxStackSize()) {
                int n = Math.min(s.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(n);
                s.shrink(n);
            }
        }
        for (int i = 0; i < c.getContainerSize() && !s.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, s.copy());
                s.setCount(0);
            }
        }
        c.setChanged();
        return s;
    }

    // ---- growth (Mossbacks) ---------------------------------------------------------------------------------------------

    private static void grow(ServerLevel level, KinBeast b) {
        BlockPos c = b.blockPosition();
        for (int i = 0; i < 6; i++) {
            BlockPos at = c.offset(level.getRandom().nextInt(MOSS * 2 + 1) - MOSS, level.getRandom().nextInt(5) - 2, level.getRandom().nextInt(MOSS * 2 + 1) - MOSS);
            if (!level.hasChunkAt(at)) continue;
            BlockState s = level.getBlockState(at);
            if (s.is(BlockTags.CROPS) && s.isRandomlyTicking()) {
                s.randomTick(level, at, level.getRandom());
                b.made++;
            }
        }
        b.act(KinBeast.WORK, true);
    }

    // ---- the Ore Shed (Pebblepups) -----------------------------------------------------------------------------------------

    /** A lode heart set down at a home: that Hearth's Ore Shed heart, once it stands in an Ore Shed room. */
    static void placed(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState s) {
        if (!(s.getBlock() instanceof LodeHeartBlock)) return;
        KinWorld w = KinWorld.get(level.getServer());
        w.placedHearts.add(heartKey(level, pos));
        w.setDirty();
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty() || pos.distSqr(h.get().anchor()) > (double) h.get().radius() * h.get().radius()) return;
        KinWorld.get(level.getServer()).oreHeart(h.get().anchor(), pos);
        KinWatch.say(p, "[The heart is beating in its new place. Build the Ore Shed round it: a room with the heart and a barrel. Pebblepups can hear it.]", 12);
    }

    static String heartKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "|" + pos.asLong();
    }

    /** A heart a player set down gives only itself back when broken: its ore came out the first time. */
    static void heartDrops(net.neoforged.neoforge.event.level.BlockDropsEvent e) {
        if (!(e.getState().getBlock() instanceof LodeHeartBlock)) return;
        KinWorld w = KinWorld.get(e.getLevel().getServer());
        if (!w.placedHearts.remove(heartKey(e.getLevel(), e.getPos()))) return;
        w.setDirty();
        Item heart = e.getState().getBlock().asItem();
        e.getDrops().removeIf(ie -> !ie.getItem().is(heart));
    }

    /** A Pebblepup's turn at the Ore Shed: one thing off its barrels, sorted or crushed, back into them. */
    private static void shed(ServerLevel level, KinBeast b) {
        KinWorld w = KinWorld.get(level.getServer());
        BlockPos heart = b.home == null ? null : w.oreHeart(b.home);
        if (heart == null || !(level.getBlockState(heart).getBlock() instanceof LodeHeartBlock) || !Hearth.inRoom(level, heart, RoomType.MINE_HEAD)) {
            goTo(b, b.nook, 0.8);
            return;
        }
        goTo(b, heart.above(), 0.9);
        if (!every(b, SHED_EVERY)) return;
        List<Container> barrels = barrels(level, heart);
        if (barrels.isEmpty()) return;
        for (Container c : barrels) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) continue;
                List<ItemStack> out = process(level, b, s);
                if (out == null) continue;
                s.shrink(1);
                c.setChanged();
                for (ItemStack o : out) {
                    ItemStack left = sortInto(barrels, o);
                    if (!left.isEmpty()) b.spawnAtLocation(level, left);
                    b.made += o.getCount();
                }
                b.act(KinBeast.WORK, true);
                return;
            }
        }
    }

    private static List<Container> barrels(ServerLevel level, BlockPos heart) {
        List<Container> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2; dy++)
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    m.set(heart.getX() + dx, heart.getY() + dy, heart.getZ() + dz);
                    if (level.getBlockState(m).is(Blocks.BARREL) && level.getBlockEntity(m) instanceof Container c) out.add(c);
                }
        return out;
    }

    /** One nugget in this many crushed: the home's Shed bells (SPEC §43.7) make it more often, never better than one in 16. */
    static int nuggetPer(ServerLevel level, KinBeast b) {
        if (b.home == null) return NUGGET_PER;
        return Math.max(16, NUGGET_PER - 4 * dev.awaken.bells.Bells.voice(level, b.home, dev.awaken.bells.BellCatalog.Voice.SHED));
    }

    /** What one item becomes in a Pebblepup's paws, or null if it isn't theirs to work. Never more than came in. */
    static @Nullable List<ItemStack> process(ServerLevel level, KinBeast b, ItemStack s) {
        if (rubble(s)) {
            List<ItemStack> out = new ArrayList<>(List.of(new ItemStack(Items.GRAVEL)));
            if (level.getRandom().nextInt(nuggetPer(level, b)) == 0) out.add(new ItemStack(level.getRandom().nextInt(3) == 0 ? Items.GOLD_NUGGET : Items.IRON_NUGGET));
            return out;
        }
        if (s.is(Items.GRAVEL)) {
            List<ItemStack> out = new ArrayList<>(List.of(new ItemStack(level.getRandom().nextInt(4) == 0 ? Items.FLINT : Items.SAND)));
            if (level.getRandom().nextInt(nuggetPer(level, b)) == 0) out.add(new ItemStack(level.getRandom().nextInt(3) == 0 ? Items.GOLD_NUGGET : Items.IRON_NUGGET));
            return out;
        }
        Block block = Block.byItem(s.getItem());
        if (block != Blocks.AIR && block.defaultBlockState().is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            // an ore block brought whole: broken as a pick would, without luck
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), level, b.blockPosition(), null, b, pick);
            return drops.isEmpty() ? null : drops;
        }
        return null;
    }

    static boolean rubble(ItemStack s) {
        return s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.TUFF) || s.is(Items.ANDESITE) || s.is(Items.DIORITE)
                || s.is(Items.GRANITE) || s.is(Items.NETHERRACK) || s.is(Items.BLACKSTONE) || s.is(Items.CALCITE);
    }

    /** Sorted: onto a stack of the same thing if a barrel has one, else into the emptiest barrel. */
    private static ItemStack sortInto(List<Container> barrels, ItemStack o) {
        for (Container c : barrels)
            for (int i = 0; i < c.getContainerSize(); i++)
                if (ItemStack.isSameItemSameComponents(c.getItem(i), o)) {
                    ItemStack left = put(c, o);
                    if (left.isEmpty()) return left;
                    o = left;
                }
        Container emptiest = null;
        int free = -1;
        for (Container c : barrels) {
            int f = 0;
            for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) f++;
            if (f > free) {
                free = f;
                emptiest = c;
            }
        }
        return emptiest == null ? o : put(emptiest, o);
    }

    /** Every beast that lives at this home and is loaded, for the page's "what it's doing now". */
    static List<KinBeast> loaded(MinecraftServer server, BlockPos anchor) {
        List<KinBeast> out = new ArrayList<>();
        ServerLevel level = server.overworld();
        for (KinWorld.Homed m : KinWorld.get(server).at(anchor)) if (level.getEntity(m.id()) instanceof KinBeast b) out.add(b);
        return out;
    }
}
