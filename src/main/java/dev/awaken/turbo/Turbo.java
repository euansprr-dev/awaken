package dev.awaken.turbo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.shaderc.Shaderc;

/**
 * Turbo: Awaken's door to Minecraft's Vulkan internals, under the Turbo contract (CLAUDE.md, SPEC §19). Client only.
 * <p>The probe runs once, on the render thread at the first frame. Turbo turns on only when the master switch is on,
 * this is a verified Minecraft and NeoForge build ({@link TurboVersions#VERIFIED}), the backend is Vulkan, and a
 * self-test passes (a compute shader fills a buffer that is read back and checked). Otherwise it logs one
 * {@code [turbo] off: <why>} line and every feature stays out of the way: the game runs exactly as without Turbo.
 * <p>For other modules: {@link #compute()} (null when off), {@link #on()}, {@link #why()},
 * {@link #deviceExtensions()} and {@link #enabledDeviceExtensions()}; for Turbo features that drive Vulkan themselves,
 * {@link #vulkanDevice()} and {@link #vkImage}.
 */
public final class Turbo {
    private Turbo() {}

    private static boolean probed;
    private static boolean on;
    private static String why = "not probed yet";
    private static @Nullable TurboDevice device;
    private static @Nullable TurboCompute compute;
    private static boolean titleSaved, sawOverlay;
    private static int worldFrames;

    public static void init(IEventBus modBus) {
        TurboDemo.init(modBus);
        TurboBench.init();
        NeoForge.EVENT_BUS.addListener(Turbo::frameStart);
        NeoForge.EVENT_BUS.addListener(Turbo::frameEnd);
        NeoForge.EVENT_BUS.addListener(Turbo::stopping);
        NeoForge.EVENT_BUS.addListener(Turbo::commands);
    }

    // ------------------------------------------------------------------------------------------------------- API

    /** True when the probe passed and the compute bridge is ready. */
    public static boolean on() {
        return on;
    }

    /** "on" or why Turbo is off (backend, version, switch, self-test). */
    public static String why() {
        return on ? "on" : why;
    }

    /** The compute bridge, or null when Turbo is off. Render thread; runs the probe if it hasn't run yet. */
    public static @Nullable TurboCompute compute() {
        if (!probed) probe();
        return compute;
    }

    /** Every extension the Vulkan device offers (MoltenVK's list on a Mac); empty unless on a verified Vulkan game. */
    public static Set<String> deviceExtensions() {
        TurboDevice d = device;
        return d == null ? Set.of() : d.available;
    }

    /** The extensions Minecraft enabled on its device (what Turbo may actually use without asking for more). */
    public static Set<String> enabledDeviceExtensions() {
        TurboDevice d = device;
        return d == null ? Set.of() : d.enabled;
    }

    /**
     * Minecraft's Vulkan device for Turbo features that drive Vulkan themselves (Pixels): null unless this is a verified
     * build on the Vulkan backend. Render thread; runs the probe if it hasn't run yet.
     */
    public static @Nullable VulkanDevice vulkanDevice() {
        if (!probed) probe();
        TurboDevice d = device;
        return d == null ? null : d.vulkan;
    }

    /** The VkImage behind a texture on the Vulkan backend, else 0. */
    public static long vkImage(GpuTexture texture) {
        return texture instanceof VulkanGpuTexture v ? v.vkImage() : 0L;
    }

    /** True when Minecraft runs on its Vulkan backend (worker-thread safe; false before the device exists). */
    public static boolean isVulkan() {
        GpuDevice d = RenderSystem.tryGetDevice();
        return d instanceof FrontendGpuDevice f && f.backend instanceof VulkanDevice;
    }

    /** The GlslCompiler mixin's hook: the performance level on Vulkan, the game's own level otherwise. */
    public static int shaderOptimisationLevel(int level) {
        return isVulkan() ? Shaderc.shaderc_optimization_level_performance : level;
    }

    // ------------------------------------------------------------------------------------------------------ probe

    static synchronized void probe() {
        if (probed) return;
        probed = true;
        String versions = TurboVersions.problem();
        if (!TurboSwitches.on(TurboSwitches.Feature.TURBO)) {
            off("switched off (" + TurboSwitches.describe() + ")");
            return;
        }
        if (versions != null) {
            off(versions);
            return;
        }
        GpuDevice gpu = RenderSystem.tryGetDevice();
        if (!(gpu instanceof FrontendGpuDevice f) || !(f.backend instanceof VulkanDevice vk)) {
            off("the backend is " + (gpu == null ? "not up yet" : gpu.getDeviceInfo().backendName()) + ", not Vulkan");
            return;
        }
        if (!(gpu.createCommandEncoder() instanceof FrontendCommandEncoder fe) || !(fe.backend() instanceof VulkanCommandEncoder ve)) {
            off("the command encoder is not Vulkan's");
            return;
        }
        TurboDevice d;
        try {
            d = new TurboDevice(vk, fe, ve);
        } catch (RuntimeException | LinkageError e) {
            off("can't read the Vulkan device: " + e);
            return;
        }
        device = d;
        logExtensions(d);
        if (!TurboSwitches.on(TurboSwitches.Feature.COMPUTE)) {
            off("compute switched off");
            return;
        }
        TurboCompute c = null;
        try {
            c = new TurboCompute(d);
            long t0 = System.nanoTime();
            String failed = selfTest(c);
            if (failed != null) {
                off("self-test failed: " + failed);
                return;
            }
            compute = c;
            on = true;
            TurboVersions.LOG.info("[turbo] on: Vulkan on {} ({}), {}; self-test {} µs; background queue {} (family {}); {}",
                    vk.getDeviceInfo().name(), vk.getDeviceInfo().driverInfo(), TurboVersions.describe(), (System.nanoTime() - t0) / 1000,
                    d.asyncIsGraphics ? "is the graphics queue" : "is its own queue", d.asyncQueue.queueFamilyIndex(), TurboSwitches.describe());
        } catch (RuntimeException | LinkageError e) {
            off("self-test threw " + e);
        }
    }

