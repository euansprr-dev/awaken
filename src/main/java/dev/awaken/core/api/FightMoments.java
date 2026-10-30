package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A player's fight moments, as they happen (SPEC §44.5): Arms reports its blade's moments (the ones its Marks answer:
 * a hit, a finisher, a deflect, a perfect dodge, a broken stance, a kill, a guard, a perfect heavy), Powers each landed
 * cast. Armour's set abilities listen. Server only; reporting with no listener costs a loop over an empty list.
 */
public final class FightMoments {
    private FightMoments() {}

    public enum Moment { HIT, FINISHER, DEFLECT, PERFECT_DODGE, BREAK, KILL, GUARD, PERFECT_HEAVY, CAST }

    public interface Listener {
        /** {@code target}: the creature struck, deflected or dodged (null when there is none, and for a cast). */
        void on(ServerPlayer player, Moment moment, @Nullable LivingEntity target);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static void listen(Listener l) {
        LISTENERS.add(l);
    }

    public static void report(ServerPlayer player, Moment moment, @Nullable LivingEntity target) {
        for (Listener l : LISTENERS) l.on(player, moment, target);
    }
}
