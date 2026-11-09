package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.check.LookChecks;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.N.Hex;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static dev.awaken.gates.realms.kit.N.fbm;
import static dev.awaken.gates.realms.kit.N.hex;
import static dev.awaken.gates.realms.kit.N.hexMetric;
import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.rnd;
import static dev.awaken.gates.realms.kit.N.smooth;
import static dev.awaken.gates.realms.kit.N.sq;

/**
 * Amber Reach (SPEC §50.8): a golden valley with a honey river, a wax-folk village and a citadel you can see from the moment
 * you arrive, walled in by stepped hex cliffs. Everything stands on what was really drawn under it.
 */
public final class AmberReach extends Land {
    static final int VILLAGE_Y = 69, CIT_Y = 66, WATER = 62;
    /** Past this distance from the valley's middle the rock wall has risen too high to climb. */
    static final double WALL = 560;

    private final SimplexNoise n1, n2, n3, n4, n5, n6, n7, n9;
    final double cu, cv, vu, vv, plazaU, plazaV;
    private final double[] ru, rv;
    private final double[][] mesas;
    private final double[][] road;
    final List<P> pieces = new ArrayList<>();
    private final List<double[]> ways = new ArrayList<>();
    private final Random r;
    final long seed;

    public AmberReach(Ctx ctx) {
        super(ctx);
        seed = ctx.seed();
        r = new Random(seed ^ 0x416D626572L);
        n1 = N.noise(seed + 101);
        n2 = N.noise(seed + 102);
        n3 = N.noise(seed + 103);
        n4 = N.noise(seed + 104);
        n5 = N.noise(seed + 105);
        n6 = N.noise(seed + 106);
        n7 = N.noise(seed + 107);
        n9 = N.noise(seed + 109);
        cu = 372 + jit(10);
        cv = 92 + jit(16);
        vu = 116 + jit(10);
        vv = 58 + jit(8);
        plazaU = vu + 2;
        plazaV = vv - 19; // its steps come down to the road's edge, never across it
        double[][] rk = {{-40, -96 + jit(10)}, {45, -46 + jit(14)}, {95, -16 + jit(10)}, {150, 8 + jit(8)}, {205, 32 + jit(10)},
                {258, 10 + jit(10)}, {282, 40 + jit(10)}, {292, 82 + jit(6)}};
        int seg = 36, n = rk.length;
        ru = new double[(n - 1) * seg + 1];
        rv = new double[ru.length];
        for (int i = 0; i < n - 1; i++) for (int s = 0; s <= seg; s++) {
            double[] a = rk[Math.max(0, i - 1)], b = rk[i], c = rk[i + 1], d = rk[Math.min(n - 1, i + 2)];
            double t = s / (double) seg, t2 = t * t, t3 = t2 * t;
            ru[i * seg + s] = 0.5 * (2 * b[0] + (c[0] - a[0]) * t + (2 * a[0] - 5 * b[0] + 4 * c[0] - d[0]) * t2 + (3 * b[0] - a[0] - 3 * c[0] + d[0]) * t3);
            rv[i * seg + s] = 0.5 * (2 * b[1] + (c[1] - a[1]) * t + (2 * a[1] - 5 * b[1] + 4 * c[1] - d[1]) * t2 + (3 * b[1] - a[1] - 3 * c[1] + d[1]) * t3);
        }
        double[][] base = {{196, 200, 76, 60}, {124, 180, 46, 44}, {240, -120, 68, 54}, {156, -152, 44, 36}, {322, -100, 48, 64},
                {440, 214, 70, 90}, {78, -180, 34, 28}, {462, -60, 62, 100}, {300, 262, 56, 76}};
        mesas = new double[base.length][];
        for (int i = 0; i < base.length; i++) {
            double[] b = base[i];
            mesas[i] = new double[]{b[0] + jit(18), b[1] + jit(18), b[2] + jit(8), b[3] + jit(8)};
        }
        road = new double[][]{{0, 0}, {30, 8}, {60, 24}, {vu - 22, vv - 2}, {vu + 44, vv + 4}, {cu - 138 - 44, cv}, {cu - 118, cv}};
        for (int i = 0; i + 1 < road.length; i++) ways.add(new double[]{road[i][0], road[i][1], road[i + 1][0], road[i + 1][1], 3.0});
        build();
    }

    private double jit(double d) {
        return (r.nextDouble() - 0.5) * 2 * d;
    }

    // ---- the land ----------------------------------------------------------------------------------------------------------

    /** Distance to the river's bank, negative in the water. */
    double riverD(double u, double v) {
        double best = 1e12;
        int at = 0;
        for (int i = 0; i < ru.length; i += 3) {
            double d = sq(u - ru[i]) + sq(v - rv[i]);
            if (d < best) {
                best = d;
                at = i;
            }
        }
        double t = at / (double) (ru.length - 1);
        return Math.sqrt(best) - (lerp(17, 6, t) + 2.4 * n2.get(u / 26.0, v / 26.0));
    }

    double villageD(double u, double v) {
        return Math.hypot(u - vu, (v - vv) * 1.05) - 62 - 8 * n6.get(u / 40.0, v / 40.0);
    }

    double roadD(double u, double v) {
        double best = 1e9;
        for (int i = 0; i + 1 < road.length; i++) {
            double ax = road[i][0], az = road[i][1], bx = road[i + 1][0], bz = road[i + 1][1];
            double dx = bx - ax, dz = bz - az, t = Math.max(0, Math.min(1, ((u - ax) * dx + (v - az) * dz) / (dx * dx + dz * dz)));
            best = Math.min(best, Math.hypot(u - ax - dx * t, v - az - dz * t));
        }
        return best;
    }

    double heightOf(double u, double v, double d) {
        double low = 5 * fbm(n1, u, v, 1 / 150.0, 3), hi = 1.4 * fbm(n3, u, v, 1 / 18.0, 2);
        double y = 65 + 7 * smooth(2, 46, d) + (low + hi) * smooth(3, 50, d);
        y += 38 * smooth(52, 6, Math.hypot(u + 6, v * 0.55)) + 1.2 * n4.get(u / 11.0, v / 11.0) * smooth(70, 30, Math.hypot(u, v));
        double c = Math.hypot(u - 190, v * 1.15);
        y += 78 * Math.pow(smooth(230, 520, c), 1.3) * (0.6 + 0.4 * fbm(n5, u, v, 1 / 210.0, 3));
        // the valley's wall: stepped cliffs too high to scale, with the high country lost in haze behind them
        y += 110 * smooth(WALL - 40, WALL + 60, c);
        y = lerp(y, VILLAGE_Y, smooth(18, -2, villageD(u, v)));
        y = lerp(y, CIT_Y, smooth(172, 128, Math.hypot(u - cu, v - cv)));
        if (d < 4) y = lerp(58.5, Math.max(y, WATER + 1.2), smooth(-5, 4, d));
        return y;
    }

    /** The exact top block of the ground in a column: every pass and piece stands on this. */
    @Override
    public int ground(int x, int z) {
        double d = riverD(x, z);
        return column(x, z, d, null);
    }

