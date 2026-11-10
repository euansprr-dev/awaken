package dev.awaken.finds.kit;

import dev.awaken.finds.Scene;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import static dev.awaken.finds.kit.B.*;

/**
 * The monument kit (SPEC §61.2): olden stone as working parts, in the order a monument is built. Bones ({@link #mass},
 * {@link #pier}, {@link #column}), courses ({@link #face}: no wall is one flat plane), pieces ({@link #colossus},
 * {@link #lamp}), then one pass over everything built, {@link #age}: break it along a ragged line, weather it (worn,
 * dark under every ledge, mossy where it is low and wet), crumble its edges, drop rubble where masonry went, and let fall
 * whatever is no longer held up. Heights are relative to the heart's ground: 0 is the ground's top block, 1 the first air.
 */
public final class M {
    private M() {}

    public static BlockState stone() { return st(Olden.STONE.get()); }
    public static BlockState block() { return st(Olden.BLOCK.get()); }
    public static BlockState band() { return st(Olden.BAND.get()); }
    public static BlockState panel() { return st(Olden.PANEL.get()); }
    public static BlockState glyph() { return st(Olden.GLYPH.get()); }
    public static BlockState flag() { return st(Olden.FLAG.get()); }
    public static BlockState mosaic() { return st(Olden.MOSAIC.get()); }
    public static BlockState rubble() { return st(Olden.RUBBLE.get()); }
    public static BlockState bronze() { return st(Olden.BRONZE.get()); }
    public static BlockState bronzeBand() { return st(Olden.BRONZE_BAND.get()); }
    public static BlockState door() { return st(Olden.DOOR.get()); }
    public static BlockState seal() { return st(Olden.SEAL.get()); }
    public static BlockState lichen() { return st(Olden.LICHEN.get()); }
    public static BlockState capital() { return st(Olden.CAPITAL.get()); }
    public static BlockState wall() { return st(Olden.WALL.get()); }
    public static BlockState stairs() { return st(Olden.STONE_STAIRS.get()); }
    public static BlockState blockStairs() { return st(Olden.BLOCK_STAIRS.get()); }
    public static BlockState slab() { return st(Olden.STONE_SLAB.get()); }
    public static BlockState blockSlab() { return st(Olden.BLOCK_SLAB.get()); }
    public static BlockState pillar(Direction.Axis a) { return axis(st(Olden.PILLAR.get()), a); }

    public static BlockState lamp(boolean lit) {
        return st(Olden.LAMP.get()).setValue(Olden.Lamp.LIT, lit);
    }

    /** The stone of a course: every fifth is dressed block, the rest ashlar. */
    public static BlockState course(int y) {
        return Math.floorMod(y, 5) == 0 ? block() : stone();
    }

    // ---- bones ------------------------------------------------------------------------------------------------------

