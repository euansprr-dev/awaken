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

/** Marks (shoot): things only an arrow reaches. */
final class Marks {
    private Marks() {}

    /** The Gold in the Nest: knock the gold off. Hit the nest and the bees see to it that nothing comes loose for a while. */
    private static final class Gold implements Script {
        @Override
        public void shot(Ctx c, ServerPlayer p, String part, int i) {
            if (part.equals("nest")) {
                c.clock(c.now());
                c.sound(c.pos("nest", 0), SoundEvents.BEE_LOOP_AGGRESSIVE, 1.0F, 1.0F);
                c.puff(c.pos("nest", 0), ParticleTypes.WAX_OFF, 14, 0.5, 0.05);
                return;
            }
            if (!part.equals("gold") || (c.clock() != 0 && c.now() - c.clock() < 100)) return;
            c.set("gold", 0, AIR);
            c.sound(c.pos("gold", 0), SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.2F);
            c.complete(p);
        }
    }

    /** The Knocker: three knocks, however they get there. */
    private static final class Knock implements Script {
        private void knock(Ctx c, ServerPlayer p) {
            int n = c.v(0) + 1;
            c.v(0, n);
            c.sound(c.pos("knocker", 0), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1.2F, 0.6F + 0.12F * n);
            if (n >= 3) c.complete(p);
        }

        @Override
        public void shot(Ctx c, ServerPlayer p, String part, int i) {
            if (part.equals("knocker")) knock(c, p);
        }

