package dev.awaken.gates.world;

/**
 * Rune puzzles: a line of runes where pressing one flips it and its neighbours. The start state is made by pressing a
 * few runes on a solved line, so every puzzle is solvable (press the same runes again).
 */
public final class Puzzles {
    private Puzzles() {}

    /** Initial lit state for rune i of a puzzle room. */
    public static boolean initiallyLit(Room r, int count, int i) {
        return (initialMask(r, count) & (1 << i)) != 0;
    }

    public static int initialMask(Room r, int count) {
        int all = (1 << count) - 1;
        int mask = all;
        int presses = 2 + (int) Math.floorMod(r.seed >> 5, 2L);
        int used = 0;
        for (int k = 0; k < presses; k++) {
            int p = Hash.range(r.seed, k, 31, count);
            if ((used & (1 << p)) != 0) p = (p + 1) % count;
            used |= 1 << p;
            mask = press(mask, p, count);
        }
        if (mask == all) mask = press(mask, count / 2, count);
        return mask;
    }

    public static int press(int mask, int i, int count) {
        for (int j = i - 1; j <= i + 1; j++) if (j >= 0 && j < count) mask ^= 1 << j;
        return mask;
    }

    public static boolean solved(int mask, int count) {
        return mask == (1 << count) - 1;
    }
}
