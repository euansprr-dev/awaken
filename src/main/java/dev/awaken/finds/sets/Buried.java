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

/** Buried Things (dig): what's under the ground, and what gives it away. */
final class Buried {
    private Buried() {}

    static void init() {
        Kind.of(FSet.BURIED, 1, "foxs_dig", "The Fox's Dig").verb(Verb.DIG).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.SNOW, Group.DARK).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A small fox digging hard at one spot. It runs when it sees you.")
                .learned("It could smell something. You finished the hole.")
                .reach(6).icon(() -> net.minecraft.world.item.Items.SWEET_BERRIES)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.put(0, 0, 0, Blocks.AIR.defaultBlockState());
                    s.put(0, -1, 0, Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("spot", 0, -1, 0);
                    s.part("cache", 0, 0, 0);
                    s.floor(1, 0, Blocks.COARSE_DIRT.defaultBlockState());
                    s.floor(0, 1, Blocks.COARSE_DIRT.defaultBlockState());
                    s.floor(-1, 1, Blocks.ROOTED_DIRT.defaultBlockState());
                    s.on(1, 0, Dress.pebbles(s));
                    s.on(-1, 1, Dress.twigs(s));
                    s.spawn(net.minecraft.world.entity.EntityTypes.FOX, 1, 1, 0, "fox");
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Dig.at("spot"))
                .play(t -> t.dig("spot", 0))
                .add();

