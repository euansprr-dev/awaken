package dev.awaken.anims.client.station;

import com.mojang.math.Quadrant;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.model.standalone.UnbakedStandaloneModel;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Native chunk geometry: a furnace has a mouth with inner walls, rather than a closed cube behind floating icons. */
final class FurnaceModels {
    private FurnaceModels() {}
    private static final Map<BlockState, StandaloneModelKey<BlockStateModel>> KEYS = new IdentityHashMap<>();
    private static volatile Map<BlockState, Selection> installed = Map.of();

    static void init(IEventBus modBus) {
        modBus.addListener(ModelEvent.RegisterStandalone.class, FurnaceModels::register);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, FurnaceModels::replace);
        modBus.addListener(AddClientReloadListenersEvent.class, e -> {
            var id = Awaken.id("furnace_renderer_compatibility");
            e.addListener(id, (ResourceManagerReloadListener) resources -> {
                var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
                var probe = new BlockEntityRenderState();
                for (var entry : installed.entrySet()) {
                    BlockState state = entry.getKey();
                    probe.blockEntityType = state.is(Blocks.SMOKER) ? BlockEntityTypes.SMOKER
                            : state.is(Blocks.BLAST_FURNACE) ? BlockEntityTypes.BLAST_FURNACE : BlockEntityTypes.FURNACE;
                    entry.getValue().enabled = dispatcher.getRenderer(probe) == null;
                }
            });
            e.addDependency(VanillaClientListeners.MODELS, id);
            e.addDependency(VanillaClientListeners.BLOCK_ENTITY_RENDERER, id);
        });
    }

    private static void register(ModelEvent.RegisterStandalone event) {
        KEYS.clear();
        Block[] blocks = {Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE};
        String[] names = {"furnace", "smoker", "blast_furnace"};
        for (int i = 0; i < blocks.length; i++) for (BlockState state : blocks[i].getStateDefinition().getPossibleStates()) {
            String name = names[i] + (state.getValue(AbstractFurnaceBlock.LIT) ? "_lit" : "");
            Direction facing = state.getValue(AbstractFurnaceBlock.FACING);
            Quadrant turn = switch (facing) { case EAST -> Quadrant.R90; case SOUTH -> Quadrant.R180; case WEST -> Quadrant.R270; default -> Quadrant.R0; };
            Variant variant = new Variant(Awaken.id("block/furnace_interior/" + name)).withYRot(turn);
            var key = new StandaloneModelKey<BlockStateModel>(() -> "furnace interior " + name + " " + facing);
            KEYS.put(state, key);
            event.register(key, new UnbakedStandaloneModel<>() {
                public void resolveDependencies(ResolvableModel.Resolver resolver) { variant.resolveDependencies(resolver); }
                public BlockStateModel bake(ModelBaker baker, ModelDebugName debug) { return new SingleVariant(variant.bake(baker)); }
            });
        }
    }

    private static void replace(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, Selection> next = new IdentityHashMap<>();
        var result = event.getBakingResult();
        for (var entry : KEYS.entrySet()) {
            BlockStateModel old = result.blockStateModels().get(entry.getKey());
            BlockStateModel replacement = result.standaloneModels().get(entry.getValue());
            // Leave custom/dynamic/wrapped and non-cube models to their renderer. Never modify another model in place.
            if (replacement == null || !cube(old)) continue;
            Selection selected = new Selection(old, replacement);
            result.blockStateModels().put(entry.getKey(), selected);
            next.put(entry.getKey(), selected);
        }
        installed = Map.copyOf(next);
    }

    static boolean active(BlockState state) {
        Selection expected = installed.get(state);
        return expected != null && expected.enabled && Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state) == expected;
    }

    /** Select once after renderers reload; meshing workers read only this immutable model choice. */
    private static final class Selection implements BlockStateModel {
        private final BlockStateModel original, recessed;
        private volatile boolean enabled;
        Selection(BlockStateModel original, BlockStateModel recessed) { this.original = original; this.recessed = recessed; }
        private BlockStateModel selected() { return enabled ? recessed : original; }
        @Override public void collectParts(RandomSource random, List<BlockStateModelPart> parts) { selected().collectParts(random, parts); }
        @Override public Material.Baked particleMaterial() { return selected().particleMaterial(); }
        @Override public int materialFlags() { return selected().materialFlags(); }
        @Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return selected().createGeometryKey(level, pos, state, random);
        }
    }

    private static boolean cube(BlockStateModel model) {
        if (model == null || model.getClass() != SingleVariant.class) return false;
        List<BlockStateModelPart> parts = new ArrayList<>(1);
        model.collectParts(RandomSource.create(0), parts);
        if (parts.size() != 1 || !parts.getFirst().getQuads(null).isEmpty()) return false;
        for (Direction direction : Direction.values()) {
            List<BakedQuad> quads = parts.getFirst().getQuads(direction);
            if (quads.size() != 1) return false;
            BakedQuad q = quads.getFirst();
            int corners = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                var pos = q.position(vertex);
                if ((pos.x() != 0 && pos.x() != 1) || (pos.y() != 0 && pos.y() != 1) || (pos.z() != 0 && pos.z() != 1)) return false;
                double plane = direction.getAxis() == Direction.Axis.X ? pos.x() : direction.getAxis() == Direction.Axis.Y ? pos.y() : pos.z();
                if (plane != (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) return false;
                corners |= 1 << ((int) pos.x() + 2 * (int) pos.y() + 4 * (int) pos.z());
            }
            if (Integer.bitCount(corners) != 4) return false;
        }
        return true;
    }
}
