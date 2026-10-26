package dev.awaken.hearth.room;

import java.util.List;

import static dev.awaken.hearth.room.Furniture.ANVIL;
import static dev.awaken.hearth.room.Furniture.BARREL;
import static dev.awaken.hearth.room.Furniture.BED;
import static dev.awaken.hearth.room.Furniture.BELL;
import static dev.awaken.hearth.room.Furniture.BOOKSHELF;
import static dev.awaken.hearth.room.Furniture.COOKER;
import static dev.awaken.hearth.room.Furniture.CRAFTING;
import static dev.awaken.hearth.room.Furniture.FORGE;
import static dev.awaken.hearth.room.Furniture.GATE;
import static dev.awaken.hearth.room.Furniture.GROWING;
import static dev.awaken.hearth.room.Furniture.HAY;
import static dev.awaken.hearth.room.Furniture.LECTERN;
import static dev.awaken.hearth.room.Furniture.MACHINE;
import static dev.awaken.hearth.room.Furniture.MAT;
import static dev.awaken.hearth.room.Furniture.PAD;
import static dev.awaken.hearth.room.Furniture.SEAT;
import static dev.awaken.hearth.room.Furniture.STAND;
import static dev.awaken.hearth.room.Furniture.TABLE;
import static dev.awaken.hearth.room.Furniture.TROPHY;

/**
 * What rooms are for (SPEC §28.2, PLAN 4.6): each kind does something real. No menus: build it and it counts. A kind is
 * all of its {@link #furniture} (at least {@link Furniture#needed} of each) in one closed room, plus its
 * {@link #condition} where it has one (a glass roof, a big floor, height over the ground). Only furniture names a room's
 * purpose; how good the room is comes from the six readings ({@link Reading}), never from a named block.
 * The guide, the Now tracker and the tests read the rule from here. ARMORY keeps its id: it is the Forge room now.
 */
public enum RoomType {
    SHELTER("Shelter", "shelter", List.of(), null, 6, 2, "rest heals here",
            "Closed and roofed: rest heals here, and the Hearth reads everything you build from now on.", "Rest heals here."),
    BEDROOM("Bedroom", "bedroom", List.of(BED), null, 12, 3, "Rested when you wake",
            "Wake in your own bed at home and you're Rested: energy comes back faster and crafts learn faster, longer the more comfortable home is.",
            "Wake here Rested."),
    SANCTUARY("Sanctuary", "sanctuary", List.of(BED, MAT), null, 12, 3, "better dreams, faster healing",
            "Sleep here to heal a Fracture and wake with Regeneration. Resting here mends in half the time, and dreams come more often.",
            "Sleep here: Fracture heals."),
    KITCHEN("Kitchen", "kitchen", List.of(COOKER, CRAFTING, BARREL), null, 16, 3, "home-cooked meals last longer",
            "Meals made here are home-cooked: eating one makes what it gives you last half again as long.", "Home-cooked meals."),
    ARMORY("Forge", "forge", List.of(ANVIL, FORGE, STAND), null, 16, 3, "the Forge can temper higher",
            "Your Forge tempers gear past level I here: two levels for every Forge tier.", "Your Forge tempers higher."),
    STUDY("Study", "study", List.of(LECTERN, BOOKSHELF), null, 16, 3, "a place to read",
            "A lectern and shelves of books. Blueprints and the reading of relics will come here.", "A room to read in."),
    GREENHOUSE("Greenhouse", "greenhouse", List.of(GROWING), "under a glass roof", 20, 3, "a place for herbs",
            "Soil or planted pots under a glass roof. Herbs will grow faster here.", "Plants under glass."),
    STABLE("Stable", "stable", List.of(HAY, GATE), null, 20, 3, "your whistle reaches twice as far",
            "Hay and a gate: your bonded mount has a home, and your whistle carries twice as far.", "Your whistle carries further."),
    HALL("Hall", "hall", List.of(SEAT, TABLE), "a big floor (48 or more)", 64, 4, "a meal here feeds the hall",
            "A meal eaten in your hall feeds everyone in it.", "A meal here feeds everyone."),
    BELL_TOWER("Bell tower", "bell tower", List.of(BELL), "8 or more above the ground outside", 4, 3, "breaks warned two play-days early",
            "Your own bell: it rings at dawn and dusk, warns of gate breaks two play-days early, and Deep Tide nights stay calm on your land.",
            "Your bell keeps watch."),
    LOOKOUT("Lookout", "lookout", List.of(), "12 or more above the ground, windows on three sides", 9, 3, "the Atlas fills in round home",
            "From up here the Atlas fills in 256 blocks round home, and Mira hears one more rumour a play-day.", "The land round home is known."),
    TROPHY_HALL("Trophy Hall", "trophy hall", List.of(TROPHY), null, 24, 4, "a bonus for every different boss",
            "Every different Lord's trophy hung here gives +1% damage and +0.5 max health.", "+1% damage for each Lord."),
    WORKSHOP("Workshop", "workshop", List.of(MACHINE), null, 16, 3, "machines spend a quarter less",
            "Precursor machines in this room spend a quarter less essence.", "Machines spend less here."),
    BARRACKS("Barracks", "barracks", List.of(PAD), null, 20, 3, "Echoes on duty rest and train better",
            "Echoes on role pads here heal four times faster, and Train pads train twice as fast.", "Echoes here heal and train.");

