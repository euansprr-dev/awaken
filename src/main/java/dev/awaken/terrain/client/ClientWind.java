package dev.awaken.terrain.client;

import dev.awaken.terrain.SwayClock;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * This client's view of {@link Wind}: one field, set once a frame (before anything draws), read by the waving shader's
 * texels, the falling leaves and the leaf rustle so all three move with the same gusts. The field follows a
 * {@link SwayClock}, never the raw game time: the server's time syncs pull the client's clock back whenever the
 * integrated server lags, and the wind must not rewind with it. The sway's own to-and-fro runs on {@link #animSeconds},
 * real frame time alone, so a slow server never slows or jerks it.
 */
public final class ClientWind {
    private ClientWind() {}

    private static final Wind.Field FIELD = new Wind.Field();
    private static final SwayClock CLOCK = new SwayClock();
    private static boolean ready;
    private static ClientLevel clockLevel;
    private static long lastNanos;
    private static double anim;
    /** The game's own clock this frame in seconds (what the wind followed before the smooth clock), for the probe. */
    static double rawSeconds;
    static float rawPartial;
    /** Debug ({@code /awaken terrain wind force}): a fixed steady strength (-1 = real) and a floor under every gust. */
    static float forceSteady = -1F, forceGust;
    /** Showcase ({@code /awaken terrain wind front}): a fixed heading (NaN = real) and one front launched at a time. */
    static float forceDirX = Float.NaN, forceDirZ;
    static double frontAt = Double.NaN, frontX, frontZ;

    static void update(Minecraft mc, float partialTick) {
        long now = System.nanoTime();
        double frame = lastNanos == 0 ? 0.0 : (now - lastNanos) * 1e-9;
        lastNanos = now;
        if (mc.level == null) {
            ready = false;
            return;
        }
        if (mc.level != clockLevel) {
            CLOCK.reset();
            clockLevel = mc.level;
        }
        boolean paused = mc.isPaused();
        rawPartial = partialTick;
        rawSeconds = (mc.level.getGameTime() + (double) partialTick) / 20.0;
        double seconds = CLOCK.advance(rawSeconds, frame, paused);
        if (!paused) anim = (anim + Math.clamp(frame, 0.0, SwayClock.MAX_FRAME)) % 2048.0;
        FIELD.setTicks(Wind.salt(mc.level), seconds * 20.0, mc.level.getRainLevel(partialTick), mc.level.getThunderLevel(partialTick));
        if (forceSteady >= 0F) FIELD.steady = forceSteady;
        FIELD.gustFloor = forceGust;
        if (!Float.isNaN(forceDirX)) {
            FIELD.dirX = forceDirX;
            FIELD.dirZ = forceDirZ;
        }
        FIELD.frontAt = frontAt;
        FIELD.frontX = frontX;
        FIELD.frontZ = frontZ;
        FIELD.frontDirX = Float.isNaN(forceDirX) ? 0F : forceDirX;
        FIELD.frontDirZ = Float.isNaN(forceDirX) ? 0F : forceDirZ;
        ready = true;
    }

    /**
     * Launches a showcase front from {@code back} blocks behind (x, z) along (dirX, dirZ), and holds the wind's heading
     * there so everything leans the way the front rolls ({@code wind real} lets go).
     */
    static void front(double x, double z, float dirX, float dirZ, double back) {
        forceDirX = dirX;
        forceDirZ = dirZ;
        frontX = x - dirX * back;
        frontZ = z - dirZ * back;
        frontAt = FIELD.seconds;
    }

    static void real() {
        forceSteady = -1F;
        forceGust = 0F;
        forceDirX = Float.NaN;
        frontAt = Double.NaN;
    }

    /** Seconds of unpaused real time modulo 2048: the clock of every sway's to-and-fro (whole turns in 2048 s, so the
     * wrap is seamless). */
    static double animSeconds() {
        return anim;
    }

    /** The wind this frame, or null outside a world. Read-only for callers. */
    public static Wind.Field field() {
        return ready ? FIELD : null;
    }
}
