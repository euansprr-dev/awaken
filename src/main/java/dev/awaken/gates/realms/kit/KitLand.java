package dev.awaken.gates.realms.kit;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.check.LookChecks;
import dev.awaken.gates.realms.kit.piece.Waystone;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What every land built from the kit needs and none should write again: its list of pieces, the rule that nothing is built
 * through anything else or across a road, Waystones and the clear ground around what you've come to touch (SPEC §50.8).
 */
public abstract class KitLand<M extends Mats> extends Land {
    public final long seed;
    protected final Random r;
    public final List<Piece<? super M>> pieces = new ArrayList<>();
    protected final List<double[]> ways = new ArrayList<>();
    private final List<Site> clearings = new ArrayList<>();

    /** @param salt set per land, so two lands on one seed don't roll the same numbers */
    protected KitLand(Ctx ctx, long salt) {
        super(ctx);
        seed = ctx.seed();
        r = new Random(seed ^ salt);
    }

    protected abstract M mats();

    protected final double jit(double d) {
        return (r.nextDouble() - 0.5) * 2 * d;
    }

    /** Puts a piece down only where it's clear: not through another piece, not on a road, a stair or a door's way (SPEC §50.6). */
    protected final boolean place(Piece<? super M> p) {
        if (LookChecks.clash(p, pieces, ways) != null) return false;
        pieces.add(p);
        return true;
    }

    protected final void drawPieces(V v, M m) {
        for (Piece<? super M> p : pieces) if (p.hits(v)) p.draw(v, m);
    }

    /** A Waystone, and the site the live game finds it by: the site's y is the stone you touch. */
    protected final void waystone(int x, int z, String name) {
        Waystone w = new Waystone(x, z, this);
        pieces.add(w);
        site(SiteKind.WAYSTONE, x, w.gy + 2, z, 4, name);
    }

    /** Call once the sites are placed: nothing grows on what you've come to look at or put a hand on. */
    protected final void clearSites() {
        for (Site s : sites()) if (s.kind() == SiteKind.WAYSTONE || s.kind() == SiteKind.FIND || s.kind() == SiteKind.UPDRAFT || s.kind() == SiteKind.PORTAL) {
            clearings.add(new Site(s.kind(), s.index(), s.x(), s.y(), s.z(), Math.max(s.radius(), 3), s.what()));
        }
    }

    /** Whether this column is kept clear of growth. */
    protected final boolean tended(int x, int z) {
        for (Site s : clearings) if (Math.abs(s.x() - x) <= s.radius() && Math.abs(s.z() - z) <= s.radius()) return true;
        return false;
    }

    /**
     * Plans this land's run with {@link WayPlanner} (SPEC §50.9): an open land drawn by hand, on plan version 2, that
     * wasn't given its run leg by leg. Call last in the constructor, once every site is placed. False: it couldn't be
     * planned, and the land keeps the run read off its sites (the open-land check keeps it switched off).
     */
    protected final boolean designWay(WayPlanner.Ground ok, WayPlanner.Dress dress) {
        return designWay(ok, dress, java.util.Set.of());
    }

    /** As above, with the labels of this land's pieces a camp may stand among (a tree's crown, a pole, a stub of wall). */
    protected final boolean designWay(WayPlanner.Ground ok, WayPlanner.Dress dress, java.util.Set<String> among) {
        return designWay(ok, dress, among, List.of());
    }

    /** As above, with places the way must pass straight after its first camp, in order (x, y, z: a bridge over a gorge). */
    protected final boolean designWay(WayPlanner.Ground ok, WayPlanner.Dress dress, java.util.Set<String> among, List<int[]> via) {
        boolean done = WayPlanner.plan(this, pieces, among, via, ok, dress, seed, new WayPlanner.Build() {
            @Override
            public int den(int x, int z) {
                site(SiteKind.DEN, x, ground(x, z) + 1, z, 10, "");
                return sites(SiteKind.DEN).size() - 1;
            }

            @Override
            public void leg(Leg l) {
                KitLand.this.leg(l.id(), l.kind(), l.name(), l.x(), l.y(), l.z(), l.radius(), l.site(), l.pack(), l.when(), l.exits().toArray(String[]::new));
            }

            @Override
            public void course(Course c) {
                KitLand.this.course(c);
            }

            @Override
            public void stones(dev.awaken.gates.realms.kit.piece.Hops h) {
                pieces.add(h);
            }
        });
        if (!done) dev.awaken.Awaken.LOG.warn("[realms] {}: no way through could be planned ({}); its run stays read off its sites", ctx.id(), WayPlanner.why());
        return done;
    }

    @Override
    public List<? extends Piece<?>> pieces() {
        return List.copyOf(pieces);
    }

    @Override
    public List<double[]> ways() {
        return List.copyOf(ways);
    }
}
