package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Build;
import dev.awaken.gates.realms.kit.N;
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
 * The Overgrown Temple's open country (SPEC §50.9, "Rolled lands"): the skin of a rolled land, in the Fane family and the
 * temple's own plants. Moss underfoot, lying in layers so the ground rolls; rooted earth under the trees; cliffs of pale
 * cut-looking limestone with moss on every ledge; an old flagged road, half taken back; great trees in stands with vines
 * hanging under their crowns and clearings between; ruined pillars round the dens; braziers still lit along the way.
 * Its first land is Fallwater Vale, on the Valley.
 */
public final class TempleCountry extends GeneratedLand {
    private static final List<List<String>> HERDS = List.of(List.of("thorn_hound*3", "thorn_archer*2"), List.of("vine_creaker*2", "thorn_archer*2"),
            List.of("thorn_archer*3", "incense_priest"), List.of("thorn_hound*4", "vine_creaker"), List.of("vine_creaker*3", "incense_priest"));
    private static final List<List<String>> AMBUSHES = List.of(List.of("thorn_hound*5", "vine_creaker"), List.of("thorn_archer*3", "thorn_hound*3"));

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    private static final class Own {
        final BlockState bed = b("fane_bed"), worn = b("fane_stone_worn"), pale = b("fane_stone_pale"), brick = b("fane_brick"), tile = b("fane_tile"), bark = b("fane_bark"),
                barkMoss = b("fane_bark_moss"), leaf = b("fane_leaf"), leafLit = b("fane_leaf_lit"), leafDeep = b("fane_leaf_deep"), fern = b("temple_fern"), carpet = b("temple_moss"),
                leaves = b("temple_leaves"), vines = b("temple_vines"), idol = b("temple_idol");
        final Dress dress = new Dress(b("fane_moss"), b("temple_moss_drift"), b("fane_flagstone"), b("fane_flagstone_slab"), b("fane_flagstone_stairs"), b("fane_stone"), b("fane_stone_slab"),
                b("fane_stone_stairs"), b("fane_stone_deep"), b("temple_carving"), b("fane_gilt"), null, b("fane_fall"), Blocks.WATER.defaultBlockState(), b("fane_timber"), b("fane_planks"),
                Build.lit(b("temple_brazier")), b("temple_root"), Blocks.IRON_CHAIN.defaultBlockState());
    }

    private static volatile Own own;

    private static Own own() {
        Own o = own;
        if (o == null) synchronized (TempleCountry.class) {
            if ((o = own) == null) own = o = new Own();
        }
        return o;
    }

    private SimplexNoise groveNoise;

    public TempleCountry(Ctx ctx) {
        super(ctx, 0x54454D50L);
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
        return List.of("thorn_archer*2", "vine_creaker");
    }

    @Override
    protected int airTint() {
        return 0xd8ffb0;
    }

    @Override
    public String heartClosedLine() {
        return "Roots have grown across the head of the stair, as thick as your arm. Something down in the valley is holding them there.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        return airOf(new AirPlan().mist(WATER + 2, 5, 0xb8d8a8, 0.5F).mist(WATER + 18, 6, 0xd0e8c0, 0.25F));
    }

    /**
     * Green country is one green: moss everywhere the forest stands, with bare rooted earth only in patches under it and
     * the road's flags showing through. What was built (a temple's tiers) is flagged, with moss taking it in drifts.
     */
    @Override
    protected BlockState cap(Spot s) {
        Own o = own();
        Dress d = dress;
        if (s.wet) return o.bed;
        if (s.built) return s.drift > 0.3 ? d.soil() : s.roll < 0.16 ? o.tile : d.stone();
        switch (s.way) {
            case STAIR:
                return d.rock();
            case TRAIL:
                return s.mid < 1.4F ? (s.roll < 0.85 ? d.stone() : d.soil()) : s.roll < 0.35 ? d.stone() : d.soil();
            case PAD:
                return s.patch > 0.1 ? d.soil() : d.stone();
            case FLOOR:
                return d.stone();
            case SPUR:
                return d.soil();
            default:
        }
        if (s.columns || s.drop >= 3 || s.rise >= 4 && s.drop >= 1) return s.drift > -0.35 ? d.soil() : d.rock();
        return s.patch < -0.5 && s.drop <= 1 ? o.bed : d.soil();
    }

    @Override
    protected BlockState under(Spot s, BlockState cap) {
        Own o = own();
        return cap == dress.soil() || cap == o.bed ? o.bed : dress.rock();
    }

