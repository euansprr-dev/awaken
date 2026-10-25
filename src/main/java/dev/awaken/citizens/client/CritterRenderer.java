package dev.awaken.citizens.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.citizens.CityCritter;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.CreatureLife;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.model.HeroModel;
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
 * Draws the city's animals (M4 B3b) from their rigs ({@link CityFaunaData}): one model per animal, each coat a texture
 * variant, the life layer per body; a carthorse draws its cart behind it on the cart's own line (the brain keeps the
 * axle on the road the horse has walked). Cats' eyes catch the lamps at night (their glow layer, by darkness), and so
 * does a boat's bow lantern when it's lit. Boats float: no shadow, no life layer.
 */
final class CritterRenderer extends EntityRenderer<CityCritter, CritterRenderState> {
    static final String[] IDS = {"city_pigeon", "city_gull", "city_cat", "city_dog", "city_horse", "city_boat"};
    private static final String[] TEMPER = {"cute", "cute", "cute", "cute", "grazer", "citizen"};
    private static final float[] SHADOW = {0.12F, 0.2F, 0.3F, 0.35F, 0.95F, 0F};
    /** Loads a boat may carry (CritterModel's load bones): none, crates aft, nets forward. */
    private static final int[] BOAT_LOADS = {-1, 1, 3};
    /** Ticks a clip takes to crossfade into the next. */
    static final float FADE = 5F;

    private static Map<String, HeroRig> rigs;

