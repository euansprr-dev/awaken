package dev.awaken.gates.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.net.RigPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Gate Rig draws (SPEC §17 "Gate Rig"), in two kinds of geometry: light (the beam, its flares, the cracks
 * that glow before a row falls, a ceiling about to give) in {@link RigPipelines#light()}, and moving blocks (a tile
 * dropping into the pit, a row knitting back, a mirror swinging round, a block of ceiling falling) drawn with the
 * game's own block models, so they look exactly like the blocks the server swapped.
 */
final class RigFx {
    private RigFx() {}

    /**
     * A block in motion, from the moment its cue arrived (client game time). {@code seed}: a per-cell jitter (for a press:
     * how many ticks it takes to come down; it rests at the bottom after that).
     */
    record Move(int kind, BlockState state, BlockPos pos, long start, int ticks, float a, int theme, float seed) {}

    /** A glow on cells (a warning, a flash), from the moment its cue arrived. */
    record Glow(int kind, List<BlockPos> cells, long start, int ticks, int theme) {}

    static final List<Move> MOVES = new ArrayList<>();
    static final List<Glow> GLOWS = new ArrayList<>();

    private static double cx, cy, cz;
    private static float pt;
    private static long now;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    static void clear() {
        MOVES.clear();
        GLOWS.clear();
    }

    /** Drops what has finished playing (client tick). */
    static void expire(long time) {
        MOVES.removeIf(m -> time - m.start > m.ticks + 1);
        GLOWS.removeIf(g -> time - g.start > g.ticks + 1);
    }

    // ---- colours ----------------------------------------------------------------------------------------------------

    /** The theme's light: what its beam, its cracks and its wells glow with (0xRRGGBB). */
    static int light(int theme) {
        return switch (Theme.values()[Math.floorMod(theme, Theme.values().length)]) {
            case EMBER_MINES -> 0xFF7A22;
            case GLASS_DESERT -> 0xFFC15A;
            case BELL_CRYPT -> 0x7FE8FF;
            case MIRROR_PALACE -> 0xFFE7A8;
            case PRECURSOR_LAB -> 0x6FF6FF;
            case NIGHTMARE -> 0xB07CFF;
            case SKY_ARCHIPELAGO -> 0xBFE6FF;
            case CLOCKWORK_FOUNDRY -> 0xFFA63A;
            case FROST_MONASTERY -> 0x9FDCFF;
            default -> 0xFFF1D6;
        };
    }

