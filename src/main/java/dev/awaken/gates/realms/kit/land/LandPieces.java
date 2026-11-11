package dev.awaken.gates.realms.kit.land;

import dev.awaken.gates.realms.kit.Build;
import dev.awaken.gates.realms.kit.N;
import dev.awaken.gates.realms.kit.N.Hex;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.land.GeneratedLand.Dress;
import dev.awaken.gates.realms.kit.land.GeneratedLand.Kit;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What stands in a rolled land whatever its skin (SPEC §50.9, "Rolled lands"): the way in, a cache, a Waystone, standing
 * stones, a lamp on a post, a broken bridge, a gate, a paved court, and the great builders a skin picks its great thing
 * from. Each is drawn in the skin's own {@link Dress} and stands on the ground that is really there.
 */
public final class LandPieces {
    private LandPieces() {}

    private static BlockState along(BlockState post, Direction.Axis axis) {
        return post.hasProperty(RotatedPillarBlock.AXIS) ? post.setValue(RotatedPillarBlock.AXIS, axis) : post;
    }

    /** One standing stone: thick at the foot, a lit seam at its waist, a broken top. */
    static void stone(V v, Dress d, int x, int gy, int z, int h, long salt) {
        for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
            int far = a * a + c * c;
            if (far == 2 && N.rnd(x + a, z + c, salt) < 0.6) continue;
            int top = far == 0 ? h : far == 1 ? (int) (h * (0.45 + 0.4 * N.rnd(x + a, z + c, salt + 1))) : 1;
            v.foot(x + a, gy, z + c, d.rock());
            for (int k = 1; k <= top; k++) {
                boolean lit = far == 0 && k == Math.max(2, h / 2) || far == 1 && k == Math.max(1, h / 2 - 1) && N.rnd(x + a, z + c, salt + 2) < 0.5;
                v.set(x + a, gy + k, z + c, lit ? d.seam() : k % 4 == 0 ? d.dark() : d.rock());
            }
            if (far == 1 && top >= 1 && v.get(x + a, gy + top + 1, z + c) == null && N.rnd(x + a, z + c, salt + 3) < 0.6) v.set(x + a, gy + top + 1, z + c, d.rockSlab());
        }
        v.set(x, gy + h + 1, z, d.rockSlab());
    }

    /** Where you arrive: a round floor with a lit heart, the arch home behind you, two broken pillars ahead to look between. */
    public static final class Arrival extends Piece<Kit> {
        final int cx, feet, cz;
        final GeneratedLand land;

        Arrival(int cx, int feet, int cz, GeneratedLand land) {
            this.cx = cx;
            this.feet = feet;
            this.cz = cz;
            this.land = land;
            box(cx, cz, 16, 18);
            startsWay = true;
            mayOverlap = true;
            label = "the way in";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double far = Math.hypot(x - cx, z - cz);
                if (far > 6.4 || v.get(x, feet - 1, z) == null) continue;
                Hex t = N.hex(x, z, 1.9);
                double k = N.rnd(t.q(), t.r(), 301);
                BlockState b = far < 1.2 ? d.seam() : far < 3.6 ? (t.edge() > 0.78 ? d.dark() : k < 0.5 ? d.stone() : d.rock()) : far < 4.5 ? d.rock() : k < 0.3 ? d.dark() : d.stone();
                v.set(x, feet - 1, z, b);
                for (int y = feet; y < feet + 4; y++) if (v.get(x, y, z) != null) v.set(x, y, z, null);
                // the rim is broken: half blocks of it still stand, but never across the arch or the way out
                if (far > 5.5 && Math.abs(x - cx) > 2 && N.rnd(x, z, 302) < 0.45) v.set(x, feet, z, d.rockSlab());
            }
            arch(v, d);
            pillar(v, d, cx - 12, cz - 13, 12, 303);
            pillar(v, d, cx + 13, cz - 15, 8, 304);
        }

        /** The arch home, three deep: jambs on stepped feet, a lit keystone, a coping, and the opening the arch's light stands in. */
        private void arch(V v, Dress d) {
            int az = cz + 6;
            for (int k = -1; k <= 1; k++) for (int s = -4; s <= 4; s++) {
                int x = cx + s, z = az + k, a = Math.abs(s);
                v.foot(x, feet - 1, z, a <= 1 ? d.stone() : d.rock());
                if (a <= 1) v.set(x, feet - 1, z, k == 0 ? d.dark() : d.stone());
                for (int h = 0; h <= 5; h++) {
                    BlockState b = null;
                    if (a <= 1) {
                        if (h == 3) b = a == 0 ? (k == 0 ? d.seam() : d.rock()) : Build.under(d.rockStairs(), s < 0 ? Direction.WEST : Direction.EAST);
                        else if (h == 4) b = d.rock();
                        else if (h == 5 && k == 0) b = d.rockSlab();
                    } else if (a <= 3) {
                        if (h <= 4) b = h == 0 || h == 4 ? d.dark() : d.rock();
                        else if (k == 0) b = a == 2 ? d.rockSlab() : null;
                    } else if (k == 0) b = h == 0 ? d.rock() : h == 1 ? Build.stair(d.rockStairs(), s < 0 ? Direction.EAST : Direction.WEST) : null;
                    if (b != null || a <= 3 && h <= 5) v.set(x, feet + h, z, b);
                }
            }
            for (int s = -3; s <= 3; s += 6) v.set(cx + s, feet + 5, az, d.lamp());
        }

        /** A broken pillar of the land's own rock, in courses, its top gone. */
        private void pillar(V v, Dress d, int px, int pz, int h, long salt) {
            int gy = land.ground(px, pz);
            for (int a = -2; a <= 2; a++) for (int c = -2; c <= 2; c++) {
                double far = N.hexMetric(a, c);
                if (far > 2.1) continue;
                int top = far > 1.2 ? (int) (h * (0.3 + 0.5 * N.rnd(px + a, pz + c, salt))) : h - (int) (2 * N.rnd(px + a, pz + c, salt + 1));
                v.foot(px + a, gy, pz + c, d.rock());
                for (int k = 1; k <= top; k++) v.set(px + a, gy + k, pz + c, k % 5 == 0 ? d.dark() : k == h / 2 && far < 1.2 ? d.seam() : d.rock());
                if (v.get(px + a, gy + top + 1, pz + c) == null && N.rnd(px + a, pz + c, salt + 2) < 0.5) v.set(px + a, gy + top + 1, pz + c, d.rockSlab());
            }
        }
    }

    /** Something left out in the land: a cache on a worn stone, and what someone lit beside it. */
    public static final class Cache extends Piece<Kit> {
        final int x, gy, z;
        final long salt;
        final GeneratedLand land;

        Cache(int x, int z, long salt, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.salt = salt;
            this.land = land;
            gy = land.ground(x, z);
            box(x, z, 2, 2);
            mayOverlap = true;
            label = "a cache";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
                if (a != 0 && c != 0 && N.rnd(a + 3, c + 3, salt) < 0.4) continue;
                v.foot(x + a, gy, z + c, d.stone());
                v.set(x + a, gy, z + c, a == 0 && c == 0 ? d.rock() : d.stone());
                for (int y = gy + 1; y <= gy + 3; y++) if (v.get(x + a, y, z + c) != null) v.set(x + a, y, z + c, null);
            }
            v.set(x, gy + 1, z, m.cache);
            int sx = N.rnd(salt, 1, 2) < 0.5 ? 1 : -1;
            if (V.holds(v.get(x + sx, gy, z))) v.set(x + sx, gy + 1, z, d.lamp());
        }
    }

    /** A Waystone: a low plinth, the stone you lay a hand on, two posts with their lamps. */
    public static final class Marker extends Piece<Kit> {
        public final int x, gy, z;
        final GeneratedLand land;

        Marker(int x, int z, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.land = land;
            gy = land.ground(x, z);
            box(x, z, 3, 3);
            mayOverlap = true;
            label = "a Waystone";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            for (int a = -2; a <= 2; a++) for (int c = -2; c <= 2; c++) {
                if (Math.abs(a) + Math.abs(c) > 3) continue;
                v.foot(x + a, gy, z + c, d.rock());
                v.set(x + a, gy, z + c, (a + c & 1) == 0 ? d.dark() : d.rock());
                for (int y = gy + 1; y <= gy + 5; y++) if (v.get(x + a, y, z + c) != null) v.set(x + a, y, z + c, null);
                if (Math.abs(a) + Math.abs(c) == 1) v.set(x + a, gy + 1, z + c, d.rockSlab());
            }
            v.set(x, gy + 1, z, d.rock());
            v.set(x, gy + 2, z, m.shrine);
            for (int s = -1; s <= 1; s += 2) {
                int px = x + 2 * s, pz = z - s;
                for (int y = gy + 1; y <= gy + 2; y++) v.set(px, y, pz, d.post());
                v.set(px, gy + 3, pz, d.lamp());
            }
        }
    }

    /** A ring of standing stones round a place, open toward the way that comes to it. */
    public static final class StoneRing extends Piece<Kit> {
        final int[][] stones;
        final long salt;
        final GeneratedLand land;

        /** @param wayX,wayZ a point on the way in: no stone stands toward it */
        public StoneRing(int cx, int cz, double radius, int count, double wayX, double wayZ, long salt, GeneratedLand land) {
            this.salt = salt;
            this.land = land;
            double gap = Math.atan2(wayZ - cz, wayX - cx);
            java.util.List<int[]> out = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                double a = gap + Math.PI * 2 * (i + 0.5) / count, turn = Math.abs(Math.atan2(Math.sin(a - gap), Math.cos(a - gap)));
                int x = (int) Math.round(cx + Math.cos(a) * radius), z = (int) Math.round(cz + Math.sin(a) * radius);
                // not in the mouth of the place, and never on the line you walk
                if (turn < 0.7 || land.wayDist(x, z) < 4.2) continue;
                out.add(new int[]{x, land.ground(x, z), z, 4 + (int) (N.rnd(i, salt, 5) * 5)});
            }
            stones = out.toArray(new int[0][]);
            box(cx, cz, (int) radius + 3, (int) radius + 3);
            mayOverlap = true;
            label = "standing stones";
        }

        @Override
        public void draw(V v, Kit m) {
            for (int[] s : stones) if (s[0] >= v.x0 - 3 && s[0] <= v.x0 + 18 && s[2] >= v.z0 - 3 && s[2] <= v.z0 + 18) stone(v, land.dress(), s[0], s[1], s[2], s[3], salt + s[0] * 31L + s[2]);
        }
    }

    /** A lamp someone left burning: a post of the land's timber and the lamp on it. */
    public static final class LampPost extends Piece<Kit> {
        final int x, gy, z, h;
        final GeneratedLand land;

        public LampPost(int x, int z, int h, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.land = land;
            gy = land.ground(x, z);
            box(x, z, 1, 1);
            label = "a lamp";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            v.foot(x, gy, z, d.stone());
            for (int y = gy + 1; y <= gy + h; y++) v.set(x, y, z, d.post());
            v.set(x, gy + h + 1, z, d.lamp());
        }
    }

    /**
     * A bridge with two spans gone. It runs along x at one height; {@code cells} says, from west to east, where deck still
     * lies. Spans that touch a lip hang from it on a beam; a span that stands free stands on a trestle down to whatever is
     * under it.
     */
    public static final class Bridge extends Piece<Kit> {
        final int x0, feet, z;
        final boolean[] cells;
        final GeneratedLand land;

        Bridge(int x0, int feet, int z, boolean[] cells, GeneratedLand land) {
            this.x0 = x0;
            this.feet = feet;
            this.z = z;
            this.cells = cells;
            this.land = land;
            box(x0 + cells.length / 2, z, cells.length / 2 + 2, 3);
            mayOverlap = true;
            startsWay = true;
            label = "a broken bridge";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            BlockState beam = along(d.post(), Direction.Axis.X);
            for (int i = 0; i < cells.length; i++) {
                if (!cells[i]) continue;
                int x = x0 + i;
                for (int c = -1; c <= 1; c++) v.set(x, feet - 1, z + c, d.planks());
                boolean free = true, first = i == 0 || !cells[i - 1], end = i == cells.length - 1 || !cells[i + 1];
                for (int k = i; k >= 0 && cells[k]; k--) if (k == 0) free = false;
                for (int k = i; k < cells.length && cells[k]; k++) if (k == cells.length - 1) free = false;
                if (!free) v.set(x, feet - 2, z, beam);
                else if (first || end) {
                    // a trestle: two posts down to the ground, however far that is, tied under the deck
                    for (int c = -1; c <= 1; c += 2) v.foot(x, feet - 2, z + c, d.post());
                    v.set(x, feet - 2, z, along(d.post(), Direction.Axis.Z));
                } else v.set(x, feet - 2, z, beam);
            }
            // what fell lies under the gaps
            for (int i = 1; i < cells.length - 1; i++) {
                if (cells[i] || N.rnd(x0 + i, z, 311) > 0.6) continue;
                int x = x0 + i, c = (int) (N.rnd(x0 + i, z, 312) * 3) - 1, gy = v.groundBelow(x, feet - 3, z + c);
                if (gy > 0 && v.get(x, gy + 1, z + c) == null) v.set(x, gy + 1, z + c, d.planks());
            }
        }
    }

    /** A gate: two pillars of the land's rock, a beam between them, a lantern on a chain. It runs along z. */
    public static final class Gate extends Piece<Kit> {
        final int x, z, h;
        final GeneratedLand land;

        Gate(int x, int z, int h, GeneratedLand land) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.land = land;
            box(x, z, 2, 7);
            mayOverlap = true;
            startsWay = true;
            label = "a gate";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            // both heads at one height, whatever the ground does under each foot: the beam has to meet them both
            int top = Math.max(land.ground(x, z - 5), land.ground(x, z + 5)) + h;
            for (int side = -1; side <= 1; side += 2) {
                int pz = z + side * 5, gy = land.ground(x, pz);
                for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
                    boolean corner = a != 0 && c != 0, mid = a == 0 && c == 0;
                    v.foot(x + a, gy, pz + c, d.rock());
                    v.set(x + a, gy + 1, pz + c, d.dark());
                    if (corner) {
                        v.set(x + a, gy + 2, pz + c, d.rockSlab());
                        continue;
                    }
                    for (int y = gy + 2; y <= top; y++) {
                        if (!mid && y == top - 1) continue; // the shaft narrows under its head
                        v.set(x + a, y, pz + c, y == top ? (mid ? d.seam() : d.rockSlab()) : y == top - 2 ? d.ore() : y % 5 == 2 && mid ? d.seam() : y % 5 == 0 ? d.dark() : d.rock());
                    }
                }
                v.set(x, top + 1, pz, d.lamp());
            }
            BlockState beam = along(d.post(), Direction.Axis.Z);
            for (int c = -4; c <= 4; c++) v.set(x, top - 3, z + c, beam);
            v.set(x, top - 4, z, d.chain());
            v.set(x, top - 5, z, m.hung);
        }
    }

    /** A court's paving, laid over whatever level ground is there: flags with dark joints, a broken lit ring, ore at the middle. */
    public static final class Court extends Piece<Kit> {
        final int cx, feet, cz;
        final double radius;
        final GeneratedLand land;

        Court(int cx, int feet, int cz, double radius, GeneratedLand land) {
            this.cx = cx;
            this.feet = feet;
            this.cz = cz;
            this.radius = radius;
            this.land = land;
            box(cx, cz, (int) radius + 1, (int) radius + 1);
            mayOverlap = true;
            startsWay = true;
            label = "a court";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double far = Math.hypot(x - cx, z - cz);
                if (far > radius || v.get(x, feet - 1, z) == null || v.get(x, feet, z) != null) continue;
                Hex t = N.hex(x, z, 2.4);
                double k = N.rnd(t.q(), t.r(), 321);
                boolean ring = Math.abs(far - radius * 0.7) < 0.6;
                v.set(x, feet - 1, z, far < 1.5 ? d.ore() : ring ? (N.rnd(x, z, 322) < 0.6 ? d.seam() : d.dark()) : t.edge() > 0.8 ? d.dark() : k < 0.5 ? d.stone() : d.rock());
            }
        }
    }

    /**
     * One of the great builders: a crown of horns round a crater. Each horn is a claw of the land's rock as tall as a
     * tower, leaning in over the bowl, banded with ore, cracked with what still burns in it, lit at the tip. The bowl's
     * floor is the Lord's: level, flagged, with two rings and a heart that burn. The side the way comes in by has no horn.
     */
    public static final class Crown extends Piece<Kit> {
        final int cx, cz, floor;
        final double arena;
        /** For each horn: where its foot is, the ground there, how tall, how thick at the foot, how far its tip leans in. */
        final double[][] horns;
        final GeneratedLand land;
        final long salt;

        Crown(int cx, int cz, int floor, double arena, double ring, double open, int count, long salt, GeneratedLand land) {
            this.cx = cx;
            this.cz = cz;
            this.floor = floor;
            this.arena = arena;
            this.land = land;
            this.salt = salt;
            horns = new double[count][];
            for (int i = 0; i < count; i++) {
                double a = open + Math.PI * 2 * (i + 0.5) / count, bx = cx + Math.cos(a) * ring, bz = cz + Math.sin(a) * ring;
                double tall = (i % 2 == 0 ? 52 : 36) + N.rnd(i, salt, 1) * 12;
                horns[i] = new double[]{bx, bz, land.ground((int) Math.round(bx), (int) Math.round(bz)), tall, 5.4 + N.rnd(i, salt, 2) * 1.6, 8 + N.rnd(i, salt, 3) * 5};
            }
            box(cx, cz, (int) ring + 9, (int) ring + 9);
            mayOverlap = true;
            startsWay = true;
            label = "the crown";
        }

        @Override
        public void draw(V v, Kit m) {
            Dress d = land.dress();
            for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
                double far = Math.hypot(x - cx, z - cz);
                if (far <= arena) floor(v, d, x, z, far);
                for (int i = 0; i < horns.length; i++) {
                    double[] h = horns[i];
                    if (Math.abs(x - h[0]) > h[4] + h[5] + 2 || Math.abs(z - h[1]) > h[4] + h[5] + 2) continue;
                    horn(v, d, x, z, h, i);
                }
            }
        }

        private void floor(V v, Dress d, int x, int z, double far) {
            if (v.get(x, floor - 1, z) == null || v.get(x, floor, z) != null) return;
            double a = Math.atan2(z - cz, x - cx);
            BlockState b;
            if (far < 2.5) b = d.seam();
            else if (d.crust() != null && (Math.abs(far - arena * 0.4) < 0.6 || Math.abs(far - arena * 0.72) < 0.6)) b = N.rnd(x, z, 331) < 0.75 ? d.crust() : d.seam();
            else if (far > 3 && Math.abs(Math.sin(a * 3)) * far < 0.55) b = d.dark();
            else {
                Hex t = N.hex(x, z, 2.6);
                b = t.edge() > 0.8 ? d.dark() : N.rnd(t.q(), t.r(), 332) < 0.55 ? d.stone() : d.rock();
            }
            v.set(x, floor - 1, z, b);
        }

        private void horn(V v, Dress d, int x, int z, double[] h, int i) {
            double ux = cx - h[0], uz = cz - h[1], ul = Math.max(1e-6, Math.hypot(ux, uz));
            ux /= ul;
            uz /= ul;
            int gy = (int) h[2], tall = (int) h[3];
            boolean footed = false;
            for (int y = gy - 4; y <= gy + tall; y++) {
                // never thinner than a few cells: each course has to stand on the one under it, however far the horn leans
                double t = Math.max(0, (y - gy) / h[3]), lean = h[5] * Math.pow(t, 1.7), rad = Math.max(1.3, h[4] * Math.pow(1 - t, 0.8) + 0.45);
                double px = h[0] + ux * lean, pz = h[1] + uz * lean;
                if (N.hexMetric(x - px, z - pz) > rad) continue;
                if (!footed && y <= gy - 2) {
                    // whatever the ground does under it, a horn's foot goes down into it; higher up it leans out over the air
                    v.foot(x, y - 1, z, d.rock());
                    footed = true;
                }
                BlockState b;
                if (y >= gy + tall - 2) b = d.seam();
                else if (Math.floorMod(y + i * 3, 13) == 0) b = d.ore();
                else if (N.rnd(x * 7L + y, z, salt + i) < 0.05 + 0.1 * t) b = d.seam();
                else b = Math.floorMod(y, 5) == 2 ? d.dark() : d.rock();
                v.set(x, y, z, b);
            }
        }
    }
}
