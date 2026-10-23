package dev.awaken.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every Hearth in the world, one per player. Stored with the overworld's data. */
public final class HearthData extends SavedData {
    public static final int SCHEMA = 1;

    public static final Codec<HearthData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> SCHEMA),
            HearthRecord.CODEC.listOf().fieldOf("hearths").forGetter(d -> List.copyOf(d.byOwner.values()))
    ).apply(i, HearthData::new));

    public static final SavedDataType<HearthData> TYPE = new SavedDataType<>(Awaken.id("hearths"), HearthData::new, CODEC);

    private final Map<UUID, HearthRecord> byOwner = new LinkedHashMap<>();

    public HearthData() {}

    private HearthData(int schema, List<HearthRecord> list) {
        for (HearthRecord r : list) byOwner.put(r.owner, r);
    }

    public static HearthData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<HearthRecord> all() {
        return byOwner.values();
    }

    public Optional<HearthRecord> of(UUID owner) {
        return Optional.ofNullable(byOwner.get(owner));
    }

    public Optional<HearthRecord> at(BlockPos pos, String dimension) {
        for (HearthRecord r : byOwner.values()) if (r.pos.equals(pos) && r.dimension.equals(dimension)) return Optional.of(r);
        return Optional.empty();
    }

    /** The Hearth whose radius contains {@code pos}, if any (nearest centre wins when radii overlap). */
    public Optional<HearthRecord> containing(BlockPos pos, String dimension) {
        HearthRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (HearthRecord r : byOwner.values()) {
            if (!r.dimension.equals(dimension) || !r.contains(pos)) continue;
            double d = r.pos.distSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Nearest Hearth anchor within {@code radius} blocks (horizontal distance), any owner. */
    public Optional<HearthRecord> nearest(BlockPos pos, String dimension, int radius) {
        HearthRecord best = null;
        double bestD = (double) radius * radius;
        for (HearthRecord r : byOwner.values()) {
            if (!r.dimension.equals(dimension)) continue;
            double dx = r.pos.getX() - pos.getX(), dz = r.pos.getZ() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    public void put(HearthRecord r) {
        byOwner.put(r.owner, r);
        setDirty();
    }

    public void remove(UUID owner) {
        if (byOwner.remove(owner) != null) setDirty();
    }

    public List<HearthRecord> snapshot() {
        return new ArrayList<>(byOwner.values());
    }
}
