package dev.awaken.terrain.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.Awaken;
import dev.awaken.compat.Companions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;

import java.util.Map;
import java.util.Optional;

/**
 * Waving leaves under Sodium (docs/spikes/waving-leaves.md). Three parts, all switched together:
 * <ul>
 * <li>a built-in client pack ({@code resourcepacks/awaken_waving_leaves}) that replaces Sodium's terrain vertex shader,
 * offered only while {@link WaveSupport} recognises the loaded Sodium and the {@code waving_leaves} setting is on;</li>
 * <li>{@link WavingLeavesModel}: every {@link LeavesBlock} model, vanilla or modded, gets the leaf marker alpha;</li>
 * <li>{@link WindTexels}: the wind, written into the block atlas once a frame.</li>
 * </ul>
 * Without Sodium (the vanilla renderer) none of it is installed and leaves are still. With Iris loaded it is never
 * offered; with an Iris shaderpack on, the pack's shader is not used anyway.
 */
public final class WavingLeaves {
    private WavingLeaves() {}

    static final String PACK_PATH = "resourcepacks/awaken_waving_leaves";
    static final String PACK_ID = "mod/" + Awaken.MODID + ":awaken_waving_leaves";

    private static volatile boolean packOffered; // the last pack scan offered the shader pack
    private static int markedStates;

    public static void init(IEventBus modBus) {
        modBus.addListener(WavingLeaves::addPack);
        modBus.addListener(WavingLeaves::markLeafModels);
        modBus.addListener(WavingLeaves::atlasStitched);
        NeoForge.EVENT_BUS.addListener(WavingLeaves::frame);
        NeoForge.EVENT_BUS.addListener(WavingLeaves::commands);
    }

    /** Waving can run here and the player wants it. */
    static boolean wanted() {
        return TerrainClientConfig.wavingLeaves() && WaveSupport.supported();
    }

    private static void addPack(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        // asked again at every pack scan, so turning the setting off and reloading removes the pack
        e.addRepositorySource(consumer -> {
            packOffered = false;
            if (!wanted()) return;
            var awaken = ModList.get().getModContainerById(Awaken.MODID).orElseThrow().getModInfo();
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(PACK_ID, Component.literal("AWAKEN waving leaves"), PackSource.BUILT_IN, Optional.empty()),
                    new JarContentsPackResources.JarContentsResourcesSupplier(awaken.getOwningFile().getFile().getContents(), PACK_PATH),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack == null) {
                Awaken.LOG.warn("Waving leaves: the built-in shader pack could not be read");
                return;
            }
            consumer.accept(pack);
            packOffered = true;
        });
    }

    private static void markLeafModels(ModelEvent.ModifyBakingResult e) {
        WavingLeavesModel.clearCache();
        markedStates = 0;
        if (!packOffered) return;
        int n = 0;
        try {
            for (Map.Entry<BlockState, BlockStateModel> en : e.getBakingResult().blockStateModels().entrySet()) {
                if (en.getKey().getBlock() instanceof LeavesBlock && !(en.getValue() instanceof WavingLeavesModel)) {
                    en.setValue(new WavingLeavesModel(en.getValue()));
                    n++;
                }
            }
        } catch (UnsupportedOperationException ex) {
            Awaken.LOG.warn("Waving leaves: block models could not be marked", ex);
        }
        markedStates = n;
        Awaken.LOG.info("Waving leaves: marked {} leaf block states", n);
    }

    private static void atlasStitched(TextureAtlasStitchedEvent e) {
        if (e.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) WindTexels.onBlockAtlasStitched(e.getAtlas());
    }

    private static void frame(RenderFrameEvent.Pre e) {
        if (!packOffered || Companions.shaderPackActive()) return;
        Minecraft mc = Minecraft.getInstance();
        WindTexels.write(mc, e.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    /** {@code /awaken terrain waving on|off|status|debug <0..2>} (client side): switch and reload, or say why it is off. */
    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("terrain").then(Commands.literal("waving")
                .then(Commands.literal("on").executes(c -> toggle(true)))
                .then(Commands.literal("off").executes(c -> toggle(false)))
                .then(Commands.literal("status").executes(c -> status()))
                // measuring aid: 0 normal, 1 shader runs but finds no wind (exits early), 2 full shader, amplitude 0
                .then(Commands.literal("debug").then(Commands.argument("mode", IntegerArgumentType.integer(0, 2))
                        .executes(c -> debug(IntegerArgumentType.getInteger(c, "mode"))))))));
    }

    private static int toggle(boolean on) {
        TerrainClientConfig.setWavingLeaves(on);
        Awaken.LOG.info("Waving leaves: setting {}, reloading resources", on ? "on" : "off");
        Minecraft.getInstance().reloadResourcePacks();
        return Command.SINGLE_SUCCESS;
    }

    private static int debug(int mode) {
        WindTexels.debugMode = mode;
        Awaken.LOG.info("Waving leaves: debug mode {}", mode);
        return Command.SINGLE_SUCCESS;
    }

    private static int status() {
        String reason = WaveSupport.unsupportedReason();
        String line = "Waving leaves: setting " + (TerrainClientConfig.wavingLeaves() ? "on" : "off")
                + (reason == null ? ", Sodium supported" : ", unavailable: " + reason)
                + ", shader pack " + (packOffered ? "on" : "off") + ", " + markedStates + " leaf states marked, wind texels "
                + (WindTexels.cornerFree() ? "free" : "blocked") + (Companions.shaderPackActive() ? ", Iris shaderpack on" : "");
        Awaken.LOG.info(line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
