package dev.awaken.body.ascent;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.ArrivalHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The core's watch (SPEC §17.9): twice a second, a player within {@link #REACH} who hasn't the wings is given them, if
 * they came up by moving (ArrivalHooks.byMovement: a rope, a leap, a power's flight; never a tower), with the scene of
 * a first time. Someone who built their way up is told the core answers only to the long way. Nothing saved.
 */
public class WingCoreBlockEntity extends BlockEntity {
    static final double REACH = 3.0;

    public WingCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AscentContent.WING_CORE_BE.get(), pos, state);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, WingCoreBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0 || !(level instanceof ServerLevel sl)) return;
        Vec3 c = Vec3.atCenterOf(pos);
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.distanceToSqr(c) > REACH * REACH || Ascent.has(p, Ascent.Tier.WINGS)) continue;
            reached(p, pos);
        }
    }

    /** A player at the core without wings: given them by the long way, or told the core stays dark. */
    static boolean reached(ServerPlayer p, BlockPos pos) {
        if (!ArrivalHooks.byMovement(p) || !Ascent.arrivedByMovement(p, 20)) {
            // once a minute at most
            if (p.level().getGameTime() % 1200 < 10) {
                p.sendSystemMessage(Component.translatable("awaken.ascent.core.dark"), true);
            }
            return false;
        }
        int before = AscentServer.tiers(p);
        Ascent.grant(p, Ascent.Tier.WINGS, "sky platform core");
        int given = AscentServer.tiers(p) & ~before;
        AwakenNet.send(p, new AscentPayloads.Remember(given, AscentPayloads.GIFT_WINGS));
        AscentServer.show(p, new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_REMEMBER, 0));
        p.level().playSound(null, pos, AscentContent.VOW_CALL.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1F, 1.2F);
        dev.awaken.progression.Progression.count(p, "awaken:ascent_wings_core");
        dev.awaken.core.DirectorLog.log(p, "ascent", "the sky platform's core gives wings at " + pos.toShortString());
        return true;
    }

    /** Client: the core breathes a ring of light now and then. */
    static void clientTick(Level level, BlockPos pos, BlockState state, WingCoreBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 60 == 0) dev.awaken.body.ascent.client.AscentFxBridge.coreRing(pos);
    }
}
