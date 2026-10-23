package dev.awaken.mobs.def;

import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

import static dev.awaken.mobs.def.Ability.Kind.*;
import static dev.awaken.mobs.def.MobDef.P;
import static dev.awaken.mobs.def.MobDef.m;
import static dev.awaken.mobs.def.Trait.*;

/**
 * The Awakened layer's first creatures (plan v2 section 4.2): twelve overworld natives in three biome families, each
 * with a purpose. Four ambient (never hostile), three neutral (fight back), two night hunters (hostile only in
 * darkness) and one rare named beast per family. Spawning, sightings and the reveal live in the world module
 * ({@code dev.awaken.world.fauna}); biomes and weights in {@code data/awaken/neoforge/biome_modifier/fauna_*.json}.
 *
 * <p>Keep each {@code m(...)} call on one line: tools/art/specs/fauna.py reads them for textures, lang and loot.
 * Our own bodies are drawn untinted ({@code tint(0xFFFFFF)}); vanilla bodies get a tint close to their skin so a
 * gap in the skin layer never shows the vanilla colours.
 */
final class FaunaBestiary {
    private FaunaBestiary() {}

    private static Ability.Builder a(Ability.Kind k, String name) {
        return Ability.of(k, name);
    }

    static void define(List<MobDef> out) {
        greenwild(out);
        sunscorch(out);
        frostwild(out);
    }

