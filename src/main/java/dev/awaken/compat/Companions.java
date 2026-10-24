package dev.awaken.compat;

import net.neoforged.fml.ModList;

/** What the optional companion mods are doing right now, for Awaken's own systems to adapt to. Safe without them. */
public final class Companions {
    private Companions() {}

    private static final boolean IRIS = ModList.get().isLoaded("iris");

    /** True while an Iris shaderpack draws the world (Lens then steps aside). Client side only. */
    public static boolean shaderPackActive() {
        return IRIS && IrisCompat.shaderPackInUse();
    }
}
