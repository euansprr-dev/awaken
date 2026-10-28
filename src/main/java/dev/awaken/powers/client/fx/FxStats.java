package dev.awaken.powers.client.fx;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * What power FX cost to draw (SPEC 34.9): the time FxRender spends building geometry and extracting the world each frame.
 * {@code /awaken fx stats} prints p50 / p99 over the last frames; {@code /awaken fx bench <power> <level> <count>} puts the
 * power at that level on its key (the admin showcase), casts it {@code count} times a second and a half apart and prints the
 * frames of the run. The gate: a level-5 bench at most +0.5 ms p50 and +1 ms p99 over level 1. Render thread only.
 */
final class FxStats {
    private FxStats() {}

    private static final int FRAMES = 1200;
    private static final long[] RING = new long[FRAMES];
    private static int head, filled;
    private static long frame, started;

    // the bench: casts left, the tick of the next, the frame count when it began
    private static String benchPower = "";
    private static int benchLevel, benchLeft, benchNext, benchTail;

    /** Starts a timed span (pair with {@link #end}). */
    static long begin() {
        return System.nanoTime();
    }

    static void end(long t0) {
        frame += System.nanoTime() - t0;
    }

    /** A new frame is being extracted: the last one's total goes in the ring. */
    static void nextFrame() {
        RING[head] = frame;
        head = (head + 1) % FRAMES;
        filled = Math.min(FRAMES, filled + 1);
        frame = 0;
    }

    static void reset() {
        filled = 0;
        head = 0;
        frame = 0;
    }

    /** p50, p99 and max in ms over the frames since the last reset (at most {@link #FRAMES}). */
    private static double[] percentiles() {
        if (filled == 0) return new double[3];
        long[] a = new long[filled];
        for (int i = 0; i < filled; i++) a[i] = RING[(head - 1 - i + FRAMES) % FRAMES];
        Arrays.sort(a);
        return new double[]{a[filled / 2] / 1e6, a[Math.min(filled - 1, (int) (filled * 0.99))] / 1e6, a[filled - 1] / 1e6};
    }

    private static void report(String what) {
        double[] p = percentiles();
        String line = String.format(Locale.ROOT, "%s: power FX %.3f ms p50, %.3f ms p99, %.3f ms max over %d frames; %d sprites, %d shapes live",
                what, p[0], p[1], p[2], filled, FxWorld.POOL.live(), FxWorld.liveShapes);
        Awaken.LOG.info("[fx] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
    }

    /** Client tick: runs the bench's casts. */
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            benchLeft = benchTail = 0;
            return;
        }
        if (benchLeft > 0 && FxWorld.ticks >= benchNext) {
            // the showcase refills energy and clears the cooldown, so every cast goes off
            mc.player.connection.sendCommand("awaken admin power showcase " + benchPower + " " + benchLevel);
            mc.player.connection.sendCommand("awaken admin power cast " + benchPower);
            benchLeft--;
            benchNext = FxWorld.ticks + 30;
            if (benchLeft == 0) benchTail = FxWorld.ticks + 60;
        }
        if (benchTail > 0 && FxWorld.ticks >= benchTail) {
            benchTail = 0;
            report(String.format(Locale.ROOT, "bench %s at level %d (%d ticks)", benchPower, benchLevel, FxWorld.ticks - started));
        }
    }

    static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("awaken").then(Commands.literal("fx")
                .then(Commands.literal("stats").executes(c -> {
                    report("stats");
                    reset();
                    return 1;
                }))
                .then(Commands.literal("bench").then(Commands.argument("power", StringArgumentType.word())
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 200)).executes(c -> {
                                    benchPower = StringArgumentType.getString(c, "power");
                                    benchLevel = IntegerArgumentType.getInteger(c, "level");
                                    benchLeft = IntegerArgumentType.getInteger(c, "count");
                                    benchNext = FxWorld.ticks + 10;
                                    started = FxWorld.ticks;
                                    reset();
                                    return 1;
                                })))))));
    }
}
