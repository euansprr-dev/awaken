package dev.awaken.finds.kit;

import dev.awaken.finds.Scene;
import dev.awaken.ground.GroundCover;
import dev.awaken.ground.cover.PebblesBlock;
import dev.awaken.ground.cover.TwigsBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dressing a scene so it sits in the land (SPEC §58.8): the worn ground round it, what lies on that ground, an old
 * road, a cairn. Everything fades with distance from the middle and nothing is laid on a regular pattern, so the edge
 * of a find is never a line.
 */
public final class Dress {
    private Dress() {}

    public static BlockState pebbles(Scene s) {
        return GroundCover.PEBBLES.get().defaultBlockState().setValue(PebblesBlock.KIND, s.rnd.nextInt(4)).setValue(PebblesBlock.STONES, 1 + s.rnd.nextInt(3));
    }

    public static BlockState twigs(Scene s) {
        return GroundCover.TWIGS.get().defaultBlockState().setValue(TwigsBlock.WOOD, s.rnd.nextInt(3))
                .setValue(TwigsBlock.FACING, Direction.from2DDataValue(s.rnd.nextInt(4)));
    }

    public static BlockState litter(Scene s) {
        BlockState b = Blocks.LEAF_LITTER.defaultBlockState();
        var seg = b.getBlock().getStateDefinition().getProperty("segment_amount");
        if (seg instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) b = b.setValue(ip, 1 + s.rnd.nextInt(3));
        return b;
    }

    /**
     * The worn ground round a find: path and bare earth where feet went, thinning to nothing at {@code r}; pebbles,
     * twigs and litter lying on it. Leaves alone any column the scene has already built on, and anything under water.
     */
    public static void skirt(Scene s, int r, Mats m) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z) / r;
                if (d > 1 || s.depth(x, z) > 0) continue;
                int g = s.g(x, z);
                if (s.taken(x, g, z) || s.taken(x, g + 1, z)) continue;
                double worn = (1 - d) * (1 - d), roll = s.rnd.nextDouble();
                if (roll < worn * 0.55) s.floor(x, z, m.path());
                else if (roll < worn * 0.55 + (1 - d) * 0.22) s.floor(x, z, m.loose());
                double lie = s.rnd.nextDouble();
                boolean pathNow = s.get(x, g, z) == m.path() && m.path().is(Blocks.DIRT_PATH);
                if (pathNow) continue; // a path block is lower than a full one: nothing lies on it
                if (lie < 0.07 * (1.2 - d)) s.on(x, z, pebbles(s));
                else if (lie < 0.11 * (1.2 - d)) s.on(x, z, twigs(s));
                else if (lie < 0.16 * (1.2 - d)) s.on(x, z, litter(s));
            }
    }

    /**
     * An old road along the kind's own north-south line from {@code z0} to {@code z1}: cobble showing through the
     * turf, two ruts, ragged edges. {@code half} is how far it reaches either side of the middle.
     */
    public static void road(Scene s, int z0, int z1, int half, Mats m) {
        for (int z = z0; z <= z1; z++) {
            int wander = (int) Math.round(Math.sin(z * 0.21 + s.site.seed() % 7) * 0.8);
            for (int x = -half - 1; x <= half + 1; x++) {
                int ax = Math.abs(x - wander);
                if (s.depth(x, z) > 0 || s.taken(x, s.g(x, z), z)) continue;
                double roll = s.rnd.nextDouble();
                if (ax > half) {
                    if (roll < 0.25) s.floor(x, z, m.loose());
                    continue;
                }
                if (ax == 1 && roll < 0.7) s.floor(x, z, m.path());
                else if (roll < 0.42) s.floor(x, z, m.stone());
                else if (roll < 0.52) s.floor(x, z, m.mossy());
                else if (roll < 0.74) s.floor(x, z, m.loose());
                if (roll > 0.93 && !(s.get(x, s.g(x, z), z) == m.path() && m.path().is(Blocks.DIRT_PATH))) s.on(x, z, pebbles(s));
            }
        }
    }

    /** A cairn {@code h} blocks high on a column: its body, and flat stones as the top. Returns the height of its top block above the ground. */
    public static int cairn(Scene s, int x, int z, int h, int topStones) {
        for (int k = 0; k < h - 1; k++) s.up(x, z, k, Props.CAIRN.get().of(k == 0 ? 1 : s.rnd.nextInt(2) * 3, Direction.from2DDataValue(s.rnd.nextInt(4))));
        s.up(x, z, h - 1, Props.FLAT_STONES.get().of(Math.max(0, Math.min(3, topStones - 1)), Direction.from2DDataValue(s.rnd.nextInt(4))));
        return h - 1;
    }

    /**
     * A crown of leaves round a point {@code k} above a column's ground: everything within {@code r - 1} steps, and a
     * ragged outer layer. Every leaf touches one nearer the middle, so nothing hangs loose; the middle itself is left
     * for the trunk.
     */
    public static void crown(Scene s, int cx, int cz, int k, int r, BlockState leaf) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                for (int y = -1; y <= r; y++) {
                    int d = Math.abs(x) + Math.abs(z) + Math.abs(y) * (y < 0 ? 2 : 1);
                    // the middle is always there: the four arms hang together through it, and it sits on the trunk
                    if (d > r || (d == r && d > 0 && s.rnd.nextBoolean())) continue;
                    int dy = s.g(cx, cz) + 1 + k + y;
                    if (!s.taken(cx + x, dy, cz + z)) s.put(cx + x, dy, cz + z, leaf);
                }
    }

    /** A few loose things of one kind lying within {@code r} of a point, never where something already is. */
    public static void scatter(Scene s, int cx, int cz, int r, int n, java.util.function.Supplier<BlockState> what) {
        for (int i = 0, tries = 0; i < n && tries < n * 8; tries++) {
            int x = cx + s.rnd.nextInt(2 * r + 1) - r, z = cz + s.rnd.nextInt(2 * r + 1) - r, g = s.g(x, z);
            if (s.depth(x, z) > 0 || s.taken(x, g + 1, z) || (s.get(x, g, z) != null && s.get(x, g, z).is(Blocks.DIRT_PATH))) continue;
            s.on(x, z, what.get());
            i++;
        }
    }

    /** Moss on the top of a stone at a height above a column's ground, about one time in {@code oneIn}. */
    public static void moss(Scene s, int x, int z, int k, int oneIn) {
        if (s.rnd.nextInt(oneIn) == 0 && !s.taken(x, s.g(x, z) + 1 + k, z)) s.up(x, z, k, Blocks.MOSS_CARPET.defaultBlockState());
    }
}
