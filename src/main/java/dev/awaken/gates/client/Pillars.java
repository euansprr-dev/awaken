package dev.awaken.gates.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Light pillars over every known gate, coloured by rank, visible up to 1500 blocks. Pillars beyond a safe distance are
 * drawn at a clamped distance along the view ray and scaled down by the same factor, so they look exactly as far away as
 * they are but never fall past the far plane or into the fog.
 */
public final class Pillars {
    private Pillars() {}

    private record Draw(double dx, double dy, double dz, float scale, int height, int core, int glow, float solid, float halo) {}

    private static final ContextKey<List<Draw>> KEY = new ContextKey<>(Awaken.id("gate_pillars"));
    public static final int HEIGHT = 900;

    static void onExtract(ExtractLevelRenderStateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        List<GatePayloads.Beacon> beacons = ClientGates.beacons();
        if (beacons.isEmpty()) return;
        Vec3 cam = e.getCamera().position();
        double clamp = Math.max(48, mc.options.getEffectiveRenderDistance() * 16 * 0.7);
        List<Draw> out = new ArrayList<>();
        for (GatePayloads.Beacon b : beacons) {
            // the beam rises from the top of the vortex, which grows with rank
            var size = dev.awaken.gates.GateVortex.size(b.rank());
            double bx = b.pos().getX() + 0.5, by = b.pos().getY() + size.centreY() + size.ry() * 1.08, bz = b.pos().getZ() + 0.5;
            double rx = bx - cam.x, ry = by - cam.y, rz = bz - cam.z;
            double dist = Math.sqrt(rx * rx + rz * rz + ry * ry);
            if (dist > GatePayloads.BEACON_RANGE + 100) continue;
            float k = dist > clamp ? (float) (clamp / dist) : 1F;
            float widen = (float) Math.max(1.0, Math.sqrt(rx * rx + rz * rz) / 96.0);
            int colour = ClientGates.rankColour(b.rank());
            int core = colour, glow = colour;
            if (b.rank() == 6) {
                core = 0xFF0C0A08;
                glow = 0xFFD4AF37;
            }
            if (b.broken()) {
                core = 0xFF7A1018;
                glow = 0xFF5A5A5A;
            }
            if ((b.flags() & GatePayloads.Beacon.RED) != 0 && !b.broken()) glow = 0xFFFF2030;
            out.add(new Draw(rx * k - 0.5, ry * k, rz * k - 0.5, k, HEIGHT, core, glow, 0.22F * widen, 0.32F * widen));
        }
        e.getRenderState().setRenderData(KEY, out);
    }

    /**
     * The pillar itself (Euan, 2026-10-26: vanilla's beacon beam read as "a weird boxed outline" over Carillon, its halo a
     * flat translucent square column): soft light, never a box. Three ribbons turned to face the camera about the
     * pillar's axis, each fading from its middle to nothing at its edges (so no edge shows from any side): a near-white
     * core, the rank's glow, a wide faint haze; all fade in at the foot and out toward the top. Bands of light rise up the
     * column, and it breathes. Additive in the Gate Rig's light (depth-tested, no depth written: hills hide it, Lens's
     * bloom takes the core), full at night, dimmer by day. One pass for every pillar; nothing allocated here.
     */
    static void onSubmit(SubmitCustomGeometryEvent e) {
        List<Draw> draws = e.getLevelRenderState().getRenderData(KEY);
        if (draws == null || draws.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float time = (e.getLevelRenderState().gameTime % 24000) + e.getLevelRenderState().worldPartialTicks;
        float dark = mc.level == null ? 1F : mc.level.getSkyDarken() / 11F;
        float day = 0.55F + 0.45F * dark;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RigPipelines.light(), (pose, vc) -> {
            for (Draw d : draws) pillar(vc, d, time, day);
        });
    }

    /** Rows up the column (denser near the foot, where it is seen from close by). */
    private static final float[] ROWS = {0F, 0.004F, 0.012F, 0.025F, 0.045F, 0.07F, 0.1F, 0.14F, 0.19F, 0.25F, 0.32F, 0.4F, 0.5F, 0.6F,
            0.7F, 0.8F, 0.9F, 1F};

    private static void pillar(VertexConsumer vc, Draw d, float time, float day) {
        float k = d.scale();
        float bx = (float) d.dx() + 0.5F * k, by = (float) d.dy(), bz = (float) d.dz() + 0.5F * k;
        // the ribbons turn about the pillar's axis to face the camera (the camera is at the origin)
        float tx = -bx, tz = -bz, tl = (float) Math.sqrt(tx * tx + tz * tz);
        if (tl < 1e-4F) return;
        float rx = -tz / tl, rz = tx / tl;
        float h = d.height() * k;
        float breath = 0.9F + 0.1F * Mth.sin(time * 0.045F + bx * 0.01F);
        int core = brighten(d.core()), glow = d.glow();
        // core: thin and hot; glow: the rank's colour; haze: wide and faint (widths grow with distance, as before)
        ribbon(vc, bx, by, bz, rx, rz, h, d.solid() * 0.55F * k, core, 0.95F * day * breath, time, 0.6F);
        ribbon(vc, bx, by, bz, rx, rz, h, d.halo() * 2.4F * k, glow, 0.42F * day * breath, time, 1.0F);
        ribbon(vc, bx, by, bz, rx, rz, h, d.halo() * 7.5F * k, glow, 0.12F * day, time, 0.4F);
    }

    /** One soft ribbon: two halves per row, the centre line at its strength, each outer edge at nothing. */
    private static void ribbon(VertexConsumer vc, float bx, float by, float bz, float rx, float rz, float h, float half,
                               int argb, float strength, float time, float shimmer) {
        float pa = 0;
        for (int i = 0; i < ROWS.length; i++) {
            float f = ROWS[i], y = by + f * h;
            // fade in over the foot (the first few blocks), out over the top 40%; rising bands of light
            float foot = Mth.clamp(f * h / 6F, 0F, 1F), top = 1F - Mth.smoothstep(Mth.clamp((f - 0.6F) / 0.4F, 0F, 1F));
            float bands = 1F - shimmer * 0.22F * (0.5F + 0.5F * Mth.sin(f * h * 0.08F - time * 0.09F));
            float a = strength * foot * top * bands;
            if (i > 0) {
                float y0 = by + ROWS[i - 1] * h;
                int c0 = col(argb, pa), c1 = col(argb, a), z0 = col(argb, 0F);
                // left half: edge (0) to centre (a)
                vc.addVertex(bx - rx * half, y0, bz - rz * half).setColor(z0);
                vc.addVertex(bx, y0, bz).setColor(c0);
                vc.addVertex(bx, y, bz).setColor(c1);
                vc.addVertex(bx - rx * half, y, bz - rz * half).setColor(z0);
                // right half
                vc.addVertex(bx, y0, bz).setColor(c0);
                vc.addVertex(bx + rx * half, y0, bz + rz * half).setColor(z0);
                vc.addVertex(bx + rx * half, y, bz + rz * half).setColor(z0);
                vc.addVertex(bx, y, bz).setColor(c1);
            }
            pa = a;
        }
    }

    /** The colour with the strength as its alpha (the additive blend weighs the colour by it). */
    private static int col(int rgb, float a) {
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** Toward white: the core burns hotter than its glow (a dark core, the S-rank's, stays dark). */
    private static int brighten(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        if (r + g + b < 120) return c;
        return 0xFF000000 | Math.min(255, r + (255 - r) * 2 / 3) << 16 | Math.min(255, g + (255 - g) * 2 / 3) << 8 | Math.min(255, b + (255 - b) * 2 / 3);
    }
}
