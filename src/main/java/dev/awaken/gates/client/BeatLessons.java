package dev.awaken.gates.client;

import dev.awaken.body.ascent.client.GateLessons;
import dev.awaken.gates.net.BeatPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** A gate's lines on the client (SPEC §72.6): the gold figure the first time a family is met, and a clean line's reward. */
final class BeatLessons {
    private BeatLessons() {}

    static void lesson(BeatPayloads.Lesson msg) {
        GateLessons.show(msg.path());
    }

    /** A soft shimmer from the amethyst's own recording, under the sounds of the run, and a little Flow. */
    static void clean(BeatPayloads.Clean msg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        mc.level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F, 1.15F, false);
        GateLessons.clean(msg.beats());
    }
}
