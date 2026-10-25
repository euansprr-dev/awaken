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
 * Creatures' stances as this client knows them (SPEC §24), installed as {@link Stances} for the plates: the server's
 * snapshot on every change, drained here by the server's own rule ({@link ArmsRules#drained}), so nothing is sent
 * while a stance only recovers. The focus is the creature this fighter last struck, held by the server's rule too
 * (it lets go after a second of looking well away).
 */
final class ArmsStances implements Stances.Source {
    static final ArmsStances INSTANCE = new ArmsStances();

    /** A stance as last sent: its value, its hold, and the client tick it arrived. */
    private static final class Snap {
        float value;
        int max, at;
    }

    private static final Map<Integer, Snap> SNAPS = new HashMap<>();
    static int focus = -1;
    private static int focusSeenAt;

    static void onSnapshot(int entity, float value, int max, int brokenTicks) {
        Snap s = SNAPS.computeIfAbsent(entity, k -> new Snap());
        s.value = value;
        s.max = Math.max(1, max);
        s.at = ArmsPredict.ticks;
        if (brokenTicks > 0) ArmsPredict.BROKEN.put(entity, ArmsPredict.ticks + brokenTicks);
    }

    /** Our blade landed on it: it becomes the focus. */
    static void struck(int entity) {
        focus = entity;
        focusSeenAt = ArmsPredict.ticks;
    }

    @Override
    public float share(Entity e) {
        if (broken(e)) return 1;
        Snap s = SNAPS.get(e.getId());
        if (s == null) return 0;
        return ArmsRules.drained(s.value, ArmsPredict.ticks - s.at, ArmsRules.lordly(s.max)) / s.max;
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
        // a stance drained back to nothing is forgotten
        if (!SNAPS.isEmpty() && (ArmsPredict.ticks & 63) == 0) {
            SNAPS.entrySet().removeIf(en -> ArmsRules.drained(en.getValue().value, ArmsPredict.ticks - en.getValue().at, true) <= 0
                    && ArmsPredict.ticks - en.getValue().at > 40);
        }
    }

    static void reset() {
        SNAPS.clear();
        focus = -1;
    }
}
