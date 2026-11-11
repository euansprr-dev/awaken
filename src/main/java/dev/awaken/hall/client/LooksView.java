package dev.awaken.hall.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourInlay;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.Forged;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.client.ForgeClient;
import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.core.client.ui.Figure;
import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * You, in what you wear (SPEC §52, §44.8): each piece can take the look of any set you've forged whole, keeping its own
 * armour, ability and mastery, and a dye you carry colours the whole suit's glow. Changed at a stand; looked at anywhere.
 */
final class LooksView extends CloseView {
    private static final float ROW_Y = 300F, ROW_H = 92F, SWATCH = 30F;
    private static final float ACCENT_Y = 690F;
    private static final String[] PIECE = {"Helm", "Cuirass", "Greaves", "Boots"};

    private final Component title = Stage.blackletter("Your looks");
    private final float fromX, fromY;
    private List<FormattedCharSequence> intro = List.of(), note = List.of();

    LooksView(float fromX, float fromY) {
        this.fromX = fromX;
        this.fromY = fromY;
    }

    @Override float zoomX() {
        return fromX;
    }

    @Override float zoomY() {
        return fromY;
    }

    @Override int tier() {
        return 0;
    }

    private static ItemStack worn(int slot) {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getItemBySlot(ForgeSong.PIECES[slot].getSlot());
    }

