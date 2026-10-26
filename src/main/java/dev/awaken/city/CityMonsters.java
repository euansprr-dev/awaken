package dev.awaken.city;

import dev.awaken.core.api.MobHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Monsters near the city (M4, the Commons step; SPEC §16.3). Euan leaves Carillon with no weapon, only his first power,
 * and the fields at night were crowded. Outside the wall a natural spawn of anything hostile (vanilla's monsters, the
 * Awakened hunters) goes ahead only by {@link #pass(double)}: one in ten over the fields and the roads, easing up the
 * road to two in five by the Wayfarers' fire and to every one 640 out, and never where three already stand within 48 at
 * about its height (to 470 out). A few still come, so the first night keeps a little threat; never a crowd. Inside the wall the citizens' lamps decide (CitizensModule); the camp keeps its own
 * ground (living.wayfarer.Camp, 32 round the fire).
 */
final class CityMonsters {
    private CityMonsters() {}

    /**
     * The natural spawner's position check (26.3 calls finalizeSpawn directly, without FinalizeSpawnEvent: the position
     * check is the one hook a natural spawn passes through with its mob).
     */
    static void onPositionCheck(MobSpawnEvent.PositionCheck e) {
        if (e.getSpawnType() != EntitySpawnReason.NATURAL || !(e.getLevel().getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) return;
        CityLayout l = City.layout();
        Mob mob = e.getEntity();
        if (l == null) return;
        if (!hostile(mob)) return;
        CHECKED.incrementAndGet();
        if (thinned(l, mob, mob.getRandom().nextDouble())) {
            THINNED.incrementAndGet();
            e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        } else if (crowded(l, level, mob)) {
            CROWDED.incrementAndGet();
            e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /** Tours: hostile natural spawns checked, turned away by the roll, turned away by a crowd (since the server started). */
    static final java.util.concurrent.atomic.AtomicInteger CHECKED = new java.util.concurrent.atomic.AtomicInteger(),
            THINNED = new java.util.concurrent.atomic.AtomicInteger(), CROWDED = new java.util.concurrent.atomic.AtomicInteger();

    /** Near the city at most this many hostile creatures stand round a spawn (within 48, at about its height: the caves
     * beneath don't count against the surface, nor the surface against the caves). */
    static final int CROWD = 3, CROWD_TO = 470;

    /** Whether a spawn near the city would join a crowd (only asked of spawns the roll let through: a few a second). */
    static boolean crowded(CityLayout l, ServerLevel level, Mob mob) {
        if (!hostile(mob)) return false;
        int px = l.px(mob.getBlockX()), pz = l.pz(mob.getBlockZ());
        if (l.plan.kind(px, pz) != CityPlan.VOID || (double) px * px + (double) pz * pz > (double) CROWD_TO * CROWD_TO) return false;
        var box = mob.getBoundingBox().inflate(48, 8, 48);
        return level.getEntitiesOfClass(Mob.class, box, CityMonsters::hostile).size() >= CROWD;
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
        if (r <= 330) return 0.10;
        if (r <= 470) return 0.10 + (r - 330) / 140.0 * 0.35;
        if (r <= 640) return 0.45 + (r - 470) / 170.0 * 0.55;
        return 1.0;
    }
}
