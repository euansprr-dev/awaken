package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.powers.fx.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Debris chunks: little tumbling cubes wearing the texture of the block they were torn from, thrown by slams and
 * impacts. They bounce once, roll to a stop and shrink away. Fixed pool, parallel arrays.
 */
final class FxDebris {
    private FxDebris() {}

    private static final int CAP = FxBudget.debrisCap(FxBudget.Level.FULL);
    private static final double[] X = new double[CAP], Y = new double[CAP], Z = new double[CAP];
    private static final double[] PX = new double[CAP], PY = new double[CAP], PZ = new double[CAP];
    private static final float[] VX = new float[CAP], VY = new float[CAP], VZ = new float[CAP];
    private static final float[] RA = new float[CAP], RB = new float[CAP], PRA = new float[CAP], PRB = new float[CAP];
    private static final float[] SA = new float[CAP], SB = new float[CAP], SIZE = new float[CAP], FLOOR = new float[CAP];
    private static final float[] U0 = new float[CAP], U1 = new float[CAP], V0 = new float[CAP], V1 = new float[CAP];
    private static final int[] AGE = new int[CAP], LIFE = new int[CAP], LIGHT = new int[CAP], TINT = new int[CAP];
    private static int live;
    private static RenderType type;
    private static final SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, vc) -> {
        try {
            draw(vc);
        } catch (RuntimeException ex) {
            dev.awaken.Awaken.LOG.warn("Power FX debris failed to draw; clearing it", ex);
            live = 0;
        }
    };
    private static double cx, cy, cz;
    private static float pt;

    static boolean any() {
        return live > 0;
    }

    static void clear() {
        live = 0;
    }

    /** Throws a chunk of a block; ignored when the setting allows no debris or the pool is full. */
    static void spawn(BlockState state, double x, double y, double z, double vx, double vy, double vz, float size, int life, float floorY) {
        int cap = FxBudget.debrisCap(FxWorld.level);
        if (live >= cap || state.isAir()) return;
        TextureAtlasSprite s;
        try {
            s = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
        } catch (RuntimeException e) {
            return;
        }
        int i = live++;
        X[i] = PX[i] = x;
        Y[i] = PY[i] = y;
        Z[i] = PZ[i] = z;
        VX[i] = (float) vx;
        VY[i] = (float) vy;
        VZ[i] = (float) vz;
        RA[i] = PRA[i] = FxWorld.R.f(0, 6.28f);
        RB[i] = PRB[i] = FxWorld.R.f(0, 6.28f);
        SA[i] = FxWorld.R.s() * 0.5f;
        SB[i] = FxWorld.R.s() * 0.5f;
        SIZE[i] = size;
        FLOOR[i] = floorY;
        AGE[i] = 0;
        LIFE[i] = life;
        float uo = FxWorld.R.f(0, 3), vo = FxWorld.R.f(0, 3);
        U0[i] = s.getU(uo / 4f);
        U1[i] = s.getU((uo + 1) / 4f);
        V0[i] = s.getV(vo / 4f);
        V1[i] = s.getV((vo + 1) / 4f);
        LIGHT[i] = FxWorld.light(x, y, z);
        TINT[i] = 0xFFFFFFFF;
    }

    static void tick(ClientLevel level) {
        int i = 0;
        while (i < live) {
            if (++AGE[i] >= LIFE[i]) {
                remove(i);
                continue;
            }
            PX[i] = X[i];
            PY[i] = Y[i];
            PZ[i] = Z[i];
            PRA[i] = RA[i];
            PRB[i] = RB[i];
            VY[i] -= 0.045f;
            VX[i] *= 0.98f;
            VY[i] *= 0.98f;
            VZ[i] *= 0.98f;
            X[i] += VX[i];
            Y[i] += VY[i];
            Z[i] += VZ[i];
            float half = SIZE[i] * 0.5f;
            if (Y[i] - half < FLOOR[i]) {
                Y[i] = FLOOR[i] + half;
                if (VY[i] < -0.12f) {
                    VY[i] = -VY[i] * 0.32f;
                } else {
                    VY[i] = 0;
                    SA[i] *= 0.6f;
                    SB[i] *= 0.6f;
                }
                VX[i] *= 0.55f;
                VZ[i] *= 0.55f;
            }
            RA[i] += SA[i];
            RB[i] += SB[i];
            if (AGE[i] % 5 == 0) LIGHT[i] = FxWorld.light(X[i], Y[i] + 0.2, Z[i]);
            i++;
        }
    }

    private static void remove(int i) {
        int l = --live;
        if (i == l) return;
        X[i] = X[l];
        Y[i] = Y[l];
        Z[i] = Z[l];
        PX[i] = PX[l];
        PY[i] = PY[l];
        PZ[i] = PZ[l];
        VX[i] = VX[l];
        VY[i] = VY[l];
        VZ[i] = VZ[l];
        RA[i] = RA[l];
        RB[i] = RB[l];
        PRA[i] = PRA[l];
        PRB[i] = PRB[l];
        SA[i] = SA[l];
        SB[i] = SB[l];
        SIZE[i] = SIZE[l];
        FLOOR[i] = FLOOR[l];
        U0[i] = U0[l];
        U1[i] = U1[l];
        V0[i] = V0[l];
        V1[i] = V1[l];
        AGE[i] = AGE[l];
        LIFE[i] = LIFE[l];
        LIGHT[i] = LIGHT[l];
        TINT[i] = TINT[l];
    }

    static void submit(PoseStack ps, SubmitNodeCollector out, double camX, double camY, double camZ, float partial) {
        if (live == 0) return;
        cx = camX;
        cy = camY;
        cz = camZ;
        pt = partial;
        if (type == null) type = RenderTypes.entityCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        out.submitCustomGeometry(ps, type, DRAW);
    }

    private static void draw(VertexConsumer vc) {
        for (int i = 0; i < live; i++) {
            float life01 = (AGE[i] + pt) / LIFE[i];
            float shrink = life01 > 0.75f ? 1 - (life01 - 0.75f) / 0.25f : 1f;
            float h = SIZE[i] * 0.5f * shrink;
            if (h <= 0.005f) continue;
            float x = (float) (Mth.lerp(pt, PX[i], X[i]) - cx), y = (float) (Mth.lerp(pt, PY[i], Y[i]) - cy), z = (float) (Mth.lerp(pt, PZ[i], Z[i]) - cz);
            float a = Mth.lerp(pt, PRA[i], RA[i]), b = Mth.lerp(pt, PRB[i], RB[i]);
            float ca = Mth.cos(a), sa = Mth.sin(a), cb = Mth.cos(b), sb = Mth.sin(b);
            // rotation = Ry(a) * Rx(b): local axes in world space
            float xx = ca, xy = 0, xz = -sa;
            float yx = sa * sb, yy = cb, yz = ca * sb;
            float zx = sa * cb, zy = -sb, zz = ca * cb;
            cube(vc, i, x, y, z, h, xx, xy, xz, yx, yy, yz, zx, zy, zz);
        }
    }

    private static void cube(VertexConsumer vc, int i, float x, float y, float z, float h, float xx, float xy, float xz,
                             float yx, float yy, float yz, float zx, float zy, float zz) {
        // face = centre +- axis*h, spanned by the other two axes
        face(vc, i, x, y, z, h, yx, yy, yz, zx, zy, zz, xx, xy, xz);
        face(vc, i, x, y, z, h, zx, zy, zz, yx, yy, yz, -xx, -xy, -xz);
        face(vc, i, x, y, z, h, zx, zy, zz, xx, xy, xz, yx, yy, yz);
        face(vc, i, x, y, z, h, xx, xy, xz, zx, zy, zz, -yx, -yy, -yz);
        face(vc, i, x, y, z, h, xx, xy, xz, yx, yy, yz, zx, zy, zz);
        face(vc, i, x, y, z, h, yx, yy, yz, xx, xy, xz, -zx, -zy, -zz);
    }

    /** One face with normal n, spanned by s and t so that s x t = n (counter-clockwise seen from outside). */
    private static void face(VertexConsumer vc, int i, float x, float y, float z, float h, float sx, float sy, float sz,
                             float tx, float ty, float tz, float nx, float ny, float nz) {
        float ox = x + nx * h, oy = y + ny * h, oz = z + nz * h;
        int o = OverlayTexture.NO_OVERLAY, l = LIGHT[i], c = TINT[i];
        vc.addVertex(ox + (-sx - tx) * h, oy + (-sy - ty) * h, oz + (-sz - tz) * h, c, U0[i], V1[i], o, l, nx, ny, nz);
        vc.addVertex(ox + (sx - tx) * h, oy + (sy - ty) * h, oz + (sz - tz) * h, c, U1[i], V1[i], o, l, nx, ny, nz);
        vc.addVertex(ox + (sx + tx) * h, oy + (sy + ty) * h, oz + (sz + tz) * h, c, U1[i], V0[i], o, l, nx, ny, nz);
        vc.addVertex(ox + (-sx + tx) * h, oy + (-sy + ty) * h, oz + (-sz + tz) * h, c, U0[i], V0[i], o, l, nx, ny, nz);
    }
}
