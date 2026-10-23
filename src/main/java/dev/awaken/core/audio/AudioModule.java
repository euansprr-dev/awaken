package dev.awaken.core.audio;

import dev.awaken.progression.Progression;
import dev.awaken.progression.RankHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The audio module (docs/modules/audio.md): music cues, the System's UI family, ambience beds and gate/Descent tracks.
 * Common side: sound events, payloads, the cue cooldowns, the discovery and emitter watches, commands, GameTests.
 * Client side: {@code core.audio.client.AudioClient}.
 */
public final class AudioModule {
    private AudioModule() {}

    public static void init(IEventBus modBus) {
        AudioSounds.register(modBus);
        modBus.addListener(AudioNet::register);
        Progression.onAwakened(p -> AudioCues.play(p, Cue.AWAKENING));
        RankHooks.onRankUp((p, rank) -> AudioCues.play(p, Cue.RANK_UP));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> AudioWatch.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> AudioWatch.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            AudioWatch.clear();
            AudioCues.clear();
        });
        AudioCommands.register();
        AudioTests.register();
    }
}
