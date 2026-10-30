package dev.awaken.bells;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** GameTests for the bells (SPEC §43.7): one per mechanism. CI runs them. */
final class BellTests {
    private BellTests() {}

    static void register() {
        AwakenTests.register("bells_catalog_ring", 40, BellTests::catalogRing);
        AwakenTests.register("bells_peals", 40, BellTests::peals);
        AwakenTests.register("bells_tower_plans", 20, BellTests::towerPlans);
        AwakenTests.register("bells_site_wakes", 60, BellTests::siteWakes);
        AwakenTests.register("bells_hundred_peal", 20, BellTests::hundredPeal);
        AwakenTests.register("bells_story_start", 20, BellTests::storyStart);
    }

    /**
     * Ilse's telling (SPEC §43.15) files the Ember's Remembered lead once (the Book opens at 0 / 100), none once it's held;
     * the ground is quiet within 100 of a woken bell's place or a tower's frame, and not 150 away.
     */
    static void storyStart(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        int ember = BellCatalog.of("ember").no();
        Bells.told(p);
        Bells.told(p);
        BellBook.Lead l = Bells.book(p).leads.get(ember);
        h.assertTrue(l != null && l.has(BellBook.How.REMEMBERED) && l.lines.size() == 1, "Ilse's telling files the Ember's lead, once");
        ServerPlayer q = h.makeMockServerPlayerInLevel();
        Bells.ring(q, "ember", "in a test");
        Bells.told(q);
        BellBook.Lead m = Bells.book(q).leads.get(ember);
        h.assertTrue(m == null || !m.has(BellBook.How.REMEMBERED), "no lead for a bell already held");
        ServerLevel level = h.getLevel();
        BellWorld w = BellWorld.get(level.getServer());
        BlockPos site = new BlockPos(2_000_000, 70, 2_000_000), frame = new BlockPos(-2_000_000, 70, 2_000_000);
        BellWorld.Site s = new BellWorld.Site("ember", BellSites.Kind.values()[0], site);
        BellWorld.Home home = new BellWorld.Home();
        home.frame = frame;
        try {
            h.assertTrue(!Bells.quiet(level, site), "nothing is heard where nothing rings");
            s.state = BellWorld.Site.ASLEEP;
            w.sites.put("test_story", s);
            h.assertTrue(!Bells.quiet(level, site.east(50)), "a sleeping bell keeps nothing off");
            s.state = BellWorld.Site.AWAKE;
            h.assertTrue(Bells.quiet(level, site.east(99)), "a woken bell's place keeps gates off within 100");
            h.assertTrue(!Bells.quiet(level, site.east(150)), "and not 150 away");
            w.homes.put(frame.below(4).asLong(), home);
            h.assertTrue(Bells.quiet(level, frame.south(80)), "a tower's frame keeps them off too");
        } finally {
            w.sites.remove("test_story");
            w.homes.remove(frame.below(4).asLong());
        }
        h.succeed();
    }

    /** A hundred bells numbered in order, every one built (100); a bell rings home once, and the first brings the handbell. */
    static void catalogRing(GameTestHelper h) {
        Set<String> ids = new HashSet<>();
        int built = 0;
        for (int i = 0; i < 100; i++) {
            BellCatalog.Def d = BellCatalog.of(i);
            h.assertTrue(d != null && d.no() == i && ids.add(d.id()), "bell " + i + " is in its place, its id unique");
            if (d.built()) built++;
        }
        h.assertTrue(built == 100, "every bell is built (" + built + ")");
        h.assertTrue(BellCatalog.MELODY.length == 100, "the song has a step for every bell");
        for (BellCatalog.Peal pl : BellCatalog.PEALS)
            for (String id : pl.bells()) h.assertTrue(BellCatalog.of(id) != null && BellCatalog.of(id).built(), pl.name() + "'s " + id + " is a built bell");
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(Bells.ring(p, "ember", "in a test"), "the Ember Bell rings home");
        h.assertTrue(!Bells.ring(p, "ember", "in a test"), "a bell rings home once");
        h.assertTrue(Bells.count(p) == 1 && Bells.book(p).given, "it's held and the handbell was given");
        h.assertTrue(p.getInventory().contains(s -> s.is(BellContent.HANDBELL.get())), "the handbell is in the player's bag");
        h.succeed();
    }

