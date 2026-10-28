package dev.awaken.mobs.def;

import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

import java.util.List;

import static dev.awaken.mobs.def.Ability.Kind.*;
import static dev.awaken.mobs.def.MobDef.P;
import static dev.awaken.mobs.def.MobDef.m;
import static dev.awaken.mobs.def.Trait.*;

/**
 * The fifteen gate themes: at least three normal creatures each, an elite, the Warden (the gate's "you've stayed too
 * long" hunter) and the Gate Lord. Keep each {@code m(...)} call on one line: tools/content/mobs_bestiary.py reads them.
 */
final class GateBestiary {
    private GateBestiary() {}

    private static Ability.Builder a(Ability.Kind k, String name) {
        return Ability.of(k, name);
    }

    static void define(List<MobDef> out) {
        bellCrypt(out);
        fungalHollow(out);
        emberMines(out);
        drownedArchive(out);
        clockworkFoundry(out);
        glassDesert(out);
        hive(out);
        overgrownTemple(out);
        lanternMarket(out);
        frostMonastery(out);
        mirrorPalace(out);
        precursorLab(out);
        nightmare(out);
        skyArchipelago(out);
        abyssalShrine(out);
    }

    private static void bellCrypt(List<MobDef> out) {
        out.add(m("toll_ghoul", "Toll Ghoul", Theme.BELL, Tier.NORMAL, Model.ZOMBIE, "Rings a hand-bell: a toll that slows everyone around it").hp(22).dmg(3).spd(0.23).hold(Items.BELL)
                .ab(a(SLAM, "Bell Toll").radius(3.5F).power(0.6F).fx(MobEffects.SLOWNESS, 40, 1).cd(140)).build());
        out.add(m("crypt_archer", "Crypt Archer", Theme.BELL, Tier.NORMAL, Model.SKELETON, "Mourning Volley: draws a long breath, then three arrows").hp(18).dmg(3).spd(0.24).hold(Items.BOW).traits(RANGED)
                .ab(a(ARROWS, "Mourning Volley").n(3).wind(22).cd(70)).build());
        out.add(m("bell_wisp", "Bell Wisp", Theme.BELL, Tier.NORMAL, Model.VEX, "Grave Chime: chimes, then dives straight at you").hp(10).dmg(3).spd(0.5).traits(HAUNTED)
                .ab(a(CHARGE, "Grave Chime").power(1.2F).wind(16).cd(90).range(12)).build());
        out.add(m("crypt_keeper", "Crypt Keeper", Theme.BELL, Tier.ELITE, Model.WITHER_SKELETON, "Bell Slam: lifts its blade over its head and brings the floor down").hp(24).dmg(5).spd(0.26).hold(Items.IRON_SWORD)
                .ab(a(SLAM, "Bell Slam").power(1.6F).heavy(), a(CHARGE, "Coffin Rush")).build());
        out.add(m("the_sexton", "The Sexton", Theme.BELL, Tier.WARDEN, Model.WARDEN, "Hunts by the toll of your footsteps; its Knell tears at the spirit").hp(34).dmg(6).spd(0.28).traits(HUNTER)
                .ab(a(BEAM, "Knell").bolt(BoltKind.SPIRIT).power(1.8F).wind(34).heavy(), a(SLAM, "Grave Stamp").radius(4), a(DARKNESS, "Last Light").radius(14)).build());
        out.add(m("bell_warden", "The Bell Warden", Theme.BELL, Tier.LORD, Model.BELL, "Toll Waves you dodge by stepping in; halfway through, the statues move").hp(30).dmg(4.5).spd(0.24).bar(BossBarColor.BLUE).arena(22)
                .intro("The bell above you is not a bell.")
                .phase(1.0F, "The Bell Warden rings once.", a(BURST, "Toll Wave").radius(10).inner(3.0F).power(1.6F), a(SLAM, "Clapper Strike").radius(4), a(BOLT, "Echo Bolts").n(3).bolt(BoltKind.SPIRIT))
                .phase(0.66F, "The statues move.", a(SUMMON, "The Statues Move").summon("toll_ghoul").n(3), a(CHARGE, "Bell Rush").heavy(), a(BURST, "Toll Wave").radius(11).inner(3.0F))
                .phase(0.33F, "The Grand Peal.", a(BURST, "Grand Peal").radius(16).inner(4).power(2.6F).wind(50).heavy(), a(NOVA, "Shattered Bronze").n(12).bolt(BoltKind.SPIRIT), a(SLAM, "Clapper Strike").radius(4))
                .parts(P("bell_clapper", "Warden's Clapper", "brass"), P("crypt_bronze", "Crypt Bronze", "brass")).build());
    }

    private static void fungalHollow(List<MobDef> out) {
        out.add(m("sporeling", "Sporeling", Theme.FUNGAL, Tier.NORMAL, Model.SLIME, "Hops at you and pops into a cloud of spores when it dies").hp(8).dmg(2).spd(0.3).scale(0.5F).traits(DEATH_CLOUD)
                .ab(a(LEAP, "Pounce").power(0.8F).radius(1.5F).wind(10).cd(80).range(6)).build());
        out.add(m("mycelid", "Mycelid Shambler", Theme.FUNGAL, Tier.NORMAL, Model.ZOMBIE, "Spore Cough: hunches, then a poison cone").hp(22).dmg(3).spd(0.22)
                .ab(a(CONE, "Spore Cough").power(0.4F).bolt(BoltKind.SPORE).fx(MobEffects.POISON, 60, 0).cd(130)).build());
        out.add(m("capweaver", "Capweaver", Theme.FUNGAL, Tier.NORMAL, Model.CAVE_SPIDER, "Web Spit: a marked circle that roots you if you stay in it").hp(14).dmg(2.5).spd(0.32)
                .ab(a(ROOT, "Web Spit").radius(1.6F).wind(20).cd(140).range(12)).build());
        out.add(m("puffcap", "Puffcap", Theme.FUNGAL, Tier.NORMAL, Model.CREEPER, "Swells and bursts into choking spores").hp(16).dmg(2).spd(0.24).traits(FUSE, DEATH_CLOUD).blast(1.6F).build());
        out.add(m("hollow_tender", "Hollow Tender", Theme.FUNGAL, Tier.ELITE, Model.WITCH, "Tends the colony: heals every fungus near it, lobs spores").hp(24).dmg(3).spd(0.24).traits(RANGED)
                .ab(a(HEAL, "Tend the Colony"), a(BOLT, "Spore Lob").bolt(BoltKind.SPORE), a(CLOUD, "Rot Bloom").fx(MobEffects.POISON, 80, 0)).build());
        out.add(m("the_rot", "The Rot", Theme.FUNGAL, Tier.WARDEN, Model.RAVAGER, "Rot Charge: lowers its head, then a charge that leaves spores behind").hp(36).dmg(6).spd(0.3).traits(HUNTER)
                .ab(a(CHARGE, "Rot Charge").power(1.6F).heavy(), a(NOVA, "Spore Burst").n(12).bolt(BoltKind.SPORE), a(CLOUD, "Choking Field").radius(4).fx(MobEffects.POISON, 100, 1)).build());
        out.add(m("mother_of_spores", "Mother of Spores", Theme.FUNGAL, Tier.LORD, Model.GHAST, "Spore Rain from above, then she births the colony").hp(28).dmg(4).spd(0.2).scale(0.75F).bar(BossBarColor.PURPLE).arena(24)
                .intro("Something above you breathes in.")
                .phase(1.0F, "Mother of Spores exhales.", a(RAIN, "Spore Rain").n(4).bolt(BoltKind.SPORE).fx(MobEffects.POISON, 60, 0), a(BOLT, "Spore Lob").n(3).bolt(BoltKind.SPORE))
                .phase(0.66F, "She births.", a(SUMMON, "Birth").summon("sporeling").n(4), a(NOVA, "Spore Halo").n(14).bolt(BoltKind.SPORE))
                .phase(0.33F, "The Bloom.", a(BURST, "Bloom").radius(14).inner(4).bolt(BoltKind.SPORE).fx(MobEffects.POISON, 100, 1).heavy(), a(RAIN, "Spore Rain").n(6).bolt(BoltKind.SPORE), a(SUMMON, "Birth").summon("sporeling").n(3))
                .parts(P("spore_sac", "Spore Sac", "fungal"), P("mother_cap", "Mother's Cap", "fungal")).build());
    }

