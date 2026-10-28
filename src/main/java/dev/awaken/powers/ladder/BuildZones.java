package dev.awaken.powers.ladder;

import dev.awaken.powers.Cast;
import dev.awaken.powers.Constructs;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Architect constructs whose ladder steps keep working after the cast (SPEC 36): a Ricochet or Mirror wall, a Crystal
 * wall blasts can't break, a Shatterproof cone. Events (a shot landing, a blast, a hit) look a block up here to learn who
 * built it and at which step. In memory only, like the constructs' own memory: after a restart they stand as plain light.
 */
public final class BuildZones {
    private BuildZones() {}

    public static final class Zone {
        public final UUID owner;
        public final String power;
        public final int level;
        public final ResourceKey<Level> dim;
        public final Set<BlockPos> blocks;
        public final AABB box;
        public final long until;
        /** The cast that built it: its damage, colours and riders answer for the construct later. */
        public final Cast cast;

        Zone(Cast c, Set<BlockPos> blocks, AABB box, long until) {
            this.owner = c.player.getUUID();
            this.power = c.type.path;
            this.level = c.powerLevel;
            this.dim = c.level.dimension();
            this.blocks = blocks;
            this.box = box;
            this.until = until;
            this.cast = c;
        }

        /** True while at least this block of the construct still stands. */
        public boolean stands(ServerLevel level, BlockPos pos) {
            return blocks.contains(pos) && Constructs.isConstruct(level, pos);
        }

        /** Under the roof: inside the construct's footprint and below its top (a cone's shelter). */
        public boolean shelters(Vec3 at) {
            return at.x >= box.minX && at.x <= box.maxX && at.z >= box.minZ && at.z <= box.maxZ && at.y <= box.maxY && at.y >= box.minY - 4;
        }
    }

    private static final List<Zone> LIVE = new ArrayList<>();

    /** Remembers a construct's blocks for its life. Only the steps that need it call this. */
    public static Zone add(Cast c, Collection<BlockPos> shape, int ticks) {
        prune(c.level.getGameTime());
        Set<BlockPos> blocks = new HashSet<>(shape.size() * 2);
        AABB box = null;
        for (BlockPos p : shape) {
            if (!Constructs.isConstruct(c.level, p)) continue;
            blocks.add(p.immutable());
            AABB b = new AABB(p);
            box = box == null ? b : box.minmax(b);
        }
        Zone z = new Zone(c, blocks, box == null ? new AABB(c.player.blockPosition()) : box, c.level.getGameTime() + ticks);
        if (!blocks.isEmpty()) LIVE.add(z);
        return z;
    }

    /** The live zone a construct block belongs to, or null. */
    public static @Nullable Zone at(ServerLevel level, BlockPos pos) {
        if (LIVE.isEmpty()) return null;
        long now = level.getGameTime();
        for (Zone z : LIVE) {
            if (z.until < now || z.dim != level.dimension() || !z.box.contains(Vec3.atCenterOf(pos))) continue;
            if (z.stands(level, pos)) return z;
        }
        return null;
    }

    /** A live construct of {@code power} at {@code level}+ whose roof covers a point, or null. */
    public static @Nullable Zone sheltering(ServerLevel level, Vec3 at, String power, int minLevel) {
        if (LIVE.isEmpty()) return null;
        long now = level.getGameTime();
        for (Zone z : LIVE) {
            if (z.until < now || z.dim != level.dimension() || z.level < minLevel || !z.power.equals(power)) continue;
            if (z.shelters(at)) return z;
        }
        return null;
    }

    static void prune(long now) {
        LIVE.removeIf(z -> z.until < now || z.cast.player.isRemoved());
    }

    public static void clear() {
        LIVE.clear();
    }

    public static int count() {
        return LIVE.size();
    }
}
