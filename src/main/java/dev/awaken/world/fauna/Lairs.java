package dev.awaken.world.fauna;

import com.mojang.datafixers.util.Pair;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Forces;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Notices;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.world.fauna.lair.LairPiece;
import dev.awaken.world.fauna.lair.LairStructure;
import dev.awaken.world.fauna.lair.LairStyle;
import dev.awaken.world.fauna.lair.LairWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The named beasts' lairs at run time (docs/modules/fauna.md, "Lairs"). Lairs are learnt as their chunks load; every
 * two seconds each overworld player near a known lair may wake it (its beast appears at the den, under the same rules
 * as any named beast: the fauna game rule, Peaceful, awakened and level 3+, never inside a Hearth radius, one beast near
 * a player) or find it (discovery XP, a toast). A player hears of a lair the first time they meet a creature of its
 * family from level 3, or meet its beast: one structure lookup (a few candidate chunks) per rumour, at most one
 * rumour a tick. The Now tracker then points at it.
 */
public final class Lairs {
    private Lairs() {}

    /** Lair work runs this often (ticks). */
    static final int PERIOD = 40;
    /** A loaded lair this close to a player gets its beast. */
    public static final int WAKE = 96;
    /** Standing this close to the den counts as finding the lair. */
    public static final int FOUND = 20;
    /** After its beast falls a lair stays quiet this long (game ticks: an hour of play, three days). */
    public static final long RESPAWN = 20L * 60 * 60;
    /** Rumours reach this far (blocks). */
    public static final int HEAR_RANGE = 3000;
    /** Finding a lair pays this share of a level once. */
    static final double FOUND_XP = 0.15;
    /** A rumour that found nothing is not retried for this long (ticks). */
    static final long MISS_COOLDOWN = 20L * 60 * 10;

    record Pending(long key, String beast, BlockPos den) {}

    record Rumour(UUID player, String beast) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final ArrayDeque<Rumour> RUMOURS = new ArrayDeque<>();
    private static final Map<String, Long> MISSES = new HashMap<>();

    /** Finds the nearest lair of a beast (the structure lookup; tests swap it, isolated, in try/finally). */
    interface Locator {
        @Nullable BlockPos locate(ServerLevel level, String beast, BlockPos near);
    }

    static Locator locator = Lairs::locate;

    // ---- learning lairs as their chunks load ------------------------------------------------------------------------