    private static void emberMines(List<MobDef> out) {
        out.add(m("cinder_miner", "Cinder Miner", Theme.EMBER, Tier.NORMAL, Model.HUSK, "Ember Swing: an overhead pick strike that sets you alight").hp(22).dmg(3.5).spd(0.23).hold(Items.IRON_PICKAXE).traits(IGNITE)
                .ab(a(SLAM, "Ember Swing").radius(2.5F).power(1.4F).bolt(BoltKind.FIRE).wind(24).heavy()).build());
        out.add(m("slag_cube", "Slag Cube", Theme.EMBER, Tier.NORMAL, Model.MAGMA, "Molten Splash: lands in a ring of fire").hp(18).dmg(3).spd(0.28).traits(FIRE_IMMUNE, IGNITE)
                .ab(a(LEAP, "Molten Splash").radius(2.5F).power(1.2F).bolt(BoltKind.FIRE)).build());
        out.add(m("ember_imp", "Ember Imp", Theme.EMBER, Tier.NORMAL, Model.BLAZE, "Cinder Burst: three fire bolts after a crackle").hp(12).dmg(3).spd(0.3).scale(0.65F).traits(FIRE_IMMUNE, RANGED)
                .ab(a(BOLT, "Cinder Burst").n(3).bolt(BoltKind.FIRE)).build());
        out.add(m("scorch_crawler", "Scorch Crawler", Theme.EMBER, Tier.NORMAL, Model.SILVERFISH, "Comes out of the coal seams biting, and the bites burn").hp(10).dmg(2.5).spd(0.33).scale(1.7F).traits(IGNITE, FIRE_IMMUNE)
                .ab(a(LEAP, "Scorch Bite").radius(1.5F).power(0.9F).range(6).wind(10)).build());
        out.add(m("slag_boar", "Slag Boar", Theme.EMBER, Tier.ELITE, Model.HOGLIN, "Slag Charge: paws the ground, then charges through fire").hp(26).dmg(5).spd(0.3).traits(FIRE_IMMUNE)
                .ab(a(CHARGE, "Slag Charge").bolt(BoltKind.FIRE).heavy(), a(GUST, "Tusk Toss").radius(3.5F).power(0.8F)).build());
        out.add(m("the_overseer", "The Overseer", Theme.EMBER, Tier.WARDEN, Model.WITHER_SKELETON, "Walks through fire to reach you, ringed in embers").hp(34).dmg(6.5).spd(0.27).scale(1.35F).hold(Items.NETHERITE_AXE).traits(FIRE_IMMUNE, IGNITE, HUNTER)
                .ab(a(CHARGE, "Furnace Walk"), a(NOVA, "Ember Ring").n(10).bolt(BoltKind.FIRE), a(AURA, "Heat Haze").bolt(BoltKind.FIRE)).build());
        out.add(m("ember_tyrant", "The Ember Tyrant", Theme.EMBER, Tier.LORD, Model.PIGLIN_BRUTE, "Magma Fist and Eruption, then a Meltdown you have to step out of").hp(30).dmg(5).spd(0.25).scale(3.0F).hold(Items.NETHERITE_AXE).wear(EquipmentSlot.HEAD, Items.GOLDEN_HELMET).traits(FIRE_IMMUNE, IGNITE).bar(BossBarColor.RED).arena(22)
                .intro("The mine was dug to keep something in.")
                .phase(1.0F, "The Ember Tyrant draws itself up.", a(SLAM, "Magma Fist").radius(4).bolt(BoltKind.FIRE).heavy(), a(CHARGE, "Tyrant's Advance"))
                .phase(0.66F, "The mountain answers.", a(RAIN, "Eruption").n(5).bolt(BoltKind.FIRE), a(SUMMON, "Stoke the Imps").summon("ember_imp").n(1), a(SLAM, "Magma Fist").radius(4).bolt(BoltKind.FIRE))
                .phase(0.33F, "Meltdown.", a(AURA, "Meltdown").radius(7).bolt(BoltKind.FIRE).heavy(), a(RAIN, "Eruption").n(7).bolt(BoltKind.FIRE), a(CHARGE, "Tyrant's Advance"))
                .parts(P("tyrant_crown", "Tyrant's Crown", "brass"), P("molten_core", "Molten Core", "ember")).build());
    }

