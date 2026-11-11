package dev.awaken.gear;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Hunter gear (plan v2 §4.5): gear crafted from what the awakened world drops, in tiers by rank, beside vanilla gear.
 * Tiers E (Wildhide), D (Beastbound), C (Gatewrought), B (Veilwoven), A (Lordscale) and S (Sovereign Weave), seven pieces
 * each, crafted at a crafting table from creature drops, beast trophies, Gate Lord and Descent relics and essence (recipes unlock in the book through progression's
 * ingredient rule and the level road). Material hooks are item tags {@code #awaken:hunter_<hide|core|bind>_<tier>} that
 * new creature families join from their own merge folder. See docs/modules/gear.md.
 */
public final class GearModule {
    private GearModule() {}

    public static void init(IEventBus modBus) {
        GearItems.init(modBus);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(SetBonus::onPlayerTick);
        bus.addListener(Pounce::onPlayerTick);
        bus.addListener(Pounce::onJump);
        bus.addListener(EventPriority.LOW, GearCombat::onIncomingDamage);
        bus.addListener(EventPriority.HIGHEST, GearCombat::beforeDeath);
        bus.addListener(EventPriority.LOWEST, GearCombat::afterDeath);
        // how our gear wears (SPEC §67.1): armour by how hard it is hit, and a worn-out piece at half its worth
        bus.addListener(EventPriority.LOWEST, GearWear::onIncomingDamage);
        bus.addListener(EventPriority.LOWEST, GearWear::onShieldBlock);
        bus.addListener(GearWear::onArmourHurt);
        bus.addListener(GearWear::onAttributes);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> GearState.forget(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> GearState.clear());
        // a focus's power damage moves with its Temper, and its attributes don't show it
        dev.awaken.core.api.ForgeHooks.stat(s -> s.getItem() instanceof HunterGearItem g && g.piece == Piece.FOCUS
                ? new dev.awaken.core.api.ForgeHooks.Stat("Power damage", "+" + Math.round(GearCombat.focusBonus(s) * 100) + "%") : null);
        GearNow.register();
        GearCommands.register();
        GearTests.register();
    }
}
