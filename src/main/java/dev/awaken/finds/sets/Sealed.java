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

/** Sealed Things (break): something shut that opens only to the right thing. */
final class Sealed {
    private Sealed() {}

    private static final BlockState ROCK = Blocks.STONE.defaultBlockState();

    /** A mound with a hollow in it and a mouth facing north: {@code shell} outside, air inside, the mouth left for the kind to stop up. */
    private static void den(Scene s, BlockState shell, BlockState cap) {
        for (int x = -2; x <= 2; x++)
            for (int z = 2; z <= 4; z++)
                for (int k = 0; k <= 2; k++) {
                    boolean hollow = x == 0 && z == 3 && k <= 1, mouth = x == 0 && z == 2 && k <= 1;
                    if (hollow || mouth) continue;
                    if (k == 2 && (Math.abs(x) == 2 || z == 4) && s.rnd.nextBoolean()) continue;
                    s.up(x, z, k, k == 2 ? cap : shell);
                }
    }

    /** The Sky Seal: four bolts, all sheared inside ten seconds, or they seat themselves again. */
    private static final class Bolts implements Script {
        private boolean shear(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("bolt")) return false;
            if (!Hands.pick(held) && !Hands.blade(held)) {
                c.sound(c.pos(part, i), SoundEvents.STONE_HIT, 0.5F, 0.6F);
                return true;
            }
            int bits = c.v(0);
            if (bits == 0) c.clock(c.now());
            if ((bits & (1 << i)) != 0) return true;
            bits |= 1 << i;
            c.v(0, bits);
            c.set(part, i, Hands.lit(c.at(part, i), true));
            c.sound(c.pos(part, i), SoundEvents.ANVIL_PLACE, 0.8F, 1.2F + 0.1F * Integer.bitCount(bits));
            c.puff(c.pos(part, i), ParticleTypes.CRIT, 8, 0.2, 0.1);
            if (Integer.bitCount(bits) == c.count(part)) c.complete(p);
            return true;
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            return shear(c, p, part, i, held);
        }

        @Override
        public boolean hit(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            return shear(c, p, part, i, held);
        }

