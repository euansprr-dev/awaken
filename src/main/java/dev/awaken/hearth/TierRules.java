package dev.awaken.hearth;

import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiers grow from the home (SPEC §28.8, PLAN 4.8, Enshrouded's ladder): beside the rank and the metal each tier already
 * asks for ({@link Upgrades#anchorTier}: iron at II, gold and amethyst at III, diamonds at IV, diamonds, obsidian and a
 * Lord's Heart at V), the home must have grown. II Steading: four rooms, one of them Fine, and a field or a pen. III Hall:
 * a Grand room. IV Village: a bell tower and six rooms. V Town: ten rooms, two of them Grand (SPEC §43.6: nobody moves in
 * any more, so the tiers read what you built; the beasts' nooks grow with the tier). The Anchor's flame shows the tier.
 */
public final class TierRules {
    private TierRules() {}

    /** One thing a tier asks of the home, and whether it stands. */
    public record Need(String text, boolean met) {}

    /** What the home needs for {@code target} (2-5), each ticked against the last reading. */
    public static List<Need> needs(HearthRecord h, int target) {
        List<Need> out = new ArrayList<>();
        switch (target) {
            case 2 -> {
                boolean fine = false;
                for (Room r : h.rooms) fine |= r.reading().grade().ordinal() >= Reading.Grade.FINE.ordinal();
                out.add(new Need("four rooms (" + Math.min(h.rooms.size(), 4) + "/4)", h.rooms.size() >= 4));
                out.add(new Need("one of them Fine", fine));
                out.add(new Need("a field or a pen", (h.land & (Comfort.FIELD | Comfort.PEN)) != 0));
            }
            case 3 -> {
                boolean grand = false;
                for (Room r : h.rooms) grand |= r.reading().grade() == Reading.Grade.GRAND;
                out.add(new Need("a Grand room", grand));
            }
            case 4 -> {
                out.add(new Need("a bell tower", h.hasRoom(RoomType.BELL_TOWER)));
                out.add(new Need("six rooms (" + Math.min(h.rooms.size(), 6) + "/6)", h.rooms.size() >= 6));
            }
            case 5 -> {
                int grand = 0;
                for (Room r : h.rooms) if (r.reading().grade() == Reading.Grade.GRAND) grand++;
                out.add(new Need("ten rooms (" + Math.min(h.rooms.size(), 10) + "/10)", h.rooms.size() >= 10));
                out.add(new Need("two of them Grand (" + Math.min(grand, 2) + "/2)", grand >= 2));
            }
            default -> {
            }
        }
        return out;
    }

    public static boolean met(HearthRecord h, int target) {
        for (Need n : needs(h, target)) if (!n.met()) return false;
        return true;
    }

    /** The tier's name (PLAN 4.8). */
    public static String name(int tier) {
        return switch (tier) {
            case 0 -> "Hearthfire";
            case 1 -> "Homestead";
            case 2 -> "Steading";
            case 3 -> "Hall";
            case 4 -> "Village";
            default -> "Town";
        };
    }

    /** From Tier IV your home is a village: named on the Atlas, "&lt;NAME&gt; · your village". */
    public static boolean village(HearthRecord h) {
        return h.tier >= 4;
    }
}
