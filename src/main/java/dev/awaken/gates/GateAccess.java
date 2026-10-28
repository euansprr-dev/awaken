package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.GateHooks;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.Hash;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/** The gates module's side of {@link GateHooks}: what other modules (story, veil, above, hearth) can see and ask. */
final class GateAccess implements GateHooks.Access {
    static void install() {
        GateHooks.install(new GateAccess());
    }

    private static GateHooks.GateView view(ServerLevel level, GateRecord g) {
        GatePlan p = g.plan;
        return new GateHooks.GateView(g.key(), g.pos, p.rank().ordinal(), p.theme().themeId(), p.theme().displayName, p.theme().omen,
                p.layout().displayName, p.modifiers().stream().map(m -> m.displayName).toList(), p.twist().displayName, p.floors(),
                p.boss(), g.state.name(), g.neverBreaks(level.getServer()) ? Long.MAX_VALUE : g.breaksOnPlayDay, g.mouth());
    }

    private static GateRecord record(ServerLevel level, String id) {
        try {
            return GateRegistry.get(level.getServer()).gate(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<GateHooks.GateView> near(ServerLevel level, BlockPos pos, int radius) {
        GateRegistry reg = GateRegistry.get(level.getServer());
        return Gates.near(level, pos, radius).stream().map(i -> reg.gate(i.id())).filter(g -> g != null && g.state != GateState.RELEASED)
                .map(g -> view(level, g)).toList();
    }

    @Override
    public Optional<GateHooks.GateView> get(ServerLevel level, String id) {
        GateRecord g = record(level, id);
        return g == null ? Optional.empty() : Optional.of(view(level, g));
    }

    @Override
    public void markScouted(ServerPlayer player, String id) {
        try {
            Gates.markScouted(player, Integer.parseInt(id));
        } catch (NumberFormatException ignored) {
            // not one of ours
        }
    }

    @Override
    public boolean stabilise(ServerLevel level, String id, int days) {
        GateRecord g = record(level, id);
        if (g == null || g.neverBreaks(level.getServer()) || days <= 0) return false;
        if (!(g.state == GateState.BUILDING || g.state == GateState.READY || g.state == GateState.OPEN || g.state == GateState.ACTIVE)) return false;
        g.breaksOnPlayDay += days;
        // the one-day warning goes out again if the new break day comes round
        if (g.breaksOnPlayDay - PlayDays.current(level.getServer()) > 1) g.warned = false;
        g.trace.add("stabilised +" + days + " play-days on play-day " + PlayDays.current(level.getServer()));
        GateRegistry.get(level.getServer()).setDirty();
        return true;
    }

    @Override
    public boolean inGate(Entity e) {
        return Gates.isInGate(e);
    }

    @Override
    public Optional<String> openScripted(ServerPlayer player, String theme, int rank, BlockPos near, int floors) {
        Theme t = Theme.parse(theme.startsWith("awaken:") ? theme.substring("awaken:".length()) : theme);
        if (t == null) t = Theme.BELL_CRYPT;
        Rank r = Rank.values()[Math.clamp(rank, 0, Rank.values().length - 1)];
        long seed = Hash.mix(player.getUUID().getLeastSignificantBits() ^ player.level().getGameTime());
        int n = floors > 0 ? floors : Curve.floorsFor(r, false);
        // the story's Bell Crypt: one floor asks for the Ritual of the Bell's hall, more for today's three-floor crypt
        GatePlan plan = t == Theme.BELL_CRYPT && r == Rank.E
                ? (floors == 1 ? Gates.ritualPlan(seed) : withFloors(Gates.prologuePlan(seed), n))
                : new GatePlan(seed, GatePlan.CONTENT_VERSION, r, t, Layout.LABYRINTH, List.<Modifier>of(), Twist.NONE, n, t.bossId(),
                        Theme.OVERGROWN_TEMPLE, 0);
        int id = Gates.openScripted(player, plan, near);
        return id < 0 ? Optional.empty() : Optional.of(Integer.toString(id));
    }

    @Override
    public Optional<String> openWandering(ServerPlayer player, BlockPos near, int floors, int lifetimeTicks) {
        int id = Gates.openWandering(player, near, floors, lifetimeTicks);
        return id < 0 ? Optional.empty() : Optional.of(Integer.toString(id));
    }

    @Override
    public void release(ServerLevel level, String id) {
        GateRecord g = record(level, id);
        if (g != null) GateService.release(level.getServer(), g);
    }

    private static GatePlan withFloors(GatePlan p, int floors) {
        return p.withFloors(floors);
    }
}
