package dev.awaken.body.client;

import dev.awaken.body.Body;
import dev.awaken.body.Move;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;

/** Status screen: the moves your body has and how to do them, so an unlock toast you missed can be found again. */
final class BodySection extends UiSection {
    @Override public String title() { return "THE BODY"; }
    @Override public int order() { return 30; } // right after the Ascent: the moves you have

    @Override
    public boolean visible() {
        var p = Minecraft.getInstance().player;
        return p != null && Progression.profile(p).awakened();
    }

    @Override
    protected void build(Ui ui) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        // only what you have: moves still to come are never listed
        for (Move m : Move.values()) {
            if (!Body.unlocked(p, m)) continue;
            ui.text(m.title, Glass.a(Glass.TITLE, 1F));
            ui.text(m.how, Glass.a(Glass.LABEL, 0.85F));
            ui.gap(2);
        }
    }
}
