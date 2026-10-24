package dev.awaken.gates;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.gates.run.Runs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Until real parties exist (the city's guilds replace this through {@link PartyHooks#install}), a player's party is
 * everyone inside the gate run they are in, plus everyone who shares a Hearth with them (SPEC "Together"). Alone, it is
 * a party of one. Gates and rewards ask {@link PartyHooks} only, never this class.
 */
final class GateParty implements PartyHooks.Access {
    static void install() {
        PartyHooks.install(new GateParty());
    }

    @Override
    public List<UUID> members(Player p) {
        if (!(p instanceof ServerPlayer sp)) return List.of(p.getUUID());
        Set<UUID> out = new LinkedHashSet<>();
        out.add(sp.getUUID());
        GateRecord g = Runs.gateOf(sp);
        if (g != null) out.addAll(g.inside);
        out.addAll(HearthHooks.household(sp));
        return new ArrayList<>(out);
    }

    @Override
    public boolean sameParty(Player a, Player b) {
        if (a.getUUID().equals(b.getUUID())) return true;
        if (!(a instanceof ServerPlayer sa) || !(b instanceof ServerPlayer sb)) return false;
        GateRecord ga = Runs.gateOf(sa);
        if (ga != null && ga.inside.contains(sb.getUUID())) return true;
        return HearthHooks.shareHearth(sa, sb.getUUID());
    }
}
