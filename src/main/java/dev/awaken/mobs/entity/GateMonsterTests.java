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
                for (String kind : new String[] {"ambient", "hurt", "death", "wind", "blow"}) {
                    h.assertTrue(MobSounds.monster(d.id, kind) != null, d.id + ": no " + kind + " voice");
                }
                // its loops have no seam (SPEC §60.9): every looping track carries a key before its start and one after its
                // end, so the game's curve has a slope to take where the loop comes round
                for (HeroRig.Clip c : rig.clips.values()) {
                    if (!c.loop()) continue;
                    for (HeroRig.Track t : c.tracks()) {
                        if (t.keys().size() < 5) continue;
                        float first = t.keys().get(0).t(), last = t.keys().get(t.keys().size() - 1).t();
                        h.assertTrue(first < -1.0E-4F && last > c.length() + 1.0E-4F, d.id + "/" + c.name() + "/" + t.bone() + ": a loop with a seam (keys " + first + " to " + last + ")");
                    }
                }
                // its fight is its own (SPEC §60.8): its blows throw its own matter; a body that strings its blows has
                // its other swing; and its rhythm is weighed so a string does per second what one plain swing did
                h.assertTrue(d.matter != null, d.id + ": its blows throw nothing of its own");
                if (d.blowChain > 1) {
                    h.assertTrue(rig.clips.containsKey("windup2") && rig.clips.containsKey("strike2"), d.id + ": it strings its blows and has no other swing");
                }
                int rank = switch (d.tier) {
                    case ELITE -> 9;
                    case NORMAL -> 8;
                    default -> 10;
                };
                int tell = d.blowTell > 0 ? d.blowTell : rank;
                float string = d.blowChain * tell + (d.blowChain - 1) * MobDef.STRING_GAP + 20;
                float perSecond = d.blowWeight(rank) * d.blowChain / string, plain = 1.0F / (rank + 20);
                h.assertTrue(d.blowTell == 0 || d.blowTell >= 8, d.id + ": a blow told for under 8 ticks");
                h.assertTrue(Math.abs(perSecond - plain) < 1.0E-4F, d.id + ": its rhythm does " + perSecond + " a tick, a plain swing " + plain);
                // what it throws is its own (SPEC §60.7): never an arrow, never the plain glow
                boolean shoots = false;
                for (MobDef.Phase p : d.phases) {
                    for (Ability a : p.abilities()) shoots |= a.kind() == Ability.Kind.BOLT || a.kind() == Ability.Kind.ARROWS || a.kind() == Ability.Kind.NOVA;
                }
                h.assertTrue(!shoots || (d.shot != null && d.muzzleUp > 0), d.id + ": it shoots and has no shot of its own");
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

        AwakenTests.register("gate_monster_looses_its_own_shots", 240, h -> {
            ServerLevel level = h.getLevel();
            GateMonster m = spawn(h, "crypt_archer", 1.5, 0.5);
            h.assertTrue(m.def().shot == dev.awaken.mobs.def.ShotLook.RIB, "the Mourner's shots are its ribs");
            Zombie z = dummy(h, 1.5, 6.5);
            face(m, 0.0F);
            boolean[] did = new boolean[1];
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
                for (AwBolt b : level.getEntitiesOfClass(AwBolt.class, m.getBoundingBox().inflate(24))) b.discard();
            });
            h.succeedWhen(() -> {
                if (!did[0]) did[0] = m.act(level, 0, z);
                h.assertTrue(did[0], "its volley begins");
                var shots = level.getEntitiesOfClass(AwBolt.class, m.getBoundingBox().inflate(24), b -> b.getOwner() == m);
                h.assertTrue(!shots.isEmpty(), "it looses shots");
                h.assertTrue(shots.get(0).look() == dev.awaken.mobs.def.ShotLook.RIB, "and they are ribs");
                h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, m.getBoundingBox().inflate(24)).isEmpty(),
                        "never an arrow");
            });
        });

        AwakenTests.register("gate_monster_spent_after_its_blow", 60, h -> {
            GateMonster m = spawn(h, "silver_knight", 1.5, 1.5);
            h.assertTrue(m.def().spent > 0 && !m.isOpen(), "the Empty Suit's own swing leaves it open");
            m.setNoAi(true);
            m.setTell(8, dev.awaken.core.api.Strikes.Kind.LIGHT, false);
            h.assertTrue(FaunaMob.cueStage(m.cue()) == FaunaMob.CUE_WINDUP, "its body winds its blow up");
            m.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(m.isOpen() && FaunaMob.cueStage(m.cue()) == RigBody.CUE_OPEN, "it swung: it stands open");
            m.discard();
            h.succeed();
        });

        AwakenTests.register("gate_monster_strings_its_blows", 200, h -> {
            GateMonster m = spawn(h, "cog_hound", 1.5, 1.5);
            MobDef d = m.def();
            h.assertTrue(d.blowChain == 2 && d.blowWeight(8) < 1.0F, "the Cog Hound snaps twice, each bite lighter than one plain one");
            Zombie z = dummy(h, 1.5, 2.6);
            face(m, 0.0F);
            m.setTarget(z);
            int first = GateMonster.meleeSlot(d, false), second = GateMonster.secondSlot(d);
            int[] at = {-1, -1}; // the tick its first bite was told, and its second
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
            });
            h.succeedWhen(() -> {
                m.setTarget(z);
                int cue = m.cue();
                if (FaunaMob.cueStage(cue) == FaunaMob.CUE_WINDUP) {
                    if (at[0] < 0 && FaunaMob.cueSlot(cue) == first) at[0] = m.tickCount;
                    if (at[0] >= 0 && at[1] < 0 && FaunaMob.cueSlot(cue) == second) at[1] = m.tickCount;
                }
                h.assertTrue(at[0] >= 0, "it tells its first bite");
                h.assertTrue(at[1] >= 0, "and, its target still there, its second: its other swing");
                int apart = at[1] - at[0];
                h.assertTrue(apart >= d.blowTell + MobDef.STRING_GAP && apart <= d.blowTell + MobDef.STRING_GAP + 3,
                        "the second is told a follow-through after the first lands (" + apart + " ticks after the first was)");
            });
        });

        // the real loop (its own goals, the selector): a plain blow is told for its whole tell and then lands
        AwakenTests.register("gate_monster_plain_blow_lands", 200, h -> {
            GateMonster m = spawn(h, "silver_knight", 1.5, 1.5);
            MobDef d = m.def();
            Zombie z = dummy(h, 1.5, 2.6);
            face(m, 0.0F);
            m.setTarget(z);
            float hp = z.getHealth();
            int[] told = {-1, 0}; // the tick its blow was first told, and the longest it has been told for so far
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
            });
            h.succeedWhen(() -> {
                m.setTarget(z);
                if (m.tellKind() != dev.awaken.core.api.Strikes.Kind.NONE) {
                    if (told[0] < 0) told[0] = m.tickCount;
                    told[1] = m.tickCount - told[0];
                }
                h.assertTrue(told[0] >= 0, "it tells its blow");
                h.assertTrue(z.getHealth() < hp, "the blow lands (told for " + told[1] + " ticks so far)");
                h.assertTrue(told[1] >= d.blowTell - 2, "after its whole tell of " + d.blowTell + " ticks, not cut short (" + told[1] + ")");
                h.assertTrue(m.isOpen(), "and its own swing leaves the Empty Suit open");
            });
        });

        // a step, then its cut: the body stands poised while the cut is told and strikes as that tell ends
        AwakenTests.register("gate_monster_poised_after_its_step", 240, h -> {
            ServerLevel level = h.getLevel();
            GateMonster m = spawn(h, "sand_stalker", 1.5, 0.5);
            Zombie z = dummy(h, 1.5, 5.5);
            face(m, 0.0F);
            float hp = z.getHealth();
            boolean[] did = new boolean[3]; // its step began, it stood poised, it struck
            AwakenTests.onEnd(h, () -> {
                m.discard();
                z.discard();
            });
            h.succeedWhen(() -> {
                h.assertTrue(m.abilities != null, "its abilities are ready (made on its first tick)");
                if (!did[0]) did[0] = m.act(level, 0, z);
                h.assertTrue(did[0], "its step begins");
                int stage = FaunaMob.cueStage(m.cue());
                if (stage == RigBody.CUE_POISED) did[1] = true;
                h.assertTrue(did[1], "it steps and stands poised, its cut told");
                if (stage == FaunaMob.CUE_STRIKE) did[2] = true;
                h.assertTrue(did[2], "the cut is thrown as its tell ends");
                h.assertTrue(z.getHealth() < hp, "and it lands");
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
            int[] began = {-1};
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
                    if (began[0] < 0) began[0] = m.tickCount;
                    // it squares up to its target for PLANT_TURN ticks first; only then is its facing held
                    h.assertTrue(m.tickCount - began[0] > GateMonster.PLANT_TURN + 1, "it has squared up and holds its facing");
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
