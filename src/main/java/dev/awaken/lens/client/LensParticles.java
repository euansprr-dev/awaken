package dev.awaken.lens.client;

import dev.awaken.lens.LensModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3fc;

/**
 * Lens 2's ambient particles (SPEC §22), AWAKEN's own (sprites from tools/art/specs/lens_fx.py, never vanilla's): dust
 * that only shows in sunlit air and glints when it's between you and the sun, embers, frost, spores, wisps and your
 * breath in the cold. Spawned on the client around the camera on Cinematic (half as many) and Ultra, by place: each gate
 * theme has its own mix, the overworld has sunlit dust by day and breath in the snow, the Nether embers. The video
 * setting "Particles" scales them (Minimal: none). Nothing travels over the network.
 */
public final class LensParticles {
    private LensParticles() {}

    static final int MOTE = 0, EMBER = 1, FROST = 2, SPORE = 3, WISP = 4, BREATH = 5;

    /** One emitter of a place: what, its colour, how many a tick at Ultra, and how it moves. */
    private record Emitter(int kind, int tint, float rate, float rise) {}

    private static final Emitter[] NONE = {};
    private static final java.util.Map<String, Emitter[]> GATES = java.util.Map.ofEntries(
            gate("bell_crypt", new Emitter(WISP, 0xA8E8FF, 0.25f, 0.002f), new Emitter(MOTE, 0xB8C4D8, 1.0f, 0f)),
            gate("fungal_hollow", new Emitter(SPORE, 0x7CFFC8, 1.5f, 0.004f), new Emitter(SPORE, 0xC890FF, 0.5f, 0.003f)),
            gate("ember_mines", new Emitter(EMBER, 0xFFB060, 2.0f, 0.03f), new Emitter(MOTE, 0x8A7A70, 1.0f, -0.003f)),
            gate("drowned_archive", new Emitter(MOTE, 0x8CE0E0, 1.2f, 0.012f), new Emitter(WISP, 0x60C0FF, 0.15f, 0.003f)),
            gate("clockwork_foundry", new Emitter(EMBER, 0xFFD27A, 0.8f, 0.02f), new Emitter(MOTE, 0xC8A060, 1.0f, 0f)),
            gate("glass_desert", new Emitter(MOTE, 0xFFF6E0, 1.5f, 0.001f)),
            gate("hive", new Emitter(SPORE, 0xFFC040, 1.5f, 0.002f), new Emitter(MOTE, 0xE8B060, 0.6f, 0f)),
            gate("overgrown_temple", new Emitter(SPORE, 0xD8F080, 1.0f, 0.001f), new Emitter(WISP, 0xE0FFB0, 0.15f, 0.002f)),
            gate("lantern_night_market", new Emitter(EMBER, 0xFFC870, 0.6f, 0.012f), new Emitter(WISP, 0xFFB060, 0.2f, 0.004f)),
            gate("frost_monastery", new Emitter(FROST, 0xE0F0FF, 2.0f, -0.012f)),
            gate("mirror_palace", new Emitter(MOTE, 0xE8E0FF, 1.2f, 0f), new Emitter(WISP, 0xD0C8FF, 0.1f, 0f)),
            gate("precursor_lab", new Emitter(MOTE, 0x70E8FF, 1.0f, 0.002f)),
            gate("nightmare", new Emitter(WISP, 0xC060A0, 0.3f, 0.002f), new Emitter(MOTE, 0x806070, 0.8f, 0.006f)),
            gate("sky_archipelago", new Emitter(MOTE, 0xF0F8FF, 1.0f, 0.004f), new Emitter(WISP, 0xFFFFFF, 0.1f, 0.003f)),
            gate("abyssal_shrine", new Emitter(WISP, 0x8060FF, 0.15f, 0.001f), new Emitter(MOTE, 0x7060A0, 0.4f, 0.002f)));
    private static final Emitter[] NETHER = {new Emitter(EMBER, 0xFF8040, 1.0f, 0.03f)};

    private static java.util.Map.Entry<String, Emitter[]> gate(String theme, Emitter... e) {
        return java.util.Map.entry("awaken:gate_" + theme, e);
    }

