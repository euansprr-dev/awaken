package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.KitLand;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.piece.Find;
import dev.awaken.gates.realms.kit.piece.Hut;
import dev.awaken.gates.realms.kit.piece.MouthRing;
import dev.awaken.gates.realms.kit.piece.Stall;
import dev.awaken.gates.realms.kit.piece.Tree;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.rnd;
import static dev.awaken.gates.realms.kit.N.smooth;

/**
 * The Vine Seat (SPEC 50.8): a stepped temple on an island in a green lagoon with one enormous tree growing out of the top of it. Its roots come down over
 * the tiers and through the inside of the building, and the Lord waits in a round floor at the foot of the trunk. The rule of the place: the roots close in
 * on whatever stands still, and shut the old ways behind you.
 */
public final class VineSeat extends KitLand<VineSeat.M> {
    static final int WATER = 62, BASE = 64, TH = 14, TIERS = 6, H0 = 112, IN = 16, DOOR_Y = BASE + 3 * TH, BOWL = 70, HALF = 400;
    static final double BOWL_R = 44, WELL_R = 23, RIM = 340;
    /** Half the width of the great stair. */
    static final int SW = 13;

    public static final class M extends Mats {
        final BlockState bark = b("fane_bark"), barkMoss = b("fane_bark_moss"), leaf = b("fane_leaf"), thorn = b("fane_thorn"), relief = b("fane_relief"),
                vines = b("temple_vines"), fern = b("temple_fern"), drift = b("temple_moss_drift"), carpet = b("temple_moss"), brazier = b("temple_brazier"), fall = b("temple_waterfall"),
                carving = b("temple_carving"), link = Blocks.IRON_CHAIN.defaultBlockState(), water = Blocks.WATER.defaultBlockState(),
                tallFern = Blocks.LARGE_FERN.defaultBlockState(), bush = Blocks.AZALEA.defaultBlockState(), grass = Blocks.SHORT_GRASS.defaultBlockState(), lily = Blocks.LILY_PAD.defaultBlockState();

        M() {
            super("fane", new Accents(b("temple_carving"), b("fane_relief"), b("fane_window"), b("temple_vines"), b("temple_brazier"), b("temple_moss_drift"), b("temple_idol"),
                    Blocks.JUNGLE_FENCE.defaultBlockState(), b("fane_leaf")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (VineSeat.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    @Override
    protected M mats() {
        return m();
    }

    /** A room knows the shape of its floor, so the game can stand things on it. */
    abstract static class Room extends Piece<M> {
        abstract boolean has(int x, int z);

        abstract int floor(int x, int z);
    }

    final SimplexNoise n1, n2, n3, moss;
    private final List<Room> rooms = new ArrayList<>();
    private final List<int[]> towers = new ArrayList<>();
    private final List<Curtain> curtains = new ArrayList<>();
    private final List<Edit> grille = new ArrayList<>();

    public VineSeat(Ctx ctx) {
        super(ctx, 0x46414E45L);
        n1 = N.noise(seed + 11);
        n2 = N.noise(seed + 12);
        n3 = N.noise(seed + 13);
        moss = N.noise(seed + 14);
        for (int i = 0; i < 26; i++) {
            double a = i * 2.399963 + jit(0.4), d = 372 + (i % 5) * 16 + jit(14);
            towers.add(new int[]{(int) (Math.cos(a) * d), (int) (Math.sin(a) * d), 30 + (i * 7) % 26, 120 + (i * 37) % 110});
        }
        pieces.add(new Temple());
        pieces.add(new GreatStair());
        pieces.add(new Gatehouse());
        pieces.add(new Face(-56));
        pieces.add(new Face(56));
        room(new Bowl());
        room(new Gallery());
        // the arms from the balcony out to the third terrace
        room(new Arm(true, 54, 66, 0, 4, DOOR_Y, 10));
        room(new Arm(true, -66, -54, 0, 4, DOOR_Y, 10));
        room(new Arm(false, 54, 66, 0, 4, DOOR_Y, 10));
        room(new Arm(false, -66, -54, 0, 4, DOOR_Y, 10));
        // the Root Hall, inside the second tier on the west side
        room(new Arm(true, -98, -94, 0, 3, BASE + TH, 6));
        room(new RootHall());
        stairs();
        arcStairs();
        for (int x : new int[]{-92, -34, 30, 90}) pieces.add(new Falls(x, 0));
        for (int x : new int[]{-70, 70}) pieces.add(new Falls(x, 1));
        causeway();
        islets();
        tree();
        curtains();
        village();
        placeSites();
        clearSites();
    }

    private void room(Room r) {
        r.mayOverlap = true;
        rooms.add(r);
        pieces.add(r);
    }

    // ---- the land ----------------------------------------------------------------------------------------------------------

    private double r(int x, int z) {
        return Math.hypot(x, z * 0.92) + n1.get(x / 120.0, z / 120.0) * 26 + n2.get(x / 40.0, z / 40.0) * 8;
    }

    /** The ground with nothing built on it: lagoon, shore, jungle floor, and limestone towers closing it all in. */
    int terrain(int x, int z) {
        double r = r(x, z);
        double h = lerp(53, 66, smooth(176, 232, r));
        if (r > 232) h += (n3.get(x / 70.0, z / 70.0) + 0.7) * 9 * smooth(232, 280, r);
        h += Math.pow(smooth(RIM, RIM + 66, r), 1.6) * 190;
        for (int[] t : towers) {
            double d = Math.hypot(x - t[0], z - t[1]) + n2.get(x / 16.0, z / 16.0) * 7;
            if (d < t[2]) h = Math.max(h, 60 + t[3] * Math.pow(1 - Math.pow(d / t[2], 5), 0.5));
        }
        return (int) h;
    }

    private static int half(int tier) {
        return H0 - tier * IN;
    }

    private static int tierTop(int tier) {
        return BASE + (tier + 1) * TH;
    }

    /** Which step of the temple this column stands on, or -1 clear of it. */
    private static int tierAt(int mm) {
        if (mm > H0) return -1;
        return Math.min(TIERS - 1, (H0 - mm) / IN);
    }

    private static int stairY(int z) {
        return BASE + Math.max(0, Math.min(42, 122 - z));
    }

    @Override
    public int ground(int x, int z) {
        for (int i = rooms.size() - 1; i >= 0; i--) if (rooms.get(i).has(x, z)) return rooms.get(i).floor(x, z);
        int a = Math.abs(x), mm = Math.max(a, Math.abs(z));
        if (a <= SW && z >= 66 && z <= 123) return z <= 80 ? DOOR_Y : stairY(z);
        if (a <= 4 && z > 123 && z <= 214 || a <= 12 && z >= 196 && z <= 214) return WATER + 3;
        if (mm <= H0) return tierTop(tierAt(mm)) - 1;
        return Math.max(terrain(x, z), 0);
    }

    @Override
    public double edge(int x, int z) {
        return Math.hypot(x, z) - 400;
    }

    @Override
    public int halfSize() {
        return HALF;
    }

    @Override
    public int biome(int x, int y, int z) {
        return x * x + z * z < BOWL_R * BOWL_R && y < DOOR_Y ? 1 : 0;
    }

    @Override
    public float mouthYaw() {
        return 180;
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return x * x + z * z < BOWL_R * BOWL_R && y < DOOR_Y;
    }

    @Override
    public String heartClosedLine() {
        return "The roots have grown shut across every way down to that floor. Something else in this place is keeping them closed.";
    }

    @Override
    public List<Edit> change(String moment) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<Edit> out = new ArrayList<>();
        switch (moment) {
            case "heart_open" -> {
                for (Edit e : grille) out.add(new Edit(e.x(), e.y(), e.z(), air));
            }
            case "heart_shut" -> out.addAll(grille);
            case "heart_weeps" -> spurs(out, 1);
            case "heart_winds" -> spurs(out, 2);
            case "heart_still" -> spurs(out, 0);
            default -> {
                if (moment.startsWith("roots_shut_") || moment.startsWith("roots_open_")) {
                    int i = Integer.parseInt(moment.substring(11));
                    if (i >= 0 && i < curtains.size()) curtains.get(i).edits(out, moment.startsWith("roots_open"));
                }
            }
        }
        return out;
    }

    public int curtainCount() {
        return curtains.size();
    }

    public int[] curtainAt(int i) {
        Curtain c = curtains.get(i);
        return new int[]{c.x, c.fy + 1, c.z};
    }

    /** As the Lord weakens, roots heave up through the paving in six spokes, and then six more, and the open floor is gone. */
    private void spurs(List<Edit> out, int stage) {
        M m = m();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -42; x <= 42; x++) for (int z = -42; z <= 42; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r < 19 || r > 40) continue;
            double a = Math.atan2(z, x) + Math.PI;
            double sp = a / (Math.PI / 6);
            int spoke = (int) Math.floor(sp);
            double off = Math.abs(sp - spoke - 0.5) * r * (Math.PI / 6);
            if (off > 1.6) continue;
            boolean first = spoke % 2 == 0, on = stage >= 1 && first || stage >= 2 && !first;
            int h = 1 + (int) Math.round(3.2 * Math.sin((r - 19) / 21 * Math.PI) * (1.2 - off / 2));
            for (int k = 1; k <= h; k++) out.add(new Edit(x, BOWL + k, z, on ? (k == h ? m.barkMoss : m.bark) : air));
        }
    }

    @Override
    public void paint(V v) {
        M m = mats();
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) column(v, m, x, z);
        drawPieces(v, m);
        for (Edit e : grille) if (v.in(e.x(), e.y(), e.z())) v.set(e.x(), e.y(), e.z(), e.state());
    }

