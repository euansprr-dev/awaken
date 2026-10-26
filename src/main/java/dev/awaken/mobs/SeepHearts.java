package dev.awaken.mobs;

import dev.awaken.mobs.entity.SeepHeart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Public API of the mobs module for the seep heart ({@code awaken:seep_heart}, W5): the world places one at a live seep's
 * socket and listens for its death ({@code LivingDeathEvent} on {@link #type()}). See mobs.entity.SeepHeart.
 */
public final class SeepHearts {
    private SeepHearts() {}

    /** Within this many blocks of a socket, a living heart already rooted there is the one {@link #spawn} returns. */
    public static final double SAME_SOCKET = 2.0;

    /** The seep heart's entity type. */
    public static EntityType<?> type() {
        return MobTypes.type("seep_heart");
    }

    public static boolean is(@Nullable Entity e) {
        return e instanceof SeepHeart;
    }

    /**
     * Roots a seep heart at {@code socket}: the air block it stands in on the chamber floor (given the floor block itself,
     * it stands on it). It spawns asleep, facing the nearest player, at its level the normal way (a BEAST's, from the
     * region: {@code living.api.Regions.creatureLevel}), never despawns, and returns it. Idempotent: a living heart
     * already rooted within {@value #SAME_SOCKET} blocks is returned instead of a second one. Null when the socket's
     * chunk has not loaded its entities yet (ask again on a later tick) or the socket is inside solid rock.
     */
    public static @Nullable LivingEntity spawn(ServerLevel level, BlockPos socket) {
        BlockPos at = stand(level, socket);
        if (!level.hasChunkAt(at) || !level.areEntitiesLoaded(ChunkPos.pack(at))) return null;
        LivingEntity there = at(level, at);
        if (there != null) return there;
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return null;
        EntityType<? extends dev.awaken.mobs.entity.AwakenMob> type = MobTypes.type("seep_heart");
        if (type == null || !(type.create(level, EntitySpawnReason.EVENT) instanceof SeepHeart heart)) return null;
        Player near = level.getNearestPlayer(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 48.0, false);
        float yaw = near == null ? level.getRandom().nextFloat() * 360.0F
                : (float) (net.minecraft.util.Mth.atan2(near.getZ() - (at.getZ() + 0.5), near.getX() - (at.getX() + 0.5)) * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0F;
        heart.root(at, yaw);
        EventHooks.finalizeMobSpawn(heart, level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(heart);
        return heart;
    }

    /** The living seep heart rooted within {@value #SAME_SOCKET} blocks of {@code socket}, or null. */
    public static @Nullable LivingEntity at(ServerLevel level, BlockPos socket) {
        List<SeepHeart> near = level.getEntitiesOfClass(SeepHeart.class, new AABB(socket).inflate(SAME_SOCKET + 1.0), SeepHeart::isAlive);
        for (SeepHeart h : near) if (h.socket().distSqr(socket) <= SAME_SOCKET * SAME_SOCKET) return h;
        return null;
    }

    /** The block a heart stands in for this socket: the socket, or the block on top of a floor block given instead. */
    static BlockPos stand(ServerLevel level, BlockPos socket) {
        BlockState s = level.getBlockState(socket);
        if (s.isFaceSturdy(level, socket, Direction.UP) && level.getBlockState(socket.above()).getCollisionShape(level, socket.above()).isEmpty()) return socket.above();
        return socket;
    }
}
