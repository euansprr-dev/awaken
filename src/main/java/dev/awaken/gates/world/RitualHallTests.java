package dev.awaken.gates.world;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.progression.Rank;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static dev.awaken.gates.world.InteriorTests.key;
import static dev.awaken.gates.world.InteriorTests.passable;
import static dev.awaken.gates.world.InteriorTests.standable;

/**
 * The Ritual of the Bell's hall (docs/proposals/the-ritual.md): it fits the Seated One and its statues inside the
 * floor's band, every place the ritual sends you is reachable on foot, only the story's ritual plan builds it, and it
 * reads at default brightness even though it is dark by design.
 */
final class RitualHallTests {
    private RitualHallTests() {}

    static void register() {
        AwakenTests.register("gates_ritual_hall_fits", 400, h -> {
            GatePlan plan = Gates.ritualPlan(0x5EA7EDL);
            h.assertTrue(plan.ritual() && plan.floors() == 1, "the ritual plan is one floor");
            GateLayout l = GateTestAccess.build(plan);
            h.assertTrue(l.main.size() == 1 && l.annex.isEmpty(), "one floor, no annex");
            FloorLayout f = l.main.get(0);
            Room start = f.room(f.start), hall = f.room(f.end);
            h.assertTrue(start.type == Room.Type.START && hall.type == Room.Type.RITUAL && hall.piece == SetPiece.RITUAL_HALL,
                    "you arrive in the antechamber and the hall is the floor's end: " + start.type + " -> " + hall.type);
            h.assertTrue(hall.gw == 3 && hall.gh == 3 && hall.height == PiecesRitual.VAULT_H, "the hall keeps an arena's slots (3x3) under the band");
            h.assertTrue(f.criticalPath().size() == 2 && start.doorTo(hall.id) != null, "the antechamber opens straight into the hall");
            h.assertTrue(start.feature(Room.FeatureKind.ENTRANCE) != null && hall.features.isEmpty(), "an entrance sigil, and no Lord's spot in the hall");
            h.assertTrue(f.baseY + PiecesRitual.VAULT_H < Cells.floorY(1) + Cells.BAND_ABOVE, "the vault stays inside the floor's band");
            Map<Long, BlockState> b = InteriorTests.paintFloor(l, f, 21);
            int floor = f.baseY;
            // the Seated One: seated 22 blocks, its raised hand at most 24 above the dais, inside |u| <= 6.5 and w 33..40
            PiecesRitual.Spot t = PiecesRitual.throne(hall);
            int tx = (int) Math.floor(t.x()), tz = (int) Math.floor(t.z());
            h.assertTrue(RoomGeometry.ceiling(hall, tx, tz) >= PiecesRitual.DAIS_H + 24, "24 blocks of air over the dais for the raised hand");
            for (int x = hall.x0(); x <= hall.x1(); x++) {
                double au = Math.abs(PiecesRitual.u(hall, x));
                if (au > 6.5) continue;
                for (int z = hall.z0(); z <= hall.z1(); z++) {
                    int w = PiecesRitual.w(hall, z);
                    if (w < 34 || w > 40) continue;
                    for (int y = floor + PiecesRitual.DAIS_H; y < floor + PiecesRitual.DAIS_H + 24; y++) {
                        BlockState s = b.get(key(x, y, z));
                        h.assertTrue(s == null || s.isAir() || s.is(Blocks.BARRIER) || s.is(Blocks.LIGHT),
                                "the Seated One's space is clear: " + s + " at u " + au + " w " + w + " row " + (y - floor));
                    }
                }
            }
            // the statues kneel on their plinths with room to stand up
            for (PiecesRitual.Spot s : PiecesRitual.statues(hall)) {
                int x = (int) Math.floor(s.x()), z = (int) Math.floor(s.z());
                h.assertTrue(standable(b.get(key(x, floor + s.dy() - 1, z))), "a statue's plinth at " + x + "," + z);
                for (int y = floor + s.dy(); y < floor + s.dy() + 4; y++) h.assertTrue(passable(b.get(key(x, y, z))), "head room over a statue at " + x + "," + z);
            }
            // on foot from the entrance sigil: the doorway, every plate, every ring segment, the dais, a place by each statue
            Set<Long> seen = walk(f, b, start.centerX(), floor, start.centerZ());
            List<String> missing = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                double[] p = PiecesRitual.plate(hall, k);
                if (!reached(seen, (int) Math.floor(p[0]), floor, (int) Math.floor(p[1]))) missing.add("plate " + k);
            }
            for (int seg = 0; seg < 8; seg++) {
                boolean any = false;
                for (int x = hall.x0(); x <= hall.x1() && !any; x++)
                    for (int z = hall.z0(); z <= hall.z1() && !any; z++)
                        if (PiecesRitual.ringSegment(hall, x, z) == seg && reached(seen, x, floor, z)) any = true;
                if (!any) missing.add("ring segment " + seg);
            }
            if (!reached(seen, tx, floor + 1, hall.z0() + PiecesRitual.DAIS_W) && !reached(seen, tx - 2, floor + 1, hall.z0() + PiecesRitual.DAIS_W))
                missing.add("the dais's first step");
            for (PiecesRitual.Spot s : PiecesRitual.statues(hall)) {
                int x = (int) Math.floor(s.x() + Math.signum(PiecesRitual.xAt(hall, 0) - s.x()) * 2.5), z = (int) Math.floor(s.z());
                if (!reached(seen, x, floor, z)) missing.add("beside the statue at " + x + "," + z);
            }
            h.assertTrue(missing.isEmpty(), "not reachable on foot: " + missing);
            h.succeed();
        });

