package dev.awaken.mobs.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.client.model.HeroModel;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Draws a hero body: our own texture with the creature's tint, its emissive glow layer (brighter while it winds up),
 * and the clips the entity cues. A move's wind-up is stretched to the ability's real wind-up so it peaks exactly as
 * the move fires; the strike follows from the wind-up's last pose; a wind-up cut short fades out instead of popping.
 * Beasts fall over their own death clip rather than vanilla's sideways flip.
 */
public final class HeroRenderer extends MobRenderer<AwakenMob, HeroRenderState, HeroModel> {
    /** Seconds a held wind-up waits for its strike before it fades, and how long the fade takes. */
    private static final float HOLD = 1.0F, FADE = 0.3F;

    private final Identifier texture;
    /** The closed-eyes skin (tools/art: {@code <id>_blink.png}) a blink draws, or null if the creature has none. */
    private final @Nullable Identifier blink;
    private final int[] windup, strike;
    private final float[] windupSeconds;
    private final int roar, warn;

    public HeroRenderer(EntityRendererProvider.Context ctx, MobDef def, HeroRig rig) {
        super(ctx, new HeroModel(ctx.bakeLayer(HeroModel.layer(rig.id)), rig), rig.shadow);
        this.texture = Awaken.id("textures/entity/mobs/" + def.id + ".png");
        this.blink = exists(Awaken.id("textures/entity/mobs/" + def.id + "_blink.png"));
        HeroModel m = getModel();
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
        addLayer(new Glow(this, Awaken.id("textures/entity/mobs/" + def.id + "_glow.png"), exists(Awaken.id("textures/entity/mobs/" + def.id + "_glow_blink.png"))));
    }

    static @Nullable Identifier exists(Identifier id) {
        return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? id : null;
    }

    static boolean shut(LivingEntityRenderState s) {
        Life l = Life.from(s);
        return l != null && l.closed() > 0.5F;
    }

    private static @Nullable Ability ability(MobDef d, String name) {
        for (MobDef.Phase p : d.phases) for (Ability a : p.abilities()) if (a.name().equals(name)) return a;
        return null;
    }

    @Override
    public HeroRenderState createRenderState() {
        return new HeroRenderState();
    }

    @Override
    public void extractRenderState(AwakenMob mob, HeroRenderState s, float partialTicks) {
        super.extractRenderState(mob, s, partialTicks);
        AwLook l = mob.look(partialTicks);
        s.setRenderData(AwModels.LOOK, l);
        if (!(mob instanceof FaunaMob f)) return;
        s.pose = l.fauna;
        s.gait = f.gait(partialTicks);
        s.speed = f.gaitSpeed();
        s.walk = Mth.clamp(s.walkAnimationSpeed * 1.6F, 0.0F, 1.0F);
        HeroRig rig = getModel().rig();
        s.run = Mth.clamp((s.speed - rig.runFrom) / Math.max(0.01F, rig.runFull - rig.runFrom), 0.0F, 1.0F);
        s.airborne = (l.fauna & FaunaMob.P_AIR) != 0;
        s.angry = (l.fauna & FaunaMob.P_ANGRY) != 0 ? 1.0F : 0.0F;
        float now = s.ageInTicks;
        // the cue: what it is doing now
        resolve(f.cue(), (now - f.cueStart()) / 20.0F, s, false);
        // the previous cue, only while a cut-short wind-up fades
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
        live(mob, s, l, f);
    }

