package dev.awaken.body.client.feel;

import dev.awaken.Awaken;
import mirsario.cameraoverhaul.configuration.ConfigData;
import mirsario.cameraoverhaul.configuration.Configuration;

/**
 * Camera Overhaul (a pack mod, optional) rolls and sways the camera with your speed, and sways it idly whenever you
 * stand still. While an Ascent state owns the camera the two would stack, and while a scene holds the view still (a
 * station you work at, a scene's own camera: {@code FirstPerson.viewHeld}) its idle sway moves the world under hands
 * that are pinned to your pointer. The Feel Director switches it off for that time through its own public settings
 * (never saved: its file keeps the player's choice) and puts it back after. The ONLY class that names Camera
 * Overhaul: {@link FeelDirector} reaches it only once {@code ModList} said the mod is loaded, and any failure turns
 * the pause off for the session.
 */
final class CameraOverhaulPause {
    private CameraOverhaulPause() {}

    private static boolean paused, wasEnabled, broken;

    static void hold(boolean ascentOwnsCamera) {
        // (the name is from its first use: true for anything that needs the view left alone)
        if (broken || ascentOwnsCamera == paused) return;
        try {
            ConfigData cfg = Configuration.get();
            if (cfg == null || cfg.general == null) return;
            if (ascentOwnsCamera) {
                wasEnabled = cfg.general.enabled;
                cfg.general.enabled = false;
            } else {
                cfg.general.enabled = wasEnabled;
            }
            paused = ascentOwnsCamera;
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            Awaken.LOG.warn("Could not pause Camera Overhaul during Ascent moves; leaving it alone", e);
        }
    }
}
