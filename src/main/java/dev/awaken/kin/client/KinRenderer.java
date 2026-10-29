package dev.awaken.kin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.kin.KinBeast;
import dev.awaken.kin.KinKind;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the kin (SPEC §43.5) from their rigs ({@link KinData}): what each is doing (its synced act) picks the clip,
 * moving swaps in its walk or run, one-shots (the tell, a nudge, a sniff) play once from when they start, and clips
 * crossfade. A Lampwing's belly is a lamp: it glows at night, dimmer by day and dimmest asleep; a Pebblepup's crystals
 * catch the light in the dark. Nothing in the air casts a shadow.
 */
public final class KinRenderer extends EntityRenderer<KinBeast, KinRenderState> {
    static final float FADE = 5F;

    private static @Nullable Map<String, HeroRig> rigs;

    static synchronized Map<String, HeroRig> rigs() {
        if (rigs == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : KinData.SOURCES) {
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

    /** What the client remembers of one beast's clips (the server only sends its act). */
    private static final class Anim {
        int clip = -1, prev = -1;
        float start, prevStart, prevEnd = -100F;
        @Nullable Object life;
    }

    private final Map<KinBeast, Anim> anims = new WeakHashMap<>();
    private final KinKind kind;
    private final KinModel model;
    private final Identifier skin;
    private final @Nullable Identifier blink, glow;
    private final float scale;
    private final net.minecraft.client.renderer.item.ItemModelResolver items;

    public KinRenderer(EntityRendererProvider.Context ctx, KinKind kind) {
        super(ctx);
        this.kind = kind;
        String id = "kin_" + kind.id();
        model = new KinModel(ctx.bakeLayer(layer(id)), rigs().get(id), KinData.TEMPER.getOrDefault(id, "cute"));
        var rm = Minecraft.getInstance().getResourceManager();
        String base = "textures/entity/kin/" + kind.id();
        skin = Awaken.id(base + ".png");
        Identifier b = Awaken.id(base + "_blink.png"), g = Awaken.id(base + "_glow.png");
        blink = rm.getResource(b).isPresent() ? b : null;
        glow = rm.getResource(g).isPresent() ? g : null;
        scale = KinData.SCALE.getOrDefault(id, 1F);
        items = ctx.getItemModelResolver();
    }

    @Override
    public KinRenderState createRenderState() {
        return new KinRenderState();
    }

    /** The clip for an act, swapping in its walk or run while it moves. */
    static String clipFor(int act, double speed) {
        boolean moving = speed > 0.015, fast = speed > 0.12;
        return switch (act) {
            case KinBeast.SLEEP -> "sleep";
            case KinBeast.HIDE -> moving ? "run" : "hide";
            case KinBeast.TELL -> "tell";
            case KinBeast.NUDGE -> "nudge";
            case KinBeast.SNIFF -> "sniff";
            case KinBeast.FLEE -> moving ? "run" : "idle";
            case KinBeast.WANT -> moving ? "move" : "want";
            case KinBeast.WORK -> moving ? "carry" : "work";
            case KinBeast.CARRY -> "carry";
            default -> moving ? (fast ? "run" : "move") : "idle";
        };
    }

    @Override
    public void extractRenderState(KinBeast e, KinRenderState s, float partial) {
        super.extractRenderState(e, s, partial);
        float age = e.tickCount + partial;
        s.ageInTicks = age;
        s.bodyRot = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        s.yRot = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - s.bodyRot), -100F, 100F);
        s.xRot = e.getXRot(partial);
        double speed = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        s.walkAnimationSpeed = Mth.clamp((float) speed * 6F, 0F, 1F);
        s.modelScale = scale;
        s.airborne = kind.flies() && e.act() != KinBeast.SLEEP;
        Anim a = anims.computeIfAbsent(e, x -> new Anim());
        int want = model.clip(clipFor(e.act(), speed));
        if (want < 0) want = model.clip("idle");
        if (want != a.clip) {
            a.prev = a.clip;
            a.prevStart = a.start;
            a.prevEnd = age;
            a.clip = want;
            a.start = age;
        }
        s.clip = a.clip;
        s.clipTime = Math.max(0F, (age - a.start) / 20F);
        s.prevClip = a.prev;
        float since = age - a.prevEnd;
        s.prevWeight = a.prev >= 0 && since < FADE ? 1F - Mth.smoothstep(Mth.clamp(since / FADE, 0F, 1F)) : 0F;
        s.prevTime = Math.max(0F, (age - a.prevStart) / 20F);
        Life life = CreatureLife.life(a.life, model.life, e.getId() * 31 + kind.ordinal());
        a.life = life;
        life.walk = s.walkAnimationSpeed;
        life.airborne = s.airborne;
        life.busy = s.clipTime < 0.2F;
        CreatureLife.tick(life, s, true);
        s.life = life;
        s.texture = blink != null && e.act() == KinBeast.SLEEP ? blink : skin;
        if (blink != null && CreatureLife.eyesClosed(life) > 0.5F) s.texture = blink;
        s.glow = glow;
        s.glowAlpha = 0F;
        s.backY = e.getBbHeight() * 0.95F;
        if (e.held().isEmpty()) s.held.clear();
        else items.updateForTopItem(s.held, e.held(), net.minecraft.world.item.ItemDisplayContext.GROUND, e.level(), e, e.getId());
        if (glow != null && e.level() != null) {
            int block = LightCoordsUtil.block(s.lightCoords), sky = LightCoordsUtil.sky(s.lightCoords);
            float dark = e.level().getSkyDarken() / 11F;
            float lit = Math.max(block, sky * (1F - dark)) / 15F;
            if (kind == KinKind.LAMPWING) {
                // a lamp: always a little lit, full at night, low while it sleeps
                float on = Mth.clamp(1.15F - lit * 0.7F, 0.45F, 1F);
                if (e.act() == KinBeast.SLEEP) on = 0.18F;
                if (e.act() == KinBeast.TELL) on = 1F;
                s.glowAlpha = on;
            } else s.glowAlpha = Mth.clamp(1.0F - lit * 1.4F, 0F, 0.7F);
        }
    }

    @Override
    protected float getShadowRadius(KinRenderState s) {
        return s.airborne ? 0F : 0.3F * s.modelScale / 0.7F;
    }

    @Override
    public void submit(KinRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, 180F - s.bodyRot);
        if (s.life != null) CreatureLife.rootPose(s.life, pose, s);
        pose.scale(-s.modelScale, -s.modelScale, s.modelScale);
        pose.translate(0F, -1.501F, 0F);
        Identifier tex = s.texture == null ? skin : s.texture;
        out.submitModel(model, s, pose, RenderTypes.entityCutout(tex), s.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, s.outlineColor);
        if (s.glow != null && s.glowAlpha > 0.02F) {
            out.order(1).submitModel(model, s, pose, RenderTypes.entityTranslucentEmissive(s.glow), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ARGB.white(s.glowAlpha), null, 0);
        }
        pose.popPose();
        if (!s.held.isEmpty()) {
            // carried on its back, riding its walk a little
            pose.pushPose();
            pose.rotateDegrees(Axis.YP, 180F - s.bodyRot);
            pose.translate(0F, s.backY + Mth.sin(s.ageInTicks * 0.5F) * 0.02F * s.walkAnimationSpeed, 0F);
            pose.rotateDegrees(Axis.YP, 90F);
            pose.scale(0.75F, 0.75F, 0.75F);
            s.held.submit(pose, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
            pose.popPose();
        }
        super.submit(s, pose, out, camera);
    }
}
