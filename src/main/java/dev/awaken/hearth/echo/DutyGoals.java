package dev.awaken.hearth.echo;

import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** The jobs Echoes do on role pads. Each is a small, budgeted goal; none needs anything from the player. */
public final class DutyGoals {
    private DutyGoals() {}

    public static void install(Mob mob, EchoRole role, BlockPos pad, HearthRecord h) {
        EchoGoals.install(mob, true);
        UUID owner = h.owner;
        mob.setHomeTo(h.pos, h.radius());
        // Carriers start with empty hands: whatever they hold is cargo, not a weapon.
        if (role == EchoRole.HAUL || role == EchoRole.FARM) mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        switch (role) {
            case GUARD -> {
                mob.goalSelector.addGoal(5, new Patrol(mob, owner, pad));
                mob.targetSelector.addGoal(1, new GuardHunt(mob, owner, h));
            }
            case HAUL -> mob.goalSelector.addGoal(4, new Haul(mob, owner, pad));
            case FARM -> mob.goalSelector.addGoal(4, new Farm(mob, owner, pad));
            case TRAIN -> mob.goalSelector.addGoal(6, new Spar(mob));
            case MINE -> {}
        }
        mob.goalSelector.addGoal(7, new ReturnToPad(mob, pad));
    }

    static @Nullable HearthRecord hearth(Mob mob, UUID owner) {
        if (!(mob.level() instanceof ServerLevel sl)) return null;
        return HearthService.data(sl.getServer()).of(owner).orElse(null);
    }

    /** Idle Echoes stand on their pad. */
    static final class ReturnToPad extends Goal {
        private final Mob mob;
        private final BlockPos pad;

        ReturnToPad(Mob mob, BlockPos pad) {
            this.mob = mob;
            this.pad = pad;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null && mob.distanceToSqr(Vec3.atBottomCenterOf(pad)) > 2.5;
        }

        @Override
        public void start() {
            if (mob.distanceToSqr(Vec3.atBottomCenterOf(pad)) > 96 * 96) {
                Vec3 v = Vec3.atBottomCenterOf(pad);
                mob.snapTo(v.x, v.y + 0.1, v.z, mob.getYRot(), 0f);
                return;
            }
            mob.getNavigation().moveTo(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && !mob.getNavigation().isDone();
        }
    }

    /** Guards walk the bounds of home now and then. */
    static final class Patrol extends Goal {
        private final Mob mob;
        private final UUID owner;
        private final BlockPos pad;
        private int wait = 100;

        Patrol(Mob mob, UUID owner, BlockPos pad) {
            this.mob = mob;
            this.owner = owner;
            this.pad = pad;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || --wait > 0) return false;
            wait = 200 + mob.getRandom().nextInt(200);
            return true;
        }

