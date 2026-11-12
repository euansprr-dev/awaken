package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.Passage;
import dev.awaken.gates.Gates;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Waking on the journey before a Hearth (SPEC §67.12; Euan 12 Nov: "if you die before placing your hearth, it spawns
 * you ALL THE WAY BACK IN CARILLON"). A player who has landed at the Far Landing, has no Hearth and no bed (or inn room)
 * of their own, and falls nearer the landing than the city wakes on the landing's pier by the hold instead of at world
 * spawn. Each player by their own landing and their own fall; a world without a landing never gets here.
 */
final class Waking {
    private Waking() {}

    /** Where you wake: on the pier in the landing's bay, a step from the hold (south-facing plan). */
    static final BlockPos BAY = new BlockPos(-6, 1, -6);
    /** The once-mark for the first wake here, which is the one that says so. */
    static final String TOLD = "woke_landing";

    /** Where each player last fell, in the overworld (a gate's door for a fall in a gate). Server thread only. */
    private static final Map<UUID, BlockPos> FELL = new HashMap<>();
    /** Where each player last stood in the overworld, sampled every two seconds: a fall in another dimension. */
    private static final Map<UUID, BlockPos> LAST_OUT = new HashMap<>();
    /** Players sent to the landing's bay, and where they fell: logged and told once their new body is up there. */
    private static final Map<UUID, Woke> WOKE = new HashMap<>();

    private record Woke(BlockPos bay, String fell) {}

    /** Every 40 ticks: where each player in the overworld is. */
    static void tick(MinecraftServer server) {
        if (!dev.awaken.core.api.TheLull.farLanding(server)) return;
        for (ServerPlayer p : server.overworld().players()) LAST_OUT.put(p.getUUID(), p.blockPosition());
    }

    /** HIGHEST, while a gate run still knows its door: where the fall was, on the overworld's map. */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !dev.awaken.core.api.TheLull.farLanding(p.level().getServer())) return;
        BlockPos at = Gates.doorOf(p).orElse(null);
        if (at == null) at = p.level().dimension() == Level.OVERWORLD ? p.blockPosition() : LAST_OUT.get(p.getUUID());
        if (at == null) FELL.remove(p.getUUID());
        else FELL.put(p.getUUID(), at);
    }

    /** LOWEST, the fall standing: a player who will wake at the landing has its land made while they're down (§28.9). */
    static void onFallen(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        FrontierData.Mooring m = mooringFor(p);
        if (m == null || p.getRespawnConfig() != null || !nearer(FELL.get(p.getUUID()), m, p.level().getServer())) return;
        BlockPos at = Skiff.at(m.origin(), m.facing(), BAY);
        Passage.warm(p.level().getServer().overworld(), at.getX() + 0.5, at.getZ() + 0.5);
    }

    /**
     * HIGH, before the Descent's checkpoint, the Hearth and a party's gate door (each of which still decides after it):
     * vanilla sending a landed player with no Hearth to world spawn sends them to the landing instead.
     */
    static void onRespawnPosition(PlayerRespawnPositionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isFromEndFight()) return;
        BlockPos fell = FELL.remove(p.getUUID());
        FrontierData.Mooring m = mooringFor(p);
        if (m == null) return;
        MinecraftServer server = p.level().getServer();
        TeleportTransition old = e.getTeleportTransition();
        // vanilla is sending them to world spawn: no point set (and no other module chose a place), or its bed is gone
        boolean worldSpawn = old.missingRespawnBlock() || p.getRespawnConfig() == null && old.newLevel() == server.findRespawnDimension();
        if (!worldSpawn) return;
        if (!nearer(fell, m, server, old.position())) {
            Awaken.LOG.info("[frontier] {} wakes at world spawn: they fell nearer the city than the landing ({})",
                    p.getGameProfile().name(), fell.toShortString());
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos bay = Skiff.at(m.origin(), m.facing(), BAY), hold = Skiff.at(m.origin(), m.facing(), Skiff.HOLD);
        float yaw = (float) (Mth.atan2(-(hold.getX() - bay.getX()), hold.getZ() - bay.getZ()) * Mth.RAD_TO_DEG);
        e.setTeleportTransition(new TeleportTransition(overworld, Vec3.atBottomCenterOf(bay), Vec3.ZERO, yaw, 0F,
                old.missingRespawnBlock(), false, Set.of(), old.postTeleportTransition()));
        WOKE.put(p.getUUID(), new Woke(bay, fell == null ? "somewhere unknown" : fell.toShortString()));
    }

    /** The new body is up: if it's in the bay (nothing after us chose another place), log it; the first time, say why. */
    static void onRespawned(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Woke w = WOKE.remove(p.getUUID());
        if (w == null || p.level() != p.level().getServer().overworld() || !p.blockPosition().closerThan(w.bay(), 3)) return;
        Awaken.LOG.info("[frontier] {} wakes at the Far Landing ({}): no Hearth, no bed, fell at {}",
                p.getGameProfile().name(), w.bay().toShortString(), w.fell());
        DirectorLog.log(p, "frontier", "wakes at the Far Landing: no Hearth or bed yet, fell at " + w.fell());
        if (!FrontierData.get(p.level().getServer()).mark(p.getUUID(), TOLD)) return;
        Signal.toast(p, Component.literal("You woke up back at the Far Landing. Until you set down your Hearth or sleep in a bed, this is where you'll wake if you fall."));
    }

    static void clear() {
        FELL.clear();
        LAST_OUT.clear();
        WOKE.clear();
    }

    /** The landing's mooring, if this player has landed there, it stands, and they have no Hearth; else null. */
    private static FrontierData.@Nullable Mooring mooringFor(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        if (!dev.awaken.core.api.TheLull.farLanding(server)) return null;
        FrontierData d = FrontierData.get(server);
        FrontierData.Mooring m = d.landing();
        if (m == null || !m.built() || !d.marked(p.getUUID(), "landed")) return null;
        return HearthHooks.hearthsOf(p).isEmpty() ? m : null;
    }

    /** Whether a fall at {@code fell} is nearer the landing than world spawn (an unknown place counts as nearer). */
    private static boolean nearer(@Nullable BlockPos fell, FrontierData.Mooring m, MinecraftServer server) {
        BlockPos spawn = server.findRespawnDimension().getRespawnData().pos();
        return nearer(fell, m, server, Vec3.atBottomCenterOf(spawn));
    }

    private static boolean nearer(@Nullable BlockPos fell, FrontierData.Mooring m, MinecraftServer server, Vec3 spawn) {
        if (fell == null) return true;
        double dl = Vec3.atBottomCenterOf(fell).distanceToSqr(Vec3.atBottomCenterOf(m.origin()));
        return dl < Vec3.atBottomCenterOf(fell).distanceToSqr(spawn);
    }
}
