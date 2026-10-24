package dev.awaken.veil;

import dev.awaken.signal.Signal;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point for the Veil: meditation, spirit projection and the Body, the cord, Awareness, spirit sight, the dead,
 * possession, Cord-Eaters, Veil Gates and the Stillpoint. See docs/SPEC.md section 9 and docs/modules/veil.md.
 */
public final class VeilModule {
    private VeilModule() {}

    public static void init(IEventBus modBus) {
        dev.awaken.mobs.Mobs.installSpiritHooks(new dev.awaken.mobs.Mobs.SpiritHooks() {
            @Override public boolean isSpirit(net.minecraft.server.level.ServerPlayer p) { return Veil.isSpirit(p); }
            @Override public void onCordBitten(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.entity.LivingEntity eater) {
                if (Projection.live(p) != null) Projection.end(p, Projection.EndReason.CORD_CUT);
            }
        });
        VeilContent.register(modBus);
        modBus.addListener(VeilModule::registerPayloads);
        VeilEvents.init();
        VeilCommands.register();
        VeilLessons.register(); // meditation and the Veil taught in play (level road flags)
        VeilTests.register();
        TogetherVeilTests.register();
    }

    static final Map<UUID, Long> REFUSED = new HashMap<>();

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(VeilPayloads.State.TYPE, VeilPayloads.State.STREAM_CODEC);
        r.playToClient(VeilPayloads.Meditation.TYPE, VeilPayloads.Meditation.STREAM_CODEC);
        r.playToClient(VeilPayloads.Sight.TYPE, VeilPayloads.Sight.STREAM_CODEC);
        r.playToClient(VeilPayloads.Codex.TYPE, VeilPayloads.Codex.STREAM_CODEC);
        r.playToClient(VeilPayloads.Glimpse.TYPE, VeilPayloads.Glimpse.STREAM_CODEC);
        r.playToServer(VeilPayloads.MeditateInput.TYPE, VeilPayloads.MeditateInput.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Meditation.input(sp, msg.holding(), msg.justSit());
        });
        r.playToServer(VeilPayloads.PossessInput.TYPE, VeilPayloads.PossessInput.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Possession.input(sp, msg);
        });
        r.playToServer(VeilPayloads.Action.TYPE, VeilPayloads.Action.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) action(sp, msg);
        });
    }

    private static void action(ServerPlayer sp, VeilPayloads.Action msg) {
        Projection.Spirit s = Projection.live(sp);
        if (s == null) return;
        switch (msg.kind()) {
            case VeilPayloads.Action.TALK -> Spirits.talk(sp, msg.entityId());
            case VeilPayloads.Action.POSSESS -> Possession.request(sp, s, msg.entityId());
            case VeilPayloads.Action.RELEASE -> Possession.release(sp, s, null);
            case VeilPayloads.Action.ATTACK -> Possession.act(sp, s, msg.entityId());
            case VeilPayloads.Action.RETURN -> Projection.end(sp, Projection.EndReason.RETURNED);
            case VeilPayloads.Action.USE -> {
                VeilInteract.Result result = VeilInteract.use(sp, msg.pos());
                if (result == VeilInteract.Result.NOT_BLUE) {
                    long now = sp.level().getGameTime();
                    Long last = REFUSED.get(sp.getUUID());
                    if (last == null || now - last > 60) {
                        REFUSED.put(sp.getUUID(), now);
                        Signal.toast(sp, Component.literal("Your hand goes through it. Only what glows blue answers."));
                    }
                }
            }
            default -> {}
        }
    }
}
