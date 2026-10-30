package dev.awaken.powers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Where on an entity an effect attaches (feet, casting hand, chest, eyes...), interpolated to the frame, without
 * allocating. A player's hand is where the drawn palm is while a cast shows it ({@link Muzzles}), else a fixed offset.
 */
final class Anchors {
    private Anchors() {}

    static final int FEET = 0;
    static final int HAND = 1;
    static final int BODY = 2;
    static final int EYE = 3;
    static final int OFFHAND = 4;
    static final int HEAD = 5;
    static final int BACK = 6;
    /** The ground under the stamping foot (Muzzles). */
    static final int PLANT = 7;
    /** The sternum: the core (Muzzles). */
    static final int CORE = 8;
    /** Between both palms: the ball a two-handed charge holds (Muzzles). */
    static final int BALL = 9;

    /** Writes the anchor's world position into out[0..2]. */
    static void of(Entity e, int anchor, float pt, double[] out) {
        double x = Mth.lerp(pt, e.xo, e.getX());
        double y = Mth.lerp(pt, e.yo, e.getY());
        double z = Mth.lerp(pt, e.zo, e.getZ());
        switch (anchor) {
            case FEET -> {
                out[0] = x;
                out[1] = y + 0.05;
                out[2] = z;
            }
            case BODY -> {
                out[0] = x;
                out[1] = y + e.getBbHeight() * 0.55;
                out[2] = z;
            }
            case HEAD -> {
                out[0] = x;
                out[1] = y + e.getBbHeight() + 0.25;
                out[2] = z;
            }
            case EYE -> {
                out[0] = x;
                out[1] = y + e.getEyeHeight();
                out[2] = z;
            }
            case HAND, OFFHAND -> hand(e, anchor == OFFHAND, pt, x, y, z, out);
            case PLANT -> Muzzles.plant(e, pt, out);
            case CORE -> Muzzles.core(e, pt, out);
            case BALL -> Muzzles.ball(e, pt, out);
            case BACK -> {
                float yaw = e.getViewYRot(pt) * Mth.DEG_TO_RAD;
                out[0] = x + Mth.sin(yaw) * 0.35;
                out[1] = y + e.getBbHeight() * 0.65;
                out[2] = z - Mth.cos(yaw) * 0.35;
            }
            default -> {
                out[0] = x;
                out[1] = y;
                out[2] = z;
            }
        }
    }

    private static void hand(Entity e, boolean off, float pt, double x, double y, double z, double[] out) {
        if (!(e instanceof Player)) {
            out[0] = x;
            out[1] = y + e.getBbHeight() * 0.55;
            out[2] = z;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        // where the drawn palm is (Muzzles: your fist in first person, the posed model otherwise), else the old offsets below
        if (Muzzles.palm(e, off, pt, firstPerson, out)) return;
        float yaw = e.getViewYRot(pt) * Mth.DEG_TO_RAD;
        float pitch = e.getViewXRot(pt) * Mth.DEG_TO_RAD;
        // look = (-sin(yaw) cos(pitch), -sin(pitch), cos(yaw) cos(pitch)); right = (-cos(yaw), 0, -sin(yaw))
        float cp = Mth.cos(pitch);
        double lx = -Mth.sin(yaw) * cp, ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * cp;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double side = (off ? -1 : 1) * (firstPerson ? 0.32 : 0.36);
        double fwd = firstPerson ? 0.55 : 0.35;
        double down = firstPerson ? 0.3 : 0.45;
        double ey = y + e.getEyeHeight();
        out[0] = x + lx * fwd + rx * side;
        out[1] = ey + ly * fwd - down;
        out[2] = z + lz * fwd + rz * side;
    }
}
