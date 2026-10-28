package dev.awaken.gates.run;

import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which gate monsters are alive in which room. The saved floor roster restores counts before entities load; each
 * entity's persistent tag supplies its ownership. Used for room clears, arena waves, the entity budget and kill XP.
 */
public final class GateMobs {
    private GateMobs() {}

    public record Key(int gate, int area, int floor, int room) {}

    private static final String TAG = "awaken_gate_mob";
    private static final Map<Key, Set<UUID>> ALIVE = new HashMap<>();
    private static final Map<UUID, Key> INDEX = new HashMap<>();
    private static final Map<Integer, Integer> PER_GATE = new HashMap<>();

    /** room = -1 for pressure waves, wardens and other mobs that don't belong to one room. */
    public static void register(Entity e, Key key) {
        CompoundTag t = new CompoundTag();
        t.putInt("gate", key.gate());
        t.putInt("area", key.area());
        t.putInt("floor", key.floor());
        t.putInt("room", key.room());
        e.getPersistentData().put(TAG, t);
        index(e.getUUID(), key);
        remember(e, key);
    }

    private static void index(UUID id, Key key) {
        if (INDEX.putIfAbsent(id, key) != null) return;
        ALIVE.computeIfAbsent(key, k -> new HashSet<>()).add(id);
        PER_GATE.merge(key.gate(), 1, Integer::sum);
    }

    /** Re-index an entity loaded from disk. */
    public static void onLoad(Entity e) {
        if (e instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive()) return;
        CompoundTag t = e.getPersistentData().getCompoundOrEmpty(TAG);
        if (t.isEmpty()) return;
        Key key = new Key(t.getIntOr("gate", -1), t.getIntOr("area", 0), t.getIntOr("floor", 1), t.getIntOr("room", -1));
        index(e.getUUID(), key);
        remember(e, key);
        Encounters.onLoad(e, key);
    }

    private static void remember(Entity e, Key key) {
        if (!(e.level() instanceof ServerLevel level)) return;
        var reg = GateRegistry.get(level.getServer());
        var gate = reg.gate(key.gate());
        if (gate != null && gate.floor(key.area(), key.floor()).living.put(e.getUUID().toString(), key.room()) == null) reg.setDirty();
    }

    /** Restore counts before any players arrive, without loading a chunk or depending on entity load order. */
    public static void restore(MinecraftServer server) {
        for (var g : GateRegistry.get(server).all()) {
            if (g.state == GateState.RELEASED) continue;
            for (var entry : g.floors.entrySet()) {
                String[] af = entry.getKey().split(":");
                int area = Integer.parseInt(af[0]), floor = Integer.parseInt(af[1]);
                entry.getValue().living.forEach((id, room) -> index(UUID.fromString(id), new Key(g.id, area, floor, room)));
            }
        }
        // Older saves have no roster. Adopt entities already loaded before ServerStarted as well as later onLoad calls.
        ServerLevel level = GateService.gateLevel(server);
        if (level != null) for (Entity e : level.getAllEntities()) onLoad(e);
    }

    public static Key keyOf(Entity e) {
        Key k = INDEX.get(e.getUUID());
        if (k != null) return k;
        CompoundTag t = e.getPersistentData().getCompoundOrEmpty(TAG);
        if (t.isEmpty()) return null;
        return new Key(t.getIntOr("gate", -1), t.getIntOr("area", 0), t.getIntOr("floor", 1), t.getIntOr("room", -1));
    }

    /** Forgets a dead or discarded mob. Returns its key, or null if it wasn't tracked. */
    public static Key remove(Entity e) {
        Key k = keyOf(e);
        if (k == null) return null;
        boolean indexed = INDEX.remove(e.getUUID()) != null;
        boolean recorded = false;
        if (e.level() instanceof ServerLevel level) {
            var reg = GateRegistry.get(level.getServer());
            var gate = reg.gate(k.gate());
            if (gate != null && gate.floor(k.area(), k.floor()).living.remove(e.getUUID().toString()) != null) {
                recorded = true;
                reg.setDirty();
            }
        }
        if (!indexed && !recorded) return null;
        Set<UUID> s = ALIVE.get(k);
        if (s != null) {
            s.remove(e.getUUID());
            if (s.isEmpty()) ALIVE.remove(k);
        }
        if (indexed) PER_GATE.merge(k.gate(), -1, Integer::sum);
        return k;
    }

    public static int alive(Key k) {
        Set<UUID> s = ALIVE.get(k);
        return s == null ? 0 : s.size();
    }

    public static Set<UUID> aliveIds(Key k) {
        Set<UUID> s = ALIVE.get(k);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    public static int inGate(int gate) {
        return Math.max(0, PER_GATE.getOrDefault(gate, 0));
    }

    public static void forgetGate(int gate) {
        ALIVE.keySet().removeIf(k -> k.gate() == gate);
        INDEX.values().removeIf(k -> k.gate() == gate);
        PER_GATE.remove(gate);
    }

    public static void clear() {
        ALIVE.clear();
        INDEX.clear();
        PER_GATE.clear();
    }
}
