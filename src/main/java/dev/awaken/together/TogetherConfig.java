package dev.awaken.together;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Together's switches and Gentle's numbers (SPEC §70), in {@code awaken-together.toml}. Every stage has its own switch,
 * and with one off that stage plays exactly as before. The numbers are first values: they are tuned after two people
 * have played, never before.
 */
public final class TogetherConfig {
    private TogetherConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue PARTY = B
            .comment("Parties formed by a card on the band. Off, your party is your household and your gate run, as before.")
            .define("party", true);
    private static final ModConfigSpec.BooleanValue SEEING = B
            .comment("The party strip, the party's dots on the Atlas and pings on the lock key.")
            .define("seeing", true);
    private static final ModConfigSpec.BooleanValue PAIR = B
            .comment("Rules for any two in one fight: whose blow it is, a boss that holds its target, its back turned,",
                    "stance that grows with fighters, standing back up in gates, waking at the gate's door.")
            .define("pair", true);
    private static final ModConfigSpec.BooleanValue GENTLE = B
            .comment("My fights: Full or Gentle. Off, everyone is on Full.")
            .define("gentle", true);
    private static final ModConfigSpec.BooleanValue JOINS = B
            .comment("The Answering Peal, Match my friend, shown the way, leads from a partner's finds, the Ours page.")
            .define("joins", true);

    static {
        B.push("gentle_numbers");
    }

    private static final ModConfigSpec.DoubleValue DAMAGE = B
            .comment("What a blow does to a player on Gentle, as a share of what it would do on Full.")
            .defineInRange("damage", 0.5, 0.1, 1.0);
    private static final ModConfigSpec.IntValue WINDS = B
            .comment("Second winds a floor (or, outside a gate, until a minute of calm).")
            .defineInRange("second_winds", 3, 0, 9);
    private static final ModConfigSpec.DoubleValue WEIGHT = B
            .comment("What a player on Gentle counts for when a room or a boss is sized (a player on Full is 1).")
            .defineInRange("weight", 0.5, 0.0, 1.0);
    private static final ModConfigSpec.IntValue HOLD = B
            .comment("Ticks a boss holds a blow longer before it comes, when it is aimed at a player on Gentle.")
            .defineInRange("hold", 8, 0, 20);
    private static final ModConfigSpec.DoubleValue OPENING = B
            .comment("How much longer the opening is after a phrase aimed at a player on Gentle.")
            .defineInRange("opening", 1.5, 1.0, 3.0);
    private static final ModConfigSpec.IntValue PHRASES = B
            .comment("With a player on Full in sight, a boss gives a player on Gentle one phrase in this many.")
            .defineInRange("one_phrase_in", 4, 1, 12);
    private static final ModConfigSpec.IntValue PACK = B
            .comment("How many small monsters may be on a player on Gentle at a time.")
            .defineInRange("pack", 2, 1, 8);

    static {
        B.pop();
        B.push("pair_numbers");
    }

    private static final ModConfigSpec.DoubleValue BACK = B
            .comment("How much more a blow does that lands while the creature is in the middle of a blow at someone else.")
            .defineInRange("back_turned", 0.25, 0.0, 1.0);
    private static final ModConfigSpec.IntValue DOWN = B
            .comment("Ticks a fighter stays down, waiting to be lifted, before the death is counted.")
            .defineInRange("down_ticks", 400, 100, 1200);
    private static final ModConfigSpec.IntValue LIFT = B
            .comment("Ticks of Use held on a downed partner to lift them.")
            .defineInRange("lift_ticks", 40, 10, 200);
    private static final ModConfigSpec.DoubleValue LIFT_HEALTH = B
            .comment("Share of health a lifted fighter stands up with.")
            .defineInRange("lift_health", 0.3, 0.05, 1.0);

    static {
        B.pop();
    }

    private static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SYNCED, SPEC, Awaken.MODID + "-together.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Together's settings could not be registered; using defaults", e);
        }
    }

    // A value read before its file is loaded (very early, or a test server without it) answers with its default.
    private static boolean on(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    private static int num(ModConfigSpec.IntValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    private static double num(ModConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    public static boolean party() { return on(PARTY); }
    public static boolean seeing() { return on(SEEING); }
    public static boolean pair() { return on(PAIR); }
    public static boolean gentle() { return on(GENTLE); }
    public static boolean joins() { return on(JOINS); }

    public static double gentleDamage() { return num(DAMAGE); }
    public static int secondWinds() { return num(WINDS); }
    public static double gentleWeight() { return num(WEIGHT); }
    public static int gentleHold() { return num(HOLD); }
    public static double gentleOpening() { return num(OPENING); }
    public static int onePhraseIn() { return num(PHRASES); }
    public static int gentlePack() { return num(PACK); }
    public static double backTurned() { return num(BACK); }
    public static int downTicks() { return num(DOWN); }
    public static int liftTicks() { return num(LIFT); }
    public static double liftHealth() { return num(LIFT_HEALTH); }
}
