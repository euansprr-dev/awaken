package dev.awaken.village.plan;

import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint.Canvas;
import dev.awaken.city.VillageStyles;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.village.plan.VillageBlocks.*;

/**
 * A town's walls (SPEC §28.9): a curtain of the land's stone following the ground round the town, eight high with a walk
 * along its top and crenels on its outer edge; towers about every thirty blocks, twelve high with loopholes, a door on the
 * town side and a ladder to their tops, crowned with merlons or a timber roof by the land; at each gate two towers
 * flanking an arched passage with its portcullis raised, lanterns hung under the arch and the town's banners. The bell
 * tower on the market square stands taller than the walls. Pure and clipped to the canvas, like the rest of the painter.
 */
final class TownPainter {
    private TownPainter() {}

    /** The curtain's height over its ground (its walk), and a tower's. */
    static final int WALL_H = 8, TOWER_H = 12;

    /** One wall cell: its foundation, body, walk and (on the outer edge) its crenel. */
    static void wall(VillagePlan p, Canvas c, int x, int z, int g) {
        if (inTower(p, x, z)) {
            for (int ry = g - 3; ry <= g; ry++) c.set(x, ry, z, stone(p, c, x, ry, z));
            c.plate(x, z);
            return;
        }
        for (int ry = g - 3; ry <= g; ry++) c.set(x, ry, z, COBBLE);
        c.plate(x, z);
        c.set(x, g + 1, z, plinth(p));
        for (int ry = g + 2; ry <= g + WALL_H; ry++) c.set(x, ry, z, stone(p, c, x, ry, z));
        c.column(x, g + WALL_H + 1, g + WALL_H + 5, z, AIR);
        if (outerEdge(p, x, z)) {
            // merlons two cells in three along the line (by the cell's angle round the town)
            double deg = (Math.toDegrees(Math.atan2(z, x)) + 360) % 360;
            int step = (int) Math.floor(deg * Math.toRadians(1) * ringAt(p, x, z) / 1.0);
            if (Math.floorMod(step, 3) != 0) c.set(x, g + WALL_H + 1, z, stone(p, c, x, g + WALL_H + 1, z));
            else c.set(x, g + WALL_H + 1, z, cap(p));
        }
    }

    /** Whether a wall cell is on the side away from the town (the parapet's). */
    static boolean outerEdge(VillagePlan p, int x, int z) {
        double d = Math.hypot(x, z), w = ringAt(p, x, z);
        if (d < w + 0.4) return false;
        // the next cell outward isn't wall
        double ux = x / d, uz = z / d;
        return p.kind((int) Math.round(x + ux), (int) Math.round(z + uz)) != CityPlan.WALL;
    }

    static float ringAt(VillagePlan p, double x, double z) {
        float[] ring = p.wallRing;
        if (ring == null) return 0;
        double deg = (Math.toDegrees(Math.atan2(z, x)) + 360) % 360;
        int a = (int) Math.floor(deg) % 360, b = (a + 1) % 360;
        float f = (float) (deg - Math.floor(deg));
        return ring[a] * (1 - f) + ring[b] * f;
    }

    static boolean inTower(VillagePlan p, int x, int z) {
        for (int[] t : p.towers) if (Math.abs(x - t[0]) <= t[3] && Math.abs(z - t[1]) <= t[3]) return true;
        return false;
    }

    // ---- materials ------------------------------------------------------------------------------------------------------

    static BlockState stone(VillagePlan p, Canvas c, int x, int ry, int z) {
        int roll = c.pct(x * 7 + ry, z, 811);
        return switch (p.family) {
            case VillageStyles.DESERT -> roll < 55 ? SANDSTONE : roll < 85 ? CUT_SANDSTONE : SMOOTH_SANDSTONE;
            case VillageStyles.SAVANNA -> roll < 60 ? MUD_BRICKS : roll < 85 ? PACKED_MUD : SANDSTONE;
            case VillageStyles.MIRE, VillageStyles.JUNGLE -> roll < 55 ? MUD_BRICKS : roll < 80 ? MOSSY_COBBLE : MOSSY_STONE_BRICKS;
            default -> roll < 52 ? STONE_BRICKS : roll < 66 ? MOSSY_STONE_BRICKS : roll < 78 ? CRACKED_STONE_BRICKS : roll < 92 ? COBBLE : MOSSY_COBBLE;
        };
    }