        AwakenTests.register("gates_ritual_plan_only_for_the_story", 200, h -> {
            for (Rank rank : Rank.values()) {
                for (int i = 0; i < 300; i++) {
                    var roll = dev.awaken.gates.Director.roll(0xA11CEL + i * 7919L + rank.ordinal() * 104729L, rank, new dev.awaken.gates.run.GateMemory(), rank, null);
                    h.assertFalse(roll.plan().ritual(), "the Director never rolls the ritual: " + roll.plan());
                }
            }
            GatePlan old = Gates.prologuePlan(42);
            h.assertTrue(!old.ritual() && old.boss().equals(Theme.BELL_CRYPT.bossId()), "today's story crypt keeps the Bell Warden");
            GateLayout ol = GateTestAccess.build(old.rebuilt(GatePlan.CONTENT_VERSION));
            h.assertTrue(ol.main.size() == old.floors() && ol.main.get(ol.main.size() - 1).room(ol.main.get(ol.main.size() - 1).end).type == Room.Type.BOSS,
                    "an old story crypt rebuilt on this version keeps its floors and its Lord");
            GatePlan early = new GatePlan(9, 2, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE, 1, GatePlan.RITUAL_BOSS, Theme.HIVE, 0);
            h.assertFalse(early.ritual(), "the ritual needs content version 3");
            GatePlan hive = new GatePlan(9, GatePlan.CONTENT_VERSION, Rank.E, Theme.HIVE, Layout.LABYRINTH, List.of(), Twist.NONE, 1, GatePlan.RITUAL_BOSS, Theme.HIVE, 0);
            h.assertFalse(hive.ritual(), "and the Bell Crypt");
            for (GatePlan p : List.of(early, hive)) {
                GateLayout pl = GateTestAccess.build(p);
                for (Room r : pl.main.get(0).rooms) h.assertTrue(r.piece != SetPiece.RITUAL_HALL, "no hall outside the ritual plan");
            }
            for (Theme t : Theme.values()) h.assertFalse(SetPiece.roomPieces(t).contains(SetPiece.RITUAL_HALL), "never an ordinary room's piece");
            h.succeed();
        });