    static synchronized Map<String, HeroRig> rigs() {
        if (rigs == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : CityFaunaData.SOURCES) {
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

    private final CritterModel[] models = new CritterModel[IDS.length];
    private final CritterModel cart;
    private final Identifier[][] skins = new Identifier[IDS.length][], blinks = new Identifier[IDS.length][], glows = new Identifier[IDS.length][];
    private final float[] scale = new float[IDS.length];
    private final Identifier cartSkin;

    CritterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        var rm = Minecraft.getInstance().getResourceManager();
        for (int k = 0; k < IDS.length; k++) {
            HeroRig rig = rigs().get(IDS[k]);
            models[k] = new CritterModel(ctx.bakeLayer(layer(IDS[k])), rig, TEMPER[k]);
            List<String> vs = CityFaunaData.VARIANTS.get(IDS[k]);
            skins[k] = new Identifier[vs.size()];
            blinks[k] = new Identifier[vs.size()];
            glows[k] = new Identifier[vs.size()];
            for (int v = 0; v < vs.size(); v++) {
                String base = "textures/entity/city/" + IDS[k] + (vs.get(v).isEmpty() ? "" : "_" + vs.get(v));
                skins[k][v] = Awaken.id(base + ".png");
                Identifier b = Awaken.id(base + "_blink.png"), g = Awaken.id(base + "_glow.png");
                blinks[k][v] = rm.getResource(b).isPresent() ? b : null;
                glows[k][v] = rm.getResource(g).isPresent() ? g : null;
            }
            scale[k] = CityFaunaData.SCALE.get(IDS[k]);
        }
        cart = new CritterModel(ctx.bakeLayer(layer("city_cart")), rigs().get("city_cart"), "citizen");
        cartSkin = Awaken.id("textures/entity/city/city_cart.png");
    }

    CritterModel model(int kind) {
        return models[Mth.clamp(kind, 0, models.length - 1)];
    }

    CritterModel cart() {
        return cart;
    }

    @Override
    public CritterRenderState createRenderState() {
        return new CritterRenderState();
    }

    @Override
    protected AABB getBoundingBoxForCulling(CityCritter e, float partial) {
        return e.getInterpolatedBoundingBox(partial).inflate(e.kind >= CityCritter.HORSE ? 3.2 : 0.6);
    }

    @Override
    public void extractRenderState(CityCritter e, CritterRenderState s, float partial) {
        super.extractRenderState(e, s, partial);
        int k = Mth.clamp(e.kind, 0, IDS.length - 1);
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
        CritterModel m = models[k];
        clips(e, s, age);
        // where it looks: the brain's target, as yaw and pitch off the body
        s.yRot = 0F;
        s.xRot = 0F;
        if (!Double.isNaN(e.lookX)) {
            double dx = e.lookX - s.x, dz = e.lookZ - s.z, dy = e.lookY - (s.y + 0.4);
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            s.yRot = Mth.clamp(Mth.wrapDegrees(yaw - s.bodyRot), -110F, 110F);
            s.xRot = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        }
        s.crew = true;
        s.load = -1;
        if (e.kind == CityCritter.BOAT) {
            s.crew = e.crew;
            s.load = BOAT_LOADS[Math.floorMod(e.seed >> 5, BOAT_LOADS.length)];
            s.life = null;
            s.glowAlpha = e.lamp && s.glow != null ? 1F : 0F;
            s.wheel = 0F;
            return;
        }
        Life life = CreatureLife.life(e.clientLife, m.life, e.getId() * 31 + e.seed);
        e.clientLife = life;
        life.walk = s.walkAnimationSpeed;
        life.airborne = e.kind <= CityCritter.GULL && e.state >= CityCritters.FLY && e.state <= CityCritters.LAND;
        life.busy = s.clip >= 0 && s.clipTime < 0.2F;
        CreatureLife.tick(life, s, true);
        s.life = life;
        if (s.blink != null && CreatureLife.eyesClosed(life) > 0.5F) s.texture = s.blink;
        // a cat's eyes shine in the dark: by how little light falls on it
        s.glowAlpha = 0F;
        if (s.glow != null && e.level() != null) {
            int block = LightCoordsUtil.block(s.lightCoords), sky = LightCoordsUtil.sky(s.lightCoords);
            float dark = e.level().getSkyDarken() / 11F;
            float lit = Math.max(block, sky * (1F - dark)) / 15F;
            s.glowAlpha = Mth.clamp(1.15F - lit * 1.6F, 0F, 1F);
        }
        s.wheel = Mth.lerp(partial, e.wheelO, e.wheel);
        if (e.kind == CityCritter.HORSE) {
            s.cartDx = Mth.lerp(partial, e.cartDxO, e.cartDx);
            s.cartDz = Mth.lerp(partial, e.cartDzO, e.cartDz);
            s.cartDy = Mth.lerp(partial, e.cartYO, e.cartY);
            s.cartYaw = Mth.rotLerp(partial, e.cartYawO, e.cartYaw);
            if (s.cart == null) s.cart = new CritterRenderState();
            CritterRenderState c = s.cart;
            c.lightCoords = s.lightCoords;
            c.ageInTicks = age;
            c.wheel = s.wheel;
            c.load = Math.floorMod(e.seed >> 3, 3);
            c.clip = cart.clip(e.speed > 0.01F ? "walk" : "idle");
            c.clipTime = age / 20F;
            c.prevWeight = 0F;
            c.life = null;
            c.yRot = 0F;
            c.xRot = 0F;
        }
    }

    /** The clip now and the one it fades from (the brain switched at clipStart). */
    private static void clips(CityCritter e, CritterRenderState s, float age) {
        s.clip = e.clip;
        s.clipTime = Math.max(0F, (age - e.clipStart) / 20F);
        s.prevClip = e.prevClip;
        float since = age - e.prevEnd;
        s.prevWeight = e.prevClip >= 0 && since < FADE ? 1F - Mth.smoothstep(Mth.clamp(since / FADE, 0F, 1F)) : 0F;
        s.prevTime = Math.max(0F, (age - e.prevStart) / 20F);
    }

    @Override
    protected float getShadowRadius(CritterRenderState s) {
        return SHADOW[s.kind];
    }

    @Override
    public void submit(CritterRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        CritterModel m = models[s.kind];
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, 180F - s.bodyRot);
        if (s.life != null) CreatureLife.rootPose(s.life, pose, s);
        pose.scale(-s.modelScale, -s.modelScale, s.modelScale);
        pose.translate(0F, -1.501F, 0F);
        out.submitModel(m, s, pose, RenderTypes.entityCutout(s.texture), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
        if (s.glow != null && s.glowAlpha > 0.02F) {
            out.order(1).submitModel(m, s, pose, RenderTypes.entityTranslucentEmissive(s.glow), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ARGB.white(s.glowAlpha), null, 0);
        }
        pose.popPose();
        if (s.kind == CityCritter.HORSE && s.cart != null) {
            pose.pushPose();
            pose.translate(s.cartDx, s.cartDy, s.cartDz);
            pose.rotateDegrees(Axis.YP, 180F - s.cartYaw);
            pose.scale(-1F, -1F, 1F);
            pose.translate(0F, -1.501F, 0F);
            out.submitModel(cart, s.cart, pose, RenderTypes.entityCutout(cartSkin), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
            pose.popPose();
        }
        super.submit(s, pose, out, camera);
    }
}
