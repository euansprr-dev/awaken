package dev.awaken.descent.world;

import dev.awaken.descent.Strata;
import net.minecraft.core.BlockPos;

/**
 * Fixed landmarks of every stratum, in local coordinates (relative to the stratum centre; the Endless offsets them by
 * the cell centre). The generator builds them; travel, waystones and rules read them. Pure data.
 */
public final class Layout {
    private Layout() {}

    public static final int MIN_Y = 0;
    public static final int HEIGHT = 256;
    /** Feet level on the arrival platform at the top of every stratum. */
    public static final int ENTRY_Y = 200;
    public static final int PLATFORM_R = 5;
    /** Helix start angle: the ramp leaves the arrival platform here (north, -z). */
    public static final double THETA0 = -Math.PI / 2;

    /** Feet level on the floor of the stratum (the camp, the pit and the guardian's lair). */
    public static int floorY(int form) {
        return switch (form) {
            case 7 -> 47;  // the central island above the lower sea
            case 10 -> 40; // the arena under the Seed
            default -> 20;
        };
    }

    /** Arrival platform centre (feet level). */
    public static BlockPos entry(int form) {
        return new BlockPos(0, ENTRY_Y, -Terrain.entryDistance(form));
    }

    /** The ascent stone at the back of the arrival platform. */
    public static BlockPos ascent(int form) {
        return new BlockPos(0, ENTRY_Y, -Terrain.entryDistance(form) - 4);
    }

    /** The Waystone of the bottom camp. */
    public static BlockPos waystone(int form) {
        return new BlockPos(8, floorY(form), 0);
    }

    /** Where fast travel and respawns put you: beside the Waystone. */
    public static BlockPos campArrival(int form) {
        return new BlockPos(7, floorY(form), 2);
    }

    /** Foot half of the camp's straw bed (head is one block east). */
    public static BlockPos bedFoot(int form) {
        return new BlockPos(10, floorY(form), 3);
    }

    public static BlockPos lantern(int form) {
        return new BlockPos(8, floorY(form), -2);
    }

    /** Centre of the 5x5 pit (the pit blocks replace the floor's top layer). */
    public static BlockPos pit(int form) {
        return new BlockPos(0, floorY(form) - 1, 0);
    }

    public static final int PIT_HALF = 2;

    public static BlockPos lair(int form) {
        return new BlockPos(0, floorY(form), -14);
    }

    /** The five Foundry conduits around the pit (form 6), in puzzle order around the ring. */
    public static BlockPos conduit(int form, int i) {
        double a = Math.PI / 2 + i * (Math.PI * 2 / 5);
        return new BlockPos((int) Math.round(Math.cos(a) * 6), floorY(form), (int) Math.round(Math.sin(a) * 6));
    }

    public static final int CONDUITS = 5;

    /** Where the finale boss stands in the Seed's arena. */
    public static BlockPos finale() {
        return new BlockPos(0, floorY(10), -16);
    }

    /** Local position to world position for a depth index. */
    public static BlockPos world(int depth, BlockPos local) {
        return local.offset(Strata.centerX(depth), 0, Strata.centerZ(depth));
    }

    public static BlockPos local(int depth, BlockPos world) {
        return world.offset(-Strata.centerX(depth), 0, -Strata.centerZ(depth));
    }
}
