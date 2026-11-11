package dev.awaken.powers.client.fx;

import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxPool;
import dev.awaken.powers.fx.FxStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import static dev.awaken.powers.client.fx.FxWorld.R;
import static dev.awaken.powers.client.fx.FxWorld.alpha;
import static dev.awaken.powers.client.fx.FxWorld.n;
import static dev.awaken.powers.fx.PowerFxParticles.*;

/**
 * A look of its own for a power (SPEC §32, "Signatures"; the film of 10 Nov 2026 showed Thunderclap, Frost Nova,
 * Telekinesis and Spirit Palm all ending as a thin ring, a flash and a number, and Barrier and Mend showing nothing in first
 * person). After a power's shared recipe has drawn, its signature adds the one thing only that power has: frost that crawls
 * out along the ground and stands up as crystals, a clap that sends lightning along the floor, a handprint that goes through
 * the body, a plate of hard light that turns with your look. Things that take time are jobs here, stepped each client tick.
 * All of it is drawn, never real, inside the effect budget, and never decides anything.
 */
final class Signatures {
    private Signatures() {}

    private static final int J_RIME = 1, J_SHOCK = 2, J_GRIP = 3, J_THROUGH = 4, J_PLATE = 5, J_PETALS = 6, J_INK = 7, J_BRAND = 8, J_SEAR = 9,
            J_LANCES = 10, J_DRAFT = 11, J_BREATH = 12, J_ROOT = 13;

    /** A signature that takes time: what, where, how big, whose, how old. */
    private static final class Job {
        int kind, age, life, caster = -1, target = -1, seed, n;
        double x, y, z;
        float dx, dy, dz, radius, h;
        boolean full;
        FxStyle style = FxStyle.NEUTRAL;
        String power = "";
    }

    private static final Job[] JOBS = new Job[32];

    static {
        for (int i = 0; i < JOBS.length; i++) JOBS[i] = new Job();
    }

    private static @Nullable Job job(int kind, FxEvent e, FxStyle st, String power, int life, float radius) {
        Job free = null;
        for (Job j : JOBS) {
            if (j.kind == 0) {
                free = j;
                break;
            }
        }
        if (free == null) return null;
        free.kind = kind;
        free.age = 0;
        free.life = life;
        free.caster = e.caster();
        free.target = e.target();
        free.seed = e.seed();
        free.x = e.x();
        free.y = e.y();
        free.z = e.z();
        free.dx = e.dx();
        free.dy = e.dy();
        free.dz = e.dz();
        free.radius = radius;
        free.n = 0;
        free.h = 0;
        free.full = e.has(FxEvent.FULL);
        free.style = st;
        free.power = power;
        return free;
    }

    static void reset() {
        for (Job j : JOBS) j.kind = 0;
    }

    /** A power that sat on its caster ended early (a shield broke): its signature goes with it. */
    static void end(int caster, String power) {
        for (Job j : JOBS) if (j.kind != 0 && j.caster == caster && j.power.equals(power)) j.life = Math.min(j.life, j.age + 2);
    }

    /** After the shared recipe: the power's own layer for this beat, if it has one. */
    static void add(FxEvent e, FxStyle st, @Nullable Entity caster, boolean mine) {
        if (e.power() <= 0 || e.has(FxEvent.RIDER) || FxWorld.minimal()) return;
        PowerType type = PowerRegistry.byIndex(e.power() - 1);
        if (type == null) return;
        String power = type.path;
        FxKind kind = e.kind();
        switch (power) {
            case "frost_nova" -> {
                if (kind == FxKind.FROST) job(J_RIME, e, st, power, 10, Math.max(2f, e.scale()));
            }
            case "thunderclap" -> {
                if (kind == FxKind.RING && e.has(FxEvent.REACH)) shock(e, st);
            }
            case "telekinesis" -> {
                if (kind == FxKind.LIFT) job(J_GRIP, e, st, power, Math.max(8, e.ticks()), 1f);
            }
            case "spirit_palm" -> {
                if (kind == FxKind.PALM) through(e, st, power);
                else if (kind == FxKind.ECHO) exit(e, st);
            }
            case "barrier" -> {
                if (kind == FxKind.DOME && e.has(FxEvent.FOLLOW)) job(J_PLATE, e, st, power, Math.max(20, e.ticks()), 1f);
                else if (kind == FxKind.CHARGE_END) end(e.caster(), power);
            }
            case "mend" -> {
                if (kind == FxKind.HEAL && caster != null) job(J_PETALS, e, st, power, Math.min(40, Math.max(14, e.ticks())), 0.6f);
            }
            case "shadow_step" -> {
                if (kind == FxKind.BLINK) ink(e, st, caster, mine);
            }
            case "brand" -> {
                if (kind == FxKind.AURA && Math.round(e.aux()) == FxKind.Aura.BRAND.ordinal()) branded(e, st, power);
                else if (kind == FxKind.CHARGE_END) end(e.caster(), power);
                else if (kind == FxKind.NOVA) brandBurst(e, st, mine);
                else if (kind == FxKind.GLYPH) job(J_SEAR, e, st, power, Math.max(10, e.ticks()), Math.max(0.6f, e.scale()));
            }
            case "hoarfrost_lances" -> {
                if (kind == FxKind.AURA && Math.round(e.aux()) == FxKind.Aura.LANCES.ordinal()) {
                    end(e.caster(), power);
                    Job j = job(J_LANCES, e, st, power, Math.max(20, e.ticks()), 1f);
                    if (j != null) j.n = Math.max(1, Math.min(8, Math.round(e.scale())));
                } else if (kind == FxKind.CHARGE_END) {
                    end(e.caster(), power);
                } else if (kind == FxKind.BOLT) {
                    lanceFlies(e, st);
                }
            }
            case "updraft" -> {
                if (kind == FxKind.GLYPH) {
                    Job j = job(J_DRAFT, e, st, power, Math.max(20, e.ticks()), Math.max(1f, e.scale()));
                    if (j != null) j.h = Math.max(4f, e.aux());
                }
            }
            case "backdraft" -> {
                if (kind == FxKind.TETHER) drawnIn(e, st);
                else if (kind == FxKind.BREATH) {
                    Job j = job(J_BREATH, e, st, power, Math.max(6, e.ticks()), Math.max(3f, e.scale()));
                    if (j != null) {
                        j.h = Math.max(10f, e.aux());
                        if (mine) FxScreen.flash(st.mid, j.full ? 0.12f : 0.06f, 8);
                    }
                }
            }
            case "thornwake" -> {
                if (kind == FxKind.TETHER) rootTakes(e, st);
                else if (kind == FxKind.RING && e.has(FxEvent.REACH)) groundWakes(e, st);
                else if (kind == FxKind.PILLAR) elderRoot(e, st);
            }
            default -> {
            }
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Job j : JOBS) {
            if (j.kind == 0) continue;
            if (FxWorld.begin(j.x, j.y, j.z, j.seed + j.age * 7919)) {
                switch (j.kind) {
                    case J_RIME -> rime(j);
                    case J_GRIP -> grip(j, mc);
                    case J_THROUGH -> throughStep(j);
                    case J_PLATE -> plate(j, mc);
                    case J_PETALS -> petals(j, mc);
                    case J_BRAND -> brandStep(j, mc);
                    case J_SEAR -> sear(j);
                    case J_LANCES -> lances(j, mc);
                    case J_DRAFT -> draft(j);
                    case J_BREATH -> breath(j);
                    case J_ROOT -> rootGrows(j);
                    default -> {
                    }
                }
            }
            if (++j.age >= j.life) j.kind = 0;
        }
    }

