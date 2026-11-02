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
