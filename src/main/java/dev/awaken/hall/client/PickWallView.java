package dev.awaken.hall.client;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.PickAnvil;
import dev.awaken.arms.client.ArmouryCache;
import dev.awaken.delve.forged.ForgedPickItem;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.hall.HallNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Picks wall (SPEC §49, §52): the four forms of forged pick in niches, ranks receding upward, lit as the Arms wall is,
 * round your lantern and below your level, what you can make or already have standing lit in the dark. Under the tabs, the
 * pick in your hand: point at it and click, and the niche it can become comes close.
 */
final class PickWallView {
    private static final Identifier DARK = Awaken.id("textures/gui/hall/pick_dark.png"), LIT = Awaken.id("textures/gui/hall/pick_lit.png");
    private static final int KEY_DARK = 13, KEY_LIT = 14, KEY_HOVER = 15;
    private static final int HOT = 0xFFC4E6FF;
    /** The pick in your hand, under the tabs: where it's drawn, and the box a click takes. */
    private static final float HAND_X = 30F, HAND_Y = 96F, HAND_ICON = 3F, HAND_W = 400F, HAND_H = 58F;

    private final Component quiet = Stage.blackletter("Every pick you could carry");
    private final Component[] letters = new Component[HallNet.ROWS];
    private final float[] filterX = new float[WallView.FILTERS.length];

    int filter = WallView.ALL;
    float lampX = 854F, lampY = 470F;
    HallLayout.@Nullable Cell hover;
    boolean handHover;
    private Placed[] dark = new Placed[0], lit = dark, lifted = dark;
    private long version, liftVersion;
    private HallNet.@Nullable Wall builtWall;
    private @Nullable Object builtView, builtHeld;
    private int builtFilter = -1;
    private int tide = HallLayout.ROW_TOP[0] - 14;
    private int ready, tracked;
    private HallLayout.@Nullable Cell liftedFor, hoverNamed;
    private int liftedState = -1;
    private @Nullable Component hoverName;

    PickWallView() {
        for (int i = 0; i < letters.length; i++) letters[i] = Stage.blackletter(ArmsArmoury.TIERS[i]);
    }

    /** How a niche's pick is drawn when it's lit: a form not yet learned is plain iron (known of, not yours), short of parts dim. */
    static int mode(int state) {
        return state == HallNet.SHAPE ? Placed.IRON : state == HallNet.KNOWN ? Placed.DIM : Placed.FULL;
    }

    static boolean isTracked(int col, int tier) {
        return ArmouryCache.tracked(HallPicks.style(col), ArmsArmoury.TIERS[tier]);
    }

    private boolean matches(int state, int col, int tier) {
        return switch (filter) {
            case WallView.READY -> state == HallNet.READY || !HallPicks.rows(col, tier).isEmpty() && HallPicks.rows(col, tier).getFirst().ready();
            case WallView.TRACKED -> isTracked(col, tier);
            default -> true;
        };
    }

    private void rebuild(HallNet.Wall w) {
        Object v = ArmouryCache.view, held = HallClient.held;
        if (w == builtWall && v == builtView && held == builtHeld && filter == builtFilter) return;
        builtWall = w;
        builtView = v;
        builtHeld = held;
        builtFilter = filter;
        version++;
        tide = HallLayout.ROW_TOP[WallView.youTier(w)] - 14;
        Placed[] d = new Placed[PickLayout.COLS * HallNet.ROWS], l = new Placed[d.length];
        int n = 0, r = 0, t = 0;
        for (int col = 0; col < PickLayout.COLS; col++) {
            for (int tier = 0; tier < HallNet.ROWS; tier++) {
                int state = w.pick(col, tier);
                if (state == HallNet.READY) r++;
                if (isTracked(col, tier)) t++;
                String id = HallPicks.id(col, tier);
                Meshes.Up up = Meshes.up(id);
                if (up == null) continue;
                HallLayout.Cell c = PickLayout.cell(col, tier);
                float scale = PickLayout.portrait(up, c);
                boolean m = matches(state, col, tier), standing = (state == HallNet.MADE || state == HallNet.READY) && m;
                d[n] = new Placed(id, c.x(), c.cy(), scale, -24F, 6F, 0F, standing ? Placed.FULL : Placed.SHAPE, standing ? .95F : .9F);
                l[n] = new Placed(id, c.x(), c.cy(), scale, -24F, 6F, 0F, mode(state), m ? 1F : .13F);
                n++;
            }
        }
        ready = r;
        tracked = t;
        dark = java.util.Arrays.copyOf(d, n);
        lit = java.util.Arrays.copyOf(l, n);
    }

