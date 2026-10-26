package dev.awaken.city;

import dev.awaken.core.api.MobHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Monsters near the city (M4, the Commons step; SPEC §16.3). Euan leaves Carillon with no weapon, only his first power,
 * and the fields at night were crowded. Outside the wall a natural spawn of anything hostile (vanilla's monsters, the
 * Awakened hunters) goes ahead only by {@link #pass(double)}: one in eight over the fields and the roads, easing up the
 * road to two in five by the Wayfarers' fire and to every one 640 out. A few still come, so the first night keeps a
 * little threat; never a crowd. Inside the wall the citizens' lamps decide (CitizensModule); the camp keeps its own
 * ground (living.wayfarer.Camp, 32 round the fire).
 */
final class CityMonsters {
    private CityMonsters() {}

    static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        if (e.getSpawnType() != EntitySpawnReason.NATURAL || !(e.getLevel().getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) return;
        CityLayout l = City.layout();
        Mob mob = e.getEntity();
        if (l != null && thinned(l, mob, mob.getRandom().nextDouble())) e.setSpawnCancelled(true);
    }

    /** Whether this natural spawn is one the city's surroundings turn away, for a roll in 0..1. */
    static boolean thinned(CityLayout l, Mob mob, double roll) {
        if (!hostile(mob)) return false;
        int px = l.px(mob.getBlockX()), pz = l.pz(mob.getBlockZ());
        if (l.plan.kind(px, pz) != CityPlan.VOID) return false; // inside the wall the lamps decide
        return roll >= pass(Math.sqrt((double) px * px + (double) pz * pz));
    }

    /** Anything that would come at you: a monster, or one of the Awakened creatures that isn't peaceful. */
    static boolean hostile(Mob mob) {
        MobCategory cat = mob.getType().getCategory();
        if (cat == MobCategory.MONSTER || mob instanceof Enemy) return true;
        return "AWAKEN_FAUNA".equals(cat.name()) && !MobHooks.peaceful(mob);
    }

    /** The share of hostile natural spawns that go ahead this far from the city's centre. */
    static double pass(double r) {
        if (r <= 330) return 0.12;
        if (r <= 470) return 0.12 + (r - 330) / 140.0 * 0.33;
        if (r <= 640) return 0.45 + (r - 470) / 170.0 * 0.55;
        return 1.0;
    }
}
