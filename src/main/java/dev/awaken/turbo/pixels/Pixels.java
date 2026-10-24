package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.awaken.Awaken;
import dev.awaken.turbo.pixels.mixin.RenderTargetAccess;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Pixels (Far Sight path 4, SPEC §16): the world is drawn at a lower resolution and rebuilt to the screen's. A Turbo
 * feature: it reaches into the renderer through its own mixins (dev.awaken.turbo.pixels.mixin), so it only turns on
 * for the game and NeoForge versions it was verified on, switches itself off for good on the first thing that goes
 * wrong, and with it off the game renders exactly as without Awaken.
 */
public final class Pixels {
    private Pixels() {}

    /** The versions Pixels' hooks were read against and tested on. Any other: off, with one log line. */
    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95";

    // settings as of the last tick (config, or the autopilot's override)
    private static float scale = 1f;
    private static PixelsConfig.Upscaler upscaler = PixelsConfig.Upscaler.FSR;
    private static float forcedScale = -1f;
    private static PixelsConfig.@Nullable Upscaler forcedUpscaler;
    // -1 unknown, 0 off (logged why), 1 on
    private static int versionOk = -1, accessOk = -1;
    private static boolean failed;

    /** Once a tick: the settings, read into plain fields. */
    static void refresh() {
        try {
            if (PixelsConfig.SPEC.isLoaded()) {
                scale = PixelsConfig.RENDER_SCALE.get().floatValue();
                upscaler = PixelsConfig.UPSCALER.get();
            }
        } catch (RuntimeException e) {
            // keep the last good values
        }
        if (forcedScale > 0f) scale = forcedScale;
        if (forcedUpscaler != null) upscaler = forcedUpscaler;
    }

    /** The world's share of the screen's resolution this frame (1 = native). */
    static float scale() {
        return Math.clamp(scale, 0.5f, 1f);
    }

    /** The upscaler that will run: FSR and SHARP fall back to bilinear when their pipelines didn't compile. */
    static PixelsConfig.Upscaler upscaler() {
        return switch (upscaler) {
            case FSR -> PixelsPipelines.fsrOk() ? PixelsConfig.Upscaler.FSR : PixelsConfig.Upscaler.BILINEAR;
            case SHARP -> PixelsPipelines.sharpOk() ? PixelsConfig.Upscaler.SHARP : PixelsConfig.Upscaler.BILINEAR;
            case BILINEAR -> PixelsConfig.Upscaler.BILINEAR;
        };
    }

    /** Whether Pixels may take this frame: right versions, its hooks applied, nothing failed, an upscaler compiled. */
    static boolean usable() {
        if (failed) return false;
        if (versionOk < 0) versionOk = versionsMatch() ? 1 : 0;
        if (versionOk == 0) return false;
        if (accessOk < 0) accessOk = accessApplied() ? 1 : 0;
        return accessOk == 1 && PixelsPipelines.bilinearOk();
    }

    /** Pixels steps aside for the rest of the session: the world renders at native resolution. */
    static void fail(String why, @Nullable Throwable cause) {
        if (failed) return;
        failed = true;
        PixelsFrame.release();
        if (cause != null) Awaken.LOG.warn("[pixels] off: {}", why, cause);
        else Awaken.LOG.warn("[pixels] off: {}", why);
    }

    private static boolean versionsMatch() {
        String mc = SharedConstants.getCurrentVersion().name();
        String neo = ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        if (MINECRAFT.equals(mc) && NEOFORGE.equals(neo)) return true;
        Awaken.LOG.warn("[pixels] off: verified on Minecraft {} with NeoForge {}, this is {} with {}", MINECRAFT, NEOFORGE, mc, neo);
        return false;
    }

    private static boolean accessApplied() {
        try {
            // the accessor mixin adds its interface to RenderTarget; without it, Pixels can't lend the main target out
            if (RenderTargetAccess.class.isAssignableFrom(RenderTarget.class)) return true;
        } catch (LinkageError ignored) {
            // the mixin package failed to load
        }
        Awaken.LOG.warn("[pixels] off: the render target accessor did not apply");
        return false;
    }

    /**
     * The autopilot's line: {@code pixels <scale> [fsr|sharp|bilinear]} overrides the settings for the following scenes
     * (not saved), {@code pixels config} goes back to them, {@code pixels state} logs what's in force;
     * {@code pixels gpu on|off|reset|<label>} times the world pass and the upscale on the GPU (PixelsTiming).
     */
    public static String autopilot(String arg) {
        String[] a = arg.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (a[0].equals("gpu")) {
            String what = a.length > 1 ? a[1] : "";
            switch (what) {
                case "on", "off" -> PixelsTiming.enable(what.equals("on"));
                case "reset" -> PixelsTiming.reset();
                default -> {
                    return PixelsTiming.report(what);
                }
            }
            return "gpu timing " + what;
        }
        switch (a[0]) {
            case "config" -> {
                forcedScale = -1f;
                forcedUpscaler = null;
            }
            case "state" -> {
                // nothing to change
            }
            default -> {
                forcedScale = Math.clamp(Float.parseFloat(a[0]), 0.5f, 1f);
                if (a.length > 1) forcedUpscaler = PixelsConfig.Upscaler.valueOf(a[1].toUpperCase(Locale.ROOT));
            }
        }
        refresh();
        return String.format(Locale.ROOT, "scale %.2f, %s, %s", scale(), upscaler(), failed ? "off (failed)" : usable() ? "ready" : "unavailable");
    }
}
