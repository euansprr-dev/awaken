package dev.awaken.powers.client.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.awaken.powers.Fx;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxPool;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.PowerFxParticles;
import dev.awaken.powers.ladder.PowerLadder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.awaken.powers.client.fx.FxWorld.R;
import static dev.awaken.powers.client.fx.FxWorld.alpha;

/**
 * The ladder's looks (SPEC 34.5): every FX event carries its power and level, and after the power's own recipe has built,
 * the look layers of each step up to that level are drawn on top, riding the event kinds the table names. Level 5 also
 * draws the power's every effect in its family's awakened palette ({@link #restyle}). Seeds come from the event, so every
 * viewer sees the same layers; counts go through the budget like every recipe.
 */
final class LadderFx {
    private LadderFx() {}

    static final int HELIX = 1, ORBIT = 2, SIGIL = 3, FISSURE = 4, RINGS = 5, BURST = 6, COLUMN = 7, FORKS = 8, DOME = 9, TRAIL = 10,
            BLOOM = 11, GHOST = 12, GROW = 13;
    private static final String[] TYPES = {"", "helix", "orbit", "sigil", "fissure", "rings", "burst", "column", "forks", "dome", "trail",
            "bloom", "ghost", "grow"};
    static final int AT_ORIGIN = 0, AT_END = 1, AT_GROUND = 2, AT_TARGET = 3, AT_HAND = 4, AT_OFFHAND = 5, AT_BODY = 6, AT_FEET = 7,
            AT_HEAD = 8, AT_BACK = 9;
    private static final String[] PLACES = {"origin", "end", "ground", "target", "hand", "offhand", "body", "feet", "head", "back"};
    private static final int[] ANCHOR = {-1, -1, -1, Anchors.BODY, Anchors.HAND, Anchors.OFFHAND, Anchors.BODY, Anchors.FEET, Anchors.HEAD,
            Anchors.BACK};

    /** One look layer, parsed once. Fields mean what the python helper of its type says (power_ladder_table.py). */
    static final class Layer {
        int type, tag;
        long on;
        int at, col, n, sprite = -1, life, delay, gap, every;
        float a, b, c, d, e;
        boolean dir, alongDir, aimFace;
    }

    /** [power index][level 0..5] -> layers of that step. */
    private static Layer[][][] TABLE;
    /** Per caster and layer: the tick a throttled layer last fired (a held beam re-sends its event five times a second). */
    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap LAST = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    /** Per caster: the tick its ladder sound layer last played (one per cast, however many sounds the cast makes). */
    private static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap TIER_LAST = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

    /**
     * The ladder's sound layer (SPEC 34.5): with the first sound of a level-3+ cast, the family's tier layer plays under it (level 5
     * its awakened one), on the frame the power's own sound does, from the event's level bits (no packet of its own).
     */
    static void tierSound(FxEvent e, net.minecraft.client.multiplayer.ClientLevel level) {
        int lvl = Fx.levelOf(e);
        if (lvl < 3 || e.power() <= 0 || e.caster() < 0 || e.has(FxEvent.RIDER)) return;
        int last = TIER_LAST.getOrDefault(e.caster(), Integer.MIN_VALUE);
        if (FxWorld.ticks - last < 12 && FxWorld.ticks >= last) return;
        PowerType type = PowerRegistry.byIndex(e.power() - 1);
        if (type == null) return;
        if (TIER_LAST.size() > 256) TIER_LAST.clear();
        TIER_LAST.put(e.caster(), FxWorld.ticks);
        level.playLocalSound(e.x(), e.y(), e.z(), dev.awaken.powers.PowerSounds.tier(type.family, lvl).value(), net.minecraft.sounds.SoundSource.PLAYERS,
                Math.min(1f, e.scale()) * 0.9f, 1f, false);
    }

