package dev.awaken.finds.kit;

import dev.awaken.finds.Scene;
import net.minecraft.core.Direction;

import static dev.awaken.finds.kit.B.*;

/**
 * How a monument meets the ground (SPEC §62.3). A monument does not ask the land to be level; it brings its ground with
 * it, the way real ones do: a terrace whose floor is level and whose edge is masonry wherever the land is not. Where the
 * land falls away the edge is a battered retaining wall standing on the land itself, column by column; where it rises
 * the cut is faced; where it is level there is a kerb flush with the grass. No dirt ramps, and nothing left hanging:
 * every wall starts under the real ground of its own column. Steps run from the terrace to whatever ground is there.
 * <p>
 * Heights are relative to the heart's ground, like everything in a scene. On a pad stood by hand the land is level, so
 * a terrace at height 0 shows only its kerb; a monument that should stand on a podium asks for a floor above 0.
 */
public final class G {
    private G() {}

    /** A footprint: how far a column lies outside it (0 or less is inside). */
    @FunctionalInterface
    public interface Shape {
        double out(int x, int z);
    }

    public static Shape disc(int cx, int cz, double r) {
        return (x, z) -> Math.sqrt((x - cx) * (double) (x - cx) + (z - cz) * (double) (z - cz)) - r;
    }

    public static Shape box(int x0, int z0, int x1, int z1) {
        return (x, z) -> {
            int ox = Math.max(x0 - x, x - x1), oz = Math.max(z0 - z, z - z1);
            return ox > 0 && oz > 0 ? Math.sqrt(ox * ox + oz * oz) : Math.max(ox, oz);
        };
    }

    public static Shape either(Shape a, Shape b) {
        return (x, z) -> Math.min(a.out(x, z), b.out(x, z));
    }

    /** The compass direction that leads into a footprint from a column at its edge. */
    public static Direction inward(Shape f, int x, int z) {
        double gx = f.out(x + 1, z) - f.out(x - 1, z), gz = f.out(x, z + 1) - f.out(x, z - 1);
        return Math.abs(gx) >= Math.abs(gz) ? (gx > 0 ? Direction.WEST : Direction.EAST) : (gz > 0 ? Direction.NORTH : Direction.SOUTH);
    }

    /** How much of a fall the terrace's wall takes, and how much of a rise its faced cut; the land is eased to meet the rest. */
    public static final int WALL = 6, CUT = 4;
    /** How far out from a terrace's wall the land is regraded to meet it. A kind's reach has to hold its terrace and this. */
    public static final int SKIRT = 10;

    /** Where the ground stands at a terrace's wall once the land has been eased toward it: the land's own height, held within what the wall and the cut take. */
    private static int foot(Scene s, int x, int z, int top) {
        return Math.max(top - WALL, Math.min(top + CUT, s.nat(x, z)));
    }

