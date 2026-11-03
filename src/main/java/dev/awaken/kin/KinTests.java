package dev.awaken.kin;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;

import java.util.List;

/** GameTests for the kin (SPEC §43.5, §43.6): one per mechanism. CI runs them. */
final class KinTests {
    private KinTests() {}

    static void register() {
        AwakenTests.register("kin_kinship_steps", 40, KinTests::kinshipSteps);
        AwakenTests.register("kin_ore_shed_limits", 40, KinTests::oreShedLimits);
        AwakenTests.register("kin_fields_harvest", 400, KinTests::fieldsHarvest);
        AwakenTests.register("kin_rumours_real", 100, KinTests::rumoursReal);
        AwakenTests.register("kin_done_never_shows", 200, KinTests::doneNeverShows);
        AwakenTests.register("kin_lines_once", 20, KinTests::linesOnce);
        AwakenTests.register("kin_plate", 40, KinTests::plate);
        AwakenTests.register("kin_ride_line", 20, KinTests::rideLine);
        AwakenTests.register("kin_name", 40, KinTests::name);
    }

    /**
     * (SPEC §43.17) Who wears the kin plate: every kin, a tamed pet, a mount that trusts someone; not a wild mount, a wild
     * wolf or a zombie. What it says: a wild one could live with you and counts the helps to trust; one at home says who
     * it lives with and what it's doing; a Gnawed one says so. It shows while looked at and for 4 s after a hurt near you.
     */
    static void plate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        KinBeast b = KinHome.spawn(level, KinKind.LAMPWING, h.absolutePos(new BlockPos(1, 1, 1)), EntitySpawnReason.COMMAND);
        h.assertTrue(b != null, "a Lampwing spawns");
        var wolf = h.spawn(net.minecraft.world.entity.EntityTypes.WOLF, new BlockPos(2, 1, 1));
        var tame = h.spawn(net.minecraft.world.entity.EntityTypes.WOLF, new BlockPos(3, 1, 1));
        tame.tame(p);
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
        var mountType = dev.awaken.mobs.MobTypes.type("frost_stag");
        h.assertTrue(mountType != null, "the Frost Stag is a mount");
        var stag = (dev.awaken.mobs.entity.WildMount) h.spawn(mountType, new BlockPos(3, 1, 3));
        h.assertTrue(KinPlates.wears(b) && KinPlates.wears(tame) && !KinPlates.wears(wolf) && !KinPlates.wears(zombie) && !KinPlates.wears(stag),
                "kin and pets wear it; a wild wolf, a zombie and a wild mount don't");
        stag.tameTo(p, false);
        h.assertTrue(KinPlates.wears(stag), "a mount that trusts you wears it");
        KinNet.Bond wild = KinPlates.read(p, b);
        h.assertTrue(wild.who().equals("Could live with you") && wild.tone() == KinPlates.WILD && wild.step() == Kinship.Step.WARY.ordinal()
                && wild.note().startsWith("Help it three times"), "a wild one: " + wild);
        b.helps.put(p.getUUID(), 2);
        h.assertTrue(KinPlates.read(p, b).note().equals("One more help and it will trust you."), "one help off its trust");
        b.keeper = p.getUUID();
        KinNet.Bond home = KinPlates.read(p, b);
        h.assertTrue(home.who().equals("lives with you") && home.tone() == KinPlates.WARM && home.note().equals(KinPage.doing(b)), "at home: " + home);
        b.gnawed(true);
        h.assertTrue(KinPlates.read(p, b).who().equals("Gnawed") && KinPlates.read(p, b).tone() == KinPlates.GNAWED, "Gnawed says so");
        h.assertTrue(KinPlates.read(p, tame).who().equals("yours") && KinPlates.read(p, stag).who().equals("your mount"), "yours, your mount");
        // when it shows: a look (then a quick fade), a hurt near you (4 s, then a fade); never past the reach
        long never = Long.MIN_VALUE;
        h.assertTrue(KinPlates.visibility(100, 100, never, 5, 24) == 1 && KinPlates.visibility(100 + KinPlates.LOOK_FADE, 100, never, 5, 24) == 0
                && KinPlates.visibility(100, 100, never, 30, 24) == 0, "the look's reach and fade");
        h.assertTrue(KinPlates.visibility(100 + KinPlates.HURT_TICKS, never, 100, 8, 24) == 1
                && KinPlates.visibility(100 + KinPlates.HURT_TICKS + KinPlates.HURT_FADE, never, 100, 8, 24) == 0
                && KinPlates.visibility(110, never, 100, KinPlates.HURT_RANGE + 1, 24) == 0, "hurt near you: 4 s, then gone; not from far off");
        b.discard();
        stag.discard();
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** (SPEC §43.17) The ride line: 3 s after you climb on, then while the mount is hurt or you're in a fight. */
    static void rideLine(GameTestHelper h) {
        h.assertTrue(KinPlates.rideShows(0, 1F, false) && KinPlates.rideShows(KinPlates.RIDE_SHOW, 1F, false), "for 3 s after you climb on");
        h.assertFalse(KinPlates.rideShows(KinPlates.RIDE_SHOW + 1, 1F, false), "then not while it's well and calm");
        h.assertTrue(KinPlates.rideShows(500, 0.9F, false) && KinPlates.rideShows(500, 0.2F, false), "while it's hurt");
        h.assertTrue(KinPlates.rideShows(500, 1F, true), "in a fight");
        h.succeed();
    }

