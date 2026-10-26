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
    public static final ModConfigSpec.BooleanValue WARDS = B.comment("Precursor wards: blocks placed near Precursor sites crumble after 3 s and come back,",
                    "and a site's own walls can't be broken (SPEC §17)")
            .define("wards", true);
    public static final ModConfigSpec.BooleanValue WILDS = B.comment("The Wilds: how you arrived, the Horizon line, biome discoveries and sky-only rewards (SPEC §17)")
            .define("wilds", true);

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
        public static final ModConfigSpec.BooleanValue HORIZON_LINE = C
                .comment("Show the Horizon line under the Now tracker: the nearest place worth exploring, and now and then one you",
                        "can't reach yet.")
                .define("horizonLine", true);
        /** How much the System tells you unasked (SPEC §26). */
        public enum Notifications { MINIMAL, STANDARD, GUIDED }

        public static final ModConfigSpec.EnumValue<Notifications> NOTIFICATIONS = C
                .comment("How much the System tells you unasked. STANDARD: decisions, danger and what you earned, spaced out;",
                        "small finds go quietly to the Journal (K). MINIMAL: only decisions, danger and firsts. GUIDED: small finds",
                        "show too, with shorter gaps.")
                .defineEnum("notifications", Notifications.STANDARD);
        public static final ModConfigSpec.BooleanValue TIPS = C
                .comment("Just-in-time tips: a short line in your own keys the first time something comes up, gone when you do it,",
                        "never more than a few times. Counts are kept in config/awaken-tips.json (delete it to see them again).")
                .define("tips", true);
        public static final ModConfigSpec.DoubleValue SYSTEM_SCALE = C
                .comment("Size of System cards, story lines and the Now tracker (toasts stay vanilla-sized).")
                .defineInRange("systemScale", 1.0, 0.75, 1.5);
        public static final ModConfigSpec.BooleanValue SKIP_EXPERIMENTAL_WARNING = C
                .comment("Answer vanilla's \"experimental settings\" prompt for you. AWAKEN's dimensions come from data packs,",
                        "which vanilla calls experimental; the prompt otherwise blocks every new world.")
                .define("skipExperimentalWarning", true);
        public static final ModConfigSpec.BooleanValue LENS_IN_SCENES = C
                .comment("Story scenes (the awakening's ritual, the black and the tear) turn Lens on for their length even when",
                        "Lens is off, like a cutscene, and hand the frame back after. Never while an Iris shaderpack is on.")
                .define("lensInScenes", true);
        /**
         * AWAKEN's own light layer (dev.awaken.lens): OFF, SWIFT (horizon haze and soft bloom), CINEMATIC (Lens 2: sun
         * shadows, contact shadows, AO, filmic light, light shafts) or ULTRA (the same at higher quality, SPEC §22).
         */
        public enum LensLook { OFF, SWIFT, CINEMATIC, ULTRA }

        public static final ModConfigSpec.EnumValue<LensLook> LENS = C
                .comment("Lens, AWAKEN's light layer: OFF, SWIFT (haze, bloom and a look for each dimension; built for 120 fps),",
                        "CINEMATIC (adds sun shadows, ambient occlusion, warm and cool light and light shafts; 90+ fps) or ULTRA",
                        "(the same at higher quality; 60+ fps). It steps aside while an Iris shaderpack is on.")
                .defineEnum("lens", LensLook.SWIFT);
        public static final ModConfigSpec.BooleanValue LENS_TEMPORAL = C
                .comment("Lens 2 (Cinematic and Ultra) spreads its clouds and light over frames: each pixel's clouds are worked out one",
                        "frame in four and its light every other frame, the rest carried forward from the frames before (still and",
                        "slow views look the same; a fast turn settles in a few frames). Off: every pixel every frame.")
                .define("lensTemporal", true);

        /** Far Sight, AWAKEN's own far terrain (dev.awaken.farsight, SPEC §20): how far it draws. */
        public enum FarSight { OFF, NEAR, FULL }

        public static final ModConfigSpec.EnumValue<FarSight> FAR_SIGHT = C
                .comment("Far Sight, AWAKEN's own far terrain past your render distance: mountains, forests and air to the horizon.",
                        "FULL draws to 16 km, NEAR to 4 km, OFF none. Single-player worlds; it steps aside while Distant Horizons",
                        "is installed.")
                .defineEnum("farSight", FarSight.FULL);
        public static final ModConfigSpec.BooleanValue FAR_SIGHT_WATER_EDGES = C
                .comment("While Far Sight draws, leave out the water walls at the edge of the loaded chunks (thin pale lines over the",
                        "far sea after a teleport or in fast flight). Needs Sodium; off: water meshes exactly as Sodium's.")
                .define("farSightWaterEdges", true);
        public static final ModConfigSpec.BooleanValue FAR_SIGHT_HORIZON = C
                .comment("While you stand still, Far Sight draws the land past about 500 m once into a panorama and reuses it each",
                        "frame (the same picture, a few ms of GPU back). Moving, it draws everything as before. Off: always as before.")
                .define("farSightHorizon", true);
        public static final ModConfigSpec SPEC = C.build();

        private Client() {}
    }

    private AwakenConfig() {}
}
