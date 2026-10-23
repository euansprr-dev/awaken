package dev.awaken.core;

import com.google.gson.JsonObject;
import dev.awaken.Awaken;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Playtest mode (opt-in, config {@code playtestMode}). Every 15 minutes the Signal asks what you'll do next and how
 * fun it is right now; answers and key events go to {@code <world>/awaken/playtest.jsonl} on this computer only.
 * {@code tools/playtest/report.py} turns that file into a session report.
 */
public final class Playtest {
    private Playtest() {}

    private static final int PROMPT_EVERY = 20 * 60 * 15;
    private static final Map<UUID, Long> SESSION_START = new HashMap<>();
    private static final Map<UUID, Long> LAST_DEATH = new HashMap<>();

    static void clear() {
        SESSION_START.clear();
        LAST_DEATH.clear();
    }

    public static boolean enabled() {
        try {
            return AwakenConfig.PLAYTEST_MODE.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(Playtest::onTick);
        NeoForge.EVENT_BUS.addListener(Playtest::onLogin);
        NeoForge.EVENT_BUS.addListener(Playtest::onLogout);
        NeoForge.EVENT_BUS.addListener(Playtest::onDeath);
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("next").then(Commands.argument("what", StringArgumentType.greedyString()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                JsonObject o = new JsonObject();
                o.addProperty("answer", StringArgumentType.getString(c, "what"));
                log(p, "next", o);
                Signal.toast(p, Component.literal("Noted"));
                return 1;
            })));
            root.then(Commands.literal("fun").then(Commands.argument("score", IntegerArgumentType.integer(1, 5)).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                JsonObject o = new JsonObject();
                o.addProperty("score", IntegerArgumentType.getInteger(c, "score"));
                log(p, "fun", o);
                Signal.toast(p, Component.literal("Noted"));
                return 1;
            })));
            root.then(Commands.literal("unfair").executes(c -> {
                flagUnfair(c.getSource().getPlayerOrException());
                return 1;
            }));
            root.then(Commands.literal("surprise").then(Commands.argument("what", StringArgumentType.greedyString()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                JsonObject o = new JsonObject();
                o.addProperty("what", StringArgumentType.getString(c, "what"));
                log(p, "surprise", o);
                Signal.toast(p, Component.literal("Noted"));
                return 1;
            })));
        });
    }

    /** Flags the player's most recent death as unfair (key J or /awaken unfair). */
    public static void flagUnfair(ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("since_death_s", (p.level().getGameTime() - LAST_DEATH.getOrDefault(p.getUUID(), 0L)) / 20);
        log(p, "unfair_death", o);
        Signal.toast(p, Component.literal("Death flagged as unfair"));
    }

    /** Appends one event. Cheap no-op when playtest mode is off. */
    public static void log(ServerPlayer player, String type, JsonObject data) {
        if (!enabled()) return;
        MinecraftServer server = player.level().getServer();
        JsonObject o = data == null ? new JsonObject() : data.deepCopy();
        o.addProperty("t", Instant.now().toString());
        o.addProperty("tick", server.getTickCount());
        o.addProperty("player", player.getName().getString());
        o.addProperty("type", type);
        o.addProperty("dim", player.level().dimension().identifier().toString());
        write(server, o.toString());
    }

    public static void log(ServerPlayer player, String type, String key, Object value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, String.valueOf(value));
        log(player, type, o);
    }

    private static synchronized void write(MinecraftServer server, String line) {
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("awaken");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("playtest.jsonl"), line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Awaken.LOG.warn("Playtest log write failed: {}", e.toString());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && enabled()) {
            SESSION_START.put(p.getUUID(), p.level().getGameTime());
            log(p, "session_start", null);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && enabled()) {
            log(p, "session_end", null);
            SESSION_START.remove(p.getUUID());
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            LAST_DEATH.put(p.getUUID(), p.level().getGameTime());
            if (!enabled()) return;
            JsonObject o = new JsonObject();
            o.addProperty("cause", e.getSource().getMsgId());
            var killer = e.getSource().getEntity();
            if (killer != null) o.addProperty("killer", killer.getType().toShortString());
            log(p, "death", o);
        }
    }

    private static void onTick(ServerTickEvent.Post e) {
        if (!enabled() || e.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            Long start = SESSION_START.get(p.getUUID());
            if (start == null) {
                SESSION_START.put(p.getUUID(), p.level().getGameTime());
                continue;
            }
            long played = p.level().getGameTime() - start;
            if (played > 0 && played % PROMPT_EVERY < 20) {
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Playtest check-in"))
                        .body(Component.literal("What are you doing next, and why? Press T and type /awaken next <answer>. "
                                + "How fun is it right now? /awaken fun 1-5"))
                        .ttl(20 * 15).build());
                log(p, "prompt", null);
            }
        }
    }
}
