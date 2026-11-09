package dev.awaken.hall.client;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsHomes;
import dev.awaken.arms.ArmsItems;
import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.anvil.WarAnvil;
import dev.awaken.arms.client.ArmouryCache;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.ForgeHooks;
import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One weapon, close (SPEC §52): the wall falls away, the weapon flies out of its niche and hangs turning, the six ranks of
 * its kind on the left and on the right what it does, what's special about it and what it takes to make.
 */
final class PieceView {
    private static final int KEY_HERO = 3, KEY_LADDER = 4;
    private static final float CX = 580F, CY = 486F;
    private static final int LADDER_X = 26, LADDER_TOP = 120, LADDER_BOTTOM = 848, RUNG = (LADDER_BOTTOM - LADDER_TOP) / 6, PORTRAIT = 88;
    private static final float PANEL_X0 = 1020F, PANEL_X1 = 1684F, PANEL_Y0 = 30F, PANEL_Y1 = 930F, X = PANEL_X0 + 32F, RIGHT = PANEL_X1 - 32F, TEXT_W = RIGHT - X;
    private static final float HOLD_SECONDS = .6F, FLY_SECONDS = .5F, BACK_SECONDS = .3F;
    private static final int FORGE_FIRE = 0xFFF2B35F;
    private static final int NEEDS_ACROSS = 4;
    private static final float NEED_W = TEXT_W / NEEDS_ACROSS, NEED_H = 128F;

    record Act(String label, int op, boolean hold, boolean enabled, int key) {}

    /** What goes into the forge: the real item (a group's members in turn, so you know any of them when you find it), the count, its name and where to find it. */
    private record Need(ItemStack[] icons, String count, String name, List<FormattedCharSequence> nameLines, boolean ok, String source) {}

    // what's shown
    int col, tier;
    String style = "longsword", id = "wildhide_longsword";
    final LordForged.@Nullable Forged lord;
    private final HallLayout.@Nullable Cell from;
    private final float fromX, fromY, fromS;
    private String shownId;
    private float morph = 1F;

    // the flight in and out, the turn
    float fly;
    boolean closing;
    private float yaw = -24F, vel = 20F, bob;
    boolean dragging;
    private long heroVersion, ladderVersion;
    private Placed[] ladder = new Placed[0];

    // the panel: rebuilt only when what it reads changes
    private boolean dirty = true;
    private ArmsPayloads.@Nullable ArmouryDetail panelDetail;
    private ArmsPayloads.@Nullable ArmouryView panelView;
    private HallNet.@Nullable Wall panelWall;
    private String eyebrow = "";
    private Component name = Component.empty();
    private float nameSize = 4F;
    private int rank;
    private List<FormattedCharSequence> blurb = List.of(), why = List.of();
    /** Whole paragraphs, so the panel leaves one out rather than cutting it off. */
    private final List<List<FormattedCharSequence>> special = new ArrayList<>(4);
    private final List<String[]> stats = new ArrayList<>(4);
    private String compare = "";
    private int compareColour = Stage.LABEL;
    private final List<Need> needs = new ArrayList<>(6);
    private int whyColour = Stage.LABEL;
    private @Nullable Act primary;
    private final List<Act> subs = new ArrayList<>(3);
    private String prevName = "", nextName = "";

    // what the last frame drew where
    private float primaryY0, primaryY1;
    private final float[] subX0 = new float[3], subX1 = new float[3];
    private float subY0, subY1;
    private int hoverNeed = -1;
    float hold;
    boolean holdKey, holdMouse;
    private float pendingFor;

    PieceView(int col, int tier, HallLayout.Cell from) {
        this.col = col;
        this.tier = tier;
        this.lord = null;
        this.from = from;
        this.fromX = from.x();
        this.fromY = from.cy();
        select(col, tier);
        this.shownId = id;
        Meshes.Up up = Meshes.up(id);
        this.fromS = up == null ? 1F : HallLayout.portrait(up, from.height());
    }

