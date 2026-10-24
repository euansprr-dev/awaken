package dev.awaken.compat;

import net.irisshaders.iris.api.v0.IrisApi;

/** Loaded only when Iris is present (see {@link Companions}). */
final class IrisCompat {
    private IrisCompat() {}

    static boolean shaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