    /** (SPEC §43.17) Naming from the Hearth page: only its keeper names it; cleaned, 24 at most; empty takes it away. */
    static void name(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel(), q = h.makeMockServerPlayerInLevel();
        KinBeast b = KinHome.spawn(level, KinKind.PEBBLEPUP, h.absolutePos(new BlockPos(1, 1, 1)), EntitySpawnReason.COMMAND);
        h.assertTrue(b != null, "a Pebblepup spawns");
        String id = b.getUUID().toString();
        h.assertFalse(KinPlates.name(p, id, "Pip"), "a wild one isn't yours to name");
        b.keeper = p.getUUID();
        h.assertTrue(KinPlates.name(p, id, "  Pip   the  Bright ") && b.getCustomName() != null && b.getCustomName().getString().equals("Pip the Bright"), "named, spaces folded");
        h.assertFalse(KinPlates.name(q, id, "Rocky"), "only its keeper names it");
        h.assertTrue(KinPlates.name(p, id, "A name far longer than twenty-four letters") && b.getCustomName().getString().length() <= KinPlates.NAME_MAX, "24 at most");
        h.assertTrue(KinPlates.name(p, id, "   ") && b.getCustomName() == null, "an empty name takes it away");
        b.discard();
        h.getLevel().getServer().getPlayerList().remove(p);
        h.getLevel().getServer().getPlayerList().remove(q);
        h.succeed();
    }

    /** Up close, a beast's line is given once until it changes, per beast and per player. */
    static void linesOnce(GameTestHelper h) {
        java.util.UUID me = java.util.UUID.randomUUID(), you = java.util.UUID.randomUUID();
        java.util.UUID wing = java.util.UUID.randomUUID(), bun = java.util.UUID.randomUUID();
        String asleep = "[It's asleep. Its breathing is very slow.]";
        h.assertTrue(KinWatch.fresh(me, wing, asleep), "the first time, it's said");
        h.assertFalse(KinWatch.fresh(me, wing, asleep), "said again while nothing changed: no");
        h.assertTrue(KinWatch.fresh(you, wing, asleep), "another player hears it too");
        h.assertTrue(KinWatch.fresh(me, bun, asleep), "another beast's line is its own");
        h.assertTrue(KinWatch.fresh(me, wing, "[It calls you Warm One. It's happy here.]"), "it woke: a new line");
        h.assertTrue(KinWatch.fresh(me, wing, asleep), "asleep again the next day: said once more");
        h.succeed();
    }

    /** Help raises Kinship through its steps; a killing drops it far and the kind runs for days. */
    static void kinshipSteps(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Kinship ks = Kinship.of(p);
        h.assertTrue(ks.step(KinKind.PEBBLEPUP) == Kinship.Step.WARY, "a kind starts Wary");
        boolean crossed = Kinship.add(p, KinKind.PEBBLEPUP, Kinship.HELP);
        h.assertTrue(crossed && Kinship.of(p).step(KinKind.PEBBLEPUP) == Kinship.Step.CURIOUS, "one help makes it Curious");
        for (int i = 0; i < 10; i++) Kinship.add(p, KinKind.PEBBLEPUP, Kinship.HELP);
        h.assertTrue(Kinship.of(p).step(KinKind.PEBBLEPUP) == Kinship.Step.KIN, "eleven helps make it Kin");
        h.assertTrue(Kinship.of(p).step(KinKind.LAMPWING) == Kinship.Step.WARY, "Kinship is kept kind by kind");
        // help is warmed by Spirit, a killing never is
        int before = Kinship.of(p).kind(KinKind.PEBBLEPUP).points;
        Kinship.add(p, KinKind.PEBBLEPUP, -Kinship.KILL);
        h.assertTrue(Kinship.of(p).kind(KinKind.PEBBLEPUP).points == before - Kinship.KILL, "a killing takes " + Kinship.KILL + " away");
        h.succeed();
    }

