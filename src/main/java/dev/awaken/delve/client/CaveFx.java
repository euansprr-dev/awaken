package dev.awaken.delve.client;

import dev.awaken.delve.cave.CaveContent;
import dev.awaken.delve.cave.CaveKind;
import dev.awaken.delve.cave.CaveNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.List;

/**
 * The cave places seen from outside (PLAN 8.2): mist rising out of a sinkhole by day (thickest in the morning, gone
 * by night; soft lobes that swell and thin as they climb past the rim, lit by the sky) and a breath of cold air at a
 * chute's mouth (pale streaks drifting out of it). Our own sprites (tools/art/specs/caves.py); a few a tick at most,
 * only near the player.
 */
final class CaveFx {
    private CaveFx() {}

    private static List<CaveNet.Site> sites = List.of();
    private static final RandomSource R = RandomSource.create();

    static void near(CaveNet.Near m) {
        sites = List.copyOf(m.sites());
    }

    static void reset() {
        sites = List.of();
    }

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(CaveContent.MIST.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Wisp(l, x, y, z, xa, ya, za, s, true));
        e.registerSpriteSet(CaveContent.DRAUGHT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Wisp(l, x, y, z, xa, ya, za, s, false));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || sites.isEmpty() || mc.isPaused()) return;
        long time = level.getOverworldClockTime() % 24000L;
        // the mist: thick at dawn, thinning through the day, a little at dusk, none at night
        float day = time < 1000 ? 1.0F : time < 6000 ? 1.0F - (time - 1000) / 7000F : time < 11500 ? 0.35F : time < 13000 ? 0.5F : 0F;
        for (CaveNet.Site s : sites) {
            double dx = s.at().getX() + 0.5 - mc.player.getX(), dz = s.at().getZ() + 0.5 - mc.player.getZ();
            double d2 = dx * dx + dz * dz;
            if (s.kind() == CaveKind.SINKHOLE.ordinal()) {
                if (day <= 0 || d2 > 150 * 150) continue;
                // a few wisps a second at dawn, thinning through the day: mist, never a fog bank
                float rate = day * (0.3F + s.radius() / 28F) * (d2 < 60 * 60 ? 1F : 0.5F);
                int n = (int) rate + (R.nextFloat() < rate - (int) rate ? 1 : 0);
                for (int i = 0; i < n; i++) {
                    double a = R.nextDouble() * Math.PI * 2, r = Math.sqrt(R.nextDouble()) * s.radius() * 0.85;
                    // most rise from the top of the shaft so they clear the rim in their life: seen from the rim and afar
                    double u = R.nextDouble();
                    double y = s.at().getY() - 1 - u * u * Math.min(24, s.depth() * 0.4);
                    level.addParticle(CaveContent.MIST.get(), s.at().getX() + 0.5 + Math.cos(a) * r, y, s.at().getZ() + 0.5 + Math.sin(a) * r,
                            (R.nextDouble() - 0.5) * 0.01, 0.025 + R.nextDouble() * 0.02, (R.nextDouble() - 0.5) * 0.01);
                }
            } else if (s.kind() == CaveKind.CHUTE.ordinal()) {
                if (d2 > 28 * 28 || R.nextFloat() > 0.35F) continue;
                // out of the mouth, back the way you'd come in (the chute's rot: forward is downhill)
                int rot = s.depth() & 3;
                double fx = rot == 1 ? -1 : rot == 3 ? 1 : 0, fz = rot == 0 ? 1 : rot == 2 ? -1 : 0;
                double ox = s.at().getX() + 0.5 + (R.nextDouble() - 0.5) * 2.4 + fx * 1.5, oz = s.at().getZ() + 0.5 + (R.nextDouble() - 0.5) * 2.4 + fz * 1.5;
                level.addParticle(CaveContent.DRAUGHT.get(), ox, s.at().getY() + 0.3 + R.nextDouble() * 2.2, oz,
                        -fx * (0.05 + R.nextDouble() * 0.03), 0.004, -fz * (0.05 + R.nextDouble() * 0.03));
            }
        }
    }

    /** One wisp: mist (big, slow, rising, swelling) or a draught's streak (small, quick, along the air). */
    static final class Wisp extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final boolean mist;
        private final float base, peak;

        Wisp(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, boolean mist) {
            super(level, x, y, z, xa, ya, za, sprites.get(level.getRandom()));
            this.sprites = sprites;
            this.mist = mist;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.gravity = 0F;
            this.friction = mist ? 0.995F : 0.97F;
            this.hasPhysics = false;
            this.lifetime = mist ? 120 + random.nextInt(80) : 30 + random.nextInt(20);
            this.quadSize = mist ? 1.3F + random.nextFloat() * 1.0F : 0.35F + random.nextFloat() * 0.25F;
            this.base = quadSize;
            this.peak = mist ? 0.28F + random.nextFloat() * 0.1F : 0.32F;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            float c = mist ? 0.92F + random.nextFloat() * 0.06F : 0.86F;
            setColor(c, c + 0.02F, mist ? c + 0.03F : 1F);
            this.alpha = 0F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            // mist is lit by the sky that reaches it; the draught keeps a little light of its own so it shows in the dark
            int c = super.getLightCoords(a);
            return mist ? c : LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), 5), LightCoordsUtil.sky(c));
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            alpha = peak * Math.min(1F, Math.min(t * (mist ? 4F : 6F), (1F - t) * (mist ? 1.6F : 2.5F)));
            quadSize = base * (mist ? 1F + 1.2F * t : 1F + 0.5F * t);
            if (mist) {
                // thins to nothing round the camera: you walk out of mist, never into a white wall
                var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
                double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                alpha *= (float) Math.max(0, Math.min(1, (d - 2.5) / 6.0));
            }
            oRoll = roll;
            roll += mist ? 0.004F : 0.01F;
        }
    }
}
