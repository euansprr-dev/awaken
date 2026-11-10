package dev.awaken.core.client.ui;

import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The band's places (the K menu, SPEC §57): what each of its six rail nodes shows on the stage. A module gives a place for
 * its page ({@link #set}); a page without one shows its old sections in the stage until its module moves. Client only.
 */
public final class BandPlaces {
    private BandPlaces() {}

    /** The stage a place draws on, in sheet pixels: inside the band's glass, under its name, over the bar of keys. */
    public static final float X0 = 192F, Y0 = 144F, X1 = 1640F, Y1 = 856F;
    /** Where the panel that speaks about the thing in focus starts: the stage's right 440. */
    public static final float PANEL_X = X1 - 440F;

    /** One of the band's places. Everything is in sheet pixels; the mouse is off the sheet (-10000) while the band opens. */
    public interface Place {
        /** The band came to this place; {@code focus} names what a link asked for (may be empty). */
        default void opened(String... focus) {}

        /** Draws the stage and adds what can be clicked to {@code hot}. {@code dt}: seconds since the last frame. */
        void draw(Sheet s, Hot hot, float mx, float my, float dt);

        /** What stands at the right of the place's name: a chip of context ("At your fire you can do more"), or null. */
        default @Nullable String chip() {
            return null;
        }

        /** The keys that work here now, left of the shell's own. */
        default void hints(List<Kit.Hint> out) {}

        /** A key the place takes (the shell has Esc, K, Q, E and 1 to 6). */
        default boolean key(KeyEvent event) {
            return false;
        }

        default boolean keyReleased(KeyEvent event) {
            return false;
        }

        /** Tab and Shift+Tab: the next thing on the stage. */
        default void next(int step) {}

        /** Esc: one step back inside the place. False when there is nothing to step back from (the band shuts). */
        default boolean back() {
            return false;
        }

        default boolean scroll(float mx, float my, double amount) {
            return false;
        }

        /** The mouse button went up (a hold let go). */
        default void released() {}

        /** What Esc would do, for the bar of keys: "Close", or where it steps back to. */
        default String backWords() {
            return "Close";
        }
    }

    private static final Place[] PLACES = new Place[8];

    /** Gives page {@code ordinal} (StatusSections.Tab's) its place. */
    public static void set(int ordinal, Place place) {
        PLACES[ordinal] = place;
    }

    public static @Nullable Place of(int ordinal) {
        return ordinal >= 0 && ordinal < PLACES.length ? PLACES[ordinal] : null;
    }
}
