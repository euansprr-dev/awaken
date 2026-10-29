package dev.awaken.caves;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * The regions' cave shapes (SPEC §42.3), as densities: negative is open cave, about a quarter of a unit per block from
 * the wall, so the router's interpolation between cell corners finds smooth walls. Sampled only on the 4 × 8 × 4 cell
 * corners, so nothing narrower than about five blocks or lower than about six survives; every shape here is at least
 * that. The noises are the world's own (the pack's {@code awaken:caves/shape_a}, {@code shape_b} at about 128 blocks and
 * {@code shape_flat} at about 256; normalised, so about a third is one standard deviation), so a shape runs across chunk
 * borders whole. Everything 2D is worked out once a column ({@link Column}): a corner costs a few multiplications.
 */
final class CaveShapes {
    private CaveShapes() {}

    /** One column's shape values, worked out the first time its region asks. */
    static final class Column {
        boolean tubes, tilted, halls, fissure, lakes, geodes, vaults, warped, shaft;
        float across, centre, half, tilt;
        int layer = Integer.MIN_VALUE;
        float foot;
        // the P3 shapes' column values
        float hallAcross, hallCentre, crack, ledgePhase, lakeAcross, lakeCentre, throatAcross, throatY, vaultFoot, vaultFloor,
                vaultRoof, pillar, chamber, chamberY, shaftDist;

        void reset() {
            tubes = tilted = halls = fissure = lakes = geodes = vaults = warped = shaft = false;
            layer = Integer.MIN_VALUE;
        }
    }

    /**
     * The shape's density at a corner; {@code under} is how far below the seal it is, in blocks (the Shallows hang their
     * galleries from the ground, not from a fixed y).
     */
    static float density(CaveRegion.Shape shape, Column c, Noise a, Noise b, Noise flat, int x, int y, int z, float under) {
        return switch (shape) {
            case TUBES -> tubes(c, a, b, flat, x, y, z, under);
            case TERRACES -> terraces(c, a, flat, x, y, z);
            case HALLS -> halls(c, a, flat, x, y, z, under);
            case FISSURES -> fissure(c, a, flat, x, y, z);
            case LAKES -> lakes(c, b, flat, x, y, z);
            case GEODES -> geodes(c, a, flat, x, y, z);
            case VAULTS -> vaults(c, flat, x, y, z);
            case WARPED -> warped(c, a, b, flat, x, y, z);
            case NONE -> 1f;
        };
    }

    /** The Rootways' channels: half-widths in noise units, at the narrowest and added in a hall. */
    static final float CHANNEL = 0.035f, HALL = 0.06f;

    /**
     * The Rootways: winding galleries hung under the seal, two crossing families of channels (the zero lines of two 2D
     * noises), 5 to 9 blocks wide and 5 to 8 high, opening into low wide halls where the flat noise rises. Never above
     * {@link CaveRegion#MAX_Y} less a gallery, so a gallery under a hill isn't cut by the cap.
     */
    static float tubes(Column c, Noise a, Noise b, Noise flat, int x, int y, int z, float under) {
        if (!c.tubes) {
            float hall = Math.clamp((flat.get(x * 0.5, z * 0.5) - 0.2f) * 4f, 0f, 1f);
            float w = CHANNEL + HALL * hall;
            // across: the nearer channel of the two families, in quarter-units per block at their usual slope
            c.across = Math.min(Math.abs(a.get(x, z)) - w, Math.abs(b.get(x + 3000.0, z - 3000.0)) - w) * 9f;
            // a gallery's middle 7 to 11 blocks under the seal, taller in a hall
            c.centre = 9f + 2f * flat.get(x + 1500.0, z + 1500.0);
            c.half = 2.75f + 1.25f * hall;
            c.tubes = true;
        }
        // its middle as a y: hung from the seal, held under the cap
        float mid = Math.min(y + under - c.centre, CaveRegion.MAX_Y - 3 - c.half);
        float upDown = (Math.abs(y - mid) - c.half) * 0.25f;
        return Math.max(c.across, upDown);
    }

    /** The terraces' layers, in blocks: a gallery {@link #GALLERY} high in every {@link #LAYER}. */
    static final int LAYER = 16, GALLERY = 9;
    /** How much of a layer opens: where its footprint noise is above this (about 30% of each layer). */
    static final float FOOT = 0.17f;

