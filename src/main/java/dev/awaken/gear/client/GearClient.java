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
        modBus.addListener(GearClient::decorations);
    }

    /** A crack down an icon, in the icon's own 16 pixels: x, y and 0 for the break, 1 for its lit edge. */
    private static final int[][] CRACK = {
            {9, 1, 0}, {9, 2, 0}, {8, 3, 0}, {8, 4, 0}, {9, 5, 0}, {10, 6, 0}, {9, 7, 0}, {8, 8, 0}, {7, 9, 0}, {7, 10, 0}, {8, 11, 0}, {7, 12, 0},
            {6, 13, 0}, {6, 14, 0}, {10, 2, 1}, {9, 4, 1}, {11, 6, 1}, {9, 8, 1}, {8, 10, 1}, {8, 12, 1}};

    /**
     * A worn-out piece of ours shows it on its icon (SPEC §68.1): a crack down it, wherever the icon is drawn (the hotbar,
     * the bag, a chest), on every item that is never destroyed ({@link dev.awaken.core.api.Lasting}), whichever module made it.
     */
    private static void decorations(net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent e) {
        net.neoforged.neoforge.client.IItemDecorator crack = (g, font, stack, x, y) -> {
            if (!dev.awaken.core.api.Lasting.wornOut(stack)) return false;
            for (int[] c : CRACK) g.fill(x + c[0], y + c[1], x + c[0] + 1, y + c[1] + 1, c[2] == 0 ? 0xFF14100E : 0xFFE0A050);
            return false;
        };
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item instanceof dev.awaken.core.api.Lasting) e.register(item, crack);
        }
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
