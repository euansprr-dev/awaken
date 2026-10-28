package dev.awaken.mobs.client.kit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.client.anim.HeroRenderState;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.client.model.HeroModel;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Draws a kit body (SPEC §35.4) at true size (the entity's scale is undone: its pixels are the world's). On the hero
 * model's graph (idle, distance-locked walk and run, the look, the life layer) it lays the synced move's wind-up, active
 * and recovery clips, stretched to the ticks the server plays (a held wind-up stays at its peak, trembling), fades the
 * move before out, steps round when it turns more than 45°, reels the way a blow came, kneels while its stance is
 * broken and rises after, and plants its feet on the ground under them.
 */
public final class KitRenderer extends MobRenderer<AwakenMob, HeroRenderState, HeroModel> {
    private static final float FADE = 0.15F, TURN_STEP = 45F, TURN_TIME = 0.4F;

    private final Kits.Kit kit;
    private final Identifier texture;
    private final @Nullable Identifier blink;
    /** Per move: its wind-up, active and recovery clip slots. */
    private final int[] windup, active, recover;
    private final int roar, kneel, rise, hurtL, hurtR, hurtF, turnL, turnR, death;
    private final boolean introKneels;
    /** Where the feet stand at rest, in blocks of body space (x left, z forward), for the ground under them. */
    private final float footLX, footLZ, footRX, footRZ;
    private final boolean hasFeet;

    public KitRenderer(EntityRendererProvider.Context ctx, MobDef def, Kits.Kit kit) {
        super(ctx, new HeroModel(ctx.bakeLayer(HeroModel.layer(kit.id())), kit.rig()), kit.rig().shadow);
        this.kit = kit;
        this.texture = Awaken.id("textures/entity/kit/" + def.id + ".png");
        Identifier b = Awaken.id("textures/entity/kit/" + def.id + "_blink.png");
        this.blink = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(b).isPresent() ? b : null;
        HeroModel m = getModel();
        int n = kit.moves().moves().size();
        windup = new int[n];
        active = new int[n];
        recover = new int[n];
        for (int i = 0; i < n; i++) {
            String id = kit.moves().get(i).id();
            windup[i] = m.clip(id + "_windup");
            active[i] = m.clip(id + "_active");
            recover[i] = m.clip(id + "_recover");
        }
        roar = m.clip("roar");
        kneel = m.clip("kneel");
        rise = m.clip("rise");
        hurtL = m.clip("hurt_l");
        hurtR = m.clip("hurt_r");
        hurtF = m.clip("hurt");
        turnL = m.clip("turn_l");
        turnR = m.clip("turn_r");
        death = m.clip("death");
        introKneels = kit.fight() != null && "kneel".equals(kit.fight().intro().pose());
        HeroRig rig = kit.rig();
        float[] l = foot(rig, "l"), r = foot(rig, "r");
        hasFeet = l != null && r != null;
        footLX = l == null ? 0 : l[0];
        footLZ = l == null ? 0 : l[1];
        footRX = r == null ? 0 : r[0];
        footRZ = r == null ? 0 : r[1];
        addLayer(new Glow(this, Awaken.id("textures/entity/kit/" + def.id + "_glow.png")));
    }

    /** The rest position of a foot bone in body space (blocks: x left, z forward), summing its pivots; null if none. */
    private static float @Nullable [] foot(HeroRig rig, String side) {
        int i = rig.bone("foot_" + side);
        if (i < 0) return null;
        float x = 0, z = 0;
        while (i >= 0) {
            HeroRig.Bone b = rig.bones.get(i);
            x += b.px();
            z += b.pz();
            i = b.parent();
        }
        return new float[]{x / 16F, -z / 16F};
    }

    @Override
    public HeroRenderState createRenderState() {
        return new HeroRenderState();
    }

