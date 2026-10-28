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
 * chute's mouth (pale streaks drifting out of it), a grotto's own light (its curtain and its pool), and a mouth's tells
 * (SPEC §34.1): its breath (mist exhaled out of the arch at dawn and after rain, seen from 100+ m), its draught (streaks
 * drifting out) and the cold air heard within 32 m. Our own sprites (tools/art/specs/caves.py); a few a tick at most.
 * Far mist goes straight to the particle engine: the level's own path drops anything past 32 blocks.
 */
final class CaveFx {
    private CaveFx() {}

    private static List<CaveNet.Site> sites = List.of();
    private static final RandomSource R = RandomSource.create();
    // a mouth's draught, heard: the loop at the nearest mouth and the one fading after a move
    private static final SenseLoop[] DRAUGHT = new SenseLoop[2];
    /** Game time until which the ground counts as just rained on (the mouths breathe after rain). */
    private static long wetUntil;

    /** The cave places near you the server named (mouths, sinkholes, chutes...), for the shafts and the breath. */
    static List<CaveNet.Site> sites() {
        return sites;
    }

    static void near(CaveNet.Near m) {
        sites = List.copyOf(m.sites());
    }

    static void reset() {
        sites = List.of();
        for (int i = 0; i < 2; i++) {
            if (DRAUGHT[i] != null) DRAUGHT[i].release();
            DRAUGHT[i] = null;
        }
        wetUntil = 0;
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
        if (level == null || mc.player == null || mc.isPaused()) return;
        if (level.getRainLevel(1F) > 0.2F) wetUntil = level.getGameTime() + 3600;
        if (sites.isEmpty()) {
            if (DRAUGHT[0] != null) draught(mc, 0F, 0, 0, 0);
            return;
        }
        long time = level.getOverworldClockTime() % 24000L;
        boolean far = mc.options.particles().get() != net.minecraft.server.level.ParticleStatus.MINIMAL;
        // a mouth's breath: strongest at dawn and for three minutes after rain, faint by day, a little at dusk
        float breath = Math.max(time < 1500 ? 1F : time < 4000 ? 1F - (time - 1500) / 2900F : time < 11800 ? 0.14F : time < 13200 ? 0.35F : 0.08F,
                level.getGameTime() < wetUntil ? 0.9F : 0F);
        float heard = 0F;
        double hx = 0, hy = 0, hz = 0;
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
                    if (far) mc.particleEngine.createParticle(CaveContent.MIST.get(), s.at().getX() + 0.5 + Math.cos(a) * r, y, s.at().getZ() + 0.5 + Math.sin(a) * r,
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
            } else if (s.kind() == CaveKind.MOUTH.ordinal()) {
                mouth(mc, s, d2, breath, far);
                double dy = s.at().getY() + 1.5 - mc.player.getEyeY();
                float d = (float) Math.sqrt(d2 + dy * dy);
                // the cold air coming out: heard within 32 m, louder at the lip, the nearest mouth only
                float v = d < 32F ? 0.45F * 8F / (8F + d) * (1F - Mth.clamp((d - 24F) / 8F, 0F, 1F)) : 0F;
                if (v > heard) {
                    heard = v;
                    hx = s.at().getX() + 0.5;
                    hy = s.at().getY() + 1.5;
                    hz = s.at().getZ() + 0.5;
                }
            }
        }
        draught(mc, heard, hx, hy, hz);
    }

    /**
     * A mouth's tells: its breath (pale lobes exhaled out of the arch's upper half, drifting out and climbing past the lip,
     * from as far as 160 m) and its draught (streaks out of the throat, within 28 m). The site's radius is the arch's
     * width; its depth packs the turn (forward = into the hill), the form and the arch's height.
     */
    private static void mouth(Minecraft mc, CaveNet.Site s, double d2, float breath, boolean far) {
        int rot = s.depth() & 3, h = Math.max(4, s.depth() >> 3), w = Math.max(3, s.radius());
        double fx = rot == 1 ? -1 : rot == 3 ? 1 : 0, fz = rot == 0 ? 1 : rot == 2 ? -1 : 0;
        double cx = s.at().getX() + 0.5, cy = s.at().getY(), cz = s.at().getZ() + 0.5;
        if (far && d2 < 160 * 160) {
            float rate = breath * (0.25F + w / 24F) * (d2 < 64 * 64 ? 1F : 0.6F);
            if (R.nextFloat() < rate) {
                // just inside the arch, high up: a breath leaves by the top of a mouth
                double o = (R.nextDouble() - 0.5) * w * 0.7, in = 1.0 + R.nextDouble() * 2.5;
                mc.particleEngine.createParticle(CaveContent.MIST.get(), cx + fx * in - fz * o, cy + h * (0.45 + R.nextDouble() * 0.4), cz + fz * in + fx * o,
                        -fx * (0.03 + R.nextDouble() * 0.02), 0.02 + R.nextDouble() * 0.015, -fz * (0.03 + R.nextDouble() * 0.02));
            }
        }
        if (d2 < 28 * 28 && R.nextFloat() < 0.4F) {
            double o = (R.nextDouble() - 0.5) * (w - 1), in = 1.5 + R.nextDouble() * 3;
            mc.level.addParticle(CaveContent.DRAUGHT.get(), cx + fx * in - fz * o, cy + 0.3 + R.nextDouble() * (h - 1.5), cz + fz * in + fx * o,
                    -fx * (0.05 + R.nextDouble() * 0.03), 0.004, -fz * (0.05 + R.nextDouble() * 0.03));
        }
    }

    /** The draught's loop at the nearest mouth (the breath-of-air sound, lower and cold), fading across a move. */
    private static void draught(Minecraft mc, float goal, double x, double y, double z) {
        var sm = mc.getSoundManager();
        for (int i = 0; i < 2; i++) {
            if (DRAUGHT[i] != null && (DRAUGHT[i].isStopped() || DRAUGHT[i].age > 40 && !sm.isActive(DRAUGHT[i]))) DRAUGHT[i] = null;
        }
        SenseLoop cur = DRAUGHT[0];
        if (cur != null) {
            double dx = cur.getX() - x, dy = cur.getY() - y, dz = cur.getZ() - z;
            if (goal > 0.01F && dx * dx + dy * dy + dz * dz > 36 && DRAUGHT[1] == null) {
                cur.release();
                DRAUGHT[1] = cur;
                DRAUGHT[0] = cur = null;
            } else {
                cur.goal = goal;
            }
        }
        if (cur == null && goal > 0.015F) {
            SenseLoop l = new SenseLoop(dev.awaken.delve.sense.SenseModule.BREATH.get(), false, x, y, z, 0.78F, 0.03F);
            l.goal = goal;
            DRAUGHT[0] = l;
            sm.play(l);
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
