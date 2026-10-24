package dev.awaken.turbo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreSubmitInfo;
import org.lwjgl.vulkan.VkSemaphoreTypeCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo2;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/**
 * Turbo's compute bridge (SPEC §16): GLSL compute shaders run on Minecraft's own Vulkan device, writing
 * {@link TurboBuffer}s that normal RenderPipelines draw from (vertex, index, indirect). Get it from
 * {@link Turbo#compute()}, which is null whenever Turbo is off; everything here is render thread only.
 * <ul>
 *   <li>{@link #frame()}: commands recorded into this frame's own command stream, ahead of every draw recorded after
 *       it; the draws see the results (same frame, no waiting).</li>
 *   <li>{@link #async()}: commands on the compute queue, signalling a timeline value (the ticket).
 *       {@link #awaitOnGpu} makes the rest of this frame wait for it on the GPU; {@link #done} polls it.</li>
 * </ul>
 * Programs bind storage buffers 0..n-1 (set 0, push descriptors) and up to 128 bytes of push constants.
 * Background passes must not write a buffer the frames are drawing from: double-buffer and swap after the ticket.
 */
public final class TurboCompute {
    final TurboDevice device;
    private final long timeline;
    private final long asyncPool;
    private long lastTicket;
    private long completed;
    private final ArrayDeque<InFlight> inFlight = new ArrayDeque<>();
    private final ArrayDeque<VkCommandBuffer> spare = new ArrayDeque<>();
    private final List<TurboBuffer> waiting = new ArrayList<>();

    private record InFlight(long ticket, VkCommandBuffer cb) {}

