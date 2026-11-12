package dev.awaken.finds.sets;

import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Tell;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.Dress;
import dev.awaken.finds.kit.Mats;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import dev.awaken.finds.kit.PropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.awaken.finds.sets.Bits.*;

import net.minecraft.world.item.DyeColor;

/** Mended Things (mend): something broken, and the pieces to put it right. What you mend stays mended. */
final class Mended {
    private Mended() {}

    private static boolean hat(ItemStack st) {
        return st.is(Items.CARVED_PUMPKIN) || st.is(ItemTags.HEAD_ARMOR);
    }

    private static boolean timber(ItemStack st) {
        return st.is(ItemTags.PLANKS) || st.is(ItemTags.WOODEN_SLABS) || st.is(ItemTags.LOGS);
    }

    private static boolean roofing(ItemStack st) {
        return st.is(ItemTags.LOGS) || st.is(ItemTags.PLANKS) || st.getItem() instanceof net.minecraft.world.item.BlockItem bi
                && (bi.getBlock() instanceof StairBlock || bi.getBlock() instanceof net.minecraft.world.level.block.SlabBlock);
    }

    static void init() {
        Kind.of(FSet.MENDED, 1, "broken_signpost", "The Broken Signpost").verb(Verb.MEND).curio().rank(0).anchor(Anchor.LINE).length(20).made(Made.FALLEN)
                .see("A signpost beside an old road, with its three arms lying on the ground.")
                .learned("Each arm is carved with a shape, not a name. With all three back, it points again.")
                .reach(10).persists().icon(Props.SIGNPOST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -9, 9, 1, m);
                    s.partOn("post", 2, 0, 0, Props.SIGNPOST.get().of(0));
                    int[][] at = {{-2, 3}, {3, -4}, {-3, -2}};
                    for (int i = 0; i < 3; i++) s.partOn("loose", at[i][0], at[i][1], 0, Props.SIGN_ARM.get().of(i, Direction.from2DDataValue(s.rnd.nextInt(4))));
                    s.partOn("cache", 3, 1, 0);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Fit.on("post", st -> st.is(Props.SIGN_ARM.get().asItem()), 3).carry("loose"))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i); for (int i = 0; i < 3; i++) t.hold(Props.SIGN_ARM.get()).use("post", 0); })
                .add();

        Kind.of(FSet.MENDED, 2, "scarecrow", "The Scarecrow").verb(Verb.MEND).curio().rank(0).lands(Group.PLAINS, Group.FOREST).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A scarecrow fallen off its pole in a field gone wild, and a pumpkin somebody carved lying in the weeds.")
                .learned("Stood up and given its hat back, it looked pleased with itself. The crows came straight away.")
                .reach(6).persists().icon(Props.SCARECROW)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.partOn("build", 0, 0, 0, Props.SCARECROW.get().of(3));
                    s.partOn("loose", 1, 1, 0, Props.SCARECROW.get().of(0, Direction.EAST));
                    s.partOn("hat", -2, 2, 0, Blocks.CARVED_PUMPKIN.defaultBlockState());
                    for (int x = -4; x <= 4; x++)
                        for (int z = -4; z <= 4; z++) {
                            if (s.taken(x, s.g(x, z) + 1, z) || (x == 0 && z == 0) || s.rnd.nextInt(3) == 0) continue;
                            s.floor(x, z, (x & 1) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
                            if (s.rnd.nextInt(3) == 0) s.on(x, z, s.rnd.nextBoolean() ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.FERN.defaultBlockState());
                        }
                    s.partOn("cache", 0, 1, 0);
                    s.on(3, -3, m.fence());
                    s.on(-4, -2, m.fence());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Assemble.of("build", st -> st.is(Props.SCARECROW.get().asItem()), Props.SCARECROW.get().of(1)).carry("loose"),
                        Assemble.of("build", Mended::hat, Props.SCARECROW.get().of(2)).carry("hat")))
                .after(c -> Dry.birds(c, 2))
                .play(t -> t.use("loose", 0).hold(Props.SCARECROW.get()).use("build", 0).use("hat", 0).hold(Items.CARVED_PUMPKIN).use("build", 0))
                .add();

        Kind.of(FSet.MENDED, 3, "well_winch", "The Well Winch").verb(Verb.MEND).curio().rank(1).lands(Group.PLAINS, Group.FOREST, Group.DESERT).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A well with good water in it and a winch with nothing on it. A rope and a pail are lying in the grass.")
                .learned("Rope on, pail on, and the first pull brought up more than water.")
                .reach(6).slope(1).persists().icon(Props.WINCH)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int dy = -2; dy <= 0; dy++) s.put(0, dy, 2, Blocks.WATER.defaultBlockState());
                    s.put(0, -3, 2, m.stone());
                    for (int x = -1; x <= 1; x++)
                        for (int z = 1; z <= 3; z++) {
                            if (x == 0 && z == 2) continue;
                            for (int dy = -2; dy <= 0; dy++) s.put(x, dy, z, (x + z + dy) % 2 == 0 ? m.stone() : m.mossy());
                            if (z == 2) s.column(x, z, 0, 1, m.fence());
                            else s.on(x, z, m.slab());
                        }
                    s.put(0, 2, 2, Props.WINCH.get().of(0));
                    s.part("build", 0, 2, 2);
                    s.part("build", 0, 2, 2);
                    s.partOn("loose", 2, 0, 0, Props.ROPE_COIL.get().of(0));
                    s.partOn("loose", -2, 1, 0, Props.BASKET.get().of(0));
                    s.partOn("cache", 0, 4, 0);
                    s.on(2, 3, Blocks.SHORT_GRASS.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Assemble.of("build", st -> st.is(Props.ROPE_COIL.get().asItem()) || st.is(Props.BASKET.get().asItem()) || st.is(Items.BUCKET),
                        Props.WINCH.get().of(0), Props.WINCH.get().of(1)).carry("loose"))
                .play(t -> t.use("loose", 0).use("loose", 1).hold(Props.ROPE_COIL.get()).use("build", 0).hold(Props.BASKET.get()).use("build", 0))
                .add();

        Kind.of(FSet.MENDED, 4, "rope_bridge", "The Rope Bridge").verb(Verb.MEND).curio().rank(1).lands(Group.JUNGLE, Group.MOUNTAIN, Group.FOREST, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A plank bridge over a gully with six planks gone from the middle.")
                .learned("Six planks of your own, and it's a bridge again. It'll stay one.")
                .reach(8).slope(1).persists().icon(() -> Items.OAK_PLANKS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -5; x <= 5; x++)
                        for (int z = -3; z <= 3; z++) {
                            if (x == 0 && z == 0) continue;
                            for (int dy = -2; dy <= 0; dy++) s.air(x, dy, z);
                            if (s.rnd.nextInt(3) == 0) s.put(x, -3, z, Blocks.GRAVEL.defaultBlockState());
                        }
                    s.put(0, -1, 0, m.log());
                    s.put(0, 0, 0, m.log());
                    BlockState slat = top(m.woodSlab());
                    for (int x = -5; x <= 5; x++) {
                        if (x == 0) continue;
                        if (Math.abs(x) >= 4) s.put(x, 0, 0, slat);
                        else s.part("socket", x, 0, 0);
                    }
                    for (int x = -6; x <= 6; x += 12)
                        for (int z = -1; z <= 1; z += 2) {
                            s.on(x, z, m.fence());
                            s.on(x, z * 2, Props.ROPE_PEG.get().of(0));
                        }
                    s.partOn("cache", 7, 1, 0, Props.FOOTING.get().of(0));
                    s.on(-7, -1, Props.ROPE_COIL.get().of(0));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Sockets.need("socket", Mended::timber, i -> top(Blocks.SPRUCE_SLAB.defaultBlockState())))
                .play(t -> { for (int i = 0; i < 6; i++) t.hold(Items.OAK_PLANKS).use("socket", i); })
                .add();

        Kind.of(FSet.MENDED, 5, "sprung_boat", "The Sprung Boat").verb(Verb.MEND).curio().rank(2).lands(Group.SWAMP, Group.OCEAN, Group.PLAINS, Group.FOREST).anchor(Anchor.SHORE).made(Made.FALLEN)
                .see("A rowing boat pulled up on the shore with one plank sprung and no oars.")
                .learned("A plank for the hole and two sticks for oars. It floated, and it's yours.")
                .reach(8).persists().icon(() -> Items.OAK_BOAT)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    BlockState side = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true);
                    s.partOn("hull", 0, 1, 0, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                    s.partOn("hull", 0, 3, 0, Blocks.SPRUCE_SLAB.defaultBlockState());
                    s.partOn("hull", 0, 4, 0, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                    s.partOn("plank", 0, 2, 0);
                    for (int z = 2; z <= 3; z++) {
                        s.partOn("hull", -1, z, 0, side.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.WEST));
                        s.partOn("hull", 1, z, 0, side.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.EAST));
                    }
                    s.partOn("oar", -2, 2, 0);
                    s.partOn("oar", 2, 3, 0);
                    s.partOn("cache", 0, 0, 0);
                    s.part("water", 0, Math.max(s.sea(), s.g(0, 7)) + 1, 7);
                    s.on(2, 1, Props.ROPE_COIL.get().of(0));
                    s.on(-2, 4, Props.ROPE_PEG.get().of(0));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Steps.of(Sockets.need("plank", Mended::timber, i -> Blocks.SPRUCE_SLAB.defaultBlockState()),
                        Sockets.need("oar", st -> st.is(Items.STICK) || st.is(ItemTags.SHOVELS), i -> Props.OLD_ROD.get().of(0))))
                .after(c -> {
                    fill(c, "hull", AIR);
                    fill(c, "plank", AIR);
                    fill(c, "oar", AIR);
                    var boat = EntityTypes.OAK_BOAT.create(c.level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    if (boat == null) return;
                    BlockPos w = c.pos("water", 0);
                    boat.snapTo(w.getX() + 0.5, w.getY() + 0.2, w.getZ() + 0.5, 0, 0);
                    c.level.addFreshEntity(boat);
                })
                .play(t -> t.hold(Items.OAK_PLANKS).use("plank", 0).hold(Items.STICK).use("oar", 0).hold(Items.STICK).use("oar", 1))
                .add();

        Kind.of(FSet.MENDED, 6, "shrine_roof", "The Shrine Roof").verb(Verb.MEND).curio().rank(2).lands(Group.FOREST, Group.SNOW, Group.DARK, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A little wayside shrine with its roof fallen in round the figure inside. The beam and two tiles are lying where they fell.")
                .learned("Beam first, then the tiles. Nothing hunts near it at night now.")
                .reach(6).persists().wards(10).icon(Props.STANDING_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -1; x <= 1; x++) {
                        s.column(x, 3, 0, 1, (x & 1) == 0 ? m.stone() : m.mossy());
                        s.up(x, 3, 2, m.slab());
                    }
                    for (int x = -1; x <= 1; x += 2) s.column(x, 2, 0, 1, m.wall());
                    s.on(0, 2, Props.STANDING_STONE.get().of(1));
                    int roof = s.g(0, 2) + 3;
                    s.part("build", 0, roof, 2);
                    s.part("build", -1, roof, 2);
                    s.part("build", 1, roof, 2);
                    s.partOn("loose", 0, 1, 0, axis(m.log(), Direction.Axis.X));
                    s.partOn("loose", -2, 1, 0, stair(m, Direction.EAST));
                    s.partOn("loose", 2, 2, 0, stair(m, Direction.NORTH));
                    s.partOn("cache", 1, 1, 0);
                    s.on(-1, 1, Blocks.MOSS_CARPET.defaultBlockState());
                    s.on(-2, 3, Blocks.FERN.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> {
                    Mats m = Mats.of(Group.FOREST);
                    return Assemble.of("build", Mended::roofing, axis(m.log(), Direction.Axis.X), stair(m, Direction.EAST), stair(m, Direction.WEST)).carry("loose");
                })
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i); for (int i = 0; i < 3; i++) t.hold(Items.OAK_LOG).use("build", 0); })
                .add();

        Kind.of(FSet.MENDED, 7, "loom", "The Loom").verb(Verb.MEND).find().rank(3).lands(Group.PLAINS, Group.SNOW, Group.FOREST).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A roofless hut with a ground loom pegged out in it, one row woven and the next left empty. Skeins are lying about.")
                .learned("Four skeins finished the row, and the pattern was a picture. The weaver was looking at something a long way off.")
                .reach(7).slope(1).persists().lead().loot("cloth").icon(() -> Items.LOOM)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -4; x <= 4; x++)
                        for (int z = -1; z <= 5; z++) {
                            boolean edge = Math.abs(x) == 4 || z == -1 || z == 5;
                            if (!edge) {
                                if ((x + z) % 3 != 0) s.floor(x, z, m.planks());
                                continue;
                            }
                            if (x == 0 && z == -1) continue;                 // the doorway
                            int h = s.rnd.nextInt(3);
                            boolean corner = Math.abs(x) == 4 && (z == -1 || z == 5);
                            if (corner) s.column(x, z, 0, 1, m.wall());
                            else if (h == 0) s.on(x, z, m.slab());
                            else if (h == 1) s.on(x, z, (x + z) % 2 == 0 ? m.stone() : m.mossy());
                            else {
                                s.on(x, z, m.stone());
                                s.up(x, z, 1, m.slab());
                            }
                        }
                    DyeColor[] dye = {DyeColor.RED, DyeColor.WHITE, DyeColor.BLUE, DyeColor.YELLOW};
                    for (int x = -1; x <= 2; x++) {
                        s.on(x, 2, Blocks.CARPET.pick(dye[(x + 1) & 3]).defaultBlockState());
                        s.partOn("socket", x, 3, 0);
                    }
                    for (int x = -2; x <= 3; x += 5) {
                        s.on(x, 1, m.fence());
                        s.on(x, 4, m.fence());
                    }
                    s.on(0, 4, Blocks.LOOM.defaultBlockState());
                    int[][] at = {{1, 0}, {-1, 0}, {2, 4}, {-1, 4}};
                    for (int i = 0; i < 4; i++) s.partOn("loose", at[i][0], at[i][1], 0, Blocks.CARPET.pick(dye[(i + 2) & 3]).defaultBlockState());
                    s.partOn("cache", 1, 1, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("socket", st -> st.is(ItemTags.WOOL_CARPETS) || st.is(ItemTags.WOOL) || st.is(Items.STRING),
                        i -> Blocks.CARPET.pick(new DyeColor[] {DyeColor.BLUE, DyeColor.RED, DyeColor.RED, DyeColor.WHITE}[i & 3]).defaultBlockState()).carry("loose"))
                .play(t -> { for (int i = 0; i < 4; i++) t.use("loose", i); for (int i = 0; i < 4; i++) t.hold(Items.STRING).use("socket", i); })
                .add();

        Kind.of(FSet.MENDED, 8, "stopped_cart", "The Stopped Cart").verb(Verb.MEND).find().rank(4).lands(Group.DARK, Group.BADLANDS, Group.MOUNTAIN).anchor(Anchor.LINE).length(24).made(Made.FALLEN)
                .see("A loaded cart on three wheels at the side of an old road, its load still roped down. The fourth wheel is in the ditch.")
                .learned("With the wheel back on, the ropes went slack and the load came free. Nobody had touched it since it stopped.")
                .reach(11).persists().icon(Props.CART_WHEEL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -10, 10, 1, m);
                    BlockState bed = top(m.woodSlab());
                    for (int x = 2; x <= 3; x++)
                        for (int z = 0; z <= 2; z++) s.on(x, z, bed);
                    s.on(1, 0, Props.CART_WHEEL.get().of(0));
                    s.on(1, 2, Props.CART_WHEEL.get().of(0));
                    s.on(4, 2, Props.CART_WHEEL.get().of(0));
                    s.partOn("axle", 4, 0, 0);
                    s.up(2, 1, 1, Blocks.BARREL.defaultBlockState());
                    s.partOn("cache", 3, 1, 1, Props.BUNDLE.get().of(0));
                    s.up(2, 0, 1, Props.BUNDLE.get().of(1));
                    s.up(3, 2, 1, Props.ROPE_COIL.get().of(0));
                    s.up(2, 2, 1, m.fence());
                    s.up(3, 0, 1, m.fence());
                    s.on(2, 3, m.fence());
                    s.on(3, 3, m.fence());
                    s.partOn("loose", -4, -5, 0, Props.CART_WHEEL.get().of(1));
                    s.on(-3, -4, Dress.twigs(s));
                    s.on(5, 1, Props.LOOSE_STONE.get().of(0));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Sockets.need("axle", st -> st.is(Props.CART_WHEEL.get().asItem()), i -> Props.CART_WHEEL.get().of(0)).carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.CART_WHEEL.get()).use("axle", 0))
                .add();
    }
}