    static BlockState plinth(VillagePlan p) {
        return switch (p.family) {
            case VillageStyles.DESERT -> CHISELED_SANDSTONE;
            case VillageStyles.SAVANNA, VillageStyles.MIRE, VillageStyles.JUNGLE -> COBBLE;
            default -> POLISHED_ANDESITE;
        };
    }

    static BlockState cap(VillagePlan p) {
        return switch (p.family) {
            case VillageStyles.DESERT -> SANDSTONE_SLAB;
            case VillageStyles.SAVANNA, VillageStyles.MIRE, VillageStyles.JUNGLE -> MUD_BRICK_SLAB;
            default -> STONE_SLAB;
        };
    }

    /** Roofed towers in the timbered lands, crenellated ones in the stone and sand lands. */
    static boolean roofed(VillagePlan p) {
        return switch (p.family) {
            case VillageStyles.TEMPERATE, VillageStyles.ELDER, VillageStyles.CHERRY, VillageStyles.COAST, VillageStyles.FROST -> true;
            default -> false;
        };
    }

    // ---- towers ---------------------------------------------------------------------------------------------------------

    /** A tower: a hollow stone shaft, a door to the town, loopholes, a ladder, a floor at the walk, its crown. */
    static void tower(VillagePlan p, Canvas c, int[] t) {
        int tx = t[0], tz = t[1], g = t[2], h = t[3];
        if (Math.abs(tx - c.x0() - 8) > 12 + h || Math.abs(tz - c.z0() - 8) > 12 + h) return;
        VillageBlocks.Wood w = VillagePainter.wood(p.family);
        // the side toward the town (its door) and the ladder's wall opposite
        int in = Math.abs(tx) >= Math.abs(tz) ? (tx > 0 ? 3 : 1) : (tz > 0 ? 0 : 2);
        int top = g + TOWER_H;
        for (int dz = -h; dz <= h; dz++) {
            for (int dx = -h; dx <= h; dx++) {
                int x = tx + dx, z = tz + dz;
                boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                boolean corner = Math.abs(dx) == h && Math.abs(dz) == h;
                c.set(x, g + 1, z, edge ? plinth(p) : w.planks());
                for (int ry = g + 2; ry <= top; ry++) c.set(x, ry, z, edge ? stone(p, c, x, ry, z) : AIR);
                c.set(x, g + WALL_H, z, edge ? stone(p, c, x, g + WALL_H, z) : w.planks());
                c.set(x, top, z, edge ? stone(p, c, x, top, z) : w.planks());
                // loopholes: the middle of each face, at the room's eye and over the walk
                boolean mid = Math.abs(dx) == h && dz == 0 || Math.abs(dz) == h && dx == 0;
                if (mid && !corner) {
                    c.set(x, g + 3, z, IRON_BARS);
                    c.set(x, g + WALL_H + 2, z, IRON_BARS);
                }
                if (roofed(p)) continue;
                // a crenellated crown
                if (edge) c.set(x, top + 1, z, corner || (dx + dz) % 2 == 0 ? stone(p, c, x, top + 1, z) : cap(p));
                else c.column(x, top + 1, top + 3, z, AIR);
            }
        }
        // the door on the town side, and the ladder up the inside of the far wall to the floor at the walk and the top
        int sx = VillagePlanner.Work.STEP_X[in], sz = VillagePlanner.Work.STEP_Z[in];
        int dx = tx + sx * h, dz = tz + sz * h;
        c.set(dx, g + 2, dz, AIR);
        c.set(dx, g + 3, dz, AIR);
        int lx = tx - sx * (h - 1), lz = tz - sz * (h - 1);
        c.column(lx, g + 2, top, lz, LADDER[in]);
        // the walk meets the tower: an opening on each side along the wall at the walk's height
        for (int s = 0; s < 4; s++) {
            if (s == in || s == ((in + 2) & 3)) continue;
            int ox = tx + VillagePlanner.Work.STEP_X[s] * h, oz = tz + VillagePlanner.Work.STEP_Z[s] * h;
            c.set(ox, g + WALL_H + 1, oz, AIR);
            c.set(ox, g + WALL_H + 2, oz, AIR);
        }
        if (roofed(p)) roof(c, tx, tz, top, h + 1, w);
        else c.set(lx, top + 1, lz, AIR);
    }

