package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Gauntlet stand's block entity (SPEC §71.3): nothing saved; on the client it says, twice a second, that it is there. */
public class LeapStandBlockEntity extends BlockEntity {
    public LeapStandBlockEntity(BlockPos pos, BlockState state) {
        super(AscentContent.LEAP_STAND_BE.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, LeapStandBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 10 == 0) {
            dev.awaken.body.ascent.client.AscentFxBridge.stand(pos, state.getValue(LeapStandBlock.MOVE).ordinal());
        }
    }
}
