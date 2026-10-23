package dev.awaken.hearth;

import dev.awaken.hearth.echo.Echoes;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Public API of the hearth module for other modules (lucidium reads Sanctuaries, powers' echo_swap reads Echoes).
 * Server side only; every call is cheap.
 */
public final class Hearth {
    private Hearth() {}

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

    /** Whether this player's Hearth has at least one room of this type. */
    public static boolean hasRoom(ServerPlayer player, RoomType type) {
        return HearthService.hearthOf(player).map(h -> h.hasRoom(type)).orElse(false);
    }

    /** The player's live field Echoes (summoned, alive, in the player's level). For echo_swap and friends. */
    public static List<LivingEntity> ownedEchoes(ServerPlayer player) {
        return Echoes.fieldEntities(player);
    }

    /** The player's Hearth Anchor position, if they have one (always in the overworld). */
    public static Optional<BlockPos> anchor(ServerPlayer player) {
        return HearthService.hearthOf(player).map(h -> h.pos);
    }

    /** Whether {@code pos} lies inside this player's Hearth radius. */
    public static boolean atHome(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD
                && HearthService.hearthOf(player).map(h -> h.contains(player.blockPosition())).orElse(false);
    }
}
