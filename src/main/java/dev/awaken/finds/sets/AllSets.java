package dev.awaken.finds.sets;

/** Runs every set file once, in Journal order, so the kinds register. */
public final class AllSets {
    private AllSets() {}

    public static void init() {
        Cairns.init();
        Stilled.init();
        Lamps.init();
        Ringing.init();
        Machines.init();
        Gods.init();
        Wisps.init();
        Buried.init();
        Hung.init();
        Dry.init();
        Marks.init();
        Runs.init();
        High.init();
        Weather.init();
        Mended.init();
        Survey.init();
        Sealed.init();
        Beasts.init();
        PlacesWood.init();
        PlacesPlain.init();
        PlacesWild.init();
        PlacesStone.init();
        PlacesFar.init();
        // the twelve families of monuments (SPEC §62), in Journal order, and the remains that point to them
        MDish.init();
        MRoad.init();
        MArchive.init();
        MStair.init();
        MMast.init();
        MOrrery.init();
        MHall.init();
        MGarden.init();
        MClock.init();
        MShaft.init();
        MChains.init();
        MDoor.init();
        Remains.init();
    }

    /** Scenes that are not kinds: a generator given other numbers, drawn for a plan's page by the picture maker. */
    public static java.util.Map<String, java.util.function.Consumer<dev.awaken.finds.Scene>> proofs() {
        return java.util.Map.of();
    }
}
