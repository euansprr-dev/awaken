package dev.awaken.gates.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Your ghost on a Gauntlet's course (SPEC §17.5 "Gauntlet"): your best run of it, replayed beside you as you run it
 * again. Client only (each runner sees their own): never sent, never saved, moved every tick by the client's ghost
 * driver, drawn as the Echo's translucent self in the course's gold.
 */
public class GauntletGhost extends EchoGhost {
    private int tint = 0xA8FFEFC4;

    public GauntletGhost(EntityType<? extends EchoGhost> type, Level level) {
        super(type, level);
    }

    /** The driver moves it; nothing of its own runs on the client. */
    @Override
    public void tick() {
        if (!level().isClientSide()) super.tick();
    }

    public void tint(int argb) {
        this.tint = argb;
    }

    @Override
    public int skinTint() {
        return tint;
    }
}
