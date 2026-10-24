package dev.awaken.hearth.guide;

import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Miss;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Why a room isn't recognised yet, in plain words, from the scanner's own result: a closed room missing furniture
 * ("missing: a Meditation Mat"), or furniture whose space leaks ("it sees the sky above the bed (no roof)", "an opening
 * at x y z", "too big (over 32 wide)"). Pure: the Hearth guide's card, the Now tracker line and the tests share it.
 */
public final class WhyNot {
    private WhyNot() {}

    public enum Kind { MISSING, SKY, ROOF_HOLE, OPENING, TOO_BIG }

    /**
     * {@code card}: the guide's line. {@code tracker}: one Now line that fits; {@code compass} says whether it fits with a
     * compass too. {@code at}: where to look (null = nowhere in particular); {@code mark}: worth a marker in the world.
     */
    public record Reason(RoomType type, Kind kind, String card, String tracker, boolean compass, @Nullable BlockPos at, boolean mark) {}

    /** The best explanation for {@code type}, or empty when it is built or there's no furniture for it in the radius. */
    public static Optional<Reason> of(RoomType type, List<Room> rooms, List<Miss> misses) {
        for (Room r : rooms) if (r.types().contains(type)) return Optional.empty();
        // A closed room with some of the furniture: the closest thing to done.
        Room best = null;
        int bestHave = 0;
        for (Room r : rooms) {
            int have = 0;
            for (Furniture f : type.furniture) if (r.count(f) > 0) have++;
            if (have > bestHave) {
                best = r;
                bestHave = have;
            }
        }
        if (best != null) {
            List<Furniture> missing = new ArrayList<>();
            for (Furniture f : type.furniture) if (best.count(f) == 0) missing.add(f);
            return Optional.of(missing(type, missing, best.centre().above()));
        }
        // Furniture whose space leaks, in the order the room lists it (the bed before the mat).
        for (Furniture f : type.furniture) {
            for (Miss m : misses) if (m.furniture() == f) return Optional.of(leak(type, m));
        }
        return Optional.empty();
    }

    static Reason missing(RoomType type, List<Furniture> missing, BlockPos at) {
        StringBuilder labels = new StringBuilder(), shorts = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            String sep = i == 0 ? "" : i == missing.size() - 1 ? " and " : ", ";
            labels.append(sep).append(missing.get(i).label);
            shorts.append(i == 0 ? "" : ", ").append(article(missing.get(i).shortName)).append(missing.get(i).shortName);
        }
        String card = "Closed and roofed, but missing: " + labels + ".";
        String tracker = Fit.first(Fit.TRACKER, type.label + ": add " + shorts, type.label + ": " + missing.size() + " to add");
        return new Reason(type, Kind.MISSING, card, tracker, false, at, false);
    }

    static Reason leak(RoomType type, Miss m) {
        String where = m.at().getX() + " " + m.at().getY() + " " + m.at().getZ();
        return switch (m.leak()) {
            case SKY -> new Reason(type, Kind.SKY, "Not a room yet: it sees the sky above " + m.furniture().the + " (no roof).",
                    Fit.first(Fit.TRACKER_WITH_COMPASS, type.label + ": no roof", type.label + ": roof"), true, m.at(), true);
            case ROOF_HOLE -> new Reason(type, Kind.ROOF_HOLE, "Not a room yet: it sees the sky at " + where + " (a hole in the roof).",
                    Fit.first(Fit.TRACKER_WITH_COMPASS, type.label + ": roof hole", type.label + ": hole"), true, m.at(), true);
            case OPENING -> new Reason(type, Kind.OPENING, "Not a room yet: an opening at " + where
                    + ". Close it with a block, a door or a glass pane.",
                    Fit.first(Fit.TRACKER_WITH_COMPASS, type.label + ": wall gap", type.label + ": gap"), true, m.at(), true);
            case TOO_BIG -> new Reason(type, Kind.TOO_BIG, "Not a room yet: too big (over 32 wide). Wall off a smaller room around "
                    + m.furniture().the + ".", Fit.first(Fit.TRACKER_WITH_COMPASS, type.label + ": too big", type.label + ": big"),
                    true, m.seed(), false);
        };
    }

    private static String article(String word) {
        return "aeiouAEIOU".indexOf(word.charAt(0)) >= 0 ? "an " : "a ";
    }
}
