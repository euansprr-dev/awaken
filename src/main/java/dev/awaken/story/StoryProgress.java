package dev.awaken.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Per-player story state (saved, kept on death, synced to its owner). */
public final class StoryProgress {
    /**
     * RITUAL: sealed in the Ritual of the Bell's hall (story/Ritual) until the Seated One's hand closes. Saved by name:
     * a save from before this state never holds it, a save from after it read by an older build falls back to NOT_STARTED.
     */
    public enum Prologue { NOT_STARTED, GLITCHED, GATE_OPEN, RITUAL, ACCEPTING, DONE;
        public static final Codec<Prologue> CODEC = dev.awaken.core.Codecs.lenientEnum(Prologue.class, NOT_STARTED); }

    Prologue prologue = Prologue.NOT_STARTED;
    String prologueGate = "";
    long prologueSince;
    double returnX, returnY, returnZ;
    final Set<String> seen = new LinkedHashSet<>();          // revelations, rival beats, signal lines, sovereigns met
    final Set<String> sovereignsDefeated = new LinkedHashSet<>();
    /** The Bell Crypt as you left it, for the Now tracker: deepest floor reached, floors cleared, the last floors counter seen. */
    int cryptFloor, cryptCleared;
    long cryptCounterSeen = -1;

    public Prologue prologue() { return prologue; }
    public Set<String> seen() { return seen; }
    public Set<String> sovereignsDefeated() { return sovereignsDefeated; }

    /** 1: v2 adds the Bell Crypt floor tracker (defaults for older saves). 2: the RITUAL prologue state (no new fields). */
    static final int SCHEMA = 2;

    private record Crypt(int schema, int floor, int cleared, long counterSeen) {
        static final Crypt NONE = new Crypt(SCHEMA, 0, 0, -1);
        static final Codec<Crypt> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Crypt::schema),
                Codec.INT.optionalFieldOf("floor", 0).forGetter(Crypt::floor),
                Codec.INT.optionalFieldOf("cleared", 0).forGetter(Crypt::cleared),
                Codec.LONG.optionalFieldOf("counter_seen", -1L).forGetter(Crypt::counterSeen)
        ).apply(i, Crypt::new));
    }

    public static final Codec<StoryProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Prologue.CODEC.fieldOf("prologue").forGetter(s -> s.prologue),
            Codec.STRING.fieldOf("gate").forGetter(s -> s.prologueGate),
            Codec.LONG.fieldOf("since").forGetter(s -> s.prologueSince),
            Codec.DOUBLE.listOf().fieldOf("return").forGetter(s -> List.of(s.returnX, s.returnY, s.returnZ)),
            Codec.STRING.listOf().fieldOf("seen").forGetter(s -> List.copyOf(s.seen)),
            Codec.STRING.listOf().fieldOf("sovereigns").forGetter(s -> List.copyOf(s.sovereignsDefeated)),
            Crypt.CODEC.optionalFieldOf("crypt", Crypt.NONE).forGetter(s -> new Crypt(SCHEMA, s.cryptFloor, s.cryptCleared, s.cryptCounterSeen))
    ).apply(i, (pro, gate, since, ret, seen, sov, crypt) -> {
        StoryProgress s = new StoryProgress();
        s.prologue = pro;
        s.prologueGate = gate;
        s.prologueSince = since;
        if (ret.size() == 3) { s.returnX = ret.get(0); s.returnY = ret.get(1); s.returnZ = ret.get(2); }
        s.seen.addAll(seen);
        s.sovereignsDefeated.addAll(sov);
        s.cryptFloor = crypt.floor();
        s.cryptCleared = crypt.cleared();
        s.cryptCounterSeen = crypt.counterSeen();
        return s;
    }));

}
