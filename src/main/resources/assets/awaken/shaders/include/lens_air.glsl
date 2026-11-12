// AWAKEN Lens, the Air (SPEC §22 "Steady Light"): Lens's own sky, and the colour its far fog and its clouds take from
// it. One model for all three, so the land fades into the very colour the sky has behind it and a cloud's shade is the
// sky's light. Needs lens_frame.glsl (F) before it.
// Frame texel 22: xyz toward the sun (under the horizon by night), w the air's strength (0: off, the look without it).
// Frame texel 23: rgb the game's own sky colour this frame (what it drew behind the sun, moon and stars), a its stars'
// brightness.

float airOn() { return F(22).w; }

// A land with a sky of its own (SPEC §22 "The Air in other worlds"): every world but the overworld says its sky's and
// its fog's colours in its biomes, and there the Air draws those and not its own model. Frame texels 24, 25, 26 w
// (the spare of the Holo plates' texels): the land's horizon (its fog colour as the game has it now, the look's haze
// tint over it); 27 w: how far the sky is the land's (0 the Air's own, 1 the land's). Its zenith is texel 23's sky
// colour, the game's.
vec4 airLand() { return vec4(F(24).w, F(25).w, F(26).w, F(27).w); }

// the hour by the sun's height: x day, y dusk or dawn (it peaks as the sun crosses the horizon), z night
vec3 airHour(float sy) {
    float day = smoothstep(-0.06, 0.30, sy);
    float dusk = smoothstep(-0.32, -0.06, sy) * (1.0 - smoothstep(0.10, 0.50, sy));
    float night = 1.0 - smoothstep(-0.26, -0.02, sy);
    return vec3(day, dusk, night);
}

// the sky in a direction, without the sun's disc, the stars or the clouds: a deep violet-blue overhead paling to the
// horizon by day; rose and gold toward the sun and violet away from it at dusk and dawn; near-black blue by night
vec3 airSky(vec3 dir, vec3 sun, float rain) {
    vec3 hr = airHour(sun.y);
    float e = clamp(dir.y, 0.0, 1.0);
    // toward the sun round the horizon (by compass only), 0 away from it to 1 at it
    float side = 0.5 + 0.5 * dot(normalize(dir.xz + vec2(1.0e-5)), normalize(sun.xz + vec2(1.0e-5)));
    vec3 dayZ = vec3(0.33, 0.41, 0.86), dayM = vec3(0.53, 0.61, 0.95), dayH = vec3(0.83, 0.86, 0.98);
    vec3 duskZ = vec3(0.17, 0.16, 0.44), duskM = vec3(0.47, 0.34, 0.66);
    vec3 duskH = mix(vec3(0.60, 0.50, 0.80), vec3(1.0, 0.64, 0.46), side * side);
    vec3 nightZ = vec3(0.010, 0.016, 0.046), nightM = vec3(0.022, 0.036, 0.092), nightH = vec3(0.050, 0.076, 0.155);
    vec3 z = mix(nightZ, dayZ, hr.x), m = mix(nightM, dayM, hr.x), h = mix(nightH, dayH, hr.x);
    z = mix(z, duskZ, hr.y * 0.85);
    m = mix(m, mix(duskM, duskH, 0.35 * side), hr.y * 0.85);
    h = mix(h, duskH, hr.y * 0.9);
    float up = 1.0 - e;
    vec3 c = mix(z, m, smoothstep(0.0, 1.0, pow(up, 1.5)));
    c = mix(c, h, pow(up, 4.0));
    // the sun's glow in the air: wide and faint by day, low, wide and warm at dusk
    float mu = max(dot(dir, sun), 0.0);
    vec3 glowC = mix(vec3(1.0, 0.93, 0.80), vec3(1.0, 0.56, 0.32), hr.y);
    float glow = (pow(mu, 5.0) * 0.10 + pow(mu, 36.0) * 0.20) * max(hr.x, hr.y) * (1.0 - hr.z);
    c = c + glowC * glow * (1.0 - c);
    // the moon's side of the night sky is a little lighter
    c += vec3(0.020, 0.030, 0.055) * pow(max(dot(dir, -sun), 0.0), 6.0) * hr.z;
    // rain and storm: toward a cool grey as bright as the sky was
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, vec3(l) * vec3(0.90, 0.95, 1.02) * 0.82, rain * 0.85);
    // a land's own sky: its sky colour overhead (a little deeper at the top), its horizon below, in the model's own
    // shape, with the sun's glow in its air. The game's colours already carry the land's hour and weather
    vec4 land = airLand();
    if (land.a > 0.0) {
        vec3 lz = F(23).rgb;
        vec3 cl = mix(lz * 0.80, lz, smoothstep(0.0, 1.0, pow(up, 1.5)));
        cl = mix(cl, land.rgb, pow(up, 4.0));
        cl += glowC * glow * 0.6 * (1.0 - cl);
        c = mix(c, cl, land.a);
    }
    return c;
}

