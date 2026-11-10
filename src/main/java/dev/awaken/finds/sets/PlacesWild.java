package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.Pal;
import dev.awaken.finds.kit.PropBlock;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/** Places of the swamp, the jungle, the snow and the mountain (SPEC §59, docs/places/BRIEFS.md 13-18). */
final class PlacesWild {
    private PlacesWild() {}

    private static BlockState fire(boolean lit) {
        return st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, lit);
    }

    /** A walk two wide between two points at one height, of top slabs, with a post to the bed under every fourth step. */
    private static void walk(Scene s, int x0, int z0, int x1, int z1, int y, BlockState slab, BlockState post) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)), px = Math.abs(x1 - x0) >= Math.abs(z1 - z0) ? 0 : 1, pz = 1 - px;
        for (int i = 0; i <= n; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, n)), z = z0 + Math.round((z1 - z0) * i / (float) Math.max(1, n));
            for (int w = 0; w <= 1; w++) {
                BlockState was = s.get(x + px * w, y, z + pz * w);
                if (was == null || was.isAir()) s.put(x + px * w, y, z + pz * w, top(slab));
            }
            if (i % 4 == 2) for (int k = y - 1; k >= -3; k--) if (s.get(x, k, z) == null || s.get(x, k, z) == WATER) s.put(x, k, z, post);
        }
    }

    static void init() {
        Kind.of(FSet.PLACES, 13, "stilt_market", "The Stilt Market").verb(Verb.CARRY).place().rank(2).lands(Group.SWAMP, Group.JUNGLE, Group.FOREST)
                .anchor(Anchor.FIELD).slope(5).made(Made.STILLED)
                .see("Lit roofs on legs over a dark pool, joined by plank walks: a market, with every trader and buyer turned to stone in the middle of a bargain.")
                .learned("Three baskets were still in the boats. Each went to the stall that sold what was in it, and then the market bell.")
                .reach(34).icon(Props.BASKET)
                .air(Air.of(0x56677A, 46).mote(() -> ParticleTypes.FIREFLY, 0.5F, 6, 0.0F).loop(() -> SoundEvents.FROG_AMBIENT, 80, 0.5F, 1F).loop(() -> SoundEvents.WATER_AMBIENT, 60, 0.4F, 0.8F))
                .scene(s -> {
                    s.level(26, 8).clear(28);
                    Pal p = Pal.MARSH;
                    BlockState post = st(Blocks.MANGROVE_LOG), plank = st(Blocks.MANGROVE_PLANKS), slab = st(Blocks.SPRUCE_SLAB), thatch = st(Blocks.HAY_BLOCK), cap = st(Blocks.WOOL_SLAB.pick(DyeColor.BROWN));
                    pool(s, 0, 0, 18.5, 18.5, 0, 3, st(Blocks.MUD), st(Blocks.CLAY));
                    // the round hall in the middle
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4 + Math.PI / 8;
                        int x = (int) Math.round(Math.cos(a) * 4.2), z = (int) Math.round(Math.sin(a) * 4.2);
                        for (int y = -3; y <= 6; y++) s.put(x, y, z, post);
                    }
                    disc(s, 0, 0, 4.7, 3, plank);
                    cone(s, 0, 0, 5, 7, thatch, cap);
                    s.put(0, 6, 0, lantern(true, false));
                    s.put(2, 4, -2, st(Blocks.BARREL));
                    s.part("cache", 2, 5, -2);
                    s.put(-2, 4, -2, st(Blocks.LECTERN));
                    s.put(0, 4, -3, st(Blocks.SPRUCE_FENCE));
                    s.put(0, 5, -3, st(Blocks.BELL));
                    s.part("bell", 0, 5, -3);
                    stilled(s, -1, 4, -1, 1, Direction.EAST);
                    // five stalls round it, each on four legs under its own roof, each selling one thing
                    int[][] at = {{-12, -6}, {12, -6}, {-11, 8}, {11, 8}, {0, -14}};
                    DyeColor[] awning = {DyeColor.RED, DyeColor.CYAN, DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.YELLOW};
                    for (int i = 0; i < 5; i++) {
                        int cx = at[i][0], cz = at[i][1];
                        for (int x = cx - 2; x <= cx + 2; x += 4) for (int z = cz - 2; z <= cz + 2; z += 4) for (int y = -3; y <= 6; y++) s.put(x, y, z, post);
                        fill(s, cx - 2, 3, cz - 2, cx + 2, 3, cz + 2, plank);
                        gable(s, cx - 2, cz - 2, cx + 2, cz + 2, 6, p, i % 2 == 0, 1, 0);
                        s.put(cx, 7, cz, lantern(true, false));
                        Direction in = Math.abs(cx) > Math.abs(cz) ? (cx > 0 ? Direction.WEST : Direction.EAST) : Direction.SOUTH;
                        int fx = cx + in.getStepX() * 2, fz = cz + in.getStepZ() * 2, sx = in.getStepZ(), sz = in.getStepX();
                        for (int k = -1; k <= 1; k++) s.put(fx + sx * k, 4, fz + sz * k, k == 0 ? AIR : top(st(Blocks.SPRUCE_SLAB)));
                        s.put(fx + sx, 5, fz + sz, st(Blocks.CARPET.pick(awning[i])));
                        stilled(s, cx, 4, cz, i & 3, in);
                        stilled(s, cx + in.getStepX() * 3, 4, cz + in.getStepZ() * 3, (i + 2) & 3, in.getOpposite());
                        BlockState goods = i == 0 ? Props.OLD_ROD.get().of(0) : i == 1 ? Props.BASKET.get().of(0) : i == 2 ? st(Blocks.DECORATED_POT) : i == 3 ? st(Blocks.WOOL.pick(DyeColor.MAGENTA)) : lantern(false, false);
                        s.put(cx - sx, 4, cz - sz, goods);
                        if (i < 3) s.part("socket", fx - sx, 5, fz - sz);
                        walk(s, cx + in.getStepX() * 3, cz + in.getStepZ() * 3, in.getStepX() == 0 ? 0 : cx > 0 ? 5 : -5, in.getStepX() == 0 ? -5 : cz + (cz > 0 ? -4 : 3), 3, slab, post);
                    }
                    walk(s, -1, 5, -1, 19, 3, slab, post);                // the jetty south, and steps down to the shore
                    walk(s, -1, -17, -1, -19, 3, slab, post);
                    steps(s, -1, 1, 22, 0, 3, 2, st(Blocks.SPRUCE_STAIRS), plank);
                    steps(s, -1, 1, -22, 2, 3, 2, st(Blocks.SPRUCE_STAIRS), plank);
                    // boats under the walks, a basket in each
                    int[][] boat = {{5, 10}, {-6, -11}, {7, -12}};
                    for (int i = 0; i < 3; i++) {
                        for (int k = -1; k <= 1; k++) s.put(boat[i][0] + k, 0, boat[i][1], k == 0 ? st(Blocks.SPRUCE_PLANKS) : stair(st(Blocks.SPRUCE_STAIRS), k < 0 ? Direction.EAST : Direction.WEST));
                        s.put(boat[i][0], 1, boat[i][1], Props.BASKET.get().of(1));
                        s.part("loose", boat[i][0], 1, boat[i][1]);
                    }
                    for (int k = -1; k <= 1; k++) s.put(-9 + k, -2, 2, st(Blocks.SPRUCE_SLAB));
                    for (int i = 0; i < 22; i++) { int x = s.rnd.nextInt(35) - 17, z = s.rnd.nextInt(35) - 17; if (s.get(x, 0, z) == WATER && !s.taken(x, 1, z)) s.put(x, 1, z, st(Blocks.LILY_PAD)); }
                    for (int i = 0; i < 14; i++) { double a = i * Math.PI / 7 + 0.2; int x = (int) Math.round(Math.cos(a) * 20), z = (int) Math.round(Math.sin(a) * 20); if (!s.taken(x, 1, z) && Math.abs(x) > 3) s.put(x, 1, z, st(i % 3 == 0 ? Blocks.MANGROVE_ROOTS : i % 3 == 1 ? Blocks.FERN : Blocks.SHORT_GRASS)); }
                    s.spawn(EntityTypes.FROG, 15, 16, 0, "frog").spawn(EntityTypes.FROG, -16, -15, 0, "frog").spawnUp(EntityTypes.CAT, 0, 0, 13, Scene.Spawn.STAY, "cat");
                    wild(s, 22, 33, 7);
                    s.cam(0, 12, 46, 180, 8).cam(18, 24, 18, 135, 40).cam(-1, 4, 15, 180, 4).cam(1, 4, 2, 160, 2);
                })
                .script(() -> Steps.of(Sockets.need("socket", st -> st.is(Props.BASKET.get().asItem()), i -> Props.BASKET.get().of(1)).carry("loose"),
                        Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), SoundEvents.BELL_BLOCK, 2.5F, 1.0F))))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i); for (int i = 0; i < 3; i++) t.hold(Props.BASKET.get()).use("socket", i); t.use("bell", 0); })
                .add();

        Kind.of(FSet.PLACES, 14, "hanging_village", "The Hanging Village").verb(Verb.PULL).place().rank(2).lands(Group.JUNGLE, Group.DARK)
                .anchor(Anchor.FIELD).slope(6).made(Made.STILLED)
                .see("Four giant trees with a thatched ring round each, high up, and rope bridges between them. There are people up there, not moving.")
                .learned("The basket lift still ran. The weaver had finished the drum skin and never carried it over; you did, and the drum spoke.")
                .reach(32).icon(() -> Items.JUNGLE_LOG)
                .air(Air.of(0x6E8A4E, 60).mote(() -> ParticleTypes.SPORE_BLOSSOM_AIR, 0.9F, 14, -0.01F).loop(() -> SoundEvents.PARROT_AMBIENT, 150, 0.4F, 1F))
                .scene(s -> {
                    s.level(24, 8).clear(27);
                    BlockState log = st(Blocks.JUNGLE_LOG), plank = st(Blocks.JUNGLE_PLANKS), deck = top(st(Blocks.JUNGLE_SLAB)), rail = st(Blocks.BAMBOO_FENCE), thatch = st(Blocks.HAY_BLOCK), cap = st(Blocks.BAMBOO_MOSAIC_SLAB);
                    int[][] tree = {{-10, 10}, {10, 10}, {-10, -10}, {10, -10}};
                    for (int i = 0; i < 4; i++) {
                        int tx = tree[i][0], tz = tree[i][1], top = i == 3 ? 21 : 14;
                        cone(s, tx, tz, 6, top + 4, thatch, cap);
                        if (i == 3) cone(s, tx, tz, 5, 18, thatch, cap);
                        giant(s, tx, tz, 1, 30, 2, log, leaf(Blocks.JUNGLE_LEAVES), 7.5);
                        for (int lvl = 14; lvl <= top; lvl += 7)
                            for (int x = -6; x <= 6; x++)
                                for (int z = -6; z <= 6; z++) {
                                    double d = Math.sqrt(x * x + z * z);
                                    if (d > 2.6 && d <= 5.6 && s.get(tx + x, lvl, tz + z) == null) s.put(tx + x, lvl, tz + z, deck);
                                    if (d > 4.8 && d <= 5.6 && Math.abs(x) > 1 && Math.abs(z) > 1 && lvl == 14) s.put(tx + x, 15, tz + z, rail);
                                }
                        for (int d = 0; d < 4; d++) { int px = tx + (d < 2 ? 3 : -3), pz = tz + (d % 2 == 0 ? 3 : -3); for (int y = 15; y <= 17; y++) s.put(px, y, pz, rail); s.put(px, 13, pz, lantern(true, false)); }
                        stilled(s, tx + 4, 15, tz + (i % 2 == 0 ? 0 : 1), i, i % 2 == 0 ? Direction.WEST : Direction.EAST);
                        vines(s, tx - 4, 2, tz - 4, tx + 4, 13, tz + 4, 22);
                    }
                    // what each ring is for
                    s.put(-10, 15, 14, thatch);
                    s.put(-9, 15, 14, thatch);
                    s.put(13, 15, 10, st(Blocks.SMOOTH_STONE_SLAB));
                    s.put(13, 16, 10, fire(true));
                    s.put(-14, 15, -10, st(Blocks.LOOM));
                    s.put(-13, 15, -13, Props.BUNDLE.get().of(0));
                    s.part("loose", -13, 15, -13);
                    for (int x = 6; x <= 8; x++) for (int z = -10; z <= -8; z++) if (s.get(x, 15, z) == null) s.put(x, 15, z, AIR);
                    for (int y = 15; y <= 21; y++) s.put(10, y, -7, ladder(Direction.SOUTH));
                    s.put(13, 22, -10, st(Blocks.JUNGLE_FENCE));
                    s.part("drum", 13, 23, -10);
                    s.part("cache", 12, 22, -11);
                    stilled(s, 14, 22, -9, 2, Direction.WEST);
                    // bridges along the square's sides and in to a hanging floor in the middle
                    for (int k = -5; k <= 5; k++)
                        for (int w = -1; w <= 1; w++) {
                            for (int side = -10; side <= 10; side += 20) {
                                if (s.get(k, 14, side + w) == null) s.put(k, 14, side + w, deck);
                                if (s.get(side + w, 14, k) == null) s.put(side + w, 14, k, deck);
                                if (w != 0 && Math.abs(k) <= 3) { s.put(k, 15, side + w, rail); s.put(side + w, 15, k, rail); }
                            }
                        }
                    for (int k = 3; k <= 8; k++) for (int w = -1; w <= 1; w++) { s.put(w, 14, k, deck); s.put(w, 14, -k, deck); if (w != 0 && k < 8) { s.put(w, 15, k, rail); s.put(w, 15, -k, rail); } }
                    disc(s, 0, 0, 3.2, 14, deck);
                    s.put(0, 15, 0, Props.FIRE_BASKET.get().of(1));
                    for (int d = 0; d < 4; d++) for (int y = 15; y <= 19; y++) s.put(d < 2 ? 2 : -2, y, d % 2 == 0 ? 2 : -2, st(Blocks.IRON_CHAIN));
                    // the two ways up: a rope ladder at the south-west tree, the basket lift at the north-east
                    for (int y = 1; y <= 14; y++) s.put(-10, y, 13, ladder(Direction.SOUTH));
                    limb(s, 13, 17, -10, 17, 17, -10, axis(log, Direction.Axis.X));
                    for (int y = 3; y <= 16; y++) s.put(17, y, -10, Props.ROPE_LENGTH.get().of(0));
                    s.part("rope", 17, 3, -10);
                    for (int y = 1; y <= 15; y += 2) s.part("load", 16, y, -10);
                    s.put(16, 1, -10, Props.BASKET.get().of(0));
                    s.put(16, 15, -10, AIR);
                    for (int i = 0; i < 16; i++) { int x = s.rnd.nextInt(41) - 20, z = s.rnd.nextInt(41) - 20; if (!s.taken(x, 1, z) && s.get(x, 0, z) == null) s.put(x, 1, z, st(i % 4 == 0 ? Blocks.MELON : i % 4 == 1 ? Blocks.FERN : Blocks.SHORT_GRASS)); }
                    s.spawnUp(EntityTypes.PARROT, -6, 0, 16, 0, "parrot").spawnUp(EntityTypes.PARROT, 6, 2, 16, 0, "parrot").spawn(EntityTypes.OCELOT, 4, 16, 0, "ocelot");
                    wild(s, 20, 31, 7);
                    s.cam(0, 16, 48, 180, -4).cam(22, 40, 22, 135, 42).cam(-10, 2, 18, 180, -32).cam(0, 15, 1, -135, -2);
                })
                .script(() -> Steps.of(Haul.on("rope", "load").trail(Props.ROPE_LENGTH.get().of(0)), Sockets.need("drum", st -> st.is(Props.BUNDLE.get().asItem()), i -> st(Blocks.NOTE_BLOCK)).carry("loose")))
                .after(c -> c.sound(c.pos("drum", 0), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 3.0F, 0.7F))
                .play(t -> { for (int i = 0; i < t.count("load") - 1; i++) t.use("rope", 0); t.use("loose", 0).hold(Props.BUNDLE.get()).use("drum", 0); })
                .add();

        Kind.of(FSet.PLACES, 15, "sunken_temple", "The Sunken Temple").verb(Verb.TURN).place().rank(3).lands(Group.JUNGLE, Group.SWAMP, Group.DARK)
                .anchor(Anchor.FIELD).slope(6).made(Made.FALLEN)
                .see("A stepped stone pyramid half swallowed by roots, one corner fallen into a flooded court, a tree growing out of its top.")
                .learned("Inside, one shaft of light and four mirrors. Turned right, the light reached the disc, and the dais opened.")
                .reach(36).icon(() -> Items.GOLD_BLOCK)
                .air(Air.of(0x7A8A52, 44).mote(() -> ParticleTypes.SPORE_BLOSSOM_AIR, 0.9F, 12, -0.01F).loop(() -> SoundEvents.BEE_LOOP, 90, 0.25F, 0.5F).loop(() -> SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 70, 0.5F, 0.9F))
                .scene(s -> {
                    s.level(28, 8).clear(30);
                    Pal p = Pal.JUNGLE;
                    java.util.function.IntFunction<BlockState> face = h -> h < 60 ? st(Blocks.MOSSY_STONE_BRICKS) : h < 110 ? st(Blocks.CRACKED_STONE_BRICKS) : h < 190 ? st(Blocks.MOSSY_COBBLESTONE) : st(Blocks.COBBLESTONE);
                    BlockState core = st(Blocks.COARSE_DIRT), lip = st(Blocks.MOSSY_COBBLESTONE_SLAB);
                    for (int k = 0; k < 5; k++) {
                        int half = 15 - 3 * k;
                        for (int x = -half; x <= half; x++)
                            for (int z = -half; z <= half; z++) {
                                double fallen = Math.sqrt((x - 16) * (x - 16) + (z - 16) * (z - 16)) - (9 + n(s, x, z, 4.0) * 5) + k * 1.5;
                                if (fallen < 0) continue;                  // the south-east corner has gone
                                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                                for (int y = 3 * k + 1; y <= 3 * k + 3; y++) s.put(x, y, z, edge || y == 3 * k + 3 ? face.apply(h(s, x, y, z)) : core);
                                if (edge && k < 4 && h(s, x, k, z) < 150) s.put(x, 3 * k + 4, z, lip);
                            }
                    }
                    pool(s, 17, 17, 6.5, 6.0, 0, 2, st(Blocks.MUD), st(Blocks.CLAY));
                    rubble(s, 13, 13, 6, 24, p);
                    // the great stair up the south face to the door on the third step
                    for (int i = 0; i < 9; i++) for (int x = -2; x <= 2; x++) { s.put(x, 1 + i, 16 - i, stair(st(Blocks.MOSSY_COBBLESTONE_STAIRS), Direction.NORTH)); for (int y = 1; y < 1 + i; y++) s.put(x, y, 16 - i, face.apply(h(s, x, y, 16 - i))); }
                    fill(s, -2, 9, 1, 2, 13, 7, st(Blocks.MOSSY_STONE_BRICKS));
                    air(s, -1, 10, 1, 1, 12, 7);
                    s.put(-2, 12, 7, st(Blocks.CHISELED_STONE_BRICKS));
                    s.put(2, 12, 7, st(Blocks.CHISELED_STONE_BRICKS));
                    // the chamber under the summit, lit by a hole in its roof
                    fill(s, -5, 6, -9, 5, 15, 1, st(Blocks.MOSSY_STONE_BRICKS));
                    air(s, -4, 8, -8, 4, 14, 0);
                    floor(s, -4, -8, 4, 0, 7, st(Blocks.MOSSY_COBBLESTONE), st(Blocks.CHISELED_STONE_BRICKS), 12);
                    air(s, -1, 10, 1, 1, 12, 1);
                    for (int x = -1; x <= 1; x++) { s.put(x, 9, 0, stair(st(Blocks.MOSSY_COBBLESTONE_STAIRS), Direction.SOUTH)); s.put(x, 8, -1, stair(st(Blocks.MOSSY_COBBLESTONE_STAIRS), Direction.SOUTH)); s.put(x, 8, 0, st(Blocks.MOSSY_COBBLESTONE)); }
                    for (int y = 15; y <= 16; y++) s.put(0, y, -4, AIR);
                    s.put(0, 8, -4, st(Blocks.CHISELED_STONE_BRICKS));
                    s.put(0, 9, -4, st(Blocks.GOLD_BLOCK));
                    s.part("dais", 0, 9, -4);
                    s.part("cache", 0, 8, -3);
                    int[] t = Turn.targets(s.site.seed(), 4);
                    int[][] at = {{-3, -4}, {3, -4}, {0, -7}, {0, -1}};
                    for (int i = 0; i < 4; i++) {
                        s.put(at[i][0], 8, at[i][1], st(Blocks.MOSSY_STONE_BRICKS));
                        s.put(at[i][0], 9, at[i][1], Props.MIRROR_DISC.get().of(0));
                        s.part("mirror", at[i][0], 9, at[i][1]);
                        s.put(at[i][0] + DX[t[i]], 7, at[i][1] + DZ[t[i]], st(Blocks.GOLD_BLOCK));
                    }
                    for (int x = -4; x <= 4; x += 8) for (int z = -7; z <= -1; z += 6) stilled(s, x, 8, z, 1, x < 0 ? Direction.EAST : Direction.WEST);
                    stilled(s, 1, 8, -6, 2, Direction.SOUTH);
                    s.put(0, 13, -4, light(13));
                    s.put(0, 10, -4, light(10));
                    s.put(-3, 12, -7, light(6));
                    // the tree on the summit, its roots pouring down the north face
                    giant(s, 3, -2, 16, 12, 1, st(Blocks.JUNGLE_LOG), leaf(Blocks.JUNGLE_LEAVES), 6.5);
                    for (int rx = 2; rx <= 4; rx += 2)
                        for (int k = 4; k >= 0; k--) {
                            int zf = -(15 - 3 * k) - 1;
                            for (int y = 3 * k + 3; y >= 3 * k + 1; y--) s.put(rx, y, zf, st(Blocks.JUNGLE_LOG));
                            if (k > 0) for (int z = zf; z >= zf - 3; z--) s.put(rx, 3 * k, z, axis(st(Blocks.JUNGLE_LOG), Direction.Axis.Z));
                        }
                    for (int rx = 2; rx <= 4; rx += 2) for (int z = -3; z >= -4; z--) s.put(rx, 16, z, axis(st(Blocks.JUNGLE_LOG), Direction.Axis.Z));
                    for (int z = 19; z <= 27; z += 4) for (int x = -4; x <= 4; x += 8) pillar(s, x, z, 1, 3 - ((z / 4) & 1), st(Blocks.MOSSY_STONE_BRICKS), st(Blocks.CHISELED_STONE_BRICKS));
                    path(s, 0, 30, 0, 17, 2, st(Blocks.MOSSY_COBBLESTONE), st(Blocks.COARSE_DIRT));
                    vines(s, -17, 2, -17, 17, 16, 17, 9);
                    moss(s, -16, 1, -16, 16, 16, 16, 18);
                    s.spawn(EntityTypes.FROG, 20, 20, 0, "frog").spawnUp(EntityTypes.PARROT, -6, 6, 12, 0, "parrot");
                    wild(s, 24, 35, 9);
                    s.cam(0, 16, 52, 180, 6).cam(25, 34, 25, 135, 40).cam(0, 10, 8, 180, 0).cam(2, 9, -1, 150, 6);
                })
                .script(() -> Steps.of(Turn.dials("mirror"), Prize.at("dais")))
                .play(t -> { int[] w = Turn.targets(t.seed(), 4); for (int i = 0; i < 4; i++) for (int k = 0; k < w[i]; k++) t.use("mirror", i); t.use("dais", 0); })
                .add();

        Kind.of(FSet.PLACES, 16, "hot_spring_bathhouse", "The Bathhouse at the Hot Spring").verb(Verb.WAIT).place().rank(1).lands(Group.SNOW, Group.MOUNTAIN)
                .anchor(Anchor.FIELD).slope(5).made(Made.BUILT)
                .see("Steam standing over a snowed-in bathhouse and three pools, warm light in its windows, and stone bathers up to their shoulders in the long one.")
                .learned("Wood in the stove, the brass valve turned, and ten slow breaths in the long bath. You left warm and mended.")
                .reach(34).tell(Kind.Tell.STEAM).icon(() -> Items.BUCKET)
                .air(Air.of(0xE3E6E2, 34).mote(() -> ParticleTypes.CLOUD, 0.5F, 4, 0.03F).loop(() -> SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 60, 0.5F, 1F))
                .scene(s -> {
                    s.level(26, 8).clear(29);
                    Pal p = Pal.TIMBER;
                    BlockState stone = st(Blocks.SMOOTH_STONE), calcite = st(Blocks.CALCITE), deck = st(Blocks.SPRUCE_PLANKS);
                    ground(s, 0, 6, 17, h -> h < 105 ? stone : h < 150 ? st(Blocks.ANDESITE) : h < 180 ? calcite : null);
                    fill(s, -9, 0, -2, 9, 0, 1, deck);                      // the boarded walk from the door to the water
                    for (int z = 0; z <= 1; z++) for (int x = -3; x <= 3; x += 6) { for (int y = 1; y <= 3; y++) s.put(x, y, z - 1 + z, st(Blocks.SPRUCE_FENCE)); }
                    fill(s, -4, 4, -2, 4, 4, 1, st(Blocks.SPRUCE_SLAB));
                    pool(s, -9, 6, 3.4, 3.4, 0, 2, st(Blocks.SMOOTH_BASALT), st(Blocks.MAGMA_BLOCK));
                    // where the water comes up: white stone basins stepping down a mound, each spilling into the next
                    for (int k = 3; k >= 0; k--) {
                        int bx = -19 + k * 3, bz = -3 + k * 3, top = 5 - k;
                        double r = 2.6 + k * 0.5;
                        for (int x = -4; x <= 4; x++)
                            for (int z = -4; z <= 4; z++) {
                                double d = Math.sqrt(x * x + z * z);
                                if (d > r + 0.4) continue;
                                for (int y = 1; y <= top; y++) s.put(bx + x, y, bz + z, y == top && d < r - 0.8 ? WATER : h(s, bx + x, y, bz + z) < 60 ? st(Blocks.DIORITE) : calcite);
                            }
                    }
                    boulder(s, -22, -8, 3.0, st(Blocks.STONE), st(Blocks.ANDESITE), calcite);
                    boulder(s, -8, 12, 1.8, st(Blocks.STONE), st(Blocks.ANDESITE), st(Blocks.MOSSY_COBBLESTONE));
                    boulder(s, 8, 9, 1.6, st(Blocks.STONE), st(Blocks.ANDESITE), st(Blocks.MOSSY_COBBLESTONE));
                    // the tea house across the yard, and the chimney that smokes over it all
                    house(s, 12, -10, 19, -3, 0, 3, p, 3, 0);
                    s.put(17, 1, -8, top(st(Blocks.SPRUCE_SLAB)));
                    s.put(17, 2, -8, st(Blocks.POTTED_AZURE_BLUET));
                    s.put(16, 1, -8, st(Blocks.CARPET.pick(DyeColor.RED)));
                    s.put(18, 1, -6, st(Blocks.CARPET.pick(DyeColor.WHITE)));
                    stilled(s, 16, 1, -6, 2, Direction.EAST);
                    for (int y = 1; y <= 9; y++) s.put(7, y, -10, st(y % 3 == 0 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS));
                    s.put(7, 10, -10, st(Blocks.CAMPFIRE).setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, true));
                    for (int x = 10; x <= 16; x += 3) for (int z = 2; z <= 5; z += 3) { s.put(x, 1, z, st(Blocks.SPRUCE_FENCE)); s.put(x, 2, z, st(Blocks.SPRUCE_FENCE)); }
                    fill(s, 9, 3, 1, 17, 3, 6, st(Blocks.SPRUCE_SLAB));   // the roofed bench by the cold plunge
                    for (int x = 11; x <= 15; x++) s.put(x, 1, 2, stair(st(Blocks.SPRUCE_STAIRS), Direction.NORTH));
                    for (int x = -4; x <= 6; x++) for (int z = 3; z <= 7; z++) { s.put(x, 0, z, WATER); s.put(x, -1, z, WATER); s.put(x, -2, z, h(s, x, -2, z) < 128 ? calcite : stone); }
                    for (int x = -5; x <= 7; x++) for (int z = 2; z <= 8; z++) if (x == -5 || x == 7 || z == 2 || z == 8) s.put(x, 0, z, (x + z) % 3 == 0 ? calcite : stone);
                    pool(s, 11, 12, 2.4, 2.4, 0, 2, st(Blocks.STONE), st(Blocks.GRAVEL));
                    s.part("plunge", 11, 1, 12);
                    s.part("bath", 1, 0, 5);
                    for (int[] k : new int[][] {{-2, 4}, {0, 6}, {3, 4}, {5, 6}}) stilled(s, k[0], 0, k[1], k[0] & 3, Direction.SOUTH);
                    for (int x = -12; x <= 14; x++) if (x % 5 != 0) { s.put(x, 1, 16, st(Blocks.BAMBOO_FENCE)); if (x % 2 == 0) s.put(x, 2, 16, st(Blocks.BAMBOO_FENCE)); }
                    // the bathhouse: a changing room, the stove, a tea room
                    house(s, -8, -9, 8, -3, 0, 3, p, 2, 0);
                    for (int z = -8; z <= -4; z++) for (int y = 1; y <= 3; y++) { if (z != -6 || y == 3) s.put(-3, y, z, st(Blocks.SPRUCE_PLANKS)); if (z != -6 || y == 3) s.put(3, y, z, st(Blocks.SPRUCE_PLANKS)); }
                    s.put(-7, 1, -8, Props.BASKET.get().of(0));
                    s.put(-6, 1, -8, Props.BASKET.get().of(1));
                    s.put(-5, 2, -8, st(Blocks.CARPET.pick(DyeColor.LIGHT_BLUE)));
                    s.put(-5, 1, -8, top(st(Blocks.SPRUCE_SLAB)));
                    s.put(0, 1, -8, st(Blocks.FURNACE));
                    s.part("stove", 0, 1, -8);
                    s.put(1, 1, -8, Props.BRASS_RING.get().of(0));
                    s.part("valve", 1, 1, -8);
                    s.put(-1, 1, -8, st(Blocks.SPRUCE_LOG));
                    s.put(-2, 1, -5, st(Blocks.SPRUCE_LOG));
                    s.part("loose", -2, 1, -5);
                    s.put(6, 1, -6, top(st(Blocks.SPRUCE_SLAB)));
                    s.put(6, 2, -6, st(Blocks.POTTED_AZURE_BLUET));
                    s.put(5, 1, -6, st(Blocks.CARPET.pick(DyeColor.WHITE)));
                    s.put(7, 1, -6, st(Blocks.CARPET.pick(DyeColor.BLUE)));
                    s.put(6, 1, -8, st(Blocks.CAULDRON));
                    s.part("cache", 5, 1, -5);
                    stilled(s, 0, 1, 1, 1, Direction.SOUTH);
                    logPile(s, -14, -6, 1, 4, false, st(Blocks.SPRUCE_LOG));
                    for (int[] k : new int[][] {{-11, 1}, {9, -1}, {-7, 14}, {8, 15}, {-3, 19}, {3, 19}}) lamp(s, k[0], 1, k[1], 2, st(Blocks.COBBLESTONE_WALL), false);
                    path(s, 0, 33, 0, 17, 1, st(Blocks.SPRUCE_PLANKS), st(Blocks.GRAVEL));
                    s.tell(-9, 6, 2);
                    s.spawn(EntityTypes.FOX, 14, -8, 0, "fox");
                    wild(s, 20, 33, 12);
                    s.cam(0, 12, 42, 180, 10).cam(20, 24, 24, 140, 38).cam(0, 2, 21, 180, 0).cam(0, 1, -4, 0, -2);
                })
                .script(() -> Steps.of(Sockets.need("stove", st -> st.is(net.minecraft.tags.ItemTags.LOGS) || st.is(net.minecraft.tags.ItemTags.PLANKS),
                                i -> st(Blocks.FURNACE).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true)).carry("loose"),
                        Prize.at("valve").then((c, i) -> c.puff(c.pos("plunge", 0), ParticleTypes.CLOUD, 30, 1.2, 0.03)), When.stay("bath", c -> true, 10)))
                .after(c -> { for (var pl : c.players(24)) pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 900, 1)); })
                .play(t -> t.use("loose", 0).hold(Items.SPRUCE_LOG).use("stove", 0).use("valve", 0).stand("bath", 0).ticks(60))
                .add();

        Kind.of(FSet.PLACES, 17, "ice_hall", "The Ice Hall").verb(Verb.BREAK).place().rank(3).lands(Group.SNOW)
                .anchor(Anchor.FIELD).slope(5).made(Made.STILLED)
                .see("A feast hall cased in blue ice to the ridge, half buried in a drift. Through the ice: long tables, raised cups, a king on a high seat.")
                .learned("Fire took the seal off the doors. With the long hearth lit the ice sang, and the king's horn came away from his hand.")
                .reach(32).icon(() -> Items.BLUE_ICE)
                .air(Air.of(0x8FB4D6, 50).mote(() -> ParticleTypes.SNOWFLAKE, 1.2F, 10, -0.04F).loop(() -> SoundEvents.ELYTRA_FLYING, 220, 0.1F, 0.6F))
                .scene(s -> {
                    s.level(24, 8).clear(26);
                    Pal p = Pal.TIMBER;
                    java.util.function.IntFunction<BlockState> ice = h -> h < 70 ? st(Blocks.BLUE_ICE) : st(Blocks.PACKED_ICE);
                    blob(s, 2, 2, -10, 17, 5.5, 4.5, h -> st(Blocks.SNOW_BLOCK));
                    for (int x = -15; x <= 15; x++)                         // the casing first: the hall goes inside it
                        for (int z = -8; z <= 8; z++)
                            for (int y = 1; y <= 12; y++) if (Math.abs(z) <= (y <= 4 ? 7 : 12 - y)) s.put(x, y, z, ice.apply(h(s, x, y, z)));
                    floor(s, -13, -5, 13, 5, 0, st(Blocks.SPRUCE_PLANKS), st(Blocks.DARK_OAK_PLANKS), 30);
                    air(s, -12, 1, -4, 12, 4, 4);
                    walls(s, -13, -5, 13, 5, 1, 4, p, 0);
                    for (int x = -13; x <= 13; x += 26) for (int z = -5; z <= 5; z += 10) for (int y = 1; y <= 4; y++) s.put(x, y, z, st(Blocks.DARK_OAK_LOG));
                    gable(s, -13, -5, 13, 5, 4, p, true, 1, 0);
                    for (int i = 1; i <= 4; i++) air(s, -12, 4 + i, -4 + i, 12, 4 + i, 4 - i);
                    for (int x = -9; x <= 9; x += 6) { for (int z = -4; z <= 4; z += 8) for (int y = 1; y <= 4; y++) s.put(x, y, z, st(Blocks.DARK_OAK_LOG)); for (int z = -4; z <= 4; z++) s.put(x, 5, z, axis(st(Blocks.DARK_OAK_LOG), Direction.Axis.Z)); s.put(x, 4, 0, lantern(true, true)); }
                    // the doors at the west end, sealed
                    for (int y = 1; y <= 2; y++) { s.put(-13, y, 0, AIR); s.put(-14, y, 0, st(Blocks.BLUE_ICE)); s.part("seal", -14, y, 0); s.put(-15, y, 0, AIR); }
                    // the feast
                    for (int z = -2; z <= 2; z += 4) {
                        table(s, -8, 1, z, 15, true, p);
                        for (int x = -8; x <= 6; x += 2) { stilled(s, x, 1, z + (z < 0 ? -1 : 1), x & 3, z < 0 ? Direction.SOUTH : Direction.NORTH); if (x % 4 == 0) s.put(x, 2, z, st(Blocks.DECORATED_POT)); }
                    }
                    for (int x = -4; x <= 4; x += 4) { s.put(x, 0, 0, st(Blocks.COBBLED_DEEPSLATE)); s.put(x, 1, 0, fire(false)); s.part("hearth", x, 1, 0); }
                    fill(s, 10, 1, -1, 11, 1, 1, st(Blocks.DARK_OAK_PLANKS));
                    s.put(11, 2, 0, stair(st(Blocks.DARK_OAK_STAIRS), Direction.EAST));
                    stilled(s, 10, 2, 0, 1, Direction.WEST);
                    s.put(10, 2, 1, Props.STONE_HORN.get().of(0));
                    s.part("prize", 10, 2, 1);
                    s.part("cache", 9, 1, 1);
                    // outside: marker poles to the doors, a frozen well, pillars of ice
                    for (int x = -24; x <= -17; x += 3) for (int z = -2; z <= 2; z += 4) { for (int y = 1; y <= 3; y++) s.put(x, y, z, st(Blocks.SPRUCE_FENCE)); s.put(x, 4, z, Props.FLAG.get().of(1)); }
                    ring(s, -8, 14, 1.5, 1, st(Blocks.COBBLESTONE));
                    s.put(-8, 0, 14, st(Blocks.ICE));
                    // the ice grew past the hall: spikes of it stand round the place, the tallest three times the roof
                    for (int[] k : new int[][] {{-20, -15, 16}, {-6, -20, 11}, {12, -19, 20}, {21, -8, 12}, {20, 12, 17}, {4, 19, 9}, {-17, 14, 13}, {-13, -17, 8}}) {
                        for (int y = 1; y <= k[2]; y++) {
                            double r = 2.7 * Math.pow(1 - (y - 1) / (double) k[2], 0.8) + 0.35;
                            int lx = k[0] + (y * (k[0] > 0 ? 1 : -1)) / 9;
                            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (x * x + z * z <= r * r) s.put(lx + x, y, k[1] + z, ice.apply(h(s, lx + x, y, k[1] + z)));
                        }
                    }
                    for (int z = -1; z <= 1; z += 2) stilled(s, -17, 1, z * 2, 0, Direction.WEST);   // the door wards, iced where they stood
                    for (int i = 0; i < 6; i++) { double a = Math.PI * (0.15 + 0.14 * i); int x = (int) Math.round(Math.cos(a) * 19), z = 8 + (int) Math.round(Math.sin(a) * 12); for (int y = 1; y <= 3 + (i * 7 % 3); y++) s.put(x, y, z, ice.apply(h(s, x, y, z))); }
                    fill(s, 6, 1, 12, 8, 1, 12, st(Blocks.SPRUCE_SLAB));
                    s.put(6, 1, 13, st(Blocks.SPRUCE_FENCE));
                    s.put(8, 1, 13, st(Blocks.SPRUCE_FENCE));
                    wild(s, 20, 31, 7);
                    s.cam(0, 14, 44, 180, 10).cam(19, 24, 19, 135, 40).cam(-21, 2, 0, -90, 0).cam(-9, 2, 0, -90, 0);
                })
                .script(() -> Steps.of(Seal.opens("seal", 4, Hands::flame), Light.all("hearth"), Prize.at("prize")))
                .play(t -> { t.hold(Items.TORCH); for (int i = 0; i < 4; i++) t.hit("seal", 0); t.hold(Items.FLINT_AND_STEEL); for (int i = 0; i < 3; i++) t.use("hearth", i); t.use("prize", 0); })
                .add();

        Kind.of(FSet.PLACES, 18, "bell_foundry", "The Bell Foundry").verb(Verb.RING).place().rank(2).lands(Group.MOUNTAIN, Group.SNOW)
                .anchor(Anchor.FIELD).slope(6).made(Made.FALLEN)
                .see("A ruined works: a cracked brick stack, a casting pit under a crane, and the green shoulder of a great bell half buried by the gate.")
                .learned("Coal, fire, the crane's winch, and the last bell came up out of its mould. The two test bells still agreed with each other.")
                .reach(32).icon(() -> Items.BELL)
                .air(Air.of(0x7C7873, 56).mote(() -> ParticleTypes.ASH, 1.4F, 9, -0.01F).loop(() -> SoundEvents.BELL_RESONATE, 480, 0.3F, 0.5F).loop(() -> SoundEvents.FIRE_AMBIENT, 100, 0.4F, 1F))
                .scene(s -> {
                    s.level(24, 8).clear(26);
                    Pal p = Pal.BRICK;
                    BlockState brick = st(Blocks.BRICKS), copper = st(Blocks.CUT_COPPER.waxed().weathered()), green = st(Blocks.CUT_COPPER.waxed().oxidized());
                    ground(s, 0, 0, 20, h -> h < 60 ? st(Blocks.GRAVEL) : h < 110 ? st(Blocks.TUFF) : h < 140 ? st(Blocks.COARSE_DIRT) : null);
                    walls(s, -19, -15, 19, 17, 1, 3, p, 2);
                    air(s, 0, 1, 17, 2, 4, 17);
                    // the furnace house and its stack
                    house(s, -15, -12, -5, -4, 0, 4, p, 2, 45);
                    s.put(-10, 1, -11, st(Blocks.BLAST_FURNACE));
                    s.part("furnace", -10, 1, -11);
                    s.put(-11, 1, -11, brick);
                    s.put(-9, 1, -11, brick);
                    for (int y = 1; y <= 18; y++) for (int x = -17; x <= -15; x++) for (int z = -14; z <= -12; z++) if ((x != -16 || z != -13) && !(y == 18 && h(s, x, y, z) < 110)) s.put(x, y, z, h(s, x, y, z) < 50 ? st(Blocks.GRANITE) : brick);
                    // the pit, the crane over it, and what is in the mould
                    for (int x = 2; x <= 10; x++)
                        for (int z = -6; z <= 2; z++) {
                            double d = Math.sqrt((x - 6) * (x - 6) + (z + 2) * (z + 2));
                            if (d > 4.3) continue;
                            for (int y = -5; y <= 0; y++) s.put(x, y, z, d > 3.3 || y == -5 ? (h(s, x, y, z) < 80 ? st(Blocks.DEEPSLATE_BRICKS) : brick) : AIR);
                            if (d > 3.3 && !(x == 6 && z > 0)) s.put(x, 1, z, along(st(Blocks.IRON_BARS), Math.abs(x - 6) < Math.abs(z + 2)));
                        }
                    for (int y = -4; y <= 0; y++) s.put(6, y, 1, ladder(Direction.NORTH));
                    for (int y = -4; y <= -2; y++) for (int x = 5; x <= 7; x++) for (int z = -3; z <= -1; z++) {
                        boolean coreCell = x == 6 && z == -2;
                        s.put(x, y, z, coreCell ? (y == -2 ? green : copper) : st(Blocks.TERRACOTTA));
                        if (!coreCell || y == -2) { if (!coreCell) s.part("mould", x, y, z); }
                    }
                    s.put(6, -4, -4, st(Blocks.MAGMA_BLOCK));
                    for (int y = 1; y <= 9; y++) s.put(11, y, -2, st(Blocks.SPRUCE_LOG));
                    limb(s, 11, 9, -2, 6, 9, -2, axis(st(Blocks.SPRUCE_LOG), Direction.Axis.X));
                    s.put(10, 8, -2, st(Blocks.SPRUCE_FENCE));
                    for (int y = -1; y <= 8; y++) s.put(6, y, -2, st(Blocks.IRON_CHAIN));
                    s.put(12, 1, -2, Props.WINCH.get().of(0));
                    s.part("chain", 12, 1, -2);
                    stilled(s, 9, 1, 3, 1, Direction.WEST);
                    // the great bell by the gate, the test frame, the coal
                    dome(s, 10, 10, 3.5, 0, copper);
                    ring(s, 10, 10, 3.5, 0, green);
                    ring(s, 10, 10, 4.2, 1, green);
                    s.put(10, 4, 10, st(Blocks.DARK_OAK_LOG));
                    for (int x = -8; x <= -4; x += 4) for (int y = 1; y <= 4; y++) s.put(x, y, 10, st(Blocks.DARK_OAK_FENCE));
                    fill(s, -8, 5, 10, -4, 5, 10, st(Blocks.DARK_OAK_PLANKS));
                    BlockState hung = st(Blocks.BELL).setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING);
                    for (int x = -7; x <= -5; x += 2) { s.put(x, 4, 10, hung); s.part("bell", x, 4, 10); }
                    s.part("cache", -6, 1, 11);
                    fill(s, 13, 1, -12, 15, 1, -11, st(Blocks.COAL_BLOCK));
                    s.put(14, 2, -11, st(Blocks.COAL_BLOCK));
                    s.part("loose", 14, 2, -11);
                    for (int x = 12; x <= 16; x += 4) for (int y = 1; y <= 3; y++) s.put(x, y, -13, st(Blocks.SPRUCE_FENCE));
                    fill(s, 12, 4, -13, 16, 4, -10, st(Blocks.SPRUCE_SLAB));
                    blob(s, -12, 1, 8, 3.4, 2.2, 3.0, h -> h < 110 ? st(Blocks.TUFF) : h < 180 ? st(Blocks.SMOOTH_BASALT) : st(Blocks.GRAVEL));
                    blob(s, 15, 1, 3, 2.4, 1.8, 2.6, h -> h < 130 ? st(Blocks.TUFF) : st(Blocks.SMOOTH_BASALT));
                    fill(s, -2, 1, 4, -1, 1, 5, top(st(Blocks.SPRUCE_SLAB)));
                    s.put(-3, 1, 4, Props.CART_WHEEL.get().of(0));
                    s.put(0, 1, 5, Props.CART_WHEEL.get().of(0));
                    s.put(-2, 2, 4, st(Blocks.RAW_COPPER_BLOCK));
                    rubble(s, -10, -2, 5, 12, p);
                    path(s, 1, 24, 1, 17, 1, st(Blocks.GRAVEL), st(Blocks.COARSE_DIRT));
                    wild(s, 20, 31, 7);
                    s.cam(0, 14, 44, 180, 10).cam(19, 24, 19, 135, 40).cam(1, 2, 21, 180, 0).cam(1, 2, 2, -100, 16);
                })
                .script(() -> Steps.of(Sockets.need("furnace", st -> st.is(Items.COAL) || st.is(Items.CHARCOAL) || st.is(Items.COAL_BLOCK),
                                i -> st(Blocks.BLAST_FURNACE).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true)).carry("loose"),
                        Prize.at("chain").then((c, i) -> { PlacesWood.fillPart(c, "mould"); c.sound(c.pos("chain", 0), SoundEvents.CHAIN_STEP, 2.0F, 0.6F); }), Notes.of("bell", 2)))
                .play(t -> { t.use("loose", 0).hold(Items.COAL_BLOCK).use("furnace", 0).use("chain", 0); for (int i : Notes.order(t.seed(), 2, 2)) t.use("bell", i); })
                .add();
    }
}
