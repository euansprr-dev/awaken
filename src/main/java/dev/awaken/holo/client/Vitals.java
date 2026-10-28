package dev.awaken.holo.client;

import dev.awaken.core.api.HudState;
import dev.awaken.holo.ui.ClearRules;
import dev.awaken.holo.ui.ClearRules.Piece;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Health and food on the clear screen (SPEC §40.6): thin segmented lines low in the middle, in the band's light, in place
 * of vanilla's hearts and shanks. Health shows in a fight, when hurt or when low; the Aether line (Ascent's) sits under
 * it; food shows only when hungry. One segment a heart; what a blow just took stays amber for a moment and then drains
 * away (the plates' read, and the fighting games' delayed bar); a blow flashes the line and shakes it a pixel; a heal
 * lights its new segments; low health breathes; absorption is a gold thread over the line. Each line opens from its
 * middle as it comes and closes to it as it goes. Nothing allocated per frame.
 */
final class Vitals {
    private Vitals() {}

    /** Rows apart (GUI px), and how far the lines rise to clear the hotbar while it shows. */
    static final int ROW = 4, RISE = 20, BOTTOM = 14;
    private static final int FOOD_HALF = 30, GAP = 1;
    private static final int HEALTH = 0xFF6F61, HEALTH_TOP = 0xFFC9C0, EMPTY = 0x2A1416, GHOST = 0xF2A541, HEAL = 0xFFF1EC,
            ABSORB = 0xFFD966, FOOD = 0xE8C07A, FOOD_TOP = 0xFFF0CF, FOOD_EMPTY = 0x2A2010;
    /** How long a blow's amber waits before it drains, and how fast it drains (share of the line a second). */
    private static final float GHOST_WAIT_MS = 420F, GHOST_RATE = 0.55F, FLASH_MS = 130F, SHAKE_MS = 220F, HEAL_MS = 380F;

    private static float ghost = -1, lastHealth = -1, healFrom;
    private static long hitAt, healAt, lastNanos;

