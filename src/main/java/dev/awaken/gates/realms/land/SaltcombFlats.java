package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.KitLand;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.piece.Find;
import dev.awaken.gates.realms.kit.piece.Hut;
import dev.awaken.gates.realms.kit.piece.MouthRing;
import dev.awaken.gates.realms.kit.piece.Stall;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.Build.lit;
import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.Build.under;
import static dev.awaken.gates.realms.kit.N.hexMetric;
import static dev.awaken.gates.realms.kit.N.rnd;
import static dev.awaken.gates.realms.kit.N.smooth;

/**
 * Saltcomb Flats (SPEC 50.8): a dry hive-city half out of the sand, at the far end of a white salt pan cracked into hexagons and pooled with brine. The only road
 * is a line of black stepping stones, and three times it runs out where the crust lies across open brine. The rule of the place: the salt sets hard in the cool wind
 * and goes soft when the sun comes back, so a ford is a run while it holds and a slow swim when it doesn't. The Lord waits in the Sinking Court, a drum of comb
 * at the foot of the city, and the floor goes down under you as the fight goes on.
 */
public final class SaltcombFlats extends KitLand<SaltcombFlats.M> {
    static final int CRUST = 64, BASIN = 60, F = 72, HALF = 420, RIM = 352;
    /** The court: its middle, the arena's edge, the inner and outer faces of its wall, where the roof begins, and the hole left in the top. */
    static final int CCZ = -60, RI = 42, RW = 60, RO = 67, SPRING = 52, OCULUS = 22;
    static final int[] FORDS = {188, 128, 72};
    static final int FRX = 28, FRZ = 12;
    static final double S3 = Math.sqrt(3), TAU = Math.PI * 2;

    public static final class M extends Mats {
        final BlockState bone = b("salt_bark"), boneWorn = b("salt_bark_moss"), relief = b("salt_relief"), cloth = b("salt_banner"), fall = b("salt_fall"),
                crust = b("salt_crust"), cracked = b("salt_crust_cracked"), sand = b("salt_sand"), ripple = b("salt_sand_ripple"), combBlock = b("salt_comb"),
                brazier = b("temple_brazier"), water = Blocks.WATER.defaultBlockState(), dead = Blocks.DEAD_BUSH.defaultBlockState(), dry = Blocks.SHORT_DRY_GRASS.defaultBlockState(),
                airLight = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 11);

