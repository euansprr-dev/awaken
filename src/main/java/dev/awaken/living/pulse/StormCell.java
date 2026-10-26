package dev.awaken.living.pulse;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Rolling Storm's cell as both sides see it (SPEC §21.6): where its base sits, where its rain falls, how it fades in
 * and out, how far its weather reaches, how late its thunder arrives, and how it is handed to Lens; and its strike. No
 * client classes, so the server, the client and the GameTest share one model. Sizes match
 * {@code shaders/include/lens_storm.glsl} and the no-Lens fallback's cells.
 */
public final class StormCell {
    private StormCell() {}

    /** The base sits this far over the ground the cell first stood on, within these heights. */
    public static final double BASE_ABOVE_GROUND = 92, BASE_MIN = 140, BASE_MAX = 215;
    /** From the base to the anvil's top. */
    public static final double HEIGHT = 300;
    /** The rain core: its radius, centred this far downwind of the cell's centre. */
    public static final double RAIN_RADIUS = 58, RAIN_AHEAD = 12;
    /** rolling_storm.json's {@code duration_s} (the client times the fade at its end by it; the GameTest checks). */
    public static final float DURATION_S = 240;
    /** Seconds it takes to build at its start and to rain itself out at its end. */
    public static final float FADE_IN_S = 12, FADE_OUT_S = 15;
    /** Its local weather: full rain within {@code RAIN_FULL} of the core's centre, none past {@code RAIN_EDGE}. */
    public static final double RAIN_FULL = 45, RAIN_EDGE = 110;
    /** Thunder's speed over the land, in blocks a second: the world's scale, not the metre's (a strike on the horizon
     * rolls in a few seconds later, as one a few kilometres off would). */
    public static final double SOUND_SPEED = 130;
    /** How far a strike is seen and heard (the game sends its bolt only to 256 blocks; a StormStrike carries it further). */
    public static final double HEARD = 640;
    /** A strike this close is a crack; farther, a roll. */
    public static final double CRACK = 70;
    /** Floats per cell in {@link #pack}: three texels of the Lens frame. */
    public static final int FLOATS = 12;

    /** Where the base sits for a cell whose centre first stood on ground at {@code groundY}. */
    public static double baseY(double groundY) {
        return Math.clamp(groundY + BASE_ABOVE_GROUND, BASE_MIN, BASE_MAX);
    }

    /** How fully it is drawn, 0..1: it builds over its first seconds and rains itself out over its last. */
    public static float strength(float ageS, float progress, float durationS) {
        float in = smooth(ageS / FADE_IN_S);
        float out = smooth((1F - progress) * durationS / FADE_OUT_S);
        return Math.min(in, out);
    }

    /** How much of its rain (and its darkness) reaches a place {@code d} blocks from the core's centre, 0..1. */
    public static float weatherAt(double d) {
        return smooth((float) ((RAIN_EDGE - d) / (RAIN_EDGE - RAIN_FULL)));
    }

    /** Seconds before a strike {@code d} blocks off is heard. */
    public static float thunderDelay(double d) {
        return (float) (Math.max(0, d) / SOUND_SPEED);
    }

    /**
     * One cell for Lens ({@code lens_storm_frame.glsl}): 12 floats at {@code at}: centre x, z camera-relative, base y,
     * strength; downwind x, z, ground y, noise time; the latest flash camera-relative x, y, z and its strength.
     */
    public static void pack(float[] out, int at, double cx, double cz, double baseY, float strength, float windX, float windZ,
                            double groundY, float time, double fx, double fy, double fz, float flash, double camX, double camY, double camZ) {
        out[at] = (float) (cx - camX);
        out[at + 1] = (float) (cz - camZ);
        out[at + 2] = (float) baseY;
        out[at + 3] = strength;
        float len = (float) Math.sqrt(windX * windX + windZ * windZ);
        out[at + 4] = len > 1.0e-4F ? windX / len : 1F;
        out[at + 5] = len > 1.0e-4F ? windZ / len : 0F;
        out[at + 6] = (float) groundY;
        out[at + 7] = time;
        out[at + 8] = (float) (fx - camX);
        out[at + 9] = (float) (fy - camY);
        out[at + 10] = (float) (fz - camZ);
        out[at + 11] = flash;
    }

    /**
     * A strike at {@code at} (the surface): the bolt (visual only: clients see it, flash the cloud and hear it late),
     * word of it to everyone within {@link #HEARD} (beyond the bolt's own reach), 3 damage to anyone within 4 blocks,
     * struck sand to glass.
     */
    public static void strike(ServerLevel level, BlockPos at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            level.addFreshEntity(bolt);
        }
        var word = new dev.awaken.living.LivingNet.StormStrike(at.getX() + 0.5F, at.getY(), at.getZ() + 0.5F);
        for (ServerPlayer pl : level.players()) {
            if (heard(pl.getX() - at.getX(), pl.getZ() - at.getZ())) dev.awaken.core.AwakenNet.send(pl, word);
        }
        for (ServerPlayer pl : level.players()) {
            if (!pl.isSpectator() && !pl.isCreative() && pl.distanceToSqr(Vec3.atBottomCenterOf(at)) < 16) {
                pl.hurtServer(level, level.damageSources().lightningBolt(), 3.0F);
            }
        }
        if (level.getBlockState(at.below()).is(BlockTags.SAND) && Behaviours.allowed(level, at)) {
            level.setBlock(at.below(), Blocks.GLASS.defaultBlockState(), 3);
        }
    }

    /** Whether a strike this far off (horizontally) reaches a player. */
    public static boolean heard(double dx, double dz) {
        return dx * dx + dz * dz <= HEARD * HEARD;
    }

    static float smooth(float x) {
        float t = Math.clamp(x, 0F, 1F);
        return t * t * (3F - 2F * t);
    }
}
