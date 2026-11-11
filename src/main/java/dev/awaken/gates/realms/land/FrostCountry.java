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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

/**
 * The Frost Monastery's open country (SPEC §50.9, "Rolled lands"): the skin of a rolled land, in the monastery's own
 * blocks. Snow over everything, lying in layers so it rolls; the monks' old walls of cut grey stone holding every bench of
 * it up, a dark course every few rows, ice hanging down them in falls and snow clinging in patches; a flagged road, half
 * drifted over; water that is ice, so a lake is somewhere to walk, swept bare in lanes with the snow in swells between;
 * butter lamps on red posts along the way, prayer flags on their masts, a chorten at a den. And one thing still whole: the
 * monastery itself, white walls leaning in under a red storey and a dark roof, lamps in its windows.
 * Its first land is the Hush Tarn, on the Basin.
 */
public final class FrostCountry extends GeneratedLand {
    private static final List<List<String>> HERDS = List.of(List.of("rime_acolyte*3", "hail_stray*2"), List.of("ice_hound*3", "frost_wisp*2"),
            List.of("hail_stray*3", "chant_master"), List.of("frost_wisp*3", "ice_hound*2"), List.of("rime_acolyte*2", "ice_hound*2", "frost_wisp"));
    private static final List<List<String>> AMBUSHES = List.of(List.of("ice_hound*5", "frost_wisp"), List.of("hail_stray*3", "ice_hound*3"));

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    private static final class Own {
        final BlockState snow = b("frost_snow"), drift = b("frost_snow_drift"), stone = b("frost_stone"), carved = b("frost_stone_carved"), tiles = b("frost_tiles"),
                timber = b("frost_timber"), plaster = b("frost_plaster"), ice = b("frost_ice"), iceLit = b("frost_ice_glowing"), icicles = b("frost_icicles"), flags = b("frost_flags"),
                lamp = b("frost_butter_lamp"), crystal = b("frost_crystal"), chips = b("frost_chips");
        final BlockState beam = timber.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        // water is ice here, and so is what shuts the Lord's door
        final Dress dress = new Dress(snow, drift, stone, b("frost_stone_slab"), b("frost_stone_stairs"), stone, b("frost_stone_slab"), b("frost_stone_stairs"), tiles, carved, iceLit, null,
                null, ice, timber, tiles, lamp, ice, Blocks.IRON_CHAIN.defaultBlockState());

        BlockState layers(int n) {
            return drift.setValue(DepthBlocks.Drift.LAYERS, Math.max(1, Math.min(6, n)));
        }
    }

    private static volatile Own own;

    private static Own own() {
        Own o = own;
        if (o == null) synchronized (FrostCountry.class) {
            if ((o = own) == null) own = o = new Own();
        }
        return o;
    }

    public FrostCountry(Ctx ctx) {
        super(ctx, 0x46524F53L);
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
        return List.of("rime_acolyte*2", "chant_master");
    }

    @Override
    protected int airTint() {
        return 0xcfe6ff;
    }

    @Override
    public String heartClosedLine() {
        return "The gateway is closed with ice as thick as a wall. Whoever keeps the landing on the shore is keeping it frozen.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        // a breath of mist lying on the ice, no deeper than your knee: any deeper and the last walk is made blind
        return airOf(new AirPlan().mist(WATER + 1, 1, 0xe4f0ff, 0.3F));
    }

    @Override
    protected Piece<? super Kit> hall(int cx, int cz, int feet, double radius, long salt) {
        return new Dzong(cx, cz, feet, radius, salt, this);
    }

    /**
     * White country is one white: snow everywhere, with the grey of the old stone only where it is too steep to lie, along
     * the middle of the road, and on what was built. Under the ice the lake's bed is dark, so the ice looks deep.
     */
    @Override
    protected BlockState cap(Spot s) {
        Own o = own();
        if (s.crust) return o.ice;
        if (s.wet) return o.tiles;
        if (s.built) return s.drift > 0.2 ? o.snow : o.stone;
        switch (s.way) {
            case STAIR:
            case FLOOR:
                return o.stone;
            case TRAIL:
                // the road is flagged, and the snow lies over it in drifts: bare along its middle, lost at its edges
                return s.mid < 1.4F ? (s.patch > 0.28 ? o.snow : s.roll < 0.9 ? o.stone : o.tiles) : s.patch > -0.1 || s.roll < 0.3 ? o.snow : o.stone;
            case PAD:
                return s.patch > 0 ? o.snow : o.stone;
            case SPUR:
                return s.roll < 0.3 ? o.stone : o.snow;
            default:
        }
        if (s.columns || s.drop >= 3 || s.rise >= 4 && s.drop >= 1) return s.drift > -0.45 ? o.snow : o.stone;
        return o.snow;
    }

