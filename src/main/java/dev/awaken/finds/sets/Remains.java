package dev.awaken.finds.sets;

import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Olden;
import dev.awaken.finds.verb.Points;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import static dev.awaken.finds.kit.B.*;

/**
 * The remains (SPEC §62.6): the land between the monuments. Small pieces of the same world from the same kit, each
 * standing only where a monument stands within a thousand blocks, each turned to face it (a kind's south is its front),
 * each with one carving that says which way and how far. No relic and nothing to solve: they are the edge of something
 * big, and how the world tells you where to go without a marker.
 */
final class Remains {
    private Remains() {}

    private static int y(Scene s, int x, int z) {
        return s.g(x, z) + 1;
    }

    /** A carving on the ground at a column. */
    private static void carve(Scene s, int x, int z) {
        Mon.carving(s, x, y(s, x, z), z);
    }

    private static void head(Scene s) {
        int y0 = y(s, 0, 0) - 2;                                   // sunk to its eyes
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 5; y++) for (int z = -2; z <= 2; z++)
            s.put(x, y0 + y, z, z == 2 && Math.abs(x) <= 1 && y >= 2 && y <= 4 ? M.panel() : y == 3 && z == 2 ? M.dark() : y == 5 && (Math.abs(x) == 2 || Math.abs(z) == 2) ? M.blockSlab() : M.block());
        for (int x = -3; x <= 3; x++) for (int z = -2; z <= 3; z++) if ((Math.abs(x) == 3) != (z == 3)) s.put(x, y0 + 5, z, M.blockSlab());
        carve(s, 4, 1);
        M.heap(s, -5, -2, 2.4);
        M.finish(s, 4, 60);
    }

    private static void arch(Scene s) {
        for (int side = -1; side <= 1; side += 2) for (int x = 3; x <= 4; x++) for (int z = -1; z <= 0; z++) for (int y = Math.min(0, s.g(side * x, z)); y <= 7; y++) s.put(side * x, y, z, y == 7 ? M.capital() : y % 5 == 0 ? M.band() : M.course(y));
        for (int x = -4; x <= 4; x++) for (int z = -1; z <= 0; z++) { int up = 8 + (int) Math.round(Math.sqrt(Math.max(0, 16 - x * x)) * 0.75); s.put(x, up, z, M.block()); if (Math.abs(x) <= 2) s.put(x, up + 1, z, x == 0 ? M.seal() : M.band()); }
        carve(s, 5, 1);
        M.finish(s, 3, 50);
    }

    private static void milestones(Scene s) {
        for (int i = 0; i < 4; i++) {
            int z = -6 + i * 4, yy = y(s, 0, z);
            if (i == 1) { s.put(1, yy, z, M.pillar(Direction.Axis.X)); s.put(2, yy, z, M.pillar(Direction.Axis.X)); s.put(0, yy, z, M.capital()); continue; }   // one is down
            s.put(0, yy, z, M.block());
            s.put(0, yy + 1, z, M.pillar(Direction.Axis.Y));
            s.put(0, yy + 2, z, i == 3 ? M.glyph() : M.band());
            s.put(0, yy + 3, z, M.blockSlab());
        }
        s.part("carving", 0, y(s, 0, 6) + 2, 6);
        path(s, 2, -8, 2, 8, 0, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        M.finish(s, 2, 50);
    }

    private static void pier(Scene s) {
        M.pier(s, 0, 0, 2, 13, 5);
        for (int z = 3; z <= 5; z++) for (int x = -2; x <= 2; x++) { s.put(x, 13, z, M.block()); s.put(x, 12, z, z == 5 ? M.blockSlab().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP) : M.stone()); }
        M.ruin(s, -3, 3, 3, 6, 12, 1, 15);
        M.heap(s, 2, 7, 3.0);
        carve(s, 4, 2);
        M.finish(s, 5, 50);
    }

    private static void column(Scene s) {
        for (int z = -6; z <= 6; z++) {
            if (z == -2 || z == 3) continue;                        // it broke in three as it fell
            int yy = y(s, z > 3 ? 1 : 0, z);
            s.put(z > 3 ? 1 : 0, yy, z, z == 6 ? M.capital() : M.pillar(Direction.Axis.Z));
        }
        s.put(0, y(s, 0, -8), -8, M.capital());
        s.put(0, y(s, 0, -8) + 1, -8, M.pillar(Direction.Axis.Y));
        carve(s, -2, -7);
        M.finish(s, 2, 50);
    }

    private static void steps(Scene s) {
        for (int i = 0; i < 7; i++) for (int x = -1; x <= 1; x++) {
            int z = -3 + i;
            for (int y = Math.min(0, s.g(x, z)); y <= i; y++) s.put(x, y, z, y == i ? M.step(s, x, z, Direction.SOUTH) : M.course(y));
        }
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 6; y++) s.put(x, y, 4, y == 6 ? M.flag() : M.course(y));
        s.put(-1, 7, 4, M.wall());
        carve(s, 3, -2);
        M.heap(s, 0, 7, 2.6);
        M.finish(s, 3, 50);
    }

    private static void ringPiece(Scene s) {
        int g = s.g(0, 0);
        M.ring(s, 0, g - 3, 0, 9, 2.0, 1.4, 1, 0, 0, deg -> deg >= 20 && deg <= 160 ? M.ringStuff(deg, false) : null);
        carve(s, 3, 3);
        M.finish(s, 2, 40);
    }

    private static void linkPiece(Scene s) {
        int g = s.g(0, 0);
        MChains.link(0, g + 2.5, 0, 0, 0, 1, 5.6, 3.2, false, (x, y, z, st) -> { if (y > s.g(x, z) - 1) s.put(x, y, z, st); });
        MChains.link(2, g + 1.6, 6, 0.9, 0, 0.5, 5.6, 3.2, true, (x, y, z, st) -> { if (y > s.g(x, z) - 1 && !s.taken(x, y, z)) s.put(x, y, z, st); });
        carve(s, -4, 2);
        M.finish(s, 2, 40);
    }

    private static void lamp(Scene s) {
        int yy = y(s, 0, 0);
        disc(s, 0, 0, 2.3, yy - 1, M.block());
        disc(s, 0, 0, 1.3, yy, M.block());
        for (int y = yy + 1; y <= yy + 5; y++) s.put(0, y, 0, y == yy + 3 ? M.band() : M.wall());
        s.put(0, yy + 6, 0, M.lamp(true));
        s.put(0, yy + 7, 0, M.blockSlab());
        carve(s, 3, 1);
        M.finish(s, 2, 40);
    }

    private static void bed(Scene s) {
        int yy = y(s, 0, 0);
        for (int z = -1; z <= 1; z++) s.put(0, yy, z, M.block());
        s.put(0, yy + 1, -1, stair(M.blockStairs(), Direction.NORTH));
        s.put(0, yy + 1, 0, M.blockSlab());
        s.put(0, yy + 1, 1, M.blockSlab());
        for (int z = -2; z <= 2; z += 4) for (int y = yy; y <= yy + 3; y++) s.put(z == -2 ? -1 : 1, y, z, M.wall());
        s.put(-1, yy + 4, -2, M.blockSlab());
        s.put(1, yy + 4, 2, M.blockSlab());
        carve(s, 2, -1);
        s.put(-2, yy, 1, candles(2, false));
        M.finish(s, 3, 50);
    }

    private static void trough(Scene s) {
        int yy = y(s, 0, 0);
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) { s.put(x, yy - 1, z, M.block()); s.put(x, yy, z, Math.abs(x) == 2 || Math.abs(z) == 1 ? M.block() : WATER); }
        for (int z = 2; z <= 5; z++) { s.put(0, y(s, 0, z) - 1, z, M.stone()); s.put(-1, y(s, -1, z), z, M.blockSlab()); s.put(1, y(s, 1, z), z, M.blockSlab()); }
        carve(s, 3, -1);
        M.finish(s, 4, 50);
    }

    private static void slab(Scene s) {
        for (int x = -2; x <= 2; x++) for (int z = -3; z <= 3; z++) {
            int yy = y(s, x, z);
            double r = Math.hypot(x, z);
            s.put(x, yy, z, Math.abs(x) == 2 || Math.abs(z) == 3 ? M.bronzeBand() : Math.abs(r - 1.6) < 0.5 ? M.seal() : M.door());
        }
        carve(s, 4, 0);
        M.heap(s, -4, -3, 2.2);
        M.finish(s, 2, 50);
    }

    private static void rim(Scene s) {
        for (int deg = 200; deg <= 340; deg += 3) {
            int x = (int) Math.round(Math.cos(Math.toRadians(deg)) * 9), z = 6 + (int) Math.round(Math.sin(Math.toRadians(deg)) * 9), top = 2 + (int) (n(s, x * 1.3, z * 1.3, 3.0) * 4);
            for (int y = Math.min(0, s.g(x, z)); y <= top; y++) s.put(x, y, z, y == top ? M.bronzeBand() : M.course(y));
        }
        carve(s, 0, 2);
        M.heap(s, 5, 2, 2.6);
        M.finish(s, 3, 50);
    }

    private static void hand(Scene s) {
        int y0 = y(s, 0, 0) - 1;                                    // a fist, wrist-deep in the ground, knuckles up
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++) for (int z = -1; z <= 2; z++) s.put(x, y0 + y, z, y == 4 && z == 2 ? M.blockSlab() : (y >= 2 && z == 2 && x % 2 != 0) ? M.dark() : M.block());
        for (int y = 2; y <= 3; y++) s.put(-3, y0 + y, 0, M.block());
        carve(s, 4, 1);
        M.finish(s, 3, 60);
    }

    private static void obelisk(Scene s) {
        int yy = y(s, 0, 0);
        disc(s, 0, 0, 2.3, yy - 1, M.block());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) s.put(x, yy, z, M.block());
        for (int y = yy + 1; y <= yy + 10; y++) s.put(y > yy + 6 ? 1 : 0, y, 0, y == yy + 4 ? M.glyph() : y == yy + 7 ? M.band() : M.block());
        s.put(1, yy + 11, 0, M.blockSlab());
        s.part("carving", 0, yy + 4, 0);
        M.finish(s, 3, 50);
    }

    private static int n;

    private static void reg(String id, String name, int reach, Kind.Maker maker, String see, String line) {
        Kind.of(FSet.REMAINS, ++n, id, name).verb(Verb.READ).find().rank(0).anchor(Anchor.FLAT).slope(3).made(Made.FALLEN).reach(reach).loot("remains")
                .see(see).learned("You read what was cut on it. It points at something bigger, out that way.")
                .scene(maker).script(() -> Points.to("carving", line)).play(t -> t.use("carving", 0)).add();
    }

    static void init() {
        reg("remain_head", "A Giant's Head", 8, Remains::head, "A stone head as big as a hut, sunk to its eyes in the ground, with a mask for a face.", "It says nothing at all. But it was set down looking at something.");
        reg("remain_arch", "A Lone Arch", 8, Remains::arch, "One stone arch standing by itself, with nothing on either side of it.", "It says: through here, and keep on.");
        reg("remain_milestones", "The Milestones", 10, Remains::milestones, "Four stone posts in a line, a few strides apart. One has fallen.", "A number, and a name worn away. The posts go on in a line.");
        reg("remain_pier", "A Pier With No Bridge", 9, Remains::pier, "A stone pier taller than the trees, with the stub of an arch on one side and nothing else.", "It says: the road went over here.");
        reg("remain_column", "A Fallen Column", 10, Remains::column, "A fluted stone column lying in the grass in three pieces, its head pointing away.", "It says only who paid for it. It fell the way it was meant to face.");
        reg("remain_steps", "The Steps to Nothing", 9, Remains::steps, "Seven stone steps climbing to a landing in the air, and stopping.", "It says: mind the last step.");
        reg("remain_ring", "A Piece of a Ring", 10, Remains::ringPiece, "The top of a great bronze ring standing out of the ground like the handle of a buried jar.", "A row of marks along the bronze, for measuring something overhead.");
        reg("remain_link", "A Link", 13, Remains::linkPiece, "A bronze chain link as long as a cart, standing on edge in the dirt, with another lying beside it.", "It says: one of nine hundred. Do not move.");
        reg("remain_lamp", "A Lamp Still Lit", 7, Remains::lamp, "A stone lamp on a post, by itself, burning.", "It says: keep it filled. Someone may come this way.");
        reg("remain_bed", "A Bed in the Open", 7, Remains::bed, "A stone bed under the sky, with what is left of a canopy over it. Nobody is on it.", "It says: I woke, and walked away from here. I went that way.");
        reg("remain_trough", "A Stone Trough", 8, Remains::trough, "A stone trough with water still in it, and the stub of a channel leading away.", "It says: this is the overflow. The count is kept that way.");
        reg("remain_slab", "A Door Lying Down", 8, Remains::slab, "A bronze door with a ring of pale inlay, lying flat in the grass, a long way from any wall.", "It says: carried this far. Too heavy. The door it belongs to is on.");
        reg("remain_rim", "A Piece of a Rim", 10, Remains::rim, "A curve of stone wall with a bronze band on its top, like a slice of something round.", "It says: this is the hundredth part of an ear.");
        reg("remain_hand", "A Stone Hand", 8, Remains::hand, "A stone fist the size of a cart, wrist-deep in the ground, knuckles up.", "Cut across the knuckles: the rest of me is where I was standing.");
        reg("remain_obelisk", "A Waymark", 6, Remains::obelisk, "A thin stone post twice the height of a house, leaning a little, with one carved stone in it at head height.", "It says: you are on the way.");
    }
}
