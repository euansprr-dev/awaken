package dev.awaken.hall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.HashMap;
import java.util.Map;

/** The Hall's shaders, optional: if one doesn't build the Hall still works, flatter (plain light; the wall all lit). */
final class HallPipelines {
    private HallPipelines() {}

    static final RenderPipeline WEAPON = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Awaken.id("pipeline/hall_weapon"))
            .withVertexShader(Awaken.id("core/hall_weapon"))
            .withFragmentShader(Awaken.id("core/hall_weapon"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withCull(false) // the depth test settles closed meshes, and the fade between two weapons needs no winding
            .build();
    /** The wall's lit picture, seen through the lantern. */
    static final RenderPipeline LANTERN = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Awaken.id("pipeline/hall_lantern"))
            .withVertexShader(Awaken.id("core/hall_lantern"))
            .withFragmentShader(Awaken.id("core/hall_lantern"))
            .build();
    /** The weapons' lit picture (premultiplied, and rendered upside down in its texture). */
    static final RenderPipeline LANTERN_PIC = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Awaken.id("pipeline/hall_lantern_pic"))
            .withVertexShader(Awaken.id("core/hall_lantern"))
            .withFragmentShader(Awaken.id("core/hall_lantern"))
            .withShaderDefine("PIC")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .build();

    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    static void register(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(WEAPON);
        e.registerOptionalPipeline(LANTERN);
        e.registerOptionalPipeline(LANTERN_PIC);
    }

    private static boolean compiled(RenderPipeline p) {
        try {
            return RenderSystem.getCompiledPipelineNullable(p) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static boolean weaponOk() {
        return compiled(WEAPON);
    }

    static boolean lanternOk() {
        return compiled(LANTERN) && compiled(LANTERN_PIC);
    }

    static RenderType weapon(Identifier texture) {
        return TYPES.computeIfAbsent(texture, t -> RenderType.create("awaken_hall_weapon",
                RenderSetup.builder(WEAPON).withTexture("Sampler0", t).useLightmap().createRenderSetup()));
    }

    static RenderType fallback(Identifier texture) {
        return RenderTypes.entityCutout(texture, false);
    }

    /** The vertex colour the lantern shader reads as numbers: stage x, y, and the tide's y. */
    static int packLamp(float x, float y, int tideY) {
        int ix = Math.clamp(Math.round(x), 0, 4095), iy = Math.clamp(Math.round(y), 0, 4095), t = Math.clamp(tideY / 4 + 1, 1, 255);
        return t << 24 | ix >> 4 << 16 | (ix & 15) << 12 | iy >> 8 << 8 | iy & 255;
    }
}
