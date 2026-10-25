package dev.awaken.core.audio;

import dev.awaken.progression.Progression;
import dev.awaken.progression.RankHooks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The audio module (docs/modules/audio.md): music cues, the System's UI family, ambience beds, gate/Descent tracks and
 * the layered music. Common side: sound events, payloads, the cue cooldowns, the discovery and emitter watches, the
 * layer watch (contexts and intensity), commands, GameTests. Client side: {@code core.audio.client.AudioClient}.
 */
public final class AudioModule {
    private AudioModule() {}

    public static void init(IEventBus modBus) {
        AudioSounds.register(modBus);
        modBus.addListener(AudioNet::register);
        Progression.onAwakened(p -> AudioCues.play(p, Cue.AWAKENING));
        RankHooks.onRankUp((p, rank) -> AudioCues.play(p, Cue.RANK_UP));
        MusicLayers.register(MusicLayers.Slot.MUSIC, 100, "gate", GateMusic.INSTANCE);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            AudioWatch.tick(e.getServer());
            LayerWatch.tick(e.getServer());
        });
        // after everything else had its say: the damage that really landed, the deaths that really happened
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, LayerWatch::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDeathEvent.class, LayerWatch::onDeath);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            AudioWatch.forget(e.getEntity().getUUID());
            LayerWatch.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            AudioWatch.clear();
            AudioCues.clear();
            LayerWatch.clear();
        });
        AudioCommands.register();
        AudioTests.register();
        AudioLayerTests.register();
        WorldSoundTests.register();
    }
}
