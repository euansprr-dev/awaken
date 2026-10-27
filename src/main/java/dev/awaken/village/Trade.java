package dev.awaken.village;

import dev.awaken.citizens.api.Settlement;
import dev.awaken.city.VillageStyles;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Trade in emeralds (SPEC §28.6, PLAN 5.5, decision 5): each trade sells what its people make and buys what they need;
 * each land's villages make something of their own (a fishing village's shells, a caravanserai's glass, the frost's
 * furs); the village scribe sells enchanted books, the inn a bed for the night. Prices show standing on the trade
 * screen itself (a stranger pays a little over, a friend and kin under, the old price struck through). Nothing ever
 * locks for good: what a village buys from you is capped a play-day, and the next day it buys again.
 */
final class Trade {
    private Trade() {}

    /** One deal: what the player gives (item, count) and gets; "buys" when the village is the one buying. */
    record Deal(String id, Item cost, int costCount, ItemStack result, boolean buys, int perDay) {}

    static Deal sells(String id, Item item, int count, int emeralds) {
        return new Deal(id, Items.EMERALD, emeralds, new ItemStack(item, count), false, 16);
    }

    static Deal buys(String id, Item item, int count, int emeralds, int perDay) {
        return new Deal(id, item, count, new ItemStack(Items.EMERALD, emeralds), true, perDay);
    }

