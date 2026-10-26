package dev.awaken.world.landmark;

import dev.awaken.above.ruin.PrecursorRuinStructure;
import dev.awaken.above.scan.Scanning;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.GateHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Finds;
import dev.awaken.world.anomaly.Spots;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Notices when an awakened player reaches a landmark, cheaply: once a second per player (spread by UUID) it measures the
 * distance to the Well and looks at the structure references of the one chunk the player stands in (a map lookup; most
 * chunks hold none) for a Precursor ruin; every fifth check it asks the gates module for gates within
 * {@link #GATE_REACH}. No structure searches. The first of each kind pays a discovery
 * ({@code Progression.discover(player, "landmark/<kind>", 0.15)}; gates per theme, {@code "landmark/gate/<theme>"}).
 */
final class LandmarkWatch {
    private LandmarkWatch() {}

    static final int PERIOD = 20;
    static final int GATE_EVERY = 5;
    /** The Well counts as reached within this many blocks of its centre (its ring is 13 wide). */
    static final int WELL_REACH = 20;
    /** A ruin counts as reached within this many blocks of its bounds. */
    static final int RUIN_REACH = 10;
    static final int GATE_REACH = 20;

    static void tick(MinecraftServer s) {
        // GameTests call check() themselves: mock players from other tests must never collect landmarks by accident
        if (s instanceof GameTestServer) return;
        int t = s.getTickCount();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            int h = p.getUUID().hashCode();
            if (Math.floorMod(t + h, PERIOD) == 0) check(p, Math.floorMod(t / PERIOD + h, GATE_EVERY) == 0);
        }
    }

    /** One look around for this player. Returns how many landmarks were newly reached. */
    static int check(ServerPlayer p, boolean gates) {
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD || p.isSpectator() || !Progression.profile(p).awakened()) return 0;
        BlockPos me = p.blockPosition();
        int found = 0;
        var well = Landmarks.wellPos(level.getServer());
        if (well.isPresent() && Spots.horizontal(me, well.get()) <= WELL_REACH && Math.abs(me.getY() - well.get().getY()) <= 24) {
            if (reach(p, LandmarkKind.WELL, "well")) found++;
        }
        if (Landmarks.aboveOn()) found += ruins(p, level, me);
        if (gates) found += gates(p, level, me);
        return found;
    }

    private static int ruins(ServerPlayer p, ServerLevel level, BlockPos me) {
        if (!level.hasChunkAt(me)) return 0;
        LevelChunk chunk = level.getChunkAt(me);
        Map<Structure, LongSet> refs = chunk.getAllReferences();
        if (refs.isEmpty()) return 0;
        int found = 0;
        for (Map.Entry<Structure, LongSet> e : refs.entrySet()) {
            if (!(e.getKey() instanceof PrecursorRuinStructure ruin)) continue;
            for (long ref : e.getValue()) {
                ChunkPos start = ChunkPos.unpack(ref);
                String id = Landmarks.ruinId(start);
                if (Landmarks.reached(p, id) || !level.hasChunk(start.x(), start.z())) continue;
                StructureStart s = level.getChunk(start.x(), start.z()).getStartForStructure(ruin);
                if (s == null || !s.isValid()) continue;
                BoundingBox box = s.getBoundingBox().inflatedBy(RUIN_REACH);
                if (box.isInside(me) && reach(p, LandmarkKind.of(ruin.kind()), id, s.getBoundingBox().getCenter())) found++;
            }
        }
        return found;
    }

    private static int gates(ServerPlayer p, ServerLevel level, BlockPos me) {
        int found = 0;
        for (GateHooks.GateView g : GateHooks.near(level, me, GATE_REACH + 8)) {
            if ("RELEASED".equals(g.state()) || Spots.horizontal(me, g.mouth()) > GATE_REACH) continue;
            if (reachGate(p, g)) found++;
        }
        return found;
    }

    /**
     * The player reached landmark {@code id} of {@code kind}: remembered (no more Horizon line, Scanner omen or marker
     * whisper for it), and the first of its kind is a discovery with a toast. Returns false if it was already known.
     */
    static boolean reach(ServerPlayer p, LandmarkKind kind, String id) {
        BlockPos at = kind == LandmarkKind.WELL ? Landmarks.wellPos(p.level().getServer()).orElse(null) : null;
        return reach(p, kind, id, at);
    }

    /** As above, with where the landmark stands (for the Gate Map; null when unknown). */
    static boolean reach(ServerPlayer p, LandmarkKind kind, String id, @Nullable BlockPos at) {
        LandmarkMemory m = Landmarks.memory(p);
        if (!m.reach(id)) return false;
        if (at != null) m.place(id, kind, at);
        // a climb site reached without its key: the band keeps it, and the world calls you back with the rope (SPEC §27)
        String need = LandmarkHorizon.need(kind);
        if (!need.isEmpty() && !dev.awaken.core.api.HorizonHooks.can(p, need)) {
            dev.awaken.core.api.Promises.seen(p, "landmark:" + id, at, dev.awaken.core.api.Promises.Need.tier(dev.awaken.core.api.HorizonHooks.TETHER),
                    kind == LandmarkKind.SKY_PLATFORM ? "A sky platform" : "A Tether Spire's crown");
        }
        Landmarks.sync(p);
        if (m.whispered.equals(id)) m.whispered = "";
        LandmarkHorizon.forget(p.getUUID());
        Scanning.forget(p.getUUID());
        Progression.count(p, "awaken:landmarks_reached");
        boolean fresh = Progression.discover(p, kind.discoveryKey(), Landmarks.DISCOVERY);
        DirectorLog.log(p, "landmark", "reached " + kind.display + " (" + id + ")" + (fresh ? ": the first of its kind, a discovery" : ""));
        if (fresh) {
            Progression.markNewThing(p, kind.display);
            Finds.tell(p, Finds.Find.of("landmark", "New landmark · " + kind.display, kind.display, kind.found, 0x9FE6D6, true));
            Playtest.log(p, "landmark", "kind", kind.id);
        }
        return true;
    }

    /** A gate of a theme this player has never stood at: a discovery per theme. */
    static boolean reachGate(ServerPlayer p, GateHooks.GateView g) {
        String theme = g.theme();
        String path = theme.indexOf(':') >= 0 ? theme.substring(theme.indexOf(':') + 1) : theme;
        if (!Progression.discover(p, LandmarkKind.GATE.discoveryKey() + "/" + path, Landmarks.DISCOVERY)) return false;
        Progression.markNewThing(p, g.themeName() + " gate");
        Finds.tell(p, Finds.Find.of("gate", "New gate · " + g.themeName(), g.themeName() + " gate", Rank.of(g.rank()).label + "-rank", 0x7FC3FF, true));
        DirectorLog.log(p, "landmark", "first " + g.themeName() + " gate (" + path + "): a discovery");
        Playtest.log(p, "landmark", "kind", "gate/" + path);
        return true;
    }
}
