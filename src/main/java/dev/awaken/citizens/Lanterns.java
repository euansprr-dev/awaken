package dev.awaken.citizens;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * Hand lanterns (the Watch at night, the lamplighter on the way, a late walker). Since M4 B3b their light is drawn on the
 * client ({@code citizens.client.CityGlow}: a hot core, a halo and a warm pool on the ground), so the server places no
 * blocks at all: each move of a real light block relit 8-18 sections and re-meshed 60-100 client sections (docs/perf/
 * CITY-FPS.md). What is left here clears the light blocks older saves left behind (every one placed was written to the
 * saved data until taken away), and the old light-block path behind {@code /awaken citizens lanterns blocks} for
 * measuring the difference ({@link #legacy}, off by default and never saved).
 */
final class Lanterns {
    static final int MAX = 12, LEVEL = 11;
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL);

    private final Settlement c;
    private final ServerLevel level;
    /** Per record: where its lantern's light is (BlockPos.asLong), or Long.MIN_VALUE. */
    private final long[] at;
    private int active;

    Lanterns(Settlement c) {
        this.c = c;
        this.level = c.level;
        at = new long[c.ledger.n];
        Arrays.fill(at, Long.MIN_VALUE);
        // lights a stopped server left in the world: cleared as their chunks load
        stale = c.data.lights.clone();
    }

    private long[] stale;

    /** The pre-B3b light blocks, for a before/after measurement only (off by default, never saved). */
    static boolean legacy;
    /** Light-block writes since the last {@code lanterns} report (each one a relight and a client re-mesh). */
    static long writes;

    void tick(long now) {
        if (stale.length > 0 && now % 100 == 0) {
            for (int k = 0; k < stale.length; k++) {
                if (stale[k] == Long.MIN_VALUE || !level.isLoaded(BlockPos.of(stale[k]))) continue;
                clear(stale[k]);
                forget(stale[k]);
                stale[k] = Long.MIN_VALUE;
            }
            boolean any = false;
            for (long p : stale) any |= p != Long.MIN_VALUE;
            if (!any) stale = new long[0];
        }
        if (now % 5 != 2) return;
        if (!legacy) {
            if (active > 0) stop();
            return;
        }
        Ledger l = c.ledger;
        for (int i = 0; i < l.n; i++) {
            CitizenBody b = l.body[i];
            boolean carries = b != null && !b.fading() && b.heldOff() == Props.LANTERN && b.nearestSq < 48 * 48;
            if (!carries) {
                if (at[i] != Long.MIN_VALUE) put(i, Long.MIN_VALUE);
                continue;
            }
            BlockPos head = BlockPos.containing(b.getX(), b.getY() + 1.2, b.getZ());
            long want = head.asLong();
            if (want == at[i]) continue;
            if (at[i] == Long.MIN_VALUE && active >= MAX) continue;
            if (!level.getBlockState(head).isAir()) {
                BlockPos up = head.above();
                if (!level.getBlockState(up).isAir()) continue;
                want = up.asLong();
                if (want == at[i]) continue;
            }
            put(i, want);
        }
    }

    private void put(int i, long pos) {
        if (at[i] != Long.MIN_VALUE) {
            clear(at[i]);
            active--;
            forget(at[i]);
        }
        at[i] = pos;
        if (pos == Long.MIN_VALUE) return;
        level.setBlock(BlockPos.of(pos), LIGHT, Block.UPDATE_CLIENTS);
        writes++;
        active++;
        long[] ls = c.data.lights;
        c.data.lights = Arrays.copyOf(ls, ls.length + 1);
        c.data.lights[ls.length] = pos;
        c.data.setDirty();
    }

    private void forget(long pos) {
        long[] ls = c.data.lights;
        for (int k = 0; k < ls.length; k++) {
            if (ls[k] != pos) continue;
            long[] out = new long[ls.length - 1];
            System.arraycopy(ls, 0, out, 0, k);
            System.arraycopy(ls, k + 1, out, k, ls.length - k - 1);
            c.data.lights = out;
            c.data.setDirty();
            return;
        }
    }

    private void clear(long pos) {
        BlockPos p = BlockPos.of(pos);
        if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.LIGHT)) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            writes++;
        }
    }

    int active() {
        return active;
    }

    void stop() {
        for (int i = 0; i < at.length; i++) if (at[i] != Long.MIN_VALUE) put(i, Long.MIN_VALUE);
    }
}
