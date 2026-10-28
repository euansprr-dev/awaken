package dev.awaken.living.client;

import dev.awaken.Awaken;
import dev.awaken.living.LivingNet;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The band's words on the screen (SPEC §21.1, §21.2): a region's place caption between two hairlines, the level pane
 * (projected glass, typed in light, scanned in and glitched out like the System's cards), and the "+N XP" that rises off
 * the level line when the stream lands.
 */
final class LivingHud {
    private LivingHud() {}

    static void register(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, Awaken.id("living"), LivingHud::render);
        e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Awaken.id("living_boss_plate_ends"), BossPlate::renderEnds);
    }

    // ---- the region caption -----------------------------------------------------------------------------------------

    private static String capName = "", capSub = "";
    private static boolean capDanger;
    private static float capFrom = -1000;
    static final float CAP_IN = 10, CAP_HOLD = 60, CAP_OUT = 22;

    static void region(LivingNet.RegionMsg m) {
        if (!m.caption()) return;
        capName = m.name().toUpperCase(java.util.Locale.ROOT);
        capSub = m.commons() ? "HOME · Lv " + m.low() + "–" + m.high() : "Lv " + m.low() + "–" + m.high() + (m.danger() ? " · DANGER" : "");
        capDanger = m.danger();
        capFrom = LivingClient.ticks;
    }

    // ---- the level pane ---------------------------------------------------------------------------------------------

    private static int paneLevel, panePoints;
    private static String paneRank = "";
    private static float paneFrom = -1000;
    static final float PANE_TICKS = 48;

    static void pane(int level, int points, String rank) {
        paneLevel = level;
        panePoints = points;
        paneRank = rank;
        paneFrom = LivingClient.ticks;
    }

    // ---- the XP pop -------------------------------------------------------------------------------------------------

    static final int POPS = 4;
    private static final int[] popXp = new int[POPS], popKind = new int[POPS];
    private static final float[] popFrom = {-1000, -1000, -1000, -1000};
    static final float POP_TICKS = 40;

    static void pop(int xp, int kind) {
        float t = LivingClient.ticks;
        // merge into the newest pop if it is fresh (several kills in a breath read as one number)
        int newest = 0;
        for (int i = 1; i < POPS; i++) if (popFrom[i] > popFrom[newest]) newest = i;
        if (t - popFrom[newest] < 12 && popKind[newest] == kind) {
            popXp[newest] += xp;
            popFrom[newest] = t - 2;
            return;
        }
        int slot = 0;
        for (int i = 1; i < POPS; i++) if (popFrom[i] < popFrom[slot]) slot = i;
        popXp[slot] = xp;
        popKind[slot] = kind;
        popFrom[slot] = t;
    }

    static void reset() {
        capFrom = paneFrom = -1000;
        for (int i = 0; i < POPS; i++) popFrom[i] = -1000;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float t = LivingClient.ticks + dt.getGameTimeDeltaPartialTick(false);
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        caption(g, font, w, h, t);
        if (!LivingClient.shown) return;
        pane(g, font, w, h, t);
        pops(g, font, w, h, t);
    }

    private static void caption(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        float age = t - capFrom;
        if (age < 0 || age > CAP_IN + CAP_HOLD + CAP_OUT) return;
        float a = age < CAP_IN ? age / CAP_IN : age < CAP_IN + CAP_HOLD ? 1 : 1 - (age - CAP_IN - CAP_HOLD) / CAP_OUT;
        int accent = capDanger ? 0xFF7A52 : 0x9FE6D6;
        float scale = Math.max(1.6F, Math.min(2.4F, w / 260F));
        int nameW = spacedWidth(font, capName, 2);
        int line = (int) Math.max(160, nameW * scale + 60);
        int y = (int) (h * 0.24F);
        int x0 = (w - line) / 2;
        // two hairlines that draw outward from the middle as it arrives
        float open = Mth.clamp(age / (CAP_IN * 1.6F), 0, 1);
        int half = (int) (line / 2F * (1 - (1 - open) * (1 - open)));
        int hair = argb(0.55F * a, accent);
        g.fill(w / 2 - half, y - 4, w / 2 + half, y - 3, hair);
        g.fill(w / 2 - half, (int) (y + 9 * scale + 16), w / 2 + half, (int) (y + 9 * scale + 17), hair);
        g.pose().pushMatrix();
        g.pose().translate(w / 2F, y + 2);
        g.pose().scale(scale, scale);
        spaced(g, font, capName, -nameW / 2, 0, 2, argb(a, capDanger ? 0xFFB49A : 0xEAF7FF));
        g.pose().popMatrix();
        String sub = capSub;
        HudLook.centered(g, font, sub, w / 2, (int) (y + 9 * scale + 5), argb(0.9F * a, accent), false);
        if (x0 < 0) return;
    }

    private static void pane(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        float age = t - paneFrom;
        if (age < 0 || age > PANE_TICKS) return;
        float reveal = Mth.clamp(age / 6F, 0, 1);
        float glitch = age > PANE_TICKS - 7 ? (age - (PANE_TICKS - 7)) / 7F : 0;
        float alpha = 1 - glitch;
        int pw = 190, ph = 74;
        int x = w / 2 - pw - 34, y = (int) (h * 0.30F);
        HudLook.card(g, x, y, pw, ph, 0x7FC3FF, alpha, reveal, glitch);
        if (reveal < 0.35F) return;
        float typed = Mth.clamp((age - 2) / 10F, 0, 1);
        String title = "LEVEL " + paneLevel;
        g.pose().pushMatrix();
        g.pose().translate(x + 14, y + 10);
        g.pose().scale(2.4F, 2.4F);
        HudLook.text(g, font, title.substring(0, Math.max(1, Math.round(title.length() * typed))), 0, 0, argb(alpha, 0xEAF7FF), false);
        g.pose().popMatrix();
        if (typed >= 1) {
            g.fill(x + 14, y + 36, x + pw - 14, y + 37, argb(0.6F * alpha, 0x7FC3FF));
            if (panePoints > 0) HudLook.text(g, font, "+" + panePoints + " STAT POINTS", x + 14, y + 43, argb(alpha, 0x9FE6D6), false);
            if (!paneRank.isEmpty()) HudLook.text(g, font, paneRank, x + 14, y + 57, argb(0.85F * alpha, 0x8FA7C4), false);
        }
    }

    private static void pops(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        for (int i = 0; i < POPS; i++) {
            float age = t - popFrom[i];
            if (age < 0 || age > POP_TICKS) continue;
            float k = age / POP_TICKS;
            float a = k < 0.15F ? k / 0.15F : 1 - Math.max(0, (k - 0.55F) / 0.45F);
            int col = LivingFx.XP_KIND[Math.max(0, Math.min(LivingFx.XP_KIND.length - 1, popKind[i]))];
            String s = "+" + popXp[i] + " XP";
            int lx = w / 2 - 91 - 6 - 34, ly = (int) (h - 30 - 10 * k);
            int sw = font.width(s);
            HudLook.glow(g, lx - sw / 2 - 4, ly - 2, sw + 8, 12, col, 0.6F * a);
            HudLook.text(g, font, s, lx - sw / 2, ly, argb(a, col), true);
        }
    }

    private static int spacedWidth(Font font, String s, int spacing) {
        int tw = 0;
        for (int i = 0; i < s.length(); i++) tw += font.width(String.valueOf(s.charAt(i))) + spacing;
        return Math.max(0, tw - spacing);
    }

    private static void spaced(GuiGraphicsExtractor g, Font font, String s, int x, int y, int spacing, int argb) {
        int px = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            HudLook.text(g, font, c, px, y, argb, false);
            px += font.width(c) + spacing;
        }
    }

    static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
