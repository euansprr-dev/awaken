package dev.awaken.world.fauna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * A player's mount (attachment {@code awaken:fauna_mount}, kept through death, schema 1): which creature answers their
 * whistle, what kind it is, and where it was last seen (dimension, position, resting or not). The mount itself saves
 * its owner, its trust and its bridle ({@code WildMount.Bond}); this is the owner's side, for the whistle and the Now
 * tracker while the mount's chunk sleeps.
 */
public record MountBond(int schema, Optional<UUID> mount, String kind, BlockPos last, String dimension, boolean resting) {
    public static final int SCHEMA = 1;
    public static final MountBond EMPTY = new MountBond(SCHEMA, Optional.empty(), "", BlockPos.ZERO, "", false);

    public static final MapCodec<MountBond> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema").orElse(SCHEMA).forGetter(MountBond::schema),
            UUIDUtil.CODEC.optionalFieldOf("mount").forGetter(MountBond::mount),
            Codec.STRING.optionalFieldOf("kind", "").forGetter(MountBond::kind),
            BlockPos.CODEC.optionalFieldOf("last", BlockPos.ZERO).forGetter(MountBond::last),
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(MountBond::dimension),
            Codec.BOOL.optionalFieldOf("resting", false).forGetter(MountBond::resting)
    ).apply(i, MountBond::new));

    public boolean has() {
        return mount.isPresent();
    }

    public boolean is(UUID id) {
        return mount.isPresent() && mount.get().equals(id);
    }

    public MountBond seen(BlockPos at, String dim, boolean rest) {
        return new MountBond(SCHEMA, mount, kind, at.immutable(), dim, rest);
    }

    public static MountBond of(UUID id, String kind, BlockPos at, String dim, boolean rest) {
        return new MountBond(SCHEMA, Optional.of(id), kind, at.immutable(), dim, rest);
    }
}
