package dev.awaken.opening;

import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;

/** Block states by id and properties ("minecraft:white_bed[facing=north,part=head]"), resolved once and cached. */
final class StageBlocks {
    private StageBlocks() {}

    private static final Map<String, BlockState> CACHE = new HashMap<>();

    static synchronized BlockState of(String spec) {
        BlockState cached = CACHE.get(spec);
        if (cached != null) return cached;
        String id = spec;
        String props = null;
        int b = spec.indexOf('[');
        if (b >= 0) {
            id = spec.substring(0, b);
            props = spec.substring(b + 1, spec.length() - 1);
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        BlockState state;
        if (block == Blocks.AIR && !id.endsWith(":air")) {
            Awaken.LOG.warn("[opening] unknown block {}", spec);
            state = Blocks.STONE.defaultBlockState();
        } else {
            state = block.defaultBlockState();
            if (props != null && !props.isEmpty()) {
                for (String kv : props.split(",")) {
                    String[] p = kv.split("=");
                    Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                    if (prop == null) {
                        Awaken.LOG.warn("[opening] {} has no property {}", id, p[0]);
                        continue;
                    }
                    state = with(state, prop, p[1].trim());
                }
            }
        }
        CACHE.put(spec, state);
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }
}
