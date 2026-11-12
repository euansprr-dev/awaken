package dev.awaken.gates.realms.kit;

/**
 * One set piece of a Realm: it knows its footprint and draws itself into whichever chunk it touches, so a citadel crossing
 * sixty chunks comes out whole from any of them. A piece that really does hang in the air says so with {@link #airborne}
 * and is the only kind the no-floating check lets through.
 */
public abstract class Piece<M> {
    public int x0, z0, x1, z1;
    /** Whether this piece may stand clear of the ground on purpose (clouds, a floating island). */
    public boolean airborne;
    /** A name for the checks' reports. */
    public String label = getClass().getSimpleName();
    /** Whether it may overlap other pieces (lamps on a plaza, a spire on its plinth). */
    public boolean mayOverlap;
    /** Whether a road may begin here (the Mouth's ring, a stair's landing): the one kind of piece allowed to touch a way. */
    public boolean startsWay;

    public final boolean hits(V v) {
        return x1 >= v.x0 - 1 && x0 <= v.x0 + 16 && z1 >= v.z0 - 1 && z0 <= v.z0 + 16;
    }

    protected final int ax(V v) { return Math.max(x0, v.x0 - 1); }
    protected final int bx(V v) { return Math.min(x1, v.x0 + 16); }
    protected final int az(V v) { return Math.max(z0, v.z0 - 1); }
    protected final int bz(V v) { return Math.min(z1, v.z0 + 16); }

    protected final void box(int cx, int cz, int rx, int rz) {
        x0 = cx - rx;
        x1 = cx + rx;
        z0 = cz - rz;
        z1 = cz + rz;
    }

    public final boolean overlaps(Piece<M> o) {
        return x1 >= o.x0 && x0 <= o.x1 && z1 >= o.z0 && z0 <= o.z1;
    }

    public final boolean covers(double x, double z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    /**
     * Whether something of this piece stands within {@code pad} of here, seen from above: its box, unless the piece knows
     * its own shape better (a root's curve, whose box is mostly air). A way's planner keeps camps and stones clear of it.
     */
    public boolean stands(int x, int z, int pad) {
        return x + pad >= x0 && x - pad <= x1 && z + pad >= z0 && z - pad <= z1;
    }

    public abstract void draw(V v, M m);
}
