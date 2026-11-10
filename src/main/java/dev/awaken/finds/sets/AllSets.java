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
    }
}
