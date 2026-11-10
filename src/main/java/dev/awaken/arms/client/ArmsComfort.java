package dev.awaken.arms.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.FirstPerson;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Comfort in a fight (SPEC §25 "Feel"; {@code config/awaken-arms-client.toml}, also in the mod's config screen): one
 * switch per layer of the feel. Reduced motion is the body's one setting ({@code FirstPerson.reducedMotion}, from the
 * feel settings): it halves every view kick and moment and keeps the low-health look still. Awaken's camera-shake
 * setting still turns every kick off.
 */
final class ArmsComfort {
    private ArmsComfort() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    static final ModConfigSpec.BooleanValue HIT_STOP = B
            .comment("The freeze on a blow: the struck body and your hands hold for a beat of its weight (the game's clock never stops).")
            .define("hitStop", true);
    static final ModConfigSpec.DoubleValue VIEW_KICK = B
            .comment("How much blows move the view: your strikes, a deflect, a guard broken, a blow landing on you (0 = never).")
            .defineInRange("viewKick", 1.0, 0.0, 1.0);
    static final ModConfigSpec.BooleanValue MOMENTS = B
            .comment("The fight's big moments in the view: a deflect's snap, a stance breaking, a perfect dodge's breath, an Execution",
                    "leaning in, the Art's release (the field of view, briefly; and Lens's colour for the breath).")
            .define("moments", true);
    static final ModConfigSpec.BooleanValue COMBAT_CAMERA = B
            .comment("In the shoulder view, the camera closes in a little and drops while you fight (with the action camera on).")
            .define("combatCamera", true);
    static final ModConfigSpec.BooleanValue LOW_HEALTH = B
            .comment("Under 30% health: a slow heartbeat, and the frame's edges cool and fade (with Lens on).")
            .define("lowHealth", true);
    // Fight Feel (SPEC §25 "Fight Feel")
    static final ModConfigSpec.BooleanValue RECOIL = B
            .comment("A struck body recoils along the blow and springs back, and a body you kill falls the way the blade went.")
            .define("recoil", true);
    static final ModConfigSpec.BooleanValue KILL_MOMENT = B
            .comment("A killing blow is its own moment: a longer freeze, a harder kick, its own sound and the ribbon's flare;",
                    "the last foe of a fight lets the view breathe out.")
            .define("killMoment", true);
    // Weight in the Hand (SPEC §25): each layer of the polish pass has its switch; off is the picture and the sound as before it
    static final ModConfigSpec.BooleanValue RECORDED_SOUNDS = B
            .comment("The weapon sounds made of Minecraft's own recordings (the hits, the parry, the charge and its glint, the kill).",
                    "Off: the first sound kit, as before the polish pass.")
            .define("recordedSounds", true);
    static final ModConfigSpec.BooleanValue WEAPON_SHAKE = B
            .comment("While a blow that landed is frozen, your weapon shudders along the cut and dies away. Off: it holds still.")
            .define("weaponShake", true);
    static final ModConfigSpec.BooleanValue WEAPON_SETTLE = B
            .comment("Where a weapon stops after a cut it carries a little past and comes back once, by its weight. Off: it stops dead.")
            .define("weaponSettle", true);
    static final ModConfigSpec.BooleanValue LIVING_HOLD = B
            .comment("A weapon held at rest breathes a little, and trails your look by its weight. Off: it holds still and every weapon trails alike.")
            .define("livingHold", true);
    static final ModConfigSpec.BooleanValue WEAPON_DRAW = B
            .comment("Changing to a weapon draws it: it comes up on its own clock with a turn of the wrist and its own sound.",
                    "Off: vanilla's rise, as slow as the weapon's attack cooldown, with no sound.")
            .define("weaponDraw", true);
    static final ModConfigSpec.BooleanValue GUARD_EASE = B
            .comment("The guard comes up on a curve and settles, and lowers over three ticks. Off: straight up, and down in one frame.")
            .define("guardEase", true);
    static final ModConfigSpec.BooleanValue GLANCE = B
            .comment("A strike that meets a wall glances off it: the block's sound, sparks, the blade knocked back. Off: nothing.")
            .define("glance", true);
    // Swing Studio (SPEC §25 "Swing Studio"): the swings read from a tuning folder while tuning is on (/awaken swing tune)
    static final ModConfigSpec.BooleanValue SWING_TUNING = B
            .comment("Swing Studio: read the first-person swings from the tuning folder as the lab writes them (/awaken swing tune on|off).")
            .define("swingTuning", false);
    static final ModConfigSpec.ConfigValue<String> SWING_TUNING_PATH = B
            .comment("Swing Studio's tuning folder (empty: AWAKEN_TUNING, else /Volumes/MEDIA/Awaken-tuning if it exists, else awaken-tuning in the game folder).")
            .define("swingTuningPath", "");
    static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-arms-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Arms comfort settings could not be registered; using defaults", e);
        }
    }

    static boolean hitStop() {
        return on(HIT_STOP);
    }

    /** The scale of a view kick now: the setting, halved under reduced motion, nothing with camera shake off. */
    static float kick() {
        try {
            if (!AwakenConfig.Client.CAMERA_SHAKE.get()) return 0;
        } catch (IllegalStateException notLoaded) {
            // defaults
        }
        float k = 1F;
        try {
            if (SPEC.isLoaded()) k = VIEW_KICK.get().floatValue();
        } catch (RuntimeException e) {
            // defaults
        }
        return reduced() ? k * 0.5F : k;
    }

    /** The scale of a moment now (0 when they're off). */
    static float moments() {
        return on(MOMENTS) ? reduced() ? 0.5F : 1F : 0F;
    }

    static boolean combatCamera() {
        return on(COMBAT_CAMERA);
    }

    static boolean recoil() {
        return on(RECOIL);
    }

    static boolean killMoment() {
        return on(KILL_MOMENT);
    }

    static boolean lowHealth() {
        return on(LOW_HEALTH);
    }

    static boolean recordedSounds() {
        return on(RECORDED_SOUNDS);
    }

    /** The shake's and the settle's scale now: 1, half under reduced motion, 0 when switched off. */
    static float shake() {
        return on(WEAPON_SHAKE) ? reduced() ? 0.5F : 1F : 0F;
    }

    static float settle() {
        return on(WEAPON_SETTLE) ? reduced() ? 0.5F : 1F : 0F;
    }

    /** The breath and the weighted look: off under reduced motion. */
    static boolean livingHold() {
        return on(LIVING_HOLD) && !reduced();
    }

    static boolean weaponDraw() {
        return on(WEAPON_DRAW);
    }

    static boolean guardEase() {
        return on(GUARD_EASE);
    }

    static boolean glance() {
        return on(GLANCE);
    }

    /** Flips every layer of the polish pass at once (/awaken swing weight): the quick A/B against the picture before it. */
    static void layers(boolean on) {
        try {
            if (!SPEC.isLoaded()) return;
            for (ModConfigSpec.BooleanValue v : new ModConfigSpec.BooleanValue[] {WEAPON_SHAKE, WEAPON_SETTLE, LIVING_HOLD, WEAPON_DRAW, GUARD_EASE}) v.set(on);
            SPEC.save();
        } catch (RuntimeException e) {
            Awaken.LOG.warn("The weight layers' state could not be saved", e);
        }
    }

    static boolean swingTuning() {
        try {
            return SPEC.isLoaded() && SWING_TUNING.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String swingTuningPath() {
        try {
            return SPEC.isLoaded() ? SWING_TUNING_PATH.get() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Saves Swing Studio's state (the config file keeps it across launches). */
    static void swingTuning(boolean on, String path) {
        try {
            if (!SPEC.isLoaded()) return;
            SWING_TUNING.set(on);
            SWING_TUNING_PATH.set(path);
            SPEC.save();
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Swing Studio's state could not be saved", e);
        }
    }

    static boolean reduced() {
        return FirstPerson.reducedMotion.getAsBoolean();
    }

    private static boolean on(ModConfigSpec.BooleanValue v) {
        try {
            return !SPEC.isLoaded() || v.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
