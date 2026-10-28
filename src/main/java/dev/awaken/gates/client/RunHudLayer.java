package dev.awaken.gates.client;

import dev.awaken.core.api.UiHooks;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The run panel (SPEC §27.10; Euan, 2026-10-27: "move the floors to the top right where the current active things to
 * do are"): a small card in the top-right stack, under the task card, or in the corner itself when there is no task,
 * in the task card's look and size ({@link UiHooks#topRightScale}). Rank and gate with the floor beside them (a deeper
 * gate, a cleared tick), then the Satchel, kills and the floor clock with how hard the gate is pushing, noise under
 * Silence, the collapse countdown and the twist's one-line hint. It steps down under the System's toasts and any boss
 * bar it would reach ({@link UiHooks#topRightBelow}): at once when the task card arrives, easing up into the room when
 * it goes. Never dims; hidden with the HUD, under F3 and, like all of Awaken's HUD, while a screen other than chat is
 * open. Measured only when the server's update changes ({@link Layout}); a frame only draws.
 */
public final class RunHudLayer {
    private RunHudLayer() {}

    /** The widest line before the hint is shortened (the task card's), and the narrowest card. */
    private static final int MAX_W = 215, MIN_W = 96;
    private static final int HINT = 0xE2D2FF, HINT_DISGUISED = 0x9AE6B4;

    /** Where the card sits (GUI px from the top): under the stack at once, easing up when room frees. */
    private static float y, yO;

    /** The card as measured for one update from the server. */
    private record Layout(GatePayloads.RunHud hud, String rank, int rankColour, String name, String floor, boolean cleared,
                          String satchel, String kills, String pressure, int pressureColour, String collapse,
                          int collapseColour, String hint, int hintColour, int w, int h) {}

    private static @Nullable Layout layout;

    // ---- the clear screen (SPEC §40.6): one line, the full panel when something changes ---------------------------------

    /** How open the full panel is (0 the one line, 1 the full card), eased a tick at a time; what last opened it. */
    private static float open = 1F, openO = 1F;
    private static int ticks, openedAt;
    private static long content = Long.MIN_VALUE;
    /** How long a change keeps the full panel open (6 s), and the noise that keeps it open under Silence. */
    private static final int OPEN_TICKS = 120, LOUD = 12;

    /** Everything but the clocks: a change here opens the full panel. */
    private static long content(GatePayloads.RunHud h) {
        long k = h.gateId();
        k = 31 * k + h.rank();
        k = 31 * k + h.area();
        k = 31 * k + h.floor();
        k = 31 * k + h.satchelSlots();
        k = 31 * k + h.kills();
        k = 31 * k + h.pressure();
        k = 31 * k + h.flags();
        k = 31 * k + h.hint().hashCode();
        return k;
    }

    private static void tickOpen(GatePayloads.RunHud h) {
        ticks++;
        long c = content(h);
        if (c != content) {
            content = c;
            openedAt = ticks;
        }
        boolean full = dev.awaken.core.api.HudState.classic() || dev.awaken.core.api.HudState.glancing()
                || ticks - openedAt < OPEN_TICKS || h.noise() >= LOUD;
        openO = open;
        open = full ? Math.min(1F, open + 0.2F) : Math.max(0F, open - 0.08F);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientGates.inRun()) {
            y = yO = 0;
            content = Long.MIN_VALUE;
            open = openO = 1F;
            return;
        }
        tickOpen(ClientGates.hud());
        Layout lay = measure(mc.font, ClientGates.hud());
        float want = UiHooks.topRightBelow(1f, lay.w * UiHooks.topRightScale());
        yO = y;
        y = want >= y ? want : y + (want - y) * 0.35f;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen() || !ClientGates.inRun()) return;
        Font font = mc.font;
        GatePayloads.RunHud h = ClientGates.hud();
        Layout lay = measure(font, h);
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float s = UiHooks.topRightScale();
        int w = lay.w;
        float o = Mth.lerp(pt, openO, open);
        o = o * o * (3 - 2 * o);
        // the one line: rank, gate and floor, and the collapse clock under it (it never hides)
        int compactH = 15 + (lay.collapse.isEmpty() ? 0 : 10);
        int hgt = Math.round(Mth.lerp(o, compactH, lay.h));
        float top = Math.max(UiHooks.topRightBelow(pt, w * s), Mth.lerp(pt, yO, y));
        int x = Math.round(g.guiWidth() - w * s - UiHooks.TOP_RIGHT_MARGIN);

        g.pose().pushMatrix();
        g.pose().translate(x, top);
        g.pose().scale(s, s);
        int accent = (h.flags() & GatePayloads.RunHud.RED) != 0 ? Glass.WARN : Glass.EDGE;
        if (HudLook.holo()) HudLook.card(g, 0, 0, w, hgt, accent, 1f, 1f, 0f, HudLook.FRAME);
        else {
            g.fill(0, 0, w, hgt, Glass.a(Glass.GLASS, 0.55f));
            g.fill(w - 1, 0, w, hgt, Glass.a(accent, 0.8f));
        }
        // rank and gate, the floor at the right
        HudLook.text(g, font, lay.rank, 4, 3, lay.rankColour, true);
        HudLook.text(g, font, lay.name, 4 + font.width(lay.rank) + 4, 3, Glass.a(Glass.EDGE, 1f), true);
        int fx = w - 4 - font.width(lay.floor);
        if (lay.cleared) {
            fx -= font.width(" ✓");
            HudLook.text(g, font, " ✓", w - 4 - font.width(" ✓"), 3, Glass.a(Glass.GOOD, 1f), true);
        }
        HudLook.text(g, font, lay.floor, fx, 3, Glass.a(Glass.LABEL, 1f), true);
        if (o < 0.5f && !lay.collapse.isEmpty()) HudLook.text(g, font, lay.collapse, 4, 14, fa(lay.collapseColour, 1f - 2f * o), true);
        if (o < 0.5f) {
            g.pose().popMatrix();
            return;
        }
        float b = 2f * o - 1f; // the body's rows come in over the second half of the opening
        int ry = 14;
        HudLook.text(g, font, lay.satchel, 4, ry, Glass.a(Glass.AMBER, b), true);
        ry += 10;
        HudLook.text(g, font, lay.kills, 4, ry, Glass.a(Glass.TEXT, b), true);
        if (!lay.pressure.isEmpty()) HudLook.text(g, font, lay.pressure, w - 4 - font.width(lay.pressure), ry, fa(lay.pressureColour, b), true);
        ry += 10;
        if (h.noise() >= 0) {
            HudLook.text(g, font, "Noise", 4, ry, Glass.a(Glass.LABEL, b), true);
            int bx = 4 + font.width("Noise") + 5, bw = w - 4 - bx;
            g.fill(bx, ry + 2, bx + bw, ry + 6, Glass.a(0x000000, 0.5f * b));
            g.fill(bx, ry + 2, bx + Math.min(bw, h.noise() * bw / 24), ry + 6, Glass.a(h.noise() >= 18 ? Glass.WARN : Glass.EDGE, b));
            ry += 10;
        }
        if (!lay.collapse.isEmpty()) {
            HudLook.text(g, font, lay.collapse, 4, ry, fa(lay.collapseColour, b), true);
            ry += 10;
        }
        if (!lay.hint.isEmpty()) HudLook.text(g, font, lay.hint, 4, ry, fa(lay.hintColour, b), true);
        g.pose().popMatrix();
    }

    /** The layout for this update: the last one when the server sent nothing new (no strings built, nothing allocated). */
    private static Layout measure(Font font, GatePayloads.RunHud h) {
        Layout was = layout;
        if (was != null && was.hud == h) return was;
        String rank = ClientGates.rankLabel(h.rank());
        String name = themeName(h.theme()).toUpperCase(Locale.ROOT);
        String floor = (h.area() == 1 ? "Deeper · " : "") + "Floor " + h.floor() + "/" + h.floors();
        boolean cleared = (h.flags() & GatePayloads.RunHud.CLEARED_FLOOR) != 0;
        String satchel = "Satchel " + h.satchelSlots() + "/27 · " + h.satchelXp() + " XP";
        int s = h.floorSeconds();
        String kills = "Kills " + h.kills() + " · " + String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        String pressure = switch (h.pressure()) {
            case 1 -> "stirring";
            case 2 -> "awake";
            case 3 -> "Warden";
            default -> "";
        };
        int pressureColour = Glass.a(h.pressure() == 1 ? Glass.AMBER : Glass.WARN, 1f);
        int c = h.collapseSeconds();
        String collapse = c < 0 ? "" : c > 0 ? String.format(Locale.ROOT, "Collapse in %d:%02d", c / 60, c % 60) : "Collapsing!";
        int collapseColour = Glass.a(c < 60 ? Glass.WARN : Glass.AMBER, 1f);
        int floorW = font.width(floor) + (cleared ? font.width(" ✓") : 0);
        // the widest line decides the card, up to MAX_W; a long hint is shortened to fit
        int wide = font.width(rank) + 4 + font.width(name) + 10 + floorW;
        wide = Math.max(wide, font.width(satchel));
        wide = Math.max(wide, font.width(kills) + (pressure.isEmpty() ? 0 : 10 + font.width(pressure)));
        wide = Math.max(wide, font.width(collapse));
        wide = Math.max(wide, font.width(h.hint()));
        int w = Mth.clamp(wide, MIN_W, MAX_W) + 8;
        String hint = fit(font, h.hint(), w - 8);
        int nameRoom = w - 8 - font.width(rank) - 4 - 10 - floorW;
        name = fit(font, name, Math.max(12, nameRoom));
        int rows = 2 + (h.noise() >= 0 ? 1 : 0) + (collapse.isEmpty() ? 0 : 1) + (hint.isEmpty() ? 0 : 1);
        int hintColour = Glass.a((h.flags() & GatePayloads.RunHud.DISGUISED) != 0 ? HINT_DISGUISED : HINT, 1f);
        Layout lay = new Layout(h, rank, ClientGates.rankColour(h.rank()), name, floor, cleared, satchel, kills, pressure,
                pressureColour, collapse, collapseColour, hint, hintColour, w, 16 + rows * 10);
        layout = lay;
        return lay;
    }

    /** An ARGB colour with its alpha scaled. */
    private static int fa(int argb, float a) {
        return Math.round((argb >>> 24) * Mth.clamp(a, 0f, 1f)) << 24 | argb & 0xFFFFFF;
    }

    /** {@code s} shortened with "…" to fit {@code room} px. */
    private static String fit(Font font, String s, int room) {
        if (font.width(s) <= room) return s;
        String cut = s;
        while (cut.length() > 1 && font.width(cut + "…") > room) cut = cut.substring(0, cut.length() - 1);
        return cut.stripTrailing() + "…";
    }

    static String themeName(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
