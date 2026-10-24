package dev.awaken.holo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

/**
 * The holo module's two pipelines, both registered as optional so a shader pack or a driver that can't build them
 * turns the Holo UI off (flat Status screen) instead of failing the resource reload:
 * <ul>
 *   <li>{@link #GLASS}: translucent, full-bright, no depth test (walls never cut a panel); it does write depth, so the
 *       always-on-top pass's depth (and Lens, which reads the merged depth) sees a panel at its real distance;</li>
 *   <li>{@link #GLOW}: true additive (ONE, ONE), no depth test or write.</li>
 * </ul>
 * Panels are drawn in the level's always-on-top phase, which never runs the OIT stages, so no OIT set is needed.
 */
public final class HoloPipelines {
    private HoloPipelines() {}

    public static final RenderPipeline GLASS = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(Awaken.id("pipeline/holo_glass"))
            .withVertexShader(Awaken.id("core/holo_glass"))
            .withFragmentShader(Awaken.id("core/holo_glass"))
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .withCull(false)
            .build();

    public static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withLocation(Awaken.id("pipeline/holo_glow"))
            .withVertexShader(Awaken.id("core/holo_glow"))
            .withFragmentShader(Awaken.id("core/holo_glow"))
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    /** The same glass and glow shaders on GUI pipelines, for the System's cards (HoloGuiLook). */
    public static final RenderPipeline GUI_GLASS = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Awaken.id("pipeline/holo_gui_glass"))
            .withVertexShader(Awaken.id("core/holo_glass"))
            .withFragmentShader(Awaken.id("core/holo_glass"))
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .build();

    public static final RenderPipeline GUI_GLOW = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Awaken.id("pipeline/holo_gui_glow"))
            .withVertexShader(Awaken.id("core/holo_glow"))
            .withFragmentShader(Awaken.id("core/holo_glow"))
            .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .build();

    private static RenderType glassType, glowType;
    private static int ok = -1; // -1 unknown, 0 failed, 1 compiled
    private static int guiOk = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(GLASS);
        e.registerOptionalPipeline(GLOW);
        e.registerOptionalPipeline(GUI_GLASS);
        e.registerOptionalPipeline(GUI_GLOW);
    }

    public static RenderType glass() {
        if (glassType == null) glassType = RenderType.create("awaken_holo_glass", RenderSetup.builder(GLASS).createRenderSetup());
        return glassType;
    }

    public static RenderType glow() {
        if (glowType == null) glowType = RenderType.create("awaken_holo_glow", RenderSetup.builder(GLOW).createRenderSetup());
        return glowType;
    }

    /** Whether both pipelines compiled. Checked once per session (a failed one would recompile on every lookup). */
    public static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(GLASS) != null && RenderSystem.getCompiledPipelineNullable(GLOW) != null ? 1 : 0;
            } catch (RuntimeException e) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[holo] the holo shaders did not load; the Status screen stays flat");
        }
        return ok == 1;
    }

    /** Whether the GUI pipelines compiled (the System's cards stay flat otherwise). */
    public static boolean guiOk() {
        if (guiOk < 0) {
            try {
                guiOk = RenderSystem.getCompiledPipelineNullable(GUI_GLASS) != null && RenderSystem.getCompiledPipelineNullable(GUI_GLOW) != null ? 1 : 0;
            } catch (RuntimeException e) {
                guiOk = 0;
            }
            if (guiOk == 0) Awaken.LOG.warn("[holo] the holo GUI shaders did not load; the System's cards stay flat");
        }
        return guiOk == 1;
    }

    /** After a resource reload the pipelines are rebuilt: look again. */
    static void recheck() {
        ok = -1;
        guiOk = -1;
    }
}
