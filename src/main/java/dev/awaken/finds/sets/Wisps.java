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

/** Wisps (follow): things that move off when you come near and wait when you fall behind. */
final class Wisps {
    private Wisps() {}

    private static final BlockState LEAF = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    /** A wandering way from the heart over dry ground: {@code steps} legs of {@code len} blocks. Returns the waypoints. */
    static int[][] wander(Scene s, int steps, double len, double wobble) {
        int[][] at = new int[steps + 1][2];
        double a = s.rnd.nextDouble() * 6.283;
        int x = 0, z = 0;
        for (int i = 1; i <= steps; i++) {
            a += (s.rnd.nextDouble() - 0.5) * wobble;
            int nx = x + (int) Math.round(Math.cos(a) * len), nz = z + (int) Math.round(Math.sin(a) * len);
            for (int t = 0; t < 6 && s.depth(nx, nz) > 0; t++) {
                a += 1.0;
                nx = x + (int) Math.round(Math.cos(a) * len);
                nz = z + (int) Math.round(Math.sin(a) * len);
            }
            x = nx;
            z = nz;
            at[i][0] = x;
            at[i][1] = z;
        }
        return at;
    }

    static void init() {
        Kind.of(FSet.WISPS, 1, "marsh_light", "The Marsh Light").verb(Verb.FOLLOW).curio().rank(0).lands(Group.SWAMP, Group.PLAINS, Group.FOREST, Group.DARK).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("A small light over the ground after dusk, which moves off when you come near.")
                .learned("It waits when you fall behind and takes the dry way. Where it stopped, something was buried.")
                .reach(30).slope(4).tell(Tell.FIREFLIES).icon(() -> net.minecraft.world.item.Items.GLOWSTONE_DUST)
                .scene(s -> {
                    int[][] w = wander(s, 6, 4.5, 1.4);
                    for (int[] p : w) s.partOn("way", p[0], p[1], 0);
                    int x = w[6][0], z = w[6][1];
                    s.floor(x, z, Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("spot", x, s.g(x, z), z);
                    s.part("cache", x, s.g(x, z) + 1, z);
                    Dress.scatter(s, x, z, 2, 3, () -> Dress.pebbles(s));
                })
                .script(() -> Steps.of(When.only(When.NIGHT.or(When.DUSK), Guide.along("way")), Dig.at("spot")))
                .play(t -> { t.hour(18000); for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(140); t.dig("spot", 0); })
                .add();

        Kind.of(FSet.WISPS, 2, "firefly_river", "The Firefly River").verb(Verb.FOLLOW).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.SWAMP).anchor(Anchor.FLAT).made(Made.ALIVE)
                .see("A stream of fireflies flowing between the trees at head height, after dark.")
                .learned("They flow to one place every night. Somebody left a jar there once, and they're used to it.")
                .reach(32).slope(4).loot("fireflies").tell(Tell.FIREFLIES).icon(() -> net.minecraft.world.item.Items.GLASS_BOTTLE)
                .scene(s -> {
                    int[][] w = wander(s, 7, 4.2, 1.0);
                    for (int[] p : w) s.partOn("way", p[0], p[1], 1);
                    int x = w[7][0], z = w[7][1];
                    s.on(x, z, Blocks.FIREFLY_BUSH.defaultBlockState());
                    s.partOn("cache", x + 1, z, 0, Props.BASKET.get().of(0));
                    if (!s.taken(x - 1, s.g(x - 1, z + 1) + 1, z + 1)) s.on(x - 1, z + 1, Blocks.FIREFLY_BUSH.defaultBlockState());
                    Dress.scatter(s, x, z, 2, 3, () -> Dress.litter(s));
                })
                .script(() -> When.only(When.NIGHT, Guide.along("way").as(net.minecraft.core.particles.ParticleTypes.FIREFLY)))
                .play(t -> { t.hour(18000); for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(160); })
                .add();

        Kind.of(FSet.WISPS, 3, "lost_lamb", "The Lost Lamb").verb(Verb.FOLLOW).curio().rank(1).lands(Group.PLAINS, Group.MOUNTAIN, Group.FOREST).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A lamb on its own, bleating, that runs a little way and looks back at you.")
                .learned("It was taking you to its mother, caught in the thorns. Cut loose, they went off together.")
                .reach(30).slope(4).icon(() -> net.minecraft.world.item.Items.WHEAT)
                .scene(s -> {
                    int[][] w = wander(s, 6, 4.5, 1.2);
                    for (int[] p : w) s.partOn("way", p[0], p[1], 0);
                    s.spawn(net.minecraft.world.entity.EntityTypes.SHEEP, 0, 0, Scene.Spawn.BABY, "lamb");
                    int x = w[6][0], z = w[6][1];
                    int[][] ring = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] r : ring) s.partOn("thorn", x + r[0] * 2, z + r[1] * 2, 0, Blocks.DEAD_BUSH.defaultBlockState());
                    s.spawn(net.minecraft.world.entity.EntityTypes.SHEEP, x + 1, z, Scene.Spawn.NO_AI, "ewe");
                    s.partOn("cache", x - 1, z, 0);
                })
                .script(() -> Steps.of(Guide.along("way").creature("lamb"), Dig.at("thorn")))
                .after(c -> { for (var e : c.life("ewe")) if (e instanceof net.minecraft.world.entity.Mob m) m.setNoAi(false); })
                .play(t -> { for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(140); for (int i = 0; i < 4; i++) t.dig("thorn", i); })
                .add();

        Kind.of(FSet.WISPS, 4, "snagged_string", "The Snagged String").verb(Verb.FOLLOW).curio().rank(1).lands(Group.PLAINS, Group.FOREST, Group.SNOW).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A string tied to a peg, running off across the ground with nothing you can see on the end.")
                .learned("Followed from snag to snag, it ended at a kite, and the parcel somebody had tied to it.")
                .reach(28).slope(4).icon(Props.KITE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    int[][] at = {{0, 0}, {0, 6}, {7, 6}, {7, 14}, {14, 14}, {14, 22}};
                    BlockState string = Blocks.TRIPWIRE.defaultBlockState();
                    for (int i = 0; i < at.length; i++) {
                        s.partOn("snag", at[i][0], at[i][1], 0, i == 0 ? Props.ROPE_PEG.get().of(0) : i % 2 == 0 ? m.fence() : Blocks.DEAD_BUSH.defaultBlockState());
                        if (i == 0) continue;
                        int x0 = at[i - 1][0], z0 = at[i - 1][1], dx = Integer.signum(at[i][0] - x0), dz = Integer.signum(at[i][1] - z0);
                        for (int x = x0 + dx, z = z0 + dz; x != at[i][0] || z != at[i][1]; x += dx, z += dz)
                            if (s.depth(x, z) == 0) s.on(x, z, string);
                    }
                    s.on(15, 22, Props.KITE.get().of(1));
                    s.partOn("cache", 14, 23, 0, Props.BUNDLE.get().of(2));
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Read.all("snag").inOrder())
                .play(t -> { for (int i = 0; i < t.count("snag"); i++) t.use("snag", i); })
                .add();

        Kind.of(FSet.WISPS, 5, "bell_bird", "The Bell Bird").verb(Verb.FOLLOW).curio().rank(2).lands(Group.JUNGLE, Group.FOREST, Group.SWAMP).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A bird whose call is one clear bell note. It flies from post to post ahead of you.")
                .learned("Kept in sight for nine perches, it led to its nest. It lines it with whatever shines.")
                .reach(34).slope(4).icon(Props.NEST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    int[][] w = wander(s, 8, 4.0, 1.1);
                    for (int i = 0; i < w.length; i++) {
                        s.column(w[i][0], w[i][1], 0, 1, m.fence());
                        s.partOn("way", w[i][0], w[i][1], 2);
                        if (i == 8) s.up(w[i][0], w[i][1], 2, Props.NEST.get().of(2));
                    }
                    s.spawnUp(net.minecraft.world.entity.EntityTypes.PARROT, 0, 0, 2, 0, "bird");
                    s.partOn("cache", w[8][0], w[8][1] + 1, 0);
                })
                .script(() -> Guide.along("way").creature("bird").note(net.minecraft.sounds.SoundEvents.BELL_BLOCK).speed(0.3))
                .play(t -> { for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(80); })
                .add();

        Kind.of(FSet.WISPS, 6, "root_line", "The Root Line").verb(Verb.FOLLOW).find().rank(2).lands(Group.FOREST, Group.DARK, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.ALIVE)
                .see("A single thick root running along the ground, in and out of the earth, for a long way.")
                .learned("It belongs to one old tree, a long walk off, and the tree had grown round something.")
                .reach(44).slope(5).tell(Tell.GLINT).icon(() -> net.minecraft.world.item.Items.MANGROVE_ROOTS)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    int[][] w = wander(s, 8, 4.8, 0.7);
                    for (int i = 0; i < w.length; i++) {
                        s.partOn("way", w[i][0], w[i][1], 0);
                        if (i == 0) continue;
                        int n = 4;
                        for (int k = 1; k <= n; k++) {
                            int x = w[i - 1][0] + (w[i][0] - w[i - 1][0]) * k / n, z = w[i - 1][1] + (w[i][1] - w[i - 1][1]) * k / n;
                            if (s.depth(x, z) > 0) continue;
                            if ((i + k) % 3 == 0) s.floor(x, z, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
                            else if (!s.taken(x, s.g(x, z) + 1, z) && (x != w[i][0] || z != w[i][1])) s.on(x, z, Props.ROOT_LOOP.get().of(s.rnd.nextInt(4), Direction.from2DDataValue(s.rnd.nextInt(4))));
                        }
                    }
                    int x = w[8][0], z = w[8][1];
                    for (int dx = 0; dx <= 1; dx++)
                        for (int dz = 0; dz <= 1; dz++) s.column(x + 1 + dx, z + 1 + dz, 0, 9, m.log());
                    Dress.crown(s, x + 1, z + 1, 10, 4, LEAF);
                    Dress.crown(s, x + 2, z + 2, 10, 3, LEAF);
                    s.partOn("cache", x, z + 1, 0, Props.HOARD.get().of(0));
                    s.tell(x, z, 1);
                })
                .script(() -> Guide.along("way").as(net.minecraft.core.particles.ParticleTypes.WAX_OFF).speed(0.22))
                .play(t -> { for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(120); })
                .add();

        Kind.of(FSet.WISPS, 7, "ghost_steps", "The Ghost Steps").verb(Verb.FOLLOW).find().rank(3).lands(Group.SNOW, Group.DARK, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("Footprints that appear in front of you at night, one at a time, and nobody making them.")
                .learned("Walked in, they led to a drift with something left inside it. Step off and they stop for the night.")
                .reach(40).slope(5).icon(() -> net.minecraft.world.item.Items.LEATHER_BOOTS)
                .scene(s -> {
                    int[][] w = wander(s, 8, 4.4, 0.8);
                    for (int[] p : w) s.partOn("way", p[0], p[1], 0);
                    int x = w[8][0], z = w[8][1];
                    BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState(), layer = Blocks.SNOW.defaultBlockState();
                    var layers = net.minecraft.world.level.block.SnowLayerBlock.LAYERS;
                    s.on(x + 1, z + 1, Props.BUNDLE.get().of(2));
                    s.partOn("cache", x + 1, z + 1, 0);
                    s.partOn("drift", x + 1, z, 0, layer.setValue(layers, 7));
                    s.partOn("drift", x, z + 1, 0, layer.setValue(layers, 6));
                    s.on(x + 2, z + 1, snow);
                    s.on(x + 1, z + 2, layer.setValue(layers, 5));
                    s.up(x + 2, z + 1, 1, layer.setValue(layers, 3));
                    s.on(x + 2, z, layer.setValue(layers, 4));
                })
                .script(() -> Steps.of(When.only(When.NIGHT, Guide.along("way").as(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE).note(net.minecraft.sounds.SoundEvents.SNOW_STEP).speed(0.12)), Dig.at("drift")))
                .play(t -> { t.hour(18000); for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(200); t.dig("drift", 0).dig("drift", 1); })
                .add();

        Kind.of(FSet.WISPS, 8, "wind_road", "The Wind Road").verb(Verb.FOLLOW).find().rank(4).lands(Group.MOUNTAIN, Group.SNOW, Group.BADLANDS).anchor(Anchor.HILLTOP).made(Made.NATURAL)
                .see("A thin line of seeds on the wind that never comes down, rising away from a bare hilltop toward a needle of rock.")
                .learned("On wings, it marked a rising current all the way to the top of the needle, where nobody walks.")
                .reach(30).slope(4).needsMove().icon(() -> net.minecraft.world.item.Items.FEATHER)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    for (int i = 0; i <= 8; i++) s.partOn("way", 0, i * 3, 1 + i * 3);
                    int z = 26;
                    for (int k = 0; k <= 26; k++) {
                        int r = k < 8 ? 1 : 0;
                        for (int dx = -r; dx <= r; dx++)
                            for (int dz = -r; dz <= r; dz++) {
                                boolean corner = dx != 0 && dz != 0;
                                if (corner && k >= 4) continue;
                                s.up(dx, z + dz, k, corner ? m.wall() : k % 5 == 4 ? m.mossy() : m.stone());
                            }
                        if (k >= 8 && k % 3 == 0) s.up(k % 2 == 0 ? 1 : -1, z, k, m.wall());
                    }
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            if (dx != 0 || dz != 0) s.up(dx, z + dz, 26, m.slab());
                    s.partOn("cache", 0, z, 27, Props.FOOTING.get().of(0));
                })
                .script(() -> Guide.along("way").as(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR).speed(0.3))
                .play(t -> { for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(60); })
                .add();
    }
}