    // Forest, plains, meadow, cherry grove.
    private static void greenwild(List<MobDef> out) {
        out.add(m("lumen_moth", "Lumen Moth", Theme.GREENWILD, Tier.NORMAL, Model.MOTH, "Glows from dusk to dawn; flocks to torchlight and circles above buried ore").hp(4).dmg(1).spd(0.32).xp(1).tint(0xFFFFFF).glow(0xFFD27A).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.DOWSE).build());
        out.add(m("bloom_sprite", "Bloom Sprite", Theme.GREENWILD, Tier.NORMAL, Model.ALLAY, "Drifts between flowers by day; crops and saplings it touches grow").hp(6).dmg(1).spd(0.4).xp(1).tint(0xF6D2E8).glow(0xFFE2F2).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.TEND).build());
        out.add(m("mossback", "Mossback", Theme.GREENWILD, Tier.NORMAL, Model.TORTOISE, "A grazing tortoise with a garden on its shell; hides in it, then slams").hp(30).dmg(4).spd(0.13).armor(6).kb(0.6).scale(1.5F).xp(6).tint(0xFFFFFF).glow(0x9CFF7A).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.GRAZE)
                .ab(a(GUARD, "Shell Up").cd(140), a(SLAM, "Shell Slam").radius(3.0F).power(1.2F).cd(120)).build());
        out.add(m("gloomstalker", "Gloomstalker", Theme.GREENWILD, Tier.NORMAL, Model.FOX, "Stalks you through the dark, low to the ground, then pounces; it never steps into torchlight").hp(16).dmg(4).spd(0.3).scale(1.35F).xp(8).tint(0x3E3550).glow(0xC08CFF).voice(Model.Voice.STALKER).fauna(FaunaRole.HUNTER).talent(FaunaTalent.STALK)
                .ab(a(LEAP, "Pounce").power(1.2F).radius(1.8F).wind(14).cd(80).range(7)).build());
        out.add(m("briar_sow", "The Briar Sow", Theme.GREENWILD, Tier.WARDEN, Model.BRIAR_SOW, "A boar the forest grew a hedge on: she snorts once, then charges, and her thorns root you").hp(28).dmg(4.5).spd(0.24).armor(4).scale(1.15F).xp(60).arena(40).tint(0xFFFFFF).glow(0x9CFF7A).bar(BossBarColor.GREEN).voice(Model.Voice.BOAR).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(CHARGE, "Bramble Charge").power(1.5F).heavy().cd(110), a(SLAM, "Root Stomp").radius(4.0F).cd(120))
                .phase(0.6F, "The Briar Sow tears up the earth.", a(ROOT, "Thornsnare").radius(2.5F), a(CLOUD, "Pollen Haze").fx(MobEffects.SLOWNESS, 60, 1), a(CHARGE, "Bramble Charge").power(1.5F).heavy().cd(100))
                .phase(0.3F, "Thorns burst from her hide.", a(FRENZY, "Last Stand"), a(NOVA, "Thorn Burst").n(10).bolt(BoltKind.THORN), a(CHARGE, "Bramble Charge").power(1.6F).heavy().cd(80))
                .parts(P("briar_tusk", "Briar Tusk", "moss")).build());
    }

    // Desert, savanna, badlands.
    private static void sunscorch(List<MobDef> out) {
        out.add(m("glass_scarab", "Glass Scarab", Theme.SUNSCORCH, Tier.NORMAL, Model.SCARAB, "Scuttles over hot sand and dives into it when you stomp close; sneak up to catch its quartz").hp(4).dmg(1).spd(0.3).scale(1.4F).xp(1).tint(0xFFFFFF).glow(0xFFF4D0).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.BURROW).build());
        out.add(m("dune_ray", "Dune Ray", Theme.SUNSCORCH, Tier.NORMAL, Model.RAY, "Glides a hand's breadth over the dunes; strike it and it lashes back with its tail").hp(20).dmg(4).spd(0.45).scale(1.4F).xp(6).tint(0xFFFFFF).glow(0x5FE8D8).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.GLIDE)
                .ab(a(CHARGE, "Tail Lash").power(1.2F).range(10).cd(100), a(GUST, "Sand Gust").radius(5.0F).power(0.6F).cd(200)).build());
        out.add(m("dune_wyrm", "The Dune Wyrm", Theme.SUNSCORCH, Tier.WARDEN, Model.DUNE_WYRM, "Swims through the air over the sand as if it were water; it sinks away and surfaces beside you").hp(26).dmg(4.5).spd(0.5).scale(1.4F).xp(60).arena(40).tint(0xFFFFFF).glow(0xFFB347).bar(BossBarColor.YELLOW).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(BLINK, "Surface").power(1.2F).cd(140), a(CHARGE, "Sand Dive").power(1.4F).cd(110))
                .phase(0.6F, "The Dune Wyrm sinks, and the sand starts to fall.", a(RAIN, "Sandfall").n(3).bolt(BoltKind.GLASS), a(BLINK, "Surface").power(1.2F).cd(120), a(CHARGE, "Sand Dive").power(1.4F).cd(100))
                .phase(0.3F, "Glass cracks along its scales.", a(BURST, "Sunscorch Ring").radius(9.0F).inner(3.0F).power(1.6F).heavy(), a(NOVA, "Glass Shards").n(12).bolt(BoltKind.GLASS), a(BLINK, "Surface").power(1.3F).cd(100))
                .parts(P("wyrm_scale", "Wyrm Scale", "sand")).build());
    }

    // Taiga, snowy plains and slopes, grove.
    private static void frostwild(List<MobDef> out) {
        out.add(m("frostfinch", "Frostfinch", Theme.FROSTWILD, Tier.NORMAL, Model.PARROT, "Hops about in flocks and scatters if you run at it; warms itself at campfires").hp(4).dmg(1).spd(0.28).xp(1).tint(0xEEF6FF).glow(0x9FE8FF).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.FLOCK).build());
        out.add(m("frost_stag", "Frost Stag", Theme.FROSTWILD, Tier.NORMAL, Model.STAG, "Grazes at the tree line and bolts from running feet; corner it and it charges").hp(22).dmg(4).spd(0.3).kb(0.3).xp(6).tint(0xFFFFFF).glow(0x9FE8FF).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.SKITTISH)
                .ab(a(CHARGE, "Antler Rush").power(1.3F).range(10).cd(100)).build());
        out.add(m("wisp_hound", "Wisp Hound", Theme.FROSTWILD, Tier.NORMAL, Model.SNOW_WOLF, "Hunts the snow in packs at night; its bite numbs, and it will not follow you into firelight").hp(14).dmg(3.5).spd(0.33).scale(1.1F).xp(8).tint(0xB8DCFF).glow(0x9FE8FF).traits(SLOW_TOUCH).voice(Model.Voice.WISP).fauna(FaunaRole.HUNTER).talent(FaunaTalent.STALK)
                .ab(a(LEAP, "Frost Bite").power(1.0F).radius(1.6F).wind(12).cd(90).range(6)).build());
        out.add(m("old_antler", "The Old Antler", Theme.FROSTWILD, Tier.WARDEN, Model.OLD_ANTLER, "An ancient stag whose antlers carry the forest's light; it guards one valley and bellows you off it").hp(30).dmg(4.5).spd(0.28).scale(1.2F).xp(60).arena(48).tint(0xFFFFFF).glow(0x7FC3FF).bar(BossBarColor.BLUE).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(CHARGE, "Antler Rush").power(1.5F).heavy().cd(110), a(SLAM, "Hoof Quake").radius(4.5F).cd(120))
                .phase(0.6F, "The forest holds its breath.", a(GUST, "Bellow").radius(8.0F).power(0.7F), a(ROOT, "Root Snare").radius(2.5F), a(CHARGE, "Antler Rush").power(1.5F).heavy().cd(100))
                .phase(0.3F, "The Old Antler's crown blazes.", a(BEAM, "Crown Light").bolt(BoltKind.SPIRIT).power(1.5F).heavy(), a(BURST, "Rime Ring").radius(10.0F).inner(3.5F).bolt(BoltKind.FROST), a(FRENZY, "The Last Rut"))
                .parts(P("antler_tine", "Antler Tine", "frost")).build());
    }
}
