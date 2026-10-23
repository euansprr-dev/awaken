package dev.awaken.powers;

import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transient per-player server state: running powers, short buffs, dash chains, combat timer and cached perks.
 * Never saved: everything here is seconds long. Cleared on logout, death and world change (see {@link Casting}).
 */
public final class PowerState {
    private static final Map<UUID, PowerState> STATES = new HashMap<>();

    public static PowerState of(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new PowerState());
    }

    static @Nullable PowerState peek(Player player) {
        return STATES.get(player.getUUID());
    }

    static void forget(Player player) {
        STATES.remove(player.getUUID());
    }

    static void clearAll() {
        STATES.clear();
    }

    public record Buff(long until, double value) {}

    final List<Running> running = new ArrayList<>();
    private final Map<String, Buff> buffs = new HashMap<>();

    // dash chains
    int dashChain;
    boolean wasOnGround = true;

    // combat
    long lastCombat = -10_000;

    // cached perks (refreshed every second)
    double regenPerk;
    double cdrPerk;
    double flowPerk;
    double forceAmp;
    long perksAt = -1;

    // passives
    int passiveTick;
    long lastDangerHash;
    int flowTickBank;

    boolean loadoutDirty;

    void refreshPerks(ServerPlayer player, long now) {
        if (perksAt >= 0 && now - perksAt < 20) return;
        perksAt = now;
        regenPerk = Progression.perk(player, Perks.ENERGY_REGEN);
        cdrPerk = Progression.perk(player, Perks.COOLDOWN_REDUCTION);
        flowPerk = Progression.perk(player, Perks.FLOW_DURATION);
        forceAmp = Progression.perk(player, Perks.FORCE_AMP);
    }

    /** One tick spent in Flow (counted in batches as {@code awaken:flow_ticks}). */
    public void flowTick() {
        flowTickBank++;
    }

    // ---- buffs ------------------------------------------------------------------------------------------------------

    public void buff(String key, long until, double value) {
        buffs.put(key, new Buff(until, value));
    }

    public double buff(String key, long now) {
        Buff b = buffs.get(key);
        if (b == null) return 0;
        if (b.until() < now) {
            buffs.remove(key);
            return 0;
        }
        return b.value();
    }

    public boolean has(String key, long now) {
        return buff(key, now) != 0;
    }

    public void clearBuff(String key) {
        buffs.remove(key);
    }

    /** Multiplier on power damage from temporary buffs (Overload). */
    public float damageBuff(long now) {
        return (float) (1.0 + buff(Buffs.OVERLOAD, now));
    }

    // ---- combat -----------------------------------------------------------------------------------------------------

    public void markCombat(ServerPlayer player) {
        lastCombat = player.level().getServer().overworld().getGameTime();
    }

    public boolean inCombat(long now) {
        return now - lastCombat < 100;
    }

    /** A cast near hostiles counts as combat (for blocks placed mid-fight). */
    void markCombatIfFoesNear(ServerPlayer player) {
        if (!Combat.foesAround(player, player.position(), 10).isEmpty()) markCombat(player);
    }

    public @Nullable Running running(String key) {
        for (Running r : running) if (r.key.equals(key) && !r.ended) return r;
        return null;
    }

    public List<Running> runningList() {
        return running;
    }

    /** Buff keys shared between powers and the damage hooks. */
    public static final class Buffs {
        private Buffs() {}

        public static final String OVERLOAD = "overload";
        public static final String BACKSTAB = "backstab";
        public static final String BRACE = "brace";
        public static final String BRACE_REFLECT = "brace_reflect";
        public static final String NANITE = "nanite";
        public static final String CLOAK = "cloak";
        public static final String READ_INTENT = "read_intent";
        public static final String FORESIGHT = "foresight";
        public static final String TWIN = "twin";
        public static final String NO_FALL = "no_fall";
        public static final String BULWARK = "bulwark";
        public static final String OVERLOAD_COST = "overload_cost";
        public static final String CONDUIT = "conduit";
    }
}
