package dev.awaken.citizens.api;

import net.minecraft.core.BlockPos;

/**
 * A settlement to run on the people engine (SPEC §28.4): a stable key (its saved state is found by it on every start;
 * W3 keys villages by their planned site), the name people use for it, its origin (the roster's offsets and the street
 * level), its radius (what counts as inside, and how near a player must be for it to wake), where its people come from,
 * and whether the city's daily events (a burglary most nights, pickpockets, fires, carts) are planned for it: off for
 * villages until W4's director decides their troubles; the events themselves work in any settlement.
 */
public record SettlementSpec(String key, String name, BlockPos origin, int radius, RosterSource source, boolean plannedEvents) {
    public SettlementSpec {
        if (key.isEmpty() || key.equals(Settlements.CARILLON_KEY)) throw new IllegalArgumentException("a settlement needs its own key, not '" + key + "'");
        if (radius <= 0) throw new IllegalArgumentException("radius " + radius);
        origin = origin.immutable();
    }

    public SettlementSpec(String key, String name, BlockPos origin, int radius, RosterSource source) {
        this(key, name, origin, radius, source, false);
    }
}