    private void column(V v, M m, int x, int z) {
        if (Math.abs(x) > HALF || Math.abs(z) > HALF) return;
        int g = terrain(x, z);
        double rr = r(x, z);
        boolean karst = rr > RIM + 8 || g > 96;
        v.set(x, 0, z, m.bedrock);
        for (int y = 1; y < g - 3; y++) v.set(x, y, z, karst ? rockOf(m, x, y, z) : m.rock);
        for (int y = Math.max(1, g - 3); y < g; y++) v.set(x, y, z, karst ? rockOf(m, x, y, z) : m.bed);
        boolean wet = g < WATER;
        BlockState top;
        if (wet) top = rnd(x, z, 1) < 0.4 ? m.bed : m.deep;
        else if (g <= WATER + 1) top = m.bed;
        else if (karst) {
            int slope = Math.abs(terrain(x + 2, z) - g) + Math.abs(terrain(x, z + 2) - g);
            top = slope > 4 ? rockOf(m, x, g, z) : m.moss;
        } else top = moss.get(x / 22.0, z / 22.0) > 0.45 ? m.bed : m.moss;
        v.set(x, g, z, top);
        if (wet) {
            for (int y = g + 1; y <= WATER; y++) v.set(x, y, z, m.water);
            if (g >= WATER - 3 && rnd(x, z, 2) < 0.05) v.set(x, WATER + 1, z, m.lily);
            return;
        }
        if (top != m.moss && top != m.bed) return;
        if (tended(x, z)) return;
        double c = rnd(x, z, 3);
        if (c < 0.1) v.set(x, g + 1, z, m.fern);
        else if (c < 0.16) v.set(x, g + 1, z, m.grass);
        else if (c < 0.2) v.set(x, g + 1, z, m.bush);
        else if (c < 0.3) v.set(x, g + 1, z, m.carpet);
    }

    private BlockState rockOf(M m, int x, int y, int z) {
        double s = n2.get(x / 18.0 + y / 40.0, z / 18.0) + (Math.floorMod(y + (int) (n1.get(x / 60.0, z / 60.0) * 6), 7) < 2 ? 0.45 : 0);
        return s > 0.6 ? m.moss : s > 0.2 ? m.pale : s > -0.35 ? m.stoneA : m.worn;
    }

    /** One block of a built wall: pale where the sun's been, mossy low down and where the water runs. */
    BlockState course(M m, int x, int y, int z) {
        double n = moss.get(x / 13.0 + z / 13.0, y / 9.0) + (y < BASE + 20 ? 0.3 : 0);
        double f = rnd(x * 31 + z, y, 77);
        if (n > 0.6) return f < 0.6 ? m.worn : m.moss;
        if (n > 0.25) return f < 0.25 ? m.worn : f < 0.6 ? m.stoneA : m.pale;
        return f < 0.72 ? m.pale : m.stoneA;
    }

    // ---- the temple ---------------------------------------------------------------------------------------------------------

    /** Six great steps of cut stone, each face broken by buttresses and panels so no wall is ever one flat plane. */
    final class Temple extends Piece<M> {
        Temple() {
            box(0, 0, H0 + 6, H0 + 6);
            mayOverlap = true;
            label = "temple";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) column(v, m, x, z);
        }

        private int skirt(int h) {
            return h < 4 ? 2 : h < 8 ? 1 : 0;
        }

