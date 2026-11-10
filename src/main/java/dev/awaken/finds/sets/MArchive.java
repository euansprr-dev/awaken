package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Olden;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The Archives (SPEC §62.8, family 3): round houses of stone shelves under a dome, where the old world wrote everything
 * down. The tablets are thrown about. You put the five that fell back in their order (the studs in the floor before
 * each niche say which is first), the reading wheel turns, and what it was guarding is yours.
 */
final class MArchive {
    private MArchive() {}

    /**
     * {@code R}: the wall's outer radius. {@code gal}: how many galleries. {@code P}: its floor. {@code dome}: 0 none,
     * 1 half fallen, 2 whole with a lantern, 3 whole but breached at its foot (the buried one). {@code wall}: 0 whole,
     * 1 ragged all round, 2 the east side down. {@code cz}: where its middle is.
     */
    record Arc(int R, int gal, int P, int dome, int wall, boolean lit, int cz) {}

    private static double deg(int x, int z) {
        return Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 360L);
    }

    static void archive(Scene s, Arc d, int reach) {
        int R = d.R(), P = d.P(), cz = d.cz(), W = P + 5 * d.gal() + 2;
        boolean buried = d.dome() == 3;
        G.platform(s, G.disc(0, cz, R + (buried ? 3 : 5)), buried ? 0 : P, reach);
        s.clear(reach);
        if (buried) for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (x * x + z * z <= (R - 1) * (R - 1)) for (int y = P + 1; y <= 0; y++) s.put(x, y, cz + z, AIR);
        // -- the floor: flagstones in rings, mosaic at the middle
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R - 0.5) continue;
                s.put(x, P, cz + z, r < 4.5 ? M.mosaic() : Math.abs(r - 7) < 0.6 || Math.abs(r - (R - 5)) < 0.6 ? M.band() : Math.floorMod(x * 3 + z * 5, 11) == 0 ? M.block() : M.flag());
                if (buried) s.put(x, P - 1, cz + z, M.stone());
            }
        // -- the wall, two thick, with what is on its inside: shelves of tablets in bays between piers, a gallery at every fifth course
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = -R - 2; z <= R + 2; z++) {
                double r = Math.sqrt(x * x + z * z), a = deg(x, z);
                int bay = (int) (a / 30), in = (int) Math.floorMod(Math.round(a), 30L);
                boolean pierLine = in <= 2 || in >= 28, door = !buried && Math.abs(a - 90) <= 7;
                if (r > R - 1.5 && r <= R + 0.5) {
                    for (int y = P + 1; y <= W; y++) {
                        int k = Math.floorMod(y - P - 1, 5);
                        boolean slit = !buried && in >= 14 && in <= 16 && k >= 1 && k <= 3 && r > R - 0.5 && y < W - 1;
                        if (door && y <= P + 5) { s.put(x, y, cz + z, y == P + 5 ? M.band() : AIR); continue; }
                        s.put(x, y, cz + z, slit ? AIR : y == W ? M.band() : k == 4 ? M.block() : M.course(y));
                    }
                } else if (r > R + 0.5 && r <= R + 1.5 && pierLine && !buried && !door) {   // a pilaster up the outside at every bay
                    for (int y = P + 1; y <= W - 2; y++) s.put(x, y, cz + z, y == P + 1 || y == P + 2 ? M.block() : Math.floorMod(y - P, 5) == 0 ? M.band() : M.stone());
                    s.put(x, W - 1, cz + z, M.capital());
                    s.put(x, W, cz + z, M.blockSlab());
                } else if (r > R - 2.5 && r <= R - 1.5 && !(door && true)) {              // the shelves
                    for (int g = 0; g <= d.gal(); g++) {
                        int base = P + 5 * g;
                        if (g == d.gal()) break;
                        for (int y = base + 1; y <= base + 4; y++) {
                            int pick = Math.floorMod(bay * 7 + (int) (a / 5) * 3 + y * 5, 9);
                            s.put(x, y, cz + z, pierLine ? M.block() : y == base + 4 ? M.band() : pick < 4 ? M.panel() : pick < 6 ? M.glyph() : pick < 8 ? M.dark() : st(Olden.WORN.get()));
                        }
                        s.put(x, base + 5, cz + z, M.block());
                    }
                } else if (r > R - 4.5 && r <= R - 2.5 && !(door && true)) {              // the galleries' walks and their rails
                    for (int g = 1; g < d.gal(); g++) {
                        int y = P + 5 * g;
                        if (in >= 20 && in <= 24 && bay == 7) continue;                   // the ladder comes up through here
                        s.put(x, y, cz + z, r > R - 3.5 ? M.flag() : M.block());
                        if (r <= R - 3.5 && Math.floorMod((int) a, 6) != 0) s.put(x, y + 1, cz + z, M.wall());
                    }
                }
            }
        // -- the ladder between the galleries, on the pier at seven o'clock
        if (d.gal() > 1) {
            double la = Math.toRadians(232);
            int lx = (int) Math.round(Math.cos(la) * (R - 3)), lz = (int) Math.round(Math.sin(la) * (R - 3));
            for (int y = P + 1; y <= P + 5 * (d.gal() - 1); y++) {
                s.put(lx, y, cz + lz, ladder(Direction.EAST));
                s.put(lx - 1, y, cz + lz, M.block());
            }
        }
        // -- the door on the south, with its porch and steps
        if (!buried) {
            for (int x = -3; x <= 3; x += 6) M.column(s, x, cz + R + 3, P + 1, P + 6);
            for (int x = -4; x <= 4; x++) for (int z = R + 1; z <= R + 4; z++) {
                s.put(x, P + 7, cz + z, Math.abs(x) == 4 || z == R + 4 ? M.band() : M.block());
                if (Math.abs(x) <= 2) s.put(x, P + 8, cz + z, Math.abs(x) == 2 ? M.blockSlab() : M.block());
                if (!s.taken(x, P, cz + z)) s.put(x, P, cz + z, M.flag());
            }
            s.put(0, P + 6, cz + R + 1, M.seal());
            int steps = G.stairs(s, 0, cz + R + 6, Direction.SOUTH, 2, P);
            path(s, 0, cz + R + 6 + steps + 10, 0, cz + R + 6 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        }
        // -- the dome
        int dome = d.dome();
        if (dome > 0) {
            M.dome(s, 0, cz, R, W + 1, 2, (x, y, z) -> {
                if (dome == 1) return x * 0.8 - z * 0.6 + (n(s, x * 1.1 + 40, z * 1.1 + y, 5.0) - 0.5) * 9 < 1.5 - y * 0.25;
                if (dome == 3) return !(z > R * 0.55 && Math.abs(x) <= 3 && y <= 7);          // the breach, low on the south
                return true;
            });
            if (dome == 2) {                                         // the lantern over the eye
                for (int q = 0; q < 8; q++) { double a = q * Math.PI / 4; M.column(s, (int) Math.round(Math.cos(a) * 3), cz + (int) Math.round(Math.sin(a) * 3), W + R, W + R + 4); }
                disc(s, 0, cz, 4.3, W + R + 5, M.block());
                disc(s, 0, cz, 2.3, W + R + 6, M.bronze());
                s.put(0, W + R + 4, cz, M.lamp(true));
                s.put(0, W + R + 7, cz, M.bronzeBand());
            }
        }
        if (d.wall() == 1) M.ruin(s, -R - 2, cz - R - 2, R + 2, cz + R + 2, W - 3, 3, W + 2);
        if (d.wall() == 2) {
            M.ruin(s, 3, cz - R - 2, R + 2, cz + R - 4, P + 4, 3, W + 2);
            M.ruin(s, -R - 2, cz - R - 2, 2, cz + R + 2, W - 2, 2, W + 2);
            for (int i = 0; i < 4; i++) M.heap(s, R + 3 + i % 2 * 3, cz - R / 2 + i * (R / 3), 3.2 + i % 3 * 0.5);
        }
        if (buried) for (int y = P + 5 * (d.gal() - 1) + 1; y <= 0; y++) s.put(0, y, cz + R - 2, ladder(Direction.NORTH));   // what the ones who dug in left
        // -- the reading wheel on its dais, the hub's light, and what it guards
        disc(s, 0, cz, 4.3, P + 1, M.block());
        disc(s, 0, cz, 3.3, P + 2, M.block());
        for (int q = 0; q < 4; q++) s.put(DX[q] * 4, P + 1, cz + DZ[q] * 4, stair(M.blockStairs(), DIR[(q + 2) % 4]));
        M.ring(s, 0, P + 8, cz, 4, 1.3, 1.3, 1, 0, 0, deg -> deg % 45 < 4 ? M.block() : M.bronze());
        for (int k = -3; k <= 3; k++) { if (k != 0) { s.put(0, P + 8 + k, cz, M.bronzeBand()); s.put(0, P + 8, cz + k, M.bronzeBand()); } }
        s.put(0, P + 8, cz, M.lamp(d.lit()));
        s.part("hub", 0, P + 8, cz);
        for (int side = -1; side <= 1; side += 2) for (int y = P + 3; y <= P + 8; y++) s.put(side * 2, y, cz, y == P + 8 ? M.capital() : M.pillar(Direction.Axis.Y));
        s.put(-1, P + 8, cz, M.bronzeBand());                       // the axle, post to post
        s.put(1, P + 8, cz, M.bronzeBand());
        s.put(0, P + 3, cz, M.seal());
        s.part("prize", 0, P + 3, cz);
        for (int q = 0; q < 4; q++) { s.put(DX[q], P + 3, cz + DZ[q], M.bronzeBand()); s.part("door", DX[q], P + 3, cz + DZ[q]); }
        // -- the five niches, each with its studs in the floor: one stud is the first, five the last
        int[] bays = {0, 1, 4, 5, 9, 10, 11, 8};
        int shift = Mon.pick(s, 33, 5);
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(bays[(i * 3 + shift) % bays.length] * 30 + 15);
            int x = (int) Math.round(Math.cos(a) * (R - 2)), z = (int) Math.round(Math.sin(a) * (R - 2));
            s.put(x, P + 2, cz + z, M.dark());
            s.part("socket", x, P + 2, cz + z);
            s.put(x, P + 3, cz + z, M.band());
            for (int k = 0; k <= i; k++) s.put((int) Math.round(Math.cos(a) * (R - 4 - k)), P, cz + (int) Math.round(Math.sin(a) * (R - 4 - k)), M.bronzeBand());
        }
        // -- the tablets that fell, lying where they were thrown: one on the dais, the rest about the floor and the door
        int[][] loose = {{3, 2}, {-R / 2, R / 3}, {R / 3, -R / 2}, {-2, R - 6}, {buried ? 4 : 2, buried ? -5 : R + 3}};
        for (int[] k : loose) {
            int y = P + 1;
            while (s.taken(k[0], y, cz + k[1]) && !s.get(k[0], y, cz + k[1]).isAir() && y < P + 6) y++;
            s.put(k[0], y, cz + k[1], M.glyph());
            s.part("loose", k[0], y, cz + k[1]);
        }
        for (int i = 0; i < R * 3; i++) {                           // and the hundreds that are past reading
            double a = h(s, i, 5, 9) / 255.0 * Math.PI * 2, r = 5 + h(s, i, 6, 9) % Math.max(1, R - 9);
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            if (s.taken(x, P + 1, cz + z)) continue;
            s.put(x, P + 1, cz + z, i % 4 == 0 ? st(Olden.FLAG_SLAB.get()) : i % 4 == 1 ? M.blockSlab() : i % 4 == 2 ? M.rubble() : M.slab());
        }
        // -- carvings: by the door, on the dais, on the top gallery
        Mon.carving(s, buried ? -3 : 4, P + 2, cz + (buried ? R - 2 : R + 1) - (buried ? 0 : 0));
        Mon.carving(s, -4, P + 2, cz - 1);
        double ca = Math.toRadians(300);
        Mon.carving(s, (int) Math.round(Math.cos(ca) * (R - 2)), P + 5 * Math.max(0, d.gal() - 1) + 2, cz + (int) Math.round(Math.sin(ca) * (R - 2)));
        if (d.lit()) for (int q = 0; q < 12; q += 2) { double a = Math.toRadians(q * 30); s.put((int) Math.round(Math.cos(a) * (R - 2)), P + 3, cz + (int) Math.round(Math.sin(a) * (R - 2)), M.lamp(true)); }
        s.put(0, P + 5, cz + 5, light(9));
        s.put(0, P + 5, cz - 5, light(9));
        M.finish(s, P + 5, 38);
        // -- what grew in: ivy and a young tree through the floor where the dome is open
        if (dome == 1 || d.wall() > 0) {
            tree(s, R / 2, cz + R / 3, P + 1, 6, st(Blocks.OAK_LOG), leaf(Blocks.OAK_LEAVES), 2.2);
            s.put(R / 2, P, cz + R / 3, st(Blocks.ROOTED_DIRT));
        }
        if (buried) {
            // earth and trees lie against the dome all round, as they planted them
            for (int q = 0; q < 6; q++) { double a = q * Math.PI / 3 + 0.5; int x = (int) Math.round(Math.cos(a) * (R + 5)), z = cz + (int) Math.round(Math.sin(a) * (R + 5)); if (z < cz + R - 4 && open(s, x, z, 2)) tree(s, x, z, s.g(x, z) + 1, 7 + q % 3, st(Blocks.OAK_LOG), leaf(Blocks.OAK_LEAVES), 2.6); }
            path(s, 0, cz + R + 12, 0, cz + R + 2, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        }
        Mon.camp(s, R + 12 > reach - 6 ? -R - 9 : R + 11, cz + R / 2 + 4);
        wild(s, Math.min(reach - 8, Math.abs(cz) + R + 8), reach - 1, 14);
        s.cam(-R - 8, P + 6, cz + R + 14, -145, -14).cam(R, W + R + 12, cz + R + 12, 140, 44).cam(0, P + 3, cz + R + 9, 180, -8).cam(0, P + 2, cz + R - 5, 180, -22);
    }

    static void init() {
        Object[][] five = {
                {1, "archive_small", "The Reading Room", new Arc(9, 1, 2, 0, 1, false, 0), 30,
                        "A small round house of stone with no roof, its wall worn down to a ragged line. Inside, a bronze wheel stands on a dais among fallen tablets.",
                        "You put the five tablets back in their niches in order. The wheel turned once, and the bars about its foot let go.",
                        "A notice by the door. It says: take one, read it, bring it back. Clean hands.",
                        "It says this room held the tablets a child should read before twelve. There were two hundred.",
                        "Scratched in a child's hand: I have read them all. Now what."},
                {2, "archive_fallen", "The Fallen Archive", new Arc(14, 2, 2, 0, 2, false, 0), 36,
                        "A round house of stone split open down one side, so that its two galleries of shelves stand in the weather like a cut honeycomb. A bronze wheel stands in the middle of the floor.",
                        "You put the five tablets back in their niches in order. The wheel turned, and the bars about its foot let go.",
                        "It says the wall came down in the year nobody was keeping count any more.",
                        "A copyist's mark, over and over, on tablet after tablet. One person copied this whole wall. Her name is on none of them.",
                        "It says: the rain is in. I am moving what I can to the dry side. I am one man and there are nine thousand."},
                {3, "archive", "The Archive", new Arc(17, 3, 2, 1, 0, false, 0), 40,
                        "A great round house of stone under half a dome. Sun pours through where the rest fell, onto three galleries of shelves and a bronze wheel on a dais, with tablets spilled all over the floor.",
                        "You put the five tablets back in their niches in order. The reading wheel turned, slowly, and the bars about its foot let go.",
                        "Everything we learned is here. We made three copies. If you can read this, the other two are gone.",
                        "The order of the shelves, cut over the door. First what is true. Then what is useful. Then what is beautiful. Last, on the highest shelf, what we were afraid of.",
                        "It says: the highest shelf is empty. They took those tablets with them when they went. All three ways took some."},
                {4, "archive_buried", "The Buried Archive", new Arc(15, 2, -12, 3, 0, false, -22), 46,
                        "A stone dome rising straight out of the ground with old trees planted close round it. Low on one side somebody has broken a way in, and a ladder goes down into the dark.",
                        "You climbed down into it and put the five tablets back in their order. The wheel turned in the dark, and the bars about its foot let go.",
                        "It says they buried this one on purpose, to keep it. They planted the trees themselves.",
                        "It says: what is in here is what we decided not to tell our children. We could not bring ourselves to break it.",
                        "A later hand, cut with a poor tool: we dug in. We read it. We wish we had not. We have left it as we found it."},
                {5, "archive_whole", "The Whole Archive", new Arc(18, 3, 3, 2, 0, true, 0), 42,
                        "A round house of stone under a whole dome with a lantern on its crown, pilasters all the way round, a porch on the south. Light shows in every window slit.",
                        "Even here five tablets were out of their places. You set them back in order. The wheel turned and the bars about its foot let go.",
                        "It says: this is the copy that was to outlast us. Whoever you are, it is yours. Read it all.",
                        "A tablet set in the wall by itself. It says: we learned that the world has layers, and that there was something under the lowest. Everything after that was an argument about what to do.",
                        "The last tablet on the last shelf. It says: three answers. Leave. Dream. Hold the door. We could not agree, so we did all three. Tell us, if you can, which was right."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Arc d = (Arc) v[3];
            int reach = (Integer) v[4];
            Mon.kind(Family.ARCHIVE, tier, (String) v[1], (String) v[2]).verb(Verb.CARRY)
                    .lands(Group.FOREST, Group.PLAINS, Group.SNOW, Group.DARK, Group.JUNGLE).slope(13)
                    .see((String) v[5]).learned((String) v[6]).reach(reach)
                    .air(Air.of(0xD6CDB4, 120).mote(() -> ParticleTypes.WHITE_ASH, 0.3F, 10, -0.004F).loop(() -> SoundEvents.BOOK_PAGE_TURN, 260, 0.2F, 0.6F))
                    .scene(s -> archive(s, d, reach))
                    .script(() -> Lore.with(Steps.of(
                                    Sockets.need("socket", st -> st.is(Olden.GLYPH.get().asItem()), i -> M.glyph()).carry("loose").inOrder(),
                                    Then.run(c -> {
                                        Mon.open(c, "door", SoundEvents.GRINDSTONE_USE);
                                        Mon.all(c, "hub", M.lamp(true));
                                        c.sound(c.pos("hub", 0), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.7F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> { for (int i = 0; i < 5; i++) t.use("loose", i).hold(Olden.GLYPH.get()).use("socket", i); t.ticks(10).use("carving", 0).use("prize", 0); })
                    .add();
        }
    }
}
