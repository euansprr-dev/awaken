package dev.awaken.wildlife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.CreatureLife;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.model.HeroModel;
import dev.awaken.wildlife.WildCritter;
import dev.awaken.wildlife.WildKinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the wild's animals (SPEC "Wild life") from their rigs ({@link WildFaunaData}), as Carillon draws its own: one
 * model per animal, each coat a texture, the life layer per body. A bird in the air banks into its turns and pitches with
 * its climb; one on the water sits in it; an owl's and a fox's eyes catch the light in the dark (their glow layer, by
 * darkness). Nothing in the air casts a shadow.
 */
final class WildRenderer extends EntityRenderer<WildCritter, WildRenderState> {
    private static final String[] TEMPER = {"cute", "cute", "grazer", "hunter", "hunter", "cute", "grazer", "hunter",
            "cute", "cute", "hunter", "cute"};
    private static final float[] SHADOW = {0.1F, 0.1F, 0.2F, 0.2F, 0.3F, 0.25F, 0.25F, 0.3F, 0.15F, 0.2F, 0.35F, 0.1F};
    static final float FADE = 5F;

    private static Map<String, HeroRig> rigs;

    static synchronized Map<String, HeroRig> rigs() {
        if (rigs == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : WildFaunaData.SOURCES) {
                HeroRig r = HeroRig.parse(src);
                m.put(r.id, r);
            }
            rigs = m;
        }
        return rigs;
    }

    static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(Awaken.id(id), "main");
    }

    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (HeroRig r : rigs().values()) e.registerLayerDefinition(layer(r.id), () -> HeroModel.create(r));
    }

    private final WildModel[] models = new WildModel[WildKinds.BODIES];
    private final Identifier[][] skins = new Identifier[WildKinds.BODIES][], blinks = new Identifier[WildKinds.BODIES][],
            glows = new Identifier[WildKinds.BODIES][];
    private final float[] scale = new float[WildKinds.BODIES];

    WildRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        var rm = Minecraft.getInstance().getResourceManager();
        for (int k = 0; k < WildKinds.BODIES; k++) {
            String id = WildKinds.RIGS[k];
            models[k] = new WildModel(ctx.bakeLayer(layer(id)), rigs().get(id), TEMPER[k]);
            List<String> vs = WildFaunaData.VARIANTS.get(id);
            skins[k] = new Identifier[vs.size()];
            blinks[k] = new Identifier[vs.size()];
            glows[k] = new Identifier[vs.size()];
            for (int v = 0; v < vs.size(); v++) {
                String base = "textures/entity/wild/" + id + (vs.get(v).isEmpty() ? "" : "_" + vs.get(v));
                skins[k][v] = Awaken.id(base + ".png");
                Identifier b = Awaken.id(base + "_blink.png"), g = Awaken.id(base + "_glow.png");
                blinks[k][v] = rm.getResource(b).isPresent() ? b : null;
                glows[k][v] = rm.getResource(g).isPresent() ? g : null;
            }
            scale[k] = WildFaunaData.SCALE.get(id);
        }
    }

    WildModel model(int kind) {
        return models[Mth.clamp(kind, 0, models.length - 1)];
    }

    @Override
    public WildRenderState createRenderState() {
        return new WildRenderState();
    }

    @Override
    protected AABB getBoundingBoxForCulling(WildCritter e, float partial) {
        return e.getInterpolatedBoundingBox(partial).inflate(e.kind == WildKinds.RAPTOR || e.kind == WildKinds.HERON ? 1.2 : 0.5);
    }

    @Override
    public void extractRenderState(WildCritter e, WildRenderState s, float partial) {
        super.extractRenderState(e, s, partial);
        int k = Mth.clamp(e.kind, 0, WildKinds.BODIES - 1);
        s.kind = k;
        int v = Math.floorMod(e.variant, skins[k].length);
        s.variant = v;
        s.texture = skins[k][v];
        s.blink = blinks[k][v];
        s.glow = glows[k][v];
        s.modelScale = scale[k];
        float age = e.tickCount + partial;
        s.ageInTicks = age;
        s.bodyRot = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        s.walkAnimationSpeed = Mth.clamp(e.speed * 6F, 0F, 1F);
        s.roll = Mth.lerp(partial, e.rollO, e.roll);
        s.pitch = Mth.lerp(partial, e.pitchO, e.pitch);
        s.sink = e.sink;
        s.airborne = Birds.airborne(e);
        s.clip = e.clip;
        s.clipTime = Math.max(0F, (age - e.clipStart) / 20F);
        s.prevClip = e.prevClip;
        float since = age - e.prevEnd;
        s.prevWeight = e.prevClip >= 0 && since < FADE ? 1F - Mth.smoothstep(Mth.clamp(since / FADE, 0F, 1F)) : 0F;
        s.prevTime = Math.max(0F, (age - e.prevStart) / 20F);
        s.yRot = 0F;
        s.xRot = 0F;
        if (!Double.isNaN(e.lookX)) {
            double dx = e.lookX - s.x, dz = e.lookZ - s.z, dy = e.lookY - (s.y + 0.3);
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            s.yRot = Mth.clamp(Mth.wrapDegrees(yaw - s.bodyRot), -120F, 120F);
            s.xRot = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        }
        Life life = CreatureLife.life(e.clientLife, models[k].life, e.getId() * 31 + e.seed);
        e.clientLife = life;
        life.walk = s.walkAnimationSpeed;
        life.airborne = s.airborne;
        life.busy = s.clip >= 0 && s.clipTime < 0.2F;
        CreatureLife.tick(life, s, true);
        s.life = life;
        if (s.blink != null && CreatureLife.eyesClosed(life) > 0.5F) s.texture = s.blink;
        // eyes that shine in the dark: by how little light falls on it
        s.glowAlpha = 0F;
        if (s.glow != null && e.level() != null) {
            int block = LightCoordsUtil.block(s.lightCoords), sky = LightCoordsUtil.sky(s.lightCoords);
            float dark = e.level().getSkyDarken() / 11F;
            float lit = Math.max(block, sky * (1F - dark)) / 15F;
            s.glowAlpha = Mth.clamp(1.1F - lit * 1.6F, 0F, 0.85F);
        }
    }

    @Override
    protected float getShadowRadius(WildRenderState s) {
        return s.airborne || s.sink > 0F ? 0F : SHADOW[s.kind];
    }

    @Override
    public void submit(WildRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        WildModel m = models[s.kind];
        pose.pushPose();
        if (s.sink > 0F) pose.translate(0F, -s.sink, 0F);
        pose.rotateDegrees(Axis.YP, 180F - s.bodyRot);
        if (s.roll != 0F || s.pitch != 0F) {
            // bank and pitch about the body's middle, not the feet
            float mid = 0.12F * s.modelScale;
            pose.translate(0F, mid, 0F);
            pose.rotateDegrees(Axis.ZP, s.roll);
            pose.rotateDegrees(Axis.XP, s.pitch);
            pose.translate(0F, -mid, 0F);
        }
        if (s.life != null) CreatureLife.rootPose(s.life, pose, s);
        pose.scale(-s.modelScale, -s.modelScale, s.modelScale);
        pose.translate(0F, -1.501F, 0F);
        out.submitModel(m, s, pose, RenderTypes.entityCutout(s.texture), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
        if (s.glow != null && s.glowAlpha > 0.02F) {
            out.order(1).submitModel(m, s, pose, RenderTypes.entityTranslucentEmissive(s.glow), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ARGB.white(s.glowAlpha), null, 0);
        }
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
