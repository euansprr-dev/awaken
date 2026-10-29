package dev.awaken.mobs.def;

import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

import java.util.List;

import static dev.awaken.mobs.def.Ability.Kind.*;
import static dev.awaken.mobs.def.MobDef.P;
import static dev.awaken.mobs.def.MobDef.m;
import static dev.awaken.mobs.def.Trait.*;

/**
 * The Descent (strata fauna, the nine stratum guardians plus the Seed's warden), the Veil, the Lucidium's dream fauna,
 * the eight Sovereigns of the Hollow Court, the Signal's Shadow, and the nightmare templates. Boss ids here are the
 * ones other modules call ({@code awaken:guardian_rim} ... {@code awaken:signals_shadow}). One {@code m(...)} per line.
 */
final class DeepBestiary {
    private DeepBestiary() {}

    private static Ability.Builder a(Ability.Kind k, String name) {
        return Ability.of(k, name);
    }

    static void define(List<MobDef> out) {
        strata(out);
        veilAndDream(out);
        guardians(out);
        sovereigns(out);
        nightmares(out);
    }

    private static void strata(List<MobDef> out) {
        // 1 The Rim: roots and glowing moss
        out.add(m("rim_crawler", "Rim Crawler", Theme.RIM, Tier.NORMAL, Model.CAVE_SPIDER, "Glows in the moss; pounces from the roots").hp(14).dmg(2.5).spd(0.32)
                .ab(a(LEAP, "Moss Pounce").radius(1.6F)).build());
        out.add(m("moss_shambler", "Moss Shambler", Theme.RIM, Tier.NORMAL, Model.ZOMBIE, "Moss Grip: a slow grab that roots you").hp(22).dmg(3).spd(0.22).tint(0x90C070)
                .ab(a(ROOT, "Moss Grip").radius(1.8F).range(8)).build());
        out.add(m("glowcap", "Glowcap", Theme.RIM, Tier.NORMAL, Model.SLIME, "A lantern of moss that makes you glow for everything else").hp(12).dmg(2).spd(0.26).scale(0.7F).tint(0xB0FF90)
                .ab(a(LEAP, "Glow Hop").fx(MobEffects.GLOWING, 100, 0)).build());
        // 2 The Hanging Gardens: vertical, vines, wind
        out.add(m("vine_lurker", "Vine Lurker", Theme.GARDENS, Tier.NORMAL, Model.SPIDER, "Drops from the vines above you").hp(18).dmg(3).spd(0.32).tint(0x88C070)
                .ab(a(LEAP, "Drop From Above").radius(2.2F).heavy()).build());
        out.add(m("pollen_drifter", "Pollen Drifter", Theme.GARDENS, Tier.NORMAL, Model.BREEZE, "Pollen Gust: shoves you off the ledge").hp(16).dmg(3).spd(0.28).tint(0xE0F098).traits(RANGED)
                .ab(a(GUST, "Pollen Gust").radius(6).power(0.6F), a(BOLT, "Thorn Seed").bolt(BoltKind.THORN)).build());
        out.add(m("bloom_creeper", "Bloom Creeper", Theme.GARDENS, Tier.NORMAL, Model.CREEPER, "Blooms: swells with petals and bursts").hp(16).dmg(2).spd(0.24).tint(0xB8E890).traits(FUSE).blast(1.8F).build());
        // 3 The Drowned Choir: flooded halls, singing
        out.add(m("choir_drowned", "Choir Drowned", Theme.CHOIR, Tier.NORMAL, Model.DROWNED, "Sings: every drowned near it hits harder and heals").hp(20).dmg(3).spd(0.23)
                .ab(a(HEAL, "Choir Song").power(0.1F), a(BOLT, "Salt Note").bolt(BoltKind.WATER)).build());
        out.add(m("deep_eel", "Deep Eel", Theme.CHOIR, Tier.NORMAL, Model.SERPENT, "Strikes out of the flooded dark").hp(16).dmg(3.5).spd(0.32).scale(0.8F)
                .ab(a(CHARGE, "Eel Strike").power(1.2F).wind(12).cd(80)).build());
        out.add(m("bell_jelly", "Bell Jelly", Theme.CHOIR, Tier.NORMAL, Model.SLIME, "A translucent jelly whose sting numbs").hp(14).dmg(2.5).spd(0.24).tint(0x88D8F0).traits(HAUNTED, SLOW_TOUCH)
                .ab(a(LEAP, "Numbing Sting").fx(MobEffects.SLOWNESS, 60, 1)).build());
        // 4 The Bone Orchard: giant ribs; the dead get up once
        out.add(m("orchard_dead", "Orchard Dead", Theme.ORCHARD, Tier.NORMAL, Model.SKELETON, "Gets up once after you kill it").hp(18).dmg(3).spd(0.25).hold(Items.BOW).traits(RANGED, RISES)
                .ab(a(ARROWS, "Marrow Shot").n(2)).build());
        out.add(m("rib_hound", "Rib Hound", Theme.ORCHARD, Tier.NORMAL, Model.WOLF, "A skeletal hound that gets up once").hp(16).dmg(3).spd(0.34).tint(0xE8E0D0).traits(RISES)
                .ab(a(LEAP, "Rib Lunge").radius(1.8F)).build());
        out.add(m("marrow_fiend", "Marrow Fiend", Theme.ORCHARD, Tier.NORMAL, Model.HUSK, "Marrow Crush withers; gets up once").hp(22).dmg(3.5).spd(0.23).tint(0xF0E0D0).traits(RISES)
                .ab(a(SLAM, "Marrow Crush").radius(2.8F).fx(MobEffects.WITHER, 60, 0)).build());
        // 5 The Silent Halls: sound draws the Listeners
        out.add(m("listener", "Listener", Theme.SILENT, Tier.NORMAL, Model.LISTENER, "Blind; hunts the last sound you made. Sneak, and it loses you").hp(28).dmg(6).spd(0.3).traits(LISTENER)
                .ab(a(SLAM, "Found You").radius(2.8F).power(1.4F).wind(16).heavy()).build());
        out.add(m("hush_moth", "Hush Moth", Theme.SILENT, Tier.NORMAL, Model.PHANTOM, "Eats the light around you").hp(10).dmg(2).spd(0.4).scale(0.6F).tint(0xE0E4F0)
                .ab(a(DARKNESS, "Eats the Light").radius(10).cd(240)).build());
        out.add(m("still_one", "Still One", Theme.SILENT, Tier.NORMAL, Model.CREAKING, "Only moves while nobody is looking").hp(24).dmg(4.5).spd(0.32).tint(0xD8DCE8).traits(WATCHED_FREEZE)
                .ab(a(SLAM, "Unseen Blow").radius(2.5F)).build());
        // 6 The Precursor Foundry: machines wake when powered
        out.add(m("foundry_sentinel", "Foundry Sentinel", Theme.FOUNDRY, Tier.NORMAL, Model.IRON_GOLEM, "Dormant machine that wakes when you come close").hp(30).dmg(5).spd(0.24).scale(0.8F).tint(0xA0B8D8).traits(DORMANT)
                .ab(a(SLAM, "Wake Slam").radius(3).heavy(), a(BEAM, "Weld Beam").bolt(BoltKind.CHARGE)).build());
        out.add(m("arc_drone", "Arc Drone", Theme.FOUNDRY, Tier.NORMAL, Model.BEE, "Arcs: a crackle, then a bolt").hp(10).dmg(2.5).spd(0.5).tint(0xA8C0E0).traits(RANGED)
                .ab(a(BOLT, "Arc").bolt(BoltKind.CHARGE)).build());
        out.add(m("servitor", "Servitor", Theme.FOUNDRY, Tier.NORMAL, Model.VINDICATOR, "Servo Rush with a precursor axe").hp(22).dmg(4).spd(0.27).hold(Items.IRON_AXE).tint(0xB0C4E0)
                .ab(a(CHARGE, "Servo Rush")).build());
        // 7 The Inverted Sea: gravity flips with the tides
        out.add(m("tide_eel", "Tide Eel", Theme.INVERTED, Tier.NORMAL, Model.SERPENT, "Swims through the air when the tide flips").hp(18).dmg(3.5).spd(0.34)
                .ab(a(CHARGE, "Tide Strike").power(1.2F).wind(12).cd(80)).build());
        out.add(m("drift_jelly", "Drift Jelly", Theme.INVERTED, Tier.NORMAL, Model.GHAST, "Drifts above you, raining salt").hp(14).dmg(2.5).spd(0.2).scale(0.3F).traits(HAUNTED, RANGED)
                .ab(a(BOLT, "Salt Rain").n(2).bolt(BoltKind.WATER)).build());
        out.add(m("upturned_drowned", "Upturned Drowned", Theme.INVERTED, Tier.NORMAL, Model.DROWNED, "Walks the wrong way up; spits the undertow").hp(20).dmg(3).spd(0.24).traits(UPSIDE_DOWN)
                .ab(a(BOLT, "Undertow Spit").bolt(BoltKind.WATER)).build());
        // 8 The Dreaming Stratum: rooms change when unobserved
        out.add(m("dream_wisp", "Dream Wisp", Theme.DREAMING, Tier.NORMAL, Model.VEX, "Flickers in and out, then dives").hp(10).dmg(3).spd(0.5).traits(HAUNTED)
                .ab(a(CHARGE, "Flicker").wind(12).cd(80).range(10)).build());
        out.add(m("shifting_one", "Shifting One", Theme.DREAMING, Tier.NORMAL, Model.ENDERMAN, "Rearranges itself around you").hp(24).dmg(4).spd(0.3)
                .ab(a(BLINK, "Rearrange")).build());
        out.add(m("lullaby_phantom", "Lullaby Phantom", Theme.DREAMING, Tier.NORMAL, Model.PHANTOM, "Its lullaby makes you heavy").hp(14).dmg(3).spd(0.4)
                .ab(a(DARKNESS, "Lullaby").radius(8).fx(MobEffects.SLOWNESS, 60, 1), a(CHARGE, "Swoop")).build());
        // 9 The Hollow Court: your own shadow fights you
        out.add(m("court_shadow", "Your Shadow", Theme.COURT, Tier.NORMAL, Model.MIMIC, "Your own shadow: your face, your blade").hp(22).dmg(4).spd(0.29).traits(MIMIC, HAUNTED)
                .ab(a(BLINK, "Shadowstep"), a(CHARGE, "Your Charge")).build());
        out.add(m("hollow_courtier", "Hollow Courtier", Theme.COURT, Tier.NORMAL, Model.EVOKER, "Court etiquette: fangs for anyone who approaches").hp(22).dmg(4).spd(0.25).traits(RANGED)
                .ab(a(FANGS, "Court Etiquette"), a(BOLT, "Hollow Favour").bolt(BoltKind.VOID)).build());
        out.add(m("court_hound", "Court Hound", Theme.COURT, Tier.NORMAL, Model.ZOGLIN, "A gilded charge through the court").hp(26).dmg(4.5).spd(0.3)
                .ab(a(CHARGE, "Gilded Charge")).build());
        // 10 The Seed: the bottom, the truth
        out.add(m("seed_mote", "Seed Mote", Theme.SEED, Tier.NORMAL, Model.VEX, "A spark of the Signal that flares when it dives").hp(12).dmg(3.5).spd(0.5)
                .ab(a(CHARGE, "Mote Flare").bolt(BoltKind.SIGNAL).wind(12).cd(80).range(12)).build());
        out.add(m("signal_echo", "Signal Echo", Theme.SEED, Tier.NORMAL, Model.HUMAN, "An echo of an instruction: lances you, then is somewhere else").hp(24).dmg(4.5).spd(0.29)
                .ab(a(BEAM, "Echoed Instruction").bolt(BoltKind.SIGNAL), a(BLINK, "Elsewhere")).build());
        out.add(m("seed_root", "Seed Root", Theme.SEED, Tier.NORMAL, Model.CREAKING, "The Seed's roots: fangs in lines of light").hp(26).dmg(4.5).spd(0.26).tint(0xF0E8C0)
                .ab(a(FANGS, "Seed Roots")).build());
    }

