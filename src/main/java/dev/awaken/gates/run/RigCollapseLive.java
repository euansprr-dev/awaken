package dev.awaken.gates.run;

import dev.awaken.Awaken;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.world.RigCollapse;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * A Collapse Run while it plays (SPEC §17 "Gate Rig"): the pit throws back whoever falls in (a sting and the start of
 * the run, never a death trap), and the run announces itself the moment the floor starts to go.
 */
final class RigCollapseLive {
    private RigCollapseLive() {}

    static final ResourceKey<DamageType> RUBBLE = ResourceKey.create(Registries.DAMAGE_TYPE, Awaken.id("gate_rubble"));
    /** Ticks in the pit before it throws you out; what it costs. */
    static final int PIT_GRACE = 4;
    static final float PIT_DAMAGE = 3F, PIT_SHARE = 0.08F;

    static void started(ServerLevel level, RigDirector.Live l) {
        RigDirector.sound(level, l.center(), GatesRegistry.SND_RIG_RUN.get(), 1.3F, 0.9F);
        for (ServerPlayer p : l.here) p.sendOverlayMessage(Component.literal("The floor is going. Run."));
    }

    static void tick(ServerLevel level, RigDirector.Live l, long now) {
        if (l.here.isEmpty()) {
            l.inPit.clear();
            return;
        }
        RigCollapse.Run run = l.rig.run;
        double floor = l.world(0, 0, 0).getY();
        for (ServerPlayer p : l.here) {
            double[] q = l.local(p.position());
            boolean in = p.getY() < floor - 1.4 && q[0] >= run.x0() && q[0] < run.x0() + run.w() && q[2] >= run.z0() && q[2] < run.z0() + run.d();
            if (!in) {
                l.inPit.remove(p.getUUID());
                continue;
            }
            if (l.inPit.merge(p.getUUID(), 1, Integer::sum) >= PIT_GRACE) {
                l.inPit.remove(p.getUUID());
                expel(level, l, p);
            }
        }
        // monsters that fall in (waves, listeners) burn or break at the bottom
        if (now % 20 == 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, l.box(), e -> !(e instanceof Player) && e.isAlive() && e.getY() < floor - 1.4)) {
                e.hurtServer(level, level.damageSources().source(RUBBLE), 4F);
            }
        }
    }

    /** Out of the pit: a sting, back on the landing the run started from (facing the way on), and the pit's roar. */
    private static void expel(ServerLevel level, RigDirector.Live l, ServerPlayer p) {
        RigCollapse.Run run = l.rig.run;
        boolean back = l.running(1) && !l.running(0);
        int from = back ? run.rows() - 1 : 0, toward = back ? run.rows() - 3 : 2;
        double[] at = run.rowCentre(from), look = run.rowCentre(toward);
        BlockPos o = l.world(0, 0, 0);
        Vec3 to = new Vec3(o.getX() + at[0], o.getY(), o.getZ() + at[1]);
        float yaw = (float) Math.toDegrees(Math.atan2(-(look[0] - at[0]), look[1] - at[1]));
        p.hurtServer(level, level.damageSources().source(RUBBLE), PIT_DAMAGE + p.getMaxHealth() * PIT_SHARE);
        p.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, 10F, true);
        p.resetFallDistance();
        RigDirector.sound(level, to, GatesRegistry.SND_RIG_PIT.get(), 1F, 1F);
        RigDirector.send(level, l, new RigPayloads.Cue(RigPayloads.Cue.PIT, l.f.theme.ordinal(), List.of(BlockPos.containing(to).asLong()),
                List.of(), 20, 0));
        Signal.toast(p, Component.literal("The pit throws you back."));
    }
}
