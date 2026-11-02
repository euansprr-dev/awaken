package dev.awaken.enchant.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.awaken.Awaken;
import dev.awaken.enchant.Marks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A dev hand for live tests of the table (autopilot runs only): reads lines from {@code <game>/enchant-live/in.txt} while
 * the client runs and moves the mouse the way a person would, through Minecraft's own mouse handler and the screen's
 * click, drag and release, once per rendered frame (what MouseHandler does for a real mouse; a window in the background
 * gets no real moves). Each stroke runs at a human pace (minimum-jerk speed, a wobbling hand, a start that misses a
 * little) and logs how it scored to {@code out.txt}.
 * <p>Lines: {@code cmd <command>}, {@code shot <name>}, {@code burst <name> <every ticks> <count>},
 * {@code trace <ms per stroke> <wobble> [gap ms] [miss]} (wobble and miss in mark units), {@code click <x> <y>} and
 * {@code move <x> <y>} (GUI), {@code pen <u> <v>} and {@code hold <u> <v>} (the mouse over, or pressed at, a point of the
 * mark's square), {@code lift}, {@code use}, {@code close}, {@code wait <ticks>}, {@code state}.
 */
final class LiveHand {
    private LiveHand() {}

    private static final boolean ON = System.getProperty("awaken.autopilot") != null;
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static final Random RNG = new Random(7);
    private static int waitTicks, polled;
    private static String burstName = "";
    private static int burstEvery, burstLeft, burstNext, burstNo;

    // the trace under way
    private static boolean tracing, down;
    private static int strokeAt, gapMs, frames;
    private static float msPer, wobble, miss;
    private static long phaseStart;
    private static boolean approaching;
    private static float startU, startV, fromU, fromV, ph1, ph2;
    private static double lastX, lastY;

    static void tick() {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (++polled % 10 == 0) poll(mc);
        if (burstLeft > 0 && --burstNext <= 0) {
            shot(mc, burstName + "_" + String.format(Locale.ROOT, "%02d", burstNo++));
            burstLeft--;
            burstNext = burstEvery;
        }
        if (tracing) return;
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!QUEUE.isEmpty() && waitTicks == 0 && !tracing) run(mc, QUEUE.poll());
    }

    private static void poll(Minecraft mc) {
        Path in = mc.gameDirectory.toPath().resolve("enchant-live/in.txt");
        if (!Files.exists(in)) return;
        try {
            List<String> lines = Files.readAllLines(in);
            Files.delete(in);
            for (String l : lines) if (!l.isBlank() && !l.startsWith("#")) QUEUE.add(l.trim());
        } catch (IOException e) {
            out("could not read in.txt: " + e);
        }
    }

    private static void run(Minecraft mc, String line) {
        String[] a = line.split(" ");
        out("> " + line);
        switch (a[0]) {
            case "cmd" -> {
                if (mc.player != null) mc.player.connection.sendCommand(line.substring(4).replaceFirst("^/", ""));
                waitTicks = 5;
            }
            case "shot" -> shot(mc, a[1]);
            case "burst" -> {
                burstName = a[1];
                burstEvery = Integer.parseInt(a[2]);
                burstLeft = Integer.parseInt(a[3]);
                burstNext = 0;
                burstNo = 0;
            }
            case "wait" -> waitTicks = Integer.parseInt(a[1]);
            case "click" -> {
                setCursor(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                button(mc, true);
                button(mc, false);
            }
            case "move" -> setCursor(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
            case "use" -> {
                net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
                waitTicks = 2;
            }
            case "pen" -> moveTo(mc, Float.parseFloat(a[1]), Float.parseFloat(a[2]));
            case "hold" -> {
                moveTo(mc, Float.parseFloat(a[1]), Float.parseFloat(a[2]));
                button(mc, true);
            }
            case "lift" -> button(mc, false);
            case "close" -> {
                if (mc.gui.screen() != null) mc.gui.screen().onClose();
            }
            case "state" -> out(state(mc));
            case "trace" -> {
                if (InscribeScene.phase != InscribeScene.TRACE) {
                    out("not tracing (phase " + InscribeScene.phase + ")");
                    return;
                }
                msPer = Float.parseFloat(a[1]);
                wobble = Float.parseFloat(a[2]);
                gapMs = a.length > 3 ? Integer.parseInt(a[3]) : 350;
                miss = a.length > 4 ? Float.parseFloat(a[4]) : 0.06F;
                tracing = true;
                beginStroke();
            }
            default -> out("unknown: " + line);
        }
    }

    private static String state(Minecraft mc) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "phase %d no %d stroke %d/%d progress %d fps %d clean %.2f shares",
                InscribeScene.phase, InscribeScene.no, InscribeScene.stroke, InscribeScene.strokes(), InscribeScene.progress, mc.getFps(), InscribeScene.clean()));
        for (int i = 0; i < InscribeScene.strokes(); i++) sb.append(String.format(Locale.ROOT, " %.2f", InscribeScene.SHARE[i]));
        return sb.toString();
    }

    // ---- the hand ---------------------------------------------------------------------------------------------------

    private static void beginStroke() {
        strokeAt = InscribeScene.stroke;
        approaching = true;
        phaseStart = System.nanoTime();
        frames = 0;
        float[] s = Marks.of(InscribeScene.no)[strokeAt];
        double ang = RNG.nextDouble() * Math.PI * 2;
        startU = s[0] + (float) Math.cos(ang) * miss;
        startV = s[1] + (float) Math.sin(ang) * miss;
        fromU = InscribeScene.penU;
        fromV = InscribeScene.penV;
        ph1 = RNG.nextFloat() * 6.28F;
        ph2 = RNG.nextFloat() * 6.28F;
    }

    /** Each rendered frame: where the hand is now, sent as a real mouse would be. */
    static void frame() {
        if (!ON || !tracing) return;
        Minecraft mc = Minecraft.getInstance();
        if (InscribeScene.phase != InscribeScene.TRACE || !(mc.gui.screen() instanceof InscribeScreen)) {
            if (down) button(mc, false);
            tracing = false;
            out("trace ended (phase " + InscribeScene.phase + ") " + state(mc));
            return;
        }
        double ms = (System.nanoTime() - phaseStart) / 1e6;
        float[] s = Marks.of(InscribeScene.no)[strokeAt];
        if (approaching) {
            // the hand comes to the glowing end, eased, in a quarter second, then presses
            double t = Math.min(1, ms / 250);
            double e = jerk(t);
            moveTo(mc, Mth.lerp(e, fromU, startU), Mth.lerp(e, fromV, startV));
            if (t >= 1) {
                button(mc, true);
                approaching = false;
                phaseStart = System.nanoTime();
                if (!InscribeScene.drawing) out(String.format(Locale.ROOT, "stroke %d: the press missed the end (%.2f off)", strokeAt,
                        Mth.length(startU - s[0], startV - s[1])));
            }
            return;
        }
        if (InscribeScene.stroke != strokeAt) {
            // done: the stroke counted while the hand was still on it (a real hand lifts soon after)
            finishStroke(mc, true, ms);
            return;
        }
        double t = Math.min(1, ms / msPer);
        float[] at = along(s, (float) jerk(t));
        float wob = (float) (wobble * (0.65 * Math.sin(ms / 1000 * 2 * Math.PI * 3.1 + ph1) + 0.35 * Math.sin(ms / 1000 * 2 * Math.PI * 7.3 + ph2)));
        // the start's miss fades out as the hand settles onto the line
        float settle = (float) Math.max(0, 1 - t * 4);
        moveTo(mc, at[0] - at[3] * wob + (startU - s[0]) * settle, at[1] + at[2] * wob + (startV - s[1]) * settle);
        frames++;
        if (InscribeScene.stroke != strokeAt) finishStroke(mc, true, ms);
        else if (t >= 1 && ms > msPer + 120) finishStroke(mc, false, ms);
    }

    private static void finishStroke(Minecraft mc, boolean counted, double ms) {
        button(mc, false);
        float[] s = Marks.of(InscribeScene.no)[strokeAt];
        out(String.format(Locale.ROOT, "stroke %d %s after %.0f ms, %d frames: share %.2f%s", strokeAt, counted ? "counted" : "NOT counted",
                ms, frames, InscribeScene.SHARE[strokeAt], counted ? "" : " (stalled at point " + InscribeScene.progress + " of " + s.length / 2 + ")"));
        if (!counted || InscribeScene.stroke >= InscribeScene.strokes()) {
            tracing = false;
            out(state(mc));
            return;
        }
        // a breath between strokes, then the next
        phaseStart = System.nanoTime() + gapMs * 1_000_000L;
        approaching = true;
        strokeAt = InscribeScene.stroke;
        float[] n = Marks.of(InscribeScene.no)[strokeAt];
        double ang = RNG.nextDouble() * Math.PI * 2;
        fromU = InscribeScene.penU;
        fromV = InscribeScene.penV;
        startU = n[0] + (float) Math.cos(ang) * miss;
        startV = n[1] + (float) Math.sin(ang) * miss;
        frames = 0;
    }

    /** Minimum-jerk easing: how a practised hand speeds up and slows down along a stroke. */
    private static double jerk(double t) {
        if (t <= 0) return 0;
        t = Math.min(1, t);
        return t * t * t * (10 - 15 * t + 6 * t * t);
    }

    /** The point a fraction of the way along a stroke by length, with its unit direction: {u, v, du, dv}. */
    private static float[] along(float[] s, float f) {
        int n = s.length / 2;
        float total = 0;
        for (int i = 1; i < n; i++) total += Mth.length(s[i * 2] - s[i * 2 - 2], s[i * 2 + 1] - s[i * 2 - 1]);
        float want = f * total, run = 0;
        for (int i = 1; i < n; i++) {
            float du = s[i * 2] - s[i * 2 - 2], dv = s[i * 2 + 1] - s[i * 2 - 1], l = Mth.length(du, dv);
            if (run + l >= want || i == n - 1) {
                float k = l <= 1e-6F ? 0 : Mth.clamp((want - run) / l, 0, 1);
                return new float[]{s[i * 2 - 2] + du * k, s[i * 2 - 1] + dv * k, l <= 1e-6F ? 1 : du / l, l <= 1e-6F ? 0 : dv / l};
            }
            run += l;
        }
        return new float[]{s[0], s[1], 1, 0};
    }

    /** A point on the mark (its own square) to the GUI pixel where the eye sees it, then the mouse there. */
    private static void moveTo(Minecraft mc, double u, double v) {
        var cam = mc.gameRenderer.mainCamera();
        Screen sc = mc.gui.screen();
        if (sc == null) return;
        double m = InscribeScene.MARK;
        double px = m * (u * InscribeScene.rx - v * InscribeScene.fx), pz = m * (u * InscribeScene.rz - v * InscribeScene.fz);
        Vec3 c = InscribeScene.centre, o = cam.position();
        double dx = c.x + px - o.x, dy = InscribeScene.slateY + 0.035 - o.y, dz = c.z + pz - o.z;
        var fw = cam.forwardVector();
        var up = cam.upVector();
        var left = cam.leftVector();
        double f = dx * fw.x() + dy * fw.y() + dz * fw.z();
        if (f <= 1e-4) return;
        double tan = Math.tan(Math.toRadians(cam.getFov()) / 2), aspect = sc.width / (double) sc.height;
        double nx = -(dx * left.x() + dy * left.y() + dz * left.z()) / (f * tan * aspect);
        double ny = (dx * up.x() + dy * up.y() + dz * up.z()) / (f * tan);
        setCursor(mc, (nx + 1) / 2 * sc.width, (1 - ny) / 2 * sc.height);
        if (down) {
            double gx = lastX, gy = lastY;
            sc.mouseMoved(gx, gy);
            sc.mouseDragged(new MouseButtonEvent(gx, gy, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), 0, 0);
        }
    }

    private static void setCursor(Minecraft mc, double gx, double gy) {
        Window w = mc.getWindow();
        lastX = gx;
        lastY = gy;
        double sx = gx * w.getScreenWidth() / w.getGuiScaledWidth(), sy = gy * w.getScreenHeight() / w.getGuiScaledHeight();
        mc.mouseHandler.onMove(w.handle(), sx, sy, 0, 0);
    }

    private static void button(Minecraft mc, boolean press) {
        down = press;
        mc.mouseHandler.onButton(mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), press ? 1 : 0);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> out("shot " + name));
    }

    private static void out(String s) {
        Awaken.LOG.info("[live] {}", s);
        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("enchant-live/out.txt");
            Files.createDirectories(p.getParent());
            Files.writeString(p, s + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
