package dev.awaken.powers.client.fx;

import dev.awaken.powers.Fx;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxPool;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.powers.fx.PowerFxParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.powers.client.fx.FxWorld.R;
import static dev.awaken.powers.client.fx.FxWorld.alpha;
import static dev.awaken.powers.client.fx.FxWorld.n;
import static dev.awaken.powers.fx.PowerFxParticles.*;

/**
 * Effect recipes: every {@link FxKind} built from sprites, meshes, debris, afterimages and screen feel, in the
 * visual language of docs/modules/power-fx.md. Randomness comes only from the event seed, so all viewers see the
 * same shape; counts come from the budget, so settings and distance thin them.
 */
final class FxRecipes {
    private FxRecipes() {}

    // emitters (per-tick behaviour of a live shape)
    static final int E_CHARGE = 1, E_BEAM = 2, E_CONE = 3, E_GHOSTS = 4, E_TRAIL = 5, E_DOME = 6, E_VORTEX = 7, E_AURA = 8,
            E_FLURRY = 9, E_HEAL = 10, E_FIRE = 11, E_EXHAUST = 12, E_CONSTRUCT = 13, E_PSI = 14, E_METEOR = 15, E_LIFT = 16, E_AURA_CHILD = 17, E_CRACKLE = 18;

    private static final double[] P = new double[3];
    private static final FxKind.Aura[] AURAS = FxKind.Aura.values();
    private static final BlockPos.MutableBlockPos MUT = new BlockPos.MutableBlockPos();

    // ---- entry ------------------------------------------------------------------------------------------------------

    static void build(FxEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (e.kind() == FxKind.SOUND) {
            sound(e, level);
            return;
        }
        if (!FxWorld.begin(e.x(), e.y(), e.z(), e.seed())) return;
        FxStyle st = e.style();
        Entity caster = e.caster() >= 0 ? level.getEntity(e.caster()) : null;
        boolean mine = mc.player != null && e.caster() == mc.player.getId();
        boolean ult = e.has(FxEvent.ULT);
        float lvl = 1 + 0.1f * (Fx.levelOf(e) - 1);
        float big = (ult ? 1.5f : 1f) * lvl;
        switch (e.kind()) {
            case CAST -> cast(e, st, caster);
            case CHARGE -> charge(e, st, caster, mine);
            case CHARGE_END -> chargeEnd(e, st, caster);
            case IMPACT -> impact(e, st, big, mine);
            case SLAM -> slam(e, st, big, mine);
            case RING -> ring(e, st, big);
            case NOVA -> nova(e, st, big, mine);
            case CONE -> cone(e, st, big);
            case BEAM -> beam(e, st, caster, mine);
            case BOLT -> bolt(e, st);
            case LIGHTNING -> lightning(e, st);
            case STRIKE -> strike(e, st, big, mc);
            case DASH -> dash(e, st, caster, mine);
            case BLINK -> blink(e, st, caster, mine);
            case TRAIL -> trail(e, st);
            case AFTERIMAGE -> afterimage(e, st, caster);
            case SMOKE -> smoke(e, st, big);
            case DOME -> dome(e, st, big);
            case VORTEX -> vortex(e, st, big);
            case TETHER -> tether(e, st);
            case AURA -> aura(e, st);
            case PALM -> palm(e, st, big, mine);
            case ECHO -> echo(e, st, big);
            case FLURRY -> flurry(e, st);
            case HEAL -> heal(e, st);
            case FROST -> frost(e, st, big);
            case FIRE_TRAIL -> fireTrail(e, st);
            case EXHAUST -> exhaust(e, st, mine);
            case CONSTRUCT -> construct(e, st);
            case PILLAR -> pillar(e, st);
            case HOLO -> holo(e, st);
            case RAIL -> rail(e, st, caster, mine);
            case SUMMON -> summon(e, st, big);
            case PSI -> psi(e, st, caster);
            case GLYPH -> glyph(e, st, big);
            case METEOR -> meteor(e, st, mine);
            case LIFT -> lift(e, st);
            case ULT -> ultBeat(e, st, mc);
            case EVOLVE -> evolve(e, st, caster);
            case FLASH -> {
                if (mine) FxScreen.flash(st.mid, e.scale(), 6);
            }
            case POSE -> {
                // only the caster's cast profile began (FxClient); nothing is drawn
            }
        }
    }

    /** A power's sound on its release frame: the registry id rides in colour (Fx.sound). */
    private static void sound(FxEvent e, ClientLevel level) {
        net.minecraft.sounds.SoundEvent ev = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.byId(e.colour());
        if (ev != null) level.playLocalSound(e.x(), e.y(), e.z(), ev, net.minecraft.sounds.SoundSource.PLAYERS, e.scale(), e.aux(), false);
    }

    /** Where a caster's beams and orbs start: their palm, or the core for a profile that opens the chest. */
    static int originAnchor(Entity caster) {
        var prof = dev.awaken.powers.fx.CastProfiles.byId(CastAnims.profileOf(caster.getId()));
        return prof != null && prof.core() ? Anchors.CORE : Anchors.HAND;
    }

    // ---- shared layers ----------------------------------------------------------------------------------------------

    /** A shrinking 4-point star over a soft glow: the instant of contact. */
    static void flare(double x, double y, double z, float size, FxStyle st, int life) {
        int f = FxWorld.sprite(FLARE, x, y, z, 0, 0, 0, life, size, size * 0.45f, st.core, alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
        FxWorld.spin(f, R.f(0, 0.8f), 0);
        FxWorld.sprite(HALO, x, y, z, 0, 0, 0, life + 3, size * 2.4f, size * 0.8f, alpha(st.mid, 1f), alpha(st.edge, 0), FxPool.ADD);
    }

    /** Sparks flying out (biased along a direction when given), stretched by their speed. */
    static void sparks(double x, double y, double z, int count, float speed, FxStyle st, int life, float gravity,
                       double bx, double by, double bz, float bias) {
        for (int i = 0; i < count; i++) {
            double vx = R.s(), vy = R.s(), vz = R.s();
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
            vx = vx / l + bx * bias;
            vy = vy / l + by * bias;
            vz = vz / l + bz * bias;
            l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
            float sp = speed * R.f(0.5f, 1.2f);
            // sparks start between white and the family colour: pure white additive vanishes against a bright sky
            int s = FxWorld.sprite(SPARK, x, y, z, vx / l * sp, vy / l * sp, vz / l * sp, life + R.i(4), R.f(0.16f, 0.26f), 0.04f,
                    FxWorld.mix(st.core, st.mid, 0.45f), alpha(st.mid, 0), FxPool.ADD | FxPool.STRETCH);
            FxWorld.drag(s, 0.86f);
            FxWorld.grav(s, gravity);
        }
    }

    static void embers(double x, double y, double z, int count, float spread, FxStyle st, float rise) {
        for (int i = 0; i < count; i++) {
            int s = FxWorld.sprite(EMBER, x + R.s() * spread, y + R.s() * spread * 0.5, z + R.s() * spread, R.s() * 0.04, R.f(0, 0.06f), R.s() * 0.04,
                    14 + R.i(14), R.f(0.12f, 0.22f), 0.04f, st.core, alpha(st.edge, 0), FxPool.ADD | FxPool.ANIM);
            FxWorld.grav(s, -rise);
            FxWorld.drag(s, 0.94f);
        }
    }

    /** Smoke puffs: light dust lit by the world, or dark ink that stays visible at night (shadow, gravity). */
    static void smokePuffs(double x, double y, double z, int count, float spread, float size, FxStyle st, float speed, int life) {
        boolean ink = st.inky();
        for (int i = 0; i < count; i++) {
            double vx = R.s() * speed, vy = R.f(0.01f, speed), vz = R.s() * speed;
            int c0 = ink ? 0xE81A1428 : alpha(FxWorld.mix(st.accent, 0xFFD8D4CC, 0.5f), 0.75f);
            int c1 = ink ? 0x00231A3B : alpha(st.accent, 0);
            double px = x + R.s() * spread, py = y + R.s() * spread * 0.5, pz = z + R.s() * spread;
            int l = life + R.i(life / 2 + 1);
            float s0 = size * R.f(0.6f, 1f), s1 = size * R.f(1.6f, 2.4f), rot = R.f(0, 6.28f), spin = R.s() * 0.05f;
            if (ink && i % 2 == 0) {
                // a violet rim: the same puff a little larger, drawn first (lit pass), so the ink reads at night
                int rim = FxWorld.sprite(SMOKE, px, py, pz, vx, vy, vz, l, s0 * 1.14f, s1 * 1.14f, alpha(st.mid, 0.55f), alpha(st.edge, 0),
                        FxPool.LIT | FxPool.ANIM);
                FxWorld.spin(rim, rot, spin);
                FxWorld.drag(rim, 0.9f);
                FxWorld.POOL.light[rim] = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
            }
            int s = FxWorld.sprite(SMOKE, px, py, pz, vx, vy, vz, l, s0, s1, c0, c1, (ink ? FxPool.BRIGHT : FxPool.LIT) | FxPool.ANIM);
            FxWorld.spin(s, rot, spin);
            FxWorld.drag(s, 0.9f);
        }
        if (ink) {
            // a violet rim of glints so ink reads against dark ground
            for (int i = 0; i < count / 2; i++) {
                int s = FxWorld.sprite(MOTE, x + R.s() * spread * 1.2, y + R.s() * spread * 0.6, z + R.s() * spread * 1.2, R.s() * 0.03, R.f(0, 0.05f),
                        R.s() * 0.03, 12 + R.i(10), 0.16f, 0.04f, st.mid, alpha(st.edge, 0), FxPool.ADD);
                FxWorld.drag(s, 0.92f);
            }
        }
    }

    /** Ground dust billowing out along a circle. */
    static void dustRing(double x, double y, double z, float radius, int count, FxStyle st, float out) {
        float floor = (float) y;
        int c0 = alpha(FxWorld.mix(st.accent, 0xFFCFC6B5, 0.35f), 0.85f), c1 = alpha(st.accent, 0);
        for (int i = 0; i < count; i++) {
            float a = R.f(0, 6.2832f), r = radius * R.f(0.55f, 1.05f);
            double cx = Mth.cos(a), cz = Mth.sin(a);
            int s = FxWorld.sprite(DUST, x + cx * r, y + R.f(0.05f, 0.4f), z + cz * r, cx * out * R.f(0.5f, 1.2f), R.f(0.01f, 0.04f), cz * out * R.f(0.5f, 1.2f),
                    22 + R.i(14), R.f(0.7f, 1.1f), R.f(1.8f, 2.6f), c0, c1, FxPool.LIT | FxPool.ANIM);
            FxWorld.drag(s, 0.88f);
            FxWorld.floor(s, floor);
            FxWorld.spin(s, R.f(0, 6.28f), R.s() * 0.03f);
        }
    }

    /** Chunks of the ground block thrown up and out. */
    static void debris(double x, double y, double z, float radius, int count, float up, float out) {
        BlockState ground = groundBlock(x, y, z);
        if (ground == null) return;
        float floor = (float) FxWorld.groundY(x, y + 0.5, z);
        for (int i = 0; i < count; i++) {
            float a = R.f(0, 6.2832f), r = radius * R.f(0.2f, 1f);
            double cx = Mth.cos(a), cz = Mth.sin(a);
            FxDebris.spawn(ground, x + cx * r, y + 0.2, z + cz * r, cx * out * R.f(0.5f, 1.3f), up * R.f(0.6f, 1.3f), cz * out * R.f(0.5f, 1.3f),
                    R.f(0.16f, 0.34f), 30 + R.i(25), floor);
        }
    }

    static BlockState groundBlock(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (int i = 0; i < 4; i++) {
            MUT.set(x, y - 0.2 - i, z);
            BlockState s = level.getBlockState(MUT);
            if (!s.isAir() && s.getFluidState().isEmpty()) return s;
        }
        return null;
    }

    static Shape ring(double x, double y, double z, float r0, float r1, float w0, float w1, int life, int c0, int c1,
                      float nx, float ny, float nz) {
        Shape s = FxWorld.shape(Shape.RING, life);
        if (s == null) return null;
        s.x = x;
        s.y = y;
        s.z = z;
        s.a = r0;
        s.b = r1;
        s.c = w0;
        s.d = w1;
        s.c0 = c0;
        s.c1 = c1;
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-4f) {
            nx = 0;
            ny = 1;
            nz = 0;
            l = 1;
        }
        s.nx = nx / l;
        s.ny = ny / l;
        s.nz = nz / l;
        s.fadeIn = 0;
        s.fadeOut = Math.max(2, life / 3);
        return s;
    }

    static Shape disc(double x, double y, double z, PowerFxParticles.Sprite sprite, float s0, float s1, int life, int c0, int c1) {
        Shape s = FxWorld.shape(Shape.DISC, life);
        if (s == null) return null;
        s.x = x;
        s.y = y;
        s.z = z;
        s.sprite = sprite.index();
        s.a = s0;
        s.b = s1;
        s.c0 = c0;
        s.c1 = c1;
        return s;
    }

    static Shape attach(Shape s, Entity e, int anchor) {
        if (s != null && e != null) {
            s.follow = e.getId();
            s.anchor = anchor;
            Anchors.of(e, anchor, 1f, P);
            s.x = P[0];
            s.y = P[1];
            s.z = P[2];
        }
        return s;
    }

    static Shape emitter(FxEvent e, int emitter, int life, Entity follow, int anchor) {
        Shape s = FxWorld.shape(Shape.EMITTER, life);
        if (s == null) return null;
        s.x = e.x();
        s.y = e.y();
        s.z = e.z();
        s.ex = e.x() + e.dx();
        s.ey = e.y() + e.dy();
        s.ez = e.z() + e.dz();
        s.style = e.style();
        s.seed = e.seed();
        s.emitter = emitter;
        s.a = e.scale();
        s.c0 = e.colour();
        attach(s, follow, anchor);
        return s;
    }

    private static int col(FxEvent e) {
        return FxWorld.midOr(e.colour(), e.style());
    }

    // ---- recipes ----------------------------------------------------------------------------------------------------

    private static void cast(FxEvent e, FxStyle st, Entity caster) {
        if (caster == null) return;
        Anchors.of(caster, Anchors.HAND, 1f, P);
        for (int i = 0; i < n(5); i++) {
            int s = FxWorld.sprite(MOTE, P[0] + R.s() * 0.15, P[1] + R.s() * 0.15, P[2] + R.s() * 0.15, R.s() * 0.04, R.f(0.01f, 0.05f), R.s() * 0.04,
                    10 + R.i(6), 0.18f, 0.04f, st.core, alpha(st.mid, 0), FxPool.ADD);
            FxWorld.drag(s, 0.9f);
        }
        FxWorld.sprite(GLOW, P[0], P[1], P[2], 0, 0, 0, 6, 0.7f, 0.2f, alpha(st.mid, 0.8f), alpha(st.edge, 0), FxPool.ADD);
    }