    private static float carry;
    private static int breathIn = 40;

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(LensModule.MOTE.get(), s -> new Provider(s, MOTE));
        e.registerSpriteSet(LensModule.EMBER.get(), s -> new Provider(s, EMBER));
        e.registerSpriteSet(LensModule.FROST.get(), s -> new Provider(s, FROST));
        e.registerSpriteSet(LensModule.SPORE.get(), s -> new Provider(s, SPORE));
        e.registerSpriteSet(LensModule.WISP.get(), s -> new Provider(s, WISP));
        e.registerSpriteSet(LensModule.BREATH.get(), s -> new Provider(s, BREATH));
    }

    /**
     * Once a client tick while Lens 2 draws. {@code biome} the camera's biome id when a look is keyed on it (gates have
     * one per theme), {@code light}/{@code lightDir} the sun's strength and direction (0 without a sun), {@code warm}
     * the sunrise colour as 0xRRGGBB and its strength.
     */
    static void tick(Minecraft mc, boolean ultra, String biome, float light, Vector3fc lightDir, int warm, float warmth,
            LensHeightfield height) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) return;
        float scale = (ultra ? 1f : 0.5f) * (status == ParticleStatus.DECREASED ? 0.5f : 1f);
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        RandomSource r = level.getRandom();
        Emitter[] place = biome != null ? GATES.getOrDefault(biome, NONE) : NONE;
        if (place == NONE && level.dimension() == net.minecraft.world.level.Level.NETHER) place = NETHER;
        for (Emitter e : place) emit(level, r, eye, camera.forwardVector(), e.kind, e.tint, e.rate * scale, e.rise, 1f);
        // the overworld by day: dust, only where the sun reaches, glinting when it's between you and the sun
        if (place == NONE && light > 0.2f && height != null) {
            int n = count(r, 2.5f * scale);
            for (int i = 0; i < n; i++) {
                double x = eye.x + (r.nextFloat() - 0.5f) * 18, y = eye.y + (r.nextFloat() - 0.4f) * 8, z = eye.z + (r.nextFloat() - 0.5f) * 18;
                if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir() || !height.sunlit(x, y, z, lightDir)) continue;
                float dx = (float) (x - eye.x), dy = (float) (y - eye.y), dz = (float) (z - eye.z), len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                float toward = (dx * lightDir.x() + dy * lightDir.y() + dz * lightDir.z()) / Math.max(len, 0.01f);
                float glint = 0.2f + 0.8f * smooth((toward - 0.2f) / 0.75f);
                int tint = lerpColour(0xFFF4E0, warm, warmth * 0.7f);
                spawn(level, r, MOTE, tint, x, y, z, 0, 0.002, 0, glint * light);
            }
        }
        // breath in the cold: a puff every few seconds from just in front of the face (first person only)
        boolean cold = "awaken:gate_frost_monastery".equals(biome) || coldAt(level, camera.blockPosition());
        if (cold && !camera.isDetached() && --breathIn <= 0) {
            breathIn = 55 + r.nextInt(30);
            Vector3fc f = camera.forwardVector();
            for (int i = 0; i < 4; i++) {
                double d = 0.32 + i * 0.05;
                spawn(level, r, BREATH, 0xF4F8FF, eye.x + f.x() * d, eye.y - 0.14 + f.y() * d, eye.z + f.z() * d,
                        f.x() * 0.03 + (r.nextFloat() - 0.5) * 0.01, 0.006, f.z() * 0.03 + (r.nextFloat() - 0.5) * 0.01, 1f);
            }
        }
    }

    private static void emit(ClientLevel level, RandomSource r, Vec3 eye, Vector3fc forward, int kind, int tint, float rate, float rise, float alpha) {
        int n = count(r, rate);
        for (int i = 0; i < n; i++) {
            // around the camera, more of them ahead where they're seen
            double x = eye.x + (r.nextFloat() - 0.5f) * 24 + forward.x() * 6, y = eye.y + (r.nextFloat() - 0.45f) * 10,
                    z = eye.z + (r.nextFloat() - 0.5f) * 24 + forward.z() * 6;
            if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;
            spawn(level, r, kind, tint, x, y, z, (r.nextFloat() - 0.5) * 0.01, rise, (r.nextFloat() - 0.5) * 0.01, alpha);
        }
    }

    private static int count(RandomSource r, float rate) {
        carry += rate;
        int n = (int) carry;
        carry -= n;
        return n;
    }

    private static boolean coldAt(ClientLevel level, BlockPos pos) {
        return level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.SNOW;
    }

    private static void spawn(ClientLevel level, RandomSource r, int kind, int tint, double x, double y, double z, double xa, double ya, double za, float alpha) {
        LensParticle.nextTint = tint;
        LensParticle.nextAlpha = alpha;
        var type = switch (kind) {
            case EMBER -> LensModule.EMBER.get();
            case FROST -> LensModule.FROST.get();
            case SPORE -> LensModule.SPORE.get();
            case WISP -> LensModule.WISP.get();
            case BREATH -> LensModule.BREATH.get();
            default -> LensModule.MOTE.get();
        };
        level.addParticle(type, x, y, z, xa, ya, za);
    }

    private static int lerpColour(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255),
                bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    private static float smooth(float x) {
        float t = Math.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private record Provider(SpriteSet sprites, int kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z, double xa, double ya,
                double za, RandomSource random) {
            return new LensParticle(level, x, y, z, xa, ya, za, sprites, kind);
        }
    }

    /**
     * One particle, its kind's look and motion: dust drifting and twinkling; embers rising and cooling from yellow to
     * red; frost crystals tumbling down; spores bobbing with a slow pulse; wisps wandering and breathing in and out; a
     * breath puff swelling and thinning. Glowing kinds are full bright (Lens's bloom picks them up); frost and breath
     * take the world's light.
     */
    static final class LensParticle extends SingleQuadParticle {
        static int nextTint = 0xFFFFFF;
        static float nextAlpha = 1f;
        private final int kind;
        private final float size0, phase, spin;
        private float base;
        private final float r0, g0, b0;

        LensParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites, int kind) {
            super(level, x, y, z, xa, ya, za, sprites.get(level.getRandom()));
            this.kind = kind;
            this.xd = xa;
            this.yd = ya;
            this.zd = za;
            this.hasPhysics = false;
            this.gravity = 0f;
            RandomSource r = level.getRandom();
            int c = nextTint;
            r0 = ((c >> 16) & 255) / 255f;
            g0 = ((c >> 8) & 255) / 255f;
            b0 = (c & 255) / 255f;
            setColor(r0, g0, b0);
            phase = r.nextFloat() * 6.283f;
            spin = (r.nextFloat() - 0.5f) * 0.08f;
            switch (kind) {
                case EMBER -> {
                    lifetime = 40 + r.nextInt(50);
                    size0 = 0.03f + r.nextFloat() * 0.04f;
                    friction = 0.97f;
                    base = 0.95f;
                }
                case FROST -> {
                    lifetime = 90 + r.nextInt(80);
                    size0 = 0.04f + r.nextFloat() * 0.05f;
                    friction = 0.98f;
                    base = 0.85f;
                }
                case SPORE -> {
                    lifetime = 100 + r.nextInt(100);
                    size0 = 0.03f + r.nextFloat() * 0.035f;
                    friction = 0.96f;
                    base = 0.8f;
                }
                case WISP -> {
                    lifetime = 120 + r.nextInt(100);
                    size0 = 0.11f + r.nextFloat() * 0.09f;
                    friction = 0.97f;
                    base = 0.5f;
                }
                case BREATH -> {
                    lifetime = 24 + r.nextInt(10);
                    size0 = 0.07f + r.nextFloat() * 0.03f;
                    friction = 0.9f;
                    base = 0.3f;
                }
                default -> {
                    lifetime = 70 + r.nextInt(80);
                    size0 = 0.018f + r.nextFloat() * 0.025f;
                    friction = 0.96f;
                    base = 0.85f;
                }
            }
            base *= Math.clamp(nextAlpha, 0f, 1f);
            quadSize = size0;
            alpha = 0f;
        }

        @Override
        protected SingleQuadParticle.Layer getLayer() {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }

        @Override
        protected int getLightCoords(float a) {
            return kind == FROST || kind == BREATH ? super.getLightCoords(a) : 0xF000F0;
        }

        @Override
        public void tick() {
            super.tick();
            float life = (float) age / lifetime, t = age + phase * 10f;
            // in over the first fifth, out over the last third
            float fade = Math.min(1f, life * 5f) * Math.min(1f, (1f - life) * 3f);
            RandomSource r = level.getRandom();
            switch (kind) {
                case EMBER -> {
                    xd += Mth.sin(t * 0.21f) * 0.0015;
                    zd += Mth.cos(t * 0.17f) * 0.0015;
                    // cools as it rises: white-gold to orange to a dull red
                    float cool = life;
                    setColor(Math.min(1f, r0 + 0.2f * (1f - cool)), g0 * (1f - 0.65f * cool), b0 * (1f - 0.85f * cool));
                    alpha = base * fade * (0.75f + 0.25f * Mth.sin(t * 0.9f));
                }
                case FROST -> {
                    xd += Mth.sin(t * 0.07f) * 0.0008;
                    zd += Mth.cos(t * 0.05f) * 0.0008;
                    oRoll = roll;
                    roll += spin;
                    alpha = base * fade;
                }
                case SPORE -> {
                    yd += Mth.sin(t * 0.06f) * 0.0006;
                    xd += (r.nextFloat() - 0.5f) * 0.0008;
                    zd += (r.nextFloat() - 0.5f) * 0.0008;
                    alpha = base * fade * (0.65f + 0.35f * Mth.sin(t * 0.12f));
                }
                case WISP -> {
                    xd += Mth.sin(t * 0.031f) * 0.0012;
                    zd += Mth.cos(t * 0.027f) * 0.0012;
                    yd += Mth.sin(t * 0.045f) * 0.0006;
                    alpha = base * fade * (0.6f + 0.4f * Mth.sin(t * 0.08f));
                    quadSize = size0 * (0.9f + 0.15f * Mth.sin(t * 0.08f));
                }
                case BREATH -> {
                    quadSize = size0 * (1f + 3.2f * life);
                    alpha = base * (1f - life) * Math.min(1f, life * 8f);
                }
                default -> {
                    xd += (r.nextFloat() - 0.5f) * 0.0006;
                    yd += (r.nextFloat() - 0.5f) * 0.0004;
                    zd += (r.nextFloat() - 0.5f) * 0.0006;
                    alpha = base * fade * (0.55f + 0.45f * Mth.sin(t * 0.35f));
                }
            }
        }
    }
}
