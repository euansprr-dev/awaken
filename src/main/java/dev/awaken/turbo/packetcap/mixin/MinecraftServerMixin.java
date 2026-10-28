package dev.awaken.turbo.packetcap.mixin;

import dev.awaken.turbo.packetcap.PacketCap;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3's {@code MinecraftServer.stopServer} (read against 26.3 / NeoForge 26.3.0.95): the tasks still queued run as it
 * begins, and on an integrated server once more at its end (the client's halt task among them: PacketCap). Not required.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "stopServer", at = @At("HEAD"), require = 0)
    private void awaken$packetCapStopping(CallbackInfo ci) {
        PacketCap.stopping((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("TAIL"), require = 0)
    private void awaken$packetCapStopped(CallbackInfo ci) {
        PacketCap.stopped((MinecraftServer) (Object) this);
    }
}