    // ---- Break finishers: each family ends it its own way -------------------------------------------------------------

    /**
     * A power cast into a stance just broken (FxKind.FINISHER): the family's own ending on the body, and for the caster a
     * held beat. Nine looks, by the power's family; the style of the power colours it.
     */
    static void finisher(FxEvent e, FxStyle st, @Nullable Entity caster, boolean mine) {
        PowerType type = e.power() > 0 ? PowerRegistry.byIndex(e.power() - 1) : null;
        if (type == null) return;
        double x = e.x(), y = e.y(), z = e.z();
        float l = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        float dx = l > 1e-4f ? e.dx() / l : 0, dy = l > 1e-4f ? e.dy() / l : 0, dz = l > 1e-4f ? e.dz() / l : 1;
        double gy = FxWorld.groundY(x, y, z);
        FxRecipes.flare(x, y, z, 2.2f, st, 6);
        switch (type.family) {
            case KINETIC -> {
                // it goes straight through: a shock ring across the blow, the ground breaking under it
                FxRecipes.ring(x, y, z, 0.3f, 3.2f, 0.5f, 0.1f, 9, alpha(st.core, 1f), alpha(st.mid, 0), dx, dy, dz);
                FxRecipes.debris(x, gy + 0.1, z, 1.4f, n(14), 0.5f, 0.3f);
                FxRecipes.decal(x, gy + 0.02, z, CRACK, 3.2f, 0xD0FFFFFF, 160);
                FxRecipes.dustRing(x, gy, z, 1.6f, n(18), st, 0.16f);
            }
            case VELOCITY -> {
                // you were already past it: cuts through the body from every side
                for (int i = 0; i < n(7); i++) {
                    float a = R.f() * Mth.TWO_PI, h = R.f(-0.5f, 0.6f);
                    FxRecipes.arc(x + Mth.cos(a) * 1.8, y + h, z + Mth.sin(a) * 1.8, x - Mth.cos(a) * 1.8, y - h * 0.6, z - Mth.sin(a) * 1.8, st, 4 + R.i(3), 0.05f);
                }
                if (caster != null) FxAfterimages.capture(caster, 14, st.mid, 0);
                FxRecipes.sparks(x, y, z, n(14), 0.5f, st, 7, 0.01f, dx, dy, dz, 0.4f);
            }
            case FLIGHT -> {
                // the ground lets go of it
                FxRecipes.ring(x, gy + 0.1, z, 0.4f, 2.6f, 0.4f, 0.1f, 12, alpha(st.core, 0.9f), alpha(st.mid, 0), 0, 1, 0);
                for (int i = 0; i < 3; i++) FxRecipes.ring(x, gy + 0.6 + i * 0.9, z, 1.4f - i * 0.3f, 0.3f, 0.2f, 0.05f, 10 + i * 3, alpha(st.mid, 0.8f), alpha(st.core, 0), 0, 1, 0);
                FxRecipes.debris(x, gy + 0.1, z, 1.2f, n(10), 0.7f, 0.05f);
                FxRecipes.motes(x, y + 0.6, z, n(14), 1.0f, st, 0.05f);
            }
            case ENERGY -> {
                // the light goes in and comes out of its back
                FxRecipes.arc(x - dx * 1.5, y - dy * 1.5, z - dz * 1.5, x + dx * 7, y + dy * 7, z + dz * 7, st, 7, 0.22f);
                FxRecipes.flare(x + dx * 1.2, y + dy * 1.2, z + dz * 1.2, 2.8f, st, 7);
                FxRecipes.sparks(x + dx * 0.8, y + dy * 0.8, z + dz * 0.8, n(20), 0.7f, st, 9, 0.01f, dx, dy, dz, 1.6f);
                FxRecipes.decal(x + dx * 2.5, gy + 0.02, z + dz * 2.5, SCORCH, 2.4f, 0xC0FFFFFF, 160);
            }
            case SHADOW -> {
                // your shadow finishes it a beat after you
                if (caster != null) FxAfterimages.capture(caster, 20, st.edge, 0);
                FxRecipes.tendrils(x, gy + 0.1, z, n(8), st);
                FxRecipes.smokePuffs(x, y, z, n(10), 0.8f, 1.2f, st, 0.03f, 26);
                FxRecipes.ring(x, y, z, 2.2f, 0.2f, 0.3f, 0.05f, 8, alpha(st.mid, 0.9f), alpha(st.edge, 0), dx, dy, dz);
            }
            case MIND -> {
                // it stops: rings closing on its head
                double hy = y + 0.9;
                for (int i = 0; i < 4; i++) FxRecipes.ring(x, hy, z, 2.2f - i * 0.4f, 0.25f, 0.12f, 0.04f, 8 + i * 3, alpha(st.mid, 0.9f), alpha(st.core, 0.2f), 0, 1, 0);
                FxRecipes.glyphAt(x, gy + 0.05, z, 1.8f, st, 30, 0.06f);
                FxRecipes.wisps(x, hy, z, n(8), 0.5f, st, 0.02f);
            }
            case ARCHITECT -> {
                // the floor comes up and pins it
                for (int i = 0; i < 4; i++) {
                    float a = i * Mth.HALF_PI + 0.4f;
                    FxRecipes.debris(x + Mth.cos(a) * 0.9, gy + 0.1, z + Mth.sin(a) * 0.9, 0.3f, n(4), 0.8f, 0.0f);
                    FxRecipes.arc(x + Mth.cos(a) * 1.6, gy + 0.05, z + Mth.sin(a) * 1.6, x + Mth.cos(a) * 0.5, y + 0.6, z + Mth.sin(a) * 0.5, st, 12, 0.09f);
                }
                FxRecipes.glyphAt(x, gy + 0.05, z, 2.2f, st, 34, 0f);
                FxRecipes.dustRing(x, gy, z, 1.2f, n(12), st, 0.1f);
            }
            case TECH -> {
                // everything you have placed fires at once: lines converging from all round
                for (int i = 0; i < n(6); i++) {
                    float a = R.f() * Mth.TWO_PI;
                    FxRecipes.arc(x + Mth.cos(a) * 6, y + R.f(1.5f, 4f), z + Mth.sin(a) * 6, x, y, z, st, 5 + R.i(4), 0.06f);
                }
                FxRecipes.sparks(x, y, z, n(22), 0.6f, st, 8, 0.04f, 0, 1, 0, 0.3f);
                FxRecipes.sphereBurst(x, y, z, 0.3f, 1.8f, 6, st);
            }
            default -> {
                // Spirit: one palm, and the echo comes out the other side
                FxWorld.sprite(PALM, x - dx * 0.4, y - dy * 0.4, z - dz * 0.4, dx * 0.03, dy * 0.03, dz * 0.03, 10, 1.2f, 3.2f, st.core, alpha(st.mid, 0), FxPool.ADD);
                for (int i = 0; i < 4; i++) {
                    float d = 0.3f + i * 0.6f;
                    FxRecipes.ring(x + dx * d, y + dy * d, z + dz * d, 0.2f, 1.0f + i * 0.35f, 0.2f, 0.05f, 7 + i * 2, alpha(st.core, 0.9f), alpha(st.mid, 0), dx, dy, dz);
                }
                FxRecipes.wisps(x + dx * 1.5, y + dy * 1.5, z + dz * 1.5, n(10), 0.6f, st, 0.04f);
            }
        }
        if (mine) {
            FxScreen.fovPunch(-0.06f, 10);
            FxScreen.flash(st.mid, 0.1f, 6);
            dev.awaken.lens.client.LensClient.pulse(0.3f);
        }
    }

