package dev.awaken.core.audio.client;

import dev.awaken.core.audio.AudioNet;
import dev.awaken.core.audio.Cue;
import dev.awaken.core.audio.UiSound;
import dev.awaken.core.audio.client.world.Conductor;
import dev.awaken.core.audio.client.world.WorldSound;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Client entry of the audio module: cues through the music manager, the layered music, the System's UI rules, ambience beds, world sound. */
public final class AudioClient {
    private AudioClient() {}

    public static void init(IEventBus modBus) {
        WorldSound.init(modBus);
        Conductor.music(() -> LayerPlayer.MUSIC.ownsChannel() ? LayerPlayer.MUSIC.level() : -1, CuePlayer::playingStage);
        Conductor.register(new Conductor.Source() {
            @Override
            public int voices() {
                return Ambience.voices();
            }

            @Override
            public float emphasis() {
                return Ambience.emphasis();
            }
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(AudioNet.PlayCue.TYPE, (msg, ctx) -> CuePlayer.request(Cue.byOrdinal(msg.cue())));
            e.register(AudioNet.PlayUi.TYPE, (msg, ctx) -> AudioUi.server(UiSound.byOrdinal(msg.sound())));
            e.register(AudioNet.Emitters.TYPE, (msg, ctx) -> Ambience.emitters(msg.emitters()));
            e.register(AudioNet.LayerState.TYPE, (msg, ctx) -> {
                var slot = dev.awaken.core.audio.MusicLayers.Slot.byOrdinal(msg.slot());
                if (slot != null) LayerPlayer.of(slot).accept(msg);
            });
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            AudioUi.tick();
            LayerPlayer.MUSIC.tick();
            LayerPlayer.AMBIENCE.tick();
            CuePlayer.tick();
            Ambience.tick();
        });
        NeoForge.EVENT_BUS.addListener(SelectMusicEvent.class, CuePlayer::onSelectMusic);
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, AudioUi::onPlaySound);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, Ambience::onJoin);
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, Ambience::onLeave);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.LevelEvent.Unload.class, Ambience::onUnload);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            CuePlayer.clear();
            Ambience.clear();
            LayerPlayer.MUSIC.clear();
            LayerPlayer.AMBIENCE.clear();
        });
    }
}
