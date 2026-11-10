package dev.awaken.arms;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.HearthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * The world's answer to a blow (SPEC §25 "Weight in the Hand", stage 5; approved with Fight Feel on 2026-10-28 and never
 * built): a blow doesn't land through a wall, a strike that meets a block glances off it, and a sweep cuts the wild grass in
 * its way. One set of rules for the server's sweep and the client's prediction, each on its own copy of the world.
 */
public final class ArmsWalls {
    private ArmsWalls() {}

    /** What a blade meets when it glances: nothing; stone or metal (a ring and sparks); wood (a knock); earth (a thud); something soft (a rustle, no recoil). */
    public static final int GLANCE_NONE = 0, GLANCE_HARD = 1, GLANCE_WOOD = 2, GLANCE_EARTH = 3, GLANCE_SOFT = 4;
    /** The fan a glance looks along, either side of the look (degrees), and how far a pinning blow also looks down. */
    static final float GLANCE_FAN = 14F, GLANCE_DOWN = 25F;
    /** The most plants one blow cuts. */
    public static final int GRASS_MAX = 12;
    /** Wild plants a sweep cuts: grass and ferns, never a flower, a crop or a sapling. Other modules add their own wild grass. */
    public static final TagKey<Block> CUT_BY_SWEEPS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("cut_by_sweeps"));

    /**
     * Whether a blow from {@code eye} can reach a body: a line to its nearest point, its middle or its top is clear of
     * every block that would stop a body. Three points, so a creature half behind a door frame is still struck; a fence,
     * glass and leaves stop a blade, tall grass doesn't.
     */
    public static boolean sees(Level level, Vec3 eye, AABB box) {
        double cx = (box.minX + box.maxX) * 0.5, cz = (box.minZ + box.maxZ) * 0.5;
        return clear(level, eye, Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY), Mth.clamp(eye.z, box.minZ, box.maxZ))
                || clear(level, eye, cx, (box.minY + box.maxY) * 0.5, cz)
                || clear(level, eye, cx, box.maxY - 0.05, cz);
    }

    private static boolean clear(Level level, Vec3 eye, double x, double y, double z) {
        Vec3 to = new Vec3(x, y, z);
        if (to.distanceToSqr(eye) < 1e-6) return true;
        return level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /**
     * The block a strike that struck no body glances off: the nearest along a fan of three lines either side of the look
     * (and, for a blow that pins, one more looking down), within the move's reach. Null: the blade met only air.
     */
    public static @Nullable BlockHitResult glance(Level level, Vec3 eye, float yaw, float pitch, double reach, boolean pins) {
        BlockHitResult best = null;
        double near = Double.MAX_VALUE;
        for (int i = 0; i < (pins ? 4 : 3); i++) {
            float y = yaw + (i == 0 ? 0F : i == 1 ? -GLANCE_FAN : i == 2 ? GLANCE_FAN : 0F), p = pitch + (i == 3 ? GLANCE_DOWN : 0F);
            Vec3 to = eye.add(Vec3.directionFromRotation(Mth.clamp(p, -89F, 89F), y).scale(reach));
            BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            double d = hit.getLocation().distanceToSqr(eye);
            if (d < near) {
                near = d;
                best = hit;
            }
        }
        return best;
    }

    /** What kind of thing a block is to a blade, by the tool that mines it. */
    public static int kind(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(BlockTags.MINEABLE_WITH_HOE)) return GLANCE_SOFT;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return GLANCE_WOOD;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return GLANCE_EARTH;
        return GLANCE_HARD;
    }

    /**
     * A sweep's active tick cuts the wild plants in its arc and reach, as a player's break (so a claim can refuse it and the
     * drops are vanilla's), at most {@link #GRASS_MAX} a blow counting {@code cut} already: never in adventure mode, never
     * inside a Hearth's radius, the city or a village. Returns how many it cut now.
     */
    public static int cutGrass(ServerPlayer p, Vec3 eye, float yaw, float arc, double reach, int cut) {
        if (cut >= GRASS_MAX || !p.getAbilities().mayBuild || p.isSpectator()) return 0;
        ServerLevel level = p.level();
        BlockPos feet = p.blockPosition();
        if (HearthHooks.inside(level, feet) || CityHooks.inCity(level, feet)
                || level.structureManager().getStructureWithPieceAt(feet, StructureTags.VILLAGE).isValid()) return 0;
        int r = (int) Math.ceil(reach), n = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2 && cut + n < GRASS_MAX; dy++) {
            for (int dx = -r; dx <= r && cut + n < GRASS_MAX; dx++) {
                for (int dz = -r; dz <= r && cut + n < GRASS_MAX; dz++) {
                    at.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    double x = at.getX() + 0.5 - eye.x, z = at.getZ() + 0.5 - eye.z;
                    if (x * x + z * z > reach * reach) continue;
                    float toward = (float) (Mth.atan2(z, x) * Mth.RAD_TO_DEG) - 90F;
                    if (!ArmsRules.inArc(toward - yaw, arc)) continue;
                    BlockState state = level.getBlockState(at);
                    if (!state.is(CUT_BY_SWEEPS)) continue;
                    if (net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(level, p.gameMode.getGameModeForPlayer(), p, at, state).isCanceled()) continue;
                    if (level.destroyBlock(at, !p.isCreative(), p)) n++;
                }
            }
        }
        return n;
    }
}
