package dev.awaken.turbo;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;

/**
 * A storage buffer compute shaders write and Minecraft draws from. It is a real {@link VulkanGpuBuffer}, so a normal
 * RenderPass takes it as a vertex buffer, an index buffer or indirect draw commands, and the CommandEncoder can write
 * to or copy it. Made only by {@link TurboCompute#buffer}; closing it frees the memory once the GPU is done with it.
 */
public final class TurboBuffer extends VulkanGpuBuffer {
    private final TurboCompute owner;
    private final long allocation;
    private final String label;
    private boolean closed;
    private int mappings;
    /** The async submission that last used this buffer: its memory waits for that to finish before it is freed. */
    long lastTicket;

    private TurboBuffer(TurboCompute owner, long vkBuffer, long allocation, int usage, long size, String label) {
        super(vkBuffer, usage, size);
        this.owner = owner;
        this.allocation = allocation;
        this.label = label;
    }

    static TurboBuffer create(TurboCompute owner, String label, @GpuBuffer.Usage int usage, long size) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        // what Minecraft sees: the asked-for uses plus copies both ways, so writeToBuffer and copyToBuffer accept it
        int mcUsage = usage | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(size)
                    .usage(VulkanConst.bufferUsageToVk(mcUsage) | VK12.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
            int[] families = owner.device.sharedFamilies();
            if (families.length > 1) {
                // the compute queue is another family: share instead of handing ownership back and forth
                IntBuffer f = stack.mallocInt(families.length).put(families).flip();
                info.sharingMode(VK12.VK_SHARING_MODE_CONCURRENT).pQueueFamilyIndices(f);
            } else {
                info.sharingMode(VK12.VK_SHARING_MODE_EXCLUSIVE);
            }
            VmaAllocationCreateInfo alloc = VmaAllocationCreateInfo.calloc(stack);
            if ((usage & GpuBuffer.USAGE_MAP_READ) != 0) {
                alloc.usage(Vma.VMA_MEMORY_USAGE_AUTO).flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT);
            } else if ((usage & GpuBuffer.USAGE_MAP_WRITE) != 0) {
                alloc.usage(Vma.VMA_MEMORY_USAGE_AUTO).flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
            } else {
                alloc.usage(Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            }
            LongBuffer buf = stack.callocLong(1);
            PointerBuffer mem = stack.callocPointer(1);
            int r = Vma.vmaCreateBuffer(owner.device.vma(), info, alloc, buf, mem, null);
            if (r != VK12.VK_SUCCESS) throw new IllegalStateException("vmaCreateBuffer " + label + " returned " + r);
            return new TurboBuffer(owner, buf.get(0), mem.get(0), mcUsage, size, label);
        }
    }

    public String label() {
        return this.label;
    }

    @Override
    public void destroy() {
        Vma.vmaDestroyBuffer(this.owner.device.vma(), this.vkBuffer(), this.allocation);
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        if (this.closed) return;
        if (this.mappings != 0) throw new IllegalStateException("Attempt to close a mapped buffer");
        this.closed = true;
        this.owner.retire(this);
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        if (this.closed) throw new IllegalStateException("Buffer already closed");
        if (read && (this.usage() & GpuBuffer.USAGE_MAP_READ) == 0) throw new IllegalStateException("Buffer is not readable");
        if (write && (this.usage() & GpuBuffer.USAGE_MAP_WRITE) == 0) throw new IllegalStateException("Buffer is not writable");
        if (!read && !write) throw new IllegalArgumentException("At least read or write must be true");
        if (offset < 0 || length < 0 || offset + length > this.size() || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Mapping " + length + " bytes at " + offset + " of a " + this.size() + " byte buffer");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer p = stack.callocPointer(1);
            int r = Vma.vmaMapMemory(this.owner.device.vma(), this.allocation, p);
            if (r != VK12.VK_SUCCESS) throw new IllegalStateException("vmaMapMemory returned " + r);
            if (read) Vma.vmaInvalidateAllocation(this.owner.device.vma(), this.allocation, offset, length);
            this.mappings++;
            ByteBuffer bytes = MemoryUtil.memByteBuffer(p.get(0) + offset, (int) length);
            return new GpuBufferSlice.MappedView(this.slice(offset, length), bytes, new Runnable() {
                private boolean done;

                @Override
                public void run() {
                    if (this.done) return;
                    this.done = true;
                    if (write) Vma.vmaFlushAllocation(TurboBuffer.this.owner.device.vma(), TurboBuffer.this.allocation, offset, length);
                    TurboBuffer.this.mappings--;
                    Vma.vmaUnmapMemory(TurboBuffer.this.owner.device.vma(), TurboBuffer.this.allocation);
                }
            });
        }
    }
}
