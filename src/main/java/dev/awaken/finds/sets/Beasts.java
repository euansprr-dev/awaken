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

import net.minecraft.world.item.DyeColor;

/** Beast Works (sneak): what the animals made, and how to be near it without being a nuisance. */
final class Beasts {
    private Beasts() {}

    private static final BlockState ROCK = Blocks.STONE.defaultBlockState();
    private static final int ASLEEP = Scene.Spawn.NO_AI | Scene.Spawn.QUIET;

    static boolean blue(ItemStack st) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return id.contains("blue") || id.contains("lapis") || id.contains("cornflower") || id.contains("orchid");
    }

    static void init() {
        Kind.of(FSet.BEASTS, 1, "magpie_bower", "The Magpie Bower").verb(Verb.SNEAK).curio().rank(0).lands(Group.FOREST, Group.PLAINS, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("An arch of woven sticks on the ground, with blue things laid out in front of it.")
                .learned("Take nothing. Add something blue, and it moves one of its own to your side.")
                .reach(6).icon(Props.HOARD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -1; x <= 1; x += 2) s.column(x, 0, 0, 1, m.fence());
                    for (int x = -1; x <= 1; x++) s.up(x, 0, 2, m.woodSlab());
                    s.on(0, -1, Props.HOARD.get().of(0));
                    s.partOn("cache", 0, 0, 0);
                    s.partOn("socket", 0, 1, 0);
                    s.on(-1, 2, Blocks.CORNFLOWER.defaultBlockState());
                    s.on(1, 1, Blocks.CARPET.pick(DyeColor.BLUE).defaultBlockState());
                    s.on(2, 2, Dress.twigs(s));
                    s.spawn(Life.type("wild_crow", EntityTypes.PARROT), 0, -2, 0, "bird");
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("socket", Beasts::blue, i -> Blocks.CARPET.pick(DyeColor.BLUE).defaultBlockState()))
                .play(t -> t.hold(Items.LAPIS_LAZULI).use("socket", 0))
                .add();

        Kind.of(FSet.BEASTS, 2, "marmot_town", "The Marmot Town").verb(Verb.SNEAK).curio().rank(1).lands(Group.MOUNTAIN, Group.SNOW, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A patch of burrows with a marmot sitting up on every rock, watching.")
                .learned("Crossed at a creep, stopping whenever one stiffened. Their store was at the far side, and they let you look.")
                .reach(9).slope(3).icon(Props.BASKET)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    var marmot = Life.type("scree_marmot", EntityTypes.RABBIT);
                    int[][] rock = {{-3, -2}, {3, -1}, {-2, 3}, {2, 4}, {0, 0}};
                    for (int i = 0; i < rock.length; i++) {
                        s.on(rock[i][0], rock[i][1], i % 2 == 0 ? Props.BOULDER.get().of(i & 3) : m.stone());
                        if (i % 2 == 1) s.up(rock[i][0], rock[i][1], 1, m.slab());
                        s.spawnUp(marmot, rock[i][0], rock[i][1], i % 2 == 1 ? 2 : 1, Scene.Spawn.NO_AI, "watch");
                    }
                    s.partOn("sleeper", 0, 0, 1);
                    int[][] hole = {{-1, -3}, {1, 2}, {-3, 1}, {3, 2}, {-1, 5}, {2, -3}};
                    for (int[] h : hole) {
                        s.put(h[0], s.g(h[0], h[1]), h[1], AIR);
                        s.put(h[0], s.g(h[0], h[1]) - 1, h[1], Blocks.COARSE_DIRT.defaultBlockState());
                        s.floor(h[0] + 1, h[1], Blocks.COARSE_DIRT.defaultBlockState());
                    }
                    s.partOn("prize", 0, 7, 0, Props.BASKET.get().of(1));
                    s.partOn("cache", 0, 7, 0);
                    s.on(1, 7, Props.LOOSE_STONE.get().of(0));
                    s.on(-1, 6, Blocks.SHORT_GRASS.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Quiet.near("sleeper", "prize", 7).voice(SoundEvents.RABBIT_AMBIENT))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.BEASTS, 3, "bears_larder", "The Bear's Larder").verb(Verb.SNEAK).find().rank(1).lands(Group.FOREST, Group.SNOW, Group.DARK).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A den in a hump of rock, and a bear the size of a hut asleep across the only way in.")
                .learned("Between its paws, one step at a time, stopping when they twitched. Honey, bones and one very old sword.")
                .reach(8).loot("old_sword").icon(() -> Items.HONEYCOMB)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int x = -3; x <= 3; x++)
                        for (int z = 2; z <= 6; z++)
                            for (int dy = 1; dy <= 4; dy++) {
                                boolean in = Math.abs(x) <= 2 && z >= 3 && z <= 5 && dy <= 2, mouth = x == 0 && z == 2 && dy <= 2;
                                if (in || mouth) {
                                    s.air(x, dy, z);
                                    continue;
                                }
                                if (dy == 4 && (Math.abs(x) == 3 || z == 2 || z == 6 || s.rnd.nextInt(3) == 0)) continue;
                                s.put(x, dy, z, dy == 4 ? Blocks.GRASS_BLOCK.defaultBlockState() : ROCK);
                            }
                    s.part("sleeper", 0, 1, 1);
                    s.spawn(Life.type("honey_bear", EntityTypes.POLAR_BEAR), 0, 1, ASLEEP, "bear");
                    s.put(0, 1, 5, Blocks.DECORATED_POT.defaultBlockState());
                    s.part("prize", 0, 1, 5);
                    s.part("cache", 0, 1, 4);
                    s.put(-2, 1, 5, Blocks.HONEYCOMB_BLOCK.defaultBlockState());
                    s.put(2, 1, 4, Props.GREAT_RIB.get().of(1));
                    s.put(1, 1, 5, Props.GREAT_RIB.get().of(0));
                    s.put(-2, 1, 3, Props.NEST.get().of(0));
                    s.on(2, 0, Props.GREAT_RIB.get().of(2));
                    s.on(-2, 1, Dress.twigs(s));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Quiet.near("sleeper", "prize", 5))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.BEASTS, 4, "owl_roost", "The Owl Roost").verb(Verb.SNEAK).curio().rank(2).lands(Group.FOREST, Group.DARK, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A hollow trunk as wide as a doorway, with owls asleep on every ledge inside it.")
                .learned("Up the inside a few rungs at a time, holding still whenever one shifted. They had eaten something that was carrying gold.")
                .reach(6).loot("pellets").icon(Props.NEST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    BlockState log = m.log();
                    var owl = Life.type("hollow_owl", EntityTypes.PARROT);
                    for (int x = -1; x <= 1; x++)
                        for (int z = 1; z <= 3; z++) {
                            if (x == 0 && z == 2) continue;
                            int h = 7 - ((x + z) & 1);
                            for (int k = 0; k <= h; k++) {
                                if (x == 0 && z == 1 && k <= 1) continue;      // the way in
                                s.up(x, z, k, log);
                            }
                        }
                    for (int k = 0; k <= 5; k++) s.up(0, 2, k, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
                    s.partOn("prize", 0, 2, 6, Props.NEST.get().of(1));
                    s.partOn("cache", 0, 2, 6);
                    s.partOn("sleeper", 0, 2, 3);
                    s.spawnUp(owl, 1, 2, 8, ASLEEP, "owl").spawnUp(owl, -1, 3, 8, ASLEEP, "owl").spawnUp(owl, 0, 3, 8, ASLEEP, "owl");
                    Dress.crown(s, 0, 3, 8, 2, leaf(Blocks.OAK_LEAVES));
                    s.on(1, 0, Props.ROOT_LOOP.get().of(1));
                    s.on(-1, 0, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Quiet.near("sleeper", "prize", 4).voice(SoundEvents.PARROT_AMBIENT))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.BEASTS, 5, "stags_clearing", "The Stag's Clearing").verb(Verb.SNEAK).curio().rank(2).lands(Group.FOREST, Group.SNOW, Group.DARK).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A clearing trampled flat, with shed antlers lying about and a stag standing at the far edge.")
                .learned("Walked across, slowly, looking at the ground. It let you take one tine and watched you leave.")
                .reach(8).loot("antler").icon(Props.GREAT_RIB)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    for (int x = -5; x <= 5; x++)
                        for (int z = -5; z <= 5; z++)
                            if (x * x + z * z <= 26 && s.rnd.nextInt(3) != 0) s.floor(x, z, s.rnd.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
                    s.partOn("sleeper", 0, 6, 0);
                    s.spawn(Life.type("frost_stag", EntityTypes.GOAT), 0, 6, Scene.Spawn.NO_AI, "stag");
                    s.partOn("prize", 0, 1, 0, Props.GREAT_RIB.get().of(0));
                    s.partOn("cache", 0, 1, 0);
                    s.on(-3, -1, Props.GREAT_RIB.get().of(2));
                    s.on(2, -3, Props.GREAT_RIB.get().of(1));
                    s.on(3, 2, Dress.twigs(s));
                    s.on(-2, 3, Dress.litter(s));
                    Dress.skirt(s, 7, m);
                })
                .script(() -> Quiet.near("sleeper", "prize", 8).voice(SoundEvents.HORSE_BREATHE))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.BEASTS, 6, "great_skep", "The Great Skep").verb(Verb.SNEAK).find().rank(2).lands(Group.PLAINS, Group.FOREST).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A woven straw hive as tall as a hut, its door waxed shut, with a bell on a post beside it.")
                .learned("Ring first. The bees unsealed the door themselves, and let you take one jar.")
                .reach(8).slope(1).loot("honey").tell(Tell.HUM).icon(() -> Items.HONEY_BOTTLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState hay = Blocks.HAY_BLOCK.defaultBlockState(), thatch = Blocks.WOOL_SLAB.pick(DyeColor.YELLOW).defaultBlockState();
                    for (int x = -2; x <= 2; x++)
                        for (int z = 2; z <= 6; z++) {
                            int ax = Math.abs(x), az = Math.abs(z - 4);
                            if (Math.max(ax, az) == 2 && ax + az < 4) {
                                if (x == 0 && z == 2) continue;                 // the door
                                s.column(x, z, 0, 1, hay);
                            }
                            if (ax + az <= 2) s.put(x, s.g(0, 4) + 3, z, thatch);
                        }
                    s.put(0, s.g(0, 4) + 4, 4, Blocks.WOOL_STAIRS.pick(DyeColor.YELLOW).defaultBlockState());
                    s.partOn("door", 0, 2, 0, Blocks.HONEYCOMB_BLOCK.defaultBlockState());
                    s.partOn("door", 0, 2, 1, Blocks.HONEYCOMB_BLOCK.defaultBlockState());
                    s.on(0, 5, Blocks.DECORATED_POT.defaultBlockState());
                    s.partOn("prize", 0, 5, 0);
                    s.partOn("cache", 0, 4, 0);
                    s.floor(1, 4, Blocks.HONEY_BLOCK.defaultBlockState());
                    s.column(2, 0, 0, 1, m.fence());
                    s.partOn("bell", 2, 0, 2, Blocks.BELL.defaultBlockState());
                    s.spawnUp(EntityTypes.BEE, 0, 1, 2, 0, "bee").spawnUp(EntityTypes.BEE, -2, 1, 3, 0, "bee");
                    s.on(-3, 1, Blocks.CORNFLOWER.defaultBlockState());
                    s.on(3, 2, Blocks.POPPY.defaultBlockState());
                    s.on(-3, 4, Blocks.DANDELION.defaultBlockState());
                    s.tell(0, 4, 4);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Prize.at("bell").then((c, i) -> {
                    c.sound(c.pos("bell", 0), SoundEvents.BELL_BLOCK, 2.0F, 1.0F);
                    fill(c, "door", AIR);
                    c.sound(c.pos("door", 0), SoundEvents.BEE_LOOP, 1.0F, 1.0F);
                }), Prize.at("prize")))
                .play(t -> t.use("bell", 0).use("prize", 0))
                .add();

        Kind.of(FSet.BEASTS, 7, "boar_wallow", "The Boar Wallow").verb(Verb.SNEAK).curio().rank(3).lands(Group.SWAMP, Group.FOREST, Group.DARK).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A mud hole with a family of boar asleep in it, and something bright half sunk in the middle.")
                .learned("Waded in a step at a time. One ear turned, and that was all. The bright thing came out of the mud with a suck.")
                .reach(7).slope(1).tell(Tell.GLINT).icon(Props.GOLD_LUMP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    var boar = Life.type("slag_boar", EntityTypes.PIG);
                    for (int x = -3; x <= 3; x++)
                        for (int z = 1; z <= 7; z++) {
                            int d2 = x * x + (z - 4) * (z - 4);
                            if (d2 > 10) continue;
                            s.floor(x, z, Blocks.MUD.defaultBlockState());
                            if (d2 > 5 && s.rnd.nextInt(3) == 0) s.on(x, z, Blocks.MUD_BRICK_SLAB.defaultBlockState());
                        }
                    s.partOn("prize", 0, 4, 0, Props.HOARD.get().of(0));
                    s.partOn("cache", 0, 4, 0);
                    s.partOn("sleeper", 0, 4, 0);
                    s.spawn(boar, -1, 3, ASLEEP, "boar").spawn(boar, 1, 5, ASLEEP, "boar").spawn(boar, 1, 3, ASLEEP | Scene.Spawn.BABY, "boar");
                    s.tell(0, 4, 1);
                    s.on(-3, 0, Blocks.FERN.defaultBlockState());
                    s.on(4, 5, Blocks.BROWN_MUSHROOM.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Quiet.near("sleeper", "prize", 4).voice(SoundEvents.PIG_AMBIENT_BABY.value()))
                .play(t -> t.use("prize", 0))
                .add();

        Kind.of(FSet.BEASTS, 8, "sleeping_hill", "The Sleeping Hill").verb(Verb.SNEAK).find().rank(4).lands(Group.PLAINS, Group.DARK, Group.FOREST).anchor(Anchor.FLAT).made(Made.ALIVE)
                .see("A low green mound that rises and falls, very slowly, with flowers on its back that grow nowhere else.")
                .learned("Climbed softly. It breathed the whole time and never woke. You took one flower, and its seed.")
                .reach(11).slope(1).loot("seeds").icon(() -> Items.TORCHFLOWER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    net.minecraft.world.level.block.Block[] rare = {Blocks.TORCHFLOWER, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.FERN, Blocks.SHORT_GRASS, Blocks.AZURE_BLUET};
                    for (int x = -5; x <= 5; x++)
                        for (int z = 1; z <= 11; z++) {
                            double d = Math.sqrt(x * x + (z - 6) * (z - 6));
                            int h = d <= 1.5 ? 3 : d <= 3.2 ? 2 : d <= 4.8 ? 1 : 0;
                            if (h == 0) continue;
                            for (int k = 0; k < h; k++) s.up(x, z, k, k == h - 1 ? (((x + z) & 3) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState()) : Blocks.COARSE_DIRT.defaultBlockState());
                            if (!(x == 0 && z == 6) && s.rnd.nextInt(3) == 0) s.up(x, z, h, rare[s.rnd.nextInt(rare.length)].defaultBlockState());
                        }
                    s.partOn("sleeper", 0, 6, 1);
                    s.partOn("prize", 0, 6, 3, Blocks.TORCHFLOWER.defaultBlockState());
                    s.partOn("cache", 0, 6, 3);
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Quiet.near("sleeper", "prize", 7).voice(SoundEvents.HORSE_BREATHE))
                .play(t -> t.use("prize", 0))
                .add();
    }
}
