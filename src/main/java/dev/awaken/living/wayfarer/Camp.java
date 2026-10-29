package dev.awaken.living.wayfarer;

import dev.awaken.Awaken;
import dev.awaken.living.LivingContent;
import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Wayfarers' camp as a place (SPEC §27.6): a few things round Ilse's fire, laid once on the ground there (two
 * bedrolls with their heads away from the warmth, logs to sit on, the packs with a lantern, a pot hung over the fire,
 * the grass trodden flat), and the ground it keeps. No monster spawns within {@link #SAFE} of the fire, and whatever
 * wanders in is turned back out and hunts nobody here, so nothing comes at you while you sit with Ilse. A bedroll sleeps
 * the night through like a bed but never moves your spawn (that's the Hearth's), and when a step you're on needs the
 * dark it says so instead. At night Hettie lies down on the far bedroll; Ilse keeps the fire.
 */
public final class Camp {
    private Camp() {}

    /** No monster spawns this close to the fire; none hunts a player this close. */
    public static final int SAFE = 32;
    /** Monsters this close are turned back out past the edge. */
    static final int TURN = 30;

    /** Offsets from the fire: the two bedrolls' feet (their heads one further out), the logs, the packs. */
    static final int[][] BEDROLLS = {{-2, 0, 0}, {0, 0, 2}};
    static final Direction[] BEDROLL_FACING = {Direction.WEST, Direction.SOUTH};

    /** The fire, once found (and the camp it belongs to): spawn checks read this, never search. */
    private static long fireAt = Long.MIN_VALUE, fireOfCamp = Long.MIN_VALUE;
    /** When the last search found no fire (searched again 10 s later). */
    private static final long NEVER = -1_000_000L;
    private static long missedAt = NEVER;

    /** The camp's fire (the campfire within 6 of the camp's ground), or null before the camp is founded or lit. */
    public static @Nullable BlockPos fire(MinecraftServer server) {
        Scenes s = Scenes.get(server);
        if (s.camp == Scenes.NONE) return null;
        if (fireOfCamp == s.camp && fireAt != Long.MIN_VALUE) return BlockPos.of(fireAt);
        ServerLevel level = server.overworld();
        BlockPos camp = BlockPos.of(s.camp);
        long now = server.getTickCount();
        if (!level.hasChunkAt(camp) || now - missedAt < 200) return null;
        for (BlockPos q : BlockPos.betweenClosed(camp.offset(-6, -3, -6), camp.offset(6, 3, 6))) {
            if (level.getBlockState(q).is(Blocks.CAMPFIRE)) {
                fireAt = q.asLong();
                fireOfCamp = s.camp;
                return q.immutable();
            }
        }
        missedAt = now;
        return null;
    }

    /** Forgets the fire (a world closed, or the fire was relit somewhere else). */
    public static void forget() {
        fireAt = fireOfCamp = Long.MIN_VALUE;
        missedAt = NEVER;
    }

    /** Whether {@code pos} is on the camp's safe ground (needs the fire already found). */
    public static boolean inside(Level level, BlockPos pos, int radius) {
        if (fireAt == Long.MIN_VALUE || level.dimension() != Level.OVERWORLD) return false;
        BlockPos f = BlockPos.of(fireAt);
        double dx = pos.getX() - f.getX(), dz = pos.getZ() - f.getZ();
        return dx * dx + dz * dz < (double) radius * radius && Math.abs(pos.getY() - f.getY()) < 20;
    }

    // ---- the camp round the fire -------------------------------------------------------------------------------------

    /** Lays the camp round its fire, once (only on open ground: a trunk or a rock where something goes, and it's left out). */
    static void build(ServerLevel level, BlockPos fire) {
        // the plants, the moss and the litter round the fire cleared, the ground trodden flat
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 5.2 || dx == 0 && dz == 0) continue;
            for (int dy = -2; dy <= 2; dy++) {
                BlockPos q = fire.offset(dx, dy, dz);
                if (litter(level, q)) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockPos at = ground(level, fire.offset(dx, 0, dz));
            if (at != null && d < 3.6 && level.getBlockState(at.below()).is(BlockTags.DIRT)) {
                long h = (dx * 73856093L) ^ (dz * 19349663L);
                level.setBlock(at.below(), (h & 3) == 0 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // two bedrolls, feet to the fire
        for (int i = 0; i < BEDROLLS.length; i++) {
            BlockPos foot = ground(level, fire.offset(BEDROLLS[i][0], BEDROLLS[i][1], BEDROLLS[i][2]));
            Direction facing = BEDROLL_FACING[i];
            BlockPos head = foot == null ? null : foot.relative(facing);
            if (foot == null || !open(level, foot) || !open(level, head)) continue;
            BlockState s = LivingContent.BEDROLL.get().defaultBlockState().setValue(AbstractBedBlock.FACING, facing);
            level.setBlock(foot, s.setValue(AbstractBedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
            level.setBlock(head, s.setValue(AbstractBedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
        }
        // logs to sit on: a bench on the north side, a seat on the east
        BlockState benchX = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState benchZ = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int[] o : new int[][]{{-1, -2}, {0, -2}}) {
            BlockPos at = ground(level, fire.offset(o[0], 0, o[1]));
            if (at != null) place(level, at, benchX);
        }
        for (int[] o : new int[][]{{2, 0}, {2, 1}}) {
            BlockPos at = ground(level, fire.offset(o[0], 0, o[1]));
            if (at != null) place(level, at, benchZ);
        }
        // the packs, a lantern on them, and the night's wood
        BlockPos packs = ground(level, fire.offset(-3, 0, 2));
        if (packs != null && place(level, packs, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP))) {
            place(level, packs.above(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
        BlockPos wood = ground(level, fire.offset(-4, 0, 1));
        if (wood != null) place(level, wood, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        // the pot over the fire, on its tripod
        BlockPos pot = fire.above();
        if (level.getBlockState(pot).isAir()) level.setBlock(pot, LivingContent.CAMP_POT.get().defaultBlockState(), Block.UPDATE_ALL);
        Awaken.LOG.info("[living] the camp laid round its fire at {}", fire.toShortString());
    }

    private static boolean open(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP);
    }

    /** The ground in this column near the fire's height: the first open spot over something solid, from one above it
     *  down to two below (the fire stands on a bale; the ground round it may be a step lower under moss). */
    static @Nullable BlockPos ground(ServerLevel level, BlockPos near) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos at = near.above(dy);
            if (litter(level, at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (open(level, at)) return at;
        }
        return null;
    }

    /** What lies on the ground and gives way underfoot: grass, flowers, mushrooms, ferns, moss and carpets, snow. */
    private static boolean litter(ServerLevel level, BlockPos at) {
        BlockState st = level.getBlockState(at);
        if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() || st.is(Blocks.CAMPFIRE) || st.is(BlockTags.LOGS)) return false;
        return st.canBeReplaced() || st.getDestroySpeed(level, at) <= 0.2F && !st.isCollisionShapeFullBlock(level, at);
    }

    private static boolean place(ServerLevel level, BlockPos at, BlockState s) {
        if (!open(level, at)) return false;
        level.setBlock(at, s, Block.UPDATE_ALL);
        return true;
    }

    // ---- the ground it keeps -----------------------------------------------------------------------------------------

    /** Every half second while the camp is loaded: monsters on its ground are turned back out and stop hunting. */
    public static void ward(MinecraftServer server) {
        if (server.getTickCount() % 10 == 0) turnAway(server);
    }

    static void turnAway(MinecraftServer server) {
        BlockPos f = fire(server);
        ServerLevel level = server.overworld();
        if (f == null || !level.hasChunkAt(f)) return;
        double cx = f.getX() + 0.5, cz = f.getZ() + 0.5;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(f).inflate(TURN, 16, TURN), m -> m instanceof Enemy && m.isAlive())) {
            double dx = m.getX() - cx, dz = m.getZ() - cz, d = Math.sqrt(dx * dx + dz * dz);
            if (d > TURN) continue;
            if (m.getTarget() instanceof Player) m.setTarget(null);
            if (m.getNavigation().isInProgress() && m.tickCount % 40 != 0) continue;
            // out past the edge, the way it came in
            double k = (SAFE + 10) / Math.max(1.0, d);
            if (d < 1) {
                dx = 1;
                dz = 0;
            }
            m.getNavigation().moveTo(cx + dx * k, m.getY(), cz + dz * k, 1.25);
        }
    }

    /** No monster spawns on the camp's ground. */
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getEntityType().getCategory() != MobCategory.MONSTER) return;
        EntitySpawnReason why = e.getSpawnType();
        if (why != EntitySpawnReason.NATURAL && why != EntitySpawnReason.CHUNK_GENERATION && why != EntitySpawnReason.PATROL
                && why != EntitySpawnReason.REINFORCEMENT) return;
        if (inside(e.getLevel().getLevel(), e.getPos(), SAFE)) e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /** Nothing hunts a player on the camp's ground. */
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getEntity() instanceof Enemy && e.getNewAboutToBeSetTarget() instanceof ServerPlayer p && inside(p.level(), p.blockPosition(), SAFE)) e.setCanceled(true);
    }

    // ---- the night ---------------------------------------------------------------------------------------------------

    /** Why a step this player is on needs the dark (a bedroll won't sleep it away), or null. */
    public static @Nullable String nightNeeded(ServerPlayer p) {
        for (QuestState.Active a : QuestEngine.state(p).active()) {
            QuestDef d = QuestBook.get(a.chain());
            if (d == null || a.step() >= d.steps().size()) continue;
            var night = d.steps().get(a.step()).night();
            if (night.isPresent()) return night.get();
        }
        return null;
    }
}
