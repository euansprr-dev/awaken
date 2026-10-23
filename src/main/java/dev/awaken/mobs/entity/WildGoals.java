package dev.awaken.mobs.entity;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.mobs.def.FaunaTalent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Movement goals of the P2 creatures (see {@link FaunaGoals} for P1's). Same budget: fliers fly straight lines (a new
 * point every half second or so), walkers repath at most every ten ticks, and the few lookups (a poisoned player, a
 * small slime, a bee nest through the POI index) run every one to five seconds while the creature is idle.
 */
final class WildGoals {
    private WildGoals() {}

    /** Talents whose goal moves a flier itself (no generic wandering on top). */
    static boolean ownsFlight(FaunaTalent t) {
        return t == FaunaTalent.CLEANSE || t == FaunaTalent.POLLINATE || t == FaunaTalent.SOAR;
    }

    /** The talent's goals, added by {@link FaunaMob#registerGoals}. */
    static void add(FaunaMob mob, FaunaTalent t) {
        switch (t) {
            case CLEANSE -> mob.goal(4, new Cleanse(mob));
            case FORAGE, DIG -> mob.goal(4, new Forage(mob));
            case GULP -> mob.goal(4, new Gulp(mob));
            case POLLINATE -> mob.goal(4, new Pollinate(mob));
            case HONEY -> mob.goal(4, new Honey(mob));
            case SOAR -> mob.goal(5, new Soar(mob));
            case MYCEL -> {
                mob.goal(4, new TendWalk(mob));
                mob.goal(5, new FaunaGoals.Graze(mob));
            }
            case TRAIL, SPORE -> mob.goal(5, new FaunaGoals.Graze(mob));
            default -> { }
        }
    }

    /** Drift low over the ground in short straight hops (moths, bogwicks, petalwings with nothing to do). */
    static void drift(FaunaMob mob, double speed) {
        RandomSource r = mob.getRandom();
        double x = mob.getX() + (r.nextFloat() * 2 - 1) * 5;
        double z = mob.getZ() + (r.nextFloat() * 2 - 1) * 5;
        double y = FaunaMob.groundAt(mob.level(), x, z) + 1.2 + r.nextFloat() * 1.8;
        mob.getMoveControl().setWantedPosition(x, Math.min(y, mob.getY() + 2.5), z, speed);
    }

    // ---- bogwick ---------------------------------------------------------------------------------------------------

    /** Bogwicks: drawn to a poisoned player (they draw the poison out), otherwise they drift low over the bog. */
    static final class Cleanse extends Goal {
        private final FaunaMob mob;
        private int repath, check;
        private float angle;
        private @Nullable Player ward;

        Cleanse(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 10 + mob.getRandom().nextInt(6);
            angle += 1.2F + mob.getRandom().nextFloat() * 0.6F;
            ServerLevel level = getServerLevel(mob);
            if (--check <= 0) {
                check = 2;
                ward = poisoned(level, mob, 16.0);
            }
            Player w = ward;
            if (w != null && w.isAlive() && w.hasEffect(MobEffects.POISON)) {
                double r = 1.1 + mob.getRandom().nextFloat() * 0.5;
                mob.getMoveControl().setWantedPosition(w.getX() + Mth.cos(angle) * r, w.getEyeY() - 0.2 + Mth.sin(angle * 1.7F) * 0.3,
                        w.getZ() + Mth.sin(angle) * r, 0.9);
                if (mob.distanceToSqr(w) < 3.5 * 3.5) WildGoals.cleanse(mob, level, w);
                return;
            }
            drift(mob, 0.5);
        }
    }

    static @Nullable Player poisoned(ServerLevel level, LivingEntity near, double range) {
        Player best = null;
        double bestD = range * range;
        for (Player p : level.players()) {
            if (p.isSpectator() || !p.hasEffect(MobEffects.POISON)) continue;
            double d = p.distanceToSqr(near);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** Draws the poison out of a player (a bogwick needs ten seconds of rest between two cures). */
    static boolean cleanse(FaunaMob mob, ServerLevel level, Player p) {
        if (mob.talentCooldown > 0 || !p.hasEffect(MobEffects.POISON)) return false;
        p.removeEffect(MobEffects.POISON);
        mob.talentCooldown = 200;
        mob.alertTicks = 30;
        level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.35, 0.5, 0.35, 0.02);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.8F);
        return true;
    }