// the fog's colour for land seen in a direction: the sky that stands behind it there (the horizon's, for land seen
// from above), so far land fades into exactly what is drawn behind it
vec3 airFog(vec3 dir, vec3 sun, float rain) {
    return airSky(normalize(vec3(dir.x, max(dir.y, 0.0), dir.z) + vec3(1.0e-5, 0.0, 0.0)), sun, rain);
}

float airHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float airNoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(airHash(i), airHash(i + vec3(1, 0, 0)), f.x), mix(airHash(i + vec3(0, 1, 0)), airHash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(airHash(i + vec3(0, 0, 1)), airHash(i + vec3(1, 0, 1)), f.x), mix(airHash(i + vec3(0, 1, 1)), airHash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float airFbm(vec3 p) {
    return airNoise(p) * 0.5 + airNoise(p * 2.03 + 7.1) * 0.3 + airNoise(p * 4.1 - 3.7) * 0.2;
}

// The night sky (SPEC §22 "Steady Light"). It turns with the sun about the world's z, as the game's stars do.
vec3 airStarDir(vec3 dir, vec3 sun) {
    vec2 s = normalize(sun.xy + vec2(1.0e-5));
    return vec3(dir.x * s.y - dir.y * s.x, dir.x * s.x + dir.y * s.y, dir.z);
}

// one layer of stars: a grid `scale` cells across the sky, `keep` the share of cells that hold a star, each a square
// of the game's own kind `size` of a cell across, with its own brightness, tint and slow twinkle; `glint` gives the
// bright ones four thin points
vec3 airStarLayer(vec3 rd, float scale, float keep, float size, float seconds, float glint) {
    vec3 p = rd * scale, cell = floor(p);
    float h = airHash(cell);
    if (h > keep) return vec3(0.0);
    vec3 d = p - (cell + 0.25 + 0.5 * vec3(airHash(cell + 7.1), airHash(cell + 13.7), airHash(cell + 29.3)));
    float bright = 0.3 + 0.7 * airHash(cell + 3.3);
    float tw = 0.72 + 0.28 * sin(seconds * (0.5 + 2.5 * airHash(cell + 5.9)) + 6.2832 * h / keep);
    vec3 tint = mix(vec3(0.70, 0.82, 1.0), vec3(1.0, 0.84, 0.66), smoothstep(0.5, 1.0, airHash(cell + 17.0)));
    tint = mix(tint, vec3(1.0), 0.4);
    float core = step(max(abs(d.x), max(abs(d.y), abs(d.z))), size * (0.55 + 0.6 * bright));
    float shine = core;
    if (glint > 0.0 && bright > 0.6) {
        vec3 t1 = normalize(cross(rd, vec3(0.0, 0.0, 1.0)) + vec3(1.0e-4, 0.0, 0.0)), t2 = cross(rd, t1);
        float a = abs(dot(d, t1)), b = abs(dot(d, t2));
        shine += (exp(-b * 46.0 - a * 7.0) + exp(-a * 46.0 - b * 7.0)) * glint * (bright - 0.6) * 2.5;
    }
    return tint * shine * bright * tw;
}

// the galaxy: a soft band across the sky with dark lanes through it and a warmer, brighter heart
const vec3 AIR_GALAXY_POLE = vec3(0.3162, 0.4743, 0.8222);

// how far into the band a direction of the turning sky is (0..1): cheap, for the stars that crowd along it
float airGalaxyBand(vec3 rd) {
    float b = dot(rd, AIR_GALAXY_POLE);
    return exp(-b * b * 13.0);
}

// its light: noise, so it is drawn once a frame into the small sky buffer (lens_airsky.fsh) and read from there
vec3 airGalaxy(vec3 rd) {
    float band = airGalaxyBand(rd);
    if (band < 0.01) return vec3(0.0);
    float n = airFbm(rd * 3.4 + 1.7), lanes = airFbm(rd * 7.5 - 4.1);
    float heart = pow(max(dot(rd, normalize(cross(AIR_GALAXY_POLE, vec3(0.0, 1.0, 0.0)))), 0.0), 3.0);
    float glow = band * (0.30 + 0.95 * n) * (1.0 - 0.6 * smoothstep(0.46, 0.66, lanes) * band);
    vec3 col = mix(vec3(0.16, 0.24, 0.56), vec3(0.46, 0.34, 0.66), n) + vec3(0.60, 0.38, 0.22) * heart * 0.7;
    return col * glow * (0.20 + 0.30 * heart);
}

// a shooting star: one chance every nine seconds, a short bright streak that falls and fades
vec3 airMeteor(vec3 dir, float seconds) {
    float slot = floor(seconds / 9.0), tt = seconds - slot * 9.0;
    float h = airHash(vec3(slot, 3.1, 7.7));
    if (h > 0.5 || tt > 0.9) return vec3(0.0);
    float az = 6.2832 * airHash(vec3(slot, 1.3, 2.9)), el = 0.5 + 0.8 * airHash(vec3(slot, 5.5, 0.4));
    vec3 a = vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el));
    vec3 t = normalize(cross(a, normalize(vec3(sin(h * 40.0), 0.4, cos(h * 40.0)))));
    if (t.y > 0.0) t = -t;
    float prog = tt / 0.9;
    vec3 dd = dir - normalize(a + t * 0.34 * prog);
    float along = dot(dd, -t), across = length(dd + t * along);
    float tail = 0.12 * sin(3.1416 * prog) + 1.0e-4;
    return vec3(0.88, 0.94, 1.0) * (smoothstep(0.0024, 0.0006, across) * step(0.0, along) * smoothstep(tail, 0.0, along) * 1.7);
}

