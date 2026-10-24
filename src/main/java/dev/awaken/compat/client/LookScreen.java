package dev.awaken.compat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.compat.Companions;
import dev.awaken.compat.FarView;
import dev.awaken.compat.Look;
import dev.awaken.compat.Look.Tier;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * AWAKEN · LOOK (docs/plan-pack.html, "The one new screen"): three tiers, one click each. A choice sets Lens, Distant
 * Horizons and Iris together ({@link LookSwitch}); the lines under the tiers say what each mod got and why Sodium
 * keeps its own settings. The frame-time line is the last minute's p50 and p99, green while it fits the tier's
 * budget. The world keeps running behind the glass, so a choice shows at once. Keys: 1 · 2 · 3, Esc.
 */
public final class LookScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final Tier[] TIERS = Tier.values();
    private static final int CARD_GAP = 6, PAD = 8;

    private final @Nullable Screen parent;
    private int px, pw, cw, ch, cardsY;
    private final List<List<FormattedCharSequence>> blurbs = new java.util.ArrayList<>();
    private Look.Availability ultra = new Look.Availability(false, "");
    // the frame-time line, refreshed twice a second
    private final float[] stats = new float[4];
    private String frameLabel = "LAST 60 S", frameValue = "measuring…";
    private int frameColour = Glass.LABEL, clock;
    // a line under the mods: why Ultra is off, or what the last choice did
    private String note = "";
    private int noteColour = Glass.LABEL;
    private int hovered = -1;

    public LookScreen(@Nullable Screen parent) {
        super(Component.literal("AWAKEN · LOOK"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(396, width - 12);
        px = (width - pw) / 2;
        cw = (pw - 2 * PAD - 2 * CARD_GAP) / TIERS.length;
        blurbs.clear();
        int lines = 1;
        for (Tier t : TIERS) {
            List<FormattedCharSequence> l = font.split(Component.literal(t.blurb), cw - 10);
            blurbs.add(l);
            lines = Math.max(lines, l.size());
        }
        ch = 6 + 10 + 3 + lines * 10 + 4 + 10 + 4;
        ultra = LookSwitch.ultra();
        refreshFrames();
    }

    @Override
    public void tick() {
        if (++clock % 10 == 0) refreshFrames();
    }

    private void refreshFrames() {
        var w = minecraft.getWindow();
        String res = w.getWidth() + "×" + w.getHeight();
        if (LookClient.FRAMES.percentiles(60_000_000_000L, 30, stats)) {
            frameLabel = "LAST " + Math.max(1, Math.round(stats[2])) + " S · " + res;
            frameValue = String.format(Locale.ROOT, "p50 %.1f ms · p99 %.1f ms", stats[0], stats[1]);
            Tier t = LookSwitch.current();
            Boolean fits = t == null ? null : Look.fits(t, stats[0], stats[1]);
            frameColour = fits == null ? Glass.TEXT : fits ? Glass.GOOD : Glass.AMBER;
        } else {
            frameLabel = "LAST 60 S · " + res;
            frameValue = "measuring…";
            frameColour = Glass.LABEL;
        }
    }

    private int cardX(int i) {
        return px + PAD + i * (cw + CARD_GAP);
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < TIERS.length; i++) {
            if (Glass.inside(mx, my, cardX(i), cardsY, cw, ch)) return i;
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // light: the world behind is the point (a choice shows at once)
        g.fillGradient(0, 0, width, height, 0x30050810, 0x80050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int innerW = pw - 2 * PAD - 4;
        // the cards where they were last frame (the panel only moves when its content grows)
        hovered = cardAt(mouseX, mouseY);
        Ui m = Ui.measure(font, innerW);
        body(m, true);
        int ph = 24 + ch + 8 + m.height() + 18;
        int py = Math.max(6, (height - ph) / 2);
        cardsY = py + 24;
        Glass.panel(g, px, py, pw, ph, Glass.EDGE, 1f);
        g.centeredText(font, title, px + pw / 2, py + 9, Glass.a(Glass.EDGE, 1f));

        Tier current = LookSwitch.current();
        for (int i = 0; i < TIERS.length; i++) card(g, i, TIERS[i], current);

        Ui ui = Ui.draw(g, font, px + PAD + 2, cardsY + ch + 6, innerW, mouseX, mouseY);
        body(ui, false);
        g.centeredText(font, Component.literal("Click a tier or press 1 · 2 · 3      Esc: back"), px + pw / 2, py + ph - 13,
                Glass.a(Glass.LABEL, 0.75f));
    }

    private void card(GuiGraphicsExtractor g, int i, Tier t, @Nullable Tier current) {
        int x = cardX(i), y = cardsY;
        boolean on = t == current, hover = i == hovered, can = t != Tier.ULTRA || ultra.available();
        g.fill(x, y, x + cw, y + ch, on ? Glass.a(0x16233A, 0.96f) : Glass.a(0x1B2635, hover && can ? 0.95f : 0.85f));
        if (on) {
            g.outline(x, y, cw, ch, Glass.a(Glass.GOOD, 1f));
            g.outline(x + 1, y + 1, cw - 2, ch - 2, Glass.a(Glass.GOOD, 0.55f));
        } else {
            g.outline(x, y, cw, ch, hover && can ? Glass.a(Glass.EDGE, 0.9f) : Glass.a(0x4A5D78, 0.9f));
        }
        float dim = can ? 1f : 0.55f;
        g.text(font, t.label, x + 6, y + 6, Glass.a(Glass.TITLE, dim), false);
        String key = String.valueOf(i + 1);
        g.text(font, key, x + cw - 6 - font.width(key), y + 6, Glass.a(Glass.LANTERN, 0.7f * dim), false);
        List<FormattedCharSequence> lines = blurbs.get(i);
        for (int l = 0; l < lines.size(); l++) g.text(font, lines.get(l), x + 6, y + 19 + l * 10, Glass.a(0xA9BBD4, dim), false);
        String status = on ? "● ON" : !can ? "NOT AVAILABLE" : hover ? "CHOOSE" : "";
        int colour = on ? Glass.GOOD : !can ? Glass.AMBER : Glass.EDGE;
        if (!status.isEmpty()) g.text(font, status, x + 6, y + ch - 14, Glass.a(colour, 1f), false);
    }

    /** Everything under the tiers, laid out the same way to measure and to draw. */
    private void body(Ui ui, boolean measuring) {
        ui.rule();
        ui.pair(frameLabel, frameValue, Glass.a(Glass.LABEL, 1f), Glass.a(frameColour, 1f));
        if (LookSwitch.DH) {
            boolean here = minecraft.level == null || FarView.allowed(minecraft.level.dimension().identifier().toString());
            int r = DistantHorizonsLook.radius();
            ui.pair("HORIZON", !here ? "OFF IN THIS PLACE" : r > 0 ? r + " CHUNKS" : "…", Glass.a(Glass.LABEL, 1f), Glass.a(Glass.GOOD, 1f));
        }
        ui.rule();
        Tier current = LookSwitch.current();
        String lens = switch (dev.awaken.core.AwakenConfig.Client.LENS.get()) {
            case OFF -> "off";
            case SWIFT -> "Swift";
            case CINEMATIC -> "Cinematic";
        };
        if (Companions.shaderPackActive()) lens += ", stepping aside for the shaderpack";
        mod(ui, "LENS", lens, Glass.TEXT);
        if (!LookSwitch.DH) {
            mod(ui, "DISTANT HORIZONS", "not installed", Glass.LABEL);
        } else if (LookSwitch.chosen != null) {
            mod(ui, "DISTANT HORIZONS", LookSwitch.dhResult, LookSwitch.dhWarn ? Glass.AMBER : Glass.TEXT);
        } else {
            boolean same = current != null && Look.dhMatches(current, DistantHorizonsLook::current);
            mod(ui, "DISTANT HORIZONS", same ? "set for " + current.pretty() : "its own settings (changed in its menu)", Glass.TEXT);
        }
        mod(ui, "SODIUM", LookSwitch.SODIUM ? "keeps its own settings: no supported way to set them" : "not installed", Glass.LABEL);
        String iris = !Companions.irisLoaded() ? "not installed" : !ultra.available() ? "can't run Ultra here"
                : Companions.irisShadersEnabled() ? "shaders on (your shaderpack)" : "shaders off";
        mod(ui, "IRIS", iris, Companions.irisLoaded() && ultra.available() ? Glass.TEXT : Glass.LABEL);
        boolean why = hovered == 2 && !ultra.available();
        String n = why ? ultra.reason() : note;
        // room for the longer of the two, so hovering Ultra doesn't move the panel
        if (measuring && !ultra.available() && ultra.reason().length() > n.length()) n = ultra.reason();
        if (!n.isEmpty()) {
            ui.gap(3);
            ui.textAtMost(n, Glass.a(why ? Glass.AMBER : noteColour, 1f), 3);
        }
    }

    private static void mod(Ui ui, String label, String value, int colour) {
        ui.pair(label, value, Glass.a(Glass.LABEL, 1f), Glass.a(colour, 1f));
    }

    /** Chooses a tier, as a click on its card would. False when it can't run here (the reason shows instead). */
    public boolean pick(Tier t) {
        if (!LookSwitch.apply(t)) {
            note = ultra.reason();
            noteColour = Glass.AMBER;
            return false;
        }
        note = LookSwitch.DH && LookSwitch.chosen != null && !LookSwitch.dhWarn
                ? "Distant Horizons shows these settings as API LOCK in its own menu until you restart; then they're its own again."
                : "";
        noteColour = Glass.LABEL;
        refreshFrames();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int i = cardAt(event.x(), event.y());
            if (i >= 0) {
                pick(TIERS[i]);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        int i = k == InputConstants.KEY_1 ? 0 : k == InputConstants.KEY_2 ? 1 : k == InputConstants.KEY_3 ? 2 : -1;
        if (i >= 0) {
            pick(TIERS[i]);
            return true;
        }
        if (LookClient.LOOK.matches(event) && parent == null) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
