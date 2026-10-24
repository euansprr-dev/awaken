package dev.awaken.turbo;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanQueue;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import java.nio.IntBuffer;
import java.util.Set;
import java.util.TreeSet;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtensionProperties;

/**
 * Minecraft's own Vulkan objects as Turbo sees them: the device (through the one access-transformed field), the frame
 * encoder, and the queue Turbo submits background work to. Nothing here owns GPU objects; Minecraft does.
 */
final class TurboDevice {
    final VulkanDevice vulkan;
    final VkDevice vkDevice;
    final VulkanCommandEncoder encoder;
    final FrontendCommandEncoder frontend;
    /** Where background passes go: Minecraft's compute queue, which nothing in 26.3 submits to. */
    final VulkanQueue asyncQueue;
    final int graphicsFamily;
    /** True when the compute queue is the graphics queue itself (one queue in the family). */
    final boolean asyncIsGraphics;
    final double timestampPeriod;
    /** Every extension the physical device offers, and the ones Minecraft enabled on the device. */
    final Set<String> available;
    final Set<String> enabled;

    TurboDevice(VulkanDevice vulkan, FrontendCommandEncoder frontend, VulkanCommandEncoder encoder) {
        this.vulkan = vulkan;
        this.vkDevice = vulkan.vkDevice();
        this.frontend = frontend;
        this.encoder = encoder;
        this.asyncQueue = vulkan.computeQueue();
        this.graphicsFamily = vulkan.graphicsQueue().queueFamilyIndex();
        this.asyncIsGraphics = vulkan.computeQueue().vkQueue().address() == vulkan.graphicsQueue().vkQueue().address();
        this.timestampPeriod = vulkan.getDeviceInfo().timestampPeriod();
        this.available = listExtensions(this.vkDevice);
        Set<String> on = new TreeSet<>();
        for (String s : vulkan.getDeviceInfo().underlyingExtensions()) if (s.endsWith(" (D)")) on.add(s.substring(0, s.length() - 4));
        this.enabled = on;
    }

    long vma() {
        return this.vulkan.vma();
    }

    /** The queue families a Turbo buffer is shared between: one when the compute queue is in the graphics family. */
    int[] sharedFamilies() {
        int compute = this.asyncQueue.queueFamilyIndex();
        return compute == this.graphicsFamily ? new int[] {compute} : new int[] {this.graphicsFamily, compute};
    }

    private static Set<String> listExtensions(VkDevice device) {
        Set<String> out = new TreeSet<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer n = stack.callocInt(1);
            if (VK12.vkEnumerateDeviceExtensionProperties(device.getPhysicalDevice(), (String) null, n, null) != VK12.VK_SUCCESS) return out;
            try (VkExtensionProperties.Buffer props = VkExtensionProperties.calloc(n.get(0))) {
                if (VK12.vkEnumerateDeviceExtensionProperties(device.getPhysicalDevice(), (String) null, n, props) != VK12.VK_SUCCESS) return out;
                for (VkExtensionProperties p : props) out.add(p.extensionNameString());
            }
        }
        return out;
    }
}
