package dev.awaken.veil;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.entity.VeilSpirit;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The spirits of the dead: where they stand (deterministic, see {@link SpiritPlacer}), the ghost entities that show them
 * to spirits, the conversations, and the Codex cards they leave behind.
 */
public final class Spirits {
    private Spirits() {}

    public static final String NAMED_LATENT = "awaken:latent/spirit_named";
    /** How far a spirit's ghost is kept around a projecting player. */
    static final int VISIBLE = 80;
    /** Never more than this many spirits around one player at once. */
    static final int MAX_NEAR = 3;
    static final int TALK_RANGE = 7;
    /** Sample heights: the surface, then two cave depths for the underground biomes. */
    private static final int[] DEPTHS = {0, -40};

    /** hint biome id -> spirits that live there (stable order). */
    private static final Map<String, List<String>> BY_BIOME = new HashMap<>();

    /** A place a spirit stands. key is unique per placement. */
    public record Spot(String spiritId, String key, Vec3 pos, String biome) {}

    static void rebuildIndex() {
        BY_BIOME.clear();
        for (var e : SignalLines.SPIRITS.entrySet()) BY_BIOME.computeIfAbsent(e.getValue().biomeHint(), k -> new ArrayList<>()).add(e.getKey());
    }

    /** Spirits that would stand in this biome: exact match first, otherwise the longest hint the biome's name contains. */
    public static List<String> forBiome(String biomeId) {
        List<String> exact = BY_BIOME.get(biomeId);
        if (exact != null) return exact;
        String path = biomeId.substring(biomeId.indexOf(':') + 1);
        String best = null;
        for (String hint : BY_BIOME.keySet()) {
            String hp = hint.substring(hint.indexOf(':') + 1);
            if (path.contains(hp) && (best == null || hp.length() > best.length())) best = hint;
        }
        return best == null ? List.of() : BY_BIOME.get(best);
    }

    // ---- placement --------------------------------------------------------------------------------------------------

