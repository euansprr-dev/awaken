package dev.awaken.terrain.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.Awaken;
import dev.awaken.compat.Companions;
import dev.awaken.terrain.PlantSway;
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
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;

import java.util.Map;
import java.util.Optional;

/**
 * Waving leaves and swaying plants under Sodium (docs/spikes/waving-leaves.md, SPEC 15). Three parts, all switched
 * together:
 * <ul>
 * <li>a built-in client pack ({@code resourcepacks/awaken_waving_leaves}) that replaces Sodium's terrain vertex shader,
 * offered only while {@link WaveSupport} recognises the loaded Sodium and the {@code waving_leaves} setting is on;</li>
 * <li>{@link WavingModel}: every {@link LeavesBlock} model, vanilla or modded, gets the leaf marker alpha, and every
 * plant {@link PlantSway} names (grass, flowers, crops, saplings, cane, bamboo, vines, kelp, pads...) its per-vertex
 * weights;</li>
 * <li>{@link WindTexels}: the wind, written into the block atlas once a frame.</li>
 * </ul>
 * Without Sodium (the vanilla renderer) none of it is installed and nothing moves. With Iris loaded it is never
 * offered; with an Iris shaderpack on, the pack's shader is not used anyway.
 */
public final class Waving {
    private Waving() {}

    static final String PACK_PATH = "resourcepacks/awaken_waving_leaves";
    static final String PACK_ID = "mod/" + Awaken.MODID + ":awaken_waving_leaves";

    private static volatile boolean packOffered; // the last pack scan offered the shader pack
    private static int markedStates, plantStates;

    public static void init(IEventBus modBus) {
        modBus.addListener(Waving::addPack);
        modBus.addListener(Waving::markModels);
        modBus.addListener(Waving::atlasStitched);
        NeoForge.EVENT_BUS.addListener(Waving::commands);
    }

    /** Waving can run here and the player wants it. */
    static boolean wanted() {
        return TerrainClientConfig.waving() && WaveSupport.supported();
    }