    private static void drownedArchive(List<MobDef> out) {
        out.add(m("ink_drowned", "Ink Drowned", Theme.DROWNED, Tier.NORMAL, Model.DROWNED, "Ink Spit: a black bolt that blinds").hp(20).dmg(3).spd(0.23).hold(Items.TRIDENT)
                .ab(a(BOLT, "Ink Spit").bolt(BoltKind.WATER).cd(80)).build());
        out.add(m("page_wraith", "Page Wraith", Theme.DROWNED, Tier.NORMAL, Model.VEX, "A swarm of wet pages that rustle, then cut").hp(9).dmg(2.5).spd(0.5).tint(0xE8E0C8)
                .ab(a(CHARGE, "Papercut").power(1.0F).wind(12).cd(70).range(10)).build());
        out.add(m("archive_eye", "Archive Eye", Theme.DROWNED, Tier.NORMAL, Model.GUARDIAN, "Reading Beam: locks on, glows, then burns").hp(18).dmg(3).spd(0.25).traits(RANGED)
                .ab(a(BEAM, "Reading Beam").bolt(BoltKind.WATER).power(1.6F)).build());
        out.add(m("salt_stray", "Salt Stray", Theme.DROWNED, Tier.NORMAL, Model.STRAY, "Brine Arrows that weigh you down").hp(18).dmg(3).spd(0.24).hold(Items.BOW).traits(RANGED, SLOW_TOUCH)
                .ab(a(ARROWS, "Brine Arrows").n(2).fx(MobEffects.SLOWNESS, 60, 1)).build());
        out.add(m("drowned_scribe", "Drowned Scribe", Theme.DROWNED, Tier.ELITE, Model.EVOKER, "Writes wraiths from torn pages; ink fangs rise along the floor").hp(24).dmg(4).spd(0.25).traits(RANGED)
                .ab(a(SUMMON, "Torn Pages").summon("page_wraith").n(2), a(FANGS, "Ink Fangs"), a(BOLT, "Ink Spit").bolt(BoltKind.WATER)).build());
        out.add(m("the_curator", "The Curator", Theme.DROWNED, Tier.WARDEN, Model.ELDER_GUARDIAN, "Shushes you with Mining Fatigue, then its beam reads you to death").hp(34).dmg(6).spd(0.22).scale(0.8F).traits(HUNTER, RANGED)
                .ab(a(BEAM, "Silence in the Stacks").bolt(BoltKind.WATER).power(2.0F).heavy(), a(DARKNESS, "Shush").fx(MobEffects.MINING_FATIGUE, 200, 2), a(NOVA, "Ink Tide").n(10).bolt(BoltKind.WATER)).build());
        out.add(m("drowned_archivist", "The Drowned Archivist", Theme.DROWNED, Tier.LORD, Model.DROWNED, "Tidal Page washes you back; the shelves empty; then the Flood").hp(30).dmg(4.5).spd(0.24).scale(1.9F).hold(Items.TRIDENT).offhand(Items.BOOK).bar(BossBarColor.BLUE).arena(22)
                .intro("Every book here was written by someone who drowned.")
                .phase(1.0F, "The Archivist opens a book.", a(CONE, "Tidal Page").radius(7).power(1.4F).bolt(BoltKind.WATER).heavy(), a(BOLT, "Ink Volley").n(3).bolt(BoltKind.WATER))
                .phase(0.66F, "The shelves empty.", a(SUMMON, "Loose Pages").summon("page_wraith").n(4), a(CLOUD, "Ink Cloud").radius(3).fx(MobEffects.BLINDNESS, 60, 0), a(CONE, "Tidal Page").radius(7).bolt(BoltKind.WATER))
                .phase(0.33F, "The Flood.", a(AURA, "Flood").radius(8).bolt(BoltKind.WATER).fx(MobEffects.SLOWNESS, 40, 1).heavy(), a(PULL, "Undertow").radius(12), a(CONE, "Tidal Page").radius(8).bolt(BoltKind.WATER))
                .parts(P("archivist_quill", "Archivist's Quill", "water"), P("waterlogged_codex", "Waterlogged Codex", "water")).build());
    }

    private static void clockworkFoundry(List<MobDef> out) {
        out.add(m("cog_hound", "Cog Hound", Theme.CLOCKWORK, Tier.NORMAL, Model.WOLF, "Ratchet Pounce: winds itself up with a clatter, then leaps").hp(16).dmg(3).spd(0.34)
                .ab(a(LEAP, "Ratchet Pounce").radius(1.8F)).build());
        out.add(m("rivet_gunner", "Rivet Gunner", Theme.CLOCKWORK, Tier.NORMAL, Model.PILLAGER, "Rivet Burst: three hot rivets from a crossbow").hp(18).dmg(3).spd(0.26).hold(Items.CROSSBOW).traits(RANGED)
                .ab(a(BOLT, "Rivet Burst").n(3).bolt(BoltKind.PLAIN).power(0.8F)).build());
        out.add(m("spark_drone", "Spark Drone", Theme.CLOCKWORK, Tier.NORMAL, Model.BEE, "Spark Sting: buzzes louder, then a sting that stuns").hp(9).dmg(2).spd(0.5).traits(SLOW_TOUCH)
                .ab(a(CHARGE, "Spark Sting").bolt(BoltKind.CHARGE).wind(14).cd(80).range(10)).build());
        out.add(m("foundry_marshal", "Foundry Marshal", Theme.CLOCKWORK, Tier.ELITE, Model.VINDICATOR, "Steam Charge, then a Piston Slam where it stops").hp(26).dmg(5).spd(0.28).hold(Items.IRON_AXE)
                .ab(a(CHARGE, "Steam Charge").heavy(), a(SLAM, "Piston Slam")).build());
        out.add(m("the_regulator", "The Regulator", Theme.CLOCKWORK, Tier.WARDEN, Model.IRON_GOLEM, "Overclocks as it nears you; Governor Slam and a spark ring").hp(38).dmg(7).spd(0.27).tint(0xE6B860).traits(HUNTER)
                .ab(a(SLAM, "Governor Slam").radius(4).heavy(), a(NOVA, "Spark Ring").n(12).bolt(BoltKind.CHARGE), a(FRENZY, "Overclock")).build());
        out.add(m("gearwright_colossus", "The Gearwright Colossus", Theme.CLOCKWORK, Tier.LORD, Model.IRON_GOLEM, "Piston Slams and spark halos; at the end an Overload arc").hp(34).dmg(5).spd(0.22).scale(1.8F).tint(0xF0C060).bar(BossBarColor.YELLOW).arena(24)
                .intro("The foundry built one thing. It is still building it.")
                .phase(1.0F, "The Colossus engages.", a(SLAM, "Piston Slam").radius(4.5F).heavy(), a(NOVA, "Spark Halo").n(12).bolt(BoltKind.CHARGE))
                .phase(0.66F, "Gears engage.", a(SUMMON, "Release Drones").summon("spark_drone").n(3), a(CHARGE, "Colossal Stride"), a(SLAM, "Piston Slam").radius(4.5F))
                .phase(0.33F, "Overload.", a(BEAM, "Overload Arc").bolt(BoltKind.CHARGE).power(2.2F).heavy(), a(RAIN, "Arc Strikes").n(6).bolt(BoltKind.CHARGE), a(SLAM, "Piston Slam").radius(5))
                .parts(P("colossus_gear", "Colossus Gear", "brass"), P("mainspring", "Mainspring", "brass")).build());
    }

