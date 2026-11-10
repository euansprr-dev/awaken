package dev.awaken.holo.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.BandPlaces.Place;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.holo.ui.HoloTimeline;
import dev.awaken.holo.ui.HoverTick;
import dev.awaken.holo.ui.StageLayout;
import dev.awaken.progression.Progression;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.progression.client.StatusSections.Tab;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Journal;
import dev.awaken.signal.client.QuietScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The band's stage (the K menu, SPEC §57): the band's glass fills the view on the one sheet ({@link Sheet}), with the six
 * places down the wrist edge, the open place on the stage and one bar of keys under it. The glass unfolds from the wrist as
 * a 3D panel and lands on the plate's rectangle, where this flat face takes over (as the bag's does). Each place draws
 * itself ({@link BandPlaces}); a page whose module hasn't moved yet shows its old sections ({@link LegacyPlace}).
 * <p>
 * Keys, the same on every AWAKEN screen: Esc one step back, Tab the next thing, Q and E the place beside this one, 1 to 6 a
 * place, K shuts it. The world keeps running and toasts wait ({@link QuietScreen}).
 */
public final class BandStage extends Screen implements QuietScreen {
    /** The light of the open place (the System's blue, brighter). */
    private static final int HOT = 0xC4E6FF;
    /** Where each place was left, for the session: K opens on Status, a place opens as you left it. */
    private static final LegacyPlace[] LEGACY = new LegacyPlace[Tab.values().length];

    private final HoloPanel panel;
    private final Hot hot = new Hot();
    private final HoverTick hover = new HoverTick();
    private final List<Kit.Hint> left = new ArrayList<>(), right = new ArrayList<>();
    private Sheet sheet;
    private Tab tab;
    private String[] focus;
    private long lastNanos;
    private float printed = 1F, mx = -10000, my = -10000;

    public BandStage(Tab open, String... focus) {
        super(Component.translatable("screen.awaken.status"));
        tab = open;
        this.focus = focus;
        panel = HoloClient.statusPanel();
    }

    /** The place page {@code t} shows: its module's own, or its old sections on the stage. */
    static Place place(Tab t) {
        Place p = BandPlaces.of(t.ordinal());
        if (p != null) return p;
        LegacyPlace l = LEGACY[t.ordinal()];
        if (l == null) LEGACY[t.ordinal()] = l = new LegacyPlace(t);
        return l;
    }

    @Override
    protected void init() {
        sheet = new Sheet(font);
        int[] r = StageLayout.plateOnScreen(width, height);
        HoloClient.openStagePanel(r[0], r[1], r[2], r[3]);
        if (focus != null) {
            place(tab).opened(focus);
            focus = null;
        }
    }

    // ---- how far along: the 3D panel first, then this face -----------------------------------------------------------

    private static boolean threeD() {
        return HoloPipelines.ok() && !HoloConfig.reducedMotion;
    }

    /** This face's opacity: up as the 3D panel lands (or a plain fade without it). */
    private float faceAlpha() {
        HoloTimeline t = panel.timeline;
        if (!t.active()) return 1F;
        return threeD() ? t.landed() : t.glass(true);
    }

    private boolean contentShown() {
        return faceAlpha() >= .5F;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // no blur or dim of the game's own: the world stays in view behind the glass (Lens dims it through UiHooks.focus)
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0F : Math.min(.1F, (now - lastNanos) / 1e9F);
        lastNanos = now;
        // the 3D panel carries bare glass while it unfolds and folds: this face holds the words
        if (threeD() && (panel.fade > 0F || !panel.timeline.fullyOpen())) panel.canvas.begin(panel.w, panel.h);
        Tab asked = StatusSections.takeRequest();
        if (asked != null) select(asked);
        float k = faceAlpha();
        Sheet s = sheet;
        s.sizeStep = HoloConfig.bandSize.step;
        s.begin(g, width, height);
        boolean shown = contentShown();
        mx = shown ? s.sx(mouseX) : -10000;
        my = shown ? s.sy(mouseY) : -10000;
        hot.clear();
        veil(s, k);
        plate(s, g, k);
        if (shown) {
            rail(s, dt);
            head(s);
            Place p = place(tab);
            printed = HoloConfig.reducedMotion ? 1F : Math.min(1F, printed + dt / .22F);
            // a new place prints in behind the scan line: nothing below the line yet
            float line = StageLayout.STAGE_Y0 + (StageLayout.PLATE_Y1 - StageLayout.STAGE_Y0) * HoloTimeline.easeOut(printed);
            boolean clip = printed < 1F;
            if (clip) g.enableScissor(Math.round(StageLayout.PLATE_X0), Math.round(StageLayout.STAGE_Y0) - 8, Math.round(StageLayout.PLATE_X1), Math.round(line));
            p.draw(s, hot, mx, my, dt);
            if (clip) {
                g.disableScissor();
                s.fill(StageLayout.PLATE_X0 + 8, line - 2, StageLayout.PLATE_X1 - 8, line, Glass.a(HOT, .7F * (1F - printed)));
            }
            keys(s, p);
        }
        s.end();
    }