        private void column(V v, M m, int x, int z) {
            int ax = Math.abs(x), az = Math.abs(z), mm = Math.max(ax, az), along = ax > az ? z : x;
            int b = Math.floorMod(along + 9, 22);
            Direction out = ax > az ? (x > 0 ? Direction.EAST : Direction.WEST) : (z > 0 ? Direction.SOUTH : Direction.NORTH);
            if (mm <= H0 + 4) for (int y = BASE - 8; y < BASE; y++) v.set(x, y, z, course(m, x, y, z));
            int top = -1;
            for (int t = 0; t < TIERS; t++) for (int h = 0; h < TH; h++) {
                int y = BASE + t * TH + h, hf = half(t) + skirt(h);
                boolean butt = b <= 3, panel = b >= 8 && b <= 17 && h >= 4 && h <= 10;
                if (butt) hf += 2;
                if (mm > hf) continue;
                int d = hf - mm; // how far in from the face
                if (panel && d <= 1) {
                    v.set(x, y, z, d == 0 ? null : Math.floorMod(along, 3) == 0 ? m.pale : h == 7 ? m.relief : m.worn);
                    continue;
                }
                BlockState s;
                if (d > 3) s = m.stoneA;
                else if (h == TH - 1) s = Math.floorMod(along, 4) < 2 ? m.relief : m.pale;
                else if (h == 3 || h == 7) s = m.pale;
                else if (butt && b % 3 == 0) s = m.pale;
                else s = course(m, x, y, z);
                v.set(x, y, z, s);
                top = Math.max(top, y);
            }
            // the top of the step you can walk on
            int t = tierAt(mm);
            if (t < 0) return;
            int ty = tierTop(t) - 1;
            if (top < ty) return;
            int d = half(t) - mm;
            v.set(x, ty, z, paving(m, x, z, along));
            if (d <= 1 && Math.floorMod(along, 6) < 3 && !(Math.abs(x) <= SW + 4 && z > 0)) {
                v.set(x, ty + 1, z, m.pale);
                if (Math.floorMod(along, 12) < 3) v.set(x, ty + 2, z, m.stoneSlab);
            } else if (d > 3) {
                double c = rnd(x, z, 21);
                double mo = moss.get(x / 9.0, z / 9.0);
                if (mo > 0.35) {
                    v.set(x, ty, z, m.moss);
                    if (c < 0.35) v.set(x, ty + 1, z, m.fern);
                    else if (c < 0.55) v.set(x, ty + 1, z, m.carpet);
                } else if (c < 0.06) v.set(x, ty + 1, z, m.drift);
            }
            // vines off a small lip at the top of the step, short enough to stop above the skirt
            if (d == 0 && t < TIERS - 1 && b > 3 && moss.get(along / 6.0, t * 3.0) > 0.2) {
                int p = out.getStepX(), q = out.getStepZ(), len = 2 + (int) (rnd(x, z, 22) * 4);
                v.set(x + p, ty, z + q, m.pale);
                for (int k = 1; k <= len; k++) v.set(x + p, ty - k, z + q, m.vines);
            }
        }

        private BlockState paving(M m, int x, int z, int along) {
            double f = rnd(x >> 1, z >> 1, 25);
            return f < 0.5 ? m.pale : f < 0.82 ? m.stoneA : f < 0.92 ? m.worn : m.hexstone;
        }
    }

