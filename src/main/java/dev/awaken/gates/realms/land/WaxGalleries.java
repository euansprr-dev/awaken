package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.WarmWaxBlock;
import dev.awaken.gates.realms.blockout.Blockout;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.SheetLand;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.piece.Find;
import dev.awaken.gates.realms.kit.piece.Stall;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.Build.lit;
import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.Build.top;
import static dev.awaken.gates.realms.kit.Build.under;
import static dev.awaken.gates.realms.kit.N.fbm;
import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Wax Galleries (SPEC §50.8, gate 11): out of a dark rock tunnel at the head of a broad flight of steps, and the whole
 * hollow is below you. A round hall, five tiers of arcades all the way round, every second arch full of candles, five combs
 * the size of towers hung from the dome, each letting one thread of wax fall into a lake that never sets. Pale floes of
 * cold wax are the ways across; warm wax drags at you. The Lord's seat is a disc hung on four chains over a drop.
 *
 * <p>Drawn on the blockout {@code wax_galleries}: every big shape is read from it by name. Angles are degrees from east
 * (+x) towards north (+z), so the flight is at 270, the village cove at 180, the chandlery at 0 and the bridgehead at 90.
 */
public final class WaxGalleries extends SheetLand<WaxGalleries.M> {
    /** The shore's top block (the lake's surface is level with it), and the lake bed's. */
    static final int SHORE = 40, BED = 38;
    /** The first gallery's feet; each tier is ten higher. A corridor is seven high, its floor the block under its feet. */
    static final int TIER0 = 44, TIERS = 5, HALL_TOP = 95;
    /** The hall's wall, the arcade wall's back and the corridor's back, as the blockout's cylinders count them. */
    static final double HALL = 60.5, WALL = 62.5, RING = 68.5;
    /** Of a bay's six degrees: how much is pier, and how much of an opening's head each arch block closes. */
    static final double PIER = 0.31, ARCH = 0.165, MID = 0.655;
    static final int SEAT_Z = 128, SEAT_TOP = 63;
    /** Where the seat's four chains leave its rim: the diagonals turned twenty degrees, which keeps one out of the painting's eye. */
    static final int[] CHAINS = {65, 155, 245, 335};
    /** The bays where three steps climb from the shore into the first gallery, none near the flight or the cove. */
    static final int[] ENTRY = {2, 10, 20, 26, 37, 53};
    /** Where a thick fall of set wax runs from the dome to the shore. */
    static final int[] FALLS = {24, 72, 108, 144, 204, 236, 312, 342};
    /**
     * The corridor stairs: tier, the angle they start at, and which way they climb. The sheet's four places (30, 150, 210,
     * 330) each carry four flights; a flight comes up through the next corridor's floor, so the next one starts where it
     * ends and each place winds away from the flight of steps, the cove and the chandlery.
     */
    static final int[][] STAIRS = {{0, 30, 1}, {1, 44, 1}, {2, 58, 1}, {3, 72, 1}, {0, 150, -1}, {1, 136, -1}, {2, 122, -1}, {3, 108, -1},
            {0, 210, -1}, {1, 196, -1}, {2, 182, -1}, {3, 168, -1}, {0, 330, -1}, {1, 316, -1}, {2, 332, 1}, {3, 346, 1}};
    static final int STAIR_CELLS = 14;
    static final double STAIR_R = 67.5;
    /** Dry ways over the lake: south shore to the isle to the north shore, the isle to east and west, then spurs to what lies out on it. */
    static final double[][] LINES = {{0, -54, 0, -30}, {0, -30, 0, 54}, {0, -30, 54, 0}, {0, -30, -54, 0},
            {0, 16, 11, 16}, {0, 34, -12, 34}, {0, 44, 13, 44}, {27, -15, 30, -24}, {-27, -15, -30, -24}, {0, -37, 6, -37}};
    static final int MAIN_LINES = 4;
    static final int[][] FLOE_FINDS = {{11, 16}, {-12, 34}, {13, 44}, {30, -24}, {-30, -24}};
    /** tier, angle */
    static final int[][] HALL_FINDS = {{0, 80}, {0, 352}, {1, 100}, {1, 165}, {2, 140}, {2, 215}, {3, 35}, {3, 205}, {4, 270}};
    static final int[][] DENS = {{0, 100}, {0, 232}, {1, 75}, {1, 225}, {2, 25}, {3, 130}};

    private static final int WALLR = Blockout.Role.WALL.ordinal() + 1, FLOORR = Blockout.Role.FLOOR.ordinal() + 1, GREATR = Blockout.Role.GREAT.ordinal() + 1,
            WALKR = Blockout.Role.WALK.ordinal() + 1, WATERR = Blockout.Role.WATER.ordinal() + 1, GLOWR = Blockout.Role.GLOW.ordinal() + 1;
    private static final ThreadLocal<byte[]> COLUMN = ThreadLocal.withInitial(() -> new byte[V.H]);
    private static final int G = 63;

    private final SimplexNoise n1, n2, n3, n4;
    private final Blockout.Mass dome, lake, isle, flight, cove, seatComb;
    private final Blockout.Mass[] pendants, cones;
    /** What each lake column is: 0 open wax, 1 and 2 the steps hidden under it, 3 a floe's lowered edge, 4 a floe. */
    private final byte[] floe = new byte[(2 * G + 1) * (2 * G + 1)];
    private final double[] fallX = new double[FALLS.length], fallZ = new double[FALLS.length];
    private final int[][] isleBanks = new int[8][2];
    /** x, z, length */
    private final int[][] strings = new int[40][3];
    private final List<Edit> door = new ArrayList<>();

