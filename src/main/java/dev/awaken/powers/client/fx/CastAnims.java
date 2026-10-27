package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.CastClips;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.CastProfiles;
import dev.awaken.powers.fx.FxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Cast animations without mixins. Third person: a render-state modifier swaps in vanilla arm poses (the horn pose is
 * a straight punch, the bow pose two palms out, the trident pose an arm cocked overhead, the shield pose a guard)
 * and twists the body, and a player render event leans the whole figure. First person: the hand render event moves
 * the drawn arm, and draws the off arm too for two-handed casts. Held wind-ups tremble until released.
 * <p>
 * With the Player Animation Library installed (optional), third person plays a full-body clip per power family instead
 * ({@link PalCasts}, the only class that touches the library; clips from {@code CastClips}); the vanilla poses and the
 * lean then step aside for that player, and first person stays exactly as here.
 */
final class CastAnims {
    private CastAnims() {}

    private static final class Anim {
        int entity = -1;
        CastAnim kind = CastAnim.NONE;
        float start;
        int ticks;
        boolean held;
        float releasedAt = -1;
        float hold;
        /** The library is posing this one (full body), so the vanilla poses and lean leave it alone. */
        boolean pal;
        Family family = Family.ENERGY;
        boolean palFrozen;
        boolean palLetGo;
        /** The power's cast profile (its own clips and first-person clips), or null: the family's pose. */
        CastProfiles.@Nullable Profile profile;
        /** The profile's hold loop has taken over from its begin clip. */
        boolean holdStarted;
        /** The profile's screen moment has played (once, on the release tick). */
        boolean momentPlayed;
    }

    /** The Player Animation Library is installed (and took our layer): casts get full-body clips in third person. */
    private static boolean pal = ModList.get().isLoaded("player_animation_library");

    static void init() {
        if (!pal) return;
        try {
            PalCasts.init();
        } catch (RuntimeException | LinkageError ex) {
            // another version of the library with a different API: keep the vanilla poses
            pal = false;
            dev.awaken.Awaken.LOG.warn("Player Animation Library found but its API did not match; using the vanilla cast poses", ex);
        }
    }