    final class GreatStair extends Piece<M> {
        GreatStair() {
            box(0, 94, SW + 8, 34);
            mayOverlap = true;
            label = "great stair";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = Math.abs(x);
                if (z > 123 || z < 66) continue;
                int sy = z <= 80 ? DOOR_Y : stairY(z);
                if (a <= SW) {
                    for (int y = BASE - 6; y < sy; y++) v.set(x, y, z, course(m, x, y, z));
                    boolean landing = z <= 80 || z >= 122 || Math.floorMod(z, 14) == 10;
                    v.set(x, sy, z, landing ? m.pale : stair(a <= 5 ? m.paleStairs : m.stoneStairs, Direction.NORTH));
                    for (int y = sy + 1; y <= sy + 14; y++) v.set(x, y, z, null);
                    if (z > 80 && a > 9 && moss.get(x / 5.0, z / 5.0) > 0.4) v.set(x, sy + 1, z, m.carpet);
                } else if (a <= SW + 4 && z > 80) {
                    // the side walls of the flight, in big steps, and a way off onto each terrace it passes
                    boolean gap = Math.abs(z - 108) < 4 || Math.abs(z - 94) < 4;
                    int step = Math.floorMod(z, 6) < 3 ? 3 : 2;
                    int ty = gap ? (Math.abs(z - 108) < 4 ? tierTop(0) : tierTop(1)) - 1 : sy + step;
                    for (int y = BASE - 6; y <= ty; y++) v.set(x, y, z, a == SW + 4 ? m.pale : course(m, x, y, z));
                    if (gap) {
                        for (int y = ty + 1; y <= sy + 10; y++) v.set(x, y, z, null);
                    } else {
                        if (Math.floorMod(z, 12) < 2) v.set(x, ty + 1, z, m.brazier);
                        for (int y = ty + 1; y <= sy + 14; y++) if (v.get(x, y, z) != m.brazier) v.set(x, y, z, null);
                    }
                }
            }
        }
    }

    /** The green tiled door block at the head of the stair. */
    final class Gatehouse extends Piece<M> {
        Gatehouse() {
            box(0, 66, 13, 9);
            mayOverlap = true;
            label = "gatehouse";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = Math.abs(x);
                if (a > 12 || z < 60 || z > 72) continue;
                int top = DOOR_Y + 22 - Math.max(0, a - 6);
                for (int y = DOOR_Y + 1; y <= top; y++) {
                    int h = y - DOOR_Y;
                    boolean door = a <= 4 && h <= 11 - (a == 4 ? 1 : 0);
                    if (door) {
                        v.set(x, y, z, null);
                        continue;
                    }
                    boolean frame = a <= 6 && h <= 13;
                    v.set(x, y, z, frame ? (h == 13 ? m.relief : a == 6 ? m.deep : m.pale) : h % 6 == 0 ? m.relief : Math.floorMod(a + h, 5) == 0 ? m.tileDeep : m.tile);
                }
            }
        }
    }

    /** A stone head as tall as a tower, looking out over the lagoon, one either side of the stair. */
    final class Face extends Piece<M> {
        final int cx;

        Face(int cx) {
            this.cx = cx;
            box(cx, 104, 17, 12);
            mayOverlap = true;
            label = "face";
        }

        private int depth(int u, int v) {
            int a = Math.abs(u);
            if (v > 34) return a <= 8 ? -4 : -99;
            if (v > 30) return a <= 12 ? -2 : -99;
            if (v >= 26) return a <= 14 ? 0 : -99;
            if (a > 14) return -99;
            if (v >= 23) return a <= 12 ? 2 : 0;
            if (v >= 17 && a >= 3 && a <= 9) return v >= 20 && a >= 5 && a <= 7 ? -3 : -2;
            if (a <= 2 && v >= 10) return v <= 12 ? 4 : 3;
            if (a == 3 && v >= 9 && v <= 12) return 2;
            if (a >= 12 && v >= 11 && v <= 21) return -1;
            if (v >= 7 && v <= 16 && a <= 11) return 1;
            if (v == 6 && a <= 6) return -2;
            if ((v == 5 || v == 7) && a <= 7) return 2;
            if (v <= 4) return a <= 9 ? 1 : a <= 12 ? -1 : -3;
            return 0;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int u = x - cx;
                for (int k = 0; k <= 38; k++) {
                    int y = BASE + TH + k, d = depth(u, k), front = 110 + d;
                    if (d == -99 || z > front || z < 96) continue;
                    boolean skin = z == front;
                    v.set(x, y, z, !skin ? m.stoneA : d == -3 ? m.deep : k >= 26 && k <= 30 && Math.floorMod(u, 4) == 0 ? m.relief : course(m, x, y, z));
                    if (skin && k > 0 && moss.get(x / 6.0, y / 6.0) > 0.5) v.set(x, y, z, m.moss);
                }
            }
        }
    }

    /** Water down the front of a step, from its lip to whatever's below. */
    final class Falls extends Piece<M> {
        final int x;
        final int tier;

        Falls(int x, int tier) {
            this.x = x;
            this.tier = tier;
            box(x, half(tier) + 3, 3, 3);
            mayOverlap = true;
            label = "falls";
        }

        @Override
        public void draw(V v, M m) {
            int front = half(tier) + 3, top = tierTop(tier);
            int low = tier == 0 ? 54 : tierTop(tier - 1) - 1;
            for (int a = x - 1; a <= x + 1; a++) if (a >= v.x0 - 1 && a <= v.x0 + 16) {
                for (int y = top; y > low; y--) {
                    if (front >= v.z0 - 1 && front <= v.z0 + 16) v.set(a, y, front, m.fall);
                }
                for (int z = half(tier) - 2; z < front; z++) if (z >= v.z0 - 1 && z <= v.z0 + 16) {
                    v.set(a, top, z, null);
                    v.set(a, top - 1, z, m.tileDeep);
                }
            }
        }
    }

    // ---- inside ---------------------------------------------------------------------------------------------------------------

    /** The Lord's floor: a great round paved court at the foot of the trunk, the roof far overhead and open to the sun round the tree. */
    final class Bowl extends Room {
        Bowl() {
            box(0, 0, 50, 50);
            label = "root bowl";
        }

        @Override
        boolean has(int x, int z) {
            return x * x + z * z < BOWL_R * BOWL_R;
        }

        @Override
        int floor(int x, int z) {
            double r = Math.sqrt(x * x + z * z);
            return r >= 42 ? BOWL + 2 : r >= 40 ? BOWL + 1 : BOWL;
        }

        private int roof(double r) {
            if (r < WELL_R) return 176;
            return DOOR_Y + 24 - (int) (Math.pow(smooth(WELL_R, BOWL_R, r), 2) * 18);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double r = Math.sqrt(x * x + z * z), th = Math.atan2(z, x);
                if (r >= BOWL_R + 5) continue;
                if (r >= BOWL_R) {
                    wall(v, m, x, z, r, th);
                    continue;
                }
                int fl = floor(x, z), roof = roof(r);
                for (int y = fl + 1; y <= roof; y++) v.set(x, y, z, null);
                for (int y = BOWL - 6; y < fl; y++) v.set(x, y, z, m.stoneA);
                v.set(x, fl, z, top(m, x, z, r, th));
                pool(v, m, x, z);
                if (r > WELL_R) v.set(x, roof + 1, z, Math.floorMod((int) Math.round(th * 12 / Math.PI), 4) == 0 ? m.bark : course(m, x, roof + 1, z));
                furniture(v, m, x, z, r, th, fl);
            }
        }

        private void pool(V v, M m, int x, int z) {
            double d = Math.hypot(x + 28, z - 22) + moss.get(x / 8.0, z / 8.0) * 3;
            if (d > 10) return;
            v.set(x, BOWL, z, d > 9 ? m.pale : m.water);
            if (d <= 9) {
                v.set(x, BOWL - 1, z, m.water);
                v.set(x, BOWL - 2, z, m.tileDeep);
            }
            for (int y = BOWL + 1; y <= BOWL + 4; y++) v.set(x, y, z, null);
        }

        private BlockState top(M m, double x, double z, double r, double th) {
            for (double ring : new double[]{20, 30, 38}) if (Math.abs(r - ring) < 0.6) return rnd((int) x, (int) z, 31) < 0.1 ? m.worn : m.gold;
            int ring = (int) Math.floor(r / 2);
            int n = Math.max(8, ring * 6), sec = (int) Math.floor((th / (2 * Math.PI) + 0.5 + ring * 0.211) * n);
            double t = rnd(ring, sec, 32);
            if (moss.get(x / 7.0, z / 7.0) > 0.45) return t < 0.5 ? m.moss : m.worn;
            return t < 0.55 ? m.flag : t < 0.8 ? m.hexstone : t < 0.92 ? m.tile : m.stoneA;
        }

        private void furniture(V v, M m, int x, int z, double r, double th, int fl) {
            if (Math.hypot(x + 28, z - 22) < 12) return;
            int sp = (int) Math.round(th / (Math.PI / 6));
            double along = (th - sp * (Math.PI / 6)) * r;
            // short carved posts round the outer ring
            if (Math.abs(r - 35.5) < 1.6 && Math.abs(along) < 1.6 && Math.abs(z) + Math.abs(x) > 8) {
                int h = 3 + Math.floorMod(sp * 5, 3);
                for (int k = 1; k <= h; k++) v.set(x, fl + k, z, k == h ? m.stoneSlab : k % 2 == 0 ? m.relief : m.pale);
            }
            // lanterns hung from the vault on chains
            if (Math.abs(r - 31) < 0.6 && Math.abs(along) < 0.6 && Math.floorMod(sp, 2) == 0) {
                int roof = roof(r), low = BOWL + 18 + Math.floorMod(sp * 3, 8);
                for (int y = roof; y > low + 3; y--) v.set(x, y, z, m.link);
                v.set(x, low + 3, z, m.timber);
                for (int y = low; y <= low + 2; y++) v.set(x, y, z, m.window);
                v.set(x, low - 1, z, m.timber);
            }
        }

        private void wall(V v, M m, int x, int z, double r, double th) {
            int u = (int) Math.round(th * BOWL_R);
            boolean pier = Math.floorMod(u, 22) <= 3;
            for (int y = BOWL + 3; y < DOOR_Y; y++) {
                int h = y - BOWL - 3, row = h / 8, hh = h % 8;
                int d = (int) (r - BOWL_R);
                BlockState s;
                if (pier) s = d <= 3 ? (Math.floorMod(u, 22) == 0 || Math.floorMod(u, 22) == 3 ? m.pale : m.relief) : m.stoneA;
                else if (d == 0 && hh < 5 && hh > 0 && Math.floorMod(u + row * 3, 7) >= 2) s = null;
                else if (d == 1 && hh < 5 && hh > 0 && Math.floorMod(u + row * 3, 7) >= 2) s = hh == 1 && rnd(u, row, 41) < 0.12 ? m.brazier : m.worn;
                else if (hh == 7) s = m.pale;
                else s = course(m, x, y, z);
                v.set(x, y, z, s);
            }
        }
    }

    /** The round walk at door height, looking down into the Bowl between pillars. */
    final class Gallery extends Room {
        static final int R0 = 45, R1 = 55, H = 12;

        Gallery() {
            box(0, 0, R1 + 3, R1 + 3);
            label = "gallery";
            // the thorn curtain across every arch, until the Lord's door opens
            for (int x = -R0; x <= R0; x++) for (int z = -R0; z <= R0; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < BOWL_R - 1 || r >= R0 || Math.floorMod((int) Math.round(Math.atan2(z, x) * 50), 16) <= 3) continue;
                for (int y = DOOR_Y + 1; y <= DOOR_Y + 8; y++) grille.add(new Edit(x, y, z, m().thorn));
            }
        }

        @Override
        boolean has(int x, int z) {
            double r = Math.sqrt(x * x + z * z);
            return r >= R0 && r < R1;
        }

        @Override
        int floor(int x, int z) {
            return DOOR_Y;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < BOWL_R - 1 || r >= R1 + 2) continue;
                double th = Math.atan2(z, x);
                int u = (int) Math.round(th * 50);
                if (r < R0) {
                    // the arches you look through; a thorn curtain fills them until the Lord's door opens
                    boolean pier = Math.floorMod(u, 16) <= 3;
                    for (int y = DOOR_Y; y <= DOOR_Y + H; y++) {
                        int h = y - DOOR_Y;
                        if (pier || h == 0 || h >= 9) v.set(x, y, z, h == 0 ? m.pale : h >= 9 ? (Math.floorMod(u, 4) < 2 ? m.relief : m.pale) : course(m, x, y, z));
                        else v.set(x, y, z, null);
                    }
                } else if (r < R1) {
                    v.set(x, DOOR_Y, z, Math.floorMod(u, 8) < 4 ? m.flag : m.hexstone);
                    for (int y = DOOR_Y + 1; y <= DOOR_Y + H; y++) v.set(x, y, z, null);
                    v.set(x, DOOR_Y + H + 1, z, Math.floorMod(u, 10) < 2 ? m.pale : course(m, x, DOOR_Y + H + 1, z));
                    if (rnd(x, z, 51) < 0.03) v.set(x, DOOR_Y + 1, z, m.drift);
                } else for (int y = DOOR_Y; y <= DOOR_Y + H + 1; y++) {
                    int h = y - DOOR_Y;
                    boolean lit = h == 5 && Math.floorMod(u, 14) == 0;
                    v.set(x, y, z, lit ? m.window : h == 8 ? m.relief : course(m, x, y, z));
                }
            }
        }
    }

    /** A straight passage. */
    final class Arm extends Room {
        final boolean alongX;
        final int a0, a1, cross, hw, fy, tall;

        Arm(boolean alongX, int a0, int a1, int cross, int hw, int fy, int tall) {
            this.alongX = alongX;
            this.a0 = a0;
            this.a1 = a1;
            this.cross = cross;
            this.hw = hw;
            this.fy = fy;
            this.tall = tall;
            if (alongX) {
                x0 = a0;
                x1 = a1;
                z0 = cross - hw - 2;
                z1 = cross + hw + 2;
            } else {
                z0 = a0;
                z1 = a1;
                x0 = cross - hw - 2;
                x1 = cross + hw + 2;
            }
            label = "arm";
        }

        @Override
        boolean has(int x, int z) {
            int a = alongX ? x : z, c = (alongX ? z : x) - cross;
            return a >= a0 && a <= a1 && Math.abs(c) <= hw;
        }

        @Override
        int floor(int x, int z) {
            return fy;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = alongX ? x : z, c = Math.abs((alongX ? z : x) - cross);
                if (c > hw + 1) continue;
                if (c == hw + 1) {
                    for (int y = fy; y <= fy + tall + 1; y++) v.set(x, y, z, Math.floorMod(a, 6) == 0 ? m.pale : course(m, x, y, z));
                    continue;
                }
                v.set(x, fy, z, Math.floorMod(a + c, 2) == 0 ? m.flag : m.hexstone);
                for (int y = fy + 1; y <= fy + tall; y++) v.set(x, y, z, null);
                v.set(x, fy + tall + 1, z, Math.floorMod(a, 6) == 0 ? m.relief : m.stoneA);
            }
        }
    }

    /** The second Warden's room: roots have come down through the roof and taken the middle of it. */
    final class RootHall extends Room {
        static final int X0 = -94, X1 = -60, Z = 22, FY = BASE + TH, TALL = 13;

        RootHall() {
            x0 = X0 - 2;
            x1 = X1 + 2;
            z0 = -Z - 2;
            z1 = Z + 2;
            label = "root hall";
        }

        @Override
        boolean has(int x, int z) {
            return x >= X0 && x <= X1 && Math.abs(z) <= Z;
        }

        @Override
        int floor(int x, int z) {
            return FY;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                boolean wall = x <= X0 - 1 || x >= X1 + 1 || Math.abs(z) >= Z + 1;
                if (wall) {
                    for (int y = FY; y <= FY + TALL + 1; y++) v.set(x, y, z, y == FY + 6 ? m.relief : course(m, x, y, z));
                    continue;
                }
                v.set(x, FY, z, moss.get(x / 6.0, z / 6.0) > 0.3 ? m.moss : Math.floorMod(x + z, 2) == 0 ? m.flag : m.hexstone);
                for (int y = FY + 1; y <= FY + TALL; y++) v.set(x, y, z, null);
                v.set(x, FY + TALL + 1, z, course(m, x, FY + TALL + 1, z));
                boolean col = Math.floorMod(x + 77, 14) <= 2 && Math.floorMod(z + 22, 16) <= 2 && (x < -82 || x > -72 || Math.abs(z) > 8);
                if (col) for (int y = FY + 1; y <= FY + TALL; y++) v.set(x, y, z, (y - FY) % 4 == 0 ? m.relief : m.pale);
                if (Math.floorMod(x + 77, 14) == 7 && Math.floorMod(z + 22, 11) == 5) {
                    for (int y = FY + TALL; y > FY + 7; y--) v.set(x, y, z, m.link);
                    v.set(x, FY + 7, z, m.window);
                    v.set(x, FY + 6, z, m.timber);
                }
            }
        }
    }

    private void stairs() {
        pieces.add(new TierStair(0, 34, 0));
        pieces.add(new TierStair(2, 34, 0));
        pieces.add(new TierStair(0, -34, 1));
        pieces.add(new TierStair(2, -34, 1));
        pieces.add(new TierStair(0, -44, 2));
        pieces.add(new TierStair(1, 30, 3));
        pieces.add(new TierStair(2, 12, 4));
    }

    /** A flight let into the face of a step, so the terraces stay whole. face: 0 east, 1 north, 2 west, 3 south. */
    final class TierStair extends Piece<M> {
        final int face, along, tier;

        TierStair(int face, int along, int tier) {
            this.face = face;
            this.along = along;
            this.tier = tier;
            int h = half(tier + 1);
            int cx = face == 0 ? h - 7 : face == 2 ? -h + 7 : along, cz = face == 1 ? -h + 7 : face == 3 ? h - 7 : along;
            box(cx, cz, face % 2 == 0 ? 10 : 4, face % 2 == 0 ? 4 : 10);
            mayOverlap = true;
            label = "tier stair";
        }

        @Override
        public void draw(V v, M m) {
            int h = half(tier + 1) + 2, y0 = tierTop(tier) - 1;
            Direction in = face == 0 ? Direction.WEST : face == 1 ? Direction.SOUTH : face == 2 ? Direction.EAST : Direction.NORTH;
            for (int k = -1; k <= 15; k++) for (int w = -2; w <= 2; w++) {
                int m1 = h - k;
                int x = face == 0 ? m1 : face == 2 ? -m1 : along + w, z = face == 1 ? -m1 : face == 3 ? m1 : along + w;
                if (!v.in(x, y0, z)) continue;
                int step = Math.max(0, Math.min(14, k));
                int fl = y0 + step;
                for (int y = y0; y < fl; y++) v.set(x, y, z, m.stoneA);
                v.set(x, fl, z, k >= 14 || k < 0 ? m.flag : stair(m.stoneStairs, in));
                for (int y = fl + 1; y <= fl + 5; y++) v.set(x, y, z, null);
            }
        }
    }

    private void arcStairs() {
        pieces.add(new ArcStair(1));
        pieces.add(new ArcStair(-1));
    }

    /** The way down from the gallery to the Lord's floor, let into the thickness of the Bowl's wall on both sides. */
    final class ArcStair extends Piece<M> {
        static final double R = 49, PHI0 = -0.05, DPHI = (DOOR_Y - BOWL - 2) / R;
        final int side;

        ArcStair(int side) {
            this.side = side;
            box(side * 40, 24, 16, 30);
            mayOverlap = true;
            label = "arc stair";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < R - 3.5 || r > R + 3.5 || x * side <= 0) continue;
                double phi = Math.atan2(z, Math.abs(x));
                if (phi < PHI0 - 0.02 || phi > PHI0 + DPHI + 0.14) continue;
                int fl = Math.max(BOWL + 2, DOOR_Y - (int) Math.round((phi - PHI0) * R));
                for (int y = BOWL + 2; y < fl; y++) v.set(x, y, z, course(m, x, y, z));
                boolean flat = fl == DOOR_Y || fl == BOWL + 2;
                v.set(x, fl, z, flat ? m.flag : stair(m.stoneStairs, Math.abs(z) > Math.abs(x) ? Direction.NORTH : side > 0 ? Direction.NORTH : Direction.NORTH));
                for (int y = fl + 1; y <= fl + 6; y++) v.set(x, y, z, null);
                if (phi > PHI0 + DPHI + 0.02) for (double in = r; in > BOWL_R - 1; in -= 0.5) {
                    int ix = (int) Math.round(in * Math.cos(phi)) * side, iz = (int) Math.round(in * Math.sin(phi));
                    for (int y = BOWL + 3; y <= BOWL + 8; y++) v.set(ix, y, iz, null);
                }
            }
        }
    }

    // ---- outside ----------------------------------------------------------------------------------------------------------

    private void causeway() {
        pieces.add(new Causeway());
    }

    /** The old road across the lagoon: broken in two places, with stones to step over. */
    final class Causeway extends Piece<M> {
        Causeway() {
            box(0, 170, 14, 46);
            mayOverlap = true;
            label = "causeway";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = Math.abs(x);
                if (z <= 123 || z > 214) continue;
                if (z >= 196 && a <= 12) {
                    for (int y = 52; y <= WATER + 3; y++) v.set(x, y, z, y == WATER + 3 ? (a >= 11 || z == 196 ? m.stoneA : Math.floorMod(x + z, 3) == 0 ? m.pale : m.flag) : course(m, x, y, z));
                    if (a == 12 && Math.floorMod(z, 3) == 0) v.set(x, WATER + 4, z, m.pale);
                    continue;
                }
                if (a > 7) continue;
                boolean gap = Math.abs(z - 170) < 8;
                if (gap) {
                    // the road has gone here: a line of tumbled stones, wet underfoot
                    if (a <= 1 + Math.floorMod(z * 7, 3) / 2) for (int y = 52; y <= WATER + 2 + Math.floorMod(z + x, 2); y++) v.set(x, y, z, y >= WATER + 2 ? m.worn : course(m, x, y, z));
                    continue;
                }
                if (a > 4) {
                    if (a == 5) for (int y = 52; y <= WATER + 4; y++) v.set(x, y, z, Math.floorMod(z, 6) < 3 && y == WATER + 4 ? m.pale : y == WATER + 4 ? null : course(m, x, y, z));
                    continue;
                }
                for (int y = 52; y <= WATER + 3; y++) v.set(x, y, z, y == WATER + 3 ? (Math.floorMod(x + z, 3) == 0 ? m.hexstone : m.flag) : course(m, x, y, z));
                for (int y = WATER + 4; y <= WATER + 7; y++) v.set(x, y, z, null);
                if (rnd(x, z, 61) < 0.08) v.set(x, WATER + 4, z, m.carpet);
            }
        }
    }

    private void islets() {
        pieces.add(new Islet(-96, 150, 22, 3));
        pieces.add(new Islet(100, 142, 17, 3));
        pieces.add(new Islet(-150, 70, 15, 2));
        pieces.add(new Islet(170, 0, 28, 2));
    }

    /** A ruin standing in the water, the same stepped building as the temple on a small scale. */
    final class Islet extends Piece<M> {
        final int cx, cz, hw, steps;

        Islet(int cx, int cz, int hw, int steps) {
            this.cx = cx;
            this.cz = cz;
            this.hw = hw;
            this.steps = steps;
            box(cx, cz, hw + 2, hw + 2);
            mayOverlap = true;
            label = "islet";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int mm = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                if (mm > hw) continue;
                int top = WATER + 1 + Math.min(8 * steps / 3 + 2, (hw - mm) / 2);
                for (int y = 50; y < top; y++) v.set(x, y, z, course(m, x, y, z));
                v.set(x, top, z, moss.get(x / 8.0, z / 8.0) > 0.2 ? m.moss : (Math.floorMod(x + z, 2) == 0 ? m.pale : m.stoneA));
                double c = rnd(x, z, 71);
                boolean col = Math.floorMod(x - cx + 12, 8) == 4 && Math.floorMod(z - cz + 12, 8) == 4 && mm < hw - 6;
                if (col) {
                    int h = 3 + Math.floorMod(x * 3 + z, 6);
                    for (int k = 1; k <= h; k++) v.set(x, top + k, z, k == h ? m.stoneSlab : Math.floorMod(k, 2) == 0 ? m.relief : m.pale);
                } else if (c < 0.2) v.set(x, top + 1, z, m.fern);
                else if (c < 0.3) v.set(x, top + 1, z, m.carpet);
            }
        }
    }

    // ---- the tree -----------------------------------------------------------------------------------------------------------

    /** A round bough or root drawn down a smooth line, so a dozen of them twist together into a trunk. */
    final class Tube extends Piece<M> {
        final double[] px, py, pz, pr;

        Tube(double[][] keys, int seg, String what) {
            int n = keys.length;
            px = new double[(n - 1) * seg + 1];
            py = new double[px.length];
            pz = new double[px.length];
            pr = new double[px.length];
            double lx = 1e9, hx = -1e9, lz = 1e9, hz = -1e9;
            for (int i = 0; i < n - 1; i++) for (int s = 0; s <= seg; s++) {
                double t = s / (double) seg, t2 = t * t, t3 = t2 * t;
                double[] a = keys[Math.max(0, i - 1)], b = keys[i], c = keys[i + 1], d = keys[Math.min(n - 1, i + 2)];
                int k = i * seg + s;
                px[k] = cr(a[0], b[0], c[0], d[0], t, t2, t3);
                py[k] = cr(a[1], b[1], c[1], d[1], t, t2, t3);
                pz[k] = cr(a[2], b[2], c[2], d[2], t, t2, t3);
                pr[k] = lerp(b[3], c[3], t);
                lx = Math.min(lx, px[k] - pr[k]);
                hx = Math.max(hx, px[k] + pr[k]);
                lz = Math.min(lz, pz[k] - pr[k]);
                hz = Math.max(hz, pz[k] + pr[k]);
            }
            x0 = (int) lx - 1;
            x1 = (int) hx + 1;
            z0 = (int) lz - 1;
            z1 = (int) hz + 1;
            mayOverlap = true;
            label = what;
        }

        private double cr(double a, double b, double c, double d, double t, double t2, double t3) {
            return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (3 * b - a - 3 * c + d) * t3);
        }

        @Override
        public void draw(V v, M m) {
            int vx0 = v.x0 - 1, vx1 = v.x0 + 16, vz0 = v.z0 - 1, vz1 = v.z0 + 16;
            for (int i = 0; i < px.length; i++) {
                double r = pr[i];
                if (px[i] + r < vx0 || px[i] - r > vx1 || pz[i] + r < vz0 || pz[i] - r > vz1) continue;
                int r0 = (int) Math.ceil(r);
                int cx = (int) Math.floor(px[i]), cy = (int) Math.floor(py[i]), cz = (int) Math.floor(pz[i]);
                for (int x = Math.max(cx - r0, vx0); x <= Math.min(cx + r0, vx1); x++) for (int z = Math.max(cz - r0, vz0); z <= Math.min(cz + r0, vz1); z++)
                    for (int y = cy - r0; y <= cy + r0; y++) {
                        double dx = x + 0.5 - px[i], dy = y + 0.5 - py[i], dz = z + 0.5 - pz[i];
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 > r * r) continue;
                        boolean mossy = dy > r * 0.3 && d2 > (r - 1.3) * (r - 1.3) && moss.get(x / 5.0, (y + z) / 5.0) > 0.0;
                        v.set(x, y, z, mossy ? m.barkMoss : m.bark);
                    }
            }
        }
    }

    final class Crown extends Piece<M> {
        final List<double[]> lobes = new ArrayList<>();

        Crown() {
            box(0, 0, 150, 150);
            mayOverlap = true;
            label = "crown";
        }

        void lobe(double x, double y, double z, double r) {
            lobes.add(new double[]{x, y, z, r});
        }

        @Override
        public void draw(V v, M m) {
            int vx0 = v.x0 - 1, vx1 = v.x0 + 16, vz0 = v.z0 - 1, vz1 = v.z0 + 16;
            for (double[] l : lobes) {
                double r = l[3], ry = r * 0.62;
                if (l[0] + r < vx0 || l[0] - r > vx1 || l[2] + r < vz0 || l[2] - r > vz1) continue;
                for (int x = Math.max((int) (l[0] - r), vx0); x <= Math.min((int) (l[0] + r), vx1); x++) for (int z = Math.max((int) (l[2] - r), vz0); z <= Math.min((int) (l[2] + r), vz1); z++)
                    for (int y = (int) (l[1] - ry); y <= (int) (l[1] + ry); y++) {
                        double dx = (x - l[0]) / r, dy = (y - l[1]) / ry, dz = (z - l[2]) / r;
                        double d = dx * dx + dy * dy * (dy < 0 ? 1.5 : 1) + dz * dz;
                        double wob = n3.get(x / 7.0 + y / 9.0, z / 7.0 - y / 9.0) * 0.35;
                        if (d > 1 + wob) continue;
                        if (y < l[1] - ry * 0.4 && rnd(x * 7 + y, z, 81) < 0.2) continue;
                        if (v.get(x, y, z) == null) v.set(x, y, z, m.leaf);
                    }
            }
        }
    }

    private void tree() {
        // the trunk is a dozen strands twisted together, and each runs on down as a root
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n + jit(0.1);
            List<double[]> k = new ArrayList<>();
            for (int s = 0; s <= 9; s++) {
                double y = 244 - s * 10.0, a = a0 + s * 0.62, rho = 9 + s * 0.8;
                k.add(new double[]{Math.cos(a) * rho, y, Math.sin(a) * rho, 5.0 + s * 0.14});
            }
            double a1 = a0 + 9 * 0.62;
            double sa = Math.sin(a1), ca = Math.cos(a1);
            if (sa > 0.35) southRoot(k, i, a1);
            else if (i % 3 == 0) sideRoot(k, i, a1);
            else insideRoot(k, i, a1);
            pieces.add(new Tube(k.toArray(new double[0][]), 8, "root " + i));
        }
        Crown crown = new Crown();
        pieces.add(crown);
        for (int i = 0; i < 13; i++) {
            double a = i * 2 * Math.PI / 13 + jit(0.3), reach = 96 + jit(26), up = 248 + jit(18);
            double ex = Math.cos(a) * reach, ez = Math.sin(a) * reach;
            pieces.add(new Tube(new double[][]{{Math.cos(a) * 8, 232, Math.sin(a) * 8, 7}, {Math.cos(a) * 30, 244, Math.sin(a) * 30, 5}, {ex * 0.7, up - 4, ez * 0.7, 3.4}, {ex, up, ez, 2}}, 10, "bough"));
            crown.lobe(ex, up + 8, ez, 30 + jit(9));
            crown.lobe(ex * 0.72, up + 18, ez * 0.72, 32);
            crown.lobe(ex * 0.42, up + 26, ez * 0.42, 30);
            double b = a + 0.6;
            pieces.add(new Tube(new double[][]{{ex * 0.6, up - 6, ez * 0.6, 2.8}, {ex * 0.8 + Math.cos(b) * 22, up, ez * 0.8 + Math.sin(b) * 22, 1.8}}, 12, "bough"));
            crown.lobe(ex * 0.8 + Math.cos(b) * 26, up + 5, ez * 0.8 + Math.sin(b) * 26, 24);
        }
        crown.lobe(0, 286, 0, 46);
    }

    private double[] onSteps(double x, double z, double r) {
        int mm = (int) Math.max(Math.abs(x), Math.abs(z));
        int t = tierAt(mm);
        return new double[]{x, (t < 0 ? WATER : tierTop(t)) + r * 0.4, z, r};
    }

    /** Down the front of the temple, past both sides of the door, and away over the steps. */
    private void southRoot(List<double[]> k, int i, double a1) {
        double side = Math.cos(a1) >= 0 ? 1 : -1, off = 22 + (i % 4) * 9;
        k.add(new double[]{side * 10, 150, 22, 4.6});
        for (int s = 0; s < 6; s++) {
            double z = 34 + s * 16, x = side * (10 + (off - 10) * s / 5.0 + 5 * Math.sin(s * 1.7 + i));
            k.add(onSteps(x, z, 3.8 - s * 0.32));
            k.add(onSteps(x + side * 1.5, z + 9, 3.6 - s * 0.32));
        }
    }

    private void sideRoot(List<double[]> k, int i, double a1) {
        double ca = Math.cos(a1), sa = Math.sin(a1);
        double mx = Math.abs(ca) > Math.abs(sa) ? Math.signum(ca) : 0, mz = Math.abs(ca) > Math.abs(sa) ? 0 : Math.signum(sa);
        double px = -mz, pz = mx;
        k.add(new double[]{mx * 18 + px * 8, 152, mz * 18 + pz * 8, 5.8});
        for (int s = 1; s <= 4; s++) k.add(onSteps(mx * (18 + s * 20) + px * (8 + s * 6), mz * (18 + s * 20) + pz * (8 + s * 6), 5.4 - s * 0.7));
    }

    /** Down inside the building to the Lord's floor, arching out over it and back to the ground. */
    private void insideRoot(List<double[]> k, int i, double a1) {
        double ca = Math.cos(a1), sa = Math.sin(a1);
        double tip = 26 + (i % 4) * 4;
        k.add(new double[]{ca * 16, 140, sa * 16, 6.4});
        k.add(new double[]{ca * 18, 112, sa * 18, 6.0});
        k.add(new double[]{ca * 24, 96, sa * 24, 5.2});
        k.add(new double[]{ca * tip * 0.95, 88, sa * tip * 0.95, 4.4});
        k.add(new double[]{ca * (tip + 8), 79, sa * (tip + 8), 3.2});
        k.add(new double[]{ca * (tip + 11), BOWL + 2, sa * (tip + 11), 2.4});
    }

    // ---- the rule --------------------------------------------------------------------------------------------------------------

    private void curtains() {
        curtain(true, 60, 0, 4, DOOR_Y);
        curtain(true, -60, 0, 4, DOOR_Y);
        curtain(false, 0, 60, 4, DOOR_Y);
        curtain(false, 0, -60, 4, DOOR_Y);
        curtain(true, -96, 0, 3, BASE + TH);
    }

    private void curtain(boolean alongX, int x, int z, int hw, int fy) {
        Curtain c = new Curtain(alongX, x, z, hw, fy);
        curtains.add(c);
        pieces.add(c);
    }

    /** A wall of roots across a passage. It draws back for whoever comes up to it on the move, and closes again once they've stopped or gone. */
    final class Curtain extends Piece<M> {
        final boolean alongX;
        final int x, z, hw, fy;

        Curtain(boolean alongX, int x, int z, int hw, int fy) {
            this.alongX = alongX;
            this.x = x;
            this.z = z;
            this.hw = hw;
            this.fy = fy;
            box(x, z, alongX ? 1 : hw + 1, alongX ? hw + 1 : 1);
            mayOverlap = true;
            label = "root curtain";
        }

        void edits(List<Edit> out, boolean open) {
            BlockState s = open ? Blocks.AIR.defaultBlockState() : m().thorn;
            for (int t = -hw; t <= hw; t++) for (int y = fy + 1; y <= fy + 8; y++) out.add(new Edit(alongX ? x : x + t, y, alongX ? z + t : z, s));
        }

        @Override
        public void draw(V v, M m) {
            for (int t = -hw - 1; t <= hw + 1; t++) {
                int cx = alongX ? x : x + t, cz = alongX ? z + t : z;
                v.set(cx, fy + 9, cz, m.bark);
                if (Math.abs(t) == hw + 1) for (int y = fy + 1; y <= fy + 9; y++) v.set(cx, y, cz, m.bark);
            }
        }
    }

    // ---- who's here, and what's worth finding -------------------------------------------------------------------------------

    private void village() {
        int[][] huts = {{-156, 212, 6}, {-180, 192, 7}, {-132, 188, 5}, {-200, 226, 6}, {-146, 244, 6}, {-114, 226, 5}, {-186, 252, 5}};
        for (int[] h : huts) pieces.add(new Hut(h[0], h[1], h[2], Math.atan2(214 - h[1], -160 - h[0]), seed + h[0], this));
        for (int[] s : new int[][]{{-160, 210}, {-152, 222}, {-168, 228}, {-142, 214}}) pieces.add(new Stall(s[0], s[1], seed + s[0], this));
        for (int i = 0; i < 220; i++) {
            double a = i * 2.399963 + jit(0.5), d = 238 + jit(60) + (i % 12) * 7;
            int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
            if (Math.abs(x) < 16 && z > 0) continue;
            if (Math.hypot(x + 160, z - 214) < 62 || terrain(x, z) <= WATER + 1 || terrain(x, z) > 92) continue;
            place(new Tree(x, z, 11 + (i * 7) % 15, seed + i, this));
        }
    }

    private void placeSites() {
        site(SiteKind.MOUTH, 0, WATER + 4, 206, 12, "");
        site(SiteKind.PORTAL, 0, WATER + 4, 212, 2, "");
        pieces.add(new MouthRing(0, WATER + 3, 209, this));
        site(SiteKind.LORD, 0, BOWL + 1, 26, 40, "");
        site(SiteKind.WARDEN, 170, WATER + 12, 0, 16, "water");
        site(SiteKind.WARDEN, -78, BASE + TH + 1, 0, 14, "roots");
        site(SiteKind.WARDEN, 0, tierTop(5), -27, 8, "crown");
        site(SiteKind.VILLAGE, -160, ground(-160, 214) + 1, 214, 46, "");
        for (int[] d : new int[][]{{88, 104}, {-88, 104}, {104, 40}, {-104, 40}, {60, -90}, {-60, -90}, {150, 150}, {-180, 120}}) site(SiteKind.DEN, d[0], ground(d[0], d[1]) + 1, d[1], 10, "");
        waystone(9, 198, "the old road");
        waystone(-68, 72, "the third step");
        waystone(90, 10, "the east terrace");
        int[][] low = {{90, 96}, {-100, 88}, {104, -52}, {-104, -40}, {-60, 104}, {60, 72}, {-58, 72}, {60, -80}, {-40, -96}, {112, 150}, {-96, 158}, {-150, 72}, {176, 8}};
        int i = 0;
        for (int[] f : low) {
            int gy = ground(f[0], f[1]);
            pieces.add(new Find(f[0], gy, f[1], seed + i++));
            site(SiteKind.FIND, f[0], gy + 1, f[1], 2, "");
        }
        int[][] high = {{0, tierTop(3) - 1, -56}, {46, tierTop(4) - 1, 0}, {-46, tierTop(4) - 1, 0}, {28, tierTop(5) - 1, 0}, {42, DOOR_Y, 26}};
        for (int[] f : high) {
            pieces.add(new Find(f[0], f[1], f[2], seed + i++));
            site(SiteKind.FIND, f[0], f[1] + 1, f[2], 2, "high");
        }
    }
}
