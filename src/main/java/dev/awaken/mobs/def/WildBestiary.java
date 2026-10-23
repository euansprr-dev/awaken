package dev.awaken.mobs.def;

import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

import static dev.awaken.mobs.def.Ability.Kind.*;
import static dev.awaken.mobs.def.MobDef.P;
import static dev.awaken.mobs.def.MobDef.m;
import static dev.awaken.mobs.def.Trait.*;

/**
 * The Awakened layer, part two (plan v2 P2, "the living world"): creatures for the rest of the land, six more biome
 * families with the P1 role mix (mostly ambient, a neutral, a night hunter where it fits, one named beast each). Every
 * creature has a talent that helps or hinders the player where they can see it, and each family drops one or two
 * materials ({@code <family>_<thing>}, registered by the world module) for Hunter gear. Spawning, lairs and the rest
 * live in {@code dev.awaken.world.fauna}; see docs/modules/fauna.md, "P2".
 *
 * <p>Keep each {@code m(...)} call on one line: tools/art/specs/fauna2.py reads them for textures, lang and loot.
 */
final class WildBestiary {
    private WildBestiary() {}

    private static Ability.Builder a(Ability.Kind k, String name) {
        return Ability.of(k, name);
    }

    static void define(List<MobDef> out) {
        mire(out);
        canopy(out);
        duskwood(out);
        blossom(out);
        crag(out);
        spore(out);
    }

