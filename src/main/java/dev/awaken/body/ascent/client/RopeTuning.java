package dev.awaken.body.ascent.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentSwing;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The assisted swing's live tuning (SPEC §17.31, the Swing Studio's way): {@code /awaken rope tune on} reads
 * {@code rope_swing.json} from the tuning folder (the path given, else AWAKEN_TUNING, else /Volumes/MEDIA/Awaken-tuning,
 * else the game folder's awaken-tuning), looked at once a second; a changed file is read on a worker and put in use
 * between swings. Any of the tune's numbers by name ({@code {"schemaVersion": 1, "drive": 0.024, "boost": 1.18}}); the
 * rest keep the jar's. {@code /awaken rope note <words>} writes what the swing felt like, with the numbers in use, to
 * {@code rope_notes.jsonl}. Off (the default, and every new session): the jar's numbers.
 */
final class RopeTuning {
    private RopeTuning() {}

    static final int SCHEMA = 1;
    private static final String FILE = "rope_swing.json", NOTES = "rope_notes.jsonl";

    private static @Nullable Path dir;
    private static long seen = Long.MIN_VALUE;
    private static int poll;
    private static boolean loading;
    private static volatile AscentSwing.@Nullable Tune ready;
    private static Map<String, Double> inUse = Map.of();
    private static volatile Map<String, Double> readNums = Map.of();

    static void init() {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal(Awaken.MODID).then(Commands.literal("rope")
                        .then(Commands.literal("tune")
                                .executes(c -> say(status()))
                                .then(Commands.literal("on")
                                        .executes(c -> on(null))
                                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                                .executes(c -> on(StringArgumentType.getString(c, "path")))))
                                .then(Commands.literal("off").executes(c -> off())))
                        .then(Commands.literal("note").then(Commands.argument("words", StringArgumentType.greedyString())
                                .executes(c -> note(StringArgumentType.getString(c, "words"))))))));
    }

    /** The client tick: the folder looked at once a second, a read tune put in while no swing holds the body. */
    static void tick() {
        if (dir == null) return;
        if (++poll % 20 == 0 && !loading) look();
        AscentSwing.Tune t = ready;
        if (t != null && !AscentMoves.swinging()) {
            ready = null;
            AscentSwing.T = t;
            inUse = readNums;
            say("Rope tuning: " + (inUse.isEmpty() ? "the jar's numbers" : inUse.size() + " numbers from " + FILE) + " in use");
        }
    }

    private static int on(@Nullable String path) {
        Path d = folder(path);
        dir = d;
        seen = Long.MIN_VALUE;
        poll = 0;
        return say("Rope tuning: reading " + d.resolve(FILE) + (Files.exists(d.resolve(FILE)) ? "" : " (nothing there yet)"));
    }

    private static int off() {
        dir = null;
        ready = null;
        inUse = Map.of();
        AscentSwing.T = AscentSwing.Tune.JAR;
        return say("Rope tuning: off, the jar's numbers");
    }

    private static String status() {
        return dir == null ? "Rope tuning: off (/awaken rope tune on)" : "Rope tuning: on, " + dir.resolve(FILE) + ", " + inUse;
    }

    private static Path folder(@Nullable String path) {
        if (path != null && !path.isBlank()) return Path.of(path.trim());
        String env = System.getenv("AWAKEN_TUNING");
        if (env != null && !env.isBlank()) return Path.of(env);
        Path media = Path.of("/Volumes/MEDIA/Awaken-tuning");
        if (Files.isDirectory(media)) return media;
        return Minecraft.getInstance().gameDirectory.toPath().resolve("awaken-tuning");
    }

    private static void look() {
        Path d = dir;
        if (d == null) return;
        Path f = d.resolve(FILE);
        long m;
        try {
            m = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : Long.MIN_VALUE;
        } catch (IOException e) {
            return;
        }
        if (m == seen || m == Long.MIN_VALUE) return;
        seen = m;
        loading = true;
        CompletableFuture.supplyAsync(() -> read(f), net.minecraft.util.Util.backgroundExecutor())
                .whenComplete((nums, err) -> Minecraft.getInstance().execute(() -> {
                    loading = false;
                    if (err != null) {
                        Throwable c = err.getCause() != null ? err.getCause() : err;
                        say("Rope tuning: kept what's in use; " + FILE + " was refused (" + c.getMessage() + ")");
                        return;
                    }
                    readNums = nums;
                    ready = new AscentSwing.Tune(nums);
                }));
    }

    /** The file's numbers by name; a newer schema than this build reads is refused. */
    static Map<String, Double> read(Path f) {
        try (Reader in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(in).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    static Map<String, Double> parse(JsonObject o) {
        int schema = o.has("schemaVersion") ? o.get("schemaVersion").getAsInt() : SCHEMA;
        if (schema > SCHEMA) throw new IllegalStateException("schema " + schema + " is newer than this game reads (" + SCHEMA + ")");
        Map<String, Double> nums = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().equals("schemaVersion")) continue;
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) nums.put(e.getKey(), e.getValue().getAsDouble());
        }
        return nums;
    }

    private static int note(String words) {
        Path d = dir != null ? dir : folder(null);
        JsonObject o = new JsonObject();
        o.addProperty("time", java.time.Instant.now().toString());
        JsonObject nums = new JsonObject();
        inUse.forEach(nums::addProperty);
        o.add("numbers", nums);
        o.addProperty("note", words);
        try {
            Files.createDirectories(d);
            Files.writeString(d.resolve(NOTES), o + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            return say("Rope tuning: the note could not be written (" + e.getMessage() + ")");
        }
        return say("Rope tuning: noted");
    }

    private static int say(String line) {
        Awaken.LOG.info("[ascent] {}", line);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
