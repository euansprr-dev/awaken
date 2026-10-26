package dev.awaken.holo;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Holo UI, common half (docs/modules/holo.md): the {@code awaken:holo_state} relay that lets other players see your
 * wrist come up, the panels' sound events, and {@code /awaken holo demo}. Everything you see on a panel is client side
 * ({@code holo.client}); the side-agnostic rules it follows (timelines, wells, hover ticks, the HUD fade) live in
 * {@code holo.ui}, where GameTests check them.
 */
public final class HoloModule {
    private HoloModule() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(HoloNet::register);
        HoloSounds.register(modBus);
        dev.awaken.holo.band.BandModule.init(modBus);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> HoloRelay.flush(e.getServer(), HoloRelay.SEND));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, e -> {
            if (e.getEntity() instanceof ServerPlayer tracker) HoloRelay.onStartTracking(tracker, e.getTarget());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) HoloRelay.forget(p);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) HoloRelay.reset(p);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) HoloRelay.reset(p);
        });
        AwakenCommands.add((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> demo = Commands.literal("demo").requires(AwakenCommands.admin());
            demo.executes(c -> demo(c.getSource().getPlayerOrException(), 1));
            demo.then(Commands.literal("panel").executes(c -> demo(c.getSource().getPlayerOrException(), 1)));
            demo.then(Commands.literal("wrist").executes(c -> demo(c.getSource().getPlayerOrException(), 2)));
            demo.then(Commands.literal("off").executes(c -> demo(c.getSource().getPlayerOrException(), 0)));
            root.then(Commands.literal("holo").then(demo));
        });
        HoloTests.register();
    }

    private static int demo(ServerPlayer p, int mode) {
        if (p.connection.hasChannel(HoloNet.Demo.TYPE)) PacketDistributor.sendToPlayer(p, new HoloNet.Demo(mode));
        return 1;
    }
}
