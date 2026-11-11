package dev.awaken.arms.client;

import dev.awaken.arms.ArmsRules;
import dev.awaken.core.api.Stances;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Creatures' stances as this client knows them (SPEC §25), installed as {@link Stances} for the plates: the server's
 * snapshot on every change, drained here by the server's own rule ({@link ArmsRules#drained}), so nothing is sent
 * while a stance only recovers; a held one (a duel body fighting, SPEC §47.3) doesn't drain until the next snapshot
 * lets it go. The focus is the creature this fighter last struck, held by the server's rule too (it lets go after a
 * second of looking well away).
 */
final class ArmsStances implements Stances.Source {
    static final ArmsStances INSTANCE = new ArmsStances();

    /**
     * A stance as last sent: its value, its hold, the client tick it arrived, the tick it last rose, whether it is held
     * now, and whether it ever was (a duel body: its plate shows the stance from its first blow, SPEC §47.3).
     */
    private static final class Snap {
        float value;
        int max, at, rose = -100_000;
        boolean held, duel;

        float now() {
            return held ? value : ArmsRules.drained(value, ArmsPredict.ticks - at, ArmsRules.lordly(max));
        }

        boolean draining() {
            return !held && value > 0 && ArmsPredict.ticks - at > ArmsRules.POSTURE_IDLE_TICKS && now() > 0;
        }
    }

    private static final Map<Integer, Snap> SNAPS = new HashMap<>();
    /** Our blows that landed on each creature (the plate's one hint reads it, SPEC §47.3). */
    private static final Map<Integer, Integer> STRUCK = new HashMap<>();
    static int focus = -1;
    private static int focusSeenAt;

    static void onSnapshot(int entity, float value, int max, int brokenTicks, boolean held) {
        Snap s = SNAPS.computeIfAbsent(entity, k -> new Snap());
        if (value > s.now() + 0.5F) s.rose = ArmsPredict.ticks; // the plate flashes what a blow put in
        s.value = value;
        s.max = Math.max(1, max);
        s.at = ArmsPredict.ticks;
        s.held = held;
        if (held) s.duel = true;
        if (brokenTicks > 0) ArmsPredict.BROKEN.put(entity, ArmsPredict.ticks + brokenTicks);
    }

    /** Our blade landed on it: it becomes the focus. */
    static void struck(int entity) {
        focus = entity;
        focusSeenAt = ArmsPredict.ticks;
        STRUCK.merge(entity, 1, Integer::sum);
    }

    @Override
    public float share(Entity e) {
        if (broken(e)) return 1;
        Snap s = SNAPS.get(e.getId());
        return s == null ? 0 : s.now() / s.max;
    }

    @Override
    public boolean duel(Entity e) {
        Snap s = SNAPS.get(e.getId());
        return s != null && s.duel;
    }

    @Override
    public boolean draining(Entity e) {
        Snap s = SNAPS.get(e.getId());
        return s != null && s.draining();
    }

    @Override
    public int struck(Entity e) {
        return STRUCK.getOrDefault(e.getId(), 0);
    }

    @Override
    public int sinceRise(Entity e) {
        Snap s = SNAPS.get(e.getId());
        return s == null ? Integer.MAX_VALUE : ArmsPredict.ticks - s.rose;
    }

    @Override
    public boolean broken(Entity e) {
        return ArmsPredict.BROKEN.containsKey(e.getId());
    }

    @Override
    public boolean focused(Entity e) {
        return e.getId() == focus;
    }

    static void tick() {
        if ((ArmsPredict.ticks & 3) != 0) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (focus >= 0) {
            Entity f = p == null || p.level() == null ? null : p.level().getEntity(focus);
            if (f == null || !f.isAlive() || f.distanceToSqr(p) > 64) {
                focus = -1;
            } else {
                float toward = (float) (Mth.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F;
                if (ArmsRules.lookedAt(toward, p.getYRot())) focusSeenAt = ArmsPredict.ticks;
                else if (ArmsPredict.ticks - focusSeenAt >= ArmsRules.FOCUS_DROP_TICKS) focus = -1;
            }
        }
        // a stance drained back to nothing is forgotten; a duel body's stays while it is here (its plate shows it empty)
        if ((ArmsPredict.ticks & 63) == 0 && p != null && p.level() != null) {
            var level = p.level();
            SNAPS.entrySet().removeIf(en -> en.getValue().duel ? level.getEntity(en.getKey()) == null
                    : !en.getValue().held && en.getValue().now() <= 0 && ArmsPredict.ticks - en.getValue().at > 40);
            STRUCK.keySet().removeIf(id -> level.getEntity(id) == null);
        }
    }

    static void reset() {
        SNAPS.clear();
        STRUCK.clear();
        focus = -1;
    }
}
