package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentMemory;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Quiet notes over the Flow line (SPEC §17.6): a mastery level gained ("Tether mastery III · the line reaches
 * further"), shown for a few seconds as the body gets better, never a toast.
 */
final class AscentNotes {
    private AscentNotes() {}

    private static Component note;
    private static int at = -1000, clock;
    private static final int LIFE = 110;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** The server's levels changed: a note for the first track that rose (the client's copy was {@code before}). */
    static void mastery(int before, int after) {
        if (before == after) return;
        for (AscentMemory.Track t : AscentMemory.Track.values()) {
            int b = (before >> (4 * t.ordinal())) & 15, a = (after >> (4 * t.ordinal())) & 15;
            if (a > b && a <= 10) {
                String key = "awaken.ascent.mastery." + t.name().toLowerCase(java.util.Locale.ROOT);
                note = Component.translatable("awaken.ascent.mastery", Component.translatable(key), ROMAN[a],
                        Component.translatable(key + ".gain"));
                at = clock;
                return;
            }
        }
    }

    /** A new longest chain. */
    static void record(int chain) {
        note = Component.translatable("awaken.ascent.record", chain);
        at = clock;
        AscentFlow.gain(0.2F);
    }

    static void tick() {
        clock++;
    }

    static void reset() {
        note = null;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (note == null || mc.player == null || mc.gui.hud.isHidden()) return;
        float t = clock - at + dt.getGameTimeDeltaPartialTick(false);
        if (t > LIFE) {
            note = null;
            return;
        }
        float a = Math.min(1F, Math.min(t / 10F, (LIFE - t) / 20F));
        // over the hotbar, above the XP numbers and the Flow line
        int y = g.guiHeight() - 59;
        HudLook.centered(g, mc.font, note.getString(), g.guiWidth() / 2, y, Glass.a(0xEAF7FF, a), true);
    }
}