    /** Solid coursed masonry in a box. */
    public static void mass(Scene s, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = y0; y <= y1; y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) s.put(x, y, z, course(y));
    }

    /** The four walls of a box, one block thick, coursed. */
    public static void shell(Scene s, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (x == x0 || x == x1 || z == z0 || z == z1) s.put(x, y, z, course(y));
    }

    /**
     * A pier standing on the land's own ground: a core {@code 2*half+1} square from the ground under each column up to
     * {@code y1}, a battered foot one wider for {@code foot} courses with a sloped top, a band every ten courses, and a
     * capital that corbels out under whatever it carries.
     */
    public static void pier(Scene s, int cx, int cz, int half, int y1, int foot) {
        for (int x = -half - 1; x <= half + 1; x++)
            for (int z = -half - 1; z <= half + 1; z++) {
                boolean core = Math.abs(x) <= half && Math.abs(z) <= half;
                boolean corner = Math.abs(x) == half + 1 && Math.abs(z) == half + 1;
                int g = Math.min(0, s.g(cx + x, cz + z)) - 1;
                if (core) {
                    for (int y = g; y <= y1; y++) s.put(cx + x, y, cz + z, y > 0 && y % 10 == 0 ? band() : course(y));
                } else if (!corner) {
                    for (int y = g; y <= foot; y++) s.put(cx + x, y, cz + z, block());
                    Direction in = Math.abs(x) == half + 1 ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
                    s.put(cx + x, foot + 1, cz + z, stair(blockStairs(), in));
                    s.put(cx + x, y1 - 1, cz + z, stairT(blockStairs(), in));
                    s.put(cx + x, y1, cz + z, block());
                } else {
                    for (int y = g; y <= foot - 2; y++) s.put(cx + x, y, cz + z, block());
                    s.put(cx + x, foot - 1, cz + z, blockSlab());
                    s.put(cx + x, y1, cz + z, block());
                }
            }
    }

    /** A column: a base, a fluted shaft, a capital. */
    public static void column(Scene s, int x, int z, int y0, int y1) {
        s.put(x, y0, z, capital());
        for (int y = y0 + 1; y < y1; y++) s.put(x, y, z, pillar(Direction.Axis.Y));
        s.put(x, y1, z, capital());
    }

    // ---- courses ----------------------------------------------------------------------------------------------------

    /**
     * Relief on a wall that already stands: the wall runs from {@code a0} to {@code a1} along x (when {@code out} is
     * north or south) or z, at {@code plane} on the other axis, from {@code y0} to {@code y1}. A plinth stands proud at
     * the foot with a sloped top; pilasters every {@code bay} blocks rise to a capital; a carved band runs at two
     * thirds; panels are set into the wall between the pilasters; a cornice overhangs the top.
     */
    public static void face(Scene s, Direction out, int a0, int a1, int plane, int y0, int y1, int bay) {
        boolean alongX = out.getAxis() == Direction.Axis.Z;
        int ox = out.getStepX(), oz = out.getStepZ(), bandY = y0 + (y1 - y0) * 2 / 3;
        Direction in = out.getOpposite();
        for (int a = a0; a <= a1; a++) {
            int x = alongX ? a : plane, z = alongX ? plane : a;
            int px = x + ox, pz = z + oz;                       // one block proud of the wall
            boolean pilaster = (a - a0) % bay == 0 || a == a1;
            s.put(px, y0, pz, block());
            s.put(px, y0 + 1, pz, block());
            s.put(px, y0 + 2, pz, pilaster ? block() : stair(blockStairs(), in));
            if (pilaster) {
                for (int y = y0 + 3; y <= y1 - 3; y++) s.put(px, y, pz, y == bandY ? band() : stone());
                s.put(px, y1 - 2, pz, capital());
            }
            s.put(x, bandY, z, band());
            int mid = (a - a0) % bay;
            if (!pilaster && bay >= 4 && mid >= 1 && mid <= bay - 1 && y1 - y0 >= 9) {
                int py = y0 + 4;
                if (mid == bay / 2 || (bay % 2 == 0 && mid == bay / 2 - 1)) for (int y = py; y <= Math.min(py + 2, bandY - 1); y++) s.put(x, y, z, panel());
            }
            s.put(px, y1 - 1, pz, stairT(blockStairs(), in));
            s.put(px, y1, pz, blockSlab());
        }
    }

    // ---- pieces -----------------------------------------------------------------------------------------------------

    /**
     * A standing figure in a long robe, {@code h} tall with its plinth, its back to {@code facing}'s opposite. Three
     * wide at the robe, five at the shoulders, hands folded. {@code lost}: 0 whole, 1 the head gone, 2 an arm gone.
     */
    public static void colossus(Scene s, int x, int y, int z, Direction facing, int h, int lost) {
        boolean ns = facing.getAxis() == Direction.Axis.Z;      // wide along x when it faces north or south
        int fx = facing.getStepX(), fz = facing.getStepZ();
        for (int w = -2; w <= 2; w++)
            for (int d = 0; d <= 1; d++) {
                int bx = x + (ns ? w : -fx * d), bz = z + (ns ? -fz * d : w);
                boolean arm = Math.abs(w) == 2, gone = lost == 2 && w == 2;
                s.put(bx, y, bz, block());                      // the plinth, five by two
                if (!arm) {
                    for (int k = 1; k <= h - 6; k++) s.put(bx, y + k, bz, k % 4 == 0 ? block() : stone());   // the robe
                    s.put(bx, y + h - 5, bz, block());          // the shoulders' middle
                    for (int k = h - 4; k <= h - 2; k++) s.put(bx, y + k, bz, w == 0 && d == 0 && k == h - 3 ? panel() : block());   // the head, a mask for a face
                    s.put(bx, y + h - 1, bz, w == 0 ? block() : blockSlab());   // the crown
                } else if (d == 0 && !gone) {
                    s.put(bx, y + h - 5, bz, block());          // a shoulder, and the arm hanging from it
                    for (int k = h - 8; k <= h - 6; k++) s.put(bx, y + k, bz, stone());
                    s.put(bx, y + h - 4, bz, blockSlab());
                } else if (d == 0) {
                    s.put(bx, y + h - 5, bz, slab());           // where the arm broke off
                }
            }
        s.put(x + fx, y + h - 7, z + fz, stairT(blockStairs(), facing.getOpposite()));   // the folded hands
        if (lost == 1) {                                        // the head gone: a stump of neck
            for (int w = -1; w <= 1; w++)
                for (int d = 0; d <= 1; d++)
                    for (int k = h - 4; k <= h - 1; k++) {
                        BlockPos p = s.rel(x + (ns ? w : -fx * d), y + k, z + (ns ? -fz * d : w));
                        s.blocks().remove(BlockPos.asLong(p.getX(), p.getY(), p.getZ()));
                    }
            s.put(x, y + h - 4, z, slab());
        }
    }

    /** A lamp in a stone housing on a short post; lit or waiting. */
    public static void lampPost(Scene s, int x, int y, int z, int h, boolean lit) {
        for (int k = 0; k < h; k++) s.put(x, y + k, z, wall());
        s.put(x, y + h, z, lamp(lit));
        s.put(x, y + h + 1, z, blockSlab());
    }

    /** A heap of fallen masonry: rubble and whole stones, {@code r} across and about half as high, half sunk. */
    public static void heap(Scene s, int x, int z, double r) {
        blob(s, x, s.g(x, z) + (int) (r * 0.25), z, r, r * 0.6, r * 0.85, hh -> hh < 110 ? rubble() : hh < 170 ? st(Olden.WORN.get()) : hh < 215 ? stone() : block());
    }

    // ---- age --------------------------------------------------------------------------------------------------------

    /**
     * Breaks what stands in a box along a ragged line: every column loses what is above {@code keep} plus up to
     * {@code jag} more or less (soft noise, so whole runs stand or fall together). In kind coordinates.
     */
    public static void ruin(Scene s, int x0, int z0, int x1, int z1, int keep, int jag, int yTop) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int line = keep + (int) Math.round((n(s, x * 1.7 + 11, z * 1.7 - 5, 3.2) - 0.5) * 2 * jag);
                for (int y = line + 1; y <= yTop; y++) {
                    BlockPos p = s.rel(x, y, z);
                    s.blocks().remove(BlockPos.asLong(p.getX(), p.getY(), p.getZ()));
                }
            }
    }

    /**
     * The one pass over everything built (SPEC §61.2, "age"). {@code wet}: how far up moss climbs. {@code wear}: 0..100,
     * how much of the ashlar is worn and how many edges have crumbled.
     */
    public static void age(Scene s, int wet, int wear) {
        Long2ObjectOpenHashMap<BlockState> m = s.blocks();
        Block stone = Olden.STONE.get(), block = Olden.BLOCK.get(), stairs = Olden.STONE_STAIRS.get(), bstairs = Olden.BLOCK_STAIRS.get(), slab = Olden.STONE_SLAB.get();
        LongArrayList keys = new LongArrayList(m.keySet());
        // edges first: a cube with open air over it and over one side has lost its arris
        for (long k : keys) {
            BlockState b = m.get(k);
            if (b == null || !(b.is(stone) || b.is(block))) continue;
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (y < 2 || solid(m, x, y + 1, z)) continue;
            int hh = h(s, x, y + 31, z);
            if (hh >= wear * 2) continue;
            int open = 0;
            Direction side = null;
            for (int d = 0; d < 4; d++) if (!solid(m, x + DX[d], y, z + DZ[d])) { open++; side = DIR[d]; }
            if (open == 0 || open > 2) continue;
            if (hh % 3 == 0) m.put(k, st(Olden.STONE_SLAB.get()));
            else m.put(k, stair(st(Olden.WORN_STAIRS.get()), side.getOpposite()));
        }
        for (long k : keys) {
            BlockState b = m.get(k);
            if (b == null || !Olden.is(b)) continue;
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            // shade: a face under a real ledge (something solid within two above the air in front of it)
            boolean shaded = false;
            for (int d = 0; d < 4 && !shaded; d++) {
                int ax = x + DX[d], az = z + DZ[d];
                if (solid(m, ax, y, az)) continue;
                shaded = solid(m, ax, y + 1, az) || solid(m, ax, y + 2, az);
            }
            double patch = n(s, x * 1.3 + y * 0.7, z * 1.3 - y * 0.4, 4.0);
            int hh = h(s, x, y, z);
            boolean topOpen = !solid(m, x, y + 1, z);
            boolean low = y <= wet + (int) (patch * 4) - 2;
            if (b.is(stone)) {
                if (shaded) m.put(k, st(Olden.DARK.get()));
                else if (low && (topOpen || patch > 0.55) && hh < 150) m.put(k, st(Olden.MOSSY.get()));
                else if (patch * 100 < wear * 0.9 || hh < wear / 3) m.put(k, st(Olden.WORN.get()));
            } else if (b.is(block)) {
                if (shaded) m.put(k, st(Olden.BLOCK_DARK.get()));
            } else if (b.is(stairs) || b.is(bstairs)) {
                BlockState to = shaded ? st(Olden.DARK_STAIRS.get()) : patch * 100 < wear && b.is(stairs) ? st(Olden.WORN_STAIRS.get()) : null;
                if (to != null) m.put(k, to.setValue(StairBlock.FACING, b.getValue(StairBlock.FACING)).setValue(StairBlock.HALF, b.getValue(StairBlock.HALF)));
            } else if (b.is(slab)) {
                BlockState to = shaded ? st(Olden.DARK_SLAB.get()) : patch * 100 < wear ? st(Olden.WORN_SLAB.get()) : null;
                if (to != null) m.put(k, to.setValue(SlabBlock.TYPE, b.getValue(SlabBlock.TYPE)));
            }
            // growth: a mat of moss on low open tops, where it is wet
            if (topOpen && low && hh > 205 && b.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && !m.containsKey(BlockPos.asLong(x, y + 1, z))) m.put(BlockPos.asLong(x, y + 1, z), Blocks.MOSS_CARPET.defaultBlockState());
        }
        settle(s);
    }

    private static boolean solid(Long2ObjectOpenHashMap<BlockState> m, int x, int y, int z) {
        BlockState b = m.get(BlockPos.asLong(x, y, z));
        return b != null && !b.isAir() && b.canOcclude();
    }

    /** Lets fall whatever no longer touches the ground through its faces: the same flood the scenes test runs. */
    public static void settle(Scene s) {
        Long2ObjectOpenHashMap<BlockState> m = s.blocks();
        LongOpenHashSet reached = new LongOpenHashSet();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        for (var e : m.long2ObjectEntrySet()) {
            if (e.getValue().isAir() || e.getValue().is(Blocks.LIGHT)) continue;
            long k = e.getLongKey();
            if (BlockPos.getY(k) <= s.groundAt(BlockPos.getX(k), BlockPos.getZ(k)) + 1) {
                reached.add(k);
                q.enqueue(k);
            }
        }
        while (!q.isEmpty()) {
            long k = q.dequeueLong();
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            for (int d = 0; d < 6; d++) {
                long nb = BlockPos.asLong(x + (d == 0 ? 1 : d == 1 ? -1 : 0), y + (d == 2 ? 1 : d == 3 ? -1 : 0), z + (d == 4 ? 1 : d == 5 ? -1 : 0));
                BlockState b = m.get(nb);
                if (b != null && !b.isAir() && !b.is(Blocks.LIGHT) && reached.add(nb)) q.enqueue(nb);
            }
        }
        LongArrayList gone = new LongArrayList();
        for (var e : m.long2ObjectEntrySet()) {
            BlockState b = e.getValue();
            if (!b.isAir() && !b.is(Blocks.LIGHT) && !reached.contains(e.getLongKey())) gone.add(e.getLongKey());
        }
        for (long k : gone) m.remove(k);
    }

    /** A stair of the family the right way up, worn or whole by the place. */
    public static BlockState step(Scene s, int x, int z, Direction up) {
        return stair(h(s, x, 77, z) < 60 ? st(Olden.WORN_STAIRS.get()) : stairs(), up);
    }

    static boolean isTop(BlockState b) {
        return b.hasProperty(StairBlock.HALF) && b.getValue(StairBlock.HALF) == Half.TOP;
    }
}
