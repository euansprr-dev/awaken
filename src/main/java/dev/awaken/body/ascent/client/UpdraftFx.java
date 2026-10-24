package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** An updraft vent heard: the column's breath, a soft rising rush, played near it now and then. */
public final class UpdraftFx {
    private UpdraftFx() {}

    public static void breathe(Level level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.level() != level) return;
        double dy = Math.max(0, mc.player.getY() - pos.getY() - 1);
        double dx = mc.player.getX() - pos.getX() - 0.5, dz = mc.player.getZ() - pos.getZ() - 0.5;
        if (dx * dx + dz * dz > 30 * 30 || dy > 40) return;
        // heard from inside the column at the body's height, from outside at the vent
        boolean inside = dx * dx + dz * dz < 16;
        double y = inside ? mc.player.getY() + 1 : pos.getY() + 2;
        float pitch = 0.9F + level.getRandom().nextFloat() * 0.2F;
        level.playLocalSound(inside ? mc.player.getX() : pos.getX() + 0.5, y, inside ? mc.player.getZ() : pos.getZ() + 0.5,
                AscentContent.UPDRAFT.get(), SoundSource.AMBIENT, inside ? 0.6F : 0.45F, pitch, false);
    }
}
