package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.lens.client.WorldProjection;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pixels' steady pass (SPEC §18): the world's image held steady while the camera moves. Detail near a pixel's size (far
 * terrain's block columns, far crowns, crop rows, thin spires) lands on different pixels as the camera breathes, so it
 * flickers frame to frame; sharpening made it worse. Each frame the last frame's steady image is carried to where it
 * now lies on screen (through the depth: the camera's own motion, exact for the sway's turns), kept within what this
 * frame's own pixels around it allow (so a moving mob or a new view never smears), and this frame blended in at a
 * tenth: what flickered settles on its average, what stood still stays sharp. At the world's resolution, before the
 * upscale, which then sharpens a steady image (and adds no flicker). Runs at native resolution too (the world then
 * draws into a same-size target, copied out unsharpened). Without jitter it steadies, it doesn't add resolution.
 */
final class PixelsSteady {
    private PixelsSteady() {}

    /** This frame's share of the steady image. */
    static final float BLEND = 0.1f;

    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().get();
    private static @Nullable MappableRingBuffer ubo;
    // the steady image, this frame's and last frame's (the world's resolution, 10 bits a channel: a tenth's steps
    // don't round away as they would in 8)
    private static @Nullable TextureTarget a, b;
    private static boolean flip;
    private static final Supplier<String> LABEL = () -> "Pixels steady";
    private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0f, 0f, 0f, 1f));

    // this frame's matrices (AfterLevel) and last frame's
    private static final Matrix4f proj = new Matrix4f(), bob = new Matrix4f(), viewProj = new Matrix4f(), inverse = new Matrix4f(),
            prevViewProj = new Matrix4f(), reproject = new Matrix4f();
    private static double camX, camY, camZ, prevX, prevY, prevZ;
    private static long prevNs;
    private static boolean captured, hasPrev, zeroToOne, zeroToOneKnown;

    /**
     * In AfterLevel: the CPU's estimate of the matrices this frame drew the world with (the game's bob and hurt tilt), for
     * when the world's own (WorldProjection, a camera mod's roll included) aren't there.
     */
    static void capture(RenderLevelStageEvent.AfterLevel e) {
        CameraRenderState cam = e.getLevelRenderState().cameraRenderState;
        proj.set(cam.projectionMatrix).mul(WorldProjection.vanillaBob(cam, bob));
        viewProj.set(proj).mul(cam.viewRotationMatrix);
        camX = cam.pos.x;
        camY = cam.pos.y;
        camZ = cam.pos.z;
        captured = true;
    }

    /** A frame without the steady pass (native, a Holo panel, off): the next one starts afresh. */
    static void skip() {
        hasPrev = false;
        captured = false;
    }

    /**
     * Fills this frame's steady image from the world ({@code low}: colour and depth; {@code hand}: the hand's depth) and
     * last frame's, and returns it (the world's resolution) for the upscale.
     */
    static GpuTextureView resolve(CommandEncoder encoder, TextureTarget low, TextureTarget hand) {
        int w = low.width, h = low.height;
        if (!zeroToOneKnown) {
            zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
            zeroToOneKnown = true;
        }
        if (ubo == null) {
            ubo = new MappableRingBuffer(() -> "awaken pixels steady", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UBO_SIZE);
        }
        boolean resized = a == null || a.width != w || a.height != h;
        a = sized(a, "Pixels steady A", w, h);
        b = sized(b, "Pixels steady B", w, h);
        TextureTarget out = flip ? b : a, last = flip ? a : b;
        flip = !flip;

        long now = System.nanoTime();
        // last frame's image holds across ordinary frames; not across a teleport, a long gap, a resize or a fresh start
        boolean valid = captured && hasPrev && !resized && now - prevNs < 250_000_000L
                && Math.abs(camX - prevX) + Math.abs(camY - prevY) + Math.abs(camZ - prevZ) < 24.0;
        if (valid) {
            // this frame's NDC -> camera-relative world -> last frame's camera -> last frame's clip space
            viewProj.invert(inverse);
            reproject.set(prevViewProj).translate((float) (camX - prevX), (float) (camY - prevY), (float) (camZ - prevZ)).mul(inverse);
        } else {
            reproject.identity();
        }
        GpuBuffer frame = ubo.currentBuffer();
        try (var view = frame.map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putMat4f(reproject).putVec4(valid ? 1f : 0f, zeroToOne ? 1f : 0f, BLEND, 0f);
        }
        // the world's own reprojection over the CPU's estimate, from the projection it was really drawn with (a camera
        // mod's roll included: Camera Overhaul's would ghost the steady image otherwise)
        if (valid && WorldProjection.history()) WorldProjection.copyReprojection(encoder, frame, 0L);
        try (RenderPass pass = encoder.createRenderPass(LABEL, out.getColorTextureView(), CLEAR)) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PixelsPipelines.STEADY));
            pass.setUniform("InSampler", low.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("DepthSampler", low.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("HandSampler", hand.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("HistorySampler", last.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("PixelsSteady", frame);
            pass.draw(3, 1, 0, 0);
        }
        ubo.rotate();

        prevViewProj.set(viewProj);
        prevX = camX;
        prevY = camY;
        prevZ = camZ;
        prevNs = now;
        hasPrev = captured;
        captured = false;
        return out.getColorTextureView();
    }

    /** Frees the steady images (Pixels stepped aside, or the pass was turned off). */
    static void release() {
        if (a != null) a.destroyBuffers();
        if (b != null) b.destroyBuffers();
        a = b = null;
        hasPrev = false;
    }

    private static TextureTarget sized(@Nullable TextureTarget t, String label, int w, int h) {
        if (t == null) return new TextureTarget(label, w, h, GpuFormat.RGB10A2_UNORM, null);
        if (t.width != w || t.height != h) t.resize(w, h);
        return t;
    }
}
