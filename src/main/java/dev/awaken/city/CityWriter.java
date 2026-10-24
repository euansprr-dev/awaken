package dev.awaken.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Commits a painted buffer to the world. Worldgen writes straight into the proto chunk's sections (never setBlock:
 * G0 measured it 2-14x slower), drops block entities left in cells the city replaces (bee nests in cleared trees),
 * carries the plate down to rock where terrain didn't flatten the ground, and primes the heightmaps once.
 * A dev repaint writes a live chunk with setBlock (no neighbour or shape updates: the painter chose final states).
 */
final class CityWriter {
    private CityWriter() {}

    static final int MAX_FILL = 48;
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Worldgen: the buffer (one chunk) into the proto chunk's sections. Returns blocks written. */
    static int toProto(ChunkAccess chunk, CityBuffer buf, int streetY) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinY(), maxY = chunk.getMaxY();
        int n = 0;
        for (BlockPos p : chunk.getBlockEntitiesPos()) {
            int ry = p.getY() - streetY;
            if (ry < CityBuffer.YMIN || ry > CityBuffer.YMAX) continue;
            if (buf.cells[buf.index(p.getX() & 15, ry, p.getZ() & 15)] != null) chunk.removeBlockEntity(p);
        }
        LevelChunkSection[] held = new LevelChunkSection[chunk.getSectionsCount()];
        try {
            for (int ry = CityBuffer.YMIN; ry <= CityBuffer.YMAX; ry++) {
                int y = streetY + ry;
                if (y < minY || y > maxY) continue;
                int si = chunk.getSectionIndex(y);
                LevelChunkSection sec = chunk.getSection(si);
                int sy = SectionPos.sectionRelative(y);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState s = buf.cells[buf.index(lx, ry, lz)];
                        if (s == null) continue;
                        if (s.isAir() && sec.hasOnlyAir()) continue;
                        if (held[si] == null) {
                            sec.acquire();
                            held[si] = sec;
                        }
                        sec.setBlockState(lx, sy, lz, s, false);
                        n++;
                        if (s.hasBlockEntity() && s.getBlock() instanceof EntityBlock eb) {
                            BlockEntity be = eb.newBlockEntity(new BlockPos(bx + lx, y, bz + lz), s);
                            if (be != null) chunk.setBlockEntity(be);
                        }
                    }
                }
            }
            // the plate: below the lowest painted row, stone down to the first solid block (fills valleys and lakes
            // where no basin was carved; in a carved basin the first block down is already rock)
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    if (!buf.plate[lz * 16 + lx]) continue;
                    int low = CityBuffer.YMIN;
                    while (low <= CityBuffer.YMAX && buf.cells[buf.index(lx, low, lz)] == null) low++;
                    for (int k = 1; k <= MAX_FILL; k++) {
                        int y = streetY + low - k;
                        if (y < minY) break;
                        int si = chunk.getSectionIndex(y);
                        LevelChunkSection sec = chunk.getSection(si);
                        int sy = SectionPos.sectionRelative(y);
                        BlockState below = sec.getBlockState(lx, sy, lz);
                        if (!below.isAir() && below.getFluidState().isEmpty()) break;
                        if (held[si] == null) {
                            sec.acquire();
                            held[si] = sec;
                        }
                        sec.setBlockState(lx, sy, lz, CityBlocks.STONE, false);
                        n++;
                    }
                }
            }
        } finally {
            for (LevelChunkSection s : held) if (s != null) s.release();
        }
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
        return n;
    }

    /**
     * Trees grown by a neighbouring chunk can spill leaves and branches into a chunk the city already painted. Each
     * city chunk sweeps the strip of its neighbours that its own trees could reach and removes vegetation the city
     * never places (non-persistent leaves, unstripped logs, vines, bee nests, cocoa, mushroom blocks).
     */
    static int sweepNeighbours(WorldGenLevel level, CityLayout layout, int chunkX, int chunkZ) {
        int removed = 0;
        int y0 = layout.streetY + 1, y1 = layout.streetY + 44;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = chunkX + dx, nz = chunkZ + dz;
                if (!layout.touches(nx, nz)) continue;
                ChunkAccess n = level.getChunk(nx, nz, ChunkStatus.TERRAIN, false);
                if (n == null) continue;
                // only the strip of the neighbour within 8 blocks of this chunk
                int lx0 = dx < 0 ? 8 : 0, lx1 = dx > 0 ? 7 : 15, lz0 = dz < 0 ? 8 : 0, lz1 = dz > 0 ? 7 : 15;
                for (int y = y0; y <= y1; y++) {
                    int si = n.getSectionIndex(y);
                    if (si < 0 || si >= n.getSectionsCount()) continue;
                    LevelChunkSection sec = n.getSection(si);
                    if (sec.hasOnlyAir() || !sec.maybeHas(CityWriter::vegetation)) {
                        y = (SectionPos.blockToSectionCoord(y) << 4) + 15;
                        continue;
                    }
                    int sy = SectionPos.sectionRelative(y);
                    for (int lz = lz0; lz <= lz1; lz++) {
                        for (int lx = lx0; lx <= lx1; lx++) {
                            int wx = (nx << 4) + lx, wz = (nz << 4) + lz;
                            BlockState s = sec.getBlockState(lx, sy, lz);
                            if (!vegetation(s)) continue;
                            if (!owned(layout, wx, wz)) continue;
                            level.setBlock(pos.set(wx, y, wz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            removed++;
                        }
                    }
                }
            }
        }
        return removed;
    }

    /** The columns the city clears above its ground: inside the wall, its apron and (in a carved basin) the fields. */
    static boolean owned(CityLayout layout, int wx, int wz) {
        int px = wx - layout.cx, pz = wz - layout.cz;
        if (layout.plan.kind(px, pz) != CityPlan.VOID) return true;
        long r2 = (long) px * px + (long) pz * pz;
        int out = layout.carved ? layout.plan.fieldsTo : layout.plan.fieldsFrom;
        return r2 <= (long) out * out;
    }

    /** What trees and wild growth leave behind; the city never places any of it. */
    static boolean vegetation(BlockState s) {
        if (s.isAir()) return false;
        Block b = s.getBlock();
        if (b instanceof LeavesBlock) return !s.getValue(LeavesBlock.PERSISTENT);
        if (s.is(BlockTags.LOGS)) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
            return !path.startsWith("stripped_") && !path.endsWith("_wood") && !path.endsWith("_hyphae");
        }
        return b == Blocks.VINE || b == Blocks.BEE_NEST || b == Blocks.COCOA || b == Blocks.BROWN_MUSHROOM_BLOCK
                || b == Blocks.RED_MUSHROOM_BLOCK || b == Blocks.MUSHROOM_STEM || b == Blocks.MANGROVE_ROOTS
                || b == Blocks.PALE_HANGING_MOSS || b == Blocks.SNOW;
    }

    /** Dev repaint of a live chunk. Returns blocks changed. */
    static int toLevel(ServerLevel level, LevelChunk chunk, CityBuffer buf, int streetY) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int ry = CityBuffer.YMIN; ry <= CityBuffer.YMAX; ry++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    BlockState s = buf.cells[buf.index(lx, ry, lz)];
                    if (s == null) continue;
                    pos.set(bx + lx, streetY + ry, bz + lz);
                    if (chunk.getBlockState(pos) == s) continue;
                    level.setBlock(pos, s, FLAGS);
                    n++;
                }
            }
        }
        return n;
    }
}
