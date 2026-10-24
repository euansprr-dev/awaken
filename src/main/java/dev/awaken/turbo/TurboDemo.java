package dev.awaken.turbo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The Turbo bridge's proof (switch demo, or {@code /awaken turbo demo on}): every frame, right after the sky, a compute
 * shader builds a 256 x 256 grid (vertices, indices and the indexed-indirect draw's arguments, with the GPU choosing
 * which quads are drawn) and a normal RenderPipeline draws it in the same frame with {@code drawIndexedIndirect}.
 * The grid hangs 8 blocks below where the player stood when it was switched on. GPU time comes from Minecraft's own
 * timestamp queries (read four frames later, never waited on); every 600 frames one {@code [turbo] demo} log line.
 */
final class TurboDemo {
    private TurboDemo() {}

    private static final int N = 256;
    private static final int INDICES = (N - 1) * (N - 1) * 6;

    static final RenderPipeline GRID = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(Awaken.id("pipeline/turbo_grid"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withColorTargetState(ColorTargetState.DEFAULT) // one opaque colour target: the builder's default is none
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static boolean wanted;
    private static double ax, ay, az;
    private static TurboCompute.@Nullable Program program;
    private static @Nullable TurboBuffer verts, indices, args;
    private static @Nullable GpuQueryPool timer;
    private static final ByteBuffer RESET = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder());
    private static final ByteBuffer PUSH = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
    private static long frame;
    // running sums since the last log line (ns) and how many frames had timestamps
    private static long cpuNs, computeNs, drawNs, frames, timed;
    private static String last = "no frames yet";

    static void init(IEventBus modBus) {
        modBus.addListener(RegisterRenderPipelinesEvent.class, e -> e.registerOptionalPipeline(GRID));
        NeoForge.EVENT_BUS.addListener(TurboDemo::afterSky);
        wanted = TurboSwitches.on(TurboSwitches.Feature.DEMO);
        RESET.putInt(0).putInt(1).putInt(0).putInt(0).putInt(0).flip();
    }

    static String start() {
        if (Turbo.compute() == null) return "Turbo demo: Turbo is off (" + Turbo.why() + ")";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "Turbo demo: join a world first";
        anchorAt(mc.player.position());
        prepare(Turbo.compute());
        wanted = true;
        return "Turbo demo on: a compute-built " + N + " x " + N + " grid 8 blocks below you, drawn after the sky";
    }

    static String stop() {
        wanted = false;
        return "Turbo demo off. " + status();
    }

    static String status() {
        return (wanted ? "on" : "off") + "; " + last;
    }

    /** Compiles the shader and makes the buffers ahead of the first drawn frame (the command's frame pays for it). */
    private static void prepare(TurboCompute c) {
        if (program != null) return;
        program = c.program("awaken:turbo/grid.comp", Turbo.source("grid.comp"), 3, 16);
        verts = c.buffer("turbo demo vertices", GpuBuffer.USAGE_VERTEX, (long) N * N * 16);
        indices = c.buffer("turbo demo indices", GpuBuffer.USAGE_INDEX, (long) INDICES * 4);
        args = c.buffer("turbo demo draw", GpuBuffer.USAGE_INDIRECT_PARAMETERS, 32);
        timer = RenderSystem.getDevice().createTimestampQueryPool(12);
    }

    private static void anchorAt(Vec3 p) {
        ax = Math.floor(p.x) - N / 2.0;
        ay = Math.floor(p.y) - 8;
        az = Math.floor(p.z) - N / 2.0;
    }

    /** Never lets an exception reach the render loop: a failure turns the demo off with one log line. */
    private static void afterSky(RenderLevelStageEvent.AfterSky e) {
        if (!wanted) return;
        try {
            draw(e);
        } catch (RuntimeException | LinkageError ex) {
            wanted = false;
            last = "stopped: " + ex;
            TurboVersions.LOG.error("[turbo] demo off: {}", ex.toString(), ex);
        }
    }

    private static void draw(RenderLevelStageEvent.AfterSky e) {
        TurboCompute c = Turbo.compute();
        if (c == null) return;
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(GRID);
        if (compiled == null) return;
        if (program == null) {
            prepare(c);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) anchorAt(mc.player.position());
        }
        long t0 = System.nanoTime();
        LevelRenderState state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        float time = ((state.gameTime % 24000L) + state.worldPartialTicks) / 20.0F;
        PUSH.clear().putFloat((float) (ax - cam.x)).putFloat((float) (ay - cam.y)).putFloat((float) (az - cam.z)).putFloat(time).flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int slot = (int) (frame % 4) * 3;
        readTimes(slot);
        encoder.writeTimestamp(timer, slot);
        try (TurboCompute.Pass pass = c.frame()) {
            pass.update(args, 0, RESET.rewind());
            pass.dispatch(program, N / 16, N / 16, 1, PUSH, verts, indices, args);
        }
        encoder.writeTimestamp(timer, slot + 1);
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = encoder.createRenderPass(() -> "Awaken Turbo grid", target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(e.getModelViewMatrix())));
            pass.setPipeline(compiled);
            pass.setVertexBuffer(0, verts.slice());
            pass.setIndexBuffer(indices, IndexType.INT);
            pass.drawIndexedIndirect(args.slice(0, 20), 1);
        }
        encoder.writeTimestamp(timer, slot + 2);
        cpuNs += System.nanoTime() - t0;
        frames++;
        frame++;
        if (frames >= 600) logStats();
    }

    /** This slot's timestamps from four frames ago (long finished: at most two frames are in flight). */
    private static void readTimes(int slot) {
        if (frame < 4 || timer == null) return;
        OptionalLong[] v = timer.getValues(slot, 3);
        if (v[0].isEmpty() || v[1].isEmpty() || v[2].isEmpty()) return;
        double period = Turbo.compute() == null ? 1.0 : Turbo.compute().device.timestampPeriod;
        computeNs += (long) ((v[1].getAsLong() - v[0].getAsLong()) * period);
        drawNs += (long) ((v[2].getAsLong() - v[1].getAsLong()) * period);
        timed++;
    }

    private static void logStats() {
        last = String.format(Locale.ROOT, "%d frames: CPU %.1f µs, GPU compute %s, GPU draw %s per frame", frames, cpuNs / 1000.0 / frames,
                timed == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f µs", computeNs / 1000.0 / timed),
                timed == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f µs", drawNs / 1000.0 / timed));
        TurboVersions.LOG.info("[turbo] demo {}", last);
        cpuNs = computeNs = drawNs = frames = timed = 0;
    }
}
