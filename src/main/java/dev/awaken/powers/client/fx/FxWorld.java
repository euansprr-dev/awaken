package dev.awaken.powers.client.fx;

import dev.awaken.powers.fx.FxBudget;
import dev.awaken.powers.fx.FxPool;
import dev.awaken.powers.fx.FxRand;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.PowerFxParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The client's live effects: the sprite pool, the mesh shapes, and the helpers recipes use to spawn them. Everything
 * is preallocated; building or drawing an effect allocates nothing. Ticked once per client tick, anchors refreshed
 * once per frame.
 */
final class FxWorld {
    private FxWorld() {}

    static final FxPool POOL = new FxPool(FxBudget.spriteCap(FxBudget.Level.FULL));
    static final Shape[] SHAPES = new Shape[FxBudget.shapeCap(FxBudget.Level.FULL)];
    static final FxRand R = new FxRand();
    private static final double[] TMP = new double[3];
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();

    static {
        for (int i = 0; i < SHAPES.length; i++) SHAPES[i] = new Shape();
    }

    /** Density factor for the effect being built (setting x particle option x distance). */
    static float k = 1f;
    static FxBudget.Level level = FxBudget.Level.FULL;
    static int shapeCap = SHAPES.length;
    static int liveShapes;
    static int ticks;
    /** Counts effect builds; every shape remembers the build that made it (the ladder decorates its event's shapes). */
    static int serial;

    // ---- per effect -------------------------------------------------------------------------------------------------

    /** Starts building an effect at a point; false = too far away to build at all. */
    static boolean begin(double x, double y, double z, int seed) {
        Minecraft mc = Minecraft.getInstance();
        level = PowerFxConfig.level();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        R.seed(seed);
        if (dist > FxBudget.CULL_DISTANCE) return false;
        k = FxBudget.factor(level, PowerFxConfig.particleStatus(), dist);
        return true;
    }

    /** Scaled sprite count for a layer that wants {@code base} at full detail. */
    static int n(int base) {
        return FxBudget.count(base, k);
    }

    static boolean minimal() {
        return level == FxBudget.Level.MINIMAL;
    }

    // ---- sprites ----------------------------------------------------------------------------------------------------

    static int sprite(PowerFxParticles.Sprite s, double x, double y, double z, double vx, double vy, double vz, int life,
                      float size0, float size1, int c0, int c1, int flags) {
        int f = flags;
        if ((f & (FxPool.ADD | FxPool.LIT | FxPool.BRIGHT)) == 0) f |= s.additive() ? FxPool.ADD : FxPool.LIT;
        int i = POOL.add(x, y, z, (float) vx, (float) vy, (float) vz, life, size0, size1, c0, c1, s.index(), f);
        if (s.frames() > 1 && (f & FxPool.ANIM) == 0) POOL.frame[i] = R.i(s.frames());
        if ((f & FxPool.LIT) != 0) POOL.light[i] = light(x, y, z);
        return i;
    }

    static void drag(int i, float d) {
        POOL.drag[i] = d;
    }

    static void grav(int i, float g) {
        POOL.grav[i] = g;
    }

    static void spin(int i, float rot, float rotV) {
        POOL.rot[i] = POOL.prot[i] = rot;
        POOL.rotV[i] = rotV;
    }

    static void floor(int i, float y) {
        POOL.floor[i] = y;
    }

    static void stretch(int i, float s) {
        POOL.stretch[i] = s;
    }

    // ---- shapes -----------------------------------------------------------------------------------------------------

    /** A fresh shape, or null when the cap is reached (the effect simply has one layer fewer). */
    static @Nullable Shape shape(int type, int life) {
        if (liveShapes >= shapeCap) return null;
        for (Shape s : SHAPES) {
            if (!s.live) {
                s.reset();
                s.live = true;
                s.type = type;
                s.life = Math.max(1, life);
                s.serial = serial;
                liveShapes++;
                return s;
            }
        }
        return null;
    }

    /** Ends every shape attached to an entity with the given emitter (a charge being released). */
    static void endAttached(int entityId, int emitter) {
        for (Shape s : SHAPES) {
            if (s.live && s.follow == entityId && s.emitter == emitter) s.life = Math.min(s.life, s.age + s.fadeOut);
        }
    }

