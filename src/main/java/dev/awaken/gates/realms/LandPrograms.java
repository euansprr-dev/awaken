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
            Map.entry("awaken:catacombs", dev.awaken.gates.realms.land.Catacomb::new),
            Map.entry("awaken:tallow_runs", dev.awaken.gates.realms.land.TallowRun::new));

    public static boolean has(String id) {
        return PROGRAMS.containsKey(id);
    }

    public static Land make(Land.Ctx ctx) {
        return PROGRAMS.get(ctx.bp().land().program()).apply(ctx);
    }
}
