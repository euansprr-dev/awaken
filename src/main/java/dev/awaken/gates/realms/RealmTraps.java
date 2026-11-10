package dev.awaken.gates.realms;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GatesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Realm's traps (SPEC §50.9): presses that fill a stretch of a way on a beat, spitters that light and then strike along
 * a line, floor that gives way a moment after it's stood on. Each is the land's own blocks, flipped on the Realm's clock,
 * so a trap is where it is for everyone and a restart only starts the beat again. What a trap is doing follows from the
 * time alone; the world is put right whenever it disagrees. Runs twice a second for each player near one.
 */
final class RealmTraps {
    private RealmTraps() {}

    static final int PRESS_DOWN = 30, PRESS_WARN = 30, SPIT_WARN = 20, SPIT_FIRE = 10, CRUMBLE_DELAY = 15, CRUMBLE_BACK = 100, NEAR = 48;
    /** As the old gates' Gauntlet hurts (RigGauntletLive): a flat part and a share of your health, so it stings at every level. */
    static final float PRESS_DAMAGE = 3F, PRESS_SHARE = 0.11F, SPIT_DAMAGE = 2F, SPIT_SHARE = 0.08F;

    private record Crumble(long breaksAt, long backAt, BlockState on, BlockState off) {}

    /** Which beat each spitter last struck on, so two players near one are not struck twice. */
    private static final Map<Long, Long> FIRED = new HashMap<>();
    private static final Map<BlockPos, Crumble> CRUMBLING = new HashMap<>();

    static int at(Land.Trap t, long now) {
        return (int) Math.floorMod(now + t.phase(), (long) t.period());
    }

    static boolean pressDown(Land.Trap t, long now) {
        return at(t, now) < PRESS_DOWN;
    }

    static boolean pressWarns(Land.Trap t, long now) {
        return at(t, now) >= t.period() - PRESS_WARN;
    }

    static boolean spitLit(Land.Trap t, long now) {
        int c = at(t, now);
        return c >= t.period() - SPIT_WARN || c < SPIT_FIRE;
    }

    static boolean spitFires(Land.Trap t, long now) {
        return at(t, now) < SPIT_FIRE;
    }

    static void tick(ServerLevel level, GateRecord g, Land land, RealmRef ref, ServerPlayer p, int lx, int ly, int lz) {
        List<Land.Trap> traps = land.traps();
        long now = level.getGameTime();
        for (int i = 0; i < traps.size(); i++) {
            Land.Trap t = traps.get(i);
            if (Math.abs((t.x0() + t.x1()) / 2 - lx) > NEAR || Math.abs((t.z0() + t.z1()) / 2 - lz) > NEAR || Math.abs((t.y0() + t.y1()) / 2 - ly) > NEAR) continue;
            BlockPos first = Realms.world(ref.cell(), t.x0(), t.y0(), t.z0());
            if (!level.isLoaded(first)) continue;
            switch (t.kind()) {
                case PRESS -> press(level, ref, t, first, now);
                case SPIT -> spit(level, g, ref, t, i, first, now);
                case CRUMBLE -> step(level, ref, t, p, now);
            }
        }
        if (!CRUMBLING.isEmpty()) crumble(level, now);
    }

