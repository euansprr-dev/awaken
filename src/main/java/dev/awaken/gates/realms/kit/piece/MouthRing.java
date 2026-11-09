package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.N.Hex;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.gates.realms.kit.N.hex;
import static dev.awaken.gates.realms.kit.N.hexMetric;
import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.rnd;
import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.Build.under;

/**
 * Where you arrive: a stepped hexagonal dais that was fine once, six pylons broken at different heights with one lying where it fell, wheat coming
 * back through the paving, and the arch home behind you. The side toward the citadel stays open, so the first thing you see is the valley.
 */
public final class MouthRing extends Piece<Mats> {
    final int cx, gy, cz;
    final Land land;

    public MouthRing(int cx, int gy, int cz, Land land) {
        this.cx = cx;
        this.gy = gy;
        this.cz = cz;
        this.land = land;
        box(cx, cz, 14, 14);
        startsWay = true;
    }

    @Override
    public void draw(V v, Mats m) {
        for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) step(v, m, x, z);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            if (Math.cos(a) > 0.6) continue; // never between you and the citadel
            pylon(v, m, cx + (int) Math.round(Math.cos(a) * 9.5), cz + (int) Math.round(Math.sin(a) * 9.5), new int[]{4, 9, 6, 11, 3, 7}[i], i);
        }
        fallen(v, m);
        arch(v, m);
    }

    /** One column of the dais: the top course, then a ring of slabs, a ring a step lower, slabs again, then what's left of the paving. */
    private void step(V v, Mats m, int x, int z) {
        double d = hexMetric(x - cx, z - cz);
        if (d > 11.5) return;
        if (d > 7.8) {
            Hex t = hex(x, z, 2.1);
            if (rnd(t.q(), t.r(), 112) > lerp(0.62, 0.12, (d - 7.8) / 3.7)) return;
            int g = land.ground(x, z);
            v.set(x, g, z, rnd(t.q(), t.r(), 113) < 0.5 ? m.flag : m.hexstone);
            v.set(x, g + 1, z, null);
            return;
        }
        for (int y = gy + 1; y < gy + 6; y++) v.set(x, y, z, null);
        Hex t = hex(x, z, 1.7);
        double chip = rnd(x, z, 114);
        if (d <= 4.2) {
            // the arrival floor: hexes with a ring of gilt let into it, six lines running out from the middle
            v.foot(x, gy - 1, z, m.pale);
            double ang = Math.atan2(z - cz, x - cx), spoke = Math.abs(Math.sin(3 * ang)) * d;
            v.set(x, gy, z, d < 1.1 ? m.gold : d > 3.3 && d < 3.9 ? (chip < 0.25 ? m.flag : m.gold) : spoke < 0.55 && d > 1.1 ? m.pale : rnd(t.q(), t.r(), 111) < 0.5 ? m.hexstone : m.flag);
        } else if (d <= 5.4) {
            v.foot(x, gy - 1, z, m.stoneA);
            if (chip < 0.14) v.set(x, gy, z, null);
            else v.set(x, gy, z, rnd(t.q(), t.r(), 115) < 0.55 ? m.paleSlab : m.stoneSlab);
        } else if (d <= 6.6) {
            v.foot(x, gy - 2, z, m.stoneA);
            v.set(x, gy - 1, z, rnd(t.q(), t.r(), 116) < 0.5 ? m.flag : m.stoneA);
            v.set(x, gy, z, chip < 0.05 ? m.crumbs : null);
        } else {
            v.foot(x, gy - 2, z, m.stoneA);
            if (chip < 0.22) v.set(x, gy - 1, z, null);
            else v.set(x, gy - 1, z, rnd(t.q(), t.r(), 117) < 0.5 ? m.flagSlab : m.stoneSlab);
        }
    }

    /** A carved pylon on a stepped base: flared capital, shaft in courses, and however much is left of the top. */
    private void pylon(V v, Mats m, int sx, int sz, int h, int i) {
        int g = land.ground(sx, sz);
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a != 0 && b != 0) {
                if (rnd(sx + a, sz + b, 118) < 0.7) v.set(sx + a, g, sz + b, m.deep);
                if (rnd(sx + a, sz + b, 119) < 0.55) v.set(sx + a, g + 1, sz + b, m.stoneSlab);
                continue;
            }
            v.set(sx + a, g, sz + b, m.deep);
        }
        int cap = Math.max(2, h * 2 / 3);
        for (int y = 1; y <= h; y++) {
            v.set(sx, g + y, sz, y == 1 ? m.deep : y == cap + 1 ? m.gold : (y + i) % 3 == 0 ? m.worn : m.pale);
        }
        for (Direction f : sides) {
            int fx = sx + f.getStepX(), fz = sz + f.getStepZ();
            v.set(fx, g + 1, fz, stair(m.stoneStairs, f.getOpposite()));
            for (int y = 2; y < Math.min(cap, h); y++) v.set(fx, g + y, fz, (y + i) % 3 == 1 ? m.stoneA : m.worn);
            if (h > 3) v.set(fx, g + Math.min(cap, h), fz, under(m.paleStairs, f.getOpposite()));
            // wax has run down the stone where it broke, and hangs off the capital
            if (rnd(sx, sz + f.ordinal(), 120 + i) < 0.5 && h > 3) v.set(fx, g + Math.min(cap, h) - 1, fz, m.capped);
        }
        // the broken top: part of a course still sits on the shaft
        Direction lean = sides[i % 4];
        v.set(sx + lean.getStepX(), g + h, sz + lean.getStepZ(), h > 5 ? under(m.stoneStairs, lean.getOpposite()) : stair(m.stoneStairs, lean.getOpposite()));
        v.set(sx, g + h + 1, sz, i % 2 == 0 ? m.candle : stair(m.paleStairs, lean));
        v.set(sx - lean.getStepX(), g + 1 + (h > 4 ? 1 : 0), sz - lean.getStepZ(), m.capped);
    }

    /** The seventh pylon fell a long time ago: drums lying in the paving where they came to rest, half sunk. */
    private void fallen(V v, Mats m) {
        int x0 = cx - 2, z0 = cz + 12;
        for (int k = 0; k < 9; k++) {
            if (k == 3 || k == 6) continue; // the joints between drums
            int x = x0 + k, z = z0 - (k > 5 ? 1 : 0), g = land.ground(x, z);
            v.set(x, g, z, (k & 1) == 0 ? m.pale : m.worn);
            v.set(x, g + 1, z, k == 0 ? stair(m.paleStairs, Direction.EAST) : k == 8 ? stair(m.stoneStairs, Direction.WEST) : (k & 1) == 0 ? m.worn : m.pale);
            v.set(x, g + 1, z + 1, rnd(x, z, 121) < 0.65 ? m.stoneSlab : m.crumbs);
            v.set(x, g + 1, z - 1, stair(m.stoneStairs, Direction.SOUTH));
            if (rnd(x, z, 122) < 0.4 && v.get(x, g + 2, z) == null) v.set(x, g + 2, z, m.crumbs);
        }
    }

    /** Half the arch's clear width at a height, from the floor: straight jambs, then a pointed head. */
    private static double open(int y) {
        return y <= 6 ? 2.5 : y >= 11 ? -1 : 2.5 - Math.pow((y - 6) / 4.4, 1.6) * 2.5;
    }

    /** The arch home behind where you arrive: three deep, a moulded reveal, gilt at the crown, and the hive starting to take it back. */
    private void arch(V v, Mats m) {
        int ax = cx - 6;
        for (int d = -1; d <= 1; d++) for (int s = -6; s <= 6; s++) for (int y = 1; y <= 14; y++) {
            int a = Math.abs(s);
            double o = open(y), reveal = d == 0 ? o + 0.0 : o + 0.9, out = d == 0 ? o + 2.5 : o + 2.1;
            if (y > 11) reveal = -1;
            if (a < reveal + 0.5) {
                if (d == 0 && y <= 11 && a < o + 0.5) v.set(ax, gy + y, cz + s, null);
                continue;
            }
            double outer = y <= 6 ? 5.0 : Math.max(0, 5.0 - (y - 6) * 0.62);
            if (a > outer + (d == 0 ? 0.5 : 0.0) || a > out + 0.5) continue;
            boolean crown = y >= 10 && a <= 1, impost = y == 6 || y == 7 && a >= 3, plinth = y <= 1;
            BlockState b = crown ? m.gold : plinth ? m.deep : impost ? m.deep : a < o + 1.6 && d == 0 ? ((y + a) % 2 == 0 ? m.pale : m.hexstone) : (y + d) % 3 == 0 ? m.worn : m.stoneA;
            v.set(ax + d, gy + y, cz + s, b);
            if (y == 1) v.foot(ax + d, gy, cz + s, m.pale);
        }
        // plinths, stepped out at the foot of each jamb
        for (int side = -1; side <= 1; side += 2) for (int d = -2; d <= 2; d++) for (int a = 3; a <= 6; a++) {
            if (Math.abs(d) < 2 && a < 6) continue;
            int x = ax + d, z = cz + side * a;
            v.foot(x, gy, z, m.deep);
            v.set(x, gy + 1, z, d == -2 ? stair(m.stoneStairs, Direction.EAST) : d == 2 ? stair(m.stoneStairs, Direction.WEST) : stair(m.stoneStairs, side > 0 ? Direction.NORTH : Direction.SOUTH));
        }
        // a level way in, the floor carried from the dais through the arch
        for (int x = ax - 2; x <= cx - 3; x++) for (int z = cz - 3; z <= cz + 3; z++) {
            v.foot(x, gy - 1, z, m.pale);
            v.set(x, gy, z, x < ax - 1 || x > ax + 1 ? (hex(x, z, 1.7).edge() > 0.8 ? m.deep : m.flag) : Math.abs(z - cz) == 3 ? m.deep : m.hexstone);
        }
        // wax taking the top of one jamb, and running down it
        for (int y = 5; y <= 7; y++) v.set(ax + 1, gy + y, cz + 4, y == 6 ? m.capped : m.comb);
        v.set(ax + 1, gy + 4, cz + 4, m.drip);
        v.set(ax, gy + 8, cz - 2, m.drip);
        v.set(ax, gy + 15, cz, m.candle);
        for (int z : new int[]{-5, 5}) v.set(ax + 2, gy + 2, cz + z, m.candle);
    }
}
