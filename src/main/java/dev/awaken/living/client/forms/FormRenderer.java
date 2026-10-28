package dev.awaken.living.client.forms;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.living.combat.Forms.Species;
import dev.awaken.mobs.client.anim.HeroRenderState;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.life.LifeProfile;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.client.model.HeroModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Draws one form body (SPEC §21.13): a hero rig of ours ({@link HeroModel}, its clips and the life layer) on a vanilla creature, painted
 * for its species and land, with its eyes lit in the rank's colour and a soft halo round each (faint by day, strong at night), the
 * move the server cues acted out (the wind-up stretched to the move's real length, then the strike), its own fall instead of vanilla's
 * sideways flip, and the creeper's swell. One renderer per rig; {@link FormBodies} hands it each creature's {@link FormBody}.
 */
final class FormRenderer extends MobRenderer<Mob, HeroRenderState, HeroModel> {
    /** The rank colours (D gold, C orange, B red, A violet, S indigo), as the plate and the old glow. */
    static final int[] RANK = {0xFFFFFF, 0xE4D063, 0xF29A48, 0xEC5656, 0xB07CFF, 0x8C7BFF};
    /** Beyond this (squared) the eye halos are not drawn (they are a few pixels there). */
    static final double HALO_SQ = 48.0 * 48.0;
    private static final float HOLD = 1.0F, FADE = 0.3F;

    final HeroRig rig;
    final int rung;
    final String body;
    /** [species][family]: skin, closed-eye skin; glow and closed-eye glow by species. Null where not painted. */
    private final Identifier[][] skin, skinShut;
    /** [species][family]: the coats the game decides, drawn over the body by {@link Coat}: a sheep's wool, a pig's saddle. */
    private final Identifier[][] wool, saddle;
    private final RenderType[] glow, glowShut;
    private final RenderType halo;
    private final int roar, deathClip, angry, angryMelee;
    private final float deathLength;
    /** The chains from the root to each eye (empty: no eyes to light). */
    final ModelPart[][] eyes;
    final float scale;

    FormRenderer(EntityRendererProvider.Context ctx, HeroRig rig, String body, int rung, ModelLayerLocation layer) {
        super(ctx, new HeroModel(ctx.bakeLayer(layer), rig, LifeProfile.temperament(temperament(body))), rig.shadow);
        this.rig = rig;
        this.body = body;
        this.rung = rung;
        this.scale = 1.0F;
        Species[] all = Species.values();
        skin = new Identifier[all.length][3];
        skinShut = new Identifier[all.length][3];
        wool = new Identifier[all.length][3];
        saddle = new Identifier[all.length][3];
        glow = new RenderType[all.length];
        glowShut = new RenderType[all.length];
        for (Species sp : all) {
            if (!sp.layout.equals(body)) continue;
            String base = "textures/entity/forms/body/" + rig.id + "_" + sp.id;
            for (int f = 0; f < 3; f++) {
                skin[sp.ordinal()][f] = exists(Awaken.id(base + "_" + f + ".png"));
                skinShut[sp.ordinal()][f] = exists(Awaken.id(base + "_" + f + "_blink.png"));
                wool[sp.ordinal()][f] = exists(Awaken.id(base + "_" + f + "_wool.png"));
                saddle[sp.ordinal()][f] = exists(Awaken.id(base + "_" + f + "_saddle.png"));
            }
            Identifier g = exists(Awaken.id(base + "_glow.png")), gs = exists(Awaken.id(base + "_glow_blink.png"));
            glow[sp.ordinal()] = g == null ? null : RenderTypes.entityTranslucentEmissive(g);
            glowShut[sp.ordinal()] = gs == null ? null : RenderTypes.entityTranslucentEmissive(gs);
        }
        halo = RenderTypes.eyes(Awaken.id("textures/entity/forms/halo.png"));
        HeroModel m = getModel();
        roar = m.clip("roar");
        angry = m.clip("angry");
        angryMelee = m.clip("angry_melee");
        deathClip = m.clip("death");
        deathLength = m.length(deathClip);
        eyes = new ModelPart[][]{chain(m, rig, "eye_l"), chain(m, rig, "eye_r")};
        addLayer(new Glow(this));
        if (body.equals("sheep") || body.equals("pig")) addLayer(new Coat(this));
        if (FormGear.humanoid(body)) addLayer(new FormGear(this, ctx));
    }

    /** The life layer's temperament for a body: the walkers and the creeper are monsters, the spider an insect, the animals grazers. */
    static String temperament(String body) {
        return switch (body) {
            case "zombie", "skeleton", "creeper" -> "monster";
            case "spider" -> "insect";
            default -> "grazer";
        };
    }

    static @Nullable Identifier exists(Identifier id) {
        return Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? id : null;
    }

    /** The parts from the model's root down to a bone, so a layer can stand at it (empty if the rig has no such bone). */
    static ModelPart[] chain(HeroModel m, HeroRig rig, String bone) {
        int i = rig.bone(bone);
        if (i < 0) return new ModelPart[0];
        int n = 0;
        for (int k = i; k >= 0; k = rig.bones.get(k).parent()) n++;
        ModelPart[] out = new ModelPart[n + 1];
        Function<String, ModelPart> lookup = m.root().createPartLookup();
        out[0] = m.root();
        for (int k = i, at = n; k >= 0; k = rig.bones.get(k).parent(), at--) out[at] = lookup.apply(rig.bones.get(k).name());
        return out;
    }

    static void standAt(ModelPart[] chain, PoseStack pose) {
        for (ModelPart p : chain) p.translateAndRotate(pose);
    }

    boolean paints(Species sp) {
        return skin[sp.ordinal()][0] != null;
    }

    @Override
    public HeroRenderState createRenderState() {
        return new HeroRenderState();
    }

    @Override
    public void extractRenderState(Mob mob, HeroRenderState s, float partialTicks) {
        super.extractRenderState(mob, s, partialTicks);
        FormBody b = FormBodies.bodyOf(mob);
        if (b == null) return;
        s.setRenderData(AwModels.LOOK, b.look);
        s.setRenderData(FormBodies.BODY, b);
        // the walk: distance on the ground, frame to frame, so a cycle covers exactly its stride
        double dx = s.x - b.lastX, dz = s.z - b.lastZ, step = Math.sqrt(dx * dx + dz * dz);
        if (b.placed && step < 2.0) b.gait += (float) step;
        b.lastX = s.x;
        b.lastZ = s.z;
        b.placed = true;
        if (b.gait > 10_000) b.gait -= 10_000;
        s.gait = b.gait;
        s.speed = (float) Math.hypot(mob.getX() - mob.xo, mob.getZ() - mob.zo);
        s.walk = Mth.clamp(s.walkAnimationSpeed * 1.6F, 0.0F, 1.0F);
        s.run = Mth.clamp((s.speed - rig.runFrom) / Math.max(0.01F, rig.runFull - rig.runFrom), 0.0F, 1.0F);
        s.airborne = !mob.onGround() && !mob.isInWater() && !mob.onClimbable() && mob.getDeltaMovement().y < -0.1;
        float swell = mob instanceof Creeper c ? c.getSwelling(partialTicks) : 0.0F;
        b.look.swell = swell;
        s.angry = mob instanceof Creeper ? Mth.clamp(swell * 1.4F, 0.0F, 1.0F) : mob.isAggressive() ? 1.0F : 0.0F;
        // a skeleton with a blade hunts in its own stance (HeroModel's angry clip is the bow's), carried in the free clip slot
        b.stance = angryMelee >= 0 && b.vanilla instanceof SkeletonRenderState k && !k.isHoldingBow ? s.angry : 0.0F;
        if (b.stance > 0) s.angry = 0.0F;
        // the swing: a melee blow starts its clip (from B the clip is the two-hit the server deals)
        if (mob.isSwinging() && !b.wasSwinging) b.meleeStart = s.ageInTicks;
        b.wasSwinging = mob.isSwinging();
        float melee = (s.ageInTicks - b.meleeStart) / 20.0F;
        s.melee = melee >= 0 && melee < 2.0F ? melee : -1;
        b.look.hurt = mob.hurtTime > 0 ? Mth.clamp((mob.hurtTime - partialTicks) / 10.0F, 0.0F, 1.0F) : 0.0F;
        s.hurt = mob.hurtTime > 0 ? (10 - mob.hurtTime + partialTicks) / 20.0F : -1;
        s.dying = mob.deathTime > 0 || mob.isDeadOrDying() ? (mob.deathTime + partialTicks) / 20.0F : -1;
        resolveCue(mob, b, s, partialTicks);
        live(mob, b, s);
    }

    /** Which clip the move plays now, how far in, how strongly; a wind-up cut short lets go over a moment. */
    private void resolveCue(Mob mob, FormBody b, HeroRenderState s, float partialTicks) {
        HeroModel m = getModel();
        if (b.cueDirty) {
            b.cueDirty = false;
            if (b.cue.isEmpty()) {
                b.cueWindupClip = b.cueStrikeClip = b.cueOnceClip = -1;
            } else {
                b.cueWindupClip = m.clip(b.cue + "_windup");
                b.cueStrikeClip = m.clip(b.cue + "_strike");
                b.cueOnceClip = b.cueWindupClip < 0 ? m.clip(b.cue) : -1; // a roar is one clip
            }
        }
        s.cueClip = -1;
        s.cueWeight = 0;
        s.cueTime = 0;
        float since = (float) (mob.level().getGameTime() - b.cueStart + partialTicks) / 20.0F;
        if (b.cueOnceClip >= 0) {
            s.cueClip = b.cueOnceClip;
            letGo(s, since, m.length(s.cueClip));
        } else if (b.cueWindupClip >= 0 || b.cueStrikeClip >= 0) {
            float real = Math.max(0.05F, b.cueWindup / 20.0F), hold = b.cueHold / 20.0F;
            if (since < real + hold && b.cueWindupClip >= 0) {
                float len = m.length(b.cueWindupClip);
                s.cueClip = b.cueWindupClip;
                s.cueTime = Math.min(len, since * len / real);
                s.cueWeight = 1.0F;
                b.fadeTime = s.cueTime;
            } else if (b.cueStrikeClip >= 0) {
                s.cueClip = b.cueStrikeClip;
                letGo(s, since - real - hold, m.length(s.cueClip));
            } else {
                float over = since - real - hold - HOLD;
                s.cueClip = b.cueWindupClip;
                s.cueTime = m.length(s.cueClip);
                s.cueWeight = over <= 0 ? 1.0F : Math.max(0.0F, 1.0F - over / FADE);
            }
        }
        // a wind-up the server ended early fades from the pose it had reached
        s.prevClip = -1;
        s.prevWeight = 0;
        if (b.fadeClip >= 0) {
            if (Float.isNaN(b.fadeAt)) b.fadeAt = s.ageInTicks;
            float fade = (s.ageInTicks - b.fadeAt) / 20.0F;
            if (fade < FADE) {
                s.prevClip = b.fadeClip;
                s.prevTime = b.fadeTime;
                s.prevWeight = 1.0F - fade / FADE;
            } else b.fadeClip = -1;
        }
        // otherwise the slot carries the hunting stance on the move (HeroModel lets the angry clip go while it walks): a zombie chases
        // with its arms up, as it always has
        if (s.prevClip < 0 && b.stance > 0) {
            Life l = Life.from(s);
            s.prevClip = angryMelee;
            s.prevTime = l != null ? l.idleTime() : s.ageInTicks / 20.0F;
            s.prevWeight = b.stance;
        } else if (s.prevClip < 0 && angry >= 0 && s.angry > 0 && s.walk > 0.01F) {
            Life l = Life.from(s);
            s.prevClip = angry;
            s.prevTime = l != null ? l.idleTime() : s.ageInTicks / 20.0F;
            s.prevWeight = s.angry * s.walk;
        }
    }

    private static void letGo(HeroRenderState s, float since, float len) {
        if (since < 0) return;
        s.cueTime = Math.min(since, len);
        s.cueWeight = since <= len ? 1.0F : Math.max(0.0F, 1.0F - (since - len) / 0.2F);
    }

    /** The creature's life: made once, fed this frame's move, blow and fall, stepped (the head turns the slow way). */
    private void live(Mob mob, FormBody b, HeroRenderState s) {
        HeroModel m = getModel();
        Life life = Life.of(b.look.life, m.life(), mob.getId());
        b.look.life = life;
        life.hurt = b.look.hurt;
        life.entity = mob.getId();
        life.windup = s.cueClip >= 0 && s.cueClip == b.cueWindupClip && s.cueWeight > 0
                ? Mth.clamp(s.cueTime / Math.max(0.01F, m.length(s.cueClip)), 0.0F, 1.0F) : -1;
        life.roar = s.cueWeight > 0 && s.cueClip >= 0 && s.cueClip == roar;
        life.roarClip = roar >= 0;
        life.busy = s.cueWeight > 0.05F || s.melee >= 0 || s.hurt >= 0;
        life.dying = s.dying;
        life.deathLength = deathLength;
        life.aggressive = s.angry > 0.5F || b.stance > 0.5F;
        life.stride = rig.walkStride + (rig.runStride - rig.walkStride) * s.run;
        life.walk = s.airborne ? 0.0F : s.walk;
        life.airborne = s.airborne;
        life.swimming = mob.isInWater();
        life.tick(s, true);
    }

    @Override
    public Identifier getTextureLocation(HeroRenderState s) {
        FormBody b = s.getRenderData(FormBodies.BODY);
        if (b == null) return FormBodies.MISSING;
        int sp = b.species.ordinal(), f = skin[sp][b.family] != null ? b.family : 0;
        Identifier shut = skinShut[sp][f];
        if (shut != null && shut(s)) return shut;
        Identifier t = skin[sp][f];
        return t != null ? t : FormBodies.MISSING;
    }

    static boolean shut(HeroRenderState s) {
        Life l = Life.from(s);
        return l != null && l.closed() > 0.5F;
    }

    /** The creeper's swell: it flashes white and puffs up, as vanilla's does. */
    @Override
    protected float getWhiteOverlayProgress(HeroRenderState s) {
        FormBody b = s.getRenderData(FormBodies.BODY);
        float swell = b != null ? b.look.swell : 0.0F;
        return (int) (swell * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swell, 0.5F, 1.0F);
    }

    @Override
    protected void scale(HeroRenderState s, PoseStack pose) {
        FormBody b = s.getRenderData(FormBodies.BODY);
        float k = b != null && b.species == Species.CAVE_SPIDER ? 0.7F : 1.0F;
        if (b != null && b.look.swell > 0) {
            float swell = b.look.swell;
            float wobble = 1.0F + Mth.sin(swell * 100.0F) * swell * 0.01F;
            swell = Mth.clamp(swell, 0.0F, 1.0F);
            swell *= swell;
            swell *= swell;
            float xz = (1.0F + swell * 0.4F) * wobble, y = (1.0F + swell * 0.1F) / wobble;
            pose.scale(k * xz, k * y, k * xz);
            return;
        }
        if (k != 1.0F) pose.scale(k, k, k);
    }

    /** The body falls by its own death clip: no vanilla sideways flip. */
    @Override
    protected void setupRotations(HeroRenderState s, PoseStack pose, float bodyRot, float entityScale) {
        if (isShaking(s)) bodyRot += (float) (Math.cos(Mth.floor(s.ageInTicks) * 3.25F) * Math.PI * 0.4F);
        pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
    }

    /** The eyes and seams (emissive, in the rank's light, flaring as a move winds up) and a soft halo round each eye. */
    /** A coat over the body from its own texture: the wool in the sheep's colour unless it is shorn; the saddle when it wears one. */
    private static final class Coat extends RenderLayer<HeroRenderState, HeroModel> {
        private final FormRenderer r;

        Coat(FormRenderer r) {
            super(r);
            this.r = r;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s, float yRot, float xRot) {
            FormBody b = s.getRenderData(FormBodies.BODY);
            if (b == null || s.isInvisible) return;
            int sp = b.species.ordinal();
            if (b.vanilla instanceof SheepRenderState sheep && !sheep.isSheared && r.wool[sp][b.family] != null) {
                coloredCutoutModelCopyLayerRender(getParentModel(), r.wool[sp][b.family], pose, collector, light, s, sheep.getWoolColor(), 1);
            }
            if (b.vanilla instanceof PigRenderState pig && !pig.saddle.isEmpty() && r.saddle[sp][b.family] != null) {
                coloredCutoutModelCopyLayerRender(getParentModel(), r.saddle[sp][b.family], pose, collector, light, s, -1, 1);
            }
        }
    }

    private static final class Glow extends RenderLayer<HeroRenderState, HeroModel> {
        private final FormRenderer r;

        Glow(FormRenderer r) {
            super(r);
            this.r = r;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s, float yRot, float xRot) {
            FormBody b = s.getRenderData(FormBodies.BODY);
            if (b == null || s.isInvisible) return;
            Life l = Life.from(s);
            float windup = l != null && l.windup >= 0 ? l.windup : 0.0F;
            float a = Mth.clamp(0.7F + 0.075F * r.rung + 0.35F * windup, 0.0F, 1.0F);
            if (s.dying >= 0) a *= Math.max(0.0F, 1.0F - s.dying / 0.9F); // the light goes out as it falls
            if (s.hurt >= 0 && s.hurt < 0.25F) a *= 0.55F; // a blow dims it a moment
            if (a <= 0.01F) return;
            int sp = b.species.ordinal();
            boolean closed = shut(s);
            RenderType t = closed && r.glowShut[sp] != null ? r.glowShut[sp] : r.glow[sp];
            if (t != null)
                collector.order(2).submitModel(getParentModel(), s, pose, t, LightCoordsUtil.FULL_BRIGHT,
                        LivingEntityRenderer.getOverlayCoords(s, 0.0F), ARGB.white(a), null, s.outlineColor);
            if (closed || s.distanceToCameraSq > HALO_SQ || r.eyes[0].length == 0) return;
            // the halo: faint by day, strong at night; it swells with a wind-up
            var level = Minecraft.getInstance().level;
            float night = level == null ? 0.5F : Mth.clamp(level.getSkyDarken() / 11.0F, 0.0F, 1.0F);
            float dark = 1.0F - Math.max(LightCoordsUtil.block(s.lightCoords), LightCoordsUtil.sky(s.lightCoords) * (1.0F - night)) / 15.0F;
            float ha = a * (0.22F + 0.6F * dark) * (1.0F + 0.6F * windup);
            b.haloSize = 1.4F + 0.35F * r.rung + 1.2F * windup;
            b.haloColour = ARGB.color(Math.round(255 * Mth.clamp(ha, 0.0F, 1.0F)), RANK[r.rung]);
            for (ModelPart[] eye : r.eyes) {
                if (eye.length == 0) continue;
                pose.pushPose();
                standAt(eye, pose);
                collector.order(3).submitCustomGeometry(pose, r.halo, b);
                pose.popPose();
            }
        }
    }

    static List<String> bodies() {
        return List.of("zombie", "skeleton", "spider", "creeper", "pig", "cow", "sheep", "chicken");
    }
}
