package dev.awaken.kin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where Aya and Dubu can be (SPEC §69.3, §69.9): ground to stand on, a raised spot in the light for her loaf, the sun,
 * the fire's side, the bed; and where to turn up out of your sight. Each search reads a handful of blocks, at most a
 * dozen tries, and is asked for once per choice of theirs (a few times a minute), never every tick.
 */
final class FamilySpots {
    private FamilySpots() {}

    /** Somewhere to stand in this column near this height: solid under foot, clear at the feet and above, dry. */
    static @Nullable BlockPos standable(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
        if (!level.hasChunkAt(new BlockPos(bx, by, bz))) return null;
        for (int dy = 3; dy >= -5; dy--) {
            m.set(bx, by + dy, bz);
            if (stands(level, m)) return m.immutable();
        }
        return null;
    }

    private static boolean stands(ServerLevel level, BlockPos at) {
        BlockState here = level.getBlockState(at);
        if (!here.getCollisionShape(level, at).isEmpty() || !here.getFluidState().isEmpty()) return false;
        BlockPos up = at.above(), down = at.below();
        if (!level.getBlockState(up).getCollisionShape(level, up).isEmpty()) return false;
        BlockState under = level.getBlockState(down);
        if (under.getCollisionShape(level, down).isEmpty() || !under.getFluidState().isEmpty()) return false;
        return !under.is(BlockTags.CAMPFIRES) && !under.is(Blocks.MAGMA_BLOCK) && !under.is(Blocks.CACTUS) && !here.is(BlockTags.FIRE);
    }

    /** A stroll's end: somewhere to stand within {@code reach} of the fire. */
    static @Nullable BlockPos wander(ServerLevel level, BlockPos a, int reach, RandomSource rnd) {
        int r = Math.max(4, reach);
        for (int k = 0; k < 6; k++) {
            BlockPos at = standable(level, a.getX() + rnd.nextInt(r * 2 + 1) - r, a.getY(), a.getZ() + rnd.nextInt(r * 2 + 1) - r);
            if (at != null) return at;
        }
        return null;
    }

    /** Beside the fire, never in it: two or three blocks off. */
    static @Nullable BlockPos byTheFire(ServerLevel level, BlockPos a, RandomSource rnd) {
        for (int k = 0; k < 8; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2, d = 2.0 + rnd.nextDouble() * 1.5;
            BlockPos at = standable(level, a.getX() + 0.5 + Math.cos(ang) * d, a.getY(), a.getZ() + 0.5 + Math.sin(ang) * d);
            if (at != null) return at;
        }
        return null;
    }

    /** Open sky on a clear day: where she rolls belly up and he naps. */
    static @Nullable BlockPos sun(ServerLevel level, BlockPos a, RandomSource rnd) {
        if (KinWatch.night(level) || level.isRaining()) return null;
        for (int k = 0; k < 10; k++) {
            BlockPos at = standable(level, a.getX() + rnd.nextInt(21) - 10, a.getY(), a.getZ() + rnd.nextInt(21) - 10);
            if (at != null && level.canSeeSky(at)) return at;
        }
        return null;
    }

    /**
     * Her loaf's place: a spot a block or two above the ground beside it, in good light, that she can reach (a sill, a
     * slab, a stair, a barrel, a bed). Never a chest's lid or any other thing you open, bar a barrel.
     */
    static @Nullable BlockPos perch(ServerLevel level, BlockPos a, RandomSource rnd) {
        for (int k = 0; k < 12; k++) {
            BlockPos at = standable(level, a.getX() + rnd.nextInt(21) - 10, a.getY() + 1, a.getZ() + rnd.nextInt(21) - 10);
            if (at == null || level.getMaxLocalRawBrightness(at) < 11) continue;
            BlockPos under = at.below();
            if (level.getBlockEntity(under) instanceof Container && !level.getBlockState(under).is(Blocks.BARREL)) continue;
            boolean raised = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = at.relative(d);
                // the ground beside it is one block lower (she steps up onto it), not a drop
                if (stands(level, side.below()) && !stands(level, side)) {
                    raised = true;
                    break;
                }
            }
            if (raised) return at;
        }
        return null;
    }

    /** The bed someone of the household sleeps in, while it still stands: she sleeps on it. */
    static @Nullable BlockPos bed(ServerLevel level, FamilyWorld.Home home) {
        if (home.bed.isEmpty()) return null;
        BlockPos b = home.bed.get();
        if (!level.hasChunkAt(b) || !level.getBlockState(b).is(BlockTags.BEDS)) return null;
        return b.above();
    }

    /** A foe within {@code reach} of it: something hostile, or anything hunting. */
    static boolean foeNear(ServerLevel level, FamilyPet b, double reach) {
        if ((b.tickCount + b.getId()) % 10 != 0) return b.waryUntil > level.getGameTime();
        boolean foe = !level.getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(reach), m -> m.isAlive() && m instanceof Enemy && !(m instanceof FamilyPet)).isEmpty();
        if (foe) b.waryUntil = level.getGameTime() + 60;
        return foe || b.waryUntil > level.getGameTime();
    }

    /**
     * Somewhere to turn up out of {@code p}'s sight: {@code from} to {@code to} blocks off, behind them (or, with
     * {@code anywhere}, in any direction where a hill or a wall hides it). Null when there is no such ground now.
     */
    static @Nullable BlockPos unseen(ServerLevel level, ServerPlayer p, double from, double to, boolean anywhere, RandomSource rnd) {
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        double behind = Math.atan2(-look.z, -look.x);
        Vec3 eye = p.getEyePosition();
        BlockPos fallback = null;
        for (int k = 0; k < 14; k++) {
            double ang = anywhere ? rnd.nextDouble() * Math.PI * 2 : behind + (rnd.nextDouble() - 0.5) * 2.2;
            double d = from + rnd.nextDouble() * (to - from);
            double x = p.getX() + Math.cos(ang) * d, z = p.getZ() + Math.sin(ang) * d;
            BlockPos at = standable(level, x, p.getY() + 2, z);
            if (at == null) at = standable(level, x, p.getY() - 5, z);
            if (at == null) continue;
            Vec3 c = new Vec3(at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5);
            Vec3 dir = c.subtract(eye).normalize();
            boolean back = dir.x * look.x + dir.z * look.z < -0.15;
            boolean hidden = level.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS;
            if (hidden || (back && !anywhere)) return at;
            if (back || fallback == null) fallback = at;
        }
        return fallback;
    }
}