    PieceView(LordForged.Forged forged, float sealX, float sealY) {
        this.lord = forged;
        this.from = null;
        this.fromX = sealX;
        this.fromY = sealY;
        this.id = forged.weapon();
        ArmsData.Weapon w = ArmsItems.data(id);
        this.style = w == null ? "longsword" : w.style();
        this.col = Math.max(0, HallLayout.styleColumn(style));
        this.tier = w == null ? 5 : Math.max(0, tierOf(w.tier()));
        this.shownId = id;
        Meshes.Up up = Meshes.up(id);
        this.fromS = up == null ? 1F : 24F / up.length();
    }

    private static int tierOf(String t) {
        for (int i = 0; i < ArmsArmoury.TIERS.length; i++) if (ArmsArmoury.TIERS[i].equals(t)) return i;
        return -1;
    }

    void select(int newCol, int newTier) {
        col = Math.clamp(newCol, 0, HallNet.COLS - 1);
        tier = Math.clamp(newTier, 0, HallNet.ROWS - 1);
        style = HallNet.COLUMNS[col];
        id = WallView.weaponId(col, tier);
        dirty = true;
        ladderVersion++;
        hold = 0F;
        pendingFor = 0F;
        if (morph >= 1F) morph = 0F;
        ArmouryCache.ask(ArmsArmoury.DETAIL, style, ArmsArmoury.TIERS[tier]);
    }

    void step(int dc, int dt) {
        if (lord != null) return;
        int c = Math.clamp(col + dc, 0, HallNet.COLS - 1), t = Math.clamp(tier + dt, 0, HallNet.ROWS - 1);
        if (c != col || t != tier) select(c, t);
    }

    static String weaponName(String weaponId) {
        Item item = BuiltInRegistries.ITEM.getValue(Awaken.id(weaponId));
        return item == Items.AIR ? weaponId : item.getName(new ItemStack(item)).getString();
    }

    private static float ease(float t) {
        float u = 1F - t;
        return 1F - u * u * u;
    }

    float eased() {
        return ease(fly);
    }

    /** True once it has flown all the way back. */
    boolean advance(float dt) {
        fly = closing ? Math.max(0F, fly - dt / BACK_SECONDS) : Math.min(1F, fly + dt / FLY_SECONDS);
        return closing && fly <= 0F;
    }

    float zoomX() {
        return fromX;
    }

