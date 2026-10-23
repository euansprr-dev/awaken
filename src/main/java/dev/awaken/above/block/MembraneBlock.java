package dev.awaken.above.block;

import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Pressure Membrane: you walk through it, water doesn't. In 26.3 fluids only spread into blocks tagged
 * {@code minecraft:washed_away_by_fluids} (or liquid containers), so a passable block outside that tag holds the sea
 * back from an ocean lab's airlock.
 */
public class MembraneBlock extends TransparentBlock {
    public MembraneBlock(BlockBehaviour.Properties p) {
        super(p);
    }
}
