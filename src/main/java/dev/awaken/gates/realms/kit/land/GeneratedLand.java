package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.block.DepthBlocks;
import dev.awaken.gates.realms.kit.Build;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.N.Hex;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.SheetLand;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An open land nobody laid out by hand (SPEC §50.9, "Rolled lands"): a whole outdoor world rolled from the gate's seed, the
 * open-sky twin of {@code GeneratedCrawl}. A {@link Landform} gives the big shape and lays the way through it; the land
 * that extends this is a <b>skin</b>: what the ground, the cliffs and the sea are made of, what grows, what was left
 * behind, who lives there. A new skin is a new country; a new landform is a new kind of journey.
 *
 * <p><b>The ground is shaped to the way, not the way to the ground.</b> A landform lays its run as <i>stations</i>: points
 * with a height, a flat radius and a width over which the land comes back to its own shape. Every column takes the height
 * of the station it is on, or is blended between the nearest station and the natural ground beside it. So a path is
 * always cut into a slope or carried out from it, a den's place is always level, a stair's treads are always exact, and
 * every leg can be walked to whatever the seed did to the mountain behind it. Nothing here asks another chunk what it
 * drew: a column's height is a function of where it is and the seed.
 *
 * <p>The ground steps in half blocks everywhere (a slab of the ground's own stone, or loose ground lying in layers an
 * eighth deep), rock that stands steep stands in columns, and what a skin scatters goes only on ground that is really
 * there.
 */
public abstract class GeneratedLand extends SheetLand<GeneratedLand.Kit> {
    public static final int WATER = 62;
    /** What a station is: a worn path, a level place, a tread of a stair, a narrow track, a floor a piece lays itself. */
    public static final byte TRAIL = 1, PAD = 2, STAIR = 3, SPUR = 4, FLOOR = 5;
    /** The parts of what stands under a table of the Isles ({@link #stalk}): a stalk's side, a table's underside, a span's underside. */
    public static final int STALK = 0, CAP_UNDER = 1, SPAN_UNDER = 2;
    /** How far from a way the land beside it is kept gentle, so the way can be seen from and the cut beside it is small. */
    static final double CALM = 24;
    private static final int G = 20;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Direction[] SIDE_DIR = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

    /** Only so the kit's own blocks (the cache, the Waystone's stone) exist: nothing of another land's is drawn here. */
    public static final class Kit extends Mats {
        Kit() {
            super("amber", new Accents(STONE, STONE, STONE, STONE, STONE, STONE, STONE, STONE, STONE));
        }
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();
    private static volatile Kit kit;

    /**
     * What a country is made of, by the job each block does. {@code soil}: loose ground, on the flats and in the hollows;
     * {@code drift}: the same lying in layers (a {@link DepthBlocks.Drift}), or null. {@code stone}: firm ground, scree,
     * the path. {@code rock}: what cliffs and standing stones are. {@code dark}: a cliff's dark courses. {@code seam} and
     * {@code ore}: what shows along a line in the rock. {@code crust} and {@code fall}: what runs down the land and
     * pours over its ledges, or null for none. {@code post}, {@code planks}, {@code lamp}, {@code bars}, {@code chain}:
     * what was left behind is made of these.
     */
    public record Dress(BlockState soil, BlockState drift, BlockState stone, BlockState stoneSlab, BlockState stoneStairs, BlockState rock, BlockState rockSlab,
                        BlockState rockStairs, BlockState dark, BlockState seam, BlockState ore, BlockState crust, BlockState fall, BlockState water, BlockState post,
                        BlockState planks, BlockState lamp, BlockState bars, BlockState chain) {}

    /** What a skin is told about one column of ground when it is asked what lies there. */
    public static final class Spot {
        public int x, y, z;
        /** The kind of way this column is on (TRAIL, PAD...), or 0. */
        public byte way;
        /** Blocks outside the nearest way's level part (0 on it, 99 far from any), and from that station's middle. */
        public float off, mid;
        /** How far the highest neighbour stands over this column, and how far the lowest lies under it. */
        public int rise, drop;
        public boolean crust, columns, wet;
        /** Whether rock stands a few blocks upwind (the wind is from the west): where what the wind carries comes to rest. */
        public boolean lee;
        /** Whether this column is part of something built (a temple's tiers, a causeway): masonry, not ground. */
        public boolean built;
        /** 0 to 1: how near what burns (a channel, a rift) is. */
        public double heat;
        /** A slow noise, -1 to 1, for beds of one thing in another; a slower, smoother one for whole drifts; and this column's own number, 0 to 1. */
        public double patch, drift, roll;
    }

