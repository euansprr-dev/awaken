package dev.awaken.world.landmark;

import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.core.AwakenConfig;
import dev.awaken.descent.Descent;
import dev.awaken.progression.Progression;
import dev.awaken.world.anomaly.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Landmarks, the public side (docs/modules/landmarks.md): where they are around a player, which ones the player has
 * reached in person, the nearest one still unknown (for the Now tracker, the Scanner and a grove's spirit), and a rumour
 * line for the town. Positions of ruins come from {@link RuinLocator} (placement maths, no chunk loads; approximate
 * centres), the Well's from {@link Descent#wellPos}.
 */
public final class Landmarks {
    private Landmarks() {}

    /** Ruins further than this are not suggested or whispered about. */
    public static final int NEAR = 700;
    /** The Well is suggested from anywhere around spawn (it stands 400–700 blocks out). */
    public static final int WELL_RANGE = 1500;
    /** First time a player reaches each kind: this fraction of a level ({@code Progression.discover}). */
    public static final double DISCOVERY = 0.15;
    /** A spirit's whisper keeps its landmark up in the Now tracker this long. */
    public static final int WHISPER_LIFE = 10 * 60 * 20;
    static final List<RuinKind> RUINS = List.of(RuinKind.values());

    /** One landmark instance. {@code pos} is exact for the Well, approximate (start chunk centre, the caller's y) for ruins. */
    public record Landmark(String id, LandmarkKind kind, BlockPos pos, boolean spawnVault) {
        /** From this level there is a reason to go: the spawn vault's antechamber holds a Scanner, so from awakening. */
        public int reasonLevel() {
            return spawnVault ? 2 : kind.reasonLevel;
        }
    }

    /** A rumour line for the town: a stable id (so it isn't told twice), a title, and Mira's words. */
    public record Rumour(String id, String title, String body) {}

    /** The landmarks setting (on when the config isn't loaded yet, as in GameTests). */
    static boolean enabled() {
        try {
            return dev.awaken.core.AwakenConfig.LANDMARKS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** GameTests stand a Well in (try/finally, isolated batch). */
    static volatile @Nullable BlockPos testWell;
    /** GameTests that must not see real ruins from the test world's placement maths. */
    static volatile boolean testNoRuins;

    /** GameTests only (isolated batch, try/finally): stand a Well at {@code well} (null: the real one) and hide real ruins. */
    public static void testOverrides(@Nullable BlockPos well, boolean noRuins) {
        testWell = well;
        testNoRuins = noRuins;
    }

    public static LandmarkMemory memory(Player p) {
        return p.getData(LandmarkContent.MEMORY);
    }

    /** Sends this player's landmark memory to their client after a change (the Gate Map reads it). */
    static void sync(ServerPlayer p) {
        p.syncData(LandmarkContent.MEMORY);
    }

    static boolean descentOn() {
        try {
            return AwakenConfig.DESCENT.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static boolean aboveOn() {
        try {
            return AwakenConfig.ABOVE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** The Well's centre, once it is built (and the Descent is on). */
    public static Optional<BlockPos> wellPos(MinecraftServer server) {
        BlockPos t = testWell;
        if (t != null) return Optional.of(t);
        if (!descentOn()) return Optional.empty();
        return Descent.wellPos(server);
    }

    public static String ruinId(ChunkPos start) {
        return "ruin:" + start.x() + "," + start.z();
    }

    /** The id of the ruin whose start chunk holds {@code approxCentre} (what {@link RuinLocator} reports). */
    public static String ruinIdAt(BlockPos approxCentre) {
        return ruinId(ChunkPos.containing(approxCentre));
    }

    public static boolean reached(ServerPlayer p, String id) {
        return memory(p).reached(id);
    }

    /**
     * Landmarks around a player in the overworld, nearest first: the Well within {@link #WELL_RANGE}, ruins within
     * {@code radius}. {@code budget} caps the uncached terrain samples the ruin search may spend (results are cached).
     */
    public static List<Landmark> around(ServerPlayer p, int radius, int budget) {
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD) return List.of();
        BlockPos me = p.blockPosition();
        List<Landmark> out = new ArrayList<>();
        wellPos(level.getServer()).ifPresent(w -> {
            if (Spots.horizontal(me, w) <= WELL_RANGE) out.add(new Landmark("well", LandmarkKind.WELL, w, false));
        });
        if (!testNoRuins && aboveOn()) {
            for (RuinLocator.Found f : RuinLocator.near(level, me, radius, RUINS, null, false, 8, budget)) {
                out.add(new Landmark(ruinIdAt(f.pos()), LandmarkKind.of(f.kind()), f.pos(), f.kind() == RuinKind.SPAWN_VAULT));
            }
        }
        out.sort(Comparator.comparingDouble(l -> Spots.horizontal(me, l.pos())));
        return out;
    }

    /**
     * Pure: the nearest landmark not yet {@code known}; with {@code needReason}, only one a player of {@code level} has a
     * reason to visit. Ruins count within {@link #NEAR}, the Well within {@link #WELL_RANGE}.
     */
    static Optional<Landmark> pick(List<Landmark> all, BlockPos from, Predicate<String> known, int level, boolean needReason) {
        Landmark best = null;
        double bestD = Double.MAX_VALUE;
        for (Landmark l : all) {
            if (known.test(l.id()) || (needReason && level < l.reasonLevel())) continue;
            double d = Spots.horizontal(from, l.pos());
            if (d > (l.kind() == LandmarkKind.WELL ? WELL_RANGE : NEAR) || d >= bestD) continue;
            best = l;
            bestD = d;
        }
        return Optional.ofNullable(best);
    }

    /** The nearest landmark this player hasn't reached (and, with {@code needReason}, has a reason to visit now). */
    public static Optional<Landmark> nearestUnknown(ServerPlayer p, boolean needReason) {
        return pick(around(p, NEAR, 6), p.blockPosition(), id -> reached(p, id), Progression.profile(p).level(), needReason);
    }

    /**
     * What a shimmer grove's spirit says: the nearest unknown landmark the player has a reason to visit ("“Something
     * sealed, 420 m NW”"). It is remembered, so the Now tracker raises it for {@link #WHISPER_LIFE}. Empty when none.
     */
    public static Optional<String> whisper(ServerPlayer p) {
        Optional<Landmark> l = nearestUnknown(p, true);
        if (l.isEmpty()) return Optional.empty();
        LandmarkMemory m = memory(p);
        m.whispered = l.get().id();
        m.whisperedAt = p.level().getGameTime();
        if (m.place(l.get().id(), l.get().kind(), l.get().pos())) sync(p);
        LandmarkNow.forget(p.getUUID());
        return Optional.of(String.format(l.get().kind().whisper, Spots.where(p.blockPosition(), l.get().pos())));
    }

    /**
     * A rumour for the town (hearth: Mira, see the request in docs/modules/landmarks.md): the nearest landmark this player
     * hasn't reached, has a reason to visit and hasn't been told about, with a direction and a rounded distance. Marks it
     * told. Empty when there is nothing new to say.
     */
    public static Optional<Rumour> rumour(ServerPlayer p) {
        LandmarkMemory m = memory(p);
        BlockPos me = p.blockPosition();
        Optional<Landmark> l = pick(around(p, NEAR, 8), me, id -> m.reached(id) || m.told.contains(id), Progression.profile(p).level(), true);
        if (l.isEmpty()) return Optional.empty();
        Landmark k = l.get();
        m.told.add(k.id());
        if (m.place(k.id(), k.kind(), k.pos())) sync(p);
        String dir = compassWords(me, k.pos());
        int dist = (int) (Math.round(Spots.horizontal(me, k.pos()) / 50.0) * 50);
        return Optional.of(new Rumour("landmark/" + k.id(), k.kind().display + ", " + dir,
                "Mira: “" + String.format(k.kind().rumour, dir, Math.max(50, dist)) + "”"));
    }

    private static final String[] WORDS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};

    /** "to the southeast" (north is -Z). */
    static String compassWords(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        return "to the " + WORDS[Math.floorMod((int) Math.round(deg / 45.0), 8)];
    }
}
