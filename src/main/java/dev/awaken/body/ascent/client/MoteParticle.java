package dev.awaken.body.ascent.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A mote of the Signal's light: a spark where a rope bites, the shocked air of a sonic boom, a feather shed off the
 * wings. Full bright, it shrinks and thins as it dies; {@link #next} sets the next one's colour, size, life, gravity
 * and whether it collides (client thread only, just before spawning).
 */
final class MoteParticle extends SingleQuadParticle {
    private static int nextTint = 0xFFFFFF, nextLife = 10;
    private static float nextSize = 1F, nextGravity = 0F;
    private static boolean nextPhysics, nextLit;

    static void next(int tint, float size, int life, float gravity, boolean physics) {
        nextTint = tint;
        nextSize = size;
        nextLife = life;
        nextGravity = gravity;
        nextPhysics = physics;
        nextLit = false;
    }

    /** As {@link #next}, lit by the world instead of glowing: grit knocked off rock (the rappel's kick). */
    static void nextGrit(int tint, float size, int life, float gravity) {
        next(tint, size, life, gravity, true);
        nextLit = true;
    }

    private final boolean lit = nextLit;

    private final SpriteSet sprites;
    private final float size0;

    MoteParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.sprites = sprites;
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
        this.friction = 0.88F;
        this.gravity = nextGravity;
        this.hasPhysics = nextPhysics;
        this.lifetime = Math.max(2, nextLife + this.random.nextInt(4) - 2);
        this.size0 = (0.06F + this.random.nextFloat() * 0.05F) * nextSize;
        this.quadSize = size0;
        int c = nextTint;
        this.setColor(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F);
        this.alpha = 0.95F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float a) {
        return lit ? super.getLightCoords(a) : 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        float life = (float) this.age / this.lifetime;
        this.alpha = 0.95F * (1F - life * life);
        this.quadSize = size0 * (1F - 0.6F * life);
    }

    record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xa, double ya, double za, RandomSource random) {
            return new MoteParticle(level, x, y, z, xa, ya, za, sprites);
        }
    }
}
