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
        for (ArmsData.Weapon w : ArmsData.WEAPONS) {
            load(manager, w.id(), Awaken.id("arms/weapons/" + w.id() + ".mesh"));
            if (GAUNTLETS.equals(w.style())) load(manager, w.id() + SLEEVE, Awaken.id("arms/weapons/" + w.id() + SLEEVE + ".mesh"));
            if (SHIELDBLADE.equals(w.style())) { // the hands draw its two parts (arms_weapons.pair_of)
                for (String part : PARTS) load(manager, w.id() + part, Awaken.id("arms/weapons/" + w.id() + part + ".mesh"));
            }
        }
        // the War Anvil's hammer and tongs (SPEC §46.3)
        for (String tool : TOOLS) load(manager, "anvil/" + tool, Awaken.id("arms/anvil/" + tool + ".mesh"));
        WeaponPipelines.recheck();
    }

    /** The first-person sleeve a gauntlet runs on into (WeaponRenderer): its own mesh and texture, this suffix on the weapon's id. */
    static final String SLEEVE = "_arm";
    private static final String GAUNTLETS = "gauntlets", SHIELDBLADE = "shieldblade";
    private static final String[] PARTS = {"_blade", "_shield"};
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
