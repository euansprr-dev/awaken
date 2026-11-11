package dev.awaken.core.api;

import dev.awaken.core.AwakenConfig;
import net.minecraft.world.entity.player.Player;

/**
 * My fights: Full or Gentle (SPEC §70.4). One setting per player; Gentle is the same fight asked more simply of that
 * player only. Other modules ask here; the together module installs the real thing, and until then everyone is on Full.
 * Server side.
 */
public final class FightsHooks {
    private FightsHooks() {}

    public interface Access {
        /** On Gentle right now. */
        default boolean gentle(Player p) { return false; }

        /** What this player counts for when a room or a boss is sized: 1 on Full, half that on Gentle. */
        default double weight(Player p) { return 1.0; }

        /** Kneeling through a second wind, or down and waiting for a hand: not hittable, not acting. */
        default boolean kneeling(Player p) { return false; }

        /**
         * A killing blow in a gate, after Cheat Death had its chance: true if the player goes down instead (the death is
         * then cancelled by the caller) because someone of their party still stands on the floor to lift them.
         */
        default boolean goesDown(Player p) { return false; }

        /** Whether the rules for any two in one fight are on (a boss holds its target through a phrase). */
        default boolean pair() { return false; }

        /**
         * Asked by a boss as it is about to start a phrase: whom it goes for now. The answer is {@code current} unless
         * someone else of that player's party has earned its attention (SPEC §70.3) or is owed a phrase (§70.4).
         */
        default net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity nextTarget(net.minecraft.world.entity.Mob boss,
                net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity current) { return current; }

        /** Ticks a blow aimed at a player on Gentle holds longer before it comes. */
        default int gentleHold() { return 0; }

        /** How much longer the opening is after a phrase aimed at a player on Gentle. */
        default double gentleOpening() { return 1.0; }

        /** What a blow's stance damage counts for (its back turned: a quarter more). Asked by Arms. */
        default int stance(net.minecraft.world.entity.LivingEntity target, int amount, Player by) { return amount; }
    }

    /** Persistent data on a creature sized for a party: how many times its own stance it has (absent: once). */
    public static final String PARTY_STANCE = "awaken_party_stance";

    /** Marks a creature's stance as grown for this many fighters, as its health is (+50% for each beyond the first). */
    public static void sizeStance(net.minecraft.world.entity.Entity e, double fighters) {
        if (fighters > 1.0) e.getPersistentData().putDouble(PARTY_STANCE, 1.0 + 0.5 * (fighters - 1.0));
    }

    public static double partyStance(net.minecraft.world.entity.Entity e) {
        return e.getPersistentData().getDoubleOr(PARTY_STANCE, 1.0);
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static boolean gentle(Player p) {
        return access.gentle(p);
    }

    public static double weight(Player p) {
        return access.weight(p);
    }

    public static boolean kneeling(Player p) {
        return access.kneeling(p);
    }

    public static boolean goesDown(Player p) {
        return access.goesDown(p);
    }

    public static int stance(net.minecraft.world.entity.LivingEntity target, int amount, Player by) {
        return access.stance(target, amount, by);
    }

    public static boolean pair() {
        return access.pair();
    }

    public static net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity nextTarget(net.minecraft.world.entity.Mob boss,
            net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity current) {
        return access.nextTarget(boss, current);
    }

    /** On Gentle, for a boss choosing what to throw at whoever it's aiming at. */
    public static boolean gentle(net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity target) {
        return target instanceof Player p && access.gentle(p);
    }

    public static int gentleHold() {
        return access.gentleHold();
    }

    public static double gentleOpening() {
        return access.gentleOpening();
    }

    /** How many fighters these players count for together: never under one. */
    public static double fighters(Iterable<? extends Player> who) {
        double n = 0;
        for (Player p : who) n += weight(p);
        return Math.max(1.0, n);
    }

    /** What a death costs this player: Story's rules on Gentle, the world's setting otherwise. */
    public static AwakenConfig.Difficulty deathCost(Player p) {
        return gentle(p) ? AwakenConfig.Difficulty.STORY : AwakenConfig.DIFFICULTY.get();
    }
}
