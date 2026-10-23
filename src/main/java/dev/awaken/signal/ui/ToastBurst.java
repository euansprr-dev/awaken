package dev.awaken.signal.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Merges bursts of toasts. Toasts wait a moment ({@link #QUIET} ticks without a new one, {@link #MAX_HOLD} at most) so
 * a burst arrives together; more than two toasts within {@link #WINDOW} ticks become one ("3 new things · K"). Pure
 * logic, tested by GameTests.
 */
public final class ToastBurst<T> {
    public static final int QUIET = 8;
    public static final int MAX_HOLD = 20;
    public static final int WINDOW = 40;
    public static final int MERGE_AT = 3;

    /** One thing to show: a single toast, or several merged into one. */
    public record Out<T>(List<T> items) {
        public boolean merged() { return items.size() > 1; }
        public T first() { return items.get(0); }
    }

    private final List<T> buffer = new ArrayList<>();
    private final Deque<Long> shown = new ArrayDeque<>();
    private long firstAt, lastAt;

    public void offer(long now, T item) {
        if (buffer.isEmpty()) firstAt = now;
        lastAt = now;
        buffer.add(item);
    }

    /** What to show now (often nothing). */
    public List<Out<T>> poll(long now) {
        while (!shown.isEmpty() && now - shown.peekFirst() >= WINDOW) shown.removeFirst();
        if (buffer.isEmpty()) return List.of();
        int recent = shown.size();
        boolean settled = now - lastAt >= QUIET || now - firstAt >= MAX_HOLD;
        // two already showed in this window: keep gathering until it closes so the rest arrive as one toast
        if (recent >= MERGE_AT - 1 && now - firstAt < WINDOW && now - shown.peekLast() < WINDOW) settled = false;
        if (!settled) return List.of();
        List<Out<T>> out = new ArrayList<>();
        if (buffer.size() >= MERGE_AT || (buffer.size() >= 2 && recent + buffer.size() >= MERGE_AT)) {
            out.add(new Out<>(List.copyOf(buffer)));
        } else {
            for (T t : buffer) out.add(new Out<>(List.of(t)));
        }
        for (int i = 0; i < out.size(); i++) shown.addLast(now);
        buffer.clear();
        return out;
    }

    public int buffered() { return buffer.size(); }

    public void clear() {
        buffer.clear();
        shown.clear();
    }
}