    private static void veilAndDream(List<MobDef> out) {
        out.add(m("cord_eater", "Cord-Eater", Theme.VEIL, Tier.NORMAL, Model.SERPENT, "Hunts spirits through walls and bites the cord that ties you to your body").hp(20).dmg(4).spd(0.34).scale(1.2F).traits(SPIRIT_HUNTER, NOCLIP, HAUNTED)
                .ab(a(CHARGE, "Cord Bite").bolt(BoltKind.SPIRIT).wind(14).cd(90).range(12)).build());
        out.add(m("veil_wraith", "Veil Wraith", Theme.VEIL, Tier.NORMAL, Model.PHANTOM, "A Veil creature: half there, diving through the fog").hp(14).dmg(3).spd(0.42).traits(HAUNTED)
                .ab(a(CHARGE, "Wraith Dive").bolt(BoltKind.SPIRIT).wind(14).cd(90)).build());
        out.add(m("dreamling", "Dreamling", Theme.DREAM, Tier.NORMAL, Model.SLIME, "Bounces like a thought you can't hold").hp(12).dmg(2).spd(0.28).scale(0.8F)
                .ab(a(LEAP, "Bounce").bolt(BoltKind.DREAM)).build());
        out.add(m("memory_mimic", "Memory Mimic", Theme.DREAM, Tier.NORMAL, Model.MIMIC, "Remembers you: your face, a little wrong").hp(18).dmg(3).spd(0.28).traits(MIMIC, HAUNTED)
                .ab(a(BLINK, "Remembered You")).build());
    }

