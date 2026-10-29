package dev.awaken.progression.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.progression.net.ProgressionPayloads;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The band's Status page as a sheet about you (SPEC §30.3), not a feed: the hero (your face, name and title, LEVEL
 * large, the XP to the next level as segments, what the next level opens, your rank as a sigil) and the six stats in one
 * line, where points are placed and then confirmed in one request. Both draw their own labels (no title rows).
 */
final class StatusSheet {
    private StatusSheet() {}

    static final int INK_HI = 0xFFFFFF, INK = 0xD9E4F2, HOT = 0xC4E6FF, NAVY = 0x05080F;

    // ---- drawing helpers (the same on the holo panel and the flat screen: GuiCanvas routes to either) --------------

    /** Text at a scale from (x, y); returns its width in units. */
    static float text(GuiGraphicsExtractor g, Font f, String s, float x, float y, float scale, int rgb, float a) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(f, s, 0, 0, Glass.a(rgb, a), false);
        g.pose().popMatrix();
        return f.width(s) * scale;
    }

    static float width(Font f, String s, float scale) {
        return f.width(s) * scale;
    }

    /** A pixel diamond's outline (radius r) round (cx, cy), filled faintly when {@code fill} > 0. */
    static void diamond(GuiGraphicsExtractor g, int cx, int cy, int r, int rgb, float a, float fill) {
        if (fill > 0) for (int i = 0; i < r; i++) g.fill(cx - r + i + 1, cy - i, cx + r - i, cy + i + 1, Glass.a(rgb, fill));
        for (int i = 0; i <= r; i++) {
            int c = Glass.a(rgb, a);
            g.fill(cx - r + i, cy - i, cx - r + i + 1, cy - i + 1, c);
            g.fill(cx + r - i, cy - i, cx + r - i + 1, cy - i + 1, c);
            g.fill(cx - r + i, cy + i, cx - r + i + 1, cy + i + 1, c);
            g.fill(cx + r - i, cy + i, cx + r - i + 1, cy + i + 1, c);
        }
    }

    /** Four 4-unit corner ticks round a box (focus, the portrait's frame). */
    static void ticks(GuiGraphicsExtractor g, int x, int y, int w, int h, int rgb, float a) {
        int c = Glass.a(rgb, a), L = 4;
        g.fill(x - 1, y - 1, x - 1 + L, y, c);
        g.fill(x - 1, y - 1, x, y - 1 + L, c);
        g.fill(x + w + 1 - L, y - 1, x + w + 1, y, c);
        g.fill(x + w, y - 1, x + w + 1, y - 1 + L, c);
        g.fill(x - 1, y + h, x - 1 + L, y + h + 1, c);
        g.fill(x - 1, y + h + 1 - L, x, y + h + 1, c);
        g.fill(x + w + 1 - L, y + h, x + w + 1, y + h + 1, c);
        g.fill(x + w, y + h + 1 - L, x + w + 1, y + h + 1, c);
    }

    /** XP as segments: 3 units each, 1 apart; the one being filled half lit. */
    static void segments(GuiGraphicsExtractor g, int x, int y, int w, int h, double f, int rgb) {
        int n = Math.max(1, (w + 1) / 4);
        double filled = Math.max(0, Math.min(1, f)) * n;
        for (int i = 0; i < n; i++) {
            int sx = x + i * 4;
            int c = i < (int) filled ? Glass.a(rgb, 0.95f) : i < filled ? Glass.a(rgb, 0.5f) : Glass.a(Glass.EDGE, 0.14f);
            g.fill(sx, y, sx + 3, y + h, c);
        }
    }

    static Profile profile() {
        return CoreSections.profile();
    }

    static String plural(long n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    // ---- the hero -------------------------------------------------------------------------------------------------

    /** Who you are: face, name, title, path, LEVEL, XP as segments, the next unlock, and the rank as a sigil. */
    static final class Hero implements StatusSections.Section {
        static final int H = 50;
        private List<Defs.LevelRoadDef> road = List.of();
        private int roadFrom = -1;
        private int sigilX, sigilY;
        /** Motion reports change: the XP you gained since you last looked fills in when the band opens. */
        private static double seenFraction = -1;
        private static int seenLevel = -1;
        private static long lastDrawn, fillFrom;
        private static double fillStart;

        public String title() { return ""; }
        public int order() { return 0; }
        public int height(int width) { return profile().awakened() ? H : 22; }

        /** The level road's next step above your level ("Level 15 opens The Veil"). */
        private Defs.LevelRoadDef next(int level) {
            var map = Content.LEVEL_ROAD.map;
            if (map.size() != roadFrom) {
                List<Defs.LevelRoadDef> l = new ArrayList<>(map.values());
                l.sort(Comparator.comparingInt(Defs.LevelRoadDef::level).thenComparing(Defs.LevelRoadDef::title));
                road = List.copyOf(l);
                roadFrom = map.size();
            }
            for (Defs.LevelRoadDef d : road) if (d.level() > level) return d;
            return null;
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float pt) {
            Minecraft mc = Minecraft.getInstance();
            Font f = mc.font;
            Profile p = profile();
            float small = StatusSections.small(), micro = StatusSections.micro();
            String name = mc.player == null ? "" : mc.player.getName().getString();
            if (!p.awakened()) {
                text(g, f, name, x, y + 1, 1f, INK_HI, 1f);
                text(g, f, "You haven't awakened yet. Keep exploring: something out there is waiting for you to find it.", x, y + 12, small, INK, 1f);
                return;
            }
            Rank rank = p.rank();
            // your face, from your own skin, framed in your rank's colour
            int fx = x + 1, fy = y + 2;
            g.fill(fx - 1, fy - 1, fx + 33, fy + 33, Glass.a(NAVY, 0.5f));
            if (mc.player != null) {
                var skin = mc.player.getSkin().body().texturePath();
                var c = GuiCanvas.of(g);
                c.texture(skin, fx, fy, 32, 32, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
                c.texture(skin, fx, fy, 32, 32, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF); // the hat layer
            }
            ticks(g, fx - 1, fy - 1, 34, 34, rank.colour, 1f);
            int nx = x + 42;
            float tx = nx + text(g, f, name, nx, y + 2, 1f, INK_HI, 1f);
            var title = p.title().map(Content.TITLES::get).orElse(null);
            if (title != null) text(g, f, "“" + title.name() + "”", tx + 5, y + 2.5f, small, Glass.AMBER, 1f);
            var path = p.path().map(Content.PATHS::get).orElse(null);
            text(g, f, path == null ? "LEVEL" : "LEVEL  ·  PATH OF THE " + path.name().toUpperCase(java.util.Locale.ROOT), nx, y + 14, micro, Glass.LABEL, 1f);
            float lw = text(g, f, String.valueOf(p.level()), nx, y + 20, 2f, INK_HI, 1f);
            float lx = nx + lw + 7;
            double need = Curve.xpToNext(p.level());
            text(g, f, String.format("%,.0f / %,.0f XP", p.xp(), need), lx, y + 21, small, INK, 1f);
            Defs.LevelRoadDef nextStep = next(p.level());
            int sigilR = 14, sx = x + w - sigilR - 4;
            if (nextStep != null) {
                String pre = "Level " + nextStep.level() + " opens ";
                float pw = text(g, f, pre, lx, y + 30, small, Glass.LABEL, 1f);
                String t = ellipsize(f, nextStep.title(), (sx - sigilR - 6) - (lx + pw), small);
                text(g, f, t, lx + pw, y + 30, small, Glass.HORIZON, 1f);
            }
            double frac = p.xp() / need;
            long now = System.currentTimeMillis();
            if (now - lastDrawn > 500) { // a new look: fill from what you saw last time (from empty after a level-up)
                fillStart = seenLevel == p.level() ? Math.min(seenFraction, frac) : 0;
                fillFrom = now + 300;
                if (seenLevel < 0 || StatusSections.still()) fillStart = frac;
            }
            lastDrawn = now;
            seenFraction = frac;
            seenLevel = p.level();
            float k = Math.max(0, Math.min(1, (now - fillFrom) / 250f));
            double shown = fillStart + (frac - fillStart) * (1 - (1 - k) * (1 - k));
            segments(g, nx, y + 41, Math.max(40, sx - sigilR - 8 - nx), 3, shown, Glass.EDGE);
            // the rank as a sigil: its letter in light inside a pixel diamond; the next rank under it
            int sy = y + 17;
            sigilX = sx;
            sigilY = sy;
            diamond(g, sx, sy, sigilR, rank.colour, 0.95f, 0.08f);
            if (p.reassessmentReady()) diamond(g, sx, sy, sigilR + 3, Glass.AMBER, 0.9f, 0f);
            text(g, f, rank.label, sx - width(f, rank.label, 2f) / 2f + 1, sy - 7, 2f, INK_HI, 1f);
            String rk = rank.label + "-RANK";
            text(g, f, rk, sx - width(f, rk, micro) / 2f, sy + sigilR + 4, micro, rank.colour, 1f);
            Rank next = rank.next();
            String nt = p.reassessmentReady() ? "TRIAL READY" : next == null ? "THE HIGHEST" : next.label + " AT LEVEL " + next.levelRequired;
            text(g, f, nt, sx - width(f, nt, micro) / 2f, sy + sigilR + 11, micro, p.reassessmentReady() ? Glass.AMBER : Glass.LABEL, 1f);
            // what the next rank opens for you, on the sigil (only locks you've met: never a list of spoilers)
            if (next != null && mc.player != null && Math.abs(mouseX - sx) + Math.abs(mouseY - sy) <= sigilR) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(next.label + "-rank at level " + next.levelRequired + (p.reassessmentReady() ? ": your trial is ready at your Spire." : ", then its Reassessment.")));
                List<String> opens = WaitingSection.nextRankOpens(mc.player, next);
                if (!opens.isEmpty()) lines.add(Component.literal("It opens what waits for you: " + String.join(", ", opens) + "."));
                GuiCanvas.of(g).tooltip(f, lines.stream().flatMap(l -> f.split(l, 220).stream()).toList(), mouseX, mouseY);
            }
        }
    }

    static String ellipsize(Font f, String s, float maxW, float scale) {
        if (f.width(s) * scale <= maxW) return s;
        while (!s.isEmpty() && f.width(s + "…") * scale > maxW) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    // ---- the stats ------------------------------------------------------------------------------------------------

    private static final String[] NAME = {"STRENGTH", "AGILITY", "VITALITY", "INTELLIGENCE", "PERCEPTION", "SPIRIT"};
    private static final String[] SAYS = {"Harder hits, and stronger Kinetic powers.", "You move faster, and Flow builds sooner.",
            "More health, and you shrug off more harm.", "More energy for Energy and Tech powers.",
            "More critical hits, and you notice secrets and omens.", "Stronger in the Veil and in dreams."};

    /** Points placed but not yet confirmed, in the order they were placed (kept while the band is shut). */
    static final List<Stat> PLACED = new ArrayList<>();
    /** When the last confirm landed, and on which stats (their values flash). */
    static long confirmedAt;
    static final boolean[] FLASH = new boolean[6];

    static int placed(Stat s) {
        int n = 0;
        for (Stat t : PLACED) if (t == s) n++;
        return n;
    }

    /** The six stats in one line; + under each while points wait; the line under them says what the focused one does. */
    static final class StatLine implements StatusSections.Section {
        static final int H = 60;
        private int focus = -1;
        private int lx, ly, lw;
        private int confirmX, confirmW, undoX, undoW, buttonsY = -1000;

        public String title() { return ""; }
        public int order() { return 10; }
        public int height(int width) { return profile().awakened() ? H : 0; }
        public boolean visible() { return profile().awakened(); }

        private int column(double mx, double my) {
            if (my < ly + 8 || my >= ly + 46 || mx < lx || mx >= lx + lw) return -1;
            return (int) ((mx - lx) * 6 / lw);
        }

        public void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, float pt) {
            Minecraft mc = Minecraft.getInstance();
            Font f = mc.font;
            Profile p = profile();
            float small = StatusSections.small(), micro = StatusSections.micro();
            lx = x;
            ly = y;
            lw = w;
            // never more placed than you have (a point spent elsewhere, a death's fracture)
            while (PLACED.size() > p.unspent()) PLACED.removeLast();
            int unspent = p.unspent(), left = unspent - PLACED.size();
            // the label row: STATS, a hairline, and what waits on you
            float hw = text(g, f, "STATS", x, y + 1, micro, Glass.LABEL, 1f);
            float right = x + w;
            String chip = unspent > 0 ? "◆ " + plural(unspent, "POINT").toUpperCase(java.util.Locale.ROOT) + " TO SPEND" : "NO POINTS TO SPEND";
            float cw = width(f, chip, micro);
            if (unspent > 0) {
                g.fill((int) (right - cw - 6), y - 1, (int) right, y + 7, Glass.a(Glass.AMBER, 0.14f));
                g.outline((int) (right - cw - 6), y - 1, (int) (cw + 6), 8, Glass.a(Glass.AMBER, 0.65f));
                text(g, f, chip, right - cw - 3, y + 1, micro, Glass.AMBER, 1f);
            } else text(g, f, chip, right - cw, y + 1, micro, Glass.LABEL, 0.7f);
            right -= cw + 10;
            var level = mc.level;
            if (level != null && p.fractureUntil() > level.getGameTime()) {
                // a fracture weakens every stat, so it is told where the stats are
                long min = Math.max(1, (p.fractureUntil() - level.getGameTime() + 1199) / 1200);
                String fr = "! FRACTURED · " + min + " MIN · 15% WEAKER";
                float fw = width(f, fr, micro);
                g.fill((int) (right - fw - 6), y - 1, (int) right, y + 7, Glass.a(Glass.WARN, 0.16f));
                g.outline((int) (right - fw - 6), y - 1, (int) (fw + 6), 8, Glass.a(Glass.WARN, 0.75f));
                text(g, f, fr, right - fw - 3, y + 1, micro, Glass.WARN, 1f);
                right -= fw + 10;
            }
            g.fill((int) (x + hw + 4), y + 3, (int) right, y + 4, Glass.a(Glass.EDGE, 0.22f));
            // the six columns
            int hover = column(mouseX, mouseY);
            int shown = hover >= 0 ? hover : focus;
            long since = System.currentTimeMillis() - confirmedAt;
            int top = y + 9;
            for (Stat s : Stat.values()) {
                int i = s.ordinal();
                int cx0 = x + i * w / 6, cx1 = x + (i + 1) * w / 6, mid = (cx0 + cx1) / 2;
                boolean hot = i == shown;
                int pend = placed(s);
                boolean flash = since < 600 && FLASH[i];
                if (hot) {
                    g.fillGradient(cx0 + 1, top, cx1 - 1, top + 40, Glass.a(Glass.EDGE, 0.20f), Glass.a(Glass.EDGE, 0.02f));
                    g.fill(cx0 + 1, top, cx1 - 1, top + 1, Glass.a(HOT, 0.9f));
                } else if (flash) {
                    g.fillGradient(cx0 + 1, top, cx1 - 1, top + 40, Glass.a(Glass.AMBER, 0.22f * (1 - since / 600f)), Glass.a(Glass.AMBER, 0f));
                }
                if (i > 0) g.fill(cx0, top + 6, cx0 + 1, top + 26, Glass.a(Glass.EDGE, 0.14f));
                text(g, f, s.name(), mid - width(f, s.name(), micro) / 2f, top + 4, micro, hot ? INK_HI : Glass.LABEL, 1f);
                String v = String.valueOf(p.stat(s) + pend);
                float vw = width(f, v, 2f);
                text(g, f, v, mid - vw / 2f, top + 12, 2f, pend > 0 || flash ? Glass.AMBER : INK_HI, 1f);
                if (pend > 0) text(g, f, "+" + pend, mid + vw / 2f + 1, top + 11, small, Glass.AMBER, 1f);
                if (left > 0 || pend > 0) {
                    boolean lit = hot || pend > 0;
                    diamond(g, mid, top + 33, 4, lit ? Glass.AMBER : Glass.EDGE, lit ? 1f : 0.6f, lit ? 0.22f : 0f);
                    int pc = Glass.a(lit ? INK_HI : Glass.EDGE, lit ? 1f : 0.8f);
                    g.fill(mid - 1, top + 33, mid + 2, top + 34, pc);
                    g.fill(mid, top + 32, mid + 1, top + 35, pc);
                }
            }
            // the line under the stats
            int fy = top + 42;
            buttonsY = -1000;
            if (shown >= 0) {
                float nw = text(g, f, NAME[shown], x, fy + 1, micro, HOT, 1f);
                text(g, f, ellipsize(f, SAYS[shown], w - nw - 5, small), x + nw + 5, fy, small, INK, 1f);
            } else if (!PLACED.isEmpty()) {
                text(g, f, PLACED.size() + " of " + unspent + " placed. Nothing is spent until you confirm.", x, fy, small, Glass.AMBER, 1f);
            } else if (unspent > 0) {
                text(g, f, "Press + on a stat to raise it, then confirm.", x, fy, small, Glass.LABEL, 1f);
            } else {
                Profile.DayLog d = p.day();
                if (d.day() >= 0 && (d.kills() > 0 || d.gates() > 0 || p.level() > d.levelStart())) {
                    String today = "Today: level " + d.levelStart() + (p.level() > d.levelStart() ? " → " + p.level() : "")
                            + " · " + plural(d.kills(), "kill") + " · " + plural(d.gates(), "gate") + (d.best().isBlank() ? "" : " · best: " + d.best());
                    text(g, f, ellipsize(f, today, w, small), x, fy, small, Glass.LABEL, 1f);
                }
            }
            if (!PLACED.isEmpty() && shown < 0) {
                // Confirm (Enter) and Undo (Backspace), at the right of the line
                buttonsY = fy - 2;
                undoW = button(g, f, "Undo", "BKSP", 0, 0, false, micro, true);
                confirmW = button(g, f, "Confirm", "ENTER", 0, 0, false, micro, true);
                undoX = x + w - undoW;
                confirmX = undoX - 4 - confirmW;
                boolean oc = mouseX >= confirmX && mouseX < confirmX + confirmW && mouseY >= buttonsY && mouseY < buttonsY + 11;
                boolean ou = mouseX >= undoX && mouseX < undoX + undoW && mouseY >= buttonsY && mouseY < buttonsY + 11;
                button(g, f, "Confirm", "ENTER", confirmX, buttonsY, oc, micro, false);
                button(g, f, "Undo", "BKSP", undoX, buttonsY, ou, micro, false);
            }
        }

        /** A glass key with its keycap; returns its width (measure only when {@code measure}). */
        private static int button(GuiGraphicsExtractor g, Font f, String label, String key, int x, int y, boolean hover, float micro, boolean measure) {
            float small = StatusSections.small();
            int kw = Math.round(width(f, key, micro)) + 5;
            int w = Math.round(width(f, label, small)) + 9 + kw;
            if (measure) return w;
            int accent = label.equals("Confirm") ? Glass.AMBER : Glass.EDGE;
            g.fill(x, y, x + w, y + 11, Glass.a(accent, hover ? 0.28f : 0.12f));
            g.outline(x, y, w, 11, Glass.a(accent, hover ? 1f : 0.6f));
            text(g, f, label, x + 4, y + 2, small, INK_HI, 1f);
            int kx = x + w - kw - 2;
            g.fill(kx, y + 2, kx + kw, y + 9, Glass.a(NAVY, 0.6f));
            g.outline(kx, y + 2, kw, 7, Glass.a(Glass.LABEL, 0.55f));
            text(g, f, key, kx + 2.5f, y + 3.5f, micro, INK, 1f);
            return w;
        }

        public boolean click(double mx, double my, int x, int y, int w) {
            if (buttonsY > -1000 && my >= buttonsY && my < buttonsY + 11) {
                if (mx >= confirmX && mx < confirmX + confirmW) return confirm();
                if (mx >= undoX && mx < undoX + undoW) return undo();
            }
            int col = column(mx, my);
            if (col < 0) return false;
            focusOn(col);
            if (my >= ly + 9 + 28) return place(Stat.values()[col]); // the + under the value
            return true;
        }

        /** Focus moves to a stat: the narrator says what it is and does ("Agility 22. You move faster..."). */
        private void focusOn(int col) {
            if (col == focus) return;
            focus = col;
            Profile p = profile();
            Stat s = Stat.values()[col];
            int left = p.unspent() - PLACED.size();
            StatusSections.narrate(s.displayName + " " + (p.stat(s) + placed(s)) + ". " + SAYS[col]
                    + (left > 0 ? " " + left + (left == 1 ? " point" : " points") + " to place." : ""));
        }

        public boolean key(KeyEvent e) {
            Profile p = profile();
            if (!p.awakened()) return false;
            int k = e.key();
            if (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_RIGHT) {
                focusOn(focus < 0 ? 0 : Math.floorMod(focus + (k == InputConstants.KEY_LEFT ? -1 : 1), 6));
                return true;
            }
            if ((k == InputConstants.KEY_SPACE || k == InputConstants.KEY_EQUALS || k == InputConstants.KEY_ADD) && focus >= 0) return place(Stat.values()[focus]);
            if ((k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) && !PLACED.isEmpty()) return confirm();
            if (k == InputConstants.KEY_BACKSPACE && !PLACED.isEmpty()) return undo();
            return false;
        }

        private static boolean place(Stat s) {
            if (PLACED.size() >= profile().unspent()) return false;
            PLACED.add(s);
            StatusSections.cue(StatusSections.Cue.SPEND);
            return true;
        }

        private static boolean undo() {
            if (PLACED.isEmpty()) return false;
            PLACED.removeLast();
            StatusSections.cue(StatusSections.Cue.UNDO);
            return true;
        }

        /** One request for every point placed ("STR:1,AGI:2"); the server checks it against what you have. */
        private static boolean confirm() {
            if (PLACED.isEmpty()) return false;
            StringBuilder arg = new StringBuilder();
            java.util.Arrays.fill(FLASH, false);
            for (Stat s : Stat.values()) {
                int n = placed(s);
                if (n == 0) continue;
                if (!arg.isEmpty()) arg.append(',');
                arg.append(s.name()).append(':').append(n);
                FLASH[s.ordinal()] = true;
            }
            ClientPacketDistributor.sendToServer(new ProgressionPayloads.StatusAction("spend_many", arg.toString()));
            PLACED.clear();
            confirmedAt = System.currentTimeMillis();
            StatusSections.cue(StatusSections.Cue.CONFIRM);
            return true;
        }
    }
}
