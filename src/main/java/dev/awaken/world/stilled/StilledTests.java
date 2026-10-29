package dev.awaken.world.stilled;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GameTests for the Stilled (SPEC §43.3). */
final class StilledTests {
    private StilledTests() {}

    static void register() {
        AwakenTests.register("stilled_spots", 20, StilledTests::spots);
        AwakenTests.registerIsolated("stilled_figures", 400, StilledTests::figures);
    }

    /**
     * Where they sit, pure: Aldo, then two more up the north road within 400 past the ridge; the third joins the first
     * hamlet when it's that near; cells keep out of the ridge, two in three near cells hold one, the bands' sizes, the
     * rolls' shares (letters, keys, keepsakes; pairs and parents rarer), all the same twice for a seed.
     */
    static void spots(GameTestHelper h) {
        long seed = 0x5EED_57L;
        List<StilledSpots.Spot> f = StilledSpots.fixed(0, 0, 510, null, seed);
        h.assertTrue(f.size() == 3 && f.get(0).id().equals("aldo") && f.get(0).z() == -522 && f.get(1).z() == -680 && f.get(2).z() == -840,
                "three up the north road past the ridge: " + f);
        h.assertTrue(f.get(0).carry().equals("letter") && f.get(0).n() == StilledText.ALDO && f.get(0).name().equals("Aldo")
                && f.get(0).pose() == StilledText.Pose.MILESTONE, "Aldo on his milestone, with his note to Ilse");
        for (int i = 1; i < f.size(); i++)
            h.assertTrue(Math.abs(f.get(i).z() - f.get(i - 1).z()) >= StilledSpots.APART, "150 apart up the road");
        List<StilledSpots.Spot> joined = StilledSpots.fixed(0, 0, 510, new int[]{10, -870}, seed);
        h.assertTrue(joined.size() == 3 && joined.get(2).where() == StilledSpots.Where.HOME_EDGE && joined.get(2).carry().equals("key")
                && joined.get(2).pose() == StilledText.Pose.REACHING, "the third sits at the near hamlet's edge with a key");
        List<StilledSpots.Spot> apart = StilledSpots.fixed(0, 0, 360, new int[]{0, -1400}, seed);
        h.assertTrue(apart.size() == 4 && apart.get(3).id().equals("hamlet") && apart.get(2).where() == StilledSpots.Where.ROAD,
                "a far hamlet gets its own");

        h.assertTrue(StilledSpots.cellSize(2999) == 1000 && StilledSpots.cellSize(3000) == 2000 && StilledSpots.cellSize(6000) == 3000, "bands");
        List<StilledSpots.Spot> cells = StilledSpots.cells(0, 0, 510, seed, 0, 0, 9000);
        h.assertTrue(cells.equals(StilledSpots.cells(0, 0, 510, seed, 0, 0, 9000)), "the same twice");
        Set<String> ids = new HashSet<>();
        int near = 0;
        for (StilledSpots.Spot s : cells) {
            h.assertTrue(ids.add(s.id()), "one a cell: " + s.id());
            h.assertTrue(Math.hypot(s.x(), s.z()) >= 550, "none inside the ridge: " + s);
            if (s.id().startsWith("g1_")) near++;
        }
        int nearCells = 0;
        for (int i = -4; i < 4; i++) for (int j = -4; j < 4; j++) if (Math.hypot((i + 0.5) * 1000, (j + 0.5) * 1000) < 3000) nearCells++;
        h.assertTrue(near >= nearCells * 0.4 && near <= nearCells * 0.85, "about two near cells in three: " + near + " of " + nearCells);
        h.assertTrue(cells.size() > near + 8, "the far bands hold some too: " + cells.size());

        int letters = 0, keys = 0, keepsakes = 0, pairs = 0, children = 0, n = 4000;
        for (int i = 0; i < n; i++) {
            StilledSpots.Spot s = StilledSpots.roll("t" + i, 0, 0, StilledSpots.mix(i * 7919L));
            switch (s.carry()) {
                case "letter" -> letters++;
                case "key" -> keys++;
                default -> keepsakes++;
            }
            if (s.pose() == StilledText.Pose.PAIR) pairs++;
            if (s.pose() == StilledText.Pose.CHILD) children++;
        }
        h.assertTrue(Math.abs(letters / (double) n - 0.4) < 0.05 && Math.abs(keys / (double) n - 0.35) < 0.05 && Math.abs(keepsakes / (double) n - 0.25) < 0.05,
                "letters, keys, keepsakes 40/35/25: " + letters + "/" + keys + "/" + keepsakes);
        h.assertTrue(Math.abs(pairs / (double) n - 0.1) < 0.03 && Math.abs(children / (double) n - 0.1) < 0.03, "pairs and parents rarer");
        h.succeed();
    }