// how the night's light fades toward the horizon
float airNightRise(vec3 dir) { return dir.y < -0.02 ? 0.0 : smoothstep(-0.02, 0.10, dir.y); }

// what the night adds to the sky in a direction that must be sharp (before the clouds): stars in four sizes, more of
// the small ones along the galaxy, a shooting star now and then, the moon's halo. The galaxy's own light is the sky
// buffer's (airSlow)
vec3 airNight(vec3 dir, vec3 sun, float seconds) {
    if (dir.y < -0.02) return vec3(0.0);
    vec3 rd = airStarDir(dir, sun);
    float band = airGalaxyBand(rd);
    // (a sky full of them: a dust of faint ones, thick along the galaxy; the small; the middling; and the few bright
    // ones with their four points)
    vec3 c = airStarLayer(rd, 230.0, 0.075 + 0.20 * band, 0.16, seconds, 0.0) * 0.50;
    c += airStarLayer(rd, 140.0, 0.085 + 0.10 * band, 0.11, seconds, 0.0) * 0.85;
    c += airStarLayer(rd, 70.0, 0.085, 0.075, seconds, 0.0) * 1.25;
    c += airStarLayer(rd, 27.0, 0.085, 0.036, seconds, 1.0) * 1.8;
    c += airMeteor(dir, seconds);
    // a wide soft halo round the moon
    float mu = max(dot(dir, -sun), 0.0);
    c += vec3(0.10, 0.13, 0.20) * (pow(mu, 90.0) * 0.9 + pow(mu, 14.0) * 0.18);
    return c * airNightRise(dir);
}

