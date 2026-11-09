package dev.awaken.arms.client;

import dev.awaken.core.api.FirstPerson;
import dev.awaken.delve.api.Pick;
import dev.awaken.delve.forged.DelveForged;
import dev.awaken.delve.forged.ForgedPicks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The Mattock turns in the hand (SPEC §49.8): look from rock to earth or wood and the holder tosses it a little way up the
 * haft, spins it end for end about the haft, and catches it, with the adze leading now; look back to rock and it goes round
 * again to the pick. Only what's seen: the block breaks at the same pace whichever end leads, a turn never slows the
 * dig, the server isn't told. Worked out here for anyone holding one near enough to see, from where they're looking.
 */
final class PickTurn {
    private PickTurn() {}

    /** The toss and the catch, in ticks; the steady look a change needs; how soon it may go round again. */
    private static final int FLIP = 7, QUICK = 3, SETTLE = 2, REST = 2;
    /** Other players turn theirs only within sight. */
    private static final double NEAR_SQR = 32 * 32;

    private static final class Hold {
        /** Half-turns made before this one; odd is the adze leading. */
        int halves;
        /** When this half-turn began; -1 for not turning. */
        int began = -1, length, ended = -99;
        boolean wanted;
        int steady, seen;
    }

    private static final Map<Integer, Hold> HOLDS = new HashMap<>();
    private static int clock;

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            HOLDS.clear();
            return;
        }
        if (mc.isPaused()) return;
        clock++;
        for (AbstractClientPlayer p : level.players()) {
            ItemStack s = p.getMainHandItem();
            if (!"mattock".equals(Pick.form(s)) || !ForgedPicks.is(s)) continue;
            boolean own = p == mc.player;
            if (!own && mc.getCameraEntity() != null && p.distanceToSqr(mc.getCameraEntity()) > NEAR_SQR) continue;
            HitResult hit = own ? mc.hitResult : p.pick(p.blockInteractionRange(), 1F, false);
            BlockState target = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? level.getBlockState(b.getBlockPos()) : null;
            step(HOLDS.computeIfAbsent(p.getId(), k -> new Hold()), p, target, own);
        }
        if (clock % 40 == 0) HOLDS.values().removeIf(h -> clock - h.seen > 40);
    }

    private static void step(Hold h, AbstractClientPlayer p, @Nullable BlockState target, boolean own) {
        boolean fresh = clock - h.seen > 1;
        h.seen = clock;
        boolean want = target == null ? (fresh ? false : h.wanted) : ForgedPicks.softSide(target);
        if (fresh) {
            // drawn from the belt: it comes up the right way round, no flourish
            h.halves = want ? 1 : 0;
            h.began = -1;
            h.wanted = want;
            h.steady = 0;
            return;
        }
        if (want == h.wanted) h.steady++;
        else {
            h.wanted = want;
            h.steady = 1;
        }
        if (h.began >= 0) {
            if (clock - h.began < h.length) return;
            // caught
            h.halves++;
            h.began = -1;
            h.ended = clock;
            play(p, own, false, odd(h));
            return;
        }
        if (h.wanted == odd(h) || h.steady < SETTLE || clock - h.ended < REST) return;
        h.began = clock;
        h.length = FirstPerson.reducedMotion.getAsBoolean() ? QUICK : FLIP;
        play(p, own, true, odd(h));
    }

    private static boolean odd(Hold h) {
        return (h.halves & 1) != 0;
    }

    private static void play(AbstractClientPlayer p, boolean own, boolean toss, boolean adze) {
        // nearer your ear than the body's own; other people's are out in the world and quieter
        var sound = toss ? DelveForged.MATTOCK_TOSS.get() : DelveForged.MATTOCK_CATCH.get();
        float volume = (own ? 0.6F : 0.35F) * (toss ? 0.9F : 1F);
        float pitch = (adze ? 0.92F : 1.06F) + p.getRandom().nextFloat() * 0.06F;
        p.level().playLocalSound(p.getX(), p.getEyeY() - 0.4, p.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static float t(Hold h) {
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Mth.clamp((clock - h.began + pt) / h.length, 0F, 1F);
    }

    /** Half-turns made by this holder's Mattock now, fractional in mid-air (quick off the hand, slowing into the catch). */
    static float halves(int holder) {
        Hold h = holder < 0 ? null : HOLDS.get(holder);
        if (h == null) return 0F;
        if (h.began < 0) return h.halves;
        float t = t(h);
        return h.halves + (t < 0.5F ? 2F * t * t : 1F - (2F - 2F * t) * (2F - 2F * t) / 2F);
    }

    /** How far up the haft it's been tossed now, 0..1 (one arc, thrown and falling back). */
    static float toss(int holder) {
        Hold h = holder < 0 ? null : HOLDS.get(holder);
        if (h == null || h.began < 0 || FirstPerson.reducedMotion.getAsBoolean()) return 0F;
        float t = t(h);
        return 4F * t * (1F - t);
    }

    /** The hand under it: a flick up as it leaves, a dip as it lands (first person). */
    static void pose(float pt, FirstPerson.View v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || FirstPerson.reducedMotion.getAsBoolean()) return;
        Hold h = HOLDS.get(mc.player.getId());
        if (h == null) return;
        float y = 0F;
        if (h.began >= 0) {
            float t = t(h);
            // up with the throw, then ready under it, and giving a little as it lands
            y = t < 0.2F ? 0.05F * (t / 0.2F) : t < 0.8F ? 0.05F * (1F - (t - 0.2F) / 0.6F) : -0.045F * ((t - 0.8F) / 0.2F);
        } else {
            float since = clock - h.ended + pt;
            if (since >= 0F && since < 3F) y = -0.045F * (1F - since / 3F);
        }
        if (y != 0F) FirstPerson.View.add(v.main, 1F, 0F, y, 0F, y * 40F, 0F, 0F);
    }

    static void reset() {
        HOLDS.clear();
    }
}
