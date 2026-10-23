package dev.awaken.hearth.client;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Status screen section "HEARTH": tier and radius, recognised rooms, the town, Echoes, trophies and records. */
final class HearthSection extends dev.awaken.signal.client.UiSection {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    private static final List<String> ALL_ROOMS = List.of("Sanctuary", "Armory", "Trophy Hall", "Workshop", "Barracks");

    private static HearthView view() {
        var p = Minecraft.getInstance().player;
        return p == null ? HearthView.EMPTY : p.getData(HearthContent.VIEW.get());
    }

    @Override
    public String title() {
        return "HEARTH";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public boolean visible() {
        HearthView v = view();
        return v.has() || !v.echoes().isEmpty();
    }

    @Override
    protected void build(dev.awaken.signal.client.Ui ui) {
        HearthView v = view();
        long field = v.echoes().stream().filter(HearthView.EchoView::field).count();
        long duty = v.echoes().stream().filter(e -> !e.duty().isEmpty()).count();
        String echoes = "Echoes " + v.echoes().size() + " · " + field + "/" + v.fieldCap() + " marching · " + duty + " on duty";
        if (!v.has()) {
            ui.text("No Hearth yet · craft an Anchor from a Lord's Heart, 4 stone bricks and 4 copper", Glass.a(Glass.LABEL, 1f));
            if (!v.echoes().isEmpty()) ui.text(echoes, Glass.a(Glass.VIOLET, 1f));
            return;
        }
        String head = "Tier " + ROMAN[Math.max(0, Math.min(5, v.tier()))] + " · radius " + v.radius() + " · Forge " + ROMAN[Math.max(0, Math.min(5, v.forgeTier()))];
        ui.pair(head, v.pos().getX() + ", " + v.pos().getY() + ", " + v.pos().getZ(), Glass.a(Glass.LANTERN, 1f), Glass.a(Glass.LABEL, 0.8f));
        var flow = ui.flow();
        for (String room : ALL_ROOMS) {
            String shown = v.rooms().stream().filter(r -> r.startsWith(room)).findFirst().orElse(null);
            flow.chip(shown != null ? shown : room, shown != null ? Glass.AMBER : 0x4A5A72);
        }
        flow.end();
        ui.text("Town · " + (v.town().isEmpty() ? "Nobody has moved in yet" : String.join(", ", v.town())), Glass.a(Glass.TEXT, 1f));
        ui.text(echoes, Glass.a(Glass.VIOLET, 1f));
        ui.text("Trophies " + v.trophies() + " · Records " + v.records() + " · Pads " + v.pads(), Glass.a(Glass.LABEL, 1f));
    }
}