    private static void addPack(AddPackFindersEvent e) {
        if (e.getPackType() != PackType.CLIENT_RESOURCES) return;
        // asked again at every pack scan, so turning the setting off and reloading removes the pack
        e.addRepositorySource(consumer -> {
            packOffered = false;
            if (!wanted()) return;
            var awaken = ModList.get().getModContainerById(Awaken.MODID).orElseThrow().getModInfo();
            Pack pack = Pack.readMetaAndCreate(
                    new PackLocationInfo(PACK_ID, Component.literal("AWAKEN waving leaves and plants"), PackSource.BUILT_IN, Optional.empty()),
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

    private static void markModels(ModelEvent.ModifyBakingResult e) {
        WavingModel.clearCache();
        markedStates = 0;
        plantStates = 0;
        if (!packOffered) return;
        int leaves = 0, plants = 0;
        try {
            for (Map.Entry<BlockState, BlockStateModel> en : e.getBakingResult().blockStateModels().entrySet()) {
                if (en.getValue() instanceof WavingModel) continue;
                BlockState state = en.getKey();
                if (PlantSway.isLeaf(state)) {
                    en.setValue(WavingModel.leaves(en.getValue()));
                    leaves++;
                } else {
                    PlantSway.Rule rule = PlantSway.rule(state);
                    if (rule != null) {
                        en.setValue(WavingModel.plant(en.getValue(), rule));
                        plants++;
                    }
                }
            }
        } catch (UnsupportedOperationException ex) {
            Awaken.LOG.warn("Waving leaves: block models could not be marked", ex);
        }
        markedStates = leaves;
        plantStates = plants;
        Awaken.LOG.info("Waving leaves: marked {} leaf block states and {} plant block states", leaves, plants);
    }

    private static void atlasStitched(TextureAtlasStitchedEvent e) {
        if (e.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) WindTexels.onBlockAtlasStitched(e.getAtlas());
    }

    /** Once a frame, after {@link ClientWind} is set: the wind for the shader. */
    static void frame(Minecraft mc, float partialTick) {
        if (!packOffered || Companions.shaderPackActive()) return;
        WindTexels.write(mc, partialTick);
    }

    /**
     * {@code /awaken terrain waving on|off|status|debug <0..3>|plants <air> <water> <sheen> <push>|probe frames <n>|probe
     * seconds <s>|probe turn|probe stop} (client side): switch and reload (leaves and plants together), say why it is
     * off, tune the plants live (blocks of sway per unit of weight; a tuning aid, not saved), or log the shader's offsets
     * frame by frame ({@link SwayProbe}, a measuring aid).
     */
    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("terrain").then(Commands.literal("waving")
                .then(Commands.literal("on").executes(c -> toggle(true)))
                .then(Commands.literal("off").executes(c -> toggle(false)))
                .then(Commands.literal("status").executes(c -> status()))
                // measuring aid: 0 normal, 1 shader runs but finds no wind (exits early), 2 full shader, amplitude 0,
                // 3 plants still (leaves wave on)
                .then(Commands.literal("debug").then(Commands.argument("mode", IntegerArgumentType.integer(0, 3))
                        .executes(c -> debug(IntegerArgumentType.getInteger(c, "mode")))))
                // measuring aid: log the shader's offsets frame by frame (SwayProbe)
                .then(Commands.literal("probe")
                        .then(Commands.literal("frames").then(Commands.argument("n", IntegerArgumentType.integer(1, 600))
                                .executes(c -> probe(() -> SwayProbe.frames(IntegerArgumentType.getInteger(c, "n"))))))
                        .then(Commands.literal("seconds").then(Commands.argument("s", IntegerArgumentType.integer(1, 600))
                                .executes(c -> probe(() -> SwayProbe.seconds(IntegerArgumentType.getInteger(c, "s"))))))
                        .then(Commands.literal("turn").executes(c -> probe(SwayProbe::turn)))
                        .then(Commands.literal("stop").executes(c -> probe(SwayProbe::stop))))
                .then(Commands.literal("plants").then(Commands.argument("air", FloatArgumentType.floatArg(0F, 0.5F))
                        .then(Commands.argument("water", FloatArgumentType.floatArg(0F, 0.5F))
                                .then(Commands.argument("sheen", FloatArgumentType.floatArg(0F, 0.5F))
                                        .then(Commands.argument("push", FloatArgumentType.floatArg(0F, 1F)).executes(c -> plants(
                                                FloatArgumentType.getFloat(c, "air"), FloatArgumentType.getFloat(c, "water"),
                                                FloatArgumentType.getFloat(c, "sheen"), FloatArgumentType.getFloat(c, "push")))))))))));
    }

    private static int probe(Runnable action) {
        action.run();
        Awaken.LOG.info("Sway probe: {}", SwayProbe.status());
        return Command.SINGLE_SUCCESS;
    }

    private static int plants(float air, float water, float sheen, float push) {
        WindTexels.plantAmplitude = air;
        WindTexels.waterAmplitude = water;
        WindTexels.sheen = sheen;
        WindTexels.push = push;
        Awaken.LOG.info("Waving plants: air {}, water {}, sheen {}, push {}", air, water, sheen, push);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggle(boolean on) {
        TerrainClientConfig.setWaving(on);
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
        String line = "Waving leaves: setting " + (TerrainClientConfig.waving() ? "on" : "off")
                + (reason == null ? ", Sodium supported" : ", unavailable: " + reason)
                + ", shader pack " + (packOffered ? "on" : "off") + ", " + markedStates + " leaf states and " + plantStates
                + " plant states marked, wind texels "
                + (WindTexels.cornerFree() ? "free" : "blocked") + (Companions.shaderPackActive() ? ", Iris shaderpack on" : "");
        Awaken.LOG.info(line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