    /** The Ore Shed only sorts and crushes what's brought: rubble to gravel, gravel to flint or sand, a few nuggets. */
    static void oreShedLimits(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        KinBeast b = KinHome.spawn(level, KinKind.PEBBLEPUP, h.absolutePos(new BlockPos(1, 1, 1)), EntitySpawnReason.COMMAND);
        h.assertTrue(b != null, "a Pebblepup spawns");
        int gravel = 0, nuggets = 0, other = 0;
        for (int i = 0; i < 640; i++) {
            List<ItemStack> out = KinHome.process(level, b, new ItemStack(Items.COBBLESTONE));
            h.assertTrue(out != null, "rubble is the Ore Shed's to work");
            for (ItemStack s : out) {
                if (s.is(Items.GRAVEL)) gravel += s.getCount();
                else if (s.is(Items.IRON_NUGGET) || s.is(Items.GOLD_NUGGET)) nuggets += s.getCount();
                else other += s.getCount();
            }
        }
        h.assertTrue(gravel == 640 && other == 0, "each rubble makes one gravel and nothing else (" + gravel + ", " + other + ")");
        h.assertTrue(nuggets <= 50, "a few nuggets at most, never ore (" + nuggets + " from 640)");
        for (int i = 0; i < 64; i++) {
            List<ItemStack> out = KinHome.process(level, b, new ItemStack(Items.GRAVEL));
            h.assertTrue(out != null && (out.getFirst().is(Items.FLINT) || out.getFirst().is(Items.SAND)) && out.getFirst().getCount() == 1, "gravel sorts to flint or sand");
        }
        h.assertTrue(KinHome.process(level, b, new ItemStack(Items.DIAMOND)) == null, "a diamond isn't the Ore Shed's to work");
        b.discard();
        h.succeed();
    }

