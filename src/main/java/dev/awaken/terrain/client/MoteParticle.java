package dev.awaken.terrain.client;

import dev.awaken.terrain.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * What drifts over the second pair of Awakened biomes ({@link LandsMotes}), Awaken's own particle:
 * <ul>
 * <li>a <b>glint</b>: sun caught on glass for a blink, a four-pointed star that swells and is gone, where it was born;</li>
 * <li>a <b>spark</b>: the fault's light breaking loose, rising fast and flickering, bending with the wind;</li>
 * <li>a <b>dust</b> mote: a grain of the split rock carried low on the wind (lit by the world, not glowing).</li>
 * </ul>
 * Moves itself (no collision, nothing allocated per tick or frame).
 */
final class MoteParticle extends net.minecraft.client.particle.SingleQuadParticle {
    static final int GLINT = 0, SPARK = 1, DUST = 2;

    private final int kind;
    private final float baseSize, baseAlpha;
    private final Quaternionf rot = new Quaternionf();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    private float phase, flicker;
    private int light;

    MoteParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int rgb, float size, int kind) {
        super(level, x, y, z, sprite);
        this.kind = kind;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.xd = this.yd = this.zd = 0;
        this.baseSize = size;
        this.quadSize = kind == GLINT ? 0F : size;
        this.setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        this.baseAlpha = kind == DUST ? 0.55F : 1F;
        this.alpha = 0F;
        this.lifetime = switch (kind) {
            case GLINT -> 6 + random.nextInt(9);
            case SPARK -> 30 + random.nextInt(40);
            default -> 90 + random.nextInt(90);
        };
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.flicker = 0.3F + random.nextFloat() * 0.4F;
        this.roll = this.oRoll = kind == GLINT ? random.nextFloat() * 0.6F - 0.3F : random.nextFloat() * Mth.TWO_PI;
        this.yd = kind == SPARK ? 0.035 + random.nextFloat() * 0.05 : 0;
        this.light = kind == DUST ? readLight() : LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        long t0 = System.nanoTime();
        tickMote();
        LandsMotes.particleNanos += System.nanoTime() - t0;
    }

    private void tickMote() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (++age >= lifetime) {
            remove();
            return;
        }
        float life = age / (float) lifetime;
        if (kind == GLINT) {
            // a blink: swells to full and shrinks away, turning a little
            float s = Mth.sin(life * Mth.PI);
            quadSize = baseSize * (0.35F + 0.65F * s);
            alpha = baseAlpha * s;
            roll += 0.04F;
            return;
        }
        Wind.Field wind = ClientWind.field();
        float strength = wind == null ? 0.2F : wind.strength(x, z);
        float dirX = wind == null ? 1F : wind.dirX, dirZ = wind == null ? 0F : wind.dirZ;
        phase += 0.13F;
        if (kind == SPARK) {
            // rising fast, slowing, leaning downwind; a flicker in its light
            yd *= 0.975;
            xd += (dirX * 0.03F * strength - xd) * 0.08;
            zd += (dirZ * 0.03F * strength - zd) * 0.08;
            x += xd + Mth.cos(phase * 1.7F) * 0.01;
            y += yd;
            z += zd + Mth.sin(phase * 1.3F) * 0.01;
            float inOut = Math.min(1F, Math.min(life * 10F, (1F - life) * 3F));
            alpha = baseAlpha * inOut * (1F - flicker + flicker * Mth.abs(Mth.sin(phase * 2.9F)));
            quadSize = baseSize * (0.6F + 0.4F * (1F - life));
            return;
        }
        // dust: carried low on the wind, rising and falling a little
        float speed = 0.02F + 0.1F * strength * strength;
        xd += (dirX * speed - xd) * 0.05;
        zd += (dirZ * speed - zd) * 0.05;
        x += xd;
        y += Mth.sin(phase * 0.5F) * 0.006;
        z += zd;
        roll += 0.01F;
        alpha = baseAlpha * Math.min(1F, Math.min(life * 5F, (1F - life) * 4F));
        if ((age & 7) == 0) light = readLight();
    }

    private int readLight() {
        at.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return level.hasChunkAt(at) ? LightCoordsUtil.getLightCoords(level, at) : 15728640;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return light;
    }

    @Override
    public void extract(QuadParticleRenderState out, Camera camera, float partialTick) {
        long t0 = System.nanoTime();
        if (quadSize > 0.001F && alpha > 0.01F) {
            rot.set(camera.rotation()).rotateZ(Mth.lerp(partialTick, oRoll, roll));
            extractRotatedQuad(out, camera, rot, partialTick);
        }
        LandsMotes.extractNanos += System.nanoTime() - t0;
    }
}
