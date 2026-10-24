package dev.awaken.compat;

/**
 * Where far-terrain mods may draw past the render distance: everywhere but Awaken's {@link Instanced} dimensions, where
 * far terrain would only show phantom rooms. Plain Java with no companion-mod types, so tests can check it alone.
 */
public final class FarView {
    private FarView() {}

    public static boolean allowed(String dimension) {
        return !Instanced.is(dimension);
    }
}
