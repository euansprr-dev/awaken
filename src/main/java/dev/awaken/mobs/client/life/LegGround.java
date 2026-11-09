package dev.awaken.mobs.client.life;

import net.minecraft.client.Minecraft;

/** The ground the feet stand on in the client's world; kept apart so a test can walk the feet on any blocks ({@link LegSurface}). */
final class LegGround extends LegSurface {
    LegGround() {
        super(() -> Minecraft.getInstance().level);
    }
}
