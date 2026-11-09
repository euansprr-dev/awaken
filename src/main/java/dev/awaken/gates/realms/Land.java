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

    /** One block a moment in the place changes, in the Realm's own coordinates. */
    public record Edit(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {}

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

    /** The blocks a moment changes: the way to the Lord opening, the hall changing mid-fight. Empty for a land without them. */
    public List<Edit> change(String moment) {
        return List.of();
    }

    /** Whether a point is inside the place the Lord is fought in. */
    public boolean inHeart(int x, int y, int z) {
        return false;
    }

    /** Draws the chunk this volume covers, every pass, in order. */
    public abstract void paint(V v);

    /** Mist layers, shafts of light, glowing lamps and still water for the client's air and Lens, in land coordinates. Fixed for the land. */
    public dev.awaken.gates.world.FloorAir air() {
        return dev.awaken.gates.world.FloorAir.NONE;
    }

    /** Whether this land is a place under a roof: no sun reaches it, what light there is comes from its candles and a few cracks. */
    public boolean indoors() {
        return false;
    }

    /** Which way the arch home runs: along Z (facing east and west) or along X. */
    public net.minecraft.core.Direction.Axis portalAxis() {
        return net.minecraft.core.Direction.Axis.Z;
    }

    /** What the System says the first time you stand at the Lord's closed door, or null to say nothing. */
    public String heartClosedLine() {
        return null;
    }

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
