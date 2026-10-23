package dev.awaken.world.anomaly.client;

import dev.awaken.world.anomaly.GroveVariant;
import dev.awaken.world.anomaly.ShimmerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * An ore-sight grove, client side: while you stand in one, the ore under and around it is outlined through the stone
 * (per-tick gizmos, always on top), coloured by kind. The client already holds the blocks; it looks for ore around the
 * grove's heart every two seconds (a bounded box, at most {@link #MAX} outlines, nearest first).
 */
final class OreSight {
    private OreSight() {}

    static final int SIDE = 12, DOWN = 18, UP = 5, MAX = 64;

    private record Ore(BlockPos pos, int argb) {}

    private static final List<Ore> ORES = new ArrayList<>();
    private static int groveId = -1;
    private static long scannedAt = Long.MIN_VALUE;
    private static long lookedAt = Long.MIN_VALUE;
    private static ShimmerEntity grove;

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            reset();
            return;
        }
        long now = level.getGameTime();
        if (now - lookedAt >= 10 || now < lookedAt) {
            lookedAt = now;
            grove = find(mc);
        }
        ShimmerEntity g = grove;
        if (g == null || g.isRemoved() || !g.active() || g.fading()) {
            ORES.clear();
            groveId = -1;
            return;
        }
        if (g.getId() != groveId || now - scannedAt >= 40 || now < scannedAt) {
            groveId = g.getId();
            scannedAt = now;
            scan(level, g.blockPosition());
        }
        for (Ore o : ORES) Gizmos.cuboid(o.pos(), 0.003F, GizmoStyle.stroke(o.argb(), 2.0F)).setAlwaysOnTop();
    }

    private static void reset() {
        ORES.clear();
        groveId = -1;
        grove = null;
    }

    /** An awake ore-sight grove whose ring the player stands in, if any. */
    private static ShimmerEntity find(Minecraft mc) {
        double r = ShimmerEntity.radius();
        AABB box = mc.player.getBoundingBox().inflate(r + 2, 12, r + 2);
        for (ShimmerEntity s : mc.level.getEntitiesOfClass(ShimmerEntity.class, box)) {
            if (s.variant() != GroveVariant.ORE_SIGHT || !s.active() || s.fading()) continue;
            double dx = mc.player.getX() - s.getX(), dz = mc.player.getZ() - s.getZ();
            if (dx * dx + dz * dz <= (r + 1) * (r + 1)) return s;
        }
        return null;
    }

    private static void scan(ClientLevel level, BlockPos c) {
        ORES.clear();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (int dy = -DOWN; dy <= UP; dy++) {
            for (int dx = -SIDE; dx <= SIDE; dx++) {
                for (int dz = -SIDE; dz <= SIDE; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!level.isLoaded(m)) continue;
                    BlockState s = level.getBlockState(m);
                    if (s.is(BlockTags.ORES) || s.is(Tags.BlockItems.ORES.block())) found.add(m.immutable());
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c)));
        for (int i = 0; i < found.size() && i < MAX; i++) ORES.add(new Ore(found.get(i), colour(level.getBlockState(found.get(i)))));
    }

    private static int colour(BlockState s) {
        if (s.is(BlockItemTags.DIAMOND_ORES.block())) return 0xFF6FF3FF;
        if (s.is(BlockItemTags.EMERALD_ORES.block())) return 0xFF5CFF8A;
        if (s.is(BlockTags.GOLD_ORES)) return 0xFFFFD84A;
        if (s.is(BlockItemTags.REDSTONE_ORES.block())) return 0xFFFF4A4A;
        if (s.is(BlockItemTags.LAPIS_ORES.block())) return 0xFF4A7BFF;
        if (s.is(BlockTags.IRON_ORES)) return 0xFFE8C2A0;
        if (s.is(BlockTags.COPPER_ORES)) return 0xFFFF9A5A;
        if (s.is(BlockItemTags.COAL_ORES.block())) return 0xFF9A9A9A;
        if (s.is(Blocks.ANCIENT_DEBRIS)) return 0xFFB07A5A;
        return 0xFFFFC66B;
    }
}
