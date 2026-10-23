package dev.awaken.descent;

import dev.awaken.core.api.MobHooks;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.rules.Zones;
import dev.awaken.descent.spawn.DescentSpawner;
import dev.awaken.descent.well.WellData;
import dev.awaken.descent.world.Layout;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Public API of the Descent for other modules (story, above). Keep these signatures stable.
 */
public final class Descent {
    private Descent() {}

    public static final String FINALE_BOSS = "awaken:signals_shadow";

    /** Depth index of an entity (0 = not in the Descent; 1..10 strata; 11+ the Endless). */
    public static int depthOf(Entity e) {
        return Strata.depthAt(e.level().dimension(), e.blockPosition());
    }

    public static boolean isInDescent(Entity e) {
        return Strata.isDescent(e.level().dimension());
    }

    /** The Well's centre in the overworld, once it is built. */
    public static Optional<BlockPos> wellPos(MinecraftServer server) {
        WellData d = WellData.get(server);
        return d.built() ? Optional.of(d.center()) : Optional.empty();
    }

    /**
     * The story module calls this to start the finale in the Seed's arena: spawns {@value #FINALE_BOSS} (once at a
     * time). The player must be in stratum 10. Returns the boss, or null if it couldn't start (or is already running).
     */
    public static @Nullable LivingEntity beginFinale(ServerPlayer player) {
        if (depthOf(player) != Strata.COUNT || !(player.level() instanceof ServerLevel level)) return null;
        if (Zones.finaleActive(level)) return null;
        BlockPos at = Layout.world(Strata.COUNT, Layout.finale());
        LivingEntity boss = MobHooks.spawnBoss(level, FINALE_BOSS, at, Strata.contentLevel(Strata.COUNT), "");
        if (boss == null) return null;
        DescentSpawner.flag(boss, DescentSpawner.FLAG_FINALE);
        Zones.setFinale(level, boss);
        level.playSound(null, at, DescentContent.SND_SEED.get(), net.minecraft.sounds.SoundSource.HOSTILE, 3.0F, 0.6F);
        return boss;
    }

    /** True while the finale boss is alive. */
    public static boolean finaleActive(MinecraftServer server) {
        ServerLevel level = server.getLevel(Strata.key(Strata.COUNT));
        return level != null && Zones.finaleActive(level);
    }

    /** The Seed is done for this player (the story calls it after its ending; the finale boss's death also does). */
    public static void completeSeed(ServerPlayer player) {
        DescentData d = DescentSync.data(player);
        if (d.seedCleared) return;
        d.seedCleared = true;
        Progression.markNewThing(player, "the seed");
        Progression.bestMoment(player, "Opened the Seed");
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("The Seed opens"))
                .body(Component.literal("Under the arena the pit wakes. Below the bottom of the world there is more: the Endless."))
                .row(Component.literal("The Endless"), Component.literal("Generated strata, stacking rules, your depth record"))
                .ttl(20 * 12).priority(8).build());
        DescentSync.push(player);
    }

    /**
     * The Deep Lift (above module): ride to a stratum's camp without Strain. {@code stratum} is a depth index; 0 rides to
     * the Well. The destination camp must be lit. Returns false (with a message) if the ride can't happen.
     */
    public static boolean ride(ServerPlayer player, int stratum) {
        if (!DescentModule.enabled()) return false;
        if (stratum <= 0) {
            DescentTravel.send(player, 0, DescentTravel.Arrival.CAMP, false);
            return true;
        }
        DescentData d = DescentSync.data(player);
        if (!d.lit.contains(stratum)) {
            player.sendOverlayMessage(Component.literal("The Deep Lift only rides to camps you have lit."));
            return false;
        }
        DescentTravel.send(player, stratum, DescentTravel.Arrival.CAMP, false);
        return true;
    }

    /** Whether a player is within r blocks of the camp Waystone of a depth. */
    public static boolean nearCamp(ServerPlayer p, int depth, double r) {
        BlockPos w = DescentWaystones.waystonePos(depth);
        return p.blockPosition().distSqr(w) < r * r;
    }
}
