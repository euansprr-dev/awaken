package dev.awaken.hearth.block;

import dev.awaken.hearth.HearthContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hearthfire's block entity: nothing saved, it only tells the client where the fires are so their glow can be drawn
 * (a warm halo and a pool of light on the ground at night, the city's drawn-light way) without scanning for blocks.
 */
public class HearthfireBlockEntity extends BlockEntity {
    /** Client side: every Hearthfire loaded now (a handful at most). */
    public static final List<BlockPos> LOADED = new ArrayList<>();

    public HearthfireBlockEntity(BlockPos pos, BlockState state) {
        super(HearthContent.HEARTHFIRE_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide() && !LOADED.contains(worldPosition)) LOADED.add(worldPosition.immutable());
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) LOADED.remove(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide()) LOADED.remove(worldPosition);
    }
}
