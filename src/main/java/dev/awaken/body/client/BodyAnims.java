package dev.awaken.body.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.body.BodyClips;
import dev.awaken.body.BodyRules;
import dev.awaken.body.Move;
import dev.awaken.core.api.PlayerClips;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;

/**
 * How a body in motion looks from outside (third person, and every other player): no mixins, only the render state
 * and the pose stack. The slide is feet first, sitting low with the lead leg out and a hand trailing; the roll is a
 * tucked shoulder roll over the landing; the mantle reaches up both arms, then leans over the lip; the tactical
 * sprint leans in with a longer stride. Fixed slots, nothing allocated per frame.
 * <p>
 * With the Player Animation Library installed (optional), each move plays a full-body clip instead
 * ({@link BodyClips}, through {@link PlayerClips}; bent elbows and knees with Bendable Cuboids): the slide held for as
 * long as it runs, then settling or a tucked lift out of a slide-cancel; the roll a full tuck turning through; the
 * mantle a reach, pull and press; the tactical sprint a looped stride. The vanilla poses below then step aside for that
 * player (only the slide's facing and its crawl-to-sit fix stay on the render state). Other players' moves play from
 * the same signals as before: the server's Seen message to start, their pose and sprint flag to end.
 */
final class BodyAnims {
    private BodyAnims() {}

    private static final class Anim {
        int entity = -1;
        int move;
        float start;
        float yaw;
        /** A library clip is drawing this one; the vanilla poses leave it alone. */
        boolean clip;
        /** A held clip's move just ended: next tick picks how it lets go (a slide's jump-out or its settle). */
        boolean ending;
        /** A slide down a run tips the whole figure to the slope (degrees, eased; last tick's and this tick's). */
        float tilt, tiltO;
        /** A crossing's kind (VaultRules.VAULT .. OVER). */
        int kind;
    }

