package dev.awaken.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World gameplay config (SYNCED: stored per world, sent to clients). See docs/SPEC.md section 11. */
public final class AwakenConfig {
    public enum Difficulty { STORY, HUNTER, HARDLINE, HARDCORE }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<Difficulty> DIFFICULTY = B
            .comment("What a death costs. STORY keeps half your Satchel; HUNTER loses it and keeps your gear;",
                    "HARDLINE adds Grave Echoes everywhere and the Penalty Zone; HARDCORE follows vanilla hardcore.")
            .defineEnum("difficulty", Difficulty.HUNTER);

    public static final ModConfigSpec.DoubleValue STORY_LENGTH = B
            .comment("Story length. 0.5 is about 50 hours to Sovereign, 1.0 about 100, 1.5 about 150.")
            .defineInRange("storyLength", 1.0, 0.25, 3.0);

    public static final ModConfigSpec.BooleanValue PLAYTEST_MODE = B
            .comment("Playtest mode: every 15 minutes a small prompt asks what you're doing next and how fun it is.",
                    "Answers and events go to world/awaken/playtest.jsonl on this computer only.")
            .define("playtestMode", false);

    static {
        B.push("features");
    }

    public static final ModConfigSpec.BooleanValue GATES = B.define("gates", true);
    public static final ModConfigSpec.BooleanValue DESCENT = B.define("descent", true);
    public static final ModConfigSpec.BooleanValue LUCIDIUM = B.define("lucidium", true);
    public static final ModConfigSpec.BooleanValue VEIL = B.define("veil", true);
    public static final ModConfigSpec.BooleanValue ABOVE = B.define("above", true);
    public static final ModConfigSpec.BooleanValue HEARTH = B.define("hearth", true);
    public static final ModConfigSpec.BooleanValue STORY = B.define("story", true);
    public static final ModConfigSpec.BooleanValue ANOMALIES = B.comment("Rift tears, wandering gates and spirit processions near awakened players")
            .define("anomalies", true);
    public static final ModConfigSpec.BooleanValue LANDMARKS = B.comment("Landmark discovery, Now hints, Mira's landmark rumours and night markers over ruins")
            .define("landmarks", true);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    /** XP multiplier from the story length setting (shorter story = more XP). */
    public static double xpMultiplier() {
        return 1.0 / STORY_LENGTH.get();
    }

    /** Client-only preferences (CLIENT config). */
    public static final class Client {
        private static final ModConfigSpec.Builder C = new ModConfigSpec.Builder();
        public static final ModConfigSpec.BooleanValue CAMERA_SHAKE = C
                .comment("Screen shake on heavy hits.")
                .define("cameraShake", true);
        public static final ModConfigSpec.DoubleValue SIGNAL_TYPE_SPEED = C
                .comment("How fast Signal windows type their text (characters per tick).")
                .defineInRange("signalTypeSpeed", 3.0, 0.5, 50.0);
        public static final ModConfigSpec.BooleanValue AURA = C
                .comment("Show your rank aura.")
                .define("aura", true);

        /** How the System speaks: V2 = toasts, choice cards, story lines (default); CLASSIC = the old big window. */
        public enum SystemStyle { V2, CLASSIC }

        public static final ModConfigSpec.EnumValue<SystemStyle> SYSTEM_STYLE = C
                .comment("V2: small toasts, choice cards and story lines. CLASSIC: the old centre window (rollback).")
                .defineEnum("systemStyle", SystemStyle.V2);
        public static final ModConfigSpec.BooleanValue NOW_TRACKER = C
                .comment("Show the Now tracker: one line in the top-left corner with something you could do next.")
                .define("nowTracker", true);
        public static final ModConfigSpec.DoubleValue SYSTEM_SCALE = C
                .comment("Size of System cards, story lines and the Now tracker (toasts stay vanilla-sized).")
                .defineInRange("systemScale", 1.0, 0.75, 1.5);
        public static final ModConfigSpec.BooleanValue SKIP_EXPERIMENTAL_WARNING = C
                .comment("Answer vanilla's \"experimental settings\" prompt for you. AWAKEN's dimensions come from data packs,",
                        "which vanilla calls experimental; the prompt otherwise blocks every new world.")
                .define("skipExperimentalWarning", true);
        /** AWAKEN's own light layer (dev.awaken.lens): OFF, SWIFT (horizon haze and soft bloom) or CINEMATIC (adds light shafts). */
        public enum LensLook { OFF, SWIFT, CINEMATIC }

        public static final ModConfigSpec.EnumValue<LensLook> LENS = C
                .comment("Lens, AWAKEN's light layer: OFF, SWIFT (haze, bloom and a look for each dimension; built for 120 fps)",
                        "or CINEMATIC (adds light shafts from the sun). It steps aside while an Iris shaderpack is on.")
                .defineEnum("lens", LensLook.SWIFT);
        public static final ModConfigSpec SPEC = C.build();

        private Client() {}
    }

    private AwakenConfig() {}
}
