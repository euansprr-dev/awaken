package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.turbo.pixels.mixin.RenderTargetAccess;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * One frame of Pixels, on the render thread (SPEC §16). Around {@code GameRenderer.renderLevel()} and the post effects
 * (Lens first), the main target and the hand's depth target lend themselves to smaller textures: their own colour and
 * depth are set aside and the low-resolution ones put in their place, on the same objects, so everything that holds
 * the main target (the level renderer, the sky, Distant Horizons, other mods) draws at the lower resolution without
 * knowing. After the post effects the real textures go back and the upscaler fills the main colour from the low one;
 * the GUI then draws on top at the screen's resolution.
 */
final class PixelsFrame {
    private PixelsFrame() {}

    // the world at the lower resolution (colour + depth, formats as the main target's), the hand's depth, and FSR's
    // intermediate (EASU's output at the screen's resolution, sharpened by RCAS into the main target)
    private static @Nullable TextureTarget low, lowHand, easu;
    // the main and hand targets' own textures while they're lent out
    private static @Nullable RenderTarget lentMain, lentHand;
    private static @Nullable GpuTexture mainColor, mainDepth, handDepth;
    private static @Nullable GpuTextureView mainColorView, mainDepthView, handDepthView;
    private static int mainW, mainH, handW, handH;
    // both hooks must be seen in one frame before the first swap, so a hook that didn't apply never leaves the frame
    // half-swapped; the begin hook's sighting this frame
    private static boolean proven, beganThisFrame;
    // what the last frame drew, logged when it changes
    private static int loggedW, loggedH;
    private static PixelsConfig.@Nullable Upscaler loggedUpscaler;

    /** Before {@code renderLevel()}. */
    static void begin(RenderTarget main, RenderTarget hand) {
        if (lentMain != null) giveBack(); // never expected: the end hook always runs before the next frame
        beganThisFrame = true;
        if (!proven) return;
        float scale = Pixels.scale();
        int w = main.width, h = main.height;
        int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
        if (scale >= 1f || sw >= w && sh >= h || !Pixels.usable()) {
            release();
            logIfChanged(w, h, null);
            return;
        }
        try {
            low = sized(low, "Pixels world", sw, sh, main.getColorTexture().getFormat(), main.getDepthTexture().getFormat());
            lowHand = sized(lowHand, "Pixels hand depth", sw, sh, null, hand.getDepthTexture().getFormat());
            lend(main, hand);
        } catch (RuntimeException e) {
            giveBack();
            Pixels.fail("could not make the low-resolution targets", e);
        }
    }

    /** After the post effects, before the GUI. */
    static void end(RenderTarget main, RenderTarget hand) {
        if (beganThisFrame && !proven) {
            proven = true;
            Awaken.LOG.info("[pixels] render hooks in place");
        }
        beganThisFrame = false;
        if (lentMain == null) return;
        giveBack();
        PixelsConfig.Upscaler upscaler = Pixels.upscaler();
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            GpuTextureView world = low.getColorTextureView();
            if (upscaler == PixelsConfig.Upscaler.FSR) {
                easu = sized(easu, "Pixels EASU", main.width, main.height, main.getColorTexture().getFormat(), null);
                pass(encoder, "Pixels EASU", easu.getColorTextureView(), PixelsPipelines.EASU, world, FilterMode.NEAREST);
                pass(encoder, "Pixels RCAS", main.getColorTextureView(), PixelsPipelines.RCAS, easu.getColorTextureView(), FilterMode.NEAREST);
            } else {
                if (easu != null) {
                    easu.destroyBuffers();
                    easu = null;
                }
                pass(encoder, "Pixels bilinear", main.getColorTextureView(), PixelsPipelines.BILINEAR, world, FilterMode.LINEAR);
            }
            logIfChanged(low.width, low.height, upscaler);
        } catch (RuntimeException e) {
            Pixels.fail("the upscale pass failed", e);
        }
    }

    /** After the whole frame: a level that threw mid-frame must not leave the main target lent out. */
    static void frameEnd() {
        beganThisFrame = false;
        if (lentMain != null) {
            giveBack();
            Pixels.fail("the frame ended with the world still at the lower resolution", null);
        }
    }

    private static void pass(CommandEncoder encoder, String label, GpuTextureView out, RenderPipeline pipeline, GpuTextureView in, FilterMode filter) {
        try (RenderPass pass = encoder.createRenderPass(() -> label, out, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("InSampler", in, RenderSystem.getSamplerCache().getClampToEdge(filter));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static TextureTarget sized(@Nullable TextureTarget t, String label, int w, int h,
                                       com.mojang.renderpearl.api.@Nullable GpuFormat color, com.mojang.renderpearl.api.@Nullable GpuFormat depth) {
        if (t == null) return new TextureTarget(label, w, h, color, depth);
        if (t.width != w || t.height != h) t.resize(w, h);
        return t;
    }

    private static void lend(RenderTarget main, RenderTarget hand) {
        mainColor = main.getColorTexture();
        mainColorView = main.getColorTextureView();
        mainDepth = main.getDepthTexture();
        mainDepthView = main.getDepthTextureView();
        mainW = main.width;
        mainH = main.height;
        handDepth = hand.getDepthTexture();
        handDepthView = hand.getDepthTextureView();
        handW = hand.width;
        handH = hand.height;
        lentMain = main;
        lentHand = hand;
        put(main, low.getColorTexture(), low.getColorTextureView(), low.getDepthTexture(), low.getDepthTextureView(), low.width, low.height);
        put(hand, null, null, lowHand.getDepthTexture(), lowHand.getDepthTextureView(), lowHand.width, lowHand.height);
    }

    private static void giveBack() {
        if (lentMain != null) put(lentMain, mainColor, mainColorView, mainDepth, mainDepthView, mainW, mainH);
        if (lentHand != null) put(lentHand, null, null, handDepth, handDepthView, handW, handH);
        lentMain = lentHand = null;
        mainColor = mainDepth = handDepth = null;
        mainColorView = mainDepthView = handDepthView = null;
    }

    private static void put(RenderTarget t, @Nullable GpuTexture color, @Nullable GpuTextureView colorView,
                            @Nullable GpuTexture depth, @Nullable GpuTextureView depthView, int w, int h) {
        RenderTargetAccess a = (RenderTargetAccess) (Object) t;
        if (color != null) {
            a.awaken$setColorTexture(color);
            a.awaken$setColorTextureView(colorView);
        }
        a.awaken$setDepthTexture(depth);
        a.awaken$setDepthTextureView(depthView);
        t.width = w;
        t.height = h;
    }

    /** Frees the low-resolution targets (the scale went back to native, or Pixels stepped aside). */
    static void release() {
        if (low != null) low.destroyBuffers();
        if (lowHand != null) lowHand.destroyBuffers();
        if (easu != null) easu.destroyBuffers();
        low = lowHand = easu = null;
    }

    private static void logIfChanged(int w, int h, PixelsConfig.@Nullable Upscaler upscaler) {
        if (w == loggedW && h == loggedH && upscaler == loggedUpscaler) return;
        loggedW = w;
        loggedH = h;
        loggedUpscaler = upscaler;
        if (upscaler == null) Awaken.LOG.info("[pixels] native {}x{}", w, h);
        else Awaken.LOG.info("[pixels] world {}x{}, {} to the screen", w, h, upscaler);
    }
}
