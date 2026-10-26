package dev.awaken.powers.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Powers page's lead (SPEC §30.6): your six keys as the hero (ON YOUR KEYS, energy at the right), then every power
 * you own, then a card for the one picked or under the cursor. Pick a power (click), then press the key it should go on
 * (Z X C V, R, Alt: your own bindings) or click a key; the keys it fits light amber. Clicking a key first picks up the
 * power on it; the same key again clears it. Esc lets go of a pick before it closes the band.
 */
final class PowersSection implements StatusSections.Section {
    private static final int KEY = 30, KEY_GAP = 6, KEY_SPLIT = 10;
    private static final int CELL = 22, PAD = 3;
    private static final int KEYCAP = 9;

    private static @Nullable String selected;
    // the card's height fits the longest description you own, so the page never jumps as you hover
    private static int cardFor = -1, cardOwned = -1, cardH;

    @Override
    public String title() {
        return "ON YOUR KEYS";
    }

    @Override
    public int order() {
        return 0; // first on the Powers page
    }

    @Override
    public boolean visible() {
        Loadout l = ClientPowers.loadout();
        return l != null && !l.owned().isEmpty();
    }

    @Override
    public String note() {
        Loadout l = ClientPowers.loadout();
        if (l == null) return "";
        return String.format(Locale.ROOT, "ENERGY %d / %d · %.1f A SECOND", (int) ClientPowers.energy(), (int) l.maxEnergy(), l.regenPerTick() * 20);
    }

