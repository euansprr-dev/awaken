package dev.awaken.ground;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A half-block of natural ground (SPEC §31, P1): the slab the soft-steps pass lays on the foot of a one-block rise. A plain
 * {@link SlabBlock} (waterlogging, double slabs and placement are vanilla's) that mobs may spawn on, unlike a vanilla slab
 * whose half-height top is not a "sturdy" face. Not in {@code #minecraft:supports_vegetation}: nothing grows on it and
 * nothing floats above it; a plant stands inside the slab's own block ({@link TurfSlabBlock#TUFT}).
 */
public class SoftSlabBlock extends SlabBlock {
    public SoftSlabBlock(Properties properties) {
        super(properties.isValidSpawn((state, level, pos, type) -> state.getValue(TYPE) != SlabType.TOP));
    }
}
