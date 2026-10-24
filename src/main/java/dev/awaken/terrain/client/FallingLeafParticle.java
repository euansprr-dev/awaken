package dev.awaken.terrain.client;

import dev.awaken.terrain.Wind;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

/**
 * One falling leaf (or, as a mote, one glowing speck rising), Awaken's own particle: it tumbles (the quad turns about
 * its own horizontal axis, so it thins to an edge and widens again, the fake flip), flutters side to side across the
 * wind, drifts with the {@link Wind} at its own position and streams in gusts. On the ground or on water it lies flat
 * for 1-3 s, then fades. Moves itself (no vanilla collision, no allocation per tick); light is read once a tick.
 */
final class FallingLeafParticle extends net.minecraft.client.particle.SingleQuadParticle {
    static final int LEAF_SPRITES = 6;
    private static final int FALLING = 0, RESTING = 1, FADING = 2;
    private static final int FADE_TICKS = 20;

    private final boolean mote;
    private final float baseAlpha;
    private final Quaternionf rot = new Quaternionf();
    private final Quaternionf rest = new Quaternionf();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    private int state = FALLING, restTicks, fadeTicks, light;
    private float flip, oFlip, flipSpeed, flutter, flutterSpeed, flutterAmp, spinSpeed, pulse;

    FallingLeafParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, int rgb, float size, boolean mote) {
        super(level, x, y, z, sprite);
        this.mote = mote;
        this.hasPhysics = false;
        this.gravity = 0F;
        this.xd = this.yd = this.zd = 0;
        this.quadSize = size;
        this.setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        this.baseAlpha = mote ? 0.9F : 1F;
        this.alpha = mote ? 0F : 1F;
        this.lifetime = mote ? 120 + random.nextInt(100) : 500;
        this.flip = random.nextFloat() * Mth.TWO_PI;
        this.oFlip = flip;
        this.flipSpeed = 0.12F + random.nextFloat() * 0.22F;
        this.flutter = random.nextFloat() * Mth.TWO_PI;
        this.flutterSpeed = 0.09F + random.nextFloat() * 0.1F;
        this.flutterAmp = 0.02F + random.nextFloat() * 0.035F;
        this.spinSpeed = (random.nextFloat() - 0.5F) * 0.16F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.pulse = random.nextFloat() * Mth.TWO_PI;
        this.light = mote ? LightCoordsUtil.FULL_BRIGHT : readLight();
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        oFlip = flip;
        if (++age >= lifetime) {
            remove();
            return;
        }
        Wind.Field wind = ClientWind.field();
        if (wind == null) {
            remove();
            return;
        }
        if (mote) tickMote(wind);
        else if (state == FALLING) tickFalling(wind);
        else if (state == RESTING && --restTicks <= 0) state = FADING;
        if (state == FADING) {
            alpha = baseAlpha * (--fadeTicks / (float) FADE_TICKS);
            if (fadeTicks <= 0) remove();
        }
        if ((age & 3) == 0 && !mote) light = readLight();
    }

    private void tickFalling(Wind.Field wind) {
        float strength = wind.strength(x, z);
        float gust = wind.gust(x, z);
        // drift toward the wind's speed here (blocks a tick: a breeze 0.4 blocks/s, a strong gust ~3)
        float speed = 0.015F + 0.13F * strength * strength + 0.05F * gust;
        xd += (wind.dirX * speed - xd) * 0.07;
        zd += (wind.dirZ * speed - zd) * 0.07;
        // tumbling: falls faster edge-on, floats face-down; a gust lifts it a little
        flip += flipSpeed * (0.7F + 0.8F * strength);
        float edge = Math.abs(Mth.sin(flip));
        float fall = -(0.028F + 0.045F * edge) + 0.02F * gust;
        yd += (fall - yd) * 0.12;
        // flutter: swinging side to side across the wind
        flutter += flutterSpeed;
        float side = Mth.cos(flutter) * flutterAmp * (1F + strength);
        roll += spinSpeed * (1F + 2F * gust);
        double nx = x + xd - wind.dirZ * side, ny = y + yd + Math.abs(Mth.sin(flutter)) * 0.006, nz = z + zd + wind.dirX * side;
        if (land(nx, ny, nz)) return;
        x = nx;
        y = ny;
        z = nz;
    }

    /** True when the leaf came to rest in this move (on a block top or on water). */
    private boolean land(double nx, double ny, double nz) {
        at.set(Mth.floor(nx), Mth.floor(ny), Mth.floor(nz));
        if (!level.hasChunkAt(at)) {
            remove();
            return true;
        }
        FluidState fluid = level.getFluidState(at);
        if (fluid.is(FluidTags.WATER)) {
            double surface = at.getY() + fluid.getHeight(level, at);
            if (ny <= surface) return settle(nx, surface + 0.02, nz, 40 + random.nextInt(40));
        }
        BlockState st = level.getBlockState(at);
        if (!st.isAir()) {
            VoxelShape shape = st.getCollisionShape(level, at);
            if (!shape.isEmpty()) {
                double top = at.getY() + shape.max(Direction.Axis.Y);
                if (ny <= top) return settle(nx, top + 0.01, nz, 20 + random.nextInt(40));
            }
        }
        return false;
    }

    private boolean settle(double nx, double ny, double nz, int ticks) {
        x = nx;
        y = ny;
        z = nz;
        xd = yd = zd = 0;
        state = RESTING;
        restTicks = ticks;
        fadeTicks = FADE_TICKS;
        // lying flat, face up, turned any way
        rest.identity().rotateY(random.nextFloat() * Mth.TWO_PI).rotateX(-Mth.HALF_PI);
        return true;
    }

    private void tickMote(Wind.Field wind) {
        float strength = wind.strength(x, z);
        xd += (wind.dirX * 0.012F * strength - xd) * 0.05;
        zd += (wind.dirZ * 0.012F * strength - zd) * 0.05;
        flutter += flutterSpeed * 0.5F;
        yd += (0.012F + 0.006F * Mth.sin(flutter) - yd) * 0.1;
        x += xd + Mth.cos(flutter) * 0.004;
        y += yd;
        z += zd + Mth.sin(flutter * 0.7F) * 0.004;
        pulse += 0.11F;
        float life = age / (float) lifetime;
        float inOut = Math.min(1F, Math.min(life * 6F, (1F - life) * 4F));
        alpha = baseAlpha * inOut * (0.65F + 0.35F * Mth.sin(pulse));
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
        if (state != FALLING || mote) {
            if (mote) rot.set(camera.rotation());
            else rot.set(rest);
        } else {
            // face the camera, spin, then turn about the leaf's own horizontal axis: its height on screen is |cos(flip)|
            // (never quite edge-on, and never turned past the edge: particles are one-sided)
            float c = Math.max(0.12F, Math.abs(Mth.cos(Mth.lerp(partialTick, oFlip, flip))));
            rot.set(camera.rotation()).rotateZ(Mth.lerp(partialTick, oRoll, roll)).rotateX((float) Math.acos(c));
        }
        extractRotatedQuad(out, camera, rot, partialTick);
    }
}
