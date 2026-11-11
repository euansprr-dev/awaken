package dev.awaken.gates.realms.land;

import dev.awaken.Awaken;
import dev.awaken.gates.block.DepthBlocks;
import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.land.GeneratedLand;
import dev.awaken.gates.realms.kit.land.LandPieces;
import dev.awaken.gates.realms.kit.land.Landform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Glass Desert's open country (SPEC §50.9, "Rolled lands"): the skin of a rolled land, in the desert's own blocks.
 * Its water is glass: the sand melted once and set flat, amber over a dark bed, and the sand has been blowing back over
 * it in long swells ever since, with a light still under it here and there and great shards of it standing where it
 * splashed. Whatever stands out of it is pale sand in dunes, or sandstone in tables with a course of brick every few
 * rows. The road is paved, a line of blue tile down its middle, and pillars stand beside it out of the glass, some
 * broken, some still carrying a light. Its great thing is the tower that did the melting: the Burning Glass.
 * Its first land is the Glass Sea, on the Causeway.
 */
public final class GlassCountry extends GeneratedLand {
    private static final List<List<String>> HERDS = List.of(List.of("glass_husk*3", "sand_stalker*2"), List.of("heat_shimmer*3", "glass_husk*2"),
            List.of("sand_stalker*3", "prism_sentinel"), List.of("glass_husk*4", "heat_shimmer"), List.of("sand_stalker*2", "heat_shimmer*2", "glass_husk"));
    private static final List<List<String>> AMBUSHES = List.of(List.of("sand_stalker*5", "heat_shimmer"), List.of("glass_husk*3", "sand_stalker*3"));

