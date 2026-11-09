package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.HashMap;
import java.util.Map;

/**
 * What anyone watching a fighter could see them doing (SPEC §48.1): what the body has started, never a key that was
 * pressed. A foe reads this, and only counts a fact once it has been true for its reaction time. Vanilla's facts are read
 * here; Arms installs what only it knows (a heavy winding up, a blow's recovery, a dodge), Powers a cast. Server side.
 */
public final class Watch {
    private Watch() {}

    public static final int DRINK = 1, GUARD = 1 << 1, HEAVY = 1 << 2, RECOVERY = 1 << 3, DODGE = 1 << 4, CAST = 1 << 5;

    public interface Source {
        int facts(ServerPlayer player);
    }

    private static Source arms = p -> 0, powers = p -> 0;
    /** A test's fighter, doing what the test says it is. */
    private static final Map<Integer, Integer> STAGED = new HashMap<>();

    public static void installArms(Source s) {
        arms = s;
    }

    public static void installPowers(Source s) {
        powers = s;
    }

    /** The facts true of this fighter now, as bits. */
    public static int facts(LivingEntity e) {
        int f = 0;
        if (e.isUsingItem()) {
            ItemUseAnimation a = e.getUseItem().getUseAnimation();
            if (a == ItemUseAnimation.EAT || a == ItemUseAnimation.DRINK) f |= DRINK;
        }
        if (e.isBlocking()) f |= GUARD;
        if (e instanceof ServerPlayer p) f |= arms.facts(p) | powers.facts(p);
        if (!STAGED.isEmpty()) f |= STAGED.getOrDefault(e.getId(), 0);
        return f;
    }

    /** Tests: this fighter also counts as doing {@code facts} (0 ends it). */
    public static void stage(LivingEntity e, int facts) {
        if (facts == 0) STAGED.remove(e.getId());
        else STAGED.put(e.getId(), facts);
    }
}
