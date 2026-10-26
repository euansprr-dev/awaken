package dev.awaken.hearth.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumSet;
import java.util.List;

/**
 * What the Hearth sees in a closed room (SPEC §28.2, PLAN 4.5): six readings 0-3 (closed, light, warmth, room to live,
 * made with care, detail and comfort), the grade they make (the average, never more than one step above the weakest:
 * a grand hall with one dark corner is Fine until you light it), the room's name (its grade, main material and purpose,
 * or the words on a sign inside), and the one thing that would lift it most. Pure: the scanner, the tests and the room
 * panel share it.
 */
public record Reading(int closed, int light, int warmth, int space, int care, int detail, Grade grade, String name, String lift,
                      RoomType purpose) {

    public enum Grade {
        BARE("Bare", "bare"), SNUG("Snug", "snug"), FINE("Fine", "fine"), GRAND("Grand", "grand");

        public final String label, word;

        Grade(String label, String word) {
            this.label = label;
            this.word = word;
        }
    }

    /** The six readings' short names, in order (the room panel's pips). */
    public static final List<String> NAMES = List.of("Closed", "Light", "Warmth", "Room", "Care", "Detail");

    public int at(int i) {
        return switch (i) {
            case 0 -> closed;
            case 1 -> light;
            case 2 -> warmth;
            case 3 -> space;
            case 4 -> care;
            default -> detail;
        };
    }

    public int weakest() {
        int w = 3;
        for (int i = 0; i < 6; i++) w = Math.min(w, at(i));
        return w;
    }

    public static final Codec<Reading> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("readings").forGetter(r -> List.of(r.closed, r.light, r.warmth, r.space, r.care, r.detail)),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Reading::name),
            Codec.STRING.optionalFieldOf("lift", "").forGetter(Reading::lift),
            Codec.STRING.optionalFieldOf("purpose", "SHELTER").forGetter(r -> r.purpose.name())
    ).apply(i, Reading::saved));

    private static Reading saved(List<Integer> v, String name, String lift, String purpose) {
        int[] r = new int[6];
        for (int k = 0; k < 6 && k < v.size(); k++) r[k] = Math.max(0, Math.min(3, v.get(k)));
        RoomType p;
        try {
            p = RoomType.valueOf(purpose);
        } catch (IllegalArgumentException e) {
            p = RoomType.SHELTER;
        }
        return new Reading(r[0], r[1], r[2], r[3], r[4], r[5], grade(r), name, lift, p);
    }

    // ---- the rules ----------------------------------------------------------------------------------------------------

    /** Reads a room the scanner just walked. {@code types}: the kinds it was recognised as. */
    public static Reading of(Tally t, EnumSet<RoomType> types) {
        RoomType purpose = RoomType.SHELTER;
        for (RoomType k : RoomType.PURPOSE) {
            if (types.contains(k)) {
                purpose = k;
                break;
            }
        }
        int closed = closed(t.seeThrough, t.faces);
        int light = light(t.litFloor, t.floor, t.windows);
        int warmth = t.warmth;
        int space = space(t.floor, t.height, purpose);
        int care = care(t.worked, t.faces, t.workedKinds());
        int detail = detail(t.trimKinds(), t.softKinds());
        int[] r = {closed, light, warmth, space, care, detail};
        Grade g = grade(r);
        String name = t.sign.isBlank() ? name(g, t.mainFamily(), purpose) : t.sign.strip();
        return new Reading(closed, light, warmth, space, care, detail, g, name, lift(r, g, t, purpose), purpose);
    }

    /** Leaves, fences and bars close a room for the scan but let the wind through. */
    public static int closed(int seeThrough, int faces) {
        if (faces <= 0 || seeThrough == 0) return 3;
        return seeThrough * 100 <= faces * 15 ? 2 : 1;
    }

    /** All the floor lit at night and windows by day; one dark corner keeps it at 1. */
    public static int light(int lit, int floor, int windows) {
        boolean allLit = floor > 0 && lit * 100 >= floor * 97;
        boolean mostlyLit = floor > 0 && lit * 100 >= floor * 60;
        if (allLit) return windows > 0 ? 3 : 2;
        return mostlyLit || windows > 0 ? 1 : 0;
    }

    /** Floor for what the room is for (tall halls allowed), never a fixed size. */
    public static int space(int floor, int height, RoomType purpose) {
        double r = floor / (double) purpose.floorTarget;
        int s = r < 0.5 ? 0 : r < 1 ? 1 : r < 1.75 ? 2 : 3;
        if (height <= 2) s = Math.min(s, 1);
        else if (height < purpose.heightTarget) s = Math.min(s, 2);
        return s;
    }

    /** The share of worked blocks in its walls, floor and roof, and at least three kinds of them for the top mark. */
    public static int care(int worked, int faces, int kinds) {
        if (faces <= 0) return 0;
        double s = worked / (double) faces;
        if (s >= 0.85 && kinds >= 3) return 3;
        if (s >= 0.6 && kinds >= 2) return 2;
        return s >= 0.25 ? 1 : 0;
    }

    /** Trim (one kind, then three) and soft things (one kind, then two): the best of each kind counts, never a stack. */
    public static int detail(int trimKinds, int softKinds) {
        int d = (trimKinds >= 1 ? 1 : 0) + (trimKinds >= 3 ? 1 : 0) + (softKinds >= 1 ? 1 : 0) + (softKinds >= 2 ? 1 : 0);
        return Math.min(3, d);
    }

    /** The average of the six, but never more than one step above the weakest. */
    public static Grade grade(int[] r) {
        int sum = 0, weakest = 3;
        for (int v : r) {
            sum += v;
            weakest = Math.min(weakest, v);
        }
        double avg = sum / 6.0;
        int g = avg >= 2.5 ? 3 : avg >= 1.75 ? 2 : avg >= 1.25 ? 1 : 0;
        return Grade.values()[Math.min(g, weakest + 1)];
    }

    /** "the snug timber bedroom". */
    public static String name(Grade g, Materials.Family main, RoomType purpose) {
        String material = main.word.isEmpty() ? "" : main.word + " ";
        return "the " + g.word + " " + material + purpose.noun;
    }

    /** The one thing that would lift the room most: the weakest reading's fix ("" when it's Grand all round). */
    static String lift(int[] r, Grade g, Tally t, RoomType purpose) {
        int weakest = 3;
        for (int v : r) weakest = Math.min(weakest, v);
        if (weakest >= 3) return "";
        // the easiest fixes first when readings tie: light, warmth, detail, care, room, closed
        int[] order = {1, 2, 5, 4, 3, 0};
        for (int i : order) {
            if (r[i] != weakest) continue;
            return switch (i) {
                case 1 -> t.floor > 0 && t.litFloor * 100 < t.floor * 97
                        ? ("middle".equals(t.darkEnd()) ? "light the middle" : "light the " + t.darkEnd() + " end")
                        : "windows";
                case 2 -> t.warmth == 0 ? "a fire" : t.warmth == 1 ? "a lit fire: a campfire, a stove or a fireplace" : "an open fire";
                case 5 -> t.softKinds() == 0 ? "something soft: a carpet, a banner, a painting"
                        : t.trimKinds() == 0 ? "trim: stairs, slabs or trapdoors on the walls" : "another kind of detail";
                case 4 -> t.workedKinds() < 2 && t.faces > 0 && t.worked * 100 >= t.faces * 60 ? "a second kind of worked block"
                        : t.workedKinds() < 3 && t.faces > 0 && t.worked * 100 >= t.faces * 85 ? "a third kind of worked block"
                        : "worked blocks: planks, bricks, cut stone";
                case 3 -> t.height <= 2 ? "a higher ceiling" : t.height < purpose.heightTarget ? "a higher ceiling" : "more floor";
                default -> "close the gaps: leaves, fences and bars let the wind in";
            };
        }
        return "";
    }
}
