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

/** Every weapon's sculpted mesh (SPEC §25 "Weapons"), read with the resources and again on each reload. */
final class WeaponMeshes {
    private WeaponMeshes() {}

    private static final Map<String, WeaponMesh> MESHES = new HashMap<>();

    static @Nullable WeaponMesh get(String id) {
        return MESHES.get(id);
    }

    static void reload(ResourceManager manager) {
        MESHES.clear();
        for (ArmsData.Weapon w : ArmsData.WEAPONS) {
            Identifier file = Awaken.id("arms/weapons/" + w.id() + ".mesh");
            var res = manager.getResource(file);
            if (res.isEmpty()) {
                Awaken.LOG.warn("[arms] no mesh for {} ({})", w.id(), file);
                continue;
            }
            try (InputStream in = res.get().open()) {
                MESHES.put(w.id(), WeaponMesh.read(in));
            } catch (IOException e) {
                Awaken.LOG.error("[arms] the mesh for {} did not load: {}", w.id(), e.getMessage());
            }
        }
        WeaponPipelines.recheck();
    }
}
