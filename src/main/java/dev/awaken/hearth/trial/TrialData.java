package dev.awaken.hearth.trial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Which trial cells are in use, and where each tested player came from (so a crash or logout still sends them home). */
public final class TrialData extends SavedData {
    public record Return(String dimension, double x, double y, double z, float yaw) {
        public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Return::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Return::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(Return::yaw)
        ).apply(i, Return::new));
    }

    public static final Codec<TrialData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("busy").forGetter(d -> List.copyOf(d.busy)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Return.CODEC).fieldOf("returns").forGetter(d -> d.returns)
    ).apply(i, TrialData::new));

    public static final SavedDataType<TrialData> TYPE = new SavedDataType<>(Awaken.id("trials"), TrialData::new, CODEC);

    final Set<Integer> busy = new HashSet<>();
    final Map<UUID, Return> returns = new HashMap<>();

    public TrialData() {}

    private TrialData(List<Integer> busy, Map<UUID, Return> returns) {
        // Cells are never busy across a restart: sessions don't survive one.
        this.returns.putAll(returns);
    }

    public static TrialData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Lowest free cell of the right kind (desert cells are odd). */
    public int allocate(boolean desert) {
        for (int i = desert ? 1 : 0; i < TrialChunkGenerator.GRID * TrialChunkGenerator.GRID; i += 2) {
            if (busy.add(i)) {
                setDirty();
                return i;
            }
        }
        return desert ? 1 : 0;
    }

    public void free(int cell) {
        if (busy.remove(cell)) setDirty();
    }
}
