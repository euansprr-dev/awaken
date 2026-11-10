package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The Orreries (SPEC §64.8, family 6): how those who went up worked out the way. Great bronze rings leaning round a
 * stone globe on a terrace, and a wheel-house for each ring. A post by the globe faces each wheel-house and carries
 * studs; you give each wheel as many turns as its post has studs, the rings agree, the markers light and the vault
 * under the globe lets go.
 */
final class MOrrery {
    private MOrrery() {}

    /** A ring: its radius, which way its face looks (degrees, east 0), how far it leans from upright, and whether it has fallen flat. */
    record Ring(int R, int yaw, int lean, boolean flat, int fx, int fz) {
        static Ring up(int R, int yaw, int lean) { return new Ring(R, yaw, lean, false, 0, 0); }
        static Ring down(int R, int fx, int fz) { return new Ring(R, 0, 0, true, fx, fz); }
    }

    private static void globe(Scene s, int cx, int cy, int cz, int r, boolean lit) {
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d > r + 0.3) continue;
                    s.put(cx + x, cy + y, cz + z, d < r - 1.2 ? M.stone() : y == 0 ? (lit && Math.floorMod(x + z, 3) == 0 ? M.seal() : M.bronzeBand()) : x == 0 || z == 0 ? M.bronze() : Math.abs(y) == r ? M.block() : M.course(cy + y));
                }
    }

    /**
     * {@code T}: the terrace's radius. {@code top}: its floor. {@code rings}: what stands and what lies. {@code g}: the
     * globe's radius (0: it has rolled off its plinth). {@code wheels}: how many wheel-houses. {@code great}: the one
     * great ring, whose globe stands on a mast at its middle.
     */
    static void orrery(Scene s, int T, int top, Ring[] rings, int g, int wheels, boolean lit, boolean great, int reach) {
        G.Shape foot = great ? G.either(G.disc(0, 0, T), G.box(-reach + 13, -7, reach - 13, 7)) : G.disc(0, 0, T);
        G.platform(s, foot, top, reach);
        s.clear(reach);
        int biggest = 0;
        for (Ring r : rings) if (!r.flat()) biggest = Math.max(biggest, r.R());
        int C = great ? top + biggest + 2 : top + Math.max(8, (int) (biggest * 0.45) + 3);
        // -- the terrace's floor: flagstones, a band under each ring, bronze lines out from the middle
        for (int x = -reach; x <= reach; x++)
            for (int z = -reach; z <= reach; z++) {
                if (foot.out(x, z) > 0) continue;
                double r = Math.sqrt(x * x + z * z), a = Math.toDegrees(Math.atan2(z, x));
                boolean line = Math.abs(((a % 30) + 30) % 30 - 15) > 14.2 && r > 7 && r < T - 1, band = false;
                for (Ring k : rings) band |= Math.abs(r - k.R()) < 0.6 && !k.flat();
                s.put(x, top, z, line ? M.bronzeBand() : band ? M.band() : r > T - 1.6 && !great ? M.block() : Math.floorMod(x * 3 + z * 7, 13) == 0 ? M.block() : M.flag());
            }
        // -- the rings
        for (Ring k : rings) {
            if (k.flat()) {
                int gap = Mon.pick(s, 50 + k.R(), 360);
                M.ring(s, k.fx(), top + 1, k.fz(), k.R(), 2.2, 1.1, 0, 1, 0, deg -> Math.floorMod(deg - gap, 360) < 70 || Math.floorMod(deg - gap, 360) > 200 && Math.floorMod(deg - gap, 360) < 230 ? null : M.ringStuff(deg, false));
                continue;
            }
            double yaw = Math.toRadians(k.yaw()), lean = Math.toRadians(k.lean());
            double w = k.R() >= 24 ? 2.6 : k.R() >= 14 ? 2.2 : 1.8;
            M.ring(s, 0, C, 0, k.R(), w, great ? 3.2 : 1.7, Math.cos(yaw) * Math.cos(lean), Math.sin(lean), Math.sin(yaw) * Math.cos(lean), deg -> M.ringStuff(deg, lit));
        }
        if (great) {                                               // the great ring stands in a shoe of masonry at each foot
            for (int side = -1; side <= 1; side += 2)
                for (int x = biggest - 6; x <= biggest + 3; x++)
                    for (int z = -5; z <= 5; z++) {
                        int h = top + 2 + Math.max(0, 8 - Math.abs(x - biggest) * 2 - Math.abs(z));
                        for (int y = top + 1; y <= h; y++) if (!M.isBronze(s.get(side * x, y, z))) s.put(side * x, y, z, y == h ? M.block() : M.course(y));
                    }
        }
        // -- the plinth with the vault in it, the mast, the globe
        disc(s, 0, 0, 6.3, top + 1, M.block());
        disc(s, 0, 0, 5.3, top + 2, M.block());
        for (int y = top + 3; y <= top + 6; y++) disc(s, 0, 0, 4.3, y, y == top + 6 ? M.band() : M.course(y));
        air(s, -1, top + 3, -1, 1, top + 4, 1);
        for (int y = top + 3; y <= top + 4; y++) {
            s.put(0, y, 3, AIR);
            s.put(0, y, 4, M.door());
            s.part("door", 0, y, 4);
            s.put(-1, y, 4, M.bronzeBand());
            s.put(1, y, 4, M.bronzeBand());
        }
        s.put(0, top + 3, -1, M.seal());
        s.part("prize", 0, top + 3, -1);
        s.put(0, top + 5, 0, M.lamp(true));
        if (g > 0) {
            for (int y = top + 7; y <= C - g - 1; y++) { int hw = y < top + 10 && !great ? 1 : 0; for (int x = -hw; x <= hw; x++) for (int z = -hw; z <= hw; z++) s.put(x, y, z, hw == 0 ? M.pillar(Direction.Axis.Y) : y % 5 == 0 ? M.band() : M.block()); }
            s.put(0, C - g - 1, 0, M.capital());
            globe(s, 0, C, 0, g, lit);
        } else {
            for (int y = top + 7; y <= top + 9; y++) s.put(0, y, 0, M.pillar(Direction.Axis.Y));
            s.put(0, top + 10, 0, M.blockSlab());
            int gx = (int) (T * 0.55), gz = (int) (T * 0.5);        // it rolled to the terrace's edge and stopped
            globe(s, gx, top + 4, gz, 4, false);
        }
        // -- the wheel-houses on the rim, and by the plinth a post facing each, with its studs
        int[] want = Turn.targets(s.site.seed(), wheels);
        int[] where = great ? new int[] {215, 325} : wheels <= 2 ? new int[] {180, 0} : wheels == 3 ? new int[] {180, 270, 0} : new int[] {180, 270, 0, 135};
        for (int i = 0; i < wheels; i++) {
            double a = Math.toRadians(where[i]);
            int wx = (int) Math.round(Math.cos(a) * (T - 5)), wz = (int) Math.round(Math.sin(a) * (T - 5));
            for (int q = 0; q < 4; q++) M.column(s, wx + (q < 2 ? -2 : 2), wz + (q % 2 == 0 ? -2 : 2), top + 1, top + 4);
            disc(s, wx, wz, 3.4, top + 5, M.block());
            disc(s, wx, wz, 2.3, top + 6, M.bronze());
            s.put(wx, top + 7, wz, M.blockSlab());
            s.put(wx, top + 1, wz, M.block());
            s.put(wx, top + 2, wz, Props.BRASS_RING.get().of(0));
            s.part("wheel", wx, top + 2, wz);
            int px = (int) Math.round(Math.cos(a) * 8), pz = (int) Math.round(Math.sin(a) * 8);
            s.put(px, top + 1, pz, M.block());
            for (int k = 0; k < want[i]; k++) s.put(px, top + 2 + k, pz, M.bronzeBand());
            s.put(px, top + 2 + want[i], pz, M.blockSlab());
        }
        for (int q = 0; q < 8; q++) {                              // the markers that light when the rings agree
            double a = Math.toRadians(q * 45 + 22);
            int mx = (int) Math.round(Math.cos(a) * 11), mz = (int) Math.round(Math.sin(a) * 11);
            s.put(mx, top + 1, mz, M.wall());
            s.put(mx, top + 2, mz, M.lamp(lit && q % 2 == 0));
            s.part("marker", mx, top + 2, mz);
        }
        Mon.carving(s, 3, top + 3, 5);
        Mon.carving(s, -5, top + 3, 3);
        Mon.carving(s, 5, top + 3, -3);
        if (lit) for (int q = 0; q < 12; q++) { double a = Math.toRadians(q * 30 + 15); M.lampPost(s, (int) Math.round(Math.cos(a) * (T - 2)), top + 1, (int) Math.round(Math.sin(a) * (T - 2)), 2, true); }
        int steps = G.stairs(s, 0, great ? 8 : T + 1, Direction.SOUTH, 2, top);
        int sz = (great ? 8 : T + 1) + steps;
        path(s, 0, sz + 10, 0, sz, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        M.finish(s, top + 3, 42);
        Mon.camp(s, -(int) (T * 0.6), (int) (T * 0.52));
        wild(s, Math.min(reach - 6, T + 4), reach - 1, 10);
        s.cam(-T - 2, top + 6, T + 6, -138, -22).cam(T, C + biggest, T + 8, 140, 40).cam(0, top + 3, 12, 180, -30).cam((int) (T * 0.7), top + 2, 4, 90, -30);
    }

    static void init() {
        Object[][] five = {
                {1, "orrery_small", "The Small Rings", new Object[] {14, 2, new Ring[] {Ring.up(10, 20, 0), Ring.down(7, 5, 5)}, 2, 2, false, false}, 27,
                        "A bronze ring twice the height of a house stands on edge on a little stone terrace, round a stone ball on a post. A second ring lies flat beside it.",
                        "You gave each wheel as many turns as its post has studs. The ring's markers lit, and the vault under the ball let go.",
                        "It says: turn each wheel once for every stud on the post that faces it. That is all the art there is in it. The rest is waiting for a clear night.",
                        "It says this one is a toy beside the others. They built it to teach children how the big ones worked.",
                        "Cut by a small hand, deep and uneven: I made the rings agree. I am nine."},
                {2, "orrery_fallen", "The Fallen Rings", new Object[] {22, 2, new Ring[] {Ring.down(18, -1, 1), Ring.down(13, 5, -4), Ring.down(9, -6, -7)}, 0, 3, false, false}, 35,
                        "Three great bronze rings lie flat on a round terrace, one over another like dropped hoops. The stone ball they turned round has rolled to the edge.",
                        "You turned each wheel by the studs on its post. Under your feet something still answered: the markers lit, and the vault in the empty plinth let go.",
                        "It says: turn each wheel once for every stud on the post that faces it. The rings will not move now, but the works under the floor still count.",
                        "It says the rings came down the winter after the last of the keepers went up. Nobody was left to oil them.",
                        "It says: I was the keeper's boy. I was too young to go. I have watched the sky from here every clear night since, without the rings. You can still do it."},
                {3, "orrery", "The Orrery", new Object[] {30, 3, new Ring[] {Ring.up(26, 20, 16), Ring.up(21, 125, -22), Ring.up(16, 78, 9), Ring.down(11, 16, 15)}, 4, 3, false, false}, 43,
                        "Three bronze rings as wide as a field lean at different angles round a stone globe on a hilltop terrace, and a fourth lies broken across the paving. Stone wheel-houses stand at the terrace's edge.",
                        "You gave each wheel as many turns as its post has studs. The rings agreed, their markers lit one after another, and the vault under the globe let go.",
                        "It says: turn each wheel once for every stud on the post that faces it. When the three rings agree, you are looking at the way up.",
                        "The way up opens when the three rings agree. We have had it wrong twice.",
                        "It says: the first time we had it wrong, forty people stepped off the top of the stair into nothing. We do not speak of the second time."},
                {4, "orrery_great", "The Great Ring", new Object[] {16, 3, new Ring[] {Ring.up(30, 90, 0)}, 3, 2, false, true}, 48,
                        "One bronze ring stands on edge across a ridge, sixty blocks from foot to crown, with nothing inside it but sky and a stone ball on a mast at its exact middle.",
                        "You turned the two wheels by the studs on their posts. All the way round the ring the markers lit, and the vault under the mast let go.",
                        "It says: turn each wheel once for every stud on the post that faces it. This ring has only one thing to say, and it says it at dusk on the right day.",
                        "It says they built this one last and largest because the small ones kept being wrong.",
                        "It says: stand at the south wheel on the evening of the longest day and look through. That is where they went. It is further than it looks."},
                {5, "orrery_whole", "The Rings That Still Turn", new Object[] {32, 4, new Ring[] {Ring.up(28, 15, 14), Ring.up(23, 110, -20), Ring.up(18, 70, 24), Ring.up(13, 160, -8)}, 5, 4, true, false}, 45,
                        "Four bronze rings stand whole round a great globe, every marker on them burning, lamps lit all round the terrace. Now and then one of the rings moves, a hand's width, with a sound like a door.",
                        "You set the four wheels by their studs. The rings came round together and stopped. Every marker burned white, and the vault under the globe let go.",
                        "It says: turn each wheel once for every stud on the post that faces it. It is right now. We checked it for a hundred years. Do not let anyone tell you the way up was a guess.",
                        "It says: the rings are right. What we did not know was what would be there when we arrived.",
                        "The last cut, on the plinth. It says: we left it running so that whoever followed would not have to do the sums again. If you follow, bring a coat."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Object[] o = (Object[]) v[3];
            int reach = (Integer) v[4], wheels = (Integer) o[4];
            Mon.kind(Family.ORRERY, tier, (String) v[1], (String) v[2]).verb(Verb.TURN).anchor(Anchor.HIGH)
                    .lands(Group.MOUNTAIN, Group.PLAINS, Group.SNOW, Group.BADLANDS, Group.DESERT).slope(16)
                    .see((String) v[5]).learned((String) v[6]).reach(reach)
                    .air(Air.of(0xB9C4DA, 150).mote(() -> ParticleTypes.END_ROD, 0.05F, 16, 0.004F).loop(() -> SoundEvents.AMETHYST_BLOCK_CHIME, 320, 0.15F, 0.4F))
                    .scene(s -> orrery(s, (Integer) o[0], (Integer) o[1], (Ring[]) o[2], (Integer) o[3], wheels, (Boolean) o[5], (Boolean) o[6], reach))
                    .script(() -> Lore.with(Steps.of(
                                    Turn.dials("wheel"),
                                    Then.run(c -> {
                                        Mon.all(c, "marker", M.lamp(true));
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("prize", 0), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.8F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> { int[] w = Turn.targets(t.seed(), wheels); for (int i = 0; i < wheels; i++) for (int k = 0; k < w[i]; k++) t.use("wheel", i); t.ticks(10).use("carving", 0).use("prize", 0); })
                    .add();
        }
    }
}
