package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.data.Vision;
import dev.awaken.lucidium.journal.DayJournal;
import dev.awaken.lucidium.journal.Describe;
import dev.awaken.lucidium.journal.SnapshotFiles;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Visions: a dream shows a real place in your world with its coordinates hidden. During the day the game picks one:
 * an unvisited surface structure near you (found with the structure placement maths, no searching, then loaded in the
 * background and checked), or failing that an old remembered place you haven't been near for days. After the dream
 * it pins to the Codex as a rumour with only a direction; walk within 16 blocks of it awake to find it.
 */
public final class Visions {
    private Visions() {}

    public static final String VISION_FOUND_SAME_DAY = "awaken:latent/vision_found_same_day";
    private static final Deque<UUID> QUEUE = new ArrayDeque<>();
    private static long lastTimeOfDay = -1;

    // ---- preparing ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (!AwakenConfig.LUCIDIUM.get()) return;
        if (server.getTickCount() % 20 == 11) {
            long tod = Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
            long last = lastTimeOfDay;
            lastTimeOfDay = tod;
            boolean noon = last >= 0 && ((last < 6000 && tod >= 6000) || (last > tod && tod >= 6000));
            if (noon) for (ServerPlayer p : server.getPlayerList().getPlayers()) QUEUE.add(p.getUUID());
        }
        // one player per tick keeps the work tiny
        UUID next = QUEUE.poll();
        if (next != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(next);
            if (p != null) prepare(p, false);
        }
    }

    /** Picks tonight's vision if none is waiting. {@code force} ignores the once-a-day limit (admin). */
    public static void prepare(ServerPlayer sp, boolean force) {
        MinecraftServer server = sp.level().getServer();
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(server);
        if (!force && (d.visionPrepDay == day || d.pendingVision().isPresent())) return;
        int unfound = 0;
        for (Vision v : d.visions) if (v.shown() && !v.found()) unfound++;
        if (!force && unfound >= 3) return; // don't pile up rumours
        d.visionPrepDay = day;
        ServerLevel level = server.overworld();
        if (sp.level().dimension() == Level.OVERWORLD) {
            Optional<Candidate> c = structureCandidate(sp, level, d);
            if (c.isPresent()) {
                loadAndCapture(sp.getUUID(), level, c.get());
                return;
            }
        }
        oldPlace(sp, d, day);
    }

    record Candidate(ChunkPos chunk, Holder<Structure> structure, String id) {}

    /**
     * The nearest surface structure the player has never been near, whose placement and biome say it should be there.
     * Placement is pure maths; only the 16 nearest candidates get a (cheap, read-only) biome sample.
     */
    static Optional<Candidate> structureCandidate(ServerPlayer sp, ServerLevel level, Dreamer d) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState rs = level.getChunkSource().randomState();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        int px = sp.chunkPosition().x(), pz = sp.chunkPosition().z();
        record Option(double dist, ChunkPos chunk, StructureSet set) {}
        List<Option> options = new ArrayList<>();
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) continue;
            boolean surface = false;
            for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
                if (e.structure().value().step() == GenerationStep.Decoration.SURFACE_STRUCTURES) surface = true;
            }
            if (!surface) continue;
            for (int gx = -2; gx <= 2; gx++) {
                for (int gz = -2; gz <= 2; gz++) {
                    ChunkPos cand = spread.getPotentialStructureChunk(state.getLevelSeed(), px + gx * spread.spacing(), pz + gz * spread.spacing());
                    if (!spread.isStructureChunk(state, cand.x(), cand.z())) continue;
                    double dx = (cand.x() - px) * 16.0, dz = (cand.z() - pz) * 16.0;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist < 160 || dist > 1400 || visited(d, cand)) continue;
                    options.add(new Option(dist, cand, set.value()));
                }
            }
        }
        if (options.isEmpty()) return Optional.empty();
        options.sort(java.util.Comparator.comparingDouble(Option::dist));
        Climate.Sampler sampler = rs.createClimateSampler(SamplerContext.builder().build());
        BiomeResolver resolver = gen.getBiomeSource().createResolver(sampler);
        for (int i = 0; i < Math.min(16, options.size()); i++) {
            Option o = options.get(i);
            int bx = o.chunk().getMiddleBlockX(), bz = o.chunk().getMiddleBlockZ();
            int y = gen.getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
            Holder<Biome> biome = resolver.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(y), QuartPos.fromBlock(bz));
            for (StructureSet.StructureSelectionEntry e : o.set().structures()) {
                Structure s = e.structure().value();
                if (s.step() != GenerationStep.Decoration.SURFACE_STRUCTURES || !s.biomes().contains(biome)) continue;
                var key = structures.getKey(s);
                if (key != null) return Optional.of(new Candidate(o.chunk(), e.structure(), key.toString()));
            }
        }
        return Optional.empty();
    }

    /** Has the player been near this chunk (a remembered place or an earlier vision within 96 blocks)? */
    private static boolean visited(Dreamer d, ChunkPos c) {
        BlockPos mid = new BlockPos(c.getMiddleBlockX(), 64, c.getMiddleBlockZ());
        for (Snapshot s : d.snapshots) {
            if (horizontalSq(s.pos(), mid) < 96 * 96) return true;
        }
        for (Vision v : d.visions) {
            if (horizontalSq(v.pos(), mid) < 96 * 96) return true;
        }
        return false;
    }

    private static long horizontalSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Loads the candidate chunk in the background, confirms the structure is really there, then remembers it. */
    private static void loadAndCapture(UUID player, ServerLevel level, Candidate c) {
        MinecraftServer server = level.getServer();
        level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, c.chunk(), 1).thenRunAsync(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(player);
            if (sp == null) return;
            ChunkAccess chunk = level.getChunk(c.chunk().x(), c.chunk().z());
            StructureStart start = chunk.getStartForStructure(c.structure().value());
            if (start == null || !start.isValid()) {
                oldPlace(sp, Dreams.dreamer(sp), PlayDays.current(server));
                return;
            }
            var box = start.getBoundingBox();
            int x = (box.minX() + box.maxX()) / 2, z = (box.minZ() + box.maxZ()) / 2;
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (DayJournal.othersBase(sp, level, feet)) return;
            if (!level.isLoaded(feet.offset(-8, 0, -8)) || !level.isLoaded(feet.offset(7, 0, 7))) {
                level.getChunk((x - 8) >> 4, (z - 8) >> 4);
                level.getChunk((x + 7) >> 4, (z + 7) >> 4);
            }
            CompoundTag tag = SnapshotFiles.capture(level, feet);
            Fragment f = Fragment.fromTemplate(BuiltInRegistries.BLOCK, tag);
            Dreamer d = Dreams.dreamer(sp);
            long day = PlayDays.current(server);
            String id = d.newId(day) + "_v";
            SnapshotFiles.write(server, player, id, tag);
            String label = Describe.place(f, c.id());
            d.putVision(new Vision(id, id, label, level.dimension().identifier().toString(), feet, "", day, -1, false));
            DirectorLog.log(sp, "vision", "prepared: " + label + " (" + c.id() + ")");
        }, server);
    }

    /** Fallback: an old remembered place, at least three play-days old and far from where you are now. */
    static void oldPlace(ServerPlayer sp, Dreamer d, long day) {
        Snapshot best = null;
        for (Snapshot s : d.snapshots) {
            if (s.kind() == Snapshot.Kind.VISION || day - s.day() < 3) continue;
            if (!s.dim().equals(sp.level().dimension().identifier().toString())) continue;
            if (s.pos().distSqr(sp.blockPosition()) < 150 * 150) continue;
            boolean used = false;
            for (Vision v : d.visions) if (v.snapshotId().equals(s.id())) used = true;
            if (used) continue;
            if (best == null || s.day() < best.day()) best = s;
        }
        if (best == null) return;
        String label = best.detail().isEmpty() ? best.label() : best.detail();
        d.putVision(new Vision(best.id() + "_old", best.id(), label, best.dim(), best.pos(), "", day, -1, false));
        DirectorLog.log(sp, "vision", "prepared from memory: " + label);
    }

    // ---- after the dream ----------------------------------------------------------------------------------------------

    /** The dream showed it: pin it to the Codex with a direction from your bed. */
    static void reveal(ServerPlayer sp, Vision v, long day) {
        if (v.shown()) return;
        Dreamer d = Dreams.dreamer(sp);
        BlockPos from = sp.blockPosition();
        var cfg = sp.getRespawnConfig();
        if (cfg != null) from = cfg.respawnData().pos();
        d.putVision(v.withShown(day, hint(from, v.pos())));
        sync(sp);
    }

    public static String hint(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        String[] dirs = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        int idx = (int) Math.floorMod(Math.round(ang / 45.0), 8);
        String far = dist < 300 ? "not far" : dist < 800 ? "a long walk" : dist < 2000 ? "far" : "very far";
        return "It lies " + far + " to the " + dirs[idx] + " of your bed.";
    }

    /** Awake and within 16 blocks of a vision's place: found. */
    static void checkFound(ServerPlayer sp) {
        if ((sp.tickCount + 7) % 40 != 0 || Dreams.isDreaming(sp)) return;
        Dreamer d = Dreams.dreamer(sp);
        String dim = sp.level().dimension().identifier().toString();
        for (Vision v : List.copyOf(d.visions)) {
            if (!v.shown() || v.found() || !v.dim().equals(dim)) continue;
            if (v.pos().distSqr(sp.blockPosition()) > 16 * 16) continue;
            d.putVision(v.withFound());
            long day = PlayDays.current(sp.level().getServer());
            Progression.count(sp, Counters.SECRETS_FOUND);
            if (day == v.shownDay()) Progression.count(sp, VISION_FOUND_SAME_DAY, 1);
            Progression.markNewThing(sp, "vision found");
            Progression.bestMoment(sp, "You found the place from your dream");
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("You've been here before"))
                    .body(Component.literal(v.label() + ". You saw it in a dream, and it's real."))
                    .ttl(20 * 8).priority(5).build());
            sync(sp);
        }
    }

    /** Sends the Codex lines for this player: revealed visions and the dream signs they have noticed. */
    public static void sync(ServerPlayer sp) {
        Dreamer d = Dreams.dreamer(sp);
        List<LucidiumNet.CodexLine> lines = new ArrayList<>();
        for (Vision v : d.visions) {
            if (!v.shown()) continue;
            lines.add(new LucidiumNet.CodexLine("awaken:vision/" + v.id(), "Visions", v.label(),
                    v.found() ? "You saw it in a dream on day " + v.shownDay() + ", and then you found it." : "A dream on day " + v.shownDay() + ". " + v.hint(),
                    v.found()));
        }
        for (String s : d.signsSeen) {
            DreamSign.byName(s).ifPresent(sign -> lines.add(new LucidiumNet.CodexLine("awaken:dream_sign/" + sign.name().toLowerCase(),
                    "Dream signs", sign.title, sign.description + " If you see it, you are dreaming.", true)));
        }
        AwakenNet.send(sp, new LucidiumNet.CodexSync(lines));
    }

    static void reset() {
        QUEUE.clear();
        lastTimeOfDay = -1;
    }
}
