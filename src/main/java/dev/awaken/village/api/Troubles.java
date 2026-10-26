package dev.awaken.village.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Village troubles as other modules see them (SPEC §12, §28.7): who a player brought home. A person cut free from a
 * lair (Taken) or found on a night search (Gone Missing) and walked back to their village's square is remembered as
 * rescued by the player who brought them, and by every party member within 48 of the reunion. W6's Hearth reads it to
 * let them settle. Server side; before the village module installs itself every call answers "none".
 */
public final class Troubles {
    private Troubles() {}

    /**
     * Someone a player brought home: their village (key and name), their record in that settlement's roster
     * ({@code Settlement.personName/job/age(rec)} answer for it while the village runs), their name ("Bren Ashdown"),
     * their trade (a {@code Roster.JOBS} name: "farmer", "miner", "hunter", "resident"...), their age class (0 a child
     * .. 3 old), the play-day they came home, which trouble it was ("taken", "missing"), and whether they have since
     * settled at someone's Hearth ({@link #settle}).
     */
    public record Rescued(String village, String villageName, int rec, String name, String trade, int age, long day, String trouble,
                          boolean settled) {}

    public interface Access {
        List<Rescued> rescued(ServerPlayer player);

        void settle(ServerPlayer player, Rescued who, String hearthName);

        default boolean raidComing(ServerLevel level, String village, int camp, String campName, int tier, long raidDay) {
            return false;
        }

        default boolean lairSealed(ServerLevel level, BlockPos den) {
            return false;
        }
    }

    private static @Nullable Access access;

    public static void install(Access a) {
        access = a;
    }

    /** Everyone this player brought home, oldest first (a child is listed too: W6 decides who can move). */
    public static List<Rescued> rescued(ServerPlayer player) {
        return access == null ? List.of() : access.rescued(player);
    }

    /**
     * W6: a rescued person moved to a player's Hearth. The village remembers it in its history ("Bren went to live at
     * Ashford with Kestrel") and {@link #rescued} answers with {@code settled} true from then on.
     */
    public static void settle(ServerPlayer player, Rescued who, String hearthName) {
        if (access != null) access.settle(player, who, hearthName);
    }

    /**
     * Living Wilds' camps (§21.8): a camp grown to raid has a village nearer than any Hearth. True when the village takes
     * it as its Burning (its ladder of warnings, the raid on {@code raidDay}'s night run by the village); false (a calm
     * world, a village already in trouble) and the camp raids as it always did.
     */
    public static boolean raidComing(ServerLevel level, String village, int camp, String campName, int tier, long raidDay) {
        return access != null && access.raidComing(level, village, camp, campName, tier, raidDay);
    }

    /** Fauna's lairs: a lair a village's Taken ended in is cleared for good (its beast never comes back). */
    public static boolean lairSealed(ServerLevel level, BlockPos den) {
        return access != null && access.lairSealed(level, den);
    }
}
