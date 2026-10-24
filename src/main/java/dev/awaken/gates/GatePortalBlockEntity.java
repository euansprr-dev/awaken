package dev.awaken.gates;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The anchor of a doorway inside a gate (bottom of the opening's middle column). It holds no data: the block state
 * carries the axis and the gate's rank, and the renderer ({@code gates.client.GatePortalRenderer}) measures the opening.
 */
public final class GatePortalBlockEntity extends BlockEntity {
    public GatePortalBlockEntity(BlockPos pos, BlockState state) {
        super(GatesRegistry.PORTAL_BE.get(), pos, state);
    }
}
