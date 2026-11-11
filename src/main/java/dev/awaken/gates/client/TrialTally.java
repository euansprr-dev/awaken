package dev.awaken.gates.client;

import dev.awaken.core.api.Notices;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.net.TrialPayloads;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The tally at the end of the Leaper's Gauntlet (SPEC §71.8, the plan's 9.4), in the look of the Lord's payout: the XP in
 * the middle of the view, climbing a step with every bell; then the Leaper's mastery bar filling to where it now is; then
 * the run itself, its time and medal and how often each move was made. It waits for an Unlock title to finish, holds
 * while you read it, and goes.
 */
final class TrialTally {
    private TrialTally() {}

    private static final int STEPS = 10;
    private static final long STEP_MS = 190, BAR_MS = 900, LINE_MS = 350, HOLD_MS = 6500, OUT_MS = 700, IN_MS = 200;
    private static final int GOLD = 0xE8C98A, PALE = 0xFFF4DC, SILVER = 0xD8DEE6, BRONZE = 0xC98A5A, DIM = 0x06050A;
    /** The Lord's payout's own walk over the game's bell: low, never pitched up into a bright ding. */
    private static final float[] WALK = {0.63F, 0.71F, 0.84F, 0.94F, 1.06F, 1.26F};
    private static final Object HOLD = new Object();

    private static TrialPayloads.Tally tally;
    private static long from = -1;
    private static int rung;

    static void show(TrialPayloads.Tally t) {
        if (tally == null) Notices.client().hold(HOLD, true); // nothing else talks over it
        tally = t;
        from = -1;
        rung = 0;
    }

    static void reset() {
        if (tally != null) Notices.client().hold(HOLD, false);
        tally = null;
        from = -1;
    }

