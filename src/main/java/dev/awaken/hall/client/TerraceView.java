package dev.awaken.hall.client;

import dev.awaken.Awaken;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.client.ForgeClient;
import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.hall.HallNet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The terraces (SPEC §52): all 29 suits standing in the dark, ranks climbing away. Like the wall, it is lit round your
 * lantern and below your level; what you own or could make now stands lit in the dark as well, and unfound suits are shapes.
 */
final class TerraceView {
    private static final Identifier DARK = Awaken.id("textures/gui/hall/terr_dark.png"), LIT = Awaken.id("textures/gui/hall/terr_lit.png");
    private static final int KEY_DARK = 5, KEY_LIT = 6, KEY_HOVER = 11;
    private static final float LOOKS_Y = 92F;
    private static final int HOT = 0xFFC4E6FF;
    private static final float YAW = -24F, PITCH = -4F;

    private final Component quiet = Stage.blackletter("Every suit you could wear");
    private final Component[] letters = new Component[HallNet.ROWS];

    int filter = WallView.ALL;
    float lampX = 854F, lampY = 620F;
    TerraceLayout.@Nullable Fig hover;
    private Placed[] dark = new Placed[0], lit = dark;
    private long version;
    private ArmourPayloads.@Nullable Offers built;
    private int builtFilter = -1, builtTide = -1;
    private int tide = TerraceLayout.tide(0), ready, tracked;
    private @Nullable Component hoverName;
    private TerraceLayout.@Nullable Fig hoverNamed;
    private boolean hoverHidden;
    private final float[] filterX = new float[WallView.FILTERS.length];
    private float looksRight;
    /** The suit this stand is holding (a stand builds one set at a time, SPEC §44.14), and how many of its pieces stand on it. */
    private @Nullable ArmourSet builtHeld;
    private int builtStood = -1;
    /** The suit under the pointer, drawn again lit and a little larger: its own picture, redrawn only when the pointer moves on. */
    private Placed[] lifted = new Placed[0];
    private TerraceLayout.@Nullable Fig liftedFor;
    private boolean liftedHidden;
    private long liftVersion;

    /** The set the stand you opened this at is holding; null when it is bare, or when you're only looking. */
    static @Nullable ArmourSet held(ArmourPayloads.@Nullable Offers o) {
        if (o == null || !o.stand()) return null;
        var be = ForgeClient.stand(o.pos());
        return be == null ? null : be.held();
    }

    static int stood(ArmourPayloads.@Nullable Offers o) {
        if (o == null || !o.stand()) return 0;
        var be = ForgeClient.stand(o.pos());
        return be == null ? 0 : be.standing();
    }

    TerraceView() {
        for (int i = 0; i < letters.length; i++) letters[i] = Stage.blackletter(ArmsArmoury.TIERS[i]);
    }

    private boolean matches(ArmourPayloads.Offers o, ArmourSet set) {
        return switch (filter) {
            case WallView.READY -> SuitState.count(o, set, SuitState.READY) > 0;
            case WallView.TRACKED -> SuitState.tracked(o, set);
            default -> true;
        };
    }