    /**
     * The Ochre Deep: stacked galleries, one every {@link #LAYER} blocks, each with its own footprint (a slice of a 3D
     * noise, so neighbouring layers differ), floors gently tilted. Where one layer's footprint runs on past the one
     * above or below you get the ledges and drops between terraces.
     */
    static float terraces(Column c, Noise a, Noise flat, int x, int y, int z) {
        if (!c.tilted) {
            c.tilt = 4f * flat.get(x - 1500.0, z + 700.0);
            c.tilted = true;
        }
        float t = y + 64 + c.tilt;
        int k = Math.floorDiv((int) Math.floor(t), LAYER);
        float in = t - k * LAYER;
        float upDown = in < GALLERY ? (Math.abs(in - GALLERY / 2f) - GALLERY / 2f) * 0.25f : Math.min(in - GALLERY, LAYER - in) * 0.25f;
        if (c.layer != k) {
            c.foot = a.get(x * 0.8, k * 97.0, z * 0.8);
            c.layer = k;
        }
        float across = (FOOT - c.foot) * 6f;
        return Math.max(across, upDown);
    }

    // ---- P3 (SPEC §42.8) -------------------------------------------------------------------------------------------

    /** The Pale Halls: where the hall noise is above this (about 45% of the region), at least this channel between. */
    static final float HALL_FOOT = 0.08f, HALL_CHANNEL = 0.03f;

    /**
     * The Pale Halls: wide halls with flat roofs and floors on one bedding plane, 6 to 8 high, 10 to 13 under the seal,
     * joined by low passages along the channel noise where no hall is.
     */
    static float halls(Column c, Noise a, Noise flat, int x, int y, int z, float under) {
        if (!c.halls) {
            float hall = (HALL_FOOT - flat.get(x * 0.7 + 500.0, z * 0.7 - 500.0)) * 6f;
            float channel = (Math.abs(a.get(x * 0.9 + 1200.0, z * 0.9)) - HALL_CHANNEL) * 9f;
            c.hallAcross = Math.min(hall, channel);
            c.hallCentre = 11.5f + 1.5f * flat.get(x * 0.25 - 900.0, z * 0.25);
            c.halls = true;
        }
        float mid = Math.min(y + under - c.hallCentre, CaveRegion.MAX_Y - 5);
        float half = c.hallAcross < 0 && c.hallAcross > -0.6f ? 2.75f : 3.75f;   // the passages lower than the halls
        return Math.max(c.hallAcross, (Math.abs(y - mid) - half) * 0.25f);
    }

    /** The Windcleft's cracks: half-width in noise units, and how much a ledge band widens it. */
    static final float CRACK = 0.022f, LEDGE = 0.012f;
    /** The cracks close over the band's last blocks: they end at about y 4. */
    static final float CRACK_FLOOR = 4f, CRACK_CLOSE = 8f;

    /**
     * The Windcleft: tall narrow fissures along the zero lines of a long 2D noise, 5 to 8 wide, through the whole Middle
     * Dark under the hills (from y 59, or the Shallows' floor where that is lower, down to about y 4), widening in ledge
     * bands every 24 blocks and closing at the bottom.
     */
    static float fissure(Column c, Noise a, Noise flat, int x, int y, int z) {
        if (!c.fissure) {
            c.crack = Math.abs(a.get(x * 0.55 - 2500.0, z * 0.55 + 800.0));
            c.ledgePhase = 24f * flat.get(x * 0.3, z * 0.3 - 2100.0);
            c.fissure = true;
        }
        float ledge = Math.max(0f, Mth.sin((y + c.ledgePhase) * (Mth.TWO_PI / 24f)));
        float across = (c.crack - CRACK - LEDGE * ledge) * 20f;
        float close = Math.max(0f, CRACK_FLOOR + CRACK_CLOSE - y) * 0.25f + Math.max(0f, y - (CaveRegion.MAX_Y - 3)) * 0.25f;
        return across + close;
    }

    /** The Drowned Galleries: where the lake noise is above this (about 35%), the cavern 7 to 9 high round y 8 to 16. */
    static final float LAKE_FOOT = 0.13f, SUMP = 0.03f;

