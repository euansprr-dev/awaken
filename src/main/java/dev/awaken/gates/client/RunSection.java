package dev.awaken.gates.client;

import dev.awaken.core.api.MapMarks;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The band's Status page inside a gate (SPEC §30): the run (rank, floor, Satchel, kills, time on the floor, the gate's
 * hint) with a button to look into the Satchel. Shown only during a run, above the Journal. The gates themselves are
 * on the Atlas ({@link #marks}): the Gate Map tab is gone.
 */
final class RunSection implements StatusSections.Section {
    private static final int H = 68;
    private int satchelX, satchelY, satchelW;

    @Override
    public String title() {
        return "THIS GATE";
    }

    @Override
    public int order() {
        return 15; // the Status page, above the Journal
    }

    @Override
    public boolean visible() {
        return ClientGates.inRun();
    }

    @Override
    public int height(int width) {
        return H;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float pt) {
        Font font = Minecraft.getInstance().font;
        GatePayloads.RunHud h = ClientGates.hud();
        g.fill(x, y, x + width, y + H, 0xC00F1724);
        g.outline(x, y, width, H, 0xFF7FC3FF);
        String rank = ClientGates.rankLabel(h.rank());
        g.text(font, rank, x + 6, y + 6, ClientGates.rankColour(h.rank()), false);
        g.text(font, RunHudLayer.themeName(h.theme()) + " · floor " + h.floor() + " of " + h.floors(), x + 10 + font.width(rank), y + 6, 0xFFFFFFFF, false);
        g.text(font, "Satchel " + h.satchelSlots() + " / 27 slots · " + h.satchelXp() + " Satchel XP", x + 6, y + 20, 0xFFFFCF7A, false);
        int s = h.floorSeconds();
        g.text(font, "Kills " + h.kills() + " · " + String.format("%d:%02d", s / 60, s % 60) + " on this floor", x + 6, y + 32, 0xFFB9C7D9, false);
        if (!h.hint().isEmpty()) g.text(font, h.hint(), x + 6, y + 44, 0xFFE2D2FF, false);
        String btn = "[ View Satchel ]";
        satchelW = font.width(btn);
        satchelX = x + width - satchelW - 6;
        satchelY = y + 54;
        boolean hover = mouseX >= satchelX && mouseX < satchelX + satchelW && mouseY >= satchelY && mouseY < satchelY + 10;
        g.text(font, btn, satchelX, satchelY, hover ? 0xFFFFFFFF : 0xFF7FC3FF, false);
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        if (mouseX >= satchelX && mouseX < satchelX + satchelW && mouseY >= satchelY && mouseY < satchelY + 10) {
            ClientPacketDistributor.sendToServer(new GatePayloads.OpenSatchel());
            return true;
        }
        return false;
    }

    /** The gates whose pillars you can see, for the Atlas: rank, how long each holds, broken ones in the warning red. */
    static List<MapMarks.Gate> marks(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return List.of(); // gates show from the surface
        List<MapMarks.Gate> out = new ArrayList<>();
        for (GatePayloads.Beacon b : ClientGates.beacons()) {
            boolean calm = (b.flags() & GatePayloads.Beacon.CALM) != 0, scripted = (b.flags() & GatePayloads.Beacon.SCRIPTED) != 0;
            String when = b.broken() ? "broken: its monsters are out" : scripted ? "" : calm ? "calm"
                    : b.breaksIn() == 0 ? "breaks today" : "breaks in " + b.breaksIn() + (b.breaksIn() == 1 ? " day" : " days");
            String rank = ClientGates.rankLabel(b.rank());
            out.add(new MapMarks.Gate(b.pos().immutable(), rank, b.broken() ? 0xFF8A7A : ClientGates.rankColour(b.rank()) & 0xFFFFFF,
                    rank + "-rank gate", when, b.broken(), false));
        }
        return out;
    }
}
