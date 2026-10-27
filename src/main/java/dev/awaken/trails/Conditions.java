package dev.awaken.trails;

import dev.awaken.core.api.TrailsApi;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The tokens a trail's {@code opens}, {@code done} and {@code show} are written in (docs/trails/PLAN.md §3.3): the
 * Requirements tokens (level:, rank:, counter:, hearth, power:...), two of our own (holds:, count:) and the families other
 * modules add through {@link TrailsApi#condition}. An unknown token is never met.
 */
public final class Conditions {
    private Conditions() {}

    public static boolean allMet(ServerPlayer p, List<String> tokens) {
        for (String t : tokens) if (!met(p, t)) return false;
        return true;
    }

    public static boolean met(ServerPlayer p, String token) {
        int c = token.indexOf(':');
        String family = c < 0 ? token : token.substring(0, c), rest = c < 0 ? "" : token.substring(c + 1);
        switch (family) {
            case "holds" -> {
                return holds(p, rest);
            }
            case "count" -> {
                int at = rest.lastIndexOf(':');
                if (at < 0) return false;
                try {
                    return Progression.profile(p).counter(rest.substring(0, at)) >= Long.parseLong(rest.substring(at + 1));
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            default -> {
                var custom = TrailsApi.conditionFor(family);
                return custom != null ? custom.test(p, rest) : Requirements.met(p, token);
            }
        }
    }

    /** Whether a token is well formed and has an owner (the content test checks every trail with this). */
    public static boolean known(String token) {
        int c = token.indexOf(':');
        String family = c < 0 ? token : token.substring(0, c), rest = c < 0 ? "" : token.substring(c + 1);
        if (family.equals("holds")) return Identifier.tryParse(rest) != null;
        if (family.equals("count")) return rest.lastIndexOf(':') > 0;
        if (TrailsApi.conditionFor(family) != null) return true;
        return Requirements.known(token);
    }

    private static boolean holds(ServerPlayer p, String id) {
        Identifier want = Identifier.tryParse(id);
        if (want == null) return false;
        var item = BuiltInRegistries.ITEM.getValue(want);
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && s.is(item)) return true;
        return false;
    }
}
