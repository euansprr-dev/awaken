package dev.awaken.together.client;

import dev.awaken.progression.client.StatusSections;
import dev.awaken.together.TogetherNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The band's way in (SPEC §70.1, §70.4), on the Status page: who you're travelling with, anyone near you could ask,
 * and your own My fights switch. Every button that concerns someone else sends them a card; nothing is a typed command.
 */
final class TogetherSection implements StatusSections.Section {
    private static final int ROW = 13, PAD = 6, MAX_NEAR = 3;
    private static final double NEAR = 48;
    private static final int INK = 0xFFFFFFFF, SOFT = 0xFFB9C7D9, HOLO = 0xFF7FD4FF, ROSE = 0xFFF0B79C, RIM = 0xFF7FD4FF;
    private static final String TRAVEL = "[ Travel together ]", LEAVE = "[ Leave party ]", FULL = "[ Full ]", GENTLE = "[ Gentle ]", HOME = "[ Share my home ]";
    private static final String FIGHTS = "My fights are yours alone. Full is the game as it is. On Gentle, blows aimed at you are plainer and do half, and a blow that would kill you is a second wind instead. You can change it any time you're out of a fight.";
    private static final String EQUAL = "[ Equal ]", HALF = "[ Half ]", OWN = "[ Own ]";
    private static final String[] SHARED = {
            "Each their own: nobody's XP is passed on. You each level by what you do yourselves.",
            "Half: each of you gets half of what the others earn, wherever you are.",
            "Equal: what any of you earns, you all get, wherever you are. You level together."};
    private static final UUID NOBODY = new UUID(0, 0);

