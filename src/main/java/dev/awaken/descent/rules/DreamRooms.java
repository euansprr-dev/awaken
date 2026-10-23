package dev.awaken.descent.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.Strata;
import dev.awaken.descent.world.Layout;
import dev.awaken.descent.world.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule 8, rooms change when you're not looking. The server watches every player's view; a dream room that someone saw
 * recently but nobody is looking at (or standing near) may swap its contents for another variant. The current variant
 * of each room is saved so rebuilds are exact. A rebuild is spread over a few ticks.
 */
public final class DreamRooms {
    private DreamRooms() {}

    public static final class Data extends SavedData {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("variants").forGetter(d -> d.variants)
        ).apply(i, m -> {
            Data d = new Data();
            d.variants.putAll(m);
            return d;
        }));
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Awaken.id("descent_dream_rooms"), Data::new, CODEC);
        final Map<String, Integer> variants = new HashMap<>();

        public Data() {}
    }

    static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data.TYPE);
    }

    static int variant(ServerLevel level, Terrain t, int depth, int room) {
        Integer v = data(level).variants.get(depth + ":" + room);
        return v != null ? v : t.defaultVariant(room / Terrain.ROOM_SECTORS, room % Terrain.ROOM_SECTORS);
    }

    /** Per-zone watcher state. */
    static final class Watch {
        final long[] seen = new long[Terrain.roomCount()];
        final long[] swapped = new long[Terrain.roomCount()];
        Rebuild job;
    }

    static final class Rebuild {
        final int room, variant, depth;
        final BlockPos min;
        final int sx, sy, sz;
        int cursor;

        Rebuild(int depth, int room, int variant, BlockPos min, int sx, int sy, int sz) {
            this.depth = depth;
            this.room = room;
            this.variant = variant;
            this.min = min;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }
    }

    /** Whether any player is looking toward (or standing near) a world position. Pure maths on positions and looks. */
    public static boolean observed(List<ServerPlayer> players, Vec3 center) {
        for (ServerPlayer p : players) {
            Vec3 eye = p.getEyePosition();
            Vec3 to = center.subtract(eye);
            double d2 = to.lengthSqr();
            if (d2 < 10 * 10) return true;
            if (d2 > 60 * 60) continue;
            if (viewDot(p.getLookAngle(), to) > 0.35) return true;
        }
        return false;
    }

    public static double viewDot(Vec3 look, Vec3 toTarget) {
        double len = toTarget.length();
        return len < 1e-6 ? 1 : look.dot(toTarget) / len;
    }

    static void tick(ServerLevel level, Terrain t, int depth, Watch w, List<ServerPlayer> players, long now, RandomSource rnd) {
        if (w.job != null) {
            step(level, t, w);
            return;
        }
        if (now % 40 != 0) return;
        for (int room = 0; room < Terrain.roomCount(); room++) {
            BlockPos c = Layout.world(depth, t.roomCenter(room));
            Vec3 cv = Vec3.atCenterOf(c);
            boolean anyNear = false;
            for (ServerPlayer p : players) if (p.distanceToSqr(cv) < 72 * 72) { anyNear = true; break; }
            if (!anyNear) continue;
            if (observed(players, cv)) {
                w.seen[room] = now;
                continue;
            }
            if (w.seen[room] == 0 || now - w.seen[room] > 1200 || now - w.swapped[room] < 600) continue;
            if (rnd.nextFloat() > 0.3F) continue;
            int cur = variant(level, t, depth, room);
            int next = Math.floorMod(cur + 1 + rnd.nextInt(Terrain.ROOM_VARIANTS - 1), Terrain.ROOM_VARIANTS);
            int span = 15;
            BlockPos min = c.offset(-span, -3, -span);
            if (!level.hasChunk(min.getX() >> 4, min.getZ() >> 4) || !level.hasChunk((min.getX() + 2 * span) >> 4, (min.getZ() + 2 * span) >> 4)) continue;
            boolean inside = false;
            for (ServerPlayer p : players) {
                BlockPos lp = Layout.local(depth, p.blockPosition());
                if (t.roomAt(lp.getX(), lp.getY(), lp.getZ()) == room || t.roomAt(lp.getX(), lp.getY() + 1, lp.getZ()) == room) inside = true;
            }
            if (inside) continue;
            w.swapped[room] = now;
            data(level).variants.put(depth + ":" + room, next);
            data(level).setDirty();
            w.job = new Rebuild(depth, room, next, min, 2 * span + 1, 9, 2 * span + 1);
            level.playSound(null, c, DescentContent.SND_ROOM_SHIFT.get(), SoundSource.AMBIENT, 0.6F, 0.6F + rnd.nextFloat() * 0.3F);
            return;
        }
    }

    private static void step(ServerLevel level, Terrain t, Watch w) {
        Rebuild j = w.job;
        int total = j.sx * j.sy * j.sz;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int budget = 2500;
        while (j.cursor < total && budget-- > 0) {
            int i = j.cursor++;
            int x = i % j.sx, z = (i / j.sx) % j.sz, y = i / (j.sx * j.sz);
            m.set(j.min.getX() + x, j.min.getY() + y, j.min.getZ() + z);
            BlockPos lp = Layout.local(j.depth, m);
            if (t.roomAt(lp.getX(), lp.getY(), lp.getZ()) != j.room) continue;
            BlockState want = t.roomBlockAt(lp.getX(), lp.getY(), lp.getZ(), j.variant);
            if (want == null) continue;
            if (level.getBlockState(m) != want) level.setBlock(m, want, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
        if (j.cursor >= total) w.job = null;
    }

    static boolean applies(int depth) {
        return Strata.formOf(depth) == 8;
    }
}