    private static Layer[][][] table() {
        if (TABLE != null) return TABLE;
        List<PowerType> all = new ArrayList<>(PowerRegistry.all());
        Layer[][][] t = new Layer[all.size()][6][];
        int tag = 1;
        for (int i = 0; i < all.size(); i++) {
            PowerType type = all.get(i);
            for (int lv = 0; lv < 6; lv++) {
                List<JsonObject> looks = lv >= 2 ? PowerLadder.looks(type.path, lv) : List.of();
                Layer[] ls = new Layer[looks.size()];
                for (int k = 0; k < ls.length; k++) {
                    ls[k] = parse(looks.get(k));
                    ls[k].tag = tag++;
                }
                t[i][lv] = ls;
            }
        }
        return TABLE = t;
    }

    private static Layer parse(JsonObject o) {
        Layer l = new Layer();
        String k = o.get("k").getAsString();
        for (int i = 1; i < TYPES.length; i++) if (TYPES[i].equals(k)) l.type = i;
        for (JsonElement on : o.getAsJsonArray("on")) l.on |= 1L << FxKind.valueOf(on.getAsString()).ordinal();
        String at = s(o, "at", "origin");
        for (int i = 0; i < PLACES.length; i++) if (PLACES[i].equals(at)) l.at = i;
        l.col = switch (s(o, "col", "mid")) {
            case "core" -> 0;
            case "edge" -> 2;
            case "accent" -> 3;
            default -> 1;
        };
        String sprite = s(o, "sprite", "");
        if (!sprite.isEmpty()) {
            for (PowerFxParticles.Sprite sp : PowerFxParticles.SPRITES) if (sp.name().equals(sprite)) l.sprite = sp.index();
        }
        l.life = i(o, "life");
        l.delay = i(o, "delay");
        l.every = i(o, "every");
        switch (l.type) {
            case HELIX -> {
                l.n = i(o, "strands");
                l.a = f(o, "turns");
                l.b = f(o, "radius");
                l.c = f(o, "width");
                l.d = f(o, "spin");
            }
            case ORBIT -> {
                l.n = i(o, "n");
                l.a = f(o, "r0");
                l.b = f(o, "r1");
                l.c = f(o, "spin");
                l.d = f(o, "size");
                l.e = f(o, "tilt");
            }
            case SIGIL -> {
                l.a = f(o, "size");
                l.c = f(o, "spin");
                l.aimFace = "aim".equals(s(o, "face", "aim"));
            }
            case FISSURE -> {
                l.n = i(o, "n");
                l.a = f(o, "length");
                l.b = f(o, "width");
                l.life = i(o, "cool");
                l.alongDir = "dir".equals(s(o, "along", "radial"));
            }
            case RINGS -> {
                l.n = i(o, "n");
                l.gap = i(o, "gap");
                l.a = f(o, "r0");
                l.b = f(o, "r1");
                l.c = f(o, "width");
                l.dir = "dir".equals(s(o, "face", "up"));
            }
            case BURST -> {
                l.n = i(o, "n");
                l.a = f(o, "speed");
                l.b = f(o, "size");
                l.c = f(o, "grav");
            }
            case COLUMN -> {
                l.a = f(o, "height");
                l.b = f(o, "width");
            }
            case FORKS -> {
                l.n = i(o, "n");
                l.a = f(o, "reach");
            }
            case DOME -> l.a = f(o, "radius");
            case TRAIL -> l.a = f(o, "width");
            case BLOOM -> l.a = f(o, "size");
            case GHOST -> l.a = f(o, "scale");
            case GROW -> l.a = f(o, "scale");
            default -> {
            }
        }
        return l;
    }

    private static String s(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? def : e.getAsString().toLowerCase(Locale.ROOT);
    }

    private static float f(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0f : e.getAsFloat();
    }

    private static int i(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : Math.round(e.getAsFloat());
    }

    // ---- the event ----------------------------------------------------------------------------------------------------

