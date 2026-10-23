package dev.awaken.hearth.request;

import dev.awaken.hearth.HearthContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The delivery moment: what you carried streams out of your hands in an arc into the Anchor (the items themselves,
 * with embers), then the Anchor flares and chimes. Server-driven particles over {@link #FLIGHT} ticks, so every
 * player nearby sees the same thing; at most a few runs live at once and each costs a handful of packets a tick.
 */
final class RequestFx {
    private RequestFx() {}

    static final int FLIGHT = 14;
    private static final int MAX_RUNS = 8;

    private static final class Run {
        final ServerLevel level;
        final Vec3 from, ctrl, to;
        final List<ItemParticleOption> items;
        final boolean complete;
        int age;

        Run(ServerLevel level, Vec3 from, Vec3 to, List<ItemParticleOption> items, boolean complete) {
            this.level = level;
            this.from = from;
            this.to = to;
            this.items = items;
            this.complete = complete;
            double lift = 2.2 + from.distanceTo(to) * 0.18;
            this.ctrl = from.add(to).scale(0.5).add(0, lift, 0);
        }

        Vec3 at(double u) {
            double a = (1 - u) * (1 - u), b = 2 * (1 - u) * u, c = u * u;
            return new Vec3(a * from.x + b * ctrl.x + c * to.x, a * from.y + b * ctrl.y + c * to.y, a * from.z + b * ctrl.z + c * to.z);
        }

        Vec3 tangent(double u) {
            Vec3 d = ctrl.subtract(from).scale(2 * (1 - u)).add(to.subtract(ctrl).scale(2 * u));
            return d.scale(1.0 / FLIGHT);
        }
    }

    private static final List<Run> RUNS = new ArrayList<>();

    /** Starts the stream from the player to the Anchor. {@code kinds}: one stack per kind of item delivered (up to three). */
    static void deliver(ServerLevel level, ServerPlayer sp, BlockPos anchor, List<ItemStack> kinds, boolean complete) {
        if (RUNS.size() >= MAX_RUNS) RUNS.remove(0);
        List<ItemParticleOption> items = new ArrayList<>();
        for (ItemStack s : kinds) {
            if (items.size() >= 3) break;
            if (!s.isEmpty()) items.add(new ItemParticleOption(ParticleTypes.ITEM, s.getItem()));
        }
        Vec3 hands = sp.position().add(sp.getLookAngle().multiply(0.4, 0, 0.4)).add(0, 1.15, 0);
        Vec3 top = Vec3.atBottomCenterOf(anchor).add(0, 1.05, 0);
        RUNS.add(new Run(level, hands, top, items, complete));
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), RequestContent.SND_DELIVER.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        for (int i = RUNS.size() - 1; i >= 0; i--) {
            Run r = RUNS.get(i);
            int t = r.age++;
            if (t < FLIGHT) stream(r, t);
            else if (t == FLIGHT) arrive(r);
            if (t >= FLIGHT + 6) RUNS.remove(i);
            else if (t > FLIGHT) glow(r, t - FLIGHT);
        }
    }

    private static void stream(Run r, int t) {
        for (int k = 0; k < 3; k++) {
            double u = (t + k / 3.0) / FLIGHT;
            for (int j = 0; j < r.items.size(); j++) {
                double uj = Math.max(0, u - j * 0.05);
                Vec3 p = r.at(uj), v = r.tangent(uj);
                // count 0: the offsets are the velocity, so each bit of the item flies along the arc
                r.level.sendParticles(r.items.get(j), p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
            }
            Vec3 p = r.at(u);
            r.level.sendParticles(HearthContent.HEARTH_EMBER.get(), p.x, p.y, p.z, 1, 0.06, 0.06, 0.06, 0.0);
        }
        Vec3 head = r.at(Math.min(1, (t + 1.0) / FLIGHT));
        r.level.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 1, 0.02, 0.02, 0.02, 0.0);
    }

    private static void arrive(Run r) {
        Vec3 c = r.to;
        r.level.sendParticles(HearthContent.HEARTH_EMBER.get(), c.x, c.y + 0.2, c.z, r.complete ? 46 : 18, 0.45, 0.35, 0.45, 0.04);
        r.level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.3, c.z, r.complete ? 16 : 5, 0.2, 0.4, 0.2, 0.08);
        if (r.complete) {
            // a flat ring of light around the Anchor
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                r.level.sendParticles(ParticleTypes.WAX_ON, c.x + Math.cos(a) * 1.4, c.y - 0.6, c.z + Math.sin(a) * 1.4, 0,
                        Math.cos(a), 0.05, Math.sin(a), 0.12);
            }
            r.level.playSound(null, c.x, c.y, c.z, RequestContent.SND_COMPLETE.get(), SoundSource.BLOCKS, 1f, 1f);
            r.level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.7f, 1.3f);
        } else {
            r.level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9f, 1.4f);
        }
    }

    private static void glow(Run r, int t) {
        if (!r.complete) return;
        Vec3 c = r.to;
        r.level.sendParticles(HearthContent.HEARTH_EMBER.get(), c.x, c.y + 0.3 + t * 0.25, c.z, 2, 0.15, 0.1, 0.15, 0.01);
    }

    static void clear() {
        RUNS.clear();
    }

    static int running() {
        return RUNS.size();
    }
}
