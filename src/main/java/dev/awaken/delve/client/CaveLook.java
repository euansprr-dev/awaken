package dev.awaken.delve.client;

import dev.awaken.lens.client.LensClient;

/**
 * The Lens below (SPEC §28.5; PLAN 8.4): the cave biomes' own looks are biome looks ({@code looks_delve.json}); two
 * place looks blend over them, set every tick: a plain cave's own (layer 0, {@code awaken:underground}, by how far
 * under the rock you are, only where the camera's biome isn't a cave biome: a stone cave under Elderwood stops wearing
 * Elderwood's gold haze) and a seep's violet (layer 1, {@code awaken:seep}, by {@link CaveState#seep()}). Lens plays
 * neither while it is off, and a story moment's look wins over both.
 */
final class CaveLook {
    private CaveLook() {}

    static final String UNDERGROUND = "awaken:underground", SEEP = "awaken:seep";

    static void tick() {
        boolean on = SenseState.active;
        LensClient.placeLook(0, UNDERGROUND, on && SenseState.biome == SenseState.PLAIN ? SenseState.under : 0F);
        LensClient.placeLook(1, SEEP, on ? CaveState.seep() : 0F);
    }
}