    @Override
    public void extractRenderState(AwakenMob mob, HeroRenderState s, float partialTicks) {
        super.extractRenderState(mob, s, partialTicks);
        s.scale = 1.0F; // modelled at true size (SPEC §35, decision 2): the entity's scale is its box, not its body
        AwLook l = mob.look(partialTicks);
        s.setRenderData(AwModels.LOOK, l);
        for (int k = 0; k < s.layerClip.length; k++) {
            s.layerClip[k] = -1;
            s.layerWeight[k] = 0;
        }
        s.footL = s.footR = 0;
        s.kitDrop = 0;
        if (!(mob instanceof AwakenBoss boss)) return;
        HeroRig rig = kit.rig();
        HeroModel m = getModel();
        s.gait = boss.kitGait(partialTicks);
        double dx = mob.getX() - mob.xo, dz = mob.getZ() - mob.zo;
        s.speed = (float) Math.sqrt(dx * dx + dz * dz);
        s.walk = Mth.clamp(s.walkAnimationSpeed * 1.6F, 0.0F, 1.0F);
        s.run = Mth.clamp((s.speed - rig.runFrom) / Math.max(0.01F, rig.runFull - rig.runFrom), 0.0F, 1.0F);
        s.airborne = mob.def().flies();
        s.angry = mob.getTarget() != null || l.telegraph > 0 ? 1.0F : 0.0F;
        float now = mob.level().getGameTime() + partialTicks;
        float speed = boss.kitSpeed();

        // the move: its stage from the synced start, so everyone sees the same frame
        s.cueClip = -1;
        s.cueWeight = 0;
        int mv = boss.kitMove();
        if (mv >= 0 && mv < windup.length) {
            stage(s, mv, boss.kitHold(), (now - boss.kitStart()) / 20F, speed, false);
            float in = (now - boss.kitStart()) / 20F;
            if (in < FADE) s.cueWeight *= Mth.smoothstep(in / FADE);
        }
        // the move before fades out from where it was (a string's next step crossfades; a cut-short move lets go)
        s.prevClip = -1;
        s.prevWeight = 0;
        int pm = boss.kitPrevMove();
        float sinceEnd = (now - boss.kitPrevEnd()) / 20F;
        if (pm >= 0 && pm < windup.length && sinceEnd < FADE) {
            int clip = s.cueClip;
            float time = s.cueTime, weight = s.cueWeight;
            stage(s, pm, boss.kitPrevHold(), (boss.kitPrevEnd() - boss.kitPrevStart()) / 20F, speed, true);
            s.prevClip = s.cueClip;
            s.prevTime = s.cueTime;
            s.prevWeight = s.cueWeight * (1 - Mth.smoothstep(sinceEnd / FADE));
            s.cueClip = clip;
            s.cueTime = time;
            s.cueWeight = weight;
        }

        // the kneel (a broken stance, or waiting for the fight to start) and the rise after it
        int pose = boss.kitPose();
        float poseFor = (now - boss.kitPoseAt()) / 20F;
        boolean kneeling = pose == AwakenBoss.KIT_POSE_BROKEN || (pose == AwakenBoss.KIT_POSE_INTRO && introKneels);
        if (kneeling && kneel >= 0) {
            layer(s, 0, kneel, pose == AwakenBoss.KIT_POSE_INTRO && poseFor > 1 ? m.length(kneel) : Math.min(poseFor, m.length(kneel)), 1);
        } else if (rise >= 0 && (boss.kitPoseFrom() == AwakenBoss.KIT_POSE_BROKEN || (boss.kitPoseFrom() == AwakenBoss.KIT_POSE_INTRO && introKneels))
                && poseFor < m.length(rise) + 0.2F) {
            layer(s, 0, rise, Math.min(poseFor, m.length(rise)), poseFor <= m.length(rise) ? 1 : 1 - (poseFor - m.length(rise)) / 0.2F);
        }

        // a blow's way picks the reaction: struck from its left it twists right, and the other way
        s.hurt = -1;
        if (l.hurt > 0) {
            float t = (1.0F - l.hurt) * mob.hurtDuration / 20.0F;
            float[] dir = new float[2];
            float k = CombatFeedback.blowOn(mob.getId(), dir);
            int clip = hurtF;
            if (k > 0 && (hurtL >= 0 || hurtR >= 0)) {
                // the blow's way in the body's frame: its x is the body's left
                float yaw = s.bodyRot * Mth.DEG_TO_RAD;
                float side = dir[0] * Mth.cos(yaw) + dir[1] * Mth.sin(yaw);
                if (side > 0.35F && hurtR >= 0) clip = hurtR; // pushed toward its left: struck from its right
                else if (side < -0.35F && hurtL >= 0) clip = hurtL;
            }
            if (clip == hurtF) s.hurt = t;
            else layer(s, 1, clip, t, Math.min(1, 0.6F + 0.4F * Math.max(1, k)));
        }

        // a turn step: when the body has to come round more than 45° it steps with its feet
        float turn = Mth.wrapDegrees(mob.yBodyRot - mob.yBodyRotO);
        if (Math.abs(turn) > 3 && mv < 0 && (turnL >= 0 || turnR >= 0) && !s.airborne) {
            float t = (mob.tickCount % (int) (TURN_TIME * 20)) / 20F + partialTicks / 20F;
            int clip = turn > 0 ? turnL : turnR;
            if (clip >= 0) layer(s, 2, clip, t * m.length(clip) / TURN_TIME, Mth.clamp(Math.abs(turn) / (TURN_STEP / (TURN_TIME * 20)), 0, 1));
        }

        s.melee = -1;
        s.dying = mob.isDeadOrDying() ? (mob.deathTime + partialTicks) / 20.0F : -1;
        if (s.dying >= 0) s.hasRedOverlay = mob.deathTime < 8 || mob.hurtTime > 0;
        if (hasFeet && !s.airborne && s.dying < 0 && mv < 0) ground(mob, s, partialTicks);
        live(mob, s, l, mv);
    }

