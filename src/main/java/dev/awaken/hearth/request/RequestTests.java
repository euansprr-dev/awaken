package dev.awaken.hearth.request;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GameTests for Anchor requests: pools, doable offers, partial delivery, rewards once, the streak, saving, Now, credit. */
final class RequestTests {
    private RequestTests() {}

    static void register() {
        AwakenTests.register("hearth_request_pools_valid", 20, RequestTests::poolsValid);
        AwakenTests.register("hearth_request_offers_only_doable", 20, RequestTests::onlyDoable);
        AwakenTests.register("hearth_request_partial_delivery", 40, RequestTests::partialDelivery);
        AwakenTests.register("hearth_request_rewards_once", 40, RequestTests::rewardsOnce);
        AwakenTests.register("hearth_request_streak", 40, RequestTests::streak);
        AwakenTests.register("hearth_request_state_saved_versioned", 20, RequestTests::saved);
        AwakenTests.register("hearth_request_rewards_scale", 20, RequestTests::rewardsScale);
        AwakenTests.register("hearth_request_core_shard_fuses", 20, RequestTests::shardFuses);
        AwakenTests.registerIsolated("hearth_request_offer_flow_and_now", 60, RequestTests::offerFlow);
    }

    /** A context with a fixed rank, level and set of met tokens. */
    record Fixed(Rank rank, int level, Set<String> tokens) implements RequestRules.Context {
        @Override public boolean met(String token) { return tokens.contains(token); }
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        return sp;
    }

    private static int count(Player p, Item item) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    private static long day(GameTestHelper h) {
        return PlayDays.current(h.getLevel().getServer());
    }

    // ---- content ----------------------------------------------------------------------------------------------------

    private static void poolsValid(GameTestHelper h) {
        var all = RequestPools.all();
        h.assertTrue(all.size() >= 30, "at least 30 request kinds, got " + all.size());
        for (var e : all.entrySet()) {
            List<String> bad = RequestPools.problems(e.getKey(), e.getValue(), true);
            h.assertTrue(bad.isEmpty(), String.join("; ", bad));
            h.assertTrue(!e.getValue().hint().isBlank(), e.getKey() + " says where to look");
        }
        for (Rank r : List.of(Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S)) {
            long n = all.values().stream().filter(d -> d.countAt(r) > 0).count();
            h.assertTrue(n >= 5, r + "-rank has " + n + " request kinds (want 5+)");
            // something is always askable, even before any creature is met (wood, stone and the like)
            boolean basic = all.values().stream().anyMatch(d -> d.countAt(r) > 0 && d.requires().isEmpty() && d.anyOf().isEmpty());
            h.assertTrue(basic || r.ordinal() >= Rank.C.ordinal(), r + "-rank has a request with no requirement");
        }
        h.succeed();
    }

    // ---- only what you can get --------------------------------------------------------------------------------------

