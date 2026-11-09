package dev.awaken.shaping.client;

import dev.awaken.Awaken;
import dev.awaken.shaping.ShapingContent;
import dev.awaken.shaping.ShapingPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Map;

/** Made by Hand's client (SPEC §52): cut blocks drawn in their looks, the Mallet's mouse controls, the ring, the knock. */
public final class ShapingClient {
    private ShapingClient() {}

    private static volatile List<Identifier> known = List.of();

    static List<Identifier> known() {
        return known;
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ModelEvent.ModifyBakingResult.class, ShapingClient::bake);
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class,
                e -> e.register(List.of(new LookTint()), ShapingContent.SHAPED.get()));
        modBus.addListener(RegisterGuiLayersEvent.class,
                e -> e.registerAbove(VanillaGuiLayers.HOTBAR, Awaken.id("mallet_hud"), MalletHud::render));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(ShapingPayloads.Known.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> known = msg.learned()));
            e.register(ShapingPayloads.Fx.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> KnockFx.add(msg)));
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, MalletInput::interact);
        bus.addListener(InputEvent.MouseScrollingEvent.class, MalletInput::scroll);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            MalletInput.tick();
            KnockFx.tick();
        });
        bus.addListener(SubmitCustomGeometryEvent.class, ShapingOverlay::submit);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, KnockFx::view);
    }

    private static void bake(ModelEvent.ModifyBakingResult e) {
        ShapedModel.clear();
        Map<BlockState, BlockStateModel> models = e.getBakingResult().blockStateModels();
        try {
            for (BlockState s : ShapingContent.SHAPED.get().getStateDefinition().getPossibleStates()) {
                BlockStateModel old = models.get(s);
                if (old != null) models.put(s, new ShapedModel(old));
            }
        } catch (UnsupportedOperationException ex) {
            Awaken.LOG.warn("Cut blocks can't be drawn: the block models could not be wrapped", ex);
        }
    }

    /** A cut block is tinted as its look is, so a cut of grass or leaves would still take the biome's colour. */
    private static final class LookTint implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            BlockState look = ShapedModel.lookAt(level, pos);
            BlockTintSource source = look == null ? null : Minecraft.getInstance().getBlockColors().getTintSource(look, 0);
            return source == null ? -1 : source.colorInWorld(look, level, pos);
        }
    }
}