    /**
     * The Drowned Galleries: wide low lake caverns at a fixed depth (round y 12, under the aquifers' water, which floods
     * them), joined by low half-flooded passages along the second channel noise.
     */
    static float lakes(Column c, Noise b, Noise flat, int x, int y, int z) {
        if (!c.lakes) {
            float lake = (LAKE_FOOT - flat.get(x * 0.6 - 900.0, z * 0.6 + 900.0)) * 6f;
            float sump = (Math.abs(b.get(x * 0.8, z * 0.8 - 1700.0)) - SUMP) * 9f;
            c.lakeAcross = Math.min(lake, sump);
            c.lakeCentre = 12f + 4f * flat.get(x * 0.2 + 300.0, z * 0.2 + 3100.0);
            c.lakes = true;
        }
        float half = c.lakeAcross < 0 && c.lakeAcross > -0.6f ? 2.75f : 4f;
        return Math.max(c.lakeAcross, (Math.abs(y - c.lakeCentre) - half) * 0.25f);
    }

    /** The Glimmer Hollows' rooms: one in most cells of a {@link #GEODE}-block grid, 5 to 8 across in radius. */
    static final int GEODE = 28;
    static final float GEODE_ROOMS = 0.7f, THROAT = 0.03f;

    /**
     * The Glimmer Hollows: round crystal rooms (squat spheres on a jittered 3D grid, most cells holding one) off narrow
     * throats that wind through the band at one height a column.
     */
    static float geodes(Column c, Noise a, Noise flat, int x, int y, int z) {
        if (!c.geodes) {
            c.throatAcross = (Math.abs(a.get(x * 1.1 - 700.0, z * 1.1 + 4400.0)) - THROAT) * 9f;
            c.throatY = 14f + 7f * flat.get(x * 0.3 + 2200.0, z * 0.3);
            c.geodes = true;
        }
        float throat = Math.max(c.throatAcross, (Math.abs(y - c.throatY) - 2.75f) * 0.25f);
        int gx = Math.floorDiv(x, GEODE), gy = Math.floorDiv(y, GEODE), gz = Math.floorDiv(z, GEODE);
        float room = 1f;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    long h = hash(gx + i, gy + j, gz + k, 0x6E0DEL);
                    if (unit(h, 0) > GEODE_ROOMS) continue;
                    float cx = (gx + i + 0.25f + 0.5f * unit(h, 1)) * GEODE, cy = (gy + j + 0.3f + 0.4f * unit(h, 2)) * GEODE;
                    float cz = (gz + k + 0.25f + 0.5f * unit(h, 3)) * GEODE, r = 5f + 3f * unit(h, 4);
                    float dx = x - cx, dy = (y - cy) * 1.25f, dz = z - cz;
                    room = Math.min(room, ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - r) * 0.25f);
                }
            }
        }
        return Math.min(room, throat);
    }

    /** The Black Vaults: where the vault noise is above this (about half the region), and its columns' grid and radius. */
    static final float VAULT_FOOT = 0f;
    static final int COLUMN = 16;
    static final float COLUMN_R = 3.5f;

    /**
     * The Black Vaults: colossal halls on the Deep's floor (from about y -55, over the lava sea's top), their roofs rising
     * from 12 high at the edges to 48 in the middle, held up by ranks of rock columns on a jittered 16-block grid.
     */
    static float vaults(Column c, Noise flat, int x, int y, int z) {
        if (!c.vaults) {
            float f = flat.get(x * 0.5 - 3300.0, z * 0.5 + 1900.0);
            c.vaultFoot = (VAULT_FOOT - f) * 6f;
            c.vaultFloor = -55f + 3f * flat.get(x * 0.4, z * 0.4 + 5200.0);
            c.vaultRoof = 12f + 36f * Math.clamp((f - VAULT_FOOT) / 0.3f, 0f, 1f);   // the roof's height over the floor
            // the nearest column's distance (2D: they stand floor to roof)
            int gx = Math.floorDiv(x, COLUMN), gz = Math.floorDiv(z, COLUMN);
            float best = Float.MAX_VALUE;
            for (int i = -1; i <= 1; i++) {
                for (int k = -1; k <= 1; k++) {
                    long h = hash(gx + i, 0, gz + k, 0xBA5A17L);
                    float cx = (gx + i + 0.3f + 0.4f * unit(h, 0)) * COLUMN, cz = (gz + k + 0.3f + 0.4f * unit(h, 1)) * COLUMN;
                    best = Math.min(best, (x - cx) * (x - cx) + (z - cz) * (z - cz));
                }
            }
            c.pillar = (COLUMN_R - (float) Math.sqrt(best)) * 0.25f;   // positive inside a column
            c.vaults = true;
        }
        float upDown = Math.max(c.vaultFloor - y, y - (c.vaultFloor + c.vaultRoof)) * 0.25f;
        return Math.max(Math.max(c.vaultFoot, upDown), c.pillar);
    }

    /** The Violet Deep: its tunnels' half-width in noise units, and where a chamber opens (about a fifth of the region). */
    static final float WARP = 0.06f, CHAMBER = 0.22f;

    /**
     * The Violet Deep: tight tunnels winding in 3D (where two 3D noises are both near zero, as vanilla's noodles but
     * wider), opening now and then into a vaulted chamber 14 to 20 high.
     */
    static float warped(Column c, Noise a, Noise b, Noise flat, int x, int y, int z) {
        if (!c.warped) {
            c.chamber = (CHAMBER - flat.get(x * 0.8 + 4100.0, z * 0.8 - 600.0)) * 6f;
            c.chamberY = -30f + 6f * flat.get(x * 0.3 - 4100.0, z * 0.3);
            c.warped = true;
        }
        float n1 = Math.abs(a.get(x * 0.9 + 6100.0, y * 1.4, z * 0.9)), n2 = Math.abs(b.get(x * 0.9, y * 1.4 - 900.0, z * 0.9 + 6100.0));
        float tunnel = (Math.max(n1, n2) - WARP) * 9f;
        float dome = Math.max(c.chamber, (Math.abs(y - c.chamberY) - 8f) * 0.25f);
        return Math.min(tunnel, dome);
    }

    /**
     * Shafts and chasms joining the bands inside regions: a shaft (7 wide) in {@link #SHAFTS} of the cells of a
     * {@link #SHAFT}-block grid, a chasm (a slot as wide, {@link #CHASM_MIN} to twice that long, any heading) in
     * {@link #CHASMS} more.
     */
    static final int SHAFT = 160;
    static final float SHAFTS = 0.4f, SHAFT_R = 3.5f, CHASMS = 0.3f, CHASM_MIN = 24f;
    /** A shaft's foot: under the Middle Dark, into the top of the Deep (the Black Vaults' high roofs reach it). */
    static final int SHAFT_FLOOR = -14;

    /** How far a corner is outside the cell's shaft or chasm, as a density (negative inside it); the column's, worked out once. */
    static float shaft(Column c, int x, int z) {
        if (!c.shaft) {
            int gx = Math.floorDiv(x, SHAFT), gz = Math.floorDiv(z, SHAFT);
            long h = hash(gx, 0, gz, 0x5AF7L);
            float d = Float.MAX_VALUE, roll = unit(h, 0);
            if (roll < SHAFTS + CHASMS) {
                float cx = (gx + 0.2f + 0.6f * unit(h, 1)) * SHAFT, cz = (gz + 0.2f + 0.6f * unit(h, 2)) * SHAFT;
                if (roll >= SHAFTS) {
                    // a chasm: the distance to a segment through the cell's point
                    float a = unit(h, 3) * Mth.PI, half = CHASM_MIN * (0.5f + 0.5f * unit(h, 4));
                    float ux = Mth.cos(a), uz = Mth.sin(a), t = Mth.clamp((x - cx) * ux + (z - cz) * uz, -half, half);
                    cx += ux * t;
                    cz += uz * t;
                }
                d = (float) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            }
            c.shaftDist = (d - SHAFT_R) * 0.25f;
            c.shaft = true;
        }
        return c.shaftDist;
    }

    private static long hash(int x, int y, int z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    /** The k-th of a hash's units in [0, 1). */
    private static float unit(long h, int k) {
        long v = h * (0x9E3779B97F4A7C15L + 2L * k + 1);
        v ^= v >>> 31;
        return (v >>> 40) / (float) (1L << 24);
    }
}
