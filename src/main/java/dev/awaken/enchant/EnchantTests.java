package dev.awaken.enchant;

import dev.awaken.armour.Archetype;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourForge;
import dev.awaken.armour.ArmourItems;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.Forged;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.enchant.net.EnchantPayloads;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Enchanting's GameTests (SPEC §45): one per mechanism, prefix {@code enchant_}. */
final class EnchantTests {
    private EnchantTests() {}

    static void register() {
        AwakenTests.register("enchant_forged_sets_take", 20, EnchantTests::forgedSetsTake);
        AwakenTests.register("enchant_rule", 20, EnchantTests::rule);
        AwakenTests.register("enchant_learn", 20, EnchantTests::learn);
        AwakenTests.register("enchant_gate_page", 20, EnchantTests::gatePage);
        AwakenTests.register("enchant_room_depth", 20, EnchantTests::roomDepth);
        AwakenTests.register("enchant_ward_cap", 20, EnchantTests::wardCap);
        AwakenTests.register("enchant_bane_spares_lords", 20, EnchantTests::baneSparesLords);
        AwakenTests.register("enchant_fold", 20, EnchantTests::fold);
        AwakenTests.registerIsolated("enchant_inscribe", 100, EnchantTests::inscribe);
        AwakenTests.registerIsolated("enchant_tasks", 100, EnchantTests::tasks);
    }

