package dev.awaken.kin;

import java.util.List;
import java.util.Locale;

/**
 * The land's own beasts that can live with you (SPEC §43.5; JOURNEY part 2's table). Each kind's words are what the
 * System can say about it: what sort of animal, where it lives, its three watch lines (vague to plain; the third states
 * the want, never the help), its job at home, its nook, its name for you at Kin. {@link #built} kinds walk the world
 * now; the rest come in later stages and are never shown until they do. Stage 5 brings the second eight, stage 6 the
 * last four (the Bumblepuff, the Loomworm, the Cloudcalf and the Boulderback).
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
    TOTTER("Totter", true, "a round beetle that carries things in neat rows", "woods' floors",
            List.of("It keeps carrying things from one heap to another.",
                    "It lines everything up in rows, and frets at the one that's out of place.",
                    "It wants what's scattered put back in its row."),
            "Carries between your works: the Smeltery's ingots to the Forge's chest, the fields' harvest to the larder. With a Storehouse, it shelves everything you drop.",
            "a chest or barrel at your home to look after", "Tidy One", 0x5E86B4),
    THATCHBACK("Thatchback", true, "a beaver-badger that carries planks on its tail", "riverbanks",
            List.of("It keeps dragging sticks to the water and looking at what it's built.",
                    "It's measuring its dam against the river. It isn't big enough yet.",
                    "It wants whole logs for its dam, more than it can fell by itself."),
            "Builds your works from their plans, with the timber and stone you leave in the plan's chest.",
            "a Work Plan or a stack of logs at your home", "Builder", 0x8A5A3A),
    EMBERKIT("Emberkit", true, "a salamander kitten that glows from inside", "hot springs",
            List.of("It's curled up tight and shivering, even here.",
                    "It keeps pressing itself against the warm stones by the water.",
                    "It wants something warm to lie by: a fire, or a hot stone."),
            "Smelts the ore you drop in the Smeltery's chest, with no fuel. The Forge is still yours.",
            "a Smeltery (a vent stone and a furnace), or a lit campfire at your home", "Warm Hands", 0xF07A3A),
    PUFFWOOL("Puffwool", true, "a cloud of a sheep on tiny legs", "high pasture",
            List.of("It keeps rubbing itself against the rocks.",
                    "Its wool is full of burrs from the bushes, and it can't reach them.",
                    "It wants the burrs brushed out of its wool."),
            "Gives you its wool once a day, when you brush it.",
            "a pen at your home: a hay bale to lie by", "Gentle Hands", 0xE6E2EE),
    DUMPLIN("Dumplin", true, "a round bird that can't fly", "grassland, in flocks",
            List.of("The flock keeps wandering in circles.",
                    "They follow whatever's tallest, and nothing here stands tall.",
                    "They want someone up high to follow home to their nest."),
            "Leaves eggs in the nearest chest every day.",
            "a pen at your home: a hay bale to nest in", "Tall One", 0xE8C27A),
    BUMBLEPUFF("Bumblepuff", true, "a fuzzy bee as big as a loaf that floats more than it flies", "flower fields",
            List.of("It drifts from one bare patch of ground to the next and back to its old nest.",
                    "Somebody picked the meadow round its nest bare. It hasn't anywhere to go.",
                    "It wants flowers round its nest. Lots of them."),
            "Leaves honeycomb and honey in the nearest chest every day: wax for your candles.",
            "a bee nest or a beehive at your home", "Flower-Bringer", 0xF0C040),
    PONDLING("Pondling", true, "a frog-axolotl that fishes", "still water",
            List.of("It sits at the water's edge and won't go in.",
                    "Something at the bottom of the pond is souring the water.",
                    "It wants the rot cleared out of its pond."),
            "Fishes your water: a few fish in the nearest chest every day.",
            "a pond at your home", "Clear Water", 0x6CC0B6),
    SNUFFLET("Snufflet", true, "a piglet with a long snout", "old woods",
            List.of("It keeps snuffling at one spot and squealing.",
                    "Whatever it smells is deeper than it can dig.",
                    "It wants you to dig where it's snuffling."),
            "Forages round your home for mushrooms, berries and herbs, and leaves them in the nearest chest.",
            "grass and open ground at your home", "Digger", 0xD89A9A),
    CHIMEWREN("Chimewren", true, "a round songbird that copies sounds", "the open country, in flocks",
            List.of("The flock keeps singing the same few notes over and over.",
                    "They copy every sound they hear, and they're tired of their own songs.",
                    "They want to hear a new song. A bell, maybe: they're the one kind that loves them."),
            "In a Rookery, sings you a note your song is missing, and which way it came from.",
            "a Rookery (a Chime Perch in a room)", "Bell-Ringer", 0x8FB0DC),
    LOOMWORM("Loomworm", true, "a fat, fuzzy caterpillar that spins", "old woods",
            List.of("It's lying on a fallen log, looking up at the branches.",
                    "It's eaten every leaf it can reach, and it won't touch the wrong tree's.",
                    "It wants a branch of the tree it lives on, brought down to it."),
            "Spins thread and cloth: string and white wool in the nearest chest every day.",
            "leaves at your home it can reach: a tree, or a hedge", "Leaf-Bringer", 0xB8D88A),
    CLOUDCALF("Cloudcalf", true, "a sky-whale calf as big as a cow that drifts", "the sky over the high peaks",
            List.of("It drifts in slow circles, calling out over the peaks.",
                    "It's alone. Something it belongs with is calling back, far off, and it can't tell where.",
                    "It wants its pod. It would follow someone up at its own height who knew the way."),
            "Rains on your fields: crops under it grow faster and the soil stays wet.",
            "open sky over a field at your home", "Pod-Finder", 0xA8C8F0),
    BOULDERBACK("Boulderback", true, "something big, tusked and mossy, with eyes that glow in the dark", "deep caves",
            List.of("It's standing very still in the dark, watching your light.",
                    "Every time a light comes near, it backs away into the dark.",
                    "It wants to be left alone in the dark. If you put your light away and wait, it might come to you."),
            "Carries stone: your works go up more than twice as fast while it's home, and it brings up stone from below every day.",
            "somewhere dark at your home: no light and no sky", "Quiet One", 0x6A8A5A);

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

    /** The kinds that fly (a moth, a bee, a sky-whale calf): flying moves, no fall damage, down to the ground to sleep. */
    public boolean flies() {
        return this == LAMPWING || this == BUMBLEPUFF || this == CLOUDCALF;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static KinKind of(String id) {
        for (KinKind k : values()) if (k.id().equals(id)) return k;
        return LAMPWING;
    }

    /** The kinds that live in the world now (stage 2: four; stage 5: twelve; stage 6: all sixteen). */
    public static List<KinKind> livingNow() {
        return java.util.Arrays.stream(values()).filter(k -> k.built).toList();
    }
}
