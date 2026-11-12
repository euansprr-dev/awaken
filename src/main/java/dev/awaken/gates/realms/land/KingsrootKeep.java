package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Build;
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
import static dev.awaken.gates.realms.kit.Build.under;
import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.rnd;
import static dev.awaken.gates.realms.kit.N.smooth;

/**
 * Kingsroot Keep (SPEC 50.8): a castle in the rain that the jungle has eaten. Two rings of wall and a great keep climb a hill inside a river gorge, every
 * gate is shut and has been for a very long time, and roots as thick as houses have poured down from the top of the keep and broken the walls on their way.
 * The rule of the place: you get in the way the roots did. Where they've broken a wall you can climb it. The Lord waits in the great hall, which has no roof left.
 */
public final class KingsrootKeep extends KitLand<KingsrootKeep.M> {
    static final int WATER = 62, BED = 54, APRON = 100, OUTER = 104, INNER = 150, F = 150, SETBACK = 190, CROWN = 236, HALF = 440;
    /** How thick a curtain wall is. */
    static final int CW = 11;
    static final int FOOT1 = APRON - 1, WALK1 = 29, FOOT2 = OUTER - 1, WALK2 = 67;
    static final int KX = 54, KZ0 = -68, KZ1 = 60, HALL_X = 40, HALL_Z0 = -54, HALL_Z1 = 46;
    static final double RIM = 384;
    static final int[] PIERS = {32, 10, -12, -34};

    public static final class M extends Mats {
        final BlockState bark = b("keep_bark"), barkMoss = b("keep_bark_moss"), leaf = b("keep_leaf"), leafLit = b("keep_leaf_lit"), leafDeep = b("keep_leaf_deep"), thorn = b("keep_thorn"), relief = b("keep_relief"),
                vines = b("keep_vines"), climb = b("keep_climb_root"), cloth = b("keep_banner"), fall = b("keep_fall"),
                fern = b("temple_fern"), drift = b("temple_moss_drift"), carpet = b("temple_moss"), brazier = b("temple_brazier"),
                water = Blocks.WATER.defaultBlockState(), grass = Blocks.SHORT_GRASS.defaultBlockState(), airLight = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 11);

        M() {
            super("keep", new Accents(b("keep_relief"), b("keep_relief"), b("keep_window"), b("keep_vines"), b("temple_brazier"), b("temple_moss_drift"), b("temple_idol"),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(), b("keep_leaf")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (KingsrootKeep.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    @Override
    protected M mats() {
        return m();
    }

    record Breach(int cx, int cz, boolean alongX, int hw, int depth) {}

    /** The line of a wall: straight runs that meet at right angles, so the castle's plan is never one neat square. */
    static final class Ring {
        final int[][] v;
        final boolean[] along;
        final Direction[] in;

        Ring(int[][] v) {
            this.v = v;
            along = new boolean[v.length];
            in = new Direction[v.length];
            for (int i = 0; i < v.length; i++) {
                int[] a = v[i], b = v[(i + 1) % v.length];
                along[i] = a[1] == b[1];
                double mx = (a[0] + b[0]) / 2.0 + 0.5, mz = (a[1] + b[1]) / 2.0 + 0.5;
                if (along[i]) in[i] = inside(mx, a[1] + 1.5) ? Direction.SOUTH : Direction.NORTH;
                else in[i] = inside(a[0] + 1.5, mz) ? Direction.EAST : Direction.WEST;
            }
        }

        boolean inside(double x, double z) {
            boolean in = false;
            for (int i = 0, j = v.length - 1; i < v.length; j = i++) {
                if ((v[i][1] > z) != (v[j][1] > z) && x < (v[j][0] - v[i][0]) * (z - v[i][1]) / (double) (v[j][1] - v[i][1]) + v[i][0]) in = !in;
            }
            return in;
        }

        private double seg2(int i, double x, double z) {
            int[] a = v[i], b = v[(i + 1) % v.length];
            double cx = Math.max(Math.min(a[0], b[0]), Math.min(Math.max(a[0], b[0]), x)), cz = Math.max(Math.min(a[1], b[1]), Math.min(Math.max(a[1], b[1]), z));
            return (x - cx) * (x - cx) + (z - cz) * (z - cz);
        }

        /** How far from the wall's outer line, negative inside it. */
        double dist(double x, double z) {
            double best = 1e18;
            for (int i = 0; i < v.length; i++) best = Math.min(best, seg2(i, x, z));
            return inside(x, z) ? -Math.sqrt(best) : Math.sqrt(best);
        }

        int nearest(double x, double z) {
            double best = 1e18;
            int at = 0;
            for (int i = 0; i < v.length; i++) {
                double d = seg2(i, x, z);
                if (d < best) {
                    best = d;
                    at = i;
                }
            }
            return at;
        }
    }

    final SimplexNoise n1, n2, n3, wet;
    final Ring ring1, ring2;
    private final List<int[]> stacks = new ArrayList<>();
    private final List<Edit> grille = new ArrayList<>();
    private final List<int[]> heapsA = new ArrayList<>(), heapsB = new ArrayList<>(), heaps = new ArrayList<>();

    public KingsrootKeep(Ctx ctx) {
        super(ctx, 0x4B454550L);
        n1 = N.noise(seed + 21);
        n2 = N.noise(seed + 22);
        n3 = N.noise(seed + 23);
        wet = N.noise(seed + 24);
        ring1 = new Ring(new int[][]{{-80, 168, 0}, {80, 168, 0}, {80, 150, 1}, {140, 150, 1}, {140, 30, 0}, {158, 30, 1}, {158, -90, 1}, {110, -90, 1}, {110, -150, 1}, {-90, -150, 1}, {-90, -128, 0}, {-156, -128, 1}, {-156, -20, 1}, {-140, -20, 0}, {-140, 150, 1}, {-80, 150, 1}});
        ring2 = new Ring(new int[][]{{-48, 100, 0}, {48, 100, 0}, {48, 84, 1}, {92, 84, 1}, {92, -30, 0}, {84, -30, 1}, {84, -92, 1}, {-70, -92, 1}, {-70, -76, 0}, {-92, -76, 1}, {-92, 84, 1}, {-48, 84, 1}});
        for (int i = 0; i < 18; i++) {
            double a = i * 2.399963 + jit(0.3), d = 300 + (i % 4) * 22 + jit(12);
            int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
            if (Math.abs(x) < 70 && z > 200) continue;
            stacks.add(new int[]{x, z, 24 + (i * 7) % 18, 70 + (i * 37) % 80});
        }
        pieces.add(new Walls(ring1, FOOT1, WALK1, OUTER - FOOT1, List.of(new Breach(-100, 150, true, 11, 10), new Breach(104, 150, true, 11, 10))));
        pieces.add(new Walls(ring2, FOOT2, WALK2, INNER - FOOT2, List.of(new Breach(-70, 84, true, 13, 14), new Breach(92, 30, false, 13, 14))));
        towers(ring1, FOOT1, WALK1, 10);
        towers(ring2, FOOT2, WALK2, 9);
        gate(160, 14, 24, FOOT1, WALK1, 178, 18, 10, WALK1 + 6, 0);
        gate(94, 12, 22, FOOT2, WALK2, 110, 16, 9, WALK2 + 8, 1);
        wallStairs();
        keep();
        room(new Hall());
        bridge();
        forecourt();
        roots();
        groundCover();
        village();
        placeSites();
        clearSites();
        // plan version 2 (SPEC §50.9): a designed way up to the keep, camps and stones on dry ground clear of its walls
        if (ctx.version() >= 2) {
            M m = m();
            // a camp may stand under the old trees and among the apron's poles, plinths and broken arches
            designWay((x, z) -> ground(x, z) > WATER + 1 && !inKeep(x, z) && (x < -164 || x > 166 || z < -158 || z > 176),
                    new dev.awaken.gates.realms.kit.WayPlanner.Dress(m.stoneA, m.pale, m.band),
                    java.util.Set.of("crown", "bough", "Tree", "banner pole", "fire plinth", "wall stub", "ruin arch"));
        }
    }

    private void room(Piece<M> p) {
        p.mayOverlap = true;
        pieces.add(p);
    }

    // ---- the land ----------------------------------------------------------------------------------------------------------

    /** The ground with nothing built on it: two wards on the hill, the apron under the walls, the gorge, and the jungle on the far bank. */
    int terrain(int x, int z) {
        double px = x + 0.5, pz = z + 0.5, o1 = ring1.dist(px, pz);
        if (o1 <= 0) {
            if (ring2.inside(px, pz)) return INNER + (int) Math.round(n3.get(x / 40.0, z / 40.0) * 0.9);
            return OUTER + (int) Math.round(n3.get(x / 46.0, z / 46.0) * 1.6);
        }
        double o = o1 + n1.get(x / 90.0, z / 90.0) * 12 + n2.get(x / 26.0, z / 26.0) * 4;
        double h;
        if (o < 36) h = APRON + n3.get(x / 30.0, z / 30.0) * 1.1;
        else if (o < 56) h = lerp(APRON, BED, smooth(36, 56, o));
        else if (o < 88) h = BED + n2.get(x / 18.0, z / 18.0) * 2;
        else if (o < 112) h = lerp(BED, APRON, smooth(88, 112, o));
        else h = APRON + (n3.get(x / 70.0, z / 70.0) + 0.5) * 7 * smooth(112, 154, o);
        double f = smooth(0, 14, Math.max(Math.abs(x) - 32, Math.max(258 - z, z - 334)));
        h = lerp(APRON, h, f);
        double r = Math.hypot(x, z);
        h += Math.pow(smooth(RIM, RIM + 56, r), 1.6) * 170;
        if (f > 0.99) for (int[] t : stacks) {
            double d = Math.hypot(x - t[0], z - t[1]) + n2.get(x / 16.0, z / 16.0) * 7;
            if (d < t[2]) h = Math.max(h, 60 + t[3] * Math.pow(1 - Math.pow(d / t[2], 6), 0.5));
        }
        return (int) h;
    }

    private boolean inKeep(int x, int z) {
        return Math.abs(x) <= KX && z >= KZ0 && z <= KZ1;
    }

    @Override
    public int ground(int x, int z) {
        if (Math.abs(x) <= 4 && z >= 186 && z <= 280) return APRON;
        if (inKeep(x, z)) return Math.abs(x) <= HALL_X && z >= HALL_Z0 && z <= HALL_Z1 ? F : SETBACK;
        return Math.max(terrain(x, z), 0);
    }

    @Override
    public double edge(int x, int z) {
        return Math.hypot(x, z) - HALF;
    }

    @Override
    public int halfSize() {
        return HALF;
    }

    @Override
    public int biome(int x, int y, int z) {
        return inHeart(x, y, z) ? 1 : 0;
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
        return Math.abs(x) <= HALL_X && z >= HALL_Z0 && z <= HALL_Z1 && y >= F;
    }

    @Override
    public String heartClosedLine() {
        return "The great doors of the keep are barred from the inside, and the bars are held by something still walking around out here.";
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
            case "heart_weeps" -> falls(out, heapsA, true);
            case "heart_winds" -> falls(out, heapsB, true);
            case "heart_still" -> {
                falls(out, heapsA, false);
                falls(out, heapsB, false);
            }
            default -> {}
        }
        return out;
    }

    /** As the Lord weakens, what's left of the vault comes down in the nave: first two heaps in front of the dais, then two more by the door. */
    private void falls(List<Edit> out, List<int[]> where, boolean on) {
        M m = m();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int[] h : where) for (int x = -h[2]; x <= h[2]; x++) for (int z = -h[2]; z <= h[2]; z++) {
            double d = Math.hypot(x, z);
            if (d > h[2]) continue;
            int tall = (int) Math.round(h[3] * Math.sqrt(1 - d / (h[2] + 0.5)));
            for (int k = 1; k <= tall; k++) out.add(new Edit(h[0] + x, F + k, h[1] + z, !on ? air : k == tall && rnd(x * 3, z, 9) < 0.4 ? m.barkMoss : rubble(m, h[0] + x, F + k, h[1] + z)));
        }
    }

    @Override
    public void paint(V v) {
        M m = mats();
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) column(v, m, x, z);
        drawPieces(v, m);
        drape(v, m);
        settle(v, m);
        dev.awaken.gates.realms.kit.Settle.overSlabs(v); // nothing small half a block over a low slab (SPEC 50.6)
        for (Edit e : grille) if (v.in(e.x(), e.y(), e.z())) v.set(e.x(), e.y(), e.z(), e.state());
    }

    /** The jungle's share: vines down from everything with an underside, moss and fern along the tops of the walls. */
    private void drape(V v, M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            if (inHeart(x, F + 2, z) || ring1.dist(x + 0.5, z + 0.5) > 2) continue;
            int run = 0;
            for (int y = 300; y >= APRON + 6; y--) {
                BlockState s = v.get(x, y, z);
                if (s == null) continue;
                if (s == m.flag && v.get(x, y + 1, z) == null) {
                    double c = rnd(x, z, 31);
                    if (c < 0.12) v.set(x, y + 1, z, m.carpet);
                    else if (c < 0.17) v.set(x, y + 1, z, m.fern);
                    else if (c < 0.2) v.set(x, y + 1, z, m.drift);
                }
                if (!V.holds(s) || v.get(x, y - 1, z) != null || s == m.leaf || s == m.leafLit || s == m.leafDeep) continue;
                if (rnd(x + y, z, 32) > 0.14) continue;
                int len = 2 + (int) (rnd(x, z + y, 33) * 12);
                for (int k = 1; k <= len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.vines);
            }
        }
    }

