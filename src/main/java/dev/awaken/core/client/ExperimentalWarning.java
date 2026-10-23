package dev.awaken.core.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * AWAKEN's dimensions (gates, trials, the Halo, the Descent) come from data packs, which vanilla calls "experimental": it
 * stops on a Yes/No prompt when a world is created (and NeoForge once more on its first load). Like the "Shut Up
 * Experimental Settings" mod most modpacks carry, this answers it for the player, on the next tick so the screen change
 * never happens inside its own init. Client setting {@code skipExperimentalWarning}.
 */
public final class ExperimentalWarning {
    private ExperimentalWarning() {}

    private static @Nullable Screen pending;
    private static String answerKey = "";

    static void init() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> {
            if (!enabled()) return;
            Screen s = e.getScreen();
            if (s instanceof ConfirmScreen && hasKey(s.getTitle(), "selectWorld.warning.experimental.title")) {
                pending = s;
                answerKey = "gui.yes";
            } else if (s instanceof BackupConfirmScreen && hasKey(s.getTitle(), "selectWorld.backupQuestion.experimental")) {
                pending = s;
                answerKey = "selectWorld.backupJoinSkipButton";
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            Screen s = pending;
            if (s == null) return;
            pending = null;
            if (Minecraft.getInstance().gui.screen() != s) return; // the player already answered
            for (var child : s.children()) {
                if (child instanceof Button b && hasKey(b.getMessage(), answerKey)) {
                    Awaken.LOG.info("[core] answered vanilla's experimental-settings prompt (setting skipExperimentalWarning)");
                    b.onPress(new MouseButtonInfo(0, 0));
                    return;
                }
            }
        });
    }

    private static boolean hasKey(Component c, String key) {
        return c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    private static boolean enabled() {
        try {
            return AwakenConfig.Client.SKIP_EXPERIMENTAL_WARNING.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
