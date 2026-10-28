package dev.awaken.delve.cave;

import dev.awaken.Awaken;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.TrailsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The trail "The Delver's Pick" (SPEC §34.4): its place {@code adit}, the nearest Delver adit to your home (to you, with
 * no home), where the pick waits in the stone. The search runs off the server thread as the Hearth's does; until it
 * answers there's no place. A home with no adit near gets the pick handed over, as the Hearth's metal card does.
 */
public final class PickTrail {
    private PickTrail() {}

    public static final String PLACE = "adit";
    /** Without a home the search follows you: it's asked again once you're this far from where it last was. */
    static final int MOVED = 96;

    private record Asked(BlockPos from, boolean home, CompletableFuture<Optional<BlockPos>> adit) {}

    private static final Map<UUID, Asked> ASKED = new HashMap<>();

    static void register() {
        TrailsApi.place(PLACE, PickTrail::adit);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> ASKED.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> ASKED.clear());
    }

    /** The adit's mouth, once the search has answered; null while it runs, off the overworld, or with none in reach. */
    static @Nullable BlockPos adit(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return null;
        Optional<BlockPos> home = HearthHooks.hearthOf(p);
        BlockPos from = home.orElse(p.blockPosition());
        Asked asked = ASKED.get(p.getUUID());
        boolean stale = asked == null || asked.home() != home.isPresent()
                || (home.isPresent() ? !asked.from().equals(from) : asked.from().distSqr(from) > (double) MOVED * MOVED);
        if (stale) {
            CompletableFuture<Optional<BlockPos>> f = CompletableFuture.supplyAsync(() -> find(level, from), Util.backgroundExecutor())
                    .exceptionally(t -> {
                        Awaken.LOG.warn("[delve] the pick trail's adit search failed", t);
                        return Optional.empty();
                    });
            asked = new Asked(from, home.isPresent(), f);
            ASKED.put(p.getUUID(), asked);
        }
        Optional<BlockPos> got = asked.adit().getNow(null);
        if (got == null) return null;
        // no adit near home: no pick in a stone to find, so the Hearth hands it over (once; it checks)
        if (got.isEmpty() && asked.home()) dev.awaken.delve.WakingPick.handOver(p);
        return got.orElse(null);
    }

    /** The nearest adit within the Hearth's search radius of {@code from} (pure: the seed's plan). */
    static Optional<BlockPos> find(ServerLevel level, BlockPos from) {
        Spec s = CaveLocate.nearest(level, from, CaveKind.MOUTH, MouthPointer.RADIUS, false, m -> m.p(Mouth.FORM) == Mouth.ADIT);
        return s == null ? Optional.empty() : Optional.of(new BlockPos(s.x(), s.y(), s.z()));
    }
}