        M() {
            super("salt", new Accents(b("salt_comb"), b("salt_relief"), b("salt_window"), b("hive_drip"), lit(b("hive_candle")), b("hive_wax_crumbs"), b("hive_drum"),
                    Blocks.DARK_OAK_FENCE.defaultBlockState(), b("amber_leaf_gold")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (SaltcombFlats.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    @Override
    protected M mats() {
        return m();
    }

    /** One hexagon of the black road. */
    record Stone(int x, int z, int r, int top) {}

    final SimplexNoise n1, n2, n3;
    private final List<Stone> stones = new ArrayList<>();
    private final List<int[]> mounds = new ArrayList<>(), cones = new ArrayList<>(), stacks = new ArrayList<>(), keepClear = new ArrayList<>();
    private final List<Edit> grille = new ArrayList<>(), fordCrust = new ArrayList<>();
    private final List<int[]> sinkA = new ArrayList<>(), sinkB = new ArrayList<>();

    public SaltcombFlats(Ctx ctx) {
        super(ctx, 0x53414C54L);
        n1 = N.noise(seed + 31);
        n2 = N.noise(seed + 32);
        n3 = N.noise(seed + 33);
        for (int i = 0; i < 18; i++) {
            double a = i * 2.399963 + jit(0.3), d = 318 + (i % 4) * 20 + jit(12);
            int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
            if (Math.abs(x) < 70 && z > 200) continue;
            stacks.add(new int[]{x, z, 22 + (i * 7) % 18, 90 + (i * 37) % 80});
        }
        mounds.add(new int[]{-74, 212, 40, 4});
        mounds.add(new int[]{62, 204, 36, 4});
        road();
        planFords();
        planFloor();
        grilleOfDoor();
        placeSites();
        // the road ends at the door, so the door gets drawn after the sand
        pieces.add(new Court());
        pieces.add(new Egg(0, CCZ - 14, 13));
        courtFurniture();
        pieces.add(new Stones());
        city();
        foreground();
        flats();
        village();
        clearSites();
    }

    // ---- the plan ----------------------------------------------------------------------------------------------------------

    private void road() {
        stones.add(new Stone(0, 244, 14, CRUST + 1));
        double z = 228;
        int i = 0;
        while (z > 22) {
            boolean ford = false;
            for (int fz : FORDS) if (Math.abs(z - fz) < FRZ + 2) ford = true;
            if (!ford) {
                int x = (int) Math.round(Math.sin(z / 58.0) * 6 + jit(3.2));
                int top = z > 60 ? CRUST + 1 : CRUST + 1 + (int) Math.floor((60 - z) / 40.0 * (F - CRUST - 1));
                stones.add(new Stone(x, (int) z, 5 + (i % 3 == 1 ? 1 : 0), top));
                i++;
            }
            z -= z <= 66 ? 5.0 : 10.5;
        }
        stones.add(new Stone(0, 15, 12, F));
    }

    private void planFords() {
        M m = m();
        for (int fz : FORDS) for (int x = -FRX; x <= FRX; x++) for (int z = fz - FRZ; z <= fz + FRZ; z++) {
            if (N.sq(x / (double) FRX) + N.sq((z - fz) / (double) FRZ) < 1) fordCrust.add(new Edit(x, CRUST, z, crustAt(m, x, z)));
        }
    }

    private BlockState crustAt(M m, int x, int z) {
        return N.hex(x + 0.5, z + 0.5, 4.5).edge() > 0.84 ? m.cracked : m.crust;
    }

    private BlockState tile(M m, int x, int z) {
        N.Hex t = N.hex(x + 0.5, z + 0.5 - CCZ, 4.4);
        double c = rnd(t.q(), t.r(), 91);
        if (t.edge() > 0.89) return m.worn;
        return c < 0.45 ? m.pale : c < 0.75 ? m.plaster : m.stoneA;
    }

    /** Which hexagons of the floor go first and which go second. They are whole tiles, so the edge of the hole breaks the way paving would. */
    private void planFloor() {
        for (int x = -RI; x <= RI; x++) for (int z = -RI; z <= RI; z++) {
            double D = Math.hypot(x + 0.5, z + 0.5);
            if (D >= RI - 1) continue;
            N.Hex t = N.hex(x + 0.5, z + 0.5, 4.4);
            double td = Math.hypot(t.cx(), t.cz()), at = Math.atan2(t.cz(), t.cx());
            if (td < 13 || Math.hypot(t.cx(), t.cz() + 14) < 18) continue;
            if (at > -0.5 && at < 1.15) sinkA.add(new int[]{x, z + CCZ});
            else if (at > 2.15 || at < -2.6) sinkB.add(new int[]{x, z + CCZ});
        }
    }

    private void grilleOfDoor() {
        M m = m();
        for (int x = -7; x <= 7; x++) for (int y = F + 1; y <= F + doorTop(x); y++) for (int z = CCZ + RO - 3; z <= CCZ + RO - 2; z++) {
            N.Hex c = N.hex(x + 0.5, y + 0.5, 3.2);
            grille.add(new Edit(x, y, z, z == CCZ + RO - 2 && c.edge() > 0.72 ? m.pale : z == CCZ + RO - 2 && rnd(c.q(), c.r(), 88) < 0.25 ? m.window : m.combBlock));
        }
    }

    static int doorTop(int x) {
        return 11 + (int) Math.round(5 * Math.sqrt(Math.max(0, 1 - N.sq((x + 0.5) / 8.0))));
    }

    // ---- the land ----------------------------------------------------------------------------------------------------------

    /** How high the sand stands here. Below the crust there's no sand and the salt has the ground. */
    double sandH(int x, int z) {
        double wob = n1.get(x / 150.0, z / 150.0) * 34 + n2.get(x / 46.0, z / 46.0) * 9;
        double north = smooth(26, -150, z + wob), side = smooth(170, 330, Math.abs(x) + wob), south = smooth(286, 380, z + wob);
        double rise = Math.max(north, Math.max(side, south));
        double crest = 1 - Math.abs(n3.get(x / 130.0 + z / 300.0, z / 64.0));
        double h = CRUST - 4 + 40 * north + 26 * side + 34 * south + crest * crest * 13 * smooth(0.12, 0.8, rise);
        for (int[] c : mounds) {
            double d = Math.hypot(x - c[0], z - c[1]) + n2.get(x / 12.0, z / 12.0) * 5;
            if (d < c[2]) h = Math.max(h, CRUST + c[3] * Math.pow(1 - d / c[2], 1.4));
        }
        double r = Math.hypot(x, z);
        h += Math.pow(smooth(RIM + 16, RIM + 66, r), 1.6) * 180;
        if (r > 280) for (int[] t : stacks) {
            double d = Math.hypot(x - t[0], z - t[1]) + n2.get(x / 16.0, z / 16.0) * 7;
            if (d < t[2]) h = Math.max(h, CRUST + t[3] * Math.pow(1 - Math.pow(d / t[2], 6), 0.5));
        }
        return h;
    }

    private boolean inFord(int x, int z) {
        for (int fz : FORDS) if (N.sq(x / (double) FRX) + N.sq((z - fz) / (double) FRZ) < 1) return true;
        return false;
    }

    private int stoneTop(int x, int z) {
        int best = -1;
        for (Stone s : stones) {
            if (Math.abs(x - s.x) > s.r + 2 || Math.abs(z - s.z) > s.r + 2) continue;
            if (hexMetric(x + 0.5 - s.x, z + 0.5 - s.z) <= s.r) best = Math.max(best, s.top);
        }
        return best;
    }

    private boolean inCourt(int x, int z) {
        return Math.hypot(x + 0.5, z + 0.5 - CCZ) <= RO + 2;
    }

    @Override
    public int ground(int x, int z) {
        int st = stoneTop(x, z);
        if (st > 0) return st;
        if (inCourt(x, z)) return F;
        if (inFord(x, z)) return CRUST;
        double h = sandH(x, z);
        return h > CRUST + 0.5 ? (int) h : CRUST;
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
        return Math.hypot(x + 0.5, z + 0.5 - CCZ) <= RW && y >= F;
    }

    @Override
    public String heartClosedLine() {
        return "Somebody has sealed the door of the court with fresh comb, and it won't give while whatever keeps it is still out there on the sand.";
    }

    @Override
    public List<Edit> change(String moment) {
        M m = m();
        BlockState air = Blocks.AIR.defaultBlockState();
        List<Edit> out = new ArrayList<>();
        switch (moment) {
            case "heart_open" -> {
                for (Edit e : grille) out.add(new Edit(e.x(), e.y(), e.z(), air));
            }
            case "heart_shut" -> out.addAll(grille);
            case "heart_weeps" -> sink(out, m, sinkA, true);
            case "heart_winds" -> sink(out, m, sinkB, true);
            case "heart_still" -> {
                sink(out, m, sinkA, false);
                sink(out, m, sinkB, false);
            }
            case "salt_soft" -> {
                for (Edit e : fordCrust) out.add(new Edit(e.x(), e.y(), e.z(), m.water));
            }
            case "salt_hard" -> out.addAll(fordCrust);
            default -> {}
        }
        return out;
    }

    private void sink(List<Edit> out, M m, List<int[]> where, boolean down) {
        for (int[] c : where) {
            out.add(new Edit(c[0], F, c[1], down ? m.water : tile(m, c[0], c[1])));
            out.add(new Edit(c[0], F - 1, c[1], down ? m.water : m.bed));
        }
    }

    @Override
    public void paint(V v) {
        M m = mats();
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) column(v, m, x, z);
        drawPieces(v, m);
        settle(v, m);
        for (Edit e : grille) if (v.in(e.x(), e.y(), e.z())) v.set(e.x(), e.y(), e.z(), e.state());
    }

    /** Anything small left over a hole goes, and the court gets its invisible lamps so the real game isn't black in the corners. */
    private void settle(V v, M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            double D = Math.hypot(x + 0.5, z + 0.5 - CCZ);
            if (D < RW - 2 && Math.floorMod(x, 9) == 0 && Math.floorMod(z, 9) == 0) for (int y : new int[]{F + 6, F + 24, F + 42}) if (v.get(x, y, z) == null) v.set(x, y, z, m.airLight);
            for (int y = CRUST; y <= 360; y++) {
                BlockState s = v.get(x, y, z);
                if (s == null) continue;
                // where one tower's mouths cut through another's, the odd stray block is left behind: it goes
                if (y > F + 2 && alone(v, x, y, z)) {
                    v.set(x, y, z, null);
                    continue;
                }
                if (s == m.dead || s == m.dry || s == m.crumbs) {
                    if (!V.holds(v.get(x, y - 1, z))) v.set(x, y, z, null);
                } else if (s == m.flag && v.get(x, y + 1, z) == null && y > F + 8 && rnd(x, z + y, 41) < 0.18) v.set(x, y + 1, z, m.crumbs);
            }
        }
    }

    private static boolean alone(V v, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            int n = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            if ((n == 1 || n == 2) && v.get(x + dx, y + dy, z + dz) != null) return false;
        }
        return true;
    }

    private dev.awaken.gates.world.FloorAir builtAir;

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        if (builtAir != null) return builtAir;
        AirPlan a = new AirPlan().mist(CRUST + 2, 5, 0xeadfc6, 0.2F).mist(CRUST + 44, 16, 0xd9b673, 0.16F).mist(F + 3, 5, 0xb89a58, 0.32F)
                .water(CRUST - 1, -320, -40, 320, 300).water(F - 2, -44, CCZ - 44, 44, CCZ + 44);
        a.shaft(0, F + 100, CCZ, 0, F, CCZ + 4, 13F, 0xffe2a4, 1F);
        for (int i = 0; i < 9; i++) {
            double t = i * TAU / 9;
            int x = (int) (Math.cos(t) * 15), z = CCZ + (int) (Math.sin(t) * 15);
            a.shaft(x, F + 92, z, x + (int) (Math.cos(t) * 12), F, z + (int) (Math.sin(t) * 12), 5F, 0xffdf9c, 0.55F);
        }
        for (int i = 0; i < 12; i++) {
            double t = (i + 0.5) * TAU / 12;
            a.light((int) (Math.cos(t) * (RI - 2)), F + 4, CCZ + (int) (Math.sin(t) * (RI - 2)), 0xffa45a, 13F, 1F);
            a.light((int) (Math.cos(t) * (RW - 1)), F + 22, CCZ + (int) (Math.sin(t) * (RW - 1)), 0xff9c4c, 16F, 0.9F);
            a.light((int) (Math.cos(t) * (RW - 1)), F + 40, CCZ + (int) (Math.sin(t) * (RW - 1)), 0xff9c4c, 16F, 0.7F);
        }
        return builtAir = a.build();
    }

