package dev.awaken.lucidium.trial;

import dev.awaken.body.ascent.SwingSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The assisted swing's questions (SPEC §17.31) answered from a course's blocks before any are placed, as the rope's own
 * {@code LevelSpace} answers them from a level: block rays, the body's box against collisions, the floor under a point
 * and a column's top. Lets the solver throw the real rope at a Run's anchors (SPEC §9.6).
 */
final class CourseSpace implements SwingSpace {
    private final BlockGetter v;
    private final CollisionContext ctx = CollisionContext.empty();
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    CourseSpace(BlockGetter v) {
        this.v = v;
    }

    @Override
    public boolean ray(double ox, double oy, double oz, double dx, double dy, double dz, double range, Hit out) {
        Vec3 from = new Vec3(ox, oy, oz), to = new Vec3(ox + dx * range, oy + dy * range, oz + dz * range);
        BlockHitResult h = v.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
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
        out.leaf = false; // a dream has no leaves
        return true;
    }

    @Override
    public double topOver(Hit h, double maxUp) {
        for (int k = 0; k <= Math.ceil(maxUp) + 1; k++) {
            m.set(h.bx, h.by + k, h.bz);
            VoxelShape shape = v.getBlockState(m).getCollisionShape(v, m);
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
        AABB box = new AABB(x0, y0, z0, x1, y1, z1);
        for (int bx = (int) Math.floor(x0); bx <= (int) Math.floor(x1); bx++)
            for (int by = (int) Math.floor(y0); by <= (int) Math.floor(y1); by++)
                for (int bz = (int) Math.floor(z0); bz <= (int) Math.floor(z1); bz++) {
                    m.set(bx, by, bz);
                    VoxelShape shape = v.getBlockState(m).getCollisionShape(v, m, ctx);
                    if (shape.isEmpty()) continue;
                    for (AABB part : shape.toAabbs()) if (part.move(bx, by, bz).intersects(box)) return true;
                }
        return false;
    }

    @Override
    public double floorUnder(double x, double from, double z) {
        BlockHitResult h = v.clip(new ClipContext(new Vec3(x, from, z), new Vec3(x, from - 64, z), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, ctx));
        return h.getType() == HitResult.Type.BLOCK ? h.getLocation().y : from - 64;
    }
}
