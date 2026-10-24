package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModList;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Whether Awaken's terrain shader override fits the Sodium that is loaded. The override is a modified copy of Sodium's
 * own vertex shader, so it is only safe against the exact files it was written from: the Sodium version must be the
 * pinned one AND Sodium's terrain shader sources must hash to what they were. Any Sodium update (they rewrite these
 * files at every Minecraft port) turns waving off instead of breaking terrain. Iris replaces Sodium's terrain pipeline
 * with its own, so with Iris loaded the override never applies.
 */
final class WaveSupport {
    private WaveSupport() {}

    static final String SODIUM_VERSION = "0.9.3-beta.1+mc26.3";
    /** SHA-256 over Sodium 0.9.3-beta.1's terrain shader sources, in {@link #SODIUM_SHADERS} order. */
    static final String SODIUM_SHADER_SHA256 = "61daf1040c8e3873e504aaf5f4fdf3592e732980e1e7b9f78b9c283f5e7d0cc8";
    private static final String[] SODIUM_SHADERS = {
            "blocks/block_layer_opaque.vsh", "blocks/block_layer_opaque.fsh", "include/chunk_vertex.glsl",
            "include/chunk_material.glsl", "include/globals.glsl", "include/fog.glsl"};

    private static String reason; // null once checked and supported
    private static boolean checked;

    /** Null when waving leaves can run; otherwise why not, in a few words. */
    static synchronized String unsupportedReason() {
        if (!checked) {
            reason = check();
            checked = true;
            if (reason == null) Awaken.LOG.info("Waving leaves: Sodium {} with known terrain shaders, override available", SODIUM_VERSION);
            else Awaken.LOG.info("Waving leaves off: {}", reason);
        }
        return reason;
    }

    static boolean supported() {
        return unsupportedReason() == null;
    }

    private static String check() {
        ModList mods = ModList.get();
        if (mods.isLoaded("iris")) return "Iris is loaded (it replaces Sodium's terrain shaders)";
        if (!mods.isLoaded("sodium")) return "Sodium is not loaded (the vanilla renderer has no waving)";
        String version = mods.getModContainerById("sodium").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        if (!SODIUM_VERSION.equals(version)) return "Sodium " + version + " is not " + SODIUM_VERSION + ", the version the shader was written for";
        try {
            IModFileInfo file = mods.getModFileById("sodium");
            JarContents contents = file.getFile().getContents();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String name : SODIUM_SHADERS) sha.update(contents.readFile("assets/sodium/shaders/" + name));
            String hash = HexFormat.of().formatHex(sha.digest());
            if (!SODIUM_SHADER_SHA256.equals(hash)) return "Sodium's terrain shaders changed (sha256 " + hash + ")";
        } catch (Exception e) {
            return "Sodium's terrain shaders could not be read (" + e + ")";
        }
        return null;
    }
}
