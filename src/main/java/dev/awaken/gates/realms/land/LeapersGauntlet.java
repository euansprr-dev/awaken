package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.CrawlLand;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Leaper's Gauntlet (SPEC §17.10, the plan of 11 Nov 2026 in docs/leapers-gauntlet/PLAN.md): the trial that gives the
 * leap, laid by hand in the Stopped Works' blocks. A works that stopped mid-stroke; what it made is still on its stands.
 * No fighting. The way in is crossed with the moves anyone has and ends at a gulf nobody can cross; a stand gives one
 * move of the leap; the hall past it cannot be crossed without that move and asks for it four times; then the next
 * stand. No hall before the Long Run has a clock or costs anything to fall in.
 *
 * <p>Built so far: the way in, the first stand, the hall "Twice" (the double jump), the second stand, the hall "Through"
 * (the dash: a gap of nine, two presses over the way that still turn over, a gap of eight round a corner, a gulf of
 * twelve), the third stand and "Down" (the slam: a cracked deck under a gantry, three castings stood in a doorway, a ram
 * stuck down in the way with its plate in a sunk bay), the Long Run (three halls on three levels, a well between each
 * two, five rings, a clock) and the tally floor. Sizes are the plan's targets until the walk check runs on the leap's
 * real reach.
 *
 * <p>A hall here is a {@link Hall}: a machine hall seventeen wide cut out of the works, with a way of decks five wide
 * down its middle and nothing else to stand on. Under the decks is the casting pit, eighteen down, its floor a crust of
 * slag that glows; nobody reaches it, because the fall catch has them first (and it is deep enough that the catch
 * cannot miss). The first walk of this land (run 43021300881) was a brick passage seven wide with a light of 15 in a
 * hundred: the kind of hallway the new gates exist not to be. Its second (run 43027608011) had the Stopped Works' walls
 * and twice the light, and was still a shaft: flat walls striped with brass every fourth row, nothing in it bigger than
 * a block. So a hall has its big things now: piers two by two down both walls every eighth cell, an iron beam across
 * from pier to pier under the roof, press heads hung from the roof over the pit at whatever height each stopped at, and
 * a casting floor of lit moulds in an iron grid where the slag was. The walls between are calm: brick and iron in
 * panels, one course of brass in eight, with furnace mouths over the pit, a pipe run, gauge lights, and lamps let into
 * a ribbed roof.
 *
 * <p>A stand's room is a {@link Round}: the stand on a dais in the middle, and over it a press head hung in a well in the
 * roof, which makes one stroke when the stand is taken. The way on from the second and the third stand is barred until
 * that stand is taken ({@code trial_open_dash}, {@code trial_open_slam}); it opens for everyone in the gate, for good,
 * and so does everything a slam moves. Nothing in this land hurts for falling ({@link #soft}).
 */
public final class LeapersGauntlet extends CrawlLand<StoppedWorks.M> {
    /** The way in's floor; the level "Through" is on; how far under a hall's first deck its pit lies; how far over its highest its roof. */
    static final int Y = 100, Y2 = Y + 8, DEEP = 18, HEAD = 10;
    /** The Long Run's second and third levels (its first is the way in's), and its par in ticks. */
    static final int L2 = Y - 10, L3 = Y - 20, PAR = 85 * 20;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The glow over the casting floor, and what a press head is made of (the Gauntlet's own ram). */
    private static final BlockState GLOW = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13),
            RAM = dev.awaken.gates.GatesRegistry.PRESS.get().defaultBlockState();
    /** The bars that wait for a stand: a box each, x0 y0 z0 x1 y1 z1. */
    private static final int[] DOOR_DASH = {-4, Y2, -34, 4, Y2 + 5, -34}, DOOR_SLAM = {56, Y2, 7, 56, Y2 + 5, 15};
    /** A cracked deck from wall to wall of a well: the row it is laid in, the lip it lies under, and the ladder from it back up to that lip. */
    private record Deck(String id, int x0, int z0, int x1, int z1, int row, int lip, int lx, int lz, Direction face, boolean again) {}

    /** Castings stood in a doorway, each three high: the cells they fill, and the box a slam has to land in to clear them. */
    private record Cast(String id, int x0, int z0, int x1, int z1, int feet, int[] plate, boolean again) {}

    private static final Deck[] DECKS = {
            new Deck("deck", 70, 8, 76, 14, Y2 - 6, Y2, 70, 11, Direction.EAST, false),
            new Deck("run_deck1", 132, -116, 138, -109, Y - 1, Y + 5, 135, -109, Direction.NORTH, true),
            new Deck("run_deck2", 151, -7, 157, 0, L2 - 3, L2 + 3, 154, -7, Direction.SOUTH, true)};
    private static final Cast[] CASTS = {
            new Cast("castings", 101, 10, 101, 12, Y, new int[]{94, Y - 1, 7, 108, Y + 2, 15}, false),
            new Cast("run_castings", 153, -27, 155, -27, L2 + 3, new int[]{151, L2 + 2, -34, 157, L2 + 5, -20}, true)};
    /** A press over the way: how deep its head is and how far it lifts; the beat of the three in "Through" and how much later each next one is. */
    static final int RAM_TALL = 3, RAM_LIFT = 5, RAM_BEAT = 64, RAM_LAG = 10;

    /** A box of air with decks in it and, here and there, a wall standing in it. A deck is x0, z0, x1, z1 and the height feet stand at. */
    static final class Hall extends Part {
        /** The height of its first deck (what its walls are dressed by), its pit's floor and its roof. */
        final int base, pit, top;
        final List<int[]> decks = new ArrayList<>(), walls = new ArrayList<>(), piers = new ArrayList<>();
        /** Where a beam crosses from a pier to the one facing it: how far along the hall. */
        final List<Integer> beams = new ArrayList<>();
        final boolean alongZ;

        Hall(String name, int x0, int z0, int x1, int z1, int base, int rise) {
            this.name = name;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.base = base;
            pit = base - DEEP;
            top = base + rise + HEAD;
            alongZ = z1 - z0 >= x1 - x0;
        }

        /**
         * Piers two by two against both long walls every eighth cell, from the casting floor to the roof; none where a
         * deck comes to the wall. Where both of a pair stand, a beam crosses between them. Call it after the decks.
         */
        Hall piers() {
            for (int a = (alongZ ? z0 : x0) + 4; a + 1 <= (alongZ ? z1 : x1) - 2; a += 8) {
                int stood = 0;
                for (int side = 0; side < 2; side++) {
                    int[] p = alongZ ? new int[]{side == 0 ? x0 : x1 - 1, a, side == 0 ? x0 + 1 : x1, a + 1} : new int[]{a, side == 0 ? z0 : z1 - 1, a + 1, side == 0 ? z0 + 1 : z1};
                    boolean clear = true;
                    for (int[] d : decks) if (p[2] >= d[0] - 1 && p[0] <= d[2] + 1 && p[3] >= d[1] - 1 && p[1] <= d[3] + 1) clear = false;
                    if (!clear) continue;
                    piers.add(p);
                    stood++;
                }
                if (stood == 2) beams.add(a);
            }
            return this;
        }

        Hall deck(int dx0, int dz0, int dx1, int dz1, int feet) {
            decks.add(new int[]{dx0, dz0, dx1, dz1, feet});
            return this;
        }

        /** A wall from the pit to the roof: the works' own rock left standing (something to run along). */
        Hall wall(int wx0, int wz0, int wx1, int wz1) {
            walls.add(new int[]{wx0, wz0, wx1, wz1});
            return this;
        }

        /** The deck over a column, or null over the pit. */
        int[] deckAt(int x, int z) {
            int[] best = null;
            for (int[] d : decks) if (x >= d[0] && x <= d[2] && z >= d[1] && z <= d[3] && (best == null || d[4] > best[4])) best = d;
            return best;
        }

        @Override
        public int feet2(int x, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return NONE;
            for (int[] w : walls) if (x >= w[0] && x <= w[2] && z >= w[1] && z <= w[3]) return NONE;
            for (int[] w : piers) if (x >= w[0] && x <= w[2] && z >= w[1] && z <= w[3]) return NONE;
            int[] d = deckAt(x, z);
            return (d == null ? pit : d[4]) * 2;
        }

        @Override
        public int roof(int x, int z) {
            return top;
        }

        /** Where the fall catch sets someone down: the middle of every deck, and both ends of a long one. */
        List<int[]> ledges() {
            List<int[]> out = new ArrayList<>();
            for (int[] d : decks) {
                int x = (d[0] + d[2]) / 2, z = (d[1] + d[3]) / 2;
                if (d[3] - d[1] > 6) {
                    out.add(new int[]{x, d[4], d[1] + 2});
                    out.add(new int[]{x, d[4], d[3] - 2});
                } else if (d[2] - d[0] > 6) {
                    out.add(new int[]{d[0] + 2, d[4], z});
                    out.add(new int[]{d[2] - 2, d[4], z});
                } else out.add(new int[]{x, d[4], z});
            }
            return out;
        }
    }

    /**
     * A stand's room: round, its floor level, with a dais in the middle (three by three and a block up, a ring of half
     * steps round it) and a well in the roof over the dais for the press to hang in.
     */
    static final class Round extends Part {
        final int cx, cz, feet, r, height;

        Round(String name, int cx, int feet, int cz, int r, int height) {
            this.name = name;
            this.cx = cx;
            this.cz = cz;
            this.feet = feet;
            this.r = r;
            this.height = height;
            x0 = cx - r;
            x1 = cx + r;
            z0 = cz - r;
            z1 = cz + r;
        }

        double d(int x, int z) {
            return Math.hypot(x - cx, z - cz);
        }

        /** The cell the stand's plate lies in, and the press head's lowest cell when it is at rest. */
        int plate() {
            return feet + 1;
        }

        int head() {
            return feet + 8;
        }

        @Override
        public int feet2(int x, int z) {
            double d = d(x, z);
            if (d > r + 0.4) return NONE;
            return d <= 1.5 ? (feet + 1) * 2 : d <= 2.9 ? feet * 2 + 1 : feet * 2;
        }

        @Override
        public int roof(int x, int z) {
            return d(x, z) <= 2.9 ? feet + height + 6 : feet + height;
        }
    }

    /** A plain room: a box with a level floor, and here and there a part of the floor sunk. */
    static final class Room extends Part {
        final int feet, top;
        final List<int[]> lows = new ArrayList<>();

        Room(String name, int x0, int z0, int x1, int z1, int feet, int height) {
            this.name = name;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.feet = feet;
            top = feet + height;
        }

        Room low(int lx0, int lz0, int lx1, int lz1, int f) {
            lows.add(new int[]{lx0, lz0, lx1, lz1, f});
            return this;
        }

        @Override
        public int feet2(int x, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return NONE;
            for (int[] l : lows) if (x >= l[0] && x <= l[2] && z >= l[1] && z <= l[3]) return l[4] * 2;
            return feet * 2;
        }

        @Override
        public int roof(int x, int z) {
            return top;
        }
    }

    private final Hall wayIn, twice, through, east, run1, run2, run3;
    private final List<Hall> halls = new ArrayList<>();
    private final Round[] rounds = new Round[3];
    private final Room cast, bay, tally;
    private final Timed timed;
    private final List<Leap> leaps = new ArrayList<>();
    private final List<Stand> stands = new ArrayList<>();
    private final List<Ram> rams = new ArrayList<>();
    private final List<Slam> slams = new ArrayList<>();
    private final List<Spot> spots = new ArrayList<>();

    public LeapersGauntlet(Ctx ctx) {
        super(ctx, 0x4C454150L);
        part(new Cave("root", 0, Y, -140, 6, 6, 6, seed + 1));
        // the way in: a gap of two, a step, a gap of three, all with what anyone has; then the lip of the first gulf, and a
        // deck off it to the east wall, where the first stand's room is
        wayIn = part(new Hall("way_in", -8, -136, 8, -103, Y, 1)
                .deck(-2, -136, 2, -128, Y).deck(-2, -125, 2, -119, Y).deck(-2, -118, 2, -113, Y + 1).deck(-2, -109, 2, -103, Y + 1).deck(3, -108, 8, -104, Y + 1).piers());
        part(new Tunnel("to_stand1", 8, Y + 1, -106, 14, Y + 1, -106, 2.4, 5, seed + 2));
        rounds[0] = part(new Round("stand1", 20, Y + 1, -106, 6, 7));
        // "Twice": the gulf (six wide, the far side two higher), a step three up, a gap onto a ledge two higher, and a
        // long gap with only a wall beside it
        twice = part(new Hall("twice", -8, -102, 8, -51, Y + 1, 7)
                .deck(-2, -96, 2, -90, Y + 3).deck(-2, -89, 2, -84, Y + 6).deck(-2, -79, 2, -74, Y + 8).deck(-2, -62, 2, -51, Y + 8)
                .wall(-3, -74, -3, -62).piers());
        rounds[1] = part(new Round("stand2", 0, Y2, -44, 7, 7));
        // "Through": a gap of nine; then the way turns east, with a gap of eight just past the turn and a gulf of twelve
        part(new Tunnel("to_through", 0, Y2, -38, 0, Y2, -30, 2.6, 5, seed + 5));
        through = part(new Hall("through", -8, -30, 8, 19, Y2, 0).deck(-2, -30, 2, -25, Y2).deck(-2, -15, 2, 13, Y2).deck(3, 9, 8, 13, Y2).piers());
        east = part(new Hall("through_east", 9, 3, 41, 19, Y2, 0).deck(9, 9, 10, 13, Y2).deck(19, 9, 25, 13, Y2).deck(38, 9, 41, 13, Y2).piers());
        rounds[2] = part(new Round("stand3", 48, Y2, 11, 7, 7));
        part(new Tunnel("to_down", 54, Y2, 11, 62, Y2, 11, 2.6, 5, seed + 7));
        // "Down": a gantry over a well with a cracked deck across it five down; under the well, a room whose way on is a
        // doorway three castings stand in; past them, a room with a sunk bay and, in the way out, a ram stuck down
        part(new Room("gantry", 63, 7, 69, 15, Y2, 7));
        part(new Room("well", 70, 8, 76, 14, Y, 15));
        part(new Tunnel("under", 77, Y, 11, 84, Y, 11, 2.4, 5, seed + 8));
        cast = part(new Room("castings", 85, 5, 99, 17, Y, 9));
        part(new Room("gate2", 100, 10, 104, 12, Y, 3));
        bay = part(new Room("hammer", 105, 5, 123, 17, Y, 11).low(109, 9, 113, 13, Y - 4).low(114, 9, 114, 13, Y - 3).low(115, 9, 115, 13, Y - 2).low(116, 9, 116, 13, Y - 1));
        part(new Room("gate3", 124, 9, 128, 13, Y, 9));
        part(new Room("landing", 129, 5, 141, 17, Y, 8));
        // the Long Run: three halls on three levels, each run the other way to the one before; a well with a cracked deck
        // takes you down from one to the next. Up the first (a gap, a gulf, a step, a gap of nine, a gap beside a wall, a
        // gulf of twelve), back along the second (a gap of nine, two presses, a step, a gulf of twelve, castings in a
        // doorway), and a last sprint down the third (a gap, a gap beside a wall, a gap of nine, and the line)
        part(new Room("run_door", 133, 1, 137, 4, Y, 5));
        run1 = part(new Hall("run1", 127, -104, 143, 0, Y, 6)
                .deck(133, -8, 137, 0, Y).deck(133, -20, 137, -12, Y).deck(133, -34, 137, -27, Y + 2).deck(133, -42, 137, -35, Y + 5)
                .deck(133, -60, 137, -52, Y + 5).deck(133, -79, 137, -71, Y + 5).deck(133, -104, 137, -92, Y + 5)
                .wall(132, -70, 132, -61).piers());
        part(new Room("run1_out", 133, -108, 137, -105, Y + 5, 5));
        part(new Room("well1", 132, -116, 138, -109, L2, Y + 12 - L2));
        part(new Room("well1_out", 139, -114, 145, -110, L2, 5));
        run2 = part(new Hall("run2", 146, -116, 162, -12, L2, 4)
                .deck(146, -114, 151, -110, L2).deck(152, -114, 156, -100, L2).deck(152, -90, 156, -62, L2).deck(152, -61, 156, -54, L2 + 3)
                .deck(152, -41, 156, -29, L2 + 3).deck(152, -25, 156, -12, L2 + 3)
                .wall(146, -28, 162, -26).piers());
        part(new Room("run2_gate", 153, -28, 155, -26, L2 + 3, 3));
        part(new Room("run2_out", 152, -11, 156, -8, L2 + 3, 5));
        part(new Room("well2", 151, -7, 157, 0, L3, L2 + 10 - L3));
        part(new Room("well2_out", 158, -5, 164, -1, L3, 5));
        run3 = part(new Hall("run3", 165, -80, 181, 0, L3, 1)
                .deck(165, -5, 170, -1, L3).deck(171, -14, 175, -1, L3).deck(171, -26, 175, -18, L3).deck(171, -45, 175, -37, L3)
                .deck(171, -80, 175, -55, L3)
                .wall(170, -36, 170, -27).piers());
        part(new Room("run3_out", 171, -84, 175, -81, L3, 5));
        tally = part(new Room("tally", 166, -98, 180, -85, L3, 9));
        done();
        halls.addAll(List.of(wayIn, twice, through, east, run1, run2, run3));

        site(SiteKind.MOUTH, 0, Y, -140, 6, "");
        site(SiteKind.PORTAL, 0, Y, -145, 2, "");
        // a way home before the Long Run, for whoever will not run it today, and the way out at its end
        site(SiteKind.PORTAL, 135, Y, 16, 2, "");
        site(SiteKind.PORTAL, 173, L3, -97, 2, "");
        clearSites();

        List<String> none = List.of();
        leg("root", LegKind.ARRIVE, "The stopped works", 0, Y, -140, 6, -1, none, "", "way_in");
        leg("way_in", LegKind.MOVEMENT, "The way in", 0, Y + 1, -116, 16, -1, none, "", "stand1");
        leg("stand1", LegKind.BREATHER, "The first stand", 20, Y + 1, -106, 6, -1, none, "", "twice");
        leg("twice", LegKind.MOVEMENT, "Twice", 0, Y + 6, -78, 26, -1, none, "", "stand2");
        leg("stand2", LegKind.BREATHER, "The second stand", 0, Y2, -44, 7, -1, none, "", "through");
        leg("through", LegKind.MOVEMENT, "Through", 0, Y2, -8, 24, -1, none, "", "stand3");
        leg("stand3", LegKind.BREATHER, "The third stand", 48, Y2, 11, 7, -1, none, "", "down");
        leg("down", LegKind.MOVEMENT, "Down", 92, Y, 11, 34, -1, none, "", "landing");
        leg("landing", LegKind.BREATHER, "The landing", 135, Y, 11, 6, -1, none, "", "run1");
        leg("run1", LegKind.MOVEMENT, "The Long Run", 135, Y + 5, -56, 54, -1, none, "", "run2");
        leg("run2", LegKind.MOVEMENT, "The Long Run", 154, L2, -70, 54, -1, none, "", "run3");
        leg("run3", LegKind.MOVEMENT, "The Long Run", 173, L3, -41, 44, -1, none, "", "tally");
        leg("tally", LegKind.BREATHER, "The tally floor", 173, L3, -91, 7, -1, none, "");

        // the first gulf is the way in's to catch: whoever falls there has not stood on anything of "Twice" yet
        course(new Course("way_in", -9, Y - DEEP - 2, -137, 9, Y + 14, -97, wayIn.ledges(), true));
        course(new Course("twice", -9, Y - DEEP - 2, -96, 9, Y + 22, -50, twice.ledges(), true));
        course(new Course("through", -9, Y2 - DEEP - 2, -31, 8, Y2 + 12, 20, through.ledges(), true));
        course(new Course("through", 9, Y2 - DEEP - 2, 2, 42, Y2 + 12, 20, east.ledges(), true));
        // the Long Run's pit throws back to the last deck stood on, and takes nothing
        course(new Course("run1", 126, Y - DEEP - 2, -105, 144, Y + 18, 1, run1.ledges(), true));
        course(new Course("run2", 145, L2 - DEEP - 2, -117, 163, L2 + 16, -11, run2.ledges(), true));
        course(new Course("run3", 164, L3 - DEEP - 2, -81, 182, L3 + 13, 1, run3.ledges(), true));

        leaps.add(new Leap("double", 0, Y + 1, -103, 0, Y + 3, -96));
        leaps.add(new Leap("double", 0, Y + 3, -90, 0, Y + 6, -89));
        leaps.add(new Leap("double", 0, Y + 6, -84, 0, Y + 8, -79));
        leaps.add(new Leap("double", 0, Y + 8, -74, 0, Y + 8, -62));
        leaps.add(new Leap("dash", 0, Y2, -25, 0, Y2, -15));
        leaps.add(new Leap("dash", 10, Y2, 11, 19, Y2, 11));
        leaps.add(new Leap("dash", 25, Y2, 11, 38, Y2, 11));
        // the halls that teach end here; the Long Run's crossings are for the walk check only, and are shown to nobody
        List<Leap> taught = List.copyOf(leaps);
        leaps.add(new Leap("double", 135, Y, -20, 135, Y + 2, -27));
        leaps.add(new Leap("double", 135, Y + 2, -34, 135, Y + 5, -35));
        leaps.add(new Leap("dash", 135, Y + 5, -42, 135, Y + 5, -52));
        leaps.add(new Leap("double", 135, Y + 5, -60, 135, Y + 5, -71));
        leaps.add(new Leap("dash", 135, Y + 5, -79, 135, Y + 5, -92));
        leaps.add(new Leap("dash", 154, L2, -100, 154, L2, -90));
        leaps.add(new Leap("double", 154, L2, -62, 154, L2 + 3, -61));
        leaps.add(new Leap("dash", 154, L2 + 3, -54, 154, L2 + 3, -41));
        leaps.add(new Leap("double", 173, L3, -26, 173, L3, -37));
        leaps.add(new Leap("dash", 173, L3, -45, 173, L3, -55));

        String[] moves = {"double", "dash", "slam"};
        for (int i = 0; i < 3; i++) stands.add(new Stand(moves[i], rounds[i].cx, rounds[i].plate(), rounds[i].cz, rounds[i].head()));

        // two presses over the long deck of "Through", the second a beat later than the first, so a dash rides the beat
        // down the line; set between the hall's beams, and clear of the turn at the deck's far end (a third there could
        // be walked round by its corner)
        int n = 0;
        for (int z : new int[]{-5, 3}) rams.add(new Ram(-2, z, 2, z + 2, Y2, RAM_TALL, RAM_LIFT, RAM_BEAT, -RAM_LAG * n++, "", RAM, mats().iron));
        // the ram stuck down in the way out of "Down": four deep, lifted for good by a slam on its plate
        rams.add(new Ram(125, 9, 127, 13, Y, 4, 4, 0, 0, "hammer", RAM, mats().iron));
        // two more on the Long Run's second level, on the same beat
        n = 0;
        for (int z : new int[]{-85, -77}) rams.add(new Ram(152, z, 156, z + 2, L2, RAM_TALL, RAM_LIFT, RAM_BEAT, -RAM_LAG * n++, "", RAM, mats().iron));

        for (Deck d : DECKS) slams.add(new Slam(d.id(), d.x0(), d.row(), d.z0(), d.x1(), d.row() + 3, d.z1(), d.again()));
        for (Cast c : CASTS) slams.add(new Slam(c.id(), c.plate()[0], c.plate()[1], c.plate()[2], c.plate()[3], c.plate()[4], c.plate()[5], c.again()));
        slams.add(new Slam("hammer", 109, Y - 5, 9, 113, Y - 2, 13));

        timed = new Timed(new int[]{133, Y, -8, 137, Y + 3, -1}, new int[]{171, L3, -80, 175, L3 + 3, -76},
                List.of(new int[]{135, Y + 5, -38, -1}, new int[]{135, Y + 5, -96, -1}, new int[]{154, L2, -66, 1}, new int[]{154, L2 + 3, -18, 1}, new int[]{173, L3, -41, -1}),
                PAR, new int[]{135, Y, 11, 180});

        spots.add(new Spot("reach", 0, Y + 1, -103, 0, Y + 3, -96));
        int[] count = new int[2];
        for (Leap l : taught) {
            // the presses are the second place the dash is asked for
            if (l.move().equals("dash") && count[1] == 1) spots.add(new Spot("use/dash/" + ++count[1], 0, Y2, -8, 0, Y2, 8));
            int k = l.move().equals("double") ? 0 : 1;
            spots.add(new Spot("use/" + l.move() + "/" + ++count[k], l.x0(), l.y0(), l.z0(), l.x1(), l.y1(), l.z1()));
        }
        spots.add(new Spot("use/slam/1", 68, Y2, 11, 73, Y2 - 5, 11));
        spots.add(new Spot("use/slam/2", 91, Y, 11, 97, Y, 11));
        spots.add(new Spot("use/slam/3", 107, Y, 11, 111, Y - 4, 11));
    }

    @Override
    public List<Stand> stands() {
        return stands;
    }

    @Override
    public List<Ram> rams() {
        return rams;
    }

    @Override
    public List<Slam> slams() {
        return slams;
    }

    @Override
    public List<Spot> spots() {
        return spots;
    }

    @Override
    public Timed timed() {
        return timed;
    }

    @Override
    public boolean soft() {
        return true;
    }

    @Override
    public List<Leap> leaps() {
        return leaps;
    }

    @Override
    protected StoppedWorks.M mats() {
        return StoppedWorks.m();
    }

    // ---- what the works is made of: the Stopped Works' own walls ----------------------------------------------------------

    /** Calm walls: brick and iron in panels five by four, a panel of old copper now and then, and one course of brass in eight. */
    @Override
    protected BlockState rock(StoppedWorks.M m, int x, int y, int z) {
        if (Math.floorMod(y, 8) == 4) return m.brass;
        double n = rnd(Math.floorDiv(x, 5) * 31L + Math.floorDiv(z, 5), Math.floorDiv(y, 4), 4103);
        return n < 0.55 ? m.brick : n < 0.9 ? m.iron : m.sheetOld;
    }

    @Override
    protected BlockState deep(StoppedWorks.M m) {
        return m.plate;
    }

    /** A deck is plate with a strip of brass across each end, so where it stops can be read from the one before; a pit's floor is slag; rooms are plate and old sheet. */
    @Override
    protected BlockState floor(StoppedWorks.M m, Part part, int x, int y, int z) {
        if (part instanceof Hall h) {
            int[] d = h.deckAt(x, z);
            // the casting floor: moulds two by two, still lit, in a grid of iron
            if (d == null) return Math.floorMod(x, 4) >= 2 && Math.floorMod(z, 4) >= 2 ? m.gauge : m.iron;
            // the plate before the castings on the Long Run
            if (h == run2 && z >= -34 && z <= -29) return m.brass;
            boolean alongZ = d[3] - d[1] >= d[2] - d[0];
            boolean end = alongZ ? z == d[1] || z == d[3] : x == d[0] || x == d[2];
            return end ? m.brass : Math.floorMod(x + z, 5) == 0 ? m.sheetOld : m.plate;
        }
        if (part instanceof Round r) {
            // the dais in brass round an iron bed for the stand; a cross of brass in the floor leading to it
            double d = r.d(x, z);
            if (d <= 1.5) return x == r.cx && z == r.cz ? m.iron : m.brass;
            if (d > 2.9 && (x == r.cx || z == r.cz)) return m.brass;
            return rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 4104) < 0.3 ? m.sheetOld : m.plate;
        }
        // the plates a slam is made on: in front of the castings, and the floor of the sunk bay
        if (part == cast && x >= 94 && z >= 8 && z <= 14) return m.brass;
        if (part == bay && x >= 109 && x <= 113 && z >= 9 && z <= 13) return m.brass;
        // the tally floor: a round plate of brass with an iron heart
        if (part == tally) {
            double d = Math.hypot(x - 173, z + 91);
            if (d <= 4.5) return d <= 1.5 ? m.iron : m.brass;
        }
        if (part instanceof Cave && Math.floorMod(x, 4) == 0) return m.brass;
        return rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 4104) < 0.3 ? m.sheetOld : m.plate;
    }

    @Override
    protected BlockState step(StoppedWorks.M m) {
        return m.ironSlab;
    }

    @Override
    protected BlockState doorBlock(StoppedWorks.M m) {
        return m.bars;
    }

    /**
     * What is set into the walls and the roof. A hall's walls are dressed by its first deck's height, not by the pit under
     * them: furnace mouths three and four under the decks, glowing over the slag; the pipe run two over them; a gauge
     * light every fifth cell at three, with its pool; a second pipe at seven. A room's and a passage's are dressed from
     * their own floor. Every roof is plate on ribs of brass with a lamp let in every fourth cell each way.
     */
    @Override
    protected void adorn(V v, StoppedWorks.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean hall = part instanceof Hall, free = v.get(x, y, z) == null;
        int up = y - (part instanceof Hall h ? h.base : Part.airFrom(part.feet2(x, z)));
        if (wx != 0 || wz != 0) {
            int ax = x + wx, az = z + wz, along = wx != 0 ? az : ax;
            if (hall && (up == -3 || up == -4) && Math.floorMod(along, 6) <= 1) v.set(ax, y, az, m.furnace);
            else if (!hall && !(part instanceof Tunnel) && part.x1 - part.x0 > 6 && up == 0 && Math.floorMod(along, 6) <= 1) {
                v.set(ax, y, az, m.furnace);
                if (free && Math.floorMod(along, 6) == 0) v.set(x, y, z, m.pool);
            } else if (up == 2 || hall && up == 7) v.set(ax, y, az, wx != 0 ? m.pipeZ : m.pipeX);
            else if (up == 3 && Math.floorMod(along, 5) == 0) {
                v.set(ax, y, az, m.gauge);
                if (free) v.set(x, y, z, m.pool);
            } else if (up == 1 && rnd(ax * 7L + y, az, 4105) < 0.06) v.set(ax, y, az, rnd(ax, az, 4106) < 0.5 ? m.gear : m.cog);
        }
        if (underRoof && !onFloor) {
            boolean lamp = Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2;
            v.set(x, y + 1, z, lamp ? m.gauge : Math.floorMod(z, 3) == 0 ? m.brass : rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 4107) < 0.7 ? m.plate : m.iron);
            if (lamp && free) v.set(x, y, z, m.pool);
        }
    }

    /** A hall's big things: its piers in iron with a collar of brass at the decks and under the roof, its beams, and the press heads that stopped where they were. */
    @Override
    protected void special(V v, StoppedWorks.M m) {
        for (Hall h : halls) {
            if (h.x1 < v.x0 - 1 || h.x0 > v.x0 + 16 || h.z1 < v.z0 - 1 || h.z0 > v.z0 + 16) continue;
            for (int[] p : h.piers) {
                for (int x = Math.max(p[0], v.x0 - 1); x <= Math.min(p[2], v.x0 + 16); x++) for (int z = Math.max(p[1], v.z0 - 1); z <= Math.min(p[3], v.z0 + 16); z++) {
                    for (int y = h.pit; y < h.top; y++) v.set(x, y, z, y == h.base - 1 || y == h.top - 1 ? m.brass : m.iron);
                }
            }
            int n = 0;
            for (int a : h.beams) {
                // the beam, one under the roof's ribs, from the pier on one side to the pier on the other
                int by = h.top - 3;
                for (int c = (h.alongZ ? h.x0 : h.z0) + 2; c <= (h.alongZ ? h.x1 : h.z1) - 2; c++) put(v, h.alongZ ? c : a, by, h.alongZ ? a : c, m.iron);
                // a press head in the bay past it, over the pit on one side and then the other: a rod from the roof, a head
                // three by three and two deep, as far down as it had got
                int side = n++ % 2 == 0 ? 4 : -4, mid = (h.alongZ ? h.x0 + h.x1 : h.z0 + h.z1) / 2 + side, along = a + 4;
                int drop = 2 + (int) (rnd(a, side, 4110) * 5), hx = h.alongZ ? mid : along, hz = h.alongZ ? along : mid;
                boolean over = false;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) over |= h.feet2(hx + dx, hz + dz) != h.pit * 2;
                // none beside a press that still runs: the line over the way is read on its own
                for (Ram r : rams) over |= hx + 2 >= r.x0() - 1 && hx - 2 <= r.x1() + 1 && hz + 2 >= r.z0() - 1 && hz - 2 <= r.z1() + 1;
                if (over) continue;
                for (int y = h.top - 1; y >= h.top - drop; y--) put(v, hx, y, hz, m.iron);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 2; dy++) put(v, hx + dx, h.top - drop - dy, hz + dz, RAM);
            }
        }
        // a press over the way as it stands when nobody is near: one that runs is up, the stuck one is down; its rod goes
        // on up into the roof
        for (Ram r : rams) {
            int lowest = r.feet() + (r.period() > 0 ? r.travel() : 0), cx = (r.x0() + r.x1()) / 2, cz = (r.z0() + r.z1()) / 2;
            for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) for (int y = lowest; y < lowest + r.tall(); y++) put(v, x, y, z, r.block());
            for (int y = lowest + r.tall(); open(cx, y, cz); y++) put(v, cx, y, cz, r.rod());
        }
        // over each stand, the press that makes its stroke when the stand is taken
        for (Round r : rounds) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy < 2; dy++) put(v, r.cx + dx, r.head() + dy, r.cz + dz, RAM);
            for (int y = r.head() + 2; open(r.cx, y, r.cz); y++) put(v, r.cx, y, r.cz, m.iron);
        }
        // "Down": the cracked deck from wall to wall of the well, with a ladder from it back up to the gantry; and the
        // three castings in their doorway, each an iron body, a chest of gears and a lit head
        for (Deck d : DECKS) {
            for (int x = d.x0(); x <= d.x1(); x++) for (int z = d.z0(); z <= d.z1(); z++) put(v, x, d.row(), z, m.grate);
            for (int y = d.row() + 1; y < d.lip(); y++) put(v, d.lx(), y, d.lz(), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, d.face()));
        }
        for (Cast c : CASTS) for (int x = c.x0(); x <= c.x1(); x++) for (int z = c.z0(); z <= c.z1(); z++) for (int dy = 0; dy < 3; dy++) put(v, x, c.feet() + dy, z, casting(m, dy));
        // a ring of the Long Run: an arch of brass over the lane, lit at its crown and its shoulders
        for (int[] r : timed.rings()) {
            for (int dx = -2; dx <= 2; dx++) put(v, r[0] + dx, r[1] + 5, r[2], dx == 0 || Math.abs(dx) == 2 ? m.gauge : m.brass);
            for (int dy = 0; dy < 5; dy++) for (int side = -2; side <= 2; side += 4) put(v, r[0] + side, r[1] + dy, r[2], m.brass);
        }
    }

    private static BlockState casting(StoppedWorks.M m, int dy) {
        return dy == 0 ? m.iron : dy == 1 ? m.gear : m.gauge;
    }

    /** Sets a block in this chunk's own air, margin included. */
    private void put(V v, int x, int y, int z, BlockState s) {
        if (x < v.x0 - 1 || x > v.x0 + 16 || z < v.z0 - 1 || z > v.z0 + 16) return;
        if (open(x, y, z) && v.get(x, y, z) == null) v.set(x, y, z, s);
    }

    /** Light over every deck and floor every fifth cell, the moulds' glow every fourth cell over the casting floor, and the bars that wait for a stand shut. */
    @Override
    protected void finish(V v, StoppedWorks.M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            for (Part p : parts) {
                int f = p.feet2(x, z);
                if (f == NONE) continue;
                int feet = Part.airFrom(f);
                boolean pit = p instanceof Hall h && h.deckAt(x, z) == null;
                if (pit && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) air(v, x, feet + 1, z, GLOW);
                if (!pit && Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0) air(v, x, feet + 3, z, m.pool);
            }
        }
        // light under the cracked deck, so the way on shows through it
        for (Deck d : DECKS) for (int x = d.x0() + 1; x < d.x1(); x += 4) for (int z = d.z0() + 1; z < d.z1(); z += 4) {
            if (x >= v.x0 && x <= v.x0 + 15 && z >= v.z0 && z <= v.z0 + 15) air(v, x, d.row() - 1, z, m.pool);
        }
        // light on each stand and on the press over it
        for (Round r : rounds) for (int side = -2; side <= 2; side += 4) {
            if (r.cx + side < v.x0 || r.cx + side > v.x0 + 15 || r.cz < v.z0 || r.cz > v.z0 + 15) continue;
            air(v, r.cx + side, r.feet + 3, r.cz, m.pool);
            air(v, r.cx + side, r.head() + 1, r.cz, GLOW);
        }
        for (int[] d : new int[][]{DOOR_DASH, DOOR_SLAM}) {
            for (int x = Math.max(d[0], v.x0); x <= Math.min(d[3], v.x0 + 15); x++) for (int z = Math.max(d[2], v.z0); z <= Math.min(d[5], v.z0 + 15); z++) {
                for (int y = d[1]; y <= d[4]; y++) if (open(x, y, z)) v.set(x, y, z, m.bars);
            }
        }
    }

    private void air(V v, int x, int y, int z, BlockState s) {
        if (open(x, y, z) && v.get(x, y, z) == null) v.set(x, y, z, s);
    }

    private List<Edit> unbar(int[] d) {
        List<Edit> out = new ArrayList<>();
        for (int x = d[0]; x <= d[3]; x++) for (int y = d[1]; y <= d[4]; y++) for (int z = d[2]; z <= d[5]; z++) if (open(x, y, z)) out.add(new Edit(x, y, z, AIR));
        return out;
    }

    /** A stand taken opens the bars past it; the walk check goes through both, as a player who has taken them does. */
    @Override
    public List<Edit> change(String moment) {
        if (moment.equals("trial_open_dash")) return unbar(DOOR_DASH);
        if (moment.equals("trial_open_slam")) return unbar(DOOR_SLAM);
        boolean slam = moment.startsWith("slam_"), rearm = moment.startsWith("rearm_");
        if (slam || rearm) {
            String id = moment.substring(moment.indexOf('_') + 1);
            StoppedWorks.M m = mats();
            List<Edit> out = new ArrayList<>();
            for (Deck d : DECKS) if (d.id().equals(id)) {
                for (int x = d.x0(); x <= d.x1(); x++) for (int z = d.z0(); z <= d.z1(); z++) out.add(new Edit(x, d.row(), z, slam ? AIR : m.grate));
            }
            for (Cast c : CASTS) if (c.id().equals(id)) {
                for (int x = c.x0(); x <= c.x1(); x++) for (int z = c.z0(); z <= c.z1(); z++) for (int dy = 0; dy < 3; dy++) out.add(new Edit(x, c.feet() + dy, z, slam ? AIR : casting(m, dy)));
            }
            return out;
        }
        if (moment.equals("walk_open")) {
            List<Edit> out = unbar(DOOR_DASH);
            out.addAll(unbar(DOOR_SLAM));
            for (Slam sl : slams) out.addAll(change("slam_" + sl.id()));
            // the stuck ram lifted: its head out of the way
            for (Ram r : rams) if (r.period() == 0) {
                for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) for (int y = r.feet(); y < r.feet() + r.travel(); y++) out.add(new Edit(x, y, z, AIR));
            }
            return out;
        }
        return super.change(moment);
    }

    /** The air of a works: the heat lying over each casting floor, and the glow of the moulds coming up through it. */
    @Override
    public dev.awaken.gates.world.FloorAir air() {
        dev.awaken.gates.realms.kit.AirPlan air = new dev.awaken.gates.realms.kit.AirPlan()
                .mist(Y - DEEP + 1, 4, 0xff8a30, 0.4F).mist(Y2 - DEEP + 1, 4, 0xff8a30, 0.4F).mist(L2 - DEEP + 1, 4, 0xff8a30, 0.4F).mist(L3 - DEEP + 1, 4, 0xff8a30, 0.4F);
        for (Hall h : halls) {
            air.light((h.x0 + h.x1) / 2, h.pit + 3, (h.z0 + h.z1) / 2, 0xff9a40, 16, 0.8F);
        }
        return air.build();
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    @Override
    public int biome(int x, int y, int z) {
        return 0;
    }

    @Override
    public float mouthYaw() {
        return 0;
    }
}
