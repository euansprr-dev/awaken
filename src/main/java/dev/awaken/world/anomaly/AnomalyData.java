package dev.awaken.world.anomaly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * World state for anomalies ({@code data/awaken/anomaly_state.dat}, world-global): the active anomalies, blocks still to
 * be taken down when their chunk loads (a cache whose time ran out far away), the id counter and the world switch.
 * Versioned by {@link #SCHEMA}; every field has a default so older files load.
 */
public final class AnomalyData extends SavedData {
    public static final int SCHEMA = 1;

    /** A block an ended anomaly left in a chunk that wasn't loaded; removed only if it is still ours. */
    public record Removal(String dimension, BlockPos pos) {
        static final Codec<Removal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Removal::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Removal::pos)
        ).apply(i, Removal::new));
    }

    int nextId = 1;
    boolean enabled = true;
    final List<Anomaly> active = new ArrayList<>();
    final List<Removal> removals = new ArrayList<>();

    public AnomalyData() {}

    private AnomalyData(int schema, int nextId, boolean enabled, List<Anomaly> active, List<Removal> removals) {
        this.nextId = Math.max(1, nextId);
        this.enabled = enabled;
        this.active.addAll(active);
        this.removals.addAll(removals);
    }

    public static final Codec<AnomalyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> SCHEMA),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(d -> d.nextId),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(d -> d.enabled),
            Anomaly.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(d -> List.copyOf(d.active)),
            Removal.CODEC.listOf().optionalFieldOf("removals", List.of()).forGetter(d -> List.copyOf(d.removals))
    ).apply(i, AnomalyData::new));

    public static final SavedDataType<AnomalyData> TYPE = new SavedDataType<>(Awaken.id("anomaly_state"), AnomalyData::new, CODEC);

    public static AnomalyData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public int allocateId() {
        setDirty();
        return nextId++;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        setDirty();
    }

    public List<Anomaly> active() {
        return active;
    }

    public List<Removal> removals() {
        return removals;
    }

    public @Nullable Anomaly find(int id) {
        for (Anomaly a : active) if (a.id == id) return a;
        return null;
    }

    public @Nullable Anomaly activeFor(UUID owner) {
        for (Anomaly a : active) if (a.owner.equals(owner)) return a;
        return null;
    }

    void add(Anomaly a) {
        active.add(a);
        setDirty();
    }

    boolean remove(Anomaly a) {
        boolean r = active.remove(a);
        if (r) setDirty();
        return r;
    }
}
