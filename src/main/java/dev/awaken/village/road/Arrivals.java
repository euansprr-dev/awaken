package dev.awaken.village.road;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * A player who changes level stands on nothing the old one held (SPEC §28.9, Cost; Euan's 42 s freeze in a D-rank trial,
 * 2026-10-28). Vanilla keeps an entity's supporting block ({@code mainSupportingBlockPos}) through a teleport to another
 * dimension, and the player's first movement tick there reads it ({@code getOnPos}) in the new level: back from a trial
 * cell at 35 64 42, the overworld was asked for chunk 2,2, generated it on the server thread, and waited for whatever
 * held that level's world generation (a lattice chunk's highroad, 34 s). Going in, a gate or trial cell's level was asked
 * for a chunk at the player's overworld spot the same way. Forgetting the block when the level changes makes that tick
 * read where the player now stands, the chunk it is in.
 */
public final class Arrivals {
    private Arrivals() {}

    /** Before anything else hears of the change (highest priority): the old level's block is forgotten. */
    public static void changed(PlayerEvent.PlayerChangedDimensionEvent e) {
        e.getEntity().mainSupportingBlockPos = Optional.empty();
    }
}