    private void column(V v, M m, int x, int z) {
        if (Math.abs(x) > HALF || Math.abs(z) > HALF) return;
        v.set(x, 0, z, m.bedrock);
        if (inFord(x, z)) {
            for (int y = 1; y < BASIN; y++) v.set(x, y, z, m.rock);
            v.set(x, BASIN, z, m.crust);
            for (int y = BASIN + 1; y < CRUST; y++) v.set(x, y, z, m.water);
            v.set(x, CRUST, z, crustAt(m, x, z));
            return;
        }
        double h = sandH(x, z);
        if (h > CRUST + 0.5) sandColumn(v, m, x, z, h);
        else saltColumn(v, m, x, z, h);
    }

    private void sandColumn(V v, M m, int x, int z, double h) {
        int g = (int) h;
        boolean rock = h > 128 || (h > CRUST + 6 && Math.abs(sandH(x + 3, z) - h) + Math.abs(sandH(x, z + 3) - h) > 9);
        for (int y = 1; y < g - 5; y++) v.set(x, y, z, rock ? rockOf(m, x, y, z) : m.rock);
        for (int y = Math.max(1, g - 5); y < g; y++) v.set(x, y, z, rock ? rockOf(m, x, y, z) : m.bed);
        if (rock) {
            v.set(x, g, z, rockOf(m, x, g, z));
            return;
        }
        v.set(x, g, z, n2.get(x / 22.0, z / 9.0) > -0.15 ? m.ripple : m.sand);
        if (tended(x, z)) return;
        double c = rnd(x, z, 3);
        if (c < 0.004) v.set(x, g + 1, z, m.dead);
        else if (c < 0.03) v.set(x, g + 1, z, m.dry);
        else if (c < 0.045) v.set(x, g + 1, z, m.crumbs);
    }

    /** The pan: plates of crust with raised rims, about one in five sunk and full of brine, and every plate crazed into smaller cells. */
    private void saltColumn(V v, M m, int x, int z, double h) {
        for (int y = 1; y < BASIN; y++) v.set(x, y, z, m.rock);
        N.Hex p = N.hex(x + 7.5, z + 3.5, 13);
        double e = p.edge();
        boolean pan = rnd(p.q(), p.r(), seed + 5) < 0.3 && h < CRUST - 2 && Math.hypot(x, z - 244) > 30;
        double shore = smooth(CRUST - 2.2, CRUST + 0.4, h);
        for (int y = BASIN; y < CRUST; y++) v.set(x, y, z, m.bed);
        if (pan && e < 0.87) {
            v.set(x, BASIN, z, m.crust);
            for (int y = BASIN + 1; y < CRUST; y++) v.set(x, y, z, m.water);
            return;
        }
        BlockState top = crustAt(m, x, z);
        if (shore > 0.2 && n2.get(x / 7.0, z / 7.0) < shore * 1.6 - 0.8) top = rnd(x, z, 8) < 0.5 ? m.sand : m.ripple;
        v.set(x, CRUST, z, top);
        double breakUp = n1.get(x / 9.0, z / 9.0);
        if (e >= 0.93 && breakUp > -0.45) v.set(x, CRUST + 1, z, rnd(x, z, 7) < 0.3 ? m.pale : m.cracked);
        else if (e >= 0.89 && breakUp > 0.1) v.set(x, CRUST + 1, z, m.paleSlab);
        else {
            for (int[] c : cones) {
                double d = Math.hypot(x - c[0], z - c[1]);
                if (d >= c[2]) continue;
                int tall = (int) Math.round(c[3] * Math.pow(1 - d / c[2], 1.3));
                for (int k = 1; k <= tall; k++) v.set(x, CRUST + k, z, k == tall && rnd(x, z, 12) < 0.35 ? m.paleSlab : rnd(x + k, z, 13) < 0.3 ? m.cracked : m.crust);
                return;
            }
            if (top == m.sand || top == m.ripple) {
                if (rnd(x, z, 3) < 0.05) v.set(x, CRUST + 1, z, m.dry);
            }
        }
    }

    private BlockState rockOf(M m, int x, int y, int z) {
        int band = Math.floorMod(y + (int) (n1.get(x / 60.0, z / 60.0) * 7), 13);
        double s = n2.get(x / 18.0 + y / 40.0, z / 18.0);
        if (band < 2) return s > 0 ? m.deep : m.brick;
        if (band < 6) return s > 0.35 ? m.worn : m.brick;
        return s > 0.25 ? m.stoneA : s > -0.35 ? m.worn : m.pale;
    }

    BlockState course(M m, int x, int y, int z) {
        double p = n3.get((x + z) / 17.0, y / 13.0), f = rnd(x * 31L + z, y, 77);
        if (p > 0.5) return f < 0.7 ? m.worn : m.stoneA;
        if (p < -0.55) return f < 0.6 ? m.brick : m.stoneA;
        return f < 0.26 ? m.pale : f < 0.93 ? m.stoneA : m.worn;
    }

    static Direction toward(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
    }

    /**
     * One cell of honeycomb wall, the thing this whole city is made of. d counts in from the skin, u runs along the wall and w up it, s is the size of a cell.
     * Pale lattice stands proud, a brow over each mouth and a sill under it; behind the mouth is blue-black shade, and in about one cell out of ten somebody's left a lamp.
     */
    BlockState comb(M m, int x, int y, int z, double u, double w, double s, int d, Direction in, long lamps, double lit) {
        N.Hex c = N.hex(u, w, s);
        double e = c.edge(), dv = w - c.cz();
        if (d == 0) {
            if (e < 0.7) return null;
            if (e < 0.86) {
                if (dv > s * 0.42) return under(m.stoneStairs, in);
                if (dv < -s * 0.42) return m.stoneSlab;
            }
            return e > 0.95 ? m.pale : course(m, x, y, z);
        }
        if (d == 1) return e < 0.62 ? null : e > 0.9 ? m.stoneA : m.worn;
        if (d == 2) return e < 0.34 && rnd(c.q(), c.r(), lamps) < lit ? m.window : m.deep;
        return m.deep;
    }

    // ---- what's built here -------------------------------------------------------------------------------------------------

    /** A spire of comb: tiers that step in as it climbs, a railed ledge at the top of each, every face a lattice of cells, finishing in a needle. */
    final class Spire extends Piece<M> {
        final int cx, cz, foot, top, tiers, tierH;
        final double R;
        final double[] rad;
        final long lamps;

