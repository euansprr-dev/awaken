package dev.awaken.kin;

import java.util.List;
import java.util.Locale;

/**
 * The land's own beasts that can live with you (SPEC §43.5; JOURNEY part 2's table). Each kind's words are what the
 * System can say about it: what sort of animal, where it lives, its three watch lines (vague to plain; the third states
 * the want, never the help), its job at home, its nook, its name for you at Kin. {@link #built} kinds walk the world
 * now; the rest come in later stages and are never shown until they do.
 */
public enum KinKind {
    LAMPWING("Lampwing", true, "a moth the size of a cat with a glowing belly", "the edges of woods, after dark",
            List.of("It keeps circling the edge of the trees after dark.",
                    "It drifts toward any glow it can see, then back to the trees.",
                    "It wants a warm light near the trees, where it can see it."),
            "Lights your home at night. Nothing from the dark spawns where a Lampwing roosts.",
            "a warm light in your yard", "Warm One", 0xF2C46A),
    BURROWBUN("Burrowbun", true, "something small and round that digs", "soft meadow ground",
            List.of("It keeps scratching at the ground and giving up.",
                    "It only digs where the soil is soft.",
                    "It wants soft, broken ground to burrow in."),
            "Tends your fields: harvests what's ripe, plants it again, and keeps the soil soft.",
            "a tilled field at your home", "Soft Ground", 0xC9A27A),
    MOSSBACK("Mossback", true, "a slow thing with a little garden growing on its shell", "wet ground in the sun, by meres and rivers",
            List.of("It sits very still, and the garden on its back is drooping.",
                    "It keeps turning its shell toward the sun, and toward the water.",
                    "It wants a wet, sunny spot: water close by and open sky above it."),
            "Crops near it grow faster.",
            "water with open sky at your home", "Long Shadow", 0x7FA25A),
    PEBBLEPUP("Pebblepup", true, "a stone-skinned pup that hoards shiny stones", "rocky slopes and cave mouths",
            List.of("It keeps going back to a pile of stones.",
                    "It only adds the shiny ones.",
                    "It wants more shiny stones for its pile."),
            "Sorts and crushes the ore and rubble you bring to the Ore Shed. With you in caves, it hears lode hearts.",
            "an Ore Shed, or bare stone at your home", "Big Stone", 0xA7A9B2),
    TOTTER("Totter", false, "a round beetle that carries things in neat rows", "woods' floors and empty villages", List.of(), "", "", "", 0),
    THATCHBACK("Thatchback", false, "a beaver-badger that carries planks on its tail", "rivers", List.of(), "", "", "", 0),
    EMBERKIT("Emberkit", false, "a salamander kitten that glows from inside", "hot springs and warm caves", List.of(), "", "", "", 0),
    PUFFWOOL("Puffwool", false, "a cloud of a sheep on tiny legs", "high pasture", List.of(), "", "", "", 0),
    DUMPLIN("Dumplin", false, "a round bird that can't fly", "grassland, in flocks", List.of(), "", "", "", 0),
    BUMBLEPUFF("Bumblepuff", false, "a fuzzy bee as big as a loaf", "flower fields", List.of(), "", "", "", 0),
    PONDLING("Pondling", false, "a frog-axolotl that fishes", "still, clean water", List.of(), "", "", "", 0),
    SNUFFLET("Snufflet", false, "a piglet with a long snout", "old woods near ruins", List.of(), "", "", "", 0),
    CHIMEWREN("Chimewren", false, "a round songbird that copies sounds", "everywhere, in flocks", List.of(), "", "", "", 0),
    LOOMWORM("Loomworm", false, "a fat, fuzzy caterpillar that spins", "old woods' canopy", List.of(), "", "", "", 0),
    CLOUDCALF("Cloudcalf", false, "a sky-whale calf that drifts", "the sky islands", List.of(), "", "", "", 0),
    BOULDERBACK("Boulderback", false, "something big and tusked that glows in the dark", "deep caves", List.of(), "", "", "", 0);

    public final String title, sort, where, job, nook, callsYou;
    public final boolean built;
    public final List<String> watch;
    /** The colour its card and its tell's sparks take. */
    public final int colour;

    KinKind(String title, boolean built, String sort, String where, List<String> watch, String job, String nook, String callsYou, int colour) {
        this.title = title;
        this.built = built;
        this.sort = sort;
        this.where = where;
        this.watch = watch;
        this.job = job;
        this.nook = nook;
        this.callsYou = callsYou;
        this.colour = colour;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static KinKind of(String id) {
        for (KinKind k : values()) if (k.id().equals(id)) return k;
        return LAMPWING;
    }

    /** The kinds that live in the world now (stage 2: four). */
    public static List<KinKind> livingNow() {
        return java.util.Arrays.stream(values()).filter(k -> k.built).toList();
    }
}
