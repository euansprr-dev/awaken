package dev.awaken.turbo;

import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.VersionInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The version lock of the Turbo contract: Turbo runs only on the Minecraft and NeoForge builds it was verified on.
 * After an update it stays off until its tests and tours pass again and the new versions are added here.
 * No Minecraft classes here: the mixin plugin asks before Minecraft's classes load.
 */
public final class TurboVersions {
    private TurboVersions() {}

    static final Logger LOG = LoggerFactory.getLogger("awaken/turbo");

    /** Minecraft version → the NeoForge builds verified on it. */
    public static final Map<String, Set<String>> VERIFIED = Map.of("26.3", Set.of("26.3.0.95"));

    private static @Nullable String checked;
    private static boolean done;

    /** Null when this game is a verified build, otherwise why not (for the "[turbo] off" line). */
    public static synchronized @Nullable String problem() {
        if (done) return checked;
        done = true;
        try {
            VersionInfo info = FMLLoader.getCurrent().getVersionInfo();
            Set<String> neo = VERIFIED.get(info.mcVersion());
            if (neo == null) checked = "Minecraft " + info.mcVersion() + " is not verified for Turbo";
            else if (!neo.contains(info.neoForgeVersion())) checked = "NeoForge " + info.neoForgeVersion() + " is not verified for Turbo";
            else checked = null;
        } catch (RuntimeException | LinkageError e) {
            checked = "versions unknown (" + e + ")";
        }
        return checked;
    }

    /** "MC 26.3, NeoForge 26.3.0.95" for the log line. */
    public static String describe() {
        try {
            VersionInfo info = FMLLoader.getCurrent().getVersionInfo();
            return "MC " + info.mcVersion() + ", NeoForge " + info.neoForgeVersion();
        } catch (RuntimeException | LinkageError e) {
            return "versions unknown";
        }
    }
}
