package dev.awaken.compat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.compat.Companions;
import dev.awaken.compat.FarView;
import dev.awaken.compat.Look;
import dev.awaken.compat.Look.Tier;
import dev.awaken.core.AwakenConfig;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AWAKEN · LOOK (docs/plan-pack.html, "The one new screen"): three tiers, one click each. A choice sets Lens, Distant
 * Horizons and Iris together ({@link LookSwitch}). Under the tiers, four presets ({@link LookPresets}: Performance,
 * Close-up, Balanced, Vista) set the Look, Far Sight's reach, the render distance, the 4K render scale and steady at
 * once, and every one of those is a row of its own that a click changes (right click: back), so all of it shows on one
 * page (Euan, 2026-10-26). The frame-time line is the last minute's p50 and p99, green while it fits the tier's
 * budget. Drawn like the System's own cards: projected glass that scans in while the holo look is on
 * ({@link HudLook}), flat {@link Glass} otherwise. The world keeps running behind it and is never dimmed (this isn't a
 * reading screen), so a choice shows at once. Keys: 1 · 2 · 3, Esc.
 */
public final class LookScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final Tier[] TIERS = Tier.values();
    private static final LookPresets.Preset[] PRESETS = LookPresets.Preset.values();
    // the clickable setting rows, in order
    private static final int S_LOOK = 0, S_FAR = 1, S_DISTANCE = 2, S_SCALE = 3, S_STEADY = 4, S_TEMPORAL = 5, SETTINGS = 6;
    private static final int CARD_GAP = 6, PAD = 8, LINE = 10, TOP = 24, FOOT = 18;
    private static final int TITLE = 0xFFF4F6FB, LABEL = 0xFF8FA7C4, BLURB = 0xFFA9BBD4, DIM = 0xFF6F8299;

    private final @Nullable Screen parent;
    private long openedAt;
    private int px, pw, cw, ch, cardsY;
    private final List<List<FormattedCharSequence>> blurbs = new ArrayList<>(), presetBlurbs = new ArrayList<>();
    private int pcw, pch, presetsY;
    // where each setting row was drawn last frame (y), for clicks; -1 not drawn
    private final int[] settingY = new int[SETTINGS];
    private int hoveredPreset = -1, hoveredSetting = -1, innerX0, innerW0;
    private Look.Availability ultra = new Look.Availability(false, "");
    // the frame-time line, refreshed twice a second
    private final float[] stats = new float[4];
    private String frameLabel = "LAST 60 S", frameValue = "measuring…";
    private int frameColour = Glass.LABEL, clock;
    // under the mods: why Ultra can't run (while it's hovered), or what the last choice did
    private String note = "";
    private int noteColour = Glass.LABEL;
    private int hovered = -1;
    // the rows under the tiers, rebuilt each frame: label, value, colour
    private final List<String[]> rows = new ArrayList<>();
    private final List<Integer> rowColours = new ArrayList<>();

    public LookScreen(@Nullable Screen parent) {
        super(Component.literal("AWAKEN · LOOK"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (openedAt == 0) openedAt = System.nanoTime();
        java.util.Arrays.fill(settingY, -1);
        pw = Math.min(396, width - 12);
        px = (width - pw) / 2;
        cw = (pw - 2 * PAD - 2 * CARD_GAP) / TIERS.length;
        blurbs.clear();
        int lines = 1;
        for (Tier t : TIERS) {
            List<FormattedCharSequence> l = font.split(Component.literal(t.blurb), cw - 12);
            blurbs.add(l);
            lines = Math.max(lines, l.size());
        }
        ch = 6 + LINE + 3 + lines * LINE + 4 + LINE + 4;
        pcw = (pw - 2 * PAD - (PRESETS.length - 1) * CARD_GAP) / PRESETS.length;
        presetBlurbs.clear();
        int plines = 1;
        for (LookPresets.Preset q : PRESETS) {
            List<FormattedCharSequence> l = font.split(Component.literal(q.blurb), pcw - 10);
            presetBlurbs.add(l);
            plines = Math.max(plines, l.size());
        }
        pch = 5 + LINE + 2 + plines * LINE + 3;
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

    private int presetX(int i) {
        return px + PAD + i * (pcw + CARD_GAP);
    }

    private int presetAt(double mx, double my) {
        for (int i = 0; i < PRESETS.length; i++) {
            if (Glass.inside(mx, my, presetX(i), presetsY, pcw, pch)) return i;
        }
        return -1;
    }

    private int settingAt(double mx, double my) {
        if (mx < innerX0 || mx > innerX0 + innerW0) return -1;
        for (int i = 0; i < SETTINGS; i++) if (settingY[i] >= 0 && my >= settingY[i] - 1 && my < settingY[i] + LINE) return i;
        return -1;
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < TIERS.length; i++) {
            if (Glass.inside(mx, my, cardX(i), cardsY, cw, ch)) return i;
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // light, in both looks: the world behind is the point (a choice shows at once)
        g.fillGradient(0, 0, width, height, 0x30050810, 0x78050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        boolean holo = HudLook.holo();
        int innerX = px + PAD + 2, innerW = pw - 2 * PAD - 4;
        // the cards where they were last frame (the panel only moves when its content grows)
        hovered = cardAt(mouseX, mouseY);
        hoveredPreset = presetAt(mouseX, mouseY);
        hoveredSetting = settingAt(mouseX, mouseY);
        innerX0 = innerX;
        innerW0 = innerW;
        buildRows();
        // every tier runs everywhere since Lens 2's Ultra (SPEC §22); the note is the last choice's
        boolean why = false;
        String shown = note, room = note;
        List<FormattedCharSequence> noteLines = shown.isEmpty() ? List.of() : font.split(Component.literal(shown), innerW);
        int noteRoom = room.isEmpty() ? 0 : 3 + Math.min(3, font.split(Component.literal(room), innerW).size()) * LINE;
        // the presets with their heading, a rule, the settings, a rule, a line per row, the note
        int presetsH = LINE + 2 + pch + 4;
        int bodyH = 5 + SETTINGS * LINE + 5 + 5 + rows.size() * LINE + noteRoom;
        int ph = TOP + ch + 6 + presetsH + bodyH + FOOT;
        int py = Math.max(6, (height - ph) / 2);
        cardsY = py + TOP;
        presetsY = cardsY + ch + 6 + LINE + 2;

        float ms = (System.nanoTime() - openedAt) / 1e6f;
        if (holo) {
            float reveal = Math.min(1f, ms / 320f);
            HudLook.card(g, px, py, pw, ph, 0xFF7FC3FF, 1f, reveal, 0f);
            g.enableScissor(px, py, px + pw, py + (int) Math.ceil(ph * (1 - Math.pow(1 - reveal, 3))));
        } else {
            Glass.panel(g, px, py, pw, ph, Glass.EDGE, 1f);
        }
        HudLook.centered(g, font, title.getString(), px + pw / 2, py + 9, Glass.a(Glass.EDGE, 1f), false);

        Tier current = LookSwitch.current();
        for (int i = 0; i < TIERS.length; i++) {
            float r = holo ? Math.min(1f, Math.max(0f, (ms - 160f - i * 70f) / 260f)) : 1f;
            if (r > 0) card(g, i, TIERS[i], current, holo, r);
        }

        LookPresets.Preset preset = LookPresets.current();
        row(g, innerX, innerW, cardsY + ch + 6, "PRESETS", preset == null ? "a custom mix" : preset.label, preset == null ? Glass.LABEL : Glass.GOOD);
        for (int i = 0; i < PRESETS.length; i++) presetChip(g, i, PRESETS[i], PRESETS[i] == preset, holo);
        int y = presetsY + pch + 4;
        y = rule(g, innerX, innerW, y);
        for (int i = 0; i < SETTINGS; i++) {
            settingY[i] = y;
            setting(g, innerX, innerW, y, i);
            y += LINE;
        }
        int frameRows = LookSwitch.DH ? 2 : 1;
        y = rule(g, innerX, innerW, y);
        for (int i = 0; i < rows.size(); i++) {
            if (i == frameRows) y = rule(g, innerX, innerW, y);
            row(g, innerX, innerW, y, rows.get(i)[0], rows.get(i)[1], rowColours.get(i));
            y += LINE;
        }
        if (!noteLines.isEmpty()) {
            y += 3;
            int colour = Glass.a(why ? Glass.AMBER : noteColour, 1f);
            for (int l = 0; l < Math.min(3, noteLines.size()); l++) HudLook.text(g, font, noteLines.get(l), innerX, y + l * LINE, colour, false);
        }
        HudLook.centered(g, font, "Click a tier (1 · 2 · 3) or a preset · click a setting to change it, right click back · Esc",
                px + pw / 2, py + ph - 13, DIM, false);
        if (holo) g.disableScissor();
    }

    private void presetChip(GuiGraphicsExtractor g, int i, LookPresets.Preset p, boolean on, boolean holo) {
        int x = presetX(i), y = presetsY;
        boolean hover = i == hoveredPreset;
        int edge = on ? Glass.a(Glass.GOOD, 1f) : hover ? Glass.a(Glass.EDGE, 1f) : 0xFF4A5D78;
        if (holo) {
            HudLook.card(g, x, y, pcw, pch, edge, 1f, 1f, 0f);
            if (on || hover) HudLook.glow(g, x, y, pcw, pch, edge, on ? 1f : 0.6f);
        } else {
            g.fill(x, y, x + pcw, y + pch, on ? Glass.a(0x16233A, 0.96f) : Glass.a(0x1B2635, hover ? 0.95f : 0.85f));
            g.outline(x, y, pcw, pch, Glass.a(edge, 0.9f));
        }
        HudLook.text(g, font, p.label, x + 5, y + 5, Glass.a(on ? Glass.GOOD : TITLE, 1f), false);
        List<FormattedCharSequence> lines = presetBlurbs.get(i);
        for (int l = 0; l < lines.size(); l++) HudLook.text(g, font, lines.get(l), x + 5, y + 5 + LINE + 2 + l * LINE, Glass.a(BLURB, 1f), false);
    }

    /** One setting: its name on the left, "‹ value ›" on the right, brighter under the mouse. */
    private void setting(GuiGraphicsExtractor g, int x, int w, int y, int i) {
        boolean hover = i == hoveredSetting;
        if (hover) g.fill(x - 2, y - 1, x + w + 2, y + LINE - 1, Glass.a(Glass.EDGE, 0.10f));
        String label = switch (i) {
            case S_LOOK -> "LOOK";
            case S_FAR -> "FAR TERRAIN";
            case S_DISTANCE -> "RENDER DISTANCE";
            case S_SCALE -> "RENDER SCALE ON 4K";
            case S_STEADY -> "STEADY (NO SHIMMER)";
            default -> "LENS OVER FRAMES";
        };
        Tier t = LookSwitch.current();
        String value = switch (i) {
            case S_LOOK -> t == null ? "off" : t.pretty();
            case S_FAR -> switch (LookPresets.far()) {
                case FULL -> "Full, 16 km";
                case NEAR -> "Near, 4 km";
                case OFF -> "Off";
            };
            case S_DISTANCE -> LookPresets.distance() + " chunks";
            case S_SCALE -> LookPresets.scale4k() >= 0.995 ? "native" : String.format(Locale.ROOT, "%.2f", LookPresets.scale4k());
            case S_STEADY -> LookPresets.steady() ? "on" : "off";
            default -> LookPresets.temporal() ? "on" : "off";
        };
        HudLook.text(g, font, label, x, y, hover ? Glass.a(Glass.EDGE, 1f) : LABEL, false);
        String shown = "‹ " + value + " ›";
        HudLook.text(g, font, shown, x + w - font.width(shown), y, Glass.a(hover ? TITLE : Glass.TEXT, 1f), false);
    }

    /** A click on setting i: the next value (step 1) or the one before (step -1), set now and saved. */
    private void change(int i, int step) {
        switch (i) {
            case S_LOOK -> {
                Tier t = LookSwitch.current();
                pick(TIERS[Math.floorMod((t == null ? -step : t.ordinal()) + step, TIERS.length)]);
            }
            case S_FAR -> {
                AwakenConfig.Client.FarSight[] all = AwakenConfig.Client.FarSight.values();
                LookPresets.setFar(all[Math.floorMod(LookPresets.far().ordinal() - step, all.length)]);
            }
            case S_DISTANCE -> LookPresets.setDistance(LookPresets.next(LookPresets.DISTANCES, LookPresets.distance(), step));
            case S_SCALE -> LookPresets.setScale4k(LookPresets.next(LookPresets.SCALES, LookPresets.scale4k(), step));
            case S_STEADY -> LookPresets.setSteady(!LookPresets.steady());
            default -> LookPresets.setTemporal(!LookPresets.temporal());
        }
        note = "";
    }

    private void card(GuiGraphicsExtractor g, int i, Tier t, @Nullable Tier current, boolean holo, float reveal) {
        int x = cardX(i), y = cardsY;
        boolean on = t == current, hover = i == hovered, can = true;
        int edge = on ? Glass.a(Glass.GOOD, 1f) : hover && can ? Glass.a(Glass.EDGE, 1f) : 0xFF4A5D78;
        if (holo) {
            HudLook.card(g, x, y, cw, ch, edge, can ? 1f : 0.7f, reveal, 0f);
            if (on || hover && can) HudLook.glow(g, x, y, cw, ch, edge, on ? 1f : 0.6f);
        } else {
            g.fill(x, y, x + cw, y + ch, on ? Glass.a(0x16233A, 0.96f) : Glass.a(0x1B2635, hover && can ? 0.95f : 0.85f));
            g.outline(x, y, cw, ch, Glass.a(edge, 0.9f));
            if (on) g.outline(x + 1, y + 1, cw - 2, ch - 2, Glass.a(Glass.GOOD, 0.55f));
        }
        float dim = can ? 1f : 0.55f;
        HudLook.text(g, font, t.label, x + 6, y + 6, Glass.a(TITLE, dim), false);
        String key = String.valueOf(i + 1);
        HudLook.text(g, font, key, x + cw - 6 - font.width(key), y + 6, Glass.a(Glass.LANTERN, 0.75f * dim), false);
        List<FormattedCharSequence> lines = blurbs.get(i);
        for (int l = 0; l < lines.size(); l++) HudLook.text(g, font, lines.get(l), x + 6, y + 19 + l * LINE, Glass.a(BLURB, dim), false);
        String status = on ? "● ON" : !can ? "NOT AVAILABLE" : hover ? "CHOOSE" : "";
        int colour = on ? Glass.GOOD : !can ? Glass.AMBER : Glass.EDGE;
        if (!status.isEmpty()) HudLook.text(g, font, status, x + 6, y + ch - 14, Glass.a(colour, 1f), false);
    }

    /** The rows under the tiers: the frame-time line and the horizon, then what each mod has. */
    private void buildRows() {
        rows.clear();
        rowColours.clear();
        add(frameLabel, frameValue, frameColour);
        if (LookSwitch.DH) {
            boolean here = minecraft.level == null || FarView.allowed(minecraft.level.dimension().identifier().toString());
            int r = DistantHorizonsLook.radius();
            add("HORIZON", !here ? "OFF IN THIS PLACE" : r > 0 ? r + " CHUNKS" : "…", Glass.GOOD);
        }
        Tier current = LookSwitch.current();
        if (Companions.shaderPackActive()) add("LENS", "stepping aside for the shaderpack", Glass.AMBER);
        if (!LookSwitch.DH) {
            // Distant Horizons is out of the pack since 25 Oct: nothing to say
        } else if (LookSwitch.chosen != null) {
            add("DISTANT HORIZONS", LookSwitch.dhResult, LookSwitch.dhWarn ? Glass.AMBER : Glass.TEXT);
        } else {
            boolean same = current != null && Look.dhMatches(current, DistantHorizonsLook::current);
            add("DISTANT HORIZONS", same ? "set for " + current.pretty() : "its own settings (changed in its menu)", Glass.TEXT);
        }
        if (Companions.irisLoaded()) add("IRIS", Companions.irisShadersEnabled() ? "shaders on (Lens steps aside)" : "shaders off", Glass.LABEL);
    }

    private void add(String label, String value, int colour) {
        rows.add(new String[]{label, value});
        rowColours.add(colour);
    }

    /** A label on the left, its value on the right (cut to fit with "…"). */
    private void row(GuiGraphicsExtractor g, int x, int w, int y, String label, String value, int colour) {
        HudLook.text(g, font, label, x, y, LABEL, false);
        int room = w - font.width(label) - 10;
        String v = value;
        if (font.width(v) > room) v = font.plainSubstrByWidth(v, Math.max(0, room - font.width("…"))).stripTrailing() + "…";
        HudLook.text(g, font, v, x + w - font.width(v), y, Glass.a(colour, 1f), false);
    }

    private int rule(GuiGraphicsExtractor g, int x, int w, int y) {
        g.fill(x, y + 2, x + w, y + 3, Glass.a(Glass.EDGE, 0.14f));
        return y + 5;
    }

    /** Chooses a tier, as a click on its card would. False when it can't run here (the reason shows instead). */
    public boolean pick(Tier t) {
        if (!LookSwitch.apply(t)) {
            note = ultra.reason();
            noteColour = Glass.AMBER;
            return false;
        }
        note = LookSwitch.DH && !LookSwitch.dhWarn
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
            int p = presetAt(event.x(), event.y());
            if (p >= 0) {
                if (!LookPresets.apply(PRESETS[p])) {
                    note = ultra.reason();
                    noteColour = Glass.AMBER;
                }
                refreshFrames();
                return true;
            }
        }
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || right) {
            int s = settingAt(event.x(), event.y());
            if (s >= 0) {
                change(s, right ? -1 : 1);
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
