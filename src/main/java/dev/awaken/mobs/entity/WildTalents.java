package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.FaunaTalent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The P2 creatures' talents that are not movement (see {@link WildGoals} for those): what a gloam snail leaves behind,
 * what a shroomlet plants, a kite's falling feather, an owl's stare, a dart frog's skin, a marmot's alarm. Called from
 * {@link FaunaMob} at most every two seconds per creature (the marmot's alarm every five ticks, like a scarab's);
 * every world change is guarded by the Hearth check, so nothing ever grows or glows inside a home.
 */
final class WildTalents {
    private WildTalents() {}

    /** Every two seconds, from {@code FaunaMob.tickTalent}. */
    static void tick(FaunaMob m, ServerLevel level) {
        switch (m.talent()) {
            case TRAIL -> trail(m, level);
            case SPORE -> plantMushroom(m, level);
            case MYCEL -> {
                if (m.lure == null && m.talentCooldown <= 0) m.lure = m.findCrop(level);
            }
            case SOAR -> shedFeather(m, level);
            case WATCH -> watch(m, level);
            default -> { }
        }
    }

    // ---- items by id (the family materials are registered by the world module) ------------------------------------

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Awaken.id(id));
    }

    // ---- gloam snail: glow lichen where it rests in the dark ------------------------------------------------------

    /** How far apart two patches of a snail's lichen are at least. */
    static final int TRAIL_SPACING = 3;

    static boolean trail(FaunaMob m, ServerLevel level) {
        if (m.talentCooldown > 0 || !m.onGround() || m.getTarget() != null) return false;
        BlockPos at = m.blockPosition();
        if (!canTrail(level, at)) return false;
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
        if (!lichen.canSurvive(level, at)) return false;
        level.setBlock(at, lichen, Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.GLOW, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.0);
        m.talentCooldown = 1200 + m.getRandom().nextInt(1200);
        return true;
    }

    /** Dark, natural ground with nothing in the way, no lichen nearby, and not inside anyone's Hearth. */
    static boolean canTrail(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at).isAir()) return false;
        BlockState below = level.getBlockState(at.below());
        if (!(below.is(BlockTags.SUBSTRATE_OVERWORLD) || below.is(BlockTags.BASE_STONE_OVERWORLD))) return false;
        if (level.getBrightness(LightLayer.BLOCK, at) >= 7) return false;
        if (level.isBrightOutside() && level.canSeeSky(at)) return false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -TRAIL_SPACING; dx <= TRAIL_SPACING; dx++) {
            for (int dz = -TRAIL_SPACING; dz <= TRAIL_SPACING; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getBlockState(p.setWithOffset(at, dx, dy, dz)).is(Blocks.GLOW_LICHEN)) return false;
                }
            }
        }
        return !HearthHooks.inside(level, at);
    }

    // ---- shroomlet: small mushrooms in the shade, a spore puff when startled ------------------------------------------

    static boolean plantMushroom(FaunaMob m, ServerLevel level) {
        if (m.talentCooldown > 0 || m.getTarget() != null) return false;
        RandomSource r = m.getRandom();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            int x = m.getBlockX() + r.nextInt(11) - 5, z = m.getBlockZ() + r.nextInt(11) - 5;
            p.set(x, m.getBlockY() + r.nextInt(3) - 1, z);
            if (!level.hasChunkAt(p) || !level.getBlockState(p).isAir()) continue;
            BlockState shroom = (r.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
            if (!shroom.canSurvive(level, p) || HearthHooks.inside(level, p)) continue;
            level.setBlock(p, shroom, Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x + 0.5, p.getY() + 0.4, z + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
            m.talentCooldown = 1200 + r.nextInt(1200);
            return true;
        }
        m.talentCooldown = 200;
        return false;
    }

    /** A shroomlet startled or struck: a cloud of spores (harmless) and a little pop. */
    static void puff(FaunaMob m, ServerLevel level) {
        if (m.puffTicks > 0) return;
        m.puffTicks = 24;
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, m.getX(), m.getY() + m.getBbHeight() * 0.8, m.getZ(), 24, 0.5, 0.3, 0.5, 0.02);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, m.getX(), m.getY() + m.getBbHeight() * 0.8, m.getZ(), 12, 0.4, 0.2, 0.4, 0.01);
        level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.NEUTRAL, 0.6F, 1.6F);
    }

    // ---- peak kite: a feather drifts down near a player ---------------------------------------------------------------

    static boolean shedFeather(FaunaMob m, ServerLevel level) {
        if (m.talentCooldown > 0) return false;
        Player p = nearestHorizontal(level, m, 40);
        if (p == null || m.getRandom().nextInt(30) != 0) return false;
        Item feather = item("crag_feather");
        m.spawnAtLocation(level, new ItemStack(feather == Items.AIR ? Items.FEATHER : feather));
        m.talentCooldown = 1200;
        return true;
    }

    private static @Nullable Player nearestHorizontal(ServerLevel level, LivingEntity near, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level.players()) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - near.getX(), dz = p.getZ() - near.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ---- hollow owl: stares down the nearest monster in the dark and makes it glow ------------------------------------

    /** How far an owl looks for monsters, and how far its player may be. */
    static final double WATCH_RANGE = 20, WATCH_PLAYER = 32;

    static @Nullable Monster watch(FaunaMob m, ServerLevel level) {
        if (m.getTarget() != null) return null;
        if (level.isBrightOutside() && level.canSeeSky(m.blockPosition())) return null;
        Player p = level.getNearestPlayer(m, WATCH_PLAYER);
        if (p == null || p.isSpectator()) return null;
        List<Monster> near = level.getEntitiesOfClass(Monster.class, new AABB(m.blockPosition()).inflate(WATCH_RANGE),
                e -> e.isAlive() && !(e instanceof FaunaMob f && f.faunaRole() != FaunaRole.HUNTER));
        Monster best = null;
        double bestD = Double.MAX_VALUE;
        for (Monster e : near) {
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return null;
        boolean fresh = !best.hasEffect(MobEffects.GLOWING);
        best.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
        m.getLookControl().setLookAt(best, 40.0F, 40.0F);
        m.alertTicks = 50;
        if (fresh) {
            level.playSound(null, m.getX(), m.getY(), m.getZ(), m.def().voice().ambient != null ? m.def().voice().ambient : SoundEvents.PARROT_AMBIENT,
                    SoundSource.NEUTRAL, 1.4F, 0.62F);
            level.sendParticles(ParticleTypes.NOTE, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 1, 0, 0, 0, 0.0);
        }
        return best;
    }

    // ---- dart frog: bare hands on venomous skin -------------------------------------------------------------------------

    static boolean venom(FaunaMob m, DamageSource source) {
        if (!(source.getEntity() instanceof Player p) || source.getDirectEntity() != p || !p.getMainHandItem().isEmpty()) return false;
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), m);
        return true;
    }

    // ---- basilisk and shroomlet: who counts as a threat ------------------------------------------------------------------

    static void noticeThreats(FaunaMob m, ServerLevel level) {
        boolean lizard = m.talent() == FaunaTalent.SPRINT;
        Player p = level.getNearestPlayer(m, lizard ? 12.0 : 7.0);
        if (p == null || p.isSpectator()) return;
        double r = lizard ? (p.isSprinting() ? 12 : p.isShiftKeyDown() ? 2.5 : 6) : (p.isSprinting() ? 6 : p.isShiftKeyDown() ? 1.5 : 3);
        if (m.distanceToSqr(p) >= r * r) return;
        m.scare(p.position(), lizard ? 70 : 40);
        if (!lizard) puff(m, level);
    }

    // ---- scree marmot: whistle, sit up, then dive ------------------------------------------------------------------------

    /** A marmot sits up and whistles at a player coming fast; one who keeps coming sees it dive into the ground. */
    static void checkAlarm(FaunaMob m, ServerLevel level) {
        Player p = level.getNearestPlayer(m, 9.0);
        if (p == null || p.isSpectator() || p.isShiftKeyDown() || !m.onGround()) return;
        double d2 = m.distanceToSqr(p);
        double alarm = p.isSprinting() ? 9 : 5;
        if (d2 >= alarm * alarm) return;
        if (m.alertTicks <= 0) {
            m.alertTicks = 30;
            m.getNavigation().stop();
            m.getLookControl().setLookAt(p, 30.0F, 30.0F);
            level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.NEUTRAL, 1.0F, 1.9F);
            return;
        }
        if (m.alertTicks < 20 && d2 < 3.5 * 3.5 && diggable(level.getBlockState(m.blockPosition().below()), true)) {
            m.burrowTicks = 1;
            m.getNavigation().stop();
            m.playSound(level.getBlockState(m.blockPosition().below()).getSoundType().getHitSound(), 0.8F, 1.2F);
        }
    }

    // ---- foraging (mudskippers, marmots) ---------------------------------------------------------------------------------

    /** Ground a forager can dig in: mud, clay, sand and dirt for a mudskipper; scree, gravel, stone and snow for a marmot. */
    static boolean diggable(BlockState below, boolean marmot) {
        if (marmot) {
            return below.is(Blocks.GRAVEL) || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(BlockTags.SUBSTRATE_OVERWORLD) || below.is(BlockTags.SNOW)
                    || below.is(Blocks.CALCITE) || below.is(Blocks.TUFF);
        }
        return below.is(BlockTags.MUD) || below.is(Blocks.CLAY) || below.is(BlockTags.SAND) || below.is(BlockTags.SUBSTRATE_OVERWORLD);
    }

    /** What a forager turns up (rolled once per dig). */
    static ItemStack find(FaunaMob m) {
        int roll = m.getRandom().nextInt(100);
        if (m.talent() == FaunaTalent.DIG) {
            if (roll < 3) return new ItemStack(Items.EMERALD);
            if (roll < 13) return new ItemStack(Items.GOLD_NUGGET, 1 + m.getRandom().nextInt(2));
            if (roll < 30) return new ItemStack(Items.COPPER_NUGGET, 2 + m.getRandom().nextInt(3));
            if (roll < 55) return new ItemStack(Items.IRON_NUGGET, 1 + m.getRandom().nextInt(3));
            return new ItemStack(Items.FLINT);
        }
        if (roll < 15) return new ItemStack(Items.LILY_PAD);
        if (roll < 40) return new ItemStack(Items.SLIME_BALL);
        return new ItemStack(Items.CLAY_BALL, 1 + m.getRandom().nextInt(2));
    }

    /** The dig itself: the find pops out of the ground in front of the creature. */
    static ItemStack dig(FaunaMob m, ServerLevel level) {
        BlockState below = level.getBlockState(m.blockPosition().below());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), m.getX(), m.getY() + 0.1, m.getZ(), 10, 0.25, 0.1, 0.25, 0.08);
        ItemStack found = find(m);
        m.spawnAtLocation(level, found.copy(), 0.3F);
        level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.BRUSH_GRAVEL, SoundSource.NEUTRAL, 0.7F, 1.2F);
        return found;
    }

    // ---- bog toad: small slimes are dinner -------------------------------------------------------------------------------

    static void gulp(FaunaMob m, ServerLevel level, LivingEntity cube) {
        m.playSound(SoundEvents.FROG_TONGUE, 1.0F, 0.8F);
        m.playSound(SoundEvents.FROG_EAT, 1.0F, 0.8F);
        level.sendParticles(ParticleTypes.ITEM_SLIME, cube.getX(), cube.getY() + 0.3, cube.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        cube.spawnAtLocation(level, new ItemStack(Items.SLIME_BALL));
        cube.discard();
    }
}
