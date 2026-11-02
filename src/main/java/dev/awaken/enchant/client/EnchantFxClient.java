package dev.awaken.enchant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3fc;

/**
 * Enchantments seen at work (SPEC §45.7, §45.10), drawn as light in the enchantment's colour: a ward's ring closing
 * round you as it takes some of a blow, a bane's spears driving into what it bit, a glint of gold over an extra drop, a
 * column rising round you as a page is learned, and an old enchanting table's burst as it turns. At most 32 at once,
 * each a fraction of a second; nothing is allocated per frame.
 */
public final class EnchantFxClient {
    private EnchantFxClient() {}

    private static final int MAX = 32;
    private static final int[] LIFE = {14, 12, 22, 44, 30};
    private static final int[] KIND = new int[MAX], ENTITY = new int[MAX], COLOUR = new int[MAX], BORN = new int[MAX];
    private static final double[] X = new double[MAX], Y = new double[MAX], Z = new double[MAX];
    private static final float[] STRENGTH = new float[MAX], SEED = new float[MAX];
    private static int count, ticks;

    static void add(EnchantPayloads.Fx fx) {
        if (fx.kind() < 0 || fx.kind() >= LIFE.length) return;
        int i = count < MAX ? count++ : oldest();
        KIND[i] = fx.kind();
        ENTITY[i] = fx.entity();
        X[i] = fx.x();
        Y[i] = fx.y();
        Z[i] = fx.z();
        COLOUR[i] = fx.colour() & 0xFFFFFF;
        STRENGTH[i] = Mth.clamp(fx.strength(), 0.2F, 1F);
        BORN[i] = ticks;
        SEED[i] = (float) ((fx.x() * 13.1 + fx.z() * 7.7 + ticks * 0.37) % Mth.TWO_PI);
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
                i--;
            }
        }
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
                switch (KIND[i]) {
                    case EnchantPayloads.FX_WARD -> {
                        // a ring closing in round you at the chest, and a fainter one above
                        double r = 1.1 - 0.5 * ease(k);
                        float a = (1 - k) * 0.85F * s;
                        ring(pose, vc, x, y + hgt * 0.55, z, r, 0.12, argb(InscribeScene.hot(col, 0.3F), a));
                        ring(pose, vc, x, y + hgt * 0.85, z, r * 0.7, 0.08, argb(col, a * 0.6F));
                    }
                    case EnchantPayloads.FX_BANE -> {
                        // three spears of light driving into it
                        float a = (1 - k) * s;
                        for (int j = 0; j < 3; j++) {
                            float ang = SEED[i] + j * Mth.TWO_PI / 3;
                            double far = 1.4 - 1.0 * ease(Math.min(1F, k * 1.6F)), near = Math.max(0.15, far - 0.7);
                            double yy = y + hgt * (0.45 + 0.15 * j);
                            beam(pose, vc, x + Mth.cos(ang) * far, yy + 0.3, z + Mth.sin(ang) * far,
                                    x + Mth.cos(ang) * near, yy, z + Mth.sin(ang) * near, 0.07, argb(InscribeScene.hot(col, 0.2F), a));
                        }
                        star(pose, vc, x, y + hgt * 0.6, z, 0.35 * (1 - k) + 0.1, argb(InscribeScene.hot(col, 0.5F), a * 0.7F));
                    }
                    case EnchantPayloads.FX_SPOILS -> {
                        // a glint of gold, rising
                        float a = Mth.sin(k * Mth.PI) * s;
                        for (int j = 0; j < 3; j++) {
                            float ang = SEED[i] + j * 2.1F;
                            star(pose, vc, x + Mth.cos(ang) * 0.25, y + 0.3 + k * 0.6 + j * 0.15, z + Mth.sin(ang) * 0.25,
                                    0.12 + 0.06 * Mth.sin(age * 0.8F + j), argb(InscribeScene.hot(col, 0.4F), a));
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
                    default -> {}
                }
            }
        });
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