    /** The wall itself, scaled about (zx, zy) as the player steps in to a niche. */
    void draw(Stage s, HallNet.Wall w, float zoom, float zx, float zy) {
        rebuild(w);
        var g = s.g;
        g.pose().pushMatrix();
        if (zoom != 1F) g.pose().translate(zx, zy).scale(zoom, zoom).translate(-zx, -zy);
        float real = s.real(1.5F);
        g.blit(RenderPipelines.GUI_TEXTURED, DARK, 0, 0, 0F, 0F, Stage.W, Stage.H, Stage.W, Stage.H, Stage.W, Stage.H);
        g.submitPictureInPictureRenderState(new PicState(KEY_DARK, 0, 0, Stage.W, Stage.H, real, version, dark, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, g.pose()));
        g.nextStratum();
        boolean shaded = HallPipelines.lanternOk();
        int lamp = HallPipelines.packLamp(lampX, lampY, tide);
        g.blit(shaded ? HallPipelines.LANTERN : RenderPipelines.GUI_TEXTURED, LIT, 0, 0, 0F, 0F, Stage.W, Stage.H, Stage.W, Stage.H, Stage.W, Stage.H, shaded ? lamp : -1);
        g.submitPictureInPictureRenderState(new PicState(KEY_LIT, 0, 0, Stage.W, Stage.H, real, version, lit,
                shaded ? HallPipelines.LANTERN_PIC : RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, shaded ? lamp : -1, g.pose()));
        g.nextStratum();
        HallLayout.Cell e = PickLayout.cell(0, WallView.youTier(w)), f = PickLayout.cell(PickLayout.COLS - 1, WallView.youTier(w));
        s.glow(854F, tide, (f.x() - e.x()) / 2F + f.w(), 5F, 0xFFCF7A, .9F);
        lift(s, w, real);
        g.pose().popMatrix();
    }

    /** The hover state: light in the niche you point at, and its pick again, fully lit and a touch larger. */
    private void lift(Stage s, HallNet.Wall w, float real) {
        HallLayout.Cell h = hover;
        if (h == null) return;
        int state = w.pick(h.col(), h.tier());
        if (h != liftedFor || state != liftedState) {
            liftedFor = h;
            liftedState = state;
            liftVersion++;
            String id = HallPicks.id(h.col(), h.tier());
            Meshes.Up up = Meshes.up(id);
            lifted = up == null ? new Placed[0] : new Placed[] {new Placed(id, h.x(), h.cy(), PickLayout.portrait(up, h) * 1.1F, -24F, 6F, 0F, mode(state), 1F)};
        }
        s.glow(h.x(), h.cy(), h.w() * .62F, (h.y1() - h.y0()) * .58F, 0xFFCF7A, .7F);
        s.glow(h.x(), h.cy(), h.w() * .8F, (h.y1() - h.y0()) * .66F, 0xC4E6FF, .34F);
        if (lifted.length == 0) return;
        s.g.submitPictureInPictureRenderState(new PicState(KEY_HOVER, 0, 0, Stage.W, Stage.H, real, liftVersion, lifted, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, s.g.pose()));
        s.g.nextStratum();
    }

