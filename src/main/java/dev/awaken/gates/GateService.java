package dev.awaken.gates;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateCells;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Gate lifecycle on the server: creating a gate (cell, plan, background build of the surface structure and the first
 * floor), state changes, release and cleanup of cells. All on the server thread.
 */
public final class GateService {
    private GateService() {}

    /** Radius (chunks) of the first floor around the entrance generated before the gate is READY. */
    static final int PREGEN_RADIUS = 3;
    static final int BUILD_TIMEOUT = 20 * 90;
    /** The same budget in real time: chunk workers run on their own threads, so a server ticking faster than 20 a
     *  second (the GameTest server never waits between ticks) must not time a build out before the workers could. */
    static final long BUILD_TIMEOUT_NANOS = 90L * 1_000_000_000L;

    private static final class Build {
        final int gateId;
        final long started;
        final long startedNanos = System.nanoTime();
        CompletableFuture<?> surface, interior;
        ChunkPos surfaceChunk, interiorChunk;
        ServerLevel interiorLevel;
        boolean surfaceDone, interiorDone;
        int attempts;

        Build(int gateId, long started) {
            this.gateId = gateId;
            this.started = started;
        }
    }

    private static final Map<Integer, Build> BUILDS = new HashMap<>();
    /** A next floor's start being generated: its level, chunk and when it was asked for. */
    private record Pregen(ServerLevel level, int x, int z, int at) {}

    private static final List<Pregen> PREGEN = new ArrayList<>();
    private static Path worldRoot;

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Tests only: a level that stands in for the gate dimension (the GameTest server has no datapack dimensions). */
    static ServerLevel testLevel;

    /** GameTests in other modules only: stand {@code level} in for the gate dimension, or null to stop. Reset it in a finally. */
    public static void useTestLevel(ServerLevel level) {
        testLevel = level;
    }

    public static ServerLevel gateLevel(MinecraftServer server) {
        return testLevel != null ? testLevel : server.getLevel(GatesRegistry.GATES);
    }

    /** The level a floor is built in: the open sky's for an open land (SPEC §38.11), else the gates'. */
    public static ServerLevel levelOf(MinecraftServer server, FloorLayout f) {
        if (testLevel != null) return testLevel;
        if (f != null && f.sky) {
            ServerLevel sky = server.getLevel(GatesRegistry.GATES_SKY);
            if (sky != null) return sky;
        }
        return server.getLevel(GatesRegistry.GATES);
    }

    public static ServerLevel levelOf(MinecraftServer server, GateRecord g, int area, int floor) {
        return levelOf(server, GateLayout.of(g.plan).floor(area, floor));
    }

    /** Every level gates are built in that this server has (the gates' and the open sky's; the stand-in in tests). */
    public static List<ServerLevel> levels(MinecraftServer server) {
        if (testLevel != null) return List.of(testLevel);
        ServerLevel gl = server.getLevel(GatesRegistry.GATES), sky = server.getLevel(GatesRegistry.GATES_SKY);
        if (gl == null) return sky == null ? List.of() : List.of(sky);
        return sky == null ? List.of(gl) : List.of(gl, sky);
    }

    /** Whether open lands can be built (the open sky's dimension is there; tests paint them in their stand-in). */
    public static boolean hasSky(MinecraftServer server) {
        return testLevel != null || server.getLevel(GatesRegistry.GATES_SKY) != null;
    }

    /** A level gates are built in. */
    public static boolean isGateLevel(net.minecraft.world.level.Level level) {
        return testLevel != null ? level == testLevel : GatesRegistry.isGateDim(level.dimension());
    }

    /** Inside the gate world: in the gate level and inside a cell's area. */
    public static boolean inGate(net.minecraft.world.entity.Entity e) {
        return isGateLevel(e.level()) && Cells.cellAt(e.getBlockX(), e.getBlockZ()) >= 0;
    }

    public static boolean inGate(net.minecraft.world.level.Level level, BlockPos pos) {
        return isGateLevel(level) && Cells.cellAt(pos.getX(), pos.getZ()) >= 0;
    }

    // ---- creation ---------------------------------------------------------------------------------------------------

