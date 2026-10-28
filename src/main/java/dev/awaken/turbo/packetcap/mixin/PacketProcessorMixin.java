package dev.awaken.turbo.packetcap.mixin;

import dev.awaken.turbo.packetcap.PacketCap;
import net.minecraft.network.PacketProcessor;
import net.neoforged.neoforge.network.handling.QueuedPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

/**
 * 26.3's packet loop ({@code PacketProcessor.processQueuedPackets}, read against 26.3 / NeoForge 26.3.0.95): the
 * server's packets get PacketCap's budget a tick. Not required: if it doesn't apply, the probe never fires and every
 * queued packet is handled before each tick, as vanilla's.
 */
@Mixin(PacketProcessor.class)
abstract class PacketProcessorMixin {
    @Shadow @Final private Queue<QueuedPacket> packetsToBeHandled;
    @Shadow private boolean closed;

    @Inject(method = "processQueuedPackets", at = @At("HEAD"), cancellable = true, require = 0)
    private void awaken$packetCap(CallbackInfo ci) {
        if (PacketCap.process((PacketProcessor) (Object) this, this.packetsToBeHandled, this.closed)) ci.cancel();
    }
}