    /** A level-5 power's events draw in its family's awakened palette; an explicit colour leans halfway toward it. */
    static FxEvent restyle(FxEvent e) {
        if (e.power() <= 0 || Fx.levelOf(e) < 5 || e.kind() == FxKind.SOUND || e.kind() == FxKind.POSE) return e;
        FxStyle a = e.style().awakened();
        if (a == e.style()) return e;
        int colour = e.colour() == 0 ? 0 : FxWorld.mix(e.colour() | 0xFF000000, a.mid, 0.5f);
        return new FxEvent(e.kind(), a, e.caster(), e.target(), e.x(), e.y(), e.z(), e.dx(), e.dy(), e.dz(), colour, e.scale(), e.aux(), e.seed(),
                e.ticks(), e.flags(), e.power());
    }

    /** Before the power's own recipe: a new build serial, so {@link #decorate} finds the shapes this event made. */
    static void begin() {
        FxWorld.serial++;
    }

    /** After the power's own recipe: the step layers of every level up to the event's. */
    static void decorate(FxEvent e) {
        int power = e.power() - 1;
        int level = Fx.levelOf(e);
        if (power < 0 || level < 2 || e.kind() == FxKind.SOUND || e.kind() == FxKind.POSE || e.has(FxEvent.RIDER)) return;
        Layer[][][] t = table();
        if (power >= t.length) return;
        long bit = 1L << e.kind().ordinal();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity caster = e.caster() >= 0 ? mc.level.getEntity(e.caster()) : null;
        PowerType type = PowerRegistry.byIndex(power);
        int family = type == null ? 0 : type.family.ordinal();
        if (!FxWorld.begin(e.x(), e.y(), e.z(), e.seed())) return;
        for (int lv = 2; lv <= Math.min(5, level); lv++) {
            for (Layer l : t[power][lv]) {
                if ((l.on & bit) == 0) continue;
                if (l.every > 0 && caster != null) {
                    long key = ((long) caster.getId() << 20) ^ l.tag;
                    int last = LAST.getOrDefault(key, Integer.MIN_VALUE);
                    if (FxWorld.ticks - last < l.every) continue;
                    LAST.put(key, FxWorld.ticks);
                }
                R.seed(e.seed() * 31 + l.tag);
                try {
                    apply(l, e, caster, family);
                } catch (RuntimeException ex) {
                    dev.awaken.Awaken.LOG.debug("Ladder look failed", ex);
                }
            }
        }
        if (LAST.size() > 512) LAST.clear();
    }

    private static final double[] P = new double[3];

    /** Where a layer sits: P = the point; returns the entity it follows (or null for a fixed point). */
    private static @Nullable Entity place(Layer l, FxEvent e, @Nullable Entity caster) {
        switch (l.at) {
            case AT_END -> {
                P[0] = e.x() + e.dx();
                P[1] = e.y() + e.dy();
                P[2] = e.z() + e.dz();
                return null;
            }
            case AT_GROUND -> {
                P[0] = e.x();
                P[1] = FxWorld.groundY(e.x(), e.y() + 0.5, e.z()) + 0.04;
                P[2] = e.z();
                return null;
            }
            case AT_TARGET -> {
                Entity t = e.target() >= 0 && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(e.target()) : null;
                if (t != null) {
                    Anchors.of(t, Anchors.BODY, 1f, P);
                    return t;
                }
            }
            case AT_ORIGIN -> {
            }
            default -> {
                if (caster != null) {
                    Anchors.of(caster, ANCHOR[l.at], 1f, P);
                    return caster;
                }
            }
        }
        P[0] = e.x();
        P[1] = e.y();
        P[2] = e.z();
        return null;
    }

    private static int colour(FxStyle st, int slot) {
        return switch (slot) {
            case 0 -> st.core;
            case 2 -> st.edge;
            case 3 -> st.accent;
            default -> st.mid;
        };
    }

    private static int lifeOf(Layer l, FxEvent e, int def) {
        return l.life > 0 ? l.life : e.ticks() > 0 ? e.ticks() + 4 : def;
    }

