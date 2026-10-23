package dev.awaken.lucidium.plan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Deterministic block remaps for one dream: a mood (stone becomes quartz, grass becomes moss, oak becomes birch...)
 * plus a colour rotation of every dyed block. Properties (facing, axis, half...) are carried over. Only vanilla
 * blocks are remapped; modded blocks keep their look. Thread-safe (worldgen and the compiler share it).
 */
public final class DreamPalette implements UnaryOperator<BlockState> {

    /** A mood: exact id swaps first, then the first id token swap that names a real block. */
    public record Mood(String name, Map<String, String> exact, List<String[]> tokens) {}

    public static final List<Mood> MOODS = List.of(
            mood("pearl",
                    map("stone", "smooth_quartz", "granite", "calcite", "diorite", "calcite", "andesite", "smooth_quartz",
                            "deepslate", "calcite", "cobblestone", "quartz_bricks", "stone_bricks", "quartz_bricks",
                            "mossy_cobblestone", "moss_block", "grass_block", "moss_block", "dirt", "coarse_dirt",
                            "sandstone", "quartz_block", "glowstone", "sea_lantern", "torch", "soul_torch",
                            "wall_torch", "soul_wall_torch", "lantern", "soul_lantern", "oak_leaves", "cherry_leaves",
                            "netherrack", "pink_terracotta", "tuff", "calcite"),
                    "cobblestone", "quartz", "dark_oak", "pale_oak", "oak", "birch", "spruce", "cherry"),
            mood("amethyst",
                    map("stone", "amethyst_block", "deepslate", "purpur_block", "cobblestone", "purpur_block",
                            "stone_bricks", "purpur_pillar", "grass_block", "mycelium", "dirt", "rooted_dirt",
                            "glowstone", "shroomlight", "torch", "soul_torch", "wall_torch", "soul_wall_torch",
                            "andesite", "purple_terracotta", "granite", "magenta_terracotta", "diorite", "pink_terracotta"),
                    "cobblestone", "purpur", "dark_oak", "cherry", "oak", "cherry", "spruce", "mangrove", "birch", "cherry"),
            mood("tide",
                    map("stone", "prismarine", "cobblestone", "prismarine_bricks", "deepslate", "dark_prismarine",
                            "stone_bricks", "prismarine_bricks", "grass_block", "warped_nylium", "dirt", "clay",
                            "oak_leaves", "warped_wart_block", "spruce_leaves", "warped_wart_block",
                            "birch_leaves", "warped_wart_block", "glowstone", "sea_lantern", "torch", "soul_torch",
                            "wall_torch", "soul_wall_torch", "oak_log", "warped_stem", "spruce_log", "warped_stem"),
                    "cobblestone", "prismarine", "dark_oak", "warped", "oak", "warped", "spruce", "mangrove", "birch", "warped"),
            mood("ember",
                    map("stone", "blackstone", "cobblestone", "polished_blackstone_bricks", "deepslate", "basalt",
                            "stone_bricks", "polished_blackstone_bricks", "grass_block", "crimson_nylium", "dirt", "netherrack",
                            "sand", "soul_sand", "oak_leaves", "nether_wart_block", "spruce_leaves", "nether_wart_block",
                            "birch_leaves", "nether_wart_block", "oak_log", "crimson_stem", "spruce_log", "crimson_stem",
                            "birch_log", "crimson_stem", "glowstone", "shroomlight"),
                    "cobblestone", "blackstone", "dark_oak", "crimson", "oak", "crimson", "spruce", "crimson", "birch", "crimson"),
            mood("snowglobe",
                    map("stone", "packed_ice", "cobblestone", "snow_block", "deepslate", "blue_ice", "grass_block", "snow_block",
                            "dirt", "snow_block", "stone_bricks", "packed_ice", "water", "ice", "lava", "magma_block",
                            "oak_leaves", "pale_oak_leaves", "spruce_leaves", "pale_oak_leaves", "birch_leaves", "pale_oak_leaves",
                            "torch", "soul_torch", "wall_torch", "soul_wall_torch"),
                    "dark_oak", "pale_oak", "oak", "pale_oak", "spruce", "pale_oak", "birch", "pale_oak"),
            mood("copper",
                    map("stone", "oxidized_cut_copper", "cobblestone", "cut_copper", "deepslate", "tuff",
                            "stone_bricks", "weathered_cut_copper", "grass_block", "moss_block", "oak_leaves", "azalea_leaves",
                            "spruce_leaves", "flowering_azalea_leaves", "glowstone", "shroomlight"),
                    "cobblestone", "cut_copper", "dark_oak", "jungle", "oak", "acacia", "spruce", "jungle"),
            mood("dusk",
                    map("glowstone", "shroomlight", "torch", "soul_torch", "wall_torch", "soul_wall_torch"))
    );

    private static final DyeColor[] COLOURS = DyeColor.values();
    /** Longest names first, so "light_blue_" wins over "blue_". */
    private static final DyeColor[] BY_LENGTH = Arrays.stream(COLOURS)
            .sorted(Comparator.comparingInt((DyeColor c) -> -c.getName().length())).toArray(DyeColor[]::new);

    private final Mood mood;
    private final int hueShift;
    private final Map<BlockState, BlockState> cache = new ConcurrentHashMap<>();

    public DreamPalette(Mood mood, int hueShift) {
        this.mood = mood;
        this.hueShift = Math.floorMod(hueShift, COLOURS.length);
    }

    public static DreamPalette of(String moodName, int hueShift) {
        return new DreamPalette(mood(moodName).orElse(MOODS.getLast()), hueShift);
    }

    public static Optional<Mood> mood(String name) {
        for (Mood m : MOODS) if (m.name().equals(name)) return Optional.of(m);
        return Optional.empty();
    }

    public Mood mood() {
        return mood;
    }

    @Override
    public BlockState apply(BlockState state) {
        if (state.isAir()) return state;
        return cache.computeIfAbsent(state, this::compute);
    }

    private BlockState compute(BlockState s) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        if (!"minecraft".equals(id.getNamespace())) return s;
        String path = id.getPath();
        String target = mood.exact().get(path);
        if (target == null) target = rotateColour(path, hueShift);
        if (target == null) {
            for (String[] t : mood.tokens()) {
                if (path.contains(t[0])) {
                    String candidate = path.replace(t[0], t[1]);
                    if (exists(candidate)) target = candidate;
                    break;
                }
            }
        }
        if (target == null) return s;
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(target));
        return block.map(b -> b.withPropertiesOf(s)).orElse(s);
    }

    /** "red_wool" shifted by 2 -> the colour two steps further round the dye wheel. Null if not a dyed block. */
    public static String rotateColour(String path, int shift) {
        if (Math.floorMod(shift, COLOURS.length) == 0) return null;
        for (DyeColor c : BY_LENGTH) {
            String prefix = c.getName() + "_";
            if (path.startsWith(prefix)) {
                DyeColor to = COLOURS[Math.floorMod(c.ordinal() + shift, COLOURS.length)];
                String candidate = to.getName() + "_" + path.substring(prefix.length());
                return exists(candidate) ? candidate : null;
            }
        }
        return null;
    }

    private static boolean exists(String path) {
        return BuiltInRegistries.BLOCK.containsKey(Identifier.withDefaultNamespace(path));
    }

    private static Mood mood(String name, Map<String, String> exact, String... tokens) {
        List<String[]> t = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < tokens.length; i += 2) t.add(new String[]{tokens[i], tokens[i + 1]});
        return new Mood(name, exact, List.copyOf(t));
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return Map.copyOf(m);
    }
}
