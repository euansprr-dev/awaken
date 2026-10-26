package dev.awaken.citizens;

import java.util.List;

/**
 * Where a settlement stands and what the people engine asks of its ground (SPEC §28.4): its origin (roster and plan
 * coordinates are offsets from it, y from its street level), its districts and their names, the ground's height, open
 * ground and water for a bucket line, and the places tagged for its events. Carillon's answers come from its plan
 * ({@link CarillonSite}); a settlement built in code answers from what its builder laid out ({@link BuiltSite}).
 *
 * <p>Events and work pick by tag, never by a district constant: {@link #FIRE} (a house with a door that can burn),
 * {@link #CART} (a lane a cart can spill across), {@link #STALL} (where a pickpocket works). Carillon maps its districts
 * to the tags (the Works burn, the Quay's lanes take the carts, the Market's stalls the pickpockets), so its picks are
 * the ones it always made. Coordinates are world blocks unless a method says plan.
 */
interface Site {
    String FIRE = "fire", CART = "cart", STALL = "stall";

    /** A building plot an event can use: plan box, roof top above the street, its door (plan; MIN_VALUE: none). */
    record Lot(int id, int x0, int z0, int x1, int z1, int top, int doorX, int doorZ) {}

    int cx();

    int cz();

    /** The street level that place and spot heights (ry) are measured from. */
    int baseY();

    /** Carillon itself: its client life (the Site packet), its opening, its soundscape, its gates and wall. */
    boolean carillon();

    /** Whether a world column belongs to the settlement (W3's predicate; Carillon's is the wall's inside). */
    boolean inside(int x, int z);

    /** The district a world column lies in (0 when none or outside). */
    int district(int x, int z);

    /** How many district ids there are (sizes the saved safety and gratitude). */
    int districts();

    /** What people call a district ({district} in speech, the crier's news). */
    String districtName(int id);

    /** The district an event of this tag reports itself in: Carillon's tag district, else where it happened. */
    int districtFor(String tag, int x, int z);

    /** The ground's top block at a world column (absolute y). */
    int ground(int x, int z);

    /** Open ground people can stand on at a world column. */
    boolean walkable(int x, int z);

    /** Water a bucket line can draw from at a world column. */
    boolean water(int x, int z);

    /** The lots carrying a tag (in a stable order). */
    List<Lot> lots(String tag);

    /** Whether a world point carries a tag (CART: a lane a cart can spill across). */
    boolean tagged(String tag, double x, double z);

    /** What the client draws for itself (a built settlement's chimneys and places that sound: x, ry, z offsets and kind). */
    default List<int[]> emitters() {
        return List.of();
    }
}
