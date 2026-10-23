package dev.awaken.world.fauna;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The P2 families' crafting materials, {@code awaken:<family>_<thing>}, for Hunter gear (built by another module; it
 * refers to them by id). Two per family; the creatures drop them (loot tables), a few talents shed them, and the
 * family's named beast drops a handful. docs/modules/fauna.md lists who drops what.
 */
public final class FaunaItems {
    private FaunaItems() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    /** Every material id, family by family. */
    public static final List<String> MATERIALS = List.of(
            "mire_hide", "mire_glowsac",
            "canopy_venom", "canopy_scale",
            "dusk_shell", "dusk_feather",
            "blossom_dust", "blossom_fur",
            "crag_horn", "crag_feather",
            "spore_dust", "spore_gel");

    private static final Map<String, DeferredItem<Item>> BY_ID = new LinkedHashMap<>();

    static {
        for (String id : MATERIALS) BY_ID.put(id, ITEMS.registerSimpleItem(id, p -> p.rarity(Rarity.COMMON)));
    }

    static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(FaunaItems::tab);
    }

    public static Item get(String id) {
        DeferredItem<Item> h = BY_ID.get(id);
        if (h == null) throw new IllegalArgumentException("not a fauna material: " + id);
        return h.get();
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (DeferredItem<Item> item : BY_ID.values()) e.accept(item.get());
    }
}
