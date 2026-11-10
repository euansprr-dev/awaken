package dev.awaken.powers.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.ladder.PowerLadder;
import dev.awaken.progression.client.SectionHost;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * K, Powers, on the band's stage (SPEC §57.5): your six keys with the power on each, every power you own in one grid with
 * no scrolling (thirteen across), and the panel at the right reading the power you point at, else the one you picked, else
 * the one on your first key, so it is never an empty box. Pick a power, then press the key it should go on (your own
 * bindings) or click a key; Esc lets go of the pick. What your body does, your crafts and the Veil follow under the grid
 * as they were written, until they move. The picking, the keys and the server's say are the old page's own.
 */
final class PowersPlace implements BandPlaces.Place {
    private static final float X0 = BandPlaces.X0, Y0 = BandPlaces.Y0, PANEL = BandPlaces.X1 - 488F;
    private static final float KEY = 96F, CELL = 64F, STEP = 72F;
    private static final int COLS = 13;

    private final SectionHost host = new SectionHost();
    /** The power Tab has walked to (its place in the grid), or -1. */
    private int walked = -1;
    private @Nullable PowerType pointed;

    @Override
    public void opened(String... focus) {
        host.focus(focus);
        walked = -1;
    }

    private static float keyX(int s) {
        return X0 + s * (KEY + 14) + (s >= Loadout.ULTIMATE ? 22 : 0);
    }