    private static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }

    private static final class Own {
        final BlockState sand = b("desert_sand"), rippled = b("desert_sand_rippled"), drift = b("desert_sand_drift"), stone = b("desert_sandstone"), carved = b("desert_sandstone_carved"),
                brick = b("desert_brick"), tile = b("desert_tile"), column = b("desert_column"), glass = b("desert_glass"), glassLit = b("desert_glass_glowing"), crystal = b("desert_crystal"),
                grass = b("desert_grass"), shards = b("desert_shards");
        // water is glass here, and so is what shuts the Lord's door
        final Dress dress = new Dress(sand, drift, stone, b("desert_sandstone_slab"), b("desert_sandstone_stairs"), stone, b("desert_sandstone_slab"), b("desert_sandstone_stairs"), brick, carved,
                glassLit, null, b("desert_sandfall"), glass, column, brick, glassLit, glass, Blocks.IRON_CHAIN.defaultBlockState());

        BlockState layers(int n) {
            return drift.setValue(DepthBlocks.Drift.LAYERS, Math.max(1, Math.min(6, n)));
        }
    }

    private static volatile Own own;

    private static Own own() {
        Own o = own;
        if (o == null) synchronized (GlassCountry.class) {
            if ((o = own) == null) own = o = new Own();
        }
        return o;
    }

    /** Where the Burning Glass stands, once a landform has asked for it: its light falls on the Lord's floor. */
    private int[] glassAt;

    public GlassCountry(Ctx ctx) {
        super(ctx, 0x474C4153L);
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
        return List.of("glass_husk*2", "prism_sentinel");
    }

    @Override
    protected int airTint() {
        return 0xffe2a8;
    }

    @Override
    public String heartClosedLine() {
        return "The gate at the head of the stair is closed with glass, fused right across it. Whoever holds the bastion below is keeping it shut.";
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan();
        // the Burning Glass still burns: its beam comes down on the floor in front of it
        if (glassAt != null) air.shaft(glassAt[0], glassAt[1] + Tower.LENS, glassAt[2] + Tower.MID_Z, glassAt[0], glassAt[1], glassAt[2], 5F, 0xffd890, 0.5F);
        return airOf(air);
    }

    @Override
    protected Piece<? super Kit> hall(int cx, int cz, int feet, double radius, long salt) {
        glassAt = new int[]{cx, feet, cz};
        return new Tower(cx, cz, feet, radius, salt, this);
    }

    /**
     * Pale country: sand wherever it can lie, rippled where it lies flat, sandstone where the land stands steep and under
     * the road. Under the glass the bed is dark brick, so the glass looks deep.
     */
    @Override
    protected BlockState cap(Spot s) {
        Own o = own();
        if (s.crust) return o.glassLit;
        if (s.wet) return o.brick;
        if (s.built) return s.drift > 0.3 ? o.sand : o.stone;
        switch (s.way) {
            case STAIR:
            case FLOOR:
                return o.stone;
            case TRAIL:
                // paved, with a line of blue tile down the middle; the sand lies over it in places
                if (s.patch > 0.34) return o.sand;
                return s.mid < 0.8F ? (s.roll < 0.86 ? o.tile : o.stone) : s.roll < 0.9 ? o.stone : o.brick;
            case PAD:
                return s.patch > 0.1 ? o.sand : o.stone;
            case SPUR:
                return s.roll < 0.45 ? o.stone : o.sand;
            default:
        }
        if (s.columns || s.drop >= 3 || s.rise >= 4 && s.drop >= 1) return s.drift > 0.1 ? o.sand : o.stone;
        return o.sand;
    }

    @Override
    protected BlockState under(Spot s, BlockState cap) {
        Own o = own();
        return cap == o.sand ? o.sand : o.stone;
    }

    /** Sandstone in courses, a course of brick every seventh row, a carved block in a brick course now and then, and a streak of glass where the melting ran down. */
    @Override
    protected BlockState face(Spot s, int y) {
        Own o = own();
        if (N.rnd(Math.floorDiv(s.x, 2), Math.floorDiv(s.z, 2), seed + 31) < 0.06) return o.glass;
        if (Math.floorMod(y, 7) == 2) return N.rnd(s.x * 31L + y, s.z, seed + 32) < 0.06 ? o.carved : o.brick;
        return o.stone;
    }

    /** A den's place: standing stones round it, and at every other one a pair of pillars with a light on each. */
    @Override
    protected void denPlace(int x, int feet, int z, int radius, int kind, double wayX, double wayZ) {
        pieces.add(new LandPieces.StoneRing(x, z, radius - 0.8, kind % 2 == 0 ? 7 : 5, wayX, wayZ, seed + 500 + kind, this));
        if (kind % 2 == 0) return;
        double away = Math.atan2(z - wayZ, x - wayX), across = away + Math.PI / 2;
        for (int side = -1; side <= 1; side += 2) {
            int px = x + (int) Math.round(Math.cos(away) * (radius - 3) + Math.cos(across) * 3 * side), pz = z + (int) Math.round(Math.sin(away) * (radius - 3) + Math.sin(across) * 3 * side);
            if (wayDist(px, pz) > 4) pieces.add(new Pillar(px, pz, 6, true, this));
        }
    }

    /** Pillars beside the road, out of the glass or the sand, one in three still carrying a light, one in four broken; and shards of the glass standing in the sea. */
    @Override
    protected void furnish() {
        List<double[]> way = wayPoints();
        Site mouth = mouth();
        int n = 0;
        for (int i = 14; i + 3 < way.size(); i += 9 + r.nextInt(3)) {
            double[] a = way.get(i - 2), c = way.get(i + 2), at = way.get(i);
            double dx = c[0] - a[0], dz = c[1] - a[1], l = Math.hypot(dx, dz);
            if (l < 2 || l > 6) continue;
            for (int side = -1; side <= 1; side += 2) {
                int x = (int) Math.round(at[0] - dz / l * 6.2 * side), z = (int) Math.round(at[1] + dx / l * 6.2 * side);
                int gy = ground(x, z);
                n++;
                if (gy + 1 > at[2] + 1 || wayDist(x, z) < 5 || Math.hypot(x - mouth.x(), z - mouth.z()) < 15) continue;
                // every pillar's head is at one height over the road, however deep its foot stands
                int head = (int) at[2] + 5, h = head - gy;
                boolean broken = N.rnd(n, 3, seed + 61) < 0.25;
                maybe(new Pillar(x, z, broken ? Math.max(2, h - 2 - r.nextInt(4)) : h, !broken && n % 3 == 0, this));
            }
        }
        int half = halfSize() - 40;
        for (int tries = 0, stood = 0; tries < 900 && stood < 16; tries++) {
            int x = r.nextInt(2 * half) - half, z = r.nextInt(2 * half) - half;
            if (ground(x, z) != WATER || !offWays(x, z, 16)) continue;
            // shards stand in clumps: one great one and a few lesser round it
            int count = 2 + r.nextInt(4);
            for (int k = 0; k < count; k++) {
                int sx = x + (k == 0 ? 0 : r.nextInt(13) - 6), sz = z + (k == 0 ? 0 : r.nextInt(13) - 6);
                if (ground(sx, sz) != WATER || !offWays(sx, sz, 12)) continue;
                maybe(new Shard(sx, sz, k == 0 ? 12 + r.nextInt(12) : 4 + r.nextInt(7), seed + 900 + tries * 7L + k, this));
            }
            stood++;
        }
    }

    /** What lies on open ground: on the glass, sand in long swells, a light under it here and there, splinters; on the sand, dry grass in clumps and crystals at the foot of rock. */
    @Override
    protected void scatter(V v, Kit m, Spot s, int y) {
        Own o = own();
        if (s.wet) {
            double lie = s.drift * 0.9 + s.patch * 0.6;
            if (lie > 0.28) v.set(s.x, y, s.z, o.layers(1 + (int) ((lie - 0.28) * 7)));
            else if (N.rnd(Math.floorDiv(s.x, 8), Math.floorDiv(s.z, 8), seed + 51) < 0.1 && s.roll < 0.5) v.set(s.x, y - 1, s.z, o.glassLit);
            else if (s.roll > 0.985) v.set(s.x, y, s.z, o.shards);
            return;
        }
        BlockState on = v.get(s.x, y - 1, s.z);
        if (s.way == TRAIL || s.way == PAD || s.way == SPUR) {
            if (V.holds(on) && s.roll > 0.965) v.set(s.x, y, s.z, o.shards);
            return;
        }
        if (on == o.sand) {
            double clump = N.rnd(Math.floorDiv(s.x, 6), Math.floorDiv(s.z, 6), seed + 21);
            if (clump < 0.16 && s.roll < 0.3) v.set(s.x, y, s.z, o.grass);
            else if (s.rise >= 2 && s.roll < 0.1) v.set(s.x, y, s.z, o.crystal);
            // sand that lies flat is rippled by the wind (a whole block: it takes the place of what the land laid)
            else if (s.rise + s.drop == 0 && s.patch > 0.12) v.set(s.x, y - 1, s.z, o.rippled);
        } else if (V.holds(on) && s.roll > 0.95) v.set(s.x, y, s.z, o.shards);
    }

    /** A pillar: a drum of the old stone for a foot, the shaft, and either a carved head with a light on it or a broken top. */
    static final class Pillar extends Piece<Kit> {
        final int x, gy, z, h;
        final boolean lit;

        Pillar(int x, int z, int h, boolean lit, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.lit = lit;
            gy = land.ground(x, z);
            box(x, z, 1, 1);
            mayOverlap = true;
            label = "a pillar";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            v.foot(x, gy, z, o.stone);
            v.set(x, gy + 1, z, o.stone);
            for (int y = gy + 2; y <= gy + h; y++) v.set(x, y, z, o.column);
            if (!lit) return;
            v.set(x, gy + h, z, o.carved);
            v.set(x, gy + h + 1, z, o.glassLit);
        }
    }

    /** A shard of the glass standing in the sea: thick at the foot, leaning, drawn out to a point. */
    static final class Shard extends Piece<Kit> {
        final int x, gy, z, h;
        final double lx, lz, r0;
        final long salt;

        Shard(int x, int z, int h, long salt, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.salt = salt;
            gy = land.ground(x, z);
            double a = N.rnd(salt, 1, 2) * 6.283, lean = 0.12 + N.rnd(salt, 2, 2) * 0.22;
            lx = Math.cos(a) * lean;
            lz = Math.sin(a) * lean;
            r0 = 1.3 + h * 0.07;
            int reach = (int) Math.ceil(r0 + h * 0.36) + 1;
            box(x, z, reach, reach);
            mayOverlap = true;
            label = "a shard of the glass";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            for (int k = 0; k <= h; k++) {
                // never thinner than a block and a bit: each course has to stand on the one under it, however it leans
                double cx = x + lx * k, cz = z + lz * k, rad = Math.max(1.05, r0 * (1 - k / (h + 1.0)));
                int a = (int) Math.ceil(rad);
                boolean any = false;
                for (int dx = -a; dx <= a; dx++) for (int dz = -a; dz <= a; dz++) {
                    int px = (int) Math.round(cx) + dx, pz = (int) Math.round(cz) + dz;
                    if (Math.hypot(px - cx, pz - cz) > rad) continue;
                    if (k == 0) v.foot(px, gy, pz, o.glass);
                    v.set(px, gy + k, pz, N.rnd(px * 31L + k, pz, salt) < 0.12 ? o.glassLit : o.glass);
                    any = true;
                }
                if (!any || k == h) v.set((int) Math.round(cx), gy + k + (any ? 1 : 0), (int) Math.round(cz), o.glass);
            }
        }
    }

    /**
     * The Burning Glass: the tower that melted the sand. In front, the floor the Lord keeps: tile and sandstone in a
     * sunburst of twenty-four rays, a carved sun at its heart, inside a wall that is glass between twelve pillars, so the
     * sea is seen through it; a gate on the south side between two pylons. Behind it the tower: square, thirty-two across
     * at its foot and drawing in a block every eight rows, sandstone on a foot of brick, two rows of blue tile every
     * sixteen, a seam of lit glass up the middle of every face. On its head, in a ring of stone forty-four across, the
     * glass itself: a disc thirty-six across that still shines, facing the way you came.
     */
    static final class Tower extends Piece<Kit> {
        static final int TALL = 80, RING = 22, DISC = 18, LENS = TALL + 2 + RING, MID_Z = -41, HALF = 16;
        final int cx, cz, feet;
        final double radius;
        final long salt;
        final GeneratedLand land;

        Tower(int cx, int cz, int feet, double radius, long salt, GeneratedLand land) {
            this.cx = cx;
            this.cz = cz;
            this.feet = feet;
            this.radius = radius;
            this.salt = salt;
            this.land = land;
            box(cx, cz - 16, 30, 46);
            mayOverlap = true;
            startsWay = true;
            label = "the Burning Glass";
        }

        @Override
        public void draw(V v, Kit m) {
            Own o = own();
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                int dx = x - cx, dz = z - cz;
                if (dz <= MID_Z + HALF) tower(v, o, x, z, dx, dz - MID_Z);
                else court(v, o, x, z, dx, dz);
            }
        }

        private void tower(V v, Own o, int x, int z, int dx, int sz) {
            int ex = Math.abs(dx), ez = Math.abs(sz), far = Math.max(ex, ez);
            if (far <= HALF) {
                v.foot(x, feet - 1, z, o.stone);
                for (int k = 0; k < TALL; k++) {
                    int half = HALF - k / 8;
                    if (far > half) break;
                    BlockState b;
                    if (far < half) b = o.brick;
                    else {
                        int along = ex == half ? sz : dx;
                        if (ex == half && ez == half) b = o.stone;
                        else if (along == 0 && k >= 6) b = o.glassLit;
                        else if (Math.abs(along) == 2 && k % 16 >= 6 && k % 16 <= 9) b = o.glass;
                        else if (k < 3) b = o.brick;
                        else if (k % 16 >= 14) b = Math.floorMod(along, 4) == 2 && k % 16 == 14 ? o.carved : o.tile;
                        else b = o.stone;
                    }
                    v.set(x, feet + k, z, b);
                }
            }
            // its head: a floor of tile a little wider than the tower's top, and on it the ring that holds the glass
            int top = HALF - (TALL - 1) / 8;
            if (far <= top + 2) {
                v.set(x, feet + TALL, z, o.tile);
                if (far <= top) v.set(x, feet + TALL + 1, z, far == top ? o.stone : o.tile);
            }
            if (ez > 1 || ex > RING) return;
            for (int k = TALL + 2; k <= LENS + RING; k++) {
                double rr = Math.hypot(dx, k - LENS);
                if (rr > RING + 0.3) continue;
                if (rr >= DISC) {
                    double a = Math.atan2(k - LENS, dx), eighth = Math.PI / 4, off = Math.abs(a - Math.rint(a / eighth) * eighth) * rr;
                    v.set(x, feet + k, z, off < 1.6 ? o.tile : N.rnd(x * 31L + k, z, salt + 5) < 0.12 ? o.carved : o.stone);
                } else if (ez == 0) v.set(x, feet + k, z, o.glassLit);
            }
        }

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
                // the gate: a pylon either side of the way in, banded in tile, a light on each
                if (ex < 3) return;
                v.foot(x, gy, z, o.stone);
                for (int k = 0; k <= 11; k++) v.set(x, feet + k, z, k < 2 ? o.brick : k == 6 || k == 10 ? o.tile : k == 11 ? o.carved : o.stone);
                if (ex == 4 && line) v.set(x, feet + 12, z, o.glassLit);
                return;
            }
            // twelve pillars, and between them a wall of the glass on a kerb of stone
            double twelfth = Math.PI / 6, off = a - Math.floor(a / twelfth) * twelfth - twelfth / 2, alongWall = Math.abs(off) * radius, edge = twelfth * radius / 2;
            boolean pier = alongWall > edge - 1.1 && Math.abs(far - (radius + 1.2)) < 1.25;
            if (!pier && !line) return;
            v.foot(x, gy, z, o.stone);
            if (pier) {
                for (int k = 0; k <= 8; k++) v.set(x, feet + k, z, k == 0 ? o.stone : k == 8 ? o.carved : o.column);
            } else {
                v.set(x, feet, z, o.stone);
                for (int k = 1; k <= 4; k++) v.set(x, feet + k, z, o.glass);
            }
        }

        /** The Lord's floor: a sunburst, rays of blue tile and of sandstone turn about, a band of brick at two radii, a carved sun at the heart. */
        private void floor(V v, Own o, int x, int z, double far, double a) {
            if (v.get(x, feet - 1, z) == null || v.get(x, feet, z) != null) return;
            BlockState b;
            if (far < 2.5) b = o.carved;
            else if (far < 5.5 || Math.abs(far - radius * 0.62) < 0.7 || far > radius - 2.2) b = o.brick;
            else b = Math.floorMod((int) Math.floor(a / (Math.PI / 12)), 2) == 0 ? o.tile : o.stone;
            v.set(x, feet - 1, z, b);
        }
    }
}
