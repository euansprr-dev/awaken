package dev.awaken.world.anomaly;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * The calm anomaly, day only: a spirit procession. Five warm lights wait 32–64 blocks away; when the player comes
 * close they drift off along a 20–36 block path, moving only while followed, and at its end they sink into a small
 * spirit cache. Opening it pays its loot table and, the first time, a discovery ("rewards curiosity"). Nobody follows,
 * and after six minutes the lights simply fade.
 */
final class Processions {
    private Processions() {}

    static final int MOTES = 5;
    static final int WAIT = 6 * Pacing.MINUTE;
    static final int CACHE_LIFE = 5 * Pacing.MINUTE;
    /** The lights set off when the player comes this close to them... */
    static final int NOTICE = 16;
    /** ...and keep going while the player stays this close to the lead light. */
    static final int FOLLOW = 12;
    /** Blocks along the path per director pass (10 ticks) while followed: an easy walking pace. */
    static final double PACE = 0.8;
    /** Spacing between the lights along the path. */
    static final double GAP = 2.2;
    static final int START_MAX = 64;
    static final int PATH_MIN = 20, PATH_MAX = 36;

    /** Where the lights lead from {@code start}: onward, away from the player, onto another valid spot. */
    static @Nullable BlockPos findTarget(ServerLevel level, ServerPlayer p, BlockPos start, AnomalyData data, RandomSource r,
                                         Spots.Rules base, Map<String, Integer> rejects) {
        double away = Math.atan2(start.getZ() - p.getBlockZ(), start.getX() - p.getBlockX());
        return Spots.find(level, start, r, base.distance(PATH_MIN, PATH_MAX), data, away, Math.PI * 0.6, rejects);
    }

    static Anomaly open(ServerLevel level, ServerPlayer owner, BlockPos start, BlockPos target, AnomalyData data) {
        long now = level.getGameTime();
        Anomaly a = new Anomaly(data.allocateId(), AnomalyKind.SPIRIT_PROCESSION, owner.getUUID(), start, now, now + WAIT);
        a.target = target.immutable();
        data.add(a);
        ensureMotes(level, a, now);
        level.playSound(null, start.getX() + 0.5, start.getY() + 1.5, start.getZ() + 0.5, AnomalyContent.MOTES.get(), SoundSource.AMBIENT, 2.0F, 1.1F);
        return a;
    }

    static boolean owns(ServerLevel level, int anomalyId, UUID mote) {
        Anomaly a = AnomalyData.get(level.getServer()).find(anomalyId);
        return a != null && a.kind == AnomalyKind.SPIRIT_PROCESSION && a.stage != Anomaly.CACHE && a.motes.contains(mote);
    }

    static double pathLength(Anomaly a) {
        return Math.max(1.0, Spots.horizontal(a.pos, a.target));
    }

    /** A point {@code t} (0..1) along the path, floating a little over the ground. */
    static Vec3 along(Level level, Anomaly a, double t) {
        double x = Mth.lerp(t, a.pos.getX(), a.target.getX()) + 0.5;
        double z = Mth.lerp(t, a.pos.getZ(), a.target.getZ()) + 0.5;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double ground = level.hasChunkAt(bx, bz) ? Spots.surface(level, bx, bz).getY() : Mth.lerp(t, a.pos.getY(), a.target.getY());
        return new Vec3(x, ground + 2.3, z);
    }

    /** Where the lead light is (for the Now line and the follow check). */
    static Vec3 lead(Level level, Anomaly a) {
        return a.stage == Anomaly.CACHE ? Vec3.atCenterOf(a.focus()) : along(level, a, a.progress);
    }

    /** One director pass. Returns why it ended, or null while it goes on. */
    static @Nullable String update(ServerLevel level, AnomalyData data, Anomaly a, long now) {
        if (a.stage == Anomaly.CACHE) {
            if (now >= a.endsAt) return "the cache faded unopened";
            BlockPos at = a.focus();
            if (level.hasChunkAt(at) && !level.getBlockState(at).is(AnomalyContent.SPIRIT_CACHE.get())) return "the cache is gone";
            return null;
        }
        if (now >= a.endsAt) return "nobody followed the lights";
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        Vec3 lead = along(level, a, a.progress);
        if (owner != null && owner.level() == level && owner.isAlive() && !owner.isSpectator()) {
            double d = Math.sqrt(Mth.square(owner.getX() - lead.x) + Mth.square(owner.getZ() - lead.z));
            if (d <= (a.stage == Anomaly.OPEN ? NOTICE : FOLLOW)) {
                if (a.stage == Anomaly.OPEN) {
                    a.stage = Anomaly.LEADING;
                    level.playSound(null, lead.x, lead.y, lead.z, AnomalyContent.MOTES.get(), SoundSource.AMBIENT, 1.5F, 1.3F);
                    DirectorLog.log(owner, "anomaly", "procession #" + a.id + ": you came close, the lights set off");
                }
                a.progress = Math.min(1.0, a.progress + PACE / pathLength(a));
                data.setDirty();
            }
        }
        if (a.progress >= 1.0) return placeCache(level, data, a, owner, now) ? null : "no room for the cache at the end of the path";
        ensureMotes(level, a, now);
        steer(level, a, now);
        return null;
    }