    private static void glassDesert(List<MobDef> out) {
        out.add(m("glass_husk", "Glass Husk", Theme.GLASS, Tier.NORMAL, Model.HUSK, "Sandblast blinds; shatters into shards when slain").hp(20).dmg(3).spd(0.23).traits(DEATH_SHARDS)
                .ab(a(CONE, "Sandblast").power(0.6F).bolt(BoltKind.GLASS).fx(MobEffects.BLINDNESS, 30, 0)).build());
        out.add(m("sand_stalker", "Sand Stalker", Theme.GLASS, Tier.NORMAL, Model.SPIDER, "Burrows and bursts up beside you").hp(16).dmg(3).spd(0.32).tint(0xE8D098)
                .ab(a(BLINK, "Burrow Strike")).build());
        out.add(m("heat_shimmer", "Heat Shimmer", Theme.GLASS, Tier.NORMAL, Model.BREEZE, "Mirage Gust: a wall of hot air, then glass on the wind").hp(16).dmg(3).spd(0.28).traits(RANGED)
                .ab(a(GUST, "Mirage Gust").radius(5).power(0.4F), a(BOLT, "Glass Gust").bolt(BoltKind.GLASS)).build());
        out.add(m("prism_sentinel", "Prism Sentinel", Theme.GLASS, Tier.ELITE, Model.ENDERMAN, "Refracts: teleports beside you, then lances light").hp(26).dmg(5).spd(0.3)
                .ab(a(BLINK, "Refract"), a(BEAM, "Prism Lance").bolt(BoltKind.GLASS)).build());
        out.add(m("the_mirage", "The Mirage", Theme.GLASS, Tier.WARDEN, Model.ILLUSIONER, "Splits into mirages; only one of them casts a shadow").hp(32).dmg(5.5).spd(0.28).traits(HUNTER, RANGED)
                .ab(a(DECOY, "Mirages").n(3), a(BOLT, "Sunshards").n(5).bolt(BoltKind.GLASS), a(BLINK, "Heat Mirage")).build());
        out.add(m("glass_serpent", "The Glass Serpent", Theme.GLASS, Tier.LORD, Model.SERPENT, "Lunges, dives into the sand and erupts beneath you, then the Glass Storm").hp(32).dmg(5).spd(0.3).scale(2.2F).bar(BossBarColor.WHITE).arena(26)
                .intro("Your reflection blinks first.")
                .phase(1.0F, "The Glass Serpent uncoils.", a(CHARGE, "Glass Lunge").heavy(), a(NOVA, "Shard Spray").n(10).bolt(BoltKind.GLASS))
                .phase(0.66F, "It dives into the sand.", a(BLINK, "Burrow Strike").power(1.6F).heavy(), a(RAIN, "Erupting Glass").n(5).bolt(BoltKind.GLASS), a(CHARGE, "Glass Lunge"))
                .phase(0.33F, "Glass storm.", a(NOVA, "Glass Storm").n(20).bolt(BoltKind.GLASS).heavy(), a(CHARGE, "Glass Lunge"), a(RAIN, "Erupting Glass").n(6).bolt(BoltKind.GLASS))
                .parts(P("serpent_scale", "Glass Serpent Scale", "mirror"), P("sand_glass_fang", "Sandglass Fang", "sand")).build());
    }

    private static void hive(List<MobDef> out) {
        out.add(m("drone_bee", "Hive Drone", Theme.HIVE, Tier.NORMAL, Model.BEE, "Stinger Dive: hovers, buzzes, dives, poisons").hp(10).dmg(2.5).spd(0.5).scale(1.3F).traits(POISON_TOUCH)
                .ab(a(CHARGE, "Stinger Dive").wind(14).cd(80).range(10)).build());
        out.add(m("hive_larva", "Hive Larva", Theme.HIVE, Tier.NORMAL, Model.SILVERFISH, "Wriggles out of the comb in numbers").hp(6).dmg(2).spd(0.3).scale(1.4F).tint(0xFFE090)
                .ab(a(LEAP, "Wriggle").radius(1.2F).power(0.8F).range(5).wind(8).cd(60)).build());
        out.add(m("honey_slime", "Honey Slime", Theme.HIVE, Tier.NORMAL, Model.SLIME, "Sticky: every touch slows you").hp(16).dmg(2.5).spd(0.27).scale(0.9F).traits(SLOW_TOUCH)
                .ab(a(LEAP, "Sticky Landing").bolt(BoltKind.HONEY).fx(MobEffects.SLOWNESS, 60, 1)).build());
        out.add(m("hive_guard", "Hive Guard", Theme.HIVE, Tier.ELITE, Model.SPIDER, "Mandible Crush, and a wax seal that roots you").hp(24).dmg(5).spd(0.3).scale(1.25F).tint(0x806020)
                .ab(a(LEAP, "Mandible Crush").heavy(), a(ROOT, "Wax Seal").fx(MobEffects.SLOWNESS, 60, 3)).build());
        out.add(m("the_swarm_mind", "The Swarm Mind", Theme.HIVE, Tier.WARDEN, Model.PHANTOM, "Never alone: drones pour from it while honey falls").hp(30).dmg(5.5).spd(0.35).scale(2.2F).traits(HUNTER)
                .ab(a(SUMMON, "Call the Swarm").summon("drone_bee").n(3), a(CHARGE, "Swarm Dive"), a(RAIN, "Honey Drop").bolt(BoltKind.HONEY).fx(MobEffects.SLOWNESS, 60, 2)).build());
        out.add(m("hive_regent", "The Hive Regent", Theme.HIVE, Tier.LORD, Model.BEE, "Royal Dives and Honey Rain; at the end she feeds on royal jelly and frenzies").hp(28).dmg(4.5).spd(0.45).scale(4.0F).bar(BossBarColor.YELLOW).arena(22)
                .intro("A hum you feel in your teeth.")
                .phase(1.0F, "The Regent rises from the comb.", a(CHARGE, "Royal Dive").heavy(), a(SUMMON, "Brood").summon("drone_bee").n(3))
                .phase(0.66F, "The comb weeps.", a(RAIN, "Honey Rain").n(5).bolt(BoltKind.HONEY).fx(MobEffects.SLOWNESS, 60, 2), a(BOLT, "Stinger Volley").n(4).bolt(BoltKind.THORN), a(CHARGE, "Royal Dive"))
                .phase(0.33F, "Royal Jelly.", a(HEAL, "Royal Jelly").power(0.15F), a(FRENZY, "Frenzy"), a(SUMMON, "Brood").summon("drone_bee").n(3), a(CHARGE, "Royal Dive").heavy())
                .parts(P("regent_stinger", "Regent's Stinger", "amber"), P("royal_jelly", "Royal Jelly", "amber")).build());
    }

