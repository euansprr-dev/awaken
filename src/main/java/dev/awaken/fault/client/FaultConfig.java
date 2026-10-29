package dev.awaken.fault.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** How Faults play on this client ({@code config/awaken-faults-client.toml}, SPEC §43.8). */
public final class FaultConfig {
    private FaultConfig() {}

    /** FULL: the tearing, the words, the cut. GENTLE: the sound and the words, no tearing. STILL: a plain card. */
    public enum Mode { FULL, GENTLE, STILL }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<Mode> FAULTS = B
            .comment("How the big moments break the screen (SPEC 43.8). FULL: the frame tears into sliding bands, red and cyan split,",
                    "a cut to black with the System's words, a held look at what's happening. GENTLE keeps the sound and the words",
                    "and drops the tearing. STILL is a plain card. Never more than three flashes a second either way; turning off",
                    "screen flashes or camera shake makes FULL play as GENTLE.")
            .defineEnum("faults", Mode.FULL);
    public static final ModConfigSpec SPEC = B.build();

    public static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-faults-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Fault client settings could not be registered; the defaults apply", e);
        }
    }

    /** The mode in force: the setting, made gentler by screen flashes or camera shake turned off. */
    public static Mode mode() {
        Mode m;
        try {
            m = FAULTS.get();
        } catch (IllegalStateException notLoaded) {
            m = Mode.FULL;
        }
        if (m == Mode.FULL && (!flashes() || !shake())) m = Mode.GENTLE;
        return m;
    }

    private static boolean flashes() {
        try {
            return !dev.awaken.powers.client.fx.PowerFxConfig.SPEC.isLoaded() || dev.awaken.powers.client.fx.PowerFxConfig.SCREEN_FLASHES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static boolean shake() {
        try {
            return dev.awaken.core.AwakenConfig.Client.CAMERA_SHAKE.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
