package dev.awaken.powers.client;

import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The POWERS section of the Status tab: your six keys, energy, and every power you own. Click a power, then click a
 * key to put it there (click the same key again to clear it). Clicking a key first picks up the power on it.
 */
final class PowersSection implements StatusSections.Section {
    private static final int KEY = 22;
    private static final int CELL = 22;
    private static final int PAD = 3;

    private static @Nullable String selected;

    @Override
    public String title() {
        return "POWERS";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean visible() {
        Loadout l = ClientPowers.loadout();
        return l != null && !l.owned().isEmpty();
    }

    private static List<PowerType> owned(Loadout l) {
        List<PowerType> out = new ArrayList<>();
        for (Loadout.Owned o : l.owned().values()) {
            PowerType t = PowerRegistry.get(o.id());
            if (t != null) out.add(t);
        }
        out.sort(Comparator.<PowerType>comparingInt(t -> t.isPassive() ? 1 : 0).thenComparing(t -> t.family).thenComparing(t -> t.name));
        return out;
    }

    private static int columns(int width) {
        return Math.max(1, (width + PAD) / (CELL + PAD));
    }

    private static int gridTop() {
        return KEY + 12 + 14;
    }

    private static int detailLines(Font font, int width) {
        PowerType t = PowerRegistry.get(selected == null ? "" : selected);
        if (t == null) return 1;
        return 3 + font.split(Component.literal(t.description), width).size();
    }

    @Override
    public int height(int width) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return 0;
        int n = owned(l).size();
        int rows = (n + columns(width) - 1) / columns(width);
        Font font = Minecraft.getInstance().font;
        // room for the longest description shown on hover (most wrap to 3-4 lines)
        return gridTop() + rows * (CELL + PAD) + 6 + Math.max(6, detailLines(font, width)) * 10;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return;
        Font font = Minecraft.getInstance().font;
        long now = ClientPowers.now();
        PowerType hovered = null;

        // the six keys
        for (int s = 0; s < Loadout.SLOTS; s++) {
            int kx = x + s * (KEY + 6) + (s >= 4 ? 8 : 0);
            PowerType t = PowerRegistry.get(l.slot(s));
            boolean hover = mouseX >= kx && mouseX < kx + KEY && mouseY >= y && mouseY < y + KEY;
            boolean fits = selected != null && PowerRegistry.get(selected) != null && PowerRegistry.get(selected).fitsSlot(s);
            g.fill(kx, y, kx + KEY, y + KEY, hover ? 0xC0243046 : 0xA0101622);
            int edge = fits ? 0xFFFFCF7A : s == Loadout.ULTIMATE ? 0xFF8A6A3A : s == Loadout.MOVEMENT ? 0xFF3C6A8A : 0xFF3A4A60;
            g.outline(kx, y, KEY, KEY, edge);
            if (t != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), kx + 3, y + 3, 16, 16);
                if (hover) hovered = t;
            }
            g.centeredText(font, Component.literal(PowerHud.keyLabel(s)), kx + KEY / 2, y + KEY + 2, 0xFFAFC2D8);
        }

        // energy line
        float max = l.maxEnergy();
        String energy = String.format("Energy %d / %d  ·  %.1f per second", (int) ClientPowers.energy(), (int) max, l.regenPerTick() * 20);
        g.text(font, Component.literal(energy), x, y + KEY + 14, 0xFF9AD4FF, false);

        // owned powers
        List<PowerType> list = owned(l);
        int cols = columns(width);
        int top = y + gridTop();
        for (int i = 0; i < list.size(); i++) {
            PowerType t = list.get(i);
            int cx = x + (i % cols) * (CELL + PAD);
            int cy = top + (i / cols) * (CELL + PAD);
            String id = t.id.toString();
            boolean sel = id.equals(selected);
            boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            if (hover) hovered = t;
            g.fill(cx, cy, cx + CELL, cy + CELL, sel ? 0xC0384A20 : hover ? 0xC0243046 : 0x90101622);
            g.outline(cx, cy, CELL, CELL, sel ? 0xFFFFCF7A : 0xFF000000 | darken(t.family.colour));
            g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), cx + 3, cy + 3, 16, 16);
            Loadout.Owned o = l.get(id);
            int level = o == null ? 1 : o.level();
            for (int p = 0; p < level; p++) g.fill(cx + 2 + p * 3, cy + CELL - 4, cx + 4 + p * 3, cy + CELL - 2, 0xFFFFCF7A);
            // mastery toward the next level: a hairline under the pips (full and gold when mastered)
            int barW = CELL - 4;
            g.fill(cx + 2, cy + CELL - 1, cx + 2 + barW, cy + CELL, 0xFF1A2230);
            int filled = Math.round(barW * l.masteryProgress(id));
            if (filled > 0) g.fill(cx + 2, cy + CELL - 1, cx + 2 + filled, cy + CELL, level >= Loadout.MAX_LEVEL ? 0xFFFFCF7A : 0xFF9AD4FF);
            if (l.slotOf(id) >= 0) g.fill(cx + CELL - 4, cy + 1, cx + CELL - 1, cy + 4, 0xFF9AD4FF);
            if (t.isPassive()) g.fill(cx + CELL - 4, cy + 1, cx + CELL - 1, cy + 4, 0xFF86E0A0);
            if (l.cooldownLeft(id, now) > 0) g.fill(cx + 3, cy + 3, cx + 19, cy + 19, 0x80000000);
        }

        // details for the selected (or hovered) power
        PowerType show = hovered != null ? hovered : PowerRegistry.get(selected == null ? "" : selected);
        int dy = top + ((list.size() + cols - 1) / cols) * (CELL + PAD) + 6;
        if (show == null) {
            g.text(font, Component.literal("Click a power, then a key. Passives (green) are always on."), x, dy, 0xFF7F8EA3, false);
            return;
        }
        Loadout.Owned o = l.get(show.id.toString());
        String variant = o == null || o.variant() == 0 ? "" : " · " + show.variant(o.variant()).name();
        String slot = switch (show.slot) {
            case ACTIVE -> "Active";
            case ULTIMATE -> "Ultimate (R)";
            case MOVEMENT -> "Movement";
            case PASSIVE -> "Passive";
        };
        g.text(font, Component.literal(show.name).withColor(show.family.colour), x, dy, 0xFFFFFFFF, false);
        String meta = show.family.label + " · " + slot + variant + " · Lv " + (o == null ? 1 : o.level())
                + (show.isPassive() ? "" : " · " + (int) show.cost + " energy · " + trim(show.cooldown / 20.0) + " s");
        g.text(font, Component.literal(meta), x, dy + 10, 0xFF9FB0C8, false);
        g.text(font, Component.literal(masteryLine(l, show, o)), x, dy + 20, 0xFF9AD4FF, false);
        g.textWithWordWrap(font, Component.literal(show.description), x, dy + 30, width, 0xFFD6DEE8, false);
    }

    /** "Mastery 34% → Lv 3 · grows in real fights", or "Mastered: level 5". */
    private static String masteryLine(Loadout l, PowerType t, Loadout.@org.jspecify.annotations.Nullable Owned o) {
        int level = o == null ? 1 : o.level();
        if (level >= Loadout.MAX_LEVEL) return "Mastered: level " + Loadout.MAX_LEVEL;
        int pct = (int) Math.floor(100 * l.masteryProgress(t.id.toString()));
        String how = t.isPassive() ? "grows as it strikes" : "grows in real fights";
        return "Mastery " + pct + "% → Lv " + (level + 1) + " · " + how;
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    private static int darken(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 3 / 5;
        int gg = (rgb >> 8 & 0xFF) * 3 / 5;
        int b = (rgb & 0xFF) * 3 / 5;
        return r << 16 | gg << 8 | b;
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return false;
        for (int s = 0; s < Loadout.SLOTS; s++) {
            int kx = x + s * (KEY + 6) + (s >= 4 ? 8 : 0);
            if (mouseX < kx || mouseX >= kx + KEY || mouseY < y || mouseY >= y + KEY) continue;
            String current = l.slot(s);
            if (selected == null) {
                if (!current.isEmpty()) {
                    selected = current;
                    click();
                }
                return true;
            }
            PowerType t = PowerRegistry.get(selected);
            if (selected.equals(current)) {
                ClientPacketDistributor.sendToServer(new PowerPayloads.Equip(s, ""));
                selected = null;
                click();
            } else if (t != null && t.fitsSlot(s)) {
                ClientPacketDistributor.sendToServer(new PowerPayloads.Equip(s, selected));
                selected = null;
                click();
            } else {
                ClientPowers.flash(s, "That power doesn't fit this key");
            }
            return true;
        }
        List<PowerType> list = owned(l);
        int cols = columns(width);
        int top = y + gridTop();
        for (int i = 0; i < list.size(); i++) {
            int cx = x + (i % cols) * (CELL + PAD);
            int cy = top + (i / cols) * (CELL + PAD);
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                String id = list.get(i).id.toString();
                selected = list.get(i).isPassive() || id.equals(selected) ? null : id;
                click();
                return true;
            }
        }
        return false;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
