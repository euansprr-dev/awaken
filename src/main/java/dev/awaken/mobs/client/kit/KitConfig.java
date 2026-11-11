package dev.awaken.mobs.client.kit;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for our creatures' bodies ({@code config/awaken-mobs-client.toml}, SPEC §35.4). */
public final class KitConfig {
    private KitConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue KIT_BODIES = B
            .comment("Bosses wear their own Creature Kit bodies, moving through every move they make (SPEC 35). Off: their old bodies (the fight is the same). Takes effect on a resource reload (F3+T).")
            .define("kitBodies", true);
    public static final ModConfigSpec.BooleanValue HIT_FEEL = B
            .comment("A boss's heavy blows shake your view when they land near you (only if camera shake is on too), and a boss pauses for a split second when a blow lands, its on you or yours on it (SPEC 35.10). Off: no shake and no pause from bosses.")
            .define("hitFeel", true);
    /** How a boss's finishing blow is shown to the one who strikes it (SPEC §67.4). */
    public enum FinishView {
        /** The view leaves your eyes for the blow and the boss's ending, then comes back. */
        CINEMATIC,
        /** The same beats through your own eyes: no camera move. */
        OWN_EYES,
        /** Nothing but the blow itself. */
        OFF
    }

    public static final ModConfigSpec.EnumValue<FinishView> FINISH_VIEW = B
            .comment("Finishing blows: CINEMATIC (the view leaves your eyes for the blow and the boss's ending), OWN_EYES (the same through your own eyes, no camera move), OFF. Reduced motion uses OWN_EYES in place of CINEMATIC.")
            .defineEnum("finishingBlows", FinishView.CINEMATIC);
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ENDINGS_SEEN = B
            .comment("The bosses whose ending you have watched once: from the second time, holding Sneak skips it. The game keeps this list.")
            .defineListAllowEmpty("endingsSeen", java.util.List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec SPEC = B.build();

    public static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-mobs-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Creature kit client settings could not be registered; the defaults apply", e);
        }
    }

    public static boolean kitBodies() {
        try {
            return KIT_BODIES.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static FinishView finishView() {
        try {
            return FINISH_VIEW.get();
        } catch (IllegalStateException notLoaded) {
            return FinishView.CINEMATIC;
        }
    }

    /** Whether this boss's ending has been watched before (so it may be skipped). */
    public static boolean endingSeen(String boss) {
        try {
            return ENDINGS_SEEN.get().contains(boss);
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    /** This boss's ending has been watched through: remembered across sessions. */
    public static void sawEnding(String boss) {
        try {
            if (ENDINGS_SEEN.get().contains(boss)) return;
            java.util.List<String> all = new java.util.ArrayList<>(ENDINGS_SEEN.get());
            all.add(boss);
            ENDINGS_SEEN.set(all);
            ENDINGS_SEEN.save();
        } catch (RuntimeException notLoaded) {
            Awaken.LOG.warn("A watched ending could not be remembered", notLoaded);
        }
    }

    public static boolean hitFeel() {
        try {
            return HIT_FEEL.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
