package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/** The Creature Kit (SPEC §35.2-34.4): its data loads and checks out, moves sync, blows land where they're drawn, phases. */
public final class KitTests {
    private KitTests() {}

    static AwakenBoss spawn(ServerLevel level, net.minecraft.gametest.framework.GameTestHelper h, String id) {
        MobDef def = Bestiary.get(id);
        AwakenMob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(4.5, 1, 4.5)), 10,
                new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        h.assertTrue(m instanceof AwakenBoss, id + " spawned as a boss");
        return (AwakenBoss) m;
    }

    public static void register() {
        // every kit the index names loads, passes the load checks (clips match ticks, tells, holds, the script's coverage)
        AwakenTests.register("kit_tables_load", 20, h -> {
            h.assertTrue(Kits.failed().isEmpty(), "every kit loads: " + Kits.failed());
            h.assertTrue(Kits.all().size() == Kits.named().size() && Kits.all().size() >= 3, "the three showcase kits load: " + Kits.all().keySet());
            for (Kits.Kit k : Kits.all().values()) {
                h.assertTrue(Bestiary.get(k.id()) != null, k.id() + " is in the bestiary");
                h.assertTrue(k.fight() != null, k.id() + " has a fight script");
                h.assertTrue(Kits.problems(k).isEmpty(), k.id() + ": " + Kits.problems(k));
                for (KitMoves.Move m : k.moves().moves()) {
                    if (m.kind() == dev.awaken.core.api.Strikes.Kind.NONE) continue;
                    // the tell is the rule: even enraged, no blow comes faster than its class allows
                    KitMoves.Played p = KitMoves.played(m, k.fight().enrage().speed(), 0);
                    h.assertTrue(p.windup() >= KitMoves.minWindup(m.kind()), k.id() + "/" + m.id() + " enraged wind-up " + p.windup());
                }
            }
            h.succeed();
        });

        // a move plays on the server and syncs (move, start) for the client to play the same clip at the same tick
        AwakenTests.register("kit_move_sync", 160, h -> {
            ServerLevel level = h.getLevel();
            AwakenBoss boss = spawn(level, h, "ember_tyrant");
            int want = boss.kit().moves().index("hammer");
            h.assertTrue(boss.playKitMove("hammer"), "the hammer plays");
            h.assertTrue(boss.kitMove() == want, "its index is synced: " + boss.kitMove() + " for " + want);
            h.assertTrue(boss.kitStart() == (int) level.getGameTime(), "with the tick it started");
            h.assertTrue("hammer".equals(boss.kitPlaying()), "the server is playing it");
            h.succeedWhen(() -> {
                h.assertTrue(boss.kitPlaying() == null && boss.kitMove() < 0, "the move ends and the sync clears");
                boss.discard();
            });
        });

        // the capsules are where the clip draws the blow: the hammer lands in front, the stomp's shockwave reaches behind
        AwakenTests.register("kit_hit_volume", 20, h -> {
            Kits.Kit k = Kits.get("ember_tyrant");
            h.assertTrue(k != null, "the Tyrant's kit");
            AABB ahead = new AABB(-0.3, 0, 2.2, 0.3, 1.8, 2.8), behind = new AABB(-0.3, 0, -2.8, 0.3, 1.8, -2.2);
            h.assertTrue(touches(k.moves().get(k.moves().index("hammer")), ahead), "the hammer meets someone 2.5 blocks in front");
            h.assertTrue(!touches(k.moves().get(k.moves().index("hammer")), behind), "and not someone behind it");
            h.assertTrue(touches(k.moves().get(k.moves().index("stomp")), behind), "the stomp reaches the one behind it");
            h.succeed();
        });

        // crossing a phase line mid-fight plays that phase's transition before anything from its pool
        AwakenTests.register("kit_phase_order", 260, h -> {
            ServerLevel level = h.getLevel();
            AwakenBoss boss = spawn(level, h, "ember_tyrant");
            Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
            pig.setPos(h.absoluteVec(new Vec3(4.5, 1, 9.5)));
            pig.setNoAi(true); // not invulnerable: Mob.getTarget drops a target that can't be seen as an enemy
            level.addFreshEntity(pig);
            boss.setTarget(pig);
            boss.setHealth(boss.getMaxHealth() * 0.6F);
            String[] first = {null};
            h.onEachTick(() -> {
                boss.setTarget(pig);
                String now = boss.kitPlaying();
                if (first[0] == null && now != null && !now.equals("roar")) first[0] = now;
            });
            h.succeedWhen(() -> {
                h.assertTrue(first[0] != null, "a move after the intro");
                h.assertTrue(first[0].equals("stoke"), "the second phase opens with its transition, not " + first[0]);
                boss.discard();
                pig.discard();
            });
        });

        // a posture break (Strikes.stagger) ends the move mid-swing: the opening is real
        AwakenTests.register("kit_break_interrupts", 20, h -> {
            ServerLevel level = h.getLevel();
            AwakenBoss boss = spawn(level, h, "ember_tyrant");
            h.assertTrue(boss.playKitMove("hammer"), "the hammer plays");
            h.assertTrue(boss.stagger(40), "it staggers");
            h.assertTrue(boss.kitPlaying() == null && boss.kitMove() < 0, "the hammer ends there");
            boss.discard();
            h.succeed();
        });

        // everyone left: the script resets to its intro pose with nothing playing
        AwakenTests.register("kit_reset_script", 20, h -> {
            ServerLevel level = h.getLevel();
            AwakenBoss boss = spawn(level, h, "unit_zero");
            h.assertTrue(boss.playKitMove("sever_a"), "a cut plays");
            boss.reset(level);
            h.assertTrue(boss.kitPlaying() == null, "nothing plays after a reset");
            h.assertTrue(boss.kitPose() == AwakenBoss.KIT_POSE_INTRO, "it waits in its intro pose (Unit Zero kneels)");
            boss.discard();
            h.succeed();
        });
    }

    /** Any active tick's capsules touch the box (the boss at the origin facing +z, as FightRunner places them at yaw 0). */
    private static boolean touches(KitMoves.Move m, AABB box) {
        for (var caps : m.hits()) {
            for (KitMoves.Capsule c : caps) {
                if (FightRunner.touches(new Vec3(c.ax(), c.ay(), c.az()), new Vec3(c.bx(), c.by(), c.bz()), c.r(), box)) return true;
            }
        }
        return false;
    }
}
