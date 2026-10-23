package dev.awaken.signal.ui;

import dev.awaken.signal.SignalWindow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The client's pending decisions and the one choice card on screen. Pure logic (no client classes) so the key rules
 * are tested: one card at a time; Y/N presses count only for the card that is visible and armed, so a stray press can
 * never answer the next card; an unanswered card folds into the tracker after {@link #FOLD_TICKS} and stays pending
 * until the server's time-to-live runs out.
 */
public final class ChoiceDeck {
    /** Presses in the first ticks of a card are ignored: they were meant for whatever was there before. */
    public static final int ARM_TICKS = 8;
    /** Pause between one card closing and the next opening. */
    public static final int GAP_TICKS = 10;
    /** An unanswered card folds into the tracker after 20 seconds. */
    public static final int FOLD_TICKS = 20 * 20;

    public static final class Choice {
        public final String action;
        private SignalWindow window;
        private int ticksLeft;
        private int age;
        private boolean shown, folded;

        Choice(SignalWindow window, int ticksLeft, boolean folded) {
            this.action = window.action().orElseThrow();
            this.window = window;
            this.ticksLeft = ticksLeft;
            this.folded = folded;
        }

        public SignalWindow window() { return window; }
        public int ticksLeft() { return ticksLeft; }
        /** Ticks this card has been on screen. */
        public int age() { return age; }
        public boolean folded() { return folded; }
    }

    public record Answer(String action, boolean accepted) {}

    /** A server-side pending entry: the window and how long it has left. */
    public record Entry(SignalWindow window, int ticksLeft) {}

    private final List<Choice> pending = new ArrayList<>();
    private Choice current;
    private int gap;

    /** A new decision from the server. The newest copy of an action replaces an older one (and keeps its place). */
    public void offer(SignalWindow w, int ticksLeft) {
        if (w.action().isEmpty()) return;
        Choice existing = find(w.action().get());
        if (existing != null) {
            existing.window = w;
            existing.ticksLeft = ticksLeft;
            if (existing != current) { // asked again: show it again
                existing.folded = false;
                existing.shown = false;
            }
            return;
        }
        pending.add(new Choice(w, ticksLeft, false));
    }

    /**
     * The server's list of what is still pending (on login, after an answer, on expiry). Anything not in it is gone;
     * anything new is added folded, so a relog never throws cards at you.
     */
    public void sync(List<Entry> entries) {
        List<String> keep = new ArrayList<>();
        for (Entry e : entries) {
            if (e.window().action().isEmpty()) continue;
            String a = e.window().action().get();
            keep.add(a);
            Choice c = find(a);
            if (c == null) pending.add(new Choice(e.window(), e.ticksLeft(), true));
            else c.ticksLeft = e.ticksLeft();
        }
        for (Iterator<Choice> it = pending.iterator(); it.hasNext(); ) {
            Choice c = it.next();
            if (!keep.contains(c.action)) {
                it.remove();
                if (c == current) close();
            }
        }
    }

    /**
     * One client tick. {@code yes}/{@code no} are the clicks of the Y and N keys since the last tick (already drained
     * from the key mappings); they only count for an armed card. {@code paused} (a screen is open) stops the fold timer.
     * Returns the answer to send, or null.
     */
    public Answer tick(int yes, int no, boolean paused) {
        for (Iterator<Choice> it = pending.iterator(); it.hasNext(); ) {
            Choice c = it.next();
            if (--c.ticksLeft <= 0) {
                it.remove();
                if (c == current) close();
            }
        }
        if (current == null) {
            if (gap > 0) {
                gap--;
                return null;
            }
            for (Choice c : pending) {
                if (!c.shown && !c.folded) {
                    current = c;
                    c.shown = true;
                    c.age = 0;
                    break;
                }
            }
            return null; // presses with no armed card mean nothing
        }
        if (!paused) current.age++;
        if (current.age <= ARM_TICKS) return null;
        if (yes > 0 || no > 0) {
            Choice c = current;
            pending.remove(c);
            close();
            return new Answer(c.action, yes > 0);
        }
        if (current.age >= FOLD_TICKS) {
            current.folded = true;
            close();
        }
        return null;
    }

    /** Answered from the Status screen. Returns the answer to send, or null if it isn't pending any more. */
    public Answer answer(String action, boolean accepted) {
        Choice c = find(action);
        if (c == null) return null;
        pending.remove(c);
        if (c == current) close();
        return new Answer(action, accepted);
    }

    public void withdraw(String action) {
        Choice c = find(action);
        if (c == null) return;
        pending.remove(c);
        if (c == current) close();
    }

    private void close() {
        current = null;
        gap = GAP_TICKS;
    }

    private Choice find(String action) {
        for (Choice c : pending) if (c.action.equals(action)) return c;
        return null;
    }

    /** The card on screen, or null. */
    public Choice current() { return current; }

    /** Whether the card on screen takes key presses yet. */
    public boolean armed() { return current != null && current.age > ARM_TICKS; }

    /** Everything pending, oldest first (includes the card on screen). */
    public List<Choice> pending() { return pending; }

    /** Pending decisions not on screen: the tracker's "N decisions waiting". */
    public int waiting() { return pending.size() - (current == null ? 0 : 1); }

    public void clear() {
        pending.clear();
        current = null;
        gap = 0;
    }
}
