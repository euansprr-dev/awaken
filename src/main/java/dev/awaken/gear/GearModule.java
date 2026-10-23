package dev.awaken.gear;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Hunter gear (plan v2 §4.5): gear crafted from what the awakened world drops, in tiers by rank, beside vanilla gear.
 * Tiers E (Wildhide), D (Beastbound), C (Gatewrought) and B (Veilwoven), seven pieces each, crafted at a crafting table
 * from creature drops, beast trophies, Gate Lord relics and essence (recipes unlock in the book through progression's
 * ingredient rule and the level road). Material hooks are item tags {@code #awaken:hunter_<hide|core|bind>_<tier>} that
 * new creature families join from their own merge folder. See docs/modules/gear.md.
 */
public final class GearModule {
    private GearModule() {}

    public static void init(IEventBus modBus) {
        GearItems.init(modBus);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(SetBonus::onPlayerTick);
        bus.addListener(EventPriority.LOW, GearCombat::onIncomingDamage);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> GearState.forget(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> GearState.clear());
        GearNow.register();
        GearCommands.register();
        GearTests.register();
    }
}
