package dev.awaken.terrain;

import dev.awaken.Awaken;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.SortedMap;
import java.util.TreeMap;

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

    /**
     * The pack's own JSON files under {@code folder} (e.g. {@code worldgen/biome}), by id without the folder or the
     * extension; empty where the world did not load the pack. For tests and the studio: the files as data.
     */
    static SortedMap<Identifier, Resource> files(MinecraftServer server, String folder) {
        SortedMap<Identifier, Resource> out = new TreeMap<>();
        var pack = server.getPackRepository().getPack(ID);
        if (pack == null) return out;
        for (var e : server.getResourceManager().listResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
            if (!e.getValue().sourcePackId().equals(pack.getId())) continue;
            String p = e.getKey().getPath();
            out.put(Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), p.substring(folder.length() + 1, p.length() - ".json".length())), e.getValue());
        }
        return out;
    }
}
