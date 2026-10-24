package dev.awaken.farsight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * One real chunk as far terrain sees it (SPEC §20, the generated layer): 8×8 cells of 2×2 blocks, ring 0's size. Each
 * cell keeps its highest top (leaves and water count: from afar a forest is its canopy, a lake its surface), the
 * water's depth there and the average colour of its top blocks. Coarser cells are summed from these.
 *
 * @param cell   per cell {@code depth << 16 | (top + HEIGHT_OFFSET)}, or 0 where nothing stands (a void column)
 * @param colour per cell 0xRRGGBB
 */
public record FarChunk(int x, int z, int[] cell, int[] colour) {
    public static final int SIDE = 8, CELLS = SIDE * SIDE;

    public static final Codec<FarChunk> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(FarChunk::x),
            Codec.INT.fieldOf("z").forGetter(FarChunk::z),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("cell").forGetter(FarChunk::cell),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("colour").forGetter(FarChunk::colour)
    ).apply(i, FarChunk::new));

    public boolean valid() {
        return cell.length == CELLS && colour.length == CELLS;
    }

    public static int top(int cell) {
        return (cell & 0xFFFF) - FarColumns.HEIGHT_OFFSET;
    }

    public static int depth(int cell) {
        return cell >>> 16;
    }

    /**
     * Reads a loaded chunk: its heightmaps and the blocks on top. Server thread only, and it touches nothing but the
     * chunk itself; about 256 block reads.
     */
    public static FarChunk of(LevelChunk chunk) {
        int[] cell = new int[CELLS], colour = new int[CELLS];
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ(), minY = chunk.getMinY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sz = 0; sz < SIDE; sz++) {
            for (int sx = 0; sx < SIDE; sx++) {
                int top = Integer.MIN_VALUE, depth = 0, wet = 0, n = 0, r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int lx = sx * 2 + (k & 1), lz = sz * 2 + (k >> 1);
                    // the top block's y (ChunkAccess.getHeight is the highest taken, not the first free)
                    int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
                    if (y < minY) continue;
                    pos.set(bx + lx, y, bz + lz);
                    BlockState state = chunk.getBlockState(pos);
                    int rgb = FarPalette.block(state, chunk, pos,
                            chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())));
                    if (rgb < 0) continue;
                    top = Math.max(top, y + 1);
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        wet++;
                        depth = Math.max(depth, y - chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz));
                    }
                    r += rgb >> 16 & 255;
                    g += rgb >> 8 & 255;
                    b += rgb & 255;
                    n++;
                }
                int i = sz * SIDE + sx;
                if (n == 0) continue;
                // a cell is water where at least half its columns are
                int d = wet * 2 >= n ? Math.clamp(depth, 1, FarColumns.MAX_DEPTH) : 0;
                cell[i] = d << 16 | Math.clamp(top + FarColumns.HEIGHT_OFFSET, 1, 65535);
                colour[i] = (r / n) << 16 | (g / n) << 8 | b / n;
            }
        }
        return new FarChunk(chunk.getPos().x(), chunk.getPos().z(), cell, colour);
    }
}
