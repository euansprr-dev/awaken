package dev.awaken.kin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.kin.FamilyPet;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.CreatureLife;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.model.HeroModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws Aya and Dubu (SPEC §68.9) from their rigs ({@link FamilyData}), as the kin are drawn: the pose it holds picks the
 * clip of the same name, moving swaps in its walk or run, clips crossfade, and the life layer (breath, blinks, springy
 * ears and tail) goes on top. Asleep, its eyes are shut. Two things are theirs: a walk or a run is played by the ground
 * covered, a stride a cycle, so feet stay where they land (up to a cap, past which they patter as the game's own animals
 * do); and sitting down, lying down, getting up and the flop onto her back take half a second, not a quarter.
 */
public final class FamilyRenderer extends EntityRenderer<FamilyPet, KinRenderState> {
    private static @Nullable Map<String, HeroRig> rigs;

    static synchronized Map<String, HeroRig> rigs() {
        if (rigs == null) {
            Map<String, HeroRig> m = new LinkedHashMap<>();
            for (String[] src : FamilyData.SOURCES) {
                HeroRig r = HeroRig.parse(src);
                m.put(r.id, r);
            }
            rigs = m;
        }
        return rigs;
    }

    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (HeroRig r : rigs().values()) e.registerLayerDefinition(KinRenderer.layer(r.id), () -> HeroModel.create(r));
    }

    /** What the client remembers of one body's clips (the server only sends its pose). */
    private static final class Anim {
        int clip = -1, prev = -1;
        float start, prevStart, prevEnd = -100F;
        /** Strides taken (cycles of a walk or run), and where and when it was last drawn. */
        float phase, lastAge = -1F;
        double lastX, lastZ;
        @Nullable Object life;
    }

    private final Map<FamilyPet, Anim> anims = new WeakHashMap<>();
    private final KinModel model;
    private final Identifier skin;
    private final @Nullable Identifier blink;
    private final float scale;
    private final int salt;

    public FamilyRenderer(EntityRendererProvider.Context ctx, String name) {
        super(ctx);
        String id = "family_" + name;
        model = new KinModel(ctx.bakeLayer(KinRenderer.layer(id)), rigs().get(id), FamilyData.TEMPER.getOrDefault(id, "cute"));
        String base = "textures/entity/family/" + name;
        skin = Awaken.id(base + ".png");
        Identifier b = Awaken.id(base + "_blink.png");
        blink = Minecraft.getInstance().getResourceManager().getResource(b).isPresent() ? b : null;
        scale = FamilyData.SCALE.getOrDefault(id, 0.5F);
        salt = name.hashCode();
    }

    @Override
    public KinRenderState createRenderState() {
        return new KinRenderState();
    }

    /** The clip for a pose: its own, or its walk or run while it moves (the giraffe stays in a carrying mouth). */
    private boolean gait(int clip) {
        return clip >= 0 && (clip == model.clip("move") || clip == model.clip("run") || clip == model.clip("carry"));
    }

    /** A pose it settles into or gets up from (those blends are slower: a movement, not a pop). */
    private boolean settles(int clip) {
        return clip >= 0 && !gait(clip) && clip != model.clip("idle") && clip != model.clip("nudge");
    }

    private int clipFor(int act, double speed) {
        boolean moving = speed > 0.015, fast = speed > 0.15;
        String name = act >= 0 && act < FamilyPet.ACTS.length ? FamilyPet.ACTS[act] : "idle";
        if (moving) name = "carry".equals(name) ? "carry" : fast ? "run" : "move";
        int clip = model.clip(name);
        if (clip < 0 && "rest".equals(name)) clip = model.clip(model.clip("loaf") >= 0 ? "loaf" : "lie");
        return clip < 0 ? model.clip("idle") : clip;
    }

    @Override
    public void extractRenderState(FamilyPet e, KinRenderState s, float partial) {
        super.extractRenderState(e, s, partial);
        float age = e.tickCount + partial;
        s.ageInTicks = age;
        s.bodyRot = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        s.yRot = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - s.bodyRot), -100F, 100F);
        s.xRot = e.getXRot(partial);
        double speed = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
        s.walkAnimationSpeed = Mth.clamp((float) speed * 6F, 0F, 1F);
        s.modelScale = scale;
        s.airborne = false;
        s.gnawed = false;
        Anim a = anims.computeIfAbsent(e, x -> new Anim());
        int want = clipFor(e.act(), speed);
        if (want != a.clip) {
            a.prev = a.clip;
            a.prevStart = a.start;
            a.prevEnd = age;
            a.clip = want;
            a.start = age;
        }
        // strides by the ground covered: a walk's cycle is one stride long, a run's one bound
        double x = Mth.lerp(partial, e.xo, e.getX()), z = Mth.lerp(partial, e.zo, e.getZ());
        float dt = a.lastAge < 0F ? 0F : Mth.clamp(age - a.lastAge, 0F, 3F);
        if (dt > 0F) {
            boolean run = a.clip == model.clip("run");
            float stride = Math.max(0.1F, run ? model.rig().runStride : model.rig().walkStride);
            a.phase += Math.min((float) Math.hypot(x - a.lastX, z - a.lastZ) / stride, (run ? 0.19F : 0.175F) * dt);
        }
        a.lastAge = age;
        a.lastX = x;
        a.lastZ = z;
        s.clip = a.clip;
        s.clipTime = gait(a.clip) ? a.phase * model.length(a.clip) : Math.max(0F, (age - a.start) / 20F);
        s.prevClip = a.prev;
        float since = age - a.prevEnd, fade = settles(a.clip) || settles(a.prev) ? 10F : KinRenderer.FADE;
        s.prevWeight = a.prev >= 0 && since < fade ? 1F - Mth.smoothstep(Mth.clamp(since / fade, 0F, 1F)) : 0F;
        s.prevTime = gait(a.prev) ? a.phase * model.length(a.prev) : Math.max(0F, (age - a.prevStart) / 20F);
        Life life = CreatureLife.life(a.life, model.life, e.getId() * 31 + salt);
        a.life = life;
        life.walk = s.walkAnimationSpeed;
        life.airborne = false;
        life.busy = !gait(a.clip) && s.clipTime < 0.2F;
        CreatureLife.tick(life, s, true);
        s.life = life;
        boolean asleep = "sleep".equals(FamilyPet.ACTS[Math.max(0, Math.min(FamilyPet.ACTS.length - 1, e.act()))]);
        s.texture = blink != null && (asleep || CreatureLife.eyesClosed(life) > 0.5F) ? blink : skin;
        s.glow = null;
        s.glowAlpha = 0F;
        s.held.clear();
    }

    @Override
    protected float getShadowRadius(KinRenderState s) {
        return 0.3F * s.modelScale / 0.7F;
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
        pose.popPose();
        super.submit(s, pose, out, camera);
    }
}
