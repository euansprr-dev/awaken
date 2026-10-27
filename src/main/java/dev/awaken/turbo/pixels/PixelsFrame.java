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
import dev.awaken.core.api.UiHooks;
import dev.awaken.turbo.pixels.mixin.RenderTargetAccess;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * One frame of Pixels, on the render thread (SPEC §18). Around {@code GameRenderer.renderLevel()} and the post effects
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
    // the passes' labels, made once (no allocation per frame)
    private static final Supplier<String> EASU_LABEL = () -> "Pixels EASU", RCAS_LABEL = () -> "Pixels RCAS",
            SHARP_LABEL = () -> "Pixels sharp", BILINEAR_LABEL = () -> "Pixels bilinear";
    // every upscale pass writes every pixel: clearing first is free on a tile-based GPU, loading the old frame is not
    private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0f, 0f, 0f, 1f));
    // what the last frame drew, logged when it changes
    private static int loggedW, loggedH;
    private static PixelsConfig.@Nullable Upscaler loggedUpscaler;
    private static boolean loggedSteady;
    // whether this frame takes the steady pass (decided before the world draws)
    private static boolean steady;

    /** Before {@code renderLevel()}. */
    static void begin(RenderTarget main, RenderTarget hand) {
        if (lentMain != null) giveBack(); // never expected: the end hook always runs before the next frame
        beganThisFrame = true;
        PixelsTiming.stamp(PixelsTiming.BEFORE_WORLD);
        if (!proven) return;
        int w = main.width, h = main.height;
        float scale = Pixels.scale(w, h);
        int sw = Math.max(1, Math.round(w * scale)), sh = Math.max(1, Math.round(h * scale));
        // the steady pass takes the world at native resolution too (into a same-size target); it needs the world's depth
        // at the end, which the game keeps only when a post effect runs (Lens): otherwise the hand clears it first
        steady = Pixels.usable() && Pixels.steady() && !net.minecraft.client.Minecraft.getInstance().gameRenderer.getRequestedPostEffects().isEmpty();
        // its reprojection from the projection the world is really drawn with (a camera mod's roll included)
        if (steady) dev.awaken.lens.client.WorldProjection.want();
        if (!steady && (scale >= 1f || sw >= w && sh >= h) || !Pixels.usable()) {
            release();
            logIfChanged(w, h, null);
            return;
        }
        if (sw > w || sh > h) {
            sw = w;
            sh = h;
        }
        // a panel holds the player's attention (the K menu, a reading screen): Holo draws inside the world, so its text
        // would be upscaled with it; the world renders native until the panel closes (targets kept for after)
        if (UiHooks.focus() > 0f) {
            PixelsSteady.skip();
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
        PixelsTiming.stamp(PixelsTiming.AFTER_WORLD);
        if (lentMain == null) {
            PixelsSteady.skip();
            PixelsTiming.stamp(PixelsTiming.AFTER_UPSCALE);
            return;
        }
        giveBack();
        PixelsConfig.Upscaler upscaler = Pixels.upscaler();
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            // steadied first, at the world's resolution; then upscaled as ever (native: copied out, nothing sharpened that
            // wasn't before)
            GpuTextureView world;
            if (steady) {
                world = PixelsSteady.resolve(encoder, low, lowHand);
            } else {
                world = low.getColorTextureView();
                PixelsSteady.skip();
            }
            if (low.width >= main.width && low.height >= main.height) {
                pass(encoder, BILINEAR_LABEL, main.getColorTextureView(), PixelsPipelines.BILINEAR, world, FilterMode.NEAREST);
            } else if (upscaler == PixelsConfig.Upscaler.FSR) {
                easu = sized(easu, "Pixels EASU", main.width, main.height, main.getColorTexture().getFormat(), null);
                pass(encoder, EASU_LABEL, easu.getColorTextureView(), PixelsPipelines.EASU, world, FilterMode.NEAREST);
                pass(encoder, RCAS_LABEL, main.getColorTextureView(), PixelsPipelines.RCAS, easu.getColorTextureView(), FilterMode.NEAREST);
            } else {
                if (easu != null) {
                    easu.destroyBuffers();
                    easu = null;
                }
                if (upscaler == PixelsConfig.Upscaler.SHARP) {
                    pass(encoder, SHARP_LABEL, main.getColorTextureView(), PixelsPipelines.SHARP, world, FilterMode.LINEAR);
                } else {
                    pass(encoder, BILINEAR_LABEL, main.getColorTextureView(), PixelsPipelines.BILINEAR, world, FilterMode.LINEAR);
                }
            }
            PixelsTiming.stamp(PixelsTiming.AFTER_UPSCALE);
            logIfChanged(low.width, low.height, upscaler, steady);
        } catch (RuntimeException e) {
            Pixels.fail("the upscale pass failed", e);
        }
    }

    /** After the whole frame: a level that threw mid-frame must not leave the main target lent out. */
    static void frameEnd() {
        beganThisFrame = false;
        PixelsTiming.frameEnd();
        if (lentMain != null) {
            giveBack();
            Pixels.fail("the frame ended with the world still at the lower resolution", null);
        }
    }

    private static void pass(CommandEncoder encoder, Supplier<String> label, GpuTextureView out, RenderPipeline pipeline, GpuTextureView in, FilterMode filter) {
        try (RenderPass pass = encoder.createRenderPass(label, out, CLEAR)) {
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
        PixelsSteady.release();
    }

    private static void logIfChanged(int w, int h, PixelsConfig.@Nullable Upscaler upscaler) {
        logIfChanged(w, h, upscaler, false);
    }

    private static void logIfChanged(int w, int h, PixelsConfig.@Nullable Upscaler upscaler, boolean steady) {
        if (w == loggedW && h == loggedH && upscaler == loggedUpscaler && steady == loggedSteady) return;
        loggedW = w;
        loggedH = h;
        loggedUpscaler = upscaler;
        loggedSteady = steady;
        if (upscaler == null) Awaken.LOG.info("[pixels] native {}x{}", w, h);
        else Awaken.LOG.info("[pixels] world {}x{}, {}{} to the screen", w, h, steady ? "steady, " : "", upscaler);
    }
}
