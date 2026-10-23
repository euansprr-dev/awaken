# Playing AWAKEN

## Install (once)
1. Install NeoForge **26.3.0.95** for Minecraft 26.3: download the installer from https://neoforged.net, run it, choose
   "Install client". A "NeoForge 26.3" profile appears in the Minecraft launcher.
2. Build the mod (or use the jar from `build/libs/`): `tools/gradle.sh 900 build` → `build/libs/awaken-<version>.jar`.
3. Copy the jar into your NeoForge game folder's `mods/` (in the launcher: Installations → NeoForge 26.3 → Edit →
   Game directory, then open its `mods` folder).
4. Recommended for 120 fps: Sodium, Lithium, FerriteCore, ImmediatelyFast and EntityCulling for 26.3 (all optional).
5. Start a new world. Give the game 4–6 GB of memory (launcher → Installations → JVM arguments → `-Xmx6G`).

## Your first hour
Play normal Minecraft. Something will glitch. A white light will rise on the horizon. Walk to it.
Everything else teaches itself. If you'd rather skip the prologue: `/awaken story skip`.

## Keys (change them under Options → Controls → AWAKEN)
| Key | Does |
|---|---|
| Z X C V | Powers 1–4 |
| R | Ultimate |
| Left Alt | Movement power |
| K | Status (stats, Truths, Codex, Gate Map) |
| Sneak + G (hold) | Meditate · step out of your body |
| H | Reality check (in dreams it matters) |
| Y / N | Answer the Signal |
| B | Bind an Echo / summon your Echoes |
| J | Playtest: flag your last death as unfair (playtest mode only) |

## Settings
Mod settings live in the world's config (`awaken-synced.toml`): difficulty (STORY, HUNTER default, HARDLINE, HARDCORE),
story length (0.5 ≈ 50 h, 1.0 ≈ 100 h, 1.5 ≈ 150 h), playtest mode, and each direction on/off.
Client preferences (`awaken-client.toml`): camera shake, Signal typing speed, your rank aura.

## Playtest mode
Turn on `playtestMode`. Every 15 minutes the Signal asks what you're doing next (`/awaken next <answer>`) and how fun it
is (`/awaken fun 1-5`). Note surprises with `/awaken surprise <what>`. Afterwards:
`python3 tools/playtest/report.py <world>/awaken/playtest.jsonl report.md`.

## Testing commands (operators)
`/awaken status` · `/awaken why` (why the game made its last decisions) · `/awaken admin awaken|levels <n>|rankup|truths|paths|directive|regimen|daysend`
· `/awaken admin story revelation <key>|rival <rank>|ending|prologue` · each module adds its own (`/awaken gate ...`,
`/awaken descent ...`, `/awaken dream ...`, `/awaken veil ...`, `/awaken hearth ...`, `/awaken powers ...`).
