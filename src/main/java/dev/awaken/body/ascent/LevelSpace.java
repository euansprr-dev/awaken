package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The swing's questions answered from a real level (SPEC §17.31): vanilla's block rays (the same the rope has always
 * bitten with), the body's box against block collisions, the floor under a point, a column's top. Bound to one level
 * and one body at a time ({@link #at}); the client keeps one.
 */
public final class LevelSpace implements SwingSpace {
    private @Nullable Level level;
    private @Nullable Entity who;
    private CollisionContext ctx = CollisionContext.empty();
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    public LevelSpace at(Level level, @Nullable Entity who) {
        this.level = level;
        if (who != this.who) ctx = who == null ? CollisionContext.empty() : CollisionContext.of(who);
        this.who = who;
        return this;
    }

    private Level level() {
        Level l = level;
        if (l == null) throw new IllegalStateException("LevelSpace used before at()");
        return l;
    }

    @Override
    public boolean ray(double ox, double oy, double oz, double dx, double dy, double dz, double range, Hit out) {
        Level l = level();
        Vec3 from = new Vec3(ox, oy, oz), to = new Vec3(ox + dx * range, oy + dy * range, oz + dz * range);
        BlockHitResult h = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
        if (h.getType() != HitResult.Type.BLOCK) return false;
        Vec3 p = h.getLocation();
        Direction f = h.getDirection();
        out.nx = f.getStepX();
        out.ny = f.getStepY();
        out.nz = f.getStepZ();
        out.x = p.x + out.nx * 0.04;
        out.y = p.y + out.ny * 0.04;
        out.z = p.z + out.nz * 0.04;
        out.t = p.distanceTo(from);
        BlockPos b = h.getBlockPos();
        out.bx = b.getX();
        out.by = b.getY();
        out.bz = b.getZ();
        out.leaf = l.getBlockState(b).is(BlockTags.LEAVES);
        return true;
    }

    @Override
    public double topOver(Hit h, double maxUp) {
        if (h.leaf) return Double.NaN;
        Level l = level();
        // up the hit block's column while it stays solid: the first cell with no collision over it is the top
        for (int k = 0; k <= Math.ceil(maxUp) + 1; k++) {
            m.set(h.bx, h.by + k, h.bz);
            VoxelShape shape = l.getBlockState(m).getCollisionShape(l, m);
            if (shape.isEmpty()) {
                double top = h.by + k;
                return top - h.y <= maxUp ? top : Double.NaN;
            }
            if (shape.max(Direction.Axis.Y) < 1) {
                double top = h.by + k + shape.max(Direction.Axis.Y);
                return top - h.y <= maxUp ? top : Double.NaN;
            }
        }
        return Double.NaN;
    }

    @Override
    public boolean boxHits(double x0, double y0, double z0, double x1, double y1, double z1) {
        return !level().noBlockCollision(who, new AABB(x0, y0, z0, x1, y1, z1));
    }

    @Override
    public double floorUnder(double x, double from, double z) {
        // as AscentAnchors.floorUnder (water is a floor too), 64 down at most
        BlockHitResult h = level().clip(new ClipContext(new Vec3(x, from, z), new Vec3(x, from - 64, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, ctx));
        return h.getType() == HitResult.Type.BLOCK ? h.getLocation().y : from - 64;
    }
}