    /** Sets cueClip / cueTime / cueWeight for a cue that started {@code since} seconds ago. */
    private void resolve(int cue, float since, HeroRenderState s, boolean held) {
        int stage = FaunaMob.cueStage(cue), slot = FaunaMob.cueSlot(cue);
        s.cueClip = -1;
        s.cueWeight = 0;
        s.cueTime = 0;
        HeroModel m = getModel();
        switch (stage) {
            case FaunaMob.CUE_WINDUP -> {
                if (slot < 0 || slot >= windup.length) return;
                int c = windup[slot];
                float len = m.length(c);
                float real = windupSeconds[slot];
                if (real <= 0) { // a move that is a roar
                    s.cueClip = c;
                    letGo(s, since, len);
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
                letGo(s, since, m.length(s.cueClip));
            }
            case FaunaMob.CUE_ROAR, FaunaMob.CUE_WARN -> {
                s.cueClip = stage == FaunaMob.CUE_ROAR ? roar : warn;
                letGo(s, since, m.length(s.cueClip));
            }
            default -> { }
        }
    }

    /** A one-shot cue plays to its end, then holds its last frame while it fades (no pop). */
    private static void letGo(HeroRenderState s, float since, float len) {
        s.cueTime = Math.min(since, len);
        s.cueWeight = since <= len ? 1.0F : Math.max(0.0F, 1.0F - (since - len) / 0.2F);
    }

    /** The creature's life: made once, fed this frame's cue, blow and fall, stepped (it also turns the head the slow way). */
    private void live(AwakenMob mob, HeroRenderState s, AwLook l, FaunaMob f) {
        HeroModel m = getModel();
        Life life = Life.of(l.life, m.life(), mob.getId());
        l.life = life;
        int stage = FaunaMob.cueStage(f.cue());
        life.hurt = l.hurt;
        life.windup = stage == FaunaMob.CUE_WINDUP && s.cueClip >= 0 && s.cueWeight > 0 && s.cueClip != roar
                ? Mth.clamp(s.cueTime / Math.max(0.01F, m.length(s.cueClip)), 0.0F, 1.0F) : -1;
        life.roar = s.cueWeight > 0 && s.cueClip >= 0 && (s.cueClip == roar || s.cueClip == warn);
        life.roarClip = roar >= 0;
        life.busy = s.cueWeight > 0.05F || s.melee >= 0 || s.hurt >= 0;
        life.dying = s.dying;
        life.deathLength = m.length(m.clip("death"));
        HeroRig rig = m.rig();
        life.stride = rig.walkStride + (rig.runStride - rig.walkStride) * s.run;
        life.walk = s.airborne ? 0.0F : s.walk;
        life.airborne = s.airborne;
        life.tick(s, true);
    }

    @Override
    public Identifier getTextureLocation(HeroRenderState s) {
        return blink != null && shut(s) ? blink : texture;
    }

    @Override
    protected int getModelTint(HeroRenderState s) {
        return look(s).tint;
    }

    @Override
    protected @Nullable RenderType getRenderType(HeroRenderState s, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (bodyVisible && look(s).translucent) return RenderTypes.entityTranslucent(getTextureLocation(s));
        return super.getRenderType(s, bodyVisible, forceTransparent, glowing);
    }

    @Override
    protected float getWhiteOverlayProgress(HeroRenderState s) {
        AwLook l = look(s);
        if (!l.flash) return 0.0F;
        return ((int) (s.ageInTicks * 0.8F)) % 2 == 0 ? 0.0F : Mth.clamp(0.4F + l.swell * 0.6F, 0.4F, 1.0F);
    }

    /** The body falls by its own death clip: no vanilla sideways flip. */
    @Override
    protected void setupRotations(HeroRenderState s, PoseStack pose, float bodyRot, float entityScale) {
        if (isShaking(s)) bodyRot += (float) (Math.cos(Mth.floor(s.ageInTicks) * 3.25F) * Math.PI * 0.4F);
        pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
    }

    private static AwLook look(HeroRenderState s) {
        AwLook l = s.getRenderData(AwModels.LOOK);
        return l != null ? l : DEFAULT;
    }

    private static final AwLook DEFAULT = new AwLook();

    /** The creature's emissive layer (eyes, veins, crowns), as bright as its look says (it flares while winding up). */
    private static final class Glow extends RenderLayer<HeroRenderState, HeroModel> {
        private final RenderType type;
        private final @Nullable RenderType shut;

        Glow(HeroRenderer parent, Identifier texture, @Nullable Identifier blink) {
            super(parent);
            this.type = RenderTypes.entityTranslucentEmissive(texture);
            this.shut = blink == null ? null : RenderTypes.entityTranslucentEmissive(blink);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s, float yRot, float xRot) {
            if (s.isInvisible) return;
            AwLook l = look(s);
            float a = Mth.clamp(l.glow * (l.translucent ? 0.8F : 1.0F), 0.0F, 1.0F);
            if (s.dying >= 0) a *= Math.max(0.0F, 1.0F - s.dying / (NamedBeast.DEATH_TICKS / 20.0F)); // the light goes out as it falls
            if (a <= 0.01F) return;
            collector.order(2).submitModel(getParentModel(), s, pose, shut != null && shut(s) ? shut : type, LightCoordsUtil.FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(s, 0.0F), ARGB.white(a), null, s.outlineColor);
        }
    }

    public static boolean has(MobDef d) {
        return HeroRig.get(d.id) != null;
    }

    public static HeroRenderer create(EntityRendererProvider.Context ctx, MobDef d) {
        HeroRig rig = HeroRig.get(d.id);
        if (rig == null) throw new IllegalStateException("no hero rig for " + d.id);
        return new HeroRenderer(ctx, d, rig);
    }
}
