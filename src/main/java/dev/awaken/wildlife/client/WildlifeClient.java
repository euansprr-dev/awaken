package dev.awaken.wildlife.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.awaken.Awaken;
import dev.awaken.wildlife.WildlifeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Wild life on the client (SPEC "Wild life"): the animals' renderer, the director's tick, the insects' pass, and
 * {@code /awaken wildlife [on|off|count|dial <x>|lineup [clip|off] [variant]]} (client: what's about and what you can see; a density dial for tours).
 */
public final class WildlifeClient {
    private WildlifeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(WildlifeModule.CRITTER.get(), WildRenderer::new));
        modBus.addListener(WildRenderer::layers);
        modBus.addListener(RegisterRenderPipelinesEvent.class, InsectRender::onRegister);
        Wildlife.init();
        WildSound.init();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, Wildlife::tick);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, InsectRender::submit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            Wildlife.clear();
            WildSound.clear();
        });
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal(Awaken.MODID).then(Commands.literal("wildlife")
                        .executes(c -> say(status()))
                        .then(Commands.literal("count").executes(c -> say(status())))
                        .then(Commands.literal("off").executes(c -> {
                            Wildlife.enabled = false;
                            return say("Wild life: off");
                        }))
                        .then(Commands.literal("on").executes(c -> {
                            Wildlife.enabled = true;
                            return say("Wild life: on");
                        }))
                        .then(Commands.literal("lineup")
                                .executes(c -> say(Wildlife.lineup(Minecraft.getInstance().level, "idle", 0)))
                                .then(Commands.argument("clip", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(c -> say(Wildlife.lineup(Minecraft.getInstance().level,
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "clip"), 0)))
                                        .then(Commands.argument("variant", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 63))
                                                .executes(c -> say(Wildlife.lineup(Minecraft.getInstance().level,
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(c, "clip"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "variant")))))))
                        .then(Commands.literal("dial").then(Commands.argument("x", FloatArgumentType.floatArg(0F, 4F)).executes(c -> {
                            Wildlife.dial = FloatArgumentType.getFloat(c, "x");
                            return say("Wild life density x" + Wildlife.dial);
                        }))))));
    }

    private static String status() {
        var level = Minecraft.getInstance().level;
        if (level == null) return "Wild life: no world";
        return (Wildlife.enabled ? "Wild life: " : "Wild life (off): ") + Wildlife.counts(level);
    }

    private static int say(String line) {
        Awaken.LOG.info("[wildlife] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
