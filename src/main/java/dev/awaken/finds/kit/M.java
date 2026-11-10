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
                    && !m.containsKey(BlockPos.asLong(x, y + 1, z)) && !(x == 0 && z == 0 && y + 1 == -2)) m.put(BlockPos.asLong(x, y + 1, z), Blocks.MOSS_CARPET.defaultBlockState());
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

    // ---- big pieces (SPEC §64.4) --------------------------------------------------------------------------------------

    /** Puts a figure's block: its own right, up and front, turned to face a way, or laid on its back with its head that way. */
    private static void fig(Scene s, int ox, int oy, int oz, Direction facing, boolean lying, int r, int u, int f, BlockState b) {
        int fx = facing.getStepX(), fz = facing.getStepZ(), rx = -fz, rz = fx;
        if (lying) s.put(ox + r * rx + u * fx, oy + f, oz + r * rz + u * fz, b);
        else s.put(ox + r * rx + f * fx, oy + u, oz + r * rz + f * fz, b);
    }

    /**
     * A giant in a long robe and a hood, {@code h} tall with its plinth (15 or more; {@link #colossus} is the small one),
     * head bowed, hands folded. {@code seated}: on a throne with its knees forward, the way they sit at a door.
     * {@code lost}: bit 1 the head, bit 2 an arm. {@code lying}: fallen on its back with its head toward {@code facing},
     * broken at the waist.
     */
    public static void giant(Scene s, int x, int y, int z, Direction facing, int h, boolean seated, int lost, boolean lying) {
        int hw = Math.max(2, Math.round(h / 9f)), hd = Math.max(1, hw - 1), hh = Math.max(1, hw - 1);
        int seat = seated ? Math.max(3, Math.round(h * 0.26f)) : 0, top = Math.round(h * (seated ? 0.66f : 0.62f)), waist = seated ? seat + 1 : Math.round(h * 0.4f);
        int back = lying ? hd : 0;                                // a lying figure rests on its back: front is up
        java.util.function.IntUnaryOperator brk = u -> lying && u > waist ? u + 2 : u;   // the break at the waist
        // plinth
        if (!lying) for (int r = -hw - 2; r <= hw + 2; r++) for (int f = -hd - 1; f <= (seated ? hd + hw + 3 : hd + 1); f++) fig(s, x, y, z, facing, false, r, 0, f, block());
        if (seated) {                                             // throne, thighs, shins and feet
            for (int r = -hw - 1; r <= hw + 1; r++)
                for (int u = 1; u <= seat; u++) for (int f = -hd - 1; f <= hd; f++) fig(s, x, y, z, facing, lying, r, u, f + back, u == seat ? block() : course(u));
            for (int r = -hw - 1; r <= hw + 1; r++) for (int u = seat + 1; u <= top + 1; u++) fig(s, x, y, z, facing, lying, r, u, -hd - 1 + back, u % 5 == 0 ? band() : block());
            for (int r = -hw; r <= hw; r++) {
                if (r == 0 && hw >= 2) continue;
                for (int f = hd + 1; f <= hd + hw + 1; f++) fig(s, x, y, z, facing, lying, r, seat + 1, f + back, block());
                for (int u = 1; u <= seat; u++) fig(s, x, y, z, facing, lying, r, u, hd + hw + 1 + back, u == 1 ? block() : stone());
                fig(s, x, y, z, facing, lying, r, 1, hd + hw + 2 + back, blockSlab());
            }
        }
        // robe (or torso), with a belt
        for (int u = seated ? seat + 1 : 1; u <= top; u++)
            for (int r = -hw; r <= hw; r++)
                for (int f = -hd; f <= hd; f++)
                    fig(s, x, y, z, facing, lying, r, brk.applyAsInt(u), f + back, u == waist ? band() : u % 4 == 0 || (f == hd && Math.floorMod(r, 2) == 1) ? block() : stone());
        if (!seated && !lying)                                    // the hem spreads
            for (int r = -hw - 1; r <= hw + 1; r++)
                for (int f = -hd - 1; f <= hd + 1; f++)
                    if ((Math.abs(r) == hw + 1) != (Math.abs(f) == hd + 1)) { fig(s, x, y, z, facing, false, r, 1, f, block()); fig(s, x, y, z, facing, false, r, 2, f, stair(blockStairs(), Math.abs(r) == hw + 1 ? (r > 0 ? facing.getCounterClockWise() : facing.getClockWise()) : (f > 0 ? facing.getOpposite() : facing))); }
        // shoulders and arms
        for (int r = -hw - 1; r <= hw + 1; r++) for (int f = -hd; f <= hd; f++) fig(s, x, y, z, facing, lying, r, brk.applyAsInt(top + 1), f + back, block());
        for (int side = -1; side <= 1; side += 2) {
            if (side > 0 && (lost & 2) != 0) { fig(s, x, y, z, facing, lying, side * (hw + 1), brk.applyAsInt(top), back, slab()); continue; }
            int elbow = seated ? seat + 2 : Math.round(h * 0.36f);
            for (int u = elbow; u <= top; u++) fig(s, x, y, z, facing, lying, side * (hw + 1), brk.applyAsInt(u), back, stone());
            if (seated) for (int f = 1; f <= hd + hw; f++) fig(s, x, y, z, facing, lying, side * (hw + 1), brk.applyAsInt(elbow), f + back, f == hd + hw ? block() : stone());
        }
        if (!seated) for (int r = -hw; r <= hw; r++) fig(s, x, y, z, facing, lying, r, brk.applyAsInt(Math.round(h * 0.36f) + 1), hd + 1 + back, block());   // the folded hands
        // head: a hood with a mask for a face, bowed forward a block
        if ((lost & 1) != 0) {
            for (int r = -hh; r <= hh; r++) fig(s, x, y, z, facing, lying, r, brk.applyAsInt(top + 2), back, slab());
            return;
        }
        int h0 = top + 2, h1 = h0 + 2 * hh, lean = seated ? 0 : 1;
        for (int u = h0; u <= h1; u++)
            for (int r = -hh; r <= hh; r++)
                for (int f = -hh; f <= hh; f++) {
                    boolean face = f == hh && Math.abs(r) < hh && u > h0 && u < h1;
                    fig(s, x, y, z, facing, lying, r, brk.applyAsInt(u), f + lean + back, face ? panel() : u == h0 + hh && f == hh ? dark() : block());
                }
        for (int r = -hh - 1; r <= hh + 1; r++)                   // the hood's brim and crown
            for (int f = -hh; f <= hh + 1; f++) {
                if (Math.abs(r) == hh + 1 && f > 0) continue;
                fig(s, x, y, z, facing, lying, r, brk.applyAsInt(h1 + 1), f + lean + back, Math.abs(r) <= hh && f <= hh ? block() : blockSlab());
            }
    }

    public static BlockState dark() { return st(Olden.BLOCK_DARK.get()); }

    /** Whether a block (or nothing) is one of the bronze ones: a ring's own stuff, which masonry built after it must not swallow. */
    public static boolean isBronze(BlockState b) {
        return b != null && (b.is(Olden.BRONZE.get()) || b.is(Olden.BRONZE_BAND.get()) || b.is(Olden.LAMP.get()));
    }

    /**
     * A ring standing or leaning in the air: {@code R} to the middle of its band, the band {@code w} wide in the ring's
     * own plane and {@code t} thick across it. The plane's normal is (nx, ny, nz). {@code at} gives the block for a
     * degree round the ring (0..359) or null where the ring is broken.
     */
    public static void ring(Scene s, int cx, int cy, int cz, double R, double w, double t, double nx, double ny, double nz, java.util.function.IntFunction<BlockState> at) {
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len; ny /= len; nz /= len;
        // a basis in the plane: u is level where it can be, v is what is left
        double ux = -nz, uy = 0, uz = nx, ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-6) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
        int b = (int) Math.ceil(R + w) + 1;
        for (int x = -b; x <= b; x++)
            for (int y = -b; y <= b; y++)
                for (int z = -b; z <= b; z++) {
                    double dn = x * nx + y * ny + z * nz;
                    if (Math.abs(dn) > t / 2) continue;
                    double qx = x - dn * nx, qy = y - dn * ny, qz = z - dn * nz, rad = Math.sqrt(qx * qx + qy * qy + qz * qz);
                    if (Math.abs(rad - R) > w / 2) continue;
                    int deg = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(qx * vx + qy * vy + qz * vz, qx * ux + qy * uy + qz * uz))), 360L);
                    BlockState st = at.apply(deg);
                    if (st != null) s.put(cx + x, cy + y, cz + z, st);
                }
    }

    /** What a ring of the old world is made of: bronze, with a stone marker every thirty degrees and a lamp at every ninety. */
    public static BlockState ringStuff(int deg, boolean lit) {
        int m = Math.floorMod(deg, 30);
        if (Math.floorMod(deg, 90) <= 2) return lamp(lit);
        return m <= 3 ? block() : m == 15 ? bronzeBand() : bronze();
    }

    /** Which blocks of a shape still stand. */
    @FunctionalInterface
    public interface Keep {
        boolean test(int x, int y, int z);
    }

    /**
     * A dome of masonry on a round wall's top: a shell {@code thick} deep with eight ribs standing proud of it, a band
     * every sixth course and an eye open at the crown. {@code keep} says which of it still stands (offsets from the
     * dome's middle at its springing).
     */
    public static void dome(Scene s, int cx, int cz, double R, int y0, int thick, Keep keep) {
        int b = (int) Math.ceil(R) + 2;
        for (int x = -b; x <= b; x++)
            for (int z = -b; z <= b; z++)
                for (int y = 0; y <= b; y++) {
                    double d = Math.sqrt(x * x + y * y + z * z), flat = Math.sqrt(x * x + z * z);
                    if (flat < 2.3 && y > R - thick - 1) continue;                     // the eye
                    double a = Math.toDegrees(Math.atan2(z, x)), off = Math.abs(((a % 45) + 45) % 45 - 22.5);
                    boolean rib = off > 22.5 - Math.toDegrees(0.8 / Math.max(1, flat)) && d > R - 0.2 && d <= R + 1.2 && flat > 2.5;
                    if (!(d > R - thick && d <= R) && !rib) continue;
                    if (!keep.test(x, y, z)) continue;
                    s.put(cx + x, y0 + y, cz + z, rib ? block() : y % 6 == 0 ? band() : d <= R - thick + 1 ? stone() : course(y0 + y));
                }
    }

    /**
     * A round tower's wall, one block thick: a band and a corbel ring every {@code storey} courses, a slit window on
     * each storey, turning as it climbs. Whatever is inside it is the caller's.
     */
    public static void tower(Scene s, int cx, int cz, double r, int y0, int y1, int storey) {
        int ir = (int) Math.ceil(r) + 2;
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++) {
                double d = Math.sqrt(x * x + z * z);
                boolean wall = d >= r - 0.5 && d < r + 0.5, proud = d >= r + 0.5 && d < r + 1.5;
                if (!wall && !proud) continue;
                double a = Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 360L);
                for (int y = y0; y <= y1; y++) {
                    int k = Math.floorMod(y - y0, storey), floor = (y - y0) / storey;
                    if (proud) {
                        if (k == storey - 1) s.put(cx + x, y, cz + z, blockSlab());
                        else if (k == storey - 2) s.put(cx + x, y, cz + z, stairT(blockStairs(), Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH)));
                        continue;
                    }
                    boolean slit = k >= 2 && k <= 4 && Math.abs(Math.floorMod((long) a - floor * 70L, 360L) - 180) > 174;
                    s.put(cx + x, y, cz + z, slit ? AIR : k == storey - 1 ? band() : course(y));
                }
            }
    }

    /**
     * A stair winding up inside a round tower: slabs in wedges round a newel, half a block a wedge, so it is walked and
     * not jumped. {@code r} is the clear radius inside the wall. Returns nothing; the newel is a fluted pillar.
     */
    public static void spiral(Scene s, int cx, int cz, int r, int y0, int y1) {
        int n = 12;                                               // wedges a turn: six blocks of rise, room to stand
        for (int i = 0; i < (y1 - y0) * 2; i++) {
            double a0 = i * 2 * Math.PI / n, a1 = a0 + 2 * Math.PI / n + 0.12;
            int y = y0 + i / 2;
            BlockState step = i % 2 == 0 ? blockSlab() : top(blockSlab());
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d < 0.9 || d > r + 0.3) continue;
                    double a = Math.atan2(z, x), rel = ((a - a0) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                    if (rel > a1 - a0) continue;
                    s.put(cx + x, y, cz + z, step);
                    for (int k = 1; k <= 3; k++) if (!(s.get(cx + x, y + k, cz + z) != null && Olden.is(s.get(cx + x, y + k, cz + z)) && s.get(cx + x, y + k, cz + z).getBlock() instanceof SlabBlock)) s.put(cx + x, y + k, cz + z, AIR);
                }
        }
        for (int y = y0; y <= y1; y++) s.put(cx, y, cz, pillar(Direction.Axis.Y));
    }

    /**
     * An arch between two piers, seen as the masonry over it: how deep the stone under a deck is at {@code t} (0..1
     * across the opening) for a round arch that rises {@code rise} above its springing. 0 at the springing would be the
     * whole height; callers fill from the deck down by this much.
     */
    public static int haunch(double t, int rise) {
        return (int) Math.round(rise * (1 - Math.sqrt(Math.max(0, 1 - (2 * t - 1) * (2 * t - 1)))));
    }

    // ---- wear by land (SPEC §64.5) ------------------------------------------------------------------------------------

    /**
     * The last pass of every monument: {@link #age} with the wet and the wear its land gives it, then what that land
     * leaves on old stone. Desert and badlands: sand drifted against the side the wind comes from. Forest, jungle,
     * swamp and the dark woods: vines down the faces. Mountain: nothing grows, more is worn. (Snow comes back by itself
     * in snow country: the feature lays it.) {@code wet} and {@code wear} are the monument's own, before the land.
     */
    public static void finish(Scene s, int wet, int wear) {
        var land = s.land();
        int[] box = s.box();
        int gone = h(s, 3, 1, 4) % 21 - 10;                       // how far this one site has gone: ten either way
        switch (land) {
            case DESERT, BADLANDS -> {
                age(s, Math.max(-64, wet - 30), Math.min(95, wear + 12 + gone));
                drift(s, land == dev.awaken.living.region.RegionMap.Group.BADLANDS ? st(Blocks.RED_SAND) : st(Blocks.SAND));
            }
            case JUNGLE, SWAMP -> {
                age(s, wet + 9, Math.min(95, wear + 6 + gone));
                vinesTurned(s, box, 16);
            }
            case DARK, FOREST -> {
                age(s, wet + 4, Math.min(95, wear + gone));
                vinesTurned(s, box, 6);
            }
            case MOUNTAIN, SNOW -> age(s, wet - 4, Math.min(95, wear + 16 + gone));
            default -> age(s, wet, Math.max(5, Math.min(95, wear + gone)));
        }
    }

    /** Vines over a scene's whole box. The box is in turned offsets; vines look at neighbours, so the turn does not matter to them. */
    private static void vinesTurned(Scene s, int[] box, int pct) {
        Long2ObjectOpenHashMap<BlockState> m = s.blocks();
        LongArrayList keys = new LongArrayList(m.keySet());
        for (long k : keys) {
            BlockState b = m.get(k);
            if (b == null || !Olden.is(b) || !b.canOcclude()) continue;
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (y < 2 || h(s, x, y + 17, z) * 100 >= pct * 256) continue;
            for (int d = 0; d < 4; d++) {
                int ax = x + DX[d], az = z + DZ[d], reach = s.site.kind().reach();
                if (Math.abs(ax) > reach || Math.abs(az) > reach || m.containsKey(BlockPos.asLong(ax, y, az))) continue;
                // the vine hangs in the air beside the stone, fixed to the face it grows on
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(DIR[d].getOpposite()), true);
                int len = 1 + (h(s, x, y + 50, z) & 3);
                for (int j = 0; j < len && !m.containsKey(BlockPos.asLong(ax, y - j, az)) && y - j > s.groundAt(ax, az); j++) m.put(BlockPos.asLong(ax, y - j, az), vine);
                break;
            }
        }
    }

    /** Sand against the foot of everything, deeper on the side the wind comes from (the site's own). */
    private static void drift(Scene s, BlockState sand) {
        Long2ObjectOpenHashMap<BlockState> m = s.blocks();
        int wind = h(s, 9, 9, 9) & 3;
        LongArrayList keys = new LongArrayList(m.keySet());
        for (long k : keys) {
            BlockState b = m.get(k);
            if (b == null || !Olden.is(b) || !b.canOcclude()) continue;
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (y != s.groundAt(x, z) + 1) continue;               // the bottom course only
            for (int d = 0; d < 4; d++) {
                int ax = x + DX[d], az = z + DZ[d], g = s.groundAt(ax, az), reach = s.site.kind().reach();
                if (Math.abs(ax) >= reach || Math.abs(az) >= reach || m.containsKey(BlockPos.asLong(ax, g + 1, az)) || g < y - 2) continue;
                int deep = (d == wind ? 2 : 0) + (n(s, ax * 1.4, az * 1.4, 5.0) > 0.5 ? 1 : 0);
                for (int j = 1; j <= deep; j++) {
                    if (m.containsKey(BlockPos.asLong(ax, g + j, az))) break;
                    m.put(BlockPos.asLong(ax, g + j, az), sand);
                }
                if (deep >= 2) {                                   // a drift tails off a block further out
                    int bx = ax + DX[d], bz = az + DZ[d], g2 = s.groundAt(bx, bz);
                    if (Math.abs(bx) <= reach && Math.abs(bz) <= reach && !m.containsKey(BlockPos.asLong(bx, g2 + 1, bz)) && g2 >= g - 1) m.put(BlockPos.asLong(bx, g2 + 1, bz), sand);
                }
            }
        }
    }

    /** A stair of the family the right way up, worn or whole by the place. */
    public static BlockState step(Scene s, int x, int z, Direction up) {
        return stair(h(s, x, 77, z) < 60 ? st(Olden.WORN_STAIRS.get()) : stairs(), up);
    }

    static boolean isTop(BlockState b) {
        return b.hasProperty(StairBlock.HALF) && b.getValue(StairBlock.HALF) == Half.TOP;
    }
}
