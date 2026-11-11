package dev.awaken.arms.client;

import dev.awaken.Awaken;
import dev.awaken.arms.Archetype;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Development only: what the hands drew, every frame, over the last minute or so of real play, written to
 * logs/arms-blackbox.tsv on leaving the world. A "sometimes" in the fighting (Euan 2026-11-09) needs a person's clicks at
 * the real frame rate against a real server, which no tour has. Rows are F (a frame) or E (what {@link ArmsFilm#event} saw).
 */
final class ArmsBlackBox {
    private ArmsBlackBox() {}

    static final int UNDECIDED = 1, DRAWN = 2, CHARGING = 4, WAITING = 8, KEY = 16, HAS = 32, BLENDING = 64;

    private static final boolean ON = !FMLEnvironment.isProduction();

    /** Whether this is a development run (the black box and the ghost report are kept). */
    static boolean on() {
        return ON;
    }
    private static final int FRAMES = 1 << 14, EVENTS = 1 << 12;
    /** How many black boxes are kept under their own time. */
    private static final int KEPT = 5;
    /** A frame's numbers: the camera's lean, the clip's main hand, then each hand as shown. */
    private static final int COLS = 3 + 7 * 3;
    private static final long[] FRAME_AT = new long[ON ? FRAMES : 0], EVENT_AT = new long[ON ? EVENTS : 0];
    private static final float[] DATA = new float[ON ? FRAMES * COLS : 0], CLOCK = new float[ON ? FRAMES : 0];
    private static final byte[] MOVE = new byte[ON ? FRAMES : 0], FLAGS = new byte[ON ? FRAMES : 0];
    private static final String[] EVENT = new String[ON ? EVENTS : 0];
    private static long frames, events;
    private static float leanYaw, leanPitch, leanRoll;

    /** The camera's lean this frame (it is worked out before the hands are). */
    static void lean(float yaw, float pitch, float roll) {
        leanYaw = yaw;
        leanPitch = pitch;
        leanRoll = roll;
    }

    static void event(String what) {
        if (!ON) return;
        int i = (int) (events++ & (EVENTS - 1));
        EVENT_AT[i] = System.nanoTime();
        EVENT[i] = what;
    }

    static void frame(Archetype.@Nullable Move m, float t, int flags, Vector3f clipD, Quaternionf clipQ, Vector3f mainD, Quaternionf mainQ, Vector3f offD, Quaternionf offQ) {
        if (!ON) return;
        int i = (int) (frames++ & (FRAMES - 1));
        FRAME_AT[i] = System.nanoTime();
        MOVE[i] = (byte) (m == null ? -1 : m.ordinal());
        FLAGS[i] = (byte) flags;
        CLOCK[i] = t;
        int c = i * COLS;
        DATA[c++] = leanYaw;
        DATA[c++] = leanPitch;
        DATA[c++] = leanRoll;
        c = put(c, clipD, clipQ);
        c = put(c, mainD, mainQ);
        put(c, offD, offQ);
    }

    private static int put(int c, Vector3f d, Quaternionf q) {
        DATA[c++] = d.x;
        DATA[c++] = d.y;
        DATA[c++] = d.z;
        DATA[c++] = q.x;
        DATA[c++] = q.y;
        DATA[c++] = q.z;
        DATA[c++] = q.w;
        return c;
    }

    /** Written on leaving the world, oldest first, frames and events in time order. */
    static void dump() {
        if (!ON || frames == 0) return;
        int count = (int) Math.min(frames, FRAMES), eventCount = (int) Math.min(events, EVENTS);
        int first = (int) ((frames - count) & (FRAMES - 1)), firstEvent = (int) ((events - eventCount) & (EVENTS - 1));
        long origin = FRAME_AT[first];
        StringBuilder out = new StringBuilder(count * 200);
        out.append("kind\tms\tmove\tclock\tflags\tlean_yaw\tlean_pitch\tlean_roll");
        for (String who : new String[] {"clip", "main", "off"}) for (String c : new String[] {"x", "y", "z", "qx", "qy", "qz", "qw"}) out.append('\t').append(who).append('_').append(c);
        out.append('\n');
        int e = 0;
        for (int n = 0; n < count; n++) {
            int i = (first + n) & (FRAMES - 1);
            for (; e < eventCount && EVENT_AT[(firstEvent + e) & (EVENTS - 1)] <= FRAME_AT[i]; e++) {
                int k = (firstEvent + e) & (EVENTS - 1);
                if (EVENT_AT[k] >= origin) out.append(String.format(Locale.ROOT, "E\t%.2f\t%s%n", (EVENT_AT[k] - origin) / 1e6, EVENT[k]));
            }
            var m = Archetype.Move.byId(MOVE[i]);
            out.append(String.format(Locale.ROOT, "F\t%.2f\t%s\t%.3f\t%d", (FRAME_AT[i] - origin) / 1e6, m == null ? "-" : m.key, CLOCK[i], FLAGS[i]));
            for (int c = 0; c < COLS; c++) out.append(String.format(Locale.ROOT, "\t%.4f", DATA[i * COLS + c]));
            out.append('\n');
        }
        try {
            File dir = new File(Minecraft.getInstance().gameDirectory, "logs");
            dir.mkdirs();
            Files.writeString(new File(dir, "arms-blackbox.tsv").toPath(), out, StandardCharsets.UTF_8);
            // and under its own time, the last KEPT of them: the plain file is overwritten by the next world left, which
            // lost a boss fight's record six minutes after it (SPEC §68.5)
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.writeString(new File(dir, "arms-blackbox-" + stamp + ".tsv").toPath(), out, StandardCharsets.UTF_8);
            File[] kept = dir.listFiles((d, name) -> name.startsWith("arms-blackbox-") && name.endsWith(".tsv"));
            if (kept != null && kept.length > KEPT) {
                java.util.Arrays.sort(kept, java.util.Comparator.comparing(File::getName));
                for (int i = 0; i < kept.length - KEPT; i++) Files.deleteIfExists(kept[i].toPath());
            }
            Awaken.LOG.info("[arms] black box: the last {} frames written (also as arms-blackbox-{}.tsv)", count, stamp);
        } catch (IOException ex) {
            Awaken.LOG.warn("[arms] black box not saved: {}", ex.toString());
        }
        frames = events = 0;
    }
}
