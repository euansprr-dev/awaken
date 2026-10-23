package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;

/**
 * Architect assembly: each construct block's twelve edges light up as glowing seams the moment the build sweep
 * reaches it, its faces flash once, then the seams cool to a faint outline and fade. Shape fields: x/y/z = where the
 * sweep starts (the caster), c = sweep speed in blocks per tick, c0 = seam colour, c1 = flash colour.
 */
final class FxSeams {
    private FxSeams() {}

    private static final float W = 0.035f;

    static void draw(VertexConsumer vc, Shape sh, float fade) {
        float now = sh.age + FxRender.pt;
        double cx = FxRender.camX(), cy = FxRender.camY(), cz = FxRender.camZ();
        float speed = sh.c <= 0 ? 1.5f : sh.c;
        for (int i = 0; i < sh.blockCount; i++) {
            long packed = sh.blocks[i];
            int bx = BlockPos.getX(packed), by = BlockPos.getY(packed), bz = BlockPos.getZ(packed);
            double ddx = bx + 0.5 - sh.x, ddy = by + 0.5 - sh.y, ddz = bz + 0.5 - sh.z;
            float at = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / speed;
            float since = now - at;
            if (since < 0) continue;
            // bright as it assembles, cooling to a faint outline
            float glow = since < 2 ? since / 2 : Math.max(0.22f, 1 - (since - 2) / 10);
            float a = glow * fade;
            if (a <= 0.02f) continue;
            int col = FxWorld.alpha(sh.c0, a);
            float x0 = (float) (bx - cx), y0 = (float) (by - cy), z0 = (float) (bz - cz);
            float x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;
            float w = W * (since < 3 ? 1.6f : 1f);
            // bottom square, top square, uprights
            edge(vc, x0, y0, z0, x1, y0, z0, w, col);
            edge(vc, x1, y0, z0, x1, y0, z1, w, col);
            edge(vc, x1, y0, z1, x0, y0, z1, w, col);
            edge(vc, x0, y0, z1, x0, y0, z0, w, col);
            edge(vc, x0, y1, z0, x1, y1, z0, w, col);
            edge(vc, x1, y1, z0, x1, y1, z1, w, col);
            edge(vc, x1, y1, z1, x0, y1, z1, w, col);
            edge(vc, x0, y1, z1, x0, y1, z0, w, col);
            edge(vc, x0, y0, z0, x0, y1, z0, w, col);
            edge(vc, x1, y0, z0, x1, y1, z0, w, col);
            edge(vc, x1, y0, z1, x1, y1, z1, w, col);
            edge(vc, x0, y0, z1, x0, y1, z1, w, col);
            if (since < 4) faces(vc, x0, y0, z0, x1, y1, z1, FxWorld.alpha(sh.c1, (1 - since / 4) * 0.35f * fade));
        }
    }

    private static void edge(VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float w, int col) {
        FxRender.segment(vc, ax, ay, az, bx, by, bz, w, w, col, col);
    }

    /** A brief translucent flash of the block's six faces (slightly inflated so it sits over the block). */
    private static void faces(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int col) {
        float e = 0.01f;
        x0 -= e;
        y0 -= e;
        z0 -= e;
        x1 += e;
        y1 += e;
        z1 += e;
        FxRender.quadFacing(vc, x0, y1, z0, col, x0, y1, z1, col, x1, y1, z1, col, x1, y1, z0, col);
        FxRender.quadFacing(vc, x0, y0, z0, col, x1, y0, z0, col, x1, y0, z1, col, x0, y0, z1, col);
        FxRender.quadFacing(vc, x0, y0, z0, col, x0, y1, z0, col, x1, y1, z0, col, x1, y0, z0, col);
        FxRender.quadFacing(vc, x0, y0, z1, col, x1, y0, z1, col, x1, y1, z1, col, x0, y1, z1, col);
        FxRender.quadFacing(vc, x0, y0, z0, col, x0, y0, z1, col, x0, y1, z1, col, x0, y1, z0, col);
        FxRender.quadFacing(vc, x1, y0, z0, col, x1, y1, z0, col, x1, y1, z1, col, x1, y0, z1, col);
    }

    /** Sparks that run along a random edge of a random assembled block (called by the emitter). */
    static void spark(Shape sh) {
        if (sh.blockCount == 0) return;
        long packed = sh.blocks[FxWorld.R.i(sh.blockCount)];
        double bx = BlockPos.getX(packed), by = BlockPos.getY(packed), bz = BlockPos.getZ(packed);
        double ddx = bx + 0.5 - sh.x, ddy = by + 0.5 - sh.y, ddz = bz + 0.5 - sh.z;
        float at = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / (sh.c <= 0 ? 1.5f : sh.c);
        if (sh.age < at || sh.age > at + 8) return;
        int axis = FxWorld.R.i(3);
        double px = bx + (axis == 0 ? 0 : FxWorld.R.i(2)), py = by + (axis == 1 ? 0 : FxWorld.R.i(2)), pz = bz + (axis == 2 ? 0 : FxWorld.R.i(2));
        float sp = 0.18f;
        double vx = axis == 0 ? sp : 0, vy = axis == 1 ? sp : 0, vz = axis == 2 ? sp : 0;
        int i = FxWorld.sprite(dev.awaken.powers.fx.PowerFxParticles.MOTE, px, py, pz, vx, vy, vz, 6, 0.12f, 0.04f, sh.c1, FxWorld.alpha(sh.c0, 0),
                dev.awaken.powers.fx.FxPool.ADD);
        FxWorld.drag(i, 1f);
    }
}