    static @Nullable Shape findAttached(int entityId, int type, int emitter) {
        for (Shape s : SHAPES) {
            if (s.live && s.follow == entityId && s.type == type && s.emitter == emitter && s.age < s.life - s.fadeOut) return s;
        }
        return null;
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null) return;
        ticks++;
        FxBudget.Level now = PowerFxConfig.level();
        if (now != level || POOL.cap() != FxBudget.spriteCap(now)) {
            level = now;
            POOL.setCap(FxBudget.spriteCap(now));
            shapeCap = FxBudget.shapeCap(now);
        }
        POOL.tick();
        if (ticks % 4 == 0) {
            for (int i = 0; i < POOL.live(); i++) {
                // full-bright lit sprites (ink rims) keep their light; the rest follow the world's
                if ((POOL.flags[i] & FxPool.LIT) != 0 && POOL.light[i] != LightCoordsUtil.FULL_BRIGHT) {
                    POOL.light[i] = light(POOL.x[i], POOL.y[i], POOL.z[i]);
                }
            }
        }
        int live = 0;
        for (Shape s : SHAPES) {
            if (!s.live) continue;
            if (++s.age >= s.life) {
                s.live = false;
                continue;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            if (s.follow >= 0) {
                Entity e = lvl.getEntity(s.follow);
                if (e == null || e.isRemoved()) {
                    s.follow = -1;
                    s.life = Math.min(s.life, s.age + s.fadeOut);
                } else {
                    Anchors.of(e, s.anchor, 1f, TMP);
                    s.x = TMP[0];
                    s.y = TMP[1];
                    s.z = TMP[2];
                }
            }
            // a delayed ladder layer (age below 0) waits: no trail history, no emitter yet
            if (s.age >= 0) {
                if (s.type == Shape.TRAIL) s.pushHistory(s.x, s.y, s.z);
                if (s.emitter != 0) FxRecipes.emit(s);
            }
            live++;
        }
        liveShapes = live;
        FxDebris.tick(lvl);
        FxAfterimages.tick();
    }

    /** Per frame: interpolated positions of every anchored shape. */
    static void extract(float pt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null || liveShapes == 0) return;
        for (Shape s : SHAPES) {
            if (!s.live) continue;
            if (s.follow >= 0) {
                Entity e = lvl.getEntity(s.follow);
                if (e != null) {
                    Anchors.of(e, s.anchor, pt, TMP);
                    s.rx = TMP[0];
                    s.ry = TMP[1];
                    s.rz = TMP[2];
                } else {
                    s.rx = s.x;
                    s.ry = s.y;
                    s.rz = s.z;
                }
            } else {
                s.rx = s.x + s.vx * pt;
                s.ry = s.y + s.vy * pt;
                s.rz = s.z + s.vz * pt;
            }
            if (s.target >= 0) {
                Entity t = lvl.getEntity(s.target);
                if (t != null) {
                    Anchors.of(t, Anchors.BODY, pt, TMP);
                    s.ex = TMP[0];
                    s.ey = TMP[1];
                    s.ez = TMP[2];
                }
            }
            if ((s.flags & Shape.AIM) != 0 && mc.player != null && s.follow == mc.player.getId()) FxBeams.aim(s, mc, pt);
            if ((s.flags & Shape.LOOK) != 0 && s.follow >= 0) {
                Entity e = lvl.getEntity(s.follow);
                if (e != null) {
                    float yaw = e.getViewYRot(pt) * net.minecraft.util.Mth.DEG_TO_RAD, pitch = e.getViewXRot(pt) * net.minecraft.util.Mth.DEG_TO_RAD;
                    float cp = net.minecraft.util.Mth.cos(pitch);
                    s.nx = -net.minecraft.util.Mth.sin(yaw) * cp;
                    s.ny = -net.minecraft.util.Mth.sin(pitch);
                    s.nz = net.minecraft.util.Mth.cos(yaw) * cp;
                }
            }
            s.rex = s.ex;
            s.rey = s.ey;
            s.rez = s.ez;
        }
    }

    static void reset() {
        POOL.clear();
        for (Shape s : SHAPES) s.live = false;
        liveShapes = 0;
        FxDebris.clear();
        FxAfterimages.clear();
    }

    // ---- small helpers ----------------------------------------------------------------------------------------------

    static int light(double x, double y, double z) {
        ClientLevel lvl = Minecraft.getInstance().level;
        if (lvl == null) return LightCoordsUtil.FULL_BRIGHT;
        MUT.set(x, y, z);
        return lvl.hasChunkAt(MUT) ? LightCoordsUtil.getLightCoords(lvl, MUT) : LightCoordsUtil.FULL_SKY;
    }

    /** ARGB with a new alpha (0..1). */
    static int alpha(int argb, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    /** Lerp between two ARGB colours. */
    static int mix(int c0, int c1, float t) {
        if (t <= 0) return c0;
        if (t >= 1) return c1;
        int a = (int) (((c0 >>> 24) & 255) + (((c1 >>> 24) & 255) - ((c0 >>> 24) & 255)) * t);
        int r = (int) (((c0 >> 16) & 255) + (((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * t);
        int g = (int) (((c0 >> 8) & 255) + (((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * t);
        int b = (int) ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** The colour the event asked for, or the style's mid colour. */
    static int midOr(int colour, FxStyle style) {
        return colour != 0 ? (colour | 0xFF000000) : style.mid;
    }

    static double groundY(double x, double y, double z) {
        ClientLevel lvl = Minecraft.getInstance().level;
        if (lvl == null) return y;
        for (int i = 0; i < 6; i++) {
            MUT.set(x, y - i, z);
            if (!lvl.getBlockState(MUT).getCollisionShape(lvl, MUT).isEmpty()) return MUT.getY() + 1.0;
        }
        return y;
    }
}
