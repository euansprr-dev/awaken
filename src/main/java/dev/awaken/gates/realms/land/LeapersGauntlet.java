package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.CrawlLand;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
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
 * <p>Built so far: the way in, the first stand, the hall "Twice" (the double jump) and the second stand. Still to lay:
 * "Through" (the dash), "Down" (the slam), the Long Run, the tally floor. Sizes are the plan's targets until the walk
 * check runs on the leap's real reach.
 *
 * <p>A hall here is a {@link Hall}: a box cut out of the rock with floor only where a deck is laid. Everything else in
 * it is the pit, fourteen under the way in, with slag at the bottom that nobody reaches: the fall catch has them first.
 */
public final class LeapersGauntlet extends CrawlLand<StoppedWorks.M> {
    private static final BlockState SLAG = Blocks.MAGMA_BLOCK.defaultBlockState();
    /** The way in's floor, and the pit's. */
    static final int Y = 100, PIT = 86;

    /** A box of air with decks in it. A deck is x0, z0, x1, z1 and the height feet stand at. */
    static final class Hall extends Part {
        final int pit, top;
        final List<int[]> decks = new ArrayList<>();

        Hall(String name, int x0, int z0, int x1, int z1, int pit, int top) {
            this.name = name;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.pit = pit;
            this.top = top;
        }

        Hall deck(int dx0, int dz0, int dx1, int dz1, int feet) {
            decks.add(new int[]{dx0, dz0, dx1, dz1, feet});
            return this;
        }

        /** The height feet stand at on the deck over a column, or the pit's. */
        int feet(int x, int z) {
            int best = pit;
            for (int[] d : decks) if (x >= d[0] && x <= d[2] && z >= d[1] && z <= d[3]) best = Math.max(best, d[4]);
            return best;
        }

        @Override
        public int feet2(int x, int z) {
            return x < x0 || x > x1 || z < z0 || z > z1 ? NONE : feet(x, z) * 2;
        }

        @Override
        public int roof(int x, int z) {
            return top;
        }

        /** Where the fall catch sets someone down: the middle of every deck, and both ends of a long one. */
        List<int[]> ledges() {
            List<int[]> out = new ArrayList<>();
            for (int[] d : decks) {
                int x = (d[0] + d[2]) / 2;
                if (d[3] - d[1] > 6) {
                    out.add(new int[]{x, d[4], d[1] + 2});
                    out.add(new int[]{x, d[4], d[3] - 2});
                } else out.add(new int[]{x, d[4], (d[1] + d[3]) / 2});
            }
            return out;
        }
    }

    private final Cave root, stand1, stand2;
    private final Hall wayIn, twice;
    private final List<Leap> leaps = new ArrayList<>();

