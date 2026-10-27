package dev.awaken.gear;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Curve;
import dev.awaken.progression.LevelRoad;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Hunter gear GameTests: registry and hooks, recipes, unlocks, stats order, set bonuses, combat, tempering, Now. */
final class GearTests {
    private GearTests() {}

    /** What each biome family's P1 creatures drop (docs/modules/fauna.md and their loot tables), essence included. */
    static final Map<String, List<Item>> FAMILY_DROPS = new LinkedHashMap<>();
    /** Common vanilla bits any first-hour player has. */
    static final List<Item> COMMON = List.of(Items.STICK, Items.FLINT);

    static {
        FAMILY_DROPS.put("Greenwild", List.of(Items.LEATHER, Items.STRING, Items.MOSS_BLOCK, Items.TURTLE_SCUTE, Items.GLOWSTONE_DUST,
                Items.BONE_MEAL, Items.PINK_PETALS));
        FAMILY_DROPS.put("Sunscorch", List.of(Items.PHANTOM_MEMBRANE, Items.LEATHER, Items.STRING, Items.QUARTZ));
        FAMILY_DROPS.put("Frostwild", List.of(Items.LEATHER, Items.MUTTON, Items.BONE, Items.SNOWBALL, Items.FEATHER));
    }

    static void register() {
        AwakenTests.register("gear_registry_and_hooks", 20, GearTests::registryAndHooks);
        AwakenTests.register("gear_recipes_resolve", 20, GearTests::recipesResolve);
        AwakenTests.register("gear_e_tier_from_first_hour_drops", 20, GearTests::firstHour);
        AwakenTests.register("gear_recipes_unlock", 60, GearTests::recipesUnlock);
        AwakenTests.register("gear_tier_stats_ordered", 20, GearTests::statsOrdered);
        AwakenTests.register("gear_set_bonuses_apply_and_remove", 20, GearTests::setBonuses);
        AwakenTests.registerIsolated("gear_combat_wards_primes_flicker", 40, GearTests::combat);
        AwakenTests.registerIsolated("gear_focus_in_world", 40, GearTests::focusInWorld);
        AwakenTests.register("gear_tempering_works", 20, GearTests::tempering);
        AwakenTests.register("gear_now_suggests_next_piece", 20, GearTests::now);
        AwakenTests.register("gear_late_set_bonuses", 20, GearTests::lateBonuses);
        AwakenTests.registerIsolated("gear_lordscale_overflow", 40, GearTests::overflow);
        AwakenTests.register("gear_silhouettes_valid", 20, GearTests::silhouettes);
        AwakenTests.register("gear_premium_spec", 20, GearTests::premiumSpec);
        AwakenTests.register("gear_premium_hood", 20, GearTests::premiumHood);
    }

    // ---- registry, tags, recipes ------------------------------------------------------------------------------------

    private static void registryAndHooks(GameTestHelper h) {
        h.assertTrue(GearItems.all().size() == HunterTier.values().length * Piece.values().length, "7 pieces per tier");
        for (HunterTier tier : HunterTier.values()) {
            for (TagKey<Item> tag : List.of(tier.hideTag(), tier.coreTag(), tier.bindTag())) {
                h.assertTrue(BuiltInRegistries.ITEM.get(tag).map(s -> s.size() > 0).orElse(false), "hook tag has items: " + tag.location());
            }
            for (Piece piece : Piece.values()) {
                Identifier id = Awaken.id(tier.itemId(piece));
                h.assertTrue(BuiltInRegistries.ITEM.getValue(id) == GearItems.get(tier, piece), "registered " + id);
            }
        }
        // the hooks hold the materials the tiers are designed around (other modules' items: a rename shows up here)
        expectIn(h, HunterTier.D.coreTag(), "briar_tusk", "wyrm_scale", "antler_tine", "crypt_bronze", "bell_clapper");
        expectIn(h, HunterTier.C.coreTag(), "colossus_gear", "serpent_scale", "archivist_quill", "broker_lantern");
        expectIn(h, HunterTier.B.coreTag(), "glacier_heart", "mirror_shard_crown", "zero_core", "sleeper_eyelid");
        expectIn(h, HunterTier.B.bindTag(), "dream_residue");
        // A: the B- and A-rank Lords and the deep guardians; S: the Herald, the Dreaming and the Hollow Court
        expectIn(h, HunterTier.A.coreTag(), "leviathan_fin", "herald_halo", "furnace_engine_core", "moon_scale", "architect_key");
        expectIn(h, HunterTier.A.hideTag(), "precursor_alloy");
        expectIn(h, HunterTier.S.coreTag(), "herald_halo", "architect_key", "regent_shadow", "mirror_heart");
        expectIn(h, HunterTier.S.bindTag(), "dream_residue");
        h.succeed();
    }

    private static void expectIn(GameTestHelper h, TagKey<Item> tag, String... ids) {
        for (String id : ids) {
            Item item = BuiltInRegistries.ITEM.getValue(Awaken.id(id));
            h.assertTrue(item != null && item != Items.AIR && item.builtInRegistryHolder().is(tag), tag.location() + " holds awaken:" + id);
        }
    }

