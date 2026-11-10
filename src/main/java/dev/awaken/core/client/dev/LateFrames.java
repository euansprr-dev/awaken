package dev.awaken.core.client.dev;

import dev.awaken.Awaken;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * Dev only (autopilot {@code late on|calibrate|report <label>|off}): where a late frame's time went (Steady Light,
 * SPEC §22). Two things, both cheap enough to leave the frames as they are.
 *
 * <p>Every frame is split into its three parts on the render thread: the client ticks that ran in it, the render
 * (RenderFrameEvent.Pre to Post), and the rest (the swap, the frame limiter, anything between). A late frame's parts
 * are compared with the on-time frames' medians, so the report says which part the lost time was in.
 *
 * <p>A watcher thread looks at the render thread twice a millisecond, and only when the part it is in has already run
 * longer than usual (the medians from {@code calibrate}, plus a margin) does it take the render thread's stack. Those
 * stacks, counted, are what the long ticks and long renders were doing.
 */
final class LateFrames {
    private static final int MAX = 60_000;
    private static final long SLOT = 8_333_333L;
    private static final long[] GAP = new long[MAX], TICK = new long[MAX], RENDER = new long[MAX];
    private static final byte[] TICKS = new byte[MAX];
    private static int frames;
    private static long lastPost, tickStart, tickSum, renderStart, renderTime;
    private static int ticksIn;
    // what the watcher reads: the part the render thread is in (0 between, 1 a tick, 2 the render) and since when
    private static volatile int part;
    private static volatile long partStart;
    private static volatile boolean on, sampling;
    private static volatile long tickOver = 4_000_000L, renderOver = 14_000_000L, betweenOver = 14_000_000L;
    private static volatile Thread render;
    private static Thread watcher;
    private static final Map<String, int[]> STACKS = new HashMap<>();
    private static final int[] SAMPLES = new int[3];
    private static long gcCount, gcMillis;
    private static boolean hooked;

    private LateFrames() {}

    static String command(String arg) {
        String[] a = arg.trim().split("\\s+");
        switch (a[0]) {
            case "on" -> {
                hook();
                reset();
                on = true;
                return "recording frames (stacks " + (sampling ? "on" : "after calibrate") + ")";
            }
            case "calibrate" -> {
                return calibrate();
            }
            case "report" -> {
                return report(a.length > 1 ? a[1] : "late");
            }
            case "off" -> {
                on = false;
                sampling = false;
                return "off";
            }
            default -> {
                return "late on|calibrate|report <label>|off";
            }
        }
    }