    /** {@code ChunkEvent.Load} (main thread, before the chunk is FULL): queue the lairs this chunk starts. */
    static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Map<Structure, StructureStart> starts = e.getChunk().getAllStarts();
        if (starts.isEmpty()) return;
        for (Map.Entry<Structure, StructureStart> s : starts.entrySet()) {
            if (!(s.getKey() instanceof LairStructure) || !s.getValue().isValid()) continue;
            for (StructurePiece p : s.getValue().getPieces()) {
                if (p instanceof LairPiece lp) PENDING.add(new Pending(s.getValue().getChunkPos().pack(), lp.style().beast, lp.den()));
            }
        }
    }

    static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            LairRegistry reg = LairRegistry.get(server);
            for (Pending p : PENDING) reg.add(p.key(), p.beast(), p.den());
            PENDING.clear();
        }
        if (!RUMOURS.isEmpty()) processRumour(server, RUMOURS.poll());
        if (server.getTickCount() % PERIOD != 0) return;
        ServerLevel level = server.overworld();
        LairRegistry reg = LairRegistry.get(server);
        if (reg.isEmpty() || level.players().isEmpty()) return;
        for (ServerPlayer p : level.players()) visit(level, reg, p);
    }

    static void visit(ServerLevel level, LairRegistry reg, ServerPlayer p) {
        if (p.isSpectator()) return;
        for (LairRegistry.Entry e : List.copyOf(reg.all())) {
            double dx = e.den().getX() + 0.5 - p.getX(), dz = e.den().getZ() + 0.5 - p.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 > WAKE * WAKE) continue;
            // (a cave lair is found down in it, not from the surface above it)
            if (d2 < FOUND * FOUND && Math.abs(e.den().getY() - p.getY()) < 16 && Progression.profile(p).awakened()) found(p, e);
            wake(level, reg, e, p, true);
        }
    }

    // ---- the beast at home --------------------------------------------------------------------------------------------

    /** Whether this lair's beast is alive (loaded, or remembered by the beast registry while its chunk sleeps). */
    static boolean home(ServerLevel level, LairRegistry.Entry e) {
        if (e.resident().isEmpty()) return false;
        UUID id = e.resident().get();
        return level.getEntity(id) != null || BeastRegistry.get(level.getServer()).has(id);
    }

    /**
     * Why this lair's beast would not appear for {@code p} now, or null when it does (with {@code commit}, it then
     * appears). Reasons are constants.
     */
    static @Nullable String wake(ServerLevel level, LairRegistry reg, LairRegistry.Entry e, ServerPlayer p, boolean commit) {
        if (!FaunaModule.spawningOn(level)) return "game rule awaken:fauna_spawning is off";
        if (level.getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
        if (dev.awaken.village.api.Troubles.lairSealed(level, e.den())) return "a village's Taken cleared it for good"; // SPEC §28.7
        if (home(level, e)) return "its beast is home";
        if (e.felledAt() > 0 && level.getGameTime() - e.felledAt() < RESPAWN) return "its beast fell; the lair is quiet";
        if (!level.isPositionEntityTicking(e.den())) return "the den isn't loaded";
        MobDef d = Bestiary.get(e.beast());
        if (d == null || d.fauna != FaunaRole.BEAST) return "not a named beast";
        Profile prof = Progression.profile(p);
        if (!prof.awakened()) return "the player hasn't awakened";
        if (prof.level() < Habitat.of(d.id).minPlayerLevel()) return "player level too low";
        if (FaunaRules.hearth.near(level, e.den())) return "inside a Hearth radius";
        if (FaunaCensus.countNear(level, FaunaRole.BEAST, p.getX(), p.getZ(), 128.0) >= FaunaRole.BEAST.capPerPlayer) return "another beast is near";
        if (!commit) return null;
        int lv = FaunaRules.contentLevel(level, e.den(), FaunaRole.BEAST);
        AwakenMob m = Mobs.spawn(level, d, standing(level, d, e.den()), lv, new MobRole(d.tier.role, lv, d.theme.id, Optional.empty(), ""),
                EnumSet.noneOf(Forces.Tag.class));
        if (m == null) return "nothing spawned";
        m.setHomeTo(e.den(), d.arena);
        reg.resident(e.key(), m.getUUID());
        DirectorLog.log(p, "fauna", d.id + " woke in its lair at " + e.den().toShortString() + " lv" + lv);
        return null;
    }

    /** A spot at the den where the beast fits (carpets and petals on the floor are fine to stand in). */
    static Vec3 standing(ServerLevel level, MobDef d, BlockPos den) {
        EntityType<?> type = MobTypes.type(d);
        double[] lifts = {0.0, 0.07, 1.0, 2.0};
        for (double lift : lifts) {
            Vec3 at = Vec3.atBottomCenterOf(den).add(0, lift, 0);
            AABB box = type == null ? new AABB(den) : type.getSpawnAABB(at.x, at.y, at.z).deflate(1.0E-4);
            if (level.noCollision(box)) return at;
        }
        return Vec3.atBottomCenterOf(den).add(0, 1.0, 0);
    }

    /** Its beast is gone (killed, or discarded by a command): the lair is quiet for a while. */
    static void beastGone(ServerLevel level, UUID beast) {
        LairRegistry.get(level.getServer()).felled(beast, level.getGameTime());
    }

    static boolean isLairBeast(MinecraftServer server, UUID beast) {
        return LairRegistry.get(server).ofBeast(beast) != null;
    }

    // ---- finding and hearing of lairs ---------------------------------------------------------------------------------

    /** The player stands in a lair: remembered as found, paid once. */
    /** How many kinds of beast this player has learned the lair of (Titles and directives read it). */
    public static final String LAIRS_HEARD = "awaken:lairs_heard";

    static boolean found(ServerPlayer p, LairRegistry.Entry e) {
        FaunaState s = p.getData(FaunaModule.STATE.get());
        FaunaState.KnownLair k = s.lair(e.key());
        if (k != null && k.found()) return false;
        know(p, e.key(), e.beast(), e.den(), true);
        if (Progression.discover(p, "lair/" + e.beast(), FOUND_XP)) tellFound(p, e);
        Playtest.log(p, "lair", "found", e.beast());
        DirectorLog.log(p, "fauna", "found the lair of " + e.beast() + " at " + e.den().toShortString());
        return true;
    }

    /**
     * Seen through the binoculars (SPEC §39.10): the lair's discovery as if found, and the den goes on the tracker; it
     * is found (the band stops pointing at it) when the player stands in it. Returns whether it was a new find.
     */
    static boolean spotted(ServerPlayer p, LairRegistry.Entry e) {
        if (!Progression.discover(p, "lair/" + e.beast(), FOUND_XP)) return false;
        FaunaState.KnownLair k = p.getData(FaunaModule.STATE.get()).lair(e.key());
        if (k == null) know(p, e.key(), e.beast(), e.den(), false);
        tellFound(p, e);
        Playtest.log(p, "lair", "spotted", e.beast());
        DirectorLog.log(p, "fauna", "spotted the lair of " + e.beast() + " at " + e.den().toShortString() + " through the binoculars");
        return true;
    }

    private static void tellFound(ServerPlayer p, LairRegistry.Entry e) {
        MobDef d = Bestiary.get(e.beast());
        LairStyle style = LairStyle.byBeast(e.beast());
        String place = style != null ? capital(style.title) : "A lair";
        Finds.tell(p, Finds.Find.of("lair", "Lair found · " + place, place, d != null ? possessive(d.name) + " den" : "A beast's den",
                d != null ? d.glow : 0xFFFFFF, true));
    }

    /** Remembers a lair for this player (the tracker reads it) and marks the beast's lair as heard of (the Codex). */
    static void know(ServerPlayer p, long key, String beast, BlockPos pos, boolean found) {
        p.setData(FaunaModule.STATE.get(), p.getData(FaunaModule.STATE.get()).withLair(new FaunaState.KnownLair(beast, key, pos, found)));
        if (Progression.profile(p).counter("awaken:lair_heard/" + beast) == 0) Progression.count(p, LAIRS_HEARD); // beast kinds heard of
        Progression.setCounter(p, "awaken:lair_heard/" + beast, 1);
    }

    /** Queues a rumour: the player hears where this beast's nearest lair is (processed one a tick). */
    public static void hear(ServerPlayer p, MobDef beast) {
        if (beast.fauna != FaunaRole.BEAST || LairStyle.byBeast(beast.id) == null) return;
        Rumour r = new Rumour(p.getUUID(), beast.id);
        if (!RUMOURS.contains(r)) RUMOURS.add(r);
    }

    static void processRumour(MinecraftServer server, @Nullable Rumour r) {
        if (r == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(r.player());
        if (p == null || p.level().dimension() != Level.OVERWORLD) return;
        heardNow(p, r.beast());
    }

    /** The rumour itself (tests call it directly). Returns the lair's position, or null when there is none near. */
    static @Nullable BlockPos heardNow(ServerPlayer p, String beast) {
        ServerLevel level = p.level();
        BlockPos at = p.blockPosition();
        FaunaState s = p.getData(FaunaModule.STATE.get());
        for (FaunaState.KnownLair k : s.lairs()) {
            if (k.beast().equals(beast) && horizontal(k.pos(), at) < HEAR_RANGE) return null; // already knows one
        }
        String miss = p.getUUID() + "/" + beast;
        Long missed = MISSES.get(miss);
        if (missed != null && level.getGameTime() - missed < MISS_COOLDOWN) return null;
        LairRegistry reg = LairRegistry.get(level.getServer());
        LairRegistry.Entry known = reg.nearest(beast, at, HEAR_RANGE);
        long key;
        BlockPos pos;
        if (known != null) {
            key = known.key();
            pos = known.den();
        } else {
            BlockPos found = locator.locate(level, beast, at);
            if (found == null || horizontal(found, at) > HEAR_RANGE) {
                MISSES.put(miss, level.getGameTime());
                return null;
            }
            key = ChunkPos.containing(found).pack();
            pos = new BlockPos(found.getX() + 8, at.getY(), found.getZ() + 8);
        }
        know(p, key, beast, pos, false);
        MobDef d = Bestiary.get(beast);
        LairStyle style = LairStyle.byBeast(beast);
        double dx = pos.getX() - at.getX(), dz = pos.getZ() - at.getZ();
        String where = distance(Math.sqrt(dx * dx + dz * dz)) + " " + FaunaNow.compass(dx, dz);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Heard of · " + (style != null ? capital(style.title) : "a lair"))
                        .withColor(d != null ? d.glow : 0xFFFFFF))
                .body(Component.literal((d != null ? possessive(d.name) + " lair" : "A lair") + " · " + where))
                .presentation(SignalWindow.Presentation.TOAST).tier(Notices.Tier.AMBIENT).topic("lair").ttl(20 * 6).build());
        DirectorLog.log(p, "fauna", "heard of the lair of " + beast + " near " + pos.toShortString());
        return pos;
    }

    /** The structure lookup: a few candidate chunks of the beast's lair set around the player. */
    static @Nullable BlockPos locate(ServerLevel level, String beast, BlockPos near) {
        Optional<Holder.Reference<Structure>> h = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(LairWorldgen.structure(beast));
        if (h.isEmpty()) return null;
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(h.get()), near, 1, false);
        return found == null ? null : found.getFirst();
    }

    /** The named beast of a biome family (null for a family without one). */
    static @Nullable MobDef beastOf(Theme family) {
        for (MobDef d : Bestiary.fauna()) if (d.theme == family && d.fauna == FaunaRole.BEAST) return d;
        return null;
    }

    /** A first sighting: from level 3 the creature's family tells of its beast's lair; a beast itself, of its own. */
    static void onSighting(ServerPlayer p, MobDef seen) {
        if (Progression.profile(p).level() < 3) return;
        MobDef beast = seen.fauna == FaunaRole.BEAST ? seen : beastOf(seen.theme);
        if (beast != null) hear(p, beast);
    }

    /** Reaching level 3: rumours of the lairs of every family met so far. */
    static void onLevel(ServerPlayer p, int level) {
        if (level != 3) return;
        java.util.Set<Theme> met = EnumSet.noneOf(Theme.class);
        for (MobDef d : Bestiary.fauna()) if (Sightings.hasSeen(p, d.id)) met.add(d.theme);
        for (Theme t : met) {
            MobDef beast = beastOf(t);
            if (beast != null) hear(p, beast);
        }
    }

    // ---- building one by hand (commands, tests) -----------------------------------------------------------------------

    /** Builds a lair with its floor centre at {@code centre} and registers it; returns its den. */
    static BlockPos placeAt(ServerLevel level, LairStyle style, BlockPos centre, long seed) {
        // W5: a cave lair laid by hand opens into a pocket of cave as worldgen's opens into the natural caves
        int[] joint = null;
        if (style.placement == LairStyle.Placement.CAVE) {
            joint = new int[]{centre.getX() + LairStyle.EXTENT + 12, centre.getY() + 2, centre.getZ() + 5};
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 3; dy++) {
                if (dx * dx / 16.0 + dz * dz / 9.0 + (dy - 1.5) * (dy - 1.5) / 4.0 > 1.1) continue;
                level.setBlock(m.set(joint[0] + dx, joint[1] + dy, joint[2] + dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
        LairPiece piece = new LairPiece(style, centre, seed, style.variant(level.getBiome(centre)), joint);
        piece.build(level, piece.getBoundingBox());
        LairRegistry.get(level.getServer()).add(ChunkPos.containing(centre).pack(), style.beast, piece.den());
        return piece.den();
    }

    // ---- words ---------------------------------------------------------------------------------------------------------

    static String possessive(String name) {
        return name.endsWith("s") ? name + "'" : name + "'s";
    }

    static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String distance(double blocks) {
        if (blocks < 1000) return Math.round(blocks / 10.0) * 10 + " m";
        return String.format(java.util.Locale.ROOT, "%.1f km", blocks / 1000.0);
    }

    static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void clear() {
        PENDING.clear();
        RUMOURS.clear();
        MISSES.clear();
    }

    static int pendingRumours() {
        return RUMOURS.size();
    }
}
