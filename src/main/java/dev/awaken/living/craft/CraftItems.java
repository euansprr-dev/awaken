package dev.awaken.living.craft;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Consumer;

/**
 * The crafts' items (SPEC §21.9): six wild herbs (from ripe plants, by land) and nine meals made from them, each with its
 * strength. A meal's effects last half again as long for a cook with Hunter's Stew.
 */
public final class CraftItems {
    private CraftItems() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    public static final DeferredItem<Item> SUNLEAF = herb("sunleaf");
    public static final DeferredItem<Item> FROSTCAP = herb("frostcap");
    public static final DeferredItem<Item> EMBERROOT = herb("emberroot");
    public static final DeferredItem<Item> MOONMOSS = herb("moonmoss");
    public static final DeferredItem<Item> DUSKBLOOM = herb("duskbloom");
    public static final DeferredItem<Item> SPIRITGRASS = herb("spiritgrass");

    /** One of a meal's strengths: the effect, seconds, amplifier. */
    record Strength(Holder<MobEffect> effect, int seconds, int amp) {}

    public static final DeferredItem<Meal> HERB_BROTH = meal("herb_broth", 6, 0.6F, new Strength(MobEffects.REGENERATION, 20, 0));
    public static final DeferredItem<Meal> FROSTCAP_TEA = meal("frostcap_tea", 4, 0.4F, new Strength(MobEffects.RESISTANCE, 60, 0));
    public static final DeferredItem<Meal> EMBER_SKEWER = meal("ember_skewer", 8, 0.8F, new Strength(MobEffects.STRENGTH, 60, 0));
    public static final DeferredItem<Meal> MOONMOSS_SOUP = meal("moonmoss_soup", 6, 0.6F, new Strength(MobEffects.NIGHT_VISION, 120, 0));
    public static final DeferredItem<Meal> HUNTERS_STEW = meal("hunters_stew", 10, 0.9F,
            new Strength(MobEffects.STRENGTH, 120, 0), new Strength(MobEffects.SPEED, 120, 0));
    public static final DeferredItem<Meal> FORAGERS_SALAD = meal("foragers_salad", 5, 1.0F, new Strength(MobEffects.REGENERATION, 30, 0));
    public static final DeferredItem<Meal> MINERS_PASTY = meal("miners_pasty", 9, 0.8F, new Strength(MobEffects.HASTE, 180, 0));
    public static final DeferredItem<Meal> ANGLERS_CHOWDER = meal("anglers_chowder", 8, 0.7F,
            new Strength(MobEffects.WATER_BREATHING, 120, 0), new Strength(MobEffects.DOLPHINS_GRACE, 60, 0));
    public static final DeferredItem<Meal> SPIRIT_TEA = meal("spirit_tea", 3, 0.5F, new Strength(MobEffects.LUCK, 120, 0));

    private static DeferredItem<Item> herb(String id) {
        return ITEMS.registerSimpleItem(id, p -> p.stacksTo(64));
    }

    private static DeferredItem<Meal> meal(String id, int nutrition, float saturation, Strength... strengths) {
        return ITEMS.registerItem(id, p -> new Meal(p.stacksTo(16).food(new FoodProperties.Builder().nutrition(nutrition)
                .saturationModifier(saturation).build()).usingConvertsTo(Items.BOWL), List.of(strengths)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** A meal: food, then its strengths (longer for a cook with Hunter's Stew). */
    public static final class Meal extends Item {
        final List<Strength> strengths;

        Meal(Item.Properties p, List<Strength> strengths) {
            super(p);
            this.strengths = strengths;
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity who) {
            if (!level.isClientSide() && who instanceof ServerPlayer p) {
                double k = Crafts.has(p, Craft.COOKING, "hunters_stew") ? 1.5 : 1.0;
                for (Strength s : strengths) p.addEffect(new MobEffectInstance(s.effect(), (int) (s.seconds() * 20 * k), s.amp()));
            }
            return super.finishUsingItem(stack, level, who);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            for (Strength s : strengths) {
                out.accept(Component.translatable(s.effect().value().getDescriptionId()).append(" · " + s.seconds() + " s")
                        .withStyle(net.minecraft.ChatFormatting.BLUE));
            }
        }
    }
}
