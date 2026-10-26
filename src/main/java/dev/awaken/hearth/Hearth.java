package dev.awaken.hearth;

import dev.awaken.hearth.echo.Echoes;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the hearth module for other modules (lucidium reads Sanctuaries, powers' echo_swap reads Echoes, the
 * city moves townsfolk to a Hearth you share). Server side only; every call is cheap. A Hearth has one owner and any
 * number of members (SPEC "Together"); "belongs" and "member of" mean either.
 */
public final class Hearth {
    private Hearth() {}

    /**
     * One Hearth as other modules see it: whose it is, where its Anchor stands (always the overworld), its tier and
     * radius, and who shares it (members in the order they joined, the owner not among them).
     */
    public record HearthInfo(UUID owner, String ownerName, BlockPos anchor, int tier, int radius, List<UUID> members, Optional<UUID> foundedFrom) {
        /** Whether this player owns the Hearth or is a member of it. */
        public boolean has(UUID player) {
            return owner.equals(player) || members.contains(player);
        }
    }

    static HearthInfo info(HearthRecord h) {
        return new HearthInfo(h.owner, h.ownerName, h.pos.immutable(), h.tier, h.radius(), List.copyOf(h.members.keySet()),
                Optional.ofNullable(h.foundedFrom));
    }

    /** Every Hearth this player belongs to, as owner or member: their own first, then the others in the order they joined. */
    public static List<HearthInfo> hearthsOf(MinecraftServer server, UUID player) {
        List<HearthInfo> out = new ArrayList<>(2);
        for (HearthRecord h : HearthData.get(server).hearthsOf(player)) out.add(info(h));
        return out;
    }

    /** Every Hearth this player belongs to (see {@link #hearthsOf(MinecraftServer, UUID)}). */
    public static List<HearthInfo> hearthsOf(ServerPlayer player) {
        return hearthsOf(player.level().getServer(), player.getUUID());
    }

    /** The Hearth this player lives at: their own, else the first they are a member of. */
    public static Optional<HearthInfo> home(ServerPlayer player) {
        return HearthService.homeOf(player).map(Hearth::info);
    }

    /** Whether this player owns or is a member of the Hearth whose Anchor stands at {@code anchor}. */
    public static boolean isMember(ServerPlayer player, BlockPos anchor) {
        return HearthData.get(player.level().getServer()).at(anchor, HearthService.OVERWORLD).map(h -> h.belongs(player.getUUID())).orElse(false);
    }

    /** Whether {@code player} owns or is a member of the Hearth owned by {@code hearthOwner} (works for offline players). */
    public static boolean isMember(MinecraftServer server, UUID player, UUID hearthOwner) {
        return HearthData.get(server).of(hearthOwner).map(h -> h.belongs(player)).orElse(false);
    }

    /** The Hearth whose radius holds {@code pos} (nearest Anchor where radii overlap), whoever's it is. */
    public static Optional<HearthInfo> hearthAt(ServerLevel level, BlockPos pos) {
        return HearthData.get(level.getServer()).containing(pos, level.dimension().identifier().toString()).map(Hearth::info);
    }

    /** Whether {@code pos} is inside a detected Sanctuary (a bed and a Meditation Mat in one enclosed room). */
    public static boolean isSanctuary(Level level, BlockPos pos) {
        return inRoom(level, pos, RoomType.SANCTUARY);
    }

    /** Whether {@code pos} is inside a detected room of this type in any Hearth. */
    public static boolean inRoom(Level level, BlockPos pos, RoomType type) {
        if (!(level instanceof ServerLevel sl) || !HearthService.enabled()) return false;
        return HearthData.get(sl.getServer()).containing(pos, sl.dimension().identifier().toString())
                .flatMap(h -> h.roomAt(pos, type)).isPresent();
    }

    /** Whether this player's home Hearth (own or shared) has at least one room of this type. */
    public static boolean hasRoom(ServerPlayer player, RoomType type) {
        return HearthService.homeOf(player).map(h -> h.hasRoom(type)).orElse(false);
    }

    /** The player's live field Echoes (summoned, alive, in the player's level). For echo_swap and friends. */
    public static List<LivingEntity> ownedEchoes(ServerPlayer player) {
        return Echoes.fieldEntities(player);
    }

    /** The player's home Anchor position (own Hearth, else the first shared one), if they have one (always in the overworld). */
    public static Optional<BlockPos> anchor(ServerPlayer player) {
        return HearthService.homeOf(player).map(h -> h.pos);
    }

    /** Whether the player stands inside the radius of a Hearth they belong to. */
    public static boolean atHome(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD && HearthService.sharedAt(player, player.blockPosition()).isPresent();
    }

    /**
     * (W6, SPEC §28.8) A home grown into a village (Tier IV, or a town at V): its name (on the Atlas), its owner, its Anchor
     * and land, how many live there, and where a festival would gather (its hall's middle, else the Anchor's yard) and
     * its bell tower. The hook W4's festival and W7's caravans use; the people engine's settlement for it is
     * {@code hearth:<owner>} (Settlements).
     */
    public record Village(UUID owner, String name, BlockPos anchor, int radius, int tier, int people, BlockPos gather,
                          Optional<BlockPos> bell) {}

    public static List<Village> villages(net.minecraft.server.MinecraftServer server) {
        List<Village> out = new java.util.ArrayList<>();
        for (HearthRecord h : HearthData.get(server).all()) {
            if (!TierRules.village(h)) continue;
            BlockPos gather = h.pos;
            Optional<BlockPos> bell = Optional.empty();
            for (dev.awaken.hearth.room.Room r : h.rooms) {
                if (r.types().contains(RoomType.HALL)) gather = r.centre();
                if (r.types().contains(RoomType.BELL_TOWER)) bell = Optional.of(r.centre());
            }
            out.add(new Village(h.owner, h.displayName(), h.pos, h.radius(), h.tier, h.folk.living(), gather, bell));
        }
        return out;
    }
}
