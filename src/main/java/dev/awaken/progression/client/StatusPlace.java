package dev.awaken.progression.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Figure;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Journal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * K, Status, on the band's stage (SPEC §57.4): you, standing in what you wear and looking at the pointer (the real player,
 * as the bag draws you, so your body is under your armour); your level and rank as numbers; what the next levels open; your
 * six stats, placed then confirmed with a hold; and every Truth as a mark, lit when it's true and amber when it's close. The
 * panel at the right speaks about the mark you point at, and otherwise says what happened lately and holds what only
 * applies now (a decision waiting, this gate, the Deep Pack, this room, your mount). Nothing scrolls but that panel.
 */
public final class StatusPlace implements BandPlaces.Place {
    private static final float X0 = BandPlaces.X0, Y0 = BandPlaces.Y0, MID = X0 + 452F, PANEL = BandPlaces.X1 - 488F;
    /** The old sections this place replaces, by their order on the page (SPEC §30.1): the rest still show in the panel. */
    private static final Set<Integer> REPLACED = Set.of(0, 10, 18, 20, 710, 720, 735);
    private static final float HOLD_SECONDS = .6F;

    private final SectionHost host = new SectionHost();
    private final CoreSections.TruthList near = new CoreSections.TruthList(CoreSections.TruthList.Kind.NEAR);
    private List<Defs.LevelRoadDef> road = List.of();
    private int roadFrom = -1, stat = -1, mark = -1, pointed = -1;
    private boolean holdKey, holdMouse;
    private float held;
    private @Nullable String tipTitle;
    private String tipMore = "";

    private static boolean replaced(StatusSections.Section s) {
        return REPLACED.contains(s.order());
    }

    @Override
    public void opened(String... focus) {
        mark = -1;
        host.focus(focus);
    }

