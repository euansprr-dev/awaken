package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Hearth lookups for other modules (break warnings, respawn, colour restoration, who shares a home). Installed by the
 * hearth module. A Hearth has an owner and members (SPEC "Together"); "belongs" means either.
 */
public final class HearthHooks {
    private HearthHooks() {}

    public interface Lookup {
        Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius);
        /** The player's home Anchor: the Hearth they own, else the first one they are a member of. */
        Optional<BlockPos> hearthOf(ServerLevel level, UUID player);
        /** Inside any Hearth's own radius (it grows with the Hearth's tier). */
        default boolean inside(ServerLevel level, BlockPos pos) { return false; }
        /** Whether this player owns or is a member of the Hearth anchored at {@code anchor}. */
        default boolean belongs(ServerLevel level, UUID player, BlockPos anchor) {
            return hearthOf(level, player).map(anchor::equals).orElse(false);
        }
        /** Every Hearth Anchor this player owns or is a member of (their own first). */
        default List<BlockPos> hearthsOf(ServerLevel level, UUID player) {
            return hearthOf(level, player).map(List::of).orElse(List.of());
        }
        /** Everyone who shares a Hearth with this player (owners and members of each Hearth they belong to), never the player. */
        default Set<UUID> household(ServerLevel level, UUID player) { return Set.of(); }
        /** Every Hearth Anchor whose centre is within {@code radius} blocks (horizontally) of {@code pos}. */
        default List<BlockPos> within(ServerLevel level, BlockPos pos, int radius) {
            return nearest(level, pos, radius).map(List::of).orElse(List.of());
        }
        /** Whether Deep Tide nights stay calm at {@code pos} (a Hearth with a bell tower, SPEC §28.2). */
        default boolean tideCalm(ServerLevel level, BlockPos pos) { return false; }
        /** Essence a machine at {@code pos} pays for {@code amount} (a Workshop at home pays a quarter less). */
        default int machineCost(ServerLevel level, BlockPos pos, int amount) { return amount; }
        /**
         * Whether {@code pos} lies in a Greenhouse room at a Hearth (herbs there ripen three times as often, SPEC §28.8).
         * Both sides: a server reads every Hearth's rooms; a client its own home's, as synced (so its glint agrees).
         */
        default boolean greenhouse(net.minecraft.world.level.Level level, BlockPos pos) { return false; }
        /** Whether the player stands at a Hearth they belong to that has a Study: Blueprints take half the paper (SPEC §28.8). */
        default boolean study(ServerPlayer player) { return false; }
        /**
         * Moves the player's respawn point for one of our own systems; {@code lent}: a bed for the next fall only (an inn
         * room), else the point given back. The Hearth never reads either as the player choosing another bed (SPEC §28.10).
         */
        default void systemRespawn(ServerPlayer player, ServerPlayer.@Nullable RespawnConfig to, boolean lent) {
            player.setRespawnPosition(to, false);
        }
        /** The metal the player's home is short of for its next tier, when only materials stand in the way (SPEC §34.1). */
        default Optional<MetalNeed> metalNeed(ServerPlayer player) { return Optional.empty(); }
    }

    /**
     * A home short of a metal: which ({@code iron}, {@code copper}, {@code gold}), how many ingots the next tier takes
     * (a block counts nine), how many the player carries, what for ("Hearth Tier II"), and the home's Anchor.
     */
    public record MetalNeed(String metal, int need, int have, String what, BlockPos home) {}

    private static Lookup lookup = new Lookup() {
        @Override public Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius) { return Optional.empty(); }
        @Override public Optional<BlockPos> hearthOf(ServerLevel level, UUID owner) { return Optional.empty(); }
    };

    public static void install(Lookup l) {
        lookup = l;
    }

    /** The installed lookup (tests swap it in and out in try/finally). */
    public static Lookup installed() {
        return lookup;
    }

    /** Whether {@code pos} lies inside any Hearth's radius (exact, by tier). */
    public static boolean inside(ServerLevel level, BlockPos pos) {
        return lookup.inside(level, pos);
    }

    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius) {
        return lookup.nearest(level, pos, radius);
    }

    /** The player's home Anchor in the overworld (own Hearth, else the first shared one), if they have one. */
    public static Optional<BlockPos> hearthOf(ServerPlayer player) {
        return lookup.hearthOf(player.level().getServer().overworld(), player.getUUID());
    }

    /** Whether the player owns or shares the Hearth anchored at {@code anchor} (overworld). */
    public static boolean belongs(ServerPlayer player, BlockPos anchor) {
        return lookup.belongs(player.level().getServer().overworld(), player.getUUID(), anchor);
    }

    /** Every Hearth Anchor the player owns or shares (overworld), their own first. */
    public static List<BlockPos> hearthsOf(ServerPlayer player) {
        return lookup.hearthsOf(player.level().getServer().overworld(), player.getUUID());
    }

    /** Everyone who shares a Hearth with this player, never the player. */
    public static Set<UUID> household(ServerPlayer player) {
        return lookup.household(player.level().getServer().overworld(), player.getUUID());
    }

    /** Whether two players share at least one Hearth (one owns it and the other is a member, or both are members). */
    public static boolean shareHearth(ServerPlayer a, UUID b) {
        return !a.getUUID().equals(b) && household(a).contains(b);
    }

    /** Whether Deep Tide nights stay calm at {@code pos}: inside a Hearth whose bell tower stands. */
    public static boolean tideCalm(ServerLevel level, BlockPos pos) {
        return lookup.tideCalm(level, pos);
    }

    /** Essence a machine at {@code pos} pays for {@code amount}: a quarter less in a Workshop at home. */
    public static int machineCost(ServerLevel level, BlockPos pos, int amount) {
        return lookup.machineCost(level, pos, amount);
    }

    /** Whether {@code pos} lies in a Greenhouse at a Hearth (either side): herbs ripen three times as often there. */
    public static boolean greenhouse(net.minecraft.world.level.Level level, BlockPos pos) {
        return lookup.greenhouse(level, pos);
    }

    /** Whether the player stands at home with a Study: the Blueprints' paper is halved. */
    public static boolean study(ServerPlayer player) {
        return lookup.study(player);
    }

    /** An inn room: the player wakes at {@code bed} if they fall before it's given back, and at their Hearth after. */
    public static void lendRespawn(ServerPlayer player, ServerPlayer.RespawnConfig bed) {
        lookup.systemRespawn(player, bed, true);
    }

    /** Gives back the respawn point a lent bed stood in for ({@code null}: none). */
    public static void returnRespawn(ServerPlayer player, ServerPlayer.@Nullable RespawnConfig before) {
        lookup.systemRespawn(player, before, false);
    }

    /** The metal the player's home is short of for its next tier (only materials stand in the way), if any. */
    public static Optional<MetalNeed> metalNeed(ServerPlayer player) {
        return lookup.metalNeed(player);
    }

    /** Every Hearth Anchor within {@code radius} blocks of {@code pos}, whoever owns it. */
    public static List<BlockPos> within(ServerLevel level, BlockPos pos, int radius) {
        return lookup.within(level, pos, radius);
    }
}