    private static final Anim[] SLOTS = new Anim[32];
    private static final Quaternionf Q = new Quaternionf();
    private static int pushed;

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Anim();
    }

    static void play(int entityId, Move move, float yaw) {
        // first person: our own move's counterpart (Hands, BodyFp)
        if (isLocal(entityId)) {
            String c = BodyClips.of(move);
            if (c != null) BodyFp.clip(c);
        }
        Anim a = find(entityId);
        if (a == null) a = free();
        a.entity = entityId;
        a.move = move.ordinal();
        a.start = ClientMoves.ticks;
        a.yaw = yaw;
        a.ending = false;
        a.tilt = a.tiltO = 0;
        String clip = BodyClips.of(move);
        a.clip = clip != null && PlayerClips.available() && PlayerClips.play(entityId, clip, move == Move.TACTICAL_SPRINT ? 3 : move == Move.SLIDE ? 2 : 1);
    }

    /**
     * A crossing (SPEC §17.27): its kind's clip, from the prediction (ours) or the server's Seen (others'). The body is
     * in the crawl box while it goes over: the render state still draws a standing figure (the clip, or the fallback's
     * tilt), never a crawl.
     */
    static void vault(int entityId, int kind, float yaw) {
        if (isLocal(entityId)) BodyFp.vault(kind);
        // a Runner clip on the same entity steps aside (a climb over from a hang)
        for (int i = 0; i < RUN_ENTITY.length; i++) {
            if (RUN_ENTITY[i] != entityId) continue;
            RUN_ENTITY[i] = -1;
            RUN_CLIP[i] = null;
        }
        Anim a = find(entityId);
        if (a == null) a = free();
        a.entity = entityId;
        a.move = Move.VAULT.ordinal();
        a.kind = kind;
        a.start = ClientMoves.ticks;
        a.yaw = yaw;
        a.ending = false;
        a.tilt = a.tiltO = 0;
        a.clip = PlayerClips.available() && PlayerClips.play(entityId, BodyClips.VAULTS.get(Mth.clamp(kind, 0, BodyClips.VAULTS.size() - 1)), 1);
    }

    static void stop(int entityId, Move move) {
        Anim a = find(entityId);
        if (a == null || a.move != move.ordinal()) return;
        if (a.clip && BodyClips.held(move)) a.ending = true; // let go next tick, once a jump-out has left the ground
        else a.entity = -1;
    }

    /** A held clip lets go: a slide settles up to standing or, already in the air, lifts out in a tuck. */
    private static void letGo(Anim a, @org.jspecify.annotations.Nullable Entity e) {
        String clip = a.move == Move.SLIDE.ordinal() ? e != null && !e.onGround() ? BodyClips.SLIDE_JUMP : BodyClips.SLIDE_END : BodyClips.SPRINT_END;
        if (!PlayerClips.play(a.entity, clip, 2)) PlayerClips.stop(a.entity);
        a.entity = -1;
    }

    static void tick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        for (Anim a : SLOTS) {
            if (a.entity < 0) continue;
            float age = ClientMoves.ticks - a.start;
            if (a.clip && BodyClips.held(Move.byId(a.move))) {
                Entity e = mc.level == null ? null : mc.level.getEntity(a.entity);
                boolean remote = mc.player == null || a.entity != mc.player.getId();
                // other players' held moves end as their crawl pose or their sprint does (ours end through stop())
                // (after a few ticks: the Seen message can arrive before their pose or sprint flag has synced)
                boolean over = a.ending || e == null || age > length(a.move) + 2
                        || remote && age >= 3 && (a.move == Move.SLIDE.ordinal() ? !e.hasPose(Pose.SWIMMING) : !e.isSprinting());
                if (over) {
                    letGo(a, e);
                    continue;
                }
            } else if (age > length(a.move) + 2) {
                a.entity = -1;
                continue;
            } else if (a.move == Move.VAULT.ordinal() && (mc.player == null || a.entity != mc.player.getId()) && age >= 4) {
                // others' crossings end as their crawl box does (ours through stop())
                Entity e = mc.level == null ? null : mc.level.getEntity(a.entity);
                if (e == null || !e.hasPose(Pose.SWIMMING)) {
                    a.entity = -1;
                    continue;
                }
            }
            if (a.move == Move.SLIDE.ordinal()) slope(a, mc.level == null ? null : mc.level.getEntity(a.entity));
            // other players' slides throw their grit every tick (ours do it as they move)
            if (a.move == Move.SLIDE.ordinal() && mc.level != null && (mc.player == null || a.entity != mc.player.getId())) {
                Entity e = mc.level.getEntity(a.entity);
                if (e != null && e.hasPose(Pose.SWIMMING)) {
                    float yaw = a.yaw * Mth.DEG_TO_RAD;
                    BodyFx.slideSpray(e, -Mth.sin(yaw), Mth.cos(yaw), 1F);
                }
            }
        }
    }

    /** The slope the slide is going down, from how the body moved this tick (ours and others' alike; SPEC §17.22). */
    private static void slope(Anim a, @org.jspecify.annotations.Nullable Entity e) {
        a.tiltO = a.tilt;
        float target = 0;
        if (e != null) {
            double dx = e.getX() - e.xo, dz = e.getZ() - e.zo, dy = e.getY() - e.yo, h = Math.sqrt(dx * dx + dz * dz);
            if (h > 0.05 && dy < -0.02) target = Mth.clamp((float) Math.toDegrees(Math.atan2(-dy, h)), 0F, 40F);
        }
        a.tilt += (target - a.tilt) * 0.4F;
    }

    static void reset() {
        for (Anim a : SLOTS) {
            if (a.entity >= 0 && a.clip) PlayerClips.stop(a.entity);
            a.entity = -1;
        }
        for (int i = 0; i < RUN_ENTITY.length; i++) {
            if (RUN_ENTITY[i] >= 0) PlayerClips.stop(RUN_ENTITY[i]);
            RUN_ENTITY[i] = -1;
            RUN_CLIP[i] = null;
        }
    }

    private static int length(int move) {
        return switch (Move.byId(move)) {
            case ROLL -> BodyRules.ROLL_TICKS;
            case TACTICAL_SPRINT -> BodyRules.TAC_TICKS;
            // a slide down a run goes on (ours ends through stop(), others' with their crawl pose)
            case SLIDE -> BodyRules.SLIDE_TICKS + BodyRules.SLIDE_GRACE + BodyRules.STAIR_MAX_TICKS + BodyRules.STAIR_RUNOUT;
            case MANTLE -> BodyRules.MANTLE_TICKS + 3;
            case VAULT -> 28; // the longest crossing (a rail on a block, walked at); ours end through stop()
            // spike S3: other players' runner poses last this long (the local player's follow its own state)
            case WALL_RUN -> dev.awaken.body.RunnerRules.RUN_TICKS;
            case WALL_CLIMB -> dev.awaken.body.RunnerRules.CLIMB_TICKS + 2;
            case WALL_JUMP, LEDGE_JUMP -> 10;
            case LEDGE_HANG, SHIMMY, GRIP -> 60;
            case null -> 0;
        };
    }

    private static Anim find(int id) {
        for (Anim a : SLOTS) if (a.entity == id) return a;
        return null;
    }

    private static Anim free() {
        Anim oldest = SLOTS[0];
        for (Anim a : SLOTS) {
            if (a.entity < 0) return a;
            if (a.start < oldest.start) oldest = a;
        }
        return oldest;
    }

    /** The slot drawing this player now, or null; a remote slide ends with its crawl pose. */
    private static Anim live(Entity e) {
        Anim a = find(e.getId());
        if (a == null || a.clip || a.move == Move.VAULT.ordinal()) return a; // a clip's (and a crossing's) end is decided in tick()
        if (a.move == Move.SLIDE.ordinal() && !e.hasPose(Pose.SWIMMING)) {
            a.entity = -1;
            return null;
        }
        if (a.move == Move.TACTICAL_SPRINT.ordinal() && !e.isSprinting()) {
            a.entity = -1;
            return null;
        }
        return a;
    }

    // ---- the render state -------------------------------------------------------------------------------------------

    static void modify(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player) || !(state instanceof HumanoidRenderState s)) return;
        if (entity == net.minecraft.client.Minecraft.getInstance().player && RunnerMoves.active() && runnerPose(s)) return;
        Anim a = live(entity);
        if (a == null) return;
        if (a.clip) {
            // the clip poses every limb; vanilla still must not draw a crawl or a crouch under it, and a slide faces its way
            if (a.ending) return;
            s.isCrouching = false;
            if (a.move == Move.VAULT.ordinal()) {
                // over the rail the box is the crawl's: the clip draws the figure, facing the way it crosses
                s.swimAmount = 0;
                s.isVisuallySwimming = false;
                s.yRot = Mth.clamp(Mth.wrapDegrees(s.yRot + s.bodyRot - a.yaw), -80F, 80F);
                s.bodyRot = a.yaw;
                return;
            }
            if (a.move == Move.SLIDE.ordinal() && entity.hasPose(Pose.SWIMMING)) {
                s.swimAmount = 0;
                s.isVisuallySwimming = false;
                s.yRot = Mth.clamp(Mth.wrapDegrees(s.yRot + s.bodyRot - a.yaw), -80F, 80F);
                s.bodyRot = a.yaw;
            }
            return;
        }
        float age = ClientMoves.ticks + s.partialTick - a.start;
        boolean right = s.mainArm == HumanoidArm.RIGHT;
        switch (Move.byId(a.move)) {
            case SLIDE -> {
                // vanilla would draw a crawl (face down); a slide sits back, feet first
                s.swimAmount = 0;
                s.isVisuallySwimming = false;
                s.isCrouching = false;
                s.isPassenger = true; // legs out in front
                s.walkAnimationSpeed = 0;
                // the body points down the slide; the head keeps looking where it was
                s.yRot = Mth.clamp(Mth.wrapDegrees(s.yRot + s.bodyRot - a.yaw), -80F, 80F);
                s.bodyRot = a.yaw;
                // the lead hand up in a guard; the other hangs back to the ground (the seat's forward tilt, undone by the lean)
                if (right) s.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                else s.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            }
            case MANTLE -> {
                float u = age / BodyRules.MANTLE_TICKS;
                if (u < 0.45F) {
                    s.rightArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                    s.leftArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                } else {
                    s.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                    s.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                    s.isCrouching = u < 1.2F;
                }
                s.walkAnimationSpeed = 0;
            }
            case VAULT -> {
                // without the clips: standing (never a crawl), facing the way; the arms reach for the rail, then the
                // figure is tilted over it (onPre)
                s.swimAmount = 0;
                s.isVisuallySwimming = false;
                s.yRot = Mth.clamp(Mth.wrapDegrees(s.yRot + s.bodyRot - a.yaw), -80F, 80F);
                s.bodyRot = a.yaw;
                s.walkAnimationSpeed = 0;
                boolean reach = age < VAULT_PLANT[Mth.clamp(a.kind, 0, VAULT_PLANT.length - 1)];
                HumanoidModel.ArmPose arms = reach ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.BLOCK;
                if (a.kind == dev.awaken.body.VaultRules.VAULT) {
                    // one hand on the rail (the off hand), the main arm out for balance
                    if (right) s.leftArmPose = arms;
                    else s.rightArmPose = arms;
                } else {
                    s.rightArmPose = arms;
                    s.leftArmPose = arms;
                }
                s.isCrouching = !reach;
            }
            case ROLL -> {
                if (age < BodyRules.ROLL_TICKS) {
                    s.isCrouching = true;
                    s.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                    s.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                    s.walkAnimationSpeed = 0;
                }
            }
            case TACTICAL_SPRINT -> s.walkAnimationSpeed = Math.min(1.6F, s.walkAnimationSpeed * 1.4F);
            case WALL_RUN -> s.walkAnimationSpeed = Math.max(1.1F, s.walkAnimationSpeed);
            case LEDGE_HANG, SHIMMY -> {
                s.rightArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                s.leftArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                s.walkAnimationSpeed = 0;
                s.isCrouching = false;
            }
            case null, default -> {
            }
        }
    }

    // ---- the whole figure -------------------------------------------------------------------------------------------

    static void onPre(RenderPlayerEvent.Pre<?> e) {
        AvatarRenderState s = e.getRenderState();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && s.id == mc.player.getId() && RunnerMoves.active()) {
            runnerFigure(e.getPoseStack(), s);
            return;
        }
        Anim a = find(s.id);
        if (a == null) return;
        float yaw = s.bodyRot * Mth.DEG_TO_RAD;
        PoseStack ps = e.getPoseStack();
        float slope = a.move == Move.SLIDE.ordinal() ? Mth.lerp(s.partialTick, a.tiltO, a.tilt) : 0F;
        if (a.clip) { // a clip moves the whole figure itself; down a run it is only tipped to the slope
            if (slope > 0.5F && s.hasPose(Pose.SWIMMING)) {
                ps.pushPose();
                pushed++;
                tip(ps, 0.1F, slope, yaw);
            }
            return;
        }
        float age = ClientMoves.ticks + s.partialTick - a.start;
        // forward (the way the body faces) and the axis across it: turning about it tips the top forward
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        switch (Move.byId(a.move)) {
            case SLIDE -> {
                if (!s.hasPose(Pose.SWIMMING)) return;
                float in = Math.min(1F, age / 2.5F);
                float lean = -34F * in; // sitting well back, feet first
                ps.pushPose();
                pushed++;
                if (slope > 0.5F) tip(ps, 0.1F, slope, yaw); // down a run: the whole seat follows the slope
                // the hips come down to the ground and back a little, so the legs lie along the box's length
                ps.translate(-fx * 0.2F * in, -0.6F * in, -fz * 0.2F * in);
                tip(ps, 0.72F, lean, yaw);
            }
            case ROLL -> {
                if (age >= BodyRules.ROLL_TICKS) return;
                float u = Mth.clamp(age / BodyRules.ROLL_TICKS, 0, 1);
                float turn = 360F * (u * u * (3 - 2 * u)); // smooth start and finish
                float lift = 0.45F * Mth.square(Mth.sin(turn * 0.5F * Mth.DEG_TO_RAD));
                ps.pushPose();
                pushed++;
                ps.translate(fx * 0.5F * Mth.sin(u * Mth.PI), lift - 0.25F * Mth.sin(u * Mth.PI), fz * 0.5F * Mth.sin(u * Mth.PI));
                tip(ps, 0.6F, turn, yaw);
            }
            case MANTLE -> {
                float u = Mth.clamp(age / BodyRules.MANTLE_TICKS, 0, 1.3F);
                // hanging off the hands, then the chest goes over the lip
                float lean = u < 0.45F ? -8F * u / 0.45F : 42F * Mth.sin(Math.min(1F, (u - 0.45F) / 0.85F) * Mth.PI);
                ps.pushPose();
                pushed++;
                tip(ps, 0.9F, lean, yaw);
            }
            case TACTICAL_SPRINT -> {
                float in = Math.min(1F, age / 4F);
                ps.pushPose();
                pushed++;
                tip(ps, 0F, 18F * in, yaw);
            }
            case VAULT -> {
                // the figure lowered so its hips cross at the rail, leaning over it; a running vault rolls onto its side
                // with the legs swung out to the main hand's side
                int k = Mth.clamp(a.kind, 0, VAULT_PLANT.length - 1);
                float plant = VAULT_PLANT[k], len = VAULT_LEN[k];
                float in = Mth.clamp(age / plant, 0, 1), out = Mth.clamp((len - age) / (len - plant - VAULT_OVER[k]), 0, 1);
                float w = Math.min(in, out);
                w = w * w * (3 - 2 * w);
                if (w <= 0) return;
                float side = s.mainArm == HumanoidArm.RIGHT ? 1 : -1;
                ps.pushPose();
                pushed++;
                if (k == dev.awaken.body.VaultRules.ONTO) {
                    tip(ps, 0.9F, 34F * w, yaw);
                    return;
                }
                ps.translate(0, -0.72F * w, 0);
                tip(ps, 0.9F, (k == dev.awaken.body.VaultRules.VAULT ? 28F : 40F) * w, yaw);
                if (k == dev.awaken.body.VaultRules.VAULT || k == dev.awaken.body.VaultRules.HOP) {
                    // roll about the way, the top onto the planted (off) hand, the legs swung out to the main side (a turn
                    // about forward (fx, fz) by a positive angle takes the top to the right)
                    ps.translate(0, 0.9F, 0);
                    ps.rotate(Q.rotationAxis(-(k == dev.awaken.body.VaultRules.VAULT ? 58F : 30F) * side * w * Mth.DEG_TO_RAD, fx, 0, fz));
                    ps.translate(0, -0.9F, 0);
                }
            }
            case null, default -> {
            }
        }
    }

    /** A crossing's nominal timing by kind (VaultRules.VAULT .. OVER, ticks): the hand meets the rail, over the top, the whole. */
    static final float[] VAULT_PLANT = {3, 4, 4, 4, 5}, VAULT_OVER = {3, 5, 7, 4, 5}, VAULT_LEN = {12, 15, 21, 11, 18};

    static void onPost(RenderPlayerEvent.Post<?> e) {
        if (pushed > 0) {
            e.getPoseStack().popPose();
            pushed--;
        }
    }

    // ---- the Runner's clips (Player Animation Library) ---------------------------------------------------------------

    private static final int[] RUN_ENTITY = new int[32];
    private static final String[] RUN_CLIP = new String[32];

    static {
        java.util.Arrays.fill(RUN_ENTITY, -1);
    }

    private static boolean isLocal(int entityId) {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        return p != null && p.getId() == entityId;
    }

    /** A Runner clip on an entity (the local player from its prediction, others from the server's Seen). */
    static void runner(int entityId, String clip) {
        if (isLocal(entityId)) BodyFp.clip(clip);
        if (!PlayerClips.available()) return;
        int slot = -1, free = -1;
        for (int i = 0; i < RUN_ENTITY.length; i++) {
            if (RUN_ENTITY[i] == entityId) slot = i;
            else if (RUN_ENTITY[i] < 0 && free < 0) free = i;
        }
        if (slot < 0) slot = free < 0 ? 0 : free;
        // a loop already running stays as it is; a one-shot always starts again
        if (dev.awaken.body.BodyClips.loops(clip) && clip.equals(RUN_CLIP[slot]) && RUN_ENTITY[slot] == entityId) return;
        // a body move's clip on the same layer steps aside
        Anim a = find(entityId);
        if (a != null) a.entity = -1;
        if (PlayerClips.play(entityId, clip, clip.equals(dev.awaken.body.BodyClips.HANG) ? 3 : 2)) {
            RUN_ENTITY[slot] = entityId;
            RUN_CLIP[slot] = clip;
        }
    }

    /** The Runner move is over: a loop settles through its {@code _end}, a one-shot finishes by itself. */
    static void runnerEnd(int entityId) {
        for (int i = 0; i < RUN_ENTITY.length; i++) {
            if (RUN_ENTITY[i] != entityId) continue;
            String c = RUN_CLIP[i];
            if (c != null && dev.awaken.body.BodyClips.loops(c)) PlayerClips.play(entityId, c + "_end", 2);
            RUN_ENTITY[i] = -1;
            RUN_CLIP[i] = null;
        }
    }

    // ---- the Runner's Kit, vanilla-model poses (the local player, from its own prediction; under the clips) ---------

    /** Limbs: a wall-run's quick stride; a hang's arms overhead, legs still, the body square to the wall. */
    private static boolean runnerPose(HumanoidRenderState s) {
        switch (RunnerMoves.state) {
            case RUN -> {
                s.walkAnimationSpeed = Math.max(1.3F, s.walkAnimationSpeed);
                s.isCrouching = false;
                return true;
            }
            case HANG, PATH -> {
                float wy = RunnerMoves.wallYaw();
                s.yRot = Mth.clamp(Mth.wrapDegrees(s.yRot + s.bodyRot - wy), -80F, 80F);
                s.bodyRot = wy;
                s.isCrouching = false;
                boolean over = RunnerMoves.state == RunnerMoves.State.PATH && RunnerMoves.pathKind == RunnerMoves.CLIMB
                        && RunnerMoves.pathAge > RunnerMoves.pathLen * 0.45;
                HumanoidModel.ArmPose arms = over ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.THROW_TRIDENT;
                s.rightArmPose = arms;
                s.leftArmPose = arms;
                s.walkAnimationSpeed = RunnerMoves.shimmying ? 0.35F : 0F;
                return true;
            }
            case GRIP -> {
                // facing the line; without the clips, the arms take turns up it while climbing, both up holding
                float wy = RunnerMoves.wallYaw();
                s.yRot = Mth.clamp(Mth.wrapDegrees(s.yRot + s.bodyRot - wy), -80F, 80F);
                s.bodyRot = wy;
                s.isCrouching = false;
                boolean climbing = GripMoves.mode == GripMoves.CLIMB || GripMoves.mode == GripMoves.HOP;
                boolean first = ((int) (GripMoves.travelled / 0.8)) % 2 == 0;
                s.rightArmPose = climbing && !first ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.THROW_TRIDENT;
                s.leftArmPose = climbing && first ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.THROW_TRIDENT;
                s.walkAnimationSpeed = climbing ? 0.5F : 0F;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** The whole figure: on a wall the body leans out from it, feet to the wall; hanging, the feet tuck in under the hands. */
    private static void runnerFigure(PoseStack ps, AvatarRenderState s) {
        float age = ClientMoves.ticks + s.partialTick;
        switch (RunnerMoves.state) {
            case RUN -> {
                double tx = RunnerMoves.runTx, tz = RunnerMoves.runTz;
                double nx = RunnerMoves.runAxis == 0 ? -RunnerMoves.runSide : 0, nz = RunnerMoves.runAxis == 2 ? -RunnerMoves.runSide : 0;
                float side = Math.signum((float) (-tz * nx + tx * nz));
                float in = Math.min(1F, (age - RunnerMoves.runStart) / 4F);
                ps.pushPose();
                pushed++;
                ps.rotate(Q.rotationAxis(22F * side * in * Mth.DEG_TO_RAD, (float) tx, 0, (float) tz));
            }
            case HANG, PATH -> {
                if (RunnerMoves.state == RunnerMoves.State.PATH && RunnerMoves.pathKind == RunnerMoves.CLIMB) {
                    float u = Mth.clamp(RunnerMoves.pathAge / (float) RunnerMoves.pathLen, 0, 1);
                    ps.pushPose();
                    pushed++;
                    tip(ps, 0.9F, u < 0.45F ? -8F * u / 0.45F : 42F * Mth.sin(Math.min(1F, (u - 0.45F) / 0.85F) * Mth.PI), s.bodyRot * Mth.DEG_TO_RAD);
                    return;
                }
                float sway = RunnerMoves.shimmying ? 2.5F * Mth.sin(age * 0.6F) : 1.2F * Mth.sin(age * 0.12F);
                ps.pushPose();
                pushed++;
                tip(ps, 2.0F, -6F + sway, s.bodyRot * Mth.DEG_TO_RAD);
            }
            case GRIP -> {
                // close in to the line, pivoting at the hands: sliding, the hips sit back a little off it
                float slide = GripMoves.mode == GripMoves.SLIDE ? 1F : 0F;
                float sway = 0.8F * Mth.sin(age * 0.15F);
                ps.pushPose();
                pushed++;
                tip(ps, 1.7F, 3F - 9F * slide + sway, s.bodyRot * Mth.DEG_TO_RAD);
            }
            default -> {
            }
        }
    }

    /** Tips the figure forward by degrees (negative: back) about a horizontal axis across the body at pivot height. */
    private static void tip(PoseStack ps, float pivot, float degrees, float yaw) {
        ps.translate(0, pivot, 0);
        ps.rotate(Q.rotationAxis(degrees * Mth.DEG_TO_RAD, Mth.cos(yaw), 0, Mth.sin(yaw)));
        ps.translate(0, -pivot, 0);
    }
}
