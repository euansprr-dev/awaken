// AWAKEN Lens: far terrain drawn by a companion with its own depth (compat.client.FarTerrain), shared by the Lens passes.
// Params texel 0: b = far terrain drawn in the last 0.2 s; a = its depth (bit 0 reverse-Z, bit 1 zero-to-one, bit 2 a
// depth Lens may read is shared, bit 3 it hazes its own distance). The trust texel (pass lens_far_probe) says whether that depth has proven itself:
// r 1 once it has, g easing from 0 to 1 after that.

int farFlags(vec4 p0) { return int(p0.a * 255.0 + 0.5); }

// the far renderer drew here: its depth isn't the cleared value (0 with reverse-Z, 1 without)
bool farDrew(float depth, int flags) {
    return (flags & 1) != 0 ? depth > 1.0e-6 : depth < 0.999999;
}

// far terrain covers this pixel, by a depth Lens trusts
bool farHit(float depth, int flags, vec4 trust) {
    return trust.r > 0.5 && farDrew(depth, flags);
}
