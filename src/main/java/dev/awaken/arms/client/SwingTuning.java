package dev.awaken.arms.client;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import dev.awaken.arms.FpSwings;
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
import java.util.concurrent.CompletableFuture;

/**
 * Swing Studio's live tuning (SPEC §25 "Swing Studio"): while it is on, the first-person swings come from a tuning
 * folder instead of the jar. The lab bakes a round there ({@code anim_lab.py bake --out <folder>/fp_swings.json}); the
 * folder is looked at once a second, a changed file is read on a worker and put in use between moves, never in the
 * middle of one. A newer schema than this build reads, or a round older than the jar's, is refused with a line in chat.
 * {@code prev.json} beside it is the round before, for an A/B; in blind mode the two are named X and Y, the names
 * dealt at random. {@code /awaken swing note} writes what the player felt to {@code notes.jsonl} with what was playing.
 * Off: the jar's swings, exactly as without the studio.
 */
final class SwingTuning {
    private SwingTuning() {}

    private static final String FILE = "fp_swings.json", PREV = "prev.json", NOTES = "notes.jsonl";

    private static @Nullable Path dir;
    private static long seen = Long.MIN_VALUE;
    private static int poll;
    private static boolean loading;
    // the folder's two sets once read (now, and the round before), waiting to go in; and those in use
    private static volatile FpSwings.@Nullable Data readyNow, readyPrev;
    private static FpSwings.@Nullable Data now, prev;
    private static boolean showingPrev, blind, blindFlip;
    private static String lastStyle = "-", lastMove = "-";

    static void init() {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal(Awaken.MODID).then(Commands.literal("swing")
                        .then(Commands.literal("tune")
                                .executes(c -> say(status()))
                                .then(Commands.literal("on")
                                        .executes(c -> on(ArmsComfort.swingTuningPath()))
                                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                                .executes(c -> on(StringArgumentType.getString(c, "path")))))
                                .then(Commands.literal("off").executes(c -> off())))
                        .then(Commands.literal("ab").executes(c -> ab()))
                        .then(Commands.literal("blind").executes(c -> blind()))
                        .then(Commands.literal("note").then(Commands.argument("words", StringArgumentType.greedyString())
                                .executes(c -> note(StringArgumentType.getString(c, "words")))))
                        .then(Commands.literal("last").executes(c -> say(last()))))));
    }

    /** The client tick: the last move remembered, the folder looked at once a second, a read set put in between moves. */
    static void tick() {
        if (ArmsPredict.move != null && ArmsPredict.style != null) {
            lastStyle = ArmsPredict.style.id();
            lastMove = ArmsPredict.move.key;
        }
        if (dir == null) {
            if (ArmsComfort.swingTuning() && poll++ == 0) start(ArmsComfort.swingTuningPath(), false); // as the config left it
            return;
        }
        if (++poll % 20 == 0 && !loading) look();
        FpSwings.Data n = readyNow;
        if (n != null && ArmsPredict.move == null && !ArmsPredict.charging) {
            readyNow = null;
            now = n;
            prev = readyPrev;
            showingPrev = false;
            FpSwings.swap(now);
            say("Swing Studio: round " + now.round() + " in" + (prev != null ? " (A/B ready)" : ""));
        }
    }

    private static int on(String path) {
        start(path, true);
        return Command.SINGLE_SUCCESS;
    }

    private static void start(String path, boolean save) {
        Path d = folder(path);
        dir = d;
        seen = Long.MIN_VALUE;
        poll = 0;
        if (save) ArmsComfort.swingTuning(true, path == null ? "" : path);
        say("Swing Studio: reading " + d.resolve(FILE) + (Files.exists(d.resolve(FILE)) ? "" : " (nothing there yet)"));
    }

    private static int off() {
        dir = null;
        now = prev = null;
        readyNow = readyPrev = null;
        FpSwings.swap(null);
        ArmsComfort.swingTuning(false, ArmsComfort.swingTuningPath());
        return say("Swing Studio: off, the jar's swings (round " + FpSwings.jarData().round() + ")");
    }

    /** The folder: the one given, else AWAKEN_TUNING, else the MEDIA drive's if it is there, else the game folder's. */
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
        CompletableFuture.supplyAsync(() -> {
            FpSwings.Data n = read(f), p = Files.exists(d.resolve(PREV)) ? read(d.resolve(PREV)) : null;
            int jar = FpSwings.jarData().round();
            if (n.round() < jar) throw new IllegalStateException("round " + n.round() + " is older than the game's round " + jar + ": bake it again");
            return new FpSwings.Data[] {n, p};
        }, net.minecraft.util.Util.backgroundExecutor()).whenComplete((r, err) -> Minecraft.getInstance().execute(() -> {
            loading = false;
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                say("Swing Studio: kept what's playing; " + FILE + " was refused (" + c.getMessage() + ")");
                return;
            }
            readyPrev = r[1];
            readyNow = r[0];
        }));
    }

    private static FpSwings.Data read(Path f) {
        try (Reader in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return FpSwings.parse(in);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static int ab() {
        if (now == null || prev == null) return say("Swing Studio: no A/B (tuning off, or no " + PREV + " in the folder)");
        if (ArmsPredict.move != null) return say("Swing Studio: finish the move first");
        showingPrev = !showingPrev;
        FpSwings.swap(showingPrev ? prev : now);
        return say("Swing Studio: playing " + label());
    }

    private static int blind() {
        blind = !blind;
        blindFlip = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
        if (blind && now != null && prev != null && ArmsPredict.move == null) {
            showingPrev = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
            FpSwings.swap(showingPrev ? prev : now);
        }
        return say("Swing Studio: blind " + (blind ? "on, playing " + label() : "off, playing " + label()));
    }

    /** What is playing as the player is told: round numbers, or X and Y in blind mode. */
    private static String label() {
        if (now == null) return "the jar's swings";
        if (blind) return (showingPrev ^ blindFlip) ? "Y" : "X";
        return showingPrev && prev != null ? "the round before (" + prev.round() + ")" : "round " + now.round();
    }

    private static int note(String words) {
        Path d = dir != null ? dir : folder(ArmsComfort.swingTuningPath());
        JsonObject o = new JsonObject();
        o.addProperty("time", java.time.Instant.now().toString());
        o.addProperty("style", lastStyle);
        o.addProperty("move", lastMove);
        FpSwings.Data playing = FpSwings.current();
        o.addProperty("round", playing.round());
        o.addProperty("version", now == null ? "jar" : showingPrev ? "prev" : "now");
        if (blind) o.addProperty("blind", label());
        o.addProperty("note", words);
        try {
            Files.createDirectories(d);
            Files.writeString(d.resolve(NOTES), o + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            return say("Swing Studio: the note could not be written (" + e.getMessage() + ")");
        }
        return say("Swing Studio: noted for " + lastStyle + " " + lastMove + " (" + label() + ")");
    }

    private static String last() {
        return "Swing Studio: last move " + lastStyle + " " + lastMove + ", playing " + label();
    }

    private static String status() {
        return dir == null ? "Swing Studio: off, the jar's swings (round " + FpSwings.jarData().round() + ")"
                : "Swing Studio: on, " + dir.resolve(FILE) + ", playing " + label();
    }

    private static int say(String line) {
        Awaken.LOG.info("[arms] {}", line);
        var mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
