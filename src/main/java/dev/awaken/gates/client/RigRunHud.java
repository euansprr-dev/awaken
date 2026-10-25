package dev.awaken.gates.client;

import dev.awaken.gates.net.RigPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * A Gauntlet's clock (SPEC §17.5 "Gauntlet"): while you run the course, your time top centre with a pip for each ring
 * (lit once passed) and the course's par under it; a ring passed flashes its split; the far landing holds your time
 * and the medal it won for a few seconds. Your own run only (the server tells only the runner).
 */
public final class RigRunHud {
    private RigRunHud() {}

    static final int GOLD = 0xFFF2C14E, SILVER = 0xFFD5DEE8, BRONZE = 0xFFD08A4E, PALE = 0xFFB9C7D9, WHITE = 0xFFFFFFFF;
    private static final int PLATE = 0x90101820;
    static final int HOLD = 20 * 6;

    private static boolean running;
    /** Client game time the clock started (corrected by the server's own count at each ring). */
    private static long start, shownAt = Long.MIN_VALUE / 4, splitAt = Long.MIN_VALUE / 4;
    private static int ring = -1, rings, par, finalTicks, medal, split;

    static void run(RigPayloads.Run r) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        switch (r.phase()) {
            case RigPayloads.Run.START -> {
                running = true;
                start = now;
                ring = -1;
                rings = r.rings();
                par = r.par();
                shownAt = Long.MIN_VALUE / 4;
                splitAt = Long.MIN_VALUE / 4;
            }
            case RigPayloads.Run.RING -> {
                ring = r.ring();
                split = r.ticks();
                start = now - r.ticks();
                splitAt = now;
            }
            case RigPayloads.Run.FINISH -> {
                running = false;
                ring = r.ring();
                finalTicks = r.ticks();
                medal = r.medal();
                par = r.par();
                shownAt = now;
            }
            default -> running = false;
        }
    }

    /** The ring this run has passed (-1: none yet, or not running). The client's rings light up to it. */
    static int passed() {
        return running ? ring : -1;
    }

    static boolean running() {
        return running;
    }

    static void clear() {
        running = false;
        shownAt = splitAt = Long.MIN_VALUE / 4;
    }

    static String clock(double ticks) {
        double s = Math.max(0, ticks) / 20.0;
        int m = (int) (s / 60);
        return m > 0 ? String.format("%d:%04.1f", m, s - m * 60) : String.format("%.1f", s);
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        long now = mc.level.getGameTime();
        float pt = dt.getGameTimeDeltaPartialTick(false);
        boolean result = !running && now - shownAt < HOLD;
        if (!running && !result) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, y = 26;
        double ticks = running ? now - start + pt : finalTicks;
        String time = clock(ticks);
        int colour = WHITE;
        float fade = 1F;
        if (result) {
            colour = medal == RigPayloads.Run.GOLD ? GOLD : medal == RigPayloads.Run.SILVER ? SILVER : BRONZE;
            fade = Mth.clamp((HOLD - (now - shownAt) - pt) / 20F, 0F, 1F);
            // text this faint would draw opaque (the font treats a near-zero alpha as none)
            if (fade < 0.05F) return;
        }
        int a = (int) (fade * 255) << 24;
        // the clock, twice the size, on a soft plate
        int tw = font.width(time) * 2;
        g.fill(cx - tw / 2 - 8, y - 4, cx + tw / 2 + 8, y + 20, (int) (fade * 0x90) << 24 | (PLATE & 0xFFFFFF));
        g.pose().pushMatrix();
        g.pose().translate(cx - tw / 2F, y);
        g.pose().scale(2F, 2F);
        g.text(font, time, 0, 0, a | (colour & 0xFFFFFF), true);
        g.pose().popMatrix();
        int ly = y + 24;
        if (result) {
            String m = (medal == RigPayloads.Run.GOLD ? "Gold" : medal == RigPayloads.Run.SILVER ? "Silver" : "Bronze") + " · par " + clock(par);
            g.centeredText(font, m, cx, ly, a | (colour & 0xFFFFFF));
            return;
        }
        // a pip for each ring, lit once passed; the split of the last one for two seconds
        if (rings > 0) {
            int pw = rings * 8 - 2, px = cx - pw / 2;
            for (int i = 0; i < rings; i++) {
                boolean lit = i <= ring;
                int c = lit ? GOLD : 0x80B9C7D9;
                g.fill(px + i * 8, ly, px + i * 8 + 6, ly + 6, c);
            }
            ly += 10;
        }
        if (now - splitAt < 40) {
            float s = Math.max(0.05F, 1F - (now - splitAt + pt) / 40F);
            g.centeredText(font, "ring " + (ring + 1) + " · " + clock(split), cx, ly, ((int) (s * 255) << 24) | (GOLD & 0xFFFFFF));
        } else {
            g.centeredText(font, "par " + clock(par), cx, ly, PALE);
        }
    }
}
