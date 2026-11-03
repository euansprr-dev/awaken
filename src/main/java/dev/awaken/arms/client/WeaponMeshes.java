package dev.awaken.arms.client;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.WeaponMesh;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Every weapon's sculpted mesh (SPEC §25 "Weapons") and the War Anvil's tools, read with the resources and again on each reload. */
final class WeaponMeshes {
    private WeaponMeshes() {}

    private static final Map<String, WeaponMesh> MESHES = new HashMap<>();

    static @Nullable WeaponMesh get(String id) {
        return MESHES.get(id);
    }

    static void reload(ResourceManager manager) {
        MESHES.clear();
        for (ArmsData.Weapon w : ArmsData.WEAPONS) load(manager, w.id(), Awaken.id("arms/weapons/" + w.id() + ".mesh"));
        // the War Anvil's hammer and tongs (SPEC §46.3)
        for (String tool : TOOLS) load(manager, "anvil/" + tool, Awaken.id("arms/anvil/" + tool + ".mesh"));
        WeaponPipelines.recheck();
    }

    private static final String[] TOOLS = {"hammer", "tongs", "stone_hammer"};

    private static void load(ResourceManager manager, String id, Identifier file) {
        var res = manager.getResource(file);
        if (res.isEmpty()) {
            Awaken.LOG.warn("[arms] no mesh for {} ({})", id, file);
            return;
        }
        try (InputStream in = res.get().open()) {
            MESHES.put(id, WeaponMesh.read(in));
        } catch (IOException e) {
            Awaken.LOG.error("[arms] the mesh for {} did not load: {}", id, e.getMessage());
        }
    }
}
