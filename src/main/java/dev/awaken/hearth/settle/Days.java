package dev.awaken.hearth.settle;

import dev.awaken.citizens.api.RosterBuilder;
import org.jspecify.annotations.Nullable;

/**
 * A day at home for each settler (time of day, 0 = 06:00), the village's rhythm in your house: up at six and breakfast
 * at the table, work from seven or so, the midday meal, work again, supper, the evening by the fire, then bed. Their
 * work is where their room is: Oren at the anvil in the Forge, Hettie at the field's edge (the pen after the morning),
 * Ilse at the Greenhouse's soil, Teodor at the Study's lectern, Mira at the Lookout's window (and the yard's edge with a
 * lantern at dusk), Sun in the Sanctuary, the miner down the shaft from the morning until the evening's haul comes up.
 * Wren, Pell and the hands go about the place. Nudged per person so a house never moves as one.
 */
final class Days {
    private Days() {}

    /** Start {@code start} nudged by up to ±{@code k} ticks for person {@code i}. */
    private static int at(int i, int start, int k) {
        long z = (i + 1) * 0x9E3779B97F4A7C15L ^ start * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 31)) * 0xbf58476d1ce4e5b9L;
        return Math.floorMod(start + (int) Math.floorMod(z, (long) (2 * k + 1)) - k, 24000);
    }

    /** When record {@code i} sets out from home for breakfast, and for the morning's work (time of day). */
    static int breakfast(int i) {
        return at(i, 350, 150);
    }

    static int work(int i) {
        return at(i, 1000, 150);
    }

    /** When the miner who is record {@code i} comes up the shaft with the evening's haul (time of day). */
    static int surfaces(int i) {
        return at(i, Work.HAUL_TOD, 60);
    }

    static void of(RosterBuilder.Person p, int i, String role, int home, @Nullable Integer work, @Nullable Integer work2, int meal, int fire,
                   @Nullable Integer watch, int errands) {
        if (work != null) p.work(work);
        p.segment(0, "home", -1);
        p.segment(at(i, 350, 150), "stand", meal); // breakfast
        switch (role) {
            case "miner" -> {
                // down the shaft (going in at the hatch is going down) from the morning; up with the haul at evening
                if (work != null) p.segment(at(i, 900, 120), "inside", work);
                else p.segment(at(i, 900, 120), "wander", errands, -1, 400, 0);
                p.segment(at(i, Work.HAUL_TOD, 60), "stand", meal);
                p.segment(at(i, 12400, 200), "stand", fire);
                p.segment(at(i, 14600, 200), "home", -1);
                return;
            }
            case "wren", "pell", "hand", "vael" -> {
                p.segment(at(i, 1100, 200), "wander", errands, -1, 600, 0);
                p.segment(at(i, 5400, 150), "stand", meal);
                p.segment(at(i, 6500, 200), "wander", errands, -1, 600, 0);
            }
            default -> {
                int morning = work != null ? work : -1;
                if (morning >= 0) p.segment(at(i, 1000, 150), "stand", morning);
                else p.segment(at(i, 1000, 150), "wander", errands, -1, 500, 0);
                // Hettie: the pen after the morning's field
                if (work2 != null) p.segment(at(i, 3800, 150), "stand", work2);
                p.segment(at(i, 5400, 150), "stand", meal);
                int afternoon = work2 != null && work != null ? work : morning;
                if (afternoon >= 0) p.segment(at(i, 6500, 150), "stand", afternoon);
                else p.segment(at(i, 6500, 150), "wander", errands, -1, 500, 0);
            }
        }
        p.segment(at(i, 11300, 150), "stand", meal); // supper
        if ("mira".equals(role) && watch != null) {
            // the watch at dusk, a lantern in hand, then the fire
            p.segment(at(i, 12200, 100), "stand", watch);
            p.segment(at(i, 13800, 150), "stand", fire);
            p.segment(at(i, 15200, 200), "home", -1);
            return;
        }
        p.segment(at(i, 12300, 250), "stand", fire);
        p.segment(at(i, 14200 + (i % 3) * 300, 200), "home", -1);
    }
}