    private static void icon(Sheet s, PowerType t, float x, float y, float size) {
        s.g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), Math.round(x), Math.round(y), Math.round(size), Math.round(size));
    }

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        Loadout l = ClientPowers.loadout();
        if (l == null || l.owned().isEmpty()) {
            s.lit("No powers yet.", X0, Y0 + 220, Sheet.TITLE, Glass.a(Glass.TITLE, 1F));
            s.text("Your first one comes soon after you awaken.", X0, Y0 + 262, Sheet.BODY, Glass.a(s.label, 1F));
            host.draw(s, hot, StatusSections.Tab.POWERS, sec -> sec.order() != 0, X0, Y0 + 320, PANEL - 24, BandPlaces.Y1, mx, my, "", Sheet.calm());
            return;
        }
        long now = ClientPowers.now();
        PowerType pick = PowerRegistry.get(PowersSection.selected == null ? "" : PowersSection.selected);
        pointed = null;
        // your six keys, the power on each, and your own binding under it
        s.caps("On your keys", X0, Y0 + 2, Glass.a(s.label, 1F));
        float ex = X0 + s.width("ON YOUR KEYS", Sheet.LABEL) + 18;
        float full = (float) Math.clamp(ClientPowers.energy() / Math.max(1, l.maxEnergy()), 0D, 1D);
        s.fill(ex, Y0 + 3, ex + 300, Y0 + 13, Sheet.argb(0x05080F, .6F));
        s.fill(ex, Y0 + 3, ex + 300 * full, Y0 + 13, Glass.a(Glass.EDGE, 1F));
        s.text(String.format(Locale.ROOT, "%d energy · %.1f a second", (int) ClientPowers.energy(), l.regenPerTick() * 20), ex + 314, Y0 + 2, Sheet.LABEL,
                Glass.a(s.label, 1F));
        float ky = Y0 + 30;
        for (int i = 0; i < Loadout.SLOTS; i++) {
            float kx = keyX(i);
            PowerType t = PowerRegistry.get(l.slot(i));
            boolean over = Sheet.in(mx, my, kx, ky, kx + KEY, ky + KEY + 34), fits = pick != null && pick.fitsSlot(i);
            Kit.well(s, kx, ky, kx + KEY, ky + KEY, fits ? Kit.Lit.WAITS : Kit.Lit.KNOWN, over);
            if (t != null) {
                icon(s, t, kx + 12, ky + 12, 72);
                if (l.cooldownLeft(t.id.toString(), now) > 0) s.fill(kx + 12, ky + 12, kx + 84, ky + 84, 0x80000000);
                if (over) pointed = t;
            }
            String cap = PowerHud.keyLabel(i);
            Kit.key(s, cap, kx + KEY / 2F - Math.max(26, s.width(cap, Sheet.LABEL) + 14) / 2F, ky + KEY + 8, fits);
            int slot = i;
            hot.add(kx, ky, kx + KEY, ky + KEY + 34, () -> clickKey(slot));
        }
        // every power you own
        List<PowerType> list = PowersSection.owned(l);
        float gy = ky + KEY + 72;
        s.caps("Your powers · " + list.size(), X0, gy - 24, Glass.a(s.label, 1F));
        for (int i = 0; i < list.size(); i++) {
            PowerType t = list.get(i);
            float cx = X0 + (i % COLS) * STEP, cy = gy + (i / COLS) * STEP;
            String id = t.id.toString();
            boolean sel = id.equals(PowersSection.selected), over = Sheet.in(mx, my, cx, cy, cx + CELL, cy + CELL);
            if (over) pointed = t;
            Kit.well(s, cx, cy, cx + CELL, cy + CELL, sel ? Kit.Lit.WAITS : i == walked ? Kit.Lit.FOCUS : Kit.Lit.KNOWN, over);
            s.fill(cx, cy, cx + CELL, cy + 2, Glass.a(t.family.colour, sel ? 1F : .75F));
            icon(s, t, cx + 8, cy + 6, 48);
            Loadout.Owned o = l.get(id);
            int level = o == null ? 1 : o.level();
            for (int p = 0; p < level; p++) s.fill(cx + 5 + p * 8, cy + CELL - 12, cx + 11 + p * 8, cy + CELL - 6, Glass.a(Glass.AMBER, 1F));
            float done = level >= Loadout.MAX_LEVEL ? 1F : l.masteryProgress(id);
            s.fill(cx + 4, cy + CELL - 4, cx + CELL - 4, cy + CELL - 2, Sheet.argb(0x1A2230, 1F));
            if (done > 0) s.fill(cx + 4, cy + CELL - 4, cx + 4 + (CELL - 8) * done, cy + CELL - 2, level >= Loadout.MAX_LEVEL ? Glass.a(Glass.AMBER, 1F) : 0xFF9AD4FF);
            if (t.isPassive()) s.fill(cx + CELL - 12, cy + 5, cx + CELL - 5, cy + 12, Glass.a(Glass.GOOD, 1F));
            else if (l.slotOf(id) >= 0) s.fill(cx + CELL - 12, cy + 5, cx + CELL - 5, cy + 12, 0xFF9AD4FF);
            if (l.cooldownLeft(id, now) > 0) s.fill(cx + 8, cy + 6, cx + 56, cy + 54, 0x80000000);
            int index = i;
            hot.add(cx, cy, cx + CELL, cy + CELL, () -> pick(list.get(index), index));
        }
        float below = gy + ((list.size() + COLS - 1) / COLS) * STEP + 14;
        // what your body does, your crafts, the Veil: as they were written, until they move
        if (below + 60 < BandPlaces.Y1) host.draw(s, hot, StatusSections.Tab.POWERS, sec -> sec.order() != 0, X0, below, PANEL - 24, BandPlaces.Y1, mx, my, "", Sheet.calm());
        // the panel: never empty
        PowerType show = pointed != null ? pointed : pick != null ? pick : walked >= 0 && walked < list.size() ? list.get(walked) : first(l, list);
        s.fill(PANEL, Y0, PANEL + 1, BandPlaces.Y1, Glass.a(Glass.EDGE, .2F));
        if (show != null) {
            String id = show.id.toString();
            if (!id.equals(panelFor)) {
                panelFor = id;
                panelScroll = 0;
            }
            panelScroll = Math.clamp(panelScroll, 0F, Math.max(0F, panelTall - (BandPlaces.Y1 - Y0)));
            s.g.enableScissor(Math.round(PANEL) + 2, Math.round(Y0) - 4, Math.round(BandPlaces.X1) + 4, Math.round(BandPlaces.Y1));
            panelTall = panel(s, l, show, pick, Y0 - panelScroll) - (Y0 - panelScroll);
            s.g.disableScissor();
            Kit.scrollbar(s, BandPlaces.X1 + 10, Y0, BandPlaces.Y1, panelScroll, panelTall);
        }
    }

    /** A power's words are never trimmed (SPEC §57.6): if they're taller than the panel, the panel scrolls. */
    private float panelScroll, panelTall;
    private String panelFor = "";

    private static @Nullable PowerType first(Loadout l, List<PowerType> list) {
        for (int i = 0; i < Loadout.SLOTS; i++) {
            PowerType t = PowerRegistry.get(l.slot(i));
            if (t != null) return t;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /** The power in focus: what kind it is, its name, the icon itself, what it does, its numbers, its mastery and its ladder. */
    private static float panel(Sheet s, Loadout l, PowerType show, @Nullable PowerType pick, float y) {
        float x = PANEL + 24, w = BandPlaces.X1 - x;
        boolean picked = show == pick;
        Loadout.Owned o = l.get(show.id.toString());
        int level = o == null ? 1 : o.level();
        String slot = switch (show.slot) {
            case ACTIVE -> "Active";
            case ULTIMATE -> "Ultimate";
            case MOVEMENT -> "Movement";
            case PASSIVE -> "Passive";
        };
        String variant = o == null || o.variant() == 0 ? "" : " · " + show.variant(o.variant()).name();
        s.caps(show.family.label + " · " + slot + variant, x, y, Glass.a(s.label, 1F));
        y = s.lines(show.name, x, y + 26, w, Sheet.TITLE, Glass.a(picked ? Glass.AMBER : show.family.colour, 1F)) - 56;
        Kit.floor(s, x + 72, y + 196, 96);
        icon(s, show, x + 8, y + 66, 128);
        float nx = x + 176;
        if (!show.isPassive()) {
            nx += Kit.number(s, String.valueOf((int) show.cost), "Energy", nx, y + 78, Sheet.TITLE + 1, Glass.TITLE) + 28;
            Kit.number(s, PowersSection.trim(show.cooldown / 20.0) + " s", "Cooldown", nx, y + 78, Sheet.TITLE + 1, Glass.TITLE);
        }
        // mastery as segments, then in words
        float my = y + 150;
        int segs = 12, filled = level >= Loadout.MAX_LEVEL ? segs : Math.round(segs * l.masteryProgress(show.id.toString()));
        for (int i = 0; i < segs; i++) s.fill(x + 176 + i * 18, my, x + 176 + i * 18 + 15, my + 8, i < filled ? Glass.a(Glass.AMBER, 1F) : Glass.a(Glass.EDGE, .18F));
        s.lines(PowersSection.masteryLine(level, l.masteryProgress(show.id.toString()), show), x + 176, my + 16, w - 176, Sheet.LABEL, 0xFF9AD4FF);
        float ty = s.lines(show.description, x, y + 226, w, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 8;
        String hint = PowersSection.hint(show, pick);
        if (!hint.isEmpty()) ty = s.lines(hint, x, ty, w, Sheet.BODY, Glass.a(Glass.AMBER, 1F)) + 10;
        // its ladder: the steps past level 1, the ones you have lit, the next one marked
        if (PowerLadder.entry(show.path) == null) return ty;
        s.caps("Ladder · level " + level + " of " + PowerLadder.TOP, x, ty, Glass.a(s.label, 1F));
        ty += 24;
        for (int lv = 2; lv <= PowerLadder.TOP; lv++) {
            boolean reached = level >= lv, next = lv == level + 1;
            int pip = reached ? Glass.a(show.family.colour, 1F) : next ? 0xFF9AD4FF : Glass.a(Glass.EDGE, .45F);
            if (reached) s.fill(x + 2, ty + 3, x + 12, ty + 13, pip);
            else s.outline(x + 2, ty + 3, x + 12, ty + 13, 2, pip);
            String line = PowerLadder.name(show.path, lv).getString() + (lv == PowerLadder.TOP ? " · Awakened" : "") + ": " + PowerLadder.does(show.path, lv).getString();
            ty = s.lines(line, x + 22, ty, w - 22, Sheet.LABEL, reached ? Glass.a(Glass.TEXT, 1F) : next ? 0xFF9AD4FF : Glass.a(s.label, 1F)) + 6;
        }
        return ty;
    }

    /** A key clicked: with nothing picked it picks what's on it; with a pick it takes it (or says why not). */
    private static void clickKey(int slot) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return;
        if (PowersSection.selected == null) {
            String current = l.slot(slot);
            if (!current.isEmpty()) {
                PowersSection.selected = current;
                PowersSection.click();
            }
            return;
        }
        PowersSection.equip(slot);
    }

    private void pick(PowerType t, int index) {
        String id = t.id.toString();
        walked = index;
        PowersSection.selected = t.isPassive() || id.equals(PowersSection.selected) ? null : id;
        PowersSection.click();
        StatusSections.narrate(t.name + ". " + t.description + " " + PowersSection.hint(t, PowerRegistry.get(PowersSection.selected == null ? "" : PowersSection.selected)));
    }

    @Override
    public void hints(List<Kit.Hint> out) {
        PowerType pick = PowerRegistry.get(PowersSection.selected == null ? "" : PowersSection.selected);
        if (pick != null) {
            switch (pick.slot) {
                case ULTIMATE -> out.add(Kit.Hint.of("Put it on that key", PowerHud.keyLabel(Loadout.ULTIMATE)));
                case MOVEMENT -> out.add(Kit.Hint.of("Put it on that key", PowerHud.keyLabel(Loadout.MOVEMENT)));
                default -> out.add(Kit.Hint.of("Put it on that key", PowerHud.keyLabel(0), PowerHud.keyLabel(1), PowerHud.keyLabel(2), PowerHud.keyLabel(3)));
            }
        } else {
            out.add(Kit.Hint.of("Next power", "Tab"));
            out.add(Kit.Hint.of("Pick it", "Enter"));
        }
        if (host.more() || panelTall > BandPlaces.Y1 - Y0) out.add(Kit.Hint.of("More", "Scroll"));
    }

    @Override
    public boolean key(KeyEvent e) {
        Loadout l = ClientPowers.loadout();
        if (l == null) return false;
        if (PowersSection.selected != null) {
            for (int i = 0; i < Loadout.SLOTS; i++) {
                if (!ClientCasting.key(i).matches(e)) continue;
                PowersSection.equip(i);
                return true;
            }
        }
        List<PowerType> list = PowersSection.owned(l);
        if ((e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER || e.key() == InputConstants.KEY_SPACE)
                && walked >= 0 && walked < list.size()) {
            pick(list.get(walked), walked);
            return true;
        }
        return host.key(e, StatusSections.Tab.POWERS, sec -> sec.order() != 0);
    }

    @Override
    public void next(int step) {
        Loadout l = ClientPowers.loadout();
        if (l == null || l.owned().isEmpty()) return;
        int n = PowersSection.owned(l).size();
        walked = Math.floorMod((walked < 0 ? (step > 0 ? -1 : 0) : walked) + step, n);
        PowerType t = PowersSection.owned(l).get(walked);
        StatusSections.narrate(t.name + ". " + t.description);
    }

    @Override
    public boolean back() {
        if (PowersSection.selected != null) { // let go of the pick before the band shuts
            PowersSection.selected = null;
            PowersSection.click();
            return true;
        }
        return false;
    }

    @Override
    public String backWords() {
        return PowersSection.selected != null ? "Let go of it" : "Close";
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        if (mx >= PANEL) {
            panelScroll -= (float) amount * 40;
            return true;
        }
        return host.scroll(amount);
    }
}
