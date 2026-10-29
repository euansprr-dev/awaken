package dev.awaken.living.wonder;

import dev.awaken.core.api.Spotting;
import dev.awaken.living.api.Regions;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wonders spotted through the binoculars (SPEC §39.10): one you haven't found, held in the glass, is found as if you
 * stood at it ({@link WonderWatch#find}). Seen by its plan's real bounds (worked out once per wonder).
 */
public final class WonderSpotting {
    private WonderSpotting() {}

    /** Plan bounds per wonder key: minX, minY, minZ, maxX, maxY, maxZ relative to the heart. */
    private static final Map<String, int[]> BOUNDS = new HashMap<>();
    private static final int BOUNDS_MAX = 256;

    public static void register() {
        Spotting.register(SOURCE);
    }

    static final Spotting.Source SOURCE = new Spotting.Source() {
        @Override
        public boolean moves() {
            return false;
        }

        @Override
        public void gather(ServerPlayer p, double reach, Spotting.Sink out) {
            if (!Regions.on(p.level())) return;
            ServerLevel level = p.level();
            List<String> have = WonderWatch.found(p);
            for (Wonders.Site s : WonderWatch.near(level, p.getBlockX(), p.getBlockZ(), (int) reach)) {
                if (have.contains(s.key())) continue;
                int[] b = bounds(s);
                out.place(s.key().hashCode() & 0xFFFF_FFFFL | ((long) s.x() << 32), s.x() + b[0], s.y() + b[1], s.z() + b[2],
                        s.x() + b[3] + 1, s.y() + b[4] + 1, s.z() + b[5] + 1, s);
            }
        }

        @Override
        public boolean find(ServerPlayer p, Object ref) {
            return ref instanceof Wonders.Site s && Regions.on(p.level()) && WonderWatch.find(p, p.level(), s);
        }
    };

    private static int[] bounds(Wonders.Site s) {
        int[] b = BOUNDS.get(s.key());
        if (b != null) return b;
        if (BOUNDS.size() >= BOUNDS_MAX) BOUNDS.clear();
        b = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (LongIterator it = s.plan().blocks().keySet().iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            b[0] = Math.min(b[0], x);
            b[1] = Math.min(b[1], y);
            b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x);
            b[4] = Math.max(b[4], y);
            b[5] = Math.max(b[5], z);
        }
        if (b[0] > b[3]) b = new int[]{-4, 0, -4, 4, 8, 4}; // an empty plan: its heart
        BOUNDS.put(s.key(), b);
        return b;
    }
}
