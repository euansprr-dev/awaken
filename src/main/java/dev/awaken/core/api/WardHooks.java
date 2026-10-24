package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Precursor wards (SPEC §17 "The Wilds"): around a Precursor site a block a player places crumbles after three seconds
 * and falls back to them, and the site's own walls can't be broken. A site gets its ward one of two ways: a structure
 * listed in {@code #awaken:warded} (or {@code #awaken:warded_sky}, whose ward reaches further down) gets one from the
 * wilds module with no code, so a new structure only joins the tag; a site placed by code (a hero tree's cache)
 * registers a {@link Source}. The rules themselves (who, where, the Hearth's exemption) live in the wilds module.
 */
public final class WardHooks {
    private WardHooks() {}

    /** One ward: the site's stable id, what kind of site it guards ("ruin/vault", "spire"), and the box it covers. */
    public record Ward(String id, String kind, BoundingBox box) {
        public boolean contains(BlockPos pos) {
            return box.isInside(pos);
        }
    }

    /**
     * The wards of one kind of site: reports every ward whose box holds {@code pos}. Called on block events and a slow
     * per-player check, never per tick, so a few map lookups are fine; no structure searches and no chunk loads.
     */
    @FunctionalInterface
    public interface Source {
        void wardsAt(ServerLevel level, BlockPos pos, Consumer<Ward> out);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** GameTests take their stand-in sources back out (try/finally). */
    public static void unregister(Source source) {
        SOURCES.remove(source);
    }

    /** The first ward that holds {@code pos}, whatever the Hearth or the config say (a failing source is skipped). */
    public static Optional<Ward> at(ServerLevel level, BlockPos pos) {
        Ward[] found = new Ward[1];
        for (Source s : SOURCES) {
            try {
                s.wardsAt(level, pos, w -> {
                    if (found[0] == null) found[0] = w;
                });
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[wards] a ward source failed: {}", e.toString());
            }
            if (found[0] != null) return Optional.of(found[0]);
        }
        return Optional.empty();
    }
}
