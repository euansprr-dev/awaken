package dev.awaken.gates.run;

import dev.awaken.Awaken;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.RigBlocks;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.world.RigBeam;
import dev.awaken.gates.world.RigMirrors;
import dev.awaken.gates.world.RigRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Mirror Hall while people are in it (SPEC §17 "Gate Rig"): the light is traced through the real blocks every tick
 * (the same {@link RigBeam} the client draws and the solvability check walks), reaching the seal fires the hall's
 * trigger, monsters standing in the light burn, and using a mirror swings it a quarter round.
 */
final class RigMirrorsLive {
    private RigMirrorsLive() {}

    static final ResourceKey<DamageType> LIGHT = ResourceKey.create(Registries.DAMAGE_TYPE, Awaken.id("gate_light"));
    /** What the light does to a monster in it, every half second: a burn, more at higher ranks, and fire. */
    static final float BURN = 3F, BURN_PER_RANK = 1.5F;
    /** A mirror turns at most this often (its swing must finish first). */
    static final int TURN_COOLDOWN = RigRoom.SPIN_TICKS + 2;

    private static final Map<Long, Long> TURNED = new HashMap<>();

    static void tick(ServerLevel level, RigDirector.Live l, long now) {
        if (l.here.isEmpty()) return;
        RigMirrors.Puzzle pz = l.rig.mirrors;
        List<int[]> segments = new ArrayList<>(8);
        long hit = RigBeam.trace(grid(level, l), pz.lensX(), pz.lensZ(), pz.lensDir(),
                (ax, az, bx, bz, dir) -> segments.add(new int[]{ax, az, bx, bz}));
        l.beamOn = hit == RigBeam.pack(pz.sealX(), pz.sealZ());
        if (now % 10 == 0) burn(level, l, segments);
        if (now % 80 == 0) {
            BlockPos lens = l.world(pz.lensX(), RigMirrors.BEAM_DY, pz.lensZ());
            RigDirector.sound(level, Vec3.atCenterOf(lens), GatesRegistry.SND_RIG_HUM.get(), 0.5F, 1F);
        }
    }

    /** The hall as the light sees it in the world: mirrors by their turn, the seal, anything solid, the room's walls. */
    static RigBeam.Grid grid(ServerLevel level, RigDirector.Live l) {
        RigMirrors.Puzzle pz = l.rig.mirrors;
        return new RigBeam.Grid() {
            @Override
            public RigBeam.Hit at(int x, int z) {
                BlockPos p = l.world(x, RigMirrors.BEAM_DY, z);
                BlockState s = level.getBlockState(p);
                if (x == pz.sealX() && z == pz.sealZ()) return s.is(GatesRegistry.RIG_SEAL.get()) ? RigBeam.Hit.RECEIVER : RigBeam.Hit.SOLID;
                if (!pz.interior(x, z)) return RigBeam.Hit.SOLID;
                if (s.is(GatesRegistry.RIG_MIRROR.get())) return RigBeam.Hit.MIRROR;
                return s.getCollisionShape(level, p).isEmpty() ? RigBeam.Hit.EMPTY : RigBeam.Hit.SOLID;
            }

            @Override
            public int turn(int x, int z) {
                BlockState s = level.getBlockState(l.world(x, RigMirrors.BEAM_DY, z));
                return s.is(GatesRegistry.RIG_MIRROR.get()) ? s.getValue(RigBlocks.Mirror.TURN) : 0;
            }
        };
    }

    private static void burn(ServerLevel level, RigDirector.Live l, List<int[]> segments) {
        ServerPlayer credit = l.turner == null ? null : level.getServer().getPlayerList().getPlayer(l.turner);
        float dmg = BURN + BURN_PER_RANK * l.g.plan.rank().ordinal();
        BlockPos o = l.world(0, RigMirrors.BEAM_DY, 0);
        for (int[] s : segments) {
            AABB beam = new AABB(o.getX() + Math.min(s[0], s[2]) + 0.15, o.getY() + 0.1, o.getZ() + Math.min(s[1], s[3]) + 0.15,
                    o.getX() + Math.max(s[0], s[2]) + 0.85, o.getY() + 0.9, o.getZ() + Math.max(s[1], s[3]) + 0.85);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, beam, e -> !(e instanceof Player) && e.isAlive() && !e.isInvulnerable())) {
                if (e.hurtServer(level, level.damageSources().source(LIGHT, null, credit), dmg)) {
                    e.igniteForSeconds(3F);
                    level.sendParticles(GatesRegistry.P_SPARK.get(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
                    RigDirector.sound(level, e.position(), GatesRegistry.SND_RIG_BURN.get(), 0.7F, 0.9F + 0.2F * level.getRandom().nextFloat());
                }
            }
        }
    }

    /** Swings a mirror a quarter clockwise: the real block takes its new turn at once (hidden while the client swings it). */
    static void turn(ServerLevel level, RigDirector.Live l, ServerPlayer p, BlockPos pos, long now) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(GatesRegistry.RIG_MIRROR.get()) || s.getValue(RigBlocks.Mirror.TURNING)) return;
        Long last = TURNED.get(pos.asLong());
        if (last != null && now - last < TURN_COOLDOWN) return;
        TURNED.put(pos.asLong(), now);
        int old = s.getValue(RigBlocks.Mirror.TURN);
        BlockState next = s.setValue(RigBlocks.Mirror.TURN, (old + 1) % 4);
        level.setBlock(pos, next.setValue(RigBlocks.Mirror.TURNING, true), Block.UPDATE_CLIENTS);
        l.pending.add(new RigDirector.Pending(now + RigRoom.SPIN_TICKS, pos.immutable(), next, -1, 0));
        l.turner = p.getUUID();
        RigDirector.sound(level, Vec3.atCenterOf(pos), GatesRegistry.SND_RIG_TURN.get(), 0.9F, 0.9F + 0.15F * old);
        RigDirector.send(level, l, new RigPayloads.Cue(RigPayloads.Cue.SPIN, l.f.theme.ordinal(), List.of(pos.asLong()),
                List.of(Block.getId(next)), RigRoom.SPIN_TICKS, old));
        if (TURNED.size() > 512) TURNED.clear();
    }

    static void resetAll() {
        TURNED.clear();
    }
}
