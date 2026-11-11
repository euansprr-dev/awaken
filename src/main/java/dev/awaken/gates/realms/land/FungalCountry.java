package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.block.DepthBlocks;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.N.Hex;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.land.GeneratedLand;
import dev.awaken.gates.realms.kit.land.LandPieces;
import dev.awaken.gates.realms.kit.land.Landform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;

/**
 * The Fungal Hollow's open country (SPEC §50.9, "Rolled lands"): the skin of a rolled land, in the hollow's own blocks,
 * under a night sky. Everything that stands is a glowcap: a table of the Isles is a cap, purple flesh with glowing moss
 * over most of it and bare loam where anything walks, its stalk a pale stem, its whole underside gills that light the
 * sea beneath; a span between two of them is a strand of the same stem. Glowcaps the size of trees stand on the caps in
 * stands, light pours off the rims into the sea in threads, and lesser things glow underfoot. Its great thing is a cap
 * with a ring of glowcaps round the Lord's floor and one more, far taller, growing out of its back.
 * Its first land is the High Caps, on the Isles.
 */
public final class FungalCountry extends GeneratedLand {
    private static final List<List<String>> HERDS = List.of(List.of("sporeling*4", "mycelid*2"), List.of("capweaver*3", "puffcap*2"), List.of("mycelid*3", "hollow_tender"),
            List.of("sporeling*3", "capweaver*2"), List.of("puffcap*3", "mycelid*2"));
    private static final List<List<String>> AMBUSHES = List.of(List.of("sporeling*6", "puffcap*2"), List.of("capweaver*3", "sporeling*3"));

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    private static final class Own {
        final BlockState rock = b("fungal_rock"), veined = b("fungal_rock_veined"), loam = b("fungal_loam"), moss = b("fungal_moss"), drift = b("fungal_moss_drift"), stem = b("glowcap_stem"),
                flesh = b("glowcap_cap"), gills = b("glowcap_gills"), sprouts = b("glowcap_sprouts"), threads = b("mycelium_threads"), fall = b("fungal_glowfall"), spores = b("fungal_spores");
        final Dress dress = new Dress(moss, drift, loam, b("fungal_loam_slab"), b("fungal_loam_stairs"), rock, b("fungal_rock_slab"), b("fungal_rock_stairs"), loam, veined, gills, null, fall,
                Blocks.WATER.defaultBlockState(), stem, flesh, gills, stem, Blocks.IRON_CHAIN.defaultBlockState());

        BlockState layers(int n) {
            return drift.setValue(DepthBlocks.Drift.LAYERS, Math.max(1, Math.min(6, n)));
        }
    }

    private static volatile Own own;

    private static Own own() {
        Own o = own;
        if (o == null) synchronized (FungalCountry.class) {
            if ((o = own) == null) own = o = new Own();
        }
        return o;
    }

    private SimplexNoise groveNoise;

    public FungalCountry(Ctx ctx) {
        super(ctx, 0x46554E47L);
        generate(Landform.of(ctx));
    }