    private static void overgrownTemple(List<MobDef> out) {
        out.add(m("vine_creaker", "Vine Creaker", Theme.TEMPLE, Tier.NORMAL, Model.CREAKING, "Only moves while you aren't looking at it").hp(22).dmg(4).spd(0.3).traits(WATCHED_FREEZE)
                .ab(a(ROOT, "Root Grasp").radius(1.8F)).build());
        out.add(m("thorn_archer", "Thorn Archer", Theme.TEMPLE, Tier.NORMAL, Model.BOGGED, "Thorn Arrows that poison").hp(18).dmg(3).spd(0.24).hold(Items.BOW).traits(RANGED, POISON_TOUCH)
                .ab(a(ARROWS, "Thorn Arrows").n(2).fx(MobEffects.POISON, 60, 0)).build());
        out.add(m("thorn_hound", "Thorn Hound", Theme.TEMPLE, Tier.NORMAL, Model.WOLF, "Bramble Pounce out of the undergrowth").hp(16).dmg(3).spd(0.34).tint(0x80B060)
                .ab(a(LEAP, "Bramble Pounce").radius(1.8F)).build());
        out.add(m("incense_priest", "Incense Priest", Theme.TEMPLE, Tier.ELITE, Model.EVOKER, "Root Fangs along the floor; incense that heals its flock").hp(24).dmg(4).spd(0.25).tint(0x9CCC88).traits(RANGED)
                .ab(a(FANGS, "Root Fangs"), a(HEAL, "Incense"), a(CLOUD, "Incense Smoke").fx(MobEffects.WEAKNESS, 80, 0)).build());
        out.add(m("the_overgrowth", "The Overgrowth", Theme.TEMPLE, Tier.WARDEN, Model.IRON_GOLEM, "A temple statue grown through with roots; rings of fangs").hp(38).dmg(7).spd(0.25).tint(0x8AB070).traits(HUNTER)
                .ab(a(SLAM, "Temple Fist").radius(4).heavy(), a(FANGS_RING, "Root Ring").n(10), a(ROOT, "Strangling Vines").radius(2.5F)).build());
        out.add(m("root_saint", "The Root Saint", Theme.TEMPLE, Tier.LORD, Model.CREAKING, "Root Lances in lines, then the whole temple grabs for you").hp(30).dmg(4.5).spd(0.24).scale(1.8F).bar(BossBarColor.GREEN).arena(22)
                .intro("Incense and wet roots.")
                .phase(1.0F, "The Root Saint opens its eyes.", a(FANGS, "Root Lance").n(14), a(SLAM, "Saint's Palm").radius(4))
                .phase(0.66F, "The temple grows.", a(SUMMON, "Grow Creakers").summon("vine_creaker").n(2), a(ROOT, "Root Grasp").radius(3), a(FANGS_RING, "Root Ring").n(10))
                .phase(0.33F, "Thorn Halo.", a(NOVA, "Thorn Halo").n(16).bolt(BoltKind.THORN).heavy(), a(FANGS, "Root Lance").n(18), a(ROOT, "Root Grasp").radius(3))
                .parts(P("saint_heartwood", "Saint's Heartwood", "wood"), P("temple_resin", "Temple Resin", "amber")).build());
    }

    private static void lanternMarket(List<MobDef> out) {
        out.add(m("lantern_moth", "Lantern Moth", Theme.LANTERN, Tier.NORMAL, Model.PHANTOM, "Drawn to light; harmless until something hits it").hp(8).dmg(2).spd(0.4).scale(0.55F).traits(NEUTRAL)
                .ab(a(CHARGE, "Drawn to Light").power(0.8F).wind(12).cd(80).range(10)).build());
        out.add(m("pickpocket", "Market Pickpocket", Theme.LANTERN, Tier.NORMAL, Model.PILLAGER, "Snatches a shard and runs; catch it to get it back").hp(14).dmg(2).spd(0.33).traits(STEALER).build());
        out.add(m("wick_slime", "Wick Slime", Theme.LANTERN, Tier.NORMAL, Model.SLIME, "A slime with a candle in it; burns if provoked").hp(12).dmg(2).spd(0.26).scale(0.7F).traits(NEUTRAL, IGNITE)
                .ab(a(LEAP, "Wick Hop").bolt(BoltKind.FIRE)).build());
        out.add(m("masked_duelist", "Masked Duelist", Theme.LANTERN, Tier.ELITE, Model.PIGLIN, "Parries your swing, then a Fan Dance lunge").hp(24).dmg(5).spd(0.3).hold(Items.GOLDEN_SWORD).traits(NEUTRAL)
                .ab(a(GUARD, "Parry"), a(CHARGE, "Fan Dance")).build());
        out.add(m("the_lamplighter", "The Lamplighter", Theme.LANTERN, Tier.WARDEN, Model.ENDERMAN, "Steps between lamps and snuffs them out").hp(32).dmg(6).spd(0.3).traits(HUNTER)
                .ab(a(BLINK, "Between Lamps"), a(DARKNESS, "Snuff the Lamps"), a(BOLT, "Lamp Oil").n(3).bolt(BoltKind.FIRE)).build());
        out.add(m("lantern_broker", "The Lantern Broker", Theme.LANTERN, Tier.LORD, Model.LANTERN, "Trades with you; duels you only if you ask (sneak and use)").hp(28).dmg(4.5).spd(0.28).traits(FRIENDLY).bar(BossBarColor.YELLOW).arena(18)
                .intro("A duel, then. Lanterns down.")
                .phase(1.0F, "The Broker bows.", a(BOLT, "Lantern Bolts").n(3).bolt(BoltKind.FIRE), a(BLINK, "Market Step"))
                .phase(0.66F, "Prices are going up.", a(SUMMON, "Release the Moths").summon("lantern_moth").n(3), a(NOVA, "Paper Lanterns").n(10).bolt(BoltKind.FIRE))
                .phase(0.33F, "Lights out.", a(DARKNESS, "Lights Out").heavy(), a(BLINK, "Market Step"), a(BOLT, "Lantern Bolts").n(5).bolt(BoltKind.FIRE))
                .parts(P("broker_lantern", "Broker's Lantern", "amber"), P("gilded_ledger", "Gilded Ledger", "brass")).build());
    }

