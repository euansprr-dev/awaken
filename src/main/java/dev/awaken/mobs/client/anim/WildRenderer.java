package dev.awaken.mobs.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.client.model.HeroModel;
import dev.awaken.mobs.client.model.WildModel;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import dev.awaken.mobs.entity.WildPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Draws a P3 body (the water and cave creatures): the hero renderer's cues, glow and fall, plus a swimmer's pitch (the
 * body tilts with its swim, nose down as it dives), a drip lurker hanging upside down from the roof, and the held
 * poses and bridle the entity reports ({@link WildPose}).
 */
public final class WildRenderer extends MobRenderer<AwakenMob, WildRenderState, WildModel> {
    private static final float HOLD = 1.0F, FADE = 0.3F;

    private final Identifier texture;
    private final int[] windup, strike;
    private final float[] windupSeconds;
    private final int roar, warn;

    public WildRenderer(EntityRendererProvider.Context ctx, MobDef def, HeroRig rig) {
        super(ctx, new WildModel(ctx.bakeLayer(HeroModel.layer(rig.id)), rig), rig.shadow);
        this.texture = Awaken.id("textures/entity/mobs/" + def.id + ".png");
        WildModel m = getModel();
        List<String> names = FaunaMob.moveNames(def);
        windup = new int[names.size()];
        strike = new int[names.size()];
        windupSeconds = new float[names.size()];
        roar = m.clip("roar");
        warn = m.clip("warn");
        for (int i = 0; i < names.size(); i++) {
            String mv = rig.move(names.get(i));
            Ability a = ability(def, names.get(i));
            windupSeconds[i] = a == null ? 1.0F : (a.windup() + (a.heavy() ? 8 : 0)) / 20.0F;
            if ("roar".equals(mv) || "warn".equals(mv)) {
                windup[i] = m.clip(mv);
                strike[i] = -1;
                windupSeconds[i] = 0;
                continue;
            }
            windup[i] = mv != null ? m.clip(mv + "_windup") : -1;
            strike[i] = mv != null ? m.clip(mv + "_strike") : -1;
            if (windup[i] < 0) windup[i] = m.clip("windup");
            if (strike[i] < 0) strike[i] = m.clip("strike");
        }
        addLayer(new Glow(this, Awaken.id("textures/entity/mobs/" + def.id + "_glow.png")));
    }

    public static WildRenderer create(EntityRendererProvider.Context ctx, MobDef d) {
        HeroRig rig = HeroRig.get(d.id);
        if (rig == null) throw new IllegalStateException("no rig for " + d.id);
        return new WildRenderer(ctx, d, rig);
    }

    private static @Nullable Ability ability(MobDef d, String name) {
        for (MobDef.Phase p : d.phases) for (Ability a : p.abilities()) if (a.name().equals(name)) return a;
        return null;
    }

    @Override
    public WildRenderState createRenderState() {
        return new WildRenderState();
    }

    @Override
    public void extractRenderState(AwakenMob mob, WildRenderState s, float partialTicks) {
        super.extractRenderState(mob, s, partialTicks);
        AwLook l = mob.look(partialTicks);
        s.setRenderData(AwModels.LOOK, l);
        s.wild = mob instanceof WildPose w ? w.wildState() : 0;
        s.pitch = (s.wild & WildPose.S_SWIM) != 0 ? Mth.clamp(mob.getXRot(partialTicks), -60.0F, 60.0F) : 0.0F;
        if (!(mob instanceof FaunaMob f)) return;
        s.gait = f.gait(partialTicks);
        s.speed = f.gaitSpeed();
        s.walk = Mth.clamp(s.walkAnimationSpeed * 1.6F, 0.0F, 1.0F);
        HeroRig rig = getModel().rig();
        s.run = Mth.clamp((s.speed - rig.runFrom) / Math.max(0.01F, rig.runFull - rig.runFrom), 0.0F, 1.0F);
        s.angry = (l.fauna & FaunaMob.P_ANGRY) != 0 ? 1.0F : 0.0F;
        float now = s.ageInTicks;
        resolve(f.cue(), (now - f.cueStart()) / 20.0F, s, false);
        s.prevClip = -1;
        s.prevWeight = 0;
        if (FaunaMob.cueStage(f.cue()) == FaunaMob.CUE_NONE && FaunaMob.cueStage(f.prevCue()) == FaunaMob.CUE_WINDUP) {
            float fade = (now - f.prevCueEnd()) / 20.0F;
            if (fade < FADE) {
                int clip = s.cueClip;
                float time = s.cueTime;
                resolve(f.prevCue(), (f.prevCueEnd() - f.prevCueStart()) / 20.0F, s, true);
                s.prevClip = s.cueClip;
                s.prevTime = s.cueTime;
                s.prevWeight = 1.0F - fade / FADE;
                s.cueClip = clip;
                s.cueTime = time;
            }
        }
        int melee = mob.tickCount - f.meleeStart();
        s.melee = melee >= 0 && melee < 40 ? (now - f.meleeStart()) / 20.0F : -1;
        s.hurt = l.hurt > 0 ? (1.0F - l.hurt) * mob.hurtDuration / 20.0F : -1;
        s.dying = mob.isDeadOrDying() ? (f.dyingTicks() + partialTicks) / 20.0F : -1;
        if (s.dying >= 0) s.hasRedOverlay = f.dyingTicks() < 8 || mob.hurtTime > 0;
    }

