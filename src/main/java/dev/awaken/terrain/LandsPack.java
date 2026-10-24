package dev.awaken.terrain;

import dev.awaken.Awaken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The Awakened Lands data pack (biomes, their placement, trees). It asks for the feature flag
 * {@code awaken:awakened_lands}: a world being created may turn on any flag, so new worlds load it; a world made before
 * it existed has no such flag, so the game skips the pack there and its terrain never changes. Players can still
 * switch it off in the Data Packs screen when they create a world. Biome placement needs Lithostitched (the pack's
 * {@code world} profile); without it the biomes exist but nothing places them.
 */
final class LandsPack {
    private LandsPack() {}

    /** The pack's id in the pack repository (a built-in pack of the mod). */
    static final String ID = "mod/awaken:datapacks/awakened_lands";

    static void onAddPackFinders(AddPackFindersEvent e) {
        e.addPackFinders(Awaken.id("datapacks/awakened_lands"), PackType.SERVER_DATA, Component.literal("Awakened Lands"),
                PackSource.BUILT_IN, false, Pack.Position.TOP);
    }
}
