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

/** Surveyor's Stones (read): things somebody wrote down in stone, and what they were looking for. */
final class Survey {
    private Survey() {}

    private static final BlockState ROCK = Blocks.STONE.defaultBlockState();

    /** The Boundary Stone: four faces to read, and one of them is under a heap of earth. */
    private static final class Faces implements Script {
        private final Read read = Read.all("stone");

        @Override
        public boolean mayBreak(Ctx c, ServerPlayer p, String part, int i) {
            return part.equals("spot");
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            return read.use(c, p, part, i, held);
        }
    }

    /** The order the picture posts stand in: which picture is at each of the four places. Never the right order as made. */
    private static int[] shuffle(long seed) {
        int[][] all = {{1, 0, 3, 2}, {2, 0, 3, 1}, {3, 1, 0, 2}, {1, 3, 0, 2}, {2, 3, 1, 0}, {3, 0, 2, 1}};
        return all[(int) Math.floorMod(seed ^ 0x51CEL, all.length)];
    }

    static void init() {
        Kind.of(FSet.SURVEY, 1, "surveyors_stone", "The Surveyor's Stone").verb(Verb.READ).curio().rank(0).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A squared stone on a block of rock, with a number, an arrow and a brass stud.")
                .learned("One of twelve. The arrow is cut toward the next, and the next is further out.")
                .reach(5).lead().icon(Props.MILESTONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 0, m.stone());
                    s.partOn("stone", 0, 0, 1, Props.MILESTONE.get().of(3));
                    s.on(1, 0, m.slab());
                    Dress.moss(s, 1, 0, 1, 2);
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Read.all("stone"))
                .play(t -> t.use("stone", 0))
                .add();

