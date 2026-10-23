package dev.awaken.powers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.powers.fx.FxPool;
import dev.awaken.powers.fx.FxRand;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws every live effect in four batches: additive meshes (rings, spheres, beams, trails, bolts, waves, seams; the
 * vanilla lightning render type), additive sprites (the particle atlas through the energy-swirl render type),
 * lit alpha sprites (smoke, dust, decals) and full-bright alpha sprites (ink with a glow, dark cores). Vertices are
 * written camera-relative straight into the buffer; nothing is allocated per frame.
 */
final class FxRender {
    private FxRender() {}

    static float pt;
    private static double cx, cy, cz;
    // camera right / up / forward (towards the viewer) in world space
    private static float rx, ry, rz, ux, uy, uz, fx, fy, fz;
    private static final Vector3f V = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    private static final FxRand JAG = new FxRand();
    private static float time;

    // render types are created on first draw (never during class loading on a server or before the GPU is up)
    private static RenderType addMesh, addSprites, litSprites, brightSprites, underlay;

    private static final SubmitNodeCollector.CustomGeometryRenderer MESHES = (pose, vc) -> {
        try {
            drawMeshes(vc);
        } catch (RuntimeException ex) {
            fail(ex);
        }
    };
    private static final SubmitNodeCollector.CustomGeometryRenderer ADD_SPRITES = (pose, vc) -> safeSprites(vc, FxPool.ADD);
    private static final SubmitNodeCollector.CustomGeometryRenderer LIT_SPRITES = (pose, vc) -> safeSprites(vc, FxPool.LIT);
    private static final SubmitNodeCollector.CustomGeometryRenderer BRIGHT_SPRITES = (pose, vc) -> safeSprites(vc, FxPool.BRIGHT);
    private static final SubmitNodeCollector.CustomGeometryRenderer UNDERLAY = (pose, vc) -> {
        try {
            drawUnderlay(vc);
        } catch (RuntimeException ex) {
            fail(ex);
        }
    };

    private static void safeSprites(VertexConsumer vc, int bucket) {
        try {
            drawSprites(vc, bucket);
        } catch (RuntimeException ex) {
            fail(ex);
        }
    }

    private static void fail(RuntimeException ex) {
        if (!broken) dev.awaken.Awaken.LOG.error("Power FX rendering failed and is switched off for this session", ex);
        broken = true;
    }

    /**
     * Daylight underlay: pure additive light washes out to white against a bright sky, so every glow is first laid down
     * as a darker, half-transparent copy in its own colour. At night it disappears into the dark; by day it keeps the
     * family colour readable. Meshes reuse their geometry through {@link #put} with a white texel of the atlas.
     */
    private static boolean under;
    private static float whiteU, whiteV;

    static void onExtract(ExtractLevelRenderStateEvent e) {
        pt = e.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        FxWorld.extract(pt);
        FxAfterimages.extract(pt);
    }

    /** Set if drawing ever throws: effects stop drawing for the session instead of crashing every frame. */
    private static boolean broken;

    static void onSubmit(SubmitCustomGeometryEvent e) {
        if (broken) return;
        try {
            submit(e);
        } catch (RuntimeException ex) {
            fail(ex);
        }
    }