    /** Sets cue clip, time and weight for move {@code mv} {@code since} seconds after it began. */
    private void stage(HeroRenderState s, int mv, int hold, float since, float speed, boolean ending) {
        HeroModel m = getModel();
        KitMoves.Played p = KitMoves.played(kit.moves().get(mv), speed, hold);
        float w = p.windup() / 20F, h = p.hold() / 20F, a = p.active() / 20F, r = p.recover() / 20F;
        s.cueWeight = 1;
        if (since < w + h) {
            s.cueClip = windup[mv];
            s.cueTime = Math.min(m.length(s.cueClip), since / Math.max(0.05F, w) * m.length(s.cueClip));
        } else if (since < w + h + a) {
            s.cueClip = active[mv];
            s.cueTime = (since - w - h) / Math.max(0.05F, a) * m.length(s.cueClip);
        } else {
            s.cueClip = recover[mv];
            float into = since - w - h - a;
            s.cueTime = Math.min(m.length(s.cueClip), into / Math.max(0.05F, r) * m.length(s.cueClip));
            if (!ending && into > r) s.cueWeight = Math.max(0, 1 - (into - r) / FADE);
        }
    }

    private static void layer(HeroRenderState s, int k, int clip, float time, float weight) {
        s.layerClip[k] = clip;
        s.layerTime[k] = Math.max(0, time);
        s.layerWeight[k] = Mth.clamp(weight, 0, 1);
    }

