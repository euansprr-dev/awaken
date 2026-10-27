package dev.awaken.powers.client.fx;

import dev.awaken.powers.fx.CastProfiles;

/** The profile ids the client's special cases name (looked up once; the table is built in a fixed order). */
final class CastProfilesIds {
    private CastProfilesIds() {}

    static final int SHOCKWAVE = CastProfiles.byPower("shockwave").id();
    static final int BEAM = CastProfiles.byPower("beam").id();
}
