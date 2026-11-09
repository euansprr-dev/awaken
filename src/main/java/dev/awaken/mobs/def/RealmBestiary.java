package dev.awaken.mobs.def;

import java.util.List;

import static dev.awaken.mobs.def.MobDef.m;

/** The peaceful life of a gate world (SPEC §50.8): spawned only by gates.realms.RealmLife, never by a biome or a list. */
final class RealmBestiary {
    private RealmBestiary() {}

    static void define(List<MobDef> out) {
        out.add(m("pollen_grazer", "Pollen Grazer", Theme.HIVE, Tier.NORMAL, Model.POLLEN_GRAZER, "A shaggy six-legged herd beast that wears the valley's pollen on its shoulders").hp(26).dmg(2).spd(0.16).kb(0.5).xp(3).tint(0xFFFFFF).glow(0xFFE08A).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.GRAZE).placed().build());
        out.add(m("wax_folk", "Wax-folk", Theme.HIVE, Tier.NORMAL, Model.WAX_FOLK, "One of the Reach's people, a squat candle who has been burning for longer than anyone remembers").hp(10).dmg(1).spd(0.13).xp(1).tint(0xFFFFFF).glow(0xFFD28A).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.NONE).placed().build());
        out.add(m("amberfly", "Amberfly", Theme.HIVE, Tier.NORMAL, Model.AMBERFLY, "A fat little fly whose back end is a lit bead; they hang in the air above the honey").hp(2).dmg(1).spd(0.28).xp(1).tint(0xFFFFFF).glow(0xFFC45A).fauna(FaunaRole.AMBIENT).talent(FaunaTalent.NONE).placed().build());
    }
}
