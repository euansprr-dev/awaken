package dev.awaken.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Per-player story state (saved, kept on death, synced to its owner). */
public final class StoryProgress {
    public enum Prologue { NOT_STARTED, GLITCHED, GATE_OPEN, ACCEPTING, DONE;
        public static final Codec<Prologue> CODEC = dev.awaken.core.Codecs.lenientEnum(Prologue.class, NOT_STARTED); }

    Prologue prologue = Prologue.NOT_STARTED;
    String prologueGate = "";
    long prologueSince;
    double returnX, returnY, returnZ;
    final Set<String> seen = new LinkedHashSet<>();          // revelations, rival beats, signal lines, sovereigns met
    final Set<String> sovereignsDefeated = new LinkedHashSet<>();

    public Prologue prologue() { return prologue; }
    public Set<String> seen() { return seen; }
    public Set<String> sovereignsDefeated() { return sovereignsDefeated; }

    public static final Codec<StoryProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Prologue.CODEC.fieldOf("prologue").forGetter(s -> s.prologue),
            Codec.STRING.fieldOf("gate").forGetter(s -> s.prologueGate),
            Codec.LONG.fieldOf("since").forGetter(s -> s.prologueSince),
            Codec.DOUBLE.listOf().fieldOf("return").forGetter(s -> List.of(s.returnX, s.returnY, s.returnZ)),
            Codec.STRING.listOf().fieldOf("seen").forGetter(s -> List.copyOf(s.seen)),
            Codec.STRING.listOf().fieldOf("sovereigns").forGetter(s -> List.copyOf(s.sovereignsDefeated))
    ).apply(i, (pro, gate, since, ret, seen, sov) -> {
        StoryProgress s = new StoryProgress();
        s.prologue = pro;
        s.prologueGate = gate;
        s.prologueSince = since;
        if (ret.size() == 3) { s.returnX = ret.get(0); s.returnY = ret.get(1); s.returnZ = ret.get(2); }
        s.seen.addAll(seen);
        s.sovereignsDefeated.addAll(sov);
        return s;
    }));

}
