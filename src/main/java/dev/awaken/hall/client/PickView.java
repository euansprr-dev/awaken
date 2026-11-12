package dev.awaken.hall.client;

import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.arms.anvil.PickAnvil;
import dev.awaken.arms.anvil.WarAnvil;
import dev.awaken.arms.anvil.client.AnvilClient;
import dev.awaken.arms.client.ArmouryCache;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.delve.forged.PickForm;
import dev.awaken.delve.forged.PickStats;
import dev.awaken.hall.HallNet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One forged pick, close (SPEC §49, §52): the wall falls away and the pick hangs turning, the six ranks of its form on the
 * left, and on the right what it digs, what sets the form apart, what it takes, and what you can do here. At a War Anvil the
 * action is the anvil's own: forge it, or, when it's the niche the pick in your hand would become, raise it, re-draw it,
 * dress it, work in what you've done, or remake an old pick. Every word on whether you can is the server's.
 */
final class PickView extends CloseView {
    private static final int KEY_HERO = 16, KEY_LADDER = 17;
    private static final float CX = 580F, CY = 486F;
    private static final int LADDER_X = 26, LADDER_TOP = 120, LADDER_BOTTOM = 848, RUNG = (LADDER_BOTTOM - LADDER_TOP) / 6, PORTRAIT = 88;
    private static final float PANEL_X0 = Panel.X0, PANEL_Y0 = Panel.Y0, PANEL_Y1 = Panel.Y1, X = Panel.X, RIGHT = Panel.RIGHT, TEXT_W = Panel.TEXT_W;
    /** Where an action comes from: the anvil's rows for the pick in your hand, or the Armoury's ops for a new one. */
    private static final int FROM_HELD = 0, FROM_ARMOURY = 1;

    int col, tier;
    String style = "pick_delver", id = "wildhide_pick_delver";
    private final float fromX, fromY, fromS;
    private String shownId;
    private float morph = 1F, bob;
    private long heroVersion, ladderVersion, ladderBuilt = -1;
    private Placed[] ladder = new Placed[0];
    private HallNet.@Nullable Wall ladderWall;
    private final Component[] letter = {Stage.blackletter("E"), Stage.blackletter("D"), Stage.blackletter("C"), Stage.blackletter("B"), Stage.blackletter("A"), Stage.blackletter("S")};

    // the panel: rebuilt only when what it reads changes
    private boolean dirty = true;
    private @Nullable Object panelDetail, panelView, panelWall, panelHeld;
    private String eyebrow = "";
    private Component name = Component.empty();
    private float nameSize = 4F;
    private int mode = Placed.FULL;
    private List<FormattedCharSequence> blurb = List.of(), why = List.of();
    private final List<HallSpecial.Mark> marks = new ArrayList<>(4);
    private HallSpecial.@Nullable Mark overMark;
    private final List<String[]> stats = new ArrayList<>(4);
    private String compare = "";
    private int compareColour = Stage.LABEL, whyColour = Stage.LABEL;
    private String needsHeading = "What it takes";
    private final List<Panel.Need> needs = new ArrayList<>(6);
    private int hoverNeed = -1;
    /** An action: its words, where it goes, its code there, whether it's held, and whether it can begin. */
    private record Act(String label, int from, int code, boolean hold, boolean enabled, int key) {}
    private @Nullable Act primary;
    private final List<Act> subs = new ArrayList<>(3);
    private String prevName = "", nextName = "";

    // what the last frame drew where
    private float primaryY0, primaryY1, subY0, subY1;
    private final float[] subX0 = new float[3], subX1 = new float[3];
    private final Panel.Hold hold = new Panel.Hold();
    private float pendingFor;

    PickView(int col, int tier, HallLayout.Cell from) {
        this.fromX = from.x();
        this.fromY = from.cy();
        this.col = -1;
        select(col, tier);
        this.shownId = id;
        Meshes.Up up = Meshes.up(id);
        this.fromS = up == null ? 1F : PickLayout.portrait(up, from);
    }

    void select(int newCol, int newTier) {
        int c = Math.clamp(newCol, 0, PickLayout.COLS - 1), t = Math.clamp(newTier, 0, HallNet.ROWS - 1);
        if (col >= 0 && t != tier) HallSounds.rank();
        col = c;
        tier = t;
        style = HallPicks.style(col);
        id = HallPicks.id(col, tier);
        dirty = true;
        ladderVersion++;
        hold.amount = 0F;
        pendingFor = 0F;
        if (morph >= 1F) morph = 0F;
        ArmouryCache.ask(ArmsArmoury.DETAIL, style, ArmsArmoury.TIERS[tier]);
        Stage.say(HallPicks.name(col, tier));
    }

