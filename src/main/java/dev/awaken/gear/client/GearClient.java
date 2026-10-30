package dev.awaken.gear.client;

import dev.awaken.gear.HunterGearItem;
import dev.awaken.gear.SetBonus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Client side of Hunter gear: the "worn n/4" tooltip line, the late sets' silhouettes (where armour has no meshes). */
public final class GearClient {
    private GearClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(Silhouettes::registerExtensions);
        modBus.addListener(Silhouettes::addReloadListener);
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, GearClient::onTooltip);
    }

    /** Armour pieces say how much of their set you wear, right under the tier line. */
    private static void onTooltip(ItemTooltipEvent e) {
        if (!(e.getItemStack().getItem() instanceof HunterGearItem g) || !g.piece.isArmour()) return;
        Player p = e.getEntity();
        if (p == null) return;
        int n = SetBonus.count(p, g.tier);
        Component line = Component.literal("You wear " + n + " of 4").withColor(n >= SetBonus.HALF ? g.tier.rank.colour : 0x8A93A6);
        List<Component> tip = e.getToolTip();
        String head = g.tier.rank.label + "-rank Hunter gear";
        for (int i = 0; i < tip.size(); i++) {
            if (tip.get(i).getString().startsWith(head)) {
                tip.add(i + 1, line);
                return;
            }
        }
        tip.add(line);
    }
}
