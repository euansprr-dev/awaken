package dev.awaken.compat.client;

import dev.awaken.compat.Discovered;
import dev.awaken.compat.WorldMaps;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

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
     * Development (autopilot {@code fardepth share|auto}): Distant Horizons shares its depth with Lens on every graphics
     * API, so a tour can check it where it isn't checked yet (Vulkan), or again only where it was. Nothing without DH.
     */
    public static void shareFarDepthEverywhere(boolean on) {
        if (ModList.get().isLoaded("distanthorizons")) DistantHorizonsClient.shareEverywhere(on);
    }
}
