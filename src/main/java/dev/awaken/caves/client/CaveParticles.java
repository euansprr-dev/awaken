package dev.awaken.caves.client;

import dev.awaken.caves.skin.Drips;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The skin's drips (SPEC §42.4): a drop swells at a stalactite's tip, lets go, falls, and throws a small crown of beads
 * where it lands; and the regions' motes (§42.7). Sprites from tools/art/specs/deep_skin.py (drip: four frames gathering
 * to falling; splash: four of the crown rising and falling back; spores, pollen, warm dust; mist, mica and glitter
 * glints, ash, seep).
 */
final class CaveParticles {
    private CaveParticles() {}

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Drips.DRIP.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Drop(l, x, y, z, s));
        e.registerSpriteSet(Drips.SPLASH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Splash(l, x, y, z, s));
        e.registerSpriteSet(Drips.SPORE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s.get(r), 0.035F, 0.0035F, 3, 0.9F));
        e.registerSpriteSet(Drips.POLLEN.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s.get(r), 0.03F, 0.0015F, 2, 0.9F));
        e.registerSpriteSet(Drips.WARM_DUST.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s.get(r), 0.03F, 0.001F, 2, 0.9F));
        // mist: wide and faint, hanging; ash falls, one flake in three still an ember (sprite 0, which glows); seep rises
        e.registerSpriteSet(Drips.MIST.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s.get(r), 0.32F, 0F, 2, 0.3F));
        e.registerSpriteSet(Drips.ASH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> {
            int k = r.nextInt(3);
            return new Mote(l, x, y, z, s.get(k, 2), 0.04F, 0.006F, k == 0 ? 12 : 2, 0.9F);
        });
        e.registerSpriteSet(Drips.SEEP.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Mote(l, x, y, z, s.get(r), 0.035F, -0.005F, 8, 0.8F));
        e.registerSpriteSet(Drips.MICA.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Glint(l, x, y, z, s, 0.05F, 3));
        e.registerSpriteSet(Drips.GLITTER.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Glint(l, x, y, z, s, 0.045F, 5));
    }

    /**
     * A mote of the regions' air (SPEC §42.7, §42.8): spores and ash sinking slowly, pollen, warm dust and mist hanging,
     * seep rising, each drifting on the cave's slow draught with a little sway; it fades in and out to its peak, and never
     * goes quite black (the floor of its light), so a torch shows the air full of it.
     */
    static final class Mote extends SingleQuadParticle {
        private final float sway;
        private final int dark;
        private final float peak;

        Mote(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, float size, float sink, int dark, float peak) {
            super(level, x, y, z, 0, 0, 0, sprite);
            this.dark = dark;
            this.peak = peak;
            xd = (random.nextFloat() - 0.5F) * 0.006F;
            yd = -sink * (0.6F + random.nextFloat() * 0.8F);
            zd = (random.nextFloat() - 0.5F) * 0.006F;
            gravity = 0;
            friction = 1F;
            hasPhysics = false;
            lifetime = 100 + random.nextInt(80);
            quadSize = size * (0.7F + random.nextFloat() * 0.6F);
            sway = random.nextFloat() * 6.28F;
            alpha = 0F;
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float partial) {
            int c = super.getLightCoords(partial);
            return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), dark), LightCoordsUtil.sky(c));
        }

        @Override
        public void tick() {
            super.tick();
            float t = age + sway * 20F;
            xd += Math.sin(t * 0.05F) * 0.00015F;
            zd += Math.cos(t * 0.043F) * 0.00015F;
            alpha = Math.min(1F, Math.min(age / 25F, (lifetime - age) / 35F)) * peak;
        }
    }

    /**
     * A glint (SPEC §42.8): the Windcleft's mica catching your light on a wall, the Glimmer Hollows' glittering dust: it
     * stays put and twinkles once, its three sprites growing to the brightest and back, over half a second or so.
     */
    static final class Glint extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final int dark;

        Glint(ClientLevel level, double x, double y, double z, SpriteSet sprites, float size, int dark) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.dark = dark;
            xd = yd = zd = 0;
            gravity = 0;
            hasPhysics = false;
            lifetime = 8 + random.nextInt(10);
            quadSize = size * (0.7F + random.nextFloat() * 0.6F);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float partial) {
            int c = super.getLightCoords(partial);
            return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), dark), LightCoordsUtil.sky(c));
        }

        @Override
        public void tick() {
            super.tick();
            // up to the brightest at the middle of its life, then back
            int half = Math.max(1, lifetime / 2);
            setSprite(sprites.get(age < half ? age * 2 / half : Math.max(0, (lifetime - age) * 2 / half), 2));
        }
    }

    /** The drop: hangs while it swells, then falls; on landing it splashes. */
    static final class Drop extends SingleQuadParticle {
        private final SpriteSet sprites;
        private final int hang;

        Drop(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            this.hang = 14 + random.nextInt(16);
            xd = yd = zd = 0;
            gravity = 0;
            friction = 1F;
            hasPhysics = true;
            lifetime = hang + 60;
            quadSize = 0.045F;
            setSprite(sprites.get(0, 3));
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float partial) {
            // water catches what light there is: a drop never goes quite black
            int c = super.getLightCoords(partial);
            return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(c), 3), LightCoordsUtil.sky(c));
        }

        @Override
        public void tick() {
            if (age < hang) {
                age++;
                setSprite(sprites.get(Math.min(2, age * 3 / hang), 3));   // swelling
                return;
            }
            if (age == hang) {
                gravity = 0.9F;
                friction = 0.98F;
                setSprite(sprites.get(3, 3));                          // long as it falls
            }
            super.tick();
            if (onGround || removed) {
                level.addParticle(Drips.SPLASH.get(), x, y + 0.02, z, 0, 0, 0);
                remove();
            }
        }
    }

    static final class Splash extends SingleQuadParticle {
        private final SpriteSet sprites;

        Splash(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.first());
            this.sprites = sprites;
            xd = yd = zd = 0;
            gravity = 0;
            hasPhysics = false;
            lifetime = 7;
            quadSize = 0.09F;
            setSpriteFromAge(sprites);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
            alpha = 1F - (float) age / lifetime * 0.6F;
        }
    }
}