    /** Creates a gate in BUILDING state and starts building it. */
    public static GateRecord create(MinecraftServer server, int id, GatePlan plan, BlockPos ground, int axis, Optional<UUID> owner,
                                    long breaksInDays, List<String> trace, boolean scripted) {
        GateRegistry reg = GateRegistry.get(server);
        int cell = reg.allocateCell();
        long day = PlayDays.current(server);
        // each floor's world and mood, picked now and saved with the plan (SPEC §38)
        plan = dev.awaken.gates.worlds.GateWorlds.dress(server, plan, owner);
        GateRecord g = new GateRecord(id, plan, ground, axis, cell, day, day + breaksInDays, GateState.BUILDING, owner);
        g.foldsOnPlayDay = day + Breaks.UNSEEN_DAYS; // until someone comes close (SPEC §7, "Seen first")
        g.trace.addAll(trace);
        g.scripted = scripted;
        reg.put(g);
        GateCells.put(cell, plan);
        startBuild(server, g);
        return g;
    }

    static void startBuild(MinecraftServer server, GateRecord g) {
        Build b = new Build(g.id, now(server));
        ServerLevel over = server.overworld();
        b.surfaceChunk = ChunkPos.containing(g.pos);
        if (!g.structurePlaced) b.surface = over.getChunkSource().addTicketAndLoadWithRadius(GatesRegistry.BUILD_TICKET.get(), b.surfaceChunk, 1);
        else b.surfaceDone = true;
        ServerLevel gl = levelOf(server, g, 0, 1);
        if (gl != null && !g.interiorReady) {
            BlockPos entry = entrance(g, 0, 1);
            b.interiorLevel = gl;
            b.interiorChunk = ChunkPos.containing(entry);
            b.interior = gl.getChunkSource().addTicketAndLoadWithRadius(GatesRegistry.BUILD_TICKET.get(), b.interiorChunk, PREGEN_RADIUS);
        } else b.interiorDone = true;
        BUILDS.put(g.id, b);
    }

