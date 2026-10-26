package dev.awaken.city;

import dev.awaken.city.CityPainter.Ctx;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityCommons.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The High Gate road past the painter's reach (M4, the Commons step; SPEC §16.3): on over the natural ground of the
 * basin's rim to the Wayfarers' fire (450 north of the centre, a little east). The same carriageway, verges, lamps and
 * milestones as inside the reach (no ditch, no hedges: the land is wild here), laid on the ground as it lies, a plank
 * bridge with rails where it crosses water. It ends 26 short of the fire at a fingerpost, and a trodden footpath bends
 * on to the camp (the camp itself is the Wayfarers'). Painted at worldgen from the chunk's own ground, so a chunk is its
 * own: nothing here reads a neighbour.
 */
final class CityTrail {
    private CityTrail() {}

    /** The road's end (plan z) and the footpath's (to x 4.5); the fire's nominal spot is 6, -450. */
    static final int END_Z = -424, PATH_END_Z = -440;
    static final int HALF = 8;

    static final BlockState SPRUCE_SLAB_TOP = of("minecraft:spruce_slab[type=top]");

    /** Whether the trail writes into this chunk (the north road's corridor past the reach). */
    static boolean touches(CityLayout l, int chunkX, int chunkZ) {
        if (!l.carved) return false;
        int x0 = (chunkX << 4) - l.cx, z0 = (chunkZ << 4) - l.cz;
        return x0 <= HALF && x0 + 15 >= -HALF && z0 <= -l.reach + 4 && z0 + 15 >= PATH_END_Z - 2;
    }

    /** Whether a chunk lies next to the trail (its trees may have grown into the trail's columns). */
    static boolean near(CityLayout l, int chunkX, int chunkZ) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) if (touches(l, chunkX + dx, chunkZ + dz)) return true;
        return false;
    }

    /** The columns the trail keeps clear above its ground: the road and its verges, the footpath, the fingerpost. */
    static boolean owns(CityLayout l, int wx, int wz) {
        int x = wx - l.cx, z = wz - l.cz;
        if (!l.carved || Math.sqrt((double) x * x + (double) z * z) <= l.reach) return false;
        if (z >= END_Z && z <= -l.reach + 8 && Math.abs(x + 0.5) <= VERGE + 1) return true;
        return z < END_Z && z >= PATH_END_Z && Math.abs(x + 0.5 - pathX(z)) <= 2.1 || post(x, z);
    }

    /**
     * After a chunk next to the trail has grown its trees (the city's feature is its last decoration): takes every tree's
     * logs and leaves out of the trail's columns in it and its neighbours, then whatever of those trees now hangs in the
     * air (the city's orphan pass). Returns blocks removed.
     */
    static int sweep(WorldGenLevel level, CityLayout l, int chunkX, int chunkZ) {
        int removed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = chunkX + dx, nz = chunkZ + dz;
                if (!touches(l, nx, nz)) continue;
                ChunkAccess n = dx == 0 && dz == 0 ? level.getChunk(nx, nz) : level.getChunk(nx, nz, ChunkStatus.TERRAIN, false);
                if (n == null) continue;
                for (int lx = 0; lx < 16; lx++)
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = (nx << 4) + lx, wz = (nz << 4) + lz;
                        if (!owns(l, wx, wz)) continue;
                        for (int y = l.streetY - 16; y <= l.streetY + 64; y++) {
                            if (!tree(n.getBlockState(m.set(wx, y, wz)))) continue;
                            level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            removed++;
                        }
                    }
            }
        }
        return removed + (removed > 0 ? CityWriter.collectOrphansAt(level, chunkX, chunkZ) : 0);
    }

    /** Any tree's wood or leaves (nothing of the trail's own is: in its columns all of it goes, vanilla bark too). */
    static boolean tree(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || CityWriter.vegetation(s);
    }

    /** Paints the trail's columns of one chunk into its buffer (the city's, already painted or reset), reading its ground. */
    static void paint(CityLayout l, ChunkAccess chunk, CityBuffer b, long seed) {
        Ctx c = new Ctx(l, seed);
        CityPlan p = l.plan;
        int[] r = p.roads.get(0); // the High Gate road, from the gate north
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = b.px0 + lx, z = b.pz0 + lz;
                if (Math.sqrt((double) x * x + (double) z * z) <= l.reach) continue;
                double off = x + 0.5 - r[0], along = r[1] - (z + 0.5);
                boolean road = z >= END_Z && Math.abs(off) <= VERGE;
                boolean foot = z < END_Z && z >= PATH_END_Z && Math.abs(x + 0.5 - pathX(z)) <= 1.1;
                if (!road && !foot && !post(x, z)) continue;
                int wx = chunk.getPos().getMinBlockX() + lx, wz = chunk.getPos().getMinBlockZ() + lz;
                int gy = ground(chunk, wx, wz, l.streetY, m);
                if (gy == Integer.MIN_VALUE) continue;
                boolean wet = gy < 0;
                int y = (wet ? -gy : gy) - l.streetY;
                if (y < CityBuffer.YMIN + 2 || y > CityBuffer.YMAX - 16) continue;
                if (post(x, z)) {
                    b.column(x, y + 1, y + 30, z, AIR);
                    b.set(x, y + 1, z, FINGERPOST[N][0]);
                    b.set(x, y + 2, z, FINGERPOST[N][1]);
                    continue;
                }
                if (foot) {
                    int h = c.pct(x, z, 720);
                    b.set(x, y, z, wet ? SPRUCE_PLANKS : h < 55 ? PATH : h < 70 ? COARSE_DIRT : GRASS);
                    b.column(x, y + 1, y + 30, z, AIR);
                    continue;
                }
                if (wet) {
                    bridge(b, x, y, z, off);
                    continue;
                }
                b.column(x, y + 1, y + 30, z, AIR); // a tree standing here goes, trunk and crown
                if (Math.abs(off) <= CARRIAGE) carriage(c, b, x, y, z, along, off);
                else verge(c, b, x, y, z, along, off);
                furnish(c, b, 0, x, y, z, along, off, false);
            }
        }
    }

    /** The footpath's centre from the road's end to the camp: bending from the road's line to 4.5 east. */
    static double pathX(int z) {
        double t = (END_Z - (z + 0.5)) / (END_Z - PATH_END_Z);
        return 0.5 + 4.0 * t * t * (3 - 2 * t);
    }

    /** The fingerpost at the road's end, on the west verge, its arm to the camp (north). */
    static boolean post(int x, int z) {
        return x == -6 && z == END_Z;
    }

    /** Over water: a plank deck one above the surface, rails at the kerbs. */
    static void bridge(CityBuffer b, int x, int y, int z, double off) {
        double a = Math.abs(off);
        if (a > CARRIAGE + 1) return;
        b.set(x, y + 1, z, SPRUCE_SLAB_TOP);
        b.column(x, y + 2, y + 6, z, AIR);
        if (a > CARRIAGE) b.set(x, y + 2, z, CityCommons.FENCE_RUN[1]);
    }

    /**
     * The column's ground (world y of its top solid block, under any tree or plant), or minus the water's surface y where
     * the top is water; MIN_VALUE when nothing is found near the street's level.
     */
    static int ground(ChunkAccess chunk, int wx, int wz, int streetY, BlockPos.MutableBlockPos m) {
        for (int y = streetY + 48; y >= streetY - 24; y--) {
            BlockState s = chunk.getBlockState(m.set(wx, y, wz));
            if (s.isAir()) continue;
            if (!s.getFluidState().isEmpty()) return -y;
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || !s.isSolid()) continue;
            return y;
        }
        return Integer.MIN_VALUE;
    }
}
