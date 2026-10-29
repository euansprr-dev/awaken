package dev.awaken.hearth.client;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;


/** Status screen section "HEARTH": tier and radius, recognised rooms, the town, trophies and records. */
final class HearthSection extends dev.awaken.signal.client.UiSection {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

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
        var player = Minecraft.getInstance().player;
        return v.has() || player != null && dev.awaken.progression.Progression.profile(player).awakened();
    }

    @Override
    protected void build(dev.awaken.signal.client.Ui ui) {
        HearthView v = view();
        if (!v.has()) {
            ui.text("Meditation Mat", Glass.TEXT);
            ui.text(dev.awaken.hearth.guide.Guide.MAT_RECIPE, Glass.LABEL);
            ui.text(dev.awaken.hearth.guide.Guide.MAT_USE, Glass.LABEL);
            ui.text("No Hearth yet · Ilse keeps the last fire up the north road: carry a coal from it and set it down where you'd live",
                    Glass.a(Glass.LABEL, 1f));
            return;
        }
        String head = (v.tier() <= 0 ? "Hearthfire" : "Tier " + ROMAN[Math.max(0, Math.min(5, v.tier()))] + " · Forge " + ROMAN[Math.max(0, Math.min(5, v.forgeTier()))])
                + " · radius " + v.radius();
        ui.pair(v.home().name().isEmpty() ? head : v.home().name(), v.home().name().isEmpty() ? v.pos().getX() + ", " + v.pos().getY() + ", " + v.pos().getZ() : head,
                Glass.a(Glass.LANTERN, 1f), Glass.a(Glass.LABEL, 0.8f));
        String comfort = "Comfort " + v.home().comfort() + "/" + dev.awaken.hearth.Comfort.MAX
                + (v.home().style().isEmpty() ? "" : " · " + v.home().style());
        long left = dev.awaken.hearth.Rested.left(Minecraft.getInstance().player);
        ui.pair(comfort, left > 0 ? "Rested · " + Math.max(1, (left + 1199) / 1200) + " min" : "",
                Glass.a(Glass.TEXT, 1f), Glass.a(Glass.AMBER, 1f));
        var flow = ui.flow();
        boolean any = false;
        for (dev.awaken.hearth.room.RoomType t : dev.awaken.hearth.room.RoomType.values()) {
            String shown = v.rooms().stream().filter(r -> r.startsWith(t.label)).findFirst().orElse(null);
            if (shown == null) continue;
            flow.chip(shown, Glass.AMBER);
            any = true;
        }
        if (!any) flow.chip("No rooms yet: walls and a roof", 0x4A5A72);
        flow.end();
        // the full guide (what each room needs, why one isn't recognised, the next upgrade)
        var open = ui.flow();
        if (open.button("Your Hearth: rooms and upgrades", Glass.LANTERN, true)) GuideClient.send(dev.awaken.hearth.guide.Guide.Act.OPEN);
        open.end();
        if (!v.town().isEmpty()) ui.text("Town · " + String.join(", ", v.town()), Glass.a(Glass.TEXT, 1f));
        ui.text("Trophies " + v.trophies() + " · Records " + v.records(), Glass.a(Glass.LABEL, 1f));
        ui.text("Meditation Mat", Glass.TEXT);
        ui.text(dev.awaken.hearth.guide.Guide.MAT_RECIPE, Glass.LABEL);
        ui.text(dev.awaken.hearth.guide.Guide.MAT_USE, Glass.LABEL);
    }
}
