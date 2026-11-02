package dev.awaken.enchant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.enchant.Family;
import dev.awaken.enchant.Marks;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3fc;

/**
 * Enchantments seen at work (SPEC §45.7, §45.10, §45.12), drawn as light in the enchantment's colour: a ward's mark
 * flashing where a blow landed, a bane's mark burning on what it bit, a glint of gold riding an extra drop down, a
 * block lifting out whole, a thread from an orb into the piece it mends, a puff of light under a softened fall, a
 * swimmer's wake, a column rising round you as a page is learned, and an old enchanting table's burst as it turns. At
 * most 48 at once, each a fraction of a second; nothing is allocated per frame.
 */
public final class EnchantFxClient {
    private EnchantFxClient() {}

    private static final int MAX = 48;
    /** Ticks each kind lasts: ward, bane, spoils, learn, flare, silk, mend, puff, wake. */
    private static final int[] LIFE = {18, 16, 34, 44, 30, 20, 14, 20, 24};
    private static final int[] KIND = new int[MAX], ENTITY = new int[MAX], COLOUR = new int[MAX], BORN = new int[MAX], NO = new int[MAX];
    private static final double[] X = new double[MAX], Y = new double[MAX], Z = new double[MAX];
    private static final float[] STRENGTH = new float[MAX], SEED = new float[MAX], DIR = new float[MAX];
    private static int count, ticks;

    static void add(EnchantPayloads.Fx fx) {
        add(fx.kind(), fx.entity(), fx.x(), fx.y(), fx.z(), fx.colour(), fx.strength(), fx.no(), fx.dir());
    }

    private static void add(int kind, int entity, double x, double y, double z, int colour, float strength, int no, float dir) {
        if (kind < 0 || kind >= LIFE.length) return;
        int i = count < MAX ? count++ : oldest();
        KIND[i] = kind;
        ENTITY[i] = entity;
        X[i] = x;
        Y[i] = y;
        Z[i] = z;
        COLOUR[i] = colour & 0xFFFFFF;
        STRENGTH[i] = Mth.clamp(strength, 0.2F, 1F);
        BORN[i] = ticks;
        SEED[i] = (float) ((x * 13.1 + z * 7.7 + ticks * 0.37) % Mth.TWO_PI);
        NO[i] = no;
        DIR[i] = dir;
    }

    private static int oldest() {
        int o = 0;
        for (int i = 1; i < count; i++) if (BORN[i] < BORN[o]) o = i;
        return o;
    }

    static void tick() {
        ticks++;
        for (int i = 0; i < count; i++) {
            if (ticks - BORN[i] > LIFE[KIND[i]]) {
                count--;
                KIND[i] = KIND[count];
                ENTITY[i] = ENTITY[count];
                X[i] = X[count];
                Y[i] = Y[count];
                Z[i] = Z[count];
                COLOUR[i] = COLOUR[count];
                STRENGTH[i] = STRENGTH[count];
                BORN[i] = BORN[count];
                SEED[i] = SEED[count];
                NO[i] = NO[count];
                DIR[i] = DIR[count];
                i--;
            }
        }
        if (ticks % 3 == 0) wakes();
    }

