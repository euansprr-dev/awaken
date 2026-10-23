package dev.awaken.veil;

import dev.awaken.veil.block.SpiritUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one readable rule, enforced: a spirit passes through everything and can only touch what glows blue. Spirits are
 * spectators and cannot click blocks, so touching goes through our own message and is checked here.
 */
public final class VeilInteract {
    private VeilInteract() {}

    public static final double REACH = 5.5;

    public enum Result { ACCEPTED, NOT_SPIRIT, TOO_FAR, NOT_BLUE, NOTHING_HAPPENED }

    public static Result use(ServerPlayer sp, BlockPos pos) {
        if (!Veil.isSpirit(sp)) return Result.NOT_SPIRIT;
        Projection.Spirit s = Projection.live(sp);
        if (s != null && s.possessed() != null) return Result.NOT_SPIRIT; // wearing a creature: its hands, not yours
        ServerLevel level = sp.level();
        if (!level.isLoaded(pos)) return Result.TOO_FAR;
        Vec3 eye = sp.getEyePosition();
        if (eye.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) return Result.TOO_FAR;
        BlockState state = level.getBlockState(pos);
        if (!state.is(VeilContent.VEIL_INTERACTABLE) || !(state.getBlock() instanceof SpiritUsable usable)) return Result.NOT_BLUE;
        return usable.spiritUse(level, pos, state, sp) ? Result.ACCEPTED : Result.NOTHING_HAPPENED;
    }
}