        @Override
        public boolean hit(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("knocker")) return false;
            knock(c, p);
            return true;
        }
    }

    /** The Sun Dish: turn the mirror until it faces the dish, with the sun high and nothing between. */
    private static final class Dish implements Script {
        private boolean right(Ctx c) {
            BlockState s = c.at("mirror", 0);
            return s.getBlock() instanceof PropBlock && ((s.getValue(PropBlock.FACING).getOpposite().get2DDataValue() - c.site.rot()) & 3) == 0
                    && c.hour() >= 4500 && c.hour() <= 7500 && !c.raining();
        }

        private void beam(Ctx c, ServerPlayer p) {
            BlockPos a = c.pos("mirror", 0), b = c.pos("dish", 0);
            for (int k = 0; k <= 16; k++)
                c.level.sendParticles(ParticleTypes.END_ROD, a.getX() + 0.5 + (b.getX() - a.getX()) * k / 16.0, a.getY() + 0.5 + (b.getY() - a.getY()) * k / 16.0,
                        a.getZ() + 0.5 + (b.getZ() - a.getZ()) * k / 16.0, 1, 0, 0, 0, 0);
            c.sound(b, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.7F);
            c.complete(p);
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("mirror")) return false;
            BlockState s = c.at("mirror", 0);
            if (!(s.getBlock() instanceof PropBlock)) return false;
            c.set("mirror", 0, s.setValue(PropBlock.FACING, s.getValue(PropBlock.FACING).getClockWise()));
            c.sound(c.pos("mirror", 0), SoundEvents.GRINDSTONE_USE, 0.5F, 1.3F);
            if (right(c)) beam(c, p);
            return true;
        }

        @Override
        public void tick(Ctx c) {
            // set right and left alone: it answers when the sun comes round
            if (c.now() % 20 != 0 || !right(c)) return;
            var near = c.players(12);
            if (!near.isEmpty()) beam(c, near.get(0));
        }
    }

    static void init() {
        Kind.of(FSet.MARKS, 1, "gourd_tree", "The Gourd Tree").verb(Verb.SHOOT).curio().rank(0).lands(Group.FOREST, Group.PLAINS, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A tree hung with painted gourds on strings, swinging.")
                .learned("Somebody practised here. All five down in ten shots, and the last one had something in it.")
                .reach(7).icon(Props.GOURD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    BlockState leaf = leaf(Blocks.OAK_LEAVES);
                    s.column(0, 0, 0, 5, m.log());
                    for (int[] a : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}}) s.up(a[0], a[1], 4, leaf);
                    for (int[] a : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) s.up(a[0], a[1], 4, leaf);
                    Dress.crown(s, 0, 0, 5, 3, leaf);
                    int[][] at = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}};
                    for (int i = 0; i < 5; i++) s.partOn("mark", at[i][0], at[i][1], 3, Props.GOURD.get().of(i & 3));
                    s.partOn("cache", 1, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> dev.awaken.finds.verb.Marks.hit("mark").shots(10))
                .play(t -> { for (int i = 0; i < t.count("mark"); i++) t.shoot("mark", i); })
                .add();

        Kind.of(FSet.MARKS, 2, "three_bells", "The Three Bells").verb(Verb.SHOOT).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.DESERT).anchor(Anchor.LINE).length(46).made(Made.BUILT)
                .see("A flat stone to stand on, and three small bells on poles going away from it, each further than the last.")
                .learned("The near two anyone can ring. The far one took a proper shot, and a fletcher had left something for whoever made it.")
                .reach(24).icon(() -> Items.BELL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -21, 21, 0, m);
                    s.on(0, -20, Props.START_STONE.get().of(0));
                    s.partOn("cache", 1, -20, 0, Props.FOOTING.get().of(0));
                    int[] z = {-8, 6, 20}, h = {1, 2, 3};
                    for (int i = 0; i < 3; i++) {
                        s.column(0, z[i], 0, h[i], m.fence());
                        s.partOn(i == 2 ? "far" : "bell", 0, z[i], h[i] + 1, Blocks.BELL.defaultBlockState());
                        s.on(1, z[i], Props.LOOSE_STONE.get().of(i));
                    }
                    s.on(-1, -19, Dress.twigs(s));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> dev.awaken.finds.verb.Marks.hit("far").becomes(i -> Blocks.BELL.defaultBlockState()))
                .play(t -> t.shoot("bell", 0).shoot("far", 0))
                .add();

        Kind.of(FSet.MARKS, 3, "gold_in_the_nest", "The Gold in the Nest").verb(Verb.SHOOT).curio().rank(1).lands(Group.JUNGLE, Group.FOREST).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A paper nest the size of a barrel hanging over a pool, with something gold stuck in its side.")
                .learned("A clean shot knocked the gold off and left the nest alone. The bees never noticed.")
                .reach(7).slope(1).tell(Tell.GLINT).icon(Props.GOLD_LUMP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int x = -1; x <= 1; x++)
                        for (int z = 2; z <= 4; z++) {
                            s.put(x, 0, z, Blocks.WATER.defaultBlockState());
                            s.put(x, -1, z, Blocks.MUD.defaultBlockState());
                        }
                    for (int x = -2; x <= 2; x++)
                        for (int z = 1; z <= 5; z++)
                            if ((Math.abs(x) == 2 || z == 1 || z == 5) && s.get(x, 0, z) == null) s.put(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
                    s.column(-3, 3, 0, 5, m.log());
                    for (int x = -2; x <= 0; x++) s.up(x, 3, 5, axis(m.log(), Direction.Axis.X));
                    Dress.crown(s, -3, 3, 6, 2, leaf(Blocks.OAK_LEAVES));
                    s.put(0, s.g(-3, 3) + 5, 3, Blocks.BEE_NEST.defaultBlockState());
                    s.part("nest", 0, s.g(-3, 3) + 5, 3);
                    s.put(0, s.g(-3, 3) + 5, 2, Props.GOLD_LUMP.get().front(0, Direction.SOUTH));
                    s.part("gold", 0, s.g(-3, 3) + 5, 2);
                    s.partOn("cache", 0, 0, 0);
                    s.tell(0, 2, 5);
                    s.on(2, 0, Blocks.FERN.defaultBlockState());
                    s.on(-2, 0, Dress.litter(s));
                    Dress.skirt(s, 5, m);
                })
                .script(Gold::new)
                .play(t -> t.shoot("gold", 0))
                .add();

        Kind.of(FSet.MARKS, 4, "hanging_crate", "The Hanging Crate").verb(Verb.SHOOT).curio().rank(1).lands(Group.MOUNTAIN, Group.BADLANDS, Group.SNOW).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A crate hung from a beam between two tall posts, far too high to reach, on one tight rope.")
                .learned("One shot through the rope and it came down on your side.")
                .reach(6).icon(() -> Items.BARREL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -3; x <= 3; x += 6) {
                        s.column(x, 0, 0, 7, m.log());
                        s.on(x, 1, m.fence());
                        s.on(x, -1, m.fence());
                        s.on(x < 0 ? x - 1 : x + 1, 0, Props.LOOSE_STONE.get().of(x & 3));
                    }
                    int top = s.g(0, 0) + 8;
                    for (int x = -2; x <= 2; x++) s.put(x, top, 0, axis(m.log(), Direction.Axis.X));
                    s.put(0, top - 1, 0, Props.TAUT_ROPE.get().of(0));
                    s.put(0, top - 2, 0, Props.TAUT_ROPE.get().of(0));
                    s.part("rope", 0, top - 2, 0);
                    s.part("rope", 0, top - 1, 0);
                    s.put(0, top - 3, 0, Blocks.BARREL.defaultBlockState());
                    s.part("crate", 0, top - 3, 0);
                    s.partOn("cache", 0, 0, 0);
                    s.on(1, 1, Props.ROPE_COIL.get().of(0));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> dev.awaken.finds.verb.Marks.hit("rope"))
                .after(c -> {
                    fill(c, "rope", AIR);
                    c.set("crate", 0, AIR);
                    c.set("cache", 0, Blocks.BARREL.defaultBlockState());
                    c.sound(c.pos("cache", 0), SoundEvents.WOOD_BREAK, 1.5F, 0.6F);
                })
                .play(t -> t.shoot("rope", 0).shoot("rope", 1))
                .add();

        Kind.of(FSet.MARKS, 5, "flag_line", "The Flag Line").verb(Verb.SHOOT).curio().rank(2).lands(Group.MOUNTAIN, Group.PLAINS, Group.SNOW).anchor(Anchor.LINE).length(44).made(Made.BUILT)
                .see("Five flags going away down the valley, each with an iron ring on a pole beside it.")
                .learned("Five rings and five arrows, and not one to spare. A marksman left a token for the next.")
                .reach(22).icon(Props.RING_TARGET)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.on(0, -19, Props.START_STONE.get().of(0));
                    s.partOn("cache", -1, -19, 0, Props.FOOTING.get().of(0));
                    for (int i = 0; i < 5; i++) {
                        int z = -9 + i * 7, x = (i % 2 == 0 ? -2 : 2) + s.rnd.nextInt(3) - 1, h = 1 + (i * 2) % 3;
                        s.column(x, z, 0, h - 1, m.fence());
                        s.partOn("mark", x, z, h, Props.RING_TARGET.get().of(0));
                        s.on(x + 1, z, Props.FLAG.get().of(i & 3));
                        s.on(x - 1, z, Props.LOOSE_STONE.get().of(i & 3));
                    }
                    Dress.skirt(s, 3, m);
                })
                .script(() -> dev.awaken.finds.verb.Marks.hit("mark").shots(5).becomes(i -> Props.RING_TARGET.get().of(1)))
                .play(t -> { for (int i = 0; i < 5; i++) t.shoot("mark", i); })
                .add();

        Kind.of(FSet.MARKS, 6, "knocker", "The Knocker").verb(Verb.SHOOT).find().rank(2).lands(Group.MOUNTAIN, Group.BADLANDS).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("A narrow stone turret against the cliff, with a barred door three men up and an iron knocker on it. No stair.")
                .learned("Three knocks, by arrow. The bars went up and a ladder came down. Somebody had lived up there.")
                .reach(7).icon(Props.KNOCKER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -1; x <= 1; x++)
                        for (int z = 2; z <= 4; z++)
                            for (int dy = 1; dy <= 9; dy++) {
                                boolean corner = x != 0 && z != 3, mid = x == 0 && z == 3;
                                if (mid) {
                                    if (dy == 5) s.put(x, dy, z, m.woodSlab());
                                    else if (dy == 9) s.put(x, dy, z, m.slab());
                                    else s.air(x, dy, z);
                                } else if (corner) s.put(x, dy, z, dy == 9 ? m.slab() : m.wall());
                                else s.put(x, dy, z, dy == 9 ? m.wall() : (dy + x + z) % 3 == 0 ? m.mossy() : m.stone());
                            }
                    s.put(0, 6, 2, Blocks.IRON_BARS.defaultBlockState());
                    s.put(0, 7, 2, Blocks.IRON_BARS.defaultBlockState());
                    s.part("door", 0, 6, 2);
                    s.part("door", 0, 7, 2);
                    s.put(0, 7, 1, Props.KNOCKER.get().front(0, Direction.NORTH));
                    s.part("knocker", 0, 7, 1);
                    for (int dy = 1; dy <= 5; dy++) s.part("ladder", 0, dy, 1);
                    s.put(0, 6, 3, Blocks.DECORATED_POT.defaultBlockState());
                    s.part("cache", 0, 7, 3);
                    s.put(0, 7, 4, Blocks.IRON_BARS.defaultBlockState());
                    s.on(1, 0, Props.LOOSE_STONE.get().of(1));
                    s.on(-2, 1, Props.LOOSE_STONE.get().of(0));
                    Dress.skirt(s, 3, m);
                })
                .script(Knock::new)
                .after(c -> {
                    fill(c, "door", AIR);
                    c.set("knocker", 0, AIR);
                    fill(c, "ladder", c.turned(Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH)));
                    c.sound(c.pos("door", 0), SoundEvents.IRON_DOOR_OPEN, 1.5F, 0.7F);
                })
                .play(t -> t.shoot("knocker", 0).shoot("knocker", 0).shoot("knocker", 0))
                .add();

        Kind.of(FSet.MARKS, 7, "stone_thrower", "The Stone Thrower").verb(Verb.SHOOT).find().rank(3).lands(Group.BADLANDS, Group.DESERT).anchor(Anchor.LINE).length(40).made(Made.FALLEN)
                .see("An old siege engine, wound and loaded, pointed roughly at a far stack of rock. Two iron rings turn on its frame.")
                .learned("Rings set to their marks, then the lever. One stone, and the stack came down. It was hollow.")
                .reach(20).icon(Props.BOULDER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    int[] t = Turn.targets(s.site.seed(), 2);
                    for (int x = -1; x <= 1; x += 2) {
                        for (int z = -9; z <= -7; z++) s.on(x, z, axis(m.log(), Direction.Axis.Z));
                        s.column(x, -8, 1, 2, m.fence());
                    }
                    for (int z = -9; z <= -7; z++) s.up(0, z, 2, axis(m.log(), Direction.Axis.Z));
                    s.up(0, -9, 3, Props.BOULDER.get().of(0));
                    s.part("shot", 0, s.g(0, -9) + 4, -9);
                    s.partOn("lever", 0, -7, 3, Props.WINCH.get().of(0));
                    for (int i = 0; i < 2; i++) {
                        int x = i == 0 ? -3 : 3;
                        s.partOn("dial", x, -8, 0, Props.RING_POST.get().of(0));
                        s.on(x + DX[t[i]], -8 + DZ[t[i]], Props.START_STONE.get().of(1));
                    }
                    s.on(2, -5, Props.BOULDER.get().of(1));
                    s.on(-2, -6, Props.LOOSE_STONE.get().of(2));
                    // the far stack
                    for (int x = -1; x <= 1; x++)
                        for (int z = 15; z <= 17; z++) {
                            if (x == 0 && z == 16) continue;
                            boolean corner = x != 0 && z != 16;
                            s.partOn("stack", x, z, 0, corner ? m.wall() : m.stone());
                            s.partOn("stack", x, z, 1, corner ? m.slab() : m.mossy());
                        }
                    s.partOn("stack", 0, 16, 1, m.slab());
                    s.partOn("stack", 0, 16, 2, m.wall());
                    s.on(0, 16, Blocks.DECORATED_POT.defaultBlockState());
                    s.partOn("cache", 0, 16, 1);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Steps.of(Turn.dials("dial"), Prize.at("lever")))
                .after(c -> {
                    c.set("shot", 0, AIR);
                    fill(c, "stack", AIR);
                    c.sound(c.pos("cache", 0), SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.6F);
                    c.puff(c.pos("cache", 0), ParticleTypes.CLOUD, 30, 1.2, 0.05);
                })
                .play(t -> { int[] w = Turn.targets(t.seed(), 2); for (int i = 0; i < 2; i++) for (int k = 0; k < w[i]; k++) t.use("dial", i); t.use("lever", 0); })
                .add();

        Kind.of(FSet.MARKS, 8, "sun_dish", "The Sun Dish").verb(Verb.SHOOT).find().rank(4).lands(Group.DESERT, Group.BADLANDS).anchor(Anchor.LINE).length(40).made(Made.BUILT)
                .see("A bronze mirror on a post, and a bronze dish on a pillar a long bowshot off.")
                .learned("Turned to the dish at noon, the mirror threw the sun at it. The dish answered, and the stone at its foot let go.")
                .reach(20).tell(Tell.GLINT).icon(Props.MIRROR_DISC)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.column(0, -12, 0, 1, m.wall());
                    s.partOn("mirror", 0, -12, 2, Props.MIRROR_DISC.get().front(0, Direction.EAST));
                    s.on(1, -12, m.slab());
                    s.on(-1, -11, Props.LOOSE_STONE.get().of(0));
                    s.column(0, 14, 0, 3, m.wall());
                    s.up(0, 14, 4, m.slab());
                    s.partOn("dish", 0, 14, 5, Props.MIRROR_DISC.get().front(1, Direction.NORTH));
                    s.partOn("lamp", 1, 14, 0, Props.IRON_LAMP.get().of(0));
                    s.partOn("cache", 0, 13, 0, Props.CAP_STONE.get().of(0));
                    s.on(-1, 14, m.slab());
                    s.tell(0, 14, 6);
                    Dress.skirt(s, 3, m);
                })
                .script(Dish::new)
                .after(c -> c.set("lamp", 0, Hands.lit(c.at("lamp", 0), true)))
                .play(t -> t.hour(6000).use("mirror", 0))
                .add();
    }
}
