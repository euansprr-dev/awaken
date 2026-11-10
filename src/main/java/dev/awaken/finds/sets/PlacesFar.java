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

/** Places of the badlands, the coast and the rare lands (SPEC §59, docs/places/BRIEFS.md 25-30). */
final class PlacesFar {
    private PlacesFar() {}

    private static BlockState fire(boolean lit) {
        return st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, lit);
    }

    private static BlockState band(int y) {
        return switch (Math.floorMod(y, 9)) {
            case 2 -> st(Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE));
            case 4, 5 -> st(Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN));
            case 7 -> st(Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE));
            case 8 -> st(Blocks.DYED_TERRACOTTA.pick(DyeColor.RED));
            default -> st(Blocks.TERRACOTTA);
        };
    }

    /** A giant mushroom: a stem and a cap three layers deep, spotted, with light under its rim. */
    private static void shroom(Scene s, int x, int z, int h, int r, boolean red) {
        BlockState stem = st(Blocks.MUSHROOM_STEM), cap = st(red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK);
        for (int y = 1; y <= h; y++) s.put(x, y, z, stem);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r + 0.3) s.put(x + dx, h + 1, z + dz, red && h(s, x + dx, h, z + dz) < 26 ? stem : cap);
                if (d <= r - 1.2) s.put(x + dx, h + 2, z + dz, red && h(s, x + dx, h + 1, z + dz) < 30 ? stem : cap);
                if (d > r - 0.7 && d <= r + 0.3) s.put(x + dx, h, z + dz, cap);
            }
        for (int d = 0; d < 4; d++) if (r >= 4) s.put(x + DX[d] * (r - 2), h, z + DZ[d] * (r - 2), st(Blocks.SHROOMLIGHT));
    }

    static void init() {
        Kind.of(FSet.PLACES, 25, "mesa_granary", "The Mesa Granary").verb(Verb.CARRY).place().rank(3).lands(Group.BADLANDS)
                .anchor(Anchor.FIELD).slope(6).made(Made.STILLED)
                .see("A lone flat-topped butte with a row of doorways halfway up its face, ladders to them, and stone people grinding corn on the ledge.")
                .learned("The grain basket went up to the one empty store, and you lit the fire on top. It can be seen from a long way off.")
                .reach(32).icon(() -> Items.TERRACOTTA)
                .air(Air.of(0xC98E66, 70).mote(() -> ParticleTypes.WHITE_ASH, 0.5F, 10, 0.002F).loop(() -> SoundEvents.ELYTRA_FLYING, 300, 0.07F, 0.7F))
                .scene(s -> {
                    s.level(24, 8).clear(26);
                    ground(s, 0, 0, 22, h -> h < 80 ? st(Blocks.RED_SAND) : h < 120 ? st(Blocks.TERRACOTTA) : null);
                    // the butte: twenty-two high, banded, a little narrower toward the top
                    for (int x = -15; x <= 15; x++)
                        for (int z = -15; z <= 11; z++) {
                            double e = x * x / 182.0 + (z + 2) * (z + 2) / 132.0, rag = n(s, x, z, 4.5) * 0.22;
                            for (int y = 1; y <= 22; y++) if (e <= 1.0 + rag - y / 22.0 * 0.3) s.put(x, y, z, band(y + (int) (n(s, x * 2.0, z * 2.0, 9.0) * 2)));
                        }
                    // the alcove halfway up the south face, and the fronts built in it
                    int ledge = 0;
                    for (int x = -9; x <= 9; x++) {
                        int face = -99;
                        for (int z = 12; z >= -2; z--) if (s.get(x, 10, z) != null) { face = z; break; }
                        if (face < 0) continue;
                        if (x == 0) ledge = face;
                        for (int z = face; z >= face - 5; z--) for (int y = 10; y <= 13; y++) s.put(x, y, z, AIR);
                        for (int z = face; z >= face - 5; z--) if (s.get(x, 9, z) == null) s.put(x, 9, z, band(9));
                        int front = face - 3;
                        boolean door = Math.floorMod(x, 3) == 1;
                        for (int y = 10; y <= 13; y++) s.put(x, y, front, door && y <= 11 ? AIR : y == 13 ? st(Blocks.MUD_BRICK_SLAB) : h(s, x, y, front) < 70 ? st(Blocks.PACKED_MUD) : st(Blocks.MUD_BRICKS));
                        if (door) s.put(x, 10, front - 1, x == 7 ? AIR : st(Blocks.DECORATED_POT));
                        if (x == 7) s.part("store", 7, 10, front - 1);
                        if (Math.floorMod(x, 3) == 2 && Math.abs(x) < 8) for (int y = 10; y <= 12; y++) s.put(x, y, front - 1, st(Blocks.MUD_BRICKS));
                    }
                    for (int lx = -6; lx <= 6; lx += 6) {                 // three ladders up the face
                        int face = -99;
                        for (int z = 14; z >= -2; z--) if (s.get(lx, 9, z) != null) { face = z; break; }
                        for (int y = 1; y <= 9; y++) { for (int z = face; z >= face - 1 && y < 9; z--) if (s.get(lx, y, z) == null) s.put(lx, y, z, band(y)); s.put(lx, y, face + 1, ladder(Direction.SOUTH)); }
                    }
                    for (int[] k : new int[][] {{-4, 1}, {2, 1}, {5, 2}}) { stilled(s, k[0], 10, ledge - k[1], k[0] & 3, Direction.SOUTH); s.put(k[0] + 1, 10, ledge - k[1], st(Blocks.GRINDSTONE)); }
                    s.put(-8, 10, ledge - 2, fire(true));
                    s.put(-7, 10, ledge - 4, stair(st(Blocks.MUD_BRICK_STAIRS), Direction.NORTH));
                    // the way to the top inside the rock, and what is up there
                    for (int y = 10; y <= 23; y++) { s.put(8, y, ledge - 4, ladder(Direction.SOUTH)); if (y > 13) s.put(8, y, ledge - 5, band(y)); }
                    pool(s, 3, -5, 2.2, 2.2, 22, 1, st(Blocks.TERRACOTTA), st(Blocks.CLAY));
                    for (int x = -3; x <= -1; x++) s.put(x, 23, 4, st(Blocks.MUD_BRICK_WALL));
                    stilled(s, -2, 23, 3, 1, Direction.SOUTH);
                    s.put(-6, 23, -3, st(Blocks.HAY_BLOCK));
                    s.put(-6, 24, -3, fire(false));
                    s.part("fire", -6, 24, -3);
                    s.part("cache", -5, 23, -3);
                    s.put(4, 1, ledge + 5, Props.BASKET.get().of(1));
                    s.part("loose", 4, 1, ledge + 5);
                    for (int i = 0; i < 12; i++) { int x = s.rnd.nextInt(41) - 20, z = 12 + s.rnd.nextInt(10); if (!s.taken(x, 1, z)) s.put(x, 1, z, st(i % 3 == 0 ? Blocks.DEAD_BUSH : i % 3 == 1 ? Blocks.CACTUS : Blocks.DECORATED_POT)); }
                    path(s, 0, 30, 0, ledge + 3, 1, st(Blocks.RED_SAND), st(Blocks.COARSE_DIRT));
                    wild(s, 20, 31, 7);
                    s.cam(0, 14, 46, 180, -2).cam(21, 36, 19, 135, 40).cam(0, 2, ledge + 8, 180, -28).cam(-3, 10, ledge - 1, -90, 4);
                })
                .script(() -> Steps.of(Sockets.need("store", st -> st.is(Props.BASKET.get().asItem()), i -> Props.BASKET.get().of(1)).carry("loose"), Light.all("fire")))
                .play(t -> t.use("loose", 0).hold(Props.BASKET.get()).use("store", 0).hold(Items.FLINT_AND_STEEL).use("fire", 0))
                .add();

        Kind.of(FSet.PLACES, 26, "glass_garden", "The Glass Garden").verb(Verb.RING).place().rank(4).lands(Group.DESERT)
                .anchor(Anchor.FIELD).slope(5).made(Made.STRANGE)
                .see("A wide shallow crater full of glass spires taller than houses, white and blue and violet, round a small green pool. Something black sits in the middle.")
                .learned("Five of the spires sing. Struck in their order, the black stone gave up the iron that fell with it.")
                .reach(36).loot("star_iron").icon(Props.STAR_IRON)
                .air(Air.of(0xCDBFE3, 70).mote(() -> ParticleTypes.END_ROD, 0.25F, 10, 0.0F).loop(() -> SoundEvents.AMETHYST_BLOCK_CHIME, 60, 0.35F, 1.4F))
                .scene(s -> {
                    s.level(28, 8);
                    for (int x = -25; x <= 25; x++) for (int z = -25; z <= 25; z++) { double d = Math.sqrt(x * x + z * z); if (d <= 25) s.ground(x, z, -(int) Math.round(5 * (1 - d * d / 625.0))); else if (d <= 28) s.ground(x, z, d > 25.8 && d < 27.6 ? 2 : 1); } // the bowl, and the lip thrown up round it
                    ground(s, 0, 0, 27, h -> h < 60 ? st(Blocks.SMOOTH_BASALT) : h < 100 ? st(Blocks.BLACKSTONE) : h < 170 ? st(Blocks.SAND) : st(Blocks.RED_SAND));
                    DyeColor[] tint = {DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.WHITE};
                    int spires = 0;
                    for (int i = 0; i < 800 && spires < 64; i++) {
                        int x = s.rnd.nextInt(45) - 22, z = s.rnd.nextInt(45) - 22;
                        double d = Math.sqrt(x * x + z * z);
                        if (d > 22 || d < 6 || Math.abs(x) < 2 && z > 0 || s.taken(x, s.g(x, z) + 1, z) || s.taken(x + 1, s.g(x + 1, z) + 1, z) || s.taken(x, s.g(x, z + 1) + 1, z + 1)) continue;
                        int g = s.g(x, z), hgt = 3 + (int) ((1 - d / 23) * 19 * (0.45 + s.rnd.nextDouble() * 0.55));
                        BlockState glass = st(Blocks.STAINED_GLASS.pick(tint[s.rnd.nextInt(5)]));
                        if (spires % 7 == 3) {                              // one in seven has fallen
                            for (int k = 0; k < Math.min(hgt, 6); k++) if (!s.taken(x + k, s.g(x + k, z) + 1, z)) s.put(x + k, s.g(x + k, z) + 1, z, glass);
                            for (int k = 0; k < 4; k++) { int px = x + s.rnd.nextInt(5), pz = z + s.rnd.nextInt(5) - 2; if (!s.taken(px, s.g(px, pz) + 1, pz)) s.put(px, s.g(px, pz) + 1, pz, st(Blocks.GLASS_PANE)); }
                        } else {
                            for (int y = 1; y <= hgt; y++) s.put(x, g + y, z, y == 1 && hgt > 8 ? st(Blocks.SEA_LANTERN) : glass);
                            if (hgt > 5) for (int dd = 0; dd < 4; dd++) for (int y = 1; y <= hgt / 3 - (dd & 1); y++) if (!s.taken(x + DX[dd], s.g(x + DX[dd], z + DZ[dd]) + y, z + DZ[dd])) s.put(x + DX[dd], s.g(x + DX[dd], z + DZ[dd]) + y, z + DZ[dd], glass);
                            if (hgt < 7) s.put(x, g + hgt + 1, z, st(Blocks.AMETHYST_CLUSTER));
                        }
                        spires++;
                    }
                    // the five that sing, in a ring round the middle
                    for (int i = 0; i < 5; i++) {
                        double a = i * Math.PI * 2 / 5 + 0.4;
                        int x = (int) Math.round(Math.cos(a) * 7), z = (int) Math.round(Math.sin(a) * 7), g = s.g(x, z);
                        for (int y = 1; y <= 3; y++) s.put(x, g + y, z, st(Blocks.AMETHYST_BLOCK));
                        s.put(x, g + 4, z, st(Blocks.AMETHYST_CLUSTER));
                        s.part("stone", x, g + 2, z);
                    }
                    // the oasis and the thing that fell
                    pool(s, 2, 3, 3.2, 2.8, -5, 2, st(Blocks.SAND), st(Blocks.CLAY));
                    for (int x = -3; x <= 6; x++) for (int z = -1; z <= 7; z++) if (s.get(x, -5, z) == null && h(s, x, 0, z) < 190) { s.put(x, -5, z, st(Blocks.GRASS_BLOCK)); if (h(s, x, 1, z) < 90 && !s.taken(x, -4, z)) s.put(x, -4, z, st(Blocks.SHORT_GRASS)); }
                    palm(s, 6, 2, -4, 7, 1);
                    palm(s, -2, 6, -4, 8, 3);
                    palm(s, 3, 7, -4, 6, 0);
                    s.put(0, -4, -3, st(Blocks.TINTED_GLASS));
                    blob(s, 0, -3, -3, 1.3, 1.0, 1.3, h -> h < 100 ? st(Blocks.BLACKSTONE) : st(Blocks.SMOOTH_BASALT));
                    s.put(0, -2, -3, Props.STAR_IRON.get().of(0));
                    s.part("prize", 0, -2, -3);
                    s.part("cache", 1, -4, -2);
                    stilled(s, 4, -4, 5, 3, Direction.WEST);
                    for (int z = 26; z >= 8; z--) for (int x = -1; x <= 1; x++) if (h(s, x, 3, z) < 200 && !s.taken(x, s.g(x, z) + 1, z)) s.put(x, s.g(x, z), z, st(Blocks.SMOOTH_SANDSTONE));
                    wild(s, 24, 35, 9);
                    s.cam(0, 14, 50, 180, 18).cam(21, 22, 21, 135, 44).cam(0, 2, 28, 180, 12).cam(4, -4, 10, 150, -8);
                })
                .script(() -> Steps.of(Notes.of("stone", 5), Prize.at("prize")))
                .play(t -> { for (int i : Notes.order(t.seed(), 5, 5)) t.use("stone", i); t.use("prize", 0); })
                .add();

        Kind.of(FSet.PLACES, 27, "skerry_light", "The Lighthouse on the Skerry").verb(Verb.LIGHT).place().rank(0)
                .anchor(Anchor.SHORE).made(Made.BUILT)
                .see("A white tower with a red band on a rock at the water's edge. The lamp at the top is dark, and the keeper is at the rail looking out.")
                .learned("Up the stair with a light, and the lamp took. Then the bell at the end of the jetty, for anyone still out there.")
                .reach(30).icon(() -> Items.LANTERN)
                .air(Air.of(0xB7C4CC, 60).mote(() -> ParticleTypes.CLOUD, 0.15F, 6, 0.01F).loop(() -> SoundEvents.WATER_AMBIENT, 35, 0.8F, 0.8F))
                .scene(s -> {
                    s.level(-9, -8, 7, 4, 0);
                    java.util.function.IntFunction<BlockState> rock = h -> h < 60 ? st(Blocks.DEEPSLATE) : h < 110 ? st(Blocks.ANDESITE) : h < 135 ? st(Blocks.MOSSY_COBBLESTONE) : st(Blocks.STONE);
                    blob(s, 0, -1, 10, 12, 6, 10, rock);
                    Pal p = Pal.WHITE;
                    BlockState white = st(Blocks.CALCITE), red = st(Blocks.DYED_TERRACOTTA.pick(DyeColor.RED));
                    for (int y = 0; y <= 4; y++) disc(s, 0, 12, 4.4, y, st(Blocks.STONE));
                    for (int x = -5; x <= 5; x++) for (int z = 7; z <= 17; z++) { double d = Math.sqrt(x * x + (z - 12) * (z - 12)); if (d >= 3.5 && d < 4.5) for (int y = 5; y <= 24; y++) s.put(x, y, z, y == 5 ? st(Blocks.POLISHED_ANDESITE) : y >= 13 && y <= 15 ? red : h(s, x, y, z) < 40 ? st(Blocks.DIORITE) : white); else if (d < 3.5) for (int y = 5; y <= 24; y++) s.put(x, y, z, AIR); }
                    air(s, 0, 5, 8, 0, 6, 8);
                    BlockState hi = top(st(Blocks.SPRUCE_SLAB));
                    for (int y = 5; y <= 24; y++) s.put(0, y, 15, ladder(Direction.NORTH));
                    for (int y = 9; y <= 21; y += 6) for (int d = 0; d < 4; d++) s.put(DX[d] * 4, y, 12 + DZ[d] * 4, along(st(Blocks.GLASS_PANE), DX[d] == 0));
                    // the gallery, the lamp room, the cap
                    for (int x = -6; x <= 6; x++) for (int z = 6; z <= 18; z++) { double d = Math.sqrt(x * x + (z - 12) * (z - 12)); if (d < 5.6) s.put(x, 24, z, hi); if (d >= 5.0 && d < 5.9) s.put(x, 25, z, st(Blocks.IRON_BARS)); if (d >= 2.5 && d < 3.4) for (int y = 25; y <= 27; y++) s.put(x, y, z, st(Blocks.GLASS)); }
                    s.put(0, 24, 12, hi);
                    s.put(0, 24, 15, ladder(Direction.NORTH));
                    cone(s, 0, 12, 3, 28, st(Blocks.CUT_COPPER.waxed().weathered()), st(Blocks.CUT_COPPER_SLAB.waxed().weathered()));
                    s.put(0, 25, 12, st(Blocks.POLISHED_ANDESITE));
                    s.put(0, 26, 12, st(Blocks.REDSTONE_LAMP));
                    s.part("lamp", 0, 26, 12);
                    stilled(s, 0, 25, 17, 1, Direction.SOUTH);
                    // the way up the rock to the door
                    steps(s, -1, 1, -2, 2, 4, 3, st(Blocks.COBBLESTONE_STAIRS), st(Blocks.STONE));
                    fill(s, -1, 0, 2, 1, 4, 7, st(Blocks.STONE));
                    for (int z = 2; z <= 7; z++) for (int y = 5; y <= 7; y++) for (int x = -1; x <= 1; x++) if (z < 7 || x == 0) s.put(x, y, z, AIR);
                    // the keeper's cottage in the lee, the jetty and its bell, the wreck
                    house(s, -12, -11, -6, -6, 0, 3, Pal.COBBLE.roof(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB), 1, 0);
                    s.put(-11, 1, -10, st(Blocks.HAY_BLOCK));
                    s.put(-8, 1, -10, st(Blocks.BARREL));
                    s.part("cache", -8, 2, -10);
                    int sea = s.sea();
                    for (int z = 0; z <= 14; z++) { s.put(13, sea + 1, z, top(st(Blocks.SPRUCE_SLAB))); s.put(14, sea + 1, z, top(st(Blocks.SPRUCE_SLAB))); if (z % 4 == 2) for (int y = sea; y >= sea - 5; y--) s.put(13, y, z, st(Blocks.SPRUCE_LOG)); }
                    s.put(14, sea + 2, 14, st(Blocks.SPRUCE_FENCE));
                    s.put(14, sea + 3, 14, st(Blocks.BELL));
                    s.part("bell", 14, sea + 3, 14);
                    limb(s, -10, sea - 1, 6, -15, sea + 2, 12, st(Blocks.DARK_OAK_PLANKS));
                    limb(s, -11, sea - 1, 9, -9, sea + 3, 14, st(Blocks.DARK_OAK_LOG));
                    limb(s, -14, sea - 1, 8, -13, sea + 1, 15, st(Blocks.DARK_OAK_PLANKS));
                    s.spawn(EntityTypes.CAT, -7, -4, Scene.Spawn.STAY, "cat");
                    s.cam(-20, 12, -24, -38, 2).cam(18, 34, -6, 42, 38).cam(0, 5, 2, 0, -6).cam(0, 25, 16, 0, 8);
                })
                .script(() -> Steps.of(Light.all("lamp"), Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), SoundEvents.BELL_BLOCK, 3.0F, 0.8F))))
                .play(t -> t.hold(Items.LANTERN).use("lamp", 0).use("bell", 0))
                .add();

        Kind.of(FSet.PLACES, 28, "careened_ship", "The Careened Ship").verb(Verb.MEND).place().rank(1)
                .anchor(Anchor.SHORE).made(Made.STILLED)
                .see("A big ship hauled up on the beach with scaffolding along her side and a gap in her planking. The shipwrights are still on the scaffold.")
                .learned("Three planks closed the gap and the wedge came out. She settled, the way a ship does, and the captain's chest was not locked.")
                .reach(36).persists().icon(() -> Items.OAK_BOAT)
                .air(Air.of(0xC8CEC6, 70).mote(() -> ParticleTypes.CAMPFIRE_COSY_SMOKE, 0.06F, 4, 0.03F).loop(() -> SoundEvents.WATER_AMBIENT, 45, 0.6F, 0.8F).loop(() -> SoundEvents.WOOD_HIT, 80, 0.4F, 0.9F))
                .scene(s -> {
                    s.level(0, -10, 21, 5, 0);
                    BlockState hull = st(Blocks.DARK_OAK_PLANKS), spruce = st(Blocks.SPRUCE_PLANKS), deck = st(Blocks.OAK_PLANKS), fence = st(Blocks.SPRUCE_FENCE), log = st(Blocks.SPRUCE_LOG);
                    for (int x = -17; x <= 17; x++) {
                        double w = 4.4 * Math.sqrt(Math.max(0, 1 - (x / 17.6) * (x / 17.6))) + 0.4;
                        for (int o = (int) -w; o <= (int) w; o++) {
                            int bottom = 1 + (int) Math.round((o / w) * (o / w) * 3.0), out = Math.abs(o) + 1;
                            // the planking is one skin: each strake comes up to meet the foot of the next one out
                            int meet = out <= (int) w ? 1 + (int) Math.round((out / w) * (out / w) * 3.0) : bottom;
                            boolean side = Math.abs(o) == (int) w;
                            for (int y = bottom; y <= 6; y++) {
                                boolean gap = y == 3 && o == (int) w && x >= 0 && x <= 2;
                                if (gap) { s.part("plank", x, y, -9 + o); continue; }
                                if (y <= meet || side || y == 6) s.put(x, y, -9 + o, y == 6 ? deck : (x >= 3 && x <= 6 && o == (int) w && y <= 4) ? st(Blocks.BIRCH_PLANKS) : h(s, x, y, o) < 50 ? spruce : hull);
                                else s.put(x, y, -9 + o, AIR);
                            }
                            if (side && Math.abs(x) < 16) s.put(x, 7, -9 + o, fence);
                        }
                    }
                    limb(s, 17, 7, -9, 22, 9, -9, axis(log, Direction.Axis.X));
                    // the stern cabin, the wheel, the masts
                    for (int x = -16; x <= -10; x++) for (int z = -12; z <= -6; z++) { double w = 4.4 * Math.sqrt(Math.max(0, 1 - (x / 17.6) * (x / 17.6))) + 0.4; if (Math.abs(z + 9) > (int) w) continue; boolean edge = Math.abs(z + 9) == (int) w || x == -10 || x == -16; for (int y = 7; y <= 9; y++) s.put(x, y, z, edge ? (y == 8 && (x + z) % 2 == 0 ? along(st(Blocks.GLASS_PANE), x != -10 && x != -16) : spruce) : AIR); s.put(x, 10, z, st(Blocks.SPRUCE_SLAB)); }
                    air(s, -10, 7, -9, -10, 8, -9);
                    s.put(-14, 7, -9, st(Blocks.BARREL));
                    s.part("chest", -14, 7, -9);
                    s.part("cache", -13, 7, -9);
                    stilled(s, -12, 7, -8, 1, Direction.EAST);
                    s.put(-8, 7, -9, st(Blocks.SPRUCE_FENCE));
                    s.put(-8, 8, -9, Props.CART_WHEEL.get().of(0));
                    for (int y = 7; y <= 9; y++) s.put(-5, y, -9, log);
                    for (int y = 7; y <= 24; y++) s.put(3, y, -9, log);
                    for (int y = 7; y <= 11; y++) s.put(11, y, -9, log);
                    limb(s, 3, 16, -14, 3, 16, -4, axis(log, Direction.Axis.Z));
                    for (int z = -13; z <= -5; z++) if (z != -9) s.put(3, 15, z, st(Blocks.WOOL.pick(DyeColor.WHITE)));
                    ring(s, 3, -9, 1.5, 21, st(Blocks.SPRUCE_SLAB));
                    ring(s, 3, -9, 1.5, 22, fence);
                    // the scaffold along her seaward side, the props under her, the wedge
                    for (int x = -8; x <= 9; x++) { for (int lvl = 3; lvl <= 5; lvl += 2) s.put(x, lvl, -3, top(st(Blocks.SPRUCE_SLAB))); if (x % 4 == 0) for (int y = 1; y <= 6; y++) s.put(x, y, -2, fence); }
                    for (int x = -8; x <= 9; x += 4) { s.put(x, 3, -2, top(st(Blocks.SPRUCE_SLAB))); s.put(x, 5, -2, top(st(Blocks.SPRUCE_SLAB))); }
                    for (int[] k : new int[][] {{-3, 4, 1}, {2, 4, 2}, {7, 6, 3}}) stilled(s, k[0], k[1], -3, k[2], Direction.NORTH);
                    for (int x = -12; x <= 12; x += 6) { limb(s, x, 1, -2, x, 3, -4, log); limb(s, x, 1, -16, x, 3, -14, log); }
                    s.put(1, 1, -4, Props.WEDGE.get().of(0));
                    s.part("wedge", 1, 1, -4);
                    for (int i = 0; i < 3; i++) { s.put(4 + i, 1, 1, st(Blocks.BIRCH_PLANKS)); s.part("loose", 4 + i, 1, 1); }
                    // the camp: the tar pot, the saw pit, the long boat, the anchor
                    s.put(-12, 1, 2, fire(true));
                    s.put(-12, 2, 2, st(Blocks.CAULDRON));
                    for (int x = -18; x <= -14; x++) { s.put(x, 0, -1, AIR); s.put(x, -1, -1, st(Blocks.COARSE_DIRT)); }
                    limb(s, -19, 1, -1, -13, 1, -1, axis(log, Direction.Axis.X));
                    for (int k = -1; k <= 1; k++) s.put(12 + k, 1, 3, k == 0 ? spruce : stair(st(Blocks.SPRUCE_STAIRS), k < 0 ? Direction.EAST : Direction.WEST));
                    s.put(16, 1, 1, st(Blocks.ANVIL));
                    limb(s, 16, 1, 0, 16, 1, -2, st(Blocks.IRON_CHAIN));
                    for (int x = -6; x <= 0; x += 3) { s.put(x, 1, 4, st(Blocks.BARREL)); s.put(x + 1, 1, 4, Props.ROPE_COIL.get().of(0)); }
                    for (int[] k : new int[][] {{-15, 4}, {10, 5}}) s.put(k[0], 1, k[1], Props.FIRE_BASKET.get().of(1));
                    s.cam(0, 13, -46, 0, 8).cam(19, 24, 9, 135, 40).cam(1, 2, 3, 180, -12).cam(6, 7, -9, 90, 0);
                })
                .script(() -> Steps.of(Sockets.need("plank", st -> st.is(net.minecraft.tags.ItemTags.PLANKS), i -> st(Blocks.BIRCH_PLANKS)).carry("loose"),
                        Prize.at("wedge").then((c, i) -> { c.set("wedge", 0, AIR); c.sound(c.pos("wedge", 0), SoundEvents.WOOD_BREAK, 3.0F, 0.5F); }), Prize.at("chest")))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i); for (int i = 0; i < 3; i++) t.hold(Items.BIRCH_PLANKS).use("plank", i); t.use("wedge", 0).use("chest", 0); })
                .add();

        Kind.of(FSet.PLACES, 29, "hermits_rock", "The Hermit's Rock").verb(Verb.READ).place().rank(0).lands(Group.FOREST, Group.PLAINS, Group.MOUNTAIN, Group.JUNGLE)
                .anchor(Anchor.FIELD).slope(5).made(Made.STILLED)
                .see("One boulder the size of a house with a door in it, a window, and smoke coming out of the top. Somebody is sitting on the bench outside with a cup.")
                .learned("You watered the six beds, put a log in the stove and read what was open on the table. It was a list of what to plant next year.")
                .reach(26).icon(() -> Items.BOOK)
                .air(Air.of(0xCFCDB8, 90).mote(() -> ParticleTypes.CAMPFIRE_COSY_SMOKE, 0.03F, 6, 0.03F).loop(() -> SoundEvents.AMETHYST_BLOCK_CHIME, 160, 0.2F, 1.6F))
                .scene(s -> {
                    s.level(18, 8).clear(12);
                    blob(s, 0, 5, -3, 7.6, 5.6, 6.2, h -> h < 40 ? st(Blocks.MOSSY_COBBLESTONE) : h < 80 ? st(Blocks.ANDESITE) : st(Blocks.STONE));
                    air(s, -4, 1, -6, 3, 4, 0);
                    floor(s, -4, -6, 3, 0, 0, st(Blocks.SPRUCE_PLANKS), st(Blocks.OAK_PLANKS), 30);
                    fill(s, -4, 5, -6, 3, 5, 0, st(Blocks.SPRUCE_PLANKS));
                    air(s, -3, 6, -5, 2, 8, -1);
                    for (int z = 1; z <= 5; z++) for (int y = 1; y <= 2; y++) s.put(0, y, z, AIR);
                    s.put(0, 3, 3, stairT(st(Blocks.COBBLESTONE_STAIRS), Direction.NORTH));
                    for (int z = 1; z <= 6; z++) { if (s.get(3, 2, z) != null && s.get(3, 2, z + 1) == null) s.put(3, 2, z, along(st(Blocks.GLASS_PANE), true)); else s.put(3, 2, z, AIR); }
                    for (int z = 0; z <= 6; z++) { if (s.get(-1, 7, z) != null && s.get(-1, 7, z + 1) == null) s.put(-1, 7, z, along(st(Blocks.GLASS_PANE), true)); else if (s.get(-1, 7, z) != null) s.put(-1, 7, z, AIR); }
                    for (int y = 1; y <= 5; y++) s.put(3, y, -6, ladder(Direction.WEST));
                    s.put(-4, 1, -6, st(Blocks.FURNACE));
                    s.part("stove", -4, 1, -6);
                    for (int y = 2; y <= 12; y++) s.put(-4, y, -6, y > 10 ? st(Blocks.COBBLESTONE_WALL) : st(Blocks.COBBLESTONE));
                    s.put(-4, 13, -6, fire(true));
                    table(s, -2, 1, -3, 3, true, Pal.COBBLE);
                    s.put(-1, 2, -3, AIR);
                    s.put(-3, 1, -1, st(Blocks.LECTERN));
                    s.part("book", -3, 1, -1);
                    s.part("cache", -2, 1, -1);
                    s.put(2, 1, -5, st(Blocks.BARREL));
                    s.put(2, 2, -5, st(Blocks.DECORATED_POT));
                    s.put(-2, 6, -4, st(Blocks.HAY_BLOCK));
                    s.put(-1, 6, -4, st(Blocks.HAY_BLOCK));
                    s.put(-2, 7, -4, st(Blocks.CARPET.pick(DyeColor.GREEN)));
                    s.put(0, 4, -3, lantern(true, false));
                    s.put(1, 1, 3, st(Blocks.SPRUCE_LOG));
                    s.part("loose", 1, 1, 3);
                    // the garden: six beds, a well, the bench, the hens
                    net.minecraft.world.level.block.Block[] crop = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET};
                    for (int i = 0; i < 6; i++) {
                        int bx = -9 + (i % 3) * 3, bz = 8 + (i / 3) * 3;
                        for (int x = bx; x <= bx + 1; x++) for (int z = bz; z <= bz + 1; z++) s.put(x, 0, z, st(Blocks.COARSE_DIRT));
                        s.put(bx, 1, bz, st(crop[i]));
                        s.put(bx + 1, 1, bz + 1, st(Blocks.FERN));
                        s.part("bed", bx + 1, 1, bz);
                        s.put(bx + 1, 1, bz, st(Blocks.SHORT_GRASS));
                    }
                    ring(s, 6, 10, 1.5, 1, st(Blocks.MOSSY_COBBLESTONE));
                    s.put(6, 0, 10, WATER);
                    s.put(6, -1, 10, st(Blocks.COBBLESTONE));
                    s.put(4, 1, 6, stair(st(Blocks.SPRUCE_STAIRS), Direction.NORTH));
                    s.put(5, 1, 6, stair(st(Blocks.SPRUCE_STAIRS), Direction.NORTH));
                    stilled(s, 6, 1, 6, 3, Direction.SOUTH);
                    for (int i = 0; i < 60; i++) { double a = i * Math.PI / 30; int x = (int) Math.round(Math.cos(a) * 15), z = 2 + (int) Math.round(Math.sin(a) * 14); if ((Math.abs(x) > 1 || z < 0) && !s.taken(x, 1, z) && z > -7) s.put(x, 1, z, st(h(s, x, 1, z) < 80 ? Blocks.MOSSY_COBBLESTONE_WALL : Blocks.COBBLESTONE_WALL)); }
                    for (int x = 10; x <= 12; x++) for (int z = -2; z <= 0; z++) { if (x != 11 || z != -1) for (int y = 1; y <= 2; y++) s.put(x, y, z, x == 11 && z == 0 && y == 1 ? AIR : st(Blocks.SPRUCE_PLANKS)); s.put(x, 3, z, st(Blocks.SPRUCE_SLAB)); }
                    s.put(-6, 1, 5, st(Blocks.SPRUCE_FENCE));
                    s.put(-6, 2, 5, st(Blocks.SPRUCE_FENCE));
                    s.put(-6, 3, 5, st(Blocks.SPRUCE_FENCE));
                    s.put(-5, 3, 5, st(Blocks.SPRUCE_FENCE));
                    s.put(-5, 2, 5, Props.WIND_CHIME.get().of(0));
                    for (int z = 7; z <= 15; z += 2) s.put(z % 4 == 3 ? 0 : 1, 1, z, Props.FLAT_STONES.get().of(z & 3));
                    moss(s, -8, 6, -10, 8, 12, 4, 26);
                    vines(s, -9, 2, -10, 9, 9, 4, 8);
                    s.spawn(EntityTypes.CHICKEN, 10, 2, 0, "hen").spawn(EntityTypes.CHICKEN, 12, 3, 0, "hen").spawn(EntityTypes.CAT, 3, 7, Scene.Spawn.STAY, "cat");
                    wild(s, 14, 25, 7);
                    s.cam(0, 10, 36, 180, 8).cam(14, 18, 15, 135, 42).cam(0, 2, 11, 180, 0).cam(1, 2, -1, 110, 0);
                })
                .script(() -> Steps.of(Read.all("bed"), Sockets.need("stove", st -> st.is(net.minecraft.tags.ItemTags.LOGS),
                        i -> st(Blocks.FURNACE).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true)).carry("loose"), Read.all("book")))
                .play(t -> { for (int i = 0; i < 6; i++) t.use("bed", i); t.use("loose", 0).hold(Items.SPRUCE_LOG).use("stove", 0).use("book", 0); })
                .add();

        Kind.of(FSet.PLACES, 30, "spore_cathedral", "The Spore Cathedral").verb(Verb.LIGHT).place().rank(4).lands(Group.MUSHROOM, Group.DARK, Group.SWAMP)
                .anchor(Anchor.FIELD).slope(6).made(Made.ALIVE)
                .see("Two rows of mushrooms as tall as towers, their caps meeting overhead and glowing underneath, and one far bigger than the rest at the end of them.")
                .learned("Six lamps down the nave, then the climb inside the great stem. Under its cap it keeps one clear stone.")
                .reach(36).icon(() -> Items.RED_MUSHROOM)
                .air(Air.of(0x6A5080, 44).mote(() -> ParticleTypes.CRIMSON_SPORE, 1.3F, 14, -0.01F).loop(() -> SoundEvents.BEACON_AMBIENT, 100, 0.4F, 0.5F))
                .scene(s -> {
                    s.level(28, 8).clear(30);
                    ground(s, 0, 0, 27, h -> h < 150 ? st(Blocks.MYCELIUM) : h < 200 ? st(Blocks.PODZOL) : null);
                    for (int i = 0; i < 6; i++) for (int side = -1; side <= 1; side += 2) shroom(s, side * 7, 15 - i * 6, 17 + ((i * 3 + side) & 3) * 2, 5 + (i & 1), (i + (side > 0 ? 1 : 0)) % 2 == 0);
                    for (int[] k : new int[][] {{-15, -8}, {15, 6}, {-16, 10}, {16, -12}}) shroom(s, k[0], k[1], 7 + (k[0] & 3), 3, false);
                    for (int z = 26; z >= -17; z--) for (int x = -1; x <= 1; x++) if (h(s, x, 5, z) < 190) s.put(x, 0, z, st(Blocks.MUSHROOM_STEM));
                    int n = 0;
                    for (int z = 12; z >= -12; z -= 12) for (int x = -3; x <= 3; x += 6) { s.put(x, 1, z, st(Blocks.MUSHROOM_STEM)); s.put(x, 2, z, st(Blocks.MUSHROOM_STEM)); s.put(x, 3, z, Props.PAPER_LANTERN.get().of(0)); s.part("lamp", x, 3, z); n++; }
                    // the great one at the north end: hollow, a ladder up it, a room under the cap
                    BlockState stem = st(Blocks.MUSHROOM_STEM), cap = st(Blocks.RED_MUSHROOM_BLOCK);
                    for (int x = -4; x <= 4; x++) for (int z = -26; z <= -18; z++) { double d = Math.sqrt(x * x + (z + 22) * (z + 22)); if (d >= 2.5 && d < 3.5) for (int y = 1; y <= 28; y++) s.put(x, y, z, stem); else if (d < 2.5) { for (int y = 1; y <= 28; y++) s.put(x, y, z, y == 25 && !(x == 0 && z == -24) ? stem : AIR); } }
                    for (int y = 1; y <= 2; y++) s.put(0, y, -19, AIR);
                    for (int y = 1; y <= 25; y++) s.put(0, y, -24, ladder(Direction.SOUTH));
                    for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) { double d = Math.sqrt(dx * dx + dz * dz); if (d <= 9.3 && d >= 3.5) s.put(dx, 29, -22 + dz, h(s, dx, 29, dz) < 28 ? stem : cap); if (d <= 8.2) s.put(dx, 30, -22 + dz, h(s, dx, 30, dz) < 28 ? stem : cap); if (d <= 6) s.put(dx, 31, -22 + dz, cap); if (d > 8.6 && d <= 9.3) s.put(dx, 28, -22 + dz, cap); if (d > 3.4 && d < 4.4) s.put(dx, 28, -22 + dz, st(Blocks.SHROOMLIGHT)); }
                    s.put(0, 26, -21, st(Blocks.AMETHYST_BLOCK));
                    s.put(0, 27, -21, st(Blocks.AMETHYST_CLUSTER));
                    s.part("prize", 0, 27, -21);
                    s.part("cache", 1, 26, -22);
                    s.put(-1, 29, -22, st(Blocks.SHROOMLIGHT));
                    stilled(s, 2, 1, -17, 3, Direction.NORTH);
                    for (int i = 0; i < 70; i++) { int x = s.rnd.nextInt(53) - 26, z = s.rnd.nextInt(53) - 26; if (!s.taken(x, 1, z) && x * x + z * z < 700 && Math.abs(x) > 1) s.put(x, 1, z, st(i % 6 == 0 ? Blocks.RED_MUSHROOM : i % 6 == 1 ? Blocks.BROWN_MUSHROOM : i % 6 == 2 ? Blocks.CARPET.pick(DyeColor.PURPLE) : i % 6 == 3 ? Blocks.CARPET.pick(DyeColor.MAGENTA) : i % 6 == 4 ? Blocks.AMETHYST_CLUSTER : Blocks.FERN)); }
                    s.spawn(EntityTypes.MOOSHROOM, 12, 14, 0, "cow").spawn(EntityTypes.MOOSHROOM, -13, 2, 0, "cow");
                    wild(s, 24, 35, 9);
                    s.cam(0, 16, 54, 180, 2).cam(23, 42, 21, 135, 42).cam(0, 2, 25, 180, -10).cam(0, 2, 3, 180, -14);
                })
                .script(() -> Steps.of(Light.all("lamp"), Prize.at("prize")))
                .play(t -> { t.hold(Items.FLINT_AND_STEEL); for (int i = 0; i < 6; i++) t.use("lamp", i); t.use("prize", 0); })
                .add();
    }
}
