package dev.awaken.gates.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
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

    static void onSubmit(SubmitCustomGeometryEvent e) {
        List<Draw> draws = e.getLevelRenderState().getRenderData(KEY);
        if (draws == null || draws.isEmpty()) return;
        PoseStack ps = e.getPoseStack();
        float time = (e.getLevelRenderState().gameTime % 4000) + e.getLevelRenderState().worldPartialTicks;
        for (Draw d : draws) {
            ps.pushPose();
            ps.translate(d.dx, d.dy, d.dz);
            ps.scale(d.scale, d.scale, d.scale);
            if (d.core == d.glow) {
                BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0F, time, 0, d.height,
                        d.core, d.solid, d.halo);
            } else {
                BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0F, time, 0, d.height,
                        d.core, d.solid, d.solid);
                BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BeaconRenderer.BEAM_LOCATION, 1.0F, -time * 0.7F, 0, d.height,
                        d.glow, d.solid * 0.4F, d.halo * 1.25F);
            }
            ps.popPose();
        }
    }
}
