package dev.awaken.delve.home;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.DescentHooks;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.OpeningHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Optional;

/**
 * The way home on the server (SPEC §28.5): every {@link #SAMPLE} ticks each player's trail takes a sample of where they
 * stand, the Now line offers "Surface ↑ 34 · NE 60" while it's {@link HomeTrail#shown}, and a client's ask for the
 * thread is answered with the path. Overworld only: never in a gate, the Descent, a Hearth or the opening.
 */
public final class HomeServer {
    private HomeServer() {}

    public static final int SAMPLE = 5;
    /**
     * Useful but never over danger (90+) or a clear quest step (a tracked chain 68, a taken Directive 58): over points
     * to spend (55), the beasts and lairs (14-46) and the Hearth's room ideas (50).
     */
    public static final int PRIORITY = 56;
    /** Sky light at the head at most this counts as underground (a house's rooms and a tree's shade stay brighter). */
    public static final int DARK = 6;
    /** The client asks at most this often (ticks). */
    static final int ASK_EVERY = 20;

    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    static void tick(ServerPlayer p) {
        if ((p.tickCount + p.getId()) % SAMPLE != 0) return;
        sample(p);
    }

    /** One sample (server thread): under open sky, under rock, or neither (a roof, a canopy). */
    static void sample(ServerPlayer p) {
        if (p.isSpectator()) return;
        HomeTrail t = p.getData(HomeModule.TRAIL.get());
        ServerLevel level = p.level();
        if (!eligibleDim(level.dimension())) {
            // only the overworld has a sky to come back to; a gate or the Descent ends the trail (you come back by a door)
            if (!t.isEmpty()) t.clear();
            return;
        }
        int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean sky = top <= y + 1;
        boolean under = false;
        if (!sky) {
            // under rock: the sky's light doesn't reach the head, and what's over you isn't a lake's water
            boolean dark = level.getBrightness(LightLayer.SKY, M.set(x, y + 1, z)) <= DARK;
            under = dark && level.getFluidState(M.set(x, top - 1, z)).isEmpty();
        }
        t.step(x, y, z, sky, under, level.getGameTime());
    }

    /** The Now line's source. */
    static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        HomeTrail t = p.getData(HomeModule.TRAIL.get());
        if (!t.shown() || !eligible(p)) return List.of();
        return List.of(line(p, t));
    }

    static NowHooks.Suggestion line(ServerPlayer p, HomeTrail t) {
        return new NowHooks.Suggestion(HomeTrail.NOW_ID, PRIORITY, Component.literal(t.text(p.getBlockX(), p.getBlockY(), p.getBlockZ())),
                Optional.of(t.anchor()));
    }

    /** Where the way home is offered at all. */
    public static boolean eligible(ServerPlayer p) {
        return eligibleDim(p.level().dimension()) && !p.isSpectator() && !GateHooks.inGate(p) && !DescentHooks.inDescent(p)
                && !OpeningHooks.inOpening(p) && !HearthHooks.inside(p.level(), p.blockPosition());
    }

    /** The overworld only: every AWAKEN dimension is a gate, a stratum, a dream or a trial, each with its own way out. */
    public static boolean eligibleDim(ResourceKey<Level> dim) {
        return dim == Level.OVERWORLD;
    }

    /** A client asked for the thread: the path home, if there is one to show. */
    static void ask(ServerPlayer p, boolean force) {
        HomeTrail t = p.getData(HomeModule.TRAIL.get());
        long now = p.level().getGameTime();
        if (now - t.lastAsk < ASK_EVERY) return;
        t.lastAsk = now;
        if (!eligible(p) || t.size() < 2 || (!t.shown() && !force)) return;
        AwakenNet.send(p, new HomeNet.Way(t.path(), t.pathReachesSky(), force));
    }

    /** Runs the thread for this player now (tours, the stage command). */
    static void thread(ServerPlayer p) {
        HomeTrail t = p.getData(HomeModule.TRAIL.get());
        if (t.size() >= 2) AwakenNet.send(p, new HomeNet.Way(t.path(), t.pathReachesSky(), true));
    }
}
