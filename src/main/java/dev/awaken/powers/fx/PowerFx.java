package dev.awaken.powers.fx;

import dev.awaken.powers.net.FxPayloads;
import net.neoforged.bus.api.IEventBus;

/** Common entry of the power FX system: particle types and the FX payload. The client half is {@code FxClient}. */
public final class PowerFx {
    private PowerFx() {}

    public static void init(IEventBus modBus) {
        PowerFxParticles.init(modBus);
        FxPayloads.init(modBus);
        PowerFxTests.register();
    }
}
