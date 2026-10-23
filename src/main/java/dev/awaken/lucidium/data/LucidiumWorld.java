package dev.awaken.lucidium.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-wide lucidium memory: every player's last known bed, so the Day Journal never snapshots someone else's
 * base, even while they are offline.
 */
public final class LucidiumWorld extends SavedData {
    public static final Codec<LucidiumWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", 1).forGetter(w -> 1),
            Codec.unboundedMap(Codec.STRING, GlobalPos.CODEC).optionalFieldOf("beds", Map.of()).forGetter(w -> w.beds)
    ).apply(i, (schema, beds) -> new LucidiumWorld(beds)));

    public static final SavedDataType<LucidiumWorld> TYPE = new SavedDataType<>(Awaken.id("lucidium"), LucidiumWorld::new, CODEC);

    private final Map<String, GlobalPos> beds = new HashMap<>();

    public LucidiumWorld() {}

    private LucidiumWorld(Map<String, GlobalPos> beds) {
        this.beds.putAll(beds);
    }

    public static LucidiumWorld get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void setBed(UUID player, GlobalPos pos) {
        GlobalPos old = beds.put(player.toString(), pos);
        if (!pos.equals(old)) setDirty();
    }

    /** True if another player's bed is within {@code radius} blocks (same dimension). */
    public boolean otherBedNear(UUID self, ResourceKey<Level> dim, BlockPos pos, int radius) {
        long r2 = (long) radius * radius;
        for (var e : beds.entrySet()) {
            if (e.getKey().equals(self.toString())) continue;
            GlobalPos g = e.getValue();
            if (g.dimension().equals(dim) && g.pos().distSqr(pos) <= r2) return true;
        }
        return false;
    }
}
