package dev.awaken.delve.cave;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A place's own axes: local +z is "forward" (into the hill, down the chute), +x to the right of it, turned a quarter
 * turn at a time ({@code rot} 0: forward is south, 1 west, 2 north, 3 east). Blocks with a facing turn with it.
 */
final class Frame {
    final int ox, oy, oz, rot;

    Frame(int ox, int oy, int oz, int rot) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.rot = rot & 3;
    }

    int x(int lx, int lz) {
        return switch (rot) {
            case 1 -> ox - lz;
            case 2 -> ox - lx;
            case 3 -> ox + lz;
            default -> ox + lx;
        };
    }

    int z(int lx, int lz) {
        return switch (rot) {
            case 1 -> oz + lx;
            case 2 -> oz - lz;
            case 3 -> oz - lx;
            default -> oz + lz;
        };
    }

    double x(double lx, double lz) {
        return switch (rot) {
            case 1 -> ox - lz;
            case 2 -> ox - lx;
            case 3 -> ox + lz;
            default -> ox + lx;
        };
    }

    double z(double lx, double lz) {
        return switch (rot) {
            case 1 -> oz + lx;
            case 2 -> oz - lz;
            case 3 -> oz - lx;
            default -> oz + lz;
        };
    }

    int y(int ly) {
        return oy + ly;
    }

    /** A local direction (south = forward, east = right) in the world. */
    Direction dir(Direction local) {
        Direction d = local;
        for (int i = 0; i < rot; i++) d = d.getClockWise();
        return d;
    }

    void put(Sink k, int lx, int ly, int lz, BlockState s) {
        Carve.put(k, x(lx, lz), oy + ly, z(lx, lz), Carve.turn(s, rot));
    }

    void air(Sink k, int lx, int ly, int lz) {
        Carve.air(k, x(lx, lz), oy + ly, z(lx, lz));
    }

    BlockState get(Sink k, int lx, int ly, int lz) {
        return k.get(x(lx, lz), oy + ly, z(lx, lz));
    }

    boolean inside(Sink k, int lx, int ly, int lz) {
        return k.inside(x(lx, lz), oy + ly, z(lx, lz));
    }

    /** Carves the local box (inclusive). */
    void carve(Sink k, int lx0, int ly0, int lz0, int lx1, int ly1, int lz1) {
        int wx0 = x(lx0, lz0), wz0 = z(lx0, lz0), wx1 = x(lx1, lz1), wz1 = z(lx1, lz1);
        Carve.carveBox(k, wx0, oy + ly0, wz0, wx1, oy + ly1, wz1);
    }

    /** Fills the local box (inclusive) with a block, turned. */
    void fill(Sink k, int lx0, int ly0, int lz0, int lx1, int ly1, int lz1, BlockState s) {
        int wx0 = x(lx0, lz0), wz0 = z(lx0, lz0), wx1 = x(lx1, lz1), wz1 = z(lx1, lz1);
        Carve.fill(k, wx0, oy + ly0, wz0, wx1, oy + ly1, wz1, Carve.turn(s, rot));
    }

    boolean touches(Sink k, int lx0, int lz0, int lx1, int lz1) {
        int wx0 = x(lx0, lz0), wz0 = z(lx0, lz0), wx1 = x(lx1, lz1), wz1 = z(lx1, lz1);
        return k.touches(Math.min(wx0, wx1), Math.min(wz0, wz1), Math.max(wx0, wx1), Math.max(wz0, wz1));
    }

    void mark(Sink k, String what, int lx, int ly, int lz) {
        k.mark(what, x(lx, lz), oy + ly, z(lx, lz));
    }
}