    private List<Defs.LevelRoadDef> ahead(int level) {
        var map = Content.LEVEL_ROAD.map;
        if (map.size() != roadFrom) {
            List<Defs.LevelRoadDef> l = new ArrayList<>(map.values());
            l.sort(Comparator.comparingInt(Defs.LevelRoadDef::level).thenComparing(Defs.LevelRoadDef::title));
            road = List.copyOf(l);
            roadFrom = map.size();
        }
        List<Defs.LevelRoadDef> out = new ArrayList<>();
        for (Defs.LevelRoadDef d : road) if (d.level() > level && out.size() < 3) out.add(d);
        return out;
    }

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        Minecraft mc = Minecraft.getInstance();
        Profile p = StatusSheet.profile();
        tipTitle = null;
        if (mc.player == null) return;
        String name = mc.player.getName().getString();
        if (!p.awakened()) {
            figure(s, mc, mx, my);
            s.lit(name, MID, Y0 + 40, Sheet.TITLE, Glass.a(Glass.TITLE, 1F));
            s.lines("You haven't awakened yet. Keep exploring: something out there is waiting for you to find it.", MID, Y0 + 84, 520, Sheet.BODY, Glass.a(s.label, 1F));
            return;
        }
        figure(s, mc, mx, my);
        var title = p.title().map(Content.TITLES::get).orElse(null);
        float ny = s.linesCentred(name, X0 + 210, BandPlaces.Y1 - 56, 420, Sheet.TITLE, Glass.a(Glass.TITLE, 1F));
        if (title != null) s.linesCentred("“" + title.name() + "”", X0 + 210, ny, 420, Sheet.BODY, Glass.a(Glass.AMBER, 1F));
        sheet(s, hot, p, mx, my, dt);
        panel(s, hot, p, mx, my);
        if (tipTitle != null) Kit.tip(s, mx, my, tipTitle, tipMore.isEmpty() ? List.of() : List.of(tipMore));
    }

    /** You, as the bag draws you: the real player, armour and all, turning to the pointer, on the band's lit floor. */
    private static void figure(Sheet s, Minecraft mc, float mx, float my) {
        float x0 = X0, x1 = X0 + 420, y0 = Y0 - 6, y1 = BandPlaces.Y1 - 70;
        float scale = (y1 - y0) * .40F;
        // the floor under your feet (the figure stands centred in its box, 1.8 blocks tall)
        Kit.floor(s, (x0 + x1) / 2F, (y0 + y1) / 2F + scale * .9F + 8, 190);
        boolean on = mx > -9000;
        float gx = on ? s.ox + mx * s.k : s.gx((x0 + x1) / 2F), gy = on ? s.oy + my * s.k : s.gy(y0 + 150);
        Figure.followsMouse(s.g, s.gx(x0), s.gy(y0), s.gx(x1), s.gy(y1), Math.round(scale * s.k), .0625F, gx, gy,
                mc.player);
    }

    private void sheet(Sheet s, Hot hot, Profile p, float mx, float my, float dt) {
        float x = MID, y = Y0;
        // level and rank, as numbers
        Rank rank = p.rank();
        float lw = Kit.number(s, String.valueOf(p.level()), "Level", x, y, 9F, Glass.TITLE);
        float rx = x + Math.max(lw, 80) + 36;
        Kit.number(s, rank.label, "Rank", rx, y, 9F, Sheet.RANK[Math.clamp(rank.ordinal(), 0, 5)]);
        Rank next = rank.next();
        float tx = rx + 110;
        String when = p.reassessmentReady() ? "Your trial is ready" : next == null ? "The highest rank" : next.label + "-rank at level " + next.levelRequired;
        float wy = s.lines(when, tx, y + 26, PANEL - 16 - tx, Sheet.BODY, Glass.a(p.reassessmentReady() ? Glass.AMBER : s.label, 1F)) + 4;
        var path = p.path().map(Content.PATHS::get).orElse(null);
        if (path != null) s.lines("Path of the " + path.name(), tx, wy, PANEL - 16 - tx, Sheet.BODY, Glass.a(s.label, 1F));
        if (next != null && Sheet.in(mx, my, rx, y, rx + 96, y + 100)) {
            List<String> opens = WaitingSection.nextRankOpens(Minecraft.getInstance().player, next);
            tip(next.label + "-rank at level " + next.levelRequired, p.reassessmentReady() ? "Your trial is ready at your Spire."
                    : opens.isEmpty() ? "Then its Reassessment." : "It opens what waits for you: " + String.join(", ", opens) + ".");
        }
        y += 112;
        // XP, as segments
        double need = Math.max(1, p.xpToNext()), frac = Math.clamp(p.xp() / need, 0D, 1D);
        int segs = 30;
        for (int i = 0; i < segs; i++) {
            float sx = x + i * 16;
            boolean full = i < (int) (frac * segs), part = i < frac * segs;
            s.fill(sx, y, sx + 13, y + 8, full ? Glass.a(Glass.GOOD, 1F) : part ? Glass.a(Glass.GOOD, .5F) : Sheet.argb(0x05080F, .6F));
        }
        s.text(String.format("%,.0f / %,.0f XP", p.xp(), need), x, y + 16, Sheet.LABEL, Glass.a(s.label, 1F));
        y += 50;
        // what the next levels open
        List<Defs.LevelRoadDef> stops = ahead(p.level());
        if (!stops.isEmpty()) {
            s.caps("What comes next", x, y, Glass.a(s.label, 1F));
            float cx = x, tall = 0;
            for (Defs.LevelRoadDef d : stops) tall = Math.max(tall, 34 + s.tall(d.title(), 136, Sheet.LABEL) + 10);
            for (Defs.LevelRoadDef d : stops) {
                boolean over = Sheet.in(mx, my, cx, y + 24, cx + 158, y + 24 + tall);
                Kit.well(s, cx, y + 24, cx + 158, y + 24 + tall, Kit.Lit.KNOWN, over);
                s.text("Level " + d.level(), cx + 12, y + 34, Sheet.BODY, Glass.a(Glass.TITLE, 1F));
                s.lines(d.title(), cx + 12, y + 58, 136, Sheet.LABEL, Glass.a(Glass.HORIZON, 1F));
                if (over) tip("Level " + d.level() + " · " + d.title(), d.text());
                cx += 166;
            }
            y += 24 + tall + 20;
        }
        float under = stats(s, hot, p, x, y, mx, my, dt);
        truths(s, hot, p, x, under + 14, mx, my);
    }

    /** The six stats and the line that says what the one in focus does. Returns the y under it all. */
    private float stats(Sheet s, Hot hot, Profile p, float x, float y, float mx, float my, float dt) {
        List<Stat> placed = StatusSheet.PLACED;
        while (placed.size() > p.unspent()) placed.removeLast();
        int unspent = p.unspent(), left = unspent - placed.size();
        s.caps("Stats", x, y + 9, Glass.a(s.label, 1F));
        float cx = x + s.width("STATS", Sheet.LABEL) + 14;
        if (unspent > 0) Kit.chip(s, net.minecraft.world.item.ItemStack.EMPTY, left + " to place", cx, y, Glass.AMBER);
        y += 44;
        long since = System.currentTimeMillis() - StatusSheet.confirmedAt;
        int over = -1;
        for (Stat st : Stat.values()) {
            int i = st.ordinal();
            float c0 = x + i * 82, c1 = c0 + 78;
            boolean hover = Sheet.in(mx, my, c0, y, c1, y + 96), hot1 = hover || i == stat;
            if (hover) over = i;
            int pend = StatusSheet.placed(st);
            boolean flash = since < 600 && StatusSheet.FLASH[i];
            if (hot1) Kit.well(s, c0, y, c1, y + 96, i == stat ? Kit.Lit.FOCUS : Kit.Lit.KNOWN, hover);
            else if (flash) s.fill(c0, y, c1, y + 96, Glass.a(Glass.AMBER, .22F * (1 - since / 600F)));
            String v = String.valueOf(p.stat(st) + pend);
            s.lit(v, c0 + 39 - s.width(v, 5F) / 2F, y + 8, 5F, Glass.a(pend > 0 || flash ? Glass.AMBER : Glass.TITLE, 1F));
            s.text(st.name(), c0 + 39 - s.width(st.name(), Sheet.LABEL) / 2F, y + 54, Sheet.LABEL, Glass.a(hot1 ? Glass.TITLE : s.label, 1F));
            if (pend > 0) s.text("+" + pend, c1 - s.width("+" + pend, Sheet.LABEL) - 4, y + 4, Sheet.LABEL, Glass.a(Glass.AMBER, 1F));
            if (left > 0) {
                Kit.mark(s, c0 + 39, y + 82, 9, Glass.AMBER, hot1 ? .3F : 0F, hot1 ? 1F : .7F, false);
                s.text("+", c0 + 39 - s.width("+", Sheet.LABEL) / 2F, y + 76, Sheet.LABEL, Glass.a(Glass.AMBER, 1F));
            }
            hot.add(c0, y, c1, y + 96, () -> {
                focusOn(i);
                StatusSheet.StatLine.place(st);
            });
        }
        int shown = over >= 0 ? over : stat;
        String line = shown >= 0 ? Stat.values()[shown].displayName + ". " + StatusSheet.SAYS[shown]
                : !placed.isEmpty() ? placed.size() + " of " + unspent + " placed. Nothing is spent until you confirm."
                : unspent > 0 ? "Click a stat to raise it, then confirm." : "";
        float ly = y + 106;
        if (!line.isEmpty()) ly = s.lines(line, x, ly, 492, Sheet.BODY, Glass.a(shown >= 0 ? Glass.TEXT : placed.isEmpty() ? s.label : Glass.AMBER, 1F)) + 6;
        // a hold confirms: placed points can't be taken back once they're spent
        if (!placed.isEmpty()) {
            float bw = Kit.buttonWidth(s, "Enter", "Hold to confirm");
            boolean overC = Sheet.in(mx, my, x, ly, x + bw, ly + Kit.BUTTON_H);
            if ((holdKey || holdMouse && overC) && held < 1F) held += dt / HOLD_SECONDS;
            else if (!holdKey && !(holdMouse && overC)) held = 0F;
            Kit.button(s, "Enter", "Hold to confirm", x, ly, Kit.Look.PRIMARY, overC, true, held);
            hot.add(x, ly, x + bw, ly + Kit.BUTTON_H, () -> holdMouse = true);
            float ux = x + bw + 10, uw = Kit.buttonWidth(s, "Bksp", "Undo");
            Kit.button(s, "Bksp", "Undo", ux, ly, Kit.Look.QUIET, Sheet.in(mx, my, ux, ly, ux + uw, ly + Kit.BUTTON_H), true, 0F);
            hot.add(ux, ly, ux + uw, ly + Kit.BUTTON_H, StatusSheet.StatLine::undo);
            if (held >= 1F) {
                held = 0F;
                holdKey = holdMouse = false;
                StatusSheet.StatLine.confirm();
            }
            ly += Kit.BUTTON_H + 8;
        } else held = 0F;
        return ly;
    }

    /** Focus moves to a stat: the narrator says what it is and does. */
    private void focusOn(int i) {
        if (i == stat) return;
        stat = i;
        Profile p = StatusSheet.profile();
        Stat st = Stat.values()[i];
        int left = p.unspent() - StatusSheet.PLACED.size();
        StatusSections.narrate(st.displayName + " " + (p.stat(st) + StatusSheet.placed(st)) + ". " + StatusSheet.SAYS[i]
                + (left > 0 ? " " + left + (left == 1 ? " point" : " points") + " to place." : ""));
    }

    /** Every Truth as a mark: yours lit, the closest three amber, the rest dark. Nothing is named until it's yours or close. */
    private void truths(Sheet s, Hot hot, Profile p, float x, float y, float mx, float my) {
        int total = Content.TRUTHS.map.size(), mine = p.truthsTrue().size();
        if (total <= 0) return;
        boolean open = dev.awaken.progression.Progression.truthsOpen(p);
        s.caps("Truths · " + mine + " of " + total, x, y, Glass.a(s.label, 1F));
        if (!open) {
            s.lines("Truths open at level 5. Anything you've done by then counts.", x, y + 26, PANEL - 24 - x, Sheet.BODY, Glass.a(s.label, 1F));
            return;
        }
        int close = near.ids().size(), per = 16;
        pointed = -1;
        for (int i = 0; i < total; i++) {
            float cx = x + 15 + (i % per) * 31, cy = y + 40 + (i / per) * 31;
            boolean true1 = i < mine, near1 = !true1 && i < mine + close, on = i == mark;
            boolean over = Sheet.in(mx, my, cx - 16, cy - 16, cx + 16, cy + 16);
            Kit.mark(s, cx, cy, 15, true1 ? Glass.GOOD : near1 ? Glass.AMBER : 0x6F829C, true1 ? .35F : 0F, true1 || near1 ? 1F : .45F, on || over);
            int index = i;
            hot.add(cx - 16, cy - 16, cx + 16, cy + 16, () -> {
                mark = mark == index ? -1 : index;
                StatusSections.cue(StatusSections.Cue.PAGE);
            });
            if (over && (true1 || near1)) pointed = i; // the panel reads it while the pointer rests there
        }
    }

    /** The Truth the panel reads: the one clicked, or the one under the pointer; -1 for none. */
    private int reading() {
        return mark >= 0 ? mark : pointed;
    }

    private void panel(Sheet s, Hot hot, Profile p, float mx, float my) {
        float x = PANEL + 24, y = Y0;
        s.fill(PANEL, Y0, PANEL + 1, BandPlaces.Y1, Glass.a(Glass.EDGE, .2F));
        int at = reading();
        List<String> mine = new ArrayList<>(p.truthsTrue());
        mine.sort(Comparator.naturalOrder());
        List<String> close = near.ids();
        if (at >= 0) {
            boolean got = at < mine.size(), nearly = !got && at < mine.size() + close.size();
            if (got || nearly) {
                String id = got ? mine.get(at) : close.get(at - mine.size());
                var d = Content.TRUTHS.get(id);
                s.caps(got ? "True" : "Closest to true", x, y, Glass.a(got ? Glass.GOOD : Glass.AMBER, 1F));
                float ty = s.lines(d == null ? id : "“" + d.text() + "”", x, y + 28, 440, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 10;
                if (d != null) {
                    if (!got && d.hint().isPresent()) ty = s.lines(d.hint().get(), x, ty, 440, Sheet.BODY, Glass.a(s.label, 1F)) + 10;
                    if (!got) {
                        float f = (float) Math.clamp(d.condition().progress(p, null), 0D, 1D);
                        s.fill(x, ty, x + 440, ty + 8, Sheet.argb(0x05080F, .6F));
                        s.fill(x, ty, x + 440 * f, ty + 8, Glass.a(Glass.AMBER, 1F));
                        ty += 24;
                    }
                    s.caps(got ? "It gave you" : "It gives", x, ty, Glass.a(s.label, 1F));
                    s.lines(Rewards.describe(d.reward()), x, ty + 22, 440, Sheet.BODY, Glass.a(got ? Glass.TEXT : Glass.AMBER, 1F));
                }
            } else {
                s.caps("Not yet", x, y, Glass.a(s.label, 1F));
                s.lines("A Truth comes true by itself when you live it.", x, y + 28, 440, Sheet.BODY, Glass.a(s.label, 1F));
            }
            return;
        }
        // what happened lately
        List<Journal.Entry> entries = Journal.entries();
        if (!entries.isEmpty()) {
            s.caps("Lately", x, y, Glass.a(s.label, 1F));
            y += 26;
            for (int i = 0; i < Math.min(3, entries.size()); i++) {
                Journal.Entry e = entries.get(i);
                float h = s.tall(e.title(), 440, Sheet.BODY) + 2 + s.tall(e.line(), 440, Sheet.LABEL) + 10;
                if (i > 0 && y + h > Y0 + 330) break; // an entry shows whole or waits in the Journal: never half of one
                y = s.lines(e.title(), x, y, 440, Sheet.BODY, Glass.a(e.colour() & 0xFFFFFF, 1F)) + 2;
                if (!e.line().isBlank()) y = s.lines(e.line(), x, y, 440, Sheet.LABEL, Glass.a(s.label, 1F));
                y += 10;
            }
            y += 14;
        }
        // deeds: three numbers
        s.caps("Deeds", x, y, Glass.a(s.label, 1F));
        float dx = x;
        String[][] deeds = {{"awaken:kills", "Monsters slain"}, {"awaken:gates_cleared", "Gates cleared"}, {"awaken:bosses_killed", "Bosses felled"}};
        for (String[] d : deeds) dx += Kit.number(s, String.format("%,d", p.counter(d[0])), d[1], dx, y + 24, Sheet.TITLE, Glass.TITLE) + 34;
        y += 96;
        // what only applies now, and what hasn't moved yet (titles, latents), as it was written
        host.draw(s, hot, StatusSections.Tab.STATUS, sec -> !replaced(sec), PANEL + 24, y, BandPlaces.X1, BandPlaces.Y1, mx, my, "", Sheet.calm());
    }

    private void tip(String title, String more) {
        tipTitle = title;
        tipMore = more;
    }

    @Override
    public void hints(List<Kit.Hint> out) {
        Profile p = StatusSheet.profile();
        if (p.unspent() > StatusSheet.PLACED.size() || !StatusSheet.PLACED.isEmpty()) out.add(Kit.Hint.of("Place a point", "Space"));
        if (!StatusSheet.PLACED.isEmpty()) {
            out.add(Kit.Hint.hold("Confirm", "Enter"));
            out.add(Kit.Hint.of("Undo", "Bksp"));
        }
        out.add(Kit.Hint.of("Next stat", "Tab"));
        if (dev.awaken.progression.Progression.truthsOpen(p) && p.truthsTrue().size() + near.ids().size() > 0) out.add(Kit.Hint.of("Truths", "↑", "↓"));
    }

    @Override
    public boolean key(KeyEvent e) {
        Profile p = StatusSheet.profile();
        if (!p.awakened()) return false;
        int k = e.key();
        if (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_RIGHT) {
            focusOn(stat < 0 ? 0 : Math.floorMod(stat + (k == InputConstants.KEY_LEFT ? -1 : 1), 6));
            return true;
        }
        if ((k == InputConstants.KEY_SPACE || k == InputConstants.KEY_EQUALS || k == InputConstants.KEY_ADD) && stat >= 0) {
            StatusSheet.StatLine.place(Stat.values()[stat]);
            return true;
        }
        if ((k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) && !StatusSheet.PLACED.isEmpty()) {
            holdKey = true;
            return true;
        }
        if (k == InputConstants.KEY_BACKSPACE && !StatusSheet.PLACED.isEmpty()) return StatusSheet.StatLine.undo();
        if ((k == InputConstants.KEY_DOWN || k == InputConstants.KEY_UP) && dev.awaken.progression.Progression.truthsOpen(p)) {
            // the Truths you can read (true, then closest to true), one at a time, without the pointer
            int n = p.truthsTrue().size() + near.ids().size();
            if (n > 0) {
                mark = k == InputConstants.KEY_DOWN ? (mark + 1 >= n ? 0 : mark + 1) : mark <= 0 ? n - 1 : mark - 1;
                StatusSections.cue(StatusSections.Cue.PAGE);
                return true;
            }
        }
        return host.key(e, StatusSections.Tab.STATUS, sec -> !replaced(sec));
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) holdKey = false;
        return false;
    }

    @Override
    public void released() {
        holdMouse = false;
    }

    @Override
    public void next(int step) {
        focusOn(stat < 0 ? 0 : Math.floorMod(stat + step, 6));
    }

    @Override
    public boolean back() {
        if (mark >= 0) {
            mark = -1;
            return true;
        }
        return false;
    }

    @Override
    public String backWords() {
        return mark >= 0 ? "Back" : "Close";
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        return mx >= PANEL && host.scroll(amount);
    }
}
