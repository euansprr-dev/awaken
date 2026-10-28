package dev.awaken.delve.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.delve.cave.CaveKind;
import dev.awaken.delve.cave.CaveNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Light shafts (SPEC §34.5): while the sun is up and the sky is clear, soft shafts of daylight fall into the dark at the
 * mouths and sinkholes near you (at most {@value #MAX}, nearest first, within {@value #REACH}): down through a sinkhole's
 * throat, slanting in under a mouth's arch, each a few camera-facing ribbons, brightest where the light comes in and
 * fading as it falls. Dust from the motes and the breath drifts through them. Gone at night and in rain.
 */
final class Shafts {
    private Shafts() {}

    static final int MAX = 3, REACH = 64;
    private static final int SUN = 0xFFF1D6;
    private record Shaft(Vec3 top, Vec3 bottom, float width, float strength) {}

    private static final List<Shaft> SHAFTS = new ArrayList<>();
    private static double cx, cy, cz;

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        SHAFTS.clear();
        if (level == null || mc.player == null || !SenseState.active) return;
        // daylight: the sky's darkening low, no rain
        float day = 1F - level.getSkyDarken() / 8F - level.getRainLevel(1F);
        if (day <= 0.05F) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        List<CaveNet.Site> sites = new ArrayList<>(CaveFx.sites());
        sites.sort((a, b) -> Double.compare(a.at().distToCenterSqr(cam), b.at().distToCenterSqr(cam)));
        for (CaveNet.Site s : sites) {
            if (SHAFTS.size() >= MAX) break;
            if (s.at().distToCenterSqr(cam) > REACH * REACH) break;
            if (s.kind() == CaveKind.SINKHOLE.ordinal()) {
                double x = s.at().getX() + 0.5, z = s.at().getZ() + 0.5, top = s.at().getY() + 1.5;
                SHAFTS.add(new Shaft(new Vec3(x, top, z), new Vec3(x + 1.5, top - Math.max(8, s.depth()), z + 0.8), Math.max(1.5F, s.radius() * 0.45F), day));
            } else if (s.kind() == CaveKind.MOUTH.ordinal() && (s.depth() >> 2 & 3) == 2) {
                // a doline: straight down its well
                double x = s.at().getX() + 0.5, z = s.at().getZ() + 0.5, top = s.at().getY() + 1.5;
                SHAFTS.add(new Shaft(new Vec3(x, top, z), new Vec3(x + 0.6, top - Math.max(8, s.depth() >> 4), z + 0.4), Math.max(1.2F, s.radius() * 0.35F), day));
            } else if (s.kind() == CaveKind.MOUTH.ordinal()) {
                // the mouth's way in is its frame's forward: rot 0 south, 1 west, 2 north, 3 east
                int rot = s.depth() & 3;
                double fx = rot == 1 ? -1 : rot == 3 ? 1 : 0, fz = rot == 0 ? 1 : rot == 2 ? -1 : 0;
                double x = s.at().getX() + 0.5, y = s.at().getY() + 3.5, z = s.at().getZ() + 0.5;
                SHAFTS.add(new Shaft(new Vec3(x - fx * 1.5, y + 1.5, z - fz * 1.5), new Vec3(x + fx * 9, y - 4, z + fz * 9), Math.max(1.2F, s.radius() * 0.5F), day * 0.8F));
            }
        }
        if (SHAFTS.isEmpty()) return;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), DelveRender.glow(), DRAW);
    }

    private static final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer DRAW = (pose, vc) -> {
        try {
            for (Shaft s : SHAFTS) {
                for (int k = 0; k < 3; k++) {
                    float w = s.width() * (1F - k * 0.28F);
                    ribbon(vc, s.top(), s.bottom(), w, 0.05F * s.strength() * (k == 2 ? 1.6F : 1F));
                }
            }
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[delve] light shafts failed", ex);
        }
    };

    /** A soft ribbon from top to bottom, turned to face the camera, bright at the top fading to nothing below. */
    private static void ribbon(VertexConsumer vc, Vec3 a, Vec3 b, float w, float alpha) {
        Vec3 axis = b.subtract(a);
        Vec3 mid = a.add(axis.scale(0.5));
        Vec3 side = axis.cross(new Vec3(mid.x - cx, mid.y - cy, mid.z - cz)).normalize().scale(w);
        if (!Double.isFinite(side.x)) return;
        int top = DelveRender.col(SUN, alpha), bottom = DelveRender.col(SUN, 0F);
        Vec3 m = a.add(axis.scale(0.35));
        int middle = DelveRender.col(SUN, alpha * 0.7F);
        quad(vc, a, m, side, top, middle);
        quad(vc, m, b, side, middle, bottom);
    }

    private static void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 side, int ca, int cb) {
        vc.addVertex((float) (a.x - side.x - cx), (float) (a.y - side.y - cy), (float) (a.z - side.z - cz)).setColor(DelveRender.col(ca & 0xFFFFFF, 0F));
        vc.addVertex((float) (a.x - cx), (float) (a.y - cy), (float) (a.z - cz)).setColor(ca);
        vc.addVertex((float) (b.x - cx), (float) (b.y - cy), (float) (b.z - cz)).setColor(cb);
        vc.addVertex((float) (b.x - side.x - cx), (float) (b.y - side.y - cy), (float) (b.z - side.z - cz)).setColor(DelveRender.col(cb & 0xFFFFFF, 0F));
        vc.addVertex((float) (a.x - cx), (float) (a.y - cy), (float) (a.z - cz)).setColor(ca);
        vc.addVertex((float) (a.x + side.x - cx), (float) (a.y + side.y - cy), (float) (a.z + side.z - cz)).setColor(DelveRender.col(ca & 0xFFFFFF, 0F));
        vc.addVertex((float) (b.x + side.x - cx), (float) (b.y + side.y - cy), (float) (b.z + side.z - cz)).setColor(DelveRender.col(cb & 0xFFFFFF, 0F));
        vc.addVertex((float) (b.x - cx), (float) (b.y - cy), (float) (b.z - cz)).setColor(cb);
    }

    static void reset() {
        SHAFTS.clear();
    }
}
