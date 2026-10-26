package dev.awaken.delve;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.WardHooks;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Cave-ins (PLAN 7.4; the pattern of the gates' {@code RigCaveIn}): a lode's heart broken in a big, unsupported
 * chamber shakes its roof loose. Dust trickles from the stones that will fall for three seconds (the client's warning,
 * with a creak), then they come down as falling blocks and land as rubble: run, slide or wall-run out. Rare, warned and
 * fair: only a chamber at least {@link #MIN_SPAN} across both ways with real headroom, only natural rock, never inside a
 * Hearth, the city or a ward, and never a roof anyone built or propped (a section with player-placed solid blocks).
 */
public final class CaveIns {
    private CaveIns() {}

    public static final int WARN = 60, MIN_AIR = 160, MIN_SPAN = 7, MAX_FILL = 1200, ROOF_RADIUS = 6, MAX_ROOF = 40, MIN_ROOF = 10;
    /** A big chamber's roof comes down this often when its heart breaks. */
    public static final double CHANCE = 0.6;
    /** What a falling stone does to whoever stands under it (per block fallen, at most). */
    static final float HURT_PER_BLOCK = 1.5F;
    static final int HURT_MAX = 8;

    private record Pending(ServerLevel level, long at, long[] roof, BlockState[] states) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    /** A lode's heart just broke: maybe its roof comes down ({@link #CHANCE}). */
    static boolean consider(ServerLevel level, Lode l, ServerPlayer p) {
        return consider(level, l.heartPos(), p, level.getRandom().nextDouble() < CHANCE);
    }

    /** {@code roll}: whether this one comes down if the chamber allows (tests pass true). */
    static boolean consider(ServerLevel level, BlockPos heart, ServerPlayer p, boolean roll) {
        if (!roll) return false;
        long[] roof = roof(level, heart);
        if (roof.length == 0) return false;
        BlockState[] states = new BlockState[roof.length];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < roof.length; i++) states[i] = level.getBlockState(m.set(roof[i]));
        PENDING.add(new Pending(level, level.getGameTime() + WARN, roof, states));
        DelveNet.near(level, null, heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5, 48,
                new DelveNet.CaveWarn(heart.asLong(), roof, WARN));
        level.playSound(null, heart, DelveContent.CAVE_CREAK.get(), SoundSource.BLOCKS, 1.4F, 0.9F + 0.2F * level.getRandom().nextFloat());
        p.sendOverlayMessage(Component.literal("The roof is giving way").withColor(0xE8C9A0));
        return true;
    }

    /**
     * The stones that would come down over this heart, nearest first; empty when this is no chamber for a cave-in
     * (too small or narrow, low, built in, or somewhere a cave-in never happens).
     */
    static long[] roof(ServerLevel level, BlockPos heart) {
        if (HearthHooks.inside(level, heart) || CityHooks.inCity(level, heart) || WardHooks.at(level, heart).isPresent()) return new long[0];
        // the chamber: air reached from beside the heart, within a box round it
        LongOpenHashSet air = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            BlockPos n = heart.relative(d);
            if (level.getBlockState(n).isAir() && air.add(n.asLong())) queue.enqueue(n.asLong());
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        while (!queue.isEmpty() && air.size() < MAX_FILL) {
            long c = queue.dequeueLong();
            int cx = BlockPos.getX(c), cy = BlockPos.getY(c), cz = BlockPos.getZ(c);
            x0 = Math.min(x0, cx);
            x1 = Math.max(x1, cx);
            z0 = Math.min(z0, cz);
            z1 = Math.max(z1, cz);
            for (Direction d : Direction.values()) {
                int nx = cx + d.getStepX(), ny = cy + d.getStepY(), nz = cz + d.getStepZ();
                if (Math.abs(nx - heart.getX()) > 12 || Math.abs(nz - heart.getZ()) > 12 || ny < heart.getY() - 5 || ny > heart.getY() + 12) continue;
                long n = BlockPos.asLong(nx, ny, nz);
                if (air.contains(n) || !level.getBlockState(m.set(nx, ny, nz)).isAir()) continue;
                air.add(n);
                queue.enqueue(n);
            }
        }
        if (air.size() < MIN_AIR || x1 - x0 + 1 < MIN_SPAN || z1 - z0 + 1 < MIN_SPAN) return new long[0];
        // its roof: the stone over the highest air of each column near the heart, where there is headroom
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap top = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap bottom = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        top.defaultReturnValue(Integer.MIN_VALUE);
        bottom.defaultReturnValue(Integer.MAX_VALUE);
        for (long c : air) {
            long col = BlockPos.asLong(BlockPos.getX(c), 0, BlockPos.getZ(c));
            top.put(col, Math.max(top.get(col), BlockPos.getY(c)));
            bottom.put(col, Math.min(bottom.get(col), BlockPos.getY(c)));
        }
        int tall = 0;
        for (var e : top.long2IntEntrySet()) if (e.getIntValue() - bottom.get(e.getLongKey()) + 1 >= 3) tall++;
        if (tall * 10 < top.size() * 3) return new long[0];
        DelveData data = DelveData.get(level);
        LongArrayList roof = new LongArrayList();
        for (var e : top.long2IntEntrySet()) {
            int x = BlockPos.getX(e.getLongKey()), z = BlockPos.getZ(e.getLongKey());
            int dx = x - heart.getX(), dz = z - heart.getZ();
            if (dx * dx + dz * dz > ROOF_RADIUS * ROOF_RADIUS || e.getIntValue() - bottom.get(e.getLongKey()) + 1 < 3) continue;
            m.set(x, e.getIntValue() + 1, z);
            if (data.builtAt(m)) return new long[0];                 // someone built here: this roof stays up
            if (natural(level.getBlockState(m))) roof.add(m.asLong());
        }
        if (roof.size() < MIN_ROOF) return new long[0];
        roof.sort((long a, long b) -> Double.compare(dist2(heart, a), dist2(heart, b)));
        return roof.subList(0, Math.min(MAX_ROOF, roof.size())).toLongArray();
    }

    private static double dist2(BlockPos h, long c) {
        double dx = BlockPos.getX(c) - h.getX(), dz = BlockPos.getZ(c) - h.getZ();
        return dx * dx + dz * dz;
    }

    /** Rock the world made: stone, deepslate and their kin, ores, dirt and gravel. */
    static boolean natural(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES.block()) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK);
    }

    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(pd -> {
            if (pd.level.getServer() != server) return true;
            if (pd.at > pd.level.getGameTime()) return false;
            due.add(pd);
            return true;
        });
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Pending pd : due) {
            int fell = 0;
            for (int i = 0; i < pd.roof.length; i++) {
                m.set(pd.roof[i]);
                if (pd.level.getBlockState(m) != pd.states[i]) continue;   // mined or changed meanwhile: it stays
                FallingBlockEntity f = FallingBlockEntity.fall(pd.level, m.immutable(), pd.states[i]);
                f.setHurtsEntities(HURT_PER_BLOCK, HURT_MAX);
                fell++;
            }
            if (fell > 0) {
                BlockPos c = BlockPos.of(pd.roof[0]);
                pd.level.playSound(null, c, DelveContent.CAVE_FALL.get(), SoundSource.BLOCKS, 2.0F, 0.85F + 0.2F * pd.level.getRandom().nextFloat());
            }
        }
    }

    /** Roofs waiting to fall (tests). */
    static int pending() {
        return PENDING.size();
    }

    static void clear() {
        PENDING.clear();
    }
}
