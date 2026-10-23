package dev.awaken.signal;

import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The Signal: the voice of the system that chose you. See docs/SPEC.md section 5. */
public final class SignalModule {
    private SignalModule() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(SignalModule::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(SignalPayloads.Show.TYPE, SignalPayloads.Show.STREAM_CODEC);
        r.playToClient(SignalPayloads.Toast.TYPE, SignalPayloads.Toast.STREAM_CODEC);
        r.playToServer(SignalPayloads.Answer.TYPE, SignalPayloads.Answer.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Signal.dispatch(sp, msg.action(), msg.accepted());
        });
    }
}
