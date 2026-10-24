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

/**
 * Every Hearth in the world, one owned per player; any player may also be a member of others' Hearths (SPEC
 * "Together"). Stored with the overworld's data. Schema 2 adds the households kept while an owner's Anchor is cold.
 */
public final class HearthData extends SavedData {
    public static final int SCHEMA = 2;

    /** The members a Hearth had when its Anchor was broken: they share it again when the owner places it anew. */
    record Kept(UUID owner, List<HearthRecord.Member> members) {
        static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.core.UUIDUtil.CODEC.fieldOf("owner").forGetter(Kept::owner),
                HearthRecord.Member.CODEC.listOf().fieldOf("members").forGetter(Kept::members)
        ).apply(i, Kept::new));
    }

    public static final Codec<HearthData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> SCHEMA),
            HearthRecord.CODEC.listOf().fieldOf("hearths").forGetter(d -> List.copyOf(d.byOwner.values())),
            Kept.CODEC.listOf().optionalFieldOf("kept", List.of()).forGetter(d -> List.copyOf(d.kept.values()))
    ).apply(i, HearthData::new));

    public static final SavedDataType<HearthData> TYPE = new SavedDataType<>(Awaken.id("hearths"), HearthData::new, CODEC);

    private final Map<UUID, HearthRecord> byOwner = new LinkedHashMap<>();
    private final Map<UUID, Kept> kept = new LinkedHashMap<>();

    public HearthData() {}

    private HearthData(int schema, List<HearthRecord> list, List<Kept> keptList) {
        for (HearthRecord r : list) byOwner.put(r.owner, r);
        for (Kept k : keptList) kept.put(k.owner(), k);
    }

    // ---- membership -------------------------------------------------------------------------------------------------

    /** The Hearth this player lives at: their own, else the first they are a member of (no allocation). */
    public Optional<HearthRecord> home(UUID player) {
        HearthRecord own = byOwner.get(player);
        if (own != null) return Optional.of(own);
        for (HearthRecord r : byOwner.values()) if (r.members.containsKey(player)) return Optional.of(r);
        return Optional.empty();
    }

    /** Every Hearth this player belongs to: their own first, then the ones they are a member of. */
    public List<HearthRecord> hearthsOf(UUID player) {
        List<HearthRecord> out = new ArrayList<>(2);
        HearthRecord own = byOwner.get(player);
        if (own != null) out.add(own);
        for (HearthRecord r : byOwner.values()) if (r.members.containsKey(player)) out.add(r);
        return out;
    }

    /** The Hearths this player is a member of (not their own). */
    public List<HearthRecord> memberOf(UUID player) {
        List<HearthRecord> out = new ArrayList<>(1);
        for (HearthRecord r : byOwner.values()) if (r.members.containsKey(player)) out.add(r);
        return out;
    }

    /** Hearths founded from this owner's Hearth as sister homes. */
    public int sistersOf(UUID owner) {
        int n = 0;
        for (HearthRecord r : byOwner.values()) if (owner.equals(r.foundedFrom)) n++;
        return n;
    }

    /** Keeps a household while its Anchor is cold (also with no members: its owner is moving home, never founding). */
    void keep(HearthRecord h) {
        List<HearthRecord.Member> m = new ArrayList<>();
        h.members.forEach((id, name) -> m.add(new HearthRecord.Member(id, name == null ? "" : name)));
        kept.put(h.owner, new Kept(h.owner, m));
        setDirty();
    }

    /** Whether this player broke their own Anchor and hasn't placed it again (they are moving home). */
    boolean movingHome(UUID owner) {
        return kept.containsKey(owner);
    }

    /** The household kept for this owner (removed: it is handed back once). */
    List<HearthRecord.Member> takeKept(UUID owner) {
        Kept k = kept.remove(owner);
        if (k == null) return List.of();
        setDirty();
        return k.members();
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
