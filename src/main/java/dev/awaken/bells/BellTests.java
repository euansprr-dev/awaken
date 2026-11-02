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
        AwakenTests.register("bells_work_plans", 40, BellTests::workPlans);
        AwakenTests.register("bells_hearth_tower", 20, BellTests::hearthTower);
        AwakenTests.register("bells_wagon", 20, BellTests::wagon);
        AwakenTests.register("bells_old_towers", 20, BellTests::oldTowers);
        AwakenTests.register("bells_lord_room", 20, BellTests::lordRoom);
        AwakenTests.registerIsolated("bells_lord_bell_full_pockets", 100, BellTests::lordBellFullPockets);
        AwakenTests.register("bells_found_place", 20, BellTests::foundPlace);
        AwakenTests.registerIsolated("bells_village_quest", 40, BellTests::villageQuest);
        AwakenTests.register("bells_dream_puzzles", 20, BellTests::dreamPuzzles);
        AwakenTests.register("bells_spirit_keeper", 20, BellTests::spiritKeeper);
        AwakenTests.register("bells_dulled", 20, BellTests::dulled);
        AwakenTests.register("bells_look", 20, BellTests::look);
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
        // each of the hundred comes home as a thing to hang, but the six people still ring, which stay where they hang, the
        // Provings' Great Bells, which the Registry's wagon brings, and the Seed Bell, which comes with the Great Belfry
        ServerPlayer r = h.makeMockServerPlayerInLevel();
        for (int i = 0; i < 100; i++) {
            BellCatalog.Def d = BellCatalog.of(i);
            Bells.ring(r, d, "in a test");
            boolean item = r.getInventory().contains(s -> d.equals(BellItem.def(s)));
            if (Bells.far(d)) h.assertTrue(!item && Bells.book(r).far.containsKey(i), d.id() + " stays where it hangs");
            else if (d.source() == BellCatalog.Source.PROVING) h.assertTrue(!item && Bells.book(r).wagon.containsKey(i), d.id() + " goes on the wagon");
            else if (d.source() == BellCatalog.Source.SEED) h.assertTrue(!item, d.id() + " stays for the Great Belfry");
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

    /**
     * An old belfry's shape (read when it turns to beams) has four Bell Beams in a 5×5 under ten high; an old tower's nine in
     * a 5×5 under fifteen; neither touches the frame.
     */
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
            if (Hanging.hooks(e.getValue()) > 0) n++;
        }
        h.assertTrue(n == bells, what + " has " + bells + " beams to hang bells from (" + n + ")");
    }

    /**
     * The Hundred Peal's chain (SPEC §43.14): the Great Belfry has 25 beams (nine Great) in a 7×7 under twenty-three high and leaves
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
        java.util.List<String> files = new java.util.ArrayList<>(java.util.List.of("bell_handbell", "bell_toll", "bell_hum", "bell_wake", "bell_gap",
                "bell_hang", "bell_swell"));
        for (int k = 0; k < 15; k++) files.addAll(List.of("bell_note_" + k, "bell_far_" + k, "bell_muffled_" + k));
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
        liftAll(h, p);
        h.assertTrue(Bells.has(p, "wren") && Bells.count(p) == 1, "the first Lord kept the Wren");
        h.assertTrue(Bells.count(away) == 0, "a party member 200 away wasn't there to find it");
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
        liftAll(h, p);
        h.assertTrue(Bells.count(p) == 1, "the next Lord never keeps one");
        // one in three between, never two in a row, always by the fourth since: one or two more from the next three
        boolean last = false;
        int kept = 0;
        for (int i = 0; i < 3; i++) {
            int had = Bells.count(p);
            Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.E, "NONE", party, p);
            liftAll(h, p);
            boolean now = Bells.count(p) > had;
            h.assertFalse(now && last, "two Lords in a row never both keep one");
            if (now) kept++;
            last = now;
        }
        h.assertTrue(kept >= 1, "by the fourth since, a Lord has kept another (" + Bells.count(p) + ")");
        Bells.lordFell(level, lord.get(), dev.awaken.progression.Rank.D, "BELL_CRYPT", party, p);
        liftAll(h, p);
        h.assertTrue(Bells.has(p, "crypt"), "a gate world's Lord at its bell's rank keeps that bell");
        h.succeed();
    }

    /** {@code p} lifts every bell lying round this test's middle (what its Lords kept); the floor is clear after. */
    private static void liftAll(GameTestHelper h, ServerPlayer p) {
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2));
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6)))
            if (h.getLevel().getBlockState(b).is(BellContent.FOUND_BELL.get())) FoundBells.lift(h.getLevel(), b.immutable(), p);
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6)))
            if (h.getLevel().getBlockState(b).is(BellContent.FOUND_BELL.get())) h.getLevel().setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * A Lord's bell lifted in a gate with full pockets (SPEC §43.16 stage 4, §7 "Held for you"): it isn't dropped on the
     * floor but held for you, to take from the inventory's panel.
     */
    static void lordBellFullPockets(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        dev.awaken.gates.GateService.useTestLevel(level);
        dev.awaken.gates.GateRecord g = null;
        ServerPlayer p = null;
        try {
            g = dev.awaken.gates.GateLiveTests.standUp(h, dev.awaken.gates.GateLiveTests.plan(4307, dev.awaken.gates.content.Theme.BELL_CRYPT,
                    dev.awaken.gates.content.Layout.LABYRINTH, dev.awaken.gates.content.Twist.NONE, 0), 0, 1);
            p = dev.awaken.gates.GateLiveTests.player(h);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            for (int i = 0; i < p.getInventory().getNonEquipmentItems().size(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            h.assertTrue(dev.awaken.gates.run.Runs.enter(p, g), "entered");
            BlockPos at = FoundBells.keep(level, p.blockPosition(), g.key(), BellCatalog.of("wren").no(), "under an E gate's Lord, where the gate had taken it", List.of(p.getUUID()));
            h.assertTrue(at != null, "the bell lies on the floor");
            int near = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6)).size();
            FoundBells.lift(level, at, p);
            boolean held = dev.awaken.gates.run.Spoils.held(p).items.stream().anyMatch(s -> BellCatalog.of("wren").equals(BellItem.def(s)));
            h.assertTrue(Bells.has(p, "wren") && held, "lifted with full pockets, the bell is held for you");
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6)).size() == near, "and nothing fell to the floor");
        } finally {
            dev.awaken.gates.GateLiveTests.teardown(level, p, g);
        }
        h.succeed();
    }

    /**
     * A Lord's bell lies in its room (SPEC §43.16 stage 4): on the floor near where it fell, one for the party, each lifting
     * their own (the second still finds it there); nothing in their hands until they do. A gate that closes first takes it
     * back to the pool: the next Lord that hunter brings down keeps one for them.
     */
    static void lordRoom(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 by = h.absoluteVec(new Vec3(3.5, 1, 3.5));
        ServerPlayer p = h.makeMockServerPlayerInLevel(), q = h.makeMockServerPlayerInLevel();
        p.snapTo(by.x, by.y, by.z, 0F, 0F);
        q.snapTo(by.x + 1, by.y, by.z, 0F, 0F);
        BlockPos fell = h.absolutePos(new BlockPos(2, 1, 2));
        net.minecraft.world.entity.LivingEntity lord = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        Bells.lordFell(level, lord, dev.awaken.progression.Rank.D, "BELL_CRYPT", Set.of(p.getUUID(), q.getUUID()), p);
        BellCatalog.Def crypt = BellCatalog.of("crypt");
        BlockPos at = null;
        for (BlockPos b : BlockPos.betweenClosed(fell.offset(-FoundBells.KEEP_REACH, -3, -FoundBells.KEEP_REACH), fell.offset(FoundBells.KEEP_REACH, 3, FoundBells.KEEP_REACH)))
            if (level.getBlockState(b).is(BellContent.FOUND_BELL.get())) {
                h.assertTrue(at == null, "one bell lies there for the party");
                at = b.immutable();
            }
        h.assertTrue(at != null && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP), "the bell lies on the floor near where the Lord fell (" + at + ")");
        h.assertFalse(Bells.has(p, "crypt") || Bells.has(q, "crypt"), "nothing is in their hands yet");
        FoundBells.lift(level, at, p);
        h.assertTrue(Bells.has(p, "crypt") && level.getBlockState(at).is(BellContent.FOUND_BELL.get()), "the first lifts theirs, and it's still there for the other");
        FoundBells.lift(level, at, q);
        h.assertTrue(Bells.has(q, "crypt") && level.getBlockState(at).isAir(), "the second lifts theirs and the floor is clear");
        // a gate that closes with a bell unlifted: back to the pool
        BellWorld w = BellWorld.get(level.getServer());
        w.kept.add(new BellWorld.Kept("-7", at, BellCatalog.of("barrow").no(), List.of(p.getUUID()), ""));
        BellBook b = Bells.book(p);
        b.lordsSince = 0;
        Bells.save(p, b);
        FoundBells.closed(level.getServer());
        h.assertTrue(w.kept.isEmpty() && w.lost.containsKey(p.getUUID()), "a closed gate takes it back");
        FoundBells.lost(p);
        h.assertTrue(Bells.book(p).lordsSince >= Bells.LORDS_WITHOUT && !w.lost.containsKey(p.getUUID()) && !Bells.has(p, "barrow"),
                "and the next Lord they bring down will keep a bell for them");
        h.assertTrue(crypt != null, "the crypt's bell is in the catalogue");
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

    /**
     * The Thatchbacks' bell plans (SPEC §43.16 stage 3): a Belfry raised from its Work Plan has four Bell Beams nine up and
     * a ladder, a Bell Tower six Bell Beams and two Great Beams thirteen up; each plan goes when it's done; and a bell hangs
     * on a belfry's beam even when the fire is higher than the plan, and makes the home's bell tower.
     */
    static void workPlans(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos c = h.absolutePos(new BlockPos(2, 1, 2)), t = c.east(9), fire = c.above(3).west(3);
        HearthRecord rec = null;
        try {
            h.assertTrue(dev.awaken.kin.works.WorkPlans.raise(level, c, Direction.NORTH, "belfry"), "the Belfry is built to the end");
            h.assertTrue(dev.awaken.kin.works.WorkPlans.raise(level, t, Direction.NORTH, "bell_tower"), "the Bell Tower is built to the end");
            h.assertTrue(level.getBlockState(c).isAir() && level.getBlockState(t).isAir(), "each plan goes when it's done");
            int beams = 0, ladders = 0, small = 0, great = 0;
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-2, 0, -2), c.offset(2, 13, 2))) {
                if (level.getBlockState(b).is(BellContent.BELL_BEAM.get())) beams += b.getY() == c.getY() + 9 ? 1 : 100;
                if (level.getBlockState(b).is(Blocks.LADDER)) ladders++;
            }
            for (BlockPos b : BlockPos.betweenClosed(t.offset(-2, 0, -2), t.offset(2, 16, 2))) {
                if (level.getBlockState(b).is(BellContent.BELL_BEAM.get())) small += b.getY() == t.getY() + 13 ? 1 : 100;
                if (level.getBlockState(b).is(BellContent.GREAT_BEAM.get())) great += b.getY() == t.getY() + 13 ? 1 : 100;
            }
            h.assertTrue(beams == 4 && ladders == 10, "the Belfry: four Bell Beams nine up and a ladder (" + beams + ", " + ladders + ")");
            h.assertTrue(small == 6 && great == 2, "the Bell Tower: six Bell Beams and two Great Beams at the top (" + small + ", " + great + ")");
            rec = home(level, p, fire);
            BlockPos beam = c.offset(1, 9, 0);
            h.assertTrue(hang(level, beam, p, small(1).getFirst()), "a bell hangs on the Belfry's beam, though it's only six above the fire");
            h.assertTrue(dev.awaken.core.api.BellHooks.tower(fire), "and the home has its bell tower");
        } finally {
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-2, 0, -2), t.offset(4, 16, 2)))
                level.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            if (rec != null) leave(level, rec);
        }
        h.succeed();
    }

    /**
     * The Hearth reads the bells (SPEC §43.16 stage 3): tier II asks for a bell tower and it stands once a bell hangs 8 up;
     * tier IV asks for thirty bells in the song; a bell tower (not only the room) keeps Deep Tide nights calm.
     */
    static void hearthTower(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2)), beam = fire.above(8);
        HearthRecord rec = home(level, p, fire);
        try {
            h.assertFalse(need(rec, 2, "bell tower"), "tier II asks for a bell tower");
            h.assertFalse(dev.awaken.hearth.RoomEffects.bellTower(rec), "and with none there's no bell tower's calm");
            place(level, beam, BellContent.BELL_BEAM.get());
            h.assertTrue(hang(level, beam, p, small(1).getFirst()), "a bell hangs 8 above the fire");
            h.assertTrue(need(rec, 2, "bell tower") && dev.awaken.hearth.RoomEffects.bellTower(rec), "now the home has its bell tower, and its calm");
            h.assertFalse(need(rec, 4, "thirty bells"), "tier IV wants thirty bells in the song");
            BellWorld.Home home = BellWorld.get(level.getServer()).peek(fire);
            for (int i = 0; i < 29; i++) home.far.put(60 + i, fire);
            home.rebuild();
            h.assertTrue(need(rec, 4, "thirty bells"), "and has them at thirty");
        } finally {
            leave(level, rec, beam);
        }
        h.succeed();
    }

    private static boolean need(HearthRecord rec, int tier, String what) {
        for (dev.awaken.hearth.TierRules.Need n : dev.awaken.hearth.TierRules.needs(rec, tier)) if (n.text().contains(what)) return n.met();
        throw new IllegalStateException("tier " + tier + " has no need for " + what);
    }

    /**
     * A Proving's Great Bell comes by the Registry's wagon (SPEC §43.16 stage 3): not into your hands but onto the wagon,
     * left in your yard on a later play-day (never the same one), and yours when you lift it.
     */
    static void wagon(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(3, 1, 3));
        p.snapTo(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5, 0F, 0F);
        HearthRecord rec = home(level, p, fire);
        BellCatalog.Def tenor = BellCatalog.of("tenor");
        BlockPos at = null;
        try {
            Bells.ring(p, tenor, "in a test");
            BellBook b = Bells.book(p);
            h.assertTrue(b.wagon.containsKey(tenor.no()) && !p.getInventory().contains(s -> tenor.equals(BellItem.def(s))), "a Great Bell goes on the wagon, not into your hands");
            FoundBells.wagon(p, rec);
            h.assertTrue(Bells.book(p).wagon.containsKey(tenor.no()), "and isn't left the same day");
            b.wagon.put(tenor.no(), dev.awaken.core.PlayDays.current(level.getServer()) - 1);
            Bells.save(p, b);
            FoundBells.wagon(p, rec);
            BellWorld.Home home = BellWorld.get(level.getServer()).peek(fire);
            at = home == null ? null : home.yard.get(tenor.no());
            h.assertTrue(at != null && level.getBlockState(at).is(BellContent.FOUND_BELL.get()) && Bells.book(p).wagon.isEmpty(), "the next day it's in the yard (" + at + ")");
            h.assertTrue(at.distManhattan(fire) <= 10, "a few steps from the fire");
            FoundBells.lift(level, at, p);
            h.assertTrue(p.getInventory().contains(s -> tenor.equals(BellItem.def(s))) && level.getBlockState(at).isAir() && home.yard.isEmpty(), "lifted, it's yours to hang");
        } finally {
            if (at != null) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            leave(level, rec);
        }
        h.succeed();
    }

    /**
     * An old home's self-raised belfry (SPEC §43.16 stage 3): its vanilla bells become Bell Beams where they hung, once,
     * and a bell hangs on them though they're only five above the fire.
     */
    static void oldTowers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 1, 2)), frame = fire.east(4);
        HearthRecord rec = home(level, p, fire);
        Map<BlockPos, BlockState> shape = BellTower.belfry(frame, 0);
        try {
            level.setBlock(frame, BellContent.BELL_FRAME.get().defaultBlockState(), Block.UPDATE_ALL);
            for (Map.Entry<BlockPos, BlockState> e : shape.entrySet())
                level.setBlock(e.getKey(), Hanging.hooks(e.getValue()) > 0 ? Blocks.BELL.defaultBlockState() : e.getValue(), Block.UPDATE_ALL);
            BellWorld w = BellWorld.get(level.getServer());
            BellWorld.Home home = w.home(fire);
            home.frame = frame;
            home.stage = 1;
            BellTower.convert(level, home);
            int beams = 0, bells = 0;
            BlockPos one = null;
            for (BlockPos b : shape.keySet()) {
                if (level.getBlockState(b).is(Blocks.BELL)) bells++;
                if (level.getBlockEntity(b) instanceof HooksEntity be && be.old) {
                    beams++;
                    one = b.immutable();
                }
            }
            h.assertTrue(beams == 4 && bells == 0 && home.beams, "the old belfry's four bells are Bell Beams now (" + beams + ", " + bells + ")");
            h.assertTrue(one != null && hang(level, one, p, small(1).getFirst()), "and a bell hangs on one, though it's only five above the fire");
        } finally {
            List<BlockPos> all = new ArrayList<>(shape.keySet());
            all.add(frame);
            leave(level, rec, all.toArray(BlockPos[]::new));
        }
        h.succeed();
    }

    // ---- stage 5: every bell at a real place ----------------------------------------------------------------------

    /**
     * A bell won at a real place (stage 5): set down a few steps from the hunter on free floor (never where they stand),
     * not in their hands, on their Tasks, and never twice; lifted, it's theirs and forgotten. One knocked out of where it
     * lay goes into their hands. The Registry's two come from Bram in person.
     */
    static void foundPlace(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 by = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.snapTo(by.x, by.y, by.z, 0F, 0F);
        BellCatalog.Def d = BellCatalog.of("stampede"), e = BellCatalog.of("flood");
        BellWorld w = BellWorld.get(level.getServer());
        h.assertTrue(FoundBells.leave(p, d, "in a test", "[test]"), "it's set down");
        BellWorld.Kept k = w.kept.stream().filter(x -> x.no == d.no() && x.left.contains(p.getUUID())).findFirst().orElse(null);
        h.assertTrue(k != null && k.gate.isEmpty() && level.getBlockState(k.pos).is(BellContent.FOUND_BELL.get()) && !k.pos.equals(p.blockPosition()),
                "it lies on the floor near them, not where they stand");
        h.assertTrue(!Bells.has(p, d.id()) && FoundBells.waiting(level.getServer(), p.getUUID(), d.no()) && BellGoals.lying(p) != null,
                "it isn't in their hands yet, and it's on their Tasks");
        h.assertFalse(FoundBells.leave(p, d, "in a test", "[test]"), "never set down twice");
        FoundBells.lift(level, k.pos, p);
        h.assertTrue(Bells.has(p, d.id()) && level.getBlockState(k.pos).isAir() && !FoundBells.waiting(level.getServer(), p.getUUID(), d.no()),
                "lifted, it's theirs and the floor is clear");
        h.assertTrue(FoundBells.leave(p, e, "in a test", "[test]"), "another set down");
        BellWorld.Kept k2 = w.kept.stream().filter(x -> x.no == e.no() && x.left.contains(p.getUUID())).findFirst().orElse(null);
        h.assertTrue(k2 != null, "and watched");
        level.setBlock(k2.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        FoundBells.watch(level.getServer());
        h.assertTrue(Bells.has(p, e.id()) && !FoundBells.waiting(level.getServer(), p.getUUID(), e.no()), "knocked out of its place, it's in their hands");
        ServerPlayer q = h.makeMockServerPlayerInLevel();
        Bells.bram(q, 1);
        h.assertTrue(!Bells.has(q, "registry") && Bells.book(q).said.contains("bram_registry"), "the System says Bram has something, and he isn't here to hand it over");
        Bells.handed(q, "registry");
        h.assertTrue(Bells.has(q, "registry"), "he hands it over in person");
        h.succeed();
    }

    /**
     * A village's bell (stage 5): hidden where its people left it (here buried under a mossy stone), a letter on a lectern
     * by the well saying exactly where, the Tasks pointing at the letter, then at the bell, and nothing once it's lifted.
     */
    static void villageQuest(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos well = h.absolutePos(new BlockPos(2, 1, 2)), ground = h.absolutePos(new BlockPos(9, 1, 2));
        p.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 1.5, 0F, 0F);
        VillageBells.Quest q = VillageBells.quest("mill");
        BellCatalog.Def d = BellCatalog.of("mill");
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Village v = VillageBells.choose(w, d.no(), "test_village", "Testford", well);
        VillageBells.stage(level.getServer(), p.getUUID(), d.no(), VillageBells.HEARD);
        try {
            VillageBells.hideAt(level, v, q, VillageBells.Hide.BURIED, ground);
            h.assertTrue(v.letter != null && level.getBlockEntity(v.letter) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity le && le.hasBook(),
                    "a letter on a lectern by the well");
            h.assertTrue(v.words.contains("steps") && v.words.contains("mossy stone") && v.words.contains("Testford"), "it says exactly where: " + v.words);
            h.assertTrue(v.spot != null && level.getBlockState(v.spot).is(BellContent.FOUND_BELL.get()) && level.getBlockState(v.spot.above()).is(Blocks.MOSSY_COBBLESTONE),
                    "the bell's buried under a mossy stone");
            h.assertTrue(VillageBells.steps(p).size() == 1 && VillageBells.steps(p).getFirst().title().contains("letter"), "the Tasks say find the letter");
            VillageBells.read(p, v.letter);
            h.assertTrue(VillageBells.stage(level.getServer(), p.getUUID(), d.no()) == VillageBells.READ, "reading it counts");
            h.assertTrue(VillageBells.steps(p).getFirst().title().contains("where the letter says") && VillageBells.steps(p).getFirst().where().isEmpty(),
                    "then the Tasks say find it where the letter says, without a pin");
            VillageBells.hum(p);
            FoundBells.lift(level, v.spot, p);
            h.assertTrue(Bells.has(p, "mill") && level.getBlockState(v.spot).isAir() && VillageBells.steps(p).isEmpty(), "dug up and lifted, and the Tasks are done with it");
        } finally {
            for (BlockPos b : new BlockPos[]{v.letter, v.spot, v.spot == null ? null : v.spot.above()})
                if (b != null) level.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            w.villages.remove(d.no());
            w.quests.remove(p.getUUID());
        }
        h.succeed();
    }

    /** The dream bells' order and rules (stage 5): the Dreaming Bell first and asking nothing, the rest their rank's lucidity; in view or not. */
    static void dreamPuzzles(GameTestHelper h) {
        h.assertTrue(DreamBells.ORDER.size() == 6 && DreamBells.ORDER.getFirst().equals("dreaming"), "six dream bells, the Dreaming Bell first");
        for (String id : DreamBells.ORDER)
            h.assertTrue(BellCatalog.of(id).source() == BellCatalog.Source.LUCIDIUM, id + " is a dream's bell");
        h.assertTrue(DreamBells.need(BellCatalog.of("dreaming")) == 0 && DreamBells.need(BellCatalog.of("daydream")) == 40
                && DreamBells.need(BellCatalog.of("falling")) == 60 && DreamBells.need(BellCatalog.of("lucid")) == 80, "lucidity by rank, none for the first");
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 by = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.snapTo(by.x, by.y, by.z, 0F, 0F); // facing south (+z)
        BlockPos ahead = BlockPos.containing(by.x, by.y + 1, by.z + 10), behind = BlockPos.containing(by.x, by.y + 1, by.z - 10);
        h.assertTrue(DreamBells.looksAt(p, ahead) && !DreamBells.looksAt(p, behind), "a bell ahead is in view, one behind isn't");
        h.assertFalse(DreamBells.softLanding(p), "an ordinary fall still hurts");
        h.succeed();
    }

    /**
     * A spirit holding a bell (stage 5): the next spirit whose story you hear takes one of the Veil's five for your rank
     * and keeps it till what it stayed for is done where it stands; then it's in your hands. One spirit, one bell.
     */
    static void spiritKeeper(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 0F, 0F);
        BellWorld w = BellWorld.get(level.getServer());
        try {
            SpiritBells.told(p, "awaken:test_spirit", "Ama", at, dev.awaken.progression.Rank.E);
            h.assertTrue(w.keepers.getOrDefault(p.getUUID(), Map.of()).isEmpty(), "below C rank, no spirit holds one for you");
            int ghost = BellCatalog.of("ghost").no(), whisper = BellCatalog.of("whisper").no();
            level.setBlock(at.east(), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
            SpiritBells.told(p, "awaken:lit_spirit", "Bryn", at, dev.awaken.progression.Rank.C);
            Map<Integer, BellWorld.Keeper> mine = w.keepers.get(p.getUUID());
            h.assertTrue(mine != null && mine.containsKey(whisper) && !mine.containsKey(ghost), "a lantern already lit there: that spirit holds the next bell, not the lantern's");
            mine.remove(whisper);
            level.setBlock(at.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            SpiritBells.told(p, "awaken:test_spirit", "Ama", at, dev.awaken.progression.Rank.C);
            h.assertTrue(mine != null && mine.size() == 1 && mine.containsKey(ghost), "at C rank it holds the Ghost Lantern Bell");
            SpiritBells.told(p, "awaken:test_spirit", "Ama", at, dev.awaken.progression.Rank.C);
            h.assertTrue(mine.size() == 1, "telling it again asks again; one spirit, one bell");
            h.assertTrue(!SpiritBells.steps(p).isEmpty() && SpiritBells.steps(p).getFirst().title().contains("lantern"), "its errand is on the Tasks");
            SpiritBells.check(p, mine);
            h.assertFalse(Bells.has(p, "ghost"), "no lantern yet, no bell");
            level.setBlock(at.east(), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
            SpiritBells.check(p, mine);
            h.assertTrue(Bells.has(p, "ghost") && mine.get(ghost).done && SpiritBells.steps(p).isEmpty(), "a lantern lit where it stands: it lets the bell go");
        } finally {
            level.setBlock(at.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            w.keepers.remove(p.getUUID());
        }
        h.succeed();
    }

    /**
     * A bell lying on a gate's floor is dulled by the Deep (SPEC §43.16 stage 6): drawn dulled where it lies, and still dulled
     * in your bag once lifted (until it hangs, when its hook draws it clean).
     */
    static void dulled(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 by = h.absoluteVec(new Vec3(3.5, 1, 3.5));
        p.snapTo(by.x, by.y, by.z, 0F, 0F);
        BellCatalog.Def wren = BellCatalog.of("wren");
        BellWorld w = BellWorld.get(level.getServer());
        BlockPos at = FoundBells.keep(level, h.absolutePos(new BlockPos(2, 1, 2)), "awaken:test_dull", wren.no(), "under a test Lord", List.of(p.getUUID()));
        try {
            h.assertTrue(at != null && level.getBlockEntity(at) instanceof FoundBellEntity be && be.dulled(), "a bell on a gate's floor lies dulled");
            FoundBells.lift(level, at, p);
            ItemStack carried = ItemStack.EMPTY;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++)
                if (wren.equals(BellItem.def(p.getInventory().getItem(i)))) carried = p.getInventory().getItem(i);
            h.assertTrue(Bells.has(p, "wren") && BellItem.isDulled(carried), "lifted, it's still dulled in your bag");
            h.assertFalse(BellItem.isDulled(BellItem.of(wren.no())), "a bell from anywhere else is clean");
        } finally {
            w.kept.removeIf(k -> k.gate.equals("awaken:test_dull"));
            if (at != null) level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        h.succeed();
    }

    /**
     * Every bell has its look (stage 6): items/bell.json picks a model for each of the hundred, clean and dulled, and each
     * model's lip is where BellLook says (the renderers set bells on the floor and under beams by it).
     */
    static void look(GameTestHelper h) {
        com.google.gson.JsonObject def = json("/assets/awaken/items/bell.json").getAsJsonObject("model");
        for (String branch : List.of("on_false", "on_true")) {
            Map<Integer, String> picked = new java.util.HashMap<>();
            for (var c : def.getAsJsonObject(branch).getAsJsonArray("cases")) {
                String model = c.getAsJsonObject().getAsJsonObject("model").get("model").getAsString();
                for (var n : c.getAsJsonObject().getAsJsonArray("when")) h.assertTrue(picked.put(n.getAsInt(), model) == null, "bell " + n + " is picked once");
            }
            for (int no = 0; no < 100; no++) {
                String model = picked.get(no);
                h.assertTrue(model != null, "bell " + no + " has a model (" + branch + ")");
                h.assertTrue(model.startsWith("awaken:item/bell/" + BellLook.family(BellCatalog.of(no))), "bell " + no + " is cast in its family's metal (" + model + ")");
                float lip = Float.MAX_VALUE;
                for (var e : json("/assets/awaken/models/" + model.substring("awaken:".length()) + ".json").getAsJsonArray("elements")) {
                    var o = e.getAsJsonObject();
                    if (o.getAsJsonObject("faces").has("down") && o.getAsJsonObject("faces").getAsJsonObject("down").get("texture").getAsString().equals("#mouth"))
                        lip = Math.min(lip, o.getAsJsonArray("from").get(1).getAsFloat());
                }
                h.assertTrue(Math.abs(lip - BellLook.bottom(no)) < 0.05F, "bell " + no + "'s lip is where BellLook puts it (" + lip + " vs " + BellLook.bottom(no) + ")");
            }
        }
        h.succeed();
    }

    private static com.google.gson.JsonObject json(String resource) {
        try (java.io.InputStream in = BellTests.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing " + resource);
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