    private static void frostMonastery(List<MobDef> out) {
        out.add(m("rime_acolyte", "Rime Acolyte", Theme.FROST, Tier.NORMAL, Model.PILLAGER, "Frost Palm: a cold shove that slows").hp(20).dmg(3.5).spd(0.26).traits(SLOW_TOUCH)
                .ab(a(GUST, "Frost Palm").radius(3).power(0.8F).bolt(BoltKind.FROST).fx(MobEffects.SLOWNESS, 60, 1)).build());
        out.add(m("hail_stray", "Hail Stray", Theme.FROST, Tier.NORMAL, Model.STRAY, "Hail Volley: three chilling arrows").hp(18).dmg(3).spd(0.24).hold(Items.BOW).traits(RANGED)
                .ab(a(ARROWS, "Hail Volley").n(3).fx(MobEffects.SLOWNESS, 60, 0)).build());
        out.add(m("frost_wisp", "Frost Wisp", Theme.FROST, Tier.NORMAL, Model.BREEZE, "Blizzard Bolts on the wind").hp(16).dmg(3).spd(0.28).tint(0xC0E8FF).traits(RANGED)
                .ab(a(BOLT, "Blizzard Bolt").n(2).bolt(BoltKind.FROST), a(GUST, "Gale").radius(4).power(0.3F)).build());
        out.add(m("ice_hound", "Ice Hound", Theme.FROST, Tier.NORMAL, Model.SNOW_WOLF, "Ice Pounce; its bite slows").hp(16).dmg(3).spd(0.35).traits(SLOW_TOUCH)
                .ab(a(LEAP, "Ice Pounce").radius(1.8F).bolt(BoltKind.FROST)).build());
        out.add(m("chant_master", "Chant Master", Theme.FROST, Tier.ELITE, Model.EVOKER, "Chants up a wisp; ice spikes under your feet").hp(24).dmg(4).spd(0.25).tint(0xC8E8FF).traits(RANGED)
                .ab(a(SUMMON, "Chant").summon("frost_wisp").n(1), a(NOVA, "Frost Nova").n(10).bolt(BoltKind.FROST), a(RAIN, "Ice Spikes").n(3).bolt(BoltKind.FROST)).build());
        out.add(m("the_yeti", "The Yeti", Theme.FROST, Tier.WARDEN, Model.POLAR_BEAR, "Avalanche Slam and freezing breath").hp(38).dmg(7).spd(0.3).scale(1.4F).traits(HUNTER)
                .ab(a(SLAM, "Avalanche Slam").radius(4).bolt(BoltKind.FROST).heavy(), a(CHARGE, "Snow Charge"), a(CONE, "Frost Breath").bolt(BoltKind.FROST).fx(MobEffects.SLOWNESS, 60, 2)).build());
        out.add(m("abbot_of_frost", "The Abbot of Frost", Theme.FROST, Tier.LORD, Model.EVOKER, "Frost Hymns and Ice Spikes; the choir joins; then the Glacial Vow").hp(28).dmg(4.5).spd(0.25).scale(1.7F).tint(0xD8F0FF).bar(BossBarColor.WHITE).arena(22)
                .intro("Chanting above a sea of clouds.")
                .phase(1.0F, "The Abbot begins the hymn.", a(BOLT, "Frost Hymn").n(5).bolt(BoltKind.FROST), a(RAIN, "Ice Spikes").n(4).bolt(BoltKind.FROST))
                .phase(0.66F, "The choir joins.", a(SUMMON, "The Choir").summon("frost_wisp").n(2), a(AURA, "Blizzard").radius(9).bolt(BoltKind.FROST).fx(MobEffects.SLOWNESS, 40, 1), a(RAIN, "Ice Spikes").n(5).bolt(BoltKind.FROST))
                .phase(0.33F, "Glacial Vow.", a(BURST, "Glacial Vow").radius(14).inner(4).bolt(BoltKind.FROST).heavy(), a(RAIN, "Ice Spikes").n(6).bolt(BoltKind.FROST), a(NOVA, "Frost Nova").n(14).bolt(BoltKind.FROST))
                .parts(P("abbot_prayer_beads", "Abbot's Prayer Beads", "frost"), P("glacier_heart", "Glacier Heart", "frost")).build());
    }

    private static void mirrorPalace(List<MobDef> out) {
        out.add(m("mirror_shade", "Mirror Shade", Theme.MIRROR, Tier.NORMAL, Model.MIMIC, "Wears your face and steps out behind you").hp(18).dmg(3.5).spd(0.28).traits(MIMIC, HAUNTED)
                .ab(a(BLINK, "Behind You")).build());
        out.add(m("silver_knight", "Silver Knight", Theme.MIRROR, Tier.NORMAL, Model.ZOMBIE, "Parries: raises its blade, then punishes the swing").hp(22).dmg(4).spd(0.24).armor(4).tint(0xD8E0F0).hold(Items.IRON_SWORD).wear(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE).wear(EquipmentSlot.HEAD, Items.IRON_HELMET)
                .ab(a(GUARD, "Parry")).build());
        out.add(m("glint_mite", "Glint Mite", Theme.MIRROR, Tier.NORMAL, Model.ENDERMITE, "Jumps from reflection to reflection").hp(8).dmg(2).spd(0.33).scale(2.2F)
                .ab(a(BLINK, "Glint").power(0.8F).cd(90)).build());
        out.add(m("glass_duelist", "Glass Duelist", Theme.MIRROR, Tier.ELITE, Model.SKELETON, "Ripostes your blows and sends your arrows back").hp(24).dmg(5).spd(0.28).hold(Items.IRON_SWORD).traits(REFLECT)
                .ab(a(GUARD, "Riposte"), a(CHARGE, "Mirror Lunge")).build());
        out.add(m("the_unblinking", "The Unblinking", Theme.MIRROR, Tier.WARDEN, Model.ENDERMAN, "Never blinks: teleports when you look away, gaze like a blade").hp(34).dmg(6.5).spd(0.3).scale(1.2F).tint(0xE0E8F8).traits(HUNTER)
                .ab(a(BLINK, "Never Blinks"), a(BEAM, "Silver Gaze").bolt(BoltKind.GLASS), a(DECOY, "Reflections").n(2)).build());
        out.add(m("mirror_knight", "The Mirror Knight", Theme.MIRROR, Tier.LORD, Model.MIMIC, "Wears your face, parries perfectly, splits into reflections").hp(30).dmg(5).spd(0.28).scale(1.4F).hold(Items.DIAMOND_SWORD).wear(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE).wear(EquipmentSlot.HEAD, Items.DIAMOND_HELMET).traits(MIMIC, REFLECT).bar(BossBarColor.WHITE).arena(22)
                .intro("Footsteps that match yours.")
                .phase(1.0F, "The Mirror Knight raises your blade.", a(GUARD, "Perfect Parry"), a(CHARGE, "Mirror Lunge"))
                .phase(0.66F, "It splits.", a(DECOY, "Mirror Split").n(3), a(BLINK, "Through the Glass"), a(CHARGE, "Mirror Lunge"))
                .phase(0.33F, "The palace shatters.", a(NOVA, "Shatter").n(18).bolt(BoltKind.GLASS).heavy(), a(DECOY, "Mirror Split").n(2), a(GUARD, "Perfect Parry"), a(CHARGE, "Mirror Lunge"))
                .parts(P("mirror_shard_crown", "Shard Crown", "mirror"), P("knight_reflection", "Captured Reflection", "mirror")).build());
    }