    TurboCompute(TurboDevice device) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreTypeCreateInfo type = VkSemaphoreTypeCreateInfo.calloc(stack).sType$Default()
                    .semaphoreType(VK12.VK_SEMAPHORE_TYPE_TIMELINE).initialValue(0L);
            LongBuffer out = stack.callocLong(1);
            check(VK12.vkCreateSemaphore(device.vkDevice, VkSemaphoreCreateInfo.calloc(stack).sType$Default().pNext(type), null, out), "vkCreateSemaphore");
            this.timeline = out.get(0);
            VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK12.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT | VK12.VK_COMMAND_POOL_CREATE_TRANSIENT_BIT)
                    .queueFamilyIndex(device.asyncQueue.queueFamilyIndex());
            check(VK12.vkCreateCommandPool(device.vkDevice, pool, null, out), "vkCreateCommandPool");
            this.asyncPool = out.get(0);
        }
    }

    // ---------------------------------------------------------------------------------------------- programs, buffers

    /**
     * Compiles a GLSL 450 compute shader (local size in the source) into a pipeline with {@code storageBuffers}
     * bindings in set 0 and {@code pushBytes} of push constants (0..128, a multiple of 4).
     */
    public Program program(String name, String glsl, int storageBuffers, int pushBytes) {
        if (pushBytes < 0 || pushBytes > 128 || pushBytes % 4 != 0) throw new IllegalArgumentException("pushBytes " + pushBytes);
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        long result = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_performance);
            result = Shaderc.shaderc_compile_into_spv(compiler, glsl, Shaderc.shaderc_compute_shader, name, "main", options);
            if (result == 0L || Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw new IllegalArgumentException("Turbo compute shader " + name + ": " + (result == 0L ? "no result" : Shaderc.shaderc_result_get_error_message(result)));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            LongBuffer out = stack.callocLong(1);
            check(VK12.vkCreateShaderModule(device.vkDevice, VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv), null, out), "vkCreateShaderModule");
            long module = out.get(0);
            try {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(Math.max(storageBuffers, 1), stack);
                for (int i = 0; i < storageBuffers; i++) {
                    bindings.get(i).binding(i).descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK12.VK_SHADER_STAGE_COMPUTE_BIT);
                }
                bindings.limit(storageBuffers);
                check(VK12.vkCreateDescriptorSetLayout(device.vkDevice, VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default()
                        .flags(KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR).pBindings(bindings), null, out), "vkCreateDescriptorSetLayout");
                long setLayout = out.get(0);
                VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(setLayout));
                if (pushBytes > 0) {
                    layoutInfo.pPushConstantRanges(VkPushConstantRange.calloc(1, stack).stageFlags(VK12.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushBytes));
                }
                check(VK12.vkCreatePipelineLayout(device.vkDevice, layoutInfo, null, out), "vkCreatePipelineLayout");
                long layout = out.get(0);
                VkComputePipelineCreateInfo.Buffer info = VkComputePipelineCreateInfo.calloc(1, stack).sType$Default().layout(layout);
                info.stage().sType$Default().stage(VK12.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
                check(VK12.vkCreateComputePipelines(device.vkDevice, PipelineCacheStore.current(), info, null, out), "vkCreateComputePipelines " + name);
                return new Program(this, name, out.get(0), layout, setLayout, storageBuffers, pushBytes);
            } finally {
                VK12.vkDestroyShaderModule(device.vkDevice, module, null);
            }
        } finally {
            if (result != 0L) Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    /**
     * A buffer compute writes and Minecraft reads. {@code usage} takes {@link GpuBuffer} bits: VERTEX, INDEX and
     * INDIRECT_PARAMETERS for drawing; MAP_READ / MAP_WRITE for CPU access (host-visible memory). Copies both ways and
     * storage access are always included.
     */
    public TurboBuffer buffer(String label, @GpuBuffer.Usage int usage, long size) {
        RenderSystem.assertOnRenderThread();
        return TurboBuffer.create(this, label, usage, size);
    }

    /** Commands for this frame, run before every draw recorded after {@link Pass#close()}. Not inside a render pass. */
    public Pass frame() {
        RenderSystem.assertOnRenderThread();
        if (device.frontend.isInRenderPass()) throw new IllegalStateException("Turbo frame pass inside a render pass");
        VkCommandBuffer cb = device.encoder.allocateAndBeginTransientCommandBuffer();
        // everything already recorded this frame (and the frames before) is done with these buffers first
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanCommandEncoder.memoryBarrier(cb, stack);
        }
        return new Pass(this, cb, false);
    }

    /** Commands on the compute queue; {@link Pass#close()} returns their ticket. */
    public Pass async() {
        RenderSystem.assertOnRenderThread();
        recycle();
        VkCommandBuffer cb = spare.poll();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (cb == null) {
                PointerBuffer p = stack.callocPointer(1);
                check(VK12.vkAllocateCommandBuffers(device.vkDevice, VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                        .commandPool(asyncPool).level(VK12.VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1), p), "vkAllocateCommandBuffers");
                cb = new VkCommandBuffer(p.get(0), device.vkDevice);
            } else {
                VK12.vkResetCommandBuffer(cb, 0);
            }
            check(VK12.vkBeginCommandBuffer(cb, VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK12.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer");
            VulkanCommandEncoder.memoryBarrier(cb, stack);
        }
        return new Pass(this, cb, true);
    }

    /** True once the GPU has finished the async pass with this ticket. */
    public boolean done(long ticket) {
        if (ticket <= completed) return true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer v = stack.callocLong(1);
            if (VK12.vkGetSemaphoreCounterValue(device.vkDevice, timeline, v) == VK12.VK_SUCCESS) completed = v.get(0);
        }
        return ticket <= completed;
    }

    /** The rest of this frame's GPU work (from the next recorded command on) waits for this ticket. */
    public void awaitOnGpu(long ticket) {
        RenderSystem.assertOnRenderThread();
        if (ticket > lastTicket) throw new IllegalArgumentException("ticket " + ticket + " was never submitted");
        if (done(ticket)) return;
        device.encoder.waitSemaphore(timeline, ticket, KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR);
    }

    /** Blocks the CPU until the ticket is done or the time runs out; true when done. For tests and the probe. */
    public boolean awaitOnCpu(long ticket, long timeoutNanos) {
        if (done(ticket)) return true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int r = VK12.vkWaitSemaphores(device.vkDevice, VkSemaphoreWaitInfo.calloc(stack).sType$Default()
                    .semaphoreCount(1).pSemaphores(stack.longs(timeline)).pValues(stack.longs(ticket)), timeoutNanos);
            return r == VK12.VK_SUCCESS && done(ticket);
        }
    }

    /** Per frame: recycle finished async command buffers and free retired buffers whose last ticket is done. */
    void tick() {
        recycle();
        for (int i = waiting.size() - 1; i >= 0; i--) {
            TurboBuffer b = waiting.get(i);
            if (done(b.lastTicket)) {
                waiting.remove(i);
                device.encoder.queueForDestroy(b);
            }
        }
    }

    void retire(TurboBuffer b) {
        if (b.lastTicket == 0L || done(b.lastTicket)) device.encoder.queueForDestroy(b);
        else waiting.add(b);
    }

    private void recycle() {
        while (!inFlight.isEmpty() && done(inFlight.peek().ticket())) spare.add(inFlight.poll().cb());
    }

    /** Waits for every async pass (up to a second); for shutdown and program teardown. */
    void drain() {
        if (lastTicket > 0) awaitOnCpu(lastTicket, 1_000_000_000L);
    }

    private long submitAsync(VkCommandBuffer cb) {
        long ticket = ++lastTicket;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo2.Buffer submit = VkSubmitInfo2.calloc(1, stack).sType$Default()
                    .pCommandBufferInfos(VkCommandBufferSubmitInfo.calloc(1, stack).sType$Default().commandBuffer(cb))
                    .pSignalSemaphoreInfos(VkSemaphoreSubmitInfo.calloc(1, stack).sType$Default()
                            .semaphore(timeline).value(ticket).stageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR));
            check(KHRSynchronization2.vkQueueSubmit2KHR(device.asyncQueue.vkQueue(), submit, 0L), "vkQueueSubmit2KHR");
        }
        inFlight.add(new InFlight(ticket, cb));
        return ticket;
    }

    static void check(int result, String what) {
        if (result != VK12.VK_SUCCESS) throw new IllegalStateException(what + " returned " + result);
    }

    private static void barrier(VkCommandBuffer cb, long srcStage, long srcAccess, long dstStage, long dstAccess) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer b = VkMemoryBarrier2.calloc(1, stack).sType$Default()
                    .srcStageMask(srcStage).srcAccessMask(srcAccess).dstStageMask(dstStage).dstAccessMask(dstAccess);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(cb, VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(b));
        }
    }

    // --------------------------------------------------------------------------------------------------------- types

    /** A compiled compute shader. Close it when done (the GPU objects go once frames stop using them). */
    public static final class Program implements AutoCloseable {
        private final TurboCompute owner;
        public final String name;
        final long pipeline, layout, setLayout;
        public final int storageBuffers, pushBytes;
        private boolean closed;

        Program(TurboCompute owner, String name, long pipeline, long layout, long setLayout, int storageBuffers, int pushBytes) {
            this.owner = owner;
            this.name = name;
            this.pipeline = pipeline;
            this.layout = layout;
            this.setLayout = setLayout;
            this.storageBuffers = storageBuffers;
            this.pushBytes = pushBytes;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            owner.drain();
            var d = owner.device.vkDevice;
            owner.device.encoder.queueForDestroy(() -> {
                VK12.vkDestroyPipeline(d, pipeline, null);
                VK12.vkDestroyPipelineLayout(d, layout, null);
                VK12.vkDestroyDescriptorSetLayout(d, setLayout, null);
            });
        }
    }

    /** A run of compute commands. Each command waits for the one before it; close() hands them to the GPU. */
    public static final class Pass implements AutoCloseable {
        private static final long WORK = KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR | KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT_KHR;
        private static final long WORK_ACCESS = KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR
                | KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR;
        private final TurboCompute owner;
        private final VkCommandBuffer cb;
        private final boolean async;
        private boolean first = true;
        private boolean closed;
        private long ticket;
        private final List<TurboBuffer> used = new ArrayList<>(4);

        Pass(TurboCompute owner, VkCommandBuffer cb, boolean async) {
            this.owner = owner;
            this.cb = cb;
            this.async = async;
        }

        /** The raw command buffer, for Turbo features that record their own commands (timestamps). */
        VkCommandBuffer commandBuffer() {
            return cb;
        }

        /** Writes up to 64 KB inline (a multiple of 4 bytes at a multiple of 4). */
        public Pass update(TurboBuffer b, long offset, ByteBuffer data) {
            step(b);
            VK12.vkCmdUpdateBuffer(cb, b.vkBuffer(), offset, data);
            return this;
        }

        /** Fills {@code size} bytes (a multiple of 4) with a repeated 32-bit value. */
        public Pass fill(TurboBuffer b, long offset, long size, int value) {
            step(b);
            VK12.vkCmdFillBuffer(cb, b.vkBuffer(), offset, size, value);
            return this;
        }

        /** Runs the program over {@code x * y * z} workgroups with buffers bound to 0..n-1 in order. */
        public Pass dispatch(Program p, int x, int y, int z, @Nullable ByteBuffer push, TurboBuffer... buffers) {
            if (buffers.length != p.storageBuffers) throw new IllegalArgumentException(p.name + " takes " + p.storageBuffers + " buffers, got " + buffers.length);
            if ((push == null ? 0 : push.remaining()) != p.pushBytes) throw new IllegalArgumentException(p.name + " takes " + p.pushBytes + " push bytes");
            step(buffers);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VK12.vkCmdBindPipeline(cb, VK12.VK_PIPELINE_BIND_POINT_COMPUTE, p.pipeline);
                if (buffers.length > 0) {
                    VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(buffers.length, stack);
                    for (int i = 0; i < buffers.length; i++) {
                        writes.get(i).sType$Default().dstBinding(i).descriptorCount(1).descriptorType(VK12.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                                .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(buffers[i].vkBuffer()).offset(0).range(VK12.VK_WHOLE_SIZE));
                    }
                    KHRPushDescriptor.vkCmdPushDescriptorSetKHR(cb, VK12.VK_PIPELINE_BIND_POINT_COMPUTE, p.layout, 0, writes);
                }
                if (push != null) VK12.vkCmdPushConstants(cb, p.layout, VK12.VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
                VK12.vkCmdDispatch(cb, x, y, z);
            }
            return this;
        }

        /** Async passes: the ticket this pass signals (0 until closed, always 0 for frame passes). */
        public long ticket() {
            return ticket;
        }

        /** Hands the commands to the GPU: into this frame's stream, or onto the compute queue (returns the ticket). */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (async) {
                // results visible to the frames that wait on the ticket, and to the CPU (readbacks)
                barrier(cb, WORK, KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                        KHRSynchronization2.VK_PIPELINE_STAGE_2_HOST_BIT_KHR | KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                        KHRSynchronization2.VK_ACCESS_2_HOST_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR);
                check(VK12.vkEndCommandBuffer(cb), "vkEndCommandBuffer");
                ticket = owner.submitAsync(cb);
                for (TurboBuffer b : used) b.lastTicket = ticket;
            } else {
                // results visible to every later command of the frame: vertex/index fetch, indirect args, shaders, copies
                barrier(cb, WORK, KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                        KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR);
                check(VK12.vkEndCommandBuffer(cb), "vkEndCommandBuffer");
                owner.device.encoder.execute(cb);
            }
        }

        /** Before each command: the command before it has finished with the buffers (one barrier per command). */
        private void step(TurboBuffer... buffers) {
            if (closed) throw new IllegalStateException("pass closed");
            for (TurboBuffer b : buffers) {
                if (b.isClosed()) throw new IllegalStateException("buffer " + b.label() + " is closed");
                if (!used.contains(b)) used.add(b);
            }
            if (!first) barrier(cb, WORK, WORK_ACCESS, WORK, WORK_ACCESS);
            first = false;
        }
    }
}
