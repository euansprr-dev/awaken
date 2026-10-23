package dev.awaken.gates.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's run through one gate (attachment {@code awaken:gate_run}, saved with the player so a disconnect resumes
 * where you were). {@code gateId < 0} means not in a gate. Mutated only on the server thread.
 */
public final class RunState {
    public static final int SCHEMA = 1;

    public int gateId = -1;
    /** 0 main dungeon, 1 annex (second gate / inner gate). */
    public int area;
    public int floor = 1;
    public final List<ItemStack> satchel = new ArrayList<>();
    public double satchelXp;
    public int kills;
    public long startedAt;
    public long floorStartedAt;
    /** Game time the player logged out mid-run (-1 = online): the floor clock is moved on by the time away. */
    public long leftAt = -1;
    public float damageFloor;
    public float damageRun;
    public double killXpFloor;
    public boolean cheatDeathUsed;
    public int lanterns;
    /** "area:floor" keys of floors already cleared in this run (shrine reached / boss dead). */
    public final Set<String> cleared = new LinkedHashSet<>();
    public boolean scoutedAtEntry;
    public int floorsCleared;
    public boolean extracted;
    public int noise;
    /** Which run of this player in this gate (0 = first): part of claim ids. */
    public int runIndex;
    public long disguiseBrokenUntil;

    public boolean active() {
        return gateId >= 0;
    }

    public boolean clearedFloor(int area, int floor) {
        return cleared.contains(area + ":" + floor);
    }

    public static RunState none() {
        return new RunState();
    }

    // ---- codec ------------------------------------------------------------------------------------------------------

    private record Head(int schema, int gateId, int area, int floor, long startedAt, long floorStartedAt, int kills,
                        boolean cheat, int lanterns, boolean scouted, int floorsCleared, int runIndex) {
        static final Codec<Head> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(Head::schema),
                Codec.INT.fieldOf("gate").forGetter(Head::gateId),
                Codec.INT.fieldOf("area").forGetter(Head::area),
                Codec.INT.fieldOf("floor").forGetter(Head::floor),
                Codec.LONG.fieldOf("started").forGetter(Head::startedAt),
                Codec.LONG.fieldOf("floor_started").forGetter(Head::floorStartedAt),
                Codec.INT.fieldOf("kills").forGetter(Head::kills),
                Codec.BOOL.fieldOf("cheat_used").forGetter(Head::cheat),
                Codec.INT.fieldOf("lanterns").forGetter(Head::lanterns),
                Codec.BOOL.fieldOf("scouted").forGetter(Head::scouted),
                Codec.INT.fieldOf("floors_cleared").forGetter(Head::floorsCleared),
                Codec.INT.optionalFieldOf("run", 0).forGetter(Head::runIndex)
        ).apply(i, Head::new));
    }

    private record Body(List<ItemStack> satchel, double xp, float damageFloor, float damageRun, double killXpFloor, List<String> cleared) {
        static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.listOf().fieldOf("satchel").forGetter(Body::satchel),
                Codec.DOUBLE.fieldOf("satchel_xp").forGetter(Body::xp),
                Codec.FLOAT.fieldOf("damage_floor").forGetter(Body::damageFloor),
                Codec.FLOAT.fieldOf("damage_run").forGetter(Body::damageRun),
                Codec.DOUBLE.fieldOf("kill_xp_floor").forGetter(Body::killXpFloor),
                Codec.STRING.listOf().fieldOf("cleared").forGetter(Body::cleared)
        ).apply(i, Body::new));
    }

    public static final MapCodec<RunState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Head.CODEC.fieldOf("head").forGetter(r -> new Head(SCHEMA, r.gateId, r.area, r.floor, r.startedAt, r.floorStartedAt,
                    r.kills, r.cheatDeathUsed, r.lanterns, r.scoutedAtEntry, r.floorsCleared, r.runIndex)),
            Body.CODEC.fieldOf("body").forGetter(r -> new Body(r.satchel.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList(),
                    r.satchelXp, r.damageFloor, r.damageRun, r.killXpFloor, List.copyOf(r.cleared))),
            Codec.LONG.optionalFieldOf("left_at", -1L).forGetter(r -> r.leftAt)
    ).apply(i, RunState::fromParts));

    private static RunState fromParts(Head h, Body b, long leftAt) {
        RunState r = new RunState();
        r.leftAt = leftAt;
        r.gateId = h.gateId;
        r.area = h.area;
        r.floor = h.floor;
        r.startedAt = h.startedAt;
        r.floorStartedAt = h.floorStartedAt;
        r.kills = h.kills;
        r.cheatDeathUsed = h.cheat;
        r.lanterns = h.lanterns;
        r.scoutedAtEntry = h.scouted;
        r.floorsCleared = h.floorsCleared;
        r.runIndex = h.runIndex;
        for (ItemStack s : b.satchel) r.satchel.add(s.copy());
        r.satchelXp = b.xp;
        r.damageFloor = b.damageFloor;
        r.damageRun = b.damageRun;
        r.killXpFloor = b.killXpFloor;
        r.cleared.addAll(b.cleared);
        return r;
    }
}
