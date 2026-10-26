package dev.awaken.farsight.voxel;

/**
 * One chunk's quads at every level ({@link VoxelMesher}), {@link VoxelMesher#INTS} ints a quad, with the block range
 * they span (world y) for culling.
 */
public record VoxelMesh(int chunkX, int chunkZ, int slot, int minY, int maxY, int[][] quads) {
    public int quadCount(int level) {
        return quads[level].length / VoxelMesher.INTS;
    }
}
