package dev.awaken.gear;

import dev.awaken.Awaken;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Progression;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Now tracker's gear line (low priority, an idea): "You can craft a Wildhide Vest" when the player holds what the
 * next tier of a piece needs, doesn't own that piece yet, and it would beat what they wear or carry. One line at most,
 * worked out at most once a second per player.
 */
final class GearNow {
    private GearNow() {}

    static final int PRIORITY = 25;
    private static final int CACHE_TICKS = 20;
    /** The order pieces are suggested in when two are craftable at the same tier: what changes a fight most first. */
    private static final Piece[] ORDER = {Piece.BLADE, Piece.CHESTPLATE, Piece.LEGGINGS, Piece.HELMET, Piece.BOOTS, Piece.FOCUS, Piece.MATTOCK};

    static void register() {
        NowHooks.register(GearNow::suggest);
    }

    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return List.of();
        GearState st = GearState.of(p);
        long now = p.level().getGameTime();
        if (now >= st.nowAt && now - st.nowAt < CACHE_TICKS) return st.now;
        st.nowAt = now;
        Item item = next(p);
        st.now = item == null ? List.of() : List.of(new NowHooks.Suggestion("gear:craft/" + key(item), PRIORITY, text(item), Optional.empty()));
        return st.now;
    }

    private static Component text(Item item) {
        boolean plural = item instanceof HunterGearItem g && (g.piece == Piece.LEGGINGS || g.piece == Piece.BOOTS);
        return Component.literal(plural ? "You can craft " : "You can craft a ").append(Component.translatable(item.getDescriptionId())).append(" at a crafting table");
    }

    private static String key(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    /** The piece to suggest, or null. Highest tier first, then {@link #ORDER}. */
    static @Nullable Item next(ServerPlayer p) {
        Inventory inv = p.getInventory();
        Map<Item, Integer> have = new IdentityHashMap<>();
        int[] owned = new int[Piece.values().length];
        java.util.Arrays.fill(owned, -1);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            have.merge(s.getItem(), s.getCount(), Integer::sum);
            if (s.getItem() instanceof HunterGearItem g) owned[g.piece.ordinal()] = Math.max(owned[g.piece.ordinal()], g.tier.ordinal());
        }
        Item best = null;
        int bestTier = -1;
        for (Piece piece : ORDER) {
            if (piece.isArmour()) continue; // forged at a Fitting Stand: the armour module's line says so (SPEC §44.4)
            int t = owned[piece.ordinal()] + 1;
            if (t >= HunterTier.values().length || t <= bestTier) continue;
            HunterTier tier = HunterTier.values()[t];
            if (!beats(p, tier, piece)) continue;
            Item item = GearItems.get(tier, piece);
            if (craftable(p, item, have)) {
                best = item;
                bestTier = t;
            }
        }
        return best;
    }

    /** Whether the piece would be an upgrade on what the player wears (armour), wields (blade) or digs with (mattock). */
    private static boolean beats(ServerPlayer p, HunterTier tier, Piece piece) {
        if (piece.isArmour()) {
            EquipmentSlot slot = piece.slot();
            return tier.armour.defense(piece.armorType) >= armourOf(p.getItemBySlot(slot), slot);
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() instanceof HunterGearItem) continue;
            if (piece == Piece.BLADE && meleeOf(s) > tier.blade.damage() + 0.5) return false;
            if (piece == Piece.MATTOCK) {
                Tool tool = s.get(DataComponents.TOOL);
                if (tool != null && tool.getMiningSpeed(Blocks.STONE.defaultBlockState()) >= tier.mattock.speed()) return false;
            }
        }
        return true;
    }

    private static double armourOf(ItemStack s, EquipmentSlot slot) {
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().value() == Attributes.ARMOR.value() && e.slot().test(slot)) sum += e.modifier().amount();
        }
        return sum;
    }

    private static double meleeOf(ItemStack s) {
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value() && e.slot().test(EquipmentSlot.MAINHAND)) sum += e.modifier().amount();
        }
        return sum;
    }

    /** Whether the inventory counts cover the piece's recipe (ingredients within one recipe never overlap). */
    static boolean craftable(ServerPlayer p, Item item, Map<Item, Integer> have) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Awaken.id(key(item)));
        Optional<RecipeHolder<?>> r = p.level().getServer().getRecipeManager().byKey(key);
        if (r.isEmpty()) return false;
        List<Ingredient> need = r.get().value().placementInfo().ingredients();
        if (need.isEmpty()) return false;
        Map<Item, Integer> left = new IdentityHashMap<>(have);
        for (Ingredient ing : need) {
            boolean found = false;
            for (Map.Entry<Item, Integer> e : left.entrySet()) {
                if (e.getValue() > 0 && ing.acceptsItem(e.getKey().builtInRegistryHolder())) {
                    e.setValue(e.getValue() - 1);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
