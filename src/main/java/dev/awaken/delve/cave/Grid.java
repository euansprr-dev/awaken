package dev.awaken.delve.cave;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * A place built in memory, solid rock all round (the route test, the cost measure, ASCII slices): a box of block
 * states, stone (deepslate below y 0) outside what the builder carved.
 */
public final class Grid implements Sink {
    final int x0, y0, z0, sx, sy, sz;
    private final BlockState[] cells;
    final Map<String, int[]> marks = new HashMap<>();
    int writes;

    public Grid(int x0, int y0, int z0, int x1, int y1, int z1) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.sx = x1 - x0 + 1;
        this.sy = y1 - y0 + 1;
        this.sz = z1 - z0 + 1;
        cells = new BlockState[sx * sy * sz];
        BlockState stone = Blocks.STONE.defaultBlockState(), deep = Blocks.DEEPSLATE.defaultBlockState();
        for (int y = 0; y < sy; y++) {
            BlockState s = y0 + y < 0 ? deep : stone;
            for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) cells[idx(x, y, z)] = s;
        }
    }

    private int idx(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    @Override
    public int minX() {
        return x0;
    }

    @Override
    public int maxX() {
        return x0 + sx - 1;
    }

    @Override
    public int minZ() {
        return z0;
    }

    @Override
    public int maxZ() {
        return z0 + sz - 1;
    }

    @Override
    public int minY() {
        return y0;
    }

    @Override
    public int maxY() {
        return y0 + sy - 1;
    }

    @Override
    public BlockState get(int x, int y, int z) {
        if (!inside(x, y, z)) return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        return cells[idx(x - x0, y - y0, z - z0)];
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        if (!inside(x, y, z)) return;
        cells[idx(x - x0, y - y0, z - z0)] = state;
        writes++;
    }

    @Override
    public void mark(String what, int x, int y, int z) {
        marks.put(what, new int[]{x, y, z});
    }

    public int[] mark(String what) {
        return marks.get(what);
    }

    /** One horizontal slice as text, for the test's failure message and for looking at a place without the game. */
    public String slice(int y) {
        StringBuilder sb = new StringBuilder();
        for (int z = z0; z < z0 + sz; z++) {
            for (int x = x0; x < x0 + sx; x++) {
                BlockState s = get(x, y, z);
                char c = s.isAir() ? '.' : !s.getFluidState().isEmpty() ? '~' : s.is(net.minecraft.tags.BlockTags.CLIMBABLE) ? 'H'
                        : s.getBlock() instanceof net.minecraft.world.level.block.StairBlock ? '/' : s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock ? '_' : '#';
                sb.append(c);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