    private static void hook() {
        if (hooked) return;
        hooked = true;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> {
            if (!on) return;
            tickStart = System.nanoTime();
            partStart = tickStart;
            part = 1;
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (!on) return;
            long now = System.nanoTime();
            if (tickStart != 0) {
                tickSum += now - tickStart;
                ticksIn++;
            }
            partStart = now;
            part = 0;
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> {
            if (!on) return;
            if (render == null) render = Thread.currentThread();
            renderStart = System.nanoTime();
            partStart = renderStart;
            part = 2;
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> {
            if (!on) return;
            long now = System.nanoTime();
            renderTime = renderStart != 0 ? now - renderStart : 0;
            if (lastPost != 0 && frames < MAX) {
                GAP[frames] = now - lastPost;
                TICK[frames] = tickSum;
                RENDER[frames] = renderTime;
                TICKS[frames] = (byte) Math.min(ticksIn, 127);
                frames++;
            }
            lastPost = now;
            tickSum = 0;
            ticksIn = 0;
            partStart = now;
            part = 0;
        });
        watcher = new Thread(LateFrames::watch, "Awaken late frames");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void reset() {
        frames = 0;
        lastPost = 0;
        tickSum = 0;
        ticksIn = 0;
        synchronized (STACKS) {
            STACKS.clear();
            Arrays.fill(SAMPLES, 0);
        }
        long[] gc = gc();
        gcCount = gc[0];
        gcMillis = gc[1];
    }

    private static long[] gc() {
        long n = 0, ms = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!b.getName().contains("Pauses")) continue;
            n += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        return new long[] {n, ms};
    }

    private static long median(long[] src, int n) {
        if (n == 0) return 0;
        long[] c = Arrays.copyOf(src, n);
        Arrays.sort(c);
        return c[n / 2];
    }

    /** The usual length of each part from the frames so far, and from now on a part that runs well past it is sampled. */
    private static String calibrate() {
        int n = frames;
        if (n < 200) return "too few frames to calibrate (" + n + ")";
        long[] between = new long[n], ticks = new long[n];
        int tn = 0;
        for (int i = 0; i < n; i++) {
            between[i] = GAP[i] - TICK[i] - RENDER[i];
            if (TICKS[i] > 0) ticks[tn++] = TICK[i];
        }
        long mt = median(ticks, tn), mr = median(RENDER, n), mb = median(between, n);
        tickOver = mt + 2_000_000L;
        renderOver = mr + 3_000_000L;
        betweenOver = mb + 3_000_000L;
        sampling = true;
        reset();
        return String.format(Locale.ROOT, "usual tick %.2f ms, render %.2f, between %.2f; sampling a tick past %.1f, a render past %.1f, a wait past %.1f",
                mt / 1e6, mr / 1e6, mb / 1e6, tickOver / 1e6, renderOver / 1e6, betweenOver / 1e6);
    }

    private static void watch() {
        while (true) {
            LockSupport.parkNanos(500_000L);
            Thread t = render;
            if (!on || !sampling || t == null) continue;
            int p = part;
            long over = System.nanoTime() - partStart - (p == 1 ? tickOver : p == 2 ? renderOver : betweenOver);
            if (over <= 0) continue;
            StackTraceElement[] st = t.getStackTrace();
            if (st.length == 0) continue;
            String key = (p == 1 ? "tick   | " : p == 2 ? "render | " : "wait   | ") + brief(st);
            synchronized (STACKS) {
                STACKS.computeIfAbsent(key, k -> new int[1])[0]++;
                SAMPLES[p]++;
            }
        }
    }

    // the leaf and the callers that say whose work it is: at most nine frames, runs of one class folded, plumbing skipped
    private static String brief(StackTraceElement[] st) {
        StringBuilder sb = new StringBuilder();
        String lastClass = "";
        int kept = 0;
        for (int i = 0; i < st.length && kept < 9; i++) {
            String c = st[i].getClassName();
            if (i > 0 && (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.") || c.startsWith("it.unimi.")
                    || c.startsWith("com.google.") || c.startsWith("net.neoforged.bus.") || c.contains("$$Lambda"))) continue;
            if (c.equals(lastClass)) continue;
            lastClass = c;
            int cut = c.lastIndexOf('.', c.lastIndexOf('.') - 1);
            if (kept > 0) sb.append(" < ");
            sb.append(cut > 0 ? c.substring(cut + 1) : c).append('.').append(st[i].getMethodName());
            kept++;
        }
        return sb.toString();
    }

    private static String report(String label) {
        int n = frames;
        if (n < 50) return "too few frames (" + n + ")";
        long[] between = new long[n], ticks = new long[n];
        int tn = 0, late = 0, doubles = 0;
        for (int i = 0; i < n; i++) {
            between[i] = GAP[i] - TICK[i] - RENDER[i];
            if (TICKS[i] > 0) ticks[tn++] = TICK[i];
            if (TICKS[i] > 1) doubles++;
        }
        long mt = median(ticks, tn), mr = median(RENDER, n), mb = median(between, n);
        double exT = 0, exR = 0, exB = 0;
        int byT = 0, byR = 0, byB = 0, lateTicked = 0;
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (Math.round(GAP[i] / (double) SLOT) < 3) continue;
            late++;
            if (TICKS[i] > 0) lateTicked++;
            // a frame with no tick has no tick time to be over by
            double t = Math.max(0, TICK[i] - (TICKS[i] > 0 ? mt : 0)), r = Math.max(0, RENDER[i] - mr), b = Math.max(0, between[i] - mb);
            exT += t;
            exR += r;
            exB += b;
            if (t >= r && t >= b) byT++;
            else if (r >= b) byR++;
            else byB++;
            if (rows.size() < 400) rows.add(String.format(Locale.ROOT, "%d\t%.2f\t%.2f\t%.2f\t%.2f\t%d", i, GAP[i] / 1e6, TICK[i] / 1e6, RENDER[i] / 1e6, between[i] / 1e6, TICKS[i]));
        }
        long[] gc = gc();
        double ex = Math.max(exT + exR + exB, 1);
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "frames %d, late (three refreshes or more) %d = %.1f%%; frames with two ticks or more %d%n", n, late, 100.0 * late / n, doubles));
        out.append(String.format(Locale.ROOT, "usual: a tick %.2f ms (in %d of %d frames), the render %.2f, between %.2f%n", mt / 1e6, tn, n, mr / 1e6, mb / 1e6));
        out.append(String.format(Locale.ROOT, "the late frames' lost time: ticks %.0f%%, render %.0f%%, between %.0f%%; worst part of each: tick %d, render %d, between %d; %d of them had a tick%n",
                100 * exT / ex, 100 * exR / ex, 100 * exB / ex, byT, byR, byB, lateTicked));
        out.append(String.format(Locale.ROOT, "GC pauses in the window: %d, %d ms in all%n", gc[0] - gcCount, gc[1] - gcMillis));
        String head = out.toString();
        List<Map.Entry<String, int[]>> top;
        int[] samples;
        synchronized (STACKS) {
            top = new ArrayList<>(STACKS.entrySet());
            samples = SAMPLES.clone();
        }
        top.sort((x, y) -> y.getValue()[0] - x.getValue()[0]);
        out.append(String.format(Locale.ROOT, "%nstacks of the render thread while a part ran long (samples: tick %d, render %d, wait %d)%n", samples[1], samples[2], samples[0]));
        int shown = 0;
        for (Map.Entry<String, int[]> e : top) {
            if (shown++ >= 60) break;
            out.append(String.format(Locale.ROOT, "%5d  %s%n", e.getValue()[0], e.getKey()));
        }
        out.append("\nlate frames: index, gap, ticks, render, between (ms), ticks run\n");
        for (String r : rows) out.append(r).append('\n');
        try {
            Path dir = Path.of("perf-traces");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(label + "-late.txt"), out.toString());
        } catch (java.io.IOException e) {
            Awaken.LOG.warn("[autopilot] late report not written: {}", e.toString());
        }
        reset();
        return head.replace(System.lineSeparator(), " | ").trim();
    }
}
