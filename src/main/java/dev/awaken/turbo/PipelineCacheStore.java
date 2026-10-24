package dev.awaken.turbo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

/**
 * The persistent VkPipelineCache (switch pipelineCache). One cache for the whole game, made on the first pipeline
 * compile from {@code awaken-turbo/pipeline-cache-<mc>-<neoforge>.bin} in the game folder, saved at the title screen,
 * a few seconds into the first world and when the game stops. The file is Awaken's 16-byte header (magic, schema
 * version, data length) and then the driver's own blob, used only when its vendor, device and cache UUID match this
 * GPU. Any failure leaves the handle at 0, which is the game's own behaviour.
 * Compiles run on worker threads: they share the read lock (the cache is internally synchronized for pipeline
 * creation), saving takes the write lock so the blob is never read mid-compile.
 */
public final class PipelineCacheStore {
    private PipelineCacheStore() {}

    private static final int MAGIC = 0x41574B50; // "AWKP"
    private static final int SCHEMA_VERSION = 1;
    private static final int HEADER = 16;

    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static volatile boolean tried;
    private static volatile long cache;
    private static @Nullable VkDevice device;
    private static long savedSize = -1;
    private static long loadedBytes;
    private static int compiles;
    private static @Nullable String problem;

    /** The cache for this device, or 0 when there is none (the game's own value). Worker threads. */
    public static long handle(VkDevice vkDevice) {
        if (!tried) create(vkDevice);
        if (cache != 0L) {
            synchronized (PipelineCacheStore.class) {
                compiles++;
            }
        }
        return cache;
    }

    /** Brackets a pipeline creation that uses the cache (shared lock), so a save never reads it mid-compile. */
    public static void beginCompile() {
        LOCK.readLock().lock();
    }

    public static void endCompile() {
        LOCK.readLock().unlock();
    }

    /** The cache handle if one was made, else 0 (for Turbo's own compute pipelines). */
    static long current() {
        return cache;
    }

    static String status() {
        if (!tried) return "not used yet";
        if (cache == 0L) return "off (" + problem + ")";
        return "on, loaded " + loadedBytes / 1024 + " KB, " + compiles + " pipeline compiles through it, saved " + Math.max(savedSize, 0) / 1024 + " KB";
    }

    private static synchronized void create(VkDevice vkDevice) {
        if (tried) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            device = vkDevice;
            ByteBuffer initial = readValid(vkDevice);
            VkPipelineCacheCreateInfo info = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();
            if (initial != null) info.pInitialData(initial);
            LongBuffer out = stack.callocLong(1);
            int result = VK12.vkCreatePipelineCache(vkDevice, info, null, out);
            if (initial != null) {
                MemoryUtil.memFree(initial);
                savedSize = loadedBytes; // the file already holds this much: save again only once it grows
            }
            if (result != VK12.VK_SUCCESS) {
                problem = "vkCreatePipelineCache returned " + result;
                TurboVersions.LOG.warn("[turbo] pipeline cache off: {}", problem);
            } else {
                cache = out.get(0);
                TurboVersions.LOG.info("[turbo] pipeline cache on: {} KB from {}", loadedBytes / 1024, file());
            }
        } catch (RuntimeException | LinkageError e) {
            problem = e.toString();
            TurboVersions.LOG.warn("[turbo] pipeline cache off: {}", problem);
        } finally {
            tried = true;
        }
    }

    /** The saved blob if it exists, has our header and was made by this GPU and driver; null otherwise. */
    private static @Nullable ByteBuffer readValid(VkDevice vkDevice) {
        Path f = file();
        if (!Files.isRegularFile(f)) return null;
        try {
            byte[] bytes = Files.readAllBytes(f);
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.length < HEADER + 32 || b.getInt(0) != MAGIC || b.getInt(4) != SCHEMA_VERSION) return null;
            long len = b.getLong(8);
            if (len != bytes.length - HEADER) return null;
            // VkPipelineCacheHeaderVersionOne: headerSize, headerVersion, vendorID, deviceID, pipelineCacheUUID[16]
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
                VK12.vkGetPhysicalDeviceProperties(vkDevice.getPhysicalDevice(), props);
                if (b.getInt(HEADER + 8) != props.vendorID() || b.getInt(HEADER + 12) != props.deviceID()) return null;
                ByteBuffer uuid = props.pipelineCacheUUID();
                for (int i = 0; i < 16; i++) if (bytes[HEADER + 16 + i] != uuid.get(i)) return null;
            }
            ByteBuffer data = MemoryUtil.memAlloc((int) len);
            data.put(bytes, HEADER, (int) len).flip();
            loadedBytes = len;
            return data;
        } catch (IOException | RuntimeException e) {
            TurboVersions.LOG.warn("[turbo] pipeline cache file ignored: {}", e.toString());
            return null;
        }
    }

    /** Writes the cache if it grew since the last save. Render thread; takes a few ms for a few MB. */
    static void save(String when) {
        VkDevice d = device;
        if (cache == 0L || d == null) return;
        LOCK.writeLock().lock();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.callocPointer(1);
            if (VK12.vkGetPipelineCacheData(d, cache, size, null) != VK12.VK_SUCCESS) return;
            long n = size.get(0);
            if (n == savedSize || n <= 0) return;
            ByteBuffer data = MemoryUtil.memAlloc((int) n);
            try {
                if (VK12.vkGetPipelineCacheData(d, cache, size, data) != VK12.VK_SUCCESS) return;
                n = size.get(0);
                byte[] out = new byte[HEADER + (int) n];
                ByteBuffer b = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
                b.putInt(MAGIC).putInt(SCHEMA_VERSION).putLong(n);
                data.get(out, HEADER, (int) n);
                Path f = file();
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.write(tmp, out);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                savedSize = n;
                TurboVersions.LOG.info("[turbo] pipeline cache saved ({}): {} KB, {} compiles so far", when, n / 1024, compiles);
            } finally {
                MemoryUtil.memFree(data);
            }
        } catch (IOException | RuntimeException e) {
            TurboVersions.LOG.warn("[turbo] pipeline cache not saved: {}", e.toString());
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static Path file() {
        String v = TurboVersions.describe().replace("MC ", "").replace(", NeoForge ", "-");
        return FMLPaths.GAMEDIR.get().resolve("awaken-turbo").resolve("pipeline-cache-" + v + ".bin");
    }
}