        @Override
        public void tick(Ctx c) {
            if (c.v(0) == 0 || c.now() - c.clock() <= 200) return;
            c.v(0, 0);
            for (int i = 0; i < c.count("bolt"); i++) c.set("bolt", i, c.made("bolt", i));
            c.sound(c.pos("bolt", 0), SoundEvents.ANVIL_LAND, 0.6F, 0.6F);
        }
    }

    static void init() {
        Kind.of(FSet.SEALED, 1, "cracked_boulder", "The Cracked Boulder").verb(Verb.BREAK).curio().rank(0).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A boulder with a crack through it, and something glinting inside.")
                .learned("A pick opened it along the crack. It was hollow, and lined.")
                .reach(5).tell(Tell.GLINT).loot("geode").icon(() -> Items.AMETHYST_SHARD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -1; x <= 1; x++)
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && z == 0) continue;
                            s.on(x, z, s.rnd.nextInt(3) == 0 ? m.mossy() : m.stone());
                            if (Math.abs(x) + Math.abs(z) == 1) s.up(x, z, 1, stair(m, x == 1 ? Direction.WEST : x == -1 ? Direction.EAST : z == 1 ? Direction.NORTH : Direction.SOUTH));
                        }
                    s.up(0, 0, 1, m.slab());
                    s.on(0, 0, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                    s.partOn("cache", 0, 0, 0);
                    s.partOn("seal", 0, 1, 0, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                    s.tell(0, 1, 1);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Seal.opens("seal", 3, Hands::pick))
                .play(t -> t.hold(Items.IRON_PICKAXE).hit("seal", 0).hit("seal", 0).hit("seal", 0))
                .add();

        Kind.of(FSet.SEALED, 2, "thorn_door", "The Thorn Door").verb(Verb.BREAK).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.SWAMP).anchor(Anchor.FLAT).made(Made.ALIVE)
                .see("A wall of bramble grown over the mouth of a hollow in an earth bank.")
                .learned("A blade went through it. The hollow was full of what the brambles had caught over the years.")
                .reach(6).icon(() -> Items.SHEARS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    den(s, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState());
                    s.partOn("seal", 0, 2, 0, Blocks.MANGROVE_ROOTS.defaultBlockState());
                    s.partOn("seal", 0, 2, 1, Blocks.MANGROVE_ROOTS.defaultBlockState());
                    s.on(0, 3, Props.BASKET.get().of(1));
                    s.partOn("cache", 0, 1, 0);
                    s.on(-1, 1, Blocks.DEAD_BUSH.defaultBlockState());
                    s.on(1, 1, Blocks.SWEET_BERRY_BUSH.defaultBlockState());
                    s.up(1, 3, 3, Blocks.FERN.defaultBlockState());
                    s.on(-2, 1, Props.ROOT_LOOP.get().of(0));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Seal.opens("seal", 2, Hands::blade))
                .play(t -> t.hold(Items.IRON_AXE).hit("seal", 0).hit("seal", 1))
                .add();

        Kind.of(FSet.SEALED, 3, "ice_plug", "The Ice Plug").verb(Verb.BREAK).curio().rank(1).lands(Group.SNOW, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A hole in a hump of rock, stopped with blue ice. There's a shape in the ice.")
                .learned("Fire took it out, slowly. Something had crawled in there and been kept.")
                .reach(6).tell(Tell.DRIP).icon(() -> Items.BLUE_ICE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    den(s, ROCK, Blocks.SNOW_BLOCK.defaultBlockState());
                    s.partOn("seal", 0, 2, 0, Blocks.BLUE_ICE.defaultBlockState());
                    s.partOn("seal", 0, 2, 1, Blocks.BLUE_ICE.defaultBlockState());
                    s.on(0, 3, Blocks.DECORATED_POT.defaultBlockState());
                    s.partOn("cache", 0, 1, 0);
                    s.on(-1, 1, Blocks.SNOW.defaultBlockState());
                    s.on(1, 1, Props.LOOSE_STONE.get().of(0));
                    s.on(2, 1, Blocks.SNOW.defaultBlockState());
                    s.tell(0, 2, 2);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Seal.opens("seal", 6, Hands::flame))
                .play(t -> { t.hold(Items.TORCH); for (int i = 0; i < 6; i++) t.hit("seal", 0); })
                .add();

        Kind.of(FSet.SEALED, 4, "web_sack", "The Web Sack").verb(Verb.BREAK).curio().rank(1).lands(Group.DARK, Group.JUNGLE, Group.SWAMP).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A grey bundle the size of a barrel, hung on one thread in the middle of a web between two trees.")
                .learned("One shot through the thread and it dropped clear of the web. It was what the spider had been saving.")
                .reach(6).icon(Props.BUNDLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    for (int x = -3; x <= 3; x += 6) {
                        s.column(x, 0, 0, 7, m.log());
                        Dress.crown(s, x, 0, 7, 2, leaf(Blocks.DARK_OAK_LEAVES));
                    }
                    int top = s.g(0, 0) + 8;
                    for (int x = -2; x <= 2; x++) s.put(x, top, 0, axis(m.log(), Direction.Axis.X));
                    s.put(0, top - 1, 0, Props.TAUT_ROPE.get().of(0));
                    s.part("thread", 0, top - 1, 0);
                    s.put(0, top - 2, 0, Props.BUNDLE.get().of(2));
                    s.part("sack", 0, top - 2, 0);
                    BlockState web = Blocks.COBWEB.defaultBlockState();
                    for (int x = -2; x <= 2; x++) {
                        if (x == 0) continue;
                        s.put(x, top - 1, 0, web);
                        s.put(x, top - 2, 0, web);
                        if (Math.abs(x) == 2) s.put(x, top - 3, 0, web);
                    }
                    s.partOn("cache", 0, 0, 0);
                    s.on(1, 1, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    s.on(-2, 1, web);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> dev.awaken.finds.verb.Marks.hit("thread"))
                .after(c -> {
                    c.set("sack", 0, AIR);
                    c.set("cache", 0, Props.BUNDLE.get().of(2));
                })
                .play(t -> t.shoot("thread", 0))
                .add();

        Kind.of(FSet.SEALED, 5, "rusted_grate", "The Rusted Grate").verb(Verb.BREAK).curio().rank(2).lands(Group.SWAMP, Group.PLAINS, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("An iron grate over the mouth of a stone drain, rusted solid, with something caught behind it.")
                .learned("A pick broke the hinges. Cold would have done it too: iron that old doesn't like either.")
                .reach(6).icon(() -> Items.IRON_BARS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    for (int z = 2; z <= 4; z++) {
                        for (int x = -1; x <= 1; x += 2) {
                            s.on(x, z, z == 2 ? m.wall() : (x + z) % 2 == 0 ? m.stone() : m.mossy());
                            s.up(x, z, 1, z == 2 ? m.slab() : top(m.slab()));
                        }
                        s.up(0, z, 1, z == 2 ? stair(m, Direction.SOUTH) : m.slab());
                    }
                    s.on(0, 4, m.mossy());
                    s.partOn("seal", 0, 2, 0, Blocks.IRON_BARS.defaultBlockState());
                    s.partOn("cache", 0, 2, 0);
                    s.on(0, 3, Blocks.DECORATED_POT.defaultBlockState());
                    s.floor(0, 1, Blocks.MUD.defaultBlockState());
                    s.floor(0, 0, Blocks.MUD.defaultBlockState());
                    s.on(1, 1, Blocks.MOSS_CARPET.defaultBlockState());
                    s.on(-2, 2, Blocks.FERN.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Seal.opens("seal", 5, Hands::pick, Hands::cold))
                .play(t -> { t.hold(Items.IRON_PICKAXE); for (int i = 0; i < 5; i++) t.hit("seal", 0); })
                .add();

        Kind.of(FSet.SEALED, 6, "glass_seed", "The Glass Seed").verb(Verb.BREAK).curio().rank(2).lands(Group.DESERT, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("A pod of dark glass the size of a pumpkin, and an old bell on a post a few steps off. Hitting the pod does nothing.")
                .learned("It broke to the bell, not to a blow. The seed inside grows something odd.")
                .reach(6).loot("seeds").icon(() -> Items.TINTED_GLASS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.partOn("seal", 0, 2, 0, Blocks.TINTED_GLASS.defaultBlockState());
                    s.partOn("cache", 0, 2, 0);
                    for (int d = 0; d < 4; d++) s.on(DX[d], 2 + DZ[d], d % 2 == 0 ? Props.LOOSE_STONE.get().of(d) : Blocks.DEAD_BUSH.defaultBlockState());
                    s.column(-3, -1, 0, 1, m.fence());
                    s.partOn("bell", -3, -1, 2, Blocks.BELL.defaultBlockState());
                    s.on(-2, -1, Blocks.DEAD_BUSH.defaultBlockState());
                    s.on(-3, 0, m.slab());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), SoundEvents.BELL_BLOCK, 2.0F, 1.0F)))
                .after(c -> {
                    c.set("seal", 0, AIR);
                    c.sound(c.pos("seal", 0), SoundEvents.GLASS_BREAK, 1.5F, 0.7F);
                })
                .play(t -> t.use("bell", 0))
                .add();

        Kind.of(FSet.SEALED, 7, "stone_egg", "The Stone Egg").verb(Verb.BREAK).find().rank(3).lands(Group.BADLANDS, Group.MOUNTAIN, Group.DESERT).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("A smooth stone egg as tall as a door. The ground is scorched on one side of it and frosted on the other.")
                .learned("It wanted heat, cold and a blow, all inside a few breaths. Inside it was hollow, and lined with crystal.")
                .reach(6).loot("geode").tell(Tell.HUM).icon(() -> Items.SMOOTH_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState egg = Blocks.SMOOTH_STONE.defaultBlockState();
                    s.partOn("seal", 0, 3, 0, egg);
                    s.partOn("seal", 0, 3, 1, egg);
                    s.up(0, 3, 2, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                    Direction[] in = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
                    for (int d = 0; d < 4; d++) s.on(DX[d], 3 + DZ[d], Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, in[d]));
                    s.partOn("cache", 0, 3, 0);
                    s.floor(2, 3, Blocks.MAGMA_BLOCK.defaultBlockState());
                    s.floor(2, 4, Blocks.COARSE_DIRT.defaultBlockState());
                    s.floor(-2, 3, Blocks.PACKED_ICE.defaultBlockState());
                    s.on(-2, 2, Blocks.SNOW.defaultBlockState());
                    s.on(-3, 3, Blocks.SNOW.defaultBlockState());
                    s.on(3, 2, Blocks.DEAD_BUSH.defaultBlockState());
                    s.tell(0, 3, 3);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Seal.opens("seal", 1, Hands::flame, Hands::cold, Hands::pick).allOf())
                .after(c -> c.set("seal", 0, Blocks.AMETHYST_CLUSTER.defaultBlockState()))
                .play(t -> t.hold(Items.FLINT_AND_STEEL).hit("seal", 0).hold(Items.SNOWBALL).hit("seal", 0).hold(Items.IRON_PICKAXE).hit("seal", 0))
                .add();

        Kind.of(FSet.SEALED, 8, "sky_seal", "The Sky Seal").verb(Verb.BREAK).find().rank(4).lands(Group.MOUNTAIN, Group.SNOW).anchor(Anchor.PEAK).made(Made.BUILT)
                .see("A bronze cap on a peak, held down by four bolts as thick as your wrist, over something hollow.")
                .learned("All four sheared before the first could seat itself again. Under the cap is a shaft with rungs, and a long way down.")
                .reach(6).slope(3).icon(Props.BOLT)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    BlockState rung = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH);
                    for (int dy = -6; dy <= 0; dy++) {
                        s.put(0, dy, 4, m.stone());
                        s.put(0, dy, 3, dy <= -5 ? Blocks.WATER.defaultBlockState() : rung);
                    }
                    s.put(0, -7, 3, m.stone());
                    s.put(0, 1, 3, Blocks.CUT_COPPER_SLAB.waxed().weathered().defaultBlockState());
                    s.part("cap", 0, 1, 3);
                    for (int d = 0; d < 4; d++) s.partOn("bolt", DX[d], 3 + DZ[d], 0, Props.BOLT.get().of(0));
                    for (int x = -1; x <= 1; x += 2)
                        for (int z = 2; z <= 4; z += 2) s.on(x, z, m.slab());
                    s.partOn("cache", 2, 3, 0, Props.FOOTING.get().of(0));
                    s.on(-2, 2, Props.SUMMIT_STONE.get().of(1));
                    s.on(2, 5, Blocks.SNOW.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(Bolts::new)
                .after(c -> {
                    c.set("cap", 0, AIR);
                    c.sound(c.pos("cap", 0), SoundEvents.ANVIL_LAND, 1.5F, 0.5F);
                })
                .play(t -> { t.hold(Items.IRON_PICKAXE); for (int i = 0; i < 4; i++) t.hit("bolt", i); })
                .add();
    }
}
