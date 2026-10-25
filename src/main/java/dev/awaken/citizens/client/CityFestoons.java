package dev.awaken.citizens.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Choir Square's festoons (M4 B3b, the B3a tour's "the square at night reads dark, its floor sparse"): strings of paper
 * lanterns from the eight lamp posts round the bowl, a ring between neighbours and four long strings crossing over the
 * Bell Stone, so the square has a canopy of light. By day the lanterns are coloured paper on dark rope; from dusk the
 * lamplighters' hour lights them (CityGlow draws each flame and its glow), until the small hours.
 * <p>Drawn on the client, no blocks (nothing to climb or catch on: they hang above the run's lines). The strings come from
 * the plan's lamps in the bowl and the lamp heads the server sends; built once.
 */
public final class CityFestoons {
    private CityFestoons() {}

    /** Every lantern: world x, y, z, colour (ARGB), seed; and every rope as a run of points. */
    private static float[] lanterns = new float[0], ground = new float[0];
    private static final List<float[]> ROPES = new ArrayList<>();
    private static boolean built;
    private static int light = LightCoordsUtil.FULL_BRIGHT, lightAge = -1000;
    private static final int[] PAPER = {0xFFB8352A, 0xFFE0A030, 0xFFEADAB0, 0xFFC4572E, 0xFFD88A2A};
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    static final float SPACING = 1.6F;

    static void reset() {
        built = false;
        lanterns = new float[0];
        ground = new float[0];
        ROPES.clear();
    }

    private static void build(ClientLevel level) {
        CityPlan plan = CityLife.plan();
        if (plan == null) return;
        long[] pos = CitizenFx.positions();
        byte[] kind = CitizenFx.kinds();
        int cx = CityLife.cx, cz = CityLife.cz;
        // the bowl's lamp posts in order round the Bell Stone (angle), their heads from the server's list
        List<double[]> posts = new ArrayList<>();
        for (int[] l : plan.lamps) {
            double r = Math.hypot(l[0] - plan.squareX, l[1] - plan.squareZ);
            if (r < 10 || r > 26) continue;
            for (int i = 0; i < pos.length; i++) {
                if (kind[i] != 5 || BlockPos.getX(pos[i]) != cx + l[0] || BlockPos.getZ(pos[i]) != cz + l[1]) continue;
                posts.add(new double[]{cx + l[0] + 0.5, BlockPos.getY(pos[i]) + 0.95, cz + l[1] + 0.5, Math.atan2(l[1] - plan.squareZ, l[0] - plan.squareX)});
            }
        }
        if (posts.size() < 4) return;
        built = true;
        posts.sort((a, b) -> Double.compare(a[3], b[3]));
        List<float[]> lan = new ArrayList<>();
        int n = posts.size();
        for (int k = 0; k < n; k++) string(posts.get(k), posts.get((k + 1) % n), 0.9, lan, k);
        for (int k = 0; k < n / 2; k++) string(posts.get(k), posts.get(k + n / 2), 2.2, lan, 100 + k);
        lanterns = new float[lan.size() * 5];
        for (int i = 0; i < lan.size(); i++) System.arraycopy(lan.get(i), 0, lanterns, i * 5, 5);
    }

    /** A rope sagging between two posts (a catenary near enough: a parabola), with lanterns hung along it. */
    private static void string(double[] a, double[] b, double sag, List<float[]> lan, int seed) {
        double len = Math.hypot(b[0] - a[0], b[2] - a[2]);
        int segs = Math.max(4, (int) (len / 0.8));
        float[] rope = new float[(segs + 1) * 3];
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs;
            rope[i * 3] = (float) Mth.lerp(t, a[0], b[0]);
            rope[i * 3 + 1] = (float) (Mth.lerp(t, a[1], b[1]) - sag * 4 * t * (1 - t));
            rope[i * 3 + 2] = (float) Mth.lerp(t, a[2], b[2]);
        }
        ROPES.add(rope);
        int count = (int) (len / SPACING);
        for (int i = 1; i < count; i++) {
            double t = i / (double) count;
            float x = (float) Mth.lerp(t, a[0], b[0]), z = (float) Mth.lerp(t, a[2], b[2]);
            float y = (float) (Mth.lerp(t, a[1], b[1]) - sag * 4 * t * (1 - t)) - 0.32F;
            lan.add(new float[]{x, y, z, Float.intBitsToFloat(PAPER[Math.floorMod(seed * 7 + i * 3, PAPER.length)]), seed * 31 + i});
        }
    }

    /** Lit from dusk till the small hours (the lamplighters' hours). */
    static boolean lit(ClientLevel level) {
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        return tod >= 12200 && tod < 19500;
    }

    // ---- the rope and the paper (lit by the world) -----------------------------------------------------------------

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !CityLife.known()) return;
        if (!built) {
            if (level.getGameTime() % 40 == 0) build(level);
            if (!built) return;
        }
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        if (lanterns.length == 0 || Math.hypot(lanterns[0] - cam.x, lanterns[2] - cam.z) > 140) return;
        RIGHT.set(1, 0, 0).rotate(state.cameraRenderState.orientation);
        UP.set(0, 1, 0).rotate(state.cameraRenderState.orientation);
        if (mc.player != null && mc.player.tickCount - lightAge > 40 || mc.player != null && mc.player.tickCount < lightAge) {
            lightAge = mc.player.tickCount;
            BlockPos p = BlockPos.containing(lanterns[0], lanterns[1], lanterns[2]);
            light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
        }
        boolean on = lit(level);
        double cx = cam.x, cy = cam.y, cz = cam.z;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.leash(), (pose, vc) -> {
            try {
                boolean first = true;
                for (float[] rope : ROPES) {
                    for (int i = 0; i < rope.length / 3; i++) {
                        float x = (float) (rope[i * 3] - cx), y = (float) (rope[i * 3 + 1] - cy), z = (float) (rope[i * 3 + 2] - cz);
                        if (i == 0 && !first) strip(vc, x, y + 0.03F, z, 0xFF2A2018);
                        strip(vc, x, y + 0.03F, z, 0xFF2A2018);
                        strip(vc, x, y - 0.03F, z, 0xFF1C150F);
                        if (i == rope.length / 3 - 1) strip(vc, x, y - 0.03F, z, 0xFF1C150F);
                    }
                    first = false;
                }
                for (int k = 0; k + 4 < lanterns.length; k += 5) {
                    int col = Float.floatToRawIntBits(lanterns[k + 3]);
                    if (on) col = 0xFF000000 | brighten(col);
                    float x = (float) (lanterns[k] - cx), y = (float) (lanterns[k + 1] - cy), z = (float) (lanterns[k + 2] - cz);
                    paper(vc, x, y, z, col, on);
                }
            } catch (RuntimeException ex) {
                Awaken.LOG.error("[citizens] festoons failed", ex);
                reset();
            }
        });
    }

    private static int brighten(int c) {
        int r = Math.min(255, (c >> 16 & 255) + 70), g = Math.min(255, (c >> 8 & 255) + 60), b = Math.min(255, (c & 255) + 30);
        return r << 16 | g << 8 | b;
    }

    private static void strip(VertexConsumer vc, float x, float y, float z, int col) {
        vc.addVertex(x, y, z).setColor(col).setLight(light);
    }

    /** A paper lantern: a small rounded shape facing the camera (a cap, a belly, a foot), as strip quads. */
    private static void paper(VertexConsumer vc, float x, float y, float z, int col, boolean on) {
        int l = on ? LightCoordsUtil.FULL_BRIGHT : light;
        float w = 0.15F, h = 0.2F;
        float rx = RIGHT.x, ry = RIGHT.y, rz = RIGHT.z, ux = UP.x, uy = UP.y, uz = UP.z;
        // degenerate in, the quad (bottom-left, top-left, bottom-right, top-right), degenerate out
        float bx = x - rx * w - ux * h, by = y - ry * w - uy * h, bz = z - rz * w - uz * h;
        vc.addVertex(bx, by, bz).setColor(col).setLight(l);
        vc.addVertex(bx, by, bz).setColor(col).setLight(l);
        vc.addVertex(x - rx * w + ux * h, y - ry * w + uy * h, z - rz * w + uz * h).setColor(col).setLight(l);
        vc.addVertex(x + rx * w - ux * h, y + ry * w - uy * h, z + rz * w - uz * h).setColor(col).setLight(l);
        float tx = x + rx * w + ux * h, ty = y + ry * w + uy * h, tz = z + rz * w + uz * h;
        vc.addVertex(tx, ty, tz).setColor(col).setLight(l);
        vc.addVertex(tx, ty, tz).setColor(col).setLight(l);
    }

    // ---- the flames (CityGlow's additive pass) --------------------------------------------------------------------

    static final CityGlow.Source GLOW = (out, partial, cx, cy, cz) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !built || !lit(mc.level)) return;
        for (int k = 0; k + 4 < lanterns.length; k += 5) {
            float x = lanterns[k], y = lanterns[k + 1], z = lanterns[k + 2];
            if (out.far(x, y, z)) continue;
            float f = out.flicker((int) lanterns[k + 4]);
            out.halo(x, y, z, 0.55F, 0.09F, f);
        }
        // the floor under the canopy catches it: a broad pool under every sixth lantern
        if (ground.length != lanterns.length / 5) ground = new float[lanterns.length / 5];
        for (int k = 0, i = 0; k + 4 < lanterns.length; k += 5, i++) {
            if (i % 6 != 3) continue;
            if (ground[i] == 0F) ground[i] = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(lanterns[k]), Mth.floor(lanterns[k + 2])) + 0.03F;
            if (ground[i] > lanterns[k + 1]) continue; // under a stall's roof: no pool
            out.pool(lanterns[k], ground[i], lanterns[k + 2], 4.2F, 0.10F);
        }
    };
}
