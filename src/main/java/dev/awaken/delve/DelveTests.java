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
        AwakenTests.register("delve_true_strike", 40, DelveTests::trueStrike);
        AwakenTests.register("delve_strike_rules", 20, DelveTests::strikeRules);
        AwakenTests.register("delve_lodes_air", 100, DelveTests::lodesAir);
        AwakenTests.register("delve_thinned_ores", 20, DelveTests::thinnedOres);
        AwakenTests.register("delve_waking_pick", 20, DelveTests::wakingPick);
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
     * The Delver's Pick (SPEC §34.4): the pick in the stone gives a player their own pick, once while they carry it (the
     * stone stays for the next), and counts the pull; asleep it mines as a stone pick; its hum finds the nearest lode
     * block within 16 of the eye and nothing at 20.
     */
    @SuppressWarnings("removal")
    private static void wakingPick(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos stone = h.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(stone, DelveContent.WAKING_PICK_STONE.get().defaultBlockState(), 2);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.assertTrue(WakingPick.pull(p, stone), "the first pull gives the pick");
        h.assertTrue(WakingPick.carries(p), "the player carries it");
        h.assertFalse(WakingPick.pull(p, stone), "no second pick while you carry one");
        h.assertTrue(level.getBlockState(stone).is(DelveContent.WAKING_PICK_STONE.get()), "the pick in the stone stays");
        h.assertTrue(dev.awaken.progression.Progression.profile(p).counter(WakingPick.PULLED) == 1, "the pull is counted");
        ItemStack pick = new ItemStack(DelveContent.WAKING_PICK.get());
        h.assertTrue(pick.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState()), "asleep it takes iron, as stone does");
        h.assertFalse(pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()), "and not diamond");
        BlockPos eye = h.absolutePos(BlockPos.ZERO).atY(240);
        LongArrayList near = new LongArrayList();
        for (int i = 0; i < 20; i++) near.add(eye.offset(10 + i, 0, 0).asLong());
        Lode a = LodePlans.handmade(Ore.COPPER, near.toLongArray());
        LodePlans.EXTRA.add(a);
        try {
            var v = WakingPick.nearest(level, eye);
            h.assertTrue(v.isPresent() && v.get().ore() == Ore.COPPER && v.get().nearest().equals(eye.offset(10, 0, 0)), "it hums toward copper 10 off: " + v);
            h.assertTrue(WakingPick.nearest(level, eye.offset(-10, 0, 0)).isEmpty(), "nothing when the lode is 20 off");
        } finally {
            LodePlans.EXTRA.remove(a);
        }
        h.succeed();
    }

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

    // ---- the strike (SPEC §34.3) --------------------------------------------------------------------------------------

    /**
     * True strikes are the server's call: looking at an iron ore's seam from in front, the bite is true (the block's
     * mining speed steps up by the rule, the seam moves); aimed at the face's far corner it isn't; a claim with the wrong
     * count or too soon after the last is refused; stone takes none.
     */
    private static void trueStrike(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos ore = h.absolutePos(new BlockPos(1, 2, 1));
        for (int dz = 0; dz <= 4; dz++) for (int dy = -1; dy <= 1; dy++) level.setBlock(ore.offset(0, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 2);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        p.setPos(ore.getX() + 0.5, ore.getY() - 1.1, ore.getZ() + 3.2);
        net.minecraft.core.Direction face = net.minecraft.core.Direction.SOUTH;
        float before = level.getBlockState(ore).getDestroyProgress(p, level, ore);
        // off the seam: the face's corner farthest from it
        net.minecraft.world.phys.Vec3 mid = TrueStrikes.middle(ore, face, 0);
        double cu = mid.x - ore.getX() < 0.5 ? 0.92 : 0.08, cv = mid.y - ore.getY() < 0.5 ? 0.92 : 0.08;
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, TrueStrikes.onFace(ore, face, cu, cv));
        h.assertFalse(TrueStrikes.accept(p, ore, face, 0), "a bite at the face's far corner is not true");
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mid);
        h.assertFalse(TrueStrikes.accept(p, ore, face, 1), "a claim with the wrong count is refused");
        h.assertTrue(TrueStrikes.accept(p, ore, face, 0), "a bite on the seam is true");
        h.assertTrue(TrueStrikes.trues(p, ore) == 1, "the server counts it");
        float after = level.getBlockState(ore).getDestroyProgress(p, level, ore);
        h.assertTrue(Math.abs(after / before - TrueStrikes.speed(1)) < 1e-3, "the block gives faster by the rule: " + before + " -> " + after);
        h.assertTrue(TrueStrikes.speed(10) == 2F, "never more than twice as fast");
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, TrueStrikes.middle(ore, face, 1));
        h.assertFalse(TrueStrikes.accept(p, ore, face, 1), "not again within a bite of the last");
        h.assertTrue(TrueStrikes.landedTrue(p, ore), "a break now would have landed true");
        h.assertFalse(TrueStrikes.middle(ore, face, 0).equals(TrueStrikes.middle(ore, face, 1)), "the seam moves after a true strike");
        level.setBlock(ore, Blocks.STONE.defaultBlockState(), 2);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, TrueStrikes.middle(ore, face, 0));
        h.assertFalse(TrueStrikes.accept(p, ore, face, 0), "stone takes no true strikes");
        for (int i = 0; i < 200; i++) {
            double[] sm = TrueStrikes.seam(ore.asLong() + i * 977L, i % 6, i % 5);
            h.assertTrue(sm[0] >= 0.22 && sm[0] <= 0.78 && sm[1] >= 0.22 && sm[1] <= 0.78, "the seam keeps off the face's edges");
        }
        h.succeed();
    }

    /**
     * The strike's particles: it draws the rock, ore and loose-ground families (vanilla's ores by name, our hearts and
     * Deepglass) and nothing else; a bite and a break throw what SPEC §34.3 says at each particle setting, none at
     * minimal; one break's chips and a last blow's streaks fit under the caps.
     */
    private static void strikeRules(GameTestHelper h) {
        for (var b : new net.minecraft.world.level.block.Block[]{Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.ANDESITE, Blocks.COBBLESTONE})
            h.assertTrue(StrikeBlocks.family(b) == StrikeBlocks.Family.ROCK, "rock: " + b);
        for (var b : new net.minecraft.world.level.block.Block[]{Blocks.IRON_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.NETHER_GOLD_ORE,
                Ore.IRON.heart(), DelveContent.DEEPGLASS.get()})
            h.assertTrue(StrikeBlocks.family(b) == StrikeBlocks.Family.ORE, "ore: " + b);
        for (var b : new net.minecraft.world.level.block.Block[]{Blocks.DIRT, Blocks.GRAVEL, Blocks.SAND, Blocks.GRASS_BLOCK})
            h.assertTrue(StrikeBlocks.family(b) == StrikeBlocks.Family.LOOSE, "loose: " + b);
        for (var b : new net.minecraft.world.level.block.Block[]{Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.STONE_BRICKS, Blocks.TORCH, Blocks.AIR,
                DelveContent.PITON.get()})
            h.assertFalse(StrikeBlocks.owns(b), "not ours: " + b);
        RandomSource r = RandomSource.create(5);
        for (int i = 0; i < 300; i++) {
            int bite = StrikeBlocks.biteChips(2, r), brk = StrikeBlocks.breakChips(2, r);
            h.assertTrue(bite >= 2 && bite <= 4 && brk >= 8 && brk <= 14, "chips: a bite 2-4, a break 8-14: " + bite + ", " + brk);
            int st = StrikeBlocks.streaks(false, false, 2, r), last = StrikeBlocks.streaks(true, false, 2, r);
            h.assertTrue(st >= 2 && st <= 6 && last >= 6 && last <= 10, "streaks: a bite 2-6, the last 6-10: " + st + ", " + last);
            h.assertTrue(StrikeBlocks.streaks(true, true, 2, r) <= StrikeBlocks.MAX_STREAKS && brk <= StrikeBlocks.MAX_CHIPS, "under the caps");
            h.assertTrue(StrikeBlocks.biteChips(1, r) <= bite + 2 && StrikeBlocks.breakChips(1, r) <= 7, "fewer when decreased");
        }
        h.assertTrue(StrikeBlocks.biteChips(0, r) == 0 && StrikeBlocks.breakChips(0, r) == 0 && StrikeBlocks.streaks(true, true, 0, r) == 0,
                "nothing at minimal");
        h.succeed();
    }

    // ---- metal lives in the caves (SPEC §34.2) ------------------------------------------------------------------------

    /**
     * On the vanilla overworld's own noise (a fixed seed, beside the test server's flat world): the wall lodes of a few
     * regions are laid against cave air (every heart in the rock within three of air you could stand in), a good share of
     * their blocks face open air, and most of them found a wall; the same seed plans them the same way twice.
     */
    private static void lodesAir(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        long seed = 3405691582L;
        var ra = level.registryAccess();
        var settings = ra.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var preset = ra.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(preset), settings);
        var rs = net.minecraft.world.level.levelgen.RandomState.create(ra.lookupOrThrow(Registries.NOISE), seed, settings.value());
        var height = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.level.NoiseColumn> cache = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        LodePlans.Columns cols = (x, z) -> cache.computeIfAbsent(((long) x << 32) | (z & 0xFFFFFFFFL), k -> gen.getBaseColumn(x, z, height, rs));
        int lodes = 0, cells = 0, facing = 0, iron = 0;
        int planned = 0;
        for (int rx = 0; rx < 2; rx++) for (int rz = 0; rz < 2; rz++) {
            List<Lode> walls = LodePlans.walls(seed, rx, rz, cols);
            planned += 7;
            List<Lode> again = LodePlans.walls(seed, rx, rz, cols);
            h.assertTrue(walls.size() == again.size() && (walls.isEmpty() || walls.getFirst().heart() == again.getFirst().heart()), "the same plan twice");
            for (Lode l : walls) {
                lodes++;
                if (l.ore() == Ore.IRON) iron++;
                BlockPos hp = l.heartPos();
                boolean nearAir = false;
                for (int dx = -3; dx <= 3 && !nearAir; dx++) for (int dz = -3; dz <= 3 && !nearAir; dz++)
                    if (cols.at(hp.getX() + dx, hp.getZ() + dz).getBlock(hp.getY()).isAir()) nearAir = true;
                h.assertTrue(nearAir, "a wall lode's heart is within 3 of cave air: " + hp);
                for (long c : l.cells()) {
                    int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
                    if (cols.at(x, z).getBlock(y).isAir()) continue;   // cut by the cave: never written
                    cells++;
                    if (cols.at(x + 1, z).getBlock(y).isAir() || cols.at(x - 1, z).getBlock(y).isAir() || cols.at(x, z + 1).getBlock(y).isAir()
                            || cols.at(x, z - 1).getBlock(y).isAir() || cols.at(x, z).getBlock(y + 1).isAir() || cols.at(x, z).getBlock(y - 1).isAir()) facing++;
                }
            }
        }
        double share = cells == 0 ? 0 : facing / (double) cells;
        dev.awaken.Awaken.LOG.info("[delve] wall lodes: {} of {} laid ({} iron), {} of {} blocks face air ({})", lodes, planned, iron, facing, cells,
                String.format(java.util.Locale.ROOT, "%.2f", share));
        h.assertTrue(lodes >= planned / 2, "most wall lodes find a cave: " + lodes + " of " + planned);
        h.assertTrue(iron >= 6, "at least six iron wall lodes in four regions: " + iron);
        h.assertTrue(share >= 0.25, "a good share of their blocks face the cave: " + share);
        h.succeed();
    }

    /**
     * Vanilla's scattered iron, copper, coal and gold are thinned in new land: each has a copy at about 35% (a third, by
     * count or a 1-in-3 chance), the modifier is loaded, and it swaps them in every overworld biome; diamonds stay.
     */
    private static void thinnedOres(GameTestHelper h) {
        var ra = h.getLevel().registryAccess();
        var placed = ra.lookupOrThrow(Registries.PLACED_FEATURE);
        for (String n : ThinOres.THINNED) {
            var copy = placed.get(ThinOres.thinned(n));
            var orig = placed.get(ThinOres.vanilla(n));
            h.assertTrue(copy.isPresent() && orig.isPresent(), "a thinned copy of " + n);
            h.assertTrue(copy.get().value().feature().equals(orig.get().value().feature()), "the same ore: " + n);
            int a = count(orig.get().value()), b = count(copy.get().value());
            boolean rarity = copy.get().value().placement().stream().anyMatch(m -> m instanceof net.minecraft.world.level.levelgen.placement.RarityFilter);
            h.assertTrue(rarity || (b >= 1 && b <= Math.ceil(a * ThinOres.SHARE) && b < a), n + ": " + a + " -> " + b);
        }
        var mods = ra.lookupOrThrow(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        h.assertTrue(mods.get(ResourceKey.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIERS, dev.awaken.Awaken.id("delve_thin_ores"))).isPresent(),
                "the thinning modifier is loaded");
        h.assertFalse(ThinOres.THINNED.contains("ore_diamond"), "diamonds are not thinned");
        h.succeed();
    }

    /** A placed feature's fixed count (1 if none). */
    private static int count(net.minecraft.world.level.levelgen.placement.PlacedFeature f) {
        for (var m : f.placement()) {
            if (m instanceof net.minecraft.world.level.levelgen.placement.CountPlacement c && c.count() instanceof net.minecraft.util.valueproviders.ConstantInt k) return k.value();
        }
        return 1;
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
