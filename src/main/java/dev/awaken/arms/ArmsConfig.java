package dev.awaken.arms;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Arms' world options ({@code config/awaken-arms.toml}, synced so every client predicts what the server judges).
 * {@code enabled}: off gives back vanilla melee everywhere (weapons still swing as swords); the rollback switch.
 */
public final class ArmsConfig {
    private ArmsConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Arms: guard, deflect and dodge on every melee weapon, told enemy blows, posture and the weapons' moves (SPEC §25).",
                    "Off: vanilla melee everywhere; Arms weapons swing as plain swords.")
            .define("enabled", true);
    public static final ModConfigSpec.BooleanValue DIRECTIONAL_KNOCKBACK = B
            .comment("An Arms blow pushes the way the blade went: a side cut sideways (never toward you), a thrust straight back,",
                    "an overhead pins, an uppercut lifts, a kill throws the body further (SPEC §25 \"Fight Feel\").",
                    "Off: vanilla's push, straight away from where you look.")
            .define("directionalKnockback", true);
    public static final ModConfigSpec.BooleanValue CLAIMED_HITS = B
            .comment("A hit the fighter's own game showed lands even when the creature has moved a little on the server since:",
                    "the game draws creatures a few ticks behind, so a body just knocked back can look in reach and no longer be",
                    "(SPEC §25 \"Weight in the Hand\"). Only creatures, only once a move, and only within 1.25 blocks and 20 degrees",
                    "past the move's own reach and arc. Off: only what the server's own sweep reaches is struck.")
            .define("claimedHits", true);
    public static final ModConfigSpec.BooleanValue WALLS_STOP_BLOWS = B
            .comment("A blow lands only on a body the fighter's eye can reach past every block: a line to its nearest point, its middle",
                    "or its top (SPEC §25 \"Weight in the Hand\"). Off: blows land through walls, as before.")
            .define("wallsStopBlows", true);
    public static final ModConfigSpec.BooleanValue CUT_GRASS = B
            .comment("A sweep cuts the wild grass and ferns in its way (never flowers or crops; never at a Hearth, in the city or a village).")
            .define("cutGrass", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SYNCED, SPEC, Awaken.MODID + "-arms.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Arms settings could not be registered; using defaults", e);
        }
    }

    /** Test stand-in (null: read the config). */
    static Boolean enabledOverride;

    public static boolean enabled() {
        if (enabledOverride != null) return enabledOverride;
        try {
            return !SPEC.isLoaded() || ENABLED.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean wallsStopBlows() {
        try {
            return !SPEC.isLoaded() || WALLS_STOP_BLOWS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean cutGrass() {
        try {
            return !SPEC.isLoaded() || CUT_GRASS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Test stand-in (null: read the config). */
    static Boolean claimedHitsOverride;

    public static boolean claimedHits() {
        if (claimedHitsOverride != null) return claimedHitsOverride;
        try {
            return !SPEC.isLoaded() || CLAIMED_HITS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean directionalKnockback() {
        try {
            return !SPEC.isLoaded() || DIRECTIONAL_KNOCKBACK.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
