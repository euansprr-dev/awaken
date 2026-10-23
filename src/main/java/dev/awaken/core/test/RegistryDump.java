package dev.awaken.core.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes every registered AWAKEN item, block and entity id to run/awaken-registry.json during GameTests, so
 * tools/qa/check_assets.py can verify each one has its model, texture, lang key and loot table.
 */
final class RegistryDump {
    private RegistryDump() {}

    static void register() {
        AwakenTests.register("core_registry_dump", 20, h -> {
            JsonObject root = new JsonObject();
            JsonArray items = new JsonArray(), blocks = new JsonArray(), blockItems = new JsonArray(), entities = new JsonArray();
            BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals(Awaken.MODID)).sorted().forEach(k -> {
                items.add(k.getPath());
                if (BuiltInRegistries.ITEM.getValue(k) instanceof BlockItem) blockItems.add(k.getPath());
            });
            BuiltInRegistries.BLOCK.keySet().stream().filter(k -> k.getNamespace().equals(Awaken.MODID)).sorted().forEach(k -> blocks.add(k.getPath()));
            BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k -> k.getNamespace().equals(Awaken.MODID)).sorted().forEach(k -> entities.add(k.getPath()));
            root.add("items", items);
            root.add("block_items", blockItems);
            root.add("blocks", blocks);
            root.add("entities", entities);
            try {
                Files.writeString(Path.of("awaken-registry.json"), root.toString());
            } catch (IOException e) {
                h.fail("could not write dump: " + e);
            }
            h.succeed();
        });
    }
}
