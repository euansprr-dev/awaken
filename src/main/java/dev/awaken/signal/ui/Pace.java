package dev.awaken.signal.ui;

import dev.awaken.core.api.Notices.Tier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The notification director's pace (SPEC §26), pure logic (tested by GameTests; the client's {@code Herald} feeds it
 * ticks). CRITICAL goes straight out. Everything else waits for a quiet moment: a gap since the last message (longer
 * in calm exploration), at most {@link Rules#burst} in a {@link Rules#window}, never while held (a fight, a screen, a
 * cinematic). Waiting items of the same topic merge into one. What the density doesn't show, and what waited too long
 * to still be news, is filed in the Journal instead: a find is never lost, it just doesn't interrupt. An immediate
 * message answers something the player just did on purpose (a find they looked at): it skips the gap and the burst
 * limit and goes first, held only while nothing can show at all.
 */
public final class Pace<T> {
    public enum Density { MINIMAL, STANDARD, GUIDED }

    /**
     * One density's numbers, in ticks. {@code maxWait}: an ordinary message older than this is filed, not shown late;
     * a moment (a first of its kind, a wonder) waits up to {@code momentWait} for its quiet.
     */
    public record Rules(int gap, int calmGap, int burst, int window, int maxWait, int momentWait,
                        boolean importantShows, boolean ambientShows) {
        public static Rules of(Density d) {
            return switch (d) {
                case MINIMAL -> new Rules(30 * 20, 45 * 20, 1, 120 * 20, 30 * 20, 180 * 20, false, false);
                case STANDARD -> new Rules(8 * 20, 14 * 20, 3, 90 * 20, 45 * 20, 180 * 20, true, false);
                case GUIDED -> new Rules(4 * 20, 6 * 20, 5, 60 * 20, 60 * 20, 180 * 20, true, true);
            };
        }
    }

    public enum Act { SHOW, FILE }

    /** One decision: show these now (several merged when they share a topic), or file them. */
    public record Out<T>(Act act, Tier tier, boolean moment, List<T> items) {
        public boolean merged() { return items.size() > 1; }
        public T first() { return items.get(0); }
    }

    private static final class Waiting<T> {
        final Tier tier;
        final String topic;
        final boolean moment, immediate;
        final long since;
        final List<T> items = new ArrayList<>(2);

        Waiting(Tier tier, String topic, boolean moment, boolean immediate, long since) {
            this.tier = tier;
            this.topic = topic;
            this.moment = moment;
            this.immediate = immediate;
            this.since = since;
        }
    }

    private final List<Out<T>> ready = new ArrayList<>();
    private final List<Waiting<T>> queue = new ArrayList<>();
    private final Deque<Long> shown = new ArrayDeque<>();
    private long lastShown = Long.MIN_VALUE / 4;
    private Rules rules;

    public Pace(Rules rules) {
        this.rules = rules;
    }

    public void rules(Rules r) {
        this.rules = r;
    }

    public Rules rules() {
        return rules;
    }

    /** A message arrives. {@code topic} merges waiting messages ("" never merges). */
    public void offer(long now, T item, Tier tier, String topic, boolean moment) {
        offer(now, item, tier, topic, moment, false);
    }

    /** {@code immediate}: it answers what the player just did (the density still decides whether it shows at all). */
    public void offer(long now, T item, Tier tier, String topic, boolean moment, boolean immediate) {
        if (tier == Tier.CRITICAL) {
            ready.add(new Out<>(Act.SHOW, tier, moment, List.of(item)));
            return;
        }
        boolean shows = moment || (tier == Tier.IMPORTANT ? rules.importantShows : rules.ambientShows);
        if (!shows) {
            ready.add(new Out<>(Act.FILE, tier, false, List.of(item)));
            return;
        }
        if (!moment && !topic.isEmpty()) {
            for (Waiting<T> w : queue) {
                if (!w.moment && w.immediate == immediate && w.tier == tier && w.topic.equals(topic)) {
                    w.items.add(item);
                    return;
                }
            }
        }
        Waiting<T> w = new Waiting<>(tier, topic, moment, immediate, now);
        w.items.add(item);
        queue.add(w);
    }

    /** {@link #poll(long, boolean, boolean, boolean)} where whatever holds holds everything. */
    public List<Out<T>> poll(long now, boolean held, boolean calm) {
        return poll(now, held, held, calm);
    }

    /**
     * What happens now: everything ready (criticals, filings), anything that waited too long (filed), and at most one
     * paced message if the moment is free. {@code blocked}: nothing paced shows (a screen, a fight, a card up).
     * {@code held}: only immediate ones may (a world still settling). {@code calm}: the longer gap applies.
     */
    public List<Out<T>> poll(long now, boolean blocked, boolean held, boolean calm) {
        List<Out<T>> out = new ArrayList<>();
        for (Out<T> o : ready) {
            out.add(o);
            if (o.act() == Act.SHOW) mark(now);
        }
        ready.clear();
        for (int i = queue.size() - 1; i >= 0; i--) {
            Waiting<T> w = queue.get(i);
            if (now - w.since > (w.moment ? rules.momentWait : rules.maxWait)) {
                queue.remove(i);
                out.add(new Out<>(Act.FILE, w.tier, w.moment, List.copyOf(w.items)));
            }
        }
        while (!shown.isEmpty() && now - shown.peekFirst() >= rules.window) shown.removeFirst();
        if (blocked || queue.isEmpty()) return out;
        Waiting<T> next = next(true);
        if (next == null) {
            if (held || now - lastShown < (calm ? rules.calmGap : rules.gap) || shown.size() >= rules.burst) return out;
            next = next(false);
        }
        queue.remove(next);
        out.add(new Out<>(Act.SHOW, next.tier, next.moment, List.copyOf(next.items)));
        mark(now);
        return out;
    }

    /** The next to show: moments first, then the more important tier, then the oldest. */
    private Waiting<T> next(boolean immediateOnly) {
        Waiting<T> next = null;
        for (Waiting<T> w : queue) {
            if (immediateOnly && !w.immediate) continue;
            if (next == null || (w.moment && !next.moment) || (w.moment == next.moment && w.tier.ordinal() < next.tier.ordinal())) next = w;
        }
        return next;
    }

    /** Something took the screen outside the queue (a critical card, a caption that claimed its moment). */
    public void mark(long now) {
        lastShown = now;
        shown.addLast(now);
    }

    /** Whether a message of this tier could show right now (a client caption asking for its moment). */
    public boolean free(long now, Tier tier, boolean held, boolean calm) {
        if (tier == Tier.CRITICAL) return true;
        if (held || !queue.isEmpty()) return false;
        while (!shown.isEmpty() && now - shown.peekFirst() >= rules.window) shown.removeFirst();
        return now - lastShown >= (calm ? rules.calmGap : rules.gap) && shown.size() < rules.burst;
    }

    public int waiting() {
        int n = 0;
        for (Waiting<T> w : queue) n += w.items.size();
        return n;
    }

    public void clear() {
        ready.clear();
        queue.clear();
        shown.clear();
        lastShown = Long.MIN_VALUE / 4;
    }
}
