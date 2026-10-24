package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Development only ({@code /awaken ascent pilot rec <name> <ticks> [downscale]}): every rendered frame for a span of
 * ticks, saved as clips/&lt;name&gt;/f00000.png in the game folder with each frame's game time in times.txt, so
 * tools/ascent/clip.py can turn a tour into a real-time clip (frames are as uneven as the game's frame rate; the times
 * make the video even). Only while the autopilot runs; at most 900 frames a clip.
 */
final class ClipRecorder {
    private ClipRecorder() {}

    private static final boolean ON = System.getProperty("awaken.autopilot") != null;
    private static String name;
    private static int ticksLeft, frames, scale = 2;
    private static float lastT = -100;
    private static File dir;
    private static StringBuilder times;
    private static final AtomicInteger PENDING = new AtomicInteger();

    static void start(String clip, int ticks, int downscale) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        name = clip;
        ticksLeft = ticks;
        frames = 0;
        scale = Math.max(1, downscale);
        dir = new File(mc.gameDirectory, "clips/" + clip);
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        times = new StringBuilder();
        lastT = -100;
        Awaken.LOG.info("[ascent] recording {} for {} ticks", clip, ticks);
    }

    static void tick() {
        if (name == null) return;
        if (--ticksLeft <= 0) finish();
    }

    static void frame() {
        if (name == null || frames >= 900) return;
        Minecraft mc = Minecraft.getInstance();
        var target = mc.gameRenderer.mainRenderTarget();
        while (scale > 1 && (target.width % scale != 0 || target.height % scale != 0)) scale--;
        float t = AscentMoves.ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        // about 33 frames a second of game time is plenty for a clip, and keeps the disk and the frame rate sane
        if (t - lastT < 0.6F) return;
        lastT = t;
        int index = frames++;
        times.append(index).append(' ').append(t).append('\n');
        File file = new File(dir, String.format("f%05d.png", index));
        PENDING.incrementAndGet();
        Screenshot.takeScreenshot(target, scale, image -> Util.ioPool().execute(() -> {
            try (NativeImage img = image) {
                img.writeToFile(file);
            } catch (IOException ex) {
                Awaken.LOG.warn("[ascent] frame {} not saved: {}", index, ex.toString());
            } finally {
                PENDING.decrementAndGet();
            }
        }));
    }

    private static void finish() {
        try {
            Files.writeString(new File(dir, "times.txt").toPath(), times, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Awaken.LOG.warn("[ascent] times for {} not saved: {}", name, ex.toString());
        }
        Awaken.LOG.info("[ascent] recorded {}: {} frames", name, frames);
        name = null;
    }
}
