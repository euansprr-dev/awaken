package dev.awaken.together;

import dev.awaken.core.api.FightsHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.api.RidingHooks;
import dev.awaken.signal.Signal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Together, All the Way (SPEC §70): the party you chose, seeing each other, the rules for any two in one fight, and
 * each player's own difficulty (My fights: Full or Gentle). It replaces Riding Along (§55). Alone, nothing here does
 * anything. Must be set up after gates, whose party rule this one stands on.
 */
public final class TogetherModule {
    private TogetherModule() {}

    public static void init(IEventBus modBus) {
        TogetherConfig.register();
        TogetherContent.register(modBus);
        modBus.addListener(TogetherNet::register);
        PartyHooks.install(new PartyService(PartyHooks.installed()));
        FightsHooks.install(new FightsHooks.Access() {
            @Override
            public boolean gentle(Player p) {
                return Fights.gentle(p);
            }

            @Override
            public double weight(Player p) {
                return Fights.gentle(p) ? TogetherConfig.gentleWeight() : 1.0;
            }

            @Override
            public boolean kneeling(Player p) {
                return Fights.kneeling(p.getUUID());
            }

            @Override
            public boolean goesDown(Player p) {
                return p instanceof ServerPlayer sp && Fights.goesDown(sp);
            }

            @Override
            public int stance(net.minecraft.world.entity.LivingEntity target, int amount, Player by) {
                return by instanceof ServerPlayer sp ? Fights.stance(target, amount, sp) : amount;
            }

            @Override
            public boolean pair() {
                return TogetherConfig.pair();
            }

            @Override
            public net.minecraft.world.entity.LivingEntity nextTarget(net.minecraft.world.entity.Mob boss, net.minecraft.world.entity.LivingEntity current) {
                return Fights.nextTarget(boss, current);
            }

            @Override
            public int gentleHold() {
                return TogetherConfig.gentleHold();
            }

            @Override
            public double gentleOpening() {
                return TogetherConfig.gentleOpening();
            }
        });
        // on a knee nothing can be cast: powers already ask this of the hook Riding Along left behind
        RidingHooks.install(new RidingHooks.Access() {
            @Override
            public boolean folded(Player p) {
                return Fights.kneeling(p.getUUID());
            }
        });
        Signal.onAction("together", PartyService::onAnswer);
        dev.awaken.core.api.BellHooks.onRung(Answering::rung);
        TogetherCommands.register();
        TogetherTests.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, Fights::onDeath);
        bus.addListener(EventPriority.HIGHEST, Fights::onIncoming);
        bus.addListener(EventPriority.LOWEST, Fights::onDamage);
        bus.addListener(EventPriority.HIGH, Fights::onTarget);
        bus.addListener(EventPriority.HIGHEST, Fights::onInteract);
        bus.addListener(EventPriority.LOW, Fights::onRespawnPosition);
        bus.addListener((ServerTickEvent.Post e) -> {
            Fights.tick(e.getServer());
            TogetherNet.tick(e.getServer());
            TourPartner.tick(e.getServer());
            Answering.tick(e.getServer());
            if (e.getServer().getTickCount() % 20 == 0) PartyService.openingTogether(e.getServer());
        });
        bus.addListener((ServerStoppingEvent e) -> TourPartner.removeAll(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> {
            Fights.forget();
            PartyService.forget();
            TogetherNet.forget();
            Answering.forget();
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            // whoever is already playing hears that someone new has arrived, once a session (SPEC §70.6)
            if (!(e.getEntity() instanceof ServerPlayer p) || !TogetherConfig.joins() || dev.awaken.progression.Progression.profile(p).awakened()) return;
            for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
                if (o != p && dev.awaken.progression.Progression.profile(o).awakened()) {
                    Signal.toast(o, net.minecraft.network.chat.Component.literal(p.getGameProfile().name() + " has arrived, and hasn't awakened yet."));
                }
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            Fights.forget(e.getEntity().getUUID());
            TogetherNet.forget(e.getEntity().getUUID());
        });
        // on a knee you can't touch anything
        bus.addListener(EventPriority.HIGHEST, (AttackEntityEvent e) -> {
            if (Fights.kneeling(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.RightClickBlock e) -> {
            if (Fights.kneeling(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.LeftClickBlock e) -> {
            if (Fights.kneeling(e.getEntity().getUUID())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (PlayerInteractEvent.RightClickItem e) -> {
            if (Fights.kneeling(e.getEntity().getUUID())) e.setCanceled(true);
        });
    }
}
