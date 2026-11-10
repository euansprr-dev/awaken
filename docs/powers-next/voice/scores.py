"""Sound scores for the taster (docs/powers-next/PLAN.md, "Sound"). Names and numbers only: no audio lives in the repo.

A layer is (name, tick, volume, pitch[, jitter[, cut seconds]]). `minecraft:` names are the game's own recordings as sounds.json
names them; `awaken:` names are the mod's files in build today. A name ending in # is every numbered take, one picked per cast.
TODAY is what build/awaken-v1 plays now, on the ticks its code plays them. A and B are two proposals: A my best guess, B a
different idea, so Euan can say which way to go. `made` says in plain words what each layer is, for the page.
"""

HAY, WELL, PALM, NOVA, FROST, STEP = "haymaker", "gravity_well", "spirit_palm", "nova", "frost_nova", "shadow_step"
ORDER = [HAY, WELL, PALM, NOVA, FROST, STEP]
NAMES = {HAY: "Haymaker", WELL: "Gravity Well", PALM: "Spirit Palm", NOVA: "Nova", FROST: "Frost Nova", STEP: "Shadow Step"}
WHAT = {
    HAY: "Held one second to full, let go, lands on a creature.",
    WELL: "Opens, pulls for four seconds, closes.",
    PALM: "The slap, then the echo from inside the target a second later.",
    NOVA: "Half a second of swelling, then the burst.",
    FROST: "The hand comes down, the ground is slapped, the frost runs out.",
    STEP: "You vanish and arrive behind the target.",
}
K = "awaken:powers/power_kit_"

TODAY = {
    HAY: [(K + "hay_charge_#", 0, 1.0, 1.0, 0), (K + "hay_peak_#", 20, 1.0, 1.0, 0), (K + "hay_release_#", 24, 1.0, 0.9, 0),
          (K + "hay_impact_#", 25, 1.0, 0.85, 0), (K + "hay_tail_#", 25, 0.9, 1.0, 0)],
    WELL: [(K + "well_open_#", 0, 1.0, 1.0, 0), (K + "well_loop_#", 0, 1.0, 1.0, 0, 4.0), (K + "well_tail_#", 80, 1.0, 1.0, 0)],
    PALM: [(K + "spirit_#", 0, 1.0, 1.0, 0), (K + "spirit_#", 20, 1.0, 0.8, 0)],
    NOVA: [(K + "gravity_#", 0, 1.0, 1.4, 0), (K + "nova_#", 9, 1.0, 1.0, 0)],
    FROST: [(K + "frost_#", 5, 1.0, 1.0, 0)],
    STEP: [(K + "shadow_#", 0, 0.9, 1.0, 0), (K + "shadow_#", 0, 0.9, 1.3, 0)],
}

