package dev.awaken.powers;

import dev.awaken.core.api.Strikes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What a power does to a body's stance (SPEC §50.2): the four weights, under the same rules a blade's blow answers to, so a
 * body in an opening breaks faster and a duel body standing braced barely moves. A longsword swung without stopping is about
 * 8 stance a second; a Haymaker every time it's ready about 3.
 */
public final class PowerStance {
    private PowerStance() {}

    public static final int LIGHT = 3, MEDIUM = 6, HEAVY = 12, MASSIVE = 20;
    // ArmsRules.OPENING_POSTURE and GUARDED_POSTURE: powers reach Arms through Strikes, never its classes
    private static final float OPENING = 1.5f, BRACED = 0.5f;

    public static void add(ServerPlayer by, LivingEntity target, int amount) {
        if (amount <= 0 || target instanceof Player || !target.isAlive()) return;
        float share = Strikes.opening(target) ? OPENING : Strikes.duelist(target) ? BRACED : 1f;
        Strikes.posture(target, Math.max(1, Math.round(amount * share)), by);
    }
}
