package dev.awaken.signal.client;

import dev.awaken.core.api.Destinations;
import dev.awaken.core.api.NowHooks;
import dev.awaken.signal.NowCorner;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The client's copy of the Now tracker's suggestions (the band's lists and "Anything for me?" read them), the compass
 * that points at them, and the one place the corner shows (SPEC §27.11): the destination you chose, else the current
 * task step's place.
 */
public final class NowClient {
    private NowClient() {}

    private static SignalPayloads.Now current = new SignalPayloads.Now(Identifier.withDefaultNamespace("overworld"), List.of());
    private static int ticks;
    private static int version;

    static void accept(SignalPayloads.Now now) {
        version++;
        current = now;
    }

    // ---- where you're going (SPEC §27.11) -----------------------------------------------------------------------------

    private static NowCorner.@Nullable Place heading;
    /** What the heading was worked out from (the same objects: nothing changed). */
    private static Destinations.@Nullable Target fromTarget;
    private static SignalPayloads.@Nullable TaskRow fromRow;
    private static int headingVersion, headingAt;
    /** A step's place that moves (a person walking) less than this is the same place: no fade, no new thread. */
    static final int SAME_PLACE = 24;

    /** Where you're going, if anywhere: the one place the corner shows ({@link NowCorner}). */
    public static Optional<NowCorner.Place> heading() {
        return Optional.ofNullable(heading);
    }

    /** The same, for the corner's frame (no wrapper). */
    static NowCorner.@Nullable Place current() {
        return heading;
    }

    private static void updateHeading() {
        Destinations.Target d = Destinations.mine().orElse(null);
        SignalPayloads.TaskRow row = d != null ? null : TaskHud.place();
        if (d == fromTarget && row == fromRow) return;
        fromTarget = d;
        fromRow = row;
        NowCorner.Place next = NowCorner.of(d, TaskHud.task());
        if (java.util.Objects.equals(next, heading)) return;
        boolean same = next != null && heading != null && next.destination() == heading.destination() && next.name().equals(heading.name())
                && next.at().distSqr(heading.at()) < SAME_PLACE * SAME_PLACE;
        heading = next;
        headingVersion++;
        if (same) return;
        headingAt = ticks;
        if (next != null) dev.awaken.core.audio.client.AudioUi.nowChanged(next.destination() ? 86 : 68);
    }

    /** Changes whenever the corner's place changes (the tracker rebuilds its text then). */
    static int headingVersion() {
        return headingVersion;
    }

    /** Ticks since the corner's place last changed (a new one fades in). */
    static int sinceHeading() {
        return ticks - headingAt;
    }

    /** Changes whenever new suggestions arrive. */
    static int version() {
        return version;
    }

    static void tick() {
        ticks++;
        updateHeading();
        thread();
    }

    // ---- the Signal's thread (SPEC §27.7) -----------------------------------------------------------------------------

    /** How far the thread runs along the ground, how fast (blocks a tick), and how often at most. */
    static final int THREAD_LEN = 26, THREAD_EVERY = 2400, IDLE = 160;
    private static int threadFor = -1, lastThread = -100_000, still, runAt = -1;
    private static double lastX, lastZ, runX, runY, runZ, runDx, runDz;

    /**
     * Toward where you're going (the corner's place), a thread of the Signal's light runs low along the ground for a few
     * seconds: once when that place is new, and once more if the player stands still outdoors for 8 s with it. At most
     * every two minutes (a destination you just chose runs at once), never in a screen. The guiding wind, in our blue:
     * nothing stays on screen.
     */
    private static void thread() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null) return;
        if (runAt >= 0) {
            int t = ticks - runAt;
            if (t > THREAD_LEN) runAt = -1;
            else if (t >= 2) {
                for (int k = 0; k < 2; k++) {
                    double d = t + k * 0.5;
                    double x = runX + runDx * d, z = runZ + runDz * d;
                    int top = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                    double y = Math.abs(top - runY) < 6 ? top + 0.15 : runY + 0.15;
                    dev.awaken.body.ascent.client.AscentFxBridge.threadMote(x, y, z, runDx, runDz);
                }
            }
        }
        boolean moved = (p.getX() - lastX) * (p.getX() - lastX) + (p.getZ() - lastZ) * (p.getZ() - lastZ) > 0.0025;
        lastX = p.getX();
        lastZ = p.getZ();
        still = moved ? 0 : still + 1;
        NowCorner.Place h = heading;
        if (h == null || mc.gui.screen() != null || !p.level().dimension().identifier().equals(h.dimension())) return;
        BlockPos at = h.at();
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 12) return;
        // a new place runs its thread once things settle (a destination the first moment after the map has closed),
        // a destination even if a thread ran lately; after that, only standing still with it, every two minutes at most
        boolean fresh = threadFor != headingAt && sinceHeading() >= 30;
        if (fresh ? !h.destination() && ticks - lastThread < THREAD_EVERY : still != IDLE || ticks - lastThread < THREAD_EVERY) return;
        threadFor = headingAt;
        lastThread = runAt = ticks;
        runX = p.getX();
        runY = p.getY();
        runZ = p.getZ();
        runDx = dx / dist;
        runDz = dz / dist;
    }

    /** What the Now tracker offers (the band's lists; the corner shows only {@link #heading}). */
    public static List<NowHooks.Suggestion> suggestions() {
        return current.suggestions();
    }

    /** " ↗ 40 m" for a suggestion with a place in the player's dimension; empty otherwise. */
    public static Optional<String> compass(NowHooks.Suggestion s) {
        if (s.where().isEmpty() || ownsWay(s.id())) return Optional.empty();
        return compass(s.where().get(), current.dimension());
    }

    /** Ids (prefixes) of suggestions that show their own way: no straight thread, no compass after their text. */
    private static volatile String[] ownWay = new String[0];

    /**
     * A suggestion whose text already says the way and whose module draws its own thread (the way home underground,
     * SPEC §28.5: "Surface ↑ 34 · NE 60", its thread along the path you came by): the straight thread and the compass
     * leave it alone.
     */
    public static synchronized void ownWay(String idPrefix) {
        String[] next = java.util.Arrays.copyOf(ownWay, ownWay.length + 1);
        next[ownWay.length] = idPrefix;
        ownWay = next;
    }

    static boolean ownsWay(String id) {
        for (String p : ownWay) if (id.startsWith(p)) return true;
        return false;
    }

    /** " ↗ 40 m" toward {@code at}, when the player stands in {@code dimension} (the Horizon line uses it too). */
    static Optional<String> compass(BlockPos at, Identifier dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.level().dimension().identifier().equals(dimension)) return Optional.empty();
        // height counts: a place over your head is never "here" (SPEC §27.1)
        return Optional.of(dev.awaken.signal.NowWay.arrow(at.getX() + 0.5 - mc.player.getX(), at.getY() - mc.player.getY(),
                at.getZ() + 0.5 - mc.player.getZ(), mc.player.getYRot()));
    }

    static void clear() {
        version++;
        current = new SignalPayloads.Now(current.dimension(), List.of());
        Destinations.accept(null);
        heading = null;
        fromTarget = null;
        fromRow = null;
        headingVersion++;
    }
}
