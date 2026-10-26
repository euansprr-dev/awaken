package dev.awaken.above.block;

import dev.awaken.above.AboveBlocks;
import dev.awaken.above.AboveSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes a sealed door hum for anyone near it whose rank is high enough, and read anyone who comes close who isn't (it
 * says the rank it waits for). Checks every 2 seconds. Once after it loads it paints its panels with its rank (their
 * seams glow in its colour, SPEC §17.27), so doors built before panels knew their rank light up too.
 */
public class DoorCoreBlockEntity extends BlockEntity {
    /** Not saved: a door checks its panels once each time it loads, and that is cheap. */
    private boolean painted;

    private static final double HUM_RANGE = 14.0;
    /** Close enough to the door to be read: in front of it, a step or two from touching it. */
    static final double READ_RANGE = 5.0;

    public DoorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AboveBlocks.DOOR_CORE_BE.get(), pos, state);
    }

    /** Client: every 2 s, within 24 blocks, the door breathes its rank's colour (it waits for that rank, whoever looks). */
    public static void clientTick(Level level, BlockPos pos, BlockState state, DoorCoreBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 40L) != 0) return;
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, false);
        if (near == null) return;
        dev.awaken.body.ascent.client.AscentFxBridge.doorBreath(pos, DoorCoreBlock.required(state).colour);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DoorCoreBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 40L) != 0 || !(level instanceof ServerLevel sl)) return;
        if (!be.painted) {
            be.painted = true;
            DoorCoreBlock.paint(sl, pos, state);
        }
        for (ServerPlayer sp : sl.players()) {
            if (!sp.isSpectator() && sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= READ_RANGE * READ_RANGE
                    && !DoorCoreBlock.canOpen(sp, state)) DoorCoreBlock.readOnApproach(sp, sl, pos, state);
        }
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, HUM_RANGE, false);
        if (near == null || near.isSpectator() || !DoorCoreBlock.canOpen(near, state)) return;
        sl.playSound(null, pos, AboveSounds.DOOR_HUM.get(), SoundSource.BLOCKS, 0.9f, 0.9f + level.getRandom().nextFloat() * 0.1f);
        sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.6, 0.8, 0.6, 0.01);
    }
}
