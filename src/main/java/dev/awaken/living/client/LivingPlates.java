package dev.awaken.living.client;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.Stances;
import dev.awaken.holo.client.HoloCanvas;
import dev.awaken.holo.client.HoloWorld;
import dev.awaken.living.combat.PlateRules;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;

/**
 * Creature plates on the Holo's own glass (SPEC §21.1, {@link HoloWorld}): the same sheet, rim, brackets and scan line
 * as the K menu's panel, readable at any distance. A row with the name (an elite says so first) and the level in its
 * rank's colour on a chip, the title under it for named beasts, the ten-segment bar with the amber drain of what was
 * just lost, and under it, once Arms has strained it, the creature's stance (gold while broken); the fighter's focus
 * wears the bright rim. Picking, fades and the first-read scan come from {@link LivingClient}. Off with reduced motion (Holo's
 * setting) and in pure survival; if the holo shaders didn't load, {@link LivingFx} draws its own fallback.
 */
final class LivingPlates {
    private LivingPlates() {}

    private static final HoloWorld.Spec SPEC = new HoloWorld.Spec();
    /** The plate being written (the content callback reads these; one plate at a time, never allocated). */
    private static LivingEntity cur;
    private static CreatureTag curTag;
    private static Profile curProf;
    private static final HoloWorld.Content WRITE = LivingPlates::write;

    static final int PAD = 6, ROW = 5, BAR_H = 4, STANCE_H = 2;
    /** The focused creature's rim; the stance's amber, white-hot as it nears breaking; a broken stance's gold. */
    private static final int FOCUS = 0xDDF2FF, STANCE = 0xF2A541, STANCE_HOT = 0xFFE6B0, BROKEN = 0xFFD060;

    /** A stance under strain or broken shows a thin strip under the health bar (Arms, SPEC §24). */
    static boolean stanceShown(LivingEntity e) {
        return Stances.broken(e) || Stances.share(e) > 0.01F;
    }

    /** Whether the holo plates draw (else the fallback does, unless plates are off entirely). */
    static boolean holo() {
        return HoloWorld.on();
    }

