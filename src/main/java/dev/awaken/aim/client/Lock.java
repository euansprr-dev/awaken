package dev.awaken.aim.client;

import dev.awaken.aim.AimRules;
import dev.awaken.aim.AimSounds;
import dev.awaken.core.api.Aim;
import dev.awaken.core.api.CameraHooks;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.client.InputLock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The mark and the lock (SPEC §40.3). Every other tick the creature nearest the middle of the view is marked (25°, 24
 * blocks, seen, hostile or hunting you; the current one is sticky). The lock key locks it: the view follows it each frame
 * (eased, at most 300°/s, the mouse keeping a quarter), a flick or the wheel moves the lock to the next creature that way,
 * and it lets go on the key, after 1.5 s unseen, past 40 blocks, on a teleport, or moves on when the creature dies.
 * Client state only: the server checks every press for itself ({@link Aim#meant}).
 */
final class Lock {
    private Lock() {}

    /** The marked creature's id (-1: none), and the locked one (-1: not locked). A lock is always also the mark. */
    static int marked = -1, locked = -1;
    /** When the mark, the lock and the last switch began (nanos), for the reticle's motion. */
    static long markedAt, lockedAt, switchedAt, releasedAt, brokeAt, hitAt;
    /** Where the lock was let go or broke (world), for the reticle's fading ring and splinters. */
    static double goneX, goneY, goneZ;
    static float goneR = 16F;

    private static int ticks, lostTicks, airTicks, prevHurt;
    private static Level lastLevel;
    private static double lastX, lastY, lastZ;
    private static boolean snapping;
    private static long lastFrame, switchGapUntil;
    private static float flickX, flickY;

    // the picking's reused arrays: candidates for the mark (best few, then a sight check) and for a switch
    private static final int MAX = 32, SIGHT_CHECKS = 6;
    private static final int[] BEST_ID = new int[SIGHT_CHECKS];
    private static final float[] BEST_SCORE = new float[SIGHT_CHECKS];
    private static final int[] CAND_ID = new int[MAX];
    private static final float[] CAND_YAW = new float[MAX], CAND_PITCH = new float[MAX], SX = new float[MAX], SY = new float[MAX];
    private static int candN;

    // ---- every tick ---------------------------------------------------------------------------------------------------

    static void tick(Minecraft mc) {
        ticks++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !AimConfig.enabled() || p.isSpectator() || !p.isAlive()) {
            reset(p != null && locked >= 0);
            return;
        }
        // a teleport or a new level lets go at once
        if (mc.level != lastLevel || p.distanceToSqr(lastX, lastY, lastZ) > 64) {
            if (locked >= 0) release(false);
            marked = -1;
        }
        lastLevel = mc.level;
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
        airTicks = p.onGround() || p.isInWater() ? 0 : airTicks + 1;
        if (locked >= 0) hold(mc, p);
        if ((ticks & 1) == 0) pick(mc, p);
    }

    /** The locked creature still there, seen, near: else let go, or move on if it died. */
    private static void hold(Minecraft mc, LocalPlayer p) {
        Entity e = mc.level.getEntity(locked);
        if (!(e instanceof LivingEntity le) || e.isRemoved() || le.isDeadOrDying()) {
            if (e instanceof LivingEntity le && le.isDeadOrDying()) died(mc, p, le);
            else release(true);
            return;
        }
        if (p.distanceTo(le) > AimRules.LOCK_RANGE) {
            release(true);
            return;
        }
        if (Aim.sees(p, le)) lostTicks = 0;
        else if (++lostTicks >= AimRules.LOST_TICKS) {
            release(true);
            return;
        }
        if (le.hurtTime > prevHurt) hitAt = System.nanoTime();
        prevHurt = le.hurtTime;
    }

    /** The locked creature fell: the ring breaks, and the lock moves to the next one fighting you in front, if any. */
    private static void died(Minecraft mc, LocalPlayer p, LivingEntity le) {
        gone(le);
        brokeAt = System.nanoTime();
        play(AimSounds.BREAK.get(), 1.0F, 0.9F);
        int dead = locked;
        locked = -1;
        int next = nearest(mc, p, AimRules.NEXT_CONE, AimRules.NEXT_RANGE, dead);
        if (next >= 0) take(next, false);
        else if (marked == dead) marked = -1;
    }

    private static void gone(LivingEntity le) {
        AABB b = le.getBoundingBox();
        goneX = (b.minX + b.maxX) / 2;
        goneY = (b.minY + b.maxY) / 2;
        goneZ = (b.minZ + b.maxZ) / 2;
        goneR = Reticle.lastR;
    }

    // ---- the mark -----------------------------------------------------------------------------------------------------

    /** Hostile, or hunting you (its aggression is synced), and never a person. */
    static boolean foe(LivingEntity e) {
        if (e instanceof Player || !e.isAlive()) return false;
        if (e instanceof Enemy) return true;
        if (e instanceof Mob m && m.isAggressive()) return true;
        return CreatureTag.of(e).kind().hostile();
    }

    private static void pick(Minecraft mc, LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot(), pitch = p.getXRot();
        int bestN = 0;
        candN = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || le == p || !foe(le)) continue;
            AABB b = le.getBoundingBox();
            double dist = Aim.gap(eye, b);
            if (dist > AimRules.LOCK_RANGE) continue;
            float off = Aim.offEdge(eye, b, yaw, pitch);
            if (locked >= 0 && le.getId() != locked && off <= AimRules.SWITCH_CONE && candN < MAX) {
                Vec3 c = b.getCenter();
                CAND_ID[candN] = le.getId();
                CAND_YAW[candN] = yawTo(eye, c);
                CAND_PITCH[candN] = pitchTo(eye, c);
                candN++;
            }
            float sc = AimRules.score(off, dist, le.getId() == marked, AimRules.MARK_CONE, AimRules.MARK_RANGE);
            if (Float.isNaN(sc)) continue;
            // keep the best few, best first
            int at = bestN < SIGHT_CHECKS ? bestN++ : SIGHT_CHECKS;
            while (at > 0 && sc < BEST_SCORE[at - 1]) {
                if (at < SIGHT_CHECKS) {
                    BEST_SCORE[at] = BEST_SCORE[at - 1];
                    BEST_ID[at] = BEST_ID[at - 1];
                }
                at--;
            }
            if (at < SIGHT_CHECKS) {
                BEST_SCORE[at] = sc;
                BEST_ID[at] = le.getId();
            }
        }
        if (locked >= 0) return; // the lock is the mark
        int was = marked;
        marked = -1;
        for (int i = 0; i < bestN; i++) {
            if (mc.level.getEntity(BEST_ID[i]) instanceof LivingEntity le && Aim.sees(p, le)) {
                marked = BEST_ID[i];
                break;
            }
        }
        if (marked != was && marked >= 0) markedAt = System.nanoTime();
    }

    /** The nearest-the-middle foe within a cone and range, seen, other than {@code except}; -1 when none. */
    private static int nearest(Minecraft mc, LocalPlayer p, float cone, float range, int except) {
        Vec3 eye = p.getEyePosition();
        int best = -1;
        float bestScore = Float.NaN;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || le == p || le.getId() == except || !foe(le)) continue;
            AABB b = le.getBoundingBox();
            float sc = AimRules.score(Aim.offEdge(eye, b, p.getYRot(), p.getXRot()), Aim.gap(eye, b), false, cone, range);
            if (AimRules.better(sc, bestScore) && Aim.sees(p, le)) {
                bestScore = sc;
                best = le.getId();
            }
        }
        return best;
    }

    // ---- the key, the wheel, the flick --------------------------------------------------------------------------------

    /** The lock key: let go when locked, else lock the mark (or a creature under the crosshair). False: nothing to lock. */
    static boolean press(Minecraft mc) {
        if (locked >= 0) {
            release(true);
            return true;
        }
        int t = marked;
        if (t < 0 && mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le.isAlive() && !(le instanceof Player)) {
            t = le.getId();
        }
        if (t < 0) return false;
        take(t, true);
        return true;
    }

    private static void take(int id, boolean sound) {
        locked = id;
        marked = id;
        long now = System.nanoTime();
        lockedAt = now;
        markedAt = now;
        lostTicks = 0;
        prevHurt = 0;
        snapping = true;
        candN = 0;
        if (sound) play(AimSounds.LOCK.get(), 1.0F, 0.85F);
    }

    static void release(boolean sound) {
        if (locked < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(locked) instanceof LivingEntity le) gone(le);
        releasedAt = System.nanoTime();
        locked = -1;
        snapping = false;
        if (sound) play(AimSounds.RELEASE.get(), 1.0F, 0.6F);
    }

    static void reset(boolean sound) {
        if (locked >= 0) release(sound);
        marked = -1;
        candN = 0;
    }

    /** The wheel while locked: up moves the lock left, down right (the hotbar stays put). */
    static boolean scroll(double notches) {
        if (locked < 0 || notches == 0 || paused(Minecraft.getInstance())) return false;
        switchToward(notches > 0 ? -1F : 1F, 0F);
        return true;
    }

    /** Moves the lock to the nearest creature on the screen along (dx, dy) from the locked one; false when none is. */
    private static boolean switchToward(float dx, float dy) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || candN == 0 || !(mc.level.getEntity(locked) instanceof LivingEntity cur)) return false;
        Vec3 eye = p.getEyePosition();
        Vec3 c = cur.getBoundingBox().getCenter();
        float cy = yawTo(eye, c), cp = pitchTo(eye, c);
        for (int i = 0; i < candN; i++) {
            SX[i] = Mth.wrapDegrees(CAND_YAW[i] - cy);
            SY[i] = CAND_PITCH[i] - cp;
        }
        for (int tries = 0; tries < 3; tries++) {
            int i = AimRules.pickSide(SX, SY, candN, dx, dy);
            if (i < 0) return false;
            if (mc.level.getEntity(CAND_ID[i]) instanceof LivingEntity le && le.isAlive() && Aim.sees(p, le)) {
                locked = CAND_ID[i];
                marked = locked;
                switchedAt = System.nanoTime();
                lostTicks = 0;
                prevHurt = le.hurtTime;
                candN = 0;
                play(AimSounds.SWITCH.get(), 1.0F, 0.7F);
                return true;
            }
            SX[i] = SY[i] = 0F; // unseen: skip it and look again
        }
        return false;
    }

    // ---- the view -----------------------------------------------------------------------------------------------------

    /** Nothing turns or switches under a screen, a scene, the binoculars, sleep, F1 or as a spectator. */
    static boolean paused(Minecraft mc) {
        LocalPlayer p = mc.player;
        return p == null || mc.gui.screen() != null || mc.gui.hud.isHidden() || InputLock.held() || p.isSleeping() || p.isSpectator()
                || CameraHooks.zoom() > 1.05;
    }

    /** In the air on your own way (flight, a glide, the rope, a long fall), on a wall or a ledge: your look steers you. */
    private static boolean steering(LocalPlayer p) {
        return p.isFallFlying() || p.getAbilities().flying || MovementHooks.hanging(p) || MovementHooks.wallRunning(p) || airTicks > 16;
    }

    private static boolean drawingBow(LocalPlayer p) {
        return p.isUsingItem() && (p.getUseItem().getItem() instanceof BowItem || p.getUseItem().getItem() instanceof CrossbowItem);
    }

    private static AimConfig.Camera camera() {
        AimConfig.Camera c = AimConfig.camera();
        return c == AimConfig.Camera.FOLLOW && FirstPerson.reducedMotion.getAsBoolean() ? AimConfig.Camera.SNAP : c;
    }

    /** Whether the view turns toward the lock this frame. */
    private static boolean turning(Minecraft mc, LocalPlayer p) {
        if (locked < 0 || paused(mc) || steering(p)) return false;
        AimConfig.Camera c = camera();
        return c == AimConfig.Camera.FOLLOW || (c == AimConfig.Camera.SNAP && snapping);
    }

    /** Each frame, before the world is drawn: the view eases toward the locked creature's body. */
    static void frame(Minecraft mc, float partial) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !turning(mc, p)) return;
        if (!(mc.level.getEntity(locked) instanceof LivingEntity le)) return;
        Vec3 eye = p.getEyePosition(partial);
        double tx = Mth.lerp(partial, le.xo, le.getX()), tz = Mth.lerp(partial, le.zo, le.getZ());
        double ty = Mth.lerp(partial, le.yo, le.getY()) + le.getBbHeight() * 0.58;
        double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
        float wantYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float wantPitch = Mth.clamp((float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -80F, 80F);
        float viewYaw = p.getYRot(partial), viewPitch = p.getXRot(partial);
        float gapYaw = Mth.wrapDegrees(wantYaw - viewYaw), gapPitch = wantPitch - viewPitch;
        // drawing a bow, the lock loosens to a gentle pull: the drop and the lead are yours
        float k = drawingBow(p) ? 0.3F : 1F;
        float stepYaw = AimRules.turnStep(gapYaw, dt * k), stepPitch = AimRules.turnStep(gapPitch, dt * k) * 0.85F;
        if (camera() == AimConfig.Camera.SNAP && Math.abs(gapYaw) < 2F && Math.abs(gapPitch) < 2F) snapping = false;
        if (stepYaw == 0F && stepPitch == 0F) return;
        // as Entity.turn does: the old rotation moves with the new, so the frame's blend never jumps
        p.setYRot(p.getYRot() + stepYaw);
        p.yRotO += stepYaw;
        p.setXRot(Mth.clamp(p.getXRot() + stepPitch, -90F, 90F));
        p.xRotO = Mth.clamp(p.xRotO + stepPitch, -90F, 90F);
    }

    /** The mouse while locked: it keeps a quarter of its turn, and a quick flick moves the lock. */
    static void turn(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || locked < 0 || paused(mc)) {
            flickX = flickY = 0;
            return;
        }
        long now = System.nanoTime();
        float ms = lastFlick == 0 ? 16F : Math.min(200F, (now - lastFlick) / 1.0E6F);
        lastFlick = now;
        float mx = (float) mc.mouseHandler.getXVelocity(), my = (float) mc.mouseHandler.getYVelocity();
        flickX = AimRules.flickSum(flickX, mx, ms);
        flickY = AimRules.flickSum(flickY, my, ms);
        if (AimConfig.flick() && now >= switchGapUntil && Math.sqrt(flickX * flickX + flickY * flickY) >= AimRules.FLICK_PX) {
            switchToward(flickX, flickY);
            flickX = flickY = 0;
            switchGapUntil = now + AimRules.SWITCH_GAP_MS * 1_000_000L;
        }
        if (turning(mc, p)) {
            float keep = drawingBow(p) ? 0.8F : AimRules.MOUSE_KEEP;
            e.setMouseSensitivity(AimRules.keepSensitivity(e.getMouseSensitivity(), keep));
        }
    }

    private static long lastFlick;

    // ---- what others read ---------------------------------------------------------------------------------------------

    /** The creature a press means: the lock, else what the crosshair is on, else the mark. */
    static int target() {
        return locked >= 0 ? locked : marked;
    }

    /** A cast's look, bent toward the locked or marked creature's body by at most {@link AimRules#BEND_POWER}. */
    static void bend(float yaw, float pitch, float[] out) {
        Minecraft mc = Minecraft.getInstance();
        int id = target();
        if (mc.player == null || mc.level == null || id < 0 || !AimConfig.enabled() || !(mc.level.getEntity(id) instanceof LivingEntity le)) {
            out[0] = yaw;
            out[1] = pitch;
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 c = new Vec3(le.getX(), le.getY() + le.getBbHeight() * 0.58, le.getZ());
        AimRules.bend(yaw, pitch, yawTo(eye, c), pitchTo(eye, c), AimRules.BEND_POWER, out);
    }

    static float yawTo(Vec3 eye, Vec3 c) {
        return (float) (Mth.atan2(c.z - eye.z, c.x - eye.x) * Mth.RAD_TO_DEG) - 90F;
    }

    static float pitchTo(Vec3 eye, Vec3 c) {
        double dx = c.x - eye.x, dz = c.z - eye.z;
        return (float) (-Mth.atan2(c.y - eye.y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
    }

    private static void play(SoundEvent s, float pitch, float volume) {
        if (!AimConfig.sounds()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }
}
