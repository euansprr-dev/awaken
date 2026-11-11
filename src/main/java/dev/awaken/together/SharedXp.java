package dev.awaken.together;

import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A party earns as one (SPEC §70.8). Whatever one member is paid for fighting, mining, building or a gate, the others
 * who are online get too, wherever they are: all of it (Equal), a share of it (Half), or none (Each their own). The
 * party chooses on the band; a new party starts on the world's setting.
 *
 * <p>Nothing is paid twice. What a member was paid themselves for the same thing in the same tick (a kill both stood
 * near, a floor both cleared) is taken off what they are passed, so two who play side by side earn what they always
 * did, and two who play apart each earn what both did. Firsts stay your own: discoveries, the story, Truths and
 * commands are never passed on. A share is paid as it was earned (no second scale or perk) and is never shared on.
 */
final class SharedXp {
    private SharedXp() {}

    static final int OWN = 0, HALF = 1, EQUAL = 2;

    /** What is passed on: the repeatable work of playing. Firsts and the story are each player's own. */
    private static final Set<Progression.XpSource> SHARED = EnumSet.of(Progression.XpSource.GATE_KILL, Progression.XpSource.GATE_FLOOR,
            Progression.XpSource.GATE_BOSS, Progression.XpSource.HOME, Progression.XpSource.WORLD, Progression.XpSource.DESCENT,
            Progression.XpSource.ANOMALY, Progression.XpSource.REGIMEN, Progression.XpSource.DIRECTIVE);

    private record Grant(UUID who, double xp, Progression.XpSource source, @Nullable String key) {}

    /** This tick's grants, settled when the tick ends (so what two members earned from one thing can be told apart from a share owed). */
    private static final List<Grant> TICK = new ArrayList<>();

    static void forget() {
        TICK.clear();
    }

    static String label(int share) {
        return share == EQUAL ? "Equal" : share == HALF ? "Half" : "Each their own";
    }

    /** The rule a party plays by now. */
    static int of(PartyData.Party party) {
        return party.share < OWN || party.share > EQUAL ? TogetherConfig.share() : party.share;
    }

    static double part(int share) {
        return share == EQUAL ? 1.0 : share == HALF ? TogetherConfig.sharePart() : 0.0;
    }

    /** Every grant (after its own scale): kept until the tick ends. */
    static void granted(ServerPlayer p, double xp, Progression.XpSource source, @Nullable String worldKey) {
        if (xp > 0 && SHARED.contains(source) && TogetherConfig.party()) TICK.add(new Grant(p.getUUID(), xp, source, worldKey));
    }

    /** The end of a tick: each party's members are passed what the others earned and they did not. */
    static void settle(MinecraftServer s) {
        if (TICK.isEmpty()) return;
        List<Grant> grants = new ArrayList<>(TICK);
        TICK.clear();
        PartyData data = PartyData.get(s);
        // by party, by thing, by member: what each was paid themselves this tick
        Map<PartyData.Party, Map<String, Map<UUID, Double>>> own = new HashMap<>();
        Map<String, Grant> sample = new HashMap<>();
        for (Grant g : grants) {
            PartyData.Party party = data.of(g.who());
            if (party == null || part(of(party)) <= 0) continue;
            String thing = g.source().name() + "/" + (g.key() == null ? "" : g.key());
            own.computeIfAbsent(party, k -> new HashMap<>()).computeIfAbsent(thing, k -> new HashMap<>()).merge(g.who(), g.xp(), Double::sum);
            sample.putIfAbsent(thing, g);
        }
        for (var byParty : own.entrySet()) {
            PartyData.Party party = byParty.getKey();
            double part = part(of(party));
            for (var byThing : byParty.getValue().entrySet()) {
                Grant like = sample.get(byThing.getKey());
                Map<UUID, Double> paid = byThing.getValue();
                for (UUID id : party.members) {
                    ServerPlayer to = s.getPlayerList().getPlayer(id);
                    if (to == null) continue;
                    double best = 0;
                    for (var e : paid.entrySet()) if (!e.getKey().equals(id)) best = Math.max(best, e.getValue());
                    double due = part * best - paid.getOrDefault(id, 0.0);
                    if (due > 1e-6) Progression.grantShared(to, due, like.source(), like.key());
                }
            }
        }
    }

    /** A member changes the party's rule from the band; everyone in it is told in a sentence. */
    static void set(ServerPlayer p, int share) {
        PartyData data = PartyData.get(p.level().getServer());
        PartyData.Party party = data.of(p.getUUID());
        if (party == null) {
            Signal.toast(p, Component.literal("Shared XP is for a party. Ask someone to travel together first."));
            return;
        }
        share = Math.max(OWN, Math.min(EQUAL, share));
        if (of(party) == share && party.share == share) return;
        party.share = share;
        data.setDirty();
        String what = share == EQUAL ? "Equal: what any of you earns, you all get."
                : share == HALF ? "Half: each of you gets half of what the others earn."
                : "Each their own: nobody's XP is passed on.";
        for (ServerPlayer m : PartyService.online(p)) {
            Signal.toast(m, Component.literal((m == p ? "You" : p.getGameProfile().name()) + " set the party's shared XP to " + what));
        }
        DirectorLog.log(p, "together", "shared XP set to " + label(share));
        TogetherNet.dirty();
    }
}
