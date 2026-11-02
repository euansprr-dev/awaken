package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The duel (SPEC §46.3): every phrase ends in an opening; a deflected blow throws the limb back and the phrase goes on,
 * its last blow turned aside makes it reel; a white-gold shot deflected flies back at its maker.
 */
public final class DuelTests {
    private DuelTests() {}

    public static void register() {
        AwakenTests.register("fights_phrase_opening", 200, DuelTests::phraseOpening);
        AwakenTests.register("fights_deflect_recoil", 240, DuelTests::deflectRecoil);
        AwakenTests.register("fights_shot_returns", 100, DuelTests::shotReturns);
        AwakenTests.register("fights_duel_stance", 60, DuelTests::duelStance);
        AwakenTests.register("fights_topple", 100, DuelTests::topple);
        AwakenTests.register("fights_fang_bite", 60, DuelTests::fangBite);
    }

    /** The Gearwright's sweep and chop, then nothing for at least the shortest opening, and it stands open through it. */
    static void phraseOpening(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AwakenBoss boss = KitTests.spawn(level, h, "gearwright_colossus");
        Pig pig = pig(h, 6.5);
        boss.setTarget(pig);
        h.assertTrue(boss.duel(), "the Gearwright fights as a duel");
        h.assertTrue(boss.playKitString("sweep", List.of("chop")), "the phrase plays");
        int[] ended = {-1}, next = {-1}, shut = {-1};
        boolean[] chopped = {false};
        h.onEachTick(() -> {
            boss.setTarget(pig);
            int now = (int) level.getGameTime();
            String playing = boss.kitPlaying();
            if ("chop".equals(playing)) chopped[0] = true;
            if (ended[0] < 0) {
                if (playing == null) ended[0] = now;
                return;
            }
            if (next[0] < 0 && playing != null) next[0] = now;
            if (next[0] < 0 && shut[0] < 0 && !Strikes.opening(boss)) shut[0] = now;
        });
        h.succeedWhen(() -> {
            h.assertTrue(next[0] >= 0, "it starts again after its opening");
            h.assertTrue(chopped[0], "the chop linked on");
            int open = next[0] - ended[0];
            h.assertTrue(open >= FightRunner.OPENING_MIN, "nothing starts for " + open + " ticks after the phrase, under " + FightRunner.OPENING_MIN);
            h.assertTrue(shut[0] < 0 || shut[0] - ended[0] >= FightRunner.OPENING_MIN, "it stands open through it (shut after " + (shut[0] - ended[0]) + ")");
            boss.discard();
            pig.discard();
        });
    }

