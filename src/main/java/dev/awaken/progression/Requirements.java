package dev.awaken.progression;

import dev.awaken.Awaken;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * What a Directive or Regimen drill needs before it may be offered ({@code "requires": [...]} in its JSON). A
 * suggestion the player can't act on is never offered (plan v2 §4.6). Tokens:
 * <pre>
 * power:awaken:dash     owns that power            movement   owns a movement power
 * dash                  owns a dash-like power     active     owns an active power
 * forces                owns a power with a force  family:energy  owns a power of that family
 * unlock:awaken:veil    a level-road flag is unlocked (the System has taught it)
 * level:8               level 8 or more            hearth     has a Hearth Anchor
 * counter:awaken:x      counter x is at least 1
 * rank:C                rank C or higher           path       has chosen a Path (Second Awakening)
 * </pre>
 * Unknown tokens count as unmet (and are logged once), so a typo hides a directive rather than offering the impossible.
 */
public final class Requirements {
    private Requirements() {}

    /** Installed by the powers module; answers the power tokens above. Without powers, power tokens are unmet. */
    public static BiPredicate<ServerPlayer, String> powerCheck = (p, token) -> false;

    private static final Set<String> WARNED = new HashSet<>();

    public static boolean met(ServerPlayer player, List<String> requires) {
        for (String r : requires) if (!met(player, r)) return false;
        return true;
    }

    public static boolean met(ServerPlayer player, String token) {
        if (isPowerToken(token)) return powerCheck.test(player, token);
        Profile p = Progression.profile(player);
        try {
            if (token.startsWith("unlock:")) return LevelRoad.hasFlag(p, token.substring("unlock:".length()));
            if (token.startsWith("level:")) return p.level() >= Integer.parseInt(token.substring("level:".length()));
            if (token.startsWith("counter:")) return p.counter(token.substring("counter:".length())) >= 1;
            if (token.equals("hearth")) return dev.awaken.core.api.HearthHooks.hearthOf(player).isPresent();
            if (token.equals("path")) return !p.path().isEmpty();
            if (token.startsWith("rank:")) return p.rank().ordinal() >= Rank.valueOf(token.substring("rank:".length())).ordinal();
        } catch (RuntimeException bad) {
            warn(token);
            return false;
        }
        warn(token);
        return false;
    }

    static boolean isPowerToken(String token) {
        return token.startsWith("power:") || token.startsWith("family:") || token.equals("movement") || token.equals("dash")
                || token.equals("active") || token.equals("forces");
    }

    /** Whether a token is well formed (content tests check every directive and drill with this). */
    public static boolean known(String token) {
        if (isPowerToken(token)) return !token.endsWith(":");
        if (token.equals("hearth") || token.equals("path")) return true;
        if (token.startsWith("rank:")) {
            try {
                Rank.valueOf(token.substring(5));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (token.startsWith("level:")) {
            try {
                Integer.parseInt(token.substring(6));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return (token.startsWith("unlock:") || token.startsWith("counter:")) && token.indexOf(':', token.indexOf(':') + 1) > 0;
    }

    private static void warn(String token) {
        if (WARNED.add(token)) Awaken.LOG.warn("[progression] unknown requirement '{}': treated as unmet", token);
    }
}