        @Override
        public void start() {
            HearthRecord h = hearth(mob, owner);
            if (h == null) return;
            BlockPos defend = Duty.defendPos(owner);
            if (defend != null) {
                // A break nearby: go and hold it. Step through the shadows when it's far.
                if (mob.distanceToSqr(Vec3.atCenterOf(defend)) > 40 * 40 && mob.level().isLoaded(defend)) {
                    int y = mob.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, defend.getX() + 3, defend.getZ() + 3);
                    mob.snapTo(defend.getX() + 3.5, y, defend.getZ() + 3.5, mob.getYRot(), 0f);
                } else {
                    mob.getNavigation().moveTo(defend.getX() + 0.5, defend.getY(), defend.getZ() + 0.5, 1.2);
                }
                return;
            }
            int r = Math.max(8, h.radius() / 2);
            int x = h.pos.getX() + mob.getRandom().nextInt(2 * r + 1) - r, z = h.pos.getZ() + mob.getRandom().nextInt(2 * r + 1) - r;
            mob.getNavigation().moveTo(x + 0.5, h.pos.getY(), z + 0.5, 0.9);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && !mob.getNavigation().isDone();
        }
    }

    /** Guards pick monsters anywhere inside the Hearth, or around the break they went to hold. */
    static final class GuardHunt extends Goal {
        private final Mob mob;
        private final UUID owner;
        private final HearthRecord h;
        private int wait;

        GuardHunt(Mob mob, UUID owner, HearthRecord h) {
            this.mob = mob;
            this.owner = owner;
            this.h = h;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || --wait > 0) return false;
            wait = 10;
            BlockPos defend = Duty.defendPos(owner);
            Vec3 c = defend != null && mob.distanceToSqr(Vec3.atCenterOf(defend)) < 48 * 48 ? Vec3.atCenterOf(defend) : mob.position();
            double r = 16;
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                    e -> e instanceof Enemy && e.isAlive() && !Echoes.isEcho(e))) {
                if (defend == null && !h.contains(e.blockPosition())) continue;
                if (!Echoes.canTarget(mob, e)) continue;
                double d = mob.distanceToSqr(e);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) mob.setTarget(best);
            return false;
        }
    }

    /** Haulers pick up loose items inside the Hearth and put them in the chest that already holds that item. */
    static final class Haul extends Goal {
        private final Mob mob;
        private final UUID owner;
        private final BlockPos pad;
        private @Nullable ItemEntity item;
        private @Nullable BlockPos chest;
        private int wait, timeout;

        Haul(Mob mob, UUID owner, BlockPos pad) {
            this.mob = mob;
            this.owner = owner;
            this.pad = pad;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null) return false;
            if (!mob.getMainHandItem().isEmpty()) return true; // still carrying something
            if (--wait > 0 || !(mob.level() instanceof ServerLevel level)) return false;
            wait = 40;
            HearthRecord h = hearth(mob, owner);
            if (h == null || Duty.containers(level, h).isEmpty()) return false;
            ItemEntity best = null;
            double bestD = 24 * 24;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pad).inflate(24, 6, 24),
                    e -> e.isAlive() && !e.hasPickUpDelay() && e.getAge() > 100 && h.contains(e.blockPosition()))) {
                double d = mob.distanceToSqr(e);
                if (d < bestD && Duty.chooseContainer(level, h, e.getItem(), pad) != null) {
                    bestD = d;
                    best = e;
                }
            }
            item = best;
            return best != null;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && timeout > 0 && (item != null || chest != null || !mob.getMainHandItem().isEmpty());
        }

        @Override
        public void start() {
            timeout = 400;
            if (item != null) mob.getNavigation().moveTo(item, 1.1);
        }

        @Override
        public void tick() {
            timeout--;
            if (!(mob.level() instanceof ServerLevel level)) return;
            HearthRecord h = hearth(mob, owner);
            if (h == null) return;
            if (item != null) {
                if (!item.isAlive()) {
                    item = null;
                    return;
                }
                if (mob.distanceToSqr(item) < 2.25) {
                    mob.setItemSlot(EquipmentSlot.MAINHAND, item.getItem().copy());
                    item.discard();
                    item = null;
                    chest = Duty.chooseContainer(level, h, mob.getMainHandItem(), pad);
                    if (chest != null) mob.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.1);
                } else if (mob.getNavigation().isDone()) {
                    mob.getNavigation().moveTo(item, 1.1);
                }
                return;
            }
            ItemStack carried = mob.getMainHandItem();
            if (carried.isEmpty()) return;
            if (chest == null) chest = Duty.chooseContainer(level, h, carried, pad);
            BlockPos target = chest != null ? chest : pad;
            if (mob.distanceToSqr(Vec3.atCenterOf(target)) < 6.5 || timeout <= 1) {
                mob.swingForAttack(InteractionHand.MAIN_HAND);
                h.itemsHauled += carried.getCount();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                Duty.store(level, h, carried, target);
                chest = null;
            } else if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.1);
            }
        }

        @Override
        public void stop() {
            item = null;
            mob.getNavigation().stop();
        }
    }

    /** Farmers harvest ripe crops near their pad, replant them, and put the harvest away. */
    static final class Farm extends Goal {
        private static final int RANGE = 10;
        private final Mob mob;
        private final UUID owner;
        private final BlockPos pad;
        private @Nullable BlockPos crop;
        private final List<ItemStack> carried = new ArrayList<>();
        private int wait, timeout;

        Farm(Mob mob, UUID owner, BlockPos pad) {
            this.mob = mob;
            this.owner = owner;
            this.pad = pad;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null) return false;
            if (!carried.isEmpty()) return true;
            if (--wait > 0) return false;
            wait = 40;
            crop = findRipe();
            return crop != null;
        }

        private @Nullable BlockPos findRipe() {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -RANGE; dx <= RANGE; dx++) {
                    for (int dz = -RANGE; dz <= RANGE; dz++) {
                        m.set(pad.getX() + dx, pad.getY() + dy, pad.getZ() + dz);
                        BlockState s = mob.level().getBlockState(m);
                        if (s.getBlock() instanceof CropBlock c && c.isMaxAge(s)) {
                            double d = mob.distanceToSqr(Vec3.atCenterOf(m));
                            if (d < bestD) {
                                bestD = d;
                                best = m.immutable();
                            }
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && timeout > 0 && (crop != null || !carried.isEmpty());
        }

        @Override
        public void start() {
            timeout = 400;
            if (crop != null) mob.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 1.0);
        }

        @Override
        public void tick() {
            timeout--;
            if (!(mob.level() instanceof ServerLevel level)) return;
            HearthRecord h = hearth(mob, owner);
            if (h == null) return;
            if (crop != null) {
                BlockState s = level.getBlockState(crop);
                if (!(s.getBlock() instanceof CropBlock c) || !c.isMaxAge(s)) {
                    crop = null;
                    return;
                }
                if (mob.distanceToSqr(Vec3.atCenterOf(crop)) < 4.5) {
                    mob.swingForAttack(InteractionHand.MAIN_HAND);
                    List<ItemStack> drops = Block.getDrops(s, level, crop, null);
                    level.setBlock(crop, c.getStateForAge(0), Block.UPDATE_ALL);
                    level.levelEvent(2001, crop, Block.getId(s));
                    boolean seedTaken = false;
                    for (ItemStack d : drops) {
                        if (!seedTaken && d.is(s.getBlock().asItem())) {
                            d.shrink(1);
                            seedTaken = true;
                        }
                        if (!d.isEmpty()) carried.add(d);
                    }
                    h.cropsHarvested++;
                    crop = findRipe();
                    if (crop != null && carried.size() < 6) {
                        mob.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 1.0);
                    } else {
                        crop = null;
                    }
                    if (!carried.isEmpty()) mob.setItemSlot(EquipmentSlot.MAINHAND, carried.get(0).copy());
                } else if (mob.getNavigation().isDone()) {
                    mob.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 1.0);
                }
                return;
            }
            if (carried.isEmpty()) return;
            BlockPos chest = Duty.chooseContainer(level, h, carried.get(0), pad);
            BlockPos target = chest != null ? chest : pad;
            if (mob.distanceToSqr(Vec3.atCenterOf(target)) < 6.5 || timeout <= 1) {
                for (ItemStack d : carried) Duty.store(level, h, d, target);
                carried.clear();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
            if (timeout <= 0 && !carried.isEmpty() && mob.level() instanceof ServerLevel level) {
                HearthRecord h = hearth(mob, owner);
                if (h != null) for (ItemStack d : carried) Duty.store(level, h, d, pad);
                carried.clear();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    /** Training Echoes spar with the air on their pad. */
    static final class Spar extends Goal {
        private final Mob mob;
        private int t;

        Spar(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override public boolean canUse() { return mob.getTarget() == null; }
        @Override public boolean canContinueToUse() { return mob.getTarget() == null; }

        @Override
        public void tick() {
            if (++t % 25 == 0) {
                mob.setYRot(mob.getYRot() + 90f);
                mob.setYHeadRot(mob.getYRot());
                mob.swingForAttack(InteractionHand.MAIN_HAND);
            }
        }
    }
}
