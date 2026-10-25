package dev.awaken.citizens.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * Carillon's street lamp head, on the posts M1 painted (where the painter put a lantern). The lamplighter lights it at
 * dusk and puts it out at dawn, so the light really changes; a broken one stays dark until a mason mends it, and a dark
 * lamp at night is where the city lets monsters in (CitizenSpawns). Light 15 only while lit.
 */
public class StreetLampBlock extends Block {
    public enum Lamp implements StringRepresentable {
        OUT, LIT, BROKEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);
    private static final VoxelShape SHAPE = Shapes.or(Block.column(6.0, 0.0, 10.0), Block.column(4.0, 10.0, 12.0));

    public StreetLampBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LAMP, Lamp.OUT));
    }

    public static int light(BlockState s) {
        return s.getValue(LAMP) == Lamp.LIT ? 15 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LAMP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
