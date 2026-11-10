package dev.awaken.gates.realms.kit;

import dev.awaken.gates.world.FloorAir;

import java.util.ArrayList;
import java.util.List;

/** A land's air, written down as it is built: where the mist lies, where light comes down, what glows, where water stands. */
public final class AirPlan {
    private final List<FloorAir.Light> lights = new ArrayList<>();
    private final List<FloorAir.Shaft> shafts = new ArrayList<>();
    private final List<FloorAir.Mist> mists = new ArrayList<>();
    private final List<FloorAir.Water> waters = new ArrayList<>();

    public AirPlan light(int x, int y, int z, int rgb, float radius, float strength) {
        lights.add(new FloorAir.Light(x, y, z, rgb, radius, strength));
        return this;
    }

    public AirPlan shaft(int x0, int y0, int z0, int x1, int y1, int z1, float radius, int rgb, float strength) {
        shafts.add(new FloorAir.Shaft(x0, y0, z0, x1, y1, z1, radius, rgb, strength));
        return this;
    }

    public AirPlan mist(int y, int thickness, int rgb, float density) {
        mists.add(new FloorAir.Mist(y, thickness, rgb, density));
        return this;
    }

    public AirPlan water(int y, int x0, int z0, int x1, int z1) {
        waters.add(new FloorAir.Water(y, x0, z0, x1, z1));
        return this;
    }

    /** How many of each there are so far: Gate Air takes 96 lights, 24 shafts, 6 mist layers and 12 waters (SPEC §38.9). */
    public int lights() {
        return lights.size();
    }

    public int shafts() {
        return shafts.size();
    }

    public int mists() {
        return mists.size();
    }

    public int waters() {
        return waters.size();
    }

    public FloorAir build() {
        return new FloorAir(lights, shafts, mists, waters);
    }
}