    private static final class Near {
        int idx;
        double e, d;
    }

    private static final class Scratch {
        final int[] h2 = new int[G * G], idx = new int[G * G];
        final double[] s = new double[G * G];
        final byte[] way = new byte[G * G];
        final float[] off = new float[G * G], mid = new float[G * G];
        final BlockState[] cap = new BlockState[G * G], top = new BlockState[G * G];
        // what bones worked out about each column, kept for the finish: the noise is not asked twice
        final byte[] rise = new byte[G * G], drop = new byte[G * G], flags = new byte[G * G];
        final float[] heat = new float[G * G], patch = new float[G * G], drift = new float[G * G], roll = new float[G * G];
        final Near near = new Near();
        final Spot spot = new Spot();
    }

    private static final class ColCache {
        final long[] key = new long[1024];
        final double[] val = new double[1024];
        final boolean[] used = new boolean[1024];
        final Near near = new Near();
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final ThreadLocal<Near> NEAR = ThreadLocal.withInitial(Near::new);
    // a land's own: two Realms can be drawn on one worker, and a column of one is not a column of the other
    private final ThreadLocal<ColCache> columnTops = ThreadLocal.withInitial(ColCache::new);

    private double[] sx = new double[1024], sz = new double[1024], sy = new double[1024];
    private float[] sw = new float[1024], sb = new float[1024];
    private byte[] sk = new byte[1024], sd = new byte[1024];
    private int sn;
    private int[][] grid;
    private int gx0, gz0, gw, gd;

    private final SimplexNoise fine, patch, strata, vein, slow;
    protected Dress dress;
    /** Whether this country's water is a body (ice): then it is ground, a lake is somewhere to walk, and a skin's growth lies on it. */
    private boolean frozen;
    Landform form;
    private final List<Edit> heartOpen = new ArrayList<>(), heartShut = new ArrayList<>();
    private int[] heart;
    /** The main way's stations, in the order you walk them: x, z, feet. For whatever a skin stands along the way. */
    private final List<double[]> wayPoints = new ArrayList<>();

