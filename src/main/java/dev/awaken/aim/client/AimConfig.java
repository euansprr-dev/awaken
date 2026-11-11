package dev.awaken.aim.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Lock-on's client settings (SPEC §40.8), in their own file ({@code config/awaken-aim-client.toml}). */
public final class AimConfig {
    private AimConfig() {}

    public enum Camera {
        /** The view keeps the locked creature in the middle (eased, at most 300°/s). */
        FOLLOW,
        /** The view turns to the creature once, when you lock (Monster Hunter's target camera); then it's yours. */
        SNAP,
        /** The view is never turned; the lock still marks, steers strikes and bends powers. */
        OFF
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Lock-on: the creature nearest the middle of your view is marked, and the lock key (middle mouse) locks it.",
                    "Off, strikes go only where the crosshair is, as before.")
            .define("enabled", true);
    static final ModConfigSpec.EnumValue<Camera> CAMERA = B
            .comment("While locked: FOLLOW keeps the creature in the middle, SNAP turns to it once when you lock, OFF never turns",
                    "the view. Reduced motion uses SNAP in place of FOLLOW.")
            .defineEnum("camera", Camera.FOLLOW);
    static final ModConfigSpec.BooleanValue FLICK = B
            .comment("A quick flick of the mouse moves the lock to the next creature that way (the wheel always does).")
            .define("flick_switch", true);
    static final ModConfigSpec.BooleanValue MARK = B
            .comment("Show the mark's corner brackets on the creature nearest the middle while you hold a weapon or fight.")
            .define("show_mark", true);
    static final ModConfigSpec.BooleanValue THREATS = B
            .comment("A creature winding up a blow out of your view puts an arc on that edge of the screen, in its tell's colour.")
            .define("threat_arcs", true);
    static final ModConfigSpec.BooleanValue BOSS_RING = B
            .comment("A boss that fights you shows the ring that closes on its blows whenever it is on screen, with no lock or mark on it.")
            .define("boss_ring", true);
    static final ModConfigSpec.BooleanValue WORDS = B
            .comment("After a guard that missed, the ring says why for a moment: \"Too early\", \"Too late\", \"Raised again too soon\",",
                    "\"It came from the side\" or \"Dodge that one\".")
            .define("timing_words", true);
    static final ModConfigSpec.BooleanValue SOUNDS = B
            .comment("The lock's own sounds: taken, moved, released, and the ring breaking when the creature falls.")
            .define("sounds", true);
    static final ModConfigSpec SPEC = B.build();

    private static volatile boolean enabled = true, flick = true, mark = true, threats = true, sounds = true, bossRing = true, words = true;
    private static volatile Camera camera = Camera.FOLLOW;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-aim-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Lock-on settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            enabled = ENABLED.get();
            camera = CAMERA.get();
            flick = FLICK.get();
            mark = MARK.get();
            threats = THREATS.get();
            sounds = SOUNDS.get();
            bossRing = BOSS_RING.get();
            words = WORDS.get();
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Lock-on settings could not be read; using defaults", e);
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    static Camera camera() {
        return camera;
    }

    static boolean flick() {
        return flick;
    }

    static boolean mark() {
        return mark;
    }

    static boolean threats() {
        return threats;
    }

    static boolean bossRing() {
        return bossRing;
    }

    static boolean words() {
        return words;
    }

    static boolean sounds() {
        return sounds;
    }
}