    /** The sweep deflected: a recoil, then the chop goes on; the chop deflected: it reels, open, and starts nothing. */
    static void deflectRecoil(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AwakenBoss boss = KitTests.spawn(level, h, "gearwright_colossus");
        Pig pig = pig(h, 6.5);
        boss.setTarget(pig);
        h.assertTrue(boss.playKitString("sweep", List.of("chop")), "the phrase plays");
        int start = (int) level.getGameTime();
        // 0 sweep playing, 1 recoiling, 2 chop playing, 3 reeling, 4 started again
        int[] state = {0}, at = {start, 0, 0, 0, 0};
        boolean[] open = {true}, recoilPose = {false};
        h.onEachTick(() -> {
            boss.setTarget(pig);
            int now = (int) level.getGameTime();
            String playing = boss.kitPlaying();
            switch (state[0]) {
                case 0 -> {
                    if ("sweep".equals(playing) && now - start >= 4) {
                        boss.deflected(Strikes.Kind.LIGHT);
                        h.assertTrue(boss.kitPlaying() == null, "a deflect ends the sweep");
                        state[0] = 1;
                        at[1] = now;
                    }
                }
                case 1 -> {
                    if (boss.kitPose() == AwakenBoss.KIT_POSE_RECOIL) recoilPose[0] = true;
                    if (playing != null) {
                        h.assertTrue("chop".equals(playing), "the phrase goes on with the chop, not " + playing);
                        state[0] = 2;
                        at[2] = now;
                    }
                }
                case 2 -> {
                    boss.deflected(Strikes.Kind.LIGHT);
                    h.assertTrue(boss.reeling() && boss.inOpening(), "its last blow turned aside, it reels open");
                    state[0] = 3;
                    at[3] = now;
                }
                case 3 -> {
                    if (playing != null) {
                        state[0] = 4;
                        at[4] = now;
                    } else if (!boss.inOpening()) open[0] = false;
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(state[0] == 4, "it fought on after the reel (stage " + state[0] + ")");
            int recoil = at[2] - at[1], reel = at[4] - at[3];
            h.assertTrue(recoil >= FightRunner.RECOIL_TICKS && recoil <= FightRunner.RECOIL_TICKS + 3, "the recoil held " + recoil + " ticks");
            h.assertTrue(recoilPose[0], "and showed as the recoil pose");
            h.assertTrue(reel >= FightRunner.REEL_TICKS + FightRunner.OPENING_MIN / 2, "the reel and its opening held only " + reel + " ticks");
            h.assertTrue(open[0], "it stood open until it started again");
            boss.discard();
            pig.discard();
        });
    }

    /** A white-gold shot deflected flies back and strikes its maker, never the one who sent it; an amber one won't turn. */
    static void shotReturns(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 10; x++) for (int z = -2; z <= 4; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        MobDef def = Bestiary.get("toll_ghoul");
        AwakenMob maker = Mobs.spawn(level, def, h.absoluteVec(new Vec3(0.5, 1, 1.5)), 5,
                new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
        h.assertTrue(maker != null, "a maker spawned");
        maker.setNoAi(true);
        var live = AwakenTests.survivalPlayer(h, "shot_sender", new BlockPos(7, 1, 1));
        ServerPlayer p = live.player();
        Vec3 from = maker.getEyePosition(), to = p.position().add(0, 1.2, 0);
        AwBolt[] shot = new AwBolt[2];
        Strikes.deliver(maker, Strikes.Kind.LIGHT, () -> (shot[0] = AwBolt.shoot(level, maker, from, to.subtract(from), BoltKind.PLAIN, 4F, null, 0.6)) != null);
        Strikes.deliver(maker, Strikes.Kind.HEAVY, () -> (shot[1] = AwBolt.shoot(level, maker, from, to.subtract(from), BoltKind.PLAIN, 4F, null, 0.6)) != null);
        h.assertTrue(!shot[1].sendBack(p), "an amber shot breaks on the blade: it never turns");
        shot[1].discard();
        h.assertTrue(shot[0].sendBack(p), "a white-gold shot turns");
        h.assertTrue(!shot[0].sendBack(p), "once");
        float makerWas = maker.getHealth(), playerWas = p.getHealth();
        h.onEachTick(() -> p.setPos(h.absoluteVec(new Vec3(7.5, 1, 1.5))));
        h.succeedWhen(() -> {
            h.assertTrue(maker.getHealth() < makerWas, "the maker is struck by its own shot");
            h.assertTrue(p.getHealth() == playerWas, "the one who sent it back is not");
            maker.discard();
            live.leave();
        });
    }

    /**
     * A duel boss holds its own stance (the fight script's, tuned with the sim), and braced it takes half of a blow's
     * stance: an opening takes more, a riposte all of it (SPEC §46.3).
     */
    static void duelStance(GameTestHelper h) {
        AwakenBoss boss = KitTests.spawn(h.getLevel(), h, "gearwright_colossus");
        int stance = boss.duelStance();
        h.assertTrue(stance > dev.awaken.arms.ArmsRules.POSTURE_LORD && Strikes.duelist(boss) && Strikes.stance(boss) == stance,
                "a duel boss holds its own stance (" + stance + ")");
        var live = AwakenTests.survivalPlayer(h, "stance_breaker", new BlockPos(1, 1, 1));
        Strikes.posture(boss, 26, live.player());
        float share = dev.awaken.arms.Arms.posture(boss);
        h.assertTrue(Math.abs(share - 26F / stance) < 1e-4, "26 of " + stance + " filled, read " + share);
        h.assertTrue(dev.awaken.arms.ArmsRules.hitPosture(8, false, false, true) == 4, "braced: half");
        h.assertTrue(dev.awaken.arms.ArmsRules.hitPosture(8, true, false, true) == 12, "in an opening: half again");
        h.assertTrue(dev.awaken.arms.ArmsRules.hitPosture(8, false, true, true) == 8, "a riposte: all of it");
        h.assertTrue(dev.awaken.arms.ArmsRules.hitPosture(8, false, false, false) == 8, "not a duel: all of it");
        boss.discard();
        live.leave();
        h.succeed();
    }

    /** A big beast's broken stance puts it on its side for the break (SPEC §46.5); a short stagger and a plain animal don't. */
    static void topple(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MobDef d = Bestiary.get("briar_sow");
        AwakenMob beast = Mobs.spawn(level, d, h.absoluteVec(new Vec3(2.5, 1, 2.5)), 3, new MobRole(d.tier.role, 3, d.theme.id, Optional.empty(), ""), Set.of());
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
        pig.setPos(h.absoluteVec(new Vec3(7.5, 1, 7.5)));
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); // the sow is territorial: it outlives her
        pig.setHealth(1000);
        level.addFreshEntity(pig);
        int broken = dev.awaken.arms.ArmsRules.BROKEN_TICKS;
        boolean[] done = {false};
        h.onEachTick(() -> {
            // once both stand (each blow below acts once: a failed check names itself)
            if (done[0] || !beast.onGround() || !pig.onGround()) return;
            done[0] = true;
            h.assertTrue(Topple.big(beast), "a named beast is big: " + dev.awaken.core.api.CreatureTag.of(beast));
            Strikes.stagger(beast, 20);
            h.assertFalse(Topple.down(beast), "a short stagger (armour's) doesn't put it down");
            Strikes.stagger(beast, broken);
            h.assertTrue(Topple.down(beast) && beast.staggered(), "a broken stance puts it on its side, held still");
            Topple.Down down = beast.getData(Topple.DOWN);
            long now = level.getGameTime();
            h.assertTrue(Topple.over(down, now + Topple.FALL + Topple.BOUNCE) == 1.0F && Topple.over(down, now + broken - 1) < 0.3F
                    && Topple.over(down, now + broken) == 0.0F, "over, then up as the break ends");
            Strikes.stagger(beast, broken + 20);
            h.assertTrue(beast.getData(Topple.DOWN).from() == down.from(), "a break held longer doesn't fall again");
            Strikes.stagger(pig, broken);
            h.assertFalse(Topple.down(pig), "a plain pig doesn't topple: " + dev.awaken.core.api.CreatureTag.of(pig));
            pig.setData(dev.awaken.core.api.CoreApi.CREATURE_TAG, new dev.awaken.core.api.CreatureTag(3, dev.awaken.core.api.CreatureTag.Kind.PASSIVE, true, "Alpha"));
            Strikes.stagger(pig, broken);
            h.assertTrue(Topple.down(pig), "a herd's alpha (a quarry) does: " + dev.awaken.core.api.CreatureTag.of(pig) + ", big " + Topple.big(pig)
                    + ", on the ground " + pig.onGround() + ", " + pig.getData(Topple.DOWN));
            beast.discard();
            pig.discard();
            h.succeed();
        });
    }

    /** A duel's fang bites for what it was marked with (its boss's own blow); any other fang for vanilla's 6. */
    static void fangBite(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Pig a = pig(h, 2.5), b = pig(h, 6.5), owner = pig(h, 10.5);
        boolean[] raised = {false};
        h.onEachTick(() -> {
            if (!raised[0]) {
                raised[0] = true; // still pigs (no AI) never settle: the fangs rise where they hang
                EvokerFangs marked = new EvokerFangs(level, a.getX(), a.getY(), a.getZ(), 0F, 0, owner);
                DuelFangs.mark(marked, 2.5F);
                level.addFreshEntity(marked);
                level.addFreshEntity(new EvokerFangs(level, b.getX(), b.getY(), b.getZ(), 0F, 0, owner));
                return;
            }
            if (a.getHealth() < 1000 && b.getHealth() < 1000) {
                h.assertTrue(Math.abs(1000 - a.getHealth() - 2.5F) < 0.01F, "a duel's fang bites for its mark: " + (1000 - a.getHealth()));
                h.assertTrue(Math.abs(1000 - b.getHealth() - 6F) < 0.01F, "any other fang for vanilla's 6: " + (1000 - b.getHealth()));
                h.succeed();
            }
        });
    }

    private static Pig pig(GameTestHelper h, double z) {
        ServerLevel level = h.getLevel();
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
        pig.setPos(h.absoluteVec(new Vec3(4.5, 1, z)));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        level.addFreshEntity(pig);
        return pig;
    }
}
