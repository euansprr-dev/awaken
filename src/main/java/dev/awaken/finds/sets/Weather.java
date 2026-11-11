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

import java.util.function.Predicate;

/** Night and Weather (wait): things that are only there at the right hour or under the right sky. */
final class Weather {
    private Weather() {}

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    /** The Rain Pond: dry until it rains, then full, with something silver in it that a bottle catches. */
    private static final class Pond implements Script {
        @Override
        public void tick(Ctx c) {
            boolean wet = c.raining();
            if ((c.v(0) == 1) != wet) {
                c.v(0, wet ? 1 : 0);
                fill(c, "pond", wet ? WATER : AIR);
            }
            if (wet && c.now() % 10 == 0) c.puff(c.pos("pond", 0), ParticleTypes.FISHING, 3, 0.6, 0.02);
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("pond") && !part.equals("rim")) return false;
            if (!c.raining()) {
                c.puff(c.pos(part, i), ParticleTypes.ASH, 6, 0.3, 0.01);
                return true;
            }
            if (!held.is(Items.GLASS_BOTTLE)) return false;
            if (!p.isCreative()) held.shrink(1);
            c.sound(c.pos(part, i), SoundEvents.BOTTLE_FILL, 1.0F, 1.2F);
            c.complete(p);
            return true;
        }