M = "minecraft:"
A = {
    HAY: dict(title="Leather, a held breath, iron", made=[
        "the charge: a crossbow being drawn, slowed down, and leather tightening three times",
        "under it: the Warden's heartbeat, three beats, each one sooner and higher",
        "at full: the crossbow's lock",
        "the let-go: a sweep of air and one beat of a dragon's wing",
        "the hit: a critical hit, an iron golem being struck, wood splitting, and a mace on the ground under it",
        "after: a far explosion turned right down, and one heavy footfall"],
        score=[(M + "item/crossbow/loading_start", 0, 0.9, 0.6), (M + "item/armor/equip_leather1", 0, 1.0, 0.7), (M + "item/armor/equip_leather#", 7, 0.8, 0.8),
               (M + "item/armor/equip_leather#", 13, 0.9, 0.9), (M + "mob/warden/heartbeat_#", 2, 0.35, 1.0), (M + "mob/warden/heartbeat_#", 10, 0.5, 1.15),
               (M + "mob/warden/heartbeat_#", 16, 0.65, 1.3), (M + "item/crossbow/loading_end", 20, 1.0, 0.75),
               (M + "entity/player/attack/sweep#", 24, 1.0, 0.7), (M + "mob/enderdragon/wings#", 24, 0.6, 1.2),
               (M + "entity/player/attack/crit#", 25, 0.9, 0.7), (M + "mob/irongolem/hit#", 25, 1.0, 0.75), (M + "mob/zombie/wood#", 25, 0.5, 0.6),
               (M + "item/mace/smash_ground2", 25, 0.45, 0.8, 0.02, 1.6), (M + "random/explode1", 26, 0.22, 0.6, 0.02, 2.2), (M + "mob/ravager/step#", 27, 0.4, 0.7)]),
    WELL: dict(title="Going under", made=[
        "it opens: the sound of your head going under water, slowed down, with the Warden drawing breath far below",
        "while it pulls: a whirlpool heard from inside, slowed to half speed, over a conduit's low throb",
        "a heartbeat every second, quiet",
        "it closes: a conduit shutting down, cut short, and one deep impact"],
        score=[(M + "ambient/underwater/enter#", 0, 0.9, 0.6), (M + "mob/warden/sonic_charge3", 0, 0.4, 0.6, 0.02, 2.2),
               (M + "block/bubble_column/whirlpool_inside", 2, 0.8, 0.5, 0.0), (M + "block/bubble_column/whirlpool_inside", 50, 0.8, 0.52, 0.0, 1.9),
               (M + "block/conduit/ambient", 4, 0.9, 0.7, 0.0, 4.2), (M + "mob/warden/heartbeat_#", 20, 0.4, 0.8), (M + "mob/warden/heartbeat_#", 40, 0.45, 0.8),
               (M + "mob/warden/heartbeat_#", 60, 0.5, 0.8), (M + "block/conduit/deactivate", 80, 0.6, 0.8, 0.0, 2.4), (M + "mob/warden/attack_impact_1", 80, 0.6, 0.6)]),
    PALM: dict(title="Skin, then something deep", made=[
        "the slap: a strong hit, a slime's wet slap and leather",
        "the echo: the Warden's heartbeat pitched down, an iron golem struck, and a knockback blow under it"],
        score=[(M + "entity/player/attack/strong#", 0, 1.0, 0.8), (M + "mob/slime/attack2", 0, 0.7, 0.7), (M + "item/armor/equip_leather1", 0, 0.8, 1.0),
               (M + "mob/warden/heartbeat_#", 20, 1.0, 0.7), (M + "mob/irongolem/hit#", 20, 0.7, 0.6), (M + "entity/player/attack/knockback#", 20, 0.5, 0.6)]),
    NOVA: dict(title="A breath in, then the sky", made=[
        "the swell: a Breeze breathing in and a flame catching",
        "the burst: a big firework, an explosion pitched up and thin, and a gust",
        "after: the far-off firework and a little thunder"],
        score=[(M + "mob/breeze/inhale#", 0, 1.0, 0.8), (M + "fire/ignite", 0, 0.5, 0.6), (M + "fireworks/largeblast1", 9, 1.0, 0.7),
               (M + "random/explode#", 9, 0.5, 1.3, 0.04, 1.8), (M + "entity/wind_charge/wind_burst#", 9, 0.8, 0.8),
               (M + "fireworks/largeblast_far1", 13, 0.7, 0.6), (M + "ambient/weather/thunder#", 12, 0.2, 1.2, 0.03, 3.5)]),
    FROST: dict(title="Ice that creaks", made=[
        "the arm: a sweep of air",
        "the slap: a mace on the ground, turned down, and the sound the game makes when you freeze",
        "the frost running out: a Creaking locking still, three cracks of dripstone, one low crack of glass",
        "after: the Creaking coming loose, slowed to half, and a cold gust"],
        score=[(M + "entity/player/attack/sweep#", 1, 0.6, 0.8), (M + "item/mace/smash_ground2", 5, 0.4, 1.2, 0.02, 1.0), (M + "entity/player/hurt/freeze_hurt#", 5, 1.0, 0.7),
               (M + "mob/creaking/creaking_freeze#", 5, 1.0, 0.6), (M + "random/glass3", 6, 0.4, 0.5), (M + "block/dripstone/break#", 7, 0.8, 0.7),
               (M + "block/dripstone/break#", 9, 0.8, 0.8), (M + "block/dripstone/break#", 12, 0.7, 0.9), (M + "mob/creaking/creaking_unfreeze#", 10, 0.8, 0.5),
               (M + "entity/wind_charge/wind_burst2", 6, 0.3, 0.6)]),
    STEP: dict(title="A breath cut off", made=[
        "you go: a breath in, cut off, and an Enderman's short jump pitched low",
        "two ticks of nothing",
        "you arrive: a low sweep and one beat of a wing"],
        score=[(M + "random/breath", 0, 0.9, 1.25, 0.03, 0.35), (M + "mob/endermen/portal2", 0, 0.45, 0.6), (M + "entity/player/attack/sweep#", 3, 0.9, 0.6),
               (M + "mob/enderdragon/wings#", 3, 0.45, 0.7)]),
}
B = {
    HAY: dict(title="The mace", made=[
        "the charge: wood creaking under load, four times, each closer",
        "at full: an iron golem winding up",
        "the let-go: a spear's lunge",
        "the hit: the heaviest mace blow in the game, a knockback hit and the Warden's own strike"],
        score=[(M + "mob/creaking/creaking_sway#", 0, 0.7, 0.6), (M + "mob/creaking/creaking_sway#", 7, 0.8, 0.65), (M + "mob/creaking/creaking_sway#", 12, 0.9, 0.7),
               (M + "mob/creaking/creaking_sway#", 16, 1.0, 0.75), (M + "mob/irongolem/throw", 20, 1.0, 0.8), (M + "item/spear/lunge#", 23, 0.8, 1.3, 0.03, 0.6),
               (M + "item/mace/smash_ground_heavy", 25, 0.8, 0.9, 0.0, 2.6), (M + "entity/player/attack/knockback#", 25, 1.0, 0.8), (M + "mob/warden/attack_impact_2", 25, 0.7, 0.7)]),
    WELL: dict(title="Wind and a portal", made=[
        "it opens: a Breeze breathing in",
        "while it pulls: the elytra's rushing air at half speed and a nether portal's whirr, low and quiet",
        "it closes: a wind charge going off, pitched down"],
        score=[(M + "mob/breeze/inhale#", 0, 1.0, 0.6), (M + "item/elytra/elytra_loop", 2, 1.0, 0.5, 0.0, 4.0), (M + "portal/portal", 2, 0.3, 0.5, 0.0, 4.0),
               (M + "entity/wind_charge/wind_burst1", 80, 0.8, 0.6)]),
    PALM: dict(title="A stunned beast", made=[
        "the slap: a strong hit and a knockback blow",
        "the echo: a Ravager being stunned, cut short, and wood struck low"],
        score=[(M + "entity/player/attack/strong#", 0, 1.0, 0.9), (M + "entity/player/attack/knockback#", 0, 0.6, 1.1),
               (M + "mob/ravager/stun#", 20, 0.8, 0.9, 0.03, 1.2), (M + "mob/zombie/wood#", 20, 0.45, 0.5)]),
    NOVA: dict(title="The Warden's boom", made=[
        "the swell: a Breeze breathing in",
        "the burst: the Warden's sonic boom, a Ghast's fireball slowed down, and a gust"],
        score=[(M + "mob/breeze/inhale#", 0, 1.0, 0.7), (M + "mob/warden/sonic_boom#", 9, 0.7, 1.2, 0.03, 2.2), (M + "mob/ghast/fireball4", 9, 0.8, 0.6),
               (M + "mob/breeze/wind_burst#", 9, 0.7, 0.9)]),
    FROST: dict(title="Under the ice", made=[
        "the slap: the Warden's heartbeat and a splash heard from under water",
        "the frost: the freezing sound twice, and a Creaking locking still"],
        score=[(M + "mob/warden/heartbeat_#", 5, 0.8, 0.9), (M + "ambient/underwater/exit#", 5, 0.5, 1.3, 0.03, 1.0), (M + "entity/player/hurt/freeze_hurt#", 5, 1.0, 0.6),
               (M + "entity/player/hurt/freeze_hurt#", 9, 0.8, 0.8), (M + "mob/creaking/creaking_freeze#", 7, 1.0, 0.5)]),
    STEP: dict(title="The Illusioner's trick", made=[
        "you go: the Illusioner's mirror trick, sped up and cut short",
        "you arrive: a low sweep"],
        score=[(M + "mob/illusion_illager/mirror_move1", 0, 0.5, 1.3, 0.03, 0.7), (M + "entity/player/attack/sweep#", 3, 0.9, 0.6)]),
}