    @Override
    protected Dress makeDress() {
        return own().dress;
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) (seed % 97), HERDS.size()));
    }

    @Override
    protected List<String> ambushPack(int index) {
        return AMBUSHES.get(Math.floorMod(index + (int) (seed % 89), AMBUSHES.size()));
    }

    @Override
    protected List<String> wardenPack(int index) {
        return List.of("mycelid*2", "capweaver");
    }

    @Override
    protected int airTint() {
        return 0x8ff0e8;
    }

    @Override
    public String heartClosedLine() {
        return "Stems have grown up across the way in, packed as tight as a fence. Whatever waits where the last span comes in is feeding them.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        // mist on the sea, lit from under the caps
        return airOf(new AirPlan().mist(WATER + 1, 3, 0x6fd8d0, 0.35F));
    }

    @Override
    protected Piece<? super Kit> hall(int cx, int cz, int feet, double radius, long salt) {
        return new Grove(cx, cz, feet, radius, salt, this);
    }

    /** A cap's top: glowing moss over most of it, bare flesh at its shoulder and in patches, loam wherever anything walks. */
    @Override
    protected BlockState cap(Spot s) {
        Own o = own();
        if (s.crust) return o.gills;
        if (s.wet) return o.loam;
        switch (s.way) {
            case STAIR:
                return o.rock;
            case FLOOR:
            case SPUR:
                return o.loam;
            case TRAIL:
                return s.mid < 1.4F ? (s.roll < 0.9 ? o.loam : o.moss) : s.patch > -0.1 ? o.moss : o.loam;
            case PAD:
                return s.patch > 0 ? o.moss : o.loam;
            default:
        }
        if (s.built || s.columns || s.drop >= 1 || s.drift < -0.3) return o.flesh;
        return o.moss;
    }

    @Override
    protected BlockState under(Spot s, BlockState cap) {
        return own().flesh;
    }

    /** A cap's side is its flesh; the flank of a span is stem; where a cap is torn the flesh glows. */
    @Override
    protected BlockState face(Spot s, int y) {
        Own o = own();
        if (s.heat > 0.5) return N.rnd(s.x * 31L + y, s.z, seed + 33) < 0.22 ? o.gills : o.flesh;
        return s.off <= 3.2F ? o.stem : o.flesh;
    }

    @Override
    protected BlockState stalk(int part, int x, int y, int z) {
        Own o = own();
        if (part == CAP_UNDER) return N.rnd(Math.floorDiv(x, 2) * 31L + Math.floorDiv(z, 2), 5, seed + 34) < 0.14 ? o.flesh : o.gills;
        return o.stem;
    }

    /** A den's place: standing stones round it, and at every other one a glowcap either side. */
    @Override
    protected void denPlace(int x, int feet, int z, int radius, int kind, double wayX, double wayZ) {
        pieces.add(new LandPieces.StoneRing(x, z, radius - 0.8, kind % 2 == 0 ? 7 : 5, wayX, wayZ, seed + 500 + kind, this));
        if (kind % 2 == 0) return;
        double away = Math.atan2(z - wayZ, x - wayX), across = away + Math.PI / 2;
        for (int side = -1; side <= 1; side += 2) {
            int px = x + (int) Math.round(Math.cos(away) * (radius - 2) + Math.cos(across) * 5 * side), pz = z + (int) Math.round(Math.sin(away) * (radius - 2) + Math.sin(across) * 5 * side);
            if (wayDist(px, pz) > 4.5 && open(px, pz, 1, 2)) pieces.add(new Glowcap(px, pz, 8, 3.4, seed + 520 + kind + side, this));
        }
    }

    /** Lamps of the same stuff along the way; glowcaps the size of trees in stands, with clearings between; light pouring off the rims into the sea. */
    @Override
    protected void furnish() {
        List<double[]> way = wayPoints();
        Site mouth = mouth();
        int side = 1;
        for (int i = 16; i + 3 < way.size(); i += 14 + r.nextInt(5)) {
            double[] a = way.get(i - 2), c = way.get(i + 2), at = way.get(i);
            double dx = c[0] - a[0], dz = c[1] - a[1], l = Math.hypot(dx, dz);
            if (l < 2 || l > 6) continue;
            int x = (int) Math.round(at[0] - dz / l * 4.4 * side), z = (int) Math.round(at[1] + dx / l * 4.4 * side);
            side = -side;
            if (!open(x, z, 1, 1) || Math.abs(ground(x, z) + 1 - at[2]) > 2 || wayDist(x, z) < 3.8) continue;
            maybe(new LandPieces.LampPost(x, z, 2 + r.nextInt(2), this));
        }
        groveNoise = N.noise(seed + 7300);
        int half = halfSize() - 40, stood = 0;
        for (int i = 0; i < 9000 && stood < 170; i++) {
            int x = r.nextInt(2 * half) - half, z = r.nextInt(2 * half) - half;
            if (ground(x, z) < WATER + 8 || N.fbm(groveNoise, x, z, 1 / 30.0, 2) < 0.02) continue;
            if (!open(x, z, 3, 2) || !offWays(x, z, 6) || Math.hypot(x - mouth.x(), z - mouth.z()) < 16) continue;
            boolean great = r.nextInt(5) == 0;
            pieces.add(new Glowcap(x, z, great ? 11 + r.nextInt(6) : 4 + r.nextInt(5), great ? 4.2 + r.nextDouble() * 1.4 : 2.2 + r.nextDouble() * 1.2, seed + 7400 + i, this));
            stood++;
        }
        // threads of light off the rims: where a cap's edge stands straight over the sea
        for (int i = 0, falls = 0; i < 20000 && falls < 70; i++) {
            int x = r.nextInt(2 * half) - half, z = r.nextInt(2 * half) - half, top = ground(x, z);
            if (top < WATER + 12 || !offWays(x, z, 5)) continue;
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int[] d = sides[r.nextInt(4)];
            if (ground(x + d[0], z + d[1]) >= WATER || ground(x + 2 * d[0], z + 2 * d[1]) >= WATER) continue;
            pieces.add(new Glowfall(x + d[0], z + d[1], top, this));
            falls++;
        }
    }

    /** What lies on a cap: sprouts in clumps on the moss, threads over the bare flesh, spores. */
    @Override
    protected void scatter(V v, Kit m, Spot s, int y) {
        Own o = own();
        if (s.wet) return;
        BlockState on = v.get(s.x, y - 1, s.z);
        if (s.way == TRAIL || s.way == PAD || s.way == SPUR) {
            if (on == o.loam && s.roll > 0.965) v.set(s.x, y, s.z, o.spores);
            return;
        }
        double clump = N.rnd(Math.floorDiv(s.x, 5), Math.floorDiv(s.z, 5), seed + 21);
        if (on == o.moss) {
            if (s.roll < (clump < 0.4 ? 0.3 : 0.05)) v.set(s.x, y, s.z, o.sprouts);
            else if (s.roll > 0.95) v.set(s.x, y, s.z, o.spores);
        } else if (on == o.flesh) {
            if (s.roll < 0.16) v.set(s.x, y, s.z, o.threads);
            else if (s.roll > 0.96) v.set(s.x, y, s.z, o.spores);
        }
    }

    /** A cap on its stem, gills under it: drawn at any size from a lamp's to a tree's. */
    private static void glowcap(V v, Own o, int x, int base, int z, int h, double rad, long salt) {
        for (int y = base; y < base + h; y++) v.set(x, y, z, o.stem);
        int a = (int) Math.ceil(rad), top = base + h;
        for (int dx = -a; dx <= a; dx++) for (int dz = -a; dz <= a; dz++) {
            double far = Math.hypot(dx, dz);
            if (far > rad) continue;
            // gills under the rim, flesh over them, a second course of flesh over the middle so the top is domed
            if (far > 0.5) v.set(x + dx, top - 1, z + dz, o.gills);
            v.set(x + dx, top, z + dz, o.flesh);
            if (far < rad - 1.3) v.set(x + dx, top + 1, z + dz, o.flesh);
            if (rad > 3.6 && far < rad - 2.9) v.set(x + dx, top + 2, z + dz, o.flesh);
        }
    }

    /** A glowcap the size of a tree. */
    static final class Glowcap extends Piece<Kit> {
        final int x, gy, z, h;
        final double rad;
        final long salt;

        Glowcap(int x, int z, int h, double rad, long salt, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.rad = rad;
            this.salt = salt;
            gy = land.ground(x, z);
            int reach = (int) Math.ceil(rad) + 1;
            box(x, z, reach, reach);
            mayOverlap = true;
            label = "a glowcap";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            v.foot(x, gy, z, o.stem);
            glowcap(v, o, x, gy + 1, z, h, rad, salt);
            // a thick one stands on a foot
            if (rad > 3.6) for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (V.holds(v.get(x + d[0], gy, z + d[1]))) v.set(x + d[0], gy + 1, z + d[1], o.stem);
        }
    }

    /** A thread of light off a cap's rim: it stands in the sea on a stone and reaches the rim's top. */
    static final class Glowfall extends Piece<Kit> {
        final int x, z, top;

        Glowfall(int x, int z, int top, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.top = top;
            box(x, z, 0, 0);
            mayOverlap = true;
            label = "a thread of light";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            v.foot(x, WATER, z, o.rock);
            for (int y = WATER + 1; y <= top; y++) if (v.get(x, y, z) == null) v.set(x, y, z, o.fall);
        }
    }

    /**
     * What stands on the last cap. In front, the Lord's floor: moss and loam, rings of the hollow's rock, a veined stone
     * that glows at its heart, inside a fence of stems capped with flesh, and twelve glowcaps standing in the fence, their
     * gills lighting the floor; two taller ones at the gate. Behind it, growing out of the cap's back, one glowcap far
     * taller than the rest: a stem five across at its waist, a cap forty-four across fifty blocks up, gills under all of it.
     */
    static final class Grove extends Piece<Kit> {
        static final int BACK = -34, TALL = 46;
        static final double CAP = 22;
        final int cx, cz, feet;
        final double radius;
        final long salt;
        final GeneratedLand land;

        Grove(int cx, int cz, int feet, double radius, long salt, GeneratedLand land) {
            this.cx = cx;
            this.cz = cz;
            this.feet = feet;
            this.radius = radius;
            this.salt = salt;
            this.land = land;
            box(cx, cz - 14, 31, 44);
            mayOverlap = true;
            startsWay = true;
            label = "the last cap's grove";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, dz = z - cz;
                court(v, o, x, z, dx, dz);
                great(v, o, x, z, dx, dz - BACK);
            }
            // the twelve in the fence, and the two at the gate
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                if (k == 3) continue;
                glowcap(v, o, cx + (int) Math.round(Math.cos(a) * (radius + 1.2)), feet + 4, cz + (int) Math.round(Math.sin(a) * (radius + 1.2)), 5 + (int) (N.rnd(k, 3, salt) * 3), 3.3, salt + k);
            }
            for (int side = -1; side <= 1; side += 2) glowcap(v, o, cx + 4 * side, feet + 4, cz + (int) Math.round(radius + 1), 9, 3.6, salt + 40 + side);
        }

        private void court(V v, Own o, int x, int z, int dx, int dz) {
            double far = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
            if (far < radius - 0.5) {
                if (v.get(x, feet - 1, z) == null || v.get(x, feet, z) != null) return;
                BlockState b;
                if (far < 2.5) b = o.veined;
                else if (Math.abs(far - radius * 0.45) < 0.6 || Math.abs(far - radius * 0.8) < 0.6) b = N.rnd(x, z, salt + 6) < 0.3 ? o.veined : o.rock;
                else if (far > 3 && Math.abs(Math.sin(a * 3)) * far < 0.55) b = o.loam;
                else {
                    Hex t = N.hex(x, z, 2.6);
                    b = t.edge() > 0.8 ? o.loam : N.rnd(t.q(), t.r(), salt + 7) < 0.7 ? o.moss : o.loam;
                }
                v.set(x, feet - 1, z, b);
                return;
            }
            if (far < radius + 0.4 || far > radius + 1.6) return;
            // the way in is on the south side, five wide
            if (dz > 0 && Math.abs(dx) <= 2) return;
            v.foot(x, Math.min(feet - 1, land.ground(x, z)), z, o.stem);
            for (int k = 0; k <= 3; k++) v.set(x, feet + k, z, o.stem);
            v.set(x, feet + 4, z, o.flesh);
        }

        /** The great glowcap: its stem flares at the foot and under the cap; the cap is domed, six thick at its middle, gills under all of it. */
        private void great(V v, Own o, int x, int z, int dx, int sz) {
            double far = Math.hypot(dx, sz);
            if (far > CAP + 0.5) return;
            for (int k = 0; k < TALL; k++) {
                double rad = 3.0 + 3.2 * N.smooth(9, 0, k) + 4 * N.smooth(TALL - 9, TALL, k);
                if (far > rad) continue;
                if (k == 0) v.foot(x, feet - 1, z, o.stem);
                v.set(x, feet + k, z, far > rad - 1.6 ? o.stem : o.flesh);
            }
            // the cap: its underside rises toward the rim, its top falls toward it
            double in = far / CAP;
            int under = TALL + (int) Math.round(3 * in * in), over = TALL + 2 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - in * in)));
            if (far > CAP) return;
            for (int k = under; k <= over; k++) v.set(x, feet + k, z, k == under && far > 6.5 ? (N.rnd(Math.floorDiv(x, 2) * 31L + Math.floorDiv(z, 2), 7, salt) < 0.12 ? o.flesh : o.gills) : o.flesh);
        }
    }
}
