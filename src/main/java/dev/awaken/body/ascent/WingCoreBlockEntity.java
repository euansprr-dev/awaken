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
 * The core's watch (SPEC §17.9): twice a second, a player within {@link #REACH} who hasn't the wings is given them, with
 * the scene of a first time, if they hold the rank it waits for ({@link #RANK}, progression plan decision 2) and the
 * rope carried them up to it (the platform's visit, ArrivalHooks.byLine: its rings by rope, never a pearl, a power's
 * flight, a hang or a tower). Otherwise it says why on first contact, then at most every {@link #TELL_EVERY}, each
 * refusal in its own words ({@link Refusal}), and logs it. Nothing saved. The wings themselves hang in a case over it
 * (the client's WingCaseRenderer).
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

    /** Why the core stays dark for someone, in the order it asks (SPEC §17.9): the rank first, then the way up. */
    enum Refusal {
        /** It gives the wings. */
        NONE(""),
        /** Below its rank: come back at rank C (the promise is noted). */
        RANK("rank"),
        /** Blocks they placed on the way up or since: a tower, a bridge. */
        BUILT("built"),
        /** Up by movement, but the rope never carried them: a pearl, a power's flight, a climb by hand. */
        LINE("line");

        final String key;

        Refusal(String key) {
            this.key = key;
        }
    }

    /**
     * The core's verdict. The way up is the platform's visit (the Wilds' ward, opened on the way in and kept while the
     * player stays): once the rope has carried them up its rings, a long look round the deck before walking in still
     * counts. Until 11 Nov the core also asked that a third of the last 20 s be spent on the rope or in a Runner move, so a
     * climber who looked round the deck for 14 s before walking in was turned away with the rope refusal (very likely
     * what Euan met that day, up its rings by rope: the core logged nothing then, so it can't be told for sure).
     */
    static Refusal judge(ServerPlayer p) {
        if (dev.awaken.progression.Progression.rank(p).ordinal() < RANK.ordinal()) return Refusal.RANK;
        if (!ArrivalHooks.byMovement(p)) return Refusal.BUILT;
        if (!ArrivalHooks.byLine(p)) return Refusal.LINE;
        return Refusal.NONE;
    }

    /** A player at the core without wings: given them, or told why the core stays dark. */
    static boolean reached(ServerPlayer p, BlockPos pos) {
        Refusal why = judge(p);
        if (why != Refusal.NONE) {
            if (dev.awaken.core.Throttle.allow(p, "wing_core_" + why.key, TELL_EVERY)) {
                p.sendSystemMessage(Component.translatable("awaken.ascent.core." + why.key, RANK.label), true);
                // the case's own answer to the rank: the vault's shutter refusing, low (Minecraft's own recording)
                if (why == Refusal.RANK) p.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.VAULT_INSERT_ITEM_FAIL,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 0.7F);
                dev.awaken.Awaken.LOG.info("[ascent] the sky core at {} stays dark for {}: {}", pos.toShortString(), p.getPlainTextName(), why.key);
            }
            if (why == Refusal.RANK) {
                dev.awaken.core.api.Promises.seen(p, "wing_core:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(), pos,
                        dev.awaken.core.api.Promises.Need.rank(RANK.label), "A sky core's wings");
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
        dev.awaken.Awaken.LOG.info("[ascent] the sky core at {} gives {} the wings", pos.toShortString(), p.getPlainTextName());
        return true;
    }

    /** Client: the case's air (light off the feathers, wind across the deck, the wings stirring). */
    static void clientTick(Level level, BlockPos pos, BlockState state, WingCoreBlockEntity be) {
        dev.awaken.body.ascent.client.AscentFxBridge.caseTick(level, pos);
    }
}
