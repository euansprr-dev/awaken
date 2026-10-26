package dev.awaken.delve.client;

import dev.awaken.delve.DelveContent;
import dev.awaken.delve.Ore;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The deep's own particles (sprites from tools/art/specs/delve.py): sparks struck off hard rock (white-hot, cooling to
 * the ore's colour as they fly and fall), grit knocked off soft ground, dust trickling from a roof about to fall, an
 * ore's glint where light touches a lode, and the Deep's motes off Deepglass. A tint set just before spawning colours
 * the next one (client thread only); a glint spawned by a block carries its ore in its y speed instead.
 */
public final class DelveParticles {
    private DelveParticles() {}

    static int nextTint = 0xFFFFFF;
    static float nextSize = 1F;

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(DelveContent.SPARK.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.SPARK));
        e.registerSpriteSet(DelveContent.GRIT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.GRIT));
        e.registerSpriteSet(DelveContent.DUST.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.DUST));
        e.registerSpriteSet(DelveContent.GLINT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.GLINT));
        e.registerSpriteSet(DelveContent.MOTE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.MOTE));
    }

    static void spawn(ClientLevel level, SimpleParticleType type, int tint, float size, double x, double y, double z, double xa, double ya, double za) {
        nextTint = tint;
        nextSize = size;
        level.addParticle(type, x, y, z, xa, ya, za);
        nextTint = 0xFFFFFF;
        nextSize = 1F;
    }

    static final class Bit extends SingleQuadParticle {
        static final int SPARK = 0, GRIT = 1, DUST = 2, GLINT = 3, MOTE = 4;
        private final SpriteSet sprites;
        private final int kind;
        private final float base, r0, g0, b0;

        Bit(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, int kind) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.kind = kind;
            int c = nextTint;
            if (kind == GLINT && ya >= 1 && ya <= Ore.values().length) {
                c = Ore.values()[(int) ya - 1].tint;   // a heart's own glint: its ore in the speed, standing still
                xa = ya = za = 0;
            }
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            float shade = 0.92F + random.nextFloat() * 0.16F;
            r0 = Math.min(1F, ((c >> 16) & 255) / 255F * shade);
            g0 = Math.min(1F, ((c >> 8) & 255) / 255F * shade);
            b0 = Math.min(1F, (c & 255) / 255F * shade);
            setColor(r0, g0, b0);
            switch (kind) {
                case SPARK -> {
                    gravity = 0.9F;
                    friction = 0.9F;
                    hasPhysics = true;
                    lifetime = 7 + random.nextInt(7);
                    quadSize = (0.035F + random.nextFloat() * 0.03F) * nextSize;
                    setColor(1F, 0.97F, 0.88F);   // white-hot at the bite
                }
                case GRIT -> {
                    gravity = 0.8F;
                    friction = 0.92F;
                    hasPhysics = true;
                    lifetime = 14 + random.nextInt(12);
                    quadSize = (0.04F + random.nextFloat() * 0.04F) * nextSize;
                }
                case DUST -> {
                    gravity = 0.06F;
                    friction = 0.96F;
                    hasPhysics = false;
                    lifetime = 30 + random.nextInt(20);
                    quadSize = (0.03F + random.nextFloat() * 0.035F) * nextSize;
                }
                case GLINT -> {
                    gravity = 0F;
                    friction = 0.8F;
                    hasPhysics = false;
                    lifetime = 12 + random.nextInt(12);
                    quadSize = (0.07F + random.nextFloat() * 0.05F) * nextSize;
                }
                default -> {
                    gravity = -0.004F;
                    friction = 0.985F;
                    hasPhysics = false;
                    lifetime = 40 + random.nextInt(30);
                    quadSize = (0.04F + random.nextFloat() * 0.04F) * nextSize;
                }
            }
            base = quadSize;
            setSpriteFromAge(sprites);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            if (kind == GRIT) return super.getLightCoords(a);
            if (kind == DUST) {
                // dust catches what light there is: never quite black, or a cave-in's cloud would vanish in the dark
                int c = super.getLightCoords(a);
                return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), 6), LightCoordsUtil.sky(c));
            }
            return LightCoordsUtil.FULL_BRIGHT;
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            switch (kind) {
                case SPARK -> {
                    // white-hot to the ore's colour, then out
                    setColor(1F + (r0 - 1F) * t, 0.97F + (g0 - 0.97F) * t, 0.88F + (b0 - 0.88F) * t);
                    alpha = 1F - t * t;
                    quadSize = base * (1F - 0.5F * t);
                }
                case GLINT -> {
                    // a star's twinkle: in quick, a held peak, out slow
                    alpha = Math.min(1F, Math.min(t * 6F, (1F - t) * 2F));
                    quadSize = base * (0.6F + 0.6F * (float) Math.sin(t * Math.PI));
                }
                case MOTE -> {
                    alpha = Math.min(1F, Math.min(t * 4F, (1F - t) * 2F)) * 0.85F;
                    quadSize = base * (0.8F + 0.25F * (float) Math.sin(t * Math.PI * 4));
                }
                case DUST -> {
                    // billows as it falls and spreads, then thins away
                    alpha = 0.75F * Math.min(1F, Math.min(t * 8F, (1F - t) * 3F));
                    quadSize = base * (1F + 1.3F * t);
                }
                default -> alpha = 0.9F * (1F - t * t);
            }
        }
    }
}
