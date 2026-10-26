package dev.awaken.city;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.city.CityGrammar.STEP;

/**
 * One storey's floor plan while {@link CityInteriors} furnishes it: the cells inside the walls, the windows and door
 * beside them, what stands in each and whether you can still walk from the way in to everything that needs reaching.
 * Every piece is taken through {@link Floor#take}, which keeps it only if the door (or the top of the stairs) still
 * reaches every bed, every chest's front, the stairs and each other target along a path one wide and two tall.
 */
final class CityRooms {
    private CityRooms() {}

    /** A cell's state: open floor, floor that must stay open (door, stairs' ends), furniture, no floor at all. */
    static final byte FREE = 0, KEEP = 1, USED = 2, HOLE = 3;
    /** What a cell holds, where it matters later (no lantern over the stairs or the hearth). */
    static final byte K_NONE = 0, K_STAIR = 1, K_HEARTH = 2, K_TABLE = 3, K_BED = 4, K_SEAT = 5, K_THING = 6, K_LADDER = 7;

    static final class Floor {
        /** Storey index (0 the ground), the inside's north-west cell and size, its floor's row over the plot's floor, its air rows. */
        final int idx, x0, z0, w, d, base, rows;
        final byte[] state;
        /** The rows of glass (or the door) in the wall beside a cell, bit r for row r; 0 where the wall is blank. */
        final byte[] glass;
        /** The sides (bits 1 << side) where the cell has a wall. */
        final byte[] walls;
        /** The highest row a cell's furniture fills, and whether its top takes a pot, a candle or a lantern. */
        final byte[] height;
        final boolean[] shelf;
        /** The wall a piece backs onto (-1 none), and what the cell holds ({@link #K_NONE}...). */
        final byte[] against, kind;
        /** What stands in each cell's air rows ([cell * rows + row - 1]), and the floor block where it isn't the palette's. */
        final BlockState[] block, floorAt;
        /** Where you come in: the door's inner cell, the top of the stairs (-1: anywhere). */
        int start = -1;
        /** Cells that must stay reachable, and needs (any one of the cells reachable: a bed's side, a chest's front). */
        final List<Integer> targets = new ArrayList<>();
        final List<int[]> needs = new ArrayList<>();
        /** Whether this storey's light (its one lantern, lit stove or forge) is placed. */
        boolean lit;
        private final boolean[] seen;
        private final int[] queue;

        Floor(int idx, int x0, int z0, int x1, int z1, int base, int rows) {
            this.idx = idx;
            this.x0 = x0;
            this.z0 = z0;
            this.w = x1 - x0 + 1;
            this.d = z1 - z0 + 1;
            this.base = base;
            this.rows = rows;
            int n = w * d;
            state = new byte[n];
            glass = new byte[n];
            walls = new byte[n];
            height = new byte[n];
            shelf = new boolean[n];
            against = new byte[n];
            kind = new byte[n];
            java.util.Arrays.fill(against, (byte) -1);
            block = new BlockState[n * rows];
            floorAt = new BlockState[n];
            seen = new boolean[n];
            queue = new int[n];
        }

        int n() {
            return w * d;
        }

        int cell(int x, int z) {
            return x < x0 || z < z0 || x >= x0 + w || z >= z0 + d ? -1 : (x - x0) + (z - z0) * w;
        }

        int x(int k) {
            return x0 + k % w;
        }

        int z(int k) {
            return z0 + k / w;
        }

        /** The neighbouring cell on a side, or -1 outside. */
        int step(int k, int side) {
            return k < 0 ? -1 : cell(x(k) + STEP[side][0], z(k) + STEP[side][1]);
        }

        boolean wall(int k, int side) {
            return k >= 0 && (walls[k] & (1 << side)) != 0;
        }

        boolean corner(int k) {
            return Integer.bitCount(walls[k]) >= 2;
        }

        boolean free(int k) {
            return k >= 0 && state[k] == FREE;
        }

        /** Whether something filling rows 1 to top stands clear of the glass beside the cell (only lower things before a window). */
        boolean clear(int k, int top) {
            return (glass[k] & ((2 << top) - 2)) == 0;
        }

        /** The lowest row of glass beside a cell (0: none). */
        int sill(int k) {
            return glass[k] == 0 ? 0 : Integer.numberOfTrailingZeros(glass[k]);
        }

        int dist(int a, int b) {
            return Math.abs(x(a) - x(b)) + Math.abs(z(a) - z(b));
        }

        /** Manhattan distance from a cell to the inside's middle, doubled (so even sizes have a middle too). */
        int fromMiddle(int k) {
            return Math.abs(2 * (k % w) - (w - 1)) + Math.abs(2 * (k / w) - (d - 1));
        }

        void put(int k, int r, BlockState s) {
            if (k < 0 || r < 1 || r > rows) return;
            block[k * rows + r - 1] = s;
            if (r > height[k]) height[k] = (byte) r;
        }

        BlockState at(int k, int r) {
            return k < 0 || r < 1 || r > rows ? null : block[k * rows + r - 1];
        }

        /** Marks cells as furniture and adds needs; keeps them only if every target and need can still be walked to. */
        boolean take(int[] cells, int[]... more) {
            for (int k : cells) if (!free(k)) return false;
            for (int k : cells) state[k] = USED;
            int added = 0;
            for (int[] need : more) {
                if (need == null) continue;
                needs.add(need);
                added++;
            }
            if (ok()) return true;
            for (int k : cells) state[k] = FREE;
            for (int i = 0; i < added; i++) needs.removeLast();
            return false;
        }

        /** Sets cells' state without asking (stairs, holes, the flue: they come first). */
        void mark(int k, byte s) {
            if (k >= 0) state[k] = s;
        }

        /** Whether the way in reaches every target and at least one cell of every need. */
        boolean ok() {
            if (start < 0) return true;
            walk();
            for (int t : targets) if (!seen[t]) return false;
            for (int[] need : needs) {
                boolean any = false;
                for (int k : need) {
                    if (k >= 0 && seen[k]) {
                        any = true;
                        break;
                    }
                }
                if (!any) return false;
            }
            return true;
        }

        private void walk() {
            java.util.Arrays.fill(seen, false);
            if (state[start] == USED || state[start] == HOLE) return;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int k = queue[head++];
                for (int s = 0; s < 4; s++) {
                    int m = step(k, s);
                    if (m < 0 || seen[m] || state[m] == USED || state[m] == HOLE) continue;
                    seen[m] = true;
                    queue[tail++] = m;
                }
            }
        }

        /** The open cells round a set (orthogonal neighbours not in it): any one of them reachable is a way to it. */
        int[] around(int... cells) {
            int[] out = new int[cells.length * 4];
            int n = 0;
            for (int k : cells) {
                for (int s = 0; s < 4; s++) {
                    int m = step(k, s);
                    if (m < 0) continue;
                    boolean mine = false;
                    for (int c : cells) mine |= c == m;
                    if (!mine) out[n++] = m;
                }
            }
            return java.util.Arrays.copyOf(out, n);
        }
    }
}
