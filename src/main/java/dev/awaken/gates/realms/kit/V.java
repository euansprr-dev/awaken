package dev.awaken.gates.realms.kit;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A chunk's scratch volume in the Realm's own coordinates (0,0 is the Realm's middle), one block of margin all round so a
 * late pass can read the neighbours of what earlier passes drew. Null is air. One per worldgen thread, cleared per chunk.
 */
public final class V {
    public static final int H = 384, W = 18;

    private static final ThreadLocal<BlockState[]> SCRATCH = ThreadLocal.withInitial(() -> new BlockState[W * W * H]);

    public final int x0, z0;
    final BlockState[] b;

    public V(int x0, int z0) {
        this.x0 = x0;
        this.z0 = z0;
        b = SCRATCH.get();
        java.util.Arrays.fill(b, null);
    }

    public boolean in(int x, int y, int z) {
        return x >= x0 - 1 && x <= x0 + 16 && z >= z0 - 1 && z <= z0 + 16 && y >= 0 && y < H;
    }

    private int i(int x, int y, int z) {
        return ((x - x0 + 1) * W + z - z0 + 1) * H + y;
    }

    public BlockState get(int x, int y, int z) {
        return in(x, y, z) ? b[i(x, y, z)] : null;
    }

    public void set(int x, int y, int z, BlockState s) {
        if (in(x, y, z)) b[i(x, y, z)] = s;
    }

    /** Sets a block only where there is nothing yet. */
    public void add(int x, int y, int z, BlockState s) {
        if (in(x, y, z) && b[i(x, y, z)] == null) b[i(x, y, z)] = s;
    }

    public boolean empty(int x, int y, int z) {
        return in(x, y, z) && b[i(x, y, z)] == null;
    }

    public boolean solid(int x, int y, int z) {
        BlockState s = get(x, y, z);
        return s != null && s.canOcclude();
    }

    /**
     * The highest block of a column at or below {@code from} that holds things up, or -1. What stands on the ground stands
     * on this, never on a guess at the height: a plant or a post left over from an earlier pass doesn't count.
     */
    public int groundBelow(int x, int from, int z) {
        for (int y = Math.min(from, H - 1); y >= 0; y--) if (holds(get(x, y, z))) return y;
        return -1;
    }

    /** Whether a block is something else can stand on: it has a body and isn't a plant. */
    public static boolean holds(BlockState s) {
        return s != null && s.getFluidState().isEmpty() && !s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
    }

    /**
     * Puts a footing under something: fills straight down from {@code y} to whatever holds it up. Every post, pillar and
     * wall stands on one, so nothing can be left in the air when the land under it comes out lower than the plan guessed.
     */
    public void foot(int x, int y, int z, BlockState s) {
        for (int k = y; k >= 0; k--) {
            if (holds(get(x, k, z))) return;
            set(x, k, z, s);
        }
    }

    /** Writes the chunk itself (not the margin) out. */
    public void emit(Out o) {
        for (int a = 0; a < 16; a++) for (int c = 0; c < 16; c++) for (int y = 0; y < H; y++) {
            BlockState s = b[((a + 1) * W + c + 1) * H + y];
            if (s != null) o.set(x0 + a, y, z0 + c, s);
        }
    }

    public interface Out {
        void set(int x, int y, int z, BlockState s);
    }
}
