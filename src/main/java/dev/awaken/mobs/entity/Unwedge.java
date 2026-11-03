package dev.awaken.mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A body caught in something gets out by moving (SPEC "Fliers find their way"; Euan 2026-10-28, the Plumed Coil stuck
 * in a tree): the nearest way out it can fly or walk, or else it works its way through leaves, slowly, the leaves
 * shaking. Never a teleport, never through wood or stone, never a block broken. Sealed in, it waits.
 */
final class Unwedge {
    private Unwedge() {}

    /** How fast a body works its way through leaves, blocks a tick. */
    static final double PUSH_SPEED = 0.12;
    /** How far a way out is looked for (blocks): fliers, walkers. */
    static final int FLY_REACH = 8, WALK_REACH = 4;

    /** Up first, then up and out, then straight out: the order ways out are tried in (the first of equals wins). */
    private static final Vec3[] FLY_DIRS, WALK_DIRS;

    static {
        double d = Math.sqrt(0.5);
        FLY_DIRS = new Vec3[] {new Vec3(0, 1, 0),
                new Vec3(d, d, 0), new Vec3(-d, d, 0), new Vec3(0, d, d), new Vec3(0, d, -d),
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
                new Vec3(d, 0, d), new Vec3(-d, 0, d), new Vec3(d, 0, -d), new Vec3(-d, 0, -d)};
        WALK_DIRS = new Vec3[] {new Vec3(0, 1, 0),
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1),
                new Vec3(d, 0, d), new Vec3(-d, 0, d), new Vec3(d, 0, -d), new Vec3(-d, 0, -d)};
    }

    /** A way out: where to, and whether it means working through leaves on the way. */
    record Out(Vec3 to, boolean push) {}

    /** Whether the body is inside a block now (spawned or saved there; a moving body never gets in). */
    static boolean inside(AwakenMob m) {
        return !m.level().noBlockCollision(m, m.getBoundingBox().deflate(0.01));
    }

    /** Leaves, vines, grass and flowers, bamboo: what a big body can push through. Never wood or stone. */
    static boolean foliage(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES) || s.is(Blocks.BAMBOO);
    }

    /**
     * The nearest way out of where the body is: to a spot where its hitbox (with {@code pad} round it) is clear, or, with
     * {@code room}, where its wings and coils have their room (FlightRoom). A way it can fly or walk straight beats one
     * through leaves, which counts double. Null when there is none within reach (sealed in: it waits).
     */
    static @Nullable Out wayOut(AwakenMob m, boolean flies, double pad, boolean room) {
        Level level = m.level();
        Vec3 here = m.position();
        AABB body = m.getBoundingBox();
        Out best = null;
        double bestCost = Double.MAX_VALUE;
        if (room) {
            // over the canopy first: the nearest height above with room, straight up or through the leaves
            Vec3 up = FlightRoom.roomAbove(m, here.x, here.y, here.z);
            if (up != null && up.y > here.y + 0.01) {
                Vec3 d = up.subtract(here);
                if (FlightRoom.sweep(m, body, d) >= 1.0) return new Out(up, false);
                if (onlyFoliage(level, body.expandTowards(d), body)) {
                    best = new Out(up, true);
                    bestCost = d.length() * 2.0;
                }
            }
        }
        Vec3[] dirs = flies ? FLY_DIRS : WALK_DIRS;
        int reach = flies ? FLY_REACH : WALK_REACH;
        for (Vec3 dir : dirs) {
            int far = !flies && dir.y > 0 ? 2 : reach; // a walker climbs out of a bush, never into the air
            double clear = FlightRoom.sweep(m, body, dir.scale(far)) * far;
            boolean leaves = true;
            for (int k = 1; k <= far; k++) {
                boolean straight = k <= clear + 1.0E-6;
                if (!straight) {
                    AABB leg = body.move(dir.scale(k - 1)).expandTowards(dir);
                    leaves = leaves && onlyFoliage(level, leg, body);
                    if (!leaves) break; // wood or stone between: no way out this side
                }
                double cost = straight ? k : k * 2.0;
                if (cost >= bestCost) break;
                double x = here.x + dir.x * k, y = here.y + dir.y * k, z = here.z + dir.z * k;
                if (room ? FlightRoom.fits(m, x, y, z) : FlightRoom.passes(m, x, y, z, pad)) {
                    best = new Out(new Vec3(x, y, z), !straight);
                    bestCost = cost;
                    break;
                }
            }
        }
        return best;
    }

    /**
     * One tick of getting out toward {@code to}: a straight way is moved with vanilla's collision; leaves are pushed
     * through slowly, shaking, and only leaves (a block the body is already in may be left, nothing else entered).
     * False once there, or when something solid is in the way.
     */
    static boolean step(AwakenMob m, Vec3 to, boolean push) {
        Vec3 here = m.position(), d = to.subtract(here);
        double len = d.length();
        if (len < 0.05) return false;
        Vec3 step = len <= PUSH_SPEED ? d : d.scale(PUSH_SPEED / len);
        AABB body = m.getBoundingBox(), next = body.move(step);
        if (!push) {
            m.move(MoverType.SELF, step);
            return m.position().distanceToSqr(here) > 1.0E-6;
        }
        if (!onlyFoliage(m.level(), next, body)) return false;
        m.setDeltaMovement(Vec3.ZERO);
        m.setPos(here.add(step));
        if (step.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F;
            m.setYRot(Mth.approachDegrees(m.getYRot(), yaw, 8.0F));
            m.yBodyRot = m.getYRot();
        }
        if (m.level() instanceof ServerLevel level && (m.tickCount & 3) == 0) rustle(level, m, next);
        return true;
    }

    /** The leaves it is pushing through shake and fall about it, with their own rustle now and then. */
    private static void rustle(ServerLevel level, AwakenMob m, AABB box) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 4; i++) {
            p.set(Mth.floor(Mth.lerp(m.getRandom().nextDouble(), box.minX, box.maxX)), Mth.floor(Mth.lerp(m.getRandom().nextDouble(), box.minY, box.maxY)),
                    Mth.floor(Mth.lerp(m.getRandom().nextDouble(), box.minZ, box.maxZ)));
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !foliage(s)) continue;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 5, 0.4, 0.4, 0.4, 0.05);
            if ((m.tickCount & 15) == 0) {
                level.playSound(null, p, s.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.7F, 0.8F + m.getRandom().nextFloat() * 0.3F);
            }
            return;
        }
    }

    /** Whether every block in {@code box} that it isn't already in ({@code body}) is air or foliage. */
    static boolean onlyFoliage(Level level, AABB box, AABB body) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
        int x1 = Mth.floor(box.maxX - 1.0E-7), y1 = Mth.floor(box.maxY - 1.0E-7), z1 = Mth.floor(box.maxZ - 1.0E-7);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            if (body.intersects(x, y, z, x + 1, y + 1, z + 1)) continue;
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || foliage(s) || s.getCollisionShape(level, p).isEmpty()) continue;
            return false;
        }
        return true;
    }
}
