package dev.awaken.finds.verb;

import java.util.function.Consumer;

/**
 * A step that is the world answering (SPEC §61.4): the moment the step before it is done, something happens (a door
 * turns, a dish hums) and the next step begins. Nobody does anything for it.
 */
public final class Then implements Script {
    private final Consumer<Ctx> what;

    private Then(Consumer<Ctx> what) {
        this.what = what;
    }

    public static Then run(Consumer<Ctx> what) {
        return new Then(what);
    }

    @Override
    public void tick(Ctx c) {
        what.accept(c);
        c.complete(null);
    }
}
