package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import dev.awaken.core.api.TipHooks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The Ascent's tips (Carillon's tip system, core.api.TipHooks; text in assets/awaken/tips/ascent/): each tier's tip is
 * offered as the tier is given (the Vow's rope, the Leaper's trial, a sky core's wings, flight) and counted done each
 * time it's used; the free-run's is the client's own (body.client.FreeRun). Nothing shows until the tip system is in.
 */
public final class AscentTips {
    private AscentTips() {}

    public static final Identifier ROPE = Awaken.id("ascent/rope"), LEAPER = Awaken.id("ascent/leaper"), WINGS = Awaken.id("ascent/wings"),
            FLIGHT = Awaken.id("ascent/flight"), FREERUN = Awaken.id("ascent/freerun"), SOVEREIGN = Awaken.id("ascent/sovereign");

    static @Nullable Identifier of(Ascent.Tier tier) {
        return switch (tier) {
            case TETHER -> ROPE;
            case LEAPER -> LEAPER;
            case WINGS -> WINGS;
            case SKYBORN -> FLIGHT;
            case SOVEREIGN -> SOVEREIGN;
            default -> null;
        };
    }

    static void register() {
        Ascent.onGranted((p, tier) -> {
            Identifier tip = of(tier);
            if (tip != null) TipHooks.offer(p, tip);
        });
    }

    /** A state begun: the tip that teaches it is done once more. */
    static void used(ServerPlayer p, AscentState s) {
        Identifier tip = switch (s) {
            case TETHER, ZIP -> ROPE;
            case GLIDE -> WINGS;
            case SOAR -> FLIGHT;
            default -> null;
        };
        if (tip != null) TipHooks.done(p, tip);
    }
}
