package dev.awaken.hearth;

import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Materials;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Scan;
import dev.awaken.hearth.room.Tally;

import java.util.ArrayList;
import java.util.List;

/**
 * A home's Comfort and Style (SPEC §28.2, PLAN 4.5), from its last reading. Comfort is the best of each kind, never a
 * stack: your best bedroom's grade (0-3), a warm hearth, a table with seats, soft things, plants, and the land's
 * features (a field, a pen, a garden, a lit yard, a wall), up to {@link #MAX}. Style is what the home is made of, in
 * words ("timber and fieldstone"). Pure: the scan's end, the guide and the tests share it.
 */
public final class Comfort {
    private Comfort() {}

    public static final int MAX = 12;
    /** The land's features as bits (saved with the Hearth). */
    public static final int FIELD = 1, PEN = 2, GARDEN = 4, YARD = 8, WALL = 16;

    public static int land(Scan.Land l) {
        return (l.field() ? FIELD : 0) | (l.pen() ? PEN : 0) | (l.garden() ? GARDEN : 0) | (l.litYard() ? YARD : 0) | (l.wall() ? WALL : 0);
    }

    /** The home's Comfort from its rooms and the land's feature bits. */
    public static int of(List<Room> rooms, int land) {
        int bedroom = 0;
        boolean warm = false, table = false, soft = false, plants = false;
        for (Room r : rooms) {
            if (r.types().contains(RoomType.BEDROOM) || r.types().contains(RoomType.SANCTUARY)) bedroom = Math.max(bedroom, r.reading().grade().ordinal());
            warm |= r.reading().warmth() >= 2;
            table |= r.fact(Tally.F_TABLES) >= 1 && r.fact(Tally.F_SEATS) >= 2;
            soft |= r.fact(Tally.F_SOFT) >= 2;
            plants |= r.count(Furniture.GROWING) > 0;
        }
        return bedroom + (warm ? 1 : 0) + (table ? 1 : 0) + (soft ? 1 : 0) + (plants ? 1 : 0) + Integer.bitCount(land & 31);
    }

    /** What would add a point next, in a few words (the guide), or "" at the top. */
    public static String next(List<Room> rooms, int land) {
        int bedroom = -1;
        boolean warm = false, table = false, soft = false, plants = false;
        for (Room r : rooms) {
            if (r.types().contains(RoomType.BEDROOM) || r.types().contains(RoomType.SANCTUARY)) bedroom = Math.max(bedroom, r.reading().grade().ordinal());
            warm |= r.reading().warmth() >= 2;
            table |= r.fact(Tally.F_TABLES) >= 1 && r.fact(Tally.F_SEATS) >= 2;
            soft |= r.fact(Tally.F_SOFT) >= 2;
            plants |= r.count(Furniture.GROWING) > 0;
        }
        if (bedroom < 0) return "a bedroom: a bed in a closed room";
        if (!warm) return "a warm room: a lit stove or an open fire";
        if (bedroom < 2) return "a finer bedroom";
        if (!table) return "a table and seats";
        if (!soft) return "soft things: carpets, banners, paintings";
        if (!plants) return "plants: a pot or soil indoors";
        if ((land & FIELD) == 0) return "a field (16 farmland)";
        if ((land & YARD) == 0) return "a lit yard (six lights outdoors)";
        if ((land & GARDEN) == 0) return "a garden (a composter and flowers)";
        if ((land & PEN) == 0) return "a pen (animals by fences)";
        if ((land & WALL) == 0) return "a wall round the place";
        if (bedroom < 3) return "a grand bedroom";
        return "";
    }

    /** "timber and fieldstone": the two families most of the home is made of ("" before any room). */
    public static String style(int[] families) {
        int first = -1, second = -1;
        for (int i = 0; i < families.length; i++) {
            if (i == Materials.Family.OTHER.ordinal() || families[i] <= 0) continue;
            if (first < 0 || families[i] > families[first]) {
                second = first;
                first = i;
            } else if (second < 0 || families[i] > families[second]) {
                second = i;
            }
        }
        if (first < 0) return "";
        String a = Materials.Family.values()[first].word;
        if (second < 0 || families[second] * 5 < families[first]) return a;
        return a + " and " + Materials.Family.values()[second].word;
    }

    /** The Rested minutes this Comfort gives (8 and one a point). */
    public static int restedMinutes(int comfort) {
        return 8 + Math.max(0, Math.min(MAX, comfort));
    }

    /** The land's features in words, for the guide. */
    public static List<String> landWords(int land) {
        List<String> out = new ArrayList<>(5);
        if ((land & FIELD) != 0) out.add("a field");
        if ((land & PEN) != 0) out.add("a pen");
        if ((land & GARDEN) != 0) out.add("a garden");
        if ((land & YARD) != 0) out.add("a lit yard");
        if ((land & WALL) != 0) out.add("a wall");
        return out;
    }

    /** A room's grade word with its name, for a card ("Fine · the fine timber bedroom"). */
    public static String gradeLine(Reading r) {
        return r.grade().label + " · " + r.name();
    }
}
