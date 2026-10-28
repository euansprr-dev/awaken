package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.mobs.Ritual;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.model.HeroModel;
import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Renderers for the Ritual of the Bell's cast: the Seated One (its rig drawn x4, about 22 blocks seated) and the twelve
 * statues (x1.2). Each plays the clip its stage asks for, crossfading from the last, turns its head where the entity
 * looks, lights its eyes on an emissive layer, and measures its eyes and palm on the posed model for the story's
 * effects ({@link RitualAnchors}). Both cull by a box much larger than the entity, so they never pop out of view.
 */
final class RitualRenderers {
    private RitualRenderers() {}

    static final String SEATED = "the_seated_one", STATUE = "ritual_statue";
    /** Eye points in the head bone's own space (model pixels), and the palm in the right hand's. */
    static final float[] seatedEyeL = {3.0F, -5.5F, -6.2F}, seatedEyeR = {-3.0F, -5.5F, -6.2F}, seatedPalm = {0, 0, 0};
    static final float[] statueEye = {0, -3.0F, -3.5F};

    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        if (HeroRig.get(SEATED) != null) e.registerEntityRenderer(Ritual.SEATED_ONE.get(), Seated::new);
        else e.registerEntityRenderer(Ritual.SEATED_ONE.get(), NoopRenderer::new);
        if (HeroRig.get(STATUE) != null) e.registerEntityRenderer(Ritual.STATUE.get(), Statue::new);
        else e.registerEntityRenderer(Ritual.STATUE.get(), NoopRenderer::new);
    }

    /** Per entity: the stage it showed last and when it changed, for the crossfade. */
    private static final class Track {
        int stage = -1, prevClip = -1;
        float prevTime;
        double changedAt;
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    private static Track track(int id) {
        if (TRACKS.size() > 64) TRACKS.clear();
        return TRACKS.computeIfAbsent(id, k -> new Track());
    }

    /** Sets the clip for a stage, crossfading (0.35 s) from what was playing when the stage changed. */
    private static void stageClip(RitualModel m, RitualModel.State s, Track t, int stage, int clip, double now, double stageAge) {
        if (stage != t.stage) {
            if (t.stage >= 0) {
                t.prevClip = s.clip;
                t.prevTime = s.time;
            }
            t.stage = stage;
            t.changedAt = now - stageAge;
        }
        float since = (float) (now - t.changedAt);
        s.clip = clip;
        s.time = since;
        s.weight = 1;
        float fade = Mth.clamp(since / 0.35F, 0, 1);
        if (fade < 1 && t.prevClip >= 0 && t.prevClip != clip) {
            s.prevClip = t.prevClip;
            s.prevTime = t.prevTime;
            s.prevWeight = 1 - fade;
            s.weight = fade;
        } else s.prevClip = -1;
    }

    // ---- the Seated One ---------------------------------------------------------------------------------------------

    static final class Seated extends LivingEntityRenderer<SeatedOne, RitualModel.State, RitualModel> {
        private final Identifier texture = Awaken.id("textures/entity/mobs/" + SEATED + ".png");
        private final int dormant, wake, awake, gaze, raise, close, sleep;

        Seated(EntityRendererProvider.Context ctx) {
            super(ctx, new RitualModel(ctx.bakeLayer(HeroModel.layer(SEATED)), HeroRig.get(SEATED)), 0F);
            RitualModel m = getModel();
            dormant = m.clip("dormant");
            wake = m.clip("wake");
            awake = m.clip("awake");
            gaze = m.clip("gaze");
            raise = m.clip("raise");
            close = m.clip("close");
            sleep = m.clip("sleep");
            addLayer(new Glow<>(this, Awaken.id("textures/entity/mobs/" + SEATED + "_glow.png"), false));
            addLayer(new Glow<>(this, Awaken.id("textures/entity/mobs/" + SEATED + "_runes.png"), true));
            addLayer(new Probe<>(this, true));
        }

        @Override
        public RitualModel.State createRenderState() {
            return new RitualModel.State();
        }

        @Override
        public void extractRenderState(SeatedOne e, RitualModel.State s, float pt) {
            super.extractRenderState(e, s, pt);
            s.entityId = e.getId();
            double now = (e.level().getGameTime() + pt) / 20.0;
            double age = e.clientStageSeen == Long.MIN_VALUE ? 30 : (e.level().getGameTime() - e.clientStageSeen + pt) / 20.0;
            int clip = switch (e.stage()) {
                case SeatedOne.WAKING -> wake;
                case SeatedOne.AWAKE -> awake;
                case SeatedOne.GAZE -> gaze;
                case SeatedOne.RAISE -> raise;
                case SeatedOne.CLOSE -> close;
                case SeatedOne.SPENT -> sleep;
                default -> dormant;
            };
            if (clip < 0) clip = dormant;
            stageClip(getModel(), s, track(e.getId()), e.stage(), clip, now, age);
            // the head turns only once it's awake (the look bones follow the entity's head yaw)
            s.look = e.stage() == SeatedOne.DORMANT || e.stage() == SeatedOne.SPENT ? 0 : 1;
            s.glow = RitualAnchors.eyeLight(e, pt);
            s.glowRgb = 0xFFFFFF;
            // the runes swell beat by beat (the director's value); awake, they breathe with it
            float runes = Mth.lerp(pt, e.clientRunesO, e.clientRunes);
            int stage = e.stage();
            if (stage == SeatedOne.AWAKE || stage == SeatedOne.GAZE || stage == SeatedOne.RAISE)
                runes *= 0.88F + 0.12F * Mth.sin((e.tickCount + pt) * 0.13F);
            s.runes = runes;
        }

        @Override
        public Identifier getTextureLocation(RitualModel.State s) {
            return texture;
        }

        @Override
        protected void scale(RitualModel.State s, PoseStack pose) {
            pose.scale(4F, 4F, 4F);
        }

        @Override
        protected AABB getBoundingBoxForCulling(SeatedOne e, float pt) {
            return e.getBoundingBox().inflate(16, 6, 16);
        }

        @Override
        protected boolean shouldShowName(SeatedOne e, double d) {
            return false;
        }

        /**
         * One light for the whole colossus: the director's key light (the candle line and the altar's fire on it, synced
         * and eased), never darker than the dais candles before it, lifted while its eyes are open. Pale marble against
         * the black hall; when every flame dies only its runes and its halo are left.
         */
        @Override
        protected int getBlockLightLevel(SeatedOne e, net.minecraft.core.BlockPos pos) {
            var level = e.level();
            int best = Math.max(super.getBlockLightLevel(e, pos), Math.round(e.clientKey));
            for (int dz = 3; dz <= 6; dz++) {
                net.minecraft.core.BlockPos p = pos.relative(e.getDirection(), dz).above();
                best = Math.max(best, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p));
            }
            int eyes = (int) (RitualAnchors.eyeLight(e, 0) * 3);
            return Math.min(15, best + eyes);
        }
    }

    // ---- the statues ------------------------------------------------------------------------------------------------

    static final class Statue extends LivingEntityRenderer<RitualStatue, RitualModel.State, RitualModel> {
        private final Identifier texture = Awaken.id("textures/entity/mobs/" + STATUE + ".png");
        private final int kneel, rise, stand, walk, windup, strike, recover;

        Statue(EntityRendererProvider.Context ctx) {
            super(ctx, new RitualModel(ctx.bakeLayer(HeroModel.layer(STATUE)), HeroRig.get(STATUE)), 0.7F);
            RitualModel m = getModel();
            kneel = m.clip("kneel");
            rise = m.clip("rise");
            stand = m.clip("stand");
            walk = m.clip("walk");
            windup = m.clip("windup");
            strike = m.clip("strike");
            recover = m.clip("recover");
            addLayer(new Glow<>(this, Awaken.id("textures/entity/mobs/" + STATUE + "_glow.png"), false));
            addLayer(new Probe<>(this, false));
        }

        @Override
        public RitualModel.State createRenderState() {
            return new RitualModel.State();
        }

        @Override
        public void extractRenderState(RitualStatue e, RitualModel.State s, float pt) {
            super.extractRenderState(e, s, pt);
            s.entityId = e.getId();
            double now = (e.level().getGameTime() + pt) / 20.0;
            double age = e.stageAge(pt) / 20.0;
            int stage = e.stage();
            int clip = switch (stage) {
                case RitualStatue.RISE -> rise;
                case RitualStatue.STAND -> stand;
                case RitualStatue.WALK -> walk;
                case RitualStatue.WINDUP -> windup;
                case RitualStatue.STRIKE -> age > 0.6 && recover >= 0 ? recover : strike;
                case RitualStatue.GLARE -> kneel;
                default -> kneel;
            };
            if (clip < 0) clip = kneel;
            Track t = track(e.getId());
            stageClip(getModel(), s, t, stage == RitualStatue.STRIKE && age > 0.6 ? 100 : stage, clip, now, stage == RitualStatue.STRIKE && age > 0.6 ? age - 0.6 : age);
            if (stage == RitualStatue.WALK) s.time = dev.awaken.mobs.Ritual.walkSeconds(e, pt); // locked to distance, never sliding
            s.look = stage == RitualStatue.KNEEL || stage == RitualStatue.WINDUP || stage == RitualStatue.STRIKE ? 0.15F : 1;
            boolean angry = stage == RitualStatue.GLARE || stage == RitualStatue.WINDUP || stage == RitualStatue.STRIKE;
            s.glow = stage == RitualStatue.KNEEL ? 0.18F : angry ? 1.0F : 0.6F;
            s.glowRgb = angry ? 0xFF3A2A : 0xFFD58A;
        }

        @Override
        public Identifier getTextureLocation(RitualModel.State s) {
            return texture;
        }

        @Override
        protected void scale(RitualModel.State s, PoseStack pose) {
            pose.scale(1.2F, 1.2F, 1.2F);
        }

        @Override
        protected AABB getBoundingBoxForCulling(RitualStatue e, float pt) {
            return e.getBoundingBox().inflate(3, 2, 3);
        }

        @Override
        protected boolean shouldShowName(RitualStatue e, double d) {
            return false;
        }
    }

    // ---- layers -----------------------------------------------------------------------------------------------------

    /** An emissive layer: the eyes (as bright and as coloured as the state says), or the Seated One's runes. */
    private static final class Glow<E extends net.minecraft.world.entity.LivingEntity> extends RenderLayer<RitualModel.State, RitualModel> {
        private final RenderType type;
        private final boolean runes;

        Glow(LivingEntityRenderer<E, RitualModel.State, RitualModel> parent, Identifier texture, boolean runes) {
            super(parent);
            this.type = RenderTypes.entityTranslucentEmissive(texture);
            this.runes = runes;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, RitualModel.State s, float yRot, float xRot) {
            float a = Mth.clamp(runes ? s.runes : s.glow, 0, 1);
            if (a <= 0.01F || s.isInvisible) return;
            int colour = ARGB.color((int) (a * 255), runes ? 0xFFFFFF : s.glowRgb);
            collector.order(2).submitModel(getParentModel(), s, pose, type, LightCoordsUtil.FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(s, 0.0F), colour, null, s.outlineColor);
        }
    }

    /** Measures the posed eyes and palm (camera-relative here, made world positions for the effects). */
    private static final class Probe<E extends net.minecraft.world.entity.LivingEntity> extends RenderLayer<RitualModel.State, RitualModel> {
        private final boolean seated;
        private final Vector3f v = new Vector3f();

        Probe(LivingEntityRenderer<E, RitualModel.State, RitualModel> parent, boolean seated) {
            super(parent);
            this.seated = seated;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, RitualModel.State s, float yRot, float xRot) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
            RitualAnchors.Seen seen = RitualAnchors.of(s.entityId);
            RitualModel m = getParentModel();
            if (seated) {
                if (m.measure(pose, "head", seatedEyeL[0], seatedEyeL[1], seatedEyeL[2], v)) seen.left = new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
                if (m.measure(pose, "head", seatedEyeR[0], seatedEyeR[1], seatedEyeR[2], v)) seen.right = new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
                if (m.measure(pose, "hand_r", seatedPalm[0], seatedPalm[1], seatedPalm[2], v)) seen.palm = new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
            } else if (m.measure(pose, "head", statueEye[0], statueEye[1], statueEye[2], v)) {
                seen.left = new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
            }
            seen.seenAt = net.minecraft.util.Util.getMillis();
            RitualAnchors.forgetOld();
        }
    }
}