        Spire(int cx, int cz, double R, int H, int foot) {
            this.cx = cx;
            this.cz = cz;
            this.R = R;
            this.foot = foot;
            tierH = R > 20 ? 14 : R > 9 ? 10 : 7;
            tiers = Math.max(2, H / tierH);
            top = foot + tiers * tierH;
            rad = new double[tiers + 1];
            for (int i = 0; i <= tiers; i++) rad[i] = Math.max(1.2, R * Math.pow(1 - (double) i / tiers, 0.58) * (i == 0 || R < 14 ? 1 : 0.9 + 0.2 * rnd(cx + i, cz, 57)) + (i < tiers ? 1.5 : 0));
            lamps = seed + cx * 7L + cz;
            box(cx, cz, (int) R + 5, (int) R + 5);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, D = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                if (D > R + 3) continue;
                Direction in = toward(dx, dz);
                if (D <= R) v.foot(x, foot - 1, z, m.stoneA);
                for (int y = foot; y <= top + 7; y++) {
                    int h = y - foot;
                    if (h >= tiers * tierH) {
                        int over = h - tiers * tierH;
                        if (D <= 1.4 - over * 0.2) v.set(x, y, z, over % 2 == 0 ? m.pale : m.stoneA);
                        continue;
                    }
                    int k = h / tierH, th = h - k * tierH;
                    double r = rad[k] - 1.6 * th / tierH;
                    BlockState s = cell(m, x, y, z, D, a, in, r, k, th);
                    if (s != null) v.set(x, y, z, s);
                    else if (D <= r) v.set(x, y, z, null);
                }
            }
        }

