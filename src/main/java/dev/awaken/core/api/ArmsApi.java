package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * What the rest of the game may ask of Arms about patterns (docs/arms-progression/PLAN.md §4 "Public API"): which styles
 * a fighter knows, teaching one, and Hale's issue. Arms installs it at start; before that, and with Arms absent, nothing
 * is known and nothing is taught. Never reach into the arms package for these.
 */
public final class ArmsApi {
    private ArmsApi() {}

    /** Arms' side of the API. */
    public interface Hooks {
        /** Whether this fighter has the style's pattern (the Longsword is everyone's). */
        boolean knows(ServerPlayer player, String style);

        /** Teaches the style: it is known at once and its Blueprint goes into the pack. False if it was already known. */
        boolean learn(ServerPlayer player, String style, String source);

        /** Whether Hale's issue is waiting for this fighter. */
        boolean issuePending(ServerPlayer player);

        /** Opens the pick of a first weapon on the fighter's screen. */
        void openIssue(ServerPlayer player);

        /** Whether the fighter has taken the Association's issue (Hale shows the rack to those who have). */
        boolean issueTaken(ServerPlayer player);

        /** Opens the Armoury at Hale's rack. */
        void openRack(ServerPlayer player);

        /** A gate creature spawned: on the main gate's first floor the first elite or the third creature becomes the bearer of the gate's pattern. */
        void gateSpawn(net.minecraft.world.entity.LivingEntity mob, String gateKey, int area, int floor, boolean elite);

        /** This creature carries its theme's pattern (a riftborn camp's heart), claimed once per fighter under the key. */
        void markBearer(net.minecraft.world.entity.LivingEntity mob, String claimKey);

        /** The Lord's hoard carries the gate's pattern for a fighter who never downed the bearer. */
        void lordPattern(ServerPlayer player, String gateKey, String themeId);

        /** What a gate's card says of the pattern its first floor carries, or null (known, or none). */
        @org.jspecify.annotations.Nullable String patternLine(ServerPlayer player, String themeId);

        /** A chain of the living module ended: the person who teaches a style at its end pays it. */
        void questDone(ServerPlayer player, String chain);

        /** A fighter in a main gate's Lord room: their roll for Spoils, a Marked weapon of the Lord's style (docs/arms-progression/PLAN.md §3.7). */
        void lordSpoils(ServerPlayer player, String rankLabel, String themeId);

        /** A Lord spawned: it holds its own Lord-forged weapon in its hand, glinting, so you can see what you fight for. */
        void lordWields(net.minecraft.world.entity.LivingEntity lord);

        /** Places a Pattern Stand: its make ("rack", "lectern", "plinth", "bier"), facing, and the pattern its rank's band draws by the structure's seed. False if nothing could be placed. */
        boolean placeStand(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing, String kind, String rankLabel, long seed);

        /** The rank label ("E" to "S") of a level, by the plate bands. */
        String rankOfLevel(int level);

        /** The patterns a village smith sells this fighter: two of the village's rank, drawn by its seed, never one they know. */
        java.util.List<VillageOffer> villagePatterns(ServerPlayer player, long seed, int landLevel);

        /** The smith's pattern was bought: it is known at once. */
        void villageBought(ServerPlayer player, String style);

        /** Hale's rack stands beside him (two racks that hang the Wildhide weapons and open his Armoury): placed if there is none near, true when one stands. */
        boolean placeHaleRack(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos hale);
    }

    /** One pattern on a village smith's rack: the style, its Blueprint to hand over, and its price in emeralds (before standing). */
    public record VillageOffer(String style, net.minecraft.world.item.ItemStack blueprint, int emeralds) {}

    private static Hooks hooks;
    private static BiConsumer<ServerPlayer, String> speech = (p, line) -> {};

    public static void install(Hooks h) {
        hooks = h;
    }

    public static boolean knows(ServerPlayer player, String style) {
        return hooks != null && hooks.knows(player, style);
    }

    public static boolean learn(ServerPlayer player, String style, String source) {
        return hooks != null && hooks.learn(player, style, source);
    }

    public static boolean issuePending(ServerPlayer player) {
        return hooks != null && hooks.issuePending(player);
    }

    public static void openIssue(ServerPlayer player) {
        if (hooks != null) hooks.openIssue(player);
    }

    public static boolean issueTaken(ServerPlayer player) {
        return hooks != null && hooks.issueTaken(player);
    }

    public static void openRack(ServerPlayer player) {
        if (hooks != null) hooks.openRack(player);
    }

    public static void gateSpawn(net.minecraft.world.entity.LivingEntity mob, String gateKey, int area, int floor, boolean elite) {
        if (hooks != null) hooks.gateSpawn(mob, gateKey, area, floor, elite);
    }

    public static void markBearer(net.minecraft.world.entity.LivingEntity mob, String claimKey) {
        if (hooks != null) hooks.markBearer(mob, claimKey);
    }

    public static void lordPattern(ServerPlayer player, String gateKey, String themeId) {
        if (hooks != null) hooks.lordPattern(player, gateKey, themeId);
    }

    public static @org.jspecify.annotations.Nullable String patternLine(ServerPlayer player, String themeId) {
        return hooks == null ? null : hooks.patternLine(player, themeId);
    }

    public static void questDone(ServerPlayer player, String chain) {
        if (hooks != null) hooks.questDone(player, chain);
    }

    public static void lordSpoils(ServerPlayer player, String rankLabel, String themeId) {
        if (hooks != null) hooks.lordSpoils(player, rankLabel, themeId);
    }

    public static void lordWields(net.minecraft.world.entity.LivingEntity lord) {
        if (hooks != null) hooks.lordWields(lord);
    }

    public static boolean placeStand(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing, String kind, String rankLabel, long seed) {
        return hooks != null && hooks.placeStand(level, pos, facing, kind, rankLabel, seed);
    }

    public static String rankOfLevel(int level) {
        return hooks == null ? "E" : hooks.rankOfLevel(level);
    }

    public static java.util.List<VillageOffer> villagePatterns(ServerPlayer player, long seed, int landLevel) {
        return hooks == null ? java.util.List.of() : hooks.villagePatterns(player, seed, landLevel);
    }

    public static void villageBought(ServerPlayer player, String style) {
        if (hooks != null) hooks.villageBought(player, style);
    }

    public static boolean placeHaleRack(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos hale) {
        return hooks != null && hooks.placeHaleRack(level, hale);
    }

    /** The living module says what people say ("Hale: ...") on screen; arms asks it to, without knowing how. */
    public static void speaking(BiConsumer<ServerPlayer, String> s) {
        speech = s;
    }

    public static void say(ServerPlayer player, String line) {
        speech.accept(player, line);
    }
}
