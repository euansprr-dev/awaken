package dev.awaken.delve.client;

import dev.awaken.Awaken;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.DelveNet;
import dev.awaken.delve.Ore;
import dev.awaken.delve.TuningForkItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The Tuning Fork's song (PLAN 7.5): struck on stone, it rings for a few seconds, and while it rings its pitch climbs
 * as you turn to face the nearest lode of its ore (the higher, the straighter you face it); nothing within 48 and it
 * gives a short, flat note. On the band a soft arc shows which way to turn: a bead of the ore's colour slides along it
 * toward the lode, brightening as you face it, a small mark above or below it when the lode is up or down.
 */
final class ForkSong {
    private ForkSong() {}

    static final int RING = 90, DULL = 26;
    private static Ore ore = Ore.IRON;
    private static boolean found;
    private static BlockPos target = BlockPos.ZERO;
    private static long ringAt = Long.MIN_VALUE / 2;
    private static int ticks;
    private static Ring sound;
    /** How squarely the player faces the target now (0..1), and its bearing relative to the view (degrees) and height. */
    static float align, bearing, rise;

    static void ring(DelveNet.ForkRing m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || m.ore() < 0 || m.ore() >= Ore.values().length) return;
        ore = Ore.values()[m.ore()];
        found = m.found();
        target = BlockPos.of(m.target());
        ringAt = mc.level.getGameTime();
        ticks = 0;
        update(mc.player, 1F);
        if (sound != null) mc.getSoundManager().stop(sound);
        sound = new Ring(found ? RING : DULL);
        mc.getSoundManager().play(sound);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.level.getGameTime() - ringAt > RING) return;
        ticks++;
        update(mc.player, 1F);
    }

    private static void update(LocalPlayer p, float pt) {
        if (!found) {
            align = 0;
            return;
        }
        Vec3 eye = p.getEyePosition(pt), to = Vec3.atCenterOf(target).subtract(eye);
        double len = to.length();
        if (len < 1e-3) {
            align = 1;
            bearing = 0;
            rise = 0;
            return;
        }
        Vec3 look = p.getViewVector(pt);
        align = (float) Math.max(0, look.dot(to.scale(1 / len)));
        float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        bearing = Mth.wrapDegrees(want - p.getYRot(pt));
        rise = (float) to.y;
    }

    /** The ring's pitch now: the ore's note, climbing an octave and a bit as you face the lode. */
    static float pitch() {
        float base = TuningForkItem.pitch(ore);
        if (!found) return base * 0.62F;
        return Math.min(2F, base * (0.72F + 0.62F * align * align * align));
    }

    static final class Ring extends AbstractTickableSoundInstance {
        private final int life;
        private int age;

        Ring(int life) {
            super(DelveContent.FORK_RING.get(), SoundSource.PLAYERS, RandomSource.create());
            this.life = life;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.looping = true;
            this.volume = 0.85F;
            this.pitch = pitch();
        }

        @Override
        public void tick() {
            age++;
            if (age >= life) {
                stop();
                return;
            }
            volume = 0.85F * (float) Math.exp(-age / (life * 0.42));
            pitch = Mth.lerp(0.35F, pitch, pitch());
        }
    }

    // ---- the band's arc ---------------------------------------------------------------------------------------------

    static void registerLayer(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Awaken.id("delve_fork"), ForkSong::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        float age = mc.level.getGameTime() - ringAt + dt.getGameTimeDeltaPartialTick(false);
        int life = found ? RING : DULL;
        if (age < 0 || age > life) return;
        update(mc.player, dt.getGameTimeDeltaPartialTick(false));
        float a = Math.min(1F, age / 4F) * Math.min(1F, (life - age) / 18F);
        int w = g.guiWidth();
        int cx = w / 2, cy = 30, r = 58;
        int tone = mix(ore.tint, 0xFFFFFF, 0.45F);
        // the arc: a soft band of fine dots, brighter toward its middle (where you face), a notch at the top
        for (int i = 0; i <= 56; i++) {
            float k = -80F + i * (160F / 56F);
            double ang = Math.toRadians(k);
            int x = cx + (int) Math.round(Math.sin(ang) * r), y = cy - (int) Math.round(Math.cos(ang) * r * 0.36);
            float mid = 1F - Math.abs(k) / 80F;
            g.fill(x, y, x + 1, y + 1, argb((0.18F + 0.32F * mid) * a, tone));
        }
        g.fill(cx, cy - (int) (r * 0.36) - 4, cx + 1, cy - (int) (r * 0.36) - 1, argb(0.7F * a, tone));
        if (!found) return;
        // the bead: where the lode lies, sliding to the notch as you turn to it (pinned at the end when it's behind you)
        float b = Mth.clamp(bearing, -90F, 90F) / 90F * 80F;
        double ang = Math.toRadians(b);
        int bx = cx + (int) Math.round(Math.sin(ang) * r), by = cy - (int) Math.round(Math.cos(ang) * r * 0.36);
        float lit = Math.abs(bearing) > 90F ? 0.4F : 0.55F + 0.45F * align;
        int glow = mix(ore.tint, 0xFFFFFF, 0.25F + 0.5F * align * align);
        g.fill(bx - 6, by - 6, bx + 7, by + 7, argb(0.07F * a * lit, ore.tint));
        g.fill(bx - 4, by - 4, bx + 5, by + 5, argb(0.16F * a * lit, ore.tint));
        g.fill(bx - 2, by - 2, bx + 3, by + 3, argb(0.55F * a * lit, glow));
        g.fill(bx - 1, by - 1, bx + 2, by + 2, argb(1F * a * lit, glow));
        // its rays when you face it squarely: the fork is singing at its highest
        if (align > 0.92F && Math.abs(bearing) < 12F) {
            float s = (align - 0.92F) / 0.08F;
            g.fill(bx - 9, by, bx - 4, by + 1, argb(0.5F * a * s, glow));
            g.fill(bx + 5, by, bx + 10, by + 1, argb(0.5F * a * s, glow));
        }
        // up or down: a small chevron over or under the bead
        if (rise > 4) {
            g.fill(bx, by - 10, bx + 1, by - 9, argb(0.8F * a, glow));
            g.fill(bx - 1, by - 9, bx + 2, by - 8, argb(0.8F * a, glow));
        } else if (rise < -4) {
            g.fill(bx - 1, by + 8, bx + 2, by + 9, argb(0.8F * a, glow));
            g.fill(bx, by + 9, bx + 1, by + 10, argb(0.8F * a, glow));
        }
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int c0, int c1, float t) {
        int r = (int) Mth.lerp(t, (c0 >> 16) & 255, (c1 >> 16) & 255), g = (int) Mth.lerp(t, (c0 >> 8) & 255, (c1 >> 8) & 255),
                b = (int) Mth.lerp(t, c0 & 255, c1 & 255);
        return r << 16 | g << 8 | b;
    }

    static void reset() {
        ringAt = Long.MIN_VALUE / 2;
        Minecraft mc = Minecraft.getInstance();
        if (sound != null) mc.getSoundManager().stop(sound);
        sound = null;
    }
}
