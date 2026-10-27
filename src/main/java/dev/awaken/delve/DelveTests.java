package dev.awaken.delve;

import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsForge;
import dev.awaken.core.Forces;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.living.craft.Craft;
import dev.awaken.living.craft.Crafts;
import dev.awaken.progression.Progression;
import dev.awaken.progression.ProgressionModule;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The deep's GameTests (SPEC §28.3), one per mechanism: a lode's heart opening its vein (and a fair cave-in),
 * the tier metals in every recipe from D up, the Tuning Fork's answer, and ore XP by rarity and depth with the Mining
 * craft at its own pace.
 */
final class DelveTests {
    private DelveTests() {}

    /** Players a test lends the craft_rate perk to (+50%). */
    private static final Set<UUID> RATED = ConcurrentHashMap.newKeySet();

    static void register() {
        Progression.addPerkSource((p, perk) -> perk.equals(Crafts.RATE) && RATED.contains(p.getUUID()) ? 0.5 : 0);
        AwakenTests.registerIsolated("delve_lode_heart", 400, DelveTests::lodeHeart);
        AwakenTests.register("delve_tier_metals", 40, DelveTests::tierMetals);
        AwakenTests.register("delve_tuning_fork", 40, DelveTests::tuningFork);
        AwakenTests.register("delve_held_light", 20, DelveTests::heldLight);
        AwakenTests.register("progression_ore_xp", 60, DelveTests::oreXp);
    }

    // ---- a heart opens its vein -------------------------------------------------------------------------------------

