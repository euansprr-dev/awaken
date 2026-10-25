package dev.awaken.living.pulse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One happening under way (SPEC §21.6): world state, shared by everyone near it. It runs along a line {@link #from} →
 * {@link #to} (a migration's route, a storm's track; both the same spot for one that stays put), its {@link #centre} is
 * where it is now (what its tell is drawn at), and it ends on its own at {@link #end}. Its creatures are listed so
 * they leave with it.
 */
public final class Happening {
    public static final Codec<Happening> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(h -> h.id),
            Codec.STRING.fieldOf("kind").forGetter(h -> h.kind),
            Vec3.CODEC.fieldOf("centre").forGetter(h -> h.centre),
            BlockPos.CODEC.fieldOf("from").forGetter(h -> h.from),
            BlockPos.CODEC.fieldOf("to").forGetter(h -> h.to),
            Codec.LONG.fieldOf("start").forGetter(h -> h.start),
            Codec.LONG.fieldOf("end").forGetter(h -> h.end),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(h -> h.seed),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(h -> h.stage),
            UUIDUtil.CODEC.listOf().optionalFieldOf("participants", List.of()).forGetter(h -> List.copyOf(h.participants)),
            UUIDUtil.CODEC.listOf().optionalFieldOf("witnessed", List.of()).forGetter(h -> List.copyOf(h.witnessed)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("data", Map.of()).forGetter(h -> Map.copyOf(h.data))
    ).apply(i, Happening::new));

    public final long id;
    public final String kind;
    public Vec3 centre;
    public final BlockPos from, to;
    public final long start, end, seed;
    public int stage;
    public final List<UUID> participants;
    public final Set<UUID> witnessed;
    public final Map<String, Integer> data;
    /** Whether its tell moved since it was last sent (not saved). */
    transient boolean changed = true;

    Happening(long id, String kind, Vec3 centre, BlockPos from, BlockPos to, long start, long end, long seed, int stage,
              List<UUID> participants, List<UUID> witnessed, Map<String, Integer> data) {
        this.id = id;
        this.kind = kind;
        this.centre = centre;
        this.from = from;
        this.to = to;
        this.start = start;
        this.end = end;
        this.seed = seed;
        this.stage = stage;
        this.participants = new ArrayList<>(participants);
        this.witnessed = new LinkedHashSet<>(witnessed);
        this.data = new HashMap<>(data);
    }

    /** 0 at the start, 1 at the end. */
    public float progress(long now) {
        return end <= start ? 1 : Math.max(0, Math.min(1, (float) (now - start) / (end - start)));
    }

    /** The point on its line at {@code t} (0..1), at {@link #from}'s height. */
    public Vec3 along(float t) {
        return new Vec3(from.getX() + (to.getX() - from.getX()) * t + 0.5, from.getY(), from.getZ() + (to.getZ() - from.getZ()) * t + 0.5);
    }

    public int get(String key) {
        return data.getOrDefault(key, 0);
    }

    public void put(String key, int value) {
        data.put(key, value);
    }
}
