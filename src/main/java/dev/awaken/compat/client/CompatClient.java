package dev.awaken.compat.client;

import dev.awaken.compat.Discovered;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client side of the companion integrations; each loads only when its mod is present. */
public final class CompatClient {
    private CompatClient() {}

    public static void init(IEventBus modBus) {
        CompatClientConfig.register();
        modBus.addListener(FMLClientSetupEvent.class, e -> {
            if (ModList.get().isLoaded("distanthorizons")) e.enqueueWork(DistantHorizonsClient::init);
            if (ModList.get().isLoaded("jei")) e.enqueueWork(dev.awaken.compat.client.jei.JeiHooks::init);
        });
    }

    /**
     * The autopilot's {@code compat ...} lines (development tours): {@code inventory} opens the inventory (JEI's list
     * sits beside it), {@code jei <args>} drives JEI ({@link dev.awaken.compat.client.jei.JeiHooks#autopilot}),
     * {@code press <key mapping name>} presses any key mapping by its name (e.g. another mod's "open the map"),
     * {@code discovered} reports the local copy of the spoiler guard's record.
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
            default -> {
                return "unknown: " + args;
            }
        }
    }
}