    private static final Anim[] SLOTS = new Anim[48];
    private static final Quaternionf Q = new Quaternionf();
    /** The animation a render state was extracted with (render states carry no entity id). */
    private static final ContextKey<Anim> ANIM = new ContextKey<>(dev.awaken.Awaken.id("cast_anim"));
    private static boolean leaned;

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Anim();
    }

    private static float now(float pt) {
        return FxWorld.ticks + pt;
    }

    /** Starts an animation on an entity; a held one stays until {@link #release}. */
    static void play(int entityId, CastAnim kind, int ticks, @Nullable Family family) {
        start(entityId, kind, ticks, family, null);
    }

    /** Starts a power's cast profile: its own clips (third person), the vanilla pose {@code fallback} without the library. */
    static void playProfile(int entityId, CastProfiles.Profile profile, @Nullable Family family) {
        start(entityId, profile.fallback(), 0, family, profile);
    }

    private static void start(int entityId, CastAnim kind, int ticks, @Nullable Family family, CastProfiles.@Nullable Profile profile) {
        if (kind == CastAnim.NONE || entityId < 0) return;
        Anim a = find(entityId);
        boolean afterHold = profile == null && a != null && a.held && a.pal && !kind.held;
        boolean wasPal = a != null && a.pal;
        if (a == null) {
            a = free();
            // a held clip on the evicted player would loop forever
            if (a.entity >= 0 && a.pal) PalCasts.stop(a.entity);
        }
        a.entity = entityId;
        a.kind = kind;
        a.start = FxWorld.ticks;
        a.ticks = Math.max(1, ticks > 0 ? ticks : kind.ticks);
        a.held = kind.held;
        a.releasedAt = -1;
        a.hold = 0;
        a.family = family == null ? Family.ENERGY : family;
        a.palFrozen = false;
        a.palLetGo = false;
        a.pal = false;
        a.profile = profile;
        a.holdStarted = false;
        a.momentPlayed = false;
        if (pal && PowerFxConfig.animations()) {
            String clip = profile != null ? profile.begin3p() : CastClips.clip(a.family, kind, afterHold);
            a.pal = clip != null && PalCasts.play(entityId, clip, fadeIn(kind));
        }
        if (wasPal && !a.pal) PalCasts.stop(entityId);
    }

    /** Ticks a clip blends in over: casts start from a standing pose, holds and slams start already posed. */
    private static int fadeIn(CastAnim kind) {
        return switch (kind) {
            case HOVER -> 4;
            case PUNCH_WIND, CHARGE2, AIM_HOLD, SLAM_WIND -> 3;
            case SLAM -> 2;
            default -> 1;
        };
    }

    /** True when the entity started this animation within the last few ticks (prediction already played it). */
    static boolean recently(int entityId, CastAnim kind, int withinTicks) {
        Anim a = find(entityId);
        return a != null && a.kind == kind && FxWorld.ticks - a.start <= withinTicks;
    }

    /** True when the entity started this cast profile within the last few ticks (prediction already played it). */
    static boolean recentlyProfile(int entityId, int profileId, int withinTicks) {
        Anim a = find(entityId);
        return a != null && a.profile != null && a.profile.id() == profileId && FxWorld.ticks - a.start <= withinTicks;
    }

    /** The cast profile an entity is in (CastProfiles ids start at 1), or 0. */
    static int profileOf(int entityId) {
        Anim a = find(entityId);
        return a == null || a.profile == null ? 0 : a.profile.id();
    }

    /** Whether an entity has a cast pose running (the sampling layer reads only those bodies). */
    static boolean live(int entityId) {
        return find(entityId) != null;
    }

    /** Whether the entity is in this cast profile and has not let go of it (a channel's hold). */
    static boolean holding(int entityId, int profileId) {
        Anim a = find(entityId);
        return a != null && a.profile != null && a.profile.id() == profileId && a.releasedAt < 0;
    }

    /** Ticks since the entity's cast profile began (with the frame's partial tick), or -1 when it is not in that profile. */
    static float profileAge(int entityId, int profileId, float pt) {
        Anim a = find(entityId);
        return a == null || a.profile == null || a.profile.id() != profileId ? -1 : now(pt) - a.start;
    }

    static void release(int entityId) {
        Anim a = find(entityId);
        if (a != null && a.held && a.releasedAt < 0) {
            a.releasedAt = FxWorld.ticks;
            if (a.profile != null) momentFor(entityId, a.profile.endMoment(), a.profile);
        }
    }

    private static void momentFor(int entityId, String name, CastProfiles.Profile prof) {
        var level = Minecraft.getInstance().level;
        Entity who = level == null ? null : level.getEntity(entityId);
        if (who != null) CastFeel.moment(name, who, FxStyle.of(dev.awaken.powers.PowerRegistry.get(prof.power()) == null ? PowerType.Family.ENERGY : dev.awaken.powers.PowerRegistry.get(prof.power()).family).mid);
    }

    /** Freezes an entity's animation for a hit-stop. */
    static void hold(int entityId, int ms) {
        Anim a = find(entityId);
        if (a != null) a.hold = Math.max(a.hold, ms / 50f);
    }

    static void tick() {
        for (Anim a : SLOTS) {
            if (a.entity < 0) continue;
            if (a.pal && (a.hold > 0) != a.palFrozen) {
                a.palFrozen = a.hold > 0;
                PalCasts.freeze(a.entity, a.palFrozen);
            }
            if (a.hold > 0) {
                float h = Math.min(1, a.hold);
                a.start += h;
                if (a.releasedAt >= 0) a.releasedAt += h;
                a.hold -= h;
            }
            float age = FxWorld.ticks - a.start;
            boolean done = a.held ? (a.releasedAt >= 0 && FxWorld.ticks - a.releasedAt > 5) || age > 20 * 30 : age > a.ticks + 2;
            // a held clip lets go a moment after release, so a release cast arriving just behind it takes over smoothly
            // the profile's screen moment lands on the tick its clips release on
            if (a.profile != null && !a.momentPlayed && age >= a.profile.release()) {
                a.momentPlayed = true;
                momentFor(a.entity, a.profile.moment(), a.profile);
            }
            // a profile's begin clip hands over to its hold loop where it ends (the hold starts in the pose the begin ends in)
            if (a.pal && a.profile != null && a.profile.hold3p() != null && !a.holdStarted && !a.palLetGo && a.releasedAt < 0 && age >= a.profile.holdAt()) {
                a.holdStarted = true;
                if (!PalCasts.play(a.entity, a.profile.hold3p(), 2)) a.pal = false;
            }
            if (a.pal && a.held && !a.palLetGo && (done || a.releasedAt >= 0 && FxWorld.ticks - a.releasedAt >= 2)) {
                a.palLetGo = true;
                String clip = a.profile != null ? a.profile.end3p() : CastClips.letGo(a.family, a.kind);
                if (clip == null || !PalCasts.play(a.entity, clip, 2)) PalCasts.stop(a.entity);
            }
            if (done && a.pal && a.palFrozen) PalCasts.freeze(a.entity, false);
            if (done) a.entity = -1;
        }
    }

    static void reset() {
        for (Anim a : SLOTS) {
            if (a.entity >= 0 && a.pal) PalCasts.stop(a.entity);
            a.entity = -1;
        }
    }

    private static int mcPlayerId() {
        var p = Minecraft.getInstance().player;
        return p == null ? -1 : p.getId();
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

    /** 0..1 progress, or for held anims 0..1 ramp-in then 1 while held. */
    private static float progress(Anim a, float pt) {
        float age = now(pt) - a.start;
        if (a.held) return Math.min(1, age / 5f);
        return Mth.clamp(age / a.ticks, 0, 1);
    }

    /** Envelope: 0 at both ends, 1 in the middle (held: fades out after release). */
    private static float envelope(Anim a, float pt) {
        if (a.held) {
            float in = Math.min(1, (now(pt) - a.start) / 4f);
            if (a.releasedAt >= 0) return in * Math.max(0, 1 - (now(pt) - a.releasedAt) / 5f);
            return in;
        }
        float p = progress(a, pt);
        float in = Math.min(1, p / 0.15f), out = Math.min(1, (1 - p) / 0.35f);
        return Math.max(0, Math.min(in, out));
    }

    // ---- third person -----------------------------------------------------------------------------------------------

    /** Render-state modifier: poses the arms and twists the body of any humanoid with a running cast animation. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(state instanceof HumanoidRenderState s)) return;
        Anim a = PowerFxConfig.animations() ? find(entity.getId()) : null;
        // the library poses the whole body (lean included) for this one
        if (a != null && a.pal) a = null;
        s.setRenderData(ANIM, a);
        if (a == null) return;
        float pt = s.partialTick;
        float env = envelope(a, pt);
        if (env <= 0.02f) return;
        float p = progress(a, pt);
        boolean right = s.mainArm == HumanoidArm.RIGHT;
        float side = right ? 1 : -1;
        HumanoidModel.ArmPose none = HumanoidModel.ArmPose.EMPTY;
        float tremble = a.held && a.releasedAt < 0 ? Mth.sin(now(pt) * 2.7f) * 1.6f : 0;
        switch (a.kind) {
            case PUNCH -> {
                main(s, right, p < 0.12f ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.TOOT_HORN);
                twist(s, side * 28 * env);
            }
            case PUNCH_WIND -> {
                main(s, right, HumanoidModel.ArmPose.THROW_TRIDENT);
                twist(s, -side * 22 * env + tremble);
                s.isCrouching = true;
            }
            case PALM, AIM, AIM_HOLD -> {
                main(s, right, HumanoidModel.ArmPose.TOOT_HORN);
                twist(s, side * 12 * env + tremble);
            }
            case PUSH2, CLAP -> both(s, HumanoidModel.ArmPose.BOW_AND_ARROW);
            case CHARGE2 -> {
                both(s, HumanoidModel.ArmPose.BLOCK);
                twist(s, tremble);
            }
            case RAISE -> main(s, right, HumanoidModel.ArmPose.THROW_TRIDENT);
            case SLAM_WIND -> {
                both(s, HumanoidModel.ArmPose.BOW_AND_ARROW);
                s.xRot = Mth.lerp(env, s.xRot, -80) + tremble;
            }
            case SLAM -> {
                both(s, HumanoidModel.ArmPose.BOW_AND_ARROW);
                s.xRot = Mth.lerp(env, s.xRot, 55);
                s.isCrouching = p < 0.8f;
            }
            case STOMP -> s.isCrouching = p > 0.15f && p < 0.75f;
            case BRACE -> {
                both(s, HumanoidModel.ArmPose.BLOCK);
                s.isCrouching = true;
            }
            case TEMPLE -> main(s, right, HumanoidModel.ArmPose.SPYGLASS);
            case SKYWARD -> {
                both(s, HumanoidModel.ArmPose.BOW_AND_ARROW);
                s.xRot = Mth.lerp(env, s.xRot, -62);
            }
            case FLURRY -> {
                boolean left = ((int) (now(pt) / 2)) % 2 == 0;
                if (left) {
                    s.rightArmPose = right ? none : HumanoidModel.ArmPose.TOOT_HORN;
                    s.leftArmPose = right ? HumanoidModel.ArmPose.TOOT_HORN : none;
                } else {
                    s.rightArmPose = right ? HumanoidModel.ArmPose.TOOT_HORN : none;
                    s.leftArmPose = right ? none : HumanoidModel.ArmPose.TOOT_HORN;
                }
                twist(s, (left ? -1 : 1) * 18 * env);
            }
            case THROW -> {
                main(s, right, p < 0.35f ? HumanoidModel.ArmPose.THROW_TRIDENT : HumanoidModel.ArmPose.TOOT_HORN);
                twist(s, (p < 0.35f ? -side : side) * 24 * env);
            }
            case CONJURE -> {
                main(s, right, HumanoidModel.ArmPose.TOOT_HORN);
                s.xRot = Mth.lerp(env, s.xRot, p < 0.55f ? 40 : 5);
            }
            default -> {
            }
        }
    }

    private static void main(HumanoidRenderState s, boolean right, HumanoidModel.ArmPose pose) {
        if (right) s.rightArmPose = pose;
        else s.leftArmPose = pose;
    }

    private static void both(HumanoidRenderState s, HumanoidModel.ArmPose pose) {
        s.rightArmPose = pose;
        s.leftArmPose = pose;
    }

    /** Turns the body by degrees while the head keeps looking where it was. */
    private static void twist(HumanoidRenderState s, float degrees) {
        s.bodyRot += degrees;
        s.yRot -= degrees;
    }

    /** Lean (degrees, + forward) of the whole figure for an animation at this moment. */
    private static float lean(Anim a, float pt) {
        float env = envelope(a, pt);
        float p = progress(a, pt);
        return switch (a.kind) {
            case DASH -> 30 * env;
            case PUNCH -> (p < 0.12f ? -6 : 14) * env;
            case PUNCH_WIND -> -8 * env;
            case PALM, PUSH2 -> 12 * env;
            case CHARGE2 -> -6 * env;
            case SLAM -> 18 * env;
            case STOMP -> 16 * env;
            case SKYWARD -> -12 * env;
            case THROW -> (p < 0.35f ? -10 : 16) * env;
            case FLURRY -> 10 * env;
            default -> 0;
        };
    }

    static void onPlayerPre(RenderPlayerEvent.Pre<?> e) {
        leaned = false;
        if (!PowerFxConfig.animations()) return;
        AvatarRenderState s = e.getRenderState();
        Anim a = s.getRenderData(ANIM);
        if (a == null || a.entity < 0) return;
        float deg = lean(a, s.partialTick);
        float bob = a.kind == CastAnim.HOVER ? 0.06f * Mth.sin(now(s.partialTick) * 0.25f) : 0;
        if (Math.abs(deg) < 0.2f && bob == 0) return;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        leaned = true;
        if (bob != 0) ps.translate(0, bob, 0);
        float yaw = s.bodyRot * Mth.DEG_TO_RAD;
        // axis = -right of the body = (cos yaw, 0, sin yaw): rotating about it tips the top forward
        ps.rotate(Q.rotationAxis(deg * Mth.DEG_TO_RAD, Mth.cos(yaw), 0, Mth.sin(yaw)));
    }

    static void onPlayerPost(RenderPlayerEvent.Post<?> e) {
        if (leaned) {
            e.getPoseStack().popPose();
            leaned = false;
        }
    }

    // ---- first person -----------------------------------------------------------------------------------------------

    private static boolean pushedHand;

    static void onHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        // a cast profile's first person is Hands' (fp_casts.py clips), not these offsets
        Anim own = mc.player == null ? null : find(mc.player.getId());
        if (own != null && own.profile != null) return;
        if (e.getHand() == InteractionHand.OFF_HAND) {
            if (pushedHand) {
                e.getPoseStack().popPose();
                pushedHand = false;
            }
            Anim a = mc.player == null ? null : find(mc.player.getId());
            // a two-handed cast already drew this arm with the main hand
            if (a != null && a.kind.twoHanded() && e.getItemStack().isEmpty() && PowerFxConfig.animations()) e.setCanceled(true);
            return;
        }
        pushedHand = false;
        if (mc.player == null || !PowerFxConfig.animations()) return;
        Anim a = find(mc.player.getId());
        if (a == null) return;
        float pt = e.getPartialTick();
        float env = envelope(a, pt);
        if (env <= 0.02f) return;
        PlayerRenderState ps = e.getPlayerState();
        AvatarRenderState avatar = ps.avatarRenderState;
        if (avatar == null) return;
        boolean rightMain = avatar.mainArm == HumanoidArm.RIGHT;
        if (!e.getItemStack().isEmpty()) {
            // holding something: move the whole hand and undo it before the off hand draws
            e.getPoseStack().pushPose();
            pushedHand = true;
            offset(e.getPoseStack(), a, pt, env, rightMain ? 1 : -1, true);
            return;
        }
        e.setCanceled(true);
        AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
        Identifier skin = avatar.skin.body().texturePath();
        drawArm(e, renderer, skin, avatar, a, pt, env, rightMain, true);
        if (a.kind.twoHanded() && (mc.player.getOffhandItem().isEmpty())) drawArm(e, renderer, skin, avatar, a, pt, env, !rightMain, false);
    }

    private static void drawArm(RenderHandEvent e, AvatarRenderer<?> renderer, Identifier skin, AvatarRenderState avatar, Anim a, float pt,
                                float env, boolean right, boolean mainHand) {
        PoseStack ps = e.getPoseStack();
        float invert = right ? 1 : -1;
        ps.pushPose();
        offset(ps, a, pt, env, invert, mainHand);
        // the vanilla empty-hand arm transform (FirstPersonHandsAndItemsRenderer.renderPlayerArm), no swing
        float equip = mainHand ? e.getEquipProgress() : 0;
        ps.translate(invert * 0.64000005F, -0.6F + equip * -0.6F, -0.71999997F);
        ps.rotateDegrees(Axis.YP, invert * 45.0F);
        ps.translate(invert * -1.0F, 3.6F, 3.5F);
        ps.rotateDegrees(Axis.ZP, invert * 120.0F);
        ps.rotateDegrees(Axis.XP, 200.0F);
        ps.rotateDegrees(Axis.YP, invert * -135.0F);
        ps.translate(invert * 5.6F, 0.0F, 0.0F);
        if (right) renderer.renderRightHand(ps, e.getSubmitNodeCollector(), e.getPackedLight(), skin, avatar.showRightSleeve);
        else renderer.renderLeftHand(ps, e.getSubmitNodeCollector(), e.getPackedLight(), skin, avatar.showLeftSleeve);
        ps.popPose();
    }

    /** Screen-space move of a first-person arm for an animation (x right, y up, z towards the viewer). */
    private static void offset(PoseStack ps, Anim a, float pt, float env, float invert, boolean mainHand) {
        float p = progress(a, pt);
        float t = now(pt);
        float dx = 0, dy = 0, dz = 0, pitch = 0, roll = 0;
        float tremble = a.held && a.releasedAt < 0 ? Mth.sin(t * 3.1f) * 0.012f : 0;
        switch (a.kind) {
            case PUNCH, PALM -> {
                float thrust = p < 0.12f ? -p / 0.12f * 0.3f : Mth.sin(Math.min(1, (p - 0.12f) / 0.4f) * Mth.HALF_PI);
                dz = -0.42f * thrust;
                dx = -0.26f * invert * thrust;
                dy = 0.12f * thrust;
                roll = a.kind == CastAnim.PALM ? -invert * 35 * thrust : 0;
            }
            case PUNCH_WIND -> {
                dz = 0.22f;
                dx = 0.08f * invert;
                dy = -0.12f + tremble;
                roll = invert * 12;
            }
            case AIM, AIM_HOLD -> {
                dz = -0.24f;
                dx = -0.2f * invert;
                dy = 0.16f + tremble;
                pitch = 12;
            }
            case PUSH2 -> {
                float thrust = Mth.sin(Math.min(1, p / 0.4f) * Mth.HALF_PI);
                dz = -0.38f * thrust;
                dx = -0.3f * invert * thrust;
                dy = 0.14f * thrust;
                roll = -invert * 30 * thrust;
            }
            case CHARGE2 -> {
                dz = -0.06f;
                dx = -0.36f * invert;
                dy = 0.08f + tremble;
                roll = -invert * 40;
            }
            case CLAP -> {
                dz = -0.2f;
                dx = -0.42f * invert * Mth.sin(Math.min(1, p / 0.3f) * Mth.HALF_PI);
                dy = 0.1f;
            }
            case RAISE, SKYWARD, SLAM_WIND -> {
                dy = 0.55f * Mth.sin(Math.min(1, p / 0.4f) * Mth.HALF_PI) + tremble;
                dz = -0.1f;
                pitch = -30;
            }
            case SLAM -> {
                dy = -0.35f * (1 - p);
                dz = -0.15f;
                pitch = 25;
            }
            case STOMP -> dy = -0.12f * Mth.sin(p * Mth.PI);
            case BRACE -> {
                dy = 0.16f;
                dx = -0.28f * invert;
                roll = -invert * 25;
            }
            case DASH -> {
                dz = 0.24f;
                dy = -0.18f;
            }
            case TEMPLE -> {
                dy = 0.42f;
                dx = -0.12f * invert;
                dz = 0.12f;
                pitch = -20;
            }
            case FLURRY -> {
                boolean mine = ((int) (t / 2) % 2 == 0) == mainHand;
                float u = (t % 2) / 2f;
                float thrust = mine ? Mth.sin(u * Mth.PI) : 0;
                dz = -0.45f * thrust;
                dx = -0.25f * invert * thrust;
                dy = 0.1f * thrust;
            }
            case THROW -> {
                float f = p < 0.35f ? -(p / 0.35f) : Mth.sin(Math.min(1, (p - 0.35f) / 0.35f) * Mth.HALF_PI);
                dz = -0.4f * f;
                dy = 0.25f * Math.abs(f);
                dx = -0.15f * invert * f;
            }
            case CONJURE -> {
                dy = p < 0.55f ? -0.25f * env : 0.3f * env;
                dz = -0.2f;
                pitch = p < 0.55f ? 30 : -15;
            }
            case HOVER -> dy = 0.03f * Mth.sin(t * 0.25f);
            default -> {
            }
        }
        ps.translate(dx * env, dy * env, dz * env);
        if (pitch != 0) ps.rotateDegrees(Axis.XP, pitch * env);
        if (roll != 0) ps.rotateDegrees(Axis.ZP, roll * env);
    }
}