    /** A peal can be rung once all three of its bells are held, not before. */
    static void peals(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Bells.ring(p, "ember", "in a test");
        Bells.ring(p, "wren", "in a test");
        h.assertTrue(!Peals.ringable(p).contains("homeward"), "Homeward needs all three of its bells");
        Bells.ring(p, "wayside", "in a test");
        h.assertTrue(Peals.ringable(p).contains("homeward"), "with the third, Homeward can be rung");
        Peals.cycle(p);
        h.assertTrue(Bells.book(p).peal.equals("homeward"), "sneak-use sets the handbell to it");
        Peals.cycle(p);
        h.assertTrue(Bells.book(p).peal.isEmpty(), "and round again to a plain ring");
        h.succeed();
    }

    /** The belfry hangs four bells in a 5×5 under ten high; the tower nine in a 5×5 under fifteen; neither touches the frame. */
    static void towerPlans(GameTestHelper h) {
        BlockPos f = new BlockPos(0, 64, 0);
        check(h, "belfry", BellTower.belfry(f, 0), f, 2, 4, 9);
        check(h, "tower", BellTower.tower(f, Direction.NORTH), f, 2, 9, 14);
        h.succeed();
    }

    private static void check(GameTestHelper h, String what, Map<BlockPos, BlockState> plan, BlockPos f, int half, int bells, int top) {
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockPos d = e.getKey().subtract(f);
            h.assertTrue(Math.abs(d.getX()) <= half && Math.abs(d.getZ()) <= half && d.getY() >= 0 && d.getY() <= top, what + " stays in its room: " + d);
            h.assertTrue(!d.equals(BlockPos.ZERO), what + " leaves the frame be");
            if (e.getValue().is(Blocks.BELL)) n++;
        }
        h.assertTrue(n == bells, what + " hangs " + bells + " bells (" + n + ")");
    }

    /**
     * The Hundred Peal's chain (SPEC §43.14): the Great Belfry hangs 25 bells in a 7×7 under twenty-three high and leaves
     * the frame be; the Kinsong can't be rung before a player's Peal and can after, and the Wake's hook sees the Peal.
     */
    static void hundredPeal(GameTestHelper h) {
        BlockPos f = new BlockPos(0, 64, 0);
        check(h, "great belfry", BellTower.great(f, Direction.NORTH), f, 3, 25, 22);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(!Peals.ringable(p).contains(HundredPeal.KINSONG), "no Kinsong before the Hundred Peal");
        h.assertTrue(!dev.awaken.core.api.BellHooks.pealed(p), "the Wake stays shut before it");
        HundredPeal.pealed(p);
        h.assertTrue(Peals.ringable(p).contains(HundredPeal.KINSONG), "after it, the handbell rings the Kinsong");
        h.assertTrue(dev.awaken.core.api.BellHooks.pealed(p), "and the Wake can be chosen");
        h.succeed();
    }

    /** A sleeping bell struck with nothing keeping it wakes, and its bell goes home with whoever struck it. */
    static void siteWakes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(at, BellContent.SLEEPING_BELL.get().defaultBlockState());
        BellWorld w = BellWorld.get(level.getServer());
        String key = "test" + at.asLong();
        BellWorld.Site s = new BellWorld.Site("barrow", BellSites.Kind.BURIED, at);
        s.state = BellWorld.Site.ASLEEP;
        w.sites.put(key, s);
        BellSites.strike(level, at, p);
        BellSites.strike(level, at, p);
        boolean awake = s.state == BellWorld.Site.AWAKE && level.getBlockState(at).getValue(SleepingBellBlock.AWAKE);
        boolean held = Bells.has(p, "barrow");
        w.sites.remove(key);
        h.assertTrue(awake, "the bell woke");
        h.assertTrue(held, "the Barrow Bell went home with the one who struck it");
        h.succeed();
    }
}
