package dev.awaken.together;

import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.Household;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A home for two (SPEC §70.1): sharing a home is a card, never a command. A party member who has a home of their own
 * can offer it from the band; and when one of a party founds a home with another member standing by who has none, the
 * offer is made for them, once. The card and the joining are the Hearth's own ({@link Household#invite}).
 */
final class HomeForTwo {
    private HomeForTwo() {}

    /** A member this close to a new home's Anchor is asked whether it's theirs too. */
    static final double BESIDE = 24;
    /** Offers already made this session (owner, then the one asked): never asked twice unbidden. */
    private static final Set<String> OFFERED = new HashSet<>();

    static void forget() {
        OFFERED.clear();
    }

    private static Optional<Hearth.HearthInfo> own(ServerPlayer p) {
        for (Hearth.HearthInfo h : Hearth.hearthsOf(p)) if (h.owner().equals(p.getUUID())) return Optional.of(h);
        return Optional.empty();
    }

    /** The band's button: {@code owner} offers their home to {@code other}. */
    static void share(ServerPlayer owner, ServerPlayer other) {
        Optional<String> refused = Household.invite(owner, other);
        refused.ifPresent(why -> Signal.toast(owner, Component.literal(why)));
        OFFERED.add(owner.getUUID() + ":" + other.getUUID());
    }

    /** Every two seconds: a party member with a home, and another beside its Anchor with none, who hasn't been asked. */
    static void tick(MinecraftServer s) {
        if (!TogetherConfig.party() || s.getTickCount() % 40 != 0) return;
        PartyData data = PartyData.get(s);
        for (PartyData.Party party : data.parties) {
            for (UUID id : party.members) {
                ServerPlayer owner = s.getPlayerList().getPlayer(id);
                Optional<Hearth.HearthInfo> home = owner == null ? Optional.empty() : own(owner);
                if (home.isEmpty()) continue;
                for (UUID other : party.members) {
                    ServerPlayer m = other.equals(id) ? null : s.getPlayerList().getPlayer(other);
                    if (m == null || m.level() != owner.level() || !Hearth.hearthsOf(m).isEmpty()) continue;
                    if (m.blockPosition().distSqr(home.get().anchor()) > BESIDE * BESIDE) continue;
                    if (OFFERED.add(id + ":" + other)) Household.invite(owner, m);
                }
            }
        }
    }
}
