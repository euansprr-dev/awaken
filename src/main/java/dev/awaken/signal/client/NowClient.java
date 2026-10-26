package dev.awaken.signal.client;

import dev.awaken.core.api.NowHooks;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/** The client's copy of the Now tracker's suggestions, and the compass that points at them. */
public final class NowClient {
    private NowClient() {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static SignalPayloads.Now current = new SignalPayloads.Now(net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), List.of());
    private static String topId = "";
    private static int changedAt;
    private static int ticks;
    private static int version;

    static void accept(SignalPayloads.Now now) {
        version++;
        current = now;
        joined = null;
        String id = top().map(NowHooks.Suggestion::id).orElse("");
        if (!id.equals(topId)) {
            topId = id;
            changedAt = ticks;
            dev.awaken.core.audio.client.AudioUi.nowChanged(top().map(NowHooks.Suggestion::priority).orElse(0));
        }
    }

    // ---- a place the map points you at (SPEC §30.5) ------------------------------------------------------------------

    /** The map's "Point me there": it leads the Now line until you're within {@link #PIN_REACHED} blocks of it. */
    public static final String PIN_ID = "awaken:map_pin";
    static final int PIN_PRIORITY = 86, PIN_REACHED = 12;
    private static NowHooks.Suggestion pin;
    private static net.minecraft.resources.Identifier pinDimension;
    private static List<NowHooks.Suggestion> joined;

    /** Points the Now line (and its compass and thread) at {@code at} in the player's dimension, until reached. */
    public static void pin(String text, BlockPos at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        pin = NowHooks.Suggestion.at(PIN_ID, PIN_PRIORITY, text, at.immutable());
        pinDimension = mc.player.level().dimension().identifier();
        joined = null;
        version++;
        topId = PIN_ID;
        changedAt = ticks;
        threadFor = ""; // a new place: its thread may run
        lastThread = -100_000;
        dev.awaken.core.audio.client.AudioUi.nowChanged(PIN_PRIORITY);
    }

    public static void unpin() {
        if (pin == null) return;
        pin = null;
        joined = null;
        version++;
        topId = current.top().map(NowHooks.Suggestion::id).orElse("");
    }

    /** Where the map's pin is, if one is set. */
    public static Optional<BlockPos> pinned() {
        return pin == null ? Optional.empty() : pin.where();
    }

    private static Optional<NowHooks.Suggestion> top() {
        return pin != null ? Optional.of(pin) : current.top();
    }

    /** Reached (within 12 blocks, in its dimension): the pin has done its job. */
    private static void checkPin() {
        Minecraft mc = Minecraft.getInstance();
        if (pin == null || mc.player == null || pin.where().isEmpty()) return;
        if (!mc.player.level().dimension().identifier().equals(pinDimension)) return;
        BlockPos at = pin.where().get();
        double dx = at.getX() + 0.5 - mc.player.getX(), dz = at.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dz * dz < PIN_REACHED * PIN_REACHED) unpin();
    }

    /** Changes whenever new suggestions arrive (the tracker rebuilds its text then). */
    static int version() {
        return version;
    }

    static void tick() {
        ticks++;
        if (ticks % 10 == 0) checkPin();
        thread();
    }

    // ---- the Signal's thread (SPEC §27.7) -----------------------------------------------------------------------------

    /** How far the thread runs along the ground, how fast (blocks a tick), and how often at most. */
    static final int THREAD_LEN = 26, THREAD_EVERY = 2400, IDLE = 160;
    private static String threadFor = "";
    private static int lastThread = -100_000, still, runAt = -1;
    private static double lastX, lastZ, runX, runY, runZ, runDx, runDz;

    /**
     * Toward the top suggestion's place, a thread of the Signal's light runs low along the ground for a few seconds: once
     * when that place is new, and once more if the player stands still outdoors for 8 s with it. At most every two
     * minutes, never in a screen. The guiding wind, in our blue: nothing stays on screen.
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
        var top = top();
        var dim = pin != null ? pinDimension : current.dimension();
        if (top.isEmpty() || top.get().where().isEmpty() || mc.gui.screen() != null || !p.level().dimension().identifier().equals(dim)) return;
        BlockPos at = top.get().where().get();
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 12 || ticks - lastThread < THREAD_EVERY) return;
        // the map's pin runs its thread the first moment after the map has closed
        boolean fresh = !topId.equals(threadFor) && (sinceChange() == 30 || topId.equals(PIN_ID) && sinceChange() >= 30);
        if (!fresh && still != IDLE) return;
        threadFor = topId;
        lastThread = runAt = ticks;
        runX = p.getX();
        runY = p.getY();
        runZ = p.getZ();
        runDx = dx / dist;
        runDz = dz / dist;
    }

    /** Ticks since the top suggestion last changed (the tracker fades a new one in). */
    static int sinceChange() {
        return ticks - changedAt;
    }

    /** What the Now line offers, the map's pin first when one is set. */
    public static List<NowHooks.Suggestion> suggestions() {
        if (pin == null) return current.suggestions();
        if (joined == null) {
            var all = new java.util.ArrayList<NowHooks.Suggestion>(current.suggestions().size() + 1);
            all.add(pin);
            all.addAll(current.suggestions());
            joined = List.copyOf(all);
        }
        return joined;
    }

    /** " ↗ 40 m" for a suggestion with a place in the player's dimension; empty otherwise. */
    public static Optional<String> compass(NowHooks.Suggestion s) {
        if (s.where().isEmpty()) return Optional.empty();
        return compass(s.where().get(), s == pin ? pinDimension : current.dimension());
    }

    /** " ↗ 40 m" toward {@code at}, when the player stands in {@code dimension} (the Horizon line uses it too). */
    static Optional<String> compass(BlockPos at, net.minecraft.resources.Identifier dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.level().dimension().identifier().equals(dimension)) return Optional.empty();
        double dx = at.getX() + 0.5 - mc.player.getX(), dz = at.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 4) return Optional.of("here");
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return Optional.of(ARROWS[idx] + " " + Math.round(dist) + " m");
    }

    static void clear() {
        version++;
        current = new SignalPayloads.Now(current.dimension(), List.of());
        topId = "";
        pin = null;
        joined = null;
    }
}
