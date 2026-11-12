package dev.awaken.shaping;

import dev.awaken.core.api.Sources;
import dev.awaken.core.api.Tabs;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Made by Hand (SPEC §52). Stage 0: the Mallet's Knock and Looks. */
public final class ShapingModule {
    private ShapingModule() {}

    public static void init(IEventBus modBus) {
        ShapingContent.register(modBus);
        ShapingConfig.register(modBus);
        modBus.addListener(ShapingModule::registerPayloads);
        Tabs.fill(Tabs.Shelf.GEAR, ShapingContent.MALLET);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> Shaping.tick(e.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) LookBook.send(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Shaping.forget(e.getEntity().getUUID()));
        Sources.neverHeld("a block the Mallet has cut: it drops what it really is (shaping.ShapedBlock)", "shaped");
        ShapingCommands.register();
        ShapingTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(ShapingPayloads.SetLook.TYPE, ShapingPayloads.SetLook.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Shaping.onSetLook(sp, msg);
        });
        r.playToServer(ShapingPayloads.TurnMode.TYPE, ShapingPayloads.TurnMode.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Shaping.onTurnMode(sp, msg.by());
        });
        r.playToServer(ShapingPayloads.Undo.TYPE, ShapingPayloads.Undo.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Shaping.undo(sp);
        });
        r.playToServer(ShapingPayloads.Pick.TYPE, ShapingPayloads.Pick.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Shaping.onPick(sp, msg.pos());
        });
        r.playToClient(ShapingPayloads.Known.TYPE, ShapingPayloads.Known.STREAM_CODEC);
        r.playToClient(ShapingPayloads.Fx.TYPE, ShapingPayloads.Fx.STREAM_CODEC);
    }
}
