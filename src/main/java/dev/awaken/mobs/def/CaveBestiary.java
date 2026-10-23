package dev.awaken.mobs.def;

import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

import static dev.awaken.mobs.def.Ability.Kind.*;
import static dev.awaken.mobs.def.MobDef.P;
import static dev.awaken.mobs.def.MobDef.m;
import static dev.awaken.mobs.def.Trait.*;

/**
 * The Awakened layer, part three (P3): the Hollows, the creatures of the lush caves, the dripstone caves and the edge
 * of the deep dark. Two glow creatures (a cricket that sings beside ore, a salamander that ripens glow berries), two
 * night hunters that keep the light rule (a drip lurker that hangs from the roof, a blind hushling that hunts by sound)
 * and the Thousandfoot, a crystal-plated centipede with a lair in a cave of its own. See docs/modules/fauna.md, "P3".
 *
 * <p>Keep each {@code m(...)} call on one line: tools/art/specs/fauna3.py reads them for textures, lang and loot.
 */
final class CaveBestiary {
    private CaveBestiary() {}

    private static Ability.Builder a(Ability.Kind k, String name) {
        return Ability.of(k, name);
    }

    static void define(List<MobDef> out) {
        out.add(m("chime_cricket", "Chime Cricket", Theme.HOLLOWS, Tier.NORMAL, Model.CHIME_CRICKET, "A cave cricket with crystals on its back; it sings beside buried ore, faster as you come close").hp(4).dmg(1).spd(0.28).xp(1).tint(0xFFFFFF).glow(0xC8B4FF).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.CHIME).build());
        out.add(m("wickling", "Wickling", Theme.HOLLOWS, Tier.NORMAL, Model.WICKLING, "A cave salamander with a candle-flame crest; glow berries ripen on the vines it climbs past").hp(6).dmg(1).spd(0.25).xp(1).tint(0xFFFFFF).glow(0xFFB45A).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.RIPEN).build());
        out.add(m("drip_lurker", "Drip Lurker", Theme.HOLLOWS, Tier.NORMAL, Model.DRIP_LURKER, "Hangs from the cave roof like a dripstone fang and drops on whoever walks beneath it in the dark; torchlight keeps it hanging").hp(18).dmg(4).spd(0.3).xp(8).tint(0xFFFFFF).glow(0xFF8A4A).fauna(FaunaRole.HUNTER).talent(FaunaTalent.CLING)
                .ab(a(SLAM, "Spike Drop").radius(2.2F).power(1.3F).cd(100), a(LEAP, "Lunge").power(1.0F).radius(1.6F).wind(12).cd(80).range(6)).build());
        out.add(m("hushling", "Hushling", Theme.HOLLOWS, Tier.NORMAL, Model.HUSHLING, "Blind and pale, it hunts by sound at the edge of the deep dark; sneak, or stand in torchlight, and it passes you by").hp(14).dmg(3.5).spd(0.32).xp(8).tint(0xFFFFFF).glow(0x3AE8D8).traits(LISTENER).fauna(FaunaRole.HUNTER).talent(FaunaTalent.HUSH)
                .ab(a(LEAP, "Lunge").power(1.1F).radius(1.6F).wind(12).cd(80).range(6)).build());
        out.add(m("thousandfoot", "The Thousandfoot", Theme.HOLLOWS, Tier.WARDEN, Model.THOUSANDFOOT, "A centipede as long as a cave, plated in crystal; it coils round its nest and strikes out of the dark").hp(28).dmg(4.5).spd(0.3).scale(1.3F).xp(60).arena(32).tint(0xFFFFFF).glow(0xC8B4FF).bar(BossBarColor.PURPLE).fauna(FaunaRole.BEAST).talent(FaunaTalent.TERRITORY)
                .phase(1.0F, "", a(CHARGE, "Coil Strike").power(1.5F).heavy().cd(110), a(SLAM, "Mandible Snap").radius(2.8F).power(1.3F).cd(90))
                .phase(0.6F, "The Thousandfoot sinks into the rock.", a(BLINK, "Burrow").power(1.2F).cd(130), a(CONE, "Venom Spit").fx(MobEffects.POISON, 60, 0), a(CHARGE, "Coil Strike").power(1.5F).heavy().cd(100))
                .phase(0.3F, "Its crystals crack and blaze.", a(NOVA, "Crystal Shed").n(12).bolt(BoltKind.GLASS), a(RAIN, "Dripfall").n(4).bolt(BoltKind.PLAIN), a(FRENZY, "Thousand Legs"))
                .parts(P("thousandfoot_mandible", "Thousandfoot Mandible", "glass")).build());
    }
}
