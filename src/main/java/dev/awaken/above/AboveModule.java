package dev.awaken.above;

import dev.awaken.above.frame.FrameModules;
import dev.awaken.above.halo.Halo;
import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.machine.CloneVats;
import dev.awaken.above.net.AboveNet;
import dev.awaken.above.power.Power;
import dev.awaken.above.scan.Scanning;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The Above (SPEC section 10): Precursor ruins and machines, Essence power, exosuit Frames, the Clone Vat and the
 * Halo. See docs/modules/above.md.
 */
public final class AboveModule {
    private AboveModule() {}

    public static void init(IEventBus modBus) {
        Above.registerRelaySlot();
        AboveComponents.init(modBus);
        AboveBlocks.init(modBus);
        AboveItems.init(modBus);
        AboveSounds.init();
        Above.initAttachments(modBus);
        AboveWorldgen.init(modBus);
        modBus.addListener(AboveNet::register);
        PrecursorLogs.init();
        CloneVats.init();
        FrameModules.init();
        Halo.init();
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> Power.clear());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> Scanning.forget(e.getEntity().getUUID()));
        AboveCommands.register();
        AboveTests.register();
    }
}
