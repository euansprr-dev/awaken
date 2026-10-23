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
        public static final ModConfigSpec SPEC = C.build();

        private Client() {}
    }

    private AwakenConfig() {}
}