    /** The top of a column; when {@code cap} is given, also what the top block is. */
    private int column(int x, int z, double d, BlockState[] cap) {
        M m = cap == null ? null : m();
        int y = (int) Math.floor(heightOf(x, z, d));
        double dv = villageD(x, z), dc = Math.hypot(x - cu, z - cv), dr = roadD(x, z);
        boolean road = dr < 3.4 && d > 4;
        boolean paved = (dv < 1 && d > 2) || (dc < 140 && dc > 0) || road;
        if (d < 0) {
            if (cap != null) cap[0] = rnd(x >> 2, z >> 2, 3 + seed) < 0.16 ? m.deep : m.bed; // dark under the honey, so it reads as deep
        } else if (d < 5) {
            Hex t = hex(x, z, 2.2);
            boolean edge = t.edge() > 0.74;
            if (edge) y--;
            else if (rnd(t.q(), t.r(), 11 + seed) < 0.3) y++;
            if (cap != null) {
                double k = rnd(t.q(), t.r(), 9 + seed);
                cap[0] = edge ? m.pollen : k < 0.45 ? m.hexstone : k < 0.8 ? m.pale : m.worn;
            }
        } else if (paved) {
            if (cap != null) {
                // laid flags with worn patches and a few hex-paved courts; the eye should see a street, not a grid
                double court = n3.get(x / 24.0, z / 24.0), wear = n1.get(x / 7.0, z / 7.0), k = rnd(x, z, 41 + seed);
                cap[0] = court > 0.42 ? m.hexstone : wear > 0.5 && k < 0.6 ? m.worn : wear < -0.55 && k < 0.5 ? m.moss : k < 0.06 ? m.pale : m.flag;
            }
        } else {
            double soil = fbm(n9, x, z, 1 / 13.0, 2);
            if (cap != null) {
                cap[0] = m.moss;
                if (Math.hypot(x, z) > 60) {
                    if (soil > 0.5) cap[0] = m.coarse;
                    else if (soil < -0.52) cap[0] = m.rooted;
                }
            }
            if (n3.get(x / 5.0, z / 5.0) > 0.5) y++;
        }
        return y;
    }

    @Override
    public int biome(int x, int y, int z) {
        return Math.hypot(x - cu, z - cv) < 150 ? 1 : 0;
    }

    @Override
    public int halfSize() {
        return 1024;
    }

    @Override
    public float mouthYaw() {
        return (float) Math.toDegrees(Math.atan2(-(cu - 0), cv - 0));
    }

    @Override
    public List<? extends Piece<?>> pieces() {
        return List.copyOf(pieces);
    }

    @Override
    public List<double[]> ways() {
        return List.copyOf(ways);
    }

    // ---- painting -----------------------------------------------------------------------------------------------------------

