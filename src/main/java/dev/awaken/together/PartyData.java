package dev.awaken.together;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The world's parties (SPEC §70.1): up to four players who chose each other. Saved with the world; an old world loads
 * with none. {@code apart} remembers who left a party on purpose, so sharing a home doesn't put them straight back.
 */
public final class PartyData extends SavedData {
    public static final int MAX = 4;

    /** One party: its members in the order they joined, and the play-day it formed. */
    public static final class Party {
        public final List<UUID> members = new ArrayList<>();
        public final long formedDay;

        Party(List<UUID> members, long formedDay) {
            this.members.addAll(members);
            this.formedDay = formedDay;
        }

        static final Codec<Party> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(p -> List.copyOf(p.members)),
                Codec.LONG.optionalFieldOf("formed_day", 0L).forGetter(p -> p.formedDay)
        ).apply(i, Party::new));
    }

    public static final Codec<PartyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Party.CODEC.listOf().optionalFieldOf("parties", List.of()).forGetter(d -> List.copyOf(d.parties)),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("apart", List.of()).forGetter(d -> List.copyOf(d.apart)),
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(d -> 1)
    ).apply(i, PartyData::new));

    public static final SavedDataType<PartyData> TYPE = new SavedDataType<>(Awaken.id("together"), PartyData::new, CODEC);

    final List<Party> parties = new ArrayList<>();
    final Set<UUID> apart = new HashSet<>();

    public PartyData() {}

    private PartyData(List<Party> parties, List<UUID> apart, int schemaVersion) {
        for (Party p : parties) if (p.members.size() >= 2) this.parties.add(p);
        this.apart.addAll(apart);
    }

    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Party of(UUID player) {
        for (Party p : parties) if (p.members.contains(player)) return p;
        return null;
    }

    /**
     * Puts the two in one party: the one either is already in when it has room, else a new one. False when both are in
     * (different) parties, or the one party there is is full.
     */
    boolean join(UUID a, UUID b, long day) {
        Party pa = of(a), pb = of(b);
        if (pa != null && pa == pb) return true;
        if (pa != null && pb != null) return false;
        Party into = pa != null ? pa : pb;
        UUID joiner = pa != null ? b : a;
        if (into == null) {
            parties.add(new Party(List.of(a, b), day));
        } else {
            if (into.members.size() >= MAX) return false;
            into.members.add(joiner);
        }
        apart.remove(a);
        apart.remove(b);
        setDirty();
        return true;
    }

    /** Takes the player out of their party; a party of one is no party. Returns who was left behind. */
    List<UUID> leave(UUID player, boolean onPurpose) {
        Party p = of(player);
        if (p == null) return List.of();
        p.members.remove(player);
        List<UUID> rest = List.copyOf(p.members);
        if (p.members.size() < 2) parties.remove(p);
        if (onPurpose) apart.add(player);
        setDirty();
        return rest;
    }

    void clear() {
        parties.clear();
        apart.clear();
        setDirty();
    }
}
