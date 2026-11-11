package dev.awaken.finds.verb;

import java.util.function.Predicate;

/**
 * Come back (SPEC §58.5): a link that finishes by itself once time has gone by and a condition holds, as long as
 * somebody is near enough for the site to be awake. The morning after an offering is {@link #morning}. The clock is
 * when the link began.
 */
public final class Later implements Script {
    private final int ticks;
    private final Predicate<Ctx> when;

    private Later(int ticks, Predicate<Ctx> when) {
        this.ticks = ticks;
        this.when = when;
    }

    public static Later after(int ticks, Predicate<Ctx> when) {
        return new Later(ticks, when);
    }

    /** At least half a minute on, and the sun in the first half of its day. */
    public static Later morning() {
        return new Later(600, c -> c.hour() <= 6000);
    }

    @Override
    public void tick(Ctx c) {
        if (c.clock() == 0) c.clock(c.now());
        if (c.now() - c.clock() < ticks || !when.test(c)) return;
        var near = c.players(c.kind().reach() + 12);
        c.complete(near.isEmpty() ? null : near.get(0));
    }
}
