package dev.awaken.signal.ui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Just-in-time tips, the pure rules (docs/carillon/m4/onboarding.md §1, SPEC §27.7; tested by GameTests, the client's
 * {@code signal.client.Tips} feeds them ticks). One tip at a time, the highest priority whose context holds; it shows once
 * its context has held {@link #SETTLE} ticks, stays while it holds (up to {@code showFor}), and is never shown again once
 * retired (done {@code retireAfterDone} times, or shown {@code retireAfterShown} times). {@code cooldown} between showings
 * of the same tip, {@link #GAP} between any two.
 */
public final class TipRules {
    private TipRules() {}

    /** Ticks a context must hold before its tip shows (no flicker), and ticks between any two tips. */
    public static final int SETTLE = 6, GAP = 80;

    /** One tip's data: {@code assets/awaken/tips/<group>/<name>.json}. {@code awake}: before, after or any. */
    public record Def(String text, String shortText, int priority, int showFor, int retireAfterDone, int retireAfterShown, int cooldown, String awake) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Def::text),
                Codec.STRING.optionalFieldOf("short", "").forGetter(Def::shortText),
                Codec.INT.optionalFieldOf("priority", 50).forGetter(Def::priority),
                Codec.INT.optionalFieldOf("show_for", 140).forGetter(Def::showFor),
                Codec.INT.optionalFieldOf("retire_after_done", 2).forGetter(Def::retireAfterDone),
                Codec.INT.optionalFieldOf("retire_after_shown", 3).forGetter(Def::retireAfterShown),
                Codec.INT.optionalFieldOf("cooldown", 600).forGetter(Def::cooldown),
                Codec.STRING.optionalFieldOf("awake", "any").forGetter(Def::awake)
        ).apply(i, Def::new));

        /** Whether this tip applies to a player who has (or hasn't) awakened. */
        public boolean appliesTo(boolean awakened) {
            return switch (awake) {
                case "before" -> !awakened;
                case "after" -> awakened;
                default -> true;
            };
        }
    }

    /** How often a tip has shown and been done (kept per person, not per world: you learn your controls once). */
    public record Count(int shown, int done) {
        public static final Count NONE = new Count(0, 0);
    }

    public static boolean retired(Def d, Count c) {
        return c.done() >= d.retireAfterDone() || c.shown() >= d.retireAfterShown();
    }

    /** The text to show: the short form (just the keys) once it has been done at least once, if it has one. */
    public static String text(Def d, Count c) {
        return c.done() > 0 && !d.shortText().isEmpty() ? d.shortText() : d.text();
    }

    /**
     * The tip to start now, or null: among those whose context has held at least {@link #SETTLE} ticks ({@code heldSince}:
     * id → tick its context began, live ones only), the highest priority that applies, isn't retired and is off its
     * cooldown ({@code lastShown}: id → tick it last showed), and only {@link #GAP} after the last tip of any kind.
     */
    public static String pick(long now, Map<String, Long> heldSince, Map<String, Def> defs, Map<String, Count> counts,
                              Map<String, Long> lastShown, long lastAny, boolean awakened, Predicate<String> claim) {
        if (now - lastAny < GAP) return null;
        String best = null;
        int bestP = Integer.MIN_VALUE;
        for (var e : heldSince.entrySet()) {
            Def d = defs.get(e.getKey());
            if (d == null || now - e.getValue() < SETTLE || !d.appliesTo(awakened)) continue;
            if (retired(d, counts.getOrDefault(e.getKey(), Count.NONE))) continue;
            Long last = lastShown.get(e.getKey());
            if (last != null && now - last < d.cooldown()) continue;
            if (d.priority() > bestP || d.priority() == bestP && e.getKey().compareTo(best) < 0) {
                best = e.getKey();
                bestP = d.priority();
            }
        }
        return best != null && claim.test(best) ? best : null;
    }
}
