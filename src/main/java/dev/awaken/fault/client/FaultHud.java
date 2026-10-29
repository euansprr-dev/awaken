package dev.awaken.fault.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.fault.Fault;
import dev.awaken.fault.FaultModule;
import dev.awaken.opening.client.OpeningCamera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Draws and sounds a Fault (SPEC §43.8; JOURNEY part 5, "The kit"). The tell: the world's sound drops out, the HUD blinks
 * once, a bell note pitched wrong. The break: the frame tears into bands that slide sideways with red and cyan split at
 * their edges, the first line corrupting mid-word, then a hard cut to black. The words typed large in the black, each
 * glyph arriving broken and settling. The held cut through the opening's camera, letterboxed. Back: the plain line, and
 * the screen's edge flickering faintly until the mend's clean sweep. Band layouts change at most every seven ticks
 * (under three a second) and slide rather than strobe; GENTLE fades instead of tearing; STILL is a plain card.
 */
final class FaultHud {
    private FaultHud() {}

    private static final Identifier LAYER = Awaken.id("fault");
    private static final Object OWNER = "fault";
    private static final String GLYPHS = "▮▯░▒▓#%&@/\\|<>";
    /** A STILL card stays this long; the edge gives up after this long without a mend. */
    private static final int CARD = 110, EDGE_MAX = 24000;
    private static final int RED = 0xFF4050, CYAN = 0x40E0FF;

    private static @Nullable Fault fault;
    private static FaultConfig.Mode mode = FaultConfig.Mode.FULL;
    private static long ticks, start, edgeFrom, mendFrom = -1000, afterFrom = -1000;
    private static boolean edge, mendPending, camOurs;
    private static String after = "";
    private static int[] lineStart = new int[0];
    private static int tellEnd, tearEnd, blackEnd, cutEnd;
    private static long seed;

