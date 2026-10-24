package dev.awaken.hearth.room;

import java.util.List;
import java.util.function.ToIntFunction;

import static dev.awaken.hearth.room.Furniture.ANVIL;
import static dev.awaken.hearth.room.Furniture.BED;
import static dev.awaken.hearth.room.Furniture.FORGE;
import static dev.awaken.hearth.room.Furniture.MACHINE;
import static dev.awaken.hearth.room.Furniture.MAT;
import static dev.awaken.hearth.room.Furniture.PAD;
import static dev.awaken.hearth.room.Furniture.STAND;
import static dev.awaken.hearth.room.Furniture.TROPHY;

/**
 * Rooms the Hearth recognises from what an enclosed space holds. No menus: build it and it counts. {@link #furniture}
 * is the rule ({@link Room#classify} and the Hearth guide both read it), {@link #does} says what the room gives in plain
 * words, {@link #toast} is the same as one line short enough for a toast.
 */
public enum RoomType {
    SANCTUARY("Sanctuary", List.of(BED, MAT), "better dreams, faster healing",
            "Sleep here to heal a Fracture and wake with Regeneration. Resting here mends in half the time, and dreams come more often.",
            "Sleep here: Fracture heals."),
    ARMORY("Armory", List.of(ANVIL, FORGE, STAND), "the Forge can temper higher",
            "Your Forge tempers gear past level I here: two levels for every Forge tier.",
            "Your Forge tempers higher."),
    TROPHY_HALL("Trophy Hall", List.of(TROPHY), "a bonus for every different boss",
            "Every different Lord's trophy hung here gives +1% damage and +0.5 max health.",
            "+1% damage for each Lord."),
    WORKSHOP("Workshop", List.of(MACHINE), "a place for the Above's machines",
            "A room for the Precursor machines you build at the Fabricator.",
            "Your machines have a home."),
    BARRACKS("Barracks", List.of(PAD), "Echoes on duty rest and train better",
            "Echoes on role pads here heal four times faster, and Train pads train twice as fast.",
            "Echoes here heal and train.");

    public final String label;
    /** Everything the room needs (all of it, in one closed room). */
    public final List<Furniture> furniture;
    /** "a bed and a Meditation Mat". */
    public final String needs;
    public final String gives;
    public final String does;
    public final String toast;

    RoomType(String label, List<Furniture> furniture, String gives, String does, String toast) {
        this.label = label;
        this.furniture = furniture;
        this.needs = joinLabels(furniture);
        this.gives = gives;
        this.does = does;
        this.toast = toast;
    }

    /** Whether a room holding these counts is this type: at least one of every piece of furniture it needs. */
    public boolean metBy(ToIntFunction<Furniture> count) {
        for (Furniture f : furniture) if (count.applyAsInt(f) <= 0) return false;
        return true;
    }

    public boolean uses(Furniture f) {
        return furniture.contains(f);
    }

    static String joinLabels(List<Furniture> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(i == list.size() - 1 ? " and " : ", ");
            b.append(list.get(i).label);
        }
        return b.toString();
    }
}