    /** A pyramid roof of stairs over a square (half-width h), its peak capped. */
    static void roof(Canvas c, int cx, int cz, int base, int h, VillageBlocks.Wood w) {
        for (int k = 0; k <= h; k++) {
            int r = h - k, y = base + 1 + k;
            if (r == 0) {
                c.set(cx, y, cz, w.planks());
                c.set(cx, y + 1, cz, LIGHTNING_ROD);
                break;
            }
            for (int dz = -r; dz <= r; dz++)
                for (int dx = -r; dx <= r; dx++) {
                    boolean ring = Math.abs(dx) == r || Math.abs(dz) == r;
                    if (!ring) {
                        c.set(cx + dx, y, cz + dz, w.planks());
                        continue;
                    }
                    int face = dz == -r ? 2 : dz == r ? 0 : dx == -r ? 1 : 3;
                    c.set(cx + dx, y, cz + dz, w.stair()[face]);
                }
        }
    }

    // ---- gates ----------------------------------------------------------------------------------------------------------

    /**
     * A gate: over the street where it passes the wall's line, an arch of stone (upside-down stairs at its spring), the
     * curtain carried over it to the walk, the portcullis raised in its slot, lanterns hung under the arch, a banner on
     * each flanking tower's outer face.
     */
    static void gate(VillagePlan p, Canvas c, int[] gt) {
        int gx = gt[0], gz = gt[1], g = gt[2], side = gt[3];
        if (Math.abs(gx - c.x0() - 8) > 20 || Math.abs(gz - c.z0() - 8) > 20) return;
        int ox = VillagePlanner.Work.STEP_X[side], oz = VillagePlanner.Work.STEP_Z[side];
        int lx = -oz, lz = ox; // across the passage
        BlockState banner = BANNERS[(int) Math.floorMod(p.seed >>> 9, (long) BANNERS.length)];
        for (int depth = -2; depth <= 2; depth++) {
            for (int across = -3; across <= 3; across++) {
                int x = gx + ox * depth + lx * across, z = gz + oz * depth + lz * across;
                byte k = p.kind(x, z);
                boolean pass = Math.abs(across) <= 1;
                if (!pass) {
                    // the passage's sides: the curtain closes up to the towers
                    if (k != CityPlan.WALL && Math.abs(across) <= 2 && Math.abs(depth) <= 1) {
                        for (int ry = g + 1; ry <= g + WALL_H; ry++) c.set(x, ry, z, stone(p, c, x, ry, z));
                    }
                    continue;
                }
                if (Math.abs(depth) > 1) continue;
                // over the passage: the arch springs at four, the curtain closes over it to the walk
                c.column(x, g + 1, g + 3, z, AIR);
                if (across == 0) c.set(x, g + 4, z, AIR);
                else c.set(x, g + 4, z, archStair(side, across > 0 ? 1 : -1, lx, lz));
                for (int ry = g + 5; ry <= g + WALL_H; ry++) c.set(x, ry, z, stone(p, c, x, ry, z));
                if (depth == 0) c.set(x, g + 4, z, IRON_BARS); // the portcullis, raised into its slot
                if (Math.abs(depth) == 1 && across == 0) c.set(x, g + 4, z, LANTERN_HANGING);
                c.column(x, g + WALL_H + 1, g + WALL_H + 4, z, AIR);
                if (depth == 1) c.set(x, g + WALL_H + 1, z, Math.abs(across) == 1 ? stone(p, c, x, g + WALL_H + 1, z) : cap(p));
            }
        }
        // banners on the towers' outer faces, either side of the gate
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int bx = gx + lx * sgn * 5 + ox * 3, bz = gz + lz * sgn * 5 + oz * 3;
            if (p.kind(bx - ox, bz - oz) == CityPlan.WALL) c.set(bx, g + 6, bz, banner == null ? AIR : wallBanner(banner, side));
        }
    }

    /** The arch's springer: an upside-down stair whose high side faces into the passage. */
    static BlockState archStair(int side, int sgn, int lx, int lz) {
        // the stair faces toward the passage's middle
        int face = lx != 0 ? (lx * sgn > 0 ? 1 : 3) : (lz * sgn > 0 ? 2 : 0);
        return STONE_BRICK_STAIR_TOP[face];
    }

    static BlockState wallBanner(BlockState banner, int side) {
        return banner.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, DIRS[side]);
    }

    static final net.minecraft.core.Direction[] DIRS = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
    static final BlockState[] BANNERS = {of("minecraft:red_wall_banner"), of("minecraft:blue_wall_banner"), of("minecraft:green_wall_banner"),
            of("minecraft:yellow_wall_banner"), of("minecraft:white_wall_banner"), of("minecraft:black_wall_banner")};
    static final BlockState[] STONE_BRICK_STAIR_TOP = dirs("minecraft:stone_brick_stairs[facing=%s,half=top,shape=straight]");
    static final BlockState IRON_BARS = of("minecraft:iron_bars");
    static final BlockState LANTERN_HANGING = of("minecraft:lantern[hanging=true]");
    static final BlockState CHISELED_SANDSTONE = of("minecraft:chiseled_sandstone"), CHISELED_STONE = of("minecraft:chiseled_stone_bricks");

    // ---- the bell tower ---------------------------------------------------------------------------------------------------

    /**
     * The town's bell tower on the market square: a five-square shaft of dressed stone with a door to the square and a
     * ladder up its inside, an open belfry on corner posts where the bell hangs, a stair-pyramid roof and its rod.
     */
    static void bellTower(VillagePlan p, Canvas c, VillagePlan.Bell b) {
        VillageBlocks.Wood w = VillagePainter.wood(p.family);
        int g = b.lift(), cx = b.bx(), cz = b.bz();
        int belfry = g + b.bry() - 3;
        boolean desert = p.family == VillageStyles.DESERT;
        for (int z = b.z0(); z <= b.z1(); z++) {
            for (int x = b.x0(); x <= b.x1(); x++) {
                boolean edge = x == b.x0() || x == b.x1() || z == b.z0() || z == b.z1();
                boolean corner = (x == b.x0() || x == b.x1()) && (z == b.z0() || z == b.z1());
                c.set(x, g, z, edge ? plinth(p) : STONE_BRICKS);
                for (int ry = g + 1; ry < belfry; ry++) {
                    BlockState s = corner ? (desert ? CUT_SANDSTONE : POLISHED_ANDESITE) : desert ? SANDSTONE : ry % 4 == 0 ? CHISELED_STONE : STONE_BRICKS;
                    c.set(x, ry, z, edge ? s : AIR);
                }
                c.set(x, belfry, z, edge ? cap(p) : w.planks());
                for (int ry = belfry + 1; ry <= belfry + 4; ry++) c.set(x, ry, z, corner ? w.post() : AIR);
                c.set(x, belfry + 5, z, edge ? w.beamX() : w.planks());
            }
        }
        // windows up the shaft: a slit in each face's middle every four
        for (int ry = g + 5; ry < belfry - 1; ry += 4) {
            c.set(cx, ry, b.z0(), IRON_BARS);
            c.set(cx, ry, b.z1(), IRON_BARS);
            c.set(b.x0(), ry, cz, IRON_BARS);
            c.set(b.x1(), ry, cz, IRON_BARS);
        }
        // the door on the ringer's side, the ladder opposite it
        int side = b.ringerX() > b.x1() ? 1 : b.ringerX() < b.x0() ? 3 : b.ringerZ() > b.z1() ? 2 : 0;
        int sx = VillagePlanner.Work.STEP_X[side], sz = VillagePlanner.Work.STEP_Z[side];
        int dx = cx + sx * 2, dz = cz + sz * 2;
        c.set(dx, g + 1, dz, AIR);
        c.set(dx, g + 2, dz, AIR);
        c.column(cx - sx, g + 1, belfry, cz - sz, LADDER[side]);
        c.set(cx - sx, belfry, cz - sz, LADDER[side]);
        c.set(cx, belfry + 4, cz, CHAIN);
        c.set(cx, g + b.bry(), cz, BELL_CEILING_X);
        roof(c, cx, cz, belfry + 5, 3, w);
    }
}