    static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Toward white by {@code w} (the beam's core is near white, so Lens blooms it). */
    static int hot(int rgb, float w) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * w);
        g = (int) (g + (255 - g) * w);
        b = (int) (b + (255 - b) * w);
        return (r << 16) | (g << 8) | b;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (RigClient.beams.isEmpty() && MOVES.isEmpty() && GLOWS.isEmpty() && !RigClient.hasRings())) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        now = level.getGameTime();
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        out.submitCustomGeometry(ps, RigPipelines.light(), (pose, vc) -> {
            try {
                for (RigClient.Beam b : RigClient.beams) beam(vc, b);
                for (Glow g : GLOWS) glow(vc, g);
                rings(vc);
            } catch (RuntimeException ex) {
                Awaken.LOG.error("[gates] rig light failed", ex);
            }
        });
        for (Move m : MOVES) move(ps, out, level, m);
    }

    private static float age(long start) {
        return now - start + pt;
    }

    // ---- light ------------------------------------------------------------------------------------------------------

    /** The beam: a white-hot core in a glow of the theme's colour, a soft halo round it, flares where it turns and lands. */
    private static void beam(VertexConsumer vc, RigClient.Beam b) {
        float t = (now + pt) * 0.15F;
        float pulse = 0.85F + 0.15F * Mth.sin(t * 2.3F);
        int col = light(b.theme());
        for (int i = 0; i + 5 < b.points().length; i += 6) {
            double[] p = b.points();
            Vec3 a = new Vec3(p[i], p[i + 1], p[i + 2]), z = new Vec3(p[i + 3], p[i + 4], p[i + 5]);
            ribbon(vc, a, z, 1.4F, argb(col, 0.10F * pulse));
            ribbon(vc, a, z, 0.5F, argb(col, 0.42F * pulse));
            ribbon(vc, a, z, 0.14F, argb(hot(col, 0.85F), 0.95F));
            flare(vc, z, 0.6F * pulse, argb(hot(col, 0.7F), 0.8F));
            flare(vc, z, 1.8F, argb(col, 0.18F));
        }
        if (b.points().length >= 3) flare(vc, new Vec3(b.points()[0], b.points()[1], b.points()[2]), 0.9F * pulse, argb(hot(col, 0.6F), 0.9F));
        if (b.lit()) {
            double[] p = b.points();
            Vec3 end = new Vec3(p[p.length - 3], p[p.length - 2], p[p.length - 1]);
            flare(vc, end, 2.6F * pulse, argb(hot(col, 0.5F), 0.55F));
        }
    }

    private static void glow(VertexConsumer vc, Glow g) {
        float a = age(g.start) / Math.max(1, g.ticks);
        if (a < 0 || a > 1) return;
        int col = light(g.theme);
        switch (g.kind) {
            case RigPayloads.Cue.WARN -> {
                // cracks open across the tiles' tops and brighten until they go
                float flicker = 0.7F + 0.3F * Mth.sin((now + pt) * 2.7F);
                for (BlockPos c : g.cells) crack(vc, c, argb(hot(col, a * 0.5F), (0.25F + 0.75F * a) * flicker), a);
            }
            case RigPayloads.Cue.CAVE -> {
                for (BlockPos c : g.cells) ceilingCrack(vc, c, argb(0xFFD9A0, 0.15F + 0.5F * a), a);
            }
            case RigPayloads.Cue.HAZARD -> {
                // the floor under a press: chevrons of the theme's light pulsing faster and brighter until it comes down
                float beat = 0.55F + 0.45F * Mth.sin((now + pt) * (0.6F + 1.6F * a));
                for (BlockPos c : g.cells) hazard(vc, c, argb(hot(col, 0.3F * a), (0.18F + 0.6F * a) * beat));
            }
            case RigPayloads.Cue.SHATTER, RigPayloads.Cue.IGNITE -> {
                float f = (1 - a) * (1 - a);
                for (BlockPos c : g.cells) flare(vc, Vec3.atCenterOf(c), 1.4F * (0.5F + a), argb(hot(col, 0.6F), 0.8F * f));
            }
            case RigPayloads.Cue.SURGE -> {
                // the whole room tints before it turns over: a sheet of light rising up its middle
                for (BlockPos c : g.cells) flare(vc, Vec3.atCenterOf(c), 6F * a, argb(col, 0.12F * Mth.sin(a * Mth.PI)));
            }
            default -> {}
        }
    }

    /** Glowing cracks over a tile's top: a jagged cross and its edges, wider as it nears its fall. */
    private static void crack(VertexConsumer vc, BlockPos c, int argb, float a) {
        double y = c.getY() + 1.004;
        double x0 = c.getX(), z0 = c.getZ();
        float w = 0.03F + 0.06F * a;
        int seed = (int) (c.asLong() * 31);
        double j1 = ((seed >> 3) & 7) / 16.0, j2 = ((seed >> 6) & 7) / 16.0;
        strip(vc, x0 + 0.1, y, z0 + 0.2 + j1, x0 + 0.5 + j2 * 0.4, y, z0 + 0.55, w, argb);
        strip(vc, x0 + 0.5 + j2 * 0.4, y, z0 + 0.55, x0 + 0.92, y, z0 + 0.85 - j1 * 0.5, w, argb);
        strip(vc, x0 + 0.5 + j2 * 0.4, y, z0 + 0.55, x0 + 0.35 + j1, y, z0 + 0.95, w * 0.8F, argb);
        strip(vc, x0 + 0.6, y, z0 + 0.08, x0 + 0.5 + j2 * 0.4, y, z0 + 0.55, w * 0.7F, argb);
    }

    /** Two chevrons and a rim over a tile's top. */
    private static void hazard(VertexConsumer vc, BlockPos c, int argb) {
        double y = c.getY() + 1.006;
        double x0 = c.getX(), z0 = c.getZ();
        strip(vc, x0 + 0.15, y, z0 + 0.2, x0 + 0.5, y, z0 + 0.45, 0.07F, argb);
        strip(vc, x0 + 0.5, y, z0 + 0.45, x0 + 0.85, y, z0 + 0.2, 0.07F, argb);
        strip(vc, x0 + 0.15, y, z0 + 0.6, x0 + 0.5, y, z0 + 0.85, 0.07F, argb);
        strip(vc, x0 + 0.5, y, z0 + 0.85, x0 + 0.85, y, z0 + 0.6, 0.07F, argb);
        int rim = argb & 0x00FFFFFF | ((argb >>> 25) << 24);
        strip(vc, x0 + 0.03, y, z0 + 0.03, x0 + 0.97, y, z0 + 0.03, 0.03F, rim);
        strip(vc, x0 + 0.03, y, z0 + 0.97, x0 + 0.97, y, z0 + 0.97, 0.03F, rim);
    }

    // ---- the Gauntlet's rings -------------------------------------------------------------------------------------

    /** Ring segments, and how far off a ring is still drawn. */
    static final int RING_SEGMENTS = 40;
    static final double RING_SEEN = 64;

    /**
     * Each Gauntlet ring: a hoop of light standing over its row of the course, wider than the course, its foot just under
     * the floor. Your own run lights the rings you've passed gold; the next one breathes.
     */
    private static void rings(VertexConsumer vc) {
        int passed = RigRunHud.passed();
        for (RigClient.RingRoom room : RigClient.ringRooms()) {
            int col = light(room.theme());
            for (int i = 0; i < room.rings().size(); i++) {
                double[] q = room.rings().get(i);
                double mx = q[0], my = q[1], mz = q[2];
                if ((mx - cx) * (mx - cx) + (my - cy) * (my - cy) + (mz - cz) * (mz - cz) > RING_SEEN * RING_SEEN) continue;
                boolean done = i <= passed, next = RigRunHud.running() && i == passed + 1;
                float pulse = next ? 0.75F + 0.25F * Mth.sin((now + pt) * 0.25F) : 1F;
                int c = done ? 0xFFD27A : col;
                ring(vc, q, 1.2F, argb(c, (done ? 0.10F : 0.18F) * pulse));
                ring(vc, q, 0.45F, argb(hot(c, 0.4F), (done ? 0.35F : 0.6F) * pulse));
                ring(vc, q, 0.13F, argb(hot(c, 0.85F), 0.95F * pulse));
            }
        }
    }

    /** One hoop: {@code q} = centre (x, y, z), the unit across the course (ax, az), the radius. */
    private static void ring(VertexConsumer vc, double[] q, float width, int argb) {
        double r = q[5];
        Vec3 prev = null;
        for (int k = 0; k <= RING_SEGMENTS; k++) {
            double t = k * Math.PI * 2 / RING_SEGMENTS;
            double u = Math.cos(t) * r, v = Math.sin(t) * r;
            Vec3 p = new Vec3(q[0] + q[3] * u, q[1] + v, q[2] + q[4] * u);
            if (prev != null) ribbon(vc, prev, p, width, argb);
            prev = p;
        }
    }

    private static void ceilingCrack(VertexConsumer vc, BlockPos c, int argb, float a) {
        double y = c.getY() - 0.004;
        double x0 = c.getX(), z0 = c.getZ();
        float w = 0.04F + 0.05F * a;
        strip(vc, x0 + 0.15, y, z0 + 0.3, x0 + 0.85, y, z0 + 0.7, w, argb);
        strip(vc, x0 + 0.3, y, z0 + 0.85, x0 + 0.7, y, z0 + 0.15, w, argb);
    }

    /** A flat strip on a horizontal plane from (x0, z0) to (x1, z1), half-width w. */
    private static void strip(VertexConsumer vc, double x0, double y, double z0, double x1, double y1, double z1, float w, int argb) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < 1e-6) return;
        double nx = -dz / len * w, nz = dx / len * w;
        quad(vc, x0 + nx, y, z0 + nz, x1 + nx, y1, z1 + nz, x1 - nx, y1, z1 - nz, x0 - nx, y, z0 - nz, argb, argb);
    }

    /** A ribbon between two points turned to the camera, bright on its axis and fading to nothing at its edges. */
    private static void ribbon(VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb) {
        Vec3 d = b.subtract(a);
        Vec3 view = a.add(d.scale(0.5)).subtract(cx, cy, cz);
        Vec3 n = d.cross(view);
        double len = n.length();
        if (len < 1e-6) return;
        n = n.scale(width * 0.5 / len);
        int rim = argb & 0x00FFFFFF;
        quad(vc, a.x, a.y, a.z, b.x, b.y, b.z, b.x + n.x, b.y + n.y, b.z + n.z, a.x + n.x, a.y + n.y, a.z + n.z, argb, rim);
        quad(vc, a.x, a.y, a.z, b.x, b.y, b.z, b.x - n.x, b.y - n.y, b.z - n.z, a.x - n.x, a.y - n.y, a.z - n.z, argb, rim);
    }

    /** A soft round glow facing the camera: bright at its heart, nothing at its rim. */
    private static void flare(VertexConsumer vc, Vec3 p, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rim = argb & 0x00FFFFFF;
        int n = r < 1F ? 6 : 12;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            quad(vc, p.x, p.y, p.z, p.x, p.y, p.z,
                    p.x + RIGHT.x * c1 + UP.x * s1, p.y + RIGHT.y * c1 + UP.y * s1, p.z + RIGHT.z * c1 + UP.z * s1,
                    p.x + RIGHT.x * c0 + UP.x * s0, p.y + RIGHT.y * c0 + UP.y * s0, p.z + RIGHT.z * c0 + UP.z * s0, argb, rim);
        }
    }

    /** One quad, camera-relative: the first two corners take {@code inner}, the last two {@code outer}. */
    private static void quad(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        vc.addVertex((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).setColor(inner);
        vc.addVertex((float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).setColor(inner);
        vc.addVertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).setColor(outer);
        vc.addVertex((float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz)).setColor(outer);
    }

    // ---- moving blocks ----------------------------------------------------------------------------------------------

    /** A tile shudders, then drops into the pit tumbling; this deep it is gone (the pit's floor eats it). */
    static final double FALL_DEPTH = 3.0, FALL_G = 0.07;
    static final int SHUDDER = 3;

    private static void move(PoseStack ps, SubmitNodeCollector out, ClientLevel level, Move m) {
        float t = age(m.start);
        if (t < 0 || t > m.ticks) return;
        double dx = 0, dy = 0, dz = 0;
        float rx = 0, ry = 0;
        switch (m.kind) {
            case RigPayloads.Cue.FALL -> {
                if (t < SHUDDER) {
                    dx = Mth.sin(t * 9 + m.seed * 7) * 0.035;
                    dz = Mth.cos(t * 11 + m.seed * 5) * 0.035;
                } else {
                    float f = t - SHUDDER;
                    dy = -0.5 * FALL_G * f * f;
                    if (-dy > FALL_DEPTH) return;
                    rx = f * (6 + m.seed * 8);
                    ry = f * (m.seed - 0.5F) * 10;
                }
            }
            case RigPayloads.Cue.RISE -> {
                float u = Math.min(1, t / Math.max(1, m.ticks));
                float ease = 1 - (1 - u) * (1 - u) * (1 - u);
                dy = -FALL_DEPTH * (1 - ease);
            }
            case RigPayloads.Cue.SPIN -> {
                float u = Math.min(1, t / Math.max(1, m.ticks));
                float ease = u * u * (3 - 2 * u);
                // drawn at its new turn, swung back to the old one (a quarter counter-clockwise) and unwinding to rest
                ry = 90F * (1 - ease);
            }
            case RigPayloads.Cue.DROP -> {
                dy = -Math.min(m.a, 0.5 * 0.04 * t * t);
                rx = t * 1.5F * (m.seed - 0.5F);
            }
            case RigPayloads.Cue.PRESS -> {
                // out of the ceiling, gathering speed, and a jolt as it lands
                float u = Math.min(1, t / Math.max(1, m.seed));
                dy = m.a * (1 - u * u);
                if (u >= 1) dy = -0.04 * Math.max(0, 1 - (t - m.seed) / 2.5);
            }
            case RigPayloads.Cue.LIFT -> {
                float u = Math.min(1, t / Math.max(1, m.ticks));
                dy = m.a * u * u * (3 - 2 * u);
            }
            default -> {
                return;
            }
        }
        MovingBlockRenderState mb = new MovingBlockRenderState();
        mb.blockState = m.state;
        mb.blockPos = m.pos;
        mb.randomSeedPos = m.pos;
        mb.lightEngine = level.getLightEngine();
        mb.cardinalLighting = level.cardinalLighting();
        mb.biome = level.getBiome(m.pos);
        ps.pushPose();
        ps.translate(m.pos.getX() - cx + dx + 0.5, m.pos.getY() - cy + dy + 0.5, m.pos.getZ() - cz + dz + 0.5);
        if (ry != 0) ps.rotateDegrees(Axis.YP, ry);
        if (rx != 0) ps.rotateDegrees(Axis.XP, rx);
        ps.translate(-0.5, -0.5, -0.5);
        out.submitMovingBlock(ps, mb, 0);
        ps.popPose();
    }
}
