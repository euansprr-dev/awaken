package dev.awaken.world.fauna.lair;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;

/**
 * P3's two lairs, as code like the other nine ({@link LairBuilder}): the Lantern Maw's Sunken Shrine on the floor of the
 * deep sea and the Thousandfoot's Crystal Nest carved out deep underground. Every random choice is drawn in the same
 * order whatever chunk is being built (columns decide by {@link LairBuilder#noise}); blocks only land where the Sink says
 * the column is this chunk's.
 *
 * <p>The shrine never clears: under the sea everything round it is water already (and a shrine built on dry land for a
 * test stays dry). Its waterloggable blocks are waterlogged. The nest carves a dome of air (its floor sealed, a rock
 * shell round its lower walls, two tunnels out) so the caves it meets join it.
 */
final class DeepLairs {
    private DeepLairs() {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState wet(BlockState s) {
        return s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, true) : s;
    }

    private static void put(LairBuilder.Sink s, int x, int y, int z, BlockState state) {
        if (s.here(x, z)) s.set(x, y, z, state);
    }

    private static double dist(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    // ---- the Sunken Shrine (the Lantern Maw) ---------------------------------------------------------------------------

    /**
     * A drowned shrine of prismarine: a ragged floor of bricks round a dark dais lit by four sea lanterns (the den, where
     * the Maw hangs over its own light), a ring of eight broken columns (one fallen), a tall gate on the north with a
     * lantern hung from its lintel (the focal element), bones of what came too close, kelp forests round the edge,
     * seagrass in the cracks, sea pickles on the column tops; coral round the edge in warm water.
     */
    static void sunkenShrine(LairBuilder.Sink s, RandomSource r, long seed, int variant) {
        int rad = 8;
        // the floor: dark prismarine round the dais, bricks, rough prismarine in a ragged outer band, sand and gravel patches
        for (int x = -rad - 1; x <= rad + 1; x++) {
            for (int z = -rad - 1; z <= rad + 1; z++) {
                double d = dist(x, z), n = LairBuilder.patch(seed, x, z);
                if (d > rad + 0.4) continue;
                if (d > rad - 1.2 && LairBuilder.noise(seed + 1, x, z) < 0.4) continue; // the sea floor shows through
                BlockState b = d < 3.2 ? Blocks.DARK_PRISMARINE.defaultBlockState()
                        : n < 0.2 ? Blocks.SAND.defaultBlockState() : n < 0.3 ? Blocks.GRAVEL.defaultBlockState()
                        : d < 6.2 ? Blocks.PRISMARINE_BRICKS.defaultBlockState()
                        : n < 0.6 ? Blocks.PRISMARINE.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState();
                if (!s.here(x, z)) continue;
                s.set(x, 0, z, b);
                s.fillDown(x, -1, z, Blocks.PRISMARINE.defaultBlockState(), 3);
                // seagrass in the cracks and on the sand
                double g = LairBuilder.noise(seed + 2, x, z);
                if (d > 3.5 && (b.is(Blocks.SAND) || b.is(Blocks.GRAVEL) || g < 0.12)) s.set(x, 1, z, Blocks.SEAGRASS.defaultBlockState());
            }
        }
        // the dais: a low disc of dark prismarine with four sea lanterns set in its rim; the den is above its centre
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (dist(x, z) > 2.6) continue;
                boolean lamp = (Math.abs(x) == 2 && z == 0) || (Math.abs(z) == 2 && x == 0);
                put(s, x, 1, z, lamp ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
            }
        }
        // eight columns round the floor, broken at different heights; one lies fallen across the floor
        int fallen = r.nextInt(8);
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int x = (int) Math.round(6.4 * Math.cos(a)), z = (int) Math.round(6.4 * Math.sin(a));
            int h = 2 + r.nextInt(6);
            boolean lantern = r.nextInt(3) == 0;
            int pickles = 1 + r.nextInt(4);
            if (k == fallen) {
                // it fell outward, tangent to the ring
                int dx = (int) Math.round(-Math.sin(a)), dz = (int) Math.round(Math.cos(a));
                for (int i = 0; i < 4; i++) put(s, x + dx * i, 1, z + dz * i, Blocks.PRISMARINE_BRICKS.defaultBlockState());
                put(s, x, 1, z, Blocks.DARK_PRISMARINE.defaultBlockState());
                continue;
            }
            if (s.here(x, z)) {
                s.fillDown(x, 0, z, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 5);
                for (int y = 1; y <= h; y++) s.set(x, y, z, y == h && h >= 5 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
                if (h >= 5 && lantern) s.set(x, h + 1, z, Blocks.SEA_LANTERN.defaultBlockState());
                else s.set(x, h + 1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, pickles).setValue(SeaPickleBlock.WATERLOGGED, true));
            }
        }
        // the gate on the north: two tall pillars, a lintel of dark slabs, a lantern hung under it (it is seen from afar)
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 2;
            if (!s.here(x, -7)) continue;
            s.fillDown(x, 0, -7, Blocks.PRISMARINE_BRICKS.defaultBlockState(), 5);
            for (int y = 1; y <= 8; y++) s.set(x, y, -7, y % 4 == 0 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
        }
        for (int x = -3; x <= 3; x++) {
            put(s, x, 9, -7, wet(Blocks.DARK_PRISMARINE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, Math.abs(x) <= 2 ? SlabType.BOTTOM : SlabType.TOP)));
        }
        put(s, 0, 8, -7, Blocks.SEA_LANTERN.defaultBlockState());
        // what came too close: bones and a skull on the floor
        for (int k = 0; k < 5; k++) {
            int x = r.nextInt(11) - 5, z = r.nextInt(11) - 5;
            int rot = r.nextInt(16);
            if (dist(x, z) < 3.2) continue;
            put(s, x, 1, z, k == 0 ? Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rot) : Blocks.BONE_BLOCK.defaultBlockState());
        }
        // kelp forests round the edge (or coral in warm water), and pickles in the gaps
        for (int k = 0; k < 18; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = 8.6 + r.nextDouble() * 2.2;
            int x = (int) Math.round(d * Math.cos(a)), z = (int) Math.round(d * Math.sin(a));
            int h = 3 + r.nextInt(8);
            int coral = r.nextInt(4);
            if (!s.here(x, z)) continue;
            if (variant == 1 && k % 2 == 0) {
                BlockState block = switch (coral) {
                    case 0 -> Blocks.BRAIN_CORAL_BLOCK.defaultBlockState();
                    case 1 -> Blocks.TUBE_CORAL_BLOCK.defaultBlockState();
                    case 2 -> Blocks.FIRE_CORAL_BLOCK.defaultBlockState();
                    default -> Blocks.HORN_CORAL_BLOCK.defaultBlockState();
                };
                s.set(x, 0, z, block);
                s.set(x, 1, z, wet((coral % 2 == 0 ? Blocks.BRAIN_CORAL_FAN : Blocks.TUBE_CORAL_FAN).defaultBlockState()));
                continue;
            }
            s.set(x, 0, z, Blocks.GRAVEL.defaultBlockState());
            for (int y = 1; y < h; y++) s.set(x, y, z, Blocks.KELP_PLANT.defaultBlockState());
            s.set(x, h, z, Blocks.KELP.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 25));
        }
    }

    // ---- the Crystal Nest (the Thousandfoot) ---------------------------------------------------------------------------

    /**
     * A geode-like chamber deep underground: a dome of air nine blocks across with a floor in rings (smooth basalt at
     * the wall, calcite, then tuff and dripstone), stalactites and stalagmites, amethyst growing from the floor and the
     * roof, a pool, the beast's nest at the den (a ring of bone round a bed of shed chitin), glow berries hanging over it
     * (the one warm light), and two tunnels out east and west. Under the lush caves: moss, azaleas, a spore blossom.
     */
    static void crystalNest(LairBuilder.Sink s, RandomSource r, long seed, int variant) {
        int rad = 9;
        boolean lush = variant == 1;
        // the dome: its height over each column, the floor in rings, the roof and the lower wall a shell of rock
        for (int x = -rad - 1; x <= rad + 1; x++) {
            for (int z = -rad - 1; z <= rad + 1; z++) {
                double d = dist(x, z);
                if (d > rad + 1.2 || !s.here(x, z)) continue;
                int h = height(d, rad);
                boolean wall = d > rad + 0.2;
                double n = LairBuilder.patch(seed, x, z);
                if (wall) {
                    for (int y = 0; y <= 3; y++) s.set(x, y, z, n < 0.5 ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                    continue;
                }
                BlockState floor = d > rad - 1.2 ? Blocks.SMOOTH_BASALT.defaultBlockState() : d > rad - 2.6 ? Blocks.CALCITE.defaultBlockState()
                        : lush && n < 0.45 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.4 ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.TUFF.defaultBlockState();
                s.set(x, 0, z, floor);
                s.fillDown(x, -1, z, Blocks.TUFF.defaultBlockState(), 3);
                for (int y = 1; y <= h; y++) s.set(x, y, z, AIR);
                s.set(x, h + 1, z, d > rad - 1.5 ? Blocks.CALCITE.defaultBlockState() : n < 0.5 ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                if (lush && floor.is(Blocks.MOSS_BLOCK) && LairBuilder.noise(seed + 3, x, z) < 0.35 && d > 3) s.set(x, 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
        // two tunnels out, east and west, three high
        for (int side = -1; side <= 1; side += 2) {
            for (int i = rad - 1; i <= LairStyle.EXTENT; i++) {
                int x = side * i;
                for (int z = -1; z <= 1; z++) {
                    if (!s.here(x, z)) continue;
                    s.set(x, 0, z, Blocks.TUFF.defaultBlockState());
                    for (int y = 1; y <= 3; y++) s.set(x, y, z, AIR);
                }
            }
        }
        // stalactites from the roof and stalagmites from the floor (dripstone caves) or hanging vines and moss (lush)
        for (int k = 0; k < 14; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = 2.5 + r.nextDouble() * (rad - 3.5);
            int x = (int) Math.round(d * Math.cos(a)), z = (int) Math.round(d * Math.sin(a));
            int len = 1 + r.nextInt(3);
            boolean up = k % 3 == 0;
            int h = height(dist(x, z), rad);
            if (!s.here(x, z) || (Math.abs(x) <= 2 && Math.abs(z - 1) <= 2)) continue;
            if (up) fang(s, x, 1, z, len, true);
            else fang(s, x, h, z, Math.min(len, h - 2), false);
        }
        // amethyst: clusters on the floor near the walls and a few from the roof
        for (int k = 0; k < 16; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = rad - 2.5 + r.nextDouble() * 1.8;
            int x = (int) Math.round(d * Math.cos(a)), z = (int) Math.round(d * Math.sin(a));
            int size = r.nextInt(3);
            boolean roof = k % 4 == 0;
            int h = height(dist(x, z), rad);
            BlockState c = (size == 0 ? Blocks.SMALL_AMETHYST_BUD : size == 1 ? Blocks.LARGE_AMETHYST_BUD : Blocks.AMETHYST_CLUSTER).defaultBlockState();
            if (!s.here(x, z)) continue;
            if (roof) {
                s.set(x, h + 1, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                s.set(x, h, z, c.setValue(AmethystClusterBlock.FACING, Direction.DOWN));
            } else {
                s.set(x, 0, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                s.set(x, 1, z, c.setValue(AmethystClusterBlock.FACING, Direction.UP));
            }
        }
        // a pool in the floor
        int px = 4 + r.nextInt(2), pz = -5;
        for (int x = px; x <= px + 2; x++) for (int z = pz; z <= pz + 1; z++) put(s, x, 0, z, Blocks.WATER.defaultBlockState());
        // the nest at the den: a ring of bone round a bed of shed chitin (deepslate tiles) and a crown of amethyst
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 4; z++) {
                double d = dist(x, z - 1);
                if (d > 3.3) continue;
                if (d > 2.4) put(s, x, 1, z, (x + z) % 3 == 0 ? Blocks.SMALL_AMETHYST_BUD.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                else put(s, x, 0, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
            }
        }
        put(s, -2, 1, -2, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)));
        // the one warm light: glow berries hanging over the nest
        for (int k = 0; k < 3; k++) {
            int x = k == 0 ? -2 : k == 1 ? 2 : 0, z = k == 2 ? 4 : 0;
            int h = height(dist(x, z), rad);
            int len = 2 + r.nextInt(2);
            if (!s.here(x, z)) continue;
            for (int i = 0; i < len; i++) {
                boolean tip = i == len - 1;
                BlockState v = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, i % 2 == 1 || tip);
                s.set(x, h - i, z, v);
            }
        }
        if (lush) {
            put(s, 0, height(0, rad), 1, Blocks.SPORE_BLOSSOM.defaultBlockState());
            for (int k = 0; k < 4; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                int x = (int) Math.round(6 * Math.cos(a)), z = (int) Math.round(6 * Math.sin(a));
                put(s, x, 1, z, Blocks.AZALEA.defaultBlockState());
                put(s, x, 0, z, Blocks.MOSS_BLOCK.defaultBlockState());
            }
        }
    }

    /** Height of the dome's air above the floor over a column {@code d} from the centre (7 at the middle). */
    static int height(double d, int rad) {
        double t = Math.min(1.0, d / (rad + 0.6));
        return Math.max(2, (int) Math.round(7.0 * Math.sqrt(1.0 - t * t)));
    }

    /** A dripstone fang {@code len} long from (x, y, z): up from the floor, or down from the roof (y is its root). */
    private static void fang(LairBuilder.Sink s, int x, int y, int z, int len, boolean up) {
        Direction dir = up ? Direction.UP : Direction.DOWN;
        for (int i = 0; i < len; i++) {
            SpeleothemThickness t = i == len - 1 ? SpeleothemThickness.TIP : i == len - 2 ? SpeleothemThickness.FRUSTUM : i == 0 ? SpeleothemThickness.BASE
                    : SpeleothemThickness.MIDDLE;
            BlockState st = Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, dir)
                    .setValue(PointedDripstoneBlock.THICKNESS, t);
            s.set(x, up ? y + i : y - i, z, st);
        }
    }

    @SuppressWarnings("unused")
    private static int clamp(int v, int lo, int hi) {
        return Mth.clamp(v, lo, hi);
    }
}
