package dev.awaken.village.plan;

import dev.awaken.city.VillageStyles;

import java.util.Random;

/**
 * Settlement and people's names (SPEC §28.6): seeded, never random, so a place keeps its name in every session.
 * Settlement names are built from a first part and an ending that suit the land (a frost hamlet is a "-holm", a marsh
 * one a "-mere"); people get a given name and their household's family name.
 */
final class Names {
    private Names() {}

    private static final String[] FIRST = {"Ash", "Bram", "Wren", "Hollow", "Fen", "Oak", "Thorn", "Mill", "Elder", "Brook", "Hazel",
            "Rook", "Stone", "Alder", "Bright", "Cold", "Fair", "Green", "Long", "Low", "Mere", "Kettle", "Lark", "Wick", "Bell", "Cross"};
    private static final String[][] END = {
            {"by", "ford", "ley", "wick", "stead", "field", "ton", "well", "dale", "cote"},           // temperate
            {"holm", "vik", "gard", "stad", "fell", "thorp", "by"},                                    // frost
            {"wadi", "rest", "spring", "well", "court", "gate"},                                       // desert
            {"kraal", "ridge", "acre", "well", "field"},                                               // savanna
            {"mere", "fen", "moss", "marsh", "ings", "carr"},                                          // mire
            {"crag", "fell", "cairn", "shaw", "scar", "tor"},                                          // highland
            {"wood", "holt", "hurst", "den", "grove", "shaw"},                                         // elder
            {"haven", "mouth", "strand", "wick", "port", "cove"},                                      // coast
            {"vale", "ford", "creek", "glade"},                                                        // jungle
            {"bloom", "dell", "garth", "ley", "glade"}};                                               // cherry
    private static final String[] DESERT_FIRST = {"Sar", "Qad", "Ash", "Tam", "Zeb", "Kir", "Amb", "Dun", "Hal"};

    static String settlement(int family, long seed) {
        Random r = new Random(seed ^ 0x5E771E3E47L);
        String[] ends = END[Math.clamp(family, 0, END.length - 1)];
        String first = family == VillageStyles.DESERT ? DESERT_FIRST[r.nextInt(DESERT_FIRST.length)] : FIRST[r.nextInt(FIRST.length)];
        String end = ends[r.nextInt(ends.length)];
        if (first.toLowerCase(java.util.Locale.ROOT).endsWith(end.substring(0, 1))) end = ends[(java.util.Arrays.asList(ends).indexOf(end) + 1) % ends.length];
        return first + end;
    }

    private static final String[] GIVEN_M = {"Bren", "Corin", "Tam", "Aldo", "Harl", "Oswin", "Piet", "Rowan", "Edric", "Fenn", "Gus",
            "Hob", "Ivo", "Jory", "Kel", "Lorn", "Mattis", "Ned", "Osric", "Perrin", "Quill", "Ruel", "Sefton", "Tobin", "Ulric", "Walt",
            "Yorick", "Bastian", "Dunstan", "Garrick"};
    private static final String[] GIVEN_F = {"Mara", "Elda", "Wyn", "Ada", "Brisa", "Cass", "Dena", "Ester", "Fay", "Greta", "Hesper",
            "Ines", "Jenna", "Kesh", "Liesl", "Maud", "Nell", "Orla", "Pia", "Rosa", "Sabine", "Tilda", "Una", "Vesna", "Wilma", "Yara",
            "Agnes", "Bryony", "Clem", "Dorrit"};
    private static final String[] FAMILY = {"Ashby", "Fenn", "Harrow", "Cotter", "Miller", "Thatcher", "Brook", "Hale", "Marlow", "Oakes",
            "Penhallow", "Quarry", "Redd", "Sawyer", "Tanner", "Underhill", "Vane", "Wainwright", "Yeo", "Barrow", "Carver", "Dell", "Ellery",
            "Fletcher", "Gale", "Holt", "Ingle", "Joss", "Kemp", "Lowe", "Moss", "Nettle", "Orme", "Pike", "Rook", "Stone", "Tull", "Webb"};

    static String given(Random r, boolean female) {
        String[] pool = female ? GIVEN_F : GIVEN_M;
        return pool[r.nextInt(pool.length)];
    }

    static String family(Random r) {
        return FAMILY[r.nextInt(FAMILY.length)];
    }
}
