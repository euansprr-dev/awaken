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

/** Places of the plains and the swamp (SPEC §59, docs/places/BRIEFS.md 7-12). */
final class PlacesPlain {
    private PlacesPlain() {}

    private static BlockState fire(boolean lit) {
        return st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, lit);
    }

    private static Direction toward(int x, int z) {
        return Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST) : (z > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    static void init() {
        Kind.of(FSet.PLACES, 7, "stilled_wedding", "The Stilled Wedding").verb(Verb.MEND).place().rank(0).lands(Group.PLAINS, Group.FOREST)
                .anchor(Anchor.FIELD).slope(5).made(Made.STILLED)
                .see("A green set for a wedding: an arch of flowers, long tables, a ring of dancers. Everyone at it is stone, and the petals are still falling.")
                .learned("You lit their hearth, put the ring cushion back on its stand and rang the little bell for them. Nobody moved. It felt right anyway.")
                .reach(29).icon(() -> Items.PINK_PETALS)
                .air(Air.of(0xE9E2D0, 90).mote(() -> ParticleTypes.CHERRY_LEAVES, 0.7F, 9, -0.03F).loop(() -> SoundEvents.NOTE_BLOCK_HARP.value(), 90, 0.25F, 0.8F))
                .scene(s -> {
                    s.level(21, 8).clear(23);
                    Pal p = Pal.COBBLE;
                    BlockState fence = st(Blocks.OAK_FENCE), azalea = leaf(Blocks.FLOWERING_AZALEA_LEAVES);
                    for (int i = 0; i < 90; i++) {
                        int x = s.rnd.nextInt(39) - 19, z = s.rnd.nextInt(39) - 19;
                        if (x * x + z * z < 380 && !s.taken(x, 1, z)) s.put(x, 1, z, st(i % 5 == 0 ? Blocks.OXEYE_DAISY : Blocks.PINK_PETALS));
                    }
                    // the arch, and the three under it
                    for (int x = -2; x <= 2; x += 4) { for (int y = 1; y <= 3; y++) s.put(x, y, -14, fence); s.put(x, 4, -14, azalea); s.put(x, 3, -15, azalea); }
                    for (int x = -2; x <= 2; x++) { s.put(x, 5, -14, azalea); if (x != 0) s.put(x, 4, -15, azalea); }
                    s.put(-1, 4, -14, azalea);
                    s.put(1, 4, -14, azalea);
                    stilled(s, -1, 1, -13, 0, Direction.EAST);
                    stilled(s, 1, 1, -13, 2, Direction.WEST);
                    stilled(s, 0, 1, -15, 1, Direction.SOUTH);
                    s.put(2, 1, -12, fence);
                    s.part("stand", 2, 2, -12);
                    s.put(5, 1, -9, st(Blocks.CARPET.pick(DyeColor.PINK)));
                    s.part("loose", 5, 1, -9);
                    s.put(-3, 1, -12, fence);
                    s.put(-3, 2, -12, st(Blocks.BELL));
                    s.part("bell", -3, 2, -12);
                    // two long tables, most places taken
                    for (int side = -1; side <= 1; side += 2) {
                        int tx = side * 9;
                        table(s, tx, 1, -6, 12, false, p);
                        s.put(tx, 2, -3, st(Blocks.CAKE));
                        s.put(tx, 2, 2, st(Blocks.POTTED_POPPY));
                        s.put(tx, 2, -5, st(Blocks.POTTED_CORNFLOWER));
                        for (int z = -6; z <= 5; z++)
                            for (int k = -1; k <= 1; k += 2) {
                                if ((z + k + side) % 3 == 0) s.put(tx + k, 1, z, stair(st(Blocks.OAK_STAIRS), k < 0 ? Direction.WEST : Direction.EAST));
                                else stilled(s, tx + k, 1, z, (z + k) & 3, k < 0 ? Direction.EAST : Direction.WEST);
                            }
                    }
                    // the dance, and the players on their little stage
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        int x = (int) Math.round(Math.cos(a) * 4), z = 4 + (int) Math.round(Math.sin(a) * 4);
                        stilled(s, x, 1, z, i & 3, toward(-x, 4 - z));
                    }
                    fill(s, -2, 1, 11, 2, 1, 13, st(Blocks.OAK_SLAB));
                    stilled(s, -1, 2, 12, 2, Direction.NORTH);
                    stilled(s, 1, 2, 12, 3, Direction.NORTH);
                    s.put(0, 2, 13, st(Blocks.NOTE_BLOCK));
                    // the spit, the barrels, the cart of gifts
                    s.put(-15, 1, 8, fire(false));
                    s.part("hearth", -15, 1, 8);
                    for (int z = 7; z <= 9; z += 2) { s.put(-15, 1, z, fence); s.put(-15, 2, z, fence); }
                    s.put(-15, 3, 7, axis(st(Blocks.SPRUCE_LOG), Direction.Axis.Z));
                    s.put(-15, 3, 8, axis(st(Blocks.SPRUCE_LOG), Direction.Axis.Z));
                    s.put(-15, 3, 9, axis(st(Blocks.SPRUCE_LOG), Direction.Axis.Z));
                    fill(s, 14, 1, 7, 14, 1, 9, top(st(Blocks.OAK_SLAB)));
                    fill(s, 14, 2, 7, 14, 2, 9, st(Blocks.BARREL));
                    s.put(14, 3, 8, st(Blocks.BARREL));
                    fill(s, 12, 1, -11, 13, 1, -9, top(st(Blocks.OAK_SLAB)));
                    s.put(11, 1, -11, Props.CART_WHEEL.get().of(0));
                    s.put(14, 1, -9, Props.CART_WHEEL.get().of(0));
                    s.put(12, 2, -10, Props.BUNDLE.get().of(0));
                    s.put(13, 2, -11, Props.BUNDLE.get().of(2));
                    s.put(13, 2, -9, Props.BASKET.get().of(1));
                    s.part("cache", 12, 2, -11);
                    for (int i = 0; i < 6; i++) {                          // flag posts round the green, a paper lantern on every other
                        double a = i * Math.PI / 3 + 0.5;
                        int x = (int) Math.round(Math.cos(a) * 18), z = (int) Math.round(Math.sin(a) * 18);
                        for (int y = 1; y <= 5; y++) s.put(x, y, z, fence);
                        s.put(x, 6, z, i % 2 == 0 ? Props.FLAG.get().of(i & 3) : Props.PAPER_LANTERN.get().of(1));
                    }
                    path(s, 0, 24, 0, 16, 1, st(Blocks.DIRT_PATH), st(Blocks.GRASS_BLOCK));
                    s.spawn(EntityTypes.WOLF, -8, 7, Scene.Spawn.NO_AI | Scene.Spawn.QUIET, "dog");
                    s.cam(0, 14, 42, 180, 14).cam(16, 22, 15, 135, 42).cam(0, 2, 19, 180, 2).cam(0, 2, -7, 180, 0);
                })
                .script(() -> Steps.of(Light.all("hearth"), Sockets.need("stand", st -> st.is(net.minecraft.tags.ItemTags.WOOL_CARPETS), i -> st(Blocks.CARPET.pick(DyeColor.PINK))).carry("loose"),
                        Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), SoundEvents.BELL_BLOCK, 2.0F, 1.2F))))
                .play(t -> t.hold(Items.FLINT_AND_STEEL).use("hearth", 0).use("loose", 0).hold(Blocks.CARPET.pick(DyeColor.PINK)).use("stand", 0).use("bell", 0))
                .add();

        Kind.of(FSet.PLACES, 8, "barrow_field", "The Barrow Field").verb(Verb.READ).place().rank(0).lands(Group.PLAINS, Group.SNOW)
                .anchor(Anchor.FIELD).slope(5).made(Made.BUILT)
                .see("Seven round green mounds in a ring, each with a stone doorway. Six are sealed. The one in the middle stands open.")
                .learned("Four stones marked the quarters. Inside the open barrow you lit the ring of candles and the slab let itself be lifted.")
                .reach(35).icon(() -> Items.CANDLE)
                .air(Air.of(0xC9D0D2, 64).mote(() -> ParticleTypes.WHITE_ASH, 0.9F, 3, 0.003F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 520, 0.3F, 0.8F))
                .scene(s -> {
                    s.level(27, 8).clear(29);
                    java.util.function.IntFunction<BlockState> turf = h -> h < 40 ? st(Blocks.COARSE_DIRT) : h < 70 ? st(Blocks.MOSS_BLOCK) : st(Blocks.GRASS_BLOCK);
                    BlockState stone = st(Blocks.STONE), mossy = st(Blocks.MOSSY_COBBLESTONE);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3 + Math.PI / 6;
                        int x = (int) Math.round(Math.cos(a) * 20), z = (int) Math.round(Math.sin(a) * 20);
                        blob(s, x, 1, z, 4.6, 4.2, 4.6, turf);
                        int dx = (int) Math.round(-Math.cos(a) * 4.4), dz = (int) Math.round(-Math.sin(a) * 4.4), px = Math.abs(dz) > Math.abs(dx) ? 1 : 0, pz = 1 - px;
                        for (int k = -1; k <= 1; k++) for (int y = 1; y <= 3; y++) s.put(x + dx + px * k, y, z + dz + pz * k, k == 0 ? (y == 3 ? st(Blocks.TUFF) : mossy) : y == 3 ? st(Blocks.TUFF) : stone);
                    }
                    // the open barrow in the middle: a passage from the south into a round room
                    blob(s, 0, 2, -4, 7.5, 6.2, 7.5, turf);
                    for (int x = -4; x <= 4; x++) for (int z = -8; z <= 0; z++) if (x * x + (z + 4) * (z + 4) <= 15) for (int y = 0; y <= 5; y++) s.put(x, y, z, h(s, x, y, z) < 90 ? st(Blocks.ANDESITE) : st(Blocks.COBBLESTONE));
                    for (int x = -3; x <= 3; x++) for (int z = -7; z <= -1; z++) if (x * x + (z + 4) * (z + 4) <= 7) for (int y = 1; y <= 3; y++) s.put(x, y, z, AIR);
                    for (int z = -1; z <= 5; z++) {
                        for (int y = 1; y <= 2; y++) { s.put(0, y, z, AIR); s.put(-1, y, z, stone); s.put(1, y, z, stone); }
                        s.put(0, 3, z, st(Blocks.STONE_SLAB));
                        s.put(-1, 3, z, mossy);
                        s.put(1, 3, z, mossy);
                        s.put(0, 0, z, st(Blocks.COBBLESTONE));
                    }
                    for (int x = -1; x <= 1; x += 2) for (int y = 1; y <= 3; y++) s.put(x, y, 6, st(Blocks.TUFF));
                    s.put(0, 4, 6, st(Blocks.TUFF));
                    s.put(-1, 4, 6, st(Blocks.TUFF_SLAB));
                    s.put(1, 4, 6, st(Blocks.TUFF_SLAB));
                    s.put(0, 3, 6, AIR);
                    s.put(0, 1, -6, st(Blocks.SMOOTH_STONE_SLAB));
                    s.put(-1, 1, -6, st(Blocks.SMOOTH_STONE_SLAB));
                    s.put(1, 1, -6, st(Blocks.DECORATED_POT));
                    s.put(-2, 1, -4, st(Blocks.DECORATED_POT));
                    for (int[] k : new int[][] {{-1, -3}, {1, -3}, {-1, -5}, {1, -5}}) { s.put(k[0], 1, k[1], candles(2, false)); s.part("candle", k[0], 1, k[1]); }
                    s.put(0, 1, -4, Props.CAP_STONE.get().of(0));
                    s.part("slab", 0, 1, -4);
                    s.part("cache", 0, 1, -4);
                    s.put(1, 1, 5, candles(1, true));
                    stilled(s, -2, 1, 7, 3, Direction.NORTH);
                    for (int d = 0; d < 4; d++) { s.put(DX[d] * 12, 1, DZ[d] * 12 + (d == 2 ? 2 : 0), Props.STANDING_STONE.get().of(d)); s.part("stone", DX[d] * 12, 1, DZ[d] * 12 + (d == 2 ? 2 : 0)); }
                    for (double a = 0; a < Math.PI * 3.6; a += 0.035) {      // a worn way spiralling in
                        double r = 26 - a * 1.75;
                        int x = (int) Math.round(Math.cos(a + 1.3) * r), z = (int) Math.round(Math.sin(a + 1.3) * r);
                        if (!s.taken(x, 1, z) && s.get(x, 0, z) == null) s.put(x, 0, z, h(s, x, 0, z) < 150 ? st(Blocks.DIRT_PATH) : st(Blocks.COARSE_DIRT));
                    }
                    s.cam(0, 16, 48, 180, 14).cam(21, 28, 21, 135, 42).cam(0, 2, 11, 180, 2).cam(0, 1, -2, 180, 8);
                })
                .script(() -> Steps.of(Read.all("stone"), Light.all("candle"), Prize.at("slab")))
                .play(t -> { for (int i = 0; i < 4; i++) t.use("stone", i); t.hold(Items.FLINT_AND_STEEL); for (int i = 0; i < 4; i++) t.use("candle", i); t.use("slab", 0); })
                .add();

        Kind.of(FSet.PLACES, 9, "observatory", "The Observatory").verb(Verb.TURN).place().rank(3).lands(Group.PLAINS, Group.MOUNTAIN, Group.SNOW)
                .anchor(Anchor.FIELD).slope(6).made(Made.BUILT)
                .see("A pale round tower under a green copper dome with a slot in it, a brass tube pointing out at the sky. Somebody is still at the eyepiece.")
                .learned("By night the three rings went to the marks in the floor and one chart lit. It names a place a long way out.")
                .reach(23).lead().icon(() -> Items.SPYGLASS)
                .air(Air.of(0xAEB9D6, 110).mote(() -> ParticleTypes.END_ROD, 0.12F, 14, -0.003F).loop(() -> SoundEvents.BEACON_AMBIENT, 110, 0.4F, 0.7F))
                .scene(s -> {
                    s.level(16, 7).clear(18);
                    Pal p = Pal.WHITE;
                    BlockState copper = st(Blocks.CUT_COPPER.waxed().weathered()), green = st(Blocks.CUT_COPPER.waxed().oxidized()), tile = st(Blocks.DEEPSLATE_TILES);
                    ground(s, 0, 0, 14, h -> h < 150 ? st(Blocks.POLISHED_ANDESITE) : h < 200 ? st(Blocks.STONE_BRICKS) : null);
                    for (int i = 0; i < 44; i++) {
                        double a = i * Math.PI / 22;
                        int x = (int) Math.round(Math.cos(a) * 13), z = (int) Math.round(Math.sin(a) * 13);
                        if (Math.abs(x) > 1 || z < 0) s.put(x, 1, z, st(i % 5 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.DIORITE_WALL));
                    }
                    for (int d = 0; d < 4; d++) s.put((d < 2 ? 7 : -7), 1, (d % 2 == 0 ? 8 : -8), Props.STANDING_STONE.get().of(d));
                    disc(s, 0, 0, 6.6, 0, tile);
                    cyl(s, 0, 0, 7, 1, 10, p, 0);
                    for (int y = 1; y <= 3; y++) s.put(0, y, 7, AIR);
                    s.put(-1, 3, 7, stairT(st(Blocks.POLISHED_DIORITE_STAIRS), Direction.WEST));
                    s.put(1, 3, 7, stairT(st(Blocks.POLISHED_DIORITE_STAIRS), Direction.EAST));
                    for (int d = 0; d < 4; d++) { s.put(DX[d] * 7, 6, DZ[d] * 7, along(st(Blocks.GLASS_PANE), DX[d] == 0)); if (d != 2) s.put(DX[d] * 7, 3, DZ[d] * 7, along(st(Blocks.GLASS_PANE), DX[d] == 0)); }
                    ring(s, 0, 0, 7, 11, green);
                    dome(s, 0, 0, 7, 11, copper);
                    for (int z = -8; z <= -1; z++) for (int y = 12; y <= 19; y++) if (s.get(0, y, z) != null) s.put(0, y, z, AIR);   // the slot
                    BlockState brass = st(Blocks.CUT_COPPER_STAIRS.waxed().unaffected());
                    for (int k = 0; k <= 8; k++) s.put(0, 8 + k, 3 - k, stair(brass, Direction.NORTH));                         // the tube
                    s.put(0, 7, 3, st(Blocks.CUT_COPPER.waxed().unaffected()));
                    // the gallery and the stair up to it
                    BlockState lo = st(Blocks.DARK_OAK_SLAB), hi = top(lo);
                    for (int i = 0; i < 12; i++) {
                        double a = Math.toRadians(70 - i * 17);
                        s.put((int) Math.round(Math.cos(a) * 5.5), 1 + i / 2, (int) Math.round(Math.sin(a) * 5.5), i % 2 == 0 ? lo : hi);
                    }
                    for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) { double d = Math.sqrt(x * x + z * z); if (d >= 4.4 && d < 6.5 && !(x > 2 && z > 0 && z < 5)) s.put(x, 6, z, hi); }
                    for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) { double d = Math.sqrt(x * x + z * z); if (d >= 3.5 && d < 4.4) s.put(x, 7, z, st(Blocks.DARK_OAK_FENCE)); }
                    stilled(s, 1, 7, 5, 1, Direction.NORTH);
                    // the orrery and its three rings, and the marks they go to
                    s.put(0, 1, 0, st(Blocks.SMOOTH_STONE));
                    s.put(0, 2, 0, Props.ORRERY.get().of(0));
                    int[] t = Turn.targets(s.site.seed(), 3);
                    int[][] at = {{-3, 0}, {3, 0}, {0, -3}};
                    for (int i = 0; i < 3; i++) {
                        s.put(at[i][0], 1, at[i][1], Props.RING_POST.get().of(0));
                        s.part("ring", at[i][0], 1, at[i][1]);
                        s.put(at[i][0] + DX[t[i]], 0, at[i][1] + DZ[t[i]], st(Blocks.GOLD_BLOCK));
                    }
                    s.put(-4, 1, 3, st(Blocks.LECTERN));
                    s.part("chart", -4, 1, 3);
                    s.part("cache", -3, 1, 3);
                    s.put(4, 1, -3, st(Blocks.LECTERN));
                    s.put(-4, 1, -3, st(Blocks.BOOKSHELF));
                    s.put(-4, 2, -3, st(Blocks.BOOKSHELF));
                    s.put(0, 5, 0, lantern(true, true));
                    s.put(0, 6, 0, st(Blocks.IRON_CHAIN));
                    for (int y = 7; y <= 10; y++) s.put(0, y, 0, st(Blocks.IRON_CHAIN));
                    for (int x = -1; x <= 1; x += 2) lamp(s, x * 2, 1, 9, 2, st(Blocks.DIORITE_WALL), true);
                    // outside: the keeper's hut, a sundial, the old dome lying where it fell
                    house(s, 9, -12, 13, -8, 0, 2, p, 2, 0);
                    s.put(-9, 1, 9, Props.SUNDIAL.get().of(0));
                    for (int x = -13; x <= -7; x++) for (int z = -13; z <= -9; z++) for (int y = 1; y <= 4; y++) { double d = Math.sqrt((x + 10) * (x + 10) + (z + 9) * (z + 9) + (y - 1) * (y - 1)); if (d >= 3.0 && d < 4.0) s.put(x, y, z, h(s, x, y, z) < 60 ? AIR : green); }
                    path(s, 0, 18, 0, 14, 1, st(Blocks.POLISHED_ANDESITE), st(Blocks.GRAVEL));
                    s.cam(0, 12, 34, 180, 2).cam(14, 28, 14, 135, 46).cam(0, 2, 13, 180, -8).cam(3, 2, 4, 140, -18);
                })
                .script(() -> Steps.of(Turn.dials("ring").between(13000, 23000), Read.all("chart")))
                .play(t -> { t.hour(18000); int[] w = Turn.targets(t.seed(), 3); for (int i = 0; i < 3; i++) for (int k = 0; k < w[i]; k++) t.use("ring", i); t.use("chart", 0); })
                .add();

        Kind.of(FSet.PLACES, 10, "amphitheatre", "The Amphitheatre").verb(Verb.RING).place().rank(2).lands(Group.PLAINS, Group.DESERT, Group.BADLANDS)
                .anchor(Anchor.FIELD).slope(5).made(Made.BUILT)
                .see("A half bowl of pale stone seats open to the sky, and on the stage a chorus of stone singers caught with their mouths open.")
                .learned("From the mark at centre stage the four chime pillars answer each other. You found their order and the song finished.")
                .reach(32).icon(() -> Items.BELL)
                .air(Air.of(0xD9C9A8, 80).mote(() -> ParticleTypes.WHITE_ASH, 0.5F, 8, 0.004F).loop(() -> SoundEvents.NOTE_BLOCK_FLUTE.value(), 170, 0.2F, 0.7F))
                .scene(s -> {
                    s.level(24, 8).clear(26);
                    Pal p = Pal.SAND;
                    BlockState sand = st(Blocks.SAND), seat = st(Blocks.SMOOTH_SANDSTONE), cut = st(Blocks.CUT_SANDSTONE);
                    ground(s, 0, 2, 9, h -> h < 170 ? seat : cut);
                    // nine rows rising to the north, three stairways through them
                    for (int x = -23; x <= 23; x++)
                        for (int z = -22; z <= 3; z++) {
                            double d = Math.sqrt(x * x + (z - 2) * (z - 2));
                            if (d < 9 || d >= 22.5) continue;
                            int row = (int) ((d - 9) / 1.5), top = row + 1;
                            double ang = Math.atan2(-(z - 2), x);
                            boolean way = Math.abs(ang - Math.PI / 2) < 0.07 + 0.5 / d || Math.abs(ang - Math.PI / 4) < 0.5 / d + 0.03 || Math.abs(ang - Math.PI * 3 / 4) < 0.5 / d + 0.03;
                            for (int y = 1; y < top; y++) s.put(x, y, z, row >= 8 || d > 21.5 ? cut : sand);
                            Direction up = toward(x, z - 2);
                            if (way) s.put(x, top, z, stair(st(Blocks.SANDSTONE_STAIRS), up));
                            else s.put(x, top, z, ((int) ((d - 9) / 0.75)) % 2 == 0 ? stair(st(Blocks.SMOOTH_SANDSTONE_STAIRS), up) : seat);
                        }
                    // the stage and its wall of columns
                    fill(s, -9, 1, 9, 9, 1, 14, st(Blocks.SMOOTH_STONE));
                    fill(s, -8, 2, 10, 8, 2, 13, st(Blocks.SPRUCE_SLAB));
                    for (int x = -8; x <= 8; x += 4) {
                        int hgt = x == 4 ? 0 : x == -8 ? 3 : 7;
                        for (int y = 2; y <= 1 + hgt; y++) s.put(x, y, 14, y == 1 + hgt && hgt == 7 ? st(Blocks.CHISELED_SANDSTONE) : st(Blocks.QUARTZ_PILLAR));
                    }
                    fill(s, -4, 9, 14, 0, 9, 14, st(Blocks.SANDSTONE_SLAB));
                    for (int x = 3; x <= 7; x++) s.put(x, 1, 16, axis(st(Blocks.QUARTZ_PILLAR), Direction.Axis.X));
                    rubble(s, 5, 17, 3, 8, p);
                    for (int i = 0; i < 4; i++) { int x = -6 + i * 4; s.put(x, 2, 9, st(Blocks.CUT_SANDSTONE)); s.put(x, 3, 9, Props.WIND_CHIME.get().of(i)); s.part("stone", x, 3, 9); }
                    s.put(0, 2, 11, st(Blocks.CHISELED_SANDSTONE));
                    for (int i = 0; i < 12; i++) { int x = -5 + (i % 6) * 2, z = 12 + i / 6; if (!s.taken(x, 3, z)) stilled(s, x, 3, z, i & 3, Direction.NORTH); }
                    for (int i = 0; i < 11; i++) {
                        double a = Math.PI * (0.12 + 0.07 * i), d = 10.5 + (i * 5 % 8) * 1.5;
                        int x = (int) Math.round(Math.cos(a) * d), z = 2 - (int) Math.round(Math.sin(a) * d), row = (int) ((Math.sqrt(x * x + (z - 2) * (z - 2)) - 9) / 1.5);
                        if (row >= 0 && row <= 8 && !s.taken(x, row + 2, z)) stilled(s, x, row + 2, z, i & 3, Direction.SOUTH);
                    }
                    s.put(-9, 2, 9, Props.FIRE_BASKET.get().of(1));
                    s.put(9, 2, 9, Props.FIRE_BASKET.get().of(1));
                    house(s, -3, 17, 3, 21, 0, 2, p, 0, 0);
                    s.put(0, 1, 20, st(Blocks.BARREL));
                    s.part("cache", 0, 2, 20);
                    s.put(-2, 1, 20, st(Blocks.DECORATED_POT));
                    s.put(2, 1, 20, st(Blocks.CARVED_PUMPKIN));
                    path(s, 12, 24, 10, 11, 1, st(Blocks.SMOOTH_SANDSTONE), st(Blocks.SAND));
                    s.cam(0, 16, 46, 180, 14).cam(0, 32, -26, 0, 42).cam(0, 3, 10, 180, -12).cam(0, 12, -17, 0, 24);
                })
                .script(() -> Notes.of("stone", 4))
                .play(t -> { for (int i : Notes.order(t.seed(), 4, 4)) t.use("stone", i); })
                .add();

        Kind.of(FSet.PLACES, 11, "broken_aqueduct", "The Broken Aqueduct").verb(Verb.PULL).place().rank(2).lands(Group.PLAINS, Group.MOUNTAIN, Group.DESERT)
                .anchor(Anchor.FIELD).slope(7).made(Made.FALLEN)
                .see("A line of tall stone arches striding across the land, carrying water. One span has fallen, and the water pours out of the break into a pool.")
                .learned("Up the first pier, along the channel to the break, and the old gate still shut. With the fall stopped, the pool gave up what it had been covering.")
                .reach(46).persists().icon(() -> Items.STONE_BRICKS)
                .air(Air.of(0xB9D3DC, 84).mote(() -> ParticleTypes.FALLING_WATER, 0.5F, 12, -0.08F).loop(() -> SoundEvents.WATER_AMBIENT, 25, 1.0F, 0.9F))
                .scene(s -> {
                    Pal p = Pal.STONE;
                    BlockState brick = st(Blocks.STONE_BRICKS), mossy = st(Blocks.MOSSY_STONE_BRICKS), falling = WATER.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 8);
                    int[] pier = {-36, -24, -12, 0, 12, 24, 36};
                    for (int px : pier) s.level(px, 0, 5, 4, 0);
                    s.level(5, 3, 9, 5, 0);
                    for (int i = 0; i < pier.length; i++) {
                        int px = pier[i];
                        for (int x = px - 1; x <= px + 1; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 15; y++)
                            s.put(x, y, z, y <= 1 ? st(Blocks.MOSSY_COBBLESTONE) : h(s, x, y, z) < 50 ? st(Blocks.CRACKED_STONE_BRICKS) : h(s, x, y, z) < 90 + (i > 3 ? 60 : 0) ? mossy : brick);
                        for (int z = -2; z <= 2; z += 4) for (int x = px - 1; x <= px + 1; x += 2) s.put(x, 1, z, stair(st(Blocks.COBBLESTONE_STAIRS), z < 0 ? Direction.SOUTH : Direction.NORTH));
                        if (i == pier.length - 1 || i == 3) continue;      // the span from the fourth pier is the one that fell
                        for (int dx = 2; dx <= 10; dx++) {
                            int x = px + dx, yArch = 9 + (int) Math.round(Math.sqrt(Math.max(0, 25 - (dx - 6) * (dx - 6))) * 1.1);
                            for (int z = -1; z <= 1; z++) for (int y = yArch; y <= 15; y++) s.put(x, y, z, y == yArch ? (h(s, x, y, z) < 128 ? mossy : brick) : h(s, x, y, z) < 40 ? mossy : brick);
                        }
                    }
                    for (int x = -37; x <= 37; x++) {                      // the channel: walls either side, water as far as the break
                        if (x > 1 && x < 11) continue;
                        s.put(x, 16, -1, h(s, x, 16, -1) < 50 ? mossy : brick);
                        s.put(x, 16, 1, h(s, x, 16, 1) < 50 ? mossy : brick);
                        if (x <= 1) { s.put(x, 16, 0, WATER); if (x >= -1) s.part("run", x, 16, 0); }
                        else if (h(s, x, 17, 0) < 110) s.put(x, 16, 0, st(Blocks.MOSS_CARPET));
                    }
                    s.put(-38, 16, 0, brick);
                    s.put(2, 16, 0, WATER.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1));
                    s.part("fall", 2, 16, 0);
                    for (int y = 15; y >= 1; y--) { s.put(2, y, 0, falling); s.part("fall", 2, y, 0); }
                    pool(s, 5, 2, 6.0, 5.0, 0, 2, st(Blocks.GRAVEL), st(Blocks.CLAY));
                    pool(s, 8, 9, 3.6, 2.6, 0, 1, st(Blocks.MUD), st(Blocks.CLAY));
                    blob(s, 8, 0, -3, 2.4, 1.6, 2.0, h -> h < 110 ? mossy : brick);
                    blob(s, 10, 1, 4, 1.8, 1.4, 2.2, h -> h < 110 ? mossy : brick);
                    rubble(s, 6, 0, 7, 16, p);
                    s.put(-2, 17, 1, Props.WINCH.get().of(0));
                    s.part("gate", -2, 17, 1);
                    s.put(9, 1, -2, Props.FOOTING.get().of(0));
                    s.part("cache", 9, 1, -2);
                    stilled(s, -3, 17, -1, 1, Direction.EAST);
                    for (int y = 1; y <= 16; y++) s.put(-36, y, 2, ladder(Direction.SOUTH));
                    s.put(-36, 16, 1, AIR);
                    house(s, -34, 5, -29, 9, 0, 3, Pal.COBBLE, 0, 0);
                    for (int i = 4; i < pier.length; i++) vines(s, pier[i] - 2, 2, -2, pier[i] + 2, 15, 2, 30);
                    vines(s, 12, 9, -2, 36, 16, 2, 22);
                    for (int i = 0; i < 10; i++) { int x = 3 + s.rnd.nextInt(10), z = 6 + s.rnd.nextInt(7); if (!s.taken(x, 1, z) && s.get(x, 0, z) == null) s.put(x, 1, z, st(i % 2 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS)); }
                    s.spawn(EntityTypes.FROG, 8, 7, 0, "frog");
                    s.cam(0, 18, 50, 180, 4).cam(18, 30, 20, 150, 38).cam(-36, 2, 12, 180, -20).cam(-6, 17, 0, -90, 6);
                })
                .script(() -> Prize.at("gate").then((c, i) -> { PlacesWood.fillPart(c, "fall"); PlacesWood.fillPart(c, "run"); c.sound(c.pos("gate", 0), SoundEvents.IRON_DOOR_CLOSE, 2.0F, 0.6F); }))
                .play(t -> t.use("gate", 0))
                .add();

        Kind.of(FSet.PLACES, 12, "drowned_chapel", "The Drowned Chapel").verb(Verb.LIGHT).place().rank(1).lands(Group.SWAMP)
                .anchor(Anchor.FIELD).slope(5).made(Made.FALLEN)
                .see("A roofless stone chapel and its leaning tower standing in black water, green at the windows. There are still people in the pews.")
                .learned("Candles on the altar, the bell rung from its rope, and the bars over the crypt stair let go. Three tombs, and one of them not empty.")
                .reach(34).icon(() -> Items.SOUL_LANTERN)
                .air(Air.of(0x4E6B4A, 40).mote(() -> ParticleTypes.FIREFLY, 0.5F, 6, 0.0F).loop(() -> SoundEvents.FROG_AMBIENT, 90, 0.6F, 1F).loop(() -> SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 55, 0.6F, 0.8F))
                .scene(s -> {
                    s.level(26, 8).clear(28);
                    Pal p = Pal.RUIN;
                    BlockState brick = st(Blocks.STONE_BRICKS), mossy = st(Blocks.MOSSY_STONE_BRICKS), oak = st(Blocks.DARK_OAK_PLANKS);
                    pool(s, 0, 0, 21.5, 21.5, 0, 2, st(Blocks.MUD), st(Blocks.CLAY));
                    // sealed stone under the nave, and the crypt inside it
                    fill(s, -5, -10, -15, 5, -2, 11, brick);
                    air(s, -3, -8, -9, 3, -5, -3);
                    floor(s, -3, -9, 3, -3, -9, st(Blocks.DEEPSLATE_TILES), st(Blocks.COBBLED_DEEPSLATE), 30);
                    // the nave: its floor one under water
                    floor(s, -3, -9, 3, 9, -1, mossy, st(Blocks.CRACKED_STONE_BRICKS), 40);
                    fill(s, -3, 0, -9, 3, 0, 9, WATER);
                    air(s, -3, 1, -9, 3, 12, 9);
                    wall(s, -4, -10, -4, 10, -1, 7, p, 3);
                    wall(s, 4, -10, 4, 10, -1, 7, p, 3);
                    wall(s, -4, 10, 4, 10, -1, 7, p, 0);
                    for (int k = 1; k <= 4; k++) fill(s, -4 + k, 7 + k, 10, 4 - k, 7 + k, 10, k % 2 == 0 ? mossy : brick);
                    air(s, 0, 1, 10, 0, 3, 10);
                    s.put(0, 0, 10, WATER);
                    s.put(0, 6, 10, along(st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.GREEN)), true));
                    s.put(0, 7, 10, along(st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.GREEN)), true));
                    for (int z = -7; z <= 7; z += 4)
                        for (int x = -4; x <= 4; x += 8) {
                            for (int y = 3; y <= 5; y++) if (s.get(x, y, z) != null) s.put(x, y, z, z == -3 ? along(st(Blocks.STAINED_GLASS_PANE.pick(DyeColor.LIME)), false) : AIR);
                            if (s.get(x, 6, z) != null) s.put(x, 6, z, stairT(st(Blocks.STONE_BRICK_STAIRS), Direction.NORTH));
                        }
                    for (int z = -6; z <= 6; z += 6) {
                        for (int x = -2; x <= 2; x += 4) pillar(s, x, z, -1, 2 + (h(s, x, 0, z) & 3), brick, st(Blocks.CHISELED_STONE_BRICKS));
                        for (int x = -4; x <= 4; x++) s.put(x, 8, z, axis(st(Blocks.DARK_OAK_LOG), Direction.Axis.X));
                        for (int k = 1; k <= 4; k++) { s.put(-4 + k, 8 + k, z, oak); s.put(4 - k, 8 + k, z, oak); }
                        s.put(0, 7, z, st(Blocks.IRON_CHAIN));
                        s.put(0, 6, z, lantern(true, true));
                    }
                    // the apse: dry, raised, the altar on it, and the hole to the crypt
                    for (int x = -4; x <= 4; x++)
                        for (int z = -15; z <= -10; z++) {
                            double d = Math.sqrt(x * x + (z + 10) * (z + 10));
                            if (d > 4.6) continue;
                            for (int y = -1; y <= 1; y++) s.put(x, y, z, y == 1 ? (h(s, x, y, z) < 90 ? mossy : brick) : brick);
                            if (d >= 3.6) for (int y = 2; y <= 6 - (h(s, x, 3, z) & 3); y++) s.put(x, y, z, h(s, x, y, z) < 80 ? mossy : brick);
                            else for (int y = 2; y <= 8; y++) s.put(x, y, z, AIR);
                        }
                    for (int x = -2; x <= 2; x++) { for (int y = 1; y <= 5; y++) s.put(x, y, -10, AIR); s.put(x, 0, -10, brick); s.put(x, 1, -10, stair(st(Blocks.STONE_BRICK_STAIRS), Direction.NORTH)); s.put(x, 0, -9, stair(st(Blocks.STONE_BRICK_STAIRS), Direction.NORTH)); }
                    s.put(0, 2, -13, st(Blocks.CHISELED_STONE_BRICKS));
                    for (int x = -1; x <= 1; x++) { s.put(x, x == 0 ? 3 : 2, -13, candles(x == 0 ? 3 : 2, false)); s.part("candle", x, x == 0 ? 3 : 2, -13); }
                    s.put(-1, 2, -14, AIR);
                    stilled(s, 0, 2, -12, 1, Direction.SOUTH);
                    for (int y = -8; y <= 1; y++) { s.put(3, y, -12, ladder(Direction.SOUTH)); s.put(3, y, -13, brick); }
                    air(s, 3, -8, -11, 3, -7, -10);
                    for (int y = -8; y <= -7; y++) { s.put(3, y, -10, along(st(Blocks.IRON_BARS), true)); s.part("door", 3, y, -10); }
                    for (int x = -2; x <= 2; x += 2) { s.put(x, -8, -4, st(Blocks.SMOOTH_STONE)); s.put(x, -8, -5, st(Blocks.SMOOTH_STONE)); s.put(x, -7, -4, st(Blocks.SMOOTH_STONE_SLAB)); s.put(x, -7, -5, st(Blocks.CHISELED_STONE_BRICKS)); }
                    s.part("cache", 0, -8, -7);
                    s.put(-3, -6, -6, lantern(false, true));
                    s.put(-3, -7, -6, brick);
                    s.put(3, -8, -6, lantern(false, true));
                    // the tower, leaning a block above its waist, the bell rope hanging to the water
                    for (int y = -1; y <= 16; y++) {
                        int lean = y >= 9 ? -1 : 0;
                        for (int x = -9; x <= -5; x++) for (int z = 6; z <= 10; z++) {
                            boolean shell = x == -9 || x == -5 || z == 6 || z == 10;
                            if (shell) s.put(x + lean, y, z, y == -1 ? st(Blocks.MOSSY_COBBLESTONE) : h(s, x, y, z) < 90 ? mossy : h(s, x, y, z) < 130 ? st(Blocks.CRACKED_STONE_BRICKS) : brick);
                            else if (y >= 1) s.put(x + lean, y, z, AIR);
                            else s.put(x, y, z, y == -1 ? brick : WATER);
                        }
                    }
                    for (int x = -10; x <= -6; x++) for (int z = 6; z <= 10; z++) s.put(x, 17, z, (x == -10 || x == -6) && (z == 6 || z == 10) ? brick : st(Blocks.STONE_BRICK_SLAB));
                    for (int z = 7; z <= 9; z++) { s.put(-10, 14, z, AIR); s.put(-6, 14, z, AIR); }
                    s.put(-8, 16, 8, st(Blocks.BELL).setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
                    for (int y = 9; y <= 15; y++) s.put(-8, y, 8, st(Blocks.IRON_CHAIN));
                    for (int y = 2; y <= 8; y++) s.put(-7, y, 8, st(Blocks.IRON_CHAIN));
                    s.put(-8, 9, 8, st(Blocks.IRON_CHAIN));
                    s.put(-7, 9, 8, st(Blocks.IRON_CHAIN));
                    s.part("rope", -7, 2, 8);
                    for (int x = -5; x <= -4; x++) { s.put(x, 0, 8, WATER); s.put(x, 1, 8, AIR); s.put(x, 2, 8, AIR); }
                    // the causeway, the graves, what grows
                    for (int z : new int[] {11, 12, 13, 15, 17, 18, 20, 21}) { s.put(0, 0, z, st(Blocks.MOSSY_COBBLESTONE)); s.put(0, -1, z, st(Blocks.COBBLESTONE)); }
                    for (int[] k : new int[][] {{8, 4}, {10, -3}, {7, -9}, {-11, -4}, {-12, 14}, {9, 12}}) { s.put(k[0], 0, k[1], st(Blocks.MUD)); s.put(k[0], -1, k[1], st(Blocks.MUD)); s.put(k[0], 1, k[1], Props.STANDING_STONE.get().of(k[0] & 3)); }
                    for (int[] k : new int[][] {{-2, -2}, {2, -2}, {-2, 3}, {2, 3}, {-2, 7}, {2, 6}}) stilled(s, k[0], 0, k[1], k[1] & 3, Direction.NORTH);
                    for (int i = 0; i < 26; i++) { int x = s.rnd.nextInt(39) - 19, z = s.rnd.nextInt(39) - 19; if (s.get(x, 0, z) == WATER && !s.taken(x, 1, z) && (Math.abs(x) > 5 || Math.abs(z) > 11)) s.put(x, 1, z, st(Blocks.LILY_PAD)); }
                    for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; int x = (int) Math.round(Math.cos(a) * 22.5), z = (int) Math.round(Math.sin(a) * 22.5); if (!s.taken(x, 1, z)) s.put(x, 1, z, st(i % 3 == 0 ? Blocks.MANGROVE_ROOTS : Blocks.FERN)); }
                    vines(s, -6, 1, -16, 6, 9, 11, 16);
                    moss(s, -10, 1, -16, 5, 9, 11, 30);
                    s.put(0, 3, 0, light(5));
                    s.spawn(EntityTypes.FROG, 12, 20, 0, "frog").spawn(EntityTypes.FROG, -14, 18, 0, "frog");
                    s.cam(0, 12, 46, 180, 8).cam(17, 24, 17, 135, 40).cam(0, 1, 17, 180, -4).cam(1, 2, -11, 0, 6);
                })
                .script(() -> Steps.of(Light.all("candle"), Prize.at("rope").then((c, i) -> c.sound(c.pos("rope", 0), SoundEvents.BELL_BLOCK, 3.0F, 0.6F)), Prize.at("door").then((c, i) -> PlacesWood.fillPart(c, "door"))))
                .play(t -> { t.hold(Items.FLINT_AND_STEEL); for (int i = 0; i < 3; i++) t.use("candle", i); t.use("rope", 0).use("door", 0); })
                .add();
    }
}