    /** It smokes for a second and a half, comes, stays a second and a half, and goes. Whoever is under it is hurt and thrown clear to the nearer side. */
    private static void press(ServerLevel level, RealmRef ref, Land.Trap t, BlockPos first, long now) {
        boolean down = pressDown(t, now), is = level.getBlockState(first) == t.on();
        int ox = RealmCells.centerX(ref.cell()), oz = RealmCells.centerZ(ref.cell());
        double mx = ox + (t.x0() + t.x1() + 1) / 2.0, mz = oz + (t.z0() + t.z1() + 1) / 2.0;
        if (pressWarns(t, now) && !is) {
            level.sendParticles(ParticleTypes.DUST_PLUME, mx, t.y0() + 0.1, mz, 14, (t.x1() - t.x0() + 1) / 2.6, 0.05, (t.z1() - t.z0() + 1) / 2.6, 0.01);
            level.playSound(null, mx, t.y0(), mz, GatesRegistry.SND_RIG_CRACK.get(), SoundSource.BLOCKS, 0.6F, 0.6F + 0.4F * (at(t, now) - (t.period() - PRESS_WARN)) / PRESS_WARN);
        }
        if (down == is) return;
        boolean alongX = t.x1() - t.x0() <= t.z1() - t.z0();
        if (down) {
            AABB box = new AABB(ox + t.x0(), t.y0(), oz + t.z0(), ox + t.x1() + 1, t.y1() + 1, oz + t.z1() + 1);
            for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, box, e -> e.isAlive() && !e.isSpectator())) {
                if (!sp.isCreative()) sp.hurtServer(level, level.damageSources().generic(), PRESS_DAMAGE + sp.getMaxHealth() * PRESS_SHARE);
                double out = alongX ? (sp.getX() < mx ? ox + t.x0() - 0.6 : ox + t.x1() + 1.6) : (sp.getZ() < mz ? oz + t.z0() - 0.6 : oz + t.z1() + 1.6);
                sp.teleportTo(level, alongX ? out : sp.getX(), sp.getY(), alongX ? sp.getZ() : out, Set.of(), sp.getYRot(), sp.getXRot(), true);
            }
        }
        BlockState to = down ? t.on() : t.off();
        for (int x = t.x0(); x <= t.x1(); x++) for (int y = t.y0(); y <= t.y1(); y++) for (int z = t.z0(); z <= t.z1(); z++) {
            BlockPos at = new BlockPos(ox + x, y, oz + z);
            // it never closes on a monster: that cell stays open this beat
            if (down && !level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
            level.setBlock(at, to, Block.UPDATE_CLIENTS);
        }
        level.playSound(null, mx, t.y0(), mz, down ? GatesRegistry.SND_RIG_FALL.get() : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, down ? 1F : 0.5F, down ? 0.8F : 0.6F);
    }

    /** The cell lights a second before it strikes; then everything on its line is hit once and slowed. */
    private static void spit(ServerLevel level, GateRecord g, RealmRef ref, Land.Trap t, int index, BlockPos first, long now) {
        boolean lit = spitLit(t, now), is = level.getBlockState(first) == t.on();
        if (lit != is) {
            level.setBlock(first, lit ? t.on() : t.off(), Block.UPDATE_CLIENTS);
            if (lit) level.playSound(null, first, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 0.9F, 1.6F);
        }
        if (!spitFires(t, now)) return;
        long cycle = (now + t.phase()) / t.period();
        Long last = FIRED.put((long) g.id << 20 | index, cycle);
        if (last != null && last == cycle) return;
        int ox = RealmCells.centerX(ref.cell()), oz = RealmCells.centerZ(ref.cell());
        int n = Math.max(Math.abs(t.x1() - t.x0()), Math.max(Math.abs(t.y1() - t.y0()), Math.abs(t.z1() - t.z0())));
        for (int k = 1; k <= n; k++) {
            double f = (double) k / n;
            level.sendParticles(ParticleTypes.SPIT, ox + t.x0() + (t.x1() - t.x0()) * f + 0.5, t.y0() + (t.y1() - t.y0()) * f + 0.5, oz + t.z0() + (t.z1() - t.z0()) * f + 0.5, 3, 0.12, 0.12, 0.12, 0.02);
        }
        AABB line = new AABB(ox + Math.min(t.x0(), t.x1()), Math.min(t.y0(), t.y1()), oz + Math.min(t.z0(), t.z1()),
                ox + Math.max(t.x0(), t.x1()) + 1, Math.max(t.y0(), t.y1()) + 1, oz + Math.max(t.z0(), t.z1()) + 1).inflate(0.15, 0.6, 0.15);
        for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, line, e -> e.isAlive() && !e.isSpectator() && !e.isCreative())) {
            sp.hurtServer(level, level.damageSources().generic(), SPIT_DAMAGE + sp.getMaxHealth() * SPIT_SHARE);
            sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
        }
        level.playSound(null, first, SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1F, 0.7F);
    }

    /** Standing on a piece of it starts that piece going. */
    private static void step(ServerLevel level, RealmRef ref, Land.Trap t, ServerPlayer p, long now) {
        if (p.isCreative() || p.isSpectator() || !p.onGround()) return;
        BlockPos under = p.getOnPos();
        int x = under.getX() - RealmCells.centerX(ref.cell()), z = under.getZ() - RealmCells.centerZ(ref.cell()), y = under.getY();
        if (x < t.x0() || x > t.x1() || y < t.y0() || y > t.y1() || z < t.z0() || z > t.z1()) return;
        if (level.getBlockState(under) != t.on() || CRUMBLING.containsKey(under)) return;
        CRUMBLING.put(under.immutable(), new Crumble(now + CRUMBLE_DELAY, now + CRUMBLE_DELAY + CRUMBLE_BACK, t.on(), t.off()));
        level.playSound(null, under, GatesRegistry.SND_RIG_CRACK.get(), SoundSource.BLOCKS, 0.8F, 1.3F);
        level.sendParticles(ParticleTypes.DUST_PLUME, under.getX() + 0.5, under.getY() + 1.05, under.getZ() + 0.5, 6, 0.25, 0.02, 0.25, 0.01);
    }

    private static void crumble(ServerLevel level, long now) {
        var it = CRUMBLING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            BlockPos at = e.getKey();
            Crumble c = e.getValue();
            if (!level.isLoaded(at)) continue;
            if (now >= c.backAt()) {
                // it grows back, but never into someone
                if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(at)).isEmpty()) continue;
                level.setBlock(at, c.on(), Block.UPDATE_CLIENTS);
                it.remove();
            } else if (now >= c.breaksAt() && level.getBlockState(at) == c.on()) {
                level.setBlock(at, c.off(), Block.UPDATE_CLIENTS);
                level.playSound(null, at, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 1F, 0.7F);
            }
        }
    }
}