    /** Spirit spots in the cells around {@code center} (radius in cells). Only spots whose chunk is loaded get a height. */
    public static List<Spot> spotsAround(ServerLevel level, Vec3 center, int cellRadius) {
        List<Spot> out = new ArrayList<>();
        if (level.dimension() != Level.OVERWORLD || SignalLines.SPIRITS.isEmpty()) return out;
        long seed = level.getSeed();
        int ccx = SpiritPlacer.cellOf(center.x), ccz = SpiritPlacer.cellOf(center.z);
        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                int cx = ccx + dx, cz = ccz + dz;
                if (!SpiritPlacer.haunted(seed, cx, cz)) continue;
                int[] xz = SpiritPlacer.spot(seed, cx, cz);
                if (!level.hasChunk(xz[0] >> 4, xz[1] >> 4)) continue;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz[0], xz[1]);
                // surface first: one spirit per cell
                boolean placed = false;
                String biome = biomeAt(level, new BlockPos(xz[0], surface, xz[1]));
                String id = SpiritPlacer.choose(seed, cx, cz, forBiome(biome));
                if (id != null && !isUnderground(id)) {
                    out.add(new Spot(id, id + "@" + cx + "," + cz, new Vec3(xz[0] + 0.5, surface + 0.1, xz[1] + 0.5), biome));
                    placed = true;
                }
                if (placed) continue;
                for (int y : DEPTHS) {
                    if (y >= surface - 8 || y < level.getMinY() + 4) continue;
                    String deep = biomeAt(level, new BlockPos(xz[0], y, xz[1]));
                    String did = SpiritPlacer.choose(seed, cx, cz, forBiome(deep));
                    if (did != null && isUnderground(did)) {
                        out.add(new Spot(did, did + "@" + cx + "," + cz + "," + y, new Vec3(xz[0] + 0.5, airNear(level, xz[0], y, xz[1]), xz[1] + 0.5), deep));
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static boolean isUnderground(String spiritId) {
        SpiritDef d = SignalLines.SPIRITS.get(spiritId);
        if (d == null) return false;
        String h = d.biomeHint();
        return h.endsWith("deep_dark") || h.endsWith("_caves");
    }

    private static int airNear(ServerLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= 10; d++) {
            for (int s : new int[]{d, -d}) {
                m.set(x, y + s, z);
                if (level.getBlockState(m).isAir() && level.getBlockState(m.above()).isAir()) return y + s;
            }
        }
        return y;
    }

    static String biomeAt(ServerLevel level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        return b.unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
    }

    // ---- ghosts -----------------------------------------------------------------------------------------------------

    private static final Map<String, VeilSpirit> GHOSTS = new HashMap<>();
    /** What each projecting player wants around them (refreshed every 40 ticks). */
    private static final Map<UUID, List<Spot>> WANTED = new HashMap<>();

    static void tickSpirit(ServerPlayer sp, Projection.Spirit s) {
        if (s.age % 40 != 1) return;
        ServerLevel level = sp.level();
        List<Spot> spots = spotsAround(level, sp.position(), 2);
        Map<String, Spot> nearestPerSpirit = new LinkedHashMap<>();
        spots.sort(Comparator.comparingDouble(p -> p.pos().distanceToSqr(sp.position())));
        for (Spot p : spots) {
            if (p.pos().distanceToSqr(sp.position()) > VISIBLE * VISIBLE) break;
            nearestPerSpirit.putIfAbsent(p.spiritId(), p);
            if (nearestPerSpirit.size() >= MAX_NEAR) break;
        }
        WANTED.put(sp.getUUID(), new ArrayList<>(nearestPerSpirit.values()));
        sync(level.getServer());
    }

    static void forget(ServerPlayer sp) {
        WANTED.remove(sp.getUUID());
        CONV.remove(sp.getUUID());
        sync(sp.level().getServer());
    }

    /** Spawns wanted ghosts and removes unwanted ones. */
    private static void sync(MinecraftServer server) {
        Set<String> want = new HashSet<>();
        ServerLevel overworld = server.overworld();
        for (var e : WANTED.entrySet()) {
            for (Spot p : e.getValue()) {
                want.add(p.key());
                VeilSpirit g = GHOSTS.get(p.key());
                if (g != null && !g.isRemoved()) continue;
                VeilSpirit ghost = VeilContent.VEIL_SPIRIT.get().create(overworld, EntitySpawnReason.TRIGGERED);
                if (ghost == null) continue;
                SpiritDef def = SignalLines.SPIRITS.get(p.spiritId());
                if (def == null) continue;
                ghost.snapTo(p.pos().x, p.pos().y, p.pos().z, (float) (Math.floorMod(p.key().hashCode(), 360)), 0F);
                ghost.setSpirit(p.spiritId(), def.factionIndex());
                ghost.setCustomName(Component.literal(def.displayName()));
                ghost.setCustomNameVisible(true);
                if (overworld.addFreshEntity(ghost)) GHOSTS.put(p.key(), ghost);
            }
        }
        GHOSTS.entrySet().removeIf(e -> {
            if (want.contains(e.getKey()) && !e.getValue().isRemoved()) return false;
            if (!e.getValue().isRemoved()) e.getValue().discard();
            return true;
        });
        // ghosts face the nearest spirit
        for (VeilSpirit g : GHOSTS.values()) {
            ServerPlayer near = null;
            double best = Double.MAX_VALUE;
            for (UUID id : WANTED.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || p.level() != g.level()) continue;
                double d = p.distanceToSqr(g);
                if (d < best) { best = d; near = p; }
            }
            if (near != null) {
                double dx = near.getX() - g.getX(), dz = near.getZ() - g.getZ();
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                g.setYRot(yaw);
                g.setYHeadRot(yaw);
                g.setYBodyRot(yaw);
            }
        }
    }

    /** Removes every ghost (server stopping). */
    static void clearAll() {
        GHOSTS.values().forEach(g -> { if (!g.isRemoved()) g.discard(); });
        GHOSTS.clear();
        WANTED.clear();
        CONV.clear();
    }

    // ---- talking ----------------------------------------------------------------------------------------------------

    private record Conversation(String spiritId, int next, int lastAge) {}

    private static final Map<UUID, Conversation> CONV = new HashMap<>();

