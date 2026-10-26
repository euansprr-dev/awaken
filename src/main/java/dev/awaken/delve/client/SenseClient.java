package dev.awaken.delve.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.awaken.Awaken;
import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.core.audio.client.world.Conductor;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Locale;

/**
 * The senses underground on the client (SPEC §28.5): where you are under the rock ({@link SenseState}), the Hollows'
 * sound ({@link CaveSound}), the deep's hum and a seep's voice and muffle ({@link DeepHum}), the Lens looks below
 * ({@link CaveLook}). Counted in the world sound Conductor's voice budget. Dev: {@code /awaken delve senses [status]}
 * (what plays and why) and {@code /awaken delve senses seep <0..1>|off} (hold the seep's weight, to hear and see one
 * anywhere); the server's {@code /awaken delve senses goto <cave>} takes you to a cave biome.
 */
public final class SenseClient {
    private SenseClient() {}

    public static void init(IEventBus modBus) {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(RegisterClientCommandsEvent.class, SenseClient::commands);
        Soundscape.register(new Conductor.Source() {
            @Override
            public int voices() {
                return CaveSound.voices() + DeepHum.voices();
            }
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        SenseState.update(mc);
        CaveSound.tick(mc);
        DeepHum.tick(mc);
        CaveLook.tick();
    }

    private static void reset() {
        SenseState.reset();
        CaveSound.reset();
        DeepHum.reset();
        CaveLook.tick();
    }

    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("delve").then(Commands.literal("senses")
                .executes(c -> say(status()))
                .then(Commands.literal("status").executes(c -> say(status())))
                .then(Commands.literal("seep")
                        .then(Commands.literal("off").executes(c -> {
                            CaveState.force(-1F);
                            return say("Seep: the server's again (" + String.format(Locale.ROOT, "%.2f", CaveState.seep()) + ")");
                        }))
                        .then(Commands.argument("weight", FloatArgumentType.floatArg(0F, 1F)).executes(c -> {
                            CaveState.force(FloatArgumentType.getFloat(c, "weight"));
                            return say(String.format(Locale.ROOT, "Seep held at %.2f (/awaken delve senses seep off to let go)", CaveState.seep()));
                        }))))));
    }

    static String status() {
        if (!SenseState.active) return "Senses: not in the overworld";
        String place = CaveState.place() == null ? "none" : CaveState.place() + (CaveState.name().isEmpty() ? "" : " \"" + CaveState.name() + "\"");
        return String.format(Locale.ROOT, "Senses: %s, y %.0f, under %.2f, depth %.2f, sky light %d%s; place %s, seep %.2f%s; %s; %s; nature %.2f",
                SenseState.BIOME_NAMES[SenseState.biome], SenseState.y, SenseState.under, SenseState.depth, SenseState.sky,
                SenseState.underwater ? ", underwater" : "", place, CaveState.seep(), CaveState.seepQuiet() ? " (quiet)" : "",
                CaveSound.status(SenseState.tick), DeepHum.status(), Soundscape.natureLevel())
                + String.format(Locale.ROOT, "; Lens: underground %.2f, seep %.2f%s",
                SenseState.biome == SenseState.PLAIN ? SenseState.under : 0F, CaveState.seep(),
                dev.awaken.lens.client.LensClient.active() ? "" : " (Lens off: neither plays)");
    }

    private static int say(String line) {
        Awaken.LOG.info("[delve] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
