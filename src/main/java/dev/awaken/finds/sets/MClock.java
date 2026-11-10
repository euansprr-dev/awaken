package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

import static dev.awaken.finds.kit.B.*;

/**
 * The Water Clocks (SPEC §62.8, family 9): stepped mounts that counted how long the sleepers would sleep, by water run
 * down their faces from a cistern on top. They stopped. You climb the stair and open the sluices from the top down;
 * real water runs tier to tier down the south face into the basin, the works take up again and the vault at the foot
 * lets go. Hollow: a mount is its walls and its ledges.
 */
final class MClock {
    private MClock() {}

    /**
     * {@code ox}: where its middle is along x. {@code n}: tiers. {@code B}: half its base. {@code th}: a tier's height.
     * {@code works}: it is the one with sluices, basin, stair and vault. {@code stair}: +1 the stair climbs its east
     * face, -1 its west. {@code state}: 0 worn, 1 its east side slid away, 2 whole (and its north face already runs).
     */
    record Mount(int ox, int n, int B, int th, boolean works, int stair, int state) {
        int half(int k) { return B - 3 * k; }
        int top(int k) { return (k + 1) * th; }
        int H() { return n * th; }
    }

    static void mount(Scene s, Mount d) {
        int ox = d.ox(), n = d.n(), th = d.th(), hT = d.half(n - 1), yT = d.H();
        // -- the tiers: a wall two thick with pilasters and a band, and a ledge three wide with a groove down the middle of each face
        for (int k = 0; k < n; k++) {
            int hk = d.half(k), y0 = 1 + k * th, y1 = d.top(k);
            for (int x = -hk - 1; x <= hk + 1; x++)
                for (int z = -hk - 1; z <= hk + 1; z++) {
                    int m = Math.max(Math.abs(x), Math.abs(z)), along = Math.abs(x) > Math.abs(z) ? z : x;
                    if (m == hk + 1) {                             // pilasters stand proud of the wall, clear of the corners and the grooves
                        if (Math.abs(x) == Math.abs(z) || Math.floorMod(along, 6) != 3 || Math.abs(along) < 2) continue;
                        for (int y = k == 0 ? Math.min(0, s.g(ox + x, z)) : y0; y <= y1 - 1; y++) s.put(ox + x, y, z, y == y1 - 1 ? M.capital() : M.block());
                        continue;
                    }
                    if (m > hk) continue;
                    boolean roof = k == n - 1;
                    if (m >= hk - 1) {
                        for (int y = y0; y <= y1; y++) {
                            boolean mark = y == y0 + 1 && m == hk && Math.abs(along) >= 3 && Math.floorMod(along, 2) == 0;
                            s.put(ox + x, y, z, y == y1 ? M.block() : y == y1 - 1 ? M.band() : mark ? M.glyph() : M.course(y));
                        }
                    } else if (m >= hk - 4 || roof) {            // the ledge, and under the next tier's wall, which stands on it
                        s.put(ox + x, y1, z, Math.floorMod(x + z, 5) == 0 ? M.block() : M.flag());
                        s.put(ox + x, y1 - 1, z, M.stone());
                    }
                    // the groove: on the ledge, in the middle of each face, one deep
                    if (!roof && m >= hk - 2 && along == 0) s.put(ox + x, y1, z, AIR);
                }
        }
        // -- the cistern on top: a rim, water inside, a notch in the middle of each side
        for (int x = -hT; x <= hT; x++)
            for (int z = -hT; z <= hT; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m == hT) s.put(ox + x, yT + 1, z, Math.abs(x) == Math.abs(z) ? M.block() : M.band());
                else s.put(ox + x, yT + 1, z, WATER);
            }
        for (int q = 0; q < 4; q++) { int cx = (q < 2 ? -hT : hT), cz = (q % 2 == 0 ? -hT : hT); s.put(ox + cx, yT + 2, cz, M.block()); s.put(ox + cx, yT + 3, cz, d.state() == 2 ? M.lamp(true) : M.blockSlab()); }
        if (d.state() == 2) {                                      // the north face still runs: its notch is open, and the water is set going
            s.put(ox, yT + 1, -hT, WATER);
            s.flow(ox, yT + 1, -hT);
            for (int x = -4; x <= 4; x++) for (int z = -d.B() - 9; z <= -d.B() - 1; z++) { double r = Math.hypot(x, z + d.B() + 5); if (r <= 4.3) { s.put(ox + x, 0, z, r > 3.3 ? M.band() : WATER); if (r <= 3.3) s.put(ox + x, -1, z, M.mosaic()); } }
            s.put(ox, 0, -d.B() - 1, AIR);
            s.put(ox, -1, -d.B() - 1, M.stone());
        }
        if (!d.works()) return;
        // -- the sluices, top down: the cistern's south notch, then a gate at the lip of every ledge below
        s.put(ox, yT + 1, hT, M.bronze());
        s.part("sluice", ox, yT + 1, hT);
        for (int k = n - 2; k >= 0; k--) {
            s.put(ox, d.top(k), d.half(k), M.bronze());
            s.part("sluice", ox, d.top(k), d.half(k));
        }
        // -- the run at the foot, the basin, the float-wheel standing in it
        int B = d.B(), bz = B + 9;
        for (int z = B + 1; z <= bz - 4; z++) { s.put(ox, 0, z, AIR); s.put(ox, -1, z, M.stone()); s.put(ox - 1, 0, z, M.band()); s.put(ox + 1, 0, z, M.band()); }
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 5.3) continue;
                s.put(ox + x, 0, bz + z, r > 4.2 ? (Math.floorMod(x + z, 3) == 0 ? M.block() : M.band()) : WATER);
                if (r <= 4.2) s.put(ox + x, -1, bz + z, Math.floorMod(x * 3 + z, 5) == 0 ? M.block() : M.mosaic());
            }
        s.put(ox, 0, bz - 5, AIR);
        s.put(ox, 0, bz - 4, WATER);
        M.ring(s, ox + 3, 5, bz, 4, 1.3, 1.3, 1, 0, 0, deg -> deg % 45 < 6 ? M.bronzeBand() : M.bronze());
        for (int k = -3; k <= 3; k++) { s.put(ox + 3, 5 + k, bz, M.bronzeBand()); s.put(ox + 3, 5, bz + k, M.bronzeBand()); }
        for (int side = -1; side <= 1; side += 2) for (int y = 0; y <= 5; y++) s.put(ox + 3 + side * 2, y, bz, y == 5 ? M.capital() : M.pillar(Direction.Axis.Y));
        s.put(ox + 2, 5, bz, M.bronzeBand());
        s.put(ox + 4, 5, bz, M.bronzeBand());
        s.put(ox + 3, 5, bz, M.lamp(false));
        s.part("marker", ox + 3, 5, bz);
        // -- the vault at the foot of the south face: a bronze door, a small room in the mount's hollow
        Mon.carving(s, ox + 6, 1, bz - 1);                          // by the basin: the one that says which way to open them
        int vx = ox - 7, vz = B;
        M.shell(s, vx - 3, 1, vz - 8, vx + 3, 5, vz - 1);
        for (int x = -3; x <= 3; x++) for (int z = -8; z <= -1; z++) { s.put(vx + x, 6, vz + z, M.block()); s.put(vx + x, 0, vz + z, Math.abs(x) + Math.abs(z + 4) < 2 ? M.mosaic() : M.flag()); }
        air(s, vx - 2, 1, vz - 7, vx + 2, 5, vz - 2);
        for (int y = 1; y <= 3; y++) for (int z = vz - 1; z <= vz; z++) for (int x = -1; x <= 1; x++) {
            if (x != 0) { s.put(vx + x, y, vz, M.bronzeBand()); continue; }
            s.put(vx, y, z, z == vz ? M.door() : AIR);
            if (z == vz) s.part("door", vx, y, vz);
        }
        s.put(vx, 4, vz, M.band());
        s.put(vx, 5, vz, M.seal());
        Mon.prize(s, vx, 1, vz - 6, Direction.SOUTH);
        s.put(vx, 5, vz - 4, M.lamp(true));
        Mon.carving(s, vx + 3, 2, vz - 4);
        // -- the stair: it stands out from the face and climbs to the cistern's rim, five wide, a step off at every ledge
        int dir = d.stair();
        if (dir != 0)
            for (int j = 0; yT + 1 - j >= 1; j++) {
                int x = ox + dir * (hT + 1 + j), y = yT + 1 - j;
                for (int z = -2; z <= 2; z++) {
                    s.put(x, y, z, M.step(s, x, z, dir > 0 ? Direction.WEST : Direction.EAST));
                    for (int yy = y - 1; yy >= Math.min(0, s.g(x, z)) - 1; yy--) {
                        if (s.taken(x, yy, z) && !s.get(x, yy, z).isAir() && s.get(x, yy, z).canOcclude()) break;
                        s.put(x, yy, z, Math.abs(z) == 2 && yy % 4 == 0 ? M.band() : M.course(yy));
                    }
                    for (int yy = y + 1; yy <= y + 3; yy++) if (s.taken(x, yy, z)) s.put(x, yy, z, AIR);
                }
            }
        Mon.carving(s, ox + hT - 1, yT + 2, -hT);
    }

    private static void site(Scene s, Mount main, Mount twin, int reach) {
        int B = main.B();
        int x0 = Math.min(main.ox(), twin == null ? main.ox() : twin.ox()) - B - 4 - (main.stair() < 0 ? main.H() - (B - main.half(main.n() - 1)) + 2 : 0);
        int x1 = Math.max(main.ox(), twin == null ? main.ox() : twin.ox()) + B + 4 + (main.stair() > 0 ? main.H() - (B - main.half(main.n() - 1)) + 2 : 0);
        G.platform(s, G.box(Math.max(-reach + 4, x0), Math.max(-reach + 4, -B - (main.state() == 2 ? 12 : 4)), Math.min(reach - 4, x1), Math.min(reach - 4, B + 16)), 0, reach);
        s.clear(reach);
        mount(s, main);
        if (twin != null) {
            mount(s, twin);
            // the two were joined at the top by a channel on arches; its middle has gone
            int y = Math.min(main.H(), twin.H()), a = main.ox() + main.half(main.n() - 1) + 1, b = twin.ox() - twin.half(twin.n() - 1) - 1;
            for (int x = a; x <= b; x++) {
                if (Math.abs(x - (a + b) / 2) <= 2) continue;
                double t = (x - a) / (double) Math.max(1, b - a);
                int under = M.haunch(t, 5);
                for (int z = -1; z <= 1; z++) { for (int yy = y - 1 - under; yy <= y; yy++) s.put(x, yy, z, yy == y && z == 0 ? AIR : M.course(yy)); s.put(x, y + 1, z, z == 0 ? AIR : M.wall()); }
            }
            M.heap(s, (a + b) / 2, 3, 3.4);
        }
        if (main.state() == 1) {
            // its east side slid away: a ragged slope of what is left, and the mount's hollow open to the sky
            for (int x = main.ox() + 5; x <= main.ox() + B + 2; x++) M.ruin(s, x, -B - 2, x, B - 6, Math.max(2, main.H() - (int) ((x - main.ox() - 5) * 1.5)), 2, main.H() + 4);
            for (int i = 0; i < 6; i++) M.heap(s, main.ox() + B + 1 + i % 3 * 2, -B + 4 + i * (B / 4), 3.0 + i % 3 * 0.7);
        } else if (main.state() == 0) {
            int c = Mon.pick(s, 44, 2) == 0 ? -1 : 1;              // one back corner has slumped; which, by the site
            M.ruin(s, c < 0 ? main.ox() - B - 2 : main.ox() + B / 3, -B - 2, c < 0 ? main.ox() - B / 3 : main.ox() + B + 2, -B / 3, main.th() + 1, 3, main.H() / 2 + 2);
            M.heap(s, main.ox() + c * (B + 2), -B + 2, 4.0);
            M.heap(s, main.ox() + c * (B - 4), -B - 3, 3.2);
        }
        M.finish(s, 9, 40);
        // trees that took root on its ledges, and what later people left by the basin
        for (int k = 1; k < main.n() - 1; k += 2) {
            int hk = main.half(k), x = main.ox() + (k % 4 == 1 ? -hk + 1 : hk - 1), z = -hk / 2 + k;
            if (!s.taken(x, main.top(k) + 1, z) && s.taken(x, main.top(k), z)) tree(s, x, z, main.top(k) + 1, 5 + k % 3, st(Blocks.JUNGLE_LOG), leaf(Blocks.JUNGLE_LEAVES), 2.2);
        }
        int steps = G.stairs(s, main.ox(), Math.min(reach - 4, B + 16) + 1, Direction.SOUTH, 2, 0);
        Mon.camp(s, main.ox() - 14, Math.min(reach - 6, B + 10));
        wild(s, Math.min(reach - 6, B + 14), reach - 1, 14);
        s.cam(main.ox() - B - 6, 8, B + 16, -140, -22).cam(main.ox() + B, main.H() + 22, B + 18, 140, 42).cam(main.ox() + 8, 3, B + 14, 170, -14).cam(main.ox() + 2, main.H() + 3, main.half(main.n() - 1) + 3, 180, 30);
    }

    static void init() {
        Object[][] five = {
                {1, "clock_small", "The Small Count", new Mount(0, 3, 11, 3, true, 1, 0), null, 28,
                        "A stepped stone mount three tiers high with a stair up one side and a dry groove down the middle of each face. At its foot a bronze wheel stands in an empty basin.",
                        "You opened the three sluices from the top down. Water ran tier to tier into the basin, the wheel's light came on, and the vault at the foot let go.",
                        "It says: open the gates from the top down. Never the other way, or it floods the works.",
                        "It says this one counts a single year. They built it to prove the idea before they trusted it with more.",
                        "Scratched under the cistern: it ran dry in the first summer. We made the next one bigger."},
                {2, "clock_fallen", "The Fallen Count", new Mount(0, 5, 20, 4, true, -1, 1), null, 40,
                        "A stepped mount with one whole side slid away, so that you can see it was hollow all along. Its south face still stands, grooved from top to bottom, over a basin and a bronze wheel.",
                        "You opened the five sluices from the top down. Water came down the standing face into the basin, and the vault at its foot let go.",
                        "It says: open the gates from the top down, and it will begin to count again.",
                        "It says the mount slid in a wet spring and the count was lost. Nobody alive knew what number it had reached.",
                        "It says: we started it again from one. It seemed better than nothing. We do not know how wrong it is."},
                {3, "clock", "The Water Clock", new Mount(0, 7, 26, 4, true, 1, 0), null, 46,
                        "A stepped stone mount seven tiers high rising out of the trees, a cistern on its top and a dry groove down the middle of every face. A great stair stands out from its east side. At its foot, a bronze wheel in a basin.",
                        "You climbed the stair and opened the seven sluices from the top down. Water ran tier to tier down the south face into the basin. The wheel's light came on, and the vault at the foot let go.",
                        "It says: open the gates from the top down, and it will begin to count again.",
                        "It will wake us when the count is done. If the water stops, start it again. Please.",
                        "A row of marks, one for every hundred years. Nine are filled with bronze. The tenth is empty."},
                {4, "clock_twin", "The Twin Counts", new Mount(-20, 4, 14, 4, true, -1, 0), new Mount(20, 4, 14, 4, false, 0, 0), 48,
                        "Two stepped mounts stand side by side, joined at the top by a channel on arches with its middle gone. One has a basin and a bronze wheel at its foot. The other is dry.",
                        "You opened the four sluices of the western mount from the top down. Its face ran, its wheel lit, and the vault at its foot let go. The other mount stayed dry.",
                        "It says: open the gates from the top down, and it will begin to count again.",
                        "It says one of these counts for the sleepers and one for the world. When the two agree, it is time to wake. They have never agreed.",
                        "It says: the dry one is the world's. It stopped first."},
                {5, "clock_whole", "The Clock That Still Counts", new Mount(0, 8, 29, 4, true, 1, 2), null, 50,
                        "A stepped mount eight tiers high, whole, a lamp at every corner of its top. Water is running down its north face into a basin. The south face is dry, and its sluices are shut.",
                        "You opened the eight south sluices from the top down. Now two faces run. The wheel's light came on, and the vault at the foot let go.",
                        "It says: one face runs. Open the other from the top down and the count is whole.",
                        "It says this one never stopped. Whoever kept it running did not cut their name. There are a great many fresh marks.",
                        "The last mark is new. Under it: almost. I think almost. If I am wrong, whoever reads this, keep it running."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mount d = (Mount) v[3], twin = (Mount) v[4];
            int reach = (Integer) v[5];
            Mon.kind(Family.CLOCK, tier, (String) v[1], (String) v[2]).verb(Verb.POUR)
                    .lands(Group.JUNGLE, Group.SWAMP, Group.FOREST, Group.DARK).slope(12)
                    .see((String) v[6]).learned((String) v[7]).reach(reach)
                    .air(Air.of(0x2C4A3A, 110).mote(() -> ParticleTypes.FALLING_WATER, 0.2F, 12, 0F).loop(() -> SoundEvents.WATER_AMBIENT, 120, 0.25F, 0.8F))
                    .scene(s -> site(s, d, twin, reach))
                    .script(() -> Lore.with(Steps.of(
                                    Order.of("sluice").each((c, i) -> {
                                        c.set("sluice", i, AIR);
                                        c.sound(c.pos("sluice", i), SoundEvents.IRON_TRAPDOOR_OPEN, 2.0F, 0.6F);
                                        c.sound(c.pos("sluice", i), SoundEvents.BUCKET_EMPTY, 2.0F, 0.7F);
                                        c.puff(c.pos("sluice", i), ParticleTypes.SPLASH, 16, 0.5, 0.1);
                                    }),
                                    Then.run(c -> {
                                        Mon.all(c, "marker", M.lamp(true));
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("marker", 0), SoundEvents.GRINDSTONE_USE, 3.0F, 0.5F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[8], (String) v[9], (String) v[10]))
                    .play(t -> { for (int i = 0; i < t.count("sluice"); i++) t.use("sluice", i); t.ticks(10).use("carving", 0).use("prize", 0); })
                    .add();
        }
    }
}
