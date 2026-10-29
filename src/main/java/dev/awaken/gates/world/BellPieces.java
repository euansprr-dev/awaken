package dev.awaken.gates.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Bell Crypt's shapes, shared by its worlds (SPEC §38.6): hung bells with a lamp inside, the Great Bell you walk
 * under, stone singers, ropes. Each is a {@link Sculpt} operation over its own box, worked out per block as a chunk
 * carves (nothing is stored but the numbers).
 */
final class BellPieces {
    private BellPieces() {}

    /** A bell's outer radius at height t (0 lip, 1 crown) for a lip radius R: waisted, with a flared sound bow. */
    static double radius(double R, double t) {
        double r = R * (1 - 0.45 * Math.pow(Math.max(0, t), 0.9));
        if (t < 0.12) r += R * 0.07 * (1 - t / 0.12);
        return r;
    }

    /**
     * A hung bell: lip radius R, H tall, its lip at {@code lipY}, axis (cx, cz) through a block's centre or a block
     * corner. A lamp hangs inside it, its canon is chained up to {@code chainTo}; the great ones have a clapper too.
     */
    static void bell(Sculpt s, double cx, int lipY, double cz, double R, int H, int chainTo, BlockState bronze, BlockState lamp,
                     BlockState chain, boolean clapper) {
        int x0 = (int) Math.floor(cx - R - 1), x1 = (int) Math.ceil(cx + R + 1);
        int z0 = (int) Math.floor(cz - R - 1), z1 = (int) Math.ceil(cz + R + 1);
        s.op(x0, lipY, z0, x1, lipY + H - 1, z1, (b, x, y, z) -> {
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            double t = (y + 0.5 - lipY) / H;
            double r = radius(R, t);
            if (t > 0.86) {
                if (d < r) b.set(x, y, z, bronze);
                return;
            }
            double th = t < 0.2 ? 1.6 : 1.05;
            if (d < r && d >= r - th) b.set(x, y, z, bronze);
        });
        hanger(s, cx, lipY, cz, H, chainTo, bronze, lamp, chain);
        boolean corner = Math.abs(cx - Math.floor(cx)) < 0.25;
        int ax = (int) Math.floor(cx - (corner ? 0.5 : 0)), az = (int) Math.floor(cz - (corner ? 0.5 : 0));
        int top = lipY + H;
        if (clapper) {
            s.box(ax, lipY + 3, az, ax, top - (corner ? 4 : 3), az, chain);
            double bx = ax + 0.5, bz = az + 0.5;
            s.op(ax - 2, lipY, az - 2, ax + 2, lipY + 2, az + 2, (b, x, y, z) -> {
                double ex = x + 0.5 - bx, ey = (y - lipY - 1.5) * 1.2, ez = z + 0.5 - bz;
                if (ex * ex + ey * ey + ez * ez < 1.45 * 1.45) b.set(x, y, z, bronze);
            });
        }
    }

    /**
     * What a bell hangs by: its canon and the chains up to {@code chainTo} (a pair each way under a big corner-hung bell),
     * and its lamp inside. A swinging bell (a prop, 38.6) keeps these and loses its body: {@code lamp} is then a light.
     */
    static void hanger(Sculpt s, double cx, int lipY, double cz, int H, int chainTo, BlockState bronze, BlockState lamp, BlockState chain) {
        boolean corner = Math.abs(cx - Math.floor(cx)) < 0.25;
        int ax = (int) Math.floor(cx - (corner ? 0.5 : 0)), az = (int) Math.floor(cz - (corner ? 0.5 : 0));
        int top = lipY + H;
        for (int i = 0; i < (corner ? 2 : 1); i++) {
            for (int k = 0; k < (corner ? 2 : 1); k++) {
                s.put(ax + i, top, az + k, bronze);
                if (chainTo > top) s.box(ax + i, top + 1, az + k, ax + i, chainTo, az + k, chain);
            }
        }
        s.put(ax, top - (corner ? 3 : 2), az, lamp);
    }

    /** A stone singer of a drowned choir, standing in the water from {@code footY}, robed on the side it faces. */
    static void singer(Sculpt s, int x, int footY, int z, Direction face, BlockState body, BlockState head, BlockState robe) {
        // waist-deep: the body's lower half under the water, the chest and head above it, a robe hung over the chest
        s.box(x, footY, z, x, footY + 1, z, body);
        s.put(x, footY + 2, z, head);
        int rx = x + face.getStepX(), rz = z + face.getStepZ();
        s.putOpen(rx, footY + 1, rz, WorldMats.facing(robe, face));
    }

    /** A candle cluster of n (1..5), lit or out. */
    static BlockState candles(BlockState base, int n, boolean lit) {
        return WorldMats.with(WorldMats.with(base, "candles", Math.max(1, Math.min(5, n))), "lit", lit);
    }
}
