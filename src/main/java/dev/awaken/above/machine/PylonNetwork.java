package dev.awaken.above.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Every Waypoint Pylon in the world and who placed it (world-global SavedData). */
public final class PylonNetwork extends SavedData {
    public record Entry(UUID owner, GlobalPos pos, String name) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.STRING.fieldOf("name").forGetter(Entry::name)
        ).apply(i, Entry::new));
    }

    public static final Codec<PylonNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", 1).forGetter(n -> 1),
            Entry.CODEC.listOf().fieldOf("pylons").forGetter(n -> List.copyOf(n.entries))
    ).apply(i, (schema, list) -> new PylonNetwork(list)));

    public static final SavedDataType<PylonNetwork> TYPE = new SavedDataType<>(Awaken.id("above_pylons"), PylonNetwork::new, CODEC);

    private final List<Entry> entries = new ArrayList<>();

    public PylonNetwork() {}

    private PylonNetwork(List<Entry> list) {
        entries.addAll(list);
    }

    public static PylonNetwork get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(UUID owner, ServerLevel level, BlockPos pos, String name) {
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        entries.removeIf(e -> e.pos().equals(gp));
        int n = 1;
        for (Entry e : entries) if (e.owner().equals(owner)) n++;
        entries.add(new Entry(owner, gp, name == null || name.isEmpty() ? "Pylon " + n : name));
        setDirty();
    }

    public void remove(ServerLevel level, BlockPos pos) {
        GlobalPos gp = GlobalPos.of(level.dimension(), pos);
        if (entries.removeIf(e -> e.pos().equals(gp))) setDirty();
    }

    public void removeAt(GlobalPos gp) {
        if (entries.removeIf(e -> e.pos().equals(gp))) setDirty();
    }

    public List<Entry> of(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.owner().equals(owner)) out.add(e);
        return out;
    }
}
