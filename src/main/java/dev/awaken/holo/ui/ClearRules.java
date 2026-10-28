package dev.awaken.holo.ui;

import dev.awaken.core.api.HudState;

/**
 * The clear screen's rules (SPEC §40.6): which piece of the HUD shows in which state. Exploring shows the world (and the
 * Now line when it helps); a fight adds health, Aether and the powers; a slot change pops the hotbar for 2 s; Classic is
 * today's HUD exactly. Plain fields and functions: side-agnostic, checked by GameTests; the holo client's
 * {@code HudDirector} feeds them once a tick and eases each piece toward what they want.
 */
public final class ClearRules {
    private ClearRules() {}

    /** A fight lasts this long after its last cause (4 s). */
    public static final int FIGHT_TAIL = 80;
    /** A slot change (scroll, 1 to 9, pick block) pops the hotbar up this long (2 s). */
    public static final int HOTBAR_POP = 40;
    /** Fresh XP shows the level chip this long (3 s, with its "+XP" rise). */
    public static final int LEVEL_SHOW = 60;
    /** Hungry: the food line shows at or under this (7 of 10 shanks); at or under 6 you can't sprint and it pulses. */
    public static final int HUNGRY = 14, STARVING = 6;
    /** Low health: at or under this share the health line pulses. */
    public static final float LOW = 0.3F;
    /** A piece comes in fast, so a fight never waits for it, and leaves slowly, so the change is never noticed. */
    public static final float IN_MS = 150F, OUT_MS = 650F;
    /** The powers in a fight on the clear screen: smaller, toward the corner. */
    public static final float POWERS_SMALL = 0.8F;

    /** The pieces the director decides. */
    public enum Piece {
        /** Vanilla's hotbar (slides down out of view when hidden). */
        HOTBAR,
        /** Vanilla's hearts, armour, food, XP bar and level: the vitals lines and the wrist carry them instead. */
        VANILLA_STATUS,
        /** Health (and the Aether under it): the two thin lines low in the middle. */
        VITALS,
        /** Food, under them, only when hungry. */
        FOOD,
        /** The power bar. */
        POWERS,
        /** The level chip. */
        LEVEL
    }

    public static final int PIECES = Piece.values().length;

    /** What the director knows this tick. */
    public static final class State {
        public HudState.Screen screen = HudState.Screen.CLEAR;
        public boolean fighting, glancing, hurt, low, hungry, hotbarPop, powersBusy, levelNews, riding;
    }

    /** How strongly a piece wants to show now: 0 or 1 (the director eases between them). */
    public static float want(Piece p, State s) {
        if (s.screen == HudState.Screen.CLASSIC) return p == Piece.VITALS || p == Piece.FOOD ? 0F : 1F;
        boolean on = switch (p) {
            case HOTBAR -> s.hotbarPop || s.glancing;
            case VANILLA_STATUS -> false;
            case VITALS -> s.fighting || s.hurt || s.low;
            case FOOD -> s.hungry;
            case POWERS -> s.fighting || s.powersBusy || s.glancing;
            case LEVEL -> s.levelNews || s.glancing;
        };
        return on ? 1F : 0F;
    }

    /** One step of a piece's ease toward {@code want} over {@code dtMs} real milliseconds. */
    public static float ease(float cur, float want, float dtMs) {
        if (want > cur) return Math.min(want, cur + dtMs / IN_MS);
        return Math.max(want, cur - dtMs / OUT_MS);
    }

    /** Counts quiet ticks since its cause last held: on while fewer than its length have passed. */
    public static final class Timer {
        private final int length;
        private int since;

        public Timer(int length) {
            this.length = length;
            since = length;
        }

        public void tick(boolean cause) {
            since = cause ? 0 : Math.min(since + 1, length);
        }

        public void kick() {
            since = 0;
        }

        public boolean on() {
            return since < length;
        }

        public void reset() {
            since = length;
        }
    }
}