    /**
     * A figure set down and read: placed on natural ground, a second within 150 dropped; Aldo's letter given once, given
     * again once lost; a key's chest locked without it, opened with it once (the key stays in the lock), empty after;
     * a chest's gifts in order (patterns, then the six relics, then essence); nothing breaks either, not a player, not
     * an explosion; a keepsake's family speaks plainly.
     */
    static void figures(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Stilled data = Stilled.get(level.getServer());
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 1, z = o.getZ() + 1;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int dx = -1; dx <= 4; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                for (int up = 1; up <= 3; up++) level.setBlock(new BlockPos(x + dx, y + up, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        BlockPos feet = new BlockPos(x, y + 1, z);
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "stilled-a", feet.offset(0, 0, 3).subtract(o));
        AwakenTests.Live other = AwakenTests.survivalPlayer(h, "stilled-b", feet.offset(1, 0, 3).subtract(o));
        ServerPlayer p = live.player(), q = other.player();
        List<StilledSpots.Spot> fixed = StilledSpots.fixed(x - 6, z + 522, 510, null, level.getSeed());
        try {
            h.assertTrue(StilledPlacer.fits(level, feet), "grass with room above fits");
            h.assertTrue(!StilledPlacer.natural(Blocks.OAK_PLANKS.defaultBlockState()) && !StilledPlacer.natural(Blocks.DIRT_PATH.defaultBlockState()),
                    "never on planks or a path");
            StilledSpots.Spot aldo = fixed.getFirst();
            StilledPlacer.place(level, data, new StilledPlacer.Ready(aldo, new BlockPos(x, 0, z), 90F, null), fixed);
            h.assertTrue(level.getBlockState(feet).is(StilledContent.STILLED.get()) && data.placed.get("aldo") == feet.asLong(), "Aldo sits down");
            StilledBlockEntity be = (StilledBlockEntity) level.getBlockEntity(feet);
            h.assertTrue(be != null && be.carry.equals("letter") && be.n == StilledText.ALDO && be.name.equals("Aldo") && be.yaw() == 90F, "as himself");
            StilledPlacer.place(level, data, new StilledPlacer.Ready(fixed.get(1), new BlockPos(x + 3, 0, z), 0F, null), fixed);
            h.assertTrue(data.placed.get("north1") == Stilled.SKIPPED && !level.getBlockState(feet.east(3)).is(StilledContent.STILLED.get()),
                    "none within 150 of another");

            // Aldo's letter: once, again once lost
            Stilled.read(p, level, be);
            ItemStack letter = find(p, StilledContent.LETTER_ITEM.get());
            h.assertTrue(letter != null && letter.get(DataComponents.WRITTEN_BOOK_CONTENT) != null
                    && letter.get(DataComponents.WRITTEN_BOOK_CONTENT).author().equals("Aldo"), "his letter, from him");
            h.assertTrue(Progression.profile(p).counter("awaken:stilled_met") == 1, "met, counted");
            Stilled.read(p, level, be);
            h.assertTrue(count(p, StilledContent.LETTER_ITEM.get()) == 1 && Progression.profile(p).counter("awaken:stilled_met") == 1, "not twice");
            p.getInventory().clearContent();
            Stilled.read(p, level, be);
            h.assertTrue(count(p, StilledContent.LETTER_ITEM.get()) == 1, "given again once lost");
            h.assertTrue(StilledText.letter(StilledText.ALDO, "").contains("Ilse") && Stilled.book(StilledText.ALDO, "").pages().size() >= 1, "it reads");

            // a key and its chest
            be.figure = "tkey";
            be.carry = "key";
            be.settlement = "test:left";
            be.targetX = x;
            be.targetZ = z;
            Stilled.read(p, level, be);
            h.assertTrue(LeftChests.keyFor(p, "test:left") != null, "a key with a tag");
            BlockPos chest = feet.east(3);
            level.setBlock(chest, StilledContent.LEFT_CHEST.get().defaultBlockState(), Block.UPDATE_ALL);
            data.chests.put("test:left", chest.asLong());
            LeftChests.open(q, level, chest);
            h.assertTrue(!Stilled.has(q, "opened:test:left") && Progression.profile(q).counter("awaken:left_chests_opened") == 0, "locked without it");
            int before = p.getInventory().getContainerSize() - free(p);
            LeftChests.open(p, level, chest);
            h.assertTrue(Stilled.has(p, "opened:test:left") && Stilled.has(p, "done:tkey") && Progression.profile(p).counter("awaken:left_chests_opened") == 1,
                    "opened with it, once");
            h.assertTrue(LeftChests.keyFor(p, "test:left") == null, "the key stays in the lock");
            h.assertTrue(p.getInventory().getContainerSize() - free(p) > before, "something inside, and supplies");
            LeftChests.open(p, level, chest);
            h.assertTrue(Progression.profile(p).counter("awaken:left_chests_opened") == 1, "empty after");
            Stilled.read(p, level, be);
            h.assertTrue(LeftChests.keyFor(p, "test:left") == null, "no second key once its chest is open");

            // a chest's gifts in order: the early patterns, then the six relics, then essence
            int patterns = 0;
            while (patterns < 10) {
                q.getInventory().clearContent();
                double had = Progression.profile(q).counter("awaken:patterns_from_left_chests");
                LeftChests.give(q, level, chest);
                if (Progression.profile(q).counter("awaken:patterns_from_left_chests") > had) patterns++;
                else break;
            }
            h.assertTrue(patterns >= 1 && patterns < 10, "patterns first: " + patterns);
            // the give that taught no pattern handed over the first relic
            for (int i = 0; i < LeftChests.RELICS.length; i++) {
                var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(dev.awaken.Awaken.MODID, LeftChests.RELICS[i]));
                if (i > 0) {
                    q.getInventory().clearContent();
                    LeftChests.give(q, level, chest);
                }
                h.assertTrue(count(q, item) == 1, "then the " + LeftChests.RELICS[i]);
            }
            q.getInventory().clearContent();
            LeftChests.give(q, level, chest);
            h.assertTrue(count(q, dev.awaken.core.api.CoreApi.ESSENCE_SHARD.get()) >= 3, "then three essence");

            // nothing breaks them
            h.assertTrue(level.getBlockState(feet).getDestroySpeed(level, feet) < 0 && level.getBlockState(chest).getDestroySpeed(level, chest) < 0, "unbreakable");
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            h.assertTrue(!p.gameMode.destroyBlock(feet) && !p.gameMode.destroyBlock(chest), "not even in creative");
            level.explode(null, x + 1.5, y + 1.5, z + 0.5, 4F, Level.ExplosionInteraction.TNT);
            h.assertTrue(level.getBlockState(feet).is(StilledContent.STILLED.get()) && level.getBlockState(chest).is(StilledContent.LEFT_CHEST.get()),
                    "not by an explosion");

            String said = StilledText.reaction("Mary", 0, StilledText.relationWord("dad"));
            h.assertTrue(said.startsWith("Mary: Where did you find that? That's my dad's dog whistle. He went back for the dog."), "plain words: " + said);
            h.assertTrue(StilledText.fill(StilledText.Pose.WAITING.reading, StilledText.Look.OLD_WOMAN)
                    .equals("An old woman sitting with her arms round her knees. She sat down to wait for someone."), "the System reads them plainly");
        } finally {
            for (ServerPlayer pl : new ServerPlayer[]{p, q}) data.marks.remove(pl.getUUID());
            data.placed.remove("aldo");
            data.placed.remove("north1");
            data.chests.remove("test:left");
            data.keyed.clear();
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(feet.east(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            live.leave();
            other.leave();
        }
        h.succeed();
    }

    static ItemStack find(ServerPlayer p, net.minecraft.world.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return inv.getItem(i);
        return null;
    }

    static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        return n;
    }

    static int free(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) n++;
        return n;
    }
}