    @Override
    public void paint(V v) {
        M m = m();
        int[] gy = new int[V.W * V.W];
        BlockState[] cap = new BlockState[1];
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            double d = riverD(x, z);
            int y = column(x, z, d, cap);
            double dv = villageD(x, z), dc = Math.hypot(x - cu, z - cv);
            boolean paved = (dv < 1 && d > 2) || (dc < 140 && dc > 0) || (roadD(x, z) < 3.4 && d > 4);
            int si = (x - v.x0 + 1) * V.W + z - v.z0 + 1;
            // the land goes all the way down, so nothing below the world's floor can be seen through a cut
            for (int k = 1; k < y; k++) v.set(x, k, z, k < y - 22 ? m.rock : y - k < 5 ? (paved ? m.pale : m.worn) : m.deep);
            v.set(x, 0, z, m.bedrock);
            v.set(x, y, z, cap[0]);
            if (y < WATER) for (int k = y + 1; k <= WATER; k++) v.set(x, k, z, m.water);
            gy[si] = y;
            mesa(v, m, x, z, y);
        }
        for (P p : pieces) if (p.hits(v)) p.draw(v, m);
        shade(v, m);
        growth(v, m, gy);
    }

    // hex-cell mesas, built in stacked courses so no face is one flat plane
    private void mesa(V v, M m, int x, int z, int ground) {
        Hex h = hex(x, z, 7.5);
        double best = 0;
        for (double[] g : mesas) {
            double k = smooth(g[2], g[2] * 0.5, Math.hypot(h.cx() - g[0], h.cz() - g[1])) * g[3];
            if (k > best) best = k;
        }
        best *= smooth(150, 190, Math.hypot(h.cx() - cu, h.cz() - cv));
        // the valley's wall is more of the same, and it never stops
        double c = Math.hypot(h.cx() - 190, h.cz() * 1.15);
        double wall = 120 * smooth(WALL - 110, WALL - 10, c);
        if (wall > best) best = wall;
        if (best < 3 || roadD(h.cx(), h.cz()) < 14) return;
        double base = heightOf(h.cx(), h.cz(), riverD(h.cx(), h.cz()));
        int top = (int) (base + best * (0.55 + 0.62 * rnd(h.q(), h.r(), 5 + seed)));
        long id = h.q() * 31L + h.r();
        double tone = rnd(id, 1, 21 + seed);
        BlockState body = tone < 0.45 ? m.stoneA : tone < 0.75 ? m.worn : m.pale;
        // the gaps between columns are shallow slots, not holes down to bedrock
        boolean slot = h.edge() > 0.97;
        int ceil = slot ? Math.min(top, ground + 2) : top;
        for (int y = Math.max(ground - 2, 1); y <= ceil; y++) {
            BlockState s = body;
            double up = (y - ground) / Math.max(1.0, top - ground);
            if (up < 0.22) s = n5.get(x / 5.0, y / 4.0, z / 5.0) > -0.1 ? m.deep : body;
            else if (up > 0.86 && h.edge() > 0.7) s = m.pale;
            if (rnd(id, Math.floorDiv(y, 11), 30 + seed) < 0.09 && Math.floorMod(y, 11) == 5 && h.edge() > 0.8) s = m.glow;
            if (y == top) s = h.edge() > 0.75 ? m.pale : m.pollen;
            v.set(x, y, z, s);
        }
    }

    /** Pass four: a painter's shadows, only under real overhangs, and the small things that drip and crumble. */
    private void shade(V v, M m) {
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) for (int y = 56; y < 345; y++) {
            BlockState s = v.get(x, y, z);
            if (s != m.stoneA && s != m.worn && s != m.pale && s != m.pale) continue;
            for (int[] d : side) {
                if (v.get(x + d[0], y, z + d[1]) != null) continue;
                // only a real ledge above, two deep, darkens the wall under it
                if (v.solid(x + d[0], y + 1, z + d[1]) && v.solid(x + d[0] * 2, y + 1, z + d[1] * 2)) {
                    v.set(x, y, z, m.deep);
                    break;
                }
            }
            double wet = n7.get(x / 7.0, z / 7.0);
            if (v.get(x, y - 1, z) == null && y > 70 && wet > 0.1 && rnd(x, z + y, 51 + seed) < 0.5) {
                v.set(x, y - 1, z, m.drip);
                if (wet > 0.5 && v.get(x, y - 2, z) == null) v.set(x, y - 2, z, m.drip);
            }
            if (v.get(x, y + 1, z) == null && rnd(x, z + y, 52 + seed) < 0.05) v.set(x, y + 1, z, m.crumbs);
        }
    }

    /** Pass six: what grows, and only ever on the ground that is really there. */
    private void growth(V v, M m, int[] gy) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            int y = gy[(x - v.x0 + 1) * V.W + z - v.z0 + 1];
            BlockState s = v.get(x, y, z);
            if (s != m.moss && s != m.coarse && s != m.rooted) continue;
            if (v.get(x, y + 1, z) != null) continue;
            double p = fbm(n7, x, z, 1 / 11.0, 2), q = rnd(x, z, 77 + seed);
            if (s != m.moss) {
                if (q < 0.08) v.set(x, y + 1, z, m.deadBush);
                continue;
            }
            if (p > -0.05) {
                if (q < 0.5) v.set(x, y + 1, z, m.wheat);
                else if (q < 0.74) tall(v, x, y, z, m);
                else if (q < 0.9) v.set(x, y + 1, z, m.wheatTall);
            } else if (q < 0.42) {
                tall(v, x, y, z, m);
            } else if (q < 0.6) v.set(x, y + 1, z, m.amberGrass);
            else if (q < 0.72) v.set(x, y + 1, z, m.amberGrass);
            else if (q < 0.74) v.set(x, y + 1, z, m.bloom);
            else if (q < 0.76) v.set(x, y + 1, z, m.petals);
        }
    }

    private static void tall(V v, int x, int y, int z, M m) {
        v.set(x, y + 1, z, m.wheatTall);
    }

    // ---- what stands in it --------------------------------------------------------------------------------------------------

    static final int[] PR = {112, 96, 80, 64};

    /** Everything stands where it's clear: not through another piece, not on a road, a stair or a door's way (SPEC §50.6). */
    private boolean place(P p) {
        if (LookChecks.clash(p, pieces, ways) != null) return false;
        pieces.add(p);
        return true;
    }

    private void build() {
        int mx = -6, mz = 0;
        int my = ground(mx, mz);
        site(SiteKind.MOUTH, mx, my + 1, mz, 12, "");
        site(SiteKind.PORTAL, mx - 6, my + 1, mz, 2, "");
        pieces.add(new MouthRing(mx, my, mz));
        site(SiteKind.WAYSTONE, mx + 12, ground(mx + 12, mz + 6) + 1, mz + 6, 4, "the Mouth");
        citadel();
        village();
        flowers();
        for (int i = 0; i < 9; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = 16 + r.nextDouble() * 50;
            int x = (int) (mx + Math.cos(a) * rr), z = (int) (mz + Math.sin(a) * rr);
            if (roadD(x, z) < 8) continue;
            place(new Boulder(x, z, 3 + r.nextInt(4), 1 + r.nextInt(3), i, this));
        }
        if (roadD(mx + 20, mz - 22) > 9) pieces.add(new AmberTree(mx + 20, mz - 22, 26, 1, this));
        if (roadD(mx + 16, mz + 30) > 9) pieces.add(new AmberTree(mx + 16, mz + 30, 22, 2, this));
        placeSites();
    }

    private void placeSites() {
        // Wardens hold the land in any order; dens and finds are where the land already invites you
        double[][] wardens = {{170, -86}, {226, 150}, {330, -40}, {60, 170}, {470, 140}};
        String[] kinds = {"the_swarm_mind", "the_swarm_mind", "the_swarm_mind", "the_swarm_mind", "the_swarm_mind"};
        int want = ctx.bp().sites().wardens();
        for (int i = 0; i < Math.min(want, wardens.length); i++) {
            int x = (int) wardens[i][0], z = (int) wardens[i][1];
            site(SiteKind.WARDEN, x, ground(x, z) + 1, z, 18, kinds[i]);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * 2.399963 + jit(0.4), rr = 60 + i * 26;
            int x = (int) (190 + Math.cos(a) * rr), z = (int) (Math.sin(a) * rr * 0.86);
            if (Math.hypot(x - cu, z - cv) < 160 || villageD(x, z) < 16 || riverD(x, z) < 6) continue;
            site(SiteKind.DEN, x, ground(x, z) + 1, z, 10, "");
        }
        int finds = ctx.bp().sites().finds();
        for (int i = 0; i < finds * 3 && sites(SiteKind.FIND).size() < finds; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = 30 + r.nextDouble() * 420;
            int x = (int) (190 + Math.cos(a) * rr), z = (int) (Math.sin(a) * rr);
            if (Math.hypot(x - cu, z - cv) < 150 || villageD(x, z) < 4 || riverD(x, z) < 3 || roadD(x, z) < 5) continue;
            site(SiteKind.FIND, x, ground(x, z) + 1, z, 2, "");
        }
        site(SiteKind.VILLAGE, (int) vu, VILLAGE_Y + 1, (int) vv, 62, "");
        site(SiteKind.WAYSTONE, (int) plazaU - 18, VILLAGE_Y + 1, (int) plazaV + 12, 4, "the village");
        int cx = (int) cu, cz = (int) cv;
        site(SiteKind.WAYSTONE, cx - 112 - 18, ground(cx - 112 - 18, cz + 22) + 1, cz + 22, 4, "the citadel's foot");
        site(SiteKind.LORD, cx, CIT_Y + 41, cz, 44, ctx.bp().heart().lord());
        // the honey wind: columns of warm air you can ride up, by the river and under the cliffs
        for (int i = 0; i < 7; i++) {
            double t = (i + 0.5) / 7;
            int k = (int) (t * (ru.length - 1));
            int x = (int) ru[k] + (i % 2 == 0 ? 22 : -22), z = (int) rv[k] + (i % 3 == 0 ? 14 : -14);
            if (villageD(x, z) < 8) continue;
            site(SiteKind.UPDRAFT, x, ground(x, z), z, 3, "");
        }
    }

    private void citadel() {
        pieces.add(new Plinth(this));
        int cx = (int) cu, cz = (int) cv;
        pieces.add(new Spire(cx, cz, CIT_Y + 40, 326, 50, 12, 9, true, seed));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 6;
            pieces.add(new Spire(cx + (int) (Math.cos(a) * 60), cz + (int) (Math.sin(a) * 60), CIT_Y + 40 - 8, 214 + (int) (rnd(i, 1, 91 + seed) * 26), 14, 8, 6, false, seed));
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + 0.13;
            pieces.add(new Spire(cx + (int) (Math.cos(a) * 88), cz + (int) (Math.sin(a) * 88), CIT_Y + 20 - 2, 148 + (int) (rnd(i, 2, 92 + seed) * 32), 8.5, 8, 4, false, seed));
        }
        int foot = cx - PR[0] - 8;
        pieces.add(new Pylon(foot - 2, cz - 16, 28, this));
        pieces.add(new Pylon(foot - 2, cz + 16, 28, this));
        pieces.add(new Pylon(foot - 28, cz - 16, 14, this));
        pieces.add(new Pylon(foot - 28, cz + 16, 14, this));
        for (int i = 0; i < 12; i++) {
            place(new Lamp(foot - 34 + i * 3 - 4, cz - 11, 7, this));
            place(new Lamp(foot - 34 + i * 3 - 4, cz + 11, 7, this));
        }
    }

    private void village() {
        pieces.add(new Pavilion((int) plazaU, VILLAGE_Y + 1, (int) plazaV));
        long id = 1;
        for (int a = -7; a <= 7; a++) for (int b = -6; b <= 6; b++) {
            double px = vu + 18.5 * (a + b * 0.5) + (rnd(a, b, 101 + seed) - 0.5) * 7, pz = vv + 15.8 * b + (rnd(a, b, 102 + seed) - 0.5) * 7;
            if (villageD(px, pz) > -6 || riverD(px, pz) < 9 || roadD(px, pz) < 11) continue;
            if (Math.hypot(px - plazaU, pz - plazaV) < 23) continue;
            id++;
            double kind = rnd(a, b, 103 + seed);
            if (kind < 0.62) {
                int rr = 5 + (int) (rnd(a, b, 104 + seed) * 4);
                place(new Hut((int) px, (int) pz, rr, Math.atan2(plazaV - pz, plazaU - px), id, this));
            } else if (kind < 0.82) place(new Stall((int) px, (int) pz, id, this));
            else place(new Lamp((int) px, (int) pz, 6 + (int) (rnd(a, b, 105 + seed) * 5), this));
        }
        for (int i = 0; i < 18; i++) {
            double t = i / 17.0, ax = vu - 22, bx = vu + 44;
            double x = lerp(ax, bx, t), z = lerp(vv - 2, vv + 4, t);
            place(new Lamp((int) x, (int) z + (i % 2 == 0 ? 6 : -6), 6, this));
        }
    }

    private void flowers() {
        double[][] spots = {{160, -68, 30, 7.5, 1}, {226, -36, 26, 6, -1}, {214, 124, 40, 9, 1}, {186, 128, 34, 7.5, -1}, {250, 136, 30, 6.5, 1}, {40, -70, 22, 5.5, 1}, {96, 150, 24, 6, -1}};
        for (int i = 0; i < spots.length; i++) {
            double[] s = spots[i];
            int x = (int) (s[0] + jit(10)), z = (int) (s[1] + jit(10));
            if (roadD(x, z) < 16 || riverD(x, z) < 6) continue;
            place(new Flower(x, z, s[2] + jit(3), s[3], s[4], seed + i, this));
        }
    }

    // ---- blocks -------------------------------------------------------------------------------------------------------------

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    static BlockState top(BlockState slab) {
        return slab.setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    static BlockState stair(BlockState s, Direction f) {
        return s.setValue(StairBlock.FACING, f);
    }

    static BlockState under(BlockState s, Direction f) {
        return s.setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, Half.TOP);
    }

    static BlockState lit(BlockState s) {
        return s.getBlock().getStateDefinition().getProperty("lit") instanceof BooleanProperty p ? s.setValue(p, true) : s;
    }

    /** The land's palette, every block of it the place's own but for small fittings (fence rail, barrel, lantern, chain). */
    static final class M {
        final BlockState stoneA = b("amber_stone"), worn = b("amber_stone_worn"), deep = b("amber_stone_deep"), pale = b("amber_stone_pale"),
                hexstone = b("amber_hexstone"), flag = b("amber_flagstone"), brick = b("amber_brick"), plaster = b("amber_plaster"), band = b("amber_plaster_band"),
                tile = b("amber_tile"), tileDeep = b("amber_tile_deep"), timber = b("amber_timber"), planks = b("amber_planks"), gold = b("amber_gilt"), window = b("amber_window"),
                moss = b("amber_moss"), bed = b("amber_bed"), petal = b("amber_petal"), petalDeep = b("amber_petal_deep"), petalTip = b("amber_petal_tip"),
                seedhead = b("amber_seedhead"), stem = b("amber_stem"), leaf = b("amber_leaf"), leafGold = b("amber_leaf_gold");
        final BlockState stoneSlab = b("amber_stone_slab"), stoneStairs = b("amber_stone_stairs"), paleSlab = b("amber_stone_pale_slab"), paleStairs = b("amber_stone_pale_stairs"),
                flagSlab = b("amber_flagstone_slab"), flagStairs = b("amber_flagstone_stairs"), brickSlab = b("amber_brick_slab"), tileStairs = b("amber_tile_stairs"), tileSlab = b("amber_tile_slab"),
                tileDeepStairs = b("amber_tile_deep_stairs"), tileDeepSlab = b("amber_tile_deep_slab"), planksSlab = b("amber_planks_slab"), planksStairs = b("amber_planks_stairs"), plasterSlab = b("amber_plaster_slab");
        final BlockState wheat = b("amber_wheat"), wheatTall = b("amber_wheat_tall"), amberGrass = b("amber_grass"), bloom = b("amber_bloom");
        final BlockState comb = b("hive_comb"), capped = b("hive_comb_capped"), glow = b("hive_honey_glow"), pollen = b("hive_pollen"), royal = b("hive_royal"),
                drip = b("hive_drip"), candle = lit(b("hive_candle")), petals = b("hive_petals"), crumbs = b("hive_wax_crumbs"), drum = b("hive_drum");
        final BlockState water = Blocks.WATER.defaultBlockState(), coarse = Blocks.COARSE_DIRT.defaultBlockState(), rooted = Blocks.ROOTED_DIRT.defaultBlockState(),
                rock = Blocks.STONE.defaultBlockState(), bedrock = Blocks.BEDROCK.defaultBlockState(), shroom = Blocks.SHROOMLIGHT.defaultBlockState(),
                chain = Blocks.IRON_CHAIN.defaultBlockState(), lantern = Blocks.LANTERN.defaultBlockState(), hung = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true),
                fence = Blocks.DARK_OAK_FENCE.defaultBlockState(), barrel = Blocks.BARREL.defaultBlockState(), light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15),
                deadBush = Blocks.DEAD_BUSH.defaultBlockState();
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    abstract static class P extends Piece<M> {}

    // ---- the Mouth ----------------------------------------------------------------------------------------------------------

    /** Where you arrive: a ring of old standing stones on the hill, every one on its own footing, the view left open ahead. */
    static final class MouthRing extends P {
        final int cx, gy, cz;

        MouthRing(int cx, int gy, int cz) {
            this.cx = cx;
            this.gy = gy;
            this.cz = cz;
            box(cx, cz, 13, 13);
            startsWay = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double d = hexMetric(x - cx, z - cz);
                if (d > 9) continue;
                int top = gy;
                for (int y = top + 1; y < top + 6; y++) v.set(x, y, z, null);
                v.foot(x, top - 1, z, m.pale);
                v.set(x, top, z, d > 7.6 ? m.stoneA : ((x + z) & 1) == 0 ? m.flag : m.pale);
            }
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 6 + i * Math.PI / 3;
                int sx = cx + (int) Math.round(Math.cos(a) * 9), sz = cz + (int) Math.round(Math.sin(a) * 9);
                if (Math.cos(a) > 0.6) continue;
                int h = 5 + (i & 1) * 2;
                for (int y = 1; y <= h; y++) {
                    v.set(sx, gy + y, sz, y == h ? m.deep : m.pale);
                    if (y <= 2) for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) v.set(sx + d[0], gy + y, sz + d[1], y == 2 ? m.flagSlab : m.pale);
                }
                v.foot(sx, gy, sz, m.pale);
                v.set(sx, gy + h + 1, sz, m.candle);
            }
            // the arch home, behind where you arrive; the opening itself is lit the first time someone comes through
            int ax = cx - 6;
            for (int s = -3; s <= 3; s++) for (int y = 1; y <= 7; y++) {
                int a = Math.abs(s);
                boolean jamb = a >= 2 && y <= 5, lintel = y == 4 + (3 - a) && a < 3, cap = y == 5 + (3 - a) && a < 2;
                if (a == 3 && y > 3 || !jamb && !lintel && !cap && a != 3) continue;
                BlockState b = a == 3 ? (y == 3 ? m.deep : m.stoneA) : lintel || cap ? (y >= 6 ? m.gold : m.deep) : y == 1 || y == 5 ? m.stoneA : m.pale;
                v.set(ax, gy + y, cz + s, b);
                v.foot(ax, gy, cz + s, m.pale);
                if (a >= 2 && y == 1) {
                    v.set(ax - 1, gy + y, cz + s, m.flagSlab);
                    v.set(ax + 1, gy + y, cz + s, m.flagSlab);
                }
            }
            v.set(ax, gy + 8, cz, m.candle);
        }
    }

    // ---- flowers, trees, stones ---------------------------------------------------------------------------------------------

    static final class Flower extends P {
        final double bx, by, bz, h, R, side;
        final long seed;

        Flower(int x, int z, double h, double R, double side, long seed, AmberReach land) {
            bx = x;
            bz = z;
            by = land.ground(x, z) - 2; // rooted in the ground, not set on top of a number
            this.h = h;
            this.R = R;
            this.side = side;
            this.seed = seed;
            box(x, z, (int) (R + 14 + h * 0.3), (int) (R + 14 + h * 0.3));
        }

        private void ball(V v, double x, double y, double z, double r, BlockState s) {
            int a = (int) Math.ceil(r);
            for (int i = -a; i <= a; i++) for (int j = -a; j <= a; j++) for (int k = -a; k <= a; k++)
                if (i * i + j * j + k * k <= r * r + 0.3) v.set((int) Math.floor(x) + i, (int) Math.floor(y) + j, (int) Math.floor(z) + k, s);
        }

        @Override
        public void draw(V v, M m) {
            double tx = bx + side * 0.15 * h, tz = bz + side * 0.28 * h, ty = by + h;
            double px = bx - side * 0.14 * h, pz = bz + side * 0.04 * h, py = by + h * 0.55;
            int steps = (int) (h * 5);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps, a = (1 - t) * (1 - t), b = 2 * t * (1 - t), c = t * t;
                double x = a * bx + b * px + c * tx, y = a * by + b * py + c * ty, z = a * bz + b * pz + c * tz;
                ball(v, x, y, z, lerp(2.0, 1.1, t), m.stem);
                if (i % (steps / 5) == steps / 10 && t < 0.86) {
                    double dir = rnd(i, seed, 4) * 6.283;
                    double lx = Math.cos(dir), lz = Math.sin(dir), len = h * (0.2 + 0.08 * rnd(i, seed, 5));
                    for (double s = 0; s <= 1; s += 0.04) {
                        double sx = x + lx * s * len, sz = z + lz * s * len, sy = y + (s * (1 - s) * 7 - s * s * 3);
                        double w = Math.sin(s * Math.PI) * 1.9;
                        ball(v, sx, sy, sz, 0.55 + w * 0.65, rnd((long) (s * 40), seed, 6) < 0.3 ? m.stem : m.leaf);
                    }
                }
            }
            double nx = -0.7, ny = 0.6, nz = -side * 0.25, l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= l;
            ny /= l;
            nz /= l;
            double e1x = -nz, e1z = nx, e1l = Math.hypot(e1x, e1z);
            e1x /= e1l;
            e1z /= e1l;
            double e2x = ny * e1z, e2y = nz * e1x - nx * e1z, e2z = -ny * e1x;
            double phase = rnd(seed, 1, 2) * 6.283;
            ball(v, tx, ty, tz, 2.4, m.seedhead);
            for (int layer = 0; layer < 2; layer++) {
                int petals = layer == 0 ? 15 : 12;
                double RR = layer == 0 ? R : R * 0.74, off = layer == 0 ? -1.4 : 0;
                for (double rho = 0; rho <= RR; rho += 0.42) {
                    int arcs = Math.max(8, (int) (rho * 6.283 / 0.42));
                    for (int j = 0; j < arcs; j++) {
                        double th = j * 6.283 / arcs, q = rho / RR;
                        double pt = Math.abs(Math.sin(th * petals / 2 + phase + layer * 0.6));
                        if (q > 0.42 + 0.58 * Math.pow(pt, 0.55)) continue;
                        double bowl = -q * q * R * 0.42 + off;
                        double x = tx + rho * (Math.cos(th) * e1x + Math.sin(th) * e2x) + nx * bowl;
                        double y = ty + rho * (Math.sin(th) * e2y) + ny * bowl;
                        double z = tz + rho * (Math.cos(th) * e1z + Math.sin(th) * e2z) + nz * bowl;
                        BlockState s;
                        if (layer == 1 && q < 0.3) s = rnd((long) (x * 3), (long) (z * 3), 7) < 0.3 ? m.deep : m.seedhead;
                        else if (layer == 1 && q < 0.44) s = m.petalDeep;
                        else {
                            double tone = q + 0.16 * (rnd((long) (x * 2), (long) (y * 2), 8) - 0.5);
                            s = tone < 0.55 ? m.petalDeep : tone < 0.8 ? m.petal : m.petalTip;
                        }
                        v.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), s);
                    }
                }
            }
        }
    }

    /** A gnarled old tree, grown from the ground, that frames the view from the hill. */
    static final class AmberTree extends P {
        final int x, z, gy, h;
        final long seed;

        AmberTree(int x, int z, int h, long seed, AmberReach land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.seed = seed + land.seed;
            gy = land.ground(x, z);
            box(x, z, h, h);
            mayOverlap = true;
        }

        private void limb(V v, M m, double ax, double ay, double az, double bx, double by, double bz, double r0, double r1) {
            int n = (int) (Math.hypot(bx - ax, Math.hypot(by - ay, bz - az)) * 2) + 1;
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n, r = lerp(r0, r1, t);
                double cx = lerp(ax, bx, t), cy = lerp(ay, by, t), cz = lerp(az, bz, t);
                int a = (int) Math.ceil(r);
                for (int p = -a; p <= a; p++) for (int q = -a; q <= a; q++) for (int s = -a; s <= a; s++)
                    if (p * p + q * q + s * s <= r * r + 0.25) v.set((int) Math.floor(cx) + p, (int) Math.floor(cy) + q, (int) Math.floor(cz) + s, m.timber);
            }
        }

        private void leaves(V v, M m, double cx, double cy, double cz, double r) {
            int a = (int) Math.ceil(r);
            for (int p = -a; p <= a; p++) for (int q = -a / 2; q <= a / 2; q++) for (int s = -a; s <= a; s++) {
                double d = p * p + q * q * 3.2 + s * s;
                if (d > r * r || rnd(p + (long) cx, s + (long) cz, q + seed) < 0.18) continue;
                int X = (int) Math.floor(cx) + p, Y = (int) Math.floor(cy) + q, Z = (int) Math.floor(cz) + s;
                if (v.get(X, Y, Z) == null) v.set(X, Y, Z, rnd(X, Z, seed + 2) < 0.34 ? m.petal : m.leafGold);
            }
        }

        @Override
        public void draw(V v, M m) {
            limb(v, m, x, gy - 2, z, x + 1, gy + h * 0.55, z - 1, 2.2, 1.2);
            int boughs = 5;
            for (int i = 0; i < boughs; i++) {
                double a = i * 2.399 + rnd(seed, i, 1) * 0.8, len = h * (0.34 + 0.2 * rnd(seed, i, 2)), up = h * (0.1 + 0.22 * rnd(seed, i, 3));
                double sx = x + 1, sy = gy + h * (0.42 + 0.12 * i / boughs), sz = z - 1, ex = sx + Math.cos(a) * len, ez = sz + Math.sin(a) * len, ey = sy + up;
                limb(v, m, sx, sy, sz, ex, ey, ez, 1.1, 0.5);
                leaves(v, m, ex, ey + 1, ez, 6 + 3 * rnd(seed, i, 4));
            }
            leaves(v, m, x + 1, gy + h * 0.72, z - 1, 8);
        }
    }

    static final class Boulder extends P {
        final int x, y, z, r, h;
        final long seed;

        Boulder(int x, int z, int r, int h, long seed, AmberReach land) {
            this.x = x;
            this.y = land.ground(x, z);
            this.z = z;
            this.r = r;
            this.h = h;
            this.seed = seed;
            box(x, z, r + 1, r + 1);
        }

        @Override
        public void draw(V v, M m) {
            for (int a = ax(v); a <= bx(v); a++) for (int c = az(v); c <= bz(v); c++) {
                double hd = hexMetric(a - x, c - z);
                if (hd > r) continue;
                int top = y + h - (hd > r - 1 ? 1 : 0);
                for (int k = y - 2; k <= top; k++) v.set(a, k, c, k == top ? (hd > r - 1.4 ? m.pale : m.stoneA) : k % 3 == 0 ? m.deep : m.stoneA);
                v.foot(a, y - 3, c, m.deep);
                if (hd <= r - 1 && hd > r - 2.2) v.set(a, top + 1, c, m.stoneSlab);
                for (int k = top + 2; k < top + 5; k++) v.set(a, k, c, null);
            }
        }
    }

    // ---- the village --------------------------------------------------------------------------------------------------------

    /** A six-sided wax-folk house with a stepped dome; warm walls, small glowing windows, a real room inside. */
    static final class Hut extends P {
        final int cx, gy, cz, r, wall, dome;
        final double door;
        final long seed;

        Hut(int cx, int cz, int r, double door, long seed, AmberReach land) {
            this.cx = cx;
            this.cz = cz;
            this.gy = land.ground(cx, cz);
            this.r = r;
            this.door = door;
            this.seed = seed;
            wall = 4 + r / 3;
            dome = r + 1;
            box(cx, cz, r + 3, r + 3);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x - cx, dz = z - cz, hd = hexMetric(dx, dz), a = Math.atan2(dz, dx);
                double sec = Math.PI / 3, s = Math.floor(a / sec + 0.5), phi = a - s * sec, t = Math.hypot(dx, dz) * Math.sin(phi);
                double side = r * 1.155 / 2;
                boolean corner = Math.abs(t) > side - 1.3;
                if (hd > r + 2.6) continue;
                // a plinth that goes down to whatever is really under it
                for (int y = gy + 1; y < gy + wall + dome + 6; y++) if (hd <= r + 1.3) v.set(x, y, z, null);
                v.foot(x, gy - 1, z, m.pale);
                v.set(x, gy, z, hd > r + 1.3 ? m.paleSlab : m.pale);
                if (hd > r + 1.2) continue;
                if (hd <= r + 1.3) v.set(x, gy + 1, z, hd > r ? m.paleSlab : m.planks);
                for (int y = gy + 2; y <= gy + wall; y++) {
                    int ly = y - gy - 1;
                    if (hd > r) continue;
                    if (hd > r - 1.4) {
                        BlockState b = corner ? m.timber : ly <= 1 ? m.brick : m.plaster;
                        boolean doorSide = Math.abs(((a - door + Math.PI * 3) % (Math.PI * 2)) - Math.PI) < 0.45;
                        if (doorSide && Math.abs(t) < 1.6 && ly <= 3) {
                            v.set(x, y, z, null);
                            continue;
                        }
                        if (!corner && Math.abs(t) < 0.9 && ly >= 2 && ly <= 3) b = m.window;
                        else if (!corner && Math.abs(t) < 1.9 && ly >= 1 && ly <= 4) b = m.timber;
                        else if (ly == wall - 2 && !corner) b = m.band;
                        v.set(x, y, z, b);
                    } else v.set(x, y, z, null);
                }
                int cy = gy + wall + 1;
                if (hd <= r + 1.2) v.set(x, cy, z, hd > r ? m.planksSlab : m.timber);
                if (hd > r + 0.2) continue;
                for (int y = cy + 1; y <= cy + dome; y++) {
                    double h = (y - cy) / (double) dome;
                    double rr = r * Math.sqrt(Math.max(0, 1 - h * h));
                    if (hd > rr) continue;
                    if (hd > rr - 1.7 || h > 0.7) {
                        BlockState b = (y & 1) == 0 ? m.tileDeep : m.tile;
                        if (corner && h < 0.85) b = m.pale;
                        if (h > 0.25 && h < 0.5 && !corner && Math.abs(t) < 1) b = m.window;
                        v.set(x, y, z, b);
                    }
                }
                if (hd < 1.2) {
                    v.set(x, cy + dome + 1, z, m.gold);
                    v.set(x, cy + dome + 2, z, m.candle);
                }
            }
            v.set(cx, gy + 3, cz, m.light);
            v.set(cx, gy + 2, cz, m.drum);
            v.set(cx, gy + 3, cz, m.candle);
        }
    }

    static final class Pavilion extends P {
        final int cx, gy, cz;

        Pavilion(int cx, int gy, int cz) {
            this.cx = cx;
            this.gy = gy;
            this.cz = cz;
            box(cx, cz, 16, 16);
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, dz = z - cz, c = Math.max(Math.abs(dx), Math.abs(dz));
                if (c <= 15) v.foot(x, gy - 1, z, m.pale);
                for (int k = 0; k < 3; k++) if (c <= 15 - k * 2) v.set(x, gy + k, z, k == 2 && c < 11 ? c % 4 == 0 ? m.pale : m.hexstone : c == 15 - k * 2 ? m.pale : m.flag);
                boolean post = c == 11 && Math.abs(dx) % 11 == 0 && Math.abs(dz) % 11 == 0;
                if (post) {
                    for (int y = gy + 3; y <= gy + 15; y++) v.set(x, y, z, y == gy + 3 || y == gy + 12 ? m.pale : m.timber);
                    v.set(x, gy + 11, z, m.gold);
                }
                if (c == 11 && !post) {
                    v.set(x, gy + 11, z, ((dx + dz) & 3) == 0 ? m.gold : m.band);
                    // the frieze that carries the roof: a band, turned posts, a band
                    v.set(x, gy + 12, z, m.planks);
                    if (((dx + dz) & 1) == 0) {
                        v.set(x, gy + 13, z, m.fence);
                        v.set(x, gy + 14, z, m.fence);
                    }
                    v.set(x, gy + 15, z, m.planks);
                }
                for (int tier = 0; tier < 3; tier++) {
                    int base = gy + 12 + tier * 5, e = 15 - tier * 4;
                    for (int ring = 0; ring < 5; ring++) {
                        int er = e - ring, y = base + ring;
                        if (c != er) continue;
                        boolean ridge = Math.abs(dx) == Math.abs(dz);
                        Direction f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                        v.set(x, y, z, ridge ? m.pale : stair((ring & 1) == 0 ? m.tileDeepStairs : m.tileStairs, f));
                        if (ring == 0) v.set(x, y - 1, z, ridge ? m.gold : under(m.planksStairs, f.getOpposite()));
                    }
                    if (c < e - 4) for (int y = base; y < base + 5; y++) v.set(x, y, z, y == base + 4 ? (tier == 2 ? m.tileDeep : m.planks) : null);
                }
            }
            for (int s = -1; s <= 1; s += 2) for (int t = -1; t <= 1; t += 2) {
                v.set(cx + s * 11, gy + 10, cz + t * 11 - t, m.chain);
                v.set(cx + s * 11, gy + 9, cz + t * 11 - t, m.hung);
            }
            for (int a = -5; a <= 5; a += 2) for (int c = -5; c <= 5; c += 2) if (Math.abs(a) + Math.abs(c) < 8) {
                v.set(cx + a, gy + 3, cz + c, m.drum);
                if (Math.abs(a) + Math.abs(c) < 3) v.set(cx + a, gy + 4, cz + c, m.candle);
            }
            for (int y = gy + 9; y <= gy + 15; y++) v.set(cx, y, cz, m.chain);
            v.set(cx, gy + 8, cz, m.hung);
        }
    }

    /** A road or village lamp: a post on its own footing, from the real ground, wherever that turned out to be. */
    static final class Lamp extends P {
        final int x, y, z, h;

        Lamp(int x, int z, int h, AmberReach land) {
            this.x = x;
            this.z = z;
            this.h = h;
            y = land.ground(x, z);
            box(x, z, 1, 1);
        }

        @Override
        public void draw(V v, M m) {
            v.foot(x, y, z, m.pale);
            v.set(x, y + 1, z, m.deep);
            for (int i = 2; i < h; i++) v.set(x, y + i, z, m.fence);
            v.set(x, y + h, z, m.deep);
            for (int d = -1; d <= 1; d += 2) {
                v.set(x + d, y + h, z, m.fence);
                v.set(x + d, y + h - 1, z, m.hung);
                v.set(x, y + h, z + d, m.fence);
                v.set(x, y + h - 1, z + d, m.hung);
            }
            v.set(x, y + h + 1, z, m.glow);
            v.set(x, y + h + 2, z, m.stoneSlab);
        }
    }

    /** A market stall, or a heap of a family's stores; every stack starts from the ground. */
    static final class Stall extends P {
        final int x, y, z;
        final long seed;

        Stall(int x, int z, long seed, AmberReach land) {
            this.x = x;
            this.z = z;
            this.seed = seed;
            y = land.ground(x, z);
            box(x, z, 5, 5);
        }

        private void post(V v, M m, int a, int c, int top) {
            v.foot(x + a, y, z + c, m.pale);
            for (int k = 1; k <= top; k++) v.set(x + a, y + k, z + c, m.fence);
        }

        @Override
        public void draw(V v, M m) {
            int kind = (int) (rnd(seed, 1, 1) * 4);
            if (kind == 0) {
                for (int a = -3; a <= 3; a += 6) for (int c = -2; c <= 2; c += 4) post(v, m, a, c, 4);
                for (int a = -3; a <= 3; a++) for (int c = -2; c <= 2; c++) v.set(x + a, y + 5, z + c, (c & 2) == 0 ? (a & 1) == 0 ? m.tileSlab : m.tileDeepSlab : (a & 1) == 0 ? m.planksSlab : m.tileSlab);
                for (int a = -2; a <= 2; a++) {
                    v.foot(x + a, y, z, m.pale);
                    v.set(x + a, y + 1, z, m.barrel);
                    v.set(x + a, y + 2, z, (a & 1) == 0 ? m.drum : m.candle);
                }
                v.set(x, y + 4, z, m.hung);
            } else {
                for (int i = 0; i < 9; i++) {
                    int a = (int) (rnd(seed, i, 2) * 8) - 4, c = (int) (rnd(seed, i, 3) * 8) - 4, tall = (int) (rnd(seed, i, 4) * 3);
                    BlockState s = rnd(seed, i, 5) < 0.4 ? m.drum : rnd(seed, i, 5) < 0.75 ? m.barrel : m.planks;
                    v.foot(x + a, y, z + c, m.pale);
                    for (int k = 1; k <= tall + 1; k++) v.set(x + a, y + k, z + c, s);
                    if (rnd(seed, i, 6) < 0.5) v.set(x + a, y + tall + 2, z + c, m.candle);
                }
            }
        }
    }

    // ---- the citadel: a stepped plinth, a tower of arcaded tiers, a crowd of lesser spires ------------------------------------

    static final class Plinth extends P {
        final int cx, cz;
        final int[] pt = {CIT_Y + 10, CIT_Y + 20, CIT_Y + 30, CIT_Y + 40};
        final long seed;
        final SimplexNoise n5;

        Plinth(AmberReach land) {
            cx = (int) land.cu;
            cz = (int) land.cv;
            seed = land.seed;
            n5 = land.n5;
            box(cx, cz, 138, 138);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            double sec = Math.PI / 3;
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x - cx, dz = z - cz, rho = Math.hypot(dx, dz), a = Math.atan2(dz, dx), s = Math.floor(a / sec + 0.5), phi = a - s * sec;
                double d = rho * Math.cos(phi), u = rho * Math.sin(phi);
                if (d > PR[0] + 11) continue;
                boolean stair = Math.abs(u) <= 9, flank = Math.abs(u) <= 11.5 && !stair;
                double sy = CIT_Y + (PR[0] + 8 - d) / (PR[0] + 8.0 - PR[3]) * 40;
                if (stair && d > PR[3] - 1) {
                    int y = (int) Math.floor(sy);
                    for (int k = CIT_Y - 2; k <= y; k++) v.set(x, k, z, m.deep);
                    v.foot(x, CIT_Y - 3, z, m.deep);
                    boolean cardinal = Math.abs(Math.abs(s) - 3) < 0.5 || Math.abs(s) < 0.5;
                    boolean nosing = Math.floor(CIT_Y + (PR[0] + 8 - (d + 1)) / (PR[0] + 8.0 - PR[3]) * 40) < y;
                    BlockState tread = ((int) d & 1) == 0 ? m.stoneA : m.pale;
                    if (nosing && cardinal) tread = stair(m.stoneStairs, Math.abs(s) < 0.5 ? Direction.WEST : Direction.EAST);
                    v.set(x, y, z, tread);
                    continue;
                }
                if (flank && d > PR[3] - 1) {
                    int y = (int) Math.floor(sy) + 3;
                    for (int k = Math.max(CIT_Y - 2, y - 12); k <= y; k++) v.set(x, k, z, k >= y - 1 ? m.deep : m.stoneA);
                    v.foot(x, Math.max(CIT_Y - 2, y - 12) - 1, z, m.deep);
                    if (((int) d & 7) == 0) {
                        v.set(x, y + 1, z, m.fence);
                        v.set(x, y + 2, z, m.lantern);
                    }
                    continue;
                }
                int level = -1;
                for (int i = 0; i < 4; i++) if (d <= PR[i]) level = i;
                if (level < 0) continue;
                int top = pt[level];
                int y0 = level == 0 ? CIT_Y - 2 : pt[level - 1] - 2;
                double face = PR[level];
                if (d <= face + 2) v.foot(x, y0 - 1, z, m.deep);
                for (int y = y0; y <= top; y++) {
                    double prow = y >= top - 1 ? 1.6 : y == y0 + 2 ? 0.8 : 0;
                    Hex h = hex(u + level * 7.1, y * 1.0, 5.4);
                    double e = h.edge();
                    double kind = rnd(h.q(), h.r() * 13L + level, 61 + seed);
                    double rec = e > 0.72 ? 0 : (0.72 - e) / 0.72 * (kind < 0.45 ? 4.6 : 3.2);
                    boolean niche = false;
                    double nu = Math.floorMod((int) Math.floor(u + 400), 30) - 15;
                    int ly = y - y0;
                    if (top - y0 >= 9 && Math.abs(nu) < 6) {
                        double hw = ly < 6 ? 5 : 5 * (1 - sq((ly - 6) / 6.0));
                        if (ly >= 1 && hw > 0 && Math.abs(nu) < hw) {
                            niche = true;
                            rec = 7;
                        }
                    }
                    if (y >= top - 1) rec = 0;
                    if (d > face + prow - rec) continue;
                    boolean surface = d > face + prow - rec - 2.6;
                    if (!surface && y < top) continue;
                    BlockState b;
                    if (y == top) b = paver(m, x, z, seed);
                    else if (y >= top - 1) b = ((int) u & 1) == 0 ? m.deep : m.gold;
                    else if (niche) b = m.glow;
                    else if (e > 0.72) b = n5.get(x / 15.0, y / 11.0, z / 15.0) > 0.42 ? m.deep : (y & 3) == 0 ? m.capped : m.stoneA;
                    else b = kind < 0.16 ? m.glow : kind < 0.45 ? m.comb : kind < 0.8 ? m.capped : m.worn;
                    v.set(x, y, z, b);
                }
                if (d > face - 1.6 && d < face + 0.6 && !flank) {
                    int post = Math.floorMod((int) Math.floor(u + 400), 8);
                    v.set(x, top + 1, z, post == 0 ? m.deep : m.brickSlab);
                    if (post == 0) {
                        v.set(x, top + 2, z, m.stoneSlab);
                        if (Math.floorMod((int) Math.floor(u + 400), 24) == 0) {
                            v.set(x, top + 2, z, m.fence);
                            v.set(x, top + 3, z, m.lantern);
                        }
                    }
                }
            }
        }

        private static BlockState paver(M m, int x, int z, long seed) {
            Hex t = hex(x, z, 3);
            if (t.edge() > 0.76) return m.stoneA;
            double k = rnd(t.q(), t.r(), 71 + seed);
            return k < 0.4 ? m.pale : k < 0.7 ? m.flag : m.worn;
        }
    }

    static final class Pylon extends P {
        final int cx, cz, y0, h;

        Pylon(int cx, int cz, int h, AmberReach land) {
            this.cx = cx;
            this.cz = cz;
            this.h = h;
            y0 = land.ground(cx, cz) + 1;
            box(cx, cz, 6, 6);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int a = Math.abs(x - cx), c = Math.abs(z - cz), e = Math.max(a, c);
                if (e <= 5) v.foot(x, y0 - 1, z, m.deep);
                for (int y = y0; y <= y0 + h; y++) {
                    int ly = y - y0, r = ly < 2 ? 5 : ly < 4 ? 4 : ly > h - 3 ? 5 : 3;
                    if (e > r) continue;
                    if (e < r - 1 && ly < h) continue;
                    boolean corner = a == c;
                    boolean panel = !corner && e == r && Math.min(a, c) < 2 && ly % 8 > 1 && ly % 8 < 7 && ly > 3 && ly < h - 3;
                    v.set(x, y, z, ly >= h - 2 ? m.deep : corner ? m.gold : panel ? m.glow : (ly & 3) == 0 ? m.deep : m.stoneA);
                }
                if (e <= 2) v.set(x, y0 + h + 1, z, e == 2 ? m.fence : m.shroom);
                if (e == 0) v.set(x, y0 + h + 2, z, m.shroom);
                else if (e == 2 && a == c) v.set(x, y0 + h + 2, z, m.fence);
                if (e <= 2) v.set(x, y0 + h + 3, z, e == 2 ? m.brickSlab : m.deep);
            }
        }
    }

    static final class Spire extends P {
        final int cx, cz, y0, y1, sides, tiers;
        final double R0;
        final boolean main;
        final long seed;

        Spire(int cx, int cz, int y0, int y1, double R0, int sides, int tiers, boolean main, long seed) {
            this.cx = cx;
            this.cz = cz;
            this.y0 = y0;
            this.y1 = y1;
            this.R0 = R0;
            this.sides = sides;
            this.tiers = tiers;
            this.main = main;
            this.seed = seed;
            box(cx, cz, (int) (R0 + 10), (int) (R0 + 10));
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            double sec = 2 * Math.PI / sides, tan = Math.tan(sec / 2), tierH = (y1 - y0) / (double) tiers;
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x - cx, dz = z - cz, rho = Math.hypot(dx, dz);
                if (rho > R0 * 1.15 + 6) continue;
                double a = Math.atan2(dz, dx), s = Math.floor(a / sec + 0.5), phi = a - s * sec, d = rho * Math.cos(phi), u = rho * Math.sin(phi);
                boolean gateSide = main && Math.abs(Math.abs(s) - sides / 2.0) < 0.5;
                for (int y = y0; y <= y1; y++) {
                    double t = (y - y0) / (double) (y1 - y0), tk = t * tiers;
                    int k = Math.min(tiers - 1, (int) Math.floor(tk));
                    double f = tk - k;
                    double Rb = R0 * Math.pow(1 - k / (double) tiers, 1.22), Rt = R0 * Math.pow(1 - (k + 1) / (double) tiers, 1.22) * 1.08 + 0.4;
                    double Rf = lerp(Rb, Rt, f);
                    if (Rf < 2.2) {
                        if (rho <= Math.max(0.7, Rf)) v.set(x, y, z, y > y1 - 4 ? m.gold : (y & 3) == 0 ? m.royal : m.stoneA);
                        continue;
                    }
                    if (d > Rf + 4) continue;
                    double side = 2 * Rf * tan;
                    if (Math.abs(u) > side / 2 + 3) continue;
                    double ly = f * tierH, bandTop = 3.5;
                    double proud = 0, recess = 0;
                    BlockState b;
                    int bays = Math.max(1, (int) Math.round(side / (Rf > 26 ? 13 : 9)));
                    double bw = side / bays, ub = Math.floorMod((long) Math.floor((u + side / 2) * 16), (long) Math.ceil(bw * 16)) / 16.0 - bw / 2;
                    int bay = (int) Math.floor((u + side / 2) / bw);
                    boolean cornice = ly > tierH - bandTop, plinthBand = ly < 2.2;
                    if (cornice) {
                        double c = (ly - (tierH - bandTop)) / bandTop;
                        proud = lerp(0.6, 3.2, c);
                        boolean tooth = ((int) Math.floor(u + 300) & 1) == 0;
                        if (c > 0.36 && c < 0.7 && !tooth) proud -= 1.6;
                        b = c > 0.72 ? m.gold : c > 0.36 ? m.deep : m.stoneA;
                    } else if (plinthBand) {
                        proud = 1.2;
                        b = m.deep;
                    } else {
                        int rows = tierH > 40 ? 2 : 1;
                        double rowH = (tierH - bandTop - 2.2) / rows, yy = ly - 2.2;
                        int row = Math.min(rows - 1, (int) Math.floor(yy / rowH));
                        double yf = yy - row * rowH, top = rowH - 2.5, spring = top * 0.6, aw = bw / 2 - 2;
                        double hw = yf < spring ? aw : aw * (1 - Math.pow((yf - spring) / (top - spring), 1.5));
                        boolean opening = yf > 1.5 && yf < top && hw > 0.3 && Math.abs(ub) < hw;
                        boolean hood = yf > 0.5 && yf < top + 1.2 && Math.abs(ub) < Math.max(hw, 0.4) + 1.2 && !opening;
                        boolean rib = Math.abs(ub) > bw / 2 - 1.3;
                        if (Math.abs(u) > side / 2 - 2.2) {
                            proud = 1.8;
                            b = (y & 3) == 0 ? m.capped : m.stoneA;
                        } else if (rib) {
                            proud = 1.6;
                            b = (y & 7) == 0 ? m.gold : (int) (rnd(x >> 2, y >> 3, 7 + seed) * 3) == 0 ? m.deep : m.stoneA;
                        } else if (opening) {
                            recess = Rf > 12 ? 3.5 : 1.6;
                            boolean mullion = Math.abs(ub) < 0.55 || (int) Math.floor(yf) % 6 == 5;
                            double h = rnd(bay + 7L * k, row + 31L * (long) s, 81 + seed);
                            b = mullion ? m.deep : h < 0.62 ? m.glow : h < 0.82 ? m.comb : m.window;
                        } else if (hood) {
                            proud = 0.8;
                            b = m.pale;
                        } else {
                            Hex c = hex(u + bay * 1.7, y, 2.5);
                            recess = c.edge() > 0.7 ? 0 : 0.9;
                            b = c.edge() > 0.7 ? m.stoneA : rnd(c.q(), c.r(), 83 + seed) < 0.3 ? m.comb : m.capped;
                        }
                    }
                    if (gateSide && y < y0 + 52 && !cornice) {
                        double top = 52, spring = 26, ga = 13;
                        double gy = y - y0, hw = gy < spring ? ga : ga * (1 - Math.pow((gy - spring) / (top - spring), 1.5));
                        if (hw > 0 && Math.abs(u) < hw + 3) {
                            boolean inside = Math.abs(u) < hw;
                            int order = (int) Math.floor(hw + 3 - Math.abs(u));
                            if (inside) {
                                recess = 9;
                                proud = 0;
                                b = Math.abs(u) < 0.8 || (int) gy % 7 == 6 ? m.deep : m.glow;
                            } else {
                                proud = 2.5 - (order & 1);
                                recess = 0;
                                b = (order & 1) == 0 ? m.gold : m.deep;
                            }
                        }
                    }
                    double surf = Rf + proud - recess;
                    if (d > surf || d < surf - 3.4) continue;
                    if (Math.abs(u) > d * tan + 0.6) continue;
                    v.set(x, y, z, b);
                }
            }
        }
    }
}
