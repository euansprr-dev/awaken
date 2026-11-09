package dev.awaken.gates.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Locale;

/**
 * The crossing between the overworld and a gate (SPEC §50.5): a tear opens in the dark with the gate's painting behind it,
 * the name surfaces, and when the land has loaded the painting burns away into the real place. The server says what's being
 * crossed into just before the move ({@link GatePayloads.Crossing}); any other trip through these dimensions (a death, a
 * command) gets a plain dark screen, and the Journey between a gate's own floors shows through untouched.
 */
public final class Crossing {
    private Crossing() {}

    static final long MIN_IN_MS = 2800, MIN_OUT_MS = 1500, FRESH_MS = 8000, CLOSE_MS = 320, AFTERGLOW_MS = 1150;
    private static final String FALLBACK = "default";

    static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Awaken.id("pipeline/gate_crossing"))
            .withFragmentShader(Awaken.id("core/gate_crossing"))
            .build();

    private static int shaderOk = -1;

    private static GatePayloads.Crossing told;
    private static long toldAt;
    private static Identifier art;
    private static long afterglowFrom = -1, afterglowClock;

    static void pipelines(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(PIPELINE);
    }

    static void screens(RegisterDimensionTransitionScreenEvent e) {
        ResourceKey<Level> gates = GatesRegistry.GATES, sky = GatesRegistry.GATES_SKY;
        // a gate's own floors already have the Journey: nothing may cover it
        e.registerConditionalEffect(gates, sky, Dark::new);
        e.registerConditionalEffect(sky, gates, Dark::new);
        for (ResourceKey<Level> dim : java.util.List.of(gates, sky)) {
            e.registerIncomingEffect(dim, Crossing::screen);
            e.registerOutgoingEffect(dim, Crossing::screen);
        }
    }

    private static LevelLoadingScreen screen(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason) {
        boolean fresh = told != null && Util.getMillis() - toldAt < FRESH_MS;
        GatePayloads.Crossing c = fresh ? told : null;
        told = null;
        return c == null ? new Dark(tracker, reason) : new Scene(tracker, reason, c, art, c.entering() ? MIN_IN_MS : MIN_OUT_MS, -1);
    }

    /** The server says what's next. The painting loads now, so it's ready the moment the screen opens. */
    static void told(GatePayloads.Crossing c) {
        Minecraft mc = Minecraft.getInstance();
        Identifier id = artFor(c.key());
        mc.getTextureManager().getTexture(id);
        if (c.frozenMs() >= 0) {
            // a look at one instant of it, for tours (the land isn't loading, so nothing closes it but time)
            mc.setScreenAndShow(new Scene(new LevelLoadTracker(), LevelLoadingScreen.Reason.OTHER, c, id, 0, c.frozenMs()));
            return;
        }
        told = c;
        toldAt = Util.getMillis();
        art = id;
    }

    private static Identifier artFor(String key) {
        Identifier id = Awaken.id("textures/gui/crossing/" + key + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? id : Awaken.id("textures/gui/crossing/" + FALLBACK + ".png");
    }

    private static boolean shader() {
        if (shaderOk < 0) {
            try {
                shaderOk = RenderSystem.getCompiledPipelineNullable(PIPELINE) != null ? 1 : 0;
            } catch (RuntimeException e) {
                shaderOk = 0;
            }
            if (shaderOk == 0) Awaken.LOG.warn("[gates] the crossing's shader did not load; its painting fades instead");
        }
        return shaderOk == 1;
    }

    /** Draws the painting across the screen at this moment of the clock. */
    static void paint(GuiGraphicsExtractor g, Identifier id, long clockMs, float reveal, float keep) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (shader()) {
            int ticks = (int) ((clockMs / 2) & 0xFFFF);
            int color = Math.max(1, Math.round(Mth.clamp(keep, 0, 1) * 255)) << 24 | (ticks >> 8) << 16 | (ticks & 0xFF) << 8 | Math.round(Mth.clamp(reveal, 0, 1) * 255);
            g.blit(PIPELINE, id, 0, 0, 0F, 0F, w, h, w, h, w, h, color);
        } else {
            float a = Math.min(keep, reveal);
            int color = Math.max(1, Math.round(a * 255)) << 24 | 0xFFFFFF;
            float art = 16F / 9F, screen = w / (float) h;
            float us = screen > art ? 1 : screen / art, vs = screen > art ? art / screen : 1;
            g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, (1 - us) / 2 * 1024, (1 - vs) / 2 * 1024, w, h, (int) (us * 1024), (int) (vs * 1024), 1024, 1024, color);
        }
    }

    /** The tick after the screen closes: the painting is still up, and the world comes through it. */
    static void afterglow(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (afterglowFrom < 0 || art == null) return;
        long age = Util.getMillis() - afterglowFrom;
        if (age >= AFTERGLOW_MS) {
            afterglowFrom = -1;
            return;
        }
        float k = age / (float) AFTERGLOW_MS;
        paint(g, art, afterglowClock + age, 1, 1 - k * k * (3 - 2 * k));
    }

    static void reset() {
        told = null;
        afterglowFrom = -1;
    }

    private static int argb(float a, int rgb) {
        return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (rgb & 0xFFFFFF);
    }

    // ---- the screens --------------------------------------------------------------------------------------------------

    /** Nothing drawn at all but black: what's under it (a floor's Journey, or the dark) stays. */
    private static final class Dark extends LevelLoadingScreen {
        private LevelLoadTracker tracker;

        Dark(LevelLoadTracker tracker, Reason reason) {
            super(tracker, reason);
            this.tracker = tracker;
        }

        @Override
        public void update(LevelLoadTracker tracker, Reason reason) {
            super.update(tracker, reason);
            this.tracker = tracker;
        }

        @Override
        public void tick() {
            if (tracker.isLevelReady()) onClose();
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}
    }

    private static final class Scene extends LevelLoadingScreen {
        private static final int GOLD = 0xE8C98A, PALE = 0xFFF6E4;

        private LevelLoadTracker tracker;
        private final GatePayloads.Crossing c;
        private final Identifier id;
        private final long minMs, frozen, from = Util.getMillis();
        private final String title, kicker;
        private float shownProgress;
        private long closingAt = -1;

        Scene(LevelLoadTracker tracker, Reason reason, GatePayloads.Crossing c, Identifier id, long minMs, long frozen) {
            super(tracker, reason);
            this.tracker = tracker;
            this.c = c;
            this.id = id;
            this.minMs = minMs;
            this.frozen = frozen;
            this.title = c.title().toUpperCase(Locale.ROOT);
            this.kicker = c.kicker().toUpperCase(Locale.ROOT);
        }

        @Override
        public void update(LevelLoadTracker tracker, Reason reason) {
            super.update(tracker, reason);
            this.tracker = tracker;
        }

        private long clock() {
            return frozen >= 0 ? frozen : Util.getMillis() - from;
        }

        @Override
        public void tick() {
            shownProgress += (tracker.serverProgress() - shownProgress) * 0.2F;
            long now = Util.getMillis();
            if (frozen >= 0) {
                if (now - from > 8000) onClose();
                return;
            }
            if (closingAt < 0 && tracker.isLevelReady() && now - from >= minMs) closingAt = now;
            if (closingAt >= 0 && now - closingAt >= CLOSE_MS) {
                afterglowFrom = now;
                afterglowClock = clock();
                art = id;
                onClose();
            }
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            long t = clock();
            paint(g, id, t, Mth.clamp((t - 110) / 880F, 0, 1), 1);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
            long t = clock();
            float words = closingAt < 0 ? 1 : 1 - Mth.clamp((Util.getMillis() - closingAt) / (float) CLOSE_MS, 0, 1);
            if (words <= 0) return;
            int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2;
            int y = (int) (h * 0.665F);
            float scale = Mth.clamp(w / 230F, 1.6F, 3.6F);

            float kickerIn = Mth.clamp((t - 620) / 420F, 0, 1);
            if (!kicker.isEmpty() && kickerIn > 0) spaced(g, kicker, cx, y - 15, 2, argb(kickerIn * 0.92F * words, GOLD));

            // the name surfaces letter by letter from its middle
            float surf = (t - 780) / 900F;
            int spacing = 3, total = 0, n = title.length();
            for (int i = 0; i < n; i++) total += font.width(String.valueOf(title.charAt(i))) + spacing;
            float px = -(total - spacing) / 2F;
            float mid = (n - 1) / 2F;
            for (int i = 0; i < n; i++) {
                String ch = String.valueOf(title.charAt(i));
                float dist = mid < 0.5F ? 0 : Math.abs(i - mid) / mid;
                float la = Mth.clamp((surf - dist * 0.55F) / 0.45F, 0, 1);
                if (la > 0) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx + px * scale, y + (1 - la) * 7);
                    g.pose().scale(scale, scale);
                    g.text(font, ch, 0, 0, argb(la * words, PALE), true);
                    g.pose().popMatrix();
                }
                px += font.width(ch) + spacing;
            }

            int ruleY = (int) (y + 9 * scale + 7);
            float draw = Mth.clamp((t - 1200) / 700F, 0, 1);
            int half = (int) ((1 - (1 - draw) * (1 - draw)) * Math.min(w * 0.3F, total * scale * 0.68F));
            if (half > 0) {
                g.fillGradient(cx - half, ruleY, cx, ruleY + 1, argb(0, GOLD), argb(0.9F * words, GOLD));
                g.fillGradient(cx, ruleY, cx + half, ruleY + 1, argb(0.9F * words, GOLD), argb(0, GOLD));
            }
            float lineIn = Mth.clamp((t - 1500) / 620F, 0, 1);
            if (!c.line().isEmpty() && lineIn > 0) g.centeredText(font, c.line(), cx, ruleY + 8, argb(lineIn * 0.9F * words, PALE));

            // how far the land has got, a hair of light in the bottom bar
            int barY = h - (int) (h * 0.039F);
            int reach = (int) (w * 0.16F);
            float shown = Math.max(shownProgress, Mth.clamp(t / 3500F, 0, 0.95F));
            int lit = (int) (reach * shown);
            float barIn = Mth.clamp((t - 500) / 400F, 0, 1);
            g.fill(cx - reach, barY, cx + reach, barY + 1, argb(0.16F * barIn * words, PALE));
            g.fill(cx - lit, barY, cx + lit, barY + 1, argb(0.9F * barIn * words, GOLD));
        }

        private void spaced(GuiGraphicsExtractor g, String s, int cx, int y, int spacing, int argb) {
            int total = 0;
            for (int i = 0; i < s.length(); i++) total += font.width(String.valueOf(s.charAt(i))) + spacing;
            int x = cx - (total - spacing) / 2;
            for (int i = 0; i < s.length(); i++) {
                String ch = String.valueOf(s.charAt(i));
                g.text(font, ch, x, y, argb, true);
                x += font.width(ch) + spacing;
            }
        }
    }
}
