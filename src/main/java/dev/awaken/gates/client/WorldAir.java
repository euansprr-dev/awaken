package dev.awaken.gates.client;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.gates.world.FloorAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A world floor's air (SPEC §38.9 "Air"), all on the client and never sent: each kind in the arrival's {@code life} list
 * (the world's, with its mood's added) makes its own sprites near you, tinted for the world. Dust in the light shafts,
 * mist on still water, smoke off candles, rain through broken windows and lightning in them, a procession of ghost
 * lights, falling ash, snow, spores, petals, paper and sand, embers rising, fireflies, steam, and a far bell tolling a
 * little louder each time. At most {@value #MOST} air sprites near you.
 */
final class WorldAir {
    private WorldAir() {}

    static final int MOST = 260;
    static final int WISP = 0, STREAK = 1, FLAKE = 2, ORB = 3, FLASH = 4;

    private static final List<Air> LIVE = new ArrayList<>();
    private static int ticks, nextFlash = 300, thunderIn, nextToll = 1200, tolls, nextWalk = 200;
    private static float thunderVol;
    private static final List<Vec3> WALK = new ArrayList<>();
    private static float walked = -1;
    private static WorldPayloads.Arrival seen;

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(GatesRegistry.P_WISP.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, WISP));
        e.registerSpriteSet(GatesRegistry.P_STREAK.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, STREAK));
        e.registerSpriteSet(GatesRegistry.P_FLAKE.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, FLAKE));
        e.registerSpriteSet(GatesRegistry.P_ORB.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, ORB));
        e.registerSpriteSet(GatesRegistry.P_FLASH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Air(l, x, y, z, xa, ya, za, s, FLASH));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LIVE.removeIf(a -> !a.isAlive());
        WorldPayloads.Arrival a = GateWorldClient.current();
        Player pl = mc.player;
        if (a != seen) {
            // a new floor: a new procession, the far toll starts quiet again
            seen = a;
            WALK.clear();
            walked = -1;
            tolls = 0;
        }
        if (a == null || pl == null || mc.level == null || WorldJourney.dark()) return;
        ticks++;
        ClientLevel level = mc.level;
        RandomSource r = pl.getRandom();
        FloorAir air = a.air();
        BlockPos o = a.origin();
        for (String k : a.life()) {
            if (LIVE.size() >= MOST) break;
            switch (k) {
                case "shaft_dust" -> shaftDust(level, pl, air, o, r);
                case "mist" -> mist(level, pl, air, o, r);
                case "smoke" -> smoke(level, pl, air, o, r);
                case "rain" -> rain(level, pl, air, o, r);
                case "lightning" -> lightning(mc, pl, air, o, r);
                case "procession" -> procession(level, pl, air, o, r);
                case "far_toll" -> farToll(mc, r);
                case "ash" -> fall(level, pl, r, FLAKE, 0x5a5550, 0.9F, 0.03, 2);
                case "snow" -> fall(level, pl, r, FLAKE, 0xf2f6ff, 1.1F, 0.05, 3);
                case "petals" -> fall(level, pl, r, FLAKE, 0xf0a8c8, 1.3F, 0.025, 1);
                case "paper" -> fall(level, pl, r, FLAKE, 0xe8dcc0, 1.6F, 0.02, 1);
                case "sand" -> fall(level, pl, r, FLAKE, 0xd8b878, 0.7F, 0.01, 3);
                case "spores" -> drift(level, pl, r, ORB, 0x9cf0d8, 0.35F, 0.004, 1);
                case "embers" -> drift(level, pl, r, ORB, 0xff8a3a, 0.25F, -0.03, 1);
                case "fireflies" -> drift(level, pl, r, ORB, 0xd8f080, 0.3F, 0.0, 1);
                case "steam" -> steam(level, pl, air, o, r);
                default -> {}
            }
        }
    }

    private static Air make(ClientLevel level, SimpleParticleType type, double x, double y, double z, double vx, double vy, double vz,
                            int tint, float size) {
        Particle p = Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, vx, vy, vz);
        if (!(p instanceof Air air)) return null;
        air.setColor(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F);
        air.scale(size);
        LIVE.add(air);
        return air;
    }

    private static Vec3 at(BlockPos o, double x, double y, double z) {
        return new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z);
    }

    /** Lighter than a colour, for dust caught in a shaft of it. */
    private static int pale(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + 255) / 2) << 16 | ((g + 255) / 2) << 8 | (b + 255) / 2;
    }

    /** Motes turning slowly in the light shafts near you. */
    private static void shaftDust(ClientLevel level, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (air.shafts().isEmpty()) return;
        for (int t = 0; t < 2; t++) {
            FloorAir.Shaft s = air.shafts().get(r.nextInt(air.shafts().size()));
            double f = r.nextDouble();
            Vec3 p = at(o, s.x0() + (s.x1() - s.x0()) * f + 0.5, s.y0() + (s.y1() - s.y0()) * f + 0.5, s.z0() + (s.z1() - s.z0()) * f + 0.5)
                    .add(r.nextGaussian() * s.radius() * 0.5, r.nextGaussian() * s.radius() * 0.5, r.nextGaussian() * s.radius() * 0.5);
            if (pl.distanceToSqr(p) > 40 * 40 || !WorldLife.open(level, p)) continue;
            Air a = make(level, GatesRegistry.P_FLAKE.get(), p.x, p.y, p.z, (r.nextFloat() - 0.5) * 0.003, -0.0015, (r.nextFloat() - 0.5) * 0.003,
                    pale(s.rgb()), 0.35F);
            if (a != null) {
                a.glow = true;
                a.peak = 0.55F;
                a.setLifetime(160 + r.nextInt(160));
            }
        }
    }

    /** Mist lying on still water near you, drifting. */
    private static void mist(ClientLevel level, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (ticks % 3 != 0 || air.waters().isEmpty()) return;
        FloorAir.Water w = air.waters().get(r.nextInt(air.waters().size()));
        double x = pl.getX() + (r.nextFloat() - 0.5) * 40, z = pl.getZ() + (r.nextFloat() - 0.5) * 40;
        double lx = x - o.getX(), lz = z - o.getZ();
        if (lx < w.x0() || lx > w.x1() + 1 || lz < w.z0() || lz > w.z1() + 1) return;
        double y = o.getY() + w.y() + 1.15 + r.nextFloat() * 0.5;
        if (!WorldLife.open(level, new Vec3(x, y, z))) return;
        Air a = make(level, GatesRegistry.P_WISP.get(), x, y, z, (r.nextFloat() - 0.5) * 0.01, 0, (r.nextFloat() - 0.5) * 0.01, 0xc8d4dc, 6F + r.nextFloat() * 5F);
        if (a != null) {
            a.peak = 0.16F;
            a.setLifetime(200 + r.nextInt(120));
        }
    }

    /** A thread of smoke off a candle or a lamp near you (the warm lights). */
    private static void smoke(ClientLevel level, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (ticks % 4 != 0 || air.lights().isEmpty()) return;
        FloorAir.Light li = air.lights().get(r.nextInt(air.lights().size()));
        int rgb = li.rgb();
        if (((rgb >> 16) & 255) < (rgb & 255) + 40) return;
        Vec3 p = at(o, li.x() + 0.5 + r.nextGaussian() * 0.3, li.y() + 0.9, li.z() + 0.5 + r.nextGaussian() * 0.3);
        if (pl.distanceToSqr(p) > 20 * 20 || !WorldLife.open(level, p)) return;
        Air a = make(level, GatesRegistry.P_WISP.get(), p.x, p.y, p.z, (r.nextFloat() - 0.5) * 0.004, 0.012 + r.nextFloat() * 0.01,
                (r.nextFloat() - 0.5) * 0.004, 0x8c8478, 0.35F + r.nextFloat() * 0.2F);
        if (a != null) {
            a.peak = 0.3F;
            a.grow = 0.012F;
            a.setLifetime(60 + r.nextInt(40));
        }
    }

    /** Rain coming in through the broken windows (the light shafts' tops) near you, and down the shafts. */
    private static void rain(ClientLevel level, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (air.shafts().isEmpty()) {
            // no windows: an open world, the rain falls all round you
            for (int t = 0; t < 6; t++) {
                Vec3 p = new Vec3(pl.getX() + (r.nextFloat() - 0.5) * 36, pl.getY() + 8 + r.nextFloat() * 10, pl.getZ() + (r.nextFloat() - 0.5) * 36);
                if (!WorldLife.open(level, p)) continue;
                Air a = make(level, GatesRegistry.P_STREAK.get(), p.x, p.y, p.z, 0.05, -0.9, 0.02, 0xa8b8c8, 0.9F);
                if (a != null) {
                    a.peak = 0.45F;
                    a.setLifetime(60);
                }
            }
            return;
        }
        for (int t = 0; t < 4; t++) {
            FloorAir.Shaft s = air.shafts().get(r.nextInt(air.shafts().size()));
            double f = r.nextDouble() * 0.3;
            Vec3 p = at(o, s.x0() + (s.x1() - s.x0()) * f + 0.5, s.y0() + (s.y1() - s.y0()) * f + 0.5, s.z0() + (s.z1() - s.z0()) * f + 0.5)
                    .add(r.nextGaussian() * s.radius() * 0.6, 0, r.nextGaussian() * s.radius() * 0.6);
            if (pl.distanceToSqr(p) > 36 * 36 || !WorldLife.open(level, p)) continue;
            Vec3 d = new Vec3(s.x1() - s.x0(), s.y1() - s.y0(), s.z1() - s.z0()).normalize();
            Air a = make(level, GatesRegistry.P_STREAK.get(), p.x, p.y, p.z, d.x * 0.2, -0.9, d.z * 0.2, 0xa8b8c8, 0.9F);
            if (a != null) {
                a.peak = 0.5F;
                a.setLifetime(80);
            }
        }
    }

    /** Lightning in the windows near you, the thunder after it (sooner and louder the nearer); far off, thunder alone. */
    private static void lightning(Minecraft mc, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (thunderIn > 0 && --thunderIn == 0)
            mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(GatesRegistry.SND_WORLD_STORM.get(), 0.75F + r.nextFloat() * 0.2F, thunderVol));
        if (--nextFlash > 0) return;
        nextFlash = 500 + r.nextInt(900);
        double near = 999;
        for (FloorAir.Shaft s : air.shafts()) {
            Vec3 p = at(o, s.x0() + 0.5, s.y0() + 0.5, s.z0() + 0.5);
            double d = Math.sqrt(pl.distanceToSqr(p));
            if (d > 72) continue;
            near = Math.min(near, d);
            for (int k = 0; k < 2; k++) {
                Air a = make(mc.level, GatesRegistry.P_FLASH.get(), p.x, p.y, p.z, 0, 0, 0, 0xe4ecff, 7F + r.nextFloat() * 3);
                if (a != null) {
                    a.glow = true;
                    a.peak = 0.9F;
                    a.setLifetime(3 + k * 3);
                }
            }
        }
        thunderIn = near > 72 ? 1 : 6 + (int) (near / 3);
        thunderVol = near > 72 ? 0.6F : 0.95F;
    }

    /**
     * A procession of ghost lights walking slowly from candle to candle near you, then fading; another in a while. The
     * way is the warm lights chained nearest to nearest, so it follows the causeway, the passages or the shore.
     */
    private static void procession(ClientLevel level, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (WALK.isEmpty()) {
            if (--nextWalk > 0 || air.lights().size() < 4) return;
            nextWalk = 400 + r.nextInt(500);
            FloorAir.Light first = null;
            double best = 1e9;
            for (FloorAir.Light li : air.lights()) {
                double d = pl.distanceToSqr(at(o, li.x() + 0.5, li.y(), li.z() + 0.5));
                if (d > 14 * 14 && d < 36 * 36 && d < best) {
                    best = d;
                    first = li;
                }
            }
            if (first == null) return;
            List<FloorAir.Light> left = new ArrayList<>(air.lights());
            FloorAir.Light at = first;
            while (at != null && WALK.size() < 14) {
                left.remove(at);
                WALK.add(at(o, at.x() + 0.5, at.y() + 1.6, at.z() + 0.5));
                FloorAir.Light next = null;
                double nd = 14 * 14;
                for (FloorAir.Light li : left) {
                    double dx = li.x() - at.x(), dy = li.y() - at.y(), dz = li.z() - at.z();
                    double d = dx * dx + dy * dy * 4 + dz * dz;
                    if (d > 4 && d < nd) {
                        nd = d;
                        next = li;
                    }
                }
                at = next;
            }
            if (WALK.size() < 3) {
                WALK.clear();
                return;
            }
            walked = 0;
            for (int i = 0; i < 7; i++) {
                Vec3 p = WALK.get(0);
                Air a = make(level, GatesRegistry.P_ORB.get(), p.x, p.y, p.z, 0, 0, 0, 0xcfe4ff, 0.55F);
                if (a != null) {
                    a.glow = true;
                    a.peak = 0.75F;
                    a.walk = -i * 2.2F;
                    a.setLifetime(20 * 60);
                }
            }
            return;
        }
        walked += 0.045F;
        float length = 0;
        for (int i = 1; i < WALK.size(); i++) length += (float) WALK.get(i).distanceTo(WALK.get(i - 1));
        if (walked > length + 18) {
            WALK.clear();
            walked = -1;
        }
    }

    /** Where a ghost light this far along the procession stands (null before the start or past the end). */
    static Vec3 walkAt(float along) {
        if (WALK.size() < 2 || along < 0) return null;
        for (int i = 1; i < WALK.size(); i++) {
            Vec3 a = WALK.get(i - 1), b = WALK.get(i);
            float d = (float) a.distanceTo(b);
            if (along <= d) return a.lerp(b, along / d);
            along -= d;
        }
        return null;
    }

    /** A far bell, every two or three minutes, a little louder each time while you're on the floor. */
    private static void farToll(Minecraft mc, RandomSource r) {
        if (--nextToll > 0) return;
        nextToll = 2400 + r.nextInt(1200);
        float vol = Math.min(0.85F, 0.25F + 0.12F * tolls++);
        mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(GatesRegistry.SND_WORLD_TOLL.get(), 0.62F + r.nextFloat() * 0.06F, vol));
    }

    /** Something falling through the air round you: ash, snow, petals, paper, sand. */
    private static void fall(ClientLevel level, Player pl, RandomSource r, int kind, int tint, float size, double sway, int per) {
        for (int t = 0; t < per; t++) {
            Vec3 p = new Vec3(pl.getX() + (r.nextFloat() - 0.5) * 32, pl.getY() + 4 + r.nextFloat() * 12, pl.getZ() + (r.nextFloat() - 0.5) * 32);
            if (!WorldLife.open(level, p)) continue;
            Air a = make(level, GatesRegistry.P_FLAKE.get(), p.x, p.y, p.z, (r.nextFloat() - 0.5) * sway, -0.03 - r.nextFloat() * 0.03,
                    (r.nextFloat() - 0.5) * sway, tint, size);
            if (a != null) {
                a.peak = 0.85F;
                a.sway = (float) sway;
                a.setLifetime(220 + r.nextInt(120));
            }
        }
    }

    /** Glowing things drifting round you: spores, embers rising, fireflies blinking. */
    private static void drift(ClientLevel level, Player pl, RandomSource r, int kind, int tint, float size, double rise, int per) {
        if (ticks % 2 != 0) return;
        for (int t = 0; t < per; t++) {
            Vec3 p = new Vec3(pl.getX() + (r.nextFloat() - 0.5) * 28, pl.getY() + r.nextFloat() * 9, pl.getZ() + (r.nextFloat() - 0.5) * 28);
            if (!WorldLife.open(level, p)) continue;
            Air a = make(level, GatesRegistry.P_ORB.get(), p.x, p.y, p.z, (r.nextFloat() - 0.5) * 0.01, rise, (r.nextFloat() - 0.5) * 0.01, tint, size);
            if (a != null) {
                a.glow = true;
                a.peak = 0.8F;
                a.blink = rise == 0;
                a.setLifetime(140 + r.nextInt(160));
            }
        }
    }

    /** Steam lifting off warm water near you. */
    private static void steam(ClientLevel level, Player pl, FloorAir air, BlockPos o, RandomSource r) {
        if (ticks % 3 != 0 || air.waters().isEmpty()) return;
        FloorAir.Water w = air.waters().get(r.nextInt(air.waters().size()));
        double x = pl.getX() + (r.nextFloat() - 0.5) * 30, z = pl.getZ() + (r.nextFloat() - 0.5) * 30;
        double lx = x - o.getX(), lz = z - o.getZ();
        if (lx < w.x0() || lx > w.x1() + 1 || lz < w.z0() || lz > w.z1() + 1) return;
        double y = o.getY() + w.y() + 1.1;
        if (!WorldLife.open(level, new Vec3(x, y, z))) return;
        Air a = make(level, GatesRegistry.P_WISP.get(), x, y, z, 0, 0.02 + r.nextFloat() * 0.015, 0, 0xe8ecf0, 1.2F + r.nextFloat());
        if (a != null) {
            a.peak = 0.22F;
            a.grow = 0.02F;
            a.setLifetime(90 + r.nextInt(50));
        }
    }

    /** One sprite of the air: it moves itself, fades in and out, and some glow (lit fully, whatever the light). */
    static final class Air extends SingleQuadParticle {
        final int kind;
        boolean glow, blink;
        float peak = 0.6F, grow, sway, walk = Float.NaN;
        private final float base;

        Air(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, int kind) {
            super(level, x, y, z, sprites.get(level.getRandom()));
            this.kind = kind;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.gravity = 0;
            this.friction = 1;
            this.hasPhysics = kind == STREAK || kind == FLAKE;
            this.alpha = 0;
            this.base = quadSize = kind == STREAK ? 0.12F : kind == FLASH ? 1F : 0.1F;
            this.lifetime = 100;
            this.roll = kind == WISP || kind == FLAKE ? random.nextFloat() * Mth.TWO_PI : 0;
            this.oRoll = roll;
        }

        @Override
        public Particle scale(float s) {
            quadSize = base * s;
            return this;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            oRoll = roll;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            float t = age / (float) lifetime;
            float fade = kind == FLASH ? 1 - t : Math.min(1, Math.min(t * 6, (1 - t) * 4));
            alpha = peak * fade * (blink ? 0.4F + 0.6F * Math.max(0, Mth.sin(age * 0.15F + (float) (x * 3))) : 1);
            quadSize += grow;
            if (!Float.isNaN(walk)) {
                // a ghost light of the procession: it walks the way, a little behind the one in front, bobbing
                walk += 0.045F;
                Vec3 p = walkAt(walk);
                if (p == null) {
                    if (walk > 0 && age > 40) lifetime = Math.min(lifetime, age + 30);
                    alpha = 0;
                    return;
                }
                setPos(p.x, p.y + Mth.sin(age * 0.08F + walk) * 0.15, p.z);
                return;
            }
            if (sway > 0) {
                xd += (random.nextFloat() - 0.5) * sway * 0.2;
                zd += (random.nextFloat() - 0.5) * sway * 0.2;
                roll += 0.03F;
            }
            if (kind == WISP) roll += 0.004F;
            move(xd, yd, zd);
            if (hasPhysics && (onGround || x == xo && z == zo && y == yo)) remove();
        }

        @Override
        public FacingCameraMode getFacingCameraMode() {
            return kind == STREAK ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
        }

        @Override
        protected int getLightCoords(float a) {
            return glow ? 0xF000F0 : super.getLightCoords(a);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }
}
