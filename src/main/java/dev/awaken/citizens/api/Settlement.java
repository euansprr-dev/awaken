package dev.awaken.citizens.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * A settlement as other modules see it (SPEC §28.4): Carillon is id 0, the others are what W3 registers. Read on the
 * server thread; nothing here allocates per call except the station lists.
 */
public interface Settlement {
    int id();

    String key();

    String name();

    BlockPos origin();

    /** Whether a position belongs to it (Carillon: inside the wall; others: within their radius). */
    boolean inside(BlockPos pos);

    /** Its citizens (event actors not counted). */
    int people();

    /** Its people with a body near a player now (out of the world pool all settlements share). */
    int bodies();

    /** Ticking its day now (Carillon always; others while a player is near). */
    boolean awake();

    /**
     * How it knows a player: {@code RenownHooks.STRANGER}, {@code FAMILIAR} or {@code KNOWN} (its own memory: each
     * citizen's familiarity, and how many of its people know them; a small place needs fewer).
     */
    int standing(Player player);

    /**
     * Where its places of a kind are (world positions, feet): a place kind ({@code door}, {@code anvil}, {@code wash},
     * {@code stall_keeper}, {@code bell}, {@code tavern_out}, ...; Roster.PLACE_KINDS) or a lot tag ({@code house},
     * {@code fire}, ...: the lot's door).
     */
    List<BlockPos> stations(String kind);

    /** What people call the part of it a position lies in (its own district names). */
    String district(BlockPos pos);
}
