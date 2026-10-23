package dev.awaken.world.fauna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where the world's named beasts roam (one per region, the Mowzie's Mobs pattern), saved with the overworld so the
 * Now tracker can point at a beast whose chunk isn't loaded, and so beasts stay rare: a cooldown between new beasts,
 * a minimum spacing, and a world-wide ceiling. An entry is removed when its beast dies or is discarded. Beasts waiting
 * in their lairs (P2) are listed too, flagged: they keep roaming beasts away (the spacing) but never use up the roaming
 * cooldown or the ceiling.
 */
public final class BeastRegistry extends SavedData {
    public static final int SCHEMA = 2;
    /** Game ticks between two new beasts in one world (30 minutes). */
    public static final long COOLDOWN = 20L * 60 * 30;
    /** No new beast within this many blocks of a known one. */
    public static final int SPACING = 384;
    /** Living roaming beasts the world remembers at most (lair beasts aside). */
    public static final int MAX = 12;

    public record Entry(UUID id, String def, String dimension, BlockPos pos, long seen, boolean lair) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("def").forGetter(Entry::def),
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Entry::seen),
                Codec.BOOL.optionalFieldOf("lair", false).forGetter(Entry::lair)
        ).apply(i, Entry::new));
    }

    public static final Codec<BeastRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(r -> SCHEMA),
            Codec.LONG.optionalFieldOf("last_spawn", Long.MIN_VALUE / 2).forGetter(r -> r.lastSpawn),
            Entry.CODEC.listOf().optionalFieldOf("beasts", List.of()).forGetter(r -> List.copyOf(r.byId.values()))
    ).apply(i, BeastRegistry::new));

    public static final SavedDataType<BeastRegistry> TYPE = new SavedDataType<>(Awaken.id("fauna_beasts"), BeastRegistry::new, CODEC);

    private final Map<UUID, Entry> byId = new LinkedHashMap<>();
    private long lastSpawn = Long.MIN_VALUE / 2;
    /** A spawn the rules just allowed but the beast hasn't reported yet (not saved). */
    private long pendingUntil;

    public BeastRegistry() {}

    private BeastRegistry(int schema, long lastSpawn, List<Entry> list) {
        this.lastSpawn = lastSpawn;
        for (Entry e : list) byId.put(e.id(), e);
    }

    public static BeastRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** True the first time this beast reports (a new beast). Positions are saved when they moved a few blocks. */
    public boolean seen(UUID id, String def, String dimension, BlockPos pos, long now) {
        return seen(id, def, dimension, pos, now, false);
    }

    /** As above; a beast that waits in its lair doesn't start the roaming cooldown. */
    public boolean seen(UUID id, String def, String dimension, BlockPos pos, long now, boolean lair) {
        Entry old = byId.get(id);
        byId.put(id, new Entry(id, def, dimension, pos.immutable(), now, lair));
        if (old == null) {
            if (!lair) {
                lastSpawn = now;
                pendingUntil = 0;
            }
            setDirty();
            return true;
        }
        if (old.pos().distSqr(pos) > 16 || !old.dimension().equals(dimension)) setDirty();
        return false;
    }

    public void gone(UUID id) {
        if (byId.remove(id) != null) setDirty();
    }

    public boolean has(UUID id) {
        return byId.containsKey(id);
    }

    /** Roaming beasts alive (lair beasts aside). */
    public int roaming() {
        int n = 0;
        for (Entry e : byId.values()) if (!e.lair()) n++;
        return n;
    }

    /** Whether a new beast may spawn at pos now: cooldown passed, nothing pending, none within the spacing, room left. */
    public boolean roomFor(String dimension, BlockPos pos, long now) {
        if (now < pendingUntil || now - lastSpawn < COOLDOWN || roaming() >= MAX) return false;
        long s2 = (long) SPACING * SPACING;
        for (Entry e : byId.values()) {
            if (!e.dimension().equals(dimension)) continue;
            long dx = e.pos().getX() - pos.getX(), dz = e.pos().getZ() - pos.getZ();
            if (dx * dx + dz * dz < s2) return false;
        }
        return true;
    }

    /** The spawn rules let a beast through: hold the slot for five seconds until it reports in. */
    public void reserve(long now) {
        pendingUntil = now + 100;
    }

    public @Nullable Entry nearest(String dimension, BlockPos pos, double radius) {
        Entry best = null;
        double bestD = radius * radius;
        for (Entry e : byId.values()) {
            if (!e.dimension().equals(dimension)) continue;
            double dx = e.pos().getX() - pos.getX(), dz = e.pos().getZ() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    public List<Entry> all() {
        return new ArrayList<>(byId.values());
    }

    public long lastSpawn() {
        return lastSpawn;
    }

    /** Admin and tests: forget the cooldown (beasts already in the world stay). */
    public void resetCooldown() {
        lastSpawn = Long.MIN_VALUE / 2;
        pendingUntil = 0;
        setDirty();
    }
}
