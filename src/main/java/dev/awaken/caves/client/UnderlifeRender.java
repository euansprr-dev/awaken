package dev.awaken.caves.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws the underlife (SPEC §42.5) from its own sheet (textures/entity/caves/underlife.png, tools/art/specs/deep_life.py)
 * as small bodies, the way the wild's insects are drawn: a moth's wings hinged on its body and beating; a bat's leathery
 * wings, or the bat hanging wrapped in them; beetles, millipedes and fish flat on the face they walk or the water they
 * swim under, turning as they go; a cricket side-on. Lit by the light where each one is and by the light in your hand.
 * Camera-relative, one callback, nothing allocated per frame; about 40 bodies of 2 to 4 quads.
 */
final class UnderlifeRender {
    private UnderlifeRender() {}

    static final Identifier ATLAS = Awaken.id("textures/entity/caves/underlife.png");
    private static final float T = 1F / 64F;
    private static RenderType sheet;

    private static double cx, cy, cz;
    private static float pt, age;
    private static VertexConsumer vc;
    private static final double[] UV = new double[6];
    private static final Direction[] DIRS = Direction.values();

    static void submit(SubmitCustomGeometryEvent e) {
        if (Underlife.count() == 0) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        age = Underlife.now + pt;
        if (sheet == null) sheet = RenderTypes.entityTranslucent(ATLAS);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), sheet, BODIES);
    }

    private static final SubmitNodeCollector.CustomGeometryRenderer BODIES = (pose, consumer) -> {
        try {
            vc = consumer;
            for (int i = 0; i < Underlife.CAP; i++) {
                int k = Underlife.kind[i];
                if (k < 0) continue;
                float a = Mth.lerp(pt, Underlife.alphaO[i], Underlife.alpha[i]);
                if (a <= 0.01F) continue;
                double x = Mth.lerp(pt, Underlife.xo[i], Underlife.x[i]), y = Mth.lerp(pt, Underlife.yo[i], Underlife.y[i]),
                        z = Mth.lerp(pt, Underlife.zo[i], Underlife.z[i]);
                double dx = x - cx, dy = y - cy, dz = z - cz;
                if (dx * dx + dy * dy + dz * dz > 32 * 32 || !Underlife.inView(x, y, z, -0.2F) && dx * dx + dz * dz > 4) continue;
                float yawDeg = Mth.rotLerp(pt, Underlife.yawO[i], Underlife.yaw[i]);
                float yaw = yawDeg * Mth.DEG_TO_RAD, ph = Underlife.phase[i], rt = Underlife.rate[i];
                int light = Underlife.light[i];
                int col = (int) (a * 255) << 24 | 0xFFFFFF;
                int st = Underlife.state[i];
                switch (k) {
                    case Underlife.MOTH -> {
                        float flap = 20F + 45F * Mth.sin(age * 3.1F * rt + ph);
                        wings(dx, dy, dz, yaw, flap, 0.2F, 0.2F, 0, 0, 16, 16, col, light);
                        body(dx, dy, dz, yaw, 0.17F, 0.045F, 48, 0, 53, 14, col, light);
                    }
                    case Underlife.BAT -> {
                        if (st == Underlife.REST) {
                            hang(dx, dy, dz, yaw, ph, col, light);
                        } else {
                            // a bat's beat is deep and quick; dropping off the roof the wings are already opening
                            float flap = st == Underlife.HOP ? 70F : 5F + 60F * Mth.sin(age * 2.3F * rt + ph);
                            wings(dx, dy, dz, yaw, flap, 0.15F, 0.3F, 16, 48, 32, 64, col, light);
                            body(dx, dy, dz, yaw, 0.2F, 0.1F, 32, 48, 48, 64, col, light);
                        }
                    }
                    case Underlife.BEETLE, Underlife.MILLIPEDE -> {
                        boolean beetle = k == Underlife.BEETLE;
                        // a little sway as the legs go; the Ochre Deep's rust beetle is the cell left of the plain one
                        float walk = st == Underlife.MOVE ? Mth.sin(age * (beetle ? 1.4F : 0.6F) + ph) * (beetle ? 5F : 9F) : 0F;
                        int u0 = !beetle ? 0 : Underlife.variant[i] == Underlife.RUST ? 32 : 48;
                        onFace(dx, dy, dz, Underlife.held[i] ? Direction.UP : DIRS[Underlife.face[i]], yawDeg + walk, beetle ? 0.2F : 0.34F,
                                u0, beetle ? 16 : 32, u0 + 16, beetle ? 32 : 48, col, light);
                    }
                    case Underlife.GLOWWORM -> threads(dx, dy, dz, yaw, ph, col, light);
                    case Underlife.FISH -> {
                        float wag = Mth.sin(age * (st == Underlife.HOP ? 1.6F : 0.45F) + ph) * (st == Underlife.HOP ? 14F : 8F);
                        flat(dx, dy, dz, yaw + wag * Mth.DEG_TO_RAD, 0.36F, 0.36F, 32, 32, 48, 48, col, light);
                    }
                    case Underlife.CRICKET -> side(dx, dy, dz, yaw, 0.26F, 0.26F, 48, 32, 64, 48, col, light);
                    default -> { }
                }
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[caves] underlife failed", ex);
            Underlife.clear();
        }
    };

    // ---- shapes -------------------------------------------------------------------------------------------------------

    private static void v(float x, float y, float z, int col, float u, float vv, int light) {
        vc.addVertex(x, y, z).setColor(col).setUv(u * T, vv * T).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0F, 1F, 0F);
    }

    /** Two wings from a cell (its left edge the hinge, its top the front), opened {@code flap} degrees up. */
    private static void wings(double dx, double dy, double dz, float yaw, float flap, float len, float span,
                              int u0, int v0, int u1, int v1, int col, int light) {
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = Mth.cos(yaw), rz = Mth.sin(yaw);
        float c = Mth.cos(flap * Mth.DEG_TO_RAD), s = Mth.sin(flap * Mth.DEG_TO_RAD);
        float x = (float) dx, y = (float) dy, z = (float) dz;
        float hx = fx * len * 0.5F, hz = fz * len * 0.5F;
        for (int side = -1; side <= 1; side += 2) {
            float wx = rx * c * side * span, wy = s * span, wz = rz * c * side * span;
            v(x + hx, y, z + hz, col, u0, v0, light);
            v(x - hx, y, z - hz, col, u0, v1, light);
            v(x - hx + wx, y + wy, z - hz + wz, col, u1, v1, light);
            v(x + hx + wx, y + wy, z + hz + wz, col, u1, v0, light);
        }
    }

    /** A body along its heading: a flat strip and an upright one, crossed. */
    private static void body(double dx, double dy, double dz, float yaw, float len, float wid, int u0, int v0, int u1, int v1, int col, int light) {
        float fx = -Mth.sin(yaw) * len * 0.5F, fz = Mth.cos(yaw) * len * 0.5F, rx = Mth.cos(yaw) * wid * 0.5F, rz = Mth.sin(yaw) * wid * 0.5F;
        float x = (float) dx, y = (float) dy, z = (float) dz, h = wid * 0.5F;
        v(x + fx - rx, y, z + fz - rz, col, u0, v0, light);
        v(x - fx - rx, y, z - fz - rz, col, u0, v1, light);
        v(x - fx + rx, y, z - fz + rz, col, u1, v1, light);
        v(x + fx + rx, y, z + fz + rz, col, u1, v0, light);
        v(x + fx, y - h, z + fz, col, u0, v0, light);
        v(x - fx, y - h, z - fz, col, u0, v1, light);
        v(x - fx, y + h, z - fz, col, u1, v1, light);
        v(x + fx, y + h, z + fz, col, u1, v0, light);
    }

    /** Lying flat, heading {@code yaw}: a fish under the water. */
    private static void flat(double dx, double dy, double dz, float yaw, float len, float wid, int u0, int v0, int u1, int v1, int col, int light) {
        float fx = -Mth.sin(yaw) * len * 0.5F, fz = Mth.cos(yaw) * len * 0.5F, rx = Mth.cos(yaw) * wid * 0.5F, rz = Mth.sin(yaw) * wid * 0.5F;
        float x = (float) dx, y = (float) dy, z = (float) dz;
        v(x + fx - rx, y, z + fz - rz, col, u0, v0, light);
        v(x - fx - rx, y, z - fz - rz, col, u0, v1, light);
        v(x - fx + rx, y, z - fz + rz, col, u1, v1, light);
        v(x + fx + rx, y, z + fz + rz, col, u1, v0, light);
    }

    /** Flat on a face of rock (floor, wall or roof), a hair off it, heading {@code yawDeg} within the face. */
    private static void onFace(double dx, double dy, double dz, Direction out, float yawDeg, float size, int u0, int v0, int u1, int v1,
                               int col, int light) {
        Underlife.axes(out, UV);
        float a = yawDeg * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a), h = size * 0.5F;
        float fx = (float) (UV[0] * c + UV[3] * s) * h, fy = (float) (UV[1] * c + UV[4] * s) * h, fz = (float) (UV[2] * c + UV[5] * s) * h;
        float sx = (float) (-UV[0] * s + UV[3] * c) * h, sy = (float) (-UV[1] * s + UV[4] * c) * h, sz = (float) (-UV[2] * s + UV[5] * c) * h;
        float x = (float) dx + out.getStepX() * 0.012F, y = (float) dy + out.getStepY() * 0.012F, z = (float) dz + out.getStepZ() * 0.012F;
        v(x + fx - sx, y + fy - sy, z + fz - sz, col, u0, v0, light);
        v(x - fx - sx, y - fy - sy, z - fz - sz, col, u0, v1, light);
        v(x - fx + sx, y - fy + sy, z - fz + sz, col, u1, v1, light);
        v(x + fx + sx, y + fy + sy, z + fz + sz, col, u1, v0, light);
    }

    /** A bat hanging from the roof, wrapped in its wings: two crossed quads from its feet down, swaying a little. */
    private static void hang(double dx, double dy, double dz, float yaw, float ph, int col, int light) {
        hanging(dx, dy, dz, yaw, Mth.sin(age * 0.05F + ph) * 0.03F, 0.38F, 0.19F, 0, 48, 16, 64, col, light);
    }

    /** Glow-worm threads: a curtain of silk beaded with glowing glue, hanging from the roof, stirring slowly. */
    private static void threads(double dx, double dy, double dz, float yaw, float ph, int col, int light) {
        hanging(dx, dy, dz, yaw, Mth.sin(age * 0.021F + ph) * 0.05F, 0.85F, 0.42F, 0, 16, 16, 32, col, light);
    }

    /** Two crossed quads hung from a point, {@code h} down and {@code w} either side, their lower edge swayed. */
    private static void hanging(double dx, double dy, double dz, float yaw, float sway, float h, float w, int u0, int v0, int u1, int v1,
                                int col, int light) {
        float x = (float) dx, y = (float) dy, z = (float) dz;
        for (int q = 0; q < 2; q++) {
            float a = yaw + q * Mth.HALF_PI, rx = Mth.cos(a) * w, rz = Mth.sin(a) * w;
            float bx = -Mth.sin(a) * sway, bz = Mth.cos(a) * sway;
            v(x - rx, y, z - rz, col, u0, v0, light);
            v(x - rx + bx, y - h, z - rz + bz, col, u0, v1, light);
            v(x + rx + bx, y - h, z + rz + bz, col, u1, v1, light);
            v(x + rx, y, z + rz, col, u1, v0, light);
        }
    }

    /** Side-on, standing on the ground, its head (the cell's left) the way it faces. */
    private static void side(double dx, double dy, double dz, float yaw, float len, float tall, int u0, int v0, int u1, int v1, int col, int light) {
        float fx = -Mth.sin(yaw) * len * 0.5F, fz = Mth.cos(yaw) * len * 0.5F;
        float x = (float) dx, y = (float) dy, z = (float) dz;
        v(x + fx, y + tall, z + fz, col, u0, v0, light);
        v(x + fx, y, z + fz, col, u0, v1, light);
        v(x - fx, y, z - fz, col, u1, v1, light);
        v(x - fx, y + tall, z - fz, col, u1, v0, light);
    }
}
