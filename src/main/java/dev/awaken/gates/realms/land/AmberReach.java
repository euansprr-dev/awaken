package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.kit.KitLand;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.N.Hex;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.River;
import dev.awaken.gates.realms.kit.Road;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.piece.Find;
import dev.awaken.gates.realms.kit.piece.Boulder;
import dev.awaken.gates.realms.kit.piece.Hut;
import dev.awaken.gates.realms.kit.piece.Lamp;
import dev.awaken.gates.realms.kit.piece.MouthRing;
import dev.awaken.gates.realms.kit.piece.Pavilion;
import dev.awaken.gates.realms.kit.piece.Pylon;
import dev.awaken.gates.realms.kit.piece.Stall;
import dev.awaken.gates.realms.kit.piece.Tree;
import dev.awaken.gates.realms.kit.piece.Updraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.Build.lit;
import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.Build.top;
import static dev.awaken.gates.realms.kit.Build.under;
import static dev.awaken.gates.realms.kit.N.fbm;
import static dev.awaken.gates.realms.kit.N.hex;
import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.rnd;
import static dev.awaken.gates.realms.kit.N.smooth;
import static dev.awaken.gates.realms.kit.N.sq;

/**
 * Amber Reach (SPEC §50.8): a golden valley with a honey river, a wax-folk village and a citadel you can see from the moment
 * you arrive, walled in by stepped hex cliffs. Everything stands on what was really drawn under it.
 */
public final class AmberReach extends KitLand<AmberReach.M> {
    static final int VILLAGE_Y = 69, CIT_Y = 66, WATER = 62;
    /** Past this distance from the valley's middle the rock wall has risen too high to climb. */
    static final double WALL = 560;

    private final SimplexNoise n1, n2, n3, n4, n5, n6, n7, n9;
    final double cu, cv, vu, vv, plazaU, plazaV;
    private final River river;
    private final double[][] mesas;
    private final Road road;

    public AmberReach(Ctx ctx) {
        super(ctx, 0x416D626572L);
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
        river = new River(rk, 36, n2, 17, 6);
        double[][] base = {{196, 200, 76, 60}, {124, 180, 46, 44}, {240, -120, 68, 54}, {156, -152, 44, 36}, {322, -100, 48, 64},
                {440, 214, 70, 90}, {78, -180, 34, 28}, {462, -60, 62, 100}, {300, 262, 56, 76}};
        mesas = new double[base.length][];
        for (int i = 0; i < base.length; i++) {
            double[] b = base[i];
            mesas[i] = new double[]{b[0] + jit(18), b[1] + jit(18), b[2] + jit(8), b[3] + jit(8)};
        }
        road = new Road(new double[][]{{0, 0}, {30, 8}, {60, 24}, {vu - 22, vv - 2}, {vu + 44, vv + 4}, {cu - 138 - 44, cv}, {cu - 118, cv}});
        road.addWays(ways, 3.0);
        build();
    }

    // ---- the land ----------------------------------------------------------------------------------------------------------

    /** Distance to the river's bank, negative in the water. */
    double riverD(double u, double v) {
        return river.d(u, v);
    }

    double villageD(double u, double v) {
        return Math.hypot(u - vu, (v - vv) * 1.05) - 62 - 8 * n6.get(u / 40.0, v / 40.0);
    }

    double roadD(double u, double v) {
        return road.d(u, v);
    }

