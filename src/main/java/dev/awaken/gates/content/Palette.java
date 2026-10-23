package dev.awaken.gates.content;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The blocks one gate theme is built from. All states are resolved once (vanilla blocks exist before mods load) and
 * then shared read-only by worldgen threads.
 */
public record Palette(
        BlockState fill,        // solid mass around rooms
        BlockState floor,       // walking surface
        BlockState floorAlt,    // pattern accent in floors
        BlockState wall,        // visible wall face
        BlockState wallAlt,     // bands, panels, niches
        BlockState pillar,      // columns
        BlockState ceiling,
        BlockState stair,       // stairs block (facing set on placement)
        BlockState light,       // embedded light block (floor inlays, wall lamps)
        BlockState hang,        // hanging light under the ceiling
        BlockState liquid,      // pools and pits (air = bottomless drop to the band floor)
        BlockState carpet,      // runner along main paths
        List<BlockState> floorDecor,
        List<BlockState> hangDecor,
        List<BlockState> clutter) {

    public static Palette of(String fill, String floor, String floorAlt, String wall, String wallAlt, String pillar,
                             String ceiling, String stair, String light, String hang, String liquid, String carpet,
                             List<String> floorDecor, List<String> hangDecor, List<String> clutter) {
        return new Palette(Bs.of(fill), Bs.of(floor), Bs.of(floorAlt), Bs.of(wall), Bs.of(wallAlt), Bs.of(pillar),
                Bs.of(ceiling), Bs.of(stair), Bs.of(light), Bs.of(hang), Bs.of(liquid), Bs.of(carpet),
                floorDecor.stream().map(Bs::of).toList(), hangDecor.stream().map(Bs::of).toList(),
                clutter.stream().map(Bs::of).toList());
    }
}
