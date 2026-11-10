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
 * The Mooring Masts (SPEC §64.8, family 5): where the ships of those who went up tied on. A needle of a tower with a
 * stair wound inside it and bronze gantries reaching out, and on one of them a hull still hanging by its chains. You
 * climb inside to the gantry and knock out the pin; what hangs comes down and breaks open.
 */
final class MMast {
    private MMast() {}

    private static final int R = 4;                               // the wall's radius; the stair inside is three clear

    /** The tower itself from the ground to {@code H}: wall, stair, door, three fins against its foot. {@code whole}: capped, with a beacon. */
    static void tower(Scene s, int cx, int cz, int H, boolean whole) {
        for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) if (x * x + z * z <= (R - 0.5) * (R - 0.5)) { s.put(cx + x, 0, cz + z, M.flag()); for (int y = 1; y <= H; y++) s.put(cx + x, y, cz + z, AIR); }
        M.tower(s, cx, cz, R, 1, H, 8);
        M.spiral(s, cx, cz, R - 1, 1, H - 1);
        for (int y = 1; y <= 4; y++) for (int z = R; z <= R + 1; z++) for (int x = -1; x <= 1; x++) s.put(cx + x, y, cz + z, y == 4 ? M.band() : x == 0 ? AIR : z == R + 1 ? M.bronzeBand() : M.block());
        s.put(cx, 5, cz + R + 1, M.seal());
        for (Direction d : new Direction[] {Direction.NORTH, Direction.EAST, Direction.WEST})   // fins, tall at the wall and stepping down away from it
            for (int k = 1; k <= 4; k++) {
                int fx = cx + d.getStepX() * (R + k), fz = cz + d.getStepZ() * (R + k), top = Math.max(3, (int) (H / 3.0 * (1 - (k - 1) / 4.0)));
                for (int y = Math.min(0, s.g(fx, fz)) - 1; y <= top; y++) s.put(fx, y, fz, y > 0 && y % 10 == 0 ? M.band() : M.course(y));
                s.put(fx, top + 1, fz, stair(M.blockStairs(), d.getOpposite()));
            }
        if (whole) {
            for (int k = 0; k <= R; k++) { ring(s, cx, cz, R - k + 1, H + 1 + k, M.blockSlab()); disc(s, cx, cz, R - k, H + 1 + k, k == R ? M.lamp(true) : M.block()); }
            s.put(cx, H + R + 2, cz, M.bronzeBand());
        } else M.ruin(s, cx - R - 1, cz - R - 1, cx + R + 1, cz + R + 1, H - 3, 3, H + 1);
    }

    /** A gantry at height {@code g} reaching {@code len} out along {@code d}: a walk round the tower, a door to it, the arm and its brace. */
    static void gantry(Scene s, int cx, int cz, int g, Direction d, int len, boolean lit) {
        ring(s, cx, cz, R + 1.6, g, M.bronze());
        ring(s, cx, cz, R + 2.6, g, M.bronzeBand());
        int ox = d.getStepX(), oz = d.getStepZ(), px = -oz, pz = ox;
        for (int y = g + 1; y <= g + 2; y++) s.put(cx + ox * R, y, cz + oz * R, AIR);
        for (int k = R + 1; k <= R + len; k++)
            for (int w = -1; w <= 1; w++) {
                s.put(cx + ox * k + px * w, g, cz + oz * k + pz * w, w == 0 ? M.bronze() : M.bronzeBand());
                if (w != 0 && k % 4 == 0 && k < R + len) s.put(cx + ox * k + px * w, g + 1, cz + oz * k + pz * w, st(Blocks.IRON_BARS));
            }
        for (int k = 1; k <= Math.min(len - 2, 8); k++) {           // the brace under it, from the wall out
            int by = g - 1 - Math.max(0, 8 - k) * 3 / 4;
            s.put(cx + ox * (R + k), by, cz + oz * (R + k), M.bronzeBand());
            if (k > 1) s.put(cx + ox * (R + k), by - 1, cz + oz * (R + k), M.bronze());
        }
        if (lit) s.put(cx + ox * (R + len), g + 1, cz + oz * (R + len), M.lamp(true));
    }

    /**
     * A hull: a spindle of bronze plate, {@code len} long and {@code rad} at its widest, its long way along an axis
     * (0 x, 1 y, 2 z). Hollow. {@code part}: every block of it is a part of that name (so it can be let go), or null.
     */
    static void hull(Scene s, int cx, int cy, int cz, int len, double rad, int axis, String part) {
        for (int t = -len / 2; t <= len / 2; t++) {
            double r = rad * Math.pow(Math.sin(Math.PI * (t + len / 2.0 + 0.5) / (len + 1)), 0.6);
            int ir = (int) Math.ceil(r);
            for (int a = -ir; a <= ir; a++)
                for (int b = -ir; b <= ir; b++) {
                    double dd = Math.sqrt(a * a + b * b);
                    if (dd > r || dd < r - 1.3) continue;
                    int x = cx + (axis == 0 ? t : a), y = cy + (axis == 1 ? t : b), z = cz + (axis == 2 ? t : axis == 0 ? a : b);
                    BlockState st = Math.floorMod(t, 5) == 0 ? M.bronzeBand() : (a == 0 || b == 0) && dd > r - 0.8 ? M.block() : M.bronze();
                    s.put(x, y, z, st);
                    if (part != null) s.part(part, x, y, z);
                }
        }
    }

    /** Where a hull lies once it has come down: the lower half of one, broken in two, on the ground round (gx, gz). The middle of it is the prize's place. */
    static void wreck(Scene s, int gx, int gz, int len) {
        for (int t = -len / 2; t <= len / 2; t++) {
            if (Math.abs(t) <= 1) continue;                        // it broke its back
            double r = 3.6 * Math.pow(Math.sin(Math.PI * (t + len / 2.0 + 0.5) / (len + 1)), 0.6);
            int ir = (int) Math.ceil(r), x = gx + t + (t > 0 ? 1 : -1), drop = t > 0 ? 0 : 1;
            for (int b = -ir; b <= ir; b++)
                for (int a = -ir; a <= 0; a++) {
                    double dd = Math.sqrt(a * a + b * b);
                    if (dd > r || dd < r - 1.3) continue;
                    int y = s.g(x, gz + b) + 1 + (a + ir) - drop;
                    if (y <= s.g(x, gz + b)) continue;
                    s.put(x, y, gz + b, AIR);
                    s.part("wreck", x, y, gz + b);
                }
        }
        int py = s.g(gx, gz) + 1;
        s.put(gx, py, gz, AIR);
        s.part("prize", gx, py, gz);
    }

    private static void pin(Scene s, int x, int y, int z) {
        s.put(x, y, z, Props.PLUG.get().of(0));
        s.part("pin", x, y, z);
    }

    private static void base(Scene s, int cx, int cz, int reach) {
        G.platform(s, G.disc(cx, cz, R + 7), 0, reach);
        for (int x = -R - 6; x <= R + 6; x++) for (int z = -R - 6; z <= R + 6; z++) { double d = Math.sqrt(x * x + z * z); if (d <= R + 6.5 && d > R + 0.5 && !s.taken(cx + x, 1, cz + z)) s.put(cx + x, 0, cz + z, Math.abs(d - (R + 4)) < 0.6 ? M.band() : M.flag()); }
        Mon.carving(s, cx + 2, 1, cz + R + 1);
    }

    private static void finish(Scene s, int reach, int cx, int cz) {
        int steps = G.stairs(s, cx, cz + R + 8, Direction.SOUTH, 1, 0);
        path(s, cx, cz + R + 8 + steps + 8, cx, cz + R + 8 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        M.finish(s, 5, 48);
        Mon.camp(s, cx - R - 12, cz + 6);
        wild(s, R + 12, reach - 1, 10);
    }

    // ---- the five -----------------------------------------------------------------------------------------------------

    private static void post(Scene s) {
        s.clear(26);
        base(s, 0, 0, 26);
        tower(s, 0, 0, 30, false);
        gantry(s, 0, 0, 22, Direction.EAST, 9, false);
        gantry(s, 0, 0, 12, Direction.WEST, 3, false);
        for (int k = 1; k <= 4; k++) s.put(R + 9, 22 - k, 0, st(Blocks.IRON_CHAIN));
        hull(s, R + 9, 14, 0, 7, 2.3, 1, "hull");                 // not a ship: one of the pods they sent up after
        pin(s, R + 8, 23, 0);
        wreck(s, R + 9, 6, 8);
        Mon.carving(s, -3, 23, 0);
        Mon.carving(s, 0, 23, -3);
        finish(s, 26, 0, 0);
        s.cam(-16, 4, 22, -150, -30).cam(18, 40, 24, 140, 30).cam(0, 3, R + 6, 180, -6).cam(R + 4, 24, 3, -90, 20);
    }

    private static void fallen(Scene s) {
        s.clear(56);
        // the stump stands at the east end; the rest of it lies along the ground to the west in three lengths
        base(s, 44, 0, 56);
        tower(s, 44, 0, 17, false);
        int[] from = {-50, -24, 4}, to = {-28, 0, 30}, lift = {0, 1, 3};
        for (int q = 0; q < 3; q++)
            for (int x = from[q]; x <= to[q]; x++) {
                int cy = s.g(x, q - 1) + R + lift[q];
                for (int a = -R - 1; a <= R + 1; a++)
                    for (int b = -R - 1; b <= R + 1; b++) {
                        double dd = Math.sqrt(a * a + b * b);
                        boolean wall = dd >= R - 0.5 && dd < R + 0.5;
                        if (!wall && dd >= R - 0.5) continue;
                        if (cy + b <= s.g(x, q - 1 + a) - 1) continue;
                        s.put(x, cy + b, q - 1 + a, !wall ? AIR : Math.floorMod(x, 8) == 7 ? M.band() : M.course(x));
                    }
            }
        // its ship lies beside it, whole, with her hatch still pinned
        int hy = s.g(-40, 12) + 4;
        hull(s, -40, hy, 12, 17, 3.6, 0, null);
        for (int x = -41; x <= -40; x++) for (int y = hy - 1; y <= hy; y++) { s.put(x, y, 15, M.door()); s.part("hull", x, y, 15); s.put(x, y, 14, AIR); }
        s.put(-40, hy - 1, 12, AIR);
        s.put(-40, hy, 12, AIR);
        Mon.prize(s, -40, hy - 2, 11, Direction.SOUTH);
        pin(s, -38, s.g(-38, 17) + 1, 17);
        Mon.carving(s, -6, s.g(-6, 0) + 3, -R + 1);
        Mon.carving(s, 12, s.g(12, 1) + 5, 2 - R);
        gantry(s, 44, 0, 12, Direction.NORTH, 4, false);
        M.heap(s, 2, 6, 3.2);
        M.heap(s, -26, -6, 3.0);
        finish(s, 56, 44, 0);
        s.cam(-10, 10, 36, 180, 4).cam(0, 40, 50, 180, 42).cam(-12, 4, 12, 180, 0).cam(-20, 4, 0, -90, 0);
    }

    private static void mast(Scene s) {
        s.clear(30);
        base(s, 0, 0, 30);
        tower(s, 0, 0, 70, false);
        gantry(s, 0, 0, 28, Direction.WEST, 4, false);
        gantry(s, 0, 0, 46, Direction.NORTH, 5, false);
        gantry(s, 0, 0, 62, Direction.EAST, 16, false);
        for (int k = 1; k <= 5; k++) { s.put(R + 15, 62 - k, 0, st(Blocks.IRON_CHAIN)); s.put(R + 13, 62 - k, 0, st(Blocks.IRON_CHAIN)); }
        hull(s, R + 14, 43, 0, 27, 4.2, 1, "hull");
        pin(s, R + 14, 63, 0);
        wreck(s, R + 14, 2, 18);
        Mon.carving(s, -3, 63, 0);
        Mon.carving(s, 0, 47, -3);
        // what fell from the lower gantries
        for (int i = 0; i < 6; i++) { int x = -R - 6 - i * 2, z = -3 + i % 3 * 3, y = s.g(x, z) + 1; s.put(x, y, z, i % 2 == 0 ? M.bronze() : M.bronzeBand()); if (i % 3 == 0) s.put(x, y + 1, z, M.bronzeBand()); }
        M.heap(s, 2, -R - 9, 3.0);
        finish(s, 30, 0, 0);
        s.cam(-18, 4, 26, -150, -42).cam(26, 76, 28, 136, 34).cam(0, 3, R + 6, 180, -6).cam(R + 6, 64, 4, -90, 30);
    }

    private static void twin(Scene s) {
        s.clear(40);
        // two masts, and a hull slung level between them like a hammock
        G.platform(s, G.either(G.disc(-17, 0, R + 6), G.either(G.disc(17, 0, R + 6), G.box(-17, -5, 17, 5))), 0, 40);
        for (int x = -24; x <= 24; x++) for (int z = -8; z <= 8; z++) if (!s.taken(x, 1, z) && (Math.abs(z) <= 4 || Math.hypot(Math.abs(x) - 17, z) < R + 5.5)) s.put(x, 0, z, Math.floorMod(x + z, 5) == 0 ? M.block() : M.flag());
        tower(s, -17, 0, 50, false);
        tower(s, 17, 0, 44, false);
        gantry(s, -17, 0, 40, Direction.EAST, 8, false);
        gantry(s, 17, 0, 40, Direction.WEST, 8, false);
        hull(s, 0, 35, 0, 15, 3.4, 0, "hull");
        for (int k = 1; k <= 3; k++) { s.put(-5, 40 - k, 0, st(Blocks.IRON_CHAIN)); s.put(5, 40 - k, 0, st(Blocks.IRON_CHAIN)); }
        pin(s, -5, 41, 0);
        wreck(s, 0, 9, 14);
        Mon.carving(s, -19, 1, R + 1);
        Mon.carving(s, -20, 41, 0);
        Mon.carving(s, 20, 41, 0);
        int steps = G.stairs(s, 0, 6, Direction.SOUTH, 2, 0);
        path(s, 0, 6 + steps + 8, 0, 6 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        M.finish(s, 5, 48);
        Mon.camp(s, 0, -16);
        wild(s, 30, 39, 8);
        s.cam(-20, 4, 30, -160, -36).cam(0, 60, 34, 180, 36).cam(0, 3, 12, 180, -30).cam(-10, 42, 3, -90, 10);
    }

    private static void whole(Scene s) {
        s.clear(36);
        base(s, 0, 0, 36);
        tower(s, 0, 0, 84, true);
        gantry(s, 0, 0, 30, Direction.WEST, 10, true);
        gantry(s, 0, 0, 52, Direction.NORTH, 12, true);
        gantry(s, 0, 0, 75, Direction.EAST, 12, true);
        // the ship that never left: an open boat moored level at the top gantry, her hatch held by the pin
        int sx = R + 17;
        hull(s, sx, 75, 0, 31, 4.6, 2, null);
        for (int z = -16; z <= 16; z++) {
            double r = 4.6 * Math.pow(Math.sin(Math.PI * (z + 15.5 + 0.5) / 32), 0.6);
            for (int x = -5; x <= 5; x++) {
                for (int y = 77; y <= 81; y++) if (s.taken(sx + x, y, z)) s.put(sx + x, y, z, AIR);
                if (Math.abs(x) < r - 1.2) { s.put(sx + x, 75, z, st(Blocks.SPRUCE_PLANKS)); if (s.taken(sx + x, 76, z)) s.put(sx + x, 76, z, AIR); }
            }
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { s.put(sx + x, 75, z, M.door()); s.part("hull", sx + x, 75, z); }
        Mon.prize(s, sx, 72, 3, Direction.NORTH);
        for (int y = 72; y <= 74; y++) { s.put(sx, y, -1, ladder(Direction.SOUTH)); s.put(sx, y, -2, M.bronze()); }
        for (int z = -1; z <= 1; z++) for (int y = 76; y <= 77; y++) for (int x = -5; x <= -4; x++) s.put(sx + x, y, z, AIR);   // the rail opens at the gangway
        pin(s, sx - 2, 76, 3);
        s.put(sx, 77, -13, M.lamp(true));
        s.put(sx, 76, -13, M.bronzeBand());
        Mon.carving(s, -3, 76, 0);
        Mon.carving(s, 0, 53, -3);
        finish(s, 36, 0, 0);
        s.cam(-20, 4, 30, -150, -46).cam(30, 92, 32, 136, 34).cam(0, 3, R + 6, 180, -6).cam(R + 8, 77, 5, -90, 6);
    }

    static void init() {
        Object[][] five = {
                {1, "mast_post", "The Mooring Post", (dev.awaken.finds.Kind.Maker) MMast::post, 26,
                        "A stone tower broken off thirty blocks up, with one bronze arm sticking out of it. Something like a great bronze seed hangs from the arm on a chain.",
                        "You climbed the stair inside to the arm and knocked out the pin. The pod came down and split, and what was packed in it lay in the grass.",
                        "It says: after the last ship, we sent things up to them on the pods. Seed. Letters. We never knew if any of it arrived.",
                        "A list of what went up in this one. Wheat, flax, a child's shoes, forty letters. It was never let go.",
                        "It says: nothing ever came down. Not a word. We kept sending for eleven years."},
                {2, "mast_fallen", "The Fallen Mast", (dev.awaken.finds.Kind.Maker) MMast::fallen, 56,
                        "A stone tower lying full length across the country in three broken pieces, hollow, big enough to walk through. Its stump still stands at one end, and a crushed bronze hull lies beside it.",
                        "You knocked the pin from the hatch in the middle length. It swung open, and inside the fallen tower was what its keepers had hidden there.",
                        "It says the storm took it in one night. The ship was tied on. The keepers were inside.",
                        "It says: we got out through the door, which was by then the roof. We count ourselves lucky.",
                        "Cut on the stump: we will not build it again. There is nobody left up there to tie on to."},
                {3, "mast", "The Mooring Mast", (dev.awaken.finds.Kind.Maker) MMast::mast, 30,
                        "A needle of a tower on the high ground, ringed with broken bronze gantries. From the highest one the hull of a ship still hangs nose down on its chains, turning a little in the wind.",
                        "You climbed the stair inside to the top gantry, walked out along it and knocked out the pin. The hull came down and broke open on the ground.",
                        "Last ship. We waited four days for the ones from the valley. We cannot wait any longer.",
                        "It says: a ship takes two hundred. There were nine ships and six thousand of us. We drew lots, and then some who had won gave their place away.",
                        "It says: this one was for the people from the valley. They never came. We left it tied on in case."},
                {4, "mast_twin", "The Twin Masts", (dev.awaken.finds.Kind.Maker) MMast::twin, 40,
                        "Two stone towers stand a short way apart, and between them, high up, a bronze hull hangs level on chains like a boat on a slip.",
                        "You climbed the western tower, went out along its arm and knocked out the pin. The hull dropped between the towers and broke its back.",
                        "It says this was the yard. Ships were hung here to be finished, between the two masts, where the wind could not turn them.",
                        "It says: this was to be the tenth. There were not enough of us left on the ground to finish her.",
                        "It says: I am the last shipwright. I could finish her alone in twenty years. I have decided to plant an orchard instead."},
                {5, "mast_whole", "The Mast With Its Ship", (dev.awaken.finds.Kind.Maker) MMast::whole, 36,
                        "A whole tower with a light in its cap and a lamp at the end of every gantry. At the top a bronze ship lies moored, level, complete, waiting.",
                        "You climbed to the top and went aboard. You knocked out the pin and the hatch fell open. In the hold, laid out and labelled, was everything a crossing would need.",
                        "It says: this ship is for whoever comes late. She is provisioned. The way of working her is cut inside the hatch.",
                        "It says we do not know what is up there now. If the ones who went are still there, tell them the lamps were kept.",
                        "Cut inside the hatch: she will not fly for you. Nothing we made flies any more. We are sorry. Take what is in the hold."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.MAST, tier, (String) v[1], (String) v[2]).verb(Verb.BREAK).anchor(Anchor.HIGH)
                    .lands(Group.MOUNTAIN, Group.SNOW, Group.PLAINS, Group.DESERT, Group.BADLANDS).slope(16)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0xC4D2DE, 150).mote(() -> ParticleTypes.CLOUD, 0.05F, 16, 0.01F).loop(() -> SoundEvents.ELYTRA_FLYING, 200, 0.09F, 0.8F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Prize.at("pin").then((c, i) -> {
                                        for (int k = 0; k < c.count("hull"); k++) c.set("hull", k, AIR);
                                        for (int k = 0; k < c.count("wreck"); k++) c.set("wreck", k, c.turned(k % 7 == 0 ? M.rubble() : k % 3 == 0 ? M.bronzeBand() : M.bronze()));
                                        if (c.at("prize", 0).isAir()) c.set("prize", 0, M.seal());
                                        c.sound(c.pos("pin", 0), SoundEvents.CHAIN_BREAK, 3.0F, 0.5F);
                                        c.sound(c.pos("prize", 0), SoundEvents.ANVIL_LAND, 3.0F, 0.5F);
                                        c.puff(c.pos("prize", 0).above(), ParticleTypes.CAMPFIRE_COSY_SMOKE, 30, 2.5, 0.03);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> t.use("carving", 0).use("pin", 0).use("prize", 0))
                    .add();
        }
    }
}
