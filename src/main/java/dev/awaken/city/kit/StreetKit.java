package dev.awaken.city.kit;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.function.UnaryOperator;

import static dev.awaken.city.kit.CityKit.BLOCKS;

/**
 * The street kit's registrations (Milestone 4 step B3a: look bible §7, SPEC decision 27): stalls, wares, awnings,
 * signs, lines, banners, carts and the furniture of the streets, on the city kit's register. Art from
 * tools/art/specs/city_street.py. None drops anything (the city is civic), none has a block entity, none emits light.
 */
public final class StreetKit {
    private StreetKit() {}

    private static UnaryOperator<BlockBehaviour.Properties> wood(MapColor c, float strength) {
        return p -> p.mapColor(c).strength(strength).sound(SoundType.WOOD).noOcclusion().noLootTable();
    }

    private static UnaryOperator<BlockBehaviour.Properties> cloth(MapColor c) {
        return p -> p.mapColor(c).strength(0.3f).sound(SoundType.WOOL).noOcclusion().noLootTable().pushReaction(PushReaction.POPPED);
    }

    private static UnaryOperator<BlockBehaviour.Properties> iron() {
        return p -> p.mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.IRON).noOcclusion().noLootTable().forceSolidOff();
    }

    // canvas: a hold at the hood row and a soft landing that bounces like a bed (runner_ledges, runner_soft)
    public static final DeferredBlock<StreetBlocks.Awning> AWNING = BLOCKS.registerBlock("awning", StreetBlocks.Awning::new,
            p -> p.mapColor(MapColor.WOOL).strength(0.5f).sound(SoundType.WOOL).noOcclusion().noLootTable()
                    .bounceRestitution(0.75f).fallDistanceReduction(0.5f).forceSolidOff());
    public static final DeferredBlock<StreetBlocks.SignBracket> SIGN_BRACKET = BLOCKS.registerBlock("sign_bracket", StreetBlocks.SignBracket::new, iron());
    public static final DeferredBlock<StreetBlocks.Signboard> SIGNBOARD = BLOCKS.registerBlock("signboard", StreetBlocks.Signboard::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.6f).sound(SoundType.HANGING_SIGN).noOcclusion().noCollision().noLootTable().forceSolidOff());
    public static final DeferredBlock<StreetBlocks.HangLine> HANG_LINE = BLOCKS.registerBlock("hang_line", StreetBlocks.HangLine::new,
            p -> p.mapColor(MapColor.WOOL).strength(0.4f).sound(SoundType.WOOL).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<StreetBlocks.LinePole> LINE_POLE = BLOCKS.registerBlock("line_pole", StreetBlocks.LinePole::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<StreetBlocks.BannerCloth> BANNER_CLOTH = BLOCKS.registerBlock("banner_cloth", StreetBlocks.BannerCloth::new,
            p -> cloth(MapColor.WOOL).apply(p).noCollision().forceSolidOff());
    public static final DeferredBlock<StreetBlocks.Flagpole> FLAGPOLE = BLOCKS.registerBlock("flagpole", StreetBlocks.Flagpole::new,
            p -> wood(MapColor.WOOD, 1.0f).apply(p).forceSolidOff());

    // goods and stalls
    public static final DeferredBlock<StreetBlocks.Crate> CRATE = BLOCKS.registerBlock("crate", StreetBlocks.Crate::new, wood(MapColor.WOOD, 1.0f));
    public static final DeferredBlock<StreetBlocks.Sack> SACK = BLOCKS.registerBlock("sack", StreetBlocks.Sack::new, cloth(MapColor.COLOR_BROWN));
    public static final DeferredBlock<StreetBlocks.Wares> WARES = BLOCKS.registerBlock("wares", StreetBlocks.Wares::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.3f).sound(SoundType.WOOD).noOcclusion().noCollision().noLootTable()
                    .pushReaction(PushReaction.POPPED).forceSolidOff());
    public static final DeferredBlock<StreetBlocks.StallCounter> STALL_COUNTER = BLOCKS.registerBlock("stall_counter", StreetBlocks.StallCounter::new,
            wood(MapColor.WOOD, 1.5f));
    public static final DeferredBlock<StreetBlocks.Cask> CASK = BLOCKS.registerBlock("cask", StreetBlocks.Cask::new, wood(MapColor.WOOD, 1.5f));
    public static final DeferredBlock<StreetBlocks.Cart> CART = BLOCKS.registerBlock("cart", StreetBlocks.Cart::new, wood(MapColor.WOOD, 1.5f));
    public static final DeferredBlock<StreetBlocks.HayCart> HAY_CART = BLOCKS.registerBlock("hay_cart", StreetBlocks.HayCart::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).strength(1.0f).sound(SoundType.GRASS).noOcclusion().noLootTable());
    public static final DeferredBlock<StreetBlocks.Produce> PRODUCE = BLOCKS.registerBlock("produce", StreetBlocks.Produce::new, wood(MapColor.WOOD, 0.8f));
    public static final DeferredBlock<StreetBlocks.GoodsShelf> GOODS_SHELF = BLOCKS.registerBlock("goods_shelf", StreetBlocks.GoodsShelf::new, wood(MapColor.WOOD, 1.0f));

    // furniture
    public static final DeferredBlock<StreetBlocks.Bench> BENCH = BLOCKS.registerBlock("bench", StreetBlocks.Bench::new, wood(MapColor.WOOD, 1.5f));
    public static final DeferredBlock<StreetBlocks.MarketTable> MARKET_TABLE = BLOCKS.registerBlock("market_table", StreetBlocks.MarketTable::new, wood(MapColor.WOOD, 1.5f));
    public static final DeferredBlock<StreetBlocks.Planter> PLANTER = BLOCKS.registerBlock("planter", StreetBlocks.Planter::new, wood(MapColor.WOOD, 1.5f));
    public static final DeferredBlock<StreetBlocks.WallLantern> WALL_LANTERN = BLOCKS.registerBlock("wall_lantern", StreetBlocks.WallLantern::new,
            p -> iron().apply(p).sound(SoundType.LANTERN).noCollision().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<StreetBlocks.NoticeBoard> NOTICE_BOARD = BLOCKS.registerBlock("notice_board", StreetBlocks.NoticeBoard::new, wood(MapColor.WOOD, 1.0f));
    public static final DeferredBlock<StreetBlocks.FishingNet> FISHING_NET = BLOCKS.registerBlock("fishing_net", StreetBlocks.FishingNet::new,
            p -> cloth(MapColor.WOOL).apply(p).noCollision().forceSolidOff());
    public static final DeferredBlock<StreetBlocks.WellWindlass> WELL_WINDLASS = BLOCKS.registerBlock("well_windlass", StreetBlocks.WellWindlass::new,
            p -> wood(MapColor.WOOD, 1.0f).apply(p).forceSolidOff());
    public static final DeferredBlock<StreetBlocks.MooringRing> MOORING_RING = BLOCKS.registerBlock("mooring_ring", StreetBlocks.MooringRing::new,
            p -> iron().apply(p).noCollision());

    /** Loads the class (its blocks join the kit's register before the register goes to the bus). */
    static void init() {}
}
