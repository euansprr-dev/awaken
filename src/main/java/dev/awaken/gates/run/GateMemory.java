package dev.awaken.gates.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Director's novelty memory for one player (attachment {@code awaken:gate_memory}, kept on death): the last three
 * themes and two layouts rolled, the twist pity counter, which gates were scouted, and the bits that later counters need
 * (extraction floors for "comfort", twist streak, echo pace).
 */
public final class GateMemory {
    public static final int SCHEMA = 1;

    public final List<String> lastThemes = new ArrayList<>();
    public final List<String> lastLayouts = new ArrayList<>();
    public int sinceTwist;
    public int twistStreak;
    public final Set<Integer> scouted = new LinkedHashSet<>();
    public final List<Integer> extractFloors = new ArrayList<>();
    public final Set<String> themesCleared = new LinkedHashSet<>();
    public int bossesSinceCore;
    public long echoFloorTicks;
    public int echoFloorRooms;
    public final Set<Integer> twistsCounted = new LinkedHashSet<>();
    public int gatesRolled;

    public void rememberTheme(String id) {
        lastThemes.add(0, id);
        while (lastThemes.size() > 3) lastThemes.remove(lastThemes.size() - 1);
    }

    public void rememberLayout(String id) {
        lastLayouts.add(0, id);
        while (lastLayouts.size() > 2) lastLayouts.remove(lastLayouts.size() - 1);
    }

    public void scout(int gateId) {
        scouted.add(gateId);
        while (scouted.size() > 64) scouted.remove(scouted.iterator().next());
    }

    public void extractedAt(int floor) {
        extractFloors.add(0, floor);
        while (extractFloors.size() > 5) extractFloors.remove(extractFloors.size() - 1);
    }

    /** The floor you usually stop at: the median of your last extractions (2 before you have any). */
    public int comfortFloor() {
        if (extractFloors.isEmpty()) return 2;
        List<Integer> s = new ArrayList<>(extractFloors);
        s.sort(Integer::compare);
        return s.get(s.size() / 2);
    }

    public boolean countTwist(int gateId) {
        if (!twistsCounted.add(gateId)) return false;
        while (twistsCounted.size() > 32) twistsCounted.remove(twistsCounted.iterator().next());
        return true;
    }

    private record A(int schema, List<String> themes, List<String> layouts, int sinceTwist, int twistStreak, List<Integer> scouted,
                     List<Integer> extracts) {
        static final Codec<A> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(A::schema),
                Codec.STRING.listOf().fieldOf("themes").forGetter(A::themes),
                Codec.STRING.listOf().fieldOf("layouts").forGetter(A::layouts),
                Codec.INT.fieldOf("since_twist").forGetter(A::sinceTwist),
                Codec.INT.fieldOf("twist_streak").forGetter(A::twistStreak),
                Codec.INT.listOf().fieldOf("scouted").forGetter(A::scouted),
                Codec.INT.listOf().fieldOf("extracts").forGetter(A::extracts)
        ).apply(i, A::new));
    }

    private record B(List<String> themesCleared, int bossesSinceCore, long echoTicks, int echoRooms, List<Integer> twists, int rolled) {
        static final Codec<B> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("themes_cleared").forGetter(B::themesCleared),
                Codec.INT.fieldOf("bosses_since_core").forGetter(B::bossesSinceCore),
                Codec.LONG.fieldOf("echo_ticks").forGetter(B::echoTicks),
                Codec.INT.fieldOf("echo_rooms").forGetter(B::echoRooms),
                Codec.INT.listOf().fieldOf("twists").forGetter(B::twists),
                Codec.INT.optionalFieldOf("rolled", 0).forGetter(B::rolled)
        ).apply(i, B::new));
    }

    public static final MapCodec<GateMemory> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            A.CODEC.fieldOf("a").forGetter(m -> new A(SCHEMA, List.copyOf(m.lastThemes), List.copyOf(m.lastLayouts), m.sinceTwist,
                    m.twistStreak, List.copyOf(m.scouted), List.copyOf(m.extractFloors))),
            B.CODEC.fieldOf("b").forGetter(m -> new B(List.copyOf(m.themesCleared), m.bossesSinceCore, m.echoFloorTicks,
                    m.echoFloorRooms, List.copyOf(m.twistsCounted), m.gatesRolled))
    ).apply(i, (a, b) -> {
        GateMemory m = new GateMemory();
        m.lastThemes.addAll(a.themes);
        m.lastLayouts.addAll(a.layouts);
        m.sinceTwist = a.sinceTwist;
        m.twistStreak = a.twistStreak;
        m.scouted.addAll(a.scouted);
        m.extractFloors.addAll(a.extracts);
        m.themesCleared.addAll(b.themesCleared);
        m.bossesSinceCore = b.bossesSinceCore;
        m.echoFloorTicks = b.echoTicks;
        m.echoFloorRooms = b.echoRooms;
        m.twistsCounted.addAll(b.twists);
        m.gatesRolled = b.rolled;
        return m;
    }));
}
