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

/** Runs (run): a start, a way, and a clock. Your best time is kept. */
final class Runs {
    private Runs() {}

    /** The Vent Stair: warm air lifts whoever stands in it, and sets them down gently after. The prize is at the top. */
    private static final class Vents implements Script {
        @Override
        public int every() {
            return 2;
        }

        @Override
        public void tick(Ctx c) {
            int n = c.count("vent");
            for (ServerPlayer p : c.players(c.kind().reach() + 4))
                for (int i = 0; i < n; i++) {
                    BlockPos v = c.pos("vent", i);
                    double dx = p.getX() - (v.getX() + 0.5), dz = p.getZ() - (v.getZ() + 0.5), dy = p.getY() - v.getY();
                    if (dx * dx + dz * dz < 1.44 && dy >= -0.5 && dy < 7) {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LEVITATION, 6, 5, true, false));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 160, 0, true, false));
                    }
                }
            if (c.now() % 10 == 0) for (int i = 0; i < n; i++) c.puff(c.pos("vent", i).above(), ParticleTypes.CLOUD, 2, 0.15, 0.08);
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("prize")) return false;
            c.complete(p);
            return true;
        }
    }

    static void init() {
        Kind.of(FSet.RUNS, 1, "lantern_run", "The Lantern Run").verb(Verb.RUN).curio().rank(0).anchor(Anchor.LINE).length(66).made(Made.BUILT)
                .see("A flat stone with a mark cut in it, and a row of small lanterns going off across country.")
                .learned("Touch the stone and they light in a chain. You reached the last before the first went out.")
                .reach(34).icon(Props.START_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.partOn("start", 0, -30, 0, Props.START_STONE.get().of(1));
                    int x = 0;
                    for (int i = 0; i < 6; i++) {
                        int z = -20 + i * 10;
                        x += s.rnd.nextInt(5) - 2;
                        x = Math.max(-4, Math.min(4, x));
                        s.on(x, z, Props.POST.get().of(0));
                        s.partOn("gate", x, z, 1, Props.IRON_LAMP.get().of(0));
                        if (i == 5) s.partOn("cache", x + 1, z, 0, Props.FOOTING.get().front(0, Direction.NORTH));
                    }
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Course.run("start", "gate", 16))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();

        Kind.of(FSet.RUNS, 2, "log_road", "The Log Road").verb(Verb.RUN).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.SWAMP).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("Fallen trunks lying end to end across a wide gully, with a marked stump at the near end.")
                .learned("Stump to stump without a foot in the gully. The last one was hollow.")
                .reach(12).slope(1).icon(() -> Items.OAK_LOG)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -8; x <= 8; x++)
                        for (int z = -2; z <= 2; z++) {
                            s.air(x, 0, z);
                            if (x == 0 && z == 0) {
                                s.put(x, -1, z, Blocks.COARSE_DIRT.defaultBlockState());
                                continue;
                            }
                            s.air(x, -1, z);
                            if (s.rnd.nextInt(3) == 0) s.put(x, -2, z, Blocks.MUD.defaultBlockState());
                        }
                    for (int x = -8; x <= 8; x++) {
                        if (x == -3 || x == 4) continue;
                        s.put(x, 0, 0, axis(m.log(), Direction.Axis.X));
                    }
                    s.put(6, -1, 0, m.log());
                    s.put(-6, -1, 0, m.log());
                    s.on(-10, 0, m.log());
                    s.partOn("start", -10, 0, 1, Props.START_STONE.get().of(1));
                    for (int x : new int[] {-6, -1, 2, 6}) s.part("gate", x, 1, 0);
                    s.on(10, 0, m.log());
                    s.part("gate", 10, 2, 0);
                    s.partOn("cache", 10, 1, 0, Props.FOOTING.get().of(0));
                    s.put(-5, 1, 0, Blocks.MOSS_CARPET.defaultBlockState());
                    s.put(7, 1, 0, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    s.on(-9, 1, Blocks.FERN.defaultBlockState());
                    s.on(9, -1, Dress.twigs(s));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Course.run("start", "gate", 20).dontFall(2))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();

        Kind.of(FSet.RUNS, 3, "chalk_line", "The Chalk Line").verb(Verb.RUN).curio().rank(1).lands(Group.BADLANDS, Group.MOUNTAIN, Group.DESERT).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("A line of chalk marks along a rock face, well above the ground, from one ledge to another.")
                .learned("Run along the wall, mark to mark, and don't come down. Somebody did it often enough to wear the rock.")
                .reach(12).needsMove().icon(Props.CHALK_MARK)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -10; x <= 10; x++) {
                        for (int dy = 1; dy <= 5; dy++) {
                            s.put(x, dy, 2, Blocks.TERRACOTTA.defaultBlockState());
                            if (dy <= 4) {
                                s.air(x, dy, 1);
                                s.air(x, dy, 0);
                            }
                        }
                    }
                    for (int x = -10; x <= 10; x += 20) {
                        s.put(x, 1, 1, Blocks.TERRACOTTA.defaultBlockState());
                        s.put(x, 2, 1, m.slab());
                        s.put(x < 0 ? x + 1 : x - 1, 1, 1, m.slab());
                    }
                    s.put(-10, 3, 1, Props.START_STONE.get().of(1));
                    s.part("start", -10, 3, 1);
                    for (int x = -7; x <= 8; x += 3) {
                        s.put(x, 3, 1, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                        s.part("gate", x, 3, 1);
                    }
                    s.put(10, 3, 1, Props.FOOTING.get().of(0));
                    s.part("cache", 10, 3, 1);
                    s.on(-8, -1, Props.LOOSE_STONE.get().of(0));
                    s.on(6, -2, Props.LOOSE_STONE.get().of(2));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Course.run("start", "gate", 10).dontFall(1))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();

        Kind.of(FSet.RUNS, 4, "ring_posts", "The Ring Posts").verb(Verb.RUN).curio().rank(1).lands(Group.JUNGLE, Group.FOREST, Group.DARK).anchor(Anchor.LINE).length(40).made(Made.BUILT)
                .see("Tall posts with an iron ring at the top of each, a rope's length apart, and a stump with a ladder at the near end.")
                .learned("Ring to ring on the rope without landing. It made the rope feel longer than it was.")
                .reach(20).needsMove("tether").icon(Props.RING_TARGET)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.column(0, -16, 0, 2, m.log());
                    s.partOn("start", 0, -16, 3, Props.START_STONE.get().of(1));
                    for (int k = 0; k <= 2; k++) s.up(0, -17, k, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
                    for (int i = 0; i < 4; i++) {
                        int z = -9 + i * 8;
                        s.column(0, z, 0, 6, m.log());
                        s.partOn("gate", 0, z, 7, Props.RING_TARGET.get().of(0));
                        s.on(1, z, m.fence());
                        s.on(-1, z, Dress.twigs(s));
                    }
                    s.partOn("cache", 1, 17, 0, Props.FOOTING.get().of(0));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Course.run("start", "gate", 25).dontFall(2))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();

        Kind.of(FSet.RUNS, 5, "vent_stair", "The Vent Stair").verb(Verb.RUN).curio().rank(2).lands(Group.MOUNTAIN, Group.SNOW, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("Three needles of rock, each taller than the last, and cracks in the ground that breathe warm air straight up.")
                .learned("Each crack lifts you as high as the next needle. The air sets you down gently, too.")
                .reach(9).slope(3).tell(Tell.STEAM).icon(Props.VENT)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.partOn("vent", 0, -5, 0, Props.VENT.get().of(0));
                    int[] z = {-2, 2, 6}, h = {5, 10, 15};
                    for (int i = 0; i < 3; i++) {
                        s.column(0, z[i], 0, h[i] - 1, Blocks.STONE.defaultBlockState());
                        for (int d = 0; d < 4; d++) {
                            s.column(DX[d], z[i] + DZ[d], 0, h[i] / 3 - (d & 1), Blocks.STONE.defaultBlockState());
                            s.up(DX[d], z[i] + DZ[d], h[i] - 1, m.slab());
                        }
                        if (i < 2) s.partOn("vent", 0, z[i], h[i], Props.VENT.get().of(0));
                        s.on(1, z[i] + 1, Props.LOOSE_STONE.get().of(i));
                    }
                    s.partOn("prize", 0, 6, 15, Props.CAP_STONE.get().of(0));
                    s.partOn("cache", 0, 6, 15);
                    s.tell(0, -5, 1);
                    Dress.skirt(s, 3, m);
                })
                .script(Vents::new)
                .play(t -> t.ticks(2).use("prize", 0))
                .add();

        Kind.of(FSet.RUNS, 6, "otters_slide", "The Otters' Slide").verb(Verb.RUN).find().rank(0).anchor(Anchor.SHORE).made(Made.BEAST)
                .see("A mud bank worn into a chute that drops through three pools, with otters waiting their turn at the top.")
                .learned("Top to bottom through all three pools, as fast as they do it. The last pool is where they keep what they find.")
                .reach(10).icon(Props.HOARD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    BlockState mud = Blocks.MUD.defaultBlockState();
                    for (int x = -1; x <= 1; x++)
                        for (int z = -8; z <= -6; z++) s.column(x, z, 0, x == 0 && z == -7 ? 2 : 1 + ((x + z) & 1), mud);
                    s.partOn("start", 0, -7, 3, Props.FLAT_STONES.get().of(0));
                    s.column(0, -5, 0, 1, mud);
                    s.on(0, -4, mud);
                    s.on(1, -5, Blocks.MUD_BRICK_SLAB.defaultBlockState());
                    s.on(-1, -6, Blocks.MUD_BRICK_SLAB.defaultBlockState());
                    int[][] pool = {{0, -2}, {3, 1}, {0, 4}};
                    for (int i = 0; i < 3; i++) {
                        for (int x = 0; x <= 1; x++)
                            for (int z = 0; z <= 1; z++) {
                                s.put(pool[i][0] + x, 0, pool[i][1] + z, Blocks.WATER.defaultBlockState());
                                s.put(pool[i][0] + x, -1, pool[i][1] + z, mud);
                            }
                        for (int x = -1; x <= 2; x++)
                            for (int z = -1; z <= 2; z++)
                                if ((x == -1 || x == 2 || z == -1 || z == 2) && s.get(pool[i][0] + x, 0, pool[i][1] + z) == null) s.put(pool[i][0] + x, 0, pool[i][1] + z, mud);
                        s.part("gate", pool[i][0], 1, pool[i][1]);
                    }
                    s.partOn("cache", -1, 5, 0, Props.HOARD.get().of(0));
                    var otter = Life.type("brook_otter", EntityTypes.FROG);
                    s.spawn(otter, 1, -4, 0, "otter").spawn(otter, -1, -4, 0, "otter").spawn(otter, 2, 3, 0, "otter");
                    s.on(-2, -3, Blocks.SHORT_GRASS.defaultBlockState());
                    s.on(4, -1, Blocks.FERN.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Course.run("start", "gate", 14))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();

        Kind.of(FSet.RUNS, 7, "goat_path", "The Goat Path").verb(Verb.RUN).find().rank(2).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.CLIFF).made(Made.BEAST)
                .see("A stair of ledges up the rock, no wider than a foot, with a goat standing on the first of them.")
                .learned("Keep up with the goat and it shows you every foothold. The lick at the top has things pressed into it.")
                .reach(8).icon(() -> Items.GOAT_HORN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int i = 0; i < 9; i++) {
                        int x = -4 + i;
                        s.column(x, 2, 0, i, Blocks.STONE.defaultBlockState());
                        if (i > 0) s.column(x, 3, 0, i - 1, Blocks.STONE.defaultBlockState());
                        s.up(x, 2, i + 1, i % 3 == 2 ? Blocks.SNOW.defaultBlockState() : Dress.pebbles(s));
                        s.partOn("way", x, 2, i + 1);
                        if (i < 8) s.up(x, 1, i / 2, m.slab());
                    }
                    s.up(4, 3, 8, Blocks.CALCITE.defaultBlockState());
                    s.partOn("cache", 4, 3, 9);
                    s.spawnUp(EntityTypes.GOAT, -4, 2, 1, 0, "goat");
                    s.on(-5, 1, Props.LOOSE_STONE.get().of(1));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Guide.along("way").creature("goat").speed(0.9).note(SoundEvents.GOAT_AMBIENT))
                .play(t -> { for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(160); })
                .add();

        Kind.of(FSet.RUNS, 8, "hundred_posts", "The Hundred Posts").verb(Verb.RUN).find().rank(3).lands(Group.SWAMP, Group.OCEAN, Group.PLAINS, Group.FOREST).anchor(Anchor.SHORE).made(Made.BUILT)
                .see("A line of posts going out across open water, a stride apart, to a scrap of island.")
                .learned("Post to post, all the way, without getting wet. Nobody counted whether it was a hundred.")
                .reach(46).icon(Props.POST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    int top = Math.max(s.sea() + 1, 2);
                    int x = 0;
                    for (int i = 0; i < 13; i++) {
                        int z = 3 + i * 3;
                        if (i > 0) x = Math.max(-3, Math.min(3, x + s.rnd.nextInt(3) - 1));
                        for (int dy = Math.min(s.g(x, z) + 1, top); dy <= top; dy++) s.put(x, dy, z, m.log());
                        if (i == 0) {
                            s.put(x, top + 1, z, Props.START_STONE.get().of(1));
                            s.part("start", x, top + 1, z);
                        } else s.part("gate", x, top + 1, z);
                    }
                    for (int ix = -1; ix <= 1; ix++)
                        for (int iz = 43; iz <= 45; iz++)
                            for (int dy = Math.min(s.g(x + ix, iz) + 1, top); dy <= top; dy++) s.put(x + ix, dy, iz, dy == top ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    s.part("gate", x, top + 1, 43);
                    s.put(x, top + 1, 44, Props.FOOTING.get().of(0));
                    s.part("cache", x, top + 1, 44);
                    s.put(x + 1, top + 1, 45, Blocks.DEAD_BUSH.defaultBlockState());
                    s.put(x - 1, top + 1, 44, Props.OLD_ROD.get().of(0));
                    s.on(1, 0, Props.ROPE_COIL.get().of(0));
                    Dress.skirt(s, 2, m);
                })
                .script(() -> Course.run("start", "gate", 45).dontFall(1))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();
    }
}