    /** Fills 256 values on the GPU (async queue), waits for them on the CPU and checks every one. Null when fine. */
    private static @Nullable String selfTest(TurboCompute c) {
        int count = 256;
        int seed = (int) System.nanoTime() | 1;
        try (TurboCompute.Program p = c.program("awaken:turbo/selftest.comp", source("selftest.comp"), 1, 8)) {
            TurboBuffer out = c.buffer("turbo selftest", GpuBuffer.USAGE_MAP_READ, 4L * count);
            try {
                ByteBuffer push = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).putInt(seed).putInt(count).flip();
                TurboCompute.Pass pass = c.async();
                pass.dispatch(p, count / 64, 1, 1, push, out);
                pass.close();
                if (!c.awaitOnCpu(pass.ticket(), 2_000_000_000L)) return "the GPU didn't finish in 2 s";
                try (GpuBufferSlice.MappedView view = out.map(0, 4L * count, true, false)) {
                    ByteBuffer data = view.data().order(ByteOrder.nativeOrder());
                    for (int i = 0; i < count; i++) {
                        int want = (i * (int) 2654435761L) ^ seed;
                        if (data.getInt(i * 4) != want) return "value " + i + " is " + data.getInt(i * 4) + ", expected " + want;
                    }
                }
            } finally {
                out.close();
            }
        }
        return null;
    }

    private static void off(String reason) {
        on = false;
        why = reason;
        TurboVersions.LOG.info("[turbo] off: {}", reason);
    }

    private static void logExtensions(TurboDevice d) {
        Set<String> notEnabled = new TreeSet<>(d.available);
        notEnabled.removeAll(d.enabled);
        TurboVersions.LOG.info("[turbo] device extensions: {} available, {} enabled by Minecraft; VK_EXT_metal_objects {}; enabled: {}; available only: {}",
                d.available.size(), d.enabled.size(),
                d.enabled.contains("VK_EXT_metal_objects") ? "enabled" : d.available.contains("VK_EXT_metal_objects") ? "available, not enabled" : "absent",
                String.join(" ", d.enabled), String.join(" ", notEnabled));
    }

    /** A Turbo shader's GLSL from assets/awaken/turbo/ (read from the jar, never from resource packs). */
    static String source(String file) {
        try (InputStream in = Turbo.class.getResourceAsStream("/assets/awaken/turbo/" + file)) {
            if (in == null) throw new IllegalStateException("missing assets/awaken/turbo/" + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("can't read assets/awaken/turbo/" + file, e);
        }
    }

    // ----------------------------------------------------------------------------------------------------- events

    private static void frameStart(RenderFrameEvent.Pre e) {
        if (!probed) probe();
        TurboCompute c = compute;
        if (c == null) return;
        try {
            c.tick();
        } catch (RuntimeException | LinkageError ex) {
            // the contract: Turbo never takes the game down; it switches itself off instead
            compute = null;
            off("compute failed: " + ex);
            TurboVersions.LOG.error("[turbo] compute stopped", ex);
        }
    }

    /** Saves the pipeline cache once loading is over (the loading overlay gone) and 3,600 frames into a world. */
    private static void frameEnd(RenderFrameEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() != null) sawOverlay = true;
        if (!titleSaved && sawOverlay && mc.gui.overlay() == null) {
            titleSaved = true;
            PipelineCacheStore.save("loaded");
        }
        if (mc.level != null && worldFrames >= 0 && ++worldFrames > 3600) {
            worldFrames = -1;
            PipelineCacheStore.save("in world");
        }
    }

    private static void stopping(ClientStoppingEvent e) {
        PipelineCacheStore.save("game closing");
        TurboCompute c = compute;
        try {
            if (c != null) c.drain();
        } catch (RuntimeException | LinkageError ex) {
            TurboVersions.LOG.warn("[turbo] couldn't wait for background work at shutdown: {}", ex.toString());
        }
    }

    /** {@code /awaken turbo status | demo on|off} (client side). */
    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("awaken").then(Commands.literal("turbo")
                .then(Commands.literal("status").executes(c -> say("Turbo: " + why() + ". " + TurboVersions.describe() + "; "
                        + TurboSwitches.describe() + ". Pipeline cache: " + PipelineCacheStore.status() + ". Demo: " + TurboDemo.status())))
                .then(Commands.literal("demo")
                        .then(Commands.literal("on").executes(c -> say(TurboDemo.start())))
                        .then(Commands.literal("off").executes(c -> say(TurboDemo.stop()))))));
    }

    private static int say(String line) {
        TurboVersions.LOG.info("[turbo] {}", line);
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        return Command.SINGLE_SUCCESS;
    }
}
