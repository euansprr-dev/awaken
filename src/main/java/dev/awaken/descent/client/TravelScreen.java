package dev.awaken.descent.client;

import dev.awaken.descent.Strata;
import dev.awaken.descent.net.DescentPayloads;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** The Waystone list: lit camps and the Well, with the Strain a climb would cost. */
final class TravelScreen extends Screen {
    private final DescentPayloads.OpenTravel data;

    TravelScreen(DescentPayloads.OpenTravel data) {
        super(Component.translatable("screen.awaken.descent_travel"));
        this.data = data;
    }

    private int panelH() {
        return 46 + data.destinations().size() * 24 + 28;
    }

    @Override
    protected void init() {
        int w = 240, x = width / 2 - w / 2;
        int y = height / 2 - panelH() / 2 + 34;
        for (DescentPayloads.Destination d : data.destinations()) {
            String label = d.label() + (d.strainPercent() > 0 ? "  ·  Strain +" + d.strainPercent() + "%" : "");
            Button b = Button.builder(Component.literal(label), btn -> {
                ClientPacketDistributor.sendToServer(new DescentPayloads.Travel(d.depth()));
                onClose();
            }).bounds(x + 10, y, w - 20, 20).build();
            if (d.strainPercent() > 0) {
                b.setTooltip(Tooltip.create(Component.literal("Climbing up costs Strain: −" + d.strainPercent()
                        + "% to all stats for a while. The Deep Lift at home climbs without it.")));
            }
            addRenderableWidget(b);
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Rest here"), btn -> onClose())
                .bounds(x + 10, y + 4, w - 20, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int w = 240, x = width / 2 - w / 2, h = panelH(), y = height / 2 - h / 2;
        Glass.panel(g, x, y, w, h, Glass.LANTERN, 1f);
        g.centeredText(font, Component.literal("Waystones"), width / 2, y + 8, Glass.a(Glass.TITLE, 1f));
        g.centeredText(font, Component.literal(Strata.label(data.here())), width / 2, y + 20, Glass.a(Glass.LABEL, 1f));
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
