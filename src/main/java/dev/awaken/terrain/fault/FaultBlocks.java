package dev.awaken.terrain.fault;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Shattered Highlands' rock (docs/modules/lands.md, "Shattered Highlands"): faultstone (dark violet slate split by
 * cyan-violet light), the fault seam (crystal split open on light, the line's glowing core) and riven shale (the
 * layered dark rock of the slopes and the floating shards). The light is model emission only, never block light, so
 * a fault costs worldgen no lighting work.
 */
public final class FaultBlocks {
    private FaultBlocks() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    private static final List<DeferredItem<BlockItem>> TAB = new ArrayList<>();

    public static final DeferredBlock<Block> FAULTSTONE = withItem(BLOCKS.registerSimpleBlock("faultstone",
            p -> p.mapColor(MapColor.TERRACOTTA_PURPLE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.8F, 6.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> FAULT_SEAM = withItem(BLOCKS.registerSimpleBlock("fault_seam",
            p -> p.mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.CHIME).strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST).requiresCorrectToolForDrops().emissiveRendering(s -> true)));
    public static final DeferredBlock<RotatedPillarBlock> RIVEN_SHALE = withItem(BLOCKS.registerBlock("riven_shale", RotatedPillarBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_BLUE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 6.0F)
                    .sound(SoundType.TUFF).requiresCorrectToolForDrops()));

    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block) {
        TAB.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    /** Every Shattered Highlands block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(FAULTSTONE, FAULT_SEAM, RIVEN_SHALE);
    }

    static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(FaultBlocks::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : TAB) e.accept(item.get());
    }
}
