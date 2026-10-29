package dev.awaken.armour.client;

import dev.awaken.Awaken;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Every set's meshes (assets/awaken/armour/<set>/<piece>_<bone>.mesh), read with the resources and on each reload. */
final class ArmourMeshes {
    private ArmourMeshes() {}

    /** The sets drawn today. */
    static final String[] SETS = {"lordscale"};

    /** The bones a piece may ride on, in the order they are drawn (tools/art/specs/armour_sets.py BONES). */
    enum Bone {
        HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, CAPE0, CAPE1, CAPE2, FRONT_CLOTH, BACK_CLOTH;

        final String file = name().toLowerCase(java.util.Locale.ROOT);
    }

    static final Bone[] BONES = Bone.values();

    /** A set's meshes: [piece ordinal][bone ordinal], null where a piece has none. */
    private static final Map<String, ArmourMesh[][]> MESHES = new HashMap<>();

    static ArmourMesh @Nullable [][] of(String set) {
        return MESHES.get(set);
    }

    static Identifier texture(String set) {
        return Awaken.id("textures/entity/armour/" + set + ".png");
    }

    static void reload(ResourceManager manager) {
        MESHES.clear();
        for (String set : SETS) {
            ArmourMesh[][] table = new ArmourMesh[ArmorType.values().length][BONES.length];
            int found = 0;
            for (ArmorType piece : ArmorType.values()) {
                for (Bone bone : BONES) {
                    Identifier file = Awaken.id("armour/" + set + "/" + piece.getName() + "_" + bone.file + ".mesh");
                    var res = manager.getResource(file);
                    if (res.isEmpty()) continue;
                    try (InputStream in = res.get().open()) {
                        table[piece.ordinal()][bone.ordinal()] = ArmourMesh.read(in);
                        found++;
                    } catch (IOException e) {
                        Awaken.LOG.error("[armour] the mesh {} did not load: {}", file, e.getMessage());
                    }
                }
            }
            if (found == 0) Awaken.LOG.warn("[armour] no meshes for the set {}", set);
            MESHES.put(set, table);
        }
        ArmourPipelines.recheck();
    }
}
