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
    private static final float LANE_H = 118F, TILE_W = 158F, TILE_H = 106F;
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
        if (later >= 0) laterList(s, hot, v, mx, my);
        else hero(s, hot, v, f, mx, my);
        lanes(s, hot, v, f, mx, my);
        undo(s, hot, mx, my);
        String words = KeyText.resolve(f.title());
        if (!words.equals(said)) {
            said = words;
            Sheet.say(words + ". " + line(f));
        }
        if (tipTitle != null) Kit.tip(s, mx, my, tipTitle, tipMore);
    }

    private static String line(StepRow f) {
        ChoiceRow pick = became(f);
        if (pick != null) return pick.detail().isEmpty() ? pick.sub() : pick.detail();
        return f.extra().opens().isEmpty() ? f.why() : f.extra().opens();
    }

    private void hero(Sheet s, Hot hot, TaskPayloads.View v, StepRow f, float mx, float my) {
        float x = BandPlaces.X0, y = BandPlaces.Y0;
        boolean tracked = f.key().equals(v.pinned()), top = f.key().equals(v.top());
        ChoiceRow pick = became(f);
        // what kind of step this is, and whose
        String eyebrow = tracked ? "Tracked" : top ? "Do this next" : f.optional() ? "When you want" : "Also open to you";
        s.caps(eyebrow, x, y + 9, Glass.a(tracked || top ? Glass.AMBER : s.label, 1F));
        float cx = x + s.width(eyebrow.toUpperCase(java.util.Locale.ROOT), Sheet.LABEL) + 14;
        cx += Kit.chip(s, emblem(f.goal()), laneName(f.goal()), cx, y, Glass.EDGE) + 8;
        if (f.extra().ready()) Kit.chip(s, ItemStack.EMPTY, "Ready", cx, y, Glass.GOOD);
        y += 46;
        // its name, and one line of why
        String title = KeyText.resolve(pick != null ? pick.title() : f.title());
        List<FormattedCharSequence> name = s.wrap(title, HERO_X1 - x, 4F);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            s.text(name.get(i), x, y, 4F, Glass.a(Glass.TITLE, 1F));
            y += s.line(4F);
        }
        y += 4;
        String line = line(f);
        if (!line.isEmpty()) {
            List<FormattedCharSequence> why = s.wrap(KeyText.resolve(line), HERO_X1 - x, Sheet.BODY);
            float y0 = y;
            for (int i = 0; i < Math.min(2, why.size()); i++) {
                s.text(why.get(i), x, y, Sheet.BODY, Glass.a(s.label, 1F));
                y += s.line(Sheet.BODY);
            }
            String rest = !f.extra().opens().isEmpty() && pick == null ? f.why() : why.size() > 2 ? line : "";
            if (!rest.isEmpty() && Sheet.in(mx, my, x, y0, HERO_X1, y)) tip(title, KeyText.resolve(rest));
        }
        y += 14;
        // the picture: the picks side by side, what it makes as its recipe, the way as a needle, or the thing itself
        List<NeedRow> needs = new ArrayList<>(pick != null ? pick.needs() : f.needs());
        float bottom = BandPlaces.Y1 - 56;
        if (pick == null && !f.choices().isEmpty()) {
            needs = picks(s, hot, f, x, y, bottom - y - Kit.NEED_H - 10, mx, my);
            y += picksH;
        } else {
            List<NeedRow> made = new ArrayList<>();
            for (NeedRow n : needs) if (!n.met() && TaskCache.view.recipe(n.item()) != null && made.size() < 2) made.add(n);
            if (!made.isEmpty()) {
                needs.removeAll(made);
                recipes(s, made, x, y, mx, my);
                y += 196;
            } else if (needs.stream().noneMatch(n -> !n.item().isEmpty())) {
                boolean way = f.where().isPresent();
                float h = Math.min(250, bottom - y - (needs.isEmpty() ? 60 : 190));
                if (way) way(s, f, x, y, h);
                else thing(s, pictures(f).get(0), (x + HERO_X1) / 2F, y, h);
                y += h + 10;
            }
        }
        // what it takes: the real items in a row, then anything that isn't a thing as a line
        float nx = x;
        int shown = 0;
        for (NeedRow n : needs) {
            if (n.item().isEmpty() || shown >= 6) continue;
            shown++;
            String name1 = needName(n);
            if (Kit.need(s, WhereFrom.icon(n.item()), name1, n.have(), n.need(), nx, y, mx, my)) {
                String where = n.met() ? "You have it." : WhereFrom.line(n.item());
                tip(name1, where);
            }
            nx += Kit.NEED_W + 12;
        }
        if (shown > 0) y += Kit.NEED_H + 8;
        for (NeedRow n : needs) {
            if (!n.item().isEmpty() || y + 22 > bottom) continue;
            Kit.condition(s, n.need() > 1 ? n.label() + "  " + Math.min(n.have(), n.need()) + "/" + n.need() : n.label(), n.met(), x, y);
            y += 28;
        }
        if (!needs.isEmpty()) y += 6;
        // where, and what comes after
        BlockPos at = f.where().orElse(null);
        if (at != null && (pick != null || !f.choices().isEmpty() || !needs.isEmpty() || y < bottom - 40)) {
            float[] w = bearing(at);
            if (y + 44 <= bottom) {
                Kit.needle(s, x + 22, y + 22, 22, w[1]);
                s.text(wayWords(at, w[0]), x + 58, y + 14, Sheet.BODY, Glass.a(Glass.TEXT, 1F));
                y += 52;
            }
        }
        String then = KeyText.resolve(f.extra().then());
        if (!then.isEmpty() && y + 20 <= bottom) s.text(then, x, y, Sheet.BODY, Glass.a(s.label, .85F));
        actions(s, hot, v, f, pick, mx, my);
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

    /** What the step makes, each drawn as its recipe with the thing itself beside it and how many you have. */
    private void recipes(Sheet s, List<NeedRow> made, float x, float y, float mx, float my) {
        float each = (HERO_X1 - x) / made.size();
        for (int i = 0; i < made.size(); i++) {
            NeedRow n = made.get(i);
            RecipeRow r = TaskCache.view.recipe(n.item());
            if (r == null) continue;
            List<ItemStack> cells = new ArrayList<>(r.cells().size());
            long turn = System.currentTimeMillis() / 1000;
            for (List<String> ids : r.cells()) cells.add(ids.isEmpty() ? ItemStack.EMPTY : stack(ids.get((int) (turn % ids.size()))));
            ItemStack out = stack(n.item());
            float w = Kit.recipeWidth(r.width(), 128), rx = x + i * each + Math.max(0, (each - w) / 2F);
            Kit.recipe(s, r.width(), r.height(), cells, out, rx, y, 128);
            String name = needName(n), count = Math.min(n.have(), 9999) + "/" + n.need();
            float tw = s.width(name + " ", Sheet.BODY) + s.width(count, Sheet.BODY), tx = rx + (w - tw) / 2F, ty = y + 150;
            s.text(name, tx, ty, Sheet.BODY, Glass.a(Glass.TITLE, 1F));
            s.text(count, tx + s.width(name + " ", Sheet.BODY), ty, Sheet.BODY, Glass.a(n.met() ? Glass.GOOD : Glass.WARN, 1F));
            if (Sheet.in(mx, my, rx, y, rx + w, y + 170)) {
                String where = WhereFrom.line(n.item());
                tip(name, where);
            }
        }
    }

    /** How tall the picks drew this frame. */
    private float picksH;

    /** The picks side by side: armour as figures wearing it, anything else as the things themselves. Returns the looked-at pick's needs. */
    private List<NeedRow> picks(Sheet s, Hot hot, StepRow f, float x, float y, float room, float mx, float my) {
        List<ChoiceRow> cs = f.choices();
        int n = Math.min(4, cs.size());
        // the one you're looking at: the one you clicked, else the one you chose, else the only one there is
        String looked = !choice.isEmpty() && cs.stream().anyMatch(c -> c.id().equals(choice)) ? choice : !f.chosen().isEmpty() ? f.chosen() : n == 1 ? cs.get(0).id() : "";
        boolean worn = figures(f);
        float ph = Math.max(220, Math.min(worn ? 392 : 292, room)), tall = ph - 92;
        picksH = ph;
        float w = Math.min(252, (HERO_X1 - x) / n), x0 = x + ((HERO_X1 - x) - w * n) / 2F;
        Kit.floor(s, x0 + w * n / 2F, y + tall - 6, Math.min(360, w * n / 2F + 20));
        List<NeedRow> needs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ChoiceRow c = cs.get(i);
            float cx = x0 + i * w;
            boolean on = c.id().equals(looked), mine = c.id().equals(f.chosen()), over = Sheet.in(mx, my, cx, y, cx + w, y + ph - 8);
            if (over || on) s.fill(cx + 6, y, cx + w - 6, y + ph - 8, Glass.a(Glass.EDGE, on ? .10F : .06F));
            if (worn && i < figures.size()) {
                InventoryScreen.extractEntityInInventoryFollowsMouse(s.g, s.gx(cx), s.gy(y + 4), s.gx(cx + w), s.gy(y + tall),
                        Math.round(tall * .41F * s.k), .0625F, s.ox + mx * s.k, s.oy + my * s.k, figures.get(i));
            } else {
                int k = Math.min(4, c.show().size());
                float size = k <= 1 ? 128 : 80, gx = cx + (w - (k <= 1 ? size : 2 * size)) / 2F;
                for (int j = 0; j < k; j++) s.item(WhereFrom.icon(c.show().get(j)), gx + (k <= 1 ? 0 : (j % 2) * size), y + 14 + (k <= 1 ? 20 : (j / 2) * size), size);
            }
            float ty = y + tall + 12;
            List<FormattedCharSequence> title = s.wrap(c.title(), w - 16, Sheet.BODY);
            for (int j = 0; j < Math.min(2, title.size()); j++) {
                s.text(title.get(j), cx + (w - s.font.width(title.get(j)) * s.snap(Sheet.BODY)) / 2F, ty, Sheet.BODY, Glass.a(mine ? Glass.GOOD : Glass.TITLE, 1F));
                ty += s.line(Sheet.BODY);
            }
            List<FormattedCharSequence> sub = s.wrap(c.sub(), w - 16, Sheet.LABEL);
            for (int j = 0; j < Math.min(2, sub.size()); j++) {
                s.text(sub.get(j), cx + (w - s.font.width(sub.get(j)) * s.snap(Sheet.LABEL)) / 2F, ty + 2, Sheet.LABEL, Glass.a(s.label, 1F));
                ty += s.line(Sheet.LABEL);
            }
            if (on) s.fill(cx + 20, y + ph - 12, cx + w - 20, y + ph - 9, Glass.a(Glass.AMBER, 1F));
            if (on) needs = new ArrayList<>(c.needs());
            hot.add(cx, y, cx + w, y + ph - 8, () -> {
                choice = c.id();
                StatusSections.cue(StatusSections.Cue.PAGE);
                Sheet.say(c.title() + ". " + c.sub());
            });
        }
        return needs;
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
        String words = s.fit("Set aside: " + title, 520, Sheet.BODY);
        float bw = Kit.buttonWidth(s, "Z", "Undo"), w = s.width(words, Sheet.BODY) + bw + 48;
        float x = BandPlaces.X0 + (756 - w) / 2F, y = BandPlaces.Y1 - Kit.BUTTON_H - 64;
        s.g.nextStratum();
        s.fill(x, y, x + w, y + 56, Sheet.argb(0x0F1724, .96F));
        s.outline(x, y, x + w, y + 56, 1, Glass.a(Glass.EDGE, .55F));
        s.text(words, x + 16, y + 21, Sheet.BODY, Glass.a(Glass.TEXT, 1F));
        float bx = x + w - bw - 8;
        Kit.button(s, "Z", "Undo", bx, y + 6, Kit.Look.PLAIN, Sheet.in(mx, my, bx, y + 6, bx + bw, y + 50), true, 0F);
        hot.add(bx, y + 6, bx + bw, y + 50, TasksPlace::putBack);
    }

    private static void putBack() {
        TaskCache.undo();
        StatusSections.cue(StatusSections.Cue.UNDO);
    }

    // ---- the lanes ---------------------------------------------------------------------------------------------------

    private void lanes(Sheet s, Hot hot, TaskPayloads.View v, StepRow f, float mx, float my) {
        float y = BandPlaces.Y0;
        Goal[] goals = Goal.values();
        for (Goal g : goals) {
            List<StepRow> rows = lane(v, g), wait = waiting(v, g);
            if (rows.isEmpty() && wait.isEmpty()) continue;
            if (y > BandPlaces.Y0) s.fill(LANES_X0, y, BandPlaces.X1, y + 1, Glass.a(Glass.EDGE, .14F));
            float cx = LANES_X0 + 48;
            s.item(emblem(g.ordinal()), cx - 20, y + 22, 40);
            String name = laneName(g.ordinal()).toUpperCase(java.util.Locale.ROOT);
            s.text(name, cx - s.width(name, Sheet.LABEL) / 2F, y + 72, Sheet.LABEL, Glass.a(s.label, 1F));
            float x = LANES_X0 + 108, ty = y + 8;
            int room = wait.isEmpty() ? 3 : 3;
            for (int i = 0; i < Math.min(room, rows.size()); i++) {
                StepRow r = rows.get(i);
                tile(s, hot, v, r, r == f && later < 0, x, ty, mx, my);
                x += TILE_W + 12;
            }
            if (!wait.isEmpty()) {
                boolean over = Sheet.in(mx, my, x, ty, x + 54, ty + TILE_H), open = later == g.ordinal();
                Kit.well(s, x, ty, x + 54, ty + TILE_H, open ? Kit.Lit.FOCUS : Kit.Lit.KNOWN, over);
                s.centre("+" + wait.size(), x + 27, ty + 40, Sheet.BODY, Glass.a(over || open ? Glass.TITLE : s.label, 1F));
                int lane = g.ordinal();
                hot.add(x, ty, x + 54, ty + TILE_H, () -> {
                    later = later == lane ? -1 : lane;
                    StatusSections.cue(StatusSections.Cue.PAGE);
                });
                if (over) tip(wait.size() + " more, later", "They come up as the ones before them are done. Click to see them.");
            }
            y += LANE_H;
        }
        String foot = v.footer(f.goal());
        if (foot.isEmpty()) foot = v.footer(Goal.HEARTH.ordinal());
        if (foot.isEmpty()) foot = v.footer(Goal.RANK.ordinal());
        if (!foot.isEmpty() && y + 24 <= BandPlaces.Y1) {
            String fit = s.fit(foot, BandPlaces.X1 - LANES_X0, Sheet.LABEL);
            s.text(fit, BandPlaces.X1 - s.width(fit, Sheet.LABEL), BandPlaces.Y1 - 14, Sheet.LABEL, Glass.a(s.dim, 1F));
        }
    }

    private void tile(Sheet s, Hot hot, TaskPayloads.View v, StepRow r, boolean on, float x, float y, float mx, float my) {
        if (r.fresh()) TaskCache.drawnFresh(r.key());
        boolean over = Sheet.in(mx, my, x, y, x + TILE_W, y + TILE_H), top = r.key().equals(v.top());
        Kit.well(s, x, y, x + TILE_W, y + TILE_H, on ? Kit.Lit.FOCUS : top ? Kit.Lit.WAITS : Kit.Lit.KNOWN, over);
        List<ItemStack> pics = pictures(r);
        for (int i = 0; i < pics.size(); i++) s.item(pics.get(i), x + 9 + i * 34, y + 8, 32);
        ChoiceRow pick = became(r);
        List<NeedRow> needs = pick != null ? pick.needs() : r.needs();
        if (!needs.isEmpty() && needs.size() <= 6) Kit.pips(s, x + TILE_W - 8 - needs.size() * 11, y + 10, met(needs), needs.size());
        String title = KeyText.resolve(pick != null ? pick.title() : r.title());
        List<FormattedCharSequence> lines = s.wrap(title, TILE_W - 18, Sheet.LABEL);
        float ty = y + 44;
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            s.text(lines.get(i), x + 9, ty, Sheet.LABEL, Glass.a(Glass.TITLE, 1F));
            ty += s.line(Sheet.LABEL);
        }
        if (lines.size() > 4 && over) tip(title, "");
        if (r.fresh()) Kit.glint(s, x + TILE_W - 2, y + 2, Glass.AMBER);
        if (r.key().equals(v.pinned())) s.text("◆", x + TILE_W - 18, y + TILE_H - 20, Sheet.LABEL, Glass.a(Glass.AMBER, 1F));
        hot.add(x, y, x + TILE_W, y + TILE_H, () -> focus(r.key()));
    }

    /** Tours: brings a step into focus by its key, or by the start of it ("armour/" for the armour step). */
    void debugFocus(String key) {
        for (StepRow r : all(TaskCache.view)) {
            if (!r.key().equals(key) && !r.key().startsWith(key) && !r.title().toLowerCase(java.util.Locale.ROOT).contains(key.toLowerCase(java.util.Locale.ROOT))) continue;
            focus(r.key());
            return;
        }
    }

    private void focus(String key) {
        if (!key.equals(focus)) StatusSections.cue(StatusSections.Cue.PAGE);
        focus = key;
        choice = "";
        later = -1;
    }

    /** A lane's Later list, in the focus area: read-only, with Track and (for one you set aside) Put back. */
    private void laterList(Sheet s, Hot hot, TaskPayloads.View v, float mx, float my) {
        Goal g = Goal.values()[later];
        List<StepRow> rows = waiting(v, g);
        float x = BandPlaces.X0, y = BandPlaces.Y0;
        s.caps("Later", x, y + 9, Glass.a(s.label, 1F));
        Kit.chip(s, emblem(g.ordinal()), laneName(g.ordinal()), x + s.width("LATER", Sheet.LABEL) + 14, y, Glass.EDGE);
        y += 50;
        for (int i = 0; i < Math.min(8, rows.size()); i++) {
            StepRow r = rows.get(i);
            Kit.well(s, x, y, HERO_X1, y + 70, Kit.Lit.KNOWN, false);
            s.item(pictures(r).get(0), x + 12, y + 15, 40);
            s.text(s.fit(KeyText.resolve(r.title()), 420, Sheet.BODY), x + 66, y + 14, Sheet.BODY, Glass.a(Glass.TITLE, 1F));
            String why = r.aside() ? "You set this aside." : r.optional() ? "When you want." : "After the one before it.";
            s.text(why, x + 66, y + 40, Sheet.LABEL, Glass.a(s.label, 1F));
            boolean tracked = r.key().equals(v.pinned());
            String label = tracked ? "Tracked" : "Track";
            float w = Kit.buttonWidth(s, "", label), bx = HERO_X1 - w - 12;
            Kit.button(s, "", label, bx, y + 13, tracked ? Kit.Look.ON : Kit.Look.PLAIN, Sheet.in(mx, my, bx, y + 13, bx + w, y + 57), true, 0F);
            hot.add(bx, y + 13, bx + w, y + 57, () -> track(r));
            if (r.aside()) {
                float pw = Kit.buttonWidth(s, "", "Put back"), px = bx - pw - 10;
                Kit.button(s, "", "Put back", px, y + 13, Kit.Look.PLAIN, Sheet.in(mx, my, px, y + 13, px + pw, y + 57), true, 0F);
                hot.add(px, y + 13, px + pw, y + 57, () -> {
                    TaskCache.act(TaskPayloads.PUT_BACK, r.key());
                    StatusSections.cue(StatusSections.Cue.UNDO);
                });
            }
            y += 78;
        }
    }

    // ---- keys --------------------------------------------------------------------------------------------------------

    @Override
    public void hints(List<Kit.Hint> out) {
        StepRow f = focused(TaskCache.view);
        if (f == null) return;
        out.add(Kit.Hint.of(f.key().equals(TaskCache.view.pinned()) ? "Stop tracking" : "Track", "T"));
        out.add(Kit.Hint.of("Next step", "Tab"));
        if (!TaskCache.undoTitle().isEmpty()) out.add(Kit.Hint.of("Undo", "Z"));
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
            int n = Math.min(4, f.choices().size()), at = -1;
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
