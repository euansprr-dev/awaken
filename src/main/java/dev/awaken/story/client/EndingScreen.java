package dev.awaken.story.client;

import dev.awaken.signal.client.Glass;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The choice at the Seed. Three doors, no going back. Wake up needs the Hundred Peal (SPEC §43.14): without it its card
 * says so and can't be chosen. "Not yet" leaves the Seed waiting.
 */
public final class EndingScreen extends Screen {
    private record Choice(String id, String title, String line, int colour) {}

    private static final Choice[] CHOICES = {
            new Choice("become", "Become the Signal", "Lie down in the light. Guide the next one.", Glass.EDGE),
            new Choice("break", "Break the Signal", "Shatter the Choir. Gates go quiet forever.", Glass.WARN),
            new Choice("wake", "Wake up", "Feel what the Deep holds. Let the colour come back.", Glass.LANTERN),
    };

    private long opened = -1;
    private final boolean wake;
    private static final String WAKE_LOCKED = "Needs the Hundred Peal: all hundred bells ringing together in your Great Belfry.";

    public EndingScreen(boolean wake) {
        super(Component.literal("The Seed"));
        this.wake = wake;
    }

    private boolean open(Choice c) {
        return wake || !c.id().equals("wake");
    }

    private int laterY() {
        return height / 2 + 64;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xF0020306, 0xF0101828);
    }

    private int cardX(int i, int w) {
        int total = 3 * w + 2 * 12;
        return (width - total) / 2 + i * (w + 12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (opened < 0 && minecraft.level != null) opened = minecraft.level.getGameTime();
        float t = minecraft.level == null ? 1f : Math.min(1f, (minecraft.level.getGameTime() - opened + a) / 30f);
        g.centeredText(font, "◇ THE SEED", width / 2, height / 2 - 80, Glass.a(Glass.EDGE, t));
        g.centeredText(font, "Choose. There is no wrong answer. There is no going back.", width / 2, height / 2 - 66, Glass.a(Glass.TEXT, t));
        int w = Math.min(150, (width - 60) / 3), h = 90;
        for (int i = 0; i < CHOICES.length; i++) {
            Choice c = CHOICES[i];
            int x = cardX(i, w), y = height / 2 - 40;
            boolean on = open(c), hover = on && Glass.inside(mouseX, mouseY, x, y, w, h);
            float ca = t * (on ? (hover ? 1f : 0.8f) : 0.4f);
            Glass.panel(g, x, y, w, h, c.colour(), ca);
            g.centeredText(font, c.title(), x + w / 2, y + 12, Glass.a(c.colour(), ca));
            int ty = y + 30;
            for (var line : font.split(Component.literal(on ? c.line() : WAKE_LOCKED), w - 16)) {
                g.centeredText(font, line, x + w / 2, ty, Glass.a(Glass.TEXT, ca));
                ty += 10;
            }
        }
        boolean later = Glass.inside(mouseX, mouseY, width / 2 - 40, laterY() - 4, 80, 16);
        g.centeredText(font, "Not yet", width / 2, laterY(), Glass.a(Glass.TEXT, t * (later ? 1f : 0.55f)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int w = Math.min(150, (width - 60) / 3), h = 90;
        for (int i = 0; i < CHOICES.length; i++) {
            int x = cardX(i, w), y = height / 2 - 40;
            if (Glass.inside(event.x(), event.y(), x, y, w, h)) {
                if (!open(CHOICES[i])) return true;
                ClientPacketDistributor.sendToServer(new StoryPayloads.ChooseEnding(CHOICES[i].id()));
                onClose();
                return true;
            }
        }
        if (Glass.inside(event.x(), event.y(), width / 2 - 40, laterY() - 4, 80, 16)) {
            ClientPacketDistributor.sendToServer(new StoryPayloads.ChooseEnding("later"));
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
