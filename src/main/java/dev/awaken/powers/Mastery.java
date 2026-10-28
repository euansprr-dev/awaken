package dev.awaken.powers;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.content.PowerSummon;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Power mastery (plan v2 P3): a power grows by being used for real. Mastery points (MP):
 * <ul>
 *   <li>a cast that lands a hit on a living target pays its whole energy cost (1 MP per energy point, channel drain
 *       included);</li>
 *   <li>energy spent in a fight that hit nothing (a Dash out of a swing, a Barrier, a miss) pays {@link #FIGHT_SHARE};</li>
 *   <li>kills pay {@link #KILL} (elites and wardens {@link #ELITE}, bosses {@link #BOSS});</li>
 *   <li>energy spent at the air, out of a fight, pays nothing; nor do hits on your own side or kills of chickens.</li>
 * </ul>
 * A full bar ({@link #need}) raises the power's level by one, exactly as a duplicate Power Core does (the same
 * {@link Loadout.Owned#level()}), so cores stay a shortcut. Mirrors the model in tools/sim/pacing_sim.py.
 *
 * <p>Which power a hit belongs to: the power whose code is running ({@link Casting} marks a cast, a running channel's
 * tick and end, and a passive); else the power that made the summon that struck; else (a bolt in flight, a thrown
 * block, a delayed echo, a reflection) the power of the same Force whose spent energy is still waiting for a hit.
 * Server thread only; nothing here allocates per hit.
 */
public final class Mastery {
    private Mastery() {}

    /** Share of the energy spent in a fight that hit nothing. */
    public static final float FIGHT_SHARE = 0.5f;
    /** A fight: a real exchange (a hit landed on something living, or taken from it) in the last 5 s. */
    public static final int FIGHT_WINDOW = 100;
    /** A hit claims energy spent on its power in the last 8 s (bolts in flight, a held Haymaker, a late echo). */
    public static final int PENDING_WINDOW = 160;
    public static final float PENDING_CAP = 120f;
    /** A hit with nothing to claim (summons, passives): a flat point, at most once a second per power (2 s passives). */
    public static final float HIT = 1f;
    public static final int HIT_GAP = 20;
    public static final int PASSIVE_HIT_GAP = 40;
    public static final float KILL = 6f, ELITE = 18f, BOSS = 60f;
    /** Kills of things with less health than this teach nothing (chickens, rabbits, silverfish). */
    public static final float TRIVIAL_HEALTH = 6f;
    /** Ordinary kills pay while kill tokens last: 6 in a burst, then one every 10 s (an AoE at a mob farm). */
    public static final float KILL_TOKENS = 6f;
    static final int TOKEN_TICKS = 200;
    /**
     * Use-based earning (SPEC 36.8) for the passives that never hit: a second on a wall in a run (Wall Stride), a second run on
     * water (Water Run), a warned blow that then misses you (Danger Sense). Each power takes at most {@link #USE_CAP} a minute
     * from use, about what a main power earns in a minute of fighting (tools/sim/pacing_sim.py: ~81 MP), so a passive you lean
     * on levels about as fast as your main one and standing at a wall pays no more than that.
     */
    public static final float WALL_SECOND = 6f, WATER_SECOND = 5f, DODGED = 15f;
    public static final float USE_CAP = 90f;
    /** Powers that leave summons behind; their summons' strikes count toward them. */
    private static final Set<String> SUMMONERS = Set.of("awaken:legion", "awaken:drone", "awaken:turret", "awaken:drone_swarm");

    /**
     * Mastery points a power needs to grow from {@code powerLevel} to the next (0 at the top). Tuned in
     * tools/sim/pacing_sim.py (MASTERY_NEED) so the power you use most reaches level 3 around player level 20 and level 5
     * around 45. Pure: the client reads it for the Status card.
     */
    public static float need(int powerLevel) {
        return switch (powerLevel) {
            case 1 -> 7_000f;
            case 2 -> 13_000f;
            case 3 -> 32_000f;
            case 4 -> 50_000f;
            default -> 0f;
        };
    }

    private static @Nullable PowerType ctx;
    private static boolean inCast;
    private static final Map<PowerType, String> KEYS = new IdentityHashMap<>();

    /** The power's id string, built once (Identifier.toString makes a new string every call). */
    static String key(PowerType type) {
        String k = KEYS.get(type);
        if (k == null) KEYS.put(type, k = type.id.toString());
        return k;
    }

    /** The power whose code is running right now, or null. */
    public static @Nullable PowerType current() {
        return ctx;
    }

    /** Makes {@code power} the owner of hits until {@link #exit}; returns the previous owner to restore. */
    static @Nullable PowerType enter(@Nullable PowerType power) {
        PowerType prev = ctx;
        ctx = power;
        return prev;
    }

    static void exit(@Nullable PowerType previous) {
        ctx = previous;
    }

    /** The behaviour of a fresh cast is about to run (its own hits are paid with the cast's cost once that is known). */
    static boolean beginCast() {
        boolean prev = inCast;
        inCast = true;
        return prev;
    }

    static void endCast(boolean previous) {
        inCast = previous;
    }

    // ---- events from the cast pipeline --------------------------------------------------------------------------------

    /** A cast succeeded and spent {@code energy}. If it already landed a hit, the whole cost counts. */
    static void cast(ServerPlayer player, PowerType type, float energy, long now) {
        PowerState st = PowerState.of(player);
        PowerState.Track t = st.track(type);
        if (SUMMONERS.contains(key(type))) rememberSummons(player, st, type);
        if (t.landedInCast) {
            t.landedInCast = false;
            if (energy > 0) add(player, type, energy);
            return;
        }
        spent(player, st, t, energy, now);
    }

    /** The cast was refused after all: forget a hit it may have landed. */
    static void castFailed(ServerPlayer player, PowerType type) {
        PowerState st = PowerState.peek(player);
        if (st != null) st.track(type).landedInCast = false;
    }

    /** Energy a running power drained this tick (paid when one of its hits lands, half of it if in a fight). */
    static void drained(ServerPlayer player, PowerState st, PowerType type, float energy, long now) {
        if (energy > 0) spent(player, st, st.track(type), energy, now);
    }

    private static void spent(ServerPlayer player, PowerState st, PowerState.Track t, float energy, long now) {
        if (energy <= 0) return;
        if (now - t.pendingAt > PENDING_WINDOW) t.pending = 0;
        float fight = now - st.lastExchange < FIGHT_WINDOW ? energy * FIGHT_SHARE : 0;
        t.pending = Math.min(PENDING_CAP, t.pending + energy - fight);
        t.pendingAt = now;
        if (fight > 0) add(player, t.type, fight);
    }

    /** New summons of this cast (still on tick 0) are remembered, so their strikes count toward the power. */
    private static void rememberSummons(ServerPlayer player, PowerState st, PowerType type) {
        st.summons.keySet().removeIf(Entity::isRemoved);
        for (PowerSummon s : player.level().getEntitiesOfClass(PowerSummon.class, player.getBoundingBox().inflate(48),
                s -> s.tickCount == 0 && s.getOwner() == player)) {
            st.summons.put(s, type);
        }
    }

    /**
     * A hit from {@code owner} landed on a living target that isn't on their side (called by {@link Combat#hurtFrom}
     * after the damage went in). {@code direct} is the summon, bolt shooter or thrown block that carried it, if any.
     */
    static void landed(ServerPlayer owner, @Nullable Entity direct, LivingEntity target, Forces.@Nullable Force force, long now) {
        PowerState st = PowerState.of(owner);
        st.lastExchange = now;
        PowerType type = ctx;
        if (type == null && direct instanceof PowerSummon) type = st.summons.get(direct);
        PowerState.Track t = type != null ? st.track(type) : waiting(st, force, now);
        if (t == null) return;
        type = t.type;
        float mp = 0;
        if (inCast && type == ctx) {
            // a hit inside the power's own fresh cast: that cast's cost is paid in full when the pipeline spends it.
            // Energy older casts left waiting is not claimed here (spam at the air, then one hit, earns one cast).
            t.landedInCast = true;
        } else if (t.pending > 0 && now - t.pendingAt <= PENDING_WINDOW) {
            // a bolt landing, a channel's tick, a held punch released: claim at most about one cast's worth per hit
            float claim = Math.min(t.pending, Math.max(1f, type.cost) + type.drain * 2);
            mp += claim;
            t.pending -= claim;
        } else if (now - t.lastFlatHit >= (type.isPassive() ? PASSIVE_HIT_GAP : HIT_GAP)) {
            mp += HIT;
            t.lastFlatHit = now;
        }
        if (target.isDeadOrDying() && target.getMaxHealth() >= TRIVIAL_HEALTH) mp += killValue(t, target, now);
        if (mp > 0) add(owner, type, mp);
    }

    /** The power of this Force whose energy most recently went out without a hit yet (a bolt in flight, an echo). */
    private static PowerState.@Nullable Track waiting(PowerState st, Forces.@Nullable Force force, long now) {
        PowerState.Track best = null;
        for (int i = 0; i < st.trackList.size(); i++) {
            PowerState.Track t = st.trackList.get(i);
            if (t.pending <= 0 || now - t.pendingAt > PENDING_WINDOW || t.type.primaryForce() != force) continue;
            if (best == null || t.pendingAt > best.pendingAt) best = t;
        }
        return best;
    }

    private static float killValue(PowerState.Track t, LivingEntity target, long now) {
        Optional<MobRole> role = target.getExistingData(CoreApi.MOB_ROLE.get());
        MobRole.Role r = role.isPresent() ? role.get().role() : MobRole.Role.NONE;
        if (r == MobRole.Role.BOSS) return BOSS;
        if (r == MobRole.Role.ELITE || r == MobRole.Role.WARDEN) return ELITE;
        t.killTokens = Math.min(KILL_TOKENS, t.killTokens + (float) (now - t.tokensAt) / TOKEN_TICKS);
        t.tokensAt = now;
        if (t.killTokens < 1) return 0;
        t.killTokens -= 1;
        return KILL;
    }

    /** Pays a passive's use (SPEC 36.8) toward its bar, within its per-minute cap. Returns what was paid. */
    public static float use(ServerPlayer player, String powerId, float mp) {
        PowerType type = PowerRegistry.get(powerId);
        if (type == null || mp <= 0 || Powers.loadout(player).get(key(type)) == null) return 0;
        PowerState.Track t = PowerState.of(player).track(type);
        long minute = player.level().getServer().overworld().getGameTime() / 1200;
        if (t.useMinute != minute) {
            t.useMinute = minute;
            t.usePaid = 0;
        }
        float pay = Math.min(mp, USE_CAP - t.usePaid);
        if (pay <= 0) return 0;
        t.usePaid += pay;
        add(player, type, pay);
        return pay;
    }

    // ---- the bar --------------------------------------------------------------------------------------------------

    /**
     * Adds mastery to a power the player owns and levels it up when the bar fills (more than once if it overflows).
     * Returns the levels gained. Public for admin commands and tests.
     */
    public static int add(ServerPlayer player, PowerType type, float mp) {
        Loadout l = Powers.loadout(player);
        String id = key(type);
        Loadout.Owned o = l.owned.get(id);
        if (o == null || mp <= 0 || o.level() >= Loadout.MAX_LEVEL) return 0;
        Loadout.Bar bar = l.bar(id);
        bar.points += mp;
        int gained = 0;
        while (o.level() < Loadout.MAX_LEVEL && bar.points >= need(o.level())) {
            bar.points -= need(o.level());
            o = new Loadout.Owned(id, o.variant(), o.level() + 1);
            gained++;
        }
        PowerState.of(player).masteryDirty = true;
        if (gained == 0) return 0;
        l.owned.put(id, o);
        if (o.level() >= Loadout.MAX_LEVEL) l.mastery.remove(id);
        announce(player, type, o.level());
        DirectorLog.log(player, "powers", "mastery: " + id + " reached level " + o.level() + " through use");
        dev.awaken.core.Playtest.log(player, "power_mastery", "power", id + "@" + o.level());
        Powers.markDirty(player);
        return gained;
    }

    /** A duplicate core raised the level: keep the same share of the bar toward the new next level. */
    static void coreLevelled(Loadout l, String id, int fromLevel, int toLevel) {
        Loadout.Bar bar = l.mastery.get(id);
        if (bar == null) return;
        float before = need(fromLevel), after = need(toLevel);
        if (after <= 0 || before <= 0) {
            l.mastery.remove(id);
            return;
        }
        bar.points = Math.min(after - 1, bar.points / before * after);
    }

    private static void announce(ServerPlayer player, PowerType type, int level) {
        // the ladder's moment names the new step and what it does (SPEC 36.6)
        dev.awaken.powers.ladder.Evolution.levelled(player, type, level - 1, level);
    }
}