        @Override
        public void reset(Ctx c) {
            fill(c, "pond", AIR);
        }
    }

    /** One of several is the right one, and the world only shows which under the right sky (a reflection, a shaft of light). */
    private static final class Shown implements Script {
        private final Predicate<Ctx> when;

        Shown(Predicate<Ctx> when) {
            this.when = when;
        }

        private int right(Ctx c) {
            return Pick.right(c.seed(), c.count("stone"));
        }

        @Override
        public void tick(Ctx c) {
            boolean on = when.test(c);
            BlockState s = c.at("stone", right(c));
            if (s.getBlock() instanceof PropBlock && Hands.lit(s) != on) c.set("stone", right(c), Hands.lit(s, on));
            if (on && c.now() % 20 == 0) c.puff(c.pos("stone", right(c)).above(), ParticleTypes.END_ROD, 2, 0.15, 0.01);
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("stone")) return false;
            if (!when.test(c)) {
                c.puff(c.pos(part, i), ParticleTypes.ASH, 6, 0.3, 0.01);
                return true;
            }
            if (i == right(c)) c.complete(p);
            else c.sound(c.pos(part, i), SoundEvents.STONE_HIT, 1.0F, 0.6F);
            return true;
        }
    }

    /** The Star Bowl: on a clear night a star comes down on one of the marks. It shows where first. Be standing there. */
    private static final class Stars implements Script {
        @Override
        public int every() {
            return 2;
        }

        @Override
        public void tick(Ctx c) {
            if (!When.CLEAR_NIGHT.test(c)) return;
            long t = c.now() - c.clock();
            if (c.v(0) == 0 || t > 160) {
                c.v(0, 1 + c.level.getRandom().nextInt(c.count("mark")));
                c.clock(c.now());
                return;
            }
            BlockPos m = c.pos("mark", c.v(0) - 1);
            if (t < 100) {
                if (c.now() % 4 == 0) c.level.sendParticles(ParticleTypes.END_ROD, m.getX() + 0.5, m.getY() + 1 + (100 - t) / 5.0, m.getZ() + 0.5, 2, 0.05, 0.3, 0.05, 0);
                return;
            }
            c.sound(m, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.5F);
            c.puff(m, ParticleTypes.FIREWORK, 30, 0.4, 0.12);
            ServerPlayer who = null;
            for (ServerPlayer p : c.players(c.kind().reach() + 4)) if (p.distanceToSqr(m.getX() + 0.5, m.getY(), m.getZ() + 0.5) < 2.0 * 2.0) who = p;
            c.v(0, 0);
            if (who != null) c.complete(who);
        }
    }

    static void init() {
        Kind.of(FSet.WEATHER, 1, "sitting_stone", "The Sitting Stone").verb(Verb.WAIT).curio().rank(0).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A stone worn into a seat on top of a rise, and a flat slab set in the turf beside it.")
                .learned("Somebody sat here for the sunrise, often. The first light lifted the slab.")
                .reach(6).slope(3).icon(Props.CAP_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.partOn("seat", 0, 0, 0, stair(m, Direction.NORTH));
                    s.on(-1, 0, m.slab());
                    s.partOn("cache", 2, 1, 0, Props.CAP_STONE.get().of(0));
                    Dress.moss(s, -1, 0, 1, 2);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> When.stay("seat", When.DAWN, 20))
                .play(t -> t.hour(23500).stand("seat", 0).ticks(90))
                .add();

        Kind.of(FSet.WEATHER, 2, "rain_pond", "The Rain Pond").verb(Verb.WAIT).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.SWAMP).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A dry dip in the ground inside a ring of worn stones. Nothing to see on a clear day.")
                .learned("Rain fills it in a minute, and there are silver things in the water. One went into a bottle.")
                .reach(6).slope(1).icon(() -> Items.GLASS_BOTTLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int x = -1; x <= 1; x++)
                        for (int z = 2; z <= 4; z++) {
                            if (x != 0 && z != 3) continue;
                            s.put(x, 0, z, AIR);
                            s.put(x, -1, z, (x + z) % 2 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                            s.part("pond", x, 0, z);
                        }
                    int[][] ring = {{-2, 2}, {-2, 4}, {2, 2}, {2, 4}, {0, 6}, {-1, 1}, {1, 5}, {2, 3}};
                    for (int i = 0; i < ring.length; i++) s.partOn("rim", ring[i][0], ring[i][1], 0, (i & 1) == 0 ? Props.LOOSE_STONE.get().of(i & 3) : Props.WHITE_STONE.get().of(0));
                    s.partOn("cache", 0, 0, 0);
                    s.on(-3, 3, Blocks.SHORT_GRASS.defaultBlockState());
                    s.on(3, 5, Blocks.FERN.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(Pond::new)
                .play(t -> t.weather(2).ticks(1).hold(Items.GLASS_BOTTLE).use("pond", 0))
                .add();

        Kind.of(FSet.WEATHER, 3, "wind_harp", "The Wind Harp").verb(Verb.WAIT).curio().rank(1).lands(Group.PLAINS, Group.MOUNTAIN, Group.SNOW, Group.DESERT).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A frame of strings on a block of stone at the top of a hill. It's silent most days.")
                .learned("In rough weather it plays, and while it does the grass lies down in a line. There was something at the end of the line.")
                .reach(9).slope(3).tell(Tell.CHIME).icon(Props.WIND_HARP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 0, m.stone());
                    s.partOn("seat", 0, 0, 1, Props.WIND_HARP.get().of(0));
                    s.on(1, 0, m.slab());
                    s.on(-1, 0, stair(m, Direction.EAST));
                    for (int z = 2; z <= 6; z++) s.on(z % 2, z, Blocks.SHORT_GRASS.defaultBlockState());
                    s.floor(0, 7, Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("spot", 0, s.g(0, 7), 7);
                    s.part("cache", 0, s.g(0, 7) + 1, 7);
                    s.on(1, 7, Dress.pebbles(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(When.stay("seat", When.RAIN.or(When.STORM), 6), Dig.at("spot")))
                .play(t -> t.weather(2).stand("seat", 0).ticks(40).dig("spot", 0))
                .add();

        Kind.of(FSet.WEATHER, 4, "moon_pool", "The Moon Pool").verb(Verb.WAIT).curio().rank(1).lands(Group.FOREST, Group.DARK, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A still pool ringed with white stones.")
                .learned("In the middle of a clear night the moon sits on one stone and no other. Under that one was a moonstone.")
                .reach(6).slope(1).icon(Props.WHITE_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int x = -1; x <= 1; x++)
                        for (int z = 2; z <= 4; z++) {
                            s.put(x, 0, z, WATER);
                            s.put(x, -1, z, Blocks.GRAVEL.defaultBlockState());
                        }
                    for (int x = -2; x <= 2; x++)
                        for (int z = 1; z <= 5; z++)
                            if ((Math.abs(x) == 2 || z == 1 || z == 5) && s.get(x, 0, z) == null) s.put(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
                    int[][] ring = {{-2, 2}, {-2, 4}, {0, 5}, {2, 4}, {2, 2}, {0, 1}};
                    for (int[] a : ring) s.partOn("stone", a[0], a[1], 0, Props.WHITE_STONE.get().of(0));
                    s.partOn("cache", 0, 0, 0);
                    s.on(-2, 3, Blocks.FERN.defaultBlockState());
                    s.on(1, 5, Blocks.MOSS_CARPET.defaultBlockState());
                    s.put(0, 1, 3, Blocks.LILY_PAD.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> new Shown(When.MIDNIGHT.and(c -> !c.raining())))
                .play(t -> t.hour(18000).ticks(1).use("stone", Pick.right(t.seed(), 6)))
                .add();

        Kind.of(FSet.WEATHER, 5, "lightning_tree", "The Lightning Tree").verb(Verb.WAIT).curio().rank(2).lands(Group.PLAINS, Group.BADLANDS, Group.DESERT).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A burnt tree with an iron rod at its top, iron bars round its trunk and a capped stone in its roots.")
                .learned("Somebody built it to be struck. In a storm it was, and what was in the roots had fused to glass. It's worth more that way.")
                .reach(5).slope(3).icon(() -> Blocks.LIGHTNING_ROD.waxed().weathered())
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState burnt = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
                    s.column(0, 0, 0, 5, burnt);
                    s.partOn("seat", 0, 0, 0);
                    s.up(0, 0, 6, Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState());
                    for (int d = 0; d < 4; d++) s.up(DX[d], DZ[d], 2, Blocks.IRON_BARS.defaultBlockState());
                    s.up(1, 0, 4, axis(burnt, Direction.Axis.X));
                    s.up(-1, 0, 3, axis(burnt, Direction.Axis.X));
                    s.up(-2, 0, 3, Blocks.IRON_BARS.defaultBlockState());
                    s.partOn("cache", 1, 1, 0, Props.CAP_STONE.get().of(0));
                    s.on(-1, -1, Props.ROOT_LOOP.get().of(0));
                    s.on(-1, 1, Props.ROOT_LOOP.get().of(2));
                    s.floor(1, -1, Blocks.COARSE_DIRT.defaultBlockState());
                    s.on(2, -1, Blocks.DEAD_BUSH.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> When.stay("seat", When.STORM, 8))
                .after(c -> {
                    var bolt = EntityTypes.LIGHTNING_BOLT.create(c.level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                    if (bolt == null) return;
                    BlockPos top = c.pos("seat", 0).above(6);
                    bolt.snapTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                    bolt.setVisualOnly(true);
                    c.level.addFreshEntity(bolt);
                })
                .play(t -> t.weather(3).stand("seat", 0).ticks(60))
                .add();

        Kind.of(FSet.WEATHER, 6, "snow_shape", "The Snow Shape").verb(Verb.WAIT).curio().rank(2).lands(Group.SNOW).anchor(Anchor.LINE).length(30).made(Made.STRANGE)
                .see("The shape of someone lying down, pressed in the snow, always fresh.")
                .learned("While it snows, tracks lead away from it. They went to a drift with a cache under it, and no sign of whoever it was.")
                .reach(16).icon(() -> Items.SNOWBALL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    BlockState snow = Blocks.SNOW.defaultBlockState();
                    for (int x = -2; x <= 2; x++)
                        for (int z = -14; z <= -9; z++) {
                            boolean shape = x == 0 && z >= -13 && z <= -11 || z == -12 && Math.abs(x) == 1;
                            if (shape) s.floor(x, z, Blocks.PACKED_ICE.defaultBlockState());
                            else s.on(x, z, snow.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 1 + ((x * 3 + z * 5) & 1)));
                        }
                    int x = 0;
                    for (int i = 0; i < 6; i++) {
                        int z = -8 + i * 4;
                        x = Math.max(-3, Math.min(3, x + s.rnd.nextInt(3) - 1));
                        s.partOn("way", x, z, 0);
                    }
                    s.on(x + 1, 13, Blocks.SNOW_BLOCK.defaultBlockState());
                    s.on(x + 1, 14, snow.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 4));
                    s.on(x, 14, snow.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 2));
                    s.partOn("cache", x, 13, 0, Props.FOOTING.get().of(0));
                    s.on(x - 1, 13, Props.LOOSE_STONE.get().of(1));
                })
                .script(() -> When.only(When.SNOWING, Guide.along("way").as(ParticleTypes.SNOWFLAKE)))
                .play(t -> { t.weather(2); for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(160); })
                .add();

        Kind.of(FSet.WEATHER, 7, "star_bowl", "The Star Bowl").verb(Verb.WAIT).find().rank(3).lands(Group.DESERT, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A crater with glassy sides and scorch marks round the rim.")
                .learned("On a clear night stars come down here, and each one shows where it means to land. You were standing there.")
                .reach(10).slope(1).loot("star_iron").icon(Props.STAR_IRON)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -4; x <= 4; x++)
                        for (int z = 1; z <= 9; z++) {
                            int d2 = x * x + (z - 5) * (z - 5);
                            if (d2 > 17) continue;
                            if (d2 <= 5) {
                                s.put(x, 0, z, AIR);
                                s.put(x, -1, z, AIR);
                                s.put(x, -2, z, (x + z) % 2 == 0 ? Blocks.TINTED_GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                            } else if (d2 <= 11) {
                                s.put(x, 0, z, AIR);
                                s.put(x, -1, z, (x + z) % 3 == 0 ? Blocks.TINTED_GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                            } else {
                                s.floor(x, z, Blocks.COARSE_DIRT.defaultBlockState());
                                if ((x * 7 + z * 3) % 4 == 0) s.on(x, z, Blocks.BLACKSTONE_SLAB.defaultBlockState());
                                else if ((x + z) % 3 == 0) s.on(x, z, Props.LOOSE_STONE.get().of((x + z) & 3));
                            }
                        }
                    int[][] at = {{0, 5}, {-2, 4}, {2, 6}, {1, 3}, {-1, 7}};
                    for (int[] a : at) s.part("mark", a[0], a[0] * a[0] + (a[1] - 5) * (a[1] - 5) <= 5 ? -1 : 0, a[1]);
                    s.partOn("cache", 0, 0, 0);
                    s.on(-1, 0, Blocks.DEAD_BUSH.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(Stars::new)
                .play(t -> { t.hour(18000).weather(1).ticks(1); t.stand("mark", t.c().v(0) - 1).pass(110).ticks(1); })
                .add();

        Kind.of(FSet.WEATHER, 8, "dawn_slot", "The Dawn Slot").verb(Verb.WAIT).find().rank(4).lands(Group.MOUNTAIN, Group.BADLANDS, Group.SNOW).anchor(Anchor.LINE).length(24).made(Made.BUILT)
                .see("A slot cut through a fin of rock, and a rock face some way behind it with a row of chalk marks.")
                .learned("At sunrise the light goes through the slot and lands on one mark. That was the one that opened.")
                .reach(12).icon(Props.CHALK_MARK)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    BlockState rock = Blocks.STONE.defaultBlockState();
                    for (int x = -2; x <= 2; x++)
                        for (int dy = 1; dy <= 5 - Math.abs(x); dy++) {
                            if (x == 0 && (dy == 2 || dy == 3)) continue;
                            s.put(x, dy, -8, rock);
                        }
                    s.put(0, 1, -7, m.slab());
                    for (int x = -3; x <= 3; x++) {
                        for (int dy = 1; dy <= 4 - (Math.abs(x) == 3 ? 1 : 0); dy++) s.put(x, dy, 8, rock);
                        if (Math.abs(x) <= 2) {
                            s.put(x, 2, 7, Props.CHALK_MARK.get().front(0, Direction.SOUTH));
                            s.part("stone", x, 2, 7);
                        }
                    }
                    s.put(0, 1, 7, Props.CAP_STONE.get().of(0));
                    s.part("cache", 0, 1, 7);
                    s.on(-3, 6, Props.LOOSE_STONE.get().of(0));
                    s.on(2, -6, Props.LOOSE_STONE.get().of(3));
                    s.on(3, 6, m.slab());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> new Shown(When.DAWN))
                .play(t -> t.hour(23500).ticks(1).use("stone", Pick.right(t.seed(), 5)))
                .add();
    }
}
