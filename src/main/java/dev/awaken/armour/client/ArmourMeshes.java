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
public final class ArmourMeshes {
    private ArmourMeshes() {}

    /** The bones a piece may ride on, in the order they are drawn (tools/art/specs/armour_sets.py BONES). */
    public enum Bone {
        HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, CAPE0, CAPE1, CAPE2, FRONT_CLOTH, BACK_CLOTH;

        final String file = name().toLowerCase(java.util.Locale.ROOT);
    }

    public static final Bone[] BONES = Bone.values();

    /** A set's meshes: [piece ordinal][bone ordinal], null where a piece has none. */
    private static final Map<ArmourSet, ArmourMesh[][]> MESHES = new EnumMap<>(ArmourSet.class);
    private static final Map<ArmourSet, Identifier> TEXTURES = new EnumMap<>(ArmourSet.class);
    private static final Map<ArmourSet, ArmourMesh> RUNES = new EnumMap<>(ArmourSet.class);
    /** Mastery IV's awakened parts (&lt;piece&gt;_&lt;bone&gt;_awakened.mesh), drawn over the piece's own: [piece][bone]. */
    private static final Map<ArmourSet, ArmourMesh[][]> AWAKENED = new EnumMap<>(ArmourSet.class);

    /** A piece's bones without their living parts (&lt;piece&gt;_&lt;bone&gt;_still.mesh): only bones that have living parts. */
    private static final Map<ArmourSet, ArmourMesh[][]> STILL = new EnumMap<>(ArmourSet.class);
    /** A set's living parts by piece (live.json, SPEC §44.10). */
    private static final Map<ArmourSet, ArmourLive.Part[][]> LIVE = new EnumMap<>(ArmourSet.class);

    /** A piece's living parts, or null where it has none: then {@link #piece} is all of it. */
    public static ArmourLive.Part @Nullable [] live(ArmourSet set, ArmorType piece) {
        ArmourLive.Part[][] table = LIVE.get(set);
        return table == null ? null : table[piece.ordinal()];
    }

    /**
     * A piece's bones without their living parts, for a drawer that draws those where they are now: null at a bone
     * whose every box lives (and at any bone with no living part: draw {@link #piece}'s there).
     */
    public static ArmourMesh @Nullable [] still(ArmourSet set, ArmorType piece) {
        ArmourMesh[][] table = STILL.get(set);
        return table == null ? null : table[piece.ordinal()];
    }

    static ArmourMesh @Nullable [][] of(ArmourSet set) {
        return MESHES.get(set);
    }

    /** A piece's meshes by bone, or null where the set has none for it. */
    public static ArmourMesh @Nullable [] piece(ArmourSet set, ArmorType piece) {
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

    public static Identifier texture(ArmourSet set) {
        return TEXTURES.computeIfAbsent(set, s -> Awaken.id("textures/entity/armour/" + s.id + ".png"));
    }

    static void reload(ResourceManager manager) {
        MESHES.clear();
        RUNES.clear();
        AWAKENED.clear();
        STILL.clear();
        LIVE.clear();
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
            if (found > 0) live(manager, set);
            if (found > 0) MESHES.put(set, table);
            else if (set.hunter != null) Awaken.LOG.error("[armour] no meshes for the Hunter set {}: vanilla's shell is drawn", set.id);
        }
        ArmourPipelines.recheck();
    }

    /**
     * A set's living parts and the still meshes beside them. Anything wrong in the file (a mesh missing, a name unknown)
     * and the set has none: it is drawn whole, at rest, as before.
     */
    private static void live(ResourceManager manager, ArmourSet set) {
        Identifier file = Awaken.id("armour/" + set.id + "/live.json");
        var res = manager.getResource(file);
        if (res.isEmpty()) return;
        try (var in = new java.io.InputStreamReader(res.get().open(), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.List<java.util.List<ArmourLive.Part>> byPiece = new java.util.ArrayList<>();
            for (int i = 0; i < ArmorType.values().length; i++) byPiece.add(new java.util.ArrayList<>());
            ArmourMesh[][] still = new ArmourMesh[ArmorType.values().length][];
            for (var el : com.google.gson.JsonParser.parseReader(in).getAsJsonArray()) {
                com.google.gson.JsonObject o = el.getAsJsonObject();
                String pieceName = o.get("piece").getAsString(), boneName = o.get("bone").getAsString();
                ArmorType piece = null;
                for (ArmorType t : ArmorType.values()) if (t.getName().equals(pieceName)) piece = t;
                Bone bone = Bone.valueOf(boneName.toUpperCase(java.util.Locale.ROOT));
                ArmourMesh mesh = load(manager, Awaken.id("armour/" + set.id + "/" + o.get("mesh").getAsString() + ".mesh"));
                if (piece == null || mesh == null) throw new IOException("no piece or mesh for " + pieceName + "/" + boneName);
                byPiece.get(piece.ordinal()).add(ArmourLive.read(o, bone, mesh));
                if (still[piece.ordinal()] == null) still[piece.ordinal()] = new ArmourMesh[BONES.length];
                still[piece.ordinal()][bone.ordinal()] = load(manager, Awaken.id("armour/" + set.id + "/" + pieceName + "_" + bone.file + "_still.mesh"));
            }
            ArmourLive.Part[][] parts = new ArmourLive.Part[ArmorType.values().length][];
            for (int i = 0; i < parts.length; i++) if (!byPiece.get(i).isEmpty()) parts[i] = byPiece.get(i).toArray(ArmourLive.Part[]::new);
            LIVE.put(set, parts);
            STILL.put(set, still);
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.error("[armour] {} did not load, so the set stands still: {}", file, e.toString());
        }
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