    // ---- Frost Nova: the cold runs out along the ground and stands up ---------------------------------------------------

    private static final int ARMS = 9;

    private static void rime(Job j) {
        float t = (j.age + 1) / 9f;
        if (t > 1f) return;
        float r = j.radius * (float) Math.pow(t, 0.8);
        FxStyle st = j.style;
        for (int k = 0; k < ARMS; k++) {
            // each arm wanders a little as it runs: seeded by the arm, so every viewer sees the same frost
            float a = k / (float) ARMS * Mth.TWO_PI + (j.seed % 97) * 0.065f + 0.22f * Mth.sin(j.age * 0.9f + k * 2.1f);
            double px = j.x + Mth.cos(a) * r, pz = j.z + Mth.sin(a) * r;
            double gy = FxWorld.groundY(px, j.y + 1.2, pz);
            if (Math.abs(gy - j.y) > 2.5) continue; // the frost keeps to the ground it started on
            FxRecipes.decal(px, gy + 0.02, pz, CRACK, 0.9f + R.f() * 0.5f, 0xC8E4F6FF, 150);
            if (k % 2 == j.age % 2) {
                // a crystal stands up out of it
                int s = FxWorld.sprite(SHARD, px, gy + 0.12, pz, 0, 0.07, 0, 90 + R.i(40), 0.18f, 0.5f + R.f() * 0.35f, st.core, alpha(st.mid, 0.0f), FxPool.ADD);
                FxWorld.drag(s, 0.55f);
                FxWorld.spin(s, R.f(-0.4f, 0.4f), 0);
            }
            if (j.age % 3 == 0) FxRecipes.smokePuffs(px, gy + 0.2, pz, 1, 0.3f, 0.7f, FxStyle.FLIGHT, 0.02f, 22);
        }
        if (j.age == 0) FxRecipes.sphereBurst(j.x, j.y + 0.4, j.z, 0.3f, 1.4f, 6, st);
    }

    // ---- Thunderclap: the air breaks, and the lightning goes along the floor ---------------------------------------------

    private static void shock(FxEvent e, FxStyle st) {
        double x = e.x(), y = e.y(), z = e.z();
        float r = Math.max(2f, e.scale());
        FxRecipes.sphereBurst(x, y + 1.1, z, 0.4f, r * 0.55f, 6, st);
        int forks = n(8);
        for (int i = 0; i < forks; i++) {
            float a = i / (float) Math.max(1, forks) * Mth.TWO_PI + R.f() * 0.5f;
            double ex = x + Mth.cos(a) * r, ez = z + Mth.sin(a) * r;
            double gy = FxWorld.groundY(ex, y + 1.5, ez);
            FxRecipes.arc(x, y + 1.25, z, ex, gy + 0.1, ez, st, 6 + R.i(3), 0.06f);
            FxRecipes.sparks(ex, gy + 0.15, ez, n(3), 0.22f, st, 6, 0.02f, 0, 1, 0, 0.6f);
        }
        FxRecipes.dustRing(x, y, z, r * 0.6f, n(16), FxStyle.KINETIC, 0.14f);
        FxRecipes.flare(x, y + 1.25, z, 1.3f, st, 4);
    }

    // ---- Telekinesis: a line of force from the hand, and the ground coming up round what you hold ------------------------

    private static final double[] P = new double[3];

    private static void grip(Job j, Minecraft mc) {
        Entity target = j.target >= 0 ? mc.level.getEntity(j.target) : null;
        Entity caster = j.caster >= 0 ? mc.level.getEntity(j.caster) : null;
        if (target == null || caster == null) {
            j.life = j.age;
            return;
        }
        Vec3 c = target.getBoundingBox().getCenter();
        if (j.age % 2 == 0) {
            boolean firstPerson = caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            if (!Muzzles.palm(caster, true, 1f, firstPerson, P)) {
                Vec3 eye = caster.getEyePosition();
                P[0] = eye.x;
                P[1] = eye.y - 0.35;
                P[2] = eye.z;
            }
            FxRecipes.arc(P[0], P[1], P[2], c.x, c.y, c.z, j.style, 3, 0.025f);
        }
        if (j.age % 4 == 0) {
            float a = R.f() * Mth.TWO_PI, rad = 0.5f + target.getBbWidth();
            double px = target.getX() + Mth.cos(a) * rad, pz = target.getZ() + Mth.sin(a) * rad;
            double gy = FxWorld.groundY(px, target.getY() + 0.5, pz);
            FxRecipes.debris(px, gy + 0.1, pz, 0.2f, 1, 0.22f, 0.02f);
            FxRecipes.motes(c.x, c.y, c.z, n(2), rad, j.style, 0.03f);
        }
    }

    // ---- Spirit Palm: the handprint goes through --------------------------------------------------------------------

    private static void through(FxEvent e, FxStyle st, String power) {
        float l = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        float dx = l > 1e-4f ? e.dx() / l : 0, dy = l > 1e-4f ? e.dy() / l : 0, dz = l > 1e-4f ? e.dz() / l : 1;
        // the print itself, on the near side, growing as it sinks in
        FxWorld.sprite(PALM, e.x() - dx * 0.35, e.y() - dy * 0.35, e.z() - dz * 0.35, dx * 0.02, dy * 0.02, dz * 0.02, 9, 0.7f, 1.9f, st.core, alpha(st.mid, 0), FxPool.ADD);
        Job j = job(J_THROUGH, e, st, power, 5, 1f);
        if (j != null) {
            j.dx = dx;
            j.dy = dy;
            j.dz = dz;
        }
    }

    /** Rings that pass through the body along the blow, one a tick, each a little further and wider. */
    private static void throughStep(Job j) {
        float d = -0.2f + j.age * 0.42f;
        FxRecipes.ring(j.x + j.dx * d, j.y + j.dy * d, j.z + j.dz * d, 0.15f, 0.55f + j.age * 0.16f, 0.16f, 0.04f, 6, alpha(j.style.core, 0.9f), alpha(j.style.mid, 0),
                j.dx, j.dy, j.dz);
    }