    protected GeneratedLand(Ctx ctx, long salt) {
        super(ctx, salt);
        fine = N.noise(seed + 7001);
        patch = N.noise(seed + 7002);
        strata = N.noise(seed + 7003);
        vein = N.noise(seed + 7004);
        slow = N.noise(seed + 7005);
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------------

    /** The country's blocks. Asked once. */
    protected abstract Dress makeDress();

    /** Who stands in the n-th fight (a skin has a few companies and a land goes round them, so no two neighbours match). */
    protected abstract List<String> herd(int index);

    protected abstract List<String> ambushPack(int index);

    /** A Warden's company; start it with "!name" to name the Warden itself. */
    protected abstract List<String> wardenPack(int index);

    /** The colour of this country's light in the air: what its mist takes on. */
    protected int airTint() {
        return 0xffd9a0;
    }

    /** What a skin stands in the land once the run is laid out: its trees or what stands in for them, its lamps, its ruins. */
    protected void furnish() {}

    /**
     * The great hall a landform stands at its heart (the Basin's island): a round floor {@code radius} across the middle at
     * {@code feet}, its way in on the south side five wide, and whatever the country builds round and behind it. A skin
     * with builders of its own answers with their work; the plain answer is a ruin of the land's own rock.
     */
    protected Piece<? super Kit> hall(int cx, int cz, int feet, double radius, long salt) {
        return new LandPieces.Hall(cx, cz, feet, radius, salt, this);
    }

    /** What a table's stalk, its underside or a span's underside is made of (the Isles), by part: the plain answer is the land's rock, dark underneath. */
    protected BlockState stalk(int part, int x, int y, int z) {
        return part == STALK ? dress.rock() : dress.dark();
    }

    /** One cell of the Lord's door while it is shut: a body, whatever this country closes a way with. */
    protected BlockState door(int x, int y, int z) {
        return dress.bars();
    }

    /** A den's place, as the landform asks for it: what holds it (stones, a camp, a wall). {@code kind} goes round 0, 1, 2... */
    protected void denPlace(int x, int feet, int z, int radius, int kind, double wayX, double wayZ) {}

    /** What a skin puts on one column of open ground: {@code y} is the first cell over it and is empty. */
    protected void scatter(V v, Kit m, Spot s, int y) {}

    /** The top block of a column. */
    protected BlockState cap(Spot s) {
        Dress d = dress;
        if (s.crust) return d.crust();
        if (s.wet) return s.patch > 0.1 ? d.soil() : d.stone();
        switch (s.way) {
            case STAIR:
                return d.rock();
            case FLOOR:
                return d.stone();
            case TRAIL:
                return s.mid < 1.4F ? (s.roll < 0.82 ? d.stone() : d.dark()) : s.roll < 0.6 ? d.stone() : d.soil();
            case SPUR:
                return s.roll < 0.6 ? d.stone() : d.soil();
            case PAD:
                return s.patch > 0.2 ? d.soil() : s.roll < 0.8 ? d.stone() : d.rock();
            default:
        }
        if (s.columns || s.drop >= 3 || s.rise >= 4 && s.drop >= 1) return s.heat > 0.6 && s.roll < 0.3 ? d.seam() : d.rock();
        if (s.heat > 0.55) return s.roll < 0.35 ? d.seam() : d.rock();
        if (s.drop + s.rise >= 3) return d.stone();
        return s.patch > -0.3 ? d.soil() : d.stone();
    }

    /** What lies just under the top block. */
    protected BlockState under(Spot s, BlockState cap) {
        return cap == dress.soil() ? dress.soil() : dress.stone();
    }

    /** A cell of rock where it can be seen from the side, in the column {@code s} says: courses, and what shows along a line in them. */
    protected BlockState face(Spot s, int y) {
        Dress d = dress;
        int x = s.x, z = s.z;
        double line = Math.abs(vein.get(x / 11.0 + y / 23.0, y / 6.0, z / 11.0 - y / 29.0));
        if (line < 0.035) return N.rnd(x * 31L + y, z, seed + 11) < 0.35 ? d.ore() : d.seam();
        // courses: mostly the rock itself, with beds of the firm ground's stone and of the dark stone lying in it
        double course = strata.get(x / 60.0, y / 3.4, z / 60.0);
        if (course > 0.42) return d.dark();
        if (course < -0.38) return d.stone();
        return d.rock();
    }

    // ---- what a landform builds with ----------------------------------------------------------------------------------------

    /**
     * A station of a way: within {@code flat} of it the ground is at {@code feet} (where your feet are, the top of the
     * ground); from there the land comes back to its own shape over {@code blend} blocks. A STAIR's {@code climbs} is the
     * way its tread rises.
     */
    final int station(double x, double z, double feet, double flat, double blend, byte kind, Direction climbs) {
        if (grid != null) throw new IllegalStateException("the ways are laid: nothing may move the ground now");
        if (sn == sx.length) {
            int n = sn * 2;
            sx = Arrays.copyOf(sx, n);
            sz = Arrays.copyOf(sz, n);
            sy = Arrays.copyOf(sy, n);
            sw = Arrays.copyOf(sw, n);
            sb = Arrays.copyOf(sb, n);
            sk = Arrays.copyOf(sk, n);
            sd = Arrays.copyOf(sd, n);
        }
        sx[sn] = x;
        sz[sn] = z;
        sy[sn] = feet;
        sw[sn] = (float) flat;
        sb[sn] = (float) blend;
        sk[sn] = kind;
        sd[sn] = (byte) (climbs == null ? 0 : climbs.get2DDataValue());
        return sn++;
    }

    /** A point of the main way, for a skin's lamps and whatever else stands along it. */
    final void wayPoint(double x, double z, double feet) {
        wayPoints.add(new double[]{x, z, feet});
    }

    /** The ways are all laid: from here the ground is what it will be, and anything may stand on it. */
    final void freeze() {
        int half = form.half() + 96;
        gx0 = gz0 = -half;
        gw = gd = (2 * half >> 4) + 1;
        int[] count = new int[gw * gd];
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                grid = new int[gw * gd][];
                for (int i = 0; i < count.length; i++) {
                    if (count[i] > 0) grid[i] = new int[count[i]];
                    count[i] = 0;
                }
            }
            for (int i = 0; i < sn; i++) {
                double reach = sw[i] + Math.max(sb[i], CALM) + 1;
                int a0 = Math.max(0, (int) Math.floor(sx[i] - reach - gx0) >> 4), a1 = Math.min(gw - 1, (int) Math.floor(sx[i] + reach - gx0) >> 4);
                int c0 = Math.max(0, (int) Math.floor(sz[i] - reach - gz0) >> 4), c1 = Math.min(gd - 1, (int) Math.floor(sz[i] + reach - gz0) >> 4);
                for (int a = a0; a <= a1; a++) for (int c = c0; c <= c1; c++) {
                    if (pass == 1) grid[c * gw + a][count[c * gw + a]] = i;
                    count[c * gw + a]++;
                }
            }
        }
    }

