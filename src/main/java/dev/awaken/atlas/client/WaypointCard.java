package dev.awaken.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.atlas.AtlasData;
import dev.awaken.atlas.AtlasNet;
import dev.awaken.holo.HoloSounds;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.StringUtil;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A waypoint's card on the Atlas (SPEC §16.8; Euan 2026-11-02): beside one of your waypoints, its name to type (already
 * selected, so you can just type), eight colours, eight marks, "Share with my party", <b>Keep it</b> (Enter) and
 * <b>Remove</b> (Del). It opens on a new waypoint the moment the server has it, or on one of yours from its destination
 * card (Change it, E). While it's open it takes every key, so typing never moves the map or closes it; Esc closes the card
 * and leaves the waypoint as it was, and a click off the card keeps what you changed.
 */
final class WaypointCard {
    static final int W = 156, H = 112;
    private static final int INK = 0xFFE9F1FF, NAVY = 0xFF0B1626;
    /** How long a new waypoint is waited for before the card gives up (the server said no: full, or unknown ground). */
    private static final long WAIT = 3_000_000_000L;

    // what the last waypoint you kept looked like: the next new one starts so
    private static int lastColour, lastMark;
    private static boolean lastShared;

    private AtlasNet.@Nullable WaypointView editing;
    private boolean isNew;
    private final StringBuilder name = new StringBuilder();
    private boolean selected;
    private int colour, mark;
    private boolean shared;
    // a waypoint asked for and not here yet: where, and the highest id of yours before it
    private int waitX, waitZ, waitAfter = -1;
    private long waitSince;
    private final float[] p = new float[2];
    private int x0 = -1, y0;

    boolean isOpen() {
        return editing != null;
    }

    /** The waypoint being changed (it draws with the card's colour and mark meanwhile), or -1. */
    int editingId() {
        return editing == null ? -1 : editing.id();
    }

    int colourRgb() {
        return colour == AtlasData.OWN_COLOUR && editing != null ? editing.ownerColour() : AtlasData.COLOURS[Math.max(0, colour)];
    }

    int mark() {
        return mark;
    }

    /** Opens on one of your waypoints. */
    void edit(AtlasNet.WaypointView w, boolean fresh) {
        editing = w;
        isNew = fresh;
        name.setLength(0);
        name.append(w.name());
        selected = true;
        colour = WaypointGlyphs.colourIndex(w);
        mark = w.mark();
        shared = w.shared();
        waitAfter = -1;
        sound(1.2f);
    }

    void close() {
        editing = null;
        x0 = -1;
    }

    // ---- a new one ----------------------------------------------------------------------------------------------------

    /** Asks the server for a new waypoint at (x, y, z) named {@code name}, in the last look you kept; the card opens on it when it comes. */
    void add(AtlasState atlas, int x, int y, int z, String name) {
        if (editing != null) keep();
        int after = 0;
        for (AtlasNet.WaypointView w : atlas.waypoints) if (w.mine() && !w.fell()) after = Math.max(after, w.id());
        waitX = x;
        waitZ = z;
        waitAfter = after;
        waitSince = System.nanoTime();
        ClientPacketDistributor.sendToServer(new AtlasNet.WaypointEdit(AtlasNet.WaypointEdit.ADD, 0, x, y, z, name, lastColour, lastMark, lastShared));
        sound(1.3f);
    }

    /** Each frame: the waypoint asked for has come (open on it), or the one open has gone (close). */
    void update(AtlasState atlas) {
        if (waitAfter >= 0) {
            for (AtlasNet.WaypointView w : atlas.waypoints) {
                if (w.mine() && !w.fell() && w.id() > waitAfter && w.x() == waitX && w.z() == waitZ) {
                    edit(w, true);
                    return;
                }
            }
            if (System.nanoTime() - waitSince > WAIT) waitAfter = -1;
        }
        if (editing != null) {
            AtlasNet.WaypointView now = null;
            for (AtlasNet.WaypointView w : atlas.waypoints) if (w.mine() && w.id() == editing.id()) now = w;
            if (now == null) close(); // removed (from another of your screens, or a dimension change)
            else editing = now;
        }
    }

    // ---- what it sends --------------------------------------------------------------------------------------------------