    @Override
    public int noteColour() {
        return 0xFF000000 | Glass.EDGE;
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

    /** Key s's left edge: the four action keys, a gap, then R and Alt. */
    private static int keyX(int x, int s) {
        return x + s * (KEY + KEY_GAP) + (s >= Loadout.ULTIMATE ? KEY_SPLIT : 0);
    }

    private static int keysH() {
        return KEY + 3 + KEYCAP;
    }

    private static int gridTop() {
        return keysH() + 8 + 12;
    }

    private static int gridH(int n, int width) {
        int rows = (n + columns(width) - 1) / columns(width);
        return rows * (CELL + PAD);
    }

    private static int cardH(Loadout l, Font font, int width) {
        int n = l.owned().size();
        if (cardFor != width || cardOwned != n) {
            int lines = 2;
            for (PowerType t : owned(l)) lines = Math.max(lines, font.split(Component.literal(t.description), width - 14).size());
            cardH = 40 + lines * 10 + 12;
            cardFor = width;
            cardOwned = n;
        }
        return cardH;
    }

    @Override
    public int height(int width) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return 0;
        return gridTop() + gridH(owned(l).size(), width) + 6 + cardH(l, Minecraft.getInstance().font, width);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return;
        Font font = Minecraft.getInstance().font;
        long now = ClientPowers.now();
        PowerType hovered = null;
        PowerType pick = PowerRegistry.get(selected == null ? "" : selected);

        // the hero: your six keys, the power on each, and your own binding under it
        for (int s = 0; s < Loadout.SLOTS; s++) {
            int kx = keyX(x, s);
            PowerType t = PowerRegistry.get(l.slot(s));
            boolean hover = mouseX >= kx && mouseX < kx + KEY && mouseY >= y && mouseY < y + KEY;
            boolean fits = pick != null && pick.fitsSlot(s);
            g.fill(kx, y, kx + KEY, y + KEY, Glass.a(0x05080F, hover ? 0.55f : 0.72f));
            int edge = fits ? Glass.AMBER : s == Loadout.ULTIMATE ? 0xC98A4A : s == Loadout.MOVEMENT ? 0xD05A5A : 0x5A7090;
            g.outline(kx, y, KEY, KEY, Glass.a(edge, fits || hover ? 1f : 0.8f));
            if (fits) corners(g, kx - 2, y - 2, KEY + 4, KEY + 4, Glass.AMBER);
            if (t != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), kx + 3, y + 3, KEY - 6, KEY - 6);
                if (l.cooldownLeft(t.id.toString(), now) > 0) g.fill(kx + 3, y + 3, kx + KEY - 3, y + KEY - 3, 0x80000000);
                if (hover) hovered = t;
            }
            keycap(g, font, PowerHud.keyLabel(s), kx + KEY / 2, y + KEY + 3, fits);
        }

        // every power you own: level pips, mastery under them, a dot when it's on a key (blue) or always on (green)
        List<PowerType> list = owned(l);
        int cols = columns(width);
        int top = y + gridTop();
        label(g, font, "YOUR POWERS · " + list.size(), x, top - 12, width);
        for (int i = 0; i < list.size(); i++) {
            PowerType t = list.get(i);
            int cx = x + (i % cols) * (CELL + PAD);
            int cy = top + (i / cols) * (CELL + PAD);
            String id = t.id.toString();
            boolean sel = id.equals(selected);
            boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            if (hover) hovered = t;
            g.fill(cx, cy, cx + CELL, cy + CELL, sel ? Glass.a(0x3A2A10, 0.85f) : Glass.a(0x05080F, hover ? 0.5f : 0.66f));
            g.outline(cx, cy, CELL, CELL, sel ? 0xFF000000 | Glass.AMBER : 0xFF000000 | darken(t.family.colour));
            g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), cx + 3, cy + 3, 16, 16);
            Loadout.Owned o = l.get(id);
            int level = o == null ? 1 : o.level();
            for (int p = 0; p < level; p++) g.fill(cx + 2 + p * 3, cy + CELL - 4, cx + 4 + p * 3, cy + CELL - 2, 0xFF000000 | Glass.AMBER);
            int barW = CELL - 4;
            g.fill(cx + 2, cy + CELL - 1, cx + 2 + barW, cy + CELL, 0xFF1A2230);
            int filled = Math.round(barW * l.masteryProgress(id));
            if (filled > 0) g.fill(cx + 2, cy + CELL - 1, cx + 2 + filled, cy + CELL, level >= Loadout.MAX_LEVEL ? 0xFF000000 | Glass.AMBER : 0xFF9AD4FF);
            if (l.slotOf(id) >= 0) g.fill(cx + CELL - 4, cy + 1, cx + CELL - 1, cy + 4, 0xFF9AD4FF);
            if (t.isPassive()) g.fill(cx + CELL - 4, cy + 1, cx + CELL - 1, cy + 4, 0xFF000000 | Glass.GOOD);
            if (l.cooldownLeft(id, now) > 0) g.fill(cx + 3, cy + 3, cx + 19, cy + 19, 0x80000000);
        }

        // the card: the power picked, else the one under the cursor
        PowerType show = hovered != null ? hovered : pick;
        int dy = top + gridH(list.size(), width) + 6;
        card(g, font, l, show, pick, x, dy, width, cardH(l, font, width));
    }

    /** The power's card: amber bar when picked, its icon, name, what kind it is, mastery, what it does, and what to press. */
    private static void card(GuiGraphicsExtractor g, Font font, Loadout l, @Nullable PowerType show, @Nullable PowerType pick, int x, int y, int w, int h) {
        boolean picked = show != null && show == pick;
        g.fill(x, y, x + w, y + h, Glass.a(0x05080F, 0.55f));
        g.fill(x, y, x + 2, y + h, Glass.a(picked ? Glass.AMBER : Glass.EDGE, picked ? 1f : 0.45f));
        g.fill(x + 2, y, x + w, y + 1, Glass.a(picked ? Glass.AMBER : Glass.EDGE, 0.25f));
        if (show == null) {
            text(g, font, "Click a power to pick it, then press the key it should go on.", x + 8, y + 8, 1f, Glass.TEXT, w - 14);
            text(g, font, "Passives (the green dot) are always on.", x + 8, y + 30, StatusSections.small(), Glass.LABEL, w - 14);
            return;
        }
        Loadout.Owned o = l.get(show.id.toString());
        int level = o == null ? 1 : o.level();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(show), x + 8, y + 6, 24, 24);
        int tx = x + 38;
        g.text(font, Component.literal(show.name), tx, y + 6, Glass.a(picked ? Glass.AMBER : show.family.colour, 1f), false);
        String variant = o == null || o.variant() == 0 ? "" : " · " + show.variant(o.variant()).name();
        String slot = switch (show.slot) {
            case ACTIVE -> "ACTIVE";
            case ULTIMATE -> "ULTIMATE";
            case MOVEMENT -> "MOVEMENT";
            case PASSIVE -> "PASSIVE";
        };
        String meta = show.family.label.toUpperCase(Locale.ROOT) + " · " + slot + variant.toUpperCase(Locale.ROOT)
                + (show.isPassive() ? "" : " · " + (int) show.cost + " ENERGY · " + trim(show.cooldown / 20.0) + " S");
        text(g, font, meta, tx, y + 17, StatusSections.micro(), Glass.LABEL, w - 44);
        // mastery as segments, then in words
        int segs = 12, filled = level >= Loadout.MAX_LEVEL ? segs : Math.round(segs * l.masteryProgress(show.id.toString()));
        for (int i = 0; i < segs; i++)
            g.fill(tx + i * 4, y + 25, tx + i * 4 + 3, y + 28, i < filled ? 0xFF000000 | Glass.AMBER : Glass.a(Glass.EDGE, 0.18f));
        text(g, font, masteryLine(level, l.masteryProgress(show.id.toString()), show), tx + segs * 4 + 4, y + 24, StatusSections.small(), 0x9AD4FF, w - 44 - segs * 4 - 4);
        int dy = text(g, font, show.description, x + 8, y + 36, 1f, Glass.TEXT, w - 14);
        String hint = hint(show, pick);
        if (!hint.isEmpty()) text(g, font, hint, x + 8, dy + 3, StatusSections.small(), Glass.AMBER, w - 14);
    }

    /** What to press now, in plain words. */
    private static String hint(PowerType show, @Nullable PowerType pick) {
        if (show.isPassive()) return "A passive is always on: it needs no key.";
        if (show != pick) return "Click it to pick it.";
        return switch (show.slot) {
            case ULTIMATE -> "Press " + PowerHud.keyLabel(Loadout.ULTIMATE) + " to put it on that key.";
            case MOVEMENT -> "Press " + PowerHud.keyLabel(Loadout.MOVEMENT) + " to put it on that key.";
            default -> "Press " + PowerHud.keyLabel(0) + ", " + PowerHud.keyLabel(1) + ", " + PowerHud.keyLabel(2) + " or "
                    + PowerHud.keyLabel(3) + " to put it on that key.";
        };
    }

    /** "Mastery 34% to level 4", or "Mastered". */
    private static String masteryLine(int level, float progress, PowerType t) {
        if (level >= Loadout.MAX_LEVEL) return "Mastered";
        return "Mastery " + (int) Math.floor(100 * progress) + "% to level " + (level + 1);
    }

    // ---- drawing helpers ------------------------------------------------------------------------------------------

    /** Text at a scale, wrapped to {@code w}; returns the y under it. */
    private static int text(GuiGraphicsExtractor g, Font font, String s, int x, int y, float scale, int rgb, int w) {
        List<FormattedCharSequence> lines = font.split(Component.literal(s), Math.max(10, Math.round(w / scale)));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), 0, i * 10, Glass.a(rgb, 1f), false);
        g.pose().popMatrix();
        return y + Math.round(lines.size() * 10 * scale);
    }

    /** A label in small capitals with a hairline to the edge (as the band's section labels). */
    private static void label(GuiGraphicsExtractor g, Font font, String s, int x, int y, int w) {
        float m = StatusSections.micro();
        g.pose().pushMatrix();
        g.pose().translate(x, y + 2);
        g.pose().scale(m, m);
        g.text(font, s, 0, 0, Glass.a(Glass.LABEL, 1f), false);
        g.pose().popMatrix();
        int lx = Math.round(x + font.width(s) * m + 4);
        if (lx < x + w - 4) g.fill(lx, y + 4, x + w, y + 5, Glass.a(Glass.EDGE, 0.22f));
    }

    /** Your binding as a small key of glass under a socket. */
    private static void keycap(GuiGraphicsExtractor g, Font font, String key, int cx, int y, boolean lit) {
        float m = StatusSections.micro();
        int w = Math.max(KEYCAP + 2, Math.round(font.width(key) * m) + 6);
        int x = cx - w / 2;
        g.fill(x, y, x + w, y + KEYCAP, Glass.a(0x05080F, 0.7f));
        g.outline(x, y, w, KEYCAP, Glass.a(lit ? Glass.AMBER : Glass.EDGE, lit ? 1f : 0.5f));
        g.pose().pushMatrix();
        g.pose().translate(cx - font.width(key) * m / 2f, y + (KEYCAP - 8 * m) / 2f);
        g.pose().scale(m, m);
        g.text(font, key, 0, 0, Glass.a(lit ? Glass.AMBER : Glass.TEXT, 1f), false);
        g.pose().popMatrix();
    }

    /** Four corner ticks round a box: the keys a picked power fits. */
    private static void corners(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb) {
        int c = Glass.a(rgb, 1f), t = 4;
        g.fill(x, y, x + t, y + 1, c);
        g.fill(x, y, x + 1, y + t, c);
        g.fill(x + w - t, y, x + w, y + 1, c);
        g.fill(x + w - 1, y, x + w, y + t, c);
        g.fill(x, y + h - 1, x + t, y + h, c);
        g.fill(x, y + h - t, x + 1, y + h, c);
        g.fill(x + w - t, y + h - 1, x + w, y + h, c);
        g.fill(x + w - 1, y + h - t, x + w, y + h, c);
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static int darken(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 3 / 5;
        int gg = (rgb >> 8 & 0xFF) * 3 / 5;
        int b = (rgb & 0xFF) * 3 / 5;
        return r << 16 | gg << 8 | b;
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    @Override
    public boolean key(KeyEvent e) {
        if (selected == null) return false;
        if (e.key() == InputConstants.KEY_ESCAPE) { // let go of the pick before the band closes
            selected = null;
            click();
            return true;
        }
        for (int s = 0; s < Loadout.SLOTS; s++) {
            if (!ClientCasting.key(s).matches(e)) continue;
            equip(s);
            return true;
        }
        return false;
    }

    /** Puts the picked power on key s, or says why not. */
    private static void equip(int s) {
        Loadout l = ClientPowers.loadout();
        PowerType t = PowerRegistry.get(selected == null ? "" : selected);
        if (l == null || t == null) return;
        if (selected.equals(l.slot(s))) {
            ClientPacketDistributor.sendToServer(new PowerPayloads.Equip(s, ""));
            selected = null;
            click();
        } else if (t.fitsSlot(s)) {
            ClientPacketDistributor.sendToServer(new PowerPayloads.Equip(s, selected));
            selected = null;
            StatusSections.cue(StatusSections.Cue.CONFIRM);
        } else {
            ClientPowers.flash(s, "That power doesn't fit this key");
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return false;
        for (int s = 0; s < Loadout.SLOTS; s++) {
            int kx = keyX(x, s);
            if (mouseX < kx || mouseX >= kx + KEY || mouseY < y || mouseY >= y + KEY + 3 + KEYCAP) continue;
            if (selected == null) {
                String current = l.slot(s);
                if (!current.isEmpty()) {
                    selected = current;
                    click();
                }
                return true;
            }
            equip(s);
            return true;
        }
        List<PowerType> list = owned(l);
        int cols = columns(width);
        int top = y + gridTop();
        for (int i = 0; i < list.size(); i++) {
            int cx = x + (i % cols) * (CELL + PAD);
            int cy = top + (i / cols) * (CELL + PAD);
            if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
                PowerType t = list.get(i);
                String id = t.id.toString();
                selected = t.isPassive() || id.equals(selected) ? null : id;
                click();
                StatusSections.narrate(t.name + ". " + t.description + " " + hint(t, PowerRegistry.get(selected == null ? "" : selected)));
                return true;
            }
        }
        return false;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
