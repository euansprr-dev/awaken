package dev.awaken.mobs.entity;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.MobLoot;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The fair guard (SPEC §47.14), the backstop under the audit: for a player at or above the rank of what hurts them, no
 * one blow takes more than half their health, and no one source more than half in any four seconds (a Lord's string, a
 * beam's ticks, a Glass gate's doubled blows). A source is a creature, a Lord and what it calls being one; what has no
 * creature behind it in a gate (its traps, its water, its falling floors) is the gate's. It holds after armour, so what
 * it trims is what would have come off. A rank gap isn't guarded (an E player in an S gate dies as fast as the S gate
 * kills), nor is what passes every protection (the void, /kill), nor anything outside a gate with no creature behind it.
 */
public final class FairGuard {
    private FairGuard() {}

    /** One blow's most, and one source's most in {@link #WINDOW} ticks, as a share of max health. */
    public static final float BLOW = 0.5F, SOURCE = 0.5F;
    public static final int WINDOW = 80; // nine in ten Lord strings land inside it
    /**
     * A Lord's moment: from when it first sees you, everything together may take only this much of you for this long, so
     * walking into a boss room (its first move, its company, the room) never kills.
     */
    public static final float ARRIVAL_SHARE = 0.25F;
    public static final int ARRIVAL = 60;
    /** The key of what a gate does with no creature behind it (entity ids are never negative), of a Lord's moment, and of the unguarded. */
    private static final long GATE = -1L, MOMENT = -2L, NONE = Long.MIN_VALUE;
    /** Blows remembered per source; past this the oldest two are counted as one (the newer's tick, so never less). */
    private static final int KEEP = 8;

    /** What one source has landed on one player lately. */
    private static final class Window {
        final long[] at = new long[KEEP];
        final float[] took = new float[KEEP];
        int n;

        float since(long now) {
            float sum = 0;
            int w = 0;
            for (int i = 0; i < n; i++) {
                if (now - at[i] >= WINDOW) continue;
                at[w] = at[i];
                took[w] = took[i];
                sum += took[w++];
            }
            n = w;
            return sum;
        }

        void add(long now, float amount) {
            if (n == KEEP) {
                took[1] += took[0];
                System.arraycopy(at, 1, at, 0, KEEP - 1);
                System.arraycopy(took, 1, took, 0, KEEP - 1);
                n--;
            }
            at[n] = now;
            took[n++] = amount;
        }
    }

    private static final Map<UUID, Map<Long, Window>> SEEN = new HashMap<>();
    private static final Map<UUID, float[]> TRIMMED = new HashMap<>();
    /** When each player's Lord's moment ends (game time). */
    private static final Map<UUID, Long> MOMENTS = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Pre.class, FairGuard::onDamage);
    }

    private static void onDamage(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative() || e.getNewDamage() <= 0) return;
        DamageSource src = e.getSource();
        if (src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        long now = p.level().getGameTime();
        Long moment = MOMENTS.get(p.getUUID());
        boolean held = moment != null && now < moment;
        long key = held ? MOMENT : keyOf(src, p);
        if (key == NONE) return;
        float max = p.getMaxHealth(), dmg = e.getNewDamage(), most = held ? ARRIVAL_SHARE : SOURCE;
        Map<Long, Window> mine = SEEN.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
        if (mine.size() > 16) mine.values().removeIf(old -> old.since(now) == 0);
        Window w = mine.computeIfAbsent(key, k -> new Window());
        float lands = Math.min(dmg, Math.min(BLOW * max, Math.max(0F, most * max - w.since(now))));
        if (lands < dmg) {
            e.setNewDamage(lands);
            TRIMMED.computeIfAbsent(p.getUUID(), u -> new float[1])[0] += dmg - lands;
        }
        w.add(now, lands);
    }

    /**
     * The source's key when the guard holds it for this player: the creature behind it (or its caller), or the gate for
     * what has none; {@link #NONE} when it doesn't (a rank gap, or nothing of ours behind it).
     */
    static long keyOf(DamageSource src, ServerPlayer p) {
        Entity by = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        if (by instanceof LivingEntity mob && !(by instanceof ServerPlayer)) {
            MobRole role = mob.getExistingData(CoreApi.MOB_ROLE.get()).orElse(null);
            if (role != null && role.role() != MobRole.Role.NONE) {
                if (Progression.rank(p).ordinal() < MobLoot.rankFor(role.contentLevel()).ordinal()) return NONE;
                int caller = mob.getPersistentData().getIntOr("awaken_summoner", -1);
                return caller >= 0 ? caller : mob.getId();
            }
            // a creature with no level of ours (a gate's own, like the Hollow Follower; a vanilla one) is the gate's
        }
        int gate = GateHooks.rankOf(p);
        return gate >= 0 && Progression.rank(p).ordinal() >= gate ? GATE : NONE;
    }

    /** A Lord has first seen this player: its moment begins, if they belong in its fight (one at a time). */
    static void arrive(ServerPlayer p, AwakenMob lord) {
        if (Progression.rank(p).ordinal() < MobLoot.rankFor(lord.role().contentLevel()).ordinal() || inMoment(p)) return;
        MOMENTS.put(p.getUUID(), p.level().getGameTime() + ARRIVAL);
        Map<Long, Window> mine = SEEN.get(p.getUUID());
        if (mine != null) mine.remove(MOMENT);
    }

    static boolean inMoment(ServerPlayer p) {
        Long m = MOMENTS.get(p.getUUID());
        return m != null && p.level().getGameTime() < m;
    }

    /** Forgets what has landed on this player (when they leave, and the audit between the blows it measures). */
    public static void forget(UUID player) {
        SEEN.remove(player);
        TRIMMED.remove(player);
        MOMENTS.remove(player);
    }

    /** What the guard has held back from this player since {@link #forget}. */
    static float trimmed(UUID player) {
        float[] t = TRIMMED.get(player);
        return t == null ? 0 : t[0];
    }
}
