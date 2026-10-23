package dev.awaken.above.block;

import dev.awaken.above.AboveBlocks;
import dev.awaken.above.AboveSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Makes a sealed door hum for anyone near it whose rank is high enough. Checks every 2 seconds. */
public class DoorCoreBlockEntity extends BlockEntity {
    private static final double HUM_RANGE = 14.0;

    public DoorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AboveBlocks.DOOR_CORE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DoorCoreBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 40L) != 0 || !(level instanceof ServerLevel sl)) return;
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, HUM_RANGE, false);
        if (near == null || near.isSpectator() || !DoorCoreBlock.canOpen(near, state)) return;
        sl.playSound(null, pos, AboveSounds.DOOR_HUM.get(), SoundSource.BLOCKS, 0.9f, 0.9f + level.getRandom().nextFloat() * 0.1f);
        sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.6, 0.8, 0.6, 0.01);
    }
}