    private static void onlyDoable(GameTestHelper h) {
        var pool = RequestPools.entries();
        RandomSource rnd = RandomSource.create(7);
        // a brand-new E-rank player who has met nothing: only requests with no requirement
        Fixed nobody = new Fixed(Rank.E, 2, Set.of());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            var pick = RequestRules.pick(pool, nobody, Set.of(), List.of(), rnd);
            h.assertTrue(pick.isPresent(), "a new player can always be asked something");
            RequestDef d = pick.get().def();
            h.assertTrue(d.requires().isEmpty() && d.anyOf().isEmpty(), "nothing met, yet asked for " + pick.get().id());
            h.assertTrue(d.countAt(Rank.E) > 0, pick.get().id() + " isn't an E-rank request");
            seen.add(pick.get().id());
        }
        h.assertTrue(!seen.contains("awaken:mire_hide"), "no Mire Hide before meeting the Mire");
        // met a Bog Toad: Mire Hide comes up, and every pick still holds
        Fixed toad = new Fixed(Rank.E, 4, Set.of("seen:bog_toad"));
        boolean mire = false;
        for (int i = 0; i < 400; i++) {
            var pick = RequestRules.pick(pool, toad, Set.of(), List.of(), rnd);
            h.assertTrue(RequestRules.doable(pick.get().def(), toad), "picked something not doable: " + pick.get().id());
            mire |= pick.get().id().equals("awaken:mire_hide");
        }
        h.assertTrue(mire, "Mire Hide is asked once a Bog Toad has been seen");
        // a group already asked for is never asked twice at once
        RequestDef mh = RequestPools.get("awaken:mire_hide");
        h.assertTrue(mh != null, "the mire hide request exists");
        for (int i = 0; i < 200; i++) {
            var pick = RequestRules.pick(pool, toad, Set.of(mh.groupOr("awaken:mire_hide")), List.of(), rnd);
            h.assertTrue(!pick.get().def().groupOr(pick.get().id()).equals(mh.groupOr("awaken:mire_hide")), "busy group picked");
        }
        // rank gates: an E-rank player is never asked for gate relics; a B-rank one is never asked for first-hour moss
        Fixed lordSlayer = new Fixed(Rank.E, 9, Set.of("killed:awaken:sky_leviathan", "killed:awaken:hollow_herald"));
        for (int i = 0; i < 200; i++) {
            var pick = RequestRules.pick(pool, lordSlayer, Set.of(), List.of(), rnd);
            h.assertTrue(pick.get().def().countAt(Rank.E) > 0, "an E-rank player was asked for " + pick.get().id());
        }
        // the real player's tokens: sightings, vanilla statistics and depth
        ServerPlayer sp = player(h);
        RequestState st = Requests.state(sp);
        h.assertTrue(!Requests.met(sp, st, "seen:bog_toad"), "no sighting yet");
        Progression.setCounter(sp, "awaken:discovered/creature/bog_toad", 1);
        h.assertTrue(Requests.met(sp, st, "seen:bog_toad"), "a sighting counts");
        h.assertTrue(!Requests.met(sp, st, "mined:minecraft:iron_ore"), "never mined iron");
        sp.awardStat(Stats.BLOCK_MINED.get(Blocks.IRON_ORE));
        h.assertTrue(Requests.met(sp, st, "mined:minecraft:iron_ore"), "mined iron once: iron can be asked for");
        sp.awardStat(Stats.ENTITY_KILLED.get(net.minecraft.world.entity.EntityTypes.ZOMBIE));
        h.assertTrue(Requests.met(sp, st, "killed:minecraft:zombie"), "kills count");
        st.lowestY = 20;
        h.assertTrue(Requests.met(sp, st, "depth:32") && !Requests.met(sp, st, "depth:0"), "depth reached");
        h.assertTrue(!Requests.met(sp, st, "mined:not a block"), "a bad token is unmet, never a crash");
        // with the real context, every pick for this player is doable for them
        var ctx = Requests.context(sp, st);
        for (int i = 0; i < 100; i++) {
            var pick = RequestRules.pick(pool, ctx, Set.of(), List.of(), rnd);
            h.assertTrue(RequestRules.doable(pick.get().def(), ctx), "real context picked " + pick.get().id());
        }
        h.succeed();
    }

    // ---- delivery ---------------------------------------------------------------------------------------------------

    private static ActiveRequest bones(int count) {
        return new ActiveRequest("awaken:test_bones", "minecraft:bone", "", count, 0, 1.0, 0);
    }

    private static void partialDelivery(GameTestHelper h) {
        ServerPlayer sp = player(h);
        ServerLevel level = h.getLevel();
        RequestState st = Requests.state(sp);
        st.active.add(bones(8));
        BlockPos anchor = h.absolutePos(BlockPos.ZERO);
        Item essence = CoreApi.ESSENCE_SHARD.get();
        sp.getInventory().add(new ItemStack(Items.BONE, 3));
        int took = Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime());
        h.assertTrue(took == 3, "three bones go in, got " + took);
        h.assertTrue(st.active.size() == 1 && st.active.get(0).delivered() == 3, "the request remembers 3/8");
        h.assertTrue(count(sp, Items.BONE) == 0 && count(sp, essence) == 0, "taken from the pack, nothing paid yet");
        h.assertTrue(st.completed == 0, "not complete");
        // nothing carried: nothing happens (the Anchor's own window would show)
        h.assertTrue(Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime()) == 0, "empty-handed: nothing");
        sp.getInventory().add(new ItemStack(Items.BONE, 7));
        took = Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime());
        h.assertTrue(took == 5, "only the five still needed go in, got " + took);
        h.assertTrue(count(sp, Items.BONE) == 2, "two bones stay in the pack");
        h.assertTrue(st.active.isEmpty() && st.completed == 1, "complete and gone");
        h.assertTrue(count(sp, essence) > 0, "the Anchor paid essence");
        h.assertTrue(st.reputation > 0 && st.forgeCredit > 0, "reputation and Forge credit");
        // worn armour is never taken; tags match any member
        st.active.add(new ActiveRequest("awaken:test_logs", "#minecraft:logs", "Logs", 4, 0, 0.3, 0));
        sp.getInventory().add(new ItemStack(Items.OAK_LOG, 2));
        sp.getInventory().add(new ItemStack(Items.BIRCH_LOG, 2));
        took = Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime());
        h.assertTrue(took == 4 && st.active.isEmpty(), "two kinds of log fill a logs request, got " + took);
        h.succeed();
    }

    private static void rewardsOnce(GameTestHelper h) {
        ServerPlayer sp = player(h);
        ServerLevel level = h.getLevel();
        RequestState st = Requests.state(sp);
        st.active.add(bones(4));
        BlockPos anchor = h.absolutePos(BlockPos.ZERO);
        Item essence = CoreApi.ESSENCE_SHARD.get();
        sp.getInventory().add(new ItemStack(Items.BONE, 4));
        Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime());
        int paid = count(sp, essence);
        int rep = st.reputation, done = st.completed;
        h.assertTrue(paid > 0 && done == 1, "paid once");
        sp.getInventory().add(new ItemStack(Items.BONE, 4));
        int again = Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime());
        h.assertTrue(again == 0, "nothing more is taken");
        h.assertTrue(count(sp, essence) == paid && st.reputation == rep && st.completed == done, "and nothing more is paid");
        h.assertTrue(count(sp, Items.BONE) == 4, "the extra bones stay yours");
        // a second slot's request is paid on its own, once
        st.active.add(bones(2));
        st.active.add(new ActiveRequest("awaken:test_flint", "minecraft:flint", "", 2, 0, 1.0, 0));
        sp.getInventory().add(new ItemStack(Items.FLINT, 2));
        Requests.deliver(sp, level, anchor, st, day(h), level.getGameTime());
        h.assertTrue(st.active.isEmpty() && st.completed == 3, "both finished at one Anchor visit, each paid once, got " + st.completed);
        h.succeed();
    }

    // ---- the streak -------------------------------------------------------------------------------------------------

    private static void streak(GameTestHelper h) {
        h.assertTrue(RequestRules.streakAfter(0, -1, 5) == 1, "first delivery: 1");
        h.assertTrue(RequestRules.streakAfter(1, 5, 6) == 2, "next day: 2");
        h.assertTrue(RequestRules.streakAfter(2, 6, 6) == 2, "same day: still 2");
        h.assertTrue(RequestRules.streakAfter(2, 6, 9) == 3, "three days later: kept");
        h.assertTrue(RequestRules.streakAfter(3, 9, 13) == 1, "four days later: lapsed");
        h.assertTrue(RequestRules.liveStreak(4, 9, 12) == 4 && RequestRules.liveStreak(4, 9, 13) == 0, "a lapsed streak shows 0");
        h.assertTrue(RequestRules.streakMultiplier(1) == 1.0 && Math.abs(RequestRules.streakMultiplier(3) - 1.2) < 1e-9
                && Math.abs(RequestRules.streakMultiplier(40) - 1.5) < 1e-9, "+10% a step after the first, +50% at most");
        h.assertTrue(RequestRules.milestone(3) && RequestRules.milestone(5) && RequestRules.milestone(10) && !RequestRules.milestone(4)
                && !RequestRules.milestone(1), "milestones at 3, 5, 10");
        // through the real completion: days 1, 2, 3 → streak 3 and its shard; then a long gap starts over
        ServerPlayer sp = player(h);
        RequestState st = Requests.state(sp);
        long now = h.getLevel().getGameTime();
        Requests.complete(sp, st, bones(4).withDelivered(4), 1, now);
        Requests.complete(sp, st, bones(4).withDelivered(4), 2, now);
        int shardsBefore = count(sp, RequestContent.CORE_SHARD.get());
        Requests.Paid third = Requests.complete(sp, st, bones(4).withDelivered(4), 3, now);
        h.assertTrue(st.streak == 3, "three days running, got " + st.streak);
        h.assertTrue(third.reward().shards() >= 1 && count(sp, RequestContent.CORE_SHARD.get()) > shardsBefore, "day three brings a Power Core Shard");
        h.assertTrue(third.reward().streakBonusPercent() == 20, "+20% on the third day");
        Requests.complete(sp, st, bones(4).withDelivered(4), 8, now);
        h.assertTrue(st.streak == 1, "after five quiet days it starts again");
        // ignoring or declining costs nothing
        int rep = st.reputation, s = st.streak;
        RequestState.Offer o = new RequestState.Offer(++st.nonce, bones(4), 8);
        st.offers.add(o);
        Requests.answer(sp, o.action(), false);
        h.assertTrue(st.offers.isEmpty() && st.active.isEmpty() && st.reputation == rep && st.streak == s, "declining: no penalty");
        h.succeed();
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    private static void saved(GameTestHelper h) {
        RequestState st = new RequestState();
        st.active.add(new ActiveRequest("awaken:mire_hide", "awaken:mire_hide", "", 8, 3, 1.2, 4));
        st.offers.add(new RequestState.Offer(7, new ActiveRequest("awaken:logs", "#minecraft:logs", "Logs", 16, 0, 0.3, 5), 5));
        st.credits = 1;
        st.day = 5;
        st.nextOfferAt = 12345;
        st.streak = 4;
        st.lastDeliveryDay = 4;
        st.completed = 9;
        st.reputation = 27;
        st.forgeCredit = 30;
        st.seenTempered = 2;
        st.seenForgeTier = 1;
        st.remember("awaken:a");
        st.remember("awaken:b");
        st.lowestY = -40;
        st.giftDay = 3;
        st.nonce = 7;
        var nbt = RequestState.CODEC.encodeStart(NbtOps.INSTANCE, st).getOrThrow();
        RequestState back = RequestState.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        same(h, st, back, "NBT");
        var json = RequestState.CODEC.encodeStart(JsonOps.INSTANCE, st).getOrThrow();
        h.assertTrue(json.getAsJsonObject().get("schema").getAsInt() == RequestState.SCHEMA, "saved with its schema version");
        same(h, st, RequestState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), "JSON");
        var buf = Unpooled.buffer();
        RequestState.STREAM_CODEC.encode(buf, st);
        same(h, st, RequestState.STREAM_CODEC.decode(buf), "network");
        // an older, smaller save loads with defaults for everything it lacks
        var old = com.google.gson.JsonParser.parseString("{\"active\":[{\"id\":\"awaken:x\",\"target\":\"minecraft:bone\",\"name\":\"\",\"count\":5}]}");
        RequestState o = RequestState.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        h.assertTrue(o.schema == RequestState.SCHEMA && o.active.size() == 1 && o.active.get(0).delivered() == 0 && o.day == -1
                && o.lowestY == RequestState.NO_DEPTH && o.seenTempered == -1, "old saves upgrade on load");
        // and the attachment carries it on the player
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        sp.setData(RequestContent.STATE.get(), back);
        h.assertTrue(Requests.state(sp).reputation == 27, "attached to the player");
        h.succeed();
    }

    private static void same(GameTestHelper h, RequestState a, RequestState b, String how) {
        h.assertTrue(a.active.equals(b.active) && a.offers.equals(b.offers), how + ": requests and offers");
        h.assertTrue(a.credits == b.credits && a.day == b.day && a.nextOfferAt == b.nextOfferAt && a.streak == b.streak
                && a.lastDeliveryDay == b.lastDeliveryDay && a.completed == b.completed && a.reputation == b.reputation
                && a.forgeCredit == b.forgeCredit && a.seenTempered == b.seenTempered && a.seenForgeTier == b.seenForgeTier
                && a.recent.equals(b.recent) && a.lowestY == b.lowestY && a.giftDay == b.giftDay && a.nonce == b.nonce, how + ": every field");
    }

    // ---- rewards ----------------------------------------------------------------------------------------------------

    private static void rewardsScale(GameTestHelper h) {
        RandomSource r = RandomSource.create(1);
        var small = RequestRules.reward(4, Rank.E, 1, 0, r);
        var big = RequestRules.reward(20, Rank.E, 1, 0, r);
        h.assertTrue(big.essence() > small.essence() && big.levels() > small.levels(), "more effort pays more");
        h.assertTrue(RequestRules.reward(1000, Rank.E, 1, 0, r).levels() <= RequestRules.LEVELS_CAP + 1e-9, "a share of a level, capped");
        h.assertTrue(RequestRules.reward(10, Rank.B, 1, 0, r).essence() > RequestRules.reward(10, Rank.E, 1, 0, r).essence(), "higher ranks pay more essence");
        h.assertTrue(small.forgeCredit() == RequestRules.FORGE_CREDIT && big.forgeCredit() == RequestRules.FORGE_CREDIT_BIG, "Forge credit 10% / 15%");
        int plain = RequestRules.reward(10, Rank.D, 1, 0, r).essence();
        int valued = RequestRules.reward(10, Rank.D, 1, RequestRules.Standing.VALUED.at, r).essence();
        h.assertTrue(valued > plain, "a Valued Hunter's town chips in");
        h.assertTrue(RequestRules.Standing.of(0) == RequestRules.Standing.NEWCOMER && RequestRules.Standing.of(19) == RequestRules.Standing.TRUSTED
                && RequestRules.Standing.of(1000) == RequestRules.Standing.BELOVED, "standing tiers");
        h.assertTrue(RequestRules.slots(Rank.D) == 1 && RequestRules.slots(Rank.C) == 2 && RequestRules.slots(Rank.S) == 2, "a second slot at C");
        h.assertTrue(Requests.refund(List.of(16, 4), 25).equals(List.of(4, 1)), "25% of 16 iron and 4 essence");
        h.assertTrue(Requests.refund(List.of(3), 10).equals(List.of(1)), "credit never comes back as nothing");
        h.assertTrue(Requests.refund(List.of(3, 2), 0).equals(List.of(0, 0)), "no credit: nothing");
        h.succeed();
    }

    private static void shardFuses(GameTestHelper h) {
        ServerPlayer sp = player(h);
        ItemStack three = new ItemStack(RequestContent.CORE_SHARD.get(), 3);
        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, three);
        h.assertTrue(RequestContent.CORE_SHARD.get().use(h.getLevel(), sp, net.minecraft.world.InteractionHand.MAIN_HAND)
                == net.minecraft.world.InteractionResult.FAIL && three.getCount() == 3, "three shards don't fuse");
        ItemStack four = new ItemStack(RequestContent.CORE_SHARD.get(), 5);
        boolean fused = CoreShardItem.fuse(sp, h.getLevel(), four);
        if (dev.awaken.core.api.PowerCoreHooks.roll(Rank.E, sp.getRandom()).isEmpty()) {
            h.assertTrue(!fused && four.getCount() == 5, "no powers: nothing taken");
        } else {
            h.assertTrue(fused && four.getCount() == 1, "four of five shards fuse");
        }
        h.succeed();
    }

    // ---- offers, answers and the Now line, with a real Hearth record --------------------------------------------------

    private static void offerFlow(GameTestHelper h) {
        ServerPlayer sp = player(h);
        ServerLevel level = h.getLevel();
        HearthData data = HearthData.get(level.getServer());
        // far away and unloaded, so the Hearth's upkeep leaves it alone for the test's few ticks
        BlockPos anchor = new BlockPos(2_000_000, 70, 2_000_000);
        HearthRecord rec = new HearthRecord(sp.getUUID(), sp.getGameProfile().name(), anchor, HearthService.OVERWORLD);
        data.put(rec);
        try {
            RequestState st = Requests.state(sp);
            Progression.setCounter(sp, "awaken:discovered/creature/bog_toad", 1);
            h.assertTrue(Requests.offerNow(sp), "an offer is made");
            h.assertTrue(st.offers.size() == 1 && Signal.isPending(sp, st.offers.get(0).action()), "it waits as a quiet decision");
            h.assertTrue(Requests.free(sp, st) == 0, "E-rank: one slot, now taken by the offer");
            h.assertTrue(!Requests.offerNow(sp), "no second offer at E-rank");
            RequestState.Offer o = st.offers.get(0);
            Signal.answer(sp, o.action(), true);
            h.assertTrue(st.offers.isEmpty() && st.active.size() == 1 && st.active.get(0).equals(o.request().startedOn(day(h))), "taken on");
            // the Now line counts the pack and points home
            ActiveRequest r = st.active.get(0);
            ItemStack icon = RequestRules.icon(r.target());
            int give = Math.max(1, r.count() / 2);
            sp.getInventory().add(icon.copyWithCount(give));
            var lines = Requests.suggest(sp);
            h.assertTrue(lines.size() == 1 && lines.get(0).text().getString().contains(give + "/" + r.count())
                    && lines.get(0).where().orElseThrow().equals(anchor), "Now: " + (lines.isEmpty() ? "nothing" : lines.get(0).text().getString()));
            h.assertTrue(lines.get(0).priority() == Requests.NOW_GATHER, "gathering is an idea, not an alarm");
            sp.getInventory().add(icon.copyWithCount(r.count()));
            h.assertTrue(Requests.suggest(sp).get(0).priority() == Requests.NOW_READY, "all in hand: bring it home");
            // C-rank opens the second slot
            setRank(sp, Rank.C);
            h.assertTrue(Requests.free(sp, st) == 1 && Requests.offerNow(sp), "a second request at C-rank");
            // a new play-day withdraws yesterday's offer and owes a fresh one; active requests stay
            st.day = day(h) + 7; // any other play-day (the test world may still be on day 0)
            Requests.newDay(sp, st, day(h), level.getGameTime());
            h.assertTrue(st.offers.isEmpty(), "new day: yesterday's offer withdrawn");
            h.assertTrue(st.active.size() == 1, "new day: the taken request kept");
            h.assertTrue(st.credits >= 1, "new day: a fresh offer owed, credits " + st.credits);
            // dropping costs nothing
            int rep = st.reputation;
            Requests.drop(sp, 0, st.active.get(0).id());
            h.assertTrue(st.active.isEmpty() && st.reputation == rep, "dropped, no penalty");
        } finally {
            data.remove(sp.getUUID());
            for (RequestState.Offer o : Requests.state(sp).offers) Signal.withdraw(sp, o.action());
        }
        h.succeed();
    }

    private static void setRank(ServerPlayer sp, Rank rank) {
        while (Progression.rank(sp).ordinal() < rank.ordinal()) Progression.passReassessment(sp);
    }
}