    private static List<ArmourSet> choices(ArmourSet own) {
        List<ArmourSet> out = new ArrayList<>();
        out.add(own);
        ArmourPayloads.Offers o = HallClient.suits;
        if (o != null) for (ArmourPayloads.Offer one : o.offers()) {
            ArmourSet s = ArmourSet.values()[one.piece() / 4];
            if ((one.flags() & ArmourPayloads.KNOWN_WHOLE) != 0 && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static boolean atStand() {
        ArmourPayloads.Offers o = HallClient.suits;
        return o != null && o.stand();
    }

    @Override void draw(Stage s, HallNet.Wall w, float dt, float time, double mouseX, double mouseY) {
        float e = eased(), mx = s.sx(mouseX), my = s.sy(mouseY);
        if (intro.isEmpty()) {
            intro = s.wrap("Wear the look of any set you've forged whole. Each piece keeps its own armour, ability and mastery.", Panel.TEXT_W, 3F);
            note = s.wrap("A dye you carry colours the whole suit's glow. The first square gives each piece its own glow back.", Panel.TEXT_W, 3F);
        }
        s.glow(560F, 490F, 720F, 620F, 0xFFCF7A, .24F * e);
        var player = Minecraft.getInstance().player;
        if (player != null && e > .3F) {
            int x0 = Math.round(s.ox + 180F * s.k), y0 = Math.round(s.oy + 40F * s.k), x1 = Math.round(s.ox + 940F * s.k), y1 = Math.round(s.oy + 900F * s.k);
            Figure.followsMouse(s.g, x0, y0, x1, y1, Math.round(360F * s.k), .0625F, (float) mouseX, (float) mouseY, player);
        }
        float slide = (1F - e) * (1F - e);
        s.g.nextStratum();
        s.g.pose().pushMatrix().translate(720F * slide, 0F);
        Panel.frame(s);
        s.g.nextStratum();
        float y = Panel.Y0 + 26F;
        s.title(title, Panel.X, y, 4F, Stage.TITLE);
        y += 110F;
        Panel.lines(s, intro, y, 3, Stage.LABEL);
        boolean open = atStand();
        for (int i = 0; i < 4; i++) row(s, i, ROW_Y + i * ROW_H, mx, my, open);
        accent(s, mx, my, open);
        s.g.pose().popMatrix();
        float fy = 916F + 60F * slide;
        s.hint("Esc", "Back", 40F, fy);
    }

    private void row(Stage s, int slot, float y, float mx, float my, boolean open) {
        ItemStack it = worn(slot);
        ArmourLooks.Look own = ArmourLooks.of(it);
        s.text(PIECE[slot], Panel.X, y + 14F, 3F, Stage.LABEL);
        if (own == null || own.piece() != ForgeSong.PIECES[slot]) {
            s.text("Nothing forged worn here", Panel.X + 150F, y + 14F, 3F, Stage.DIM);
            return;
        }
        Panel.icon(s, it, Panel.X + 150F, y, 3F);
        ArmourSet drawn = ArmourLooks.drawn(it).set();
        s.fitted(drawn == own.set() ? drawn.setName + " (its own)" : drawn.setName, Panel.X + 212F, y + 14F, 340F, 3F, 0xFF000000 | drawn.rank.colour, -1);
        if (open && choices(own.set()).size() > 1) {
            arrow(s, Panel.RIGHT - 90F, y + 6F, "<", mx, my);
            arrow(s, Panel.RIGHT - 40F, y + 6F, ">", mx, my);
        }
    }

    private static void arrow(Stage s, float x, float y, String label, float mx, float my) {
        boolean over = mx >= x && mx < x + 40F && my >= y && my < y + 44F;
        if (over) s.fill(x, y, x + 40F, y + 44F, 0x552A3A56);
        s.outline(x, y, x + 40F, y + 44F, 2, 0xA07FC3FF);
        s.centre(label, x + 20F, y + 8F, 3F, Stage.TITLE);
    }

    private void accent(Stage s, float mx, float my, boolean open) {
        s.text("Accent", Panel.X, ACCENT_Y, 3F, Stage.EDGE);
        float y = ACCENT_Y + 36F;
        int current = accentNow();
        for (int k = 0; k <= DyeColor.values().length; k++) {
            float x = Panel.X + k * (SWATCH + 6F);
            boolean have = k == 0 || dyes(k) > 0;
            int colour = k == 0 ? 0xFF2A3446 : 0xFF000000 | DyeColor.byId(k - 1).getTextureDiffuseColor();
            s.fill(x, y, x + SWATCH, y + SWATCH, have ? colour : colour & 0x00FFFFFF | 0x40000000);
            boolean sel = k == 0 ? current == 0 : current == colour;
            if (sel) s.outline(x - 3F, y - 3F, x + SWATCH + 3F, y + SWATCH + 3F, 2, Stage.LANTERN);
        }
        Panel.lines(s, note, y + SWATCH + 14F, 3, Stage.DIM);
        if (!open) s.text("Looks are changed at a Fitting Stand.", Panel.X, Panel.Y1 - 52F, 3F, Stage.LANTERN);
    }

    /** The accent the worn suit shares (0: its own glow; -1: mixed). */
    private static int accentNow() {
        int seen = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            ItemStack it = worn(i);
            if (ArmourLooks.of(it) == null) continue;
            Forged story = it.get(ArmourContent.FORGED.get());
            int a = story == null || story.accent() == 0 || story.inlayForce() != null && story.accent() == ArmourInlay.colour(story.inlayForce()) ? 0 : story.accent();
            if (seen == Integer.MIN_VALUE) seen = a;
            else if (seen != a) return -1;
        }
        return seen == Integer.MIN_VALUE ? -1 : seen;
    }

    private static int dyes(int k) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        if (player.isCreative()) return 99;
        DyeColor colour = DyeColor.byId(k - 1);
        var inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof DyeItem && st.get(DataComponents.DYE) == colour) n += st.getCount();
        }
        return n;
    }

    @Override boolean click(float x, float y) {
        if (fly < 1F) return false;
        if (x >= 30F && x < 220F && y >= 906F && y < 946F) {
            closing = true;
            return true;
        }
        ArmourPayloads.Offers o = HallClient.suits;
        if (o == null || !o.stand()) return x >= Panel.X0;
        for (int i = 0; i < 4; i++) {
            float ry = ROW_Y + i * ROW_H + 6F;
            if (y < ry || y >= ry + 44F) continue;
            int d = x >= Panel.RIGHT - 90F && x < Panel.RIGHT - 50F ? -1 : x >= Panel.RIGHT - 40F && x < Panel.RIGHT ? 1 : 0;
            ArmourLooks.Look own = ArmourLooks.of(worn(i));
            if (d == 0 || own == null) return true;
            List<ArmourSet> list = choices(own.set());
            int at = Math.max(0, list.indexOf(ArmourLooks.drawn(worn(i)).set()));
            ArmourSet next = list.get(Math.floorMod(at + d, list.size()));
            ForgeClient.send(o.pos(), ArmourPayloads.LOOK, i, 0F, next == own.set() ? 0F : next.ordinal() + 1);
            return true;
        }
        float sy = ACCENT_Y + 36F;
        if (y >= sy && y < sy + SWATCH && x >= Panel.X) {
            int k = (int) ((x - Panel.X) / (SWATCH + 6F));
            if (k <= DyeColor.values().length && (k == 0 || dyes(k) > 0)) {
                ForgeClient.send(o.pos(), ArmourPayloads.ACCENT, 0, 0F, k);
                return true;
            }
        }
        return x >= Panel.X0;
    }

    @Override void step(int dc, int dt) {}

    @Override boolean key(int key) {
        if (key == InputConstants.KEY_L) {
            closing = true;
            return true;
        }
        return false;
    }

    @Override void space(boolean down) {}
}
