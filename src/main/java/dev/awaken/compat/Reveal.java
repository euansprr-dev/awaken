package dev.awaken.compat;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps a viewer's hidden things in step with the spoiler rule: each {@link #update} walks everything guarded, hides what
 * may not show and brings back what now may, and reports each change once. So a recipe viewer is touched only for what
 * actually moved (a discovery, a game mode change, the setting), and an item appears the moment it is discovered.
 * Things are matched by identity. Not thread-safe: one viewer, one thread.
 */
public final class Reveal<T> {
    private final Set<T> hidden = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Applies the rule to {@code all}; {@code hide} and {@code show} hear about the things whose state changed. */
    public void update(Iterable<T> all, Predicate<T> shows, Consumer<T> hide, Consumer<T> show) {
        for (T t : all) {
            boolean visible = shows.test(t);
            if (visible && hidden.remove(t)) show.accept(t);
            else if (!visible && hidden.add(t)) hide.accept(t);
        }
    }

    public boolean isHidden(T t) {
        return hidden.contains(t);
    }

    public int hiddenCount() {
        return hidden.size();
    }

    /** Forgets everything hidden (the viewer restarted and shows everything again). */
    public void clear() {
        hidden.clear();
    }
}