    /** Pieces carve through each other, so now and then a plant ends up over a hole: it goes. */
    private void settle(V v, M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) for (int y = WATER; y <= 300; y++) {
            BlockState s = v.get(x, y, z);
            if (s == null || !(s == m.fern || s == m.carpet || s == m.drift || s == m.grass)) continue;
            BlockState u = v.get(x, y - 1, z);
            if (!V.holds(u)) v.set(x, y, z, null);
        }
    }

    private dev.awaken.gates.world.FloorAir builtAir;

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        if (builtAir != null) return builtAir;
        AirPlan a = new AirPlan().mist(WATER + 4, 8, 0x8ea0a8, 0.5F).mist(OUTER + 3, 6, 0x6b7c84, 0.28F).mist(F + 3, 4, 0x5d5a58, 0.34F).water(WATER, -320, -320, 320, 380).water(F, -22, -36, 22, 46);
        a.shaft(0, F + 104, -46, 0, F, -44, 8F, 0xa6bccb, 0.9F);
        for (int i = 0; i < 8; i++) {
            int x = i % 2 == 0 ? -12 : 12, z = 40 - i * 11;
            a.shaft(x, F + 98, z, x + (i % 3 - 1) * 3, F, z - 2, 6F, 0x9fb6c8, 0.55F);
        }
        for (int z : PIERS) for (int sx = -1; sx <= 1; sx += 2) {
            a.light(sx * 21, F + 4, z, 0xff9650, 15F, 1.1F);
            a.light(sx * 36, F + 6, z, 0xff8a48, 12F, 0.7F);
        }
        a.light(0, F + 12, -48, 0xffa458, 18F, 1F);
        return builtAir = a.build();
    }

    private void column(V v, M m, int x, int z) {
        if (Math.abs(x) > HALF || Math.abs(z) > HALF) return;
        int g = terrain(x, z);
        double px = x + 0.5, pz = z + 0.5;
        boolean within = ring1.dist(px, pz) <= 0, upper = within && ring2.inside(px, pz);
        v.set(x, 0, z, m.bedrock);
        boolean bare = !within && (Math.abs(terrain(x + 2, z) - g) + Math.abs(terrain(x, z + 2) - g) > 3 || g > 120);
        for (int y = 1; y < g - 3; y++) v.set(x, y, z, bare ? rockOf(m, x, y, z) : m.rock);
        for (int y = Math.max(1, g - 3); y < g; y++) v.set(x, y, z, bare ? rockOf(m, x, y, z) : m.bed);
        BlockState top;
        if (g <= WATER) top = rnd(x, z, 1) < 0.5 ? m.bed : m.deep;
        else if (bare) top = rockOf(m, x, g, z);
        else if (within) {
            double track = wet.get(x / 26.0, z / 26.0);
            double f = rnd(x, z, 2);
            if (Math.abs(track) < 0.1) top = f < 0.55 ? m.hexstone : f < 0.8 ? m.flag : m.bed;
            else if (upper) top = n2.get(x / 12.0, z / 12.0) > 0.25 ? m.moss : f < 0.5 ? m.flag : m.hexstone;
            else top = n2.get(x / 16.0, z / 16.0) > -0.2 ? m.moss : m.bed;
        } else if (Math.abs(x) <= 28 && z >= 262 && z <= 328) top = rnd(x, z, 2) < 0.5 ? m.flag : rnd(x, z, 3) < 0.5 ? m.hexstone : m.deep;
        else top = n2.get(x / 16.0, z / 16.0) > 0.35 ? m.bed : m.moss;
        v.set(x, g, z, top);
        if (g < WATER) {
            for (int y = g + 1; y <= WATER; y++) v.set(x, y, z, m.water);
            return;
        }
        if (top != m.moss && top != m.bed || tended(x, z)) return;
        double c = rnd(x, z, 3);
        if (c < 0.12) v.set(x, g + 1, z, m.fern);
        else if (c < 0.3) v.set(x, g + 1, z, m.grass);
        else if (c < 0.36) v.set(x, g + 1, z, m.drift);
    }

    private BlockState rockOf(M m, int x, int y, int z) {
        double s = n2.get(x / 18.0 + y / 40.0, z / 18.0) + (Math.floorMod(y + (int) (n1.get(x / 60.0, z / 60.0) * 6), 7) < 2 ? 0.45 : 0);
        return s > 0.8 ? m.worn : s > 0.2 ? m.stoneA : s > -0.35 ? m.deep : m.stoneA;
    }

    /** One block of a built wall: mostly slate, black where the rain runs, green where it stays, with a paler dressed stone here and there. */
    BlockState course(M m, int x, int y, int z) {
        double w = wet.get((x + z) / 9.0, y / 30.0), p = n3.get((x + z) / 17.0, y / 13.0);
        double f = rnd(x * 31 + z, y, 77);
        if (w > 0.6) return f < 0.72 ? m.deep : m.stoneA;
        if (p > 0.52) return f < 0.78 ? m.worn : m.stoneA;
        return f < 0.08 ? m.pale : f < 0.93 ? m.stoneA : m.deep;
    }

    BlockState rubble(M m, int x, int y, int z) {
        double f = rnd(x * 13 + z, y, 79);
        return f < 0.3 ? m.deep : f < 0.6 ? m.stoneA : f < 0.8 ? m.worn : m.pale;
    }

    // ---- how a wall is made ---------------------------------------------------------------------------------------------------

    private static boolean arch(int bs, int a) {
        double half = a <= 6 ? 4.6 : 4.6 - (a - 6) * 1.3;
        return half > 0 && Math.abs(bs - 9.5) <= half;
    }

    /**
     * One cell of the outside of a castle wall. Steps in and out in four planes: a battered foot, pilasters on a sixteen-block rhythm, a pointed blind arch in
     * every bay with a lit arrow slit at its back, string courses across the lot, and corbels carrying a parapet out over the top with merlons and fires on it.
     * s is how far in from the outermost plane, t how far along, h how high above the foot, walk the height of the wall-walk. Null is air.
     */
    BlockState wall(M m, int x, int y, int z, int t, int s, int h, int walk, Direction in, boolean corner) {
        if (h > walk + 4) return null;
        int bs = Math.floorMod(t, 16), bay = Math.floorDiv(t, 16);
        boolean pil = corner || bs <= 3;
        if (h > walk) {
            if (s > 0) return null;
            int cr = Math.floorMod(t, 4);
            if (h == walk + 1) return course(m, x, y, z);
            if (cr >= 2) return h == walk + 2 && Math.floorMod(t, 8) == 2 ? m.brazier : null;
            return h == walk + 4 ? m.stoneSlab : m.pale;
        }
        if (h == walk) return s == 0 ? m.pale : m.flag;
        if (h == walk - 1) return s == 0 ? under(m.paleStairs, in) : m.stoneA;
        if (h == walk - 2) {
            if (s == 0) return Math.floorMod(t, 3) == 0 ? under(m.stoneStairs, in) : null;
            return s == 1 ? (Math.floorMod(t, 4) < 2 ? m.relief : m.pale) : m.stoneA;
        }
        if (s == 0) return h <= 2 ? course(m, x, y, z) : h == 3 ? stair(m.stoneStairs, in) : null;
        int lim = walk - 3, zh = Math.floorMod(h - 8, 13), z0 = h - zh;
        boolean fits = h >= 8 && z0 + 12 <= lim;
        if (s == 1) {
            if (h <= 6) return course(m, x, y, z);
            if (h == 7) return pil ? course(m, x, y, z) : stair(m.stoneStairs, in);
            if (pil) {
                if (h == lim) return under(m.stoneStairs, in);
                return (bs == 0 || bs == 3) && Math.floorMod(h, 2) == 0 ? m.pale : course(m, x, y, z);
            }
            return fits && zh == 0 ? stair(m.paleStairs, in) : null;
        }
        boolean open = !pil && fits && zh >= 2 && zh <= 11 && arch(bs, zh - 2);
        boolean slit = open && (bs == 9 || bs == 10) && zh >= 3 && zh <= 9;
        if (s == 2) {
            if (pil) return course(m, x, y, z);
            if (open) return null;
            if (fits && zh >= 3 && arch(bs, zh - 3)) return m.pale;
            if (h == lim) return Math.floorMod(t, 4) < 2 ? m.relief : m.stoneA;
            if (fits && zh == 0) return m.pale;
            return course(m, x, y, z);
        }
        if (s == 3) {
            if (!open) return m.stoneA;
            if (slit) return null;
            if (zh == 2) return stair(m.paleStairs, in);
            return course(m, x, y, z);
        }
        if (s == 4 && slit) return Math.floorMod(bay * 3 + z0, 3) != 2 ? m.window : m.deep;
        return m.stoneA;
    }

    /** One cell of the inside of a curtain: a run of vaulted bays you could stable a horse in, some with a fire still going at the back. */
    BlockState casemate(M m, int x, int y, int z, int t, int sIn, int h, int top) {
        if (h <= 0) return m.stoneA;
        int bs = Math.floorMod(t + 6, 12);
        boolean open = bs >= 2 && bs <= 9 && h <= (bs == 2 || bs == 9 ? 5 : bs == 3 || bs == 8 ? 6 : 7);
        if (sIn == 0) {
            if (open) return null;
            if (h == 8) return m.pale;
            return (bs == 0 || bs == 1 || bs == 10 || bs == 11) && Math.floorMod(h, 2) == 0 ? m.pale : course(m, x, y, z);
        }
        if (sIn == 1) return open ? (bs == 5 && h == 1 && Math.floorMod(t, 24) < 12 ? m.brazier : null) : m.stoneA;
        return open ? (rnd(t, h, 83) < 0.3 ? m.worn : m.brick) : m.stoneA;
    }

    /** A ring of curtain wall, broken where the roots went through it. */
    final class Walls extends Piece<M> {
        final Ring ring;
        final int foot, walk, ih;
        final List<Breach> breaches;

        Walls(Ring ring, int foot, int walk, int ih, List<Breach> breaches) {
            this.ring = ring;
            this.foot = foot;
            this.walk = walk;
            this.ih = ih;
            this.breaches = breaches;
            int lx = 9999, hx = -9999, lz = 9999, hz = -9999;
            for (int[] p : ring.v) {
                lx = Math.min(lx, p[0]);
                hx = Math.max(hx, p[0]);
                lz = Math.min(lz, p[1]);
                hz = Math.max(hz, p[1]);
            }
            x0 = lx - 2;
            x1 = hx + 2;
            z0 = lz - 2;
            z1 = hz + 2;
            mayOverlap = true;
            label = "curtain wall";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double px = x + 0.5, pz = z + 0.5, d = ring.dist(px, pz);
                if (d > 0 || d < -(CW + 34)) continue;
                int s = (int) Math.floor(-d), seg = ring.nearest(px, pz);
                boolean along = ring.along[seg];
                Direction in = ring.in[seg];
                int t = along ? x : z;
                Breach b = null;
                for (Breach c : breaches) if (c.alongX == along && Math.abs(along ? x - c.cx : z - c.cz) <= c.hw && Math.abs(along ? z - c.cz : x - c.cx) <= CW + 34) b = c;
                if (s >= CW) {
                    if (b != null) spill(v, m, x, z, s, along, b);
                    continue;
                }
                int top = walk + 4, dt = 0;
                if (b != null) {
                    dt = Math.abs(along ? x - b.cx : z - b.cz);
                    top = walk - Math.max(0, b.depth - Math.max(0, dt - 1)) + (rnd(x, z, 5) < 0.2 && dt > 2 ? 1 : 0);
                }
                for (int y = foot - 5; y <= foot + walk + 4; y++) {
                    int h = y - foot;
                    BlockState c;
                    if (h < 0) c = m.stoneA;
                    else if (b != null && dt <= 4 && s < 2) c = s == 1 && dt <= 3 && h <= top ? m.climb : null;
                    else if (b != null && h > top) c = null;
                    else if (b != null && h == top) c = rubble(m, x, y, z);
                    else if (b != null && dt <= 4 && s < 5) c = course(m, x, y, z);
                    else if (s >= CW - 3 && h < walk - 2) c = casemate(m, x, y, z, t, CW - 1 - s, h - ih, walk - ih);
                    else if (s == CW - 1 && h == walk + 1) c = m.stoneSlab;
                    else c = wall(m, x, y, z, t, s, h, walk, in, false);
                    v.set(x, y, z, c);
                }
            }
        }

        /** What the wall spilled into the ward when it came down: a slope of rubble you can walk. */
        private void spill(V v, M m, int x, int z, int s, boolean along, Breach b) {
            int dt = Math.abs(along ? x - b.cx : z - b.cz);
            int top = walk - Math.max(0, b.depth - Math.max(0, dt - 1));
            int st = top - (s - CW + 1) - Math.max(0, dt - 7) * 2;
            for (int y = foot + ih; y <= foot + st; y++) v.set(x, y, z, y == foot + st && rnd(x, z, 6) < 0.35 ? m.moss : rubble(m, x, y, z));
        }
    }

    /** A round tower. Every vertex of a ring has one, so no corner is ever two planes meeting. */
    final class Tower extends Piece<M> {
        final int cx, cz, R, foot, walk, top, roof;
        final Ring ring;
        final double ox, oz;

        Tower(int cx, int cz, int R, int foot, int walk, int top, int roof, Ring ring, double ox, double oz) {
            this.cx = cx;
            this.cz = cz;
            this.R = R;
            this.foot = foot;
            this.walk = walk;
            this.top = top;
            this.roof = roof;
            this.ring = ring;
            this.ox = ox;
            this.oz = oz;
            box(cx, cz, R + 3, R + 3);
            mayOverlap = true;
            label = "tower";
        }

        private double radius(int h) {
            if (h <= 3) return R + 1.6;
            if (h <= 7) return R + 0.8;
            if (h >= top - 3 && h <= top + 3) return R + 1.6;
            return R;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, dz = z - cz;
                double r = Math.hypot(dx, dz);
                if (r > R + 2.6) continue;
                double a = Math.atan2(dz, dx);
                int arc = (int) Math.floor((a + Math.PI) * R);
                Direction in = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                for (int y = foot - 4; y <= foot + top + 3; y++) {
                    int h = y - foot;
                    double rad = radius(h);
                    if (r > rad) continue;
                    v.set(x, y, z, cell(m, x, y, z, h, r, rad, arc, a, in));
                }
                if (roof > 0) cone(v, m, x, z, r, in);
            }
        }

        private BlockState cell(M m, int x, int y, int z, int h, double r, double rad, int arc, double a, Direction in) {
            if (h < 0) return m.stoneA;
            if (h > walk && h <= walk + 6 && r < R - 3) return null;
            if (h == walk && r < R - 2) return m.flag;
            if (ring != null && h > walk && h <= walk + 4) {
                double d = ring.dist(x + 0.5, z + 0.5);
                if (d < -2 && d > -(CW - 2)) return null;
            }
            if (h > top) {
                if (roof > 0) return h == top + 1 && r >= R - 0.6 ? course(m, x, y, z) : null;
                if (r < R - 0.2) return null;
                if (h == top + 1) return course(m, x, y, z);
                return Math.floorMod(arc, 5) < 3 ? (h == top + 3 ? m.pale : m.stoneA) : h == top + 2 && Math.floorMod(arc, 15) == 4 ? m.brazier : null;
            }
            if (h == top) return r >= R - 0.2 ? m.pale : m.flag;
            if (h == top - 3 && r > R + 0.2) return Math.floorMod(arc, 3) == 0 ? under(m.stoneStairs, in) : null;
            if (h == top - 2 && r > R + 0.2) return under(m.paleStairs, in);
            if (h == 3 && r > R + 0.8) return stair(m.stoneStairs, in);
            if (h == 7 && r > R) return stair(m.stoneStairs, in);
            if (rad - r > 3.2) return m.stoneA;
            int n = R >= 10 ? 8 : 6;
            double u = (a + Math.PI) / (2 * Math.PI) * n;
            int sector = (int) u;
            double off = (u - sector - 0.5) * (2 * Math.PI / n) * R;
            boolean slit = Math.abs(off) < 1.1 && Math.floorMod(h - 10 + sector * 5, 14) < 6 && h >= 8 && h < top - 4;
            if (slit && rad - r < 2) return null;
            if (slit && rad - r < 3.2) return Math.floorMod(sector + h / 14, 3) != 2 ? m.window : m.deep;
            if (Math.abs(off) < 2.1 && rad - r < 1.6 && h >= 8 && h < top - 4 && Math.floorMod(h - 9 + sector * 5, 14) == 0) return under(m.paleStairs, in);
            if (Math.floorMod(h - 8, 12) == 0) return Math.floorMod(arc, 4) < 2 ? m.relief : m.pale;
            return course(m, x, y, z);
        }

        /** A steep cone of green tile, or what the storms have left of one. */
        private void cone(V v, M m, int x, int z, double r, Direction in) {
            double reach = R + 2.5;
            if (r > reach) return;
            int yb = foot + top + 1, ytop = yb + (int) ((reach - r) * 1.5);
            for (int y = Math.max(yb, ytop - 3); y <= ytop; y++) {
                if (roof == 2 && (x - cx) * ox + (z - cz) * oz > r * 0.15 && y > yb + 4) continue;
                BlockState tile = r > R + 1.6 && y == ytop ? stair(m.tileStairs, in) : Math.floorMod(y, 5) == 0 ? m.tileDeep : m.tile;
                v.set(x, y, z, tile);
            }
            if (r < 0.8 && roof == 1) for (int k = 1; k <= 3; k++) v.set(x, ytop + k, z, k == 3 ? m.lantern : m.gold);
        }
    }

    private void towers(Ring ring, int foot, int walk, int R) {
        int n = ring.v.length;
        for (int i = 0; i < n; i++) {
            int[] a = ring.v[i], b = ring.v[(i + 1) % n];
            if (a[2] == 1) tower(ring, foot, walk, a[0], a[1], R + i % 2, i);
            int len = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
            if (len > 90) tower(ring, foot, walk, (a[0] + b[0]) / 2 + (ring.along[i] ? 11 : 0), (a[1] + b[1]) / 2 + (ring.along[i] ? 0 : 11), R - 1, i + n);
        }
    }

    private void tower(Ring ring, int foot, int walk, int x, int z, int R, int i) {
        int top = walk + 6 + (i * 7) % 9;
        int roof = i % 4 == 0 ? 1 : i % 4 == 2 ? 2 : 0;
        double a = i * 2.1;
        pieces.add(new Tower(x, z, R, foot, walk, top, roof, ring, Math.cos(a), Math.sin(a)));
    }

    /** A block of masonry with a castle wall on all four sides. */
    final class Mass extends Piece<M> {
        final int cx, cz, hx, hz, foot, walk;
        int notchT = Integer.MIN_VALUE;
        Direction notchFace;

        Mass(int cx, int cz, int hx, int hz, int foot, int walk, String what) {
            this.cx = cx;
            this.cz = cz;
            this.hx = hx;
            this.hz = hz;
            this.foot = foot;
            this.walk = walk;
            box(cx, cz, hx, hz);
            mayOverlap = true;
            label = what;
        }

        Mass notch(int t, Direction in) {
            notchT = t;
            notchFace = in;
            return this;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int sx = hx - Math.abs(x - cx), sz = hz - Math.abs(z - cz), s = Math.min(sx, sz);
                boolean onX = sz < sx;
                int t = onX ? x : z;
                Direction in = onX ? (z > cz ? Direction.NORTH : Direction.SOUTH) : (x > cx ? Direction.WEST : Direction.EAST);
                boolean corner = Math.max(sx, sz) < 4, notched = in == notchFace && Math.abs(t - notchT) <= 4;
                for (int y = foot - 2; y <= foot + walk + 4; y++) {
                    int h = y - foot;
                    BlockState c;
                    if (h < 0) c = m.stoneA;
                    else if (notched && h > walk) c = null;
                    else if (notched && s < 2) c = s == 1 && Math.abs(t - notchT) <= 3 ? m.climb : null;
                    else if (notched && s < 5) c = h == walk ? m.flag : course(m, x, y, z);
                    else c = wall(m, x, y, z, t, s, h, walk, in, corner);
                    v.set(x, y, z, c);
                }
            }
        }
    }

    /** A wall's great gate, in a block of masonry with two drum towers in front of it. Barred, and it has been for a very long time. */
    private void gate(int cz, int hz, int hx, int foot, int walk, int tz, int tx, int R, int ttop, int roof) {
        pieces.add(new Mass(0, cz, hx, hz, foot, walk, "gatehouse"));
        pieces.add(new Tower(-tx, tz, R, foot, walk, ttop, roof, null, 0, 0));
        pieces.add(new Tower(tx, tz, R, foot, walk, ttop + 3, roof * 2, null, 0.8, 0.6));
        pieces.add(new GateDoor(cz + hz, foot + 2));
        pieces.add(new Cloth(true, cz + hz, -3, 3, foot + walk - 5, 7));
    }

    /** Two leaves of black oak behind a portcullis, set deep in a stepped arch. */
    final class GateDoor extends Piece<M> {
        final int face, gy;

        GateDoor(int face, int gy) {
            this.face = face;
            this.gy = gy;
            box(0, face - 3, 9, 5);
            mayOverlap = true;
            label = "gate";
        }

        private int archTop(int ax, int step) {
            return (int) (13 + step * 1.0 - Math.pow(Math.max(0, ax - 3.0 + step * 0.0) / (8.0 + step), 2) * 8);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = Math.abs(x), step = face - z;
                if (step < 0 || step > 5) continue;
                int hw = 8 - step, top = archTop(a, 2 - Math.min(2, step));
                if (a > hw + 1) continue;
                for (int y = gy - 1; y <= gy + top + 1; y++) {
                    int h = y - gy;
                    if (h < 0) {
                        v.set(x, y, z, m.flag);
                        continue;
                    }
                    if (a == hw + 1 || h == top + 1) {
                        if (h <= top + 1) v.set(x, y, z, Math.floorMod(h + step, 2) == 0 ? m.pale : m.relief);
                        continue;
                    }
                    if (h > top) continue;
                    if (step < 4) {
                        v.set(x, y, z, null);
                        continue;
                    }
                    if (step == 4) v.set(x, y, z, a % 3 == 0 || h % 3 == 0 ? m.timber : null);
                    else v.set(x, y, z, a == 0 ? m.deep : h % 4 == 2 && a % 3 == 1 ? m.gold : m.planks);
                }
            }
        }
    }

    /** A length of rust-red cloth on a black beam, torn off unevenly at the bottom. */
    final class Cloth extends Piece<M> {
        final boolean alongX;
        final int c, a0, a1, y, len;

        Cloth(boolean alongX, int c, int a0, int a1, int y, int len) {
            this.alongX = alongX;
            this.c = c;
            this.a0 = a0;
            this.a1 = a1;
            this.y = y;
            this.len = len;
            if (alongX) {
                x0 = a0;
                x1 = a1;
                z0 = c;
                z1 = c;
            } else {
                x0 = c;
                x1 = c;
                z0 = a0;
                z1 = a1;
            }
            mayOverlap = true;
            label = "cloth";
        }

        @Override
        public void draw(V v, M m) {
            for (int t = a0; t <= a1; t++) {
                int x = alongX ? t : c, z = alongX ? c : t;
                v.set(x, y + 1, z, m.timber);
                int l = len - (int) (rnd(t, c, 91) * 6) - (t == a0 || t == a1 ? 2 : 0);
                for (int k = 0; k < l; k++) v.set(x, y - k, z, m.cloth);
            }
        }
    }

    final class WallStair extends Piece<M> {
        final boolean alongX;
        final int c0, c1, start, dir, yTop, yBot;
        final BlockState body;

        WallStair(boolean alongX, int c0, int c1, int start, int dir, int yTop, int yBot) {
            this.alongX = alongX;
            this.c0 = c0;
            this.c1 = c1;
            this.start = start;
            this.dir = dir;
            this.yTop = yTop;
            this.yBot = yBot;
            body = null;
            int end = start + dir * (yTop - yBot + 1);
            if (alongX) {
                x0 = Math.min(start, end);
                x1 = Math.max(start, end);
                z0 = c0;
                z1 = c1;
            } else {
                x0 = c0;
                x1 = c1;
                z0 = Math.min(start, end);
                z1 = Math.max(start, end);
            }
            mayOverlap = true;
            label = "wall stair";
        }

        @Override
        public void draw(V v, M m) {
            Direction up = alongX ? (dir > 0 ? Direction.WEST : Direction.EAST) : (dir > 0 ? Direction.NORTH : Direction.SOUTH);
            for (int k = 0; k <= yTop - yBot + 1; k++) for (int c = c0; c <= c1; c++) {
                int x = alongX ? start + dir * k : c, z = alongX ? c : start + dir * k, y = yTop - k;
                if (!v.in(x, 100, z)) continue;
                for (int yy = yBot - 2; yy < y; yy++) v.set(x, yy, z, c == c0 || c == c1 ? course(m, x, yy, z) : m.stoneA);
                v.set(x, y, z, k == 0 ? m.flag : stair(m.stoneStairs, up));
                for (int yy = y + 1; yy <= y + 4; yy++) if (v.get(x, yy, z) != null && V.holds(v.get(x, yy, z))) v.set(x, yy, z, null);
            }
        }
    }

    private void wallStairs() {
        pieces.add(new WallStair(true, 153, 156, -30, -1, FOOT1 + WALK1, OUTER + 1));
        pieces.add(new WallStair(true, 153, 156, 30, 1, FOOT1 + WALK1, OUTER + 1));
        pieces.add(new WallStair(true, 69, 72, 54, 1, FOOT2 + WALK2, INNER + 1));
        pieces.add(new WallStair(false, 78, 81, -2, -1, FOOT2 + WALK2, INNER + 1));
    }

    // ---- the keep ---------------------------------------------------------------------------------------------------------------

    private void keep() {
        int cz = (KZ0 + KZ1) / 2, hz = (KZ1 - KZ0) / 2;
        pieces.add(new Mass(0, cz, KX, hz, F, SETBACK - F, "keep foot").notch(-30, Direction.NORTH));
        pieces.add(new Mass(0, cz, KX - 8, hz - 8, SETBACK, CROWN - SETBACK, "keep"));
        pieces.add(new Mass(-46, 52, 11, 11, SETBACK, 70, "keep tower"));
        pieces.add(new Mass(46, 52, 11, 11, SETBACK, 84, "keep tower"));
        pieces.add(new Mass(-46, -60, 11, 11, SETBACK, 62, "keep tower"));
        pieces.add(new Mass(46, -60, 11, 11, SETBACK, 96, "keep tower"));
        pieces.add(new Passage(35, 54, 52, 58));
        pieces.add(new Passage(48, 41, 52, 58));
        pieces.add(new Tower(0, -66, 9, CROWN - 6, 6, 40, 1, null, 0, 0));
        pieces.add(new Tower(-54, -4, 7, SETBACK - 2, 8, 64, 2, null, -0.8, 0.5));
        pieces.add(new Cloth(true, KZ1, -16, -11, SETBACK - 10, 18));
        pieces.add(new Cloth(true, KZ1, 16, 21, SETBACK - 10, 16));
        pieces.add(new Cloth(true, 52, -8, 8, CROWN - 8, 24));
        for (int[] h : new int[][]{{-14, 22, 5, 4, 0}, {14, 22, 5, 4, 0}, {-12, -2, 6, 5, 1}, {12, -2, 6, 5, 1}}) (h[4] == 0 ? heapsB : heapsA).add(h);
        for (int i = 0; i < 18; i++) {
            double a = jit(Math.PI), reach = 26 + jit(10);
            heaps.add(new int[]{(int) (Math.cos(a) * 34), (int) (Math.sin(a) * 40) - 4, 2 + (i % 3), 1 + (i % 2)});
        }
        for (int y = F + 1; y <= F + 18; y++) for (int x = -7; x <= 7; x++) {
            int top = F + 14 + (int) (4.0 * Math.sqrt(Math.max(0, 1 - x * x / 56.0)));
            if (y <= top && (Math.abs(x) % 3 == 0 || (y - F) % 3 == 0)) grille.add(new Edit(x, y, 56, m().timber));
        }
    }

    /** A vaulted way through the foot of a corner tower, so the walk round the keep isn't cut off. */
    final class Passage extends Piece<M> {
        Passage(int ax, int az, int bx, int bz) {
            x0 = ax;
            z0 = az;
            x1 = bx;
            z1 = bz;
            mayOverlap = true;
            label = "tower passage";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                v.set(x, SETBACK, z, m.flag);
                for (int y = SETBACK + 1; y <= SETBACK + 5; y++) v.set(x, y, z, null);
            }
        }
    }

    /** The great hall. A nave between eight huge piers, aisles and two galleries behind them, the stumps of a vault, and the sky where the roof used to be. */
    final class Hall extends Piece<M> {
        Hall() {
            box(0, (HALL_Z0 + HALL_Z1) / 2, HALL_X + 6, (HALL_Z1 - HALL_Z0) / 2 + 16);
            label = "great hall";
        }

        private int zp(int z) {
            int best = PIERS[0];
            for (int p : PIERS) if (Math.abs(z - p) < Math.abs(z - best)) best = p;
            return best;
        }

        private int pierTop(int zp, int side) {
            return zp == -34 ? F + 60 : F + 52 + Math.floorMod(zp * 7 + side * 5, 13);
        }

        private BlockState floor(M m, int x, int z) {
            int a = Math.abs(x);
            if (a <= 14) {
                boolean chev = Math.floorMod(a + z, 8) < 4;
                if (a == 0 || Math.floorMod(z, 11) == 0) return m.pale;
                return chev ? m.deep : m.flag;
            }
            if (a <= 16) return m.pale;
            return rnd(x, z, 11) < 0.5 ? m.flag : m.hexstone;
        }

        private boolean puddle(int x, int z) {
            return wet.get(x / 7.0, z / 7.0) > 0.58 && Math.abs(x) <= 34 && z > -34;
        }

        @Override
        public void draw(V v, M m) {
            int vx0 = ax(v), vx1 = bx(v), vz0 = az(v), vz1 = bz(v);
            for (int x = vx0; x <= vx1; x++) for (int z = vz0; z <= vz1; z++) {
                if (Math.abs(x) <= HALL_X && z >= HALL_Z0 && z <= HALL_Z1) {
                    for (int y = F + 1; y <= F + 108; y++) v.set(x, y, z, null);
                    v.set(x, F, z, puddle(x, z) ? m.water : floor(m, x, z));
                    v.set(x, F - 1, z, m.stoneA);
                }
                door(v, m, x, z);
                sideBay(v, m, x, z);
            }
            for (int x = vx0; x <= vx1; x++) for (int z = vz0; z <= vz1; z++) {
                if (Math.abs(x) > HALL_X || z < HALL_Z0 || z > HALL_Z1) continue;
                arcade(v, m, x, z);
                gallery(v, m, x, z);
            }
            for (int zp : PIERS) for (int sx = -1; sx <= 1; sx += 2) pier(v, m, sx * 27, zp, sx);
            farArch(v, m);
            dais(v, m);
            rubbleHeaps(v, m);
            hanging(v, m);
        }

        private void door(V v, M m, int x, int z) {
            int a = Math.abs(x);
            if (z <= HALL_Z1 || z > KZ1 || a > 9) return;
            int top = F + 14 + (int) (4.0 * Math.sqrt(Math.max(0, 1 - a * a / 56.0)));
            for (int y = F + 1; y <= top + 1; y++) {
                if (a == 8 || a == 9 || y == top + 1) v.set(x, y, z, Math.floorMod(y + z, 2) == 0 ? m.pale : m.relief);
                else if (a <= 7 && y <= top) v.set(x, y, z, null);
            }
            if (a <= 7) v.set(x, F, z, Math.floorMod(z, 3) == 0 ? m.pale : m.flag);
        }

        private void sideBay(V v, M m, int x, int z) {
            int a = Math.abs(x);
            if (a != HALL_X + 1 && a != HALL_X + 2 || z < HALL_Z0 + 2 || z > HALL_Z1 - 2) return;
            int bs = Math.floorMod(z + 5, 22);
            if (bs > 10) return;
            for (int h = 1; h <= 13; h++) {
                int half = h <= 9 ? 5 : 5 - (h - 9);
                boolean open = Math.abs(bs - 5) <= half;
                if (!open) continue;
                int y = F + h;
                if (a == HALL_X + 1) v.set(x, y, z, null);
                else v.set(x, y, z, Math.abs(bs - 5) <= 1 && h >= 5 && h <= 10 ? m.window : Math.floorMod(h, 3) == 0 ? m.pale : m.brick);
            }
        }

        private int archTop(int rel, int base) {
            return base + (int) Math.round(8 * Math.sqrt(Math.max(0, 1 - rel * rel / 56.0)));
        }

        private void arcade(V v, M m, int x, int z) {
            int a = Math.abs(x);
            if (a < 23 || a > 31) return;
            int zp = zp(z), d = Math.abs(z - zp);
            if (d <= 4 || z > 40 || z < -38) return;
            int mid = z > zp ? zp + 11 : zp - 11, rel = z - mid;
            int top1 = archTop(rel, F + 9), top2 = archTop(rel, F + 24);
            for (int y = top1 + 1; y <= F + 18; y++) v.set(x, y, z, y == top1 + 1 ? m.pale : Math.floorMod(rel, 7) == 3 && y > F + 14 ? m.relief : course(m, x, y, z));
            for (int y = top2 + 1; y <= F + 37; y++) v.set(x, y, z, y == top2 + 1 ? m.pale : course(m, x, y, z));
        }

        private void gallery(V v, M m, int x, int z) {
            int a = Math.abs(x);
            if (a < 23 || a > 40 || z > 44 || z < -38) return;
            boolean well1 = a >= 33 && a <= 36 && z >= 17 && z <= 36, well2 = a >= 37 && a <= 40 && z >= -3 && z <= 15;
            if (!well1) {
                v.set(x, F + 19, z, a == 23 ? under(m.paleStairs, x > 0 ? Direction.EAST : Direction.WEST) : m.stoneA);
                v.set(x, F + 20, z, a == 23 ? m.pale : Math.floorMod(x + z, 5) == 0 ? m.brick : m.flag);
            }
            if (!well2) {
                v.set(x, F + 37, z, a == 23 ? under(m.paleStairs, x > 0 ? Direction.EAST : Direction.WEST) : m.stoneA);
                v.set(x, F + 38, z, a == 23 ? m.pale : Math.floorMod(x - z, 5) == 0 ? m.brick : m.flag);
            }
            int zpz = zp(z);
            if (a == 23 && Math.abs(z - zpz) > 4) {
                for (int level : new int[]{F + 20, F + 38}) {
                    if (Math.floorMod(z, 3) == 0) {
                        v.set(x, level + 1, z, m.brick);
                        v.set(x, level + 2, z, m.brick);
                        v.set(x, level + 3, z, Build.top(m.brickSlab));
                    } else v.set(x, level + 3, z, Build.top(m.brickSlab));
                }
            }
            // the two flights up
            if (well1) {
                int y = F + 1 + (36 - z);
                for (int yy = F + 1; yy < y; yy++) v.set(x, yy, z, m.stoneA);
                v.set(x, y, z, stair(m.stoneStairs, Direction.NORTH));
            }
            if (well2) {
                int y = F + 21 + (15 - z);
                for (int yy = F + 21; yy < y; yy++) v.set(x, yy, z, m.stoneA);
                v.set(x, y, z, stair(m.stoneStairs, Direction.NORTH));
            }
        }

        private void pier(V v, M m, int cx, int cz, int side) {
            int top = pierTop(cz, side);
            for (int x = cx - 6; x <= cx + 6; x++) for (int z = cz - 6; z <= cz + 6; z++) {
                if (!v.in(x, 100, z)) continue;
                int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
                int ragged = top - (int) (n3.get(x / 3.0, z / 3.0) * 3 + 2 + Math.max(0, (x - cx) * side) * 1.2);
                for (int y = F + 1; y <= ragged; y++) {
                    BlockState c = pierCell(m, x, y, z, dx, dz);
                    if (c != null) v.set(x, y, z, c);
                }
            }
            int inner = cx - side * 5;
            for (int z = cz - 2; z <= cz + 2; z++) {
                if (!v.in(inner, 100, z)) continue;
                v.set(inner, F + 34, z, m.timber);
                int len = 22 - (int) (rnd(z, cx, 92) * 7) - (Math.abs(z - cz) == 2 ? 2 : 0);
                for (int k = 0; k < len; k++) v.set(inner, F + 33 - k, z, m.cloth);
            }
            for (int x = cx - side * 6; x != cx - side * 8; x -= side) if (v.in(x, 100, cz)) {
                v.set(x, F + 1, cz, x == cx - side * 7 ? m.stoneA : m.pale);
                if (x == cx - side * 7) {
                    v.set(x, F + 2, cz, m.pale);
                    v.set(x, F + 3, cz, m.brazier);
                }
            }
            rib(v, m, cx, cz, side, top);
        }

        private BlockState pierCell(M m, int x, int y, int z, int dx, int dz) {
            int h = y - F;
            int half = h <= 2 ? 6 : h <= 4 ? 5 : 4;
            boolean band1 = y >= F + 16 && y <= F + 20, band2 = y >= F + 40 && y <= F + 43;
            if (band1 || band2) half = 5;
            if (dx > half || dz > half) return null;
            if (half == 4 && dx == 4 && dz == 4) return null;
            boolean face = dx == half || dz == half;
            if (!face) return m.stoneA;
            if (band1 || band2) return Math.floorMod(x + z + y, 3) == 0 ? m.pale : m.relief;
            if (h <= 2) return h == 2 ? m.pale : m.stoneA;
            if (Math.floorMod(h, 2) == 0 && (dx == dz || dx == 3 && dz == 4 || dx == 4 && dz == 3)) return m.pale;
            return course(m, x, y, z);
        }

        /** What's left of the vault: a rib leaning out from the top of each pier, broken off in mid air. */
        private void rib(V v, M m, int cx, int cz, int side, int top) {
            int reach = 9 + Math.floorMod(cz * 3 + side * 7, 7);
            int x0 = cx - side * 4;
            for (int i = 0; i <= reach; i++) {
                int x = x0 - side * i;
                int yc = top - 4 + (int) (14 * Math.sin(Math.min(1, i / 17.0) * Math.PI / 2));
                int thick = Math.max(3, 7 - i / 3);
                for (int z = cz - 2; z <= cz + 2; z++) {
                    if (!v.in(x, 100, z)) continue;
                    for (int y = yc - thick; y <= yc; y++) {
                        boolean under = y == yc - thick;
                        v.set(x, y, z, under ? under(m.stoneStairs, side > 0 ? Direction.EAST : Direction.WEST) : Math.abs(z - cz) == 2 ? m.pale : y == yc ? m.relief : course(m, x, y, z));
                    }
                }
            }
        }

        /** The one arch that's still whole, in front of the dais, because the roots behind it are holding it up. */
        private void farArch(V v, M m) {
            for (int x = -23; x <= 23; x++) for (int z = -37; z <= -33; z++) {
                if (!v.in(x, 100, z)) continue;
                int a = Math.abs(x);
                int yi = F + 44 + (int) (32 * Math.sqrt(Math.max(0, 1 - a * a / 576.0)));
                boolean face = z == -37 || z == -33;
                for (int y = yi; y <= yi + 5; y++) v.set(x, y, z, y == yi ? (face ? m.relief : m.pale) : face && Math.floorMod(a, 6) == 0 ? m.pale : course(m, x, y, z));
            }
        }

        private void dais(V v, M m) {
            for (int x = -22; x <= 22; x++) for (int z = HALL_Z0; z <= -36; z++) {
                if (!v.in(x, 100, z)) continue;
                int a = Math.abs(x), top = z >= -43 ? F + 1 + (-36 - z) : F + 9;
                for (int y = F + 1; y < top; y++) v.set(x, y, z, m.stoneA);
                boolean run = a <= 8;
                if (z >= -43) v.set(x, top, z, run ? stair(m.paleStairs, Direction.NORTH) : Math.floorMod(z, 2) == 0 ? course(m, x, top, z) : m.relief);
                else v.set(x, top, z, a <= 6 && Math.floorMod(a + z, 4) < 2 ? m.brick : m.flag);
            }
            int fy = F + 10;
            for (int x = -5; x <= 5; x++) for (int z = -53; z <= -47; z++) {
                if (!v.in(x, 100, z)) continue;
                int a = Math.abs(x);
                if (z >= -49 && a <= 3) {
                    v.set(x, fy, z, a == 3 ? m.gold : m.pale);
                    if (a == 3) v.set(x, fy + 1, z, m.gold);
                    continue;
                }
                if (z > -50) continue;
                int tall = a <= 3 ? 20 : a == 4 ? 16 : 12;
                for (int k = 0; k < tall; k++) {
                    int y = fy + k;
                    BlockState c = a == 5 ? m.pale : k > 14 && a >= 2 ? m.relief : z == -50 && a <= 2 && k >= 2 && k <= 12 ? (Math.floorMod(k, 3) == 0 ? m.gold : a == 0 ? m.window : m.relief) : a <= 3 ? m.pale : course(m, x, y, z);
                    v.set(x, y, z, c);
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) for (int k = 0; k < 3; k++) {
                int x = sx * (9 + k * 5), z = -46;
                if (!v.in(x, 100, z)) continue;
                v.set(x, F + 10, z, m.pale);
                v.set(x, F + 11, z, m.brazier);
            }
        }

        private void rubbleHeaps(V v, M m) {
            for (int[] h : heaps) for (int x = h[0] - h[2]; x <= h[0] + h[2]; x++) for (int z = h[1] - h[2]; z <= h[1] + h[2]; z++) {
                if (!v.in(x, 100, z) || Math.abs(x) <= 19 || Math.abs(x) > HALL_X || z < -36 || z > HALL_Z1) continue;
                double d = Math.hypot(x - h[0], z - h[1]);
                if (d > h[2] + 0.5) continue;
                int top = v.groundBelow(x, 200, z);
                if (top < 0 || top > F + 3) continue;
                int t = (int) Math.round(h[3] * Math.sqrt(1 - d / (h[2] + 1)));
                for (int k = 1; k <= t; k++) v.set(x, F + k, z, k == t ? (rnd(x, z, 12) < 0.5 ? m.stoneSlab : m.drift == null ? m.worn : m.worn) : rubble(m, x, F + k, z));
            }
        }

        /** Rain and neglect: vines down from every edge that has one. */
        private void hanging(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                if (Math.abs(x) > HALL_X || z < HALL_Z0 || z > HALL_Z1) continue;
                if (Math.floorMod(x, 9) == 0 && Math.floorMod(z, 9) == 0 && Math.abs(x) >= 24 && v.get(x, F + 6, z) == null) v.set(x, F + 6, z, m.airLight);
                if (rnd(x, z, 93) > 0.09) continue;
                for (int y = F + 83; y > F + 6; y--) {
                    BlockState here = v.get(x, y, z);
                    if (here == null || !V.holds(here) || v.get(x, y - 1, z) != null) continue;
                    int len = 3 + (int) (rnd(x, z, 94) * 13);
                    for (int k = 1; k <= len && y - k > F + 1 && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.vines);
                    break;
                }
            }
        }
    }

    // ---- the way in ------------------------------------------------------------------------------------------------------------

    private void bridge() {
        pieces.add(new Bridge());
    }

    /** A long bridge of cut stone on four great piers, over the gorge to the shut gate. */
    final class Bridge extends Piece<M> {
        static final int Z0 = 186, Z1 = 280;
        final int[] sp = {204, 226, 248, 270};

        Bridge() {
            box(0, (Z0 + Z1) / 2, 14, (Z1 - Z0) / 2 + 2);
            mayOverlap = true;
            label = "bridge";
        }

        private int nearest(int z) {
            int b = sp[0];
            for (int p : sp) if (Math.abs(z - p) < Math.abs(z - b)) b = p;
            return b;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = Math.abs(x), sz = nearest(z), dz = Math.abs(z - sz);
                if (a <= 7) {
                    double arch = dz <= 3 ? 0 : Math.sqrt(Math.max(0, 1 - Math.pow((11 - dz) / 8.0, 2)));
                    int under = dz <= 3 ? BED - 2 : APRON - 7 - (int) ((1 - arch) * 26);
                    for (int y = under; y <= APRON; y++) {
                        BlockState c;
                        if (y == APRON) c = a <= 5 ? (rnd(x, z, 13) < 0.55 ? m.flag : Math.floorMod(z, 7) == 0 ? m.pale : m.hexstone) : m.pale;
                        else if (a == 7 && y == APRON - 1) c = under(m.paleStairs, x > 0 ? Direction.WEST : Direction.EAST);
                        else if (a == 7) c = dz > 3 && y >= under + 1 && y <= APRON - 3 && Math.floorMod(z, 4) < 2 ? null : null;
                        else if (y == under && dz > 3) c = m.pale;
                        else if (a == 6) c = Math.floorMod(z, 4) == 0 && y == APRON - 2 ? m.relief : course(m, x, y, z);
                        else c = m.stoneA;
                        if (c != null) v.set(x, y, z, c);
                    }
                    if (a == 6 || a == 7) parapet(v, m, x, z, a);
                }
                if (dz <= 3 + Math.max(0, 6 - a) / 2 && a <= 11) for (int y = BED - 2; y <= WATER + 9 - a / 2; y++) {
                    v.set(x, y, z, y == WATER + 9 - a / 2 ? (a >= 8 ? stair(m.stoneStairs, x > 0 ? Direction.WEST : Direction.EAST) : m.worn) : course(m, x, y, z));
                }
            }
        }

        private void parapet(V v, M m, int x, int z, int a) {
            if (a != 6) return;
            double gap = n2.get(z / 11.0, x > 0 ? 3.0 : 9.0);
            int tall = gap > 0.35 ? 0 : gap > 0.1 ? 1 : 2 + (Math.floorMod(z, 6) < 2 ? 1 : 0);
            for (int k = 1; k <= tall; k++) v.set(x, APRON + k, z, k == tall ? m.pale : course(m, x, APRON + k, z));
            if (tall > 0 && Math.floorMod(z, 24) == 8) v.set(x, APRON + tall + 1, z, m.brazier);
        }
    }

    private void forecourt() {
        pieces.add(new Arch(-52, 288));
        pieces.add(new Arch(-18, 268));
        pieces.add(new Pole(-20, 276));
        pieces.add(new Pole(22, 276));
        for (int[] p : new int[][]{{-9, 274}, {9, 274}, {-13, 292}, {13, 292}, {-24, 306}, {24, 306}, {-9, 188}, {9, 188}}) pieces.add(new Plinth(p[0], p[1]));
        for (int i = 0; i < 12; i++) {
            int x = (i % 2 == 0 ? -1 : 1) * (18 + i * 3 % 12), z = 262 + (i * 37) % 62;
            pieces.add(new Stub(x, z, 3 + i % 4));
        }
    }

    /** A wall that's lost most of itself, with one arch still in it. */
    final class Arch extends Piece<M> {
        final int cx, cz;

        Arch(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            box(cx, cz, 12, 3);
            mayOverlap = true;
            label = "ruin arch";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, a = Math.abs(dx), dz = Math.abs(z - cz);
                if (dz > 2) continue;
                int g = terrain(x, z), tall = 19 - a - (dx > 0 ? a / 2 : 0) + (int) (rnd(x, z, 14) * 3);
                for (int h = 1; h <= tall; h++) {
                    boolean open = a <= 3 && h <= 8 + (3 - a) * 1 / 1;
                    if (open) continue;
                    boolean arch = a <= 4 && h == 9 + (4 - a);
                    v.set(x, g + h, z, arch ? m.pale : dz == 2 && Math.floorMod(h, 4) == 0 ? m.pale : course(m, x, g + h, z));
                }
            }
        }
    }

    final class Pole extends Piece<M> {
        final int cx, cz;

        Pole(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            box(cx, cz, 7, 3);
            mayOverlap = true;
            label = "banner pole";
        }

        @Override
        public void draw(V v, M m) {
            int g = terrain(cx, cz);
            for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
                int a = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                for (int h = 1; h <= 3 - a; h++) v.set(x, g + h, z, a == 0 ? m.stoneA : m.pale);
            }
            for (int h = 3; h <= 30; h++) v.set(cx, g + h, cz, m.timber);
            for (int k = 1; k <= 3; k++) v.set(cx, g + 30 + k, cz, m.gold);
            int dir = cx < 0 ? -1 : 1;
            for (int t = 0; t <= 6; t++) {
                int x = cx + dir * t;
                if (!v.in(x, 100, cz)) continue;
                v.set(x, g + 27, cz, m.timber);
                if (t == 0) continue;
                int l = 19 - (int) (rnd(t, cz, 15) * 8) - t / 2;
                for (int k = 1; k <= l; k++) v.set(x, g + 27 - k, cz, m.cloth);
            }
        }
    }

    /** A fire on a stepped block of stone. */
    final class Plinth extends Piece<M> {
        final int cx, cz;

        Plinth(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            box(cx, cz, 2, 2);
            mayOverlap = true;
            label = "fire plinth";
        }

        @Override
        public void draw(V v, M m) {
            int g = ground(cx, cz);
            for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
                int a = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                for (int h = 1; h <= 3 - a; h++) v.set(x, g + h, z, a == 2 ? stair(m.stoneStairs, Math.abs(x - cx) == 2 ? (x > cx ? Direction.WEST : Direction.EAST) : (z > cz ? Direction.NORTH : Direction.SOUTH)) : a == 1 ? m.pale : m.relief);
            }
            v.set(cx, g + 4, cz, m.brazier);
        }
    }

    /** The bottom few courses of something that's gone. */
    final class Stub extends Piece<M> {
        final int cx, cz, r;

        Stub(int cx, int cz, int r) {
            this.cx = cx;
            this.cz = cz;
            this.r = r;
            box(cx, cz, r + 1, 2);
            mayOverlap = true;
            label = "wall stub";
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                if (Math.abs(z - cz) > 1) continue;
                int g = terrain(x, z), tall = 1 + (int) (rnd(x, cz, 16) * 3) + (r + 1 - Math.abs(x - cx)) / 2;
                for (int h = 1; h <= tall; h++) v.set(x, g + h, z, h == tall && rnd(x, z, 17) < 0.5 ? m.worn : course(m, x, g + h, z));
            }
        }
    }

    // ---- the roots and the trees ----------------------------------------------------------------------------------------------

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
                        boolean mossy = dy > r * 0.3 && d2 > (r - 1.3) * (r - 1.3) && n2.get(x / 5.0, (y + z) / 5.0) > 0.05;
                        v.set(x, y, z, mossy ? m.barkMoss : m.bark);
                    }
            }
        }
    }

    final class Crown extends Piece<M> {
        final List<double[]> lobes = new ArrayList<>();
        final int reach;
        int lowY = 999, highY = 0, longest = 16;

        Crown(int cx, int cz, int reach) {
            this.reach = reach;
            box(cx, cz, reach, reach);
            mayOverlap = true;
            label = "crown";
        }

        void lobe(double x, double y, double z, double r) {
            lobes.add(new double[]{x, y, z, r});
            lowY = Math.min(lowY, (int) (y - r));
            highY = Math.max(highY, (int) (y + r));
        }

        private boolean leaf(BlockState s, M m) {
            return s == m.leaf || s == m.leafLit || s == m.leafDeep;
        }

        @Override
        public void draw(V v, M m) {
            int vx0 = v.x0 - 1, vx1 = v.x0 + 16, vz0 = v.z0 - 1, vz1 = v.z0 + 16;
            for (double[] l : lobes) {
                double r = l[3], ry = r * 0.42;
                if (l[0] + r < vx0 || l[0] - r > vx1 || l[2] + r < vz0 || l[2] - r > vz1) continue;
                for (int x = Math.max((int) (l[0] - r), vx0); x <= Math.min((int) (l[0] + r), vx1); x++) for (int z = Math.max((int) (l[2] - r), vz0); z <= Math.min((int) (l[2] + r), vz1); z++)
                    for (int y = (int) (l[1] - ry); y <= (int) (l[1] + ry); y++) {
                        double dx = (x - l[0]) / r, dy = (y - l[1]) / ry, dz = (z - l[2]) / r;
                        double d = dx * dx + dy * dy * (dy < 0 ? 1.5 : 1) + dz * dz;
                        double wob = n3.get(x / 7.0 + y / 9.0, z / 7.0 - y / 9.0) * 0.45;
                        if (d > 1 + wob) continue;
                        if (y < l[1] - ry * 0.4 && rnd(x * 7 + y, z, 81) < 0.2) continue;
                        if (v.get(x, y, z) != null) continue;
                        double tone = dy * 0.9 + n3.get(x / 5.0 + y / 6.0, z / 5.0 - y / 6.0) * 0.55;
                        v.set(x, y, z, tone > 0.38 ? m.leafLit : tone < -0.32 ? m.leafDeep : m.leaf);
                    }
            }
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                if (rnd(x, z, 87) > 0.07) continue;
                for (int y = highY; y >= lowY; y--) {
                    if (!leaf(v.get(x, y, z), m) || v.get(x, y - 1, z) != null) continue;
                    int len = 3 + (int) (rnd(x, z, 88) * longest);
                    for (int k = 1; k <= len && v.get(x, y - k, z) == null; k++) v.set(x, y - k, z, m.vines);
                    break;
                }
            }
        }
    }

    private void tube(double[][] k, String what) {
        pieces.add(new Tube(k, 10, what));
    }

    /** Roots as thick as a body down each side of the net, so a breach reads as the place the jungle came in. */
    private void breachRoots(Ring ring, int foot, int walk, Breach b) {
        Direction in = ring.in[ring.nearest(b.cx + 0.5, b.cz + 0.5)];
        int ax = b.alongX ? 1 : 0, az = b.alongX ? 0 : 1, top = foot + walk - b.depth + 3;
        for (int sg = -1; sg <= 1; sg += 2) for (int i = 0; i < 2; i++) {
            int off = sg * (7 + i * 3);
            double px = b.cx + ax * off + in.getStepX() * 1.4, pz = b.cz + az * off + in.getStepZ() * 1.4;
            double ox = -in.getStepX(), oz = -in.getStepZ();
            tube(new double[][]{{px + in.getStepX() * 3, top + 4 + i * 3, pz + in.getStepZ() * 3, 2.6}, {px + ax * sg, top - 4, pz + az * sg, 2.3}, {px + ax * sg * 2 + ox, top - (top - foot) / 2.0, pz + az * sg * 2 + oz, 2.1}, {px + ox * 3 + ax * sg * 2, foot + 2, pz + oz * 3 + az * sg * 2, 2.8}}, "breach root");
        }
    }

    /** A great root poured from the top of the keep, over everything between there and the river. */
    private void roots() {
        for (Breach b : List.of(new Breach(-100, 150, true, 11, 10), new Breach(104, 150, true, 11, 10))) breachRoots(ring1, FOOT1, WALK1, b);
        for (Breach b : List.of(new Breach(-70, 84, true, 13, 14), new Breach(92, 30, false, 13, 14))) breachRoots(ring2, FOOT2, WALK2, b);
        tube(new double[][]{{-18, 250, 46, 7.5}, {-18, 226, 57, 7}, {-20, 196, 62, 6.5}, {-24, 172, 66, 6}, {-36, 155, 74, 5.2}, {-58, 155, 79, 4.8}, {-82, 171, 84, 4.6}, {-84, 138, 96, 4.4}, {-90, 110, 114, 4}, {-104, 108, 130, 3.6}, {-110, 124, 146, 3.2}, {-111, 118, 157, 2.8}, {-116, 102, 172, 2.2}}, "great root");
        tube(new double[][]{{24, 250, 46, 7.2}, {24, 222, 57, 6.6}, {28, 194, 62, 6.2}, {36, 172, 66, 5.6}, {52, 155, 76, 5}, {70, 174, 86, 4.8}, {76, 138, 98, 4.4}, {82, 110, 116, 4}, {92, 108, 134, 3.6}, {95, 125, 148, 3.2}, {93, 118, 158, 2.8}, {96, 102, 172, 2.2}}, "great root");
        tube(new double[][]{{34, 250, 30, 6.2}, {49, 224, 34, 5.6}, {56, 194, 38, 5}, {64, 172, 42, 4.4}, {76, 156, 42, 4}, {91, 174, 45, 3.8}, {104, 136, 46, 3.4}, {122, 108, 58, 3}}, "great root");
        tube(new double[][]{{-34, 250, 22, 6.2}, {-49, 224, 18, 5.4}, {-56, 196, 8, 4.6}, {-66, 172, -8, 4.2}, {-78, 156, -28, 3.6}, {-96, 174, -40, 3.2}, {-108, 130, -46, 3}, {-128, 108, -56, 2.6}}, "great root");
        tube(new double[][]{{6, 250, 46, 5.4}, {4, 220, 56, 4.6}, {-2, 200, 60, 3.6}, {-6, 192, 58, 2.4}}, "root");
        tube(new double[][]{{46, 240, -30, 4.6}, {50, 210, -34, 4}, {56, 192, -44, 3}}, "root");
        // the roots behind the throne: two pairs wound round each other all the way down the wall
        for (int p = 0; p < 2; p++) for (int i = 0; i < 2; i++) {
            List<double[]> k = new ArrayList<>();
            double side = p == 0 ? -1 : 1, spread = 11;
            for (int s = 0; s <= 9; s++) {
                double y = F + 72 - s * 6.4, phi = s * 0.72 + i * Math.PI;
                k.add(new double[]{side * spread + Math.cos(phi) * 4.6, y, -52 + Math.sin(phi) * 1.8, 2.6});
            }
            k.add(new double[]{side * (spread + 5 + i * 4), F + 14, -44 + i, 2.3});
            k.add(new double[]{side * (spread + 8 + i * 5), F + 11, -38 + i * 2, 1.6});
            tube(k.toArray(new double[0][]), "throne root");
        }
        tree(-43, 20, CROWN - 4, 26);
        tree(43, -34, CROWN - 4, 22);
        for (int[] t : stacks) if (t[3] > 90) {
            double top = 60 + t[3];
            tree2(t[0], t[1], (int) top - 2, 18 + t[2] / 2);
        }
        for (int[] p : new int[][]{{-74, 290}, {66, 306}, {-98, 340}, {96, 350}}) tree2(p[0], p[1], terrain(p[0], p[1]) - 1, 26);
    }

    private void tree(int x, int z, int y, int reach) {
        Crown c = new Crown(x, z, reach + 20);
        pieces.add(new Tube(new double[][]{{x, y - 12, z, 5}, {x, y + 10, z, 4.2}, {x + 3, y + 28, z - 2, 3}}, 10, "keep tree"));
        for (int i = 0; i < 7; i++) {
            double a = i * 2.399963, ex = x + Math.cos(a) * reach, ez = z + Math.sin(a) * reach, ey = y + 32 + jit(5);
            pieces.add(new Tube(new double[][]{{x + 3, y + 24, z - 2, 2.4}, {ex, ey, ez, 1.3}}, 36, "bough"));
            c.lobe(ex, ey + 2, ez, 15 + jit(4));
        }
        c.lobe(x, y + 36, z, reach * 0.8);
        pieces.add(c);
    }

    /** One of the flat-topped jungle giants that stand round the gorge. */
    private void tree2(int x, int z, int gy, int reach) {
        Crown c = new Crown(x, z, reach + 20);
        c.longest = 10;
        int h = 34 + (int) (rnd(x, z, 18) * 22);
        pieces.add(new Tube(new double[][]{{x, gy - 4, z, 5.5}, {x, gy + 12, z, 3.8}, {x + 2, gy + h, z - 1, 2.6}}, 10, "giant trunk"));
        for (int i = 0; i < 7; i++) {
            double a = i * 2.399963 + jit(0.2), ex = x + Math.cos(a) * reach, ez = z + Math.sin(a) * reach, ey = gy + h + 6 + jit(4);
            pieces.add(new Tube(new double[][]{{x + 2, gy + h - 3, z - 1, 2.4}, {ex, ey, ez, 1.2}}, 36, "bough"));
            c.lobe(ex, ey + 2, ez, 13 + jit(4));
        }
        c.lobe(x, gy + h + 10, z, reach * 0.74);
        pieces.add(c);
    }

    private void groundCover() {
        for (int i = 0; i < 260; i++) {
            double a = i * 2.399963 + jit(0.5), d = 150 + i * 1.1 + jit(30);
            int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
            if (Math.abs(x) < 34 && z > 150) continue;
            int g = terrain(x, z);
            if (ring1.dist(x + 0.5, z + 0.5) < 80 || g <= WATER + 1 || g > 118) continue;
            place(new Tree(x, z, 11 + (i * 7) % 15, seed + i, this));
        }
        for (int[] t : new int[][]{{-120, 96}, {-116, 40}, {-118, -72}, {-40, -128}, {64, -118}, {124, -30}, {128, 12}, {-68, 126}, {-120, 136}, {30, 124}}) place(new Tree(t[0], t[1], 16 + (t[0] & 7), seed + t[1], this));
        for (int[] t : new int[][]{{-72, 40}, {-70, -30}, {70, -60}, {66, 20}, {-30, 76}, {34, 72}}) place(new Tree(t[0], t[1], 12 + (t[0] & 5), seed + t[1], this));
    }

    // ---- who's here, and what's worth finding -------------------------------------------------------------------------------

    private void village() {
        int[][] huts = {{112, 118, 6}, {128, 96, 6}, {104, 96, 5}, {122, 134, 5}, {132, 118, 5}, {112, 76, 5}, {128, 66, 4}};
        for (int[] h : huts) pieces.add(new Hut(h[0], h[1], h[2], Math.atan2(104 - h[1], 118 - h[0]), seed + h[0], this));
        for (int[] s : new int[][]{{112, 106}, {124, 108}, {118, 92}}) pieces.add(new Stall(s[0], s[1], seed + s[0], this));
    }

    private void placeSites() {
        site(SiteKind.MOUTH, 0, APRON + 1, 298, 12, "");
        site(SiteKind.PORTAL, 0, APRON + 1, 304, 2, "");
        pieces.add(new MouthRing(0, APRON, 301, this));
        site(SiteKind.LORD, 0, F + 1, 8, 42, "");
        site(SiteKind.WARDEN, 0, FOOT1 + WALK1 + 1, 158, 14, "gate");
        site(SiteKind.WARDEN, 0, INNER + 1, 72, 14, "court");
        site(SiteKind.WARDEN, 50, SETBACK + 1, -6, 8, "battlement");
        site(SiteKind.VILLAGE, 118, ground(118, 104) + 1, 104, 34, "");
        for (int[] d : new int[][]{{-118, 110}, {-116, 58}, {-118, -48}, {-20, -118}, {40, -124}, {124, 48}, {132, -50}, {-30, 122}}) site(SiteKind.DEN, d[0], ground(d[0], d[1]) + 1, d[1], 10, "");
        waystone(10, 288, "the old bridge");
        waystone(-74, 124, "the outer ward");
        waystone(-70, 58, "the inner ward");
        int[][] low = {{-126, 120}, {-124, 20}, {-128, -100}, {-60, -130}, {20, -138}, {90, -128}, {128, -70}, {128, 26}, {60, 126}, {-40, 134}, {-74, 20}, {70, 40}, {-60, -80}};
        int i = 0;
        for (int[] f : low) {
            int gy = ground(f[0], f[1]);
            pieces.add(new Find(f[0], gy, f[1], seed + i++));
            site(SiteKind.FIND, f[0], gy + 1, f[1], 2, "");
        }
        int[][] high = {{-100, FOOT1 + WALK1, 145}, {145, FOOT1 + WALK1, -60}, {-70, FOOT2 + WALK2, -86}};
        for (int[] f : high) {
            pieces.add(new Find(f[0], f[1], f[2], seed + i++));
            site(SiteKind.FIND, f[0], f[1] + 1, f[2], 2, "high");
        }
    }
}