    /** Everything that names the wall: caption, purse, the pick in your hand, ranks and forms. */
    void ui(Stage s, HallNet.Wall w) {
        caption(s, w);
        purse(s);
        hand(s);
        int you = WallView.youTier(w);
        for (int i = 0; i < HallNet.ROWS; i++) {
            float x = PickLayout.left(i) - 78F, mid = (HallLayout.ROW_TOP[i] + HallLayout.ROW_BOTTOM[i]) / 2F;
            s.title(letters[i], x, mid - 24F, 2F, 0xFF000000 | Stage.RANK[i]);
            s.text(Integer.toString(ArmsArmoury.LEVEL[i]), x + 6F, mid + 20F, 2F, i <= you ? Stage.LABEL : Stage.DIM, true);
        }
        for (int c = 0; c < PickLayout.COLS; c++) s.centre(HallPicks.label(c), PickLayout.COL_X[c], 930F, 3F, Stage.LABEL);
        HallLayout.Cell hc = hover;
        if (hc != null) s.brackets(hc.x() - hc.w() / 2F + 2F, hc.y0() + 2F, hc.x() + hc.w() / 2F - 2F, hc.y1() - 2F, Math.max(12F, hc.w() * .14F), 4, HOT);
        s.right("You, level " + w.level(), 1684F, tide - 30F, 3F, Stage.LANTERN);
        // the tracked pick's niche carries a blue mark, the one in your hand an amber one
        ForgedPickItem held = HallPicks.forged();
        for (int col = 0; col < PickLayout.COLS; col++) {
            for (int tier = 0; tier < HallNet.ROWS; tier++) {
                HallLayout.Cell c = PickLayout.cell(col, tier);
                if (isTracked(col, tier)) s.fill(c.x() - 7F, c.y1() - 6F, c.x() + 7F, c.y1() - 2F, Stage.EDGE);
                if (held != null && held.form.ordinal() == col && held.tier == tier) s.fill(c.x() - 22F, c.y1() - 12F, c.x() + 22F, c.y1() - 8F, Stage.LANTERN);
            }
        }
    }

    private void caption(Stage s, HallNet.Wall w) {
        HallLayout.Cell h = hover;
        if (h == null) {
            if (handHover && handTarget(w) != null) {
                s.titleCentre(quiet, 854F, 26F, 2F, Stage.TITLE);
                WallView.words(s, "Click to see what the anvil can do with it.", filterX, Stage.LABEL);
                return;
            }
            s.titleCentre(quiet, 854F, 26F, 2F, Stage.TITLE);
            s.centre("Click one to bring it close.", 854F, 92F, 3F, Stage.LABEL);
            return;
        }
        int state = w.pick(h.col(), h.tier());
        if (hoverNamed != h || hoverName == null) {
            hoverNamed = h;
            hoverName = Stage.blackletter(state == HallNet.SHAPE ? HallPicks.label(h.col()) : HallPicks.name(h.col(), h.tier()));
        }
        s.titleCentre(hoverName, 854F, 26F, 2F, 0xFF000000 | Stage.RANK[h.tier()]);
        AnvilPayloads.Option row = HallPicks.rows(h.col(), h.tier()).stream().findFirst().orElse(null);
        String words = row != null ? rowWords(row) : stateWords(state, h);
        boolean good = row != null ? row.ready() : state == HallNet.READY;
        WallView.words(s, words + (isTracked(h.col(), h.tier()) ? " · Tracked" : ""), filterX, good ? Stage.GOOD : Stage.LABEL);
    }

    /** What the anvil would do to the pick in your hand here, in a few words. */
    private static String rowWords(AnvilPayloads.Option row) {
        String what = switch (row.kind() % 100) {
            case AnvilWork.RAISE -> "Raise yours to this";
            case AnvilWork.REDRAW -> "Re-draw yours as this";
            case AnvilWork.WORK_IN -> "Yours: work in what you've done, free";
            case AnvilWork.DRESS -> "Yours: dress the edge";
            case AnvilWork.MIGRATE -> "Remake your old pick as this, free";
            default -> row.name();
        };
        return row.ready() ? what + " here" : what + " (not yet)";
    }

