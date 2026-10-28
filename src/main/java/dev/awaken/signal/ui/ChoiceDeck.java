package dev.awaken.signal.ui;

import dev.awaken.signal.SignalWindow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The client's pending decisions and the one choice card on screen. Pure logic (no client classes) so the key rules
 * are tested: one card at a time; Y/N presses count only for the card that is visible and armed, so a stray press can
 * never answer the next card; an unanswered card folds into the tracker after {@link #FOLD_TICKS} and stays pending
 * until the server's time-to-live runs out. A card asked while someone is talking (an offer after the giver's lines)
 * waits until they have finished, so the question comes after what it is about.
 */
public final class ChoiceDeck {
    /**
     * A card takes Y/N only after this many ticks in a row with no Y/N press since it appeared: presses before that
     * were meant for whatever was there before (or are a held, mashed key), so they restart the wait instead.
     */
    public static final int ARM_TICKS = 8;
    /** Pause between one card closing and the next opening. */
    public static final int GAP_TICKS = 10;
    /** An unanswered card folds into the tracker after 20 seconds. */
    public static final int FOLD_TICKS = 20 * 20;
    /** A card waits for speech at most this long (a minute), then shows anyway. */
    public static final int SPEECH_WAIT_TICKS = 20 * 60;

    public static final class Choice {
        public final String action;
        private SignalWindow window;
        private int ticksLeft;
        private int age;
        private int quiet;
        private int waited;
        private boolean shown, folded, afterSpeech;

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
        /** Waiting for a speech to end before it shows. */
        public boolean afterSpeech() { return afterSpeech; }
    }

    public record Answer(String action, boolean accepted) {}

    /** A server-side pending entry: the window and how long it has left. */
    public record Entry(SignalWindow window, int ticksLeft) {}

    private final List<Choice> pending = new ArrayList<>();
    private Choice current;
    private int gap;
    private int sinceSpeech = GAP_TICKS;

    public void offer(SignalWindow w, int ticksLeft) {
        offer(w, ticksLeft, false);
    }

    /**
     * A new decision from the server. The newest copy of an action replaces an older one (and keeps its place).
     * {@code speaking}: a story line is on screen or queued, so an ordinary card waits until it has been said.
     */
    public void offer(SignalWindow w, int ticksLeft, boolean speaking) {
        if (w.action().isEmpty()) return;
        boolean wait = speaking && !urgent(w);
        Choice existing = find(w.action().get());
        if (existing != null) {
            existing.window = w;
            existing.ticksLeft = ticksLeft;
            if (existing != current) { // asked again: show it again
                existing.folded = false;
                existing.shown = false;
                existing.afterSpeech = wait;
                existing.waited = 0;
            }
            return;
        }
        Choice c = new Choice(w, ticksLeft, false);
        c.afterSpeech = wait;
        pending.add(c);
        if (urgent(w) && current != null && !urgent(current.window)) { // a warning can't wait 20 s behind a question
            current.shown = false;
            current = null;
            gap = 0;
        }
    }

    private static int rank(SignalWindow w) {
        return (urgent(w) ? 1000 : 0) + w.priority();
    }

    /** Warnings and priority 9+ go first and push an ordinary card back into the queue. */
    public static boolean urgent(SignalWindow w) {
        return w.kind() == SignalWindow.Kind.WARNING || w.priority() >= 9;
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
        return tick(yes, no, paused, false);
    }

    /** One client tick; {@code speaking}: a story line is on screen or queued (cards asked during it wait). */
    public Answer tick(int yes, int no, boolean paused, boolean speaking) {
        sinceSpeech = speaking ? 0 : Math.min(GAP_TICKS, sinceSpeech + 1);
        for (Iterator<Choice> it = pending.iterator(); it.hasNext(); ) {
            Choice c = it.next();
            if (--c.ticksLeft <= 0) {
                it.remove();
                if (c == current) close();
                continue;
            }
            if (c.afterSpeech && (sinceSpeech >= GAP_TICKS || ++c.waited >= SPEECH_WAIT_TICKS)) c.afterSpeech = false;
        }
        if (current == null) {
            if (gap > 0) {
                gap--;
                return null;
            }
            Choice next = null;
            for (Choice c : pending) { // highest priority first, oldest first among equals
                if (!c.shown && !c.folded && !c.afterSpeech && (next == null || rank(c.window) > rank(next.window))) next = c;
            }
            if (next != null) {
                current = next;
                next.shown = true;
                next.age = 0;
                next.quiet = 0;
            }
            return null; // presses with no armed card mean nothing
        }
        if (!paused) current.age++;
        if (current.age >= FOLD_TICKS) {
            current.folded = true;
            close();
            return null;
        }
        if (current.quiet < ARM_TICKS) {
            if (yes > 0 || no > 0) current.quiet = 0; // still pressing: not for this card
            else current.quiet++;
            return null;
        }
        if (yes > 0 || no > 0) {
            Choice c = current;
            pending.remove(c);
            close();
            return new Answer(c.action, yes > 0);
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
    public boolean armed() { return current != null && current.quiet >= ARM_TICKS; }

    /** Everything pending, oldest first (includes the card on screen). */
    public List<Choice> pending() { return pending; }

    /** Pending decisions not on screen: the tracker's "N decisions waiting". */
    public int waiting() { return pending.size() - (current == null ? 0 : 1); }

    public void clear() {
        pending.clear();
        current = null;
        gap = 0;
        sinceSpeech = GAP_TICKS;
    }
}