    /** Look at a spirit and press Focus: the next line of its story. */
    static void talk(ServerPlayer sp, int entityId) {
        Projection.Spirit s = Projection.live(sp);
        if (s == null) return;
        Entity e = sp.level().getEntity(entityId);
        if (!(e instanceof VeilSpirit ghost) || ghost.distanceTo(sp) > TALK_RANGE) return;
        String id = ghost.spiritId();
        SpiritDef def = SignalLines.SPIRITS.get(id);
        if (def == null || def.lines().isEmpty()) return;
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        Conversation c = CONV.get(sp.getUUID());
        int line;
        if (c == null || !c.spiritId().equals(id) || s.age - c.lastAge() > 600) {
            line = 0;
            long now = sp.level().getGameTime();
            long last = mem.spiritLastCounted.getOrDefault(id, -100000L);
            if (now - last >= 1200) {
                // every conversation counts, first or repeat (the emergent Truth listens for repeats)
                mem.spiritLastCounted.put(id, now);
                Progression.count(sp, Counters.SPIRITS_SPOKEN, 1);
            }
        } else {
            line = c.next();
        }
        if (line >= def.lines().size()) line = 0;
        int total = def.lines().size();
        CONV.put(sp.getUUID(), new Conversation(id, line + 1, s.age));
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(def.displayName()))
                .body(Component.literal(def.lines().get(line)))
                .footer(Component.literal((line + 1) + " / " + total + (line + 1 < total ? "   [G] go on" : "")))
                .ttl(20 * 12).priority(4).build());
        sp.level().playSound(null, ghost.getX(), ghost.getEyeY(), ghost.getZ(), VeilContent.WHISPER.get(), SoundSource.NEUTRAL, 0.6F,
                0.8F + 0.1F * def.factionIndex());
        int heard = Math.max(mem.spiritLines.getOrDefault(id, 0), line + 1);
        boolean changed = heard != mem.spiritLines.getOrDefault(id, 0);
        mem.spiritLines.put(id, heard);
        if (line + 1 >= total) {
            CONV.remove(sp.getUUID());
            if (mem.spiritsComplete.add(id)) {
                changed = true;
                DirectorLog.log(sp, "veil", "heard the whole story of " + id);
                if (Progression.profile(sp).counter(NAMED_LATENT) == 0) Progression.setCounter(sp, NAMED_LATENT, 1);
                Progression.markNewThing(sp, "spirit story");
                Signal.toast(sp, Component.literal("You'll remember " + def.name() + ". · Codex"));
            }
        }
        if (changed) sendCodex(sp);
    }

    // ---- Codex ------------------------------------------------------------------------------------------------------

    public static void sendCodex(ServerPlayer sp) {
        AwakenNet.send(sp, new VeilPayloads.Codex(codexCards(sp.getData(VeilContent.MEMORY.get()))));
    }

    static List<VeilPayloads.CodexCard> codexCards(VeilMemory mem) {
        List<VeilPayloads.CodexCard> out = new ArrayList<>();
        for (var e : mem.spiritLines.entrySet()) {
            SpiritDef def = SignalLines.SPIRITS.get(e.getKey());
            if (def == null || e.getValue() <= 0) continue;
            String path = e.getKey().substring(e.getKey().indexOf(':') + 1);
            String cardId = "veil:spirit/" + path;
            int heard = Math.min(e.getValue(), def.lines().size());
            String body = String.join(" ", def.lines().subList(0, heard));
            boolean complete = mem.spiritsComplete.contains(e.getKey());
            List<String> links = new ArrayList<>();
            if (complete && !def.gives().isEmpty()) {
                int colon = def.gives().indexOf(':');
                String kind = colon < 0 ? "hint" : def.gives().substring(0, colon);
                String what = colon < 0 ? def.gives() : def.gives().substring(colon + 1);
                String last = def.lines().get(def.lines().size() - 1);
                String givesId = "veil:" + kind + "/" + what;
                links.add(givesId);
                switch (kind) {
                    case "rumour" -> out.add(new VeilPayloads.CodexCard(givesId, "Rumours", def.displayName() + ": \"" + last + "\"", "",
                            false, List.of(cardId)));
                    case "lore" -> out.add(new VeilPayloads.CodexCard(givesId, "Lore", pretty(what), body, true, List.of(cardId)));
                    default -> out.add(new VeilPayloads.CodexCard(givesId, "Hints", pretty(what), last, true, List.of(cardId)));
                }
            }
            out.add(new VeilPayloads.CodexCard(cardId, "Spirits", def.displayName(), complete ? body : body + " ...", true, List.copyOf(links)));
        }
        return out;
    }

    static String pretty(String id) {
        String s = id.replace('_', ' ').trim();
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    // ---- command support --------------------------------------------------------------------------------------------

    /** For /awaken veil spirits: spots near a position, nearest first. */
    static List<Spot> nearestSpots(ServerLevel level, Vec3 pos, int cellRadius) {
        List<Spot> spots = spotsAround(level, pos, cellRadius);
        spots.sort(Comparator.comparingDouble(p -> p.pos().distanceToSqr(pos)));
        return spots;
    }

    static @Nullable SpiritDef def(String id) {
        return SignalLines.SPIRITS.get(id);
    }
}
