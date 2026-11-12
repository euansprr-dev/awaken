package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Kinds;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A line of stones to jump along (SPEC §50.9 "A way with climbs"; Euan, 12 Nov: the dungeon runs are better "with the
 * movement, the jumping"): pillars from one end to the other, each a step higher or lower than the last, the gaps a running
 * jump clears and a walk doesn't. Every land dresses it in its own stone ({@code body}, {@code cap}, {@code band}): one
 * piece written once, never a palette swap of a room. Its {@link #course} catches a fall back onto the last stone stood on.
 */
public final class Hops extends Piece<Mats> {
    /** One stone: its middle, its half-width (1 is three blocks across, 2 is five) and the y of its top block (feet stand on top + 1). */
    public record Stone(int x, int z, int r, int top) {}

    public final List<Stone> stones;
    final BlockState body, cap, band;

    public Hops(List<Stone> stones, BlockState body, BlockState cap, BlockState band, String label) {
        this.stones = List.copyOf(stones);
        this.body = body;
        this.cap = cap;
        this.band = band;
        this.label = label;
        int ax = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (Stone s : this.stones) {
            ax = Math.min(ax, s.x() - s.r());
            az = Math.min(az, s.z() - s.r());
            bx = Math.max(bx, s.x() + s.r());
            bz = Math.max(bz, s.z() + s.r());
        }
        x0 = ax;
        z0 = az;
        x1 = bx;
        z1 = bz;
        mayOverlap = true;
        startsWay = true;
    }

    /**
     * Stones in a line from (x0, z0) to (x1, z1), tops going from {@code top0} to {@code top1} a step at a time, never more
     * than one block up from one stone to the next. Each stone's middle is {@code 2r + 1 + gap} from the last, so the gap
     * between their edges is {@code gap} (2 or 3: a running jump). A wobble across the line from the seed, so it never
     * reads as a ruler.
     */
    public static List<Stone> line(int x0, int z0, int top0, int x1, int z1, int top1, int r, int gap, long seed) {
        double len = Math.hypot(x1 - x0, z1 - z0), step = 2 * r + 1 + gap;
        int n = Math.max(2, (int) Math.round(len / step) + 1);
        double ux = (x1 - x0) / Math.max(1, len), uz = (z1 - z0) / Math.max(1, len);
        List<Stone> out = new ArrayList<>(n);
        int last = top0;
        for (int i = 0; i < n; i++) {
            double f = i / (double) (n - 1);
            // the ends stay where they're asked for; between them each stone leans a block to one side or the other
            double side = i == 0 || i == n - 1 ? 0 : (Math.floorMod(seed * 31 + i * 0x9E37L, 3) - 1) * 0.9;
            int x = (int) Math.round(x0 + (x1 - x0) * f - uz * side), z = (int) Math.round(z0 + (z1 - z0) * f + ux * side);
            int want = (int) Math.round(top0 + (top1 - top0) * f), top = Math.max(last - 1, Math.min(last + 1, want));
            if (i == 0) top = top0;
            out.add(new Stone(x, z, r, top));
            last = top;
        }
        return out;
    }

    /**
     * As {@link #line}, but each top stands {@code lift} over the ground under it (a stair up a slope), still never more
     * than one block up or down from the last. Where the ground climbs faster than that, a stone stands lower over it.
     */
    public static List<Stone> follow(int x0, int z0, int x1, int z1, int lift, int r, int gap, long seed, Land land) {
        List<Stone> flat = line(x0, z0, 0, x1, z1, 0, r, gap, seed);
        List<Stone> out = new ArrayList<>(flat.size());
        int last = Integer.MIN_VALUE;
        for (Stone s : flat) {
            int want = land.ground(s.x(), s.z()) + lift, top = last == Integer.MIN_VALUE ? want : Math.max(last - 1, Math.min(last + 1, want));
            out.add(new Stone(s.x(), s.z(), r, top));
            last = top;
        }
        return on(out, land);
    }

    /**
     * The same stones with a way on for anyone, no move needed: the first one's top a block over the ground beside it (a
     * jump up), and each after it never more than one up from the last, so the line climbs to its height a step a stone.
     * Only ever lowers a stone.
     */
    public static List<Stone> on(List<Stone> line, Land land) {
        List<Stone> out = new ArrayList<>(line.size());
        int last = Integer.MIN_VALUE;
        for (Stone s : line) {
            int top = last == Integer.MIN_VALUE ? Math.min(s.top(), land.ground(s.x(), s.z()) + 1) : Math.min(s.top(), last + 1);
            out.add(new Stone(s.x(), s.z(), s.r(), top));
            last = top;
        }
        return out;
    }

    /** Where a fall from these stones is caught, back onto the last one stood on. */
    public Land.Course course(String leg, Land land) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        List<int[]> ledges = new ArrayList<>(stones.size());
        for (Stone s : stones) {
            low = Math.min(low, land.ground(s.x(), s.z()));
            high = Math.max(high, s.top());
            ledges.add(new int[]{s.x(), s.top() + 1, s.z()});
        }
        return new Land.Course(leg, x0 - 4, Math.min(low, land.water()) - 3, z0 - 4, x1 + 4, high + 10, z1 + 4, ledges);
    }

    /** The first stone's feet and the last's: where the stretch starts and ends. */
    public int[] start() {
        Stone s = stones.getFirst();
        return new int[]{s.x(), s.top() + 1, s.z()};
    }

    public int[] end() {
        Stone s = stones.getLast();
        return new int[]{s.x(), s.top() + 1, s.z()};
    }

    /**
     * Down to what carries a stone: a drift, a plant or a crown's leaves hold a boot but not a stone (the float check finds
     * a stone left on a frost drift or a sand drift), so the footing goes on through them to the ground.
     */
    private void footed(V v, int x, int y, int z) {
        for (int k = y; k >= 0; k--) {
            BlockState at = v.get(x, k, z);
            if (V.holds(at) && Kinds.of(at) == Kinds.BODY && !(at.getBlock() instanceof LeavesBlock)) return;
            v.set(x, k, z, body);
        }
    }

    @Override
    public void draw(V v, Mats m) {
        for (Stone s : stones) {
            int r = s.r(), rr = r * r + r;
            if (s.x() + r < v.x0 - 1 || s.x() - r > v.x0 + 16 || s.z() + r < v.z0 - 1 || s.z() - r > v.z0 + 16) continue;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > rr) continue;
                int x = s.x() + dx, z = s.z() + dz;
                boolean rim = dx * dx + dz * dz > (r - 1) * (r - 1) + (r - 1);
                v.set(x, s.top(), z, cap);
                // a band of the land's accent a few blocks under the top, so a stone reads as cut and set, not grown
                v.set(x, s.top() - 1, z, body);
                v.set(x, s.top() - 2, z, rim ? band : body);
                footed(v, x, s.top() - 3, z);
            }
            // nothing grows or rests on a stone you're meant to land on
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int y = s.top() + 1; y <= s.top() + 3; y++) {
                if (dx * dx + dz * dz <= rr) v.set(s.x() + dx, y, s.z() + dz, null);
            }
        }
    }
}
