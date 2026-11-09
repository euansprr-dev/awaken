package dev.awaken.storage;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Tally;
import dev.awaken.kin.works.Storehouse;
import dev.awaken.kin.works.WorkRooms;
import dev.awaken.storage.Tidying.Outcome;
import dev.awaken.storage.Tidying.Result;
import dev.awaken.storage.Tidying.Section;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Storage's GameTests (SPEC §56). */
final class StorageTests {
    private StorageTests() {}

    static void register() {
        AwakenTests.register("storage_sort_share", 20, StorageTests::sortShare);
        AwakenTests.register("storage_sort_golden", 20, StorageTests::sortGolden);
        AwakenTests.register("storage_bay_majority", 20, StorageTests::bayMajority);
        AwakenTests.register("storage_bay_exact_item", 20, StorageTests::bayExactItem);
        AwakenTests.register("storage_bay_double_is_one", 20, StorageTests::bayDouble);
        AwakenTests.register("storage_bay_copper", 20, StorageTests::bayCopper);
        AwakenTests.register("storage_bay_cells", 20, StorageTests::bayCells);
        AwakenTests.register("storage_tidy_conserves", 200, StorageTests::tidyConserves);
        AwakenTests.register("storage_tidy_rows", 20, StorageTests::tidyRows);
        AwakenTests.register("storage_tidy_refuses_other_menus", 20, StorageTests::tidyRefuses);
        AwakenTests.register("storage_tidy_in_a_chest", 40, StorageTests::tidyChest);
        AwakenTests.register("storage_tidy_rate_limit", 20, StorageTests::tidyRateLimit);
        AwakenTests.register("storage_tidy_stale_state", 20, StorageTests::tidyStale);
        AwakenTests.register("storage_tidy_hotbar_and_pins", 20, StorageTests::tidyPins);
        AwakenTests.register("storage_tidy_shulker", 20, StorageTests::tidyShulker);
        AwakenTests.register("storage_undo_refuses_changed", 40, StorageTests::undoChanged);
    }