    /** The world steps back where the words are: under the bar of keys and behind the rail. */
    private static void veil(Sheet s, float k) {
        s.gradient(0, Sheet.H - 120, Sheet.W, Sheet.H, Sheet.argb(0x05080F, 0F), Sheet.argb(0x05080F, .86F * k));
        for (int i = 0; i < 8; i++) s.fill(i * 24, 0, (i + 1) * 24, Sheet.H - 120, Sheet.argb(0x05080F, .72F * k * (1F - i / 8F)));
    }

    /** The band's glass, as the bag draws it, three sheet pixels to one of its units; solid without the holo shaders. */
    private static void plate(Sheet s, GuiGraphicsExtractor g, float k) {
        float x0 = StageLayout.PLATE_X0, y0 = StageLayout.PLATE_Y0, x1 = StageLayout.PLATE_X1, y1 = StageLayout.PLATE_Y1;
        if (HoloGuiLook.INSTANCE.enabled() && !s.bright) {
            g.pose().pushMatrix().scale(StageLayout.GLASS_UNIT, StageLayout.GLASS_UNIT);
            HoloGuiLook.INSTANCE.panel(g, Math.round(x0 / StageLayout.GLASS_UNIT), Math.round(y0 / StageLayout.GLASS_UNIT),
                    Math.round((x1 - x0) / StageLayout.GLASS_UNIT), Math.round((y1 - y0) / StageLayout.GLASS_UNIT), k);
            g.pose().popMatrix();
            // a denser navy than the bag's: this glass is read over a whole bright sky
            s.gradient(x0, y0, x1, y1, Sheet.argb(Glass.GLASS_TOP, .50F * k), Sheet.argb(Glass.GLASS, .58F * k));
        } else {
            s.gradient(x0, y0, x1, y1, Sheet.argb(Glass.GLASS_TOP, (s.bright ? 1F : .94F) * k), Sheet.argb(Glass.GLASS, (s.bright ? 1F : .94F) * k));
            s.outline(x0, y0, x1, y1, 2, Glass.a(s.bright ? HOT : Glass.EDGE, (s.bright ? 1F : .5F) * k));
        }
    }

