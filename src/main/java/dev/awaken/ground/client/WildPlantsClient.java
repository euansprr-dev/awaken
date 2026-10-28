package dev.awaken.ground.client;

import dev.awaken.ground.wild.WildBlocks;
import dev.awaken.ground.wild.WildDoubleBlock;
import dev.awaken.ground.wild.WildKind;
import dev.awaken.ground.wild.WildLife;
import dev.awaken.ground.wild.WildLooks;
import dev.awaken.terrain.Wind;
import dev.awaken.terrain.client.ClientWind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * The wild's plants on the client (SPEC §41.2): their colours (the game's grass and leaf colours, with the lawn's drift), seed
 * fluff lifted off seeding grass and rye by gusts, a swish as you push through tall plants, and the meadow's hiss rising
 * with the wind where long grass stands around you. Nothing here allocates per tick.
 */
public final class WildPlantsClient {
    private WildPlantsClient() {}

    /** Seed fluff: odds a seeding head that the game's ambient pass visits lets a seed go, at a full gust. */
    private static final float SEED_ODDS = 0.06F;
    /** Ticks between two swishes of the same body (a stride or so). */
    private static final int SWISH_GAP = 7;
    /** The ambient pass visits ~1,300 blocks a tick around you; this many long-grass hits a tick is a full meadow. */
    private static final float MEADOW_FULL = 28F;

    private static float meadowHits, meadow;
    private static long lastSwish;
    private static Hiss hiss;

    public static void init(IEventBus modBus) {
        modBus.addListener(WildPlantsClient::tints);
        modBus.addListener((RegisterParticleProvidersEvent e) -> e.registerSpriteSet(WildBlocks.SEED.get(), Seed.Provider::new));
        NeoForge.EVENT_BUS.addListener(WildPlantsClient::tick);
        WildLife.installFx(new WildLife.Fx() {
            @Override
            public void ambient(BlockState state, Level level, BlockPos pos, RandomSource random) {
                WildPlantsClient.ambient(state, level, pos, random);
            }

            @Override
            public void brush(BlockState state, Level level, BlockPos pos, Entity entity) {
                WildPlantsClient.brush(level, entity);
            }
        });
    }

    private static void tints(RegisterColorHandlersEvent.BlockTintSources e) {
        BlockTintSource grass = new GroundClient.DriftTint(BlockTintSources.grass());
        BlockTintSource tall = new GroundClient.DriftTint(BlockTintSources.doubleTallGrass());
        e.register(List.of(grass), WildBlocks.MEADOW_GRASS.get(), WildBlocks.FROST_GRASS.get(), WildBlocks.RYE_STEMS.get(),
                WildBlocks.TRAMPLED_GRASS.get(), WildBlocks.CLOVER.get(), WildBlocks.THISTLE.get(), WildBlocks.HEATHER.get(),
                WildBlocks.GORSE.get(), WildBlocks.DRY_TUSSOCK.get(), WildBlocks.BRAMBLE.get());
        e.register(List.of(tall), WildBlocks.LONG_GRASS.get(), WildBlocks.WILD_RYE.get(), WildBlocks.COW_PARSLEY.get(),
                WildBlocks.FOXGLOVE.get(), WildBlocks.BRACKEN.get(), WildBlocks.REEDS.get());
        e.register(List.of(new GroundClient.DriftTint(BlockTintSources.grassBlock())), WildBlocks.WILD_SWARD.get());
        e.register(List.of(BlockTintSources.foliage()), WildBlocks.HAWTHORN_LEAVES.get());
    }

    // ---- the ambient pass: seed fluff, and how much meadow is around ---------------------------------------------------

    private static void ambient(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Block b = state.getBlock();
        if (!(b instanceof WildDoubleBlock d) || state.getValue(DoublePlantBlock.HALF) != DoubleBlockHalf.UPPER) return;
        WildKind kind = d.kind();
        if (kind == WildKind.LONG || kind == WildKind.RYE) meadowHits++;
        boolean seeding = kind == WildKind.RYE || kind == WildKind.LONG && state.getValue(WildDoubleBlock.LOOK) == WildLooks.SEEDING;
        if (!seeding) return;
        Wind.Field wind = ClientWind.field();
        if (wind == null) return;
        float gust = wind.gust(pos.getX(), pos.getZ());
        if (gust < 0.35F || random.nextFloat() > SEED_ODDS * gust) return;
        level.addParticle(WildBlocks.SEED.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.6 + random.nextDouble() * 0.4,
                pos.getZ() + random.nextDouble(), 0, 0, 0);
    }

