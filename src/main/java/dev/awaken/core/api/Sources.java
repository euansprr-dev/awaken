package dev.awaken.core.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ways into the game that aren't data (SPEC §43.11, the bar: "nothing without a way in"). A recipe or a loot table
 * speaks for itself; a thing that comes from code (a reward, a work, a placement in the world, something a citizen hands
 * you) is declared here by the module that gives it, in plain words, and a thing never meant to be held (a technical
 * block, a citizen's prop) goes on the never-held list with why. The {@code journey_ledger} GameTest checks every
 * {@code awaken} item and block has one or the other. Ids are paths in the {@code awaken} namespace.
 */
public final class Sources {
    private Sources() {}

    private static final Map<String, String> DECLARED = new LinkedHashMap<>();
    private static final Map<String, String> NEVER_HELD = new LinkedHashMap<>();

    /** These come into the game this way ("a Gate Lord's kill: Lord-forged, to the slayer's hand"). */
    public static synchronized void declare(String how, String... ids) {
        for (String id : ids) DECLARED.merge(id, how, (a, b) -> a + "; " + b);
    }

    /** These are never meant to be held, and why ("a citizen's prop: only ever in their hands"). */
    public static synchronized void neverHeld(String why, String... ids) {
        for (String id : ids) NEVER_HELD.put(id, why);
    }

    public static synchronized Map<String, String> declared() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(DECLARED));
    }

    public static synchronized Map<String, String> neverHeld() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(NEVER_HELD));
    }
}
