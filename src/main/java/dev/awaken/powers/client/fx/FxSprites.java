package dev.awaken.powers.client.fx;

import dev.awaken.powers.fx.PowerFxParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jspecify.annotations.Nullable;

/**
 * The FX sprite sets (one per {@link PowerFxParticles.Sprite}, loaded by the particle engine from
 * {@code particles/power_fx_*.json}) and a plain particle for when a type is spawned through the vanilla engine.
 */
final class FxSprites {
    private FxSprites() {}

    private static final SpriteSet[] SETS = new SpriteSet[PowerFxParticles.SPRITES.size()];

    static void register(RegisterParticleProvidersEvent e) {
        for (PowerFxParticles.Sprite s : PowerFxParticles.SPRITES) {
            e.registerSpriteSet(s.type().get(), sprites -> {
                SETS[s.index()] = sprites;
                return new Provider(sprites, s);
            });
        }
    }

    /** The frame of a sprite, or null before resources have loaded. */
    static @Nullable TextureAtlasSprite get(int sprite, int frame) {
        SpriteSet set = sprite >= 0 && sprite < SETS.length ? SETS[sprite] : null;
        if (set == null) return null;
        int frames = PowerFxParticles.SPRITES.get(sprite).frames();
        try {
            if (frames <= 1) return set.first();
            int f = Math.floorMod(frame, frames);
            return set.get(f, frames - 1);
        } catch (RuntimeException notLoaded) {
            return null;
        }
    }

    static int frames(int sprite) {
        return PowerFxParticles.SPRITES.get(sprite).frames();
    }

    /** A power FX sprite spawned like any particle (commands, other mods): drifts, animates, fades. */
    static final class Plain extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final boolean additive;
        private final int frames;

        Plain(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, PowerFxParticles.Sprite s) {
            super(level, x, y, z, xa, ya, za, sprites.first());
            this.sprites = sprites;
            this.additive = s.additive();
            this.frames = s.frames();
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.friction = 0.92f;
            this.hasPhysics = false;
            this.lifetime = 14 + random.nextInt(10);
            pick();
        }

        private void pick() {
            if (frames > 1) setSprite(sprites.get(Math.min(frames - 1, age * frames / Math.max(1, lifetime)), frames - 1));
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            return additive ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(a);
        }

        @Override
        public void tick() {
            super.tick();
            pick();
            alpha = 1f - (float) age / lifetime;
        }
    }

    record Provider(SpriteSet sprites, PowerFxParticles.Sprite s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za,
                                       RandomSource random) {
            return new Plain(level, x, y, z, xa, ya, za, sprites, s);
        }
    }
}