    @Override
    protected BlockState under(Spot s, BlockState cap) {
        Own o = own();
        return cap == o.snow ? o.snow : o.stone;
    }

    /**
     * A wall of the old stone: a dark course every eighth row, ice hanging down it in falls a couple of blocks wide, snow
     * clinging in patches. Where the ground is split (a crevasse) the walls are ice, lit from inside.
     */
    @Override
    protected BlockState face(Spot s, int y) {
        Own o = own();
        if (s.heat > 0.5) return N.rnd(s.x * 31L + y, s.z, seed + 33) < 0.3 ? o.iceLit : o.ice;
        if (N.rnd(Math.floorDiv(s.x, 2), Math.floorDiv(s.z, 2), seed + 31) < 0.1) return o.ice;
        if (Math.floorMod(y, 8) == 3) return o.tiles;
        return N.rnd(Math.floorDiv(s.x, 4) * 31L + Math.floorDiv(y, 3), Math.floorDiv(s.z, 4), seed + 32) < 0.22 ? o.snow : o.stone;
    }

    /** A den's place: standing stones round it, and at every other one a chorten with a mast of flags either side. */
    @Override
    protected void denPlace(int x, int feet, int z, int radius, int kind, double wayX, double wayZ) {
        pieces.add(new LandPieces.StoneRing(x, z, radius - 0.8, kind % 2 == 0 ? 7 : 5, wayX, wayZ, seed + 500 + kind, this));
        if (kind % 2 == 0) return;
        double away = Math.atan2(z - wayZ, x - wayX);
        int sx = x + (int) Math.round(Math.cos(away) * (radius - 3)), sz = z + (int) Math.round(Math.sin(away) * (radius - 3));
        if (wayDist(sx, sz) > 4.5) pieces.add(new Chorten(sx, sz, this));
    }

    /** Butter lamps along the road, and on across the ice; masts of prayer flags off it; a chorten here and there on the benches. */
    @Override
    protected void furnish() {
        List<double[]> way = wayPoints();
        int side = 1;
        for (int i = 10; i + 3 < way.size(); i += 15 + r.nextInt(5)) {
            int[] at = beside(way, i, 4.4 * side);
            side = -side;
            if (at == null) continue;
            int gy = ground(at[0], at[1]);
            if (gy < WATER || Math.abs(gy + 1 - way.get(i)[2]) > 3 || wayDist(at[0], at[1]) < 3.8) continue;
            maybe(new LandPieces.LampPost(at[0], at[1], 2 + r.nextInt(2), this));
        }
        for (int i = 24; i + 3 < way.size(); i += 30 + r.nextInt(12)) {
            int[] at = beside(way, i, (7 + r.nextInt(3)) * (r.nextBoolean() ? 1 : -1));
            if (at == null || !open(at[0], at[1], 2, 2) || wayDist(at[0], at[1]) < 5.5) continue;
            maybe(new Mast(at[0], at[1], 6 + r.nextInt(3), r.nextBoolean(), this));
        }
        int half = halfSize() - 40, stood = 0;
        for (int i = 0; i < 500 && stood < 6; i++) {
            int x = r.nextInt(2 * half) - half, z = r.nextInt(2 * half) - half;
            if (!open(x, z, 3, 1) || !offWays(x, z, 10) || built(x, z)) continue;
            if (maybe(new Chorten(x, z, this))) stood++;
        }
    }

    /** A point so far to one side of the way at one of its points, or null where the way turns too sharply to say which side that is. */
    private static int[] beside(List<double[]> way, int i, double by) {
        double[] a = way.get(i - 2), c = way.get(i + 2), at = way.get(i);
        double dx = c[0] - a[0], dz = c[1] - a[1], l = Math.hypot(dx, dz);
        if (l < 2 || l > 6) return null;
        return new int[]{(int) Math.round(at[0] - dz / l * by), (int) Math.round(at[1] + dx / l * by)};
    }

