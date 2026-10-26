package dev.awaken.delve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

/**
 * What the deep remembers, per dimension (schemaVersion 1): the lodes whose heart has been broken (a heart gives once,
 * for everyone: lodes are shared) and the 16-block sections where a player has built with solid blocks underground
 * (a roof you built or propped never caves in).
 */
public final class DelveData extends SavedData {
    static final int SCHEMA = 1;
    /** Building above this is never under a lode's roof: no need to remember it. */
    static final int BUILT_BELOW_Y = 64;

    public static final Codec<DelveData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(d -> SCHEMA),
            Codec.LONG.listOf().optionalFieldOf("brokenHearts", List.of()).forGetter(d -> new LongArrayList(d.broken)),
            Codec.LONG.listOf().optionalFieldOf("builtSections", List.of()).forGetter(d -> new LongArrayList(d.built))
    ).apply(i, DelveData::new));

    public static final SavedDataType<DelveData> TYPE = new SavedDataType<>(Awaken.id("delve"), DelveData::new, CODEC);

    private final LongOpenHashSet broken = new LongOpenHashSet();
    private final LongOpenHashSet built = new LongOpenHashSet();

    public DelveData() {}

    private DelveData(int schema, List<Long> broken, List<Long> built) {
        this.broken.addAll(broken);
        this.built.addAll(built);
    }

    public static DelveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Records that this lode's heart broke; false if it already had. */
    boolean breakHeart(long lode) {
        if (!broken.add(lode)) return false;
        setDirty();
        return true;
    }

    public boolean heartBroken(long lode) {
        return broken.contains(lode);
    }

    void built(BlockPos pos) {
        if (pos.getY() >= BUILT_BELOW_Y) return;
        if (built.add(SectionPos.asLong(pos))) setDirty();
    }

    /** Whether a player has built with solid blocks in the section holding {@code pos}. */
    public boolean builtAt(BlockPos pos) {
        return built.contains(SectionPos.asLong(pos));
    }

    /** Tests: forget a lode's heart and the building near a spot. */
    void forget(long lode, BlockPos near) {
        broken.remove(lode);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            built.remove(SectionPos.asLong(near.offset(dx * 16, dy * 16, dz * 16)));
        }
    }
}
