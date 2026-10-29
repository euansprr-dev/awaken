package dev.awaken.armour.client;

import dev.awaken.Awaken;
import dev.awaken.armour.ArmourSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Every set's meshes (assets/awaken/armour/<set>/<piece>_<bone>.mesh), read with the resources and on each reload. A set
 * whose art isn't made yet has none and its pieces are left to vanilla; a Hunter set without them is an error.
 */
final class ArmourMeshes {
    private ArmourMeshes() {}

    /** The bones a piece may ride on, in the order they are drawn (tools/art/specs/armour_sets.py BONES). */
    enum Bone {
        HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, CAPE0, CAPE1, CAPE2, FRONT_CLOTH, BACK_CLOTH;

        final String file = name().toLowerCase(java.util.Locale.ROOT);
    }

    static final Bone[] BONES = Bone.values();

    /** A set's meshes: [piece ordinal][bone ordinal], null where a piece has none. */
    private static final Map<ArmourSet, ArmourMesh[][]> MESHES = new EnumMap<>(ArmourSet.class);
    private static final Map<ArmourSet, Identifier> TEXTURES = new EnumMap<>(ArmourSet.class);
    private static final Map<ArmourSet, ArmourMesh> RUNES = new EnumMap<>(ArmourSet.class);
    /** Mastery IV's awakened parts (&lt;piece&gt;_&lt;bone&gt;_awakened.mesh), drawn over the piece's own: [piece][bone]. */
    private static final Map<ArmourSet, ArmourMesh[][]> AWAKENED = new EnumMap<>(ArmourSet.class);

    static ArmourMesh @Nullable [][] of(ArmourSet set) {
        return MESHES.get(set);
    }

    /** A piece's meshes by bone, or null where the set has none for it. */
    static ArmourMesh @Nullable [] piece(ArmourSet set, ArmorType piece) {
        ArmourMesh[][] table = MESHES.get(set);
        return table == null ? null : table[piece.ordinal()];
    }

    /** A piece's awakened parts by bone, or null where the set has none for it. */
    static ArmourMesh @Nullable [] awakened(ArmourSet set, ArmorType piece) {
        ArmourMesh[][] table = AWAKENED.get(set);
        return table == null ? null : table[piece.ordinal()];
    }

    /** A Channeler set's rune (assets/awaken/armour/&lt;set&gt;/rune.mesh), circling its wearer; null where it has none. */
    static @Nullable ArmourMesh rune(ArmourSet set) {
        return RUNES.get(set);
    }

    static Identifier texture(ArmourSet set) {
        return TEXTURES.computeIfAbsent(set, s -> Awaken.id("textures/entity/armour/" + s.id + ".png"));
    }

    static void reload(ResourceManager manager) {
        MESHES.clear();
        RUNES.clear();
        AWAKENED.clear();
        for (ArmourSet set : ArmourSet.values()) {
            ArmourMesh[][] table = new ArmourMesh[ArmorType.values().length][];
            ArmourMesh[][] awake = new ArmourMesh[ArmorType.values().length][];
            int found = 0, woke = 0;
            for (ArmorType piece : ArmorType.values()) {
                ArmourMesh[] bones = new ArmourMesh[BONES.length], extra = new ArmourMesh[BONES.length];
                for (Bone bone : BONES) {
                    String base = "armour/" + set.id + "/" + piece.getName() + "_" + bone.file;
                    ArmourMesh m = load(manager, Awaken.id(base + ".mesh"));
                    if (m != null) {
                        bones[bone.ordinal()] = m;
                        table[piece.ordinal()] = bones;
                        found++;
                    }
                    ArmourMesh a = load(manager, Awaken.id(base + "_awakened.mesh"));
                    if (a != null) {
                        extra[bone.ordinal()] = a;
                        awake[piece.ordinal()] = extra;
                        woke++;
                    }
                }
            }
            if (woke > 0) AWAKENED.put(set, awake);
            Identifier runeFile = Awaken.id("armour/" + set.id + "/rune.mesh");
            var rune = manager.getResource(runeFile);
            if (rune.isPresent()) {
                try (InputStream in = rune.get().open()) {
                    RUNES.put(set, ArmourMesh.read(in));
                } catch (IOException e) {
                    Awaken.LOG.error("[armour] the mesh {} did not load: {}", runeFile, e.getMessage());
                }
            }
            if (found > 0) MESHES.put(set, table);
            else if (set.hunter != null) Awaken.LOG.error("[armour] no meshes for the Hunter set {}: vanilla's shell is drawn", set.id);
        }
        ArmourPipelines.recheck();
    }

    private static @Nullable ArmourMesh load(ResourceManager manager, Identifier file) {
        var res = manager.getResource(file);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            return ArmourMesh.read(in);
        } catch (IOException e) {
            Awaken.LOG.error("[armour] the mesh {} did not load: {}", file, e.getMessage());
            return null;
        }
    }
}
