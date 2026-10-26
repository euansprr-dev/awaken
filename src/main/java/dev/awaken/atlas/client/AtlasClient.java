package dev.awaken.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.awaken.Awaken;
import dev.awaken.atlas.AtlasNet;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.List;

/**
 * The Atlas, client half (SPEC §16.8): M opens the table; columns are sampled as chunks arrive and kept in the disk
 * cache; the server's explored tiles land in {@code Discovery.CLIENT}; the mesh is kept warm round the player while
 * the table is shut. Dev commands under {@code /atlas} (client side, so they never shadow the server's
 * {@code /awaken}).
 */
public final class AtlasClient {
    private AtlasClient() {}

    /** M: the key Xaero's World Map used, now the Atlas's. */
    public static final KeyMapping KEY = new KeyMapping("key.awaken.atlas", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_M, AwakenKeys.CATEGORY);

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterRenderPipelinesEvent.class, AtlasPipelines::register);
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, e -> e.register(AtlasRenderState.class, AtlasRenderer::new));
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(KEY));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(AtlasNet.Sync.TYPE, (m, ctx) -> AtlasState.get().onSync(m));
            e.register(AtlasNet.Trail.TYPE, (m, ctx) -> AtlasState.get().onTrail(m));
            e.register(AtlasNet.Regions.TYPE, (m, ctx) -> AtlasState.get().onRegions(m.regions()));
            e.register(AtlasNet.City.TYPE, (m, ctx) -> AtlasState.get().onCity(m));
            e.register(AtlasNet.Pins.TYPE, (m, ctx) -> AtlasState.get().pins = List.copyOf(m.pins()));
        });
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, AtlasClient::commands);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
            // the integrated server fires this too, on its own thread: only the client's chunks are ours
            if (e.getLevel().isClientSide()) AtlasState.get().onChunkLoad(e.getChunk());
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre.class, e -> AtlasGlance.frame());
        AtlasGlance.register();
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> AtlasState.get().leave());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.gui.screen() instanceof AtlasScreen;
        AtlasState.get().tick(mc, open);
        while (KEY.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null) AtlasScreen.open(20f, 52f, 1f);
        }
    }

    // ---- dev commands -----------------------------------------------------------------------------------------------------

    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("atlas")
                .then(Commands.literal("open")
                        .executes(c -> open(20f, 52f, 1f))
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                .then(Commands.argument("tilt", FloatArgumentType.floatArg(20f, 89f))
                                        .then(Commands.argument("zoom", FloatArgumentType.floatArg(0.02f, 20f))
                                                .executes(c -> open(f(c, "yaw"), f(c, "tilt"), f(c, "zoom")))))))
                .then(Commands.literal("view")
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                .then(Commands.argument("tilt", FloatArgumentType.floatArg(20f, 89f))
                                        .then(Commands.argument("zoom", FloatArgumentType.floatArg(0.02f, 20f))
                                                .executes(c -> view(f(c, "yaw"), f(c, "tilt"), f(c, "zoom")))))))
                .then(Commands.literal("spin").then(Commands.argument("degPerSecond", FloatArgumentType.floatArg()).executes(c -> {
                    if (screen() != null) screen().spin = f(c, "degPerSecond");
                    return 1;
                })))
                .then(Commands.literal("freeze").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    if (screen() != null) screen().frozen = StringArgumentType.getString(c, "on").equals("on");
                    return 1;
                })))
                .then(Commands.literal("reveal").executes(c -> reveal()))
                .then(Commands.literal("prof").then(Commands.argument("bits", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 127)).executes(c -> {
                    AtlasScreen.prof = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "bits");
                    AtlasState.stats.reset();
                    return 1;
                })))
                .then(Commands.literal("drift").then(Commands.argument("hz", FloatArgumentType.floatArg(1f, 240f)).executes(c -> {
                    AtlasRenderer.DRIFT_NANOS = (long) (1e9 / f(c, "hz"));
                    return 1;
                })))
                .then(Commands.literal("stats").executes(c -> {
                    for (String line : AtlasState.describe()) Awaken.LOG.info("[atlas] stats: {}", line);
                    AtlasState.stats.reset();
                    Awaken.LOG.info("[atlas] stats: glance paint {} ms", String.format(java.util.Locale.ROOT, "%.2f", AtlasGlance.paintNanos / 1e6));
                    return 1;
                })));
    }

    private static float f(CommandContext<CommandSourceStack> c, String name) {
        return FloatArgumentType.getFloat(c, name);
    }

    private static int open(float yaw, float tilt, float zoom) {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> { // after the chat screen closes
            if (mc.gui.screen() instanceof AtlasScreen s) s.onClose();
            AtlasScreen.open(yaw, tilt, zoom);
        });
        return 1;
    }

    private static int view(float yaw, float tilt, float zoom) {
        AtlasScreen s = screen();
        if (s == null) return 0;
        s.yaw = yaw;
        s.tilt = tilt;
        s.zoom = (s.width - 24) / AtlasScreen.OPEN_SPAN * zoom;
        return 1;
    }

    /** Plays the Registry reveal on the next open; without a recorded run, a loop round where you stand. */
    private static int reveal() {
        AtlasState a = AtlasState.get();
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        if (a.trail.length < 6) {
            int n = 48;
            int[] t = new int[n * 3];
            for (int i = 0; i < n; i++) {
                double u = i / (double) (n - 1), ang = u * Math.PI * 1.4;
                t[i * 3] = (int) Math.round(p.getX() - 90 + u * 150 + Math.sin(ang * 2) * 18);
                t[i * 3 + 2] = (int) Math.round(p.getZ() + 60 - u * 120 + Math.cos(ang) * 22);
                t[i * 3 + 1] = (int) Math.round(a.store.groundY(t[i * 3], t[i * 3 + 2], p.getY()));
            }
            a.trail = t;
        }
        a.revealPending = true;
        return open(20f, 52f, 1f);
    }

    /**
     * The Registry reveal opened for you (SPEC §16.4, the Registry ceremony: right after the band's first K, as a
     * proposal): the table opens and the run you made draws itself across the city. False (nothing opens) without a
     * recorded run or with a screen up.
     */
    public static boolean openReveal() {
        Minecraft mc = Minecraft.getInstance();
        AtlasState a = AtlasState.get();
        if (mc.player == null || mc.gui.screen() != null || a.trail.length < 6) return false;
        a.revealPending = true;
        AtlasScreen.open(20f, 52f, 1f);
        return true;
    }

    /** Whether the Atlas table is the open screen. */
    public static boolean isOpen() {
        return Minecraft.getInstance().gui.screen() instanceof AtlasScreen;
    }

    private static AtlasScreen screen() {
        return Minecraft.getInstance().gui.screen() instanceof AtlasScreen s ? s : null;
    }
}
