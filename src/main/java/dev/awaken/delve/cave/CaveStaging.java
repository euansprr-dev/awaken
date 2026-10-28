package dev.awaken.delve.cave;

import dev.awaken.delve.Lode;
import dev.awaken.delve.LodePlans;
import dev.awaken.delve.Ore;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;

/**
 * Places laid on purpose round a point, as a site search would have laid them (the route test, the tours): each with a
 * pocket of "natural" cave at its joint, and a sinkhole with a handmade lode by its grotto.
 */
final class CaveStaging {
    private CaveStaging() {}

    /** A place of this kind at (x, y, z): y is a sinkhole's rim (feet on the ground) and every other kind's floor. */
    static Spec spec(CaveKind kind, int x, int y, int z, int rot, long seed) {
        Frame f = new Frame(x, y, z, rot);
        return switch (kind) {
            case SINKHOLE -> {
                int rad = 10 + (int) Math.floorMod(seed, 8L), floor = y - 54; // the world's range, 10-17
                int hx = x + rad + 16, hy = floor + 1, hz = z + 3;
                yield new Spec(kind, x, floor, z, 0, seed, true, x - rad - 13, floor, z + 6,
                        new int[]{rad, y, 1, hx, hy, hz, 200, 20, 0});
            }
            case MINE -> {
                Spec bare = new Spec(kind, x, y, z, rot, seed, false, 0, 0, 0, new int[]{22});
                int[] e = Mine.end(bare);
                Frame g = Mine.back(f, 22);
                yield new Spec(kind, x, y, z, rot, seed, true, e[0] + (g.x(0, 9) - g.x(0, 0)), e[1], e[2] + (g.z(0, 9) - g.z(0, 0)), new int[]{22});
            }
            case SEEP -> new Spec(kind, x, y, z, 0, seed, true, x - (int) Seep.HALL_LONG - 9, y, z + 2, new int[]{x + 22, y + 2, z + 1});
            case CHUTE -> new Spec(kind, x, y, z, rot, seed, true, f.x(0, -12), y, f.z(0, -12), new int[0]);
            case MOUTH -> {
                // each form in turn by the seed: an adit, a hill mouth, a doline, a cleft
                int form = (int) Math.floorMod(seed, 4L);
                int L = switch (form) { case 0 -> 20; case 2 -> 7; case 3 -> 24; default -> 30; };
                int drop = switch (form) { case 0 -> 3; case 2 -> 16; case 3 -> (L - Mouth.PORTAL) / 3; default -> 13; };
                int[] p = switch (form) {
                    case 0 -> new int[]{Mouth.ADIT, L, drop, 3, 4, f.x(0, L + 1), y - drop + 1, f.z(0, L + 1), 3, 0};
                    case 2 -> new int[]{Mouth.DOLINE, L, drop, 9, drop, 0, 0, 0, 0, 1};
                    case 3 -> new int[]{Mouth.CLEFT, L, drop, 3, 14, 0, 0, 0, 0, 0};
                    default -> new int[]{Mouth.HILL, L, drop, 7, 9, 0, 0, 0, 0, 1};
                };
                yield new Spec(kind, x, y, z, rot, seed, true, f.x(3, L + 10), y - drop, f.z(3, L + 10), p);
            }
            case GROTTO -> new Spec(kind, x, y, z, rot, seed, true, f.x(2, -Grotto.ROOM - 16), y, f.z(2, -Grotto.ROOM - 16), new int[0]);
        };
    }

    /** The pocket of natural cave the place joins: a rough chamber at the joint. */
    static void cave(Spec s, Sink k) {
        if (!s.joined()) return;
        Carve.blob(k, s.jx() + 0.5, s.jy() + 2.0, s.jz() + 0.5, 4.5, 2.6, 4.0, s.seed() + 99, 0.25, 3.0, s.jy());
    }

    /** A sinkhole's handmade lode: a band of iron through its grotto's far wall with the heart in the middle. */
    static Lode lode(Spec s) {
        BlockPos h = new BlockPos(s.p(Sinkhole.HX), s.p(Sinkhole.HY), s.p(Sinkhole.HZ));
        LongArrayList cells = new LongArrayList();
        cells.add(h.asLong());
        for (int d = -7; d <= 7; d++) for (int dy = -1; dy <= 1; dy++) {
            if (d == 0 && dy == 0) continue;
            if (dy != 0 && Math.floorMod(d * 7 + dy * 3, 4) == 0) continue;
            cells.add(h.offset(Math.floorMod(d * 5, 3) == 0 ? 1 : 0, dy, d).asLong());
        }
        return LodePlans.handmade(Ore.IRON, cells.toLongArray());
    }

    /** Lays the staged sinkhole's lode for every query (worldgen never writes handmade lodes: the command does). */
    static void stageLode(Spec s) {
        if (s.kind() == CaveKind.SINKHOLE && s.p(Sinkhole.LODE) != 0) LodePlans.stage(lode(s));
    }
}
