package dev.awaken.gates.client;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.gates.world.FloorAir;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A world floor's small life and the sounds set round you (SPEC §38.9), all on the client, never saved or sent: moths
 * round its lights, bats under its vaults, pale fish under its water, rats along its floors, dust in its air; drips off
 * the stone near you. The world's recipe names which ({@code life}, carried on the arrival). At
 * most {@value #MOST} creatures near you, made out of sight or well off and faded in, gone past {@value #GONE} blocks.
 */
final class WorldLife {
    private WorldLife() {}

    static final int MOST = 40;
    /** Made within, and gone past, this far from you. */
    static final double NEAR = 24, GONE = 36;
    /** How many of each live near you at once. */
    static final int MOTHS = 10, BATS = 6, FISH = 8, RATS = 4;

    static final int MOTH = 0, BAT = 1, FISH_K = 2, RAT = 3;

    private static final List<Life> LIVE = new ArrayList<>();
    private static final int[] COUNT = new int[4];
    private static int ticks, nextDrip = 60;

    static void register(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(GatesRegistry.P_MOTH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Life(l, x, y, z, s, MOTH));
        e.registerSpriteSet(GatesRegistry.P_BAT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Life(l, x, y, z, s, BAT));
        e.registerSpriteSet(GatesRegistry.P_FISH.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Life(l, x, y, z, s, FISH_K));
        e.registerSpriteSet(GatesRegistry.P_RAT.get(), s -> (o, l, x, y, z, xa, ya, za, r) -> new Life(l, x, y, z, s, RAT));
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LIVE.removeIf(l -> !l.isAlive());
        WorldPayloads.Arrival a = GateWorldClient.current();
        Player pl = mc.player;
        if (a == null || pl == null || mc.level == null || WorldJourney.dark()) return;
        ticks++;
        List<String> life = a.life();
        if (life.contains("dust")) dust(mc.level, pl, a);
        if (life.contains("drips") && --nextDrip <= 0) {
            nextDrip = 40 + pl.getRandom().nextInt(100);
            drip(mc, pl);
        }
        if (ticks % 10 != 0) return;
        java.util.Arrays.fill(COUNT, 0);
        for (Life l : LIVE) COUNT[l.kind]++;
        for (String k : life) {
            switch (k) {
                case "moths" -> top(mc, pl, a, MOTH, MOTHS);
                case "bats" -> top(mc, pl, a, BAT, BATS);
                case "pale_fish" -> top(mc, pl, a, FISH_K, FISH);
                case "rats" -> top(mc, pl, a, RAT, RATS);
                default -> {}
            }
        }
    }

    /** Up to two more of a kind this round, while it's under its number and everything under {@link #MOST}. */
    private static void top(Minecraft mc, Player pl, WorldPayloads.Arrival a, int kind, int want) {
        for (int i = 0; i < 2 && COUNT[kind] < want && LIVE.size() < MOST; i++) {
            Vec3 at = switch (kind) {
                case MOTH -> mothSpot(mc.level, pl, a);
                case BAT -> batSpot(mc.level, pl);
                case FISH_K -> fishSpot(mc.level, pl, a);
                default -> ratSpot(mc.level, pl);
            };
            if (at == null) return;
            SimpleParticleType type = switch (kind) {
                case MOTH -> GatesRegistry.P_MOTH.get();
                case BAT -> GatesRegistry.P_BAT.get();
                case FISH_K -> GatesRegistry.P_FISH.get();
                default -> GatesRegistry.P_RAT.get();
            };
            Particle p = mc.particleEngine.createParticle(type, at.x, at.y, at.z, 0, 0, 0);
            if (!(p instanceof Life l)) return;
            l.anchor(at);
            LIVE.add(l);
            COUNT[kind]++;
        }
    }

    /** Out of sight (behind you) or well off, so nothing pops into view. */
    private static boolean unseen(Player pl, Vec3 at) {
        Vec3 to = at.subtract(pl.getEyePosition());
        double d = to.length();
        return d > 16 || to.dot(pl.getLookAngle()) < 0.2 * d;
    }

    private static Vec3 mothSpot(ClientLevel level, Player pl, WorldPayloads.Arrival a) {
        List<FloorAir.Light> lights = a.air().lights();
        if (lights.isEmpty()) return null;
        BlockPos o = a.origin();
        for (int t = 0; t < 6; t++) {
            FloorAir.Light li = lights.get(pl.getRandom().nextInt(lights.size()));
            double x = o.getX() + li.x() + 0.5, y = o.getY() + li.y() + 0.5, z = o.getZ() + li.z() + 0.5;
            if (pl.distanceToSqr(x, y, z) > NEAR * NEAR) continue;
            Vec3 at = new Vec3(x + pl.getRandom().nextGaussian() * 0.8, y + 0.4 + pl.getRandom().nextFloat() * 0.8, z + pl.getRandom().nextGaussian() * 0.8);
            if (open(level, at)) return at;
        }
        return null;
    }

    private static Vec3 batSpot(ClientLevel level, Player pl) {
        for (int t = 0; t < 6; t++) {
            double ang = pl.getRandom().nextFloat() * Mth.TWO_PI, d = 8 + pl.getRandom().nextFloat() * 14;
            Vec3 at = new Vec3(pl.getX() + Math.cos(ang) * d, pl.getY() + 6 + pl.getRandom().nextFloat() * 12, pl.getZ() + Math.sin(ang) * d);
            if (open(level, at) && open(level, at.add(0, 1.5, 0)) && open(level, at.add(0, -1.5, 0)) && unseen(pl, at)) return at;
        }
        return null;
    }

    private static Vec3 fishSpot(ClientLevel level, Player pl, WorldPayloads.Arrival a) {
        List<FloorAir.Water> waters = a.air().waters();
        if (waters.isEmpty()) return null;
        BlockPos o = a.origin();
        for (int t = 0; t < 8; t++) {
            FloorAir.Water w = waters.get(pl.getRandom().nextInt(waters.size()));
            int x = o.getX() + Mth.nextInt(pl.getRandom(), w.x0(), w.x1()), z = o.getZ() + Mth.nextInt(pl.getRandom(), w.z0(), w.z1());
            BlockPos b = new BlockPos(x, o.getY() + w.y(), z);
            if (pl.distanceToSqr(Vec3.atCenterOf(b)) > NEAR * NEAR || !level.getFluidState(b).is(FluidTags.WATER)) continue;
            Vec3 at = new Vec3(x + 0.5, b.getY() + 0.42, z + 0.5);
            if (unseen(pl, at) || pl.distanceToSqr(at) > 100) return at;
        }
        return null;
    }

    private static Vec3 ratSpot(ClientLevel level, Player pl) {
        for (int t = 0; t < 8; t++) {
            double ang = pl.getRandom().nextFloat() * Mth.TWO_PI, d = 5 + pl.getRandom().nextFloat() * 12;
            int x = Mth.floor(pl.getX() + Math.cos(ang) * d), z = Mth.floor(pl.getZ() + Math.sin(ang) * d);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos b = BlockPos.containing(x, pl.getY() + dy, z);
                if (!Life.floor(level, b)) continue;
                // along a wall, where rats run
                boolean wall = false;
                for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) wall |= !level.getBlockState(b.relative(dir)).getCollisionShape(level, b).isEmpty();
                Vec3 at = new Vec3(x + 0.5, b.getY(), z + 0.5);
                if (wall && unseen(pl, at)) return at;
                break;
            }
        }
        return null;
    }

    static boolean open(ClientLevel level, Vec3 at) {
        BlockPos b = BlockPos.containing(at);
        return level.getBlockState(b).getCollisionShape(level, b).isEmpty() && level.getFluidState(b).isEmpty();
    }

    /** Dust drifting in the air round you, thicker in the light coming down. */
    private static void dust(ClientLevel level, Player pl, WorldPayloads.Arrival a) {
        if ((ticks & 1) != 0) return;
        Vec3 at = new Vec3(pl.getX() + (pl.getRandom().nextFloat() - 0.5) * 16, pl.getY() + pl.getRandom().nextFloat() * 8,
                pl.getZ() + (pl.getRandom().nextFloat() - 0.5) * 16);
        if (!open(level, at)) return;
        RigParticles.nextTint = 0xd9cfba;
        RigParticles.nextSize = 0.5F;
        level.addParticle(GatesRegistry.P_MOTE.get(), at.x, at.y, at.z, (pl.getRandom().nextFloat() - 0.5) * 0.004, -0.002, (pl.getRandom().nextFloat() - 0.5) * 0.004);
        RigParticles.nextTint = 0xFFFFFF;
        RigParticles.nextSize = 1F;
    }

    /** A drip off the stone somewhere near you: heard where it lands, under a ceiling. */
    private static void drip(Minecraft mc, Player pl) {
        ClientLevel level = mc.level;
        int x = Mth.floor(pl.getX() + (pl.getRandom().nextFloat() - 0.5) * 20), z = Mth.floor(pl.getZ() + (pl.getRandom().nextFloat() - 0.5) * 20);
        int y0 = Mth.floor(pl.getY());
        boolean roof = false;
        for (int dy = 2; dy < 18 && !roof; dy++) roof = !level.getBlockState(new BlockPos(x, y0 + dy, z)).isAir();
        if (!roof) return;
        for (int dy = 1; dy >= -6; dy--) {
            BlockPos b = new BlockPos(x, y0 + dy, z);
            if (level.getBlockState(b).isAir() && !level.getBlockState(b.below()).isAir()) {
                mc.getSoundManager().play(new SimpleSoundInstance(GatesRegistry.SND_WORLD_DRIP.get(), SoundSource.AMBIENT,
                        0.5F + pl.getRandom().nextFloat() * 0.3F, 0.85F + pl.getRandom().nextFloat() * 0.3F, pl.getRandom(), x + 0.5, b.getY(), z + 0.5));
                return;
            }
        }
    }

    /** One small creature, moving itself (no collision physics): it keeps to the air, the water or the floor it was made in. */
    static final class Life extends SingleQuadParticle {
        final int kind;
        private final SpriteSet sprites;
        private final int frames;
        private double ax, ay, az;
        private float heading, turn, fade;
        private int rest, dash;
        private boolean flip;
        private final FacingCameraMode flat = (q, cam, pt) -> q.rotationY(-heading).rotateX(-Mth.HALF_PI);

        Life(ClientLevel level, double x, double y, double z, SpriteSet sprites, int kind) {
            super(level, x, y, z, sprites.first());
            this.sprites = sprites;
            this.kind = kind;
            this.frames = kind == BAT ? 3 : 2;
            this.hasPhysics = false;
            this.gravity = 0;
            this.alpha = 0;
            this.heading = random.nextFloat() * Mth.TWO_PI;
            switch (kind) {
                case MOTH -> {
                    quadSize = 0.08F + random.nextFloat() * 0.03F;
                    lifetime = 400 + random.nextInt(400);
                    turn = (random.nextBoolean() ? 1 : -1) * (0.12F + random.nextFloat() * 0.12F);
                }
                case BAT -> {
                    quadSize = 0.26F + random.nextFloat() * 0.08F;
                    lifetime = 400 + random.nextInt(300);
                }
                case FISH_K -> {
                    quadSize = 0.16F + random.nextFloat() * 0.08F;
                    lifetime = 600 + random.nextInt(400);
                    setColor(0.92F, 0.96F, 1F);
                }
                default -> {
                    quadSize = 0.17F + random.nextFloat() * 0.04F;
                    lifetime = 300 + random.nextInt(300);
                    rest = 10 + random.nextInt(30);
                }
            }
        }

        void anchor(Vec3 at) {
            ax = at.x;
            ay = at.y;
            az = at.z;
        }

        static boolean floor(ClientLevel level, BlockPos b) {
            return level.getBlockState(b).getCollisionShape(level, b).isEmpty() && level.getFluidState(b).isEmpty()
                    && level.getBlockState(b.below()).isFaceSturdy(level, b.below(), net.minecraft.core.Direction.UP);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            Player pl = Minecraft.getInstance().player;
            if (age++ >= lifetime || pl == null || pl.distanceToSqr(x, y, z) > GONE * GONE) {
                remove();
                return;
            }
            fade = Math.min(1, Math.min(age, lifetime - age) / 20F);
            alpha = kind == FISH_K ? fade * 0.85F : fade;
            double d2 = pl.distanceToSqr(x, y, z);
            switch (kind) {
                case MOTH -> moth();
                case BAT -> bat(d2, pl);
                case FISH_K -> fish();
                default -> rat(d2, pl);
            }
        }

        /** Round and round its light, never quite steady. */
        private void moth() {
            heading += turn + (random.nextFloat() - 0.5F) * 0.2F;
            double r = 0.7 + 0.4 * Math.sin(age * 0.05 + ax);
            double tx = ax + Math.cos(heading) * r, ty = ay + Math.sin(age * 0.09) * 0.35, tz = az + Math.sin(heading) * r;
            setPos(x + (tx - x) * 0.35 + (random.nextFloat() - 0.5) * 0.04, y + (ty - y) * 0.3, z + (tz - z) * 0.35 + (random.nextFloat() - 0.5) * 0.04);
            setSprite(sprites.get(age % 2, 1));
            if (random.nextInt(600) == 0) turn = -turn;
        }

        /** Swooping loops under the vault, turning off the stone, off you if you come close. */
        private void bat(double d2, Player pl) {
            turn = Mth.clamp(turn + (random.nextFloat() - 0.5F) * 0.06F, -0.14F, 0.14F);
            heading += turn;
            double dx = ax - x, dz = az - z;
            if (dx * dx + dz * dz > 14 * 14) heading = Mth.rotLerpRad(0.1F, heading, (float) Mth.atan2(dz, dx));
            if (d2 < 16) heading = (float) Mth.atan2(z - pl.getZ(), x - pl.getX());
            double sp = d2 < 16 ? 0.4 : 0.24;
            double nx = x + Math.cos(heading) * sp, nz = z + Math.sin(heading) * sp, ny = y + Math.sin(age * 0.13) * 0.06 + (ay - y) * 0.01;
            if (!open(level, new Vec3(nx + Math.cos(heading) * 0.8, ny, nz + Math.sin(heading) * 0.8)) || !open(level, new Vec3(nx, ny, nz))) {
                heading += Mth.PI * (0.6F + random.nextFloat() * 0.5F);
                return;
            }
            setPos(nx, ny, nz);
            int f = (age / 2) % 4;
            setSprite(sprites.get(f == 3 ? 1 : f, 2));
        }

        /** Gliding slowly just under the surface, darting now and then, turning back at the stone. */
        private void fish() {
            if (dash > 0) dash--;
            else if (random.nextInt(160) == 0) dash = 8 + random.nextInt(8);
            turn = Mth.clamp(turn + (random.nextFloat() - 0.5F) * 0.02F, -0.05F, 0.05F);
            heading += turn;
            double sp = dash > 0 ? 0.14 : 0.035;
            double nx = x + Math.cos(heading) * sp, nz = z + Math.sin(heading) * sp;
            BlockPos ahead = BlockPos.containing(nx + Math.cos(heading) * 0.5, y, nz + Math.sin(heading) * 0.5);
            if (!level.getFluidState(ahead).is(FluidTags.WATER)) {
                heading += Mth.PI * (0.7F + random.nextFloat() * 0.4F);
                return;
            }
            setPos(nx, ay + Math.sin(age * 0.05) * 0.04, nz);
            setSprite(sprites.get((age / (dash > 0 ? 2 : 6)) % 2, 1));
        }

        /** Dashes along the floor and stops to sniff; bolts from you. */
        private void rat(double d2, Player pl) {
            if (d2 < 25 && dash == 0) {
                heading = (float) Mth.atan2(z - pl.getZ(), x - pl.getX());
                dash = 20;
                rest = 0;
            }
            if (rest > 0) {
                rest--;
                setSprite(sprites.get(0, 1));
                if (rest == 0) {
                    dash = 8 + random.nextInt(18);
                    heading += (random.nextFloat() - 0.5F) * 2.2F;
                }
                return;
            }
            if (dash > 0 && --dash == 0) rest = 15 + random.nextInt(50);
            double sp = d2 < 25 ? 0.26 : 0.17;
            double nx = x + Math.cos(heading) * sp, nz = z + Math.sin(heading) * sp;
            BlockPos next = BlockPos.containing(nx + Math.cos(heading) * 0.2, y + 0.1, nz + Math.sin(heading) * 0.2);
            if (!floor(level, next)) {
                heading += Mth.PI * (0.5F + random.nextFloat());
                return;
            }
            setPos(nx, next.getY(), nz);
            setSprite(sprites.get((age / 2) % 2, 1));
        }

        @Override
        public FacingCameraMode getFacingCameraMode() {
            return kind == FISH_K ? flat : kind == RAT ? FacingCameraMode.LOOKAT_Y : FacingCameraMode.LOOKAT_XYZ;
        }

        @Override
        public void extract(QuadParticleRenderState state, Camera camera, float partialTickTime) {
            // a rat's sprite faces right: running left across your view, it turns round
            if (kind == RAT) flip = Math.cos(heading) * camera.leftVector().x() + Math.sin(heading) * camera.leftVector().z() > 0;
            super.extract(state, camera, partialTickTime);
        }

        @Override
        protected float getU0() {
            return flip ? super.getU1() : super.getU0();
        }

        @Override
        protected float getU1() {
            return flip ? super.getU0() : super.getU1();
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }
}