    private static long climb() {
        return tally.xp() > 0 ? STEPS * STEP_MS : 0;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (tally == null || mc.level == null) return;
        // an Unlock title has the screen to itself: the tally begins when it has gone
        if (dev.awaken.core.api.Unlocks.client().showing()) {
            from = -1;
            return;
        }
        long now = Util.getMillis();
        if (from < 0) from = now + 300;
        if (now < from) return;
        long t = now - from;
        if (tally.xp() > 0 && rung < STEPS && t >= rung * STEP_MS) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, WALK[Math.min(WALK.length - 1, rung * WALK.length / STEPS)], 0.55F));
            rung++;
        }
        if (t > climb() + BAR_MS + 2 * LINE_MS + HOLD_MS + OUT_MS) reset();
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (tally == null || from < 0 || mc.gui.hud.isHidden()) return;
        long now = Util.getMillis();
        if (now < from) return;
        long t = now - from, climb = climb(), end = climb + BAR_MS + 2 * LINE_MS + HOLD_MS;
        float a = Mth.clamp(t / (float) IN_MS, 0, 1) * (1 - Mth.clamp((t - end) / (float) OUT_MS, 0, 1));
        if (a <= 0.02F) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = (int) (h * 0.33F);
        float scale = Mth.clamp(w / 130F, 3F, 5F);
        boolean paid = tally.xp() > 0;

        // the room dims a little behind it, so it reads over a lit works
        g.fillGradient(0, (int) (cy - 12 * scale), w, cy, 0, argb(0.36F * a, DIM));
        g.fillGradient(0, cy, w, (int) (cy + 7 * scale + 66), argb(0.36F * a, DIM), 0);

        int ruleY = (int) (cy + 7 * scale + 4);
        if (paid) {
            // the number, a step a bell: each step lands a little too big and settles
            int step = (int) Math.min(STEPS, t / STEP_MS + 1);
            float k = Mth.clamp((t - (step - 1) * STEP_MS) / (float) STEP_MS, 0, 1);
            float land = step < STEPS || t < climb + 200 ? 1 + 0.16F * (1 - k) * (1 - k) : 1;
            String n = "+" + (int) Math.round(tally.xp() * (double) step / STEPS);
            HudLook.centered(g, font, tally.first() ? "T H E   L E A P   I S   Y O U R S" : "T H E   L O N G   R U N", cx, cy - (int) (5 * scale) - 12, argb(0.85F * a, GOLD), false);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(scale * land, scale * land);
            HudLook.text(g, font, n, -font.width(n) / 2, -4, argb(a, PALE), false);
            g.pose().popMatrix();
            HudLook.centered(g, font, "XP", cx, ruleY - 9, argb(0.8F * a, GOLD), false);
        } else {
            HudLook.centered(g, font, "T H E   L O N G   R U N", cx, cy - 6, argb(0.85F * a, GOLD), false);
            ruleY = cy + 8;
        }
        float open = Mth.clamp(t / 500F, 0, 1);
        int half = (int) (w * (0.1F + 0.16F * (1 - (1 - open) * (1 - open))));
        g.fill(cx - half, ruleY + 2, cx + half, ruleY + 3, argb(a * 0.6F, GOLD));

        // the Leaper's mastery: where it was, filling to where it is
        float bar = Mth.clamp((t - climb) / (float) BAR_MS, 0, 1);
        if (t >= climb) {
            float la = a * Mth.clamp((t - climb) / 200F, 0, 1);
            boolean rose = tally.masteryAfter() > tally.masteryBefore();
            float to = tally.fill() / 1000F, was = rose ? 0 : Math.max(0, to - 0.2F), ease = 1 - (1 - bar) * (1 - bar);
            String label = "Leaper mastery · level " + (rose && bar < 0.35F ? tally.masteryBefore() : tally.masteryAfter());
            HudLook.centered(g, font, label, cx, ruleY + 9, argb(0.9F * la, PALE), false);
            int bw = Math.min(140, w / 3), bx = cx - bw / 2, by = ruleY + 21;
            g.fill(bx, by, bx + bw, by + 3, argb(0.5F * la, 0x2A2420));
            g.fill(bx, by, bx + Math.round(bw * Mth.lerp(ease, was, to)), by + 3, argb(la, GOLD));
        }

        // the run: its time and medal, then what it was made of
        long lineAt = climb + BAR_MS;
        if (t >= lineAt) {
            float la = a * Mth.clamp((t - lineAt) / 200F, 0, 1);
            int medal = tally.medal(), colour = medal == RigPayloads.Run.GOLD ? GOLD : medal == RigPayloads.Run.SILVER ? SILVER : BRONZE;
            String name = medal == RigPayloads.Run.GOLD ? "Gold" : medal == RigPayloads.Run.SILVER ? "Silver" : "Bronze";
            String line = RigRunHud.clock(tally.ticks()) + " · " + name;
            if (tally.best() > 0 && tally.ticks() < tally.best()) line += " · your best yet";
            else if (tally.best() > 0) line += " · best " + RigRunHud.clock(tally.best());
            HudLook.centered(g, font, line, cx, ruleY + 31, argb(la, colour), false);
        }
        if (t >= lineAt + LINE_MS && !tally.names().isEmpty()) {
            float la = a * Mth.clamp((t - lineAt - LINE_MS) / 200F, 0, 1);
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < tally.names().size(); i++) {
                if (i > 0) s.append(" · ");
                s.append(tally.names().get(i)).append(' ').append(tally.counts().get(i));
            }
            // never cut off: a line too long for a small screen drops its last part onto a second line
            String all = s.toString();
            if (font.width(all) > w - 24 && tally.names().size() > 2) {
                int cut = all.indexOf(" · ", all.length() / 2 - 4);
                if (cut > 0) {
                    HudLook.centered(g, font, all.substring(0, cut), cx, ruleY + 43, argb(0.85F * la, PALE), false);
                    HudLook.centered(g, font, all.substring(cut + 3), cx, ruleY + 54, argb(0.85F * la, PALE), false);
                    return;
                }
            }
            HudLook.centered(g, font, all, cx, ruleY + 43, argb(0.85F * la, PALE), false);
        }
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