    // Swamp, mangrove swamp.
    private static void mire(List<MobDef> out) {
        out.add(m("bogwick", "Bogwick", Theme.MIRE, Tier.NORMAL, Model.BEE, "A swamp firefly that drifts to anyone poisoned and draws the venom out with its light").hp(4).dmg(1).spd(0.3).scale(0.7F).xp(1).tint(0x6E7A3C).glow(0xB8FF6A).voice(Model.Voice.FIREFLY).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.CLEANSE).build());
        out.add(m("mudskipper", "Mudskipper", Theme.MIRE, Tier.NORMAL, Model.LIZARD, "Flops along the mud and the shallows, digging up clay and slime as it goes").hp(6).dmg(1).spd(0.25).scale(0.75F).xp(1).tint(0xFFFFFF).glow(0x9CFFD8).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.FORAGE).build());
        out.add(m("bog_toad", "Bog Toad", Theme.MIRE, Tier.NORMAL, Model.FROG, "A toad the size of a dog that snaps up small slimes; strike it and its tongue drags you in").hp(20).dmg(3.5).spd(0.22).scale(1.7F).xp(6).tint(0x5E6E34).glow(0xB8FF6A).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.GULP)
                .ab(a(PULL, "Tongue Lash").radius(6.0F).power(0.55F).range(7).cd(160), a(SLAM, "Belly Flop").radius(3.0F).power(1.1F).cd(130)).build());
        out.add(m("mire_mother", "The Mire Mother", Theme.MIRE, Tier.WARDEN, Model.FROG, "A toad as big as a hut, crowned with a glowing stone; the bog moves when she does").hp(30).dmg(4.5).spd(0.24).scale(3.6F).xp(60).arena(40).tint(0x4E5E2C).glow(0xB8FF6A).bar(BossBarColor.GREEN).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(PULL, "Tongue Lash").radius(9.0F).power(0.6F).heavy().cd(140), a(SLAM, "Belly Flop").radius(4.5F).heavy().cd(120))
                .phase(0.6F, "The Mire Mother bellows, and the bog answers.", a(CLOUD, "Bog Breath").radius(3.0F).fx(MobEffects.POISON, 80, 0), a(LEAP, "Great Leap").power(1.5F).radius(3.5F), a(PULL, "Tongue Lash").radius(9.0F).power(0.6F).cd(120))
                .phase(0.3F, "Her crown blazes through the murk.", a(NOVA, "Toadstone Shards").n(10).bolt(BoltKind.SPORE), a(FRENZY, "Last Croak"), a(SLAM, "Belly Flop").radius(5.0F).power(1.6F).heavy().cd(100))
                .parts(P("toadstone", "Toadstone", "moss")).build());
    }

    // Jungle, sparse jungle, bamboo jungle.
    private static void canopy(List<MobDef> out) {
        out.add(m("dart_frog", "Dart Frog", Theme.CANOPY, Tier.NORMAL, Model.FROG, "A jewel-bright frog that climbs the trunks; its skin is venom, so never strike it bare-handed").hp(4).dmg(1).spd(0.25).scale(0.6F).xp(1).tint(0x2A6EA8).glow(0x5CF0FF).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.VENOM).build());
        out.add(m("crested_basilisk", "Crested Basilisk", Theme.CANOPY, Tier.NORMAL, Model.LIZARD, "A crested lizard that sprints on its hind legs, straight across rivers, when startled").hp(18).dmg(3.5).spd(0.3).scale(1.3F).xp(6).tint(0xFFFFFF).glow(0xFFD24A).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.SPRINT)
                .ab(a(CHARGE, "Crest Rush").power(1.2F).range(8).cd(110)).build());
        out.add(m("canopy_prowler", "Canopy Prowler", Theme.CANOPY, Tier.NORMAL, Model.OCELOT, "A shadow-black cat with glowing rosettes; it hunts the jungle floor at night and never steps into firelight").hp(16).dmg(4).spd(0.32).scale(1.6F).xp(8).tint(0x26222E).glow(0x7CFFB0).voice(Model.Voice.PROWLER).fauna(FaunaRole.HUNTER).talent(FaunaTalent.STALK)
                .ab(a(LEAP, "Pounce").power(1.2F).radius(1.8F).wind(14).cd(80).range(7)).build());
        out.add(m("plumed_coil", "The Plumed Coil", Theme.CANOPY, Tier.WARDEN, Model.SERPENT, "A feathered serpent that swims the air between the jungle trees; its plumes rattle before it strikes").hp(26).dmg(4.5).spd(0.5).scale(1.5F).xp(60).arena(40).tint(0xFFFFFF).glow(0x5CF0FF).bar(BossBarColor.GREEN).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(CHARGE, "Coil Strike").power(1.4F).cd(110), a(BLINK, "Canopy Drop").power(1.2F).cd(140))
                .phase(0.6F, "Its plumes flare and rattle.", a(RAIN, "Venom Rain").n(3).bolt(BoltKind.THORN), a(CONE, "Venom Spray").fx(MobEffects.POISON, 60, 0), a(CHARGE, "Coil Strike").power(1.4F).cd(100))
                .phase(0.3F, "The Plumed Coil sheds a storm of feathers.", a(NOVA, "Plume Storm").n(12).bolt(BoltKind.THORN), a(GUST, "Wingbeat").radius(8.0F).power(0.7F), a(FRENZY, "Coiled Fury"))
                .parts(P("coil_plume", "Coil Plume", "leaf")).build());
    }

    // Dark forest, pale garden.
    private static void duskwood(List<MobDef> out) {
        out.add(m("gloam_snail", "Gloam Snail", Theme.DUSKWOOD, Tier.NORMAL, Model.SNAIL, "A snail with a lantern-glow shell; where it rests in the dark it leaves glowing lichen behind").hp(6).dmg(1).spd(0.12).xp(1).tint(0xFFFFFF).glow(0x9CF0FF).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.TRAIL).build());
        out.add(m("hollow_owl", "Hollow Owl", Theme.DUSKWOOD, Tier.NORMAL, Model.PARROT, "A great grey owl that watches the dark for you; whatever it stares at starts to glow").hp(16).dmg(3).spd(0.3).scale(1.8F).xp(6).tint(0x8C8478).glow(0xFFD27A).voice(Model.Voice.OWL).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.WATCH)
                .ab(a(LEAP, "Talon Dive").power(1.1F).radius(1.6F).wind(12).cd(100).range(7)).build());
        out.add(m("thornling", "Thornling", Theme.DUSKWOOD, Tier.NORMAL, Model.CREAKING, "A knot of living thorns that hunts in packs after dark; one torch and it lets you be").hp(14).dmg(3.5).spd(0.3).scale(0.6F).xp(8).tint(0x5A4A3A).glow(0xFF8A3A).traits(POISON_TOUCH).fauna(FaunaRole.HUNTER).talent(FaunaTalent.STALK)
                .ab(a(LEAP, "Thorn Lunge").power(1.1F).radius(1.6F).wind(14).cd(90).range(6)).build());
        out.add(m("weaver_queen", "The Weaver Queen", Theme.DUSKWOOD, Tier.WARDEN, Model.SPIDER, "A spider as wide as a cart whose webs hang the old trees; she wraps you before she bites").hp(28).dmg(4.5).spd(0.3).scale(2.2F).xp(60).arena(40).tint(0x3A3446).glow(0xD8C8FF).bar(BossBarColor.PURPLE).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(ROOT, "Web Snare").radius(2.5F), a(LEAP, "Pounce").power(1.4F).radius(2.5F).cd(110))
                .phase(0.6F, "The Weaver Queen climbs into her webs.", a(RAIN, "Silk Drop").n(3).bolt(BoltKind.SPIRIT), a(CONE, "Venom Spray").fx(MobEffects.POISON, 60, 0), a(LEAP, "Pounce").power(1.4F).radius(2.5F).cd(100))
                .phase(0.3F, "Her eyes burn through the dark.", a(DARKNESS, "Shroud").radius(14.0F), a(NOVA, "Web Burst").n(10).bolt(BoltKind.SPIRIT), a(FRENZY, "Brood Fury"))
                .parts(P("queen_silk", "Queen's Silk", "cloud")).build());
    }

    // Cherry grove, flower forest, birch forests.
    private static void blossom(List<MobDef> out) {
        out.add(m("petalwing", "Petalwing", Theme.BLOSSOM, Tier.NORMAL, Model.MOTH, "A butterfly with petal wings; wherever it rests on open grass, a flower opens").hp(4).dmg(1).spd(0.3).scale(0.85F).xp(1).tint(0xFFFFFF).glow(0xFFB8E0).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.POLLINATE).build());
        out.add(m("honey_bear", "Honey Bear", Theme.BLOSSOM, Tier.NORMAL, Model.POLAR_BEAR, "A sleepy bear that ambles from hive to hive; follow it and it shakes the honeycomb down").hp(30).dmg(4.5).spd(0.22).scale(0.85F).kb(0.4).xp(6).tint(0x8C5A2E).glow(0xFFC850).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.HONEY)
                .ab(a(SLAM, "Maul").radius(2.5F).power(1.2F).cd(110)).build());
        out.add(m("empress_moth", "The Empress Moth", Theme.BLOSSOM, Tier.WARDEN, Model.MOTH, "A moth as wide as a house whose wings rain petals; her dust puts the careless to sleep").hp(26).dmg(4.5).spd(0.4).scale(4.0F).xp(60).arena(40).tint(0xFFFFFF).glow(0xFFB8E0).bar(BossBarColor.PINK).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(GUST, "Wingbeat").radius(8.0F).power(0.6F).cd(160), a(RAIN, "Petal Storm").n(3).bolt(BoltKind.HONEY))
                .phase(0.6F, "The Empress Moth's eyespots open.", a(CLOUD, "Slumber Dust").radius(3.0F).fx(MobEffects.SLOWNESS, 80, 1), a(RAIN, "Petal Storm").n(4).bolt(BoltKind.HONEY), a(CHARGE, "Dive").power(1.4F).cd(110))
                .phase(0.3F, "Her wings blaze like dawn.", a(NOVA, "Scale Burst").n(12).bolt(BoltKind.DREAM), a(BEAM, "Eyespot Glare").bolt(BoltKind.SPIRIT).power(1.4F).heavy(), a(FRENZY, "Last Flight"))
                .parts(P("empress_scale", "Empress Wingscale", "dream")).build());
    }

    // Stony and jagged peaks, frozen peaks, windswept hills, stony shores.
    private static void crag(List<MobDef> out) {
        out.add(m("scree_marmot", "Scree Marmot", Theme.CRAG, Tier.NORMAL, Model.MARMOT, "A fat marmot that digs flint and nuggets out of the scree; it whistles and dives if you run at it").hp(6).dmg(1).spd(0.26).xp(1).tint(0xFFFFFF).glow(0xFFD27A).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.DIG).build());
        out.add(m("peak_kite", "Peak Kite", Theme.CRAG, Tier.NORMAL, Model.PHANTOM, "A white kite that rides the updrafts over the peaks; now and then a feather drifts down to you").hp(6).dmg(1).spd(0.45).scale(0.8F).xp(1).tint(0xD8DCE2).glow(0xA8D8FF).voice(Model.Voice.KITE).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.SOAR).build());
        out.add(m("crag_ram", "Crag Ram", Theme.CRAG, Tier.NORMAL, Model.GOAT, "A shaggy mountain ram that grazes the ledges; corner it and it butts you off them").hp(24).dmg(4).spd(0.26).scale(1.25F).kb(0.5).xp(6).tint(0xB8B0A4).glow(0xA8D8FF).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.GRAZE)
                .ab(a(CHARGE, "Ram").power(1.5F).range(10).heavy().cd(110)).build());
        out.add(m("stonehorn", "The Stonehorn", Theme.CRAG, Tier.WARDEN, Model.GOAT, "A ram the size of a boulder whose horns are rock; when it stamps, the mountain sheds stones").hp(30).dmg(4.5).spd(0.26).scale(2.6F).xp(60).arena(44).tint(0x8C8C90).glow(0xA8D8FF).bar(BossBarColor.WHITE).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(CHARGE, "Ram Charge").power(1.6F).heavy().cd(110), a(SLAM, "Stamp").radius(4.5F).cd(120))
                .phase(0.6F, "The Stonehorn's stamping shakes stones loose.", a(RAIN, "Rockfall").n(4).bolt(BoltKind.PLAIN), a(GUST, "Avalanche Bellow").radius(8.0F).power(0.7F), a(CHARGE, "Ram Charge").power(1.6F).heavy().cd(100))
                .phase(0.3F, "Its horns crack and glow.", a(BURST, "Quake Ring").radius(10.0F).inner(3.5F).power(1.6F).heavy(), a(FRENZY, "Last Stand"), a(CHARGE, "Ram Charge").power(1.7F).heavy().cd(80))
                .parts(P("stonehorn_shard", "Stonehorn Shard", "stone")).build());
    }

    // Mushroom fields.
    private static void spore(List<MobDef> out) {
        out.add(m("shroomlet", "Shroomlet", Theme.SPORE, Tier.NORMAL, Model.MUSHROOM, "A walking mushroom no taller than your knee; it puffs spores when startled and plants mushrooms in the shade").hp(6).dmg(1).spd(0.22).scale(0.9F).xp(1).tint(0xFFFFFF).glow(0xFF9AE0).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.SPORE).build());
        out.add(m("sporeback_snail", "Sporeback Snail", Theme.SPORE, Tier.NORMAL, Model.SNAIL, "A snail with a mushroom farm on its shell; mushrooms it rests beside swell into giants").hp(8).dmg(1).spd(0.1).scale(1.3F).xp(1).tint(0xFFFFFF).glow(0xFF9AE0).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.MYCEL).build());
        out.add(m("mycelord", "The Mycelord", Theme.SPORE, Tier.WARDEN, Model.MUSHROOM, "A mushroom as tall as a tree that walks its island; its cap breathes clouds of spores").hp(30).dmg(4.5).spd(0.2).scale(4.2F).xp(60).arena(40).tint(0xFFFFFF).glow(0xFF9AE0).bar(BossBarColor.PINK).voice(Model.Voice.MYCELORD).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(SLAM, "Cap Slam").radius(4.5F).heavy().cd(120), a(CLOUD, "Spore Cloud").radius(3.0F).fx(MobEffects.POISON, 60, 0))
                .phase(0.6F, "The Mycelord shudders and its gills open.", a(NOVA, "Spore Burst").n(10).bolt(BoltKind.SPORE), a(RAIN, "Spore Rain").n(3).bolt(BoltKind.SPORE), a(SLAM, "Cap Slam").radius(4.5F).heavy().cd(100))
                .phase(0.3F, "Its cap splits and glows.", a(BURST, "Mycel Ring").radius(9.0F).inner(3.0F).power(1.5F).heavy(), a(CLOUD, "Choking Spores").radius(3.5F).fx(MobEffects.NAUSEA, 100, 0), a(FRENZY, "Last Bloom"))
                .parts(P("mycel_heart", "Mycel Heart", "spore")).build());
    }
}