    private record Button(int x, int y, int w, int kind, UUID other) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + 10;
        }
    }

    private final List<Button> buttons = new ArrayList<>();

    @Override
    public String title() {
        return "TOGETHER";
    }

    @Override
    public int order() {
        return 16; // just below the run, when there is one
    }

    /** Players near enough to ask who aren't in the party already. */
    private static List<AbstractClientPlayer> near() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) return List.of();
        List<AbstractClientPlayer> out = new ArrayList<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == me || p.isSpectator() || p.distanceToSqr(me) > NEAR * NEAR) continue;
            boolean mate = false;
            for (TogetherNet.Member m : ClientParty.members()) if (m.id().equals(p.getUUID())) mate = true;
            if (!mate) out.add(p);
        }
        return out;
    }

    private static int mates() {
        return Math.max(0, ClientParty.members().size() - 1);
    }

    /** Party members who don't share your home yet: each has one more row, the offer. */
    private static int strangersAtHome() {
        int n = 0;
        for (TogetherNet.Member m : ClientParty.members()) if (!m.is(TogetherNet.F_SELF) && !m.is(TogetherNet.F_HOUSEMATE)) n++;
        return n;
    }

    @Override
    public boolean visible() {
        return Minecraft.getInstance().player != null; // the switch is yours even alone
    }

    private static List<FormattedCharSequence> fights(Font font, int width) {
        return font.split(Component.literal(FIGHTS), width - PAD * 2);
    }

    private static List<FormattedCharSequence> shared(Font font, int width) {
        return font.split(Component.literal(SHARED[Math.max(0, Math.min(2, ClientParty.share()))]), width - PAD * 2);
    }

    /** Shared XP shows with a party: its name, its three choices on a row of their own, and what the lit one means. */
    private static int sharedHeight(Font font, int width) {
        return mates() > 0 ? ROW * 2 + shared(font, width).size() * 10 + 4 : 0;
    }

    @Override
    public int height(int width) {
        Font font = Minecraft.getInstance().font;
        int rows = mates() + strangersAtHome() + Math.min(MAX_NEAR, near().size());
        return PAD + rows * ROW + (mates() > 0 ? ROW : 0) + 4 + sharedHeight(font, width) + ROW + fights(font, width).size() * 10 + PAD;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null) return;
        Font font = mc.font;
        int h = height(width);
        g.fill(x, y, x + width, y + h, 0xC00F1724);
        g.outline(x, y, width, h, RIM);
        buttons.clear();
        int ty = y + PAD, row = 0;
        // who you're travelling with
        for (TogetherNet.Member m : ClientParty.members()) {
            if (m.is(TogetherNet.F_SELF)) continue;
            int colour = ClientParty.COLOURS[row++ % ClientParty.COLOURS.length];
            g.fill(x + PAD, ty + 2, x + PAD + 4, ty + 6, colour);
            String what = m.is(TogetherNet.F_ELSEWHERE) ? "elsewhere" : (int) Math.round(Math.sqrt(me.distanceToSqr(m.x() + 0.5, m.y(), m.z() + 0.5))) + " blocks away";
            String line = "In your party, " + (m.is(TogetherNet.F_GENTLE) ? "on Gentle, " : "") + what;
            int lw = Math.min(font.width(line), width / 2);
            dev.awaken.signal.client.Ui.fitted(g, font, m.name(), x + PAD + 8, ty, width - PAD * 3 - 8 - lw, 1, INK, false);
            dev.awaken.signal.client.Ui.fitted(g, font, line, x + width - PAD - lw, ty, lw, 1, SOFT, false);
            ty += ROW;
            if (!m.is(TogetherNet.F_HOUSEMATE)) {
                button(g, font, HOME, x + width - PAD - font.width(HOME), ty, TogetherNet.HOME, m.id(), HOLO, mouseX, mouseY);
                ty += ROW;
            }
        }
        if (mates() > 0) {
            button(g, font, LEAVE, x + width - PAD - font.width(LEAVE), ty, TogetherNet.LEAVE, NOBODY, SOFT, mouseX, mouseY);
            ty += ROW;
        }
        // anyone near you could ask
        int shown = 0;
        for (AbstractClientPlayer p : near()) {
            if (shown++ == MAX_NEAR) break;
            int bx = x + width - PAD - font.width(TRAVEL);
            dev.awaken.signal.client.Ui.fitted(g, font, p.getGameProfile().name(), x + PAD, ty, bx - x - PAD - 6, 1, INK, false);
            button(g, font, TRAVEL, bx, ty, TogetherNet.TRAVEL, p.getUUID(), HOLO, mouseX, mouseY);
            ty += ROW;
        }
        ty += 4;
        // shared XP: the party's, so anyone in it may change it
        if (mates() > 0) {
            int share = Math.max(0, Math.min(2, ClientParty.share()));
            g.text(font, "Shared XP", x + PAD, ty, INK, false);
            ty += ROW;
            int ox = x + width - PAD - font.width(OWN), hx = ox - 6 - font.width(HALF), ex = hx - 6 - font.width(EQUAL);
            button(g, font, EQUAL, ex, ty, TogetherNet.SHARE_EQUAL, NOBODY, share == 2 ? HOLO : SOFT, mouseX, mouseY);
            button(g, font, HALF, hx, ty, TogetherNet.SHARE_HALF, NOBODY, share == 1 ? HOLO : SOFT, mouseX, mouseY);
            button(g, font, OWN, ox, ty, TogetherNet.SHARE_OWN, NOBODY, share == 0 ? HOLO : SOFT, mouseX, mouseY);
            ty += ROW;
            for (FormattedCharSequence l : shared(font, width)) {
                g.text(font, l, x + PAD, ty, SOFT, false);
                ty += 10;
            }
            ty += 4;
        }
        // my fights
        TogetherNet.Member self = ClientParty.self();
        boolean gentle = self != null && self.is(TogetherNet.F_GENTLE);
        g.text(font, "My fights", x + PAD, ty, INK, false);
        int gx = x + width - PAD - font.width(GENTLE), fx = gx - 6 - font.width(FULL);
        button(g, font, FULL, fx, ty, TogetherNet.FULL, NOBODY, gentle ? SOFT : HOLO, mouseX, mouseY);
        button(g, font, GENTLE, gx, ty, TogetherNet.GENTLE, NOBODY, gentle ? ROSE : SOFT, mouseX, mouseY);
        ty += ROW;
        for (FormattedCharSequence l : fights(font, width)) {
            g.text(font, l, x + PAD, ty, SOFT, false);
            ty += 10;
        }
    }

    private void button(GuiGraphicsExtractor g, Font font, String label, int x, int y, int kind, UUID other, int colour, int mouseX, int mouseY) {
        Button b = new Button(x, y, font.width(label), kind, other);
        buttons.add(b);
        g.text(font, label, x, y, b.over(mouseX, mouseY) ? INK : colour, false);
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        for (Button b : buttons) {
            if (!b.over(mouseX, mouseY)) continue;
            ClientPacketDistributor.sendToServer(new TogetherNet.Ask(b.kind(), b.other()));
            return true;
        }
        return false;
    }
}
