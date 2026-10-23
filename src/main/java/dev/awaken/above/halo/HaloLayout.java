package dev.awaken.above.halo;

import dev.awaken.above.AboveBlocks;
import dev.awaken.above.MachineType;
import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.ruin.RuinBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Halo station as a pure function of world coordinates, so the chunk generator can build any chunk on its own:
 * a domed hub (the archive and the return pad), four glass spokes, and an outer ring corridor with a glowing underside.
 * Everything else is void.
 */
public final class HaloLayout {
    private HaloLayout() {}

    public static final int FLOOR = 100;
    public static final int HUB = 12;
    public static final int RING_IN = 34;
    public static final int RING_OUT = 40;
    /** Horizontal reach of the station (chunks beyond this are empty). */
    public static final int EXTENT = RING_OUT + 2;
    public static final BlockPos PAD = new BlockPos(0, FLOOR + 1, 0);
    public static final BlockPos ARRIVAL = new BlockPos(0, FLOOR + 1, 4);

    /** A block with an item container, placed in the decoration step. */
    public record Cache(BlockPos pos, Direction facing) {}

    public static final List<Cache> CACHES = List.of(
            new Cache(new BlockPos(14, FLOOR + 1, 34), Direction.WEST),
            new Cache(new BlockPos(-14, FLOOR + 1, -34), Direction.EAST));

    private record Fixed(BlockPos pos, BlockState state) {}

    private static List<Fixed> fixed;

    private static List<Fixed> fixed() {
        List<Fixed> f = fixed;
        if (f == null) {
            f = List.of(
                    new Fixed(PAD, AboveBlocks.RETURN_PAD.get().defaultBlockState()),
                    // the Ascendant archive: five terminals facing the pad
                    new Fixed(new BlockPos(7, FLOOR + 1, 0), RuinBuilder.terminal(PrecursorLogs.Pool.HALO, Direction.WEST)),
                    new Fixed(new BlockPos(-7, FLOOR + 1, 0), RuinBuilder.terminal(PrecursorLogs.Pool.HALO, Direction.EAST)),
                    new Fixed(new BlockPos(0, FLOOR + 1, -7), RuinBuilder.terminal(PrecursorLogs.Pool.HALO, Direction.SOUTH)),
                    new Fixed(new BlockPos(5, FLOOR + 1, 5), RuinBuilder.terminal(PrecursorLogs.Pool.HALO, Direction.NORTH)),
                    new Fixed(new BlockPos(-5, FLOOR + 1, 5), RuinBuilder.terminal(PrecursorLogs.Pool.HALO, Direction.NORTH)),
                    // machines left running when they sealed it
                    new Fixed(new BlockPos(26, FLOOR + 1, 26), RuinBuilder.broken(MachineType.OBSERVATORY, Direction.NORTH)),
                    new Fixed(new BlockPos(-26, FLOOR + 1, 26), RuinBuilder.broken(MachineType.LAUNCH_RING, Direction.NORTH)),
                    new Fixed(new BlockPos(-26, FLOOR + 1, -26), RuinBuilder.broken(MachineType.CLONE_VAT, Direction.SOUTH)),
                    new Fixed(new BlockPos(26, FLOOR + 1, -26), RuinBuilder.broken(MachineType.SIGNAL_RELAY, Direction.SOUTH)),
                    new Fixed(new BlockPos(28, FLOOR + 1, 22), RuinBuilder.broken(MachineType.RECALIBRATOR, Direction.WEST)));
            fixed = f;
        }
        return f;
    }

    private static BlockState plating() { return AboveBlocks.PLATING.get().defaultBlockState(); }
    private static BlockState panel() { return AboveBlocks.PANEL.get().defaultBlockState(); }
    private static BlockState trim() { return AboveBlocks.TRIM.get().defaultBlockState(); }
    private static BlockState glow() { return AboveBlocks.GLOW_PANEL.get().defaultBlockState(); }
    private static BlockState warn() { return AboveBlocks.WARNING_LIGHT.get().defaultBlockState(); }
    private static BlockState glass() { return AboveBlocks.VOID_GLASS.get().defaultBlockState(); }

    /** The station block at a position, or null for void. */
    public static @Nullable BlockState at(int x, int y, int z) {
        if (y < FLOOR - 4 || y > FLOOR + 14) return null;
        int ax = Math.abs(x), az = Math.abs(z);
        if (ax > EXTENT || az > EXTENT) return null;
        for (Fixed f : fixed()) {
            BlockPos p = f.pos();
            if (p.getX() == x && p.getY() == y && p.getZ() == z) return f.state();
        }
        double r = Math.sqrt((double) x * x + (double) z * z);
        int dy = y - FLOOR;

        // spokes along the axes, from the hub to the ring
        boolean onX = az <= 3 && ax >= HUB - 1 && ax <= RING_IN + 1;
        boolean onZ = ax <= 3 && az >= HUB - 1 && az <= RING_IN + 1;
        if (onX || onZ) {
            int lateral = onX ? az : ax;
            int along = onX ? ax : az;
            if (dy == 0) return lateral <= 2 ? (lateral == 0 && along % 4 == 0 ? glow() : panel()) : plating();
            if (dy == -1) return lateral <= 3 ? (lateral == 0 ? glow() : plating()) : null;
            if (dy >= 1 && dy <= 4) {
                if (lateral <= 2) return null; // corridor air
                return dy == 1 || dy == 4 || along % 6 == 0 ? plating() : glass();
            }
            if (dy == 5) return lateral <= 3 ? (lateral == 0 ? glow() : plating()) : null;
            if (onX && onZ) return null;
        }

        // hub: floor, underside keel and a glass dome
        if (r <= HUB + 0.5) {
            if (dy == 0) return r > HUB - 0.5 ? trim() : (r < 1.5 ? glow() : panel());
            if (dy == -1) return r <= HUB - 1 ? (((int) Math.round(r)) % 4 == 0 ? glow() : plating()) : null;
            if (dy == -2 && r <= 7.5) return plating();
            if (dy == -3 && r <= 3.5) return r < 1.5 ? glow() : plating();
        }
        if (dy >= 1 && dy <= 13) {
            double d = Math.sqrt(r * r + dy * dy);
            if (d >= HUB - 0.5 && d < HUB + 0.5) {
                boolean rib = ax <= 0 || az <= 0 || ax == az || dy == 6;
                return dy == 12 && r < 1.5 ? glow() : rib ? plating() : glass();
            }
        }

        // the outer ring corridor
        if (r >= RING_IN - 0.5 && r < RING_OUT + 0.5) {
            boolean innerWall = r < RING_IN + 0.5;
            boolean outerWall = r >= RING_OUT - 0.5;
            double ang = Math.toDegrees(Math.atan2(z, x));
            boolean rib = Math.abs(ang % 30.0) < 2.0;
            if (dy == 0) return innerWall || outerWall ? trim() : panel();
            if (dy == -1) return ((int) Math.round(r) == 37 && ((x + z) & 3) == 0) || outerWall ? glow() : plating();
            if (dy >= 1 && dy <= 5) {
                if (!innerWall && !outerWall) return null;
                if (innerWall && (az <= 2 || ax <= 2)) return null; // spoke doorways
                if (dy == 1 || dy == 5 || rib) return plating();
                return glass();
            }
            if (dy == 6) {
                if (Math.abs(r - 37) < 0.5 && ((x * 7 + z * 3) & 7) == 0) return glow();
                return rib && outerWall ? warn() : plating();
            }
        }
        return null;
    }
}
