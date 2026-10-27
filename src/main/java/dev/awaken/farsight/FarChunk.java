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
 * canopy (schema 2); a region of schema 1 is re-learned. What floats (a sky platform) isn't ground: the cell keeps the
 * ground under it.
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
                    // the ground's top block (ChunkAccess.getHeight is the highest taken, not the first free), under
                    // whatever floats over it
                    int high = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                    if (high < minY) continue;
                    int y = groundTop(chunk, bx + lx, bz + lz, high, pos);
                    boolean under = y != high;
                    if (y < minY) continue;
                    pos.set(bx + lx, y, bz + lz);
                    BlockState state = chunk.getBlockState(pos);
                    var biome = chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
                    int rgb = FarPalette.block(state, chunk, pos, biome);
                    if (rgb < 0) continue;
                    top = Math.max(top, y + 1);
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        wet++;
                        // (the floor heightmap stops at what floats over the water too)
                        depth = Math.max(depth, y - (under ? floorUnder(chunk, pos) : chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz)));
                    }
                    r += rgb >> 16 & 255;
                    g += rgb >> 8 & 255;
                    b += rgb & 255;
                    n++;
                    // leaves above the ground: a tree's crown
                    int crownY = under ? leavesOver(chunk, pos.set(bx + lx, y, bz + lz)) : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
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

    /** Open blocks under a piece that make it float: open to the sky, at least this tall. */
    static final int FLOAT_GAP = 16;
    /** A piece standing this deep from its top without such a gap under it stands on the ground. */
    static final int FLOAT_RUN = 40;

    /**
     * The ground's top block in column (x, z) from {@code y}, the heightmap's top, down: under a piece with a gap of
     * FLOAT_GAP open blocks below it, sky light in them (a sky platform, its torn-up islands, the rings down its chain),
     * the ground below. The far terrain keeps one top a column and stood every floating thing on a pillar from the ground
     * (Euan, 2026-10-27: "huge spiky pillars from the ground up to the platform"); City Sight draws them floating. A dark
     * gap is a cave under the ground's crust, which stands. Returns minY - 1 over the void. Probes every FLOAT_GAP / 2
     * blocks (a few reads a column where the ground is solid); {@code pos} is scratch.
     */
    static int groundTop(LevelChunk chunk, int x, int z, int y, BlockPos.MutableBlockPos pos) {
        int minY = chunk.getMinY(), pieceTop = y, probe = y - 1;
        while (probe >= minY && probe >= pieceTop - FLOAT_RUN) {
            if (filled(chunk, pos.set(x, probe, z))) {
                probe -= FLOAT_GAP / 2;
                continue;
            }
            // an open run here: how tall, and is it open to the sky
            int hi = probe, lo = probe;
            while (hi + 1 < pieceTop && !filled(chunk, pos.set(x, hi + 1, z))) hi++;
            while (lo - 1 >= minY && !filled(chunk, pos.set(x, lo - 1, z))) {
                var section = chunk.getSection(chunk.getSectionIndex(lo - 1));
                lo = section.hasOnlyAir() ? Math.max(minY, ((lo - 1) & ~15)) : lo - 1;
            }
            if (hi - lo + 1 >= FLOAT_GAP && skyLit(chunk, x, z, lo, hi, pos)) {
                if (lo <= minY) return minY - 1;
                pieceTop = lo - 1;          // what it floats over: the next piece down
                probe = pieceTop - 1;
            } else {
                probe = lo - 1 - FLOAT_GAP / 2;
            }
        }
        return pieceTop;
    }

    /** Whether a block is ground: a solid cube or a fluid's surface (chains, rails, plants, leaves and air are open). */
    private static boolean filled(LevelChunk chunk, BlockPos pos) {
        BlockState s = chunk.getBlockState(pos);
        return !s.isAir() && (s.isSolidRender() || !s.getFluidState().isEmpty());
    }

    /** Whether sky light reaches into the open run lo..hi of column (x, z): its foot, middle or top. */
    private static boolean skyLit(LevelChunk chunk, int x, int z, int lo, int hi, BlockPos.MutableBlockPos pos) {
        var level = chunk.getLevel();
        for (int y : new int[]{lo, (lo + hi) >> 1, hi}) {
            if (level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos.set(x, y, z)) > 0) return true;
        }
        return false;
    }

    /** The water's floor (the top of what it lies on) under the surface at {@code pos}. */
    private static int floorUnder(LevelChunk chunk, BlockPos.MutableBlockPos pos) {
        int y = pos.getY(), minY = chunk.getMinY();
        while (y > minY && chunk.getBlockState(pos.setY(y)).getFluidState().is(FluidTags.WATER)) y--;
        return y;
    }

    /** The highest leaves over the ground at {@code pos}, up to what floats over it (for the crown), or its own y. */
    private static int leavesOver(LevelChunk chunk, BlockPos.MutableBlockPos pos) {
        int ground = pos.getY(), crown = ground;
        for (int y = ground + 1; y <= ground + 48 && y <= chunk.getMaxY(); y++) {
            BlockState s = chunk.getBlockState(pos.setY(y));
            if (s.is(net.minecraft.tags.BlockTags.LEAVES)) crown = y;
            else if (filled(chunk, pos)) break;
        }
        return crown;
    }
}