    /** Under a tenth of what you can get is "Odd things"; writes every item's sort beside the run for checking. */
    static void sortShare(GameTestHelper h) {
        Map<Sort, Integer> n = new EnumMap<>(Sort.class);
        List<String> dump = new ArrayList<>();
        int total = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem) continue;
            Sort s = Sorts.of(item);
            n.merge(s, 1, Integer::sum);
            total++;
            dump.add(s.key + "\t" + BuiltInRegistries.ITEM.getKey(item));
        }
        dump.sort(null);
        try {
            Files.write(Path.of("storage-sorts.txt"), dump);
        } catch (IOException e) {
            Awaken.LOG.warn("storage: couldn't write the sort list: {}", e.toString());
        }
        int odd = n.getOrDefault(Sort.ODD, 0);
        Awaken.LOG.info("storage sorts {}: {}", total, n);
        h.assertTrue(odd * 10 < total, odd + " of " + total + " items are Odd things");
        h.succeed();
    }

    /** The everyday things, checked by hand: a wrong answer here is what you'd notice first. */
    private static final Map<Sort, String> GOLDEN = Map.ofEntries(
            Map.entry(Sort.STONE, """
                    stone cobblestone deepslate cobbled_deepslate andesite diorite granite tuff calcite dirt coarse_dirt grass_block podzol
                    sand red_sand gravel clay sandstone red_sandstone smooth_stone stone_bricks mossy_cobblestone mossy_stone_bricks
                    cracked_stone_bricks chiseled_stone_bricks stone_slab stone_stairs cobblestone_slab cobblestone_stairs cobblestone_wall
                    stone_brick_slab stone_brick_stairs stone_brick_wall polished_andesite polished_diorite polished_granite bricks brick_slab
                    brick_stairs brick_wall netherrack basalt blackstone polished_blackstone end_stone obsidian crying_obsidian flint clay_ball
                    brick prismarine dark_prismarine soul_sand soul_soil mud packed_mud mud_bricks purpur_block quartz_block"""),
            Map.entry(Sort.WOOD, """
                    oak_log spruce_log birch_log jungle_log acacia_log dark_oak_log mangrove_log cherry_log oak_planks spruce_planks birch_planks
                    stick oak_slab oak_stairs oak_fence oak_fence_gate oak_door oak_trapdoor oak_sign oak_button oak_pressure_plate oak_wood
                    stripped_oak_log crimson_stem warped_stem crimson_planks bamboo_planks bowl"""),
            Map.entry(Sort.BUILDING, """
                    glass glass_pane white_stained_glass white_wool red_wool white_carpet white_concrete white_concrete_powder white_terracotta
                    terracotta white_glazed_terracotta torch soul_torch lantern soul_lantern ladder white_bed chest trapped_chest barrel
                    crafting_table furnace blast_furnace smoker anvil smithing_table stonecutter loom grindstone cartography_table fletching_table
                    enchanting_table brewing_stand cauldron composter hopper dropper dispenser piston sticky_piston observer repeater comparator
                    lever redstone_torch redstone_lamp rail powered_rail detector_rail activator_rail tnt note_block jukebox bookshelf lectern
                    campfire soul_campfire candle white_candle flower_pot item_frame painting armor_stand white_banner white_dye red_dye iron_bars
                    iron_door iron_trapdoor scaffolding end_rod sea_lantern glowstone shulker_box ender_chest"""),
            Map.entry(Sort.METAL, """
                    coal charcoal raw_iron raw_copper raw_gold iron_ingot copper_ingot gold_ingot netherite_ingot netherite_scrap ancient_debris
                    iron_nugget gold_nugget copper_nugget iron_ore deepslate_iron_ore coal_ore copper_ore gold_ore redstone_ore lapis_ore
                    diamond_ore emerald_ore redstone iron_block gold_block copper_block coal_block redstone_block raw_iron_block glowstone_dust
                    diamond_block lapis_block emerald_block"""),
            Map.entry(Sort.GEMS, "diamond emerald lapis_lazuli amethyst_shard quartz echo_shard prismarine_shard prismarine_crystals nether_star amethyst_block"),
            Map.entry(Sort.FOOD, """
                    bread apple golden_apple carrot potato baked_potato cooked_beef beef cooked_porkchop porkchop cooked_chicken chicken
                    cooked_mutton mutton cod cooked_cod salmon cooked_salmon cookie pumpkin_pie cake sugar melon_slice sweet_berries glow_berries
                    dried_kelp mushroom_stew rabbit_stew beetroot_soup beetroot honey_bottle chorus_fruit"""),
            Map.entry(Sort.PLANTS, """
                    wheat wheat_seeds beetroot_seeds pumpkin_seeds melon_seeds oak_sapling spruce_sapling oak_leaves spruce_leaves dandelion poppy
                    sugar_cane bamboo kelp cactus vine lily_pad brown_mushroom red_mushroom moss_block fern short_grass tall_grass pumpkin melon
                    nether_wart cocoa_beans hay_block"""),
            Map.entry(Sort.DROPS, """
                    bone bone_meal string leather feather slime_ball ender_pearl gunpowder blaze_rod blaze_powder egg rotten_flesh spider_eye
                    ink_sac phantom_membrane rabbit_hide ghast_tear shulker_shell zombie_head honeycomb magma_cream"""),
            Map.entry(Sort.GATE, "awaken:power_core awaken:tyrant_crown awaken:molten_core awaken:lord_heart awaken:axe_blueprint awaken:starfall_pattern"),
            Map.entry(Sort.GEAR, """
                    wooden_sword stone_sword iron_sword diamond_sword netherite_sword wooden_pickaxe stone_pickaxe iron_pickaxe diamond_pickaxe
                    iron_axe iron_shovel iron_hoe bow crossbow arrow shield iron_helmet iron_chestplate iron_leggings iron_boots leather_helmet
                    diamond_chestplate elytra shears flint_and_steel fishing_rod bucket water_bucket lava_bucket milk_bucket compass clock spyglass
                    lead name_tag saddle oak_boat minecart trident totem_of_undying firework_rocket bundle awaken:bell_warden_maul awaken:bellguard_helmet"""),
            Map.entry(Sort.BREWS, "potion splash_potion lingering_potion glass_bottle experience_bottle book enchanted_book writable_book paper map filled_map fermented_spider_eye"),
            Map.entry(Sort.BELLS, "bell awaken:handbell awaken:woken_clapper awaken:bell_clapper awaken:ilse_letters awaken:bell_bronze_ingot"),
            Map.entry(Sort.ODD, "music_disc_cat angler_pottery_sherd coast_armor_trim_smithing_template"));

    /** Every item on the hand-checked list goes where a player would look for it; the tags load and nothing's misnamed. */
    static void sortGolden(GameTestHelper h) {
        int checked = 0;
        List<String> wrong = new ArrayList<>();
        for (var e : GOLDEN.entrySet())
            for (String name : e.getValue().trim().split("\\s+")) {
                Identifier id = name.contains(":") ? Identifier.parse(name) : Identifier.withDefaultNamespace(name);
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) wrong.add(name + " isn't an item");
                else if (Sorts.of(item) != e.getKey()) wrong.add(name + " is " + Sorts.of(item) + ", not " + e.getKey());
                checked++;
            }
        h.assertTrue(wrong.isEmpty(), wrong.size() + " of " + checked + " wrong: " + wrong);
        h.succeed();
    }

    // ---- bays --------------------------------------------------------------------------------------------------------

    private static Room room(GameTestHelper h, BlockPos lo, BlockPos hi, boolean cells) {
        LongOpenHashSet c = new LongOpenHashSet();
        BlockPos a = h.absolutePos(lo), b = h.absolutePos(hi);
        if (cells) for (BlockPos p : BlockPos.betweenClosed(a, b)) c.add(p.asLong());
        return new Room(EnumSet.of(RoomType.STOREHOUSE), a, b, c, new int[Furniture.COUNT], new int[Tally.F_COUNT], Set.of(), Reading.of(new Tally(), EnumSet.of(RoomType.STOREHOUSE)));
    }

    private static Container put(GameTestHelper h, BlockPos at, BlockState block) {
        BlockPos p = h.absolutePos(at);
        h.getLevel().setBlock(p, block, 3);
        return WorkRooms.container(h.getLevel(), p);
    }

    /** One cobblestone in the first slot doesn't make a chest full of logs a stone chest. */
    static void bayMajority(GameTestHelper h) {
        Container c = put(h, new BlockPos(1, 2, 1), Blocks.CHEST.defaultBlockState());
        c.setItem(0, new ItemStack(Items.COBBLESTONE));
        for (int i = 1; i < 27; i++) c.setItem(i, new ItemStack(Items.OAK_LOG, 64));
        c.setItem(26, ItemStack.EMPTY);
        h.assertTrue(Storehouse.bayOf(c) == Sort.WOOD, "a chest of logs is " + Storehouse.bayOf(c));
        Room r = room(h, new BlockPos(0, 2, 0), new BlockPos(3, 4, 3), false);
        BlockPos to = Storehouse.bayFor(h.getLevel(), r, new ItemStack(Items.BIRCH_LOG));
        h.assertTrue(h.absolutePos(new BlockPos(1, 2, 1)).equals(to), "a birch log goes to the chest of logs (" + to + ")");
        h.succeed();
    }

    /** Birch goes to the far chest of birch, not the nearer chest of planks. */
    static void bayExactItem(GameTestHelper h) {
        Room r = room(h, new BlockPos(0, 2, 0), new BlockPos(8, 4, 0), false);
        put(h, new BlockPos(4, 2, 0), Blocks.BARREL.defaultBlockState()).setItem(0, new ItemStack(Items.OAK_PLANKS));
        put(h, new BlockPos(8, 2, 0), Blocks.BARREL.defaultBlockState()).setItem(0, new ItemStack(Items.BIRCH_LOG));
        BlockPos to = Storehouse.bayFor(h.getLevel(), r, new ItemStack(Items.BIRCH_LOG));
        h.assertTrue(h.absolutePos(new BlockPos(8, 2, 0)).equals(to), "the chest that already holds it wins (" + to + ")");
        h.succeed();
    }

    /** Two halves are one chest: counted once, opened whole, whichever half you hold. */
    static void bayDouble(GameTestHelper h) {
        BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
        h.getLevel().setBlock(h.absolutePos(new BlockPos(1, 2, 1)), left, 3);
        h.getLevel().setBlock(h.absolutePos(new BlockPos(2, 2, 1)), right, 3);
        Room r = room(h, new BlockPos(0, 2, 0), new BlockPos(3, 4, 3), false);
        List<BlockPos> bays = WorkRooms.containers(h.getLevel(), r);
        h.assertTrue(bays.size() == 1, "a double chest is one bay (" + bays.size() + ")");
        h.assertTrue(WorkRooms.container(h.getLevel(), bays.getFirst()).getContainerSize() == 54, "and it's all 54 slots");
        h.succeed();
    }

    static void bayCopper(GameTestHelper h) {
        put(h, new BlockPos(1, 2, 1), Blocks.COPPER_CHEST.weathering().unaffected().defaultBlockState());
        Room r = room(h, new BlockPos(0, 2, 0), new BlockPos(3, 4, 3), false);
        h.assertTrue(WorkRooms.containers(h.getLevel(), r).size() == 1, "a copper chest is a bay");
        h.succeed();
    }

    /** A chest inside the box but out of the room's cells (and not on its wall) is someone else's. */
    static void bayCells(GameTestHelper h) {
        Room r = room(h, new BlockPos(0, 2, 0), new BlockPos(1, 4, 1), true);
        r = new Room(r.types(), r.min(), r.max().offset(6, 0, 6), r.cells(), r.counts(), r.facts(), r.trophies(), r.reading());
        put(h, new BlockPos(2, 2, 0), Blocks.BARREL.defaultBlockState()); // on the wall of its cells
        put(h, new BlockPos(6, 2, 6), Blocks.BARREL.defaultBlockState()); // in the box, in the next room along
        h.assertTrue(WorkRooms.containers(h.getLevel(), r).size() == 1, "only the room's own chest counts (" + WorkRooms.containers(h.getLevel(), r).size() + ")");
        h.succeed();
    }

    // ---- Tidy --------------------------------------------------------------------------------------------------------

    /** 2,000 random fills: nothing is lost or duplicated, locked slots don't move, and tidying twice changes nothing. */
    static void tidyConserves(GameTestHelper h) {
        Item[] pool = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.BIRCH_LOG, Items.TORCH, Items.RAW_IRON, Items.IRON_INGOT, Items.DIAMOND, Items.BREAD,
                Items.WHEAT_SEEDS, Items.BONE, Items.ROTTEN_FLESH, Items.IRON_SWORD, Items.IRON_PICKAXE, Items.DIAMOND_CHESTPLATE, Items.POTION, Items.BOOK, Items.MUSIC_DISC_CAT,
                Items.ENDER_PEARL, Items.WATER_BUCKET, Items.SNOWBALL, Items.ARROW};
        Random r = new Random(29102026L);
        for (int round = 0; round < 2000; round++) {
            int size = r.nextBoolean() ? 27 : r.nextBoolean() ? 54 : r.nextBoolean() ? 9 : 5;
            ItemStack[] now = new ItemStack[size];
            boolean[] locked = new boolean[size];
            for (int i = 0; i < size; i++) {
                now[i] = ItemStack.EMPTY;
                if (r.nextInt(100) < 60) {
                    ItemStack s = new ItemStack(pool[r.nextInt(pool.length)]);
                    s.setCount(1 + r.nextInt(s.getMaxStackSize()));
                    if (s.isDamageableItem()) s.setDamageValue(r.nextInt(5));
                    now[i] = s;
                }
                locked[i] = size == 27 && r.nextInt(10) == 0;
            }
            int cols = size == 5 ? 5 : 9;
            Tidy.Plan p = Tidy.plan(now, locked, cols, 99);
            h.assertTrue(p != null, "round " + round + ": a fill that came from slots always fits back");
            h.assertTrue(Tidy.sameTotals(now, p.after()), "round " + round + ": the counts changed");
            for (int i = 0; i < size; i++) {
                if (locked[i]) h.assertTrue(ItemStack.matches(now[i], p.after()[i]), "round " + round + ": a pinned slot moved");
                h.assertTrue(p.after()[i].getCount() <= Math.max(1, p.after()[i].getMaxStackSize()), "round " + round + ": an overfull stack");
            }
            Tidy.Plan again = Tidy.plan(p.after(), locked, cols, 99);
            h.assertTrue(again != null && !again.changed(), "round " + round + ": a second Tidy changed something");
        }
        h.succeed();
    }

    /** With room to spare every sort starts its own row, and part-stacks of one thing are merged. */
    static void tidyRows(GameTestHelper h) {
        ItemStack[] now = new ItemStack[27];
        Arrays.fill(now, ItemStack.EMPTY);
        now[3] = new ItemStack(Items.BREAD, 10);
        now[20] = new ItemStack(Items.COBBLESTONE, 40);
        now[11] = new ItemStack(Items.COBBLESTONE, 40);
        now[25] = new ItemStack(Items.OAK_LOG, 8);
        Tidy.Plan p = Tidy.plan(now, new boolean[27], 9, 99);
        h.assertTrue(p != null && p.after()[0].is(Items.COBBLESTONE) && p.after()[0].getCount() == 64, "stone first, one full stack (" + (p == null ? "null" : p.after()[0]) + ")");
        h.assertTrue(p.after()[1].is(Items.COBBLESTONE) && p.after()[1].getCount() == 16, "then what's left of it");
        h.assertTrue(p.after()[9].is(Items.OAK_LOG), "wood starts the next row (" + p.after()[9] + ")");
        h.assertTrue(p.after()[18].is(Items.BREAD), "food the row after (" + p.after()[18] + ")");
        h.succeed();
    }

    private static AwakenTests.Live player(GameTestHelper h) {
        AwakenTests.Live l = AwakenTests.survivalPlayer(h, "Tidy", new BlockPos(2, 2, 2));
        AwakenTests.onEnd(h, l::leave);
        return l;
    }

    /** A chest menu for this barrel, the way a click would open it. */
    private static AbstractContainerMenu open(GameTestHelper h, ServerPlayer p, BlockPos at) {
        put(h, at, Blocks.BARREL.defaultBlockState());
        p.openMenu((MenuProvider) h.getLevel().getBlockEntity(h.absolutePos(at)));
        return p.containerMenu;
    }

    private static void fill(Container c, Object... items) {
        for (int i = 0; i < items.length; i += 2) c.setItem((Integer) items[i], (ItemStack) items[i + 1]);
    }

    /** A furnace's menu, and a chest menu that isn't really a chest, aren't ours to arrange. */
    static void tidyRefuses(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        p.containerMenu = new FurnaceMenu(7, p.getInventory());
        h.assertTrue(Tidying.tidy(p, 7, p.containerMenu.getStateId(), Section.CONTAINER).result() == Result.REFUSED, "a furnace isn't offered");
        p.containerMenu = ChestMenu.threeRows(8, p.getInventory(), new SimpleContainer(27));
        h.assertTrue(Tidying.tidy(p, 8, p.containerMenu.getStateId(), Section.CONTAINER).result() == Result.REFUSED, "someone's button panel isn't a chest");
        p.containerMenu = p.inventoryMenu;
        h.succeed();
    }

    /** Tidy a real barrel through the menu, see it say "already", then undo. */
    static void tidyChest(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        AbstractContainerMenu m = open(h, p, new BlockPos(1, 2, 1));
        Container c = (Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 2, 1)));
        fill(c, 4, new ItemStack(Items.BREAD, 3), 12, new ItemStack(Items.COBBLESTONE, 5));
        m.broadcastChanges();
        Outcome o = Tidying.tidy(p, m.containerId, m.getStateId(), Section.CONTAINER);
        h.assertTrue(o.result() == Result.DONE, "tidied (" + o.result() + ")");
        h.assertTrue(c.getItem(0).is(Items.COBBLESTONE), "stone first: " + c.getItem(0));
        h.assertTrue(c.getItem(9).is(Items.BREAD), "food on its own row: " + c.getItem(9));
        h.assertTrue(o.from().length == 2, "two trips: " + o.from().length);
        h.runAfterDelay(6, () -> {
            h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId(), Section.CONTAINER).result() == Result.ALREADY, "a second Tidy has nothing to do");
            h.runAfterDelay(6, () -> {
                h.assertTrue(Tidying.undo(p, m.containerId).result() == Result.DONE, "undone");
                h.assertTrue(c.getItem(4).is(Items.BREAD) && c.getItem(12).is(Items.COBBLESTONE), "back where they were");
                h.succeed();
            });
        });
    }

    static void tidyRateLimit(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        AbstractContainerMenu m = p.inventoryMenu;
        p.containerMenu = m;
        p.getInventory().setItem(20, new ItemStack(Items.DIRT));
        h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId(), Section.BAG).result() != Result.LIMITED, "the first goes through");
        h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId(), Section.BAG).result() == Result.LIMITED, "the second, the same tick, doesn't");
        h.succeed();
    }

    static void tidyStale(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        AbstractContainerMenu m = p.inventoryMenu;
        p.containerMenu = m;
        p.getInventory().setItem(20, new ItemStack(Items.DIRT));
        h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId() + 1, Section.BAG).result() == Result.STALE, "an out of date client is refused");
        h.assertTrue(p.getInventory().getItem(20).is(Items.DIRT), "and nothing moved");
        h.succeed();
    }

    /** The hotbar and a pinned slot stay where they are. */
    static void tidyPins(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        AbstractContainerMenu m = p.inventoryMenu;
        p.containerMenu = m;
        Inventory inv = p.getInventory();
        inv.setItem(3, new ItemStack(Items.BREAD));
        inv.setItem(9, new ItemStack(Items.DIAMOND_SWORD));
        inv.setItem(30, new ItemStack(Items.DIRT));
        StorageApi.setPinned(p, 9, true);
        h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId(), Section.BAG).result() == Result.DONE, "tidied");
        h.assertTrue(inv.getItem(3).is(Items.BREAD), "the hotbar stays");
        h.assertTrue(inv.getItem(9).is(Items.DIAMOND_SWORD), "the pin stays");
        h.assertTrue(inv.getItem(10).is(Items.DIRT), "and the dirt goes round it: " + inv.getItem(10));
        h.succeed();
    }

    /** A slot that won't take the stack (a shulker inside a shulker box): the whole Tidy stops and nothing moves. */
    static void tidyShulker(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        BlockPos at = new BlockPos(1, 2, 1);
        put(h, at, Blocks.SHULKER_BOX.defaultBlockState());
        p.openMenu((MenuProvider) h.getLevel().getBlockEntity(h.absolutePos(at)));
        AbstractContainerMenu m = p.containerMenu;
        Container c = (Container) h.getLevel().getBlockEntity(h.absolutePos(at));
        fill(c, 5, new ItemStack(Items.SHULKER_BOX), 2, new ItemStack(Items.DIRT));
        m.broadcastChanges();
        h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId(), Section.CONTAINER).result() == Result.FAILED, "stopped");
        h.assertTrue(c.getItem(5).is(Items.SHULKER_BOX) && c.getItem(2).is(Items.DIRT), "and nothing moved");
        h.succeed();
    }

    static void undoChanged(GameTestHelper h) {
        ServerPlayer p = player(h).player();
        AbstractContainerMenu m = p.inventoryMenu;
        p.containerMenu = m;
        Inventory inv = p.getInventory();
        inv.setItem(30, new ItemStack(Items.DIRT));
        h.assertTrue(Tidying.tidy(p, m.containerId, m.getStateId(), Section.BAG).result() == Result.DONE, "tidied");
        inv.setItem(20, new ItemStack(Items.STONE));
        h.runAfterDelay(6, () -> {
            h.assertTrue(Tidying.undo(p, m.containerId).result() == Result.STALE, "Undo can't run over a change");
            h.assertTrue(inv.getItem(20).is(Items.STONE), "the new stone is still there");
            h.succeed();
        });
    }
}
