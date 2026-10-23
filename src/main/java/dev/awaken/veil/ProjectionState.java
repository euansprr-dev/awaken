package dev.awaken.veil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The saved half of a projection: just enough to put the player back in their body after a logout, crash or restart.
 * The live half (ticks left, the Body entity, Cord-Eaters...) lives in {@link Projection} and is never saved.
 */
public record ProjectionState(int schemaVersion, boolean active, GameType previousMode, ResourceKey<Level> bodyDim, Vec3 bodyPos,
                              float bodyYaw, Optional<UUID> bodyId, float health, long startedAt) {
    public static final int VERSION = 1;

    public static final ProjectionState NONE = new ProjectionState(VERSION, false, GameType.SURVIVAL, Level.OVERWORLD, Vec3.ZERO, 0F,
            Optional.empty(), 20F, 0L);

    public static final MapCodec<ProjectionState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema_version", VERSION).forGetter(ProjectionState::schemaVersion),
            Codec.BOOL.fieldOf("active").forGetter(ProjectionState::active),
            GameType.CODEC.optionalFieldOf("previous_mode", GameType.SURVIVAL).forGetter(ProjectionState::previousMode),
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("body_dim", Level.OVERWORLD).forGetter(ProjectionState::bodyDim),
            Vec3.CODEC.optionalFieldOf("body_pos", Vec3.ZERO).forGetter(ProjectionState::bodyPos),
            Codec.FLOAT.optionalFieldOf("body_yaw", 0F).forGetter(ProjectionState::bodyYaw),
            UUIDUtil.CODEC.optionalFieldOf("body_id").forGetter(ProjectionState::bodyId),
            Codec.FLOAT.optionalFieldOf("health", 20F).forGetter(ProjectionState::health),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(ProjectionState::startedAt)
    ).apply(i, ProjectionState::new));

    public ProjectionState withBody(Vec3 pos, float yaw, float health) {
        return new ProjectionState(schemaVersion, active, previousMode, bodyDim, pos, yaw, bodyId, health, startedAt);
    }
}
