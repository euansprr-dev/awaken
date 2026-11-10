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
    /**
     * A potion's drink, how far apart the drink test starts them (farther than the longest answer rests), and how many:
     * enough that one run's fight doesn't decide the share.
     */
    private static final int DRINK_TICKS = 32, DRINK_EVERY = 300, DRINKS = 448;
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
        // SPEC §48.6: every boss has a mind, each answer and fork its own moves, none of them an empty hand
        AwakenTests.register("fights_mind_every_boss", 20, h -> {
            for (var kit : dev.awaken.mobs.boss.Kits.all().values()) {
                var fight = kit.fight();
                if (fight == null) continue;
                h.assertTrue(fight.mind().isPresent(), fight.id() + " has no mind");
                h.assertTrue(fight.mind().get().answers().size() >= 3, fight.id() + " has only " + fight.mind().get().answers().size() + " answers");
            }
            h.succeed();
        });
        AwakenTests.registerIsolated("fights_mind_caught", 200, FightMindTests::caught);
        AwakenTests.registerIsolated("fights_mind_repeats", 40, FightMindTests::repeats);
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
        double edge = paused ? 3 : standFor(h, s, first);
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

    private static final double STAND_STEP = 0.1;

    /**
     * How far from the boss's edge to stand for this blow: the middle of its range or, as a lunge's run lengths
     * ({@code BlowAim.RUNS}) leave thin bands it can't land from, the nearest place to that where it can.
     */
    private static double standFor(GameTestHelper h, Scene s, KitMoves.Move m) {
        double low = m.minRange() + 0.3, high = m.maxRange() - 0.3, mid = Mth.clamp((m.minRange() + m.maxRange()) * 0.5, low, high);
        for (double off = 0; off <= high - low; off += STAND_STEP) {
            for (double edge : new double[]{mid + off, mid - off}) {
                if (edge >= low && edge <= high && landsAround(s, m, edge)) return edge;
            }
        }
        h.assertTrue(false, m.id() + " can't land from anywhere in its range");
        return mid;
    }

    /** It lands from here and a step either way: a spot that only just lands is a coin toss on the tick it's asked. */
    private static boolean landsAround(Scene s, KitMoves.Move m, double edge) {
        for (double e : new double[]{edge - STAND_STEP, edge, edge + STAND_STEP}) {
            s.player.setPos(s.at(e));
            if (!s.run.couldLand(m, s.player)) return false;
        }
        return true;
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

    /** It never picks what it just made, except a blow as the last resort; a move that hurts nobody keeps its rest whatever happens. */
    private static void repeats(GameTestHelper h) {
        FightMind m = scene(h, "hive_regent").mind;
        KitMoves.Table t = Kits.all().get("hive_regent").moves();
        int rain = t.index("rain"), frenzy = t.index("frenzy"), brood = t.index("brood"), sting = t.index("sting"), cut = t.index("cut");
        long now = 1000;
        m.began(frenzy, now);
        m.nothingToStart(now);
        h.assertTrue(!m.allowed(frenzy, now + 100_000), "a buff never follows itself");
        m.began(brood, now);
        m.began(rain, now + 1);
        m.nothingToStart(now + 1);
        h.assertTrue(!m.allowed(brood, now + 100), "a move that hurts nobody keeps its rest, however long nothing else can start");
        h.assertTrue(!m.allowed(rain, now + 1 + FightMind.REPEAT_AFTER), "a blow doesn't follow itself early");
        h.assertTrue(m.allowed(rain, now + 2 + FightMind.REPEAT_AFTER), "the last resort");
        m.began(rain, now + 2 + FightMind.REPEAT_AFTER);
        h.assertTrue(m.lastResorts() == 1, "and it's counted");
        now += 1000;
        m.began(sting, now);
        m.began(cut, now + 1);
        h.assertTrue(!m.allowed(sting, now + 2), "a blow rests");
        m.nothingToStart(now + 2);
        h.assertTrue(m.allowed(sting, now + 3 + FightMind.STALE_TICKS), "unless nothing else can start");
        h.succeed();
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

    /**
     * A real fight with someone in reach, near and further off in turn, drinking now and then: most drinks in reach are
     * answered (SPEC §48.5), and every one it was free to answer is. The bar is over drinks in reach, as §48.5 has it,
     * and over enough of them to be a measure: with 56 a run's own fight moved the share by 6% either way, so the Regent
     * (about 70% since the Stinger Volley waits for phase 2, c6270a8c8) passed or failed on the dice; fifteen fights of
     * 224 still ran from 65% to 79%. A drink it can't answer by §48.2 (one that comes and goes inside its own blow, or
     * when only the move it just made reaches) is still counted when one of its answers could land.
     */
    private static void drinks(GameTestHelper h, String id) {
        Scene s = scene(h, id);
        // standing, as a player stands: a test player is never ticked onto the ground, and a kit flier follows anyone
        // three seconds off it to a block under their feet (§47.15), here into the floor
        s.player.setOnGround(true);
        FightScripts.Answer drink = s.kit.fight().mind().orElseThrow().answers().stream().filter(a -> a.on() == FightScripts.Fact.DRINK).findFirst().orElse(null);
        h.assertTrue(drink != null, id + " answers a drink");
        double[] edges = {2.0, 7.0};
        Vec3[] stand = {s.at(edges[0])};
        s.player.setPos(stand[0]);
        s.boss.getRandom().setSeed(id.hashCode() * 17L);
        net.minecraft.util.RandomSource dice = net.minecraft.util.RandomSource.create(id.hashCode());
        int[] tick = {0}, next = {60}, drinkStart = {-1}, drinkEnd = {-1}, drinks = {0}, noticeWas = {0};
        int[][] answered = {{0, 0}, {0, 0}}; // answered and in reach, near and further off
        int[] free = {0};
        boolean[] done = {false}, reach = {false}, couldHave = {false};
        StringBuilder missed = new StringBuilder();
        // a sequence's step, not onEachTick: that books one run per tick up to the timeout and walks them all every
        // tick, which over this many ticks is minutes of the suite
        h.startSequence().thenExecuteFor(SWIG, () -> {
            int t = ++tick[0];
            s.boss.setTarget(s.player);
            s.player.setHealth(s.player.getMaxHealth());
            s.player.setPos(stand[0]);
            int where = drinks[0] & 1;
            // from the room's middle: a boss drops a target that leaves its arena
            if (t == next[0] - 40) stand[0] = s.home.add(0, 0, edges[where] + s.boss.getBbWidth() * 0.5 + s.player.getBbWidth() * 0.5);
            if (t == next[0] && drinks[0] < DRINKS) {
                drinkStart[0] = t;
                drinkEnd[0] = t + DRINK_TICKS;
                drinks[0]++;
                done[0] = reach[0] = couldHave[0] = false;
                next[0] = t + DRINK_EVERY + dice.nextInt(40);
                Watch.stage(s.player, Watch.DRINK);
            }
            boolean noticing = s.boss.kitPose() == AwakenBoss.KIT_POSE_NOTICE;
            if (noticing && noticeWas[0] == 0 && t <= drinkEnd[0] && !done[0]) done[0] = true;
            noticeWas[0] = noticing ? 1 : 0;
            // once it can believe the drink: could an answer land, and was it free to give one (in time to be seen)?
            if (drinkStart[0] >= 0 && !done[0] && t >= drinkStart[0] + s.mind.react && t <= drinkEnd[0]) {
                if (s.run.inReach(drink, s.player)) reach[0] = true;
                if (t <= drinkEnd[0] - 3 && s.run.betweenBlows() && s.run.couldAnswer(drink, s.player)) couldHave[0] = true;
            }
            if (t == drinkEnd[0]) Watch.stage(s.player, 0);
            if (t == drinkEnd[0] + 1) {
                int was = (drinks[0] - 1) & 1;
                if (reach[0] || done[0]) answered[was][1]++;
                if (done[0]) answered[was][0]++;
                if (couldHave[0]) {
                    free[0]++;
                    if (!done[0]) missed.append(' ').append(drinks[0]);
                }
            }
        });
        h.runAfterDelay(SWIG, () -> {
            int yes = answered[0][0] + answered[1][0], inReach = answered[0][1] + answered[1][1];
            Awaken.LOG.info("[mind] {}: {} of {} drinks in reach answered ({} of {} near, {} of {} further off; {} drinks), every one of {} it was free to answer{}",
                    id, yes, inReach, answered[0][0], answered[0][1], answered[1][0], answered[1][1], drinks[0], free[0], missed.isEmpty() ? "" : " but" + missed);
            h.assertTrue(inReach * 4 >= drinks[0] * 3, id + ": only " + inReach + " of " + drinks[0] + " drinks were in reach of an answer (the stands are wrong)");
            h.assertTrue(yes * 100 >= inReach * 60, id + " answered " + yes + " of " + inReach + " drinks in reach");
            h.assertTrue(missed.isEmpty(), id + " was free to answer drinks" + missed + " and didn't");
            Watch.stage(s.player, 0);
            s.boss.discard();
            h.succeed();
        });
    }
}
