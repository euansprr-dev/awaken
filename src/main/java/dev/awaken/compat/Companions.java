package dev.awaken.compat;

import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/** What the optional companion mods are doing right now, for Awaken's own systems to adapt to. Safe without them. */
public final class Companions {
    private Companions() {}

    private static final boolean IRIS = ModList.get().isLoaded("iris");

    /** True while an Iris shaderpack draws the world (Lens then steps aside). Client side only. */
    public static boolean shaderPackActive() {
        return IRIS && IrisCompat.shaderPackInUse();
    }

    public static boolean irisLoaded() {
        return IRIS;
    }

    /** Whether Iris's shaders switch is on (false without Iris). Client side only. */
    public static boolean irisShadersEnabled() {
        return IRIS && IrisCompat.shadersEnabled();
    }

    /** Sets Iris's shaders switch through its API; nothing without Iris. Client side only. */
    public static void setIrisShaders(boolean on) {
        if (IRIS) IrisCompat.setShaders(on);
    }

    /** A loaded mod's version, or null when it isn't installed. */
    public static @Nullable String version(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse(null);
    }
}
