package dev.awaken.hearth.settle;

import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.Household;
import dev.awaken.village.api.Troubles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The people a player brought home from a village's trouble (W4's Taken and Gone Missing, {@link Troubles#rescued}), as
 * people who could settle at their Hearth (PLAN 4.7: "you know them ... rescued them"). Grown-ups only: a child goes
 * back to their family. They keep their trade (a miner wants your Mine head, a farmer or shepherd your field or pen, a
 * smith your Forge; anyone else finds work at move-in) and the face they wore in their village.
 */
public final class Rescued {
    private Rescued() {}

    static final String PREFIX = "rescued:";

    /** Who this player brought home who could settle with them. */
    static List<Folk.Person> of(ServerPlayer player) {
        List<Troubles.Rescued> all = Troubles.rescued(player);
        if (all.isEmpty()) return List.of();
        List<Folk.Person> out = new ArrayList<>();
        for (Troubles.Rescued r : all) {
            if (r.settled() || r.age() < 1) continue;
            String name = r.name().trim();
            int sp = name.indexOf(' ');
            String given = sp < 0 ? name : name.substring(0, sp), family = sp < 0 ? "" : name.substring(sp + 1);
            String role = switch (r.trade()) {
                case "miner" -> "miner";
                case "farmer", "shepherd" -> "farmer";
                case "smith" -> "smith";
                default -> "any";
            };
            String trade = role.equals("any") ? "resident" : r.trade();
            // the face they wore at home, read while their village runs (else one of the city's of their trade)
            String face = "";
            boolean slim = false;
            for (Settlement s : Settlements.all()) {
                if (!s.key().equals("village:" + r.village())) continue;
                Settlement.Look look = s.look(r.rec());
                String path = look.texture().getPath();
                if (path.startsWith("textures/entity/") && path.endsWith(".png")) {
                    face = path.substring("textures/entity/".length(), path.length() - 4);
                    slim = look.slim();
                }
            }
            out.add(new Folk.Person(PREFIX + r.village() + ":" + r.rec(), given, family, role, trade, face, slim, r.villageName()));
        }
        return out;
    }

    /**
     * One of them moved into this Hearth: their village's history says so, once (through the household member who
     * brought them home; a party member who also has them on their list keeps them there).
     */
    static void settled(ServerLevel level, HearthRecord h, Folk.Person who, String hearthName) {
        if (!who.id().startsWith(PREFIX)) return;
        String rest = who.id().substring(PREFIX.length());
        int cut = rest.lastIndexOf(':');
        if (cut < 0) return;
        String village = rest.substring(0, cut);
        int rec;
        try {
            rec = Integer.parseInt(rest.substring(cut + 1));
        } catch (NumberFormatException e) {
            return;
        }
        for (ServerPlayer p : Household.online(level.getServer(), h)) {
            for (Troubles.Rescued r : Troubles.rescued(p)) {
                if (r.settled() || !r.village().equals(village) || r.rec() != rec) continue;
                Troubles.settle(p, r, hearthName);
                return;
            }
        }
    }
}
