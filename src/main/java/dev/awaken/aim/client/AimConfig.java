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

    /** When the ring says why a guard missed (SPEC §68.2). */
    public enum Words {
        /** Each word its first few times ({@link #TAUGHT}), to teach the timing; then the burst and the snap carry it. */
        FIRST_FEW,
        /** Every time. */
        ALWAYS,
        /** Never: the burst and the snap only. */
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
    static final ModConfigSpec.EnumValue<Words> WORDS = B
            .comment("After a guard that missed, the ring says why for a moment: \"Too early\", \"Too late\", \"Raised again too soon\",",
                    "\"It came from the side\" or \"Dodge that one\". FIRST_FEW says each one its first 3 times only, to teach the",
                    "timing (after that the ring's white burst and its dull grey snap carry it); ALWAYS says them every time; OFF never.")
            .defineEnum("timing_words", Words.FIRST_FEW);
    static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WORDS_SEEN = B
            .comment("How many times each of those words has been shown (word=count), kept so a word learned stays quiet.",
                    "Empty it to be taught again.")
            .defineListAllowEmpty("timing_words_seen", java.util.List.of(), () -> "", o -> o instanceof String);
    static final ModConfigSpec.BooleanValue SOUNDS = B
            .comment("The lock's own sounds: taken, moved, released, and the ring breaking when the creature falls.")
            .define("sounds", true);
    static final ModConfigSpec SPEC = B.build();

    private static volatile boolean enabled = true, flick = true, mark = true, threats = true, sounds = true, bossRing = true;
    private static volatile Camera camera = Camera.FOLLOW;
    private static volatile Words words = Words.FIRST_FEW;

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

    /** How many times each timing word shows before it goes quiet: each names a different mistake (SPEC §68.2). */
    static final int TAUGHT = 3;

    /**
     * Whether a timing word shows now, counting the showing. Under FIRST_FEW the count is kept in the settings file, so a
     * word learned stays quiet through restarts.
     */
    static boolean teach(String word) {
        Words w = words;
        if (w == Words.OFF) return false;
        if (w == Words.ALWAYS) return true;
        try {
            java.util.List<String> all = new java.util.ArrayList<>(WORDS_SEEN.get());
            int at = -1, seen = 0;
            for (int i = 0; i < all.size(); i++) {
                String e = all.get(i);
                if (!e.startsWith(word + "=")) continue;
                at = i;
                try {
                    seen = Integer.parseInt(e.substring(word.length() + 1));
                } catch (NumberFormatException ignored) {
                    seen = 0;
                }
            }
            if (seen >= TAUGHT) return false;
            String now = word + "=" + (seen + 1);
            if (at >= 0) all.set(at, now);
            else all.add(now);
            WORDS_SEEN.set(all);
            WORDS_SEEN.save();
            return true;
        } catch (RuntimeException e) {
            return true; // no settings file (a broken one): saying why is the safe side
        }
    }

    static boolean sounds() {
        return sounds;
    }
}
