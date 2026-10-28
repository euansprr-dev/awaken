package dev.awaken.gates.world;

import dev.awaken.gates.content.Bs;
import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * A world's blocks by role (SPEC §38.5, "Blocks by role"): the recipe's {@code blocks} map, each role falling back to the
 * engine's default, so one engine builds many worlds. Resolved once per floor plan.
 */
final class WorldMats {
    private final Map<String, String> blocks;

    WorldMats(GateWorld w) {
        blocks = w == null ? Map.of() : w.dressing().blocks();
    }

    BlockState get(String role, String fallback) {
        return GatePainter.state(blocks.getOrDefault(role, fallback));
    }

    /** A block with one of its properties set (a cluster's candle count, a glass hue). */
    static BlockState with(BlockState s, String prop, Object value) {
        return Bs.set(s, prop, String.valueOf(value).toLowerCase(java.util.Locale.ROOT));
    }

    static BlockState facing(BlockState s, Direction d) {
        return Bs.set(s, "facing", d.getSerializedName());
    }

    static BlockState light(int level) {
        return GatePainter.state("minecraft:light[level=" + level + "]");
    }
}
