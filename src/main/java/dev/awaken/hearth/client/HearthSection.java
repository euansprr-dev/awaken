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
final class HearthSection implements StatusSections.Section {
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
    public int height(int width) {
        return view().has() ? 64 : 22;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, float pt) {
        Font f = Minecraft.getInstance().font;
        HearthView v = view();
        long field = v.echoes().stream().filter(HearthView.EchoView::field).count();
        long duty = v.echoes().stream().filter(e -> !e.duty().isEmpty()).count();
        String echoes = "Echoes " + v.echoes().size() + " · " + field + "/" + v.fieldCap() + " marching · " + duty + " on duty";
        if (!v.has()) {
            g.text(f, "No Hearth yet · craft an Anchor from a Lord's Heart", x, y, Glass.a(Glass.LABEL, 1f), false);
            g.text(f, echoes, x, y + 11, Glass.a(Glass.VIOLET, 1f), false);
            return;
        }
        String head = "Tier " + ROMAN[Math.max(0, Math.min(5, v.tier()))] + " · radius " + v.radius() + " · Forge " + ROMAN[Math.max(0, Math.min(5, v.forgeTier()))];
        g.text(f, head, x, y, Glass.a(Glass.LANTERN, 1f), false);
        String at = v.pos().getX() + ", " + v.pos().getY() + ", " + v.pos().getZ();
        g.text(f, at, x + w - f.width(at), y, Glass.a(Glass.LABEL, 0.8f), false);
        int cx = x;
        for (String room : ALL_ROOMS) {
            String shown = v.rooms().stream().filter(r -> r.startsWith(room)).findFirst().orElse(null);
            int colour = shown != null ? Glass.AMBER : 0x4A5A72;
            cx += Glass.chip(g, f, shown != null ? shown : room, cx, y + 12, colour) + 4;
        }
        String town = v.town().isEmpty() ? "Nobody has moved in yet" : String.join(", ", v.town());
        g.text(f, "Town · " + town, x, y + 28, Glass.a(Glass.TEXT, 1f), false);
        g.text(f, echoes, x, y + 40, Glass.a(Glass.VIOLET, 1f), false);
        String extra = "Trophies " + v.trophies() + " · Records " + v.records() + " · Pads " + v.pads();
        g.text(f, extra, x, y + 52, Glass.a(Glass.LABEL, 1f), false);
    }
}
