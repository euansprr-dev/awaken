package dev.awaken.terrain.fault;

import dev.awaken.terrain.block.BlockSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * Shattered Highlands' rock (docs/modules/lands.md, "Shattered Highlands"): faultstone (dark violet slate split by
 * cyan-violet light), the fault seam (crystal split open on light, the line's glowing core) and riven shale (the
 * layered dark rock of the slopes and the floating shards). The light is model emission only, never block light, so
 * a fault costs worldgen no lighting work.
 */
public final class FaultBlocks {
    private FaultBlocks() {}

    private static final BlockSet SET = new BlockSet();

    public static final DeferredBlock<Block> FAULTSTONE = SET.withItem(SET.blocks.registerSimpleBlock("faultstone",
            p -> p.mapColor(MapColor.TERRACOTTA_PURPLE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.8F, 6.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> FAULT_SEAM = SET.withItem(SET.blocks.registerSimpleBlock("fault_seam",
            p -> p.mapColor(MapColor.DIAMOND).instrument(NoteBlockInstrument.CHIME).strength(1.5F, 6.0F)
                    .sound(SoundType.AMETHYST).requiresCorrectToolForDrops().emissiveRendering(s -> true)));
    public static final DeferredBlock<RotatedPillarBlock> RIVEN_SHALE = SET.withItem(SET.blocks.registerBlock("riven_shale", RotatedPillarBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_BLUE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 6.0F)
                    .sound(SoundType.TUFF).requiresCorrectToolForDrops()));

    /** Every Shattered Highlands block, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(FAULTSTONE, FAULT_SEAM, RIVEN_SHALE);
    }

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
