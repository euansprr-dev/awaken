package dev.awaken.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Ritual of the Bell's saved state, one per ritual gate (world-global, {@code data/awaken/ritual.dat}): which beat it
 * is on and since when, who is sealed in with it, and its cast. A relog or a restart resumes the beat it was on.
 */
final class RitualData extends SavedData {
    /** 1: the first version. */
    static final int SCHEMA = 1;

    static final class State {
        int gate;
        Ritual.Beat beat = Ritual.Beat.WAITING;
        /** Overworld game time the beat began (moved on while nobody in the party is there, so a pause loses nothing). */
        long beatStart;
        /** When the first player stepped into the hall (the 90 s wait counts from here), -1 before. */
        long firstArrival = -1;
        final Set<UUID> party = new LinkedHashSet<>();
        Optional<UUID> seated = Optional.empty();
        final List<UUID> statues = new ArrayList<>();
        /** Where the gate's mouth is in the overworld (players who come back after the end wake there). */
        BlockPos mouth = BlockPos.ZERO;

        State(int gate) {
            this.gate = gate;
        }

        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(s -> SCHEMA),
                Codec.INT.fieldOf("gate").forGetter(s -> s.gate),
                Ritual.Beat.CODEC.fieldOf("beat").forGetter(s -> s.beat),
                Codec.LONG.fieldOf("beat_start").forGetter(s -> s.beatStart),
                Codec.LONG.optionalFieldOf("first_arrival", -1L).forGetter(s -> s.firstArrival),
                UUIDUtil.CODEC.listOf().optionalFieldOf("party", List.of()).forGetter(s -> List.copyOf(s.party)),
                UUIDUtil.CODEC.optionalFieldOf("seated").forGetter(s -> s.seated),
                UUIDUtil.CODEC.listOf().optionalFieldOf("statues", List.of()).forGetter(s -> List.copyOf(s.statues)),
                BlockPos.CODEC.optionalFieldOf("mouth", BlockPos.ZERO).forGetter(s -> s.mouth)
        ).apply(i, (schema, gate, beat, start, arrival, party, seated, statues, mouth) -> {
            State s = new State(gate);
            s.beat = beat;
            s.beatStart = start;
            s.firstArrival = arrival;
            s.party.addAll(party);
            s.seated = seated;
            s.statues.addAll(statues);
            s.mouth = mouth;
            return s;
        }));
    }

    static final Codec<RitualData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> SCHEMA),
            State.CODEC.listOf().optionalFieldOf("rituals", List.of()).forGetter(d -> List.copyOf(d.rituals.values()))
    ).apply(i, (schema, list) -> {
        RitualData d = new RitualData();
        for (State s : list) d.rituals.put(s.gate, s);
        return d;
    }));

    static final SavedDataType<RitualData> TYPE = new SavedDataType<>(Awaken.id("ritual"), RitualData::new, CODEC);

    final Map<Integer, State> rituals = new LinkedHashMap<>();

    RitualData() {}

    static RitualData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }
}
