package dev.awaken.gates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.gates.content.GatePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One gate in the world registry (SPEC section 7, plan contract "Gate spawn"). The plan is frozen at creation, so what
 * the Veil showed you is what's inside. Mutated only on the server thread; the registry is marked dirty after changes.
 */
public final class GateRecord {
    public final int id;
    public GatePlan plan;
    public BlockPos pos;
    /** 0: the portal stands in a plane along X (you walk through along Z); 1: along Z. */
    public final int axis;
    public int cell;
    public final long createdDay;
    public long breaksOnPlayDay;
    public GateState state;
    public Optional<UUID> owner;
    public final Set<UUID> party = new LinkedHashSet<>();
    public final Set<UUID> inside = new LinkedHashSet<>();
    public final List<String> trace = new ArrayList<>();
    public boolean scripted;
    public boolean structurePlaced;
    public boolean interiorReady;
    public boolean warned;
    public boolean nearHearth;
    public boolean bossDead, annexBossDead;
    public Optional<UUID> boss = Optional.empty();
    public Optional<UUID> annexBoss = Optional.empty();
    public Optional<UUID> lord = Optional.empty();
    public final Map<String, FloorState> floors = new HashMap<>();
    /** Echo Gate: packed positions of the first run through each floor, keyed "area:floor". */
    public final Map<String, List<Long>> echoPaths = new HashMap<>();
    /** How many runs each player has started here (claim ids stay unique across re-entries). */
    public final Map<UUID, Integer> entries = new HashMap<>();
    public long clearedAt = -1;
    /** Floors whose clear bonus each player was already paid ("uuid area:floor"): a re-entered gate never pays twice. */
    public final Set<String> floorsPaid = new LinkedHashSet<>();
    /** Broken, but the spill wave and the Lord haven't come out yet (survives a restart). */
    public boolean spillPending;
    /** Broken and its roaming Lord fell: the gate only waits to be released. */
    public boolean breakHeld;
    /** A wandering gate (an anomaly): game time it fades if nobody is inside, or -1 for an ordinary gate. */
    public long expiresAt = -1;

    public boolean wandering() {
        return expiresAt >= 0;
    }

    public GateRecord(int id, GatePlan plan, BlockPos pos, int axis, int cell, long createdDay, long breaksOnPlayDay,
                      GateState state, Optional<UUID> owner) {
        this.id = id;
        this.plan = plan;
        this.pos = pos;
        this.axis = axis;
        this.cell = cell;
        this.createdDay = createdDay;
        this.breaksOnPlayDay = breaksOnPlayDay;
        this.state = state;
        this.owner = owner;
    }

    public String key() {
        return Integer.toString(id);
    }

    public FloorState floor(int area, int floor) {
        return floors.computeIfAbsent(area + ":" + floor, k -> new FloorState());
    }

    /** Moves to {@code to} if the lifecycle allows it. */
    public boolean transition(GateState to) {
        if (state == to) return true;
        if (!state.canGo(to)) return false;
        state = to;
        return true;
    }

    /**
     * Scripted gates never break, and none do once the story has calmed the gates ("Break the Signal"), except the
     * gates of a player who hasn't reached that ending yet: theirs still break (SPEC "Together").
     */
    public boolean neverBreaks(net.minecraft.server.MinecraftServer server) {
        return scripted || wandering() || plan.skybornTrial() || dev.awaken.core.api.StoryHooks.gatesCalm(server, owner);
    }

    public long breaksIn(long today) {
        return Math.max(0, breaksOnPlayDay - today);
    }

    /** Where players stand when they come out: two blocks in front of the portal. */
    public BlockPos mouth() {
        return axis == 0 ? pos.offset(0, 1, 3) : pos.offset(3, 1, 0);
    }

    // ---- codec ------------------------------------------------------------------------------------------------------