    /** Keeps what you changed (Enter, Keep it, a click off the card). */
    void keep() {
        AtlasNet.WaypointView w = editing;
        if (w == null) return;
        String n = name.toString().strip();
        if (n.isEmpty()) n = w.name();
        if (!n.equals(w.name()) || colour != WaypointGlyphs.colourIndex(w) || mark != w.mark() || shared != w.shared())
            ClientPacketDistributor.sendToServer(new AtlasNet.WaypointEdit(AtlasNet.WaypointEdit.EDIT, w.id(), 0, 0, 0, n, colour, mark, shared));
        if (colour != AtlasData.OWN_COLOUR) lastColour = colour;
        lastMark = mark;
        lastShared = shared;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.CONFIRM.get(), 1.0f, 0.6f));
        close();
    }

    /** Takes it off the map (Remove, Del). */
    void remove() {
        if (editing == null) return;
        ClientPacketDistributor.sendToServer(new AtlasNet.WaypointEdit(AtlasNet.WaypointEdit.REMOVE, editing.id(), 0, 0, 0, "", 0, 0, false));
        sound(0.8f);
        close();
    }

    // ---- input ----------------------------------------------------------------------------------------------------------

    /** Every key while it's open: the name's editing, Enter keeps, Esc closes, Del removes. */
    boolean key(KeyEvent e) {
        if (editing == null) return false;
        if (e.isEscape()) {
            close();
            return true;
        }
        int k = e.key();
        if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) keep();
        else if (e.isSelectAll()) selected = true;
        else if (e.isPaste()) type(Minecraft.getInstance().keyboardHandler.getClipboard());
        else if (k == InputConstants.KEY_BACKSPACE) {
            if (selected) name.setLength(0);
            else if (!name.isEmpty()) name.setLength(name.offsetByCodePoints(name.length(), -1));
            selected = false;
        } else if (k == InputConstants.KEY_DELETE) {
            if (selected) { // as in any text field: Del clears what's selected
                name.setLength(0);
                selected = false;
            } else remove();
        } else if (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_RIGHT || k == InputConstants.KEY_END || k == InputConstants.KEY_HOME)
            selected = false;
        return true;
    }

    boolean chr(CharacterEvent e) {
        if (editing == null) return false;
        if (e.isAllowedChatCharacter()) type(e.codepointAsString());
        return true;
    }

    private void type(String s) {
        String in = StringUtil.filterText(s).replaceAll("\\s+", " ");
        if (selected) name.setLength(0);
        selected = false;
        for (int i = 0; i < in.length(); ) {
            int cp = in.codePointAt(i);
            if (name.codePointCount(0, name.length()) >= AtlasData.NAME_MAX) break;
            name.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
    }

    boolean over(double mx, double my) {
        return editing != null && x0 >= 0 && mx >= x0 && mx < x0 + W && my >= y0 && my < y0 + H;
    }

    /** A click on the card: a colour, a mark, the share box, Keep it, Remove, or the name (selects it all). */
    void click(double mx, double my) {
        int lx = (int) Math.floor(mx) - x0, ly = (int) Math.floor(my) - y0;
        if (ly >= 15 && ly < 31) selected = !selected;
        else if (ly >= 34 && ly < 46) {
            int i = (lx - 6) / 12;
            if (lx >= 6 && i < AtlasData.COLOURS.length) {
                colour = i;
                sound(1.25f);
            }
        } else if (ly >= 49 && ly < 63) {
            int i = (lx - 6) / 15;
            if (lx >= 6 && i < AtlasData.MARKS) {
                mark = i;
                sound(1.25f);
            }
        } else if (ly >= 66 && ly < 78) {
            shared = !shared;
            sound(shared ? 1.3f : 1.0f);
        } else if (ly >= 81 && ly < 95) keep();
        else if (ly >= 97 && ly < 109) remove();
    }

    private static void sound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), pitch, 0.6f));
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    /** The card beside its waypoint's head, kept on the table. */
    void draw(GuiGraphicsExtractor g, Font font, AtlasState atlas, AtlasDestination.Projector proj, int tx0, int ty0, int tx1, int ty1,
              float age, int mouseX, int mouseY) {
        AtlasNet.WaypointView w = editing;
        x0 = -1;
        if (w == null || !proj.at(w.x() + 0.5, atlas.store.groundY(w.x(), w.z(), w.y()), w.z() + 0.5, p)) return;
        int sx = Math.round(p[0]), hy = WaypointGlyphs.headY(Math.round(p[1]));
        int x = sx + 16, y = hy - H / 2;
        if (x + W > tx1 - 4) x = sx - 16 - W;
        x = Math.clamp(x, tx0 + 4, Math.max(tx0 + 4, tx1 - 4 - W));
        y = Math.clamp(y, ty0 + 4, Math.max(ty0 + 4, ty1 - 4 - H));
        x0 = x;
        y0 = y;
        int rgb = colourRgb(), c = 0xFF000000 | rgb;
        g.fill(x, y, x + W, y + H, Glass.a(0x0B1626, 0.95f));
        g.fill(x, y, x + 2, y + H, c);
        g.fill(x + 2, y, x + W, y + 1, Glass.a(Glass.EDGE, 0.55f));
        g.text(font, isNew ? "NEW WAYPOINT" : "YOUR WAYPOINT", x + 7, y + 5, Glass.a(Glass.EDGE, 1f), false);
        // the name
        g.fill(x + 6, y + 15, x + W - 6, y + 31, Glass.a(Glass.EDGE, 0.55f));
        g.fill(x + 7, y + 16, x + W - 7, y + 30, Glass.a(0x050A12, 0.92f));
        String n = name.toString();
        int room = W - 22;
        String shown = font.width(n) > room ? font.plainSubstrByWidth(n, room, true) : n; // its end, where you type
        int tw = font.width(shown);
        if (selected && !n.isEmpty()) g.fill(x + 9, y + 18, x + 11 + tw, y + 28, Glass.a(Glass.EDGE, 0.38f));
        g.text(font, shown, x + 10, y + 19, 0xFFFFFFFF, false);
        if (!selected && (int) (age * 2) % 2 == 0) g.fill(x + 11 + tw, y + 18, x + 12 + tw, y + 28, 0xFFFFFFFF);
        // eight colours
        for (int i = 0; i < AtlasData.COLOURS.length; i++) {
            int bx = x + 8 + i * 12, by = y + 36;
            if (i == colour) {
                g.fill(bx - 2, by - 2, bx + 11, by + 11, 0xFFFFFFFF);
                g.fill(bx - 1, by - 1, bx + 10, by + 10, NAVY);
            } else if (mouseX >= bx - 1 && mouseX < bx + 10 && mouseY >= by - 1 && mouseY < by + 10)
                g.fill(bx - 1, by - 1, bx + 10, by + 10, Glass.a(Glass.LABEL, 0.5f));
            g.fill(bx, by, bx + 9, by + 9, 0xFF000000 | AtlasData.COLOURS[i]);
        }
        // eight marks
        for (int i = 0; i < AtlasData.MARKS; i++) {
            int bx = x + 7 + i * 15, by = y + 49;
            boolean on = i == mark, over = mouseX >= bx && mouseX < bx + 13 && mouseY >= by && mouseY < by + 13;
            g.fill(bx, by, bx + 13, by + 13, on ? 0xFFFFFFFF : Glass.a(Glass.LABEL, over ? 0.55f : 0.25f));
            g.fill(bx + 1, by + 1, bx + 12, by + 12, on ? Glass.a(0x1A3550, 1f) : NAVY);
            WaypointGlyphs.glyph(g, i, false, bx + 3, by + 3, on ? 0xFFFFFFFF : INK);
        }
        // share with my party
        int sy = y + 68;
        g.fill(x + 7, sy, x + 16, sy + 9, shared ? 0xFF000000 | Glass.GOOD : Glass.a(Glass.LABEL, 1f));
        g.fill(x + 8, sy + 1, x + 15, sy + 8, NAVY);
        if (shared) g.fill(x + 9, sy + 2, x + 14, sy + 7, 0xFF000000 | Glass.GOOD);
        boolean overShare = mouseX >= x && mouseX < x + W && mouseY >= y + 66 && mouseY < y + 78;
        g.text(font, "Share with my party", x + 21, sy + 1, overShare ? 0xFFFFFFFF : INK, false);
        // keep it, remove
        int ky = y + 82;
        boolean overKeep = mouseX >= x && mouseX < x + W && mouseY >= ky - 1 && mouseY < ky + 13;
        g.fill(x + 6, ky - 1, x + W - 6, ky + 13, Glass.a(rgb, overKeep ? 0.36f : 0.22f));
        g.text(font, "› Keep it", x + 10, ky + 2, 0xFFFFFFFF, false);
        g.text(font, "Enter", x + W - 10 - font.width("Enter"), ky + 2, Glass.a(Glass.LABEL, 0.8f), false);
        int ry = y + 99;
        boolean overRemove = mouseX >= x && mouseX < x + W && mouseY >= ry - 2 && mouseY < ry + 10;
        g.text(font, "Remove", x + 10, ry, overRemove ? 0xFF000000 | Glass.WARN : Glass.a(Glass.LABEL, 0.85f), false);
        g.text(font, "Del", x + W - 10 - font.width("Del"), ry, Glass.a(Glass.LABEL, 0.7f), false);
    }
}
