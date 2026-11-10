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

/** Lamplighter's Road (light): lamps that somebody used to light. */
final class Lamps {
    private Lamps() {}

    private static final BlockState LEAF = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    /** The Night Road's first job: stand the fallen posts up. Each one used rises with its lamp on. Numbers: 0 a bit per post stood. */
    private static final class Stand implements Script {
        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (!part.equals("down")) return false;
            int bits = c.v(0);
            if ((bits & (1 << i)) != 0) return true;
            c.set("down", i, Props.POST.get().of(0));
            c.set(c.pos("down", i).above(), Props.IRON_LAMP.get().of(0));
            c.sound(c.pos("down", i), net.minecraft.sounds.SoundEvents.WOOD_PLACE, 1.0F, 0.7F);
            bits |= 1 << i;
            c.v(0, bits);
            if (Integer.bitCount(bits) == c.count("down")) c.complete(p);
            return true;
        }
    }

    static void init() {
        Kind.of(FSet.LAMPS, 1, "cold_lamps", "The Cold Lamps").verb(Verb.LIGHT).curio().rank(0).anchor(Anchor.LINE).length(52).made(Made.BUILT)
                .see("Five iron lamps on old posts along a stretch of road, all dark.")
                .learned("Somebody lit these every evening. The road went somewhere worth seeing by night.")
                .reach(27).tell(Tell.MOTHS).icon(Props.IRON_LAMP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -24, 26, 1, m);
                    int z = -20;
                    for (int i = 0; i < 5; i++) {
                        int x = i % 2 == 0 ? 2 : -2;
                        s.on(x, z, m.wall());
                        s.up(x, z, 1, Props.POST.get().of(0));
                        s.partOn("lamp", x, z, 2, Props.IRON_LAMP.get().of(0));
                        Dress.moss(s, x + (x > 0 ? 1 : -1), z, 0, 3);
                        if (i == 0) s.tell(x, z, 3);
                        if (i == 2 && s.rnd.nextBoolean()) s.on(x + (x > 0 ? 1 : -1), z, Props.CART_WHEEL.get().of(0, Direction.EAST));
                        if (i == 4) s.partOn("cache", x, z + 1, 0, Props.FOOTING.get().front(0, x > 0 ? Direction.WEST : Direction.EAST));
                        z += 9 + s.rnd.nextInt(3);
                    }
                    s.on(3, -22, Props.MILESTONE.get().of(s.rnd.nextInt(3)));
                })
                .script(() -> Light.all("lamp").within(60))
                .play(t -> { t.hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < t.count("lamp"); i++) t.use("lamp", i); })
                .add();

        Kind.of(FSet.LAMPS, 2, "moth_lantern", "The Moth Lantern").verb(Verb.LIGHT).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.SWAMP).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A paper lantern hanging from a branch, patched many times, unlit.")
                .learned("Lit after dark, it brought moths in their hundreds, and one of them was carrying something.")
                .reach(6).tell(Tell.MOTHS).icon(Props.PAPER_LANTERN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState log = m.log(), branch = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : log;
                    s.column(0, 0, 0, 5, log);
                    s.up(1, 0, 4, branch);
                    s.up(2, 0, 4, branch);
                    for (int x = -2; x <= 3; x++)
                        for (int z = -2; z <= 2; z++)
                            for (int k = 5; k <= 6; k++)
                                if (!(x == 0 && z == 0 && k == 5) && Math.abs(x) + Math.abs(z) + (k - 5) * 2 <= 4 && s.rnd.nextInt(5) != 0) s.up(x, z, k, LEAF);
                    s.partOn("lamp", 2, 0, 3, Props.PAPER_LANTERN.get().of(0));
                    s.tell(2, 0, 3);
                    s.partOn("cache", 2, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> When.only(When.NIGHT, Light.all("lamp")))
                .after(c -> c.puff(c.pos("lamp", 0), net.minecraft.core.particles.ParticleTypes.WHITE_ASH, 60, 1.2, 0.02))
                .play(t -> t.hour(18000).hold(net.minecraft.world.item.Items.TORCH).use("lamp", 0))
                .add();

        Kind.of(FSet.LAMPS, 3, "signal_fire", "The Signal Fire").verb(Verb.LIGHT).find().rank(1).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("An iron fire basket on a stone stand on a bare hill, stacked and ready, with a view for miles.")
                .learned("Inside a minute another fire answered on the horizon. Somebody keeps that one too.")
                .reach(7).slope(4).lead().icon(Props.FIRE_BASKET)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    s.on(0, 0, m.stone());
                    s.partOn("lamp", 0, 0, 1, Props.FIRE_BASKET.get().of(0));
                    for (int d = 0; d < 4; d++) {
                        Direction dir = Direction.from2DDataValue(d);
                        BlockState st = m.stairs();
                        s.on(dir.getStepX(), dir.getStepZ(), st.hasProperty(StairBlock.FACING) ? st.setValue(StairBlock.FACING, dir.getOpposite()) : st);
                    }
                    s.on(2, 2, m.log().hasProperty(RotatedPillarBlock.AXIS) ? m.log().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : m.log());
                    s.on(3, 2, m.woodSlab());
                    s.on(2, 3, Dress.twigs(s));
                    s.partOn("cache", -2, 1, 0, Props.FOOTING.get().of(0));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Light.all("lamp"))
                .after(c -> c.puff(c.pos("lamp", 0).above(2), net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 12, 0.3, 0.04))
                .play(t -> t.hold(net.minecraft.world.item.Items.FLINT_AND_STEEL).use("lamp", 0))
                .add();

        Kind.of(FSet.LAMPS, 4, "candle_hollow", "The Candle Hollow").verb(Verb.LIGHT).curio().rank(1).lands(Group.FOREST, Group.SWAMP, Group.DARK).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A hollow tree with candle stubs on every ledge inside it, and a knothole at the back you can see daylight through.")
                .learned("The draught through the knothole kept blowing them out. Blocked, they all stayed lit.")
                .reach(6).icon(() -> net.minecraft.world.item.Items.CANDLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[][] wall = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}};
                    for (int[] w : wall)
                        for (int k = 0; k <= 3; k++)
                            if (!(w[0] == 0 && k == 1)) s.up(w[0], w[1], k, m.log());
                    s.partOn("hole", 0, -1, 1);
                    for (int x = -1; x <= 1; x++) s.up(x, 0, 4, x == 0 ? m.log() : LEAF);
                    s.up(0, -1, 4, LEAF);
                    BlockState candle = Blocks.CANDLE.defaultBlockState();
                    var count = net.minecraft.world.level.block.CandleBlock.CANDLES;
                    s.partOn("lamp", 0, 0, 0, candle.setValue(count, 4));
                    s.on(-1, 1, m.woodSlab());
                    s.partOn("lamp", -1, 1, 1, candle.setValue(count, 3));
                    s.on(1, 1, m.woodSlab());
                    s.partOn("lamp", 1, 1, 1, candle.setValue(count, 2));
                    s.partOn("lamp", 0, 1, 0, candle.setValue(count, 3));
                    s.partOn("cache", 0, 2, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Light.all("lamp").draught("hole", 0, 3))
                .play(t -> { t.set("hole", 0, Blocks.DIRT.defaultBlockState()).hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < 4; i++) t.use("lamp", i); })
                .add();

        Kind.of(FSet.LAMPS, 5, "lighthouse_stub", "The Lighthouse Stub").verb(Verb.LIGHT).find().rank(2).anchor(Anchor.SHORE).made(Made.FALLEN)
                .see("The bottom of a lighthouse at the water's edge. Its lens lies in the grass beside it, still whole.")
                .learned("Carried up, set and lit, the beam stopped on one spot of sea. Something was sunk there.")
                .reach(9).slope(3).icon(Props.LENS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    for (int x = -1; x <= 1; x++)
                        for (int z = -3; z <= -1; z++) {
                            boolean mid = x == 0 && z == -2;
                            s.on(x, z, s.rnd.nextInt(4) == 0 ? m.mossy() : m.stone());
                            if (x == 0 && z == -1) {
                                BlockState st = m.stairs();
                                s.up(x, z, 1, st.hasProperty(StairBlock.FACING) ? st.setValue(StairBlock.FACING, Direction.NORTH) : st);
                            } else if (mid) s.partOn("stand", x, z, 1, m.wall());
                            else s.up(x, z, 1, (x + z) % 2 == 0 ? m.slab() : m.stone());
                        }
                    BlockState step = m.stairs();
                    s.on(0, 0, step.hasProperty(StairBlock.FACING) ? step.setValue(StairBlock.FACING, Direction.NORTH) : step);
                    s.partOn("lens", 0, -2, 2);
                    s.partOn("loose", 4, -2, 0, Props.LENS.get().of(0));
                    s.on(4, -3, Blocks.IRON_BARS.defaultBlockState());
                    s.on(5, -2, m.slab());
                    s.part("cache", 0, s.sea() + 1, 6);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Steps.of(Sockets.need("stand", st -> st.is(Props.LENS.get().asItem()), i -> Props.LENS.get().of(0)).onTop().carry("loose"), Light.all("lens")))
                .play(t -> t.use("loose", 0).hold(Props.LENS.get()).use("stand", 0).hold(net.minecraft.world.item.Items.FLINT_AND_STEEL).use("lens", 0))
                .add();

        Kind.of(FSet.LAMPS, 6, "frozen_lamp", "The Frozen Lamp").verb(Verb.LIGHT).curio().rank(2).lands(Group.SNOW).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A lamp post buried to its neck in a drift, the glass iced over.")
                .learned("Dug out and lit, it showed what the drift had been hiding.")
                .reach(5).icon(Props.IRON_LAMP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.wall());
                    s.up(0, 0, 1, Props.POST.get().of(0));
                    s.partOn("lamp", 0, 0, 2, Props.IRON_LAMP.get().of(2));
                    BlockState layer = Blocks.SNOW.defaultBlockState();
                    var layers = net.minecraft.world.level.block.SnowLayerBlock.LAYERS;
                    int[][] drift = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}};
                    for (int i = 0; i < drift.length; i++) s.partOn("drift", drift[i][0], drift[i][1], 0, layer.setValue(layers, 8 - (i % 3)));
                    s.up(1, 0, 1, layer.setValue(layers, 4));
                    s.up(0, 1, 1, layer.setValue(layers, 3));
                    s.on(2, 1, layer.setValue(layers, 3));
                    s.on(-2, 0, layer.setValue(layers, 2));
                    s.partOn("cache", 0, 2, 0, Props.BUNDLE.get().of(2));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Steps.of(Dig.at("drift"), Light.all("lamp")))
                .play(t -> { for (int i = 0; i < t.count("drift"); i++) t.dig("drift", i); t.hold(net.minecraft.world.item.Items.TORCH).use("lamp", 0); })
                .add();

        Kind.of(FSet.LAMPS, 7, "deep_lamps", "The Deep Lamps").verb(Verb.LIGHT).curio().rank(3).lands(Group.MOUNTAIN, Group.BADLANDS, Group.FOREST, Group.DARK).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("A cut going into the rock, with an iron lamp in a niche every few steps. The first one stands at the mouth.")
                .learned("Each lamp only took from the one before it. The last showed what the cut was for.")
                .reach(13).icon(Props.IRON_LAMP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int z = 0; z <= 11; z++) {
                        s.put(0, 0, z, z % 3 == 0 ? m.stone() : Blocks.GRAVEL.defaultBlockState());
                        if (z > 0) {
                            s.air(0, 1, z);
                            s.air(0, 2, z);
                        }
                    }
                    s.on(1, -1, m.wall());
                    s.partOn("lamp", 1, -1, 1, Props.IRON_LAMP.get().of(0));
                    for (int z = 2; z <= 8; z += 3) {
                        int x = (z / 3) % 2 == 0 ? 1 : -1;
                        s.put(x, 1, z, m.slab());
                        s.air(x, 2, z);
                        s.part("lamp", x, 2, z);
                        s.put(x, 2, z, Props.IRON_LAMP.get().of(0));
                    }
                    for (int x = -1; x <= 1; x++)
                        for (int z = 10; z <= 12; z++) {
                            s.put(x, 0, z, m.stone());
                            if (x != 0 || z != 11) {
                                s.air(x, 1, z);
                                s.air(x, 2, z);
                            }
                        }
                    s.put(0, 1, 11, Blocks.DECORATED_POT.defaultBlockState());
                    s.air(0, 2, 11);
                    s.part("cache", 0, 2, 11);
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Light.all("lamp").inOrder())
                .play(t -> { t.hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < t.count("lamp"); i++) t.use("lamp", i); })
                .add();

        Kind.of(FSet.LAMPS, 8, "night_road", "The Night Road").verb(Verb.LIGHT).find().rank(4).lands(Group.DARK, Group.PLAINS, Group.SWAMP, Group.FOREST).anchor(Anchor.LINE).length(84).made(Made.BUILT)
                .see("A long line of lamp posts crossing bad country, half of them fallen.")
                .learned("Every post stood up and every lamp lit in one night. Nothing hunts on that road now.")
                .reach(44).persists().wards(44).tell(Tell.MOTHS).icon(Props.POST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -42, 42, 1, m);
                    for (int i = 0; i < 9; i++) {
                        int z = -40 + i * 10, x = i % 2 == 0 ? 2 : -2;
                        if (i % 2 == 1 || i == 4) {
                            s.partOn("down", x, z, 0, Props.POST.get().of(3, Direction.from2DDataValue(s.rnd.nextInt(4))));
                            s.partOn("lamp", x, z, 1);
                        } else {
                            s.on(x, z, Props.POST.get().of(0));
                            s.partOn("lamp", x, z, 1, Props.IRON_LAMP.get().of(i == 0 ? 0 : 2));
                        }
                        if (i == 0) s.tell(x, z, 2);
                        if (i == 8) s.partOn("cache", x, z + 1, 0, Props.FOOTING.get().of(0));
                    }
                })
                .script(() -> Steps.of(new Stand(), When.only(When.NIGHT, Light.all("lamp").within(150))))
                .play(t -> { for (int i = 0; i < t.count("down"); i++) t.use("down", i); t.hour(18000).hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < t.count("lamp"); i++) t.use("lamp", i); })
                .add();
    }
}
