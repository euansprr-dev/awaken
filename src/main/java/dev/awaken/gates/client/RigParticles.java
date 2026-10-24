package dev.awaken.gates.client;

import dev.awaken.gates.GatesRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The Gate Rig's own particles (SPEC §17 "Gate Rig"; sprites from tools/art/specs/gate_rig.py): grit off a cracking
 * tile, sparks out of the cracks and off burning monsters, glints of light, mirror shards, a well's motes. A tint set
 * just before spawning colours the next one (client thread only), like the body's dust.
 */
public final class RigParticles {
    private RigParticles() {}

    /** Colour of the next particle made (0xRRGGBB), and its size. */
    static int nextTint = 0xFFFFFF;
    static float nextSize = 1F;

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(GatesRegistry.P_GRIT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.GRIT));
        e.registerSpriteSet(GatesRegistry.P_SPARK.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.SPARK));
        e.registerSpriteSet(GatesRegistry.P_GLINT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.GLINT));
        e.registerSpriteSet(GatesRegistry.P_SHARD.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.SHARD));
        e.registerSpriteSet(GatesRegistry.P_MOTE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Bit(l, x, y, z, xa, ya, za, s, Bit.MOTE));
    }

    /** One small sprite with its kind's motion: falling grit, rising sparks, drifting light, tumbling glass, a well's motes. */
    static final class Bit extends SingleQuadParticle {
        static final int GRIT = 0, SPARK = 1, GLINT = 2, SHARD = 3, MOTE = 4;
        private final SpriteSet sprites;
        private final int kind;
        private final float spin, base;

        Bit(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, int kind) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.kind = kind;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            int c = nextTint;
            float shade = 0.9F + random.nextFloat() * 0.2F;
            setColor(Math.min(1F, ((c >> 16) & 255) / 255F * shade), Math.min(1F, ((c >> 8) & 255) / 255F * shade), Math.min(1F, (c & 255) / 255F * shade));
            switch (kind) {
                case GRIT -> {
                    gravity = 0.06F;
                    friction = 0.9F;
                    hasPhysics = true;
                    lifetime = 18 + random.nextInt(16);
                    quadSize = (0.05F + random.nextFloat() * 0.05F) * nextSize;
                }
                case SPARK -> {
                    gravity = -0.015F;
                    friction = 0.92F;
                    hasPhysics = false;
                    lifetime = 10 + random.nextInt(10);
                    quadSize = (0.04F + random.nextFloat() * 0.04F) * nextSize;
                }
                case GLINT -> {
                    gravity = 0F;
                    friction = 0.9F;
                    hasPhysics = false;
                    lifetime = 16 + random.nextInt(14);
                    quadSize = (0.06F + random.nextFloat() * 0.06F) * nextSize;
                }
                case SHARD -> {
                    gravity = 0.05F;
                    friction = 0.96F;
                    hasPhysics = true;
                    lifetime = 24 + random.nextInt(20);
                    quadSize = (0.08F + random.nextFloat() * 0.08F) * nextSize;
                }
                default -> {
                    gravity = 0F;
                    friction = 0.99F;
                    hasPhysics = false;
                    lifetime = 30 + random.nextInt(24);
                    quadSize = (0.05F + random.nextFloat() * 0.05F) * nextSize;
                }
            }
            this.spin = kind == SHARD ? (random.nextFloat() - 0.5F) * 0.6F : 0F;
            this.base = quadSize;
            setSpriteFromAge(sprites);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            return kind == GRIT ? super.getLightCoords(a) : LightCoordsUtil.FULL_BRIGHT;
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            float t = (float) age / lifetime;
            oRoll = roll;
            roll += spin;
            switch (kind) {
                case GLINT, MOTE -> {
                    alpha = Math.min(1F, Math.min(t * 5F, (1F - t) * 2.5F));
                    quadSize = base * (0.75F + 0.35F * (float) Math.sin(t * Math.PI * 3));
                }
                case SPARK -> alpha = 1F - t * t;
                default -> alpha = 0.9F * (1F - t * t);
            }
        }
    }

    /** Spawns one with a tint (and a size factor) for the next sprite. */
    static void spawn(ClientLevel level, SimpleParticleType type, int tint, float size, double x, double y, double z, double xa, double ya, double za) {
        nextTint = tint;
        nextSize = size;
        level.addParticle(type, x, y, z, xa, ya, za);
    }
}
