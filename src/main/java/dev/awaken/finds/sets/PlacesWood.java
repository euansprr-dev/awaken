package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.Pal;
import dev.awaken.finds.kit.PropBlock;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/** Places of the forest and the dark forest (SPEC §59, docs/places/BRIEFS.md 1-6). */
final class PlacesWood {
    private PlacesWood() {}

    private static final int ASLEEP = Scene.Spawn.NO_AI | Scene.Spawn.QUIET;

    private static BlockState fire(boolean lit, boolean tall) {
        return st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, lit).setValue(CampfireBlock.SIGNAL_FIRE, tall);
    }

    /** An earth-covered charcoal kiln: a turfed mound as wide as {@code r}. */
    private static void kiln(Scene s, int x, int z, double r) {
        blob(s, x, 1, z, r, r * 1.05, r, h -> h < 90 ? st(Blocks.COARSE_DIRT) : h < 170 ? st(Blocks.ROOTED_DIRT) : h < 215 ? st(Blocks.MUD) : st(Blocks.MOSS_BLOCK));
    }

    static void init() {
        Kind.of(FSet.PLACES, 1, "charcoal_clearing", "The Charcoal Burners' Clearing").verb(Verb.LIGHT).place().rank(0).lands(Group.FOREST, Group.DARK)
                .anchor(Anchor.FIELD).slope(6).made(Made.BUILT)
                .see("A clearing with three earth kilns in it, one of them smoking, and stone people who were still working when they stopped.")
                .learned("You lit the cold kiln for them and opened the one that was done. The charcoal was still good.")
                .reach(31).icon(() -> Items.CHARCOAL)
                .air(Air.of(0x8C857A, 52).mote(() -> ParticleTypes.CAMPFIRE_COSY_SMOKE, 0.12F, 5, 0.03F).loop(() -> SoundEvents.CAMPFIRE_CRACKLE, 50, 0.7F, 1F))
                .scene(s -> {
                    s.level(23, 8).clear(26);
                    BlockState ash = st(Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_GRAY)), dlog = st(Blocks.DARK_OAK_LOG), slog = st(Blocks.SPRUCE_LOG);
                    ground(s, 0, 0, 21, h -> h < 80 ? st(Blocks.COARSE_DIRT) : h < 135 ? st(Blocks.PODZOL) : h < 160 ? st(Blocks.ROOTED_DIRT) : h < 178 ? st(Blocks.GRAVEL) : null);
                    path(s, 0, 30, 0, 14, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
                    // west: the cold kiln, half built: a round stack of upright logs, turfed on its north side only
                    for (int x = -15; x <= -5; x++)
                        for (int z = -9; z <= 1; z++) {
                            double d = Math.sqrt((x + 10) * (x + 10) + (z + 4) * (z + 4));
                            if (d <= 3.3) for (int y = 1; y <= (d < 1.6 ? 5 : d < 2.6 ? 4 : 3); y++) s.put(x, y, z, (x + z & 1) == 0 ? dlog : slog);
                        }
                    blob(s, -10, 1, -8, 4.4, 4.2, 2.2, h -> h < 110 ? st(Blocks.COARSE_DIRT) : h < 190 ? st(Blocks.ROOTED_DIRT) : st(Blocks.MOSS_BLOCK));
                    s.put(-10, 1, 0, fire(false, false));
                    s.part("kiln", -10, 1, 0);
                    stilled(s, -8, 1, 1, 1, Direction.WEST);
                    for (int y = 1; y <= 4; y++) s.put(-6, y, -2, ladder(Direction.EAST));
                    for (int y = 1; y <= 4; y++) s.put(-7, y, -2, dlog);
                    // east: the burning one, whole, smoke standing over it and ash round its foot
                    kiln(s, 10, -5, 5.0);
                    ground(s, 10, -5, 8, h -> h < 120 ? ash : h < 150 ? st(Blocks.COARSE_DIRT) : null);
                    s.put(10, 6, -5, st(Blocks.HAY_BLOCK));
                    s.put(10, 7, -5, fire(true, true));
                    s.put(10, 1, 1, st(Blocks.MAGMA_BLOCK));
                    s.put(10, 2, 1, top(st(Blocks.MUD_BRICK_SLAB)));
                    for (int x = 9; x <= 11; x += 2) s.put(x, 1, 1, st(Blocks.MUD_BRICKS));
                    stilled(s, 12, 1, 3, 0, Direction.NORTH);
                    // south: the drawn one, cut open, charcoal inside behind a door of mud brick
                    kiln(s, 0, 7, 4.4);
                    fill(s, -2, 1, 5, 2, 3, 8, st(Blocks.COAL_BLOCK));
                    air(s, 0, 1, 9, 0, 2, 11);
                    for (int x = -1; x <= 1; x += 2) for (int y = 1; y <= 3; y++) s.put(x, y, 11, st(Blocks.MUD_BRICKS));
                    s.put(0, 3, 11, st(Blocks.MUD_BRICK_SLAB));
                    s.put(0, 1, 11, st(Blocks.MUD_BRICKS));
                    s.put(0, 2, 11, st(Blocks.MUD_BRICKS));
                    s.part("door", 0, 1, 11);
                    s.part("door", 0, 2, 11);
                    s.part("cache", 0, 1, 12);
                    s.put(2, 1, 13, Props.BUNDLE.get().of(0));
                    s.put(-2, 1, 13, Props.BUNDLE.get().of(1));
                    s.put(-3, 1, 12, Props.BASKET.get().of(0));
                    fill(s, 4, 1, 12, 5, 1, 14, st(Blocks.COAL_BLOCK));
                    s.put(4, 2, 13, st(Blocks.COAL_BLOCK));
                    cart(s, 7, 1, 15, false);
                    // north-west: the burners' bothy, turf on its roof, and the lean-to where the cordwood dries
                    house(s, -19, 5, -12, 11, 0, 3, Pal.COBBLE.roof(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB), 1, 0);
                    s.put(-18, 1, 10, st(Blocks.HAY_BLOCK));
                    s.put(-17, 1, 10, st(Blocks.HAY_BLOCK));
                    s.put(-18, 1, 6, st(Blocks.BARREL));
                    table(s, -15, 1, 6, 2, true, Pal.TIMBER);
                    moss(s, -20, 3, 4, -11, 8, 12, 70);
                    for (int y = 1; y <= 7; y++) s.put(-18, y, 4, y < 7 ? st(Blocks.COBBLESTONE) : st(Blocks.COBBLESTONE_WALL));
                    lamp(s, -10, 1, 8, 3, st(Blocks.SPRUCE_FENCE), false);
                    stilled(s, -10, 1, 10, 3, Direction.EAST);
                    for (int x = -8; x <= 0; x += 4) for (int z = -17; z <= -13; z += 4) for (int y = 1; y <= 3; y++) s.put(x, y, z, st(Blocks.SPRUCE_FENCE));
                    for (int x = -9; x <= 1; x++) for (int z = -18; z <= -12; z++) s.put(x, 4, z, z < -15 ? st(Blocks.SPRUCE_SLAB) : top(st(Blocks.SPRUCE_SLAB)));
                    logPile(s, -7, -16, 1, 3, true, slog);
                    logPile(s, -3, -16, 1, 3, true, dlog);
                    // east edge: the cut wood, stacked in long ricks, and the sawing horse
                    logPile(s, 17, -12, 1, 5, false, slog);
                    logPile(s, 17, -5, 1, 6, false, slog);
                    logPile(s, 16, 4, 1, 5, false, dlog);
                    logPile(s, 12, 11, 1, 4, true, st(Blocks.OAK_LOG));
                    s.put(14, 1, 8, st(Blocks.SPRUCE_FENCE));
                    s.put(16, 1, 8, st(Blocks.SPRUCE_FENCE));
                    for (int x = 13; x <= 17; x++) s.put(x, 2, 8, axis(st(Blocks.STRIPPED_SPRUCE_LOG), Direction.Axis.X));
                    stilled(s, 14, 1, 9, 2, Direction.NORTH);
                    s.put(5, 1, 3, Props.CART_WHEEL.get().of(1));
                    // the stumps of what they felled, round the edge
                    for (int i = 0; i < 26; i++) {
                        double a = i * Math.PI / 13 + 0.2, d = 17 + (h(s, i, 2, 9) % 5);
                        int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
                        if ((Math.abs(x) < 3 && z > 0) || !open(s, x, z, 0)) continue;
                        s.put(x, 1, z, st(i % 3 == 0 ? Blocks.SPRUCE_LOG : i % 3 == 1 ? Blocks.OAK_LOG : Blocks.DARK_OAK_LOG));
                        if (i % 2 == 0) s.put(x, 2, z, st(i % 4 == 0 ? Blocks.MOSS_CARPET : Blocks.BROWN_MUSHROOM));
                    }
                    wild(s, 21, 30, 12);
                    s.spawn(EntityTypes.FOX, -14, -14, 0, "fox");
                    s.cam(0, 14, 40, 180, 12).cam(18, 22, 20, 135, 40).cam(0, 2, 20, 180, 0).cam(3, 2, 2, -120, 0);
                })
                .script(() -> Steps.of(Light.all("kiln"), Prize.at("door").then((c, i) -> fillPart(c, "door"))))
                .play(t -> t.hold(Items.FLINT_AND_STEEL).use("kiln", 0).use("door", 0))
                .add();

        Kind.of(FSet.PLACES, 2, "mill_at_the_weir", "The Mill at the Weir").verb(Verb.MEND).place().rank(0).lands(Group.FOREST, Group.PLAINS)
                .anchor(Anchor.FIELD).slope(5).made(Made.BUILT)
                .see("A water mill on a pond, its wheel one paddle short and its sluice shut. The miller is stone at his hopper.")
                .learned("With the paddle back and the sluice pulled the stones turned over once, and the bin let go of what it held.")
                .reach(34).persists().icon(() -> Items.WHEAT)
                .air(Air.of(0xBFD6D2, 90).mote(() -> ParticleTypes.FALLING_WATER, 0.25F, 4, -0.05F).loop(() -> SoundEvents.WATER_AMBIENT, 30, 0.8F, 1F))
                .scene(s -> {
                    s.level(26, 8).clear(29);
                    BlockState cob = st(Blocks.COBBLESTONE), gravel = st(Blocks.GRAVEL), clay = st(Blocks.CLAY);
                    pool(s, -11, -2, 8.5, 8.5, 0, 3, clay, gravel);
                    for (int x = -3; x <= 2; x++) {                       // the race, walled in stone
                        s.put(x, 0, -2, WATER);
                        s.put(x, -1, -2, cob);
                        s.put(x, 0, -3, st(Blocks.STONE_BRICKS));
                        s.put(x, 0, -1, st(Blocks.STONE_BRICKS));
                    }
                    for (int z = -6; z <= 9; z++) {                       // the wheel pit and the tail race
                        boolean pit = z <= 1;
                        s.put(3, 0, z, WATER);
                        if (pit) s.put(3, -1, z, WATER);
                        s.put(3, pit ? -2 : -1, z, cob);
                        for (int x = 2; x <= 4; x += 2) {
                            if (x == 2 && z == -2) continue;
                            s.put(x, 0, z, st(z % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
                            s.put(x, -1, z, cob);
                        }
                    }
                    s.put(3, 0, -7, st(Blocks.STONE_BRICKS));
                    pool(s, 3, 13, 4.2, 3.6, 0, 2, clay, gravel);
                    // the wheel: seven across, turning in the pit, one paddle gone from the top
                    for (int y = -1; y <= 5; y++)
                        for (int z = -5; z <= 1; z++) {
                            double d = Math.sqrt((y - 2) * (y - 2) + (z + 2) * (z + 2));
                            if (d >= 2.1 && d < 3.5 && !(y == 5 && z == -2)) s.put(3, y, z, st(Blocks.SPRUCE_PLANKS));
                            else if (d > 0.5 && d < 2.1 && (y == 2 || z == -2)) s.put(3, y, z, st(Blocks.SPRUCE_FENCE));
                        }
                    for (int x = 3; x <= 6; x++) s.put(x, 2, -2, axis(st(Blocks.DARK_OAK_LOG), Direction.Axis.X));
                    s.part("paddle", 3, 5, -2);
                    s.put(0, 1, 3, st(Blocks.SPRUCE_SLAB));
                    s.part("loose", 0, 1, 3);
                    s.put(-1, 1, -4, Props.WINCH.get().of(0));
                    s.part("lever", -1, 1, -4);
                    // the mill: stone below, timber above, a deep roof
                    floor(s, 5, -7, 13, 2, 0, cob, st(Blocks.STONE_BRICKS), 30);
                    air(s, 6, 1, -6, 12, 9, 1);
                    walls(s, 5, -7, 13, 2, 1, 3, Pal.COBBLE, 0);
                    walls(s, 5, -7, 13, 2, 4, 6, Pal.TIMBER.base(Blocks.SPRUCE_PLANKS), 0);
                    for (int x = 5; x <= 13; x += 8) for (int z = -7; z <= 2; z += 9) for (int y = 1; y <= 6; y++) s.put(x, y, z, st(Blocks.DARK_OAK_LOG));
                    for (int x = 5; x <= 13; x++) for (int z = -7; z <= 2; z += 9) s.put(x, 4, z, axis(st(Blocks.DARK_OAK_LOG), Direction.Axis.X));
                    fill(s, 6, 4, -6, 12, 4, 1, st(Blocks.SPRUCE_PLANKS));
                    gable(s, 5, -7, 13, 2, 6, Pal.TIMBER, false, 1, 0);
                    air(s, 9, 1, 2, 9, 2, 2);
                    for (int z = -5; z <= 0; z += 5) { s.put(13, 2, z, along(st(Blocks.GLASS_PANE), false)); s.put(13, 5, z, along(st(Blocks.GLASS_PANE), false)); }
                    s.put(9, 5, 2, along(st(Blocks.GLASS_PANE), true));
                    s.put(5, 2, -2, axis(st(Blocks.DARK_OAK_LOG), Direction.Axis.X));
                    for (int y = 1; y <= 4; y++) s.put(12, y, -6, ladder(Direction.WEST));
                    s.put(7, 1, -2, st(Blocks.SMOOTH_STONE));
                    s.put(7, 2, -2, st(Blocks.GRINDSTONE));
                    s.put(7, 3, -2, st(Blocks.HOPPER));
                    s.put(7, 4, -2, AIR);
                    s.put(10, 1, -5, st(Blocks.BARREL));
                    s.part("cache", 10, 2, -5);
                    s.put(11, 1, -5, Props.BUNDLE.get().of(0));
                    s.put(11, 1, -4, Props.BUNDLE.get().of(1));
                    s.put(8, 5, -4, st(Blocks.HAY_BLOCK));
                    s.put(9, 5, -4, st(Blocks.HAY_BLOCK));
                    s.put(9, 3, -2, lantern(true, false));
                    stilled(s, 8, 1, -3, 1, Direction.WEST);
                    s.put(-3, 1, 0, Props.STILLED_CHILD.get().front(0, Direction.WEST));
                    s.put(-3, 1, 1, Props.OLD_ROD.get().of(0));
                    // outside: a cart, sacks, a plank over the race, lilies
                    for (int x = 0; x <= 1; x++) s.put(x, 1, -2, top(st(Blocks.SPRUCE_SLAB)));
                    cart(s, 17, 1, 3, true);
                    s.put(15, 1, 1, Props.BUNDLE.get().of(0));
                    s.put(15, 1, 0, Props.BUNDLE.get().of(1));
                    // the hoist over the loft door, a chimney, a bench by the door
                    for (int z = 3; z <= 4; z++) s.put(9, 8, z, axis(st(Blocks.DARK_OAK_LOG), Direction.Axis.Z));
                    s.put(9, 7, 4, st(Blocks.IRON_CHAIN));
                    s.put(9, 6, 4, st(Blocks.HAY_BLOCK));
                    for (int y = 1; y <= 12; y++) s.put(13, y, -8, y < 11 ? cob : st(Blocks.COBBLESTONE_WALL));
                    s.put(7, 1, 3, stair(st(Blocks.SPRUCE_STAIRS), Direction.NORTH));
                    s.put(11, 1, 3, st(Blocks.BARREL));
                    // the barn across the yard, the miller's field behind a fence, a bridge over the tail race
                    house(s, 14, 8, 20, 15, 0, 4, Pal.TIMBER, 3, 0);
                    stack(s, 15, 1, 14, 6, true);
                    s.put(19, 1, 9, st(Blocks.HAY_BLOCK));
                    s.put(19, 2, 9, st(Blocks.HAY_BLOCK));
                    s.put(19, 1, 10, st(Blocks.HAY_BLOCK));
                    crops(s, -12, 12, -2, 21, Blocks.WHEAT);
                    for (int x = -13; x <= -1; x++) for (int z = 11; z <= 22; z += 11) if (x != -7) s.put(x, 1, z, st(Blocks.SPRUCE_FENCE));
                    for (int z = 11; z <= 22; z++) for (int x = -13; x <= -1; x += 12) s.put(x, 1, z, st(Blocks.SPRUCE_FENCE));
                    s.put(-9, 1, 16, st(Blocks.SPRUCE_FENCE));                 // the scarecrow
                    s.put(-9, 2, 16, st(Blocks.HAY_BLOCK));
                    s.put(-9, 3, 16, st(Blocks.CARVED_PUMPKIN));
                    for (int x = 1; x <= 5; x++) { s.put(x, 1, 6, top(st(Blocks.STONE_BRICK_SLAB))); if (x == 1 || x == 5) for (int z = 5; z <= 7; z += 2) s.put(x, 1, z, st(Blocks.COBBLESTONE_WALL)); }
                    tree(s, -20, 6, 1, 7, st(Blocks.OAK_LOG), leaf(Blocks.OAK_LEAVES), 2.8);
                    tree(s, -17, -13, 1, 6, st(Blocks.BIRCH_LOG), leaf(Blocks.BIRCH_LEAVES), 2.2);
                    tree(s, 2, -15, 1, 7, st(Blocks.OAK_LOG), leaf(Blocks.OAK_LEAVES), 2.8);
                    for (int i = 0; i < 9; i++) {
                        int x = -11 + (h(s, i, 4, 1) % 11) - 5, z = -2 + (h(s, i, 4, 2) % 11) - 5;
                        if (s.get(x, 0, z) == WATER && !s.taken(x, 1, z)) s.put(x, 1, z, st(Blocks.LILY_PAD));
                    }
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8;
                        int x = -11 + (int) Math.round(Math.cos(a) * 9.6), z = -2 + (int) Math.round(Math.sin(a) * 9.6);
                        if (!s.taken(x, 1, z) && s.get(x, 0, z) == null) s.put(x, 1, z, st(i % 3 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS));
                    }
                    path(s, 9, 33, 9, 4, 1, st(Blocks.DIRT_PATH), st(Blocks.COARSE_DIRT));
                    wild(s, 24, 33, 10);
                    s.spawn(EntityTypes.FROG, -4, 6, 0, "frog").spawnUp(EntityTypes.COD, -11, -2, -2, 0, "fish").spawnUp(EntityTypes.COD, -9, 0, -2, 0, "fish");
                    s.cam(-2, 13, 44, 175, 10).cam(-14, 26, 24, -140, 40).cam(9, 2, 13, 180, -8).cam(10, 1, 0, 135, 2);
                })
                .script(() -> Steps.of(Sockets.need("paddle", st -> st.is(Items.SPRUCE_SLAB) || st.is(net.minecraft.tags.ItemTags.PLANKS), i -> st(Blocks.SPRUCE_PLANKS)).carry("loose"),
                        Prize.at("lever").then((c, i) -> c.sound(c.pos("lever", 0), SoundEvents.GRINDSTONE_USE, 2.0F, 0.6F))))
                .play(t -> t.use("loose", 0).hold(Items.SPRUCE_SLAB).use("paddle", 0).use("lever", 0))
                .add();

        Kind.of(FSet.PLACES, 3, "hollow_king", "The Hollow King").verb(Verb.SNEAK).place().rank(2).lands(Group.FOREST, Group.DARK)
                .anchor(Anchor.FIELD).slope(6).made(Made.ALIVE)
                .see("One dead oak three times the height of the wood, hollow, with a door split in its side and a lantern on its highest branch.")
                .learned("Three floors inside, and owls at the top. You came down with what they were sitting on and opened the store.")
                .reach(23).icon(() -> Items.DARK_OAK_LOG)
                .air(Air.of(0x5E6B4A, 60).mote(() -> ParticleTypes.WHITE_ASH, 0.5F, 9, 0.004F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 420, 0.35F, 1.2F))
                .scene(s -> {
                    s.level(15, 8).clear(11);
                    BlockState bark = st(Blocks.DARK_OAK_WOOD), pale = st(Blocks.STRIPPED_DARK_OAK_WOOD), plank = st(Blocks.OAK_PLANKS);
                    ground(s, 0, 0, 13, h -> h < 110 ? st(Blocks.PODZOL) : h < 150 ? st(Blocks.COARSE_DIRT) : h < 175 ? st(Blocks.MOSS_BLOCK) : null);
                    // the trunk: three great lobes, flared at the foot, a little narrower at the broken crown
                    for (int x = -9; x <= 9; x++)
                        for (int z = -9; z <= 9; z++) {
                            double d = Math.sqrt(x * x + z * z), th = Math.atan2(z, x);
                            if (d < 3.2 || d >= 9) continue;
                            int top = 27 + (int) Math.round(n(s, x * 3.0, z * 3.0, 4.0) * 5);
                            for (int y = 1; y <= top; y++) {
                                double out = 5.4 + 0.5 * Math.sin(3 * th + 1) + Math.max(0, (7 - y) * 0.45) - y * 0.015;
                                if (d >= 4.0 && d < out) s.put(x, y, z, h(s, x, y, z) < 26 ? pale : bark);
                            }
                        }
                    for (int d = 0; d < 8; d++) {                          // root buttresses
                        double a = d * Math.PI / 4 + 0.35;
                        for (int j = 1; j <= 8; j++) {
                            int x = (int) Math.round(Math.cos(a) * (4.5 + j)), z = (int) Math.round(Math.sin(a) * (4.5 + j));
                            if (Math.abs(x) <= 1 && z > 0) continue;
                            for (int y = 0; y <= Math.max(0, 4 - j * 2 / 3); y++) s.put(x, y, z, bark);
                        }
                    }
                    air(s, 0, 1, 4, 0, 3, 5);                              // the split that is the door
                    s.put(1, 1, 4, AIR);
                    s.put(1, 2, 4, AIR);
                    for (int y : new int[] {10, 20}) {                     // two floors, wall to wall, a ladder through each
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (x * x + z * z < 16) s.put(x, y, z, plank);
                        s.put(0, y - 1, 0, lantern(true, false));
                    }
                    for (int y = 1; y <= 10; y++) s.put(3, y, 0, ladder(Direction.WEST));
                    for (int y = 11; y <= 20; y++) s.put(-3, y, 0, ladder(Direction.EAST));
                    // ground floor: the store
                    s.put(-2, 1, -2, st(Blocks.BARREL));
                    s.part("chest", -2, 1, -2);
                    s.part("cache", -1, 1, -1);
                    s.put(-3, 1, 0, st(Blocks.BARREL));
                    s.put(2, 1, -2, Props.BASKET.get().of(1));
                    s.put(0, 1, -3, Props.BUNDLE.get().of(0));
                    // first floor: where she lived
                    s.put(-1, 11, -1, st(Blocks.BOOKSHELF));
                    s.put(-1, 11, 0, st(Blocks.CRAFTING_TABLE));
                    s.put(1, 11, -1, st(Blocks.HAY_BLOCK));
                    s.put(0, 12, -1, candles(2, true));
                    s.put(0, 11, -1, top(st(Blocks.OAK_SLAB)));
                    stilled(s, 1, 11, 1, 2, Direction.NORTH);
                    // the loft
                    s.put(0, 21, 0, Props.NEST.get().of(2));
                    s.part("prize", 0, 21, 0);
                    s.part("sleeper", 0, 22, 0);
                    var owl = Life.type("hollow_owl", EntityTypes.PARROT);
                    s.spawnUp(owl, 1, 1, 20, ASLEEP, "owl").spawnUp(owl, -1, 0, 20, ASLEEP, "owl").spawnUp(owl, 0, -1, 20, ASLEEP, "owl");
                    for (int[] k : new int[][] {{4, 7, 0}, {-4, 13, 1}, {0, 16, -4}, {4, 23, -1}, {-1, 24, -4}}) s.put(k[0], k[1], k[2], AIR);   // knot holes
                    for (int d = 0; d < 4; d++) {                          // the bare antlers
                        double a = d * Math.PI / 2 + 0.6;
                        limb(s, (int) Math.round(Math.cos(a) * 4), 23 + (d & 1) * 2, (int) Math.round(Math.sin(a) * 4),
                                (int) Math.round(Math.cos(a) * 12), 28 + (d & 1) * 2, (int) Math.round(Math.sin(a) * 12), bark);
                        if (d == 1) {
                            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
                            s.put(x, 29, z, st(Blocks.IRON_CHAIN));
                            s.put(x, 28, z, lantern(true, false));
                            s.put(x, 30, z, bark);
                        }
                    }
                    for (int d = 0; d < 7; d++) {                          // lesser boughs, a few still in leaf
                        double a = d * 0.9 + 0.15;
                        int ex = (int) Math.round(Math.cos(a) * (9 + d % 3 * 2)), ey = 23 + (d % 4) * 2, ez = (int) Math.round(Math.sin(a) * (9 + d % 3 * 2));
                        limb(s, (int) Math.round(Math.cos(a) * 4), 17 + (d % 3) * 3, (int) Math.round(Math.sin(a) * 4), ex, ey, ez, bark);
                        if (d % 2 == 0) leaves(s, ex, ey + 1, ez, 2.5, leaf(Blocks.DARK_OAK_LEAVES));
                    }
                    vines(s, -7, 2, -7, 7, 18, 7, 9);
                    moss(s, -13, 0, -13, 13, 5, 13, 14);
                    for (int i = 0; i < 14; i++) {
                        int x = s.rnd.nextInt(25) - 12, z = s.rnd.nextInt(25) - 12;
                        if (!s.taken(x, 1, z) && x * x + z * z > 36) s.put(x, 1, z, st(i % 3 == 0 ? Blocks.RED_MUSHROOM : i % 3 == 1 ? Blocks.BROWN_MUSHROOM : Blocks.FERN));
                    }
                    ring(s, -8, 8, 1.5, 1, st(Blocks.COBBLESTONE_SLAB));
                    s.put(-8, 1, 8, fire(false, false));
                    wild(s, 11, 22, 7);
                    s.cam(0, 18, 38, 180, -4).cam(13, 36, 13, 135, 52).cam(0, 2, 13, 180, -10).cam(0, 12, 1, 150, 10);
                })
                .script(() -> Steps.of(Quiet.near("sleeper", "prize", 5).voice(SoundEvents.PARROT_AMBIENT), Prize.at("chest")))
                .play(t -> t.use("prize", 0).use("chest", 0))
                .add();

        Kind.of(FSet.PLACES, 4, "glowworm_cave", "The Glowworm Cave").verb(Verb.RING).place().rank(1).lands(Group.DARK, Group.FOREST, Group.JUNGLE, Group.MOUNTAIN)
                .anchor(Anchor.FIELD).slope(7).made(Made.NATURAL)
                .see("A dark mouth under a heap of mossy boulders. Below it, a hall with a lake that is lit from underneath and threads of blue light hanging over the water.")
                .learned("Across the stones to the island. The three hanging stones had an order, and the dead tree kept something under its roots.")
                .reach(26).tell(Kind.Tell.GLINT).icon(() -> Items.GLOW_BERRIES)
                .air(Air.of(0x1E3350, 34).below().mote(() -> ParticleTypes.GLOW, 0.5F, 9, -0.004F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 320, 0.5F, 1F)
                        .loop(() -> SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 45, 0.9F, 1F))
                .scene(s -> {
                    s.level(4, 9, 6, 5, 0);
                    java.util.function.IntFunction<BlockState> rock = h -> h < 70 ? st(Blocks.MOSSY_COBBLESTONE) : h < 130 ? st(Blocks.TUFF) : h < 200 ? st(Blocks.STONE) : st(Blocks.DEEPSLATE);
                    blob(s, 0, 2, 10, 3.5, 4.0, 3.0, rock);
                    blob(s, 8, 2, 9, 3.8, 5.0, 3.4, rock);
                    blob(s, 4, 6, 9, 5.5, 2.4, 3.0, rock);
                    // more of the tor standing round the mouth, so the heap shows over the trees
                    for (int[] k : new int[][] {{-6, 12, 30}, {14, 13, 27}, {-3, 3, 23}, {12, 3, 25}, {-10, 5, 19}, {18, 8, 19}})
                        boulder(s, k[0], k[1], k[2] / 10.0, st(Blocks.STONE), st(Blocks.MOSSY_COBBLESTONE), st(Blocks.TUFF));
                    blob(s, 5, 9, 8, 3.2, 2.6, 2.6, rock);
                    // the hall: thirty by twenty, under nine blocks of rock
                    carve(s, 0, -15, -9, 14.5, 6.0, 10.5, rock);
                    for (int x = -17; x <= 17; x++)
                        for (int z = -22; z <= 4; z++) {
                            double f = x * x / (16.5 * 16.5) + (z + 9) * (z + 9) / (12.5 * 12.5);
                            if (f <= 1) for (int y = -22; y <= -18; y++) s.put(x, y, z, rock.apply(h(s, x, y, z)));
                        }
                    // the way down from the mouth, turning once, kept clear of the heart
                    for (int i = 0; i <= 17; i++) carve(s, 4 + (i > 9 ? (i - 9) / 3 : 0), 1 - i, 9 - i, 2.3, 2.3, 2.3, rock);
                    for (int i = 0; i <= 17; i++) {
                        int x = 4 + (i > 9 ? (i - 9) / 3 : 0);
                        s.put(x, -1 - i, 9 - i, st(Blocks.COBBLESTONE_STAIRS).setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH));
                        s.put(x, -2 - i, 9 - i, rock.apply(h(s, x, i, 3)));
                    }
                    pool(s, -3, -11, 10.0, 6.5, -18, 2, st(Blocks.CLAY), st(Blocks.GRAVEL));
                    for (int[] k : new int[][] {{-7, -12}, {-2, -9}, {2, -13}, {-9, -9}, {5, -10}, {-4, -14}}) s.put(k[0], -20, k[1], st(Blocks.SEA_LANTERN));
                    ground2(s, 6, -17, -6, 4.5, st(Blocks.GRAVEL));
                    // the island and its dead tree, and stones to step across on
                    for (int x = -5; x <= -2; x++) for (int z = -13; z <= -10; z++) if ((x + 3) * (x + 3) + (z + 11) * (z + 11) <= 4) { s.put(x, -18, z, st(Blocks.MOSSY_COBBLESTONE)); s.put(x, -19, z, st(Blocks.STONE)); }
                    for (int[] k : new int[][] {{4, -9}, {2, -10}, {0, -10}, {-1, -11}}) { s.put(k[0], -18, k[1], st(Blocks.MOSSY_COBBLESTONE_SLAB).setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP)); s.put(k[0], -19, k[1], st(Blocks.COBBLESTONE)); }
                    BlockState dead = st(Blocks.STRIPPED_OAK_LOG);
                    for (int y = -17; y <= -13; y++) s.put(-3, y, -11, dead);
                    s.put(-4, -14, -11, axis(dead, Direction.Axis.X));
                    s.put(-2, -13, -11, axis(dead, Direction.Axis.X));
                    s.put(-3, -14, -12, axis(dead, Direction.Axis.Z));
                    BlockState berry = st(Blocks.CAVE_VINES).setValue(net.minecraft.world.level.block.CaveVines.BERRIES, true);
                    s.put(-4, -15, -11, berry);
                    s.put(-2, -14, -11, berry);
                    s.put(-3, -15, -12, berry);
                    int[][] chime = {{-4, -12}, {-2, -10}, {-4, -10}};
                    for (int i = 0; i < 3; i++) { s.put(chime[i][0], -17, chime[i][1], Props.WIND_CHIME.get().of(i)); s.part("stone", chime[i][0], -17, chime[i][1]); }
                    s.part("cache", -3, -17, -12);
                    s.put(-2, -17, -12, Props.ROOT_LOOP.get().of(0));
                    // what hangs from the roof: dripstone, and the blue threads over the water
                    BlockState drip = st(Blocks.POINTED_DRIPSTONE).setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
                    BlockState thread = st(Blocks.CAVE_VINES_PLANT).setValue(net.minecraft.world.level.block.CaveVines.BERRIES, true);
                    for (int x = -13; x <= 13; x++)
                        for (int z = -18; z <= 0; z++) {
                            int hh = h(s, x, 40, z);
                            if (hh > 46) continue;
                            for (int y = -16; y <= -8; y++) {
                                BlockState here = s.get(x, y, z), up = s.get(x, y + 1, z);
                                if (here == null || !here.isAir() || up == null || up.isAir()) continue;
                                if (hh < 14) s.put(x, y, z, drip);
                                else if (s.get(x, -18, z) == WATER) {
                                    int len = 1 + hh % 5;
                                    for (int k = 0; k < len && s.get(x, y - k, z) != null && s.get(x, y - k, z).isAir(); k++) s.put(x, y - k, z, k == len - 1 ? berry : thread);
                                }
                                break;
                            }
                        }
                    carve(s, -13, -16, -15, 2.6, 2.4, 2.6, h -> h < 150 ? st(Blocks.AMETHYST_BLOCK) : st(Blocks.CALCITE));
                    s.put(-13, -18, -15, st(Blocks.AMETHYST_CLUSTER));
                    for (int[] k : new int[][] {{6, -14, -4}, {-9, -13, -6}, {0, -12, -14}, {9, -14, -12}}) s.put(k[0], k[1], k[2], light(6));
                    s.spawnUp(EntityTypes.GLOW_SQUID, -5, -9, -20, 0, "squid").spawnUp(EntityTypes.GLOW_SQUID, 1, -12, -20, 0, "squid").spawnUp(EntityTypes.BAT, 6, -6, -14, 0, "bat");
                    wild(s, 13, 25, 8);
                    s.cam(4, 9, 30, 180, 12).cam(14, 12, 16, 150, 32).cam(4, 2, 15, 180, 8).cam(15, -16, -8, 90, 4);
                })
                .script(() -> Notes.of("stone", 3))
                .play(t -> { for (int i : Notes.order(t.seed(), 3, 3)) t.use("stone", i); })
                .add();

        Kind.of(FSet.PLACES, 5, "lantern_shrine", "The Lantern Shrine").verb(Verb.LIGHT).place().rank(2).lands(Group.DARK, Group.FOREST, Group.SNOW)
                .anchor(Anchor.FIELD).slope(7).made(Made.BUILT)
                .see("A straight way of stone lanterns climbing through the trees to a small shrine with a sweeping roof. Half the lanterns are dark.")
                .learned("Lit in order going up, then the bell, then something left on the altar. Nothing hunts near it now.")
                .reach(36).persists().wards(40).icon(() -> Items.LANTERN)
                .air(Air.of(0x6F7C8A, 48).mote(() -> ParticleTypes.FIREFLY, 0.3F, 5, 0.0F).loop(() -> SoundEvents.BELL_RESONATE, 600, 0.35F, 0.6F))
                .scene(s -> {
                    Pal p = Pal.RUIN;
                    BlockState tile = st(Blocks.DEEPSLATE_TILES), red = st(Blocks.MANGROVE_PLANKS);
                    // three terraces: the foot, the middle, the court
                    s.level(0, 16, 9, 6, 0).level(0, 2, 9, 4, 5).level(0, -17, 12, 6, 10).clear(30);
                    for (int z = 28; z >= -8; z--) for (int x = -2; x <= 2; x++) s.put(x, s.g(x, z), z, h(s, x, 0, z) < 60 ? st(Blocks.MOSSY_COBBLESTONE) : tile);
                    for (int f = 0; f < 2; f++) {                          // two flights of five between the terraces
                        int z0 = f == 0 ? 9 : -6, y0 = f == 0 ? 1 : 6;
                        for (int i = 0; i < 5; i++) for (int x = -2; x <= 2; x++) {
                            s.put(x, y0 + i, z0 - i, stair(st(Blocks.DEEPSLATE_TILE_STAIRS), Direction.NORTH));
                            for (int y = y0 - 3; y < y0 + i; y++) s.put(x, y, z0 - i, st(Blocks.COBBLED_DEEPSLATE));
                        }
                    }
                    for (int x = -4; x <= 4; x += 8) {                     // the gate
                        for (int y = 1; y <= 7; y++) s.put(x, y, 24, red);
                        s.put(x, 0, 24, st(Blocks.STONE_BRICKS));
                    }
                    for (int x = -6; x <= 6; x++) { s.put(x, 7, 24, st(Blocks.DARK_OAK_PLANKS)); if (Math.abs(x) <= 5) s.put(x, 5, 24, red); }
                    for (int x = -6; x <= 6; x += 12) s.put(x, 8, 24, st(Blocks.DARK_OAK_SLAB));
                    int n = 0;
                    for (int z = 20; z >= -8; z -= 4)                      // the lanterns, both sides, every other one dark
                        for (int x = -3; x <= 3; x += 6) {
                            int g = s.g(x, z);
                            s.put(x, g + 1, z, st(Blocks.MOSSY_STONE_BRICK_WALL));
                            s.put(x, g + 2, z, st(Blocks.STONE_BRICK_WALL));
                            boolean dark = (n++ % 2) == 0;
                            s.put(x, g + 3, z, dark ? Props.IRON_LAMP.get().of(0) : Props.IRON_LAMP.get().of(1));
                            if (dark) s.part("lamp", x, g + 3, z);
                            s.put(x, g + 4, z, st(Blocks.STONE_BRICK_SLAB));
                        }
                    // the court, the basin, the bell and the shrine
                    floor(s, -7, -24, 7, -10, 10, tile, st(Blocks.MOSSY_COBBLESTONE), 22);
                    s.put(-5, 11, -12, st(Blocks.WATER_CAULDRON).setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
                    for (int x = 4; x <= 6; x += 2) for (int y = 11; y <= 13; y++) s.put(x, y, -12, st(Blocks.DARK_OAK_FENCE));
                    fill(s, 4, 14, -12, 6, 14, -12, st(Blocks.DARK_OAK_PLANKS));
                    s.put(5, 13, -12, st(Blocks.BELL).setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
                    s.part("bell", 5, 13, -12);
                    fill(s, -4, 11, -23, 4, 11, -17, st(Blocks.STONE_BRICKS));
                    fill(s, -3, 12, -22, 3, 12, -18, st(Blocks.DARK_OAK_PLANKS));
                    for (int x = -3; x <= 3; x += 6) for (int z = -22; z <= -18; z += 4) for (int y = 13; y <= 15; y++) s.put(x, y, z, red);
                    fill(s, -3, 13, -22, 3, 15, -22, st(Blocks.SPRUCE_PLANKS));
                    for (int x = -3; x <= 3; x++) { s.put(x, 13, -22, st(Blocks.SPRUCE_PLANKS)); }
                    gable(s, -3, -22, 3, -18, 15, Pal.DARK, true, 1, 0);
                    s.put(0, 13, -21, st(Blocks.CHISELED_STONE_BRICKS));
                    s.part("altar", 0, 14, -21);
                    s.put(-1, 13, -21, candles(3, true));
                    s.put(1, 13, -21, candles(2, true));
                    s.put(0, 16, -20, lantern(true, false));
                    s.part("cache", 0, 13, -19);
                    for (int x = -2; x <= 2; x++) s.put(x, 11, -16, stair(st(Blocks.STONE_BRICK_STAIRS), Direction.NORTH));
                    stilled(s, 1, s.g(1, 4) + 1, 4, 1, Direction.SOUTH);
                    for (int[] k : new int[][] {{-10, -13}, {10, -15}, {-9, -25}, {9, -26}}) giant(s, k[0], k[1], s.g(k[0], k[1]) + 1, 15, 1, st(Blocks.DARK_OAK_LOG), leaf(Blocks.DARK_OAK_LEAVES), 5.5);
                    moss(s, -8, 0, -25, 8, 12, 28, 12);
                    s.spawn(EntityTypes.CAT, -4, -14, Scene.Spawn.STAY, "cat");
                    wild(s, 17, 34, 10);
                    s.cam(0, 14, 44, 180, 6).cam(14, 30, 2, 110, 40).cam(0, 2, 27, 180, -6).cam(0, 12, -11, 180, -2);
                })
                .script(() -> Steps.of(Light.all("lamp").inOrder(), Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), SoundEvents.BELL_BLOCK, 2.5F, 0.8F)),
                        Sockets.need("altar", st -> !st.isEmpty(), i -> candles(1, true))))
                .play(t -> { t.hold(Items.FLINT_AND_STEEL); for (int i = 0; i < t.count("lamp"); i++) t.use("lamp", i); t.use("bell", 0).hold(Items.APPLE).use("altar", 0); })
                .add();

        Kind.of(FSet.PLACES, 6, "beekeepers_terraces", "The Beekeeper's Terraces").verb(Verb.CARRY).place().rank(1).lands(Group.FOREST, Group.PLAINS)
                .anchor(Anchor.FIELD).slope(7).made(Made.BUILT)
                .see("A slope cut into four flowered steps behind a dry-stone wall, a row of straw hives on each, and a thatched house at the top.")
                .learned("Smoke for the big hive first, then the frame to the extractor. The honey is the kind that mends you.")
                .reach(38).loot("honey").icon(() -> Items.HONEYCOMB)
                .air(Air.of(0xD8C47A, 80).mote(() -> ParticleTypes.FALLING_NECTAR, 0.35F, 5, -0.01F).loop(() -> SoundEvents.BEE_LOOP, 70, 0.5F, 1F))
                .scene(s -> {
                    Pal p = Pal.COBBLE.roof(Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB);
                    BlockState hay = st(Blocks.HAY_BLOCK), thatch = st(Blocks.BAMBOO_MOSAIC_SLAB);
                    // a hill with its south face cut into four straight steps, two high and seven deep; the sides and the back
                    // fall away as grass
                    s.level(30, 8).clear(33);
                    for (int x = -29; x <= 29; x++)
                        for (int z = -29; z <= 29; z++) {
                            if (x * x + z * z > 29 * 29) continue;
                            int step = z > 14 ? 0 : z > 7 ? 2 : z > 0 ? 4 : z > -7 ? 6 : 8;
                            double fx = Math.abs(x) <= 15 ? 1 : Math.max(0, 1 - (Math.abs(x) - 15) / 11.0), fz = z >= -18 ? 1 : Math.max(0, 1 - (-18 - z) / 10.0);
                            fx = fx * fx * (3 - 2 * fx);
                            fz = fz * fz * (3 - 2 * fz);
                            int dy = (int) Math.round(step * fx * fz);
                            if (dy > 0) s.ground(x, z, dy);
                        }
                    net.minecraft.world.level.block.Block[] flower = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM};
                    for (int t = 1; t <= 4; t++) {
                        int zf = 21 - t * 7, y = t * 2;                    // this step's dry-stone face stands at zf, its top at y
                        for (int x = -15; x <= 15; x++) {
                            for (int k = y - 1; k <= y; k++) s.put(x, k, zf, h(s, x, k, zf) < 70 ? st(Blocks.MOSSY_COBBLESTONE) : h(s, x, k, zf) < 130 ? st(Blocks.ANDESITE) : st(Blocks.COBBLESTONE));
                            if (Math.abs(x) > 2 && h(s, x, 9, zf) < 70) s.put(x, y + 1, zf, st(h(s, x, 8, zf) < 128 ? Blocks.COBBLESTONE_WALL : Blocks.MOSSY_COBBLESTONE_WALL));
                        }
                        for (int x = -1; x <= 1; x++) {                    // the stair up the middle
                            s.put(x, y - 1, zf + 2, stair(st(Blocks.COBBLESTONE_STAIRS), Direction.NORTH));
                            s.put(x, y - 1, zf + 1, st(Blocks.COBBLESTONE));
                            s.put(x, y, zf + 1, stair(st(Blocks.COBBLESTONE_STAIRS), Direction.NORTH));
                        }
                        if (t == 4) break;
                        for (int x = -12; x <= 12; x += 4) {               // a row of hives, and flowers thick before and behind them
                            if (x == 0) continue;
                            if ((x + t * 4) % 12 == 0) { s.put(x, y + 1, zf - 4, st(Blocks.SPRUCE_FENCE)); s.put(x, y + 2, zf - 4, st(Blocks.BEEHIVE)); }
                            else { s.put(x, y + 1, zf - 4, hay); s.put(x, y + 2, zf - 4, thatch); }
                        }
                        for (int x = -14; x <= 14; x++)
                            for (int z = zf - 6; z <= zf - 1; z++) {
                                if (Math.abs(x) <= 2 || z == zf - 4 || s.taken(x, y + 1, z)) continue;
                                int hh = h(s, x, 3, z);
                                if (hh < (z == zf - 3 || z == zf - 5 ? 60 : 165)) s.put(x, y + 1, z, st(flower[(t - 1 + (hh & 1) * (z & 1)) % 4]));
                            }
                    }
                    // the big hive under its own little roof, and the keeper beside it
                    s.put(6, 7, -3, hay);
                    s.put(6, 8, -3, hay);
                    s.put(6, 9, -3, thatch);
                    s.part("skep", 6, 8, -3);
                    for (int x = 4; x <= 8; x += 4) for (int k = 7; k <= 9; k++) s.put(x, k, -3, st(Blocks.SPRUCE_FENCE));
                    for (int x = 4; x <= 8; x++) for (int z = -4; z <= -2; z++) s.put(x, 10, z, thatch);
                    stilled(s, 4, 7, -1, 1, Direction.NORTH);
                    // the honey house on the top step, thatched, with its chimney
                    house(s, -6, -18, 6, -11, 8, 4, p, 2, 0);
                    for (int k = 9; k <= 17; k++) s.put(5, k, -17, k < 16 ? st(Blocks.COBBLESTONE) : st(Blocks.COBBLESTONE_WALL));
                    s.put(-4, 9, -17, st(Blocks.CAULDRON));
                    s.part("extractor", -4, 9, -17);
                    s.put(-3, 9, -17, st(Blocks.BARREL));
                    s.part("cache", -3, 10, -17);
                    for (int x = -1; x <= 3; x++) { s.put(x, 9, -17, st(Blocks.HONEY_BLOCK)); if (x != 1) s.put(x, 10, -17, st(Blocks.HONEYCOMB_BLOCK)); }
                    s.put(5, 9, -13, st(Blocks.SMOKER));
                    s.put(4, 9, -12, Props.FIRE_BASKET.get().of(0));
                    s.part("loose", 4, 9, -12);
                    table(s, -5, 9, -14, 3, false, p);
                    s.put(-5, 11, -14, st(Blocks.CANDLE));
                    stack(s, -8, 9, -12, 4, false);
                    for (int x = -9; x <= 9; x += 18) { s.put(x, 9, -9, hay); s.put(x, 10, -9, thatch); }
                    // the foot: a walled yard with a gate, a well, a store and a cart
                    run(s, -16, 23, 16, 23, st(Blocks.COBBLESTONE_WALL), 15);
                    for (int x = -16; x <= 16; x += 32) run(s, x, 22, x, -8, st(h(s, x, 0, 0) < 128 ? Blocks.MOSSY_COBBLESTONE_WALL : Blocks.COBBLESTONE_WALL), -1);
                    for (int x = -2; x <= 2; x += 4) { s.put(x, 2, 23, st(Blocks.COBBLESTONE_WALL)); s.put(x, 3, 23, lantern(false, false)); }
                    well(s, 9, 18, Pal.COBBLE);
                    house(s, -14, 16, -9, 21, 0, 3, Pal.TIMBER, 1, 0);
                    stack(s, -8, 1, 21, 5, true);
                    cart(s, 5, 1, 20, true);
                    path(s, 0, 30, 0, 16, 1, st(Blocks.DIRT_PATH), st(Blocks.COARSE_DIRT));
                    wild(s, 19, 36, 9);
                    s.spawnUp(EntityTypes.BEE, 0, -4, 10, 0, "bee").spawnUp(EntityTypes.BEE, -8, 3, 6, 0, "bee").spawnUp(EntityTypes.BEE, 8, 10, 4, 0, "bee");
                    s.cam(0, 14, 44, 180, 6).cam(22, 30, 26, 140, 38).cam(0, 2, 27, 180, -10).cam(0, 10, -12, 180, 4);
                })
                .script(() -> Steps.of(Sockets.need("skep", st -> st.is(Props.FIRE_BASKET.get().asItem()) || Hands.flame(st), i -> st(Blocks.HAY_BLOCK)).carry("loose").keep(),
                        Prize.at("extractor")))
                .play(t -> t.use("loose", 0).hold(Props.FIRE_BASKET.get()).use("skep", 0).use("extractor", 0))
                .add();
    }

    static void fillPart(Ctx c, String part) {
        for (int i = 0; i < c.count(part); i++) c.set(part, i, AIR);
    }

    /** Swaps a disc of floor at a height that isn't the ground (a cave's beach). */
    private static void ground2(Scene s, int cx, int y, int cz, double r, BlockState st) {
        for (int x = (int) -r - 1; x <= r + 1; x++)
            for (int z = (int) -r - 1; z <= r + 1; z++) {
                BlockState was = s.get(cx + x, y, cz + z);
                if (x * x + z * z <= r * r && was != null && !was.isAir() && was != WATER && h(s, cx + x, y, cz + z) < 190) s.put(cx + x, y, cz + z, st);
            }
    }
}
