package dev.awaken.opening.client;

import dev.awaken.anims.Ease;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.holo.band.BandModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Taking the band (SPEC §16.4, "The Registry ceremony"): Bram holds the Holo band out over the counter; you reach, your
 * hand closes on it (the band now in your fist, his hand empty), and you bring it back up into view, turning it once to
 * look at it, before it settles where your hand holds things (the server has put it in your selected slot by then, so
 * vanilla's hand takes over holding it). The view turns a little to him while the hand is out. Your feet never move.
 * Ilse's coal at the Wayfarers' fire is handed over the same way (SPEC §27.6, through {@link Cinema}).
 */
final class HandOver {
    private HandOver() {}

    static final int REACH = 9, GRASP = 3, BRING = 12, LOOK = 16, SETTLE = 8;
    private static final int TOTAL = REACH + GRASP + BRING + LOOK + SETTLE;

    private static boolean active, told;
    private static int age;
    private static @Nullable Entity giver;
    private static Vec3 from = Vec3.ZERO;
    private static @Nullable ItemStack item;
    private static @Nullable Runnable took;

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ViewportEvent.ComputeCameraAngles.class, HandOver::onAngles);
        FirstPerson.world("opening_take", 250, HandOver::pose);
    }

    static boolean active() {
        return active;
    }

    /** Whoever near you holds a Holo band out (Bram behind the desk), or null. */
    static @Nullable LivingEntity holder(double within) {
        return holder(within, BandModule::isBand);
    }

    /** Whoever near you holds out what {@code what} matches (Bram with the band, then the letters), or null. */
    static @Nullable LivingEntity holder(double within, java.util.function.Predicate<ItemStack> what) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        LivingEntity best = null;
        double bestD = within * within;
        for (Entity e : p.level().getEntities(p, new AABB(p.position(), p.position()).inflate(within))) {
            if (!(e instanceof LivingEntity le) || !what.test(le.getMainHandItem())) continue;
            double d = le.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    static void begin(LivingEntity from) {
        begin(from, new ItemStack(BandModule.HOLO_BAND.get()), RegistryScene::tookBand);
    }

    /** Takes {@code what} from {@code from}'s hand; {@code onTook} runs as your hand closes on it. */
    static void begin(LivingEntity from, ItemStack what, Runnable onTook) {
        item = what;
        took = onTook;
        giver = from;
        HandOver.from = hand(from);
        age = 0;
        told = false;
        active = true;
    }

    static void reset() {
        active = false;
        giver = null;
    }

    static void tick() {
        if (!active) return;
        if (giver != null && !giver.isRemoved()) from = hand(giver);
        age++;
        if (age == REACH) {
            OpeningSounds.ui(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.6F, 0.4F);
            if (!told) {
                told = true;
                if (took != null) took.run();
            }
        }
        if (age == REACH + GRASP + BRING) OpeningSounds.ui(dev.awaken.holo.HoloSounds.HOVER.get(), 0.6F, 0.35F);
        if (age >= TOTAL) reset();
    }

    /** Where a citizen's main hand is, roughly: before the chest at the side of the arm, held out a little. */
    private static Vec3 hand(Entity e) {
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r), sx = -fz, sz = fx; // forward and the body's left
        double s = e instanceof LivingEntity le ? le.getScale() : 1;
        return e.position().add(fx * 0.42 * s - sx * 0.3 * s, 1.0 * s, fz * 0.42 * s - sz * 0.3 * s);
    }

    private static float out(float pt) {
        float t = age + pt;
        if (t < REACH + GRASP) return Ease.at(Ease.OUT_CUBIC, t / REACH);
        return 1 - Ease.at(Ease.IN_OUT_SINE, (t - REACH - GRASP) / BRING);
    }

    private static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!active) return;
        float k = out((float) e.getPartialTick()) * 0.5F;
        if (k <= 0) return;
        Vec3 o = e.getCamera().position();
        double ax = from.x - o.x, ay = from.y - o.y, az = from.z - o.z;
        float yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG), pitch = (float) (-Mth.atan2(ay, Math.sqrt(ax * ax + az * az)) * Mth.RAD_TO_DEG);
        e.setYaw(e.getYaw() + Mth.wrapDegrees(yaw - e.getYaw()) * k);
        e.setPitch(Mth.lerp(k, e.getPitch(), pitch));
    }

    private static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null) return false;
        float t = age + pt;
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        Vec3 look = mc.player.getViewVector(pt);
        double fx = look.x, fz = look.z, len = Math.max(1e-6, Math.hypot(fx, fz));
        fx /= len;
        fz /= len;
        double rx = -fz, rz = fx;
        boolean leftMain = mc.player.getMainArm() == HumanoidArm.LEFT;
        double side = leftMain ? -1 : 1;
        rig.fx = fx;
        rig.fz = fz;
        rig.handDistance = 0.75;
        // rest under the frame, out to his hand, back up before your eyes (turning it to look), then down to holding
        double restX = eye.x + fx * 0.4 + rx * side * 0.3, restY = eye.y - 0.9, restZ = eye.z + fz * 0.4 + rz * side * 0.3;
        double showX = eye.x + fx * 0.5 + rx * side * 0.08, showY = eye.y - 0.2, showZ = eye.z + fz * 0.5 + rz * side * 0.08;
        double x, y, z;
        float w = 1;
        if (t < REACH + GRASP) {
            float k = Ease.at(Ease.OUT_CUBIC, Math.min(1, t / REACH));
            x = Mth.lerp(k, restX, from.x);
            y = Mth.lerp(k, restY, from.y);
            z = Mth.lerp(k, restZ, from.z);
            w = Math.min(1, t / 3F);
        } else if (t < REACH + GRASP + BRING) {
            float k = Ease.at(Ease.IN_OUT_SINE, (t - REACH - GRASP) / BRING);
            x = Mth.lerp(k, from.x, showX);
            y = Mth.lerp(k, from.y, showY) + 0.05 * Mth.sin(k * Mth.PI);
            z = Mth.lerp(k, from.z, showZ);
        } else if (t < REACH + GRASP + BRING + LOOK) {
            // a look at it: held up, turned a little this way and that
            float k = (t - REACH - GRASP - BRING) / LOOK;
            x = showX + rx * 0.03 * Mth.sin(k * Mth.TWO_PI);
            y = showY + 0.015 * Mth.sin(k * Mth.PI);
            z = showZ + rz * 0.03 * Mth.sin(k * Mth.TWO_PI);
        } else {
            float k = Ease.at(Ease.IN_OUT_SINE, (t - TOTAL + SETTLE) / SETTLE);
            x = Mth.lerp(k, showX, restX);
            y = Mth.lerp(k, showY, restY);
            z = Mth.lerp(k, showZ, restZ);
            w = 1 - k;
        }
        double sx = eye.x + rx * side * 0.33 - fx * 0.1, sy = eye.y - 0.4, sz = eye.z + rz * side * 0.33 - fz * 0.1;
        rig.reach(leftMain ? FirstPerson.Limb.LEFT_ARM : FirstPerson.Limb.RIGHT_ARM, sx, sy, sz, x, y, z, FirstPerson.Rig.ARM_REACH, 0, 1, 0, w);
        if (t >= REACH && item != null) rig.held[leftMain ? 1 : 0] = item;
        return w > 0.01F;
    }
}
