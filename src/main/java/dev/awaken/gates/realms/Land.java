package dev.awaken.gates.realms;

import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.progression.Rank;

import java.util.ArrayList;
import java.util.List;

/**
 * One land's drawing, made once per Realm by its program and never changed after, so any worldgen thread can read it.
 * Coordinates are the Realm's own: (0,0) is its middle, y is the world's.
 */
public abstract class Land {
    public record Ctx(Blueprint bp, String id, Rank rank, long seed, int version) {}

    /** A place the live game cares about. */
    public enum SiteKind { MOUTH, PORTAL, WAYSTONE, VILLAGE, DEN, WARDEN, FIND, DELVE, LORD, UPDRAFT }

    public record Site(SiteKind kind, int index, int x, int y, int z, int radius, String what) {}

    public final Ctx ctx;
    private final List<Site> sites = new ArrayList<>();

    protected Land(Ctx ctx) {
        this.ctx = ctx;
    }

    protected final void site(SiteKind kind, int x, int y, int z, int radius, String what) {
        int n = 0;
        for (Site s : sites) if (s.kind() == kind) n++;
        sites.add(new Site(kind, n, x, y, z, radius, what));
    }

    /** The height of the Realm's water, from its blueprint. */
    public final int water() {
        return ctx.bp().land().water();
    }

    public final List<Site> sites() {
        return List.copyOf(sites);
    }

    public final List<Site> sites(SiteKind kind) {
        return sites.stream().filter(s -> s.kind() == kind).toList();
    }

    public final Site site(SiteKind kind) {
        return sites.stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
    }

    /** The top of the ground in a column (the block you'd stand on, never a piece built over it). */
    public abstract int ground(int x, int z);

    /** How far past the land's edge a point is (positive means the wind turns you back there). */
    public double edge(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) - (halfSize() - 48);
    }

    /** Draws the chunk this volume covers, every pass, in order. */
    public abstract void paint(V v);

    /** Which of the blueprint's biomes this point is. */
    public abstract int biome(int x, int y, int z);

    /** How far from the middle the land has anything to walk on. */
    public abstract int halfSize();

    /** Every set piece, for the checks. */
    public abstract List<? extends Piece<?>> pieces();

    /** Lines of ground nothing may be built on: roads, stairs, ways into doors. Each is x0,z0,x1,z1,halfWidth. */
    public abstract List<double[]> ways();

    public abstract float mouthYaw();

    public final Site mouth() {
        return site(SiteKind.MOUTH);
    }
}
