package dev.awaken.tasks.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.KeyText;
import dev.awaken.tasks.net.TaskPayloads;
import dev.awaken.tasks.net.TaskPayloads.ChoiceRow;
import dev.awaken.tasks.net.TaskPayloads.NeedRow;
import dev.awaken.tasks.net.TaskPayloads.RecipeRow;
import dev.awaken.tasks.net.TaskPayloads.StepRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * K, Tasks, on the band's stage (SPEC §57; the model is §27.13 and §53, unchanged). One view and no tabs: the step in focus
 * at the left as a picture (what it makes drawn as its recipe, what it takes as the real items with have and need, the way
 * as a needle and a distance), and every other open step at the right in its goal's lane. A click or Tab brings another
 * step into focus; T tracks it, X sets it aside, Z takes that back. Every icon is the real item and every name the item's
 * own, so what you see here is what you'll find in the world.
 */
final class TasksPlace implements BandPlaces.Place {
    private static final String ADD = "hearth/add";
    private static final float HERO_X1 = BandPlaces.X0 + 756F, LANES_X0 = BandPlaces.X1 - 676F;
    private static final String[][] EMBLEM = dev.awaken.tasks.TaskPictures.EMBLEM;

    /** The step in focus ("" for the one on top), the pick looked at on it, and the lane whose Later list is open (-1). */
    private String focus = "", choice = "";
    private int later = -1;
    private String said = "";
    private @Nullable String tipTitle;
    private final List<String> tipMore = new ArrayList<>();
    private final List<ClientMannequin> figures = new ArrayList<>();
    private String figuresFor = "";

    @Override
    public void opened(String... link) {
        focus = "";
        choice = "";
        later = -1;
    }

    // ---- what the page holds ---------------------------------------------------------------------------------------

    private static List<StepRow> lane(TaskPayloads.View v, Goal g) {
        List<StepRow> rows = new ArrayList<>(), optional = new ArrayList<>();
        for (StepRow s : v.steps()) {
            if (s.goal() != g.ordinal() || !s.shown()) continue;
            (s.optional() ? optional : rows).add(s);
        }
        rows.addAll(optional);
        return rows;
    }

    private static List<StepRow> waiting(TaskPayloads.View v, Goal g) {
        List<StepRow> rows = new ArrayList<>();
        for (StepRow s : v.steps()) if (s.goal() == g.ordinal() && !s.shown()) rows.add(s);
        return rows;
    }

    /** Every step on the stage, lane by lane: the order Tab walks. */
    private static List<StepRow> all(TaskPayloads.View v) {
        List<StepRow> out = new ArrayList<>();
        for (Goal g : Goal.values()) out.addAll(lane(v, g));
        return out;
    }

    private @Nullable StepRow focused(TaskPayloads.View v) {
        StepRow s = TaskCache.step(focus);
        if (s != null && s.shown()) return s;
        s = TaskCache.step(v.top());
        if (s != null) return s;
        List<StepRow> all = all(v);
        return all.isEmpty() ? null : all.get(0);
    }

    /** A row that becomes what was picked on it ("Add to your home"): the pick, or null while there isn't one. */
    private static @Nullable ChoiceRow became(StepRow s) {
        if (!s.key().equals(ADD)) return null;
        for (ChoiceRow c : s.choices()) if (c.id().equals(s.chosen())) return c;
        return null;
    }

