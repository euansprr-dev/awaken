package dev.awaken.hall.client;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.LordForged;
import dev.awaken.arms.client.ArmouryCache;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.hall.HallNet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The Wall (SPEC §52): every weapon you could carry, in niches, ranks receding upward. Dark except round your lantern
 * and below the tide, your level; what you can already make stands lit in the dark as well.
 */
final class WallView {
    private static final Identifier DARK = Awaken.id("textures/gui/hall/wall_dark.png"), LIT = Awaken.id("textures/gui/hall/wall_lit.png");
    private static final int KEY_DARK = 1, KEY_LIT = 2;
    static final int ALL = 0, READY = 1, TRACKED = 2;
    static final String[] FILTERS = {"Everything", "Ready now", "Tracked"};
    private final float[] filterX = new float[FILTERS.length];
    private static final float SEAL_X = 30F, SEAL_Y = 92F;
    private static final int SEAL_W = 16, SEAL_H = 24, SEAL_STEP = 22;

    private final Component quiet = Stage.blackletter("Every weapon you could carry");
    private final Component[] letters = new Component[HallNet.ROWS];

    int filter = ALL;
    float lampX = 854F, lampY = 470F;
    HallLayout.@Nullable Cell hover;
    private Placed[] dark = new Placed[0], lit = dark;
    private long version;
    private HallNet.@Nullable Wall builtWall;
    private ArmsPayloads.@Nullable ArmouryView builtView;
    private int builtFilter = -1;
    private int tide = HallLayout.ROW_TOP[0] - 14;
    private int ready, tracked;
    private @Nullable Component hoverName;
    private HallLayout.@Nullable Cell hoverNamed;

    WallView() {
        for (int i = 0; i < letters.length; i++) letters[i] = Stage.blackletter(ArmsArmoury.TIERS[i]);
    }

    static String weaponId(int col, int tier) {
        return ArmsArmoury.PREFIX[tier] + "_" + HallNet.COLUMNS[col];
    }

    static int youTier(HallNet.Wall w) {
        int best = 0;
        for (int i = 0; i < ArmsArmoury.LEVEL.length; i++) if (w.level() >= ArmsArmoury.LEVEL[i]) best = i;
        return best;
    }

    private boolean isTracked(int col, int tier) {
        var v = ArmouryCache.view;
        return v != null && v.tracked().equals(HallNet.COLUMNS[col] + ":" + ArmsArmoury.TIERS[tier]);
    }

    private boolean matches(int state, int col, int tier) {
        return switch (filter) {
            case READY -> state == HallNet.READY;
            case TRACKED -> isTracked(col, tier);
            default -> true;
        };
    }

