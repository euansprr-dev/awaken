package dev.awaken.gates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.gates.world.Cells;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The world's gates, cells and claims (world-global SavedData, {@code <world>/data/awaken/gates.dat}).
 * Cells: never-used ones come from {@code nextCell}; released cells wait in {@code releasedCells} until their region
 * files are deleted while the level is closed, then return to {@code freeCells} on the next start.
 */
public final class GateRegistry extends SavedData {
    public static final int SCHEMA = 1;

    private int nextId = 1;
    private int nextCell = 0;
    private final List<Integer> freeCells = new ArrayList<>();
    private final Set<Integer> releasedCells = new LinkedHashSet<>();
    private final Map<Integer, GateRecord> gates = new TreeMap<>();
    private final Map<String, Claim> pending = new LinkedHashMap<>();
    private final Set<String> done = new LinkedHashSet<>();

    public GateRegistry() {}

    private record Data(int schema, int nextId, int nextCell, List<Integer> free, List<Integer> released, List<GateRecord> gates,
                        List<Claim> pending, List<String> done) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(Data::schema),
                Codec.INT.fieldOf("next_id").forGetter(Data::nextId),
                Codec.INT.fieldOf("next_cell").forGetter(Data::nextCell),
                Codec.INT.listOf().fieldOf("free_cells").forGetter(Data::free),
                Codec.INT.listOf().fieldOf("released_cells").forGetter(Data::released),
                GateRecord.CODEC.listOf().fieldOf("gates").forGetter(Data::gates),
                Claim.CODEC.listOf().fieldOf("pending").forGetter(Data::pending),
                Codec.STRING.listOf().fieldOf("done").forGetter(Data::done)
        ).apply(i, Data::new));
    }

    public static final Codec<GateRegistry> CODEC = Data.CODEC.xmap(d -> {
        GateRegistry r = new GateRegistry();
        r.nextId = d.nextId;
        r.nextCell = d.nextCell;
        r.freeCells.addAll(d.free);
        r.releasedCells.addAll(d.released);
        for (GateRecord g : d.gates) r.gates.put(g.id, g);
        for (Claim c : d.pending) r.pending.put(c.id(), c);
        r.done.addAll(d.done);
        return r;
    }, r -> new Data(SCHEMA, r.nextId, r.nextCell, List.copyOf(r.freeCells), List.copyOf(r.releasedCells),
            List.copyOf(r.gates.values()), List.copyOf(r.pending.values()), List.copyOf(r.done)));

    public static final SavedDataType<GateRegistry> TYPE = new SavedDataType<>(Awaken.id("gates"), GateRegistry::new, CODEC);

    public static GateRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---- gates ------------------------------------------------------------------------------------------------------

    public int nextId() {
        setDirty();
        return nextId++;
    }

    public void put(GateRecord g) {
        gates.put(g.id, g);
        setDirty();
    }

    public GateRecord gate(int id) {
        return gates.get(id);
    }

    public Collection<GateRecord> all() {
        return gates.values();
    }

    private final Map<Integer, GateRecord> byCell = new java.util.HashMap<>();

    /** The live (not released) gate that owns a cell, or null. Cached; rebuilt when a lookup misses. */
    public GateRecord byCell(int cell) {
        GateRecord g = byCell.get(cell);
        if (g != null && g.cell == cell && g.state != GateState.RELEASED) return g;
        byCell.clear();
        for (GateRecord r : gates.values()) if (r.state != GateState.RELEASED) byCell.put(r.cell, r);
        return byCell.get(cell);
    }

    public List<GateRecord> near(BlockPos pos, double radius, boolean standingOnly) {
        List<GateRecord> out = new ArrayList<>();
        double r2 = radius * radius;
        for (GateRecord g : gates.values()) {
            if (standingOnly && !g.state.standing()) continue;
            if (g.state == GateState.RELEASED) continue;
            if (g.pos.distSqr(pos) <= r2) out.add(g);
        }
        return out;
    }

    /** Drops RELEASED records whose cells are already free (keeps the file small). */
    public void prune() {
        // keep released gates whose frame still has to come down: the removal needs the record
        gates.values().removeIf(g -> g.state == GateState.RELEASED && !g.structurePlaced && !releasedCells.contains(g.cell));
        setDirty();
    }

    // ---- cells ------------------------------------------------------------------------------------------------------

    /** Whether a cell can be allocated (4096 cells; released ones return to the free list after cleanup). */
    public boolean hasFreeCell() {
        return !freeCells.isEmpty() || nextCell < Cells.MAX_CELLS;
    }

    public int allocateCell() {
        setDirty();
        if (!freeCells.isEmpty()) return freeCells.remove(0);
        if (nextCell >= Cells.MAX_CELLS) throw new IllegalStateException("no free gate cells");
        return nextCell++;
    }

    public void releaseCell(int cell) {
        releasedCells.add(cell);
        setDirty();
    }

    public Set<Integer> releasedCells() {
        return releasedCells;
    }

    /** A released cell whose region files are gone can be used again. */
    public void cellFreed(int cell) {
        if (releasedCells.remove(cell)) {
            freeCells.add(cell);
            setDirty();
        }
    }

    public int cellsInUse() {
        return nextCell - freeCells.size();
    }

    // ---- claims -----------------------------------------------------------------------------------------------------

    /** Writes a PENDING claim. Refused (false) if the id was ever written, so a reward is paid at most once. */
    public boolean writeClaim(Claim c) {
        if (done.contains(c.id()) || pending.containsKey(c.id())) return false;
        pending.put(c.id(), c);
        setDirty();
        return true;
    }

    public boolean claimed(String id) {
        return done.contains(id) || pending.containsKey(id);
    }

    public List<Claim> pendingFor(UUID player) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : pending.values()) if (c.player().equals(player)) out.add(c);
        return out;
    }

    public boolean isDone(String id) {
        return done.contains(id);
    }

    public void markDone(String id) {
        if (pending.remove(id) != null || !done.contains(id)) {
            done.add(id);
            setDirty();
        }
    }

    /** Forget DONE ids of a gate whose cell is gone (no claim for it can ever be written again). */
    public void forgetClaims(int gateId) {
        String prefix = gateId + ":";
        done.removeIf(s -> s.startsWith(prefix));
        setDirty();
    }

    public int pendingCount() {
        return pending.size();
    }
}