    @Override void step(int dc, int dt) {
        int c = Math.clamp(col + dc, 0, PickLayout.COLS - 1), t = Math.clamp(tier + dt, 0, HallNet.ROWS - 1);
        if (c != col || t != tier) select(c, t);
    }

    @Override float zoomX() {
        return fromX;
    }

    @Override float zoomY() {
        return fromY;
    }

    @Override int tier() {
        return tier;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    @Override void draw(Stage s, HallNet.Wall w, float dt, float time, double mouseX, double mouseY) {
        rebuild(s, w);
        float e = eased();
        float mx = s.sx(mouseX), my = s.sy(mouseY);
        int glow = Stage.RANK[tier];
        s.glow(CX, CY, 720F, 620F, glow, .30F * e);
        s.glow(CX, CY, 260F, 520F, glow, .24F * e);
        hero(s, dt, time);
        if (fly <= .15F) return;
        float slide = (1F - e) * (1F - e);
        s.g.nextStratum();
        drawLadder(s, w, -180F * slide);
        drawPanel(s, 720F * slide, mx, my, dt, time);
        drawFoot(s, slide);
    }

    private void hero(Stage s, float dt, float time) {
        spin(dt);
        if (morph < 1F) {
            morph = Math.min(1F, morph + dt / .3F);
            if (morph >= .5F) shownId = id;
        } else {
            shownId = id;
        }
        bob = (float) Math.sin(time * .9F) * 6F;
        Meshes.Up up = Meshes.up(shownId);
        if (up == null) return;
        float e = eased();
        // as big as the weapons come, the head kept clear of the panel however it turns
        float aim = Math.min(760F / up.length(), 700F / Math.max(.01F, up.width()));
        float size = fromS + (aim - fromS) * e;
        float alpha = morph >= 1F ? 1F : morph < .5F ? 1F - morph * 2F : morph * 2F - 1F;
        float shownYaw = -24F + (yaw + 24F) * (closing ? e : Math.min(1F, fly * 3F));
        Placed p = new Placed(shownId, fromX + (CX - fromX) * e, fromY + (CY + bob - fromY) * e, size, shownYaw, 6F + e, 0F, mode, alpha);
        heroVersion++;
        s.g.submitPictureInPictureRenderState(new PicState(KEY_HERO, 0, 0, Stage.W, Stage.H, s.rps, heroVersion, new Placed[] {p},
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, s.g.pose()));
    }

    private static float rungY(int t) {
        return LADDER_BOTTOM - RUNG * (t + .5F);
    }

    private void drawLadder(Stage s, HallNet.Wall w, float dx) {
        var g = s.g;
        g.pose().pushMatrix().translate(dx, 0F);
        if (ladder.length == 0 || ladderBuilt != ladderVersion || ladderWall != w) buildLadder(w);
        g.submitPictureInPictureRenderState(new PicState(KEY_LADDER, 0, LADDER_TOP, LADDER_X + PORTRAIT + 8, LADDER_BOTTOM, s.rps, ladderVersion + w.hashCode(), ladder,
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, g.pose()));
        g.nextStratum();
        for (int t = 0; t < HallNet.ROWS; t++) {
            float y = rungY(t);
            int colour = 0xFF000000 | Stage.RANK[t];
            s.title(letter[t], LADDER_X + PORTRAIT + 6F, y - 26F, 2F, t == tier ? colour : Stage.argb(Stage.RANK[t], .55F));
            if (t == tier) s.fill(LADDER_X - 14F, y - 40F, LADDER_X - 10F, y + 40F, colour);
        }
        g.pose().popMatrix();
    }

    private void buildLadder(HallNet.Wall w) {
        ladderBuilt = ladderVersion;
        ladderWall = w;
        int you = WallView.youTier(w);
        List<Placed> out = new ArrayList<>(6);
        for (int t = 0; t < HallNet.ROWS; t++) {
            String rid = HallPicks.id(col, t);
            Meshes.Up up = Meshes.up(rid);
            if (up == null) continue;
            float scale = Math.min(PORTRAIT * .92F / up.length(), PORTRAIT * .92F / Math.max(.01F, up.width()));
            float alpha = t == tier ? 1F : t <= you ? .8F : .5F;
            out.add(new Placed(rid, LADDER_X + PORTRAIT / 2F, rungY(t), scale, -24F, 6F, 0F, PickWallView.mode(w.pick(col, t)), alpha));
        }
        ladder = out.toArray(new Placed[0]);
    }

    private void rebuild(Stage s, HallNet.Wall w) {
        var d = ArmouryCache.detail;
        var v = ArmouryCache.view;
        var held = HallClient.held;
        if (!dirty && d == panelDetail && v == panelView && w == panelWall && held == panelHeld) return;
        if (panelDetail != d || panelView != v || panelWall != w || panelHeld != held) pendingFor = 0F;
        dirty = false;
        panelDetail = d;
        panelView = v;
        panelWall = w;
        panelHeld = held;
        ArmsPayloads.ArmouryDetail here = d != null && d.style().equals(style) && d.tier().equals(ArmsArmoury.TIERS[tier]) ? d : null;
        int state = w.pick(col, tier);
        PickForm form = HallPicks.form(col);
        mode = PickWallView.mode(state);
        name = Stage.blackletter(form.label);
        nameSize = 5F;
        while (nameSize > 2F && s.titleWidth(name, nameSize) > TEXT_W) nameSize -= 1F;
        String prefix = ArmsArmoury.PREFIX[tier];
        eyebrow = (state == HallNet.SHAPE ? "Not learned yet" : prefix.substring(0, 1).toUpperCase(Locale.ROOT) + prefix.substring(1)) + " · " + ArmsArmoury.TIERS[tier] + " rank";
        blurb = s.wrap(Language.getInstance().getOrDefault("awaken.pick." + form.id + ".blurb"), TEXT_W, 3F);

        // what it digs: the rank's numbers through the form's, as the item's own rules have them (§49.3)
        stats.clear();
        stats.add(new String[] {HallSpecial.n(PickStats.speed(form, tier)), "Speed in rock"});
        if (form == PickForm.MATTOCK) stats.add(new String[] {HallSpecial.n(PickStats.softSpeed(form, tier)), "Earth and wood"});
        stats.add(new String[] {String.format(Locale.ROOT, "%,d", PickStats.baseUses(form, tier)), "Uses"});
        stats.add(new String[] {HallSpecial.n(PickStats.damage(form, tier) + 1F), "Damage"});
        compare(PickStats.speed(form, tier));
        marks.clear();
        marks.addAll(HallPicks.marks(form));

        // what it takes: the anvil's own row for the pick in your hand when there is one here, else a new one's parts
        List<AnvilPayloads.Option> rows = HallPicks.rows(col, tier);
        needs.clear();
        needsHeading = "What it takes";
        if (!rows.isEmpty()) {
            List<AnvilPayloads.Cost> costs = rows.getFirst().costs();
            for (AnvilPayloads.Cost c : costs) needs.add(Panel.need(s, c.tag(), c.id(), c.label(), c.have(), c.need(), Panel.across(costs.size())));
            if (costs.isEmpty()) needsHeading = "";
        } else if (here != null) {
            for (ArmsPayloads.NeedRow n : here.needs()) needs.add(Panel.need(s, n.kind().equals("tag"), n.id(), "", n.have(), n.need(), Panel.across(here.needs().size())));
        }
        actions(s, w, state, rows, here);
        prevName = col > 0 ? HallPicks.label(col - 1) : "";
        nextName = col < PickLayout.COLS - 1 ? HallPicks.label(col + 1) : "";
    }

    /** Against the pickaxe in your hand, by how fast each digs stone. */
    private void compare(float speed) {
        compare = "";
        ItemStack held = HallPicks.hand();
        if (held.isEmpty() || !PickAnvil.isPick(held)) return;
        if (held.getItem() == dev.awaken.delve.forged.ForgedPicks.get(HallPicks.form(col), tier)) {
            compare = "This is the one in your hand.";
            compareColour = Stage.LABEL;
            return;
        }
        float delta = speed - held.getDestroySpeed(Blocks.STONE.defaultBlockState());
        String on = held.getHoverName().getString();
        if (Math.abs(delta) < .05F) {
            compare = "The same speed in rock as your " + on + ".";
            compareColour = Stage.LABEL;
        } else {
            compare = (delta > 0F ? "+" : "") + HallSpecial.n(delta) + " speed on your " + on;
            compareColour = delta > 0F ? Stage.GOOD : Stage.BAD;
        }
    }

    private void actions(Stage s, HallNet.Wall w, int state, List<AnvilPayloads.Option> rows, ArmsPayloads.@Nullable ArmouryDetail here) {
        primary = null;
        subs.clear();
        String line = "";
        whyColour = Stage.LABEL;
        PickForm form = HallPicks.form(col);
        var v = ArmouryCache.view;
        boolean atAnvil = v != null && v.door() == ArmsArmoury.ANVIL;
        int a = here == null ? 0 : here.anvil();
        boolean anvil = (a & WarAnvil.Status.SHOWN) != 0;
        if (!rows.isEmpty()) {
            // the pick in your hand would become this one: the anvil's own row, its words and its readiness the server's
            AnvilPayloads.Option first = rows.getFirst();
            primary = new Act(lowerFirst(first.button()), FROM_HELD, first.kind(), true, first.ready(), 0);
            line = first.line();
            if (first.ready()) whyColour = Stage.LABEL;
            for (int i = 1; i < rows.size(); i++) subs.add(new Act(rows.get(i).button(), FROM_HELD, rows.get(i).kind(), false, rows.get(i).ready(), 0));
            if (anvil && (a & WarAnvil.Status.FORGE) != 0 && (a & WarAnvil.Status.REBUILD) == 0) subs.add(new Act("Forge a new one", FROM_ARMOURY, ArmsArmoury.FORGE_IT, false, true, 0));
        } else if (anvil) {
            if ((a & WarAnvil.Status.REBUILD) != 0) {
                primary = new Act("rebuild it", FROM_ARMOURY, ArmsArmoury.REBUILD, true, (a & WarAnvil.Status.REBUILD_OK) != 0, 0);
            } else if ((a & WarAnvil.Status.AGAIN) != 0) {
                // you've made this rank before and carry none now: half the parts (§49.4)
                primary = new Act("make it again", FROM_ARMOURY, ArmsArmoury.AGAIN, true, true, 0);
                line = "You've forged a pick of this rank before. Making it again takes half the parts.";
                if ((a & WarAnvil.Status.FORGE) != 0) subs.add(new Act("Forge it new", FROM_ARMOURY, ArmsArmoury.FORGE_IT, false, true, 0));
            } else {
                primary = new Act("forge it", FROM_ARMOURY, ArmsArmoury.FORGE_IT, true, (a & WarAnvil.Status.FORGE) != 0, 0);
            }
            if ((a & WarAnvil.Status.MEMORY) != 0 && (a & WarAnvil.Status.REBUILD) == 0)
                subs.add(new Act("From memory", FROM_ARMOURY, ArmsArmoury.FROM_MEMORY, false, (a & WarAnvil.Status.MEMORY_OK) != 0, com.mojang.blaze3d.platform.InputConstants.KEY_M));
            if (line.isEmpty()) line = here.block();
            if (line.isEmpty() && state == HallNet.MADE) line = PickWallView.carries(col, tier) ? "You carry one. You can still forge another." : "";
        } else if (state == HallNet.SHAPE) {
            line = PickAnvil.howOpens(form);
        } else if (state == HallNet.OVER) {
            line = ArmsArmoury.PREFIX[tier].substring(0, 1).toUpperCase(Locale.ROOT) + ArmsArmoury.PREFIX[tier].substring(1) + " picks open at level "
                    + ArmsArmoury.LEVEL[tier] + ". You are level " + w.level() + ".";
        } else if (state == HallNet.MADE && PickWallView.carries(col, tier)) {
            line = "You carry one. Hold it at a War Anvil to raise it, re-draw it or dress it.";
            whyColour = Stage.GOOD;
        } else {
            line = atAnvil ? "" : "Picks are made at a War Anvil. Take this there to make it.";
        }
        if (state != HallNet.SHAPE) {
            boolean tracked = PickWallView.isTracked(col, tier);
            subs.add(0, new Act(tracked ? "Stop tracking" : "Track it", FROM_ARMOURY, tracked ? ArmsArmoury.UNTRACK : ArmsArmoury.TRACK, false, true, dev.awaken.holo.ui.StageKeys.TRACK));
        }
        while (subs.size() > subX0.length) subs.removeLast();
        why = line.isEmpty() ? List.of() : s.wrap(line, TEXT_W, 3F);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    private void drawPanel(Stage s, float dx, float mx, float my, float dt, float time) {
        var g = s.g;
        g.pose().pushMatrix().translate(dx, 0F);
        Panel.frame(s);
        g.nextStratum();
        int colour = 0xFF000000 | Stage.RANK[tier];
        float y = PANEL_Y0 + 22F;
        s.text(eyebrow, X, y, 3F, colour);
        y += 36F;
        float wide = s.titleWidth(name, nameSize);
        s.glow(X + wide / 2F, y + 12F * nameSize, wide / 2F + 40F, 22F * nameSize, Stage.RANK[tier], .22F);
        s.title(name, X, y, nameSize, Stage.TITLE);
        y += 26F * nameSize + 4F;
        y = Panel.lines(s, blurb, y, 2, Stage.LABEL) + 16F;

        float x = X;
        for (String[] st : stats) {
            float column = Math.max(s.width(st[0], 5F), s.width(st[1], 2F));
            s.text(st[0], x, y, 5F, Stage.TITLE);
            s.text(st[1], x, y + 44F, 2F, Stage.EDGE);
            x += column + 30F;
        }
        y += 72F;
        if (!compare.isEmpty()) y += Math.max(32F, s.fitted(compare, X, y, TEXT_W, 3F, compareColour, -1) + 2F);
        y += 8F;

        // the action zone stands at the bottom; everything else fits above it
        float bottom = PANEL_Y1 - 22F, subTop = bottom - 36F;
        boolean hasSubs = !subs.isEmpty();
        float primaryTop = (hasSubs ? subTop - 12F : bottom) - 60F;
        float actionTop = primary != null ? primaryTop : hasSubs ? subTop : bottom;
        float whyTop = actionTop - 12F - why.size() * 30F;
        float needTop = whyTop - 8F - (needs.isEmpty() ? 0F : Panel.needsHeight(needs.size()));

        // what sets it apart: as many marks as fit above what it takes, never one cut in half
        overMark = null;
        s.text("What makes it different", X, y, 3F, Stage.EDGE);
        y += 32F;
        for (HallSpecial.Mark m : marks) {
            if (y + 30F > needTop) break;
            boolean on = fly >= 1F && mx >= X - 6F && mx < RIGHT + 6F && my >= y - 3F && my < y + 27F;
            if (on) {
                overMark = m;
                s.fill(X - 6F, y - 3F, RIGHT + 6F, y + 27F, 0x332A3A56);
            }
            float vw = m.value().isEmpty() ? 0F : s.width(m.value(), 3F) + 16F;
            s.fitted(m.label(), X, y, TEXT_W - vw, 3F, on ? Stage.TITLE : Stage.LABEL, -1);
            if (!m.value().isEmpty()) s.right(m.value(), RIGHT, y, 3F, Stage.TITLE);
            y += 30F;
        }
        y += 8F;

        // a full panel keeps what it takes and leaves the hint under it to a tip
        boolean roomy = y + Panel.needsHeight(needs.size()) <= whyTop - 8F;
        hoverNeed = needs.isEmpty() ? -1 : Panel.needs(s, needsHeading, needs, Math.max(y, needTop), mx, my, fly >= 1F, time, roomy);
        Panel.lines(s, why, whyTop, why.size(), whyColour);
        primaryY0 = primaryTop;
        primaryY1 = primaryTop + 60F;
        if (primary != null) primaryButton(s, mx, my, dt);
        else hold.amount = 0F;
        subY0 = subTop;
        subY1 = bottom;
        float sx = X;
        for (int i = 0; i < subs.size(); i++) {
            Act a = subs.get(i);
            String cap = a.key() == dev.awaken.holo.ui.StageKeys.TRACK ? "T" : a.key() == com.mojang.blaze3d.platform.InputConstants.KEY_M ? "M" : "";
            float capW = cap.isEmpty() ? 0F : 38F;
            float w = Math.min(RIGHT - sx, s.width(a.label(), 3F) + 24F + capW);
            subX0[i] = sx;
            subX1[i] = sx + w;
            boolean over = fly >= 1F && a.enabled() && mx >= sx && mx < sx + w && my >= subY0 && my < subY1;
            if (over) s.fill(sx, subY0, sx + w, subY1, 0x552A3A56);
            s.outline(sx, subY0, sx + w, subY1, 2, a.enabled() ? 0xA07FC3FF : 0x602A3446);
            if (!cap.isEmpty()) s.key(cap, sx + 10F, subY0 + 4F);
            s.fitted(a.label(), sx + 12F + capW, subY0 + 7F, w - 24F - capW, 3F, a.enabled() ? Stage.TITLE : Stage.DIM, -1);
            sx += w + 14F;
        }
        pendingFor = Math.max(0F, pendingFor - dt);
        if (!roomy && hoverNeed >= 0) Panel.sourceTip(s, needs.get(hoverNeed), Math.max(y, needTop) + 34F + Panel.NEED_H);
        if (overMark != null) tip(s, overMark.says(), mx, my);
        g.pose().popMatrix();
    }

    /** The whole sentence of a mark, by the pointer. */
    private static void tip(Stage s, String says, float mx, float my) {
        List<FormattedCharSequence> tip = s.wrap(says, 520F, 2F);
        float tw = 0F;
        for (FormattedCharSequence l : tip) tw = Math.max(tw, s.font.width(l) * s.snap(2F));
        float th = tip.size() * 21F + 18F, tx = Math.min(mx + 18F, Stage.W - tw - 34F), ty = Math.min(my + 20F, Stage.H - th - 8F);
        s.g.nextStratum();
        s.fill(tx, ty, tx + tw + 24F, ty + th, 0xF50F1724);
        s.outline(tx, ty, tx + tw + 24F, ty + th, 1, 0x997FC3FF);
        for (int i = 0; i < tip.size(); i++) s.text(tip.get(i), tx + 12F, ty + 10F + i * 21F, 2F, Stage.TITLE);
    }

    private void primaryButton(Stage s, float mx, float my, float dt) {
        Act a = primary;
        boolean pending = pendingFor > 0F;
        boolean can = a.enabled() && !pending && fly >= 1F;
        boolean over = can && mx >= X && mx < RIGHT && my >= primaryY0 && my < primaryY1;
        String label = Panel.holdLabel(a.label());
        if (hold.draw(s, primaryY0, primaryY1, pending ? "..." : label, a.enabled(), can, holdKey || holdMouse, over, dt)) {
            holdKey = holdMouse = false;
            fire(a);
        }
    }

    private void drawFoot(Stage s, float slide) {
        float y = 916F + 60F * slide;
        s.hint("Esc", "Back", 40F, y);
        if (!prevName.isEmpty()) s.text("< " + prevName, 250F, y, 3F, Stage.LABEL);
        s.hint("Drag", "Turn it", 560F, y);
        if (!nextName.isEmpty()) s.right(nextName + " >", 1000F, y, 3F, Stage.LABEL);
    }

    // ---- input --------------------------------------------------------------------------------------------------------

    @Override boolean click(float x, float y) {
        if (fly < 1F) return false;
        if (x >= 30F && x < 220F && y >= 906F && y < 946F) {
            closing = true;
            return true;
        }
        if (y >= 906F && y < 946F) {
            if (x >= 250F && x < 530F) {
                step(-1, 0);
                return true;
            }
            if (x >= 760F && x < 1004F) {
                step(1, 0);
                return true;
            }
        }
        if (x >= 0F && x < LADDER_X + PORTRAIT + 60F && y >= LADDER_TOP && y < LADDER_BOTTOM) {
            int t = Math.clamp((int) ((LADDER_BOTTOM - y) / RUNG), 0, HallNet.ROWS - 1);
            if (t != tier) select(col, t);
            return true;
        }
        if (primary != null && primary.enabled() && pendingFor <= 0F && x >= X && x < RIGHT && y >= primaryY0 && y < primaryY1) {
            if (HallConfig.clickToCommit()) fire(primary);
            else holdMouse = true;
            return true;
        }
        if (y >= subY0 && y < subY1) {
            for (int i = 0; i < subs.size(); i++) {
                if (x >= subX0[i] && x < subX1[i]) {
                    if (subs.get(i).enabled()) fire(subs.get(i));
                    return true;
                }
            }
        }
        return x >= PANEL_X0;
    }

    @Override boolean key(int key) {
        for (Act a : subs) {
            if (a.key() == key && key != 0) {
                if (a.enabled()) fire(a);
                return true;
            }
        }
        return false;
    }

    @Override void space(boolean down) {
        if (down && HallConfig.clickToCommit()) {
            if (primary != null && primary.enabled() && pendingFor <= 0F && fly >= 1F) fire(primary);
            return;
        }
        holdKey = down;
    }

    private void fire(Act a) {
        if (a.from() == FROM_HELD) {
            AnvilPayloads.Held held = HallClient.held;
            if (held == null) return;
            AnvilClient.send(held.pos(), AnvilPayloads.HELD, a.code(), 0F, 0F);
        } else {
            ArmouryCache.ask(a.code(), style, ArmsArmoury.TIERS[tier]);
        }
        pendingFor = 5F;
    }
}
