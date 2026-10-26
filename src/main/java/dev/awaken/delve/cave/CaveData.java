package dev.awaken.delve.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/**
 * What the cave places remember, per dimension (schemaVersion 1): the seeps whose heart has fallen (a quiet seep stays
 * quiet for everyone) and the sinkholes whose bats flew out on which play-day.
 */
public final class CaveData extends SavedData {
    static final int SCHEMA = 1;

    public static final Codec<CaveData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(d -> SCHEMA),
            Codec.LONG.listOf().optionalFieldOf("quietSeeps", List.of()).forGetter(d -> new LongArrayList(d.quiet))
    ).apply(i, CaveData::new));

    public static final SavedDataType<CaveData> TYPE = new SavedDataType<>(Awaken.id("delve_caves"), CaveData::new, CODEC);

    private final LongOpenHashSet quiet = new LongOpenHashSet();

    public CaveData() {}

    private CaveData(int schema, List<Long> quiet) {
        this.quiet.addAll(quiet);
    }

    public static CaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean quiet(long seep) {
        return quiet.contains(seep);
    }

    /** The seep's heart fell; false if it already had. */
    boolean quieten(long seep) {
        if (!quiet.add(seep)) return false;
        setDirty();
        return true;
    }

    /** Tests and commands: the seep wakes again. */
    void wake(long seep) {
        if (quiet.remove(seep)) setDirty();
    }
}
