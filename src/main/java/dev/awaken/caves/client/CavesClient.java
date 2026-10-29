package dev.awaken.caves.client;

import com.mojang.brigadier.Command;
import dev.awaken.Awaken;
import dev.awaken.caves.skin.CaveSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * The Living Deep on the client (SPEC §42): the puddle takes the cave's water colour; the drips' particles; the underlife
 * and {@code /awaken underlife [on|off|count|lineup|clear]} (client: what lives round you; a line-up for tours).
 */
public final class CavesClient {
    private CavesClient() {}

    public static void init(IEventBus modBus) {
        CavesClientConfig.register(modBus);
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class,
                e -> e.register(List.of(BlockTintSources.water()), CaveSkin.PUDDLE.get()));
        modBus.addListener(RegisterParticleProvidersEvent.class, CaveParticles::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, Underlife::tick);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, UnderlifeRender::submit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> Underlife.clear());
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal(Awaken.MODID).then(Commands.literal("underlife")
                        .executes(c -> say(status()))
                        .then(Commands.literal("count").executes(c -> say(status())))
                        .then(Commands.literal("off").executes(c -> {
                            Underlife.enabled = false;
                            return say("Underlife: off");
                        }))
                        .then(Commands.literal("on").executes(c -> {
                            Underlife.enabled = true;
                            return say("Underlife: on");
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            Underlife.clear();
                            return say("Underlife: cleared");
                        }))
                        .then(Commands.literal("lineup").executes(c -> {
                            var level = Minecraft.getInstance().level;
                            return say(level == null ? "Underlife: no world" : "Underlife line-up: " + Underlife.lineup(level) + " bodies");
                        })))));
    }

    private static String status() {
        return (Underlife.enabled && CavesClientConfig.underlife() ? "Underlife: " : "Underlife (off): ") + Underlife.counts();
    }

    private static int say(String line) {
        Awaken.LOG.info("[caves] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