    final void putSite(SiteKind kind, int x, int y, int z, int radius, String what) {
        site(kind, x, y, z, radius, what);
    }

    final void putLeg(String id, LegKind kind, String name, int x, int y, int z, int radius, int site, List<String> pack, String when, String... exits) {
        leg(id, kind, name, x, y, z, radius, site, pack, when, exits);
    }

    final void putCourse(Course c) {
        course(c);
    }

    /** A piece that must stand whatever is near (the way in, a bridge, the great thing). */
    final void must(Piece<? super Kit> p) {
        pieces.add(p);
    }

    /** A piece that stands only where nothing else does. */
    public final boolean maybe(Piece<? super Kit> p) {
        return place(p);
    }

    final void way(double x0, double z0, double x1, double z1, double halfWidth) {
        ways.add(new double[]{x0, z0, x1, z1, halfWidth});
    }

    /**
     * The Lord's door: a grate across the way in, from the ground up, wherever there is air. It is part of the land
     * until the last Warden falls, shuts again behind whoever goes in, and opens when nobody is left inside.
     */
    final void heartDoor(int x0, int z0, int x1, int z1, int feet, int high, int cx, int cz, double radius) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int y = Math.max(feet, ground(x, z) + 1); y < feet + high; y++) {
                heartShut.add(new Edit(x, y, z, door(x, y, z)));
                heartOpen.add(new Edit(x, y, z, AIR));
            }
        }
        heart = new int[]{cx, cz, feet, (int) Math.ceil(radius)};
    }

    /** Whether a point is clear of every way by so many blocks: where a skin may stand something big. */
    public final boolean offWays(int x, int z, double by) {
        Near n = NEAR.get();
        nearest(x, z, n);
        return n.idx < 0 || n.e > by;
    }

    /** How far a point is from the nearest line anyone walks: a path, a track, a stair. Slow; for laying a land out, never for drawing it. */
    public final double wayDist(double x, double z) {
        double best = 1e9;
        for (int i = 0; i < sn; i++) if (sk[i] == TRAIL || sk[i] == SPUR || sk[i] == STAIR) best = Math.min(best, Math.hypot(x - sx[i], z - sz[i]));
        return best;
    }

    /** Whether the ground round a point is fit to stand something on: dry, not burning, not a cliff, and near enough level. */
    public final boolean open(int x, int z, int r, int steps) {
        int y = ground(x, z);
        if (y <= WATER || form.heat(x, z) > 0.3 || form.columns(x, z) > 0.2) return false;
        for (int[] d : SIDES) if (Math.abs(ground(x + d[0] * r, z + d[1] * r) - y) > steps) return false;
        return true;
    }

    /** Whether a column is part of something built that the ground carries (a temple's tiers): nothing grows a tree there. */
    public final boolean built(int x, int z) {
        return form.built(x, z);
    }

    public final List<double[]> wayPoints() {
        return wayPoints;
    }

    public final Dress dress() {
        return dress;
    }

    /** Lays the whole land out. Call once, last thing in the skin's constructor. */
    protected final void generate(Landform.Kind kind) {
        dress = makeDress();
        frozen = V.holds(dress.water());
        form = Landform.make(kind, this);
        form.lay();
        if (grid == null) throw new IllegalStateException(kind + " laid no ways");
        furnish();
        clearSites();
    }

    // ---- the ground -----------------------------------------------------------------------------------------------------------

    private void nearest(double x, double z, Near n) {
        n.idx = -1;
        n.e = 1e9;
        n.d = 1e9;
        if (grid == null) {
            for (int i = 0; i < sn; i++) consider(i, x, z, n);
            return;
        }
        int a = ((int) Math.floor(x) - gx0) >> 4, c = ((int) Math.floor(z) - gz0) >> 4;
        if (a < 0 || c < 0 || a >= gw || c >= gd) return;
        int[] ids = grid[c * gw + a];
        if (ids == null) return;
        for (int i : ids) consider(i, x, z, n);
    }

    private void consider(int i, double x, double z, Near n) {
        double dx = x - sx[i], dz = z - sz[i], d = Math.sqrt(dx * dx + dz * dz), e = Math.max(0, d - sw[i]);
        if (e > Math.max(sb[i], CALM)) return;
        // on two ways at once, the nearer middle has it: a path keeps its own floor across a den's place
        if (e < n.e - 1e-9 || e <= n.e + 1e-9 && d < n.d) {
            n.idx = i;
            n.e = e;
            n.d = d;
        }
    }

    /** The ground's top with the ways laid on it, before anything stands in columns or is cut away. */
    private double surface(double x, double z, Near n) {
        nearest(x, z, n);
        double calm = n.idx < 0 ? 1 : N.smooth(2, CALM, n.e);
        double nat = form.natural(x, z, calm);
        if (n.idx < 0 || n.e >= sb[n.idx]) return nat;
        return n.e <= 0 ? sy[n.idx] : N.lerp(sy[n.idx], nat, N.smooth(0, sb[n.idx], n.e));
    }

    private double columnTop(Hex h) {
        ColCache c = columnTops.get();
        long key = (long) h.q() << 32 ^ h.r() & 0xFFFFFFFFL;
        int slot = (int) (key * 0x9E3779B97F4A7C15L >>> 54);
        if (!c.used[slot] || c.key[slot] != key) {
            c.val[slot] = surface(h.cx(), h.cz(), c.near) + (N.rnd(h.q(), h.r(), seed + 71) - 0.4) * form.columnJitter();
            c.key[slot] = key;
            c.used[slot] = true;
        }
        return c.val[slot];
    }

    /** The real height of the top of the ground at a column; {@code n} comes back saying which way it is on or beside. */
    private double height(int x, int z, Near n) {
        double s = surface(x, z, n);
        boolean on = n.idx >= 0 && n.e <= 0;
        // rock that stands steep stands in columns, each cell of them one height; never on a way or at its very edge
        double col = n.idx >= 0 && n.e < 1.5 ? 0 : form.columns(x, z);
        if (col > 0) {
            Hex h = N.hex(x, z, form.columnSize());
            double top = columnTop(h) - (h.edge() > 0.93 ? 1 : 0);
            s = N.lerp(s, top, col);
        }
        if (on) {
            // half a step up along a path's sides and here and there on a level place; never on the line you walk
            byte k = sk[n.idx];
            double f = fine.get(x / 3.7, z / 3.7);
            if (k == TRAIL && n.d > 1.3 && f > 0.25 || k == SPUR && n.d > 0.9 && f > 0.4 || k == PAD && n.d > 2 && f > 0.3) s += 0.5;
        }
        return form.cut(x, z, s);
    }

    private static int halves(double s) {
        return (int) Math.floor(s * 2 + 0.5);
    }

    @Override
    public final int ground(int x, int z) {
        int y = (halves(height(x, z, NEAR.get())) >> 1) - 1;
        return frozen && y < WATER ? WATER : y;
    }

    /** Where your feet are, standing at a column: the first cell over the ground. */
    public final int feet(int x, int z) {
        return ground(x, z) + 1;
    }

    @Override
    public int halfSize() {
        return form.half();
    }

    @Override
    public float mouthYaw() {
        return form.mouthYaw();
    }

    @Override
    public double edge(int x, int z) {
        return form.edge(x, z);
    }

    @Override
    public int biome(int x, int y, int z) {
        return 0;
    }

    @Override
    public Direction.Axis portalAxis() {
        return form.portalAxis();
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return heart != null && Math.hypot(x - heart[0], z - heart[1]) < heart[3] && y >= heart[2] - 3 && y < heart[2] + 70;
    }

    @Override
    public List<Edit> change(String moment) {
        if (moment.equals("heart_open")) return heartOpen;
        if (moment.equals("heart_shut")) return heartShut;
        return List.of();
    }

    @Override
    protected Kit mats() {
        Kit k = kit;
        if (k == null) synchronized (GeneratedLand.class) {
            if ((k = kit) == null) kit = k = new Kit();
        }
        return k;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------------

    @Override
    protected final void draw(V v) {
        Kit m = mats();
        Scratch q = SCRATCH.get();
        Near n = q.near;
        // heights two columns out: what a column looks like depends on how its neighbours stand
        for (int a = 0; a < G; a++) for (int c = 0; c < G; c++) {
            int x = v.x0 - 2 + a, z = v.z0 - 2 + c, i = a * G + c;
            double s = height(x, z, n);
            q.s[i] = s;
            q.h2[i] = halves(s);
            q.idx[i] = n.idx;
            q.way[i] = n.idx >= 0 && n.e <= 0 ? sk[n.idx] : 0;
            q.off[i] = n.idx < 0 ? 99 : (float) n.e;
            q.mid[i] = (float) n.d;
        }
        for (int a = 1; a < G - 1; a++) for (int c = 1; c < G - 1; c++) bones(v, m, q, a, c);
        // the door is part of the land until it is opened
        for (Edit e : heartShut) v.set(e.x(), e.y(), e.z(), e.state());
        drawPieces(v, m);
        for (int a = 2; a < G - 2; a++) for (int c = 2; c < G - 2; c++) finish(v, m, q, a, c);
    }

    /** What a skin is told of a column. Worked out once, by the bones ({@code fresh}); the finish reads it back. */
    private Spot spot(Scratch q, int a, int c, int x, int z, boolean fresh) {
        int i = a * G + c, y = (q.h2[i] >> 1) - 1;
        Spot s = q.spot;
        s.x = x;
        s.y = y;
        s.z = z;
        s.way = q.way[i];
        s.off = q.off[i];
        s.mid = q.mid[i];
        s.wet = y < WATER;
        if (!fresh) {
            s.rise = q.rise[i];
            s.drop = q.drop[i];
            s.crust = (q.flags[i] & 1) != 0;
            s.columns = (q.flags[i] & 2) != 0;
            s.lee = (q.flags[i] & 4) != 0;
            s.built = (q.flags[i] & 8) != 0;
            s.heat = q.heat[i];
            s.patch = q.patch[i];
            s.drift = q.drift[i];
            s.roll = q.roll[i];
            return s;
        }
        int hi = y, lo = y;
        for (int[] d : SIDES) {
            int t = (q.h2[(a + d[0]) * G + c + d[1]] >> 1) - 1;
            hi = Math.max(hi, t);
            lo = Math.min(lo, t);
        }
        s.rise = hi - y;
        s.drop = y - lo;
        s.heat = form.heat(x, z);
        s.crust = s.way == 0 && form.crust(x, z);
        s.columns = s.off >= 1.5F && form.columns(x, z) > 0.5;
        s.lee = !s.columns && !s.wet && (form.columns(x - 5, z) > 0.5 || form.columns(x - 10, z) > 0.5);
        s.built = form.built(x, z);
        s.patch = N.fbm(patch, x, z, 1 / 14.0, 2);
        s.drift = slow.get(x / 26.0, z / 26.0);
        s.roll = N.rnd(N.hash(x, z, seed + 9), z, seed + 10);
        q.rise[i] = (byte) Math.min(127, s.rise);
        q.drop[i] = (byte) Math.min(127, s.drop);
        q.flags[i] = (byte) ((s.crust ? 1 : 0) | (s.columns ? 2 : 0) | (s.lee ? 4 : 0) | (s.built ? 8 : 0));
        q.heat[i] = (float) s.heat;
        q.patch[i] = (float) s.patch;
        q.drift[i] = (float) s.drift;
        q.roll[i] = (float) s.roll;
        return s;
    }

    /** One column of ground: everything under it, its top block, and the half step or the layers that finish it. */
    private void bones(V v, Kit m, Scratch q, int a, int c) {
        int x = v.x0 - 2 + a, z = v.z0 - 2 + c, i = a * G + c;
        Spot s = spot(q, a, c, x, z, true);
        int y = s.y;
        Dress d = dress;
        BlockState cap = cap(s), body = under(s, cap);
        v.set(x, 0, z, m.bedrock);
        // what can be seen from the side is cut rock; under that is the land's inside, which nobody sees
        boolean cliff = s.drop >= 2 || s.columns || s.built;
        int seen = Math.max(1, y - Math.max(4, s.drop + 2));
        for (int k = 1; k < seen; k++) v.set(x, k, z, STONE);
        for (int k = seen; k < y; k++) v.set(x, k, z, cliff ? face(s, k) : body);
        BlockState top = null;
        if (s.way == STAIR) cap = Build.stair(d.rockStairs(), Direction.from2DDataValue(sd[q.idx[i]]));
        else if (y >= WATER && !s.crust) {
            double over = q.s[i] - (y + 1);
            boolean odd = (q.h2[i] & 1) != 0;
            if (cap == d.soil() && d.drift() != null) {
                // loose ground lies in layers an eighth deep, and banks up against any wall it lies at the foot of; on a way it
                // only ever makes the half step (five layers stand half a block high, as a slab does)
                int layers = s.way != 0 ? (odd ? 5 : 0) : Math.max(0, Math.min(6, (int) Math.round(over * 8)));
                if (s.way == 0 && s.rise >= 2 && s.drop == 0) layers = Math.max(layers, 3 + (int) (s.roll * 4));
                if (layers > 0) top = d.drift().setValue(DepthBlocks.Drift.LAYERS, layers);
            } else if (odd) top = cap == d.stone() || cap == d.soil() ? d.stoneSlab() : d.rockSlab();
        }
        v.set(x, y, z, cap);
        if (top != null) v.set(x, y + 1, z, top);
        for (int k = y + 1; k <= WATER; k++) v.set(x, k, z, d.water());
        q.cap[i] = cap;
        q.top[i] = top;
    }

    /** What goes on a column once the pieces stand: falls where what burns goes over a ledge, rubble at a wall's foot, the skin's growth. */
    private void finish(V v, Kit m, Scratch q, int a, int c) {
        int x = v.x0 - 2 + a, z = v.z0 - 2 + c, i = a * G + c, y = (q.h2[i] >> 1) - 1;
        if (y < WATER) {
            // a frozen lake is ground too: whatever the skin lays on open ground it may lay on the ice
            if (frozen && v.get(x, WATER, z) == dress.water() && v.get(x, WATER + 1, z) == null) scatter(v, m, spot(q, a, c, x, z, false), WATER + 1);
            return;
        }
        if (v.get(x, y, z) != q.cap[i]) return;
        BlockState over = v.get(x, y + 1, z);
        if (over != q.top[i]) return; // something was built here
        Spot s = spot(q, a, c, x, z, false);
        Dress d = dress;
        if (s.crust) {
            // it pours over every ledge in its way: the fall stands in the lower column, against the face of the higher
            if (d.fall() == null) return;
            int from = y;
            for (int[] side : SIDES) {
                int t = (q.h2[(a + side[0]) * G + c + side[1]] >> 1) - 1;
                if (t >= y + 2 && form.crust(x + side[0], z + side[1])) from = Math.max(from, t);
            }
            for (int k = y + 1; k <= from; k++) if (v.get(x, k, z) == null) v.set(x, k, z, d.fall());
            return;
        }
        if (over != null) return;
        if (s.way == 0 && s.off >= 1 && s.rise >= 2 && s.drop <= 1 && s.roll < 0.42 && q.cap[i] != d.soil()) {
            // rubble at the foot of a wall: a stair with its back to the rock, or half a block of it
            int best = 0, at = -1;
            for (int k = 0; k < 4; k++) {
                int t = (q.h2[(a + SIDES[k][0]) * G + c + SIDES[k][1]] >> 1) - 1 - y;
                if (t > best) {
                    best = t;
                    at = k;
                }
            }
            boolean hard = q.cap[i] != d.stone();
            if (at >= 0) v.set(x, y + 1, z, s.roll < 0.26 ? Build.stair(hard ? d.rockStairs() : d.stoneStairs(), SIDE_DIR[at]) : hard ? d.rockSlab() : d.stoneSlab());
            return;
        }
        if (s.way == STAIR || s.way == FLOOR || tended(x, z)) return;
        scatter(v, m, s, y + 1);
    }

    /** The air every rolled land has, on top of what its skin asked for: still water at the sea, and the heart's own light. */
    protected final dev.awaken.gates.world.FloorAir airOf(dev.awaken.gates.realms.kit.AirPlan air) {
        int h = form.half();
        if (!frozen) air.water(WATER, -h, -h, h, h);
        if (heart != null) air.light(heart[0], heart[2] + 6, heart[1], airTint(), 30, 0.8F);
        return air.build();
    }
}
