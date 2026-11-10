package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * The gates' monsters on bodies of their own (SPEC §60): every one has its body and the body acts every move the
 * bestiary gives it; and one test for each of their rules (a front that holds, an opening, stopped by a blow, a miss
 * that costs it, planted to shoot).
 */
public final class GateMonsterTests {
    private GateMonsterTests() {}

    /** How many gate monsters wear a body of their own (the Mirror Shade keeps the player's face). */
    static final int BODIES = 78;

    public static void register() {
        AwakenTests.register("gate_monsters_own_bodies", 20, h -> {
            int n = 0;
            for (MobDef d : Bestiary.all()) {
                if (!d.rig) continue;
                n++;
                HeroRig rig = HeroRig.get(d.id);
                h.assertTrue(rig != null, d.id + ": a gate monster with no body");
                for (String c : new String[] {"idle", "hurt", "death", "windup", "strike", d.flies() ? "fly" : "walk"}) {
                    h.assertTrue(rig.clips.containsKey(c), d.id + ": its body has no " + c + " clip");
                }
                for (MobDef.Phase p : d.phases) {
                    for (Ability a : p.abilities()) {
                        String mv = rig.move(a.name());
                        h.assertTrue(mv != null, d.id + ": its body has no move for " + a.name());
                        h.assertTrue(rig.clips.containsKey(mv + "_windup") && rig.clips.containsKey(mv + "_strike"), d.id + ": " + a.name() + " has no wind-up or no strike");
                        boolean lunges = a.kind() == Ability.Kind.CHARGE || a.kind() == Ability.Kind.LEAP;
                        if ((lunges && d.miss > 0) || (d.open > 0 && GateMonster.opens(a))) {
                            h.assertTrue(rig.clips.containsKey(mv + "_open"), d.id + ": " + a.name() + " leaves it open and its body has no pose to show it");
                        }
                    }
                }
                for (String kind : new String[] {"ambient", "hurt", "death"}) {
                    h.assertTrue(MobSounds.monster(d.id, kind) != null, d.id + ": no " + kind + " voice");
                }
            }
            h.assertTrue(n == BODIES, BODIES + " gate monsters on bodies of their own, found " + n);
            h.assertTrue(!Bestiary.get("mirror_shade").rig, "the Mirror Shade keeps the player's face");
            GateMonster one = spawn(h, "toll_ghoul", 1.5, 1.5);
            h.assertTrue(one != null, "a monster with a body of its own is a GateMonster");
            one.discard();
            h.succeed();
        });

        AwakenTests.register("gate_monster_front_holds", 60, h -> {
            ServerLevel level = h.getLevel();
            GateMonster m = spawn(h, "crypt_keeper", 1.5, 1.5);
            h.assertTrue(m.def().front && !m.flag(AwakenMob.F_DORMANT), "the Pallbearer's front holds");
            m.setNoAi(true);
            face(m, 0.0F); // toward +z
            Zombie front = dummy(h, 1.5, 3.4), behind = dummy(h, 1.5, -0.4);
            float hp = m.getHealth();
            h.assertTrue(!m.hurtServer(level, level.damageSources().mobAttack(front), 4.0F), "a blow from in front does nothing");
            h.assertTrue(m.getHealth() == hp, "and costs it nothing");
            h.assertTrue(m.hurtServer(level, level.damageSources().mobAttack(behind), 4.0F) && m.getHealth() < hp, "a blow from behind lands");
            m.discard();
            front.discard();
            behind.discard();
            h.succeed();
        });

        AwakenTests.register("gate_monster_open_bites_deeper", 60, h -> {
            ServerLevel level = h.getLevel();
            GateMonster shut = spawn(h, "toll_ghoul", 0.5, 1.5), open = spawn(h, "toll_ghoul", 2.5, 1.5);
            Zombie z = dummy(h, 1.5, 3.4);
            shut.setNoAi(true);
            open.setNoAi(true);
            open.open(40);
            h.assertTrue(open.isOpen() && !shut.isOpen(), "one stands open");
            float a = shut.getHealth(), b = open.getHealth();
            h.assertTrue(shut.hurtServer(level, level.damageSources().mobAttack(z), 4.0F) && open.hurtServer(level, level.damageSources().mobAttack(z), 4.0F), "both are hit");
            float lostShut = a - shut.getHealth(), lostOpen = b - open.getHealth();
            h.assertTrue(lostShut > 0 && Math.abs(lostOpen / lostShut - GateMonster.OPEN_BITE) < 0.15F,
                    "the same blow bites half again as deep into the open one (" + lostShut + " against " + lostOpen + ")");
            shut.discard();
            open.discard();
            z.discard();
            h.succeed();
        });

        AwakenTests.register("gate_monster_stopped_by_a_blow", 200, h -> {
            ServerLevel level = h.getLevel();
            GateMonster m = spawn(h, "toll_ghoul", 0.5, 1.5);
            h.assertTrue(m.def().stopped, "the Toller can be stopped");
            Zombie z = dummy(h, 3.0, 1.5);
            m.setTarget(z);
            String move = m.def().phases.get(0).abilities().get(0).name();
            boolean[] did = new boolean[2]; // started, struck
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
            });
            h.succeedWhen(() -> {
                h.assertTrue(m.abilities != null, "its abilities are ready (made on its first tick)");
                if (!did[0]) did[0] = m.abilities.start(level, move);
                h.assertTrue(did[0], "its move begins");
                if (!did[1]) {
                    h.assertTrue(m.abilities.active() != null && FaunaMob.cueStage(m.cue()) == FaunaMob.CUE_WINDUP, "its body winds the move up");
                    did[1] = true;
                    h.assertTrue(m.hurtServer(level, level.damageSources().mobAttack(z), 2.0F), "the blow lands");
                }
                h.assertTrue(m.staggered() && m.abilities.active() == null, "the blow stopped it: the move is gone and it staggers");
                h.assertTrue(FaunaMob.cueStage(m.cue()) == FaunaMob.CUE_NONE, "and its body let the pose go");
            });
        });

        AwakenTests.register("gate_monster_miss_leaves_it_stuck", 240, h -> {
            ServerLevel level = h.getLevel();
            GateMonster m = spawn(h, "thorn_hound", 0.5, 1.5);
            h.assertTrue(m.def().miss > 0, "a miss costs the Bramble Hound");
            Zombie z = dummy(h, 3.5, 1.5);
            m.setTarget(z);
            String move = m.def().phases.get(0).abilities().get(0).name();
            boolean[] did = new boolean[3]; // started, wound up, target gone
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
            });
            h.succeedWhen(() -> {
                h.assertTrue(m.abilities != null, "its abilities are ready (made on its first tick)");
                if (!did[0]) did[0] = m.abilities.start(level, move);
                h.assertTrue(did[0], "its pounce begins");
                if (!did[1]) {
                    h.assertTrue(m.abilities.active() != null, "it winds the pounce up");
                    did[1] = true;
                }
                if (!did[2]) {
                    h.assertTrue(m.abilities.active() == null, "the pounce is let go");
                    did[2] = true;
                    z.snapTo(z.getX(), z.getY() + 40, z.getZ()); // nobody is where it lands
                }
                h.assertTrue(FaunaMob.cueStage(m.cue()) == RigBody.CUE_STUCK && m.isOpen(), "it hit nobody: it is stuck where it landed, and open");
            });
        });

        AwakenTests.register("gate_monster_planted_loses_its_aim", 200, h -> {
            ServerLevel level = h.getLevel();
            GateMonster m = spawn(h, "crypt_archer", 1.5, 1.5);
            h.assertTrue(m.def().planted, "the Mourner plants to shoot");
            Zombie z = dummy(h, 1.5, 4.5);
            face(m, 0.0F);
            m.setTarget(z);
            String move = m.def().phases.get(0).abilities().get(0).name();
            boolean[] did = new boolean[2]; // started, target moved behind it
            float[] yaw = new float[1];
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
            });
            h.succeedWhen(() -> {
                h.assertTrue(m.abilities != null, "its abilities are ready (made on its first tick)");
                if (!did[0]) did[0] = m.abilities.start(level, move);
                h.assertTrue(did[0], "its volley begins");
                if (!did[1]) {
                    h.assertTrue(m.abilities.active() != null && FaunaMob.cueStage(m.cue()) == FaunaMob.CUE_WINDUP, "it plants and winds up");
                    did[1] = true;
                    yaw[0] = m.yBodyRot;
                    Vec3 behind = m.position().add(-Mth.sin(-yaw[0] * Mth.DEG_TO_RAD) * 3.0, 0, -Mth.cos(yaw[0] * Mth.DEG_TO_RAD) * 3.0);
                    z.snapTo(behind.x, z.getY(), behind.z);
                }
                h.assertTrue(m.staggered() && m.abilities.active() == null, "its target got behind it: it loses the shot and staggers");
            });
        });
    }

    private static GateMonster spawn(GameTestHelper h, String id, double x, double z) {
        MobDef d = Bestiary.get(id);
        AwakenMob m = Mobs.spawn(h.getLevel(), d, h.absoluteVec(new Vec3(x, 1, z)), 10, new MobRole(d.tier.role, 10, d.theme.id, Optional.empty(), ""), Set.of());
        h.assertTrue(m instanceof GateMonster, id + " stands on a body of its own");
        return (GateMonster) m;
    }

    private static Zombie dummy(GameTestHelper h, double x, double z) {
        Zombie zb = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        Vec3 p = h.absoluteVec(new Vec3(x, 1, z));
        zb.snapTo(p.x, p.y, p.z);
        zb.setNoAi(true);
        h.getLevel().addFreshEntity(zb);
        return zb;
    }

    private static void face(AwakenMob m, float yaw) {
        m.setYRot(yaw);
        m.yBodyRot = yaw;
        m.yBodyRotO = yaw;
        m.yHeadRot = yaw;
    }
}
