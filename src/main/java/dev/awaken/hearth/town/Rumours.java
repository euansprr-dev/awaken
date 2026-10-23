package dev.awaken.hearth.town;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.GateHooks;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/**
 * Mira's rumours: one a play-day, each a Codex entry pointing at a real gate (or, when none is known, a ruin) with a
 * direction and a rough distance. Never invented: if there's nothing out there she says so.
 */
public final class Rumours {
    private Rumours() {}

    public static final int KEEP = 12;
    private static final String[] GATE_FLAVOUR = {
            "It hums at night. Want it?", "Nobody's been back from that one. Probably fine.", "I didn't get close. I got close enough.",
            "The light's the wrong colour. You'll love it.", "There's a smell. Don't ask me what kind."};
    private static final String[] RUIN_FLAVOUR = {
            "Old stone. Older than the town. Older than towns.", "The Precursors built something there and then didn't take it with them.",
            "Teodor will want to hear about it. Bring paper."};

    static void give(ServerPlayer sp, ServerLevel level) {
        HearthPlayer hp = HearthService.player(sp);
        long today = PlayDays.current(level.getServer());
        if (hp.rumourDay == today) return;
        ServerLevel overworld = level.getServer().overworld();
        BlockPos from = sp.blockPosition();
        Rank rank = Progression.rank(sp);
        List<GateHooks.GateView> gates = Town.gatesNear(overworld, from).stream()
                .filter(g -> !"CLEARED".equals(g.state()) && !"RELEASED".equals(g.state()))
                .filter(g -> hp.rumours.stream().noneMatch(r -> r.id().equals("gate/" + g.id())))
                .sorted(Comparator.comparingInt((GateHooks.GateView g) -> Math.abs(g.rank() - rank.ordinal())).thenComparingDouble(g -> g.pos().distSqr(from)))
                .toList();
        HearthPlayer.Rumour rumour = null;
        int r = Math.floorMod((int) today, 5);
        if (!gates.isEmpty()) {
            GateHooks.GateView g = gates.get(0);
            String where = direction(from, g.pos()) + ", about " + roundDistance(from, g.pos()) + " blocks";
            rumour = new HearthPlayer.Rumour("gate/" + g.id(), "A " + Rank.of(g.rank()).label + "-rank light, " + direction(from, g.pos()),
                    "Mira: “" + where + ". " + GATE_FLAVOUR[r] + "” " + (g.omen() == null || g.omen().isEmpty() ? "" : "She says: " + g.omen() + "."), today);
        } else if (dev.awaken.core.api.WorldHooks.landmarkRumour(sp).orElse(null) instanceof dev.awaken.core.api.WorldHooks.Rumour lm) {
            rumour = new HearthPlayer.Rumour(lm.id(), lm.title(), lm.body(), today);
        } else {
            BlockPos found = overworld.findNearestMapStructure(Town.ruinsTag(), from, 48, false);
            final BlockPos ruin = found != null ? found : overworld.findNearestMapStructure(Town.fallbackRuins(), from, 32, false);
            if (ruin != null && hp.rumours.stream().noneMatch(x -> x.id().equals("ruin/" + ruin.toShortString()))) {
                rumour = new HearthPlayer.Rumour("ruin/" + ruin.toShortString(), "Ruins, " + direction(from, ruin),
                        "Mira: “" + direction(from, ruin) + ", about " + roundDistance(from, ruin) + " blocks. " + RUIN_FLAVOUR[r % RUIN_FLAVOUR.length] + "”", today);
            }
        }
        hp.rumourDay = today;
        if (rumour == null) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Mira shrugs"))
                    .body(Component.literal("“Quiet out there. Too quiet. No, actually quiet. Ask me tomorrow.”")).build());
            return;
        }
        hp.rumours.add(rumour);
        while (hp.rumours.size() > KEEP) hp.rumours.remove(0);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(rumour.title()))
                .body(Component.literal(rumour.body())).footer(Component.literal("Added to your Codex · K")).ttl(20 * 12).build());
        HearthViews.refresh(sp);
    }

    /** Eight-way compass direction from {@code a} to {@code b} ("to the northeast"). */
    public static String direction(BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        int i = Math.floorMod((int) Math.round(angle / 45.0), 8);
        return "to the " + names[i];
    }

    static int roundDistance(BlockPos a, BlockPos b) {
        double d = Math.sqrt(a.distSqr(b));
        return (int) (Math.round(d / 50.0) * 50);
    }
}
