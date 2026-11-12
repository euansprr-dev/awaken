package dev.awaken.together;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.gates.Gates;
import dev.awaken.powers.Powers;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The party (SPEC §70.1): who you chose to play with. Formed by a card the other answers, by walking the opening
 * together, or by sharing a home (joining a household joins its party while there is room; leaving the party doesn't
 * leave the home, and someone who left on purpose isn't put back). Everything else asks {@link PartyHooks}; this is the
 * rule behind it: your party, plus, as before, anyone inside your gate run. Server thread only.
 */
final class PartyService implements PartyHooks.Access {
    static final int CARD_TICKS = 20 * 30;
    /** Asking someone to travel together needs them this near, or under your roof. */
    static final double NEAR = 48;

    private record Asked(UUID who, long until) {}

    /** Cards out now: the action a client may answer, to whom it was put and until when (a client can't answer one it wasn't asked). */
    private static final Map<String, Asked> ASKED = new HashMap<>();

    /** The rule that stood before this one (gates': the run, the household): what a party is with the switch off. */
    private final PartyHooks.Access before;

    PartyService(PartyHooks.Access before) {
        this.before = before;
    }

    static void forget() {
        ASKED.clear();
    }

    // ---- the rule -----------------------------------------------------------------------------------------------------

    @Override
    public List<UUID> members(Player p) {
        if (!(p instanceof ServerPlayer sp)) return List.of(p.getUUID());
        if (!TogetherConfig.party()) return before.members(p);
        Set<UUID> out = new LinkedHashSet<>();
        out.add(sp.getUUID());
        PartyData.Party party = settled(sp);
        if (party != null) out.addAll(party.members);
        out.addAll(Gates.inside(sp));
        return new ArrayList<>(out);
    }

    @Override
    public boolean sameParty(Player a, Player b) {
        if (a.getUUID().equals(b.getUUID())) return true;
        if (!(a instanceof ServerPlayer sa) || !(b instanceof ServerPlayer)) return false;
        if (!TogetherConfig.party()) return before.sameParty(a, b);
        PartyData.Party party = settled(sa);
        if (party != null && party.members.contains(b.getUUID())) return true;
        return Gates.inside(sa).contains(b.getUUID());
    }

    /** The player's party, after a home they share has had its say: a housemate's party takes them in while it has room. */
    static PartyData.@Nullable Party settled(ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        PartyData data = PartyData.get(server);
        PartyData.Party party = data.of(sp.getUUID());
        if (party != null || data.apart.contains(sp.getUUID())) return party;
        for (UUID mate : HearthHooks.household(sp)) {
            if (mate.equals(sp.getUUID()) || data.apart.contains(mate)) continue;
            if (data.join(sp.getUUID(), mate, PlayDays.current(server))) {
                DirectorLog.log(sp, "together", "a shared home made a party with " + mate);
                return data.of(sp.getUUID());
            }
        }
        return null;
    }

    /** The party's members online, this player first; just them when they have none. */
    static List<ServerPlayer> online(ServerPlayer sp) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(sp);
        PartyData.Party party = settled(sp);
        if (party == null) return out;
        for (UUID id : party.members) {
            ServerPlayer m = id.equals(sp.getUUID()) ? null : sp.level().getServer().getPlayerList().getPlayer(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    // ---- asking -------------------------------------------------------------------------------------------------------

    private static Component line(String s) {
        return Component.literal(s);
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().name();
    }

    /** What stands in the way of these two travelling together, in words for the asker, or null. */
    static @Nullable String whyNot(ServerPlayer asker, ServerPlayer other) {
        if (asker == other) return "It takes two people.";
        if (!Progression.profile(asker).awakened() || !Progression.profile(other).awakened()) return "You both need to be awakened for that.";
        PartyData data = PartyData.get(asker.level().getServer());
        PartyData.Party mine = data.of(asker.getUUID()), theirs = data.of(other.getUUID());
        if (mine != null && mine == theirs) return "You're already travelling together.";
        if (mine != null && theirs != null) return "You're each travelling with other people already. One of you has to leave first.";
        PartyData.Party into = mine != null ? mine : theirs;
        if (into != null && into.members.size() >= PartyData.MAX) return "That party is full. It holds four.";
        boolean near = asker.level() == other.level() && asker.distanceToSqr(other) <= NEAR * NEAR;
        if (!near && !HearthHooks.shareHearth(asker, other.getUUID())) return "You need to be within " + (int) NEAR + " blocks of each other.";
        if (Powers.inCombat(asker) || Powers.inCombat(other)) return "Not in the middle of a fight. Get clear of it first.";
        return null;
    }

    /** {@code asker} wants to travel with {@code other}: a card for the other, open 30 seconds. */
    static void ask(ServerPlayer asker, ServerPlayer other) {
        String no = whyNot(asker, other);
        if (no != null) {
            Signal.toast(asker, line(no));
            return;
        }
        long now = asker.level().getServer().getTickCount();
        String theirs = "together:join:" + other.getUUID() + ":" + asker.getUUID();
        Asked a = ASKED.get(theirs);
        if (a != null && a.who().equals(asker.getUUID()) && a.until() > now) { // they had asked for this very thing: both want it
            ASKED.remove(theirs);
            Signal.withdraw(asker, theirs);
            join(asker, other);
            return;
        }
        String action = "together:join:" + asker.getUUID() + ":" + other.getUUID();
        Asked mine = ASKED.get(action);
        if (mine != null && mine.until() > now) {
            Signal.toast(asker, line("Still waiting for " + name(other) + " to answer."));
            return;
        }
        ASKED.put(action, new Asked(other.getUUID(), now + CARD_TICKS));
        Signal.send(other, SignalWindow.builder(SignalWindow.Kind.NOTICE, line(name(asker) + " wants to travel together"))
                .body(line("You'll see each other on your screens and your maps, share what you fight near each other, and go through gates and Reassessments side by side. Either of you can leave whenever you like."))
                .choice(action, "Yes", "Not now").priority(5).ttl(CARD_TICKS).build());
        Signal.toast(asker, line("Asked " + name(other) + ". They have 30 seconds to answer."));
    }

    static void onAnswer(ServerPlayer p, String action, boolean accepted) {
        MinecraftServer s = p.level().getServer();
        Asked a = ASKED.get(action);
        if (a == null || !a.who().equals(p.getUUID()) || a.until() <= s.getTickCount()) return;
        ASKED.remove(action);
        String[] part = action.split(":");
        try {
            if (!"join".equals(part[1])) return;
            ServerPlayer asker = s.getPlayerList().getPlayer(UUID.fromString(part[2]));
            if (asker == null) {
                Signal.toast(p, line("They've gone."));
                return;
            }
            if (!accepted) {
                Signal.toast(asker, line(name(p) + " said not now."));
                return;
            }
            String no = whyNot(asker, p);
            if (no != null) {
                Signal.toast(p, line(no));
                Signal.toast(asker, line(no));
                return;
            }
            join(asker, p);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            dev.awaken.Awaken.LOG.warn("[together] a bad answer: {}", action);
        }
    }

    /** Puts the two in one party now and tells them. False when the parties they are in can't take it. */
    static boolean join(ServerPlayer a, ServerPlayer b) {
        MinecraftServer s = a.level().getServer();
        PartyData data = PartyData.get(s);
        boolean were = data.of(a.getUUID()) != null && data.of(a.getUUID()) == data.of(b.getUUID());
        if (!data.join(a.getUUID(), b.getUUID(), PlayDays.current(s))) return false;
        if (!were) {
            Signal.toast(a, line("You and " + name(b) + " are travelling together now."));
            Signal.toast(b, line("You and " + name(a) + " are travelling together now."));
            PartyData.Party made = data.of(a.getUUID());
            if (made != null && SharedXp.of(made) != SharedXp.OWN) {
                String shared = "Shared XP is on " + SharedXp.label(SharedXp.of(made)) + (SharedXp.of(made) == SharedXp.EQUAL
                        ? ": what either of you earns, you both get." : ": you each get half of what the other earns.") + " The band's Together section changes it.";
                Signal.toast(a, line(shared));
                Signal.toast(b, line(shared));
            }
            a.level().playSound(null, a.getX(), a.getY(), a.getZ(), TogetherContent.JOIN, SoundSource.PLAYERS, 0.7F, 1F);
            DirectorLog.log(a, "together", "party with " + name(b));
        }
        TogetherNet.dirty();
        return true;
    }

    /** Leaves the party on purpose. A party of one is no party. */
    static void leave(ServerPlayer p) {
        MinecraftServer s = p.level().getServer();
        PartyData data = PartyData.get(s);
        if (data.of(p.getUUID()) == null) {
            Signal.toast(p, line("You aren't travelling with anyone."));
            return;
        }
        if (Powers.inCombat(p)) {
            Signal.toast(p, line("Not in the middle of a fight. Get clear of it first."));
            return;
        }
        List<UUID> rest = data.leave(p.getUUID(), true);
        Signal.toast(p, line("You've left the party."));
        for (UUID id : rest) {
            ServerPlayer m = s.getPlayerList().getPlayer(id);
            if (m != null) Signal.toast(m, line(name(p) + " has left the party."));
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), TogetherContent.LEAVE, SoundSource.PLAYERS, 0.7F, 1F);
        DirectorLog.log(p, "together", "left the party");
        TogetherNet.dirty();
    }

    // ---- the opening --------------------------------------------------------------------------------------------------

    /** Players who walk the opening together are a party from then on: nothing to accept (once a second). */
    static void openingTogether(MinecraftServer s) {
        if (!TogetherConfig.party()) return;
        ServerPlayer first = null;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.isSpectator() || !OpeningHooks.inOpening(p)) continue;
            if (first == null) {
                first = p;
                continue;
            }
            PartyData data = PartyData.get(s);
            if (data.of(p.getUUID()) == null && !data.apart.contains(p.getUUID())
                    && data.join(first.getUUID(), p.getUUID(), PlayDays.current(s))) TogetherNet.dirty();
        }
    }
}
