package dev.awaken.arms.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.awaken.Awaken;
import dev.awaken.arms.FilmLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Development only (autopilot {@code arms film <name> <ticks> [gap] [downscale]}): a fight filmed as the game draws it,
 * into clips/&lt;name&gt;/ in the game folder. A picture every {@code gap} ticks of game time at least (f00000.png...,
 * their times in times.txt as the clip recorder's), and for every frame drawn, picture or not, a row of track.tsv:
 * the hands' clock, the move, the press and hold, the hit-stop, each blade's line where it is drawn (first person in
 * the hands' view space, third person from the camera), and the camera. events.tsv holds what happened between frames
 * on both sides (the press, the strike begun, the server's word, the swish, the impact; the server's own clock through
 * {@link FilmLog}). Only while the autopilot runs.
 */
final class ArmsFilm {
    private ArmsFilm() {}

    private static final boolean ON = System.getProperty("awaken.autopilot") != null;
    private static String name;
    private static int ticksLeft, frames, pictures, scale = 2;
    private static float gap = 0.15F, lastPicture = -100;
    private static long startNanos;
    private static File dir;
    private static StringBuilder track, events, times;
    // this frame's blades: [first person main, twin, third person main, twin] x [has, tip xyz, base xyz]
    private static final float[][] BLADE = new float[4][7];
    private static final Vector3f A = new Vector3f(), B = new Vector3f();

    static boolean on() {
        return name != null;
    }

    /** The twin's own first-person draw (ArmsOffHand), whose blade has no state while it isn't striking. */
    static boolean twinPass;

    /**
     * A blade of ours, whatever its ribbon does: everything first person draws is ours, and from outside one our body
     * holds (WeaponRenderer gives a blade the wielder of its ribbon only while it traces).
     */
    static boolean ours(WeaponRenderer.@Nullable State s) {
        if (twinPass) return true;
        LocalPlayer p = Minecraft.getInstance().player;
        if (s == null || p == null) return false;
        return s.hand() || s.wielder() == p.getId() || s.holder() == p.getId();
    }

    /** 0 the main blade, 1 the twin (from outside, the one in the left arm). */
    static int side(WeaponRenderer.@Nullable State s) {
        return twinPass || s != null && (s.twin() || s.arm() == 2) ? 1 : 0;
    }

    static boolean firstPerson(WeaponRenderer.@Nullable State s) {
        return twinPass || s != null && s.hand();
    }

    static String start(String clip, int ticks, float minGap, int downscale) {
        if (!ON) return "the film runs only under the autopilot";
        Minecraft mc = Minecraft.getInstance();
        name = clip;
        ticksLeft = ticks;
        frames = pictures = 0;
        gap = Math.max(0.01F, minGap);
        scale = Math.max(1, downscale);
        lastPicture = -100;
        startNanos = System.nanoTime();
        dir = new File(mc.gameDirectory, "clips/" + clip);
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        track = new StringBuilder("frame\tpic\tgt\treal_ms\tview\traw\tmove\tstep\twait\tcharging\tkey\thold\tyaw\tpitch\tfov\tw\th\tthird"
                + "\tm_has\tm_tx\tm_ty\tm_tz\tm_bx\tm_by\tm_bz\to_has\to_tx\to_ty\to_tz\to_bx\to_by\to_bz"
                + "\tm3_has\tm3_tx\tm3_ty\tm3_tz\tm3_bx\tm3_by\tm3_bz\to3_has\to3_tx\to3_ty\to3_tz\to3_bx\to3_by\to3_bz\n");
        events = new StringBuilder("real_ms\tgt\tside\twhat\n");
        times = new StringBuilder();
        FilmLog.SERVER.clear();
        FilmLog.on = true;
        Awaken.LOG.info("[arms film] {} for {} ticks, a picture every {} ticks", clip, ticks, gap);
        return "filming " + clip;
    }

    /** Something happened on the client (a press, a move begun, the server's word, an impact). */
    static void event(String what) {
        if (name == null) return;
        events.append(String.format(Locale.ROOT, "%.2f\t%.3f\tc\t%s\n", (System.nanoTime() - startNanos) / 1e6, gameTime(), what));
    }

    /**
     * A blade drawn this frame: {@code side} 0 the main, 1 the twin; {@code hand} the first-person pass (view space), else
     * seen from outside (from the camera); its line from its base to its tip, in the pose's space.
     */
    static void blade(int side, boolean hand, Matrix4f pose, float bx, float by, float bz, float tx, float ty, float tz) {
        if (name == null) return;
        float[] b = BLADE[(hand ? 0 : 2) + (side == 0 ? 0 : 1)];
        pose.transformPosition(tx, ty, tz, A);
        pose.transformPosition(bx, by, bz, B);
        b[0] = 1;
        b[1] = A.x;
        b[2] = A.y;
        b[3] = A.z;
        b[4] = B.x;
        b[5] = B.y;
        b[6] = B.z;
    }

    static void tick() {
        if (name == null) return;
        if (--ticksLeft <= 0) finish();
    }

    private static float gameTime() {
        return ArmsPredict.ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    /** After a frame is drawn: its row, and its picture when the gap has passed. */
    static void frame() {
        if (name == null) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.nanoTime();
        for (String s; (s = FilmLog.SERVER.poll()) != null; ) {
            int tab = s.indexOf('\t');
            long at = Long.parseLong(s.substring(0, tab));
            events.append(String.format(Locale.ROOT, "%.2f\t\ts\t%s\n", (at - startNanos) / 1e6, s.substring(tab + 1)));
        }
        float t = gameTime();
        int pic = -1;
        if (t - lastPicture >= gap && pictures < 2400) {
            lastPicture = t;
            pic = pictures++;
            times.append(pic).append(' ').append(t).append('\n');
            File file = new File(dir, String.format("f%05d.png", pic));
            var target = mc.gameRenderer.mainRenderTarget();
            while (scale > 1 && (target.width % scale != 0 || target.height % scale != 0)) scale--;
            Screenshot.takeScreenshot(target, scale, image -> Util.ioPool().execute(() -> {
                try (NativeImage img = image) {
                    img.writeToFile(file);
                } catch (IOException ex) {
                    Awaken.LOG.warn("[arms film] frame {} not saved: {}", file.getName(), ex.toString());
                }
            }));
        }
        var cam = mc.gameRenderer.mainCamera();
        var m = ArmsPredict.move;
        track.append(frames++).append('\t').append(pic).append('\t').append(f(t)).append('\t').append(f((now - startNanos) / 1e6F))
                .append('\t').append(f(m == null ? -1 : ArmsPredict.viewTime())).append('\t').append(f(m == null ? -1 : ArmsPredict.moveTime()))
                .append('\t').append(m == null ? "-" : m.key).append('\t').append(ArmsPredict.step)
                .append('\t').append(ArmsPredict.waitHit ? 1 : 0).append('\t').append(ArmsPredict.charging ? 1 : 0)
                .append('\t').append(mc.options.keyAttack.isDown() ? 1 : 0).append('\t').append(ArmsFeel.holding() ? 1 : 0)
                .append('\t').append(f(cam.yRot())).append('\t').append(f(cam.xRot())).append('\t').append(f(cam.getFov()))
                .append('\t').append(mc.getWindow().getWidth()).append('\t').append(mc.getWindow().getHeight())
                .append('\t').append(mc.options.getCameraType().isFirstPerson() ? 0 : 1);
        for (float[] b : BLADE) {
            for (float v : b) track.append('\t').append(f(v));
            b[0] = 0;
        }
        track.append('\n');
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static void finish() {
        FilmLog.on = false;
        try {
            Files.writeString(new File(dir, "track.tsv").toPath(), track, StandardCharsets.UTF_8);
            Files.writeString(new File(dir, "events.tsv").toPath(), events, StandardCharsets.UTF_8);
            Files.writeString(new File(dir, "times.txt").toPath(), times, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Awaken.LOG.warn("[arms film] {} not saved: {}", name, ex.toString());
        }
        Awaken.LOG.info("[arms film] filmed {}: {} frames, {} pictures", name, frames, pictures);
        name = null;
    }
}