    private static void precursorLab(List<MobDef> out) {
        out.add(m("test_subject", "Test Subject", Theme.PRECURSOR, Tier.NORMAL, Model.ZOMBIE, "Overcharged: bursts into sparks when killed").hp(22).dmg(3).spd(0.24).traits(DEATH_SHARDS)
                .ab(a(CHARGE, "Lurch").power(1.2F).cd(110)).build());
        out.add(m("security_unit", "Security Unit", Theme.PRECURSOR, Tier.NORMAL, Model.IRON_GOLEM, "Dormant until you come close; then a Scan Lance").hp(20).dmg(4).spd(0.25).scale(0.6F).tint(0xA8C8E8).traits(DORMANT)
                .ab(a(BEAM, "Scan Lance").bolt(BoltKind.CHARGE).power(1.4F)).build());
        out.add(m("coil_unit", "Coil Unit", Theme.PRECURSOR, Tier.NORMAL, Model.BLAZE, "Coil Bolts, and a discharge if you crowd it").hp(14).dmg(3).spd(0.28).tint(0x9CC8FF).traits(RANGED)
                .ab(a(BOLT, "Coil Bolt").n(2).bolt(BoltKind.CHARGE), a(NOVA, "Discharge").n(8).bolt(BoltKind.CHARGE).range(5)).build());
        out.add(m("gel_sample", "Gel Sample", Theme.PRECURSOR, Tier.ELITE, Model.SLIME, "Containment Breach: splits in two when killed").hp(26).dmg(4).spd(0.26).scale(1.6F).tint(0x9CE8FF).traits(SPLITS)
                .ab(a(LEAP, "Containment Breach").radius(3).heavy()).build());
        out.add(m("the_sweeper", "The Sweeper", Theme.PRECURSOR, Tier.WARDEN, Model.RAVAGER, "Purge Protocol: a charge, a sweep, a decontamination blast").hp(38).dmg(7).spd(0.3).tint(0x9CC0F0).traits(HUNTER)
                .ab(a(CHARGE, "Purge Protocol").heavy(), a(NOVA, "Sweep").n(12).bolt(BoltKind.CHARGE), a(GUST, "Decon Blast").radius(6)).build());
        out.add(m("unit_zero", "Unit Zero", Theme.PRECURSOR, Tier.LORD, Model.HUMAN, "Counts down in no language: lances, orbital marks, Zero Point").hp(28).dmg(4.5).spd(0.3).scale(1.65F).bar(BossBarColor.BLUE).arena(22)
                .intro("A voice counting down in no language.")
                .phase(1.0F, "Unit Zero activates.", a(BEAM, "Zero Lance").bolt(BoltKind.CHARGE), a(BLINK, "Phase Step"))
                .phase(0.66F, "Countdown: three.", a(SUMMON, "Deploy Coils").summon("coil_unit").n(2), a(RAIN, "Orbital Mark").n(4).bolt(BoltKind.CHARGE), a(BEAM, "Zero Lance").bolt(BoltKind.CHARGE))
                .phase(0.33F, "Countdown: zero.", a(BURST, "Zero Point").radius(14).inner(3.5F).bolt(BoltKind.CHARGE).heavy(), a(BEAM, "Zero Lance").bolt(BoltKind.CHARGE), a(BLINK, "Phase Step"))
                .parts(P("zero_core", "Zero Core", "signal"), P("zero_plating", "Zero Plating", "precursor")).build());
    }

    private static void nightmare(List<MobDef> out) {
        out.add(m("sleepwalker", "Sleepwalker", Theme.NIGHTMARE, Tier.NORMAL, Model.ZOMBIE, "Moves only when you blink: steps from nowhere").hp(20).dmg(3.5).spd(0.24).traits(HAUNTED)
                .ab(a(BLINK, "Sleepstep").cd(140)).build());
        out.add(m("night_terror", "Night Terror", Theme.NIGHTMARE, Tier.NORMAL, Model.PHANTOM, "Night Dive from somewhere above").hp(14).dmg(3).spd(0.45)
                .ab(a(CHARGE, "Night Dive").wind(14).cd(90).range(14)).build());
        out.add(m("many_eyes", "Many Eyes", Theme.NIGHTMARE, Tier.NORMAL, Model.SPIDER, "Staring: every eye opens and you slow").hp(18).dmg(3).spd(0.3).tint(0xC090C0)
                .ab(a(DARKNESS, "Staring").radius(8).fx(MobEffects.SLOWNESS, 60, 1).cd(200)).build());
        out.add(m("the_tall_one", "The Tall One", Theme.NIGHTMARE, Tier.ELITE, Model.ENDERMAN, "Doorways: it is always in the one behind you").hp(26).dmg(5).spd(0.3).scale(1.35F)
                .ab(a(BLINK, "Doorway"), a(DARKNESS, "Lights Off")).build());
        out.add(m("the_waking", "The Waking", Theme.NIGHTMARE, Tier.WARDEN, Model.WARDEN, "A wake-up call that tears at the spirit").hp(36).dmg(6.5).spd(0.28).traits(HUNTER)
                .ab(a(BEAM, "Wake-Up Call").bolt(BoltKind.DREAM).power(1.9F).wind(34).heavy(), a(SLAM, "Thrash").radius(4), a(SUMMON, "Bad Dreams").summon("night_terror").n(2)).build());
        out.add(m("the_sleeper", "The Sleeper", Theme.NIGHTMARE, Tier.LORD, Model.ZOMBIE, "Sleepwalks between dream and waking; when it wakes, everything does").hp(30).dmg(4.5).spd(0.24).scale(2.2F).traits(HAUNTED).bar(BossBarColor.PINK).arena(22)
                .intro("You've been here before. You haven't.")
                .phase(1.0F, "The Sleeper stirs.", a(SLAM, "Restless Fist").radius(4.5F), a(DARKNESS, "Heavy Eyelids"))
                .phase(0.66F, "It turns over in its sleep.", a(SUMMON, "Night Terrors").summon("night_terror").n(2), a(BLINK, "Dreamstep"), a(SLAM, "Restless Fist").radius(4.5F))
                .phase(0.33F, "It wakes.", a(BURST, "Rude Awakening").radius(14).inner(3.5F).bolt(BoltKind.DREAM).heavy(), a(SUMMON, "Sleepwalkers").summon("sleepwalker").n(2), a(SLAM, "Restless Fist").radius(5))
                .parts(P("sleeper_eyelid", "Sleeper's Eyelid", "dream"), P("sleepers_lullaby", "Sleeper's Lullaby", "dream")).build());
    }