// The high layer: thin wisps far above the block clouds, drawn out along the wind in chunky pixels of their own. They
// give the day sky a second depth, take the sunset from below before anything else does, and drift at their own pace.
// airWispAlpha is how much of the sky they cover in a direction (noise: the sky buffer's), airWispColour their colour.
float airWispAlpha(vec3 dir, vec2 at, float camY, vec3 sun, float rain, float seconds) {
    if (dir.y < 0.05) return 0.0;
    vec3 hr = airHour(sun.y);
    float much = (0.34 * hr.x + 0.50 * hr.y) * (1.0 - 0.5 * rain);
    if (much <= 0.0) return 0.0;
    float t = (680.0 - camY) / dir.y;
    vec2 q = (at + dir.xz * t + vec2(seconds * 2.2, 0.0)) / 26.0;
    // (bent by a slow drift, so no streak is a ruled line)
    q += vec2(26.0, 46.0) * (vec2(airNoise(vec3(q * 0.010, 1.7)), airNoise(vec3(q * 0.013, 4.2))) - 0.5)
            + vec2(6.0, 12.0) * (vec2(airNoise(vec3(q * 0.045, 5.3)), airNoise(vec3(q * 0.05, 8.8))) - 0.5);
    // feathered streaks, longer along the sun's own east-west line (they gather toward it at dawn and dusk), in patches
    // with open sky between
    float n = airFbm(vec3(q * vec2(0.028, 0.066), 3.3)) * (0.70 + 0.6 * airNoise(vec3(q * vec2(0.008, 0.011), 9.1)));
    return smoothstep(0.44, 0.72, n) * much * smoothstep(0.05, 0.22, dir.y) * (1.0 - smoothstep(2600.0, 7000.0, t));
}

vec3 airWispColour(vec3 dir, vec3 sun, float rain) {
    vec3 hr = airHour(sun.y);
    float side = 0.5 + 0.5 * dot(normalize(dir.xz + vec2(1.0e-5)), normalize(sun.xz + vec2(1.0e-5)));
    vec3 day = vec3(1.0, 0.99, 0.97), dusk = mix(vec3(0.74, 0.58, 0.86), vec3(1.0, 0.60, 0.44), side), night = vec3(0.07, 0.09, 0.16);
    vec3 c = mix(mix(night, day, hr.x), dusk, hr.y * 0.9);
    return mix(c, vec3(dot(c, vec3(0.3, 0.59, 0.11))) * 0.8, rain * 0.8);
}

// The sky's slow parts for a direction, as the sky buffer holds them (lens_airsky.fsh writes this, the composite reads
// it back with airSlowGalaxy and airSlowWisp): rgb the galaxy's light with the night's share and its rise from the
// horizon, as a square root (the dark end keeps its steps in 8 bits); a the wisps' cover, doubled.
const float AIR_SLOW_GALAXY = 1.6;

vec4 airSlow(vec3 dir, vec2 at, float camY, vec3 sun, float rain, float seconds) {
    float dark = smoothstep(0.06, -0.20, sun.y) * (1.0 - rain) * airNightRise(dir);
    vec3 gal = dark > 0.0 ? airGalaxy(airStarDir(dir, sun)) * dark : vec3(0.0);
    return vec4(sqrt(gal * AIR_SLOW_GALAXY), airWispAlpha(dir, at, camY, sun, rain, seconds) * 2.0);
}

vec3 airSlowGalaxy(vec4 slow) { return slow.rgb * slow.rgb * (1.0 / AIR_SLOW_GALAXY); }
float airSlowWisp(vec4 slow) { return slow.a * 0.5; }