    private static void charge(FxEvent e, FxStyle st, Entity caster, boolean mine) {
        if (caster == null) return;
        // the local player's own key press already started one: keep it, just stretch it to the server's length
        Shape existing = FxWorld.findAttached(caster.getId(), Shape.DISC, E_CHARGE);
        int life = Math.max(10, e.ticks()) + 8;
        if (existing != null) {
            existing.life = Math.max(existing.life, existing.age + life);
            return;
        }
        startCharge(caster, st, e.scale(), life, e.seed());
    }

    /** The charge orb at the hand: a pulsing core in a halo that gathers sparks; also used for local prediction. */
    static void startCharge(Entity caster, FxStyle st, float scale, int life, int seed) {
        float sc = Math.max(0.5f, scale) * 1.3f;
        int origin = originAnchor(caster);
        Shape halo = attach(disc(0, 0, 0, HALO, 0.5f * sc, 1.8f * sc, life, alpha(st.mid, 0.9f), alpha(st.mid, 1f)), caster, origin);
        if (halo != null) {
            halo.emitter = E_CHARGE;
            halo.style = st;
            halo.seed = seed;
            halo.flags |= Shape.PULSE;
            halo.fadeOut = 3;
        }
        Shape core = attach(disc(0, 0, 0, st.plain() == FxStyle.KINETIC ? FLARE : GLOW, 0.1f * sc, 0.45f * sc, life, st.core, 0xFFFFFFFF), caster, origin);
        if (core != null) {
            core.emitter = E_CHARGE;
            core.emitParam = -1; // the core emits nothing
            core.flags |= Shape.FLICKER;
            core.c = 0.15f;
            core.fadeOut = 3;
        }
    }

    private static void chargeEnd(FxEvent e, FxStyle st, Entity caster) {
        if (caster == null) return;
        int what = Math.round(e.aux());
        if (what > 0) {
            // an aura ends: its emitter and the shapes it made
            for (Shape s : FxWorld.SHAPES) {
                if (s.live && s.follow == caster.getId() && (s.emitter == E_AURA || s.emitter == E_AURA_CHILD) && s.emitParam == what - 1) {
                    s.life = Math.min(s.life, s.age + s.fadeOut);
                }
            }
            return;
        }
        if (what == -2) {
            for (Shape s : FxWorld.SHAPES) {
                if (s.live && s.follow == caster.getId() && (s.type == Shape.SPHERE || s.type == Shape.SHELL)) s.life = Math.min(s.life, s.age + s.fadeOut);
            }
            return;
        }
        FxWorld.endAttached(caster.getId(), E_CHARGE);
        CastAnims.release(caster.getId());
        if (e.has(FxEvent.FULL)) {
            Anchors.of(caster, Anchors.HAND, 1f, P);
            flare(P[0], P[1], P[2], 1.2f, st, 5);
            sparks(P[0], P[1], P[2], n(10), 0.35f, st, 6, 0, 0, 0, 0, 0);
        }
    }

