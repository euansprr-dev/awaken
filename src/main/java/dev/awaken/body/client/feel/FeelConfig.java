package dev.awaken.body.client.feel;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Comfort for moving fast (SPEC §17), in its own file ({@code config/awaken-feel-client.toml}, also in the mod's config
 * screen): how far the camera rolls, how much speed widens the view (on top of vanilla's FOV-effects slider), shake,
 * reduced motion (no streaks, motes, flashes or tremor; half the roll and half the action camera's swing), the action
 * camera, and whether a world starts in the shoulder view.
 */
public final class FeelConfig {
    private FeelConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue CAMERA_ROLL = B
            .comment("How far the camera rolls into turns and swings, in degrees (0 = never).")
            .defineInRange("cameraRoll", 5.0, 0.0, 7.5);
    public static final ModConfigSpec.DoubleValue WALL_RUN_ROLL = B
            .comment("How far the camera rolls away from a wall you run along, in degrees (0 = never).")
            .defineInRange("wallRunRoll", 5.0, 0.0, 7.5);
    public static final ModConfigSpec.DoubleValue SPEED_FOV = B
            .comment("How much speed widens the view (0..1, times vanilla's FOV effects slider).")
            .defineInRange("speedFov", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue MOVEMENT_SHAKE = B
            .comment("A tremor at the highest speeds and a jolt on hard landings and sonic booms (also off with Awaken's cameraShake).")
            .define("movementShake", true);
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION = B
            .comment("Reduced motion: no light streaks, air motes, flashes or tremor, and half the camera roll.")
            .define("reducedMotion", false);
    public static final ModConfigSpec.BooleanValue ACTION_CAMERA = B
            .comment("The action camera: in the shoulder view (or vanilla's third person) it pulls back with speed, moves to the open side of a",
                    "wall-run, drops low on a slide and swings wide on the rope, the wings and flight (SPEC §17.13).")
            .define("actionCamera", true);
    public static final ModConfigSpec.BooleanValue SHOULDER_VIEW = B
            .comment("Start in Shoulder Surfing's over-the-shoulder view when you join a world in first person (Shoulder Surfing installed).",
                    "Your own choice with the perspective key holds until you leave.")
            .define("shoulderView", true);
    public static final ModConfigSpec.BooleanValue FREE_RUN = B
            .comment("The one-button free-run: hold sprint with forward and the body vaults, mantles, climbs, runs walls and leaps from",
                    "edges as the ground asks, and jumps bend a little toward the ledge ahead (SPEC §17.14). The keys still work by hand.")
            .define("freeRun", true);
    public static final ModConfigSpec.BooleanValue FP_MOVES = B
            .comment("First person: your hands and feet through every move (a pipe, a hang, a slide, the rope, wings, flight; SPEC §24).")
            .define("firstPersonMoves", true);
    public static final ModConfigSpec.BooleanValue FP_TOUCHES = B
            .comment("First person: your hands on the things you use (a door pushed, a lever thrown, a chest's lid lifted, a herb plucked).")
            .define("firstPersonTouches", true);
    public static final ModConfigSpec.BooleanValue STATIONS = B
            .comment("Station scenes: at a crafting table the view leans in and your hands lay each ingredient on its square of the table",
                    "(sneak + use for the plain screen).")
            .define("stationScenes", true);
    public static final ModConfigSpec.BooleanValue STATION_BENCH = B
            .comment("Station scenes: the view goes to the station's own place (over a table's near edge), the same from wherever you",
                    "stand (SPEC §65.1). Off: the view turns and zooms from your own eyes, as it did before.")
            .define("stationBenchView", true);
    public static final ModConfigSpec.BooleanValue ROPE_ASSIST = B
            .comment("The assisted rope swing: the rope picks a good anchor along your heading, fits its length, swings you round what's",
                    "in the way and lets go at the top (SPEC §17.31). Off: the rope bites where you look and swings as it always did.")
            .define("ropeAssist", true);
    public static final ModConfigSpec.BooleanValue ROPE_GUIDE = B
            .comment("The rope finds the way (SPEC §62): where nothing stands tall enough to swing from, the rope slings you past a top",
                    "ahead; a held rope lets go while it still goes forward and carries on through a landing. Off: the assisted swing",
                    "exactly as it was. Needs ropeAssist.")
            .define("ropeGuide", true);
    public static final ModConfigSpec.BooleanValue ROPE_HOLD = B
            .comment("The held rope (SPEC §62): the rope never lets go by itself. It swings you back and forth like a real rope until",
                    "you let go of the key; forward pumps the swing, back slows it to a hang. Off: the rope lets go at the top of its arc",
                    "and before a wall by itself, as it did before. Needs ropeAssist.")
            .define("ropeHold", true);
    public static final ModConfigSpec.BooleanValue WINGS_FEATHERS = B
            .comment("Wings of feathers (SPEC §62): each wing is feathers of light in boxes, with a look for each rung of the ladder.",
                    "Off: the single sheet the wings were before.")
            .define("wingsFeathers", true);
    public static final ModConfigSpec.BooleanValue MOVEMENT_SCORES = B
            .comment("Movement sounds made from Minecraft's own recordings (SPEC §62). Off: the sounds as they were (Classic).")
            .define("movementScores", true);
    public static final ModConfigSpec SPEC = B.build();

    public static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-feel-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Feel settings could not be registered; using defaults", e);
        }
    }

    static float roll() {
        try {
            float r = SPEC.isLoaded() ? CAMERA_ROLL.get().floatValue() : 5F;
            return reduced() ? r * 0.5F : r;
        } catch (RuntimeException e) {
            return 5F;
        }
    }

    /** The wall-run's roll (degrees); half under reduced motion. */
    public static float wallRunRoll() {
        try {
            float r = SPEC.isLoaded() ? WALL_RUN_ROLL.get().floatValue() : 5F;
            return reduced() ? r * 0.5F : r;
        } catch (RuntimeException e) {
            return 5F;
        }
    }

    static float speedFov() {
        try {
            return SPEC.isLoaded() ? SPEED_FOV.get().floatValue() : 1F;
        } catch (RuntimeException e) {
            return 1F;
        }
    }

    static boolean shake() {
        try {
            return (!SPEC.isLoaded() || MOVEMENT_SHAKE.get()) && !reduced();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean actionCamera() {
        try {
            return !SPEC.isLoaded() || ACTION_CAMERA.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean freeRun() {
        try {
            return !SPEC.isLoaded() || FREE_RUN.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** The assisted rope swing (SPEC §17.31); off, the old pendulum. */
    public static boolean ropeAssist() {
        try {
            return !SPEC.isLoaded() || ROPE_ASSIST.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** The held rope (SPEC §62): letting go of the key is the only let-go of a swing. */
    public static boolean ropeHold() {
        try {
            return !SPEC.isLoaded() || ROPE_HOLD.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** The rope finds the way (SPEC §62): the sling, the earlier let-go, the chain through a landing. */
    public static boolean ropeGuide() {
        try {
            return !SPEC.isLoaded() || ROPE_GUIDE.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Wings as feathers in boxes (SPEC §62); off, the old sheet. */
    public static boolean wingsFeathers() {
        try {
            return !SPEC.isLoaded() || WINGS_FEATHERS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Movement sounds as scores of Minecraft's recordings (SPEC §62); off, the synthesised ones. */
    public static boolean movementScores() {
        try {
            return !SPEC.isLoaded() || MOVEMENT_SCORES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** First person: hands and feet through the moves (Hands P1-P3). */
    public static boolean fpMoves() {
        try {
            return !SPEC.isLoaded() || FP_MOVES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Station scenes (Hands P5). */
    public static boolean stations() {
        try {
            return !SPEC.isLoaded() || STATIONS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Station scenes: the view goes to the station's own place (SPEC §65.1). */
    public static boolean stationBench() {
        try {
            return !SPEC.isLoaded() || STATION_BENCH.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** First person: hands on the things you use (Hands P4). */
    public static boolean fpTouches() {
        try {
            return !SPEC.isLoaded() || FP_TOUCHES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean shoulderView() {
        try {
            return !SPEC.isLoaded() || SHOULDER_VIEW.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Public: the Ascent's renderer thins its streaks and motes under reduced motion. */
    public static boolean reduced() {
        try {
            return SPEC.isLoaded() && REDUCED_MOTION.get();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
