package dev.awaken.world.fauna;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * When and where one overworld creature spawns (biomes and weights live in the biome modifiers,
 * {@code data/awaken/neoforge/biome_modifier/fauna_*.json}). {@code hint} is the one line its first sighting shows.
 */
public record Habitat(Window window, boolean caves, Ground ground, float preAwakenChance, int minPlayerLevel, String hint) {

    /** Time of day by the overworld clock (0 = sunrise, 12000 = sunset, 18000 = midnight). */
    public enum Window {
        /** Daylight. */
        DAY(23500, 11800),
        /** From dusk until just before dawn. */
        DUSK_NIGHT(11800, 23500),
        /** Daylight and the long golden hour. */
        DAY_DUSK(23500, 13500),
        ANY(0, 24000),
        /** Vanilla's monster rule instead of a clock: block light 0 and a dark sky (night, caves, deep shade). */
        DARK(0, 24000);

        final int from, to;

        Window(int from, int to) {
            this.from = from;
            this.to = to;
        }

        public boolean contains(long dayTime) {
            int t = (int) Math.floorMod(dayTime, 24000L);
            if (from <= to) return t >= from && t < to;
            return t >= from || t < to;
        }
    }

    /** What it must stand on to spawn. */
    public enum Ground {
        /** Grass, dirt, podzol, mycelium, moss, mud, snow, sand, terracotta, stone slopes: anything a wild animal walks on. */
        NATURAL,
        /** Sand, red sand, terracotta. */
        SANDY,
        /** Whatever vanilla's spawn rules allow (night hunters in caves). */
        ANY;

        public boolean accepts(BlockState below) {
            return switch (this) {
                case ANY -> true;
                case SANDY -> below.is(BlockTags.SAND) || below.is(BlockTags.TERRACOTTA) || below.is(BlockTags.BADLANDS_TERRACOTTA);
                case NATURAL -> below.is(BlockTags.ANIMALS_SPAWNABLE_ON) || below.is(BlockTags.DIRT) || below.is(BlockTags.SAND)
                        || below.is(BlockTags.TERRACOTTA) || below.is(BlockTags.BADLANDS_TERRACOTTA) || below.is(BlockTags.SNOW)
                        || below.is(BlockTags.GOATS_SPAWNABLE_ON) || below.is(BlockTags.WOLVES_SPAWNABLE_ON) || below.is(Blocks.PODZOL)
                        || below.is(BlockTags.SUBSTRATE_OVERWORLD) || below.is(Blocks.CLAY) || below.is(Blocks.CALCITE);
            };
        }
    }

    /** Ambient creatures may show themselves before awakening at this chance per spawn ("faint hints"). */
    static final float HINT = 0.35F;

    static final Map<String, Habitat> ALL = Map.ofEntries(
            // Greenwild: forest, plains, meadow
            Map.entry("lumen_moth", new Habitat(Window.DUSK_NIGHT, false, Ground.NATURAL, HINT, 0, "Circles above buried ore at night")),
            Map.entry("bloom_sprite", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Crops it hovers over grow")),
            Map.entry("mossback", new Habitat(Window.DAY_DUSK, false, Ground.NATURAL, 0, 0, "Grazes; fights back from its shell")),
            Map.entry("gloomstalker", new Habitat(Window.DARK, true, Ground.ANY, 0, 0, "Hunts in the dark. Torchlight is safe")),
            Map.entry("briar_sow", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Warns once, then charges")),
            // Sunscorch: desert, savanna, badlands
            Map.entry("glass_scarab", new Habitat(Window.DAY, false, Ground.SANDY, HINT, 0, "Sneak up on it to catch its quartz")),
            Map.entry("dune_ray", new Habitat(Window.DAY_DUSK, false, Ground.NATURAL, 0, 0, "Glides low; lashes back if struck")),
            Map.entry("dune_wyrm", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Surfaces beside you")),
            // Frostwild: taiga, snowy plains and slopes, grove
            Map.entry("frostfinch", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Drops feathers; loves campfires")),
            Map.entry("frost_stag", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 0, "Bolts from running feet. Sneak")),
            Map.entry("wisp_hound", new Habitat(Window.DARK, true, Ground.ANY, 0, 0, "Hunts in packs at night. Firelight is safe")),
            Map.entry("old_antler", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Guards its valley")),
            // P2. The Mire: swamp, mangrove swamp
            Map.entry("bogwick", new Habitat(Window.DUSK_NIGHT, false, Ground.NATURAL, HINT, 0, "Draws the poison out of you")),
            Map.entry("mudskipper", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Digs clay and slime out of the mud")),
            Map.entry("bog_toad", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 0, "Eats small slimes; its tongue pulls")),
            Map.entry("mire_mother", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Rules the bog")),
            // The Canopy: jungles
            Map.entry("dart_frog", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Venom skin. Never touch it bare-handed")),
            Map.entry("crested_basilisk", new Habitat(Window.DAY_DUSK, false, Ground.NATURAL, 0, 0, "Sprints across water when startled")),
            Map.entry("canopy_prowler", new Habitat(Window.DARK, true, Ground.ANY, 0, 0, "Hunts at night. Firelight is safe")),
            Map.entry("plumed_coil", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Strikes from the canopy")),
            // Duskwood: dark forest, pale garden
            Map.entry("gloam_snail", new Habitat(Window.ANY, false, Ground.NATURAL, HINT, 0, "Leaves glowing lichen in the dark")),
            Map.entry("hollow_owl", new Habitat(Window.DUSK_NIGHT, false, Ground.NATURAL, 0, 0, "Makes monsters glow in the dark")),
            Map.entry("thornling", new Habitat(Window.DARK, true, Ground.ANY, 0, 0, "Hunts in packs. Torchlight is safe")),
            Map.entry("weaver_queen", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Her webs hang nearby")),
            // Blossomwood: cherry grove, flower forest, birch forests
            Map.entry("petalwing", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Flowers open where it rests")),
            Map.entry("honey_bear", new Habitat(Window.DAY_DUSK, false, Ground.NATURAL, 0, 0, "Shakes honeycomb out of bee nests")),
            Map.entry("empress_moth", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Her dust brings sleep")),
            // The Crags: peaks, windswept hills, stony shores
            Map.entry("scree_marmot", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Digs flint and nuggets. Sneak up")),
            Map.entry("peak_kite", new Habitat(Window.DAY, false, Ground.NATURAL, HINT, 0, "Drops feathers as it soars")),
            Map.entry("crag_ram", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 0, "Butts you off ledges if cornered")),
            Map.entry("stonehorn", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Its stamp looses rocks")),
            // Sporewild: mushroom fields
            Map.entry("puffcap", new Habitat(Window.ANY, false, Ground.NATURAL, HINT, 0, "Plants mushrooms; puffs if startled")),
            Map.entry("sporeback_snail", new Habitat(Window.ANY, false, Ground.NATURAL, HINT, 0, "Mushrooms it rests by grow huge")),
            Map.entry("mycelord", new Habitat(Window.ANY, false, Ground.NATURAL, 0, 3, "Named beast. Its spores choke")));

    public static Habitat of(String id) {
        Habitat h = ALL.get(id);
        return h != null ? h : new Habitat(Window.DAY, false, Ground.NATURAL, 0, 0, "");
    }
}
