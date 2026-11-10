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

/** The Stilled at Rest (carry): stone people stopped in the middle of something, and the thing they're missing. */
final class Stilled {
    private Stilled() {}

    private static java.util.function.Predicate<net.minecraft.world.item.ItemStack> is(java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> b) {
        return st -> st.is(b.get().asItem());
    }

    static void init() {
        Kind.of(FSet.STILLED, 1, "stilled_fisher", "The Stilled Fisher").verb(Verb.CARRY).curio().rank(0).anchor(Anchor.SHORE).made(Made.STILLED)
                .see("A stone figure sitting on the bank with its hands held out, closed round nothing. A rod lies in the reeds.")
                .learned("It was fishing when it stopped. With the rod back in its hands, the float bobbed once.")
                .reach(8).slope(3).icon(Props.OLD_ROD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 0, Props.STILLED.get().front(2, Direction.SOUTH));
                    s.partOn("loose", -3, -1, 0, Props.OLD_ROD.get().of(0));
                    s.partOn("socket", 0, 1, 0);
                    s.part("cache", 0, s.sea() + 1, 4);
                    for (int i = 0; i < 7; i++) {
                        int x = -4 + s.rnd.nextInt(9), z = -2 + s.rnd.nextInt(4);
                        if (s.depth(x, z) == 0 && !s.taken(x, s.g(x, z) + 1, z)) s.on(x, z, (s.rnd.nextBoolean() ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
                    }
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("socket", is(Props.OLD_ROD), i -> Props.OLD_ROD.get().front(0, Direction.SOUTH)).carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.OLD_ROD.get()).use("socket", 0))
                .add();

        Kind.of(FSet.STILLED, 2, "card_game", "The Card Game").verb(Verb.CARRY).curio().rank(0).lands(Group.FOREST, Group.PLAINS, Group.DARK).anchor(Anchor.FLAT).made(Made.STILLED)
                .see("Three stone figures round a stump. The fourth seat is empty and its cards are scattered in the leaves.")
                .learned("Four hands were dealt. With the fourth laid down, the pot on the stump was anyone's.")
                .reach(6).icon(Props.STONE_CARDS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.partOn("stump", 0, 0, 0, m.log());
                    for (int i = 0; i < 4; i++) s.partOn("build", 0, 0, 1);
                    s.on(-1, 0, Props.STILLED.get().front(2, Direction.EAST));
                    s.on(1, 0, Props.STILLED.get().front(2, Direction.WEST));
                    s.on(0, -1, Props.STILLED.get().front(2, Direction.SOUTH));
                    int[][] at = {{2, 2}, {-2, 3}, {0, 4}, {3, -2}};
                    for (int[] a : at) s.partOn("loose", a[0], a[1], 0, Props.STONE_CARDS.get().of(0, Direction.from2DDataValue(s.rnd.nextInt(4))));
                    s.partOn("cache", 0, 1, 0);
                    Dress.scatter(s, 0, 0, 4, 6, () -> Dress.litter(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Assemble.of("build", is(Props.STONE_CARDS), Props.STONE_CARDS.get().of(0), Props.STONE_CARDS.get().of(1), Props.STONE_CARDS.get().of(2), Props.STONE_CARDS.get().of(3)).base("stump").carry("loose"))
                .play(t -> { for (int i = 0; i < 4; i++) t.use("loose", i).hold(Props.STONE_CARDS.get()).use("stump", 0); })
                .add();

        Kind.of(FSet.STILLED, 3, "stilled_shepherd", "The Stilled Shepherd").verb(Verb.CARRY).curio().rank(1).lands(Group.PLAINS, Group.MOUNTAIN, Group.SNOW).anchor(Anchor.SLOPE).made(Made.STILLED)
                .see("A stone shepherd looking uphill, with stone sheep round it. One lies on its side further down the slope.")
                .learned("It's heavy, and you can't run with it. The flock is whole again.")
                .reach(10).slope(12).icon(Props.STONE_SHEEP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, -4, Props.STILLED.get().front(0, Direction.NORTH));
                    s.on(-2, -5, Props.STONE_SHEEP.get().front(0, Direction.NORTH));
                    s.on(2, -6, Props.STONE_SHEEP.get().front(0, Direction.WEST));
                    s.on(-1, -2, Props.STONE_SHEEP.get().front(0, Direction.EAST));
                    s.partOn("socket", 1, -3, 0);
                    s.partOn("loose", 2, 6, 0, Props.STONE_SHEEP.get().of(1, Direction.EAST));
                    s.partOn("cache", 0, -3, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("socket", is(Props.STONE_SHEEP), i -> Props.STONE_SHEEP.get().front(0, Direction.NORTH)).carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.STONE_SHEEP.get()).use("socket", 0))
                .add();

        Kind.of(FSet.STILLED, 4, "stilled_dancers", "The Stilled Dancers").verb(Verb.CARRY).find().rank(1).lands(Group.PLAINS, Group.SNOW, Group.FOREST).anchor(Anchor.FLAT).made(Made.STILLED)
                .see("A ring of stone dancers with their arms out, and two gaps in it. Two more lie face down nearby.")
                .learned("With the ring closed, a tune you could nearly hear went round it once.")
                .reach(9).icon(Props.STILLED)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(7);
                    int[][] ring = {{4, 0}, {3, 3}, {0, 4}, {-3, 3}, {-4, 0}, {-3, -3}, {0, -4}, {3, -3}};
                    Direction[] in = {Direction.WEST, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.EAST, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    for (int i = 0; i < 8; i++) {
                        if (i == 2 || i == 5) s.partOn("socket", ring[i][0], ring[i][1], 0);
                        else s.on(ring[i][0], ring[i][1], Props.STILLED.get().front(1, in[i]));
                    }
                    s.partOn("loose", 6, 2, 0, Props.STILLED.get().of(3, Direction.EAST));
                    s.partOn("loose", -5, 6, 0, Props.STILLED.get().of(3, Direction.SOUTH));
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 7, m);
                })
                .script(() -> Sockets.need("socket", is(Props.STILLED), i -> Props.STILLED.get().front(1, i == 0 ? Direction.NORTH : Direction.EAST)).carry("loose"))
                .after(c -> c.sound(c.site.ground().above(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.9F))
                .play(t -> { for (int i = 0; i < 2; i++) t.use("loose", i).hold(Props.STILLED.get()).use("socket", i); })
                .add();

        Kind.of(FSet.STILLED, 5, "the_kite", "The Kite").verb(Verb.CARRY).curio().rank(2).lands(Group.PLAINS, Group.FOREST, Group.SNOW).anchor(Anchor.HILLTOP).made(Made.STILLED)
                .see("A small stone figure looking up, one arm raised. Something red is caught in a tree a little way off.")
                .learned("Tied back on its string, it flew for as long as the wind held.")
                .reach(11).slope(4).icon(Props.KITE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.on(0, 0, Props.STILLED_CHILD.get().front(0, Direction.EAST));
                    s.partOn("socket", 0, 0, 1);
                    s.partOn("string", 0, 0, 0);
                    BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    s.column(7, 3, 0, 3, m.log());
                    for (int x = 5; x <= 9; x++)
                        for (int z = 1; z <= 5; z++)
                            for (int k = 3; k <= 5; k++)
                                if (!(x == 7 && z == 3 && k == 3) && Math.abs(x - 7) + Math.abs(z - 3) + (k - 3) <= 3 && !(x == 6 && z == 3 && k == 3)) s.up(x, z, k, leaf);
                    s.partOn("loose", 6, 3, 3, Props.KITE.get().of(0, Direction.EAST));
                    s.partOn("cache", 1, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Assemble.of("socket", is(Props.KITE), Props.KITE.get().of(0)).base("string").carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.KITE.get()).use("string", 0))
                .add();

        Kind.of(FSet.STILLED, 6, "stilled_smith", "The Stilled Smith").verb(Verb.CARRY).find().rank(2).lands(Group.BADLANDS, Group.MOUNTAIN, Group.PLAINS).anchor(Anchor.FLAT).made(Made.STILLED)
                .see("A stone smith with an arm raised over a cold anvil with nothing on it. A half-made blade lies in the ash.")
                .learned("Fire lit, blade laid on the anvil, three blows where the hammer would have fallen. It's finished.")
                .reach(7).icon(Props.BLADE_BLANK)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.partOn("anvil", 0, 0, 0, Blocks.ANVIL.defaultBlockState());
                    s.partOn("blade", 0, 0, 1);
                    s.on(0, 1, Props.STILLED.get().front(1, Direction.NORTH));
                    s.partOn("forge", -2, 0, 0, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                    s.on(-2, -1, m.wall());
                    s.on(-3, 0, m.wall());
                    s.floor(-2, 1, Blocks.GRAVEL.defaultBlockState());
                    s.floor(-3, 1, Blocks.COARSE_DIRT.defaultBlockState());
                    s.partOn("loose", -3, 1, 0, Props.BLADE_BLANK.get().of(0, Direction.EAST));
                    s.on(2, -1, Blocks.GRINDSTONE.defaultBlockState());
                    s.on(2, 1, Blocks.CAULDRON.defaultBlockState());
                    s.partOn("cache", 1, 0, 0);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Steps.of(Light.all("forge"), Sockets.need("anvil", is(Props.BLADE_BLANK), i -> Props.BLADE_BLANK.get().of(1, Direction.EAST)).onTop().carry("loose"),
                        Seal.opens("blade", 3, st -> true)))
                .play(t -> t.hold(net.minecraft.world.item.Items.FLINT_AND_STEEL).use("forge", 0).use("loose", 0).hold(Props.BLADE_BLANK.get()).use("anvil", 0).hit("blade", 0).hit("blade", 0).hit("blade", 0))
                .add();

        Kind.of(FSet.STILLED, 7, "two_on_the_bank", "The Two on the Bank").verb(Verb.CARRY).curio().rank(3).lands(Group.JUNGLE, Group.FOREST, Group.SWAMP, Group.PLAINS).anchor(Anchor.FLAT).made(Made.STILLED)
                .see("Two stone figures reaching for each other across a gully where a plank bridge has fallen in.")
                .learned("Three planks made a bridge. They never moved, but they're on the same side now.")
                .reach(7).persists().icon(Props.STILLED)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int x = -4; x <= 4; x++)
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && z == 0) {
                                s.put(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState()); // the heart is under the middle: the gully is a block shallower there
                                continue;
                            }
                            s.put(x, 0, z, Blocks.AIR.defaultBlockState());
                            s.put(x, -1, z, s.rnd.nextInt(3) == 0 ? Blocks.MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                        }
                    s.put(2, 0, 0, m.woodSlab());
                    s.put(-3, 0, 1, m.woodSlab());
                    s.partOn("mover", 0, -3, 0, Props.STILLED.get().front(1, Direction.SOUTH));
                    s.on(0, 3, Props.STILLED.get().front(1, Direction.NORTH));
                    s.partOn("dest", 1, 3, 0);
                    s.partOn("post", 1, -2, 0, m.fence());
                    for (int z = -1; z <= 1; z++) s.part("build", 1, 1, z);
                    s.partOn("cache", -1, 3, 0);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Assemble.of("build", st -> st.is(ItemTags.PLANKS) || st.is(ItemTags.WOODEN_SLABS), Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState()).base("post"))
                .after(c -> {
                    c.set("mover", 0, Blocks.AIR.defaultBlockState());
                    c.set("dest", 0, c.turned(Props.STILLED.get().front(1, Direction.WEST)));
                })
                .play(t -> { for (int i = 0; i < 3; i++) t.hold(Blocks.OAK_PLANKS).use("post", 0); })
                .add();

        Kind.of(FSet.STILLED, 8, "long_table", "The Long Table").verb(Verb.CARRY).find().rank(4).lands(Group.DARK, Group.SNOW, Group.FOREST).anchor(Anchor.FLAT).made(Made.STILLED)
                .see("A long table out in the open with twelve stone figures sat at it. Every bowl but one holds stone food.")
                .learned("You filled the last bowl with real food and left them to it. By morning every bowl was empty, and a seat had something on it for you.")
                .reach(8).icon(Props.STONE_BOWL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    BlockState top = m.woodSlab().hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE)
                            ? m.woodSlab().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP) : m.woodSlab();
                    for (int x = -3; x <= 3; x++) {
                        s.on(x, 0, Math.abs(x) == 3 || x == 0 ? m.fence() : top);
                        if (Math.abs(x) == 3 || x == 0) s.up(x, 0, 1, m.woodSlab());
                        else if (x == 1) s.partOn("bowl", x, 0, 1, Props.STONE_BOWL.get().of(0));
                        else s.partOn("full", x, 0, 1, Props.STONE_BOWL.get().of(1));
                        if (x != 0) {
                            s.on(x, -1, Props.STILLED.get().front(2, Direction.SOUTH));
                            s.on(x, 1, Props.STILLED.get().front(2, Direction.NORTH));
                        }
                    }
                    s.partOn("cache", 0, 2, 0);
                    Dress.skirt(s, 7, m);
                })
                .script(() -> Steps.of(Sockets.need("bowl", st -> st.has(net.minecraft.core.component.DataComponents.FOOD), i -> Props.STONE_BOWL.get().of(1)), Later.morning()))
                .after(c -> {
                    c.set("bowl", 0, Props.STONE_BOWL.get().of(0));
                    for (int i = 0; i < c.count("full"); i++) c.set("full", i, Props.STONE_BOWL.get().of(0));
                })
                .play(t -> t.hold(net.minecraft.world.item.Items.BREAD).use("bowl", 0).hour(1000).ticks(1).pass(700).ticks(1))
                .add();
    }
}
