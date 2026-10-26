package dev.awaken.wildlife.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import dev.awaken.wildlife.WildKinds;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

/**
 * Draws the wild's insects, ripples and far flocks (SPEC "Wild life") from our own sprite sheet
 * (textures/entity/wild/insects.png, tools/art/specs/wild_fauna.py) as real little bodies, not particles: a butterfly is
 * two wings hinged on its body, beating fast in flight and opening and closing slowly at rest; a dragonfly's two pairs
 * shimmer; a bee's wings blur. Fireflies light in a second, additive pass (the city glow's kind of pipeline: depth-tested,
 * no depth written), so their blinks feed Lens's bloom. Camera-relative, one callback each, nothing allocated per frame.
 */
final class InsectRender {
    private InsectRender() {}

    static final Identifier ATLAS = Awaken.id("textures/entity/wild/insects.png");
    private static final float T = 1F / 64F;
    private static RenderType sheet;

    // ---- the glow's pipeline (as CityGlow's) ------------------------------------------------------------------------
    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline GLOW = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/wild_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final OitPipelineSet GLOW_OIT = OitPipelineSet.builder(Awaken.id("wild_glow"),
            RenderPipeline.builder(SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();
    private static RenderType glowType;
    private static int ok = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(GLOW);
        e.registerOitPipelineSet(GLOW_OIT);
    }

    private static RenderType glow() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(GLOW) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
        }
        if (ok == 0) return RenderTypes.lightning();
        if (glowType == null) glowType = RenderType.create("awaken_wild_glow", RenderSetup.builder(GLOW).setOitPipelines(GLOW_OIT).createRenderSetup());
        return glowType;
    }

    // ---- this frame -------------------------------------------------------------------------------------------------
    private static double cx, cy, cz;
    private static float pt, age;
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static VertexConsumer vc;
    private static int skyLight = LightCoordsUtil.pack(0, 15);

    static void submit(SubmitCustomGeometryEvent e) {
        if (Insects.count() == 0 && !Insects.flock && Wildlife.now - lastRipple() > 30) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        age = Wildlife.now + pt;
        RIGHT.set(1, 0, 0).rotate(state.cameraRenderState.orientation);
        UP.set(0, 1, 0).rotate(state.cameraRenderState.orientation);
        if (sheet == null) sheet = RenderTypes.entityTranslucent(ATLAS);
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        out.submitCustomGeometry(e.getPoseStack(), sheet, BODIES);
        out.submitCustomGeometry(e.getPoseStack(), glow(), LIGHTS);
    }

    private static long lastRipple() {
        long m = Long.MIN_VALUE;
        for (long b : Insects.rborn) m = Math.max(m, b);
        return m;
    }

    private static final SubmitNodeCollector.CustomGeometryRenderer BODIES = (pose, consumer) -> {
        try {
            vc = consumer;
            for (int i = 0; i < Insects.CAP; i++) {
                int k = Insects.kind[i];
                if (k < 0) continue;
                float a = Mth.lerp(pt, Insects.alphaO[i], Insects.alpha[i]);
                if (a <= 0.01F) continue;
                double x = Mth.lerp(pt, Insects.xo[i], Insects.x[i]), y = Mth.lerp(pt, Insects.yo[i], Insects.y[i]),
                        z = Mth.lerp(pt, Insects.zo[i], Insects.z[i]);
                double dx = x - cx, dy = y - cy, dz = z - cz;
                if (dx * dx + dy * dy + dz * dz > 48 * 48 || !Wildlife.inView(x, y, z, -0.2F) && dx * dx + dz * dz > 4) continue;
                float yaw = Mth.rotLerp(pt, Insects.yawO[i], Insects.yaw[i]) * Mth.DEG_TO_RAD;
                int light = Insects.light[i];
                int alpha = (int) (a * 255) << 24;
                float ph = Insects.phase[i], rt = Insects.rate[i];
                switch (k) {
                    case WildKinds.BUTTERFLY -> {
                        boolean rest = Insects.state[i] == Insects.REST;
                        float flap = rest ? 50F + 35F * Mth.sin(age * 0.12F + ph) : 30F + 55F * Mth.sin(age * 2.4F * rt + ph);
                        int v = Insects.variant[i];
                        // drawn near twice life size, like the birds: at life size a butterfly is a few pixels at ten blocks
                        wings(dx, dy, dz, yaw, flap, 0.3F, 0.3F, v * 16, 0, alpha | 0xFFFFFF, light);
                        body(dx, dy, dz, yaw, 0.26F, 0.05F, 23, 50, 25, 62, alpha | 0xFFFFFF, light);
                    }
                    case WildKinds.MOTH -> {
                        float flap = 20F + 45F * Mth.sin(age * 3.1F * rt + ph);
                        wings(dx, dy, dz, yaw, flap, 0.22F, 0.22F, 0, 16, alpha | 0xFFFFFF, light);
                        body(dx, dy, dz, yaw, 0.19F, 0.045F, 23, 50, 25, 62, alpha | 0xFFFFFF, light);
                    }
                    case WildKinds.DRAGONFLY -> {
                        float flap = 6F + 10F * Mth.sin(age * 5.3F + ph);
                        wings(dx, dy + 0.01, dz, yaw, flap, 0.3F, 0.32F, 16, 16, alpha | 0xFFFFFF, light);
                        int u = Insects.variant[i] % 2 == 0 ? 2 : 10;
                        body(dx, dy, dz, yaw, 0.46F, 0.07F, u, 49, u + 4, 64, alpha | 0xFFFFFF, light);
                    }
                    case WildKinds.BEE -> {
                        body(dx, dy, dz, yaw, 0.17F, 0.11F, 34, 20, 39, 29, alpha | 0xFFFFFF, light);
                        float flap = (Wildlife.now + i) % 2 == 0 ? 15F : 55F;
                        wingsCell(dx, dy + 0.03, dz, yaw, flap, 0.11F, 0.12F, 41, 19, 47, 26, alpha | 0xFFFFFF, light);
                    }
                    case WildKinds.FIREFLY -> {
                        float b = blink(i);
                        body(dx, dy, dz, yaw, 0.11F, 0.06F, 54, 21, 58, 28, alpha | 0xFFFFFF, b > 0.2F ? LightCoordsUtil.FULL_BRIGHT : light);
                    }
                    case WildKinds.GNATS -> gnats(dx, dy, dz, i, (int) (a * 150) << 24 | 0x3A3830, light);
                    case WildKinds.STRIDER -> flat(dx, dy, dz, yaw, 0.32F, 32, 32, 48, 48, alpha | 0xFFFFFF, light);
                    default -> { }
                }
            }
            ripples();
            flock();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[wildlife] insects failed", ex);
            Insects.clear();
        }
    };

    private static final SubmitNodeCollector.CustomGeometryRenderer LIGHTS = (pose, consumer) -> {
        try {
            vc = consumer;
            for (int i = 0; i < Insects.CAP; i++) {
                if (Insects.kind[i] != WildKinds.FIREFLY) continue;
                float a = Mth.lerp(pt, Insects.alphaO[i], Insects.alpha[i]);
                float b = blink(i) * a;
                if (b <= 0.02F) continue;
                double x = Mth.lerp(pt, Insects.xo[i], Insects.x[i]), y = Mth.lerp(pt, Insects.yo[i], Insects.y[i]),
                        z = Mth.lerp(pt, Insects.zo[i], Insects.z[i]);
                float dx = (float) (x - cx), dy = (float) (y - cy), dz = (float) (z - cz);
                if (dx * dx + dy * dy + dz * dz > 56 * 56) continue;
                boolean lumen = Insects.variant[i] == 4;
                float r = lumen ? 0.45F : 0.78F, g = lumen ? 0.92F : 1.0F, bl = lumen ? 1.0F : 0.32F;
                disc(dx, dy, dz, 0.75F, r, g, bl, 0.35F * b);
                disc(dx, dy, dz, 0.28F, r, g, bl, 0.6F * b);
                disc(dx, dy, dz, 0.1F, 1F, 1F, 0.85F, b);
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[wildlife] firefly light failed", ex);
        }
    };

    /** A firefly's light: a slow swell and fade every few seconds, its own rhythm, an ember between. */
    static float blink(int i) {
        float period = 40F + 40F / Insects.rate[i];
        float t = (age + Insects.phase[i] * 40F) % period;
        // a slow swell and fade over two fifths of its rhythm, then a low ember
        float lit = period * 0.42F;
        float flash = t < lit ? Mth.sin(t / lit * Mth.PI) : 0F;
        // some answer with a second, smaller flash
        if (Insects.slot[i] % 3 == 0 && t > lit + 4F && t < lit + 14F) flash = Math.max(flash, 0.6F * Mth.sin((t - lit - 4F) / 10F * Mth.PI));
        return Math.max(0.1F, flash);
    }

    // ---- shapes -------------------------------------------------------------------------------------------------------

    private static void v(float x, float y, float z, int col, float u, float vv, int light) {
        vc.addVertex(x, y, z).setColor(col).setUv(u * T, vv * T).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0F, 1F, 0F);
    }

    /** Two wings from a 16 x 16 cell (cell's left edge the hinge, its top the front), opened {@code flap} degrees up. */
    private static void wings(double dx, double dy, double dz, float yaw, float flap, float len, float span, int cu, int cv, int col, int light) {
        wingsCell(dx, dy, dz, yaw, flap, len, span, cu, cv, cu + 16, cv + 16, col, light);
    }

    private static void wingsCell(double dx, double dy, double dz, float yaw, float flap, float len, float span,
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

    /** A body along its heading: a flat strip and an upright one, crossed, from a cell's rect. */
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

    /** Lying flat on the water: a strider, a ripple. */
    private static void flat(double dx, double dy, double dz, float yaw, float size, int u0, int v0, int u1, int v1, int col, int light) {
        float fx = -Mth.sin(yaw) * size * 0.5F, fz = Mth.cos(yaw) * size * 0.5F, rx = Mth.cos(yaw) * size * 0.5F, rz = Mth.sin(yaw) * size * 0.5F;
        float x = (float) dx, y = (float) dy, z = (float) dz;
        v(x + fx - rx, y, z + fz - rz, col, u0, v0, light);
        v(x - fx - rx, y, z - fz - rz, col, u0, v1, light);
        v(x - fx + rx, y, z - fz + rz, col, u1, v1, light);
        v(x + fx + rx, y, z + fz + rz, col, u1, v0, light);
    }

    /** A speck facing the camera. */
    private static void speck(float x, float y, float z, float half, int col, int light) {
        float rx = RIGHT.x * half, ry = RIGHT.y * half, rz = RIGHT.z * half, ux = UP.x * half, uy = UP.y * half, uz = UP.z * half;
        v(x - rx - ux, y - ry - uy, z - rz - uz, col, 49, 35, light);
        v(x - rx + ux, y - ry + uy, z - rz + uz, col, 49, 33, light);
        v(x + rx + ux, y + ry + uy, z + rz + uz, col, 51, 33, light);
        v(x + rx - ux, y + ry - uy, z + rz - uz, col, 51, 35, light);
    }

    /** A gnat swarm: a score of specks, each on its own little orbit round the swarm's heart. */
    private static void gnats(double dx, double dy, double dz, int i, int col, int light) {
        float t = age * 0.35F;
        for (int j = 0; j < 22; j++) {
            float a = j * 2.399F + i, sa = 0.7F + (j % 5) * 0.23F, sb = 0.9F + (j % 3) * 0.31F;
            float ox = Mth.sin(t * sa + a) * (0.25F + (j % 4) * 0.09F);
            float oy = Mth.sin(t * sb * 1.3F + a * 1.7F) * 0.32F;
            float oz = Mth.cos(t * sa * 1.1F + a * 0.6F) * (0.25F + (j % 3) * 0.1F);
            speck((float) dx + ox, (float) dy + oy, (float) dz + oz, 0.007F, col, light);
        }
    }

    private static void ripples() {
        for (int r = 0; r < Insects.RIPPLES; r++) {
            float t = (age - Insects.rborn[r]) / 28F;
            if (t < 0F || t > 1F) continue;
            double dx = Insects.rx[r] - cx, dy = Insects.ry[r] - cy, dz = Insects.rz[r] - cz;
            if (dx * dx + dz * dz > 40 * 40) continue;
            float size = Insects.rsize[r] * (0.25F + 0.75F * Mth.sqrt(t));
            int a = (int) (110 * (1F - t)) << 24;
            flat(dx, dy, dz, 0F, size, 16, 32, 32, 48, a | 0xE8F0F4, skyLight);
        }
    }

    /** The far flock: each bird a body and two beating wings, the flock moving as one with each bird's own drift. */
    private static void flock() {
        if (!Insects.flock) return;
        float t = (age - Insects.fborn);
        double bx = Insects.fx + Insects.fdx * Insects.fspeed * t, bz = Insects.fz + Insects.fdz * Insects.fspeed * t, by = Insects.fy;
        float yaw = (float) Math.atan2(-Insects.fdx, Insects.fdz);
        boolean starlings = Insects.fkind == 1;
        float fx = (float) Insects.fdx, fz = (float) Insects.fdz;
        for (int j = 0; j < Insects.fcount; j++) {
            float a = j * 2.399F;
            double ox, oy, oz;
            if (starlings) {
                // a murmuration in small: the cloud turns and breathes
                float rr = 4F + 3F * Mth.sin(t * 0.03F + a) + (j % 5);
                float w = t * 0.05F + a;
                ox = Mth.cos(w) * rr;
                oz = Mth.sin(w) * rr * 0.6F;
                oy = Mth.sin(t * 0.04F + a * 1.3F) * 3F;
            } else {
                ox = Mth.sin(a * 3.1F) * 5F + Mth.sin(t * 0.05F + a) * 0.6F;
                oz = Mth.cos(a * 1.7F) * 6F;
                oy = Mth.sin(a * 2.3F) * 2F + Mth.sin(t * 0.3F + a) * 0.3F;
            }
            double dx = bx + ox - cx, dy = by + oy - cy, dz = bz + oz - cz;
            if (dx * dx + dy * dy + dz * dz > 160 * 160) continue;
            // finches bound: three beats and a closed-wing dip
            float ph = (t * (starlings ? 0.9F : 1.3F) + a) % (Mth.TWO_PI * 2F);
            float flap = starlings ? 25F * Mth.sin(t * 1.5F + a) : ph < Mth.TWO_PI * 1.4F ? 40F * Mth.sin(t * 2.2F + a) : -60F;
            int col = 0xFF1E1C1C;
            wingsCell(dx, dy, dz, yaw, flap, 0.2F, 0.32F, 32, 53, 44, 58, col, skyLight);
            body(dx, dy, dz, yaw, 0.3F, 0.08F, 54, 52, 58, 60, col, skyLight);
        }
    }

    /** A soft round light facing the camera: bright at the middle, nothing at the rim. */
    private static void disc(float x, float y, float z, float radius, float r, float g, float b, float a) {
        int mid = ARGBc(a, r, g, b), rim = ARGBc(0F, r, g, b);
        int n = 10;
        for (int k = 0; k < n; k++) {
            float a0 = k * Mth.TWO_PI / n, a1 = (k + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * radius, s0 = Mth.sin(a0) * radius, c1 = Mth.cos(a1) * radius, s1 = Mth.sin(a1) * radius;
            vc.addVertex(x, y, z).setColor(mid);
            vc.addVertex(x, y, z).setColor(mid);
            vc.addVertex(x + RIGHT.x * c0 + UP.x * s0, y + RIGHT.y * c0 + UP.y * s0, z + RIGHT.z * c0 + UP.z * s0).setColor(rim);
            vc.addVertex(x + RIGHT.x * c1 + UP.x * s1, y + RIGHT.y * c1 + UP.y * s1, z + RIGHT.z * c1 + UP.z * s1).setColor(rim);
        }
    }

    private static int ARGBc(float a, float r, float g, float b) {
        return (int) (Mth.clamp(a, 0F, 1F) * 255) << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }
}
