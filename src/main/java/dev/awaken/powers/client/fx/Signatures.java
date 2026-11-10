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

    private static final int J_RIME = 1, J_SHOCK = 2, J_GRIP = 3, J_THROUGH = 4, J_PLATE = 5, J_PETALS = 6, J_INK = 7;

    /** A signature that takes time: what, where, how big, whose, how old. */
    private static final class Job {
        int kind, age, life, caster = -1, target = -1, seed;
        double x, y, z;
        float dx, dy, dz, radius;
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
                if (kind == FxKind.BLINK) ink(e, st, caster);
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
                double u = col * 0.42 + (row == 0 ? 0 : 0.21), v = row * 0.38 - 0.25;
                double depth = 1.25 - 0.10 * (col * col) - 0.06 * (row * row);
                double px = eye.x + look.x * depth + right.x * u + up.x * v, py = eye.y + look.y * depth + right.y * u + up.y * v,
                        pz = eye.z + look.z * depth + right.z * u + up.z * v;
                FxWorld.sprite(HEX, px, py, pz, 0, 0, 0, 3, 0.46f, 0.46f, alpha(j.style.mid, pulse * k), alpha(j.style.core, 0.25f * k), FxPool.ADD);
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
                    j.style.core, alpha(j.style.mid, 0), FxPool.ADD);
            FxWorld.spin(s, a, 0.2f);
            FxWorld.drag(s, 0.96f);
        }
        if (j.age == 0) FxRecipes.glyphAt(caster.getX(), caster.getY() + 0.05, caster.getZ(), 1.1f, j.style, Math.min(30, j.life), 0.04f);
    }

    // ---- Shadow Step: you leave your shape behind in ink --------------------------------------------------------------

    private static void ink(FxEvent e, FxStyle st, @Nullable Entity caster) {
        if (caster != null) FxAfterimages.capture(caster, 18, st.edge, 0);
        FxRecipes.tendrils(e.x(), e.y() + 0.1, e.z(), n(5), st);
        FxRecipes.smokePuffs(e.x() + e.dx(), e.y() + e.dy() + 0.2, e.z() + e.dz(), n(6), 0.5f, 0.9f, st, 0.02f, 20);
    }
}
