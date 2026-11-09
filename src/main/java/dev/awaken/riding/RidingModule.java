package dev.awaken.riding;

import dev.awaken.core.api.RidingHooks;
import dev.awaken.signal.Signal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Riding Along (SPEC §55; Stage A of Made by Hand): a partner who isn't good at fighting comes along and is useful
 * without being able to go down, without making anything harder and without being carried to rank.
 */
public final class RidingModule {
    private RidingModule() {}

    public static void init(IEventBus modBus) {
        RidingContent.register(modBus);
        modBus.addListener(RidingNet::register);
        RidingHooks.install(new RidingHooks.Access() {
            @Override
            public boolean rider(Player p) {
                return Bonds.rider(p);
            }

            @Override
            public boolean bonded(Player p) {
                return Bonds.bonded(p);
            }

            @Override
            public Optional<UUID> partner(Player p) {
                Bonds.Bond b = Bonds.of(p.getUUID());
                return b == null ? Optional.empty() : Optional.of(b.other(p.getUUID()));
            }

            @Override
            public boolean folded(Player p) {
                return Fold.folded(p.getUUID());
            }

            @Override
            public void door(ServerPlayer p) {
                Bonds.door(p);
            }

            @Override
            public void leftRun(ServerPlayer p) {
                Bonds.leftRun(p);
            }
        });
        Signal.onAction("riding", Bonds::onAnswer);
        RidingGoals.register();
        RidingCommands.register();
        RidingTests.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, RidingEvents::onDeath);
        bus.addListener(EventPriority.LOWEST, RidingEvents::afterDeath);
        bus.addListener(EventPriority.HIGHEST, RidingEvents::onIncoming);
        bus.addListener(EventPriority.LOWEST, RidingEvents::onDamage);
        bus.addListener(EventPriority.HIGH, RidingEvents::onTarget);
        bus.addListener((ServerTickEvent.Post e) -> Bonds.tick(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> Bonds.clear());
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) RidingNet.sendTo(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Bonds.loggedOut(p);
        });
        // in a partner's shadow you can't touch anything
        bus.addListener(EventPriority.HIGHEST, (AttackEntityEvent e) -> {
            if (Fold.folded(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.RightClickBlock e) -> {
            if (Fold.folded(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.LeftClickBlock e) -> {
            if (Fold.folded(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.RightClickItem e) -> {
            if (Fold.folded(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.EntityInteract e) -> {
            if (Fold.folded(e.getEntity().getUUID())) e.setCanceled(true);
        });
    }
}
