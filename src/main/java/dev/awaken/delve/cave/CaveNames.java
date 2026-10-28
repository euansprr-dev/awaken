package dev.awaken.delve.cave;

/**
 * Every place's name (PLAN 8.4: "THE DELVERS' DEEP · old mine"), fixed by its seed: the same for every player and on
 * every start. The first sinkhole, the one by the Commons, is always the Opening (the Living Wilds' own word for it).
 */
final class CaveNames {
    private CaveNames() {}

    private static final String[] SINKHOLES = {"The Long Throat", "Fern Drop", "The Swallow", "Rook's Well", "The Sky Pit", "Mossfall",
            "The Green Throat", "Hollow Well", "The Lantern Drop", "Kettle Sink"};
    private static final String[] MINES = {"The Delvers' Deep", "Copperdown", "The Bellmetal Drift", "Hessel's Workings", "The Old Tin Cut",
            "Ringer's Dig", "The Carillon Seam", "Bellfounders' Mine"};
    private static final String[] SEEPS = {"The Weeping Dark", "The Violet Hollow", "Where the Deep Breathes", "The Bruise", "Nightglass Hollow",
            "The Humming Wound"};
    private static final String[] CHUTES = {"The Ore Run", "The Delvers' Slide", "The Long Chute", "The Tumble", "Cartfall"};
    private static final String[] GROTTOS = {"The Hidden Pool", "Behind the Fall", "Lantern Grotto", "The Quiet Water", "Glasswater",
            "The Hush Pool"};

    private static final String[] MOUTHS = {"The Cold Mouth", "The Breathing Hill", "The Dark Arch", "The Root Door", "The Hollow Lip",
            "The Low Gate"};
    private static final String[] ADITS = {"The Old Adit", "The Delvers' Drift", "The Timbered Way", "The Ore Door", "The Seam Road",
            "The Iron Cut"};

    static String name(Spec s) {
        if (s.kind() == CaveKind.SINKHOLE && s.p(Sinkhole.FIRST) != 0) return "The Opening";
        String[] pool = switch (s.kind()) {
            case SINKHOLE -> SINKHOLES;
            case MINE -> MINES;
            case SEEP -> SEEPS;
            case CHUTE -> CHUTES;
            case GROTTO -> GROTTOS;
            case MOUTH -> s.p(Mouth.FORM) == Mouth.ADIT ? ADITS : MOUTHS;
        };
        return pool[(int) Math.floorMod(Carve.hash(s.seed(), 7, 7, 7), (long) pool.length)];
    }
}
