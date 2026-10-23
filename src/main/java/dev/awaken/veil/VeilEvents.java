package dev.awaken.veil;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.GateHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.veil.entity.VeilSpirit;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus wiring for the Veil. */
final class VeilEvents {
    private VeilEvents() {}

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(SignalLines::onReload);
        bus.addListener(VeilEvents::onPlayerTick);
        bus.addListener(VeilEvents::onServerTick);
        bus.addListener(VeilEvents::onLogin);
        bus.addListener(VeilEvents::onLogout);
        bus.addListener(VeilEvents::onDeath);
        bus.addListener(VeilEvents::onDamage);
        bus.addListener(VeilEvents::onDrops);
        bus.addListener(VeilEvents::onJoin);
        bus.addListener(VeilEvents::onGameMode);
        bus.addListener(VeilEvents::onTravel);
        bus.addListener(VeilEvents::onRightClickBlock);
        bus.addListener(VeilEvents::onRightClickItem);
        bus.addListener(VeilEvents::onEntityInteract);
        bus.addListener(VeilEvents::onLeftClickBlock);
        bus.addListener(VeilEvents::onAttack);
        bus.addListener(VeilEvents::onEntityTick);
        bus.addListener(VeilEvents::onStopped);
        bus.addListener(VeilEvents::onBreak);
        bus.addListener(VeilEvents::onPlace);
        Signal.onAction("veil", (player, action, accepted) -> {
            if (accepted && "veil:return".equals(action)) Veil.returnToBody(player);
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Projection.Spirit s = Projection.LIVE.get(sp.getUUID());
        if (s != null) {
            Projection.tick(sp, s);
            return;
        }
        if (Projection.owesDeath(sp)) Projection.tryPendingDeath(sp);
        Meditation.tick(sp);
        Stillpoint.tick(sp);
        SpiritSight.tickAwake(sp);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        VeilGates.serverTick(event.getServer());
        if (event.getServer().getTickCount() % 1200 == 0) {
            long now = event.getServer().overworld().getGameTime();
            Meditation.LAST_HURT.values().removeIf(t -> now - t > 1200);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!Projection.LIVE.containsKey(sp.getUUID())) Projection.restoreFromState(sp, false);
        AwakenNet.send(sp, VeilPayloads.State.NONE);
        Spirits.sendCodex(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (Projection.LIVE.containsKey(sp.getUUID())) Projection.end(sp, Projection.EndReason.LOGOUT);
        Meditation.stop(sp);
        Meditation.LAST_HURT.remove(sp.getUUID());
        SpiritSight.forgetAwake(sp.getUUID());
        Stillpoint.forget(sp.getUUID());
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (dead instanceof ServerPlayer sp) {
            if (Projection.LIVE.containsKey(sp.getUUID())) Projection.end(sp, Projection.EndReason.DEATH);
            Meditation.stop(sp);
        } else if (dead instanceof Mannequin) {
            Projection.onBodyDeath(dead, event.getSource());
        }
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide()) return;
        if (e instanceof ServerPlayer sp) {
            Meditation.LAST_HURT.put(sp.getUUID(), sp.level().getGameTime());
            if (sp.getHealth() > 0 && sp.getHealth() <= sp.getMaxHealth() * 0.2F && Progression.profile(sp).awakened()) {
                VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
                if (!mem.glimpsed && GateHooks.inGate(sp)) {
                    // the first time is an accident: for a moment you see yourself from outside
                    mem.glimpsed = true;
                    AwakenNet.send(sp, new VeilPayloads.Glimpse(50));
                    Signal.toast(sp, Component.literal("For a moment you saw yourself from outside."));
                }
            }
        } else if (e instanceof Mannequin) {
            Projection.onBodyHurt(e, event.getSource(), event.getInflictedDamage());
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Mannequin m && m.getExistingData(VeilContent.BODY_TAG.get()).map(BodyTag::isBody).orElse(false)) {
            event.setCanceled(true); // a Body is a copy for looks: it never drops anything
        }
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();
        if (e instanceof Mannequin m) {
            BodyTag tag = m.getExistingData(VeilContent.BODY_TAG.get()).orElse(BodyTag.NONE);
            if (tag.isBody() && Projection.spiritOfBody(m) == null) event.setCanceled(true); // an orphaned Body
        } else if (e instanceof VeilSpirit && event.loadedFromDisk()) {
            event.setCanceled(true);
        } else if (event.loadedFromDisk() && e instanceof LivingEntity && CordEaters.isStrayEater(e)) {
            event.setCanceled(true);
        }
    }

    private static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && Projection.LIVE.containsKey(sp.getUUID()) && !Projection.inTransit(sp)) {
            event.setCanceled(true);
            sp.sendSystemMessage(Component.literal("You're out of your body. Return first (/awaken veil return)."));
        }
    }

    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && Projection.LIVE.containsKey(sp.getUUID()) && !Projection.inTransit(sp)
                && event.getDimension() != sp.level().dimension()) {
            event.setCanceled(true);
        }
    }

    private static boolean spirit(net.minecraft.world.entity.player.Player p) {
        return p instanceof ServerPlayer sp && Projection.LIVE.containsKey(sp.getUUID());
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (spirit(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (spirit(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (spirit(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (spirit(event.getEntity())) event.setCanceled(true);
    }

    private static void onAttack(AttackEntityEvent event) {
        if (spirit(event.getEntity())) event.setCanceled(true);
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (Possession.WORN.isEmpty() && CordEaters.EATERS.isEmpty()) return;
        Entity e = event.getEntity();
        if (e.level().isClientSide()) return;
        if (e instanceof Mob m && Possession.WORN.containsKey(m.getUUID())) {
            if (Possession.drive(m)) event.setCanceled(true);
        } else if (e instanceof LivingEntity l && CordEaters.EATERS.containsKey(l.getUUID())) {
            if (CordEaters.drive(l)) event.setCanceled(true);
        }
    }

    /** The temple and the Veil Gate pockets are shared and quiet: survival hands don't change them. */
    static boolean protectedSpot(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos) {
        if (!(level instanceof net.minecraft.world.level.Level l) || l.dimension() != Veil.STILLPOINT) return false;
        boolean temple = Math.abs(pos.getX()) <= 40 && Math.abs(pos.getZ()) <= 40;
        boolean pockets = pos.getX() >= VeilGates.POCKET_X0 - 16 && pos.getZ() >= VeilGates.POCKET_Z0 - 16;
        return temple || pockets;
    }

    private static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp && !sp.isCreative() && protectedSpot(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    private static void onPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && !sp.isCreative() && protectedSpot(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    private static void onStopped(ServerStoppedEvent event) {
        Projection.LIVE.clear();
        Projection.TRANSIT.clear();
        Meditation.STATES.clear();
        Meditation.LAST_HURT.clear();
        Possession.WORN.clear();
        CordEaters.EATERS.clear();
        Spirits.clearAll();
        SpiritSight.clearCache();
    }
}