    private static void guardians(List<MobDef> out) {
        out.add(m("guardian_rim", "The Rootmother", Theme.RIM, Tier.GUARDIAN, Model.SPIDER, "Moss webs and root lances; the Rim wakes with her").hp(30).dmg(4.5).spd(0.3).scale(2.4F).size(0.85F, 1.45F).tint(0x98C878).bar(BossBarColor.GREEN).arena(22)
                .intro("The roots under the Rim are hers.")
                .phase(1.0F, "The Rootmother descends.", a(ROOT, "Moss Web").radius(3), a(LEAP, "Rootfall").radius(3.5F).heavy())
                .phase(0.5F, "The Rim wakes.", a(SUMMON, "Brood").summon("rim_crawler").n(3), a(FANGS, "Root Lances").n(14), a(LEAP, "Rootfall").radius(3.5F))
                .parts(P("rootmother_silk", "Rootmother Silk", "moss")).build());
        out.add(m("guardian_gardens", "The Thornback", Theme.GARDENS, Tier.GUARDIAN, Model.RAVAGER, "Charges along the ledges and blows petals hard enough to knock you off").hp(32).dmg(5).spd(0.3).size(2.2F, 2.3F).tint(0xA0D090).bar(BossBarColor.GREEN).arena(24)
                .intro("Something heavy moves in the hanging gardens.")
                .phase(1.0F, "The Thornback lowers its horns.", a(CHARGE, "Thornback Charge").heavy(), a(GUST, "Gale of Petals").radius(10).power(0.8F))
                .phase(0.5F, "The vines tighten.", a(NOVA, "Thorn Spray").n(14).bolt(BoltKind.THORN), a(SUMMON, "Lurkers").summon("vine_lurker").n(2), a(CHARGE, "Thornback Charge"))
                .parts(P("thornback_horn", "Thornback Horn", "leaf")).build());
        out.add(m("guardian_choir", "The Choirmaster", Theme.CHOIR, Tier.GUARDIAN, Model.WITCH, "Hymns of salt; when the water rises, the choir sings with her").hp(30).dmg(4.5).spd(0.25).scale(2.0F).size(0.8F, 1.9F).tint(0x90C8E0).bar(BossBarColor.BLUE).arena(22)
                .intro("The singing was coming from her all along.")
                .phase(1.0F, "The Choirmaster takes a breath.", a(BOLT, "Hymn of Salt").n(4).bolt(BoltKind.WATER), a(HEAL, "Refrain").power(0.1F))
                .phase(0.5F, "The water rises.", a(AURA, "Rising Tide").radius(9).bolt(BoltKind.WATER).fx(MobEffects.SLOWNESS, 40, 1).heavy(), a(SUMMON, "The Choir").summon("choir_drowned").n(2), a(PULL, "Undertow"))
                .parts(P("choirmaster_pearl", "Choirmaster's Pearl", "water")).build());
        out.add(m("guardian_orchard", "The Ossuary King", Theme.ORCHARD, Tier.GUARDIAN, Model.WITHER_SKELETON, "Rib Spikes and a court of the dead; he gets up once").hp(28).dmg(5).spd(0.26).scale(2.0F).size(0.8F, 2.3F).tint(0xF0E8D8).hold(Items.STONE_SWORD).traits(RISES).bar(BossBarColor.WHITE).arena(22)
                .intro("The orchard grew from his ribs.")
                .phase(1.0F, "The Ossuary King stands.", a(SLAM, "Bone Crush").radius(4).heavy(), a(NOVA, "Rib Spikes").n(12).bolt(BoltKind.SPIRIT))
                .phase(0.5F, "The dead get up.", a(SUMMON, "Rise").summon("orchard_dead").n(3), a(CHARGE, "Royal March"), a(SLAM, "Bone Crush").radius(4))
                .parts(P("ossuary_crown", "Ossuary Crown", "bone")).build());
        out.add(m("guardian_silent", "The Great Listener", Theme.SILENT, Tier.GUARDIAN, Model.LISTENER, "It hears your heartbeat; scream, and it screams back").hp(30).dmg(5.5).spd(0.3).scale(1.8F).size(1.6F, 2.2F).traits(LISTENER).bar(BossBarColor.WHITE).arena(24)
                .intro("Every Listener in the halls is one of its ears.")
                .phase(1.0F, "The Great Listener turns its head.", a(SLAM, "Found You").radius(3.5F).power(1.6F).heavy(), a(BEAM, "Echo Scream").bolt(BoltKind.VOID))
                .phase(0.5F, "It stops breathing.", a(DARKNESS, "Absolute Silence").radius(20), a(SUMMON, "Ears").summon("listener").n(2), a(SLAM, "Found You").radius(3.5F).power(1.6F))
                .parts(P("listener_ear", "Great Listener's Ear", "stone")).build());
        out.add(m("guardian_foundry", "The Furnace Engine", Theme.FOUNDRY, Tier.GUARDIAN, Model.IRON_GOLEM, "Arc welds and rivet rain; the machines wake around it").hp(32).dmg(5).spd(0.24).scale(1.8F).size(1.6F, 2.3F).tint(0x98B0D0).bar(BossBarColor.YELLOW).arena(24)
                .intro("The foundry's heart is still warm.")
                .phase(1.0F, "The Furnace Engine powers up.", a(SLAM, "Engine Slam").radius(4.5F).heavy(), a(BEAM, "Arc Weld").bolt(BoltKind.CHARGE))
                .phase(0.5F, "Machines wake.", a(SUMMON, "Wake the Drones").summon("arc_drone").n(3), a(RAIN, "Rivet Rain").n(5).bolt(BoltKind.CHARGE), a(SLAM, "Engine Slam").radius(4.5F))
                .parts(P("furnace_engine_core", "Furnace Engine Core", "precursor")).build());
        out.add(m("guardian_inverted", "The Drowned Moon", Theme.INVERTED, Tier.GUARDIAN, Model.ELDER_GUARDIAN, "Moonbeams and the tide-turn that lifts you off the floor").hp(30).dmg(5).spd(0.22).scale(1.6F).size(2.0F, 2.8F).tint(0x9CB8F0).bar(BossBarColor.BLUE).arena(26)
                .intro("The sea above you has a moon in it.")
                .phase(1.0F, "The Drowned Moon rises.", a(BEAM, "Moonbeam").bolt(BoltKind.WATER).power(1.8F).heavy(), a(LIFT, "Tide Turn").radius(4))
                .phase(0.5F, "The tide turns.", a(PULL, "Undertow").radius(14), a(NOVA, "Tide Halo").n(14).bolt(BoltKind.WATER), a(LIFT, "Tide Turn").radius(5))
                .parts(P("moon_scale", "Drowned Moon Scale", "water")).build());
        out.add(m("guardian_dreaming", "The Architect of Rooms", Theme.DREAMING, Tier.GUARDIAN, Model.EVOKER, "The floor shifts, doors open behind you, the ceiling falls").hp(30).dmg(5).spd(0.26).scale(1.8F).size(2.0F, 3.0F).tint(0xE0B0E0).bar(BossBarColor.PINK).arena(22)
                .intro("You didn't come in through that door.")
                .phase(1.0F, "The Architect redraws the room.", a(FANGS, "Shifting Floor").n(14), a(BLINK, "Door Step"))
                .phase(0.5F, "The room changes.", a(DECOY, "Other Rooms").n(2), a(RAIN, "Falling Ceiling").n(5).bolt(BoltKind.DREAM), a(FANGS, "Shifting Floor").n(16))
                .parts(P("architect_key", "Architect's Key", "dream")).build());
        out.add(m("guardian_court", "The Shadow Regent", Theme.COURT, Tier.GUARDIAN, Model.MIMIC, "Your shadow on a throne: your parry, your charge, your moves").hp(32).dmg(5.5).spd(0.3).scale(1.6F).size(0.8F, 2.9F).hold(Items.NETHERITE_SWORD).traits(MIMIC).bar(BossBarColor.PURPLE).arena(22)
                .intro("It sits the way you sit.")
                .phase(1.0F, "Your shadow rises from the throne.", a(GUARD, "Your Parry"), a(CHARGE, "Your Charge"))
                .phase(0.5F, "It learns your moves.", a(DECOY, "Your Reflections").n(2), a(BLINK, "Shadowstep"), a(NOVA, "Hollow Halo").n(12).bolt(BoltKind.VOID))
                .parts(P("regent_shadow", "Regent's Shadow", "obsidian")).build());
        out.add(m("guardian_seed", "The Seed Warden", Theme.SEED, Tier.GUARDIAN, Model.SHADE, "Lances of signal light around the Seed's last door").hp(34).dmg(5.5).spd(0.28).scale(1.6F).size(1.8F, 3.1F).bar(BossBarColor.WHITE).arena(24)
                .intro("The last door. It has been waiting.")
                .phase(1.0F, "The Seed Warden unfolds.", a(BEAM, "Signal Lance").bolt(BoltKind.SIGNAL), a(RAIN, "Signal Marks").n(4).bolt(BoltKind.SIGNAL))
                .phase(0.5F, "The door opens a crack.", a(SUMMON, "Motes").summon("seed_mote").n(3), a(BURST, "Door Light").radius(13).inner(3.5F).bolt(BoltKind.SIGNAL).heavy())
                .parts(P("seed_shard", "Seed Shard", "signal")).build());
    }

