# Ritual recording sources and processing

Acquired 2026-10-28 through BigSoundBank's normal WAV download form. Each item page declares CC0;
[the library licence page](https://bigsoundbank.com/licenses.html) permits commercial use, modification and redistribution.
No paid service, generated-audio API, credential or account was used. Archived FLAC is lossless conversion of the
original WAV (ffmpeg, compression level 8). `sources.json` records both original-WAV and archived-FLAC SHA-256.
The generator checks archived hashes before decoding. Credits are retained although CC0 does not require them.

| File | Recording | Author | Use |
|---|---|---|---|
|0580.flac|[Tombstone opening](https://bigsoundbank.com/tombstone-opening-s0580.html)|Joseph SARDIN|Stone friction, doors, rising bodies, contact grit|
|1022.flac|[Fall of stone](https://bigsoundbank.com/fall-of-stone-s1022.html)|Joseph SARDIN|Three distinct physical contact transients, rubble and impacts|
|3113.flac|[Thunder 2](https://bigsoundbank.com/thunder-2-s3113.html)|Joseph SARDIN & Axeline T.|Low mass and room-pressure tails|
|0595.flac|[Wind](https://bigsoundbank.com/wind-s0595.html)|Joseph SARDIN|Air rush, hall bed, beam movement; this source is studio wind foley|
|0950.flac|[Long welding 1](https://bigsoundbank.com/long-welding-1-s0950.html)|Joseph SARDIN|Real electrical arc texture in charge, release and travel|
|1023.flac|[Explosion far away 1](https://bigsoundbank.com/explosion-far-away-s1023.html)|Joseph SARDIN|Filtered pressure attack and low body|
|3446.flac|[Bell 1 o'clock](https://bigsoundbank.com/bell-1-o-clock-s3446.html)|Joseph SARDIN & Axeline T.|Real La Loupe church bell; same source used by city audio|

`python3 tools/art/specs/ritual_sounds.py build` is deterministic and offline. `ritual_recordings.py` selects physical
contacts, resamples for mass, removes DC, shapes attack/body/tail, filters extreme lows, and adds sparse filtered
reflections. Beam texture comes from recorded arc, wind and pressure; there is no pure oscillator in these 18 rebuilt
files. They keep transient dynamics instead of being forced to a single RMS. The existing six System/void assets
remain synthesized and are mixed more quietly. The 23 second hall bed is stereo; all physical events are mono for
OpenAL placement. Source FLAC is authoring material; it is not packaged in the mod jar.

`measurements.json` measures the encoded/decoded Ogg, not only the source buffer. All 18 files must decode at 44.1 kHz,
mono except the stereo hall, below −6 dBTP. Peaks, gated RMS, duration and Ogg SHA-256 are recorded there. GameTests
repeat channel/rate/headroom checks and compare charge length to the real director constant. `audio_mix.py --fix`
recognizes this manifest and cannot crush transient dynamics by trying to reach the generic SFX RMS target.

`python3 tools/tests/ritual-audio.py` compiles the actual mixer against deterministic engine stubs: it verifies
six voices including the reserved bed, beam layers, cancelled/live windups, late/offscreen foot sampling, menus,
level transfers, scene refresh and hand-close/logout cleanup. It does not launch a game or touch an audio device.
A coincident six-voice mix (beam release 0.55, impact 0.55, travel 0.45, two footsteps 0.48 each and hall 0.5) measured
−6.35 dBTP at source distance, before native attenuation or guidance ducking. This is a representative mix check,
not a guarantee for every external pack or user's volume configuration.

The motion/mix tests and quiet analysis verify timing, limits and file integrity. No Mac playback or game launch was
used. These checks do not establish subjective cinematic quality, perceived bass weight on speakers, spatial balance
in the live hall or net-latency/perceptual synchronization. Those still need human listening/playtest review.

Optional manual preview: `python3 tools/art/audio_listen_ritual.py` writes a local HTML page with labelled controls,
no autoplay and no script-triggered playback. Generated previews are in ignored `logs/`.
