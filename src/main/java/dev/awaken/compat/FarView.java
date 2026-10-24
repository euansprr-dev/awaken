package dev.awaken.compat;

import dev.awaken.Awaken;

import java.util.Set;

/**
 * Where far-terrain mods may draw past the render distance. Awaken's instanced dimensions (gate and dream cells,
 * trials, the Halo, the Descent's sealed shafts) are void or rock until a plan arrives, and cells get reused, so far
 * terrain there would only show phantom rooms. Plain Java with no companion-mod types, so tests can check it alone.
 */
public final class FarView {
    private FarView() {}

    /** Awaken dimensions open to the horizon; every other awaken: dimension is instanced or sealed. */
    public static final Set<String> OPEN = Set.of("awaken:stillpoint");

    public static boolean allowed(String dimension) {
        return dimension == null || !dimension.startsWith(Awaken.MODID + ":") || OPEN.contains(dimension);
    }
}