    /** Players near moving through water on Depth Strider boots leave a faint wake (the client's own: nothing is sent). */
    private static void wakes() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        for (Player p : mc.level.players()) {
            if (!p.isInWater() || p.distanceToSqr(eye) > WAKE_SEEN * WAKE_SEEN) continue;
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, sp = dx * dx + dz * dz;
            if (sp < 0.0016 || !striding(p)) continue;
            double y = p.isUnderWater() ? p.getY() + 0.05 : p.getY() + p.getFluidHeight(FluidTags.WATER) + 0.02;
            add(EnchantPayloads.FX_WAKE, -1, p.getX(), y, p.getZ(), Family.CARRYING.colour, (float) Math.min(1, Math.sqrt(sp) * 5),
                    0, (float) Math.atan2(dz, dx));
        }
    }

    private static final double WAKE_SEEN = 32;

    private static boolean striding(Player p) {
        var boots = p.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) return false;
        for (Holder<Enchantment> h : boots.getEnchantments().keySet()) if (h.is(Enchantments.DEPTH_STRIDER)) return true;
        return false;
    }

    static void clear() {
        count = 0;
    }

    private static double cx, cy, cz;
    private static Vector3fc left, up;

    static void submit(SubmitCustomGeometryEvent e) {
        if (count == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        var c = mc.gameRenderer.mainCamera();
        left = c.leftVector();
        up = c.upVector();
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.lightning(), (pose, vc) -> {
            for (int i = 0; i < count; i++) {
                float age = ticks - BORN[i] + pt, k = Mth.clamp(age / LIFE[KIND[i]], 0F, 1F);
                double x = X[i], y = Y[i], z = Z[i], hgt = 1.8;
                Entity en = ENTITY[i] >= 0 ? mc.level.getEntity(ENTITY[i]) : null;
                if (en != null) {
                    Vec3 p = en.getPosition(pt);
                    x = p.x;
                    y = p.y;
                    z = p.z;
                    hgt = en.getBbHeight();
                }
                float s = STRENGTH[i];
                int col = COLOUR[i];
                double wide = en != null ? en.getBbWidth() : 0.6;
                switch (KIND[i]) {
                    case EnchantPayloads.FX_WARD -> {
                        float a = (1 - k) * (0.55F + 0.45F * s);
                        double r = 1.1 - 0.5 * ease(k);
                        if (NO[i] > 0 && !Float.isNaN(DIR[i])) {
                            // the ward's mark where the blow landed, standing between you and it, white-hot, then its
                            // colour; a ripple spreading out of it, and a faint ring round you
                            double ox = Math.cos(DIR[i]), oz = Math.sin(DIR[i]), reach = wide / 2 + 0.3;
                            double px = x + ox * reach, py = y + hgt * 0.55, pz = z + oz * reach;
                            double size = 0.3 + 0.12 * ease(Math.min(1F, k * 2F));
                            int ink = InscribeScene.hot(col, Math.max(0F, 1F - k * 2.5F));
                            mark(pose, vc, NO[i], px, py, pz, -oz, 0, ox, size, 0.075, argb(ink, a));
                            circle(pose, vc, px, py, pz, -oz, 0, ox, 0.22 + 0.55 * ease(k), 0.07, argb(col, a * 0.55F));
                            ring(pose, vc, x, y + hgt * 0.55, z, r, 0.08, argb(col, a * 0.3F));
                        } else {
                            // from nowhere in particular: a ring closing in round you at the chest, a fainter one above
                            ring(pose, vc, x, y + hgt * 0.55, z, r, 0.12, argb(InscribeScene.hot(col, 0.3F), a));
                            ring(pose, vc, x, y + hgt * 0.85, z, r * 0.7, 0.08, argb(col, a * 0.6F));
                        }
                    }
                    case EnchantPayloads.FX_BANE -> {
                        float a = (1 - k) * s;
                        // the bane's mark burning on the side the blow came from (facing you when there's no telling)
                        if (NO[i] > 0) {
                            double d = Float.isNaN(DIR[i]) ? Math.atan2(cz - z, cx - x) : DIR[i];
                            double ox = Math.cos(d), oz = Math.sin(d), reach = wide / 2 + 0.08;
                            int ink = InscribeScene.hot(col, Math.max(0F, 0.7F - k * 1.6F));
                            mark(pose, vc, NO[i], x + ox * reach, y + hgt * 0.6, z + oz * reach, -oz, 0, ox,
                                    0.26 + 0.06 * (1 - k), 0.065, argb(ink, a));
                        }
                        // and three spears of light driving into it
                        for (int j = 0; j < 3; j++) {
                            float ang = SEED[i] + j * Mth.TWO_PI / 3;
                            double far = 1.4 - 1.0 * ease(Math.min(1F, k * 1.6F)), near = Math.max(0.15, far - 0.7);
                            double yy = y + hgt * (0.45 + 0.15 * j);
                            beam(pose, vc, x + Mth.cos(ang) * far, yy + 0.3, z + Mth.sin(ang) * far,
                                    x + Mth.cos(ang) * near, yy, z + Mth.sin(ang) * near, 0.07, argb(InscribeScene.hot(col, 0.2F), a * 0.7F));
                        }
                        star(pose, vc, x, y + hgt * 0.6, z, 0.3 * (1 - k) + 0.08, argb(InscribeScene.hot(col, 0.5F), a * 0.5F));
                    }
                    case EnchantPayloads.FX_SPOILS -> {
                        // a glint of gold riding the drop down, a short trail of sparks behind it, two rising off it
                        float a = (k < 0.12F ? k / 0.12F : 1F - (k - 0.12F) / 0.88F) * s;
                        double iy = y + (en != null ? 0.22 : 0);
                        Vec3 v = en != null ? en.getDeltaMovement() : Vec3.ZERO;
                        star(pose, vc, x, iy, z, 0.16 + 0.07 * Mth.sin(age * 0.9F), argb(InscribeScene.hot(col, 0.5F), a));
                        for (int j = 1; j <= 3; j++)
                            star(pose, vc, x - v.x * j * 2.5, iy - v.y * j * 2.5 + 0.04 * j, z - v.z * j * 2.5, 0.09 - 0.018 * j,
                                    argb(InscribeScene.hot(col, 0.3F), a * (1 - j * 0.25F)));
                        for (int j = 0; j < 2; j++) {
                            float ang = SEED[i] + j * 3.1F, f = (k * 1.6F + j * 0.5F) % 1F;
                            star(pose, vc, x + Mth.cos(ang) * 0.18, iy + f * 0.55, z + Mth.sin(ang) * 0.18, 0.06,
                                    argb(col, a * (1 - f) * 0.8F));
                        }
                    }
                    case EnchantPayloads.FX_LEARN -> {
                        // a column of light round you, rings climbing it
                        float a = Mth.sin(Math.min(1F, k * 1.4F) * Mth.PI) * s;
                        for (int j = 0; j < 4; j++) {
                            float f = (k * 1.5F + j * 0.25F) % 1F;
                            ring(pose, vc, x, y + f * (hgt + 0.6), z, 0.55 + 0.1 * Mth.sin(f * Mth.PI), 0.1, argb(col, a * (1 - f) * 0.9F));
                        }
                        beam(pose, vc, x, y, z, x, y + hgt + 0.8, z, 0.5, argb(col, a * 0.18F));
                    }
                    case EnchantPayloads.FX_FLARE -> {
                        // rays out of the old table, and a ring spreading over it
                        float a = (1 - k) * s;
                        for (int j = 0; j < 10; j++) {
                            float ang = SEED[i] + j * Mth.TWO_PI / 10;
                            double r0 = 0.2 + k * 0.4, r1 = 0.6 + k * 1.6;
                            beam(pose, vc, x + Mth.cos(ang) * r0, y + 0.3, z + Mth.sin(ang) * r0,
                                    x + Mth.cos(ang) * r1, y + 0.3 + (j % 2) * 0.4 * k, z + Mth.sin(ang) * r1, 0.06, argb(col, a * 0.8F));
                        }
                        ring(pose, vc, x, y + 0.05, z, 0.4 + 1.8 * ease(k), 0.18, argb(InscribeScene.hot(col, 0.3F), a));
                    }
                    case EnchantPayloads.FX_SILK -> {
                        // the block's outline lifting out whole and fading, a soft ring spreading where it sat
                        float a = (1 - k) * 0.9F;
                        double h = 0.5 - 0.06 * k, cyb = Y[i] + 0.5 + 0.35 * ease(k);
                        cube(pose, vc, X[i], cyb, Z[i], h, 0.04, argb(InscribeScene.hot(col, 0.5F), a));
                        ring(pose, vc, X[i], Y[i] + 0.02, Z[i], 0.55 + 0.6 * ease(k), 0.14, argb(col, a * 0.6F));
                    }
                    case EnchantPayloads.FX_MEND -> {
                        // a thread of light from where the orb was into the piece it mends, a bead running it
                        double ty = y + hgt * mendHeight(NO[i]), tx = x, tz = z;
                        int slot = NO[i];
                        if (en != null && (slot == EquipmentSlot.MAINHAND.ordinal() || slot == EquipmentSlot.OFFHAND.ordinal())) {
                            float yaw = en.getYRot(pt) * Mth.DEG_TO_RAD;
                            double side = slot == EquipmentSlot.MAINHAND.ordinal() ? 0.35 : -0.35;
                            tx -= Mth.cos(yaw) * side;
                            tz -= Mth.sin(yaw) * side;
                        }
                        float a = (k < 0.75F ? 1F : (1 - k) / 0.25F) * s;
                        thread(pose, vc, X[i], Y[i], Z[i], tx, ty, tz, ease(Math.min(1F, k * 1.5F)), col, a);
                    }
                    case EnchantPayloads.FX_PUFF -> {
                        // a soft ring spreading over the ground where you landed, motes of light lifting off it
                        float a = (1 - k) * (0.5F + 0.5F * s);
                        ring(pose, vc, X[i], Y[i] + 0.05, Z[i], 0.3 + 1.2 * ease(k), 0.16, argb(InscribeScene.hot(col, 0.4F), a * 0.8F));
                        for (int j = 0; j < 6; j++) {
                            float ang = SEED[i] + j * Mth.TWO_PI / 6;
                            double r = 0.3 + 0.8 * ease(k);
                            star(pose, vc, X[i] + Mth.cos(ang) * r, Y[i] + 0.1 + 0.6 * k * (0.6 + 0.2 * (j % 3)), Z[i] + Mth.sin(ang) * r,
                                    0.08, argb(InscribeScene.hot(col, 0.5F), a));
                        }
                    }
                    case EnchantPayloads.FX_WAKE -> {
                        // a faint V of light opening behind the swimmer on the water
                        float a = (1 - k) * 0.45F * s;
                        double bx = -Math.cos(DIR[i]), bz = -Math.sin(DIR[i]), sx = -bz, sz = bx;
                        double back = 0.3 + 1.2 * k, spread = 0.25 + 0.9 * k;
                        double x0 = X[i] + bx * 0.1, z0 = Z[i] + bz * 0.1;
                        flat(pose, vc, x0, z0, X[i] + bx * back + sx * spread, Z[i] + bz * back + sz * spread, Y[i], 0.12, argb(col, a));
                        flat(pose, vc, x0, z0, X[i] + bx * back - sx * spread, Z[i] + bz * back - sz * spread, Y[i], 0.12, argb(col, a));
                    }
                    default -> {}
                }
            }
        });
    }

    /** How high up the body a mended slot's piece sits, as a share of its height. */
    private static double mendHeight(int slot) {
        if (slot == EquipmentSlot.HEAD.ordinal()) return 0.9;
        if (slot == EquipmentSlot.CHEST.ordinal()) return 0.62;
        if (slot == EquipmentSlot.LEGS.ordinal()) return 0.38;
        if (slot == EquipmentSlot.FEET.ordinal()) return 0.08;
        return 0.5;
    }

    /**
     * An enchantment's mark standing in a plane at (x, y, z): its strokes as soft lines, {@code size} blocks from the middle
     * to the edge, the plane's right along (rx, ry, rz) and its up straight up.
     */
    private static void mark(PoseStack.Pose pose, VertexConsumer vc, int no, double x, double y, double z, double rx, double ry, double rz,
                             double size, double w, int argb) {
        int rim = argb & 0xFFFFFF;
        for (float[] st : Marks.of(no)) {
            int n = st.length / 2;
            for (int i = 0; i + 1 < n; i++) {
                double a0 = st[i * 2], b0 = st[i * 2 + 1], a1 = st[i * 2 + 2], b1 = st[i * 2 + 3];
                double da = a1 - a0, db = b1 - b0, l = Math.sqrt(da * da + db * db);
                if (l < 1e-6) continue;
                double pa = -db / l * w / 2, pb = da / l * w / 2;
                double x0 = x + rx * a0 * size, y0 = y + ry * a0 * size + b0 * size, z0 = z + rz * a0 * size;
                double x1 = x + rx * a1 * size, y1 = y + ry * a1 * size + b1 * size, z1 = z + rz * a1 * size;
                double sx = rx * pa, sy = ry * pa + pb, sz = rz * pa;
                quad(pose, vc, x0, y0, z0, x1, y1, z1, x1 + sx, y1 + sy, z1 + sz, x0 + sx, y0 + sy, z0 + sz, argb, rim);
                quad(pose, vc, x0, y0, z0, x1, y1, z1, x1 - sx, y1 - sy, z1 - sz, x0 - sx, y0 - sy, z0 - sz, argb, rim);
            }
        }
    }

    /** A ring of light standing in the same kind of plane as {@link #mark}. */
    private static void circle(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, double rx, double ry, double rz,
                               double r, double w, int argb) {
        int rim = argb & 0xFFFFFF;
        for (int i = 0; i < 24; i++) {
            float a0 = i * Mth.TWO_PI / 24, a1 = (i + 1) * Mth.TWO_PI / 24;
            double c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int side = -1; side <= 1; side += 2) {
                double ro = r + side * w;
                quad(pose, vc, x + rx * c0 * r, y + ry * c0 * r + s0 * r, z + rz * c0 * r, x + rx * c1 * r, y + ry * c1 * r + s1 * r, z + rz * c1 * r,
                        x + rx * c1 * ro, y + ry * c1 * ro + s1 * ro, z + rz * c1 * ro, x + rx * c0 * ro, y + ry * c0 * ro + s0 * ro, z + rz * c0 * ro, argb, rim);
            }
        }
    }

    /** A cube's twelve edges as soft lines, centred at (x, y, z), {@code h} from the middle to each face. */
    private static void cube(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, double h, double w, int argb) {
        for (int e = 0; e < 4; e++) {
            double sx = (e & 1) == 0 ? -h : h, sz = (e & 2) == 0 ? -h : h;
            beam(pose, vc, x + sx, y - h, z + sz, x + sx, y + h, z + sz, w, argb);
            beam(pose, vc, x - h, y + (e < 2 ? -h : h), z + ((e & 1) == 0 ? -h : h), x + h, y + (e < 2 ? -h : h), z + ((e & 1) == 0 ? -h : h), w, argb);
            beam(pose, vc, x + ((e & 1) == 0 ? -h : h), y + (e < 2 ? -h : h), z - h, x + ((e & 1) == 0 ? -h : h), y + (e < 2 ? -h : h), z + h, w, argb);
        }
    }

    /** A thread arching from one point to another, lit up to {@code t} (0..1) with a bead there and fading behind it. */
    private static void thread(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x2, double y2, double z2,
                               float t, int col, float a) {
        double dx = x2 - x0, dz = z2 - z0;
        double x1 = (x0 + x2) / 2, y1 = Math.max(y0, y2) + 0.4 + 0.15 * Math.sqrt(dx * dx + dz * dz), z1 = (z0 + z2) / 2;
        double px = x0, py = y0, pz = z0;
        for (int i = 1; i <= 12; i++) {
            float u = i / 12F;
            if (u > t + 1e-4F) break;
            float m = 1 - u;
            double qx = m * m * x0 + 2 * m * u * x1 + u * u * x2, qy = m * m * y0 + 2 * m * u * y1 + u * u * y2, qz = m * m * z0 + 2 * m * u * z1 + u * u * z2;
            float lit = Mth.clamp(1F - (t - u) * 2.2F, 0F, 1F);
            if (lit > 0) beam(pose, vc, px, py, pz, qx, qy, qz, 0.05, argb(InscribeScene.hot(col, 0.3F), a * lit));
            px = qx;
            py = qy;
            pz = qz;
        }
        star(pose, vc, px, py, pz, 0.11 + (t >= 1F ? 0.08 : 0), argb(InscribeScene.hot(col, 0.6F), a));
    }

    /** A soft line lying flat at height y. */
    private static void flat(PoseStack.Pose pose, VertexConsumer vc, double x0, double z0, double x1, double z1, double y, double w, int argb) {
        double dx = x1 - x0, dz = z1 - z0, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-6) return;
        double nx = -dz / l * w / 2, nz = dx / l * w / 2;
        int rim = argb & 0xFFFFFF;
        quad(pose, vc, x0, y, z0, x1, y, z1, x1 + nx, y, z1 + nz, x0 + nx, y, z0 + nz, argb, rim);
        quad(pose, vc, x0, y, z0, x1, y, z1, x1 - nx, y, z1 - nz, x0 - nx, y, z0 - nz, argb, rim);
    }

    /** A horizontal ring of light: bright on its line, fading to nothing either side. */
    private static void ring(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, double r, double w, int argb) {
        int rim = argb & 0xFFFFFF;
        for (int i = 0; i < 24; i++) {
            float a0 = i * Mth.TWO_PI / 24, a1 = (i + 1) * Mth.TWO_PI / 24;
            double c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(pose, vc, x + c0 * r, y, z + s0 * r, x + c1 * r, y, z + s1 * r, x + c1 * (r + w), y, z + s1 * (r + w), x + c0 * (r + w), y, z + s0 * (r + w), argb, rim);
            quad(pose, vc, x + c0 * r, y, z + s0 * r, x + c1 * r, y, z + s1 * r, x + c1 * (r - w), y, z + s1 * (r - w), x + c0 * (r - w), y, z + s0 * (r - w), argb, rim);
            quad(pose, vc, x + c0 * r, y, z + s0 * r, x + c1 * r, y, z + s1 * r, x + c1 * r, y + w, z + s1 * r, x + c0 * r, y + w, z + s0 * r, argb, rim);
        }
    }

    /** A soft line of light from one point to another, turned toward the eye. */
    private static void beam(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, double w, int argb) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double ex = (x0 + x1) / 2 - cx, ey = (y0 + y1) / 2 - cy, ez = (z0 + z1) / 2 - cz;
        // side = along × toward the eye
        double sx = dy * ez - dz * ey, sy = dz * ex - dx * ez, sz = dx * ey - dy * ex, l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6) return;
        sx *= w / 2 / l;
        sy *= w / 2 / l;
        sz *= w / 2 / l;
        int rim = argb & 0xFFFFFF;
        quad(pose, vc, x0, y0, z0, x1, y1, z1, x1 + sx, y1 + sy, z1 + sz, x0 + sx, y0 + sy, z0 + sz, argb, rim);
        quad(pose, vc, x0, y0, z0, x1, y1, z1, x1 - sx, y1 - sy, z1 - sz, x0 - sx, y0 - sy, z0 - sz, argb, rim);
    }

    /** A four-pointed glint facing the eye. */
    private static void star(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, double r, int argb) {
        int rim = argb & 0xFFFFFF;
        double lx = left.x() * r, ly = left.y() * r, lz = left.z() * r, ux = up.x() * r, uy = up.y() * r, uz = up.z() * r;
        double t = 0.18;
        quad(pose, vc, x, y, z, x, y, z, x + lx + ux * t, y + ly + uy * t, z + lz + uz * t, x + ux + lx * t, y + uy + ly * t, z + uz + lz * t, argb, rim);
        quad(pose, vc, x, y, z, x, y, z, x + ux - lx * t, y + uy - ly * t, z + uz - lz * t, x - lx + ux * t, y - ly + uy * t, z - lz + uz * t, argb, rim);
        quad(pose, vc, x, y, z, x, y, z, x - lx - ux * t, y - ly - uy * t, z - lz - uz * t, x - ux - lx * t, y - uy - ly * t, z - uz - lz * t, argb, rim);
        quad(pose, vc, x, y, z, x, y, z, x - ux + lx * t, y - uy + ly * t, z - uz + lz * t, x + lx - ux * t, y + ly - uy * t, z + lz - uz * t, argb, rim);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        vc.addVertex(pose, (float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).setColor(inner);
        vc.addVertex(pose, (float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).setColor(inner);
        vc.addVertex(pose, (float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).setColor(outer);
        vc.addVertex(pose, (float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz)).setColor(outer);
    }

    private static int argb(int rgb, float a) {
        return InscribeScene.argb(rgb, a);
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }
}