    private record Head(int id, GatePlan plan, BlockPos pos, int axis, int cell, long createdDay, long breaks, GateState state,
                        Optional<UUID> owner, List<UUID> party, List<UUID> inside, List<String> trace) {
        static final Codec<Head> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Head::id),
                GatePlan.CODEC.fieldOf("plan").forGetter(Head::plan),
                BlockPos.CODEC.fieldOf("pos").forGetter(Head::pos),
                Codec.INT.fieldOf("axis").forGetter(Head::axis),
                Codec.INT.fieldOf("cell").forGetter(Head::cell),
                Codec.LONG.fieldOf("created_day").forGetter(Head::createdDay),
                Codec.LONG.fieldOf("breaks_on").forGetter(Head::breaks),
                GateState.CODEC.fieldOf("state").forGetter(Head::state),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Head::owner),
                UUIDUtil.CODEC.listOf().fieldOf("party").forGetter(Head::party),
                UUIDUtil.CODEC.listOf().fieldOf("inside").forGetter(Head::inside),
                Codec.STRING.listOf().fieldOf("trace").forGetter(Head::trace)
        ).apply(i, Head::new));
    }

    private record Flags(boolean scripted, boolean placed, boolean ready, boolean warned, boolean nearHearth, boolean bossDead,
                         boolean annexBossDead, Optional<UUID> boss, Optional<UUID> annexBoss, Optional<UUID> lord, long clearedAt) {
        static final Codec<Flags> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("scripted").forGetter(Flags::scripted),
                Codec.BOOL.fieldOf("placed").forGetter(Flags::placed),
                Codec.BOOL.fieldOf("ready").forGetter(Flags::ready),
                Codec.BOOL.fieldOf("warned").forGetter(Flags::warned),
                Codec.BOOL.fieldOf("near_hearth").forGetter(Flags::nearHearth),
                Codec.BOOL.fieldOf("boss_dead").forGetter(Flags::bossDead),
                Codec.BOOL.fieldOf("annex_boss_dead").forGetter(Flags::annexBossDead),
                UUIDUtil.CODEC.optionalFieldOf("boss").forGetter(Flags::boss),
                UUIDUtil.CODEC.optionalFieldOf("annex_boss").forGetter(Flags::annexBoss),
                UUIDUtil.CODEC.optionalFieldOf("lord").forGetter(Flags::lord),
                Codec.LONG.optionalFieldOf("cleared_at", -1L).forGetter(Flags::clearedAt)
        ).apply(i, Flags::new));
    }

    public static final Codec<GateRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Head.CODEC.fieldOf("head").forGetter(g -> new Head(g.id, g.plan, g.pos, g.axis, g.cell, g.createdDay, g.breaksOnPlayDay,
                    g.state, g.owner, List.copyOf(g.party), List.copyOf(g.inside), List.copyOf(g.trace))),
            Flags.CODEC.fieldOf("flags").forGetter(g -> new Flags(g.scripted, g.structurePlaced, g.interiorReady, g.warned,
                    g.nearHearth, g.bossDead, g.annexBossDead, g.boss, g.annexBoss, g.lord, g.clearedAt)),
            Codec.unboundedMap(Codec.STRING, FloorState.CODEC).fieldOf("floors").forGetter(g -> Map.copyOf(g.floors)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("echo", Map.of()).forGetter(g -> Map.copyOf(g.echoPaths)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).optionalFieldOf("entries", Map.of()).forGetter(g -> Map.copyOf(g.entries)),
            Codec.STRING.listOf().optionalFieldOf("floors_paid", List.of()).forGetter(g -> List.copyOf(g.floorsPaid)),
            Codec.BOOL.optionalFieldOf("spill_pending", false).forGetter(g -> g.spillPending),
            Codec.BOOL.optionalFieldOf("break_held", false).forGetter(g -> g.breakHeld),
            Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(g -> g.expiresAt)
    ).apply(i, (h, f, floors, echo, entries, paid, spill, held, expires) -> {
        GateRecord g = new GateRecord(h.id, h.plan, h.pos, h.axis, h.cell, h.createdDay, h.breaks, h.state, h.owner);
        g.party.addAll(h.party);
        g.inside.addAll(h.inside);
        g.trace.addAll(h.trace);
        g.scripted = f.scripted;
        g.structurePlaced = f.placed;
        g.interiorReady = f.ready;
        g.warned = f.warned;
        g.nearHearth = f.nearHearth;
        g.bossDead = f.bossDead;
        g.annexBossDead = f.annexBossDead;
        g.boss = f.boss;
        g.annexBoss = f.annexBoss;
        g.lord = f.lord;
        g.clearedAt = f.clearedAt;
        g.floors.putAll(floors);
        echo.forEach((k, v) -> g.echoPaths.put(k, new ArrayList<>(v)));
        g.entries.putAll(entries);
        g.floorsPaid.addAll(paid);
        g.spillPending = spill;
        g.breakHeld = held;
        g.expiresAt = expires;
        return g;
    }));
}