    private static void submit(SubmitCustomGeometryEvent e) {
        boolean shapes = FxWorld.liveShapes > 0, sprites = FxWorld.POOL.live() > 0;
        if (!shapes && !sprites && !FxDebris.any() && !FxAfterimages.any()) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        Q.set(state.cameraRenderState.orientation);
        Q.transform(1, 0, 0, V);
        rx = V.x;
        ry = V.y;
        rz = V.z;
        Q.transform(0, 1, 0, V);
        ux = V.x;
        uy = V.y;
        uz = V.z;
        Q.transform(0, 0, 1, V);
        fx = V.x;
        fy = V.y;
        fz = V.z;
        time = (state.gameTime % 24000L) + state.worldPartialTicks;
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        if (addMesh == null) {
            addMesh = RenderTypes.lightning();
            addSprites = RenderTypes.energySwirl(TextureAtlas.LOCATION_PARTICLES, 0, 0);
            litSprites = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_PARTICLES);
            brightSprites = RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_PARTICLES);
            underlay = RenderTypes.breezeEyes(TextureAtlas.LOCATION_PARTICLES);
        }
        TextureAtlasSprite white = FxSprites.get(dev.awaken.powers.fx.PowerFxParticles.GLOW.index(), 0);
        if (white != null) {
            whiteU = (white.getU0() + white.getU1()) * 0.5f;
            whiteV = (white.getV0() + white.getV1()) * 0.5f;
        }
        // draw order: daylight underlays, lit smoke and dust, additive light, then dark ink on top
        if (white != null && !FxWorld.minimal()) out.order(1).submitCustomGeometry(ps, underlay, UNDERLAY);
        if (sprites || shapes) out.order(2).submitCustomGeometry(ps, litSprites, LIT_SPRITES);
        if (shapes) out.order(3).submitCustomGeometry(ps, addMesh, MESHES);
        if (shapes || sprites) out.order(3).submitCustomGeometry(ps, addSprites, ADD_SPRITES);
        if (sprites || shapes) out.order(4).submitCustomGeometry(ps, brightSprites, BRIGHT_SPRITES);
        FxDebris.submit(ps, out, cx, cy, cz, pt);
        FxAfterimages.submit(ps, out, cx, cy, cz);
    }

    // ---- sprites ----------------------------------------------------------------------------------------------------

    private static void drawSprites(VertexConsumer vc, int bucket) {
        FxPool p = FxWorld.POOL;
        float t = pt;
        for (int i = 0; i < p.live(); i++) {
            int f = p.flags[i];
            if ((f & bucket) == 0) continue;
            float prog = p.progress(i, t);
            float size = Mth.lerp(prog, p.size0[i], p.size1[i]);
            if (size <= 0.001f) continue;
            int frames = FxSprites.frames(p.sprite[i]);
            int frame = (f & FxPool.ANIM) != 0 ? Math.min(frames - 1, (int) (prog * frames)) : p.frame[i];
            TextureAtlasSprite s = FxSprites.get(p.sprite[i], frame);
            if (s == null) continue;
            int colour = FxWorld.mix(p.c0[i], p.c1[i], prog);
            float x = (float) (Mth.lerp(t, p.px[i], p.x[i]) - cx);
            float y = (float) (Mth.lerp(t, p.py[i], p.y[i]) - cy);
            float z = (float) (Mth.lerp(t, p.pz[i], p.z[i]) - cz);
            int light = bucket == FxPool.LIT ? p.light[i] : LightCoordsUtil.FULL_BRIGHT;
            if (bucket == FxPool.ADD) colour = premul(colour);
            float rot = Mth.lerp(t, p.prot[i], p.rot[i]);
            if ((f & FxPool.STRETCH) != 0) {
                stretched(vc, s, x, y, z, p.vx[i], p.vy[i], p.vz[i], size, p.stretch[i], colour, light);
            } else if ((f & FxPool.FLAT) != 0) {
                flat(vc, s, x, y, z, size, rot, colour, light);
            } else {
                billboard(vc, s, x, y, z, size, rot, colour, light);
            }
        }
        if (bucket == FxPool.ADD) drawShapeSprites(vc, false);
        else if (bucket == FxPool.BRIGHT) drawShapeSprites(vc, true);
    }

    /** The darker copy of every glow (sprites big enough to matter, glow shapes and every mesh). */
    private static void drawUnderlay(VertexConsumer vc) {
        FxPool p = FxWorld.POOL;
        float t = pt;
        under = true;
        try {
            for (int i = 0; i < p.live(); i++) {
                int f = p.flags[i];
                if ((f & FxPool.ADD) == 0) continue;
                float prog = p.progress(i, t);
                float size = Mth.lerp(prog, p.size0[i], p.size1[i]);
                if (size < 0.18f) continue;
                int frames = FxSprites.frames(p.sprite[i]);
                int frame = (f & FxPool.ANIM) != 0 ? Math.min(frames - 1, (int) (prog * frames)) : p.frame[i];
                TextureAtlasSprite s = FxSprites.get(p.sprite[i], frame);
                if (s == null) continue;
                int colour = shade(FxWorld.mix(p.c0[i], p.c1[i], prog));
                if ((colour >>> 24) < 4) continue;
                float x = (float) (Mth.lerp(t, p.px[i], p.x[i]) - cx);
                float y = (float) (Mth.lerp(t, p.py[i], p.y[i]) - cy);
                float z = (float) (Mth.lerp(t, p.pz[i], p.z[i]) - cz);
                float rot = Mth.lerp(t, p.prot[i], p.rot[i]);
                if ((f & FxPool.STRETCH) != 0) stretched(vc, s, x, y, z, p.vx[i], p.vy[i], p.vz[i], size, p.stretch[i], colour, LightCoordsUtil.FULL_BRIGHT);
                else if ((f & FxPool.FLAT) != 0) flat(vc, s, x, y, z, size, rot, colour, LightCoordsUtil.FULL_BRIGHT);
                else billboard(vc, s, x, y, z, size, rot, colour, LightCoordsUtil.FULL_BRIGHT);
            }
            drawShapeSprites(vc, false);
            drawMeshes(vc);
        } finally {
            under = false;
        }
    }

    /**
     * Underlay colour: the same hue at about 60% brightness and a little over half the opacity. Tuned after the first
     * in-game tour: at 42% / 50% several effects still vanished against a bright sky.
     */
    static int shade(int argb) {
        int a = (int) (((argb >>> 24) & 255) * 0.58f);
        int r = (((argb >> 16) & 255) * 5) >> 3, g = (((argb >> 8) & 255) * 5) >> 3, b = ((argb & 255) * 5) >> 3;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** One mesh vertex: position + colour for the additive pass, or a full entity vertex on a white texel for the underlay. */
    static void put(VertexConsumer vc, float x, float y, float z, int argb) {
        if (under) vc.addVertex(x, y, z, shade(argb), whiteU, whiteV, OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, 0, 1, 0);
        else vc.addVertex(x, y, z).setColor(argb);
    }

    /** A glow colour for the current pass: premultiplied for additive light, shaded for the underlay. */
    private static int tint(int argb) {
        return under ? shade(argb) : premul(argb);
    }

    /** ARGB with the alpha folded into the colour (additive blending ignores alpha). */
    static int premul(int argb) {
        float a = ((argb >>> 24) & 255) / 255f;
        int r = (int) (((argb >> 16) & 255) * a);
        int g = (int) (((argb >> 8) & 255) * a);
        int b = (int) ((argb & 255) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static void billboard(VertexConsumer vc, TextureAtlasSprite s, float x, float y, float z, float size, float rot, int colour, int light) {
        float h = size * 0.5f;
        float c = Mth.cos(rot), sn = Mth.sin(rot);
        // rotated camera axes
        float ax = (rx * c + ux * sn) * h, ay = (ry * c + uy * sn) * h, az = (rz * c + uz * sn) * h;
        float bx = (ux * c - rx * sn) * h, by = (uy * c - ry * sn) * h, bz = (uz * c - rz * sn) * h;
        quadTex(vc, s, x + ax - bx, y + ay - by, z + az - bz, x + ax + bx, y + ay + by, z + az + bz,
                x - ax + bx, y - ay + by, z - az + bz, x - ax - bx, y - ay - by, z - az - bz, colour, light);
    }

    private static void stretched(VertexConsumer vc, TextureAtlasSprite s, float x, float y, float z, float vx, float vy, float vz,
                                  float size, float stretch, int colour, int light) {
        float sp = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (sp < 1e-4f) {
            billboard(vc, s, x, y, z, size, 0, colour, light);
            return;
        }
        float dx = vx / sp, dy = vy / sp, dz = vz / sp;
        float len = size * 0.5f + sp * stretch * 1.5f;
        // side = dir x view, view = towards camera = -p
        float sx = dy * -z - dz * -y, sy = dz * -x - dx * -z, sz = dx * -y - dy * -x;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-5f) return;
        float w = size * 0.25f / sl;
        sx *= w;
        sy *= w;
        sz *= w;
        float hx = dx * len * 0.5f, hy = dy * len * 0.5f, hz = dz * len * 0.5f;
        // tail (v1) to head (v0): A = tail, B = head
        float ax = x - hx, ay = y - hy, az = z - hz, bx = x + hx, by = y + hy, bz = z + hz;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int o = OverlayTexture.NO_OVERLAY;
        vc.addVertex(ax - sx, ay - sy, az - sz, colour, u0, v1, o, light, 0, 1, 0);
        vc.addVertex(ax + sx, ay + sy, az + sz, colour, u1, v1, o, light, 0, 1, 0);
        vc.addVertex(bx + sx, by + sy, bz + sz, colour, u1, v0, o, light, 0, 1, 0);
        vc.addVertex(bx - sx, by - sy, bz - sz, colour, u0, v0, o, light, 0, 1, 0);
    }

    private static void flat(VertexConsumer vc, TextureAtlasSprite s, float x, float y, float z, float size, float rot, int colour, int light) {
        float h = size * 0.5f;
        float c = Mth.cos(rot) * h, sn = Mth.sin(rot) * h;
        float yy = y + 0.03f;
        quadTex(vc, s, x + c - sn, yy, z + sn + c, x + c + sn, yy, z + sn - c, x - c + sn, yy, z - sn - c, x - c - sn, yy, z - sn + c, colour, light);
    }

    private static void quadTex(VertexConsumer vc, TextureAtlasSprite s, float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3, int colour, int light) {
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int o = OverlayTexture.NO_OVERLAY;
        vc.addVertex(x0, y0, z0, colour, u1, v1, o, light, 0, 1, 0);
        vc.addVertex(x1, y1, z1, colour, u1, v0, o, light, 0, 1, 0);
        vc.addVertex(x2, y2, z2, colour, u0, v0, o, light, 0, 1, 0);
        vc.addVertex(x3, y3, z3, colour, u0, v1, o, light, 0, 1, 0);
    }

    // ---- sprite-based shapes (glyphs, discs, hex shells) ------------------------------------------------------------

    private static void drawShapeSprites(VertexConsumer vc, boolean darkPass) {
        for (Shape sh : FxWorld.SHAPES) {
            if (!sh.live) continue;
            float fade = sh.fade(pt);
            if (fade <= 0) continue;
            switch (sh.type) {
                case Shape.DISC -> {
                    boolean dark = (sh.flags & Shape.DARK) != 0;
                    if (dark != darkPass) continue;
                    TextureAtlasSprite s = FxSprites.get(sh.sprite, sh.frame);
                    if (s == null) continue;
                    float t = sh.t(pt);
                    float size = Mth.lerp(t, sh.a, sh.b);
                    if ((sh.flags & Shape.PULSE) != 0) size *= 1 + 0.12f * Mth.sin((sh.age + pt) * 1.3f);
                    if ((sh.flags & Shape.FLICKER) != 0) size *= 0.85f + 0.3f * flick(sh.seed, sh.age);
                    int col = FxWorld.alpha(FxWorld.mix(sh.c0, sh.c1, t), fade * (((FxWorld.mix(sh.c0, sh.c1, t) >>> 24) & 255) / 255f));
                    billboard(vc, s, (float) (sh.rx - cx), (float) (sh.ry - cy), (float) (sh.rz - cz), size, sh.c * (sh.age + pt),
                            dark ? col : tint(col), LightCoordsUtil.FULL_BRIGHT);
                }
                case Shape.GLYPH -> {
                    if (darkPass) continue;
                    TextureAtlasSprite s = FxSprites.get(sh.sprite, 0);
                    if (s == null) continue;
                    float t = sh.t(pt);
                    float r = Mth.lerp(1 - (1 - t) * (1 - t) * (1 - t), sh.a, sh.b);
                    int col = tint(FxWorld.alpha(FxWorld.mix(sh.c0, sh.c1, t), fade));
                    flat(vc, s, (float) (sh.rx - cx), (float) (sh.ry - cy), (float) (sh.rz - cz), r * 2, sh.c * (sh.age + pt), col,
                            LightCoordsUtil.FULL_BRIGHT);
                }
                case Shape.SHELL -> {
                    if (darkPass) continue;
                    shell(vc, sh, fade);
                }
                case Shape.BEAM -> {
                    if (darkPass) continue;
                    TextureAtlasSprite s = FxSprites.get(dev.awaken.powers.fx.PowerFxParticles.GLOW.index(), 0);
                    if (s == null) continue;
                    float w = sh.a * (1 + 0.15f * Mth.sin((sh.age + pt) * 2.1f));
                    int body = tint(FxWorld.alpha(sh.c0, 0.75f * fade));
                    int core = tint(FxWorld.alpha(sh.c1 == 0 ? 0xFFFFFFFF : sh.c1, 0.9f * fade));
                    float ax = (float) (sh.rx - cx), ay = (float) (sh.ry - cy), az = (float) (sh.rz - cz);
                    float bx = (float) (sh.rex - cx), by = (float) (sh.rey - cy), bz = (float) (sh.rez - cz);
                    billboard(vc, s, ax, ay, az, w * 8, 0, body, LightCoordsUtil.FULL_BRIGHT);
                    billboard(vc, s, ax, ay, az, w * 3.5f, 0, core, LightCoordsUtil.FULL_BRIGHT);
                    billboard(vc, s, bx, by, bz, w * 14, time * 0.2f, body, LightCoordsUtil.FULL_BRIGHT);
                    billboard(vc, s, bx, by, bz, w * 5, 0, core, LightCoordsUtil.FULL_BRIGHT);
                }
                default -> {
                }
            }
        }
    }

    /** Hex cells on a sphere around the anchor, each facing outward, lit by a sweep that climbs the body. */
    private static void shell(VertexConsumer vc, Shape sh, float fade) {
        TextureAtlasSprite s = FxSprites.get(sh.sprite, 0);
        if (s == null) return;
        int n = 30;
        float golden = (float) (Math.PI * (3 - Math.sqrt(5)));
        float sweep = ((sh.age + pt) * 0.08f) % 1.6f - 0.3f;
        float bx = (float) (sh.rx - cx), by = (float) (sh.ry - cy), bz = (float) (sh.rz - cz);
        for (int i = 0; i < n; i++) {
            float yy = 1 - (i / (float) (n - 1)) * 2;
            float r = Mth.sqrt(Math.max(0, 1 - yy * yy));
            float th = golden * i + sh.seed;
            float nx = Mth.cos(th) * r, nz = Mth.sin(th) * r;
            float h01 = (yy + 1) * 0.5f;
            float lit = Math.max(0.15f, 1 - Math.abs(h01 - sweep) * 4);
            int col = tint(FxWorld.alpha(sh.c0, fade * lit * 0.8f));
            float px = bx + nx * sh.a, py = by + yy * sh.a * 1.35f, pz = bz + nz * sh.a;
            // a quad in the plane perpendicular to the normal
            float ex = -nz, ez = nx, ey = 0;
            float el = Mth.sqrt(ex * ex + ez * ez);
            if (el < 1e-3f) {
                ex = 1;
                ez = 0;
                el = 1;
            }
            ex /= el;
            ez /= el;
            // f = n x e
            float gx = yy * ez - nz * ey, gy = nz * ex - nx * ez, gz = nx * ey - yy * ex;
            float h = sh.b * 0.5f;
            quadTex(vc, s, px + (ex + gx) * h, py + (ey + gy) * h, pz + (ez + gz) * h, px + (ex - gx) * h, py + (ey - gy) * h, pz + (ez - gz) * h,
                    px + (-ex - gx) * h, py + (-ey - gy) * h, pz + (-ez - gz) * h, px + (-ex + gx) * h, py + (-ey + gy) * h, pz + (-ez + gz) * h,
                    col, LightCoordsUtil.FULL_BRIGHT);
        }
    }

    static float flick(int seed, int age) {
        int h = seed * 31 + age * 7919;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 1023) / 1023f;
    }

    // ---- meshes -----------------------------------------------------------------------------------------------------

    private static void drawMeshes(VertexConsumer vc) {
        for (Shape sh : FxWorld.SHAPES) {
            if (!sh.live) continue;
            float fade = sh.fade(pt);
            if (fade <= 0) continue;
            switch (sh.type) {
                case Shape.RING -> ring(vc, sh, fade);
                case Shape.SPHERE -> sphere(vc, sh, fade);
                case Shape.BEAM -> FxBeams.draw(vc, sh, fade);
                case Shape.TRAIL -> trail(vc, sh, fade);
                case Shape.LIGHTNING -> lightning(vc, sh, fade);
                case Shape.ARC -> arc(vc, sh, fade);
                case Shape.COLUMN -> column(vc, sh, fade);
                case Shape.TETHER -> tether(vc, sh, fade);
                case Shape.SEAMS -> FxSeams.draw(vc, sh, fade);
                default -> {
                }
            }
        }
    }

    private static float easeOut(float t, float pow) {
        return 1 - (float) Math.pow(1 - t, pow);
    }

    /** A flat band growing from radius a to b, width c to d, centred on the shape, around its normal. */
    private static void ring(VertexConsumer vc, Shape sh, float fade) {
        float t = sh.t(pt);
        float e = easeOut((sh.flags & Shape.HOLD) != 0 ? Math.min(1, (sh.age + pt) / 8f) : t, 2.6f);
        float r = Mth.lerp(e, sh.a, sh.b);
        if ((sh.flags & Shape.PULSE) != 0) r *= 1 + 0.08f * Mth.sin((sh.age + pt) * 0.6f);
        float w = Mth.lerp(t, sh.c, sh.d);
        int col = FxWorld.mix(sh.c0, sh.c1, t);
        float alpha = ((col >>> 24) & 255) / 255f * fade;
        if (alpha <= 0.01f || r <= 0) return;
        float ox = (float) (sh.rx - cx), oy = (float) (sh.ry - cy), oz = (float) (sh.rz - cz);
        bandAround(vc, ox, oy, oz, sh.nx, sh.ny, sh.nz, Math.max(0, r - w), r, r + w * 0.6f, FxWorld.alpha(col, alpha),
                Math.max(16, Math.min(64, (int) (r * 10))), 0, (float) (Math.PI * 2));
    }

    /**
     * Band between radii ri..rm..ro in the plane around normal n, from angle a0 to a1; inner and outer edges are
     * transparent, the middle carries the colour. Wound to face the camera.
     */
    static void bandAround(VertexConsumer vc, float ox, float oy, float oz, float nx, float ny, float nz, float ri, float rm, float ro,
                           int col, int segs, float a0, float a1) {
        // basis: e1 perpendicular to n, e2 = n x e1 (so e1, e2, n is right-handed)
        float e1x, e1y, e1z;
        if (Math.abs(ny) > 0.9f) {
            e1x = 0;
            e1y = 0;
            e1z = 1;
        } else {
            e1x = 0;
            e1y = 1;
            e1z = 0;
        }
        // make e1 perpendicular: e1 -= n (n.e1)
        float dot = e1x * nx + e1y * ny + e1z * nz;
        e1x -= nx * dot;
        e1y -= ny * dot;
        e1z -= nz * dot;
        float l = Mth.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= l;
        e1y /= l;
        e1z /= l;
        float e2x = ny * e1z - nz * e1y, e2y = nz * e1x - nx * e1z, e2z = nx * e1y - ny * e1x;
        boolean front = -(ox * nx + oy * ny + oz * nz) >= 0; // camera on the +n side
        int clear = col & 0x00FFFFFF;
        float step = (a1 - a0) / segs;
        for (int i = 0; i < segs; i++) {
            float th0 = a0 + i * step, th1 = th0 + step;
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0), c1 = Mth.cos(th1), s1 = Mth.sin(th1);
            float d0x = e1x * c0 + e2x * s0, d0y = e1y * c0 + e2y * s0, d0z = e1z * c0 + e2z * s0;
            float d1x = e1x * c1 + e2x * s1, d1y = e1y * c1 + e2y * s1, d1z = e1z * c1 + e2z * s1;
            // inner -> mid
            quadC(vc, front,
                    ox + d0x * ri, oy + d0y * ri, oz + d0z * ri, clear,
                    ox + d0x * rm, oy + d0y * rm, oz + d0z * rm, col,
                    ox + d1x * rm, oy + d1y * rm, oz + d1z * rm, col,
                    ox + d1x * ri, oy + d1y * ri, oz + d1z * ri, clear);
            // mid -> outer
            quadC(vc, front,
                    ox + d0x * rm, oy + d0y * rm, oz + d0z * rm, col,
                    ox + d0x * ro, oy + d0y * ro, oz + d0z * ro, clear,
                    ox + d1x * ro, oy + d1y * ro, oz + d1z * ro, clear,
                    ox + d1x * rm, oy + d1y * rm, oz + d1z * rm, col);
        }
    }

    /** One coloured quad; {@code forward} keeps the given order, otherwise it is reversed (other face). */
    static void quadC(VertexConsumer vc, boolean forward, float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
                      float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
        if (forward) {
            put(vc, x0, y0, z0, c0);
            put(vc, x1, y1, z1, c1);
            put(vc, x2, y2, z2, c2);
            put(vc, x3, y3, z3, c3);
        } else {
            put(vc, x3, y3, z3, c3);
            put(vc, x2, y2, z2, c2);
            put(vc, x1, y1, z1, c1);
            put(vc, x0, y0, z0, c0);
        }
    }

    /** A quad that faces the camera whichever way it was given. */
    static void quadFacing(VertexConsumer vc, float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
                           float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0, bx = x2 - x0, by = y2 - y0, bz = z2 - z0;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        boolean front = -(x0 * nx + y0 * ny + z0 * nz) >= 0;
        quadC(vc, front, x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3);
    }

    /**
     * A camera-facing strip segment from A to B, half widths wa/wb, colour at the centre line ca/cb fading to clear
     * edges.
     */
    static void segment(VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb, int ca, int cb) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        // side = d x (-m)
        float sx = dy * -mz - dz * -my, sy = dz * -mx - dx * -mz, sz = dx * -my - dy * -mx;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6f) return;
        sx /= sl;
        sy /= sl;
        sz /= sl;
        int ea = ca & 0x00FFFFFF, eb = cb & 0x00FFFFFF;
        // (A - s, A, B, B - s) then (A, A + s, B + s, B): both face the camera
        put(vc, ax - sx * wa, ay - sy * wa, az - sz * wa, ea);
        put(vc, ax, ay, az, ca);
        put(vc, bx, by, bz, cb);
        put(vc, bx - sx * wb, by - sy * wb, bz - sz * wb, eb);
        put(vc, ax, ay, az, ca);
        put(vc, ax + sx * wa, ay + sy * wa, az + sz * wa, ea);
        put(vc, bx + sx * wb, by + sy * wb, bz + sz * wb, eb);
        put(vc, bx, by, bz, cb);
    }

    /** A shell (or ground dome) with a bright rim where it turns away from the camera. */
    private static void sphere(VertexConsumer vc, Shape sh, float fade) {
        float t = sh.t(pt);
        float g = (sh.flags & Shape.HOLD) != 0 ? Math.min(1, (sh.age + pt) / 8f) : t;
        float r = Mth.lerp(easeOut(g, 2.2f), sh.a, sh.b);
        if ((sh.flags & Shape.PULSE) != 0) r *= 1 + 0.025f * Mth.sin((sh.age + pt) * 0.5f);
        if (r <= 0.05f) return;
        int col = FxWorld.mix(sh.c0, sh.c1, t);
        float base = ((col >>> 24) & 255) / 255f * fade;
        if (base <= 0.01f) return;
        float ox = (float) (sh.rx - cx), oy = (float) (sh.ry - cy), oz = (float) (sh.rz - cz);
        boolean inside = ox * ox + oy * oy + oz * oz < r * r;
        boolean hemi = (sh.flags & Shape.HEMI) != 0;
        int lat = hemi ? 6 : 10, lon = 20;
        float phiMax = hemi ? (float) (Math.PI / 2) : (float) Math.PI;
        float rimPow = sh.c <= 0 ? 2.5f : sh.c;
        float fill = sh.d;
        int rgb = col & 0x00FFFFFF;
        for (int i = 0; i < lat; i++) {
            float p0 = phiMax * i / lat, p1 = phiMax * (i + 1) / lat;
            float sp0 = Mth.sin(p0), cp0 = Mth.cos(p0), sp1 = Mth.sin(p1), cp1 = Mth.cos(p1);
            for (int j = 0; j < lon; j++) {
                float t0 = (float) (Math.PI * 2 * j / lon), t1 = (float) (Math.PI * 2 * (j + 1) / lon);
                float ct0 = Mth.cos(t0), st0 = Mth.sin(t0), ct1 = Mth.cos(t1), st1 = Mth.sin(t1);
                // normals (unit) for the four corners: phi measured from +y
                float n0x = sp0 * ct0, n0y = cp0, n0z = sp0 * st0;
                float n1x = sp1 * ct0, n1y = cp1, n1z = sp1 * st0;
                float n2x = sp1 * ct1, n2y = cp1, n2z = sp1 * st1;
                float n3x = sp0 * ct1, n3y = cp0, n3z = sp0 * st1;
                int c0 = sphereCol(rgb, base, ox, oy, oz, r, n0x, n0y, n0z, rimPow, fill, inside);
                int c1 = sphereCol(rgb, base, ox, oy, oz, r, n1x, n1y, n1z, rimPow, fill, inside);
                int c2 = sphereCol(rgb, base, ox, oy, oz, r, n2x, n2y, n2z, rimPow, fill, inside);
                int c3 = sphereCol(rgb, base, ox, oy, oz, r, n3x, n3y, n3z, rimPow, fill, inside);
                quadFacing(vc, ox + n0x * r, oy + n0y * r, oz + n0z * r, c0, ox + n1x * r, oy + n1y * r, oz + n1z * r, c1,
                        ox + n2x * r, oy + n2y * r, oz + n2z * r, c2, ox + n3x * r, oy + n3y * r, oz + n3z * r, c3);
            }
        }
    }

    private static int sphereCol(int rgb, float base, float ox, float oy, float oz, float r, float nx, float ny, float nz, float rimPow,
                                 float fill, boolean inside) {
        float px = ox + nx * r, py = oy + ny * r, pz = oz + nz * r;
        float pl = Mth.sqrt(px * px + py * py + pz * pz);
        float facing = pl < 1e-4f ? 1 : Math.abs(-(px * nx + py * ny + pz * nz) / pl);
        float rim = (float) Math.pow(1 - facing, rimPow);
        float a = base * (inside ? fill + rim * 0.45f : fill + rim * (1 - fill));
        return FxWorld.alpha(rgb, Math.min(1, a));
    }

    /** A ribbon through the anchor's recent positions, tapering to the tail. */
    private static void trail(VertexConsumer vc, Shape sh, float fade) {
        int n = sh.hn;
        if (n < 2) return;
        float w = sh.a;
        float px = (float) (sh.rx - cx), py = (float) (sh.ry - cy), pz = (float) (sh.rz - cz);
        int idx = sh.hhead;
        for (int k = 0; k < n - 1; k++) {
            int i0 = Math.floorMod(idx - k, sh.hx.length), i1 = Math.floorMod(idx - k - 1, sh.hx.length);
            float ax = k == 0 ? px : (float) (sh.hx[i0] - cx), ay = k == 0 ? py : (float) (sh.hy[i0] - cy), az = k == 0 ? pz : (float) (sh.hz[i0] - cz);
            float bx = (float) (sh.hx[i1] - cx), by = (float) (sh.hy[i1] - cy), bz = (float) (sh.hz[i1] - cz);
            float f0 = 1 - k / (float) (n - 1), f1 = 1 - (k + 1) / (float) (n - 1);
            int ca = FxWorld.alpha(FxWorld.mix(sh.c1, sh.c0, f0), fade * f0 * (((sh.c0 >>> 24) & 255) / 255f));
            int cb = FxWorld.alpha(FxWorld.mix(sh.c1, sh.c0, f1), fade * f1 * (((sh.c0 >>> 24) & 255) / 255f));
            float wa = w * (0.25f + 0.75f * f0), wb = w * (0.25f + 0.75f * f1);
            segment(vc, ax, ay, az, bx, by, bz, wa, wb, ca, cb);
        }
    }

    /** A jagged bolt from the origin (or anchor) to the end, re-forked every two ticks, with one branch. */
    private static void lightning(VertexConsumer vc, Shape sh, float fade) {
        float ax = (float) (sh.rx - cx), ay = (float) (sh.ry - cy), az = (float) (sh.rz - cz);
        float bx = (float) (sh.rex - cx), by = (float) (sh.rey - cy), bz = (float) (sh.rez - cz);
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.1f) return;
        int segs = Math.max(4, Math.min(24, (int) (len * 1.6f)));
        JAG.seed(sh.seed * 131L + sh.age / 2);
        float jag = Math.min(0.9f, 0.25f + len * 0.05f);
        // perpendicular axes
        float qx = -dz, qy = 0, qz = dx;
        float ql = Mth.sqrt(qx * qx + qz * qz);
        if (ql < 1e-4f) {
            qx = 1;
            qz = 0;
            ql = 1;
        }
        qx /= ql;
        qz /= ql;
        float wx = dy * qz - dz * qy, wy = dz * qx - dx * qz, wz = dx * qy - dy * qx;
        float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
        wx /= wl;
        wy /= wl;
        wz /= wl;
        float flick = (sh.flags & Shape.FLICKER) != 0 ? 0.55f + 0.45f * flick(sh.seed, sh.age) : 1f;
        int glow = FxWorld.alpha(sh.c0, 0.45f * fade * flick);
        int core = FxWorld.alpha(sh.c1, fade * flick);
        float w = sh.a;
        float lx = ax, ly = ay, lz = az;
        float brX = 0, brY = 0, brZ = 0;
        int branchAt = 1 + JAG.i(Math.max(1, segs - 2));
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            float nx = ax + dx * t, ny = ay + dy * t, nz = az + dz * t;
            if (i < segs) {
                float o1 = JAG.s() * jag, o2 = JAG.s() * jag;
                nx += qx * o1 + wx * o2;
                ny += qy * o1 + wy * o2;
                nz += qz * o1 + wz * o2;
            }
            segment(vc, lx, ly, lz, nx, ny, nz, w * 3, w * 3, glow, glow);
            segment(vc, lx, ly, lz, nx, ny, nz, w, w, core, core);
            if (i == branchAt) {
                brX = nx;
                brY = ny;
                brZ = nz;
            }
            lx = nx;
            ly = ny;
            lz = nz;
        }
        // one fork off the main channel
        float fl = len * 0.3f;
        float tx = brX + (dx / len + qx * JAG.s() + wx * JAG.s()) * fl * 0.6f;
        float ty = brY + (dy / len + qy * JAG.s() + wy * JAG.s()) * fl * 0.6f;
        float tz = brZ + (dz / len + qz * JAG.s() + wz * JAG.s()) * fl * 0.6f;
        float mx = (brX + tx) * 0.5f + qx * JAG.s() * 0.3f, my = (brY + ty) * 0.5f + wy * JAG.s() * 0.3f, mz = (brZ + tz) * 0.5f + qz * JAG.s() * 0.3f;
        segment(vc, brX, brY, brZ, mx, my, mz, w * 0.6f, w * 0.4f, core, core);
        segment(vc, mx, my, mz, tx, ty, tz, w * 0.4f, w * 0.1f, core, FxWorld.alpha(core, 0));
    }

    /** A rolling wave: a curved, upright sheet of force moving out along the direction, plus its ground band. */
    private static void arc(VertexConsumer vc, Shape sh, float fade) {
        float t = sh.t(pt);
        float r = Mth.lerp(t, sh.a, sh.b);
        float half = sh.c;
        float height = sh.d * (1 - 0.5f * t);
        int col = FxWorld.alpha(FxWorld.mix(sh.c0, sh.c1, t), fade * (((sh.c0 >>> 24) & 255) / 255f));
        int clear = col & 0x00FFFFFF;
        float ox = (float) (sh.rx - cx), oy = (float) (sh.ry - cy), oz = (float) (sh.rz - cz);
        float base = (float) Math.atan2(sh.nz, sh.nx);
        int segs = Math.max(6, (int) (half * r * 3));
        float step = 2 * half / segs;
        for (int i = 0; i < segs; i++) {
            float a0 = base - half + i * step, a1 = a0 + step;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            // edges of the sheet fade out at the ends of the arc
            float e0 = edge(i, segs), e1 = edge(i + 1, segs);
            int k0 = FxWorld.alpha(col, (((col >>> 24) & 255) / 255f) * e0), k1 = FxWorld.alpha(col, (((col >>> 24) & 255) / 255f) * e1);
            float x0 = ox + c0 * r, z0 = oz + s0 * r, x1 = ox + c1 * r, z1 = oz + s1 * r;
            quadFacing(vc, x0, oy, z0, k0, x0, oy + height, z0, clear, x1, oy + height, z1, clear, x1, oy, z1, k1);
            // ground band just behind the front
            float rb = Math.max(0, r - 0.9f);
            quadFacing(vc, ox + c0 * rb, oy + 0.05f, oz + s0 * rb, clear, x0, oy + 0.05f, z0, k0, x1, oy + 0.05f, z1, k1,
                    ox + c1 * rb, oy + 0.05f, oz + s1 * rb, clear);
        }
    }

    private static float edge(int i, int segs) {
        float u = i / (float) segs;
        return Math.min(1, Math.min(u, 1 - u) * 4);
    }

    /** A vertical column of light (strikes, pillars), cylindrically billboarded. */
    private static void column(VertexConsumer vc, Shape sh, float fade) {
        float t = sh.t(pt);
        float ox = (float) (sh.rx - cx), oy = (float) (sh.ry - cy), oz = (float) (sh.rz - cz);
        float h = sh.a;
        float w = sh.b * (1 - 0.6f * t);
        if ((sh.flags & Shape.FLICKER) != 0) w *= 0.6f + 0.6f * flick(sh.seed, sh.age);
        int col = FxWorld.alpha(FxWorld.mix(sh.c0, sh.c1, t), fade * (((sh.c0 >>> 24) & 255) / 255f));
        int top = FxWorld.alpha(col, 0.15f * fade);
        // halo then core
        segment(vc, ox, oy, oz, ox, oy + h, oz, w * 2.4f, w * 1.2f, FxWorld.alpha(col, 0.35f * fade), FxWorld.alpha(top, 0.05f));
        segment(vc, ox, oy, oz, ox, oy + h, oz, w, w * 0.5f, col, top);
        int core = FxWorld.alpha(sh.c2 == 0 ? 0xFFFFFFFF : sh.c2, fade);
        segment(vc, ox, oy, oz, ox, oy + h, oz, w * 0.3f, w * 0.15f, core, FxWorld.alpha(core, 0.1f * fade));
    }

    /** A cord from the anchor to the end, sagging and rippling. */
    private static void tether(VertexConsumer vc, Shape sh, float fade) {
        float ax = (float) (sh.rx - cx), ay = (float) (sh.ry - cy), az = (float) (sh.rz - cz);
        float bx = (float) (sh.rex - cx), by = (float) (sh.rey - cy), bz = (float) (sh.rez - cz);
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.2f) return;
        // a horizontal perpendicular for the ripple
        float qx = -dz, qz = dx;
        float ql = Mth.sqrt(qx * qx + qz * qz);
        if (ql < 1e-4f) {
            qx = 1;
            qz = 0;
            ql = 1;
        }
        qx /= ql;
        qz /= ql;
        int segs = Math.max(6, Math.min(20, (int) (len * 1.5f)));
        float amp = sh.b * (1 - sh.t(pt) * 0.7f);
        float tt = sh.age + pt;
        int core = FxWorld.alpha(sh.c1, fade);
        int glow = FxWorld.alpha(sh.c0, 0.5f * fade);
        float lx = ax, ly = ay, lz = az;
        for (int i = 1; i <= segs; i++) {
            float u = i / (float) segs;
            float env = Mth.sin(u * (float) Math.PI);
            float wave = Mth.sin(u * 9f - tt * 1.4f) * amp * env;
            float sag = -env * len * 0.04f * sh.d;
            float nx = ax + dx * u + qx * wave, ny = ay + dy * u + sag, nz = az + dz * u + qz * wave;
            segment(vc, lx, ly, lz, nx, ny, nz, sh.a * 2.6f, sh.a * 2.6f, glow, glow);
            segment(vc, lx, ly, lz, nx, ny, nz, sh.a, sh.a, core, core);
            lx = nx;
            ly = ny;
            lz = nz;
        }
    }

    static double camX() {
        return cx;
    }

    static double camY() {
        return cy;
    }

    static double camZ() {
        return cz;
    }

    static float time() {
        return time;
    }
}
