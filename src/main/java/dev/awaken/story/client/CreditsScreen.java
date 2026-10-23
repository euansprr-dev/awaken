package dev.awaken.story.client;

import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Credits over a sea of clouds at dawn. Scrolls on its own; Esc closes. */
public final class CreditsScreen extends Screen {
    private final String ending;
    private float scroll;

    private static final String[] LINES = {
            "AWAKEN", "", "One home. Four directions. No ceiling.", "", "",
            "Made for Euan", "", "Design · the plan of 23 October 2026", "Built with Claude", "",
            "The Signal", "Vael", "Oren · Mira · Teodor · Sun", "The Keepers of the Hollow Court", "", "",
            "Thank you for playing.", "", "Good morning."
    };

    public CreditsScreen(String ending) {
        super(Component.literal("Credits"));
        this.ending = ending;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        scroll += 0.6f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // pre-dawn indigo into amber at the horizon, with a band of cloud
        g.fillGradient(0, 0, width, height * 2 / 3, 0xFF0B1020, 0xFF3A2A40);
        g.fillGradient(0, height * 2 / 3, width, height, 0xFFE0A15A, 0xFFF6E3C6);
        for (int i = 0; i < 9; i++) {
            int cx = (int) ((i * 97 + scroll * 0.3f) % (width + 160)) - 80;
            g.fill(cx, height * 2 / 3 - 6 + (i % 3) * 5, cx + 140, height * 2 / 3 + 10 + (i % 3) * 5, 0x60FFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int y = (int) (height - (scroll + a * 0.6f));
        g.centeredText(font, ending.toUpperCase(), width / 2, y - 24, Glass.a(Glass.LANTERN, 1f));
        for (String line : LINES) {
            g.centeredText(font, line, width / 2, y, Glass.a(line.equals("AWAKEN") ? Glass.EDGE : 0xFFFFFF, 1f));
            y += 14;
        }
        if (y < -20) onClose();
    }
}
