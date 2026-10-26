package dev.awaken.city.kit;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

import static dev.awaken.city.kit.CityKit.BLOCKS;

/**
 * The commons kit's registrations (Milestone 4, the Commons step), on the city kit's register. Art from
 * tools/art/specs/city_commons.py. None drops anything, none has a block entity, none gives light.
 */
public final class CommonsKit {
    private CommonsKit() {}

    public static final DeferredBlock<CommonsBlocks.Milestone> MILESTONE = BLOCKS.registerBlock("milestone", CommonsBlocks.Milestone::new,
            p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f).sound(SoundType.STONE).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<CommonsBlocks.Fingerpost> FINGERPOST = BLOCKS.registerBlock("fingerpost", CommonsBlocks.Fingerpost::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<CommonsBlocks.Scarecrow> SCARECROW = BLOCKS.registerBlock("scarecrow", CommonsBlocks.Scarecrow::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.8f).sound(SoundType.GRASS).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<CommonsBlocks.Stook> STOOK = BLOCKS.registerBlock("stook", CommonsBlocks.Stook::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).strength(0.5f).sound(SoundType.GRASS).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.POPPED).forceSolidOff());

    /** Loads the class (its blocks join the kit's register before the register goes to the bus). */
    static void init() {}
}