    /** The order a room takes its purpose (and its name) in: the most particular first, a plain shelter last. */
    public static final List<RoomType> PURPOSE = List.of(SANCTUARY, HALL, KITCHEN, ARMORY, STUDY, GREENHOUSE, STABLE, BELL_TOWER,
            LOOKOUT, TROPHY_HALL, WORKSHOP, BARRACKS, BEDROOM, SHELTER);

    public final String label;
    /** "bedroom": the last word of a room's name. */
    public final String noun;
    /** Everything the room needs (all of it, in one closed room). */
    public final List<Furniture> furniture;
    /** What else it needs beyond furniture ("under a glass roof"), or null. */
    public final String condition;
    /** "a bed and a Meditation Mat". */
    public final String needs;
    /** Floor cells and inside height a room of this kind is comfortable at (the "room to live" reading). */
    public final int floorTarget, heightTarget;
    public final String gives;
    public final String does;
    public final String toast;

    RoomType(String label, String noun, List<Furniture> furniture, String condition, int floorTarget, int heightTarget, String gives,
             String does, String toast) {
        this.label = label;
        this.noun = noun;
        this.furniture = furniture;
        this.condition = condition;
        this.needs = needs(furniture, condition);
        this.floorTarget = floorTarget;
        this.heightTarget = heightTarget;
        this.gives = gives;
        this.does = does;
        this.toast = toast;
    }

    /** Whether a closed room with these counts and facts ({@link Tally#facts}) is this kind. */
    public boolean metBy(int[] counts, int[] facts) {
        for (Furniture f : furniture) if (counts[f.ordinal()] < f.needed()) return false;
        return conditionMet(facts);
    }

    /** The kind's condition beyond its furniture (true when it has none). */
    public boolean conditionMet(int[] facts) {
        return switch (this) {
            case GREENHOUSE -> facts[Tally.F_CEILING] > 0 && facts[Tally.F_GLASS_CEILING] * 2 >= facts[Tally.F_CEILING];
            case HALL -> facts[Tally.F_FLOOR] >= 48;
            case BELL_TOWER -> facts[Tally.F_ELEVATION] >= 8;
            case LOOKOUT -> facts[Tally.F_ELEVATION] >= 12 && Integer.bitCount(facts[Tally.F_WINDOW_DIRS]) >= 3;
            default -> true;
        };
    }

    public boolean uses(Furniture f) {
        return furniture.contains(f);
    }

    private static String needs(List<Furniture> list, String condition) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(i == list.size() - 1 && condition == null ? " and " : ", ");
            b.append(list.get(i).label);
        }
        if (condition != null) b.append(b.isEmpty() ? "" : ", ").append(condition);
        return b.isEmpty() ? "walls and a roof" : b.toString();
    }
}
