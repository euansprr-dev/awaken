package dev.awaken.lens.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * The projection the world was really drawn with, for every client part that rebuilds positions or undoes the camera's
 * motion: Lens 2's light, Pixels' steady pass and the Holo HUD (SPEC §22 "The world's own projection"). GameRenderer
 * multiplies its bob pose stack into the level's projection, and a camera mod may add to that stack (Camera Overhaul
 * rolls it as you strafe, turn and fly, and sways it at rest); that matrix exists only in the level's projection uniform
 * buffer, with no copy on the CPU and no event that hands it out.
 *
 * <p>So, on a frame someone asked for it ({@link #want}, before the level draws), a one-row pass runs first thing after
 * the level is drawn, while that buffer is still bound: it multiplies in the view rotation and inverts it, carries last
 * frame's along, places the sun on screen, builds the steady pass's reprojection and measures the roll a camera mod
 * adds on top of the game's own bob. Users copy texels out on the GPU; the roll comes back to the CPU a frame or two
 * later (the Holo HUD's only need). Where the pass can't run (it failed to build, the bound projection isn't the
 * level's, the dev switch), {@link #ready} is false and each user keeps its CPU estimate (the game's own bob, hurt tilt
 * and nausea, without a camera mod's roll). Lens off and no Pixels or Holo panel: nothing runs.
 */
public final class WorldProjection {
    private WorldProjection() {}

    /**
     * Output texels (18×1 RGBA32F): 0-3 inverse view-projection, 4-7 view-projection, 8-11 last frame's view-projection
     * (all camera-relative), 12 the sun on screen (xy uv, z 1 ahead), 13-16 this frame's NDC to last frame's clip space
     * (the camera's step included: Pixels' steady pass), 17 x the roll a camera mod adds (radians), y 1.
     */
    static final int WIDTH = 18;
    static final int SUN = 12, REPROJECT = 13, ROLL = 17;

    private static final BindGroupLayout INPUTS = BindGroupLayout.builder()
            .withUniform("HistorySampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("WorldProjection", UniformType.UNIFORM_BUFFER)
            .build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Awaken.id("pipeline/world_projection"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Awaken.id("core/world_projection"))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(INPUTS)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA32_FLOAT, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putMat4f().putMat4f().putVec4().putVec4().putVec4().get();

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterRenderPipelinesEvent.class, e -> e.registerOptionalPipeline(PIPELINE));
        // after the frame (26.3 extracts the next frame's state before RenderFrameEvent.Pre: Holo asks there)
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, e -> {
            ready = false;
            wanted = false;
            wantRoll = false;
        });
        // first after the level: the projection the world was drawn with is still bound
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.AfterLevel.class, WorldProjection::run);
    }

    // this frame: asked for, ran (its texels hold this frame's values), the roll asked for (Holo)
    private static boolean wanted, ready, wantRoll;
    // -1 unknown, 0 failed, 1 compiled; looked up once per resource load (a failed one would recompile on every lookup)
    private static int compiled = -1;
    // development (/awaken lens projection off): every user keeps its CPU estimate, as before
    private static boolean off;

    private static GpuTexture @Nullable [] targets;
    private static GpuTextureView @Nullable [] views;
    private static @Nullable MappableRingBuffer ubo;
    private static int flip;
    private static boolean hasPrev, history;
    private static double prevX, prevY, prevZ;
    private static long prevNs;
    private static final Vector3f sun = new Vector3f();
    private static final Matrix4f bob = new Matrix4f(), unbob = new Matrix4f();

    // the roll's read-back: a few small buffers in turn (each busy until its callback), the latest answer and its time
    private static final int SLOTS = 4;
    private static final GpuBuffer[] rollBuffers = new GpuBuffer[SLOTS];
    private static final boolean[] rollBusy = new boolean[SLOTS];
    private static int rollSlot;
    private static volatile float roll;
    private static volatile long rollNs;

    /** Asks for this frame's matrices (call each frame before the level draws: the world's fog, Pixels' begin, extract). */
    public static void want() {
        wanted = true;
    }

    /** Asks for this frame's matrices and the camera mod's roll read back (the Holo HUD). */
    public static void wantRoll() {
        wanted = true;
        wantRoll = true;
    }

    /** Whether this frame's texels hold the world's own matrices (after the level is drawn). */
    public static boolean ready() {
        return ready;
    }

    /** Whether last frame's matrices hold too (texels 8-16): not after a teleport, a gap or a frame without the pass. */
    public static boolean history() {
        return ready && history;
    }

    /**
     * The roll (radians, about the view axis) a camera mod adds on top of the game's own bob, as last read back: a frame
     * or two behind; 0 when there's no fresh answer.
     */
    public static float roll() {
        return System.nanoTime() - rollNs < 250_000_000L ? roll : 0f;
    }

    /** The sun's direction for its place on screen (Lens). */
    static void sun(Vector3fc toward) {
        sun.set(toward);
    }

    /** This frame's texels (when {@link #ready}). */
    static GpuTexture output() {
        return targets[flip ^ 1];
    }

    /** Copies this frame's NDC-to-last-frame's-clip matrix (64 bytes) into {@code dst} at {@code offset} on the GPU. */
    public static void copyReprojection(CommandEncoder encoder, GpuBuffer dst, long offset) {
        encoder.copyTextureToBuffer(output(), dst, offset, WorldProjection::nothing, 0, REPROJECT, 0, 4, 1);
    }

    private static void nothing() {}

    /** After a resource reload the pipeline is rebuilt: look again. */
    static void recheck() {
        compiled = -1;
    }

    /** The client command's switch: "off" (every user's CPU estimate), "on", or "check" (logs the roll). */
    static String command(String arg) {
        switch (arg) {
            case "off" -> off = true;
            case "on" -> off = false;
            case "check" -> {
                return String.format(java.util.Locale.ROOT, "projection %s; a camera mod rolls the world %.2f degrees (read %d ms ago)",
                        off ? "off (the CPU's estimate)" : "the world's own", Math.toDegrees(roll()), (System.nanoTime() - rollNs) / 1_000_000L);
            }
            default -> {
                return "lens projection: off|on|check";
            }
        }
        return "projection " + (off ? "off: the CPU's estimate (a camera mod's roll is missed)" : "the world's own");
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
            if (ok) Awaken.LOG.info("[lens] the world's own projection: ready");
            else Awaken.LOG.warn("[lens] the world's projection pass did not compile: Lens, Pixels and Holo rebuild it on the CPU (a camera mod's roll is missed)");
        }
        return compiled == 1;
    }

    private static void run(RenderLevelStageEvent.AfterLevel e) {
        boolean asked = wanted;
        wanted = false;
        if (!asked || off || RenderSystem.getProjectionType() != ProjectionType.PERSPECTIVE) {
            hasPrev = false;
            return;
        }
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        if (projection == null || !compiled()) {
            hasPrev = false;
            return;
        }
        if (targets == null) create();
        CameraRenderState cam = e.getLevelRenderState().cameraRenderState;
        double x = cam.pos.x, y = cam.pos.y, z = cam.pos.z;
        long now = System.nanoTime();
        // last frame's matrices hold across ordinary frames; not across a teleport, a long gap or a frame without the pass
        history = hasPrev && now - prevNs < 250_000_000L && Math.abs(x - prevX) + Math.abs(y - prevY) + Math.abs(z - prevZ) < 24.0;
        vanillaBob(cam, bob);
        bob.invert(unbob);
        GpuBuffer values = ubo.currentBuffer();
        try (var view = values.map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putMat4f(cam.viewRotationMatrix).putMat4f(cam.projectionMatrix).putMat4f(unbob)
                    .putVec4(sun.x, sun.y, sun.z, 0f)
                    .putVec4(history ? (float) (x - prevX) : 0f, history ? (float) (y - prevY) : 0f, history ? (float) (z - prevZ) : 0f, 0f)
                    .putVec4(history ? 1f : 0f, 0f, 0f, 0f);
        }
        int out = flip, last = flip ^ 1;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "awaken world projection", views[out], Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(PIPELINE));
            pass.setUniform("Projection", projection);
            pass.setUniform("HistorySampler", views[last], RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setUniform("WorldProjection", values);
            pass.draw(3, 1, 0, 0);
        }
        ubo.rotate();
        if (wantRoll && !rollBusy[rollSlot]) readRoll(encoder, targets[out]);
        flip = last;
        ready = true;
        hasPrev = true;
        prevX = x;
        prevY = y;
        prevZ = z;
        prevNs = now;
    }

    private static void readRoll(CommandEncoder encoder, GpuTexture src) {
        int slot = rollSlot;
        rollSlot = (rollSlot + 1) % SLOTS;
        if (rollBuffers[slot] == null) {
            rollBuffers[slot] = RenderSystem.getDevice().createBuffer(() -> "awaken world projection roll", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 16L);
        }
        GpuBuffer buffer = rollBuffers[slot];
        rollBusy[slot] = true;
        encoder.copyTextureToBuffer(src, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView read = buffer.map(true, false)) {
                ByteBuffer data = read.data();
                if (data.getFloat(4) > 0.5f) {
                    roll = data.getFloat(0);
                    rollNs = System.nanoTime();
                }
            } finally {
                rollBusy[slot] = false;
            }
        }, 0, ROLL, 0, 1, 1);
    }

    private static void create() {
        var device = RenderSystem.getDevice();
        targets = new GpuTexture[2];
        views = new GpuTextureView[2];
        for (int i = 0; i < 2; i++) {
            targets[i] = device.createTexture(() -> "awaken world projection", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
                    | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA32_FLOAT, WIDTH, 1, 1, 1);
            views[i] = device.createTextureView(targets[i]);
        }
        ubo = new MappableRingBuffer(() -> "awaken world projection", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_UNIFORM, UBO_SIZE);
    }

    /**
     * GameRenderer.bobHurt and bobView (both private there), on a matrix instead of a pose stack in the same order: the
     * game's own part of the bob, for the CPU estimates and for measuring what a camera mod adds.
     */
    public static Matrix4f vanillaBob(CameraRenderState cam, Matrix4f dest) {
        dest.identity();
        Minecraft mc = Minecraft.getInstance();
        var e = cam.entityRenderState;
        if (e.isLiving) {
            if (e.isDeadOrDying) {
                float duration = Math.min(e.deathTime, 20.0F);
                dest.rotate(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (duration + 200.0F)));
            }
            float hurt = e.hurtTime;
            if (hurt >= 0.0F && !e.preventFlinch) {
                hurt /= e.hurtDuration;
                hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
                float rr = e.hurtDir;
                dest.rotate(Axis.YP.rotationDegrees(-rr));
                dest.rotate(Axis.ZP.rotationDegrees((float) (-hurt * 14.0 * mc.options.damageTiltStrength().get())));
                dest.rotate(Axis.YP.rotationDegrees(rr));
            }
        }
        if (mc.options.bobView().get() && e.isPlayer) {
            float walk = e.backwardsInterpolatedWalkDistance, b = e.bob;
            dest.translate(Mth.sin(walk * (float) Math.PI) * b * 0.5F, -Math.abs(Mth.cos(walk * (float) Math.PI) * b), 0.0F);
            dest.rotate(Axis.ZP.rotationDegrees(Mth.sin(walk * (float) Math.PI) * b * 3.0F));
            dest.rotate(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2F) * b) * 5.0F));
        }
        return dest;
    }
}
