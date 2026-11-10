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

/** Cairns (stack): stones set on stones. */
final class Cairns {
    private Cairns() {}

    private static boolean loose(net.minecraft.world.item.ItemStack st) {
        return st.is(Props.LOOSE_STONE.get().asItem());
    }

    /** The Marble Run: shove the top boulder and each knocks the next down the hill; the last cracks the capstone. Numbers: 0 how many have gone, 1 running. */
    private static final class Roll implements Script {
        @Override public int every() { return 2; }

        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (!part.equals("boulder") || i != 0 || c.v(1) != 0) return part.equals("boulder");
            c.v(1, 1);
            c.clock(c.now());
            return true;
        }

        @Override
        public void tick(Ctx c) {
            if (c.v(1) == 0) return;
            int gone = c.v(0);
            if (gone >= c.count("boulder") || c.now() - c.clock() < (gone + 1) * 8L) return;
            BlockState b = c.at("boulder", gone);
            c.set("boulder", gone, Blocks.AIR.defaultBlockState());
            c.set("rest", gone, b);
            c.sound(c.pos("rest", gone), net.minecraft.sounds.SoundEvents.STONE_BREAK, 1.0F, 0.6F + 0.05F * gone);
            c.puff(c.pos("rest", gone), net.minecraft.core.particles.ParticleTypes.POOF, 6, 0.3, 0.02);
            c.v(0, gone + 1);
            if (gone + 1 == c.count("boulder")) {
                var near = c.players(24);
                c.complete(near.isEmpty() ? null : near.get(0));
            }
        }
    }

    /** The Balanced Rock: pull the wedge and it rocks for a minute; the hoard under it can be had only while it does. Numbers: 0 rocking. */
    private static final class Rock implements Script {
        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (part.equals("wedge") && c.v(0) == 0) {
                c.set("wedge", 0, Blocks.AIR.defaultBlockState());
                c.v(0, 1);
                c.clock(c.now());
                c.sound(c.pos("wedge", 0), net.minecraft.sounds.SoundEvents.GRINDSTONE_USE, 1.0F, 0.5F);
                return true;
            }
            if (part.equals("prize") && c.v(0) == 1) {
                c.complete(p);
                return true;
            }
            return part.equals("prize") || part.equals("wedge");
        }

        @Override
        public void tick(Ctx c) {
            if (c.v(0) != 1) return;
            long t = c.now() - c.clock();
            if (t > 1200) { // it settles: the wedge is back where it was
                c.v(0, 0);
                c.set("wedge", 0, c.made("wedge", 0));
                return;
            }
            if (t % 20 < 5) {
                c.sound(c.site.ground().above(2), net.minecraft.sounds.SoundEvents.STONE_HIT, 0.7F, 0.5F);
                c.puff(c.site.ground().above(), net.minecraft.core.particles.ParticleTypes.POOF, 3, 0.8, 0.01);
            }
        }
    }

    static void init() {
        Kind.of(FSet.CAIRNS, 1, "wanderers_cairn", "The Wanderer's Cairn").verb(Verb.STACK).curio().rank(0).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A knee-high stack of flat stones on a rise, with one more lying in the grass beside it.")
                .learned("Somebody marked this rise for whoever came after. It's a stone taller for you.")
                .reach(7).slope(3).persists().icon(Props.CAIRN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    Dress.cairn(s, 0, 0, 2, 2);
                    s.partOn("top", 0, 0, 1);
                    s.partOn("loose", 2, 1, 0, Props.LOOSE_STONE.get().of(s.rnd.nextInt(4)));
                    s.partOn("cache", -1, 1, 0);
                    Dress.scatter(s, 0, 0, 3, 4, () -> Dress.pebbles(s));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Fit.on("top", Cairns::loose, 1).carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.LOOSE_STONE.get()).use("top", 0))
                .add();

        Kind.of(FSet.CAIRNS, 2, "twin_cairns", "The Twin Cairns").verb(Verb.STACK).curio().rank(0).lands(Group.FOREST, Group.PLAINS, Group.DARK).anchor(Anchor.LINE).length(20).made(Made.BUILT)
                .see("Two cairns either side of an overgrown path. One has been knocked flat and its stones kicked about.")
                .learned("They marked a way through. Both stand again.")
                .reach(10).persists().icon(Props.FLAT_STONES)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -9, 9, 1, m);
                    Dress.cairn(s, -3, 0, 2, 3);
                    s.partOn("build", 3, 0, 0, Props.CAIRN.get().of(2));
                    for (int i = 0; i < 3; i++) s.partOn("build", 3, 0, 1);
                    int[][] at = {{5, 2}, {1, -3}, {4, -4}, {6, -1}};
                    for (int[] a : at) s.partOn("loose", a[0], a[1], 0, Props.LOOSE_STONE.get().of(s.rnd.nextInt(4), Direction.from2DDataValue(s.rnd.nextInt(4))));
                    s.partOn("cache", 3, 1, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Assemble.of("build", Cairns::loose, Props.CAIRN.get().of(0), Props.FLAT_STONES.get().of(0), Props.FLAT_STONES.get().of(1), Props.FLAT_STONES.get().of(2)).carry("loose"))
                .play(t -> { for (int i = 0; i < 4; i++) t.use("loose", i).hold(Props.LOOSE_STONE.get()).use("build", 0); })
                .add();

        Kind.of(FSet.CAIRNS, 3, "counting_cairns", "The Counting Cairns").verb(Verb.STACK).curio().rank(1).lands(Group.MOUNTAIN, Group.BADLANDS, Group.SNOW).anchor(Anchor.LINE).length(32).made(Made.BUILT)
                .see("Seven cairns in a line along a ridge, each one stone taller than the last, except the fifth.")
                .learned("A count kept in stone, one cairn a year perhaps. The fifth stands as tall as it should.")
                .reach(17).persists().icon(Props.CAIRN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int i = 0; i < 7; i++) {
                        int z = -12 + i * 4;
                        if (i == 4) {
                            s.partOn("build", 0, z, 0, Props.FLAT_STONES.get().of(2));
                            s.partOn("build", 0, z, 1);
                            s.partOn("cache", 1, z, 0);
                        } else if (i < 4) s.on(0, z, Props.FLAT_STONES.get().of(i, Direction.from2DDataValue(s.rnd.nextInt(4))));
                        else {
                            s.on(0, z, Props.CAIRN.get().of(s.rnd.nextInt(2)));
                            s.up(0, z, 1, Props.FLAT_STONES.get().of(i - 4));
                        }
                    }
                    s.partOn("loose", 3, 5, 0, Props.LOOSE_STONE.get().of(1));
                    s.partOn("loose", -4, 2, 0, Props.LOOSE_STONE.get().of(2));
                    Dress.scatter(s, 0, 0, 6, 8, () -> Dress.pebbles(s));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Assemble.of("build", Cairns::loose, Props.CAIRN.get().of(0), Props.FLAT_STONES.get().of(0)).carry("loose"))
                .play(t -> { for (int i = 0; i < 2; i++) t.use("loose", i).hold(Props.LOOSE_STONE.get()).use("build", 0); })
                .add();

        Kind.of(FSet.CAIRNS, 4, "leaning_stack", "The Leaning Stack").verb(Verb.STACK).curio().rank(1).anchor(Anchor.SHORE).made(Made.BUILT)
                .see("A stack of flat stones on a rock at the water's edge, leaning further than it should.")
                .learned("Three more stones and it still stands. Whoever started it would be pleased.")
                .reach(7).slope(3).persists().icon(Props.FLAT_STONES)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 1, m.mossy());
                    s.on(1, 1, m.slab());
                    s.partOn("top", 0, 1, 1, Props.FLAT_STONES.get().of(0, Direction.EAST));
                    int[][] at = {{-3, -1}, {2, -2}, {-1, -3}};
                    for (int[] a : at) s.partOn("loose", a[0], a[1], 0, Props.LOOSE_STONE.get().of(s.rnd.nextInt(4)));
                    s.partOn("cache", -1, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Fit.on("top", Cairns::loose, 3).carry("loose"))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i).hold(Props.LOOSE_STONE.get()).use("top", 0); })
                .add();

        Kind.of(FSet.CAIRNS, 5, "humming_cairn", "The Humming Cairn").verb(Verb.STACK).curio().rank(2).lands(Group.SNOW, Group.MOUNTAIN).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A cairn whose top stone has a hole through it. The wind makes it moan, badly.")
                .learned("Turned to the wind, the note came clean, and another cairn answered from far off.")
                .reach(6).slope(3).lead().tell(Tell.HUM).icon(Props.STANDING_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, Props.CAIRN.get().of(1));
                    s.up(0, 0, 1, Props.CAIRN.get().of(0, Direction.EAST));
                    s.partOn("top", 0, 0, 2, Props.STANDING_STONE.get().of(3));
                    s.partOn("cache", 1, 0, 0);
                    s.tell(0, 0, 3);
                    Dress.scatter(s, 0, 0, 3, 5, () -> Dress.pebbles(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Turn.dials("top").byFacing())
                .play(t -> { int n = Turn.targets(t.seed(), 1)[0]; for (int i = 0; i < n; i++) t.use("top", 0); })
                .add();

        Kind.of(FSet.CAIRNS, 6, "marble_run", "The Marble Run").verb(Verb.STACK).find().rank(2).lands(Group.PLAINS, Group.DESERT, Group.BADLANDS, Group.MOUNTAIN).anchor(Anchor.SLOPE).made(Made.BUILT)
                .see("Five round boulders sitting down a hillside with a worn groove between them, choked with brush.")
                .learned("Cleared and shoved, each knocked the next, and the last cracked the capstone at the bottom.")
                .reach(14).slope(12).icon(Props.BOULDER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    for (int z = -9; z <= 11; z++) s.floor(0, z, z % 3 == 0 ? m.loose() : m.path().is(Blocks.DIRT_PATH) ? m.loose() : m.path());
                    for (int i = 0; i < 5; i++) {
                        s.partOn("boulder", 0, -8 + i * 4, 0, Props.BOULDER.get().of(i == 3 ? 1 : 0, Direction.from2DDataValue(s.rnd.nextInt(4))));
                        s.partOn("rest", 0, i == 4 ? 10 : -5 + i * 4, 0);
                    }
                    s.partOn("clog", 0, -6, 0, Dress.twigs(s));
                    s.partOn("clog", 0, -2, 0, Blocks.DEAD_BUSH.defaultBlockState());
                    s.partOn("clog", 0, 2, 0, Dress.twigs(s));
                    s.partOn("cache", 0, 11, 0, Props.CAP_STONE.get().of(0));
                    for (int z = -8; z <= 8; z += 4) {
                        s.on(-1, z, m.slab());
                        s.on(1, z, m.slab());
                    }
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Steps.of(Dig.at("clog"), new Roll()))
                .play(t -> { for (int i = 0; i < 3; i++) t.dig("clog", i); t.use("boulder", 0); for (int i = 0; i < 5; i++) t.pass(10).ticks(1); })
                .add();

        Kind.of(FSet.CAIRNS, 7, "balanced_rock", "The Balanced Rock").verb(Verb.STACK).curio().rank(3).lands(Group.BADLANDS, Group.DESERT, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A boulder the size of a hut balanced on a point, with a small stone wedged under one side.")
                .learned("With the wedge out it rocked for a full minute. That was long enough to reach under.")
                .reach(6).tell(Tell.GLINT).icon(Props.WEDGE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.stone());
                    for (int x = -1; x <= 1; x++)
                        for (int z = -1; z <= 1; z++) {
                            boolean corner = x != 0 && z != 0;
                            s.up(x, z, 1, corner ? Props.BOULDER.get().of(s.rnd.nextInt(2)) : m.stone());
                            if (!corner) s.up(x, z, 2, x == 0 && z == 0 ? m.slab() : Props.BOULDER.get().of(3));
                        }
                    s.partOn("wedge", 1, 0, 0, Props.WEDGE.get().of(0, Direction.WEST));
                    s.partOn("prize", -1, 0, 0, Props.HOARD.get().of(0));
                    s.partOn("cache", -1, 0, 0);
                    s.tell(-1, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(Rock::new)
                .play(t -> t.use("wedge", 0).ticks(1).use("prize", 0))
                .add();

        Kind.of(FSet.CAIRNS, 8, "summit_stones", "The Summit Stones").verb(Verb.STACK).find().rank(4).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.PEAK).made(Made.BUILT)
                .see("A cairn on a summit, topped with a stone of a colour that doesn't belong up here.")
                .learned("Each summit holds a stone from the next. This one is yours to carry on.")
                .reach(6).slope(4).needsMove().lead().icon(Props.SUMMIT_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.on(0, 0, Props.CAIRN.get().of(0));
                    s.up(0, 0, 1, Props.FLAT_STONES.get().of(1));
                    s.partOn("top", 0, 0, 2, Props.SUMMIT_STONE.get().of((int) (s.site.seed() & 3)));
                    s.partOn("cache", 1, 0, 0);
                    Dress.scatter(s, 0, 0, 3, 6, () -> Dress.pebbles(s));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("top").then((c, i) -> {
                    c.pop(c.pos("top", 0), new net.minecraft.world.item.ItemStack(c.at("top", 0).getBlock()));
                    c.set("top", 0, Blocks.AIR.defaultBlockState());
                }))
                .play(t -> t.use("top", 0))
                .add();
    }
}
