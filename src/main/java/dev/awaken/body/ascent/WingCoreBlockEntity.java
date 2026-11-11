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
 * they hold the rank it waits for ({@link #RANK}, progression plan decision 2) and came up by moving
 * (ArrivalHooks.byMovement: a rope, a leap, a power's flight; never a tower), with the scene of a first time. Otherwise
 * it says why on first contact (then every {@link #TELL_EVERY}): the rank first, then a climb up its stair. Nothing
 * saved. The wings themselves hang in a case over it (the client's WingCaseRenderer).
 */
public class WingCoreBlockEntity extends BlockEntity {
    /** From the core's middle to anyone standing at the case's rail (two out, its corners nearly three). */
    static final double REACH = 4.7;
    /** The rank the core gives wings to. */
    static final dev.awaken.progression.Rank RANK = dev.awaken.progression.Rank.C;
    /** A refusal is said on arrival, then at most this often. */
    static final int TELL_EVERY = 200;

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

    /**
     * A player at the core without wings. The rank is asked first, however they came (Euan, 11 Nov 2026, at the core at
     * D: "it's way too early to get these... it says, like, come back when you're rank C"): below it the case stays
     * shut, they are told to come back at the rank, and the core goes on their Tasks page. At the rank the way they
     * came is asked: given the wings by the long way, or told why the core stays dark.
     */
    static boolean reached(ServerPlayer p, BlockPos pos) {
        if (dev.awaken.progression.Progression.rank(p).ordinal() < RANK.ordinal()) {
            if (dev.awaken.core.Throttle.allow(p, "wing_core", TELL_EVERY)) {
                p.sendSystemMessage(Component.translatable("awaken.ascent.core.rank", RANK.label), true);
                // the case's own answer: the vault's shutter refusing, low (Minecraft's own recording)
                p.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.VAULT_INSERT_ITEM_FAIL, net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 0.7F);
            }
            dev.awaken.core.api.Promises.seen(p, "wing_core:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(), pos,
                    dev.awaken.core.api.Promises.Need.rank(RANK.label), "A sky core's wings");
            return false;
        }
        // the core answers only the line (SPEC §17.29): its stair by rope; a pearl, a power's flight or a hang never
        if (!ArrivalHooks.byLine(p)) {
            if (dev.awaken.core.Throttle.allow(p, "wing_core_line", 200)) p.sendSystemMessage(Component.translatable("awaken.ascent.core.line"), true);
            return false;
        }
        if (!ArrivalHooks.byMovement(p) || !Ascent.arrivedByMovement(p, 20)) {
            if (dev.awaken.core.Throttle.allow(p, "wing_core", TELL_EVERY)) {
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
        dev.awaken.core.api.Promises.keptAll(p, "wing_core:");
        // the way down is the lesson (SPEC §63.2): a line of light off the pier, and for a minute a fall is a soft landing
        WayUp.firstFlight(p, pos);
        dev.awaken.core.DirectorLog.log(p, "ascent", "the sky platform's core gives wings at " + pos.toShortString());
        return true;
    }

    /** Client: the case's air (light off the feathers, wind across the deck, the wings stirring). */
    static void clientTick(Level level, BlockPos pos, BlockState state, WingCoreBlockEntity be) {
        dev.awaken.body.ascent.client.AscentFxBridge.caseTick(level, pos);
    }
}