    // ---- pushing through -------------------------------------------------------------------------------------------------

    private static void brush(Level level, Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || level.getGameTime() - lastSwish < SWISH_GAP) return;
        double speed = entity.getKnownMovement().horizontalDistance();
        if (speed < 0.03) return;
        lastSwish = level.getGameTime();
        float volume = (float) Math.min(1.0, 0.35 + speed * 2.5);
        level.playLocalSound(entity.getX(), entity.getY() + 0.4, entity.getZ(), WildBlocks.SWISH.get(), SoundSource.BLOCKS, volume,
                0.9F + level.getRandom().nextFloat() * 0.25F, false);
    }

    // ---- the meadow's hiss ---------------------------------------------------------------------------------------------

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        meadow += (Math.min(1F, meadowHits / MEADOW_FULL) - meadow) * 0.05F;
        meadowHits = 0;
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (hiss != null && hiss.isStopped()) hiss = null;
        if (hiss == null && target(mc) > 0.02F) {
            hiss = new Hiss();
            mc.getSoundManager().play(hiss);
        }
    }

    /** How loud the hiss should be now: the wind's strength where you stand times how much meadow is around you. */
    private static float target(Minecraft mc) {
        Wind.Field wind = ClientWind.field();
        if (wind == null || mc.player == null) return 0F;
        float s = wind.strength(mc.player.getX(), mc.player.getZ());
        return meadow * (0.15F + 0.85F * s) * 0.55F;
    }

    /** The meadow's hiss: one loop around the listener, eased toward the wind, ended when there is nothing left to hear. */
    private static final class Hiss extends AbstractTickableSoundInstance {
        private float level;

        Hiss() {
            super(WildBlocks.HISS.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            float t = mc.level == null ? 0F : target(mc);
            level += (t - level) * 0.04F;
            volume = Math.max(0.001F, level);
            if (mc.level == null || t < 0.01F && level < 0.012F) stop();
        }
    }

    // ---- seed fluff ------------------------------------------------------------------------------------------------------

    /** A seed on the wind: rides the gust at its own place, rises a little while the gust holds, sinks and fades as it drops. */
    static final class Seed extends SingleQuadParticle {
        private final float bob;

        Seed(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0, 0, 0, sprites.get(level.getRandom()));
            this.hasPhysics = false;
            this.gravity = 0F;
            this.friction = 1F;
            this.lifetime = 60 + random.nextInt(80);
            this.quadSize = 0.035F + random.nextFloat() * 0.03F;
            this.bob = random.nextFloat() * 6.28F;
            this.xd = this.yd = this.zd = 0;
            float shade = 0.92F + random.nextFloat() * 0.08F;
            setColor(shade, shade, shade * 0.95F);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (++age >= lifetime) {
                remove();
                return;
            }
            Wind.Field wind = ClientWind.field();
            if (wind == null) {
                remove();
                return;
            }
            float strength = wind.strength(x, z), gust = wind.gust(x, z);
            float speed = 0.03F + 0.16F * strength + 0.08F * gust;
            xd += (wind.dirX * speed - xd) * 0.1;
            zd += (wind.dirZ * speed - zd) * 0.1;
            yd += ((gust > 0.4F ? 0.012F : -0.008F) + 0.006F * (float) Math.sin(age * 0.3 + bob) - yd) * 0.1;
            x += xd;
            y += yd;
            z += zd;
            float left = lifetime - age;
            alpha = Math.min(1F, age / 8F) * Math.min(1F, left / 20F);
        }

        record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z, double xa, double ya, double za,
                                           RandomSource random) {
                return new Seed(level, x, y, z, sprites);
            }
        }
    }
}
