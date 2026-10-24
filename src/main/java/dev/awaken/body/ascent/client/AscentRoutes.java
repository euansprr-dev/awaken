package dev.awaken.body.ascent.client;

import dev.awaken.Awaken;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Routes (development; "golden runs" for Carillon's TryDriver and the Ascent's own regressions): a run recorded tick by
 * tick (the movement keys, the movement key, the view) and played back through the same keys, from the same start, so
 * a route that worked once can be run again after a change and its end compared. A route is a text file:
 * <pre>
 * route 1 &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;yaw&gt; &lt;pitch&gt;      the start
 * &lt;keys&gt; &lt;yaw&gt; &lt;pitch&gt;                   one line a tick; keys = bits forward 1, back 2, left 4, right 8,
 *                                         jump 16, sneak 32, sprint 64, movement key 128
 * end &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;ticks&gt;              where the run ended
 * </pre>
 * Files live in {@code <game dir>/awaken-routes/<name>.route} (or a path, when the name holds a '/'). Pilot lines:
 * {@code route rec <name>}, {@code route play <name>}, {@code route stop}. Playing teleports to the start (a command:
 * game masters only), waits for the body to settle, then plays; at the end it logs how far it ended from the
 * recording's end ({@code [ascent] route <name> ...}).
 */
public final class AscentRoutes {
    private AscentRoutes() {}

    /** Ticks between the teleport and the first frame (the body settles; the start is the same every time). */
    private static final int SETTLE = 20;

    private record Frame(int keys, float yaw, float pitch) {}

    private static String name = "";
    private static boolean recording, playing;
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static double sx, sy, sz, ex, ey, ez;
    private static float syaw, spitch;
    private static int at, wait;

    public static boolean recording() {
        return recording;
    }

    public static boolean playing() {
        return playing;
    }

    static void line(String[] a) {
        switch (a.length > 1 ? a[1] : "") {
            case "rec" -> record(a.length > 2 ? a[2] : "route");
            case "play" -> play(a.length > 2 ? a[2] : "route");
            case "stop" -> stop();
            default -> Awaken.LOG.warn("[ascent] pilot: route rec <name> | play <name> | stop");
        }
    }

    /** Starts recording the local player's run from where they stand. */
    public static void record(String route) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        stop();
        name = route;
        FRAMES.clear();
        sx = p.getX();
        sy = p.getY();
        sz = p.getZ();
        syaw = p.getYRot();
        spitch = p.getXRot();
        recording = true;
        Awaken.LOG.info("[ascent] route {} recording", name);
    }

    /** Plays a recorded route: back to its start, a moment to settle, then every tick's keys and view. */
    public static boolean play(String route) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        stop();
        if (!load(route)) return false;
        name = route;
        p.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.4f %.4f %.4f %.3f %.3f", sx, sy, sz, syaw, spitch));
        at = 0;
        wait = SETTLE;
        playing = true;
        Awaken.LOG.info("[ascent] route {} playing ({} ticks)", name, FRAMES.size());
        return true;
    }

    public static void stop() {
        if (recording) {
            recording = false;
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) save(p);
        }
        if (playing) {
            playing = false;
            keys(0);
        }
    }

    static void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            recording = playing = false;
            return;
        }
        if (recording) {
            Options o = Minecraft.getInstance().options;
            int k = bit(o.keyUp, 1) | bit(o.keyDown, 2) | bit(o.keyLeft, 4) | bit(o.keyRight, 8) | bit(o.keyJump, 16)
                    | bit(o.keyShift, 32) | bit(o.keySprint, 64) | bit(AwakenKeys.MOVEMENT, 128);
            FRAMES.add(new Frame(k, p.getYRot(), p.getXRot()));
            if (FRAMES.size() >= 20 * 60 * 5) stop(); // five minutes at most
        }
        if (!playing) return;
        if (wait > 0) {
            wait--;
            keys(0);
            return;
        }
        if (at >= FRAMES.size()) {
            playing = false;
            keys(0);
            double d = Math.sqrt((p.getX() - ex) * (p.getX() - ex) + (p.getY() - ey) * (p.getY() - ey) + (p.getZ() - ez) * (p.getZ() - ez));
            Awaken.LOG.info(String.format(Locale.ROOT, "[ascent] route %s done in %d ticks at (%.2f, %.2f, %.2f); recorded end (%.2f, %.2f, %.2f), off by %.2f",
                    name, FRAMES.size(), p.getX(), p.getY(), p.getZ(), ex, ey, ez, d));
            return;
        }
        Frame f = FRAMES.get(at++);
        keys(f.keys);
        p.setYRot(f.yaw);
        p.setYHeadRot(f.yaw);
        p.setXRot(f.pitch);
    }

    private static int bit(KeyMapping k, int bit) {
        return k.isDown() ? bit : 0;
    }

    private static void keys(int k) {
        Options o = Minecraft.getInstance().options;
        o.keyUp.setDown((k & 1) != 0);
        o.keyDown.setDown((k & 2) != 0);
        o.keyLeft.setDown((k & 4) != 0);
        o.keyRight.setDown((k & 8) != 0);
        o.keyJump.setDown((k & 16) != 0);
        o.keyShift.setDown((k & 32) != 0);
        o.keySprint.setDown((k & 64) != 0);
        AwakenKeys.MOVEMENT.setDown((k & 128) != 0);
    }

    private static Path path(String route) {
        if (route.contains("/")) return Path.of(route.endsWith(".route") ? route : route + ".route");
        return Minecraft.getInstance().gameDirectory.toPath().resolve("awaken-routes").resolve(route + ".route");
    }

    private static void save(LocalPlayer p) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "route 1 %.4f %.4f %.4f %.3f %.3f%n", sx, sy, sz, syaw, spitch));
        for (Frame f : FRAMES) b.append(String.format(Locale.ROOT, "%d %.3f %.3f%n", f.keys, f.yaw, f.pitch));
        b.append(String.format(Locale.ROOT, "end %.4f %.4f %.4f %d%n", p.getX(), p.getY(), p.getZ(), FRAMES.size()));
        Path out = path(name);
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, b);
            Awaken.LOG.info("[ascent] route {} saved: {} ticks to {}", name, FRAMES.size(), out);
        } catch (IOException e) {
            Awaken.LOG.warn("[ascent] route {} not saved: {}", name, e.toString());
        }
    }

    private static boolean load(String route) {
        Path in = path(route);
        FRAMES.clear();
        try {
            for (String l : Files.readAllLines(in)) {
                String[] t = l.trim().split("\\s+");
                if (t.length == 0 || t[0].isEmpty() || t[0].startsWith("#")) continue;
                switch (t[0]) {
                    case "route" -> {
                        sx = Double.parseDouble(t[2]);
                        sy = Double.parseDouble(t[3]);
                        sz = Double.parseDouble(t[4]);
                        syaw = Float.parseFloat(t[5]);
                        spitch = Float.parseFloat(t[6]);
                    }
                    case "end" -> {
                        ex = Double.parseDouble(t[1]);
                        ey = Double.parseDouble(t[2]);
                        ez = Double.parseDouble(t[3]);
                    }
                    default -> FRAMES.add(new Frame(Integer.parseInt(t[0]), Float.parseFloat(t[1]), Float.parseFloat(t[2])));
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.warn("[ascent] route {} can't be read from {}: {}", route, in, e.toString());
            return false;
        }
    }
}
