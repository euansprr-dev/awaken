package dev.awaken.lucidium.world;

import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Fragment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A dream compiled for worldgen: immutable pieces (islands, fragments, bridges...) indexed by chunk, plus the
 * positions the server needs while the dream runs (nodes, doors, signs, the arrival point). Built once off-thread
 * from a {@link DreamPlan}; read concurrently by worldgen threads.
 */
public final class DreamBlueprint {
    public final int cell;
    public final BlockPos center;
    public final DreamPlan plan;
    public final List<Node> nodes;
    public final List<Door> doors;
    public final Vec3 spawn;
    public final float spawnYaw;
    public final Map<DreamSign, Sign> signs;
    public final List<BlockState> manifestPalette;
    public final List<Fragment> spares;
    private final List<Piece> pieces;
    private final Map<Long, Piece[]> byChunk;

    /** A place in the dream. {@code min} is the fragment's min corner, {@code feet} the walking level at its centre. */
    public record Node(int index, String kind, String ref, String label, BlockPos feet, BlockPos min, Fragment fragment) {
        public AABB box() {
            return new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + fragment.sx, min.getY() + fragment.sy, min.getZ() + fragment.sz);
        }

        public Vec3 centre() {
            return new Vec3(feet.getX() + 0.5, feet.getY() + 2, feet.getZ() + 0.5);
        }
    }

    /** Walking into {@code trigger} moves you to {@code exit}. Special doors are the door of intention. */
    public record Door(AABB trigger, Vec3 exit, float exitYaw, boolean special) {}

    /** Where a dream sign lives: a position and, for the clock, the face's horizontal axis. */
    public record Sign(DreamSign sign, List<BlockPos> positions, int axisX, int axisZ) {}

    public DreamBlueprint(int cell, BlockPos center, DreamPlan plan, List<Piece> pieces, List<Node> nodes, List<Door> doors,
                          Vec3 spawn, float spawnYaw, Map<DreamSign, Sign> signs, List<BlockState> manifestPalette,
                          List<Fragment> spares) {
        this.cell = cell;
        this.center = center;
        this.plan = plan;
        this.pieces = List.copyOf(pieces);
        this.nodes = List.copyOf(nodes);
        this.doors = List.copyOf(doors);
        this.spawn = spawn;
        this.spawnYaw = spawnYaw;
        this.signs = Map.copyOf(signs);
        this.manifestPalette = List.copyOf(manifestPalette);
        this.spares = List.copyOf(spares);
        Map<Long, List<Piece>> index = new HashMap<>();
        for (Piece p : this.pieces) {
            BoundingBox b = p.box();
            for (int cx = SectionPos.blockToSectionCoord(b.minX()); cx <= SectionPos.blockToSectionCoord(b.maxX()); cx++)
                for (int cz = SectionPos.blockToSectionCoord(b.minZ()); cz <= SectionPos.blockToSectionCoord(b.maxZ()); cz++)
                    index.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(p);
        }
        Map<Long, Piece[]> frozen = new HashMap<>();
        index.forEach((k, v) -> frozen.put(k, v.toArray(Piece[]::new)));
        this.byChunk = Map.copyOf(frozen);
    }

    /** Writes everything that touches this chunk (worldgen thread). */
    public void write(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        Piece[] list = byChunk.get(ChunkPos.pack(pos.x(), pos.z()));
        if (list == null) return;
        ChunkWriter w = new ChunkWriter(chunk);
        for (Piece p : list) p.write(w);
    }

    /** The block this dream generates at a position (null for nothing). Used to find safe spots. */
    public BlockState blockAt(int x, int y, int z) {
        Piece[] list = byChunk.get(ChunkPos.pack(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)));
        if (list == null) return null;
        for (int i = list.length - 1; i >= 0; i--) {
            BlockState s = list[i].get(x, y, z);
            if (s != null) return s;
        }
        return null;
    }

    public int chunkCount() {
        return byChunk.size();
    }

    // ---- pieces --------------------------------------------------------------------------------------------------------

    public interface Piece {
        BoundingBox box();

        void write(ChunkWriter w);

        BlockState get(int x, int y, int z);
    }

    /** Writes into one proto chunk, clipped to it, keeping the worldgen heightmaps current. */
    public static final class ChunkWriter {
        private final ChunkAccess chunk;
        private final int minX, minZ, minY, maxY;
        private final Heightmap ocean, surface;

        ChunkWriter(ChunkAccess chunk) {
            this.chunk = chunk;
            this.minX = chunk.getPos().getMinBlockX();
            this.minZ = chunk.getPos().getMinBlockZ();
            this.minY = chunk.getMinY();
            this.maxY = chunk.getMaxY();
            this.ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            this.surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        }

        public int minX() { return minX; }
        public int minZ() { return minZ; }

        public void set(int x, int y, int z, BlockState state) {
            if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16 || y < minY || y > maxY || state == null) return;
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            int lx = x & 15, ly = y & 15, lz = z & 15;
            section.setBlockState(lx, ly, lz, state, false);
            ocean.update(lx, y, lz, state);
            surface.update(lx, y, lz, state);
        }
    }

    public record FragmentPiece(Fragment fragment, BlockPos min) implements Piece {
        @Override
        public BoundingBox box() {
            return new BoundingBox(min.getX(), min.getY(), min.getZ(), min.getX() + fragment.sx - 1, min.getY() + fragment.sy - 1, min.getZ() + fragment.sz - 1);
        }

        @Override
        public void write(ChunkWriter w) {
            int x0 = Math.max(min.getX(), w.minX()), x1 = Math.min(min.getX() + fragment.sx - 1, w.minX() + 15);
            int z0 = Math.max(min.getZ(), w.minZ()), z1 = Math.min(min.getZ() + fragment.sz - 1, w.minZ() + 15);
            for (int y = 0; y < fragment.sy; y++)
                for (int z = z0; z <= z1; z++)
                    for (int x = x0; x <= x1; x++) {
                        BlockState s = fragment.get(x - min.getX(), y, z - min.getZ());
                        if (s != null) w.set(x, min.getY() + y, z, s);
                    }
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return fragment.get(x - min.getX(), y - min.getY(), z - min.getZ());
        }
    }

    /** A floating island: an ellipse at the top tapering to a point, edges nibbled by a hash noise. */
    public record IslandPiece(int cx, int cz, int topY, double radius, int depth, long seed,
                              BlockState top, BlockState body, BlockState bottom) implements Piece {
        @Override
        public BoundingBox box() {
            int r = (int) Math.ceil(radius * 1.2) + 1;
            return new BoundingBox(cx - r, topY - depth - 2, cz - r, cx + r, topY, cz + r);
        }

        private int columnDepth(int x, int z) {
            double dx = (x - cx) / radius, dz = (z - cz) / radius;
            double d2 = dx * dx + dz * dz;
            double n = ((hash(seed, x, 0, z) & 255) / 255.0 - 0.5) * 0.3;
            if (d2 > 1.0 + n) return -1;
            double t = Math.max(0, 1.0 - d2);
            return 1 + (int) Math.round(Math.pow(t, 0.75) * depth) + (hash(seed, x, 1, z) & 1);
        }

        @Override
        public void write(ChunkWriter w) {
            BoundingBox b = box();
            int x0 = Math.max(b.minX(), w.minX()), x1 = Math.min(b.maxX(), w.minX() + 15);
            int z0 = Math.max(b.minZ(), w.minZ()), z1 = Math.min(b.maxZ(), w.minZ() + 15);
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) {
                    int d = columnDepth(x, z);
                    for (int k = 0; k <= d; k++) w.set(x, topY - k, z, k == 0 ? top : (k < 3 ? body : bottom));
                }
        }

        @Override
        public BlockState get(int x, int y, int z) {
            if (y > topY) return null;
            int d = columnDepth(x, z);
            int k = topY - y;
            if (d < 0 || k > d) return null;
            return k == 0 ? top : (k < 3 ? body : bottom);
        }
    }

    /** An explicit list of blocks (bridges, stairs, frames, sign props). */
    public static final class BlockListPiece implements Piece {
        private final int[] xs, ys, zs;
        private final BlockState[] states;
        private final BoundingBox box;
        private final Map<Long, BlockState> lookup;

        public BlockListPiece(Map<Long, BlockState> blocks) {
            int n = blocks.size();
            xs = new int[n];
            ys = new int[n];
            zs = new int[n];
            states = new BlockState[n];
            int i = 0, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (var e : blocks.entrySet()) {
                BlockPos p = BlockPos.of(e.getKey());
                xs[i] = p.getX();
                ys[i] = p.getY();
                zs[i] = p.getZ();
                states[i] = e.getValue();
                minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
                minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
                minZ = Math.min(minZ, zs[i]); maxZ = Math.max(maxZ, zs[i]);
                i++;
            }
            box = n == 0 ? new BoundingBox(0, 0, 0, 0, 0, 0) : new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            lookup = Map.copyOf(blocks);
        }

        @Override
        public BoundingBox box() {
            return box;
        }

        @Override
        public void write(ChunkWriter w) {
            for (int i = 0; i < states.length; i++) w.set(xs[i], ys[i], zs[i], states[i]);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return lookup.get(BlockPos.asLong(x, y, z));
        }
    }

    static int hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= (h >>> 31);
        h *= 0x7FB5D329728EA185L;
        h ^= (h >>> 27);
        return (int) h;
    }
}