    private static void impact(FxEvent e, FxStyle st, float big, boolean mine) {
        double x = e.x(), y = e.y(), z = e.z();
        float s = Math.max(0.4f, e.scale()) * big;
        double dl = Math.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        double bx = dl > 1e-3 ? e.dx() / dl : 0, by = dl > 1e-3 ? e.dy() / dl : 0, bz = dl > 1e-3 ? e.dz() / dl : 0;
        flare(x, y, z, 0.9f * s, st, 5);
        sparks(x, y, z, n((int) (9 * s)), 0.32f * s, st, 6, 0.01f, bx, by, bz, 0.8f);
        // a ring of force punched out along the hit
        if (dl > 1e-3) ring(x, y, z, 0.15f * s, 1.3f * s, 0.25f * s, 0.05f, 9, alpha(st.mid, 0.95f), alpha(st.edge, 0), (float) bx, (float) by, (float) bz);
        switch (st.plain()) {
            case KINETIC -> {
                smokePuffs(x, y, z, n((int) (4 * s)), 0.3f, 0.5f * s, st, 0.05f, 14);
                ring(x, y, z, 0.2f * s, 1.8f * s, 0.4f * s, 0.08f, 6, alpha(st.mid, 0.9f), alpha(st.edge, 0), (float) bx, (float) by, (float) bz);
            }
            case ENERGY, HEAT, PRISM -> {
                embers(x, y, z, n((int) (6 * s)), 0.25f, st, 0.004f);
                sphereBurst(x, y, z, 0.15f * s, 1.0f * s, 7, st);
            }
            case CHARGE -> {
                for (int i = 0; i < n(4); i++) {
                    int b = FxWorld.sprite(BOLT, x + R.s() * 0.3, y + R.s() * 0.3, z + R.s() * 0.3, 0, 0, 0, 3 + R.i(3), R.f(0.5f, 0.9f) * s, 0.3f,
                            st.core, alpha(st.mid, 0), FxPool.ADD);
                    FxWorld.spin(b, R.f(0, 6.28f), 0);
                }
                arc(x, y, z, x + R.s() * 1.5 * s, y + R.s() * s, z + R.s() * 1.5 * s, st, 4, 0.035f);
            }
            case SHADOW -> {
                smokePuffs(x, y, z, n((int) (5 * s)), 0.25f, 0.5f * s, st, 0.03f, 16);
                int sl = FxWorld.sprite(SLASH, x, y, z, 0, 0, 0, 6, 1.8f * s, 2.1f * s, st.core, alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                FxWorld.spin(sl, R.f(0, 6.28f), 0);
            }
            case SPIRIT, SOUL -> {
                // the spirit goes through: a tall ring around the target, a ring on the ground and a palm print bursting
                wisps(x, y, z, n((int) (8 * s)), 0.5f, st, 0.03f);
                if (s >= 1f) {
                    ring(x, y, z, 0.4f, 1.6f * s, 0.45f, 0.12f, 14, alpha(st.mid, 0.95f), alpha(st.edge, 0.2f), (float) bx, (float) by, (float) bz);
                    double gy = FxWorld.groundY(x, y, z);
                    ring(x, gy + 0.06, z, 0.3f, 1.4f * s, 0.5f, 0.15f, 16, alpha(st.mid, 0.9f), alpha(st.edge, 0), 0, 1, 0);
                    FxWorld.sprite(PALM, x, y, z, bx * 0.08, by * 0.08, bz * 0.08, 12, 1.4f * s, 2.2f * s, alpha(st.mid, 0.9f), alpha(st.edge, 0),
                            FxPool.ADD);
                }
            }
            case MIND -> {
                for (int i = 0; i < 3; i++) {
                    FxWorld.sprite(RING, x, y, z, 0, 0, 0, 8 + i * 3, 0.3f, (1.4f + i * 0.6f) * s, alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD);
                }
            }
            case TECH -> {
                motes(x, y, z, n((int) (8 * s)), 0.3f, st, 0.12f);
                FxWorld.sprite(HEX, x, y, z, 0, 0, 0, 7, 0.4f, 1.6f * s, alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD);
            }
            case GRAVITY -> {
                ring(x, y, z, 1.6f * s, 0.1f, 0.15f, 0.3f * s, 8, alpha(st.mid, 0.9f), alpha(st.core, 0.6f), 0, 1, 0);
                Shape d = disc(x, y, z, GLOW, 0.2f, 0.9f * s, 8, 0xF00D0A17, 0x000D0A17);
                if (d != null) d.flags |= Shape.DARK;
            }
            case FROST -> shards(x, y, z, n((int) (8 * s)), 0.25f * s, st);
            case ARCHITECT -> motes(x, y, z, n((int) (6 * s)), 0.25f, st, 0.1f);
            default -> {
            }
        }
        if (mine && !e.has(FxEvent.QUIET)) {
            FxScreen.kick(-1.2f * s, 0, (R.chance(0.5f) ? 1 : -1) * 0.8f * s);
            if (s > 1.4f) FxScreen.flash(st.mid, 0.08f * s, 4);
        }
    }

    /**
     * A blade landing (Arms), by what it meets: warm sparks off plate and stone, pale chips off bone, brown chips off
     * wood, wisps through a spirit, green spatter from slime, a dark red spatter from flesh; a small white flare at the
     * contact for all. {@code size} 0.5 a quick cut .. 1.5 an Execution. No screen feel: Arms makes its own.
     */
    static void bladeHit(double x, double y, double z, double bx, double by, double bz, float size, dev.awaken.core.api.BodyMaterial m) {
        float s = Math.max(0.3f, size);
        FxStyle st = switch (m) {
            case ARMOR, STONE -> FxStyle.HEAT;
            case BONE -> FxStyle.FLIGHT;
            case WOOD -> FxStyle.KINETIC;
            case SPIRIT -> FxStyle.SPIRIT;
            case SLIME -> FxStyle.TECH;
            case FLESH -> FxStyle.VELOCITY;
        };
        flare(x, y, z, 0.55f * s, FxStyle.NEUTRAL, 4);
        switch (m) {
            case ARMOR, STONE -> sparks(x, y, z, n((int) (12 * s)), 0.38f * s, st, 7, 0.03f, bx, by, bz, 0.6f);
            case SPIRIT -> wisps(x, y, z, n((int) (6 * s)), 0.35f, st, 0.03f);
            default -> sparks(x, y, z, n((int) (6 * s)), 0.2f * s, st, 6, 0.05f, bx, by, bz, 0.7f);
        }
    }

    /**
     * A shot on a raised guard (FxClient.guardSpark): a flash where it struck the steel, a ring off the edge on a
     * deflect, and sparks thrown back the way it came (a wide cone, a little up), falling and cooling to the weapon's
     * light. Near the eye (the fighter's own blade) everything is drawn at a fifth of its size: it is at arm's length.
     */
    static void guardSpark(double x, double y, double z, double dx, double dy, double dz, int argb, float size, boolean near) {
        int nf = near ? FxPool.NEAR : 0;
        float sc = near ? 0.2f : 1f;
        int hot = FxWorld.mix(argb, 0xFFFFFFFF, 0.62f);
        int f = FxWorld.sprite(FLARE, x, y, z, 0, 0, 0, 3, 0.7f * size * sc, 0.15f * size * sc, hot, alpha(argb, 0), FxPool.ADD | FxPool.ANIM | nf);
        FxWorld.spin(f, R.f(0, 6.28f), 0);
        FxWorld.sprite(GLOW, x, y, z, 0, 0, 0, 5, 0.75f * size * sc, 0.4f * size * sc, alpha(argb, 0.55f), alpha(argb, 0), FxPool.ADD | nf);
        if (size >= 0.9f) {
            FxWorld.sprite(RING, x, y, z, 0, 0, 0, 5, 0.12f * sc, 1.0f * sc, alpha(hot, 0.85f), alpha(argb, 0), FxPool.ADD | nf);
        }
        int count = n(Math.round(6 + 8 * size));
        float speed = (0.12f + 0.12f * size) * (near ? 0.45f : 1f);
        for (int i = 0; i < count; i++) {
            double vx = R.s(), vy = R.s() + 0.4, vz = R.s();
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
            vx = vx / l + dx * 0.9;
            vy = vy / l + dy * 0.9;
            vz = vz / l + dz * 0.9;
            l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
            float sp = speed * R.f(0.5f, 1.3f);
            int s = FxWorld.sprite(SPARK, x, y, z, vx / l * sp, vy / l * sp, vz / l * sp, 5 + R.i(5), R.f(0.14f, 0.24f) * sc, 0.03f * sc,
                    FxWorld.mix(hot, argb, 0.25f), alpha(argb, 0), FxPool.ADD | FxPool.STRETCH | nf);
            FxWorld.drag(s, 0.84f);
            FxWorld.grav(s, near ? 0.012f : 0.03f);
            FxWorld.stretch(s, 1.5f);
        }
    }

    static void sphereBurst(double x, double y, double z, float r0, float r1, int life, FxStyle st) {
        Shape sp = FxWorld.shape(Shape.SPHERE, life);
        if (sp == null) return;
        sp.x = x;
        sp.y = y;
        sp.z = z;
        sp.a = r0;
        sp.b = r1;
        sp.c = 2.2f;
        sp.d = 0.08f;
        sp.c0 = alpha(st.core, 0.95f);
        sp.c1 = alpha(st.mid, 0);
        sp.fadeIn = 0;
        sp.fadeOut = life / 2;
    }

    static void wisps(double x, double y, double z, int count, float spread, FxStyle st, float rise) {
        for (int i = 0; i < count; i++) {
            int s = FxWorld.sprite(WISP, x + R.s() * spread, y + R.s() * spread, z + R.s() * spread, R.s() * 0.02, R.f(0.01f, 0.04f), R.s() * 0.02,
                    16 + R.i(14), R.f(0.25f, 0.45f), 0.1f, alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
            FxWorld.grav(s, -rise * 0.05f);
            FxWorld.drag(s, 0.95f);
            FxWorld.spin(s, R.f(0, 6.28f), R.s() * 0.08f);
        }
    }

    /** One weapon mote (FxClient.weaponMote): the kind's own life, fade and drift; hot at birth, cooling as it goes. */
    static void weaponMote(int kind, double x, double y, double z, double vx, double vy, double vz, int argb, float size, int near) {
        switch (kind) {
            case FxClient.MOTE_EMBER -> {
                // white-hot at birth, the weapon's own orange-gold, gone at a dull red; rising, slowing, flickering
                int hot = FxWorld.mix(argb, 0xFFFFF4D8, 0.55f), cold = FxWorld.alpha(FxWorld.mix(argb, 0xFF6E1404, 0.7f), 0);
                int s = FxWorld.sprite(EMBER, x, y, z, vx + R.s() * 0.006, vy + R.f(0.004f, 0.016f), vz + R.s() * 0.006, 16 + R.i(18),
                        size * R.f(0.8f, 1.2f), size * 0.25f, hot, cold, FxPool.ADD | FxPool.ANIM | FxPool.STRETCH | near);
                FxWorld.grav(s, -0.0022f);
                FxWorld.drag(s, 0.9f);
                FxWorld.stretch(s, 1.4f);
            }
            case FxClient.MOTE_WISP -> {
                int s = FxWorld.sprite(WISP, x, y, z, vx + R.s() * 0.004, vy + R.f(0.001f, 0.006f), vz + R.s() * 0.004, 24 + R.i(22),
                        size * R.f(0.8f, 1.1f), size * 1.9f, FxWorld.alpha(argb, 0.75f), FxWorld.alpha(FxWorld.mix(argb, 0xFF2C3486, 0.4f), 0),
                        FxPool.ADD | FxPool.ANIM | near);
                FxWorld.grav(s, -0.0006f);
                FxWorld.drag(s, 0.93f);
                FxWorld.spin(s, R.f(0, 6.28f), R.s() * 0.09f);
            }
            default -> {
                int s = FxWorld.sprite(MOTE, x, y, z, vx, vy + R.f(0f, 0.004f), vz, 26 + R.i(24), size * R.f(0.7f, 1.2f), size * 0.15f,
                        FxWorld.mix(argb, 0xFFFFFFFF, 0.35f), FxWorld.alpha(argb, 0), FxPool.ADD | near);
                FxWorld.drag(s, 0.88f);
            }
        }
    }

    /** One mark of a tell (FxClient.tellMark). */
    static void tellMark(int mark, double x, double y, double z, int argb, float size, int life) {
        // a red stays red: the danger colours take little white at their heart
        boolean danger = ((argb >> 16) & 255) > 200 && ((argb >> 8) & 255) < 120;
        int hot = FxWorld.mix(argb, 0xFFFFFFFF, danger ? 0.22f : 0.5f);
        switch (mark) {
            case FxClient.TELL_GLINT -> {
                // a star's glint: white at its heart, the class's colour round it, there and gone
                FxWorld.sprite(FLARE, x, y, z, 0, 0, 0, life, size, size * 0.2f, hot, FxWorld.alpha(argb, 0), FxPool.ADD | FxPool.NEAR);
                FxWorld.sprite(GLOW, x, y, z, 0, 0, 0, life + 2, size * 0.9f, size * 0.4f, FxWorld.alpha(argb, 0.7f), FxWorld.alpha(argb, 0),
                        FxPool.ADD | FxPool.NEAR);
            }
            case FxClient.TELL_FLARE -> {
                FxWorld.sprite(FLARE, x, y, z, 0, 0, 0, life, size * 1.2f, size * 0.3f, hot, FxWorld.alpha(argb, 0), FxPool.ADD | FxPool.NEAR);
                FxWorld.sprite(RING, x, y, z, 0, 0, 0, life + 4, size * 0.4f, size * 2.2f, FxWorld.alpha(argb, 0.9f), FxWorld.alpha(argb, 0),
                        FxPool.ADD | FxPool.NEAR);
                FxWorld.sprite(GLOW, x, y, z, 0, 0, 0, life + 6, size * 1.6f, size, FxWorld.alpha(argb, 0.55f), FxWorld.alpha(argb, 0),
                        FxPool.ADD | FxPool.NEAR);
            }
            case FxClient.TELL_SIGN -> {
                // a rune ring flat on the ground under the creature: the reach of what is coming
                int s = FxWorld.sprite(GLYPH, x, y, z, 0, 0, 0, life, size, size, FxWorld.alpha(hot, 0.9f), FxWorld.alpha(argb, 0.7f),
                        FxPool.ADD | FxPool.FLAT);
                FxWorld.spin(s, 0, 0.05f);
            }
            default -> FxWorld.sprite(GLOW, x, y, z, 0, 0, 0, life, size, size * 0.8f, FxWorld.alpha(argb, 0.75f), FxWorld.alpha(argb, 0.2f),
                    FxPool.ADD | FxPool.NEAR);
        }
    }

    static void motes(double x, double y, double z, int count, float spread, FxStyle st, float speed) {
        for (int i = 0; i < count; i++) {
            int s = FxWorld.sprite(MOTE, x + R.s() * spread, y + R.s() * spread, z + R.s() * spread, R.s() * speed, R.s() * speed, R.s() * speed,
                    10 + R.i(10), 0.16f, 0.06f, st.core, alpha(st.mid, 0), FxPool.ADD);
            FxWorld.drag(s, 0.85f);
        }
    }

    static void shards(double x, double y, double z, int count, float speed, FxStyle st) {
        float floor = (float) FxWorld.groundY(x, y, z);
        for (int i = 0; i < count; i++) {
            double vx = R.s(), vy = R.f(0.1f, 1f), vz = R.s();
            int s = FxWorld.sprite(SHARD, x, y, z, vx * speed, vy * speed, vz * speed, 20 + R.i(10), R.f(0.2f, 0.35f), 0.15f, st.core,
                    alpha(st.mid, 0), FxPool.ADD);
            FxWorld.grav(s, 0.03f);
            FxWorld.floor(s, floor + 0.05f);
            FxWorld.spin(s, R.f(0, 6.28f), R.s() * 0.4f);
        }
    }

    static void arc(double ax, double ay, double az, double bx, double by, double bz, FxStyle st, int life, float width) {
        Shape s = FxWorld.shape(Shape.LIGHTNING, life);
        if (s == null) return;
        s.x = ax;
        s.y = ay;
        s.z = az;
        s.ex = bx;
        s.ey = by;
        s.ez = bz;
        s.a = width;
        s.c0 = st.mid;
        s.c1 = st.core;
        s.seed = (int) R.nextLong();
        s.flags |= Shape.FLICKER;
        s.fadeIn = 0;
        s.fadeOut = 2;
    }

    static void decal(double x, double y, double z, PowerFxParticles.Sprite sprite, float size, int colour, int life) {
        int s = FxWorld.sprite(sprite, x, y, z, 0, 0, 0, life, size, size, colour, alpha(colour, 0), FxPool.LIT | FxPool.FLAT);
        FxWorld.spin(s, R.f(0, 6.28f), 0);
        FxWorld.drag(s, 0);
    }

    private static void slam(FxEvent e, FxStyle st, float big, boolean mine) {
        double x = e.x(), z = e.z();
        double y = FxWorld.groundY(x, e.y() + 0.5, z);
        float r = Math.max(0.5f, e.scale()); // the slam's own hit radius: the ring stops exactly where the damage does
        flare(x, y + 0.3, z, 1.8f * big, st, 6);
        Shape dome = FxWorld.shape(Shape.SPHERE, 6);
        if (dome != null) {
            dome.x = x;
            dome.y = y;
            dome.z = z;
            dome.a = 0.3f;
            dome.b = r * 0.55f;
            dome.c = 1.6f;
            dome.d = 0.25f;
            dome.c0 = alpha(st.core, 0.8f);
            dome.c1 = alpha(st.mid, 0);
            dome.flags |= Shape.HEMI;
            dome.fadeIn = 0;
            dome.fadeOut = 4;
        }
        // the shockwave: a thick hot band and a wider faint one behind it
        ring(x, y + 0.08, z, 0.3f, r - 0.3f, 0.9f, 0.3f, 14, alpha(st.mid, 1f), alpha(st.edge, 0.5f), 0, 1, 0);
        ring(x, y + 0.06, z, 0.5f, r - 0.1f, 0.5f, 0.1f, 15, alpha(st.mid, 0.6f), alpha(st.edge, 0), 0, 1, 0);
        decal(x, y, z, CRACK, r * 1.1f, 0xD82A2420, 90);
        if (st.plain() == FxStyle.GRAVITY) decal(x, y, z, SCORCH, r * 0.8f, 0xC0201830, 70);
        debris(x, y, z, r * 0.4f, n((int) (14 * big)), 0.48f, 0.22f);
        debris(x, y, z, r * 0.25f, n((int) (4 * big)), 0.62f, 0.12f);
        dustRing(x, y, z, r * 0.8f, n((int) (16 * big)), st, 0.09f);
        sparks(x, y + 0.2, z, n(10), 0.45f, st, 7, 0.02f, 0, 0.4, 0, 0.3f);
        if (st.plain() == FxStyle.HEAT || e.variant() == 1 && st.plain() == FxStyle.GRAVITY) embers(x, y + 0.3, z, n(14), r * 0.5f, FxStyle.HEAT, 0.004f);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !e.has(FxEvent.QUIET)) {
            double d = mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
            if (d < r * 3) {
                float near = (float) (1 - d / (r * 3));
                FxScreen.kick(2.4f * near * big, 0, 0);
                FxScreen.flash(st.mid, 0.12f * near * big, 5);
                if (mine) FxScreen.fovPunch(-0.04f * big, 6);
            }
        }
    }

    private static void ring(FxEvent e, FxStyle st, float big) {
        float r = Math.max(0.5f, e.scale());
        int life = e.ticks() > 0 ? e.ticks() : 12;
        float nx = e.dx(), ny = e.dy(), nz = e.dz();
        if (nx == 0 && ny == 0 && nz == 0) ny = 1;
        ring(e.x(), e.y() + 0.08, e.z(), 0.2f, Math.max(0.3f, r - 0.1f), 0.45f, 0.15f, life, alpha(col(e), 0.95f), alpha(st.edge, 0), nx, ny, nz);
        for (int i = 0; i < n(12); i++) {
            float a = i / 12f * 6.2832f;
            int s = FxWorld.sprite(MOTE, e.x() + Mth.cos(a) * 0.4, e.y() + 0.2, e.z() + Mth.sin(a) * 0.4, Mth.cos(a) * r * 0.06, 0.01, Mth.sin(a) * r * 0.06,
                    life, 0.16f, 0.05f, st.core, alpha(st.mid, 0), FxPool.ADD);
            FxWorld.drag(s, 0.92f);
        }
    }

    private static void nova(FxEvent e, FxStyle st, float big, boolean mine) {
        double x = e.x(), y = e.y(), z = e.z();
        float r = Math.max(0.5f, e.scale());
        boolean implode = e.has(FxEvent.REPULSE);
        flare(x, y, z, 2.2f * big, st, 7);
        Shape shell = FxWorld.shape(Shape.SPHERE, 20);
        if (shell != null) {
            shell.x = x;
            shell.y = y;
            shell.z = z;
            shell.a = implode ? r : 0.3f;
            shell.b = implode ? 0.2f : r;
            shell.c = 1.7f;
            shell.d = 0.14f;
            shell.c0 = alpha(st.mid, 1f);
            shell.c1 = alpha(st.mid, 0.8f);
            shell.fadeIn = 0;
            shell.fadeOut = 8;
            // the shell grows in eight ticks and holds for a second; its silhouette is drawn as a bold band
            if (!implode) shell.flags |= Shape.HOLD;
            shell.flags |= Shape.RIM;
        }
        double gy0 = FxWorld.groundY(x, y, z);
        // a flash across the ground under the sphere and two more rings of latitude, so a caster standing inside
        // their own nova sees the wall of light all around
        int flash = FxWorld.sprite(HALO, x, gy0 + 0.05, z, 0, 0, 0, 12, r * 1.2f, r * 2.1f, alpha(st.mid, 0.9f), alpha(st.edge, 0),
                FxPool.ADD | FxPool.FLAT);
        FxWorld.drag(flash, 0);
        for (int k = -1; k <= 1; k += 2) {
            double ly = y + k * r * 0.5;
            if (ly < gy0 + 0.2) continue;
            Shape lat = ring(x, ly, z, implode ? r * 0.87f : 0.3f, implode ? 0.2f : r * 0.87f, 0.35f, 0.18f, 18, alpha(st.mid, 0.9f),
                    alpha(st.edge, 0.25f), 0, 1, 0);
            if (lat != null) {
                lat.flags |= Shape.HOLD;
                lat.fadeOut = 8;
            }
        }
        // the sphere's equator, so its edge reads even where the shell is faint
        Shape eq = ring(x, y, z, implode ? r : 0.3f, implode ? 0.2f : r, 0.5f, 0.3f, 18, alpha(st.mid, 1f), alpha(st.mid, 0.6f), 0, 1, 0);
        if (eq != null) {
            if (!implode) eq.flags |= Shape.HOLD;
            eq.fadeOut = 8;
        }
        sphereBurst(x, y, z, 0.2f, r * 0.55f, 7, st);
        ring(x, FxWorld.groundY(x, y, z) + 0.08, z, 0.4f, r - 0.4f, 0.8f, 0.35f, 18, alpha(st.mid, 1f), alpha(st.edge, 0.2f), 0, 1, 0);
        sparks(x, y, z, n((int) (22 * big)), 0.6f, st, 8, 0, 0, 0, 0, 0);
        if (st.plain() == FxStyle.HEAT || st.plain() == FxStyle.ENERGY) embers(x, y, z, n((int) (14 * big)), r * 0.4f, FxStyle.HEAT, 0.003f);
        if (st.plain() == FxStyle.GRAVITY) dustRing(x, FxWorld.groundY(x, y, z), z, r * 0.6f, n(12), st, -0.08f);
        if (e.has(FxEvent.ULT)) {
            glyphAt(x, FxWorld.groundY(x, y, z), z, r * 0.8f, st, 40, 0.04f);
            for (int i = 0; i < n(6); i++) {
                arc(x, y, z, x + R.s() * r, y + R.s() * r * 0.6, z + R.s() * r, FxStyle.CHARGE, 6 + R.i(4), 0.05f);
            }
            debris(x, FxWorld.groundY(x, y, z), z, r * 0.4f, n(18), 0.6f, 0.3f);
            decal(x, FxWorld.groundY(x, y, z), z, SCORCH, r * 1.2f, 0xD0201814, 120);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !e.has(FxEvent.QUIET)) {
            double d = Math.sqrt(mc.player.distanceToSqr(x, y, z));
            if (d < r * 2.5) {
                float near = (float) (1 - d / (r * 2.5));
                FxScreen.flash(st.mid, 0.2f * near * big, 6);
                FxScreen.kick(1.8f * near * big, 0, 0);
            }
        }
        if (mine) FxScreen.fovPunch(-0.05f * big, 8);
    }

    private static void cone(FxEvent e, FxStyle st, float big) {
        double x = e.x(), z = e.z();
        // a stamp (a cast profile's release): the wave starts under the planted foot, with a thump
        var prof = dev.awaken.powers.fx.CastProfiles.byId(e.profile());
        Entity stamper = prof != null && prof.power().equals("shockwave") && e.caster() >= 0 && Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        if (stamper != null) {
            Anchors.of(stamper, Anchors.PLANT, 1f, P);
            x = P[0];
            z = P[2];
        }
        double y = FxWorld.groundY(x, e.y(), z);
        float range = Math.max(2, e.scale());
        float half = (float) Math.toRadians(Math.max(8, e.aux()));
        int life = Math.max(6, e.ticks());
        float dl = Mth.sqrt(e.dx() * e.dx() + e.dz() * e.dz());
        float dx = dl > 1e-4f ? e.dx() / dl : 0, dz = dl > 1e-4f ? e.dz() / dl : 1;
        Shape w = FxWorld.shape(Shape.ARC, life + 2);
        if (w != null) {
            w.x = x;
            w.y = y + 0.02;
            w.z = z;
            w.a = 0.4f;
            w.b = range;
            w.c = half;
            w.d = 2.3f * big;
            w.nx = dx;
            w.ny = 0;
            w.nz = dz;
            w.c0 = alpha(st.mid, 1f);
            w.c1 = alpha(st.edge, 0.6f);
            w.fadeIn = 0;
            w.fadeOut = 3;
            w.emitter = E_CONE;
            w.style = st;
            w.seed = e.seed();
        }
        ring(x, y + 0.08, z, 0.2f, 1.6f, 0.5f, 0.1f, 7, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
        decal(x + dx * 0.8, y, z + dz * 0.8, CRACK, 2.2f, 0xC02A2420, 60);
        dustRing(x, y, z, 0.8f, n(5), st, 0.05f);
        if (stamper != null) plantPuff(x, y, z, st, big, stamper);
    }

    /** The foot landing: a flare, a heel's cracks and rubble, dust kicked up and sparks off the stone, and the thump. */
    private static void plantPuff(double x, double y, double z, FxStyle st, float big, Entity stamper) {
        flare(x, y + 0.25, z, 1.3f * big, st, 5);
        decal(x, y, z, CRACK, 1.1f, 0xD82A2420, 70);
        debris(x, y, z, 0.35f, n((int) (4 * big)), 0.35f, 0.1f); // few and low: they start a stride from your eyes
        dustRing(x, y, z, 1.1f, n((int) (12 * big)), st, 0.07f);
        sparks(x, y + 0.1, z, n(8), 0.4f, st, 6, 0.02f, 0, 0.35, 0, 0.25f);
    }

    private static void beam(FxEvent e, FxStyle st, Entity caster, boolean mine) {
        int life = Math.max(4, e.ticks()) + 2;
        double ex = e.x() + e.dx(), ey = e.y() + e.dy(), ez = e.z() + e.dz();
        boolean follow = e.has(FxEvent.FOLLOW);
        // a channelled beam refreshes the one already drawn for this caster
        if (caster != null) {
            Shape old = FxWorld.findAttached(caster.getId(), Shape.BEAM, E_BEAM);
            if (old != null && follow && (old.flags & Shape.AIM) != 0 == (mine && follow) && old.c0 == (col(e) | 0xFF000000)) {
                old.life = Math.max(old.life, old.age + life);
                old.ex = ex;
                old.ey = ey;
                old.ez = ez;
                return;
            }
        }
        Shape b = FxWorld.shape(Shape.BEAM, life);
        if (b == null) return;
        b.x = e.x();
        b.y = e.y();
        b.z = e.z();
        b.ex = ex;
        b.ey = ey;
        b.ez = ez;
        b.a = Math.max(0.05f, e.aux() <= 0 ? 0.1f : e.aux()) * 1.35f;
        b.c0 = col(e) | 0xFF000000;
        b.c1 = st.core;
        b.c = R.f(0, 6.28f);
        b.style = st;
        b.seed = e.seed();
        b.fadeIn = 1;
        b.fadeOut = 2;
        b.emitter = E_BEAM;
        b.range = e.scale();
        if (st.plain() == FxStyle.CHARGE) b.flags |= Shape.FLICKER;
        if (caster != null) {
            attach(b, caster, originAnchor(caster));
            if (mine && follow) {
                b.flags |= Shape.AIM;
                if (Minecraft.getInstance().player != null) FxBeams.tickAim(b, Minecraft.getInstance().player);
            }
        }
        flare(b.x, b.y, b.z, 0.6f, st, 4);
    }

    private static void bolt(FxEvent e, FxStyle st) {
        double bx = e.x() + e.dx(), by = e.y() + e.dy(), bz = e.z() + e.dz();
        Shape s = FxWorld.shape(Shape.BEAM, 7);
        int c = col(e) | 0xFF000000;
        if (s != null) {
            s.x = e.x();
            s.y = e.y();
            s.z = e.z();
            s.ex = bx;
            s.ey = by;
            s.ez = bz;
            s.a = 0.1f * Math.max(0.6f, e.scale());
            s.c0 = c;
            s.c1 = 0xFFFFFFFF;
            s.fadeIn = 0;
            s.fadeOut = 3;
            if (st.plain() == FxStyle.CHARGE) s.flags |= Shape.FLICKER;
        }
        FxWorld.sprite(HALO, bx, by, bz, 0, 0, 0, 4, 1.8f * Math.max(0.6f, e.scale()), 0.6f, alpha(c, 1f), alpha(c, 0), FxPool.ADD);
        if (R.chance(0.5f)) {
            int b = FxWorld.sprite(BOLT, bx, by, bz, 0, 0, 0, 2, 0.5f, 0.4f, st.core, alpha(st.mid, 0), FxPool.ADD);
            FxWorld.spin(b, R.f(0, 6.28f), 0);
        }
        int sp = FxWorld.sprite(SPARK, bx, by, bz, R.s() * 0.08, R.s() * 0.08, R.s() * 0.08, 5, 0.12f, 0.03f, st.core, alpha(st.mid, 0),
                FxPool.ADD | FxPool.STRETCH);
        FxWorld.drag(sp, 0.85f);
    }

    private static void lightning(FxEvent e, FxStyle st) {
        double bx = e.x() + e.dx(), by = e.y() + e.dy(), bz = e.z() + e.dz();
        arc(e.x(), e.y(), e.z(), bx, by, bz, st, Math.max(3, e.ticks()), 0.045f * Math.max(0.6f, e.scale()));
        if (!FxWorld.minimal()) arc(e.x(), e.y(), e.z(), bx, by, bz, st, Math.max(2, e.ticks() - 2), 0.025f);
        flare(bx, by, bz, 0.8f, st, 4);
        sparks(bx, by, bz, n(6), 0.25f, st, 5, 0.01f, 0, 0, 0, 0);
    }

    private static void strike(FxEvent e, FxStyle st, float big, Minecraft mc) {
        double x = e.x(), z = e.z();
        double y = FxWorld.groundY(x, e.y() + 1, z);
        float s = Math.max(0.6f, e.scale()) * big;
        arc(x + R.s() * 2, y + 22, z + R.s() * 2, x, y, z, st, 14, 0.16f * s);
        arc(x + R.s() * 3, y + 18, z + R.s() * 3, x, y + 0.5, z, st, 9, 0.08f * s);
        // the sky answers: a dark storm knot over the strike
        for (int i = 0; i < n(9); i++) {
            int c = FxWorld.sprite(SMOKE, x + R.s() * 3, y + R.f(17, 21), z + R.s() * 3, R.s() * 0.02, 0, R.s() * 0.02, 40 + R.i(20), R.f(3, 4.5f),
                    R.f(5, 7), 0xE0383C48, 0x00383C48, FxPool.BRIGHT | FxPool.ANIM);
            FxWorld.spin(c, R.f(0, 6.28f), R.s() * 0.01f);
        }
        Shape crackle = FxWorld.shape(Shape.EMITTER, 18);
        if (crackle != null) {
            crackle.x = x;
            crackle.y = y;
            crackle.z = z;
            crackle.style = st;
            crackle.seed = e.seed();
            crackle.emitter = E_CRACKLE;
        }
        Shape col = FxWorld.shape(Shape.COLUMN, 10);
        if (col != null) {
            col.x = x;
            col.y = y;
            col.z = z;
            col.a = 24;
            col.b = 0.7f * s;
            col.c0 = alpha(st.mid, 0.9f);
            col.c1 = alpha(st.edge, 0);
            col.c2 = 0xFFFFFFFF;
            col.flags |= Shape.FLICKER;
            col.seed = e.seed();
            col.fadeIn = 0;
            col.fadeOut = 4;
        }
        flare(x, y + 0.4, z, 2.4f * s, st, 6);
        ring(x, y + 0.08, z, 0.3f, 3.2f * s, 0.7f, 0.1f, 10, alpha(st.core, 1), alpha(st.mid, 0), 0, 1, 0);
        decal(x, y, z, SCORCH, 2.4f * s, 0xE0181410, 120);
        sparks(x, y + 0.3, z, n(16), 0.5f, st, 8, 0.03f, 0, 1, 0, 0.6f);
        for (int i = 0; i < n(4); i++) arc(x, y + 0.2, z, x + R.s() * 2.5, y + 0.1, z + R.s() * 2.5, st, 4, 0.03f);
        if (mc.player != null && !e.has(FxEvent.QUIET)) {
            double d = Math.sqrt(mc.player.distanceToSqr(x, y, z));
            if (d < 28) FxScreen.flash(0xFFE8F4FF, 0.3f * (float) (1 - d / 28), 5);
        }
    }

    private static void dash(FxEvent e, FxStyle st, Entity caster, boolean mine) {
        double x = e.x(), y = e.y(), z = e.z();
        float dl = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        float dx = dl > 1e-4f ? e.dx() / dl : 0, dy = dl > 1e-4f ? e.dy() / dl : 0, dz = dl > 1e-4f ? e.dz() / dl : 1;
        int life = Math.max(6, e.ticks());
        // sonic ring at the start, facing the way you go
        ring(x + dx * 0.4, y + 0.9, z + dz * 0.4, 0.3f, 1.5f, 0.32f, 0.04f, 7, alpha(st.core, 0.95f), alpha(st.mid, 0), dx, dy, dz);
        // speed lines streaming back past the body
        for (int i = 0; i < n(14); i++) {
            double ox = R.s() * 0.8, oy = R.f(0.1f, 1.8f), oz = R.s() * 0.8;
            int s = FxWorld.sprite(SPARK, x + ox + dx * R.f(0, 2), y + oy, z + oz + dz * R.f(0, 2), -dx * 0.35f, -dy * 0.35f, -dz * 0.35f, 5 + R.i(4),
                    0.12f, 0.06f, alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD | FxPool.STRETCH);
            FxWorld.stretch(s, 2.2f);
            FxWorld.drag(s, 0.8f);
        }
        if (caster != null) {
            for (int anchor : new int[]{Anchors.BODY, Anchors.FEET}) {
                Shape t = attach(FxWorld.shape(Shape.TRAIL, life + 8), caster, anchor);
                if (t != null) {
                    t.a = anchor == Anchors.BODY ? 0.28f : 0.14f;
                    t.c0 = alpha(st.plain() == FxStyle.KINETIC ? st.mid : st.core, 0.85f);
                    t.c1 = alpha(st.mid, 0);
                    t.fadeOut = 6;
                    t.pushHistory(t.x, t.y, t.z);
                }
            }
            if (st.plain() != FxStyle.KINETIC) {
                FxAfterimages.capture(caster, 12, st.mid, 0);
                Shape g = emitter(e, E_GHOSTS, life, caster, Anchors.FEET);
                if (g != null) g.emitParam = 2;
            }
            if (st.plain() == FxStyle.HEAT) {
                Shape f = emitter(e, E_TRAIL, life, caster, Anchors.FEET);
                if (f != null) f.emitParam = 1;
            }
        }
        if (caster != null && caster.onGround()) dustRing(x, y, z, 0.5f, n(st.plain() == FxStyle.KINETIC ? 10 : 4), st, 0.06f);
        if (mine) {
            FxScreen.fovPunch(0.07f, 7);
            FxScreen.speedLines(7);
        }
    }

    private static void blink(FxEvent e, FxStyle st, Entity caster, boolean mine) {
        double ax = e.x(), ay = e.y(), az = e.z();
        double bx = ax + e.dx(), by = ay + e.dy(), bz = az + e.dz();
        switch (st.plain()) {
            case SHADOW -> {
                smokePuffs(ax, ay + 1, az, n(12), 0.4f, 0.7f, st, 0.04f, 18);
                smokePuffs(bx, by + 1, bz, n(10), 0.35f, 0.6f, st, 0.03f, 16);
                int sl = FxWorld.sprite(SLASH, bx, by + 1.1, bz, 0, 0, 0, 7, 2.2f, 2.6f, st.core, alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                FxWorld.spin(sl, R.f(-0.6f, 0.6f) + 0.785f, 0);
                decal(ax, FxWorld.groundY(ax, ay + 0.5, az), az, POOL, 2.2f, 0xE0100C1C, 50);
                decal(bx, FxWorld.groundY(bx, by + 0.5, bz), bz, POOL, 1.8f, 0xE0100C1C, 40);
                tendrils(ax, ay, az, n(4), st);
                tendrils(bx, by, bz, n(3), st);
                double ga = FxWorld.groundY(ax, ay + 0.5, az), gb = FxWorld.groundY(bx, by + 0.5, bz);
                ring(ax, ga + 0.06, az, 0.2f, 1.3f, 0.25f, 0.08f, 12, alpha(st.mid, 0.9f), alpha(st.edge, 0), 0, 1, 0);
                ring(bx, gb + 0.06, bz, 0.2f, 1.1f, 0.25f, 0.08f, 12, alpha(st.mid, 0.9f), alpha(st.edge, 0), 0, 1, 0);
            }
            case SOUL, SPIRIT -> {
                double dx = bx - ax, dy = by - ay, dz = bz - az;
                int k = n(14);
                for (int i = 0; i < k; i++) {
                    float t = i / (float) Math.max(1, k - 1);
                    wisps(ax + dx * t, ay + 1 + dy * t, az + dz * t, 1, 0.3f, st, 0.02f);
                }
                if (caster != null) FxAfterimages.capture(caster, 14, st.mid, 0);
                ring(bx, by + 1, bz, 0.2f, 1.4f, 0.25f, 0.04f, 8, alpha(st.core, 0.9f), alpha(st.mid, 0), (float) dx, (float) dy, (float) dz);
            }
            case MIND -> {
                for (int i = 0; i < 3; i++) FxWorld.sprite(RING, bx, by + 1, bz, 0, 0, 0, 8 + i * 3, 0.4f, 1.6f + i * 0.5f, st.core, alpha(st.mid, 0), FxPool.ADD);
                motes(bx, by + 1, bz, n(10), 0.5f, st, 0.05f);
            }
            default -> {
                // velocity / charge: a hard streak of light along the path
                Shape s = FxWorld.shape(Shape.BEAM, 6);
                if (s != null) {
                    s.x = ax;
                    s.y = ay + 1;
                    s.z = az;
                    s.ex = bx;
                    s.ey = by + 1;
                    s.ez = bz;
                    // the halo (3.2 x the body width) is as wide as the path that shocks
                    s.a = e.aux() > 0 ? e.aux() / 3.2f : 0.22f;
                    s.c0 = st.mid;
                    s.c1 = st.core;
                    s.fadeIn = 0;
                    s.fadeOut = 5;
                    if (st.plain() == FxStyle.CHARGE) s.flags |= Shape.FLICKER;
                }
                if (st.plain() == FxStyle.CHARGE) arc(ax, ay + 1, az, bx, by + 1, bz, st, 6, 0.06f);
                flare(ax, ay + 1, az, 1.2f, st, 4);
                flare(bx, by + 1, bz, 1.4f, st, 5);
                sparks(bx, by + 1, bz, n(10), 0.3f, st, 6, 0.01f, 0, 0, 0, 0);
                if (caster != null) FxAfterimages.capture(caster, 10, st.mid, 0);
            }
        }
        if (mine) {
            FxScreen.fovPunch(0.06f, 6);
            FxScreen.flash(st.mid, 0.08f, 3);
        }
    }

    /** Dark tendrils clawing up out of the ground. */
    static void tendrils(double x, double y, double z, int count, FxStyle st) {
        for (int i = 0; i < count; i++) {
            double ox = R.s() * 0.7, oz = R.s() * 0.7;
            int s = FxWorld.sprite(SPARK, x + ox, y + 0.1, z + oz, ox * 0.02, R.f(0.12f, 0.2f), oz * 0.02, 9 + R.i(5), 0.3f, 0.08f, 0xF0231A3B,
                    alpha(st.mid, 0), FxPool.BRIGHT | FxPool.STRETCH);
            FxWorld.stretch(s, 3f);
            FxWorld.drag(s, 0.86f);
        }
    }

    private static void trail(FxEvent e, FxStyle st) {
        Entity caster = Minecraft.getInstance().level.getEntity(e.caster());
        if (caster == null) return;
        int life = Math.max(10, e.ticks());
        int aux = (int) e.aux();
        int[] anchors = aux == 1 ? new int[]{Anchors.HAND, Anchors.OFFHAND} : aux == 2 ? new int[]{Anchors.BODY} : new int[]{Anchors.FEET};
        // a repeated trail event (passives, refreshes) extends the trail already there
        boolean extended = false;
        for (Shape s : FxWorld.SHAPES) {
            if (s.live && s.follow == caster.getId() && (s.type == Shape.TRAIL || s.emitter == E_TRAIL) && s.age < s.life - s.fadeOut) {
                s.life = Math.max(s.life, s.age + life);
                extended = true;
            }
        }
        if (extended) return;
        for (int anchor : anchors) {
            Shape t = attach(FxWorld.shape(Shape.TRAIL, life), caster, anchor);
            if (t == null) continue;
            t.a = st.plain() == FxStyle.FLIGHT ? 0.12f : 0.18f;
            t.c0 = alpha(st.plain() == FxStyle.FLIGHT ? st.core : st.mid, 0.8f);
            t.c1 = alpha(st.edge, 0);
            t.fadeOut = 8;
            t.pushHistory(t.x, t.y, t.z);
        }
        Shape fx = emitter(e, E_TRAIL, life, caster, Anchors.FEET);
        if (fx != null) fx.emitParam = st.plain() == FxStyle.HEAT ? 1 : st.plain() == FxStyle.FLIGHT ? 2 : 0;
    }

    private static void afterimage(FxEvent e, FxStyle st, Entity caster) {
        if (caster == null) return;
        int life = Math.max(10, e.ticks());
        FxAfterimages.capture(caster, Math.min(life, 120), st.mid, 0);
        smokePuffs(e.x(), e.y() + 0.2, e.z(), n(4), 0.3f, 0.4f, st, 0.02f, 12);
        wisps(e.x(), e.y() + 1, e.z(), n(4), 0.4f, st, 0.02f);
    }

    private static void smoke(FxEvent e, FxStyle st, float big) {
        float s = Math.max(0.5f, e.scale()) * big;
        smokePuffs(e.x(), e.y() + 0.6, e.z(), n((int) (12 * s)), 0.5f * s, 0.7f * s, st, 0.05f, 20);
        if (st.plain() == FxStyle.SHADOW) {
            tendrils(e.x(), e.y(), e.z(), n(5), st);
            decal(e.x(), FxWorld.groundY(e.x(), e.y() + 0.5, e.z()), e.z(), POOL, 2.4f * s, 0xD8100C1C, 50);
        }
    }

    private static void dome(FxEvent e, FxStyle st, float big) {
        Entity caster = e.caster() >= 0 ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        int life = Math.max(10, e.ticks());
        float r = Math.max(0.8f, e.scale());
        boolean ground = e.aux() >= 1;
        Shape d = FxWorld.shape(Shape.SPHERE, life);
        if (d != null) {
            d.x = e.x();
            d.y = e.y();
            d.z = e.z();
            d.a = r * 0.2f;
            d.b = r;
            d.c = 2.4f;
            d.d = st.plain() == FxStyle.ARCHITECT ? 0.14f : 0.05f;
            d.c0 = alpha(col(e), 0.85f);
            d.c1 = alpha(col(e), 0.85f);
            d.flags |= Shape.HOLD | Shape.PULSE | (ground ? Shape.HEMI : 0);
            d.fadeIn = 2;
            d.fadeOut = 8;
            d.emitter = E_DOME;
            d.style = st;
            d.seed = e.seed();
            if (e.has(FxEvent.FOLLOW) && caster != null) attach(d, caster, ground ? Anchors.FEET : Anchors.BODY);
        }
        double gy = ground ? e.y() : FxWorld.groundY(e.x(), e.y(), e.z());
        ring(e.x(), gy + 0.08, e.z(), 0.3f, r * 1.05f, 0.6f, 0.15f, 10, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
        if (st.plain() == FxStyle.ENERGY && caster != null) {
            Shape sh = attach(FxWorld.shape(Shape.SHELL, life), caster, Anchors.BODY);
            if (sh != null) {
                sh.sprite = HEX.index();
                sh.a = 0.85f;
                sh.b = 0.42f;
                sh.c0 = st.mid;
                sh.seed = e.seed() & 63;
                sh.fadeOut = 6;
            }
        }
        if (st.plain() == FxStyle.ARCHITECT || st.plain() == FxStyle.SPIRIT) glyphAt(e.x(), gy, e.z(), r * 0.9f, st, Math.min(life, 60), 0.02f);
    }

    private static void vortex(FxEvent e, FxStyle st, float big) {
        double x = e.x(), y = e.y(), z = e.z();
        float r = Math.max(1f, e.scale());
        int life = Math.max(10, e.ticks());
        boolean out = e.has(FxEvent.REPULSE);
        Shape halo = disc(x, y, z, HALO, 1.0f, 2.8f * big, life, alpha(st.mid, 0.9f), alpha(st.mid, 0.9f));
        if (halo != null) {
            halo.flags |= Shape.PULSE;
            halo.fadeOut = 6;
            halo.emitter = E_VORTEX;
            halo.style = st;
            halo.seed = e.seed();
            halo.emitParam = out ? 1 : 0;
            halo.d = r;
        }
        if (!out && st.inky()) {
            Shape core = disc(x, y, z, GLOW, 0.4f, 1.15f * big, life, 0xF80D0A17, 0xF80D0A17);
            if (core != null) {
                core.flags |= Shape.DARK | Shape.PULSE;
                core.fadeOut = 6;
            }
        }
        ring(x, y, z, out ? 0.3f : r, out ? r : 0.3f, 0.4f, 0.2f, 12, alpha(st.mid, 0.9f), alpha(st.core, 0.5f), 0, 1, 0);
        double gy = FxWorld.groundY(x, y, z);
        if (y - gy < 3) decal(x, gy, z, SCORCH, r * 0.9f, 0xB0201830, life + 20);
    }

    private static void tether(FxEvent e, FxStyle st) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity caster = e.caster() >= 0 ? level.getEntity(e.caster()) : null;
        Entity target = e.target() >= 0 ? level.getEntity(e.target()) : null;
        int life = Math.max(4, e.ticks());
        int kind = (int) e.aux();
        Shape t = FxWorld.shape(Shape.TETHER, life);
        if (t == null) return;
        t.x = e.x();
        t.y = e.y();
        t.z = e.z();
        t.ex = e.x() + e.dx();
        t.ey = e.y() + e.dy();
        t.ez = e.z() + e.dz();
        t.a = kind == 1 ? 0.035f : 0.05f;
        t.b = kind == 0 ? 0.35f : kind == 2 ? 0.18f : 0.04f;
        t.d = kind == 1 ? 1.5f : 0.4f;
        t.c0 = col(e) | 0xFF000000;
        t.c1 = kind == 1 ? 0xFFE6FFFA : st.core;
        t.fadeIn = 1;
        t.fadeOut = 3;
        if (caster != null) attach(t, caster, Anchors.HAND);
        if (target != null) t.target = target.getId();
        flare(t.ex, t.ey, t.ez, 0.7f, st, 4);
        if (kind == 2 && target != null) lift(e, st);
    }

    private static void aura(FxEvent e, FxStyle st) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity on = e.caster() >= 0 ? level.getEntity(e.caster()) : null;
        if (on == null) return;
        int life = Math.max(10, e.ticks());
        int type = (int) e.aux();
        FxKind.Aura aura = AURAS[Math.max(0, Math.min(AURAS.length - 1, type))];
        // one aura of a kind per entity: a refresh extends it (and the shapes it made)
        boolean refreshed = false;
        for (Shape s : FxWorld.SHAPES) {
            if (s.live && s.follow == on.getId() && (s.emitter == E_AURA || s.emitter == E_AURA_CHILD) && s.emitParam == type) {
                s.life = Math.max(s.life, s.age + life);
                refreshed = true;
            }
        }
        if (refreshed) return;
        Shape s = emitter(e, E_AURA, life, on, Anchors.FEET);
        if (s == null) return;
        s.emitParam = type;
        switch (aura) {
            case NANITE, BARRIER -> {
                Shape sh = attach(FxWorld.shape(Shape.SHELL, life), on, Anchors.BODY);
                if (sh != null) {
                    sh.sprite = HEX.index();
                    sh.a = 0.8f;
                    sh.b = 0.38f;
                    sh.c0 = st.mid;
                    sh.seed = e.seed() & 63;
                    sh.fadeOut = 6;
                    child(sh, type);
                }
            }
            case FLIGHT -> {
                for (int anchor : new int[]{Anchors.HAND, Anchors.OFFHAND, Anchors.FEET}) {
                    Shape t = attach(FxWorld.shape(Shape.TRAIL, life), on, anchor);
                    if (t != null) {
                        t.a = 0.1f;
                        t.c0 = alpha(st.core, 0.7f);
                        t.c1 = alpha(st.mid, 0);
                        t.fadeOut = 8;
                        t.pushHistory(t.x, t.y, t.z);
                        child(t, type);
                    }
                }
            }
            case MARK, THIRD_EYE -> {
                Shape d = attach(disc(0, 0, 0, aura == FxKind.Aura.MARK ? RUNE : GLOW, 0.5f, 0.5f, life, alpha(st.mid, 0.95f), alpha(st.mid, 0.95f)),
                        on, aura == FxKind.Aura.MARK ? Anchors.HEAD : Anchors.EYE);
                if (d != null) {
                    child(d, type);
                    d.flags |= Shape.PULSE;
                    d.c = aura == FxKind.Aura.MARK ? 0.05f : 0;
                    d.frame = R.i(4);
                    if (aura == FxKind.Aura.THIRD_EYE) {
                        d.a = 0.25f;
                        d.b = 0.25f;
                    }
                }
            }
            case BRACE -> ring(e.x(), e.y() + 0.06, e.z(), 0.3f, 1.3f, 0.5f, 0.2f, 10, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
            default -> {
            }
        }
    }

    /** Marks a shape as made by an aura, so it ends and refreshes with it (its emitter does nothing itself). */
    private static void child(Shape s, int auraType) {
        s.emitter = E_AURA_CHILD;
        s.emitParam = auraType;
    }

    private static void palm(FxEvent e, FxStyle st, float big, boolean mine) {
        double x = e.x(), y = e.y(), z = e.z();
        float dl = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        float dx = dl > 1e-4f ? e.dx() / dl : 0, dy = dl > 1e-4f ? e.dy() / dl : 0, dz = dl > 1e-4f ? e.dz() / dl : 1;
        float s = Math.max(0.6f, e.scale()) * big;
        // d reaches the target; the print keeps going three blocks past it so it reads as passing through
        float travel = dl + 3f;
        float speed = 0.55f;
        int life = Math.max(8, Math.min(24, (int) (travel / speed)));
        int p1 = FxWorld.sprite(PALM, x, y, z, dx * speed, dy * speed, dz * speed, life, 1.2f * s, 3.0f * s, alpha(st.mid, 0.95f), alpha(st.edge, 0),
                FxPool.ADD);
        FxWorld.drag(p1, 1f);
        int p2 = FxWorld.sprite(PALM, x, y, z, dx * speed * 0.8f, dy * speed * 0.8f, dz * speed * 0.8f, life + 4, 1.0f * s, 2.4f * s, alpha(st.core, 0.7f),
                alpha(st.mid, 0), FxPool.ADD);
        FxWorld.drag(p2, 1f);
        Shape r = ring(x, y, z, 0.3f, 1.4f * s, 0.3f, 0.08f, life, alpha(st.mid, 0.95f), alpha(st.edge, 0.3f), dx, dy, dz);
        if (r != null) {
            r.vx = dx * speed;
            r.vy = dy * speed;
            r.vz = dz * speed;
            r.fadeOut = 4;
        }
        flare(x, y, z, 1.1f, st, 5);
        // wisps strung along the path the print takes
        for (int i = 0; i < n(10); i++) {
            float t = R.f(0, travel);
            wisps(x + dx * t, y + dy * t, z + dz * t, 1, 0.35f, st, 0.03f);
        }
        if (mine) FxScreen.kick(-0.8f, 0, 0);
    }

    private static void echo(FxEvent e, FxStyle st, float big) {
        double x = e.x(), y = e.y(), z = e.z();
        float s = Math.max(0.6f, e.scale()) * big;
        ring(x, y, z, 0.2f, 2.2f * s, 0.45f, 0.1f, 14, alpha(st.mid, 0.95f), alpha(st.edge, 0.2f), e.dx(), e.dy(), e.dz());
        double gy = FxWorld.groundY(x, y, z);
        ring(x, gy + 0.06, z, 0.2f, 1.8f * s, 0.4f, 0.1f, 16, alpha(st.mid, 0.85f), alpha(st.edge, 0), 0, 1, 0);
        // the echo of the palm: a print that blooms out of the target and fades
        FxWorld.sprite(PALM, x, y, z, 0, 0, 0, 14, 2.6f * s, 1.4f * s, alpha(st.mid, 0.9f), alpha(st.edge, 0), FxPool.ADD);
        FxWorld.sprite(PALM, x, y, z, 0, 0.02, 0, 10, 1.2f * s, 3.2f * s, alpha(st.core, 0.6f), alpha(st.mid, 0), FxPool.ADD);
        flare(x, y, z, 1.4f * s, st, 6);
        wisps(x, y, z, n(14), 0.6f, st, 0.05f);
    }

    private static void flurry(FxEvent e, FxStyle st) {
        Entity caster = e.caster() >= 0 ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        if (caster == null) return;
        Shape s = emitter(e, E_FLURRY, Math.max(10, e.ticks()), caster, Anchors.EYE);
        if (s != null) {
            s.d = e.aux() > 0 ? e.aux() : 30;
            s.b = e.scale() > 0.5f ? e.scale() : 4.5f;
        }
    }

    private static void heal(FxEvent e, FxStyle st) {
        Entity on = e.caster() >= 0 ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        int life = Math.max(8, e.ticks());
        if (on != null) emitter(e, E_HEAL, life, on, Anchors.FEET);
        double gy = FxWorld.groundY(e.x(), e.y() + 0.3, e.z());
        glyphAt(e.x(), gy, e.z(), 1.2f, st, life + 10, 0.04f);
        ring(e.x(), gy + 0.08, e.z(), 0.2f, 1.6f, 0.4f, 0.08f, 12, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
    }

    static void glyphAt(double x, double y, double z, float radius, FxStyle st, int life, float spin) {
        Shape g = FxWorld.shape(Shape.GLYPH, life);
        if (g == null) return;
        g.x = x;
        g.y = y + 0.04;
        g.z = z;
        g.sprite = GLYPH.index();
        g.a = radius * 0.3f;
        g.b = radius;
        g.c = spin;
        g.c0 = alpha(st.mid, 0.9f);
        g.c1 = alpha(st.plain() == FxStyle.ARCHITECT ? st.accent : st.mid, 0.7f);
        g.fadeIn = 3;
        g.fadeOut = Math.max(4, life / 4);
    }

    private static void frost(FxEvent e, FxStyle st, float big) {
        double x = e.x(), z = e.z();
        double y = FxWorld.groundY(x, e.y() + 0.5, z);
        float r = Math.max(0.5f, e.scale());
        flare(x, y + 1, z, 1.6f, st, 6);
        ring(x, y + 0.08, z, 0.3f, r - 0.3f, 0.8f, 0.3f, 12, alpha(st.core, 1), alpha(st.mid, 0.2f), 0, 1, 0);
        shards(x, y + 0.6, z, n((int) (22 * big)), 0.35f, st);
        smokePuffs(x, y + 0.4, z, n(10), r * 0.4f, 0.9f, FxStyle.FLIGHT, 0.03f, 26);
        decal(x, y, z, CRACK, r * 1.2f, 0xB0D8F0FF, 140);
    }

    private static void fireTrail(FxEvent e, FxStyle st) {
        Shape s = emitter(e, E_FIRE, Math.max(10, e.ticks()), null, 0);
        if (s != null) s.style = st;
        decal(e.x(), FxWorld.groundY(e.x(), e.y() + 0.5, e.z()), e.z(), SCORCH, 1.4f, 0xC0201410, Math.max(20, e.ticks()));
    }

    private static void exhaust(FxEvent e, FxStyle st, boolean mine) {
        Entity caster = e.caster() >= 0 ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        if (caster == null) return;
        Shape s = emitter(e, E_EXHAUST, Math.max(4, e.ticks()), caster, Anchors.FEET);
        if (s != null) s.style = st;
        Shape t = attach(FxWorld.shape(Shape.TRAIL, Math.max(4, e.ticks()) + 10), caster, Anchors.FEET);
        if (t != null) {
            t.a = 0.22f;
            t.c0 = alpha(st.mid, 0.85f);
            t.c1 = alpha(FxStyle.HEAT.mid, 0);
            t.fadeOut = 8;
            t.pushHistory(t.x, t.y, t.z);
        }
        flare(e.x(), e.y(), e.z(), 1.2f, FxStyle.HEAT, 4);
        if (mine) {
            FxScreen.fovPunch(0.08f, 8);
            FxScreen.speedLines(6);
        }
    }

    private static void construct(FxEvent e, FxStyle st) {
        Shape s = FxWorld.shape(Shape.SEAMS, 46);
        if (s == null) return;
        Entity caster = e.caster() >= 0 ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        if (caster != null) {
            s.x = caster.getX();
            s.y = caster.getY() + 1;
            s.z = caster.getZ();
        } else {
            s.x = e.x();
            s.y = e.y();
            s.z = e.z();
        }
        // the box to scan, once the block updates have arrived (they follow this event by a tick)
        s.ex = e.x();
        s.ey = e.y();
        s.ez = e.z();
        s.vx = 0;
        s.a = e.dx();
        s.b = e.dy();
        s.d = e.dz();
        s.c = 1.4f;
        s.c0 = st.mid;
        s.c1 = st.core;
        s.style = st;
        s.seed = e.seed();
        s.emitter = E_CONSTRUCT;
        s.fadeIn = 0;
        s.fadeOut = 10;
        double cx = e.x() + e.dx() * 0.5, cz = e.z() + e.dz() * 0.5;
        double gy = FxWorld.groundY(cx, e.y() + 0.5, cz);
        glyphAt(cx, gy, cz, Math.max(1.5f, Math.max(Math.abs(e.dx()), Math.abs(e.dz())) * 0.6f), st, 30, 0.01f);
    }

    private static void pillar(FxEvent e, FxStyle st) {
        double x = e.x(), y = e.y(), z = e.z();
        float h = Math.max(2, e.scale());
        Shape c = FxWorld.shape(Shape.COLUMN, 14);
        if (c != null) {
            c.x = x;
            c.y = y;
            c.z = z;
            c.a = h;
            c.b = 0.7f;
            c.c0 = alpha(st.mid, 0.8f);
            c.c1 = alpha(st.edge, 0);
            c.c2 = st.core;
            c.fadeIn = 0;
            c.fadeOut = 8;
        }
        ring(x, y + 0.08, z, 0.3f, 2.4f, 0.6f, 0.1f, 9, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
        debris(x, y, z, 1f, n(10), 0.55f, 0.18f);
        dustRing(x, y, z, 1f, n(8), FxStyle.KINETIC, 0.06f);
        sparks(x, y + 0.3, z, n(8), 0.4f, st, 7, 0.02f, 0, 1, 0, 0.8f);
    }

    private static void holo(FxEvent e, FxStyle st) {
        double x = e.x(), y = e.y(), z = e.z();
        int kind = (int) e.aux();
        float dl = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        float nx = dl > 1e-4f ? e.dx() / dl : 0, ny = dl > 1e-4f ? e.dy() / dl : 1, nz = dl > 1e-4f ? e.dz() / dl : 0;
        switch (kind) {
            case 1 -> {
                // turret deploy: a scanline column, a hex ring on the ground
                double gy = FxWorld.groundY(x, y + 0.5, z);
                ring(x, gy + 0.08, z, 0.2f, 1.4f, 0.2f, 0.05f, 14, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
                Shape c = FxWorld.shape(Shape.COLUMN, 12);
                if (c != null) {
                    c.x = x;
                    c.y = gy;
                    c.z = z;
                    c.a = 2.2f;
                    c.b = 0.35f;
                    c.c0 = alpha(st.mid, 0.7f);
                    c.c1 = alpha(st.edge, 0);
                    c.c2 = st.core;
                    c.fadeOut = 6;
                }
                for (int i = 0; i < n(6); i++) {
                    FxWorld.sprite(HEX, x + R.s() * 0.7, gy + R.f(0.2f, 1.6f), z + R.s() * 0.7, 0, 0.02, 0, 10 + R.i(8), 0.35f, 0.2f, alpha(st.mid, 0.9f),
                            alpha(st.edge, 0), FxPool.ADD);
                }
            }
            case 2 -> {
                Shape d = disc(x, y, z, HEX, 0.6f, 0.9f, Math.max(8, e.ticks()), alpha(st.mid, 0.9f), alpha(st.core, 0.9f));
                if (d != null) {
                    d.c = 0.08f;
                    d.flags |= Shape.PULSE;
                }
                ring(x, y, z, 0.6f, 0.3f, 0.06f, 0.04f, Math.max(8, e.ticks()), alpha(st.core, 0.9f), alpha(st.mid, 0.8f), nx, ny, nz);
            }
            default -> {
                // drone launch: a ring of light it flies out of, square motes
                ring(x, y, z, 0.2f, 0.9f, 0.15f, 0.04f, 9, alpha(st.core, 0.9f), alpha(st.mid, 0), nx, ny, nz);
                motes(x, y, z, n(10), 0.3f, st, 0.08f);
                flare(x, y, z, 0.8f, st, 4);
            }
        }
    }

    private static void rail(FxEvent e, FxStyle st, Entity caster, boolean mine) {
        double ax = e.x(), ay = e.y(), az = e.z();
        if (caster != null) {
            Anchors.of(caster, Anchors.HAND, 1f, P);
            ax = P[0];
            ay = P[1];
            az = P[2];
        }
        double bx = e.x() + e.dx(), by = e.y() + e.dy(), bz = e.z() + e.dz();
        float charge = Math.max(0.2f, Math.min(1, e.scale()));
        Shape b = FxWorld.shape(Shape.BEAM, 10);
        if (b != null) {
            b.x = ax;
            b.y = ay;
            b.z = az;
            b.ex = bx;
            b.ey = by;
            b.ez = bz;
            b.a = 0.06f + 0.07f * charge;
            b.c0 = st.mid;
            b.c1 = 0xFFFFFFFF;
            b.fadeIn = 0;
            b.fadeOut = 8;
        }
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int rings = Math.min(n(16), (int) (len / 2.2));
        for (int i = 1; i <= rings; i++) {
            double t = i / (double) (rings + 1);
            // the rings are as wide as the slug's hit line
            Shape r = ring(ax + dx * t, ay + dy * t, az + dz * t, 0.12f, e.aux() > 0 ? e.aux() : 0.6f, 0.12f, 0.03f, 8 + i / 3,
                    alpha(st.core, 0.85f), alpha(st.mid, 0), (float) dx, (float) dy, (float) dz);
            if (r != null) r.fadeIn = 0;
        }
        flare(ax, ay, az, 1.4f, st, 5);
        flare(bx, by, bz, 1.6f * (0.6f + charge), st, 6);
        sparks(bx, by, bz, n(12), 0.4f, st, 6, 0.02f, -dx / len, -dy / len, -dz / len, 0.7f);
        motes(bx, by, bz, n(8), 0.4f, st, 0.1f);
        if (mine) {
            FxScreen.kick(-3f * (0.5f + charge), 0, 0);
            FxScreen.flash(st.mid, 0.1f + 0.08f * charge, 4);
        }
    }

    private static void summon(FxEvent e, FxStyle st, float big) {
        double x = e.x(), z = e.z();
        double y = FxWorld.groundY(x, e.y() + 0.5, z);
        float s = Math.max(0.6f, e.scale()) * big;
        decal(x, y, z, POOL, 2.6f * s, 0xE8100C1C, 70);
        ring(x, y + 0.08, z, 0.2f, 1.4f * s, 0.35f, 0.08f, 10, alpha(st.mid, 0.95f), alpha(st.edge, 0), 0, 1, 0);
        smokePuffs(x, y + 0.6, z, n((int) (8 * s)), 0.4f, 0.8f, st, 0.04f, 20);
        tendrils(x, y, z, n(6), st);
        wisps(x, y + 0.6, z, n(4), 0.3f, FxStyle.SOUL, 0.04f);
    }

    private static void psi(FxEvent e, FxStyle st, Entity caster) {
        Shape s = emitter(e, E_PSI, Math.max(8, e.ticks()), caster, Anchors.EYE);
        if (s == null) return;
        s.style = st;
        float l = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        s.d = l;
    }

    private static void glyph(FxEvent e, FxStyle st, float big) {
        double gy = FxWorld.groundY(e.x(), e.y() + 0.5, e.z());
        glyphAt(e.x(), gy, e.z(), Math.max(1, e.scale()) * big, st, Math.max(10, e.ticks()), 0.025f);
    }

    private static void meteor(FxEvent e, FxStyle st, boolean mine) {
        Entity caster = e.caster() >= 0 ? Minecraft.getInstance().level.getEntity(e.caster()) : null;
        if (caster == null) return;
        int life = Math.max(10, e.ticks());
        Shape t = attach(FxWorld.shape(Shape.TRAIL, life), caster, Anchors.BODY);
        if (t != null) {
            t.a = 0.55f;
            t.c0 = alpha(st.plain() == FxStyle.HEAT ? FxStyle.HEAT.core : st.core, 0.9f);
            t.c1 = alpha(st.mid, 0);
            t.fadeOut = 6;
            t.pushHistory(t.x, t.y, t.z);
        }
        Shape g = attach(disc(0, 0, 0, HALO, 1.6f, 2.4f, life, alpha(st.mid, 0.8f), alpha(st.core, 0.9f)), caster, Anchors.BODY);
        if (g != null) g.flags |= Shape.PULSE;
        Shape em = emitter(e, E_METEOR, life, caster, Anchors.BODY);
        if (em != null) em.style = st;
        if (mine) FxScreen.speedLines(life);
    }

    private static void lift(FxEvent e, FxStyle st) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity target = e.target() >= 0 ? level.getEntity(e.target()) : null;
        if (target == null) return;
        int life = Math.max(8, e.ticks());
        Shape r = attach(ring(0, 0, 0, 0.7f, 0.9f, 0.16f, 0.1f, life, alpha(st.mid, 0.9f), alpha(st.core, 0.8f), 0, 1, 0), target, Anchors.BODY);
        if (r != null) {
            r.flags |= Shape.PULSE | Shape.HOLD;
            r.fadeOut = 6;
        }
        Shape em = emitter(e, E_LIFT, life, target, Anchors.FEET);
        if (em != null) em.style = st;
    }

    private static void ultBeat(FxEvent e, FxStyle st, Minecraft mc) {
        if (mc.player == null) return;
        double d = Math.sqrt(mc.player.distanceToSqr(e.x(), e.y(), e.z()));
        float range = Math.max(8, e.scale());
        if (d > range) return;
        float near = (float) (1 - d / range);
        FxScreen.impactFrame(near > 0.5f ? 4 : 2);
        FxScreen.flash(st.mid, 0.32f * near, 8);
        FxScreen.kick(3f * near, 0, (R.chance(0.5f) ? 1 : -1) * 1.5f * near);
        if (e.caster() == mc.player.getId()) FxScreen.fovPunch(-0.1f, 10);
    }

    // ---- emitters ---------------------------------------------------------------------------------------------------

    /** Per tick for every live shape with an emitter. Reseeds from (seed, age) so all viewers emit the same. */
    static void emit(Shape s) {
        if (!FxWorld.begin(s.x, s.y, s.z, s.seed * 31 + s.age)) return;
        FxStyle st = s.style;
        switch (s.emitter) {
            case E_CHARGE -> {
                if (s.emitParam < 0 || s.age >= s.life - s.fadeOut) return;
                // sparks pulled in toward the hand from a sphere around it
                int k = n(2);
                for (int i = 0; i < k; i++) {
                    double vx = R.s(), vy = R.s(), vz = R.s();
                    double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
                    double r = 1.1 + R.f(0, 0.4f);
                    double px = s.x + vx / l * r, py = s.y + vy / l * r, pz = s.z + vz / l * r;
                    int life = 6;
                    PowerFxParticles.Sprite sp = st.plain() == FxStyle.CHARGE ? BOLT : st.plain() == FxStyle.TECH ? MOTE : SPARK;
                    int p = FxWorld.sprite(sp, px, py, pz, (s.x - px) / life, (s.y - py) / life, (s.z - pz) / life, life, 0.16f, 0.06f, alpha(st.mid, 0.4f),
                            st.core, FxPool.ADD | (sp == SPARK ? FxPool.STRETCH : 0));
                    FxWorld.drag(p, 1f);
                }
                if (st.plain() == FxStyle.KINETIC && s.age % 3 == 0) {
                    int p = FxWorld.sprite(DUST, s.x, s.y - 0.2, s.z, R.s() * 0.02, -0.02, R.s() * 0.02, 12, 0.3f, 0.7f, 0x90B6AC93, 0x00B6AC93,
                            FxPool.LIT | FxPool.ANIM);
                    FxWorld.drag(p, 0.9f);
                }
                if (st.plain() == FxStyle.CHARGE && s.age % 3 == 0) {
                    arc(s.x, s.y, s.z, s.x + R.s() * 0.9, s.y + R.s() * 0.9, s.z + R.s() * 0.9, st, 2, 0.025f);
                }
                if (s.age > 18 && s.age % 6 == 0) {
                    // fully charged: a ring snaps around the hand
                    ring(s.x, s.y, s.z, 0.2f, 0.7f, 0.08f, 0.02f, 5, alpha(st.core, 0.9f), alpha(st.mid, 0), R.s(), R.s(), R.s());
                }
            }
            case E_BEAM -> {
                if (s.age % 2 != 0) return;
                double ex = s.ex, ey = s.ey, ez = s.ez;
                int k = n(2);
                for (int i = 0; i < k; i++) {
                    int p = FxWorld.sprite(st.plain() == FxStyle.HEAT ? EMBER : SPARK, ex, ey, ez, R.s() * 0.12, R.f(0, 0.15f), R.s() * 0.12, 6 + R.i(6), 0.18f,
                            0.05f, st.core, alpha(st.mid, 0), FxPool.ADD | (st.plain() == FxStyle.HEAT ? FxPool.ANIM : FxPool.STRETCH));
                    FxWorld.grav(p, st.plain() == FxStyle.HEAT ? -0.004f : 0.02f);
                }
                {
                    // (every other tick) rings riding down the beam: seen from behind the caster (where the beam itself hides behind the
                    // body) they read as a tunnel of light around the line of fire
                    double bx = ex - s.x, by = ey - s.y, bz = ez - s.z;
                    double bl = Math.sqrt(bx * bx + by * by + bz * bz);
                    if (bl > 2) {
                        double ux = bx / bl, uy = by / bl, uz = bz / bl;
                        for (int i = 0; i < 2; i++) {
                            double at = R.f(1.0f, (float) Math.min(bl - 0.5, 7));
                            Shape rr = ring(s.x + ux * at, s.y + uy * at, s.z + uz * at, 0.25f, 0.75f, 0.14f, 0.04f, 6, alpha(s.c0, 0.9f), alpha(st.edge, 0),
                                    (float) ux, (float) uy, (float) uz);
                            if (rr != null) {
                                rr.vx = ux * 0.6;
                                rr.vy = uy * 0.6;
                                rr.vz = uz * 0.6;
                            }
                        }
                    }
                }
                if (s.age % 4 == 0) {
                    FxWorld.sprite(HALO, ex, ey, ez, 0, 0, 0, 6, 2.6f, 0.8f, alpha(s.c0, 0.8f), alpha(s.c0, 0), FxPool.ADD);
                    // a shock ring where the beam lands and one at the palm, both facing along the beam
                    float bdx = (float) (ex - s.x), bdy = (float) (ey - s.y), bdz = (float) (ez - s.z);
                    ring(ex, ey, ez, 0.2f, 1.1f, 0.18f, 0.05f, 6, alpha(s.c0, 0.9f), alpha(st.edge, 0), bdx, bdy, bdz);
                    ring(s.x, s.y, s.z, 0.1f, 0.55f, 0.1f, 0.03f, 5, alpha(s.c0, 0.9f), alpha(st.edge, 0), bdx, bdy, bdz);
                }
                if (s.age % 10 == 0 && st.plain() == FxStyle.HEAT) {
                    double gy = FxWorld.groundY(ex, ey + 0.2, ez);
                    if (Math.abs(gy - ey) < 0.4) decal(ex, gy, ez, SCORCH, 0.9f, 0xA0201410, 60);
                    smokePuffs(ex, ey, ez, 1, 0.1f, 0.3f, FxStyle.KINETIC, 0.03f, 14);
                }
            }
            case E_CONE -> {
                // a crescent wall of dust thrown up along the whole wave front, left standing as it passes, chunks of the
                // ground and cracks along the path
                float t = s.t(0);
                float r = Mth.lerp(t, s.a, s.b);
                if (r > s.b) return;
                float base = (float) Math.atan2(s.nz, s.nx);
                int k = n(6);
                int dust = alpha(FxWorld.mix(st.accent, 0xFFCFC6B5, 0.35f), 0.9f);
                for (int i = 0; i < k; i++) {
                    float a = base + R.s() * s.c;
                    double px = s.x + Mth.cos(a) * r, pz = s.z + Mth.sin(a) * r;
                    int p = FxWorld.sprite(DUST, px, s.y + R.f(0.1f, 1.4f), pz, Mth.cos(a) * 0.05, R.f(0.02f, 0.06f), Mth.sin(a) * 0.05, 30 + R.i(16),
                            1.0f, R.f(2.4f, 3.4f), dust, alpha(st.accent, 0), FxPool.LIT | FxPool.ANIM);
                    FxWorld.drag(p, 0.9f);
                    FxWorld.floor(p, (float) s.y);
                    FxWorld.spin(p, R.f(0, 6.28f), R.s() * 0.03f);
                }
                if (s.age % 2 == 0) {
                    for (int j = 0; j < 2; j++) {
                        float a = base + R.s() * s.c;
                        debrisAt(s.x + Mth.cos(a) * r, s.y, s.z + Mth.sin(a) * r, 0.5f);
                    }
                    // cracks along the middle and both flanks of the path
                    for (int side = -1; side <= 1; side++) {
                        float a = base + side * s.c * 0.6f;
                        decal(s.x + Mth.cos(a) * r, s.y, s.z + Mth.sin(a) * r, CRACK, 1.8f, 0xC82A2420, 90);
                    }
                }
                if (s.age % 3 == 0) sparks(s.x + Mth.cos(base) * r, s.y + 0.4, s.z + Mth.sin(base) * r, n(4), 0.3f, st, 6, 0.02f, Mth.cos(base), 0.6, Mth.sin(base), 0.6f);
            }
            case E_GHOSTS -> {
                Entity e = followed(s);
                if (e != null && s.age % Math.max(1, s.emitParam) == 0) FxAfterimages.capture(e, 10, st.mid, 0);
            }
            case E_TRAIL -> {
                if (s.emitParam == 1) embers(s.x, s.y + 0.1, s.z, n(2), 0.2f, FxStyle.HEAT, 0.006f);
                else if (s.emitParam == 2 && s.age % 3 == 0) {
                    int p = FxWorld.sprite(WIND, s.x + R.s() * 0.6, s.y + R.f(0, 1.6f), s.z + R.s() * 0.6, 0, -0.03, 0, 10, 0.6f, 0.9f, alpha(st.core, 0.7f),
                            alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                    FxWorld.spin(p, R.f(0, 6.28f), 0.05f);
                } else if (s.age % 2 == 0) motes(s.x, s.y + 0.5, s.z, 1, 0.3f, st, 0.02f);
            }
            case E_DOME -> {
                if (s.age % 12 == 0) {
                    float r = s.b;
                    ring(s.x, (s.flags & Shape.HEMI) != 0 ? s.y + 0.08 : FxWorld.groundY(s.x, s.y, s.z) + 0.08, s.z, r * 0.9f, r * 1.05f, 0.3f, 0.05f, 10,
                            alpha(st.core, 0.6f), alpha(st.mid, 0), 0, 1, 0);
                }
                if (s.age % 3 == 0) {
                    // motes drifting up inside the dome
                    float a = R.f(0, 6.28f), rr = s.b * R.f(0, 0.9f);
                    int p = FxWorld.sprite(st.plain() == FxStyle.SPIRIT ? WISP : MOTE, s.x + Mth.cos(a) * rr, s.y + R.f(0, 0.5f), s.z + Mth.sin(a) * rr, 0, 0.03, 0,
                            20, 0.25f, 0.08f, alpha(st.core, 0.8f), alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                    FxWorld.drag(p, 0.98f);
                }
            }
            case E_VORTEX -> {
                boolean out = s.emitParam == 1;
                float r = s.d;
                if (s.age % 8 == 0) {
                    float tilt = R.s() * 0.5f;
                    ring(s.x, s.y, s.z, out ? 0.4f : r, out ? r : 0.3f, 0.12f, 0.2f, 14, alpha(st.mid, 0.85f), alpha(st.core, 0.4f), tilt, 1, R.s() * 0.5f);
                }
                int k = n(3);
                for (int i = 0; i < k; i++) {
                    double vx = R.s(), vy = R.s() * 0.6, vz = R.s();
                    double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4;
                    double px = s.x + vx / l * r, py = s.y + vy / l * r, pz = s.z + vz / l * r;
                    int life = 10;
                    double sx = out ? (px - s.x) / life * 0.6 : (s.x - px) / life, sy = out ? (py - s.y) / life * 0.6 : (s.y - py) / life,
                            sz = out ? (pz - s.z) / life * 0.6 : (s.z - pz) / life;
                    double ox = out ? s.x : px, oy = out ? s.y : py, oz = out ? s.z : pz;
                    // a swirl: add a sideways component
                    double tx = -sz * 0.6, tz = sx * 0.6;
                    int p = FxWorld.sprite(SPARK, ox, oy, oz, sx + tx, sy, sz + tz, life, 0.16f, 0.06f, alpha(st.mid, 0.5f), st.core, FxPool.ADD | FxPool.STRETCH);
                    FxWorld.drag(p, 1f);
                }
                if (s.age % 7 == 0 && !out) {
                    double gy = FxWorld.groundY(s.x, s.y, s.z);
                    double a = R.f(0, 6.28f);
                    double px = s.x + Math.cos(a) * r, pz = s.z + Math.sin(a) * r;
                    BlockState g = groundBlock(px, gy + 0.2, pz);
                    if (g != null) FxDebris.spawn(g, px, gy + 0.3, pz, (s.x - px) * 0.08, 0.35 + (s.y - gy) * 0.05, (s.z - pz) * 0.08, 0.16f, 22, (float) gy);
                }
                if (st.inky() && s.age % 4 == 0) smokePuffs(s.x, s.y, s.z, 1, r * 0.5f, 0.6f, st, 0.02f, 14);
            }
            case E_AURA -> aura(s, st);
            case E_CRACKLE -> {
                // electricity crawling over the ground where lightning landed
                if (s.age % 2 != 0) return;
                double px = s.x + R.s() * 1.6, pz = s.z + R.s() * 1.6;
                int b = FxWorld.sprite(BOLT, px, s.y + 0.15, pz, 0, 0, 0, 3, R.f(0.6f, 1.1f), 0.3f, st.core, alpha(st.mid, 0), FxPool.ADD);
                FxWorld.spin(b, R.f(0, 6.28f), 0);
                if (s.age % 6 == 0) arc(s.x, s.y + 0.2, s.z, px, s.y + 0.1, pz, st, 3, 0.03f);
            }
            case E_FLURRY -> {
                Entity e = followed(s);
                if (e == null) return;
                float yaw = e.getYRot() * Mth.DEG_TO_RAD, pitch = e.getXRot() * Mth.DEG_TO_RAD;
                double lx = -Mth.sin(yaw) * Mth.cos(pitch), ly = -Mth.sin(pitch), lz = Mth.cos(yaw) * Mth.cos(pitch);
                double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
                int k = n(2);
                float halfRad = (float) Math.toRadians(Math.min(75, s.d));
                for (int i = 0; i < k; i++) {
                    double f = R.f(1.0f, s.b), side = R.s() * Math.tan(halfRad) * f, up = R.s() * 0.5;
                    double px = s.x + lx * f + rx * side, py = s.y - 0.3 + ly * f + up, pz = s.z + lz * f + rz * side;
                    FxWorld.sprite(PALM, px, py, pz, lx * 0.15, ly * 0.15, lz * 0.15, 3, 0.5f, 1.1f, alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD);
                }
                if (s.age % 3 == 0) {
                    double f = R.f(1.4f, 2.2f);
                    flare(s.x + lx * f + rx * R.s() * 0.6, s.y - 0.3 + ly * f + R.s() * 0.4, s.z + lz * f + rz * R.s() * 0.6, 0.6f, st, 3);
                }
                if (s.age % 5 == 0) ring(s.x + lx * 1.5, s.y - 0.3 + ly * 1.5, s.z + lz * 1.5, 0.3f, 1.3f, 0.25f, 0.04f, 6, alpha(st.core, 0.8f),
                        alpha(st.mid, 0), (float) lx, (float) ly, (float) lz);
            }
            case E_HEAL -> {
                if (s.age % 2 != 0) return;
                float a = R.f(0, 6.28f), rr = R.f(0.2f, 0.7f);
                int p = FxWorld.sprite(R.chance(0.5f) ? MOTE : WISP, s.x + Mth.cos(a) * rr, s.y + R.f(0, 0.4f), s.z + Mth.sin(a) * rr, 0, R.f(0.03f, 0.06f), 0,
                        18 + R.i(8), 0.22f, 0.06f, alpha(st.core, 0.95f), alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                FxWorld.drag(p, 0.97f);
            }
            case E_FIRE -> {
                if (s.age % 2 != 0) return;
                double t = R.f();
                double px = Mth.lerp(t, s.x, s.ex), py = Mth.lerp(t, s.y, s.ey), pz = Mth.lerp(t, s.z, s.ez);
                embers(px, py + 0.15, pz, n(2), 0.25f, FxStyle.HEAT, 0.008f);
                if (s.age % 6 == 0) smokePuffs(px, py + 0.4, pz, 1, 0.2f, 0.4f, FxStyle.KINETIC, 0.02f, 16);
            }
            case E_EXHAUST -> {
                Entity e = followed(s);
                if (e == null) return;
                double tx = s.ex - s.x, ty = s.ey - s.y, tz = s.ez - s.z;
                double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
                double bx = l > 1e-3 ? -tx / l : 0, by = l > 1e-3 ? -ty / l : -1, bz = l > 1e-3 ? -tz / l : 0;
                int k = n(3);
                for (int i = 0; i < k; i++) {
                    int p = FxWorld.sprite(i == 0 ? GLOW : EMBER, s.x + R.s() * 0.15, s.y, s.z + R.s() * 0.15, bx * 0.35 + R.s() * 0.05, by * 0.35 + R.s() * 0.05,
                            bz * 0.35 + R.s() * 0.05, 6 + R.i(3), i == 0 ? 0.6f : 0.25f, 0.05f, i == 0 ? alpha(st.core, 0.9f) : FxStyle.HEAT.core,
                            alpha(i == 0 ? st.mid : FxStyle.HEAT.edge, 0), FxPool.ADD | (i == 0 ? 0 : FxPool.ANIM));
                    FxWorld.drag(p, 0.85f);
                }
                if (s.age % 2 == 0) smokePuffs(s.x, s.y - 0.2, s.z, 1, 0.1f, 0.35f, FxStyle.KINETIC, 0.02f, 12);
            }
            case E_CONSTRUCT -> {
                // block updates trail the event, and pillars grow over time: look again a few times
                if (s.age == 1 || s.age == 3 || s.age == 6 || s.age == 10 || s.age == 16 || s.age == 24) scanConstructs(s);
                if (s.blockCount > 0) {
                    int k = n(3);
                    for (int i = 0; i < k; i++) FxSeams.spark(s);
                }
            }
            case E_PSI -> {
                Entity e = followed(s);
                float len = s.d;
                if (len > 0.5f) {
                    if (s.age % 3 != 0) return;
                    double tx = s.ex - s.x, ty = s.ey - s.y, tz = s.ez - s.z;
                    double l = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-4;
                    float sp = 0.7f;
                    Shape r = ring(s.x, s.y, s.z, 0.25f, 0.7f, 0.12f, 0.04f, Math.max(4, (int) (l / sp)), alpha(st.core, 0.9f), alpha(st.mid, 0.2f),
                            (float) tx, (float) ty, (float) tz);
                    if (r != null) {
                        r.vx = tx / l * sp;
                        r.vy = ty / l * sp;
                        r.vz = tz / l * sp;
                        r.fadeIn = 1;
                    }
                    if (s.age % 6 == 0) {
                        int p = FxWorld.sprite(RUNE, s.ex + R.s() * 0.4, s.ey + 0.6 + R.f(0, 0.3f), s.ez + R.s() * 0.4, 0, 0.02, 0, 12, 0.35f, 0.2f,
                                alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD);
                        FxWorld.spin(p, 0, 0.1f);
                    }
                } else if (e != null && s.age % 5 == 0) {
                    ring(s.x, s.y, s.z, 0.3f, 2.6f, 0.12f, 0.04f, 12, alpha(st.core, 0.8f), alpha(st.mid, 0), 0, 1, 0);
                }
            }
            case E_METEOR -> {
                int k = n(3);
                for (int i = 0; i < k; i++) {
                    int p = FxWorld.sprite(st.plain() == FxStyle.HEAT || s.style.plain() == FxStyle.GRAVITY ? EMBER : SPARK, s.x + R.s() * 0.4, s.y + R.s() * 0.6, s.z + R.s() * 0.4,
                            R.s() * 0.05, R.f(0.1f, 0.3f), R.s() * 0.05, 8 + R.i(4), 0.25f, 0.05f, st.core, alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                    FxWorld.drag(p, 0.9f);
                }
                if (s.age % 2 == 0) smokePuffs(s.x, s.y + 0.5, s.z, 1, 0.3f, 0.7f, st, 0.02f, 16);
            }
            case E_LIFT -> {
                if (s.age % 2 != 0) return;
                float a = R.f(0, 6.28f);
                int p = FxWorld.sprite(MOTE, s.x + Mth.cos(a) * 0.6, s.y, s.z + Mth.sin(a) * 0.6, -Mth.sin(a) * 0.05, R.f(0.04f, 0.09f), Mth.cos(a) * 0.05, 14,
                        0.2f, 0.06f, alpha(st.core, 0.9f), alpha(st.mid, 0), FxPool.ADD);
                FxWorld.drag(p, 0.97f);
                if (s.age % 6 == 0) {
                    int d = FxWorld.sprite(DUST, s.x + R.s() * 0.5, s.y, s.z + R.s() * 0.5, 0, 0.05, 0, 14, 0.3f, 0.9f, 0x80B6AC93, 0x00B6AC93,
                            FxPool.LIT | FxPool.ANIM);
                    FxWorld.drag(d, 0.95f);
                }
            }
            default -> {
            }
        }
    }

    private static void debrisAt(double x, double y, double z, float up) {
        BlockState g = groundBlock(x, y + 0.3, z);
        if (g != null) FxDebris.spawn(g, x, y + 0.2, z, R.s() * 0.06, up * R.f(0.7f, 1.2f), R.s() * 0.06, R.f(0.12f, 0.2f), 26 + R.i(10), (float) y);
    }

    private static Entity followed(Shape s) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || s.follow < 0 ? null : level.getEntity(s.follow);
    }

    /** The aura looks, per tick (s.x/y/z = the entity's feet). */
    private static void aura(Shape s, FxStyle st) {
        Entity e = followed(s);
        if (e == null) return;
        FxKind.Aura aura = AURAS[Math.max(0, Math.min(AURAS.length - 1, s.emitParam))];
        double x = s.x, y = s.y, z = s.z;
        float h = e.getBbHeight();
        switch (aura) {
            case FLOW -> {
                if (s.age % 10 == 0) ring(x, y + h * 0.55, z, 0.4f, 1.6f, 0.12f, 0.03f, 10, alpha(0xFFFFFFFF, 0.7f), alpha(st.mid, 0), 0, 1, 0);
                if (s.age % 7 == 0) FxAfterimages.capture(e, 8, st.mid, 0);
                if (s.age % 2 == 0) motes(x, y + R.f(0.2f, h), z, 1, 0.4f, FxStyle.NEUTRAL, 0.01f);
            }
            case BRACE -> {
                if (s.age % 10 == 0) ring(x, y + 0.06, z, 0.6f, 1.2f, 0.3f, 0.1f, 10, alpha(st.mid, 0.8f), alpha(st.edge, 0), 0, 1, 0);
                if (s.age % 4 == 0) {
                    int p = FxWorld.sprite(DUST, x + R.s() * 0.6, y + 0.1, z + R.s() * 0.6, 0, 0.01, 0, 14, 0.3f, 0.7f, 0x70B6AC93, 0x00B6AC93, FxPool.LIT | FxPool.ANIM);
                    FxWorld.floor(p, (float) y);
                }
            }
            case NANITE, BARRIER -> {
                if (s.age % 3 == 0) motes(x, y + R.f(0.2f, h), z, 1, 0.45f, st, 0.02f);
            }
            case OVERLOAD -> {
                if (s.age % 3 == 0) {
                    int b = FxWorld.sprite(BOLT, x + R.s() * 0.5, y + R.f(0.2f, h), z + R.s() * 0.5, 0, 0, 0, 3, 0.5f, 0.4f, FxStyle.CHARGE.core,
                            alpha(FxStyle.CHARGE.mid, 0), FxPool.ADD);
                    FxWorld.spin(b, R.f(0, 6.28f), 0);
                }
                if (s.age % 2 == 0) embers(x, y + h * 0.5, z, 1, 0.4f, FxStyle.HEAT, 0.006f);
            }
            case READ, FORESIGHT -> {
                if (s.age % 12 == 0) ring(x, y + e.getEyeHeight(), z, 0.3f, 1.2f, 0.08f, 0.03f, 12, alpha(st.core, 0.8f),
                        alpha(aura == FxKind.Aura.FORESIGHT ? st.accent : st.mid, 0), 0, 1, 0);
                if (s.age % 3 == 0) {
                    int p = FxWorld.sprite(MOTE, x + R.s() * 0.5, y + R.f(0.4f, h + 0.2f), z + R.s() * 0.5, 0, 0.02, 0, 16, 0.18f, 0.05f,
                            aura == FxKind.Aura.FORESIGHT ? st.accent : st.core, alpha(st.mid, 0), FxPool.ADD);
                    FxWorld.drag(p, 0.97f);
                }
            }
            case THIRD_EYE -> {
                if (s.age % 16 == 0) ring(x, y + e.getEyeHeight() + 0.1, z, 0.2f, 0.9f, 0.06f, 0.02f, 12, alpha(st.core, 0.8f), alpha(st.mid, 0), 0, 1, 0);
            }
            case TWIN -> {
                if (s.age % 12 == 0) FxAfterimages.capture(e, 10, FxStyle.SOUL.mid, 0);
                if (s.age % 4 == 0) wisps(x, y + h * 0.6, z, 1, 0.3f, st, 0.02f);
            }
            case CLOAK -> {
                if (s.age % 8 == 0) {
                    int p = FxWorld.sprite(SMOKE, x + R.s() * 0.3, y + 0.1, z + R.s() * 0.3, 0, 0.01, 0, 16, 0.3f, 0.6f, 0x9A1A1428, 0x00231A3B,
                            FxPool.BRIGHT | FxPool.ANIM);
                    FxWorld.drag(p, 0.95f);
                }
            }
            case HOVER -> {
                if (s.age % 6 == 0) ring(x, y - 0.1, z, 0.2f, 0.9f, 0.18f, 0.04f, 10, alpha(st.core, 0.8f), alpha(st.mid, 0), 0, 1, 0);
                if (s.age % 5 == 0) {
                    int p = FxWorld.sprite(WIND, x + R.s() * 0.5, y + R.f(0, 0.8f), z + R.s() * 0.5, 0, -0.03, 0, 10, 0.5f, 0.8f, alpha(st.core, 0.7f),
                            alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                    FxWorld.spin(p, R.f(0, 6.28f), 0.06f);
                }
            }
            case FLIGHT -> {
                if (s.age % 4 == 0) {
                    int p = FxWorld.sprite(WIND, x + R.s() * 0.6, y + R.f(0, h), z + R.s() * 0.6, 0, 0, 0, 10, 0.5f, 0.9f, alpha(st.core, 0.6f),
                            alpha(st.mid, 0), FxPool.ADD | FxPool.ANIM);
                    FxWorld.spin(p, R.f(0, 6.28f), 0.05f);
                }
            }
            case MARK -> {
            }
            default -> {
                if (s.age % 4 == 0) motes(x, y + R.f(0.1f, h), z, 1, 0.45f, st, 0.015f);
            }
        }
    }

    /** Fills a seams shape with the construct blocks now standing in its box. */
    private static void scanConstructs(Shape s) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        int x0 = Mth.floor(Math.min(s.ex, s.ex + s.a)), x1 = Mth.floor(Math.max(s.ex, s.ex + s.a));
        int y0 = Mth.floor(Math.min(s.ey, s.ey + s.b)), y1 = Mth.floor(Math.max(s.ey, s.ey + s.b));
        int z0 = Mth.floor(Math.min(s.ez, s.ez + s.d)), z1 = Mth.floor(Math.max(s.ez, s.ez + s.d));
        int cap = FxWorld.minimal() ? 24 : s.blocks.length;
        s.blockCount = 0;
        for (int x = x0; x <= x1 && s.blockCount < cap; x++) {
            for (int y = y0; y <= y1 && s.blockCount < cap; y++) {
                for (int z = z0; z <= z1 && s.blockCount < cap; z++) {
                    MUT.set(x, y, z);
                    BlockState b = level.getBlockState(MUT);
                    if (b.isAir()) continue;
                    String path = BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
                    if (path.contains("construct")) s.blocks[s.blockCount++] = BlockPos.asLong(x, y, z);
                }
            }
        }
    }

    // ---- the ladder (SPEC 34.6) ---------------------------------------------------------------------------------------

    /** Each family's mote for its climb: Flight's feathers, Spirit's petals, Architect's plates... */
    private static final Sprite[] CLIMB = {SHARD, WIND, FEATHER, EMBER, WISP, RUNE, PLATE, HEX, PETAL};

    /**
     * A power climbed a step: its family's seal opens under the caster, motes of the family spiral up the body and a ring runs
     * out over the ground. aux 0 is the short sting in a fight; 1 the moment when calm, with a column of light; level 5 adds a
     * dome, a second ring and the seal drawn twice the size (in the awakened palette, LadderFx.restyle).
     */
    private static void evolve(FxEvent e, FxStyle st, @org.jspecify.annotations.Nullable Entity caster) {
        int level = Fx.levelOf(e);
        dev.awaken.powers.PowerType type = dev.awaken.powers.PowerRegistry.byIndex(e.power() - 1);
        int family = type == null ? 0 : type.family.ordinal();
        boolean full = e.aux() >= 1;
        boolean top = full && level >= 5;
        double x = e.x(), y = e.y(), z = e.z();
        if (caster != null) {
            x = caster.getX();
            y = caster.getY();
            z = caster.getZ();
        }
        double gy = FxWorld.groundY(x, y + 0.5, z) + 0.035;

        Shape seal = FxWorld.shape(Shape.SIGIL, full ? (top ? 64 : 46) : 20);
        if (seal != null) {
            seal.x = x;
            seal.y = gy;
            seal.z = z;
            seal.sprite = SIGIL.index();
            seal.frame = family;
            seal.a = 0.3f;
            seal.b = full ? (top ? 3.4f : 2.3f) : 1.3f;
            seal.c = full ? 0.035f : 0.06f;
            seal.c0 = alpha(st.mid, 0.95f);
            seal.c1 = alpha(st.core, 0.9f);
            seal.flags |= Shape.HOLD | Shape.PULSE;
            seal.fadeIn = 3;
            seal.fadeOut = full ? 16 : 7;
            if (caster != null) attach(seal, caster, Anchors.FEET);
        }

        // the family's motes spiral up the body
        Sprite mote = CLIMB[Math.min(family, CLIMB.length - 1)];
        boolean lit = !mote.additive();
        int count = n(full ? (top ? 34 : 22) : 10);
        float height = full ? 2.2f : 1.6f;
        for (int i = 0; i < count; i++) {
            float t = i / (float) Math.max(1, count - 1);
            double ang = i * 2.39996 + R.f(-0.2f, 0.2f);
            double r = 0.75 - 0.35 * t;
            double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r, py = y + 0.1 + t * height * 0.5;
            double tx = -Math.sin(ang) * 0.05, tz = Math.cos(ang) * 0.05;
            int c0 = lit ? 0xFFFFFFFF : FxWorld.mix(st.core, st.mid, 0.35f + 0.3f * t);
            int c1 = lit ? 0x00FFFFFF : alpha(st.edge, 0);
            int p = FxWorld.sprite(mote, px, py, pz, tx, R.f(0.05f, 0.09f), tz, 18 + R.i(10) + (full ? 8 : 0), R.f(0.16f, 0.26f), 0.05f, c0, c1,
                    lit ? FxPool.LIT : FxPool.ADD);
            FxWorld.drag(p, 0.95f);
            FxWorld.spin(p, R.f(0, 6.28f), R.f(-0.2f, 0.2f));
        }

        // a ring runs out over the ground
        Shape ring = ring(x, gy + 0.02, z, 0.4f, full ? (top ? 6f : 4.2f) : 2.4f, 0.5f, 0.08f, full ? 16 : 10, alpha(st.core, 0.95f),
                alpha(st.edge, 0), 0, 1, 0);
        if (ring != null) ring.fadeIn = 0;
        flare(x, y + 1.1, z, full ? 0.9f : 0.5f, st, full ? 9 : 6);
        if (!full) return;

        Shape col = FxWorld.shape(Shape.COLUMN, top ? 34 : 26);
        if (col != null) {
            col.x = x;
            col.y = gy;
            col.z = z;
            col.a = top ? 10f : 6f;
            col.b = top ? 0.7f : 0.45f;
            col.c0 = alpha(st.core, 0.9f);
            col.c1 = alpha(st.edge, 0.15f);
            col.fadeIn = 3;
            col.fadeOut = 16;
            col.seed = e.seed();
        }
        if (!top) return;
        Shape dome = FxWorld.shape(Shape.SPHERE, 30);
        if (dome != null) {
            dome.x = x;
            dome.y = gy;
            dome.z = z;
            dome.a = 0.8f;
            dome.b = 3.6f;
            dome.c = 3f;
            dome.d = 0.08f;
            dome.c0 = alpha(st.mid, 0.75f);
            dome.c1 = alpha(st.edge, 0.08f);
            dome.flags |= Shape.HEMI | Shape.RIM;
            dome.fadeIn = 2;
            dome.fadeOut = 14;
        }
        Shape late = ring(x, gy + 0.03, z, 0.6f, 7.5f, 0.35f, 0.05f, 18, alpha(st.accent, 0.9f), alpha(st.edge, 0), 0, 1, 0);
        if (late != null) late.age = -8;
    }
}
