package dev.awaken.opening.client;

import dev.awaken.Awaken;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The opening on the screen: the card (CARILLON in the System's type, a flicker of [ERROR]), the letterbox (feathered,
 * a bronze hairline, the place's name in it as the flight lands), title cards, people's lines as subtitles with their
 * names, the wait in the square, the eyelids of the infirmary, the skip hint, and the blacks between beats. While a
 * camera shot runs the game's own HUD steps aside (chat and subtitles stay).
 */
final class OpeningHud {
    private OpeningHud() {}

    static final Identifier LAYER = Awaken.id("opening_hud");
    static final int BRONZE = 0xC9A46A, PAPER = 0xF2EBDD, ASH = 0xB9B2A4, SIGNAL_RED = 0xFF3040, SIGNAL_CYAN = 0x40E0FF;

    static void register(RegisterGuiLayersEvent e) {
        e.registerAboveAll(LAYER, OpeningHud::render);
    }

    // ---- what's on screen -------------------------------------------------------------------------------------------

    /** A title card: big words, a line under them, for n ticks. */
    private record Title(String big, String small, long from, int ticks) {}

    /** Someone speaking: name, line (wrapped once), from when, for how long. */
    private static final class Line {
        String who;
        List<FormattedCharSequence> text = List.of();
        String raw = "";
        long from;
        int ticks;
    }

    private static @Nullable Title title;
    private static final List<Line> LINES = new ArrayList<>();
    /** A black over everything (0..1) the beats set and ease; the eyelids (0 shut .. 1 open) for the wake. */
    static float black, blackO, blackTarget, blackRate = 0.1F;
    static float lids = 1F, lidsO = 1F;
    static boolean lidsOn;
    /** Cinematic bars for the arrival and the wake (the sky shot has its own). */
    static float bars, barsO, barsTarget;
    /** The square's wait: how many hunters are still running (-1: not waiting). */
    static int waiting = -1;
    private static long waitingFrom;
    /** A flash of colour (the light falling, the pull), fading. */
    private static float flash;
    private static int flashColour;

    static void reset() {
        title = null;
        LINES.clear();
        black = blackO = blackTarget = 0;
        lids = lidsO = 1;
        lidsOn = false;
        bars = barsO = barsTarget = 0;
        waiting = -1;
        flash = 0;
    }

    static void title(String big, String small, int ticks) {
        title = new Title(big, small, OpeningClient.ticks(), ticks);
    }

    static void line(String who, String line, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Line l = new Line();
        l.who = who;
        l.raw = line;
        l.from = OpeningClient.ticks();
        l.ticks = Math.max(40, ticks);
        int w = Math.min(320, mc.getWindow().getGuiScaledWidth() - 40);
        l.text = mc.font.split(Component.literal(line), w);
        LINES.add(l);
        while (LINES.size() > 2) LINES.removeFirst();
    }

    static void flash(int rgb, float strength) {
        flashColour = rgb & 0xFFFFFF;
        flash = Math.max(flash, strength);
    }

    static void waiting(int n) {
        if (n >= 0 && waiting < 0) waitingFrom = OpeningClient.ticks();
        waiting = n;
    }

    static void tick() {
        blackO = black;
        black += (blackTarget - black) * blackRate;
        if (Math.abs(blackTarget - black) < 0.002F) black = blackTarget;
        barsO = bars;
        bars += (barsTarget - bars) * (barsTarget > bars ? 0.14F : 0.08F);
        flash = Math.max(0, flash - 0.04F);
        long now = OpeningClient.ticks();
        LINES.removeIf(l -> now > l.from + l.ticks + 20);
        if (title != null && now > title.from + title.ticks + 20) title = null;
    }

    /** The game's HUD steps aside while a shot holds the camera, under a black, or behind the eyelids. */
    static void hideHud(RenderGuiLayerEvent.Pre e) {
        Identifier id = e.getName();
        if (id.equals(LAYER)) return;
        String path = id.getPath();
        if (path.equals("subtitle_overlay")) return;
        boolean shot = OpeningCamera.active() || SkyShot.current != null;
        boolean hide = shot || black > 0.4F || lidsOn;
        // chat stays, except over a shot (it is a film then)
        if (path.equals("chat") && !shot) return;
        if (hide) e.setCanceled(true);
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        long now = OpeningClient.ticks();
        SkyShot sky = SkyShot.current;
        // the bars: the sky shot's own, or a cinematic's
        float b = Math.max(sky == null ? 0 : Mth.lerp(pt, sky.barsO, sky.bars), Mth.lerp(pt, barsO, bars));
        if (b > 0.01F) letterbox(g, w, h, b, sky);
        // the eyelids (the infirmary): black closing from above and below, a soft edge
        if (lidsOn) {
            float o = Mth.lerp(pt, lidsO, lids);
            int half = (int) (h * 0.5F * (1 - o));
            int soft = Math.max(6, h / 14);
            g.fill(0, 0, w, half, 0xFF000000);
            g.fillGradient(0, half, w, half + soft, 0xFF000000, 0x00000000);
            g.fill(0, h - half, w, h, 0xFF000000);
            g.fillGradient(0, h - half - soft, w, h - half, 0x00000000, 0xFF000000);
        }
        // a flash of light
        if (flash > 0.005F) g.fill(0, 0, w, h, ((int) (Math.min(1F, flash) * 255) << 24) | flashColour);
        // blacks: the sky shot's card, or a beat's
        float bl = Math.max(sky == null ? 0 : Mth.lerp(pt, sky.blackO, sky.black), Mth.lerp(pt, blackO, black));
        if (sky != null && sky.skipFlash > 0) bl = Math.max(bl, sky.skipFlash / 12F);
        if (bl > 0.003F) g.fill(0, 0, w, h, ((int) (Math.min(1F, bl) * 255) << 24));
        if (sky != null) card(g, font, w, h, pt, sky);
        if (title != null) title(g, font, w, h, pt, now);
        lines(g, font, w, h, pt, now);
        if (waiting > 0 && !OpeningCamera.active()) waitBanner(g, font, w, h, pt, now);
        if (sky != null && sky.canSkip && OpeningCamera.active()) skipHint(g, font, w, h, pt);
    }

    /** Feathered bars with a bronze hairline; as the flight lands, the place's name in the lower bar. */
    private static void letterbox(GuiGraphicsExtractor g, int w, int h, float b, @Nullable SkyShot sky) {
        int bh = (int) (h * 0.105F * b);
        int soft = Math.max(4, h / 60);
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fillGradient(0, bh, w, bh + soft, 0xC0000000, 0x00000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
        g.fillGradient(0, h - bh - soft, w, h - bh, 0x00000000, 0xC0000000);
        int hair = ((int) (0x55 * b) << 24) | BRONZE;
        g.fill(w / 6, bh - 1, w - w / 6, bh, hair);
        g.fill(w / 6, h - bh, w - w / 6, h - bh + 1, hair);
        if (sky != null && OpeningCamera.active()) {
            int age = OpeningCamera.age();
            float a = Mth.clamp((age - (SkyShot.CARD + SkyShot.FLIGHT - 110)) / 20F, 0, 1)
                    * Mth.clamp((SkyShot.CARD + SkyShot.FLIGHT - 8 - age) / 16F, 0, 1);
            if (a > 0.02F && bh > 12) {
                Font font = Minecraft.getInstance().font;
                spaced(g, font, "CARILLON", 22, h - bh / 2 - 8, 2, ((int) (a * 255) << 24) | PAPER, 1F);
                spaced(g, font, "THE RUNNERS' ROOST · FIRST LIGHT", 22, h - bh / 2 + 3, 1, ((int) (a * 200) << 24) | BRONZE, 1F);
            }
        }
    }

    /** The card: CARILLON typed out in wide spaced capitals, a flicker of [ERROR], then it fades with the black. */
    private static void card(GuiGraphicsExtractor g, Font font, int w, int h, float pt, SkyShot sky) {
        if (!OpeningCamera.active()) return;
        float t = OpeningCamera.age() + pt;
        if (t > SkyShot.CARD + 6) return;
        String name = "CARILLON";
        float fade = Mth.clamp((SkyShot.CARD - 4 - t) / 18F, 0, 1);
        if (fade <= 0) return;
        float scale = Math.max(2F, Math.min(4F, w / 150F));
        int spacing = 6;
        int tw = 0;
        for (int i = 0; i < name.length(); i++) tw += font.width(String.valueOf(name.charAt(i))) + spacing;
        tw -= spacing;
        g.pose().pushMatrix();
        g.pose().translate(w / 2F, h * 0.42F);
        g.pose().scale(scale, scale);
        float x = -tw / 2F;
        boolean glitch = t >= 58 && t < 63 || t >= 66 && t < 67;
        for (int i = 0; i < name.length(); i++) {
            String c = String.valueOf(name.charAt(i));
            float appear = Mth.clamp((t - 16 - i * 3.2F) / 6F, 0, 1);
            if (appear > 0) {
                int a = (int) (appear * fade * 255);
                float rise = (1 - appear) * 3;
                if (glitch) {
                    g.text(font, c, (int) x - 1, (int) rise, (Math.min(a, 160) << 24) | SIGNAL_RED, false);
                    g.text(font, c, (int) x + 1, (int) rise, (Math.min(a, 160) << 24) | SIGNAL_CYAN, false);
                }
                g.text(font, c, (int) x, (int) rise, (a << 24) | PAPER, false);
            }
            x += font.width(c) + spacing;
        }
        g.pose().popMatrix();
        // the [ERROR] you only half see: two frames over the name, offset like a bad signal
        if (glitch) {
            g.pose().pushMatrix();
            g.pose().translate(w / 2F + (t % 2 < 1 ? 3 : -2), h * 0.42F + scale * 3);
            g.pose().scale(scale * 0.6F, scale * 0.6F);
            String err = "[ERROR]";
            int ew = font.width(err);
            g.text(font, err, -ew / 2 - 1, 0, 0x90FF3040, false);
            g.text(font, err, -ew / 2 + 1, 0, 0x9040E0FF, false);
            g.text(font, err, -ew / 2, 0, 0xB0FFFFFF, false);
            g.pose().popMatrix();
        }
        // the line under it
        float sub = Mth.clamp((t - 46) / 14F, 0, 1) * fade;
        if (sub > 0.02F) spacedCentered(g, font, "THE CITY OF BELLS", w / 2, (int) (h * 0.42F + scale * 12 + 6), 2, ((int) (sub * 170) << 24) | BRONZE);
    }

    private static void title(GuiGraphicsExtractor g, Font font, int w, int h, float pt, long now) {
        Title tt = title;
        if (tt == null) return;
        float t = now - tt.from + pt;
        float a = Mth.clamp(t / 12F, 0, 1) * Mth.clamp((tt.ticks - t) / 16F + 1, 0, 1);
        if (a <= 0.01F) return;
        float scale = Math.max(1.5F, Math.min(3F, w / 220F));
        int y = (int) (h * 0.30F);
        int tw = spacedWidth(font, tt.big, 3);
        g.pose().pushMatrix();
        g.pose().translate(w / 2F, y);
        g.pose().scale(scale, scale);
        spaced(g, font, tt.big, -tw / 2, 0, 3, ((int) (a * 255) << 24) | PAPER, 1F);
        g.pose().popMatrix();
        int line = (int) (tw * scale * 0.5F * Mth.clamp(t / 20F, 0, 1));
        int ly = y + (int) (scale * 11);
        g.fill(w / 2 - line, ly, w / 2 + line, ly + 1, ((int) (a * 160) << 24) | BRONZE);
        if (!tt.small.isEmpty()) spacedCentered(g, font, tt.small, w / 2, ly + 6, 1, ((int) (a * 220) << 24) | ASH);
    }

    private static void lines(GuiGraphicsExtractor g, Font font, int w, int h, float pt, long now) {
        int y = h - 68;
        for (int k = LINES.size() - 1; k >= 0; k--) {
            Line l = LINES.get(k);
            float t = now - l.from + pt;
            float a = Mth.clamp(t / 8F, 0, 1) * Mth.clamp((l.ticks + 20 - t) / 20F, 0, 1);
            if (a <= 0.02F) continue;
            int lh = l.text.size() * 10;
            y -= lh + 14;
            int ia = (int) (a * 255);
            g.centeredText(font, Component.literal(l.who.toUpperCase(java.util.Locale.ROOT)), w / 2, y, (ia << 24) | BRONZE);
            for (int i = 0; i < l.text.size(); i++) {
                FormattedCharSequence s = l.text.get(i);
                g.text(font, s, w / 2 - font.width(s) / 2, y + 11 + i * 10, (ia << 24) | PAPER, true);
            }
        }
    }

    private static void waitBanner(GuiGraphicsExtractor g, Font font, int w, int h, float pt, long now) {
        float a = Mth.clamp((now - waitingFrom + pt) / 20F, 0, 1);
        int ia = (int) (a * 230);
        spacedCentered(g, font, "THE GATE IS DRAWING BREATH", w / 2, 26, 2, (ia << 24) | PAPER);
        String n = waiting == 1 ? "Waiting for 1 hunter still running" : "Waiting for " + waiting + " hunters still running";
        g.centeredText(font, Component.literal(n), w / 2, 40, ((int) (a * 200) << 24) | BRONZE);
    }

    private static void skipHint(GuiGraphicsExtractor g, Font font, int w, int h, float pt) {
        int age = OpeningCamera.age();
        if (age < SkyShot.CARD + 20 || age > SkyShot.CARD + SkyShot.FLIGHT - 40) return;
        float a = Mth.clamp((age - SkyShot.CARD - 20) / 20F, 0, 1) * 0.7F;
        String key = Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage().getString();
        String s = "Hold " + key + " to skip";
        g.text(font, s, w - font.width(s) - 14, h - 22, ((int) (a * 255) << 24) | ASH, false);
    }

    // ---- spaced capitals --------------------------------------------------------------------------------------------

    static int spacedWidth(Font font, String s, int spacing) {
        int tw = 0;
        for (int i = 0; i < s.length(); i++) tw += font.width(String.valueOf(s.charAt(i))) + spacing;
        return Math.max(0, tw - spacing);
    }

    static void spaced(GuiGraphicsExtractor g, Font font, String s, int x, int y, int spacing, int argb, float unused) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            g.text(font, c, cx, y, argb, false);
            cx += font.width(c) + spacing;
        }
    }

    static void spacedCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int spacing, int argb) {
        spaced(g, font, s, cx - spacedWidth(font, s, spacing) / 2, y, spacing, argb, 1F);
    }
}