    float zoomY() {
        return fromY;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    void draw(Stage s, HallNet.Wall w, float dt, float time, double mouseX, double mouseY) {
        rebuild(s, w);
        float e = eased();
        float mx = s.sx(mouseX), my = s.sy(mouseY);
        int glow = Stage.RANK[tier];
        s.glow(CX, CY, 720F, 620F, glow, .30F * e);
        s.glow(CX, CY, 170F, 620F, glow, .28F * e);
        hero(s, dt, time);
        if (fly <= .15F) return;
        float slide = (1F - e) * (1F - e);
        s.g.nextStratum();
        if (lord == null) drawLadder(s, w, -180F * slide);
        drawPanel(s, 720F * slide, mx, my, dt, time);
        drawFoot(s, slide);
    }

    private void hero(Stage s, float dt, float time) {
        if (!dragging) {
            vel += (12F - vel) * Math.min(1F, dt * 2.2F);
            yaw += vel * dt;
        }
        if (yaw > 180F) yaw -= 360F;
        if (yaw < -180F) yaw += 360F;
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
        float aim = Math.min(880F, 800F * (float) Math.pow(up.length() / 2.4F, .35F)) / up.length();
        float size = fromS + (aim - fromS) * e;
        float alpha = morph >= 1F ? 1F : morph < .5F ? 1F - morph * 2F : morph * 2F - 1F;
        float shownYaw = -24F + (yaw + 24F) * (closing ? e : Math.min(1F, fly * 3F));
        Placed p = new Placed(shownId, fromX + (CX - fromX) * e, fromY + (CY + bob - fromY) * e, size, shownYaw, 6F + e, -18F * e, modeOf(), alpha);
        heroVersion++;
        s.g.submitPictureInPictureRenderState(new PicState(KEY_HERO, 0, 0, Stage.W, Stage.H, s.rps, heroVersion, new Placed[] {p},
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, s.g.pose()));
    }

    private int mode = Placed.FULL;

    private int modeOf() {
        return mode;
    }

    void drag(float dx) {
        yaw += dx * .4F;
        vel = dx * 6F;
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

    private final Component[] letter = {Stage.blackletter("E"), Stage.blackletter("D"), Stage.blackletter("C"), Stage.blackletter("B"), Stage.blackletter("A"), Stage.blackletter("S")};
    private long ladderBuilt = -1;
    private HallNet.@Nullable Wall ladderWall;

    private static float rungY(int t) {
        return LADDER_BOTTOM - RUNG * (t + .5F);
    }

    private void buildLadder(HallNet.Wall w) {
        ladderBuilt = ladderVersion;
        ladderWall = w;
        int you = WallView.youTier(w);
        List<Placed> out = new ArrayList<>(6);
        for (int t = 0; t < HallNet.ROWS; t++) {
            String rid = WallView.weaponId(col, t);
            Meshes.Up up = Meshes.up(rid);
            if (up == null) continue;
            int state = w.at(col, t);
            float f = .6F + .4F * Math.min(1F, up.length() / 3F);
            float alpha = t == tier ? 1F : t <= you ? .8F : .5F;
            int m = state == HallNet.SHAPE ? Placed.SHAPE : state == HallNet.OFFER ? Placed.IRON : Placed.FULL;
            out.add(new Placed(rid, LADDER_X + PORTRAIT / 2F, rungY(t), PORTRAIT * .95F * f / up.length(), -24F, 6F, 0F, m, alpha));
        }
        ladder = out.toArray(new Placed[0]);
    }

    private void rebuild(Stage s, HallNet.Wall w) {
        var d = ArmouryCache.detail;
        var v = ArmouryCache.view;
        if (!dirty && d == panelDetail && v == panelView && w == panelWall) return;
        if (panelDetail != d || panelView != v || panelWall != w) pendingFor = 0F;
        dirty = false;
        panelDetail = d;
        panelView = v;
        panelWall = w;
        ArmsPayloads.ArmouryDetail here = d != null && d.style().equals(style) && d.tier().equals(ArmsArmoury.TIERS[tier]) ? d : null;
        int state = lord != null ? HallNet.MADE : w.at(col, tier);
        boolean hidden = state == HallNet.SHAPE;
        mode = hidden ? Placed.SHAPE : state == HallNet.OFFER ? Placed.IRON : Placed.FULL;
        rank = tier;
        ArmsData.Weapon data = ArmsItems.data(id);
        String title = hidden ? "Unknown" : lord != null ? weaponName(id) : WallView.styleName(style);
        name = Stage.blackletter(title);
        nameSize = 5F;
        while (nameSize > 2F && s.titleWidth(name, nameSize) > TEXT_W) nameSize -= 1F;
        String tierWord = data == null ? ArmsArmoury.TIERS[tier] : ArmsArmoury.PREFIX[tier].substring(0, 1).toUpperCase(Locale.ROOT) + ArmsArmoury.PREFIX[tier].substring(1);
        eyebrow = hidden ? "Not found · " + ArmsArmoury.TIERS[tier] + " rank" : lord != null ? "Lord-forged · " + ArmsArmoury.TIERS[tier] + " rank" : tierWord + " · " + ArmsArmoury.TIERS[tier] + " rank";
        String blurbText = hidden ? "A shape on the wall, nothing more." : Language.getInstance().getOrDefault("awaken.style." + style + ".blurb");
        blurb = s.wrap(blurbText, TEXT_W, 3F);

        stats.clear();
        compare = "";
        ItemStack stack = ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(Awaken.id(id));
        if (item != Items.AIR) stack = new ItemStack(item);
        if (!hidden && data != null) {
            float damage = data.damage() + 1F;
            stats.add(new String[] {HallSpecial.n(damage), "Damage", ""});
            stats.add(new String[] {HallSpecial.n(data.speed() + 4F), "Swings a second", ""});
            stats.add(new String[] {String.format(Locale.ROOT, "%,d", data.durability()), "Uses", ""});
            Archetypes.Loaded l = Archetypes.get(style);
            float reach = 0F;
            if (l != null) for (Archetype.Move m : l.chain()) {
                Archetype.MoveDef def = l.move(m);
                if (def != null) reach = Math.max(reach, def.reach());
            }
            if (reach > 0F) stats.add(new String[] {HallSpecial.n(reach), "Reach", ""});
            compare(damage);
        }

        List<String> sp = new ArrayList<>();
        if (!hidden) {
            sp.addAll(HallSpecial.of(style));
            if (lord != null) sp.addAll(HallSpecial.lord(lord));
            if (!stack.isEmpty()) sp.addAll(ForgeHooks.cardLines(stack));
        }
        special.clear();
        for (String one : sp) special.add(s.wrap(one, TEXT_W, 3F));

        needs.clear();
        if (!hidden && lord == null && here != null) for (ArmsPayloads.NeedRow n : here.needs()) needs.add(need(s, n));
        actions(s, w, state, hidden, here);
        if (lord == null) {
            prevName = neighbour(w, col - 1);
            nextName = neighbour(w, col + 1);
        }
    }

    private String tierPull(ArmsPayloads.ArmouryDetail here) {
        return tier < here.tiers().size() ? here.tiers().get(tier).pull() : "";
    }

    private String neighbour(HallNet.Wall w, int c) {
        if (c < 0 || c >= HallNet.COLS) return "";
        return w.at(c, tier) == HallNet.SHAPE ? "Not found" : WallView.styleName(HallNet.COLUMNS[c]);
    }

    private void compare(float damage) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        ArmsData.Weapon h = held.isEmpty() ? null : ArmsItems.of(held.getItem());
        if (h == null) return;
        if (h.id().equals(id)) {
            compare = "This is the one in your hand.";
            compareColour = Stage.LABEL;
            return;
        }
        float delta = damage - (h.damage() + 1F);
        String on = held.getItem().getName(held).getString();
        if (Math.abs(delta) < .05F) {
            compare = "The same damage as your " + on + ".";
            compareColour = Stage.LABEL;
        } else {
            compare = (delta > 0F ? "+" : "") + HallSpecial.n(delta) + " on your " + on;
            compareColour = delta > 0F ? Stage.GOOD : Stage.BAD;
        }
    }

    private static Need need(Stage s, ArmsPayloads.NeedRow n) {
        Identifier nid = Identifier.parse(n.id());
        List<ItemStack> icons = new ArrayList<>(1);
        String label;
        if (n.kind().equals("tag")) {
            label = Component.translatable("tag.item." + nid.getNamespace() + "." + nid.getPath()).getString();
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, nid))) icons.add(new ItemStack(h.value()));
        } else {
            Item it = BuiltInRegistries.ITEM.getValue(nid);
            ItemStack stack = new ItemStack(it);
            icons.add(stack);
            label = it.getName(stack).getString();
        }
        boolean ok = n.have() >= n.need();
        String key = n.kind().equals("tag") ? "awaken.source.tag." + nid.getPath()
                : nid.getPath().endsWith("_blueprint") ? "awaken.source.blueprint" : "awaken.source." + nid.getNamespace() + "." + nid.getPath();
        String source = Language.getInstance().has(key) ? Language.getInstance().getOrDefault(key) : "";
        return new Need(icons.toArray(new ItemStack[0]), Math.min(n.have(), n.need()) + "/" + n.need(), label, s.wrap(label, NEED_W - 8F, 2F), ok, source);
    }

    private void actions(Stage s, HallNet.Wall w, int state, boolean hidden, ArmsPayloads.@Nullable ArmouryDetail here) {
        var v = ArmouryCache.view;
        primary = null;
        subs.clear();
        String line = "";
        whyColour = Stage.LABEL;
        if (hidden) {
            line = "You have not found this pattern. It is out there.";
        } else if (lord != null) {
            line = "Taken from a Gate Lord. It can't be forged.";
        } else if (v != null) {
            int door = v.door();
            var entry = WallView.entry(style);
            String tierName = ArmsArmoury.TIERS[tier];
            boolean tracked = ArmouryCache.tracked(style, tierName);
            if (state == HallNet.OFFER) {
                if (door == ArmsArmoury.HALE && entry != null) primary = new Act("Buy pattern · " + entry.price() + " essence", ArmsArmoury.BUY, true, entry.afford(), 0);
                else line = entry == null ? "You don't know this pattern yet." : "You don't know this pattern yet. Hale sells it for " + entry.price() + " essence.";
                if (door != ArmsArmoury.HALE || !entry_afford(entry)) line = line.isEmpty() ? (entry == null ? "" : "You need " + entry.price() + " essence for this pattern.") : line;
                ArmsHomes.Home home = ArmsHomes.of(style);
                if (home != null) line += (line.isEmpty() ? "" : " ") + home.gate() + " teaches it for free, in " + home.rank().label + "-rank gates.";
            } else {
                boolean anvil = here != null && (here.anvil() & WarAnvil.Status.SHOWN) != 0;
                if (anvil) {
                    int a = here.anvil();
                    if ((a & WarAnvil.Status.REBUILD) != 0) primary = new Act("rebuild it", ArmsArmoury.REBUILD, true, (a & WarAnvil.Status.REBUILD_OK) != 0, 0);
                    else primary = new Act("forge it", ArmsArmoury.FORGE_IT, true, (a & WarAnvil.Status.FORGE) != 0, 0);
                    if ((a & WarAnvil.Status.MEMORY) != 0 && (a & WarAnvil.Status.REBUILD) == 0)
                        subs.add(new Act("From memory", ArmsArmoury.FROM_MEMORY, false, (a & WarAnvil.Status.MEMORY_OK) != 0, com.mojang.blaze3d.platform.InputConstants.KEY_M));
                    if ((a & WarAnvil.Status.BLADE) != 0) subs.add(new Act("Forge the blade", ArmsArmoury.FORGE_BLADE, false, true, 0));
                    line = here.block();
                } else if (door == ArmsArmoury.FORGE) {
                    if (!v.raise().isEmpty()) {
                        String[] r = v.raise().split("\\|", 2);
                        boolean ready = r.length > 1 && r[1].equals("ready");
                        primary = new Act("raise it to " + r[0], ArmsArmoury.RAISE, true, ready, 0);
                        if (!ready && r.length > 1) line = "The weapon in your hand: " + r[1];
                    }
                    if (entry != null && entry.state() == 0 && !style.equals("longsword")) subs.add(new Act("Draw a copy", ArmsArmoury.DRAW, false, entry.afford(), 0));
                    if (line.isEmpty()) line = "Take this to a War Anvil to make it.";
                } else if (door == ArmsArmoury.HALE) {
                    line = "You already know this pattern.";
                } else {
                    line = "Take this to a War Anvil to make it.";
                }
                if (state == HallNet.OVER && (line.isEmpty() || !anvil)) {
                    String pull = here == null ? "" : tierPull(here);
                    line = !pull.isEmpty() ? pull : ArmsArmoury.PREFIX[tier].substring(0, 1).toUpperCase(Locale.ROOT) + ArmsArmoury.PREFIX[tier].substring(1)
                            + " weapons open at level " + ArmsArmoury.LEVEL[tier] + ". You are level " + w.level() + ".";
                    if (door != ArmsArmoury.ANVIL) primary = null;
                }
                if (state == HallNet.MADE && primary == null) {
                    line = "You have made this. It's yours to carry.";
                    whyColour = Stage.GOOD;
                }
                subs.add(0, new Act(tracked ? "Stop tracking" : "Track it", tracked ? ArmsArmoury.UNTRACK : ArmsArmoury.TRACK, false, true, com.mojang.blaze3d.platform.InputConstants.KEY_T));
            }
        }
        why = line.isEmpty() ? List.of() : s.wrap(sentence(line), TEXT_W, 3F);
    }

    /** A gate's name can start a sentence ("the Fungal Hollow teaches it"). */
    private static String sentence(String line) {
        StringBuilder b = new StringBuilder(line);
        b.setCharAt(0, Character.toUpperCase(b.charAt(0)));
        for (int i = b.indexOf(". "); i >= 0 && i + 2 < b.length(); i = b.indexOf(". ", i + 2)) b.setCharAt(i + 2, Character.toUpperCase(b.charAt(i + 2)));
        return b.toString();
    }

    private static boolean entry_afford(ArmsPayloads.@Nullable StyleEntry e) {
        return e != null && e.afford();
    }

    private void drawPanel(Stage s, float dx, float mx, float my, float dt, float time) {
        var g = s.g;
        g.pose().pushMatrix().translate(dx, 0F);
        s.fill(PANEL_X0, PANEL_Y0, PANEL_X1, PANEL_Y1, 0xE60A0E16);
        s.outline(PANEL_X0, PANEL_Y0, PANEL_X1, PANEL_Y1, 2, 0x707FC3FF);
        s.fill(PANEL_X0, PANEL_Y0, PANEL_X0 + 64F, PANEL_Y0 + 3F, Stage.EDGE);
        s.fill(PANEL_X0, PANEL_Y0, PANEL_X0 + 3F, PANEL_Y0 + 64F, Stage.EDGE);
        s.fill(PANEL_X1 - 64F, PANEL_Y1 - 3F, PANEL_X1, PANEL_Y1, Stage.EDGE);
        s.fill(PANEL_X1 - 3F, PANEL_Y1 - 64F, PANEL_X1, PANEL_Y1, Stage.EDGE);
        g.nextStratum();
        int colour = 0xFF000000 | Stage.RANK[rank];
        float y = PANEL_Y0 + 22F;
        s.text(eyebrow, X, y, 3F, colour);
        y += 36F;
        float wide = s.titleWidth(name, nameSize);
        s.glow(X + wide / 2F, y + 12F * nameSize, wide / 2F + 40F, 22F * nameSize, Stage.RANK[rank], .22F);
        s.title(name, X, y, nameSize, Stage.TITLE);
        y += 26F * nameSize + 4F;
        y = lines(s, blurb, y, 2, Stage.LABEL) + 16F;

        // the numbers: wide as they need, side by side
        if (!stats.isEmpty()) {
            float x = X;
            for (String[] st : stats) {
                float column = Math.max(s.width(st[0], 5F), s.width(st[1], 2F));
                s.text(st[0], x, y, 5F, Stage.TITLE);
                s.text(st[1], x, y + 44F, 2F, Stage.EDGE);
                x += column + 30F;
            }
            y += 72F;
            if (!compare.isEmpty()) {
                s.text(s.fit(compare, TEXT_W, 3F), X, y, 3F, compareColour);
                y += 32F;
            }
            y += 8F;
        }

        // the action zone stands at the bottom; everything else fits above it
        float bottom = PANEL_Y1 - 22F, subTop = bottom - 36F;
        boolean hasSubs = !subs.isEmpty();
        float primaryTop = (hasSubs ? subTop - 12F : bottom) - 60F;
        float actionTop = primary != null ? primaryTop : hasSubs ? subTop : bottom;
        float whyTop = actionTop - 12F - why.size() * 30F;
        int needRows = (needs.size() + NEEDS_ACROSS - 1) / NEEDS_ACROSS;
        float needHeight = needs.isEmpty() ? 0F : 34F + NEED_H * needRows + 62F;

        if (!special.isEmpty()) {
            s.text("What makes it different", X, y, 3F, Stage.EDGE);
            y += 32F;
            float last = whyTop - needHeight - 8F;
            for (List<FormattedCharSequence> paragraph : special) {
                if (y + paragraph.size() * 30F > last) break;
                y = lines(s, paragraph, y, paragraph.size(), Stage.TITLE) + 6F;
            }
            y += 8F;
        }

        hoverNeed = -1;
        if (!needs.isEmpty()) {
            s.text("What it takes", X, y, 3F, Stage.EDGE);
            y += 34F;
            float top = y;
            for (int i = 0; i < needs.size(); i++) {
                Need n = needs.get(i);
                float cx = X + (i % NEEDS_ACROSS) * NEED_W, cy = top + (i / NEEDS_ACROSS) * NEED_H, mid = cx + NEED_W / 2F;
                if (fly >= 1F && mx >= cx && mx < cx + NEED_W && my >= cy && my < cy + NEED_H) hoverNeed = i;
                if (n.icons.length > 0) icon(s, n.icons[(int) (time / 1.2F) % n.icons.length], mid - 32F, cy);
                s.centre(n.count, mid, cy + 68F, 3F, n.ok ? Stage.GOOD : Stage.BAD);
                for (int l = 0; l < Math.min(2, n.nameLines.size()); l++) {
                    FormattedCharSequence line = n.nameLines.get(l);
                    s.text(line, mid - s.font.width(line) * s.snap(2F) / 2F, cy + 94F + l * 18F, 2F, n.ok ? Stage.LABEL : Stage.TITLE);
                }
            }
            y = top + needRows * NEED_H;
            Need shown = hoverNeed >= 0 ? needs.get(hoverNeed) : firstShort();
            if (shown != null) {
                lines(s, s.wrap(shown.source.isEmpty() ? shown.name : shown.source, TEXT_W, 3F), y + 2F, 2, Stage.DIM);
            }
        }

        lines(s, why, whyTop, why.size(), whyColour);
        primaryY0 = primaryTop;
        primaryY1 = primaryTop + 60F;
        if (primary != null) primaryButton(s, mx, my, dt);
        else hold = 0F;
        subY0 = subTop;
        subY1 = bottom;
        float x = X;
        for (int i = 0; i < subs.size() && i < subX0.length; i++) {
            Act a = subs.get(i);
            float w = s.width(a.label, 3F) + 24F;
            subX0[i] = x;
            subX1[i] = x + w;
            boolean over = fly >= 1F && a.enabled && mx >= x && mx < x + w && my >= subY0 && my < subY1;
            if (over) s.fill(x, subY0, x + w, subY1, 0x552A3A56);
            s.outline(x, subY0, x + w, subY1, 2, a.enabled ? 0xA07FC3FF : 0x602A3446);
            s.text(a.label, x + 12F, subY0 + 7F, 3F, a.enabled ? Stage.TITLE : Stage.DIM);
            x += w + 14F;
        }
        pendingFor = Math.max(0F, pendingFor - dt);
        g.pose().popMatrix();
    }

    private @Nullable Need firstShort() {
        for (Need n : needs) if (!n.ok) return n;
        return null;
    }

    /** The item itself, as your inventory draws it: 64 sheet pixels on a side. */
    private void icon(Stage s, ItemStack icon, float x, float y) {
        if (icon.isEmpty()) return;
        float size = s.snap(4F);
        s.g.pose().pushMatrix().translate(x, y).scale(size, size);
        s.g.item(icon, 0, 0);
        s.g.pose().popMatrix();
    }

    private static float lines(Stage s, List<FormattedCharSequence> text, float y, int most, int colour) {
        for (int i = 0; i < Math.min(most, text.size()); i++) {
            s.text(text.get(i), X, y, 3F, colour);
            y += 30F;
        }
        return y;
    }

    private void primaryButton(Stage s, float mx, float my, float dt) {
        Act a = primary;
        boolean pending = pendingFor > 0F;
        boolean can = a.enabled && !pending && fly >= 1F;
        boolean over = can && mx >= X && mx < RIGHT && my >= primaryY0 && my < primaryY1;
        if (!HallConfig.clickToCommit() && (holdKey || holdMouse) && can) {
            hold += dt / HOLD_SECONDS;
            if (hold >= 1F) {
                hold = 0F;
                holdKey = holdMouse = false;
                fire(a);
            }
        } else {
            hold = Math.max(0F, hold - dt * 4F);
        }
        s.fill(X, primaryY0, RIGHT, primaryY1, a.enabled ? (over ? 0xFF241A10 : 0xFF16120D) : 0xFF10141C);
        if (hold > 0F) {
            float end = X + (RIGHT - X) * hold;
            s.fill(X, primaryY0, end, primaryY1, 0xFFB23A08);
            s.fill(Math.max(X, end - 40F), primaryY0, end, primaryY1, 0xFFFF8A28);
            s.fill(Math.max(X, end - 6F), primaryY0, end, primaryY1, 0xFFFFF0C0);
        }
        s.outline(X, primaryY0, RIGHT, primaryY1, 3, a.enabled ? FORGE_FIRE : 0xFF2A3446);
        String label = a.label.substring(0, 1).toUpperCase(Locale.ROOT) + a.label.substring(1);
        if (a.label.equals("forge it") || a.label.equals("rebuild it") || a.label.startsWith("raise it")) label = (HallConfig.clickToCommit() ? "" : "Hold to ") + (HallConfig.clickToCommit() ? label : a.label);
        s.centre(pending ? "..." : label, (X + RIGHT) / 2F, primaryY0 + 18F, 3F, a.enabled ? 0xFFFFE9C4 : Stage.DIM);
    }

    private void drawFoot(Stage s, float slide) {
        float y = 916F + 60F * slide;
        s.text("Esc", 40F, y, 3F, Stage.EDGE);
        s.text("Back", 120F, y, 3F, Stage.LABEL);
        if (lord != null) {
            s.centre("Drag to turn", CX, y, 3F, Stage.DIM);
            return;
        }
        if (!prevName.isEmpty()) s.text("< " + prevName, 250F, y, 3F, Stage.LABEL);
        s.centre("Drag to turn", 640F, y, 3F, Stage.DIM);
        if (!nextName.isEmpty()) s.right(nextName + " >", 1000F, y, 3F, Stage.LABEL);
    }

    // ---- input --------------------------------------------------------------------------------------------------------

    /** Runs a button's work; true if a press there started or did something. */
    boolean click(float x, float y) {
        if (fly < 1F) return false;
        if (x >= 30F && x < 220F && y >= 906F && y < 946F) {
            closing = true;
            return true;
        }
        if (lord == null) {
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
        }
        if (primary != null && primary.enabled && pendingFor <= 0F && x >= X && x < RIGHT && y >= primaryY0 && y < primaryY1) {
            if (HallConfig.clickToCommit()) fire(primary);
            else holdMouse = true;
            return true;
        }
        if (y >= subY0 && y < subY1) {
            for (int i = 0; i < subs.size() && i < subX0.length; i++) {
                Act a = subs.get(i);
                if (x >= subX0[i] && x < subX1[i]) {
                    if (a.enabled) fire(a);
                    return true;
                }
            }
        }
        return x >= PANEL_X0;
    }

    boolean key(int key) {
        for (Act a : subs) {
            if (a.key == key) {
                if (a.enabled) fire(a);
                return true;
            }
        }
        return false;
    }

    void space(boolean down) {
        if (down && HallConfig.clickToCommit()) {
            if (primary != null && primary.enabled && pendingFor <= 0F && fly >= 1F) fire(primary);
            return;
        }
        holdKey = down;
    }

    private void fire(Act a) {
        ArmouryCache.ask(a.op, style, ArmsArmoury.TIERS[tier]);
        pendingFor = 5F;
    }
}
