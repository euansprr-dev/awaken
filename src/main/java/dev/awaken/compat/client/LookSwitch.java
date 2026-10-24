package dev.awaken.compat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.awaken.Awaken;
import dev.awaken.compat.Companions;
import dev.awaken.compat.Look;
import dev.awaken.compat.Look.Tier;
import dev.awaken.core.AwakenConfig;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Applies a Look tier (SPEC §14, the Look switch) to every mod that offers a supported way to set it, writing only the
 * keys {@link Look} lists: Lens (Awaken's client config), Distant Horizons (its config API now, its file for the next
 * start) and Iris's shaders switch (its API). Sodium is left alone: it has no supported way for another mod to set its
 * options, and its options file is in use while the game runs.
 */
public final class LookSwitch {
    private LookSwitch() {}

    static final boolean DH = ModList.get().isLoaded("distanthorizons");
    static final boolean SODIUM = ModList.get().isLoaded("sodium");

    /** The tier chosen in this session (null: none yet). DH's file gets it again when the game closes. */
    static @Nullable Tier chosen;
    /** What happened to Distant Horizons on the last choice, for the screen. */
    static String dhResult = "";
    static boolean dhWarn;

    /** The tier the current settings are (null while Lens is off and no shaderpack draws). */
    public static @Nullable Tier current() {
        return Look.current(AwakenConfig.Client.LENS.get(), Companions.irisShadersEnabled());
    }

    public static Look.Availability ultra() {
        return Look.ultra(Companions.version("iris"), RenderSystem.getDevice().getDeviceInfo().backendName());
    }

    /** Applies the tier. False (and nothing changed) when the tier can't run here (Ultra without a working Iris). */
    public static boolean apply(Tier tier) {
        if (tier.irisShaders && !ultra().available()) return false;
        AwakenConfig.Client.LENS.set(tier.lens);
        AwakenConfig.Client.LENS.save();
        if (DH) {
            int live = DistantHorizonsLook.applyLive(tier);
            String saveError = writeDhFile(tier);
            dhWarn = saveError != null || live < 0;
            dhResult = live < 0 && saveError == null ? "saved; applies on restart (its API isn't up yet)"
                    : saveError != null ? (live > 0 ? "set now; couldn't save: " : "couldn't set or save: ") + saveError
                    : "set now, saved for the next start";
        }
        if (Companions.irisLoaded() && Companions.irisShadersEnabled() != tier.irisShaders) Companions.setIrisShaders(tier.irisShaders);
        chosen = tier;
        Awaken.LOG.info("[look] {}: Lens {}, Distant Horizons {}, Iris shaders {}", tier, tier.lens, DH ? dhResult : "not installed",
                Companions.irisLoaded() ? tier.irisShaders : "not installed");
        return true;
    }

    /** The game is closing: DH may have saved its in-memory settings over ours since, so its file gets the tier again. */
    static void onShutdown() {
        if (DH && chosen != null) writeDhFile(chosen);
    }

    /** Writes the tier's DH keys into config/DistantHorizons.toml, everything else as it was; null when it worked. */
    static @Nullable String writeDhFile(Tier tier) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("DistantHorizons.toml");
        try {
            String text = Files.exists(file) ? Files.readString(file) : "";
            Look.Edit edit = Look.writeDh(text, Look.dh(tier));
            if (edit.text().equals(text)) return null;
            Path tmp = file.resolveSibling(file.getFileName() + ".awaken-tmp");
            Files.writeString(tmp, edit.text());
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            Awaken.LOG.info("[look] {} written to {}: changed {}, added {}", tier, file.getFileName(), edit.changed(), edit.added());
            return null;
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.warn("[look] couldn't write {}: {}", file, e.toString());
            return e.getClass().getSimpleName();
        }
    }
}
