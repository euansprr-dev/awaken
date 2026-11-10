package dev.awaken.anims.client.station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.anims.station.StationView;
import dev.awaken.anims.station.StationView.Station;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The fire's answer at a furnace you are working (SPEC §64.6): fuel onto the coals brings a flare and a handful of
 * embers of our own (small bright squares that rise, drift and go out; not the game's particles), and while its screen
 * is open a glow stands over the coal bed as high as the fuel that is left. Drawn for you, in the scene only; the
 * furnace itself is vanilla's.
 */
final class StationFire {
    private StationFire() {}

    private static final int N = 22;
    private static final double[] AT = new double[N * 3], VEL = new double[N * 3];
    private static final long[] BORN = new long[N];
    private static final float[] LIFE = new float[N];
    /** A bubble in a brewing bottle, not an ember: pale, slow, and it only rises. */
    private static final boolean[] BUBBLE = new boolean[N];
    private static int next;
    private static long flareAt, last;
    private static int seed = 0x2F6E2B1;
    private static final long FLARE_NS = 900_000_000L;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static @Nullable Station at;

    private static double rnd() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 8) & 0xFFFF) / 65535.0;
    }

    /** Fuel has landed on the coals. */
    static void flare(@Nullable Station s) {
        if (s == null) return;
        at = s;
        flareAt = System.nanoTime();
        double bx = s.pos().getX() + 0.5 - s.fx() * 0.5 + s.fx() * StationView.FURNACE_DEPTH, bz = s.pos().getZ() + 0.5 - s.fz() * 0.5 + s.fz() * StationView.FURNACE_DEPTH;
        for (int k = 0; k < 14; k++) {
            int i = next++ % N, j = i * 3;
            double across = (rnd() - 0.5) * 0.36;
            AT[j] = bx + s.rx() * across;
            AT[j + 1] = s.pos().getY() + 0.24 + rnd() * 0.06;
            AT[j + 2] = bz + s.rz() * across;
            // up, and a little out of the mouth toward you
            VEL[j] = s.rx() * (rnd() - 0.5) * 0.5 - s.fx() * (0.15 + rnd() * 0.5);
            VEL[j + 1] = 0.5 + rnd() * 0.9;
            VEL[j + 2] = s.rz() * (rnd() - 0.5) * 0.5 - s.fz() * (0.15 + rnd() * 0.5);
            BORN[i] = flareAt;
            BUBBLE[i] = false;
            LIFE[i] = (float) (0.45 + rnd() * 0.5);
        }
    }

    /** Sparks off a blow (an anvil's, a smithing table's, a grindstone's): {@code n} of them from a point, out and up. */
    static void sparks(double x, double y, double z, int n) {
        long now = System.nanoTime();
        for (int k = 0; k < n; k++) {
            int i = next++ % N, j = i * 3;
            double a = rnd() * Math.PI * 2, out = 0.5 + rnd() * 1.1;
            AT[j] = x;
            AT[j + 1] = y;
            AT[j + 2] = z;
            VEL[j] = Math.cos(a) * out;
            VEL[j + 1] = 0.5 + rnd() * 1.0;
            VEL[j + 2] = Math.sin(a) * out;
            BORN[i] = now;
            BUBBLE[i] = false;
            LIFE[i] = (float) (0.22 + rnd() * 0.3);
        }
    }

    /** A bubble of its own in a bottle that is brewing (SPEC §64.8): from a point in the bottle, straight up. */
    static void bubble(double x, double y, double z) {
        int i = next++ % N, j = i * 3;
        AT[j] = x + (rnd() - 0.5) * 0.05;
        AT[j + 1] = y + (rnd() - 0.5) * 0.03;
        AT[j + 2] = z + (rnd() - 0.5) * 0.05;
        VEL[j] = 0;
        VEL[j + 1] = 0.1 + rnd() * 0.08;
        VEL[j + 2] = 0;
        BORN[i] = System.nanoTime();
        BUBBLE[i] = true;
        LIFE[i] = (float) (0.5 + rnd() * 0.4);
    }

    /** How bright the flare is now (0..0.55), for the light on your arm. */
    static float level() {
        if (flareAt == 0) return 0;
        double u = (System.nanoTime() - flareAt) / (double) FLARE_NS;
        return u >= 1 ? 0 : (float) (0.55 * (1 - u) * (1 - u));
    }

    static void submit(SubmitCustomGeometryEvent e, StationScreen<?> screen) {
        Station s = StationClient.station;
        if (s == null) return;
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        boolean any = false;
        for (int i = 0; i < N; i++) {
            if (BORN[i] == 0) continue;
            double age = (now - BORN[i]) / 1e9;
            if (age > LIFE[i]) {
                BORN[i] = 0;
                continue;
            }
            int j = i * 3;
            AT[j] += VEL[j] * dt;
            AT[j + 1] += VEL[j + 1] * dt;
            AT[j + 2] += VEL[j + 2] * dt;
            if (BUBBLE[i]) {
                any = true;
                continue;
            }
            VEL[j + 1] -= (LIFE[i] < 0.53F ? 3.2 : 0.9) * dt; // embers slow as they rise; a blow's sparks fall
            VEL[j] *= 1 - 1.5 * dt;
            VEL[j + 2] *= 1 - 1.5 * dt;
            any = true;
        }
        float lit = screen.getMenu() instanceof AbstractFurnaceMenu f && f.isLit() ? Mth.clamp(f.getLitProgress(), 0F, 1F) : -1F;
        float flare = level();
        if (!any && lit < 0 && flare <= 0) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        CAM.set(e.getLevelRenderState().cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        PoseStack ps = e.getPoseStack();
        float t = (float) ((now % 60_000_000_000L) / 1e9);
        e.getSubmitNodeCollector().submitCustomGeometry(ps, RenderTypes.lightning(), (pose, vc) -> {
            // the glow over the coals: as high as the fuel that is left, a little higher while a flare lasts
            if (lit >= 0 || flare > 0) {
                double depth = StationView.FURNACE_DEPTH + 0.06;
                double bx = s.pos().getX() + 0.5 - s.fx() * 0.5 + s.fx() * depth - cam.x, bz = s.pos().getZ() + 0.5 - s.fz() * 0.5 + s.fz() * depth - cam.z;
                float y0 = (float) (s.pos().getY() + 0.14 - cam.y), h = 0.06F + 0.24F * Math.max(0F, lit) + 0.2F * flare;
                h *= 1F + 0.06F * Mth.sin(t * 9.3F) + 0.04F * Mth.sin(t * 15.1F);
                int a = Math.round(255 * Math.min(1F, 0.5F + flare));
                float x0 = (float) (bx - s.rx() * 0.3), z0 = (float) (bz - s.rz() * 0.3), x1 = (float) (bx + s.rx() * 0.3), z1 = (float) (bz + s.rz() * 0.3);
                vc.addVertex(pose, x0, y0, z0).setColor(255, 150, 40, a);
                vc.addVertex(pose, x1, y0, z1).setColor(255, 150, 40, a);
                vc.addVertex(pose, x1, y0 + h, z1).setColor(255, 90, 20, 0);
                vc.addVertex(pose, x0, y0 + h, z0).setColor(255, 90, 20, 0);
            }
            // the embers: small squares facing you, white-hot to red as they go out
            for (int i = 0; i < N; i++) {
                if (BORN[i] == 0) continue;
                float u = (float) ((now - BORN[i]) / 1e9) / LIFE[i];
                if (u >= 1) continue;
                int j = i * 3;
                float x = (float) (AT[j] - cam.x), y = (float) (AT[j + 1] - cam.y), z = (float) (AT[j + 2] - cam.z), r = 0.012F * (1 - 0.5F * u);
                int red = 255, g = Math.round(230 - 150 * u), b = Math.round(160 * (1 - u) * (1 - u)), a = Math.round(255 * (1 - u * u));
                if (BUBBLE[i]) {
                    // pale, small, fading in and out as it rises
                    r = 0.007F;
                    red = 190; g = 225; b = 255;
                    a = Math.round(150 * Mth.sin(u * Mth.PI));
                }
                vc.addVertex(pose, x - RIGHT.x * r - UP.x * r, y - RIGHT.y * r - UP.y * r, z - RIGHT.z * r - UP.z * r).setColor(red, g, b, a);
                vc.addVertex(pose, x + RIGHT.x * r - UP.x * r, y + RIGHT.y * r - UP.y * r, z + RIGHT.z * r - UP.z * r).setColor(red, g, b, a);
                vc.addVertex(pose, x + RIGHT.x * r + UP.x * r, y + RIGHT.y * r + UP.y * r, z + RIGHT.z * r + UP.z * r).setColor(red, g, b, a);
                vc.addVertex(pose, x - RIGHT.x * r + UP.x * r, y - RIGHT.y * r + UP.y * r, z - RIGHT.z * r + UP.z * r).setColor(red, g, b, a);
            }
        });
    }

    static void reset() {
        java.util.Arrays.fill(BORN, 0L);
        flareAt = 0;
        last = 0;
    }
}