    /** The ground under each foot: the body lowers to the lower one (at most 0.6 block) and the higher foot reaches up. */
    private void ground(AwakenMob mob, HeroRenderState s, float pt) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mob.distanceToSqr(mc.player) > 32 * 32 || !mob.onGround()) return;
        Level level = mob.level();
        double x = Mth.lerp(pt, mob.xo, mob.getX()), y = Mth.lerp(pt, mob.yo, mob.getY()), z = Mth.lerp(pt, mob.zo, mob.getZ());
        float yaw = s.bodyRot * Mth.DEG_TO_RAD, c = Mth.cos(yaw), sn = Mth.sin(yaw);
        double gl = top(level, x + footLX * c - footLZ * sn, y, z + footLX * sn + footLZ * c) - y;
        double gr = top(level, x + footRX * c - footRZ * sn, y, z + footRX * sn + footRZ * c) - y;
        float drop = (float) Math.max(-0.6, Math.min(0, Math.min(gl, gr)));
        s.footL = (float) Math.max(0, Math.min(0.6, gl - drop)) * 16;
        s.footR = (float) Math.max(0, Math.min(0.6, gr - drop)) * 16;
        s.kitDrop = drop;
    }

    /** The height of the ground's top within half a block of {@code y} at (x, z) (a slab's half, a carpet's sliver). */
    private static double top(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y + 0.5), Mth.floor(z));
        for (int k = 0; k < 3; k++) {
            VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) {
                double t = p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
                if (t <= y + 0.6) return t;
            }
            p.move(0, -1, 0);
        }
        return y - 0.6;
    }

    /** The life layer, fed this frame's move. */
    private void live(AwakenMob mob, HeroRenderState s, AwLook l, int mv) {
        HeroModel m = getModel();
        Life life = Life.of(l.life, m.life(), mob.getId());
        l.life = life;
        life.hurt = l.hurt;
        life.entity = mob.getId();
        boolean winding = mv >= 0 && mv < windup.length && s.cueClip == windup[mv] && s.cueWeight > 0;
        life.windup = winding ? Mth.clamp(s.cueTime / Math.max(0.01F, m.length(s.cueClip)), 0, 1) : -1;
        life.roar = s.cueWeight > 0 && s.cueClip >= 0 && s.cueClip == roar;
        life.roarClip = roar >= 0;
        life.busy = s.cueWeight > 0.05F || s.hurt >= 0 || s.layerWeight[0] > 0.05F;
        life.dying = s.dying;
        life.deathLength = m.length(death);
        life.boss = true;
        HeroRig rig = m.rig();
        life.stride = rig.walkStride + (rig.runStride - rig.walkStride) * s.run;
        life.walk = s.airborne ? 0.0F : s.walk;
        life.airborne = s.airborne;
        life.tick(s, true);
    }

    @Override
    public Identifier getTextureLocation(HeroRenderState s) {
        Life l = Life.from(s);
        return blink != null && l != null && l.closed() > 0.5F ? blink : texture;
    }

    @Override
    protected int getModelTint(HeroRenderState s) {
        return look(s).tint;
    }

    @Override
    protected float getWhiteOverlayProgress(HeroRenderState s) {
        return 0.0F;
    }

    /** It falls by its own death clip (no vanilla flip); the ground's dip lowers the whole body. */
    @Override
    protected void setupRotations(HeroRenderState s, PoseStack pose, float bodyRot, float entityScale) {
        if (s.kitDrop < 0) pose.translate(0, s.kitDrop, 0);
        pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
    }

    private static AwLook look(HeroRenderState s) {
        AwLook l = s.getRenderData(AwModels.LOOK);
        return l != null ? l : DEFAULT;
    }

    private static final AwLook DEFAULT = new AwLook();

    /** The light story (SPEC §35, decision 3): core, eyes and cracks, brighter as it winds up, dimming as it dies. */
    private static final class Glow extends RenderLayer<HeroRenderState, HeroModel> {
        private final RenderType type;

        Glow(KitRenderer parent, Identifier texture) {
            super(parent);
            this.type = RenderTypes.entityTranslucentEmissive(texture);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, HeroRenderState s, float yRot, float xRot) {
            if (s.isInvisible) return;
            AwLook l = look(s);
            float a = Mth.clamp(0.55F + 0.45F * l.glow, 0.0F, 1.0F);
            if (s.dying >= 0) a *= Math.max(0.0F, 1.0F - s.dying / 3.0F);
            if (a <= 0.01F) return;
            collector.order(2).submitModel(getParentModel(), s, pose, type, LightCoordsUtil.FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(s, 0.0F), ARGB.white(a), null, s.outlineColor);
        }
    }
}
