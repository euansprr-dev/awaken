package dev.awaken.gear.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.awaken.Awaken;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.gear.Silhouette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The late sets' silhouettes: Lordscale and Sovereign Weave armour is drawn with a model that has more than the
 * vanilla boxes (horns, a crest, pauldrons, spines and claws; a crown, a collar, a cape, pauldrons and fins), built
 * from the parts in {@code assets/awaken/gear_silhouettes/<set>.json} and textured from a 128x64 silhouette texture
 * whose top-left 64x32 is the normal layer. If anything about that fails, the piece falls back to the vanilla model and
 * its normal 64x32 layer (logged once), so nothing ever renders with mismatched UVs. Leggings keep the vanilla model.
 */
public final class Silhouettes {
    private Silhouettes() {}

    private static final Map<HunterTier, Map<EquipmentSlot, HumanoidModel<HumanoidRenderState>>> MODELS = new EnumMap<>(HunterTier.class);
    private static final Map<HunterTier, Boolean> FAILED = new EnumMap<>(HunterTier.class);
    private static final HunterTier[] LATE = {HunterTier.A, HunterTier.S};

    static void registerExtensions(RegisterClientExtensionsEvent event) {
        for (HunterTier tier : LATE) {
            event.registerItem(new Extension(tier), GearItems.get(tier, Piece.HELMET), GearItems.get(tier, Piece.CHESTPLATE),
                    GearItems.get(tier, Piece.BOOTS));
        }
    }

    /** Models are built lazily from resources; a resource reload throws them away. */
    static void addReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(Awaken.id("gear_silhouettes"), (ResourceManagerReloadListener) manager -> {
            MODELS.clear();
            FAILED.clear();
        });
    }

    private static EquipmentSlot slotOf(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq == null ? EquipmentSlot.MAINHAND : eq.slot();
    }

    /** The silhouette model for a tier's piece in this slot, or null to use vanilla's. */
    static @Nullable HumanoidModel<HumanoidRenderState> model(HunterTier tier, EquipmentSlot slot) {
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.FEET) return null;
        if (FAILED.getOrDefault(tier, false)) return null;
        Map<EquipmentSlot, HumanoidModel<HumanoidRenderState>> bySlot = MODELS.get(tier);
        if (bySlot == null) {
            try {
                Silhouette spec = load(tier);
                List<String> bad = spec.problems();
                if (!bad.isEmpty()) throw new IllegalStateException(String.join("; ", bad));
                bySlot = new EnumMap<>(EquipmentSlot.class);
                for (EquipmentSlot s : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET)) bySlot.put(s, build(spec, s));
                MODELS.put(tier, bySlot);
            } catch (RuntimeException | java.io.IOException e) {
                FAILED.put(tier, true);
                Awaken.LOG.warn("[gear] {} silhouette unavailable, drawing the plain armour: {}", tier.setName, e.toString());
                return null;
            }
        }
        return bySlot.get(slot);
    }

    private static Silhouette load(HunterTier tier) throws java.io.IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(Awaken.id(Silhouette.path(tier)));
        if (resource.isEmpty()) throw new java.io.FileNotFoundException(Silhouette.path(tier));
        try (Reader r = resource.get().openAsReader()) {
            return Silhouette.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(r)).getOrThrow();
        }
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            default -> "feet";
        };
    }

    /** Vanilla's armour mesh for the slot (outer 1.0, inner 0.5) plus this slot's parts, at the silhouette texture's size. */
    static HumanoidModel<HumanoidRenderState> build(Silhouette spec, EquipmentSlot slot) {
        ArmorModelSet<MeshDefinition> set = HumanoidModel.createArmorMeshSet(LayerDefinitions.INNER_ARMOR_DEFORMATION, LayerDefinitions.OUTER_ARMOR_DEFORMATION);
        MeshDefinition mesh = set.get(slot);
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> defs = new HashMap<>();
        for (String base : Silhouette.BASE) defs.put(base, root.getChild(base));
        String name = slotName(slot);
        for (Silhouette.Part p : spec.parts()) {
            if (!p.slot().equals(name)) continue;
            PartDefinition parent = defs.get(p.parent());
            if (parent == null) throw new IllegalStateException(p.name() + ": no parent " + p.parent());
            List<Float> b = p.box(), o = p.pose();
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(p.uv().get(0), p.uv().get(1)).mirror(p.mirror())
                    .addBox(b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5));
            defs.put(p.name(), parent.addOrReplaceChild(p.name(), cubes,
                    PartPose.offsetAndRotation(o.get(0), o.get(1), o.get(2), o.get(3), o.get(4), o.get(5))));
        }
        return new HumanoidModel<>(LayerDefinition.create(mesh, spec.texture().get(0), spec.texture().get(1)).bakeRoot());
    }

    private static final class Extension implements IClientItemExtensions {
        private final HunterTier tier;
        private final Identifier texture;

        Extension(HunterTier tier) {
            this.tier = tier;
            this.texture = Awaken.id(Silhouette.texture(tier));
        }

        @Override
        public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType type, Model original) {
            if (type != EquipmentClientInfo.LayerType.HUMANOID) return original;
            HumanoidModel<HumanoidRenderState> m = model(tier, slotOf(stack));
            return m == null ? original : m;
        }

        @Override
        public @Nullable Identifier getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type, EquipmentClientInfo.Layer layer, Identifier fallback) {
            if (type != EquipmentClientInfo.LayerType.HUMANOID) return null;
            return model(tier, slotOf(stack)) == null ? null : texture;
        }
    }
}
