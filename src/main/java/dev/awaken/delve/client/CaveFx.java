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
 * by night; soft lobes that swell and thin as they climb past the rim, lit by the sky), a breath of cold air at a
 * chute's mouth (pale streaks drifting out of it), and a grotto's own light (its curtain and its pool). Our own sprites (tools/art/specs/caves.py); a few a tick at most,
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
        e.registerSpriteSet(CaveContent.MIST.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Wisp(l, x, y, z, xa, ya, za, s, Kind.MIST));
        e.registerSpriteSet(CaveContent.DRAUGHT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Wisp(l, x, y, z, xa, ya, za, s, Kind.DRAUGHT));
        e.registerSpriteSet(CaveContent.GLOW.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Wisp(l, x, y, z, xa, ya, za, s, Kind.GLOW));
        e.registerSpriteSet(CaveContent.GLINT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Wisp(l, x, y, z, xa, ya, za, s, Kind.GLINT));
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
            } else if (s.kind() == CaveKind.GROTTO.ordinal()) {
                double dy = s.at().getY() - mc.player.getY();
                if (d2 + dy * dy > 40 * 40) continue;
                grotto(mc, s);
            }
        }
    }

    /**
     * A grotto's own light, drawn (no light blocks, no torches): light caught in its curtain (bright glints sliding down
     * the falling water, a pale aqua glow behind it) and its pool's sea-green halo with motes rising off the water.
     * A site's depth says which (0 the curtain, 1 the pool); its radius carries the grotto's turn.
     */
    private static void grotto(Minecraft mc, CaveNet.Site s) {
        boolean alongX = (s.radius() & 1) == 0; // the curtain runs along world x when the grotto faces north or south
        double cx = s.at().getX() + 0.5, cy = s.at().getY(), cz = s.at().getZ() + 0.5;
        if (s.depth() == 0) {
            if (R.nextFloat() < 0.9F) {
                double o = (R.nextDouble() - 0.5) * 3.0, w = (R.nextDouble() - 0.5) * 0.5;
                spawn(mc, CaveContent.GLINT.get(), cx + (alongX ? o : w), cy + 1.7 + R.nextDouble() * 0.4, cz + (alongX ? w : o),
                        0, -0.17 - R.nextDouble() * 0.08, 0, 0.78F, 0.96F, 1F);
            }
            if (R.nextFloat() < 0.07F) {
                double o = (R.nextDouble() - 0.5) * 2.4;
                spawn(mc, CaveContent.GLOW.get(), cx + (alongX ? o : 0), cy - 0.2 + R.nextDouble() * 2.0, cz + (alongX ? 0 : o),
                        0, 0.002, 0, 0.5F, 0.86F, 0.98F);
            }
        } else {
            if (R.nextFloat() < 0.09F) {
                spawn(mc, CaveContent.GLOW.get(), cx + (R.nextDouble() - 0.5) * 2.4, cy + 0.25 + R.nextDouble() * 0.4, cz + (R.nextDouble() - 0.5) * 2.4,
                        0, 0.003, 0, 0.45F, 1F, 0.72F);
            }
            if (R.nextFloat() < 0.3F) {
                spawn(mc, CaveContent.GLINT.get(), cx + (R.nextDouble() - 0.5) * 4.5, cy + 0.05, cz + (R.nextDouble() - 0.5) * 4.5,
                        (R.nextDouble() - 0.5) * 0.01, 0.012 + R.nextDouble() * 0.014, (R.nextDouble() - 0.5) * 0.01, 0.7F, 1F, 0.82F);
            }
        }
    }

    private static void spawn(Minecraft mc, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z,
                              double xa, double ya, double za, float r, float g, float b) {
        var p = mc.particleEngine.createParticle(type, x, y, z, xa, ya, za);
        if (p instanceof Wisp w) w.setColor(r, g, b);
    }

    /** What a wisp is. */
    enum Kind { MIST, DRAUGHT, GLOW, GLINT }

    /**
     * One wisp: mist (big, slow, rising, swelling), a draught's streak (small, quick, along the air), or a grotto's drawn
     * light: a glow (a soft halo, full bright, barely moving) or a glint (a small bright mote sliding down the curtain or
     * rising off the pool).
     */
    static final class Wisp extends SingleQuadParticle {
        private final Kind kind;
        private final float base, peak;

        Wisp(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, Kind kind) {
            super(level, x, y, z, xa, ya, za, sprites.get(level.getRandom()));
            this.kind = kind;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.gravity = 0F;
            this.friction = switch (kind) {
                case MIST -> 0.995F;
                case DRAUGHT -> 0.97F;
                case GLOW -> 0.98F;
                case GLINT -> 0.99F;
            };
            this.hasPhysics = false;
            this.lifetime = switch (kind) {
                case MIST -> 120 + random.nextInt(80);
                case DRAUGHT -> 30 + random.nextInt(20);
                case GLOW -> 50 + random.nextInt(40);
                case GLINT -> 16 + random.nextInt(10);
            };
            this.quadSize = switch (kind) {
                case MIST -> 1.3F + random.nextFloat() * 1.0F;
                case DRAUGHT -> 0.35F + random.nextFloat() * 0.25F;
                case GLOW -> 1.4F + random.nextFloat() * 0.9F;
                case GLINT -> 0.07F + random.nextFloat() * 0.07F;
            };
            this.base = quadSize;
            this.peak = switch (kind) {
                case MIST -> 0.28F + random.nextFloat() * 0.1F;
                case DRAUGHT -> 0.32F;
                case GLOW -> 0.2F + random.nextFloat() * 0.1F;
                case GLINT -> 0.85F;
            };
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            float c = kind == Kind.MIST ? 0.92F + random.nextFloat() * 0.06F : 0.86F;
            setColor(c, c + 0.02F, kind == Kind.MIST ? c + 0.03F : 1F);
            this.alpha = 0F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            // mist is lit by the sky that reaches it; the draught keeps a little light of its own so it shows in the dark;
            // a grotto's glow and glints are its light
            int c = super.getLightCoords(a);
            return switch (kind) {
                case MIST -> c;
                case DRAUGHT -> LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), 5), LightCoordsUtil.sky(c));
                case GLOW, GLINT -> LightCoordsUtil.FULL_BRIGHT;
            };
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            float in = kind == Kind.MIST ? 4F : kind == Kind.GLOW ? 3F : 6F, out = kind == Kind.MIST ? 1.6F : kind == Kind.GLOW ? 2F : 2.5F;
            alpha = peak * Math.min(1F, Math.min(t * in, (1F - t) * out));
            quadSize = base * switch (kind) {
                case MIST -> 1F + 1.2F * t;
                case GLOW -> 1F + 0.3F * t;
                case GLINT -> 1F - 0.4F * t;
                default -> 1F + 0.5F * t;
            };
            if (kind == Kind.MIST || kind == Kind.GLOW) {
                // thins to nothing round the camera: you walk out of mist (or a glow), never into a white wall
                var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
                double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                alpha *= (float) Math.max(0, Math.min(1, (d - (kind == Kind.MIST ? 2.5 : 1.0)) / 6.0));
            }
            oRoll = roll;
            roll += kind == Kind.MIST ? 0.004F : 0.01F;
        }
    }
}
