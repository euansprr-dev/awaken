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
        String id = now.top().map(NowHooks.Suggestion::id).orElse("");
        if (!id.equals(topId)) {
            topId = id;
            changedAt = ticks;
            dev.awaken.core.audio.client.AudioUi.nowChanged(now.top().map(NowHooks.Suggestion::priority).orElse(0));
        }
        current = now;
    }

    /** Changes whenever new suggestions arrive (the tracker rebuilds its text then). */
    static int version() {
        return version;
    }

    static void tick() {
        ticks++;
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
        var top = current.top();
        if (top.isEmpty() || top.get().where().isEmpty() || mc.gui.screen() != null || !p.level().dimension().identifier().equals(current.dimension())) return;
        BlockPos at = top.get().where().get();
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 12 || ticks - lastThread < THREAD_EVERY) return;
        boolean fresh = !topId.equals(threadFor) && sinceChange() == 30;
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

    public static List<NowHooks.Suggestion> suggestions() {
        return current.suggestions();
    }

    /** " ↗ 40 m" for a suggestion with a place in the player's dimension; empty otherwise. */
    public static Optional<String> compass(NowHooks.Suggestion s) {
        return s.where().isEmpty() ? Optional.empty() : compass(s.where().get(), current.dimension());
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
    }
}
