package dev.awaken.body.client.feel;

import net.minecraft.client.Minecraft;

/**
 * Which view a world starts in (SPEC §17.13): with Shoulder Surfing installed and {@link FeelConfig#shoulderView()} on,
 * the first moment after you join (once the opening's shots are done) a first-person view becomes the shoulder view,
 * once a session; the perspective key then does what you choose, and the View key flips first person and the shoulder
 * view ({@link dev.awaken.core.api.CameraHooks}). Without Shoulder Surfing, nothing here runs.
 */
public final class ShoulderView {
    private ShoulderView() {}

    public static final boolean INSTALLED = net.neoforged.fml.ModList.get().isLoaded("shouldersurfing");
    private static boolean offered;

    public static void init() {
        if (INSTALLED) dev.awaken.core.api.CameraHooks.installToggle(ShoulderSurfingPlugin::toggle);
    }

    public static void tick(Minecraft mc) {
        if (!INSTALLED || offered || mc.player == null || mc.gui.screen() != null || dev.awaken.opening.client.OpeningCamera.active()) return;
        offered = true;
        if (FeelConfig.shoulderView() && mc.options.getCameraType().isFirstPerson()) ShoulderSurfingPlugin.shoulder();
    }

    public static void reset() {
        offered = false;
    }
}
