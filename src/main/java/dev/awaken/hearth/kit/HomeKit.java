package dev.awaken.hearth.kit;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.core.api.CityHooks;
import dev.awaken.hearth.Later;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The climbable half of Carillon's kit, craftable (SPEC §28.1: "the kit craftable"), so a home can be a movement
 * course: drainpipes, trellises, window boxes, wall ivy and hoist beams as items, placed against a wall from the side
 * you click, and dropped again when you break one in the wild.
 *
 * <p>The blocks are Carillon's (module {@code city}, no items, no loot) and stay as they are; this names them by id and
 * their states by property name, so hearth takes no class from city. Their FACING is a ladder's: it points <em>away</em>
 * from the wall the piece hangs on (the models are drawn for facing=north with the wall at z 16), so the state is the
 * face you click on a wall. Carillon's own pieces never drop (the city is civic), whoever breaks them.
 */
public final class HomeKit {
    private HomeKit() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    /** The five climbable pieces: item id = block id. */
    public enum Piece {
        DRAINPIPE("drainpipe"), TRELLIS("trellis"), FLOWER_BOX("flower_box"), WALL_IVY("wall_ivy"), HOIST_BEAM("hoist_beam");

        public final String id;
        final DeferredBlock<Block> block;

        Piece(String id) {
            this.id = id;
            this.block = DeferredBlock.createBlock(Awaken.id(id));
        }

        /** Which piece a block is, if it is one. */
        static @Nullable Piece of(Block b) {
            for (Piece p : ALL) if (p.block.get() == b) return p;
            return null;
        }

        static @Nullable Piece of(BlockState s) {
            return of(s.getBlock());
        }
    }

    private static final Piece[] ALL = Piece.values();

    /** The item of each piece. */
    public static final Map<Piece, DeferredItem<KitItem>> ITEM = items();

    private static Map<Piece, DeferredItem<KitItem>> items() {
        Map<Piece, DeferredItem<KitItem>> m = new EnumMap<>(Piece.class);
        for (Piece p : ALL) m.put(p, ITEMS.registerItem(p.id, props -> new KitItem(p, props)));
        return m;
    }