    public static final class M extends Mats {
        final BlockState cold = b("wax_cold"), old = b("wax_old"), run = b("wax_run"), combWall = b("wax_comb"), cellLit = b("wax_cell"), deck = b("wax_deck"),
                bead = b("wax_bead_drip"), warm = b("wax_warm"), coldSlab = b("wax_cold_slab"), coldStairs = b("wax_cold_stairs"), arch = b("wax_arch"),
                dripBlock = b("wax_drip"), bank = b("wax_candle_bank"), cell = b("wax_comb_cell"), cellGlow = b("wax_comb_cell_lit"), fall = b("wax_fall");
        final BlockState tipLight = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 14), combLight = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13);

        M() {
            super("amber", new Accents(b("hive_comb"), b("hive_comb_capped"), b("hive_honey_glow"), b("hive_drip"), lit(b("hive_candle")), b("hive_wax_crumbs"), b("hive_drum"),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(), b("amber_leaf_gold")));
        }

        /** A drip: 0 is a strand that carries on below, 1 to 3 a tip, short to long. */
        BlockState drip(int length) {
            return dripBlock.setValue(WarmWaxBlock.Drip.LENGTH, length);
        }
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    @Override
    protected M mats() {
        return m();
    }

    public WaxGalleries(Ctx ctx) {
        super(ctx, 0x576178L, Blockout.load("wax_galleries"));
        n1 = N.noise(seed + 701);
        n2 = N.noise(seed + 702);
        n3 = N.noise(seed + 703);
        n4 = N.noise(seed + 704);
        dome = blockout.mass("hall_dome");
        lake = blockout.mass("lake");
        isle = blockout.mass("isle");
        flight = blockout.mass("mouth_flight");
        cove = blockout.mass("village_cove");
        seatComb = blockout.mass("seat_comb");
        pendants = new Blockout.Mass[]{blockout.mass("pendant_mid"), blockout.mass("pendant_nw"), blockout.mass("pendant_ne"), blockout.mass("pendant_w"), blockout.mass("pendant_e")};
        cones = new Blockout.Mass[]{pendants[0], pendants[1], pendants[2], pendants[3], pendants[4], seatComb};
        for (int i = 0; i < FALLS.length; i++) {
            fallX[i] = 58.6 * Math.cos(Math.toRadians(FALLS[i]));
            fallZ[i] = 58.6 * Math.sin(Math.toRadians(FALLS[i]));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            isleBanks[i][0] = (int) Math.round(6 * Math.cos(a));
            isleBanks[i][1] = -30 + (int) Math.round(6 * Math.sin(a));
        }
        for (int i = 0; i < strings.length; i++) {
            double a = rnd(i, 1, seed) * Math.PI * 2, d = 4 + rnd(i, 2, seed) * 15;
            strings[i][0] = (int) Math.round(d * Math.cos(a));
            strings[i][1] = SEAT_Z + (int) Math.round(d * Math.sin(a));
            strings[i][2] = 10 + (int) (rnd(i, 3, seed) * 21);
        }
        floes();

        // the village in its cove on the west shore: four domed wax houses and two stalls
        int[][] houses = {{-81, -6}, {-82, 3}, {-74, 12}, {-72, -15}};
        for (int[] h : houses) must(place(new House(h[0], h[1], Math.atan2(-h[1], -70 - h[0]))));
        must(place(new Stall(-62, 7, seed + 11, this)));
        must(place(new Stall(-62, -7, seed + 12, this)));
        // the chandlery's six great vats
        for (int x = 76; x <= 92; x += 8) for (int z = -8; z <= 8; z += 16) must(place(new Vat(x, z)));
        // the seat's four chains
        for (int a : CHAINS) pieces.add(new Chain(a));

        waystone(8, -56, "the foot of the steps");
        waystone(-2, -32, "under the drip");
        pieces.add(new Stone(-5, 69, 63));
        site(SiteKind.WAYSTONE, -5, 65, 69, 4, "the bridgehead");
        for (int[] d : DENS) {
            double a = Math.toRadians(d[1]);
            site(SiteKind.DEN, (int) Math.round(65.5 * Math.cos(a)), TIER0 + 10 * d[0], (int) Math.round(65.5 * Math.sin(a)), 6, "");
        }
        int n = 0;
        for (int[] f : HALL_FINDS) {
            double a = Math.toRadians(f[1]);
            int x = (int) Math.round(65 * Math.cos(a)), z = (int) Math.round(65 * Math.sin(a)), gy = TIER0 - 1 + 10 * f[0];
            pieces.add(new Find(x, gy, z, seed + 40 + n++));
            site(SiteKind.FIND, x, gy + 1, z, 2, "");
        }
        for (int[] f : FLOE_FINDS) {
            pieces.add(new Find(f[0], SHORE, f[1], seed + 40 + n++));
            site(SiteKind.FIND, f[0], SHORE + 1, f[1], 2, "floe");
        }
        clearSites();

        // the door to the bridge, sealed until the three Wardens are down
        for (int x = -1; x <= 1; x++) for (int y = 64; y <= 67; y++) for (int z = 86; z <= 87; z++) door.add(new Edit(x, y, z, null));
    }

    private static void must(boolean placed) {
        if (!placed) throw new IllegalStateException("a Wax Galleries set piece clashes with another");
    }

    // ---- where things are ---------------------------------------------------------------------------------------------

    /** Degrees from east towards north, 0 to 360. */
    static double ang(int x, int z) {
        double a = Math.toDegrees(Math.atan2(z, x));
        return a < 0 ? a + 360 : a;
    }

    /** The nearest of the four ways to a direction in degrees. */
    static Direction way(double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return Math.abs(c) >= Math.abs(s) ? c > 0 ? Direction.EAST : Direction.WEST : s > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The highest open cell under the dome in a column of the hall. */
    private int ceil(int x, int z) {
        int t = dome.top(x, z);
        return t >= 0 ? t : Math.hypot(x, z) <= HALL ? HALL_TOP : HALL_TOP - 1;
    }

    private static boolean entry(int bay) {
        for (int e : ENTRY) if (e == bay) return true;
        return false;
    }

    private static int flightTop(int z) {
        return 41 + (int) Math.round(23 * (-54 - z) / 30.0);
    }

    /** How far to each side the open mouth reaches at a row: the tunnel, then the four steps of its splay. */
    private static int mouthHalf(int z) {
        return z >= -66 ? 24 : z >= -72 ? 18 : z >= -78 ? 13 : z >= -84 ? 9 : 5;
    }

    private static double segDist(double x, double z, double[] l) {
        double dx = l[2] - l[0], dz = l[3] - l[1], t = ((x - l[0]) * dx + (z - l[1]) * dz) / (dx * dx + dz * dz);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(x - l[0] - dx * t, z - l[1] - dz * t);
    }

    private boolean lakeAt(int x, int z) {
        return lake.span(x, z) >= 0 && isle.span(x, z) < 0 && !(z <= -54 && Math.abs(x) <= 24);
    }

    private boolean floeAt(int x, int z) {
        for (double[] l : LINES) if (segDist(x, z, l) <= 2.5) return true;
        for (Blockout.Mass p : pendants) if (Math.hypot(x - p.cx, z - p.cz) <= 3.3) return true;
        for (int[] f : FLOE_FINDS) if (Math.hypot(x - f[0], z - f[1]) <= 3.3) return true;
        N.Hex h = N.hex(x, z, 5);
        for (int i = 0; i < MAIN_LINES; i++) if (segDist(h.cx(), h.cz(), LINES[i]) <= 4.4) return true;
        return rnd(h.q(), h.r(), seed ^ 0x51) > 0.55 && h.edge() < 0.86;
    }

    private int gi(int x, int z) {
        return (x + G) * (2 * G + 1) + z + G;
    }

    /**
     * Lays the floes out once. Warm wax lets nobody jump, so a floe's edge is a slab lower, and under the wax beside every
     * floe and the shore two more half steps go down to the bed: whoever falls in can always walk out.
     */
    private void floes() {
        int n = 2 * G + 1;
        boolean[] wet = new boolean[n * n], dry = new boolean[n * n];
        for (int x = -G + 1; x < G; x++) for (int z = -G + 1; z < G; z++) {
            wet[gi(x, z)] = lakeAt(x, z);
            dry[gi(x, z)] = wet[gi(x, z)] && floeAt(x, z);
        }
        int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = -G + 2; x < G - 1; x++) for (int z = -G + 2; z < G - 1; z++) {
            int i = gi(x, z);
            if (!wet[i]) continue;
            if (dry[i]) {
                boolean rim = false;
                for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) rim |= wet[gi(x + a, z + c)] && !dry[gi(x + a, z + c)];
                floe[i] = (byte) (rim ? 3 : 4);
            } else {
                // a lowered lip all along the shore and the isle, as a floe has
                for (int[] d : four) if (!wet[gi(x + d[0], z + d[1])]) floe[i] = 3;
            }
        }
        for (int level = 2; level >= 1; level--) for (int x = -G + 2; x < G - 1; x++) for (int z = -G + 2; z < G - 1; z++) {
            int i = gi(x, z);
            if (!wet[i] || floe[i] != 0) continue;
            for (int[] d : four) if (floe[gi(x + d[0], z + d[1])] > level) floe[i] = (byte) level;
        }
    }

    /** How many lake columns are cold wax you can stand on, for the rule's test. */
    public int coldPlatesOnTheWay() {
        int n = 0;
        for (byte b : floe) if (b >= 3) n++;
        return n;
    }

    @Override
    public int ground(int x, int z) {
        int g = super.ground(x, z);
        // the cove's floor is the rock's own top, which the blockout doesn't call a floor
        return g >= 0 ? g : cove.span(x, z) >= 0 ? SHORE : -1;
    }

    @Override
    public boolean indoors() {
        return true;
    }

    /** No wind at the edge of a hollow: the rock is the edge. */
    @Override
    public double edge(int x, int z) {
        return -1;
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    @Override
    public int biome(int x, int y, int z) {
        return z >= 88 && Math.hypot(x, z - SEAT_Z) <= 40.5 ? 1 : 0;
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return z >= 88 && Math.hypot(x, z - SEAT_Z) <= 40.5;
    }

    @Override
    public String heartClosedLine() {
        return "The door to the bridge is sealed with wax as thick as your arm.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().mist(42, 4, 0xd8a040, 0.5F);
        for (Blockout.Mass p : pendants) {
            int x = (int) p.cx, z = (int) p.cz;
            air.light(x, p.y0, z, 0xffc050, 18, 0.9F);
            air.shaft(x, p.y0, z, x, SHORE + 1, z, 2, 0xffd070, 0.7F);
        }
        return air.build();
    }

    @Override
    public List<Edit> change(String moment) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<Edit> out = new ArrayList<>();
        switch (moment) {
            case "heart_open" -> {
                for (Edit e : door) out.add(new Edit(e.x(), e.y(), e.z(), air));
            }
            case "heart_shut" -> {
                for (Edit e : door) out.add(new Edit(e.x(), e.y(), e.z(), m().run));
            }
            case "seat_melt_1" -> melt(out, 18, false);
            case "seat_melt_2" -> melt(out, 14, false);
            case "seat_whole" -> melt(out, 14, true);
            default -> {}
        }
        return out;
    }

    /**
     * The seat's rim goes, from the outside in, with the candles on it and the drips under it. What carries something
     * stays: the four spokes the chains are made fast to, and the way back to the bridge.
     */
    private void melt(List<Edit> out, int keep, boolean back) {
        M m = m();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -23; x <= 23; x++) for (int z = SEAT_Z - 23; z <= SEAT_Z + 23; z++) {
            double ds = Math.hypot(x, z - SEAT_Z);
            if (ds <= keep + 0.5 || ds > 22.5 || held(x, z)) continue;
            for (int y = 47; y <= 64; y++) {
                BlockState s = seatAt(m, x, y, z);
                if (s != null) out.add(new Edit(x, y, z, back ? s : air));
            }
        }
    }

    private static boolean held(int x, int z) {
        if (Math.abs(x) <= 2 && z < SEAT_Z) return true;
        for (int a : CHAINS) {
            double c = Math.cos(Math.toRadians(a)), s = Math.sin(Math.toRadians(a)), dz = z - SEAT_Z;
            if (x * c + dz * s > 0 && Math.abs(dz * c - x * s) <= 2) return true;
        }
        return false;
    }

    /** Whether a chain's first link stands on this cell of the rim. */
    private static boolean chainFoot(int x, int z) {
        for (int a : CHAINS) if (Math.hypot(x - 21 * Math.cos(Math.toRadians(a)), z - SEAT_Z - 21 * Math.sin(Math.toRadians(a))) <= 2.3) return true;
        return false;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    @Override
    protected void draw(V v) {
        M m = mats();
        Blockout.Mass[] near = blockout.near(v.x0 - 1, v.z0 - 1, v.x0 + 16, v.z0 + 16);
        if (near.length == 0) return;
        byte[] col = COLUMN.get();
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            int span = blockout.column(x, z, near, col);
            if (span < 0) continue;
            column(v, m, x, z, col, span >> 16, span & 0xFFFF);
        }
        drawPieces(v, m);
    }

    private BlockState rock(M m, int y, int warp) {
        int band = Math.floorMod(y + warp, 13);
        return band == 0 || band == 7 ? m.stoneA : m.deep;
    }

    private static int role(byte[] col, int lo, int hi, int y) {
        return y < lo || y > hi ? 0 : col[y];
    }

    private double r3(int x, int y, int z, int salt) {
        return rnd(N.hash(x, y, salt), z, seed);
    }

    /** Hangs a drip of {@code length} cells from under (x, top + 1, z); it stops short where something is in the way. */
    private void strand(V v, M m, int x, int top, int z, int length, int tip) {
        if (!v.solid(x, top + 1, z) && !(v.get(x, top + 1, z) != null && v.get(x, top + 1, z).getBlock() == m.dripBlock.getBlock())) return;
        int last = Integer.MIN_VALUE;
        for (int k = 0; k < length; k++) {
            if (!v.empty(x, top - k, z)) break;
            v.set(x, top - k, z, m.drip(0));
            last = top - k;
        }
        if (last != Integer.MIN_VALUE) v.set(x, last, z, m.drip(tip));
    }

    private void column(V v, M m, int x, int z, byte[] col, int lo, int hi) {
        double d = Math.hypot(x, z), a = ang(x, z);
        int warp = (int) Math.floor(fbm(n1, x, z, 1 / 40.0, 2) * 5);
        boolean underFlight = role(col, lo, hi, SHORE + 1) == WALKR;
        for (int y = lo; y <= hi; y++) {
            int r = col[y];
            if (r == 0) continue;
            BlockState s;
            if (r == WALLR) s = y == 0 ? m.bedrock : rock(m, y, warp);
            else if (r == FLOORR) s = y < SHORE ? m.stoneA : m.cold; // the shore and the isle: the palest thing in the hall
            else if (r == WALKR) s = z < 0 ? rock(m, y, warp) : y == SEAT_TOP && Math.abs(x) <= 1 ? m.deck : m.cold;
            else if (r == WATERR) s = underFlight ? m.stoneA : lakeCell(m, x, y, z);
            else if (r == GLOWR) s = y <= 19 ? m.glow : comb(m, x, y, z);
            else if (r == GREATR) s = seatAt(m, x, y, z);
            else s = m.deep;
            if (s != null) v.set(x, y, z, s);
        }
        if (Math.abs(x) <= 24 && z >= -98 && z <= -54) mouth(v, m, x, z, warp);
        if (d <= HALL) hall(v, m, x, z, d, a);
        else if (d <= RING) ring(v, m, x, z, d, a, col, lo, hi, warp);
        double dc = Math.hypot(x + 70, z);
        if (dc <= 20.5 && d > HALL) {
            v.set(x, SHORE, z, fbm(n2, x, z, 1 / 7.0, 2) > 0.25 ? m.cold : m.deck);
            if (dc > 18.4 && dc <= 19.5 && Math.floorMod(x * 7 + z * 3, 6) == 0 && v.empty(x, SHORE + 1, z) && v.empty(x, SHORE + 2, z)) v.set(x, SHORE + 1, z, m.bank);
            if (rnd(x, z, 23) < 0.12) strand(v, m, x, 52, z, 1 + (int) (rnd(x, z, 24) * 3), 3);
        }
        if (x >= 66 && x <= 96 && Math.abs(z) <= 14) chandlery(v, m, x, z, d);
        if (Math.abs(x) <= 8 && z >= 66 && z <= 88) bridgehead(v, m, x, z, d);
        if (z >= 88) seat(v, m, x, z);
    }

    private BlockState lakeCell(M m, int x, int y, int z) {
        int level = Math.abs(x) < G && Math.abs(z) < G ? floe[gi(x, z)] : 0;
        if (y == SHORE) return level == 4 ? m.cold : level == 3 ? m.coldSlab : m.warm;
        return level >= 2 ? m.cold : level == 1 ? m.coldSlab : m.warm;
    }

    /** A comb's skin: open cells on a staggered pattern with comb wall between, lit in drifts; its inside is cold wax. */
    private BlockState comb(M m, int x, int y, int z) {
        for (Blockout.Mass p : cones) {
            if (!p.contains(x, y, z)) continue;
            int roof = p == seatComb ? 119 : ceil(x, z);
            if (y >= roof || p.edge(x, y, z) >= 1.3) return m.cold;
            // three cells in four hold honey with the light in it, in drifts that run down the comb (the noise is four times as long in y)
            boolean drift = y <= p.y0 + 3 || fbm(n3, x + z * 0.71, y / 4.0, 1 / 5.0, 2) > -0.2;
            if (((x + y + z) & 1) == 0) return drift ? m.cellGlow : m.cell;
            return drift ? m.cellLit : m.combWall;
        }
        return m.cold;
    }

    /**
     * A comb glows as a whole: every six blocks up its axis a hidden light. The axis itself is deep in cold wax, where a
     * light would be shut in, so each stands just off the skin, one on each of the comb's four sides.
     */
    private void combLights(V v, M m, Blockout.Mass p, int x, int z, int roof) {
        int cx = (int) p.cx, cz = (int) p.cz;
        if (x != cx && z != cz) return;
        int off = Math.abs(x - cx) + Math.abs(z - cz);
        for (int y = p.y0 + 5; y < roof; y += 6) {
            double r = p.r + (p.r2 - p.r) * (y - p.y0) / (double) p.h;
            if (off == (int) Math.round(r + 1.5) && v.empty(x, y, z)) v.set(x, y, z, m.combLight);
        }
    }

    // ---- the mouth: tunnel, splay and the flight of steps ---------------------------------------------------------------

    private void mouth(V v, M m, int x, int z, int warp) {
        if (z < -84) {
            if (Math.abs(x) <= 2 && v.empty(x, 65, z)) v.set(x, 64, z, rnd(x, z, 1) < 0.6 ? m.worn : m.stoneA);
            return;
        }
        int w = mouthHalf(z), ax = Math.abs(x), top = flightTop(z);
        if (ax <= w && v.empty(x, top + 1, z)) {
            boolean rise = top > (z < -54 ? flightTop(z + 1) : SHORE);
            double sx = w - 4.5 + 1.5 * Math.sin(z * 0.6);
            boolean stream = x > 0 && Math.abs(x - sx) <= 1.4;
            // where the mouth widens the wax runs sideways off the narrower flight above
            if (x > 0 && (z == -66 || z == -72 || z == -78)) stream |= x >= mouthHalf(z - 1) - 6 && x <= sx + 1.4;
            if (ax >= w - 1) {
                v.set(x, top, z, m.stoneA);
                if (ax == w - 1 && rise && (top - 41) % 4 == 0) v.set(x, top + 1, z, m.bank);
            } else if (stream) {
                v.set(x, top, z, rise ? stair(m.coldStairs, Direction.NORTH) : m.cold);
                if (!rise && rnd(x, z, 2) < 0.45) v.set(x, top + 1, z, m.coldSlab);
            } else {
                boolean worn = Math.abs(x + 2.5 * fbm(n2, x, z, 1 / 9.0, 1)) <= 3.2;
                if (rise) v.set(x, top, z, stair(worn ? m.paleStairs : m.stoneStairs, Direction.NORTH));
                else v.set(x, top, z, worn ? m.pale : rnd(x, z, 3) < 0.3 ? m.worn : m.stoneA);
            }
        }
        if (z > -57 || ax > w) return;
        // bare rock, stepped in and out a cell or two above head height so it reads as rock and not as boxes
        int roof = z >= -65 ? 72 : z >= -71 ? 75 : 76, side = w - ax;
        // where the mouth runs out under the hall's own height there is no roof to step down from
        boolean roofed = v.solid(x, roof + 1, z);
        int roofDepth = roofed ? Math.max(0, (int) Math.floor(1.1 + 1.6 * fbm(n4, x, z + 500, 1 / 6.0, 2))) : 0;
        for (int y = top + 5; y <= roof; y++) {
            if (!v.empty(x, y, z)) continue;
            int sideDepth = Math.max(0, (int) Math.floor(1.1 + 1.6 * fbm(n4, y + (x > 0 ? 300 : 0), z, 1 / 6.0, 2)));
            if (side < sideDepth || roof - y < roofDepth) v.set(x, y, z, rock(m, y, warp));
        }
        // long drips where the roof steps down, and all along the arch that frames the hall
        boolean lip = z == -71 || z == -70 || z == -65 || z == -64 || Math.hypot(x, z + 2) <= HALL;
        if (roofed && lip && rnd(x, z, 4) < 0.6) {
            for (int y = roof; y >= roof - 3; y--) if (v.empty(x, y, z) && v.solid(x, y + 1, z)) {
                strand(v, m, x, y, z, 2 + (int) (rnd(x, z, 5) * 5), 3);
                break;
            }
        }
    }

    // ---- the hall: lake, shore, dome, what hangs and what falls -----------------------------------------------------------

    private void hall(V v, M m, int x, int z, double d, double a) {
        int ceil = ceil(x, z), bay = (int) (a / 6);
        double f = a / 6 - bay;
        boolean atMouth = z < -50 && Math.abs(x) <= 25, atCove = Math.hypot(x + 70, z) <= 21.5;

        // the eight thick falls: a column two to four across against the wall, a stepped skirt on the shore
        if (d > 53) for (int i = 0; i < FALLS.length; i++) {
            double dd = Math.hypot(x - fallX[i], z - fallZ[i]);
            if (dd > 4.7) continue;
            for (int y = SHORE + 1; y <= ceil; y++) {
                double r = y == 41 ? 4.6 : y == 42 ? 3.8 : y == 43 ? 3.0 : y == 44 ? 2.4 : 1.15 + 0.85 * (0.5 + 0.5 * Math.sin(y * 0.37 + i * 1.7));
                if (dd <= r && d > 54.5 && v.empty(x, y, z)) v.set(x, y, z, r3(x, y, z, 6) < 0.35 ? m.run : m.cold);
            }
        }

        // three steps up from the shore into the first gallery
        boolean steps = entry(bay) && f >= PIER && d > 57.5;
        if (steps) {
            int n = d > 59.5 ? 3 : d > 58.5 ? 2 : 1;
            for (int k = 1; k < n; k++) v.set(x, SHORE + k, z, m.cold);
            v.set(x, SHORE + n, z, stair(m.coldStairs, way(a)));
        }

        // every tier's floor steps out a cell as a cornice, and drips from its lip
        if (d > 59.5 && !atMouth) for (int t = 0; t < TIERS; t++) {
            int y = TIER0 - 1 + 10 * t;
            if (t == 0 && (atCove || steps) || !v.empty(x, y, z)) continue;
            v.set(x, y, z, under(m.coldStairs, way(a)));
            double mid = Math.max(0, 1 - Math.abs(f - MID) / 0.35);
            strand(v, m, x, y - 1, z, t == 0 ? 1 : 1 + (int) (mid * 2.99 * rnd(x, z, 40 + t)), 1 + (int) (rnd(x, z, 50 + t) * 3));
        }

        // each comb lets one thread fall, with a light hidden under its tip
        for (Blockout.Mass p : pendants) if (x == (int) p.cx && z == (int) p.cz) {
            v.set(x, p.y0 - 1, z, m.tipLight);
            for (int y = SHORE + 1; y <= p.y0 - 2; y++) if (v.empty(x, y, z)) v.set(x, y, z, m.fall);
        }
        for (Blockout.Mass p : pendants) if (Math.abs(x - p.cx) <= 17 && Math.abs(z - p.cz) <= 17) combLights(v, m, p, x, z, ceil);
        for (int[] b : isleBanks) if (b[0] == x && b[1] == z && v.empty(x, SHORE + 1, z)) v.set(x, SHORE + 1, z, m.bank);

        // the dome: ribs every twelve degrees standing a cell proud, curtains of long drips between them
        if (!v.empty(x, ceil, z)) return;
        double fromRib = Math.abs((a + 6) % 12 - 6);
        if (d > 8 && Math.toRadians(fromRib) * d < 1.0) {
            int ox = (int) Math.round(x + 1.4 * x / d), oz = (int) Math.round(z + 1.4 * z / d);
            for (int y = ceil; y >= Math.min(ceil, ceil(ox, oz) + 1); y--) if (v.empty(x, y, z)) v.set(x, y, z, m.cold);
        } else if (d > 14 && Math.toRadians(6 - fromRib) * d < 1.6) {
            if (rnd(x, z, 60) < 0.45) strand(v, m, x, ceil, z, 3 + (int) (rnd(x, z, 61) * 5), 3);
        } else if (rnd(x, z, 60) < 0.1) strand(v, m, x, ceil, z, 1 + (int) (rnd(x, z, 61) * 3), 1 + (int) (rnd(x, z, 62) * 3));
    }

    // ---- the arcade wall and the corridor behind it -----------------------------------------------------------------------

    private void ring(V v, M m, int x, int z, double d, double a, byte[] col, int lo, int hi, int warp) {
        int bay = (int) (a / 6);
        double f = a / 6 - bay;
        boolean atMouth = z <= -57 && Math.abs(x) <= 24, atCove = cove.span(x, z) >= 0;
        if (d <= WALL) {
            boolean inner = d <= 61.5;
            for (int y = SHORE + 1; y < HALL_TOP; y++) {
                // the mouth or the cove has cut it away here, and the flight of steps runs through where the wall would stand
                if (role(col, lo, hi, y) == 0 || atMouth && y <= 72) continue;
                int t = y < TIER0 ? -1 : (y - TIER0) / 10, ly = y - TIER0 - 10 * t;
                if (t < 0 || t >= TIERS || ly > 6) {
                    // the wall's face between the tiers: cold wax, stone only in the lowest course and through a few patches
                    if (inner) v.set(x, y, z, y == SHORE + 1 ? m.stoneA : face(m, x, y, z));
                    continue;
                }
                if (f < PIER) {
                    v.set(x, y, z, face(m, x, y, z));
                    continue;
                }
                if (ly == 6) {
                    v.set(x, y, z, m.cold);
                    continue;
                }
                // a lit bay's candles stand on a block of wax in the middle of its parapet
                boolean plinth = inner && bay % 2 == 0 && Math.abs(f - MID) < 0.11 && !(t == 0 && entry(bay));
                v.set(x, y, z, plinth && ly == 0 ? m.cold : plinth && ly == 1 ? m.bank : null);
                if (ly == 5) {
                    double tangent = a + 90;
                    if (f < PIER + ARCH) v.set(x, y, z, m.arch.setValue(dev.awaken.gates.block.WorldBlocks.Facing.FACING, way(tangent + 180)));
                    else if (f > 1 - ARCH) v.set(x, y, z, m.arch.setValue(dev.awaken.gates.block.WorldBlocks.Facing.FACING, way(tangent)));
                    else if (inner) v.set(x, y, z, m.drip(Math.abs(f - MID) < 0.12 ? 3 : 1 + (int) (rnd(x, z, 80 + t) * 2)));
                } else if (ly == 0 && inner && !plinth && !(t == 0 && entry(bay)) && bay % 4 != 3) v.set(x, y, z, m.coldSlab);
            }
            return;
        }
        int stairT = -1, stairI = 0, stairDir = 0;
        for (int[] s : STAIRS) {
            double da = (a - s[1]) * s[2];
            da = da - 360 * Math.round(da / 360);
            double arc = Math.toRadians(da) * STAIR_R;
            if (arc >= 0 && arc < STAIR_CELLS) {
                stairT = s[0];
                stairI = (int) arc;
                stairDir = s[2];
            }
        }
        for (int t = 0; t < TIERS; t++) {
            int base = TIER0 - 1 + 10 * t;
            if (t <= 2 && atMouth || t == 0 && atCove) continue;
            if (t <= 2 && z < -50 && Math.abs(x) <= 27) {
                // the corridor stops short of the mouth: its walls stay bare rock
                for (int y = base + 1; y <= base + 7; y++) if (v.empty(x, y, z)) v.set(x, y, z, rock(m, y, warp));
                continue;
            }
            // a flight from the tier below comes up through this floor along the back wall: don't lay the floor back over it
            if (!(stairT == t - 1 && d > 66.5 && stairI >= 8)) v.set(x, base, z, m.cold); // a plain pale flag of wax, no pattern
            boolean open = t == 1 && x >= 66 && Math.abs(z) <= 14 || t == 2 && Math.abs(x) <= 8 && z >= 66;
            boolean onStair = stairT == t || stairT == t - 1;
            if (!open && !onStair && v.empty(x, base + 1, z)) {
                // every second bay is lit, and the one between is dark: pools of gold with dark between
                if (bay % 2 == 0) {
                    if (d > 67.5 && f >= 0.4 && f <= 0.9 || d > 66.5 && d <= 67.5 && Math.abs(f - MID) < 0.11) v.set(x, base + 1, z, m.candle);
                } else if (d > 67.5 && Math.abs(f - MID) < 0.08 && rnd(bay, t, 9) < 0.5) v.set(x, base + 1, z, m.crumbs);
            }
            if (stairT == t && d > 66.5) {
                int h = stairI * 10 / STAIR_CELLS, top = base + h;
                boolean step = (stairI + 1) * 10 / STAIR_CELLS > h;
                for (int y = base + 1; y <= top; y++) v.set(x, y, z, m.cold);
                if (step) v.set(x, top + 1, z, stair(m.coldStairs, way(a + 90 * stairDir)));
                for (int y = top + (step ? 2 : 1); y <= top + (step ? 5 : 4); y++) v.set(x, y, z, null);
            }
        }
    }

    /** The arcade's facing: cold wax, the stone under it showing only where the noise (0 to 1) is over 0.82. */
    private BlockState face(M m, int x, int y, int z) {
        return 0.5 + 0.75 * fbm(n2, x + y * 0.7, z - y * 0.5, 1 / 5.0, 2) > 0.82 ? m.stoneA : m.cold;
    }

    // ---- the Wardens' places ----------------------------------------------------------------------------------------------

    private void chandlery(V v, M m, int x, int z, double d) {
        if (d > RING) v.set(x, 53, z, ((x + z) & 3) == 0 ? m.cold : m.deck);
        boolean side = Math.abs(z) == 14 && x >= 70 && x <= 95, end = x == 96 && Math.abs(z) <= 12;
        if (!side && !end) return;
        // racks of candle banks along the walls: a bench and a shelf over it
        v.set(x, 54, z, m.planks);
        if (rnd(x, z, 11) < 0.7) v.set(x, 55, z, m.bank);
        v.set(x, 56, z, top(m.planksSlab));
        if (rnd(x, z, 12) < 0.5) v.set(x, 57, z, m.bank);
    }

    private void bridgehead(V v, M m, int x, int z, double d) {
        if (d > RING && z <= 87) v.set(x, 63, z, Math.abs(x) <= 1 ? m.deck : ((x + z) & 3) == 0 ? m.deck : m.cold);
        if (z == 86 || z == 87) {
            // the wall across the end, and the door in it sealed with run wax until the Wardens are down
            for (int y = 64; y <= 72; y++) {
                boolean way = Math.abs(x) <= 1 && y <= 67;
                v.set(x, y, z, way ? m.run : y == 64 || Math.abs(x) == 2 && y <= 68 || Math.abs(x) <= 2 && y == 68 ? m.stoneA : r3(x, y, z, 13) < 0.4 ? m.run : m.cold);
            }
            return;
        }
        if (Math.abs(x) == 8 && (z == 70 || z == 76 || z == 82)) for (int y = 64; y <= 72; y++) v.set(x, y, z, y == 64 ? m.stoneA : m.cold);
        if (Math.abs(x) == 4 && z == 84) {
            v.set(x, 64, z, m.pale);
            v.set(x, 65, z, m.gold);
            v.set(x, 66, z, m.bank);
        }
    }

    // ---- the Lord's seat --------------------------------------------------------------------------------------------------

    /** What stands in a cell of the seat, its candles (y 64) or the drips under it (below 58), or null. The fight melts it by this. */
    private BlockState seatAt(M m, int x, int y, int z) {
        int dz = z - SEAT_Z;
        double ds = Math.hypot(x, dz);
        if (ds > 22.5) return null;
        if (y == SEAT_TOP + 1) {
            boolean ringA = ds > 19.5 && ds <= 20.5, ringB = ds > 21.5;
            boolean way = Math.abs(x) <= 3 && dz < -15;
            return (ringA || ringB) && !way && !chainFoot(x, z) ? m.bank : null;
        }
        if (y == SEAT_TOP) {
            if (ds < 0.5) return m.cellLit;
            if (ds < 1.6 || Math.abs(ds - 4) < 0.5 || Math.abs(ds - 8) < 0.5 || Math.abs(ds - 21) < 0.5) return m.gold;
            // cream wax cut as hexes about five across, the joints run with gold
            return N.hex(x, dz, 3).edge() > 0.8 ? m.gold : m.cold;
        }
        if (y >= 58) return ds > 21.3 ? m.run : rnd(x, z, 14) < 0.12 ? m.old : m.cold;
        int length = 3 + (int) (rnd(x, z, 77) * 8), k = 57 - y;
        return k < 0 || k >= length ? null : m.drip(k == length - 1 ? 3 : 0);
    }

    private void seat(V v, M m, int x, int z) {
        double ds = Math.hypot(x, z - SEAT_Z);
        if (ds <= 22.5) for (int y = 47; y <= SEAT_TOP + 1; y++) {
            if (y >= 58 && y <= SEAT_TOP) continue;
            BlockState s = seatAt(m, x, y, z);
            if (s != null && v.empty(x, y, z)) v.set(x, y, z, s);
        }
        if (Math.abs(x) <= 2 && z <= 105 && rnd(x, z, 15) < 0.5 && v.solid(x, 62, z)) strand(v, m, x, 61, z, 1 + (int) (rnd(x, z, 16) * 2), 1 + (int) (rnd(x, z, 17) * 3));
        if (x == 0 && z == SEAT_Z) v.set(x, seatComb.y0 - 1, z, m.tipLight);
        if (ds <= 23) combLights(v, m, seatComb, x, z, 119);
        // the shaft is lit by what glows in it, not by its candles alone: the honey far below, and the comb overhead
        if (ds <= 40.5 && Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0 && v.empty(x, 21, z)) v.set(x, 21, z, m.light);
        // strings of wax beads under the great comb
        for (int[] s : strings) if (s[0] == x && s[1] == z) {
            int from = seatComb.bottom(x, z) - 1, floor = ds <= 24 ? 70 : 30;
            if (from < 0) continue;
            for (int k = 0; k < s[2] && from - k >= floor; k++) if (v.empty(x, from - k, z)) v.set(x, from - k, z, m.bead);
        }
        // and the seat itself: its candles are all on the rim, twenty blocks from its middle
        if (ds <= 19 && Math.floorMod(x, 6) == 0 && Math.floorMod(z - SEAT_Z, 6) == 0 && v.empty(x, SEAT_TOP + 5, z)) v.set(x, SEAT_TOP + 5, z, m.tipLight);
        if (ds <= 18 && Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0) {
            int under = seatComb.bottom(x, z) - 1;
            if (under > 0 && v.empty(x, under, z)) v.set(x, under, z, m.tipLight);
        }
    }

    // ---- set pieces -------------------------------------------------------------------------------------------------------

    /** A wax-folk house: round, domed, a door towards the cove's middle and a lit window in its back. */
    final class House extends Piece<M> {
        final int cx, cz;
        final double doorAt;

        House(int cx, int cz, double doorAt) {
            this.cx = cx;
            this.cz = cz;
            this.doorAt = doorAt;
            box(cx, cz, 4, 4);
        }

        @Override
        public void draw(V v, M m) {
            int gy = SHORE;
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd > 3.5) continue;
                v.set(x, gy, z, m.deck);
                for (int y = gy + 1; y <= gy + 8; y++) v.set(x, y, z, null);
                double turn = Math.atan2(z - cz, x - cx) - doorAt;
                turn = Math.abs(turn - 2 * Math.PI * Math.round(turn / (2 * Math.PI)));
                if (dd > 2.5) for (int y = gy + 1; y <= gy + 4; y++) {
                    if (turn < 0.42 && y <= gy + 2) continue;
                    v.set(x, y, z, turn > Math.PI - 0.3 && y == gy + 2 ? m.window : y == gy + 4 ? m.old : y == gy + 1 ? m.stoneA : m.cold);
                }
                for (int h = 1; h <= 3; h++) {
                    double rr = 3.5 * Math.sqrt(1 - (h / 3.6) * (h / 3.6));
                    if (dd <= rr && (dd > rr - 1.3 || h == 3)) v.set(x, gy + 4 + h, z, (h & 1) == 0 ? m.cold : m.combWall);
                }
            }
            v.set(cx, gy + 8, cz, m.bank);
            v.set(cx, gy + 1, cz, m.candle);
        }
    }

    /** A chandler's vat: staves and two hoops, three high, full of warm wax. */
    final class Vat extends Piece<M> {
        final int cx, cz;

        Vat(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            box(cx, cz, 3, 3);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dd = Math.hypot(x - cx, z - cz);
                if (dd > 2.6) continue;
                boolean stave = dd > 1.6;
                v.set(x, 54, z, stave ? m.timber : m.planks);
                v.set(x, 55, z, stave ? m.planks : m.warm);
                v.set(x, 56, z, stave ? m.timber : m.warm);
            }
            v.set(cx, 58, cz, m.light); // the glow off the wax, which the block's own light is too low in the room to give

        }
    }

    /** One of the seat's four chains: links three by five with a hole, each turned a quarter from the last, from the rim up and out to the shaft's wall. */
    final class Chain extends Piece<M> {
        static final int LINKS = 16;
        final double c, s;

        Chain(int deg) {
            c = Math.cos(Math.toRadians(deg));
            s = Math.sin(Math.toRadians(deg));
            int ex = (int) Math.round(c * 43), ez = (int) Math.round(s * 43);
            x0 = Math.min(0, ex) - 2;
            x1 = Math.max(0, ex) + 2;
            z0 = SEAT_Z + Math.min(0, ez) - 2;
            z1 = SEAT_Z + Math.max(0, ez) + 2;
            mayOverlap = true;
            label = "chain";
        }

        @Override
        public void draw(V v, M m) {
            for (int k = 0; k < LINKS; k++) {
                double r = 21 + k * 19.0 / (LINKS - 1);
                int cx = (int) Math.round(c * r), cz = SEAT_Z + (int) Math.round(s * r), y = SEAT_TOP + 1 + 3 * k;
                for (int dy = 0; dy <= 4; dy++) for (int w = -1; w <= 1; w++) {
                    if (dy > 0 && dy < 4 && w == 0) continue;
                    v.set((k & 1) == 0 ? cx + w : cx, y + dy, (k & 1) == 0 ? cz : cz + w, m.cold);
                }
            }
            // made fast in the rock
            for (double r = 39; r <= 43; r += 0.5) for (int y = 111; y <= 113; y++) {
                int x = (int) Math.round(c * r), z = SEAT_Z + (int) Math.round(s * r);
                if (v.empty(x, y, z)) v.set(x, y, z, m.cold);
            }
        }
    }

    /** A Waystone small enough for a corridor: the stone you lay a hand on between two candles. */
    final class Stone extends Piece<M> {
        final int x, gy, z;

        Stone(int x, int z, int gy) {
            this.x = x;
            this.z = z;
            this.gy = gy;
            box(x, z, 1, 1);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
                v.set(x + a, gy, z + c, ((a + c) & 1) == 0 ? m.hexstone : m.flag);
                for (int y = gy + 1; y <= gy + 4; y++) v.set(x + a, y, z + c, null);
            }
            v.set(x, gy + 1, z, m.pale);
            v.set(x, gy + 2, z, m.shrine);
            for (int a = -1; a <= 1; a += 2) {
                v.set(x + a, gy + 1, z, m.gold);
                v.set(x + a, gy + 2, z, m.candle);
            }
        }
    }
}
