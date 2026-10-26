package dev.awaken.living.craft;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.mobs.MobTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What pays craft XP and what each perk does (SPEC §21.9). */
public final class CraftEvents {
    private CraftEvents() {}

    // ---- hunting ----------------------------------------------------------------------------------------------------

    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() instanceof Player) return;
        LivingEntity dead = e.getEntity();
        CreatureTag tag = CreatureTag.of(dead);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        double xp = 4 + Math.max(0, tag.level()) * 0.3;
        if (tag.kind() == CreatureTag.Kind.BEAST) xp += 60;
        else if (tag.elite()) xp += 20;
        if (Crafts.state(p).hunted(type) == 0) xp += 30; // the first of a kind
        Crafts.hunted(p, type);
        Crafts.gain(p, Craft.HUNTING, xp, 0);
    }

    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity dead = e.getEntity();
        CreatureTag tag = CreatureTag.of(dead);
        RandomSource r = p.getRandom();
        // Field Dressing: an animal gives a hide or more of its meat a third of the time
        boolean animal = tag.kind() == CreatureTag.Kind.PASSIVE || tag.kind() == CreatureTag.Kind.NEUTRAL || dead instanceof net.minecraft.world.entity.animal.Animal;
        if (animal && Crafts.has(p, Craft.HUNTING, "field_dressing") && r.nextFloat() < 0.33F) {
            ItemStack more = e.getDrops().isEmpty() ? new ItemStack(Items.LEATHER) : e.getDrops().iterator().next().getItem().copyWithCount(1);
            e.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.3, dead.getZ(), more));
        }
        // Trophy: a beast's parts twice
        if (tag.kind() == CreatureTag.Kind.BEAST && Crafts.has(p, Craft.HUNTING, "trophy")) {
            List<ItemEntity> twice = new java.util.ArrayList<>();
            for (ItemEntity ie : e.getDrops()) {
                String id = BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath();
                if (MobTypes.parts().containsKey(id)) twice.add(new ItemEntity(dead.level(), ie.getX(), ie.getY(), ie.getZ(), ie.getItem().copy()));
            }
            e.getDrops().addAll(twice);
        }
    }

    /** Weak Spot: crits 15% harder on kinds you've killed twenty of. */
    public static void onCrit(CriticalHitEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.isCriticalHit() || !Crafts.has(p, Craft.HUNTING, "weak_spot")) return;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getTarget().getType()).toString();
        if (Crafts.state(p).hunted(type) >= 20) e.setDamageMultiplier(e.getDamageMultiplier() * 1.15F);
    }

    /** Stalker (a sneak attack on a creature that hasn't seen you), Apex (elites and beasts); Heart of Stone (deep down). */
    public static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (e.getSource().getEntity() instanceof ServerPlayer p && !(target instanceof Player)) {
            float k = 1;
            if (p.isShiftKeyDown() && target instanceof Mob m && m.getTarget() != p && Crafts.has(p, Craft.HUNTING, "stalker")) k *= 1.5F;
            CreatureTag tag = CreatureTag.of(target);
            if ((tag.elite() || tag.kind() == CreatureTag.Kind.BEAST) && Crafts.has(p, Craft.HUNTING, "apex")) k *= 1.1F;
            if (k != 1) e.setAmount(e.getAmount() * k);
        }
        if (target instanceof ServerPlayer p && p.getY() < 0 && Crafts.has(p, Craft.MINING, "heart_of_stone")) e.setAmount(e.getAmount() * 0.85F);
    }

    // ---- foraging and mining: breaking ------------------------------------------------------------------------------

    public static void onBreak(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || p.isCreative() || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        BlockPos pos = e.getPos();
        if (Herbs.plant(s)) {
            boolean ripe = Herbs.ripe(pos, Herbs.day(level), Crafts.has(p, Craft.FORAGING, "root_sense"));
            if (ripe) {
                ItemStack herb = new ItemStack(Herbs.herbAt(level, pos, p.getRandom()));
                net.minecraft.world.level.block.Block.popResource(level, pos, herb);
                Crafts.gain(p, Craft.FORAGING, 12, 1);
                p.sendOverlayMessage(Component.literal("+ " + herb.getHoverName().getString()));
            } else if (!s.is(Blocks.SHORT_GRASS) && !s.is(Blocks.TALL_GRASS)) {
                Crafts.gain(p, Craft.FORAGING, 2, 0.2);
            }
            if ((s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)) && Crafts.has(p, Craft.FORAGING, "seed_keeper") && p.getRandom().nextFloat() < 0.04F) {
                Item[] rare = {Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS};
                net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(rare[p.getRandom().nextInt(rare.length)]));
            }
            return;
        }
        if (s.is(Blocks.RED_MUSHROOM) || s.is(Blocks.BROWN_MUSHROOM)) {
            Crafts.gain(p, Craft.FORAGING, 4, 0.5);
            return;
        }
        if (Herbs.ore(s)) Crafts.gain(p, Craft.MINING, oreXp(s), 1);
    }

    /**
     * Mining craft XP for an ore: 10 (15 in deepslate) by its rarity (the square root of its Hunter XP weight, so coal
     * pays 7 and diamond 25); a lode's heart pays 60.
     */
    static double oreXp(BlockState s) {
        if (BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_lode_heart")) return 60;
        var kind = dev.awaken.progression.ProgressionModule.OreXp.of(s);
        double rarity = kind == null ? 1 : Math.max(0.7, Math.min(2.5, Math.sqrt(kind.weight)));
        return (Herbs.deep(s) || kind == dev.awaken.progression.ProgressionModule.OreXp.DEEPGLASS ? 15 : 10) * rarity;
    }

    private static boolean deepglass(BlockState s) {
        var id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id.getNamespace().equals(dev.awaken.Awaken.MODID) && id.getPath().equals("deepglass");
    }

    /** Clean Strike: an ore's drops, sometimes one more; Deepglass Cutter: a shard more; Wild Larder: mushrooms one more. */
    public static void onBlockDrops(BlockDropsEvent e) {
        if (!(e.getBreaker() instanceof ServerPlayer p) || e.getDrops().isEmpty()) return;
        BlockState s = e.getState();
        RandomSource r = p.getRandom();
        boolean extra = false;
        if (Herbs.ore(s)) {
            extra = Crafts.has(p, Craft.MINING, "clean_strike") && r.nextFloat() < 0.15F
                    || deepglass(s) && Crafts.has(p, Craft.MINING, "deepglass_cutter");
        } else if (s.is(Blocks.RED_MUSHROOM) || s.is(Blocks.BROWN_MUSHROOM)) {
            extra = Crafts.has(p, Craft.FORAGING, "wild_larder");
        }
        if (extra) {
            ItemEntity first = e.getDrops().getFirst();
            e.getDrops().add(new ItemEntity(e.getLevel(), first.getX(), first.getY(), first.getZ(), first.getItem().copyWithCount(1)));
        }
    }

    /** Deepglass Cutter: Deepglass breaks twice as fast (both sides read the synced crafts, so bar and break agree). */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        BlockState s = e.getState();
        if (deepglass(s) && Crafts.has(e.getEntity(), Craft.MINING, "deepglass_cutter")) e.setNewSpeed(e.getNewSpeed() * 2F);
    }

    /** Berry bushes and glow berries picked by hand: foraging, and Wild Larder's one more. */
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND) return;
        BlockState s = e.getLevel().getBlockState(e.getPos());
        ServerLevel level = (ServerLevel) e.getLevel();
        if (s.is(Blocks.SWEET_BERRY_BUSH) && s.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) >= 2
                || (s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT)) && s.getValue(net.minecraft.world.level.block.CaveVines.BERRIES)) {
            Crafts.gain(p, Craft.FORAGING, 5, 0.5);
            if (Crafts.has(p, Craft.FORAGING, "wild_larder")) {
                net.minecraft.world.level.block.Block.popResource(level, e.getPos(), new ItemStack(s.is(Blocks.SWEET_BERRY_BUSH) ? Items.SWEET_BERRIES : Items.GLOW_BERRIES));
            }
            return;
        }
        // Field Forge: an iron ingot on a lit campfire mends what's in the other hand by a quarter
        if (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT) && p.getMainHandItem().is(Items.IRON_INGOT) && Crafts.has(p, Craft.SMITHING, "field_forge")) {
            ItemStack tool = p.getOffhandItem();
            if (tool.isDamageableItem() && tool.getDamageValue() > 0) {
                tool.setDamageValue(Math.max(0, tool.getDamageValue() - tool.getMaxDamage() / 4));
                p.getMainHandItem().shrink(1);
                level.playSound(null, e.getPos(), net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.3F);
                Crafts.gain(p, Craft.SMITHING, 8, 1);
                e.setCanceled(true);
            }
        }
    }

    // ---- fishing ----------------------------------------------------------------------------------------------------

    public static void onFished(ItemFishedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Crafts.gain(p, Craft.FISHING, 15, 1);
        RandomSource r = p.getRandom();
        List<ItemStack> more = new java.util.ArrayList<>();
        ItemStack first = e.getDrops().isEmpty() ? ItemStack.EMPTY : e.getDrops().getFirst();
        if (!first.isEmpty() && Crafts.has(p, Craft.FISHING, "calm_water") && r.nextFloat() < 0.2F) more.add(first.copyWithCount(1));
        if (!first.isEmpty() && p.level().isRaining() && Crafts.has(p, Craft.FISHING, "storm_caller")) more.add(first.copyWithCount(1));
        if (Crafts.has(p, Craft.FISHING, "shimmer") && r.nextFloat() < 0.10F) {
            Item[] treasure = {Items.NAME_TAG, Items.SADDLE, Items.NAUTILUS_SHELL, Items.BOOK, Items.EXPERIENCE_BOTTLE};
            more.add(new ItemStack(treasure[r.nextInt(treasure.length)]));
        }
        if (Crafts.has(p, Craft.FISHING, "pearl_diver")) {
            if (r.nextFloat() < 0.08F) more.add(new ItemStack(Items.PRISMARINE_SHARD));
            if (r.nextFloat() < 0.02F) more.add(new ItemStack(Items.ENDER_PEARL));
        }
        for (ItemStack st : more) p.getInventory().placeItemBackInInventory(st, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- smithing ---------------------------------------------------------------------------------------------------

    /** Patch: anvil repairs a level cheaper. */
    public static void onAnvil(AnvilUpdateEvent e) {
        if (e.getPlayer() != null && Crafts.has(e.getPlayer(), Craft.SMITHING, "patch") && e.getXpCost() > 1) e.setXpCost(e.getXpCost() - 1);
    }

    public static void onAnvilDone(AnvilCraftEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p) Crafts.gain(p, Craft.SMITHING, 12, 1);
    }

    /** A temper finished (hearth's Forge reports it as a quest use): the biggest smithing action. */
    public static void tempered(ServerPlayer p) {
        Crafts.gain(p, Craft.SMITHING, 40, 3);
    }

    public static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack made = e.getCrafting();
        if (made.isDamageableItem()) {
            Crafts.gain(p, Craft.SMITHING, 8, 1);
            RandomSource r = p.getRandom();
            if (Crafts.has(p, Craft.SMITHING, "fine_edge")) made.set(DataComponents.MAX_DAMAGE, (int) (made.getMaxDamage() * 1.1));
            var enchants = p.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            if (Crafts.has(p, Craft.SMITHING, "masterwork") && r.nextFloat() < 0.10F) made.enchant(enchants.getOrThrow(Enchantments.UNBREAKING), 1);
            if (Crafts.has(p, Craft.SMITHING, "keen") && r.nextFloat() < 0.20F && made.has(DataComponents.WEAPON)) made.enchant(enchants.getOrThrow(Enchantments.SHARPNESS), 1);
            if (Crafts.has(p, Craft.SMITHING, "signature")) {
                made.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Made by " + p.getName().getString()).withStyle(net.minecraft.ChatFormatting.GOLD))));
            }
        } else if (made.has(DataComponents.FOOD)) {
            Crafts.gain(p, Craft.COOKING, 8 * Math.max(1, made.getCount() / 2.0), 1);
        }
    }

    // ---- cooking ----------------------------------------------------------------------------------------------------

    public static void onSmelted(PlayerEvent.ItemSmeltedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getSmelting().has(DataComponents.FOOD)) {
            Crafts.gain(p, Craft.COOKING, 6 * e.getSmelting().getCount(), Math.min(3, e.getSmelting().getCount() / 4.0 + 0.5));
        }
    }

    /** Who ate a cooked meal and when (Spirit Tea's energy afterward). */
    static final Map<UUID, Long> ATE = new ConcurrentHashMap<>();

    public static void onEat(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack food = e.getItem();
        var props = food.get(DataComponents.FOOD);
        if (props == null) return;
        boolean cooked = food.getItem() instanceof CraftItems.Meal || isCooked(food);
        if (!cooked) return;
        float sat = 0;
        if (Crafts.has(p, Craft.COOKING, "hearty")) sat += 1;
        if (Crafts.has(p, Craft.COOKING, "long_meals")) sat += 2;
        if (Crafts.has(p, Craft.COOKING, "banquet")) sat += props.saturation();
        if (sat > 0) p.getFoodData().eat(0, sat / Math.max(1, props.nutrition()) / 2F + 0.0001F);
        ATE.put(p.getUUID(), p.level().getGameTime());
        // Campfire Feast: eating beside a lit campfire feeds those near too
        if (Crafts.has(p, Craft.COOKING, "campfire_feast") && nearCampfire(p)) {
            for (ServerPlayer o : p.level().players()) {
                if (o != p && o.distanceToSqr(p) < 36) o.getFoodData().eat(Math.max(1, props.nutrition() / 2), 0.3F);
            }
        }
    }

    private static boolean isCooked(ItemStack food) {
        String id = BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
        return id.startsWith("cooked_") || id.endsWith("_stew") || id.endsWith("_soup") || id.equals("bread") || id.equals("baked_potato")
                || id.equals("pumpkin_pie") || id.equals("cake");
    }

    private static boolean nearCampfire(ServerPlayer p) {
        BlockPos c = p.blockPosition();
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            BlockState s = p.level().getBlockState(b);
            if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) return true;
        }
        return false;
    }

    /** The perk source: Spirit Tea's energy for two minutes after a cooked meal. */
    public static double perk(Player p, String perk) {
        if (!perk.equals("awaken:energy_regen")) return 0;
        Long at = ATE.get(p.getUUID());
        if (at == null || p.level().getGameTime() - at > 2400) return 0;
        return Crafts.has(p, Craft.COOKING, "spirit_tea") ? 0.25 : 0;
    }

    // ---- the world around you, every second -----------------------------------------------------------------------

    /** Wildheart (forests heal), Bloom (sprites in flowers), Deep Lungs (air lasts twice as long). */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            if (t % 2 == 0 && p.isUnderWater() && p.getAirSupply() < p.getMaxAirSupply() && Crafts.has(p, Craft.FISHING, "deep_lungs")) {
                p.setAirSupply(p.getAirSupply() + 1); // vanilla takes one a tick: this gives back every other
            }
            if (t % 200 != (p.getId() & 0xFF) % 200) continue;
            ServerLevel level = p.level();
            if (Crafts.has(p, Craft.FORAGING, "wildheart") && level.getBiome(p.blockPosition()).is(BiomeTags.IS_FOREST)) {
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 240, 0, true, false, true));
            }
            if (Crafts.has(p, Craft.FORAGING, "bloom") && level.isBrightOutside() && p.getRandom().nextFloat() < 0.3F && flowersNear(level, p.blockPosition())) {
                var sprite = MobTypes.type("bloom_sprite");
                if (sprite != null && level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getType() == sprite).isEmpty()) {
                    Entity e = sprite.spawn(level, p.blockPosition().offset(p.getRandom().nextInt(7) - 3, 1, p.getRandom().nextInt(7) - 3),
                            net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    if (e instanceof Mob m) m.setPersistenceRequired();
                }
            }
        }
    }

    private static boolean flowersNear(ServerLevel level, BlockPos c) {
        int n = 0;
        for (BlockPos b : BlockPos.betweenClosed(c.offset(-5, -2, -5), c.offset(5, 2, 5))) {
            if (level.getBlockState(b).is(BlockTags.FLOWERS) && ++n >= 3) return true;
        }
        return false;
    }
}
