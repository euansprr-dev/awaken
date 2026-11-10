package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
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

import static dev.awaken.finds.kit.B.*;

/**
 * The Sealed Shafts (SPEC §62.8, family 10): the way those who went down took, and shut behind them. A round pit with
 * giants standing round its rim, a ledge winding down its wall past door after sealed door, black water at the bottom.
 * One door is not sealed. You go down to it. The pit's middle is set north of the site's heart, so the heart lies under
 * the rim's masonry and never hangs in the void.
 */
final class MShaft {
    private MShaft() {}

    private static double ang(int x, int z) {
        return Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 360L);
    }

    /** The void of a pit of radius {@code R} between two heights, and the wall that lines it, two thick, banded every sixth course. */
    static void pit(Scene s, int cz, int R, int y0, int y1) {
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = -R - 2; z <= R + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= R) for (int y = y1; y <= y0; y++) s.put(x, y, cz + z, AIR);
                else if (r <= R + 2) for (int y = y1 - 1; y <= Math.min(y0, -1); y++) s.put(x, y, cz + z, Math.floorMod(y, 6) == 0 ? M.band() : h(s, x, y, z) < 14 ? M.lichen() : M.course(y));
            }
    }

    /**
     * A ledge two wide winding down the wall of a pit from {@code y0} to {@code y1}, a block lower every thirty degrees,
     * starting at {@code a0} (degrees, east 0, south 90) and turning toward the west. Every fourth step of it passes a
     * sealed door. Returns the angle it ends at.
     */
    static int ledges(Scene s, int cz, int R, int y0, int y1, int a0) {
        int steps = y0 - y1;
        for (int i = 0; i < steps; i++) {
            int from = a0 + 30 * i, y = y0 - 1 - i;
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r > R || r <= R - 2.5) continue;
                    double a = Math.floorMod((long) ang(x, z) - from, 360L);
                    if (a >= 30) continue;
                    s.put(x, y, cz + z, a < 6 ? M.block() : M.flag());
                    s.put(x, y - 1, cz + z, r <= R - 1.5 ? M.blockSlab().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP) : M.stone());
                }
            if (i % 4 == 2) {                                      // a door in the wall beside it: bronze, with a round seal, shut
                double a = Math.toRadians(from + 15);
                int dx = (int) Math.round(Math.cos(a) * (R + 1)), dz = (int) Math.round(Math.sin(a) * (R + 1));
                for (int k = -1; k <= 1; k++) for (int u = 0; u <= 2; u++) {
                    int px = dx + (Math.abs(Math.cos(a)) < 0.7 ? k : 0), pz = dz + (Math.abs(Math.cos(a)) < 0.7 ? 0 : k);
                    s.put(px, y + 1 + u, cz + pz, k == 0 && u == 1 ? M.seal() : u == 2 ? M.band() : M.bronze());
                }
            }
        }
        return a0 + 30 * steps;
    }

    /** The bottom: a landing all the way round just over the water, three blocks of water under it, and the vault behind the one door that opens. */
    static void bottom(Scene s, int cz, int R, int y, boolean lit, boolean carve) {
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R) continue;
                if (r > R - 2.5) { s.put(x, y - 1, cz + z, M.flag()); for (int k = 2; k <= 4; k++) s.put(x, y - k, cz + z, M.stone()); }
                else for (int k = 1; k <= 3; k++) s.put(x, y - k, cz + z, WATER);
                s.put(x, y - 5, cz + z, M.block());
                if (r <= R - 2.5) s.put(x, y - 4, cz + z, M.mosaic());
            }
        for (int x = -R - 2; x <= R + 2; x++) for (int z = -R - 2; z <= R + 2; z++) { double r = Math.sqrt(x * x + z * z); if (r > R && r <= R + 2) for (int k = 1; k <= 5; k++) s.put(x, y - k, cz + z, M.stone()); }
        // the vault, east of the pit: the door that is not sealed, a latch on its frame, the room behind
        int vx = R + 1;
        M.shell(s, vx + 1, y - 1, cz - 3, vx + 7, y + 4, cz + 3);
        M.mass(s, vx + 1, y + 4, cz - 3, vx + 7, y + 5, cz + 3);
        air(s, vx + 2, y, cz - 2, vx + 6, y + 3, cz + 2);
        for (int x = vx + 2; x <= vx + 6; x++) for (int z = -2; z <= 2; z++) s.put(x, y - 1, cz + z, Math.abs(z) + Math.abs(x - vx - 4) < 2 ? M.mosaic() : M.flag());
        for (int u = 0; u <= 2; u++)
            for (int k = -1; k <= 1; k++) {
                if (k != 0) { s.put(vx, y + u, cz + k, M.bronzeBand()); s.put(vx + 1, y + u, cz + k, M.block()); continue; }
                s.put(vx, y + u, cz, u == 2 ? M.band() : AIR);
                s.put(vx + 1, y + u, cz, u == 2 ? M.band() : M.door());
                if (u < 2) s.part("door", vx + 1, y + u, cz);
            }
        s.put(vx - 2, y + 1, cz + 2, Props.PLUG.get().of(0));     // the latch, on a post on the landing beside the door
        s.put(vx - 2, y, cz + 2, M.block());
        s.part("latch", vx - 2, y + 1, cz + 2);
        Mon.prize(s, vx + 5, y, cz, Direction.WEST);
        s.put(vx + 4, y + 3, cz, M.lamp(true));
        if (carve) Mon.carving(s, vx - 2, y, cz - 2);
        if (lit) for (int q = 0; q < 4; q++) s.put(DX[q] * (R - 1), y, cz + DZ[q] * (R - 1), M.lamp(true));
    }

    /** The paved rim round a pit's mouth, with a kerb at its edge, open on the south where the ledge begins. */
    static void rim(Scene s, int cz, int R, boolean kerb) {
        for (int x = -R - 8; x <= R + 8; x++)
            for (int z = -R - 8; z <= R + 8; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= R || r > R + 7.5) continue;
                s.put(x, 0, cz + z, Math.abs(r - (R + 4)) < 0.6 ? M.bronzeBand() : Math.floorMod(Math.round(ang(x, z)), 30L) < 3 ? M.band() : Math.floorMod(x * 3 + z * 5, 9) == 0 ? M.block() : M.flag());
                s.put(x, -1, cz + z, M.stone());
                if (kerb && r <= R + 1.5 && !(Math.abs(x) <= 2 && z > 0)) s.put(x, 1, cz + z, Math.floorMod(x + z, 4) == 0 ? M.block() : M.blockSlab());
            }
    }

    /**
     * {@code state}: 0 a small one with its lid half off, 1 its rim fallen in on the east and its giants fallen outward,
     * 2 as the painting has it, 4 the lid still on.
     */
    static void shaft(Scene s, int R, int D, int n, int state, int reach) {
        int cz = -(R + 3);
        G.platform(s, G.disc(0, cz, R + 9), 0, reach);
        s.clear(reach);
        rim(s, cz, R, state != 1);
        pit(s, cz, R, 0, -D);
        ledges(s, cz, R, 0, -D, 90);
        Mon.carving(s, -5, 1, cz + R + 5);                         // at the way in, either side: what the first two say is for whoever arrives
        Mon.carving(s, 5, 1, cz + R + 5);
        bottom(s, cz, R, -D, state == 4, state != 4);
        // -- who stands round it
        int gh = state == 0 ? 15 : 18;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(state == 0 ? 240 + 60 * i : i * 360.0 / n + 30);
            int x = (int) Math.round(Math.cos(a) * (R + 5)), z = (int) Math.round(Math.sin(a) * (R + 5));
            Direction in = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
            if (state == 1 && i != 1) M.giant(s, x + in.getOpposite().getStepX() * 2, 1, cz + z + in.getOpposite().getStepZ() * 2, in.getOpposite(), gh, false, i % 3 == 0 ? 1 : 0, true);
            else if (state == 2 && i == n / 2) M.giant(s, -(R + 8), 1, cz, Direction.EAST, gh, false, 0, true);    // the one that fell in: across the rim, its head over the drop
            // (of the fallen ring, one still stands, headless: what you see of it from a hill)
            else M.giant(s, x, 1, cz + z, in, gh, false, state == 4 ? 0 : state == 1 ? 1 : (i * 5 + Mon.pick(s, 71, 3)) % 4 == 0 ? 2 : 0, false);
        }
        if (state == 0) {                                          // the lid: slid half off to the east, resting on the rim
            for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) { double r = Math.sqrt(x * x + z * z); if (r <= R + 1.3) for (int y = 1; y <= 2; y++) s.put(x + R, y, cz + z, y == 2 && Math.abs(r - R / 2.0) < 0.6 ? M.bronzeBand() : r < 1.2 && y == 2 ? M.seal() : M.block()); }
        }
        if (state == 4) {                                          // the lid, still on: stone and bronze, a notch on the south where the ledge goes under
            for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R + 1.3 || (Math.abs(x) <= 2 && z > R - 5)) continue;
                for (int y = 1; y <= 2; y++) s.put(x, y, cz + z, y == 2 && (Math.abs(r - R / 3.0) < 0.6 || Math.abs(r - R * 2 / 3.0) < 0.6) ? M.bronzeBand() : r < 1.6 && y == 2 ? M.seal() : y == 1 ? M.stone() : M.block());
            }
            Mon.carving(s, 3, -1, cz + R - 6);                      // on the lid's underside, where only someone coming up would read it
        }
        if (state == 1) {                                          // the east rim came down into it: a slope of rubble from the surface to half way
            for (int x = 1; x <= R + 2; x++) for (int z = -R; z <= R; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R + 2) continue;
                int top = -(int) ((R + 2 - x) * 1.1) - (int) (n(s, x * 1.3, z * 1.3, 4.0) * 3);
                for (int y = -D - 1; y <= Math.min(-1, top); y++) if (s.get(x, y, cz + z) == null || s.get(x, y, cz + z).isAir()) s.put(x, y, cz + z, h(s, x, y, z) < 120 ? M.rubble() : h(s, x, y, z) < 200 ? M.stone() : M.block());
            }
        }
        // -- the way in on the south, the first carvings, the windlass the last caravan left
        for (int x = -2; x <= 2; x++) s.put(x, 0, cz + R, M.step(s, x, cz + R, Direction.SOUTH));
        s.put(4, 1, cz + R + 2, Props.WINCH.get().of(0));
        for (int k = 1; k <= 5; k++) s.put(4, 1 - k, cz + R - 1, Props.ROPE_LENGTH.get().of(0));
        s.put(4, 0, cz + R - 1, AIR);
        M.finish(s, -D + 6, 46);
        int steps = G.stairs(s, 0, cz + R + 10, Direction.SOUTH, 2, 0);
        path(s, 0, cz + R + 10 + steps + 8, 0, cz + R + 10 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        Mon.camp(s, -12, Math.min(reach - 5, cz + R + 16));
        wild(s, Math.min(reach - 6, R + 14 - cz / 2), reach - 1, 8);
        s.cam(-R - 6, 14, cz + R + 16, -150, 10).cam(R, R + 20, cz + R + 12, 140, 48).cam(0, 2, cz + R + 6, 180, 30).cam(R - 3, -D + 1, cz + 3, 0, 0);
    }

    /** The Shaft of Rings: a pit inside a pit inside a pit, each a terrace down, pylons round the top in place of giants. */
    static void rings(Scene s, int reach) {
        int R1 = 20, R2 = 14, R3 = 8, cz = -(R1 + 3);
        G.platform(s, G.disc(0, cz, R1 + 9), 0, reach);
        s.clear(reach);
        rim(s, cz, R1, true);
        pit(s, cz, R1, 0, -8);
        pit(s, cz, R2, -9, -18);
        pit(s, cz, R3, -19, -30);
        for (int x = -R1; x <= R1; x++) for (int z = -R1; z <= R1; z++) {      // the two terraces' floors
            double r = Math.sqrt(x * x + z * z);
            if (r > R2 && r <= R1) { s.put(x, -9, cz + z, Math.abs(r - R2 - 3) < 0.6 ? M.bronzeBand() : M.flag()); s.put(x, -10, cz + z, M.stone()); }
            else if (r > R3 && r <= R2) { s.put(x, -19, cz + z, Math.abs(r - R3 - 3) < 0.6 ? M.bronzeBand() : M.flag()); s.put(x, -20, cz + z, M.stone()); }
        }
        int a = ledges(s, cz, R1, 0, -8, 90);
        a = ledges(s, cz, R2, -8, -18, a + 30);
        ledges(s, cz, R3, -18, -30, a + 30);
        Mon.carving(s, -5, 1, cz + R1 + 5);
        Mon.carving(s, 5, -8, cz + R2 + 3);
        bottom(s, cz, R3, -30, false, true);
        for (int i = 0; i < 8; i++) {                               // the pylons: square, banded, each with its top broken at a different height
            double an = Math.toRadians(i * 45 + 22);
            int x = (int) Math.round(Math.cos(an) * (R1 + 5)), z = cz + (int) Math.round(Math.sin(an) * (R1 + 5)), top = 11 + Mon.pick(s, 60 + i, 6);
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) for (int y = 1; y <= top; y++) s.put(x + ox, y, z + oz, y % 5 == 0 ? M.band() : (Math.abs(ox) + Math.abs(oz) == 2 && y > 2 ? M.block() : M.course(y)));
            s.put(x, top + 1, z, i % 3 == 0 ? M.lamp(true) : M.blockSlab());
            M.ruin(s, x - 1, z - 1, x + 1, z + 1, top - 1, 1, top + 1);
        }
        for (int x = -2; x <= 2; x++) s.put(x, 0, cz + R1, M.step(s, x, cz + R1, Direction.SOUTH));
        M.finish(s, -24, 46);
        int steps = G.stairs(s, 0, cz + R1 + 10, Direction.SOUTH, 2, 0);
        path(s, 0, cz + R1 + 10 + steps + 8, 0, cz + R1 + 10 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        Mon.camp(s, -14, Math.min(reach - 5, cz + R1 + 16));
        wild(s, reach - 8, reach - 1, 6);
        s.cam(-R1 - 4, 16, cz + R1 + 14, -150, 14).cam(R1, R1 + 22, cz + R1 + 12, 140, 50).cam(0, 2, cz + R1 + 6, 180, 30).cam(R3 - 3, -29, cz + 3, 0, 0);
    }

    static void init() {
        Object[][] five = {
                {1, "shaft_small", "The Capped Well", (dev.awaken.finds.Kind.Maker) s -> shaft(s, 6, 14, 2, 0, 37), 37,
                        "A round stone well as wide as a house, with a lid of stone slid half off it and two hooded giants standing behind. A ledge goes down inside, past a bronze door that is shut.",
                        "You went down the ledge to the water. One door at the bottom was only latched. You lifted the latch.",
                        "It says: this is only an air shaft. There is nothing down it but the dark, and the dark is not ours to give away.",
                        "It says the lid was meant to be moved by twelve. Whoever shifted it this far was fewer than twelve, and gave up.",
                        "Scratched by the last door, by a later hand: I went all the way down. Every door was shut but this one. I took what was here and I felt like a thief."},
                {2, "shaft_fallen", "The Fallen Ring", (dev.awaken.finds.Kind.Maker) s -> shaft(s, 12, 24, 5, 1, 49), 49,
                        "A great round pit with one side of its rim fallen in, and five stone giants lying on their backs round it, every one with its feet to the hole.",
                        "You went down the ledge, past the slope where the rim came in, to the water. The last door was only latched.",
                        "It says the giants were set facing inward, to watch the way down. Something made every one of them fall the same way: outward.",
                        "It says: the ground under the ring moved once. Only once. Like a sleeper turning over.",
                        "It says: nothing came up. We want that understood. The stones fell in. Nothing came up."},
                {3, "shaft", "The Sealed Shaft", (dev.awaken.finds.Kind.Maker) s -> shaft(s, 14, 36, 6, 2, 53), 53,
                        "A round pit as wide as a field in the dry country, ringed by hooded stone giants with their heads bowed. One has fallen forward across the rim. A ledge winds down the wall into the dark, past door after sealed door.",
                        "You went down the ledge, three times round, past nine sealed doors, to the black water. The last door was only latched. You lifted the latch.",
                        "We closed every door behind us. Do not open them. We mean it kindly.",
                        "It says: there were more of us than doors. The ones who went down last sealed in the ones who went down first.",
                        "Cut beside the one door that is not sealed. It says: this one is for our children, if they should want to bring us a message. We left a gift in it for the trouble."},
                {4, "shaft_rings", "The Shaft of Rings", (dev.awaken.finds.Kind.Maker) s -> rings(s, 65), 65,
                        "A pit inside a pit inside a pit, each one a terrace lower than the last, with broken stone pylons standing round the top. It looks like the print of a tower pressed into the ground.",
                        "You went down all three rings to the water. The last door was only latched. You lifted the latch.",
                        "It says each ring down is a hundred years of digging. They were three hundred years going down before they found what they were looking for.",
                        "It says: on the first ring we still sang at our work. On the second we did not. On the third we worked in shifts, so that nobody had to be down here long.",
                        "It says: at the bottom we stopped digging and started building doors."},
                {5, "shaft_whole", "The Shaft That Is Still Shut", (dev.awaken.finds.Kind.Maker) s -> shaft(s, 14, 36, 6, 4, 53), 53,
                        "A round lid of stone and bronze as wide as a field, lying flat in the dry country with six hooded giants standing whole round it, heads bowed. On the south side a notch goes under its edge.",
                        "You went under the lid's edge and down the ledge in the lamplight to the water. The last door was only latched.",
                        "It says: the lid is on. Leave it on. Whatever you think you are owed, it is not under here.",
                        "It says the six who stand here are the six who set the lid. They asked to be remembered facing it.",
                        "Cut on the underside of the lid, where only someone coming up would read it. It says: if you are reading this, you got out. Close it behind you."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.SHAFT, tier, (String) v[1], (String) v[2]).verb(Verb.REACH)
                    .lands(Group.DESERT, Group.BADLANDS, Group.MOUNTAIN).slope(12)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0x3A2E2A, 120).below().mote(() -> ParticleTypes.ASH, 0.3F, 12, 0.01F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 300, 0.35F, 0.6F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Prize.at("latch").then((c, i) -> {
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("latch", 0), SoundEvents.CHAIN_BREAK, 1.5F, 0.7F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> t.use("carving", 0).use("latch", 0).use("prize", 0))
                    .add();
        }
    }
}