        // Dark by design, readable at default brightness: the gate world has no ambient light at all.
        AwakenTests.register("gates_ritual_hall_light", 400, h -> {
            GatePlan plan = Gates.ritualPlan(0x11647L);
            GateLayout l = GateTestAccess.build(plan);
            FloorLayout f = l.main.get(0);
            Room hall = f.room(f.end);
            Map<Long, BlockState> b = InteriorTests.paintFloor(l, f, 23);
            List<Integer> all = InteriorTests.floorLight(f, b, r -> r.type == Room.Type.RITUAL);
            List<Integer> ante = InteriorTests.floorLight(f, b, r -> r.type == Room.Type.START);
            double median = InteriorTests.median(all);
            double dark = all.stream().filter(v -> v < 3).count() / (double) Math.max(1, all.size());
            int floor = f.baseY;
            StringBuilder spots = new StringBuilder();
            int worstSpot = 15;
            for (int k = 0; k < 3; k++) {
                double[] p = PiecesRitual.plate(hall, k);
                int v = InteriorTests.light(b, (int) Math.floor(p[0]), floor, (int) Math.floor(p[1]), floor - 2, floor + 6);
                spots.append(" plate").append(k).append('=').append(v);
                worstSpot = Math.min(worstSpot, v);
            }
            PiecesRitual.Spot t = PiecesRitual.throne(hall);
            int dais = InteriorTests.light(b, (int) Math.floor(t.x()), floor + 1, hall.z0() + PiecesRitual.DAIS_W, floor - 2, floor + 8);
            spots.append(" dais=").append(dais);
            Awaken.LOG.info("[gates] ritual hall light: median {}, {}% below 3, antechamber median {},{}", median,
                    String.format(Locale.ROOT, "%.0f", dark * 100), InteriorTests.median(ante), spots);
            h.assertTrue(median >= 6, "the hall's floor reads at default brightness: median light " + median + " (needs 6)");
            h.assertTrue(dark <= 0.12, Math.round(dark * 100) + "% of the hall's floor is below light 3 (at most 12%)");
            h.assertTrue(worstSpot >= 8 && dais >= 7, "the plates and the dais stand out:" + spots);
            h.assertTrue(InteriorTests.median(ante) >= 7, "the antechamber is lit like any start room: " + InteriorTests.median(ante));
            h.succeed();
        });
    }

    /** Every standing spot reachable on foot (step up one, drop up to three, two blocks of head room) from a start. */
    static Set<Long> walk(FloorLayout f, Map<Long, BlockState> b, int sx, int sy, int sz) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int y0 = f.baseY - Cells.BAND_BELOW, y1 = f.baseY + Cells.BAND_ABOVE - 1;
        q.add(new int[]{sx, sy, sz});
        seen.add(key(sx, sy, sz));
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + GatePainter.DX[d], nz = c[2] + GatePainter.DZ[d];
                if (nx < -16 || nz < -16 || nx > (f.w + 1) * 16 || nz > (f.h + 1) * 16) continue;
                for (int ny = c[1] + 1; ny >= c[1] - 3; ny--) {
                    if (ny <= y0 || ny + 1 > y1) continue;
                    if (!standable(b.get(key(nx, ny - 1, nz))) || !passable(b.get(key(nx, ny, nz))) || !passable(b.get(key(nx, ny + 1, nz)))) continue;
                    if (ny > c[1] && !passable(b.get(key(c[0], c[1] + 2, c[2])))) continue;
                    if (seen.add(key(nx, ny, nz))) q.add(new int[]{nx, ny, nz});
                    break;
                }
            }
        }
        return seen;
    }

    static boolean reached(Set<Long> seen, int x, int y, int z) {
        for (int dy = -1; dy <= 3; dy++) if (seen.contains(key(x, y + dy, z))) return true;
        return false;
    }
}