    // ---- mudskipper, marmot ----------------------------------------------------------------------------------------

    /** Stop, dig for two seconds, and a find pops out (only while a player is around to see it). */
    static final class Forage extends Goal {
        private final FaunaMob mob;
        private int dig;

        Forage(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (mob.talentCooldown > 0 || !mob.onGround() || mob.getTarget() != null || mob.scareTicks > 0 || mob.burrowing() || mob.alertTicks > 0) return false;
            if (mob.getRandom().nextInt(reducedTickDelay(100)) != 0) return false;
            ServerLevel level = getServerLevel(mob);
            BlockState below = level.getBlockState(mob.blockPosition().below());
            return WildTalents.diggable(below, mob.talent() == FaunaTalent.DIG) && level.getNearestPlayer(mob, 24.0) != null;
        }

        @Override public boolean canContinueToUse() { return dig > 0 && mob.getTarget() == null && mob.scareTicks <= 0 && !mob.burrowing(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            dig = 40;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.graze = Math.max(mob.graze, 2);
            ServerLevel level = getServerLevel(mob);
            if (dig % 8 == 0) {
                BlockState below = level.getBlockState(mob.blockPosition().below());
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), mob.getX(), mob.getY() + 0.1, mob.getZ(),
                        3, 0.2, 0.05, 0.2, 0.05);
            }
            if (--dig == 0) {
                WildTalents.dig(mob, level);
                mob.talentCooldown = 900 + mob.getRandom().nextInt(900);
            }
        }

