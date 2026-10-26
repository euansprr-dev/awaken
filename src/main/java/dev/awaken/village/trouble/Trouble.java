package dev.awaken.village.trouble;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One trouble a village is in (SPEC §28.7): what kind, what caused it (always something in the world: a lair, a camp, a
 * gate, the damage done), where its deed is, who it's about (a record of the settlement's roster) and who said Help.
 * It runs on its own clock ({@link #due}, a play-day) and ends better or worse; what it put in the world (a trail, the
 * survivors' bedrolls by the well) is listed in {@link #laid} and taken away when it's over. Saved in {@link TroubleState}.
 */
public final class Trouble {
    final int id;
    final String village, kind, cause;
    int stage;
    /** The play-day it began; the game time its current stage began; the play-day it ends without you. */
    final long day;
    long since, due;
    /** Where the deed is (a lair's den, a camp, a gate, the tower), and what people call it. */
    long place;
    String placeName;
    /** Who it's about (the taken, the missing), and who asks (the parent, the elder): roster records, -1 none. */
    int person, kin;
    final List<UUID> helpers;
    final Map<String, Long> data;
    final List<Long> laid;
    String text;

    Trouble(int id, String village, String kind, int stage, long day, long since, long due, String cause, long place, String placeName,
            int person, int kin, List<UUID> helpers, Map<String, Long> data, List<Long> laid, String text) {
        this.id = id;
        this.village = village;
        this.kind = kind;
        this.stage = stage;
        this.day = day;
        this.since = since;
        this.due = due;
        this.cause = cause;
        this.place = place;
        this.placeName = placeName;
        this.person = person;
        this.kin = kin;
        this.helpers = new ArrayList<>(helpers);
        this.data = new HashMap<>(data);
        this.laid = new ArrayList<>(laid);
        this.text = text;
    }

    static final Codec<Trouble> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(t -> t.id),
            Codec.STRING.fieldOf("village").forGetter(t -> t.village),
            Codec.STRING.fieldOf("kind").forGetter(t -> t.kind),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(t -> t.stage),
            Codec.LONG.fieldOf("day").forGetter(t -> t.day),
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(t -> t.since),
            Codec.LONG.fieldOf("due").forGetter(t -> t.due),
            Codec.STRING.optionalFieldOf("cause", "").forGetter(t -> t.cause),
            Codec.LONG.optionalFieldOf("place", 0L).forGetter(t -> t.place),
            Codec.STRING.optionalFieldOf("place_name", "").forGetter(t -> t.placeName),
            Codec.INT.optionalFieldOf("person", -1).forGetter(t -> t.person),
            Codec.INT.optionalFieldOf("kin", -1).forGetter(t -> t.kin),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("helpers", List.of()).forGetter(t -> t.helpers),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("data", Map.of()).forGetter(t -> t.data),
            Codec.LONG.listOf().optionalFieldOf("laid", List.of()).forGetter(t -> t.laid),
            Codec.STRING.optionalFieldOf("text", "").forGetter(t -> t.text)
    ).apply(i, Trouble::new));

    public int id() {
        return id;
    }

    public String village() {
        return village;
    }

    public String kind() {
        return kind;
    }

    public int stage() {
        return stage;
    }

    public BlockPos place() {
        return BlockPos.of(place);
    }

    public boolean helped(UUID p) {
        return helpers.contains(p);
    }

    long get(String key, long def) {
        Long v = data.get(key);
        return v == null ? def : v;
    }

    void put(String key, long v) {
        data.put(key, v);
    }

    @Override
    public String toString() {
        return kind + " #" + id + " at " + village + " stage " + stage + " (day " + day + ", due " + due + ", cause " + cause + ", place "
                + BlockPos.of(place).toShortString() + " " + placeName + ", person " + person + ", kin " + kin + ", helpers " + helpers.size() + ")";
    }
}