    /** A Burrowbun at home harvests a ripe crop, plants it again and carries the harvest to the field's chest. */
    static void fieldsHarvest(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        if (KinWatch.night(level)) level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), "time set 1000");
        BlockPos soil = new BlockPos(2, 1, 2), crop = soil.above(), chestAt = new BlockPos(4, 2, 2);
        // the test space has no floor of its own: a beast that falls through it is hurt and hides for the rest of the test
        for (int x = 0; x <= 5; x++)
            for (int z = 0; z <= 4; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        h.setBlock(chestAt, Blocks.CHEST);
        KinBeast b = KinHome.spawn(level, KinKind.BURROWBUN, h.absolutePos(new BlockPos(3, 2, 2)), EntitySpawnReason.COMMAND);
        h.assertTrue(b != null, "a Burrowbun spawns");
        // a beast whose Hearth is gone goes back to the wild, so this home needs a real one
        java.util.UUID owner = java.util.UUID.randomUUID();
        BlockPos anchor = h.absolutePos(new BlockPos(0, 2, 0));
        dev.awaken.hearth.HearthData homes = dev.awaken.hearth.HearthService.data(level.getServer());
        level.setBlock(anchor, dev.awaken.hearth.HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3); // upkeep drops an Anchor-less home
        homes.put(new dev.awaken.hearth.HearthRecord(owner, "test", anchor, dev.awaken.hearth.HearthService.OVERWORLD));
        b.settle(anchor, h.absolutePos(new BlockPos(3, 2, 3)), owner);
        h.succeedWhen(() -> {
            h.assertTrue(level.getBlockEntity(h.absolutePos(chestAt)) instanceof Container c && has(c, Items.WHEAT), "the wheat reaches the chest");
            h.assertTrue(h.getBlockState(crop).is(Blocks.WHEAT), "the wheat is planted again");
            homes.remove(owner);
        });
    }

    private static boolean has(Container c, net.minecraft.world.item.Item item) {
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
        return false;
    }

    /** Every rumour points at a real site of its kind, 100 to 600 blocks out; the page shows one whenever one exists. */
    static void rumoursReal(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos from = h.absolutePos(BlockPos.ZERO);
        boolean any = false;
        for (KinKind k : KinKind.livingNow()) {
            BlockPos at = KinSpawner.rumour(level, from, k);
            if (at == null) continue;
            any = true;
            double d = Math.hypot(at.getX() - from.getX(), at.getZ() - from.getZ());
            h.assertTrue(d >= KinSpawner.RUMOUR_MIN && d <= KinSpawner.RUMOUR_REACH, k.id() + "'s rumour is " + (int) d + " blocks out");
            KinSpawner.Site s = KinSpawner.site(level, at.getX() >> KinSpawner.CELL_SHIFT, at.getZ() >> KinSpawner.CELL_SHIFT);
            h.assertTrue(s != null && s.kind() == k, k.id() + "'s rumour points at a site of its own kind");
        }
        KinNet.Page page = KinPage.build(p);
        h.assertFalse(page.home().isEmpty(), "the page's Home always says something");
        if (any) h.assertFalse(page.kin().isEmpty(), "the Kin list holds a rumour");
        h.succeed();
    }

    /**
     * A done goal never shows, for the beasts (SPEC §27.12, swept with core.api.Shown): a Track is done the moment its kind
     * is met anywhere; the first fire's Lampwing has no Track (it's by your fire); and once it lives with you the Track
     * goes on its own and nothing anywhere still asks for a Lampwing.
     */
    static void doneNeverShows(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        BlockPos anchor = h.absolutePos(new BlockPos(1, 2, 1)), nook = h.absolutePos(new BlockPos(3, 2, 3));
        dev.awaken.hearth.HearthData homes = dev.awaken.hearth.HearthService.data(level.getServer());
        dev.awaken.hearth.HearthRecord rec = new dev.awaken.hearth.HearthRecord(p.getUUID(), "test", anchor, dev.awaken.hearth.HearthService.OVERWORLD);
        rec.name = "Ashford";
        // upkeep drops any Hearth whose Anchor block is gone (every 20 ticks, at an offset from the owner's id): without a
        // real Anchor the home could go before the Track's check, and the Lampwing with it
        level.setBlock(anchor, dev.awaken.hearth.HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
        homes.put(rec);
        // met before its rumour was followed: the Track's job is done wherever one was met
        h.assertFalse(dev.awaken.core.api.Destinations.done(p, "kin/burrowbun"), "a Burrowbun not met yet: still to find");
        Kinship ks = Kinship.of(p);
        ks.kind(KinKind.BURROWBUN).met = true;
        Kinship.save(p, ks);
        h.assertTrue(dev.awaken.core.api.Destinations.done(p, "kin/burrowbun"), "met anywhere: done");
        h.assertFalse(dev.awaken.core.api.Destinations.done(p, "kin/burrowbun/home"), "going back for one waits until one lives with you");
        // on the Tasks page's Hearth tab (SPEC §27.13): met, it's named; its want read by watching, the want is the step
        var bun = KinGoals.steps(p).stream().filter(st -> st.key().equals("kin/need/burrowbun")).findFirst().orElse(null);
        h.assertTrue(bun != null && bun.title().equals("Bring a Burrowbun home"), "a kind met is named: " + bun);
        ks = Kinship.of(p);
        ks.kind(KinKind.BURROWBUN).watched = 3;
        Kinship.save(p, ks);
        bun = KinGoals.steps(p).stream().filter(st -> st.key().equals("kin/need/burrowbun")).findFirst().orElse(null);
        h.assertTrue(bun != null && bun.title().equals("Give the Burrowbun what it wants") && bun.why().startsWith(KinKind.BURROWBUN.watch.get(2)),
                "a want read is a plain step: " + bun);
        // the first fire's Lampwing, tamed at the fire
        ks = Kinship.of(p);
        ks.lesson = 1;
        Kinship.save(p, ks);
        h.assertFalse(dev.awaken.core.api.Shown.mentioning(p, "watching your fire").isEmpty(), "the page says where it is: " + dev.awaken.core.api.Shown.all(p));
        KinPage.track(p, KinKind.LAMPWING);
        h.assertTrue(dev.awaken.core.api.Destinations.get(p).isEmpty(), "nothing to Track: it's by your fire");
        dev.awaken.core.api.Destinations.set(p, new dev.awaken.core.api.Destinations.Target("Where Lampwings live (roughly)",
                net.minecraft.world.level.Level.OVERWORLD.identifier(), anchor.offset(300, 0, 0), "rumour"), "kin/lampwing");
        h.assertFalse(dev.awaken.core.api.Shown.mentioning(p, "Lampwings live").isEmpty(), "a Track shows while it's still to do");
        KinBeast b = KinHome.spawn(level, KinKind.LAMPWING, nook, EntitySpawnReason.COMMAND);
        h.assertTrue(b != null, "a Lampwing spawns");
        b.addTag("awaken_kin_lesson");
        KinHome.settle(level, b, anchor, nook, p);
        h.assertTrue(Kinship.of(p).lesson == 2, "settling it ends the lesson");
        h.assertTrue(Kinship.of(p).kind(KinKind.LAMPWING).met, "one that comes to live with you is met: the Track's goal holds even if the home ends");
        h.succeedWhen(() -> {
            h.assertTrue(dev.awaken.core.api.Destinations.get(p).isEmpty(), "the Track went on its own once a Lampwing lives with you: "
                    + dev.awaken.core.api.Destinations.get(p) + " for '" + dev.awaken.core.api.Destinations.goal(p) + "'");
            for (String gone : new String[] {"Lampwing", "Nothing lights your yard", "watching your fire"})
                h.assertTrue(dev.awaken.core.api.Shown.mentioning(p, gone).isEmpty(), "'" + gone + "' still shows: " + dev.awaken.core.api.Shown.mentioning(p, gone));
            KinWorld.get(level.getServer()).leave(anchor, b.getUUID());
            b.discard();
            homes.remove(p.getUUID());
        });
    }
}
