package dev.awaken.terrain.block;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * One Awakened biome's blocks and their items, registered together and shown in the creative tab in the order they
 * were made. The shapes every Awakened tree shares live here (log, planks, leaves, sapling), so each biome's block class
 * only says what is its own.
 */
public final class BlockSet {
    public final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(Awaken.MODID);
    private final DeferredRegister.Items items = DeferredRegister.createItems(Awaken.MODID);
    private final List<DeferredItem<BlockItem>> tab = new ArrayList<>();

    /** Gives the block its item and its place in the tab. */
    public <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block) {
        tab.add(items.registerSimpleBlockItem(block));
        return block;
    }

    /** Gives the block an item of its own kind (a lily's places it on water). */
    public <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block, BiFunction<Block, Item.Properties, ? extends BlockItem> item) {
        tab.add(items.<BlockItem>registerItem(block.getId().getPath(), p -> item.apply(block.get(), p), Item.Properties::useBlockDescriptionPrefix));
        return block;
    }

    /** A log, wood or stripped log: one map colour on its ends (standing), another on its bark. */
    public DeferredBlock<RotatedPillarBlock> log(String name, MapColor top, MapColor side) {
        return withItem(blocks.registerBlock(name, RotatedPillarBlock::new,
                p -> p.mapColor(s -> s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? top : side)
                        .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    public DeferredBlock<Block> planks(String name, MapColor color) {
        return withItem(blocks.registerSimpleBlock(name,
                p -> p.mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava()));
    }

    /** Vanilla leaf behaviour (decay, persistence, waterlogging); no vanilla falling-leaf particles (the lands' own pool owns those). */
    public DeferredBlock<LeavesBlock> leaves(String name, MapColor color) {
        return withItem(blocks.registerBlock(name, p -> new LeavesBlock(AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
                p -> p.mapColor(color).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
                        .isValidSpawn((s, l, pos, type) -> false).isSuffocating((s, l, pos) -> false)
                        .ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, pos) -> false)));
    }

    public DeferredBlock<SaplingBlock> sapling(String name, TreeGrower grower) {
        return withItem(blocks.registerBlock(name, p -> new SaplingBlock(grower, p),
                p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED)));
    }

    public void init(IEventBus modBus) {
        blocks.register(modBus);
        items.register(modBus);
        modBus.addListener(this::creativeTab);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : tab) e.accept(item.get());
    }
}