    /** Called every server tick: finishes builds whose chunks are ready (cheap polling, no work on worker threads). */
    static void tickBuilds(MinecraftServer server) {
        if (BUILDS.isEmpty()) {
            tickPregen(server);
            return;
        }
        GateRegistry reg = GateRegistry.get(server);
        long t = now(server);
        Iterator<Build> it = BUILDS.values().iterator();
        int finished = 0;
        while (it.hasNext() && finished < 2) {
            Build b = it.next();
            GateRecord g = reg.gate(b.gateId);
            if (g == null || g.state != GateState.BUILDING) {
                releaseTickets(server, b);
                it.remove();
                continue;
            }
            if (!b.surfaceDone && b.surface != null && b.surface.isDone()) {
                b.surfaceDone = true;
                server.overworld().getChunkSource().removeTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), b.surfaceChunk, 1);
                String why = placeSurface(server, g);
                if (why != null) {
                    it.remove();
                    fail(server, g, why);
                    continue;
                }
                finished++;
            }
            if (!b.interiorDone && b.interior != null && b.interior.isDone()) {
                b.interiorDone = true;
                ServerLevel gl = b.interiorLevel;
                if (gl != null) gl.getChunkSource().removeTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), b.interiorChunk, PREGEN_RADIUS);
                g.interiorReady = true;
                reg.setDirty();
            }
            if (b.surfaceDone && b.interiorDone) {
                g.interiorReady = true;
                g.transition(GateState.READY);
                reg.setDirty();
                it.remove();
                Awaken.LOG.debug("[gates] gate {} ready ({} ticks)", g.id, t - b.started);
                GatePayloads.syncBeaconsAround(server, g.pos);
                dev.awaken.core.audio.AudioCues.playNear(server.overworld(), g.pos, 640, dev.awaken.core.audio.Cue.GATE_FORMING);
                if (g.announceTo.isPresent()) Director.announceFirst(server, g);
                continue;
            }
            if (t - b.started > BUILD_TIMEOUT && System.nanoTime() - b.startedNanos > BUILD_TIMEOUT_NANOS) {
                releaseTickets(server, b);
                it.remove();
                fail(server, g, "build timed out");
            }
        }
    }

    private static void releaseTickets(MinecraftServer server, Build b) {
        if (b.surfaceChunk != null && !b.surfaceDone) server.overworld().getChunkSource().removeTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), b.surfaceChunk, 1);
        ServerLevel gl = b.interiorLevel;
        if (gl != null && b.interiorChunk != null && !b.interiorDone) gl.getChunkSource().removeTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), b.interiorChunk, PREGEN_RADIUS);
    }

    /** Builds the surface; returns why it can't stand here (the build fails), or null when it stands. */
    private static String placeSurface(MinecraftServer server, GateRecord g) {
        ServerLevel over = server.overworld();
        int y = Overworld.groundY(over, g.pos.getX(), g.pos.getZ());
        BlockPos ground = new BlockPos(g.pos.getX(), y, g.pos.getZ());
        // never on someone's build; a scripted gate (the story's) or a trial (the Ascent's, SPEC §17.18: offered once, it
        // must form) moves to the nearest clear spot instead of failing
        int rank = g.plan.rank().ordinal();
        boolean mustForm = g.scripted || g.plan.skybornTrial();
        if (!mustForm && !Overworld.goodGround(over, ground)) return "wet or open ground at " + ground.toShortString();
        if (!mustForm && !g.anyGround && !Overworld.untouched(over, ground, g.axis, rank)) {
            return (dev.awaken.core.api.HearthHooks.nearest(over, ground, 64).isPresent() ? "a Hearth within 64 blocks of "
                    : "someone's build in the footprint at ") + ground.toShortString();
        }
        if (mustForm && !Overworld.untouched(over, ground, g.axis, rank)) {
            ground = Overworld.clearSpotNear(over, ground, g.axis).orElse(ground);
        }
        g.pos = ground; // the generator's height estimate can be a few blocks off: stand on the real ground
        g.structurePlaced = true;
        GateRegistry.get(server).setDirty();
        // a gate that can't stand is never left half-built: what did go up comes down with the failed build
        if (!Overworld.place(over, g, false)) return "its vortex had no room at " + ground.toShortString();
        return null;
    }

    /** A build failed: the cell is released and the Director rolls again later. You never see a broken gate. */
    private static void fail(MinecraftServer server, GateRecord g, String why) {
        GateRegistry reg = GateRegistry.get(server);
        g.trace.add("build failed: " + why);
        Awaken.LOG.info("[gates] gate {} failed to build: {}", g.id, why);
        g.transition(GateState.RELEASED);
        GateCells.remove(g.cell);
        reg.releaseCell(g.cell);
        reg.setDirty();
        removeStructureSoon(server, g); // the frame may already stand (the interior timed out)
        g.owner.ifPresent(o -> {
            ServerPlayer p = server.getPlayerList().getPlayer(o);
            if (p != null) dev.awaken.core.DirectorLog.log(p, "gate", "gate " + g.id + " failed to build (" + why + "), will roll again");
        });
    }

    /** Pre-generate the start of the next floor while a player fights on this one (one request at a time). */
    public static void pregenFloor(MinecraftServer server, GateRecord g, int area, int floor) {
        FloorLayout f = GateLayout.of(g.plan).floor(area, floor);
        if (f == null) return;
        ServerLevel gl = levelOf(server, f);
        if (gl == null) return;
        BlockPos e = entrance(g, area, floor);
        ChunkPos cp = ChunkPos.containing(e);
        if (gl.getChunkSource().hasChunk(cp.x(), cp.z())) return;
        gl.getChunkSource().addTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), cp, 2);
        PREGEN.add(new Pregen(gl, cp.x(), cp.z(), (int) (now(server) % Integer.MAX_VALUE)));
    }

    private static void tickPregen(MinecraftServer server) {
        if (PREGEN.isEmpty() || server.getTickCount() % 20 != 0) return;
        long t = now(server) % Integer.MAX_VALUE;
        PREGEN.removeIf(p -> {
            boolean loaded = p.level().getChunkSource().hasChunk(p.x(), p.z());
            if (loaded || t - p.at() > 1200 || t < p.at()) {
                p.level().getChunkSource().removeTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), new ChunkPos(p.x(), p.z()), 2);
                return true;
            }
            return false;
        });
    }

    // ---- geometry helpers -------------------------------------------------------------------------------------------

    /** World position of a floor-local point (x, z floor-local; y offset above the floor base). */
    public static BlockPos world(GateRecord g, FloorLayout f, int x, int yOff, int z) {
        return new BlockPos(Cells.originX(g.cell) + f.originSlotX * 16 + x, f.baseY + yOff, Cells.originZ(g.cell) + f.originSlotZ * 16 + z);
    }

    /** Where you stand in a room at floor-local (x, z): its floor, or on a world floor its ground there (SPEC §38.14). */
    public static BlockPos roomFloor(GateRecord g, FloorLayout f, Room r, int x, int z) {
        return world(g, f, x, r.dy + f.ground(x, z), z);
    }

    /** Where you arrive on a floor: standing on the entrance sigil of its start room. */
    public static BlockPos entrance(GateRecord g, int area, int floor) {
        FloorLayout f = GateLayout.of(g.plan).floor(area, floor);
        if (f == null) return new BlockPos(Cells.originX(g.cell) + 40, Cells.FLOOR1_Y, Cells.originZ(g.cell) + 40);
        Room s = f.room(f.start);
        // a world floor puts its sigil where its view is best; today's rooms have it at the room's centre
        Room.Feature e = s.feature(Room.FeatureKind.ENTRANCE);
        return e == null ? roomFloor(g, f, s, s.centerX(), s.centerZ()) : roomFloor(g, f, s, e.x(), e.z()).above(e.y());
    }

    /** Facing (degrees) toward the start room's first doorway. */
    public static float entranceYaw(GateRecord g, int area, int floor) {
        FloorLayout f = GateLayout.of(g.plan).floor(area, floor);
        if (f == null) return 0;
        if (!Float.isNaN(f.arrivalYaw)) return f.arrivalYaw;
        Room s = f.room(f.start);
        if (s.doors.isEmpty()) return 0;
        return switch (s.doors.get(0).side) {
            case 0 -> 180F;
            case 1 -> -90F;
            case 2 -> 0F;
            default -> 90F;
        };
    }

    public static Vec3 center(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    // ---- state ------------------------------------------------------------------------------------------------------

    public static void setState(MinecraftServer server, GateRecord g, GateState to) {
        if (g.transition(to)) {
            GateRegistry.get(server).setDirty();
            if (to == GateState.CLEARED || to == GateState.BROKEN || to == GateState.RELEASED) GatePayloads.syncBeaconsAround(server, g.pos);
        }
    }

    /** Cleared or broken-and-held gates leave the world: structure removed, cell released (files deleted on stop). */
    public static void release(MinecraftServer server, GateRecord g) {
        if (g.state == GateState.RELEASED) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // never release a cell with someone standing in it
            if (inGate(p) && Cells.cellAt(p.getBlockX(), p.getBlockZ()) == g.cell) return;
        }
        if (!g.transition(GateState.RELEASED)) {
            g.state = GateState.RELEASED;
        }
        GateRegistry reg = GateRegistry.get(server);
        GateCells.remove(g.cell);
        reg.releaseCell(g.cell);
        reg.setDirty();
        recycleWhileRunning(server, g.cell);
        removeStructureSoon(server, g);
        dev.awaken.gates.run.GateMobs.forgetGate(g.id);
        GatePayloads.syncBeaconsAround(server, g.pos);
    }

    /**
     * A dedicated server may run for weeks: a released cell's chunks are erased once they are out of memory (a few
     * seconds after everyone left, see {@link dev.awaken.core.RegionRecycler}) and the cell is free again. Never in
     * the GameTest stand-in level, and a restart still deletes whatever was left.
     */
    static void recycleWhileRunning(MinecraftServer server, int cell) {
        if (testLevel != null) return;
        List<ServerLevel> levels = levels(server);
        if (levels.isEmpty()) return;
        int cx = Cells.originX(cell) >> 4, cz = Cells.originZ(cell) >> 4;
        // the cell is free again once it's erased in both gate dimensions (the open sky's too)
        int[] left = {levels.size()};
        for (ServerLevel lv : levels) {
            dev.awaken.core.RegionRecycler.eraseWhenUnloaded(lv, dev.awaken.core.RegionRecycler.square(cx, cz, Cells.SIZE >> 4), () -> {
                if (--left[0] == 0) GateRegistry.get(server).cellRecycled(cell);
            });
        }
    }

    private static final List<Integer> REMOVALS = new ArrayList<>();

    private static void removeStructureSoon(MinecraftServer server, GateRecord g) {
        if (!g.structurePlaced) return;
        ServerLevel over = server.overworld();
        ChunkPos cp = ChunkPos.containing(g.pos);
        over.getChunkSource().addTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), cp, 1);
        REMOVALS.add(g.id);
    }

    static void tickRemovals(MinecraftServer server) {
        if (server.getTickCount() % 100 == 40) {
            long now = now(server);
            for (GateRecord g : List.copyOf(GateRegistry.get(server).all())) {
                // cleared gates whose last visitor left by dying (or a refused release) are swept up here
                if (g.state == GateState.CLEARED && g.inside.isEmpty()) release(server, g);
                // wandering gates fade when their time is up and nobody is inside
                else if (g.wandering() && g.state != GateState.RELEASED && g.inside.isEmpty() && now >= g.expiresAt) {
                    g.trace.add("faded");
                    release(server, g);
                }
            }
        }
        if (REMOVALS.isEmpty() || server.getTickCount() % 10 != 0) return;
        ServerLevel over = server.overworld();
        GateRegistry reg = GateRegistry.get(server);
        REMOVALS.removeIf(id -> {
            GateRecord g = reg.gate(id);
            if (g == null) return true;
            ChunkPos cp = ChunkPos.containing(g.pos);
            if (!over.getChunkSource().hasChunk(cp.x(), cp.z())) return false;
            Overworld.remove(over, g);
            g.structurePlaced = false;
            over.getChunkSource().removeTicketWithRadius(GatesRegistry.BUILD_TICKET.get(), cp, 1);
            reg.setDirty();
            return true;
        });
    }

    // ---- server lifecycle -------------------------------------------------------------------------------------------

    static void onStarted(MinecraftServer server) {
        worldRoot = server.getWorldPath(LevelResource.ROOT);
        GateCells.clear();
        BUILDS.clear();
        PREGEN.clear();
        REMOVALS.clear();
        GateRegistry reg = GateRegistry.get(server);
        if (reg.loadedSchema() < 4) Breaks.unseeAll(server, reg);
        for (Integer cell : List.copyOf(reg.releasedCells())) {
            boolean any = false;
            for (var key : List.of(GatesRegistry.GATES, GatesRegistry.GATES_SKY)) {
                Path dim = DimensionType.getStorageFolder(key, worldRoot);
                for (String sub : List.of("region", "entities", "poi")) any |= Files.exists(dim.resolve(sub).resolve(Cells.regionFile(cell)));
            }
            if (!any) reg.cellFreed(cell);
        }
        for (GateRecord g : reg.all()) {
            if (g.state == GateState.RELEASED) continue;
            if (g.state == GateState.READY && g.plan.contentVersion() < GatePlan.current() && g.party.isEmpty() && reg.hasFreeCell()) {
                // content changed and nobody entered: rebuild on the new version in a fresh cell
                reg.releaseCell(g.cell);
                g.cell = reg.allocateCell();
                g.plan = dev.awaken.gates.worlds.GateWorlds.dress(server, g.plan.rebuilt(GatePlan.current()), g.owner);
                g.interiorReady = false;
                g.state = GateState.BUILDING;
                g.trace.add("rebuilt for content version " + GatePlan.current());
            }
            GateCells.put(g.cell, g.plan);
            if (g.state == GateState.BUILDING) startBuild(server, g);
        }
        for (GateRecord g : reg.all()) {
            if ((g.state == GateState.CLEARED && g.inside.isEmpty())) release(server, g);
            // released while the server stopped before the frame came down (removals aren't saved)
            else if (g.state == GateState.RELEASED && g.structurePlaced) removeStructureSoon(server, g);
        }
        reg.prune();
    }

    /** Deletes released cells' region files. The level is closed by now, so nothing can resurrect them. */
    static void onStopped(MinecraftServer server) {
        if (worldRoot == null) return;
        GateRegistry reg;
        try {
            reg = GateRegistry.get(server);
        } catch (RuntimeException e) {
            return;
        }
        int deleted = 0;
        for (Integer cell : List.copyOf(reg.releasedCells())) {
            if (reg.byCell(cell) != null) continue; // a live gate owns it again (never delete a used cell)
            // both dimensions: an open land's floor was built under the sky, the rest in the gates
            for (var key : List.of(GatesRegistry.GATES, GatesRegistry.GATES_SKY)) {
                Path dim = DimensionType.getStorageFolder(key, worldRoot);
                for (String sub : List.of("region", "entities", "poi")) {
                    try {
                        if (Files.deleteIfExists(dim.resolve(sub).resolve(Cells.regionFile(cell)))) deleted++;
                    } catch (IOException e) {
                        Awaken.LOG.warn("[gates] could not delete {} of cell {}: {}", sub, cell, e.toString());
                    }
                }
            }
        }
        if (deleted > 0) Awaken.LOG.info("[gates] cleaned {} region files of released gate cells", deleted);
        GateCells.clear();
        BUILDS.clear();
    }

    public static int buildsInProgress() {
        return BUILDS.size();
    }
}
