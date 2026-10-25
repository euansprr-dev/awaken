package dev.awaken.core.audio.client.world;

import com.mojang.brigadier.Command;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry of the world sound Conductor: soundscapes on every resource reload, the tick, {@code /awaken audio world [off|on]}. */
public final class WorldSound {
    private WorldSound() {}

    public static void init(IEventBus modBus) {
        Conductor.init();
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("soundscapes"),
                (ResourceManagerReloadListener) Soundscapes::reload));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> Conductor.tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> Conductor.clear());
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal(Awaken.MODID).then(Commands.literal("audio").then(Commands.literal("world")
                        .executes(c -> say(Conductor.status()))
                        .then(Commands.literal("off").executes(c -> {
                            Conductor.enabled(false);
                            return say(Conductor.status());
                        }))
                        .then(Commands.literal("on").executes(c -> {
                            Conductor.enabled(true);
                            return say("World sound: on");
                        }))))));
    }

    private static int say(String line) {
        Awaken.LOG.info("[audio] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
