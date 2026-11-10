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

/** High Things (reach): the whole of it is getting your hand on something. Where it stands is the puzzle. */
final class High {
    private High() {}

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    /** A rough stack of living rock: {@code h[x][z]} high over a footprint starting at x0, z0. */
    private static void stack(Scene s, int x0, int z0, int[][] h) {
        for (int x = 0; x < h.length; x++)
            for (int z = 0; z < h[x].length; z++) if (h[x][z] > 0) s.column(x0 + x, z0 + z, 0, h[x][z] - 1, STONE);
    }

    static void init() {
        Kind.of(FSet.HIGH, 1, "glint_in_the_crown", "The Glint in the Crown").verb(Verb.REACH).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("Something flashing in the top of the tallest tree around.")
                .learned("A magpie's hoard. The broken branches made a stair, if you didn't look down.")
                .reach(6).tell(Tell.GLINT).icon(Props.NEST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState log = m.log(), leaf = leaf(Blocks.OAK_LEAVES);
                    s.column(0, 0, 0, 12, log);
                    for (int k = 0; k <= 11; k++) {
                        int d = k & 3;
                        s.up(DX[d], DZ[d], k, axis(log, DX[d] != 0 ? Direction.Axis.X : Direction.Axis.Z));
                    }
                    Dress.crown(s, 0, 0, 12, 3, leaf);
                    for (int k = 12; k <= 15; k++) s.air(0, s.g(0, 0) + 1 + k, -1);
                    for (int x = -1; x <= 1; x += 2) s.put(x, s.g(0, 0) + 13, -2, leaf);
                    s.air(0, s.g(0, 0) + 13, -3);
                    s.partOn("prize", 0, 0, 13, Props.NEST.get().of(2));
                    s.partOn("cache", 0, 0, 14);
                    s.tell(0, 0, 14);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 2, "ledge_nest", "The Ledge Nest").verb(Verb.REACH).curio().rank(1).lands(Group.MOUNTAIN, Group.SNOW, Group.BADLANDS).anchor(Anchor.CLIFF).made(Made.BEAST)
                .see("A big untidy nest on a ledge of a rock stack, and its owner circling high over it.")
                .learned("There's a way up round the back. One of the eggs was a stone it had been sitting on by mistake.")
                .reach(8).icon(Props.NEST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    stack(s, -1, 2, new int[][] {{6, 6, 6}, {6, 6, 6}, {6, 8, 7}});
                    stack(s, -3, 2, new int[][] {{0, 1, 0, 0}, {0, 2, 3, 0}});
                    s.column(-1, 5, 0, 3, STONE);
                    s.column(0, 5, 0, 4, STONE);
                    s.put(0, 6, 1, STONE);
                    s.put(0, 5, 1, top(m.slab()));
                    s.put(0, 7, 1, Props.NEST.get().of(1));
                    s.part("prize", 0, 7, 1);
                    s.part("cache", 0, 7, 1);
                    s.put(-1, 7, 2, Dress.twigs(s));
                    s.put(1, 7, 2, Blocks.SNOW.defaultBlockState());
                    s.spawnUp(Life.type("crag_feather", EntityTypes.PARROT), 0, 0, 12, 0, "bird");
                    s.on(-2, 0, Props.LOOSE_STONE.get().of(0));
                    s.on(2, 1, Props.LOOSE_STONE.get().of(3));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 3, "island_stump", "The Island Stump").verb(Verb.REACH).curio().rank(1).lands(Group.SWAMP, Group.FOREST, Group.JUNGLE, Group.PLAINS).anchor(Anchor.SHORE).made(Made.NATURAL)
                .see("A dead tree standing out in the water with a wrapped box tied at the top of it.")
                .learned("Across, then up the broken branches. Whoever tied it there didn't want it found by accident.")
                .reach(10).icon(Props.BUNDLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    int w = Math.max(s.sea(), 0), top = w + 7;
                    for (int dy = Math.min(s.g(0, 8) + 1, 1); dy <= top; dy++) s.put(0, dy, 8, m.log());
                    for (int k = 0; k <= 5; k++) {
                        int d = k & 3;
                        s.put(DX[d], w + 1 + k, 8 + DZ[d], axis(m.log(), DX[d] != 0 ? Direction.Axis.X : Direction.Axis.Z));
                    }
                    s.put(0, top + 1, 8, Props.BUNDLE.get().of(0));
                    s.part("prize", 0, top + 1, 8);
                    s.part("cache", 0, top + 1, 8);
                    s.put(1, top, 8, Props.ROPE_LENGTH.get().of(0));
                    s.on(1, 0, Props.OLD_ROD.get().of(0));
                    s.on(-1, 1, Dress.twigs(s));
                    Dress.skirt(s, 2, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 4, "arch_top", "The Arch Top").verb(Verb.REACH).curio().rank(2).lands(Group.BADLANDS, Group.DESERT).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A rock arch with a cairn on top that somebody must have built.")
                .learned("There is a way up: a run of steps worn into the back of one leg. The cairn kept something dry.")
                .reach(9).slope(1).icon(Props.CAIRN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    BlockState rock = Blocks.TERRACOTTA.defaultBlockState();
                    for (int z = 0; z <= 1; z++) {
                        for (int x = -4; x <= 4; x++) {
                            boolean leg = Math.abs(x) >= 3;
                            for (int dy = leg ? 1 : 6; dy <= 6; dy++) s.put(x, dy, z, rock);
                        }
                        s.put(-2, 5, z, rock);
                        s.put(2, 5, z, rock);
                    }
                    int[][] step = {{-7, 0, 1}, {-6, 0, 2}, {-6, 1, 3}, {-5, 1, 4}, {-5, 0, 5}};
                    for (int[] a : step) for (int dy = 1; dy <= a[2]; dy++) s.put(a[0], dy, a[1], rock);
                    s.put(0, 7, 0, Props.CAIRN.get().of(0));
                    s.part("prize", 0, 7, 0);
                    s.put(1, 7, 0, Props.FOOTING.get().of(0));
                    s.part("cache", 1, 7, 0);
                    s.put(-3, 7, 1, Props.LOOSE_STONE.get().of(1));
                    s.put(3, 7, 0, Blocks.DEAD_BUSH.defaultBlockState());
                    s.on(0, -1, Props.LOOSE_STONE.get().of(2));
                    s.on(1, 2, Blocks.DEAD_BUSH.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 5, "needle", "The Needle").verb(Verb.REACH).find().rank(2).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.CLIFF).made(Made.NATURAL)
                .see("A spike of rock standing clear of a lower stack, with a flag on the very top.")
                .learned("Up the stack, across the gap on a rope, then hand over hand. The flag is yours, and so is the view.")
                .reach(9).needsMove("tether").icon(Props.FLAG)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    stack(s, -1, -4, new int[][] {{3, 5, 7}, {2, 6, 7}, {1, 4, 7}});
                    s.put(0, 8, -2, Props.RING_POST.get().of(0));
                    s.column(0, 4, 0, 12, STONE);
                    for (int d = 0; d < 4; d++) {
                        s.column(DX[d], 4 + DZ[d], 0, 3 - (d & 1), STONE);
                        s.put(DX[d], 8, 4 + DZ[d], m.slab());
                    }
                    for (int k = 0; k < 4; k++) {
                        int d = (k + 1) & 3;
                        s.put(DX[d], 9 + k, 4 + DZ[d], top(m.slab()));
                    }
                    s.put(0, 14, 4, Props.FLAG.get().of(0));
                    s.part("prize", 0, 14, 4);
                    s.part("cache", 0, 14, 4);
                    s.on(2, 0, Props.LOOSE_STONE.get().of(1));
                    s.on(-2, 1, Blocks.SNOW.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 6, "chimney", "The Chimney").verb(Verb.REACH).curio().rank(3).lands(Group.MOUNTAIN, Group.BADLANDS, Group.FOREST, Group.PLAINS).anchor(Anchor.HILLTOP).made(Made.NATURAL)
                .see("A hole in the top of the hill, straight down and dark, with the top rungs of a ladder that has lost its middle.")
                .learned("Down the rungs, drop the gap, and there was a dry shelf at the bottom. Getting out took a jump.")
                .reach(6).slope(3).icon(() -> Items.LADDER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    BlockState rung = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST);
                    for (int dy = -8; dy <= 0; dy++) {
                        s.air(3, dy, 0);
                        s.put(2, dy, 0, m.stone());
                        if (dy >= -2 || dy <= -5) s.put(3, dy, 0, rung);
                    }
                    s.put(3, -9, 0, m.stone());
                    s.air(4, -8, 0);
                    s.air(4, -7, 0);
                    s.put(4, -9, 0, m.stone());
                    s.put(4, -8, 0, Blocks.DECORATED_POT.defaultBlockState());
                    s.part("prize", 4, -8, 0);
                    s.part("cache", 3, -8, 0);
                    s.on(2, 0, m.slab());
                    s.on(3, 1, Props.LOOSE_STONE.get().of(0));
                    s.on(4, 0, Props.LOOSE_STONE.get().of(2));
                    s.on(3, -1, m.slab());
                    s.on(4, -1, Props.ROPE_PEG.get().of(0));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 7, "hanging_garden", "The Hanging Garden").verb(Verb.REACH).find().rank(3).lands(Group.JUNGLE, Group.DARK, Group.SWAMP).anchor(Anchor.FLAT).made(Made.ALIVE)
                .see("A shelf of flowers round a great trunk, far above the forest floor, with one long vine hanging through it.")
                .learned("Up the vine and through the shelf. A fruit grows up there that grows nowhere else.")
                .reach(6).loot("seeds").tell(Tell.FIREFLIES).icon(Props.GOURD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState log = m.log(), moss = Blocks.MOSS_BLOCK.defaultBlockState();
                    s.column(0, 0, 0, 13, log);
                    net.minecraft.world.level.block.Block[] fl = {Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.FERN, Blocks.AZURE_BLUET, Blocks.PINK_TULIP};
                    for (int x = -2; x <= 2; x++)
                        for (int z = -2; z <= 2; z++) {
                            if ((x == 0 && z == 0) || (x == 2 && z == 0) || Math.abs(x) + Math.abs(z) == 4) continue;
                            s.put(x, 9, z, moss);
                            if (s.rnd.nextInt(3) != 0 && !(x == 1 && z == 1)) s.put(x, 10, z, fl[s.rnd.nextInt(fl.length)].defaultBlockState());
                        }
                    s.put(1, 12, 0, axis(log, Direction.Axis.X));
                    s.put(2, 12, 0, axis(log, Direction.Axis.X));
                    for (int dy = 4; dy <= 11; dy++) s.put(2, dy, 0, Blocks.CAVE_VINES_PLANT.defaultBlockState());
                    s.put(2, 3, 0, Blocks.CAVE_VINES.defaultBlockState());
                    s.put(1, 10, 1, Props.GOURD.get().of(3));
                    s.part("prize", 1, 10, 1);
                    s.part("cache", 1, 10, 1);
                    Dress.crown(s, 0, 0, 14, 3, leaf(Blocks.JUNGLE_LEAVES));
                    s.tell(0, 0, 10);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.HIGH, 8, "cloud_shelf", "The Cloud Shelf").verb(Verb.REACH).find().rank(4).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.PEAK).made(Made.NATURAL)
                .see("A spire with a flat top, higher than anything round it, and no way up that feet could use.")
                .learned("Nothing climbs it. You came down on it from above, and nobody had touched what was there in a very long time.")
                .reach(6).slope(3).needsMove("wings").icon(Props.CAP_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.column(3, 0, 0, 15, STONE);
                    for (int d = 0; d < 4; d++) {
                        s.column(3 + DX[d], DZ[d], 0, 5 - 2 * (d & 1), STONE);
                        s.put(3 + DX[d], s.g(3, 0) + 16, DZ[d], m.slab());
                    }
                    for (int x = 2; x <= 4; x += 2)
                        for (int z = -1; z <= 1; z += 2) s.put(x, s.g(3, 0) + 16, z, m.slab());
                    s.put(3, s.g(3, 0) + 17, 0, Props.CAP_STONE.get().of(0));
                    s.part("prize", 3, s.g(3, 0) + 17, 0);
                    s.part("cache", 3, s.g(3, 0) + 17, 0);
                    s.on(1, 1, Props.LOOSE_STONE.get().of(2));
                    s.on(5, -1, Blocks.SNOW.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();
    }
}
