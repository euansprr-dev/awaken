package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * Drawing for the ladder's shapes (SPEC 36.5): strands winding round a beam, sprites in orbit, a rune disc facing the aim,
 * glowing cracks in the ground. Camera-relative and allocation-free like the rest of {@link FxRender}.
 */
final class FxLadderDraw {
    private FxLadderDraw() {}

    /** The most strand pieces a block of beam gets, and per strand. */
    private static final int PER_BLOCK = 7, MAX_PIECES = 72;

    /** Strands winding round the line A -> B (camera-relative), turning with time. */
    static void helix(VertexConsumer vc, Shape sh, float fade, float ax, float ay, float az, float bx, float by, float bz, float tt) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.5f) return;
        float ux = dx / len, uy = dy / len, uz = dz / len;
        // a basis round the beam
        float px, py, pz;
        if (Math.abs(uy) < 0.9f) {
            px = uz;
            py = 0;
            pz = -ux;
        } else {
            px = 0;
            py = -uz;
            pz = uy;
        }
        float pl = Mth.sqrt(px * px + py * py + pz * pz);
        px /= pl;
        py /= pl;
        pz /= pl;
        float qx = uy * pz - uz * py, qy = uz * px - ux * pz, qz = ux * py - uy * px;
        int pieces = Math.min(MAX_PIECES, Math.max(8, (int) (len * PER_BLOCK)));
        float step = len / pieces;
        float w = sh.hWidth;
        int col = sh.hCol;
        float spin = tt * sh.hSpin * Mth.TWO_PI;
        for (int k = 0; k < sh.helix; k++) {
            float off = k * Mth.TWO_PI / sh.helix;
            float lx = 0, ly = 0, lz = 0;
            for (int i = 0; i <= pieces; i++) {
                float s = i * step;
                // the strand opens out from the palm over its first block and narrows into the far end
                float open = Math.min(1f, s / 1.2f) * Math.min(1f, (len - s) / 0.8f + 0.25f);
                float r = sh.hRadius * open;
                float th = off + spin + s * sh.hTurns * Mth.TWO_PI;
                float c = Mth.cos(th) * r, sn = Mth.sin(th) * r;
                float x = ax + ux * s + px * c + qx * sn, y = ay + uy * s + py * c + qy * sn, z = az + uz * s + pz * c + qz * sn;
                if (i > 0) {
                    // a glint runs along each strand as it turns
                    float a = fade * (0.45f + 0.55f * open) * (0.75f + 0.25f * Mth.sin(th * 2 - tt * 0.9f));
                    int cc = FxWorld.alpha(col, Math.min(1f, a) * (((col >>> 24) & 255) / 255f));
                    FxRender.segment(vc, lx, ly, lz, x, y, z, w, w, cc, cc);
                }
                lx = x;
                ly = y;
                lz = z;
            }
        }
    }

    /** Sprites circling the anchor in the plane of the shape's normal. */
    static void orbit(VertexConsumer vc, Shape sh, float fade) {
        TextureAtlasSprite first = FxSprites.get(sh.sprite, 0);
        if (first == null) return;
        int frames = FxSprites.frames(sh.sprite);
        float t = sh.t(FxRender.pt);
        float e = 1 - (1 - t) * (1 - t);
        float r = Mth.lerp(e, sh.a, sh.b);
        float size = sh.d;
        float cx = (float) (sh.rx - FxRender.camX()), cy = (float) (sh.ry - FxRender.camY()), cz = (float) (sh.rz - FxRender.camZ());
        boolean own = FxRender.ownHand(sh);
        if (own) {
            // round your own hand in first person: a small ring held in the hand, never across the view
            r *= 0.35f;
            size *= 0.45f;
        }
        float nx = sh.nx, ny = sh.ny, nz = sh.nz;
        float e1x, e1y, e1z;
        if (Math.abs(ny) > 0.9f) {
            e1x = 1;
            e1y = 0;
            e1z = 0;
        } else {
            e1x = 0;
            e1y = 1;
            e1z = 0;
        }
        float dot = e1x * nx + e1y * ny + e1z * nz;
        e1x -= nx * dot;
        e1y -= ny * dot;
        e1z -= nz * dot;
        float l = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= l;
        e1y /= l;
        e1z /= l;
        float e2x = ny * e1z - nz * e1y, e2y = nz * e1x - nx * e1z, e2z = nx * e1y - ny * e1x;
        int n = Math.max(1, sh.emitParam);
        float base = sh.seed * 0.37f + sh.c * (sh.age + FxRender.pt);
        int mixed = FxWorld.mix(sh.c0, sh.c1, t);
        for (int i = 0; i < n; i++) {
            float th = base + i * Mth.TWO_PI / n;
            float c = Mth.cos(th) * r, s = Mth.sin(th) * r;
            float x = cx + e1x * c + e2x * s, y = cy + e1y * c + e2y * s, z = cz + e1z * c + e2z * s;
            float dist = Mth.sqrt(x * x + y * y + z * z);
            float nf = own ? 0.85f : FxRender.near(dist);
            if (nf <= 0.01f) continue;
            float sz = Math.min(size, (own ? FxRender.HAND_CAP : FxRender.SIZE_CAP) * dist);
            TextureAtlasSprite sp = FxSprites.get(sh.sprite, frames > 1 ? (i + sh.frame) % frames : 0);
            if (sp == null) continue;
            int col = FxWorld.alpha(mixed, fade * nf * (((mixed >>> 24) & 255) / 255f));
            FxRender.billboard(vc, sp, x, y, z, sz, th * 0.5f, FxRender.tint(col), LightCoordsUtil.FULL_BRIGHT);
        }
    }

    /** A rune disc lying in the plane of the shape's normal, turning slowly. */
    static void sigil(VertexConsumer vc, Shape sh, float fade) {
        TextureAtlasSprite s = FxSprites.get(sh.sprite, sh.frame);
        if (s == null) return;
        float g = (sh.flags & Shape.HOLD) != 0 ? Math.min(1, (sh.age + FxRender.pt) / 6f) : sh.t(FxRender.pt);
        float size = Mth.lerp(1 - (1 - g) * (1 - g) * (1 - g), sh.a, sh.b);
        if ((sh.flags & Shape.PULSE) != 0) size *= 1 + 0.06f * Mth.sin((sh.age + FxRender.pt) * 0.9f);
        float x = (float) (sh.rx - FxRender.camX()), y = (float) (sh.ry - FxRender.camY()), z = (float) (sh.rz - FxRender.camZ());
        boolean own = FxRender.ownHand(sh);
        float dist = Mth.sqrt(x * x + y * y + z * z);
        float nf = own ? 0.7f : FxRender.near(dist);
        if (nf <= 0.01f) return;
        if (own) size = Math.min(size, 0.32f * dist);
        // set a little in front of the anchor along the normal, so a palm's disc sits just past the fingers
        float push = sh.d;
        x += sh.nx * push;
        y += sh.ny * push;
        z += sh.nz * push;
        float nx = sh.nx, ny = sh.ny, nz = sh.nz;
        float e1x, e1y, e1z;
        if (Math.abs(ny) > 0.9f) {
            e1x = 1;
            e1y = 0;
            e1z = 0;
        } else {
            e1x = 0;
            e1y = 1;
            e1z = 0;
        }
        float dot = e1x * nx + e1y * ny + e1z * nz;
        e1x -= nx * dot;
        e1y -= ny * dot;
        e1z -= nz * dot;
        float l = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= l;
        e1y /= l;
        e1z /= l;
        float e2x = ny * e1z - nz * e1y, e2y = nz * e1x - nx * e1z, e2z = nx * e1y - ny * e1x;
        float rot = sh.c * (sh.age + FxRender.pt);
        float c = Mth.cos(rot) * size * 0.5f, sn = Mth.sin(rot) * size * 0.5f;
        // the quad's half axes, turned by rot in the plane
        float hx = e1x * c + e2x * sn, hy = e1y * c + e2y * sn, hz = e1z * c + e2z * sn;
        float vx = e2x * c - e1x * sn, vy = e2y * c - e1y * sn, vz = e2z * c - e1z * sn;
        int mixed = FxWorld.mix(sh.c0, sh.c1, sh.t(FxRender.pt));
        int col = FxRender.tint(FxWorld.alpha(mixed, fade * nf * (((mixed >>> 24) & 255) / 255f)));
        FxRender.quadTex(vc, s, x + hx + vx, y + hy + vy, z + hz + vz, x + hx - vx, y + hy - vy, z + hz - vz,
                x - hx - vx, y - hy - vy, z - hz - vz, x - hx + vx, y - hy + vy, z - hz + vz, col, LightCoordsUtil.FULL_BRIGHT);
    }

    /** Cracks lying on the ground, glowing white-gold, cooling through the mid colour to the edge (c0 -> c1 -> c2). */
    static void fissure(VertexConsumer vc, Shape sh, float fade) {
        int per = Math.max(2, sh.emitParam);
        int cracks = sh.hn / per;
        float t = sh.t(FxRender.pt);
        // white-hot for the first fifth, then cooling
        int col = t < 0.2f ? FxWorld.mix(sh.c0, sh.c1, t / 0.2f) : FxWorld.mix(sh.c1, sh.c2, Math.min(1f, (t - 0.2f) / 0.6f));
        // the crack opens from the centre outward over its first 5 ticks
        float open = Math.min(1f, (sh.age + FxRender.pt) / 5f);
        double ox = FxRender.camX(), oy = FxRender.camY(), oz = FxRender.camZ();
        for (int k = 0; k < cracks; k++) {
            int reach = Math.max(1, Math.round((per - 1) * open));
            for (int i = 0; i < reach; i++) {
                int a = k * per + i, b = a + 1;
                float ax = (float) (sh.hx[a] - ox), ay = (float) (sh.hy[a] - oy), az = (float) (sh.hz[a] - oz);
                float bx = (float) (sh.hx[b] - ox), by = (float) (sh.hy[b] - oy), bz = (float) (sh.hz[b] - oz);
                // tapering to the tip
                float w0 = sh.a * (1 - i / (float) per), w1 = sh.a * (1 - (i + 1) / (float) per);
                float dx = bx - ax, dz = bz - az;
                float dl = Mth.sqrt(dx * dx + dz * dz);
                if (dl < 1e-4f) continue;
                float sx = -dz / dl, sz = dx / dl;
                int c0 = FxWorld.alpha(col, fade * 0.95f), c1 = FxWorld.alpha(col, fade * 0.8f);
                int clear = col & 0x00FFFFFF;
                // two halves across the crack: bright on its line, clear at its lips
                FxRender.quadFacing(vc, ax + sx * w0, ay, az + sz * w0, clear, ax, ay, az, c0, bx, by, bz, c1, bx + sx * w1, by, bz + sz * w1, clear);
                FxRender.quadFacing(vc, ax, ay, az, c0, ax - sx * w0, ay, az - sz * w0, clear, bx - sx * w1, by, bz - sz * w1, clear, bx, by, bz, c1);
            }
        }
    }
}
