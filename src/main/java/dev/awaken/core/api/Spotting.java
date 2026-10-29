package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spotting through the binoculars (SPEC §39.10). A module that owns findable things (creatures, lairs, landmarks, gates,
 * wonders) registers a {@link Source} at init; the binoculars' server side asks each source what this player hasn't
 * found yet within reach, holds the centre of the view on one and then asks its source to find it, by the source's own
 * discovery path (the same reward as going there). Server side only. A source offers nothing the player has already
 * found, never loads a chunk, and re-checks its thing in {@link Source#find} (it may have gone since it was offered).
 */
public final class Spotting {
    private Spotting() {}

    public interface Source {
        /** Whether what it offers moves (creatures: asked at every look) or stands (asked about once a second). */
        boolean moves();

        /** Offers to {@code out} what this player hasn't found within {@code reach} blocks of their eyes. */
        void gather(ServerPlayer p, double reach, Sink out);

        /** The player held the glass on {@code ref}: find it now. Returns whether it was a new find. */
        boolean find(ServerPlayer p, Object ref);
    }

    public interface Sink {
        /** Something standing, by its bounds; {@code key} names it within its source, the same at every gather. */
        void place(long key, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Object ref);

        /** Something moving: its bounds are read from the entity at each look. */
        void entity(Entity e, Object ref);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source s) {
        SOURCES.add(s);
    }

    public static List<Source> sources() {
        return SOURCES;
    }
}
