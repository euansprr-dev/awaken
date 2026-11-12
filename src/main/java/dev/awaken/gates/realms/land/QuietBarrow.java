package dev.awaken.gates.realms.land;

import dev.awaken.gates.block.WorldBlocks;
import dev.awaken.gates.realms.blockout.Blockout;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Build;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.SheetLand;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.piece.Find;
import dev.awaken.gates.realms.kit.piece.Stall;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Quiet Barrow (SPEC 50.8, gate 6): a moor at dusk. A broken paved path runs from where you arrive, between stepped burial mounds with lit
 * doorways and standing stones, to a terraced hill with a stone longship on top. The mounds are sealed and asleep; whoever runs past one wakes it
 * (RealmHush). The big shapes are the blockout's (realm_blockouts/quiet_barrow.json) and are read by name; everything here is what goes on them.
 */
public final class QuietBarrow extends SheetLand<QuietBarrow.M> {
    /** The moor never rises above this; the path, the pad you arrive on and the foot of the stair all lie at {@link #PATH_Y}. */
    static final int MOOR_TOP = 65, PATH_Y = 63, HILL0_TOP = 67, DECK = 103;
    /** The pad you arrive on, from the blockout's mouth_pad (radius 4). */
    static final double PAD = 4.5;
    static final int HX = -37, HZ = -50;
    static final double[][] PATH = {{0, -205}, {6, -185}, {-8, -168}, {-30, -156}, {-37, -150}};
    /** x, z, radius, height, the way the door faces (dx, dz). The first two are the pair either side of the path. */
    static final int[][] BARROWS = {{48, -152, 17, 9, -1, 0}, {-80, -158, 15, 8, 1, 0}, {96, -120, 13, 7, -1, 0}, {-130, -126, 12, 7, 1, 0}, {130, -60, 12, 7, -1, 0},
            {150, -110, 13, 7, 0, 1}, {-190, -60, 13, 7, 1, 0}, {60, 90, 14, 8, 0, 1}, {-40, 110, 13, 7, 0, 1}};
    static final int[][] HOUSES = {{150, -20, 0, -1}, {168, -32, -1, 0}, {170, -50, -1, 0}, {156, -62, 0, 1}, {136, -30, 1, 0}};
    private static final int G = Blockout.Role.GROUND.ordinal() + 1, WALL = Blockout.Role.WALL.ordinal() + 1, FLOOR = Blockout.Role.FLOOR.ordinal() + 1, GREAT = Blockout.Role.GREAT.ordinal() + 1,
            WALK = Blockout.Role.WALK.ordinal() + 1, WATER = Blockout.Role.WATER.ordinal() + 1, GLOW = Blockout.Role.GLOW.ordinal() + 1, FOLIAGE = Blockout.Role.FOLIAGE.ordinal() + 1;

