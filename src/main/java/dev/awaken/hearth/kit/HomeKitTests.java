package dev.awaken.hearth.kit;

import dev.awaken.Awaken;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTest for the craftable kit: each piece places against the wall you click with the facing that hangs it there, a
 * drainpipe column keeps a shoe, pipes and a head as it grows, a flower box keeps its crafted bloom, a beam grown out
 * of a beam keeps its pulley at the tip, nothing floats; a piece broken by a player drops itself, never in Carillon;
 * and every recipe loaded.
 */
final class HomeKitTests {
    private HomeKitTests() {}

    private static final String[] RECIPES = {"home_kit_drainpipe", "home_kit_drainpipe_copper", "home_kit_trellis", "home_kit_wall_ivy",
            "home_kit_hoist_beam", "home_kit_flower_box_red", "home_kit_flower_box_yellow", "home_kit_flower_box_blue",
            "home_kit_flower_box_pink", "home_kit_flower_box_white", "home_kit_flower_box_herbs", "home_kit_flower_box_empty"};

    static void register() {
        AwakenTests.registerIsolated("hearth_home_kit", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 2, origin.getY() + 100, (origin.getZ() & ~15) + 6);
            AwakenTests.clearToSky(level, base.offset(-2, -1, -3), base.offset(14, 8, 14));
            // a wall along x, 5 high, with its north face free (z 0 is the cell in front of it), and a lone pillar
            for (int x = 0; x <= 6; x++) for (int y = 0; y <= 4; y++) level.setBlock(base.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
            BlockPos pillar = base.offset(10, 0, 8);
            level.setBlock(pillar, Blocks.STONE.defaultBlockState(), 3);

            // the pieces are items, named by their blocks' ids, and the blocks know them
            for (HomeKit.Piece p : HomeKit.Piece.values()) {
                var item = BuiltInRegistries.ITEM.getValue(Awaken.id(p.id));
                h.assertTrue(item == HomeKit.ITEM.get(p).get() && p.block.get().asItem() == item, p.id + " is an item and its block's item");
            }

            // a drainpipe column: the first is a plain pipe facing out from the wall (the face you click), then shoe / pipe / head
            h.assertTrue(put(level, HomeKit.Piece.DRAINPIPE, base.offset(1, 0, 1), Direction.NORTH) == InteractionResult.SUCCESS, "a pipe is placed");
            BlockState lone = level.getBlockState(base.offset(1, 0, 0));
            h.assertTrue(HomeKit.Piece.of(lone) == HomeKit.Piece.DRAINPIPE && HomeKit.facing(lone) == Direction.NORTH
                    && HomeKit.name(lone, "part").equals("pipe"), "a lone pipe faces away from the wall: " + lone);
            put(level, HomeKit.Piece.DRAINPIPE, base.offset(1, 1, 1), Direction.NORTH);
            h.assertTrue(part(level, base.offset(1, 0, 0)).equals("shoe") && part(level, base.offset(1, 1, 0)).equals("head"),
                    "two pipes: shoe below, head above: " + part(level, base.offset(1, 0, 0)) + " " + part(level, base.offset(1, 1, 0)));
            put(level, HomeKit.Piece.DRAINPIPE, base.offset(1, 2, 1), Direction.NORTH);
            h.assertTrue(part(level, base.offset(1, 0, 0)).equals("shoe") && part(level, base.offset(1, 1, 0)).equals("pipe")
                    && part(level, base.offset(1, 2, 0)).equals("head"), "three pipes: shoe, pipe, head");
            // clicking the top of the head continues the column the same way
            put(level, HomeKit.Piece.DRAINPIPE, base.offset(1, 2, 0), Direction.UP);
            BlockState fourth = level.getBlockState(base.offset(1, 3, 0));
            h.assertTrue(HomeKit.Piece.of(fourth) == HomeKit.Piece.DRAINPIPE && HomeKit.facing(fourth) == Direction.NORTH
                    && HomeKit.name(fourth, "part").equals("head") && part(level, base.offset(1, 2, 0)).equals("pipe"),
                    "stacked on the top: the new head, the old head a pipe: " + fourth);
            // breaking the middle one tidies the column a tick later (the head keeps its hopper, the foot its shoe)
            BlockState middle = level.getBlockState(base.offset(1, 1, 0));
            level.setBlock(base.offset(1, 1, 0), Blocks.AIR.defaultBlockState(), 3);
            HomeKit.tidy(level, middle, base.offset(1, 1, 0));
            h.assertTrue(part(level, base.offset(1, 0, 0)).equals("pipe") && part(level, base.offset(1, 2, 0)).equals("shoe"),
                    "split column: the foot is a lone pipe, the upper run starts with a shoe");

            // a trellis on an east-facing wall: placed on the east side of the pillar, ivy through it
            h.assertTrue(put(level, HomeKit.Piece.TRELLIS, pillar, Direction.EAST) == InteractionResult.SUCCESS, "a trellis is placed");
            BlockState trellis = level.getBlockState(pillar.east());
            h.assertTrue(HomeKit.Piece.of(trellis) == HomeKit.Piece.TRELLIS && HomeKit.facing(trellis) == Direction.EAST
                    && HomeKit.name(trellis, "ivy").equals("true"), "a trellis faces the way you clicked, with ivy: " + trellis);
            // wall ivy at a middling density
            put(level, HomeKit.Piece.WALL_IVY, base.offset(3, 0, 1), Direction.NORTH);
            BlockState ivy = level.getBlockState(base.offset(3, 0, 0));
            h.assertTrue(HomeKit.Piece.of(ivy) == HomeKit.Piece.WALL_IVY && HomeKit.name(ivy, "density").equals("1")
                    && HomeKit.facing(ivy) == Direction.NORTH, "wall ivy, density 1: " + ivy);
            // a window box keeps the bloom it was crafted with, and an uncrafted one gets one of the plain blooms
            put(level, HomeKit.Piece.FLOWER_BOX, base.offset(2, 0, 1), Direction.NORTH, HomeKit.box("pink"));
            BlockState pink = level.getBlockState(base.offset(2, 0, 0));
            h.assertTrue(HomeKit.Piece.of(pink) == HomeKit.Piece.FLOWER_BOX && HomeKit.name(pink, "bloom").equals("pink")
                    && HomeKit.name(pink, "style").equals("wood"), "a pink wooden box: " + pink);
            put(level, HomeKit.Piece.FLOWER_BOX, base.offset(2, 2, 1), Direction.NORTH);
            BlockState plain = level.getBlockState(base.offset(2, 2, 0));
            h.assertTrue(HomeKit.Piece.of(plain) == HomeKit.Piece.FLOWER_BOX && !HomeKit.name(plain, "bloom").isEmpty()
                    && !HomeKit.name(plain, "bloom").equals("none") && !HomeKit.name(plain, "bloom").equals("herbs"), "a seeded bloom: " + plain);
            // a hoist beam, then one grown out of its tip: the pulley moves to the end
            put(level, HomeKit.Piece.HOIST_BEAM, base.offset(5, 3, 1), Direction.NORTH);
            put(level, HomeKit.Piece.HOIST_BEAM, base.offset(5, 3, 0), Direction.NORTH);
            BlockState root = level.getBlockState(base.offset(5, 3, 0)), tip = level.getBlockState(base.offset(5, 3, -1));
            h.assertTrue(HomeKit.Piece.of(tip) == HomeKit.Piece.HOIST_BEAM && HomeKit.facing(tip) == Direction.NORTH
                    && HomeKit.name(tip, "pulley").equals("true") && HomeKit.name(root, "pulley").equals("false"),
                    "the beam's pulley is at its tip: " + root + " then " + tip);
            // the tip broken: the beam behind it carries the pulley again
            level.setBlock(base.offset(5, 3, -1), Blocks.AIR.defaultBlockState(), 3);
            HomeKit.tidy(level, tip, base.offset(5, 3, -1));
            h.assertTrue(HomeKit.name(level.getBlockState(base.offset(5, 3, 0)), "pulley").equals("true"), "a beam made the tip gets its pulley back");
            // nothing floats: a pipe on the top of a lone pillar finds no wall
            h.assertTrue(put(level, HomeKit.Piece.DRAINPIPE, pillar, Direction.UP) == InteractionResult.FAIL
                    && level.getBlockState(pillar.above()).isAir(), "no wall, no pipe");

            // broken by a player, a piece drops itself (the flower box with its bloom); never in the city, never to no one
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            BlockPos pipe = base.offset(1, 2, 0), box = base.offset(2, 0, 0);
            List<ItemEntity> drops = drops(level, pipe, sp);
            h.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(HomeKit.ITEM.get(HomeKit.Piece.DRAINPIPE).get()), "a broken pipe drops a pipe");
            drops = drops(level, box, sp);
            ItemStack boxStack = drops.isEmpty() ? ItemStack.EMPTY : drops.get(0).getItem();
            var kept = boxStack.get(DataComponents.BLOCK_STATE);
            h.assertTrue(drops.size() == 1 && boxStack.is(HomeKit.ITEM.get(HomeKit.Piece.FLOWER_BOX).get()) && kept != null
                    && "pink".equals(kept.properties().get("bloom")), "a broken box drops with its bloom");
            h.assertTrue(drops(level, pipe, null).isEmpty(), "nobody broke it, nothing drops");
            h.assertTrue(drops(level, base.offset(1, 0, 1), sp).isEmpty(), "other blocks are left to their own loot");
            CityHooks.Access real = CityHooks.installed();
            try {
                CityHooks.install(new CityHooks.Access() {
                    @Override
                    public boolean inCity(Level l, BlockPos pos) {
                        return true;
                    }
                });
                h.assertTrue(drops(level, pipe, sp).isEmpty(), "Carillon's own pieces never drop");
            } finally {
                CityHooks.install(real);
            }

            // every recipe loaded (a bad one is dropped with a log line, so look for each)
            var recipes = level.getServer().getRecipeManager();
            for (String id : RECIPES) {
                h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Awaken.id(id))).isPresent(), "recipe " + id + " loaded");
            }
            h.succeed();
        });
    }

    private static String part(ServerLevel level, BlockPos pos) {
        return HomeKit.name(level.getBlockState(pos), "part");
    }

    private static InteractionResult put(ServerLevel level, HomeKit.Piece piece, BlockPos clicked, Direction face) {
        return put(level, piece, clicked, face, new ItemStack(HomeKit.ITEM.get(piece).get()));
    }

    /** Uses the piece on a face of a block, with no player (so only the face decides its facing). */
    private static InteractionResult put(ServerLevel level, HomeKit.Piece piece, BlockPos clicked, Direction face, ItemStack stack) {
        Vec3 at = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var ctx = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, new BlockHitResult(at, face, clicked, false));
        return HomeKit.ITEM.get(piece).get().place(ctx);
    }

    /** What the drops handler adds when {@code breaker} breaks the block at {@code pos} (as it is now). */
    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos, ServerPlayer breaker) {
        List<ItemEntity> list = new ArrayList<>();
        HomeKit.onDrops(new BlockDropsEvent(level, pos, level.getBlockState(pos), null, list, breaker, ItemStack.EMPTY));
        return list;
    }
}