    double heightOf(double u, double v, double d) {
        // the land lies gentler along the road, so you can see down the valley from where you walk
        double calm = lerp(0.5, 1.0, smooth(12, 56, roadD(u, v)));
        double low = 5 * calm * fbm(n1, u, v, 1 / 150.0, 3), hi = 1.4 * calm * fbm(n3, u, v, 1 / 18.0, 2);
        double y = 65 + 7 * smooth(2, 46, d) + (low + hi) * smooth(3, 50, d);
        y += 38 * smooth(52, 6, Math.hypot(u + 6, v * 0.55)) + 1.2 * n4.get(u / 11.0, v / 11.0) * smooth(70, 30, Math.hypot(u, v));
        double c = Math.hypot(u - 190, v * 1.15);
        y += 78 * Math.pow(smooth(230, 520, c), 1.3) * (0.6 + 0.4 * fbm(n5, u, v, 1 / 210.0, 3));
        // the valley's wall: stepped cliffs too high to scale, with the high country lost in haze behind them
        y += 58 * smooth(WALL - 40, WALL + 60, c);
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
                // moss only finds a way in along the edges, or the crop grows out of the middle of the square
                boolean rim = road ? dr > 2.6 : dc < 140 ? dc > 128 : dv > 0.82;
                cap[0] = court > 0.42 ? m.hexstone : wear > 0.5 && k < 0.6 ? (dc < 140 ? m.hexstone : m.pale) : rim && wear < -0.3 && k < 0.5 ? m.moss : k < 0.06 ? m.pale : m.flag;
            }
        } else {
            double soil = fbm(n9, x, z, 1 / 13.0, 2);
            if (cap != null) {
                cap[0] = m.moss;
                if (Math.hypot(x, z) > 60) {
                    if (soil > 0.62) cap[0] = m.coarse;
                }
            }
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
    public String heartClosedLine() {
        return "The wax over this door is as thick as a wall. Something out in the valley is holding it shut.";
    }

    /** The valley's wall is the edge: past its foot the wind pushes you back down into the land. */
    @Override
    public dev.awaken.gates.world.FloorAir air() {
        return new dev.awaken.gates.realms.kit.AirPlan().mist(72, 7, 0xe9c88a, 0.32F).build();
    }

    @Override
    public double edge(int x, int z) {
        return Math.hypot(x - 190, z * 1.15) - (WALL + 80);
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return Math.hypot(x - cu, z - cv) < 37.5 && y > Heart.FLOOR - 3 && y < Heart.FLOOR + 90;
    }

    @Override
    public List<Edit> change(String moment) {
        return Heart.change(moment, (int) cu, (int) cv, seed);
    }

    @Override
    public float mouthYaw() {
        return (float) Math.toDegrees(Math.atan2(-(cu - 0), cv - 0));
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
            if (y < WATER) for (int k = y + 1; k <= WATER; k++) v.set(x, k, z, m.honey);
            gy[si] = y;
            mesa(v, m, x, z, y);
        }
        drawPieces(v, m);
        shade(v, m);
        growth(v, m, gy);
    }

    /** The top of the rock standing on this hex of ground, or -1 where there's none. */
    private int rockTop(Hex h) {
        double best = 0;
        for (double[] g : mesas) {
            double k = smooth(g[2], g[2] * 0.5, Math.hypot(h.cx() - g[0], h.cz() - g[1])) * g[3];
            if (k > best) best = k;
        }
        best *= smooth(150, 190, Math.hypot(h.cx() - cu, h.cz() - cv));
        // the valley's wall is more of the same, and it never stops
        double c = Math.hypot(h.cx() - 190, h.cz() * 1.15);
        double wall = 66 * smooth(WALL - 110, WALL - 10, c);
        if (wall > best) best = wall;
        if (best < 3 || roadD(h.cx(), h.cz()) < 14) return -1;
        double base = heightOf(h.cx(), h.cz(), riverD(h.cx(), h.cz()));
        return (int) (base + best * (0.55 + 0.62 * rnd(h.q(), h.r(), 5 + seed)));
    }

    /** The top block of the rock in a column, or -1: broken a little, so nothing up there is one flat plane. */
    int mesaTop(int x, int z) {
        Hex h = hex(x, z, 7.5);
        int t = rockTop(h);
        return t < 0 || h.edge() > 0.97 ? -1 : t + (int) Math.floor(1.3 * n3.get(x / 4.0, z / 4.0));
    }

    /** Whether rock stands anywhere within r of a point: what's meant to be on open ground isn't put there. */
    boolean onRock(int x, int z, int r) {
        for (int a = -r; a <= r; a += Math.max(1, r)) for (int c = -r; c <= r; c += Math.max(1, r)) if (mesaTop(x + a, z + c) >= 0) return true;
        return false;
    }

    // hex-cell mesas, built in stacked courses so no face is one flat plane
    private void mesa(V v, M m, int x, int z, int ground) {
        Hex h = hex(x, z, 7.5);
        int rock = rockTop(h);
        if (rock < 0) return;
        int top = rock + (int) Math.floor(1.3 * n3.get(x / 4.0, z / 4.0));
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
            if (y == top) {
                // the tops are weathered ground of their own: moss and rough stone, a few things growing, a rim worn pale
                double wear = n7.get(x / 9.0, z / 9.0), k = rnd(x, z, 33 + seed);
                s = h.edge() > 0.8 ? m.pale : wear > 0.25 ? m.moss : wear < -0.3 ? m.worn : k < 0.4 ? m.pollen : m.stoneA;
            }
            v.set(x, y, z, s);
        }
        if (!slot && v.get(x, top, z) == m.moss) {
            double k = rnd(x, z, 34 + seed);
            if (k < 0.32) v.set(x, top + 1, z, m.wheat);
            else if (k < 0.5) v.set(x, top + 1, z, m.amberGrass);
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
            if (v.get(x, y + 1, z) != null || tended(x, z)) continue;
            double p = fbm(n7, x, z, 1 / 11.0, 2), q = rnd(x, z, 77 + seed);
            if (s != m.moss) {
                if (q < 0.08) v.set(x, y + 1, z, m.deadBush);
                continue;
            }
            if (p > -0.05) {
                if (q < 0.62) v.set(x, y + 1, z, m.wheat);
                else if (q < 0.7 && p > 0.3) v.set(x, y + 1, z, m.wheatTall);
                else if (q < 0.86) v.set(x, y + 1, z, m.amberGrass);
            } else if (q < 0.52) v.set(x, y + 1, z, m.amberGrass);
            else if (q < 0.56) v.set(x, y + 1, z, m.wheat);
            else if (q < 0.58) v.set(x, y + 1, z, m.bloom);
            else if (q < 0.6) v.set(x, y + 1, z, m.petals);
        }
    }

    // ---- what stands in it --------------------------------------------------------------------------------------------------

    static final int[] PR = {112, 96, 80, 64};

    private void build() {
        int mx = -6, mz = 0;
        int my = ground(mx, mz);
        site(SiteKind.MOUTH, mx, my + 1, mz, 12, "");
        site(SiteKind.PORTAL, mx - 6, my + 1, mz, 2, "");
        pieces.add(new MouthRing(mx, my, mz, this));
        waystone(mx + 15, mz + 9, "the Mouth");
        citadel();
        village();
        flowers();
        for (int i = 0; i < 9; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = 16 + r.nextDouble() * 50;
            int x = (int) (mx + Math.cos(a) * rr), z = (int) (mz + Math.sin(a) * rr);
            if (roadD(x, z) < 8) continue;
            place(new Boulder(x, z, 2 + r.nextInt(3), 1 + r.nextInt(3), i, this));
        }
        if (roadD(mx + 34, mz + 46) > 9) pieces.add(new Tree(mx + 34, mz + 46, 22, 2, this));
        placeSites();
    }

    private void placeSites() {
        // Wardens hold the land in any order; dens and finds are where the land already invites you
        double[][] wardens = {{170, -86}, {226, 150}, {330, -40}, {60, 170}, {470, 140}};
        String[] kinds = {"the_swarm_mind", "the_swarm_mind", "the_swarm_mind", "the_swarm_mind", "the_swarm_mind"};
        int want = ctx.bp().sites().wardens();
        for (int i = 0; i < Math.min(want, wardens.length); i++) {
            int[] at = clearOf((int) wardens[i][0], (int) wardens[i][1], 9);
            site(SiteKind.WARDEN, at[0], ground(at[0], at[1]) + 1, at[1], 18, kinds[i]);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * 2.399963 + jit(0.4), rr = 60 + i * 26;
            int x = (int) (190 + Math.cos(a) * rr), z = (int) (Math.sin(a) * rr * 0.86);
            if (Math.hypot(x - cu, z - cv) < 160 || Math.hypot(x + 6, z) < 56 || villageD(x, z) < 16 || riverD(x, z) < 6 || onRock(x, z, 7)) continue;
            site(SiteKind.DEN, x, ground(x, z) + 1, z, 10, "");
        }
        int finds = ctx.bp().sites().finds();
        for (int i = 0; i < finds * 3 && sites(SiteKind.FIND).size() < finds; i++) {
            double a = r.nextDouble() * Math.PI * 2, rr = 30 + r.nextDouble() * 420;
            int x = (int) (190 + Math.cos(a) * rr), z = (int) (Math.sin(a) * rr);
            if (Math.hypot(x - cu, z - cv) < 150 || villageD(x, z) < 4 || riverD(x, z) < 3 || roadD(x, z) < 5 || onRock(x, z, 4)) continue;
            int gy = ground(x, z);
            if (place(new Find(x, gy, z, seed + i))) site(SiteKind.FIND, x, gy + 1, z, 2, "");
        }
        site(SiteKind.VILLAGE, (int) vu, VILLAGE_Y + 1, (int) vv, 62, "");
        waystone((int) plazaU - 24, (int) plazaV + 8, "the village");
        int cx = (int) cu, cz = (int) cv;
        waystone(cx - 112 - 18, cz + 22, "the citadel's foot");
        site(SiteKind.LORD, cx, Heart.FLOOR + 1, cz, 44, ctx.bp().heart().lord());
        // the honey wind: a column of warm air at the foot of each great rock, and on top of that rock, something you can only reach by riding it
        for (int i = 0; i < mesas.length; i++) vent(i);
        clearSites();
    }

    private int[] clearOf(int x, int z, int r) {
        for (int ring = 0; ring < 24; ring++) for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = x + (int) (Math.cos(a) * ring * 6), pz = z + (int) (Math.sin(a) * ring * 6);
            if (!onRock(px, pz, r) && riverD(px, pz) > 6) return new int[]{px, pz};
        }
        return new int[]{x, z};
    }

    /** Walks in from outside a great rock until it starts: the vent goes just short of it, tall enough to clear the top. */
    private void vent(int i) {
        double[] g = mesas[i];
        double a = Math.atan2(vv - g[1], vu - g[0]) + jit(0.5);
        for (double rr = g[2] + 14; rr > 6; rr -= 1) {
            int x = (int) (g[0] + Math.cos(a) * rr), z = (int) (g[1] + Math.sin(a) * rr);
            if (!onRock(x, z, 6)) continue;
            x = (int) (g[0] + Math.cos(a) * (rr + 7));
            z = (int) (g[1] + Math.sin(a) * (rr + 7));
            if (onRock(x, z, 4) || riverD(x, z) < 6) return;
            int high = -1, fx = 0, fz = 0;
            for (int p = -22; p <= 22; p += 2) for (int q = -22; q <= 22; q += 2) {
                int t = mesaTop(x + p, z + q);
                if (t < 0) continue;
                // the rock's own top, nearest the vent wins a tie: what you came up for is in reach of the air
                if (t > high) {
                    high = t;
                    fx = x + p;
                    fz = z + q;
                }
            }
            if (high < ground(x, z) + 12) return;
            if (!place(new Updraft(x, z, high + 7, this))) return;
            site(SiteKind.UPDRAFT, x, ground(x, z), z, 3, "");
            pieces.add(new Find(fx, high, fz, seed + 500 + i));
            site(SiteKind.FIND, fx, high + 1, fz, 2, "high");
            return;
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
        pieces.add(new Heart(cx, cz)); // last of the great things: nothing else may reach into the hall
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
        double[][] spots = {{160, -68, 30, 7.5, 1}, {226, -36, 26, 6, -1}, {214, 124, 40, 9, 1}, {186, 128, 34, 7.5, -1}, {250, 136, 30, 6.5, 1}, {40, -70, 22, 5.5, 1}, {96, 150, 24, 6, -1}, {10, -36, 42, 10.5, 1}, {74, 68, 28, 7, -1}};
        for (int i = 0; i < spots.length; i++) {
            double[] s = spots[i];
            int x = (int) (s[0] + jit(10)), z = (int) (s[1] + jit(10));
            if (roadD(x, z) < 16 || riverD(x, z) < 6 || onRock(x, z, 5)) continue;
            place(new Flower(x, z, s[2] + jit(3), s[3], s[4], seed + i, this));
        }
    }

    // ---- blocks -------------------------------------------------------------------------------------------------------------

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    /** The land's palette, every block of it the place's own but for small fittings (fence rail, barrel, lantern, chain). */
    static final class M extends Mats {
        final BlockState petal = b("amber_petal"), petalDeep = b("amber_petal_deep"), petalTip = b("amber_petal_tip"), seedhead = b("amber_seedhead"), stem = b("amber_stem"), leaf = b("amber_leaf");
        final BlockState wheat = b("amber_wheat"), wheatTall = b("amber_wheat_tall"), amberGrass = b("amber_grass"), bloom = b("amber_bloom");
        final BlockState pollen = b("hive_pollen"), royal = b("amber_gilt"), petals = b("hive_petals"), honey = b("realm_honey");
        final BlockState coarse = Blocks.COARSE_DIRT.defaultBlockState(), rooted = Blocks.ROOTED_DIRT.defaultBlockState(), deadBush = Blocks.DEAD_BUSH.defaultBlockState();

        M() {
            super("amber", new Accents(b("hive_comb"), b("hive_comb_capped"), b("hive_honey_glow"), b("hive_drip"), lit(b("hive_candle")), b("hive_wax_crumbs"), b("hive_drum"),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(), b("amber_leaf_gold")));
        }
    }

    @Override
    protected M mats() {
        return m();
    }

    private static volatile M mats;

    static M m() {
        M p = mats;
        if (p == null) mats = p = new M();
        return p;
    }

    abstract static class P extends Piece<M> {}

    // ---- the Mouth ----------------------------------------------------------------------------------------------------------


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






    // ---- the village --------------------------------------------------------------------------------------------------------





    // ---- the heart: the Lord's hall inside the great spire -------------------------------------------------------------------

    /**
     * Inside the main spire, the Hive's oldest room (SPEC §50.8). One enormous thing hangs over it, the Mother Comb; the floor falls away in a
     * bowl under it and a gallery runs round the wall. Wax closes the door behind you; at two thirds the comb weeps and honey wells up across
     * the bowl; at a third the old vents wake and the place's own rule, rising air, carries you up to the gallery. The whole room is drawn to
     * the shape of the spire around it: every tier steps in, so the vault does too.
     */
    static final class Heart extends P {
        static final int FLOOR_TOP = CIT_Y + 40, FLOOR = FLOOR_TOP - 3, GALLERY = FLOOR_TOP + 18, DOOR_X = 41;
        private static final int SPIRE_TOP = 326, TIERS = 9;
        /** For each distance along a wall's normal, the highest y that's still inside the spire's hall. */
        private static final int[] REACH = new int[64];

        static {
            for (int d = 0; d < REACH.length; d++) {
                int y = SPIRE_TOP;
                while (y > FLOOR_TOP && hallR(y) < d) y--;
                REACH[d] = y;
            }
        }

        final int cx, cz;

        Heart(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            box(cx, cz, 50, 50);
            mayOverlap = true;
        }

        private static double spireR(int y) {
            double tk = (y - FLOOR_TOP) / (double) (SPIRE_TOP - FLOOR_TOP) * TIERS;
            int k = Math.min(TIERS - 1, (int) Math.floor(tk));
            double Rb = 50 * Math.pow(1 - k / (double) TIERS, 1.22), Rt = 50 * Math.pow(1 - (k + 1) / (double) TIERS, 1.22) * 1.08 + 0.4;
            return lerp(Rb, Rt, tk - k);
        }

        /** The inner face of the wall at a height: clear of the shell and of the panels set back in it. */
        private static double hallR(int y) {
            return spireR(y) - 7.5;
        }

        /** Half the door's width at a height above the floor. */
        static double doorHw(double gy) {
            return gy < 0 ? 0 : gy < 9 ? 6.5 : 6.5 * Math.sqrt(Math.max(0, 1 - sq((gy - 9) / 8.0)));
        }

        private static int roofAt(double d, double rho) {
            return Math.min(FLOOR_TOP + 72 - (int) (26 * sq(rho / 38)), REACH[Math.min(63, (int) Math.ceil(d))] + 1);
        }

        private static double normal(double dx, double dz) {
            double sec = Math.PI / 6, a = Math.atan2(dz, dx), phi = a - Math.floor(a / sec + 0.5) * sec;
            return Math.hypot(dx, dz) * Math.cos(phi);
        }

        static int floorY(double rho) {
            if (rho <= 22) return FLOOR;
            if (rho >= 28) return FLOOR_TOP;
            return FLOOR + (int) Math.floor((rho - 22) / 2);
        }

        /** Two ramps climb the wall from beside the door; the ramp's height in half blocks at this bearing, or -1. */
        private static int rampHalf(double rho, double theta) {
            if (rho < 35 || rho > 41) return -1;
            double fromDoor = Math.abs(Math.abs(theta) - Math.PI), a = fromDoor - Math.toRadians(24), span = Math.toRadians(54);
            if (a < 0 || a > span) return -1;
            return (int) Math.floor(a / span * 2 * (GALLERY - FLOOR_TOP));
        }

        private static boolean nearVent(double dx, double dz) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 6 + i * Math.PI / 3;
                if (Math.hypot(dx - Math.cos(a) * 31, dz - Math.sin(a) * 31) < 3.6) return true;
            }
            return false;
        }

        @Override
        public void draw(V v, M m) {
            double twelfth = Math.PI / 6;
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x - cx, dz = z - cz, rho = Math.hypot(dx, dz), theta = Math.atan2(dz, dx);
                double a = theta, s = Math.floor(a / twelfth + 0.5), phi = a - s * twelfth, d = rho * Math.cos(phi), u = rho * Math.sin(phi);
                boolean gate = Math.abs(Math.abs(s) - 6) < 0.5 && Math.abs(u) < 17;
                double lim = gate ? 37.5 : hallR(FLOOR_TOP);
                if (d > lim + 5) continue;
                boolean in = d <= lim;
                int roof = roofAt(d, rho);
                if (in) for (int y = FLOOR - 4; y < roof; y++) v.set(x, y, z, null);
                // the wall behind you: the shell is only a skin, so the room gets one of its own, joined to it, cut in panels of comb
                for (int y = FLOOR_TOP; y <= FLOOR_TOP + 80; y++) {
                    double r = gate ? 37.5 : hallR(y);
                    if (d <= r || d > r + 4.6) continue;
                    if (gate && y - FLOOR_TOP > 0 && y - FLOOR_TOP < 20 && Math.abs(u) < doorHw(y - FLOOR_TOP)) continue;
                    Hex c = hex(u + s * 3.7, y, 2.6);
                    double k = rnd(c.q(), c.r(), 905);
                    v.set(x, y, z, c.edge() > 0.72 ? ((y & 3) == 0 ? m.deep : m.stoneA) : k < 0.2 ? m.glow : k < 0.6 ? m.capped : m.comb);
                }
                if (!in) continue;
                // the vault: stepped comb cells that follow the spire's tiers in; each ring is joined to the next by a riser, so it's one roof
                Hex cell = hex(dx, dz, 3.1);
                double rk = rnd(cell.q(), cell.r(), 901);
                int cellUp = cell.edge() > 0.74 ? 0 : (int) (rk * 3), top = roof + 3;
                for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    double ndx = dx + n[0], ndz = dz + n[1];
                    top = Math.max(top, roofAt(normal(ndx, ndz), Math.hypot(ndx, ndz)) + 1);
                }
                for (int y = roof + cellUp; y <= top; y++) v.set(x, y, z, cell.edge() > 0.74 ? m.deep : y == roof + cellUp ? (rk < 0.3 ? m.glow : rk < 0.7 ? m.capped : m.comb) : m.deep);
                // the bowl, its steps and the floor at the wall
                int fy = floorY(rho);
                boolean half = rho > 22 && rho < 28 && ((int) Math.floor(rho - 22) & 1) == 1;
                for (int y = fy - 3; y <= fy; y++) v.set(x, y, z, y == fy ? floorBlock(m, x, z, rho, theta) : y == fy - 3 ? m.deep : m.stoneA);
                if (half) v.set(x, fy + 1, z, m.flagSlab);
                column(v, m, x, z, rho, theta, twelfth);
                gallery(v, m, x, z, dx, dz, rho, theta, roof);
                ramps(v, m, x, z, rho, theta);
                motherComb(v, m, x, z, cell, rk, roof);
            }
            // the seal: part of the land until the last Warden falls
            for (Edit e : seal(cx, cz, m, true)) v.set(e.x(), e.y(), e.z(), e.state());
        }

        private static BlockState floorBlock(M m, int x, int z, double rho, double theta) {
            if (rho < 3) return m.royal;
            double six = Math.PI / 3, phi = theta - Math.floor(theta / six + 0.5) * six, u = rho * Math.sin(phi);
            if (rho < 22 && Math.abs(u) < 1.1) return m.glow; // honey channels to the wall of the bowl
            if (rho > 28 && rho < 30) return m.deep;
            Hex t = hex(x, z, 2.8);
            if (t.edge() > 0.76) return m.deep;
            double k = rnd(t.q(), t.r(), 902);
            return k < 0.4 ? m.hexstone : k < 0.75 ? m.flag : k < 0.92 ? m.worn : m.pale;
        }

        /** Twelve columns and the arcade they carry; the ring is set so no column stands in front of the door. */
        private static void column(V v, M m, int x, int z, double rho, double theta, double twelfth) {
            double off = theta - twelfth / 2, turn = off / twelfth - Math.floor(off / twelfth);
            double along = Math.min(turn, 1 - turn) * twelfth * 30; // blocks along the ring from the nearest column
            double across = Math.abs(rho - 30);
            if (across > 4.5) return;
            int base = FLOOR_TOP + 1;
            if (along < 3.2 && across < 3.2) {
                double r = Math.hypot(along, across);
                for (int y = base; y <= FLOOR_TOP + 38; y++) {
                    int up = y - base;
                    double w = up < 2 ? 3.2 : up < 4 ? 2.7 : y > FLOOR_TOP + 34 ? 2.9 : 2.1;
                    if (r > w) continue;
                    BlockState b = up < 2 ? m.deep : y > FLOOR_TOP + 34 ? (y == FLOOR_TOP + 36 ? m.gold : m.deep) : (up % 9 == 8 ? m.gold : up % 3 == 0 ? m.worn : m.stoneA);
                    if (r > w - 1 && (up == 12 || up == 24) && along < 1.2) b = m.shroom;
                    v.set(x, y, z, b);
                }
            }
            // the arcade over the columns: pointed arches in courses, and the band they carry
            if (across <= 2.6) {
                double c = 1 - Math.min(1, along / (twelfth * 30 / 2));
                int soffit = FLOOR_TOP + 29 + (int) (9 * Math.sqrt(Math.max(0, 1 - c * c)));
                for (int y = soffit; y <= FLOOR_TOP + 40; y++) v.set(x, y, z, y == soffit ? m.gold : y >= FLOOR_TOP + 38 ? m.deep : (y & 3) == 0 ? m.band : m.stoneA);
            }
        }

        private static void gallery(V v, M m, int x, int z, double dx, double dz, double rho, double theta, int roof) {
            if (rho < 33 || rampHalf(rho, theta) >= 2 * (GALLERY - FLOOR_TOP) - 10 || roof <= GALLERY + 2) return;
            for (int y = GALLERY - 1; y <= GALLERY; y++) v.set(x, y, z, y == GALLERY ? (rho < 34 ? m.pale : ((x + z) & 3) == 0 ? m.hexstone : m.flag) : m.deep);
            if (rho < 34 && !nearVent(dx, dz)) {
                boolean post = ((x ^ z) & 3) == 0;
                v.set(x, GALLERY + 1, z, post ? m.fence : m.paleSlab);
                if (((x * 31 + z * 7) & 31) == 0) {
                    v.set(x, GALLERY + 1, z, m.fence);
                    v.set(x, GALLERY + 2, z, m.lantern);
                }
            }
        }

        private static void ramps(V v, M m, int x, int z, double rho, double theta) {
            int h = rampHalf(rho, theta);
            if (h < 0) return;
            int top = FLOOR_TOP + h / 2;
            for (int y = FLOOR_TOP + 1; y <= top; y++) v.set(x, y, z, y == top ? m.flag : (y & 3) == 0 ? m.deep : m.stoneA);
            if ((h & 1) == 1) v.set(x, top + 1, z, m.flagSlab);
            if (rho < 36) {
                v.set(x, top + 1, z, m.deep);
                v.set(x, top + 2, z, m.paleSlab);
            }
        }

        /** The one enormous thing: comb hanging from the vault in hex columns, longest in the middle, lit from inside. */
        private static void motherComb(V v, M m, int x, int z, Hex cell, double rk, int roof) {
            double cr = Math.hypot(cell.cx(), cell.cz());
            if (cr > 20) return;
            int bottom = FLOOR_TOP + 20 + (int) (Math.pow(cr / 20, 1.6) * 30 + rk * 6);
            boolean edge = cell.edge() > 0.74;
            for (int y = bottom + (edge ? 1 : 0); y < roof + 3; y++) {
                int up = y - bottom;
                BlockState b = edge ? (up % 9 == 0 ? m.gold : m.deep) : up < 2 ? (rk < 0.45 ? m.glow : m.capped) : up % 6 == 2 && rk < 0.7 ? m.glow : up % 2 == 0 ? m.capped : m.comb;
                v.set(x, y, z, b);
            }
            if (!edge && rk > 0.5) {
                v.set(x, bottom - 1, z, m.drip);
                if (rk > 0.8) v.set(x, bottom - 2, z, m.drip);
            }
            if (cr < 3.4 && !edge) v.set(x, bottom, z, m.royal);
        }

        // ---- what changes -----------------------------------------------------------------------------------------------

        static List<Edit> seal(int cx, int cz, M m, boolean wax) {
            List<Edit> out = new ArrayList<>();
            for (int gy = 1; gy <= 17; gy++) {
                double hw = doorHw(gy);
                for (int dz = -7; dz <= 7; dz++) {
                    if (Math.abs(dz) >= hw) continue;
                    for (int d = 38; d <= DOOR_X; d++) {
                        BlockState b = !wax ? null : d == DOOR_X ? (Math.abs(dz) < 1 || gy % 7 == 0 ? m.deep : (dz + gy & 3) == 0 ? m.royal : m.capped) : m.comb;
                        out.add(new Edit(cx - d, FLOOR_TOP + gy, cz + dz, b == null ? Blocks.AIR.defaultBlockState() : b));
                    }
                }
            }
            return out;
        }

        /** The honey in the bowl: some of the cells, so there's always a dry way across. */
        private static List<Edit> honey(int cx, int cz, boolean rise, M m, long seed) {
            List<Edit> out = new ArrayList<>();
            for (int a = -22; a <= 22; a++) for (int c = -22; c <= 22; c++) {
                double rho = Math.hypot(a, c);
                if (rho > 21.5) continue;
                Hex t = hex(a, c, 5);
                if (t.edge() > 0.8 || rnd(t.q(), t.r(), 903 + seed) > 0.62) continue;
                out.add(new Edit(cx + a, FLOOR + 1, cz + c, rise ? m.honey : Blocks.AIR.defaultBlockState()));
            }
            return out;
        }

        private static List<Edit> vents(int cx, int cz, boolean on, M m) {
            List<Edit> out = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                double a = Math.PI / 6 + i * Math.PI / 3;
                int vx = (int) Math.round(Math.cos(a) * 31), vz = (int) Math.round(Math.sin(a) * 31);
                for (int p = -2; p <= 2; p++) for (int q = -2; q <= 2; q++) {
                    if (p * p + q * q > 5) continue;
                    int X = vx + p, Z = vz + q;
                    out.add(new Edit(cx + X, FLOOR_TOP, cz + Z, on ? m.glow : floorBlock(m, cx + X, cz + Z, Math.hypot(X, Z), Math.atan2(Z, X))));
                    for (int y = FLOOR_TOP + 1; y <= GALLERY + 4; y++) out.add(new Edit(cx + X, y, cz + Z, on ? m.updraft : Blocks.AIR.defaultBlockState()));
                }
            }
            return out;
        }

        static List<Edit> change(String moment, int cx, int cz, long seed) {
            M m = m();
            return switch (moment) {
                case "heart_open" -> seal(cx, cz, m, false);
                case "heart_shut" -> seal(cx, cz, m, true);
                case "heart_weeps" -> honey(cx, cz, true, m, seed);
                case "heart_winds" -> vents(cx, cz, true, m);
                case "heart_still" -> {
                    List<Edit> out = new ArrayList<>(honey(cx, cz, false, m, seed));
                    out.addAll(vents(cx, cz, false, m));
                    yield out;
                }
                default -> List.of();
            };
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
                    // what stands proud goes deep enough to meet the panels set back behind it: no plate hangs in the wall on its own
                    boolean surface = d > face + prow - rec - (rec > 0 ? 2.6 : 8);
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
                                // the door's own frame stands out from the glowing panel it's set in
                                double dw = Heart.doorHw(gy);
                                if (gy >= 0 && gy < 19 && Math.abs(u) < dw + 2) {
                                    proud = 1.5;
                                    b = Math.abs(u) > dw + 1 ? m.gold : m.deep;
                                }
                            } else {
                                proud = 2.5 - (order & 1);
                                recess = 0;
                                b = (order & 1) == 0 ? m.gold : m.deep;
                            }
                        }
                    }
                    if (gateSide && y > y0 && y - y0 < 20 && Math.abs(u) < Heart.doorHw(y - y0)) continue; // the door is a real way through
                    double surf = Rf + proud - recess;
                    if (d > surf || d < surf - (gateSide && y - y0 < 56 ? 7 : 3.4)) continue;
                    if (Math.abs(u) > d * tan + 0.6) continue;
                    v.set(x, y, z, b);
                }
            }
        }
    }
}
