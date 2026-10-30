package dev.awaken.kin.works;

import dev.awaken.core.api.Sources;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** The works (SPEC §43.12): their blocks, the Ledger's packets, and the plans standing in the world. Started by the kin. */
public final class WorksModule {
    private WorksModule() {}

    public static void init(IEventBus modBus) {
        WorksContent.register(modBus);
        modBus.addListener(WorksNet::register);
        WorksTests.register();
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> WorkPlans.serverStopped());
        Sources.declare("steaming at the edge of an Emberkit's hot spring, out in hot dry land (kin.KinSpawner)", "vent_stone");
        Sources.declare("the Deep's rot on a wild Pondling's pond bed: cleared to help it, it drops nothing (kin.KinHelp)", "pond_rot");
        Sources.declare("comes loose from every bell a gate's Lord kept for you (bells.Bells.lordFell)", "woken_clapper");
        Sources.declare("set against a wall in every town's granary (city.CityInteriors)", "granary_keystone");
    }
}