    public LeapersGauntlet(Ctx ctx) {
        super(ctx, 0x4C454150L);
        root = part(new Cave("root", 0, Y, -140, 6, 6, 6, seed + 1));
        // the way in: a gap of two, a step, a gap of three, all with what anyone has; then the lip of the first gulf
        wayIn = part(new Hall("way_in", -3, -136, 3, -103, PIT, Y + 9)
                .deck(-3, -136, 3, -128, Y).deck(-3, -125, 3, -119, Y).deck(-3, -118, 3, -113, Y + 1).deck(-3, -109, 3, -103, Y + 1));
        // the first stand, in a room off the lip: you have looked at the gulf before you come to it
        part(new Tunnel("to_stand1", 3, Y + 1, -106, 9, Y + 1, -106, 2.2, 4, seed + 2));
        stand1 = part(new Cave("stand1", 13, Y + 1, -106, 6, 6, 6, seed + 3));
        // "Twice": the gulf (six wide, the far side two higher), a step three up, a gap onto a ledge two higher, and a
        // long gap with only a wall beside it
        twice = part(new Hall("twice", -3, -102, 3, -51, PIT, Y + 16)
                .deck(-3, -96, 3, -90, Y + 3).deck(-3, -89, 3, -84, Y + 6).deck(-3, -79, 3, -74, Y + 8).deck(-3, -62, 3, -51, Y + 8));
        stand2 = part(new Cave("stand2", 0, Y + 8, -44, 7, 7, 7, seed + 4));
        done();

        site(SiteKind.MOUTH, 0, Y, -140, 6, "");
        site(SiteKind.PORTAL, 0, Y, -145, 2, "");
        clearSites();

        List<String> none = List.of();
        leg("root", LegKind.ARRIVE, "The stopped works", 0, Y, -140, 6, -1, none, "", "way_in");
        leg("way_in", LegKind.MOVEMENT, "The way in", 0, Y + 1, -116, 16, -1, none, "", "stand1");
        leg("stand1", LegKind.BREATHER, "The first stand", 13, Y + 1, -106, 6, -1, none, "", "twice");
        leg("twice", LegKind.MOVEMENT, "Twice", 0, Y + 6, -78, 26, -1, none, "", "stand2");
        leg("stand2", LegKind.BREATHER, "The second stand", 0, Y + 8, -44, 7, -1, none, "");

        // the first gulf is the way in's to catch: whoever falls there has not stood on anything of "Twice" yet
        course(new Course("way_in", -4, PIT - 2, -137, 4, Y + 12, -97, wayIn.ledges(), true));
        course(new Course("twice", -4, PIT - 2, -96, 4, Y + 18, -50, twice.ledges(), true));

        leaps.add(new Leap("double", 0, Y + 1, -103, 0, Y + 3, -96));
        leaps.add(new Leap("double", 0, Y + 3, -90, 0, Y + 6, -89));
        leaps.add(new Leap("double", 0, Y + 6, -84, 0, Y + 8, -79));
        leaps.add(new Leap("double", 0, Y + 8, -74, 0, Y + 8, -62));
    }

    @Override
    public List<Leap> leaps() {
        return leaps;
    }

    @Override
    protected StoppedWorks.M mats() {
        return StoppedWorks.m();
    }

    // ---- what the works is made of ----------------------------------------------------------------------------------------

    /** Brick, with a course of iron every seventh row and old copper sheet between. */
    @Override
    protected BlockState rock(StoppedWorks.M m, int x, int y, int z) {
        int row = Math.floorMod(y, 7);
        return row == 0 ? m.iron : row == 3 && rnd(x, z, 4101) < 0.5 ? m.sheetOld : m.brick;
    }

    @Override
    protected BlockState deep(StoppedWorks.M m) {
        return m.iron;
    }

    /** Plate on the decks, with a seam of brass along a deck's edges so its end can be read from the one before; slag under the pits. */
    @Override
    protected BlockState floor(StoppedWorks.M m, Part part, int x, int y, int z) {
        if (part instanceof Hall h) {
            if (y < h.pit) return SLAG;
            for (int[] d : h.decks) {
                if (y != d[4] - 1 || x < d[0] || x > d[2] || z < d[1] || z > d[3]) continue;
                return z == d[1] || z == d[3] ? m.brass : m.plate;
            }
        }
        return m.plate;
    }

    @Override
    protected BlockState step(StoppedWorks.M m) {
        return m.ironSlab;
    }

    @Override
    protected BlockState doorBlock(StoppedWorks.M m) {
        return m.bars;
    }

    @Override
    protected void adorn(V v, StoppedWorks.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {}

    /** The works has no window: light stands in its air, every fifth cell, over each deck and under each roof. */
    @Override
    protected void finish(V v, StoppedWorks.M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            if (Math.floorMod(x, 5) != 0 || Math.floorMod(z, 5) != 0) continue;
            for (Hall h : new Hall[]{wayIn, twice}) {
                if (x < h.x0 || x > h.x1 || z < h.z0 || z > h.z1) continue;
                light(v, m, x, h.feet(x, z) + 3, z);
                light(v, m, x, h.top - 2, z);
            }
            for (Cave c : new Cave[]{root, stand1, stand2}) {
                int f = c.feet2(x, z);
                if (f != NONE) light(v, m, x, Part.airFrom(f) + 3, z);
            }
        }
    }

    private void light(V v, StoppedWorks.M m, int x, int y, int z) {
        if (open(x, y, z) && v.get(x, y, z) == null) v.set(x, y, z, m.pool);
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