    private static Registry<Enchantment> reg(GameTestHelper h) {
        return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    private static Holder<Enchantment> ench(GameTestHelper h, String id) {
        return reg(h).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)));
    }

    private static ItemStack with(ItemStack s, Holder<Enchantment> e, int level) {
        EnchantmentHelper.updateEnchantments(s, m -> m.set(e, level));
        return s;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    /** Every sculpted set's piece is in vanilla's enchantable tags for its slot, so the armour enchantments and Unbreaking fit it. */
    private static void forgedSetsTake(GameTestHelper h) {
        var unbreaking = reg(h).getOrThrow(Enchantments.UNBREAKING);
        var respiration = reg(h).getOrThrow(Enchantments.RESPIRATION);
        var feather = reg(h).getOrThrow(Enchantments.FEATHER_FALLING);
        int n = 0;
        for (ArmourSet set : ArmourSet.values()) {
            if (set.hunter != null || !set.made()) continue;
            for (var type : dev.awaken.armour.ForgeSong.PIECES) {
                Item item = ArmourItems.item(set, type);
                h.assertTrue(item != null, set.id + " " + type + " is an item");
                ItemStack s = new ItemStack(item);
                h.assertTrue(s.is(ItemTags.ARMOR_ENCHANTABLE), s + " is in #enchantable/armor");
                h.assertTrue(s.supportsEnchantment(unbreaking), s + " takes Unbreaking");
                EquipmentSlot slot = type.getSlot();
                if (slot == EquipmentSlot.HEAD) h.assertTrue(s.supportsEnchantment(respiration), s + " takes Respiration");
                if (slot == EquipmentSlot.FEET) h.assertTrue(s.supportsEnchantment(feather), s + " takes Feather Falling");
                if (slot != EquipmentSlot.HEAD) h.assertFalse(s.supportsEnchantment(respiration), s + " takes no Respiration off the head");
                n++;
            }
        }
        h.assertTrue(n == 92, "23 sets of four pieces, got " + n);
        h.succeed();
    }

    /**
     * The one rule: every source tag holds only what can be written (the table's own list empty, mobs only keeping and
     * carrying), every one of the thirty exists and has a way in, none adds damage, armour or an element, and the vanilla
     * enchanting table can't be crafted.
     */
    private static void rule(GameTestHelper h) {
        Registry<Enchantment> reg = reg(h);
        h.assertTrue(Inscriptions.ALL.size() == 30, "thirty enchantments");
        for (int i = 0; i < Inscriptions.ALL.size(); i++) {
            Inscriptions.Entry e = Inscriptions.ALL.get(i);
            h.assertTrue(e.no() == i + 1, e.name() + " is number " + (i + 1));
            h.assertTrue(reg.get(e.key()).isPresent(), e.id() + " exists");
            h.assertTrue(!e.gate().isEmpty() || e.market() || e.books(), e.name() + " has a way in");
            h.assertTrue(Marks.strokes(e.no()) >= 3 && Marks.strokes(e.no()) <= 6, e.name() + "'s mark has 3-6 strokes");
            Enchantment en = reg.getOrThrow(e.key()).value();
            for (var bad : List.<DataComponentType<?>>of(EnchantmentEffectComponents.DAMAGE, EnchantmentEffectComponents.POST_ATTACK,
                    EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK))
                h.assertFalse(en.effects().has(bad), e.name() + " adds no " + bad);
            for (var attr : en.getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
                Holder<Attribute> a = attr.attribute();
                h.assertFalse(a.is(Attributes.ARMOR) || a.is(Attributes.ARMOR_TOUGHNESS) || a.is(Attributes.ATTACK_DAMAGE)
                        || a.is(Attributes.ATTACK_SPEED) || a.is(Attributes.MAX_HEALTH), e.name() + " adds no strength through " + a);
            }
        }
        for (TagKey<Enchantment> tag : List.of(EnchantmentTags.ON_RANDOM_LOOT, EnchantmentTags.TRADEABLE,
                EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT, EnchantmentTags.ON_TRADED_EQUIPMENT, EnchantmentTags.IN_ENCHANTING_TABLE)) {
            HolderSet<Enchantment> set = reg.get(tag).map(x -> (HolderSet<Enchantment>) x).orElse(HolderSet.empty());
            for (Holder<Enchantment> en : set) {
                Inscriptions.Entry e = Inscriptions.of(en);
                h.assertTrue(e != null, en.getRegisteredName() + " in " + tag.location() + " can be written");
                if (tag == EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT || tag == EnchantmentTags.ON_TRADED_EQUIPMENT)
                    h.assertTrue(e.family() == Family.KEEPING || e.family() == Family.CARRYING, en.getRegisteredName() + ": no mob spawns with a ward or a bane");
            }
            if (tag == EnchantmentTags.IN_ENCHANTING_TABLE) h.assertTrue(set.size() == 0, "the vanilla table's list is empty");
        }
        var vanillaTable = ResourceKey.<net.minecraft.world.item.crafting.Recipe<?>>create(Registries.RECIPE, Identifier.parse("minecraft:enchanting_table"));
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(vanillaTable).isEmpty(),
                "the vanilla enchanting table can't be crafted (files: " + h.getLevel().getServer().getResourceManager()
                        .getResourceStack(Identifier.parse("minecraft:recipe/enchanting_table.json")).stream()
                        .map(net.minecraft.server.packs.resources.Resource::sourcePackId).toList() + ")");
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.<net.minecraft.world.item.crafting.Recipe<?>>create(Registries.RECIPE, dev.awaken.Awaken.id("inscribing_table"))).isPresent(),
                "the Inscribing Table can");
        h.succeed();
    }

    /** A page teaches for good and is spent; a known page stays; an old book teaches only what can still be written. */
    private static void learn(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Known.save(p, Known.fresh());
        ItemStack page = Pages.page(ench(h, "awaken:heat_ward"), "From a test");
        h.assertTrue(page.getHoverName().getString().equals("Page of Heat Ward"), "the page is named for its enchantment: " + page.getHoverName().getString());
        h.assertTrue(Pages.read(p, page), "reading a new page teaches it");
        Known k = Known.of(p);
        h.assertTrue(k.knows(Identifier.parse("awaken:heat_ward")) && k.learned(Identifier.parse("awaken:heat_ward")).where().equals("From a test"),
                "known, with where it came from");
        h.assertFalse(Pages.read(p, page), "a page you know teaches nothing (and is kept to give away)");
        ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(reg(h).getOrThrow(Enchantments.SHARPNESS), 3));
        h.assertTrue(Pages.teaches(book).isEmpty(), "an old book of Sharpness can't be learned any more");
        ItemStack mixed = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(reg(h).getOrThrow(Enchantments.SHARPNESS), 2);
        m.set(reg(h).getOrThrow(Enchantments.FEATHER_FALLING), 3);
        mixed.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        List<Inscriptions.Entry> on = Pages.teaches(mixed);
        h.assertTrue(on.size() == 1 && on.getFirst().id().getPath().equals("feather_falling"), "an old book teaches only what can be written: " + on);
        h.assertTrue(Pages.learn(p, on, "From an old book").size() == 1 && Known.of(p).count() == 2, "and it's learned");
        h.succeed();
    }

    /** A Lord's kill gives its kind's page into the pack the first time; a kill by no player gives nothing. */
    private static void gatePage(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        Known.save(p, Known.fresh());
        p.getInventory().clearContent();
        Zombie lord = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        lord.setData(CoreApi.MOB_ROLE, new MobRole(MobRole.Role.BOSS, 5, "awaken:ember_mines", Optional.empty(), "test_gate"));
        Pages.onDeath(new LivingDeathEvent(lord, h.getLevel().damageSources().generic()));
        h.assertTrue(pages(p) == 0, "a Lord felled by no player gives no page");
        Pages.onDeath(new LivingDeathEvent(lord, h.getLevel().damageSources().playerAttack(p)));
        h.assertTrue(pages(p) == 1, "its fighter gets the page in their pack: " + pages(p));
        ItemStack got = firstPage(p);
        h.assertTrue(Pages.teaches(got).getFirst().id().getPath().equals("heat_ward"), "the Ember Mines keep Heat Ward");
        h.assertTrue(Pages.where(got).equals("From the Ember Tyrant, the Lord of the Ember Mines"), "it says where it came from: " + Pages.where(got));
        h.assertTrue(Known.of(p).gatePaid("ember_mines"), "the kind of gate is paid");
        h.assertTrue(Inscriptions.ofGate("awaken:lantern_night_market").id().getPath().equals("looting"), "the night market's theme reads as the Lantern Market");
        h.succeed();
    }

    private static int pages(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).getItem() instanceof PageItem) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static ItemStack firstPage(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).getItem() instanceof PageItem) return p.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }

    /** Room from the forge, depth from the rank, the Study past II, archetype affinity, exclusives, full pieces and the pick. */
    private static void roomDepth(GameTestHelper h) {
        Holder<Enchantment> unbreaking = reg(h).getOrThrow(Enchantments.UNBREAKING);
        ItemStack iron = new ItemStack(Items.IRON_SWORD), diamond = new ItemStack(Items.DIAMOND_SWORD);
        h.assertTrue(EnchantRules.room(iron) == 1, "unforged gear holds one");
        h.assertTrue(EnchantRules.room(new ItemStack(Items.BOW)) == 2 && EnchantRules.room(new ItemStack(Items.FISHING_ROD)) == 2, "bows and rods hold two");
        h.assertTrue(EnchantRules.rank(iron).equals("D") && EnchantRules.rank(diamond).equals("C") && EnchantRules.rank(new ItemStack(Items.LEATHER_BOOTS)).equals("E"),
                "vanilla gear ranks by its material");
        Inscriptions.Entry ub = Inscriptions.of(unbreaking);
        h.assertTrue(EnchantRules.deepest(diamond, unbreaking, ub, true) == 3 && EnchantRules.deepest(diamond, unbreaking, ub, false) == 2,
                "a C piece goes to III in the Study, II outside");
        h.assertTrue("Deeper than II needs your Study.".equals(EnchantRules.why(with(diamond.copy(), unbreaking, 2), unbreaking, 3, false)),
                "and says why outside it");
        h.assertTrue(EnchantRules.deepest(new ItemStack(Items.LEATHER_BOOTS), unbreaking, ub, true) == 1, "an E piece takes I");
        // a forged piece: its quality is its room, its set's rank its depth, its archetype one deeper for its kind
        ArmourSet bulwark = ArmourSet.BELLGUARD;
        h.assertTrue(bulwark.made() && bulwark.rank == Rank.E && bulwark.archetype == Archetype.BULWARK, "Bellguard is a made E-rank Bulwark set");
        ItemStack chest = ArmourForge.make(bulwark, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE);
        chest.set(ArmourContent.FORGED.get(), new Forged(Forged.SCHEMA, bulwark.id, 2, 0, false, false, ""));
        h.assertTrue(EnchantRules.room(chest) == 3, "a Masterwork piece holds three: " + EnchantRules.room(chest));
        Holder<Enchantment> heat = ench(h, "awaken:heat_ward"), frost = ench(h, "awaken:frost_ward");
        h.assertTrue(EnchantRules.deepest(chest, heat, Inscriptions.of(heat), true) == 2, "a Bulwark's ward goes one deeper than E's I");
        h.assertTrue(EnchantRules.deepest(chest, unbreaking, ub, true) == 1, "but not Unbreaking");
        with(chest, heat, 1);
        h.assertTrue("It can't go with Heat Ward.".equals(EnchantRules.why(chest, frost, 1, true)), "two wards can't share a piece: " + EnchantRules.why(chest, frost, 1, true));
        // an old piece over its room says Full
        ItemStack old = with(with(new ItemStack(Items.IRON_CHESTPLATE), reg(h).getOrThrow(Enchantments.PROTECTION), 4), unbreaking, 3);
        h.assertTrue(EnchantRules.roomLine(old).equals("Full"), "an old piece over its room is Full");
        h.assertTrue(EnchantRules.why(old, reg(h).getOrThrow(Enchantments.MENDING), 1, true).startsWith("Full"), "and takes nothing new");
        // found and forged weapons, and the Delver's Pick
        h.assertTrue(EnchantRules.room(new ItemStack(item("awaken:ember_tyrant_axe"))) == 3, "a Lord-forged weapon holds three");
        h.assertTrue(EnchantRules.room(new ItemStack(item("awaken:wildhide_longsword"))) == 2, "our forged weapons hold two until the War Anvil");
        h.assertTrue(EnchantRules.rank(new ItemStack(item("awaken:wildhide_longsword"))).equals("E"), "a weapon's tier is its rank");
        h.assertFalse(EnchantRules.fits(new ItemStack(item("awaken:waking_pick")), reg(h).getOrThrow(Enchantments.EFFICIENCY)), "no Efficiency on the Delver's Pick");
        int[] p = EnchantRules.price(ub, 1, 3);
        h.assertTrue(p[0] == 6 && p[1] == 2, "deepening pays the difference");
        int[] dear = EnchantRules.price(Inscriptions.of(reg(h).getOrThrow(Enchantments.MENDING)), 0, 1);
        h.assertTrue(dear[0] == 10 && dear[1] == 4, "Mending costs 10 levels and 4 lapis");
        h.succeed();
    }

    /** A whole suit of Heat Ward III takes 30% off a creature's Heat hit and 20% off a Lord's, and nothing off melee. */
    private static void wardCap(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Holder<Enchantment> heat = ench(h, "awaken:heat_ward");
        p.setItemSlot(EquipmentSlot.HEAD, with(new ItemStack(Items.IRON_HELMET), heat, 3));
        p.setItemSlot(EquipmentSlot.CHEST, with(new ItemStack(Items.IRON_CHESTPLATE), heat, 3));
        p.setItemSlot(EquipmentSlot.LEGS, with(new ItemStack(Items.IRON_LEGGINGS), heat, 3));
        p.setItemSlot(EquipmentSlot.FEET, with(new ItemStack(Items.IRON_BOOTS), heat, 3));
        h.assertTrue(Math.abs(EnchantEffects.ward(p, Forces.Force.HEAT, false) - 0.30F) < 1e-4, "a suit takes 30% off a creature's Heat");
        h.assertTrue(Math.abs(EnchantEffects.ward(p, Forces.Force.HEAT, true) - 0.20F) < 1e-4, "and 20% off a Lord's");
        h.assertTrue(EnchantEffects.ward(p, Forces.Force.FROST, false) == 0, "and nothing off Frost");
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        var hit = new LivingIncomingDamageEvent(p, new DamageContainer(h.getLevel().damageSources().source(Forces.HEAT, z), 10F));
        EnchantEffects.onIncoming(hit);
        h.assertTrue(Math.abs(hit.getAmount() - 7F) < 1e-3, "a creature's Heat hit of 10 lands as 7: " + hit.getAmount());
        z.setData(CoreApi.MOB_ROLE, new MobRole(MobRole.Role.BOSS, 5, "awaken:ember_mines", Optional.empty(), "test_gate"));
        var lordHit = new LivingIncomingDamageEvent(p, new DamageContainer(h.getLevel().damageSources().source(Forces.HEAT, z), 10F));
        EnchantEffects.onIncoming(lordHit);
        h.assertTrue(Math.abs(lordHit.getAmount() - 8F) < 1e-3, "a Lord's lands as 8: " + lordHit.getAmount());
        var melee = new LivingIncomingDamageEvent(p, new DamageContainer(h.getLevel().damageSources().mobAttack(z), 10F));
        EnchantEffects.onIncoming(melee);
        h.assertTrue(melee.getAmount() == 10F, "its melee is never warded");
        var fire = new LivingIncomingDamageEvent(p, new DamageContainer(h.getLevel().damageSources().inFire(), 10F));
        EnchantEffects.onIncoming(fire);
        h.assertTrue(fire.getAmount() < 10F, "Heat Ward covers fire");
        h.succeed();
    }

    /** Banes bite their kind 8% a level and never a Lord, a named beast, a tamed animal or a player. */
    private static void baneSparesLords(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ItemStack sword = with(new ItemStack(Items.IRON_SWORD), ench(h, "awaken:bane_of_the_dead"), 3);
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        h.assertTrue(Math.abs(EnchantEffects.bane(p, sword, z) - 0.24F) < 1e-4, "Bane of the Dead III bites a zombie 24%");
        h.assertTrue(EnchantEffects.baneNo(p, sword, z) == 10, "the mark it leaves is Bane of the Dead's (SPEC §45.12)");
        for (var f : dev.awaken.core.Forces.Force.values())
            h.assertTrue(Inscriptions.ALL.get(EnchantFx.wardNo(f) - 1).force() == f, "a " + f + " blow flashes its own ward's mark");
        z.setData(CoreApi.MOB_ROLE, new MobRole(MobRole.Role.BOSS, 5, "awaken:bell_crypt", Optional.empty(), "test_gate"));
        h.assertTrue(EnchantEffects.bane(p, sword, z) == 0, "never a Lord");
        Zombie named = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        named.setData(CoreApi.CREATURE_TAG, new CreatureTag(10, CreatureTag.Kind.NONE, false, "Terror of Ashfen"));
        h.assertTrue(EnchantEffects.bane(p, sword, named) == 0, "never a named beast");
        ItemStack beasts = with(new ItemStack(Items.IRON_SWORD), ench(h, "awaken:bane_of_beasts"), 2);
        Cow cow = h.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(3, 1, 1));
        h.assertTrue(Math.abs(EnchantEffects.bane(p, beasts, cow) - 0.16F) < 1e-4, "Bane of Beasts II bites a wild cow 16%");
        Wolf wolf = h.spawnWithNoFreeWill(EntityTypes.WOLF, new BlockPos(1, 1, 3));
        wolf.tame(p);
        h.assertTrue(EnchantEffects.bane(p, beasts, wolf) == 0, "never a tamed animal");
        h.assertTrue(EnchantEffects.bane(p, sword, h.makeMockServerPlayerInLevel()) == 0, "never a player");
        h.succeed();
    }

    /** An old table turns into ours; the anvil takes no books and merges nothing; the grindstone gives back a third. */
    private static void fold(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        h.setBlock(rel, Blocks.ENCHANTING_TABLE);
        Fold.swap(h.getLevel(), h.absolutePos(rel), p);
        h.assertTrue(h.getBlockState(rel).is(EnchantContent.TABLE.get()), "the vanilla table became an Inscribing Table");
        Holder<Enchantment> unbreaking = reg(h).getOrThrow(Enchantments.UNBREAKING), sharp = reg(h).getOrThrow(Enchantments.SHARPNESS);
        ItemStack left = with(new ItemStack(Items.IRON_SWORD), unbreaking, 2);
        ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(sharp, 5));
        AnvilUpdateEvent withBook = new AnvilUpdateEvent(left, book, null, with(left.copy(), sharp, 5), 5, 1, p);
        Fold.onAnvil(withBook);
        h.assertTrue(withBook.isCanceled(), "an enchanted book on the anvil gives nothing");
        ItemStack right = with(new ItemStack(Items.IRON_SWORD), sharp, 3);
        ItemStack merged = with(left.copy(), sharp, 3);
        AnvilUpdateEvent repair = new AnvilUpdateEvent(left, right, null, merged, 3, 1, p);
        Fold.onAnvil(repair);
        ItemEnchantments out = repair.getOutput().getEnchantments();
        h.assertTrue(!repair.isCanceled() && out.getLevel(sharp) == 0 && out.getLevel(unbreaking) == 2, "two swords repair, the left keeps its own: " + out);
        GrindstoneEvent.OnTakeItem take = new GrindstoneEvent.OnTakeItem(ContainerLevelAccess.NULL, p, left, ItemStack.EMPTY, 30);
        Fold.onGrindstoneTake(take);
        h.assertTrue(take.getXp() == 10, "the grindstone gives back a third: " + take.getXp());
        h.succeed();
    }

    /**
     * The table: choosing takes the price, a clean trace writes it and gives a third back and puts it in memory; Esc
     * refunds; a swapped piece ends the session and refunds; a second writer is turned away; a stale session is ignored.
     */
    private static void inscribe(GameTestHelper h) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "enchant-writer", new BlockPos(1, 1, 1));
        AwakenTests.onEnd(h, live::leave);
        ServerPlayer p = live.player();
        BlockPos rel = new BlockPos(2, 1, 2);
        h.setBlock(rel, EnchantContent.TABLE.get());
        InscribingTableBlockEntity be = h.getBlockEntity(rel, InscribingTableBlockEntity.class);
        p.getInventory().clearContent();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_CHESTPLATE));
        p.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, 10));
        p.setExperienceLevels(20);
        Known k = Known.fresh();
        k.learn(Identifier.parse("minecraft:unbreaking"), "test", 0);
        Known.save(p, k);
        Inscribe.open(p, be);
        Inscribe.Session s = Inscribe.of(p);
        h.assertTrue(s != null && p.getUUID().equals(be.writer()), "laying a piece opens a session and the table shows its writer");
        int no = Inscriptions.of(Identifier.parse("minecraft:unbreaking")).no();
        Inscribe.onAct(p, new EnchantPayloads.Act(s.id + 99, EnchantPayloads.CHOOSE, no, 1, 0));
        h.assertTrue(s.ench == null, "a stale session id is ignored");
        Inscribe.onAct(p, new EnchantPayloads.Act(s.id, EnchantPayloads.CHOOSE, no, 1, 0));
        h.assertTrue(s.ench != null && p.experienceLevel == 17 && Inscribe.countLapis(p) == 9, "choosing takes 3 levels and 1 lapis: " + p.experienceLevel);
        for (int i = 0; i < Marks.strokes(no); i++) {
            s.lastStroke -= 20;
            Inscribe.onAct(p, new EnchantPayloads.Act(s.id, EnchantPayloads.STROKE, no, i, 0.95F));
        }
        Inscribe.onAct(p, new EnchantPayloads.Act(s.id, EnchantPayloads.FINISH, no, 0, 0));
        h.assertTrue(p.getMainHandItem().getEnchantments().getLevel(reg(h).getOrThrow(Enchantments.UNBREAKING)) == 1, "Unbreaking I is written");
        h.assertTrue(p.experienceLevel == 18, "a clean hand gives a level back: " + p.experienceLevel);
        h.assertTrue(Known.of(p).inMemory(Identifier.parse("minecraft:unbreaking")) && Known.of(p).written() == 1, "the mark is in memory");
        // a second writer is turned away
        ServerPlayer other = h.makeMockServerPlayerInLevel();
        Inscribe.open(other, be);
        h.assertTrue(Inscribe.of(other) == null, "someone is writing here");
        // Esc refunds a choice not yet written
        Inscribe.onAct(p, new EnchantPayloads.Act(s.id, EnchantPayloads.CHOOSE, no, 2, 0));
        h.assertTrue(p.experienceLevel == 15, "deepening to II pays 3 more levels");
        Inscribe.onAct(p, new EnchantPayloads.Act(s.id, EnchantPayloads.CANCEL, 0, 0, 0));
        h.assertTrue(p.experienceLevel == 18 && Inscribe.countLapis(p) == 9 && Inscribe.of(p) == null && be.writer() == null, "Esc refunds everything and frees the table");
        // a swapped piece ends it and refunds
        Inscribe.open(p, be);
        Inscribe.Session s2 = Inscribe.of(p);
        Inscribe.onAct(p, new EnchantPayloads.Act(s2.id, EnchantPayloads.CHOOSE, no, 2, 0));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HELMET));
        Inscribe.onAct(p, new EnchantPayloads.Act(s2.id, EnchantPayloads.FINISH, no, 0, 0));
        h.assertTrue(Inscribe.of(p) == null && p.experienceLevel == 18 && p.getMainHandItem().getEnchantments().isEmpty(), "the piece left the hand: nothing written, all refunded");
        // not enough levels: refused, nothing taken
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_LEGGINGS));
        p.setExperienceLevels(1);
        Inscribe.open(p, be);
        Inscribe.Session s3 = Inscribe.of(p);
        Inscribe.onAct(p, new EnchantPayloads.Act(s3.id, EnchantPayloads.CHOOSE, no, 1, 0));
        h.assertTrue(s3.ench == null && p.experienceLevel == 1, "too few levels: refused, nothing taken");
        Inscribe.close(p, s3, "");
        h.succeed();
    }

    /** Tasks: reading a page, making the table and the first inscription sit on the main path in turn and leave when done. */
    private static void tasks(GameTestHelper h) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "enchant-tasks", BlockPos.ZERO);
        AwakenTests.onEnd(h, live::leave);
        ServerPlayer p = live.player();
        dev.awaken.progression.Progression.awaken(p);
        p.getInventory().clearContent();
        Known.save(p, Known.fresh());
        h.assertTrue(keys(p).isEmpty(), "nothing about enchanting before armour, a weapon or a page: " + keys(p));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(step(p, "enchant/page/armour") != null && step(p, "enchant/page/armour").goal() == Goals.Goal.ARMOUR, "armour on: find an enchantment for it");
        p.getInventory().add(Pages.page(ench(h, "awaken:heat_ward"), ""));
        Goals.Step read = step(p, "enchant/read");
        h.assertTrue(read != null && read.path() == Goals.PATH_FIRST_GATE + 1 && read.goal() == Goals.Goal.ARMOUR, "a page carried: read it, on the path");
        Pages.read(p, firstPage(p));
        p.getInventory().clearContent(); // (takes the armour off too, in 26.3)
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(step(p, "enchant/read") == null && step(p, "enchant/page/armour") == null, "read: those leave");
        Goals.Step table = step(p, "enchant/table");
        h.assertTrue(table != null && table.path() == Goals.PATH_FIRST_GATE + 2, "then make the table");
        Inscribe.placed(p);
        Goals.Step first = step(p, "enchant/first");
        h.assertTrue(step(p, "enchant/table") == null && first != null && first.path() == Goals.PATH_FIRST_GATE + 3 && first.why().contains("Heat Ward"),
                "then the first inscription: " + (first == null ? "none" : first.why()));
        EnchantmentHelper.updateEnchantments(p.getItemBySlot(EquipmentSlot.CHEST), m -> m.set(ench(h, "awaken:heat_ward"), 1));
        Known k = Known.of(p);
        k.written = 1;
        Known.save(p, k);
        h.assertTrue(step(p, "enchant/first") == null, "written: it leaves");
        Goals.Step deeper = step(p, "enchant/room/armour");
        h.assertTrue(deeper != null && deeper.why().contains("can go deeper"), "Heat Ward I on iron can go to II: " + (deeper == null ? "none" : deeper.why()));
        EnchantmentHelper.updateEnchantments(p.getItemBySlot(EquipmentSlot.CHEST), m -> m.set(ench(h, "awaken:heat_ward"), 2));
        h.assertTrue(step(p, "enchant/room/armour") == null, "a full iron chestplate, as deep as it goes, asks for nothing more");
        h.succeed();
    }

    private static Goals.Step step(ServerPlayer p, String key) {
        for (Goals.Step s : EnchantGoals.steps(p)) if (s.key().equals(key)) return s;
        return null;
    }

    private static Set<String> keys(ServerPlayer p) {
        Set<String> out = new java.util.TreeSet<>();
        for (Goals.Step s : EnchantGoals.steps(p)) out.add(s.key());
        return out;
    }
}
