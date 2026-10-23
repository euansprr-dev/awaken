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
            "spore_dust", "spore_gel",
            // P3: the Tidewater and the Hollows
            "tide_scale", "tide_pelt", "tide_gel",
            "hollow_crystal", "hollow_chitin", "hollow_wick");

    private static final Map<String, DeferredItem<Item>> BY_ID = new LinkedHashMap<>();

    static {
        for (String id : MATERIALS) BY_ID.put(id, ITEMS.registerSimpleItem(id, p -> p.rarity(Rarity.COMMON)));
    }

    /** P3: the Hunter's Bridle (makes a trusting mount rideable) and the Hunter's Whistle (calls it). */
    public static final DeferredItem<Item> BRIDLE = ITEMS.registerSimpleItem("hunters_bridle", p -> p.stacksTo(8).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<WhistleItem> WHISTLE = ITEMS.registerItem("hunters_whistle", WhistleItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

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
        e.accept(BRIDLE.get());
        e.accept(WHISTLE.get());
    }
}
