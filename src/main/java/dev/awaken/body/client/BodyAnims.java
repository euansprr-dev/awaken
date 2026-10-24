package dev.awaken.body.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.body.BodyRules;
import dev.awaken.body.Move;
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
 */
final class BodyAnims {
    private BodyAnims() {}

    private static final class Anim {
        int entity = -1;
        int move;
        float start;
        float yaw;
    }

    private static final Anim[] SLOTS = new Anim[32];
    private static final Quaternionf Q = new Quaternionf();
    private static int pushed;

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Anim();
    }

    static void play(int entityId, Move move, float yaw) {
        Anim a = find(entityId);
        if (a == null) a = free();
        a.entity = entityId;
        a.move = move.ordinal();
        a.start = ClientMoves.ticks;
        a.yaw = yaw;
    }

    static void stop(int entityId, Move move) {
        Anim a = find(entityId);
        if (a != null && a.move == move.ordinal()) a.entity = -1;
    }

    static void tick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        for (Anim a : SLOTS) {
            if (a.entity < 0) continue;
            float age = ClientMoves.ticks - a.start;
            if (age > length(a.move) + 2) {
                a.entity = -1;
                continue;
            }
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

    static void reset() {
        for (Anim a : SLOTS) a.entity = -1;
    }

    private static int length(int move) {
        return switch (Move.byId(move)) {
            case ROLL -> BodyRules.ROLL_TICKS;
            case TACTICAL_SPRINT -> BodyRules.TAC_TICKS;
            case SLIDE -> BodyRules.SLIDE_TICKS + BodyRules.SLIDE_GRACE;
            case MANTLE -> BodyRules.MANTLE_TICKS + 3;
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
        if (a == null) return null;
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
        Anim a = live(entity);
        if (a == null) return;
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
                if (u < 0.55F) {
                    s.rightArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                    s.leftArmPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                } else {
                    s.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                    s.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                    s.isCrouching = u < 1.2F;
                }
                s.walkAnimationSpeed = 0;
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
            case null -> {
            }
        }
    }

    // ---- the whole figure -------------------------------------------------------------------------------------------

    static void onPre(RenderPlayerEvent.Pre<?> e) {
        AvatarRenderState s = e.getRenderState();
        Anim a = find(s.id);
        if (a == null) return;
        float age = ClientMoves.ticks + s.partialTick - a.start;
        float yaw = s.bodyRot * Mth.DEG_TO_RAD;
        // forward (the way the body faces) and the axis across it: turning about it tips the top forward
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        PoseStack ps = e.getPoseStack();
        switch (Move.byId(a.move)) {
            case SLIDE -> {
                if (!s.hasPose(Pose.SWIMMING)) return;
                float in = Math.min(1F, age / 2.5F);
                float lean = -34F * in; // sitting well back, feet first
                ps.pushPose();
                pushed++;
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
                float lean = u < 0.5F ? -6F * u / 0.5F : 34F * Mth.sin(Math.min(1F, (u - 0.5F) / 0.8F) * Mth.PI);
                ps.pushPose();
                pushed++;
                tip(ps, 0.9F, lean, yaw);
            }
            case TACTICAL_SPRINT -> {
                float in = Math.min(1F, age / 4F);
                ps.pushPose();
                pushed++;
                tip(ps, 0F, 13F * in, yaw);
            }
            case null -> {
            }
        }
    }

    static void onPost(RenderPlayerEvent.Post<?> e) {
        if (pushed > 0) {
            e.getPoseStack().popPose();
            pushed--;
        }
    }

    /** Tips the figure forward by degrees (negative: back) about a horizontal axis across the body at pivot height. */
    private static void tip(PoseStack ps, float pivot, float degrees, float yaw) {
        ps.translate(0, pivot, 0);
        ps.rotate(Q.rotationAxis(degrees * Mth.DEG_TO_RAD, Mth.cos(yaw), 0, Mth.sin(yaw)));
        ps.translate(0, -pivot, 0);
    }
}
