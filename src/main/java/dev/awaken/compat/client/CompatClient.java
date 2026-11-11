package dev.awaken.compat.client;

import dev.awaken.Awaken;
import dev.awaken.compat.Discovered;
import dev.awaken.compat.WorldMaps;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;

import java.util.ArrayList;
import java.util.List;

/** Client side of the companion integrations (each loads only when its mod is present) and the Look switch. */
public final class CompatClient {
    private CompatClient() {}

    public static void init(IEventBus modBus) {
        CompatClientConfig.register();
        LookClient.init(modBus);
        if (ModList.get().isLoaded("ambientsounds")) AmbientSoundsBridge.init();
        modBus.addListener(FMLClientSetupEvent.class, e -> {
            if (ModList.get().isLoaded("distanthorizons")) e.enqueueWork(DistantHorizonsClient::init);
            if (ModList.get().isLoaded("jei")) e.enqueueWork(dev.awaken.compat.client.jei.JeiHooks::init);
            if (ModList.get().isLoaded("entityculling")) e.enqueueWork(EntityCullingBridge::init);
        });
    }

    /**
     * The autopilot's {@code compat ...} lines (development tours): {@code inventory} opens the inventory (JEI's list
     * sits beside it), {@code jei <args>} drives JEI ({@link dev.awaken.compat.client.jei.JeiHooks#autopilot}),
     * {@code press <key mapping name>} presses any key mapping by its name (e.g. another mod's "open the map"),
     * {@code discovered} reports the local copy of the spoiler guard's record, {@code map} whether Xaero's World Map is
     * shut here ({@link WorldMaps}).
     */
    public static String autopilot(String args) {
        Minecraft mc = Minecraft.getInstance();
        String[] a = args.trim().split("\\s+", 2);
        String rest = a.length > 1 ? a[1].trim() : "";
        switch (a[0]) {
            case "inventory" -> {
                if (mc.player == null) return "no player";
                mc.gui.setScreen(new InventoryScreen(mc.player));
                return "inventory open";
            }
            case "creative" -> {
                return creativeTab(mc, rest);
            }
            case "jei" -> {
                return ModList.get().isLoaded("jei") ? dev.awaken.compat.client.jei.JeiHooks.autopilot(rest) : "JEI is not installed";
            }
            case "press" -> {
                for (KeyMapping k : mc.options.keyMappings) {
                    if (k.getName().equals(rest)) {
                        KeyMapping.click(k.getKey());
                        return "pressed " + rest;
                    }
                }
                return "no key mapping " + rest;
            }
            case "discovered" -> {
                return mc.player == null ? "no player" : Discovered.of(mc.player).size() + " discovered, guard " + (Spoilers.active() ? "on" : "off");
            }
            case "map" -> {
                if (mc.player == null) return "no player";
                boolean shut = mc.player.getActiveEffects().stream().anyMatch(i -> i.getEffect().is(WorldMaps.NO_WORLD_MAP));
                return "world map " + (shut ? "shut" : "open") + " in " + mc.player.level().dimension().identifier();
            }
            default -> {
                return "unknown: " + args;
            }
        }
    }

    /**
     * Autopilot {@code compat creative <tab>}: the creative inventory open on that tab (an id; a bare path is Awaken's),
     * on its page, as a click on the tab would. Needs creative. Logs every tab of the menu in order on the way.
     */
    private static String creativeTab(Minecraft mc, String which) {
        if (mc.player == null) return "no player";
        if (!mc.player.hasInfiniteMaterials()) return "not in creative";
        Identifier id = Identifier.tryParse(which.contains(":") ? which : Awaken.MODID + ":" + which);
        CreativeModeTab tab = id == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getValue(id);
        var screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), mc.options.operatorItemsTab().get());
        mc.gui.setScreen(screen);
        // the screen's own pages, made the way it makes them: ten category tabs a page, in the sorted order
        List<CreativeModeTab> shown = CreativeModeTabRegistry.getSortedCreativeModeTabs().stream().filter(CreativeModeTab::hasAnyItems).toList();
        StringBuilder menu = new StringBuilder();
        for (int i = 0; i < shown.size(); i++) {
            menu.append(i % 10 == 0 ? (i == 0 ? "" : " | ") + "page " + (i / 10 + 1) + ": " : ", ")
                    .append(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(shown.get(i))).append(" (").append(shown.get(i).getDisplayItems().size()).append(')');
        }
        Awaken.LOG.info("[compat] creative menu: {}", menu);
        int at = tab == null ? -1 : shown.indexOf(tab);
        if (at < 0) return "no tab " + which + " in the menu";
        // the tab's page by the screen's own ">" button (so its "1 / 2" reads right), then a click on the tab; the page
        // made here only gives the tab's place on it
        var page = new CreativeTabsScreenPage(new ArrayList<>(shown.subList(at / 10 * 10, Math.min(shown.size(), at / 10 * 10 + 10))));
        int left = (screen.width - 195) / 2, top = (screen.height - 136) / 2;
        for (int i = 0; i < at / 10; i++) click(screen, left + 195 - 10, top - 40);
        click(screen, left + 27 * page.getColumn(tab) + 13, page.isTop(tab) ? top - 16 : top + 136 + 16);
        return "creative tab " + id + " (page " + (at / 10 + 1) + ", " + tab.getDisplayItems().size() + " stacks): " + tab.getDisplayName().getString();
    }

    private static void click(CreativeModeInventoryScreen screen, double x, double y) {
        var click = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
        screen.mouseClicked(click, false);
        screen.mouseReleased(click);
    }

    /**
     * Development (autopilot {@code fardepth share|auto}): Distant Horizons shares its depth with Lens on every graphics
     * API, so a tour can check it where it isn't checked yet (Vulkan), or again only where it was. Nothing without DH.
     */
    public static void shareFarDepthEverywhere(boolean on) {
        if (ModList.get().isLoaded("distanthorizons")) DistantHorizonsClient.shareEverywhere(on);
    }
}