    private void rebuild(HallNet.Wall w) {
        ArmourPayloads.Offers o = HallClient.suits;
        int you = WallView.youTier(w);
        ArmourSet held = held(o);
        int stood = stood(o);
        if (o == built && filter == builtFilter && you == builtTide && held == builtHeld && stood == builtStood) return;
        built = o;
        builtFilter = filter;
        builtTide = you;
        builtHeld = held;
        builtStood = stood;
        version++;
        tide = TerraceLayout.tide(you);
        TerraceLayout.Fig[] figs = TerraceLayout.figs();
        Placed[] d = new Placed[figs.length], l = new Placed[figs.length];
        int r = 0, t = 0;
        // far rows first in the arrays is not needed: each row stands at its own depth
        for (int i = 0; i < figs.length; i++) {
            TerraceLayout.Fig f = figs[i];
            boolean match = o != null && matches(o, f.set());
            int own = SuitState.count(o, f.set(), SuitState.MADE), can = SuitState.count(o, f.set(), SuitState.READY);
            r += can > 0 ? 1 : 0;
            t += SuitState.tracked(o, f.set()) ? 1 : 0;
            boolean hidden = SuitState.hidden(o, f.set()), standing = (own > 0 || can > 0) && match;
            float depth = (HallNet.ROWS - f.tier()) * 260F;
            d[i] = Placed.suit(f.set(), f.x(), f.cy(), depth, f.scale(), YAW, PITCH, Placed.all(standing ? Placed.FULL : Placed.SHAPE), standing ? .95F : .9F);
            // a stand holding one suit: the others step back until it is taken off
            boolean free = held == null || held == f.set();
            l[i] = Placed.suit(f.set(), f.x(), f.cy(), depth, f.scale(), YAW, PITCH, Placed.all(hidden ? Placed.SHAPE : Placed.FULL), !match ? .13F : free ? 1F : .34F);
        }
        ready = r;
        tracked = t;
        dark = d;
        lit = l;
    }

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
        lift(s, real);
        g.pose().popMatrix();
    }

    /** The hover state: light on the floor under the suit you point at, and the suit itself again, fully lit and a touch larger. */
    private void lift(Stage s, float real) {
        TerraceLayout.Fig h = hover;
        if (h == null) return;
        boolean hidden = SuitState.hidden(built, h.set());
        if (h != liftedFor || hidden != liftedHidden) {
            liftedFor = h;
            liftedHidden = hidden;
            liftVersion++;
            // what you haven't found stays a shape, lit or not
            lifted = new Placed[] {Placed.suit(h.set(), h.x(), h.cy() - h.h() * .02F, 0F, h.scale() * 1.07F, YAW, PITCH, Placed.all(hidden ? Placed.SHAPE : Placed.FULL), 1F)};
        }
        s.glow(h.x(), h.feet() - 2F, h.h() * .52F, h.h() * .13F, 0xFFCF7A, .85F);
        s.g.submitPictureInPictureRenderState(new PicState(KEY_HOVER, 0, 0, Stage.W, Stage.H, real, liftVersion, lifted, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, -1, s.g.pose()));
        s.g.nextStratum();
    }

    void ui(Stage s, HallNet.Wall w, float tabsMouseX, float tabsMouseY) {
        ArmourPayloads.Offers o = HallClient.suits;
        caption(s, o);
        purse(s, o);
        extras(s, o);
        int you = WallView.youTier(w);
        for (int i = 0; i < HallNet.ROWS; i++) {
            float x = TerraceLayout.letterX(i), y = TerraceLayout.letterY(i);
            s.title(letters[i], x, y - 24F, 2F, 0xFF000000 | Stage.RANK[i]);
            s.text(Integer.toString(ArmsArmoury.LEVEL[i]), x + 6F, y + 20F, 2F, i <= you ? Stage.LABEL : Stage.DIM, true);
        }
        s.right("You, level " + w.level(), 1684F, tide - 30F, 3F, Stage.LANTERN);
        // the tracked suit carries a blue mark at its feet
        if (o != null && !o.tracked().isEmpty()) {
            ArmourSet set = ArmourSet.byId(o.tracked());
            if (set != null) {
                TerraceLayout.Fig f = TerraceLayout.of(set);
                s.fill(f.x() - 18F, f.feet() - 2F, f.x() + 18F, f.feet() + 2F, Stage.EDGE);
            }
        }
        // the suit this stand is holding: a lantern mark at its feet and how much of it stands
        ArmourSet held = held(o);
        if (held != null) {
            TerraceLayout.Fig f = TerraceLayout.of(held);
            s.fill(f.x() - 26F, f.feet() + 6F, f.x() + 26F, f.feet() + 10F, Stage.LANTERN);
            s.centre("On this stand · " + stood(o) + " of 4", f.x(), f.feet() + 16F, 2F, Stage.LANTERN);
        }
        // the hover state: the band's corners round the one you're about to click
        TerraceLayout.Fig h = hover;
        if (h != null) s.brackets(h.x() - h.h() * .40F, h.feet() - h.h() * 1.12F, h.x() + h.h() * .40F, h.feet() + 8F, Math.max(14F, h.h() * .12F), 3, HOT);
    }

    private void caption(Stage s, ArmourPayloads.@Nullable Offers o) {
        TerraceLayout.Fig h = hover;
        if (h == null) {
            s.titleCentre(quiet, 854F, 26F, 2F, Stage.TITLE);
            s.centre("Click one to bring it close.", 854F, 92F, 3F, Stage.LABEL);
            return;
        }
        boolean hidden = SuitState.hidden(o, h.set());
        if (hoverNamed != h || hoverName == null || hoverHidden != hidden) {
            hoverNamed = h;
            hoverHidden = hidden;
            hoverName = Stage.blackletter(hidden ? "Something you have not found" : h.set().setName);
        }
        s.titleCentre(hoverName, 854F, 26F, 2F, 0xFF000000 | Stage.RANK[h.tier()]);
        s.centre(words(o, h, hidden), 854F, 92F, 3F, SuitState.count(o, h.set(), SuitState.READY) > 0 ? Stage.GOOD : Stage.LABEL);
    }

    private static String words(ArmourPayloads.@Nullable Offers o, TerraceLayout.Fig h, boolean hidden) {
        String rank = ArmsArmoury.TIERS[h.tier()] + " rank";
        if (hidden) return rank + " · You have not found this pattern";
        String kind = h.set().archetype.label + " · " + rank + " · ";
        if (SuitState.above(o, h.set())) return kind + "Opens at level " + ArmsArmoury.LEVEL[h.tier()];
        int own = SuitState.count(o, h.set(), SuitState.MADE), can = SuitState.count(o, h.set(), SuitState.READY);
        String state = own == 4 ? "You have the whole suit" : own > 0 ? "You have " + own + " of 4" + (can > 0 ? ", " + can + " ready" : "")
                : can > 0 ? can + " of 4 ready to forge" : "None made yet";
        ArmourSet held = held(o);
        if (held == h.set()) state = stood(o) + " of 4 on this stand";
        else if (held != null) state += " · This stand holds " + held.setName;
        return kind + state + (SuitState.tracked(o, h.set()) ? " · Tracked" : "");
    }

    private void purse(Stage s, ArmourPayloads.@Nullable Offers o) {
        ArmourSet held = held(o);
        if (held != null) s.right("This stand holds " + held.setName + " · " + stood(o) + " of 4", 1684F, 18F, 3F, Stage.LANTERN);
        else s.right(o != null && o.stand() ? "At the Fitting Stand" : "Looking, not at a stand", 1684F, 18F, 3F, Stage.EDGE);
        WallView.filters(s, filter, filterX, ready, tracked);
    }

    /** Your looks. (The suit on the stand is taken off from the suit itself now, not from a line up here: Euan, 11 Nov.) */
    private void extras(Stage s, ArmourPayloads.@Nullable Offers o) {
        s.text("Your looks", 30F, LOOKS_Y, 3F, Stage.LABEL);
        looksRight = 30F + s.width("Your looks", 3F) + 16F;
    }

    boolean looksAt(float x, float y) {
        return x >= 22F && x < looksRight && y >= LOOKS_Y - 6F && y < LOOKS_Y + 32F;
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
