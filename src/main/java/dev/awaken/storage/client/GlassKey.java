package dev.awaken.storage.client;

import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** A small glass key on a container's panel; follows the panel wherever the recipe book or another mod pushes it. */
final class GlassKey extends Button {
    private final AbstractContainerScreen<?> screen;
    private final IntSupplier rx, ry;
    private final Runnable press;
    private final long created = System.nanoTime();
    private BooleanSupplier lit = () -> false;

    GlassKey(AbstractContainerScreen<?> screen, IntSupplier rx, IntSupplier ry, int w, int h, String label, String tip, Runnable press) {
        super(screen.getLeftPos() + rx.getAsInt(), screen.getTopPos() + ry.getAsInt(), w, h, Component.literal(label), b -> {}, DEFAULT_NARRATION);
        this.screen = screen;
        this.rx = rx;
        this.ry = ry;
        this.press = press;
        setTooltip(Tooltip.create(Component.literal(tip)));
    }

    GlassKey lit(BooleanSupplier on) {
        lit = on;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        press.run();
    }

    /** Each key has its own sound, played when the server answers. */
    @Override
    public void playDownSound(SoundManager sounds) {}

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float a) {
        setPosition(screen.getLeftPos() + rx.getAsInt(), screen.getTopPos() + ry.getAsInt());
        float k = Math.min(1f, (System.nanoTime() - created) / 150_000_000f);
        UiCanvas c = GuiCanvas.of(g);
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean over = isHoveredOrFocused(), on = lit.getAsBoolean();
        int accent = on ? Glass.AMBER : Glass.EDGE;
        c.fill(x, y, x + w, y + h, over || on ? Glass.a(accent, 0.30f * k) : Glass.a(0x1B2A44, 0.85f * k));
        c.outline(x, y, w, h, Glass.a(accent, (over || on ? 1f : 0.6f) * k));
        String label = getMessage().getString();
        var font = Minecraft.getInstance().font;
        c.text(font, label, x + (w - font.width(label)) / 2 + 1, y + (h - 8) / 2 + 1, Glass.a(Glass.TITLE, k), false);
    }
}
