package dev.awaken.gates.realms;

import dev.awaken.gates.realms.land.AmberReach;

import java.util.Map;
import java.util.function.Function;

/** The lands that can be drawn: one program per landform, named from a blueprint's land.program. */
public final class LandPrograms {
    private LandPrograms() {}

    private static final Map<String, Function<Land.Ctx, Land>> PROGRAMS = Map.ofEntries(
            Map.entry("awaken:honey_highlands", AmberReach::new),
            Map.entry("awaken:hour_crypt", dev.awaken.gates.realms.land.HourCrypt::new),
            Map.entry("awaken:vine_seat", dev.awaken.gates.realms.land.VineSeat::new),
            Map.entry("awaken:saltcomb_flats", dev.awaken.gates.realms.land.SaltcombFlats::new),
            Map.entry("awaken:kingsroot_keep", dev.awaken.gates.realms.land.KingsrootKeep::new),
            Map.entry("awaken:blockout", dev.awaken.gates.realms.blockout.BlockoutLand::new),
            Map.entry("awaken:quiet_barrow", dev.awaken.gates.realms.land.QuietBarrow::new),
            Map.entry("awaken:wax_galleries", dev.awaken.gates.realms.land.WaxGalleries::new),
            Map.entry("awaken:broodwarren", dev.awaken.gates.realms.land.Broodwarren::new),
            Map.entry("awaken:brood_burrow", dev.awaken.gates.realms.land.BroodBurrow::new),
            Map.entry("awaken:frozen_cells", dev.awaken.gates.realms.land.FrozenCells::new),
            Map.entry("awaken:sunken_stacks", dev.awaken.gates.realms.land.SunkenStacks::new),
            Map.entry("awaken:hollow_hills", dev.awaken.gates.realms.land.HollowHill::new),
            Map.entry("awaken:catacombs", dev.awaken.gates.realms.land.Catacomb::new),
            Map.entry("awaken:root_cellars", dev.awaken.gates.realms.land.RootCellar::new),
            Map.entry("awaken:tallow_runs", dev.awaken.gates.realms.land.TallowRun::new),
            Map.entry("awaken:glass_warrens", dev.awaken.gates.realms.land.GlassWarren::new),
            Map.entry("awaken:sporewells", dev.awaken.gates.realms.land.Sporewell::new),
            Map.entry("awaken:cinder_workings", dev.awaken.gates.realms.land.CinderWorkings::new),
            Map.entry("awaken:silvered_halls", dev.awaken.gates.realms.land.SilveredHalls::new),
            Map.entry("awaken:sealed_wing", dev.awaken.gates.realms.land.SealedWing::new),
            Map.entry("awaken:sleepless_ward", dev.awaken.gates.realms.land.SleeplessWard::new),
            Map.entry("awaken:unlit_stair", dev.awaken.gates.realms.land.UnlitStair::new),
            Map.entry("awaken:rime_hollows", dev.awaken.gates.realms.land.RimeHollow::new),
            Map.entry("awaken:green_dark", dev.awaken.gates.realms.land.GreenDark::new),
            Map.entry("awaken:stopped_works", dev.awaken.gates.realms.land.StoppedWorks::new),
            Map.entry("awaken:leapers_gauntlet", dev.awaken.gates.realms.land.LeapersGauntlet::new),
            Map.entry("awaken:ember_country", dev.awaken.gates.realms.land.EmberCountry::new),
            Map.entry("awaken:temple_country", dev.awaken.gates.realms.land.TempleCountry::new),
            Map.entry("awaken:frost_country", dev.awaken.gates.realms.land.FrostCountry::new),
            Map.entry("awaken:glass_country", dev.awaken.gates.realms.land.GlassCountry::new),
            Map.entry("awaken:fungal_country", dev.awaken.gates.realms.land.FungalCountry::new));

    /**
     * The programs that draw a place under a roof: the crawls and the two hand-built halls that say {@code indoors}. Every
     * other program is a land under the open sky ({@link RealmCountry#has}); a new one counts as open until it is listed
     * here, so a switched-off open land can never slip through. {@code realm_open_lands_off} builds each and checks.
     */
    private static final java.util.Set<String> ROOFED = java.util.Set.of(
            "awaken:hour_crypt", "awaken:wax_galleries", "awaken:broodwarren", "awaken:brood_burrow", "awaken:frozen_cells",
            "awaken:sunken_stacks", "awaken:hollow_hills", "awaken:catacombs", "awaken:root_cellars", "awaken:tallow_runs",
            "awaken:glass_warrens", "awaken:sporewells", "awaken:cinder_workings", "awaken:silvered_halls", "awaken:sealed_wing",
            "awaken:sleepless_ward", "awaken:unlit_stair", "awaken:rime_hollows", "awaken:green_dark", "awaken:stopped_works",
            "awaken:leapers_gauntlet");

    public static boolean has(String id) {
        return PROGRAMS.containsKey(id);
    }

    /** Whether a program draws a land under the open sky, known without drawing it (Euan's switch of 12 Nov, SPEC §50.7). */
    public static boolean open(String id) {
        return !ROOFED.contains(id);
    }

    public static Land make(Land.Ctx ctx) {
        return PROGRAMS.get(ctx.bp().land().program()).apply(ctx);
    }
}