    private static void sovereigns(List<MobDef> out) {
        out.add(m("sovereign_hunger", "Sovereign of Hunger", Theme.COURT, Tier.SOVEREIGN, Model.RAVAGER, "Hunger gone wrong: devours, gorges, and only gets hungrier").hp(34).dmg(5.5).spd(0.3).scale(1.8F).size(1.6F, 2.0F).tint(0xC86060).glow(0xFF4A4A).bar(BossBarColor.RED).arena(26)
                .intro("It ate the direction it came from.")
                .phase(1.0F, "The Sovereign of Hunger opens its jaws.", a(CHARGE, "Devour").power(1.6F).heavy(), a(PULL, "Gnawing Pull").radius(12))
                .phase(0.66F, "It is still hungry.", a(SUMMON, "Hounds of the Court").summon("court_hound").n(2), a(SLAM, "Gorge").radius(4.5F), a(CHARGE, "Devour"))
                .phase(0.33F, "Starving.", a(FRENZY, "Starving Frenzy"), a(AURA, "Famine").radius(8).bolt(BoltKind.VOID).fx(MobEffects.HUNGER, 100, 2).heavy(), a(CHARGE, "Devour"))
                .parts(P("hunger_fang", "Fang of Hunger", "crimson")).build());
        out.add(m("sovereign_silence", "Sovereign of Silence", Theme.COURT, Tier.SOVEREIGN, Model.LISTENER, "The Veil gone wrong: hunts every sound, swallows every light").hp(34).dmg(5.5).spd(0.3).scale(1.9F).size(0.95F, 3.1F).tint(0x8890A8).glow(0xE6F0FF).traits(LISTENER).bar(BossBarColor.WHITE).arena(26)
                .intro("It listened until there was nothing left to hear.")
                .phase(1.0F, "The Sovereign of Silence tilts its head.", a(SLAM, "Found You").radius(4).power(1.6F).heavy(), a(DARKNESS, "No Sound At All").radius(20))
                .phase(0.66F, "It hears your heartbeat.", a(BEAM, "Inverted Scream").bolt(BoltKind.VOID).power(2.0F).heavy(), a(SUMMON, "Ears").summon("listener").n(2))
                .phase(0.33F, "Silence.", a(BURST, "Deafening Silence").radius(15).inner(4).bolt(BoltKind.VOID).heavy(), a(PULL, "Hush").radius(14), a(SLAM, "Found You").radius(4))
                .parts(P("silence_ear", "Ear of Silence", "stone")).build());
        out.add(m("sovereign_sleep", "Sovereign of Sleep", Theme.COURT, Tier.SOVEREIGN, Model.PHANTOM, "The Lucidium gone wrong: lullabies that make you heavy, dives from the dark").hp(32).dmg(5.5).spd(0.38).scale(3.2F).size(0.88F, 1.72F).tint(0xC898D0).glow(0xF6C6E6).bar(BossBarColor.PINK).arena(28)
                .intro("It never let anyone wake up.")
                .phase(1.0F, "The Sovereign of Sleep spreads its wings.", a(CHARGE, "Night Dive").heavy(), a(DARKNESS, "Lullaby").radius(16).fx(MobEffects.SLOWNESS, 80, 1))
                .phase(0.66F, "You feel heavy.", a(SUMMON, "Lullabies").summon("lullaby_phantom").n(3), a(RAIN, "Dream Fall").n(5).bolt(BoltKind.DREAM), a(CHARGE, "Night Dive"))
                .phase(0.33F, "Never wake.", a(BURST, "Never Wake").radius(15).inner(4).bolt(BoltKind.DREAM).heavy(), a(CHARGE, "Night Dive"), a(DARKNESS, "Lullaby").radius(16).fx(MobEffects.SLOWNESS, 80, 2))
                .parts(P("sleep_feather", "Feather of Sleep", "dream")).build());
        out.add(m("sovereign_machines", "Sovereign of Machines", Theme.COURT, Tier.SOVEREIGN, Model.IRON_GOLEM, "The Above gone wrong: every move calculated, every strike from orbit").hp(36).dmg(5.5).spd(0.24).scale(1.9F).size(1.2F, 3.05F).tint(0xB8C8E0).glow(0xFFD060).bar(BossBarColor.YELLOW).arena(26)
                .intro("It built until nothing was left to build with.")
                .phase(1.0F, "The Sovereign of Machines computes you.", a(BEAM, "Calculated Lance").bolt(BoltKind.CHARGE).power(1.8F), a(SLAM, "Hydraulic Slam").radius(4.5F).heavy())
                .phase(0.66F, "Subroutines.", a(SUMMON, "Drones").summon("arc_drone").n(3), a(RAIN, "Orbital Strike").n(6).bolt(BoltKind.CHARGE), a(BEAM, "Calculated Lance").bolt(BoltKind.CHARGE))
                .phase(0.33F, "Shutdown.", a(BURST, "Shutdown").radius(15).inner(4).bolt(BoltKind.CHARGE).heavy(), a(NOVA, "Spark Lattice").n(16).bolt(BoltKind.CHARGE), a(SLAM, "Hydraulic Slam").radius(5))
                .parts(P("machine_crown", "Crown of Machines", "precursor")).build());
        out.add(m("sovereign_mirrors", "Sovereign of Mirrors", Theme.COURT, Tier.SOVEREIGN, Model.MIMIC, "The self gone wrong: your face, perfect parries, a hall of reflections").hp(32).dmg(5.5).spd(0.3).scale(1.8F).size(0.9F, 3.25F).hold(Items.DIAMOND_SWORD).traits(MIMIC, REFLECT).bar(BossBarColor.WHITE).arena(24)
                .intro("It only ever looked at itself.")
                .phase(1.0F, "The Sovereign of Mirrors smiles your smile.", a(GUARD, "Flawless Parry"), a(CHARGE, "Mirrored Lunge"))
                .phase(0.66F, "A hall of you.", a(DECOY, "Hall of Mirrors").n(3), a(BLINK, "Through the Glass"), a(CHARGE, "Mirrored Lunge"))
                .phase(0.33F, "Shatter.", a(NOVA, "Shatter").n(20).bolt(BoltKind.GLASS).heavy(), a(DECOY, "Hall of Mirrors").n(2), a(GUARD, "Flawless Parry"))
                .parts(P("mirror_heart", "Heart of Mirrors", "mirror")).build());
        out.add(m("sovereign_storms", "Sovereign of Storms", Theme.COURT, Tier.SOVEREIGN, Model.BREEZE, "Speed and power gone wrong: lightning on every circle, thunder that throws you").hp(32).dmg(5.5).spd(0.32).scale(2.4F).size(1.2F, 1.48F).tint(0xB8D8FF).glow(0x7FC3FF).bar(BossBarColor.BLUE).arena(28)
                .intro("It went so fast it left itself behind.")
                .phase(1.0F, "The Sovereign of Storms crackles.", a(RAIN, "Lightning").n(4).bolt(BoltKind.CHARGE), a(GUST, "Thunderclap").radius(10).power(0.8F))
                .phase(0.66F, "The storm thickens.", a(BOLT, "Storm Spears").n(5).bolt(BoltKind.CHARGE), a(LIFT, "Updraft").radius(4), a(RAIN, "Lightning").n(5).bolt(BoltKind.CHARGE))
                .phase(0.33F, "Eye of the storm.", a(RAIN, "Lightning").n(8).bolt(BoltKind.CHARGE).heavy(), a(FRENZY, "Overdrive"), a(GUST, "Thunderclap").radius(12).power(0.9F))
                .parts(P("storm_core", "Storm Core", "signal")).build());
        out.add(m("sovereign_roots", "Sovereign of Roots", Theme.COURT, Tier.SOVEREIGN, Model.CREAKING, "Home gone wrong: it holds you fast and never lets you leave").hp(36).dmg(5.5).spd(0.26).scale(1.9F).size(1.9F, 1.95F).tint(0x806848).glow(0xD4AF37).bar(BossBarColor.GREEN).arena(24)
                .intro("It made a home and closed every door.")
                .phase(1.0F, "The Sovereign of Roots takes hold.", a(FANGS, "Root Lance").n(16), a(ROOT, "Hold Fast").radius(3))
                .phase(0.66F, "Stay.", a(SUMMON, "Rooted Ones").summon("vine_creaker").n(2), a(FANGS_RING, "Hearth Ring").n(12), a(FANGS, "Root Lance").n(16))
                .phase(0.33F, "Never leave.", a(ROOT, "Hold Fast").radius(4.5F).heavy(), a(NOVA, "Thorn Halo").n(16).bolt(BoltKind.THORN), a(FANGS_RING, "Hearth Ring").n(14))
                .parts(P("root_heart", "Heart of Roots", "wood")).build());
        out.add(m("sovereign_ash", "Sovereign of Ash", Theme.COURT, Tier.SOVEREIGN, Model.BLAZE, "Burnout gone wrong: cinders, ashfall, and a burnout you have to outrun").hp(32).dmg(5.5).spd(0.3).scale(2.4F).size(1.25F, 1.65F).tint(0x9A9090).glow(0xFF7A2A).traits(FIRE_IMMUNE).bar(BossBarColor.RED).arena(26)
                .intro("It burned everything to keep going.")
                .phase(1.0F, "The Sovereign of Ash smoulders.", a(BOLT, "Cinders").n(5).bolt(BoltKind.FIRE), a(CLOUD, "Ash Cloud").radius(3.5F).fx(MobEffects.BLINDNESS, 60, 0))
                .phase(0.66F, "Ashfall.", a(RAIN, "Ashfall").n(6).bolt(BoltKind.FIRE), a(SUMMON, "Embers").summon("ember_imp").n(2), a(BOLT, "Cinders").n(5).bolt(BoltKind.FIRE))
                .phase(0.33F, "Burnout.", a(AURA, "Burnout").radius(9).bolt(BoltKind.FIRE).heavy(), a(NOVA, "Last Embers").n(16).bolt(BoltKind.FIRE), a(RAIN, "Ashfall").n(7).bolt(BoltKind.FIRE))
                .parts(P("ash_heart", "Heart of Ash", "ember")).build());
        out.add(m("signals_shadow", "The Signal's Shadow", Theme.SEED, Tier.FINAL, Model.SHADE, "It has your voice: every gate, every lance, then the Seed collapses").hp(40).dmg(6).spd(0.3).scale(2.0F).size(1.15F, 1.13F).bar(BossBarColor.WHITE).arena(30)
                .intro("It has your voice.")
                .phase(1.0F, "The Signal's Shadow speaks.", a(BEAM, "Instruction").bolt(BoltKind.SIGNAL).power(1.8F), a(BLINK, "Signal Step"), a(BOLT, "Directive").n(5).bolt(BoltKind.SIGNAL))
                .phase(0.66F, "It remembers every gate.", a(SUMMON, "Echoes").summon("signal_echo").n(2), a(RAIN, "Signal Marks").n(6).bolt(BoltKind.SIGNAL), a(DECOY, "Your Echoes").n(2))
                .phase(0.33F, "It is you, from the bottom.", a(BURST, "Seed Collapse").radius(16).inner(4).bolt(BoltKind.SIGNAL).power(2.6F).heavy(), a(PULL, "Come Down").radius(18), a(NOVA, "Signal Halo").n(20).bolt(BoltKind.SIGNAL), a(FRENZY, "Last Instruction"))
                .parts(P("signal_fragment", "Signal Fragment", "signal"), P("shadow_of_you", "Shadow of You", "obsidian")).build());
    }