    /** The health row's y: at the bottom while the hotbar is away, over it while it shows. */
    static int y(int guiHeight, float hotbar) {
        float k = Mth.clamp(hotbar, 0, 1);
        return Math.round(guiHeight - BOTTOM - RISE * k * k * (3 - 2 * k));
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.hud.isHidden() || p.isSpectator() || p.isCreative() || HudState.classic()) {
            ghost = lastHealth = -1;
            return;
        }
        long now = System.nanoTime();
        float frameMs = lastNanos == 0 ? 16F : Math.min(100F, (now - lastNanos) / 1e6F);
        lastNanos = now;
        HudDirector d = HudDirector.INSTANCE;
        float va = d.shown(Piece.VITALS), fa = d.shown(Piece.FOOD);
        float max = Math.max(1F, p.getMaxHealth()), frac = Mth.clamp(p.getHealth() / max, 0, 1);
        track(frac, now, frameMs);
        if (va < 0.01F && fa < 0.01F) return;
        int cx = g.guiWidth() / 2, y = d.vitalsY(g.guiHeight(), 0);
        if (va >= 0.01F) health(g, p, cx, y, va, frac, max, now);
        if (fa >= 0.01F) food(g, p, cx, y + 2 * ROW, fa, now);
    }

    /** The amber of a blow, the flash and shake, the light of a heal: kept from frame to frame. */
    private static void track(float frac, long now, float frameMs) {
        if (lastHealth < 0) {
            lastHealth = ghost = frac;
            return;
        }
        if (frac < lastHealth - 1e-4F) {
            hitAt = now;
            ghost = Math.max(ghost, lastHealth);
        } else if (frac > lastHealth + 1e-4F) {
            if (now - healAt > HEAL_MS * 1e6F) healFrom = lastHealth;
            healAt = now;
        }
        lastHealth = frac;
        if ((now - hitAt) / 1e6F > GHOST_WAIT_MS) ghost = Math.max(frac, ghost - GHOST_RATE * frameMs / 1000F);
        ghost = Math.max(ghost, frac);
    }

    private static void health(GuiGraphicsExtractor g, LocalPlayer p, int cx, int y, float a, float frac, float max, long now) {
        float sinceHit = (now - hitAt) / 1e6F, sinceHeal = (now - healAt) / 1e6F;
        // a blow shakes the line a pixel, side to side, settling in 220 ms
        int shake = sinceHit < SHAKE_MS ? Math.round(Mth.sin(sinceHit * 0.09F) * 2F * (1F - sinceHit / SHAKE_MS)) : 0;
        int half = HudState.VITALS_HALF;
        int x0 = cx - half + shake, x1 = cx + half + shake;
        float open = reveal(a);
        int lo = Math.round(cx + shake - half * open), hi = Math.round(cx + shake + half * open);
        int n = max <= 40 ? Math.max(1, Mth.ceil(max / 2F)) : 20;
        boolean low = frac <= ClearRules.LOW;
        float breathe = low ? 0.5F + 0.5F * Mth.sin(now / 1e9F * Mth.TWO_PI * 1.2F) : 0F;
        int core = mix(HEALTH, HEAL, 0.35F * breathe), top = mix(HEALTH_TOP, 0xFFFFFF, 0.4F * breathe);
        float flash = sinceHit < FLASH_MS ? 1F - sinceHit / FLASH_MS : 0F;
        if (flash > 0) {
            core = mix(core, 0xFFFFFF, 0.7F * flash);
            top = mix(top, 0xFFFFFF, 0.7F * flash);
        }
        // the dark under it, for snow and sky
        g.fill(lo - 1, y - 1, hi + 1, y + 3, Glass.a(0x000000, 0.42F * a));
        int litEnd = x0 + Math.round((x1 - x0) * frac);
        if (litEnd > lo) HudLook.glow(g, lo - 3, y - 2, Math.min(litEnd, hi) - lo + 6, 6, core, (0.45F + 0.35F * breathe + 0.4F * flash) * a);
        for (int k = 0; k < n; k++) {
            int s0 = x0 + Math.round((x1 - x0 + GAP) * k / (float) n), s1 = x0 + Math.round((x1 - x0 + GAP) * (k + 1) / (float) n) - GAP;
            int c0 = Math.max(s0, lo), c1 = Math.min(s1, hi);
            if (c1 <= c0) continue;
            g.fill(c0, y, c1, y + 2, Glass.a(EMPTY, 0.7F * a));
            int lit = Math.min(c1, x0 + Math.round((x1 - x0) * frac)), gone = Math.min(c1, x0 + Math.round((x1 - x0) * ghost));
            if (lit > c0) {
                g.fill(c0, y, lit, y + 1, Glass.a(top, a));
                g.fill(c0, y + 1, lit, y + 2, Glass.a(core, a));
            }
            if (gone > Math.max(c0, lit)) g.fill(Math.max(c0, lit), y, gone, y + 2, Glass.a(GHOST, 0.9F * a));
        }
        // a heal lights the segments it filled, fading over 380 ms
        if (sinceHeal < HEAL_MS && frac > healFrom) {
            int h0 = Math.max(lo, x0 + Math.round((x1 - x0) * healFrom)), h1 = Math.min(hi, litEnd);
            if (h1 > h0) g.fill(h0, y - 1, h1, y + 3, Glass.a(HEAL, 0.55F * (1F - sinceHeal / HEAL_MS) * a));
        }
        // absorption: a gold thread over the line
        float abs = p.getAbsorptionAmount();
        if (abs > 0.01F) {
            int ae = Math.min(hi, x0 + Math.round((x1 - x0) * Math.min(1F, abs / max)));
            if (ae > lo) {
                HudLook.glow(g, lo - 2, y - 3, ae - lo + 4, 4, ABSORB, 0.5F * a);
                g.fill(lo, y - 2, ae, y - 1, Glass.a(ABSORB, a));
            }
        }
        // the System's diamond at its left end
        diamond(g, lo - 5, y + 1, Glass.a(mix(core, 0xFFFFFF, 0.3F), a));
    }

    private static void food(GuiGraphicsExtractor g, LocalPlayer p, int cx, int y, float a, long now) {
        int food = p.getFoodData().getFoodLevel();
        float frac = Mth.clamp(food / 20F, 0, 1);
        boolean starving = food <= ClearRules.STARVING;
        float pulse = starving ? 0.5F + 0.5F * Mth.sin(now / 1e9F * Mth.TWO_PI * 0.9F) : 0F;
        float open = reveal(a);
        int x0 = cx - FOOD_HALF, x1 = cx + FOOD_HALF;
        int lo = Math.round(cx - FOOD_HALF * open), hi = Math.round(cx + FOOD_HALF * open);
        g.fill(lo - 1, y - 1, hi + 1, y + 2, Glass.a(0x000000, 0.4F * a));
        int core = mix(FOOD, 0xFF8A5A, 0.5F * pulse);
        for (int k = 0; k < 10; k++) {
            int s0 = x0 + Math.round((x1 - x0 + GAP) * k / 10F), s1 = x0 + Math.round((x1 - x0 + GAP) * (k + 1) / 10F) - GAP;
            int c0 = Math.max(s0, lo), c1 = Math.min(s1, hi);
            if (c1 <= c0) continue;
            g.fill(c0, y, c1, y + 1, Glass.a(FOOD_EMPTY, 0.7F * a));
            int lit = Math.min(c1, x0 + Math.round((x1 - x0) * frac));
            if (lit > c0) g.fill(c0, y, lit, y + 1, Glass.a(mix(core, FOOD_TOP, 0.25F), (0.85F + 0.15F * pulse) * a));
        }
    }

    /** A line opens from its middle as it comes and closes to it as it goes. */
    private static float reveal(float a) {
        float k = 1F - a;
        return 1F - k * k * k;
    }

    /** The band's diamond, 5 px tall, centred on (x, y). */
    private static void diamond(GuiGraphicsExtractor g, int x, int y, int argb) {
        g.fill(x, y - 2, x + 1, y + 3, argb);
        g.fill(x - 1, y - 1, x + 2, y + 2, argb);
        g.fill(x - 2, y, x + 3, y + 1, argb);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gr = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | gr << 8 | bl;
    }

    static void reset() {
        ghost = lastHealth = -1;
        lastNanos = 0;
    }
}
