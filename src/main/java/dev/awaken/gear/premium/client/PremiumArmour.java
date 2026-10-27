package dev.awaken.gear.premium.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.awaken.Awaken;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterGearItem;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.gear.premium.PremiumSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The premium test (docs/armour/PREMIUM-TEST.md; branch pack/armour-premium-test, never merged): Veilwoven drawn at one of three
 * fidelity levels, switched live by {@code armour fidelity N} in an autopilot script.
 * <ul>
 *   <li>0: as it ships (the flat 64x32 layers);</li>
 *   <li>1: the planned look: a model of real parts built from {@code gear_premium/veilwoven.json}, vanilla's armour render type and
 *       light, and an emissive pass for the glowing texels;</li>
 *   <li>2: the same model drawn by our own shader (the weapons' painted light, studio metal, rim and flowing glow), with vanilla's layer
 *       skipped (a tint of 0) and the plain render type as the fallback if the pipeline won't build.</li>
 * </ul>
 */
public final class PremiumArmour {
    private PremiumArmour() {}

    /** 0 shipped, 1 planned look, 2 plus the shader. */
    public static volatile int fidelity = 0;

    public static final Identifier TEXTURE = Awaken.id(PremiumSpec.TEXTURE);
    public static final Identifier GLOW = Awaken.id(PremiumSpec.GLOW);
    /** The tier's rank colour (B), the shader's rim light. */
    public static final int ACCENT = 0xB48CFF;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static @Nullable Map<EquipmentSlot, HumanoidModel<HumanoidRenderState>> models;
    private static boolean failed;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterClientExtensionsEvent.class, PremiumArmour::registerExtensions);
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("gear_premium"), (ResourceManagerReloadListener) m -> {
            models = null;
            failed = false;
            PremiumPipelines.recheck();
        }));
        modBus.addListener(RegisterRenderPipelinesEvent.class, PremiumPipelines::onRegister);
        modBus.addListener(EntityRenderersEvent.AddLayers.class, PremiumLayer::onAddLayers);
    }

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        for (Piece piece : Piece.ARMOUR) event.registerItem(new Extension(), GearItems.get(HunterTier.B, piece));
    }

    /** What the autopilot's {@code armour} line does. */
    public static String autopilot(String arg) {
        String[] w = arg.trim().split("\\s+");
        if (w.length == 2 && w[0].equals("fidelity")) {
            fidelity = Math.max(0, Math.min(2, Integer.parseInt(w[1])));
            return "fidelity " + fidelity + (fidelity == 2 && !PremiumPipelines.ok() ? " (the shader did not load: plain render type)" : "");
        }
        return "usage: armour fidelity 0|1|2";
    }

    public static boolean isHero(ItemStack stack) {
        return stack.getItem() instanceof HunterGearItem g && g.tier == HunterTier.B && g.piece.isArmour();
    }

    public static EquipmentSlot[] slots() {
        return SLOTS;
    }

    /** The model for a slot, built once from the spec; null if it failed (then everything draws as it ships). */
    public static @Nullable HumanoidModel<HumanoidRenderState> model(EquipmentSlot slot) {
        if (failed) return null;
        if (models == null) {
            try {
                PremiumSpec spec = load();
                List<String> bad = spec.problems();
                if (!bad.isEmpty()) throw new IllegalStateException(String.join("; ", bad));
                Map<EquipmentSlot, HumanoidModel<HumanoidRenderState>> built = new EnumMap<>(EquipmentSlot.class);
                for (EquipmentSlot s : SLOTS) built.put(s, build(spec, s));
                models = built;
            } catch (RuntimeException | IOException e) {
                failed = true;
                Awaken.LOG.warn("[gear] the premium Veilwoven model is unavailable, drawing the plain armour: {}", e.toString());
                return null;
            }
        }
        return models.get(slot);
    }

    private static PremiumSpec load() throws IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResource(Awaken.id(PremiumSpec.PATH));
        if (resource.isEmpty()) throw new java.io.FileNotFoundException(PremiumSpec.PATH);
        try (Reader r = resource.get().openAsReader()) {
            return PremiumSpec.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(r)).getOrThrow();
        }
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            default -> "feet";
        };
    }

    /**
     * The six base parts in their standing places (and the head's hat, which HumanoidModel looks for), empty, and under them
     * this slot's parts as children: each its own cube at its own pose, shells and plates and cloth alike.
     */
    static HumanoidModel<HumanoidRenderState> build(PremiumSpec spec, EquipmentSlot slot) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("head", root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO));
        defs.get("head").addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        defs.put("body", root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO));
        defs.put("right_arm", root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F)));
        defs.put("left_arm", root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F)));
        defs.put("right_leg", root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F)));
        defs.put("left_leg", root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F)));
        String name = slotName(slot);
        // parts are listed parents first, but another slot's part may be a parent: walk the whole list, skip what isn't ours
        for (PremiumSpec.Part p : spec.parts()) {
            if (!p.slot().equals(name)) continue;
            PartDefinition parent = defs.get(p.parent());
            if (parent == null) throw new IllegalStateException(p.name() + ": no parent " + p.parent());
            List<Float> b = p.box(), g = p.grow(), o = p.pose();
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(p.uv().get(0), p.uv().get(1)).mirror(p.mirror())
                    .addBox(b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5), new CubeDeformation(g.get(0), g.get(1), g.get(2)));
            defs.put(p.name(), parent.addOrReplaceChild(p.name(), cubes,
                    PartPose.offsetAndRotation(o.get(0), o.get(1), o.get(2), o.get(3), o.get(4), o.get(5))));
        }
        return new HumanoidModel<>(LayerDefinition.create(mesh, spec.texture().get(0), spec.texture().get(1)).bakeRoot());
    }

    /** Veilwoven's four pieces: the premium model and texture when the test is on; vanilla's layer skipped at level 2. */
    private static final class Extension implements IClientItemExtensions {
        private static EquipmentSlot slotOf(ItemStack stack) {
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            return eq == null ? EquipmentSlot.MAINHAND : eq.slot();
        }

        private static @Nullable EquipmentSlot slotFor(ItemStack stack, EquipmentClientInfo.LayerType type) {
            if (type == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS) return EquipmentSlot.LEGS;
            return type == EquipmentClientInfo.LayerType.HUMANOID ? slotOf(stack) : null;
        }

        @Override
        public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType type, Model original) {
            if (fidelity < 1) return original;
            EquipmentSlot slot = slotFor(stack, type);
            HumanoidModel<HumanoidRenderState> m = slot == null ? null : model(slot);
            return m == null ? original : m;
        }

        @Override
        public @Nullable Identifier getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type, EquipmentClientInfo.Layer layer, Identifier fallback) {
            EquipmentSlot slot = fidelity < 1 ? null : slotFor(stack, type);
            return slot == null || model(slot) == null ? null : TEXTURE;
        }

        @Override
        public int getArmorLayerTintColor(ItemStack stack, EquipmentClientInfo.Layer layer, int layerIdx, int fallbackColor) {
            return fidelity >= 2 && model(slotOf(stack) == EquipmentSlot.MAINHAND ? EquipmentSlot.CHEST : slotOf(stack)) != null
                    ? 0 // our own layer draws it
                    : EquipmentLayerRenderer.getColorForLayer(layer, fallbackColor);
        }
    }
}
