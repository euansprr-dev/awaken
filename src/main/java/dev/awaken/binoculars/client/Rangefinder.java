package dev.awaken.binoculars.client;

import dev.awaken.binoculars.BinocularsSounds;
import dev.awaken.binoculars.GlassRules;
import dev.awaken.core.api.Destinations;
import dev.awaken.core.api.FarGround;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What's at the middle of the view (SPEC §39.4, §39.7, §39.8): a block ray through loaded land every fourth frame, out
 * to the render distance; past it, Far Sight's ground, marched on a background thread along the same look; else
 * "far". Feeds the readout and marking a spot. Allocation-free on the render thread except when the readout's words
 * change.
 */
final class Rangefinder {
    private Rangefinder() {}

    /** How far Far Sight's land reaches, in blocks. */
    private static final double FAR = 16_000;

    private static int frames;
    private static double metres = Double.NaN;
    private static boolean hit, far;
    private static final BlockPos.MutableBlockPos AT = new BlockPos.MutableBlockPos();
    private static String words = "";
    private static long wordsKey = Long.MIN_VALUE;
    /** "Too far to tell" shows in place of the distance until then (nanoTime). */
    private static long tooFarUntil;

    // the far march: one at a time, its look and its answer
    private static final AtomicBoolean MARCHING = new AtomicBoolean();
    private static volatile double farMetres = Double.NaN, farX, farY, farZ;
    private static volatile double farDirX, farDirY, farDirZ;

    static void frame() {
        if (!Glass.seeing()) {
            frames = 0;
            return;
        }
        if (frames++ % 4 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        double reach = mc.options.getEffectiveRenderDistance() * 16.0;
        HitResult r = p.pick(reach, 1F, false);
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getViewVector(1F);
        if (r.getType() != HitResult.Type.MISS) {
            hit = true;
            far = false;
            metres = r.getLocation().distanceTo(eye);
            AT.set(r.getLocation().x - dir.x * 0.05, r.getLocation().y - dir.y * 0.05, r.getLocation().z - dir.z * 0.05);
            return;
        }
        // past the loaded land: the last far answer if it was for (nearly) this look, and ask again
        double fm = farMetres;
        if (!Double.isNaN(fm) && dir.x * farDirX + dir.y * farDirY + dir.z * farDirZ > 0.99995) {
            hit = true;
            far = true;
            metres = fm;
            AT.set(farX, farY, farZ);
        } else {
            hit = false;
            far = false;
            metres = Double.NaN;
        }
        if (FarGround.ready()) march(eye, dir, reach);
    }

    private static void march(Vec3 eye, Vec3 dir, double from) {
        if (!MARCHING.compareAndSet(false, true)) return;
        double ex = eye.x, ey = eye.y, ez = eye.z, dx = dir.x, dy = dir.y, dz = dir.z;
        Runnable job = () -> {
            try {
                double found = Double.NaN, hx = 0, hy = 0, hz = 0;
                double s = from, prev = from;
                while (s < FAR) {
                    double x = ex + dx * s, y = ey + dy * s, z = ez + dz * s;
                    double g = FarGround.ground((int) Math.floor(x), (int) Math.floor(z));
                    if (!Double.isNaN(g) && y <= g) {
                        // narrow it between the last two samples
                        double lo = prev, hi = s;
                        for (int i = 0; i < 6; i++) {
                            double m = (lo + hi) * 0.5, gy = FarGround.ground((int) Math.floor(ex + dx * m), (int) Math.floor(ez + dz * m));
                            if (!Double.isNaN(gy) && ey + dy * m <= gy) hi = m;
                            else lo = m;
                        }
                        found = hi;
                        hx = ex + dx * hi;
                        hy = ey + dy * hi;
                        hz = ez + dz * hi;
                        break;
                    }
                    if (dy > 0.2 && y > 600) break; // looking into the sky
                    prev = s;
                    s += s < 1000 ? 8 : s < 4000 ? 16 : 32;
                }
                farX = hx;
                farY = hy;
                farZ = hz;
                farDirX = dx;
                farDirY = dy;
                farDirZ = dz;
                farMetres = found;
            } catch (RuntimeException ignored) {
                farMetres = Double.NaN;
            } finally {
                MARCHING.set(false);
            }
        };
        Thread t = new Thread(job, "awaken-binoculars-range");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** The readout's words: "8× · 312 m · NE". Rebuilt only when what it says changes. */
    static String words(float step, float yRot) {
        boolean tooFar = System.nanoTime() < tooFarUntil;
        long m = tooFar ? -2 : Double.isNaN(metres) ? -1 : metres < 1000 ? Math.round(metres) : 100_000 + Math.round(metres / 100);
        long key = (m * 64 + Math.round(step * 2)) * 8 + Math.floorMod(Math.round((yRot + 180) / 45F), 8);
        if (key != wordsKey) {
            wordsKey = key;
            words = tooFar ? GlassRules.stepWords(step) + " · " + Component.translatable("binoculars.awaken.too_far").getString() + " · "
                    + GlassRules.bearing(yRot) : GlassRules.readout(step, metres, yRot);
        }
        return words;
    }

    /** Right-click while looking: the middle of the view becomes your destination, or the readout says why not. */
    static boolean mark() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return false;
        double max = far ? FAR : mc.options.getEffectiveRenderDistance() * 16.0;
        if (!GlassRules.markable(hit, AT, mc.level.getMinY(), mc.level.getMaxY(), metres, max, mc.level.getWorldBorder().isWithinBounds(AT))) {
            tooFarUntil = System.nanoTime() + 1_500_000_000L;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(BinocularsSounds.STEP.get(), 0.6F, 0.3F));
            return true;
        }
        BlockPos at = AT.immutable();
        Destinations.request(Optional.of(new Destinations.Target(GlassRules.SPOT_NAME, mc.level.dimension().identifier(), at, "spot")));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(BinocularsSounds.STEP.get(), 1.35F, 0.45F));
        p.sendOverlayMessage(Component.translatable("binoculars.awaken.marked"));
        return true;
    }

    static void reset() {
        metres = Double.NaN;
        hit = far = false;
        farMetres = Double.NaN;
        tooFarUntil = 0;
    }
}
