package dev.awaken.mobs.def;

import net.minecraft.world.BossEvent.BossBarColor;

import java.util.List;

import static dev.awaken.mobs.def.Ability.Kind.*;
import static dev.awaken.mobs.def.MobDef.P;
import static dev.awaken.mobs.def.MobDef.m;

/**
 * The Awakened layer, part three (P3): the Tidewater, the creatures of the rivers, lakes and seas. A shimmering school
 * fish and a jellyfish that carries air (ambient), a river otter that dives for finds and a sea horse you can tame and
 * ride (neutral), and the Lantern Maw, a deep-sea angler with a lair in a sunken shrine. They spawn in their own
 * category ({@code awaken:fauna_water}) with their own caps, so they never take a slot from vanilla's fish and squid
 * or from the land's creatures. Spawning, lairs and mounts live in {@code dev.awaken.world.fauna}; see
 * docs/modules/fauna.md, "P3".
 *
 * <p>Keep each {@code m(...)} call on one line: tools/art/specs/fauna3.py reads them for textures, lang and loot.
 * Swim speeds are attribute values for a swimming move control (the in-water speed is a fiftieth of it per tick).
 */
final class WaterBestiary {
    private WaterBestiary() {}

    private static Ability.Builder a(Ability.Kind k, String name) {
        return Ability.of(k, name);
    }

    static void define(List<MobDef> out) {
        out.add(m("glimmerfin", "Glimmerfin", Theme.TIDEWATER, Tier.NORMAL, Model.GLIMMERFIN, "A river fish that shimmers in schools; swim through a school and your eyes open to the dark water").hp(3).dmg(1).spd(1.0).xp(1).tint(0xFFFFFF).glow(0x9CF6FF).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.SHOAL).build());
        out.add(m("breathbell", "Breathbell Jelly", Theme.TIDEWATER, Tier.NORMAL, Model.BREATHBELL, "A jellyfish with a bell of air under its dome; a drowning swimmer who touches it breathes again").hp(6).dmg(1).spd(0.45).xp(1).tint(0xFFFFFF).glow(0xB8F0FF).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.AIRBELL).build());
        out.add(m("brook_otter", "Brook Otter", Theme.TIDEWATER, Tier.NORMAL, Model.BROOK_OTTER, "A river otter that dives and brings up what the river hides; strike it and it bites back").hp(14).dmg(3).spd(1.1).xp(5).tint(0xFFFFFF).glow(0xFFD27A).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.FETCH)
                .ab(a(LEAP, "Pounce").power(1.0F).radius(1.4F).wind(10).cd(90).range(6)).build());
        out.add(m("tidesteed", "Tidesteed", Theme.TIDEWATER, Tier.NORMAL, Model.TIDESTEED, "A sea horse with a kelp mane that races the waves; win its trust with kelp and it carries you under the sea").hp(26).dmg(4).spd(1.3).kb(0.4).xp(6).tint(0xFFFFFF).glow(0x7FE8FF).fauna(FaunaRole.NEUTRAL).talent(FaunaTalent.TIDE)
                .ab(a(CHARGE, "Tail Lash").power(1.2F).range(8).cd(110), a(GUST, "Wave Surge").radius(4.0F).power(0.6F).cd(200)).build());
        out.add(m("lantern_maw", "The Lantern Maw", Theme.TIDEWATER, Tier.WARDEN, Model.LANTERN_MAW, "An angler as big as a boat; its lure is the only light in the deep, and its jaws open behind it").hp(28).dmg(4.5).spd(1.1).scale(1.3F).xp(60).arena(40).tint(0xFFFFFF).glow(0x7FFFE0).bar(BossBarColor.BLUE).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(CHARGE, "Maw Lunge").power(1.5F).heavy().cd(110), a(PULL, "Lure").radius(10.0F).power(0.6F).range(12).cd(150))
                .phase(0.6F, "The Lantern Maw's lure goes dark.", a(DARKNESS, "Black Water").radius(14.0F), a(BURST, "Pressure Wave").radius(8.0F).inner(3.0F).power(1.3F), a(CHARGE, "Maw Lunge").power(1.5F).heavy().cd(100))
                .phase(0.3F, "Its lure blazes and its teeth glow.", a(BEAM, "Abyss Glare").bolt(BoltKind.SPIRIT).power(1.4F).heavy(), a(NOVA, "Tooth Storm").n(10).bolt(BoltKind.WATER), a(FRENZY, "Deep Hunger"))
                .parts(P("maw_lantern", "Maw Lantern", "water")).build());
    }
}
