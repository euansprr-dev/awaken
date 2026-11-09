package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A lode's heart (PLAN 7.3): a crystallised knot of its ore at the seam's centre, faintly lit and glinting. Breaking the
 * heart a lode was planned with opens it ("the vein gives", {@link Veins}); its loot is a lump of the ore.
 * <p>{@link #HOST} is only how it looks (SPEC §42.11): the heart is drawn in the rock it sits in, so a knot of ore shows in the
 * wall and not a dark cube set into it. Tool, hardness, loot and the lode's plan all go by the block, and never read it.
 */
public final class LodeHeartBlock extends Block {
    /** The rock a heart can be drawn in. {@code DEEPSLATE} first: the default, and the look of every heart before this. */
    public enum Host implements StringRepresentable {
        DEEPSLATE("minecraft:deepslate"), STONE("minecraft:stone"), ANDESITE("minecraft:andesite"), DIORITE("minecraft:diorite"),
        GRANITE("minecraft:granite"), TUFF("minecraft:tuff"), ROOTSTONE("awaken:rootstone"), OCHRE("awaken:ochre_stone"),
        LIMESTONE("awaken:limestone"), SLATE("awaken:banded_slate"), SILTSTONE("awaken:siltstone"), GLIMMER("awaken:glimmerstone"),
        BASALT("awaken:column_basalt"), SEEPSTONE("awaken:seepstone");

        private final String block;

        Host(String block) {
            this.block = block;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The look for a heart in this block of rock, or null if it isn't a rock a heart can be drawn in. */
        public static @Nullable Host of(Block rock) {
            return Lookup.BY_BLOCK.get(rock);
        }

        /** Resolved at first use, when every block is registered. By name, so the deep needn't know the caves' classes. */
        private static final class Lookup {
            static final Map<Block, Host> BY_BLOCK = new IdentityHashMap<>();

            static {
                for (Host h : values()) BY_BLOCK.put(BuiltInRegistries.BLOCK.getValue(Identifier.parse(h.block)), h);
            }
        }
    }

    public static final EnumProperty<Host> HOST = EnumProperty.create("host", Host.class);

    public final Ore ore;

    public LodeHeartBlock(BlockBehaviour.Properties p, Ore ore) {
        super(p);
        this.ore = ore;
        registerDefaultState(defaultBlockState().setValue(HOST, Host.DEEPSLATE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(HOST);
    }

    /** A heart set into this rock (the block it replaced); another ore or a built block counts as plain stone or deepslate. */
    public static BlockState in(BlockState heart, BlockState replaced) {
        Host h = Host.of(replaced.getBlock());
        if (h == null) h = replaced.getBlock().getDescriptionId().contains("deepslate") ? Host.DEEPSLATE : Host.STONE;
        return heart.setValue(HOST, h);
    }

    /** The rock most of this cell's neighbours are, or null when none is one a heart can be drawn in. */
    static @Nullable Host around(LevelReader level, BlockPos pos) {
        int[] counts = new int[Host.values().length];
        Host best = null;
        int most = 0;
        for (Direction d : Direction.values()) {
            Host h = Host.of(level.getBlockState(pos.relative(d)).getBlock());
            if (h != null && ++counts[h.ordinal()] > most) {
                most = counts[h.ordinal()];
                best = h;
            }
        }
        return best;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Host h = around(ctx.getLevel(), ctx.getClickedPos());
        return h == null ? defaultBlockState() : defaultBlockState().setValue(HOST, h);
    }

    /** A heart from a world made before it had a rock of its own takes on the one round it (hearts are few; this costs nothing). */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Host h = around(level, pos);
        if (h != null && h != state.getValue(HOST)) level.setBlock(pos, state.setValue(HOST, h), Block.UPDATE_CLIENTS);
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) != 0) return;
        Direction d = Direction.getRandom(r);
        if (level.getBlockState(pos.relative(d)).isSolidRender()) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.52 + (d.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.9 : 0);
        double y = pos.getY() + 0.5 + d.getStepY() * 0.52 + (d.getStepY() == 0 ? (r.nextDouble() - 0.5) * 0.9 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.52 + (d.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.9 : 0);
        // a glint doesn't move: its y speed carries the ore (ordinal + 1), which the client turns into its tint
        level.addParticle(DelveContent.GLINT.get(), x, y, z, 0, ore.ordinal() + 1, 0);
    }
}
