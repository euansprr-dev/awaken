package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.api.Watch;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.boss.FightScripts;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A fight's mind (SPEC §48): it reads what you've started, after its reaction time, and answers it; one test per mechanism. */
public final class FightMindTests {
    private FightMindTests() {}

    static final String[] PILOTS = {"hive_regent", "gearwright_colossus"};
    private static final int ROOM = FightRecorder.ROOM;
    /** A potion's drink, and how far apart the drink test starts them (farther than the longest answer rests). */
    private static final int DRINK_TICKS = 32, DRINK_EVERY = 300, DRINKS = 28;
    private static final int SWIG = DRINKS * (DRINK_EVERY + 40);
    /** Each pilot's answers: tests register before data loads, so the count is pinned and checked in {@link #answer}. */
    private static final int ANSWERS = 6;

    public static void register() {
        AwakenTests.registerIsolated("fights_mind_reaction", 120, FightMindTests::reaction);
        AwakenTests.register("fights_mind_reaction_by_rank", 20, h -> {
            h.assertTrue(FightMind.reaction(Rank.E) == 12, "0.6 s at E");
            h.assertTrue(FightMind.reaction(Rank.SOVEREIGN) == 7, "0.35 s at the top");
            h.succeed();
        });
        AwakenTests.registerIsolated("fights_mind_caught", 200, FightMindTests::caught);
        for (String id : PILOTS) {
            for (int n = 0; n < ANSWERS; n++) {
                int at = n;
                AwakenTests.registerIsolated("fights_mind_answer_" + id + "_" + n, 240, h -> answer(h, id, at));
            }
            for (String how : new String[]{"guard", "dodged", "stayed"}) {
                AwakenTests.registerIsolated("fights_mind_fork_" + id + "_" + how, 120, h -> fork(h, id, how));
            }
            AwakenTests.registerIsolated("fights_mind_drinks_" + id, SWIG + 60, h -> drinks(h, id));
        }
    }

    private record Scene(ServerPlayer player, AwakenBoss boss, FightRunner run, FightMind mind, Kits.Kit kit, Vec3 home) {
        /** The player this far from the boss's edge, on the +z side of it. */
        Vec3 at(double edge) {
            return boss.position().add(0, 0, edge + boss.getBbWidth() * 0.5 + player.getBbWidth() * 0.5);
        }
    }

