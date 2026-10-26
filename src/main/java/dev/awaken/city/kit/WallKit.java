package dev.awaken.city.kit;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;

import static dev.awaken.city.kit.CityKit.BLOCKS;

/**
 * The wall kit's registrations (Milestone 4, the Walls step), on the city kit's register. Art from
 * tools/art/specs/city_wall.py. None drops anything (the city is civic), none has a block entity; the brazier is the
 * only light source.
 */
public final class WallKit {
    private WallKit() {}

    public static final DeferredBlock<WallBlocks.ArrowLoop> ARROW_LOOP = BLOCKS.registerBlock("arrow_loop", WallBlocks.ArrowLoop::new,
            p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f).sound(SoundType.STONE).noOcclusion().noLootTable());
    public static final DeferredBlock<WallBlocks.WallIvy> WALL_IVY = BLOCKS.registerBlock("wall_ivy", WallBlocks.WallIvy::new,
            p -> p.mapColor(MapColor.PLANT).strength(0.2f).sound(SoundType.VINE).noOcclusion().noCollision().noLootTable()
                    .pushReaction(PushReaction.POPPED).forceSolidOff());
    public static final DeferredBlock<WallBlocks.Portcullis> PORTCULLIS = BLOCKS.registerBlock("portcullis", WallBlocks.Portcullis::new,
            p -> p.mapColor(MapColor.WOOD).strength(5.0f, 6.0f).sound(SoundType.WOOD).noOcclusion().noLootTable());
    public static final DeferredBlock<WallBlocks.Brazier> BRAZIER = BLOCKS.registerBlock("brazier", WallBlocks.Brazier::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f).sound(SoundType.IRON).noOcclusion().noLootTable().lightLevel(s -> 14).forceSolidOff());
    public static final DeferredBlock<WallBlocks.WallSpout> WALL_SPOUT = BLOCKS.registerBlock("wall_spout", WallBlocks.WallSpout::new,
            p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f).sound(SoundType.STONE).noOcclusion().noLootTable().forceSolidOff());
    public static final DeferredBlock<WallBlocks.WallShield> WALL_SHIELD = BLOCKS.registerBlock("wall_shield", WallBlocks.WallShield::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion().noCollision().noLootTable()
                    .pushReaction(PushReaction.POPPED).forceSolidOff());

    /** Loads the class (its blocks join the kit's register before the register goes to the bus). */
    static void init() {}
}