    static void extract(ExtractLevelRenderStateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !LivingClient.platesOn() || !HoloWorld.on() || LivingClient.PICKED.isEmpty()) return;
        float pt = e.getRenderState().worldPartialTicks;
        Profile prof = Progression.profile(mc.player);
        for (var c : LivingClient.PICKED) {
            if (!(mc.level.getEntity(c.id()) instanceof LivingEntity ent)) continue;
            float vis = PlateRules.visibility(c, LivingClient.ticks, LivingClient.lookRange) * LivingClient.fadeIn(c.id());
            if (vis <= 0.02F) continue;
            CreatureTag tag = CreatureTag.of(ent);
            cur = ent;
            curTag = tag;
            curProf = prof;
            Font font = mc.font;
            String name = LivingFx.name(ent, tag, prof);
            int words = font.width(name) + (shownLevel(tag) ? chipWidth(font, tag.level()) : 0) + 10 + (tag.elite() ? font.width("ELITE") + 5 : 0);
            int titleW = tag.title().isEmpty() ? 0 : (int) (font.width(tag.title()) * 0.78F);
            SPEC.w = Mth.clamp(Math.max(words, titleW) + PAD * 2, 70, 220);
            SPEC.h = (tag.title().isEmpty() ? 27 : 35) + (stanceShown(ent) ? STANCE_H + 1 : 0);
            SPEC.x = Mth.lerp(pt, ent.xo, ent.getX());
            SPEC.y = Mth.lerp(pt, ent.yo, ent.getY()) + ent.getBbHeight() + 0.3;
            SPEC.z = Mth.lerp(pt, ent.zo, ent.getZ());
            SPEC.alpha = vis;
            // the creature the fighter is fighting wears the bright rim (Arms' soft focus)
            SPEC.accent = Stances.focused(ent) ? FOCUS : accent(tag);
            float scan = LivingClient.scanAge(ent.getId()) + pt;
            SPEC.scan = scan >= 0 && scan < 7 ? scan / 6F : -1;
            HoloWorld.plate(SPEC, WRITE);
        }
        cur = null;
    }

    static int accent(CreatureTag tag) {
        if (tag.elite()) return LivingFx.AMBER;
        if (tag.kind().hostile()) return LivingFx.WARM;
        return tag.kind() == CreatureTag.Kind.PASSIVE || tag.kind() == CreatureTag.Kind.AMBIENT ? 0x8FA7C4 : Glass.EDGE;
    }

    /** The level's colour: its rank's (E white, D green, C blue, B violet, A amber, S red), grey far below you, ember far above. */
    static int levelColour(int lv, int mine) {
        if (lv <= mine - 10) return LivingFx.GREY;
        if (lv >= mine + 5) return LivingFx.EMBER;
        return LivingFx.BAND[dev.awaken.living.api.Regions.bandOf(lv)];
    }

    private static int chipWidth(Font font, int lv) {
        return font.width("Lv " + lv) + 6;
    }

    /** Whether the plate carries a level (SPEC §21.10: an animal that can't hurt you is a pig, not "Lv 68"). */
    static boolean shownLevel(CreatureTag tag) {
        return tag.kind() != CreatureTag.Kind.PASSIVE && tag.kind() != CreatureTag.Kind.AMBIENT;
    }

    private static void write(HoloCanvas cv) {
        LivingEntity e = cur;
        CreatureTag tag = curTag;
        Font font = Minecraft.getInstance().font;
        int w = SPEC.w, h = SPEC.h;
        int mine = curProf.level();
        // the level: a chip in its rank colour at the right, the number in that colour (a deadly one pulses)
        int lv = tag.level();
        String lvText = "Lv " + lv;
        int col = levelColour(lv, mine);
        boolean chip = shownLevel(tag);
        int cw = chip ? chipWidth(font, lv) : 0;
        float cx1 = w - PAD + 1, cx0 = cx1 - cw;
        if (chip) {
            float pulse = lv >= mine + 5 ? 0.75F + 0.25F * Mth.sin(LivingClient.ticks * 0.5F) : 1;
            cv.fillF(cx0, ROW - 2, cx1, ROW + 10, argb(0.30F * pulse, col), argb(0.16F * pulse, col));
            cv.fillF(cx0, ROW + 10, cx1, ROW + 11, argb(0.85F * pulse, col), argb(0.85F * pulse, col));
            cv.textF(lvText, cx0 + 3, ROW, 0xFF000000 | col, false);
        }
        // the name; an elite says so first
        float nx = PAD;
        if (tag.elite()) {
            cv.textF("ELITE", nx, ROW, 0xFF000000 | LivingFx.AMBER, false);
            nx += font.width("ELITE") + 5;
        }
        String name = LivingFx.name(e, tag, curProf);
        int room = (int) (cx0 - nx - 4);
        if (font.width(name) > room) name = font.plainSubstrByWidth(name, Math.max(10, room - font.width("…"))) + "…";
        cv.textF(name, nx, ROW, 0xFF000000 | Glass.TEXT, false);
        if (!tag.title().isEmpty()) cv.textF(tag.title(), PAD, ROW + 11, 0xFF000000 | LivingFx.AMBER, false, 0.78F);
        // the bar: ten segments, light for what's left, amber for what was just lost
        float frac = Mth.clamp(e.getHealth() / Math.max(1F, e.getMaxHealth()), 0, 1);
        float drain = Math.max(frac, LivingClient.drained(e.getId(), frac));
        boolean stance = stanceShown(e);
        float bx0 = PAD, bx1 = w - PAD, by0 = h - PAD - BAR_H + 1 - (stance ? STANCE_H + 1 : 0), by1 = by0 + BAR_H;
        float seg = (bx1 - bx0) / 10F, gap = 1;
        int litTop = frac < 0.25F ? 0xFFFF9A8A : 0xFFCDEBFF, litBottom = frac < 0.25F ? 0xFFFF7A6A : 0xFF9FD4FF;
        for (int k = 0; k < 10; k++) {
            float s0 = k / 10F, s1 = (k + 1) / 10F;
            float x0 = bx0 + k * seg + gap / 2, x1 = bx0 + (k + 1) * seg - gap / 2;
            cv.fillF(x0, by0, x1, by1, 0x9905080E, 0x9905080E);
            float lit = Mth.clamp((frac - s0) / (s1 - s0), 0, 1);
            if (lit > 0) cv.fillF(x0, by0, x0 + (x1 - x0) * lit, by1, litTop, litBottom);
            float lost = Mth.clamp((drain - s0) / (s1 - s0), 0, 1);
            if (lost > lit) cv.fillF(x0 + (x1 - x0) * lit, by0, x0 + (x1 - x0) * lost, by1, 0xFF000000 | LivingFx.AMBER, 0xFFF2A541);
        }
        if (stance) stanceStrip(cv, e, bx0, bx1, by1 + 1);
    }

    /** The stance: one thin strip filling from both ends to the middle (Sekiro's read), pulsing gold when broken. */
    private static void stanceStrip(HoloCanvas cv, LivingEntity e, float x0, float x1, float y0) {
        float y1 = y0 + STANCE_H, mid = (x0 + x1) / 2;
        cv.fillF(x0, y0, x1, y1, 0x9905080E, 0x9905080E);
        if (Stances.broken(e)) {
            float pulse = 0.7F + 0.3F * Mth.sin(LivingClient.ticks * 0.9F);
            cv.fillF(x0, y0, x1, y1, argb(pulse, BROKEN), argb(pulse, BROKEN));
            return;
        }
        float share = Mth.clamp(Stances.share(e), 0, 1), half = (x1 - x0) / 2 * share;
        int col = share > 0.75F ? STANCE_HOT : STANCE;
        cv.fillF(x0, y0, x0 + half, y1, 0xFF000000 | col, 0xFF000000 | col);
        cv.fillF(x1 - half, y0, x1, y1, 0xFF000000 | col, 0xFF000000 | col);
        if (share > 0.75F) cv.fillF(mid - 0.5F, y0, mid + 0.5F, y1, 0x66FFFFFF, 0x66FFFFFF);
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
