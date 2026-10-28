package dev.awaken.delve.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

/**
 * A chip of the struck block (SPEC §34.3): a small square cut from the block's own texture, tumbling as it flies (the
 * quad turns about its own axis, thinning to an edge and back, as {@code FallingLeafParticle} does), lit by the world's
 * light, bouncing once off the floor, then lying flat and fading by shrinking. Moves itself against block shapes (a point
 * test an axis at a time, so it can bounce, which vanilla's particle collision can't); nothing allocated per tick.
 */
final class StrikeChip extends net.minecraft.client.particle.SingleQuadParticle {
    private static final int FLYING = 0, RESTING = 1;
    /** The texture square: 3 of the sprite's 16 pixels. */
    private static final float CUT = 3F / 16F;

    private final Layer layer;
    private final float uo, vo, base;
    private final Quaternionf rot = new Quaternionf();
    private final Quaternionf rest = new Quaternionf();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    private int state = FLYING, light, bounces, restTicks;
    private float flip, oFlip, flipSpeed, spin;

    StrikeChip(ClientLevel level, double x, double y, double z, double xa, double ya, double za, BlockState block, BlockPos from, float size) {
        super(level, x, y, z, Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(block, level, from).sprite());
        this.layer = Layer.bySprite(sprite);
        this.hasPhysics = false;
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
        // a lit chip of the face: a little brighter than vanilla's crumbs (0.6), which read as soot in a lamp's light
        float shade = 0.72F + random.nextFloat() * 0.1F;
        float r = shade, g = shade, b = shade;
        BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(block, 0);
        if (tint != null) {
            int c = tint.colorAsTerrainParticle(block, level, from);
            r *= ((c >> 16) & 255) / 255F;
            g *= ((c >> 8) & 255) / 255F;
            b *= (c & 255) / 255F;
        }
        setColor(r, g, b);
        this.quadSize = this.base = size * (0.75F + random.nextFloat() * 0.5F);
        this.uo = random.nextFloat() * (1F - CUT);
        this.vo = random.nextFloat() * (1F - CUT);
        this.lifetime = 16 + random.nextInt(32);   // 0.8-1.6 s on the floor after flight, at most
        this.flip = this.oFlip = random.nextFloat() * Mth.TWO_PI;
        this.flipSpeed = 0.35F + random.nextFloat() * 0.5F;
        this.spin = (random.nextFloat() - 0.5F) * 0.6F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.light = readLight();
    }

    @Override
    protected Layer getLayer() {
        return layer;
    }

    @Override
    protected float getU0() {
        return sprite.getU(uo);
    }

    @Override
    protected float getU1() {
        return sprite.getU(uo + CUT);
    }

    @Override
    protected float getV0() {
        return sprite.getV(vo);
    }

    @Override
    protected float getV1() {
        return sprite.getV(vo + CUT);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        oFlip = flip;
        if (state == FLYING) {
            if (++age > 60) settle(14 + random.nextInt(18));
            else fly();
        } else if (--restTicks <= 0) {
            remove();
            return;
        } else if (restTicks < 10) {
            quadSize = base * restTicks / 10F;
        }
        if ((age & 3) == 0) light = readLight();
    }

    private void fly() {
        yd -= 0.045;
        xd *= 0.96;
        yd *= 0.98;
        zd *= 0.96;
        flip += flipSpeed;
        roll += spin;
        // an axis at a time, so a wall stops only its own axis
        if (solid(x + xd, y, z)) xd = -xd * 0.3;
        else x += xd;
        if (solid(x, y, z + zd)) zd = -zd * 0.3;
        else z += zd;
        if (solid(x, y + yd, z)) {
            if (yd < 0) {
                if (bounces++ == 0 && yd < -0.09) {
                    yd = -yd * 0.32;
                    xd *= 0.6;
                    zd *= 0.6;
                    flipSpeed *= 0.5F;
                } else {
                    y = floor(x, y + yd, z) + 0.005;
                    settle(lifetime);
                }
            } else {
                yd = 0;
            }
        } else {
            y += yd;
        }
    }

    private boolean solid(double px, double py, double pz) {
        at.set(Mth.floor(px), Mth.floor(py), Mth.floor(pz));
        if (!level.hasChunkAt(at)) return true;
        BlockState s = level.getBlockState(at);
        if (s.isAir()) return false;
        VoxelShape shape = s.getCollisionShape(level, at);
        if (shape.isEmpty()) return false;
        double lx = px - at.getX(), ly = py - at.getY(), lz = pz - at.getZ();
        return lx >= shape.min(Direction.Axis.X) && lx <= shape.max(Direction.Axis.X) && ly >= shape.min(Direction.Axis.Y)
                && ly <= shape.max(Direction.Axis.Y) && lz >= shape.min(Direction.Axis.Z) && lz <= shape.max(Direction.Axis.Z);
    }

    /** The top of the shape the chip fell into (the {@link #solid} it hit). */
    private double floor(double px, double py, double pz) {
        at.set(Mth.floor(px), Mth.floor(py), Mth.floor(pz));
        VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
        return shape.isEmpty() ? py : at.getY() + shape.max(Direction.Axis.Y);
    }

    private void settle(int ticks) {
        state = RESTING;
        restTicks = Math.max(12, ticks);
        xd = yd = zd = 0;
        // lying flat, face up, turned any way
        rest.identity().rotateY(random.nextFloat() * Mth.TWO_PI).rotateX(-Mth.HALF_PI);
    }

    private int readLight() {
        at.set(Mth.floor(x), Mth.floor(y + 0.05), Mth.floor(z));
        return level.hasChunkAt(at) ? LightCoordsUtil.getLightCoords(level, at) : LightCoordsUtil.pack(0, 15);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return light;
    }

    @Override
    public void extract(QuadParticleRenderState out, Camera camera, float partialTick) {
        if (state == RESTING) {
            rot.set(rest);
        } else {
            // face the camera, spin, then turn about the chip's own axis: never quite edge-on (particles are one-sided)
            float c = Math.max(0.15F, Math.abs(Mth.cos(Mth.lerp(partialTick, oFlip, flip))));
            rot.set(camera.rotation()).rotateZ(Mth.lerp(partialTick, oRoll, roll)).rotateX((float) Math.acos(c));
        }
        extractRotatedQuad(out, camera, rot, partialTick);
    }
}
