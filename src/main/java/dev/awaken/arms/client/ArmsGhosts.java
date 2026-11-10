package dev.awaken.arms.client;

import dev.awaken.Awaken;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.HitWhy;
import dev.awaken.core.api.Aim;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Development only: the ghost report (SPEC §25 "Weight in the Hand", stage 0). A ghost is a hit the hands showed (its
 * sound, sparks and freeze) that the server never confirmed; Euan's logs of 4 to 9 Nov held 99 in 334 shown hits and no
 * cause. Each blow played is noted with what the game saw; when the ledger counts one as never confirmed, the server's own
 * record of that press and body ({@link HitWhy}) gives the cause, logged as an {@code [arms] ghost} line and an E row of
 * the black box, and counted by cause for the line written on leaving the world. Nothing here changes what is shown.
 */
final class ArmsGhosts {
    private ArmsGhosts() {}

    private static final int SIZE = 64;
    private static final int[] SEQ = new int[SIZE], BODY = new int[SIZE];
    private static final long[] NANOS = new long[SIZE];
    private static final float[] GAP = new float[SIZE], REACH = new float[SIZE];
    private static final String[] WHAT = new String[SIZE];
    private static final int[] COUNT = new int[HitWhy.Cause.values().length];
    private static final StringBuilder SAY = new StringBuilder();
    private static int next, shown;
    private static float beyond; // out of reach: how far, summed

    /** A blow the hands play now: what the game saw of the body it struck. */
    static void shown(int seq, Entity body, Archetype.@Nullable Move move, Vec3 eye, double reach) {
        if (!ArmsBlackBox.on()) return;
        int i = next;
        next = (next + 1) % SIZE;
        SEQ[i] = seq;
        BODY[i] = body.getId();
        NANOS[i] = System.nanoTime();
        GAP[i] = (float) Aim.gap(eye, body.getBoundingBox());
        REACH[i] = (float) reach;
        WHAT[i] = (move == null ? "-" : move.key) + " on " + BuiltInRegistries.ENTITY_TYPE.getKey(body.getType());
        shown++;
        ArmsFilm.event(String.format(Locale.ROOT, "shown seq %d body %d %s gap %.2f reach %.2f", seq, BODY[i], WHAT[i], GAP[i], REACH[i]));
    }

    /** The ledger counted a played blow as never confirmed: its cause, from the server's own record. */
    static void ghost(int seq, int body) {
        if (!ArmsBlackBox.on()) return;
        int at = -1;
        for (int i = 0; i < SIZE; i++) if (WHAT[i] != null && SEQ[i] == seq && BODY[i] == body) at = i;
        SAY.setLength(0);
        HitWhy.Why why = HitWhy.explain(seq, body, at < 0 ? System.nanoTime() : NANOS[at], SAY);
        HitWhy.Cause cause = why.cause();
        COUNT[cause.ordinal()]++;
        beyond += why.beyond();
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(body);
        String line = String.format(Locale.ROOT, "seq %d %s: shown at gap %.2f (reach %.2f); %s: %s%s", seq, at < 0 ? "body " + body : WHAT[at],
                at < 0 ? -1F : GAP[at], at < 0 ? -1F : REACH[at], cause.label, SAY, e == null || !e.isAlive() ? "; the body is gone now" : "");
        Awaken.LOG.info("[arms] ghost: {}", line);
        ArmsFilm.event("ghost " + line);
    }

    /** On leaving the world: the ghosts by cause. */
    static void summary() {
        int total = 0;
        for (int c : COUNT) total += c;
        if (ArmsBlackBox.on() && shown > 0) {
            StringBuilder out = new StringBuilder();
            for (HitWhy.Cause c : HitWhy.Cause.values()) {
                if (COUNT[c.ordinal()] == 0) continue;
                out.append(out.length() == 0 ? "" : ", ").append(c.label).append(' ').append(COUNT[c.ordinal()]);
                if (c == HitWhy.Cause.OUT_OF_REACH) out.append(String.format(Locale.ROOT, " (%.2f beyond on average)", beyond / COUNT[c.ordinal()]));
            }
            Awaken.LOG.info("[arms] ghosts by cause, {} of {} blows shown: {}", total, shown, total == 0 ? "none" : out);
        }
        java.util.Arrays.fill(COUNT, 0);
        java.util.Arrays.fill(WHAT, null);
        shown = 0;
        beyond = 0;
        HitWhy.clear();
    }
}