    /**
     * The terrace (SPEC §62.3). Inside the footprint the ground is set to {@code top}. Round it the land is eased over
     * {@link #SKIRT} blocks until it stands no more than {@link #WALL} below the floor or {@link #CUT} above it, the way
     * ground is made up to a village: a long gentle regrade in the land's own skin. What is left the masonry takes: the
     * first course outside the footprint is the wall, the second its batter (two thirds as high, with a sloped top).
     * Where the land is level there is only a kerb flush with the grass. {@code reach} bounds the columns looked at.
     */
    public static void platform(Scene s, Shape f, int top, int reach) {
        for (int x = -reach; x <= reach; x++)
            for (int z = -reach; z <= reach; z++) {
                double d = f.out(x, z);
                if (d > 2.2 + SKIRT) continue;
                if (d <= 0) {
                    s.ground(x, z, top);
                    continue;
                }
                int nat = s.nat(x, z), foot = foot(s, x, z, top), low = top - foot;
                if (d > 2.2) {                                    // the skirt: the land comes to meet the wall's foot
                    if (foot == nat) continue;
                    double t = (d - 2.2) / SKIRT;
                    t = t * t * (3 - 2 * t);
                    int g = (int) Math.round(foot + (nat - foot) * t);
                    if (g != nat) s.ground(x, z, g);
                    continue;
                }
                if (foot != nat) s.ground(x, z, foot);
                if (d <= 1.2) {
                    if (low >= 1) {                              // the land falls away: a wall from under it to the floor
                        for (int y = foot - 1; y <= top; y++) s.put(x, y, z, y == top ? M.block() : low >= 5 && y == top - 2 ? M.band() : M.course(y));
                        if (low >= 3 && n(s, x * 1.3 + 5, z * 1.3 - 3, 4.0) > 0.36) s.put(x, top + 1, z, M.wall());
                    } else if (low <= -1) {                      // the land rises: the cut is faced, coped at the land's own height
                        s.put(x, top, z, M.block());
                        for (int y = top + 1; y <= foot; y++) s.put(x, y, z, y == foot ? M.block() : M.course(y));
                    } else s.put(x, top, z, M.flag());           // level: a kerb flush with the grass
                } else if (low >= 4) {
                    int up = foot + low * 2 / 3;
                    for (int y = foot - 1; y <= up; y++) s.put(x, y, z, M.course(y));
                    s.put(x, up + 1, z, stair(M.blockStairs(), inward(f, x, z)));
                }
            }
    }

    /**
     * Steps between a terrace and the land. {@code (x, z)} is the middle of the first row outside the terrace's floor,
     * {@code out} points away from it, the flight is {@code 2 * half + 1} wide between two cheek walls. Where the land
     * ahead is lower the flight goes down to it; where it is higher the flight is cut up into the rise. Returns how
     * many rows it took, so a path can be led to its foot.
     */
    public static int stairs(Scene s, int x, int z, Direction out, int half, int top) {
        int sx = out.getStepX(), sz = out.getStepZ(), px = -sz, pz = sx;
        boolean down = s.g(x + sx * 2, z + sz * 2) <= top;
        int k = 0;
        for (; k < 30; k++) {
            int cx = x + sx * k, cz = z + sz * k, y = down ? top - k : top + 1 + k, g = s.g(cx, cz);
            if (down ? y <= g : y > g) break;
            for (int w = -half - 1; w <= half + 1; w++) {
                int wx = cx + px * w, wz = cz + pz * w, gw = s.g(wx, wz);
                boolean cheek = Math.abs(w) == half + 1;
                if (down) {
                    for (int yy = Math.min(gw, y) - 1; yy < y; yy++) s.put(wx, yy, wz, M.course(yy));
                    s.put(wx, y, wz, cheek ? M.block() : M.step(s, wx, wz, out.getOpposite()));
                    if (cheek && k % 4 == 0) s.put(wx, y + 1, wz, M.blockSlab());
                } else {
                    s.put(wx, y - 1, wz, M.course(y - 1));
                    if (cheek) for (int yy = y; yy <= Math.max(gw, y); yy++) s.put(wx, yy, wz, yy == Math.max(gw, y) ? M.block() : M.course(yy));
                    else {
                        s.put(wx, y, wz, M.step(s, wx, wz, out));
                        for (int yy = y + 1; yy <= Math.max(gw, y + 3); yy++) s.put(wx, yy, wz, AIR);
                    }
                }
            }
        }
        return k;
    }

    /**
     * A wall-walk's worth of masonry under anything that must not hang: every column of a box that has something built
     * at {@code y} gets coursed stone from under its own ground up to {@code y - 1}. For plinths on a curved or tilted
     * floor (the Dish's pylon stood a block clear of its bowl on the low side).
     */
    public static void foot(Scene s, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (!s.taken(x, y, z)) continue;
                for (int yy = y - 1; yy >= Math.min(s.g(x, z), y - 1) - 1 && yy > y - 24; yy--) {
                    if (s.taken(x, yy, z) && !s.get(x, yy, z).isAir()) break;
                    s.put(x, yy, z, M.course(yy));
                }
            }
    }
}