        private BlockState cell(M m, int x, int y, int z, double D, double a, Direction in, double r, int k, int th) {
            double d = D - r;
            if (th == tierH - 1) {
                if (d <= 1) return d > 0 ? m.paleSlab : d > -2 ? m.pale : m.flag;
                return null;
            }
            if (th == tierH - 2 && d > 0 && d <= 1) return under(m.stoneStairs, in);
            if (d > 0) {
                // the rail along the ledge below this tier
                if (k > 0 && th <= 1) {
                    double edge = rad[k - 1] - 1.6 * (tierH - 1.0) / tierH;
                    if (D <= edge && D > edge - 1) return th == 0 ? (Math.floorMod((int) Math.floor((a + Math.PI) * edge / 2), 2) == 0 ? m.pale : null) : (Math.floorMod((int) Math.floor((a + Math.PI) * edge / 2), 2) == 0 ? m.paleSlab : null);
                }
                return null;
            }
            if (r < 3.2) return course(m, x, y, z);
            int inward = (int) Math.floor(-d);
            if (inward > 2) return m.deep;
            double s = R > 20 ? (k == 0 ? 7.5 : 5.2) : R > 9 ? (k == 0 ? 5 : 3.8) : 3;
            int n = Math.max(6, (int) Math.round(TAU * rad[k] / (S3 * s)));
            double u = (a + Math.PI) / TAU * n * S3 * s;
            return comb(m, x, y, z, u, th + 2.5, s, inward, in, lamps + k, 0.11);
        }
    }

    /** A dome of comb on a short drum. */
    final class CombDome extends Piece<M> {
        final int cx, cy, cz, R, n;
        final double s = 4.6;

        CombDome(int cx, int cy, int cz, int R) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.R = R;
            n = (int) Math.round(TAU * R / (S3 * s));
            box(cx, cz, R + 2, R + 2);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, D = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                if (D > R + 1.5) continue;
                Direction in = toward(dx, dz);
                if (D <= R) v.foot(x, cy - 9, z, m.stoneA);
                double u = (a + Math.PI) / TAU * n * S3 * s;
                for (int y = cy - 8; y <= cy + R + 7; y++) {
                    int h = y - cy;
                    if (h < 0) {
                        if (D > R + 1) continue;
                        if (h == -1 || h == -8) v.set(x, y, z, D > R ? under(m.paleStairs, in) : m.pale);
                        else if (D <= R) {
                            int d = (int) Math.floor(R - D);
                            BlockState c = d > 2 ? m.deep : comb(m, x, y, z, u, h, s, d, in, seed + cx, 0.2);
                            v.set(x, y, z, c);
                        }
                        continue;
                    }
                    double d3 = Math.hypot(D, h);
                    if (d3 > R + 0.4) {
                        if (D <= 1.2 && h <= R + 6 - (D > 0.8 ? 3 : 0)) v.set(x, y, z, h % 3 == 0 ? m.gold : m.pale);
                        else v.set(x, y, z, null);
                        continue;
                    }
                    int d = (int) Math.floor(R - d3);
                    double phi = Math.atan2(h, D);
                    BlockState c;
                    if (d > 2) c = m.deep;
                    else if (phi > 1.2) c = d == 0 ? m.pale : m.stoneA;
                    else c = comb(m, x, y, z, u, phi * R, s, d, in, seed + cz, 0.14);
                    v.set(x, y, z, c);
                }
            }
        }
    }

    /** A block of cells in the lower city, with pilasters at the corners, a string course every storey and a broken parapet. */
    final class Cells extends Piece<M> {
        final int cx, cz, hx, hz, foot, tall;
        final long lamps;

        Cells(int cx, int cz, int hx, int hz, int foot, int tall) {
            this.cx = cx;
            this.cz = cz;
            this.hx = hx;
            this.hz = hz;
            this.foot = foot;
            this.tall = tall;
            lamps = seed + cx * 13L + cz * 5L;
            box(cx, cz, hx + 1, hz + 1);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int ex = hx - Math.abs(x - cx), ez = hz - Math.abs(z - cz), e = Math.min(ex, ez);
                if (e < -1) continue;
                boolean corner = ex <= 1 && ez <= 1;
                boolean alongX = ez < ex;
                Direction in = alongX ? (z > cz ? Direction.NORTH : Direction.SOUTH) : (x > cx ? Direction.WEST : Direction.EAST);
                int along = alongX ? x : z;
                if (e < 0) {
                    if (ex >= -1 && ez >= -1 && !(ex < 0 && ez < 0)) v.set(x, foot + tall, z, under(m.paleStairs, in));
                    continue;
                }
                v.foot(x, foot - 1, z, m.stoneA);
                for (int y = foot; y <= foot + tall + 2; y++) {
                    int h = y - foot;
                    BlockState s;
                    if (h > tall) {
                        if (e > 0) s = null;
                        else s = h == tall + 1 ? (Math.floorMod(along, 4) < 2 || corner ? m.pale : null) : (corner ? m.paleSlab : Math.floorMod(along, 4) < 2 ? m.stoneSlab : null);
                    } else if (h == tall) s = e == 0 ? m.pale : m.flag;
                    else if (corner) s = Math.floorMod(h, 2) == 0 ? m.pale : course(m, x, y, z);
                    else if (e > 2) s = m.deep;
                    else if (Math.floorMod(h, 9) == 8) s = e == 0 ? under(m.paleStairs, in) : e == 1 ? m.pale : m.deep;
                    else if (h < 1) s = m.stoneA;
                    else s = comb(m, x, y, z, along * 1.0, Math.floorMod(h, 9) + 1.5, 3.6, e, in, lamps, 0.16);
                    if (s != null || h <= tall) v.set(x, y, z, s);
                }
            }
        }
    }

    /** A wrecked cell lying on its side: a six-sided tube of old wax with a thick pale rim, dark inside, half full of sand. */
    final class Tube extends Piece<M> {
        final int cx, cy, cz, len;
        final double s;

        Tube(int cx, int cy, int cz, double s, int len) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.s = s;
            this.len = len;
            box(cx, cz + len / 2, (int) (s * 1.2) + 1, len / 2 + 1);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                if (z < cz || z > cz + len) continue;
                int along = z - cz;
                for (int y = cy - (int) s - 1; y <= cy + (int) s + 1; y++) {
                    double t = hexMetric(x + 0.5 - cx, y + 0.5 - cy) / s;
                    if (t > 1) continue;
                    BlockState c;
                    if (t > 0.66) {
                        double f = rnd(x * 7L + y, z, 53);
                        if (along == len && t < 0.86) c = f < 0.5 ? m.paleSlab : m.pale;
                        else if (t > 0.93 && f < 0.1) continue;
                        else c = along >= len - 1 ? m.pale : f < 0.45 ? m.plaster : f < 0.8 ? m.pale : m.stoneA;
                    } else if (along < 3) c = m.deep;
                    else if (y < cy - s * 0.42 && along < len - 2) c = rnd(x, z, 54) < 0.5 ? m.sand : m.ripple;
                    else c = null;
                    v.set(x, y, z, c);
                }
            }
        }
    }

    /** Anything drawn cell by cell in advance: bones, ribs, awnings. */
    final class Vox extends Piece<M> {
        final List<int[]> at = new ArrayList<>();
        final List<BlockState> what = new ArrayList<>();

        Vox(String label) {
            this.label = label;
            mayOverlap = true;
            x0 = z0 = Integer.MAX_VALUE;
            x1 = z1 = Integer.MIN_VALUE;
        }

        void put(int x, int y, int z, BlockState s) {
            at.add(new int[]{x, y, z});
            what.add(s);
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }

        void ball(double cx, double cy, double cz, double rx, double ry, double rz, BlockState s, BlockState alt) {
            for (int x = (int) (cx - rx); x <= cx + rx; x++) for (int z = (int) (cz - rz); z <= cz + rz; z++) for (int y = (int) (cy - ry); y <= cy + ry; y++) {
                if (N.sq((x + 0.5 - cx) / rx) + N.sq((y + 0.5 - cy) / ry) + N.sq((z + 0.5 - cz) / rz) <= 1) put(x, y, z, alt != null && rnd(x * 3L + y, z, 61) < 0.25 ? alt : s);
            }
        }

        @Override
        public void draw(V v, M m) {
            for (int i = 0; i < at.size(); i++) {
                int[] c = at.get(i);
                if (v.in(c[0], c[1], c[2])) v.set(c[0], c[1], c[2], what.get(i));
            }
        }
    }

    /** A great arch of dark comb-stone, or a rib of a thing that died here. */
    private void rib(Vox p, double ax, double az, double bx, double bz, int foot, double rise, double thick, BlockState s, BlockState alt) {
        int steps = (int) (Math.hypot(bx - ax, bz - az) * 1.6 + rise * 1.6);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, y = foot + rise * Math.sin(t * Math.PI), w = thick * (1 - 0.45 * Math.sin(t * Math.PI));
            p.ball(ax + (bx - ax) * t, y, az + (bz - az) * t, w, w, w, s, alt);
        }
    }

    private void skeleton(int x, int z, double dirX, double dirZ, int ribs, double size) {
        M m = m();
        Vox p = new Vox("Skeleton");
        double sx = -dirZ, sz = dirX;
        int gy = ground(x, z);
        for (int i = 0; i < ribs; i++) {
            double t = i * 4.6 * size, cx = x + dirX * t, cz = z + dirZ * t, len = (ribs - i * 0.55) * 1.2 * size + 5;
            p.ball(cx, gy + 0.5, cz, 1.8, 1.8, 1.8, m.bone, m.boneWorn);
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k <= 36; k++) {
                    double u = k / 36.0 * 0.82, out = Math.sin(u * Math.PI) * len * 0.62, up = (1 - Math.cos(u * Math.PI)) * 0.5 * len * 1.25, w = Math.max(0.95, 1.1 - 0.45 * u);
                    p.ball(cx + sx * side * out, gy + up, cz + sz * side * out, w, w, w, rnd(i, k, 62) < 0.22 ? m.boneWorn : m.bone, null);
                }
            }
        }
        double hx = x - dirX * 12 * size, hz = z - dirZ * 12 * size, hy = gy + 4 * size;
        p.ball(hx, hy, hz, 6 * size, 5 * size, 6.5 * size, m.bone, m.boneWorn);
        p.ball(hx - dirX * 7 * size, hy - 1.5 * size, hz - dirZ * 7 * size, 3.5 * size, 2.6 * size, 4 * size, m.bone, m.boneWorn);
        pieces.add(p);
        Vox holes = new Vox("Skull sockets");
        for (int side = -1; side <= 1; side += 2) {
            double ex = hx + sx * side * 4.4 * size - dirX * 2 * size, ez = hz + sz * side * 4.4 * size - dirZ * 2 * size;
            for (int a = (int) (ex - 3); a <= ex + 3; a++) for (int c = (int) (ez - 3); c <= ez + 3; c++) for (int b = (int) (hy - 2); b <= hy + 3; b++) {
                if (N.sq(a + 0.5 - ex) + N.sq(b + 0.5 - hy - 0.5) + N.sq(c + 0.5 - ez) <= 5.5 * size) holes.put(a, b, c, m.deep);
            }
        }
        pieces.add(holes);
    }

    /** Two poles leaning in the wind, a sheet of torn cloth slung between them and a lamp on a chain from each. */
    private void awning(int ax, int az, int bx, int bz, int wide, int tall) {
        M m = m();
        Vox p = new Vox("Awning");
        int ga = ground(ax, az), gb = ground(bx, bz), topA = ga + tall, topB = gb + tall - 1;
        pole(p, m, ax, az, ga, topA);
        pole(p, m, bx, bz, gb, topB);
        double len = Math.hypot(bx - ax, bz - az), ux = (bx - ax) / len, uz = (bz - az) / len, sx = -uz, sz = ux;
        for (double t = 0; t <= len; t += 0.5) for (double w = -wide; w <= wide; w += 0.5) {
            double y0 = topA + (topB - topA) * t / len - 1 - 3.2 * Math.sin(t / len * Math.PI) - Math.abs(w) * 0.55;
            int x = (int) Math.floor(ax + 0.5 + ux * t + sx * w), z = (int) Math.floor(az + 0.5 + uz * t + sz * w), y = (int) Math.floor(y0);
            if (Math.abs(w) > 1 && rnd(x, z, 63) < 0.09) continue;
            p.put(x, y, z, m.cloth);
            if (w == -wide || w == wide) {
                int drop = (int) (rnd(x, z, 64) * 4);
                for (int k = 1; k <= drop; k++) p.put(x, y - k, z, m.cloth);
            }
        }
        pieces.add(p);
    }

    private void pole(Vox p, M m, int x, int z, int g, int top) {
        for (int y = g - 3; y <= top; y++) p.put(x, y, z, m.timber);
        p.put(x, top + 1, z, m.planks);
        p.put(x + 1, top - 1, z, m.planks);
        p.put(x + 1, top - 2, z, m.chain);
        p.put(x + 1, top - 3, z, m.hung);
    }

    /** A heap of wrecked cells: columns of tubes packed the way comb packs, spilling a little sand. */
    private void heap(int cx, int gy, int cz, double s, int[] rows, int len) {
        for (int i = 0; i < rows.length; i++) {
            int x = cx + (int) Math.round(i * 1.66 * s);
            for (int j = 0; j < rows[i]; j++) {
                int y = gy + (int) Math.round(s * 0.8 + j * 1.92 * s + (i % 2 == 1 ? s * 0.96 : 0));
                pieces.add(new Tube(x, y, cz + (int) jit(2.5), s, len + (int) jit(3)));
            }
        }
    }

    private void foreground() {
        heap(-92, CRUST, 200, 5.8, new int[]{1, 2, 3, 2, 1}, 7);
        heap(44, CRUST, 194, 5.4, new int[]{2, 3, 2, 1}, 7);
        skeleton(-26, 200, -0.82, 0.57, 7, 0.82);
        awning(-40, 214, -62, 204, 3, 14);
        awning(32, 212, 52, 202, 3, 13);
    }

    private void flats() {
        for (int[] c : new int[][]{{-120, 120, 14, 7}, {-152, 68, 9, 5}, {96, 150, 11, 6}, {136, 92, 15, 8}, {-90, 40, 8, 4}, {78, 52, 10, 5}, {150, 240, 12, 6}, {-160, 210, 13, 6}, {-44, 158, 7, 4}, {60, 110, 6, 3}}) cones.add(c);
        heap(-146, CRUST, 134, 5.4, new int[]{1, 2, 1}, 7);
        heap(112, CRUST, 92, 5.2, new int[]{2, 1}, 7);
        heap(70, CRUST, 18, 6, new int[]{1, 2, 2}, 8);
        heap(-92, CRUST, 26, 5.6, new int[]{2, 2, 1}, 8);
        skeleton(118, 164, 0.6, -0.8, 6, 0.8);
        skeleton(-84, 80, -0.5, -0.86, 5, 0.7);
        for (int i = 5; i < stones.size(); i += 3) {
            Stone s = stones.get(i);
            if (s.r > 9) continue;
            int side = i % 2 == 0 ? -1 : 1;
            Vox p = new Vox("Road lamp");
            int x = s.x + side * (s.r - 2);
            pole(p, m(), x, s.z, s.top, s.top + 6);
            pieces.add(p);
        }
    }

    private void spire(int cx, int cz, double R, int H) {
        int foot = ground(cx, cz) - 8;
        Spire s = new Spire(cx, cz, R, H, foot);
        pieces.add(s);
        if (R < 14) return;
        // little spires on the ledges, so the skyline is a crown and not a cone
        for (int tier = s.tiers / 3; tier < s.tiers - 1; tier += 2) {
            int count = 4 + Math.floorMod(tier, 3);
            for (int i = 0; i < count; i++) {
                double a = (i + tier * 0.37) * TAU / count, d = s.rad[tier] * 0.86;
                int left = (s.tiers - tier) * s.tierH;
                pieces.add(new Spire(cx + (int) (Math.cos(a) * d), cz + (int) (Math.sin(a) * d), Math.max(3.4, s.rad[tier] * 0.3), (int) (left * (0.55 + 0.35 * rnd(i, tier, 58))), foot + tier * s.tierH - 2));
            }
        }
    }

    private void city() {
        spire(-128, -92, 42, 190);
        spire(-52, -168, 34, 150);
        spire(24, -190, 48, 250);
        spire(118, -128, 40, 215);
        spire(188, -60, 24, 110);
        spire(-214, -40, 20, 92);
        spire(-26, 2, 12, 62);
        spire(26, 2, 12, 62);
        pieces.add(new CombDome(-82, ground(-82, -134) + 18, -134, 24));
        pieces.add(new CombDome(86, ground(86, -176) + 24, -176, 28));
        pieces.add(new CombDome(-184, ground(-184, -100) + 8, -100, 16));
        Vox arches = new Vox("Ribs");
        M m = m();
        for (int i = 0; i < 6; i++) {
            int z = -142 + i * 17, foot = ground(216, z) - 2;
            rib(arches, 186 + i * 2, z, 256 + i * 2, z, foot, 50 - i * 4, 2.8, m.deep, m.stoneA);
        }
        rib(arches, -128, -92, -52, -168, ground(-90, -130) + 70, 26, 2.4, m.deep, m.stoneA);
        rib(arches, 24, -190, 118, -128, ground(70, -160) + 96, 30, 2.6, m.deep, m.stoneA);
        rib(arches, -52, -168, 24, -190, ground(-14, -180) + 70, 24, 2.2, m.deep, m.stoneA);
        pieces.add(arches);
        // the lower city: stepped heaps of cells, each storey set back on the one under it
        int placed = 0;
        for (int tries = 0; tries < 2400 && placed < 110; tries++) {
            int x = r.nextInt(500) - 250, z = -22 - r.nextInt(170);
            if (Math.hypot(x, z - CCZ) < RO + 9 || Math.abs(x) < 9 && z > -10) continue;
            boolean free = true;
            for (int[] c : keepClear) if (Math.abs(c[0] - x) < c[2] + 14 && Math.abs(c[1] - z) < c[2] + 14) free = false;
            if (!free) continue;
            int hx = 5 + r.nextInt(8), hz = 5 + r.nextInt(8), foot = ground(x, z) - 1, levels = 1 + r.nextInt(4);
            for (int l = 0; l < levels && hx >= 4 && hz >= 4; l++) {
                int tall = 9 * (1 + r.nextInt(2)) - 1;
                pieces.add(new Cells(x, z, hx, hz, foot, tall));
                foot += tall + 1;
                x += r.nextInt(5) - 2;
                z += r.nextInt(5) - 2;
                hx -= 2 + r.nextInt(2);
                hz -= 2 + r.nextInt(2);
            }
            placed++;
        }
    }

    private void village() {
        int[][] huts = {{-154, 164, 6}, {-136, 142, 6}, {-172, 146, 5}, {-142, 186, 5}, {-124, 170, 5}, {-168, 182, 4}};
        for (int[] h : huts) pieces.add(new Hut(h[0], h[1], h[2], Math.atan2(160 - h[1], -150 - h[0]), seed + h[0], this));
        for (int[] s : new int[][]{{-150, 152}, {-160, 170}, {-140, 160}}) pieces.add(new Stall(s[0], s[1], seed + s[0], this));
        for (int[] s : new int[][]{{-96, -18}, {-84, -30}, {-104, -34}}) pieces.add(new Stall(s[0], s[1], seed + s[0], this));
        awning(-158, 150, -142, 142, 3, 11);
    }

    private void placeSites() {
        site(SiteKind.MOUTH, 0, CRUST + 2, 244, 12, "");
        site(SiteKind.PORTAL, 0, CRUST + 2, 250, 2, "");
        pieces.add(new MouthRing(0, CRUST + 1, 247, this));
        site(SiteKind.LORD, 0, F + 1, CCZ + 18, 40, "");
        int[][] wardens = {{140, 150}, {-92, -36}, {90, -60}};
        String[] wn = {"flats", "market", "dome"};
        for (int i = 0; i < 3; i++) {
            site(SiteKind.WARDEN, wardens[i][0], ground(wardens[i][0], wardens[i][1]) + 1, wardens[i][1], 14, wn[i]);
            keepClear.add(new int[]{wardens[i][0], wardens[i][1], 16});
        }
        site(SiteKind.VILLAGE, -150, ground(-150, 160) + 1, 160, 34, "");
        for (int[] d : new int[][]{{-210, 90}, {-170, 40}, {-140, -50}, {180, 40}, {210, 120}, {150, -20}, {-30, 40}, {40, 40}}) {
            site(SiteKind.DEN, d[0], ground(d[0], d[1]) + 1, d[1], 10, "");
            keepClear.add(new int[]{d[0], d[1], 12});
        }
        waystone(14, 240, "the black road");
        waystone(-14, 44, "the court steps");
        waystone(-74, -28, "the market");
        keepClear.add(new int[]{-74, -28, 8});
        int[][] low = {{-44, 182}, {60, 170}, {-100, 118}, {100, 106}, {-60, 96}, {40, 96}, {-188, 118}, {188, 90}, {-140, 16}, {140, -6}, {-200, -8}, {74, 16}, {-74, 14}};
        int i = 0;
        for (int[] f : low) {
            int gy = ground(f[0], f[1]);
            pieces.add(new Find(f[0], gy, f[1], seed + i++));
            site(SiteKind.FIND, f[0], gy + 1, f[1], 2, "");
            keepClear.add(new int[]{f[0], f[1], 6});
        }
        int[][] high = {{0, F + 30, CCZ - RW + 2}, {-41, F + 30, CCZ - 41}};
        for (int[] f : high) {
            pieces.add(new Find(f[0], f[1], f[2], seed + i++));
            site(SiteKind.FIND, f[0], f[1] + 1, f[2], 2, "high");
        }
    }

    // ---- the Lord's place --------------------------------------------------------------------------------------------------

    private void courtFurniture() {
        M m = m();
        Vox p = new Vox("Court furniture");
        for (int i = 0; i < 12; i++) {
            double t = (i + 0.5) * TAU / 12;
            int x = (int) Math.round(Math.cos(t) * (RI - 2)), z = CCZ + (int) Math.round(Math.sin(t) * (RI - 2));
            if (Math.abs(x) < 12 && z > CCZ) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.put(x + dx, F + 1, z + dz, dx == 0 && dz == 0 ? m.pale : m.stoneSlab);
            p.put(x, F + 2, z, m.pale);
            p.put(x, F + 3, z, m.brazier);
            if (i % 2 == 0) {
                // a tall pole with a torn ochre flag on it
                double t2 = t + 0.13;
                int fx = (int) Math.round(Math.cos(t2) * (RI + 1)), fz = CCZ + (int) Math.round(Math.sin(t2) * (RI + 1)), base = F + (RI + 1 - RI) / 2 + 1;
                for (int y = base; y <= F + 18; y++) p.put(fx, y, fz, m.timber);
                p.put(fx, F + 19, fz, m.gold);
                Direction along = toward(Math.sin(t2), -Math.cos(t2));
                for (int k = 1; k <= 3; k++) {
                    int cx = fx + along.getStepX() * k, cz = fz + along.getStepZ() * k, drop = 9 - k + (int) (rnd(k, i, 65) * 3);
                    for (int d = 0; d <= drop; d++) if (d < 3 || rnd(k, d + i * 13, 68) > 0.2) p.put(cx, F + 18 - d, cz, m.cloth);
                }
            }
        }
        pieces.add(p);
    }

    final class Egg extends Piece<M> {
        final int cx, cz, R;

        Egg(int cx, int cz, int R) {
            this.cx = cx;
            this.cz = cz;
            this.R = R;
            box(cx, cz, R + 1, R + 1);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, D = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                for (int y = F + 1; y <= F + R + 1; y++) {
                    int h = y - F;
                    double d3 = Math.hypot(D, h);
                    if (d3 > R + 0.3) continue;
                    int d = (int) Math.floor(R - d3);
                    if (d >= 2) continue;
                    N.Hex t = N.hex(a * R * Math.cos(Math.atan2(h, D)), Math.atan2(h, D) * R, 3.8);
                    // a bite out of one side, whole tiles at a time, from the top down, so what's left still stands on the floor
                    if (a > 0.15 && a < 1.55 && h > 2 + 3 * rnd(t.q(), t.r(), 69)) continue;
                    if (a > 0.0 && a < 1.7 && h > 8 + 4 * rnd(t.q(), t.r(), 70)) continue;
                    v.set(x, y, z, d == 1 ? m.deep : t.edge() > 0.84 ? m.worn : rnd(t.q(), t.r(), 66) < 0.5 ? m.pale : m.hexstone);
                }
                if (D < R - 2 && rnd(x, z, 67) < 0.5) v.set(x, F + 1, z, m.sand);
            }
        }
    }

    /** The drum of the court: a floor of hexagons over brine, tiers of steps, a wall of lit niches, a corbelled roof with a hole in the top. */
    final class Court extends Piece<M> {
        final int nIn = (int) Math.round(TAU * RW / (S3 * 6)), nOut = (int) Math.round(TAU * RO / (S3 * 5.5));

        Court() {
            box(0, CCZ, RO + 6, RO + 6);
            mayOverlap = true;
        }

        @Override
        public void draw(V v, M m) {
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double dx = x + 0.5, dz = z + 0.5 - CCZ, D = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                if (D > RO + 6) continue;
                Direction in = toward(dx, dz), out = in.getOpposite();
                int batter = (int) Math.ceil(RO + 5 - D);
                for (int y = BASIN - 4; y <= F + 110; y++) if (D <= RO + 3) v.set(x, y, z, null);
                if (D <= RO + 5) {
                    int base = F - Math.min(16, batter * 3);
                    v.foot(x, base, z, m.stoneA);
                    for (int y = base; y <= F; y++) if (D > RO) v.set(x, y, z, y == F && D <= RO + 1 ? m.pale : rockFace(m, x, y, z));
                }
                if (D > RO + 3) continue;
                double arc = (a + Math.PI) / TAU * 12 + 0.5;
                int pi = Math.floorMod((int) Math.round(arc), 12);
                double pw = Math.abs(arc - Math.round(arc)) * TAU * RW / 12, pwO = Math.abs(arc - Math.round(arc)) * TAU * RO / 12;
                floorAndTiers(v, m, x, z, D, a, out);
                if (D >= RW) wall(v, m, x, z, D, a, in, out, pwO);
                innerDress(v, m, x, z, D, a, in, out, pi, pw);
                roof(v, m, x, z, D, a, in, pi, pwO);
                door(v, m, x, z, dx, dz, D);
            }
        }

        private BlockState rockFace(M m, int x, int y, int z) {
            return Math.floorMod(y, 5) == 0 ? m.pale : course(m, x, y, z);
        }

        private void floorAndTiers(V v, M m, int x, int z, double D, double a, Direction out) {
            if (D >= RW) {
                for (int y = F - 8; y <= F + 12; y++) v.set(x, y, z, m.stoneA);
                return;
            }
            v.set(x, F - 6, z, m.crust);
            if (D < RI) {
                for (int y = F - 5; y <= F - 2; y++) v.set(x, y, z, m.water);
                v.set(x, F - 1, z, m.bed);
                v.set(x, F, z, tile(m, x, z));
                return;
            }
            for (int y = F - 5; y <= F; y++) v.set(x, y, z, m.stoneA);
            double t = (D - RI) / 1.5;
            int n = (int) Math.floor(t);
            boolean half = t - n >= 0.5;
            int h = Math.min(12, n);
            for (int y = F + 1; y <= F + h; y++) v.set(x, y, z, y == F + h ? (n % 2 == 0 ? m.stoneA : m.worn) : m.stoneA);
            if (half && h < 12) v.set(x, F + h + 1, z, m.stoneSlab);
        }

        private void wall(V v, M m, int x, int z, double D, double a, Direction in, Direction out, double pwO) {
            double uIn = (a + Math.PI) / TAU * nIn * S3 * 6, uOut = (a + Math.PI) / TAU * nOut * S3 * 5.5;
            int di = (int) Math.floor(D - RW), dO = (int) Math.floor(RO - D);
            for (int y = F + 13; y < F + SPRING; y++) {
                int h = y - F;
                BlockState s;
                if (di <= 2) s = comb(m, x, y, z, uIn, h, 6, di, out, seed + 71, 0.24);
                else if (D > RO) s = pwO <= 2.5 ? course(m, x, y, z) : Math.floorMod(h, 20) == 0 && D <= RO + 1 ? under(m.paleStairs, in) : null;
                else if (dO <= 2) {
                    s = pwO <= 2.5 ? course(m, x, y, z) : Math.floorMod(h, 20) == 0 ? (dO == 0 ? m.pale : m.stoneA) : comb(m, x, y, z, uOut, h, 5.5, dO, in, seed + 72, 0.1);
                } else s = m.stoneA;
                v.set(x, y, z, s);
            }
            if (D > RO) {
                for (int y = F + 1; y <= F + 12; y++) {
                    int h = y - F;
                    v.set(x, y, z, D > RO + 1 ? (h <= 5 && h <= (RO + 3 - D) * 2.5 ? course(m, x, y, z) : pwO <= 2.5 && h <= 12 ? m.pale : null) : pwO <= 2.5 ? m.pale : course(m, x, y, z));
                }
            }
        }

        private void innerDress(V v, M m, int x, int z, double D, double a, Direction in, Direction out, int pi, double pw) {
            if (D >= RW + 1) return;
            // pilasters from the top tier to the roof
            if (pw <= 2.6 && D >= RW - 2) for (int y = F + 12; y < F + SPRING; y++) {
                int h = y - F;
                boolean band = Math.floorMod(h, 12) == 0;
                if (D >= RW - 1 || pw <= 1.4 || band) v.set(x, y, z, band ? m.pale : pw > 2 ? m.pale : course(m, x, y, z));
            }
            boolean runs = pi % 3 == 1 && pw <= 1.3;
            // the gallery, notched where the sand comes down through it, and two stairs up to it from the top tier
            if (D >= RW - 4 && D < RW) {
                if (!(runs && D >= RW - 3 && D < RW - 2)) for (int y = F + 29; y <= F + 30; y++) v.set(x, y, z, y == F + 30 ? (rnd(x, z, 73) < 0.5 ? m.flag : m.hexstone) : m.stoneA);
                if (D >= RW - 2) v.set(x, F + 28, z, under(m.stoneStairs, out));
                for (int y = F + 31; y <= F + 34; y++) if (pw > 2.6) v.set(x, y, z, null);
                if (D < RW - 3 && Math.floorMod((int) Math.floor((a + Math.PI) * (RW - 3.5) / 2), 2) == 0 && (pi % 3 != 1 || pw > 3)) {
                    v.set(x, F + 31, z, m.pale);
                    v.set(x, F + 32, z, m.paleSlab);
                }
                flight(v, m, x, z, a, D);
                flight(v, m, x, z, Math.atan2(z + 0.5 - CCZ, -(x + 0.5)), D);
            }
            if (runs && D >= RW - 3 && D < RW - 2) {
                int top = F + SPRING - 6;
                v.set(x, top, z, under(m.stoneStairs, out));
                for (int y = top - 1; y > F; y--) {
                    if (v.solid(x, y, z)) break;
                    v.set(x, y, z, m.fall);
                }
            }
            if (D >= RW - 1 && D < RW) v.set(x, F + SPRING - 1, z, under(m.paleStairs, out));
        }

        /** Runs up the wall from the top tier to the gallery. Called with the angle mirrored for the far side. */
        private void flight(V v, M m, int x, int z, double a, double D) {
            double s = (a + 0.35) / 0.62;
            if (s < 0 || s >= 1) return;
            // the stair keeps to the inner half of the ledge, so you can still walk the gallery round behind it, through a gap in the pilasters
            if (D >= RW - 2) {
                for (int y = F + 31; y <= F + 33; y++) v.set(x, y, z, null);
                return;
            }
            int top = 11 + (int) Math.floor(s * 19);
            for (int y = F + 9; y <= F + top; y++) v.set(x, y, z, y == F + top ? m.flag : m.stoneA);
            for (int y = F + top + 1; y <= F + 34; y++) v.set(x, y, z, null);
        }

        private void roof(V v, M m, int x, int z, double D, double a, Direction in, int pi, double pwO) {
            if (D < OCULUS || D > RO + 1) return;
            int b = D <= RW ? F + SPRING + (int) ((RW - D) / 1.3) : F + SPRING, t = b + 6;
            for (int y = b; y <= t; y++) {
                int d = y - b;
                BlockState s;
                if (d <= 2 && D <= RW - 0.5) s = comb(m, x, y, z, x + 0.5, z + 0.5, 6, d, in, seed + 74, 0.2);
                else if (y == t) s = pwO <= 2.5 || pi % 3 == 0 && Math.abs((a + Math.PI) * D - Math.round((a + Math.PI) * D / 12) * 12) < 1 ? m.pale : rnd(x, z, 75) < 0.2 ? m.worn : m.tileDeep;
                else s = m.stoneA;
                v.set(x, y, z, s);
            }
            if (D <= OCULUS + 2) {
                v.set(x, t, z, m.pale);
                if (Math.floorMod((int) Math.floor((a + Math.PI) * OCULUS / 3), 2) == 0) {
                    v.set(x, t + 1, z, m.pale);
                    v.set(x, t + 2, z, m.paleSlab);
                }
            }
            if (D >= RO - 1) {
                v.set(x, t, z, m.pale);
                if (Math.floorMod((int) Math.floor((a + Math.PI) * RO / 3), 2) == 0) v.set(x, t + 1, z, m.pale);
            }
        }

        /** The doorway and the aisle through the tiers: a tall arch, pale at the jambs, wide enough for what lives inside. */
        private void door(V v, M m, int x, int z, double dx, double dz, double D) {
            if (dz <= 0 || Math.abs(dx) > 9.5 || D < RI - 1) return;
            int ax = Math.abs(x + (x < 0 ? 1 : 0));
            for (int y = F + 1; y <= F + 20; y++) {
                int h = y - F, top = ax <= 7 ? doorTop(x) : 0;
                if (ax <= 7 && h <= top) v.set(x, y, z, null);
                else if (D >= RW - 5 && h <= top + 2 && (ax >= 8 || h > top)) v.set(x, y, z, h % 3 == 0 ? m.gold : m.pale);
            }
            v.set(x, F, z, tile(m, x, z));
        }
    }

    /** The black road: hexagon stones standing proud of the salt, worn pale down the middle, a chipped lip round the edge. */
    final class Stones extends Piece<M> {
        Stones() {
            x0 = -40;
            x1 = 40;
            z0 = 0;
            z1 = 262;
            mayOverlap = true;
            startsWay = true;
        }

        @Override
        public void draw(V v, M m) {
            for (Stone s : stones) {
                for (int x = Math.max(ax(v), s.x - s.r - 2); x <= Math.min(bx(v), s.x + s.r + 2); x++) for (int z = Math.max(az(v), s.z - s.r - 2); z <= Math.min(bz(v), s.z + s.r + 2); z++) {
                    double md = hexMetric(x + 0.5 - s.x, z + 0.5 - s.z) / s.r;
                    if (md > 1) continue;
                    double f = rnd(x, z, 81);
                    for (int y = BASIN; y < s.top; y++) v.set(x, y, z, m.flag);
                    BlockState top = md > 0.88 ? (f < 0.1 ? null : f < 0.55 ? m.flagSlab : m.flag) : m.flag;
                    if (top != null) v.set(x, s.top, z, top);
                    else v.set(x, s.top, z, null);
                    if (md > 0.84 && f > 0.97) v.set(x, s.top + 1, z, m.crumbs);
                }
            }
        }
    }
}
