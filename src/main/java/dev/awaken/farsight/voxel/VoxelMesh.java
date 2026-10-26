package dev.awaken.farsight.voxel;

/**
 * One chunk's quads at every level ({@link VoxelMesher}), {@link VoxelMesher#INTS} ints a quad, grouped by face:
 * {@code starts[level][d]} is face d's first quad (down, up, north, south, west, east; [6] the count), with the block
 * range they span (world y) for culling, and the chunk's lowest ground (what Sodium must have built to draw it all).
 */
public record VoxelMesh(int chunkX, int chunkZ, int slot, int minY, int maxY, int groundY, int[][] quads, int[][] starts) {
    public int quadCount(int level) {
        return quads[level].length / VoxelMesher.INTS;
    }
}
