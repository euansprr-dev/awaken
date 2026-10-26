package dev.awaken.delve.client;

import dev.awaken.core.api.CombatFeedback;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import dev.awaken.delve.Veins;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lodes on the client (PLAN 7.3-7.4): the glints where light touches a lode near you (a lantern or torch close by, or the
 * miner's lamp's beam), and the vein giving when its heart breaks. Then the lode's outline lights through the rock in
 * its ore's glass (Ore Sense's look, the whole vein), white cracks run out from the heart along it with a crackle that
 * travels, the view kicks, and for 30 s its blocks fall in two blows each for the one who broke it (the client's copy
 * of the server's speed, so the bar and the break agree).
 */
public final class VeinFx {
    private VeinFx() {}

    /** The lodes the server says are near: id, ore, blocks. */
    private record Known(long id, Ore ore, long[] cells) {}

    private static final List<Known> NEAR = new ArrayList<>();
    static final int GLINT_EVERY = 6, GLINTS = 4, GLINT_RANGE = 24;

    // the opened lode: its blocks, each one's crack depth and parent, when it opened, whether the window is ours
    private static final LongOpenHashSet OPEN = new LongOpenHashSet();
    private static final Long2IntOpenHashMap DEPTH = new Long2IntOpenHashMap();
    private static final Long2LongOpenHashMap PARENT = new Long2LongOpenHashMap();
    private static long heart, openedAt = Long.MIN_VALUE / 2, windowUntil = Long.MIN_VALUE;
    private static int tint, maxDepth, crackled;

    static final float CRACK_SPEED = 1.6F;   // crack depth per tick (32 a second)
    static final int GLASS_IN = 6, GLASS_HOLD = 50, GLASS_OUT = 40;

    static void near(DelveNet.LodesNear m) {
        NEAR.clear();
        for (DelveNet.Near n : m.lodes()) {
            if (n.ore() < 0 || n.ore() >= Ore.values().length) continue;
            NEAR.add(new Known(n.id(), Ore.values()[n.ore()], n.cells()));
        }
    }

    static void open(DelveNet.VeinOpen m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || m.ore() < 0 || m.ore() >= Ore.values().length) return;
        Ore ore = Ore.values()[m.ore()];
        OPEN.clear();
        DEPTH.clear();
        PARENT.clear();
        for (long c : m.cells()) OPEN.add(c);
        heart = m.heart();
        tint = ore.tint;
        openedAt = mc.level.getGameTime();
        windowUntil = m.yours() ? openedAt + m.ticks() : Long.MIN_VALUE;
        crackled = 0;
        // the cracks' order: out from the heart through the vein's blocks (26 neighbours: the seam steps diagonally)
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        DEPTH.put(heart, 0);
        q.enqueue(heart);
        maxDepth = 0;
        while (!q.isEmpty()) {
            long c = q.dequeueLong();
            int d = DEPTH.get(c);
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                long n = BlockPos.asLong(x + dx, y + dy, z + dz);
                if (!OPEN.contains(n) || DEPTH.containsKey(n)) continue;
                DEPTH.put(n, d + 1);
                PARENT.put(n, c);
                maxDepth = Math.max(maxDepth, d + 1);
                q.enqueue(n);
            }
        }
        // a block the walk cut off from the heart (a cave between) cracks last, from the heart directly
        for (long c : OPEN) if (!DEPTH.containsKey(c)) {
            DEPTH.put(c, ++maxDepth);
            PARENT.put(c, heart);
        }
        if (m.yours()) {
            CombatFeedback.hold(100);
            CombatFeedback.kick(0F, -2.6F, 1.4F, 12);
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null) return;
        long now = level.getGameTime();
        if (now % GLINT_EVERY == 0) glints(level, p);
        if (OPEN.isEmpty()) return;
        long age = now - openedAt;
        if (age > Math.max(GLASS_IN + GLASS_HOLD + GLASS_OUT, now <= windowUntil ? windowUntil - openedAt : 0)) {
            OPEN.clear();
            return;
        }
        reveal(level, age);
    }

    /** The light and the cracks, re-drawn each tick (gizmos last a tick). */
    private static void reveal(ClientLevel level, long age) {
        float front = age * CRACK_SPEED;
        // glass: in quick, held, then out to a faint trace that stays while the window lasts
        float a = age < GLASS_IN ? age / (float) GLASS_IN : age < GLASS_IN + GLASS_HOLD ? 1F
                : Math.max(0.18F, 1F - (age - GLASS_IN - GLASS_HOLD) / (float) GLASS_OUT);
        if (age > GLASS_IN + GLASS_HOLD + GLASS_OUT) a = 0.18F;
        int stroke = (int) (Mth.clamp(a, 0, 1) * 230) << 24 | tint, fill = (int) (Mth.clamp(a, 0, 1) * 46) << 24 | tint;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : OPEN) {
            int d = DEPTH.get(c);
            if (d > front) continue;   // the light follows the cracks out
            if (level.getBlockState(m.set(c)).isAir()) continue;   // mined since
            Gizmos.cuboid(m.immutable(), 0.02F, GizmoStyle.strokeAndFill(stroke, 2.4F, fill)).setAlwaysOnTop();
        }
        // the cracks: a white line from each block back to the one it cracked from, brightest at the running front
        if (age < GLASS_IN + GLASS_HOLD + GLASS_OUT) {
            float fade = age < GLASS_IN + GLASS_HOLD ? 1F : 1F - (age - GLASS_IN - GLASS_HOLD) / (float) GLASS_OUT;
            for (long c : OPEN) {
                int d = DEPTH.get(c);
                if (d > front) continue;
                float hot = Mth.clamp(1F - (front - d) / 10F, 0.35F, 1F) * fade;
                int col = (int) (hot * 255) << 24 | 0xFFFFFF;
                Vec3 from = Vec3.atCenterOf(BlockPos.of(PARENT.get(c))), to = Vec3.atCenterOf(BlockPos.of(c));
                // a little zig in the middle so it reads as a crack, not a wire
                Vec3 mid = from.add(to).scale(0.5).add(jitter(c));
                Gizmos.line(from, mid, col, 2.6F).setAlwaysOnTop();
                Gizmos.line(mid, to, col, 2.6F).setAlwaysOnTop();
            }
            // the crackle travels with the front
            while (crackled * 6 < Math.min(front, maxDepth) && crackled < 12) {
                long at = farthestAt(crackled * 6);
                BlockPos b = BlockPos.of(at);
                level.playLocalSound(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, DelveContent.VEIN_CRACK.get(), SoundSource.BLOCKS,
                        0.7F, 0.9F + crackled * 0.03F, false);
                crackled++;
            }
        }
    }

    private static Vec3 jitter(long c) {
        long h = c * 0x9E3779B97F4A7C15L;
        return new Vec3(((h >>> 8) & 255) / 255.0 * 0.3 - 0.15, ((h >>> 16) & 255) / 255.0 * 0.3 - 0.15, ((h >>> 24) & 255) / 255.0 * 0.3 - 0.15);
    }

    private static long farthestAt(int depth) {
        for (var e : DEPTH.long2IntEntrySet()) if (e.getIntValue() == depth) return e.getLongKey();
        return heart;
    }

    /** Glints: a few lode blocks near you that are open to the air and lit (a lamp's beam, or a light close by). */
    private static void glints(ClientLevel level, Player p) {
        if (NEAR.isEmpty() || StrikeFeel.density(Minecraft.getInstance()) == 0) return;
        RandomSource r = level.getRandom();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), n = new BlockPos.MutableBlockPos();
        int shown = 0;
        for (int tries = 0; tries < 48 && shown < GLINTS; tries++) {
            Known k = NEAR.get(r.nextInt(NEAR.size()));
            long c = k.cells()[r.nextInt(k.cells().length)];
            m.set(c);
            if (m.distSqr(p.blockPosition()) > GLINT_RANGE * GLINT_RANGE || !k.ore().matches(level.getBlockState(m))) continue;
            Direction d = Direction.getRandom(r);
            n.setWithOffset(m, d);
            if (!level.getBlockState(n).isAir()) continue;
            if (level.getBrightness(LightLayer.BLOCK, n) < 8 && !LampGlow.lights(m)) continue;
            double x = m.getX() + 0.5 + d.getStepX() * 0.52 + (d.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            double y = m.getY() + 0.5 + d.getStepY() * 0.52 + (d.getStepY() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            double z = m.getZ() + 0.5 + d.getStepZ() * 0.52 + (d.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
            DelveParticles.spawn(level, DelveContent.GLINT.get(), k.ore().tint, 1F, x, y, z, 0, 0, 0);
            shown++;
        }
    }

    /** The client's copy of the two-blow speed in the opened lode's window. */
    static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (!e.getEntity().level().isClientSide() || e.getPosition().isEmpty() || OPEN.isEmpty()) return;
        if (e.getEntity().level().getGameTime() > windowUntil) return;
        BlockPos pos = e.getPosition().get();
        if (!OPEN.contains(pos.asLong())) return;
        e.setNewSpeed(Math.max(e.getNewSpeed(), Veins.twoBlows(e.getState().getDestroySpeed(e.getEntity().level(), pos))));
    }

    static void reset() {
        NEAR.clear();
        OPEN.clear();
        windowUntil = Long.MIN_VALUE;
    }
}