    static Optional<ShapedRecipe> recipe(ServerLevel level, Item item) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, BuiltInRegistries.ITEM.getKey(item));
        return level.getServer().getRecipeManager().byKey(key).map(RecipeHolder::value)
                .filter(r -> r instanceof ShapedRecipe).map(r -> (ShapedRecipe) r);
    }

    /** Every piece has a shaped crafting recipe named after it, needs essence, and its first-item fill crafts it. */
    private static void recipesResolve(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Item shard = CoreApi.ESSENCE_SHARD.get();
        for (HunterTier tier : HunterTier.values()) {
            for (Piece piece : Piece.values()) {
                Item item = GearItems.get(tier, piece);
                ShapedRecipe r = recipe(level, item).orElse(null);
                h.assertTrue(r != null, "shaped recipe for " + tier.itemId(piece));
                h.assertTrue(!r.placementInfo().isImpossibleToPlace(), "placeable: " + tier.itemId(piece));
                boolean essence = false;
                List<ItemStack> fill = new ArrayList<>();
                for (Optional<Ingredient> slot : r.getIngredients()) {
                    if (slot.isEmpty()) {
                        fill.add(ItemStack.EMPTY);
                        continue;
                    }
                    Ingredient ing = slot.get();
                    h.assertTrue(!ing.isEmpty(), "no empty ingredient in " + tier.itemId(piece));
                    if (ing.acceptsItem(shard.builtInRegistryHolder())) essence = true;
                    @SuppressWarnings("deprecation")
                    Item first = ing.items().findFirst().map(Holder::value).orElse(Items.AIR);
                    fill.add(new ItemStack(first));
                }
                h.assertTrue(essence, tier.itemId(piece) + " needs essence");
                assertCrafts(h, level, r, fill, item, "first items");
            }
        }
        h.succeed();
    }

    private static void assertCrafts(GameTestHelper h, ServerLevel level, ShapedRecipe r, List<ItemStack> fill, Item expected, String what) {
        CraftingInput input = CraftingInput.of(r.getWidth(), r.getHeight(), fill);
        var found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        String name = BuiltInRegistries.ITEM.getKey(expected).getPath();
        h.assertTrue(found.isPresent(), name + " crafts from " + what);
        h.assertTrue(found.get().id().identifier().equals(BuiltInRegistries.ITEM.getKey(expected)), name + ": no other recipe takes over, got " + found.get().id());
        h.assertTrue(found.get().value().assemble(input).is(expected), name + " comes out");
    }

    /** E-tier is craftable from each family's first-hour creature drops plus essence, sticks and flint, nothing else. */
    private static void firstHour(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int hide = 0, shards = 0;
        for (Piece piece : Piece.values()) {
            Item item = GearItems.get(HunterTier.E, piece);
            ShapedRecipe r = recipe(level, item).orElseThrow();
            for (var family : FAMILY_DROPS.entrySet()) {
                List<Item> pool = new ArrayList<>(family.getValue());
                pool.add(CoreApi.ESSENCE_SHARD.get());
                pool.addAll(COMMON);
                List<ItemStack> fill = new ArrayList<>();
                for (Optional<Ingredient> slot : r.getIngredients()) {
                    if (slot.isEmpty()) {
                        fill.add(ItemStack.EMPTY);
                        continue;
                    }
                    Item use = null;
                    for (Item it : pool) if (slot.get().acceptsItem(it.builtInRegistryHolder())) { use = it; break; }
                    h.assertTrue(use != null, family.getKey() + " has nothing for a slot of " + HunterTier.E.itemId(piece));
                    fill.add(new ItemStack(use));
                }
                assertCrafts(h, level, r, fill, item, family.getKey() + " drops");
            }
            for (Optional<Ingredient> slot : r.getIngredients()) {
                if (slot.isEmpty()) continue;
                if (slot.get().acceptsItem(Items.LEATHER.builtInRegistryHolder())) hide++;
                if (slot.get().acceptsItem(CoreApi.ESSENCE_SHARD.get().builtInRegistryHolder())) shards++;
            }
        }
        // a whole first set stays within a first hour or two of hunting: about a vanilla leather suit's worth of hide
        h.assertTrue(hide <= 16, "the E kit needs " + hide + " hide");
        h.assertTrue(shards <= 10, "the E kit needs " + shards + " essence");
        h.succeed();
    }

    /** Holding an ingredient unlocks the recipe (progression's rule, through the real player tick); the level road too. */
    private static void recipesUnlock(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        sp.getInventory().add(new ItemStack(Items.LEATHER));
        ResourceKey<Recipe<?>> vest = key(HunterTier.E, Piece.CHESTPLATE);
        for (int i = 0; i < 41 && !sp.getRecipeBook().contains(vest); i++) {
            sp.tickCount++;
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(sp));
        }
        h.assertTrue(sp.getRecipeBook().contains(vest), "holding leather unlocks the Wildhide Vest");
        // the level road points at each tier and puts its whole recipe set in the book
        Map<HunterTier, Integer> want = Map.of(HunterTier.E, 3, HunterTier.D, 13, HunterTier.C, 22, HunterTier.B, 42,
                HunterTier.A, 67, HunterTier.S, 97);
        for (HunterTier tier : HunterTier.values()) {
            String id = "awaken:hunter_gear_" + tier.name().toLowerCase();
            LevelRoad.Step step = LevelRoad.steps().stream().filter(s -> s.id().toString().equals(id)).findFirst().orElse(null);
            h.assertTrue(step != null, "level-road step " + id);
            h.assertTrue(step.def().level() == want.get(tier), id + " at level " + want.get(tier) + ", got " + step.def().level());
            List<String> ids = step.def().grants().stream().flatMap(g -> g.ids().stream()).toList();
            for (Piece piece : Piece.values()) h.assertTrue(ids.contains("awaken:" + tier.itemId(piece)), id + " grants " + tier.itemId(piece));
        }
        Profile p = Progression.profile(sp);
        while (p.level() < 13) Progression.grantXp(sp, Curve.xpToNext(p.level()) + 1, Progression.XpSource.COMMAND);
        for (Piece piece : Piece.values()) {
            h.assertTrue(sp.getRecipeBook().contains(key(HunterTier.E, piece)), "level 3 put E " + piece.id + " in the book");
            h.assertTrue(sp.getRecipeBook().contains(key(HunterTier.D, piece)), "level 13 put D " + piece.id + " in the book");
            h.assertTrue(!sp.getRecipeBook().contains(key(HunterTier.C, piece)), "C waits for level 22");
        }
        h.succeed();
    }

    private static ResourceKey<Recipe<?>> key(HunterTier tier, Piece piece) {
        return ResourceKey.create(Registries.RECIPE, Awaken.id(tier.itemId(piece)));
    }

    // ---- stats ------------------------------------------------------------------------------------------------------

    static double attr(ItemStack s, Holder<Attribute> a, EquipmentSlot slot) {
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().value() == a.value() && e.slot().test(slot)) sum += e.modifier().amount();
        }
        return sum;
    }

    private static double armourTotal(HunterTier tier) {
        double sum = 0;
        for (Piece piece : Piece.ARMOUR) sum += attr(GearItems.stack(tier, piece), Attributes.ARMOR, piece.slot());
        return sum;
    }

    private static double vanillaArmour(Item... items) {
        double sum = 0;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < items.length; i++) sum += attr(new ItemStack(items[i]), Attributes.ARMOR, slots[i]);
        return sum;
    }

    private static double melee(Item item) {
        return attr(new ItemStack(item), Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND);
    }

    /** Tiers climb in every stat, and each sits on its vanilla equivalent (E stone/copper, D iron, C diamond, B netherite). */
    private static void statsOrdered(GameTestHelper h) {
        HunterTier[] t = HunterTier.values();
        for (int i = 1; i < t.length; i++) {
            HunterTier lo = t[i - 1], hi = t[i];
            h.assertTrue(armourTotal(hi) > armourTotal(lo), hi + " armour above " + lo);
            for (Piece piece : Piece.values()) {
                ItemStack a = GearItems.stack(lo, piece), b = GearItems.stack(hi, piece);
                h.assertTrue(b.getMaxDamage() > a.getMaxDamage(), hi + " " + piece.id + " lasts longer");
                if (piece.isArmour()) {
                    h.assertTrue(attr(b, Attributes.ARMOR, piece.slot()) >= attr(a, Attributes.ARMOR, piece.slot()), hi + " " + piece.id + " armour");
                    h.assertTrue(attr(b, Attributes.ARMOR_TOUGHNESS, piece.slot()) >= attr(a, Attributes.ARMOR_TOUGHNESS, piece.slot()), hi + " " + piece.id + " toughness");
                } else {
                    h.assertTrue(attr(b, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND) > attr(a, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND), hi + " " + piece.id + " hits harder");
                }
            }
            h.assertTrue(hi.focus.powerBonus() > lo.focus.powerBonus(), hi + " focus boosts powers more");
            h.assertTrue(hi.mattock.speed() > lo.mattock.speed(), hi + " mattock digs faster");
        }
        // vanilla anchors
        double leather = vanillaArmour(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        double iron = vanillaArmour(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        double diamond = vanillaArmour(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        h.assertTrue(armourTotal(HunterTier.E) > leather && armourTotal(HunterTier.E) < iron, "E armour between leather and iron");
        h.assertTrue(armourTotal(HunterTier.D) == iron, "D armour is iron's");
        h.assertTrue(armourTotal(HunterTier.C) > iron && armourTotal(HunterTier.C) <= diamond, "C armour near diamond");
        h.assertTrue(armourTotal(HunterTier.B) >= diamond, "B armour at diamond or better");
        h.assertTrue(attr(GearItems.stack(HunterTier.B, Piece.CHESTPLATE), Attributes.ARMOR_TOUGHNESS, EquipmentSlot.CHEST)
                <= attr(new ItemStack(Items.NETHERITE_CHESTPLATE), Attributes.ARMOR_TOUGHNESS, EquipmentSlot.CHEST), "B toughness at most netherite's");
        Item[] swords = {Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
        for (int i = 0; i < swords.length; i++) {
            h.assertTrue(melee(GearItems.get(t[i], Piece.BLADE)) == melee(swords[i]), t[i] + " blade hits like " + swords[i]);
        }
        // A and S go past netherite: more armour, toughness from netherite's up, harder blades
        double netherite = vanillaArmour(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        h.assertTrue(armourTotal(HunterTier.A) > netherite && armourTotal(HunterTier.S) > armourTotal(HunterTier.A), "A and S above netherite");
        h.assertTrue(attr(GearItems.stack(HunterTier.A, Piece.CHESTPLATE), Attributes.ARMOR_TOUGHNESS, EquipmentSlot.CHEST)
                >= attr(new ItemStack(Items.NETHERITE_CHESTPLATE), Attributes.ARMOR_TOUGHNESS, EquipmentSlot.CHEST), "A toughness at least netherite's");
        h.assertTrue(melee(GearItems.get(HunterTier.A, Piece.BLADE)) > melee(Items.NETHERITE_SWORD), "the Warblade hits harder than netherite");
        h.assertTrue(armourTotal(HunterTier.S) <= 30, "S stays under the armour attribute's 30 cap");
        // mattocks: what each tier may mine, and that one head does stone, wood and earth
        assertMines(h, HunterTier.E, Blocks.IRON_ORE, true);
        assertMines(h, HunterTier.E, Blocks.DIAMOND_ORE, false);
        assertMines(h, HunterTier.D, Blocks.DIAMOND_ORE, true);
        assertMines(h, HunterTier.D, Blocks.OBSIDIAN, false);
        assertMines(h, HunterTier.C, Blocks.OBSIDIAN, true);
        assertMines(h, HunterTier.B, Blocks.ANCIENT_DEBRIS, true);
        assertMines(h, HunterTier.A, Blocks.ANCIENT_DEBRIS, true);
        assertMines(h, HunterTier.S, Blocks.OBSIDIAN, true);
        for (HunterTier tier : t) {
            Tool tool = GearItems.stack(tier, Piece.MATTOCK).get(DataComponents.TOOL);
            h.assertTrue(tool != null, tier + " mattock is a tool");
            for (BlockState s : List.of(Blocks.OAK_LOG.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState())) {
                h.assertTrue(tool.getMiningSpeed(s) == tier.mattock.speed(), tier + " mattock digs " + s.getBlock());
            }
        }
        h.succeed();
    }

    private static void assertMines(GameTestHelper h, HunterTier tier, net.minecraft.world.level.block.Block block, boolean yes) {
        Tool tool = GearItems.stack(tier, Piece.MATTOCK).get(DataComponents.TOOL);
        h.assertTrue(tool != null && tool.isCorrectForDrops(block.defaultBlockState()) == yes,
                tier + " mattock " + (yes ? "mines " : "can't mine ") + block);
    }

    // ---- set bonuses ------------------------------------------------------------------------------------------------

    static void wear(ServerPlayer p, HunterTier tier, int pieces) {
        for (int i = 0; i < 4; i++) p.setItemSlot(Piece.ARMOUR[i].slot(), i < pieces ? GearItems.stack(tier, Piece.ARMOUR[i]) : ItemStack.EMPTY);
    }

    private static double mod(ServerPlayer p, Holder<Attribute> a, Identifier id) {
        var inst = p.getAttribute(a);
        var m = inst == null ? null : inst.getModifier(id);
        return m == null ? 0 : m.amount();
    }

    private static void setBonuses(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        wear(sp, HunterTier.E, 2);
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.SNEAKING_SPEED, SetBonus.SNEAK_ID) == SetBonus.SNEAK_HALF, "two Wildhide pieces: half bonus");
        wear(sp, HunterTier.E, 4);
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.SNEAKING_SPEED, SetBonus.SNEAK_ID) == SetBonus.SNEAK_FULL, "four: full bonus");
        wear(sp, HunterTier.E, 0);
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.SNEAKING_SPEED, SetBonus.SNEAK_ID) == 0, "taken off: gone");

        double base = sp.getMaxHealth();
        wear(sp, HunterTier.D, 4);
        SetBonus.refresh(sp);
        h.assertTrue(Math.abs(sp.getMaxHealth() - (base + SetBonus.HEALTH_FULL)) < 1e-4, "Beastbound: +2 hearts, got " + sp.getMaxHealth());
        sp.setHealth(sp.getMaxHealth() * 0.3f);
        SetBonus.refresh(sp);
        h.assertTrue(SetBonus.cornered(sp), "below half health: Cornered");
        h.assertTrue(mod(sp, Attributes.ATTACK_DAMAGE, SetBonus.CORNERED_ID) == SetBonus.corneredBonus(5), "Cornered damage at STR 5");
        sp.setHealth(sp.getMaxHealth());
        SetBonus.refresh(sp);
        h.assertTrue(!SetBonus.cornered(sp) && mod(sp, Attributes.ATTACK_DAMAGE, SetBonus.CORNERED_ID) == 0, "healed: Cornered off");
        // tiers mix: two Wildhide and two Beastbound give both halves
        sp.setItemSlot(EquipmentSlot.HEAD, GearItems.stack(HunterTier.E, Piece.HELMET));
        sp.setItemSlot(EquipmentSlot.CHEST, GearItems.stack(HunterTier.E, Piece.CHESTPLATE));
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.SNEAKING_SPEED, SetBonus.SNEAK_ID) == SetBonus.SNEAK_HALF
                && mod(sp, Attributes.MAX_HEALTH, SetBonus.HEALTH_ID) == SetBonus.HEALTH_HALF, "mixed: both half bonuses");
        wear(sp, HunterTier.D, 0);
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.MAX_HEALTH, SetBonus.HEALTH_ID) == 0 && sp.getMaxHealth() == base, "all off: health back to " + base);
        // the numbers behind the twists
        h.assertTrue(SetBonus.senseRadius(5) == 14 && SetBonus.senseRadius(100) == 32, "sense radius 12 + PER/2, at most 32");
        h.assertTrue(SetBonus.corneredBonus(5) == 0.15 && SetBonus.corneredBonus(100) == 0.30, "Cornered 15% to 30%");
        h.assertTrue(SetBonus.flickerChance(5) == 0.10 && SetBonus.flickerChance(100) == 0.35, "Flicker 10% to 35%");
        h.assertTrue(SetBonus.forceWard(1) == 0 && SetBonus.forceWard(2) == SetBonus.WARD_HALF && SetBonus.forceWard(4) == SetBonus.WARD_FULL, "ward steps");
        h.succeed();
    }

    // ---- combat (the listener itself, then once through the real damage path) ----------------------------------------

    private static float hit(net.minecraft.world.entity.LivingEntity victim, DamageSource src, float amount, boolean[] cancelled) {
        LivingIncomingDamageEvent e = new LivingIncomingDamageEvent(victim, new DamageContainer(src, amount));
        GearCombat.onIncomingDamage(e);
        if (cancelled != null) cancelled[0] = e.isCanceled();
        return e.getAmount();
    }

    private static Pig pig(GameTestHelper h, int x) {
        Pig pig = h.spawn(EntityTypes.PIG, new BlockPos(x, 1, 0));
        pig.setNoAi(true);
        return pig;
    }

    private static void combat(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        var sources = level.damageSources();
        // Gatewrought wards off Force damage
        wear(sp, HunterTier.C, 2);
        h.assertTrue(Math.abs(hit(sp, sources.source(Forces.HEAT), 10, null) - 8.5f) < 1e-4, "two pieces: Force damage -15%");
        wear(sp, HunterTier.C, 4);
        h.assertTrue(Math.abs(hit(sp, sources.source(Forces.FROST), 10, null) - 7.5f) < 1e-4, "four: -25%");
        h.assertTrue(hit(sp, sources.generic(), 10, null) == 10, "plain damage untouched");
        // a power hit primes the next melee hit, which carries the Force
        Pig a = pig(h, 1), b = pig(h, 3);
        hit(a, sources.source(Forces.FROST, sp), 4, null);
        h.assertTrue(GearCombat.primed(sp), "a Frost power hit primed the blade");
        float strike = hit(b, sources.playerAttack(sp), 4, null);
        h.assertTrue(Math.abs(strike - 4 * (1 + SetBonus.PRIME_BONUS)) < 1e-4, "the primed strike hits +35%, got " + strike);
        h.assertTrue(b.hasEffect(MobEffects.SLOWNESS), "and Frost slows");
        h.assertTrue(!GearCombat.primed(sp) && hit(b, sources.playerAttack(sp), 4, null) == 4, "spent: the next hit is plain");
        // Veilwoven: Spirit ward and the Veil Flicker
        wear(sp, HunterTier.B, 2);
        h.assertTrue(Math.abs(hit(sp, sources.source(Forces.SPIRIT), 10, null) - 7f) < 1e-4, "two Veilwoven: Spirit damage -30%");
        wear(sp, HunterTier.B, 4);
        var before = GearCombat.roll;
        try {
            GearCombat.roll = p -> 0.0;
            boolean[] cancelled = {false};
            sp.setHealth(sp.getMaxHealth());
            hit(sp, sources.generic(), 6, cancelled);
            h.assertTrue(!cancelled[0], "healthy: no Flicker");
            sp.setHealth(sp.getMaxHealth() * 0.3f);
            hit(sp, sources.generic(), 6, cancelled);
            h.assertTrue(cancelled[0], "below 40%: the hit passes through");
            hit(sp, sources.generic(), 6, cancelled);
            h.assertTrue(!cancelled[0], "then 8 s of cooldown");
            GearCombat.roll = p -> 0.99;
            GearState.of(sp).flickerReadyAt = 0;
            hit(sp, sources.generic(), 6, cancelled);
            h.assertTrue(!cancelled[0], "a missed roll lets the hit land");
        } finally {
            GearCombat.roll = before;
        }
        h.succeed();
    }

    /** End to end through vanilla's damage path: the listener is on the bus and a focus lifts power damage. */
    private static void focusInWorld(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Pig plain = pig(h, 1), focused = pig(h, 3);
        DamageSource power = level.damageSources().source(Forces.KINETIC, sp);
        plain.hurtServer(level, power, 4);
        sp.setItemSlot(EquipmentSlot.OFFHAND, GearItems.stack(HunterTier.E, Piece.FOCUS));
        dev.awaken.powers.Powers.fillEnergy(sp);
        dev.awaken.powers.Casting.drainExtra(sp, 40);
        float energyBefore = dev.awaken.powers.Powers.energy(sp);
        focused.hurtServer(level, power, 4);
        float lostPlain = plain.getMaxHealth() - plain.getHealth();
        float lostFocused = focused.getMaxHealth() - focused.getHealth();
        h.assertTrue(Math.abs(lostPlain - 4) < 1e-3, "no focus: 4, got " + lostPlain);
        h.assertTrue(Math.abs(lostFocused - 4 * (1 + HunterTier.E.focus.powerBonus())) < 1e-3, "Wildhide Charm: +6%, got " + lostFocused);
        // the focus gives a little energy back for a power hit, at most once a second
        float afterHit = dev.awaken.powers.Powers.energy(sp);
        h.assertTrue(Math.abs(afterHit - energyBefore - GearCombat.focusEnergy(GearItems.stack(HunterTier.E, Piece.FOCUS))) < 1e-3,
                "a Wildhide Charm restores 0.5 energy: " + energyBefore + " -> " + afterHit);
        plain.hurtServer(level, power, 4);
        h.assertTrue(Math.abs(dev.awaken.powers.Powers.energy(sp) - afterHit) < 1e-3, "not twice in the same second");
        h.assertTrue(GearCombat.focusEnergy(GearItems.stack(HunterTier.B, Piece.FOCUS)) == 2f, "a Veilwoven Censer restores 2");
        h.succeed();
    }

    // ---- the Forge ---------------------------------------------------------------------------------------------------

    /** The Hearth Forge tempers every piece, the bonus lands on top of the tier's stats, and set bonuses still count. */
    private static void tempering(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        for (HunterTier tier : HunterTier.values()) {
            for (Piece piece : Piece.values()) {
                ItemStack s = GearItems.stack(tier, piece);
                h.assertTrue(dev.awaken.hearth.Upgrades.isTemperable(s), "the Forge takes " + tier.itemId(piece));
                ItemStack fresh = s.copy();
                dev.awaken.hearth.Upgrades.applyTemper(s, 2);
                h.assertTrue(dev.awaken.hearth.Upgrades.temperOf(s) == 2 && GearCombat.temper(s) == 2, "temper II on " + tier.itemId(piece));
                if (piece.isArmour()) {
                    EquipmentSlot slot = piece.slot();
                    h.assertTrue(Math.abs(attr(s, Attributes.ARMOR, slot) - attr(fresh, Attributes.ARMOR, slot) - 1.0) < 1e-6, "temper II: +1 armour");
                    h.assertTrue(Math.abs(attr(s, Attributes.ARMOR_TOUGHNESS, slot) - attr(fresh, Attributes.ARMOR_TOUGHNESS, slot) - 0.5) < 1e-6, "+0.5 toughness");
                } else {
                    h.assertTrue(Math.abs(attr(s, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND)
                            - attr(fresh, Attributes.ATTACK_DAMAGE, EquipmentSlot.MAINHAND) - 2.0) < 1e-6, "temper II: +2 damage on " + tier.itemId(piece));
                }
                if (piece == Piece.FOCUS) {
                    h.assertTrue(Math.abs(GearCombat.focusBonus(s) - tier.focus.powerBonus() - 2 * GearCombat.FOCUS_PER_TEMPER) < 1e-9, "a tempered focus boosts powers more");
                }
                if (piece.isArmour()) sp.setItemSlot(piece.slot(), s);
            }
            SetBonus.refresh(sp);
            h.assertTrue(SetBonus.count(sp, tier) == 4, "tempered " + tier + " pieces still make a set");
        }
        wear(sp, HunterTier.E, 0);
        SetBonus.refresh(sp);
        h.succeed();
    }

    // ---- the late sets ------------------------------------------------------------------------------------------------

    /** Lordscale's ward, Sovereign's Measure (attributes, power hits, the stat gear reads) and Unbowed's rule. */
    private static void lateBonuses(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        var sources = level.damageSources();
        wear(sp, HunterTier.A, 1);
        h.assertTrue(hit(sp, sources.source(Forces.GRAVITY), 10, null) == 10, "one Lordscale piece: no ward");
        wear(sp, HunterTier.A, 2);
        h.assertTrue(Math.abs(hit(sp, sources.source(Forces.GRAVITY), 10, null) - 8f) < 1e-4, "two: Gravity -20%");
        h.assertTrue(Math.abs(hit(sp, sources.source(Forces.CHARGE), 10, null) - 8f) < 1e-4, "and Charge -20%");
        h.assertTrue(hit(sp, sources.source(Forces.HEAT), 10, null) == 10, "Heat untouched");
        // Sovereign's Measure on the highest stat
        wear(sp, HunterTier.A, 0);
        Progression.addStat(sp, dev.awaken.progression.Stat.STR, 10);
        wear(sp, HunterTier.S, 2);
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.ATTACK_DAMAGE, SetBonus.MEASURE_ID) == 0 && SetBonus.stat(sp, dev.awaken.progression.Stat.STR) == 15,
                "two pieces: no Measure yet");
        wear(sp, HunterTier.S, 4);
        SetBonus.refresh(sp);
        h.assertTrue(SetBonus.highest(sp) == dev.awaken.progression.Stat.STR, "STR is the highest");
        h.assertTrue(SetBonus.stat(sp, dev.awaken.progression.Stat.STR) == 20 && SetBonus.stat(sp, dev.awaken.progression.Stat.AGI) == 5,
                "STR 15 counts as 20, the rest as they are");
        h.assertTrue(Math.abs(mod(sp, Attributes.ATTACK_DAMAGE, SetBonus.MEASURE_ID) - 5 * 0.025) < 1e-9, "+5 STR worth of attack");
        h.assertTrue(Math.abs(SetBonus.measurePower(sp) - 0.075) < 1e-9, "powers +0.75% a point above 5");
        Pig pig = pig(h, 2);
        float powered = hit(pig, sources.source(Forces.KINETIC, sp), 10, null);
        h.assertTrue(Math.abs(powered - 10.75f) < 1e-3, "a power hit +7.5%, got " + powered);
        Progression.addStat(sp, dev.awaken.progression.Stat.VIT, 20);
        SetBonus.refresh(sp);
        h.assertTrue(SetBonus.highest(sp) == dev.awaken.progression.Stat.VIT && mod(sp, Attributes.ATTACK_DAMAGE, SetBonus.MEASURE_ID) == 0
                && Math.abs(mod(sp, Attributes.MAX_HEALTH, SetBonus.MEASURE_ID) - 10 * 0.6) < 1e-9, "VIT 25 highest: +10 VIT worth of health");
        wear(sp, HunterTier.S, 0);
        SetBonus.refresh(sp);
        h.assertTrue(mod(sp, Attributes.MAX_HEALTH, SetBonus.MEASURE_ID) == 0, "taken off: gone");
        // Unbowed: a gate below your rank can't Fracture you (two pieces)
        h.assertTrue(SetBonus.waivesFracture(2, 3, 2) && !SetBonus.waivesFracture(2, 3, 3) && !SetBonus.waivesFracture(1, 3, 0)
                && !SetBonus.waivesFracture(4, 3, -1), "Unbowed only for gates below your rank, with two pieces");
        h.assertTrue(SetBonus.lines(HunterTier.A).size() == 2 && SetBonus.lines(HunterTier.S).size() == 2, "every set says what it does");
        h.succeed();
    }

    /** Lordscale, four pieces: every fifth power hit of one Force bursts around the target, half the hit, foes only. */
    private static void overflow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        wear(sp, HunterTier.A, 4);
        var sources = level.damageSources();
        net.minecraft.world.entity.monster.zombie.Zombie target = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        net.minecraft.world.entity.monster.zombie.Zombie near = h.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        Pig pet = pig(h, 1);
        for (var z : List.of(target, near)) {
            z.setNoAi(true);
            z.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
            var armour = z.getAttribute(Attributes.ARMOR);
            if (armour != null) armour.setBaseValue(0); // a zombie's own 2 armour would shave the burst
        }
        float before = near.getHealth(), petBefore = pet.getHealth();
        for (int i = 0; i < 4; i++) hit(target, sources.source(Forces.FROST, sp), 4, null);
        h.assertTrue(near.getHealth() == before, "four hits: no burst yet");
        hit(target, sources.source(Forces.FROST, sp), 4, null);
        h.assertTrue(Math.abs(before - near.getHealth() - 2f) < 1e-3, "the fifth Frost hit bursts: half of 4 to the zombie nearby, got "
                + (before - near.getHealth()));
        h.assertTrue(near.hasEffect(MobEffects.SLOWNESS), "and the burst carries Frost");
        h.assertTrue(pet.getHealth() == petBefore, "an animal beside it is never caught");
        // Forces count apart: Heat hits don't spend Frost's count
        for (int i = 0; i < 4; i++) hit(target, sources.source(Forces.HEAT, sp), 4, null);
        float mid = near.getHealth();
        hit(target, sources.source(Forces.FROST, sp), 4, null);
        h.assertTrue(near.getHealth() == mid, "one Frost hit after a burst starts a new count");
        wear(sp, HunterTier.A, 0);
        h.succeed();
    }

    /** The late sets' extra geometry: the client builds the armour from this file, so it must be whole and fit its texture. */
    private static void silhouettes(GameTestHelper h) {
        for (HunterTier tier : List.of(HunterTier.A, HunterTier.S)) {
            String file = "/assets/awaken/" + Silhouette.path(tier);
            try (var in = GearTests.class.getResourceAsStream(file)) {
                h.assertTrue(in != null, file + " is on the classpath");
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                Silhouette spec = Silhouette.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
                List<String> bad = spec.problems();
                h.assertTrue(bad.isEmpty(), tier.setName + ": " + String.join("; ", bad));
                h.assertTrue(spec.parts().size() >= 10, tier.setName + " adds real geometry (" + spec.parts().size() + " parts)");
                for (String slot : List.of("head", "chest", "feet")) {
                    h.assertTrue(spec.parts().stream().anyMatch(p -> p.slot().equals(slot)), tier.setName + " changes the " + slot + " silhouette");
                }
                try (var png = GearTests.class.getResourceAsStream("/assets/awaken/" + Silhouette.texture(tier))) {
                    h.assertTrue(png != null, "silhouette texture for " + tier.setName);
                    var img = javax.imageio.ImageIO.read(png);
                    h.assertTrue(img.getWidth() == spec.texture().get(0) && img.getHeight() == spec.texture().get(1), "texture size matches the spec");
                }
            } catch (java.io.IOException e) {
                h.fail(file + ": " + e);
            }
        }
        h.succeed();
    }

    /** The premium test's spec (pack/armour-premium-test): it reads, is whole texels, is inside the albedo half, and covers all four slots. */
    private static void premiumSpec(GameTestHelper h) {
        String file = "/assets/awaken/" + dev.awaken.gear.premium.PremiumSpec.PATH;
        try (var in = GearTests.class.getResourceAsStream(file)) {
            h.assertTrue(in != null, file + " is on the classpath");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            var spec = dev.awaken.gear.premium.PremiumSpec.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            List<String> bad = spec.problems();
            h.assertTrue(bad.isEmpty(), String.join("; ", bad));
            for (String slot : dev.awaken.gear.premium.PremiumSpec.SLOTS) {
                h.assertTrue(spec.parts().stream().anyMatch(p -> p.slot().equals(slot)), "the " + slot + " slot has parts");
            }
            for (String png : List.of(dev.awaken.gear.premium.PremiumSpec.TEXTURE, dev.awaken.gear.premium.PremiumSpec.GLOW)) {
                try (var img = GearTests.class.getResourceAsStream("/assets/awaken/" + png)) {
                    h.assertTrue(img != null, png + " is on the classpath");
                    var im = javax.imageio.ImageIO.read(img);
                    h.assertTrue(im.getWidth() == spec.texture().get(0) && im.getHeight() == spec.texture().get(1), png + " matches the spec's size");
                }
            }
        } catch (java.io.IOException e) {
            h.fail(file + ": " + e);
        }
        h.succeed();
    }

    /** The hero hood (pack/armour-hero-piece): the mesh reads and is sound, and its atlas is the size the mesh's uvs assume. */
    private static void premiumHood(GameTestHelper h) {
        String file = "/assets/awaken/" + dev.awaken.gear.premium.HoodMesh.PATH;
        try (var in = GearTests.class.getResourceAsStream(file)) {
            h.assertTrue(in != null, file + " is on the classpath");
            var mesh = dev.awaken.gear.premium.HoodMesh.read(in);
            List<String> bad = mesh.problems();
            h.assertTrue(bad.isEmpty(), String.join("; ", bad));
            h.assertTrue(mesh.quads() > 2000, "the hood is a real mesh, not a few boxes: " + mesh.quads() + " quads");
            try (var img = GearTests.class.getResourceAsStream("/assets/awaken/" + dev.awaken.gear.premium.HoodMesh.TEXTURE)) {
                h.assertTrue(img != null, "the hood's atlas is on the classpath");
                var im = javax.imageio.ImageIO.read(img);
                h.assertTrue(im.getWidth() == dev.awaken.gear.premium.HoodMesh.ATLAS_W && im.getHeight() == dev.awaken.gear.premium.HoodMesh.ATLAS_H,
                        "the hood's atlas is " + dev.awaken.gear.premium.HoodMesh.ATLAS_W + "x" + dev.awaken.gear.premium.HoodMesh.ATLAS_H);
            }
        } catch (java.io.IOException e) {
            h.fail(file + ": " + e);
        }
        h.succeed();
    }

    // ---- the Now tracker --------------------------------------------------------------------------------------------

    private static void now(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        h.assertTrue(GearNow.next(sp) == null, "nothing to make from nothing");
        sp.getInventory().add(new ItemStack(Items.LEATHER, 5));
        sp.getInventory().add(new ItemStack(Items.STRING, 2));
        sp.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 1));
        Item next = GearNow.next(sp);
        h.assertTrue(next == GearItems.get(HunterTier.E, Piece.CHESTPLATE), "hide, sinew and essence: the vest first, got " + next);
        sp.setItemSlot(EquipmentSlot.CHEST, GearItems.stack(HunterTier.E, Piece.CHESTPLATE));
        next = GearNow.next(sp);
        h.assertTrue(next == GearItems.get(HunterTier.E, Piece.LEGGINGS), "own the vest: the leggings next, got " + next);
        sp.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        next = GearNow.next(sp);
        h.assertTrue(next != GearItems.get(HunterTier.E, Piece.LEGGINGS), "never suggests a step down from diamond");
        var lines = GearNow.suggest(sp);
        h.assertTrue(lines.size() <= 1 && lines.stream().allMatch(s -> s.priority() == GearNow.PRIORITY), "one low-priority line at most");
        h.succeed();
    }
}
