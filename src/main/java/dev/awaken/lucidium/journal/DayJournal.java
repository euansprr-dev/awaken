package dev.awaken.lucidium.journal;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.LucidiumWorld;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.dream.Planner;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.plan.Nightmares;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Day Journal: all day it quietly snapshots memorable places (at most 10 per play-day, 64 kept): where you nearly
 * died, a boss's arena, your bed, a view you stopped to look at, where you fought the most, a new biome or structure,
 * a gate's mouth. Never another player's base: nothing is captured within 32 blocks of someone else's bed or Hearth.
 */
public final class DayJournal {
    private DayJournal() {}

    public static final int OTHERS_RADIUS = 32;
    private static final int SAME_KIND_SPACING = 24;

    private static final class Watch {
        Vec3 lastPos = Vec3.ZERO;
        float lastYaw, lastPitch;
        int still;
        int home;
        long lastNearDeath = -100000;
    }

    private static final Map<UUID, Watch> WATCH = new HashMap<>();

    static boolean enabled(ServerPlayer sp) {
        return AwakenConfig.LUCIDIUM.get() && sp.level().dimension() != LucidiumContent.LUCIDIUM && !sp.isSpectator();
    }

    // ---- periodic checks ---------------------------------------------------------------------------------------------

    public static void onPlayerTick(ServerPlayer sp) {
        if ((sp.tickCount + (sp.getId() & 15)) % 20 != 0 || !enabled(sp)) return;
        Watch w = WATCH.computeIfAbsent(sp.getUUID(), k -> new Watch());
        ServerLevel level = (ServerLevel) sp.level();
        BlockPos pos = sp.blockPosition();

        // home: near your bed or Hearth for a few seconds
        boolean nearBed = false;
        var cfg = sp.getRespawnConfig();
        if (cfg != null && cfg.respawnData().dimension().equals(level.dimension()) && cfg.respawnData().pos().distSqr(pos) <= 36) nearBed = true;
        boolean nearHearth = HearthHooks.hearthOf(sp).map(h -> level.dimension() == net.minecraft.world.level.Level.OVERWORLD && h.distSqr(pos) <= 144).orElse(false);
        w.home = (nearBed || nearHearth) ? w.home + 1 : 0;
        if (w.home == 5) capture(sp, Snapshot.Kind.HOME, nearBed ? cfg.respawnData().pos().above() : pos, nearBed ? "your bed" : "your Hearth");

        // a vista: standing still, high up, looking out over a drop
        Vec3 now = sp.position();
        boolean still = now.distanceToSqr(w.lastPos) < 0.01 && Math.abs(sp.getYRot() - w.lastYaw) < 25 && Math.abs(sp.getXRot() - w.lastPitch) < 15;
        w.still = still ? w.still + 1 : 0;
        w.lastPos = now;
        w.lastYaw = sp.getYRot();
        w.lastPitch = sp.getXRot();
        if (w.still == 4 && isVista(sp, level)) {
            capture(sp, Snapshot.Kind.VISTA, pos, "the view over the " + Describe.pretty(biomeId(level, pos)));
        }

        // every other second: a new biome or structure
        if ((sp.tickCount / 20) % 2 == 0) {
            Dreamer d = sp.getData(LucidiumContent.DREAMER);
            String biome = biomeId(level, pos);
            if (!biome.isEmpty() && d.biomesSeen.add(biome)) {
                while (d.biomesSeen.size() > 256) d.biomesSeen.remove(d.biomesSeen.iterator().next());
                capture(sp, Snapshot.Kind.BIOME, pos, "your first " + Describe.pretty(biome));
            }
            StructureStart start = level.structureManager().getStructureWithPieceAt(pos, h -> true);
            if (start.isValid()) {
                var key = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
                if (key != null && d.structuresSeen.add(key.toString())) {
                    while (d.structuresSeen.size() > 128) d.structuresSeen.remove(d.structuresSeen.iterator().next());
                    capture(sp, Snapshot.Kind.STRUCTURE, pos, Describe.structureNoun(key.toString()));
                }
            }
        }
    }

