package dev.awaken.core.api;

import net.minecraft.resources.Identifier;

/**
 * The clear screen (SPEC §40.6, client): which screen the player chose, whether a fight is on, whether they are
 * glancing at the wrist, and how strongly each HUD layer shows right now. The holo module runs the director and installs
 * it; every module's layer asks {@link #alpha} with its id and draws at that strength (0: skip it). Until installed,
 * everything shows as it always has.
 */
public final class HudState {
    private HudState() {}

    public enum Screen {
        /** The clear screen: pieces show when they matter, the dot instead of the crosshair. */
        CLEAR,
        /** The clear screen with the crosshair kept. */
        CLEAR_CROSSHAIR,
        /** Today's HUD and crosshair, exactly. */
        CLASSIC
    }

    public interface Director {
        Screen screen();

        boolean fighting();

        boolean glancing();

        float alpha(Identifier layer);

        /** The GUI y of the vitals' {@code row} (0 health, 1 Aether, 2 food), low in the middle, over the hotbar while it shows. */
        default int vitalsY(int guiHeight, int row) {
            return guiHeight - 32 + row * 4;
        }
    }

    private static Director director = new Director() {
        @Override public Screen screen() { return Screen.CLASSIC; }
        @Override public boolean fighting() { return false; }
        @Override public boolean glancing() { return false; }
        @Override public float alpha(Identifier layer) { return 1F; }
    };

    /** Half the vitals lines' width (GUI px): health, Aether and food share one centre. */
    public static final int VITALS_HALF = 62;

    public static void install(Director d) {
        director = d;
    }

    public static Screen screen() {
        return director.screen();
    }

    public static boolean classic() {
        return director.screen() == Screen.CLASSIC;
    }

    /** The crosshair is drawn as vanilla's (Classic, or the clear screen with the crosshair kept). */
    public static boolean crosshair() {
        return director.screen() != Screen.CLEAR;
    }

    /** Something hostile hunts the player, or they were hurt or struck, until 4 s after it ends. */
    public static boolean fighting() {
        return director.fighting();
    }

    /** The wrist is raised (hold Tab) or a tap's panel shows. */
    public static boolean glancing() {
        return director.glancing();
    }

    /** How strongly a HUD layer shows now, 0..1 (1 in Classic). */
    public static float alpha(Identifier layer) {
        return director.alpha(layer);
    }

    /** Where the vitals' {@code row} sits (0 health, 1 Aether, 2 food): Ascent draws its Aether line there on the clear screen. */
    public static int vitalsY(int guiHeight, int row) {
        return director.vitalsY(guiHeight, row);
    }

    private static java.util.function.DoubleSupplier aether = () -> -1;

    /** Ascent's client installs the Aether reading (0..1, -1 before Skyborn) for the glance. */
    public static void installAether(java.util.function.DoubleSupplier s) {
        aether = s;
    }

    /** The player's Aether, 0..1, or -1 when they have none yet (client). */
    public static double aether() {
        return aether.getAsDouble();
    }
}