        Kind.of(FSet.SURVEY, 2, "boundary_stone", "The Boundary Stone").verb(Verb.READ).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.SWAMP).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A leaning stone with a carved face set against each side of it. Earth has slumped over one of the four.")
                .learned("Four faces, four marks, one of them dug out. It was the corner of something that isn't here now.")
                .reach(5).icon(Props.STANDING_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 0, Props.STANDING_STONE.get().of(2));
                    Direction[] out = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    int buried = s.rnd.nextInt(4);
                    for (int d = 0; d < 4; d++) {
                        if (d == buried) {
                            s.put(DX[d], s.g(DX[d], DZ[d]), DZ[d], Props.PICTURE_STONE.get().front(d, out[d]));
                            s.part("stone", DX[d], s.g(DX[d], DZ[d]), DZ[d]);
                            s.partOn("spot", DX[d], DZ[d], 0, Blocks.COARSE_DIRT.defaultBlockState());
                        } else s.partOn("stone", DX[d], DZ[d], 0, Props.PICTURE_STONE.get().front(d, out[d]));
                    }
                    s.partOn("cache", 1, 1, 0);
                    s.on(-1, -1, Blocks.SHORT_GRASS.defaultBlockState());
                    s.on(2, -1, Props.LOOSE_STONE.get().of(1));
                    Dress.skirt(s, 4, m);
                })
                .script(Faces::new)
                .play(t -> { t.dig("spot", 0); for (int i = 0; i < 4; i++) t.use("stone", i); })
                .add();

        Kind.of(FSet.SURVEY, 3, "name_wall", "The Name Wall").verb(Verb.READ).curio().rank(1).lands(Group.MOUNTAIN, Group.BADLANDS, Group.DESERT).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("A rock face covered in chalked names and tally marks, with one of them scratched out and a clean patch beside it.")
                .learned("You read the one somebody tried to rub out, and put your own mark beside it. That name turns up again, further on.")
                .reach(6).lead().icon(Props.CHALK_MARK)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -3; x <= 3; x++)
                        for (int dy = 1; dy <= 4 - (Math.abs(x) == 3 ? 1 : 0); dy++) s.put(x, dy, 2, ROCK);
                    for (int x = -2; x <= 2; x++) s.put(x, 5 - (Math.abs(x) == 2 ? 1 : 0), 2, m.slab());
                    int[][] at = {{-2, 2}, {-1, 3}, {1, 3}, {2, 2}, {-2, 3}};
                    for (int[] a : at) s.put(a[0], a[1], 1, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                    s.put(0, 2, 1, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                    s.part("stone", 0, 2, 1);
                    s.part("blank", 1, 2, 1);
                    s.put(-1, 1, 1, m.slab());
                    s.put(2, 1, 1, Props.LOOSE_STONE.get().of(2));
                    s.partOn("cache", 0, 0, 0);
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Steps.of(Read.all("stone"), Prize.at("blank").then((c, i) -> c.set("blank", 0, c.turned(Props.CHALK_MARK.get().front(1, Direction.SOUTH))))))
                .play(t -> t.use("stone", 0).use("blank", 0))
                .add();

        Kind.of(FSet.SURVEY, 4, "picture_posts", "The Picture Posts").verb(Verb.READ).curio().rank(1).lands(Group.FOREST, Group.JUNGLE, Group.DARK).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("Four posts in a row, each with a carved picture on top, telling something in the wrong order.")
                .learned("Read in the order it happened, the last picture shows where it ended. It's close.")
                .reach(6).lead().icon(Props.PICTURE_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    int[] pic = shuffle(s.site.seed());
                    for (int want = 0; want < 4; want++)
                        for (int j = 0; j < 4; j++) {
                            if (pic[j] != want) continue;
                            int x = -3 + j * 2;
                            s.on(x, 0, m.fence());
                            s.partOn("stone", x, 0, 1, Props.PICTURE_STONE.get().front(want, Direction.NORTH));
                            s.on(x, 1, (j & 1) == 0 ? Dress.twigs(s) : Blocks.FERN.defaultBlockState());
                        }
                    s.partOn("cache", 0, -2, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Read.all("stone").inOrder())
                .play(t -> { for (int i = 0; i < 4; i++) t.use("stone", i); })
                .add();

        Kind.of(FSet.SURVEY, 5, "wet_wall", "The Wet Wall").verb(Verb.READ).curio().rank(2).lands(Group.MOUNTAIN, Group.DARK, Group.SWAMP, Group.JUNGLE).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("An overhang with a wet rock wall under it, skinned over white. There's colour under the skin.")
                .learned("Cleaned gently, with a brush or water, it's painted hands and beasts. One of the beasts you haven't met.")
                .reach(6).tell(Tell.DRIP).icon(() -> Items.BRUSH)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -3; x <= 3; x++) {
                        for (int dy = 1; dy <= 4; dy++) s.put(x, dy, 3, ROCK);
                        if (Math.abs(x) <= 2) s.put(x, 4, 2, ROCK);
                        if (Math.abs(x) <= 2) s.put(x, 4, 1, (x & 1) == 0 ? top(m.slab()) : Blocks.POINTED_DRIPSTONE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
                    }
                    for (int x = -3; x <= 3; x += 6) for (int dy = 1; dy <= 3; dy++) s.put(x, dy, 2, dy == 3 ? m.slab() : ROCK);
                    for (int x = -2; x <= 2; x++) {
                        s.put(x, 2, 2, Blocks.CALCITE.defaultBlockState());
                        s.part("skin", x, 2, 2);
                        s.put(x, 1, 2, x == 0 ? m.slab() : ROCK);
                        s.put(x, 3, 2, x % 2 == 0 ? ROCK : top(m.slab()));
                    }
                    s.partOn("cache", 0, 0, 0);
                    s.tell(0, 1, 3);
                    s.on(1, 1, Blocks.MOSS_CARPET.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Sockets.need("skin", st -> st.is(Items.BRUSH) || Hands.water(st), i -> Props.PICTURE_STONE.get().front(i, Direction.NORTH)).keep())
                .play(t -> { t.hold(Items.BRUSH); for (int i = 0; i < 5; i++) t.use("skin", i); })
                .add();

        Kind.of(FSet.SURVEY, 6, "map_floor", "The Map Floor").verb(Verb.READ).find().rank(2).lands(Group.PLAINS, Group.DESERT, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("A floor of small tiles with a third of them kicked out and lying about. What's left looks like hills and a river.")
                .learned("With the tiles back it's the land round here, from above. There's a stud in it where nothing stands that you know of.")
                .reach(7).slope(1).persists().lead().icon(Props.MAP_TILE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    int gone = 0;
                    for (int x = -2; x <= 2; x++)
                        for (int z = -2; z <= 2; z++) {
                            s.floor(x, z, m.stone());
                            boolean out = gone < 6 && (x * 3 + z * 5 + (int) (s.site.seed() & 7)) % 4 == 0 && !(x == 0 && z == 0);
                            if (out) {
                                s.partOn("socket", x, z, 0);
                                gone++;
                            } else s.on(x, z, Props.MAP_TILE.get().of(x == 1 && z == -1 ? 3 : (x + z) & 1));
                        }
                    for (int i = 0, tries = 0; i < gone && tries < 200; tries++) {
                        int x = s.rnd.nextInt(11) - 5, z = s.rnd.nextInt(11) - 5;
                        if ((Math.abs(x) <= 3 && Math.abs(z) <= 3) || s.taken(x, s.g(x, z) + 1, z)) continue;
                        s.partOn("loose", x, z, 0, Props.MAP_TILE.get().of(2));
                        i++;
                    }
                    for (int x = -3; x <= 3; x += 6)
                        for (int z = -3; z <= 3; z += 6) s.on(x, z, m.wall());
                    s.on(3, 0, m.slab());
                    s.on(-3, 1, m.slab());
                    s.on(1, 3, m.slab());
                    s.partOn("cache", 0, -3, 0, Props.FOOTING.get().of(0));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Sockets.need("socket", st -> st.is(Props.MAP_TILE.get().asItem()), i -> Props.MAP_TILE.get().of(1)).carry("loose"))
                .play(t -> { for (int i = 0; i < t.count("loose"); i++) t.use("loose", i); for (int i = 0; i < t.count("socket"); i++) t.hold(Props.MAP_TILE.get()).use("socket", i); })
                .add();

        Kind.of(FSet.SURVEY, 7, "tally_tree", "The Tally Tree").verb(Verb.READ).curio().rank(3).lands(Group.DARK, Group.FOREST, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A huge old tree with rows of notches cut up its trunk, and one row unfinished.")
                .learned("You counted the last row and cut the next notch. Whoever is counting had left something for whoever did.")
                .reach(6).icon(() -> Items.IRON_AXE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
                    for (int x = 0; x <= 1; x++)
                        for (int z = 0; z <= 1; z++) s.column(x, z, 0, 8, log);
                    Dress.crown(s, 0, 0, 9, 4, leaf(Blocks.DARK_OAK_LEAVES));
                    Dress.crown(s, 1, 1, 8, 3, leaf(Blocks.DARK_OAK_LEAVES));
                    int g = s.g(0, 0);
                    for (int k = 1; k <= 3; k++) s.put(0, g + k, -1, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                    s.put(1, g + 1, -1, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                    s.put(1, g + 2, -1, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                    s.part("stone", 1, g + 2, -1);
                    s.part("notch", 1, g + 3, -1);
                    s.on(-1, 0, Props.ROOT_LOOP.get().of(1));
                    s.on(2, 1, Props.ROOT_LOOP.get().of(3));
                    s.partOn("cache", 2, 0, 0, Props.FOOTING.get().of(0));
                    s.on(-1, 2, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Read.all("stone"), Prize.at("notch").holding(Hands::blade).then((c, i) -> {
                    c.set("notch", 0, c.turned(Props.CHALK_MARK.get().front(1, Direction.SOUTH)));
                    c.sound(c.pos("notch", 0), SoundEvents.AXE_STRIP.value(), 1.0F, 0.9F);
                })))
                .play(t -> t.use("stone", 0).hold(Items.IRON_AXE).use("notch", 0))
                .add();

        Kind.of(FSet.SURVEY, 8, "sky_slab", "The Sky Slab").verb(Verb.READ).find().rank(4).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.PEAK).made(Made.BUILT)
                .see("A flat slab on a peak, cut with a ring of studs like stars and one empty socket in the middle.")
                .learned("The star iron went in the socket and the ring made sense: it's the sky over one place, and that's where the surveyor stopped.")
                .reach(5).slope(3).lead().icon(Props.STAR_IRON)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    for (int x = -1; x <= 1; x++)
                        for (int z = 1; z <= 3; z++) {
                            s.on(x, z, x == 0 && z == 2 ? m.stone() : m.slab());
                        }
                    for (int d = 0; d < 4; d++) s.on(DX[d] * 2, 2 + DZ[d] * 2, Props.MAP_TILE.get().of(3));
                    s.partOn("socket", 0, 2, 1);
                    s.partOn("cache", 2, 0, 0, Props.CAP_STONE.get().of(0));
                    s.on(-2, 0, Props.SUMMIT_STONE.get().of(0));
                    s.on(2, 4, Blocks.SNOW.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Sockets.need("socket", st -> st.is(Props.STAR_IRON.get().asItem()), i -> Props.STAR_IRON.get().of(0)))
                .play(t -> t.hold(Props.STAR_IRON.get()).use("socket", 0))
                .add();
    }
}
