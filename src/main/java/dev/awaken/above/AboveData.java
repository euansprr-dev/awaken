package dev.awaken.above;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the Above knows about one player: blueprints scanned, Precursor logs read, ruins found, the Clone Vat bond and
 * small per-day timers. Saved on the player and kept on death. Mutate through {@link Above}; it re-syncs the owner.
 */
public final class AboveData {
    public static final int SCHEMA = 1;

    /** A ruin the player found (scanned a machine or read a log in it). kind = {@code RuinKind} id. */
    public record RuinMark(String kind, BlockPos center, GlobalPos landing) {
        public static final Codec<RuinMark> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(RuinMark::kind),
                BlockPos.CODEC.fieldOf("center").forGetter(RuinMark::center),
                GlobalPos.CODEC.fieldOf("landing").forGetter(RuinMark::landing)
        ).apply(i, RuinMark::new));
    }

    int schema = SCHEMA;
    final Set<String> blueprints = new LinkedHashSet<>();
    final Set<String> built = new LinkedHashSet<>();
    final Set<String> logs = new LinkedHashSet<>();
    final List<RuinMark> ruins = new ArrayList<>();
    final Set<String> scannedGates = new LinkedHashSet<>();
    final Map<String, Long> stabilisedDay = new HashMap<>();
    Optional<GlobalPos> cloneVat = Optional.empty();
    long cloneUsedDay = -1;
    boolean clonePending;
    long relayDay = -1;
    Optional<GlobalPos> launchOrigin = Optional.empty();
    boolean haloSeen;

    public AboveData() {}

    // ---- read API -------------------------------------------------------------------------------------------------
    public Set<String> blueprints() { return blueprints; }
    public boolean knows(MachineType t) { return blueprints.contains(t.id); }
    public Set<String> built() { return built; }
    public Set<String> logs() { return logs; }
    public List<RuinMark> ruins() { return ruins; }
    public Set<String> scannedGates() { return scannedGates; }
    public Optional<GlobalPos> cloneVat() { return cloneVat; }
    public long cloneUsedDay() { return cloneUsedDay; }
    public boolean clonePending() { return clonePending; }
    public long relayDay() { return relayDay; }
    public Optional<GlobalPos> launchOrigin() { return launchOrigin; }
    public boolean haloSeen() { return haloSeen; }
    public long stabilisedDay(String gate) { return stabilisedDay.getOrDefault(gate, -1L); }

    // ---- mutators (callers re-sync with Above.sync) ----------------------------------------------------------------
    public void addScannedGate(String id) { scannedGates.add(id); }
    public void removeScannedGate(String id) { scannedGates.remove(id); stabilisedDay.remove(id); }
    public void setStabilised(String gate, long day) { stabilisedDay.put(gate, day); }
    public void setCloneVat(Optional<GlobalPos> vat) { cloneVat = vat; }
    public void setCloneUsedDay(long day) { cloneUsedDay = day; }
    public void setClonePending(boolean pending) { clonePending = pending; }
    public void setRelayDay(long day) { relayDay = day; }
    public void setLaunchOrigin(Optional<GlobalPos> origin) { launchOrigin = origin; }
    public void setHaloSeen(boolean seen) { haloSeen = seen; }
    public void learnAll() { for (MachineType t : MachineType.values()) blueprints.add(t.id); }

    public boolean hasRuinNear(BlockPos center, int radius) {
        for (RuinMark r : ruins) if (r.center().distSqr(center) <= (long) radius * radius) return true;
        return false;
    }

    // ---- codec ----------------------------------------------------------------------------------------------------
    private record Knowledge(int schema, List<String> blueprints, List<String> built, List<String> logs, List<RuinMark> ruins,
                             List<String> gates, Map<String, Long> stabilised) {
        static final Codec<Knowledge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(Knowledge::schema),
                Codec.STRING.listOf().fieldOf("blueprints").forGetter(Knowledge::blueprints),
                Codec.STRING.listOf().optionalFieldOf("built", List.of()).forGetter(Knowledge::built),
                Codec.STRING.listOf().fieldOf("logs").forGetter(Knowledge::logs),
                RuinMark.CODEC.listOf().fieldOf("ruins").forGetter(Knowledge::ruins),
                Codec.STRING.listOf().optionalFieldOf("gates", List.of()).forGetter(Knowledge::gates),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("stabilised", Map.of()).forGetter(Knowledge::stabilised)
        ).apply(i, Knowledge::new));
    }

    private record Timers(Optional<GlobalPos> vat, long cloneUsedDay, boolean clonePending, long relayDay,
                          Optional<GlobalPos> launchOrigin, boolean haloSeen) {
        static final Codec<Timers> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.optionalFieldOf("vat").forGetter(Timers::vat),
                Codec.LONG.optionalFieldOf("clone_used_day", -1L).forGetter(Timers::cloneUsedDay),
                Codec.BOOL.optionalFieldOf("clone_pending", false).forGetter(Timers::clonePending),
                Codec.LONG.optionalFieldOf("relay_day", -1L).forGetter(Timers::relayDay),
                GlobalPos.CODEC.optionalFieldOf("launch_origin").forGetter(Timers::launchOrigin),
                Codec.BOOL.optionalFieldOf("halo_seen", false).forGetter(Timers::haloSeen)
        ).apply(i, Timers::new));
    }

    public static final MapCodec<AboveData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Knowledge.CODEC.fieldOf("knowledge").forGetter(d -> new Knowledge(d.schema, List.copyOf(d.blueprints), List.copyOf(d.built),
                    List.copyOf(d.logs), List.copyOf(d.ruins), List.copyOf(d.scannedGates), Map.copyOf(d.stabilisedDay))),
            Timers.CODEC.fieldOf("timers").forGetter(d -> new Timers(d.cloneVat, d.cloneUsedDay, d.clonePending, d.relayDay,
                    d.launchOrigin, d.haloSeen))
    ).apply(i, AboveData::fromParts));

    public static final Codec<AboveData> CODEC = MAP_CODEC.codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, AboveData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);

    /** A detached copy (sent to the client: local connections may not serialize payloads). */
    public AboveData copy() {
        return CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, this).getOrThrow()).getOrThrow();
    }

    private static AboveData fromParts(Knowledge k, Timers t) {
        AboveData d = new AboveData();
        d.schema = k.schema;
        d.blueprints.addAll(k.blueprints);
        d.built.addAll(k.built);
        d.logs.addAll(k.logs);
        d.ruins.addAll(k.ruins);
        d.scannedGates.addAll(k.gates);
        d.stabilisedDay.putAll(k.stabilised);
        d.cloneVat = t.vat;
        d.cloneUsedDay = t.cloneUsedDay;
        d.clonePending = t.clonePending;
        d.relayDay = t.relayDay;
        d.launchOrigin = t.launchOrigin;
        d.haloSeen = t.haloSeen;
        return d;
    }
}
