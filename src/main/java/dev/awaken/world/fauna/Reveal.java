package dev.awaken.world.fauna;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Later;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * The awakening is a reveal (plan v2 section 4.1): the moment a player accepts, the screen pulses once and the
 * Awakened layer steps out around them, a few of the biome's ambient creatures and a drift of light motes, so the
 * world visibly changes. The story's Accept happens in the Stillpoint and teleports home in the same call, so the
 * reveal waits until the player stands in the overworld (on the next tick, or the next visit or login).
 */
final class Reveal {
    private Reveal() {}

    /** Ambient creatures that step out at the reveal. */
    static final int BURST = 4;
    /** Players the GameTests treat as having a real client. */
    static final java.util.Set<java.util.UUID> FORCED = new java.util.HashSet<>();

    /**
     * The reveal is something a person sees: only for players whose client speaks our channel (never fake players,
     * machines or other modules' GameTest players, who would otherwise get creatures spawned around them).
     */
    static boolean hasViewer(ServerPlayer p) {
        return FORCED.contains(p.getUUID()) || AwakenNet.canReceive(p, FaunaNet.Pulse.TYPE);
    }

    static void onAwakened(ServerPlayer p) {
        if (!hasViewer(p)) return;
        p.setData(FaunaModule.STATE.get(), p.getData(FaunaModule.STATE.get()).pending());
        Later.run(p.level().getServer(), 30, () -> tryReveal(p));
    }

    /** Plays a pending reveal if the player is now in the overworld. */
    static void tryReveal(ServerPlayer p) {
        if (p.isRemoved() || !p.isAlive() || !hasViewer(p)) return;
        // Carillon's opening wakes you indoors (the infirmary): the layer steps out when the city is yours (SPEC §16.4)
        if (dev.awaken.core.api.OpeningHooks.inOpening(p)) return;
        FaunaState s = p.getData(FaunaModule.STATE.get());
        if (!s.revealPending() || p.level().dimension() != Level.OVERWORLD) return;
        p.setData(FaunaModule.STATE.get(), s.done());
        play(p, 100);
    }

    /** The pulse, the sound, the motes and the burst. Returns how many creatures stepped out. */
    static int play(ServerPlayer p, int strength) {
        ServerLevel level = p.level();
        AwakenNet.send(p, new FaunaNet.Pulse(strength));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.4F, 0.55F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.5F, 1.6F);
        motes(level, p, 0);
        Later.run(level.getServer(), 12, () -> motes(level, p, 1));
        Later.run(level.getServer(), 26, () -> motes(level, p, 2));
        int n = burst(level, p, BURST);
        DirectorLog.log(p, "fauna", "awakening reveal: " + n + " creatures stepped out");
        return n;
    }

    /** Light motes rising out of the grass in a widening ring. */
    private static void motes(ServerLevel level, ServerPlayer p, int wave) {
        if (p.isRemoved() || p.level() != level) return;
        RandomSource r = level.getRandom();
        float radius = 3.5F + wave * 4.0F;
        for (int i = 0; i < 36; i++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            float d = radius + r.nextFloat() * 3.0F;
            double x = p.getX() + Mth.cos(a) * d, z = p.getZ() + Mth.sin(a) * d;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            if (Math.abs(y - p.getY()) > 8) continue;
            level.sendParticles(p, i % 3 == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD, true, false, x, y + 0.2, z, 1, 0.1, 0.3, 0.1, 0.01);
        }
    }

    /** A few of the biome's ambient creatures, out where the player can see them. */
    static int burst(ServerLevel level, ServerPlayer p, int count) {
        List<EntityType<?>> pool = ambientHere(level, p.blockPosition());
        RandomSource r = level.getRandom();
        int spawned = 0;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        for (int attempt = 0; attempt < count * 4 && spawned < count; attempt++) {
            EntityType<?> type = pool.get(r.nextInt(pool.size()));
            float a = yaw + (r.nextFloat() - 0.5F) * 2.4F;
            float d = 5.0F + r.nextFloat() * 7.0F;
            // Minecraft yaw 0 faces +z.
            double x = p.getX() - Mth.sin(a) * d, z = p.getZ() + Mth.cos(a) * d;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            BlockPos at = BlockPos.containing(x, y, z);
            if (Math.abs(y - p.getY()) > 10 || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) continue;
            if (!level.noCollision(type.getSpawnAABB(x, y, z))) continue;
            if (!(type.create(level, EntitySpawnReason.TRIGGERED) instanceof Mob mob)) continue;
            mob.snapTo(x, y, z, r.nextFloat() * 360.0F, 0.0F);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
            level.addFreshEntity(mob);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 6, 0.3, 0.4, 0.3, 0.02);
            spawned++;
        }
        return spawned;
    }

    /** The ambient creatures the biome here would spawn now (by its fauna spawns and their time of day). */
    static List<EntityType<?>> ambientHere(ServerLevel level, BlockPos pos) {
        List<EntityType<?>> out = new ArrayList<>();
        long time = FaunaRules.dayTime(level);
        MobSpawnSettings spawns = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos);
        for (Weighted<MobSpawnSettings.SpawnerData> w : spawns.getMobsToSpawn(MobTypes.FAUNA).unwrap()) {
            MobDef d = MobTypes.def(w.value().type());
            if (d != null && d.fauna == FaunaRole.AMBIENT && Habitat.of(d.id).window().contains(time)) out.add(w.value().type());
        }
        if (out.isEmpty()) {
            // No fauna in this biome (an ocean, a cave): the Greenwild's own heralds.
            boolean night = Habitat.Window.DUSK_NIGHT.contains(time);
            EntityType<?> t = MobTypes.type(night ? "lumen_moth" : "bloom_sprite");
            if (t != null) out.add(t);
        }
        if (out.isEmpty()) out.add(MobTypes.type(Bestiary.fauna().get(0)));
        return out;
    }
}
