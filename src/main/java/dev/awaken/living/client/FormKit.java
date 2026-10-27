package dev.awaken.living.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.living.combat.Forms.Species;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pieces the forms hang on the vanilla bodies (SPEC §21.11): tusks, horns, plates, spines, crowns. {@code assets/awaken/forms/kit.json}
 * (written by {@code tools/art/specs/forms.py}, which also paints their textures, so the painter and this builder cannot disagree)
 * lists the cubes of each layout's rung; for each species and rung a layer definition is the vanilla body with its own cubes cleared
 * (every part keeps its place, so the vanilla class animates it exactly as it animates the creature) and the kit's cubes added.
 */
final class FormKit {
    private FormKit() {}

    /** One cube of a kit: a part of the vanilla body it hangs on, the texture corner, the box and where it sits on the part. */
    record Cube(String part, String name, int u, int v, float x, float y, float z, float w, float h, float d,
                float ox, float oy, float oz, float rx, float ry, float rz, boolean mirror) {}

    private static Map<String, Map<Integer, List<Cube>>> data;

    private static synchronized List<Cube> cubes(String layout, int rung) {
        if (data == null) data = load();
        return data.getOrDefault(layout, Map.of()).getOrDefault(rung, List.of());
    }

    private static Map<String, Map<Integer, List<Cube>>> load() {
        Map<String, Map<Integer, List<Cube>>> out = new HashMap<>();
        try (InputStream in = FormKit.class.getResourceAsStream("/assets/awaken/forms/kit.json")) {
            if (in == null) {
                Awaken.LOG.warn("[forms] kit.json is missing: creatures wear their overlays and no pieces");
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var layout : root.getAsJsonObject("layouts").entrySet()) {
                Map<Integer, List<Cube>> ranks = new HashMap<>();
                for (var rung : layout.getValue().getAsJsonObject().getAsJsonObject("ranks").entrySet()) {
                    List<Cube> list = new ArrayList<>();
                    for (JsonElement e : rung.getValue().getAsJsonArray()) list.add(cube(e.getAsJsonObject()));
                    ranks.put(Integer.parseInt(rung.getKey()), list);
                }
                out.put(layout.getKey(), ranks);
            }
        } catch (Exception e) {
            Awaken.LOG.warn("[forms] kit.json could not be read: {}", e.toString());
            out.clear();
        }
        return out;
    }

    private static Cube cube(JsonObject o) {
        JsonArray uv = o.getAsJsonArray("uv"), box = o.getAsJsonArray("box"), pose = o.getAsJsonArray("pose");
        return new Cube(o.get("part").getAsString(), o.get("name").getAsString(), uv.get(0).getAsInt(), uv.get(1).getAsInt(),
                box.get(0).getAsFloat(), box.get(1).getAsFloat(), box.get(2).getAsFloat(), box.get(3).getAsFloat(), box.get(4).getAsFloat(), box.get(5).getAsFloat(),
                pose.get(0).getAsFloat(), pose.get(1).getAsFloat(), pose.get(2).getAsFloat(), pose.get(3).getAsFloat(), pose.get(4).getAsFloat(), pose.get(5).getAsFloat(),
                o.has("mirror") && o.get("mirror").getAsBoolean());
    }

    static ModelLayerLocation location(Species sp, int rung) {
        return new ModelLayerLocation(Awaken.id("form_" + sp.id + "_" + rung), "main");
    }

    /** The vanilla body of a species with its own cubes cleared and the rung's kit added (then scaled as the vanilla one is). */
    static LayerDefinition definition(Species sp, int rung) {
        try {
            return build(sp, rung);
        } catch (RuntimeException failed) {
            // never take the client down for a kit: the body with nothing on it
            Awaken.LOG.warn("[forms] {} rung {} has no kit: {}", sp.id, rung, failed.toString());
            LayerDefinition bare = base(sp).apply(mesh -> {
                mesh.getRoot().clearRecursively();
                return mesh;
            });
            float s = scale(sp);
            return s == 1.0F ? bare : bare.apply(MeshTransformer.scaling(s));
        }
    }

    private static LayerDefinition build(Species sp, int rung) {
        List<Cube> kit = cubes(sp.layout, rung);
        LayerDefinition def = base(sp).apply(mesh -> {
            PartDefinition root = mesh.getRoot();
            root.clearRecursively();
            for (Cube c : kit) {
                PartDefinition part = part(root, c.part());
                if (part == null) continue;
                CubeListBuilder b = CubeListBuilder.create().texOffs(c.u(), c.v());
                if (c.mirror()) b.mirror();
                b.addBox(c.x(), c.y(), c.z(), c.w(), c.h(), c.d());
                part.addOrReplaceChild("kit_" + c.name(), b, PartPose.offsetAndRotation(c.ox(), c.oy(), c.oz(), c.rx(), c.ry(), c.rz()));
            }
            return mesh;
        });
        float s = scale(sp);
        return s == 1.0F ? def : def.apply(MeshTransformer.scaling(s));
    }

    /** A part by its path from the root ("head" or "head/hat"), or null when this vanilla body has no such part. */
    private static PartDefinition part(PartDefinition root, String path) {
        PartDefinition p = root;
        for (String name : path.split("/")) {
            try {
                p = p.getChild(name);
            } catch (RuntimeException missing) {
                return null;
            }
            if (p == null) return null;
        }
        return p;
    }

    /** The vanilla layer definition of a species before any scaling (the factories vanilla's own `LayerDefinitions` calls). */
    private static LayerDefinition base(Species sp) {
        return switch (sp) {
            case PIG -> PigModel.createBodyLayer(CubeDeformation.NONE);
            case COW, MOOSHROOM -> CowModel.createBodyLayer();
            case SHEEP -> SheepModel.createBodyLayer();
            case CHICKEN -> AdultChickenModel.createBodyLayer();
            case ZOMBIE, HUSK -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
            case SKELETON, STRAY -> SkeletonModel.createBodyLayer();
            case SPIDER, CAVE_SPIDER -> SpiderModel.createSpiderBodyLayer();
            case CREEPER -> CreeperModel.createBodyLayer(CubeDeformation.NONE);
        };
    }

    /** What vanilla scales the mesh by (the husk a little, the cave spider to 0.7). */
    private static float scale(Species sp) {
        return sp == Species.HUSK ? 1.0625F : sp == Species.CAVE_SPIDER ? 0.7F : 1.0F;
    }

    /** The vanilla model class of a species over a baked body, so it animates from the same render state. */
    static EntityModel<?> model(Species sp, ModelPart root) {
        return switch (sp) {
            case PIG -> new PigModel(root);
            case COW, MOOSHROOM -> new CowModel(root);
            case SHEEP -> new SheepModel(root);
            case CHICKEN -> new AdultChickenModel(root);
            case ZOMBIE, HUSK -> new ZombieModel<ZombieRenderState>(root);
            case SKELETON, STRAY -> new SkeletonModel<SkeletonRenderState>(root);
            case SPIDER, CAVE_SPIDER -> new SpiderModel(root);
            case CREEPER -> new CreeperModel(root);
        };
    }
}