    private static boolean isVista(ServerPlayer sp, ServerLevel level) {
        if (!sp.onGround() || sp.getXRot() < -30 || sp.getXRot() > 40) return false;
        BlockPos pos = sp.blockPosition();
        if (pos.getY() < level.getSeaLevel() + 25 || !level.canSeeSky(pos.above())) return false;
        Vec3 look = sp.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 0.3) return false;
        int drops = 0;
        for (int dist = 12; dist <= 36; dist += 12) {
            int x = (int) Math.floor(sp.getX() + look.x / len * dist), z = (int) Math.floor(sp.getZ() + look.z / len * dist);
            if (!level.isLoaded(new BlockPos(x, pos.getY(), z))) return false;
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) <= pos.getY() - 12) drops++;
        }
        return drops >= 2;
    }

    private static String biomeId(ServerLevel level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        return b.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    // ---- events ------------------------------------------------------------------------------------------------------

    /** After damage: under a quarter of your health and still standing. */
    public static void onHurt(ServerPlayer sp) {
        if (!enabled(sp) || !sp.isAlive() || sp.getHealth() > sp.getMaxHealth() * 0.25f) return;
        Watch w = WATCH.computeIfAbsent(sp.getUUID(), k -> new Watch());
        if (sp.tickCount - w.lastNearDeath < 600) return;
        w.lastNearDeath = sp.tickCount;
        capture(sp, Snapshot.Kind.NEAR_DEATH, sp.blockPosition(), "where you nearly died");
    }

    /** Something died: a boss arena, or another kill in the place you fought most today. */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        if (!enabled(killer)) return;
        MobRole role = victim.getData(CoreApi.MOB_ROLE.get());
        boolean boss = role.role() == MobRole.Role.BOSS || victim.getType() == EntityTypes.WITHER
                || victim.getType() == EntityTypes.ENDER_DRAGON || victim.getType() == EntityTypes.ELDER_GUARDIAN
                || victim.getType() == EntityTypes.WARDEN;
        if (boss) {
            String name = victim.getName().getString();
            capture(killer, Snapshot.Kind.BOSS, victim.blockPosition(), "the arena where " + name + " fell");
            return;
        }
        if (!(victim instanceof Enemy)) return;
        Dreamer d = killer.getData(LucidiumContent.DREAMER);
        d.rollDay(PlayDays.current(killer.level().getServer()));
        long area = BlockPos.asLong(victim.getBlockX() >> 5, 0, victim.getBlockZ() >> 5);
        int count = d.killAreas.merge(area, 1, Integer::sum);
        int best = 0;
        for (int v : d.killAreas.values()) best = Math.max(best, v);
        if (count == best && (count == 5 || count == 12 || count == 25)) {
            String mob = Describe.pretty(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
            recaptureKills(killer, victim.blockPosition(), "where you fought the " + mob + "s");
        }
        while (d.killAreas.size() > 64) d.killAreas.remove(d.killAreas.keySet().iterator().next());
    }

    /** The player died (not in a dream): remember the fear for this week's nightmares. */
    public static void onPlayerDeath(ServerPlayer sp, Nightmares.Family family) {
        Dreamer d = sp.getData(LucidiumContent.DREAMER);
        d.markDeath(family, PlayDays.current(sp.level().getServer()));
    }

    /** Stepping through a gate: the mouth of it. */
    public static void onTravel(ServerPlayer sp, String targetDim) {
        if (!enabled(sp)) return;
        if (targetDim.equals(Awaken.MODID + ":gates")) capture(sp, Snapshot.Kind.GATE, sp.blockPosition(), "a gate's mouth");
    }

    /** Lying down: if home wasn't remembered today, remember the bed now. */
    public static void onSleep(ServerPlayer sp, BlockPos bed) {
        if (!enabled(sp)) return;
        Dreamer d = sp.getData(LucidiumContent.DREAMER);
        long day = PlayDays.current(sp.level().getServer());
        d.rollDay(day);
        if (d.todayKinds.getOrDefault(Snapshot.Kind.HOME, 0) == 0) capture(sp, Snapshot.Kind.HOME, bed.above(), "your bed");
    }

    // ---- capture -----------------------------------------------------------------------------------------------------

    /** True if a snapshot here would include someone else's bed or Hearth. */
    public static boolean othersBase(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (LucidiumWorld.get(server).otherBedNear(sp.getUUID(), level.dimension(), pos, OTHERS_RADIUS)) return true;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == sp) continue;
            var cfg = other.getRespawnConfig();
            if (cfg != null && cfg.respawnData().dimension().equals(level.dimension())
                    && cfg.respawnData().pos().distSqr(pos) <= OTHERS_RADIUS * OTHERS_RADIUS) return true;
            Optional<BlockPos> theirs = HearthHooks.hearthOf(other);
            if (theirs.isPresent() && level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                    && theirs.get().distSqr(pos) <= OTHERS_RADIUS * OTHERS_RADIUS) return true;
        }
        Optional<BlockPos> near = HearthHooks.nearest(level, pos, OTHERS_RADIUS);
        if (near.isPresent()) {
            Optional<BlockPos> mine = HearthHooks.hearthOf(sp);
            if (mine.isEmpty() || !mine.get().equals(near.get())) return true;
        }
        return false;
    }

    /** Captures a snapshot if today's allowance, spacing and privacy allow. Returns the snapshot if saved. */
    public static Optional<Snapshot> capture(ServerPlayer sp, Snapshot.Kind kind, BlockPos feet, String label) {
        ServerLevel level = (ServerLevel) sp.level();
        MinecraftServer server = level.getServer();
        Dreamer d = sp.getData(LucidiumContent.DREAMER);
        long day = PlayDays.current(server);
        if (!d.canCapture(kind, day)) return Optional.empty();
        if (kind != Snapshot.Kind.MANUAL && kind != Snapshot.Kind.VISION) {
            for (Snapshot s : d.snapshots) {
                if (s.day() == day && s.kind() == kind && s.dim().equals(level.dimension().identifier().toString())
                        && s.pos().distSqr(feet) < SAME_KIND_SPACING * SAME_KIND_SPACING) return Optional.empty();
            }
        }
        if (othersBase(sp, level, feet)) {
            DirectorLog.log(sp, "journal", "skipped a " + kind.name().toLowerCase() + " snapshot: another player's base is near");
            return Optional.empty();
        }
        if (!level.isLoaded(feet.offset(-8, 0, -8)) || !level.isLoaded(feet.offset(7, 0, 7))) return Optional.empty();
        CompoundTag tag = SnapshotFiles.capture(level, feet);
        Fragment f = Fragment.fromTemplate(BuiltInRegistries.BLOCK, tag);
        if (f.solidCount() < 12) return Optional.empty(); // nothing there worth dreaming of
        String detail = Describe.place(f, kind == Snapshot.Kind.STRUCTURE ? label : "");
        Snapshot s = new Snapshot(d.newId(day), kind, day, level.dimension().identifier().toString(), feet.immutable(), label,
                detail, level.getGameTime());
        Optional<List<String>> removed = d.add(s);
        if (removed.isEmpty()) return Optional.empty();
        SnapshotFiles.write(server, sp.getUUID(), s.id(), tag);
        SnapshotFiles.delete(server, sp.getUUID(), removed.get());
        Planner.journalChanged(sp, s);
        DirectorLog.log(sp, "journal", "remembered " + label + " (" + detail + ")");
        return Optional.of(s);
    }

    /** The "most kills" place moves when a busier one appears: replace today's, don't add another. */
    private static void recaptureKills(ServerPlayer sp, BlockPos feet, String label) {
        ServerLevel level = (ServerLevel) sp.level();
        Dreamer d = sp.getData(LucidiumContent.DREAMER);
        long day = PlayDays.current(level.getServer());
        if (d.todayKinds.getOrDefault(Snapshot.Kind.KILLS, 0) == 0) {
            capture(sp, Snapshot.Kind.KILLS, feet, label);
            return;
        }
        if (othersBase(sp, level, feet) || !level.isLoaded(feet.offset(-8, 0, -8)) || !level.isLoaded(feet.offset(7, 0, 7))) return;
        CompoundTag tag = SnapshotFiles.capture(level, feet);
        Fragment f = Fragment.fromTemplate(BuiltInRegistries.BLOCK, tag);
        Snapshot s = new Snapshot(d.newId(day), Snapshot.Kind.KILLS, day, level.dimension().identifier().toString(), feet.immutable(),
                label, Describe.place(f, ""), level.getGameTime());
        d.replaceToday(s).ifPresent(old -> {
            SnapshotFiles.write(level.getServer(), sp.getUUID(), s.id(), tag);
            SnapshotFiles.delete(level.getServer(), sp.getUUID(), List.of(old));
        });
    }

    public static void forget(ServerPlayer sp) {
        WATCH.remove(sp.getUUID());
    }

    @SuppressWarnings("unused")
    private static boolean isBoss(Entity e) {
        return false;
    }
}
