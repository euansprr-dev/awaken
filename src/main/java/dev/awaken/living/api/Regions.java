package dev.awaken.living.api;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.hearth.Hearth;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;

import java.util.UUID;

/**
 * The region map (SPEC §21.2): the overworld as a patchwork of named regions, each with its own level span. Creatures
 * take their level from where they spawn, never from the player who is near. Server only; thread-safe.
 */
public final class Regions {
    private Regions() {}

    /** A region as other modules see it. {@code low}..{@code high} is its level span, edge to heart. */
    public record Region(long id, String name, int low, int high, int heartX, int heartZ, boolean commons) {}

    /** What a creature is for, and the levels it adds to its place's (SPEC §21.2). */
    public enum Role {
        AMBIENT(-2), NEUTRAL(0), HUNTER(1), ELITE(2), CAMP(3), BEAST(3);
        public final int offset;

        Role(int offset) {
            this.offset = offset;
        }
    }

    /** Hearths calm the land this far beyond their radius. */
    public static final int HEARTH_CALM = 96;
    /** The deep dark is harder under any region. */
    public static final int DEEP_DARK = 10;

    /** Whether regions apply here (the overworld only; the Nether and the End keep their vanilla danger). */
    public static boolean on(Level level) {
        return level instanceof ServerLevel && level.dimension() == Level.OVERWORLD;
    }

    public static int levelAt(ServerLevel level, int x, int z) {
        return RegionService.map(level.getServer()).levelAt(x, z);
    }

    public static Region regionAt(ServerLevel level, int x, int z) {
        RegionMap.Region r = RegionService.map(level.getServer()).regionAt(x, z);
        return new Region(r.id(), r.name(), r.low(), r.high(), r.heartX(), r.heartZ(), r.commons());
    }

    /**
     * A creature's level here: its place's level plus its role's offset, the deep dark's extra, never above what a Hearth
     * nearby allows (min of the place and the highest level among the household online), clamped 1-150.
     */
    public static int creatureLevel(ServerLevel level, BlockPos pos, Role role) {
        int lv = levelAt(level, pos.getX(), pos.getZ()) + role.offset;
        // the Deep Tide (new moon): the night's hunters, elites and camps run two levels harder
        if (role == Role.HUNTER || role == Role.ELITE || role == Role.CAMP) lv += dev.awaken.living.pulse.Pulse.tideExtra(level);
        if (pos.getY() < 0 && level.getBiome(pos).is(Biomes.DEEP_DARK)) lv += DEEP_DARK;
        int calm = hearthCalm(level, pos);
        if (calm > 0) lv = Math.min(lv, calm + Math.max(0, role.offset));
        return Math.max(1, Math.min(RegionMap.MAX_LEVEL, lv));
    }

    /** The cap a Hearth puts on its land (its household's highest level among players online), or 0 for none. */
    static int hearthCalm(ServerLevel level, BlockPos pos) {
        int best = 0;
        for (BlockPos anchor : HearthHooks.within(level, pos, 80 + HEARTH_CALM)) {
            var info = Hearth.hearthAt(level, anchor);
            if (info.isEmpty()) continue;
            var h = info.get();
            if (anchor.distSqr(pos) > (double) (h.radius() + HEARTH_CALM) * (h.radius() + HEARTH_CALM)) continue;
            best = Math.max(best, onlineLevel(level, h.owner()));
            for (UUID m : h.members()) best = Math.max(best, onlineLevel(level, m));
        }
        return best;
    }

    private static int onlineLevel(ServerLevel level, UUID id) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
        return p == null ? 0 : Math.max(1, Progression.profile(p).level());
    }

    /** The rank band of a level, for colours (E 1-9, D 10-24, C 25-44, B 45-69, A 70-99, S 100+): 0..5. */
    public static int bandOf(int level) {
        return level < 10 ? 0 : level < 25 ? 1 : level < 45 ? 2 : level < 70 ? 3 : level < 100 ? 4 : 5;
    }
}