    private static void apply(Layer l, FxEvent e, @Nullable Entity caster, int family) {
        FxStyle st = e.style();
        int col = colour(st, l.col);
        switch (l.type) {
            case HELIX -> helix(l, e, caster, col);
            case GROW -> grow(l);
            case GHOST -> later(l, e, l.delay);
            default -> {
                if (l.delay > 0 && l.type != RINGS && l.type != COLUMN && l.type != DOME) later(l, e, l.delay);
                else now(l, e, caster, family, st, col);
            }
        }
    }

    /** Layers that make something now (or a shape that waits: rings, columns and domes take their delay as a negative age). */
    private static void now(Layer l, FxEvent e, @Nullable Entity caster, int family, FxStyle st, int col) {
        Entity on = place(l, e, caster);
        double x = P[0], y = P[1], z = P[2];
        switch (l.type) {
            case ORBIT -> {
                if (refresh(on, Shape.ORBIT, l, lifeOf(l, e, 20))) return;
                Shape s = FxWorld.shape(Shape.ORBIT, lifeOf(l, e, 20));
                if (s == null) return;
                pos(s, x, y, z, on, l);
                s.sprite = l.sprite >= 0 ? l.sprite : PowerFxParticles.MOTE.index();
                s.emitParam = Math.max(1, FxWorld.n(Math.max(1, l.n)));
                s.a = l.a;
                s.b = l.b;
                s.c = l.c * Mth.TWO_PI;
                s.d = l.d;
                s.c0 = alpha(col, 0.95f);
                s.c1 = alpha(st.core, 0.9f);
                s.seed = e.seed();
                s.fadeIn = 3;
                s.fadeOut = 4;
                float tilt = l.e;
                float yaw = caster != null ? caster.getYRot() * Mth.DEG_TO_RAD : 0;
                s.nx = -Mth.sin(yaw) * Mth.sin(tilt);
                s.ny = Mth.cos(tilt);
                s.nz = Mth.cos(yaw) * Mth.sin(tilt);
                s.ladTag = l.tag;
            }
            case SIGIL -> {
                int life = lifeOf(l, e, 24);
                if (refresh(on, Shape.SIGIL, l, life)) return;
                Shape s = FxWorld.shape(Shape.SIGIL, life);
                if (s == null) return;
                pos(s, x, y, z, on, l);
                s.sprite = PowerFxParticles.SIGIL.index();
                s.frame = family;
                s.a = l.a * 0.55f;
                s.b = l.a;
                s.c = l.c * Mth.TWO_PI;
                s.c0 = alpha(col, 0.95f);
                s.c1 = alpha(st.core, 0.85f);
                s.flags |= Shape.HOLD | Shape.PULSE;
                s.fadeIn = 3;
                s.fadeOut = 6;
                if (l.aimFace) {
                    if (on != null && on == caster) {
                        s.flags |= Shape.LOOK;
                        s.d = l.at == AT_HAND || l.at == AT_OFFHAND ? 0.28f : 0.6f;
                    } else {
                        float dl = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
                        if (dl > 1e-3f) {
                            s.nx = e.dx() / dl;
                            s.ny = e.dy() / dl;
                            s.nz = e.dz() / dl;
                        }
                    }
                } else if (l.at != AT_GROUND && on == null) {
                    s.y = FxWorld.groundY(x, y + 0.5, z) + 0.04;
                }
                s.ladTag = l.tag;
            }
            case FISSURE -> fissure(l, e, x, y, z, st);
            case RINGS -> {
                float nx = 0, ny = 1, nz = 0;
                if (l.dir) {
                    float dl = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
                    if (dl > 1e-3f) {
                        nx = e.dx() / dl;
                        ny = e.dy() / dl;
                        nz = e.dz() / dl;
                    }
                }
                int life = l.life > 0 ? l.life : 10;
                for (int i = 0; i < Math.max(1, l.n); i++) {
                    Shape r = FxRecipes.ring(x, y + (l.dir ? 0 : 0.08), z, l.a, l.b, l.c, l.c * 0.3f, life, alpha(i == 0 ? col : st.mid, 0.95f),
                            alpha(st.edge, 0), nx, ny, nz);
                    if (r == null) break;
                    r.age = -(l.delay + i * Math.max(1, l.gap));
                    if (on != null) FxRecipes.attach(r, on, ANCHOR[l.at]);
                    r.ladTag = l.tag;
                }
            }
            case BURST -> burst(l, x, y, z, st, col);
            case COLUMN -> {
                Shape c = FxWorld.shape(Shape.COLUMN, l.life > 0 ? l.life : 14);
                if (c == null) return;
                c.x = x;
                c.y = y;
                c.z = z;
                c.a = l.a;
                c.b = l.b;
                c.c0 = alpha(col, 0.9f);
                c.c1 = alpha(st.edge, 0.2f);
                c.fadeIn = 2;
                c.fadeOut = 6;
                c.seed = e.seed();
                c.age = -l.delay;
            }
            case FORKS -> {
                int life = l.life > 0 ? l.life : 6;
                for (int i = 0; i < Math.max(1, l.n); i++) {
                    double ang = R.f(0, Mth.TWO_PI), up = R.f(-0.2f, 0.7f);
                    double r = l.a * R.f(0.6f, 1f);
                    FxRecipes.arc(x, y, z, x + Math.cos(ang) * r, y + up * r, z + Math.sin(ang) * r, st, life, 0.035f);
                }
            }
            case DOME -> {
                Shape d = FxWorld.shape(Shape.SPHERE, l.life > 0 ? l.life : 14);
                if (d == null) return;
                d.x = x;
                d.y = l.at == AT_GROUND ? y : FxWorld.groundY(x, y + 0.5, z);
                d.z = z;
                d.a = l.a * 0.35f;
                d.b = l.a;
                d.c = 3f;
                d.d = 0.1f;
                d.c0 = alpha(col, 0.8f);
                d.c1 = alpha(st.edge, 0.1f);
                d.flags |= Shape.HEMI | Shape.HOLD | Shape.RIM;
                d.fadeIn = 2;
                d.fadeOut = 6;
                d.age = -l.delay;
                if (on != null) FxRecipes.attach(d, on, ANCHOR[l.at]);
            }
            case TRAIL -> {
                // a trail on the struck body needs one (a punch into the air has none)
                Entity follow = on != null ? on : l.at == AT_TARGET ? null : caster;
                if (follow == null) return;
                int life = lifeOf(l, e, 14);
                if (refresh(follow, Shape.TRAIL, l, life)) return;
                Shape t = FxRecipes.attach(FxWorld.shape(Shape.TRAIL, life), follow, l.at >= AT_HAND ? ANCHOR[l.at] : Anchors.BODY);
                if (t == null) return;
                t.a = l.a;
                t.c0 = alpha(col, 0.85f);
                t.c1 = alpha(st.edge, 0);
                t.fadeOut = 8;
                t.pushHistory(t.x, t.y, t.z);
                t.ladTag = l.tag;
            }
            case BLOOM -> {
                FxRecipes.flare(x, y, z, l.a, st, 6);
                FxWorld.sprite(PowerFxParticles.HALO, x, y, z, 0, 0, 0, 10, l.a * 3.2f, l.a * 1.2f, alpha(col, 0.85f), alpha(st.edge, 0), FxPool.ADD);
            }
            default -> {
            }
        }
    }