    private void resolve(int cue, float since, WildRenderState s, boolean held) {
        int stage = FaunaMob.cueStage(cue), slot = FaunaMob.cueSlot(cue);
        s.cueClip = -1;
        s.cueWeight = 0;
        s.cueTime = 0;
        WildModel m = getModel();
        switch (stage) {
            case FaunaMob.CUE_WINDUP -> {
                if (slot < 0 || slot >= windup.length) return;
                int c = windup[slot];
                float len = m.length(c);
                float real = windupSeconds[slot];
                if (real <= 0) {
                    s.cueClip = c;
                    s.cueTime = since;
                    s.cueWeight = since <= len ? 1.0F : 0.0F;
                    return;
                }
                s.cueClip = c;
                s.cueTime = Math.min(len, since * len / real);
                float over = since - real - (held ? 0 : HOLD);
                s.cueWeight = held ? 1.0F : over <= 0 ? 1.0F : Math.max(0.0F, 1.0F - over / FADE);
            }
            case FaunaMob.CUE_STRIKE -> {
                if (slot < 0 || slot >= strike.length) return;
                s.cueClip = strike[slot];
                s.cueTime = since;
                s.cueWeight = since <= m.length(s.cueClip) ? 1.0F : 0.0F;
            }
            case FaunaMob.CUE_ROAR, FaunaMob.CUE_WARN -> {
                s.cueClip = stage == FaunaMob.CUE_ROAR ? roar : warn;
                s.cueTime = since;
                s.cueWeight = since <= m.length(s.cueClip) ? 1.0F : 0.0F;
            }
            default -> { }
        }
    }

    @Override
    public Identifier getTextureLocation(WildRenderState s) {
        return texture;
    }

    @Override
    protected int getModelTint(WildRenderState s) {
        return look(s).tint;
    }

    @Override
    protected @Nullable RenderType getRenderType(WildRenderState s, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (bodyVisible && look(s).translucent) return RenderTypes.entityTranslucent(texture);
        return super.getRenderType(s, bodyVisible, forceTransparent, glowing);
    }

    @Override
    protected float getWhiteOverlayProgress(WildRenderState s) {
        AwLook l = look(s);
        if (!l.flash) return 0.0F;
        return ((int) (s.ageInTicks * 0.8F)) % 2 == 0 ? 0.0F : Mth.clamp(0.4F + l.swell * 0.6F, 0.4F, 1.0F);
    }

    /** Yaw; a swimmer pitches about its middle; a hanging lurker hangs upside down from the roof. No death flip. */
    @Override
    protected void setupRotations(WildRenderState s, PoseStack pose, float bodyRot, float entityScale) {
        if (isShaking(s)) bodyRot += (float) (Math.cos(Mth.floor(s.ageInTicks) * 3.25F) * Math.PI * 0.4F);
        pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
        // (the pose is already scaled by the entity's scale here, so heights are divided by it)
        if ((s.wild & WildPose.S_HANG) != 0) {
            pose.translate(0.0F, s.boundingBoxHeight / entityScale, 0.0F);
            pose.rotateDegrees(Axis.ZP, 180.0F);
        } else if (s.pitch != 0.0F && s.dying < 0) {
            float h = s.boundingBoxHeight * 0.5F / entityScale;
            pose.translate(0.0F, h, 0.0F);
            pose.rotateDegrees(Axis.XP, -s.pitch);
            pose.translate(0.0F, -h, 0.0F);
        }
    }

    private static AwLook look(WildRenderState s) {
        AwLook l = s.getRenderData(AwModels.LOOK);
        return l != null ? l : DEFAULT;
    }

    private static final AwLook DEFAULT = new AwLook();

    /** The emissive layer (lures, crystals, glowing gills), as bright as its look says; it goes out as it dies. */
    private static final class Glow extends RenderLayer<WildRenderState, WildModel> {
        private final RenderType type;

        Glow(WildRenderer parent, Identifier texture) {
            super(parent);
            this.type = RenderTypes.entityTranslucentEmissive(texture);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, WildRenderState s, float yRot, float xRot) {
            if (s.isInvisible) return;
            AwLook l = look(s);
            float a = Mth.clamp(l.glow * (l.translucent ? 0.8F : 1.0F), 0.0F, 1.0F);
            if (s.dying >= 0) a *= Math.max(0.0F, 1.0F - s.dying / (NamedBeast.DEATH_TICKS / 20.0F));
            if (a <= 0.01F) return;
            collector.order(2).submitModel(getParentModel(), s, pose, type, LightCoordsUtil.FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(s, 0.0F), ARGB.white(a), null, s.outlineColor);
        }
    }
}