    /** What lies on open ground: on the lake, snow in long swells and here and there a light under the ice; on land, ice crystals where the wind leaves them. */
    @Override
    protected void scatter(V v, Kit m, Spot s, int y) {
        Own o = own();
        if (s.wet) {
            double lie = s.drift * 0.9 + s.patch * 0.6;
            if (lie > 0.12) v.set(s.x, y, s.z, o.layers(1 + (int) ((lie - 0.12) * 7)));
            else if (N.rnd(Math.floorDiv(s.x, 9), Math.floorDiv(s.z, 9), seed + 51) < 0.08 && s.roll < 0.45) v.set(s.x, y - 1, s.z, o.iceLit);
            else if (s.roll > 0.985) v.set(s.x, y, s.z, o.chips);
            return;
        }
        BlockState on = v.get(s.x, y - 1, s.z);
        if (s.way == TRAIL || s.way == PAD || s.way == SPUR) {
            if (on == o.stone && s.roll > 0.96) v.set(s.x, y, s.z, o.chips);
            return;
        }
        if (on == o.snow) {
            double clump = N.rnd(Math.floorDiv(s.x, 6), Math.floorDiv(s.z, 6), seed + 21);
            if (clump < 0.12 && s.roll < 0.3 || s.rise >= 2 && s.roll < 0.12) v.set(s.x, y, s.z, o.crystal);
        } else if (V.holds(on) && s.roll > 0.95) v.set(s.x, y, s.z, o.chips);
    }

    /** A mast of prayer flags: a red pole, an arm at its head, the flags hanging in a string under the arm. */
    static final class Mast extends Piece<Kit> {
        final int x, gy, z, h;
        final boolean alongX;

        Mast(int x, int z, int h, boolean alongX, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.alongX = alongX;
            gy = land.ground(x, z);
            box(x, z, 3, 3);
            mayOverlap = true;
            label = "a mast of flags";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            v.foot(x, gy, z, o.stone);
            v.set(x, gy + 1, z, o.stone);
            for (int y = gy + 2; y <= gy + h; y++) v.set(x, y, z, o.timber);
            BlockState arm = alongX ? o.beam : o.timber.setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
            for (int k = -2; k <= 2; k++) {
                if (k == 0) continue;
                int ax = alongX ? x + k : x, az = alongX ? z : z + k;
                v.set(ax, gy + h, az, arm);
                v.set(ax, gy + h - 1, az, o.flags);
                if (Math.abs(k) == 1) v.set(ax, gy + h - 2, az, o.flags);
            }
        }
    }

    /** A chorten: a square foot of the old stone, a white drum on it, a carved block that still glows, and a lamp on top. */
    static final class Chorten extends Piece<Kit> {
        final int x, gy, z;