    /** A layer on a caster that is already showing (a held power's events repeat): it lives on instead of stacking. */
    private static boolean refresh(@Nullable Entity on, int type, Layer l, int life) {
        if (on == null) return false;
        for (Shape s : FxWorld.SHAPES) {
            if (s.live && s.ladTag == l.tag && s.type == type && s.follow == on.getId() && s.age < s.life - s.fadeOut) {
                s.life = Math.max(s.life, s.age + life);
                return true;
            }
        }
        return false;
    }

    private static void pos(Shape s, double x, double y, double z, @Nullable Entity on, Layer l) {
        s.x = x;
        s.y = y;
        s.z = z;
        if (on != null) FxRecipes.attach(s, on, l.at == AT_TARGET ? Anchors.BODY : ANCHOR[l.at]);
    }

    /** Strands round the beams this event drew (or the held beam it refreshed). */
    private static void helix(Layer l, FxEvent e, @Nullable Entity caster, int col) {
        boolean any = false;
        for (Shape s : FxWorld.SHAPES) {
            if (s.live && s.serial == FxWorld.serial && s.type == Shape.BEAM) {
                strands(s, l, col);
                any = true;
            }
        }
        if (!any && caster != null) {
            Shape held = FxWorld.findAttached(caster.getId(), Shape.BEAM, FxRecipes.E_BEAM);
            if (held != null) strands(held, l, col);
        }
    }