    public static final class M extends Mats {
        final BlockState bronze = b("knell_bronze"), patina = b("knell_patina"), iron = b("knell_iron"), ossuary = b("gate_ossuary"), bones = b("crypt_bones"), bell = b("gate_bell"),
                link = Blocks.IRON_CHAIN.defaultBlockState(), timberX = timber.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), timberZ = timber.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z), brazier = Build.lit(b("temple_brazier")),
                turf = b("barrow_turf"), heather = b("barrow_heather"), heatherPale = b("barrow_heather_pale"), bent = b("barrow_bent"), cap = b("barrow_menhir_cap"), kerb = b("barrow_kerb"),
                shield = b("barrow_shield"), shieldSouth = b("barrow_shield_south"), urn = b("barrow_urn"), glow = b("barrow_grave_glow"), sail = b("barrow_sail"), water = Blocks.WATER.defaultBlockState();
        private final BlockState[] lit = new BlockState[6], out = new BlockState[6];

        M() {
            super("knell", new Accents(b("gate_ossuary"), b("crypt_tracery"), b("knell_window"), b("crypt_bell_rope"), b("crypt_candles").setValue(WorldBlocks.Candles.CANDLES, 3), b("crypt_bones"),
                    b("barrow_urn"), Blocks.IRON_BARS.defaultBlockState(), b("knell_timber")));
            for (int i = 1; i <= 5; i++) {
                lit[i] = b("crypt_candles").setValue(WorldBlocks.Candles.CANDLES, i).setValue(WorldBlocks.Candles.LIT, true);
                out[i] = lit[i].setValue(WorldBlocks.Candles.LIT, false);
            }
        }

        BlockState candles(int n, boolean burning) {
            return (burning ? lit : out)[Math.max(1, Math.min(5, n))];
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (QuietBarrow.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    @Override
    protected M mats() {
        return m();
    }

    private interface Dist {
        /** How far outside the flat a column is; zero or less inside it. */
        double of(int x, int z);
    }

    /** A place the moor is levelled for: flat inside, then it may rise or fall one block every {@code run} cells away, so you can always walk on and off it. */
    private record Flat(Dist d, int level, boolean cutsHill, boolean shoulderOnly, double run) {
        Flat(Dist d, int level, boolean cutsHill, boolean shoulderOnly) {
            this(d, level, cutsHill, shoulderOnly, 1);
        }
    }

    private final SimplexNoise n9, n31, weave, crag;
    private final Blockout.Mass[] rims;
    private final List<Flat> flats = new ArrayList<>();
    private final List<Barrow> barrows = new ArrayList<>();
    private final List<Edit> heartOpen = new ArrayList<>(), heartShut = new ArrayList<>();
    private final Blockout.Mass hill0, hull, mast, yard, sailMass, prowHead, longBarrow;
    private final Blockout.Mass[] hullParts, posts;
    private final boolean[] sailCloth = new boolean[35 * 18];
    private int stones;

    private static final ThreadLocal<int[]> SURF = ThreadLocal.withInitial(() -> new int[20 * 20]);
    private static final ThreadLocal<byte[]> COLUMN = ThreadLocal.withInitial(() -> new byte[V.H]);

    public QuietBarrow(Ctx ctx) {
        super(ctx, 0x51424152524F57L, Blockout.load("quiet_barrow"));
        n9 = N.noise(seed ^ 0x9L);
        n31 = N.noise(seed ^ 0x31L);
        weave = N.noise(seed ^ 0x5A11L);
        crag = N.noise(seed ^ 0xC2A6L);
        rims = blockout.masses().stream().filter(k -> k.name.startsWith("rim_")).toArray(Blockout.Mass[]::new);
        hill0 = blockout.mass("hill0");
        hull = blockout.mass("hull");
        mast = blockout.mass("mast");
        yard = blockout.mass("yard");
        sailMass = blockout.mass("sail");
        prowHead = blockout.mass("prow_head");
        longBarrow = blockout.mass("long_barrow");
        hullParts = new Blockout.Mass[]{hull, blockout.mass("bow1"), blockout.mass("bow2"), blockout.mass("stern1"), blockout.mass("stern2")};
        posts = new Blockout.Mass[]{blockout.mass("prow1"), blockout.mass("prow2"), blockout.mass("prow3"), prowHead, blockout.mass("sternpost1"), blockout.mass("sternpost2")};

        // the mounds first: where one stands the moor is level, and its doorway is kept clear even where the hill's first step runs into it
        for (int[] b : BARROWS) {
            int base = Math.max(PATH_Y, Math.min(PATH_Y + 1, blockout.ground(b[0], b[1]))); // never at the tarn's waterline: what wakes has to be able to stand in the doorway
            barrows.add(new Barrow(barrows.size(), b[0], b[1], b[2], b[3], b[4], b[5], base, true));
        }
        Barrow isle = new Barrow(-1, 0, 150, 10, 7, 0, -1, PATH_Y, false);
        for (Barrow b : barrows) flats.add(new Flat((x, z) -> b.apron(x, z) ? 0 : 99, b.b, true, false));
        for (Barrow b : barrows) flats.add(new Flat((x, z) -> Math.hypot(x - b.cx, z - b.cz) - (b.r + 3.5), b.b, false, false));
        flats.add(new Flat((x, z) -> box(x, z, -99, 103, -95, 107), 64, false, false));
        int[][] stonesAt = {{-28, -146}, {92, 104}, {14, 118}};
        for (int[] w : stonesAt) {
            int level = surf(w[0], w[1]);
            flats.add(new Flat((x, z) -> Math.hypot(x - w[0], z - w[1]) - 5, level, false, false));
        }
        flats.add(new Flat((x, z) -> Math.hypot(x, z + 205) - PAD, PATH_Y, false, false, 3));
        flats.add(new Flat((x, z) -> box(x, z, -3, -212, 3, -210), PATH_Y, false, false)); // under the arch home
        flats.add(new Flat((x, z) -> pathDist(x, z) - 2.5, PATH_Y, false, false, 3)); // the path lies in the heather, not in a cutting
        flats.add(new Flat((x, z) -> box(x, z, -42, -150, -32, -141), PATH_Y, false, false));
        flats.add(new Flat((x, z) -> Math.hypot(x - 150, z + 40) - 28.5, 64, false, false));
        flats.add(new Flat((x, z) -> Math.hypot(x, z - 150) - 29.5, 64, false, true));
        for (int i = 0; i + 1 < PATH.length; i++) ways.add(new double[]{PATH[i][0], PATH[i][1], PATH[i + 1][0], PATH[i + 1][1], 2.5});
        ways.add(new double[]{HX, -150, HX, -70, 4.5});

        pieces.addAll(barrows);
        pieces.add(isle);
        barrows.add(isle); // from here on it's a mound like the others for the ground and the plants; only the first nine sleep
        standingStones();
        for (int[] h : HOUSES) pieces.add(new House(h[0], h[1], h[2], h[3], seed + h[0]));
        pieces.add(new Stall(146, -44, seed + 1, this));
        pieces.add(new Stall(158, -38, seed + 2, this));
        weaveSail();
        heartDoor();

        // a den just inside each seal: what sleeps there comes out of the doorway when the mound wakes
        for (Barrow b : barrows) if (b.sealed) site(SiteKind.DEN, b.cx + b.ux * (b.r - 3), b.b + 1, b.cz + b.uz * (b.r - 3), 10, "");
        waystone(-28, -146, "the foot of the hill");
        waystone(92, 104, "the stone ring");
        waystone(14, 118, "the tarn");
        finds();
        clearSites();
        // plan version 2 (SPEC §50.9): a designed way over the moor, camps and stones on dry heather off the mounds and the path
        if (ctx.version() >= 2) {
            M m = m();
            designWay((x, z) -> ground(x, z) > water() + 1 && !inBarrow(x, z) && pathDist(x, z) > 5,
                    new dev.awaken.gates.realms.kit.WayPlanner.Dress(m.stoneA, m.pale, m.band));
        }
    }

    /** The mounds that sleep, in the order of their dens: x and z of each one's middle. */
    public List<int[]> sleepers() {
        List<int[]> out = new ArrayList<>();
        for (Barrow b : barrows) if (b.sealed) out.add(new int[]{b.cx, b.cz});
        return out;
    }

    // ---- the ground ---------------------------------------------------------------------------------------------------------

    private static double box(int x, int z, int x0, int z0, int x1, int z1) {
        double dx = Math.max(Math.max(x0 - x, x - x1), 0), dz = Math.max(Math.max(z0 - z, z - z1), 0);
        return Math.hypot(dx, dz);
    }

    static double pathDist(double x, double z) {
        double best = 1e9;
        for (int i = 0; i + 1 < PATH.length; i++) {
            double ax = PATH[i][0], az = PATH[i][1], dx = PATH[i + 1][0] - ax, dz = PATH[i + 1][1] - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz)));
            best = Math.min(best, Math.hypot(x - ax - dx * t, z - az - dz * t));
        }
        return best;
    }

    /**
     * The first look at the place (look round 1, item 9): six blocks round the Mouth, and the cone you face on arriving, 30 degrees wide along +z
     * for 40 blocks. Nothing that stands as high as your eye grows or is raised in it.
     */
    static boolean arrivalView(int x, int z) {
        double dx = x, dz = z + 205, d = Math.hypot(dx, dz);
        return d <= 6 || d <= 40 && dz > 0 && Math.abs(Math.atan2(dx, dz)) <= Math.toRadians(15);
    }

    static boolean onStair(int x, int z) {
        return x >= -41 && x <= -33 && z >= -140 && z <= -70;
    }

    /** The stair's own profile inside the blockout's wedge: flights of eight steps, then a landing ten long. It never dips under the wedge. */
    static int stairTop(int z) {
        int k = z + 140;
        if (k <= 0) return 64;
        return Math.min(95, 64 + (k - 1) / 18 * 8 + Math.min((k - 1) % 18 + 1, 8));
    }

    /** The top of the land in a column once the moor has been levelled for what stands on it (mounds not counted), or -1 off the land. */
    int surf(int x, int z) {
        if (onStair(x, z)) return stairTop(z);
        int raw = blockout.ground(x, z);
        if (raw < 0 || raw > HILL0_TOP) return raw;
        boolean moor = raw <= MOOR_TOP;
        int g = raw;
        for (Flat f : flats) {
            if (!moor && !f.cutsHill()) continue;
            double d = f.d().of(x, z);
            if (d > 6 * f.run()) continue;
            if (d <= 0) {
                if (f.shoulderOnly()) continue;
                return f.level();
            }
            int k = (int) (d / f.run());
            g = Math.max(f.level() - k, Math.min(f.level() + k, g));
        }
        // where a picture is taken standing on the moor, the moor is never higher than the feet of whoever takes it
        if (moor) for (Blockout.Camera c : blockout.cameras()) {
            double d = Math.max(Math.abs(c.x() - x - 0.5), Math.abs(c.z() - z - 0.5)) - 2.5;
            if (d < 4) g = Math.min(g, (int) Math.round(c.y() - 1.62) - 1 + Math.max(0, (int) Math.ceil(d)));
        }
        return g;
    }

    /** What you'd stand on: a mound's steps where one stands (its floor, inside it), the levelled moor everywhere else. */
    @Override
    public int ground(int x, int z) {
        for (Barrow b : barrows) if (b.in(x, z)) return b.top(x, z);
        return surf(x, z);
    }

    @Override
    public int biome(int x, int y, int z) {
        return y > 100 && Math.hypot(x - HX, z - HZ) < 60 ? 1 : 0;
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return x >= -93 && x <= 17 && z >= -59 && z <= -41 && y >= 98;
    }

    @Override
    public String heartClosedLine() {
        return "The ship's sides are too high to climb, and there's no way aboard yet.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        return new AirPlan().mist(66, 5, 0xa0acc0, 0.55F).mist(74, 4, 0x8c98b0, 0.3F).build();
    }

    @Override
    public List<Edit> change(String moment) {
        if (moment.equals("heart_open")) return heartOpen;
        if (moment.equals("heart_shut")) return heartShut;
        if (moment.startsWith("wake_")) {
            int i = Integer.parseInt(moment.substring(5));
            if (i >= 0 && i < barrows.size() && barrows.get(i).sealed) return barrows.get(i).wake;
        }
        return List.of();
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------------

    /** 0..1 for a column, mixed twice: one round of the kit's hash leaves stripes when it's asked about every cell of a field. */
    private static double roll(int x, int z, long salt) {
        return rnd(N.hash(x, z, salt), N.hash(z, x, salt + 1), salt);
    }

    private BlockState course(M m, int x, int y, int z) {
        double r = rnd(x * 31L + z, y, 0xC0);
        int k = Math.floorMod(y, 3);
        return r < 0.14 ? m.worn : r < 0.22 ? m.deep : k == 0 ? m.stoneA : k == 1 ? m.worn : m.deep;
    }

    private BlockState paving(M m, int x, int z) {
        double r = roll(x, z, 0x70);
        return r < 0.55 ? m.flag : r < 0.82 ? m.worn : m.hexstone;
    }

    @Override
    protected void draw(V v) {
        Blockout.Mass[] near = blockout.near(v.x0 - 1, v.z0 - 1, v.x0 + 16, v.z0 + 16);
        if (near.length == 0) return;
        M m = m();
        int[] s = SURF.get();
        for (int a = 0; a < 20; a++) for (int c = 0; c < 20; c++) s[a * 20 + c] = surf(v.x0 - 2 + a, v.z0 - 2 + c);
        byte[] col = COLUMN.get();
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            int span = blockout.column(x, z, near, col);
            if (span >= 0) base(v, m, x, z, col, span >> 16, span & 0xFFFF, s[(x - v.x0 + 2) * 20 + z - v.z0 + 2]);
        }
        stair(v, m);
        path(v, m, s);
        hill(v, m, s);
        rims(v, m, s);
        mouth(v, m);
        ship(v, m);
        longBarrow(v, m);
        if (v.in(110, 68, 120) || v.in(108, 68, 118) || v.in(112, 68, 122)) for (int a = -2; a <= 2; a++) for (int c = -2; c <= 2; c++) v.set(110 + a, surf(110 + a, 120 + c), 120 + c, m.pale);
        drawPieces(v, m);
        cover(v, m);
    }

    /** One column of the blockout in the moor's own blocks: turf over grave dust over rock, paving where the data says floor, the ship in its stone. */
    private void base(V v, M m, int x, int z, byte[] col, int lo, int hi, int s) {
        int g0 = -1;
        for (int y = hi; y >= lo && g0 < 0; y--) if (col[y] == G || col[y] == FLOOR || col[y] == WALK) g0 = y;
        boolean level = g0 >= 0 && s >= 0 && s != g0 && g0 <= HILL0_TOP;
        int depth = 0;
        for (int y = Math.max(hi, level ? s : hi); y >= lo; y--) {
            int r = y <= hi ? col[y] : 0;
            boolean earth = r == G || r == FLOOR || r == WALK;
            if (level) {
                if (y > s && earth) {
                    r = 0;
                    earth = false;
                } else if (y <= s && y > g0 && r == 0) {
                    r = G;
                    earth = true;
                }
            }
            if (earth) {
                BlockState b = y == 0 ? m.bedrock : depth == 0 && r != G ? floor(m, x, z) : depth < 2 ? (r == G ? m.turf : m.stoneA) : depth < 5 ? m.bed : m.deep;
                v.set(x, y, z, b);
                depth++;
                continue;
            }
            depth = 0;
            if (r == WATER) v.set(x, y, z, m.water);
            else if (r != 0) {
                BlockState b = shipState(m, x, y, z, r);
                if (b != null) v.set(x, y, z, b);
            }
        }
    }

    /** What's laid on a floor: the round pad you arrive on, the worn green of the village, plain flags anywhere else. */
    private BlockState floor(M m, int x, int z) {
        double pad = Math.hypot(x, z + 205);
        if (pad <= PAD) return pad < 1.5 ? m.hexstone : pad > 3.5 ? m.pale : paving(m, x, z);
        if (Math.hypot(x - 150, z + 40) <= 28.5) return n9.get(x / 9.0, z / 9.0) > 0.12 ? m.turf : rnd(x, z, 0x72) < 0.25 ? m.turf : paving(m, x, z);
        return paving(m, x, z);
    }

    /** The way up: stone stairs with a low wall each side, a landing after every eight steps. */
    private void stair(V v, M m) {
        if (v.x0 + 16 < -41 || v.x0 - 1 > -33 || v.z0 + 16 < -140 || v.z0 - 1 > -70) return;
        for (int x = Math.max(-41, v.x0 - 1); x <= Math.min(-33, v.x0 + 16); x++) for (int z = Math.max(-140, v.z0 - 1); z <= Math.min(-70, v.z0 + 16); z++) {
            int h = stairTop(z);
            boolean cheek = x == -41 || x == -33, step = h > stairTop(z - 1);
            for (int y = h - 2; y < h; y++) v.set(x, y, z, m.stoneA);
            v.set(x, h, z, cheek ? m.pale : step ? Build.stair(m.flagStairs, Direction.SOUTH) : rnd(x, z, 0x73) < 0.75 ? m.flag : m.worn);
            if (cheek) v.set(x, h + 1, z, m.pale);
        }
    }

    /** The broken path: five wide, most of its paving still there, a kerb stone on two cells in three along each edge, cut stones lying loose beside it. */
    private void path(V v, M m, int[] s) {
        if (v.x0 - 1 > 16 || v.x0 + 16 < -47 || v.z0 - 1 > -140 || v.z0 + 16 < -216) return;
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            double d = pathDist(x, z);
            if (d > 7.5 || Math.hypot(x, z + 205) <= PAD || box(x, z, -42, -150, -32, -141) <= 0) continue;
            int top = s[(x - v.x0 + 2) * 20 + z - v.z0 + 2];
            if (top < 0 || top > MOOR_TOP || inBarrow(x, z)) continue;
            if (d <= 2.5) {
                double r = roll(x, z, 0x74);
                if (r < 0.9) v.set(x, top, z, r < 0.5 ? m.flag : r < 0.75 ? m.worn : m.hexstone);
            } else if (d <= 3.5) {
                if (roll(x, z, 0x75) < 0.667 && v.empty(x, top + 1, z)) v.set(x, top + 1, z, m.kerb);
            } else if (roll(x, z, 0x76) < 1 / 14.0 && v.empty(x, top + 1, z)) {
                double r = roll(x, z, 0x77);
                BlockState stone = r < 0.5 ? m.stoneA : m.deep;
                if (r < 0.2 || r > 0.8) v.set(x, top, z, stone); // sunk flush with the turf
                else if (r < 0.5) {
                    v.set(x, top, z, stone);
                    v.set(x, top + 1, z, stone);
                } else v.set(x, top + 1, z, m.stoneSlab);
            }
        }
    }

    /** The hill's steps: each rise is faced in masonry with a pale top course, a buttress stub every few cells, kerb stones and candles along the lip. */
    private void hill(V v, M m, int[] s) {
        if (v.x0 - 1 > hill0.x1 + 1 || v.x0 + 16 < hill0.x0 - 1 || v.z0 - 1 > hill0.z1 + 1 || v.z0 + 16 < hill0.z0 - 1) return;
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            int i = (x - v.x0 + 2) * 20 + z - v.z0 + 2, top = s[i];
            if (onStair(x, z) || hill0.span(x, z) < 0 || inBarrow(x, z)) continue;
            int low = Math.min(Math.min(s[i - 20], s[i + 20]), Math.min(s[i - 1], s[i + 1])), high = Math.max(Math.max(s[i - 20], s[i + 20]), Math.max(s[i - 1], s[i + 1]));
            if (top > MOOR_TOP && top - low >= 2 && low >= 0) {
                for (int y = low + 1; y < top; y++) v.set(x, y, z, course(m, x, y, z));
                v.set(x, top, z, m.pale);
                if (!v.empty(x, top + 1, z)) continue;
                // two kerb stones in three, so there is always a gap to step down through; candles only on the side you arrive from
                if (z < HZ && Math.floorMod(x * 7 + z * 3, 13) == 0) v.set(x, top + 1, z, m.candles(2 + (int) (rnd(x, z, 0x78) * 3), true));
                else if (Math.floorMod(x + z * 3, 3) != 0) v.set(x, top + 1, z, m.kerb);
            } else if (high - top >= 3 && high > MOOR_TOP + 1 && Math.floorMod(x + 2 * z, 5) == 0 && v.empty(x, top + 1, z) && hill0.span(x, z) >= 0) {
                Direction to = s[i + 20] == high ? Direction.EAST : s[i - 20] == high ? Direction.WEST : s[i + 1] == high ? Direction.SOUTH : Direction.NORTH;
                int nx = x + to.getStepX(), nz = z + to.getStepZ();
                if (onStair(nx, nz) || inBarrow(nx, nz)) continue;
                for (int y = top + 1; y < high - 1; y++) v.set(x, y, z, course(m, x, y, z));
                v.set(x, high - 1, z, Build.stair(m.stoneStairs, to));
            }
        }
    }

    private boolean onRim(int x, int z) {
        for (Blockout.Mass k : rims) if (k.span(x, z) >= 0) return true;
        return false;
    }

    /** The hills round the edge aren't smooth: where a field of noise runs high the rock comes through, two to four cells proud of the turf. */
    private void rims(V v, M m, int[] s) {
        boolean any = false;
        for (Blockout.Mass k : rims) if (k.x1 >= v.x0 - 1 && k.x0 <= v.x0 + 16 && k.z1 >= v.z0 - 1 && k.z0 <= v.z0 + 16) any = true;
        if (!any) return;
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            int top = s[(x - v.x0 + 2) * 20 + z - v.z0 + 2];
            double over = crag.get(x / 15.0, z / 15.0) - 0.72;
            if (top <= MOOR_TOP || over <= 0 || !onRim(x, z) || hill0.span(x, z) >= 0) continue;
            // one rock to a patch, highest in its middle, so it reads as an outcrop and not as more standing stones
            BlockState rock = n31.get(x / 40.0, z / 40.0) > 0 ? m.stoneA : m.deep;
            v.foot(x, top, z, rock);
            for (int y = top - 1; y <= top + Math.min(4, 2 + (int) (over * 16)); y++) v.set(x, y, z, rock);
        }
    }

    /** Behind where you arrive: two stones and a lintel for the arch home to stand in. */
    private void mouth(V v, M m) {
        if (!v.in(-2, 64, -211) && !v.in(2, 64, -211)) return;
        for (int x = -2; x <= 2; x += 4) {
            v.foot(x, PATH_Y, -211, m.stoneA);
            for (int y = 64; y <= 66; y++) v.set(x, y, -211, y == 65 ? m.worn : m.stoneA);
            v.set(x, 68, -211, m.cap);
        }
        for (int x = -2; x <= 2; x++) v.set(x, 67, -211, m.pale);
    }

    // ---- the ship -------------------------------------------------------------------------------------------------------------

    /** Boxes with their corners and their bottom edges taken off, so the hull reads as a curve. */
    private static boolean rounded(Blockout.Mass h, int part, int x, int y, int z) {
        boolean side = z == h.z0 || z == h.z1;
        if (y == h.y0 && side) return true;
        if (part == 0) return side && (x == h.x0 || x == h.x1);
        int end = part <= 2 ? h.x0 : h.x1, in = part <= 2 ? 1 : -1;
        if (x == end && side) return true;
        return (part == 2 || part == 4) && (x == end && (z == h.z0 + 1 || z == h.z1 - 1) || x == end + in && side);
    }

    private boolean sailAt(int x, int y) {
        return x >= -38 && x <= -4 && y >= 128 && y <= 145 && sailCloth[(x + 38) * 18 + y - 128];
    }

    /** The cloth that's left: holes where the weave gave out, a ragged foot, and nothing that isn't still hanging from the yard or the mast. */
    private void weaveSail() {
        boolean[] cloth = new boolean[35 * 18];
        for (int x = -38; x <= -4; x++) {
            int foot = 128 + (int) (rnd(x, 7, seed ^ 0x5A) * 5);
            for (int y = foot; y <= 145; y++) cloth[(x + 38) * 18 + y - 128] = (x < -22 || x > -20) && weave.get(x * 0.21, y * 0.21) <= 0.62;
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = -38; x <= -4; x++) for (int y = 128; y <= 145; y++) {
            if (!cloth[(x + 38) * 18 + y - 128] || !(y == 145 || x == -23 || x == -19)) continue;
            sailCloth[(x + 38) * 18 + y - 128] = true;
            q.add(new int[]{x, y});
        }
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                int x = p[0] + dx, y = p[1] + dy;
                if (x < -38 || x > -4 || y < 128 || y > 145) continue;
                int i = (x + 38) * 18 + y - 128;
                if (!cloth[i] || sailCloth[i]) continue;
                sailCloth[i] = true;
                q.add(new int[]{x, y});
            }
        }
    }

    private BlockState shipState(M m, int x, int y, int z, int role) {
        if (role == GLOW) return m.glow;
        if (mast.contains(x, y, z)) return Math.floorMod(y - 104, 8) == 7 ? m.bronze : m.timber;
        if (role == FOLIAGE) return sailAt(x, y) ? m.sail : null;
        if (role != GREAT) return role == WALL ? m.stoneA : null;
        if (yard.contains(x, y, z)) return m.timberX;
        // the prow and the stern post are carved in ship(): the blockout's boxes only say where they stand
        for (Blockout.Mass p : posts) if (p.contains(x, y, z)) return null;
        boolean in = false;
        for (int i = 0; i < hullParts.length; i++) if (hullParts[i].contains(x, y, z) && !rounded(hullParts[i], i, x, y, z)) in = true;
        if (!in) return null;
        if (y == 106) return m.pale;
        if (y == DECK && x >= -79 && x <= 5 && z >= -57 && z <= -43) {
            boolean rim = x >= -44 && x <= -30 && z >= -54 && z <= -46;
            return x == -79 || x == 5 || z == -57 || z == -43 || rim ? m.pale : (x + z & 1) == 0 ? m.flag : m.tile;
        }
        return course(m, x, y, z);
    }

    /** What stands on and hangs off the hull: strakes and shields down both sides, thwarts across the deck with urns at their ends, bones, candles and braziers, the carved posts. */
    private void ship(V v, M m) {
        if (v.x0 - 1 > 22 || v.x0 + 16 < -100 || v.z0 - 1 > -39 || v.z0 + 16 < -61) return;
        for (int y = 98; y <= 104; y += 2) {
            for (int x = -80; x <= 6; x++) {
                if (x < -40 || x > -34) v.add(x, y, -60, Build.under(m.stoneStairs, Direction.SOUTH)); // the way aboard opens here
                v.add(x, y, -40, Build.under(m.stoneStairs, Direction.NORTH));
            }
            for (int x = -87; x <= 12; x++) if (x < -82 || x > 7) {
                v.add(x, y, -58, Build.under(m.stoneStairs, Direction.SOUTH));
                v.add(x, y, -42, Build.under(m.stoneStairs, Direction.NORTH));
            }
        }
        for (int x = -78; x <= 4; x += 4) {
            if (x < -40 || x > -34) v.add(x, 105, -60, m.shield);
            v.add(x, 105, -40, m.shieldSouth);
        }
        // the hold: a pale lip you step over, and a stair up out of it so nothing that falls in stays there
        for (int x = -44; x <= -30; x++) for (int z = -54; z <= -46; z++) {
            boolean ring = x == -44 || x == -30 || z == -54 || z == -46;
            if (ring && !(x == -30 && z == -47)) v.set(x, DECK + 1, z, m.paleSlab);
        }
        for (int i = 0; i < 5; i++) {
            for (int y = 98; y < 102 - i; y++) v.set(-31 - i, y, -47, m.pale);
            v.set(-31 - i, 102 - i, -47, Build.stair(m.flagStairs, Direction.EAST));
        }
        // shields along the inside of both rails too, at their top
        for (int x = -78; x <= 4; x += 4) {
            if (x < -40 || x > -34) v.add(x, 106, -57, m.shieldSouth);
            v.add(x, 106, -43, m.shield);
        }
        // thwarts: a beam across the deck every seven cells, open three wide in the middle to walk through; none over the hold
        for (int x = -76; x <= 1; x += 7) {
            if (x >= -45 && x <= -29) continue;
            for (int z = -57; z <= -43; z++) if (z < -51 || z > -49) v.set(x, DECK + 1, z, m.timberZ);
            // twenty-four urns, three to a thwart's end, on four of the thwarts
            if (x != -76 && x != -55 && x != -27 && x != -6) continue;
            for (int z = -57; z <= -43; z += 14) {
                v.set(x - 1, DECK + 1, z, m.urn);
                v.set(x + 1, DECK + 1, z, m.urn);
                v.set(x, DECK + 2, z, m.urn);
            }
        }
        // the light in pools, three fires in iron
        int[][] lights = {{-46, -55}, {-46, -45}, {-28, -55}, {-28, -45}, {-18, -53}, {-18, -47}, {-70, -52}, {-70, -48}, {-52, -56}, {-4, -52}, {-4, -48}, {2, -50}};
        for (int[] c : lights) v.set(c[0], DECK + 1, c[1], m.candles(4 + (c[0] & 1), true));
        int[][] bones = {{-74, -45}, {-66, -55}, {-58, -46}, {-50, -53}, {-26, -51}, {-24, -46}, {-12, -55}, {-10, -45}, {0, -53}, {3, -46}};
        for (int[] c : bones) v.set(c[0], DECK + 1, c[1], m.bones);
        for (int[] c : new int[][]{{-72, -50}, {-10, -50}, {-47, -50}}) {
            v.set(c[0], DECK + 1, c[1], m.iron);
            v.set(c[0], DECK + 2, c[1], m.brazier);
        }
        sheer(v, m);
        post(v, m, -93, -1, 22, 6, 3, 4, 4, 6, 3, 5, true);
        post(v, m, 16, 1, 11, 3, 1.5, 2, 2, 3, 2, 3, false);
    }

    private boolean railAt(int x, int z) {
        for (int i = 0; i < hullParts.length; i++) if (hullParts[i].contains(x, 106, z) && !rounded(hullParts[i], i, x, 106, z)) return true;
        return false;
    }

    /** The sheer line: over the last sixteen cells toward each end the rail climbs one block every four, so the ship's side is a curve and not a ruler. */
    private void sheer(V v, M m) {
        for (int x = Math.max(-93, v.x0 - 1); x <= Math.min(17, v.x0 + 16); x++) {
            int lift = ((x <= -78 ? -78 - x : x - 2) + 2) / 4;
            if (x > -78 && x < 2 || lift <= 0) continue;
            for (int z = -59; z <= -41; z++) {
                if (!railAt(x, z) || railAt(x - 1, z) && railAt(x + 1, z) && railAt(x, z - 1) && railAt(x, z + 1)) continue;
                v.set(x, 106, z, course(m, x, 106, z));
                for (int y = 107; y <= 106 + lift; y++) v.set(x, y, z, y == 106 + lift ? m.pale : course(m, x, y, z));
            }
        }
    }

    /**
     * A carved post: broad where it leaves the deck, leaning out over the end of the ship, curling back over it in an S, and ending in a head with a
     * gold eye each side and an open jaw. Scales down its outer curve. The prow's has the bell under its jaw, four links down.
     */
    private void post(V v, M m, int baseX, int out, int neck, double amp, double back, int thick, int wide, int headLen, int headWide, int headTall, boolean bell) {
        if (v.x0 - 1 > baseX + 12 || v.x0 + 16 < baseX - 12 || v.z0 - 1 > -48 || v.z0 + 16 < -53) return;
        Direction in = out < 0 ? Direction.EAST : Direction.WEST;
        double top = baseX;
        for (int i = 0; i < neck; i++) {
            double t = i / (double) (neck - 1), xc = baseX + out * (amp * Math.sin(Math.PI * t) - back * t * t);
            int tx = Math.max(1, (int) Math.round(N.lerp(thick, thick / 2.0, t))), w = Math.max(1, (int) Math.round(N.lerp(wide, wide / 2.0, t)));
            int xa = (int) Math.floor(xc - tx / 2.0 + 0.5), za = -50 - w / 2, y = 106 + i;
            for (int x = xa; x < xa + tx; x++) for (int z = za; z < za + w; z++) v.set(x, y, z, course(m, x, y, z));
            if ((i & 1) == 1 && i > 1) for (int z = za; z < za + w; z++) v.add(out < 0 ? xa - 1 : xa + tx, y, z, Build.under(m.stoneStairs, in));
            top = xc;
        }
        // the head looks back down the ship: a jaw, an open mouth, the snout over it, a low crown
        int y0 = 106 + neck, x0 = (int) Math.round(top) + out, za = -50 - headWide / 2;
        for (int j = 0; j < headTall; j++) for (int a = 0; a <= headLen; a++) {
            boolean jaw = j == 0 && a <= headLen - 2, hinge = j == 1 && a <= headLen / 2 - 1, crown = j == headTall - 1 && a >= 1 && a < headLen, skull = j >= 2 && j < headTall - 1;
            if (headTall <= 3) {
                hinge = false;
                jaw = j == 0 && a <= 1;
                skull = j == 1;
                crown = j == 2 && a <= headLen - 1;
            }
            if (!(jaw || hinge || crown || skull)) continue;
            boolean eye = j == headTall - 2 && a == headLen / 2;
            for (int z = za; z < za + headWide; z++) v.set(x0 - out * a, y0 + j, z, eye && (z == za || z == za + headWide - 1) ? m.gold : course(m, x0 - out * a, y0 + j, z));
        }
        if (!bell) return;
        int bx = x0 - out * (headLen - 2);
        for (int k = 1; k <= 4; k++) v.set(bx, y0 - k, -50, m.link);
        v.set(bx, y0 - 5, -50, m.bell);
    }

    /** The way aboard, which isn't there until the three Wardens are down: eight steps from the top of the hill, and the rail above them gone. */
    private void heartDoor() {
        M m = m();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -39; x <= -35; x++) {
            for (int z = -67; z <= -60; z++) {
                int top = 96 + z + 67;
                for (int y = 96; y < top; y++) heartOpen.add(new Edit(x, y, z, m.pale));
                heartOpen.add(new Edit(x, top, z, Build.stair(m.flagStairs, Direction.SOUTH)));
            }
            for (int z = -59; z <= -58; z++) for (int y = DECK + 1; y <= 106; y++) {
                heartOpen.add(new Edit(x, y, z, air));
                heartShut.add(new Edit(x, y, z, y == 106 ? m.pale : course(m, x, y, z)));
            }
        }
    }

    // ---- the long barrow ------------------------------------------------------------------------------------------------------

    /** The long barrow, where a Warden waits: grassed over, a low turf step round its foot with a kerb course, its hall lit and lined with the dead. Its door is at the east end and never sealed. */
    private void longBarrow(V v, M m) {
        Blockout.Mass lb = longBarrow;
        if (v.x0 - 1 > lb.x1 + 3 || v.x0 + 16 < lb.x0 - 3 || v.z0 - 1 > lb.z1 + 3 || v.z0 + 16 < lb.z0 - 3) return;
        for (int x = Math.max(lb.x0 - 2, v.x0 - 1); x <= Math.min(lb.x1 + 2, v.x0 + 16); x++) for (int z = Math.max(lb.z0 - 2, v.z0 - 1); z <= Math.min(lb.z1 + 2, v.z0 + 16); z++) {
            boolean in = x >= lb.x0 && x <= lb.x1 && z >= lb.z0 && z <= lb.z1;
            if (!in) {
                if (x > lb.x1 && z >= 102 && z <= 108) continue; // the way to the door
                int g = surf(x, z);
                boolean outer = x == lb.x0 - 2 || x == lb.x1 + 2 || z == lb.z0 - 2 || z == lb.z1 + 2;
                v.foot(x, g, z, m.deep);
                for (int y = g + 1; y <= (outer ? 65 : 66); y++) v.set(x, y, z, outer && y == g + 1 ? (roll(x, z, 0x7E) < 0.75 ? m.pale : m.stoneA) : m.turf);
                continue;
            }
            int top = lb.top(x, z);
            if (x == lb.x0 || x == lb.x1 || z == lb.z0 || z == lb.z1) {
                // grassed sides, the top edge taken off by one cell; the east end keeps its stone doorway
                boolean door = x == lb.x1 && z >= 103 && z <= 107;
                for (int y = 60; y <= top; y++) if (v.get(x, y, z) != null) v.set(x, y, z, door && y <= 68 ? m.pale : y == top ? null : m.turf);
            }
            boolean hall = x >= -154 && x <= -106 && z >= 102 && z <= 108, way = x >= -105 && z >= 104 && z <= 106;
            if (hall || way) {
                v.set(x, 64, z, (x + z & 1) == 0 ? m.flag : m.hexstone);
                if (hall && v.get(x, 70, z) != null) v.set(x, 70, z, (x & 3) == 0 ? m.timberX : m.deep);
                if (way && v.get(x, 68, z) != null) v.set(x, 68, z, m.pale);
            } else if (x >= -155 && x <= -105 && z >= 101 && z <= 109 || x >= -105 && z >= 103 && z <= 107) {
                for (int y = 65; y <= 69; y++) if (v.get(x, y, z) != null && x != lb.x1) v.set(x, y, z, (z == 101 || z == 109) && (y == 66 || y == 67) && Math.floorMod(x, 4) == 1 ? m.ossuary : course(m, x, y, z));
            }
            if (!hall) continue;
            if ((z == 102 || z == 108) && Math.floorMod(x, 6) == 0) v.set(x, 65, z, m.candles(3, true));
            else if (z == 105 && (x == -150 || x == -149 || x == -144 || x == -143 || x == -116 || x == -115 || x == -110 || x == -109)) {
                v.set(x, 65, z, m.pale);
                v.set(x, 66, z, m.paleSlab);
            } else if (z != 105 && rnd(x, z, 0x79) < 0.05) v.set(x, 65, z, m.bones);
        }
    }

    // ---- what grows -------------------------------------------------------------------------------------------------------------

    private boolean inBarrow(int x, int z) {
        for (Barrow b : barrows) if (b.in(x, z)) return true;
        return false;
    }

    /** Nothing grows on the path, in a doorway or round anything you've come to put a hand on. Heather comes right up to the paving. */
    private boolean bare(int x, int z) {
        if (pathDist(x, z) <= 2.5 || Math.hypot(x, z + 205) <= PAD || x >= -44 && x <= -30 && z >= -152 && z <= -58 || tended(x, z)) return true;
        if (box(x, z, -99, 102, -93, 108) <= 0) return true;
        for (Barrow b : barrows) if (b.apron(x, z)) return true;
        // nor where a picture is taken from the ground: a sprig in front of the eye is all the picture would show
        for (Blockout.Camera c : blockout.cameras()) if (Math.abs(c.x() - x - 0.5) <= 2.5 && Math.abs(c.z() - z - 0.5) <= 2.5 && c.y() < ground(x, z) + 5) return true;
        return false;
    }

    /** Heather, pale heather and bent grass in drifts over every piece of turf open to the sky: two fields of noise, never an even sprinkle. */
    private void cover(V v, M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            int top = -1;
            for (int y = 135; y >= 56 && top < 0; y--) if (v.get(x, y, z) != null) top = y;
            if (top < 0 || v.get(x, top, z) != m.turf || bare(x, z)) continue;
            if (top >= 80 && roll(x, z, 0x80) > 0.3 && onRim(x, z)) continue; // the high rims are wind-bare: three plants in ten
            double tarn = Math.hypot(x, z - 150);
            if (tarn > 24.5 && tarn <= 27.5) {
                if (roll(x, z, 0x7A) < 0.75) v.set(x, top + 1, z, m.bent); // reeds at the water's edge
                continue;
            }
            if (n9.get(x / 9.0, z / 9.0) + (roll(x, z, 0x7B) - 0.5) * 0.3 < -0.33) continue;
            double kind = n31.get(x / 31.0, z / 31.0) + (roll(x, z, 0x7C) - 0.5) * 0.2;
            if ((kind > 0.36 || kind < -0.44) && arrivalView(x, z)) continue; // bent and dry heather stand tall in the arriving eye: only low heather there
            v.set(x, top + 1, z, kind > 0.36 ? m.bent : kind < -0.44 ? m.heatherPale : m.heather);
        }
    }

    // ---- standing stones --------------------------------------------------------------------------------------------------------

    private boolean stoneCanStand(int x, int z) {
        // none in the arriving view, and for the first 40 blocks none within 6 of the path's centre line (7 here: a tall one has a foot two wide)
        if (Math.hypot(x, z + 205) <= 41 && (pathDist(x, z) <= 7 || arrivalView(x, z) || arrivalView(x + 1, z + 1))) return false;
        if (pathDist(x, z) < 4.5 || Math.hypot(x, z + 205) < PAD + 1 || x >= -44 && x <= -30 && z >= -152 && z <= -58) return false;
        if (x >= -100 && x <= 22 && z >= -62 && z <= -38 || Math.hypot(x, z - 150) < 30 || Math.hypot(x - 150, z + 40) < 30) return false;
        if (box(x, z, longBarrow.x0 - 3, longBarrow.z0 - 3, longBarrow.x1 + 6, longBarrow.z1 + 3) <= 0) return false;
        for (Barrow b : barrows) if (b.in(x, z) || b.apron(x, z)) return false;
        int g = ground(x, z);
        return g >= PATH_Y - 2;
    }

    private void stone(int x, int z, long salt) {
        if (!stoneCanStand(x, z)) return;
        pieces.add(new Stone(x, z, 5 + r.nextInt(5), salt, -1));
        stones++;
    }

    /** About a hundred and fifty of them: along the path, round every mound, on the lips of the hill's steps, and twelve in a true ring. */
    private void standingStones() {
        // along the path, on alternate sides
        double along = 14;
        boolean left = true;
        for (int i = 0; i + 1 < PATH.length; i++) {
            double ax = PATH[i][0], az = PATH[i][1], dx = PATH[i + 1][0] - ax, dz = PATH[i + 1][1] - az, len = Math.hypot(dx, dz);
            for (; along < len; along += 9 + r.nextInt(6)) {
                double out = (5 + r.nextInt(4)) * (left ? 1 : -1);
                stone((int) Math.round(ax + dx * along / len - dz / len * out), (int) Math.round(az + dz * along / len + dx / len * out), 0x51);
                left = !left;
            }
            along -= len;
        }
        // a ring round every mound, open at its door
        for (Barrow b : barrows) {
            if (!b.sealed) continue;
            int n = 6 + r.nextInt(5);
            double turn = r.nextDouble() * Math.PI;
            for (int i = 0; i < n; i++) {
                double a = turn + i * Math.PI * 2 / n;
                if (Math.cos(a) * b.ux + Math.sin(a) * b.uz > 0.8) continue;
                stone(b.cx + (int) Math.round(Math.cos(a) * (b.r + 3)), b.cz + (int) Math.round(Math.sin(a) * (b.r + 3)), 0x52);
            }
        }
        // the hill: the outer lip of every second step
        int[][] steps = {{140, 90}, {117, 70}, {93, 50}, {70, 30}};
        for (int[] s : steps) {
            double a = r.nextDouble();
            while (a < Math.PI * 2) {
                int x = HX + (int) Math.round(Math.cos(a) * (s[0] - 1.5)), z = HZ + (int) Math.round(Math.sin(a) * (s[1] - 1.5));
                stone(x, z, 0x53);
                a += (14 + r.nextInt(7)) / Math.hypot(s[0] * Math.sin(a), s[1] * Math.cos(a));
            }
        }
        // the ring where a Warden waits
        for (int i = 0; i < 12; i++) stone(110 + (int) Math.round(Math.cos(i * Math.PI / 6) * 16), 120 + (int) Math.round(Math.sin(i * Math.PI / 6) * 16), 0x54);
        // a pair at each of the stair's landings, standing on its side walls
        for (int z : new int[]{-131, -113, -95}) for (int x : new int[]{-41, -33}) pieces.add(new Stone(x, z, 5, 0x55, stairTop(z) + 1));
    }

    /**
     * A standing stone: one stone, one material, five to nine high, and a stepped cap so none ends in a cube. A tall one is two by two for its lower
     * half. Each column stands on the ground under it.
     */
    private final class Stone extends Piece<M> {
        final int x, z, h, fixed;
        final long salt;

        Stone(int x, int z, int h, long salt, int fixed) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.salt = salt;
            this.fixed = fixed;
            x0 = x;
            z0 = z;
            x1 = x + (h > 6 ? 1 : 0);
            z1 = z + (h > 6 ? 1 : 0);
            mayOverlap = true;
            label = "standing stone";
        }

        @Override
        public void draw(V v, M m) {
            int g = fixed >= 0 ? fixed : ground(x, z);
            if (g < 0) return;
            BlockState stone = rnd(x * 31L + z, salt, 0x57) < 0.7 ? m.stoneA : m.pale;
            v.foot(x, g, z, stone);
            for (int k = 1; k <= h; k++) v.set(x, g + k, z, stone);
            v.set(x, g + h + 1, z, m.cap);
            if (h <= 6) return;
            // the broad foot: three more columns up to half its height, each on its own ground
            for (int a = 0; a <= 1; a++) for (int c = 0; c <= 1; c++) {
                if (a + c == 0) continue;
                int gg = fixed >= 0 ? fixed : ground(x + a, z + c);
                if (gg < 0 || gg > g + h / 2) continue;
                v.foot(x + a, gg, z + c, stone);
                for (int y = gg + 1; y <= g + h / 2; y++) v.set(x + a, y, z + c, stone);
            }
        }
    }

    // ---- the mounds ---------------------------------------------------------------------------------------------------------------

    private static Direction dir(int dx, int dz) {
        return dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * A burial mound: three broad steps under turf, each climbed by narrow ones, the corners gone soft, a kerb course of stone round the foot. A doorway
     * with pale jambs and a lintel, a corbelled passage, a round chamber with a coffin. Sealed, the doorway is walled up two deep and the candles inside are out;
     * waking it takes the wall away and lights them.
     */
    private final class Barrow extends Piece<M> {
        final int index, cx, cz, r, h, ux, uz, b;
        final boolean sealed;
        final int[] rad, tier;
        final List<Edit> wake = new ArrayList<>();

        Barrow(int index, int cx, int cz, int r, int h, int ux, int uz, int b, boolean sealed) {
            this.index = index;
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            this.h = h;
            this.ux = ux;
            this.uz = uz;
            this.b = b;
            this.sealed = sealed;
            rad = new int[h];
            tier = new int[h];
            int tread = Math.max(1, (int) Math.round((r - (h - 1) - 4.5) / 2)), a = (h + 2) / 3, c = (h + 1) / 3;
            for (int k = 0; k < h; k++) {
                tier[k] = k < a ? 0 : k < a + c ? 1 : 2;
                rad[k] = r - k - tread * tier[k];
            }
            // each broad step's corner is taken off by one cell, so the mound reads as grassed over, not as cut stone
            for (int k = 1; k < h; k++) if (k == h - 1 || tier[k + 1] != tier[k]) rad[k]--;
            box(cx, cz, r + 5, r + 5);
            mayOverlap = true;
            label = "barrow";
            if (!sealed) return;
            M m = m();
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int t = r - 1; t <= r; t++) for (int s = -1; s <= 1; s++) for (int y = b + 1; y <= b + 3; y++) {
                wake.add(new Edit(cx + ux * t + uz * s, y, cz + uz * t - ux * s, s != 0 && y == b + 3 ? corbel(m, s) : air));
            }
            for (int[] c4 : CANDLES) wake.add(new Edit(cx + c4[0], b + 1, cz + c4[1], m.candles(3, true)));
        }

        static final int[][] CANDLES = {{3, 2}, {-3, 2}, {3, -2}, {-3, -2}};

        /** How far along the way to the door a column is, and how far to the side of it. */
        int t(int x, int z) {
            return (x - cx) * ux + (z - cz) * uz;
        }

        int s(int x, int z) {
            return (x - cx) * uz - (z - cz) * ux;
        }

        BlockState corbel(M m, int s) {
            return Build.under(m.stoneStairs, dir(uz * s, -ux * s));
        }

        int courses(double d) {
            int n = 0;
            for (int k = 0; k < h; k++) if (d <= rad[k] + 0.5) n++;
            return n;
        }

        boolean porch(int x, int z) {
            int t = t(x, z);
            return t >= 5 && t <= r && Math.abs(s(x, z)) <= 2;
        }

        boolean hollow(int x, int z) {
            int t = t(x, z);
            return Math.hypot(x - cx, z - cz) <= 4.5 || t >= 4 && t <= r && Math.abs(s(x, z)) <= 1;
        }

        boolean in(int x, int z) {
            return Math.hypot(x - cx, z - cz) <= r + 0.5;
        }

        /** The paved ground in front of the door, kept level and clear. */
        boolean apron(int x, int z) {
            int t = t(x, z);
            return t > r - 1 && t <= r + 4 && Math.abs(s(x, z)) <= 3;
        }

        int top(int x, int z) {
            if (hollow(x, z)) return b;
            return b + Math.max(courses(Math.hypot(x - cx, z - cz)), porch(x, z) ? 4 : 0);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double d = Math.hypot(x - cx, z - cz);
                int t = t(x, z), s = Math.abs(s(x, z));
                if (d > r + 0.5) {
                    // the paving in front of the door, on its own footing, and the two candles left burning outside
                    if (t > r && t <= r + 2 && s <= 3) {
                        v.foot(x, b, z, m.stoneA);
                        v.set(x, b, z, paving(m, x, z));
                        for (int y = b + 1; y <= b + 4; y++) v.set(x, y, z, null);
                        if (t == r + 2 && s == 2) v.set(x, b + 1, z, m.candles(3, true));
                    }
                    continue;
                }
                int n = courses(d);
                for (int y = b + 1; y <= b + h + 3; y++) v.set(x, y, z, null);
                v.foot(x, b, z, m.deep);
                // turf all the way up; the only stone is the kerb course round the foot
                for (int k = 0; k < n; k++) v.set(x, b + 1 + k, z, k == 0 && n == 1 ? (roll(x, z, 0x7D) < 0.75 ? m.pale : m.stoneA) : m.turf);
                // inside it is still built: the chamber's wall and its corbelled roof
                if (d > 4.5 && d <= 5.5) for (int y = b + 1; y <= b + 3; y++) v.set(x, y, z, course(m, x, y, z));
                if (d <= 4.5 && n >= 4) v.set(x, b + (d <= 2.5 ? 5 : 4), z, m.stoneA);
                boolean chamber = d <= 4.5, way = t >= 4 && t <= r && s <= 1;
                if (chamber || way) {
                    v.set(x, b, z, (x + z & 1) == 0 ? m.flag : m.worn);
                    for (int y = b + 1; y <= b + 3; y++) v.set(x, y, z, null);
                    if (chamber && d <= 2.5) v.set(x, b + 4, z, null);
                    if (way && !chamber) {
                        if (s == 1) v.set(x, b + 3, z, corbel(m, s(x, z)));
                        if (sealed && t >= r - 1) for (int y = b + 1; y <= b + 3; y++) v.set(x, y, z, m.deep);
                    }
                }
                if (porch(x, z) && !chamber) {
                    // the porch stands proud of the mound's lowest steps: side walls, a slab roof, a lintel across the front
                    if (s == 2) for (int y = b + 1; y <= b + 3; y++) v.set(x, y, z, t == r ? m.pale : n >= 3 ? course(m, x, y, z) : m.turf);
                    if (v.get(x, b + 4, z) == null || t == r) v.set(x, b + 4, z, t == r ? m.paleSlab : m.turf);
                }
            }
            // inside: the coffin on the middle, bones about it, a candle at each quarter
            if (!v.in(cx, b, cz) && !v.in(cx + 4, b, cz + 4) && !v.in(cx - 4, b, cz - 4) && !v.in(cx + 4, b, cz - 4) && !v.in(cx - 4, b, cz + 4)) return;
            for (int k = 0; k <= 1; k++) {
                v.set(cx - ux * k, b + 1, cz - uz * k, m.pale);
                v.set(cx - ux * k, b + 2, cz - uz * k, m.paleSlab);
            }
            for (int[] c : CANDLES) v.set(cx + c[0], b + 1, cz + c[1], m.candles(3, !sealed));
            for (int[] c : new int[][]{{2, -1}, {-2, 1}, {1, 3}, {-1, -3}}) v.set(cx + c[0], b + 1, cz + c[1], m.bones);
        }
    }

    // ---- the village ----------------------------------------------------------------------------------------------------------------

    /** A candle-keeper's house: seven square, low stone walls, a turf roof stepping in with its corners off, a door, and a candle in a window slit. */
    private final class House extends Piece<M> {
        final int cx, cz, ux, uz, g;
        final long salt;

        House(int cx, int cz, int ux, int uz, long salt) {
            this.cx = cx;
            this.cz = cz;
            this.ux = ux;
            this.uz = uz;
            this.salt = salt;
            g = surf(cx, cz);
            box(cx, cz, 3, 3);
            label = "house";
        }

        @Override
        public void draw(V v, M m) {
            for (int a = -3; a <= 3; a++) for (int c = -3; c <= 3; c++) {
                int x = cx + a, z = cz + c, ring = Math.max(Math.abs(a), Math.abs(c));
                v.foot(x, g, z, m.stoneA);
                v.set(x, g, z, ring == 3 ? m.stoneA : m.planks);
                for (int y = g + 1; y <= g + 8; y++) v.set(x, y, z, null);
                if (ring == 3) {
                    boolean corner = Math.abs(a) == 3 && Math.abs(c) == 3;
                    for (int y = g + 1; y <= g + 3; y++) v.set(x, y, z, corner ? m.pale : course(m, x, y, z));
                    if (!corner) v.set(x, g + 4, z, m.turf);
                } else if (ring == 2) {
                    if (Math.abs(a) != 2 || Math.abs(c) != 2) v.set(x, g + 5, z, m.turf);
                } else if (ring == 0 || a == 0 || c == 0) v.set(x, g + 6, z, m.turf);
            }
            // the door, and across from it a slit with a candle on its sill
            for (int y = g + 1; y <= g + 2; y++) v.set(cx + ux * 3, y, cz + uz * 3, null);
            v.set(cx - ux * 3, g + 2, cz - uz * 3, m.candles(1, true));
            v.set(cx - ux * 2 + uz, g + 1, cz - uz * 2 - ux, m.candles(2, true));
        }
    }

    // ---- what's worth finding ---------------------------------------------------------------------------------------------------------

    private void find(int x, int gy, int z, int i) {
        pieces.add(new Find(x, gy, z, seed + i));
        site(SiteKind.FIND, x, gy + 1, z, 2, "");
    }

    /** Sixteen, none behind a seal: on the tops of mounds, at the foot of tall stones well off the path, on the hill's far side, on the islet, behind the long barrow. */
    private void finds() {
        int i = 0;
        for (int k : new int[]{0, 1, 2, 4, 6, 7, 8}) {
            Barrow b = barrows.get(k);
            find(b.cx, b.b + b.h, b.cz, i++);
        }
        for (int[] f : new int[][]{{70, -190}, {-120, -190}, {185, -170}, {-150, 40}, {60, 30}}) {
            pieces.add(new Stone(f[0] + 2, f[1], 8, 0x56, -1));
            stones++;
            find(f[0], ground(f[0], f[1]), f[1], i++);
        }
        find(HX, ground(HX, 15), 15, i++);
        find(20, ground(20, -15), -15, i++);
        find(0, PATH_Y + 3, 157, i++);
        find(-166, ground(-166, 105), 105, i);
    }

    /** How many standing stones were raised, for whoever is checking the count. */
    public int stoneCount() {
        return stones;
    }
}
