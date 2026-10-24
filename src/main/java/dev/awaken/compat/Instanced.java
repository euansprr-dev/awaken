package dev.awaken.compat;

import dev.awaken.Awaken;

import java.util.Set;

/**
 * Awaken's instanced and sealed dimensions: gate and dream cells, trials, the Halo, the Descent's strata and the Endless.
 * They are void or rock until a plan arrives and their cells get reused, so companions that draw or remember a place
 * (far terrain, maps) stay out of them. Vanilla dimensions, other mods' and the Stillpoint are open. Plain Java with no
 * companion-mod types, so tests check it alone.
 */
public final class Instanced {
    private Instanced() {}

    /** Awaken dimensions open to companions; every other awaken: dimension is instanced or sealed. */
    public static final Set<String> OPEN = Set.of("awaken:stillpoint");

    public static boolean is(String dimension) {
        return dimension != null && dimension.startsWith(Awaken.MODID + ":") && !OPEN.contains(dimension);
    }
}
