package dev.awaken.lucidium.journal;

import dev.awaken.lucidium.plan.Fragment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.util.Map;

/**
 * Words for a remembered place, read from its blocks: "A waterfall with a door behind it", "A grove with a light
 * still burning". Used for vision rumours (coordinates are never shown) and the journal.
 */
public final class Describe {
    private Describe() {}

    public static String place(Fragment f, String structure) {
        Map<BlockState, Integer> hist = f.histogram();
        int water = 0, lava = 0, logs = 0, leaves = 0, sand = 0, snow = 0, ruin = 0, planks = 0, flowers = 0, ore = 0;
        boolean door = false, bell = false, chest = false, light = false, bed = false, obsidian = false;
        for (var e : hist.entrySet()) {
            BlockState s = e.getKey();
            int n = e.getValue();
            if (s.getFluidState().is(Fluids.WATER) || s.getFluidState().is(Fluids.FLOWING_WATER)) water += n;
            if (s.getFluidState().is(Fluids.LAVA) || s.getFluidState().is(Fluids.FLOWING_LAVA)) lava += n;
            if (s.is(BlockTags.LOGS)) logs += n;
            if (s.is(BlockTags.LEAVES)) leaves += n;
            if (s.is(BlockTags.SAND)) sand += n;
            if (s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(BlockTags.ICE)) snow += n;
            if (s.is(BlockTags.STONE_BRICKS) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)) ruin += n;
            if (s.is(BlockTags.PLANKS)) planks += n;
            if (s.is(BlockTags.FLOWERS)) flowers += n;
            if (s.is(BlockTags.ORES)) ore += n;
            if (s.getBlock() instanceof DoorBlock) door = true;
            if (s.is(Blocks.BELL)) bell = true;
            if (s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST)) chest = true;
            if (s.getLightEmission() >= 10) light = true;
            if (s.getBlock() instanceof BedBlock) bed = true;
            if (s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)) obsidian = true;
        }
        boolean waterfall = waterfall(f);
        String noun;
        if (!structure.isEmpty()) noun = structureNoun(structure);
        else if (waterfall) noun = "a waterfall";
        else if (lava >= 4) noun = "a pool of fire";
        else if (water >= 40) noun = "still water";
        else if (logs + leaves >= 60) noun = "a grove";
        else if (sand >= 120) noun = "dunes under an empty sky";
        else if (snow >= 60) noun = "a frozen place";
        else if (ruin >= 30) noun = "a ruin";
        else if (planks >= 30) noun = "a wooden house";
        else if (flowers >= 6) noun = "a field of flowers";
        else if (ore >= 3) noun = "a cave with ore in its walls";
        else if (obsidian) noun = "black glass stones";
        else noun = "a quiet place";
        String tail;
        if (door) tail = waterfall ? "with a door behind it" : "with a door";
        else if (bell) tail = "with a bell";
        else if (chest) tail = "where something was left behind";
        else if (bed) tail = "with a bed no one sleeps in";
        else if (light) tail = "with a light still burning";
        else tail = "";
        String s = tail.isEmpty() ? noun : noun + " " + tail;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean waterfall(Fragment f) {
        for (int z = 0; z < f.sz; z++)
            for (int x = 0; x < f.sx; x++) {
                int levels = 0;
                for (int y = 0; y < f.sy; y++) {
                    BlockState s = f.get(x, y, z);
                    if (s != null && !s.getFluidState().isEmpty() && s.getFluidState().is(Fluids.FLOWING_WATER)) levels++;
                }
                if (levels >= 5) return true;
            }
        return false;
    }

    public static String structureNoun(String structureId) {
        String p = structureId.contains(":") ? structureId.substring(structureId.indexOf(':') + 1) : structureId;
        if (p.startsWith("village")) return "a village";
        if (p.startsWith("ruined_portal")) return "a broken portal";
        if (p.startsWith("shipwreck")) return "a wrecked ship";
        if (p.startsWith("ocean_ruin")) return "ruins under the water";
        return switch (p) {
            case "desert_pyramid" -> "a temple in the sand";
            case "jungle_pyramid" -> "a temple in the vines";
            case "igloo" -> "a house of snow";
            case "swamp_hut" -> "a hut on stilts";
            case "pillager_outpost" -> "a watchtower";
            case "trail_ruins" -> "buried ruins";
            case "mansion" -> "a great dark house";
            case "monument" -> "a palace under the sea";
            case "trial_chambers" -> "copper halls underground";
            case "ancient_city" -> "a city in the dark";
            case "stronghold" -> "a stone maze";
            case "mineshaft", "mineshaft_mesa" -> "old tunnels";
            case "fortress" -> "a fortress of dark brick";
            case "bastion_remnant" -> "a black fortress";
            case "end_city" -> "towers at the end of the world";
            default -> "a building no one built";
        };
    }

    public static String pretty(String id) {
        String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (p.contains("/")) p = p.substring(p.lastIndexOf('/') + 1);
        return p.replace('_', ' ');
    }

    public static String blockName(BlockState s) {
        return pretty(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
    }
}