    private static String stateWords(int state, HallLayout.Cell h) {
        return switch (state) {
            case HallNet.MADE -> carries(h.col(), h.tier()) ? "You carry one" : "You have forged this before";
            case HallNet.READY -> "You can forge this now";
            case HallNet.KNOWN -> "You don't have the parts yet";
            case HallNet.OVER -> "Opens later at level " + ArmsArmoury.LEVEL[h.tier()];
            default -> HallPicks.form(h.col()) == dev.awaken.delve.forged.PickForm.BREAKER ? "Learn it: break a lode's heart"
                    : "Learn it: light a Delver's lamp";
        };
    }

    static boolean carries(int col, int tier) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        var item = ForgedPicks.get(HallPicks.form(col), tier);
        for (ItemStack it : player.getInventory()) if (it.is(item)) return true;
        return false;
    }

    private void purse(Stage s) {
        var v = ArmouryCache.view;
        if (v != null) {
            boolean anvil = v.door() == ArmsArmoury.ANVIL;
            s.right(anvil ? "At the" : "Picks are made", 1684F, 18F, 3F, Stage.EDGE);
            s.right(anvil ? "War Anvil" : "at a War Anvil", 1684F, 46F, 3F, Stage.LANTERN);
        }
        WallView.filters(s, filter, filterX, ready, tracked);
    }

    // ---- the pick in your hand -----------------------------------------------------------------------------------------

    /** The niche the pick in your hand can become (or is), or null when your hand holds no forged or old pick. */
    HallLayout.@Nullable Cell handTarget(HallNet.Wall w) {
        int[] at = HallPicks.opening(WallView.youTier(w));
        return at == null ? null : PickLayout.cell(at[0], at[1]);
    }

    private void hand(Stage s) {
        ItemStack held = HallPicks.hand();
        if (held.isEmpty() || !PickAnvil.isPick(held)) return;
        boolean ours = HallPicks.forged() != null || HallPicks.old() != null;
        if (handHover && ours) {
            s.fill(HAND_X - 10F, HAND_Y - 8F, HAND_X + HAND_W, HAND_Y + HAND_H, 0x552A3A56);
            s.brackets(HAND_X - 10F, HAND_Y - 8F, HAND_X + HAND_W, HAND_Y + HAND_H, 12F, 3, HOT);
        }
        Panel.icon(s, held, HAND_X, HAND_Y, HAND_ICON);
        float tx = HAND_X + 16F * s.snap(HAND_ICON) + 12F, room = HAND_X + HAND_W - 8F - tx;
        s.fitted("In your hand: " + held.getHoverName().getString(), tx, HAND_Y + 2F, room, 2F, handHover && ours ? Stage.TITLE : Stage.LABEL, -1);
        s.fitted(handLine(), tx, HAND_Y + 28F, room, 2F, HallPicks.anyRows() ? Stage.LANTERN : Stage.DIM, -1);
    }

    private static String handLine() {
        if (HallPicks.old() != null) return HallPicks.anyRows() ? "The anvil remakes it free. Click it." : "Any War Anvil remakes it free.";
        if (HallPicks.forged() != null) return HallPicks.anyRows() ? "Raise it, re-draw it or dress it. Click it." : "Hold it at a War Anvil to raise or dress it.";
        return "A forged pick digs faster and lasts.";
    }

    boolean handAt(float x, float y) {
        return (HallPicks.forged() != null || HallPicks.old() != null) && x >= HAND_X - 10F && x < HAND_X + HAND_W && y >= HAND_Y - 8F && y < HAND_Y + HAND_H;
    }

    int filterAt(float x, float y) {
        return WallView.filterAt(filterX, x, y);
    }

    void move(float dt, float toX, float toY) {
        float t = 1F - (float) Math.pow(.0009F, dt);
        lampX += (toX - lampX) * t;
        lampY += (toY - lampY) * t;
    }
}
