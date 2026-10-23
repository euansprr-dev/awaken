package dev.awaken.gates.content;

import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Block-state strings for palettes: {@code "minecraft:soul_lantern[hanging=true]"}. Vanilla block ids are stable across
 * the 26.x colour/copper collections, so palettes name blocks by id instead of by Java constant.
 */
public final class Bs {
    private Bs() {}

    public static BlockState of(String spec) {
        String id = spec;
        String props = null;
        int b = spec.indexOf('[');
        if (b >= 0) {
            id = spec.substring(0, b);
            props = spec.substring(b + 1, spec.length() - 1);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == Blocks.AIR && !id.endsWith(":air")) {
            Awaken.LOG.warn("[gates] unknown block in palette: {}", spec);
            return Blocks.STONE.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (props != null && !props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                if (p.length != 2) continue;
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop == null) continue;
                state = with(state, prop, p[1].trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    /** Sets a property by name if the state has it (used for facings on stairs, axis on logs). */
    public static BlockState set(BlockState state, String name, String value) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty(name);
        return prop == null ? state : with(state, prop, value);
    }
}
