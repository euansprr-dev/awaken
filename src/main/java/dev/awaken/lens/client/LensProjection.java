package dev.awaken.lens.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Lens 2's frame matrices from the projection the world was really drawn with (SPEC §22 "The world's own projection").
 * GameRenderer multiplies its bob pose stack into the level's projection, and a camera mod may add to that stack (Camera
 * Overhaul rolls it as you strafe, turn and fly); that matrix exists only in the level's projection uniform buffer, with
 * no copy on the CPU. So a one-row pass reads that buffer on the GPU while it is still bound (just after the level is
 * drawn), multiplies in the view rotation, inverts it, carries last frame's along and places the sun on screen; the
 * result is copied over LensFrame's CPU estimate (texels 0-11 and 21) and into LensBlock for Tiles. A pipeline that
 * fails to build leaves the CPU estimate (the game's own bob, hurt tilt and nausea, without a camera mod's roll).
 */
final class LensProjection {
    /** Output texels: 0-3 inverse view-projection, 4-7 view-projection, 8-11 last frame's, 12 the sun on screen. */
    static final int WIDTH = 13;
    /** Where the sun's place on screen goes in LensFrame (layout in lens_frame.glsl). */
    static final int SUN_TEXEL = 21;

    static final BindGroupLayout INPUTS = BindGroupLayout.builder()
            .withUniform("HistorySampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("LensProjection", UniformType.UNIFORM_BUFFER)
            .build();
    static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Awaken.id("pipeline/lens_projection"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Awaken.id("core/lens_projection"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(INPUTS)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA32_FLOAT, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().get();

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(PIPELINE);
    }

    // -1 unknown, 0 failed, 1 compiled; looked up once per resource load (a failed one would recompile on every lookup)
    private static int compiled = -1;

    /** After a resource reload the pipeline is rebuilt: look again. */
    static void recheck() {
        compiled = -1;
    }

    private static boolean compiled() {
        if (compiled < 0) {
            boolean ok;
            try {
                ok = RenderSystem.getCompiledPipelineNullable(PIPELINE) != null;
            } catch (RuntimeException e) {
                ok = false;
            }
            compiled = ok ? 1 : 0;
            if (ok) Awaken.LOG.info("[lens] frame matrices from the world's own projection");
            else Awaken.LOG.warn("[lens] projection pass did not compile: Lens rebuilds the projection itself (a camera mod's roll is missed)");
        }
        return compiled == 1;
    }

    // development (/awaken lens projection off|on|check): the CPU's estimate alone, as before (for A/B shots), and a
    // one-off read-back of how far the world's projection is rolled from the game's own camera
    private static boolean off;
    private static boolean checkWanted;
    private @Nullable GpuBuffer checkBuffer;
    private final Matrix4f checkCpu = new Matrix4f(), checkView = new Matrix4f(), checkProj = new Matrix4f();

    /** The client command's switch: "off" (the CPU's estimate alone), "on", or "check" (logs the roll once). */
    static String command(String arg) {
        switch (arg) {
            case "off" -> off = true;
            case "on" -> off = false;
            case "check" -> checkWanted = true;
            default -> {
                return "lens projection: off|on|check";
            }
        }
        return "Lens projection: " + (off ? "the CPU's estimate (a camera mod's roll is missed)" : "the world's own")
                + (checkWanted ? "; checking the roll next frame" : "");
    }

    private final GpuTexture[] targets = new GpuTexture[2];
    private final GpuTextureView[] views = new GpuTextureView[2];
    private final MappableRingBuffer ubo = new MappableRingBuffer(() -> "awaken lens projection", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UBO_SIZE);
    private int flip;
    private boolean hasHistory, wrote;
    // the level's projection uniform, as GameRenderer.renderLevel bound it for the world (null: not seen this frame)
    private @Nullable GpuBufferSlice level;

    LensProjection() {
        var device = RenderSystem.getDevice();
        for (int i = 0; i < 2; i++) {
            targets[i] = device.createTexture(() -> "awaken lens projection", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
                    | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA32_FLOAT, WIDTH, 1, 1, 1);
            views[i] = device.createTextureView(targets[i]);
        }
    }

    /**
     * First thing after the level is drawn (before any other listener could bind another): the projection the world was
     * drawn with, if it is the level's perspective one.
     */
    void capture() {
        level = RenderSystem.getProjectionType() == ProjectionType.PERSPECTIVE ? RenderSystem.getProjectionMatrixBuffer() : null;
    }

    /**
     * This frame's matrices from the captured projection and {@code cam}'s view rotation, over {@code frame}'s (which
     * LensFrame has just written from the CPU). {@code history}: last frame's matrices are usable (LensFrame's rule).
     * Returns false where it couldn't (the CPU estimate stands).
     */
    boolean write(CameraRenderState cam, Vector3fc sun, boolean history, GpuTexture frame, Matrix4fc cpuViewProj) {
        wrote = false;
        GpuBufferSlice projection = level;
        level = null;
        if (projection == null || off || !compiled()) {
            hasHistory = false;
            return false;
        }
        GpuBuffer values = ubo.currentBuffer();
        try (var view = values.map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putMat4f(cam.viewRotationMatrix).putVec4(sun.x(), sun.y(), sun.z(), 0f)
                    .putVec4(history && hasHistory ? 1f : 0f, 0f, 0f, 0f);
        }
        int out = flip, last = flip ^ 1;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "awaken lens projection", views[out], Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            pass.setUniform("Projection", projection);
            pass.setUniform("HistorySampler", views[last], RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("LensProjection", values);
            pass.draw(3, 1, 0, 0);
        }
        ubo.rotate();
        encoder.copyTextureToTexture(targets[out], frame, 0, 0, 0, 0, 0, 12, 1);
        encoder.copyTextureToTexture(targets[out], frame, 0, SUN_TEXEL, 0, 12, 0, 1, 1);
        if (checkWanted) check(encoder, targets[out], cam, cpuViewProj);
        flip = last;
        hasHistory = true;
        wrote = true;
        return true;
    }

    /** Reads this frame's matrices back and logs the roll between the world's projection and the CPU's estimate. */
    private void check(CommandEncoder encoder, GpuTexture src, CameraRenderState cam, Matrix4fc cpuViewProj) {
        checkWanted = false;
        if (checkBuffer == null) {
            checkBuffer = RenderSystem.getDevice().createBuffer(() -> "awaken lens projection check", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    (long) WIDTH * 16);
        }
        checkCpu.set(cpuViewProj);
        checkView.set(cam.viewRotationMatrix);
        checkProj.set(cam.projectionMatrix);
        GpuBuffer buffer = checkBuffer;
        encoder.copyTextureToBuffer(src, buffer, 0L, () -> {
            Matrix4f gpu = new Matrix4f();
            try (GpuBufferSlice.MappedView read = buffer.map(true, false)) {
                gpu.set(4 * 16, read.data());
            }
            float roll = rollOnScreen(gpu) - rollOnScreen(checkCpu);
            Awaken.LOG.info("[lens] projection check: the world is drawn rolled {} degrees from the game's own camera; Lens follows the world",
                    String.format(java.util.Locale.ROOT, "%.2f", Math.toDegrees(Math.atan2(Math.sin(roll), Math.cos(roll)))));
        }, 0, 0, 0, WIDTH, 1);
    }

    // the screen angle of the camera's own right, a little way ahead (corrected for the window's aspect)
    private float rollOnScreen(Matrix4fc viewProj) {
        Vector3f right = checkView.positiveX(new Vector3f()), back = checkView.positiveZ(new Vector3f());
        Vector4f a = viewProj.transform(new Vector4f(-back.x * 10f, -back.y * 10f, -back.z * 10f, 1f));
        Vector4f b = viewProj.transform(new Vector4f(-back.x * 10f + right.x, -back.y * 10f + right.y, -back.z * 10f + right.z, 1f));
        float aspect = checkProj.m11() / checkProj.m00();
        return (float) Math.atan2(b.y / b.w - a.y / a.w, (b.x / b.w - a.x / a.w) * aspect);
    }

    /** The same values into Tiles' uniform block (after LensBlock has written this frame's from the CPU). */
    void copyTo(GpuBuffer block) {
        if (!wrote) return;
        GpuTexture src = targets[flip ^ 1];
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(src, block, 0L, LensProjection::nothing, 0, 0, 0, 12, 1);
        encoder.copyTextureToBuffer(src, block, SUN_TEXEL * 16L, LensProjection::nothing, 0, 12, 0, 1, 1);
    }

    private static void nothing() {}

    /** Forget last frame's matrices (a new chain, a new place). */
    void reset() {
        hasHistory = false;
    }

    void close() {
        ubo.close();
        if (checkBuffer != null) checkBuffer.close();
        for (int i = 0; i < 2; i++) {
            views[i].close();
            targets[i].close();
        }
    }
}