    /** Cliffs are the pale stone in courses, worn green in patches; what was built is the same stone dressed, with a dark plinth and a band of brick to every tier. */
    @Override
    protected BlockState face(Spot s, int y) {
        Own o = own();
        Dress d = dress;
        double k = N.rnd(Math.floorDiv(s.x, 3) * 31L + Math.floorDiv(y, 2), Math.floorDiv(s.z, 3), seed + 31);
        if (s.built) {
            int course = Math.floorMod(y, 6);
            return course == 0 ? d.dark() : course == 3 ? o.brick : k < 0.2 ? o.worn : k < 0.5 ? o.pale : d.rock();
        }
        return Math.floorMod(y, 9) == 4 ? d.dark() : k < 0.3 ? o.worn : k < 0.42 ? o.pale : d.rock();
    }

    /** A den's place: ruined pillars round it, or pillars and an idol on its plinth with a brazier either side. */
    @Override
    protected void denPlace(int x, int feet, int z, int radius, int kind, double wayX, double wayZ) {
        pieces.add(new LandPieces.StoneRing(x, z, radius - 0.8, kind % 2 == 0 ? 7 : 5, wayX, wayZ, seed + 500 + kind, this));
        if (kind % 2 == 1) {
            double away = Math.atan2(z - wayZ, x - wayX);
            int sx = x + (int) Math.round(Math.cos(away) * (radius - 3)), sz = z + (int) Math.round(Math.sin(away) * (radius - 3));
            if (wayDist(sx, sz) > 4.5) pieces.add(new Shrine(sx, sz, this));
        }
    }

    /** Braziers along the road, still lit; and the forest: stands of great trees with clearings between, lower near the way in so the valley can be seen over them. */
    @Override
    protected void furnish() {
        List<double[]> way = wayPoints();
        int side = 1;
        for (int i = 10; i + 3 < way.size(); i += 15 + r.nextInt(5)) {
            double[] a = way.get(i - 2), c = way.get(i + 2), at = way.get(i);
            double dx = c[0] - a[0], dz = c[1] - a[1], l = Math.hypot(dx, dz);
            if (l < 2 || l > 6) continue;
            int x = (int) Math.round(at[0] - dz / l * 4.4 * side), z = (int) Math.round(at[1] + dx / l * 4.4 * side);
            side = -side;
            int gy = ground(x, z);
            if (gy <= WATER || Math.abs(gy + 1 - at[2]) > 3 || wayDist(x, z) < 3.8) continue;
            maybe(new LandPieces.LampPost(x, z, 2 + r.nextInt(2), this));
        }
        groveNoise = N.noise(seed + 7200);
        Site mouth = mouth();
        int half = halfSize() - 40;
        List<int[]> trunks = new java.util.ArrayList<>();
        for (int i = 0; i < 6000 && trunks.size() < 280; i++) {
            int x = r.nextInt(2 * half) - half, z = r.nextInt(2 * half) - half;
            // stands of them with clearings between: open ground a herd can stand on, never an even scatter
            if (N.fbm(groveNoise, x, z, 1 / 40.0, 2) < -0.05) continue;
            if (!open(x, z, 2, 2) || !offWays(x, z, 7) || built(x, z)) continue;
            boolean crowded = false;
            for (int[] t : trunks) if (Math.abs(t[0] - x) < 6 && Math.abs(t[1] - z) < 6) crowded = true;
            if (crowded) continue;
            boolean near = Math.hypot(x - mouth.x(), z - mouth.z()) < 150;
            pieces.add(new Tree(x, z, (near ? 8 : 11) + r.nextInt(near ? 5 : 9), seed + 700 + i, this));
            trunks.add(new int[]{x, z});
        }
    }

    /** What grows under the trees: ferns in clumps, moss creeping over every stone, fallen leaves. */
    @Override
    protected void scatter(V v, Kit m, Spot s, int y) {
        Own o = own();
        BlockState on = v.get(s.x, y - 1, s.z);
        if (s.way == TRAIL) {
            if (s.mid > 1.6F && s.roll < 0.12) v.set(s.x, y, s.z, o.fern);
            return;
        }
        double clump = N.rnd(Math.floorDiv(s.x, 5), Math.floorDiv(s.z, 5), seed + 21);
        if (on == dress.soil()) {
            if (s.roll < (clump < 0.45 ? 0.4 : 0.1)) v.set(s.x, y, s.z, o.fern);
            else if (s.roll > 0.93) v.set(s.x, y, s.z, o.leaves);
        } else if (on == o.bed) {
            if (s.roll < 0.2) v.set(s.x, y, s.z, o.leaves);
        } else if (V.holds(on)) {
            // moss creeps over stone wherever nobody fights on it (it is a body an eighth of a block high)
            if (s.roll < 0.24 && s.way == 0) v.set(s.x, y, s.z, o.carpet);
            else if (s.roll > 0.94) v.set(s.x, y, s.z, o.fern);
        }
    }

