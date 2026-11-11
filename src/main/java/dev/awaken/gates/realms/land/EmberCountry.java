package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.land.GeneratedLand;
import dev.awaken.gates.realms.kit.land.LandPieces;
import dev.awaken.gates.realms.kit.land.Landform;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Ember Mines' open country (SPEC §50.9, "Rolled lands"): the skin of a rolled land. Ash lying in drifts over slag,
 * cliffs of black basalt standing in columns with seams still glowing in them, cooling magma running down to a grey sea
 * and pouring over every ledge in its way, burnt trees where a forest was, and what the miners left: their lamps along
 * the way up, their bridges, a camp where a den now is. Its first land is The Cinder Crown, on the Ascent.
 */
public final class EmberCountry extends GeneratedLand {
    private static final List<List<String>> HERDS = List.of(List.of("cinder_miner*3", "ember_imp*2"), List.of("slag_cube*3", "scorch_crawler*3"),
            List.of("ember_imp*3", "slag_boar"), List.of("scorch_crawler*4", "cinder_miner*2"), List.of("slag_boar*2", "ember_imp*2"));
    private static final List<List<String>> AMBUSHES = List.of(List.of("scorch_crawler*5", "ember_imp*2"), List.of("slag_cube*4", "ember_imp*2"));

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    /** The theme's own blocks and the few of the game's it borrows (smooth basalt for a cliff's dark courses, iron for what was forged). */
    private static final class Own {
        final BlockState cinders = b("ember_cinders"), crystal = b("ember_crystal"), post = b("mine_timber"), barrel = Blocks.BARREL.defaultBlockState();
        final Dress dress = new Dress(b("ember_ash"), b("ember_ash_drift"), b("ember_slag"), b("ember_slag_slab"), b("ember_slag_stairs"), b("ember_basalt"), b("ember_basalt_slab"),
                b("ember_basalt_stairs"), Blocks.SMOOTH_BASALT.defaultBlockState(), b("ember_basalt_cracked"), b("gate_ember_ore"), b("magma_crust"), b("ember_moltenfall"),
                Blocks.WATER.defaultBlockState(), post, b("mine_planks"), b("mine_lamp"),
                Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true).setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true),
                Blocks.IRON_CHAIN.defaultBlockState());
    }

    private static volatile Own own;

    private static Own own() {
        Own o = own;
        if (o == null) synchronized (EmberCountry.class) {
            if ((o = own) == null) own = o = new Own();
        }
        return o;
    }

    public EmberCountry(Ctx ctx) {
        super(ctx, 0x454D4252L);
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
        return List.of("cinder_miner*2", "slag_cube");
    }

    @Override
    protected int airTint() {
        return 0xff8a40;
    }

    @Override
    public String heartClosedLine() {
        return "The way into the crater is barred, and the bars are still warm. Whatever holds them shut is further down the mountain.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        return airOf(new AirPlan().mist(WATER + 3, 6, 0x8a7468, 0.35F).mist(WATER + 46, 8, 0x9a6a50, 0.22F));
    }

    /**
     * Ash lies on every ledge. The top of a column or a cliff is ash wherever the ashfall reached (most places) and bare
     * black rock where it didn't, so a crag reads by its ledges; anything close to what burns is bare and cracked.
     */
    @Override
    protected BlockState cap(Spot s) {
        Dress d = dress;
        if (s.way == 0 && !s.crust && !s.wet && s.heat < 0.4 && (s.columns || s.drop >= 3) && s.patch > -0.25) return d.soil();
        return super.cap(s);
    }

    /** A den's place: a ring of standing stones, or the camp the miners left, turn about. */
    @Override
    protected void denPlace(int x, int feet, int z, int radius, int kind, double wayX, double wayZ) {
        if (kind % 2 == 0) pieces.add(new LandPieces.StoneRing(x, z, radius - 0.8, 7, wayX, wayZ, seed + 500 + kind, this));
        else {
            double away = Math.atan2(z - wayZ, x - wayX) + 0.9;
            int cx = x + (int) Math.round(Math.cos(away) * (radius - 2.5)), cz = z + (int) Math.round(Math.sin(away) * (radius - 2.5));
            if (wayDist(cx, cz) > 5.5) pieces.add(new Camp(cx, cz, seed + 520 + kind, this));
            pieces.add(new LandPieces.StoneRing(x, z, radius - 0.8, 4, wayX, wayZ, seed + 540 + kind, this));
        }
    }

    /** The miners' lamps, still burning, every few steps up the way; and the forest that burned, standing in stands with bare ash between. */
    @Override
    protected void furnish() {
        List<double[]> way = wayPoints();
        int side = 1;
        for (int i = 14; i + 3 < way.size(); i += 13 + r.nextInt(5)) {
            double[] a = way.get(i - 2), c = way.get(i + 2), at = way.get(i);
            double dx = c[0] - a[0], dz = c[1] - a[1], l = Math.hypot(dx, dz);
            if (l < 2 || l > 6) continue; // a hairpin, or the gap a bridge crosses
            int x = (int) Math.round(at[0] - dz / l * 4.4 * side), z = (int) Math.round(at[1] + dx / l * 4.4 * side);
            side = -side;
            int gy = ground(x, z);
            if (gy <= WATER || Math.abs(gy + 1 - at[2]) > 3 || wayDist(x, z) < 3.8) continue;
            maybe(new LandPieces.LampPost(x, z, 3 + r.nextInt(2), this));
        }
        int half = halfSize() - 70, made = 0;
        for (int i = 0; i < 2600 && made < 170; i++) {
            int x = r.nextInt(2 * half) - half, z = r.nextInt(2 * half) - half;
            // stands of it, never an even scatter
            if (N.fbm(grove(), x, z, 1 / 46.0, 2) < 0.12) continue;
            int gy = ground(x, z);
            if (gy > WATER + 60 || !open(x, z, 2, 1) || !offWays(x, z, 6)) continue;
            if (maybe(new Snag(x, z, 5 + r.nextInt(8), seed + 600 + i, this))) made++;
        }
    }

    private net.minecraft.world.level.levelgen.synth.SimplexNoise groveNoise;

    private net.minecraft.world.level.levelgen.synth.SimplexNoise grove() {
        if (groveNoise == null) groveNoise = N.noise(seed + 7100);
        return groveNoise;
    }

    /** What lies on the open ground: crystals crowding whatever still burns, cinders and stumps on the ash, loose stones on the slag. */
    @Override
    protected void scatter(V v, Kit m, Spot s, int y) {
        Own o = own();
        BlockState on = v.get(s.x, y - 1, s.z);
        if (s.way == TRAIL || s.way == SPUR) {
            if (s.mid > 1.6F && s.roll < 0.05) v.set(s.x, y, s.z, o.cinders);
            return;
        }
        double clump = N.rnd(Math.floorDiv(s.x, 5), Math.floorDiv(s.z, 5), seed + 21);
        if (s.heat > 0.2) {
            if (s.roll < 0.08 + 0.3 * s.heat) v.set(s.x, y, s.z, o.crystal);
            else if (s.roll > 0.9) v.set(s.x, y, s.z, o.cinders);
        } else if (on == o.dress.rock() || on == o.dress.seam()) {
            if (s.roll < 0.02) v.set(s.x, y, s.z, o.crystal);
            else if (s.roll < 0.07) v.set(s.x, y, s.z, o.cinders);
        } else if (on == o.dress.soil()) {
            if (clump > 0.86 && s.roll < 0.16) v.set(s.x, y, s.z, o.crystal);
            else if (clump < 0.3 && s.roll < 0.035 && s.way == 0) v.set(s.x, y, s.z, o.post); // a stump
            else if (s.roll > 0.9) v.set(s.x, y, s.z, o.cinders);
        } else if (on == o.dress.stone()) {
            if (s.roll < 0.05 && s.way == 0) v.set(s.x, y, s.z, o.dress.stoneSlab()); // a loose stone
            else if (s.roll > 0.86) v.set(s.x, y, s.z, o.cinders);
        }
    }

    /** A tree that burned where it stood: a black trunk, a few stubs of bough, roots still holding the ash. */
    static final class Snag extends Piece<Kit> {
        final int x, gy, z, h;
        final long salt;

        Snag(int x, int z, int h, long salt, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.salt = salt;
            gy = land.ground(x, z);
            box(x, z, 2, 2);
            label = "a burnt tree";
        }

        @Override
        public void draw(V v, Kit m) {
            BlockState trunk = own().post;
            v.foot(x, gy, z, trunk);
            for (int y = gy + 1; y <= gy + h; y++) v.set(x, y, z, trunk);
            Direction[] sides = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
            for (int k = 0; k < 4; k++) {
                Direction d = sides[k];
                BlockState lying = trunk.setValue(RotatedPillarBlock.AXIS, d.getAxis());
                // a root, where there is ground for it to lie on
                if (N.rnd(salt, k, 1) < 0.45 && V.holds(v.get(x + d.getStepX(), gy, z + d.getStepZ()))) v.set(x + d.getStepX(), gy + 1, z + d.getStepZ(), lying);
                if (N.rnd(salt, k, 2) > 0.5 || h < 6) continue;
                int by = gy + 3 + (int) (N.rnd(salt, k, 3) * (h - 4)), len = 1 + (int) (N.rnd(salt, k, 4) * 2);
                for (int i = 1; i <= len; i++) v.set(x + d.getStepX() * i, by, z + d.getStepZ() * i, lying);
                if (N.rnd(salt, k, 5) < 0.5) v.set(x + d.getStepX() * len, by + 1, z + d.getStepZ() * len, trunk);
            }
        }
    }

    /** What is left of a camp: a frame with half its posts down, boards on the ground, a barrel, a lamp still lit on the one post that stands. */
    static final class Camp extends Piece<Kit> {
        final int x, gy, z;
        final long salt;
        final GeneratedLand land;

        Camp(int x, int z, long salt, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.salt = salt;
            this.land = land;
            gy = land.ground(x, z);
            box(x, z, 3, 3);
            mayOverlap = true;
            label = "a miners' camp";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            Dress d = o.dress;
            int[][] corners = {{-2, -2}, {2, -2}, {2, 2}, {-2, 2}};
            int[] tall = new int[4];
            for (int k = 0; k < 4; k++) {
                tall[k] = k == 0 ? 4 : N.rnd(salt, k, 1) < 0.5 ? 4 : 1 + (int) (N.rnd(salt, k, 2) * 2);
                int px = x + corners[k][0], pz = z + corners[k][1];
                v.foot(px, gy, pz, d.stone());
                for (int y = gy + 1; y <= gy + tall[k]; y++) v.set(px, y, pz, d.post());
            }
            v.set(x - 2, gy + 5, z - 2, d.lamp());
            // a beam between any two posts that both still stand
            for (int k = 0; k < 4; k++) {
                int n = (k + 1) % 4;
                if (tall[k] < 4 || tall[n] < 4) continue;
                boolean alongX = corners[k][1] == corners[n][1];
                BlockState beam = d.post().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
                for (int i = -1; i <= 1; i++) v.set(x + (alongX ? i : corners[k][0]), gy + 4, z + (alongX ? corners[k][1] : i), beam);
            }
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 0; c++) if (V.holds(v.get(x + a, gy, z + c)) && N.rnd(x + a, z + c, salt) < 0.8) v.set(x + a, gy, z + c, d.planks());
            if (V.holds(v.get(x + 1, gy, z + 1)) && v.get(x + 1, gy + 1, z + 1) == null) v.set(x + 1, gy + 1, z + 1, o.barrel);
            if (V.holds(v.get(x, gy, z + 1)) && v.get(x, gy + 1, z + 1) == null && N.rnd(salt, 9, 3) < 0.6) v.set(x, gy + 1, z + 1, o.barrel);
        }
    }
}