    /** What a trade sells and buys (null: this person doesn't trade). */
    static @Nullable List<Deal> deals(String job, int family, long seed, long day, ServerLevel level) {
        List<Deal> d = new ArrayList<>();
        switch (job) {
            case "farmer" -> {
                d.add(buys("wheat", Items.WHEAT, 20, 1, 12));
                d.add(buys("potato", Items.POTATO, 26, 1, 10));
                d.add(buys("carrot", Items.CARROT, 22, 1, 10));
                d.add(buys("beetroot", Items.BEETROOT, 15, 1, 8));
                d.add(sells("bread", Items.BREAD, 6, 1));
                d.add(sells("apple", Items.APPLE, 5, 1));
                d.add(sells("pie", Items.PUMPKIN_PIE, 4, 1));
                d.add(sells("golden_carrot", Items.GOLDEN_CARROT, 3, 3));
            }
            case "shepherd" -> {
                d.add(buys("wool", Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 16, 1, 8));
                d.add(sells("shears", Items.SHEARS, 1, 2));
                d.add(sells("wool_out", Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 2, 1));
                d.add(sells("carpet", Items.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE), 4, 1));
                d.add(sells("bed", Items.BED.pick(net.minecraft.world.item.DyeColor.WHITE), 1, 3));
                d.add(sells("painting", Items.PAINTING, 1, 2));
            }
            case "woodcutter" -> {
                d.add(buys("sticks", Items.STICK, 32, 1, 6));
                d.add(sells("logs", log(family), 12, 1));
                d.add(sells("charcoal", Items.CHARCOAL, 6, 1));
                d.add(sells("ladder", Items.LADDER, 8, 1));
            }
            case "miner" -> {
                d.add(buys("coal", Items.COAL, 15, 1, 10));
                d.add(buys("raw_copper", Items.RAW_COPPER, 10, 1, 8));
                d.add(sells("torches", Items.TORCH, 16, 1));
                d.add(sells("lantern", Items.LANTERN, 1, 2));
                d.add(sells("flint", Items.FLINT, 10, 1));
            }
            case "fisher" -> {
                d.add(buys("string", Items.STRING, 20, 1, 6));
                d.add(sells("cod", Items.COD, 6, 1));
                d.add(sells("salmon", Items.SALMON, 4, 1));
                d.add(sells("cooked_cod", Items.COOKED_COD, 6, 1));
                d.add(sells("rod", Items.FISHING_ROD, 1, 3));
            }
            case "hunter" -> {
                d.add(buys("leather", Items.LEATHER, 6, 1, 8));
                d.add(buys("rabbit_hide", Items.RABBIT_HIDE, 9, 1, 6));
                d.add(sells("cooked_beef", Items.COOKED_BEEF, 4, 1));
                d.add(sells("stew", Items.RABBIT_STEW, 1, 1));
                d.add(sells("arrows", Items.ARROW, 16, 1));
                d.add(sells("bow", Items.BOW, 1, 3));
                d.add(sells("jerkin", Items.LEATHER_CHESTPLATE, 1, 5));
            }
            case "smith" -> {
                d.add(buys("iron", Items.IRON_INGOT, 4, 1, 10));
                d.add(buys("coal", Items.COAL, 15, 1, 8));
                d.add(sells("pickaxe", Items.IRON_PICKAXE, 1, 7));
                d.add(sells("axe", Items.IRON_AXE, 1, 7));
                d.add(sells("sword", Items.IRON_SWORD, 1, 6));
                d.add(sells("shovel", Items.IRON_SHOVEL, 1, 4));
                d.add(sells("shield", Items.SHIELD, 1, 5));
                d.add(sells("helmet", Items.IRON_HELMET, 1, 6));
                d.add(sells("bucket", Items.BUCKET, 1, 3));
                d.add(sells("chain", Items.IRON_CHAIN, 2, 1));
            }
            case "miller" -> {
                d.add(buys("wheat", Items.WHEAT, 18, 1, 12));
                d.add(sells("bread", Items.BREAD, 8, 1));
                d.add(sells("cookies", Items.COOKIE, 12, 1));
                d.add(sells("sugar", Items.SUGAR, 8, 1));
                d.add(sells("cake", Items.CAKE, 1, 2));
            }
            case "tavernkeeper" -> {
                d.add(sells(Inn.ROOM, VillageContent.INN_KEY.get(), 1, Inn.PRICE));
                d.add(sells("stew", Items.MUSHROOM_STEW, 1, 1));
                d.add(sells("bread", Items.BREAD, 6, 1));
                d.add(sells("beef", Items.COOKED_BEEF, 4, 1));
                d.add(sells("honey", Items.HONEY_BOTTLE, 2, 1));
            }
            case "healer" -> {
                d.add(buys("spider_eye", Items.SPIDER_EYE, 8, 1, 6));
                d.add(buys("rotten_flesh", Items.ROTTEN_FLESH, 32, 1, 6));
                d.add(new Deal("healing", Items.EMERALD, 4, PotionContents.createItemStack(Items.POTION, Potions.HEALING), false, 4));
                d.add(new Deal("regen", Items.EMERALD, 6, PotionContents.createItemStack(Items.POTION, Potions.REGENERATION), false, 3));
                d.add(sells("melon", Items.GLISTERING_MELON_SLICE, 1, 2));
            }
            case "scribe" -> {
                books(d, seed, day, level);
                d.add(buys("paper", Items.PAPER, 24, 1, 8));
                d.add(sells("book", Items.BOOK, 2, 1));
                d.add(sells("quill", Items.WRITABLE_BOOK, 1, 2));
                d.add(sells("map", Items.MAP, 1, 3));
                d.add(sells("candles", Items.CANDLE, 4, 1));
                d.add(sells("name_tag", Items.NAME_TAG, 1, 6));
            }
            case "assayer" -> {
                // (SPEC §28.9) a mining town's store: what its mine brings up, ingots, and a lode map read with lapis
                d.add(sells("raw_iron", Items.RAW_IRON, 4, 1));
                d.add(sells("raw_copper", Items.RAW_COPPER, 8, 1));
                d.add(sells("coal", Items.COAL, 12, 1));
                d.add(sells("iron_ingot", Items.IRON_INGOT, 2, 1));
                d.add(sells("gold_nugget", Items.GOLD_NUGGET, 6, 1));
                d.add(sells("lapis", Items.LAPIS_LAZULI, 4, 1));
                d.add(new Deal("lode_map", Items.EMERALD, 8, dev.awaken.delve.cave.CaveItems.map(), false, 2));
                d.add(buys("torches", Items.TORCH, 32, 1, 6));
                d.add(sells("lantern", Items.LANTERN, 1, 2));
            }
            case "carter" -> {
                d.add(sells("lead", Items.LEAD, 2, 1));
                d.add(sells("saddle", Items.SADDLE, 1, 6));
                d.add(sells("hay", Items.HAY_BLOCK, 3, 1));
                d.add(sells("chest", Items.CHEST, 1, 1));
                d.add(sells("barrel", Items.BARREL, 1, 1));
                specialty(d, (family + 3) % VillageStyles.FAMILIES.length);
            }
            case "stallkeeper" -> {
                d.add(sells("apples", Items.APPLE, 4, 1));
                d.add(sells("carrots", Items.CARROT, 8, 1));
                d.add(sells("potatoes", Items.POTATO, 8, 1));
                d.add(sells("melon", Items.MELON_SLICE, 12, 1));
                specialty(d, family);
            }
            default -> {
                return null;
            }
        }
        if (job.equals("fisher") || job.equals("hunter") || job.equals("woodcutter") || job.equals("miner")) specialty(d, family);
        return d;
    }

    /** The land's own goods (decision 5: each land's villages make something of their own). */
    static void specialty(List<Deal> d, int family) {
        switch (family) {
            case VillageStyles.FROST -> {
                d.add(sells("furs", Items.RABBIT_HIDE, 4, 1));
                d.add(sells("berries", Items.SWEET_BERRIES, 16, 1));
            }
            case VillageStyles.DESERT -> {
                d.add(sells("glass", Items.GLASS, 6, 1));
                d.add(sells("cactus", Items.CACTUS, 8, 1));
            }
            case VillageStyles.SAVANNA -> {
                d.add(sells("terracotta", Items.TERRACOTTA, 12, 1));
                d.add(sells("acacia", Items.ACACIA_LOG, 16, 1));
            }
            case VillageStyles.MIRE -> {
                d.add(sells("slime", Items.SLIME_BALL, 2, 1));
                d.add(sells("clay", Items.CLAY_BALL, 12, 1));
            }
            case VillageStyles.HIGHLAND -> {
                d.add(sells("copper", Items.COPPER_INGOT, 3, 1));
                d.add(sells("flint_s", Items.FLINT, 12, 1));
            }
            case VillageStyles.ELDER -> {
                d.add(sells("glow_berries", Items.GLOW_BERRIES, 8, 1));
                d.add(sells("dark_oak", Items.DARK_OAK_LOG, 16, 1));
            }
            case VillageStyles.COAST -> {
                d.add(sells("ink", Items.INK_SAC, 6, 1));
                d.add(sells("shell", Items.NAUTILUS_SHELL, 1, 8));
                d.add(sells("prismarine", Items.PRISMARINE_SHARD, 6, 1));
            }
            case VillageStyles.JUNGLE -> {
                d.add(sells("cocoa", Items.COCOA_BEANS, 12, 1));
                d.add(sells("bamboo", Items.BAMBOO, 24, 1));
            }
            case VillageStyles.CHERRY -> {
                d.add(sells("petals", Items.PINK_PETALS, 16, 1));
                d.add(sells("cherry", Items.CHERRY_LOG, 16, 1));
            }
            default -> {
                d.add(sells("honey_s", Items.HONEY_BOTTLE, 2, 1));
                d.add(sells("honeycomb", Items.HONEYCOMB, 3, 1));
            }
        }
    }

    static Item log(int family) {
        return switch (family) {
            case VillageStyles.FROST, VillageStyles.HIGHLAND, VillageStyles.COAST -> Items.SPRUCE_LOG;
            case VillageStyles.SAVANNA, VillageStyles.DESERT -> Items.ACACIA_LOG;
            case VillageStyles.MIRE -> Items.MANGROVE_LOG;
            case VillageStyles.ELDER -> Items.DARK_OAK_LOG;
            case VillageStyles.JUNGLE -> Items.JUNGLE_LOG;
            case VillageStyles.CHERRY -> Items.CHERRY_LOG;
            default -> Items.OAK_LOG;
        };
    }

    /** The scribe's books: three a day from the tradeable enchantments, priced by level. */
    static void books(List<Deal> d, long seed, long day, ServerLevel level) {
        var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> pool = new ArrayList<>();
        reg.get(EnchantmentTags.TRADEABLE).ifPresent(set -> set.forEach(pool::add));
        if (pool.isEmpty()) return;
        Random r = new Random(seed ^ day * 0x9E3779B97F4A7C15L);
        for (int k = 0; k < 3; k++) {
            Holder<Enchantment> e = pool.get(r.nextInt(pool.size()));
            int max = e.value().getMaxLevel();
            int lv = 1 + r.nextInt(Math.max(1, Math.min(max, 3)));
            int price = Math.min(64, 4 + lv * 5 + r.nextInt(6));
            d.add(new Deal("book" + k, Items.EMERALD, price, EnchantmentHelper.createBook(new EnchantmentInstance(e, lv)), false, 1));
        }
    }

    // ---- the screen ----------------------------------------------------------------------------------------------

    /**
     * A trader's goods for one player (vanilla's merchant screen): costs moved by standing, today's counts as uses (a
     * village buys only so much a day). Every trade counts toward standing (a few a day) and the village's prosperity.
     */
    static final class Shop implements Merchant {
        final ServerPlayer player;
        final Settlement s;
        final VillageService.Live live;
        final int rec;
        final List<Deal> deals;
        final MerchantOffers offers = new MerchantOffers();
        private @Nullable Player trading;
        /** Opened by a command (tests, tours): no walking away from the trader closes it. */
        boolean remote;

        Shop(ServerPlayer player, VillageService.Live live, int rec, List<Deal> deals, int tier) {
            this.player = player;
            this.live = live;
            this.s = live.settlement();
            this.rec = rec;
            this.deals = deals;
            long today = VillageService.today(player.level().getServer());
            for (Deal d : deals) {
                int used = Math.min(d.perDay(), live.rec().count(today, player.getUUID() + "|" + live.key() + "|" + d.id()));
                MerchantOffer o = new MerchantOffer(new ItemCost(d.cost(), d.costCount()), java.util.Optional.empty(), d.result().copy(), used,
                        d.perDay(), 0, 0f);
                o.setSpecialPriceDiff(priceDiff(d, tier));
                offers.add(o);
            }
        }

        /** The visible price of standing: a stranger pays over, a friend under, kin less again. */
        static int priceDiff(Deal d, int tier) {
            int c = d.costCount();
            if (d.buys()) {
                return switch (tier) {
                    case 0 -> Math.max(0, c / 8);
                    case 2 -> -c / 8;
                    case 3 -> -c / 5;
                    default -> 0;
                };
            }
            return switch (tier) {
                case 0 -> c >= 3 ? 1 : 0;
                case 2 -> -(c / 5);
                case 3 -> c >= 2 ? -Math.max(1, c / 3) : 0;
                default -> 0;
            };
        }

        @Override
        public void setTradingPlayer(@Nullable Player player) {
            this.trading = player;
        }

        @Override
        public @Nullable Player getTradingPlayer() {
            return trading;
        }

        @Override
        public MerchantOffers getOffers() {
            return offers;
        }

        @Override
        public void overrideOffers(MerchantOffers offers) {}

        @Override
        public void notifyTrade(MerchantOffer offer) {
            offer.increaseUses();
            int k = offers.indexOf(offer);
            Deal d = k >= 0 && k < deals.size() ? deals.get(k) : null;
            if (d == null) return;
            VillageService.traded(player, live, rec, d);
        }

        @Override
        public void notifyTradeUpdated(ItemStack stack) {}

        @Override
        public int getVillagerXp() {
            return 0;
        }

        @Override
        public void overrideXp(int xp) {}

        @Override
        public boolean showProgressBar() {
            return false;
        }

        @Override
        public SoundEvent getNotifyTradeSound() {
            return SoundEvents.ITEM_PICKUP;
        }

        @Override
        public boolean isClientSide() {
            return false;
        }

        @Override
        public boolean stillValid(Player p) {
            Entity body = s.body(rec);
            if (trading != p) return false;
            if (remote) return true;
            return body == null ? p.distanceToSqr(live.plan().ox, p.getY(), live.plan().oz) < 160 * 160 : p.distanceToSqr(body) < 64;
        }

        void open(Component title) {
            setTradingPlayer(player);
            openTradingScreen(player, title, 1);
        }
    }
}