    /** A great tree: a trunk on buttress roots, a crown in two or three masses of leaf, lit on top and dark underneath, vines hanging from its rim. */
    static final class Tree extends Piece<Kit> {
        final int x, gy, z, h;
        final long salt;

        Tree(int x, int z, int h, long salt, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.salt = salt;
            gy = land.ground(x, z);
            box(x, z, 8, 8);
            mayOverlap = true;
            label = "a great tree";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            boolean thick = h >= 14;
            int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            v.foot(x, gy, z, o.bark);
            for (int y = gy + 1; y <= gy + h; y++) v.set(x, y, z, y - gy < 4 && N.rnd(salt, y, 1) < 0.5 ? o.barkMoss : o.bark);
            for (int k = 0; k < 4; k++) {
                int ax = x + around[k][0], az = z + around[k][1];
                // a buttress root on each side: tall on a great tree, a single block on a small one, and only where there is ground under it
                int root = thick ? 2 + (int) (N.rnd(salt, k, 2) * 3) : N.rnd(salt, k, 2) < 0.5 ? 1 : 0;
                if (root == 0 || !V.holds(v.get(ax, gy, az))) continue;
                for (int y = gy + 1; y <= gy + root; y++) v.set(ax, y, az, y - gy < 3 ? o.barkMoss : o.bark);
            }
            crown(v, o, x, gy + h, z, 4.6 + h * 0.12, 3.0);
            int n = thick ? 2 : 1;
            for (int k = 0; k < n; k++) {
                double a = N.rnd(salt, k, 3) * 6.283, far = 3 + N.rnd(salt, k, 4) * 2;
                int bx = x + (int) Math.round(Math.cos(a) * far), bz = z + (int) Math.round(Math.sin(a) * far), by = gy + h - 3 - (int) (N.rnd(salt, k, 5) * 3);
                // a bough out to each lesser mass, so none of it hangs on leaves alone
                int steps = (int) Math.ceil(far);
                for (int i = 1; i <= steps; i++) v.set(x + (int) Math.round(Math.cos(a) * i), by, z + (int) Math.round(Math.sin(a) * i), o.bark);
                crown(v, o, bx, by + 1, bz, 3.6, 2.4);
            }
        }

        private void crown(V v, Own o, int cx, int cy, int cz, double rx, double ry) {
            int a = (int) Math.ceil(rx), b = (int) Math.ceil(ry);
            for (int dx = -a; dx <= a; dx++) for (int dz = -a; dz <= a; dz++) {
                int low = Integer.MAX_VALUE;
                for (int dy = b; dy >= -b; dy--) {
                    double far = (dx * dx + dz * dz) / (rx * rx) + (dy * dy) / (ry * ry);
                    if (far > 1 || far > 0.72 && N.rnd(cx + dx * 13L + dy, cz + dz, salt) < 0.35) continue;
                    if (v.get(cx + dx, cy + dy, cz + dz) != null) continue;
                    v.set(cx + dx, cy + dy, cz + dz, dy >= b - 1 ? o.leafLit : dy <= -b + 1 ? o.leafDeep : o.leaf);
                    low = cy + dy;
                }
                // vines from the rim of the crown, a few blocks long
                if (low == Integer.MAX_VALUE || dx * dx + dz * dz < rx * rx * 0.45 || N.rnd(cx + dx, cz + dz, salt + 7) > 0.3) continue;
                int len = 1 + (int) (N.rnd(cx + dx, cz + dz, salt + 8) * 3);
                for (int y = low - 1; y >= low - len && v.get(cx + dx, y, cz + dz) == null; y--) v.set(cx + dx, y, cz + dz, o.vines);
            }
        }
    }

    /** An idol on its plinth with a brazier either side, the stone gone green. */
    static final class Shrine extends Piece<Kit> {
        final int x, gy, z;
        final GeneratedLand land;

        Shrine(int x, int z, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.land = land;
            gy = land.ground(x, z);
            box(x, z, 2, 2);
            mayOverlap = true;
            label = "a shrine";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            Dress d = land.dress();
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
                v.foot(x + a, gy, z + c, d.rock());
                v.set(x + a, gy, z + c, a == 0 && c == 0 ? d.dark() : o.worn);
                for (int y = gy + 1; y <= gy + 4; y++) if (v.get(x + a, y, z + c) != null) v.set(x + a, y, z + c, null);
            }
            v.set(x, gy + 1, z, d.dark());
            v.set(x, gy + 2, z, o.idol);
            v.set(x - 1, gy + 1, z, d.lamp());
            v.set(x + 1, gy + 1, z, d.lamp());
        }
    }
}
