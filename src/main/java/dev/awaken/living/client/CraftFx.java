package dev.awaken.living.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.core.api.HearthHooks;
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
 * The crafts' perks you see and hear (SPEC §21.9, §28.3), all from blocks and creatures the client already has: Herb
 * Glint (a thin sea-green shimmer standing up from ripe plants within 16), Ore Sense (every 4 s with a pick or mattock in
 * hand, the ore within 6 pulses through the stone as projected glass outlines, then fades; with Vein Sight the whole vein
 * of each ore it finds), Deep Ear (underground, now and then, a soft sound from the way of an open cave or Deepglass
 * behind the rock), Tracks (a creature the band read leaves glowing pawprints for 30 s).
 */
final class CraftFx {
    private CraftFx() {}

    static final int HERB_RADIUS = 16, MAX_HERBS = 48, ORE_EVERY = 80, ORE_SHOW = 60, TRACK_TICKS = 600, TRACK_EVERY = 8;
    /** Vein Sight follows a vein this far from you, this many blocks in all. */
    static final int VEIN_REACH = 20, VEIN_MAX = 192;
    /** Deep Ear listens every 3 s, this far through the rock, and names a place again only after 30 s. */
    static final int EAR_EVERY = 60, EAR_REACH = 12, EAR_AGAIN = 600;
    /** Picks and the Hunter mattocks (the deep module's tag, read by id). */
    static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> MINING_TOOLS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Awaken.id("mining_tools"));

    private static final List<BlockPos> HERBS = new ArrayList<>();
    private record Ore(BlockPos pos, int rgb) {}
    private static final List<Ore> ORES = new ArrayList<>();
    /** Not Long.MIN_VALUE: {@code now - oreAt} would overflow and the first pulse never come. */
    private static final long NEVER = -1_000_000L;
    private static long oreAt = NEVER;
    /** Pawprints per creature: x, y, z, the tick laid, which foot. */
    private static final Map<Integer, ArrayDeque<double[]>> TRACKS = new HashMap<>();
    /** Creatures the band read, and when: each leaves tracks for 30 s after, wherever it wanders. */
    private static final Map<Integer, Long> SIGHTED = new HashMap<>();

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null || !LivingClient.shown) {
            HERBS.clear();
            ORES.clear();
            TRACKS.clear();
            SIGHTED.clear();
            return;
        }
        long now = level.getGameTime();
        if (now % 20 == 0) herbs(level, p);
        ores(level, p, now);
        if ((now + p.getId()) % EAR_EVERY == 0) ear(level, p, now);
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
                    if (Herbs.plant(level.getBlockState(m)) && Herbs.ripe(m, day, root, HearthHooks.greenhouse(level, m))) {
                        HERBS.add(m.immutable());
                        break;
                    }
                }
            }
        }
    }

    private static void ores(ClientLevel level, Player p, long now) {
        if (now - oreAt > ORE_SHOW) ORES.clear();
        boolean pick = p.getMainHandItem().is(ItemTags.PICKAXES) || p.getMainHandItem().is(MINING_TOOLS);
        if (pick && Crafts.has(p, Craft.MINING, "ore_sense") && (now - oreAt >= ORE_EVERY || now < oreAt)) {
            oreAt = now;
            ORES.clear();
            int r = 6;
            BlockPos c = p.blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                if (ORES.size() >= 96) break;
                m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                BlockState s = level.getBlockState(m);
                if (Herbs.ore(s)) ORES.add(new Ore(m.immutable(), tint(s)));
            }
            if (Crafts.has(p, Craft.MINING, "vein_sight")) veins(level, c);
        }
        if (ORES.isEmpty()) return;
        // a pulse in, a slow fade out: projected glass, edges only, through the stone
        float age = now - oreAt;
        float a = age < 6 ? age / 6F : 1 - (age - 6) / (ORE_SHOW - 6F);
        if (a <= 0.02F) return;
        int alpha = (int) (Mth.clamp(a, 0, 1) * 220) << 24;
        int fill = (int) (Mth.clamp(a, 0, 1) * 40) << 24;              // a faint pane on each face, so it reads as glass
        for (Ore o : ORES) Gizmos.cuboid(o.pos(), 0.02F, GizmoStyle.strokeAndFill(alpha | o.rgb(), 2.2F, fill | o.rgb())).setAlwaysOnTop();
    }

    /**
     * Vein Sight: from each ore Ore Sense found, the rest of its vein (the same ore, block to block through any of the 26
     * neighbours, as a lode's seam steps), out to {@link #VEIN_REACH} from you.
     */
    private static void veins(ClientLevel level, BlockPos c) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue q = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
        for (Ore o : ORES) {
            seen.add(o.pos().asLong());
            q.enqueue(o.pos().asLong());
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (!q.isEmpty() && ORES.size() < VEIN_MAX) {
            long at = q.dequeueLong();
            int x = BlockPos.getX(at), y = BlockPos.getY(at), z = BlockPos.getZ(at);
            int rgb = tint(level.getBlockState(m.set(x, y, z)));
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                if (seen.contains(n)) continue;
                seen.add(n);
                m.set(n);
                if (Math.abs(m.getX() - c.getX()) > VEIN_REACH || Math.abs(m.getY() - c.getY()) > VEIN_REACH || Math.abs(m.getZ() - c.getZ()) > VEIN_REACH) continue;
                BlockState s = level.getBlockState(m);
                if (!Herbs.ore(s) || tint(s) != rgb) continue;
                ORES.add(new Ore(m.immutable(), rgb));
                q.enqueue(n);
            }
        }
    }

    // ---- Deep Ear ---------------------------------------------------------------------------------------------------

    private static final int[][] EAR_DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};
    private static long heardAt = NEVER;
    private static long heard = Long.MIN_VALUE;

    /**
     * Deep Ear: underground with a pick or mattock, listen through the rock along fourteen ways for an open cave (air
     * two blocks deep at least, past two of rock or more) or Deepglass, and play its soft sound from there, so you hear
     * which way it lies. The nearest wins; a place already heard waits 30 s.
     */
    private static void ear(ClientLevel level, Player p, long now) {
        if (!Crafts.has(p, Craft.MINING, "deep_ear") || !(p.getMainHandItem().is(ItemTags.PICKAXES) || p.getMainHandItem().is(MINING_TOOLS))) return;
        BlockPos c = p.blockPosition();
        if (c.getY() > 56 || level.canSeeSky(c)) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long best = Long.MIN_VALUE;
        boolean glass = false;
        int bestD = Integer.MAX_VALUE;
        for (int[] d : EAR_DIRS) {
            int rock = 0;
            for (int i = 1; i <= EAR_REACH && i < bestD; i++) {
                m.set(c.getX() + d[0] * i, c.getY() + 1 + d[1] * i, c.getZ() + d[2] * i);
                BlockState s = level.getBlockState(m);
                if (isDeepglass(s) && rock >= 1) {
                    best = m.asLong();
                    bestD = i;
                    glass = true;
                    break;
                }
                if (!s.isAir()) {
                    rock++;
                    continue;
                }
                if (rock < 2) break;   // open right here: that's the cave you're in
                BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos(m.getX() + d[0], m.getY() + d[1], m.getZ() + d[2]);
                if (level.getBlockState(n).isAir() && level.getBlockState(n.move(d[0], d[1], d[2])).isAir()) {
                    best = m.asLong();
                    bestD = i;
                    glass = false;
                }
                break;
            }
        }
        if (best == Long.MIN_VALUE) return;
        if (Math.abs(BlockPos.getX(best) - BlockPos.getX(heard)) + Math.abs(BlockPos.getY(best) - BlockPos.getY(heard))
                + Math.abs(BlockPos.getZ(best) - BlockPos.getZ(heard)) < 6 && now - heardAt < EAR_AGAIN) return;
        heard = best;
        heardAt = now;
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(Awaken.id(glass ? "delve.deep_ear_glass" : "delve.deep_ear_cave"));
        if (sound == null) return;
        level.playLocalSound(BlockPos.getX(best) + 0.5, BlockPos.getY(best) + 0.5, BlockPos.getZ(best) + 0.5, sound,
                net.minecraft.sounds.SoundSource.AMBIENT, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F, false);
    }

    private static boolean isDeepglass(BlockState s) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id.getNamespace().equals(Awaken.MODID) && id.getPath().equals("deepglass");
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
        if (isDeepglass(s)) return 0xB48CFF;
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
        if (!on) {
            SIGHTED.clear();
            return;
        }
        for (var c : LivingClient.PICKED) SIGHTED.put(c.id(), now);
        SIGHTED.values().removeIf(t -> now - t > TRACK_TICKS || now < t);
        if (now % TRACK_EVERY != 0) return;
        for (int id : SIGHTED.keySet()) {
            Entity e = level.getEntity(id);
            if (e == null || !e.onGround()) continue;
            ArrayDeque<double[]> q = TRACKS.computeIfAbsent(id, k -> new ArrayDeque<>());
            double[] last = q.peekLast();
            if (last != null && Math.hypot(e.getX() - last[0], e.getZ() - last[2]) < 0.9) continue;
            // in grass or flowers a print on the ground is hidden: it lies on top of the plants instead
            BlockState at = level.getBlockState(e.blockPosition());
            double lift = !at.isAir() && at.getCollisionShape(level, e.blockPosition()).isEmpty() && at.getFluidState().isEmpty() ? 0.86 : 0.02;
            q.addLast(new double[]{e.getX(), e.getY() + lift, e.getZ(), now, q.size() % 2, e.getYRot(), hoofed(e) ? 1 : 0});
            while (q.size() > 80) q.pollFirst();
        }
    }

    /** Cloven hooves leave a split print; everything else a paw. */
    private static boolean hoofed(Entity e) {
        return e instanceof net.minecraft.world.entity.animal.sheep.Sheep || e instanceof net.minecraft.world.entity.animal.cow.AbstractCow
                || e instanceof net.minecraft.world.entity.animal.pig.Pig || e instanceof net.minecraft.world.entity.animal.goat.Goat
                || e instanceof net.minecraft.world.entity.animal.equine.AbstractHorse;
    }

    static void reset() {
        HERBS.clear();
        ORES.clear();
        TRACKS.clear();
        SIGHTED.clear();
        oreAt = NEVER;
        heardAt = NEVER;
        heard = Long.MIN_VALUE;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static RenderType light;
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
        float now = LivingClient.ticks + state.worldPartialTicks;
        long gameNow = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        if (!HERBS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> glints(vc, now)));
        if (!TRACKS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> prints(vc, gameNow)));
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
            streak(vc, x, y, z, 1.3, 0.07F, 0x6FE6C8, 0.85F * flick);
            streak(vc, x, y, z, 0.9, 0.3F, 0x6FE6C8, 0.24F * flick);
            float rise = ((now * 0.03F + ph / 40F) % 1F);
            streak(vc, x, y + rise * 1.1, z, 0.18, 0.12F, 0xD8FFF2, 0.8F * (1 - rise));
        }
    }

    /** Tracks: paired prints, glowing amber, the newest brightest, fading over their 30 s; a paw or a cloven hoof. */
    private static void prints(VertexConsumer vc, long now) {
        for (ArrayDeque<double[]> q : TRACKS.values()) {
            for (double[] t : q) {
                float age = (now - (long) t[3]) / (float) TRACK_TICKS;
                if (age >= 1) continue;
                float a = (1 - age) * (1 - age);
                double yaw = Math.toRadians(t[5]);
                double side = t[4] == 0 ? 0.16 : -0.16;
                double px = t[0] + Math.cos(yaw) * side, pz = t[2] + Math.sin(yaw) * side;
                if (t[6] > 0) hoof(vc, px, t[1], pz, yaw, a);
                else paw(vc, px, t[1], pz, yaw, a);
            }
        }
    }

    private static int amber(float a, float k) {
        return ((int) (Mth.clamp(a * k, 0, 1) * 255) << 24) | 0xFFB050;
    }

    private static void paw(VertexConsumer vc, double x, double y, double z, double yaw, float a) {
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
        halo(vc, x, y - 0.005, z, fx, fz, sx, sz, 0.3, 0.26, amber(a, 0.35F));
        flat(vc, x, y, z, fx, fz, sx, sz, 0.1, 0.11, amber(a, 1.0F));                          // the pad
        for (int toe = 0; toe < 4; toe++) {                                                     // four toes fanned ahead
            double off = (toe - 1.5) * 0.085, ahead = 0.15 + (toe == 0 || toe == 3 ? 0 : 0.035);
            flat(vc, x + fx * ahead + sx * off, y, z + fz * ahead + sz * off, fx, fz, sx, sz, 0.035, 0.03, amber(a, 1.0F));
        }
    }

    private static void hoof(VertexConsumer vc, double x, double y, double z, double yaw, float a) {
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
        halo(vc, x, y - 0.005, z, fx, fz, sx, sz, 0.28, 0.22, amber(a, 0.35F));
        for (int half = -1; half <= 1; half += 2) {                                             // two halves, a gap between
            double cx = x + sx * half * 0.045, cz = z + sz * half * 0.045;
            flat(vc, cx, y, cz, fx, fz, sx, sz, 0.1, 0.032, amber(a, 1.0F));
            flat(vc, cx + fx * 0.09, y, cz + fz * 0.09, fx, fz, sx, sz, 0.03, 0.022, amber(a, 1.0F));   // the pointed tip
        }
    }

    /** A soft oval of light under a print: bright in the middle, nothing at its rim (a fan of twelve). */
    private static void halo(VertexConsumer vc, double x, double y, double z, double fx, double fz, double sx, double sz, double rl, double rw, int c) {
        float x0 = (float) (x - cx), y0 = (float) (y - cy), z0 = (float) (z - cz);
        int clear = c & 0xFFFFFF;
        for (int i = 0; i < 12; i++) {
            double a0 = i * Math.PI / 6, a1 = (i + 1) * Math.PI / 6;
            float ex0 = (float) (fx * Math.cos(a0) * rl + sx * Math.sin(a0) * rw), ez0 = (float) (fz * Math.cos(a0) * rl + sz * Math.sin(a0) * rw);
            float ex1 = (float) (fx * Math.cos(a1) * rl + sx * Math.sin(a1) * rw), ez1 = (float) (fz * Math.cos(a1) * rl + sz * Math.sin(a1) * rw);
            quad(vc, x0, y0, z0, x0, y0, z0, x0 + ex1, y0, z0 + ez1, x0 + ex0, y0, z0 + ez0, c, clear);
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
