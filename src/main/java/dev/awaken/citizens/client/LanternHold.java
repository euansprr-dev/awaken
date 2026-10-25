package dev.awaken.citizens.client;

/**
 * A lantern-bearer's lantern on the client (held on the body, reused every frame): its model's render state, and where
 * its flame was this frame, camera-relative, as CitizenRenderer's lantern layer drew it (CityGlow throws the light from
 * exactly there in the same frame), with the flame's flicker so the glass and the light breathe together.
 */
final class LanternHold {
    final CritterRenderState state = new CritterRenderState();
    float rx, ry, rz, flicker = 1F;
    int frame = -1;
}
