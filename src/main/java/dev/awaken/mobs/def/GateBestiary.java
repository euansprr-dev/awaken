package dev.awaken.mobs.def;

import dev.awaken.core.api.BodyMaterial;
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
        out.add(m("toll_ghoul", "The Toller", Theme.BELL, Tier.NORMAL, Model.ZOMBIE, "Its bell swings up before it tolls and slows everyone near: strike it then and the toll is lost").hp(25.3).dmg(3).spd(0.23).rig().size(0.8F, 1.7F).stopped().open(30).fall(26)
                .ab(a(SLAM, "Bell Toll").radius(3.5F).power(0.6F).fx(MobEffects.SLOWNESS, 40, 1).cd(140)).build());
        out.add(m("crypt_archer", "The Mourner", Theme.BELL, Tier.NORMAL, Model.SKELETON, "It draws a long breath, its ribs open like wings and three of them fly; open, it cannot turn").hp(20.7).dmg(3).spd(0.24).traits(RANGED).rig().shot(ShotLook.RIB, 1.5F, 0.4F).size(0.7F, 2.25F).planted().open(24).fall(24)
                .ab(a(ARROWS, "Mourning Volley").n(3).wind(22).cd(70)).build());
        out.add(m("bell_wisp", "The Chime", Theme.BELL, Tier.NORMAL, Model.VEX, "It tips its mouth at you, then dives; a dive that misses leaves it ringing on the floor").hp(11.5).dmg(3).spd(0.5).traits(HAUNTED).rig().size(0.5F, 1.1F).miss(34)
                .ab(a(CHARGE, "Grave Chime").power(1.2F).wind(16).cd(90).range(12)).build());
        out.add(m("crypt_keeper", "The Pallbearer", Theme.BELL, Tier.ELITE, Model.WITHER_SKELETON, "Blows on its lid do nothing; it heaves itself up and drops on you, then lies with its open back to you").body(BodyMaterial.STONE).hp(27.6).dmg(5).spd(0.26).rig().size(1.0F, 2.2F).front().open(36).miss(30).fall(30)
                .ab(a(SLAM, "Bell Slam").power(1.6F).heavy(), a(CHARGE, "Coffin Rush")).build());
        out.add(m("the_sexton", "The Sexton", Theme.BELL, Tier.WARDEN, Model.WARDEN, "It hunts by sound; when it snuffs its lantern the room goes dark and its spade comes down where it last heard you").hp(39.1).dmg(6).spd(0.28).traits(HUNTER).rig().size(1.1F, 2.7F).open(28).fall(36)
                .ab(a(BEAM, "Knell").bolt(BoltKind.SPIRIT).power(1.37F).wind(34).heavy(), a(SLAM, "Grave Stamp").power(1.3F).radius(4), a(DARKNESS, "Last Light").radius(14)).build());
        out.add(m("bell_warden", "The Bell Warden", Theme.BELL, Tier.LORD, Model.BELL, "Toll Waves you dodge by stepping in; halfway through, the statues move").body(BodyMaterial.ARMOR).hp(94.4).dmg(1.4).spd(0.24).size(2.8F, 4.64F).bar(BossBarColor.BLUE).arena(22)
                .intro("The bell above you is not a bell.")
                .phase(1.0F, "The Bell Warden rings once.", a(BURST, "Toll Wave").radius(10).inner(3.0F).power(1.6F), a(SLAM, "Clapper Strike").radius(4), a(BOLT, "Echo Bolts").n(3).bolt(BoltKind.SPIRIT))
                .phase(0.66F, "The statues move.", a(SUMMON, "The Statues Move").summon("toll_ghoul").n(2), a(CHARGE, "Bell Rush").heavy(), a(BURST, "Toll Wave").radius(11).inner(3.0F))
                .phase(0.33F, "The Grand Peal.", a(BURST, "Grand Peal").radius(16).inner(4).power(2.6F).wind(50).heavy(), a(NOVA, "Shattered Bronze").n(12).bolt(BoltKind.SPIRIT), a(SLAM, "Clapper Strike").radius(4))
                .parts(P("bell_clapper", "Warden's Clapper", "brass"), P("crypt_bronze", "Crypt Bronze", "brass")).build());
    }

    private static void fungalHollow(List<MobDef> out) {
        out.add(m("sporeling", "The Sporeling", Theme.FUNGAL, Tier.NORMAL, Model.SLIME, "A puffball that hops on three root-toes; it swells before it pounces and bursts into spores when it dies").hp(9.2).dmg(2).spd(0.3).traits(DEATH_CLOUD).rig().size(0.6F, 0.6F)
                .ab(a(LEAP, "Pounce").power(0.8F).radius(1.5F).wind(10).cd(80).range(6)).build());
        out.add(m("mycelid", "The Shelf", Theme.FUNGAL, Tier.NORMAL, Model.ZOMBIE, "A walking stack of bracket fungus; its shelves lift like gills before it coughs, and stay open a moment after").hp(25.3).dmg(3).spd(0.22).rig().size(0.8F, 2.0F).open(28).fall(24)
                .ab(a(CONE, "Spore Cough").power(0.4F).bolt(BoltKind.SPORE).fx(MobEffects.POISON, 60, 0).cd(130)).build());
        out.add(m("capweaver", "The Capweaver", Theme.FUNGAL, Tier.NORMAL, Model.CAVE_SPIDER, "A toadstool on eight thread legs that sits among the mushrooms; it lifts its rim to spit a web that roots you").hp(16.1).dmg(2.5).spd(0.32).traits(DORMANT).rig().size(0.9F, 0.55F).open(24)
                .ab(a(ROOT, "Web Spit").radius(1.6F).wind(20).cd(140).range(12)).build());
        out.add(m("puffcap", "The Puffcap", Theme.FUNGAL, Tier.NORMAL, Model.CREEPER, "A thin stalk under a cap that balloons as it comes; it bursts into choking spores").hp(18.4).dmg(2).spd(0.24).traits(FUSE, DEATH_CLOUD).blast(1.6F).rig().size(0.6F, 1.5F).build());
        out.add(m("hollow_tender", "The Tender", Theme.FUNGAL, Tier.ELITE, Model.WITCH, "It roots itself under its parasol to heal every fungus near it, and cannot turn while it does").hp(27.6).dmg(3).spd(0.24).traits(RANGED).rig().shot(ShotLook.SPORE, 1.8F, 0.4F).size(0.8F, 2.4F).planted().open(26).fall(28)
                .ab(a(HEAL, "Tend the Colony"), a(BOLT, "Spore Lob").bolt(BoltKind.SPORE), a(CLOUD, "Rot Bloom").fx(MobEffects.POISON, 80, 0)).build());
        out.add(m("the_rot", "The Rot", Theme.FUNGAL, Tier.WARDEN, Model.RAVAGER, "A fallen log that got up: it charges hollow end first and leaves spores behind; make it miss and it sticks").hp(41.4).dmg(6).spd(0.3).traits(HUNTER).rig().shot(ShotLook.SPORE, 0.9F, 0.0F).size(1.6F, 1.5F).open(26).miss(40).fall(32)
                .ab(a(CHARGE, "Rot Charge").power(1.14F).heavy(), a(NOVA, "Spore Burst").n(12).bolt(BoltKind.SPORE), a(CLOUD, "Choking Field").radius(4).fx(MobEffects.POISON, 100, 0)).build());
        out.add(m("mother_of_spores", "Mother of Spores", Theme.FUNGAL, Tier.LORD, Model.GHAST, "Spore Rain from above, then she births the colony").hp(66.1).dmg(1.06).spd(0.2).scale(0.75F).size(2.4F, 7.78F).bar(BossBarColor.PURPLE).arena(24)
                .intro("Something above you breathes in.")
                .phase(1.0F, "Mother of Spores exhales.", a(RAIN, "Spore Rain").n(4).bolt(BoltKind.SPORE).fx(MobEffects.POISON, 60, 0), a(BOLT, "Spore Lob").n(3).bolt(BoltKind.SPORE))
                .phase(0.66F, "She births.", a(SUMMON, "Birth").summon("sporeling").n(2), a(NOVA, "Spore Halo").n(14).bolt(BoltKind.SPORE))
                .phase(0.33F, "The Bloom.", a(BURST, "Bloom").radius(14).inner(4).bolt(BoltKind.SPORE).fx(MobEffects.POISON, 100, 0).heavy(), a(RAIN, "Spore Rain").n(6).bolt(BoltKind.SPORE), a(SUMMON, "Birth").summon("sporeling").n(2))
                .parts(P("spore_sac", "Spore Sac", "fungal"), P("mother_cap", "Mother's Cap", "fungal")).build());
    }

    private static void emberMines(List<MobDef> out) {
        out.add(m("cinder_miner", "The Pickhead", Theme.EMBER, Tier.NORMAL, Model.HUSK, "A hunched beast with an iron pick for a head: it rears and pecks the floor alight, and the pick sticks").hp(25.3).dmg(3.5).spd(0.23).traits(IGNITE).rig().size(0.9F, 1.4F).open(34).fall(24)
                .ab(a(SLAM, "Ember Swing").radius(2.5F).power(1.4F).bolt(BoltKind.FIRE).wind(24).heavy()).build());
        out.add(m("slag_cube", "The Slag Pot", Theme.EMBER, Tier.NORMAL, Model.MAGMA, "An iron crucible that hops, slag slopping: it lands in a ring of fire and is empty and dark for a breath").hp(20.7).dmg(3).spd(0.28).traits(FIRE_IMMUNE, IGNITE).rig().size(0.9F, 0.9F).open(24)
                .ab(a(LEAP, "Molten Splash").radius(2.5F).power(1.2F).bolt(BoltKind.FIRE)).build());
        out.add(m("ember_imp", "The Bellows", Theme.EMBER, Tier.NORMAL, Model.BLAZE, "A forge bellows that flies nozzle first: it swells before each burst of cinders and hangs slack after").hp(13.8).dmg(3).spd(0.3).traits(FIRE_IMMUNE, RANGED).rig().shot(ShotLook.CINDER, 0.35F, 0.5F).size(0.6F, 0.6F).open(22)
                .ab(a(BOLT, "Cinder Burst").n(3).bolt(BoltKind.FIRE)).build());
        out.add(m("scorch_crawler", "The Coal Tick", Theme.EMBER, Tier.NORMAL, Model.SILVERFISH, "A lump of coal in the seam until two embers open; its bites burn").hp(11.5).dmg(2.5).spd(0.33).traits(IGNITE, FIRE_IMMUNE, DORMANT).rig().size(0.6F, 0.45F)
                .ab(a(LEAP, "Scorch Bite").radius(1.5F).power(0.9F).range(6).wind(10)).build());
        out.add(m("slag_boar", "The Slag Boar", Theme.EMBER, Tier.ELITE, Model.HOGLIN, "It paws the ground, then charges through fire; after a charge its furnace back vents and dims").hp(29.9).dmg(5).spd(0.3).traits(FIRE_IMMUNE).rig().size(1.3F, 1.3F).open(26).miss(36).fall(26)
                .ab(a(CHARGE, "Slag Charge").bolt(BoltKind.FIRE).heavy(), a(GUST, "Tusk Toss").radius(3.5F).power(0.8F)).build());
        out.add(m("the_overseer", "The Overseer", Theme.EMBER, Tier.WARDEN, Model.WITHER_SKELETON, "A brick chimney on iron legs that walks through fire to reach you; its furnace door swings open before the ring of embers").hp(39.1).dmg(6.5).spd(0.27).traits(FIRE_IMMUNE, IGNITE, HUNTER).rig().shot(ShotLook.CINDER, 1.6F, 0.6F).size(1.0F, 3.0F).open(30).miss(30).fall(36)
                .ab(a(CHARGE, "Furnace Walk").power(1.06F), a(NOVA, "Ember Ring").n(10).bolt(BoltKind.FIRE), a(AURA, "Heat Haze").bolt(BoltKind.FIRE)).build());
        out.add(m("ember_tyrant", "The Ember Tyrant", Theme.EMBER, Tier.LORD, Model.PIGLIN_BRUTE, "Magma Fist and Eruption, then a Meltdown you have to step out of").hp(181.7).dmg(1.11).spd(0.25).scale(3.0F).size(1.2F, 2.75F).hold(Items.NETHERITE_AXE).wear(EquipmentSlot.HEAD, Items.GOLDEN_HELMET).traits(FIRE_IMMUNE, IGNITE).bar(BossBarColor.RED).arena(22)
                .intro("The mine was dug to keep something in.")
                .phase(1.0F, "The Ember Tyrant draws itself up.", a(SLAM, "Magma Fist").radius(4).bolt(BoltKind.FIRE).heavy(), a(CHARGE, "Tyrant's Advance"))
                .phase(0.66F, "The mountain answers.", a(RAIN, "Eruption").n(5).bolt(BoltKind.FIRE), a(SUMMON, "Stoke the Imps").summon("ember_imp").n(1), a(SLAM, "Magma Fist").radius(4).bolt(BoltKind.FIRE))
                .phase(0.33F, "Meltdown.", a(AURA, "Meltdown").radius(7).bolt(BoltKind.FIRE).heavy(), a(RAIN, "Eruption").n(7).bolt(BoltKind.FIRE), a(CHARGE, "Tyrant's Advance"))
                .parts(P("tyrant_crown", "Tyrant's Crown", "brass"), P("molten_core", "Molten Core", "ember")).build());
    }

    private static void drownedArchive(List<MobDef> out) {
        out.add(m("ink_drowned", "The Blotter", Theme.DROWNED, Tier.NORMAL, Model.DROWNED, "A walking heap of drowned books; the book it has for a head opens wide before it spits ink, and a blow snaps it shut").hp(23).dmg(3).spd(0.23).rig().shot(ShotLook.INK, 1.5F, 0.6F).size(0.9F, 1.7F).stopped().open(24).fall(24)
                .ab(a(BOLT, "Ink Spit").bolt(BoltKind.WATER).cd(80)).build());
        out.add(m("page_wraith", "The Paper Crane", Theme.DROWNED, Tier.NORMAL, Model.VEX, "Birds folded from wet pages: they rustle, then cut in a line; a cut that misses leaves one sodden on the floor").hp(10.3).dmg(2.5).spd(0.5).rig().size(0.6F, 0.6F).miss(34)
                .ab(a(CHARGE, "Papercut").power(1.0F).wind(12).cd(70).range(10)).build());
        out.add(m("archive_eye", "The Reading Glass", Theme.DROWNED, Tier.NORMAL, Model.GUARDIAN, "A floating reading glass: light gathers in it, then the beam; it has to hold still to read you").hp(20.7).dmg(3).spd(0.25).traits(RANGED).rig().size(0.7F, 1.3F).planted().open(24)
                .ab(a(BEAM, "Reading Beam").bolt(BoltKind.WATER).power(1.6F)).build());
        out.add(m("salt_stray", "The Quillback", Theme.DROWNED, Tier.NORMAL, Model.STRAY, "A salt-crusted beast with a back full of quill pens; it bows to aim them, and bowed it cannot see you").hp(20.7).dmg(3).spd(0.24).traits(RANGED, SLOW_TOUCH).rig().shot(ShotLook.QUILL, 1.0F, 0.3F).size(0.9F, 1.0F).open(26).fall(20)
                .ab(a(ARROWS, "Brine Arrows").n(2).fx(MobEffects.SLOWNESS, 60, 1)).build());
        out.add(m("drowned_scribe", "The Scribe", Theme.DROWNED, Tier.ELITE, Model.EVOKER, "An inkwell on four long pens: what it writes in the air becomes a crane; a blow while it writes loses the page").hp(27.6).dmg(4).spd(0.25).traits(RANGED).rig().shot(ShotLook.INK, 1.7F, 0.6F).size(1.0F, 1.9F).stopped().open(26).fall(26)
                .ab(a(SUMMON, "Torn Pages").summon("page_wraith").n(2), a(FANGS, "Ink Fangs"), a(BOLT, "Ink Spit").bolt(BoltKind.WATER)).build());
        out.add(m("the_curator", "The Curator", Theme.DROWNED, Tier.WARDEN, Model.ELDER_GUARDIAN, "A cabinet of small drawers on a skirt of loose cards; every attack is a drawer sliding open, and when they all slam shut it has shushed you").hp(39.1).dmg(6).spd(0.22).traits(HUNTER, RANGED).rig().shot(ShotLook.CARD, 1.0F, 0.5F).size(1.2F, 1.9F).open(30).fall(36)
                .ab(a(BEAM, "Silence in the Stacks").bolt(BoltKind.WATER).power(1.65F).heavy(), a(DARKNESS, "Shush").fx(MobEffects.MINING_FATIGUE, 200, 2), a(NOVA, "Ink Tide").n(10).bolt(BoltKind.WATER)).build());
        out.add(m("drowned_archivist", "The Drowned Archivist", Theme.DROWNED, Tier.LORD, Model.DROWNED, "Tidal Page washes you back; the shelves empty; then the Flood").hp(125.3).dmg(2.02).spd(0.24).scale(1.9F).size(0.9F, 2.4F).hold(Items.TRIDENT).offhand(Items.BOOK).bar(BossBarColor.BLUE).arena(22)
                .intro("Every book here was written by someone who drowned.")
                .phase(1.0F, "The Archivist opens a book.", a(CONE, "Tidal Page").radius(7).power(1.4F).bolt(BoltKind.WATER).heavy(), a(BOLT, "Ink Volley").n(3).bolt(BoltKind.WATER))
                .phase(0.66F, "The shelves empty.", a(SUMMON, "Loose Pages").summon("page_wraith").n(2), a(CLOUD, "Ink Cloud").radius(3).fx(MobEffects.BLINDNESS, 60, 0), a(CONE, "Tidal Page").radius(7).bolt(BoltKind.WATER))
                .phase(0.33F, "The Flood.", a(AURA, "Flood").radius(8).bolt(BoltKind.WATER).fx(MobEffects.SLOWNESS, 40, 1).heavy(), a(PULL, "Undertow").radius(12), a(CONE, "Tidal Page").radius(8).bolt(BoltKind.WATER))
                .parts(P("archivist_quill", "Archivist's Quill", "water"), P("waterlogged_codex", "Waterlogged Codex", "water")).build());
    }

    private static void clockworkFoundry(List<MobDef> out) {
        out.add(m("cog_hound", "The Cog Hound", Theme.CLOCKWORK, Tier.NORMAL, Model.WOLF, "A hound built round one great gear, a key in its back: the key winds before it pounces, and a miss leaves it standing there unwinding").hp(18.4).dmg(3).spd(0.34).rig().size(0.7F, 1.2F).miss(34).fall(22)
                .ab(a(LEAP, "Ratchet Pounce").radius(1.8F)).build());
        out.add(m("rivet_gunner", "The Riveter", Theme.CLOCKWORK, Tier.NORMAL, Model.PILLAGER, "A rivet gun on three piston legs: it plants all three before a burst and cannot turn while they are down").hp(20.7).dmg(3).spd(0.26).traits(RANGED).rig().shot(ShotLook.RIVET, 1.05F, 0.75F).size(1.0F, 1.3F).planted().open(22).fall(24)
                .ab(a(BOLT, "Rivet Burst").n(3).bolt(BoltKind.PLAIN).power(0.8F)).build());
        out.add(m("spark_drone", "The Governor", Theme.CLOCKWORK, Tier.NORMAL, Model.BEE, "Two brass balls spinning round a spindle: the arms rise as it spins up; a blow stops the spin, and a sting that misses drops it").hp(10.3).dmg(2).spd(0.5).traits(SLOW_TOUCH).rig().size(0.6F, 1.0F).stopped().miss(30)
                .ab(a(CHARGE, "Spark Sting").bolt(BoltKind.CHARGE).wind(14).cd(80).range(10)).build());
        out.add(m("foundry_marshal", "The Steam Hammer", Theme.CLOCKWORK, Tier.ELITE, Model.VINDICATOR, "A boiler with one arm that is a piston as big as the rest of it: it charges, the hammer falls where it stops, and it vents for a breath after").hp(29.9).dmg(5).spd(0.28).rig().size(1.1F, 1.8F).open(28).miss(30).fall(30)
                .ab(a(CHARGE, "Steam Charge").heavy(), a(SLAM, "Piston Slam")).build());
        out.add(m("the_regulator", "The Regulator", Theme.CLOCKWORK, Tier.WARDEN, Model.IRON_GOLEM, "A riveted boiler on four legs with a dial for a face: the needle climbs as it speeds up, and when the valve blows it has nothing left for a moment").hp(43.7).dmg(7).spd(0.27).traits(HUNTER).rig().shot(ShotLook.RIVET, 1.4F, 0.0F).size(1.4F, 1.9F).open(30).fall(40)
                .ab(a(SLAM, "Governor Slam").radius(4).heavy(), a(NOVA, "Spark Ring").n(12).bolt(BoltKind.CHARGE), a(FRENZY, "Overclock")).build());
        out.add(m("gearwright_colossus", "The Gearwright Colossus", Theme.CLOCKWORK, Tier.LORD, Model.IRON_GOLEM, "Piston Slams and spark halos; at the end an Overload arc").body(BodyMaterial.ARMOR).hp(156.4).dmg(2.2).spd(0.22).scale(1.8F).size(0.83F, 4.66F).tint(0xF0C060).bar(BossBarColor.YELLOW).arena(24)
                .intro("The foundry built one thing. It is still building it.")
                .phase(1.0F, "The Colossus engages.", a(SLAM, "Piston Slam").radius(4.5F).heavy(), a(NOVA, "Spark Halo").n(12).bolt(BoltKind.CHARGE))
                .phase(0.66F, "Gears engage.", a(SUMMON, "Release Drones").summon("spark_drone").n(2), a(CHARGE, "Colossal Stride"), a(SLAM, "Piston Slam").radius(4.5F))
                .phase(0.33F, "Overload.", a(BEAM, "Overload Arc").bolt(BoltKind.CHARGE).power(2.2F).heavy(), a(RAIN, "Arc Strikes").n(6).bolt(BoltKind.CHARGE), a(SLAM, "Piston Slam").radius(5))
                .parts(P("colossus_gear", "Colossus Gear", "brass"), P("mainspring", "Mainspring", "brass")).build());
    }

    private static void glassDesert(List<MobDef> out) {
        out.add(m("glass_husk", "The Shardwalker", Theme.GLASS, Tier.NORMAL, Model.HUSK, "A bundle of long glass blades that walks on its points; it leans back before it sandblasts, and it shatters when it dies").hp(23).dmg(3).spd(0.23).traits(DEATH_SHARDS).rig().size(0.9F, 1.7F).open(26).fall(20)
                .ab(a(CONE, "Sandblast").power(0.6F).bolt(BoltKind.GLASS).fx(MobEffects.BLINDNESS, 30, 0)).build());
        out.add(m("sand_stalker", "The Sand Stalker", Theme.GLASS, Tier.NORMAL, Model.SPIDER, "Flat and wide, with sickle jaws: it goes flat into the sand and comes up beside you, jaws first").hp(18.4).dmg(3).spd(0.32).rig().size(1.2F, 0.5F).fall(20)
                .ab(a(BLINK, "Burrow Strike")).build());
        out.add(m("heat_shimmer", "The Shimmer", Theme.GLASS, Tier.NORMAL, Model.BREEZE, "Thin glass plates stacked in a twist, almost clear: it turns flat toward you before the gust").hp(18.4).dmg(3).spd(0.28).traits(RANGED).rig().shot(ShotLook.SHARD, 0.6F, 0.3F).moves(Model.Move.FLY).size(0.6F, 1.0F).open(24)
                .ab(a(GUST, "Mirage Gust").radius(5).power(0.4F), a(BOLT, "Glass Gust").bolt(BoltKind.GLASS)).build());
        out.add(m("prism_sentinel", "The Prism", Theme.GLASS, Tier.ELITE, Model.ENDERMAN, "A spire of glass hanging point down, a light climbing inside it: when the light reaches the tip it fires, and it breaks into pieces to move").hp(29.9).dmg(5).spd(0.3).rig().moves(Model.Move.FLY).size(0.7F, 1.6F).open(28)
                .ab(a(BLINK, "Refract"), a(BEAM, "Prism Lance").bolt(BoltKind.GLASS)).build());
        out.add(m("the_mirage", "The Mirage", Theme.GLASS, Tier.WARDEN, Model.ILLUSIONER, "A traveller's cloak and wide hat with nothing inside; it splits into several, and only the real one's hat casts a shadow").hp(36.8).dmg(5.5).spd(0.28).traits(HUNTER, RANGED).rig().shot(ShotLook.SHARD, 1.3F, 0.5F).size(1.0F, 2.0F).open(28).fall(28)
                .ab(a(DECOY, "Mirages").n(3), a(BOLT, "Sunshards").n(5).bolt(BoltKind.GLASS), a(BLINK, "Heat Mirage")).build());
        out.add(m("glass_serpent", "The Glass Serpent", Theme.GLASS, Tier.LORD, Model.SERPENT, "Lunges, dives into the sand and erupts beneath you, then the Glass Storm").body(BodyMaterial.STONE).hp(57.5).dmg(3).spd(0.3).scale(2.2F).size(1.4F, 1.8F).grounded().bar(BossBarColor.WHITE).arena(26)
                .intro("Your reflection blinks first.")
                .phase(1.0F, "The Glass Serpent uncoils.", a(CHARGE, "Glass Lunge").heavy(), a(NOVA, "Shard Spray").n(10).bolt(BoltKind.GLASS))
                .phase(0.66F, "It dives into the sand.", a(BLINK, "Burrow Strike").power(1.6F).heavy(), a(RAIN, "Erupting Glass").n(5).bolt(BoltKind.GLASS), a(CHARGE, "Glass Lunge"))
                .phase(0.33F, "Glass storm.", a(NOVA, "Glass Storm").n(20).bolt(BoltKind.GLASS).heavy(), a(CHARGE, "Glass Lunge"), a(RAIN, "Erupting Glass").n(6).bolt(BoltKind.GLASS))
                .parts(P("serpent_scale", "Glass Serpent Scale", "mirror"), P("sand_glass_fang", "Sandglass Fang", "sand")).build());
    }

    private static void hive(List<MobDef> out) {
        out.add(m("drone_bee", "The Stinger", Theme.HIVE, Tier.NORMAL, Model.BEE, "One long sting under four wings: it curls it under before it dives, and a dive that misses breaks it off").hp(11.5).dmg(2.5).spd(0.5).traits(POISON_TOUCH).rig().size(0.6F, 0.8F).miss(30)
                .ab(a(CHARGE, "Stinger Dive").wind(14).cd(80).range(10)).build());
        out.add(m("hive_larva", "The Grub", Theme.HIVE, Tier.NORMAL, Model.SILVERFISH, "They hump out of the comb in a row and arch right up before they jump").hp(6.9).dmg(2).spd(0.3).rig().size(0.5F, 0.45F)
                .ab(a(LEAP, "Wriggle").radius(1.2F).power(0.8F).range(5).wind(10).cd(60)).build());
        out.add(m("honey_slime", "The Honeypot", Theme.HIVE, Tier.NORMAL, Model.SLIME, "A small black ant under a belly of honey; it lands sticky and stands sloshing afterwards").hp(18.4).dmg(2.5).spd(0.27).traits(SLOW_TOUCH).rig().moves(Model.Move.GROUND).size(0.8F, 0.9F).open(26)
                .ab(a(LEAP, "Sticky Landing").bolt(BoltKind.HONEY).fx(MobEffects.SLOWNESS, 60, 1)).build());
        out.add(m("hive_guard", "The Door", Theme.HIVE, Tier.ELITE, Model.SPIDER, "Its flat face stops every blow from the front; it lunges from under it and is open while it is stretched out").hp(27.6).dmg(5).spd(0.3).rig().size(1.3F, 1.0F).front().open(30).fall(28)
                .ab(a(LEAP, "Mandible Crush").heavy(), a(ROOT, "Wax Seal").fx(MobEffects.SLOWNESS, 60, 3)).build());
        out.add(m("the_swarm_mind", "The Swarm Mind", Theme.HIVE, Tier.WARDEN, Model.PHANTOM, "A paper nest carried by a ring of drones, more pouring from its mouth; it swings like a flail under them").hp(34.5).dmg(5.5).spd(0.35).traits(HUNTER).rig().size(1.6F, 2.0F).open(30).miss(36).fall(36)
                .ab(a(SUMMON, "Call the Swarm").summon("drone_bee").n(3), a(CHARGE, "Swarm Dive"), a(RAIN, "Honey Drop").bolt(BoltKind.HONEY).fx(MobEffects.SLOWNESS, 60, 2)).build());
        out.add(m("hive_regent", "The Hive Regent", Theme.HIVE, Tier.LORD, Model.BEE, "Royal Dives and Honey Rain; at the end she feeds on royal jelly and frenzies").hp(48.4).dmg(0.67).spd(0.45).scale(2.0F).size(0.6F, 2.4F).bar(BossBarColor.YELLOW).arena(22)
                .intro("A hum you feel in your teeth.")
                .phase(1.0F, "The Regent rises from the comb.", a(CHARGE, "Royal Dive").heavy())
                .phase(0.66F, "The comb weeps.", a(RAIN, "Honey Rain").n(5).bolt(BoltKind.HONEY).fx(MobEffects.SLOWNESS, 60, 2), a(BOLT, "Stinger Volley").n(4).bolt(BoltKind.STINGER), a(CHARGE, "Royal Dive"), a(SUMMON, "Brood").summon("drone_bee").n(2))
                .phase(0.33F, "Royal Jelly.", a(HEAL, "Royal Jelly").power(0.15F), a(FRENZY, "Frenzy"), a(SUMMON, "Brood").summon("drone_bee").n(2), a(CHARGE, "Royal Dive").heavy())
                .parts(P("regent_stinger", "Regent's Stinger", "amber"), P("royal_jelly", "Royal Jelly", "amber")).build());
    }

    private static void overgrownTemple(List<MobDef> out) {
        out.add(m("vine_creaker", "The Creaker", Theme.TEMPLE, Tier.NORMAL, Model.CREAKING, "A broken statue the roots carry about; it only walks while nobody is looking").hp(25.3).dmg(4).spd(0.3).traits(WATCHED_FREEZE).rig().size(1.1F, 1.9F).open(28).fall(26)
                .ab(a(ROOT, "Root Grasp").radius(1.8F)).build());
        out.add(m("thorn_archer", "The Thorn Bud", Theme.TEMPLE, Tier.NORMAL, Model.BOGGED, "A bud on a stalk that opens to spit poisoned thorns, and stays open a moment after").hp(20.7).dmg(3).spd(0.24).traits(RANGED, POISON_TOUCH).rig().shot(ShotLook.THORN, 1.6F, 0.4F).size(0.6F, 1.9F).planted().open(24).fall(20)
                .ab(a(ARROWS, "Thorn Arrows").n(2).fx(MobEffects.POISON, 60, 0)).build());
        out.add(m("thorn_hound", "The Bramble Hound", Theme.TEMPLE, Tier.NORMAL, Model.WOLF, "Lies like a bush, then pounces; a pounce that misses lands it badly").hp(18.4).dmg(3).spd(0.34).rig().size(0.7F, 1.2F).miss(30).fall(20)
                .ab(a(LEAP, "Bramble Pounce").radius(1.8F)).build());
        out.add(m("incense_priest", "The Censer", Theme.TEMPLE, Tier.ELITE, Model.EVOKER, "A burner on stilts: Root Fangs along the floor and smoke that heals the others; a blow while it gathers itself stops it").hp(27.6).dmg(4).spd(0.25).traits(RANGED).rig().size(1.0F, 1.7F).stopped().open(26).fall(26)
                .ab(a(FANGS, "Root Fangs"), a(HEAL, "Incense"), a(CLOUD, "Incense Smoke").fx(MobEffects.WEAKNESS, 80, 0)).build());
        out.add(m("the_overgrowth", "The Overgrowth", Theme.TEMPLE, Tier.WARDEN, Model.IRON_GOLEM, "A temple pillar torn loose, its roof still on its head; its roots come up in rings").hp(43.7).dmg(7).spd(0.25).traits(HUNTER).rig().size(1.4F, 3.1F).open(30).fall(40)
                .ab(a(SLAM, "Temple Fist").power(1.07F).radius(4).heavy(), a(FANGS_RING, "Root Ring").power(0.99F).n(10), a(ROOT, "Strangling Vines").radius(2.5F)).build());
        out.add(m("root_saint", "The Root Saint", Theme.TEMPLE, Tier.LORD, Model.CREAKING, "Root Lances in lines, then the whole temple grabs for you").hp(186.4).dmg(0.86).spd(0.24).scale(1.8F).size(1.0F, 3.02F).bar(BossBarColor.GREEN).arena(22)
                .intro("Incense and wet roots.")
                .phase(1.0F, "The Root Saint opens its eyes.", a(FANGS, "Root Lance").n(14), a(SLAM, "Saint's Palm").radius(4))
                .phase(0.66F, "The temple grows.", a(SUMMON, "Grow Creakers").summon("vine_creaker").n(2), a(ROOT, "Root Grasp").radius(3), a(FANGS_RING, "Root Ring").n(10))
                .phase(0.33F, "Thorn Halo.", a(NOVA, "Thorn Halo").n(16).bolt(BoltKind.THORN).heavy(), a(FANGS, "Root Lance").n(18), a(ROOT, "Root Grasp").radius(3))
                .parts(P("saint_heartwood", "Saint's Heartwood", "wood"), P("temple_resin", "Temple Resin", "amber")).build());
    }

    private static void lanternMarket(List<MobDef> out) {
        out.add(m("lantern_moth", "The Lantern Moth", Theme.LANTERN, Tier.NORMAL, Model.PHANTOM, "A moth whose body is a lit paper lantern; harmless until something hits it").hp(9.2).dmg(2).spd(0.4).traits(NEUTRAL).rig().size(0.6F, 0.6F).miss(30)
                .ab(a(CHARGE, "Drawn to Light").power(0.8F).wind(12).cd(80).range(10)).build());
        out.add(m("pickpocket", "The Magpie", Theme.LANTERN, Tier.NORMAL, Model.PILLAGER, "A long-legged runner with a hooked mask and a sack bigger than it is: it takes a shard and runs; catch it and the sack spills").hp(16.1).dmg(2).spd(0.33).traits(STEALER).rig().size(0.7F, 1.7F).fall(22).build());
        out.add(m("wick_slime", "The Hopping Lantern", Theme.LANTERN, Tier.NORMAL, Model.SLIME, "A round paper lantern that hops on its tassel; leave it be and it leaves you be, and the split opens on its flame when it is angry").hp(13.8).dmg(2).spd(0.26).traits(NEUTRAL, IGNITE).rig().size(0.7F, 0.8F).open(24)
                .ab(a(LEAP, "Wick Hop").bolt(BoltKind.FIRE)).build());
        out.add(m("masked_duelist", "The Fan Dancer", Theme.LANTERN, Tier.ELITE, Model.PIGLIN, "A tall thing made of folding fans: shut, they turn any blow from the front; it has to open them to lunge").hp(27.6).dmg(5).spd(0.3).traits(NEUTRAL).rig().size(0.7F, 2.0F).front().open(30).miss(30).fall(28)
                .ab(a(GUARD, "Parry"), a(CHARGE, "Fan Dance")).build());
        out.add(m("the_lamplighter", "The Lamplighter", Theme.LANTERN, Tier.WARDEN, Model.ENDERMAN, "A small body on stilts as tall as a house, with a snuffing pole: it steps between the lamps and puts them out one by one").hp(36.8).dmg(6).spd(0.3).traits(HUNTER).rig().shot(ShotLook.OIL, 2.6F, 0.5F).size(0.9F, 3.3F).open(28).fall(40)
                .ab(a(BLINK, "Between Lamps"), a(DARKNESS, "Snuff the Lamps"), a(BOLT, "Lamp Oil").n(3).bolt(BoltKind.FIRE)).build());
        out.add(m("lantern_broker", "The Lantern Broker", Theme.LANTERN, Tier.LORD, Model.LANTERN, "Trades with you; duels you only if you ask (sneak and use)").hp(138).dmg(2.78).spd(0.28).scale(2.0F).size(0.6F, 2.2F).traits(FRIENDLY).bar(BossBarColor.YELLOW).arena(18)
                .intro("A duel, then. Lanterns down.")
                .phase(1.0F, "The Broker bows.", a(BOLT, "Lantern Bolts").n(3).bolt(BoltKind.FIRE), a(BLINK, "Market Step"))
                .phase(0.66F, "Prices are going up.", a(SUMMON, "Release the Moths").summon("lantern_moth").n(2), a(NOVA, "Paper Lanterns").n(10).bolt(BoltKind.FIRE))
                .phase(0.33F, "Lights out.", a(DARKNESS, "Lights Out").heavy(), a(BLINK, "Market Step"), a(BOLT, "Lantern Bolts").n(5).bolt(BoltKind.FIRE))
                .parts(P("broker_lantern", "Broker's Lantern", "amber"), P("gilded_ledger", "Gilded Ledger", "brass")).build());
    }

    private static void frostMonastery(List<MobDef> out) {
        out.add(m("rime_acolyte", "The Frozen Monk", Theme.FROST, Tier.NORMAL, Model.PILLAGER, "A kneeling figure frozen in a block of ice that slides at you, one great palm out; the palm draws back before the shove").hp(20).dmg(3.5).spd(0.26).traits(SLOW_TOUCH).rig().size(0.8F, 1.0F).open(26).fall(24)
                .ab(a(GUST, "Frost Palm").radius(3).power(0.8F).bolt(BoltKind.FROST).fx(MobEffects.SLOWNESS, 60, 1)).build());
        out.add(m("hail_stray", "The Hail Slinger", Theme.FROST, Tier.NORMAL, Model.STRAY, "A small thing with one arm twice as long as itself; the long arm whips right round behind it before it throws").hp(18).dmg(3).spd(0.24).traits(RANGED).rig().shot(ShotLook.HAIL, 1.2F, 0.3F).size(0.6F, 1.1F).open(22).fall(22)
                .ab(a(ARROWS, "Hail Volley").n(3).fx(MobEffects.SLOWNESS, 60, 0)).build());
        out.add(m("frost_wisp", "The Snow Star", Theme.FROST, Tier.NORMAL, Model.BREEZE, "A six-pointed crystal that turns in the air; its points fly off as bolts and grow back").hp(16).dmg(3).spd(0.28).traits(RANGED).rig().shot(ShotLook.POINT, 0.75F, 0.2F).moves(Model.Move.FLY).size(0.9F, 0.9F).open(22)
                .ab(a(BOLT, "Blizzard Bolt").n(2).bolt(BoltKind.FROST), a(GUST, "Gale").radius(4).power(0.3F)).build());
        out.add(m("ice_hound", "The Ice Hound", Theme.FROST, Tier.NORMAL, Model.SNOW_WOLF, "A lean hound of clear ice, its spine showing through; a pounce that misses skids on past you").hp(16).dmg(3).spd(0.35).traits(SLOW_TOUCH).rig().size(0.7F, 1.2F).miss(34).fall(20)
                .ab(a(LEAP, "Ice Pounce").radius(1.8F).bolt(BoltKind.FROST)).build());
        out.add(m("chant_master", "The Long Horn", Theme.FROST, Tier.ELITE, Model.EVOKER, "A temple horn longer than its bearer is tall: it comes up to sound, and while it is up the bearer under it has no guard").hp(24).dmg(4).spd(0.25).traits(RANGED).rig().shot(ShotLook.POINT, 1.0F, 0.9F).size(0.8F, 1.7F).stopped().open(26).fall(28)
                .ab(a(SUMMON, "Chant").summon("frost_wisp").n(1), a(NOVA, "Frost Nova").n(10).bolt(BoltKind.FROST), a(RAIN, "Ice Spikes").n(3).bolt(BoltKind.FROST)).build());
        out.add(m("the_yeti", "The Yeti", Theme.FROST, Tier.WARDEN, Model.POLAR_BEAR, "Huge in front and small behind, its fur full of icicles and frozen prayer flags; it rises on its hind legs before the slam, and its breath slows you").hp(38).dmg(7).spd(0.3).traits(HUNTER).rig().size(1.6F, 2.2F).open(30).miss(30).fall(40)
                .ab(a(SLAM, "Avalanche Slam").radius(4).bolt(BoltKind.FROST).heavy(), a(CHARGE, "Snow Charge"), a(CONE, "Frost Breath").bolt(BoltKind.FROST).fx(MobEffects.SLOWNESS, 60, 2)).build());
        out.add(m("abbot_of_frost", "The Abbot of Frost", Theme.FROST, Tier.LORD, Model.EVOKER, "Frost Hymns and Ice Spikes; the choir joins; then the Glacial Vow").hp(86.5).dmg(4.3).spd(0.25).scale(1.9F).size(0.9F, 2.0F).tint(0xD8F0FF).bar(BossBarColor.WHITE).arena(22)
                .intro("Chanting above a sea of clouds.")
                .phase(1.0F, "The Abbot begins the hymn.", a(BOLT, "Frost Hymn").n(5).bolt(BoltKind.FROST), a(RAIN, "Ice Spikes").n(4).bolt(BoltKind.FROST))
                .phase(0.66F, "The choir joins.", a(SUMMON, "The Choir").summon("frost_wisp").n(2), a(AURA, "Blizzard").radius(9).bolt(BoltKind.FROST).fx(MobEffects.SLOWNESS, 40, 1), a(RAIN, "Ice Spikes").n(5).bolt(BoltKind.FROST))
                .phase(0.33F, "Glacial Vow.", a(BURST, "Glacial Vow").power(1.67F).radius(14).inner(4).bolt(BoltKind.FROST).heavy(), a(RAIN, "Ice Spikes").n(6).bolt(BoltKind.FROST), a(NOVA, "Frost Nova").n(14).bolt(BoltKind.FROST))
                .parts(P("abbot_prayer_beads", "Abbot's Prayer Beads", "frost"), P("glacier_heart", "Glacier Heart", "frost")).build());
    }

    private static void mirrorPalace(List<MobDef> out) {
        out.add(m("mirror_shade", "Mirror Shade", Theme.MIRROR, Tier.NORMAL, Model.MIMIC, "Wears your face and steps out behind you").hp(18).dmg(3.5).spd(0.28).traits(MIMIC, HAUNTED)
                .ab(a(BLINK, "Behind You")).build());
        out.add(m("silver_knight", "The Empty Suit", Theme.MIRROR, Tier.NORMAL, Model.ZOMBIE, "A suit of mirror plate with nobody in it, the plates hanging a little apart; its shield is a mirror with you in it, and it parries: wait for its own swing").hp(22).dmg(4).spd(0.24).armor(4).rig().spent(24).size(0.8F, 1.9F).fall(26)
                .ab(a(GUARD, "Parry")).build());
        out.add(m("glint_mite", "The Glint", Theme.MIRROR, Tier.NORMAL, Model.ENDERMITE, "A shard of mirror on six needle legs, thin as a blade edge-on; it jumps from one reflection to the next").hp(8).dmg(2).spd(0.33).rig().size(0.6F, 0.9F).fall(18)
                .ab(a(BLINK, "Glint").power(0.8F).cd(90)).build());
        out.add(m("glass_duelist", "The Glass Duelist", Theme.MIRROR, Tier.ELITE, Model.SKELETON, "A fencer of clear glass with one arm far longer than the other: it answers every blow you land badly, and a lunge that misses leaves it stretched out").hp(24).dmg(5).spd(0.28).traits(REFLECT).rig().size(0.7F, 2.0F).miss(30).fall(26)
                .ab(a(GUARD, "Riposte"), a(CHARGE, "Mirror Lunge")).build());
        out.add(m("the_unblinking", "The Unblinking", Theme.MIRROR, Tier.WARDEN, Model.ENDERMAN, "A tall standing mirror on clawed feet, one eye filling the glass; it moves when you look away, and its gaze cuts in a line: the eye narrows first").hp(34).dmg(6.5).spd(0.3).traits(HUNTER).rig().size(1.1F, 2.4F).open(28).fall(32)
                .ab(a(BLINK, "Never Blinks"), a(BEAM, "Silver Gaze").bolt(BoltKind.GLASS), a(DECOY, "Reflections").n(2)).build());
        out.add(m("mirror_knight", "The Mirror Knight", Theme.MIRROR, Tier.LORD, Model.MIMIC, "Wears your face, parries perfectly, splits into reflections").hp(116.4).dmg(2.38).spd(0.28).scale(1.9F).size(0.6F, 1.8F).hold(Items.DIAMOND_SWORD).wear(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE).wear(EquipmentSlot.HEAD, Items.DIAMOND_HELMET).traits(MIMIC, REFLECT).bar(BossBarColor.WHITE).arena(22)
                .intro("Footsteps that match yours.")
                .phase(1.0F, "The Mirror Knight raises your blade.", a(GUARD, "Perfect Parry"), a(CHARGE, "Mirror Lunge"))
                .phase(0.66F, "It splits.", a(DECOY, "Mirror Split").n(3), a(BLINK, "Through the Glass"), a(CHARGE, "Mirror Lunge"))
                .phase(0.33F, "The palace shatters.", a(NOVA, "Shatter").n(18).bolt(BoltKind.GLASS).heavy(), a(DECOY, "Mirror Split").n(2), a(GUARD, "Perfect Parry"), a(CHARGE, "Mirror Lunge"))
                .parts(P("mirror_shard_crown", "Shard Crown", "mirror"), P("knight_reflection", "Captured Reflection", "mirror")).build());
    }

    private static void precursorLab(List<MobDef> out) {
        out.add(m("test_subject", "The Test Subject", Theme.PRECURSOR, Tier.NORMAL, Model.ZOMBIE, "A shape under a sheet, still strapped to the table it walks on; it lurches the whole table at you, and it bursts into sparks when it dies").hp(22).dmg(3).spd(0.24).traits(DEATH_SHARDS).rig().size(1.0F, 1.4F).miss(30).fall(22)
                .ab(a(CHARGE, "Lurch").power(1.2F).cd(110)).build());
        out.add(m("security_unit", "The Security Unit", Theme.PRECURSOR, Tier.NORMAL, Model.IRON_GOLEM, "A shut steel egg that opens three legs and one lens when you come near; shut, nothing hurts it, and it has to open to see you").hp(20).dmg(4).spd(0.25).traits(DORMANT).rig().size(0.8F, 1.0F).planted().open(24).fall(20)
                .ab(a(BEAM, "Scan Lance").bolt(BoltKind.CHARGE).power(1.4F)).build());
        out.add(m("coil_unit", "The Coil", Theme.PRECURSOR, Tier.NORMAL, Model.BLAZE, "A ring floating over a column, sparks climbing it: the sparks reach the ring, then it fires; crowd it and it lets everything go at once").hp(14).dmg(3).spd(0.28).traits(RANGED).rig().shot(ShotLook.ARC, 1.2F, 0.0F).size(0.7F, 1.2F).open(24)
                .ab(a(BOLT, "Coil Bolt").n(2).bolt(BoltKind.CHARGE), a(NOVA, "Discharge").n(8).bolt(BoltKind.CHARGE).range(5)).build());
        out.add(m("gel_sample", "The Gel Sample", Theme.PRECURSOR, Tier.ELITE, Model.SLIME, "Gel that carries the broken glass of its own tank round it like a shell; kill it and it becomes two").hp(26).dmg(4).spd(0.26).scale(1.6F).traits(SPLITS).rig().size(1.0F, 1.0F).open(26)
                .ab(a(LEAP, "Containment Breach").radius(3).heavy()).build());
        out.add(m("the_sweeper", "The Sweeper", Theme.PRECURSOR, Tier.WARDEN, Model.RAVAGER, "A floor-cleaning engine as wide as the corridor, a turning drum of brushes in front; it charges straight, and it cannot see anything that is beside it").hp(38).dmg(7).spd(0.3).traits(HUNTER).rig().shot(ShotLook.ARC, 0.9F, 0.6F).size(1.6F, 1.0F).open(28).miss(34).fall(36)
                .ab(a(CHARGE, "Purge Protocol").heavy(), a(NOVA, "Sweep").n(12).bolt(BoltKind.CHARGE), a(GUST, "Decon Blast").radius(6)).build());
        out.add(m("unit_zero", "Unit Zero", Theme.PRECURSOR, Tier.LORD, Model.HUMAN, "Counts down in no language: lances, orbital marks, Zero Point").hp(129.9).dmg(1.71).spd(0.3).scale(1.85F).size(0.56F, 2.32F).bar(BossBarColor.BLUE).arena(22)
                .intro("A voice counting down in no language.")
                .phase(1.0F, "Unit Zero activates.", a(BEAM, "Zero Lance").bolt(BoltKind.CHARGE), a(BLINK, "Phase Step"))
                .phase(0.66F, "Countdown: three.", a(SUMMON, "Deploy Coils").summon("coil_unit").n(2), a(RAIN, "Orbital Mark").n(4).bolt(BoltKind.CHARGE), a(BEAM, "Zero Lance").bolt(BoltKind.CHARGE))
                .phase(0.33F, "Countdown: zero.", a(BURST, "Zero Point").radius(14).inner(3.5F).bolt(BoltKind.CHARGE).heavy(), a(BEAM, "Zero Lance").bolt(BoltKind.CHARGE), a(BLINK, "Phase Step"))
                .parts(P("zero_core", "Zero Core", "signal"), P("zero_plating", "Zero Plating", "precursor")).build());
        // the Provings' Wardens (SPEC §47.4): one great fight each Reassessment, in a hall built for it, never announced
        out.add(m("proving_ringer", "The Ringer", Theme.BELL, Tier.PROVING, Model.HUMAN, "A bell-ringer that drowned at its ropes and kept ringing")
                .hp(420).dmg(1.4).spd(0.27).scale(2.0F).size(0.6F, 2.0F).bar(BossBarColor.BLUE).arena(40)
                .intro("Something up in the bells is swinging them.")
                .phase(1.0F, "The Ringer drops into the light.", a(SLAM, "Clang").radius(5), a(BURST, "Drowned Toll").radius(10).inner(2.5F).power(1.8F).wind(22).heavy(),
                        a(BOLT, "Peal").n(1).bolt(BoltKind.SPIRIT))
                .phase(0.5F, "The water drains.", a(SLAM, "Clang").radius(5), a(BURST, "Drowned Toll").radius(12).inner(2.5F).power(2.0F).wind(22).heavy(),
                        a(BOLT, "Peal").n(1).bolt(BoltKind.SPIRIT))
                .build());
        // the second (SPEC §47.6): it fights the way you do; its shots wear your power's look (FightRunner.yours)
        out.add(m("proving_examiner", "The Examiner", Theme.MIRROR, Tier.PROVING, Model.HUMAN, "The Registry's examiner, with your face where its own should be")
                .hp(437).dmg(1.8).spd(0.28).scale(2.1F).size(0.6F, 2.0F).bar(BossBarColor.WHITE).arena(40)
                .intro("That was never your room.")
                .phase(1.0F, "The Examiner steps out of the glass.", a(SLAM, "The Measure").radius(5), a(BOLT, "Reading").n(1),
                        a(FANGS, "Verdict").n(14))
                .phase(0.66F, "The mirrors wake.", a(SLAM, "The Measure").radius(5), a(BOLT, "Reading").n(1), a(FANGS, "Verdict").n(14),
                        a(DECOY, "Reflections").n(2))
                .phase(0.33F, "The walls close in.", a(SLAM, "The Measure").radius(5.5F), a(BOLT, "Reading").n(1), a(FANGS, "Verdict").n(16),
                        a(DECOY, "Reflections").n(2))
                .build());
        // the third (SPEC §47.7): the Broken Span's north arm, stood up; the bastion's ring gives way under it, then the lamps go
        out.add(m("proving_unmade", "The Unmade", Theme.FOUNDRY, Tier.PROVING, Model.HUMAN, "The Broken Span's north arm, stood up and walking")
                .hp(480).dmg(2.6).spd(0.25).kb(1.0).scale(3.0F).size(0.8F, 2.0F).bar(BossBarColor.PURPLE).arena(40)
                .intro("The bridge is standing up.")
                .phase(1.0F, "The Unmade stands on the bastion.", a(SLAM, "Keystone").radius(5.5F), a(ROOT, "The Fall").range(16).radius(1.5F).bolt(BoltKind.PLAIN))
                .phase(0.66F, "The floor is giving way.", a(SLAM, "Keystone").radius(6), a(ROOT, "The Fall").range(16).radius(1.5F).bolt(BoltKind.PLAIN))
                .phase(0.33F, "The lamps are going out.", a(SLAM, "Keystone").radius(6), a(ROOT, "The Fall").range(18).radius(1.5F).bolt(BoltKind.PLAIN),
                        a(RAIN, "Rubble").n(4).radius(2.5F).bolt(BoltKind.PLAIN))
                .build());
        // the fourth (SPEC §47.8): it comes down out of the vault of a cathedral over the clouds; the aisles fall, then the
        // roof lifts away and the sky remembers you
        out.add(m("proving_sky_warden", "The Sky Warden", Theme.SKY, Tier.PROVING, Model.HUMAN, "The cathedral's knight, winged in its own glass")
                .hp(520).dmg(3.0).spd(0.3).kb(0.8).scale(2.4F).size(0.6F, 2.0F).bar(BossBarColor.YELLOW).arena(40)
                .intro("Something is coming down out of the vault.")
                .phase(1.0F, "The Sky Warden comes down into the nave.", a(SLAM, "Descent").radius(5), a(CHARGE, "Stoop").range(18),
                        a(BOLT, "Lance of Light").n(1).bolt(BoltKind.GLASS), a(GUST, "Wingbeat").radius(7).power(0.8F))
                .phase(0.66F, "The aisles are falling.", a(SLAM, "Descent").radius(5.5F), a(CHARGE, "Stoop").range(18),
                        a(BOLT, "Lance of Light").n(1).bolt(BoltKind.GLASS), a(GUST, "Wingbeat").radius(8).power(1.0F))
                .phase(0.33F, "The roof is lifting away.", a(SLAM, "Descent").radius(6), a(CHARGE, "Stoop").range(20),
                        a(BOLT, "Lance of Light").n(1).bolt(BoltKind.GLASS), a(GUST, "Wingbeat").radius(8).power(1.0F),
                        a(RAIN, "Shattered Glass").n(5).radius(2.5F).bolt(BoltKind.GLASS))
                .build());
        // the fifth Proving's Warden (SPEC §47.9): the last Keeper, left at the Court's door; stands up out of your shadow
        out.add(m("proving_keepers_shadow", "The Keeper's Shadow", Theme.COURT, Tier.PROVING, Model.HUMAN, "The last Keeper, left at the Court's door to test whoever comes")
                .hp(570).dmg(2.85).spd(0.3).kb(0.9).scale(2.7F).size(0.6F, 2.0F).bar(BossBarColor.PURPLE).arena(40)
                .intro("Your shadow is spreading across the floor.")
                .phase(1.0F, "The Keeper's Shadow stands up out of your shadow.", a(SLAM, "The Lock").radius(5),
                        a(BOLT, "Keyhole").n(3).bolt(BoltKind.VOID), a(BEAM, "Shut the Door").bolt(BoltKind.VOID).power(1.8F).heavy())
                .phase(0.75F, "The doors are opening.", a(SLAM, "The Lock").radius(5.5F), a(BOLT, "Keyhole").n(3).bolt(BoltKind.VOID),
                        a(BEAM, "Shut the Door").bolt(BoltKind.VOID).power(1.9F).heavy(), a(BLINK, "Through the Door"))
                .phase(0.5F, "The dark is coming in.", a(SLAM, "The Lock").radius(5.5F), a(BOLT, "Keyhole").n(3).bolt(BoltKind.VOID),
                        a(BEAM, "Shut the Door").bolt(BoltKind.VOID).power(1.9F).heavy(), a(BLINK, "Through the Door"),
                        a(RAIN, "The Dark Rises").n(4).radius(2.5F).bolt(BoltKind.VOID))
                .phase(0.25F, "The Court's door is opening.", a(SLAM, "The Lock").radius(6), a(BOLT, "Keyhole").n(5).bolt(BoltKind.VOID),
                        a(BEAM, "Shut the Door").bolt(BoltKind.VOID).power(2.0F).heavy(), a(BLINK, "Through the Door"),
                        a(RAIN, "The Dark Rises").n(5).radius(2.5F).bolt(BoltKind.VOID), a(DECOY, "Echoes of the Court").n(2),
                        a(NOVA, "Eight Doors").n(8).bolt(BoltKind.VOID))
                .build());
        out.add(m("proving_last_loop", "The Last Loop", Theme.SEED, Tier.PROVING, Model.HUMAN, "You, as the Signal was: it stands where you stood a moment ago")
                .hp(620).dmg(3.3).spd(0.32).kb(0.9).scale(2.5F).size(0.6F, 1.7F).bar(BossBarColor.WHITE).arena(40)
                .intro("You can see where you were standing a moment ago.")
                .phase(1.0F, "The Last Loop stands where you were.", a(SLAM, "The Fall").radius(5), a(BOLT, "Signal").n(3).bolt(BoltKind.SIGNAL),
                        a(CHARGE, "The Rush").range(18), a(BEAM, "The Line").bolt(BoltKind.SIGNAL).power(1.8F).heavy())
                .phase(0.75F, "Night falls in the antechamber.", a(SLAM, "The Fall").radius(5.5F), a(BOLT, "Signal").n(3).bolt(BoltKind.SIGNAL),
                        a(CHARGE, "The Rush").range(18), a(BEAM, "The Line").bolt(BoltKind.SIGNAL).power(1.9F).heavy(), a(BLINK, "Loop"),
                        a(GUST, "Push").radius(8).power(1.0F))
                .phase(0.5F, "The loop is closing.", a(SLAM, "The Fall").radius(5.5F), a(BOLT, "Signal").n(3).bolt(BoltKind.SIGNAL),
                        a(CHARGE, "The Rush").range(18), a(BEAM, "The Line").bolt(BoltKind.SIGNAL).power(1.9F).heavy(), a(BLINK, "Loop"),
                        a(GUST, "Push").radius(8).power(1.0F), a(RAIN, "Every Power You've Had").n(6).radius(2.5F).bolt(BoltKind.SIGNAL))
                .phase(0.25F, "The Seed's door is turning.", a(SLAM, "The Fall").radius(6), a(BOLT, "Signal").n(5).bolt(BoltKind.SIGNAL),
                        a(CHARGE, "The Rush").range(20), a(BEAM, "The Line").bolt(BoltKind.SIGNAL).power(2.0F).heavy(), a(BLINK, "Loop"),
                        a(GUST, "Push").radius(8).power(1.0F), a(RAIN, "Every Power You've Had").n(8).radius(2.5F).bolt(BoltKind.SIGNAL),
                        a(DECOY, "Echoes of You").n(2), a(NOVA, "The Ring").n(10).bolt(BoltKind.SIGNAL))
                .build());
    }

    private static void nightmare(List<MobDef> out) {
        out.add(m("sleepwalker", "The Sheet", Theme.NIGHTMARE, Tier.NORMAL, Model.ZOMBIE, "A bedsheet over something taller than a person, two hands pressing out from inside; it moves only when you blink").hp(20).dmg(3.5).spd(0.24).traits(HAUNTED).rig().size(0.7F, 2.1F).fall(24)
                .ab(a(BLINK, "Sleepstep").cd(140)).build());
        out.add(m("night_terror", "The Night Terror", Theme.NIGHTMARE, Tier.NORMAL, Model.PHANTOM, "A mouth with wings and nothing else; you hear it before you see it, and it dives from straight above").hp(14).dmg(3).spd(0.45).rig().size(0.8F, 0.6F).miss(30)
                .ab(a(CHARGE, "Night Dive").wind(14).cd(90).range(14)).build());
        out.add(m("many_eyes", "Many Eyes", Theme.NIGHTMARE, Tier.NORMAL, Model.SPIDER, "A low dark heap covered in shut eyelids: when it stares every eye opens and you slow; they shut again, and that is when to go in").hp(18).dmg(3).spd(0.3).rig().size(1.1F, 0.9F).open(30).fall(24)
                .ab(a(DARKNESS, "Staring").radius(8).fx(MobEffects.SLOWNESS, 60, 1).cd(200)).build());
        out.add(m("the_tall_one", "The Tall One", Theme.NIGHTMARE, Tier.ELITE, Model.ENDERMAN, "A long black coat on a stand, taller than the door; it is always in the doorway behind you").hp(26).dmg(5).spd(0.3).rig().size(0.7F, 3.2F).open(26).fall(36)
                .ab(a(BLINK, "Doorway"), a(DARKNESS, "Lights Off")).build());
        out.add(m("the_waking", "The Waking", Theme.NIGHTMARE, Tier.WARDEN, Model.WARDEN, "A giant of rumpled bedclothes with an alarm clock caged in its chest: the ringing builds before the call; stop the clock and it sleeps for a moment").hp(36).dmg(6.5).spd(0.28).traits(HUNTER).rig().size(1.3F, 2.4F).stopped().open(30).fall(40)
                .ab(a(BEAM, "Wake-Up Call").bolt(BoltKind.DREAM).power(1.66F).wind(34).heavy(), a(SLAM, "Thrash").radius(4), a(SUMMON, "Bad Dreams").summon("night_terror").n(2)).build());
        out.add(m("the_sleeper", "The Sleeper", Theme.NIGHTMARE, Tier.LORD, Model.ZOMBIE, "Sleepwalks between dream and waking; when it wakes, everything does").hp(45.2).dmg(3.78).spd(0.24).scale(2.2F).size(1.0F, 1.8F).traits(HAUNTED).bar(BossBarColor.PINK).arena(22)
                .intro("You've been here before. You haven't.")
                .phase(1.0F, "The Sleeper stirs.", a(SLAM, "Restless Fist").radius(4.5F), a(DARKNESS, "Heavy Eyelids"))
                .phase(0.66F, "It turns over in its sleep.", a(SUMMON, "Night Terrors").summon("night_terror").n(2), a(BLINK, "Dreamstep"), a(SLAM, "Restless Fist").radius(4.5F))
                .phase(0.33F, "It wakes.", a(BURST, "Rude Awakening").power(1.94F).radius(14).inner(3.5F).bolt(BoltKind.DREAM).heavy(), a(SLAM, "Restless Fist").radius(5))
                .parts(P("sleeper_eyelid", "Sleeper's Eyelid", "dream"), P("sleepers_lullaby", "Sleeper's Lullaby", "dream")).build());
    }

    private static void skyArchipelago(List<MobDef> out) {
        out.add(m("gale_harpy", "The Kite", Theme.SKY, Tier.NORMAL, Model.PHANTOM, "Skin stretched on a diamond of bones, a hooked beak at its point and a long knotted tail; it climbs, folds and drops").hp(14).dmg(3).spd(0.45).rig().size(0.9F, 0.5F).miss(30)
                .ab(a(CHARGE, "Dive Bomb").wind(14).cd(90).range(14)).build());
        out.add(m("cloud_breeze", "The Puffer", Theme.SKY, Tier.NORMAL, Model.BREEZE, "A ball of cloud that swells to twice its size before it blows you toward the edge, and is small and slow after").hp(16).dmg(3).spd(0.28).traits(RANGED).rig().shot(ShotLook.GUST, 0.55F, 0.3F).moves(Model.Move.FLY).size(0.7F, 0.7F).open(26)
                .ab(a(GUST, "Updraft").radius(5).power(0.6F), a(BOLT, "Wind Bolt").bolt(BoltKind.GRAVITY)).build());
        out.add(m("sky_raider", "The Balloonist", Theme.SKY, Tier.NORMAL, Model.PILLAGER, "A small clawed thing hanging in a basket under a patched balloon; its bolts lift you, and a blow as it aims spoils the shot").hp(18).dmg(3).spd(0.26).traits(RANGED).rig().shot(ShotLook.QUARREL, 0.45F, 0.4F).moves(Model.Move.FLY).size(0.7F, 1.4F).stopped()
                .ab(a(BOLT, "Grav-Bolt").bolt(BoltKind.GRAVITY)).build());
        out.add(m("thunderhead", "The Thunderhead", Theme.SKY, Tier.ELITE, Model.GHAST, "A flat-topped block of dark cloud with a bright core, trailing rain: the core brightens, then lightning lands on the marked ground").hp(22).dmg(4).spd(0.25).traits(RANGED).rig().shot(ShotLook.ARC, 0.5F, 0.0F).size(1.2F, 1.0F).open(26)
                .ab(a(RAIN, "Lightning Call").n(2).bolt(BoltKind.CHARGE), a(BOLT, "Static").n(2).bolt(BoltKind.CHARGE)).build());
        out.add(m("the_gale", "The Gale", Theme.SKY, Tier.WARDEN, Model.BREEZE, "A turning column of wind with a wreck in it: sails, rope and a ship's wheel at its heart; the wind opens when it has just thrown you").hp(32).dmg(6).spd(0.3).traits(HUNTER).rig().shot(ShotLook.SPAR, 1.0F, 0.4F).moves(Model.Move.FLY).size(1.4F, 2.4F).open(30)
                .ab(a(GUST, "Gale Force").radius(8).power(0.8F).heavy(), a(LIFT, "Updraft Trap"), a(BOLT, "Wind Spears").n(4).bolt(BoltKind.GRAVITY)).build());
        out.add(m("sky_leviathan", "The Sky Leviathan", Theme.SKY, Tier.LORD, Model.SERPENT, "Dives through the sky, tilts gravity, then the Storm Eye").hp(46.5).dmg(5.73).spd(0.32).scale(3.2F).size(1.4F, 0.6F).bar(BossBarColor.BLUE).arena(28)
                .intro("The ground is much too far below.")
                .phase(1.0F, "The Sky Leviathan circles.", a(CHARGE, "Leviathan Dive").power(1.16F).heavy(), a(GUST, "Wingbeat").radius(10).power(0.6F))
                .phase(0.66F, "The sky tilts.", a(LIFT, "Gravity Tide").radius(4), a(BOLT, "Pearl Barrage").n(6).bolt(BoltKind.GRAVITY), a(CHARGE, "Leviathan Dive").power(1.16F))
                .phase(0.33F, "Storm Eye.", a(RAIN, "Storm Eye").n(7).bolt(BoltKind.CHARGE).heavy(), a(CHARGE, "Leviathan Dive").power(1.16F), a(GUST, "Wingbeat").radius(10).power(0.7F))
                .parts(P("leviathan_fin", "Leviathan Fin", "cloud"), P("sky_pearl", "Sky Pearl", "signal")).build());
    }

    private static void abyssalShrine(List<MobDef> out) {
        out.add(m("abyss_watcher", "The Watcher", Theme.ABYSS, Tier.NORMAL, Model.GUARDIAN, "A stone lamp-bowl that floats, one lidless eye on a stalk rising out of it; the stalk turns to you before the gaze").hp(18).dmg(3.5).spd(0.25).traits(RANGED).rig().size(0.7F, 1.4F).planted().open(24)
                .ab(a(BEAM, "Hollow Gaze").bolt(BoltKind.VOID)).build());
        out.add(m("silent_stalker", "The Silent One", Theme.ABYSS, Tier.NORMAL, Model.ENDERMAN, "Long limbs and no outline, a hole in the light with two rings where eyes would be; it makes no sound at all").hp(24).dmg(4).spd(0.3).rig().size(1.0F, 1.4F).fall(28)
                .ab(a(BLINK, "Soundless Step")).build());
        out.add(m("void_spawn", "The Void Spawn", Theme.ABYSS, Tier.NORMAL, Model.ENDERMITE, "Drops of dark with whip tails, hopping; they pour out of dark corners").hp(10).dmg(2.5).spd(0.3).rig().size(0.6F, 0.75F).miss(26)
                .ab(a(LEAP, "Void Bite").radius(1.5F).power(0.9F).range(6).wind(10)).build());
        out.add(m("hollow_priest", "The Hollow Priest", Theme.ABYSS, Tier.ELITE, Model.WITHER_SKELETON, "A vestment held open round nothing, a ring of light above it; the robe opens before it pulls you in").hp(26).dmg(5).spd(0.26).traits(WITHER_TOUCH).rig().shot(ShotLook.RING, 1.9F, 0.3F).size(0.9F, 2.0F).open(28).fall(28)
                .ab(a(BOLT, "Hollow Bolt").n(3).bolt(BoltKind.VOID), a(PULL, "Draw In")).build());
        out.add(m("the_nothing", "The Nothing", Theme.ABYSS, Tier.WARDEN, Model.WITHER, "Three hooded hollows on long necks from one sunken body; the pull comes when all three look at you together").hp(38).dmg(6.5).spd(0.28).traits(HUNTER).rig().shot(ShotLook.HOOD, 1.9F, 0.5F).moves(Model.Move.GROUND).size(1.6F, 2.2F).open(30).fall(40)
                .ab(a(BOLT, "Nothing Skulls").n(3).bolt(BoltKind.VOID), a(PULL, "Event Horizon").power(0.7F).heavy(), a(DARKNESS, "Unlight")).build());
        out.add(m("hollow_herald", "The Hollow Herald", Theme.ABYSS, Tier.LORD, Model.SHADE, "Void bolts, an unlit shrine, then the Event Horizon").hp(56.6).dmg(6.0).spd(0.28).scale(1.4F).size(0.85F, 3.65F).grounded().bar(BossBarColor.PURPLE).arena(24)
                .intro("Nothing. No sound at all.")
                .phase(1.0F, "The Herald speaks without sound.", a(BOLT, "Void Bolts").n(5).bolt(BoltKind.VOID), a(PULL, "Draw Near"))
                .phase(0.66F, "The shrine empties.", a(SUMMON, "Spawn of Nothing").summon("void_spawn").n(2), a(DARKNESS, "Unlit"), a(BOLT, "Void Bolts").n(5).bolt(BoltKind.VOID))
                .phase(0.33F, "Event horizon.", a(PULL, "Event Horizon").radius(16).power(0.8F).heavy(), a(BURST, "Collapse").power(1.18F).radius(13).inner(3.5F).bolt(BoltKind.VOID), a(NOVA, "Void Halo").n(16).bolt(BoltKind.VOID))
                .parts(P("herald_halo", "Herald's Halo", "abyss"), P("abyssal_glass", "Abyssal Glass", "abyss")).build());
    }
}
