package dev.awaken.opening;

import dev.awaken.city.City;
import dev.awaken.city.CityLayout;
import dev.awaken.city.CityPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where the opening happens, in this world's coordinates (SPEC §16.4): the Runners' Roost, Choir Square and the light
 * above it, the Bell Bridge, the infirmary's beds and the Registry's Assessment Lens in the Hunter Association. Plan
 * coordinates are blocks from Carillon's centre (x east, z south) and heights from the street (ry 0 is the paving's top
 * block); the High Terrace is lifted {@link #TERRACE} above it.
 */
public final class OpeningPlaces {
    /** The High Terrace's lift, from the city's plan (12 since M4 B2, SPEC decision 22; M1 had eight). */
    static final int TERRACE = CityPlan.get().terraceLift;
    /** The Association's halls (plan boxes): the infirmary west of the great door, the Registry east of it. */
    static final int INF_X0 = -21, INF_X1 = -8, HALL_Z0 = -150, HALL_Z1 = -137, ATRIUM_X0 = -7, ATRIUM_X1 = 6, REG_X0 = 7, REG_X1 = 20;
    /** Beds: heads against the north wall at these x (the first four), then against the south wall. */
    static final int[] BED_X = {-19, -16, -13, -10};
    /** The Assessment Lens: a shard of the Choir on a pedestal in the Registry. */
    static final int LENS_X = 14, LENS_Z = -146;
    /** The doorways between the halls (z rows, plan). */
    static final int DOOR_Z0 = -143, DOOR_Z1 = -142;

    final CityLayout layout;
    final int cx, cz, streetY;

    private OpeningPlaces(CityLayout l) {
        this.layout = l;
        this.cx = l.cx;
        this.cz = l.cz;
        this.streetY = l.streetY;
    }

    /** Tests: a city put somewhere in the test level (the real one stays as it is). */
    static @Nullable CityLayout testLayout;

    /** This world's places, or null outside a Carillon world. */
    static @Nullable OpeningPlaces get() {
        CityLayout l = testLayout != null ? testLayout : City.layout();
        return l == null ? null : new OpeningPlaces(l);
    }

    /** For tests: the places of a layout put anywhere. */
    static OpeningPlaces of(CityLayout l) {
        return new OpeningPlaces(l);
    }

    CityPlan plan() {
        return layout.plan;
    }

    /** Carillon's centre at street height, sent to the client so it can place the plan's points. */
    Vec3 origin() {
        return new Vec3(cx, streetY, cz);
    }

    BlockPos at(int px, int ry, int pz) {
        return new BlockPos(cx + px, streetY + ry, cz + pz);
    }

    Vec3 atV(double px, double ry, double pz) {
        return new Vec3(cx + px, streetY + ry, cz + pz);
    }

    /** Where the opening begins: the Roost's terrace (the world's spawn), and the view across the city. */
    BlockPos roost() {
        return City.start(layout);
    }

    float roostYaw() {
        return layout.plan.startYaw;
    }

    /** The middle of Choir Square, one above the paving. */
    BlockPos square() {
        return City.square(layout);
    }

    int squareRadius() {
        return layout.plan.squareR;
    }

    /** Inside the square (the arrival), from its paving up to a few storeys (the stall roofs, a drop from a balcony). */
    boolean inSquare(Vec3 p, double margin) {
        BlockPos s = square();
        double dx = p.x - (s.getX() + 0.5), dz = p.z - (s.getZ() + 0.5);
        double r = squareRadius() - margin;
        return dx * dx + dz * dz <= r * r && p.y >= s.getY() - 3 && p.y <= s.getY() + 14;
    }

    /** The great bell in the Choir's belfry (M1's spire: the bell hangs at ry 68 in the octagon's heart). */
    Vec3 belfry() {
        return atV(0.0, 68.5, -46.0);
    }

    /** The Bell Bridge's deck (plan box from the plan's bridges), its middle. */
    Vec3 bellBridge() {
        for (CityPlan.Bridge b : layout.plan.bridges) {
            if (b.id().equals("bell_bridge")) return atV((b.x0() + b.x1() + 1) / 2.0, 1, (b.z0() + b.z1() + 1) / 2.0);
        }
        return atV(-72, 1, -10.5);
    }

    CityPlan.@Nullable Bridge bellBridgeBox() {
        for (CityPlan.Bridge b : layout.plan.bridges) if (b.id().equals("bell_bridge")) return b;
        return null;
    }

    /** The infirmary bed's head block (index: 0..3 along the north wall, 4..7 along the south wall). */
    BlockPos bedHead(int i) {
        int k = Math.floorMod(i, 8);
        int x = BED_X[k % 4];
        return k < 4 ? at(x, TERRACE + 1, HALL_Z0 + 1) : at(x, TERRACE + 1, HALL_Z1 - 1);
    }

    /** The bed's foot block (the bed runs away from its wall). */
    BlockPos bedFoot(int i) {
        BlockPos h = bedHead(i);
        return Math.floorMod(i, 8) < 4 ? h.south() : h.north();
    }

    /** Where you stand when you've got up: beside the bed, toward the room's middle, facing the doorway. */
    Vec3 bedside(int i) {
        BlockPos f = bedFoot(i);
        boolean north = Math.floorMod(i, 8) < 4;
        return new Vec3(f.getX() + 1.5, f.getY(), f.getZ() + (north ? 0.9 : 0.1));
    }

    /** The Assessment Lens's crystal (it floats over its pedestal). */
    Vec3 lens() {
        return atV(LENS_X + 0.5, TERRACE + 3.4, LENS_Z + 0.5);
    }

    /** Where you stand to be read: a step in front of the Lens, facing it (north). */
    Vec3 lensMark() {
        return atV(LENS_X + 0.5, TERRACE + 1, LENS_Z + 3.5);
    }

    /** Inside the Registry hall (the walk there is over). */
    boolean inRegistry(Vec3 p) {
        double px = p.x - cx, pz = p.z - cz, ry = p.y - streetY;
        return px >= REG_X0 + 1 && px <= REG_X1 && pz >= HALL_Z0 + 1 && pz <= HALL_Z1 && ry >= TERRACE && ry <= TERRACE + 8;
    }

    /** The Association's great door, where the city opens up again ("The city is yours."). */
    Vec3 greatDoor() {
        return atV(0.5, TERRACE + 1, HALL_Z1 + 2.5);
    }

    /** The district at a spot ("bellwatch", "quay", "market", ...), or "". */
    String district(Vec3 p) {
        int d = layout.district((int) Math.floor(p.x), (int) Math.floor(p.z));
        return d <= 0 ? "" : CityPlan.DISTRICTS[d];
    }
}
