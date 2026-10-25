package dev.awaken.opening.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The opening's client settings ({@code config/awaken-opening-client.toml}): what this player has already seen and
 * chosen (the sky shot is skippable after the first viewing; first or third person, asked once), the Runner's eye
 * (on by default, shown less as you get better), the glyphs already shown, and the View key's skip of the front view.
 */
public final class OpeningConfig {
    private OpeningConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    static final ModConfigSpec.BooleanValue SKY_SEEN = B
            .comment("The opening's sky shot was seen to the end once: hold jump for a second to skip it.")
            .define("skyShotSeen", false);
    static final ModConfigSpec.ConfigValue<String> VIEW = B
            .comment("How you see Carillon, asked once at the end of the sky shot: \"first\", \"third\" or \"\" (ask).")
            .define("view", "");
    static final ModConfigSpec.BooleanValue SKIP_FRONT = B
            .comment("The View key (vanilla's Toggle Perspective, F5) flips between first and third person and skips the front view.")
            .define("viewKeySkipsFront", true);
    static final ModConfigSpec.BooleanValue RUNNERS_EYE = B
            .comment("The Runner's eye: when you hesitate at an edge, the next hold pulses once in bronze (shown less as you get better).")
            .define("runnersEye", true);
    static final ModConfigSpec.IntValue HINTS = B
            .comment("How many times the Runner's eye has shown a hold (it waits longer each time).")
            .defineInRange("runnersEyeShown", 0, 0, 10000);
    static final ModConfigSpec.ConfigValue<List<? extends String>> GLYPHS = B
            .comment("Moves whose key glyph has been shown (once per move).")
            .defineListAllowEmpty("glyphsShown", List.of(), () -> "", o -> o instanceof String);
    static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-opening-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("[opening] client settings could not be registered; using defaults", e);
        }
    }

    private static boolean loaded() {
        try {
            return SPEC.isLoaded();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean skySeen() {
        return loaded() && SKY_SEEN.get();
    }

    static void skySeen(boolean v) {
        if (!loaded()) return;
        SKY_SEEN.set(v);
        SPEC.save();
    }

    /** "first", "third" or "" (not asked yet). */
    static String view() {
        return loaded() ? VIEW.get() : "";
    }

    static void view(String v) {
        if (!loaded()) return;
        VIEW.set(v);
        SPEC.save();
    }

    static boolean skipFront() {
        return !loaded() || SKIP_FRONT.get();
    }

    static boolean runnersEye() {
        return !loaded() || RUNNERS_EYE.get();
    }

    static int hints() {
        return loaded() ? HINTS.get() : 0;
    }

    static void hint() {
        if (!loaded()) return;
        HINTS.set(Math.min(10000, HINTS.get() + 1));
        SPEC.save();
    }

    static boolean glyphShown(String move) {
        return loaded() && GLYPHS.get().contains(move);
    }

    static void glyph(String move) {
        if (!loaded()) return;
        List<String> l = new ArrayList<>(GLYPHS.get());
        if (!l.contains(move)) l.add(move);
        GLYPHS.set(l);
        SPEC.save();
    }
}
