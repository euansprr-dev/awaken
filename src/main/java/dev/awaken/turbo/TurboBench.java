package dev.awaken.turbo;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Launch-time numbers for Turbo's experiments (autopilot runs, or -Dawaken.turbo.bench=true): one {@code [turbo] bench}
 * line each for launch to title screen (the loading overlay gone), join to first world frame, and every frame of the
 * first 30 s in the world (p50, p99, p99.5, the worst frame: the first-world-load hitch). Each line carries the switches and MoltenVK settings
 * of the run. Frames go into a preallocated array: measuring costs no allocation per frame.
 */
final class TurboBench {
    private TurboBench() {}

    private static final long WINDOW = 30_000_000_000L;
    private static final long[] FRAMES = new long[30 * 400];
    private static int count;
    private static boolean titleDone, sawOverlay;
    private static long joinNanos;
    private static long windowStart = -1;
    private static long lastFrame;
    private static boolean done;

    static void init() {
        if (System.getProperty("awaken.autopilot") == null && !Boolean.getBoolean("awaken.turbo.bench")) return;
        NeoForge.EVENT_BUS.addListener(TurboBench::frame);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
            if (joinNanos == 0) joinNanos = System.nanoTime();
        });
    }

    private static void frame(RenderFrameEvent.Post e) {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        // the loading overlay gone for the first time: resources and pipelines are loaded (autopilot may already have
        // left the title screen while the overlay faded, so the screen itself isn't the signal)
        if (mc.gui.overlay() != null) sawOverlay = true;
        if (!titleDone && sawOverlay && mc.gui.overlay() == null) {
            titleDone = true;
            Optional<Instant> start = ProcessHandle.current().info().startInstant();
            TurboVersions.LOG.info("[turbo] bench launch_to_title={}ms {}",
                    start.map(s -> Long.toString(Duration.between(s, Instant.now()).toMillis())).orElse("?"), variant());
        }
        if (windowStart < 0) {
            if (mc.level == null || mc.gui.screen() != null || mc.gui.overlay() != null) return;
            windowStart = now;
            lastFrame = now;
            TurboVersions.LOG.info("[turbo] bench join_to_first_frame={}ms", joinNanos == 0 ? "?" : Long.toString((now - joinNanos) / 1_000_000));
            return;
        }
        if (count < FRAMES.length) FRAMES[count++] = now - lastFrame;
        lastFrame = now;
        if (now - windowStart >= WINDOW || count == FRAMES.length) report();
    }

    private static void report() {
        done = true;
        long[] f = Arrays.copyOf(FRAMES, count);
        Arrays.sort(f);
        int over17 = 0, over33 = 0;
        for (long v : f) {
            if (v > 16_700_000L) over17++;
            if (v > 33_300_000L) over33++;
        }
        TurboVersions.LOG.info(String.format(Locale.ROOT,
                "[turbo] bench first30s frames=%d p50=%.2fms p99=%.2fms p99.5=%.2fms max=%.1fms over16.7=%d over33=%d %s",
                count, ms(f, 0.50), ms(f, 0.99), ms(f, 0.995), f.length == 0 ? 0.0 : f[f.length - 1] / 1e6, over17, over33, variant()));
    }

    private static double ms(long[] sorted, double q) {
        if (sorted.length == 0) return 0.0;
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(q * sorted.length))] / 1e6;
    }

    /** The run's switches and any MoltenVK settings, so every line says which variant it measured. */
    private static String variant() {
        StringBuilder mvk = new StringBuilder();
        System.getenv().forEach((k, v) -> {
            if (k.startsWith("MVK_CONFIG_")) mvk.append(mvk.isEmpty() ? "" : ",").append(k.substring(11)).append('=').append(v);
        });
        return "[" + TurboSwitches.describe() + (mvk.isEmpty() ? "" : " mvk=" + mvk) + "]";
    }
}