        Kind.of(FSet.BURIED, 2, "crossed_sticks", "The Crossed Sticks").verb(Verb.DIG).curio().rank(0).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("Peeled sticks laid in a cross on a patch of bare earth, weighted with stones. There are three of these close together.")
                .learned("One was real, and two were somebody's idea of a joke.")
                .reach(7).icon(() -> net.minecraft.world.item.Items.STICK)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[][] at = {{-4, -1}, {1, 3}, {4, -3}};
                    int real = s.rnd.nextInt(3);
                    for (int i = 0; i < 3; i++) {
                        int x = at[i][0], z = at[i][1];
                        String part = i == real ? "spot" : "decoy";
                        s.floor(x, z, Blocks.COARSE_DIRT.defaultBlockState());
                        s.partOn(part, x, z, 0, Dress.twigs(s));
                        s.part(part, x, s.g(x, z), z);
                        if (i == real) s.part("cache", x, s.g(x, z) + 1, z);
                        s.floor(x + 1, z, Blocks.COARSE_DIRT.defaultBlockState());
                        s.on(x + 1, z, Dress.pebbles(s));
                        s.on(x - 1, z, Props.LOOSE_STONE.get().of(s.rnd.nextInt(4)));
                    }
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Dig.at("spot"))
                .play(t -> t.dig("spot", 0).dig("spot", 1))
                .add();

        Kind.of(FSet.BURIED, 3, "old_hearthstone", "The Old Hearthstone").verb(Verb.DIG).curio().rank(1).lands(Group.PLAINS, Group.FOREST, Group.SNOW, Group.DARK).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A square of blackened stones in the grass where a house once stood, and one big flat stone where its fire was.")
                .learned("People kept things under the hearthstone. These people did too.")
                .reach(6).icon(Props.CAP_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int x = -3; x <= 3; x++)
                        for (int z = -3; z <= 3; z++) {
                            boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                            if (!edge || s.rnd.nextInt(4) == 0) continue;
                            s.floor(x, z, s.rnd.nextBoolean() ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : m.mossy());
                            if (s.rnd.nextInt(3) == 0) s.on(x, z, s.rnd.nextBoolean() ? m.slab() : m.wall());
                        }
                    s.floor(0, -2, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                    s.partOn("stone", 0, -2, 0, Props.CAP_STONE.get().of(0));
                    s.partOn("cache", 0, -2, 0);
                    s.on(-1, -3, m.wall());
                    s.up(-1, -3, 1, m.slab());
                    s.on(1, -3, m.wall());
                    Dress.scatter(s, 0, 0, 3, 4, () -> Dress.litter(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Prize.at("stone"))
                .play(t -> t.use("stone", 0))
                .add();

        Kind.of(FSet.BURIED, 4, "rib_in_the_sand", "The Rib in the Sand").verb(Verb.DIG).curio().rank(1).lands(Group.BADLANDS, Group.DESERT).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("One huge rib standing out of the sand, and a low ridge running away from its foot.")
                .learned("The rest of the animal was under the ridge, and so was what it had swallowed.")
                .reach(7).icon(Props.GREAT_RIB)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, -3, Props.GREAT_RIB.get().of(0));
                    s.on(1, -3, Props.GREAT_RIB.get().of(2));
                    for (int i = 0; i < 4; i++) {
                        int z = -1 + i;
                        s.floor(1, z, Blocks.BONE_BLOCK.defaultBlockState());
                        s.partOn("spot", 1, z, 0, m.loose());
                        if (i == 2) s.partOn("cache", 2, z, 0);
                    }
                    s.on(0, 1, Props.GREAT_RIB.get().of(2, Direction.EAST));
                    s.on(2, -1, Props.GREAT_RIB.get().of(1));
                    Dress.scatter(s, 0, 0, 4, 5, () -> Dress.pebbles(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Dig.at("spot"))
                .play(t -> { for (int i = 0; i < 4; i++) t.dig("spot", i); })
                .add();

        Kind.of(FSet.BURIED, 5, "tide_posts", "The Tide Posts").verb(Verb.DIG).curio().rank(2).anchor(Anchor.SHORE).made(Made.BUILT)
                .see("A line of posts walking out into the water, each with a notch cut in it.")
                .learned("In the morning, when the water's lowest, the foot of the last dry post gave up a sea chest.")
                .reach(9).slope(3).icon(Props.POST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int z = -4; z <= 8; z += 2) {
                        int d = s.depth(0, z);
                        s.column(0, z, 0, Math.min(4, d + 1), Props.POST.get().of(0));
                    }
                    int last = 0;
                    for (int z = 0; z <= 8; z++) if (s.depth(1, z) == 0) last = z;
                    s.floor(1, last, Blocks.GRAVEL.defaultBlockState());
                    s.part("spot", 1, s.g(1, last), last);
                    s.part("cache", 1, s.g(1, last) + 1, last);
                    s.on(-1, -2, Props.ROPE_COIL.get().of(0));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> When.only(c -> c.hour() <= 6000, Dig.at("spot")))
                .play(t -> t.hour(2000).dig("spot", 0))
                .add();

        Kind.of(FSet.BURIED, 6, "buried_bronze", "The Buried Bronze").verb(Verb.DIG).find().rank(2).lands(Group.SWAMP, Group.PLAINS, Group.FOREST).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("The rim of something big and bronze showing through the turf.")
                .learned("A bell too big to move. Struck where it lay, the ground slumped off the frame it fell from.")
                .reach(7).icon(Props.OLD_BELL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState bronze = Blocks.CUT_COPPER.waxed().weathered().defaultBlockState(), green = Blocks.CUT_COPPER_STAIRS.waxed().oxidized().defaultBlockState();
                    for (int x = 2; x <= 4; x++)
                        for (int z = -1; z <= 1; z++) {
                            boolean mid = x == 3 && z == 0;
                            s.put(x, 0, z, mid ? Blocks.COPPER_BLOCK.waxed().oxidized().defaultBlockState() : bronze);
                            s.put(x, -1, z, bronze);
                            if (mid) s.partOn("bell", x, z, 0, Blocks.CUT_COPPER_SLAB.waxed().weathered().defaultBlockState());
                            else if (x == 3 || z == 0) s.partOn("spot", x, z, 0, Blocks.COARSE_DIRT.defaultBlockState());
                            else s.on(x, z, green.setValue(StairBlock.FACING, x == 2 ? Direction.EAST : Direction.WEST));
                        }
                    BlockState beam = m.log().hasProperty(RotatedPillarBlock.AXIS) ? m.log().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z) : m.log();
                    for (int z = -2; z <= 2; z++) {
                        s.put(6, 0, z, beam);
                        s.partOn("slump", 6, z, 0, z % 2 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
                    }
                    s.put(6, 0, 3, m.stone());
                    s.partOn("cache", 6, 3, 0, Props.FOOTING.get().front(0, Direction.WEST));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Steps.of(Dig.at("spot"), Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), net.minecraft.sounds.SoundEvents.BELL_BLOCK, 3.0F, 0.4F))))
                .after(c -> { for (int i = 0; i < c.count("slump"); i++) c.set("slump", i, Blocks.AIR.defaultBlockState()); })
                .play(t -> { for (int i = 0; i < t.count("spot"); i++) t.dig("spot", i); t.use("bell", 0); })
                .add();

        Kind.of(FSet.BURIED, 7, "ice_window", "The Ice Window").verb(Verb.DIG).curio().rank(3).lands(Group.SNOW).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A patch of clear ice in the snow, with something dark under it.")
                .learned("Melted slowly, it gave the thing up whole. Broken, it would have broken too.")
                .reach(5).icon(() -> net.minecraft.world.item.Items.ICE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState layer = Blocks.SNOW.defaultBlockState();
                    var layers = net.minecraft.world.level.block.SnowLayerBlock.LAYERS;
                    for (int x = 1; x <= 3; x++)
                        for (int z = -1; z <= 1; z++) {
                            boolean mid = x == 2 && z == 0;
                            s.put(x, 0, z, Blocks.ICE.defaultBlockState());
                            s.part(mid ? "ice" : "rim", x, 0, z);
                            s.put(x, -1, z, mid ? Props.BUNDLE.get().of(2) : Blocks.PACKED_ICE.defaultBlockState());
                        }
                    s.part("cache", 2, 0, 0);
                    s.on(0, 1, layer.setValue(layers, 3));
                    s.on(4, -1, layer.setValue(layers, 4));
                    s.on(4, 1, layer.setValue(layers, 2));
                    s.on(0, -1, layer.setValue(layers, 2));
                    s.on(2, 2, Props.LOOSE_STONE.get().of(0));
                    s.on(2, -2, layer.setValue(layers, 3));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Pour.heat("ice", 3))
                .after(c -> c.set("ice", 0, Blocks.AIR.defaultBlockState()))
                .play(t -> { t.hold(net.minecraft.world.item.Items.TORCH); for (int i = 0; i < 3; i++) t.use("ice", 0); })
                .add();

        Kind.of(FSet.BURIED, 8, "root_cellar", "The Root Cellar").verb(Verb.DIG).find().rank(4).lands(Group.DARK, Group.FOREST, Group.SNOW).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A patch of ground under the trees where nothing grows, deep in old leaves.")
                .learned("A door flat in the earth, and a cellar under it with the shelves still full.")
                .reach(7).icon(() -> net.minecraft.world.item.Items.SPRUCE_TRAPDOOR)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState door = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
                    s.put(3, 0, 0, door);
                    s.partOn("litter", 3, 0, 0, Dress.litter(s));
                    s.partOn("litter", 3, 1, 0, Dress.litter(s));
                    s.partOn("litter", 2, 0, 0, Dress.twigs(s));
                    for (int x = 2; x <= 6; x++)
                        for (int z = -2; z <= 2; z++)
                            for (int dy = -4; dy <= -1; dy++) {
                                boolean wall = x == 2 || x == 6 || Math.abs(z) == 2 || dy == -4 || dy == -1;
                                if (x == 3 && z == 0 && dy == -1) s.put(x, dy, z, Blocks.AIR.defaultBlockState());
                                else if (wall) s.put(x, dy, z, dy == -4 ? m.planks() : (x + z + dy) % 3 == 0 ? m.mossy() : m.stone());
                                else s.put(x, dy, z, Blocks.AIR.defaultBlockState());
                            }
                    for (int dy = -3; dy <= -1; dy++) s.put(3, dy, 0, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
                    s.put(5, -3, -1, Blocks.BARREL.defaultBlockState());
                    s.put(5, -3, 1, Blocks.DECORATED_POT.defaultBlockState());
                    s.put(4, -3, 1, Blocks.DECORATED_POT.defaultBlockState());
                    s.put(5, -3, 0, Blocks.DECORATED_POT.defaultBlockState());
                    s.part("jar", 5, -3, 0);
                    s.part("cache", 4, -3, 0);
                    s.put(4, -2, -1, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
                    Dress.scatter(s, 3, 0, 3, 8, () -> Dress.litter(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Dig.at("litter"), Prize.at("jar")))
                .play(t -> { for (int i = 0; i < 3; i++) t.dig("litter", i); t.use("jar", 0); })
                .add();
    }
}
