package dev.awaken.delve.cave;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Where a cave place is written, in world coordinates: one chunk of the world while it generates (everything outside
 * the clip is dropped), the whole place for commands, or an in-memory {@link Grid} for the route test. A builder draws
 * every random choice in the same order whatever the clip, so the chunks of one place always agree; the only thing it
 * reads back is the block it is about to replace (carving keeps out of the sky's water and never eats a build).
 */
public interface Sink {
    int minX();

    int maxX();

    int minZ();

    int maxZ();

    int minY();

    int maxY();

    BlockState get(int x, int y, int z);

    void set(int x, int y, int z, BlockState state);

    /** Water placed here should start flowing (a fluid tick). */
    default void flow(int x, int y, int z) {}

    /** A container at (x, y, z): the block, then these items in its first slots (a chest, a barrel). */
    default void container(int x, int y, int z, BlockState state, List<ItemStack> items) {
        set(x, y, z, state);
    }

    /** A chest that rolls the loot table {@code table} (a resource id) the first time it opens. */
    default void lootChest(int x, int y, int z, BlockState state, String table, long seed) {
        set(x, y, z, state);
    }

    /** A minecart standing on the rails at (x, y, z). */
    default void minecart(int x, int y, int z) {}

    /** A route anchor for the test ({@link Routes}): "start", "goal" and named checkpoints. */
    default void mark(String what, int x, int y, int z) {}

    default boolean here(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    default boolean inside(int x, int y, int z) {
        return here(x, z) && y >= minY() && y <= maxY();
    }

    /** Whether any of the box (inclusive) is ours: lets a builder skip a whole room in a chunk that holds none of it. */
    default boolean touches(int x0, int z0, int x1, int z1) {
        return x1 >= minX() && x0 <= maxX() && z1 >= minZ() && z0 <= maxZ();
    }
}