    private static void skyArchipelago(List<MobDef> out) {
        out.add(m("gale_harpy", "Gale Harpy", Theme.SKY, Tier.NORMAL, Model.PHANTOM, "Dive Bomb from the clouds").hp(14).dmg(3).spd(0.45).tint(0xF0F4FF)
                .ab(a(CHARGE, "Dive Bomb").wind(14).cd(90).range(14)).build());
        out.add(m("cloud_breeze", "Cloud Breeze", Theme.SKY, Tier.NORMAL, Model.BREEZE, "Updraft: blows you towards the edge").hp(16).dmg(3).spd(0.28).traits(RANGED)
                .ab(a(GUST, "Updraft").radius(5).power(0.6F), a(BOLT, "Wind Bolt").bolt(BoltKind.GRAVITY)).build());
        out.add(m("sky_raider", "Sky Raider", Theme.SKY, Tier.NORMAL, Model.PILLAGER, "Grav-Bolts that lift you off your feet").hp(18).dmg(3).spd(0.26).hold(Items.CROSSBOW).traits(RANGED)
                .ab(a(BOLT, "Grav-Bolt").bolt(BoltKind.GRAVITY)).build());
        out.add(m("thunderhead", "Thunderhead", Theme.SKY, Tier.ELITE, Model.GHAST, "Calls lightning onto marked circles").hp(22).dmg(4).spd(0.25).scale(0.35F).tint(0xC8D0E8).traits(RANGED)
                .ab(a(RAIN, "Lightning Call").n(2).bolt(BoltKind.CHARGE), a(BOLT, "Static").n(2).bolt(BoltKind.CHARGE)).build());
        out.add(m("the_gale", "The Gale", Theme.SKY, Tier.WARDEN, Model.BREEZE, "Gale Force and updraft traps over the void").hp(32).dmg(6).spd(0.3).scale(1.8F).traits(HUNTER)
                .ab(a(GUST, "Gale Force").radius(8).power(0.8F).heavy(), a(LIFT, "Updraft Trap"), a(BOLT, "Wind Spears").n(4).bolt(BoltKind.GRAVITY)).build());
        out.add(m("sky_leviathan", "The Sky Leviathan", Theme.SKY, Tier.LORD, Model.SERPENT, "Dives through the sky, tilts gravity, then the Storm Eye").hp(34).dmg(5).spd(0.32).scale(3.2F).bar(BossBarColor.BLUE).arena(28)
                .intro("The ground is much too far below.")
                .phase(1.0F, "The Sky Leviathan circles.", a(CHARGE, "Leviathan Dive").heavy(), a(GUST, "Wingbeat").radius(10).power(0.6F))
                .phase(0.66F, "The sky tilts.", a(LIFT, "Gravity Tide").radius(4), a(BOLT, "Pearl Barrage").n(6).bolt(BoltKind.GRAVITY), a(CHARGE, "Leviathan Dive"))
                .phase(0.33F, "Storm Eye.", a(RAIN, "Storm Eye").n(7).bolt(BoltKind.CHARGE).heavy(), a(CHARGE, "Leviathan Dive"), a(GUST, "Wingbeat").radius(10).power(0.7F))
                .parts(P("leviathan_fin", "Leviathan Fin", "cloud"), P("sky_pearl", "Sky Pearl", "signal")).build());
    }

    private static void abyssalShrine(List<MobDef> out) {
        out.add(m("abyss_watcher", "Abyss Watcher", Theme.ABYSS, Tier.NORMAL, Model.GUARDIAN, "Hollow Gaze: a beam that drains the spirit").hp(18).dmg(3.5).spd(0.25).traits(RANGED)
                .ab(a(BEAM, "Hollow Gaze").bolt(BoltKind.VOID)).build());
        out.add(m("silent_stalker", "Silent Stalker", Theme.ABYSS, Tier.NORMAL, Model.ENDERMAN, "Makes no sound at all; steps out beside you").hp(24).dmg(4).spd(0.3)
                .ab(a(BLINK, "Soundless Step")).build());
        out.add(m("void_spawn", "Void Spawn", Theme.ABYSS, Tier.NORMAL, Model.ENDERMITE, "Pours out of the dark and bites").hp(10).dmg(2.5).spd(0.3).scale(2.6F)
                .ab(a(LEAP, "Void Bite").radius(1.5F).power(0.9F).range(6).wind(10)).build());
        out.add(m("hollow_priest", "Hollow Priest", Theme.ABYSS, Tier.ELITE, Model.WITHER_SKELETON, "Draws you in, then Hollow Bolts that wither").hp(26).dmg(5).spd(0.26).hold(Items.STONE_SWORD).traits(WITHER_TOUCH)
                .ab(a(BOLT, "Hollow Bolt").n(3).bolt(BoltKind.VOID), a(PULL, "Draw In")).build());
        out.add(m("the_nothing", "The Nothing", Theme.ABYSS, Tier.WARDEN, Model.WITHER, "Three heads of nothing; an event horizon that pulls").hp(38).dmg(6.5).spd(0.28).scale(0.6F).traits(HUNTER)
                .ab(a(BOLT, "Nothing Skulls").n(3).bolt(BoltKind.VOID), a(PULL, "Event Horizon").power(0.7F).heavy(), a(DARKNESS, "Unlight")).build());
        out.add(m("hollow_herald", "The Hollow Herald", Theme.ABYSS, Tier.LORD, Model.SHADE, "Void bolts, an unlit shrine, then the Event Horizon").hp(32).dmg(5).spd(0.28).scale(1.4F).bar(BossBarColor.PURPLE).arena(24)
                .intro("Nothing. No sound at all.")
                .phase(1.0F, "The Herald speaks without sound.", a(BOLT, "Void Bolts").n(5).bolt(BoltKind.VOID), a(PULL, "Draw Near"))
                .phase(0.66F, "The shrine empties.", a(SUMMON, "Spawn of Nothing").summon("void_spawn").n(3), a(DARKNESS, "Unlit"), a(BOLT, "Void Bolts").n(5).bolt(BoltKind.VOID))
                .phase(0.33F, "Event horizon.", a(PULL, "Event Horizon").radius(16).power(0.8F).heavy(), a(BURST, "Collapse").radius(13).inner(3.5F).bolt(BoltKind.VOID), a(NOVA, "Void Halo").n(16).bolt(BoltKind.VOID))
                .parts(P("herald_halo", "Herald's Halo", "abyss"), P("abyssal_glass", "Abyssal Glass", "abyss")).build());
    }
}