    private static void strands(Shape s, Layer l, int col) {
        // a later step's helix replaces an earlier one (Lance's double strand over Helix's single)
        if (l.n < s.helix) return;
        s.helix = Math.max(1, l.n);
        s.hTurns = l.a;
        s.hRadius = l.b;
        s.hWidth = l.c;
        s.hSpin = l.d;
        s.hCol = alpha(col, 0.9f);
    }

    /** The shapes this event drew, k times bigger. */
    private static void grow(Layer l) {
        float k = l.a <= 0 ? 1f : l.a;
        for (Shape s : FxWorld.SHAPES) {
            if (!s.live || s.serial != FxWorld.serial || s.ladTag != 0) continue;
            switch (s.type) {
                case Shape.RING, Shape.SPHERE, Shape.ARC, Shape.GLYPH, Shape.DISC, Shape.SLASH -> {
                    s.a *= k;
                    s.b *= k;
                }
                case Shape.COLUMN -> {
                    s.a *= k;
                    s.b *= k;
                }
                case Shape.BEAM -> s.a *= k;
                default -> {
                }
            }
        }
    }

    private static void fissure(Layer l, FxEvent e, double x, double y, double z, FxStyle st) {
        int n = Math.max(1, Math.min(6, l.n));
        int per = Math.max(3, Math.min(6, 20 / n));
        Shape s = FxWorld.shape(Shape.FISSURE, l.life > 0 ? l.life : 40);
        if (s == null) return;
        s.x = x;
        s.y = y;
        s.z = z;
        double base = Math.atan2(e.dz(), e.dx());
        boolean dir = l.alongDir && (e.dx() != 0 || e.dz() != 0);
        double gy0 = FxWorld.groundY(x, y + 0.5, z);
        int idx = 0;
        for (int k = 0; k < n; k++) {
            double ang = dir ? base + (k == 0 ? 0 : (k % 2 == 0 ? 1 : -1) * 0.35 * ((k + 1) / 2)) : k * Math.PI * 2 / n + R.f(-0.3f, 0.3f);
            double px = x, pz = z;
            double seg = l.a / (per - 1);
            for (int i = 0; i < per; i++) {
                double gy = FxWorld.groundY(px, gy0 + 0.6, pz);
                if (Math.abs(gy - gy0) > 1.6) gy = gy0;
                s.hx[idx] = px;
                s.hy[idx] = gy + 0.035;
                s.hz[idx] = pz;
                idx++;
                ang += R.f(-0.45f, 0.45f);
                px += Math.cos(ang) * seg;
                pz += Math.sin(ang) * seg;
            }
        }
        s.hn = idx;
        s.emitParam = per;
        s.a = l.b;
        s.c0 = 0xFFFFFFFF & (st.core | 0xFF000000);
        s.c1 = st.accent == st.core ? st.mid : FxWorld.mix(st.mid, st.accent, 0.4f);
        s.c2 = alpha(st.edge, 0.7f);
        s.fadeIn = 0;
        s.fadeOut = 12;
        s.ladTag = l.tag;
    }

