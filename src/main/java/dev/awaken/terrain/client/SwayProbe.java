package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import dev.awaken.terrain.PlantSway;
import dev.awaken.terrain.SwayMath;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Logs, frame by frame, how far the waving shader moves a few vertices ({@code /awaken terrain waving probe}, a
 * measuring aid; SPEC 15). Each frame it decodes the corner texels just written and evaluates {@link SwayMath}, the
 * shader's mirror, for fixed points by the camera: three short grass tips, a tall grass top, the end of a hanging
 * strand and a leaf. Beside each it logs the same point on the old clock (raw game time, which rewinds whenever the
 * server's time sync pulls it back). Writes {@code sway-probe-<mode>.csv} in the game folder; {@code frames} also grabs
 * every frame as {@code screenshots/sway_frame_NN.png} with the frame rate held at 60.
 */
final class SwayProbe {
    private SwayProbe() {}

    private enum Mode { FRAMES, SECONDS, TURN }

    private static final String[] NAMES = {"grass_a", "grass_b", "grass_c", "tall_top", "strand_end", "leaf"};
    private static final int[] CODES = {code(PlantSway.MODE_ROOTED, 3), code(PlantSway.MODE_ROOTED, 3), code(PlantSway.MODE_ROOTED, 3),
            code(PlantSway.MODE_ROOTED, 8), code(PlantSway.MODE_HANGING, 7), -1};
    private static final ByteBuffer OLD = ByteBuffer.allocate(SwayMath.TEXELS * 4).order(ByteOrder.LITTLE_ENDIAN);
    private static final SwayMath.Frame NOW = new SwayMath.Frame(), WAS = new SwayMath.Frame();
    private static final Wind.Field RAW = new Wind.Field();
    private static final float[] OUT = new float[4];

    private static Mode mode;
    private static PrintWriter out;
    private static int frame, frames, savedFps = -1;
    private static long startNanos, endNanos, beginNanos;
    private static final float[][] POINTS = new float[NAMES.length][3]; // world x (wrapped), y, z (wrapped)
    private static final double[][] TRUE_XZ = new double[NAMES.length][2];

    static void frames(int n) {
        start(Mode.FRAMES, n, 0);
    }

    static void seconds(int s) {
        start(Mode.SECONDS, 0, s);
    }

    /** 60 s around the next heading turn: 20 s before it, the 20 s turn, 20 s after (starts now if a turn is close). */
    static void turn() {
        start(Mode.TURN, 0, 60);
    }

    static String status() {
        return mode == null ? "probe idle" : "probe " + mode + " frame " + frame;
    }

    private static void start(Mode m, int n, int seconds) {
        Minecraft mc = Minecraft.getInstance();
        Wind.Field wind = ClientWind.field();
        if (wind == null || mc.player == null) return;
        stop();
        Path file = mc.gameDirectory.toPath().resolve("sway-probe-" + m.name().toLowerCase(Locale.ROOT) + ".csv");
        try {
            out = new PrintWriter(Files.newBufferedWriter(file));
        } catch (IOException e) {
            Awaken.LOG.warn("Sway probe: cannot write {}", file, e);
            return;
        }
        // fixed points ahead of the camera, on the ground the player stands on
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double yaw = Math.toRadians(mc.gameRenderer.mainCamera().yRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx;
        double ground = Math.floor(mc.player.getY());
        double[][] at = {{4, -1.5, 1}, {6, 1.0, 1}, {9, 2.5, 1}, {5, 0.2, 2}, {7, -3.0, 3}, {8, 0.0, 6}}; // ahead, right, height
        for (int i = 0; i < NAMES.length; i++) {
            double x = Math.floor(cam.x + fx * at[i][0] + rx * at[i][1]) + 0.854, z = Math.floor(cam.z + fz * at[i][0] + rz * at[i][1]) + 0.854;
            TRUE_XZ[i][0] = x;
            TRUE_XZ[i][1] = z;
            POINTS[i][0] = (float) SwayMath.wrap4096(x);
            POINTS[i][1] = (float) (ground + at[i][2]);
            POINTS[i][2] = (float) SwayMath.wrap4096(z);
        }
        StringBuilder head = new StringBuilder("frame,ms,anim_t,wind_s,raw_s,dir_deg,steady,gust_a");
        for (String n2 : NAMES) head.append(',').append(n2).append("_x,").append(n2).append("_y,").append(n2).append("_z");
        for (String n2 : NAMES) head.append(',').append(n2).append("_old_x,").append(n2).append("_old_y,").append(n2).append("_old_z");
        out.println(head);
        mode = m;
        frame = 0;
        frames = n;
        long now = System.nanoTime();
        beginNanos = now;
        startNanos = now;
        if (m == Mode.TURN) {
            // turns run over ticks 1600..2000 of each 2000-tick heading segment, on the wind's own (smooth) clock
            double ticks = wind.seconds * 20.0;
            double into = ticks - Math.floor(ticks / Wind.SEGMENT_TICKS) * Wind.SEGMENT_TICKS;
            double toTurn = (Wind.SEGMENT_TICKS - Wind.TURN_TICKS - into) / 20.0;
            if (toTurn < 0) toTurn += Wind.SEGMENT_TICKS / 20.0;
            if (toTurn > 20) startNanos = now + (long) ((toTurn - 20) * 1e9);
            Awaken.LOG.info("Sway probe: the next heading turn begins in {} s; logging from {} s", String.format(Locale.ROOT, "%.1f", toTurn),
                    String.format(Locale.ROOT, "%.1f", Math.max(0, toTurn - 20)));
        }
        endNanos = seconds > 0 ? startNanos + seconds * 1_000_000_000L : Long.MAX_VALUE;
        if (m == Mode.FRAMES) {
            savedFps = mc.options.framerateLimit().get();
            mc.options.framerateLimit().set(60);
        }
        Awaken.LOG.info("Sway probe: {} started, writing {}", m, file);
    }

    /** Called right after the corner texels are written (render thread, before the level draws). */
    static void frame(ByteBuffer written, Vec3 cam) {
        if (mode == null) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (now < startNanos) return;
        // the screen now holds the frame logged last time: grab it under that frame's number
        if (mode == Mode.FRAMES && frame > 0) {
            String name = String.format(Locale.ROOT, "sway_frame_%02d.png", frame - 1);
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 2, msg -> {});
        }
        if ((mode == Mode.FRAMES && frame >= frames) || now >= endNanos || mc.level == null) {
            stop();
            return;
        }
        SwayMath.decode(written, NOW);
        // the same frame on the old clock: raw game time for the sway and for the wind
        RAW.set(mc.level, ClientWind.rawPartial);
        copyWith(NOW, WAS, (float) (ClientWind.rawSeconds % 2048.0), cam);
        StringBuilder row = new StringBuilder();
        Wind.Field wind = ClientWind.field();
        row.append(frame).append(',').append(f3((now - startNanos) / 1e6)).append(',').append(f4(NOW.t)).append(',')
                .append(f4(wind == null ? 0 : wind.seconds)).append(',').append(f4(ClientWind.rawSeconds)).append(',')
                .append(f3(Math.toDegrees(Math.atan2(NOW.dirZ, NOW.dirX)))).append(',').append(f4(NOW.steady)).append(',')
                .append(f4(SwayMath.gustAt(NOW, POINTS[0][0], POINTS[0][2])));
        appendOffsets(row, NOW, cam);
        appendOffsets(row, WAS, cam);
        out.println(row);
        frame++;
    }

    private static void appendOffsets(StringBuilder row, SwayMath.Frame f, Vec3 cam) {
        for (int i = 0; i < NAMES.length; i++) {
            float x = POINTS[i][0], y = POINTS[i][1], z = POINTS[i][2];
            if (CODES[i] < 0) SwayMath.leafOffset(f, x, y, z, OUT);
            else {
                double dx = TRUE_XZ[i][0] - cam.x, dy = y - cam.y, dz = TRUE_XZ[i][1] - cam.z;
                SwayMath.plantOffset(f, CODES[i], x, y, z, (float) Math.sqrt(dx * dx + dy * dy + dz * dz), OUT);
            }
            row.append(',').append(f5(OUT[0])).append(',').append(f5(OUT[1])).append(',').append(f5(OUT[2]));
        }
    }

    /** The frame on the old clock: raw game time for the sway, and gusts from a wind field set to raw game time. */
    private static void copyWith(SwayMath.Frame from, SwayMath.Frame to, float t, Vec3 cam) {
        OLD.clear();
        SwayMath.encode(from, OLD);
        SwayMath.decode(OLD, to);
        to.t = t;
        to.dirX = RAW.dirX;
        to.dirZ = RAW.dirZ;
        to.steady = RAW.steady;
        to.profileOff = SwayMath.profileOffset(cam.x, cam.z, RAW.dirX, RAW.dirZ);
        for (int i = 0; i < SwayMath.PROFILE; i++) {
            float along = SwayMath.sampleAlong(i, to.profileOff);
            to.gust[i] = RAW.gust(cam.x + RAW.dirX * along, cam.z + RAW.dirZ * along);
        }
    }

    static void stop() {
        if (mode == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (savedFps >= 0) {
            mc.options.framerateLimit().set(savedFps);
            savedFps = -1;
        }
        out.close();
        Awaken.LOG.info("Sway probe: {} done, {} frames in {} s", mode, frame, String.format(Locale.ROOT, "%.2f", (System.nanoTime() - beginNanos) / 1e9));
        mode = null;
        out = null;
    }

    private static int code(int mode, int level) {
        return mode * PlantSway.LEVELS + level - 1;
    }

    private static String f3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String f4(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static String f5(double v) {
        return String.format(Locale.ROOT, "%.5f", v);
    }
}