    private void rebuild(HallNet.Wall w) {
        var v = ArmouryCache.view;
        if (w == builtWall && v == builtView && filter == builtFilter) return;
        builtWall = w;
        builtView = v;
        builtFilter = filter;
        version++;
        tide = HallLayout.ROW_TOP[youTier(w)] - 14;
        Placed[] d = new Placed[HallNet.COLS * HallNet.ROWS], l = new Placed[d.length];
        int n = 0, r = 0, t = 0;
        for (int col = 0; col < HallNet.COLS; col++) {
            for (int tier = 0; tier < HallNet.ROWS; tier++) {
                int state = w.at(col, tier);
                if (state == HallNet.READY) r++;
                if (isTracked(col, tier)) t++;
                String id = weaponId(col, tier);
                Meshes.Up up = Meshes.up(id);
                if (up == null) continue;
                HallLayout.Cell c = HallLayout.cell(col, tier);
                float scale = HallLayout.portrait(up, c.height());
                boolean m = matches(state, col, tier), standing = (state == HallNet.MADE || state == HallNet.READY) && m;
                d[n] = new Placed(id, c.x(), c.cy(), scale, -24F, 6F, 0F, standing ? Placed.FULL : Placed.SHAPE, standing ? .95F : .9F);
                int mode = state == HallNet.SHAPE ? Placed.SHAPE : state == HallNet.OFFER ? Placed.IRON : state == HallNet.KNOWN ? Placed.DIM : Placed.FULL;
                l[n] = new Placed(id, c.x(), c.cy(), scale, -24F, 6F, 0F, mode, m ? 1F : .13F);
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
        s.glow(854F, tide, 830F, 5F, 0xFFCF7A, .9F);
        g.pose().popMatrix();
    }

    /** Everything that names the wall: tabs, caption, purse, seals, ranks and bays. */
    void ui(Stage s, HallNet.Wall w) {
        var v = ArmouryCache.view;
        caption(s, w);
        purse(s, v);
        seals(s, w);
        int you = youTier(w);
        for (int i = 0; i < HallNet.ROWS; i++) {
            float x = 16F + (1F - HallLayout.P[i]) * 790F - 8F, mid = (HallLayout.ROW_TOP[i] + HallLayout.ROW_BOTTOM[i]) / 2F;
            s.title(letters[i], x, mid - 24F, 2F, 0xFF000000 | Stage.RANK[i]);
            s.text(Integer.toString(ArmsArmoury.LEVEL[i]), x + 6F, mid + 20F, 2F, i <= you ? Stage.LABEL : Stage.DIM, true);
        }
        for (int i = 0; i < HallLayout.FAMILY.length; i++) s.centre(HallLayout.FAMILY[i], HallLayout.BAY_X[i], 930F, 3F, Stage.LABEL);
        s.right("You, level " + w.level(), 1684F, tide - 30F, 3F, Stage.LANTERN);
        // the tracked weapon's niche carries a blue mark
        if (v != null && !v.tracked().isEmpty()) {
            for (int col = 0; col < HallNet.COLS; col++) {
                for (int tier = 0; tier < HallNet.ROWS; tier++) {
                    if (!isTracked(col, tier)) continue;
                    HallLayout.Cell c = HallLayout.cell(col, tier);
                    s.fill(c.x() - 7F, c.y1() - 6F, c.x() + 7F, c.y1() - 2F, Stage.EDGE);
                }
            }
        }
    }

    private void caption(Stage s, HallNet.Wall w) {
        HallLayout.Cell h = hover;
        if (h == null) {
            s.titleCentre(quiet, 854F, 26F, 2F, Stage.TITLE);
            s.centre("Click anything to bring it close.", 854F, 92F, 3F, Stage.LABEL);
            return;
        }
        int state = w.at(h.col(), h.tier());
        if (hoverNamed != h || hoverName == null) {
            hoverNamed = h;
            hoverName = Stage.blackletter(state == HallNet.SHAPE ? "Something you have not found" : PieceView.weaponName(WallView.weaponId(h.col(), h.tier())));
        }
        s.titleCentre(hoverName, 854F, 26F, 2F, 0xFF000000 | Stage.RANK[h.tier()]);
        s.centre(stateWords(state, h) + (isTracked(h.col(), h.tier()) ? " · Tracked" : ""), 854F, 92F, 3F, state == HallNet.READY ? Stage.GOOD : Stage.LABEL);
    }

    private String stateWords(int state, HallLayout.Cell h) {
        return switch (state) {
            case HallNet.MADE -> "In your Hall";
            case HallNet.READY -> "You can forge this now";
            case HallNet.KNOWN -> "You know this pattern";
            case HallNet.OVER -> "Opens later at level " + ArmsArmoury.LEVEL[h.tier()];
            case HallNet.OFFER -> {
                var e = entry(HallNet.COLUMNS[h.col()]);
                yield e == null ? "Hale sells this pattern" : "Hale sells this pattern for " + e.price() + " essence";
            }
            default -> "You have not found this yet";
        };
    }

    static ArmsPayloads.@Nullable StyleEntry entry(String style) {
        var v = ArmouryCache.view;
        if (v == null) return null;
        for (var e : v.styles()) if (e.id().equals(style)) return e;
        return null;
    }

    private void purse(Stage s, ArmsPayloads.@Nullable ArmouryView v) {
        if (v != null) {
            String door = v.door() == ArmsArmoury.BAND ? "Your patterns" : "At " + Language.getInstance().getOrDefault("awaken.armoury.door." + switch (v.door()) {
                case ArmsArmoury.HALE -> "hale";
                case ArmsArmoury.FORGE -> "forge";
                default -> "anvil";
            });
            s.right(door, 1684F, 18F, 3F, Stage.EDGE);
            s.right(v.essence() + " essence", 1684F, 46F, 3F, Stage.LANTERN);
        }
        filters(s, filter, filterX, ready, tracked);
    }

    /** Everything, Ready now, Tracked: laid out from the right edge, the counts beside their words. */
    static void filters(Stage s, int on, float[] x, int ready, int tracked) {
        float right = 1684F;
        for (int i = FILTERS.length - 1; i >= 0; i--) {
            float w = s.width(label(i, ready, tracked), 3F);
            x[i] = right - w;
            right -= w + 32F;
        }
        for (int i = 0; i < FILTERS.length; i++) {
            boolean lit = on == i;
            String word = label(i, ready, tracked);
            s.text(word, x[i], 80F, 3F, lit ? Stage.TITLE : Stage.DIM);
            if (lit) s.fill(x[i], 110F, x[i] + s.width(word, 3F), 112F, Stage.LANTERN);
        }
    }

    private static String label(int i, int ready, int tracked) {
        return i == ALL ? FILTERS[i] : FILTERS[i] + " " + (i == READY ? ready : tracked);
    }

    private void seals(Stage s, HallNet.Wall w) {
        int found = 0;
        for (int i = 0; i < LordForged.ALL.size(); i++) {
            boolean open = (w.lords() >> i & 1) != 0;
            if (open) found++;
            float x = SEAL_X + i * SEAL_STEP;
            s.fill(x, SEAL_Y, x + SEAL_W, SEAL_Y + SEAL_H, open ? Stage.LANTERN : 0xFF1A2230);
            s.outline(x, SEAL_Y, x + SEAL_W, SEAL_Y + SEAL_H, 2, open ? 0xFFFFE9B8 : 0xFF2A3446);
        }
        s.text("Lord-forged " + found + " of " + LordForged.ALL.size(), SEAL_X, SEAL_Y + SEAL_H + 6F, 2F, Stage.DIM);
    }

    /** Which filter is under the sheet point, or -1. */
    int filterAt(float x, float y) {
        return filterAt(filterX, x, y);
    }

    static int filterAt(float[] filterX, float x, float y) {
        if (y < 76F || y > 116F) return -1;
        for (int i = 0; i < FILTERS.length; i++) if (x >= filterX[i] - 8F && (i == FILTERS.length - 1 || x < filterX[i + 1] - 8F)) return i;
        return -1;
    }

    /** Which found Lord-forged weapon's seal is under the point, or -1. */
    int sealAt(HallNet.Wall w, float x, float y) {
        if (y < SEAL_Y || y >= SEAL_Y + SEAL_H || x < SEAL_X) return -1;
        int i = (int) ((x - SEAL_X) / SEAL_STEP);
        return i < LordForged.ALL.size() && (w.lords() >> i & 1) != 0 ? i : -1;
    }

    void move(float dt, float toX, float toY) {
        float t = 1F - (float) Math.pow(.0009F, dt);
        lampX += (toX - lampX) * t;
        lampY += (toY - lampY) * t;
    }

    static String styleName(String style) {
        Archetypes.Loaded st = Archetypes.get(style);
        return st == null ? style : st.def().name();
    }
}