    private static void burst(Layer l, double x, double y, double z, FxStyle st, int col) {
        PowerFxParticles.Sprite sp = l.sprite >= 0 ? PowerFxParticles.SPRITES.get(l.sprite) : PowerFxParticles.SPARK;
        int count = FxWorld.n(Math.max(1, l.n));
        int life = l.life > 0 ? l.life : 14;
        boolean stretch = sp == PowerFxParticles.SPARK || sp == PowerFxParticles.BOLT;
        boolean alphaSprite = !sp.additive();
        for (int i = 0; i < count; i++) {
            double vx = R.s(), vy = R.s() * 0.6 + 0.3, vz = R.s();
            double len = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
            float sp0 = l.a * R.f(0.5f, 1.1f);
            int c0 = alphaSprite ? 0xFFFFFFFF : FxWorld.mix(st.core, col, 0.35f);
            int c1 = alphaSprite ? 0x00FFFFFF : alpha(col, 0);
            int flags = alphaSprite ? FxPool.LIT : FxPool.ADD | (stretch ? FxPool.STRETCH : 0);
            int p = FxWorld.sprite(sp, x, y, z, vx / len * sp0, vy / len * sp0, vz / len * sp0, life + R.i(5), l.b * R.f(0.8f, 1.2f), l.b * 0.4f,
                    c0, c1, flags);
            FxWorld.drag(p, 0.9f);
            FxWorld.grav(p, l.c);
            if (!stretch) FxWorld.spin(p, R.f(0, 6.28f), R.f(-0.25f, 0.25f));
        }
    }

    // ---- later ----------------------------------------------------------------------------------------------------------

    private static final int QUEUE = 48;
    private static final FxEvent[] Q_EVENT = new FxEvent[QUEUE];
    private static final Layer[] Q_LAYER = new Layer[QUEUE];
    private static final int[] Q_DUE = new int[QUEUE];
    private static int queued;

    private static void later(Layer l, FxEvent e, int delay) {
        if (queued >= QUEUE) return;
        Q_EVENT[queued] = e;
        Q_LAYER[queued] = l;
        Q_DUE[queued] = FxWorld.ticks + Math.max(1, delay);
        queued++;
    }

    /** Per client tick: delayed layers whose moment came. */
    static void tick() {
        if (queued == 0) return;
        Minecraft mc = Minecraft.getInstance();
        int i = 0;
        while (i < queued) {
            if (Q_DUE[i] > FxWorld.ticks) {
                i++;
                continue;
            }
            FxEvent e = Q_EVENT[i];
            Layer l = Q_LAYER[i];
            queued--;
            Q_EVENT[i] = Q_EVENT[queued];
            Q_LAYER[i] = Q_LAYER[queued];
            Q_DUE[i] = Q_DUE[queued];
            Q_EVENT[queued] = null;
            Q_LAYER[queued] = null;
            if (mc.level == null) continue;
            try {
                if (l.type == GHOST) {
                    // the event's own effect again, smaller: the second impact you see land (never its screen beat)
                    FxEvent g = new FxEvent(e.kind(), e.style(), e.caster(), e.target(), e.x(), e.y(), e.z(), e.dx(), e.dy(), e.dz(), e.colour(),
                            e.scale() * (l.a <= 0 ? 0.6f : l.a), e.aux(), e.seed() + 17, e.ticks(), (e.flags() | FxEvent.QUIET) & ~FxEvent.ULT & 0xFFFF, 0);
                    FxRecipes.build(g);
                } else if (FxWorld.begin(e.x(), e.y(), e.z(), e.seed())) {
                    Entity caster = e.caster() >= 0 ? mc.level.getEntity(e.caster()) : null;
                    PowerType type = PowerRegistry.byIndex(e.power() - 1);
                    R.seed(e.seed() * 31 + l.tag);
                    now(l, e, caster, type == null ? 0 : type.family.ordinal(), e.style(), colour(e.style(), l.col));
                }
            } catch (RuntimeException ex) {
                dev.awaken.Awaken.LOG.debug("Delayed ladder look failed", ex);
            }
        }
    }

    static void reset() {
        for (int i = 0; i < queued; i++) {
            Q_EVENT[i] = null;
            Q_LAYER[i] = null;
        }
        queued = 0;
        LAST.clear();
        TIER_LAST.clear();
    }
}
