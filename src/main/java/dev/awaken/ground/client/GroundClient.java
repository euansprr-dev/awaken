package dev.awaken.ground.client;

import com.mojang.brigadier.Command;
import dev.awaken.Awaken;
import dev.awaken.ground.LawnDrift;
import dev.awaken.ground.SeamlessRules;
import dev.awaken.ground.SoftSlabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client half of the Soft Land's seams (SPEC §31, P0): wraps the baked models of grass, podzol, mycelium and the
 * creeped grounds ({@link SeamlessModel}), and drifts the lawn's colour ({@link DriftTint}).
 */
public final class GroundClient {
    private GroundClient() {}

    private static int wrapStates, creepStates;

    public static void init(IEventBus modBus) {
        GroundClientConfig.register(modBus);
        FreshView.init();
        WildPlantsClient.init(modBus);
        WildTint.init();
        modBus.addListener(GroundClient::wrapModels);
        modBus.addListener(GroundClient::drift);
        NeoForge.EVENT_BUS.addListener(GroundClient::commands);
    }

    private static void wrapModels(ModelEvent.ModifyBakingResult e) {
        SeamlessModel.clearSprites();
        int wraps = 0, creeps = 0;
        try {
            for (Map.Entry<BlockState, BlockStateModel> en : e.getBakingResult().blockStateModels().entrySet()) {
                if (en.getValue() instanceof SeamlessModel) continue;
                Block b = en.getKey().getBlock();
                if (SeamlessRules.wrapperBlock(b)) {
                    en.setValue(SeamlessModel.of(en.getValue(), SeamlessModel.Kind.WRAP));
                    wraps++;
                } else if (SeamlessRules.creepedBlock(b)) {
                    en.setValue(SeamlessModel.of(en.getValue(), SeamlessModel.Kind.CREEP));
                    creeps++;
                }
            }
        } catch (UnsupportedOperationException ex) {
            Awaken.LOG.warn("Seamless ground: block models could not be wrapped", ex);
        }
        wrapStates = wraps;
        creepStates = creeps;
        Awaken.LOG.info("Seamless ground: wrapped {} grass-like and {} creeped block states", wraps, creeps);
    }

    // ---- the lawn's colour ----------------------------------------------------------------------------------------------------

    /** The grass-tinted blocks and which of their tint layers is the grass (pink petals and wildflowers have a blank first). */
    private static final Map<Block, Integer> GRASSY = Map.of(Blocks.GRASS_BLOCK, 0, Blocks.SHORT_GRASS, 0, Blocks.FERN, 0, Blocks.BUSH, 0,
            Blocks.TALL_GRASS, 0, Blocks.LARGE_FERN, 0, Blocks.PINK_PETALS, 1, Blocks.WILDFLOWERS, 1);

    private static void drift(RegisterColorHandlersEvent.BlockTintSources e) {
        BlockColors colors = e.getBlockColors();
        for (Map.Entry<Block, Integer> en : GRASSY.entrySet()) {
            List<BlockTintSource> layers = colors.getTintSources(en.getKey().defaultBlockState());
            int layer = en.getValue();
            if (layers.size() <= layer || layers.get(layer) instanceof DriftTint) continue;
            List<BlockTintSource> next = new ArrayList<>(layers);
            next.set(layer, new DriftTint(layers.get(layer)));
            e.register(next, en.getKey());
        }
        // the grass slab is tinted and drifted as the grass block is (its top, its fringe, its tuft)
        e.register(colors.getTintSources(Blocks.GRASS_BLOCK.defaultBlockState()), SoftSlabs.TURF_SLAB.get());
    }

    /** A grass colour source with the drift on it, for blocks in a world (City Sight's probe at the origin and items keep the plain colour). */
    static final class DriftTint implements BlockTintSource {
        private final BlockTintSource base;

        DriftTint(BlockTintSource base) {
            this.base = base;
        }

        @Override
        public int color(BlockState state) {
            return this.base.color(state);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            int c = this.base.colorInWorld(state, level, pos);
            if (!GroundClientConfig.drift()) return c;
            if (pos.getX() == 0 && pos.getY() == 0 && pos.getZ() == 0) return c;    // not a place: a per-biome probe
            return WildTint.apply(LawnDrift.apply(c, pos.getX(), pos.getZ()), pos.getX(), pos.getZ());
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return this.base.relevantProperties();
        }
    }

    // ---- /awaken ground ---------------------------------------------------------------------------------------------------------

    /** {@code /awaken ground status | seamless on|off | drift on|off} (client): the settings, saved, with the chunks redrawn. */
    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("ground")
                .then(Commands.literal("status").executes(c -> say(status())))
                .then(Commands.literal("seamless")
                        .then(Commands.literal("on").executes(c -> set(true, GroundClientConfig.drift())))
                        .then(Commands.literal("off").executes(c -> set(false, GroundClientConfig.drift()))))
                .then(Commands.literal("drift")
                        .then(Commands.literal("on").executes(c -> set(GroundClientConfig.seamless(), true)))
                        .then(Commands.literal("off").executes(c -> set(GroundClientConfig.seamless(), false))))));
    }

    private static String status() {
        return "Ground: seamless " + (GroundClientConfig.seamless() ? "on" : "off") + ", lawn drift " + (GroundClientConfig.drift() ? "on" : "off")
                + "; wrapped " + wrapStates + " grass-like and " + creepStates + " creeped block states";
    }

    private static int set(boolean seamless, boolean drift) {
        GroundClientConfig.set(seamless, drift);
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.levelExtractor.allChanged();
        } catch (RuntimeException ex) {
            mc.reloadResourcePacks();
        }
        return say(status());
    }

    private static int say(String line) {
        Awaken.LOG.info(line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
