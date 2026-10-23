package dev.awaken.above.ruin;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The ruins' weathering and ornament (docs/modules/structures.md): plating cracked open and streaked with rust, plating
 * gone to moss, a fluted column with brass bands, and a panel carved with a glyph that still glows faintly. Textures from
 * {@code tools/art/specs/structures.py}.
 */
public final class RuinBlocks {
    private RuinBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_BLACK).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
    }

    public static final DeferredBlock<Block> CRACKED = BLOCKS.registerSimpleBlock("ruin_plating_cracked", RuinBlocks::metal);
    public static final DeferredBlock<Block> MOSSY = BLOCKS.registerSimpleBlock("ruin_plating_mossy", p -> metal(p).mapColor(MapColor.COLOR_GREEN));
    public static final DeferredBlock<RotatedPillarBlock> PILLAR = BLOCKS.registerBlock("ruin_pillar", RotatedPillarBlock::new, RuinBlocks::metal);
    public static final DeferredBlock<Block> GLYPH = BLOCKS.registerSimpleBlock("ruin_glyph",
            p -> metal(p).mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 6));

    public static final List<DeferredBlock<? extends Block>> ALL = List.of(CRACKED, MOSSY, PILLAR, GLYPH);
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = ALL.stream().map(ITEMS::registerSimpleBlockItem).toList();

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(RuinBlocks::creativeTab);
        RuinTests.register();
        RuinShowcase.register();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (DeferredItem<BlockItem> item : BLOCK_ITEMS) e.accept(item.get());
    }
}