    private static Scene scene(GameTestHelper h, String id) {
        ServerLevel level = h.getLevel();
        FightRecorder.floor(h, ROOM * 2);
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(ROOM, 1, ROOM + 10));
        MobDef def = Bestiary.get(id);
        Vec3 home = h.absoluteVec(new Vec3(ROOM + 0.5, 1, ROOM + 0.5));
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, home, 10, new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        boss.setTarget(p);
        FightRunner run = boss.fight();
        h.assertTrue(run != null && run.mind() != null, id + " has a mind");
        return new Scene(p, boss, run, run.mind(), boss.kit(), home);
    }

    private static int bit(FightScripts.Fact f) {
        return switch (f) {
            case DRINK -> Watch.DRINK;
            case GUARD -> Watch.GUARD;
            case HEAVY -> Watch.HEAVY;
            case RECOVERY -> Watch.RECOVERY;
            case DODGE -> Watch.DODGE;
            case CAST -> Watch.CAST;
            default -> 0;
        };
    }

    /** A real drink is seen only after the reaction time; a new target starts the read again. */
    private static void reaction(GameTestHelper h) {
        Scene s = scene(h, "hive_regent");
        ServerLevel level = h.getLevel();
        ServerPlayer p = s.player, q = FairTests.reference(h, Rank.E, new BlockPos(ROOM + 4, 1, ROOM + 10));
        for (ServerPlayer who : List.of(p, q)) {
            who.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POTION));
            who.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue((Watch.facts(who) & Watch.DRINK) != 0, "a potion being drunk is there to see");
        }
        s.run.idleFor(level, 100);
        s.mind.sure = true;
        long started = level.getGameTime();
        long[] switched = {-1};
        boolean[] saw = {false, false};
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            long now = level.getGameTime();
            s.boss.setTarget(switched[0] >= 0 ? q : p);
            boolean sees = s.mind.seen(FightScripts.Fact.DRINK, 0, now) >= 0;
            if (switched[0] < 0) {
                if (sees) {
                    h.assertTrue(now - started >= s.mind.react, "seen after " + (now - started) + " ticks, sooner than its " + s.mind.react);
                    saw[0] = true;
                    switched[0] = now + 1; // from next tick its target is someone else, who has been drinking all along
                }
            } else if (now > switched[0] && sees) {
                h.assertTrue(now - switched[0] >= s.mind.react - 1, "a new target read after " + (now - switched[0]) + " ticks");
                saw[1] = true;
                s.boss.discard();
                h.succeed();
            }
            h.assertTrue(tick[0] < 100, "never saw the drink: " + saw[0] + ", then " + saw[1]);
        });
    }

    /** Each answer of each pilot: it shows it noticed, then the blow is one of the answer's, with its tell and class. */
    private static void answer(GameTestHelper h, String id, int n) {
        Scene s = scene(h, id);
        ServerLevel level = h.getLevel();
        List<FightScripts.Answer> all = s.kit.fight().mind().orElseThrow().answers();
        h.assertTrue(all.size() == ANSWERS, id + " has " + all.size() + " answers, and the tests are registered for " + ANSWERS);
        FightScripts.Answer a = all.get(n);
        boolean paused = a.on() == FightScripts.Fact.PAUSED;
        KitMoves.Move first = s.kit.moves().get(s.kit.moves().index(a.moves().get(0)));
        int wait = paused ? 76 : Math.max(s.mind.react, a.after()) + 2;
        double edge = paused ? 3 : Mth.clamp((first.minRange() + first.maxRange()) * 0.5, first.minRange() + 0.3, first.maxRange() - 0.3);
        Vec3[] stand = {s.at(edge)};
        s.player.setPos(stand[0]);
        s.run.idleFor(level, wait);
        s.mind.sure = true;
        int[] tick = {0}, noticed = {-1};
        h.onEachTick(() -> {
            tick[0]++;
            s.boss.setTarget(s.player);
            s.player.setHealth(s.player.getMaxHealth());
            s.boss.setPos(s.home); // this is the answer's test, not the flight's: a flier mustn't drift out of the answer's reach
            if (paused) {
                if (tick[0] <= 45) stand[0] = stand[0].add(0, 0, 0.19); // running away; then it stops
                else Watch.stage(s.player, 0);
            } else Watch.stage(s.player, bit(a.on()));
            s.player.setPos(stand[0]);
            if (noticed[0] < 0 && s.boss.kitPose() == AwakenBoss.KIT_POSE_NOTICE) noticed[0] = tick[0];
            String now = s.boss.kitPlaying();
            if (now == null) {
                h.assertTrue(tick[0] < 200, id + ": no answer to " + a.on() + " (noticed at " + noticed[0] + ")");
                return;
            }
            h.assertTrue(noticed[0] >= 0, id + " began " + now + " without showing it noticed");
            h.assertTrue(a.moves().contains(now), id + " answered " + a.on() + " with " + now + ", not one of " + a.moves());
            KitMoves.Move m = s.kit.moves().get(s.kit.moves().index(now));
            if (m.kind() != Strikes.Kind.NONE) {
                Strikes.Tell t = Strikes.tell(s.boss);
                h.assertTrue(t != null && t.kind() == m.kind(), id + ": " + now + " keeps its tell (" + t + ")");
            }
            Watch.stage(s.player, 0);
            s.boss.discard();
            h.succeed();
        });
    }

    /** At a string's fork the next blow is chosen by what you're doing now. */
    private static void fork(GameTestHelper h, String id, String how) {
        Scene s = scene(h, id);
        ServerLevel level = h.getLevel();
        FightScripts.Entry e = s.kit.fight().phases().get(0).pool().stream().filter(x -> !x.forks().isEmpty()).findFirst().orElseThrow();
        FightScripts.Fork f = e.forks().get(0);
        String expect = switch (how) {
            case "guard" -> f.guard().orElseThrow();
            case "dodged" -> f.dodged().orElseThrow();
            default -> f.stayed().orElseThrow();
        };
        KitMoves.Move opener = s.kit.moves().get(s.kit.moves().index(e.move()));
        Vec3 near = s.at(Math.min(1.5, opener.maxRange() - 0.3)), out = s.at(6.5);
        s.player.setPos(near);
        s.mind.sure = true;
        h.assertTrue(s.run.fight(level, e.move(), e.then().get(0), e.forks()), "the string began");
        int[] tick = {0};
        boolean[] second = {false};
        h.onEachTick(() -> {
            tick[0]++;
            s.boss.setTarget(s.player);
            s.player.setHealth(s.player.getMaxHealth());
            boolean dodged = how.equals("dodged");
            if (dodged) {
                Watch.stage(s.player, tick[0] <= 2 ? Watch.DODGE : 0);
                s.player.setPos(out);
            } else {
                Watch.stage(s.player, how.equals("guard") ? Watch.GUARD : 0);
                s.player.setPos(near);
            }
            String now = s.boss.kitPlaying();
            if (now != null && !now.equals(e.move())) second[0] = true;
            if (second[0]) {
                h.assertTrue(now != null && now.equals(expect), id + " (" + how + ") went on with " + now + ", not " + expect);
                Watch.stage(s.player, 0);
                s.boss.discard();
                h.succeed();
            }
            h.assertTrue(tick[0] < 100, id + " (" + how + ") never went on");
        });
    }

    /** The third dodge in a row is caught: the blow is held for a beat and turns to where you land; but only once it has seen it. */
    private static void caught(GameTestHelper h) {
        Scene s = scene(h, "hive_regent");
        ServerLevel level = h.getLevel();
        Vec3 near = s.at(1.5);
        s.player.setPos(near);
        s.mind.sure = true;
        s.run.idleFor(level, 150);
        int third = 21, seen = third + s.mind.react + 2;
        int[] tick = {0};
        h.onEachTick(() -> {
            int t = ++tick[0];
            s.boss.setTarget(s.player);
            s.player.setHealth(s.player.getMaxHealth());
            Watch.stage(s.player, t == 1 || t == 11 || t == third ? Watch.DODGE : 0);
            if (t == third + 1) {
                h.assertTrue(s.run.fight(level, "cut", List.of(), List.of()), "cut began");
                h.assertTrue(s.boss.kitHold() == 0, "it hasn't seen the third dodge yet, and doesn't wait for it");
            }
            if (t == seen) {
                h.assertTrue(s.run.fight(level, "cut", List.of(), List.of()), "cut began");
                h.assertTrue(s.boss.kitHold() >= FightMind.CAUGHT_HOLD, "the third dodge in a row is waited out (held " + s.boss.kitHold() + ")");
            }
            // past where the cut would have locked, you land off to its side: it still turns to you
            KitMoves.Move cut = s.kit.moves().get(s.kit.moves().index("cut"));
            if (t >= seen + cut.time().lock() + 2) s.player.setPos(s.boss.position().add(2.6, 0, 0));
            if (t == seen + cut.time().windup() + s.boss.kitHold()) {
                float want = (float) (Mth.atan2(s.player.getZ() - s.boss.getZ(), s.player.getX() - s.boss.getX()) * Mth.RAD_TO_DEG) - 90;
                float off = Math.abs(Mth.wrapDegrees(want - s.boss.yBodyRot));
                h.assertTrue(off < 30, "the held blow faces where you landed (off by " + off + "°)");
                s.boss.discard();
                h.succeed();
            }
        });
    }

    /** A real fight with someone in reach, near and further off in turn, drinking now and then: most drinks are answered. */
    private static void drinks(GameTestHelper h, String id) {
        Scene s = scene(h, id);
        double[] edges = {2.0, 7.0};
        Vec3[] stand = {s.at(edges[0])};
        s.player.setPos(stand[0]);
        s.boss.getRandom().setSeed(id.hashCode() * 17L);
        net.minecraft.util.RandomSource dice = net.minecraft.util.RandomSource.create(id.hashCode());
        int[] tick = {0}, next = {60}, drinkEnd = {-1}, drinks = {0}, noticeWas = {0};
        int[][] answered = {{0, 0}, {0, 0}}; // answered and drunk, near and further off
        boolean[] done = {false};
        h.onEachTick(() -> {
            int t = ++tick[0];
            s.boss.setTarget(s.player);
            s.player.setHealth(s.player.getMaxHealth());
            s.player.setPos(stand[0]);
            int where = drinks[0] & 1;
            // from the room's middle: a boss drops a target that leaves its arena
            if (t == next[0] - 40) stand[0] = s.home.add(0, 0, edges[where] + s.boss.getBbWidth() * 0.5 + s.player.getBbWidth() * 0.5);
            if (t == next[0] && drinks[0] < DRINKS) {
                drinkEnd[0] = t + DRINK_TICKS;
                answered[where][1]++;
                drinks[0]++;
                done[0] = false;
                next[0] = t + DRINK_EVERY + dice.nextInt(40);
                Watch.stage(s.player, Watch.DRINK);
            }
            if (t == drinkEnd[0]) Watch.stage(s.player, 0);
            boolean noticing = s.boss.kitPose() == AwakenBoss.KIT_POSE_NOTICE;
            if (noticing && noticeWas[0] == 0 && t <= drinkEnd[0] && !done[0]) {
                done[0] = true;
                answered[(drinks[0] - 1) & 1][0]++;
            }
            noticeWas[0] = noticing ? 1 : 0;
        });
        h.runAfterDelay(SWIG, () -> {
            int yes = answered[0][0] + answered[1][0];
            Awaken.LOG.info("[mind] {}: {} of {} drinks answered ({} of {} near, {} of {} further off)", id, yes, drinks[0], answered[0][0], answered[0][1], answered[1][0], answered[1][1]);
            h.assertTrue(yes * 100 >= drinks[0] * 60, id + " answered " + yes + " of " + drinks[0] + " drinks");
            Watch.stage(s.player, 0);
            s.boss.discard();
            h.succeed();
        });
    }
}