    private static void nightmares(List<MobDef> out) {
        out.add(m("nightmare_falling", "The Long Drop", Theme.FEAR, Tier.NIGHTMARE, Model.PHANTOM, "Lifts you up and lets you fall").hp(22).dmg(4).spd(0.4).scale(2.4F).bar(BossBarColor.PURPLE).arena(22)
                .intro("You never hit the ground. You always nearly do.")
                .phase(1.0F, "The ground falls away.", a(LIFT, "Lift").radius(3.5F), a(CHARGE, "Fall With Me").heavy())
                .phase(0.5F, "Further down.", a(LIFT, "Lift").radius(4.5F), a(DARKNESS, "No Bottom"), a(CHARGE, "Fall With Me")).build());
        out.add(m("nightmare_burning", "The Pyre", Theme.FEAR, Tier.NIGHTMARE, Model.BLAZE, "Fire that follows you").hp(22).dmg(4).spd(0.3).scale(2.0F).traits(FIRE_IMMUNE).glow(0xFF6A2A).bar(BossBarColor.RED).arena(22)
                .intro("It smells like the last time.")
                .phase(1.0F, "The Pyre catches.", a(BOLT, "Cinders").n(4).bolt(BoltKind.FIRE), a(AURA, "Pyre").radius(6).bolt(BoltKind.FIRE))
                .phase(0.5F, "It spreads.", a(RAIN, "Firefall").n(5).bolt(BoltKind.FIRE), a(NOVA, "Flare").n(12).bolt(BoltKind.FIRE)).build());
        out.add(m("nightmare_drowning", "The Undertow", Theme.FEAR, Tier.NIGHTMARE, Model.DROWNED, "Pulls you under and holds you there").hp(22).dmg(4).spd(0.26).scale(2.0F).glow(0x3DD8C8).bar(BossBarColor.BLUE).arena(22)
                .intro("You can't remember which way is up.")
                .phase(1.0F, "The water closes.", a(PULL, "Undertow").radius(12), a(CONE, "Rush of Water").bolt(BoltKind.WATER).radius(6))
                .phase(0.5F, "No air.", a(AURA, "Lungful").radius(7).bolt(BoltKind.WATER).fx(MobEffects.SLOWNESS, 40, 1), a(BOLT, "Black Water").n(4).bolt(BoltKind.WATER)).build());
        out.add(m("nightmare_undead", "The Hollow Crowd", Theme.FEAR, Tier.NIGHTMARE, Model.ZOMBIE, "More of them every time you look; it gets up once").hp(22).dmg(4).spd(0.26).scale(1.7F).traits(RISES).bar(BossBarColor.GREEN).arena(22)
                .intro("They all have the same face.")
                .phase(1.0F, "The crowd shuffles closer.", a(SLAM, "Crush").radius(3.5F), a(SUMMON, "More of Them").summon("sleepwalker").n(2))
                .phase(0.5F, "Closer.", a(SUMMON, "More of Them").summon("sleepwalker").n(3), a(CHARGE, "Surge").heavy()).build());
        out.add(m("nightmare_arthropod", "The Thousand Legs", Theme.FEAR, Tier.NIGHTMARE, Model.SPIDER, "Webs, and the sound of a thousand legs").hp(22).dmg(4).spd(0.32).scale(2.6F).bar(BossBarColor.PURPLE).arena(22)
                .intro("You can hear them in the walls.")
                .phase(1.0F, "Legs everywhere.", a(ROOT, "Web").radius(2.5F), a(LEAP, "Drop").radius(3).heavy())
                .phase(0.5F, "The walls open.", a(SUMMON, "Hatchlings").summon("capweaver").n(3), a(ROOT, "Web").radius(3)).build());
        out.add(m("nightmare_explosion", "The Fuse", Theme.FEAR, Tier.NIGHTMARE, Model.CREEPER, "A hiss you can't find, and lines of fire to dodge").hp(22).dmg(4).spd(0.26).scale(2.0F).glow(0xFFFFFF).bar(BossBarColor.WHITE).arena(22)
                .intro("Hsssss.")
                .phase(1.0F, "Somewhere, a fuse.", a(RAIN, "Fuse Lines").n(4).bolt(BoltKind.FIRE), a(BURST, "Blast Ring").radius(10).inner(3).heavy())
                .phase(0.5F, "Shorter fuse.", a(RAIN, "Fuse Lines").n(6).bolt(BoltKind.FIRE).wind(26), a(BURST, "Blast Ring").radius(12).inner(3).heavy()).build());
        out.add(m("nightmare_generic", "The Faceless", Theme.FEAR, Tier.NIGHTMARE, Model.HUMAN, "No face: always in the corner of your eye").hp(22).dmg(4).spd(0.28).bar(BossBarColor.PINK).arena(22)
                .intro("It was standing there the whole time.")
                .phase(1.0F, "The Faceless turns.", a(BLINK, "Corner of Your Eye"), a(DARKNESS, "Lights Off"))
                .phase(0.5F, "It is closer.", a(DECOY, "Everywhere").n(2), a(BEAM, "Stare").bolt(BoltKind.DREAM).heavy(), a(BLINK, "Corner of Your Eye")).build());
    }
}
