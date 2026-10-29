package dev.awaken.kin.works;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * The works' blocks (SPEC §43.12): each work's rare thing as a block you set in a room (a vent stone for the Smeltery, a
 * Chime Perch for the Rookery, the Crucible for the Foundry, the Drop and the Ledger for the Storehouse), the Work Plan
 * the Thatchbacks build from, the rot in a Pondling's pond, and the keystone from an empty town's granary.
 */
public final class WorksContent {
    private WorksContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    /** A stone from a hot spring's edge, warm through and steaming: the Smeltery's heart. */
    public static final DeferredBlock<VentStoneBlock> VENT_STONE = BLOCKS.registerBlock("vent_stone", VentStoneBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.5F, 6F).sound(SoundType.BASALT).lightLevel(s -> 7));
    public static final DeferredItem<BlockItem> VENT_STONE_ITEM = ITEMS.registerSimpleBlockItem(VENT_STONE, p -> p.rarity(Rarity.UNCOMMON));
    /** The Deep's rot, souring a Pondling's pond from the bottom. Clearing it is the help; it drops nothing. */
    public static final DeferredBlock<Block> POND_ROT = BLOCKS.registerSimpleBlock("pond_rot",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.MUD).noLootTable());
    /** A perch with a woken bell's clapper hung in it: Chimewrens roost round it and sing what they hear. */
    public static final DeferredBlock<ChimePerchBlock> CHIME_PERCH = BLOCKS.registerBlock("chime_perch", ChimePerchBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> CHIME_PERCH_ITEM = ITEMS.registerSimpleBlockItem(CHIME_PERCH, p -> p.rarity(Rarity.UNCOMMON));
    /** A woken bell's clapper: every sleeping bell you wake gives one up. */
    public static final DeferredItem<Item> WOKEN_CLAPPER = ITEMS.registerSimpleItem("woken_clapper", p -> p.rarity(Rarity.UNCOMMON));
    /** The Foundry's crucible: bell bronze and Deepglass. Lit while a bell is being cast. */
    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = BLOCKS.registerBlock("foundry_crucible", CrucibleBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).strength(4F, 8F).sound(SoundType.COPPER).noOcclusion()
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 12 : 0).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<BlockItem> CRUCIBLE_ITEM = ITEMS.registerSimpleBlockItem(CRUCIBLE, p -> p.rarity(Rarity.RARE));
    /** The keystone over an empty town's granary door: what the Storehouse's Drop is built round. */
    public static final DeferredBlock<Block> GRANARY_KEYSTONE = BLOCKS.registerSimpleBlock("granary_keystone",
            p -> p.mapColor(MapColor.SAND).strength(2F, 6F).sound(SoundType.STONE));
    public static final DeferredItem<BlockItem> GRANARY_KEYSTONE_ITEM = ITEMS.registerSimpleBlockItem(GRANARY_KEYSTONE, p -> p.rarity(Rarity.UNCOMMON));
    /** The Storehouse's sunken bin: tip anything in and the Totters shelve it. */
    public static final DeferredBlock<DropBlock> DROP = BLOCKS.registerBlock("storehouse_drop", DropBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<BlockItem> DROP_ITEM = ITEMS.registerSimpleBlockItem(DROP, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** The Storehouse's Ledger desk: look anything up and a Totter fetches it. */
    public static final DeferredBlock<LedgerDeskBlock> LEDGER = BLOCKS.registerBlock("ledger_desk", LedgerDeskBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> LEDGER_ITEM = ITEMS.registerSimpleBlockItem(LEDGER);
    /** A work's plan, pegged out where it's to stand: the Thatchbacks build it from what you leave in the chest by it. */
    public static final DeferredBlock<WorkPlanBlock> WORK_PLAN = BLOCKS.registerBlock("work_plan", WorkPlanBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1F).sound(SoundType.SCAFFOLDING).noOcclusion());
    public static final DeferredItem<BlockItem> WORK_PLAN_ITEM = ITEMS.registerSimpleBlockItem(WORK_PLAN);

    public static final Supplier<BlockEntityType<DropBlockEntity>> DROP_BE = BLOCK_ENTITIES.register("storehouse_drop",
            () -> new BlockEntityType<>(DropBlockEntity::new, DROP.get()));
    public static final Supplier<BlockEntityType<WorkPlanBlockEntity>> WORK_PLAN_BE = BLOCK_ENTITIES.register("work_plan",
            () -> new BlockEntityType<>(WorkPlanBlockEntity::new, WORK_PLAN.get()));

    /** Things landing on the Drop's heap, each kind with its own sound. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_METAL = sound("works.land_metal"), LAND_WOOD = sound("works.land_wood"),
            LAND_GEM = sound("works.land_gem"), LAND_SOFT = sound("works.land_soft"), LAND_STONE = sound("works.land_stone"),
            SHELVED = sound("works.shelved"), BAY_FULL = sound("works.bay_full"), CAST = sound("works.cast");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
    }
}
