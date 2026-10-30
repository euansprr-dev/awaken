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
        AwakenTests.register("bells_none_in_the_open", 40, BellTests::noneInTheOpen);
        AwakenTests.register("bells_song_arrangement", 20, BellTests::songArrangement);
        AwakenTests.register("bells_sounds_placed", 20, BellTests::soundsPlaced);
        AwakenTests.register("bells_lord_keeps", 40, BellTests::lordKeeps);
        AwakenTests.register("bells_hundred_peal", 20, BellTests::hundredPeal);
        AwakenTests.register("bells_story_start", 20, BellTests::storyStart);
    }

    /**
     * Ilse's telling (SPEC §43.15) files the Ember's Remembered lead once (the Book opens at 0 / 100), none once it's held;
     * the ground is quiet within 100 of a home's frame and not 150 away; with no frame nothing rings.
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
        BlockPos frame = new BlockPos(-2_000_000, 70, 2_000_000);
        BellWorld.Home home = new BellWorld.Home();
        home.frame = frame;
        try {
            h.assertTrue(!Bells.quiet(level, frame), "nothing is heard where nothing rings");
            w.homes.put(frame.below(4).asLong(), home);
            h.assertTrue(Bells.quiet(level, frame.south(80)), "a home's frame keeps gates off within 100");
            h.assertTrue(!Bells.quiet(level, frame.south(150)), "and not 150 away");
            // the bare fire never sings: no frame, no song
            BellWorld.Home bare = new BellWorld.Home();
            bare.hung.add(7);
            h.assertTrue(BellTower.ringsFrom(level, bare) == null, "a home with bells and no frame rings nothing");
        } finally {
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

    /**
     * No bell lies in the open (SPEC §43.16): an old world's built sleeping place near a player is cleared (its bell gone,
     * the place dropped from the world's state), one never built is dropped, and an old Book's leads to such bells close.
     */
    static void noneInTheOpen(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(at, BellContent.SLEEPING_BELL.get().defaultBlockState());
        BellWorld w = BellWorld.get(level.getServer());
        String key = "test" + at.asLong(), planned = "test_planned" + at.asLong();
        BellWorld.Site s = new BellWorld.Site("barrow", BellSites.Kind.BURIED, at);
        s.state = BellWorld.Site.ASLEEP;
        w.sites.put(key, s);
        w.sites.put(planned, new BellWorld.Site("mere", BellSites.Kind.SUNK, at.east(4000)));
        p.snapTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 3.5, 0, 0);
        boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        if (overworld) BellSites.tick(p);
        else BellSites.clear(level, s);
        boolean gone = !level.getBlockState(at).is(BellContent.SLEEPING_BELL.get());
        boolean dropped = !overworld || !w.sites.containsKey(key) && !w.sites.containsKey(planned);
        w.sites.remove(key);
        w.sites.remove(planned);
        h.assertTrue(gone, "the old sleeping bell is gone from the land");
        h.assertTrue(dropped, "the built place and the planned one are both dropped");
        BellBook b = new BellBook();
        b.leads.put(BellCatalog.of("barrow").no(), new BellBook.Lead());
        b.leads.put(BellCatalog.of("watch").no(), new BellBook.Lead());
        h.assertTrue(BellSites.closeOldLeads(b), "a lead to a bell that lay in the land closes");
        h.assertTrue(b.leads.size() == 1 && b.leads.containsKey(BellCatalog.of("watch").no()), "a holdfast's lead stays");
        h.succeed();
    }

    /**
     * The song (SPEC §43.16): one bell tolls three times; four ring three rounds, highest first, and the deepest once more;
     * thirty play the tune with each run of missing steps one beat long; never a knock, never over 25 seconds.
     */
    static void songArrangement(GameTestHelper h) {
        h.assertTrue(SongPlan.of(no -> false).strikes().isEmpty(), "no bells, no song");
        SongPlan.Plan one = SongPlan.of(no -> no == 7);
        h.assertTrue(one.strikes().size() == 3 && one.strikes().stream().allMatch(x -> x.no() == 7), "one bell tolls three times");
        h.assertTrue(one.strikes().get(1).tick() - one.strikes().get(0).tick() >= 40, "slowly, each stroke left to ring out");
        java.util.Set<Integer> four = Set.of(7, 12, 17, 40);
        SongPlan.Plan rounds = SongPlan.of(four::contains);
        h.assertTrue(rounds.strikes().size() == 13, "four bells: three rounds and the deepest once more (" + rounds.strikes().size() + ")");
        int top = rounds.strikes().getFirst().no(), last = rounds.strikes().getLast().no();
        for (int no : four) {
            h.assertTrue(SongPlan.height(top) >= SongPlan.height(no), "rounds start on the highest bell");
            h.assertTrue(SongPlan.height(last) <= SongPlan.height(no), "and end on the deepest");
        }
        java.util.Set<Integer> thirty = new java.util.HashSet<>();
        for (int no = 0; no < 100 && thirty.size() < 30; no += 3) thirty.add(no);
        SongPlan.Plan tune = SongPlan.of(thirty::contains);
        int gap = 0;
        for (int i = 1; i < tune.strikes().size(); i++) gap = Math.max(gap, tune.strikes().get(i).tick() - tune.strikes().get(i - 1).tick());
        h.assertTrue(gap <= 2 * SongPlan.SLOW_BEAT, "a run of missing bells is one beat, not a long silence (" + gap + ")");
        for (int n : new int[]{1, 2, 9, 10, 30, 64, 100}) {
            SongPlan.Plan pl = SongPlan.of(no -> no < n);
            h.assertTrue(pl.length() <= 20 * 25, n + " bells: the song is 25 seconds at most (" + pl.length() / 20 + ")");
            for (SongPlan.Strike x : pl.strikes()) h.assertTrue(x.no() < n, "only bells in the song ring: no knock for a missing one");
        }
        h.succeed();
    }

    /** Every bell sound played in the world is mono, so it comes from where its bell is and fades with distance. */
    static void soundsPlaced(GameTestHelper h) {
        java.util.List<String> files = new java.util.ArrayList<>(java.util.List.of("bell_handbell", "bell_toll", "bell_hum", "bell_wake", "bell_gap"));
        for (int k = 0; k < 15; k++) files.add("bell_note_" + k);
        for (String f : files) {
            int ch = channels("/assets/awaken/sounds/" + f + ".ogg");
            h.assertTrue(ch == 1, f + ".ogg is mono (" + ch + " channels)");
        }
        h.succeed();
    }

    /** The channel count in an Ogg Vorbis file's identification header, or -1. */
    static int channels(String resource) {
        try (java.io.InputStream in = BellTests.class.getResourceAsStream(resource)) {
            if (in == null) return -1;
            byte[] b = in.readNBytes(256);
            for (int i = 0; i + 12 < b.length; i++)
                if (b[i] == 1 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r' && b[i + 4] == 'b' && b[i + 5] == 'i' && b[i + 6] == 's') return b[i + 11] & 0xFF;
            return -1;
        } catch (java.io.IOException e) {
            return -1;
        }
    }

    /**
     * A gate's Lord keeps what its gate swallowed (SPEC §43.16): the first Lord ever kept the Wren; the next never keeps one
     * (never two in a row), and by the fourth since, one has; a Lord of a gate world at its bell's rank keeps that bell.
     */
    static void lordKeeps(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        java.util.Set<java.util.UUID> party = Set.of(p.getUUID());
        java.util.function.Supplier<net.minecraft.world.entity.LivingEntity> lord = () -> {
            net.minecraft.world.entity.monster.zombie.Zombie z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
            return z;
        };
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
        h.assertTrue(Bells.has(p, "wren") && Bells.count(p) == 1, "the first Lord kept the Wren");
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
        h.assertTrue(Bells.count(p) == 1, "the next Lord never keeps one");
        for (int i = 0; i < 3; i++) Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
        h.assertTrue(Bells.count(p) == 2, "by the fourth since, a Lord has kept another (" + Bells.count(p) + ")");
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.D, "BELL_CRYPT", party, p);
        h.assertTrue(Bells.has(p, "crypt"), "a gate world's Lord at its bell's rank keeps that bell");
        h.succeed();
    }
}