    /** Flower boxes' blooms, in the order the creative tab shows them (the property's own values, by name). */
    private static final String[] BLOOMS = {"red", "yellow", "blue", "pink", "white", "herbs", "none"};
    /** What a box with no flower of its own gets, by where it hangs, so a row never matches (herbs and bare are crafted). */
    private static final String[] SEEDED = {"red", "yellow", "blue", "pink", "white"};

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(HomeKit::creativeTab);
        NeoForge.EVENT_BUS.addListener(HomeKit::onDrops);
        HomeKitTests.register();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (Piece p : ALL) {
            if (p == Piece.FLOWER_BOX) {
                for (String bloom : BLOOMS) e.accept(box(bloom));
            } else {
                e.accept(ITEM.get(p).get());
            }
        }
    }

    /** A window box with this bloom (the bloom rides on the item as a block state, which placing applies). */
    public static ItemStack box(String bloom) {
        ItemStack s = new ItemStack(ITEM.get(Piece.FLOWER_BOX).get());
        s.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("bloom", bloom)));
        return s;
    }

    // ---- states, by property name ----------------------------------------------------------------------------------

    /** The state with a property set from its value's name; the state itself when the block has no such property or value. */
    static BlockState with(BlockState s, String property, String value) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(property);
        return p == null ? s : set(s, p, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState s, Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

    /** The name of a property's value in a state ("" when the block has no such property). */
    static String name(BlockState s, String property) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(property);
        return p == null ? "" : nameOf(s, p);
    }

    private static <T extends Comparable<T>> String nameOf(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    static Direction facing(BlockState s) {
        return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    // ---- drainpipes: a column is a shoe, pipes and a head ----------------------------------------------------------

    private static boolean joins(Level level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.is(Piece.DRAINPIPE.block.get()) && facing(s) == facing;
    }

    /** The part a pipe at {@code pos} should be: the foot of its column the shoe, the top the head, a lone one a plain pipe. */
    static String part(Level level, BlockPos pos, Direction facing) {
        boolean up = joins(level, pos.above(), facing), down = joins(level, pos.below(), facing);
        if (up && down) return "pipe";
        if (up) return "shoe";
        return down ? "head" : "pipe";
    }

    /** Puts a pipe's part right for its column now (after a neighbour came or went); anything else there is left alone. */
    static void refit(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (!s.is(Piece.DRAINPIPE.block.get())) return;
        BlockState want = with(s, "part", part(level, pos, facing(s)));
        if (want != s) level.setBlock(pos, want, Block.UPDATE_CLIENTS);
    }

    // ---- the item ----------------------------------------------------------------------------------------------------

    /** A kit piece in hand: works out its own placed state, the kit's blocks having no placement logic of theirs. */
    public static final class KitItem extends BlockItem {
        private final Piece piece;

        KitItem(Piece piece, Item.Properties props) {
            super(piece.block.get(), props);
            this.piece = piece;
        }

        public Piece piece() {
            return piece;
        }

        @Override
        protected @Nullable BlockState getPlacementState(BlockPlaceContext ctx) {
            Level level = ctx.getLevel();
            BlockPos pos = ctx.getClickedPos();
            for (Direction facing : candidates(ctx)) {
                if (!supported(level, pos, facing)) continue;
                BlockState s = stateFor(ctx, facing);
                if (canPlace(ctx, s)) return s;
            }
            return null;
        }

        /**
         * The facings to try, best first: the one you continue (a click on the top or bottom of the same piece), the face
         * you clicked on a wall, then a ladder's rule (the way you look, so a click on a floor or ceiling still finds the
         * wall in front of you).
         */
        private List<Direction> candidates(BlockPlaceContext ctx) {
            List<Direction> out = new ArrayList<>(6);
            Direction face = ctx.getClickedFace();
            // the block clicked: the cell itself when it was replaced (long grass), else the one behind the clicked face
            BlockPos hitPos = ctx.replacingClickedOnBlock() ? ctx.getClickedPos() : ctx.getClickedPos().relative(face.getOpposite());
            BlockState hit = ctx.getLevel().getBlockState(hitPos);
            if (face.getAxis() == Direction.Axis.Y && hit.is(getBlock())) addOnce(out, facing(hit));
            if (face.getAxis().isHorizontal()) addOnce(out, face);
            if (ctx.getPlayer() != null) {
                for (Direction look : ctx.getNearestLookingDirections()) {
                    if (look.getAxis().isHorizontal()) addOnce(out, look.getOpposite());
                }
            }
            return out;
        }

        private static void addOnce(List<Direction> out, Direction d) {
            if (!out.contains(d)) out.add(d);
        }

        /** Something to hang on: a wall that is solid on this side; a pipe also off the next pipe, a beam off the last beam. */
        private boolean supported(Level level, BlockPos pos, Direction facing) {
            BlockPos behind = pos.relative(facing.getOpposite());
            BlockState b = level.getBlockState(behind);
            if (b.isFaceSturdy(level, behind, facing)) return true;
            return switch (piece) {
                case DRAINPIPE -> joins(level, pos.above(), facing) || joins(level, pos.below(), facing);
                case HOIST_BEAM -> b.is(getBlock()) && facing(b) == facing;
                default -> false;
            };
        }

        private BlockState stateFor(BlockPlaceContext ctx, Direction facing) {
            Level level = ctx.getLevel();
            BlockPos pos = ctx.getClickedPos();
            BlockState s = getBlock().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            switch (piece) {
                case DRAINPIPE -> s = with(s, "part", part(level, pos, facing));
                case TRELLIS -> s = with(s, "ivy", "true");
                case FLOWER_BOX -> {
                    s = with(s, "style", "wood");
                    s = with(s, "bloom", SEEDED[seeded(pos)]);
                    // a box crafted with its flower keeps that bloom
                    s = ctx.getItemInHand().getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(s);
                }
                case WALL_IVY -> {
                    s = with(s, "density", "1");
                    s = with(s, "tone", "green");
                }
                case HOIST_BEAM -> s = with(s, "pulley", "true");
            }
            return s;
        }

        @Override
        public void onBlockPlacedBy(BlockPlaceContext ctx, Level level, BlockPos pos, BlockState state, @Nullable Player placer, ItemStack stack) {
            if (!(level instanceof ServerLevel sl)) return;
            switch (piece) {
                case DRAINPIPE -> {
                    refit(sl, pos.above());
                    refit(sl, pos.below());
                }
                case HOIST_BEAM -> {
                    // a beam grown out of a beam: the pulley stays at the tip
                    BlockPos behind = pos.relative(facing(state).getOpposite());
                    BlockState b = level.getBlockState(behind);
                    if (b.is(getBlock()) && facing(b) == facing(state)) sl.setBlock(behind, with(b, "pulley", "false"), Block.UPDATE_CLIENTS);
                }
                default -> {}
            }
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable("item.awaken." + piece.id + ".tip").withStyle(ChatFormatting.GRAY));
        }
    }

    /** A stable pick by position, so a row of boxes never matches. */
    static int seeded(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        return (int) Math.floorMod(h, (long) SEEDED.length);
    }

    // ---- breaking ----------------------------------------------------------------------------------------------------

    /** Inside Carillon (its walls, or its reserve around them): the city's own blocks, which never drop. */
    static boolean inCarillon(Level level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD && (CityHooks.inCity(level, pos) || CityBasin.reserved(pos.getX(), pos.getZ()));
    }

    /** After a pipe or a beam went from {@code at}: its column's ends, or the beam behind it, are right again. */
    static void tidy(ServerLevel level, BlockState was, BlockPos at) {
        if (was.is(Piece.DRAINPIPE.block.get())) {
            refit(level, at.above());
            refit(level, at.below());
        } else if (was.is(Piece.HOIST_BEAM.block.get())) {
            Direction f = facing(was);
            BlockPos behind = at.relative(f.getOpposite());
            if (!level.isLoaded(behind)) return;
            BlockState b = level.getBlockState(behind), ahead = level.getBlockState(behind.relative(f));
            boolean carries = ahead.is(Piece.HOIST_BEAM.block.get()) && facing(ahead) == f;
            if (b.is(Piece.HOIST_BEAM.block.get()) && facing(b) == f && !carries) level.setBlock(behind, with(b, "pulley", "true"), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * A piece broken by a player in the wild gives itself back (the blocks have no loot table). A broken pipe or beam
     * also tidies what it joined a tick later, once it is gone.
     */
    static void onDrops(BlockDropsEvent e) {
        Piece piece = Piece.of(e.getState());
        if (piece == null) return;
        ServerLevel level = e.getLevel();
        BlockPos pos = e.getPos();
        if (inCarillon(level, pos)) return;
        if (piece == Piece.DRAINPIPE || piece == Piece.HOIST_BEAM) {
            BlockPos at = pos.immutable();
            BlockState was = e.getState();
            Later.run(level.getServer(), 1, () -> tidy(level, was, at));
        }
        if (!(e.getBreaker() instanceof Player) || !level.getGameRules().get(GameRules.BLOCK_DROPS)) return;
        ItemStack stack = piece == Piece.FLOWER_BOX ? box(name(e.getState(), "bloom")) : new ItemStack(ITEM.get(piece).get());
        RandomSource r = level.getRandom();
        // where a block's own drops pop from
        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5 + Mth.nextDouble(r, -0.25, 0.25),
                pos.getY() + 0.375 + Mth.nextDouble(r, -0.25, 0.25), pos.getZ() + 0.5 + Mth.nextDouble(r, -0.25, 0.25), stack);
        drop.setDefaultPickUpDelay();
        e.getDrops().add(drop);
    }
}
