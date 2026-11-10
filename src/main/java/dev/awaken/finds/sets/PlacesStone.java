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

/** Places of the mountain, the desert and the badlands (SPEC §59, docs/places/BRIEFS.md 19-24). */
final class PlacesStone {
    private PlacesStone() {}

    private static final BlockState ROCK = st(Blocks.STONE);

    private static BlockState fire(boolean lit) {
        return st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, lit);
    }

    /** A shelf of rock up to a height, for something to stand on above the ground. */
    private static void shelf(Scene s, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int k = 1; k <= y; k++) s.put(x, k, z, h(s, x, k, z) < 36 ? st(Blocks.ANDESITE) : ROCK);
    }

    static void init() {
        Kind.of(FSet.PLACES, 19, "stair_monastery", "The Stair Monastery").verb(Verb.WAIT).place().rank(3).lands(Group.MOUNTAIN)
                .anchor(Anchor.FIELD).slope(7).made(Made.STILLED)
                .see("White cells stepping up a crag beside a stair cut into the rock, flags on every landing, and one golden roof at the top.")
                .learned("The wheels at the foot, the long stair, and the gong struck as the light changed. A monk sat in every cell and none of them minded.")
                .reach(34).lead().icon(() -> Items.BELL)
                .air(Air.of(0xC9D6E2, 90).mote(() -> ParticleTypes.SNOWFLAKE, 0.6F, 16, -0.03F).loop(() -> SoundEvents.AMETHYST_BLOCK_CHIME, 130, 0.3F, 0.8F))
                .scene(s -> {
                    s.level(26, 8).clear(28);
                    Pal p = Pal.WHITE, gold = Pal.WHITE.roof(Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB);
                    blob(s, 0, 10, -6, 16, 15, 11, h -> h < 34 ? st(Blocks.ANDESITE) : ROCK);
                    for (int x = -8; x <= 8; x++) for (int z = -14; z <= 2; z++) if (x * x + (z + 6) * (z + 6) <= 60) { for (int y = 12; y <= 24; y++) s.put(x, y, z, ROCK); for (int y = 25; y <= 30; y++) s.put(x, y, z, AIR); }
                    // three flights, each eight steps on a buttress of rock
                    int[][] flight = {{-9, 8, 1, 1}, {0, 6, 9, 3}, {-9, 4, 17, 1}};
                    for (int[] f : flight)
                        for (int i = 0; i < 8; i++) {
                            int x = f[0] + (f[3] == 1 ? i : -i), y = f[2] + i;
                            for (int z = f[1]; z <= f[1] + 1; z++) { s.put(x, y, z, stair(st(Blocks.ANDESITE_STAIRS), DIR[f[3]])); for (int k = 1; k < y; k++) s.put(x, k, z, ROCK); for (int k = y + 1; k <= y + 3; k++) s.put(x, k, z, AIR); }
                        }
                    shelf(s, -1, 6, 1, 9, 8);
                    shelf(s, -9, 4, -8, 7, 16);
                    shelf(s, -1, -1, 1, 5, 24);
                    for (int z = -1; z <= 5; z++) for (int y = 25; y <= 27; y++) for (int x = -1; x <= 1; x++) s.put(x, y, z, AIR);
                    // the cells, each on its own shelf beside a landing
                    house(s, 8, 8, 12, 11, 0, 2, p, 3, 0);
                    house(s, -15, 9, -11, 12, 0, 2, p, 1, 0);
                    shelf(s, 2, 10, 6, 13, 8);
                    house(s, 2, 10, 6, 13, 8, 2, p, 0, 0);
                    shelf(s, -14, 4, -10, 7, 16);
                    house(s, -14, 4, -10, 7, 16, 2, p, 1, 0);
                    stilled(s, 10, 1, 9, 2, Direction.WEST);
                    stilled(s, -13, 1, 10, 2, Direction.EAST);
                    stilled(s, 4, 9, 12, 2, Direction.NORTH);
                    stilled(s, -12, 17, 5, 2, Direction.EAST);
                    // the top: the court, the gong, the shrine under its gold roof
                    floor(s, -7, -13, 7, 1, 24, st(Blocks.POLISHED_ANDESITE), st(Blocks.CALCITE), 25);
                    house(s, -3, -12, 3, -7, 24, 3, gold, 2, 0);
                    s.put(0, 25, -10, st(Blocks.CHISELED_STONE_BRICKS));
                    s.put(0, 26, -10, candles(3, true));
                    s.part("cache", 0, 25, -9);
                    for (int x = 4; x <= 6; x += 2) for (int y = 25; y <= 27; y++) s.put(x, y, -2, st(Blocks.DARK_OAK_FENCE));
                    fill(s, 4, 28, -2, 6, 28, -2, st(Blocks.DARK_OAK_PLANKS));
                    s.put(5, 27, -2, st(Blocks.IRON_CHAIN));
                    s.put(5, 26, -2, Props.GONG.get().of(0));
                    s.part("gong", 5, 26, -2);
                    stilled(s, 4, 25, -1, 1, Direction.EAST);
                    // the wheels at the foot, and flags all the way up
                    for (int i = 0; i < 5; i++) { s.put(-4 + i * 2, 1, 13, st(Blocks.POLISHED_ANDESITE)); s.put(-4 + i * 2, 2, 13, Props.RING_POST.get().of(i & 3)); s.part("wheel", -4 + i * 2, 2, 13); }
                    for (int[] k : new int[][] {{-10, 1, 10}, {-1, 9, 10}, {1, 9, 5}, {-10, 17, 8}, {-2, 25, 6}, {7, 25, 0}, {-7, 25, -12}}) { for (int y = 0; y <= 3; y++) s.put(k[0], k[1] + y, k[2], st(Blocks.DARK_OAK_FENCE)); s.put(k[0], k[1] + 4, k[2], Props.FLAG.get().of((k[0] + k[2]) & 3)); if (k[1] > 1) for (int y = 1; y < k[1]; y++) if (s.get(k[0], y, k[2]) == null) s.put(k[0], y, k[2], ROCK); }
                    path(s, -11, 30, -10, 10, 1, st(Blocks.GRAVEL), st(Blocks.COARSE_DIRT));
                    s.spawnUp(EntityTypes.GOAT, 10, -6, 16, 0, "goat").spawnUp(EntityTypes.GOAT, -12, -4, 14, 0, "goat");
                    wild(s, 22, 33, 7);
                    s.cam(0, 18, 50, 180, -4).cam(22, 40, 18, 140, 34).cam(-13, 2, 16, -150, -22).cam(5, 25, 0, 150, 4);
                })
                .script(() -> Steps.of(Read.all("wheel"), When.only(When.DAWN.or(When.DUSK), Prize.at("gong").then((c, i) -> c.sound(c.pos("gong", 0), SoundEvents.BELL_RESONATE, 4.0F, 0.5F)))))
                .play(t -> { for (int i = 0; i < 5; i++) t.use("wheel", i); t.hour(23500).use("gong", 0); })
                .add();

        Kind.of(FSet.PLACES, 20, "flooded_quarry", "The Flooded Quarry").verb(Verb.BREAK).place().rank(1).lands(Group.MOUNTAIN, Group.FOREST, Group.PLAINS)
                .anchor(Anchor.FIELD).slope(6).made(Made.FALLEN)
                .see("A square-cut hole in the land, stepped, full of bright water. A crane still holds a block over it, and something pale is down on the lowest step.")
                .learned("Three wedges and a block came off the top bench. The crane let its load go. Under the water a stone hand was holding something.")
                .reach(34).icon(() -> Items.STONE)
                .air(Air.of(0xA9D2CF, 80).mote(() -> ParticleTypes.FALLING_WATER, 0.2F, 6, -0.05F).loop(() -> SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 50, 0.7F, 1F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 520, 0.25F, 1.3F))
                .scene(s -> {
                    s.level(26, 8).clear(28);
                    java.util.function.IntFunction<BlockState> cut = h -> h < 40 ? st(Blocks.ANDESITE) : h < 60 ? st(Blocks.SMOOTH_STONE) : ROCK;
                    for (int x = -18; x <= 18; x++)
                        for (int z = -14; z <= 14; z++) {
                            int k = Math.min(3, Math.min(18 - Math.abs(x), 14 - Math.abs(z)) / 3), f = -3 * (k + 1);
                            for (int y = f - 2; y <= f; y++) s.put(x, y, z, cut.apply(h(s, x, y, z)));
                            for (int y = f + 1; y <= 0; y++) s.put(x, y, z, y <= -4 ? WATER : AIR);
                        }
                    for (int x = -19; x <= 19; x++) for (int z = -15; z <= 15; z++) if (Math.abs(x) == 19 || Math.abs(z) == 15) for (int y = -2; y <= 0; y++) s.put(x, y, z, cut.apply(h(s, x, y, z)));
                    for (int i = 0; i < 3; i++) for (int x = -1; x <= 1; x++) { s.put(x, -i, 17 - i, stair(st(Blocks.STONE_STAIRS), Direction.SOUTH)); for (int y = -i + 1; y <= 2; y++) s.put(x, y, 17 - i, AIR); s.put(x, -i - 1, 17 - i, ROCK); }
                    // the hill they were cutting into: a bluff over the north-west rim, its face left in three raw benches,
                    // a scaffold still against it
                    for (int x = -30; x <= 12; x++)
                        for (int z = -33; z <= -16; z++) {
                            if (x * x + z * z > 33 * 33) continue;
                            int bench = z > -19 ? 3 : z > -22 ? 6 : 9;
                            double fx = x < -22 ? Math.max(0, 1 - (-22 - x) / 8.0) : x > 3 ? Math.max(0, 1 - (x - 3) / 5.0) : 1, fz = z >= -25 ? 1 : Math.max(0, 1 - (-25 - z) / 8.0);
                            int dy = (int) Math.round(bench * fx * fx * (3 - 2 * fx) * fz * fz * (3 - 2 * fz));
                            if (dy <= 0) continue;
                            s.ground(x, z, dy);
                            if (z >= -26 && x >= -24 && x <= 5) for (int y = Math.max(1, dy - 3); y <= dy; y++) s.put(x, y, z, cut.apply(h(s, x, y, z)));
                        }
                    for (int x = -12; x <= -6; x += 3) for (int y = 1; y <= 3; y++) s.put(x, y, -15, st(Blocks.SPRUCE_FENCE));
                    for (int x = -13; x <= -5; x++) s.put(x, 4, -15, top(st(Blocks.SPRUCE_SLAB)));
                    for (int y = 1; y <= 3; y++) s.put(-13, y, -15, ladder(Direction.SOUTH));
                    for (int[] k : new int[][] {{-18, -17}, {-3, -20}, {-10, -23}}) { s.put(k[0], s.g(k[0], k[1]) + 1, k[1], st(Blocks.CHISELED_STONE_BRICKS)); s.put(k[0] + 1, s.g(k[0] + 1, k[1]) + 1, k[1], st(Blocks.STONE_BRICKS)); }
                    // the crane on the north rim, its block still in the sling
                    for (int y = 1; y <= 9; y++) s.put(11, y, -17, st(Blocks.SPRUCE_LOG));
                    limb(s, 11, 9, -17, 11, 9, -9, axis(st(Blocks.SPRUCE_LOG), Direction.Axis.Z));
                    limb(s, 11, 6, -17, 11, 8, -13, st(Blocks.SPRUCE_FENCE));
                    for (int y = -1; y <= 8; y++) s.put(11, y, -9, st(Blocks.IRON_CHAIN));
                    s.put(11, -2, -9, st(Blocks.CHISELED_STONE_BRICKS));
                    s.part("load", 11, -2, -9);
                    s.put(12, 1, -17, Props.WINCH.get().of(0));
                    s.part("crane", 12, 1, -17);
                    // the top bench: the wedges, the man who set them
                    int[][] wedge = {{-15, 4}, {-15, 6}, {-15, 8}};
                    for (int[] w : wedge) { s.put(w[0], -2, w[1], Props.WEDGE.get().of(0)); s.part("wedge", w[0], -2, w[1]); }
                    stilled(s, -16, -2, 2, 1, Direction.SOUTH);
                    // the rim: cut blocks, the shed, a cart
                    for (int[] k : new int[][] {{-22, -6}, {-22, 2}, {21, 8}, {6, 19}}) { fill(s, k[0], 1, k[1], k[0] + 1, 2, k[1] + 1, st(Blocks.STONE_BRICKS)); s.put(k[0], 3, k[1], st(Blocks.CHISELED_STONE_BRICKS)); s.put(k[0] + 1, 3, k[1] + 1, st(Blocks.STONE_BRICK_SLAB)); }
                    house(s, 11, 17, 19, 23, 0, 3, Pal.COBBLE, 3, 0);
                    s.put(17, 1, 21, st(Blocks.BARREL));
                    s.put(17, 1, 19, st(Blocks.STONECUTTER));
                    stack(s, 12, 1, 24, 5, true);
                    for (int x = -20; x <= -14; x += 3) logPile(s, x, 19, 1, 3, false, st(Blocks.SPRUCE_LOG));
                    fill(s, -8, 1, 19, -7, 1, 20, top(st(Blocks.SPRUCE_SLAB)));
                    s.put(-9, 1, 19, Props.CART_WHEEL.get().of(0));
                    s.put(-6, 1, 20, Props.CART_WHEEL.get().of(0));
                    s.put(-8, 2, 19, st(Blocks.STONE_BRICKS));
                    // the lowest bench, under eight blocks of water: the hand, the bell
                    fill(s, -3, -11, -1, 1, -10, 1, st(Blocks.SMOOTH_STONE));
                    for (int i = 0; i < 5; i++) for (int y = -9; y <= -8 + (i == 2 ? 2 : i % 2); y++) s.put(-3 + i, y, -1, st(Blocks.SMOOTH_STONE));
                    s.put(-1, -9, 1, st(Blocks.DECORATED_POT));
                    s.part("prize", -1, -9, 1);
                    s.part("cache", -1, -8, 1);
                    s.put(-4, -11, 0, st(Blocks.SEA_LANTERN));
                    s.put(2, -11, 0, st(Blocks.SEA_LANTERN));
                    dome(s, 5, 0, 2, -11, st(Blocks.CUT_COPPER.waxed().exposed()));
                    for (int x = 4; x <= 6; x++) for (int z = -1; z <= 1; z++) s.put(x, -11, z, AIR);
                    s.put(5, -10, 0, AIR);
                    s.spawnUp(EntityTypes.COD, 6, 5, -8, 0, "fish").spawnUp(EntityTypes.COD, -6, -4, -9, 0, "fish").spawnUp(EntityTypes.SALMON, 0, 6, -10, 0, "fish");
                    path(s, 0, 30, 0, 18, 1, st(Blocks.GRAVEL), st(Blocks.COARSE_DIRT));
                    wild(s, 22, 33, 7);
                    s.cam(0, 14, 48, 180, 16).cam(22, 22, 21, 135, 44).cam(0, 2, 22, 180, 12).cam(-16, -2, -5, -100, 14);
                })
                .script(() -> Steps.of(Seal.opens("wedge", 3, Hands::pick), Prize.at("crane").then((c, i) -> { c.set("load", 0, AIR); c.sound(c.pos("crane", 0), SoundEvents.CHAIN_STEP, 2.0F, 0.6F); }), Prize.at("prize")))
                .play(t -> t.hold(Items.IRON_PICKAXE).hit("wedge", 0).hit("wedge", 0).hit("wedge", 0).use("crane", 0).use("prize", 0))
                .add();

        Kind.of(FSet.PLACES, 21, "mine_head", "The Mine Head").verb(Verb.PULL).place().rank(1).lands(Group.MOUNTAIN, Group.BADLANDS)
                .anchor(Anchor.FIELD).slope(6).made(Made.FALLEN)
                .see("A timber frame with a great wheel over a railed shaft, a spoil heap, and a track running into a hole in a rock bank. The lamps down the hole are still lit.")
                .learned("The tipped cart went back on its rails, the wheel brought the cage up, and the pay box was in it.")
                .reach(32).icon(() -> Items.IRON_PICKAXE)
                .air(Air.of(0x8E7B66, 60).mote(() -> ParticleTypes.ASH, 0.9F, 7, -0.01F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 420, 0.3F, 0.9F).loop(() -> SoundEvents.CHAIN_STEP, 150, 0.4F, 0.6F))
                .scene(s -> {
                    s.level(24, 8).clear(26);
                    BlockState log = st(Blocks.SPRUCE_LOG), dark = st(Blocks.DARK_OAK_PLANKS), fence = st(Blocks.SPRUCE_FENCE);
                    ground(s, 0, 0, 20, h -> h < 70 ? st(Blocks.GRAVEL) : h < 120 ? st(Blocks.COARSE_DIRT) : h < 140 ? st(Blocks.TUFF) : null);
                    // the shaft, railed, the cage hanging in it
                    for (int x = 5; x <= 9; x++) for (int z = -2; z <= 2; z++) for (int y = -14; y <= 0; y++) {
                        boolean in = x >= 6 && x <= 8 && Math.abs(z) <= 1;
                        s.put(x, y, z, !in ? (h(s, x, y, z) < 90 ? st(Blocks.COBBLED_DEEPSLATE) : st(Blocks.COBBLESTONE)) : y <= -12 ? WATER : AIR);
                    }
                    fill(s, 5, -15, -2, 9, -15, 2, st(Blocks.COBBLED_DEEPSLATE));
                    for (int x = 5; x <= 9; x++) for (int z = -2; z <= 2; z++) if ((x == 5 || x == 9 || Math.abs(z) == 2) && !(x == 5 && z == 0)) s.put(x, 1, z, fence);
                    for (int y = -3; y <= -2; y++) { s.put(7, y, 0, st(Blocks.IRON_BARS)); s.part("cage", 7, y, 0); }
                    for (int y = -1; y <= 12; y++) s.put(7, y, 0, st(Blocks.IRON_CHAIN));
                    s.part("up", 7, 1, 0);
                    // the headframe and its wheel
                    for (int x = 4; x <= 10; x += 6) for (int z = -3; z <= 3; z += 6) { for (int y = 1; y <= 12; y++) s.put(x, y, z, log); limb(s, x, 1, z, x == 4 ? 6 : 8, 7, z, fence); }
                    for (int y = 6; y <= 12; y += 6) { fill(s, 4, y, -3, 10, y, -3, axis(log, Direction.Axis.X)); fill(s, 4, y, 3, 10, y, 3, axis(log, Direction.Axis.X)); fill(s, 4, y, -3, 4, y, 3, axis(log, Direction.Axis.Z)); fill(s, 10, y, -3, 10, y, 3, axis(log, Direction.Axis.Z)); }
                    fill(s, 5, 12, 0, 9, 12, 0, axis(log, Direction.Axis.X));
                    for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) { double d = Math.sqrt(dx * dx + dy * dy); if (d >= 1.5 && d < 2.5) s.put(7 + dx, 15 + dy, 0, dark); else if (d > 0 && d < 1.5 && (dx == 0 || dy == 0)) s.put(7 + dx, 15 + dy, 0, fence); }
                    s.put(7, 15, 0, axis(log, Direction.Axis.Z));
                    s.put(7, 13, 0, st(Blocks.IRON_CHAIN));
                    // the winding house
                    house(s, 13, -3, 19, 3, 0, 3, Pal.COBBLE, 3, 0);
                    s.put(12, 1, 0, Props.WINCH.get().of(0));
                    s.part("wheel", 12, 1, 0);
                    limb(s, 12, 2, 0, 12, 12, 0, st(Blocks.IRON_CHAIN));
                    limb(s, 12, 12, 0, 10, 12, 0, st(Blocks.IRON_CHAIN));
                    // the rock bank, the adit into it, the room where they ate
                    blob(s, -8, 3, -16, 13, 8, 7.5, h -> h < 12 ? st(Blocks.COAL_ORE) : h < 20 ? st(Blocks.COPPER_ORE) : h < 60 ? st(Blocks.ANDESITE) : ROCK);
                    air(s, -9, 1, -22, -7, 3, -9);
                    for (int z = -21; z <= -9; z++) { s.put(-8, 0, z, st(Blocks.GRAVEL)); s.put(-8, 1, z, st(Blocks.RAIL)); if ((z + 21) % 3 == 0) { for (int y = 1; y <= 3; y++) { s.put(-9, y, z, fence); s.put(-7, y, z, fence); } s.put(-8, 3, z, top(st(Blocks.SPRUCE_SLAB))); if ((z + 21) % 6 == 0) s.put(-8, 2, z, lantern(true, false)); } }
                    s.put(-8, 2, -23, st(Blocks.COAL_ORE));
                    s.put(-9, 1, -23, st(Blocks.COPPER_ORE));
                    stilled(s, -8, 1, -22, 1, Direction.NORTH);
                    air(s, -6, 1, -19, -3, 3, -15);
                    floor(s, -6, -19, -3, -15, 0, st(Blocks.COBBLESTONE), st(Blocks.GRAVEL), 30);
                    air(s, -6, 1, -17, -6, 2, -17);
                    table(s, -4, 1, -18, 3, false, Pal.COBBLE);
                    s.put(-3, 1, -15, st(Blocks.FURNACE));
                    s.put(-5, 3, -17, lantern(true, false));
                    for (int z = -18; z <= -16; z++) stilled(s, -5, 1, z, z & 3, Direction.EAST);
                    // the track out to the tip, the spoil, the cart that came off
                    for (int z = -8; z <= 6; z++) { s.put(-8, 0, z, st(Blocks.GRAVEL)); s.put(-8, 1, z, st(Blocks.RAIL)); }
                    blob(s, -8, 0, 11, 6, 3.4, 5, h -> h < 90 ? st(Blocks.GRAVEL) : h < 170 ? st(Blocks.TUFF) : st(Blocks.COARSE_DIRT));
                    s.put(-10, 1, 2, st(Blocks.BARREL).setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.EAST));
                    s.part("cart", -10, 1, 2);
                    s.put(-10, 1, 3, Props.CART_WHEEL.get().of(1));
                    s.spawnUp(EntityTypes.MINECART, -8, -4, 0, 0, "cart").spawnUp(EntityTypes.BAT, -8, -18, 1, 0, "bat");
                    s.part("cache", 6, 1, 3);
                    path(s, 0, 30, 2, 5, 1, st(Blocks.GRAVEL), st(Blocks.COARSE_DIRT));
                    wild(s, 20, 31, 7);
                    s.cam(0, 16, 46, 180, 6).cam(21, 26, 19, 135, 40).cam(-8, 2, -4, 180, 0).cam(-8, 2, -13, 180, 0);
                })
                .script(() -> Steps.of(Prize.at("cart").then((c, i) -> { c.set("cart", 0, st(Blocks.BARREL)); c.sound(c.pos("cart", 0), SoundEvents.WOOD_PLACE, 1.5F, 0.7F); }),
                        Prize.at("wheel").then((c, i) -> { PlacesWood.fillPart(c, "cage"); c.set("up", 0, st(Blocks.BARREL)); c.sound(c.pos("wheel", 0), SoundEvents.CHAIN_STEP, 2.0F, 0.5F); }), Prize.at("up")))
                .play(t -> t.use("cart", 0).use("wheel", 0).use("up", 0))
                .add();

        Kind.of(FSet.PLACES, 22, "salt_works", "The Salt Works").verb(Verb.READ).place().rank(1).lands(Group.DESERT, Group.BADLANDS)
                .anchor(Anchor.FIELD).slope(4).made(Made.STILLED)
                .see("A grid of shallow pans glaring white and pink on the flat, walked by narrow dykes, with a wind pump at one side and rakers stopped at their work.")
                .learned("The sluices open one after another from the pump. A block of salt on the foreman's scale, and he would have paid you for it.")
                .reach(38).icon(() -> Items.QUARTZ)
                .air(Air.of(0xEDE6D6, 70).mote(() -> ParticleTypes.WHITE_ASH, 0.7F, 6, 0.004F).loop(() -> SoundEvents.ELYTRA_FLYING, 260, 0.08F, 0.9F))
                .scene(s -> {
                    s.level(30, 8);
                    Pal p = Pal.MUD;
                    BlockState dyke = st(Blocks.SMOOTH_SANDSTONE), salt = st(Blocks.CALCITE), pink = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.PINK));
                    for (int x = -16; x <= 16; x++)
                        for (int z = -10; z <= 14; z++) {
                            boolean wall = (x + 16) % 8 == 0 || (z + 10) % 8 == 0;
                            if (wall) { s.put(x, 0, z, h(s, x, 0, z) < 50 ? st(Blocks.CUT_SANDSTONE) : dyke); continue; }
                            int pan = ((x + 16) / 8) * 3 + (z + 10) / 8, kind = (pan * 7 + (int) (s.site.seed() & 3)) % 3;
                            s.put(x, -1, z, kind == 1 ? (h(s, x, -1, z) < 110 ? pink : salt) : h(s, x, -1, z) < 70 ? st(Blocks.DIORITE) : salt);
                            s.put(x, 0, z, kind == 0 ? WATER : AIR);
                            if (kind == 2) { int cx = -12 + ((x + 16) / 8) * 8, cz = -6 + ((z + 10) / 8) * 8; double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)); if (d < 2.6) { s.put(x, 0, z, salt); if (d < 1.6) s.put(x, 1, z, d < 0.6 ? salt : st(Blocks.DIORITE_SLAB)); if (d < 0.6) s.put(x, 2, z, st(Blocks.DIORITE_SLAB)); } }
                        }
                    // the feeder along the north side, its three sluices, the wind pump
                    for (int x = -16; x <= 16; x++) { s.put(x, 0, -12, WATER); s.put(x, -1, -12, dyke); s.put(x, 0, -13, dyke); s.put(x, 0, -11, dyke); }
                    s.put(-17, 0, -12, dyke);
                    s.put(17, 0, -12, dyke);
                    for (int i = 0; i < 3; i++) { s.put(-8 + i * 8, 1, -11, Props.RING_POST.get().of(0)); s.part("sluice", -8 + i * 8, 1, -11); }
                    // the wind pump: a round tower with four cloth sails turning over the feeder
                    tower(s, 0, -19, 3, 0, 11, 6, Pal.SAND, st(Blocks.SPRUCE_PLANKS), st(Blocks.SPRUCE_SLAB));
                    s.put(0, 1, -16, AIR);
                    s.put(0, 2, -16, AIR);
                    for (int z = -15; z <= -14; z++) s.put(0, 9, z, axis(st(Blocks.SPRUCE_LOG), Direction.Axis.Z));
                    for (int j = 1; j <= 5; j++) {
                        s.put(j, 9, -14, st(Blocks.SPRUCE_FENCE));
                        s.put(-j, 9, -14, st(Blocks.SPRUCE_FENCE));
                        s.put(0, 9 + j, -14, st(Blocks.SPRUCE_FENCE));
                        s.put(0, 9 - j, -14, st(Blocks.SPRUCE_FENCE));
                        if (j < 2) continue;
                        s.put(j, 10, -14, st(Blocks.WOOL.pick(DyeColor.WHITE)));
                        s.put(-j, 8, -14, st(Blocks.WOOL.pick(DyeColor.WHITE)));
                        s.put(-1, 9 + j, -14, st(Blocks.WOOL.pick(DyeColor.WHITE)));
                        s.put(1, 9 - j, -14, st(Blocks.WOOL.pick(DyeColor.WHITE)));
                    }
                    // the outer dyke stands a block proud of the pans, open where the track comes in
                    for (int x = -16; x <= 16; x++) for (int z = -10; z <= 14; z++) if ((Math.abs(x) == 16 || z == -10 || z == 14) && !(z == 14 && Math.abs(x) <= 1) && h(s, x, 1, z) < 215) s.put(x, 1, z, h(s, x, 2, z) < 60 ? st(Blocks.CUT_SANDSTONE) : dyke);
                    // the boiling house with its stack, the drying floor under cloth, the white heaps waiting for the sledges
                    house(s, -28, -6, -20, 2, 0, 4, p, 1, 0);
                    for (int y = 1; y <= 11; y++) s.put(-27, y, -5, st(y % 4 == 0 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE));
                    s.put(-27, 12, -5, st(Blocks.CAMPFIRE).setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, true));
                    s.put(-25, 1, -4, st(Blocks.CAULDRON));
                    s.put(-24, 1, -4, st(Blocks.CAULDRON));
                    s.put(-23, 1, -4, st(Blocks.FURNACE));
                    stilled(s, -24, 1, -2, 2, Direction.NORTH);
                    awning(s, -27, 6, -20, 13, 1, 4, st(Blocks.ACACIA_FENCE), DyeColor.WHITE, DyeColor.LIGHT_GRAY);
                    for (int x = -25; x <= -22; x += 3) for (int z = 8; z <= 11; z += 3) { s.put(x, 1, z, salt); s.put(x, 2, z, st(Blocks.QUARTZ_SLAB)); }
                    cone(s, 23, 13, 3, 1, salt, st(Blocks.QUARTZ_SLAB));
                    cone(s, 28, 9, 2, 1, salt, st(Blocks.QUARTZ_SLAB));
                    cone(s, 18, 17, 2, 1, salt, st(Blocks.QUARTZ_SLAB));
                    // the foreman's house, his scale, a sledge of blocks
                    house(s, 20, -4, 26, 2, 0, 3, p, 3, 0);
                    s.put(19, 1, 0, st(Blocks.SMOOTH_SANDSTONE));
                    s.part("scale", 19, 2, 0);
                    s.part("cache", 19, 1, 1);
                    stilled(s, 19, 1, -2, 1, Direction.WEST);
                    fill(s, 20, 1, 6, 21, 1, 7, top(st(Blocks.ACACIA_SLAB)));
                    s.put(20, 2, 6, salt);
                    s.part("loose", 20, 2, 6);
                    s.put(21, 2, 7, salt);
                    s.put(22, 1, 6, Props.ROPE_COIL.get().of(0));
                    for (int[] k : new int[][] {{-12, -6}, {4, 2}, {-4, 10}}) { stilled(s, k[0], 0, k[1], k[0] & 3, Direction.SOUTH); s.put(k[0] + 1, 0, k[1], Props.OLD_ROD.get().of(0)); }
                    s.spawn(EntityTypes.LLAMA, 24, 8, 0, "llama");
                    path(s, 0, 36, 0, 16, 1, dyke, st(Blocks.SAND));
                    wild(s, 26, 37, 9);
                    s.cam(0, 14, 52, 180, 12).cam(23, 26, 25, 135, 44).cam(0, 2, 19, 180, 8).cam(0, 2, 2, 135, 10);
                })
                .script(() -> Steps.of(Read.all("sluice").inOrder(), Sockets.need("scale", st -> st.is(Items.CALCITE) || st.is(Items.QUARTZ_BLOCK), i -> st(Blocks.CALCITE)).carry("loose")))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("sluice", i); t.use("loose", 0).hold(Items.CALCITE).use("scale", 0); })
                .add();

        Kind.of(FSet.PLACES, 23, "caravanserai", "The Caravanserai").verb(Verb.LIGHT).place().rank(2).lands(Group.DESERT)
                .anchor(Anchor.FIELD).slope(5).made(Made.STILLED)
                .see("A square mud wall with one tall gate and palm tops showing over it. Inside, a caravan that knelt down for the night and is kneeling still.")
                .learned("Water drawn, the three lamps lit under the arcade, and the ring from the gate room fitted the strongroom door.")
                .reach(34).icon(() -> Items.LANTERN)
                .air(Air.of(0xD2A56A, 60).mote(() -> ParticleTypes.WHITE_ASH, 0.7F, 7, 0.003F).loop(() -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 150, 0.25F, 0.9F).loop(() -> SoundEvents.FIRE_AMBIENT, 120, 0.3F, 1F))
                .scene(s -> {
                    s.level(26, 8).clear(28);
                    Pal p = Pal.MUD;
                    BlockState mud = st(Blocks.MUD_BRICKS), slab = st(Blocks.MUD_BRICK_SLAB), tile = st(Blocks.TERRACOTTA);
                    ground(s, 0, 0, 24, h -> h < 150 ? tile : h < 200 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.PACKED_MUD));
                    walls(s, -18, -18, 18, 18, 1, 6, p, 0);
                    for (int i = -18; i <= 18; i += 2) { s.put(i, 7, -18, mud); s.put(i, 7, 18, mud); s.put(-18, 7, i, mud); s.put(18, 7, i, mud); }
                    for (int i = -18; i <= 18; i += 6) for (int y = 1; y <= 7; y++) { s.put(i, y, -19, st(Blocks.PACKED_MUD)); s.put(i, y, 19, i == 0 ? AIR : st(Blocks.PACKED_MUD)); s.put(-19, y, i, st(Blocks.PACKED_MUD)); s.put(19, y, i, st(Blocks.PACKED_MUD)); }
                    // the gate and the room over it
                    air(s, -1, 1, 18, 1, 4, 18);
                    s.put(-1, 4, 18, stairT(st(Blocks.MUD_BRICK_STAIRS), Direction.WEST));
                    s.put(1, 4, 18, stairT(st(Blocks.MUD_BRICK_STAIRS), Direction.EAST));
                    fill(s, -3, 7, 15, 3, 7, 18, mud);
                    walls(s, -3, 15, 3, 18, 8, 10, p.base(Blocks.MUD_BRICKS), 0);
                    air(s, 0, 8, 15, 0, 9, 15);
                    fill(s, -3, 11, 15, 3, 11, 18, slab);
                    s.put(0, 9, 18, AIR);
                    s.put(1, 8, 17, Props.BRASS_RING.get().of(0));
                    s.part("loose", 1, 8, 17);
                    // the arcade round the court, a flat roof on it that is the wall walk
                    for (int a = -14; a <= 14; a++) for (int side = 0; side < 4; side++) {
                        int x = side == 0 ? a : side == 1 ? a : side == 2 ? -14 : 14, z = side == 0 ? -14 : side == 1 ? 14 : a;
                        if (a % 4 == 2 || a % 4 == -2) for (int y = 1; y <= 4; y++) s.put(x, y, z, y == 1 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.SANDSTONE_WALL));
                        s.put(x, 5, z, mud);
                    }
                    for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) if ((Math.abs(x) > 14 || Math.abs(z) > 14) && s.get(x, 5, z) == null) s.put(x, 5, z, top(slab));
                    steps(s, -13, 1, 12, 0, 5, 1, st(Blocks.MUD_BRICK_STAIRS), mud);
                    s.put(-13, 5, 7, AIR);
                    s.put(-13, 6, 7, AIR);
                    // what the sides are for: stables, cells, kitchen, the strongroom
                    for (int z = -10; z <= 10; z += 5) { s.put(-16, 1, z, st(Blocks.HAY_BLOCK)); s.put(-15, 1, z + 1, st(Blocks.ACACIA_FENCE)); s.put(-15, 1, z + 2, st(Blocks.ACACIA_FENCE)); }
                    for (int x = -10; x <= 5; x += 5) { for (int z = -17; z <= -15; z++) for (int y = 1; y <= 4; y++) s.put(x, y, z, mud); s.put(x + 2, 1, -16, st(Blocks.CARPET.pick(x == 0 ? DyeColor.RED : DyeColor.ORANGE))); s.put(x + 3, 1, -16, st(Blocks.CARPET.pick(DyeColor.BROWN))); }
                    s.put(16, 1, -4, st(Blocks.FURNACE));
                    s.put(16, 1, -2, st(Blocks.BARREL));
                    s.put(16, 1, 0, st(Blocks.DECORATED_POT));
                    s.put(16, 2, -2, st(Blocks.DECORATED_POT));
                    fill(s, 10, 1, -17, 10, 4, -15, mud);
                    fill(s, 10, 1, -14, 17, 4, -14, mud);
                    s.put(13, 1, -14, st(Blocks.IRON_BARS));
                    s.part("door", 13, 1, -14);
                    s.put(13, 2, -14, st(Blocks.IRON_BARS));
                    s.part("bars", 13, 2, -14);
                    s.put(14, 1, -16, st(Blocks.BARREL));
                    s.part("cache", 13, 1, -16);
                    int n = 0;
                    for (int[] k : new int[][] {{-14, -6}, {2, 14}, {14, 6}}) { s.put(k[0] + (k[0] < 0 ? 1 : k[0] > 2 ? -1 : 0), 4, k[1] + (k[1] > 10 ? -1 : 0), Props.IRON_LAMP.get().of(0)); s.part("lamp", k[0] + (k[0] < 0 ? 1 : k[0] > 2 ? -1 : 0), 4, k[1] + (k[1] > 10 ? -1 : 0)); n++; }
                    // the court: the well under its dome, palms, the fire, the caravan
                    ring(s, 0, 3, 1.5, 1, st(Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE)));
                    s.put(0, 0, 3, WATER);
                    s.put(0, -1, 3, st(Blocks.SMOOTH_SANDSTONE));
                    s.part("well", 0, 1, 3);
                    for (int x = -2; x <= 2; x += 4) for (int z = 1; z <= 5; z += 4) for (int y = 1; y <= 3; y++) s.put(x, y, z, st(Blocks.SANDSTONE_WALL));
                    dome(s, 0, 3, 2.6, 4, st(Blocks.SMOOTH_SANDSTONE));
                    palm(s, 6, -5, 1, 8, 1);
                    palm(s, -7, 6, 1, 7, 3);
                    palm(s, 8, 9, 1, 9, 0);
                    ring(s, -6, -6, 1.5, 1, st(Blocks.SANDSTONE_SLAB));
                    s.put(-6, 1, -6, fire(true));
                    int[][] beast = {{3, -8}, {6, -9}, {-2, -9}, {9, -2}};
                    for (int[] b : beast) { s.put(b[0], 1, b[1], Props.STONE_SHEEP.get().of(0)); s.put(b[0] + 1, 1, b[1] + 1, Props.BUNDLE.get().of(b[0] & 1)); }
                    for (int[] k : new int[][] {{-4, -6}, {-6, -4}, {-8, -7}, {4, -6}, {7, -7}, {1, 6}}) stilled(s, k[0], 1, k[1], k[0] & 3, k[0] < 0 ? Direction.EAST : Direction.WEST);
                    s.spawn(EntityTypes.LLAMA, -15, -8, Scene.Spawn.STAY, "llama").spawn(EntityTypes.CAT, 12, 10, Scene.Spawn.STAY, "cat");
                    path(s, 0, 32, 0, 19, 1, st(Blocks.SMOOTH_SANDSTONE), st(Blocks.SAND));
                    wild(s, 22, 33, 7);
                    s.cam(0, 16, 50, 180, 12).cam(23, 28, 23, 135, 44).cam(0, 2, 25, 180, -4).cam(5, 2, 9, 150, 0);
                })
                .script(() -> Steps.of(Prize.at("well").then((c, i) -> c.sound(c.pos("well", 0), SoundEvents.BUCKET_FILL, 1.5F, 1F)), Light.all("lamp"),
                        Sockets.need("door", st -> st.is(Props.BRASS_RING.get().asItem()), i -> AIR).carry("loose")))
                .after(c -> c.set("bars", 0, AIR))
                .play(t -> { t.use("well", 0).hold(Items.FLINT_AND_STEEL); for (int i = 0; i < 3; i++) t.use("lamp", i); t.use("loose", 0).hold(Props.BRASS_RING.get()).use("door", 0); })
                .add();

        Kind.of(FSet.PLACES, 24, "bone_yard", "The Bone Yard").verb(Verb.MEND).place().rank(2).lands(Group.BADLANDS, Group.DESERT)
                .anchor(Anchor.FIELD).slope(5).made(Made.BEAST)
                .see("Ribs taller than trees arching out of red ground, a spine along the top of them, and a skull with its jaw open that somebody has been living in.")
                .learned("The carved tooth went back in the jaw. You lit their hearth in the skull and read what they painted on the bone.")
                .reach(38).icon(() -> Items.BONE)
                .air(Air.of(0xB9895A, 56).mote(() -> ParticleTypes.WHITE_ASH, 1.2F, 8, 0.0F).loop(() -> SoundEvents.ELYTRA_FLYING, 240, 0.1F, 0.5F))
                .scene(s -> {
                    s.level(30, 8).clear(32);
                    BlockState bone = st(Blocks.BONE_BLOCK), boneX = axis(st(Blocks.BONE_BLOCK), Direction.Axis.X), boneZ = axis(st(Blocks.BONE_BLOCK), Direction.Axis.Z), quartz = st(Blocks.QUARTZ_BLOCK);
                    ground(s, 0, 0, 28, h -> h < 80 ? st(Blocks.RED_SAND) : h < 130 ? st(Blocks.TERRACOTTA) : h < 160 ? st(Blocks.COARSE_DIRT) : null);
                    // the spine and eleven pairs of ribs, smaller toward the tail
                    for (int i = 0; i <= 10; i++) {
                        int x = -16 + i * 3;
                        double k = 0.55 + 0.45 * Math.sin(Math.PI * (i + 2) / 13.0);
                        int top = (int) Math.round(10 * k), out = (int) Math.round(7 * k), mid = Math.max(2, (int) Math.round(3 * k));
                        for (int side = -1; side <= 1; side += 2) {
                            limb(s, x, top, 0, x, top + mid, side * mid, boneZ);
                            limb(s, x, top + mid, side * mid, x, top - 1, side * out, bone);
                            limb(s, x, top - 1, side * out, x, 1, side * out, bone);
                            s.put(x, 1, side * (out - 1), boneZ);
                        }
                        if (i < 10) { int nextTop = (int) Math.round(10 * (0.55 + 0.45 * Math.sin(Math.PI * (i + 3) / 13.0))); limb(s, x, top, 0, x + 3, nextTop, 0, boneX); }
                    }
                    for (int j = 0; j < 7; j++) { int x = -18 - j * 2; s.put(x, Math.max(0, 3 - j), 0, boneX); if (j < 3) for (int y = 1; y < 3 - j; y++) s.put(x, y, 0, bone); }
                    limb(s, -16, 6, 0, -18, 3, 0, boneX);
                    // the skull: a room with its mouth to the east
                    blob(s, 20, 4, 0, 6.0, 4.6, 4.8, h -> h < 40 ? quartz : bone);
                    limb(s, 14, 9, 0, 16, 7, 0, boneX);
                    for (int x = 16; x <= 24; x++) for (int z = -3; z <= 3; z++) for (int y = 1; y <= 5; y++) { double f = (x - 20) * (x - 20) / 20.0 + z * z / 10.5 + (y - 3) * (y - 3) / 7.5; if (f <= 1) s.put(x, y, z, AIR); }
                    air(s, 24, 1, -1, 27, 3, 1);
                    for (int z = -2; z <= 2; z += 4) { s.put(23, 6, z, AIR); s.put(24, 6, z, AIR); s.put(23, 7, z, AIR); }
                    floor(s, 16, -3, 25, 3, 0, st(Blocks.RED_SAND), st(Blocks.TERRACOTTA), 40);
                    for (int z = -2; z <= 2; z++) if (z != 0) { s.put(26, 1, z, st(Blocks.QUARTZ_SLAB)); }
                    s.part("jaw", 26, 1, 0);
                    s.put(19, 1, 0, fire(false));
                    s.part("hearth", 19, 1, 0);
                    s.put(17, 1, -2, st(Blocks.HAY_BLOCK));
                    s.put(17, 1, 2, st(Blocks.HAY_BLOCK));
                    stilled(s, 18, 1, 2, 3, Direction.WEST);
                    for (int i = 0; i < 3; i++) { s.put(19 + i * 2, 2, -3, Props.PICTURE_STONE.get().front(i, Direction.SOUTH)); s.part("wall", 19 + i * 2, 2, -3); }
                    s.part("cache", 21, 1, 1);
                    s.put(22, 5, 0, light(8));
                    // the camp between the ribs
                    Pal hide = Pal.SAND.roof(Blocks.WOOL_STAIRS.pick(DyeColor.BROWN), Blocks.WOOL_SLAB.pick(DyeColor.BROWN));
                    for (int[] k : new int[][] {{-8, 3}, {-1, -3}, {6, 3}}) {
                        for (int x = k[0] - 1; x <= k[0] + 1; x += 2) for (int z = k[1] - 1; z <= k[1] + 1; z += 2) for (int y = 1; y <= 2; y++) s.put(x, y, z, st(Blocks.ACACIA_FENCE));
                        gable(s, k[0] - 1, k[1] - 1, k[0] + 1, k[1] + 1, 2, hide, true, 1, 0);
                        s.put(k[0], 1, k[1], st(Blocks.CARPET.pick(DyeColor.RED)));
                    }
                    table(s, 2, 1, -1, 3, true, Pal.SAND);
                    s.put(3, 2, -1, AIR);
                    s.put(3, 2, -1, Props.GREAT_RIB.get().of(1));
                    s.part("loose", 3, 2, -1);
                    stilled(s, 3, 1, 0, 1, Direction.NORTH);
                    stilled(s, -4, 1, 1, 2, Direction.EAST);
                    for (int x = -11; x <= -9; x += 2) for (int y = 1; y <= 2; y++) s.put(x, y, -2, st(Blocks.ACACIA_FENCE));
                    s.put(-10, 2, -2, st(Blocks.ACACIA_FENCE));
                    s.put(-10, 3, -2, st(Blocks.CARPET.pick(DyeColor.BROWN)));
                    for (int[] k : new int[][] {{-5, -4}, {8, -2}, {12, 4}}) s.put(k[0], 1, k[1], Props.FIRE_BASKET.get().of(1));
                    for (int i = 0; i < 46; i++) {
                        int x = s.rnd.nextInt(57) - 28, z = s.rnd.nextInt(57) - 28;
                        if (s.taken(x, 1, z) || x * x + z * z > 780) continue;
                        s.put(x, 1, z, i % 5 == 0 ? Props.GREAT_RIB.get().of(i & 3) : i % 5 == 1 ? st(Blocks.QUARTZ_SLAB) : i % 5 == 2 ? Props.LOOSE_STONE.get().of(i & 3) : st(Blocks.DEAD_BUSH));
                    }
                    path(s, 30, 0, 27, 0, 1, st(Blocks.RED_SAND), st(Blocks.COARSE_DIRT));
                    wild(s, 26, 37, 9);
                    s.cam(0, 16, 54, 180, 8).cam(26, 28, 24, 130, 40).cam(29, 2, 0, 90, 0).cam(-4, 2, 0, -90, -18);
                })
                .script(() -> Steps.of(Sockets.need("jaw", st -> st.is(Props.GREAT_RIB.get().asItem()), i -> Props.GREAT_RIB.get().of(0)).carry("loose"), Light.all("hearth"), Read.all("wall")))
                .play(t -> { t.use("loose", 0).hold(Props.GREAT_RIB.get()).use("jaw", 0).hold(Items.FLINT_AND_STEEL).use("hearth", 0); for (int i = 0; i < 3; i++) t.use("wall", i); })
                .add();
    }
}
