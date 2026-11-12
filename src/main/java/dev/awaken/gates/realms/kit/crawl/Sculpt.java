package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A piece of a run cut by hand, cell by cell (SPEC §72.2): what the beats of a line, a well's flights and a room's
 * plinths are made of. Columns like any part (feet, roof, water standing on the floor), and on top of them boxes: solid
 * ones with a role the kind's skin dresses (a fallen beam, a worn lip, the cap over a hand slot, a rail, a climb line,
 * a plinth, a deck over a void), air ones (a well's void), and the two kinds a move needs: {@code keep} (must end up
 * air: a slide's gap, the lane a wall run flies through, the slot over a lip) and {@code wall} (must end up solid: the
 * face a run is along, the stone behind a lip). Nothing a skin, a trim or another part does can undo those:
 * {@link #enforce} runs after everything else.
 */
public final class Sculpt extends CrawlLand.Part {
    /** What a solid cell is for, so each kind can make it of its own stone. */
    public enum Role {
        /** The kind's own rock. */ ROCK,
        /** A floor's top block. */ FLOOR,
        /** A half step: the kind's slab, low. */ STEP,
        /** A slab hung high: the cap over a hand slot or a low roof. */ CAP,
        /** A fallen beam or lintel across the way (arg: 0 along x, 2 along z, 1 upright). */ BEAM,
        /** The worn, pale top of something to hang from. */ LIP,
        /** A wall's face at running height, scuffed by feet. */ SCUFF,
        /** A rail, bench or kerb to vault. */ RAIL,
        /** A climb line (arg: the side of its cell the wall is on, 0-3 as Direction 2D data; -1 hangs free). */ LINE,
        /** A block to mantle. */ PLINTH,
        /** A plinth's half-step top. */ PLINTH_TOP,
        /** A door or grate left half shut. */ DOOR,
        /** The floor before something low: dust and the mark of a draught. */ DUST,
        /** Floor that gives way. */ CRUMBLE,
        /** A ledge's top over nothing: the floor block, the rock under it two thick. */ DECK,
        /** A light set in the stone where a climb would otherwise be dark. */ GLOW,
        /** A press's tooth at rest. */ TOOTH,
        /** An anchor ring set in a roof for the rope (awaken:anchor_ring, the same in every kind). */ ANCHOR
    }

    /** A box of cells, both corners inclusive. */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1, Role role, int arg) {
        public boolean has(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }

        boolean column(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    /** The blocks a land makes each role of. */
    public interface Paint {
        /** {@code top}: the box's top cell (a floor's top block, a plinth's cap). */
        BlockState solid(Role role, int arg, int x, int y, int z, boolean top);

        BlockState floor(int x, int y, int z);

        BlockState rock(int x, int y, int z);

        BlockState step();
    }

    private static final BlockState WATER = Blocks.WATER.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState(), STONE = Blocks.STONE.defaultBlockState();
    /**
     * A free rope (awaken:rope, a climb line in #awaken:runner_climbs): looked up by name when asked (it is another
     * module's block, and a lookup kept from class load could run before the registries are filled).
     */
    static BlockState rope() {
        BlockState r = rope;
        if (r == null) {
            r = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("rope")).defaultBlockState();
            if (!r.isAir()) rope = r;
        }
        return r;
    }

    private static volatile @Nullable BlockState rope;

    /** The anchor ring (awaken:anchor_ring, the Ascent's block), looked up by name the same way. */
    static BlockState anchorRing() {
        BlockState r = anchor;
        if (r == null) {
            r = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("anchor_ring")).defaultBlockState();
            if (!r.isAir()) anchor = r;
        }
        return r;
    }

    private static volatile @Nullable BlockState anchor;

    /** Per column, every floor cut in it (feet2, roof, wet): one, or more where a flight runs under another (a well's). */
    private final Map<Long, List<int[]>> cols = new HashMap<>();
    /** Per column (once done), the floors under its highest one, (feet2, roof, wet) each. */
    private final Map<Long, int[]> lower = new HashMap<>();
    public final List<Box> solids = new ArrayList<>(), airs = new ArrayList<>(), keeps = new ArrayList<>(), walls = new ArrayList<>();
    /** Unseen lights over a climb (x, y, z), set only where nothing else is. */
    public final List<int[]> lights = new ArrayList<>();
    private int gx0, gz0, nx, nz;
    private int[] feet, top, water;
    /** Per column (once done): the solid and air boxes over it, in the order they were made. */
    private final Map<Long, Box[]> solidCols = new HashMap<>(), airCols = new HashMap<>(), wallCols = new HashMap<>(), keepCols = new HashMap<>();
    private static final Box[] NO_BOXES = new Box[0];
    private boolean frozen;

    public Sculpt(String name) {
        this.name = name;
        x0 = z0 = Integer.MAX_VALUE;
        x1 = z1 = Integer.MIN_VALUE;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    private void grow(int ax, int az, int bx, int bz) {
        x0 = Math.min(x0, Math.min(ax, bx));
        z0 = Math.min(z0, Math.min(az, bz));
        x1 = Math.max(x1, Math.max(ax, bx));
        z1 = Math.max(z1, Math.max(az, bz));
    }

    private void open() {
        if (frozen) throw new IllegalStateException("sculpt " + name + " is finished");
    }

    /**
     * A column: feet (twice their height, odd a half step), the first solid cell over the head, and water on the floor. Cut
     * again where it already has air or a floor, it is cut anew; cut clear of what it has (a well's flight under the one
     * above it, with rock between), it is another floor in the same column.
     */
    public void column(int x, int z, int feet2, int roof, int wet) {
        open();
        List<int[]> at = cols.computeIfAbsent(key(x, z), k -> new ArrayList<>(1));
        int lo = Math.floorDiv(feet2, 2) - 1, hi = roof - 1;
        at.removeIf(c -> Math.floorDiv(c[0], 2) - 1 <= hi && c[1] - 1 >= lo);
        at.add(new int[]{feet2, roof, wet});
        grow(x, z, x, z);
    }

    /** Forgets a column: rock again (unless a box says otherwise). */
    public void close(int x, int z) {
        open();
        cols.remove(key(x, z));
    }

    /** A column's highest floor (feet2, roof, wet), or null. */
    public int @Nullable [] columnAt(int x, int z) {
        List<int[]> at = cols.get(key(x, z));
        return at == null || at.isEmpty() ? null : highest(at);
    }

    private static int[] highest(List<int[]> at) {
        int[] top = at.getFirst();
        for (int[] c : at) if (c[0] > top[0]) top = c;
        return top;
    }

    private static Box box(int ax, int ay, int az, int bx, int by, int bz, Role role, int arg) {
        return new Box(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz), role, arg);
    }

    public void solid(int ax, int ay, int az, int bx, int by, int bz, Role role, int arg) {
        open();
        Box b = box(ax, ay, az, bx, by, bz, role, arg);
        solids.add(b);
        grow(b.x0, b.z0, b.x1, b.z1);
    }

    public void air(int ax, int ay, int az, int bx, int by, int bz) {
        open();
        Box b = box(ax, ay, az, bx, by, bz, Role.ROCK, 0);
        airs.add(b);
        grow(b.x0, b.z0, b.x1, b.z1);
    }

    public void keep(int ax, int ay, int az, int bx, int by, int bz) {
        open();
        keeps.add(box(ax, ay, az, bx, by, bz, Role.ROCK, 0));
    }

    public void wall(int ax, int ay, int az, int bx, int by, int bz) {
        open();
        walls.add(box(ax, ay, az, bx, by, bz, Role.ROCK, 0));
    }

    /** Fixes the shape: from here on it is read by worldgen threads and never changed. */
    public Sculpt done() {
        if (frozen) return this;
        frozen = true;
        if (x0 > x1) {
            x0 = x1 = z0 = z1 = 0;
        }
        gx0 = x0;
        gz0 = z0;
        nx = x1 - x0 + 1;
        nz = z1 - z0 + 1;
        feet = new int[nx * nz];
        top = new int[nx * nz];
        water = new int[nx * nz];
        java.util.Arrays.fill(feet, CrawlLand.NONE);
        for (var e : cols.entrySet()) {
            long k = e.getKey();
            List<int[]> at = e.getValue();
            if (at.isEmpty()) continue;
            int x = (int) (k >> 32), z = (int) k;
            int i = (x - gx0) * nz + (z - gz0);
            // the column's own floor is its highest; the others are drawn as more floors in it
            int[] c = highest(at);
            feet[i] = c[0];
            top[i] = c[1];
            water[i] = c[2];
            if (at.size() > 1) {
                int[] more = new int[3 * (at.size() - 1)];
                int j = 0;
                for (int[] o : at) if (o != c) {
                    more[j++] = o[0];
                    more[j++] = o[1];
                    more[j++] = o[2];
                }
                lower.put(k, more);
            }
        }
        index(solids, solidCols);
        index(airs, airCols);
        index(walls, wallCols);
        index(keeps, keepCols);
        // one more cell all round: the walls a run is along and the stone behind a lip are this part's to keep
        for (Box w : walls) grow(w.x0, w.z0, w.x1, w.z1);
        for (Box k : keeps) grow(k.x0, k.z0, k.x1, k.z1);
        return this;
    }

    private static void index(List<Box> boxes, Map<Long, Box[]> into) {
        Map<Long, List<Box>> m = new HashMap<>();
        for (Box b : boxes) for (int x = b.x0; x <= b.x1; x++) for (int z = b.z0; z <= b.z1; z++) m.computeIfAbsent(key(x, z), k -> new ArrayList<>(2)).add(b);
        for (var e : m.entrySet()) into.put(e.getKey(), e.getValue().toArray(NO_BOXES));
    }

    private int idx(int x, int z) {
        if (x < gx0 || z < gz0 || x >= gx0 + nx || z >= gz0 + nz) return -1;
        return (x - gx0) * nz + (z - gz0);
    }

    @Override
    public int feet2(int x, int z) {
        int i = idx(x, z);
        return i < 0 ? CrawlLand.NONE : feet[i];
    }

    @Override
    public int roof(int x, int z) {
        int i = idx(x, z);
        return i < 0 ? CrawlLand.NONE : top[i];
    }

    @Override
    public int wet(int x, int z) {
        int i = idx(x, z);
        return i < 0 ? 0 : water[i];
    }

    @Override
    public int @Nullable [] more(int x, int z) {
        return lower.get(key(x, z));
    }

    /** The last-made solid box holding this cell (a later box over an earlier one wins), or null. */
    @Nullable Box solidAt(int x, int y, int z) {
        if (!frozen) {
            for (int i = solids.size() - 1; i >= 0; i--) if (solids.get(i).has(x, y, z)) return solids.get(i);
            return null;
        }
        Box[] col = solidCols.get(key(x, z));
        if (col == null) return null;
        for (int i = col.length - 1; i >= 0; i--) if (y >= col[i].y0 && y <= col[i].y1) return col[i];
        return null;
    }

    private boolean airBox(int x, int y, int z) {
        if (!frozen) {
            for (Box b : airs) if (b.has(x, y, z)) return true;
            return false;
        }
        Box[] col = airCols.get(key(x, z));
        if (col == null) return false;
        for (Box b : col) if (y >= b.y0 && y <= b.y1) return true;
        return false;
    }

    /** Whether a cell is in one of these boxes (walls or keeps, once done). */
    private boolean inBoxes(List<Box> boxes, Map<Long, Box[]> cols, int x, int y, int z) {
        if (!frozen) {
            for (Box b : boxes) if (b.has(x, y, z)) return true;
            return false;
        }
        Box[] col = cols.get(key(x, z));
        if (col == null) return false;
        for (Box b : col) if (y >= b.y0 && y <= b.y1) return true;
        return false;
    }

    @Override
    public boolean open(int x, int y, int z) {
        if (x < x0 || x > x1 || z < z0 || z > z1) return false;
        if (solidAt(x, y, z) != null) return false;
        if (airBox(x, y, z)) return true;
        int f = feet2(x, z);
        if (f != CrawlLand.NONE && y >= airFrom(f) && y < roof(x, z)) return true;
        int[] more = more(x, z);
        if (more != null) for (int i = 0; i < more.length; i += 3) if (y >= airFrom(more[i]) && y < more[i + 1]) return true;
        return false;
    }

    @Override
    void carve(V v) {
        super.carve(v);
        if (!lower.isEmpty()) for (int x = Math.max(x0, v.x0 - 1); x <= Math.min(x1, v.x0 + 16); x++) for (int z = Math.max(z0, v.z0 - 1); z <= Math.min(z1, v.z0 + 16); z++) {
            int[] more = more(x, z);
            if (more != null) for (int i = 0; i < more.length; i += 3) {
                int from = Math.floorDiv(more[i], 2), wet = more[i + 2];
                for (int y = from; y < more[i + 1]; y++) v.set(x, y, z, y < from + wet ? WATER : null);
            }
        }
        for (Box b : airs) for (int x = Math.max(b.x0, v.x0 - 1); x <= Math.min(b.x1, v.x0 + 16); x++) for (int z = Math.max(b.z0, v.z0 - 1); z <= Math.min(b.z1, v.z0 + 16); z++) {
            for (int y = b.y0; y <= b.y1; y++) v.set(x, y, z, null);
        }
    }

    @Override
    <M extends Mats> void lay(V v, CrawlLand<M> land, M m) {
        super.lay(v, land, m);
        if (!lower.isEmpty()) for (int x = Math.max(x0, v.x0 - 1); x <= Math.min(x1, v.x0 + 16); x++) for (int z = Math.max(z0, v.z0 - 1); z <= Math.min(z1, v.z0 + 16); z++) {
            int[] more = more(x, z);
            if (more != null) for (int i = 0; i < more.length; i += 3) {
                int f = more[i], y = Math.floorDiv(f, 2);
                if (v.get(x, y - 1, z) == null) continue;
                v.set(x, y - 1, z, land.floor(m, this, x, y - 1, z));
                if ((f & 1) != 0 && v.get(x, y, z) == null) v.set(x, y, z, land.step(m));
            }
        }
        for (Box b : solids) for (int x = Math.max(b.x0, v.x0 - 1); x <= Math.min(b.x1, v.x0 + 16); x++) for (int z = Math.max(b.z0, v.z0 - 1); z <= Math.min(b.z1, v.z0 + 16); z++) {
            for (int y = b.y0; y <= b.y1; y++) v.set(x, y, z, land.beatBlock(m, this, b.role, b.arg, x, y, z, y == b.y1));
        }
    }

    /**
     * Last of all (GeneratedCrawl.finish): what a move needs is so whatever else ran. Kept cells are emptied of anything
     * but water and light; wall cells that came out empty are the kind's rock again.
     */
    <M extends Mats> void enforce(V v, CrawlLand<M> land, M m) {
        for (Box k : keeps) for (int x = Math.max(k.x0, v.x0 - 1); x <= Math.min(k.x1, v.x0 + 16); x++) for (int z = Math.max(k.z0, v.z0 - 1); z <= Math.min(k.z1, v.z0 + 16); z++) {
            for (int y = k.y0; y <= k.y1; y++) {
                BlockState s = v.get(x, y, z);
                if (s == null || !s.getFluidState().isEmpty() || s.is(Blocks.LIGHT)) continue;
                Box own = solidAt(x, y, z);
                // a climb line inside a kept lane is the line itself
                if (own != null && own.role == Role.LINE) continue;
                v.set(x, y, z, null);
            }
        }
        for (Box w : walls) for (int x = Math.max(w.x0, v.x0 - 1); x <= Math.min(w.x1, v.x0 + 16); x++) for (int z = Math.max(w.z0, v.z0 - 1); z <= Math.min(w.z1, v.z0 + 16); z++) {
            for (int y = w.y0; y <= w.y1; y++) {
                BlockState s = v.get(x, y, z);
                Box own = solidAt(x, y, z);
                if (own != null) {
                    // a beat's own piece in a wall (a lip, a cap): its block, unless something solid stands for it
                    if (!V.holds(s)) v.set(x, y, z, land.beatBlock(m, this, own.role, own.arg, x, y, z, y == own.y1));
                } else if (s == null || !s.getFluidState().isEmpty() || !s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                    // a run's face and the stone behind a lip are whole blocks: not a stair a trim set, not a fence
                    v.set(x, y, z, land.rock(m, x, y, z));
                }
            }
        }
        for (int[] l : lights) if (v.in(l[0], l[1], l[2]) && v.get(l[0], l[1], l[2]) == null) v.set(l[0], l[1], l[2], LIGHT);
    }

    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 12);

    /**
     * This piece alone as blocks, for the proof at generation (SPEC §72.4): its own cells exactly as they will be drawn
     * (the land's own blocks for every role, so a slab is a slab and a fence a fence), the land's rock everywhere else.
     * Nothing is painted. A line's pads are its own, so nothing a tunnel or a room does at either end is in a move's way.
     */
    public BlockGetter view(Paint paint) {
        return new View(this, paint);
    }

    /**
     * The state of one cell as this piece alone would draw it, after its last pass ({@link #enforce}): a kept cell is
     * empty but for water, light and a climb line; a wall cell is its own piece's block, or the rock where it came out
     * anything less than a whole block.
     */
    BlockState stateAt(Paint paint, int x, int y, int z) {
        Box s = solidAt(x, y, z);
        if (inBoxes(walls, wallCols, x, y, z)) {
            if (s != null) return paint.solid(s.role, s.arg, x, y, z, y == s.y1);
            BlockState b = drawn(paint, null, x, y, z);
            return b.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && b.getFluidState().isEmpty() ? b : paint.rock(x, y, z);
        }
        BlockState b = drawn(paint, s, x, y, z);
        if (!b.isAir() && b.getFluidState().isEmpty() && !b.is(Blocks.LIGHT) && (s == null || s.role != Role.LINE) && inBoxes(keeps, keepCols, x, y, z)) return AIR;
        return b;
    }

    /** What the carve and the lay leave in a cell: its own piece, its air, its column, the rock. */
    private BlockState drawn(Paint paint, @Nullable Box s, int x, int y, int z) {
        if (s != null) return paint.solid(s.role, s.arg, x, y, z, y == s.y1);
        if (airBox(x, y, z)) return AIR;
        int f = feet2(x, z);
        if (f == CrawlLand.NONE) return paint.rock(x, y, z);
        BlockState b = level(paint, f, roof(x, z), wet(x, z), x, y, z);
        if (b != null) return b;
        int[] more = more(x, z);
        if (more != null) for (int i = 0; i < more.length && b == null; i += 3) b = level(paint, more[i], more[i + 1], more[i + 2], x, y, z);
        return b != null ? b : paint.rock(x, y, z);
    }

    /** What one floor of a column makes of a cell: its air, water, half step or floor block, or null if the cell isn't its. */
    private static @Nullable BlockState level(Paint paint, int f, int roof, int wet, int x, int y, int z) {
        int from = Math.floorDiv(f, 2);
        if (y >= from && y < roof) {
            if (y < from + wet) return WATER;
            if ((f & 1) != 0 && y == from) return paint.step();
            return AIR;
        }
        if (y == from - 1) return paint.floor(x, y, z);
        return null;
    }

    private static final class View implements BlockGetter {
        private final Sculpt s;
        private final Paint paint;

        View(Sculpt s, Paint paint) {
            this.s = s;
            this.paint = paint;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return s.stateAt(paint, pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return V.H;
        }

        @Override
        public int getMinY() {
            return 0;
        }
    }

    /** Plain stone for everything: what the template tests prove a beat against before any kind dresses it. */
    public static final Paint PLAIN = new Paint() {
        @Override
        public BlockState solid(Role role, int arg, int x, int y, int z, boolean top) {
            return plain(role, arg);
        }

        @Override
        public BlockState floor(int x, int y, int z) {
            return STONE;
        }

        @Override
        public BlockState rock(int x, int y, int z) {
            return STONE;
        }

        @Override
        public BlockState step() {
            return Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        }
    };

    /** A role's shape in plain blocks: what every kind's block for it must match (BeatSkin.check). */
    public static BlockState plain(Role role, int arg) {
        return switch (role) {
            case STEP, PLINTH_TOP -> Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
            case CAP -> Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
            case LINE -> arg < 0 ? rope() : Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.from2DDataValue(arg).getOpposite());
            case DOOR -> Blocks.IRON_BARS.defaultBlockState();
            case GLOW -> Blocks.SHROOMLIGHT.defaultBlockState();
            case ANCHOR -> anchorRing();
            default -> STONE;
        };
    }
}
