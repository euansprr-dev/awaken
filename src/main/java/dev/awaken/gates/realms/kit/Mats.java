package dev.awaken.gates.realms.kit;

import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A land's materials by the job they do, so a kit piece drawn with one land's set comes out in that land's own stone and
 * timber. Most are named the same way for every land (a land called "amber" has amber_stone, amber_stone_worn and so on);
 * what it grows and burns differs too much for that, so it hands those in. A land extends this for anything only it has.
 */
public class Mats {
    /** What makes a land's small details its own: what grows over the stone, what glows, what's scattered about. */
    public record Accents(BlockState comb, BlockState capped, BlockState glow, BlockState drip, BlockState candle, BlockState crumbs, BlockState drum, BlockState fence, BlockState canopy) {}

    public final BlockState stoneA, worn, deep, pale, hexstone, flag, brick, plaster, band, tile, tileDeep, timber, planks, gold, window, moss, bed;
    public final BlockState stoneSlab, stoneStairs, paleSlab, paleStairs, flagSlab, flagStairs, brickSlab, tileStairs, tileSlab, tileDeepStairs, tileDeepSlab, planksSlab, planksStairs, plasterSlab;
    public final BlockState comb, capped, glow, drip, candle, crumbs, drum, fence, canopy;
    public final BlockState rock = Blocks.STONE.defaultBlockState(), bedrock = Blocks.BEDROCK.defaultBlockState(), shroom = Blocks.SHROOMLIGHT.defaultBlockState(),
            chain = Blocks.IRON_CHAIN.defaultBlockState(), lantern = Blocks.LANTERN.defaultBlockState(), hung = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true),
            barrel = Blocks.BARREL.defaultBlockState(), light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
    public final BlockState shrine = b("rest_shrine"), cache = b("gate_cache"), updraft = b("realm_updraft");

    protected Mats(String land, Accents a) {
        stoneA = b(land + "_stone");
        worn = b(land + "_stone_worn");
        deep = b(land + "_stone_deep");
        pale = b(land + "_stone_pale");
        hexstone = b(land + "_hexstone");
        flag = b(land + "_flagstone");
        brick = b(land + "_brick");
        plaster = b(land + "_plaster");
        band = b(land + "_plaster_band");
        tile = b(land + "_tile");
        tileDeep = b(land + "_tile_deep");
        timber = b(land + "_timber");
        planks = b(land + "_planks");
        gold = b(land + "_gilt");
        window = b(land + "_window");
        moss = b(land + "_moss");
        bed = b(land + "_bed");
        stoneSlab = b(land + "_stone_slab");
        stoneStairs = b(land + "_stone_stairs");
        paleSlab = b(land + "_stone_pale_slab");
        paleStairs = b(land + "_stone_pale_stairs");
        flagSlab = b(land + "_flagstone_slab");
        flagStairs = b(land + "_flagstone_stairs");
        brickSlab = b(land + "_brick_slab");
        tileStairs = b(land + "_tile_stairs");
        tileSlab = b(land + "_tile_slab");
        tileDeepStairs = b(land + "_tile_deep_stairs");
        tileDeepSlab = b(land + "_tile_deep_slab");
        planksSlab = b(land + "_planks_slab");
        planksStairs = b(land + "_planks_stairs");
        plasterSlab = b(land + "_plaster_slab");
        comb = a.comb();
        capped = a.capped();
        glow = a.glow();
        drip = a.drip();
        candle = a.candle();
        crumbs = a.crumbs();
        drum = a.drum();
        fence = a.fence();
        canopy = a.canopy();
    }

    protected static BlockState b(String id) {
        return BuiltInRegistries.BLOCK.getValue(Awaken.id(id)).defaultBlockState();
    }
}