    /** Light {@code i}'s goal: a slow ring at the start while waiting, a line along the path while leading. */
    private static Vec3 goalOf(Level level, Anomaly a, int i, long now) {
        if (a.stage == Anomaly.OPEN) {
            double ang = now * 0.02 + i * (Math.PI * 2 / MOTES);
            return new Vec3(a.pos.getX() + 0.5 + Math.cos(ang) * 1.6, a.pos.getY() + 2.0 + 0.3 * Math.sin(now * 0.05 + i), a.pos.getZ() + 0.5 + Math.sin(ang) * 1.6);
        }
        double t = Math.max(0.0, a.progress - i * GAP / pathLength(a));
        return along(level, a, t);
    }

    private static void steer(ServerLevel level, Anomaly a, long now) {
        for (int i = 0; i < a.motes.size(); i++) {
            if (level.getEntity(a.motes.get(i)) instanceof AnomalyMote m) m.goal(goalOf(level, a, i, now));
        }
    }

    /** Puts back lights whose chunk unloaded (they are never saved), once the place they hang in is ticking again. */
    static void ensureMotes(ServerLevel level, Anomaly a, long now) {
        Vec3 lead = a.stage == Anomaly.OPEN ? Vec3.atCenterOf(a.pos) : along(level, a, a.progress);
        if (!level.isPositionEntityTicking(BlockPos.containing(lead))) return;
        for (int i = 0; i < MOTES; i++) {
            if (i < a.motes.size() && level.getEntity(a.motes.get(i)) instanceof AnomalyMote m && !m.isRemoved()) continue;
            AnomalyMote m = AnomalyContent.MOTE.get().create(level, EntitySpawnReason.TRIGGERED);
            if (m == null) return;
            Vec3 g = goalOf(level, a, i, now);
            m.snapTo(g.x, g.y, g.z, 0.0F, 0.0F);
            m.goal(g);
            m.anomalyId = a.id;
            level.addFreshEntity(m);
            if (i < a.motes.size()) a.motes.set(i, m.getUUID());
            else a.motes.add(m.getUUID());
        }
    }

    /** The end of the path: the lights sink into a cache. */
    static boolean placeCache(ServerLevel level, AnomalyData data, Anomaly a, @Nullable ServerPlayer owner, long now) {
        int x = a.target.getX(), z = a.target.getZ();
        if (!level.hasChunkAt(x, z)) return true; // wait until it's loaded
        BlockPos top = Spots.surface(level, x, z).above();
        BlockState here = level.getBlockState(top);
        if (!here.canBeReplaced() || !here.getFluidState().isEmpty()) return false;
        level.setBlock(top, AnomalyContent.SPIRIT_CACHE.get().defaultBlockState(), 3);
        a.target = top.below();
        a.stage = Anomaly.CACHE;
        a.cachePlaced = true;
        a.endsAt = now + CACHE_LIFE;
        data.setDirty();
        for (UUID id : a.motes) {
            if (level.getEntity(id) instanceof AnomalyMote m) {
                m.goal(top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5);
                m.fadeAfter(30 + level.getRandom().nextInt(20));
            }
        }
        level.playSound(null, top, AnomalyContent.CACHE.get(), SoundSource.AMBIENT, 1.5F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, top.getX() + 0.5, top.getY() + 0.6, top.getZ() + 0.5, 24, 0.3, 0.4, 0.3, 0.04);
        if (owner != null && owner.level() == level) {
            Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The lights leave something behind"))
                    .body(Component.literal("A spirit cache · " + Spots.where(owner.blockPosition(), top)))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
            DirectorLog.log(owner, "anomaly", "procession #" + a.id + ": the lights sank into a cache at " + top.toShortString());
        }
        return true;
    }

    /** Someone opened (used or broke) a spirit cache at {@code pos}. Pays the procession's reward once. */
    static void cacheOpened(ServerLevel level, BlockPos pos, ServerPlayer p) {
        AnomalyData data = AnomalyData.get(level.getServer());
        for (Anomaly a : data.active()) {
            if (a.kind != AnomalyKind.SPIRIT_PROCESSION || a.stage != Anomaly.CACHE || !a.focus().equals(pos)) continue;
            AnomalyDirector.discovered(p, a.kind);
            Progression.worldXp(p, "anomaly", 0.04);
            Progression.count(p, "awaken:spirit_caches");
            p.getData(AnomalyContent.MEMORY).caches++;
            level.playSound(null, pos, AnomalyContent.CACHE.get(), SoundSource.PLAYERS, 1.2F, 1.4F);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Spirit cache"))
                    .body(Component.literal("A gift from the lights"))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 5).build());
            Playtest.log(p, "anomaly_closed", "kind", a.kind.id);
            AnomalyDirector.end(level.getServer(), data, a, "cache opened by " + p.getGameProfile().name(), false);
            return;
        }
    }

    /** Ended: the lights fade; an unopened cache comes down (now, or when its chunk next loads). */
    static void cleanup(ServerLevel level, AnomalyData data, Anomaly a, boolean removeBlocks) {
        for (UUID id : a.motes) if (level.getEntity(id) instanceof AnomalyMote m) m.fadeAfter(10);
        if (!removeBlocks || !a.cachePlaced) return;
        BlockPos at = a.focus();
        if (level.hasChunkAt(at)) {
            if (level.getBlockState(at).is(AnomalyContent.SPIRIT_CACHE.get())) level.removeBlock(at, false);
        } else {
            data.removals().add(new AnomalyData.Removal(level.dimension().identifier().toString(), at));
            data.setDirty();
        }
    }
}
