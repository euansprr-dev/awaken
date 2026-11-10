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

/** Small Gods (offer): little shrines to animals, and what the animals leave in return. */
final class Gods {
    private Gods() {}

    private static final net.minecraft.world.item.Item[] GREENS = {net.minecraft.world.item.Items.CARROT, net.minecraft.world.item.Items.WHEAT, net.minecraft.world.item.Items.BEETROOT,
            net.minecraft.world.item.Items.DANDELION, net.minecraft.world.item.Items.KELP, net.minecraft.world.item.Items.APPLE, net.minecraft.world.item.Items.MELON_SLICE,
            net.minecraft.world.item.Items.SHORT_GRASS, net.minecraft.world.item.Items.FERN, net.minecraft.world.item.Items.VINE, net.minecraft.world.item.Items.WHEAT_SEEDS,
            net.minecraft.world.item.Items.SWEET_BERRIES, net.minecraft.world.item.Items.GLOW_BERRIES};

    static boolean greens(net.minecraft.world.item.ItemStack st) {
        for (var g : GREENS) if (st.is(g)) return true;
        return st.is(ItemTags.LEAVES) || st.is(ItemTags.SAPLINGS) || flower(st);
    }

    static boolean flower(net.minecraft.world.item.ItemStack st) {
        return st.getItem() instanceof net.minecraft.world.item.BlockItem b && b.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.FLOWERS);
    }

    private static boolean shiny(net.minecraft.world.item.ItemStack st) {
        var i = net.minecraft.world.item.Items.GOLD_NUGGET;
        return st.is(i) || st.is(net.minecraft.world.item.Items.GOLD_INGOT) || st.is(net.minecraft.world.item.Items.IRON_NUGGET) || st.is(net.minecraft.world.item.Items.EMERALD)
                || st.is(net.minecraft.world.item.Items.DIAMOND) || st.is(net.minecraft.world.item.Items.AMETHYST_SHARD) || st.is(net.minecraft.world.item.Items.COPPER_INGOT)
                || st.is(net.minecraft.world.item.Items.GLASS_BOTTLE) || st.is(net.minecraft.world.item.Items.IRON_INGOT) || st.is(net.minecraft.world.item.Items.QUARTZ);
    }

    private static final BlockState LEAF = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    static void init() {
        Kind.of(FSet.GODS, 1, "nine_cats", "The Shrine of Nine Cats").verb(Verb.OFFER).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.DARK, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A knee-high stone shrine in long grass with a bowl in front of it, and cats asleep on every side.")
                .learned("They take fish. One of them brought you something it had found.")
                .reach(7).icon(Props.STONE_BOWL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.on(0, -1, m.wall());
                    s.up(0, -1, 1, m.slab());
                    s.partOn("bowl", 0, 0, 0, Props.STONE_BOWL.get().of(0));
                    s.partOn("cache", 0, 1, 0);
                    int cats = 5 + s.rnd.nextInt(5);
                    for (int i = 0; i < cats; i++) {
                        double a = i * 6.283 / cats + s.rnd.nextDouble() * 0.5, r = 2.2 + s.rnd.nextDouble() * 2.2;
                        s.spawn(net.minecraft.world.entity.EntityTypes.CAT, (int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r), Scene.Spawn.LIE | Scene.Spawn.NO_AI, "cat");
                    }
                    s.on(-2, -2, Blocks.DANDELION.defaultBlockState());
                    s.on(2, -1, Blocks.POPPY.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("bowl", st -> st.is(ItemTags.FISHES), i -> Props.STONE_BOWL.get().of(1)))
                .play(t -> t.hold(net.minecraft.world.item.Items.COD).use("bowl", 0))
                .add();

        Kind.of(FSet.GODS, 2, "toad_stone", "The Toad Stone").verb(Verb.OFFER).curio().rank(0).lands(Group.SWAMP, Group.FOREST, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A fat mossy boulder that somebody has given a mouth: a stone bowl set in its front, with flies round it.")
                .learned("It likes things that crawl. Fed, it gave something back.")
                .reach(5).tell(Tell.DRIP).icon(Props.BOULDER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, Props.BOULDER.get().of(1));
                    s.up(0, 0, 1, Props.FLAT_STONES.get().of(0));
                    s.on(-1, 0, Props.LOOSE_STONE.get().of(3));
                    s.on(1, 0, Props.LOOSE_STONE.get().of(3));
                    s.partOn("mouth", 0, 1, 0, Props.STONE_BOWL.get().of(3));
                    s.partOn("cache", 1, 1, 0);
                    s.on(-2, 1, Blocks.FERN.defaultBlockState());
                    s.on(2, -1, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    s.tell(0, 0, 2);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Sockets.need("mouth", st -> st.is(net.minecraft.world.item.Items.SPIDER_EYE) || st.is(net.minecraft.world.item.Items.SLIME_BALL) || st.is(net.minecraft.world.item.Items.STRING)
                        || st.is(net.minecraft.world.item.Items.ROTTEN_FLESH), i -> Props.STONE_BOWL.get().of(1)))
                .after(c -> c.sound(c.site.ground().above(), net.minecraft.sounds.SoundEvents.FROG_AMBIENT, 1.5F, 0.5F))
                .play(t -> t.hold(net.minecraft.world.item.Items.SPIDER_EYE).use("mouth", 0))
                .add();

        Kind.of(FSet.GODS, 3, "owl_post", "The Owl Post").verb(Verb.OFFER).curio().rank(1).lands(Group.FOREST, Group.DARK, Group.SNOW).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A post carved as an owl, with a little shelf under each wing. One shelf holds a page.")
                .learned("They trade. A page of yours for a page of theirs.")
                .reach(5).loot("pages").icon(() -> net.minecraft.world.item.Items.PAPER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.fence());
                    s.up(0, 0, 1, m.log());
                    s.up(0, 0, 2, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(net.minecraft.world.level.block.CarvedPumpkinBlock.FACING, Direction.SOUTH));
                    s.up(0, 0, 3, m.woodSlab());
                    s.partOn("shelf", -1, 0, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
                    s.up(1, 0, 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
                    s.partOn("cache", 0, 1, 0);
                    s.spawnUp(Life.type("hollow_owl", net.minecraft.world.entity.EntityTypes.PARROT), 0, 0, 4, 0, "owl");
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("shelf").holding(st -> st.is(net.minecraft.world.item.Items.PAPER) || st.is(net.minecraft.world.item.Items.BOOK) || st.is(net.minecraft.world.item.Items.WRITABLE_BOOK)
                        || st.is(net.minecraft.world.item.Items.MAP)).takes())
                .play(t -> t.hold(net.minecraft.world.item.Items.PAPER).use("shelf", 0))
                .add();

        Kind.of(FSet.GODS, 4, "hares_table", "The Hare's Table").verb(Verb.OFFER).curio().rank(1).lands(Group.PLAINS, Group.SNOW, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A flat stone set low in the grass, with hare tracks all round it.")
                .learned("Greens left at dusk were gone by morning, and there were seeds in their place.")
                .reach(5).loot("seeds").icon(Props.CAP_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.partOn("table", 0, 0, 0, Props.CAP_STONE.get().of(2));
                    s.partOn("cache", 0, 0, 1);
                    for (int i = 0; i < 6; i++) {
                        double a = i * 1.05 + s.rnd.nextDouble() * 0.4;
                        int x = (int) Math.round(Math.cos(a) * 2), z = (int) Math.round(Math.sin(a) * 2);
                        if (x != 0 || z != 0) s.floor(x, z, i % 2 == 0 ? m.loose() : Blocks.ROOTED_DIRT.defaultBlockState());
                    }
                    s.on(2, 2, Blocks.SHORT_GRASS.defaultBlockState());
                    s.on(-2, 1, Blocks.FERN.defaultBlockState());
                    s.spawn(net.minecraft.world.entity.EntityTypes.RABBIT, 3, -2, 0, "hare");
                    s.spawn(net.minecraft.world.entity.EntityTypes.RABBIT, -3, 2, 0, "hare");
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Steps.of(When.only(When.DUSK.or(When.NIGHT), Sockets.need("table", Gods::greens, i -> Props.CAP_STONE.get().of(0))), Later.morning()))
                .play(t -> t.hour(12500).hold(net.minecraft.world.item.Items.CARROT).use("table", 0).hour(1000).ticks(1).pass(700).ticks(1))
                .add();

        Kind.of(FSet.GODS, 5, "fox_gates", "The Fox Gates").verb(Verb.OFFER).curio().rank(2).lands(Group.FOREST, Group.SNOW, Group.DARK).anchor(Anchor.LINE).length(18).made(Made.BUILT)
                .see("A row of small red arches leading into a thicket, with a bowl in front of the first.")
                .learned("An egg at the first gate. At the end of the row, the fox let you see what it had been collecting.")
                .reach(10).icon(() -> net.minecraft.world.item.Items.EGG)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    BlockState post = Blocks.MANGROVE_FENCE.defaultBlockState(), top = Blocks.MANGROVE_SLAB.defaultBlockState();
                    for (int z = -4; z <= 4; z += 2) {
                        s.column(-1, z, 0, 1, post);
                        s.column(1, z, 0, 1, post);
                        for (int x = -1; x <= 1; x++) s.up(x, z, 2, top);
                        s.floor(0, z, m.path());
                    }
                    s.partOn("bowl", 0, -6, 0, Props.STONE_BOWL.get().of(0));
                    for (int x = -2; x <= 2; x++)
                        for (int z = 6; z <= 8; z++)
                            if ((x != 0 || z != 6) && !(x == 0 && z == 7)) s.column(x, z, 0, 1 + s.rnd.nextInt(2), LEAF);
                    s.partOn("den", 0, 7, 0, Props.HOARD.get().of(0));
                    s.partOn("cache", 0, 6, 0);
                    s.spawn(net.minecraft.world.entity.EntityTypes.FOX, 0, 5, 0, "fox");
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Steps.of(Sockets.need("bowl", st -> st.is(ItemTags.EGGS) || st.is(net.minecraft.world.item.Items.SWEET_BERRIES) || st.is(net.minecraft.world.item.Items.CHICKEN),
                        i -> Props.STONE_BOWL.get().of(1)), Prize.at("den")))
                .play(t -> t.hold(net.minecraft.world.item.Items.EGG).use("bowl", 0).use("den", 0))
                .add();

        Kind.of(FSet.GODS, 6, "bee_stone", "The Bee Stone").verb(Verb.OFFER).curio().rank(2).lands(Group.PLAINS, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A stone with a nest of wild bees built into it, and a small dish set in front.")
                .learned("A flower in the dish. They took it, and left honey for it.")
                .reach(5).loot("honey").icon(() -> net.minecraft.world.item.Items.HONEYCOMB)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.mossy());
                    s.up(0, 0, 1, Blocks.BEE_NEST.defaultBlockState());
                    s.up(0, 0, 2, m.slab());
                    s.on(-1, 0, m.stairs().hasProperty(StairBlock.FACING) ? m.stairs().setValue(StairBlock.FACING, Direction.EAST) : m.stairs());
                    s.on(1, 0, m.stairs().hasProperty(StairBlock.FACING) ? m.stairs().setValue(StairBlock.FACING, Direction.WEST) : m.stairs());
                    s.partOn("dish", 0, 1, 0, Props.STONE_BOWL.get().of(0));
                    s.partOn("cache", 1, 1, 0);
                    net.minecraft.world.level.block.Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET};
                    for (int i = 0; i < 6; i++) {
                        int x = -3 + s.rnd.nextInt(7), z = -3 + s.rnd.nextInt(7);
                        if (Math.abs(x) + Math.abs(z) > 2 && !s.taken(x, s.g(x, z) + 1, z)) s.on(x, z, fl[s.rnd.nextInt(fl.length)].defaultBlockState());
                    }
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Sockets.need("dish", Gods::flower, i -> Props.STONE_BOWL.get().of(2)))
                .play(t -> t.hold(net.minecraft.world.item.Items.POPPY).use("dish", 0))
                .add();

        Kind.of(FSet.GODS, 7, "crows_court", "The Crows' Court").verb(Verb.OFFER).find().rank(3).lands(Group.BADLANDS, Group.DARK, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A dead tree with birds all over it, above a flat stone covered in shiny rubbish.")
                .learned("Leave something that shines. The better it is, the better they pay.")
                .reach(7).icon(Props.HOARD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    BlockState log = m.log(), bx = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : log,
                            bz = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z) : log;
                    s.column(0, 0, 0, 7, log);
                    s.up(1, 0, 5, bx);
                    s.up(2, 0, 5, bx);
                    s.up(-1, 0, 6, bx);
                    s.up(0, 1, 4, bz);
                    s.up(0, 2, 4, bz);
                    s.up(0, -1, 7, bz);
                    var bird = Life.type("crag_feather", net.minecraft.world.entity.EntityTypes.PARROT);
                    s.spawnUp(bird, 2, 0, 6, Scene.Spawn.NO_AI, "crow");
                    s.spawnUp(bird, 0, 2, 5, Scene.Spawn.NO_AI, "crow");
                    s.spawnUp(bird, -1, 0, 7, Scene.Spawn.NO_AI, "crow");
                    s.partOn("stone", 2, 2, 0, Props.CAP_STONE.get().of(0));
                    s.on(3, 2, Props.HOARD.get().of(0));
                    s.on(2, 3, Props.LOOSE_STONE.get().of(1));
                    s.partOn("cache", 2, 2, 1);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Sockets.need("stone", Gods::shiny, i -> Props.CAP_STONE.get().of(2)))
                .play(t -> t.hold(net.minecraft.world.item.Items.GOLD_NUGGET).use("stone", 0))
                .add();

        Kind.of(FSet.GODS, 8, "snail_shrine", "The Snail Shrine").verb(Verb.OFFER).find().rank(4).lands(Group.JUNGLE, Group.SWAMP, Group.DARK).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A shrine roofed with one great pale shell. Silver trails cover the ground and all of them lead in.")
                .learned("Greens left overnight. By morning the trails had gathered on one spot, and under it was a pearl.")
                .reach(7).icon(() -> net.minecraft.world.item.Items.NAUTILUS_SHELL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    BlockState shell = Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState(), cap = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
                    for (int x = -1; x <= 1; x += 2)
                        for (int z = -1; z <= 1; z += 2) s.column(x, z, 0, 1, m.wall());
                    for (int d = 0; d < 4; d++) {
                        Direction dir = Direction.from2DDataValue(d);
                        s.up(dir.getStepX(), dir.getStepZ(), 2, shell.setValue(StairBlock.FACING, dir.getOpposite()));
                    }
                    for (int x = -1; x <= 1; x += 2)
                        for (int z = -1; z <= 1; z += 2) s.up(x, z, 2, cap);
                    s.up(0, 0, 2, cap);
                    s.partOn("bowl", 0, 0, 0, Props.STONE_BOWL.get().of(0));
                    BlockState trail = Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY).defaultBlockState();
                    for (int i = 0; i < 5; i++) {
                        double a = i * 1.26 + 0.4;
                        for (int r = 3; r <= 6; r++) {
                            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                            if (!s.taken(x, s.g(x, z) + 1, z) && s.rnd.nextInt(4) != 0) s.on(x, z, trail);
                        }
                    }
                    s.floor(4, 4, Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("spot", 4, s.g(4, 4), 4);
                    s.part("cache", 4, s.g(4, 4) + 1, 4);
                    var snail = Life.type("gloam_snail", net.minecraft.world.entity.EntityTypes.TURTLE);
                    s.spawn(snail, -3, 2, 0, "snail");
                    s.spawn(snail, 2, -3, 0, "snail");
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Steps.of(Sockets.need("bowl", Gods::greens, i -> Props.STONE_BOWL.get().of(1)), Later.morning(), Dig.at("spot")))
                .play(t -> t.hold(net.minecraft.world.item.Items.KELP).use("bowl", 0).hour(1000).ticks(1).pass(700).ticks(1).dig("spot", 0))
                .add();
    }
}
