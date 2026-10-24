package dev.awaken.compat.client;

import dev.awaken.Awaken;
import dev.awaken.compat.Look.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;

import java.util.Collection;
import java.util.List;

/**
 * Awaken's settings (Mods → AWAKEN → Config) reach the Look switch: the client section ends with a "Look" row, right
 * under Lens, whose button opens the switch. Built on NeoForge's own extension point for config sections (synthetic
 * values), so every other row is NeoForge's as before.
 */
public final class LookSettings {
    private LookSettings() {}

    /** The section factory for {@link ConfigurationScreen}: Awaken's main client file gets the Look row. */
    public static Screen section(ConfigurationScreen parent, ModConfig.Type type, ModConfig config, Component title) {
        if (type == ModConfig.Type.CLIENT && config.getFileName().equals(Awaken.MODID + "-client.toml")) return new Section(parent, type, config, title);
        return new ConfigurationSectionScreen(parent, type, config, title);
    }

    private static final class Section extends ConfigurationSectionScreen {
        Section(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
            super(parent, type, config, title);
        }

        @Override
        protected Collection<? extends Element> createSyntheticValues() {
            Tier t = LookSwitch.current();
            Button open = Button.builder(Component.literal((t == null ? "Choose" : t.pretty()) + "  ›"),
                    b -> Minecraft.getInstance().gui.setScreen(new LookScreen(this))).width(Button.DEFAULT_WIDTH).build();
            return List.of(new Element(Component.literal("Look"),
                    Component.literal("Swift, Cinematic or Ultra: one choice sets Lens, Distant Horizons and Iris together."), open, false));
        }
    }
}
