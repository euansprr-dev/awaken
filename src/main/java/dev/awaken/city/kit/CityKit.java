package dev.awaken.city.kit;

import dev.awaken.Awaken;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

/**
 * The Carillon kit (look bible §8): the city's own blocks, so the fabric stops reading as a vanilla build. Milestone 4
 * step B1 registers what facades and roofs need (roof tiles, window boxes, shutters, downpipes, trellis, lit windows,
 * loading doors, hoist beam and rope, chimney crowns); B3 adds the street life. Textures, models and blockstates come
 * from tools/art/specs/city_kit.py. None of them drops anything (the city is civic) and none has a block entity.
 */
public final class CityKit {
    private CityKit() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);

    private static UnaryOperator<BlockBehaviour.Properties> stone(MapColor c, SoundType s) {
        return p -> p.mapColor(c).strength(2.0f, 6.0f).sound(s).requiresCorrectToolForDrops().noLootTable();
    }

    // roofs: slate in Bellwatch (lighter and bluer than deepslate, so the old town stops being one dark mass), clay in the Market
    public static final DeferredBlock<Block> SLATE_TILES = BLOCKS.registerSimpleBlock("slate_tiles", stone(MapColor.COLOR_LIGHT_GRAY, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<StairBlock> SLATE_TILE_STAIRS = BLOCKS.registerBlock("slate_tile_stairs",
            p -> new StairBlock(SLATE_TILES.get().defaultBlockState(), p), stone(MapColor.COLOR_LIGHT_GRAY, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<SlabBlock> SLATE_TILE_SLAB = BLOCKS.registerBlock("slate_tile_slab", SlabBlock::new,
            stone(MapColor.COLOR_LIGHT_GRAY, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<Block> CLAY_TILES = BLOCKS.registerSimpleBlock("clay_tiles", stone(MapColor.TERRACOTTA_ORANGE, SoundType.DECORATED_POT));
    public static final DeferredBlock<StairBlock> CLAY_TILE_STAIRS = BLOCKS.registerBlock("clay_tile_stairs",
            p -> new StairBlock(CLAY_TILES.get().defaultBlockState(), p), stone(MapColor.TERRACOTTA_ORANGE, SoundType.DECORATED_POT));
    public static final DeferredBlock<SlabBlock> CLAY_TILE_SLAB = BLOCKS.registerBlock("clay_tile_slab", SlabBlock::new,
            stone(MapColor.TERRACOTTA_ORANGE, SoundType.DECORATED_POT));

    // facades
    public static final DeferredBlock<KitBlocks.FlowerBox> FLOWER_BOX = BLOCKS.registerBlock("flower_box", KitBlocks.FlowerBox::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().noLootTable().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<KitBlocks.Shutter> SHUTTER = BLOCKS.registerBlock("shutter", KitBlocks.Shutter::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().noCollision().noLootTable());
    public static final DeferredBlock<KitBlocks.Drainpipe> DRAINPIPE = BLOCKS.registerBlock("drainpipe", KitBlocks.Drainpipe::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.IRON).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<KitBlocks.Trellis> TRELLIS = BLOCKS.registerBlock("trellis", KitBlocks.Trellis::new,
            p -> p.mapColor(MapColor.PLANT).strength(0.6f).sound(SoundType.LADDER).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<KitBlocks.LitWindow> LIT_WINDOW = BLOCKS.registerBlock("lit_window", KitBlocks.LitWindow::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.GLASS).noLootTable());
    public static final DeferredBlock<KitBlocks.LoadingDoor> LOADING_DOOR = BLOCKS.registerBlock("loading_door", KitBlocks.LoadingDoor::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noLootTable());
    public static final DeferredBlock<KitBlocks.HoistBeam> HOIST_BEAM = BLOCKS.registerBlock("hoist_beam", KitBlocks.HoistBeam::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.0f).sound(SoundType.WOOD).noOcclusion().noLootTable());
    public static final DeferredBlock<KitBlocks.Rope> ROPE = BLOCKS.registerBlock("rope", KitBlocks.Rope::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.4f).sound(SoundType.WOOL).noOcclusion().noCollision().noLootTable());
    public static final DeferredBlock<KitBlocks.ChimneyBricks> CHIMNEY_BRICKS = BLOCKS.registerBlock("chimney_bricks", KitBlocks.ChimneyBricks::new,
            stone(MapColor.TERRACOTTA_RED, SoundType.STONE));

    // walls (decision 27: weighted texture variants, so walls never tile)
    public static final DeferredBlock<KitBlocks.Plaster> PLASTER = BLOCKS.registerBlock("plaster", KitBlocks.Plaster::new,
            p -> p.mapColor(MapColor.TERRACOTTA_WHITE).strength(1.5f, 6.0f).sound(SoundType.CALCITE).noLootTable());
    public static final DeferredBlock<KitBlocks.Timber> TIMBER = BLOCKS.registerBlock("timber", KitBlocks.Timber::new,
            p -> p.mapColor(MapColor.COLOR_BROWN).strength(2.0f).sound(SoundType.WOOD).noLootTable());
    public static final DeferredBlock<Block> CITY_BRICKS = BLOCKS.registerSimpleBlock("city_bricks", stone(MapColor.COLOR_RED, SoundType.STONE));
    public static final DeferredBlock<Block> ASHLAR = BLOCKS.registerSimpleBlock("ashlar", stone(MapColor.STONE, SoundType.STONE));

    // a ladder hung from a ladder (the 3D ladder's model; drops a ladder)
    public static final DeferredBlock<KitBlocks.HangingLadder> HANGING_LADDER = BLOCKS.registerBlock("hanging_ladder", KitBlocks.HangingLadder::new,
            p -> p.forceSolidOff().strength(0.4f).sound(SoundType.LADDER).noOcclusion().pushReaction(PushReaction.POPPED));

    public static void register(IEventBus modBus) {
        StreetKit.init(); // B3a's street kit joins this register first
        BLOCKS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CityKit::hangLadder);
    }

    /**
     * Using a ladder on a ladder's bottom (or top) face where no wall would take one: hangs a ladder there, facing the
     * same way (a ladder against a wall is vanilla's own placement, left alone).
     */
    static void hangLadder(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        var stack = e.getItemStack();
        net.minecraft.core.Direction face = e.getFace();
        if (face == null || face.getAxis() != net.minecraft.core.Direction.Axis.Y || !stack.is(net.minecraft.world.item.Items.LADDER)) return;
        var level = e.getLevel();
        var target = level.getBlockState(e.getPos());
        if (!(target.is(net.minecraft.world.level.block.Blocks.LADDER) || target.getBlock() instanceof KitBlocks.HangingLadder)) return;
        net.minecraft.core.Direction facing = target.getValue(net.minecraft.world.level.block.LadderBlock.FACING);
        net.minecraft.core.BlockPos at = e.getPos().relative(face);
        var there = level.getBlockState(at);
        if (!there.canBeReplaced()) return;
        var ladder = net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, facing);
        if (ladder.canSurvive(level, at)) return; // a wall there: vanilla places its own ladder
        var hung = HANGING_LADDER.get().defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.LadderBlock.WATERLOGGED, level.getFluidState(at).is(net.minecraft.world.level.material.Fluids.WATER));
        if (!hung.canSurvive(level, at)) return;
        if (!level.isClientSide()) {
            level.setBlock(at, hung, Block.UPDATE_ALL);
            SoundType st = SoundType.LADDER;
            level.playSound(null, at, st.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, (st.getVolume() + 1.0f) / 2.0f, st.getPitch() * 0.8f);
            level.gameEvent(e.getEntity(), net.minecraft.world.level.gameevent.GameEvent.BLOCK_PLACE, at);
            if (!e.getEntity().hasInfiniteMaterials()) stack.shrink(1);
        }
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
}
