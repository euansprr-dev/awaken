package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arms' weapons (SPEC §24): one item per {@link ArmsData} row (generated with their models by
 * tools/art/specs/arms_weapons.py), each a sword of its row's numbers that fights with its row's style.
 */
public final class ArmsItems {
    private ArmsItems() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    private static final Map<String, ArmsData.Weapon> BY_ID = ArmsData.WEAPONS.stream()
            .collect(Collectors.toUnmodifiableMap(ArmsData.Weapon::id, Function.identity()));
    static final List<DeferredItem<Item>> ALL = ArmsData.WEAPONS.stream().map(ArmsItems::register).toList();

    private static DeferredItem<Item> register(ArmsData.Weapon w) {
        TagKey<Item> repair = TagKey.create(Registries.ITEM, Awaken.id(w.repair()));
        return ITEMS.registerItem(w.id(), Item::new, p -> p.rarity(w.rarity())
                .sword(new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, w.durability(), 1.0F, 0.0F, w.enchantability(), repair),
                        w.damage(), w.speed()));
    }

    /** The row behind one of our weapons by its id, or null for any other item. */
    static ArmsData.@Nullable Weapon byId(Identifier id) {
        return id.getNamespace().equals(Awaken.MODID) ? BY_ID.get(id.getPath()) : null;
    }

    /** The row behind one of our weapons, or null for any other item. */
    public static ArmsData.@Nullable Weapon of(Item item) {
        return byId(BuiltInRegistries.ITEM.getKey(item));
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ArmsItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (DeferredItem<Item> item : ALL) event.accept(item.get());
    }
}