    /** The echo comes out of the far side. */
    private static void exit(FxEvent e, FxStyle st) {
        float l = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz());
        float dx = l > 1e-4f ? e.dx() / l : 0, dy = l > 1e-4f ? e.dy() / l : 0, dz = l > 1e-4f ? e.dz() / l : 1;
        FxWorld.sprite(PALM, e.x() + dx * 0.6, e.y() + dy * 0.6, e.z() + dz * 0.6, dx * 0.12, dy * 0.12, dz * 0.12, 10, 1.1f, 2.4f, st.core, alpha(st.mid, 0), FxPool.ADD);
        FxRecipes.sparks(e.x() + dx * 0.5, e.y() + dy * 0.5, e.z() + dz * 0.5, n(12), 0.4f, st, 8, 0.01f, dx, dy, dz, 1.4f);
        FxRecipes.wisps(e.x() + dx * 0.8, e.y() + dy * 0.8, e.z() + dz * 0.8, n(5), 0.3f, st, 0.03f);
    }

    // ---- Barrier: a plate of hard light that turns with your look ---------------------------------------------------

    private static void plate(Job j, Minecraft mc) {
        Entity caster = j.caster >= 0 ? mc.level.getEntity(j.caster) : null;
        if (caster == null) {
            j.life = j.age;
            return;
        }
        if (j.age % 2 != 0) return;
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        float in = Math.min(1f, j.age / 6f), out = Math.min(1f, (j.life - j.age) / 6f), k = Math.min(in, out);
        float pulse = 0.55f + 0.2f * Mth.sin(j.age * 0.25f);
        // a curved patch of hexes ahead of the chest: three rows, bowed toward you at the edges
        for (int row = -1; row <= 1; row++) {
            for (int col = -2; col <= 2; col++) {
                if (Math.abs(row) == 1 && Math.abs(col) == 2) continue;
                double u = col * 0.33 + (row == 0 ? 0 : 0.165), v = row * 0.29 - 0.22;
                double depth = 1.35 - 0.08 * (col * col) - 0.05 * (row * row);
                double px = eye.x + look.x * depth + right.x * u + up.x * v, py = eye.y + look.y * depth + right.y * u + up.y * v,
                        pz = eye.z + look.z * depth + right.z * u + up.z * v;
                // drawn over the world as well as adding light: a plate of light is invisible against a noon sky if it only adds
                // (NEAR: the renderer hides glows within a block and a half of the eye, which is exactly where your own plate is)
                FxWorld.sprite(HEX, px, py, pz, 0, 0, 0, 3, 0.34f, 0.34f, alpha(j.style.mid, 0.34f * pulse * k), alpha(j.style.mid, 0.24f * k), FxPool.BRIGHT | FxPool.NEAR);
                FxWorld.sprite(HEX, px, py, pz, 0, 0, 0, 3, 0.34f, 0.34f, alpha(j.style.core, 0.3f * pulse * k), alpha(j.style.core, 0.1f * k), FxPool.ADD | FxPool.NEAR);
            }
        }
    }

    // ---- Mend: petals climbing round you -------------------------------------------------------------------------

    private static void petals(Job j, Minecraft mc) {
        Entity caster = j.caster >= 0 ? mc.level.getEntity(j.caster) : null;
        if (caster == null) return;
        for (int i = 0; i < 2; i++) {
            float a = j.age * 0.55f + i * Mth.PI;
            double px = caster.getX() + Mth.cos(a) * j.radius, pz = caster.getZ() + Mth.sin(a) * j.radius;
            int s = FxWorld.sprite(PETAL, px, caster.getY() + 0.1 + (j.age % 12) * 0.12, pz, -Mth.sin(a) * 0.03, 0.07, Mth.cos(a) * 0.03, 16 + R.i(6), 0.2f, 0.08f,
                    j.style.core, alpha(j.style.mid, 0), FxPool.ADD | FxPool.NEAR);
            FxWorld.spin(s, a, 0.2f);
            FxWorld.drag(s, 0.96f);
        }
        if (j.age == 0) FxRecipes.glyphAt(caster.getX(), caster.getY() + 0.05, caster.getZ(), 1.1f, j.style, Math.min(30, j.life), 0.04f);
    }

    // ---- Shadow Step: you leave your shape behind in ink --------------------------------------------------------------

    private static void ink(FxEvent e, FxStyle st, @Nullable Entity caster, boolean mine) {
        // the shape is taken where the body is now. In your own eyes that is where you are standing: a wall of grey in your face
        boolean ownEyes = mine && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        if (caster != null && !ownEyes) FxAfterimages.capture(caster, 18, st.edge, 0);
        FxRecipes.tendrils(e.x(), e.y() + 0.1, e.z(), n(5), st);
        FxRecipes.smokePuffs(e.x() + e.dx(), e.y() + e.dy() + 0.2, e.z() + e.dz(), n(6), 0.5f, 0.9f, st, 0.02f, 20);
    }

    // ---- Brand: a mark seared onto the body, and the fire that comes out of it -------------------------------------------

    private static final int EMBER_HOT = 0xFFFFD27A, EMBER_DEEP = 0xFFFF6A1E, FLAME = 0xFFFF9A2E, FLAME_END = 0xFFB02A0A, BARK = 0xFF7A5230, BARK_DARK = 0xFF55381E,
            LEAF = 0xFF8FCB6B, AIR = 0xFFEAF6FF, DUSTY = 0xFFD8CFBD, ICE = 0xFFCFEFFF;

    /** Smoke off a fire: grey, lit by the world, rising and thinning. (A style's own puffs are its colour; fire's smoke is nobody's.) */
    private static void smoke(double x, double y, double z, int count, float spread, float size, int life) {
        for (int i = 0; i < count; i++) {
            int s = FxWorld.sprite(SMOKE, x + R.s() * spread, y + R.s() * spread * 0.5, z + R.s() * spread, R.s() * 0.015, R.f(0.02f, 0.05f), R.s() * 0.015, life + R.i(8), size * 0.6f, size,
                    0xA04A4744, 0x004A4744, FxPool.LIT | FxPool.ANIM);
            FxWorld.drag(s, 0.97f);
        }
    }

    private static void branded(FxEvent e, FxStyle st, String power) {
        end(e.caster(), power); // a new mark on the same body replaces the old one
        Job j = job(J_BRAND, e, st, power, Math.max(10, e.ticks()), 0.5f);
        if (j == null) return;
        if (e.dx() != 0 || e.dy() != 0 || e.dz() != 0) {
            // it leapt here from a body that died: a line of embers across
            double fx = e.x() + e.dx(), fy = e.y() + e.dy(), fz = e.z() + e.dz();
            for (int i = 0; i < n(10); i++) {
                float t = i / 10f;
                int s = FxWorld.sprite(EMBER, fx - e.dx() * t, fy - e.dy() * t + Mth.sin(t * Mth.PI) * 0.8, fz - e.dz() * t, -e.dx() * 0.02, 0.02, -e.dz() * 0.02,
                        8 + i, 0.2f, 0.05f, EMBER_HOT, alpha(EMBER_DEEP, 0), FxPool.ADD | FxPool.ANIM);
                FxWorld.drag(s, 0.9f);
            }
        }
        FxRecipes.flare(e.x(), e.y(), e.z(), 0.9f, st, 5);
        FxRecipes.embers(e.x(), e.y(), e.z(), n(8), 0.3f, st, 0.02f);
    }

    /** The mark itself: one rune that keeps its shape, on the side of the body you are looking at, breathing; faster as it runs out. */
    private static void brandStep(Job j, Minecraft mc) {
        Entity body = j.caster >= 0 ? mc.level.getEntity(j.caster) : null;
        if (body == null || !body.isAlive()) {
            j.life = j.age;
            return;
        }
        Vec3 c = body.getBoundingBox().getCenter();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        double dx = cam.x - c.x, dz = cam.z - c.z, l = Math.sqrt(dx * dx + dz * dz) + 1e-4;
        double off = body.getBbWidth() * 0.5 + 0.06;
        double px = c.x + dx / l * off, py = c.y + body.getBbHeight() * 0.12, pz = c.z + dz / l * off;
        int left = j.life - j.age;
        float beat = left < 40 ? 0.9f : 0.28f;
        float pulse = 0.75f + 0.25f * Mth.sin(j.age * beat);
        float size = Math.min(0.85f, 0.42f + body.getBbWidth() * 0.22f);
        int i = FxWorld.sprite(RUNE, px, py, pz, 0, 0, 0, 2, size, size, alpha(EMBER_HOT, pulse), alpha(EMBER_DEEP, pulse * 0.8f), FxPool.ADD);
        FxWorld.POOL.frame[i] = 0; // the hourglass: the Brand's own rune, the same on every body
        FxWorld.spin(i, 0, 0);
        FxWorld.sprite(HALO, px, py, pz, 0, 0, 0, 2, size * 2.2f, size * 2.2f, alpha(EMBER_DEEP, 0.35f * pulse), alpha(EMBER_DEEP, 0.2f * pulse), FxPool.ADD);
        if (j.age % 4 == 0) {
            int s = FxWorld.sprite(EMBER, px + R.s() * 0.15, py + R.s() * 0.15, pz + R.s() * 0.15, R.s() * 0.01, 0.035, R.s() * 0.01, 16 + R.i(8), 0.14f, 0.03f,
                    EMBER_HOT, alpha(EMBER_DEEP, 0), FxPool.ADD | FxPool.ANIM);
            FxWorld.drag(s, 0.95f);
        }
        if (j.age % 14 == 0) smoke(px, py + 0.2, pz, 1, 0.1f, 0.4f, 24);
    }

    /** A blow set it off: the fire comes out of the mark, the ground under it keeps the print and cools. */
    private static void brandBurst(FxEvent e, FxStyle st, boolean mine) {
        double x = e.x(), y = e.y(), z = e.z();
        float r = Math.max(1.5f, e.scale());
        boolean full = e.has(FxEvent.FULL);
        double gy = FxWorld.groundY(x, y + 0.5, z);
        FxRecipes.decal(x, gy + 0.02, z, SCORCH, r * 1.5f, 0xE0FFFFFF, 220);
        for (int i = 0; i < n(full ? 7 : 5); i++) {
            float a = i / 5f * Mth.TWO_PI + R.f() * 0.6f, d = r * R.f(0.25f, 0.7f);
            // cracks that start gold and cool to nothing
            FxRecipes.decal(x + Mth.cos(a) * d, gy + 0.03, z + Mth.sin(a) * d, CRACK, 1.1f + R.f() * 0.7f, EMBER_HOT, 70 + R.i(40));
        }
        FxRecipes.glyphAt(x, gy + 0.04, z, r * 0.55f, st, 26, 0.09f);
        // the fire itself: a column of big soft flame going up and out, embers thrown wide
        for (int i = 0; i < n(full ? 26 : 18); i++) {
            float a = R.f() * Mth.TWO_PI, sp = R.f(0.05f, 0.22f) * r * 0.5f;
            int s = FxWorld.sprite(GLOW, x + R.s() * 0.3, y + R.s() * 0.3, z + R.s() * 0.3, Mth.cos(a) * sp, R.f(0.04f, 0.2f), Mth.sin(a) * sp, 10 + R.i(8),
                    R.f(0.5f, 0.9f), R.f(1.2f, 2.0f), alpha(EMBER_HOT, 0.85f), alpha(EMBER_DEEP, 0), FxPool.ADD);
            FxWorld.drag(s, 0.86f);
        }
        FxRecipes.embers(x, y, z, n(full ? 40 : 26), r * 0.45f, st, 0.03f);
        FxRecipes.sparks(x, y, z, n(18), 0.55f, st, 10, 0.03f, 0, 1, 0, 0.5f);
        smoke(x, y + 0.6, z, n(8), r * 0.4f, 1.3f, 34);
        if (full) FxRecipes.sphereBurst(x, y, z, 0.4f, r, 7, st);
        if (mine) {
            FxScreen.flash(EMBER_DEEP, full ? 0.14f : 0.08f, 7);
            dev.awaken.lens.client.LensClient.pulse(full ? 0.3f : 0.18f);
        }
    }

    /** A brand left on the ground: it glows, breathes embers, and waits. */
    private static void sear(Job j) {
        double gy = FxWorld.groundY(j.x, j.y + 0.5, j.z);
        float pulse = 0.6f + 0.3f * Mth.sin(j.age * 0.3f);
        if (j.age % 2 == 0) {
            FxWorld.sprite(HALO, j.x, gy + 0.06, j.z, 0, 0, 0, 3, j.radius * 2.2f, j.radius * 2.2f, alpha(EMBER_DEEP, 0.4f * pulse), alpha(EMBER_DEEP, 0.3f * pulse),
                    FxPool.ADD | FxPool.FLAT);
        }
        if (j.age % (j.full ? 2 : 5) == 0) {
            float a = R.f() * Mth.TWO_PI, d = R.f() * j.radius * 0.8f;
            int s = FxWorld.sprite(EMBER, j.x + Mth.cos(a) * d, gy + 0.1, j.z + Mth.sin(a) * d, 0, R.f(0.03f, 0.07f), 0, 18 + R.i(10), 0.15f, 0.03f, EMBER_HOT,
                    alpha(EMBER_DEEP, 0), FxPool.ADD | FxPool.ANIM);
            FxWorld.drag(s, 0.96f);
        }
        if (j.full && j.age % 6 == 0) {
            float a = R.f() * Mth.TWO_PI, d = R.f() * j.radius * 0.7f;
            FxWorld.sprite(GLOW, j.x + Mth.cos(a) * d, gy + 0.2, j.z + Mth.sin(a) * d, 0, 0.06, 0, 12, 0.4f, 0.9f, alpha(EMBER_HOT, 0.6f), alpha(EMBER_DEEP, 0), FxPool.ADD);
        }
    }

    // ---- Hoarfrost Lances: spears of ice that hang by you, and fly when your weapon lands ---------------------------------

    /** Where lance {@code i} hangs (the server's own rule, EarnedPowers.lancePos), into {@link #P}. In your own eyes they sit ahead, in the top corners. */
    private static void lanceAt(Entity caster, int i, float pt, boolean firstPerson, float bob) {
        float yaw = (float) Math.toRadians(caster.getViewYRot(pt));
        double side = ((i & 1) == 0 ? 1 : -1) * (0.55 + 0.3 * (i >> 1));
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        Vec3 pos = caster.getPosition(pt);
        P[0] = pos.x + rx * side;
        P[1] = pos.y + caster.getBbHeight() + 0.35 + 0.12 * (i >> 1) + bob;
        P[2] = pos.z + rz * side;
        if (firstPerson) {
            Vec3 look = caster.getViewVector(pt);
            P[0] += look.x * 1.25;
            P[1] += look.y * 1.25 - 0.12;
            P[2] += look.z * 1.25;
        }
    }

    private static void lances(Job j, Minecraft mc) {
        Entity caster = j.caster >= 0 ? mc.level.getEntity(j.caster) : null;
        if (caster == null) {
            j.life = j.age;
            return;
        }
        boolean firstPerson = caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        Vec3 look = caster.getViewVector(1f);
        float grow = Math.min(1f, (j.age + 1) / 6f);
        float fade = Math.min(1f, (j.life - j.age) / 8f);
        for (int i = 0; i < j.n; i++) {
            lanceAt(caster, i, 1f, firstPerson, 0.05f * Mth.sin(j.age * 0.12f + i * 1.7f));
            double back = 0.45 * grow, fwd = 0.7 * grow;
            Shape s = FxWorld.shape(Shape.BEAM, 2);
            if (s != null) {
                s.x = P[0] - look.x * back;
                s.y = P[1] - look.y * back;
                s.z = P[2] - look.z * back;
                s.ex = P[0] + look.x * fwd;
                s.ey = P[1] + look.y * fwd;
                s.ez = P[2] + look.z * fwd;
                s.a = 0.075f;
                s.c0 = alpha(j.style.mid, fade);
                s.c1 = alpha(0xFFFFFFFF, fade);
                s.fadeIn = 0;
                s.fadeOut = 1;
            }
            int tip = FxWorld.sprite(SHARD, P[0] + look.x * fwd, P[1] + look.y * fwd, P[2] + look.z * fwd, 0, 0, 0, 2, 0.22f, 0.22f, alpha(j.style.core, fade),
                    alpha(j.style.mid, fade * 0.6f), FxPool.ADD);
            FxWorld.POOL.frame[tip] = i & 3;
            FxWorld.spin(tip, i * 0.9f, 0);
            // it is ice, not light: two chunks of it along the shaft, drawn solid
            for (int k = 0; k < 2; k++) {
                double at = (k == 0 ? -0.18 : 0.26) * grow;
                int ice = FxWorld.sprite(PLATE, P[0] + look.x * at, P[1] + look.y * at, P[2] + look.z * at, 0, 0, 0, 2, k == 0 ? 0.2f : 0.15f, k == 0 ? 0.2f : 0.15f,
                        alpha(ICE, 0.9f * fade), alpha(ICE, 0.8f * fade), FxPool.BRIGHT);
                FxWorld.POOL.frame[ice] = (i + k) & 1;
                FxWorld.spin(ice, i * 1.3f + k * 0.8f, 0);
            }
            if ((j.age + i * 3) % 9 == 0) {
                // cold falling off it
                int m = FxWorld.sprite(MOTE, P[0] + R.s() * 0.2, P[1], P[2] + R.s() * 0.2, 0, -0.02, 0, 14, 0.08f, 0.02f, j.style.core, alpha(j.style.mid, 0), FxPool.ADD);
                FxWorld.grav(m, 0.004f);
            }
        }
        if (j.age == 0) {
            for (int i = 0; i < j.n; i++) {
                lanceAt(caster, i, 1f, firstPerson, 0);
                FxRecipes.smokePuffs(P[0], P[1], P[2], 1, 0.15f, 0.5f, FxStyle.FLIGHT, 0.01f, 16);
                FxRecipes.shards(P[0], P[1], P[2], n(3), 0.12f, j.style);
            }
        }
    }

    /** One flew (the server's BOLT, its index in ticks): one fewer hangs, and where it lands the ice breaks. */
    private static void lanceFlies(FxEvent e, FxStyle st) {
        for (Job j : JOBS) {
            if (j.kind == J_LANCES && j.caster == e.caster() && j.n > 0) {
                j.n--;
                break;
            }
        }
        double bx = e.x() + e.dx(), by = e.y() + e.dy(), bz = e.z() + e.dz();
        float l = Mth.sqrt(e.dx() * e.dx() + e.dy() * e.dy() + e.dz() * e.dz()) + 1e-4f;
        // a wider, slower body of ice behind the bright line the shared bolt draws
        Shape s = FxWorld.shape(Shape.BEAM, 5);
        if (s != null) {
            s.x = e.x();
            s.y = e.y();
            s.z = e.z();
            s.ex = bx;
            s.ey = by;
            s.ez = bz;
            s.a = 0.2f;
            s.c0 = alpha(st.mid, 0.55f);
            s.c1 = alpha(st.core, 0.8f);
            s.fadeIn = 0;
            s.fadeOut = 4;
        }
        FxRecipes.shards(bx, by, bz, n(9), 0.28f, st);
        FxRecipes.smokePuffs(bx, by, bz, n(3), 0.3f, 0.8f, FxStyle.FLIGHT, 0.03f, 20);
        FxRecipes.sparks(bx, by, bz, n(8), 0.35f, st, 7, 0.03f, e.dx() / l, e.dy() / l, e.dz() / l, 0.7f);
        double gy = FxWorld.groundY(bx, by, bz);
        if (by - gy < 2.2) FxRecipes.decal(bx, gy + 0.02, bz, CRACK, 1.2f, 0xC8E4F6FF, 120);
    }

    // ---- Updraft: a column of air you can see ---------------------------------------------------------------------------

    private static void draft(Job j) {
        float r = j.radius, h = j.h;
        float fade = Math.min(1f, (j.life - j.age) / 12f) * Math.min(1f, (j.age + 1) / 5f);
        float rise = 0.38f;
        int life = (int) Math.min(60, h / rise);
        // two strands of dust drawn off the ground and wound up the column. Everything rises straight and the place it starts
        // from turns, which winds it into a helix; it is lit by the world, so it shows against a bright sky and at night alike
        for (int k = 0; k < 2; k++) {
            float a = j.age * 0.5f + k * Mth.PI + (j.seed & 7), d = r * R.f(0.62f, 0.9f);
            int s = FxWorld.sprite(DUST, j.x + Mth.cos(a) * d, j.y + 0.15 + R.f() * 0.4, j.z + Mth.sin(a) * d, 0, rise * R.f(0.92f, 1.08f), 0, life, 0.5f, 1.05f,
                    alpha(DUSTY, 0.62f * fade), alpha(DUSTY, 0), FxPool.LIT | FxPool.ANIM);
            FxWorld.drag(s, 1f);
        }
        // between them, two strands of the wind itself: pale curls, full-bright
        for (int k = 0; k < 2; k++) {
            float a = j.age * 0.5f + k * Mth.PI + Mth.HALF_PI + (j.seed & 7);
            int s = FxWorld.sprite(WIND, j.x + Mth.cos(a) * r * 0.8, j.y + 0.3, j.z + Mth.sin(a) * r * 0.8, 0, rise, 0, life, 0.9f, 0.6f, alpha(AIR, 0.85f * fade), alpha(AIR, 0),
                    FxPool.BRIGHT | FxPool.NEAR);
            FxWorld.spin(s, a, 0.1f);
            FxWorld.drag(s, 1f);
        }
        // its edge: thin lines running up, so you can see where to step
        for (int k = 0; k < 2; k++) {
            float a = R.f() * Mth.TWO_PI;
            float y = R.f() * Math.max(1f, h - 3f);
            Shape s = FxWorld.shape(Shape.BEAM, 7);
            if (s != null) {
                s.x = j.x + Mth.cos(a) * r;
                s.z = j.z + Mth.sin(a) * r;
                s.y = j.y + y;
                s.ex = s.x;
                s.ez = s.z;
                s.ey = s.y + 3f;
                s.a = 0.045f;
                s.c0 = alpha(j.style.mid, 0.7f * fade);
                s.c1 = alpha(j.style.core, 0.9f * fade);
                s.fadeIn = 2;
                s.fadeOut = 3;
            }
        }
        // a ring that climbs it
        if (j.age % 4 == 0) {
            float y = (j.age * 0.5f) % h;
            FxRecipes.ring(j.x, j.y + y, j.z, r * 0.95f, r * 0.7f, 0.14f, 0.04f, 9, alpha(j.style.core, 0.8f * fade), alpha(j.style.mid, 0), 0, 1, 0);
        }
        // leaves and grass go up with it
        if (j.age % 3 == 0) {
            float a = R.f() * Mth.TWO_PI, d = R.f() * r * 0.9f;
            int s = FxWorld.sprite(PETAL, j.x + Mth.cos(a) * d, j.y + 0.3, j.z + Mth.sin(a) * d, -Mth.sin(a) * 0.03, rise * R.f(0.8f, 1.2f), Mth.cos(a) * 0.03, life, 0.2f, 0.16f,
                    alpha(LEAF, fade), alpha(LEAF, 0.4f * fade), FxPool.LIT | FxPool.NEAR);
            // and fine grit all through it, small enough to pass your eyes without hiding the world
            int g = FxWorld.sprite(MOTE, j.x + Mth.cos(a + 2f) * d, j.y + 0.2, j.z + Mth.sin(a + 2f) * d, 0, rise * R.f(1.0f, 1.4f), 0, life, 0.07f, 0.05f, alpha(DUSTY, 0.9f * fade),
                    alpha(DUSTY, 0), FxPool.BRIGHT | FxPool.NEAR);
            FxWorld.drag(g, 1f);
            FxWorld.spin(s, a, 0.35f);
            FxWorld.drag(s, 1f);
        }
        // the ground keeps feeding it
        if (j.age % 8 == 0) FxRecipes.dustRing(j.x, j.y, j.z, r, n(10), FxStyle.KINETIC, -0.05f);
        if (j.age == 0) {
            FxRecipes.dustRing(j.x, j.y, j.z, r, n(22), FxStyle.KINETIC, 0.14f);
            FxRecipes.ring(j.x, j.y + 0.1, j.z, 0.3f, r * 1.3f, 0.5f, 0.1f, 10, alpha(j.style.core, 0.9f), alpha(j.style.mid, 0), 0, 1, 0);
        }
        // riding it yourself: the air goes past your eyes
        if (j.age % 4 == 0) {
            Entity cam = Minecraft.getInstance().getCameraEntity();
            if (cam != null && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                double dx = cam.getX() - j.x, dz = cam.getZ() - j.z;
                if (dx * dx + dz * dz <= r * r && cam.getY() >= j.y - 0.5 && cam.getY() <= j.y + h && !cam.onGround()) FxScreen.speedLines(6);
            }
        }
    }

    // ---- Backdraft: every fire near you comes in, and goes back out as one breath ------------------------------------------

    /** One fire being taken: its flame runs along the line into the mouth. */
    private static void drawnIn(FxEvent e, FxStyle st) {
        int steps = n(6);
        for (int i = 0; i < steps; i++) {
            float t = i / (float) Math.max(1, steps);
            int s = FxWorld.sprite(EMBER, e.x() + e.dx() * t, e.y() + e.dy() * t, e.z() + e.dz() * t, e.dx() * 0.16, e.dy() * 0.16, e.dz() * 0.16, 6, 0.3f, 0.08f,
                    EMBER_HOT, alpha(EMBER_DEEP, 0), FxPool.ADD | FxPool.ANIM);
            FxWorld.drag(s, 1f);
        }
        smoke(e.x(), e.y(), e.z(), 2, 0.2f, 0.6f, 18);
        FxWorld.sprite(GLOW, e.x(), e.y(), e.z(), e.dx() * 0.18, e.dy() * 0.18, e.dz() * 0.18, 5, 0.7f, 0.15f, alpha(EMBER_HOT, 0.9f), alpha(EMBER_DEEP, 0), FxPool.ADD);
    }

    /** The breath: big soft flame thrown down the cone for as long as the lungs last, white at the mouth, red and smoke at its end. */
    private static void breath(Job j) {
        float l = Mth.sqrt(j.dx * j.dx + j.dy * j.dy + j.dz * j.dz);
        float dx = l > 1e-4f ? j.dx / l : 0, dy = l > 1e-4f ? j.dy / l : 0, dz = l > 1e-4f ? j.dz / l : 1;
        float reach = j.radius, spread = (float) Math.tan(Math.toRadians(j.h)) * 0.75f;
        float strength = 1f - j.age / (float) j.life * 0.5f;
        int count = n(j.full ? 16 : 11);
        for (int i = 0; i < count; i++) {
            float sx = R.s() * spread, sy = R.s() * spread * 0.7f, sz = R.s() * spread;
            float speed = reach / 9f * R.f(0.7f, 1.15f) * strength;
            double vx = (dx + sx) * speed, vy = (dy + sy) * speed + 0.01, vz = (dz + sz) * speed;
            // it leaves from just past the hands, small, and grows as it goes: drawn near the eye, so the jet is whole from its start
            double from = 0.9 + R.f() * 0.7;
            double ox = j.x + dx * from, oy = j.y + dy * from, oz = j.z + dz * from;
            boolean core = i % 3 == 0;
            if (core) {
                int s = FxWorld.sprite(FLARE, ox, oy, oz, vx, vy, vz, 9 + R.i(4), 0.25f, 0.8f, 0xFFFFF3D0, alpha(EMBER_DEEP, 0), FxPool.ADD | FxPool.ANIM | FxPool.NEAR);
                FxWorld.drag(s, 0.93f);
                continue;
            }
            // the body of the flame is drawn over the world (so it shows at noon) and again as light (so it burns at night)
            int body = FxWorld.sprite(GLOW, ox, oy, oz, vx, vy, vz, 8 + R.i(4), 0.22f, 1.25f, i % 2 == 0 ? FLAME : EMBER_HOT, alpha(EMBER_DEEP, 0), FxPool.BRIGHT | FxPool.NEAR);
            FxWorld.drag(body, 0.93f);
            int lit = FxWorld.sprite(GLOW, ox, oy, oz, vx, vy, vz, 8 + R.i(4), 0.2f, 1.0f, alpha(EMBER_HOT, 0.7f), alpha(EMBER_DEEP, 0), FxPool.ADD | FxPool.NEAR);
            FxWorld.drag(lit, 0.93f);
        }
        if (j.age % 2 == 0) {
            FxRecipes.embers(j.x + dx * reach * 0.5, j.y + dy * reach * 0.5, j.z + dz * reach * 0.5, n(5), reach * 0.25f, j.style, 0.02f);
            smoke(j.x + dx * reach * 0.9, j.y + dy * reach * 0.9 + 0.3, j.z + dz * reach * 0.9, 1, reach * 0.15f, 1.3f, 30);
        }
        if (j.age == 0) {
            FxRecipes.flare(j.x + dx * 0.6, j.y + dy * 0.6, j.z + dz * 0.6, 1.5f, j.style, 5);
            double gx = j.x + dx * reach * 0.6, gz = j.z + dz * reach * 0.6;
            FxRecipes.decal(gx, FxWorld.groundY(gx, j.y, gz) + 0.02, gz, SCORCH, reach * 0.55f, 0xC0FFFFFF, 200);
        }
    }

    // ---- Thornwake: the plants that are really there take hold -----------------------------------------------------------

    private static final int ROOT_STEPS = 9, ROOT_TICKS = 4;

    /**
     * A root: little blocks of real wood (the debris pool's cubes, each resting where it is put), thick at the foot and thin at
     * the tip, along a curve that leaves the ground steeply and leans in to the body. It grows from the ground up over a few
     * ticks, and leaves break out near its tip. Wood, not light: it is lit like any block and shows at noon and at night.
     */
    private static void root(FxStyle st, double x, double y, double z, double tx, double ty, double tz, int life, float width, int seed) {
        for (Job free : JOBS) {
            if (free.kind != 0) continue;
            free.kind = J_ROOT;
            free.age = 0;
            free.life = ROOT_TICKS;
            free.caster = -1;
            free.target = -1;
            free.seed = seed;
            free.x = x;
            free.y = y;
            free.z = z;
            free.dx = (float) (tx - x);
            free.dy = (float) (ty - y);
            free.dz = (float) (tz - z);
            free.radius = width;
            free.n = life;
            free.h = 0;
            free.full = false;
            free.style = st;
            free.power = "thornwake";
            return;
        }
    }

    private static void rootGrows(Job j) {
        int from = j.age * ROOT_STEPS / ROOT_TICKS, to = (j.age + 1) * ROOT_STEPS / ROOT_TICKS;
        var wood = net.minecraft.world.level.block.Blocks.SPRUCE_WOOD.defaultBlockState();
        for (int i = from; i < to; i++) {
            float t = (i + 0.5f) / ROOT_STEPS;
            float u = t * t * (3 - 2 * t), up = Mth.sqrt(t);
            double px = j.x + j.dx * u + Mth.sin(t * 5f + j.seed) * 0.07, py = j.y + j.dy * up, pz = j.z + j.dz * u + Mth.cos(t * 4f + j.seed * 1.7f) * 0.07;
            float size = j.radius * (1f - 0.55f * t);
            // at rest where it is put: its floor is its own height
            FxDebris.spawn(wood, px, py, pz, 0, 0, 0, size, j.n + R.i(6), (float) py);
        }
        if (j.age == ROOT_TICKS - 1) {
            var leaves = net.minecraft.world.level.block.Blocks.AZALEA_LEAVES.defaultBlockState();
            double tx = j.x + j.dx, ty = j.y + j.dy, tz = j.z + j.dz;
            FxDebris.spawn(leaves, tx, ty + 0.05, tz, 0, 0, 0, j.radius * 0.7f, j.n, (float) (ty + 0.05));
            for (int i = 0; i < 2; i++) {
                int s = FxWorld.sprite(PETAL, tx + R.s() * 0.2, ty + R.s() * 0.2, tz + R.s() * 0.2, R.s() * 0.04, R.f(0.02f, 0.08f), R.s() * 0.04, 24 + R.i(10), 0.16f, 0.1f, LEAF,
                        alpha(LEAF, 0), FxPool.LIT);
                FxWorld.spin(s, R.f(0, 6.28f), 0.25f);
                FxWorld.grav(s, 0.01f);
                FxWorld.drag(s, 0.95f);
            }
        }
    }

    private static void rootTakes(FxEvent e, FxStyle st) {
        double tx = e.x() + e.dx(), ty = e.y() + e.dy(), tz = e.z() + e.dz();
        int life = Math.max(12, e.ticks());
        double gy = FxWorld.groundY(tx, ty + 0.5, tz);
        // where it left the plant: a little soil
        FxRecipes.debris(e.x(), e.y() + 0.1, e.z(), 0.3f, n(2), 0.3f, 0.06f);
        // and round the body: three roots out of the ground on different sides, up to the waist
        int count = Math.max(2, n(3));
        for (int i = 0; i < count; i++) {
            float a = i / (float) count * Mth.TWO_PI + (e.seed() & 15) * 0.4f, d = 0.8f + R.f() * 0.3f;
            root(st, tx + Mth.cos(a) * d, gy, tz + Mth.sin(a) * d, tx + Mth.cos(a + 1.1f) * 0.28, ty + R.f(-0.15f, 0.3f), tz + Mth.sin(a + 1.1f) * 0.28, life, 0.3f, e.seed() * 31 + i);
        }
        FxRecipes.debris(tx, gy + 0.1, tz, 0.9f, n(3), 0.35f, 0.08f);
        FxRecipes.dustRing(tx, gy, tz, 0.9f, n(8), FxStyle.KINETIC, 0.06f);
    }

    /** The cast itself: the ground stirs all the way out, and where it is green the leaves lift. */
    private static void groundWakes(FxEvent e, FxStyle st) {
        float r = Math.max(2f, e.scale());
        boolean green = e.has(FxEvent.FULL);
        for (int i = 0; i < n(green ? 14 : 6); i++) {
            float a = R.f() * Mth.TWO_PI, d = Mth.sqrt(R.f()) * r;
            double px = e.x() + Mth.cos(a) * d, pz = e.z() + Mth.sin(a) * d;
            double gy = FxWorld.groundY(px, e.y() + 1.2, pz);
            if (Math.abs(gy - e.y()) > 3) continue;
            if (i % 2 == 0) FxRecipes.debris(px, gy + 0.1, pz, 0.25f, 1, 0.25f, 0.04f);
            if (green) {
                int s = FxWorld.sprite(PETAL, px, gy + 0.2, pz, R.s() * 0.03, R.f(0.05f, 0.12f), R.s() * 0.03, 30 + R.i(16), 0.14f, 0.1f, LEAF, alpha(LEAF, 0), FxPool.LIT);
                FxWorld.spin(s, a, 0.2f);
                FxWorld.grav(s, 0.008f);
                FxWorld.drag(s, 0.96f);
            }
        }
        if (green) FxRecipes.motes(e.x(), e.y() + 0.6, e.z(), n(12), r * 0.5f, st, 0.02f);
    }

    /** Elder Grove: one great root, wound out of five, standing up under the strongest thing here. */
    private static void elderRoot(FxEvent e, FxStyle st) {
        double x = e.x(), z = e.z();
        double gy = FxWorld.groundY(x, e.y() + 0.5, z);
        float h = Math.max(2.5f, e.scale());
        int life = Math.max(20, e.ticks());
        int count = Math.max(3, n(5));
        for (int i = 0; i < count; i++) {
            float a = i / (float) count * Mth.TWO_PI, d = 1.3f;
            root(st, x + Mth.cos(a) * d, gy, z + Mth.sin(a) * d, x + Mth.cos(a + 2.2f) * 0.35, gy + h * R.f(0.7f, 1f), z + Mth.sin(a + 2.2f) * 0.35, life, 0.5f, e.seed() * 17 + i);
        }
        FxRecipes.debris(x, gy + 0.1, z, 1.6f, n(8), 0.6f, 0.18f);
        FxRecipes.dustRing(x, gy, z, 1.6f, n(16), FxStyle.KINETIC, 0.12f);
        FxRecipes.decal(x, gy + 0.02, z, CRACK, 3.4f, 0xD0FFFFFF, 200);
        for (int i = 0; i < n(14); i++) {
            int s = FxWorld.sprite(PETAL, x + R.s() * 1.2, gy + R.f(0.5f, h), z + R.s() * 1.2, R.s() * 0.08, R.f(0.03f, 0.12f), R.s() * 0.08, 34 + R.i(16), 0.16f, 0.1f, LEAF,
                    alpha(LEAF, 0), FxPool.LIT);
            FxWorld.spin(s, R.f(0, 6.28f), 0.25f);
            FxWorld.grav(s, 0.012f);
            FxWorld.drag(s, 0.95f);
        }
    }
}
