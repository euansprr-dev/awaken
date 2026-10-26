package dev.awaken.hearth.settle;

import dev.awaken.citizens.Roster;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * The face each settler wears (a skin texture under {@code textures/entity/}): Carillon's own for the townsfolk
 * ({@code townsfolk/oren}: the city's named four, the same people), the Wayfarers' ({@code wayfarer/ilse}), and for
 * someone from a village or the city one of Carillon's generated faces of their trade, picked by their id so they look
 * the same on the road in and every day after. The people engine reads it through the roster's {@code named} field
 * ({@code skin:<texture>[:slim]}, SPEC §28.8).
 */
final class Faces {
    private Faces() {}

    static String texture(Folk.Person p) {
        if (!p.face().isEmpty()) return p.face();
        int rec = citizen(p);
        return rec < 0 ? "townsfolk/oren" : String.format(Locale.ROOT, "citizen/c%03d", rec);
    }

    static boolean slim(Folk.Person p) {
        if (!p.face().isEmpty()) return p.slim();
        Roster r = Roster.get();
        int rec = citizen(p);
        return r != null && rec >= 0 && r.build[rec] == 1;
    }

    /** What the people engine's roster carries for them. */
    static String spec(Folk.Person p) {
        return "skin:" + texture(p) + (slim(p) ? ":slim" : "");
    }

    /** A Carillon record of their trade (not one of the named, grown), by their id. */
    private static int citizen(Folk.Person p) {
        Roster r = Roster.get();
        if (r == null) return -1;
        int job = switch (p.trade()) {
            case "smith" -> Roster.SMITH;
            case "farmer", "shepherd" -> Roster.PORTER;
            case "miner" -> Roster.MASON;
            case "healer" -> Roster.HEALER;
            case "archivist" -> Roster.ARCHIVIST;
            case "watch" -> Roster.WATCH;
            case "runner" -> Roster.RUNNER;
            default -> Roster.RESIDENT;
        };
        int[] pool = new int[r.citizens];
        int n = 0;
        for (int i = 0; i < r.citizens; i++) if (r.named[i].isEmpty() && r.job[i] == job && r.age[i] >= 1 && r.age[i] <= 2) pool[n++] = i;
        if (n == 0) for (int i = 0; i < r.citizens; i++) if (r.named[i].isEmpty() && r.age[i] >= 1 && r.age[i] <= 2) pool[n++] = i;
        if (n == 0) return -1;
        long h = 0xcbf29ce484222325L;
        for (byte b : p.id().getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return Arrays.copyOf(pool, n)[(int) Math.floorMod(h, (long) n)];
    }
}
