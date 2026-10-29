package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The Lull's edges (SPEC §43.1, JOURNEY part 1): where the bells still carry. Carillon's reach is a circle round the
 * world's centre (the city's, or spawn's in a classic world) through a point 60 blocks north of the Last Fire, the ridge:
 * 510 blocks in a Carillon world, 360 in a classic one. A holdfast's bell carries {@link #HOLDFAST_BELL} blocks. The
 * Ember sets only out in the quiet, past both. The living module installs the centre; the village module the holdfasts.
 */
public final class TheLull {
    private TheLull() {}

    /** The Last Fire's distance up the north road, and the ridge's, in a Carillon world and a classic one. */
    public static final int CAMP_CITY = 450, CAMP_CLASSIC = 300, PAST_CAMP = 60;
    public static final int RIDGE_CITY = CAMP_CITY + PAST_CAMP, RIDGE_CLASSIC = CAMP_CLASSIC + PAST_CAMP;
    /** How far a holdfast's bell carries (SPEC §43.2). */
    public static final int HOLDFAST_BELL = 768;

    private static Function<MinecraftServer, BlockPos> centre = s -> BlockPos.ZERO;
    private static Predicate<MinecraftServer> carillon = s -> false;
    private static BiPredicate<ServerLevel, BlockPos> holdfastHeard = (l, p) -> false;
    /** Tests only: a centre to measure from instead of the world's (set and cleared inside a try/finally). */
    public static @Nullable BlockPos testCentre;

    /** The living module: the world's centre (its region map's) and whether Carillon stands. */
    public static void install(Function<MinecraftServer, BlockPos> worldCentre, Predicate<MinecraftServer> hasCity) {
        centre = worldCentre;
        carillon = hasCity;
    }

    /** The village module: whether a holdfast's bell is heard at a place. */
    public static void installHoldfasts(BiPredicate<ServerLevel, BlockPos> heard) {
        holdfastHeard = heard;
    }

    public static BlockPos centre(MinecraftServer server) {
        return testCentre != null ? testCentre : centre.apply(server);
    }

    /** The ridge's radius in this world. */
    public static int ridge(MinecraftServer server) {
        return carillon.test(server) ? RIDGE_CITY : RIDGE_CLASSIC;
    }

    /** Whether the city's bells still carry here (inside the ridge; the overworld only). */
    public static boolean bellsCarry(ServerLevel level, BlockPos at) {
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        BlockPos c = centre(level.getServer());
        double dx = at.getX() - c.getX(), dz = at.getZ() - c.getZ();
        int r = ridge(level.getServer());
        return dx * dx + dz * dz < (double) r * r;
    }

    /** Whether a holdfast's bell is heard here. */
    public static boolean holdfastHeard(ServerLevel level, BlockPos at) {
        return holdfastHeard.test(level, at);
    }
}
