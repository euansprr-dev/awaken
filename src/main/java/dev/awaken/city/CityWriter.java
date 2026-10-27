package dev.awaken.city;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
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
import org.jspecify.annotations.Nullable;

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
            n += fills(new SectionColumn(chunk, held), buf, streetY, minY);
        } finally {
            for (LevelChunkSection s : held) if (s != null) s.release();
        }
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
        return n;
    }

    /**
     * Trees grown by a neighbouring chunk can spill leaves and branches into a chunk the city already painted. Each
     * city chunk sweeps its neighbours' columns that the city clears and removes vegetation the city never places
     * (non-persistent leaves, unstripped logs and the bark limbs of the lands' trees, vines, bee nests, cocoa, mushroom
     * blocks). All of each neighbour: a tree reaches a dozen blocks past the chunk it grew in, and its limbs lie anywhere
     * in the next one. Then {@link #collectOrphans} takes what the cut left hanging past the city's edge.
     */
    static int sweepNeighbours(WorldGenLevel level, CityLayout layout, int chunkX, int chunkZ) {
        int removed = 0;
        int y0 = layout.streetY + 1, y1 = layout.streetY + CityBuffer.YMAX;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = chunkX + dx, nz = chunkZ + dz;
                if (!layout.touches(nx, nz)) continue;
                ChunkAccess n = level.getChunk(nx, nz, ChunkStatus.TERRAIN, false);
                if (n == null) continue;
                for (int y = y0; y <= y1; y++) {
                    int si = n.getSectionIndex(y);
                    if (si < 0 || si >= n.getSectionsCount()) continue;
                    LevelChunkSection sec = n.getSection(si);
                    if (sec.hasOnlyAir() || !sec.maybeHas(CityWriter::vegetation)) {
                        y = (SectionPos.blockToSectionCoord(y) << 4) + 15;
                        continue;
                    }
                    int sy = SectionPos.sectionRelative(y);
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
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

    /**
     * How far past the city's reach the limbs of a tree it cut can still hang. The painter and the sweep clear every tree
     * column out to the reach, trunk and all; a tree that grew just inside it keeps the limbs that reached out past, and
     * those hang in the air (leaves rot off them, bark does not). Wide enough for any tree the lands grow.
     */
    static final int RING = 24;

    /**
     * Removes the natural trees' pieces that hang in the air round this chunk, wherever the city's cut can have made them:
     * groups of logs and leaves (joined face, edge or corner) none of whose blocks stands on solid ground. A group that
     * touches the edge of what is loaded is left (something unseen may hold it up). Only chunks near the city's edge
     * have anything to do. Returns blocks removed.
     */
    static int collectOrphans(WorldGenLevel level, CityLayout layout, int chunkX, int chunkZ) {
        long cx = layout.cx, cz = layout.cz, lo = layout.reach, hi = layout.reach + RING;
        long x0 = (long) (chunkX - 1) << 4, x1 = ((long) (chunkX + 2) << 4) - 1, z0 = (long) (chunkZ - 1) << 4, z1 = ((long) (chunkZ + 2) << 4) - 1;
        long nearX = Math.max(Math.max(x0 - cx, cx - x1), 0), nearZ = Math.max(Math.max(z0 - cz, cz - z1), 0);
        long farX = Math.max(Math.abs(x0 - cx), Math.abs(x1 - cx)), farZ = Math.max(Math.abs(z0 - cz), Math.abs(z1 - cz));
        // all inside the cleared columns, or all out of the ring's reach: nothing of ours to find
        if (farX * farX + farZ * farZ <= lo * lo || nearX * nearX + nearZ * nearZ > hi * hi) return 0;
        return collectOrphansAt(level, chunkX, chunkZ);
    }

    /** The orphan pass round one chunk, wherever a cut can have left trees hanging (the ring, or the trail's corridor). */
    static int collectOrphansAt(WorldGenLevel level, int chunkX, int chunkZ) {
        Long2ByteOpenHashMap blocks = new Long2ByteOpenHashMap();
        LongOpenHashSet loaded = new LongOpenHashSet();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = chunkX + dx, nz = chunkZ + dz;
                ChunkAccess c = dx == 0 && dz == 0 ? level.getChunk(nx, nz) : level.getChunk(nx, nz, ChunkStatus.TERRAIN, false);
                if (c == null) continue;
                loaded.add(ChunkPos.pack(nx, nz));
                CityTrees.read(c, CityWriter::vegetation, blocks, null);
            }
        }
        if (blocks.isEmpty()) return 0;
        LongArrayList doomed = new LongArrayList();
        CityTrees.groups(blocks, loaded::contains, (cells, grounded, clipped) -> {
            if (grounded || clipped) return;
            boolean tree = false;
            for (int i = 0; i < cells.size() && !tree; i++) tree = (blocks.get(cells.getLong(i)) & (CityTrees.WOOD | CityTrees.LEAF)) != 0;
            if (tree) doomed.addAll(cells);
        });
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < doomed.size(); i++) {
            long p = doomed.getLong(i);
            level.setBlock(pos.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return doomed.size();
    }

    /** The columns the city clears above its ground: inside the wall, its apron and (in a carved basin) the fields out to its reach. */
    static boolean owned(CityLayout layout, int wx, int wz) {
        int px = wx - layout.cx, pz = wz - layout.cz;
        if (layout.plan.kind(px, pz) != CityPlan.VOID) return true;
        long r2 = (long) px * px + (long) pz * pz;
        int out = layout.carved ? layout.reach : layout.plan.fieldsFrom;
        return r2 <= (long) out * out;
    }

    /** What trees and wild growth leave behind; the city never places any of it. */
    static boolean vegetation(BlockState s) {
        if (s.isAir()) return false;
        Block b = s.getBlock();
        if (b instanceof LeavesBlock) return !s.getValue(LeavesBlock.PERSISTENT);
        if (s.is(BlockTags.LOGS)) {
            net.minecraft.resources.Identifier key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
            String path = key.getPath();
            if (path.startsWith("stripped_")) return false;
            // the city's own trunks are vanilla bark ("oak_wood") and its timber is stripped; the lands' trees put bark on
            // their limbs too ("elder_wood"), and those are as much the trees' as their logs
            return !key.getNamespace().equals("minecraft") || !(path.endsWith("_wood") || path.endsWith("_hyphae"));
        }
        // any hanging moss: the pale garden's and the Lumen Forest's vines (left behind, they hung in the air over the
        // trail and the ring once the canopy above them was cleared)
        return b == Blocks.VINE || b == Blocks.BEE_NEST || b == Blocks.COCOA || b == Blocks.BROWN_MUSHROOM_BLOCK
                || b == Blocks.RED_MUSHROOM_BLOCK || b == Blocks.MUSHROOM_STEM || b == Blocks.MANGROVE_ROOTS
                || b instanceof net.minecraft.world.level.block.HangingMossBlock || b == Blocks.SNOW;
    }

    /** Dev repaint of a live chunk (its plates and banks too). Returns blocks changed. */
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
        return n + fills(new LevelColumn(level, chunk), buf, streetY, chunk.getMinY());
    }

    // ---- fills: the plate under what the city paints, and banks ---------------------------------------------------------

    /** How thick a crust over open air may be and still be passed (a beard's slab under a raised road): thicker is the ground. */
    static final int CRUST = 3;

    /** One column of the chunk being written, for the fills. */
    abstract static class Column {
        /** In the chunk (0..15), and in plan coordinates (the rock's). */
        int x, z, px, pz;

        abstract BlockState get(int y);

        abstract void set(int y, BlockState s);

        Column at(int x, int z, int px, int pz) {
            this.x = x;
            this.z = z;
            this.px = px;
            this.pz = pz;
            return this;
        }
    }

    /** The plates and the banks of a painted buffer (one chunk). Returns blocks written. */
    static int fills(Column c, CityBuffer buf, int streetY, int minY) {
        int n = 0;
        for (int lz = 0; lz < buf.d; lz++) {
            for (int lx = 0; lx < buf.w; lx++) {
                c.at(lx, lz, buf.px0 + lx, buf.pz0 + lz);
                // the plate: below the lowest painted row down to the ground (fills valleys and lakes where no basin was
                // carved; in a carved basin the first block down is already rock)
                if (buf.plate[lz * buf.w + lx]) {
                    int low = CityBuffer.YMIN;
                    while (low <= CityBuffer.YMAX && buf.cells[buf.index(lx, low, lz)] == null) low++;
                    if (low <= CityBuffer.YMAX) n += fill(c, streetY + low - 1, minY, true, null, buf.rock);
                }
                SettlementPaint.Mound m = buf.mounds[lz * buf.w + lx];
                if (m != null) n += fill(c, streetY + m.ry(), minY, false, m, buf.rock);
            }
        }
        return n;
    }

    /**
     * Carries a fill down from {@code y0} to the ground, at most {@link #MAX_FILL} blocks: open cells (air, plants, the
     * lands' trees; water too when {@code wet}) take the fill, a crust up to {@link #CRUST} thick with open air under it is
     * passed (its blocks stay), and anything thicker is the ground. A dry fill (a bank) that would come down on water lays
     * nothing, so a bank never dams a stream. A bank's column takes its top and soil, then the rock; one only a block or two
     * high takes its toe. Returns blocks written.
     */
    static int fill(Column c, int y0, int minY, boolean wet, SettlementPaint.@Nullable Mound m, SettlementPaint.@Nullable Rock rock) {
        int bottom = Math.max(minY, y0 - MAX_FILL + 1), y = y0;
        while (y >= bottom) {
            BlockState s = c.get(y);
            if (open(s, wet)) {
                y--;
                continue;
            }
            if (!wet && !s.getFluidState().isEmpty()) return 0;
            int t = 1;
            while (t <= CRUST && y - t >= bottom && !open(c.get(y - t), wet)) t++;
            if (t > CRUST || y - t < bottom) break;
            y -= t;
        }
        int n = 0;
        for (int yy = y0; yy > y; yy--) {
            if (!open(c.get(yy), wet)) continue;
            int depth = y0 - yy;
            BlockState s = m != null && depth == 0 && m.top() != null ? m.top()
                    : m != null && depth >= 1 && depth <= m.soilDepth() && m.soil() != null ? m.soil()
                    : rock != null ? rock.at(c.px, yy, c.pz) : CityBlocks.STONE;
            c.set(yy, s);
            n++;
        }
        if (m != null && m.toe() != null && n > 0 && y0 - y <= 2) c.set(y0, m.toe());
        return n;
    }

    /** What a fill may take: air, plants, the lands' trees and snow; fluids only for a wet fill. */
    static boolean open(BlockState s, boolean wet) {
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return wet;
        return vegetation(s) || s.canBeReplaced();
    }

    /** The proto chunk's sections. */
    private static final class SectionColumn extends Column {
        final ChunkAccess chunk;
        final LevelChunkSection[] held;

        SectionColumn(ChunkAccess chunk, LevelChunkSection[] held) {
            this.chunk = chunk;
            this.held = held;
        }

        @Override
        BlockState get(int y) {
            return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x, SectionPos.sectionRelative(y), z);
        }

        @Override
        void set(int y, BlockState s) {
            int si = chunk.getSectionIndex(y);
            LevelChunkSection sec = chunk.getSection(si);
            if (held[si] == null) {
                sec.acquire();
                held[si] = sec;
            }
            sec.setBlockState(x, SectionPos.sectionRelative(y), z, s, false);
        }
    }

    /** A live chunk (dev repaints and tests), block by block. */
    private static final class LevelColumn extends Column {
        final ServerLevel level;
        final int bx, bz;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        LevelColumn(ServerLevel level, LevelChunk chunk) {
            this.level = level;
            bx = chunk.getPos().getMinBlockX();
            bz = chunk.getPos().getMinBlockZ();
        }

        @Override
        BlockState get(int y) {
            return level.getBlockState(pos.set(bx + x, y, bz + z));
        }

        @Override
        void set(int y, BlockState s) {
            level.setBlock(pos.set(bx + x, y, bz + z), s, FLAGS);
        }
    }
}