    static ItemStack stack(String id) {
        Identifier at = Identifier.tryParse(id);
        if (at == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(at).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    static ItemStack emblem(int goal) {
        if (goal < 0 || goal >= EMBLEM.length) return ItemStack.EMPTY;
        ItemStack s = stack(EMBLEM[goal][0]);
        return s.isEmpty() ? stack(EMBLEM[goal][1]) : s;
    }

    private static String laneName(int goal) {
        Goal[] all = Goal.values();
        return goal < 0 || goal >= all.length ? "" : all[goal] == Goal.RANK ? "Gates" : all[goal].label;
    }

    /** The real items that picture a step: its picks, else what it takes, else the item its source named, else its goal's. */
    static List<ItemStack> pictures(StepRow s) {
        List<ItemStack> out = new ArrayList<>();
        ChoiceRow pick = became(s);
        if (pick == null && !s.choices().isEmpty()) {
            for (ChoiceRow c : s.choices()) {
                if (out.size() >= 4 || c.show().isEmpty()) continue;
                ItemStack st = WhereFrom.icon(c.show().get(Math.min(1, c.show().size() - 1)));
                if (!st.isEmpty()) out.add(st);
            }
        }
        if (out.isEmpty()) {
            for (NeedRow n : pick != null ? pick.needs() : s.needs()) {
                if (out.size() >= 4 || n.item().isEmpty()) continue;
                ItemStack st = WhereFrom.icon(n.item());
                if (!st.isEmpty()) out.add(st);
            }
        }
        if (out.isEmpty() && !s.extra().icon().isEmpty()) {
            ItemStack st = stack(s.extra().icon());
            if (!st.isEmpty()) out.add(st);
        }
        if (out.isEmpty()) out.add(emblem(s.goal()));
        return out;
    }

    /** A need's name: the item's own for a plain item (what the game calls it everywhere else), else the step's words. */
    static String needName(NeedRow n) {
        if (n.item().isEmpty() || n.item().startsWith("#")) return n.label();
        ItemStack st = stack(n.item());
        return st.isEmpty() ? n.label() : st.getHoverName().getString();
    }

    private static int met(List<NeedRow> needs) {
        int n = 0;
        for (NeedRow r : needs) if (r.met()) n++;
        return n;
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    /**
     * Nothing on this page is ever cut off (SPEC §57.6): every name and line is drawn whole, what holds it grows to fit, and
     * if the step in focus or the lanes come out taller than the stage, that side scrolls. These are how far each is
     * scrolled and how tall each last drew.
     */
    private float heroScroll, lanesScroll, heroTall, lanesTall;
    private String heroFor = "";

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        TaskCache.drawn();
        tipTitle = null;
        tipMore.clear();
        TaskPayloads.View v = TaskCache.view;
        StepRow f = focused(v);
        if (f == null) {
            s.lit("Nothing to do this minute.", BandPlaces.X0, BandPlaces.Y0 + 220, Sheet.TITLE, Glass.a(Glass.TITLE, 1F));
            s.text("Look around. What you find brings the next step.", BandPlaces.X0, BandPlaces.Y0 + 262, Sheet.BODY, Glass.a(s.label, 1F));
            return;
        }
        if (later >= 0 && waiting(v, Goal.values()[later]).isEmpty()) later = -1;
        String showing = later >= 0 ? "later/" + later : f.key();
        if (!showing.equals(heroFor)) {
            heroFor = showing;
            heroScroll = 0;
        }
        // the step in focus, as tall as its words
        float top = BandPlaces.Y0, bottom = BandPlaces.Y1 - (later >= 0 ? 0 : Kit.BUTTON_H + 12);
        heroScroll = Math.clamp(heroScroll, 0F, Math.max(0F, heroTall - (bottom - top)));
        boolean in = Sheet.in(mx, my, BandPlaces.X0 - 8, top, HERO_X1 + 8, bottom);
        s.g.enableScissor(Math.round(BandPlaces.X0) - 8, Math.round(top), Math.round(HERO_X1) + 8, Math.round(bottom));
        hot.clip(BandPlaces.X0 - 8, top, HERO_X1 + 8, bottom);
        float end = later >= 0 ? laterList(s, hot, v, top - heroScroll, in ? mx : -10000, in ? my : -10000)
                : hero(s, hot, v, f, top - heroScroll, bottom - top, in ? mx : -10000, in ? my : -10000);
        heroTall = end - (top - heroScroll);
        hot.unclip();
        s.g.disableScissor();
        Kit.scrollbar(s, HERO_X1 + 8, top, bottom, heroScroll, heroTall);
        if (later < 0) actions(s, hot, v, f, became(f), mx, my);
        // every other open step, in its goal's lane
        lanesScroll = Math.clamp(lanesScroll, 0F, Math.max(0F, lanesTall - (BandPlaces.Y1 - BandPlaces.Y0)));
        boolean inLanes = Sheet.in(mx, my, LANES_X0 - 8, BandPlaces.Y0, BandPlaces.X1 + 8, BandPlaces.Y1);
        s.g.enableScissor(Math.round(LANES_X0) - 8, Math.round(BandPlaces.Y0) - 8, Math.round(BandPlaces.X1) + 8, Math.round(BandPlaces.Y1));
        hot.clip(LANES_X0 - 8, BandPlaces.Y0, BandPlaces.X1 + 8, BandPlaces.Y1);
        float lend = lanes(s, hot, v, f, BandPlaces.Y0 - lanesScroll, inLanes ? mx : -10000, inLanes ? my : -10000);
        lanesTall = lend - (BandPlaces.Y0 - lanesScroll);
        hot.unclip();
        s.g.disableScissor();
        Kit.scrollbar(s, BandPlaces.X1 + 10, BandPlaces.Y0, BandPlaces.Y1, lanesScroll, lanesTall);
        undo(s, hot, mx, my);
        String words = KeyText.resolve(f.title());
        if (!words.equals(said)) {
            said = words;
            Sheet.say(words + ". " + (f.extra().opens().isEmpty() ? f.why() : f.extra().opens()));
        }
        if (tipTitle != null) Kit.tip(s, mx, my, tipTitle, tipMore);
    }

    /** What a step makes: its recipe, the thing's id and name, and how many you have of how many it takes (0: it isn't counted). */
    private record Made(RecipeRow recipe, String item, String name, int have, int need) {}

    /** The pick being looked at: the one clicked, else the one chosen, else the only one there is. */
    private @Nullable ChoiceRow looked(StepRow f) {
        List<ChoiceRow> cs = f.choices();
        for (ChoiceRow c : cs) if (c.id().equals(choice)) return c;
        for (ChoiceRow c : cs) if (c.id().equals(f.chosen())) return c;
        return cs.size() == 1 ? cs.get(0) : null;
    }

    /** The step in focus from y0 down, with {@code view} of height to fill. Returns the y under its last line. */
    private float hero(Sheet s, Hot hot, TaskPayloads.View v, StepRow f, float y0, float view, float mx, float my) {
        float x = BandPlaces.X0, y = y0, w = HERO_X1 - x;
        boolean tracked = f.key().equals(v.pinned()), top = f.key().equals(v.top());
        ChoiceRow pick = became(f);
        // whose it is, and why it's in front
        String eyebrow = tracked ? "Tracked" : top ? "Do this next" : f.optional() ? "When you want" : "Also open to you";
        s.caps(eyebrow, x, y + 9, Glass.a(tracked || top ? Glass.AMBER : s.label, 1F));
        float cx = x + s.width(eyebrow.toUpperCase(java.util.Locale.ROOT), Sheet.LABEL) + 14;
        cx += Kit.chip(s, emblem(f.goal()), laneName(f.goal()), cx, y, Glass.EDGE) + 8;
        if (f.extra().ready()) Kit.chip(s, ItemStack.EMPTY, "Ready", cx, y, Glass.GOOD);
        y += 46;
        // its name, what it gets you and why: every line of each
        y = s.lines(KeyText.resolve(pick != null ? pick.title() : f.title()), x, y, w, 4F, Glass.a(Glass.TITLE, 1F)) + 4;
        String lead = pick != null ? pick.sub() : f.extra().opens(), why = pick != null ? pick.detail() : f.why();
        if (!lead.isEmpty()) y = s.lines(KeyText.resolve(lead), x, y, w, Sheet.BODY, Glass.a(Glass.TEXT, 1F));
        if (!why.isEmpty()) y = s.lines(KeyText.resolve(why), x, y, w, Sheet.BODY, Glass.a(s.label, 1F));
        y += 14;
        // what it shows, and what must follow it
        boolean picking = pick == null && !f.choices().isEmpty();
        ChoiceRow looked = picking ? looked(f) : null;
        List<NeedRow> needs = new ArrayList<>(picking ? (looked == null ? List.<NeedRow>of() : looked.needs()) : pick != null ? pick.needs() : f.needs());
        List<Made> made = new ArrayList<>();
        if (!picking) {
            for (NeedRow n : new ArrayList<>(needs)) {
                RecipeRow r = n.met() || made.size() >= 2 ? null : TaskCache.view.recipe(n.item());
                if (r == null) continue;
                made.add(new Made(r, n.item(), needName(n), n.have(), n.need()));
                needs.remove(n);
            }
            // a step that is itself the making of one thing (a War Anvil): that thing's recipe, and what it takes below
            RecipeRow own = made.isEmpty() ? TaskCache.view.recipe(f.extra().icon()) : null;
            if (own != null) made.add(new Made(own, f.extra().icon(), stack(f.extra().icon()).getHoverName().getString(), 0, 0));
        }
        List<NeedRow> items = new ArrayList<>(), conds = new ArrayList<>();
        for (NeedRow n : needs) (n.item().isEmpty() ? conds : items).add(n);
        BlockPos at = f.where().orElse(null);
        boolean wayPicture = !picking && made.isEmpty() && items.isEmpty() && at != null;
        String then = KeyText.resolve(f.extra().then());
        float itemsH = 0;
        for (int i = 0; i < items.size(); i += 6) {
            float rowH = 0;
            for (int j = i; j < Math.min(items.size(), i + 6); j++) rowH = Math.max(rowH, Kit.needHeight(s, needName(items.get(j)), items.get(j).need()));
            itemsH += rowH + 8;
        }
        float after = itemsH + (items.isEmpty() ? 0 : 30) + conds.size() * 28 + (needs.isEmpty() ? 0 : 6) + (at != null && !wayPicture ? 52 : 0) + s.tall(then, w, Sheet.BODY);
        float room = y0 + view - y - after;
        // the picture: the picks side by side, what it makes as its recipe, the way as a needle, or the thing itself
        if (picking) y += picks(s, hot, f, looked, x, y, room, mx, my);
        else if (!made.isEmpty()) y += recipes(s, made, x, y, room, mx, my);
        else if (items.isEmpty()) {
            float h = Math.clamp(room - 10, 150F, 260F);
            if (wayPicture) way(s, f, x, y, h);
            else thing(s, pictures(f).get(0), (x + HERO_X1) / 2F, y, h);
            y += h + 10;
        }
        // what it takes: the real items, six to a row, and under them where the one you're missing comes from
        NeedRow from = null;
        for (int i = 0; i < items.size(); i += 6) {
            float rowH = 0, nx = x;
            for (int j = i; j < Math.min(items.size(), i + 6); j++) {
                NeedRow n = items.get(j);
                String name = needName(n);
                rowH = Math.max(rowH, Kit.needHeight(s, name, n.need()));
                if (Kit.need(s, WhereFrom.icon(n.item()), name, n.have(), n.need(), nx, y, mx, my) && !n.met()) from = n;
                nx += Kit.NEED_W + 12;
            }
            y += rowH + 8;
        }
        if (!items.isEmpty()) {
            if (from == null) for (NeedRow n : items) if (!n.met() && !WhereFrom.line(n.item()).isEmpty()) {
                from = n;
                break;
            }
            String line = from == null ? "" : WhereFrom.line(from.item());
            if (!line.isEmpty()) y = s.lines(needName(from) + ": " + line, x, y, w, Sheet.LABEL, Glass.a(s.label, 1F)) + 8;
        }
        // what it asks for that isn't a thing
        for (NeedRow n : conds) {
            Kit.condition(s, n.need() > 1 ? n.label() + "  " + Math.min(n.have(), n.need()) + "/" + n.need() : n.label(), n.met(), x, y);
            y += 28;
        }
        if (!needs.isEmpty()) y += 6;
        // where, and what comes after
        if (at != null && !wayPicture) {
            float[] way = bearing(at);
            Kit.needle(s, x + 22, y + 22, 22, way[1]);
            s.text(wayWords(at, way[0]), x + 58, y + 14, Sheet.BODY, Glass.a(Glass.TEXT, 1F));
            y += 52;
        }
        if (!then.isEmpty()) y = s.lines(then, x, y, w, Sheet.BODY, Glass.a(s.label, .85F));
        return y + 8;
    }

    private void tip(String title, String more) {
        tipTitle = title;
        tipMore.clear();
        if (!more.isEmpty()) tipMore.add(more);
    }

    /** The thing itself, large, on the band's lit floor. */
    private static void thing(Sheet s, ItemStack stack, float cx, float y, float h) {
        float size = Math.min(176, h - 36);
        Kit.floor(s, cx, y + h - 18, 240);
        s.item(stack, cx - size / 2F, y + (h - 30 - size) / 2F, size);
    }

    /** How far, which way and (up is ahead) where: {distance, degrees from your facing}. */
    private static float[] bearing(BlockPos at) {
        var p = Minecraft.getInstance().player;
        if (p == null) return new float[] {0, Float.NaN};
        double dx = at.getX() + .5 - p.getX(), dz = at.getZ() + .5 - p.getZ();
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        return new float[] {d, d < 3 ? Float.NaN : Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot())};
    }

    private static String compass(BlockPos at) {
        var p = Minecraft.getInstance().player;
        if (p == null) return "";
        double dx = at.getX() + .5 - p.getX(), dz = at.getZ() + .5 - p.getZ();
        String[] ways = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return ways[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0), 8L)];
    }

    private static String blocks(float d) {
        long round = d < 100 ? Math.round(d / 5) * 5 : Math.round(d / 10) * 10;
        return String.format(java.util.Locale.ROOT, "%,d", round);
    }

    private static String wayWords(BlockPos at, float d) {
        return d < 16 ? "Close by" : blocks(d) + " blocks " + compass(at);
    }

    /** A step that is a place to go: the needle, how far, and what waits there. */
    private static void way(Sheet s, StepRow f, float x, float y, float h) {
        BlockPos at = f.where().orElseThrow();
        float[] w = bearing(at);
        float r = Math.min(96, (h - 30) / 2F), cy = y + (h - 30) / 2F, cx = x + 150;
        Kit.floor(s, (x + HERO_X1) / 2F, y + h - 18, 320);
        Kit.needle(s, cx, cy, r, w[1]);
        boolean near = w[0] < 16;
        Kit.number(s, near ? "Here" : blocks(w[0]), near ? "close by" : "blocks " + compass(at), cx + r + 56, cy - 34, near ? 5F : 9F, Glass.TITLE);
        s.item(pictures(f).get(0), HERO_X1 - 200, cy - 76, 152);
    }

    /** What the step makes, each drawn as its recipe with the thing itself beside it. Returns the height it took. */
    private float recipes(Sheet s, List<Made> made, float x, float y, float room, float mx, float my) {
        float each = (HERO_X1 - x) / made.size();
        // as large as the room allows: the game's own grid, read at a glance
        float cell = 44, result = 128, tallest = 0;
        for (Made m : made) tallest = Math.max(tallest, Kit.recipeHeight(m.recipe.height(), 60, 180));
        if (room >= tallest + 60 && Kit.recipeWidth(3, 60, 180) <= each - 20) {
            cell = 60;
            result = 180;
        }
        float h = 0;
        for (Made m : made) h = Math.max(h, Kit.recipeHeight(m.recipe.height(), cell, result));
        float under = 0;
        for (int i = 0; i < made.size(); i++) {
            Made m = made.get(i);
            RecipeRow r = m.recipe;
            List<ItemStack> cells = new ArrayList<>(r.cells().size());
            long turn = System.currentTimeMillis() / 1000;
            for (List<String> ids : r.cells()) cells.add(ids.isEmpty() ? ItemStack.EMPTY : stack(ids.get((int) (turn % ids.size()))));
            float w = Kit.recipeWidth(r.width(), cell, result), rx = x + i * each + Math.max(0, (each - w) / 2F);
            float ry = y + (h - Kit.recipeHeight(r.height(), cell, result)) / 2F;
            Kit.recipe(s, r.width(), r.height(), cells, stack(m.item), rx, ry, cell, result);
            // its own name, and how many you have: under it, on as many lines as the name takes
            String count = m.need > 0 ? "  " + Math.min(m.have, m.need) + "/" + m.need : "";
            float ty = y + h + 12, mid = x + i * each + each / 2F;
            float end = s.linesCentred(m.name, mid, ty, each - 24, Sheet.BODY, Glass.a(Glass.TITLE, 1F));
            if (!count.isEmpty()) {
                s.centre(count.trim(), mid, end, Sheet.BODY, Glass.a(m.have >= m.need ? Glass.GOOD : Glass.WARN, 1F));
                end += s.line(Sheet.BODY);
            }
            under = Math.max(under, end - ty);
        }
        return h + 12 + under + 14;
    }

    /** The picks side by side: armour as figures wearing it, anything else as the things themselves. Returns the height it took. */
    private float picks(Sheet s, Hot hot, StepRow f, @Nullable ChoiceRow looked, float x, float y, float room, float mx, float my) {
        List<ChoiceRow> cs = f.choices();
        int n = cs.size();
        boolean worn = figures(f);
        float w = Math.min(252, (HERO_X1 - x) / n), x0 = x + ((HERO_X1 - x) - w * n) / 2F;
        // every pick's name and line in full: the tallest sets how much the figures get
        float words = 0;
        for (ChoiceRow c : cs) words = Math.max(words, s.tall(c.title(), w - 16, Sheet.BODY) + 2 + s.tall(c.sub(), w - 16, Sheet.LABEL));
        float tall = Math.clamp(room - words - 34, worn ? 150F : 110F, worn ? 320F : 190F), ph = tall + words + 34;
        Kit.floor(s, x0 + w * n / 2F, y + tall - 6, Math.min(360, w * n / 2F + 20));
        for (int i = 0; i < n; i++) {
            ChoiceRow c = cs.get(i);
            float cx = x0 + i * w;
            boolean on = c == looked, mine = c.id().equals(f.chosen()), over = Sheet.in(mx, my, cx, y, cx + w, y + ph - 8);
            if (over || on) s.fill(cx + 6, y, cx + w - 6, y + ph - 8, Glass.a(Glass.EDGE, on ? .10F : .06F));
            if (worn && i < figures.size()) {
                InventoryScreen.extractEntityInInventoryFollowsMouse(s.g, s.gx(cx), s.gy(y + 4), s.gx(cx + w), s.gy(y + tall),
                        Math.round(tall * .41F * s.k), .0625F, s.ox + mx * s.k, s.oy + my * s.k, figures.get(i));
            } else {
                int k = c.show().size(), across = k <= 1 ? 1 : 2, down = (k + across - 1) / across;
                float size = Math.min((w - 24) / across, (tall - 20) / Math.max(1, down)), gx = cx + (w - across * size) / 2F, gy = y + (tall - down * size) / 2F;
                for (int j = 0; j < k; j++) s.item(WhereFrom.icon(c.show().get(j)), gx + (j % across) * size, gy + (j / across) * size, size);
            }
            float ty = s.linesCentred(c.title(), cx + w / 2F, y + tall + 12, w - 16, Sheet.BODY, Glass.a(mine ? Glass.GOOD : Glass.TITLE, 1F)) + 2;
            s.linesCentred(c.sub(), cx + w / 2F, ty, w - 16, Sheet.LABEL, Glass.a(s.label, 1F));
            if (on) s.fill(cx + 20, y + ph - 12, cx + w - 20, y + ph - 9, Glass.a(Glass.AMBER, 1F));
            hot.add(cx, y, cx + w, y + ph - 8, () -> {
                choice = c.id();
                StatusSections.cue(StatusSections.Cue.PAGE);
                Sheet.say(c.title() + ". " + c.sub());
            });
        }
        return ph;
    }

    /** The figures wearing each set this step offers (a body under the armour); false when its picks aren't armour. */
    private boolean figures(StepRow f) {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder id = new StringBuilder(f.key());
        for (ChoiceRow c : f.choices()) {
            if (dev.awaken.armour.ArmourSet.byId(c.id()) == null) return false;
            id.append('|').append(c.id());
        }
        if (mc.level == null) return false;
        if (id.toString().equals(figuresFor)) return true;
        figuresFor = id.toString();
        figures.clear();
        for (ChoiceRow c : f.choices()) {
            ClientMannequin m = new ClientMannequin(mc.level, mc.playerSkinRenderCache());
            m.setId(-2000 - figures.size()); // no server entity has a negative id
            m.setPos(0, -4096, 0);
            for (String piece : c.show()) {
                ItemStack st = WhereFrom.icon(piece);
                Equippable e = st.get(DataComponents.EQUIPPABLE);
                if (e != null) m.setItemSlot(e.slot(), st, true);
            }
            figures.add(m);
        }
        return true;
    }

    private void actions(Sheet s, Hot hot, TaskPayloads.View v, StepRow f, @Nullable ChoiceRow pick, float mx, float my) {
        float x = BandPlaces.X0, y = BandPlaces.Y1 - Kit.BUTTON_H;
        boolean tracked = f.key().equals(v.pinned());
        if (pick == null && !f.choices().isEmpty()) {
            ChoiceRow c = null;
            for (ChoiceRow r : f.choices()) if (r.id().equals(choice)) c = r;
            if (c != null && !c.id().equals(f.chosen())) {
                ChoiceRow take = c;
                String label = "Choose " + take.title();
                float w = Kit.buttonWidth(s, "", label);
                Kit.button(s, "", label, x, y, Kit.Look.PRIMARY, Sheet.in(mx, my, x, y, x + w, y + Kit.BUTTON_H), true, 0F);
                hot.add(x, y, x + w, y + Kit.BUTTON_H, () -> TaskPage.choose(f, take));
                x += w + 12;
            }
            if (f.choices().size() > 1 && !f.key().equals(ADD)) {
                float w = Kit.buttonWidth(s, "", "See them worn");
                Kit.button(s, "", "See them worn", x, y, Kit.Look.PLAIN, Sheet.in(mx, my, x, y, x + w, y + Kit.BUTTON_H), true, 0F);
                hot.add(x, y, x + w, y + Kit.BUTTON_H, () -> SetPreviewScreen.open(f.key()));
                x += w + 12;
            }
        }
        if (pick != null) {
            float w = Kit.buttonWidth(s, "", "Change");
            Kit.button(s, "", "Change", x, y, Kit.Look.PLAIN, Sheet.in(mx, my, x, y, x + w, y + Kit.BUTTON_H), true, 0F);
            hot.add(x, y, x + w, y + Kit.BUTTON_H, () -> {
                TaskCache.act(TaskPayloads.CHOOSE, f.key() + "|");
                StatusSections.cue(StatusSections.Cue.UNDO);
            });
            x += w + 12;
        }
        if (f.key().startsWith("progression:points")) {
            float pw = Kit.buttonWidth(s, "", "Open Status");
            Kit.button(s, "", "Open Status", x, y, Kit.Look.PRIMARY, Sheet.in(mx, my, x, y, x + pw, y + Kit.BUTTON_H), true, 0F);
            hot.add(x, y, x + pw, y + Kit.BUTTON_H, () -> StatusSections.go(StatusSections.Tab.STATUS));
            x += pw + 12;
        }
        String label = tracked ? "Tracked" : "Track";
        float w = Kit.buttonWidth(s, "T", label);
        Kit.button(s, "T", label, x, y, tracked ? Kit.Look.ON : Kit.Look.PLAIN, Sheet.in(mx, my, x, y, x + w, y + Kit.BUTTON_H), true, 0F);
        hot.add(x, y, x + w, y + Kit.BUTTON_H, () -> track(f));
        x += w + 12;
        if (canSetAside(v, f)) {
            float aw = Kit.buttonWidth(s, "X", "Set aside");
            Kit.button(s, "X", "Set aside", x, y, Kit.Look.QUIET, Sheet.in(mx, my, x, y, x + aw, y + Kit.BUTTON_H), true, 0F);
            hot.add(x, y, x + aw, y + Kit.BUTTON_H, () -> setAside(f));
        }
    }

    private static boolean canSetAside(TaskPayloads.View v, StepRow f) {
        return v.capped() && !f.key().equals(v.pinned()) && !f.extra().urgent() && !f.optional() && !f.key().equals(v.top());
    }

    private static void track(StepRow f) {
        boolean tracked = f.key().equals(TaskCache.view.pinned());
        TaskCache.act(tracked ? TaskPayloads.UNPIN : TaskPayloads.PIN, f.key());
        StatusSections.cue(tracked ? StatusSections.Cue.UNDO : StatusSections.Cue.CONFIRM);
        Sheet.say(tracked ? "No longer tracked." : "Tracked. It stays on your screen and points the way.");
    }

    private void setAside(StepRow f) {
        TaskCache.setAside(f);
        StatusSections.cue(StatusSections.Cue.UNDO);
        focus = "";
        choice = "";
    }

    /** "Set aside: X" and Undo, for a few seconds after you do. */
    private void undo(Sheet s, Hot hot, float mx, float my) {
        String title = TaskCache.undoTitle();
        if (title.isEmpty()) return;
        String words = "Set aside: " + title;
        float bw = Kit.buttonWidth(s, "Z", "Undo"), tw = Math.min(s.width(words, Sheet.BODY), 520), th = s.tall(words, 520, Sheet.BODY);
        float w = tw + bw + 48, h = Math.max(56, th + 30);
        float x = BandPlaces.X0 + (756 - w) / 2F, y = BandPlaces.Y1 - Kit.BUTTON_H - 20 - h;
        s.g.nextStratum();
        s.fill(x, y, x + w, y + h, Sheet.argb(0x0F1724, .96F));
        s.outline(x, y, x + w, y + h, 1, Glass.a(Glass.EDGE, .55F));
        s.lines(words, x + 16, y + (h - th) / 2F + 2, 520, Sheet.BODY, Glass.a(Glass.TEXT, 1F));
        float bx = x + w - bw - 8, by = y + (h - Kit.BUTTON_H) / 2F;
        Kit.button(s, "Z", "Undo", bx, by, Kit.Look.PLAIN, Sheet.in(mx, my, bx, by, bx + bw, by + Kit.BUTTON_H), true, 0F);
        hot.add(bx, by, bx + bw, by + Kit.BUTTON_H, TasksPlace::putBack);
    }

    private static void putBack() {
        TaskCache.undo();
        StatusSections.cue(StatusSections.Cue.UNDO);
    }

    // ---- the lanes ---------------------------------------------------------------------------------------------------

    private static final float LABEL_W = 108F, TILE_W = 181F, TILE_GAP = 12F;

    /** How tall a step's tile must be for every line of its name. */
    private static float tileHeight(Sheet s, StepRow r) {
        ChoiceRow pick = became(r);
        return Math.max(92F, 42 + s.tall(KeyText.resolve(pick != null ? pick.title() : r.title()), TILE_W - 18, Sheet.LABEL) + 6);
    }

    /** Every goal's lane from y0 down: its rows as tiles, three across, each as tall as its longest name. Returns the y under the last. */
    private float lanes(Sheet s, Hot hot, TaskPayloads.View v, StepRow f, float y0, float mx, float my) {
        float y = y0;
        for (Goal g : Goal.values()) {
            List<StepRow> rows = lane(v, g), wait = waiting(v, g);
            if (rows.isEmpty() && wait.isEmpty()) continue;
            if (y > y0) s.fill(LANES_X0, y, BandPlaces.X1, y + 1, Glass.a(Glass.EDGE, .14F));
            float cx = LANES_X0 + 48, ly = y + 6;
            s.item(emblem(g.ordinal()), cx - 20, y + 12, 40);
            String name = laneName(g.ordinal()).toUpperCase(java.util.Locale.ROOT);
            s.text(name, cx - s.width(name, Sheet.LABEL) / 2F, y + 57, Sheet.LABEL, Glass.a(s.label, 1F));
            if (!wait.isEmpty()) {
                // what waits behind this lane's rows: a way to its list, under the lane's name
                String more = "+" + wait.size() + " later";
                float mw = s.width(more, Sheet.LABEL), mx0 = cx - mw / 2F;
                boolean over = Sheet.in(mx, my, mx0 - 8, y + 75, mx0 + mw + 8, y + 99), open = later == g.ordinal();
                if (over || open) s.fill(mx0 - 8, y + 75, mx0 + mw + 8, y + 99, Glass.a(Glass.EDGE, open ? .22F : .12F));
                s.text(more, mx0, y + 80, Sheet.LABEL, Glass.a(over || open ? Glass.TITLE : Glass.EDGE, 1F));
                int lane = g.ordinal();
                hot.add(mx0 - 8, y + 75, mx0 + mw + 8, y + 99, () -> {
                    later = later == lane ? -1 : lane;
                    StatusSections.cue(StatusSections.Cue.PAGE);
                });
            }
            for (int i = 0; i < rows.size(); i += 3) {
                float rowH = 0;
                for (int j = i; j < Math.min(rows.size(), i + 3); j++) rowH = Math.max(rowH, tileHeight(s, rows.get(j)));
                for (int j = i; j < Math.min(rows.size(), i + 3); j++) {
                    StepRow r = rows.get(j);
                    tile(s, hot, v, r, r == f && later < 0, LANES_X0 + LABEL_W + (j - i) * (TILE_W + TILE_GAP), ly, rowH, mx, my);
                }
                ly += rowH + 6;
            }
            // six lanes and the line under them fit the stage when no name runs past three lines; past that the lanes scroll
            y = Math.max(ly, y + 104) + 4;
        }
        String foot = v.footer(f.goal());
        if (foot.isEmpty()) foot = v.footer(Goal.HEARTH.ordinal());
        if (foot.isEmpty()) foot = v.footer(Goal.RANK.ordinal());
        if (!foot.isEmpty()) y = s.linesRight(foot, BandPlaces.X1, y + 8, BandPlaces.X1 - LANES_X0, Sheet.LABEL, Glass.a(s.dim, 1F));
        return y;
    }

    private void tile(Sheet s, Hot hot, TaskPayloads.View v, StepRow r, boolean on, float x, float y, float h, float mx, float my) {
        if (r.fresh()) TaskCache.drawnFresh(r.key());
        boolean over = Sheet.in(mx, my, x, y, x + TILE_W, y + h), top = r.key().equals(v.top());
        Kit.well(s, x, y, x + TILE_W, y + h, on ? Kit.Lit.FOCUS : top ? Kit.Lit.WAITS : Kit.Lit.KNOWN, over);
        List<ItemStack> pics = pictures(r);
        for (int i = 0; i < pics.size(); i++) s.item(pics.get(i), x + 9 + i * 34, y + 6, 32);
        ChoiceRow pick = became(r);
        List<NeedRow> needs = pick != null ? pick.needs() : r.needs();
        if (!needs.isEmpty() && needs.size() <= 6 && pics.size() < 4) Kit.pips(s, x + TILE_W - 8 - needs.size() * 11, y + 8, met(needs), needs.size());
        s.lines(KeyText.resolve(pick != null ? pick.title() : r.title()), x + 9, y + 42, TILE_W - 18, Sheet.LABEL, Glass.a(Glass.TITLE, 1F));
        if (r.fresh()) Kit.glint(s, x + TILE_W - 2, y + 2, Glass.AMBER);
        if (r.key().equals(v.pinned())) s.text("◆", x + TILE_W - 18, y + h - 20, Sheet.LABEL, Glass.a(Glass.AMBER, 1F));
        hot.add(x, y, x + TILE_W, y + h, () -> focus(r.key()));
    }

    /** Tours: brings a step into focus by its key, or by the start of it ("armour/" for the armour step). */
    void debugFocus(String key) {
        for (StepRow r : all(TaskCache.view)) {
            if (!r.key().equals(key) && !r.key().startsWith(key) && !r.title().toLowerCase(java.util.Locale.ROOT).contains(key.toLowerCase(java.util.Locale.ROOT))) continue;
            focus(r.key());
            return;
        }
    }

    /** Tours: the Later list of the first lane that has one. */
    void debugLater() {
        for (Goal g : Goal.values()) {
            if (waiting(TaskCache.view, g).isEmpty()) continue;
            later = g.ordinal();
            return;
        }
    }

    private void focus(String key) {
        if (!key.equals(focus)) StatusSections.cue(StatusSections.Cue.PAGE);
        focus = key;
        choice = "";
        later = -1;
    }

    /** A lane's Later list, in the focus area: read-only, with Track and (for one you set aside) Put back. Returns the y under it. */
    private float laterList(Sheet s, Hot hot, TaskPayloads.View v, float y0, float mx, float my) {
        Goal g = Goal.values()[later];
        List<StepRow> rows = waiting(v, g);
        float x = BandPlaces.X0, y = y0;
        s.caps("Later", x, y + 9, Glass.a(s.label, 1F));
        Kit.chip(s, emblem(g.ordinal()), laneName(g.ordinal()), x + s.width("LATER", Sheet.LABEL) + 14, y, Glass.EDGE);
        y += 50;
        for (StepRow r : rows) {
            boolean tracked = r.key().equals(v.pinned());
            String label = tracked ? "Tracked" : "Track", title = KeyText.resolve(r.title());
            float bw = Kit.buttonWidth(s, "", label), pw = r.aside() ? Kit.buttonWidth(s, "", "Put back") + 10 : 0;
            float tw = HERO_X1 - x - 66 - bw - pw - 28, h = Math.max(70, 14 + s.tall(title, tw, Sheet.BODY) + 4 + s.line(Sheet.LABEL) + 12);
            Kit.well(s, x, y, HERO_X1, y + h, Kit.Lit.KNOWN, false);
            s.item(pictures(r).get(0), x + 12, y + 15, 40);
            float ty = s.lines(title, x + 66, y + 14, tw, Sheet.BODY, Glass.a(Glass.TITLE, 1F)) + 4;
            s.text(r.aside() ? "You set this aside." : r.optional() ? "When you want." : "After the one before it.", x + 66, ty, Sheet.LABEL, Glass.a(s.label, 1F));
            float bx = HERO_X1 - bw - 12, by = y + (h - Kit.BUTTON_H) / 2F;
            Kit.button(s, "", label, bx, by, tracked ? Kit.Look.ON : Kit.Look.PLAIN, Sheet.in(mx, my, bx, by, bx + bw, by + Kit.BUTTON_H), true, 0F);
            hot.add(bx, by, bx + bw, by + Kit.BUTTON_H, () -> track(r));
            if (r.aside()) {
                float px = bx - pw;
                Kit.button(s, "", "Put back", px, by, Kit.Look.PLAIN, Sheet.in(mx, my, px, by, px + pw - 10, by + Kit.BUTTON_H), true, 0F);
                hot.add(px, by, px + pw - 10, by + Kit.BUTTON_H, () -> {
                    TaskCache.act(TaskPayloads.PUT_BACK, r.key());
                    StatusSections.cue(StatusSections.Cue.UNDO);
                });
            }
            y += h + 8;
        }
        return y;
    }

    // ---- keys --------------------------------------------------------------------------------------------------------

    @Override
    public void hints(List<Kit.Hint> out) {
        StepRow f = focused(TaskCache.view);
        if (f == null) return;
        out.add(Kit.Hint.of(f.key().equals(TaskCache.view.pinned()) ? "Stop tracking" : "Track", "T"));
        out.add(Kit.Hint.of("Next step", "Tab"));
        if (!TaskCache.undoTitle().isEmpty()) out.add(Kit.Hint.of("Undo", "Z"));
        if (heroTall > BandPlaces.Y1 - BandPlaces.Y0 - Kit.BUTTON_H - 12 || lanesTall > BandPlaces.Y1 - BandPlaces.Y0) out.add(Kit.Hint.of("More", "Scroll"));
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        if (mx < HERO_X1 + 16) heroScroll -= (float) amount * 40;
        else lanesScroll -= (float) amount * 40;
        return true;
    }

    @Override
    public boolean key(KeyEvent event) {
        TaskPayloads.View v = TaskCache.view;
        StepRow f = focused(v);
        if (f == null) return false;
        int k = event.key();
        if (k == InputConstants.KEY_T) {
            track(f);
            return true;
        }
        if (k == InputConstants.KEY_X && later < 0 && canSetAside(v, f)) {
            setAside(f);
            return true;
        }
        if (k == InputConstants.KEY_Z && !TaskCache.undoTitle().isEmpty()) {
            putBack();
            return true;
        }
        boolean picking = became(f) == null && !f.choices().isEmpty();
        if (picking && (k == InputConstants.KEY_LEFT || k == InputConstants.KEY_RIGHT)) {
            int n = f.choices().size(), at = -1;
            for (int i = 0; i < n; i++) if (f.choices().get(i).id().equals(choice.isEmpty() ? f.chosen() : choice)) at = i;
            at = Math.floorMod(at + (k == InputConstants.KEY_LEFT ? -1 : 1), n);
            choice = f.choices().get(at).id();
            StatusSections.cue(StatusSections.Cue.PAGE);
            return true;
        }
        if (picking && (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) && !choice.isEmpty() && !choice.equals(f.chosen())) {
            for (ChoiceRow c : f.choices()) if (c.id().equals(choice)) TaskPage.choose(f, c);
            return true;
        }
        return false;
    }

    @Override
    public void next(int step) {
        TaskPayloads.View v = TaskCache.view;
        List<StepRow> all = all(v);
        StepRow f = focused(v);
        if (all.isEmpty() || f == null) return;
        int at = Math.max(0, all.indexOf(f));
        focus(all.get(Math.floorMod(at + step, all.size())).key());
    }

    @Override
    public boolean back() {
        TaskPayloads.View v = TaskCache.view;
        if (later >= 0) {
            later = -1;
            return true;
        }
        if (!choice.isEmpty()) {
            choice = "";
            return true;
        }
        StepRow top = TaskCache.step(v.top()), f = focused(v);
        if (top != null && f != null && f != top) {
            focus = "";
            StatusSections.cue(StatusSections.Cue.PAGE);
            return true;
        }
        return false;
    }

    @Override
    public String backWords() {
        TaskPayloads.View v = TaskCache.view;
        StepRow top = TaskCache.step(v.top()), f = focused(v);
        return later >= 0 || !choice.isEmpty() || top != null && f != null && f != top ? "Back" : "Close";
    }
}