        Chorten(int x, int z, GeneratedLand land) {
            this.x = x;
            this.z = z;
            gy = land.ground(x, z);
            box(x, z, 2, 2);
            mayOverlap = true;
            label = "a chorten";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
                v.foot(x + a, gy, z + c, o.stone);
                for (int y = gy + 1; y <= gy + 6; y++) if (v.get(x + a, y, z + c) != null) v.set(x + a, y, z + c, null);
                v.set(x + a, gy + 1, z + c, o.stone);
                if (a == 0 || c == 0) v.set(x + a, gy + 2, z + c, o.plaster);
                else v.set(x + a, gy + 2, z + c, o.layers(2));
            }
            v.set(x, gy + 3, z, o.plaster);
            v.set(x, gy + 4, z, o.carved);
            v.set(x, gy + 5, z, o.lamp);
        }
    }

    /**
     * The monastery: the one thing in this country still whole. In front, the court the Lord keeps: a round floor inside a
     * low white wall with twelve piers, a lamp on each, and a gate on the south side under a red beam hung with flags.
     * Behind it the keep, in three tiers each set back from the one under it, every wall leaning in a block in ten: a foot
     * of the old stone, white above with windows two rows high, a red storey under the eaves with a lamp in most of its
     * windows, a dark eave a block proud all round with ice hanging from it and snow on the terrace over it. On the first
     * terrace a turret at each front corner; on the last tier a dark roof stepping in to its ridge, and a lamp on the ridge.
     */
    static final class Dzong extends Piece<Kit> {
        private static final int[] BASE = {0, 30, 56}, TALL = {30, 26, 24}, HALF_X = {36, 26, 15}, HALF_Z = {15, 11, 8}, MID_Z = {-38, -39, -40}, BAND = {4, 8, 6};
        private static final int ROOF = 80, TOP = 93, FRONT = -23;
        final int cx, cz, feet;
        final double radius;
        final long salt;
        final GeneratedLand land;

        Dzong(int cx, int cz, int feet, double radius, long salt, GeneratedLand land) {
            this.cx = cx;
            this.cz = cz;
            this.feet = feet;
            this.radius = radius;
            this.salt = salt;
            this.land = land;
            box(cx, cz - 15, 42, 44);
            mayOverlap = true;
            startsWay = true;
            label = "the monastery";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, dz = z - cz;
                if (dz <= FRONT) keep(v, o, x, z, dx, dz);
                else court(v, o, x, z, dx, dz);
            }
        }

        // ---- the keep

        /** One cell of the keep, {@code k} rows over the court's floor, or null where there is none of it. */
        private BlockState cell(Own o, int dx, int dz, int k) {
            if (k < 0 || k > TOP) return null;
            if (k >= ROOF) {
                int j = k - ROOF, ex = Math.abs(dx), ez = Math.abs(dz - MID_Z[2]);
                if (j <= 9) return ex <= 16 - j && ez <= 9 - j ? o.tiles : null;
                if (ex != 0 || ez != 0) return null;
                return k < TOP ? o.carved : o.lamp;
            }
            int t = k < BASE[1] ? 0 : k < BASE[2] ? 1 : 2, rel = k - BASE[t], h = TALL[t], lean = rel / 10;
            int hx = HALF_X[t] - lean, hz = HALF_Z[t] - lean, ex = Math.abs(dx), sz = dz - MID_Z[t], ez = Math.abs(sz);
            if (k >= 30 && k <= 39) {
                // a turret on each front corner of the first terrace
                int e = Math.max(Math.abs(ex - 31), Math.abs(dz + 27)), up = k - 30;
                if (up <= 6 && e <= 2) return up >= 5 ? o.timber : e == 2 ? o.plaster : o.tiles;
                if (up == 7 && e <= 3 || up == 8 && e <= 1) return o.tiles;
                if (up == 9 && e == 0) return o.lamp;
            }
            if (rel == h - 1) return ex <= hx + 1 && ez <= hz + 1 ? o.tiles : null;
            if (ex > hx || ez > hz) return null;
            boolean onX = ex == hx, onZ = ez == hz;
            // inside is dark, so a window is a dark slot
            if (!onX && !onZ) return o.tiles;
            if (rel == h - 2) return o.tiles;
            boolean corner = onX && onZ;
            int along = onZ ? dx : sz, wall = onZ ? (sz > 0 ? 1 : 2) : dx > 0 ? 3 : 4, red = h - 2 - BAND[t];
            if (rel >= red) {
                // the red storey: a window every fourth block, and a lamp in most of them
                if (BAND[t] >= 6 && !corner && Math.floorMod(along, 4) == 0 && (rel == red + 2 || rel == red + 3))
                    return rel == red + 2 && N.rnd(along + t * 100L, wall, salt) < 0.7 ? o.lamp : null;
                return o.timber;
            }
            if (t == 0) {
                if (onZ && sz > 0) {
                    // the great door in the south face, shut: red leaves in a frame of carved stone
                    if (ex <= 3 && rel <= 8) return o.timber;
                    if (ex <= 4 && rel <= 9) return o.carved;
                }
                if (rel < 4) return o.stone;
            }
            if (!corner && Math.floorMod(along, 6) == 3 && rel + 3 < red) {
                int w = Math.floorMod(rel - 7, 9);
                if (w == 0 || w == 1) return w == 0 && N.rnd(along + t * 100L + rel * 7L, wall, salt + 1) < 0.35 ? o.lamp : null;
            }
            return o.plaster;
        }

        private void keep(V v, Own o, int x, int z, int dx, int dz) {
            BlockState below = null, cur = cell(o, dx, dz, 0);
            if (cur != null) v.foot(x, feet - 1, z, o.stone);
            for (int k = 0; k <= TOP + 1; k++) {
                BlockState next = cell(o, dx, dz, k + 1);
                int y = feet + k;
                if (cur != null) v.set(x, y, z, cur);
                else if (below != null && below != o.lamp) {
                    // snow lies on every terrace, roof and sill
                    if (k >= 20 && v.get(x, y, z) == null) v.set(x, y, z, o.layers(2 + (int) (N.rnd(x, z, salt + k) * 3)));
                } else if (below == null && next == o.tiles && (k == 28 || k == 54 || k == 78 || k == 79) && v.get(x, y, z) == null) {
                    // under the eaves: flags along the front, ice everywhere else
                    boolean front = dz - MID_Z[k < 30 ? 0 : k < 56 ? 1 : 2] > 0 && k != 79;
                    if (front && Math.floorMod(dx, 2) == 0) v.set(x, y, z, o.flags);
                    else if (N.rnd(x, z, salt + 3 + k) < 0.4) v.set(x, y, z, o.icicles);
                }
                below = cur;
                cur = next;
            }
        }

        // ---- the court

        private void court(V v, Own o, int x, int z, int dx, int dz) {
            double far = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
            if (far < radius - 0.5) {
                floor(v, o, x, z, far, a);
                return;
            }
            if (far > radius + 3.2) return;
            int ex = Math.abs(dx), gy = Math.min(feet - 1, land.ground(x, z));
            boolean line = far >= radius + 0.4 && far <= radius + 1.6;
            if (dz > 0 && ex <= 5) {
                // the gate: a pier either side of the way in, a red beam between them, flags under the beam
                if (ex >= 3) {
                    v.foot(x, gy, z, o.stone);
                    for (int k = 0; k <= 10; k++) v.set(x, feet + k, z, k <= 1 ? o.stone : k <= 8 ? o.plaster : k == 9 ? o.timber : o.tiles);
                    v.set(x, feet + 11, z, ex == 4 && line ? o.lamp : o.layers(2));
                } else if (line) {
                    v.set(x, feet + 7, z, o.flags);
                    v.set(x, feet + 8, z, o.beam);
                    v.set(x, feet + 9, z, o.beam);
                    v.set(x, feet + 10, z, o.tiles);
                    v.set(x, feet + 11, z, o.layers(2));
                }
                return;
            }
            // twelve piers, three thick and proud of the wall either side; the wall between them one thick
            double twelfth = Math.PI / 6, off = a - Math.floor(a / twelfth) * twelfth - twelfth / 2, alongWall = Math.abs(off) * radius, edge = twelfth * radius / 2;
            boolean pier = alongWall > edge - 1.6;
            if (!pier && !line) return;
            v.foot(x, gy, z, o.stone);
            if (pier) {
                for (int k = 0; k <= 7; k++) v.set(x, feet + k, z, k <= 1 ? o.stone : k <= 5 ? o.plaster : k == 6 ? o.timber : o.tiles);
                boolean heart = alongWall > edge - 0.55 && Math.abs(far - (radius + 1.3)) < 0.55;
                v.set(x, feet + 8, z, heart ? o.lamp : o.layers(1 + (int) (N.rnd(x, z, salt + 4) * 3)));
            } else {
                for (int k = 0; k <= 4; k++) v.set(x, feet + k, z, k == 0 ? o.stone : k <= 3 ? o.plaster : o.tiles);
                v.set(x, feet + 5, z, o.layers(1 + (int) (N.rnd(x, z, salt + 4) * 3)));
            }
        }

        /** The court's floor: flags of the old stone and of white, dark in rings and in six spokes, and at its heart a carved stone that glows. */
        private void floor(V v, Own o, int x, int z, double far, double a) {
            if (v.get(x, feet - 1, z) == null || v.get(x, feet, z) != null) return;
            BlockState b;
            if (far < 2.5) b = o.carved;
            else if (Math.abs(far - radius * 0.45) < 0.6 || Math.abs(far - radius * 0.8) < 0.6) b = o.tiles;
            else if (far > 3 && Math.abs(Math.sin(a * 3)) * far < 0.55) b = o.tiles;
            else {
                Hex t = N.hex(x, z, 2.6);
                b = t.edge() > 0.8 ? o.tiles : N.rnd(t.q(), t.r(), salt + 7) < 0.6 ? o.stone : o.plaster;
            }
            v.set(x, feet - 1, z, b);
        }
    }
}
