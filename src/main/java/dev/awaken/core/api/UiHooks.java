package dev.awaken.core.api;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * What the interface tells the rest of the client. {@link #focus()} is how much an open panel holds the player's
 * attention, 0..1: about 1 while the K menu, the first-power choice or another reading screen is fully open, eased
 * through their open and close. Lens reads it to dim the world behind a panel. Several sources may report at once
 * ({@link #setFocus}, one slot per owner); focus is the largest. Toasts, cards and HUD lines never report. Client
 * only; 0 on a server or when nothing reports. Also: which HUD layers draw over screens ({@link #drawsOverScreens}),
 * and the top-right stack of HUD cards ({@link TopRight}).
 */
public final class UiHooks {
    private UiHooks() {}

    private static final int MAX_SOURCES = 8;
    private static final Object[] OWNERS = new Object[MAX_SOURCES];
    private static final float[] VALUES = new float[MAX_SOURCES];
    private static DoubleSupplier legacy = () -> 0;

    /**
     * {@code owner}'s focus, 0..1 (kept until changed or cleared). Owners are compared by identity; a slot is taken the
     * first time an owner reports a value above 0, so steady per-frame reporting allocates nothing. At most 8 owners.
     */
    public static void setFocus(Object owner, float value) {
        int free = -1;
        for (int i = 0; i < MAX_SOURCES; i++) {
            if (OWNERS[i] == owner) {
                VALUES[i] = value;
                return;
            }
            if (OWNERS[i] == null && free < 0) free = i;
        }
        if (free >= 0 && value > 0) {
            OWNERS[free] = owner;
            VALUES[free] = value;
        }
    }

    /** {@code owner} stops reporting. */
    public static void clearFocus(Object owner) {
        for (int i = 0; i < MAX_SOURCES; i++) {
            if (OWNERS[i] == owner) {
                OWNERS[i] = null;
                VALUES[i] = 0;
            }
        }
    }

    /** One pulled source (the first form of this API), counted beside the reported ones. */
    public static void installFocus(DoubleSupplier source) {
        legacy = source;
    }

    /** The largest focus any source reports, 0..1. */
    public static float focus() {
        double f = legacy.getAsDouble();
        for (int i = 0; i < MAX_SOURCES; i++) if (OWNERS[i] != null && VALUES[i] > f) f = VALUES[i];
        return f <= 0 ? 0f : f >= 1 ? 1f : (float) f;
    }

    // ---- the HUD under screens ------------------------------------------------------------------------------------

    private static volatile Set<Identifier> overScreens = Set.of();

    /**
     * Awaken's HUD layers stand aside while any screen but chat is open (core.client.HudUnderScreens); a layer that
     * belongs to a scene drawn over its own screen (the opening's letterbox over the view choice) says so here, once,
     * when it registers.
     */
    public static synchronized void drawsOverScreens(Identifier layer) {
        Set<Identifier> next = new HashSet<>(overScreens);
        next.add(layer);
        overScreens = Set.copyOf(next);
    }

    public static boolean drawsOverScreen(Identifier layer) {
        return overScreens.contains(layer);
    }

    // ---- the top-right stack (SPEC §27.10) ------------------------------------------------------------------------

    /** The stack's margin from the screen's top and right edges, GUI px (the Now card's). */
    public static final int TOP_RIGHT_MARGIN = 4;

    /** The top-right stack: the task card first (the signal module installs it), then the cards under it. */
    public interface TopRight {
        /**
         * Where a card {@code width} GUI px wide, set against the right margin, starts this frame: under the System's
         * toasts, the task card and any boss bar it would reach.
         */
        float below(float partialTick, float width);

        /** GUI px per card px for the stack's cards: half the Now card's, never under two screen pixels a font pixel. */
        float scale();
    }

    private static @Nullable TopRight topRight;

    public static void installTopRight(TopRight source) {
        topRight = source;
    }

    /** {@link TopRight#below}; the bare margin when nothing is installed. */
    public static float topRightBelow(float partialTick, float width) {
        TopRight t = topRight;
        return t == null ? TOP_RIGHT_MARGIN : t.below(partialTick, width);
    }

    /** {@link TopRight#scale}; 0.5 when nothing is installed. */
    public static float topRightScale() {
        TopRight t = topRight;
        return t == null ? 0.5f : t.scale();
    }
}
