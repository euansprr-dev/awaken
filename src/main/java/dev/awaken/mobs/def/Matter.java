package dev.awaken.mobs.def;

/**
 * What a gate monster's blows throw off (SPEC §60.8): the shape of the bits, in the colour its def gives. The shapes
 * are the shots' own ({@code mobs.MobParticles.Shot}, same names and order), so a body made of bone sheds dust, one of
 * glass slivers, one of coals embers.
 */
public enum Matter {
    SPARK, EMBER, FLAKE, WISP, PUFF, DROP, SLIVER
}
