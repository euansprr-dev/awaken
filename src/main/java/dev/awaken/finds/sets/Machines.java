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

/** Old Machines (turn): things that still work if you set them right. */
final class Machines {
    private Machines() {}

    private static final int[] MX = {-3, -2, 0, 2, 3}, MZ = {0, -2, -3, -2, 0};
    /** Where a dial's raised mark sits for each state, as a step from the dial: north, east, south, west. */
    private static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};

    private static int state(Ctx c, String part) {
        return c.at(part, 0).getValue(dev.awaken.finds.kit.PropBlock.STATE);
    }

    /** The sundial: straighten the pointer, then press the stone its shadow lies on. The shadow moves through the day. */
    private static final class Sundial implements Script {
        private int under(Ctx c) {
            return c.night() ? -1 : (int) Math.min(4, c.hour() * 5 / 12000);
        }

        @Override
        public void tick(Ctx c) {
            if (state(c, "dial") == 0) return;
            int u = under(c);
            for (int i = 0; i < c.count("mark"); i++) {
                BlockState want = Props.START_STONE.get().of(i == u ? 3 : 0);
                if (c.at("mark", i) != want) c.set("mark", i, want);
            }
        }

        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (part.equals("dial")) {
                if (state(c, "dial") == 0) {
                    c.set("dial", 0, c.at("dial", 0).setValue(dev.awaken.finds.kit.PropBlock.STATE, 1));
                    c.sound(c.pos("dial", 0), net.minecraft.sounds.SoundEvents.COPPER_HIT, 0.9F, 0.8F);
                }
                return true;
            }
            if (!part.equals("mark") || state(c, "dial") == 0) return false;
            if (i == under(c)) c.complete(p);
            else c.sound(c.pos("mark", i), net.minecraft.sounds.SoundEvents.STONE_HIT, 0.6F, 0.6F);
            return true;
        }
    }

    /** The Mirror Posts: turn each disc until it throws the light on to the next. A beam shows between every pair that's set right, so you work down the line. */
    private static final class Mirrors implements Script {
        private final Turn turn = Turn.dials("mirror");

        @Override public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            return !c.night() && turn.use(c, p, part, i, held);
        }

        @Override
        public void tick(Ctx c) {
            if (c.night() || c.now() % 10 != 0) return;
            int[] t = Turn.targets(c.seed(), c.count("mirror"));
            for (int i = 0; i < t.length; i++) {
                if (c.at("mirror", i).getValue(dev.awaken.finds.kit.PropBlock.STATE) != t[i]) return;
                var a = c.pos("mirror", i).above();
                var b = i + 1 < t.length ? c.pos("mirror", i + 1).above() : c.pos("door", 0);
                for (int k = 1; k <= 8; k++)
                    c.level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, a.getX() + 0.5 + (b.getX() - a.getX()) * k / 9.0, a.getY() + 0.5 + (b.getY() - a.getY()) * k / 9.0,
                            a.getZ() + 0.5 + (b.getZ() - a.getZ()) * k / 9.0, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void open(Ctx c, String part) {
        for (int i = 0; i < c.count(part); i++) {
            c.puff(c.pos(part, i), net.minecraft.core.particles.ParticleTypes.POOF, 6, 0.3, 0.03);
            c.set(part, i, Blocks.AIR.defaultBlockState());
        }
        if (c.count(part) > 0) c.sound(c.pos(part, 0), net.minecraft.sounds.SoundEvents.GRINDSTONE_USE, 1.2F, 0.5F);
    }

    /** A small room cut into rising ground behind a door at z, with a pot in it; the cache is the pot. */
    private static void vault(Scene s, Mats m, int z) {
        for (int x = -1; x <= 1; x++)
            for (int zz = z + 1; zz <= z + 3; zz++) {
                s.put(x, 0, zz, m.stone());
                s.air(x, 1, zz);
                s.air(x, 2, zz);
            }
        s.put(0, 1, z + 2, Blocks.DECORATED_POT.defaultBlockState());
        s.part("cache", 0, 2, z + 2);
    }

    static void init() {
        Kind.of(FSet.MACHINES, 1, "sundial", "The Sundial").verb(Verb.TURN).curio().rank(0).lands(Group.PLAINS, Group.DESERT, Group.FOREST, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A stone sundial with its pointer bent flat, and a half ring of worn stones round it.")
                .learned("Straightened, its shadow walks the stones through the day. The one it rests on gives.")
                .reach(6).icon(Props.SUNDIAL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.partOn("dial", 0, 0, 0, Props.SUNDIAL.get().of(0));
                    for (int i = 0; i < 5; i++) s.partOn("mark", MX[i], MZ[i], 0, Props.START_STONE.get().of(0));
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 6, m);
                })
                .script(Sundial::new)
                .play(t -> t.hour(6000).use("dial", 0).ticks(1).use("mark", 2))
                .add();

        Kind.of(FSet.MACHINES, 2, "weather_vane", "The Weather Vane").verb(Verb.TURN).curio().rank(0).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("An iron vane on a post, rusted pointing one way whatever the wind does.")
                .learned("Greased, it swung round and then held on something that wasn't the wind.")
                .reach(5).slope(3).lead().icon(Props.WEATHER_VANE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.wall());
                    s.up(0, 0, 1, Props.POST.get().of(0));
                    s.partOn("vane", 0, 0, 2, Props.WEATHER_VANE.get().of(0, Direction.from2DDataValue(s.rnd.nextInt(4))));
                    s.on(1, 0, m.slab());
                    s.partOn("cache", -1, 0, 0);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Sockets.need("vane", st -> st.is(ItemTags.MEAT) || st.is(ItemTags.FISHES) || st.is(net.minecraft.world.item.Items.HONEYCOMB),
                        i -> Props.WEATHER_VANE.get().of(1, Direction.EAST)))
                .play(t -> t.hold(net.minecraft.world.item.Items.PORKCHOP).use("vane", 0))
                .add();

        Kind.of(FSet.MACHINES, 3, "three_rings", "The Three Rings").verb(Verb.TURN).curio().rank(1).lands(Group.FOREST, Group.JUNGLE, Group.DARK, Group.SWAMP).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A stone post of three rings that turn, each with one raised mark. Three small stones sit round it, at three different distances.")
                .learned("Nearest stone for the bottom ring, furthest for the top. Lined up, the cap lifted.")
                .reach(6).icon(Props.RING_POST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[] t = Turn.targets(s.site.seed(), 3);
                    for (int i = 0; i < 3; i++) {
                        s.partOn("ring", 0, 0, i, Props.RING_POST.get().of(0));
                        s.on(DX[t[i]] * (2 + i), DZ[t[i]] * (2 + i), Props.START_STONE.get().of(1));
                    }
                    s.partOn("cache", 0, 0, 3, Props.CAP_STONE.get().of(0));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Turn.dials("ring"))
                .play(t -> { int[] w = Turn.targets(t.seed(), 3); for (int i = 0; i < 3; i++) for (int k = 0; k < w[i]; k++) t.use("ring", i); })
                .add();

        Kind.of(FSet.MACHINES, 4, "mirror_posts", "The Mirror Posts").verb(Verb.TURN).find().rank(1).lands(Group.DESERT, Group.BADLANDS, Group.MOUNTAIN).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("Three bronze discs on posts, green with age, aimed at nothing, in front of a sealed doorway in the rock.")
                .learned("Each one turned until it threw the sun on to the next, and the last lit the door.")
                .reach(10).icon(Props.MIRROR_DISC)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[][] at = {{-4, -5}, {3, -3}, {-2, -1}};
                    for (int[] a : at) s.partOn("mirror", a[0], a[1], 0, Props.MIRROR_DISC.get().of(0));
                    s.put(0, 0, 1, m.path());
                    for (int k = 1; k <= 2; k++) s.part("door", 0, k, 2);
                    s.put(0, 1, 2, m.stone());
                    s.put(0, 2, 2, m.stone());
                    s.put(-1, 1, 2, m.wall());
                    s.put(1, 1, 2, m.wall());
                    s.put(-1, 2, 2, m.slab());
                    s.put(1, 2, 2, m.slab());
                    vault(s, m, 2);
                    Dress.skirt(s, 6, m);
                })
                .script(Mirrors::new)
                .after(c -> open(c, "door"))
                .play(t -> { t.hour(6000); int[] w = Turn.targets(t.seed(), 3); for (int i = 0; i < 3; i++) for (int k = 0; k < w[i]; k++) t.use("mirror", i); })
                .add();

        Kind.of(FSet.MACHINES, 5, "water_wheel", "The Water Wheel").verb(Verb.TURN).find().rank(2).anchor(Anchor.SHORE).made(Made.FALLEN)
                .see("A small mill by the water with its wheel lying in the grass and its race choked with branches.")
                .learned("Race cleared, wheel seated, sluice opened. The stones turned and shook a hatch loose.")
                .reach(9).slope(3).icon(Props.MILL_WHEEL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    for (int x = -2; x <= 0; x++)
                        for (int z = -4; z <= -2; z++) {
                            boolean edge = x == -2 || x == 0 || z == -4 || z == -2;
                            if (edge && !(x == -1 && z == -2)) {
                                s.on(x, z, m.stone());
                                s.up(x, z, 1, (x + z) % 2 == 0 ? m.planks() : m.wall());
                            } else s.floor(x, z, m.planks());
                        }
                    for (int x = -2; x <= 0; x++) s.up(x, -3, 2, m.woodSlab());
                    s.partOn("cache", -1, -3, 0, Props.CAP_STONE.get().of(0));
                    BlockState axle = m.log().hasProperty(RotatedPillarBlock.AXIS) ? m.log().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : m.log();
                    s.partOn("axle", 1, -3, 1, axle);
                    s.partOn("wheel", 2, -3, 1);
                    s.partOn("loose", 4, -1, 0, Props.MILL_WHEEL.get().of(1));
                    for (int z = -3; z <= 2; z++) s.floor(2, z, z % 2 == 0 ? Blocks.GRAVEL.defaultBlockState() : m.mossy());
                    s.partOn("clog", 2, -1, 0, Dress.twigs(s));
                    s.partOn("clog", 2, 0, 0, Blocks.DEAD_BUSH.defaultBlockState());
                    s.partOn("clog", 2, 1, 0, Dress.twigs(s));
                    s.partOn("sluice", 3, -4, 0, Blocks.LEVER.defaultBlockState());
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Steps.of(Dig.at("clog"), Assemble.of("wheel", st -> st.is(Props.MILL_WHEEL.get().asItem()), Props.MILL_WHEEL.get().of(0)).base("axle").carry("loose"),
                        Prize.at("sluice").then((c, i) -> c.sound(c.pos("sluice", 0), net.minecraft.sounds.SoundEvents.GRINDSTONE_USE, 1.5F, 0.6F))))
                .play(t -> { for (int i = 0; i < 3; i++) t.dig("clog", i); t.use("loose", 0).hold(Props.MILL_WHEEL.get()).use("axle", 0).use("sluice", 0); })
                .add();

        Kind.of(FSet.MACHINES, 6, "star_ring", "The Star Ring").verb(Verb.TURN).curio().rank(2).lands(Group.MOUNTAIN, Group.SNOW, Group.DESERT).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A brass ring on a stone stand, with a sighting mark on it, and one small gold stone set in the ground nearby.")
                .learned("Turned to the gold stone after dark, the mark found its star, and the stand opened.")
                .reach(5).slope(3).icon(Props.BRASS_RING)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    int t = Turn.targets(s.site.seed(), 1)[0];
                    s.on(0, 0, m.stone());
                    s.partOn("ring", 0, 0, 1, Props.BRASS_RING.get().of(0));
                    s.on(DX[t] * 3, DZ[t] * 3, Props.START_STONE.get().of(1));
                    s.partOn("cache", DX[(t + 2) & 3], DZ[(t + 2) & 3], 0, Props.FOOTING.get().of(0));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Turn.dials("ring").between(13000, 23000))
                .play(t -> { t.hour(18000); int w = Turn.targets(t.seed(), 1)[0]; for (int k = 0; k < w; k++) t.use("ring", 0); })
                .add();

        Kind.of(FSet.MACHINES, 7, "picture_door", "The Picture Door").verb(Verb.TURN).curio().rank(3).lands(Group.MOUNTAIN, Group.DARK, Group.BADLANDS, Group.FOREST).anchor(Anchor.CLIFF).made(Made.BUILT)
                .see("A door in the rock made of four stones that turn, each a piece of a picture. A smaller copy of the picture stands outside.")
                .learned("Matched to the copy, the four stones made the picture and drew back.")
                .reach(8).icon(Props.PICTURE_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    int[] t = Turn.targets(s.site.seed(), 4);
                    int[][] at = {{0, 1}, {1, 1}, {0, 2}, {1, 2}};
                    for (int i = 0; i < 4; i++) {
                        s.put(at[i][0], at[i][1], 2, Props.PICTURE_STONE.get().of(0));
                        s.part("door", at[i][0], at[i][1], 2);
                        s.put(-3 + at[i][0], at[i][1], -2, Props.PICTURE_STONE.get().of(t[i]));
                    }
                    s.put(0, 0, 2, m.stone());
                    s.put(1, 0, 2, m.stone());
                    s.put(-1, 1, 2, m.wall());
                    s.put(2, 1, 2, m.wall());
                    for (int x = 0; x <= 1; x++)
                        for (int z = 3; z <= 5; z++) {
                            s.put(x, 0, z, m.stone());
                            s.air(x, 1, z);
                            s.air(x, 2, z);
                        }
                    s.put(0, 1, 5, Blocks.DECORATED_POT.defaultBlockState());
                    s.part("cache", 0, 2, 5);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Turn.dials("door"))
                .after(c -> open(c, "door"))
                .play(t -> { int[] w = Turn.targets(t.seed(), 4); for (int i = 0; i < 4; i++) for (int k = 0; k < w[i]; k++) t.use("door", i); })
                .add();

        Kind.of(FSet.MACHINES, 8, "seized_orrery", "The Seized Orrery").verb(Verb.TURN).find().rank(4).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A brass model of the sky on a stone stand, seized up, with three of its arms missing.")
                .learned("Arms found and fitted, then wound. Everything unfinished nearby rang once from where it stands.")
                .reach(8).slope(4).lead().icon(Props.ORRERY)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    s.partOn("orrery", 0, 0, 0, Props.ORRERY.get().of(0));
                    for (int d = 0; d < 4; d++) s.on(DX[d] * 2, DZ[d] * 2, m.slab());
                    s.partOn("loose", 5, 2, 0, Props.ORRERY_ARM.get().of(0));
                    s.partOn("loose", -4, -5, 0, Props.ORRERY_ARM.get().of(1, Direction.EAST));
                    s.column(-5, 3, 0, 1, m.stone());
                    s.on(-4, 3, m.stairs().hasProperty(StairBlock.FACING) ? m.stairs().setValue(StairBlock.FACING, Direction.WEST) : m.stairs());
                    s.partOn("loose", -5, 3, 2, Props.ORRERY_ARM.get().of(2));
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 7, m);
                })
                .script(() -> Steps.of(Fit.on("orrery", st -> st.is(Props.ORRERY_ARM.get().asItem()), 3).carry("loose"),
                        Prize.at("orrery").then((c, i) -> c.sound(c.pos("orrery", 0), net.minecraft.sounds.SoundEvents.GRINDSTONE_USE, 1.2F, 1.4F))))
                .after(Ringing::ringAll)
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i).hold(Props.ORRERY_ARM.get()).use("orrery", 0); t.use("orrery", 0); })
                .add();
    }
}
