package dev.awaken.terrain.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Locale;

/** Client side of the living land (SPEC 15): the wind as this client sees it, waving leaves, falling leaves, rustle. */
public final class TerrainClient {
    private TerrainClient() {}

    public static void init(IEventBus modBus) {
        TerrainClientConfig.register();
        WavingLeaves.init(modBus);
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("lands_air"),
                (ResourceManagerReloadListener) manager -> {
                    AirTable.reload(manager);
                    FallingLeaves.reloaded();
                }));
        NeoForge.EVENT_BUS.addListener(TerrainClient::frame);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(TerrainClient::commands);
    }

    private static void frame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        long t0 = System.nanoTime();
        ClientWind.update(mc, e.getPartialTick().getGameTimeDeltaPartialTick(false));
        WavingLeaves.frame(mc);
        FallingLeaves.windNanos += System.nanoTime() - t0;
        FallingLeaves.frame();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        FallingLeaves.tick(mc);
        long t0 = System.nanoTime();
        LeafRustle.tick(mc);
        FallingLeaves.spawnNanos += System.nanoTime() - t0;
    }

    /**
     * {@code /awaken terrain leaves status|amount <off|low|normal|high>|biome <id|clear>} and
     * {@code /awaken terrain wind status|force <steady> <gust>|real} (client side; the biome and force lines are
     * measuring and showcase aids).
     */
    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("terrain")
                .then(Commands.literal("leaves")
                        .then(Commands.literal("status").executes(c -> say("Falling leaves: " + FallingLeaves.status() + "; " + LeafRustle.status() + "; " + LandsMotes.status())))
                        .then(Commands.literal("amount").then(Commands.argument("amount", StringArgumentType.word()).executes(c -> {
                            var a = TerrainClientConfig.LeafAmount.valueOf(StringArgumentType.getString(c, "amount").toUpperCase(Locale.ROOT));
                            TerrainClientConfig.setFallingLeaves(a);
                            return say("Falling leaves: " + a);
                        })))
                        .then(Commands.literal("biome").then(Commands.argument("biome", StringArgumentType.greedyString()).executes(c -> {
                            String arg = StringArgumentType.getString(c, "biome").trim();
                            if (arg.equals("clear")) {
                                FallingLeaves.forcedBiome = null;
                                FallingLeaves.forcedBiomeId = null;
                                return say("Falling leaves: biomes as they are");
                            }
                            Identifier id = Identifier.parse(arg);
                            AirTable.BiomeStyle style = AirTable.biomeById(id);
                            if (style == null) return say("Falling leaves: no rule names " + id);
                            FallingLeaves.forcedBiome = style;
                            FallingLeaves.forcedBiomeId = id;
                            return say("Falling leaves: every spot treated as " + id);
                        }))))
                .then(Commands.literal("wind")
                        .then(Commands.literal("status").executes(c -> say(windStatus())))
                        .then(Commands.literal("real").executes(c -> {
                            ClientWind.forceSteady = -1F;
                            ClientWind.forceGust = 0F;
                            return say("Wind: the real wind");
                        }))
                        .then(Commands.literal("force").then(Commands.argument("steady", FloatArgumentType.floatArg(0F, 1F))
                                .then(Commands.argument("gust", FloatArgumentType.floatArg(0F, 1F)).executes(c -> {
                                    ClientWind.forceSteady = FloatArgumentType.getFloat(c, "steady");
                                    ClientWind.forceGust = FloatArgumentType.getFloat(c, "gust");
                                    return say("Wind: forced " + windStatus());
                                })))))));
    }

    private static String windStatus() {
        Wind.Field w = ClientWind.field();
        Minecraft mc = Minecraft.getInstance();
        if (w == null || mc.player == null) return "Wind: no world";
        double x = mc.player.getX(), z = mc.player.getZ();
        return String.format(Locale.ROOT, "Wind: toward %.0f deg, steady %.2f, gust here %.2f, strength %.2f",
                Math.toDegrees(Math.atan2(w.dirZ, w.dirX)), w.steady, w.gust(x, z), w.strength(x, z));
    }

    private static int say(String line) {
        Awaken.LOG.info(line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
