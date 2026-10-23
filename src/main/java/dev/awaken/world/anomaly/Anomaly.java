package dev.awaken.world.anomaly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One active anomaly (world SavedData {@link AnomalyData}). Mutable while it runs; saved through the {@link Saved}
 * snapshot records. Each kind uses its own part: a rift its core and spilled monsters, a procession its path and
 * motes, a wandering gate the gate's id. Entities are never saved (their types are {@code noSave}), so after a restart
 * only the record remains and {@link AnomalyDirector#recover} cleans it up.
 */
public final class Anomaly {
    /** Procession stages. Rifts use only OPEN. */
    public static final int OPEN = 0, LEADING = 1, CACHE = 2;

    public final int id;
    public final AnomalyKind kind;
    public final UUID owner;
    /** The validated ground spot: a rift's core floats above it, a procession starts there, a gate forms near it. */
    public final BlockPos pos;
    public final long startedAt;
    public long endsAt;
    /** Whether the owner has come close enough to count as having seen it (first-time discovery is paid then). */
    public boolean discovered;
    public int stage = OPEN;

    // ---- rift tear
    public String theme = "";
    public int contentLevel;
    public int maxHits;
    public int hitsLeft;
    public boolean paid;
    public boolean guardSpawned;
    public long nextWaveAt;
    public int spawned;
    public @Nullable UUID entity;
    public final List<UUID> mobs = new ArrayList<>();

    // ---- spirit procession
    /** Where the lights lead (ground); the cache stands on top of it. */
    public BlockPos target = BlockPos.ZERO;
    public double progress;
    public boolean cachePlaced;
    public final List<UUID> motes = new ArrayList<>();

    // ---- wandering gate
    public String gateId = "";

    public Anomaly(int id, AnomalyKind kind, UUID owner, BlockPos pos, long startedAt, long endsAt) {
        this.id = id;
        this.kind = kind;
        this.owner = owner;
        this.pos = pos.immutable();
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    /** Where the rift's core floats, the procession's cache stands, or (gates) the ground spot. */
    public BlockPos focus() {
        return switch (kind) {
            case RIFT_TEAR -> pos.above();
            case SPIRIT_PROCESSION -> stage == CACHE ? target.above() : pos.above();
            case WANDERING_GATE -> pos;
        };
    }

    // ---- saving --------------------------------------------------------------------------------------------------------

    record Base(Optional<Integer> schema, int id, AnomalyKind kind, UUID owner, BlockPos pos, long startedAt, long endsAt, boolean discovered,
                int stage, String gateId) {
        static final MapCodec<Base> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schema").forGetter(Base::schema),
                Codec.INT.fieldOf("id").forGetter(Base::id),
                AnomalyKind.CODEC.fieldOf("kind").forGetter(Base::kind),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Base::owner),
                BlockPos.CODEC.fieldOf("pos").forGetter(Base::pos),
                Codec.LONG.fieldOf("started").forGetter(Base::startedAt),
                Codec.LONG.fieldOf("ends").forGetter(Base::endsAt),
                Codec.BOOL.optionalFieldOf("discovered", false).forGetter(Base::discovered),
                Codec.INT.optionalFieldOf("stage", OPEN).forGetter(Base::stage),
                Codec.STRING.optionalFieldOf("gate", "").forGetter(Base::gateId)
        ).apply(i, Base::new));
    }

    record RiftPart(String theme, int contentLevel, int maxHits, int hitsLeft, boolean paid, boolean guard, long nextWaveAt, int spawned,
                    Optional<UUID> entity, List<UUID> mobs) {
        static final RiftPart NONE = new RiftPart("", 0, 0, 0, false, false, 0, 0, Optional.empty(), List.of());
        static final Codec<RiftPart> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("theme", "").forGetter(RiftPart::theme),
                Codec.INT.optionalFieldOf("level", 0).forGetter(RiftPart::contentLevel),
                Codec.INT.optionalFieldOf("max_hits", 0).forGetter(RiftPart::maxHits),
                Codec.INT.optionalFieldOf("hits_left", 0).forGetter(RiftPart::hitsLeft),
                Codec.BOOL.optionalFieldOf("paid", false).forGetter(RiftPart::paid),
                Codec.BOOL.optionalFieldOf("guard", false).forGetter(RiftPart::guard),
                Codec.LONG.optionalFieldOf("next_wave", 0L).forGetter(RiftPart::nextWaveAt),
                Codec.INT.optionalFieldOf("spawned", 0).forGetter(RiftPart::spawned),
                UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(RiftPart::entity),
                UUIDUtil.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(RiftPart::mobs)
        ).apply(i, RiftPart::new));
    }

    record TrailPart(BlockPos target, double progress, boolean cachePlaced, List<UUID> motes) {
        static final TrailPart NONE = new TrailPart(BlockPos.ZERO, 0, false, List.of());
        static final Codec<TrailPart> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("target", BlockPos.ZERO).forGetter(TrailPart::target),
                Codec.DOUBLE.optionalFieldOf("progress", 0.0).forGetter(TrailPart::progress),
                Codec.BOOL.optionalFieldOf("cache", false).forGetter(TrailPart::cachePlaced),
                UUIDUtil.CODEC.listOf().optionalFieldOf("motes", List.of()).forGetter(TrailPart::motes)
        ).apply(i, TrailPart::new));
    }

    /** The saved form, with value equality (the round-trip test compares these). */
    public record Saved(Base base, RiftPart rift, TrailPart trail) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Base.MAP_CODEC.forGetter(Saved::base),
                RiftPart.CODEC.optionalFieldOf("rift", RiftPart.NONE).forGetter(Saved::rift),
                TrailPart.CODEC.optionalFieldOf("trail", TrailPart.NONE).forGetter(Saved::trail)
        ).apply(i, Saved::new));
    }

    public Saved save() {
        return new Saved(new Base(Optional.of(AnomalyData.SCHEMA), id, kind, owner, pos, startedAt, endsAt, discovered, stage, gateId),
                new RiftPart(theme, contentLevel, maxHits, hitsLeft, paid, guardSpawned, nextWaveAt, spawned, Optional.ofNullable(entity), List.copyOf(mobs)),
                new TrailPart(target, progress, cachePlaced, List.copyOf(motes)));
    }

    public static Anomaly load(Saved s) {
        Base b = s.base();
        Anomaly a = new Anomaly(b.id(), b.kind(), b.owner(), b.pos(), b.startedAt(), b.endsAt());
        a.discovered = b.discovered();
        a.stage = b.stage();
        a.gateId = b.gateId();
        RiftPart r = s.rift();
        a.theme = r.theme();
        a.contentLevel = r.contentLevel();
        a.maxHits = r.maxHits();
        a.hitsLeft = r.hitsLeft();
        a.paid = r.paid();
        a.guardSpawned = r.guard();
        a.nextWaveAt = r.nextWaveAt();
        a.spawned = r.spawned();
        a.entity = r.entity().orElse(null);
        a.mobs.addAll(r.mobs());
        TrailPart t = s.trail();
        a.target = t.target().immutable();
        a.progress = t.progress();
        a.cachePlaced = t.cachePlaced();
        a.motes.addAll(t.motes());
        return a;
    }

    public static final Codec<Anomaly> CODEC = Saved.CODEC.xmap(Anomaly::load, Anomaly::save);
}
