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

/** Hung Things (pull): ropes, and what's on the other end. */
final class Hung {
    private Hung() {}

    private static final BlockState LEAF = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    private static BlockState axis(BlockState log, Direction.Axis a) {
        return log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, a) : log;
    }

    /** A dry gully across the kind's east-west line, three wide and two deep, a block shallower over the heart. */
    static void gully(Scene s, int x0, int x1) {
        for (int x = x0; x <= x1; x++)
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    s.put(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
                    continue;
                }
                s.put(x, 0, z, Blocks.AIR.defaultBlockState());
                s.put(x, -1, z, s.rnd.nextInt(3) == 0 ? Blocks.MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
            }
    }

    static void init() {
        Kind.of(FSet.HUNG, 1, "hanging_bundle", "The Hanging Bundle").verb(Verb.PULL).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A wrapped bundle hung high from a branch, on a rope that runs down to a peg.")
                .learned("A traveller hung it out of reach of animals and never came back. Lowered slowly, it didn't burst.")
                .reach(7).icon(Props.BUNDLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.column(0, 0, 0, 6, m.log());
                    for (int x = 1; x <= 3; x++) s.up(x, 0, 5, axis(m.log(), Direction.Axis.X));
                    Dress.crown(s, 0, 0, 6, 3, LEAF);
                    Dress.crown(s, 2, 0, 6, 2, LEAF);
                    s.up(3, 0, 4, Props.ROPE_LENGTH.get().of(0));
                    s.partOn("load", 3, 0, 3, Props.BUNDLE.get().of(0));
                    for (int k = 2; k >= 0; k--) s.partOn("load", 3, 0, k);
                    s.partOn("rope", -2, 1, 0, Props.ROPE_PEG.get().of(0));
                    s.partOn("cache", 3, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Haul.on("rope", "load").gently().trail(Props.ROPE_LENGTH.get().of(0)))
                .play(t -> t.use("rope", 0).pass(30).use("rope", 0).pass(30).use("rope", 0))
                .add();

        Kind.of(FSet.HUNG, 2, "the_plug", "The Plug").verb(Verb.PULL).curio().rank(0).lands(Group.PLAINS, Group.SWAMP, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A small pond with a rope going into the middle of it from a post on the bank.")
                .learned("Hauled, the plug came out and the pond drained in a minute. It had been keeping something.")
                .reach(8).slope(1).icon(Props.PLUG)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    for (int x = -2; x <= 2; x++)
                        for (int z = 2; z <= 6; z++) {
                            boolean rim = Math.abs(x) == 2 || z == 2 || z == 6;
                            s.put(x, -1, z, Blocks.MUD.defaultBlockState());
                            if (rim) s.put(x, 0, z, s.rnd.nextBoolean() ? Blocks.MUD.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                            else {
                                s.put(x, 0, z, Blocks.WATER.defaultBlockState());
                                s.part("water", x, 0, z);
                            }
                        }
                    s.put(0, -1, 4, Props.PLUG.get().of(0));
                    s.part("load", 0, -1, 4);
                    s.part("load", 0, 1, 2);
                    s.part("load", 1, 1, 1);
                    s.put(0, -2, 4, Props.HOARD.get().of(0));
                    s.part("cache", 0, 0, 4);
                    s.partOn("rope", 0, 0, 0, Props.ROPE_PEG.get().of(0));
                    s.on(1, 0, Props.ROPE_COIL.get().of(0));
                    s.on(-3, 4, Blocks.FERN.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Haul.on("rope", "load"))
                .after(c -> { for (int i = 0; i < c.count("water"); i++) c.set("water", i, Blocks.AIR.defaultBlockState()); })
                .play(t -> t.use("rope", 0).use("rope", 0))
                .add();

        Kind.of(FSet.HUNG, 3, "drawbridge", "The Drawbridge").verb(Verb.PULL).curio().rank(1).lands(Group.FOREST, Group.PLAINS, Group.JUNGLE, Group.SWAMP).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A plank bridge standing straight up on the far side of a gully, with its rope tied off on this side.")
                .learned("Three pulls laid it down. It stays down, and the toll box on the far side was never emptied.")
                .reach(8).persists().icon(() -> net.minecraft.world.item.Items.OAK_PLANKS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    gully(s, -4, 4);
                    for (int k = 0; k < 3; k++) {
                        s.partOn("up", 1, 2, k, m.planks());
                        s.part("deck", 1, 1, 1 - k);
                    }
                    s.partOn("rope", 2, -2, 0, Props.ROPE_PEG.get().of(0));
                    s.on(0, 2, m.fence());
                    s.on(2, 2, m.fence());
                    s.partOn("cache", 1, 4, 0, Props.FOOTING.get().front(0, Direction.NORTH));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Swap.pull("rope", "up", "deck", Blocks.OAK_SLAB.defaultBlockState()))
                .play(t -> t.use("rope", 0).use("rope", 0).use("rope", 0))
                .add();

        Kind.of(FSet.HUNG, 4, "root_handle", "The Root Handle").verb(Verb.PULL).curio().rank(1).lands(Group.JUNGLE, Group.FOREST, Group.DARK, Group.SWAMP).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A fat root sticking out of an earth bank like a handle.")
                .learned("It came a little each pull and the bank shook. On the third it was a door, onto somebody's winter store.")
                .reach(6).icon(() -> net.minecraft.world.item.Items.HANGING_ROOTS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -2; x <= 2; x++)
                        for (int z = -4; z <= -2; z++)
                            for (int k = 0; k <= 2; k++) {
                                boolean hollow = x == 0 && z == -3 && k <= 1;
                                boolean face = x == 0 && z == -2 && k <= 1;
                                if (hollow || face) continue;
                                if (k == 2 && (Math.abs(x) == 2 || z == -4) && s.rnd.nextBoolean()) continue;
                                s.up(x, z, k, k == 2 ? Blocks.GRASS_BLOCK.defaultBlockState() : (x + z + k) % 3 == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                            }
                    s.partOn("door", 0, -2, 1, Blocks.ROOTED_DIRT.defaultBlockState());
                    s.partOn("door", 0, -2, 0, Blocks.ROOTED_DIRT.defaultBlockState());
                    s.partOn("door", 0, -1, 0, Blocks.MANGROVE_ROOTS.defaultBlockState());
                    s.partOn("root", 0, -1, 0);
                    s.on(0, -3, Props.BASKET.get().of(1));
                    s.partOn("cache", 0, -3, 0);
                    s.up(1, -2, 3, Blocks.SHORT_GRASS.defaultBlockState());
                    s.up(-1, -3, 3, Blocks.FERN.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Swap.pull("root", "door", "", Blocks.AIR.defaultBlockState()).sound(net.minecraft.sounds.SoundEvents.ROOTED_DIRT_BREAK))
                .play(t -> t.use("root", 0).use("root", 0).use("root", 0))
                .add();

        Kind.of(FSet.HUNG, 5, "cliff_rope", "The Cliff Rope").verb(Verb.PULL).curio().rank(2).lands(Group.MOUNTAIN, Group.BADLANDS, Group.SNOW).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("A rope hanging down a cliff from a beam somewhere out of sight.")
                .learned("Pulled, a basket came down. It takes one, and the ledge it goes to has a long view.")
                .reach(8).icon(Props.BASKET)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    int top = Math.max(10, s.g(0, 4) + 3);
                    for (int dy = s.g(0, 4) + 1; dy <= top; dy++) s.put(0, dy, 4, m.fence());
                    for (int z = 0; z <= 3; z++) s.put(0, top, z, axis(m.log(), Direction.Axis.Z));
                    for (int dy = 2; dy < top; dy++) s.put(0, dy, 0, Props.ROPE_LENGTH.get().of(0));
                    s.part("rope", 0, 2, 0);
                    s.put(1, top, 1, m.fence());
                    for (int dy = top - 1; dy >= 1; dy -= 2) s.part("load", 1, dy, 1);
                    s.put(1, top - 1, 1, Props.BASKET.get().of(0));
                    s.part("basket", 1, top - 1 - 2 * ((top - 2) / 2), 1);
                    s.part("ledge", 0, s.g(0, 5) + 1, 5);
                    s.part("cache", 1, s.g(1, 5) + 1, 5);
                    s.put(1, s.g(1, 5) + 1, 5, Props.FOOTING.get().of(0));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Steps.of(Haul.on("rope", "load").trail(Props.ROPE_LENGTH.get().of(0)), Prize.at("basket").lift("ledge")))
                .play(t -> { for (int i = 0; i < t.count("load") - 1; i++) t.use("rope", 0); t.use("basket", 0); })
                .add();

        Kind.of(FSet.HUNG, 6, "net_in_the_trees", "The Net in the Trees").verb(Verb.PULL).find().rank(2).lands(Group.JUNGLE, Group.DARK, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A full net slung between two trees, held by four ropes pegged to the ground. One peg has a stone set against it.")
                .learned("The weighted peg was the one. The others only let it lurch.")
                .reach(8).icon(Props.ROPE_PEG)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    s.column(-3, 0, 0, 6, m.log());
                    s.column(3, 0, 0, 6, m.log());
                    for (int x = -2; x <= 2; x++) s.up(x, 0, 4, x == 0 ? Blocks.MANGROVE_ROOTS.defaultBlockState() : Props.ROPE_LENGTH.get().of(3, Direction.EAST));
                    s.up(0, 0, 5, Props.BUNDLE.get().of(2));
                    s.up(-1, 0, 5, Props.BASKET.get().of(1));
                    for (int x = -4; x <= 4; x += 8)
                        for (int k = 6; k <= 7; k++)
                            for (int z = -1; z <= 1; z++) s.up(x + (x < 0 ? 1 : -1), z, k, LEAF);
                    int[][] pegs = {{-2, 3}, {2, 3}, {-2, -3}, {2, -3}};
                    int right = Pick.right(s.site.seed(), 4);
                    for (int i = 0; i < 4; i++) {
                        s.partOn("peg", pegs[i][0], pegs[i][1], 0, Props.ROPE_PEG.get().of(0));
                        if (i == right) s.on(pegs[i][0] + 1, pegs[i][1], Props.LOOSE_STONE.get().of(0));
                    }
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Pick.oneOf("peg"))
                .play(t -> t.use("peg", Pick.right(t.seed(), 4)))
                .add();

        Kind.of(FSet.HUNG, 7, "log_dam", "The Log Dam").verb(Verb.PULL).find().rank(3).lands(Group.MOUNTAIN, Group.FOREST, Group.SNOW).anchor(Anchor.SLOPE).made(Made.BUILT)
                .see("A dam of logs across a cut in the hillside, holding back a pool, with a chain run through it and a dry bed below.")
                .learned("One pull on the chain and the dam went. Everything the dry bed was hiding ended up in a heap at the bottom.")
                .reach(13).slope(12).icon(() -> net.minecraft.world.item.Items.IRON_CHAIN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int z = -7; z <= 11; z++)
                        for (int x = -1; x <= 1; x++) {
                            if (x == 0 && z == 0) {
                                s.floor(x, z, Blocks.GRAVEL.defaultBlockState());
                                continue;
                            }
                            int g = s.g(x, z);
                            boolean pool = z <= -4 && z >= -6;
                            if (z == -7) continue;
                            if (z == -3) {
                                s.put(x, g, z, axis(m.log(), Direction.Axis.X));
                                s.part("dam", x, g, z);
                            } else {
                                s.put(x, g, z, pool ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
                                s.put(x, g - 1, z, Blocks.GRAVEL.defaultBlockState());
                            }
                        }
                    s.partOn("chain", 2, -3, 0, Props.ROPE_PEG.get().of(0));
                    s.on(-2, -3, m.wall());
                    s.part("cache", 0, s.g(0, 10), 10);
                    s.put(0, s.g(0, 10), 10, Props.HOARD.get().of(0));
                    Dress.scatter(s, 0, 8, 2, 4, () -> Dress.twigs(s));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Swap.pull("chain", "dam", "", Blocks.AIR.defaultBlockState()).atOnce().sound(net.minecraft.sounds.SoundEvents.CHAIN_PLACE))
                .play(t -> t.use("chain", 0))
                .add();

        Kind.of(FSet.HUNG, 8, "sky_anchor", "The Sky Anchor").verb(Verb.PULL).find().rank(4).lands(Group.PLAINS, Group.DESERT, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("A chain going straight up out of the ground to something small and green a long way overhead, with a winch at its foot.")
                .learned("Wound, a basket came down the chain. What it's anchored to is a scrap of land with nobody's footprints on it.")
                .reach(6).icon(Props.WINCH)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();
                    s.put(0, 1, 0, m.stone());
                    for (int dy = 2; dy <= 30; dy++) s.put(0, dy, 0, chain);
                    s.put(1, 1, 0, Props.WINCH.get().of(1, Direction.EAST));
                    s.part("winch", 1, 1, 0);
                    for (int dy = 29; dy >= 1; dy -= 7) s.part("load", -1, dy, 0);
                    s.put(-1, 29, 0, Props.BASKET.get().of(0));
                    s.part("basket", -1, 1, 0);
                    for (int x = -2; x <= 2; x++)
                        for (int z = -2; z <= 2; z++) {
                            if (Math.abs(x) + Math.abs(z) > 3 || (x == -1 && z == 0)) continue;
                            s.put(x, 31, z, Blocks.GRASS_BLOCK.defaultBlockState());
                            if (Math.abs(x) + Math.abs(z) <= 1) s.put(x, 30, z, x == 0 && z == 0 ? chain : Blocks.ROOTED_DIRT.defaultBlockState());
                        }
                    s.put(-1, 31, 0, Blocks.OAK_SLAB.defaultBlockState());
                    s.put(-1, 30, 0, m.fence());
                    s.part("top", 0, 32, 1);
                    s.put(1, 32, -1, Blocks.FERN.defaultBlockState());
                    s.put(-1, 32, 1, Blocks.DANDELION.defaultBlockState());
                    s.put(1, 32, 1, Props.FOOTING.get().of(0));
                    s.part("cache", 1, 32, 1);
                    for (int x = 2; x <= 3; x++)
                        for (int z = 2; z <= 3; z++) {
                            s.put(x, 0, z, Blocks.WATER.defaultBlockState());
                            s.put(x, -1, z, m.stone());
                        }
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Haul.on("winch", "load").trail(Blocks.AIR.defaultBlockState()), Prize.at("basket").lift("top")))
                .play(t -> { for (int i = 0; i < t.count("load") - 1; i++) t.use("winch", 0); t.use("basket", 0); })
                .add();
    }
}
