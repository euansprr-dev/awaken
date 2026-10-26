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
 * cell keeps the ground's highest top (water counts: a lake is its surface; leaves don't, trees are drawn as trees), the
 * water's depth there, the average colour of the ground's top blocks, and the trees standing on it: how far their
 * canopy rises above the ground and its colour. Coarser cells are summed from these. Far Sight 2 split ground and
 * canopy (schema 2); a region of schema 1 is re-learned.
 *
 * @param cell   per cell {@code canopy << 24 | depth << 16 | (ground top + HEIGHT_OFFSET)}, or 0 where nothing stands
 * @param colour per cell the ground's 0xRRGGBB
 * @param crown  per cell the canopy's 0xRRGGBB, 0 where no tree stands
 */
public record FarChunk(int x, int z, int[] cell, int[] colour, int[] crown) {
    public static final int SIDE = 8, CELLS = SIDE * SIDE;

    public static final Codec<FarChunk> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(FarChunk::x),
            Codec.INT.fieldOf("z").forGetter(FarChunk::z),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("cell").forGetter(FarChunk::cell),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("colour").forGetter(FarChunk::colour),
            Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream).fieldOf("crown").forGetter(FarChunk::crown)
    ).apply(i, FarChunk::new));

    public boolean valid() {
        return cell.length == CELLS && colour.length == CELLS && crown.length == CELLS;
    }

    public static int top(int cell) {
        return (cell & 0xFFFF) - FarColumns.HEIGHT_OFFSET;
    }

    public static int depth(int cell) {
        return cell >>> 16 & 255;
    }

    /** How far the trees on this cell rise above its ground, in blocks (0: none). */
    public static int canopy(int cell) {
        return cell >>> 24;
    }

    /**
     * Reads a loaded chunk: its heightmaps and the blocks on top. Server thread only, and it touches nothing but the
     * chunk itself; about 512 block reads.
     */
    public static FarChunk of(LevelChunk chunk) {
        int[] cell = new int[CELLS], colour = new int[CELLS], crown = new int[CELLS];
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ(), minY = chunk.getMinY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int sz = 0; sz < SIDE; sz++) {
            for (int sx = 0; sx < SIDE; sx++) {
                int top = Integer.MIN_VALUE, depth = 0, wet = 0, n = 0, r = 0, g = 0, b = 0;
                int canopy = 0, leaves = 0, lr = 0, lg = 0, lb = 0;
                for (int k = 0; k < 4; k++) {
                    int lx = sx * 2 + (k & 1), lz = sz * 2 + (k >> 1);
                    // the ground's top block (ChunkAccess.getHeight is the highest taken, not the first free)
                    int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                    if (y < minY) continue;
                    pos.set(bx + lx, y, bz + lz);
                    BlockState state = chunk.getBlockState(pos);
                    var biome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
                    int rgb = FarPalette.block(state, chunk, pos, biome);
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
                    // leaves above the ground: a tree's crown
                    int crownY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
                    if (crownY > y) {
                        canopy = Math.max(canopy, crownY - y);
                        pos.setY(crownY);
                        int leaf = FarPalette.block(chunk.getBlockState(pos), chunk, pos, biome);
                        if (leaf >= 0) {
                            lr += leaf >> 16 & 255;
                            lg += leaf >> 8 & 255;
                            lb += leaf & 255;
                            leaves++;
                        }
                    }
                }
                int i = sz * SIDE + sx;
                if (n == 0) continue;
                // a cell is water where at least half its columns are
                int d = wet * 2 >= n ? Math.clamp(depth, 1, FarColumns.MAX_DEPTH) : 0;
                cell[i] = Math.min(canopy, 255) << 24 | d << 16 | Math.clamp(top + FarColumns.HEIGHT_OFFSET, 1, 65535);
                colour[i] = (r / n) << 16 | (g / n) << 8 | b / n;
                if (leaves > 0) crown[i] = Math.max(1, (lr / leaves) << 16 | (lg / leaves) << 8 | lb / leaves);
            }
        }
        return new FarChunk(chunk.getPos().x(), chunk.getPos().z(), cell, colour, crown);
    }
}
