package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Theme;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.gates.world.Pieces.*;

/**
 * The Lords' arenas, each built for its Lord's moves (docs/modules/gates.md "Arenas"). An arena is a round 3x3-slot room
 * (radius 22, so every spot you can stand on is inside the Lord's arena radius; the Sky Leviathan's is islands over a
 * cloud sea) vaulting up to the top of the floor's band. The Lord appears on a lit medallion at the centre; the door
 * axis and a disc of radius 4 round the spot stay clear. Sizes scale with the room, so the inner gate's keeper arena
 * (2x2) works too. Cover sits at about 0.55 of the radius: far enough out that the fight has room, close enough to use.
 * From content version 8 every ring of cover and props also stands a block clear of its Lord's sweep ({@link Room#space}).
 */
final class PiecesArenas {
    private PiecesArenas() {}

    // ---- geometry ---------------------------------------------------------------------------------------------------

    static boolean blocks(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z), R = radius(r);
        return switch (r.piece) {
            case ARENA_BELL -> ringPoint(dx, dz, cover(r, 0.6, 1.5), 8, 0.5, 1.5);
            case ARENA_SPORES -> ringPoint(dx, dz, cover(r, 0.58, capR(r) + 0.5), 6, 0.5, 1.0);
            case ARENA_EMBER -> ringPoint(dx, dz, cover(r, 0.45, 1.0), 6, 0.5, 1.0);
            case ARENA_ARCHIVE -> ringPoint(dx, dz, cover(r, 0.55, 1.0), 8, 0.5, 1.0);
            case ARENA_GEARS -> ringPoint(dx, dz, cover(r, 0.55, 0.5), 6, 0.5, 0.5);
            case ARENA_GLASS -> ringPoint(dx, dz, cover(r, 0.55, 1.0), 6, 0.5, 1.0);
            case ARENA_HIVE -> ringPoint(dx, dz, cover(r, 0.55, 4.2), 6, 0.5, 1.5);
            case ARENA_ROOTS -> rootPillar(r, dx, dz);
            case ARENA_MARKET -> marketCounter(r, dx, dz);
            case ARENA_FROST -> ringPoint(dx, dz, cover(r, 0.55, 0.5), 8, 0.5, 0.5) || choirArc(r, dx, dz) != 0;
            case ARENA_MIRROR -> mirrorArc(r, dx, dz);
            case ARENA_LAB -> ringPoint(dx, dz, cover(r, 0.5, 1.0), 6, 0.5, 1.0);
            case ARENA_DREAM -> bed(r, dx, dz) != 0;
            case ARENA_SKY -> false;
            case ARENA_ABYSS -> ringPoint(dx, dz, cover(r, 0.6, 1.0), 6, 0.5, 1.0);
            default -> false;
        };
    }

    // ---- the Lord's space (content version 8, SPEC §38.15) ------------------------------------------------------------

    /**
     * How far each Lord's body sweeps round its spot in any clip (strikes, tails, its death), rounded up: boss-plan's
     * measure of the true-size rigs, 2026-10-30. Its arena keeps this clear from version 8.
     */
    static double sweep(SetPiece arena) {
        return switch (arena) {
            case ARENA_EMBER, ARENA_SKY -> 10;
            case ARENA_GLASS -> 9;
            case ARENA_GEARS, ARENA_BELL, ARENA_ROOTS -> 7;
            case ARENA_MARKET, ARENA_FROST -> 6;
            default -> 4;
        };
    }

    /** The Lords that swing through the inner gate's 2x2 keeper arena's cover: as a keeper they get the full 3x3 arena. */
    static boolean needsFullArena(Theme lord) {
        return switch (lord) {
            case EMBER_MINES, SKY_ARCHIPELAGO, GLASS_DESERT, CLOCKWORK_FOUNDRY -> true;
            default -> false;
        };
    }

    /**
     * The radius of a ring of props reaching {@code half} either side of it: {@code R * k} as version 7 built it, pushed
     * out from version 8 until the props stand a block clear of the Lord's sweep.
     */
    static double cover(Room r, double k, double half) {
        double R = radius(r);
        return r.space > 0 ? Math.max(R * k, r.space + half + 1) : R * k;
    }

    /** Whether the Lord's sweep (and a block to spare) reaches a spot: no prop there from version 8. */
    static boolean inSweep(Room r, double dx, double dz) {
        return r.space > 0 && Math.hypot(dx, dz) < r.space + 1;
    }

    /** The Mother of Spores's caps: their radius. */
    static double capR(Room r) {
        return Math.min(5.0, radius(r) * 0.24);
    }

    static int floorDelta(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z), R = radius(r), d = Math.hypot(dx, dz);
        return switch (r.piece) {
            case ARENA_EMBER -> lavaRing(r, dx, dz) ? -1 : KEEP;
            case ARENA_ARCHIVE -> d > R * 0.74 && d < R + 1 ? -1 : KEEP;
            case ARENA_GLASS -> d > R * 0.82 + Hash.noise(r.seed, x, z, 5) * 2.5 ? 1 : KEEP;
            default -> KEEP;
        };
    }

    static int ceilingDelta(Room r, int x, int z) {
        return 0;
    }

    static boolean wallClaim(Room r, int x, int z, int d) {
        return false;
    }

    static boolean paintWall(GatePainter.Ctx c, int bx, int bz, int x, int z, int od) {
        return false;
    }

    /** Arena walls' niches get the theme's arena touches: gears, mirrors, void. */
    static BlockState niche(GatePainter.Ctx c, int x, int z, int rel, int d, boolean big) {
        return switch (c.r.piece) {
            case ARENA_GEARS -> big && rel == 2 && GatePainter.Ctx.panelCentre((d == 0 || d == 2) ? x : z) ? GatesRegistry.GEAR.get().defaultBlockState() : null;
            default -> null;
        };
    }

    /** Sky Leviathan: island ground under this column (the rest is the cloud sea three blocks down). */
    static boolean skyIsland(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z), R = radius(r), d = Math.hypot(dx, dz);
        double n = Hash.fbm(r.seed ^ 0x51A1L, x, z, 4) * 1.6;
        if (d < R * 0.42 + n) return true;
        for (int k = 0; k < 5; k++) {
            double a = (k + 0.5) * Math.PI * 2 / 5;
            if (Math.hypot(dx - Math.cos(a) * R * 0.74, dz - Math.sin(a) * R * 0.74) < R * 0.15 + n) return true;
        }
        return false;
    }

    // ---- painting ---------------------------------------------------------------------------------------------------

    static boolean paint(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        Room r = c.r;
        double dx = dx(r, x), dz = dz(r, z);
        if (fo == 0) focal(c, bx, bz, dx, dz, walk);
        return switch (r.piece) {
            case ARENA_BELL -> bell(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_SPORES -> spores(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_EMBER -> ember(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case ARENA_ARCHIVE -> archive(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case ARENA_GEARS -> gears(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_GLASS -> glass(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case ARENA_HIVE -> hive(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_ROOTS -> roots(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_MARKET -> market(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_FROST -> frost(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_MIRROR -> mirror(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_LAB -> lab(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_DREAM -> dream(c, bx, bz, x, z, dx, dz, walk, ceil);
            case ARENA_SKY -> sky(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case ARENA_ABYSS -> abyss(c, bx, bz, x, z, dx, dz, walk, ceil);
            default -> false;
        };
    }

    static boolean free(Room r, int x, int z) {
        return !Pieces.clear(r, x, z);
    }

    /** Every arena: the Lord's medallion and a ring of lights set in the floor round it, looking up at the Lord. */
    static void focal(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk) {
        double d = Math.hypot(dx, dz);
        if (d <= 2.3) c.set(bx, walk - 1, bz, c.k.medallion());
        else if (d < 3.0) c.set(bx, walk - 1, bz, c.k.band());
        else if (!c.lightless && ring(dx, dz, 3.5, 0.5) && ringPoint(dx, dz, 3.5, 8, 0, 0.5)) c.set(bx, walk - 1, bz, c.k.sconce());
        // dim themes light their Lord from above with a light you can't see (a mapmaker's spotlight)
        if (!c.lightless && c.k.sconce().getLightEmission() < 14 && Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6)
            c.set(bx, walk + 5, bz, s("minecraft:light[level=15]"));
    }

    /** A crown of lights hanging from the apex over the Lord. */
    static boolean crown(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int ceil, double radius, int drop, BlockState frame, BlockState light) {
        double d = Math.hypot(dx, dz);
        int y = ceil - drop;
        boolean claimed = false;
        if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) {
            for (int yy = ceil - 1; yy > y; yy--) c.set(bx, yy, bz, GatePainter.chain());
            c.set(bx, y, bz, frame);
            claimed = true;
        }
        if (ring(dx, dz, radius, 0.5)) {
            c.set(bx, y, bz, frame);
            if (!c.lightless && ringPoint(dx, dz, radius, 8, 0, 0.5)) c.set(bx, y - 1, bz, light);
            claimed = true;
        }
        // spokes from the centre to the ring
        if (d < radius && (Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6)) {
            c.set(bx, y, bz, GatePainter.axis(GatePainter.chain(), Math.abs(dx) < 0.6 ? "z" : "x"));
            claimed = true;
        }
        if (ringPoint(dx, dz, radius, 4, 0.5, 0.5)) for (int yy = ceil - 1; yy > y; yy--) c.set(bx, yy, bz, GatePainter.chain());
        return claimed;
    }

    /** Chains with lights hanging round the rim of the vault. */
    static boolean rimLights(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, double R, int n, BlockState light) {
        if (c.lightless || !ringPoint(dx, dz, R, n, 0, 0.5)) return false;
        int bottom = Math.max(walk + 6, ceil - 6);
        for (int y = ceil - 1; y > bottom; y--) c.set(bx, y, bz, GatePainter.chain());
        c.set(bx, bottom, bz, light);
        return true;
    }

    // =================================================================================================================

    /**
     * The Bell Warden's belfry: its great bell hangs over the medallion where it stands, rings on the floor show how far
     * a Toll Wave travels, and eight statues stand round the floor (cover from the Echo Bolts; halfway through, they move).
     */
    static boolean bell(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        boolean claimed = false;
        if (d > 4.2 && (ring(dx, dz, R * 0.3, 0.5) || ring(dx, dz, R * 0.52, 0.5) || ring(dx, dz, R * 0.75, 0.5))) c.set(bx, walk - 1, bz, c.k.band());
        double lip = Math.min(5.2, R * 0.24);
        int tall = 9;
        if (d <= lip + 0.6) {
            Pieces.bell(c, bx, bz, dx, dz, ceil - tall - 3, tall, lip, ceil, bronze(), s("minecraft:polished_deepslate"),
                    c.lightless ? null : s("minecraft:pearlescent_froglight"));
            claimed = true;
        }
        double rs = cover(r, 0.6, 1.5);
        if (ringPoint(dx, dz, rs, 8, 0.5, 1.5) && free(r, x, z)) {
            // a robed statue on a plinth: the plus-shaped body, shoulders, a hooded head holding up a soul lantern
            int k = spoke(dx, dz, 8, 0.5);
            double a = (k + 0.5) * Math.PI / 4;
            double sx = Math.floor(Math.cos(a) * rs) + 0.5, sz = Math.floor(Math.sin(a) * rs) + 0.5;
            double lx = dx - sx, lz = dz - sz;
            boolean centre = Math.abs(lx) < 0.5 && Math.abs(lz) < 0.5, cross = Math.abs(lx) < 0.5 || Math.abs(lz) < 0.5;
            c.set(bx, walk, bz, c.k.trim());
            if (cross) {
                for (int y = walk + 1; y <= walk + 3; y++) c.set(bx, y, bz, s("minecraft:deepslate_tiles"));
                if (centre) {
                    c.set(bx, walk + 4, bz, s("minecraft:polished_deepslate"));
                    c.set(bx, walk + 5, bz, s("minecraft:chiseled_deepslate"));
                    if (!c.lightless) c.set(bx, walk + 6, bz, s("minecraft:soul_lantern[hanging=false]"));
                } else {
                    int face = Math.abs(lx) > Math.abs(lz) ? (lx > 0 ? 1 : 3) : (lz > 0 ? 2 : 0);
                    c.set(bx, walk + 4, bz, GatePainter.facing(s("minecraft:polished_deepslate_stairs[half=bottom]"), (face + 2) % 4));
                }
            } else c.set(bx, walk + 1, bz, s("minecraft:polished_deepslate_slab[type=bottom]"));
            return true;
        }
        return rimLights(c, bx, bz, dx, dz, walk, ceil, R * 0.82, 12, s("minecraft:soul_lantern[hanging=true]")) || claimed;
    }

    /**
     * Mother of Spores: six giant caps round the floor are the only shelter from the Spore Rain; an inverted cap hangs
     * from the vault where she floats, dripping spore blossoms; the floor is soft with moss.
     */
    static boolean spores(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        boolean claimed = false;
        if (d > 4.2 && Hash.noise(r.seed, x, z, 4) < 0.45) c.set(bx, walk - 1, bz, s("minecraft:moss_block"));
        // the six caps: find the nearest one
        int k = spoke(dx, dz, 6, 0.5);
        double a = (k + 0.5) * Math.PI / 3;
        double capR = capR(r), rc = cover(r, 0.58, capR + 0.5);
        double mx = Math.round(Math.cos(a) * rc), mz = Math.round(Math.sin(a) * rc);
        if (Math.hypot(dx - mx, dz - mz) <= capR + 0.5)
            claimed = PiecesRooms.mushroom(c, bx, bz, dx - mx, dz - mz, walk, 7 + k % 2, capR, ceil, free(r, x, z)) || claimed;
        // the hanging cap over the Lord
        int hy = ceil - 2;
        if (d < 4.6) {
            BlockState cap = GatesRegistry.GLOWCAP.get().defaultBlockState();
            c.set(bx, hy, bz, d < 3.6 ? GatesRegistry.GLOWCAP_GILLS.get().defaultBlockState() : cap);
            if (d >= 3.6) c.set(bx, hy - 1, bz, cap);
            if (d < 2.0) c.set(bx, hy + 1, bz, cap);
            if (!c.lightless && d > 1.5 && d < 3.4 && Hash.unit(r.seed, x, z) < 0.4) c.set(bx, hy - 1, bz, s("minecraft:spore_blossom"));
            claimed = true;
        }
        if (d < 1.0) for (int y = ceil - 1; y > hy; y--) c.set(bx, y, bz, s("minecraft:mushroom_stem"));
        // spore blossoms across the vault
        if (!c.lightless && d > 6 && Hash.unit(r.seed, x * 7, z) < 0.025) c.set(bx, ceil - 1, bz, s("minecraft:spore_blossom"));
        return claimed;
    }

    static boolean lavaRing(Room r, double dx, double dz) {
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d < R * 0.7 || d > R * 0.7 + 2.2) return false;
        // bridges on six spokes (the door's axis is always one: Pieces.clear keeps it level)
        double a = Math.atan2(dz, dx);
        double step = Math.PI / 3;
        double off = a - Math.round(a / step) * step;
        return Math.abs(off) * d > 1.6;
    }

    /**
     * The Ember Tyrant's forge floor: a moat of lava rings the arena (stone bridges cross it), six basalt columns stand
     * in the middle ring as cover from the Eruption, glowing seams run out from the anvil-stone it stands on, lanterns
     * hang from timber beams across the vault.
     */
    static boolean ember(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (fo == -1) {
            c.set(bx, walk - 1, bz, s("minecraft:magma_block"));
            c.set(bx, walk, bz, s("minecraft:lava"));
            return true;
        }
        if (d >= R * 0.7 - 1 && d <= R * 0.7 + 3.2) c.set(bx, walk - 1, bz, s("minecraft:polished_blackstone_bricks"));
        // seams of glowing ore running out from the centre
        double a = Math.atan2(dz, dx);
        double off = a - Math.round(a / (Math.PI / 4)) * (Math.PI / 4);
        if (d > 4.5 && d < R * 0.68 && Math.abs(off) * d < 0.55) c.set(bx, walk - 1, bz, GatesRegistry.EMBER_ORE.get().defaultBlockState());
        if (ringPoint(dx, dz, cover(r, 0.45, 1.0), 6, 0.5, 1.0) && free(r, x, z)) {
            for (int y = walk; y < Math.min(ceil - 1, walk + 7); y++) c.set(bx, y, bz, y == walk ? s("minecraft:polished_blackstone") : s("minecraft:polished_basalt[axis=y]"));
            c.set(bx, Math.min(ceil - 1, walk + 7), bz, s("minecraft:gilded_blackstone"));
            return true;
        }
        // timber beams across the vault, lanterns under them (from version 8 a block higher, over the Tyrant's chop)
        int by = walk + (r.space > 0 ? 14 : 13);
        if (by < ceil - 1 && (Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6)) {
            c.set(bx, by, bz, GatePainter.axis(s("minecraft:stripped_dark_oak_log"), Math.abs(dx) < 0.6 ? "z" : "x"));
            if (!c.lightless && d > 4 && Math.floorMod((int) Math.round(d), 5) == 0) c.set(bx, by - 1, bz, s("minecraft:lantern[hanging=true]"));
            return true;
        }
        return false;
    }

    /**
     * The Drowned Archivist's reading room: eight tall stacks stand round the floor (brace behind them when the Tidal Page
     * washes you back), the outer ring is already flooded, a great chandelier of lanterns hangs over the reading desk.
     */
    static boolean archive(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (fo == -1) {
            c.set(bx, walk - 1, bz, !c.lightless && Math.floorMod(x * 5 + z * 3, 9) == 0 ? s("minecraft:sea_lantern") : s("minecraft:dark_prismarine"));
            c.set(bx, walk, bz, s("minecraft:water"));
            return true;
        }
        if (ringPoint(dx, dz, cover(r, 0.55, 1.0), 8, 0.5, 1.0) && free(r, x, z)) {
            int top = Math.min(ceil - 2, walk + 7);
            for (int y = walk; y < top; y++) c.set(bx, y, bz, Hash.unit(r.seed, x + y, z) < 0.35 ? GatesRegistry.SODDEN_SHELF.get().defaultBlockState() : s("minecraft:bookshelf"));
            c.set(bx, top, bz, s("minecraft:dark_prismarine_slab[type=bottom]"));
            if (!c.lightless && Math.floorMod(x + z, 2) == 0) c.set(bx, top + 1, bz, s("minecraft:lantern[hanging=false]"));
            return true;
        }
        return crown(c, bx, bz, dx, dz, ceil, 3.5, 6, s("minecraft:dark_prismarine"), s("minecraft:lantern[hanging=true]"));
    }

    /**
     * The Gearwright Colossus's works: six copper pylons with lightning rods (the Overload Arc jumps between them), grate
     * rings in the floor, a great gear turning flat under the vault, gears set into the wall niches.
     */
    static boolean gears(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > 4.2 && (ring(dx, dz, R * 0.3, 0.5) || ring(dx, dz, R * 0.72, 0.5))) c.set(bx, walk - 1, bz, s("minecraft:waxed_copper_grate"));
        if (ringPoint(dx, dz, cover(r, 0.55, 0.5), 6, 0.5, 0.5) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:iron_block"));
            for (int y = walk + 1; y < walk + 5; y++) c.set(bx, y, bz, y == walk + 3 && !c.lightless ? s("minecraft:waxed_copper_bulb[lit=true]") : s("minecraft:waxed_cut_copper"));
            c.set(bx, walk + 5, bz, s("minecraft:waxed_lightning_rod[facing=up]"));
            return true;
        }
        // the great gear under the vault
        int gy = ceil - 3;
        double gr = Math.min(8.0, R * 0.36);
        if (d < gr + 1.0 && gy > walk + 10) {
            double a = Math.atan2(dz, dx);
            boolean tooth = Math.floorMod((int) Math.floor((a + Math.PI) / (2 * Math.PI) * 24), 2) == 0;
            BlockState s = null;
            if (d > gr) s = tooth ? s("minecraft:waxed_copper_block") : null;
            else if (d > gr - 1.2) s = s("minecraft:waxed_copper_block");
            else if (d < 1.5) s = c.lightless ? s("minecraft:iron_block") : s("minecraft:waxed_copper_bulb[lit=true]");
            else if (Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.7) s = s("minecraft:iron_block");
            if (s != null) c.set(bx, gy, bz, s);
            if (d < 1.0) for (int y = ceil - 1; y > gy; y--) c.set(bx, y, bz, s("minecraft:iron_block"));
            return s != null;
        }
        return false;
    }

    /**
     * The Glass Serpent's dunes: the floor is sand it swims through, six glass crystals stand in the middle ring to break
     * its lunges, dunes bank against the walls, an amber oculus lights the arena like a sun.
     */
    static boolean glass(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > 4.2) c.set(bx, walk - 1, bz, ring(dx, dz, R * 0.3, 0.6) ? s("minecraft:cut_sandstone") : s("minecraft:sand"));
        if (ringPoint(dx, dz, cover(r, 0.55, 1.0), 6, 0.5, 1.0) && free(r, x, z)) {
            int k = spoke(dx, dz, 6, 0.5);
            int h = 4 + (k * 3 + (int) Math.floorMod(Math.round(dx + dz), 3L)) % 4;
            c.set(bx, walk - 1, bz, c.lightless ? s("minecraft:sandstone") : s("minecraft:glowstone"));
            for (int i = 0; i < h; i++) {
                double t = Hash.unit(r.seed, x * 31 + i, z);
                c.set(bx, walk + i, bz, i == h - 1 ? s("minecraft:amethyst_cluster[facing=up]")
                        : t < 0.45 ? GatesRegistry.SANDGLASS.get().defaultBlockState() : t < 0.75 ? s("minecraft:glass") : s("minecraft:tinted_glass"));
            }
            return true;
        }
        // the oculus: amber glass in the vault with the sun behind it
        if (d < 4.0 && !c.lightless) {
            c.set(bx, ceil, bz, GatesRegistry.SANDGLASS.get().defaultBlockState());
            c.set(bx, ceil + 1, bz, s("minecraft:glowstone"));
            return true;
        }
        if (fo == 1 && Hash.unit(r.seed, x, z) < 0.05) c.set(bx, walk, bz, s("minecraft:dead_bush"));
        return false;
    }

    /**
     * The Hive Regent's throne room: six comb pillars hold up canopies of comb (shelter from the Honey Rain), honey pools
     * slow you in the middle ring, a crown of royal jelly hangs over the Regent.
     */
    static boolean hive(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        boolean claimed = false;
        int k = spoke(dx, dz, 6, 0.5);
        double a = (k + 0.5) * Math.PI / 3;
        double rp = cover(r, 0.55, 4.2);
        double px = Math.floor(Math.cos(a) * rp) + 0.5, pz = Math.floor(Math.sin(a) * rp) + 0.5;
        double pd = Math.hypot(dx - px, dz - pz);
        if (Math.abs(dx - px) < 1.5 && Math.abs(dz - pz) < 1.5 && free(r, x, z)) {
            for (int y = walk; y < ceil; y++) c.set(bx, y, bz, (y + x + z) % 3 == 0 ? GatesRegistry.BROOD_CELL.get().defaultBlockState() : s("minecraft:honeycomb_block"));
            return true;
        }
        if (pd < 4.2 && ceil - walk > 9) {
            c.set(bx, walk + 6, bz, s("minecraft:honeycomb_block"));
            if (pd > 3.4) c.set(bx, walk + 5, bz, s("minecraft:honey_block"));
            claimed = true;
        }
        if (d > R * 0.3 && d < R * 0.4 && Hash.noise(r.seed, x, z, 3) < 0.5) c.set(bx, walk - 1, bz, s("minecraft:honey_block"));
        if (d < 3.5) {
            int y = ceil - 4;
            if (d < 1.0) for (int yy = ceil - 1; yy > y; yy--) c.set(bx, yy, bz, s("minecraft:honeycomb_block"));
            c.set(bx, y, bz, d < 2.5 && !c.lightless ? GatesRegistry.ROYAL_JELLY.get().defaultBlockState() : s("minecraft:honeycomb_block"));
            if (d < 1.6) c.set(bx, y - 1, bz, GatesRegistry.ROYAL_JELLY.get().defaultBlockState());
            claimed = true;
        }
        if (Hash.unit(r.seed, x * 3, z) < 0.03 && d > 5) {
            for (int i = 1; i <= 2; i++) c.set(bx, ceil - i, bz, s("minecraft:honey_block"));
            claimed = true;
        }
        return claimed;
    }

    static boolean rootPillar(Room r, double dx, double dz) {
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d < 6 || d > R * 0.82) return false;
        double gx = dx / 7.0, gz = dz / 7.0;
        double fx = gx - Math.round(gx), fz = gz - Math.round(gz);
        // from version 8 the pillars inside the Saint's sweep (and a block to spare) aren't raised
        if (r.space > 0 && Math.hypot(Math.round(gx) * 7.0, Math.round(gz) * 7.0) < r.space + 2) return false;
        return Math.abs(fx) * 7 < 1.0 && Math.abs(fz) * 7 < 1.0;
    }

    /**
     * The Root Saint's sanctuary: pillars wrapped in roots on a seven-block grid (dodge the Root Lances down the lanes
     * between them), a jade altar where the Saint stands, a canopy of leaves under the vault hung with glowing vines.
     */
    static boolean roots(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (rootPillar(r, dx, dz) && free(r, x, z)) {
            for (int y = walk; y < ceil; y++) {
                int rel = y - walk;
                c.set(bx, y, bz, rel == 0 ? GatesRegistry.JADE.get().defaultBlockState()
                        : rel == 2 && !c.lightless && Math.floorMod(x + z, 2) == 0 ? s("minecraft:verdant_froglight") // a lamp in the root cage
                        : Hash.unit(r.seed, x + rel, z) < 0.35 ? s("minecraft:mangrove_roots")
                        : rel % 4 == 2 ? s("minecraft:chiseled_stone_bricks") : s("minecraft:mossy_stone_bricks"));
            }
            return true;
        }
        if (d > 4.2 && Hash.noise(r.seed, x, z, 3) < 0.3) c.set(bx, walk - 1, bz, s("minecraft:moss_block"));
        boolean claimed = false;
        if (d > R * 0.4 && d < R && ceil - walk > 10) {
            c.set(bx, ceil - 1, bz, s("minecraft:jungle_leaves[persistent=true]"));
            if (Hash.unit(r.seed, x, z) < 0.3) c.set(bx, ceil - 2, bz, s("minecraft:jungle_leaves[persistent=true]"));
            if (!c.lightless && Hash.unit(r.seed, x * 5, z) < 0.07) {
                int len = 2 + (int) (Hash.unit(r.seed, z, x) * 4);
                for (int i = 1; i <= len; i++) c.set(bx, ceil - 2 - i, bz, s(i == len ? "minecraft:cave_vines[berries=true]" : "minecraft:cave_vines_plant[berries=true]"));
            }
            claimed = true;
        }
        return crown(c, bx, bz, dx, dz, ceil, 3.0, 7, s("minecraft:stripped_jungle_log[axis=y]"), s("minecraft:lantern[hanging=true]"))
                || rimLights(c, bx, bz, dx, dz, walk, ceil, R * 0.9, 12, s("minecraft:lantern[hanging=true]")) || claimed;
    }

    static boolean marketCounter(Room r, double dx, double dz) {
        double R = radius(r), d = Math.hypot(dx, dz);
        if (Math.abs(d - R * 0.78) > 0.6) return false;
        double a = Math.atan2(dz, dx) / (Math.PI / 4);
        double frac = a - Math.floor(a);
        return frac > 0.22 && frac < 0.78;
    }

    /** The Lantern Broker's plaza: stalls in a ring under striped awnings, lantern strings from the vault, a round rug. */
    static boolean market(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > 4.2 && d < R * 0.35) c.set(bx, walk - 1, bz, s(((int) Math.floor(d) % 2 == 0) ? "minecraft:red_wool" : "minecraft:yellow_wool"));
        if (marketCounter(r, dx, dz) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:spruce_planks"));
            double g = Hash.unit(r.seed, x, z);
            c.set(bx, walk + 1, bz, s(g < 0.3 ? "minecraft:potted_red_tulip" : g < 0.5 ? "minecraft:cake" : g < 0.7 ? "minecraft:melon" : "minecraft:flower_pot"));
            c.set(bx, walk + 3, bz, s(Math.floorMod(x + z, 2) == 0 ? "minecraft:red_wool" : "minecraft:yellow_wool"));
            return true;
        }
        if (Math.abs(d - R * 0.78 + 1) < 0.6 && marketCounter(r, dx * (d + 1) / d, dz * (d + 1) / d)) {
            c.set(bx, walk + 3, bz, s(Math.floorMod(x + z, 2) == 0 ? "minecraft:red_wool" : "minecraft:yellow_wool"));
            if (!c.lightless && Math.floorMod(x + z, 3) == 0) c.set(bx, walk + 2, bz, s("minecraft:lantern[hanging=true]"));
            return true;
        }
        // lantern strings radiating from the apex, sagging
        if (spokeLine(dx, dz, 8) && d > 2 && d < R * 0.9) {
            int y = ceil - 1 - (int) Math.round(Math.sin(d / (R * 0.9) * Math.PI) * 3);
            c.set(bx, y, bz, GatePainter.chain());
            if (!c.lightless && Math.floorMod((int) Math.round(d), 3) == 0) c.set(bx, y - 1, bz, GatesRegistry.PAPER_LANTERN.get().defaultBlockState());
            return true;
        }
        return false;
    }

    static boolean spokeLine(double dx, double dz, int n) {
        double a = Math.atan2(dz, dx);
        double step = Math.PI * 2 / n;
        double off = a - Math.round(a / step) * step;
        return Math.abs(off) * Math.hypot(dx, dz) < 0.55;
    }

    /** Choir stalls round the Abbot's chapel: 1 seat, 2 back, 0 none (arcs with gaps on eight spokes). */
    static int choirArc(Room r, double dx, double dz) {
        double R = radius(r), d = Math.hypot(dx, dz);
        double a = Math.atan2(dz, dx) / (Math.PI / 4);
        double frac = a - Math.floor(a);
        if (frac < 0.18 || frac > 0.82) return 0;
        if (Math.abs(d - R * 0.8) < 0.5) return 1;
        if (Math.abs(d - (R * 0.8 + 1)) < 0.5) return 2;
        return 0;
    }

    /**
     * The Abbot of Frost's chapel: eight quartz pillars, choir stalls in arcs round the floor (the Choir sings from
     * them), a ring of ice where the Ice Spikes come up, an altar of blue ice under a crown of sea lanterns.
     */
    static boolean frost(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > R * 0.3 && d < R * 0.45) c.set(bx, walk - 1, bz, Hash.noise(r.seed, x, z, 3) < 0.6 ? s("minecraft:packed_ice") : s("minecraft:blue_ice"));
        if (ringPoint(dx, dz, cover(r, 0.55, 0.5), 8, 0.5, 0.5) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:quartz_bricks"));
            for (int y = walk + 1; y < ceil; y++) c.set(bx, y, bz, (y - walk) % 6 == 5 ? s("minecraft:chiseled_quartz_block") : s("minecraft:quartz_pillar[axis=y]"));
            return true;
        }
        int ch = choirArc(r, dx, dz);
        if (ch != 0 && free(r, x, z)) {
            int face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 3 : 1) : (dz > 0 ? 0 : 2);
            if (ch == 1) c.set(bx, walk, bz, GatePainter.facing(s("minecraft:quartz_stairs"), (face + 2) % 4));
            else {
                c.set(bx, walk, bz, s("minecraft:quartz_bricks"));
                c.set(bx, walk + 1, bz, s("minecraft:quartz_slab[type=bottom]"));
                if (!c.lightless && Math.floorMod(x + z, 4) == 0) c.set(bx, walk + 2, bz, s("minecraft:white_candle[candles=3,lit=true]"));
            }
            return true;
        }
        if (d > R * 0.85 && Hash.unit(r.seed, x, z) < 0.5) c.set(bx, walk, bz, s("minecraft:snow[layers=2]"));
        return crown(c, bx, bz, dx, dz, ceil, 4.0, 7, s("minecraft:chiseled_quartz_block"), s("minecraft:sea_lantern"));
    }

    static boolean mirrorArc(Room r, double dx, double dz) {
        double R = radius(r), d = Math.hypot(dx, dz);
        if (Math.abs(d - R * 0.66) > 0.55) return false;
        double a = Math.atan2(dz, dx) / (Math.PI / 4);
        double frac = a - Math.floor(a);
        return frac > 0.3 && frac < 0.7;
    }

    /**
     * The Mirror Knight's hall: eight mirrors in a ring face the floor (it steps through the glass), a black and white
     * floor with a star of quartz, a crystal chandelier of end rods and sea lanterns.
     */
    static boolean mirror(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > 4.2 && spokeLine(dx, dz, 8) && d < R * 0.6) c.set(bx, walk - 1, bz, s("minecraft:chiseled_quartz_block"));
        if (mirrorArc(r, dx, dz) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:chiseled_quartz_block"));
            for (int y = walk + 1; y < walk + 5; y++) c.set(bx, y, bz, GatesRegistry.MIRROR.get().defaultBlockState());
            c.set(bx, walk + 5, bz, s("minecraft:chiseled_quartz_block"));
            return true;
        }
        // the chandelier: glass rings and end rods
        int y = ceil - 5;
        if (d < 4.6 && y > walk + 8) {
            if (ring(dx, dz, 4.0, 0.55)) c.set(bx, y, bz, s("minecraft:glass"));
            if (ring(dx, dz, 2.5, 0.55)) c.set(bx, y + 1, bz, s("minecraft:glass"));
            if (!c.lightless && ringPoint(dx, dz, 4.0, 8, 0, 0.5)) c.set(bx, y - 1, bz, s("minecraft:end_rod[facing=down]"));
            if (d < 1.0) {
                for (int yy = ceil - 1; yy > y; yy--) c.set(bx, yy, bz, GatePainter.chain());
                c.set(bx, y, bz, c.lightless ? s("minecraft:glass") : s("minecraft:sea_lantern"));
            }
            return true;
        }
        return rimLights(c, bx, bz, dx, dz, walk, ceil, R * 0.85, 16, s("minecraft:end_rod[facing=down]"));
    }

    /**
     * Unit Zero's test chamber: target rings in the floor, six plated crates as cover from the Zero Lance, hazard stripes
     * round the edge, an oculus of light panels in the vault where the Orbital Marks come from.
     */
    static boolean lab(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > 4.2) {
            if (Math.floorMod((int) Math.floor(d), 4) == 0) c.set(bx, walk - 1, bz, s("minecraft:cyan_concrete"));
            if (d > R * 0.88) c.set(bx, walk - 1, bz, s(Math.floorMod((int) Math.floor(Math.atan2(dz, dx) * R / 1.5), 2) == 0 ? "minecraft:yellow_concrete" : "minecraft:black_concrete"));
        }
        if (ringPoint(dx, dz, cover(r, 0.5, 1.0), 6, 0.5, 1.0) && free(r, x, z)) {
            c.set(bx, walk, bz, s("awaken:precursor_plating"));
            c.set(bx, walk + 1, bz, s("awaken:precursor_plating"));
            c.set(bx, walk + 2, bz, Math.floorMod(x + z, 2) == 0 && !c.lightless ? GatesRegistry.LAB_PANEL.get().defaultBlockState() : s("minecraft:smooth_stone_slab[type=bottom]"));
            return true;
        }
        if (d < 5.0 && !c.lightless) {
            c.set(bx, ceil, bz, d < 2.0 ? s("minecraft:sea_lantern") : ring(dx, dz, 3.5, 0.6) ? GatesRegistry.LAB_PANEL.get().defaultBlockState() : s("awaken:precursor_plating"));
            return true;
        }
        return false;
    }

    /** The Sleeper's bed (at the far side from the door): 1 frame, 2 mattress, 3 pillow, 0 none. */
    static int bed(Room r, double dx, double dz) {
        Room.Door door = r.doors.isEmpty() ? null : r.doors.get(0);
        int side = door == null ? 0 : (door.side + 2) % 4;
        double R = radius(r);
        // u across the bed, v from its foot (toward the centre) to its head (at the wall)
        double u, v;
        switch (side) {
            case 0 -> { u = dx; v = -dz; }
            case 2 -> { u = dx; v = dz; }
            case 1 -> { u = dz; v = dx; }
            default -> { u = dz; v = -dx; }
        }
        double foot = R * 0.42, head = R * 0.42 + 9;
        if (Math.abs(u) > 4.0 || v < foot || v > head) return 0;
        if (Math.abs(u) > 3.0 || v > head - 1) return 1;
        if (v > head - 3) return 3;
        return 2;
    }

    /**
     * The Sleeper's bedroom: a bed for a giant against the far wall, furniture floating at every height round the room,
     * stars of crying obsidian in the vault, the floor never the same twice.
     */
    static boolean dream(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        int b = bed(r, dx, dz);
        if (b != 0 && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:dark_oak_planks"));
            c.set(bx, walk + 1, bz, s(b == 1 ? "minecraft:dark_oak_planks" : b == 3 ? "minecraft:white_wool" : "minecraft:red_wool"));
            if (b == 3) c.set(bx, walk + 2, bz, s("minecraft:white_wool"));
            if (b == 1 && Hash.unit(r.seed, x, z) < 0.2) c.set(bx, walk + 2, bz, s("minecraft:dark_oak_planks"));
            return true;
        }
        // floating furniture on a ring, each piece at its own height
        if (ringPoint(dx, dz, R * 0.6, 10, 0, 1.0)) {
            int k = spoke(dx, dz, 10, 0);
            int y = walk + 4 + (k * 3) % 6;
            if (y < ceil - 2) {
                String[] things = {"minecraft:bookshelf", "minecraft:crafting_table", "minecraft:oak_planks", "minecraft:white_wool", "minecraft:jack_o_lantern",
                        "minecraft:bookshelf", "minecraft:red_wool", "minecraft:oak_stairs[half=top]", "minecraft:note_block", "minecraft:jack_o_lantern"};
                c.set(bx, y, bz, s(c.lightless && things[k].endsWith("lantern") ? "minecraft:carved_pumpkin" : things[k]));
                if (k % 3 == 0) c.set(bx, y + 1, bz, c.lightless ? s("minecraft:oak_slab[type=bottom]") : s("minecraft:lantern[hanging=false]"));
                return true;
            }
        }
        if (Hash.unit(r.seed, x * 13, z) < 0.04) c.set(bx, ceil, bz, s("minecraft:crying_obsidian"));
        // night lights left on: soul lanterns hanging at odd heights all round the room
        return rimLights(c, bx, bz, dx, dz, walk, ceil, R * 0.82, 10, s("minecraft:soul_lantern[hanging=true]"));
    }

    /**
     * The Sky Leviathan's sky: islands over a cloud sea (fall, and the updrafts throw you back up), clouds drifting below,
     * the vault painted as open sky so it can dive out of it.
     */
    static boolean sky(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        if (fo == -3) {
            if (Hash.noise(r.seed, x, z, 5) > 0.55) c.set(bx, walk, bz, GatesRegistry.CLOUD.get().defaultBlockState());
            return true;
        }
        double R = radius(r), d = Math.hypot(dx, dz);
        if (d > 4.2 && Hash.unit(r.seed, x, z) < 0.08) c.set(bx, walk, bz, s("minecraft:short_grass"));
        if (!c.lightless && d > R * 0.5 && d < R * 0.95 && Hash.unit(r.seed, x * 3, z) < 0.01) {
            c.set(bx, walk, bz, s("minecraft:birch_fence"));
            c.set(bx, walk + 1, bz, s("minecraft:lantern[hanging=false]"));
            return true;
        }
        // floating stones high in the sky
        if (Hash.unit(r.seed, x * 11, z * 7) < 0.012 && ceil - walk > 12 && !inSweep(r, dx, dz)) {
            c.set(bx, walk + 9 + (int) (Hash.unit(r.seed, z, x) * 5), bz, s("minecraft:mossy_cobblestone"));
            return true;
        }
        return false;
    }

    /**
     * The Hollow Herald's void: six monoliths of obsidian (cover from the Void Bolts), an event horizon of void glass in
     * the floor round the unlit shrine where it waits, black candles, and stars for a ceiling.
     */
    static boolean abyss(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double R = radius(r), d = Math.hypot(dx, dz);
        if (ring(dx, dz, R * 0.4, 0.8)) c.set(bx, walk - 1, bz, GatesRegistry.VOID_GLASS.get().defaultBlockState());
        if (d > R * 0.8 && Hash.noise(r.seed, x, z, 3) < 0.5) c.set(bx, walk - 1, bz, s("minecraft:sculk"));
        if (ringPoint(dx, dz, cover(r, 0.6, 1.0), 6, 0.5, 1.0) && free(r, x, z)) {
            int k = spoke(dx, dz, 6, 0.5);
            int h = 8 + k % 3;
            for (int y = walk; y < Math.min(ceil - 1, walk + h); y++) c.set(bx, y, bz, Hash.unit(r.seed, x + y * 3, z) < 0.18 ? s("minecraft:crying_obsidian") : s("minecraft:obsidian"));
            return true;
        }
        double rc = cover(r, 0.25, 0.5);
        if (!c.lightless && ring(dx, dz, rc, 0.5) && ringPoint(dx, dz, rc, 12, 0, 0.5) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:black_candle[candles=4,lit=true]"));
            return true;
        }
        c.set(bx, ceil, bz, GatesRegistry.VOID_GLASS.get().defaultBlockState());
        // a few soul lanterns on long chains: just enough to see the monoliths' edges
        return rimLights(c, bx, bz, dx, dz, walk, ceil, R * 0.85, 6, s("minecraft:soul_lantern[hanging=true]"));
    }
}
