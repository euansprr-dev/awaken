package dev.awaken.core.client;

import dev.awaken.Awaken;
import dev.awaken.core.CorePayloads;
import dev.awaken.core.api.Notices;
import dev.awaken.core.api.Passage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The passage on the client (SPEC §28.9, "Arrivals"): while one of Awaken's teleports waits for its land, the screen
 * sinks into a deep dusk where a pale line of land draws itself out from the middle, faint motes settling into it, and
 * the player's input is held ({@link InputLock}; paced notices wait too). Once they're moved the dusk lifts. The world
 * keeps running behind it, so it never reads as a frozen frame. Its own look: not the gates' fall, not a loading screen.
 * Client only; nothing allocated a frame.
 */
public final class PassageClient {
    private PassageClient() {}

    static final long IN_MS = 350, OUT_MS = 900, WORDS_MS = 1600;
    private static final int MOTES = 72, SEGMENTS = 40;
    private static final int DUSK = 0x04070C, GLOW = 0x6E8AA0, LINE = 0xD8E6EE;
    private static final String WORDS = "The land where you're going is still forming.";
    private static final Object HOLD = new Object() {
        @Override
        public String toString() {
            return "passage";
        }
    };
    private static long from = -1, lift = -1;

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(CorePayloads.PassageWait.TYPE, (msg, ctx) -> waiting(msg.on())));
        // over the whole HUD but chat, like the other veils
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerBelow(VanillaGuiLayers.CHAT, Awaken.id("passage"), PassageClient::render));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
    }

    private static void waiting(boolean on) {
        long now = Util.getMillis();
        if (on) {
            // from a dusk still lifting, it closes again from where it is
            if (from < 0) from = now;
            else if (lift >= 0) from = now - (long) (shade(now) * IN_MS);
            lift = -1;
            InputLock.hold(HOLD, (int) (Passage.MOST_MS / 50) + 200);
            Notices.client().hold(HOLD, true);
        } else {
            if (from >= 0 && lift < 0) lift = now;
            InputLock.release(HOLD);
            Notices.client().hold(HOLD, false);
        }
    }

    /** Whether a passage is up and not yet lifting: the player is still waiting for their land (tours wait on it). */
    public static boolean waiting() {
        return from >= 0 && lift < 0;
    }

    private static void reset() {
        if (from >= 0) Notices.client().hold(HOLD, false);
        from = lift = -1;
    }

    /** How dark it is now, 0..1: in over {@link #IN_MS}, out over {@link #OUT_MS}. */
    private static float shade(long now) {
        if (from < 0) return 0;
        float a = smooth((now - from) / (float) IN_MS);
        if (lift >= 0) a = Math.min(a, 1 - smooth((now - lift) / (float) OUT_MS));
        return a;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (from < 0) return;
        long now = Util.getMillis();
        if (lift >= 0 && now - lift >= OUT_MS) {
            from = lift = -1;
            return;
        }
        float a = shade(now);
        if (a <= 0) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = (now - from) / 1000F;
        g.fill(0, 0, w, h, argb(a, DUSK));
        // a breath of cold light round the horizon, where the land is drawing itself
        int mid = (int) (h * 0.56F);
        float breath = 0.78F + 0.22F * Mth.sin(t * 2.1F);
        g.fillGradient(0, mid - h / 3, w, mid, argb(0, GLOW), argb(a * 0.11F * breath, GLOW));
        g.fillGradient(0, mid, w, mid + h / 4, argb(a * 0.11F * breath, GLOW), argb(0, GLOW));
        // the line of land: out from the middle, nearly across after a few seconds, soft at its ends
        float reach = 1 - (float) Math.exp(-t / 2.4F);
        float half = w * 0.5F * (0.06F + 0.9F * reach);
        for (int i = 0; i < SEGMENTS; i++) {
            float u0 = (float) i / SEGMENTS, u1 = (float) (i + 1) / SEGMENTS;
            float end = 1 - Math.abs((u0 + u1) - 1);
            float k = a * breath * end * end;
            int x0 = (int) (w * 0.5F - half + 2 * half * u0), x1 = (int) (w * 0.5F - half + 2 * half * u1);
            g.fillGradient(x0, mid - 3, x1, mid, argb(0, LINE), argb(k * 0.3F, LINE));
            g.fill(x0, mid, x1, mid + 1, argb(k * 0.9F, LINE));
            g.fillGradient(x0, mid + 1, x1, mid + 4, argb(k * 0.3F, LINE), argb(0, LINE));
        }
        // motes settling into it from above and below, brighter where the line has reached
        for (int i = 0; i < MOTES; i++) {
            float hx = hash(i * 3 + 1), hy = hash(i * 3 + 2), hs = hash(i * 3 + 3);
            float period = 2.2F + 2.4F * hs;
            float k = ((t + hy * period) % period) / period;
            float off = (0.08F + 0.34F * hy) * h * (1 - k * (2 - k));
            int y = (int) (hs > 0.5F ? mid - off : mid + 1 + off);
            float dx = (hx - 0.5F) * w * 0.96F * (1 - 0.12F * k);
            int x = (int) (w * 0.5F + dx);
            float near = Math.abs(dx) < half ? 1 : 0.35F;
            float ma = a * (0.12F + 0.4F * hs) * Mth.sin((float) Math.PI * k) * near * Mth.clamp(t * 2.5F, 0, 1);
            g.fill(x, y, x + 1, y + (hs > 0.85F ? 2 : 1), argb(ma, hs > 0.7F ? LINE : GLOW));
        }
        // a plain word if it takes a moment
        if (now - from > WORDS_MS) {
            Font font = Minecraft.getInstance().font;
            float wa = a * 0.7F * smooth((now - from - WORDS_MS) / 800F);
            if (wa > 0.02F) g.text(font, WORDS, (w - font.width(WORDS)) / 2, mid + 14, argb(wa, 0xB8C6CE), false);
        }
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