    static void register(RegisterGuiLayersEvent e) {
        e.registerAboveAll(LAYER, FaultHud::render);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, FaultHud::hideHud);
    }

    static void reset() {
        endCut();
        fault = null;
        edge = mendPending = false;
        afterFrom = mendFrom = -1000;
        lens(false);
    }

    static void play(Fault f) {
        if (Minecraft.getInstance().player == null) return;
        endCut();
        fault = f;
        start = ticks;
        mode = FaultConfig.mode();
        after = f.after();
        edge = mendPending = false;
        afterFrom = -1000;
        seed = RandomSource.create().nextLong();
        if (mode == FaultConfig.Mode.STILL) {
            tellEnd = tearEnd = blackEnd = 0;
            cutEnd = f.size() == Fault.Size.FLICKER ? 0 : CARD;
        } else {
            tellEnd = f.size().tell;
            tearEnd = tellEnd + f.size().tear;
            blackEnd = tearEnd + f.blackTicks();
            cutEnd = blackEnd + f.cutTicks();
        }
        // each line starts after the last is typed, with a beat between; the leak after a longer one
        List<String> lines = lines(f);
        lineStart = new int[lines.size()];
        int at = tearEnd + 8;
        for (int i = 0; i < lines.size(); i++) {
            if (i == f.words().size()) at += Fault.LINE_BEAT * 2;
            lineStart[i] = at;
            at += (int) (lines.get(i).length() / Fault.TYPE_RATE) + Fault.LINE_BEAT;
        }
        if (f.size() == Fault.Size.FLICKER) sound(FaultModule.FLICKER.get(), 1F, 0.8F);
        else if (mode == FaultConfig.Mode.STILL) sound(FaultModule.VOICE.get(), 0.9F, 0.6F);
        else {
            sound(FaultModule.TELL.get(), 1F, 1F);
            Soundscape.muffle(OWNER, 420F, 0.05F, cutEnd + 10);
        }
    }

    static void mend() {
        if (fault != null) {
            mendPending = true;
            return;
        }
        edge = false;
        mendFrom = ticks;
        sound(FaultModule.MEND.get(), 1F, 1F);
    }

    static void tick() {
        ticks++;
        if (edge && ticks - edgeFrom > EDGE_MAX) edge = false;
        Fault f = fault;
        if (f == null) return;
        long t = ticks - start;
        if (mode != FaultConfig.Mode.STILL) {
            if (t == tellEnd && f.size() != Fault.Size.FLICKER) {
                if (mode == FaultConfig.Mode.FULL) sound(FaultModule.TEAR.get(), 1F, 1F);
                lens(true);
            }
            if (t == tearEnd && f.size() != Fault.Size.FLICKER) sound(FaultModule.DROP.get(), 1F, 1F);
            for (int i = 0; i < lineStart.length; i++)
                if (t == lineStart[i]) sound(FaultModule.VOICE.get(), i == f.words().size() ? 0.7F : 1F + 0.06F * i, i == f.words().size() ? 0.6F : 1F);
            if (t == blackEnd && f.cutTicks() > 0) startCut(f);
        }
        if (t >= Math.max(cutEnd, f.size() == Fault.Size.FLICKER ? f.size().tear : 0)) {
            endCut();
            lens(false);
            fault = null;
            if (!after.isEmpty()) afterFrom = ticks;
            if (f.holds() && mode != FaultConfig.Mode.STILL && !mendPending) {
                edge = true;
                edgeFrom = ticks;
            }
            if (mendPending) {
                mendPending = false;
                mend();
            }
        }
    }

    private static List<String> lines(Fault f) {
        if (f.leak().isEmpty()) return f.words();
        List<String> out = new java.util.ArrayList<>(f.words());
        out.add(f.leak());
        return out;
    }

    // ---- the held cut ---------------------------------------------------------------------------------------------

    private static void startCut(Fault f) {
        Vec3 from = f.camFrom(), at = f.camAt();
        if (from == null || at == null) return;
        int len = f.cutTicks();
        Vec3 dir = at.subtract(from).normalize();
        OpeningCamera.take(new OpeningCamera.Shot() {
            @Override
            public void pose(float t, double[] out) {
                // a slow push toward what's happening
                Vec3 p = from.add(dir.scale(2.5 * Math.min(1F, t / len)));
                double dx = at.x - p.x, dy = at.y - p.y, dz = at.z - p.z;
                out[0] = p.x;
                out[1] = p.y;
                out[2] = p.z;
                out[3] = Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0;
                out[4] = -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
                out[5] = 0;
                out[6] = 1;
            }

            @Override
            public int length() {
                return len;
            }

            @Override
            public void finished(String reason) {
                camOurs = false;
            }
        }, 0);
        camOurs = OpeningCamera.active();
    }

    private static void endCut() {
        if (camOurs && OpeningCamera.active()) OpeningCamera.finish("done");
        camOurs = false;
    }

    private static void lens(boolean on) {
        try {
            if (on && mode == FaultConfig.Mode.FULL) dev.awaken.lens.client.LensClient.ritual(0.55F, 0.2F);
            else dev.awaken.lens.client.LensClient.ritual(0F, 0F);
        } catch (RuntimeException | LinkageError ignored) {
            // Lens off or absent: the Fault draws on its own
        }
    }

    private static void sound(SoundEvent e, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    private static void hideHud(RenderGuiLayerEvent.Pre e) {
        Fault f = fault;
        if (f == null || e.getName().equals(LAYER) || mode == FaultConfig.Mode.STILL) return;
        String path = e.getName().getPath();
        if (path.equals("chat") || path.equals("subtitle_overlay")) return;
        long t = ticks - start;
        boolean blink = f.size() != Fault.Size.FLICKER && t >= 3 && t < 5;
        if (blink || (t >= tearEnd - 1 && t < cutEnd && f.size() != Fault.Size.FLICKER)) e.setCanceled(true);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        float now = ticks + pt;
        Fault f = fault;
        if (f != null) {
            float t = now - start;
            if (mode == FaultConfig.Mode.STILL) card(g, mc.font, w, h, f, t);
            else if (f.size() == Fault.Size.FLICKER) tear(g, mc.font, w, h, f, t / f.size().tear, t, 0.45F);
            else if (t < tellEnd) tell(g, w, h, t / Math.max(1, tellEnd));
            else if (t < tearEnd) {
                float k = (t - tellEnd) / Math.max(1, tearEnd - tellEnd);
                if (mode == FaultConfig.Mode.FULL) tear(g, mc.font, w, h, f, k, t, 1F);
                else g.fill(0, 0, w, h, ((int) (Mth.clamp(k * 1.2F, 0F, 1F) * 255) << 24));
                if (t >= tearEnd - 2) g.fill(0, 0, w, h, 0xFF000000);
            } else if (t < blackEnd) {
                g.fill(0, 0, w, h, 0xFF000000);
                words(g, mc.font, w, h, f, t);
            } else if (t < cutEnd) cut(g, w, h, t - blackEnd, cutEnd - blackEnd);
        }
        afterLine(g, mc.font, w, h, now);
        if (edge) edge(g, w, h, now);
        sweep(g, w, h, now);
    }

    /** The tell: the frame darkens in from its edges. */
    private static void tell(GuiGraphicsExtractor g, int w, int h, float k) {
        int a = (int) (Mth.clamp(k, 0F, 1F) * 120);
        int band = h / 4;
        g.fillGradient(0, 0, w, band, a << 24, 0);
        g.fillGradient(0, h - band, w, h, 0, a << 24);
    }

    /** The break: bands torn and slid sideways, red and cyan split at their edges, the first line corrupting. */
    private static void tear(GuiGraphicsExtractor g, Font font, int w, int h, Fault f, float k, float t, float strength) {
        k = Mth.clamp(k, 0F, 1F) * strength;
        // a new layout at most every seven ticks: the bands move between, they never strobe
        RandomSource r = RandomSource.create(seed + (long) (t / 7F));
        int n = 7 + (int) (k * 7);
        for (int i = 0; i < n; i++) {
            int y0 = r.nextInt(Math.max(1, h));
            int bh = 3 + r.nextInt(Math.max(4, (int) (h * 0.07F)));
            float phase = r.nextFloat() * 6.28F;
            int dx = (int) (Math.sin(t * 0.55F + phase) * (6 + 46 * k));
            int dark = (int) ((0.25F + 0.6F * k) * 255 * r.nextFloat());
            g.fill(Math.max(0, dx), y0, Math.min(w, w + dx), y0 + bh, dark << 24);
            int split = 2 + (int) (5 * k);
            g.fill(Math.max(0, dx + split), y0, Math.min(w, w + dx + split), y0 + 1, ((int) (150 * k) << 24) | RED);
            g.fill(Math.max(0, dx - split), y0 + bh - 1, Math.min(w, w + dx - split), y0 + bh, ((int) (150 * k) << 24) | CYAN);
            // a few blocks of torn data
            if (r.nextFloat() < 0.5F * k) {
                int bx = r.nextInt(Math.max(1, w)), bw = 6 + r.nextInt(40);
                g.fill(bx, y0, Math.min(w, bx + bw), y0 + Math.min(bh, 4), ((int) (110 * k) << 24) | (r.nextBoolean() ? 0xE8F4FF : CYAN));
            }
        }
        // the first line breaking through, corrupting mid-word
        if (!f.words().isEmpty() && k > 0.35F) {
            String line = corrupt(f.words().getFirst(), t, 0.35F, r);
            int tw = font.width(line);
            int x = w / 2 - tw / 2, y = h / 2 - 4;
            int a = (int) (Mth.clamp((k - 0.35F) * 3F, 0F, 1F) * 220) << 24;
            g.text(font, line, x - 2, y, a | RED, false);
            g.text(font, line, x + 2, y, a | CYAN, false);
            g.text(font, line, x, y, a | 0xF2F6FF, false);
        }
    }

    /** The words in the black: large, each glyph arriving broken and settling; the leak smaller and grey. */
    private static void words(GuiGraphicsExtractor g, Font font, int w, int h, Fault f, float t) {
        List<String> lines = lines(f);
        float scale = Mth.clamp(w / 240F, 1.5F, 3F);
        int lineH = (int) (font.lineHeight * scale * 1.55F);
        int y = (int) (h * 0.42F) - lineH * f.words().size() / 2;
        boolean full = mode == FaultConfig.Mode.FULL;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean leak = i == f.words().size();
            float s = leak ? scale * 0.55F : scale;
            float since = t - lineStart[i];
            if (since < 0) break;
            int shown = Math.min(line.length(), (int) (since * Fault.TYPE_RATE) + 1);
            StringBuilder b = new StringBuilder(shown);
            RandomSource r = RandomSource.create(seed + i * 31L + (long) (t / 2F));
            for (int j = 0; j < shown; j++) {
                float age = since - j / Fault.TYPE_RATE;
                char c = line.charAt(j);
                b.append(full && age < 3F && c != ' ' ? GLYPHS.charAt(r.nextInt(GLYPHS.length())) : c);
            }
            if (shown < line.length() && ((int) (t / 8)) % 2 == 0) b.append('▮');
            String text = b.toString();
            int tw = font.width(line);
            if (leak) y += lineH / 2;
            g.pose().pushMatrix();
            g.pose().translate(w / 2F, y);
            g.pose().scale(s, s);
            int x0 = -tw / 2;
            if (full && !leak) {
                float jx = (float) Math.sin(t * 0.9F + i) * 0.8F;
                g.text(font, text, x0 - 1 - (int) jx, 0, 0x90000000 | RED, false);
                g.text(font, text, x0 + 1 + (int) jx, 0, 0x90000000 | CYAN, false);
            }
            g.text(font, text, x0, 0, leak ? 0xFF9AA3AD : 0xFFF2F6FF, false);
            // "[ERROR]" in the System's red
            if (!leak && text.startsWith("[ERROR]")) g.text(font, "[ERROR]", x0, 0, 0xFF000000 | RED, false);
            g.pose().popMatrix();
            y += leak ? (int) (lineH * 0.6F) : lineH;
        }
    }

    /** The held cut: letterboxed, the black lifting off it, a dip to black before you're back. */
    private static void cut(GuiGraphicsExtractor g, int w, int h, float t, int len) {
        int bh = (int) (h * 0.11F);
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
        float black = Math.max(1F - t / 6F, (t - (len - 4)) / 4F);
        if (black > 0) g.fill(0, 0, w, h, ((int) (Mth.clamp(black, 0F, 1F) * 255) << 24));
    }

    /** STILL: a plain card with the words and the line after. */
    private static void card(GuiGraphicsExtractor g, Font font, int w, int h, Fault f, float t) {
        float a = Mth.clamp(t / 8F, 0F, 1F) * Mth.clamp((CARD - t) / 12F, 0F, 1F);
        if (a <= 0.02F) return;
        List<String> lines = new java.util.ArrayList<>(f.words());
        if (!f.after().isEmpty()) lines.add(f.after());
        int cw = 0;
        for (String l : lines) cw = Math.max(cw, font.width(l));
        cw += 24;
        int ch = lines.size() * 12 + 14, x = w / 2 - cw / 2, y = (int) (h * 0.22F);
        int alpha = (int) (a * 200);
        g.fill(x, y, x + cw, y + ch, (alpha << 24) | 0x0E1216);
        g.fill(x, y, x + cw, y + 1, ((int) (a * 255) << 24) | CYAN);
        for (int i = 0; i < lines.size(); i++) {
            int col = i < f.words().size() ? 0xF2F6FF : 0xC9D6E2;
            g.centeredText(font, net.minecraft.network.chat.Component.literal(lines.get(i)), w / 2, y + 8 + i * 12, ((int) (a * 255) << 24) | col);
        }
        // after a STILL card, the plain line has already been read
        if (t >= CARD - 1) after = "";
    }

    /** Back: the System's words plain again. */
    private static void afterLine(GuiGraphicsExtractor g, Font font, int w, int h, float now) {
        float t = now - afterFrom;
        if (after.isEmpty() || t < 0 || t > 140) return;
        float a = Mth.clamp(t / 8F, 0F, 1F) * Mth.clamp((140 - t) / 18F, 0F, 1F);
        if (a <= 0.02F) return;
        int tw = font.width(after) + 24, x = w / 2 - tw / 2, y = (int) (h * 0.18F);
        g.fill(x, y, x + tw, y + 20, ((int) (a * 190) << 24) | 0x0E1216);
        g.fill(x, y, x + tw, y + 1, ((int) (a * 255) << 24) | CYAN);
        g.centeredText(font, net.minecraft.network.chat.Component.literal(after), w / 2, y + 6, ((int) (a * 255) << 24) | 0xE8F4FF);
    }

    /** Until the mend: the screen's edge keeps a faint, slow flicker, and now and then slips. */
    private static void edge(GuiGraphicsExtractor g, int w, int h, float now) {
        float wave = (float) (Math.sin(now * 0.21F) * 0.5 + Math.sin(now * 0.067F + 1.3F) * 0.5);
        int a = (int) ((0.18F + 0.14F * wave) * 255 * (mode == FaultConfig.Mode.FULL ? 1F : 0.6F));
        g.fill(0, 0, 2, h, (a << 24) | CYAN);
        g.fill(w - 2, 0, w, h, (a << 24) | RED);
        float slip = now % 70F;
        if (slip < 5F && mode == FaultConfig.Mode.FULL) {
            int y = (int) ((Math.sin(Math.floor(now / 70F) * 12.9898) * 0.5 + 0.5) * (h - 10));
            g.fill(0, y, 46, y + 3, (a << 24) | CYAN);
            g.fill(w - 46, y + 5, w, y + 7, (a << 24) | RED);
        }
    }

    /** The mend: one clean sweep of light across the screen. */
    private static void sweep(GuiGraphicsExtractor g, int w, int h, float now) {
        float s = (now - mendFrom) / 18F;
        if (s < 0F || s > 1.4F) return;
        int x = (int) (s * w);
        for (int k = 0; k < 12; k++) {
            int a = (int) (110 * (1F - k / 12F) * Mth.clamp(1.4F - s, 0F, 1F));
            int x0 = x - (k + 1) * 7;
            g.fill(Math.max(0, x0), 0, Math.max(0, Math.min(w, x0 + 7)), h, (a << 24) | (k < 3 ? 0xFFFFFF : 0xCFF6FF));
        }
        g.fill(Math.max(0, x - 1), 0, Math.min(w, x + 1), h, ((int) (220 * Mth.clamp(1.4F - s, 0F, 1F)) << 24) | 0xFFFFFF);
    }

    private static String corrupt(String s, float t, float rate, RandomSource r) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c != ' ' && r.nextFloat() < rate ? GLYPHS.charAt(r.nextInt(GLYPHS.length())) : c);
        }
        return b.toString();
    }
}
