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

/** Dry Things (pour): what a little water, or a little heat, brings back. */
final class Dry {
    private Dry() {}

    private static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};

    /** A few birds come down, the way they do to water. */
    static void birds(Ctx c, int n) {
        for (int i = 0; i < n; i++) {
            var e = net.minecraft.world.entity.EntityTypes.PARROT.create(c.level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (e == null) continue;
            var at = c.site.ground();
            e.snapTo(at.getX() + 0.5 + c.level.getRandom().nextInt(5) - 2, at.getY() + 4 + i, at.getZ() + 0.5 + c.level.getRandom().nextInt(5) - 2, c.level.getRandom().nextFloat() * 360, 0);
            c.level.addFreshEntity(e);
        }
    }

    private static void fill(Ctx c, String part, BlockState with) {
        for (int i = 0; i < c.count(part); i++) c.set(part, i, with);
    }

    /** The Glass Pipes: water poured into the right pipe makes it sing; any other only gurgles. */
    private static final class Pipes implements Script {
        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (!part.equals("pipe") || !Hands.water(held)) return false;
            if (i == Pick.right(c.seed(), c.count("pipe"))) {
                c.complete(p);
                return true;
            }
            c.sound(c.pos("pipe", i), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.8F, 0.6F);
            c.puff(c.pos("pipe", i).above(), net.minecraft.core.particles.ParticleTypes.SPLASH, 6, 0.2, 0.02);
            return true;
        }
    }

    static void init() {
        Kind.of(FSet.DRY, 1, "dry_basin", "The Dry Basin").verb(Verb.POUR).curio().rank(0).lands(Group.PLAINS, Group.DESERT, Group.BADLANDS, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A stone basin on a block of rock, bone dry, with dead brush round it.")
                .learned("Filled, it brought the birds down first. Then something had left a thing in the bottom.")
                .reach(6).icon(Props.STONE_BASIN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.stone());
                    s.partOn("basin", 0, 0, 1, Props.STONE_BASIN.get().of(0));
                    s.on(1, 0, m.slab());
                    s.partOn("cache", 0, 1, 0);
                    s.on(-2, 1, Blocks.DEAD_BUSH.defaultBlockState());
                    s.on(2, -2, Blocks.DEAD_BUSH.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Pour.water("basin", 2))
                .after(c -> birds(c, 3))
                .play(t -> t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("basin", 0).hold(net.minecraft.world.item.Items.WATER_BUCKET).use("basin", 0))
                .add();

        Kind.of(FSet.DRY, 2, "wilted_giant", "The Wilted Giant").verb(Verb.POUR).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.ALIVE)
                .see("A flower as tall as you are, drooping over cracked ground.")
                .learned("Three buckets and it stood up and opened. It dropped a seed for the trouble.")
                .reach(5).loot("seeds").icon(() -> net.minecraft.world.item.Items.FLOWERING_AZALEA)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.column(0, 0, 0, 1, Blocks.BAMBOO_FENCE.defaultBlockState());
                    s.partOn("droop", 1, 0, 1, Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                    s.partOn("head", 0, 0, 2);
                    for (int d = 0; d < 4; d++) s.floor(DX[d], DZ[d], Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("bed", 0, s.g(0, 1), 1);
                    s.partOn("cache", -1, 0, 0);
                    s.on(-1, 1, Blocks.DEAD_BUSH.defaultBlockState());
                    s.on(1, -1, Props.LOOSE_STONE.get().of(1));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Pour.water("bed", 3))
                .after(c -> {
                    c.set("droop", 0, Blocks.AIR.defaultBlockState());
                    c.set("head", 0, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                })
                .play(t -> { for (int i = 0; i < 3; i++) t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("bed", 0); })
                .add();

        Kind.of(FSet.DRY, 3, "float_box", "The Float Box").verb(Verb.POUR).curio().rank(1).lands(Group.DESERT, Group.BADLANDS, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A square stone shaft under a grate, with a wrapped box at the bottom where you can see it and not reach it.")
                .learned("Filled, the shaft floated the box up to your hand.")
                .reach(5).icon(Props.BUNDLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = 1; x <= 3; x++)
                        for (int z = -1; z <= 1; z++) {
                            boolean shaft = x == 2 && z == 0;
                            for (int dy = -3; dy <= 0; dy++) s.put(x, dy, z, shaft ? Blocks.AIR.defaultBlockState() : (dy + x + z) % 2 == 0 ? m.stone() : m.mossy());
                            if (!shaft) s.on(x, z, x != 2 && z != 0 ? m.slab() : m.wall());
                        }
                    s.put(2, -4, 0, m.stone());
                    s.put(2, -3, 0, Props.BUNDLE.get().of(2));
                    s.part("bottom", 2, -3, 0);
                    for (int dy = -2; dy <= 0; dy++) s.part("shaft", 2, dy, 0);
                    s.partOn("rim", 2, 1, 0);
                    // a grate over the mouth: you can see down it and can't fall in
                    s.put(2, 1, 0, Blocks.IRON_BARS.defaultBlockState());
                    s.part("top", 2, 1, 0);
                    s.part("cache", 2, 1, 0);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Pour.water("rim", 3))
                .after(c -> {
                    c.set("bottom", 0, Blocks.WATER.defaultBlockState());
                    fill(c, "shaft", Blocks.WATER.defaultBlockState());
                    c.set("top", 0, Props.BUNDLE.get().of(2));
                })
                .play(t -> { for (int i = 0; i < 3; i++) t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("rim", 0); })
                .add();

        Kind.of(FSet.DRY, 4, "channel_garden", "The Channel Garden").verb(Verb.POUR).find().rank(1).lands(Group.DESERT, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.FALLEN)
                .see("Stone channels through a dead garden, with a turning gate at each bed and a dry cistern at the top.")
                .learned("Gates set to their marks, then water at the top. It reached every bed, and the garden came back inside a minute.")
                .reach(8).slope(1).persists().icon(Props.RING_POST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(7);
                    int[] t = Turn.targets(s.site.seed(), 3);
                    for (int z = -5; z <= 5; z++) {
                        s.put(2, 0, z, Blocks.AIR.defaultBlockState());
                        s.put(2, -1, z, m.stone());
                        s.part("channel", 2, 0, z);
                    }
                    s.on(2, -6, m.stone());
                    s.partOn("source", 2, -6, 1, Props.STONE_BASIN.get().of(0));
                    for (int i = 0; i < 3; i++) {
                        int z = -3 + i * 3;
                        s.partOn("gate", 0, z, 0, Props.RING_POST.get().of(0));
                        s.on(DX[t[i]], z + DZ[t[i]], Props.START_STONE.get().of(1));
                        for (int side = -1; side <= 1; side += 2)
                            for (int bx = 0; bx <= 1; bx++) {
                                int x = side > 0 ? 4 + bx : -3 + bx;
                                s.floor(x, z, Blocks.COARSE_DIRT.defaultBlockState());
                                s.part("bed", x, s.g(x, z), z);
                                s.partOn("growth", x, z, 0, Blocks.DEAD_BUSH.defaultBlockState());
                            }
                    }
                    s.partOn("cache", 2, 6, 0, Props.FOOTING.get().of(0));
                    Dress.skirt(s, 7, m);
                })
                .script(() -> Steps.of(Turn.dials("gate"), Pour.water("source", 2)))
                .after(c -> {
                    fill(c, "channel", Blocks.WATER.defaultBlockState());
                    fill(c, "bed", Blocks.GRASS_BLOCK.defaultBlockState());
                    net.minecraft.world.level.block.Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.SHORT_GRASS, Blocks.AZURE_BLUET};
                    for (int i = 0; i < c.count("growth"); i++) c.set("growth", i, fl[i % fl.length].defaultBlockState());
                })
                .play(t -> { int[] w = Turn.targets(t.seed(), 3); for (int i = 0; i < 3; i++) for (int k = 0; k < w[i]; k++) t.use("gate", i);
                    for (int i = 0; i < 2; i++) t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("source", 0); })
                .add();

        Kind.of(FSet.DRY, 5, "stopped_spring", "The Stopped Spring").verb(Verb.POUR).curio().rank(2).lands(Group.SNOW, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A steaming crack in the rock, stopped with a slab, beside an empty hollow.")
                .learned("With the slab levered off, the hollow filled with hot water. It takes the cold out of you.")
                .reach(6).slope(1).persists().tell(Tell.STEAM).icon(Props.CAP_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.partOn("slab", 0, 0, 0, Props.CAP_STONE.get().of(0));
                    s.tell(0, 0, 1);
                    for (int x = -2; x <= 2; x++)
                        for (int z = 2; z <= 5; z++) {
                            boolean rim = Math.abs(x) == 2 || z == 2 || z == 5;
                            s.put(x, -1, z, m.stone());
                            if (rim) {
                                s.put(x, 0, z, (x + z) % 2 == 0 ? m.stone() : m.mossy());
                                if ((x + z) % 3 == 0) s.on(x, z, m.slab());
                            } else {
                                s.put(x, 0, z, Blocks.AIR.defaultBlockState());
                                s.part("pool", x, 0, z);
                            }
                        }
                    s.floor(0, 1, Blocks.GRAVEL.defaultBlockState());
                    s.partOn("cache", 1, 0, 0);
                    s.on(-3, 3, Blocks.SNOW.defaultBlockState());
                    s.on(3, 4, Blocks.SNOW.defaultBlockState());
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Prize.at("slab").then((c, i) -> c.set("slab", 0, c.at("slab", 0).setValue(dev.awaken.finds.kit.PropBlock.STATE, 1))))
                .after(c -> {
                    fill(c, "pool", Blocks.WATER.defaultBlockState());
                    for (var p : c.players(8)) {
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 600, 0));
                        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 100, 0));
                    }
                })
                .play(t -> t.use("slab", 0))
                .add();

        Kind.of(FSet.DRY, 6, "thirsty_stone", "The Thirsty Stone").verb(Verb.POUR).curio().rank(2).lands(Group.BADLANDS, Group.DESERT, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A cracked boulder with a cup worn in its top.")
                .learned("Water in the cup ran down the crack and showed carving you can only see wet. It points somewhere.")
                .reach(5).lead().icon(Props.BOULDER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, Props.BOULDER.get().front(2, Direction.SOUTH));
                    s.partOn("cup", 0, 0, 1, Props.STONE_BOWL.get().of(0));
                    s.partOn("face", 0, 1, 0, Props.LOOSE_STONE.get().of(0));
                    s.partOn("cache", 1, 1, 0);
                    s.on(-1, 0, Props.LOOSE_STONE.get().of(1));
                    Dress.scatter(s, 0, 0, 3, 4, () -> Dress.pebbles(s));
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Pour.water("cup", 1))
                .after(c -> c.set("face", 0, c.turned(Props.MILESTONE.get().front(2, Direction.SOUTH))))
                .play(t -> t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("cup", 0))
                .add();

        Kind.of(FSet.DRY, 7, "glass_pipes", "The Glass Pipes").verb(Verb.POUR).find().rank(3).lands(Group.DESERT, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A stand of tubes of fused sand, open at the top. The ground is damp at the foot of one of them.")
                .learned("Water into the damp one and it sang, and the rest answered in turn.")
                .reach(6).icon(Props.GLASS_PIPE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[][] at = {{-2, 0}, {-1, 2}, {1, 1}, {2, -1}, {0, -2}};
                    int right = Pick.right(s.site.seed(), 5);
                    for (int i = 0; i < 5; i++) {
                        int h = 1 + (i * 2 + 1) % 3;
                        for (int k = 0; k < h; k++) s.up(at[i][0], at[i][1], k, Props.GLASS_PIPE.get().of(0));
                        s.partOn("pipe", at[i][0], at[i][1], h - 1);
                        if (i == right) {
                            s.floor(at[i][0] + 1, at[i][1], Blocks.MUD.defaultBlockState());
                            s.floor(at[i][0], at[i][1] + 1, Blocks.MUD.defaultBlockState());
                        }
                    }
                    s.partOn("cache", 0, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(Pipes::new)
                .after(c -> { for (int i = 0; i < c.count("pipe"); i++) c.level.playSound(null, c.pos("pipe", i), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.5F, 0.6F + 0.15F * i); })
                .play(t -> t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("pipe", Pick.right(t.seed(), 5)))
                .add();

        Kind.of(FSet.DRY, 8, "frozen_fall", "The Frozen Fall").verb(Verb.POUR).find().rank(4).lands(Group.SNOW, Group.MOUNTAIN).anchor(Anchor.CLIFF).made(Made.NATURAL)
                .see("A waterfall frozen solid down a cliff, with a shadow behind it.")
                .learned("Heat at its foot and the whole fall let go in one piece. There was a cave behind it all along.")
                .reach(8).tell(Tell.DRIP).icon(() -> net.minecraft.world.item.Items.PACKED_ICE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    for (int x = -1; x <= 1; x++)
                        for (int dy = 1; dy <= 9; dy++) {
                            if (Math.abs(x) == 1 && dy > 7) continue;
                            s.put(x, dy, 1, (dy + x) % 3 == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
                            s.part(x == 0 && dy == 1 ? "foot" : "ice", x, dy, 1);
                        }
                    for (int z = 2; z <= 5; z++) {
                        s.put(0, 0, z, Blocks.GRAVEL.defaultBlockState());
                        s.air(0, 1, z);
                        s.air(0, 2, z);
                    }
                    s.put(0, 1, 5, Blocks.DECORATED_POT.defaultBlockState());
                    s.part("cache", 0, 2, 5);
                    s.on(-2, 0, Blocks.SNOW.defaultBlockState());
                    s.on(2, -1, Props.LOOSE_STONE.get().of(0));
                    s.on(1, -1, Blocks.SNOW.defaultBlockState());
                    s.tell(0, 0, 1);
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Pour.heat("foot", 3))
                .after(c -> {
                    fill(c, "ice", Blocks.AIR.defaultBlockState());
                    fill(c, "foot", Blocks.AIR.defaultBlockState());
                    c.sound(c.site.ground().above(3), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
                })
                .play(t -> { t.hold(net.minecraft.world.item.Items.LAVA_BUCKET); for (int i = 0; i < 3; i++) t.use("foot", 0); })
                .add();
    }
}
