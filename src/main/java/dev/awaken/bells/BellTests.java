package dev.awaken.bells;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GameTests for the bells (SPEC §43.7, §43.16): one per mechanism. CI runs them. */
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
        AwakenTests.register("bells_hang_rules", 20, BellTests::hangRules);
        AwakenTests.register("bells_song_from_bells", 20, BellTests::songFromBells);
        AwakenTests.register("bells_nothing_from_nowhere", 20, BellTests::nothingFromNowhere);
        AwakenTests.register("bells_roost_bell", 20, BellTests::roostBell);
        AwakenTests.register("bells_old_save", 20, BellTests::oldSave);
    }

    /**
     * Ilse's telling (SPEC §43.15, §43.16) ends with her own bell, the Ember, put in your hands once; the ground is quiet
     * within 100 of a home's frame and not 150 away; with no bell hung at home nothing rings.
     */
    static void storyStart(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BellCatalog.Def ember = BellCatalog.of("ember");
        Bells.told(p);
        Bells.told(p);
        int carried = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (ember.equals(BellItem.def(p.getInventory().getItem(i)))) carried++;
        h.assertTrue(Bells.has(p, "ember") && carried == 1, "Ilse gives you her bell, once (" + carried + ")");
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
            // the bare fire never sings: with no bell hung at home, the far bells alone ring nothing
            BellWorld.Home bare = new BellWorld.Home();
            bare.far.put(BellCatalog.of("square").no(), frame);
            bare.rebuild();
            h.assertTrue(BellTower.ringsFrom(level, bare) == null, "a home with no bell hung rings nothing");
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
        h.assertFalse(p.getInventory().contains(s -> s.is(BellContent.BELL_FRAME_ITEM.get())), "no frame with it: that's made at a table");
        // each of the hundred comes home as a thing to hang, but the six people still ring, which stay where they hang
        ServerPlayer r = h.makeMockServerPlayerInLevel();
        for (int i = 0; i < 100; i++) {
            BellCatalog.Def d = BellCatalog.of(i);
            Bells.ring(r, d, "in a test");
            boolean item = r.getInventory().contains(s -> d.equals(BellItem.def(s)));
            if (Bells.far(d)) h.assertTrue(!item && Bells.book(r).far.containsKey(i), d.id() + " stays where it hangs");
            else h.assertTrue(item, d.id() + " is put in their hands");
            r.getInventory().clearContent();
        }
        h.succeed();
    }

    /** A peal can be rung once all three of its bells hang in your home's song, not before (held isn't enough). */
    static void peals(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2)), frame = fire.east(2);
        HearthRecord rec = home(level, p, fire);
        try {
            place(level, frame, BellContent.BELL_FRAME.get());
            Bells.ring(p, "wayside", "in a test");
            hang(level, frame, p, BellCatalog.of("ember"));
            hang(level, frame, p, BellCatalog.of("wren"));
            h.assertTrue(!Peals.ringable(p).contains("homeward"), "Homeward needs all three of its bells hung, not one in a bag");
            hang(level, frame, p, BellCatalog.of("wayside"));
            h.assertTrue(Peals.ringable(p).contains("homeward"), "with the third hung, Homeward can be rung");
            Peals.cycle(p);
            h.assertTrue(Bells.book(p).peal.equals("homeward"), "sneak-use sets the handbell to it");
            Peals.cycle(p);
            h.assertTrue(Bells.book(p).peal.isEmpty(), "and round again to a plain ring");
        } finally {
            leave(level, rec, frame);
        }
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
        // a mock player starts at the world's spawn, which in a full batch is far from this test: stand beside the Lord
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        net.minecraft.world.phys.Vec3 by = h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 3.5));
        p.snapTo(by.x, by.y, by.z, 0F, 0F);
        ServerPlayer away = h.makeMockServerPlayerInLevel();
        away.snapTo(by.x + 200, by.y, by.z, 0F, 0F);
        java.util.Set<java.util.UUID> party = Set.of(p.getUUID(), away.getUUID());
        java.util.function.Supplier<net.minecraft.world.entity.LivingEntity> lord = () -> {
            net.minecraft.world.entity.monster.zombie.Zombie z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
            return z;
        };
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
        h.assertTrue(Bells.has(p, "wren") && Bells.count(p) == 1, "the first Lord kept the Wren");
        h.assertTrue(Bells.count(away) == 0, "a party member 200 away wasn't there to find it");
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
        h.assertTrue(Bells.count(p) == 1, "the next Lord never keeps one");
        // one in three between, never two in a row, always by the fourth since: one or two more from the next three
        boolean last = false;
        int kept = 0;
        for (int i = 0; i < 3; i++) {
            int had = Bells.count(p);
            Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
            boolean now = Bells.count(p) > had;
            h.assertFalse(now && last, "two Lords in a row never both keep one");
            if (now) kept++;
            last = now;
        }
        h.assertTrue(kept >= 1, "by the fourth since, a Lord has kept another (" + Bells.count(p) + ")");
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.D, "BELL_CRYPT", party, p);
        h.assertTrue(Bells.has(p, "crypt"), "a gate world's Lord at its bell's rank keeps that bell");
        h.succeed();
    }

    // ---- stage 2: bells you carry and hang ------------------------------------------------------------------------

    /** {@code p}'s Hearth with its fire at {@code fire}, put in for a test; {@link #leave} takes it away. */
    private static HearthRecord home(ServerLevel level, ServerPlayer p, BlockPos fire) {
        HearthRecord rec = new HearthRecord(p.getUUID(), "test", fire, level.dimension().identifier().toString());
        HearthData.get(level.getServer()).put(rec);
        return rec;
    }

    /** The test home gone again: its record, its bells' state, and {@code blocks} (cleared without dropping their bells). */
    private static void leave(ServerLevel level, HearthRecord rec, BlockPos... blocks) {
        for (BlockPos b : blocks) level.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        HearthData.get(level.getServer()).remove(rec.owner);
        BellWorld.get(level.getServer()).homes.remove(rec.pos.asLong());
    }

    private static void place(ServerLevel level, BlockPos at, Block block) {
        level.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** {@code d} used on the hook block at {@code at}, as a player would. */
    private static boolean hang(ServerLevel level, BlockPos at, ServerPlayer p, BellCatalog.Def d) {
        return Hanging.hang(level, at, p, BellItem.of(d.no()), Vec3.atCenterOf(at));
    }

    /** The first {@code n} bells that hang on a frame or a Bell Beam (not a Great Bell, not one of the far six). */
    private static List<BellCatalog.Def> small(int n) {
        List<BellCatalog.Def> out = new ArrayList<>();
        for (int i = 0; i < 100 && out.size() < n; i++) {
            BellCatalog.Def d = BellCatalog.of(i);
            if (!Hanging.great(d) && !Bells.far(d)) out.add(d);
        }
        return out;
    }

    /**
     * Where bells hang (SPEC §43.16): three on the frame and not a fourth; a Great Bell never on the frame; a Bell Beam
     * 8 above the fire takes four, 7 above refuses; a Great Beam 12 above takes a Great Bell and nothing else; only
     * inside your own Hearth; a bell taken down comes back to your hands and leaves the song, and its hook takes a bell
     * again, but never one the song already has.
     */
    static void hangRules(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2));
        p.snapTo(fire.getX() + 0.5, fire.getY(), fire.getZ() + 3.5, 0F, 0F);
        BlockPos frame = fire.east(2), beam = fire.above(8), low = fire.above(7).west(3), great = fire.above(12).north(3), greatLow = fire.above(11).south(3);
        HearthRecord rec = home(level, p, fire);
        List<BellCatalog.Def> bells = small(8);
        BellCatalog.Def big = BellCatalog.of("fourth");
        try {
            place(level, frame, BellContent.BELL_FRAME.get());
            place(level, beam, BellContent.BELL_BEAM.get());
            place(level, low, BellContent.BELL_BEAM.get());
            place(level, great, BellContent.GREAT_BEAM.get());
            place(level, greatLow, BellContent.GREAT_BEAM.get());
            ServerPlayer q = h.makeMockServerPlayerInLevel();
            h.assertFalse(hang(level, frame, q, bells.getFirst()), "bells only hang inside your own Hearth");
            h.assertFalse(hang(level, frame, p, big), "a Great Bell won't hang on the frame");
            for (int i = 0; i < 3; i++) h.assertTrue(hang(level, frame, p, bells.get(i)), "the frame takes three bells (" + bells.get(i).id() + ")");
            h.assertFalse(hang(level, frame, p, bells.get(3)), "and not a fourth");
            h.assertFalse(hang(level, low, p, bells.get(3)), "a Bell Beam 7 above the fire won't take one");
            for (int i = 3; i < 7; i++) h.assertTrue(hang(level, beam, p, bells.get(i)), "a Bell Beam 8 above takes four (" + bells.get(i).id() + ")");
            h.assertFalse(hang(level, beam, p, bells.get(7)), "and not five");
            h.assertFalse(hang(level, great, p, bells.get(7)), "a Great Beam is for a Great Bell");
            h.assertFalse(hang(level, greatLow, p, big), "a Great Beam 11 above won't take one");
            h.assertTrue(hang(level, great, p, big), "a Great Beam 12 above takes the Great Bell");
            BellWorld.Home home = BellWorld.get(level.getServer()).peek(fire);
            h.assertTrue(home != null && home.hung.size() == 8 && home.placed.size() == 8, "the song is the eight that hang");
            BellCatalog.Def first = bells.getFirst();
            int on = ((HooksEntity) level.getBlockEntity(frame)).hookOf(first.no());
            Vec3 hook = Hanging.hookPoint(level.getBlockState(frame), on).add(frame.getX(), frame.getY(), frame.getZ());
            h.assertTrue(Hanging.takeDown(level, frame, p, hook) && !home.hung.contains(first.no()), "a bell taken down leaves the song");
            h.assertTrue(p.getInventory().contains(s -> first.equals(BellItem.def(s))), "and comes back to your hands");
            h.assertFalse(hang(level, frame, p, bells.get(1)), "a bell the song already has won't hang twice");
            h.assertTrue(hang(level, frame, p, first), "the hook it left takes a bell again");
        } finally {
            leave(level, rec, frame, beam, low, great, greatLow);
        }
        h.succeed();
    }

    /**
     * The song comes from the bells (SPEC §43.16): each bell hung at home rings from its own hook, each of the far six
     * from where it hangs; the song rings from home only while a bell hangs there.
     */
    static void songFromBells(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2)), frame = fire.east(2), square = fire.offset(300, 10, 0);
        HearthRecord rec = home(level, p, fire);
        List<BellCatalog.Def> two = small(2);
        BellCatalog.Def far = BellCatalog.of("square");
        try {
            place(level, frame, BellContent.BELL_FRAME.get());
            Bells.ring(p, far, "in a test", square);
            BellWorld.Home home = BellWorld.get(level.getServer()).peek(fire);
            h.assertTrue(home != null && home.hung.contains(far.no()) && BellTower.ringsFrom(level, home) == null,
                    "the Square Bell joins the song from the square, but with nothing hung at home nothing rings");
            for (BellCatalog.Def d : two) hang(level, frame, p, d);
            Map<Integer, BlockPos> at = new java.util.HashMap<>();
            for (BellNet.Placed pl : BellTower.places(level, home)) at.put(pl.no(), pl.pos());
            HooksEntity be = (HooksEntity) level.getBlockEntity(frame);
            for (BellCatalog.Def d : two) {
                BlockPos want = BlockPos.containing(Hanging.hookPoint(level.getBlockState(frame), be.hookOf(d.no())).add(frame.getX(), frame.getY(), frame.getZ()));
                h.assertTrue(want.equals(at.get(d.no())), d.id() + " rings from its hook (" + at.get(d.no()) + ")");
            }
            h.assertTrue(square.equals(at.get(far.no())) && at.size() == 3, "the far bell rings from where it hangs, and the song is three");
            h.assertTrue(BellTower.ringsFrom(level, home) != null, "with a bell hung at home, the song rings");
        } finally {
            leave(level, rec, frame);
        }
        h.succeed();
    }

    /**
     * Nothing from nowhere (SPEC §43.16): the bare fire never sings, and a bell you carry is never lost: it doesn't fall
     * with the rest when you die, it comes back into your hands.
     */
    static void nothingFromNowhere(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2)), frame = fire.east(2);
        HearthRecord rec = home(level, p, fire);
        try {
            place(level, frame, BellContent.BELL_FRAME.get());
            BellWorld.Home home = BellWorld.get(level.getServer()).home(fire);
            h.assertFalse(BellTower.song(level, rec, home), "a frame with no bell on it sings nothing");
        } finally {
            leave(level, rec, frame);
        }
        BellCatalog.Def d = small(1).getFirst();
        Vec3 by = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        List<ItemEntity> drops = new ArrayList<>(List.of(new ItemEntity(level, by.x, by.y, by.z, BellItem.of(d.no())),
                new ItemEntity(level, by.x, by.y, by.z, new ItemStack(Items.BREAD))));
        Bells.died(p, drops);
        h.assertTrue(drops.size() == 1 && Bells.book(p).owed.contains(d.no()), "the bell doesn't fall with the rest");
        Bells.owed(p);
        h.assertTrue(p.getInventory().contains(s -> d.equals(BellItem.def(s))) && Bells.book(p).owed.isEmpty(), "it's back in their hands");
        h.succeed();
    }

    /**
     * The Lampwing's old bell (SPEC §43.16): it hangs under a branch of the nearest tree; while the Lampwing sleeps in it
     * nobody can lift it, nor anyone from another fire. A bell left for one hunter is theirs alone, and it goes once lifted.
     */
    static void roostBell(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2)), leaf = fire.offset(1, 5, 1), floor = fire.west(1);
        HearthRecord rec = home(level, p, fire);
        BellCatalog.Def nook = BellCatalog.of("nook");
        try {
            level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), Block.UPDATE_ALL);
            BlockPos at = FoundBells.hangRoost(level, fire.offset(1, 0, 1), 3, fire.asLong());
            h.assertTrue(leaf.below().equals(at) && level.getBlockState(leaf.below()).is(BellContent.FOUND_BELL.get()), "it hangs under the branch (" + at + ")");
            FoundBells.lift(level, at, p);
            h.assertTrue(!Bells.has(p, "nook") && level.getBlockState(at).is(BellContent.FOUND_BELL.get()), "nobody lifts it while the Lampwing sleeps in it");
            ServerPlayer q = h.makeMockServerPlayerInLevel();
            FoundBells.lift(level, at, q);
            h.assertTrue(!Bells.has(q, "nook"), "nor someone from another fire");
            BellCatalog.Def d = small(1).getFirst();
            h.assertTrue(FoundBells.place(level, floor, d.no(), "in a test", List.of(p.getUUID()), 0L, false, false), "a bell can be left on the floor");
            FoundBells.lift(level, floor, q);
            h.assertTrue(!Bells.has(q, d.id()), "a bell left for one hunter isn't anyone else's");
            FoundBells.lift(level, floor, p);
            h.assertTrue(Bells.has(p, d.id()) && p.getInventory().contains(s -> d.equals(BellItem.def(s))), "its hunter lifts it, and carries it");
            h.assertTrue(level.getBlockState(floor).isAir(), "and it's gone from the floor");
        } finally {
            leave(level, rec, leaf, leaf.below(), floor);
        }
        h.succeed();
    }

    /**
     * An old Book (schema 4, from before bells were things) is brought up to date once: each bell it held is owed to the
     * player's hands, the far six join the song from where they hang, and the Signal's line isn't said again.
     */
    static void oldSave(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BellCatalog.Def ember = BellCatalog.of("ember"), square = BellCatalog.of("square");
        BellBook b = new BellBook();
        b.schema = 4;
        b.given = true;
        b.held.put(ember.no(), new BellBook.Held(0, "in an old world"));
        b.held.put(square.no(), new BellBook.Held(0, "in an old world"));
        Bells.save(p, b);
        Bells.upgrade(p);
        BellBook n = Bells.book(p);
        h.assertTrue(n.schema == BellBook.SCHEMA, "the Book is brought up to date");
        h.assertTrue(n.owed.contains(ember.no()) && !n.owed.contains(square.no()), "the Ember Bell is owed as a thing, the Square Bell isn't");
        h.assertTrue(n.far.containsKey(square.no()), "the Square Bell answers from where it hangs");
        h.assertFalse(n.once("signal"), "the Signal's line was said with the first bell then, and isn't again");
        Bells.owed(p);
        h.assertTrue(p.getInventory().contains(s -> ember.equals(BellItem.def(s))), "the Ember Bell is in their hands");
        Bells.upgrade(p);
        h.assertTrue(Bells.book(p).owed.isEmpty(), "and only once");
        h.succeed();
    }
}
