package dev.awaken.compat;

import net.irisshaders.iris.api.v0.IrisApi;

/** Loaded only when Iris is present (see {@link Companions}). */
final class IrisCompat {
    private IrisCompat() {}

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    static boolean shadersEnabled() {
        return IrisApi.getInstance().getConfig().areShadersEnabled();
    }

    /** Iris's own "shaders on" switch (its public API); the chosen shaderpack stays the player's. */
    static void setShaders(boolean on) {
        IrisApi.getInstance().getConfig().setShadersEnabledAndApply(on);
    }
}