        @Override
        public void stop() {
            if (dig > 0) mob.talentCooldown = 200;
            dig = 0;
        }
    }

    // ---- bog toad ---------------------------------------------------------------------------------------------------

    /** Bog toads hunt small slimes: hop close, the tongue flicks, the slime is gone and its slime ball drops. */
    static final class Gulp extends Goal {
        private final FaunaMob mob;
        private @Nullable LivingEntity prey;
        private int check, tongue, repath;

        Gulp(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || mob.scareTicks > 0) return false;
            if (--check > 0) return false;
            check = reducedTickDelay(40);
            prey = smallCube(getServerLevel(mob), mob, 8.0);
            return prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity p = prey;
            return p != null && p.isAlive() && mob.distanceToSqr(p) < 12 * 12 && mob.getTarget() == null && mob.scareTicks <= 0;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            tongue = 0;
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity p = prey;
            if (p == null) return;
            mob.getLookControl().setLookAt(p, 30.0F, 30.0F);
            if (mob.distanceToSqr(p) > 3.2 * 3.2 && tongue == 0) {
                if (--repath <= 0) {
                    repath = 10;
                    mob.getNavigation().moveTo(p, 1.1);
                }
                return;
            }
            mob.getNavigation().stop();
            if (tongue++ == 0) mob.setTelegraph(8, false);
            if (tongue >= 8) {
                WildTalents.gulp(mob, getServerLevel(mob), p);
                prey = null;
            }
        }

        @Override
        public void stop() {
            prey = null;
            tongue = 0;
        }
    }

    static @Nullable AbstractCubeMob smallCube(ServerLevel level, LivingEntity near, double range) {
        List<AbstractCubeMob> cubes = level.getEntitiesOfClass(AbstractCubeMob.class, new AABB(near.blockPosition()).inflate(range),
                c -> c.isAlive() && c.getSize() <= 1);
        AbstractCubeMob best = null;
        double bestD = Double.MAX_VALUE;
        for (AbstractCubeMob c : cubes) {
            double d = c.distanceToSqr(near);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    // ---- petalwing ---------------------------------------------------------------------------------------------------

    /** Petalwings: find open grass, hover over it for two seconds, and a flower opens there. */
    static final class Pollinate extends Goal {
        private final FaunaMob mob;
        private int repath, search, hover;

        Pollinate(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 8;
            ServerLevel level = getServerLevel(mob);
            if (mob.lure == null && mob.talentCooldown <= 0 && --search <= 0) {
                search = 5;
                mob.lure = lawn(level, mob);
                hover = 0;
            }
            BlockPos at = mob.lure;
            if (at == null) {
                drift(mob, 0.45);
                return;
            }
            double cx = at.getX() + 0.5, cy = at.getY() + 0.9, cz = at.getZ() + 0.5;
            mob.getMoveControl().setWantedPosition(cx, cy + Mth.sin(mob.tickCount * 0.3F) * 0.15, cz, 0.5);
            if (mob.distanceToSqr(cx, cy, cz) < 1.6 * 1.6) {
                hover += 8;
                if (hover % 16 == 0) level.sendParticles(ParticleTypes.CHERRY_LEAVES, cx, cy, cz, 2, 0.25, 0.1, 0.25, 0.0);
                if (hover >= 40) {
                    bloom(level, mob, at);
                    mob.lure = null;
                }
            } else if (mob.distanceToSqr(cx, cy, cz) > 20 * 20) {
                mob.lure = null;
            }
        }
    }

    private static final Block[] FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.ALLIUM,
            Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.LILY_OF_THE_VALLEY};

    /** Open grass with air above, not inside a Hearth (a few random looks). */
    static @Nullable BlockPos lawn(ServerLevel level, FaunaMob mob) {
        RandomSource r = mob.getRandom();
        for (int i = 0; i < 10; i++) {
            int x = mob.getBlockX() + r.nextInt(17) - 8, z = mob.getBlockZ() + r.nextInt(17) - 8;
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.hasChunkAt(top)) continue;
            if (level.getBlockState(top.below()).is(Blocks.GRASS_BLOCK) && level.getBlockState(top).isAir() && !HearthHooks.inside(level, top)) return top;
        }
        return null;
    }

    /** A flower opens where the petalwing hovered (if the spot is still open grass). */
    static boolean bloom(ServerLevel level, FaunaMob mob, BlockPos at) {
        mob.talentCooldown = 1200 + mob.getRandom().nextInt(600);
        if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) || HearthHooks.inside(level, at)) return false;
        BlockState flower = FLOWERS[mob.getRandom().nextInt(FLOWERS.length)].defaultBlockState();
        if (!flower.canSurvive(level, at)) return false;
        level.setBlock(at, flower, Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.4F, 2.0F);
        return true;
    }

    // ---- honey bear --------------------------------------------------------------------------------------------------

    /** Honey bears amble to a bee nest they know of (the POI index, never a block scan) and shake it. */
    static final class Honey extends Goal {
        private final FaunaMob mob;
        private @Nullable BlockPos nest;
        private int check, shake, repath, patience;

        Honey(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.talentCooldown > 0 || mob.getTarget() != null || mob.scareTicks > 0 || !mob.onGround()) return false;
            if (--check > 0) return false;
            check = reducedTickDelay(100);
            nest = nest(getServerLevel(mob), mob.blockPosition(), 24);
            if (nest == null) mob.talentCooldown = 600;
            return nest != null;
        }

        @Override public boolean canContinueToUse() { return nest != null && shake < 40 && patience > 0 && mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            shake = 0;
            repath = 0;
            patience = 600;
        }

        @Override
        public void tick() {
            BlockPos n = nest;
            if (n == null) return;
            patience--;
            double dx = n.getX() + 0.5 - mob.getX(), dz = n.getZ() + 0.5 - mob.getZ();
            if (dx * dx + dz * dz > 2.5 * 2.5 && shake == 0) {
                if (--repath <= 0) {
                    repath = 20;
                    int ground = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, n.getX(), n.getZ());
                    mob.getNavigation().moveTo(n.getX() + 0.5, Math.min(ground, n.getY()), n.getZ() + 0.5, 0.9);
                }
                return;
            }
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(Vec3.atCenterOf(n));
            mob.setTelegraph(20, false); // the bear rears up on its hind legs
            if (++shake == 30) WildGoals.shake(getServerLevel(mob), mob, n);
        }

        @Override
        public void stop() {
            if (shake < 30) mob.talentCooldown = Math.max(mob.talentCooldown, 400);
            nest = null;
            shake = 0;
            mob.setTelegraph(0, false);
        }
    }

    static @Nullable BlockPos nest(ServerLevel level, BlockPos near, int radius) {
        return level.getPoiManager().findClosest(h -> h.is(PoiTypeTags.BEE_HOME), near, radius, PoiManager.Occupancy.ANY).orElse(null);
    }

    /** The bear shakes a nest: a full one drops its honeycomb (and is emptied), any one rains a little honey. */
    static int shake(ServerLevel level, FaunaMob mob, BlockPos nest) {
        mob.talentCooldown = 2400 + mob.getRandom().nextInt(1200);
        BlockState s = level.getBlockState(nest);
        level.sendParticles(ParticleTypes.FALLING_HONEY, nest.getX() + 0.5, nest.getY(), nest.getZ() + 0.5, 12, 0.4, 0.2, 0.4, 0.0);
        level.playSound(null, nest, SoundEvents.BEEHIVE_SHEAR, SoundSource.NEUTRAL, 0.8F, 0.8F);
        if (!(s.getBlock() instanceof BeehiveBlock hive) || s.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) return 0;
        Block.popResource(level, nest.below(), new ItemStack(Items.HONEYCOMB, 2));
        hive.resetHoneyLevel(level, s, nest);
        return 2;
    }

    // ---- peak kite ---------------------------------------------------------------------------------------------------

    /** Peak kites ride the updrafts: wide loops a dozen blocks over the ground, the loop's centre drifting slowly. */
    static final class Soar extends Goal {
        private final FaunaMob mob;
        private float angle;
        private double cx, cz;
        private int repath;
        private boolean centred;

        Soar(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void start() {
            if (!centred) {
                cx = mob.getX();
                cz = mob.getZ();
                centred = true;
            }
        }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 10;
            angle += 0.3F;
            cx += Mth.cos(angle * 0.05F) * 0.5;
            cz += Mth.sin(angle * 0.04F) * 0.5;
            float r = 10.0F + Mth.sin(angle * 0.2F) * 3.0F;
            double x = cx + Mth.cos(angle) * r, z = cz + Mth.sin(angle) * r;
            double y = FaunaMob.groundAt(mob.level(), x, z) + 13 + Mth.sin(angle * 0.5F) * 3;
            mob.getMoveControl().setWantedPosition(x, y, z, 0.8);
        }
    }

    // ---- sporeback snail ------------------------------------------------------------------------------------------------

    /** Sporeback snails crawl to a small mushroom, rest beside it for three seconds, and it swells into a giant. */
    static final class TendWalk extends Goal {
        private final FaunaMob mob;
        private int rest, repath;

        TendWalk(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.lure != null && mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            rest = 0;
            repath = 0;
        }

        @Override
        public void tick() {
            BlockPos at = mob.lure;
            if (at == null) return;
            double d2 = mob.distanceToSqr(Vec3.atBottomCenterOf(at));
            if (d2 > 20 * 20) {
                mob.lure = null;
                return;
            }
            if (d2 > 1.8 * 1.8 && rest == 0) {
                if (--repath <= 0) {
                    repath = 20;
                    if (!mob.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0)) mob.lure = null;
                }
                return;
            }
            mob.getNavigation().stop();
            mob.graze = Math.max(mob.graze, 2);
            ServerLevel level = getServerLevel(mob);
            if (rest % 15 == 0) level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
            if (++rest >= 60) {
                if (!HearthHooks.inside(level, at)) mob.tend(level, at);
                else mob.talentCooldown = 1200;
                mob.lure = null;
            }
        }
    }
}
