package dev.awaken.above;

import dev.awaken.Awaken;
import dev.awaken.above.frame.FrameItem;
import dev.awaken.above.frame.FrameModule;
import dev.awaken.above.frame.FrameModuleItem;
import dev.awaken.above.power.EssenceCellItem;
import dev.awaken.above.scan.ScannerItem;
import dev.awaken.core.CoreModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Materials, tools, Frames, modules and the block items of every Above block. */
public final class AboveItems {
    private AboveItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    // ---- materials ------------------------------------------------------------------------------------------------
    public static final DeferredItem<Item> SCRAP = ITEMS.registerSimpleItem("precursor_scrap");
    public static final DeferredItem<Item> CIRCUIT = ITEMS.registerSimpleItem("precursor_circuit");
    public static final DeferredItem<Item> ALLOY = ITEMS.registerSimpleItem("precursor_alloy", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<EssenceCellItem> CELL = ITEMS.registerItem("essence_cell", EssenceCellItem::new,
            p -> p.stacksTo(1).component(AboveComponents.ESSENCE.get(), 0));
    public static final DeferredItem<ScannerItem> SCANNER = ITEMS.registerItem("precursor_scanner", ScannerItem::new, p -> p.stacksTo(1));

    // ---- Frames (exosuit) -----------------------------------------------------------------------------------------
    public static final TagKey<Item> FRAME_REPAIR = TagKey.create(Registries.ITEM, Awaken.id("repairs_precursor_frame"));
    public static final ResourceKey<EquipmentAsset> FRAME_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Awaken.id("precursor_frame"));
    public static final ArmorMaterial FRAME_MATERIAL = new ArmorMaterial(35,
            Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3, ArmorType.BODY, 10),
            12, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5f, 0.05f, FRAME_REPAIR, FRAME_ASSET);

    public static final DeferredItem<FrameItem> FRAME_HELMET = frame("precursor_frame_helmet", ArmorType.HELMET);
    public static final DeferredItem<FrameItem> FRAME_CHEST = frame("precursor_frame_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<FrameItem> FRAME_LEGS = frame("precursor_frame_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<FrameItem> FRAME_BOOTS = frame("precursor_frame_boots", ArmorType.BOOTS);

    private static DeferredItem<FrameItem> frame(String name, ArmorType type) {
        return ITEMS.registerItem(name, p -> new FrameItem(p, type),
                p -> p.humanoidArmor(FRAME_MATERIAL, type).rarity(Rarity.RARE).component(AboveComponents.FRAME_MODULES.get(), List.of()));
    }

    public static final Map<FrameModule, DeferredItem<FrameModuleItem>> MODULES = new EnumMap<>(FrameModule.class);

    static {
        for (FrameModule m : FrameModule.values()) {
            MODULES.put(m, ITEMS.registerItem("frame_module_" + m.id, p -> new FrameModuleItem(p, m), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON)));
        }
    }

    // ---- block items ----------------------------------------------------------------------------------------------
    public static final DeferredItem<BlockItem> PLATING = ITEMS.registerSimpleBlockItem(AboveBlocks.PLATING);
    public static final DeferredItem<BlockItem> PANEL = ITEMS.registerSimpleBlockItem(AboveBlocks.PANEL);
    public static final DeferredItem<BlockItem> TRIM = ITEMS.registerSimpleBlockItem(AboveBlocks.TRIM);
    public static final DeferredItem<BlockItem> GLOW_PANEL = ITEMS.registerSimpleBlockItem(AboveBlocks.GLOW_PANEL);
    public static final DeferredItem<BlockItem> WARNING_LIGHT = ITEMS.registerSimpleBlockItem(AboveBlocks.WARNING_LIGHT);
    public static final DeferredItem<BlockItem> VOID_GLASS = ITEMS.registerSimpleBlockItem(AboveBlocks.VOID_GLASS);
    public static final DeferredItem<BlockItem> MEMBRANE = ITEMS.registerSimpleBlockItem(AboveBlocks.MEMBRANE);
    public static final DeferredItem<BlockItem> BROKEN_MACHINE = ITEMS.registerSimpleBlockItem(AboveBlocks.BROKEN_MACHINE);
    public static final DeferredItem<BlockItem> DOOR = ITEMS.registerSimpleBlockItem(AboveBlocks.DOOR);
    public static final DeferredItem<BlockItem> DOOR_CORE = ITEMS.registerSimpleBlockItem(AboveBlocks.DOOR_CORE);
    public static final DeferredItem<BlockItem> LOG_TERMINAL = ITEMS.registerSimpleBlockItem(AboveBlocks.LOG_TERMINAL);
    public static final DeferredItem<BlockItem> RING_FRAME = ITEMS.registerSimpleBlockItem(AboveBlocks.RING_FRAME);
    public static final DeferredItem<BlockItem> RETURN_PAD = ITEMS.registerSimpleBlockItem(AboveBlocks.RETURN_PAD);
    public static final DeferredItem<BlockItem> FABRICATOR = ITEMS.registerSimpleBlockItem(AboveBlocks.FABRICATOR);
    public static final Map<MachineType, DeferredItem<BlockItem>> MACHINES = new EnumMap<>(MachineType.class);

    static {
        for (MachineType t : MachineType.values()) {
            MACHINES.put(t, ITEMS.registerSimpleBlockItem(AboveBlocks.MACHINES.get(t), p -> p.rarity(Rarity.UNCOMMON)));
        }
    }

    public static Item machine(MachineType t) {
        return MACHINES.get(t).get();
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(AboveItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        event.accept(SCANNER.get());
        event.accept(SCRAP.get());
        event.accept(CIRCUIT.get());
        event.accept(ALLOY.get());
        event.accept(CELL.get());
        event.accept(FABRICATOR.get());
        for (var m : MACHINES.values()) event.accept(m.get());
        event.accept(RING_FRAME.get());
        event.accept(FRAME_HELMET.get());
        event.accept(FRAME_CHEST.get());
        event.accept(FRAME_LEGS.get());
        event.accept(FRAME_BOOTS.get());
        for (var m : MODULES.values()) event.accept(m.get());
        event.accept(PLATING.get());
        event.accept(PANEL.get());
        event.accept(TRIM.get());
        event.accept(GLOW_PANEL.get());
        event.accept(WARNING_LIGHT.get());
        event.accept(VOID_GLASS.get());
        event.accept(MEMBRANE.get());
        event.accept(BROKEN_MACHINE.get());
        event.accept(LOG_TERMINAL.get());
        event.accept(DOOR.get());
        event.accept(DOOR_CORE.get());
        event.accept(RETURN_PAD.get());
    }
}
