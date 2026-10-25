package dev.awaken.living.region;

/**
 * Region names from the land (SPEC §21.2): a word from the land's group, joined to a place word either as one word
 * ("Rimewold", "Ashfen", "Willowmere") or two ("Briar Downs", "the Gloam Reach"). Dangerous bands lean on the group's
 * grimmer words. Deterministic from the cell's hash; duplicates are possible far apart and harmless.
 */
final class Names {
    private Names() {}

    private record Words(String[] fair, String[] grim, String[] joined, String[] apart) {}

    private static Words w(String fair, String grim, String joined, String apart) {
        return new Words(fair.split(" "), grim.split(" "), joined.split(" "), apart.split(" "));
    }

    private static final Words FOREST = w("Briar Elder Moss Fern Oak Hazel Bracken Holly Rowan Alder Linden Amber",
            "Thorn Ash Black Wither Grey Raven", "wood holt hurst glade shaw dell wold", "Downs Weald Hollow Wood Glades");
    private static final Words PLAINS = w("Willow Barley Clover Meadow Sorrel Hay Fair Gold Wind Lark Sweet Oat",
            "Grey Bleak Wither Scar Long", "mere field lea ford stead dale", "Downs Reach Vale Fields Lea");
    private static final Words SNOW = w("Rime Frost White Hoar Snow Winter Pale Silver Crystal",
            "Sleet Chill Bitter Hollow Grim Iron", "wold fell reach march mere holm", "Heights Wastes Fells Reach Wilds");
    private static final Words DESERT = w("Sun Dune Glass Ochre Amber Gold Salt Mirage",
            "Dust Ember Scorch Bone Ash Cinder", "waste reach sands flats", "Waste Expanse Sands Flats Reach");
    private static final Words SWAMP = w("Reed Sedge Willow Lily Peat Heron",
            "Fen Mire Bog Murk Black Rot", "mere fen moor sink marsh", "Mire Marsh Fens Moor Sink");
    private static final Words MOUNTAIN = w("High Cloud Eagle Stone Grey Kestrel Sky",
            "Crag Scree Iron Storm Wolf Broken", "fell crag tor spire scar", "Peaks Heights Teeth Fells Spires");
    private static final Words DARK = w("Dusk Shade Hollow Moth Night",
            "Gloam Bleak Grim Raven Murk Ash", "wood reach deep holt", "Reach Deeps Wood Hollows Dark");
    private static final Words JUNGLE = w("Vine Jade Emerald Verdant Orchid Fern",
            "Coil Fang Venom Tangle Choke", "wild tangle deep", "Canopy Tangle Wilds Deeps");
    private static final Words OCEAN = w("Tide Gull Pearl Wave Coral Kelp",
            "Brine Wreck Salt Drown Grey", "strand shore reach", "Coast Shallows Strand Reach");
    private static final Words MUSHROOM = w("Spore Cap Puff Velvet Moon",
            "Spore Mould Blight", "isle mead holm", "Isles Meads Holms");
    private static final Words BADLANDS = w("Rust Red Ochre Copper Sunset",
            "Scar Cinder Blood Bone", "mesa cut rock", "Badlands Breaks Mesas Cuts");

    private static Words of(RegionMap.Group g) {
        return switch (g) {
            case FOREST -> FOREST;
            case PLAINS -> PLAINS;
            case SNOW -> SNOW;
            case DESERT -> DESERT;
            case SWAMP -> SWAMP;
            case MOUNTAIN -> MOUNTAIN;
            case DARK -> DARK;
            case JUNGLE -> JUNGLE;
            case OCEAN -> OCEAN;
            case MUSHROOM -> MUSHROOM;
            case BADLANDS -> BADLANDS;
        };
    }

    static String name(RegionMap.Group group, int band, long hash) {
        Words w = of(group);
        long h = RegionMap.mix(hash);
        // the higher the band, the likelier a grim word
        boolean grim = RegionMap.unit(h) < 0.15 + 0.2 * band;
        String[] firsts = grim ? w.grim : w.fair;
        h = RegionMap.mix(h + 1);
        String first = firsts[(int) Math.floorMod(h, (long) firsts.length)];
        h = RegionMap.mix(h + 2);
        if (RegionMap.unit(h) < 0.55) {
            h = RegionMap.mix(h + 3);
            String end = w.joined[(int) Math.floorMod(h, (long) w.joined.length)];
            // no doubled letters across the join ("Fenn", "Ashholt" stays; "Mossshaw" doesn't)
            if (first.toLowerCase(java.util.Locale.ROOT).endsWith(end.substring(0, 1))) end = end.substring(1);
            return first + end;
        }
        h = RegionMap.mix(h + 4);
        String place = w.apart[(int) Math.floorMod(h, (long) w.apart.length)];
        h = RegionMap.mix(h + 5);
        return (RegionMap.unit(h) < 0.35 ? "the " : "") + first + " " + place;
    }
}