    /**
     * A chamber 15 x 6 x 15 in stone with an iron lode along its north wall: breaking the heart names it, pays a lump,
     * opens the two-blow window on its blocks, and only once; its roof would come down, but not over building or a
     * Hearth; brought down, the roof falls.
     */
    private static void lodeHeart(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 24, 0));
        for (int dx = -9; dx <= 9; dx++) for (int dz = -11; dz <= 9; dz++) for (int dy = -1; dy <= 7; dy++) {
            boolean inside = Math.abs(dx) <= 7 && Math.abs(dz) <= 7 && dy >= 0 && dy <= 5;
            level.setBlock(o.offset(dx, dy, dz), inside ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
        }
        LongArrayList cells = new LongArrayList();
        BlockPos heart = o.offset(0, 2, -8);
        cells.add(heart.asLong());
        for (int dx = -6; dx <= 6; dx++) for (int dy = 1; dy <= 3; dy++) if (dx != 0 || dy != 2) cells.add(o.offset(dx, dy, -8).asLong());
        Lode l = LodePlans.handmade(Ore.IRON, cells.toLongArray());
        LodePlans.EXTRA.add(l);
        try {
            for (long c : l.cells()) level.setBlock(BlockPos.of(c), c == l.heart() ? Ore.IRON.heart().defaultBlockState() : Blocks.IRON_ORE.defaultBlockState(), 2);
            DelveData data = DelveData.get(level);
            data.forget(l.id(), heart);
            var live = AwakenTests.survivalPlayer(h, "delver", new BlockPos(0, 25, 2));
            ServerPlayer p = live.player();
            Progression.awaken(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            double before = Progression.xpLedger(p).of(Progression.XpSource.HOME);

            h.assertTrue(dev.awaken.delve.api.Lodes.heart(level, heart).isPresent(), "the API knows the heart");
            h.assertTrue(p.gameMode.destroyBlock(heart), "the heart breaks");
            h.assertTrue(data.heartBroken(l.id()), "the world remembers the heart gave");
            h.assertTrue(Progression.profile(p).counter("awaken:lodes_opened") == 1, "one lode opened");
            h.assertTrue(Progression.xpLedger(p).of(Progression.XpSource.HOME) > before, "the heart pays its lump");
            BlockPos cell = o.offset(3, 2, -8);
            h.assertTrue(Veins.inWindow(p, cell), "for 30 s its blocks fall in two blows");
            float hard = level.getBlockState(cell).getDestroySpeed(level, cell);
            h.assertTrue(Math.abs(Veins.twoBlows(hard) / hard / 30F * Veins.BLOW_TICKS * Veins.BLOWS - 1F) < 1e-4, "two blows of four ticks");
            h.assertTrue(!Veins.inWindow(p, o.offset(0, 2, 0)), "not a block outside it");
            // only once, for everyone
            level.setBlock(heart, Ore.IRON.heart().defaultBlockState(), 2);
            p.gameMode.destroyBlock(heart);
            h.assertTrue(Progression.profile(p).counter("awaken:lodes_opened") == 1, "a heart gives once");

            // the heavy strike: from two blocks off the wall, on the glint, three blocks bored along the aim, the wear on top
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            p.getFoodData().setFoodLevel(20);
            p.teleportTo(level, o.getX() - 3.5, o.getY() + 0.2, o.getZ() - 5.0, Set.of(), 180F, 12F, true);
            int wear = p.getMainHandItem().getDamageValue();
            var bored = HeavyStrikes.release(p, HeavyStrikes.GLINT, true);
            h.assertTrue(bored.size() == HeavyStrikes.DEPTH, "the heavy strike bores three: " + bored);
            for (BlockPos b : bored) h.assertTrue(level.getBlockState(b).isAir(), "bored " + b.toShortString());
            h.assertTrue(bored.get(1).getZ() == bored.get(0).getZ() - 1 && bored.get(2).getZ() == bored.get(0).getZ() - 2, "along the aim, into the wall");
            h.assertTrue(p.getMainHandItem().getDamageValue() - wear >= HeavyStrikes.DEPTH * (1 + HeavyStrikes.EXTRA_WEAR_GLINT), "it wears the pick harder");
            h.assertTrue(HeavyStrikes.release(p, HeavyStrikes.GLINT, true).isEmpty(), "and needs a breath before the next");

            // a cave-in: never over building, never in a Hearth; otherwise the roof comes down after its warning
            CaveIns.clear();
            long[] roof = CaveIns.roof(level, heart);
            h.assertTrue(roof.length >= CaveIns.MIN_ROOF, "a big unsupported chamber has a roof to fall: " + roof.length);
            data.built(BlockPos.of(roof[0]));
            h.assertTrue(CaveIns.roof(level, heart).length == 0, "a roof someone built under never caves in");
            data.forget(l.id(), heart);
            HearthHooks.Lookup was = HearthHooks.installed();
            try {
                HearthHooks.install(new HearthHooks.Lookup() {
                    @Override public Optional<BlockPos> nearest(ServerLevel lv, BlockPos pos, int radius) { return Optional.of(heart); }
                    @Override public Optional<BlockPos> hearthOf(ServerLevel lv, UUID owner) { return Optional.empty(); }
                    @Override public boolean inside(ServerLevel lv, BlockPos pos) { return true; }
                });
                h.assertTrue(CaveIns.roof(level, heart).length == 0, "never inside a Hearth");
            } finally {
                HearthHooks.install(was);
            }
            h.assertTrue(CaveIns.consider(level, heart, p, true) && CaveIns.pending() == 1, "it comes down: warned first");
            BlockPos first = BlockPos.of(roof[0]);
            h.assertTrue(!level.getBlockState(first).isAir(), "not yet: dust first");
            live.leave();
            h.succeedWhen(() -> h.assertTrue(level.getBlockState(first).isAir(), "after the warning the roof lets go"));
        } finally {
            LodePlans.EXTRA.remove(l);
        }
    }

    // ---- hide from the hunt, metal from the deep ----------------------------------------------------------------------

    private static void tierMetals(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // the metals themselves
        Object[][] metals = {{"D", Items.IRON_INGOT}, {"C", Items.DIAMOND}, {"B", DelveContent.DEEPGLASS_SHARD.get()},
                {"A", Items.NETHERITE_SCRAP}, {"S", DelveContent.STARMETAL_INGOT.get()}};
        for (Object[] m : metals) {
            TagKey<Item> tag = DelveContent.tierMetal((String) m[0]);
            var set = BuiltInRegistries.ITEM.get(tag).orElseThrow();
            h.assertTrue(set.size() == 1 && ((Item) m[1]).builtInRegistryHolder().is(tag), "tier " + m[0] + "'s metal is exactly " + m[1]);
        }
        // every gear piece from D up needs its metal with nothing to stand in; foci need gold; Veilwoven needs amethyst
        for (HunterTier tier : HunterTier.values()) {
            for (Piece piece : Piece.values()) {
                ShapedRecipe r = shaped(level, GearItems.get(tier, piece));
                h.assertTrue(r != null, "recipe for " + tier.itemId(piece));
                boolean metal = metalSlot(r, tier.metalTag());
                h.assertTrue(tier == HunterTier.E ? tier.metalTag() == null : metal, tier.itemId(piece) + (tier == HunterTier.E ? " (E stays hide and bone)" : " needs its metal"));
                if (piece == Piece.FOCUS && tier != HunterTier.E) h.assertTrue(only(r, Items.GOLD_INGOT), tier.itemId(piece) + " needs gold");
                if (tier == HunterTier.B) h.assertTrue(only(r, Items.AMETHYST_SHARD), tier.itemId(piece) + " needs amethyst");
            }
        }
        // every forged weapon from D up the same (the Lord-forged only drop)
        int forged = 0;
        for (ArmsData.Weapon w : ArmsData.WEAPONS) {
            ShapedRecipe r = shaped(level, BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id(w.id())));
            if (r == null) continue;
            HunterTier tier = HunterTier.valueOf(w.tier());
            if (tier == HunterTier.E) continue;
            h.assertTrue(metalSlot(r, tier.metalTag()), w.id() + " needs its tier's metal");
            forged++;
        }
        h.assertTrue(forged >= 75, "the forged weapons D-S were checked: " + forged);
        // Gatewrought matches diamond: 20 armour, 2.0 toughness a piece
        double armour = 0;
        for (Piece piece : Piece.ARMOUR) armour += attr(GearItems.stack(HunterTier.C, piece), Attributes.ARMOR, piece.slot());
        h.assertTrue(armour == 20, "Gatewrought is 20 armour: " + armour);
        h.assertTrue(attr(GearItems.stack(HunterTier.C, Piece.CHESTPLATE), Attributes.ARMOR_TOUGHNESS, EquipmentSlot.CHEST) == 2.0, "and 2.0 toughness");
        // Blueprints in lapis; the Lands' crystals and Deepglass as catalysts
        h.assertTrue(ArmsForge.BLUEPRINT_LAPIS == 1, "a Blueprint takes a lapis");
        String[] want = {"HEAT:sunshard", "FROST:frostspar", "GRAVITY:fault_seam", "CHARGE:copper_ingot", "SPIRIT:deepglass_shard"};
        for (String s : want) {
            String[] kv = s.split(":");
            Item it = ArmsForge.catalystItem(Forces.Force.valueOf(kv[0]));
            h.assertTrue(it != Items.AIR && BuiltInRegistries.ITEM.getKey(it).getPath().equals(kv[1]), kv[0] + "'s catalyst is " + kv[1]);
        }
        // gates pay no raw ore beyond their theme's flavour (the Bell Crypt's is bone)
        Set<Item> ore = Set.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.NETHERITE_SCRAP,
                Items.RAW_IRON, Items.RAW_GOLD, Items.COPPER_INGOT);
        RandomSource rnd = RandomSource.create(7);
        for (int i = 0; i < 300; i++) {
            for (ItemStack s : dev.awaken.gates.run.Loot.cache(dev.awaken.progression.Rank.A, dev.awaken.gates.content.Theme.BELL_CRYPT, rnd, true, 1.0)) {
                h.assertTrue(!ore.contains(s.getItem()), "a gate cache paid raw ore: " + s);
            }
        }
        // starmetal smelts, bell bronze casts a bell
        h.assertTrue(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, dev.awaken.Awaken.id("starmetal_ingot_from_smelting"))).isPresent(), "starmetal smelts");
        h.assertTrue(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, dev.awaken.Awaken.id("bell_from_bell_bronze"))).isPresent(), "a bell from bell bronze");
        h.succeed();
    }

    private static ShapedRecipe shaped(ServerLevel level, Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).map(RecipeHolder::value)
                .filter(r -> r instanceof ShapedRecipe).map(r -> (ShapedRecipe) r).orElse(null);
    }

    /** A slot that takes the tier's metal and nothing else. */
    @SuppressWarnings("deprecation")
    private static boolean metalSlot(ShapedRecipe r, TagKey<Item> metal) {
        if (metal == null) return false;
        for (Optional<Ingredient> slot : r.getIngredients()) {
            if (slot.isEmpty()) continue;
            List<Holder<Item>> items = slot.get().items().toList();
            if (!items.isEmpty() && items.stream().allMatch(i -> i.is(metal))) return true;
        }
        return false;
    }

    /** A slot that takes exactly this item. */
    @SuppressWarnings("deprecation")
    private static boolean only(ShapedRecipe r, Item item) {
        for (Optional<Ingredient> slot : r.getIngredients()) {
            if (slot.isEmpty()) continue;
            List<Holder<Item>> items = slot.get().items().toList();
            if (items.size() == 1 && items.getFirst().value() == item) return true;
        }
        return false;
    }

    private static double attr(ItemStack s, Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, EquipmentSlot slot) {
        double sum = 0;
        for (var e : s.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().value() == a.value() && e.slot().test(slot)) sum += e.modifier().amount();
        }
        return sum;
    }

    // ---- the Tuning Fork ----------------------------------------------------------------------------------------------

    /**
     * Tuned on an ore, struck on stone: it names a block of the nearest lode of that ore within 48 (from the plan, no
     * scan), and nothing for a lode further off. Struck high in the sky, so the world's own lodes are far below.
     */
    private static void tuningFork(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos struck = h.absolutePos(BlockPos.ZERO).atY(240);
        LongArrayList near = new LongArrayList(), far = new LongArrayList();
        for (int i = 0; i < 20; i++) near.add(struck.offset(30 + i, 4, 10).asLong());
        for (int i = 0; i < 20; i++) far.add(struck.offset(-70 - i, 0, 0).asLong());
        Lode a = LodePlans.handmade(Ore.IRON, near.toLongArray()), b = LodePlans.handmade(Ore.GOLD, far.toLongArray());
        LodePlans.EXTRA.add(a);
        LodePlans.EXTRA.add(b);
        try {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            ItemStack fork = new ItemStack(DelveContent.TUNING_FORK.get());
            BlockPos ore = h.absolutePos(new BlockPos(1, 1, 1));
            level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, fork);
            fork.useOn(new net.minecraft.world.item.context.UseOnContext(p, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ore), net.minecraft.core.Direction.UP, ore, false)));
            h.assertTrue(TuningForkItem.tuned(fork) == Ore.IRON, "touched to iron ore, it takes iron's note");
            Optional<BlockPos> t = TuningForkItem.target(level, struck, Ore.IRON);
            h.assertTrue(t.isPresent() && a.holds(t.get().asLong()), "it sings toward the iron lode 30 off: " + t);
            h.assertTrue(t.get().equals(struck.offset(30, 4, 10)), "toward its nearest block: " + t.get());
            h.assertTrue(TuningForkItem.target(level, struck, Ore.GOLD).isEmpty(), "a gold lode 70 off is out of its reach");
            h.assertTrue(TuningForkItem.target(level, struck, Ore.DIAMOND).isEmpty(), "nothing of an ore with no lode near");
            h.assertTrue(TuningForkItem.pitch(Ore.DEEPGLASS) > TuningForkItem.pitch(Ore.COAL), "each ore its own note");
        } finally {
            LodePlans.EXTRA.remove(a);
            LodePlans.EXTRA.remove(b);
        }
        h.succeed();
    }

    // ---- light held in a hand ---------------------------------------------------------------------------------------

    /**
     * Any held block that gives light lights round its bearer at its own level, in its kind's colour: a torch's warm
     * flame reaching 10.5, a soul torch blue, a redstone torch red, a sea lantern cool and steady; stone, a stick, the
     * Ember and the miner's lamp (their own light) give none; the brighter hand carries.
     */
    private static void heldLight(GameTestHelper h) {
        HeldLight.Kind torch = HeldLight.of(new ItemStack(Items.TORCH));
        h.assertTrue(torch.emission() == 14 && torch.rgb() == HeldLight.FLAME && torch.flame(), "a torch: 14, a warm flame: " + torch);
        h.assertTrue(Math.abs(torch.reach() - 10.5F) < 1e-4F, "a torch reaches 10.5: " + torch.reach());
        HeldLight.Kind soul = HeldLight.of(new ItemStack(Items.SOUL_TORCH)), red = HeldLight.of(new ItemStack(Items.REDSTONE_TORCH));
        h.assertTrue(soul.emission() == 10 && soul.rgb() == HeldLight.SOUL, "a soul torch: 10, soul blue: " + soul);
        h.assertTrue(red.emission() == 7 && red.rgb() == HeldLight.REDSTONE, "a redstone torch: 7, red: " + red);
        HeldLight.Kind lantern = HeldLight.of(new ItemStack(Items.LANTERN)), sea = HeldLight.of(new ItemStack(Items.SEA_LANTERN));
        h.assertTrue(lantern.emission() == 15 && lantern.rgb() == HeldLight.FLAME && lantern.reach() == HeldLight.MAX_REACH, "a lantern: 15, a flame: " + lantern);
        h.assertTrue(sea.emission() == 15 && sea.rgb() == HeldLight.SEA && !sea.flame(), "a sea lantern: cool and steady: " + sea);
        h.assertTrue(HeldLight.of(new ItemStack(Items.SOUL_LANTERN)).rgb() == HeldLight.SOUL, "a soul lantern is blue");
        h.assertTrue(HeldLight.of(new ItemStack(Items.GLOWSTONE)).emission() == 15, "glowstone gives 15");
        h.assertTrue(HeldLight.of(new ItemStack(Items.JACK_O_LANTERN)).flame(), "a jack o'lantern is a flame");
        for (Item none : new Item[]{Items.STONE, Items.STICK, Items.CANDLE, DelveContent.MINERS_LAMP.get(),
                BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id("hearth_ember"))}) {
            h.assertTrue(HeldLight.of(new ItemStack(none)) == HeldLight.NONE, "no held light from " + none);
        }
        h.assertTrue(HeldLight.of(ItemStack.EMPTY) == HeldLight.NONE, "nothing in hand, no light");
        h.assertTrue(HeldLight.offhandWins(new ItemStack(Items.REDSTONE_TORCH), new ItemStack(Items.LANTERN)), "the brighter hand carries");
        h.assertTrue(!HeldLight.offhandWins(new ItemStack(Items.TORCH), new ItemStack(Items.TORCH)), "the main hand on a tie");
        h.succeed();
    }

    // ---- ore XP by rarity and depth ---------------------------------------------------------------------------------

    /**
     * A diamond pays a dozen coals; ores under y 0 have their own budget (a shaft full of coal doesn't wear the deep
     * out); the first of each ore is a discovery; the Mining craft levels at its own pace, times the craft_rate perk.
     */
    private static void oreXp(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertTrue(ProgressionModule.OreXp.of(Blocks.DIAMOND_ORE.defaultBlockState()) == ProgressionModule.OreXp.DIAMOND
                && ProgressionModule.OreXp.of(Blocks.DEEPSLATE_COAL_ORE.defaultBlockState()) == ProgressionModule.OreXp.COAL
                && ProgressionModule.OreXp.of(DelveContent.DEEPGLASS.get().defaultBlockState()) == ProgressionModule.OreXp.DEEPGLASS
                && ProgressionModule.OreXp.of(Ore.IRON.heart().defaultBlockState()) == null, "ores are told apart (a heart pays its own lump)");
        h.assertTrue(ProgressionModule.OreXp.DIAMOND.weight == 12 * ProgressionModule.OreXp.COAL.weight, "a diamond is a dozen coals");
        var live = AwakenTests.survivalPlayer(h, "miner", new BlockPos(0, 1, 0));
        ServerPlayer p = live.player();
        Progression.awaken(p);
        BlockPos at = h.absolutePos(new BlockPos(0, 1, 0));
        double coal = mine(p, level, at.atY(20), Blocks.COAL_ORE.defaultBlockState());
        double diamond = mine(p, level, at.atY(10), Blocks.DIAMOND_ORE.defaultBlockState());
        h.assertTrue(diamond > coal * 9, "a diamond pays far more than coal: " + diamond + " vs " + coal);
        h.assertTrue(Progression.profile(p).counter("awaken:discovered/ore/coal") == 1
                && Progression.profile(p).counter("awaken:discovered/ore/diamond") == 1, "the first of each ore is a discovery");
        // wear the shallow budget right down with coal...
        for (int i = 0; i < 150; i++) mine(p, level, at.atY(20), Blocks.COAL_ORE.defaultBlockState());
        double worn = mine(p, level, at.atY(10), Blocks.DIAMOND_ORE.defaultBlockState());
        // ...a diamond below y 0 still pays from the deep's own
        double deep = mine(p, level, at.atY(-30), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
        h.assertTrue(deep > worn * 2.5, "the deep has its own budget: " + deep + " against a worn " + worn);
        // the Mining craft at its own pace, whatever the budget, and the craft_rate perk on top
        double plain = Crafts.gain(p, Craft.MINING, 10, 1);
        h.assertTrue(Math.abs(plain - 10) < 1e-6, "mining's craft XP isn't cut by a worn budget: " + plain);
        RATED.add(p.getUUID());
        try {
            double rated = Crafts.gain(p, Craft.MINING, 10, 1);
            h.assertTrue(Math.abs(rated - 15) < 1e-6, "awaken:craft_rate +50% makes it 15: " + rated);
        } finally {
            RATED.remove(p.getUUID());
            live.leave();
        }
        h.succeed();
    }

    /** One ore mined (the break event, as the game fires it): the Hunter XP it paid, in the ledger's units. */
    private static double mine(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState s) {
        double before = Progression.xpLedger(p).of(Progression.XpSource.HOME);
        NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, s, p));
        return Progression.xpLedger(p).of(Progression.XpSource.HOME) - before;
    }
}
