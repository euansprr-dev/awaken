package dev.awaken.living.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.living.craft.Craft;
import dev.awaken.living.craft.Crafts;
import dev.awaken.living.craft.Herbs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The crafts' perks you see (SPEC §21.9), all from blocks and creatures the client already has: Herb Glint (a thin
 * sea-green shimmer standing up from ripe plants within 16), Ore Sense (every 4 s with a pick in hand, the ore within 6,
 * or 10 with Vein Sight, pulses through the stone as projected glass outlines, then fades), Tracks (a creature the band
 * read leaves glowing pawprints for 30 s).
 */
final class CraftFx {
    private CraftFx() {}

    static final int HERB_RADIUS = 16, MAX_HERBS = 48, ORE_EVERY = 80, ORE_SHOW = 60, TRACK_TICKS = 600, TRACK_EVERY = 8;

    private static final List<BlockPos> HERBS = new ArrayList<>();
    private record Ore(BlockPos pos, int rgb) {}
    private static final List<Ore> ORES = new ArrayList<>();
    private static long oreAt = Long.MIN_VALUE;
    /** Pawprints per creature: x, y, z, the tick laid, which foot. */
    private static final Map<Integer, ArrayDeque<double[]>> TRACKS = new HashMap<>();

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null || !LivingClient.shown) {
            HERBS.clear();
            ORES.clear();
            TRACKS.clear();
            return;
        }
        long now = level.getGameTime();
        if (now % 20 == 0) herbs(level, p);
        ores(level, p, now);
        tracks(level, p, now);
    }

    private static void herbs(ClientLevel level, Player p) {
        HERBS.clear();
        if (!Crafts.has(p, Craft.FORAGING, "herb_glint")) return;
        boolean root = Crafts.has(p, Craft.FORAGING, "root_sense");
        long day = Herbs.day(level);
        BlockPos c = p.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -HERB_RADIUS; dx <= HERB_RADIUS && HERBS.size() < MAX_HERBS; dx++) {
            for (int dz = -HERB_RADIUS; dz <= HERB_RADIUS && HERBS.size() < MAX_HERBS; dz++) {
                if (dx * dx + dz * dz > HERB_RADIUS * HERB_RADIUS) continue;
                for (int dy = -5; dy <= 5; dy++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (Herbs.plant(level.getBlockState(m)) && Herbs.ripe(m, day, root)) {
                        HERBS.add(m.immutable());
                        break;
                    }
                }
            }
        }
    }

    private static void ores(ClientLevel level, Player p, long now) {
        if (now - oreAt > ORE_SHOW) ORES.clear();
        boolean pick = p.getMainHandItem().is(ItemTags.PICKAXES);
        if (pick && Crafts.has(p, Craft.MINING, "ore_sense") && (now - oreAt >= ORE_EVERY || now < oreAt)) {
            oreAt = now;
            ORES.clear();
            int r = Crafts.has(p, Craft.MINING, "vein_sight") ? 10 : 6;
            BlockPos c = p.blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                if (ORES.size() >= 96) break;
                m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                BlockState s = level.getBlockState(m);
                if (Herbs.ore(s)) ORES.add(new Ore(m.immutable(), tint(s)));
            }
        }
        if (ORES.isEmpty()) return;
        // a pulse in, a slow fade out: projected glass, edges only, through the stone
        float age = now - oreAt;
        float a = age < 6 ? age / 6F : 1 - (age - 6) / (ORE_SHOW - 6F);
        if (a <= 0.02F) return;
        int alpha = (int) (Mth.clamp(a, 0, 1) * 220) << 24;
        for (Ore o : ORES) Gizmos.cuboid(o.pos(), 0.02F, GizmoStyle.stroke(alpha | o.rgb(), 2.2F)).setAlwaysOnTop();
    }

    private static int tint(BlockState s) {
        if (s.is(BlockTags.GOLD_ORES)) return 0xFFE27A;
        if (s.is(BlockTags.IRON_ORES)) return 0xEAD2BC;
        if (s.is(BlockTags.COPPER_ORES)) return 0xFFB07A;
        if (s.is(net.minecraft.tags.BlockItemTags.DIAMOND_ORES.block())) return 0x8FF6FF;
        if (s.is(net.minecraft.tags.BlockItemTags.EMERALD_ORES.block())) return 0x7FFFA8;
        if (s.is(net.minecraft.tags.BlockItemTags.REDSTONE_ORES.block())) return 0xFF7A7A;
        if (s.is(net.minecraft.tags.BlockItemTags.LAPIS_ORES.block())) return 0x7FA0FF;
        if (s.is(net.minecraft.tags.BlockItemTags.COAL_ORES.block())) return 0xB8C2CE;
        return 0xBFE3FF;
    }

    private static void tracks(ClientLevel level, Player p, long now) {
        boolean on = Crafts.has(p, Craft.HUNTING, "tracks");
        Iterator<Map.Entry<Integer, ArrayDeque<double[]>>> it = TRACKS.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<double[]> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst()[3] > TRACK_TICKS) q.pollFirst();
            if (q.isEmpty()) it.remove();
        }
        if (!on || now % TRACK_EVERY != 0) return;
        for (var c : LivingClient.PICKED) {
            Entity e = level.getEntity(c.id());
            if (e == null || !e.onGround()) continue;
            ArrayDeque<double[]> q = TRACKS.computeIfAbsent(c.id(), k -> new ArrayDeque<>());
            double[] last = q.peekLast();
            if (last != null && Math.hypot(e.getX() - last[0], e.getZ() - last[2]) < 0.9) continue;
            q.addLast(new double[]{e.getX(), e.getY() + 0.02, e.getZ(), now, q.size() % 2, e.getYRot()});
            while (q.size() > 80) q.pollFirst();
        }
    }

    static void reset() {
        HERBS.clear();
        ORES.clear();
        TRACKS.clear();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static RenderType light, alpha;
    private static double cx, cy, cz;
    private static final Vector3f RIGHT = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();

    static void submit(SubmitCustomGeometryEvent e) {
        if (HERBS.isEmpty() && TRACKS.isEmpty()) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        if (light == null) light = RenderTypes.lightning();
        if (alpha == null) alpha = RenderTypes.debugQuads();
        float now = LivingClient.ticks + state.worldPartialTicks;
        long gameNow = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        if (!HERBS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> glints(vc, now)));
        if (!TRACKS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), alpha, (pose, vc) -> guard(() -> prints(vc, gameNow)));
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[living] craft fx failed", ex);
        }
    }

    /** Herb Glint: a hair-thin shimmer standing up from each ripe plant, and a glint that rides up it. */
    private static void glints(VertexConsumer vc, float now) {
        for (BlockPos b : HERBS) {
            float ph = (b.getX() * 7 + b.getZ() * 13) % 40;
            float flick = 0.6F + 0.4F * Mth.sin(now * 0.25F + ph);
            double x = b.getX() + 0.5, y = b.getY() + 0.1, z = b.getZ() + 0.5;
            streak(vc, x, y, z, 1.3, 0.05F, 0x6FE6C8, 0.75F * flick);
            streak(vc, x, y, z, 0.9, 0.22F, 0x6FE6C8, 0.18F * flick);
            float rise = ((now * 0.03F + ph / 40F) % 1F);
            streak(vc, x, y + rise * 1.1, z, 0.18, 0.12F, 0xD8FFF2, 0.8F * (1 - rise));
        }
    }

    /** Tracks: paired paw marks on the ground, glowing amber, fading over their 30 s. */
    private static void prints(VertexConsumer vc, long now) {
        for (ArrayDeque<double[]> q : TRACKS.values()) {
            for (double[] t : q) {
                float age = (now - (long) t[3]) / (float) TRACK_TICKS;
                if (age >= 1) continue;
                float a = (1 - age) * 0.85F;
                double yaw = Math.toRadians(t[5]);
                double side = t[4] == 0 ? 0.18 : -0.18;
                double px = t[0] + Math.cos(yaw) * side, pz = t[2] + Math.sin(yaw) * side;
                paw(vc, px, t[1], pz, yaw, a);
            }
        }
    }

    private static void paw(VertexConsumer vc, double x, double y, double z, double yaw, float a) {
        int c = ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | 0xFFC060;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
        flat(vc, x, y, z, fx, fz, sx, sz, 0.12, 0.1, c);                       // the pad
        for (int toe = -1; toe <= 1; toe++) {                                    // three toes ahead of it
            flat(vc, x + fx * 0.16 + sx * toe * 0.08, y, z + fz * 0.16 + sz * toe * 0.08, fx, fz, sx, sz, 0.045, 0.045, c);
        }
    }

    private static void flat(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double hl, double hw, int c) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz);
        quad(vc, x0 - (float) (fx * hl + sx * hw), y0, z0 - (float) (fz * hl + sz * hw), x0 + (float) (fx * hl - sx * hw), y0, z0 + (float) (fz * hl - sz * hw),
                x0 + (float) (fx * hl + sx * hw), y0, z0 + (float) (fz * hl + sz * hw), x0 - (float) (fx * hl - sx * hw), y0, z0 - (float) (fz * hl - sz * hw), c, c);
    }

    private static void streak(VertexConsumer vc, double x, double y, double z, double h, float w, int rgb, float a) {
        int c = ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | rgb, clear = rgb;
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz), hw = w / 2;
        quad(vc, x0 - RIGHT.x * hw, y0, z0 - RIGHT.z * hw, x0 + RIGHT.x * hw, y0, z0 + RIGHT.z * hw,
                x0 + RIGHT.x * hw, y0 + (float) h, z0 + RIGHT.z * hw, x0 - RIGHT.x * hw, y0 + (float) h, z0 - RIGHT.z * hw, c, clear);
    }

    private static void quad(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, int bottom, int top) {
        vc.addVertex(x0, y0, z0).setColor(bottom);
        vc.addVertex(x1, y1, z1).setColor(bottom);
        vc.addVertex(x2, y2, z2).setColor(top);
        vc.addVertex(x3, y3, z3).setColor(top);
        vc.addVertex(x3, y3, z3).setColor(top);
        vc.addVertex(x2, y2, z2).setColor(top);
        vc.addVertex(x1, y1, z1).setColor(bottom);
        vc.addVertex(x0, y0, z0).setColor(bottom);
    }
}
