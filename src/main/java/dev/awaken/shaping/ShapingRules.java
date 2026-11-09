package dev.awaken.shaping;

import dev.awaken.core.api.CityHooks;
import dev.awaken.gates.GateService;
import dev.awaken.powers.Constructs;
import dev.awaken.wilds.ward.Wards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Where a knock lands and what it makes, and where the Mallet may work (SPEC §52.2, §52.5). */
public final class ShapingRules {
    private ShapingRules() {}

    /** The part of a block a knock lands on, and the cuts it steps through from whole and back to whole. */
    public enum Zone {
        TOP(Shape.NOTCH, Shape.BEVEL, Shape.ROUND),
        UNDER(Shape.UNDER, Shape.COVE),
        MID(Shape.SLAB),
        CENTER_TOP(Shape.HALF_BOTTOM),
        CENTER_BOTTOM(Shape.HALF_TOP);

        final Shape[] cycle;

        Zone(Shape... cycle) {
            this.cycle = cycle;
        }

        int indexOf(Shape s) {
            for (int i = 0; i < this.cycle.length; i++) if (this.cycle[i] == s) return i;
            return -1;
        }
    }

    public record Aim(Zone zone, Direction facing) {}

    public record Cut(Shape shape, Direction facing) {}

    /** Whether this cut has a front at all. */
    public static boolean faces(Shape s) {
        return s != Shape.FULL && s != Shape.HALF_BOTTOM && s != Shape.HALF_TOP;
    }

    private static Zone zoneOf(Shape s) {
        return switch (s) {
            case NOTCH, BEVEL, ROUND -> Zone.TOP;
            case UNDER, COVE -> Zone.UNDER;
            case SLAB -> Zone.MID;
            case HALF_BOTTOM -> Zone.CENTER_TOP;
            case HALF_TOP -> Zone.CENTER_BOTTOM;
            case FULL -> Zone.MID;
        };
    }

    /**
     * Which part a hit at (x, y, z, inside the block) lands on. A hit on a surface the last cut made carries on with that
     * cut, so knocking the same place steps through the shapes.
     */
    public static Aim aim(Shape shape, Direction facing, Direction face, double x, double y, double z) {
        if (shape != Shape.FULL && !onOutside(face, x, y, z)) return new Aim(zoneOf(shape), facing);
        if (face == Direction.UP || face == Direction.DOWN) {
            double west = x, east = 1 - x, north = z, south = 1 - z, near = Math.min(Math.min(west, east), Math.min(north, south));
            if (near >= 0.25) return new Aim(face == Direction.UP ? Zone.CENTER_TOP : Zone.CENTER_BOTTOM, Direction.SOUTH);
            Direction edge = near == west ? Direction.WEST : near == east ? Direction.EAST : near == north ? Direction.NORTH : Direction.SOUTH;
            return new Aim(face == Direction.UP ? Zone.TOP : Zone.UNDER, edge);
        }
        return new Aim(y > 0.62 ? Zone.TOP : y < 0.38 ? Zone.UNDER : Zone.MID, face);
    }

    private static boolean onOutside(Direction face, double x, double y, double z) {
        double e = 0.002;
        return switch (face) {
            case UP -> y >= 1 - e;
            case DOWN -> y <= e;
            case NORTH -> z <= e;
            case SOUTH -> z >= 1 - e;
            case WEST -> x <= e;
            case EAST -> x >= 1 - e;
        };
    }

    /** What a knock at this aim turns the block into. */
    public static Cut next(Shape shape, Direction facing, Aim aim) {
        int at = aim.zone().indexOf(shape);
        if (at >= 0 && (facing == aim.facing() || !faces(shape))) {
            return at + 1 < aim.zone().cycle.length ? new Cut(aim.zone().cycle[at + 1], aim.facing()) : new Cut(Shape.FULL, Direction.SOUTH);
        }
        return new Cut(aim.zone().cycle[0], aim.facing());
    }

    /** Whether this player may change this block with the Mallet. Everyday building rules, then the places that are kept whole. */
    public static boolean mayChange(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!player.mayBuild() || !level.mayInteract(player, pos) || !Constructs.mayChange(player, pos)) return false;
        if (player.isCreative()) return true;
        if (GateService.inGate(level, pos) || CityHooks.isCivic(level, pos)) return false;
        if ("awaken".equals(level.dimension().identifier().getNamespace())) return false;
        return !Wards.binds(player) || Wards.enforced(level, pos).isEmpty();
    }
}
