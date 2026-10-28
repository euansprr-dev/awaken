package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** The same home governs a Lord's pursuit, flight and emergency return, including a saved break Lord. */
public final class BossHomeTests {
    private BossHomeTests() {}

    public static void register() {
        AwakenTests.registerIsolated("mobs_boss_home_bounds", 40, h -> {
            var level = h.getLevel();
            BlockPos gate = h.absolutePos(new BlockPos(0, 65, 0));
            AwakenBoss boss = (AwakenBoss) MobHooks.spawnThemed(level, "awaken:fungal_hollow", MobRole.Role.BREAK, gate, 5, Set.of(), "");
            boss.setNoAi(true);
            boss.setHomeTo(gate, 40); // Breaks.spawnLord's public Mob contract, distinct from this Lord's indoor radius 24.
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            boss.saveWithoutId(saved);
            boss.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            h.assertTrue(boss.getHomeRadius() == 40, "a saved unbound Lord keeps its 40-block home");
            Vec3 home = Vec3.atBottomCenterOf(gate);
            var hunter = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            hunter.setNoAi(true);
            hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            hunter.setHealth(1000);
            level.addFreshEntity(hunter);
            hunter.snapTo(home.x + 25, home.y + 25, home.z);
            boss.setTarget(hunter);
            Vec3 slope = home.add(27, 26, 0); // 37.5 from home: legal on a slope, beyond the indoor radius + 10.
            boss.snapTo(slope.x, slope.y, slope.z);
            boss.arena(level);
            h.assertTrue(boss.position().distanceToSqr(slope) < 1e-6, "a break Lord fighting inside its saved home must not snap to the gate");
            h.assertTrue(boss.getTarget() == hunter, "a nearby uphill hunter stays the target");

            // Exercise the actual hunt's tell, dive and orbit. Their destinations all stay in the same home, even uphill at its edge.
            hunter.snapTo(home.x + 28, home.y + 28, home.z);
            MobGoals.FlyHunt hunt = new MobGoals.FlyHunt(boss);
            for (int i = 0; i < 100; i++) {
                hunt.tick();
                var move = boss.getMoveControl();
                Vec3 wanted = new Vec3(move.getWantedX(), move.getWantedY(), move.getWantedZ());
                h.assertTrue(wanted.distanceToSqr(home) <= 40 * 40 + 1e-6, "flight never aims outside its home: " + wanted.subtract(home));
            }

            // A forced displacement can still recover; neither the old target, flight destination nor momentum can send it out again.
            hunter.snapTo(home.x + 60, home.y, home.z);
            h.assertFalse(boss.canAttack(hunter), "a hunter outside home cannot be reacquired immediately");
            boss.getMoveControl().setWantedPosition(home.x + 70, home.y, home.z, 1);
            boss.snapTo(home.x + 60, home.y, home.z);
            boss.setDeltaMovement(0.8, 0.4, 0);
            boss.arena(level);
            h.assertTrue(boss.position().distanceToSqr(home) < 1e-6, "forced displacement returns the Lord home");
            h.assertTrue(boss.getTarget() == null && !boss.getMoveControl().hasWanted() && boss.getDeltaMovement().lengthSqr() == 0,
                    "return clears the escaped target, flight command and velocity");
            boss.arena(level);
            h.assertTrue(boss.position().distanceToSqr(home) < 1e-6, "the next arena check leaves the returned Lord at home");

            // Indoor bosses retain their smaller home and the ten-block emergency margin.
            boss.setHomeTo(gate, boss.def().arena);
            boss.snapTo(home.x + 35, home.y, home.z);
            boss.arena(level);
            h.assertTrue(boss.position().distanceToSqr(home) < 1e-6, "the indoor Lord still returns outside radius 24 + 10");
            boss.discard();
            hunter.discard();
            h.succeed();
        });
    }
}
