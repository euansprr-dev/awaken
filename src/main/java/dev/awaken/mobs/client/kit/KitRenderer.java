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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Draws a kit body (SPEC §35.4, §35.10) at true size (the entity's scale is undone: its pixels are the world's). On the
 * hero model's graph (idle, distance-locked walk and run, the look, the life layer) it lays the synced move's one clip,
 * its wind-up and recovery stretched to the ticks the server plays (a held wind-up stays at its peak, trembling), steps
 * round when it turns more than 45°, reels the way a blow came, kneels while its stance is broken and rises after, and
 * plants its feet on the ground under them, through its moves too. Every change of what it plays is eased by its
 * {@link KitBlend} (a string's next blow starts where the last one ended); its blows shake the view near them and
 * pause for a moment where they land.
 */
public final class KitRenderer extends MobRenderer<AwakenMob, HeroRenderState, HeroModel> {
    private static final float FADE = 0.15F, TURN_STEP = 45F, TURN_TIME = 0.4F;

    private final Kits.Kit kit;
    private final Identifier texture;
    private final @Nullable Identifier blink;
    /** Per move: its clip slot. */
    private final int[] clip;
    /** Each body's motion state between frames (weak: it goes with the entity). */
    private final java.util.Map<AwakenMob, KitBlend> blends = new java.util.WeakHashMap<>();
    private final int roar, kneel, rise, hurtL, hurtR, hurtF, turnL, turnR, death;
    private final boolean introKneels;
    /** Where the feet stand at rest, in blocks of body space (x left, z forward), for the ground under them. */
    private final float footLX, footLZ, footRX, footRZ;
    private final boolean hasFeet;
    /** How far (blocks) the body can reach out of its box: the culling box grows by it (a serpent's lunge, SPEC §35.11). */
    private final float reach;

    public KitRenderer(EntityRendererProvider.Context ctx, MobDef def, Kits.Kit kit) {
        super(ctx, new HeroModel(ctx.bakeLayer(HeroModel.layer(kit.id())), kit.rig()), kit.rig().shadow);
        this.kit = kit;
        this.texture = Awaken.id("textures/entity/kit/" + def.id + ".png");
        Identifier b = Awaken.id("textures/entity/kit/" + def.id + "_blink.png");
        this.blink = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(b).isPresent() ? b : null;
        HeroModel m = getModel();
        int n = kit.moves().moves().size();
        clip = new int[n];
        for (int i = 0; i < n; i++) clip[i] = m.clip(kit.moves().get(i).clip());
        roar = m.clip("move_roar") >= 0 ? m.clip("move_roar") : m.clip("roar");
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
        reach = reach(rig);
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

    /** The longest chain of pivots laid end to end, in blocks, and a block for what hangs off its end. */
    private static float reach(HeroRig rig) {
        float most = 0;
        for (int i = 0; i < rig.bones.size(); i++) {
            float len = 0;
            for (int j = i; j >= 0; j = rig.bones.get(j).parent()) {
                HeroRig.Bone b = rig.bones.get(j);
                len += Mth.sqrt(b.px() * b.px() + b.py() * b.py() + b.pz() * b.pz());
            }
            most = Math.max(most, len);
        }
        return most / 16F + 1.0F;
    }

    @Override
    protected AABB getBoundingBoxForCulling(AwakenMob mob, float partialTicks) {
        return super.getBoundingBoxForCulling(mob, partialTicks).inflate(reach);
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
        KitBlend blend = blends.computeIfAbsent(mob, k -> new KitBlend());
        int start = boss.kitStart();
        int mv = boss.kitMove();
        // the body's clock: game time, stopped while a hit pause holds (per move, so the next move starts true)
        float real = mob.level().getGameTime() + partialTicks;
        if (blend.pausedFor != start) {
            blend.pausedFor = start;
            blend.paused = 0;
        }
        if (!Float.isNaN(blend.lastReal) && System.nanoTime() < blend.pauseUntil) blend.paused += Math.max(0, real - blend.lastReal);
        blend.lastReal = real;
        float now = real - blend.paused;
        float speed = boss.kitSpeed();

        // the move: one clip, its stage from the synced start, so everyone sees the same frame
        s.cueClip = -1;
        s.cueWeight = 0;
        float winding = -1;
        if (mv >= 0 && mv < clip.length) {
            winding = stage(s, mv, boss.kitHold(), (now - start) / 20F, speed);
            if (KitConfig.hitFeel()) feel(boss, mv, start, now, blend);
        }
        s.prevClip = -1;
        s.prevWeight = 0;

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
        // (the turn rate is eased on the boss, so the step fades in from 1° a tick and out again, never popping off)
        float turn = boss.kitTurn(partialTicks);
        if (Math.abs(turn) > 1 && mv < 0 && (turnL >= 0 || turnR >= 0) && !s.airborne) {
            float t = (mob.tickCount % (int) (TURN_TIME * 20)) / 20F + partialTicks / 20F;
            int clip = turn > 0 ? turnL : turnR;
            if (clip >= 0) layer(s, 2, clip, t * m.length(clip) / TURN_TIME, Mth.clamp((Math.abs(turn) - 1) / (TURN_STEP / (TURN_TIME * 20) - 1), 0, 1));
        }

        s.melee = -1;
        s.dying = mob.isDeadOrDying() ? (mob.deathTime + partialTicks) / 20.0F : -1;
        if (s.dying >= 0) s.hasRedOverlay = mob.deathTime < 8 || mob.hurtTime > 0;
        if (hasFeet && !s.airborne && s.dying < 0) ground(mob, s, partialTicks);
        live(mob, s, l, winding);
        // what it plays, as one number: a new move (or the same one again), the kneel or rise, a reaction, the fall
        int key = mv >= 0 ? (mv + 1) * 7919 + start : -1;
        for (int k = 0; k < 2; k++) key = key * 31 + (s.layerWeight[k] > 0 ? s.layerClip[k] + 2 : 0);
        if (mob.hurtTime > 0) key = key * 31 + (mob.tickCount - (mob.hurtDuration - mob.hurtTime));
        if (s.dying >= 0) key = key * 31 + 1;
        // a string's next blow (a new move straight after the last): its wind-up starts from where the last one ended
        blend.nextLink = 0;
        if (mv >= 0 && mv < clip.length && blend.lastMove >= 0 && start != blend.lastStart) {
            KitMoves.Played p = KitMoves.played(kit.moves().get(mv), speed, boss.kitHold());
            blend.nextLink = Math.max(0.1F, (p.windup() + p.hold()) / 20F);
        }
        blend.lastMove = mv;
        blend.lastStart = start;
        s.kitKey = key;
        s.kitClock = now / 20F;
        s.kitBlend = blend;
    }

    /**
     * The view shakes when a heavy or ground-striking blow lands near you, and the body's clock stops for a moment when
     * a blow lands either way (its blow on you, yours on it). Client only, behind {@code KitConfig.hitFeel}.
     */
    private void feel(AwakenBoss boss, int mv, int start, float now, KitBlend blend) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        KitMoves.Move m = kit.moves().get(mv);
        KitMoves.Played p = KitMoves.played(m, boss.kitSpeed(), boss.kitHold());
        float since = now - start;
        boolean heavy = m.kind() == dev.awaken.core.api.Strikes.Kind.HEAVY || m.kind() == dev.awaken.core.api.Strikes.Kind.PERILOUS;
        if ((heavy || m.is("area")) && !m.hits().isEmpty() && blend.shookFor != start
                && since >= p.at(m.time(), m.mark("contact", m.time().windup())) && since < p.windup() + p.hold() + p.active() + 2) {
            blend.shookFor = start;
            double d = Math.sqrt(mc.player.distanceToSqr(boss));
            if (d < 24 && dev.awaken.core.AwakenConfig.Client.CAMERA_SHAKE.get()) {
                float k = (float) (1 - d / 24) * (m.is("area") ? 1.0F : 0.6F) * (m.kind() == dev.awaken.core.api.Strikes.Kind.PERILOUS ? 1.4F : 1.0F);
                float side = (start & 1) == 0 ? 1 : -1;
                CombatFeedback.kick(0.5F * k * side, -2.4F * k, 1.4F * k * side, 7);
            }
        }
        // its blow on you: you were hurt just now, inside its strike, within its reach
        var lp = mc.player;
        if (lp.hurtTime > 0) {
            int at = lp.tickCount - (lp.hurtDuration - lp.hurtTime);
            if (at != blend.playerHurtSeen) {
                blend.playerHurtSeen = at;
                boolean striking = since >= p.windup() + p.hold() && since <= p.windup() + p.hold() + p.active() + 1;
                if (striking && !m.hits().isEmpty() && Math.sqrt(lp.distanceToSqr(boss)) < m.maxRange() + boss.getBbWidth() + 2) blend.pause(start);
            }
        }
        // yours on it
        if (boss.hurtTime > 0) {
            int at = boss.tickCount - (boss.hurtDuration - boss.hurtTime);
            if (at != blend.hurtSeen) {
                blend.hurtSeen = at;
                blend.pause(start);
            }
        }
    }

    /**
     * Sets cue clip, time and weight for move {@code mv} {@code since} seconds after it began: the played tick mapped
     * back to the clip's base timing (a hold stays at the peak). Returns how far through its wind-up it is (0..1), or -1.
     */
    private float stage(HeroRenderState s, int mv, int hold, float since, float speed) {
        KitMoves.Move m = kit.moves().get(mv);
        KitMoves.Played p = KitMoves.played(m, speed, hold);
        float tick = since * 20F;
        s.cueClip = clip[mv];
        s.cueWeight = 1;
        float base = p.base(m.time(), Math.min(tick, p.total()));
        s.cueTime = Math.min(m.length(), base) / 20F;
        if (tick > p.total()) s.cueWeight = Math.max(0, 1 - (tick - p.total()) / 20F / FADE);
        return base < m.time().windup() ? base / Math.max(1, m.time().windup()) : -1;
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
    private void live(AwakenMob mob, HeroRenderState s, AwLook l, float winding) {
        HeroModel m = getModel();
        Life life = Life.of(l.life, m.life(), mob.getId());
        l.life = life;
        life.hurt = l.hurt;
        life.entity = mob.getId();
        life.windup = winding >= 0 && s.cueWeight > 0 ? Mth.clamp(winding, 0, 1) : -1;
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
        return look(s).kitTint;  // painted in its own colours: the theme's tint would wash them (SPEC §35.4)
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