    /** You, then the six places down the wrist edge, the open one lit; a glint on one that waits on you. */
    private void rail(Sheet s, float dt) {
        var mc = minecraft;
        if (mc.player != null) {
            var profile = Progression.profile(mc.player);
            String lv = String.valueOf(profile.level());
            float cx = StageLayout.RAIL_X + StageLayout.NODE_W / 2F;
            s.lit(lv, cx - s.width(lv, 4F) / 2F, 40, 4F, Glass.a(Glass.TITLE, 1F));
            int rank = Math.clamp(profile.rank().ordinal(), 0, Sheet.RANK.length - 1);
            String r = profile.rank().name() + "-rank";
            s.caps(r, cx - s.width(r.toUpperCase(java.util.Locale.ROOT), Sheet.LABEL) / 2F, 80, Glass.a(Sheet.RANK[rank], 1F));
            float frac = (float) Math.clamp(profile.xp() / Math.max(1D, profile.xpToNext()), 0D, 1D);
            s.fill(cx - 36, 100, cx + 36, 104, Sheet.argb(0x000000, .5F));
            s.fill(cx - 36, 100, cx - 36 + 72 * frac, 104, Glass.a(Glass.GOOD, 1F));
        }
        Tab[] tabs = Tab.values();
        int over = HoverTick.NONE;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            float x = StageLayout.RAIL_X, y = StageLayout.nodeY(i), w = StageLayout.NODE_W, h = StageLayout.NODE_H;
            boolean on = t == tab, hit = Sheet.in(mx, my, x, y, x + w, y + h);
            if (hit) over = i;
            if (on) {
                for (int j = 0; j < 6; j++) s.fill(x + j * w / 6F, y, x + (j + 1) * w / 6F, y + h, Glass.a(Glass.EDGE, .18F * (1F - j / 6F)));
                s.fill(x - 8, y + 8, x - 4, y + h - 8, Glass.a(HOT, 1F));
                s.glow(x - 8, y + 8, x - 4, y + h - 8, Glass.EDGE, .6F);
            } else if (hit) {
                s.fill(x, y, x + w, y + h, Glass.a(Glass.EDGE, .08F));
            }
            int ink = on || hit ? Glass.a(on ? HOT : Glass.TITLE, 1F) : Glass.a(s.label, .8F);
            String[] rows = HoloStatusScreen.ICONS[i];
            float ix = x + (w - 48) / 2F, iy = y + 10;
            if (on) s.glow(ix + 8, iy + 8, ix + 40, iy + 40, Glass.EDGE, .5F);
            for (int ry = 0; ry < rows.length; ry++)
                for (int rx = 0; rx < rows[ry].length(); rx++)
                    if (rows[ry].charAt(rx) == '#') s.fill(ix + rx * 4, iy + ry * 4, ix + rx * 4 + 4, iy + ry * 4 + 4, ink);
            String label = t.label().toUpperCase(java.util.Locale.ROOT);
            s.text(label, x + (w - s.width(label, Sheet.LABEL)) / 2F, y + 68, Sheet.LABEL, on ? Glass.a(Glass.TITLE, 1F) : Glass.a(s.label, .95F));
            s.text(String.valueOf(i + 1), x + 6, y + 6, Sheet.LABEL, Glass.a(s.dim, 1F));
            int glint = glint(t);
            if (glint != 0) Kit.glint(s, x + w - 18, y + 14, glint);
            int index = i;
            hot.add(x, y, x + w, y + h, () -> select(tabs[index]));
        }
        if (hover.update(over, dt * 1000F, false)) HoloAudio.hover();
    }

    /** What waits on a place: points to spend on Status (amber), new steps on Tasks and new Journal lines (light). */
    private int glint(Tab t) {
        var mc = minecraft;
        if (t == Tab.STATUS && mc.player != null && Progression.profile(mc.player).unspent() > 0) return Glass.AMBER;
        if (t == Tab.TASKS && dev.awaken.core.api.UiHooks.tasksNew() > 0) return HOT;
        if (t == Tab.JOURNAL && Journal.unread() > 0) return HOT;
        return 0;
    }

    /** The open place's name; its chip of context at the right. */
    private void head(Sheet s) {
        s.lit(tab.label().toUpperCase(java.util.Locale.ROOT), StageLayout.STAGE_X0, StageLayout.PLATE_Y0 + 36, Sheet.DISPLAY, Glass.a(Glass.TITLE, 1F));
        String chip = place(tab).chip();
        if (chip == null || chip.isEmpty()) return;
        float w = 24 + s.width(chip.toUpperCase(java.util.Locale.ROOT), Sheet.LABEL);
        Kit.chip(s, net.minecraft.world.item.ItemStack.EMPTY, chip, StageLayout.STAGE_X1 - w, StageLayout.PLATE_Y0 + 44, Glass.LANTERN);
    }

    /** The bar of keys: Esc and the place's own at the left; the places and K at the right. */
    private void keys(Sheet s, Place p) {
        left.clear();
        right.clear();
        left.add(Kit.Hint.of(p.backWords(), "Esc"));
        p.hints(left);
        right.add(Kit.Hint.of("Place", "Q", "E"));
        right.add(Kit.Hint.of("Close", AwakenKeys.STATUS.getTranslatedKeyMessage().getString()));
        Kit.hints(s, left, right, StageLayout.PLATE_X0, StageLayout.PLATE_X1, StageLayout.HINT_Y);
    }

    /** Shows a place; a page that opens something of its own (the Map: the Atlas) shuts the band and opens it. */
    private void select(Tab t) {
        StatusSections.PageAction action = StatusSections.action(t);
        if (action != null) {
            if (minecraft != null) minecraft.gui.setScreen(null);
            action.open().run();
            return;
        }
        if (t == tab) return;
        StatusSections.cue(StatusSections.Cue.PAGE);
        StatusSections.narrate(t.label() + " page");
        tab = t;
        printed = 0F;
        place(t).opened();
    }

    /** The open place (the autopilot logs it). */
    public Tab tab() {
        return tab;
    }

    /** For the autopilot: a key as the player's, or a click at a sheet point. */
    public boolean autopilotClick(float x, float y) {
        return hot.click(x, y);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!contentShown() || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        hot.click(sheet.sx(event.x()), sheet.sy(event.y()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        place(tab).released();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (contentShown()) place(tab).scroll(sheet.sx(x), sheet.sy(y), scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.STATUS.matches(event)) {
            if (!panel.timeline.fullyOpen()) panel.timeline.skip();
            else onClose();
            return true;
        }
        Place p = place(tab);
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (!p.back()) onClose();
            return true;
        }
        if (p.key(event)) return true;
        Tab keyed = StatusSections.pageForKey(event);
        if (keyed != null) {
            select(keyed);
            return true;
        }
        Tab[] tabs = Tab.values();
        if (event.key() == InputConstants.KEY_TAB) {
            p.next(event.hasShiftDown() ? -1 : 1);
            return true;
        }
        if (event.key() == InputConstants.KEY_Q || event.key() == InputConstants.KEY_E) {
            // the place beside this one; the Map has its own key and its own screen
            int n = tab.ordinal(), step = event.key() == InputConstants.KEY_Q ? tabs.length - 1 : 1;
            do n = (n + step) % tabs.length;
            while (StatusSections.action(tabs[n]) != null && n != tab.ordinal());
            select(tabs[n]);
            return true;
        }
        if (event.key() >= InputConstants.KEY_1 && event.key() < InputConstants.KEY_1 + tabs.length) {
            select(tabs[event.key() - InputConstants.KEY_1]);
            return true;
        }
        return true; // the band takes the keyboard: no key falls through to the game under it
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return place(tab).keyReleased(event) || super.keyReleased(event);
    }

    @Override
    public void removed() {
        if (threeD()) panel.canvas.begin(panel.w, panel.h);
        place(tab).released();
        HoloClient.closeStagePanel();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
