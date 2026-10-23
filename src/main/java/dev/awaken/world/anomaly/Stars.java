package dev.awaken.world.anomaly;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Fallen stars, mostly at night. A light streaks down 32–96 blocks from the player and leaves a small crater with a
 * glowing meteor. Come within {@link #NOTICE} blocks and a guardian of your rank rises from it (an elite from D-rank up)
 * for {@link #GUARD_LIFE} ticks; the meteor stays sealed until the guardian is slain or fades back into the light. Then
 * open it: materials from its loot table, essence shards, {@code 0.2 × Curve.floorReward(rank, 1)} XP and a 10% Power
 * Core roll; slaying the guardian first makes it 0.3×, two more shards and 25%. Unopened, the star burns out after a few
 * minutes; the crater stays as a mark on the land.
 */
final class Stars {
    private Stars() {}

    static final int FALL_TICKS = 40;
    /** The light starts this far above its crater. */
    static final int HEIGHT = 80;
    /** Landed and nobody came: it burns out. */
    static final int LIFETIME = 6 * Pacing.MINUTE;
    /** The guardian rises when someone comes this close to the meteor... */
    static final int NOTICE = 16;
    /** ...and fades back into the light after this long unless slain. */
    static final int GUARD_LIFE = 90 * 20;
    /** It won't leave the meteor further than this. */
    static final int GUARD_RADIUS = 10;
    /** Once it cools, at least this long to open it. */
    static final int OPEN_GRACE = 3 * Pacing.MINUTE;
    static final double CORE_CHANCE = 0.10, CORE_CHANCE_SLAIN = 0.25;
    /** Entity tag + persistent int (the anomaly id) on a guardian, so a stray one can be recognised after its star is gone. */
    static final String GUARD_TAG = "awaken_star_guard";

    static double xpFor(Rank rank, boolean slain) {
        return (slain ? 0.3 : 0.2) * Curve.floorReward(rank, 1);
    }

    static int shardsFor(Rank rank, boolean slain, RandomSource r) {
        return 1 + rank.ordinal() + r.nextInt(2 + rank.ordinal()) + (slain ? 2 : 0);
    }

    // ---- falling ------------------------------------------------------------------------------------------------------

    static Anomaly open(ServerLevel level, ServerPlayer owner, BlockPos ground, AnomalyData data) {
        long now = level.getGameTime();
        Rank rank = Progression.rank(owner);
        Anomaly a = new Anomaly(data.allocateId(), AnomalyKind.FALLEN_STAR, owner.getUUID(), ground, now, now + FALL_TICKS + 20 + LIFETIME);
        a.theme = Rifts.pickTheme(level, ground, rank, owner.getRandom());
        a.contentLevel = Progression.contentLevel(rank);
        data.add(a);
        Vec3 to = Vec3.atCenterOf(ground).add(0, 0.6, 0);
        Vec3 from = startOf(level, to, owner.position(), owner.getRandom());
        FallingStarEntity e = AnomalyContent.FALLING_STAR.get().create(level, EntitySpawnReason.TRIGGERED);
        if (e != null) {
            e.setup(a.id, from, to, FALL_TICKS);
            level.addFreshEntity(e);
            a.entity = e.getUUID();
        }
        // a rushing whistle the player hears from where they stand
        level.playSound(null, (from.x + to.x) / 2, (from.y + to.y) / 2, (from.z + to.z) / 2, AnomalyContent.STAR_FALL.get(), SoundSource.AMBIENT, 6.0F, 1.0F);
        return a;
    }

    /** High in the sky off to one side of the line from the player, so the streak crosses their view as it falls. */
    static Vec3 startOf(ServerLevel level, Vec3 to, Vec3 player, RandomSource r) {
        double dx = to.x - player.x, dz = to.z - player.z;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        double side = r.nextBoolean() ? 1 : -1;
        double px = -dz / len * side, pz = dx / len * side;
        double y = Math.min(level.getMaxY() - 4, to.y + HEIGHT);
        return new Vec3(to.x + px * 45 - dx / len * 10, y, to.z + pz * 45 - dz / len * 10);
    }

    /** Whether this entity is the falling light of a star still on its way down. */
    static boolean owns(ServerLevel level, int anomalyId, UUID entity) {
        Anomaly a = AnomalyData.get(level.getServer()).find(anomalyId);
        return a != null && a.kind == AnomalyKind.FALLEN_STAR && !a.landed && entity.equals(a.entity);
    }

    /** The falling light arrived (or the director gave up waiting for it). */
    static void land(ServerLevel level, int anomalyId) {
        AnomalyData data = AnomalyData.get(level.getServer());
        Anomaly a = data.find(anomalyId);
        if (a != null && a.kind == AnomalyKind.FALLEN_STAR && !a.landed) impact(level, data, a);
    }

    /** The star hits: a crater, the sealed meteor in it, a boom. Returns false if it couldn't land (yet). */
    static boolean impact(ServerLevel level, AnomalyData data, Anomaly a) {
        if (!level.hasChunkAt(a.pos)) return false;
        RandomSource r = level.getRandom();
        BlockPos meteor = carve(level, a.pos, r);
        if (meteor == null) return false;
        level.setBlock(meteor, AnomalyContent.FALLEN_STAR.get().defaultBlockState().setValue(FallenStarBlock.SEALED, true), 3);
        long now = level.getGameTime();
        a.landed = true;
        a.target = meteor.immutable();
        a.endsAt = Math.max(a.endsAt, now + LIFETIME);
        if (a.entity != null && level.getEntity(a.entity) instanceof FallingStarEntity f) f.discard();
        a.entity = null;
        data.setDirty();
        double x = meteor.getX() + 0.5, y = meteor.getY() + 0.6, z = meteor.getZ() + 0.5;
        level.playSound(null, x, y, z, AnomalyContent.STAR_IMPACT.get(), SoundSource.AMBIENT, 5.0F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, x, y + 0.5, z, 3, 1.2, 0.4, 1.2, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y + 0.4, z, 30, 1.6, 0.6, 1.6, 0.03);
        level.sendParticles(ParticleTypes.FIREWORK, true, true, x, y + 0.6, z, 40, 0.6, 0.6, 0.6, 0.25);
        level.sendParticles(ParticleTypes.LAVA, true, true, x, y + 0.3, z, 12, 1.0, 0.2, 1.0, 0.0);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        if (owner != null) DirectorLog.log(owner, "anomaly", "fallen star #" + a.id + " landed at " + meteor.toShortString());
        return true;
    }

    /**
     * A shallow bowl (radius ~3, two blocks deep in the middle) dug out of natural ground and lined with scorched stone,
     * magma under the meteor. Builds and anything not plainly natural are left alone. Returns where the meteor goes, or
     * null when there is no room for it.
     */
    static @Nullable BlockPos carve(ServerLevel level, BlockPos ground, RandomSource r) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.4) continue;
                int x = ground.getX() + dx, z = ground.getZ() + dz;
                if (!level.hasChunkAt(x, z)) continue;
                BlockPos top = Spots.surface(level, x, z);
                if (Math.abs(top.getY() - ground.getY()) > 2) continue; // a tree trunk or a ledge: not ours to dig
                BlockState over = level.getBlockState(top.above());
                if (!over.isAir() && over.canBeReplaced() && over.getFluidState().isEmpty()) level.setBlock(top.above(), air, 3);
                int depth = d < 1.3 ? 2 : d < 2.4 ? 1 : 0;
                int removed = 0;
                while (removed < depth && natural(level.getBlockState(top.below(removed)))) {
                    level.setBlock(top.below(removed), air, 3);
                    removed++;
                }
                BlockPos floor = top.below(removed);
                if (natural(level.getBlockState(floor)) && (d < 2.4 || r.nextInt(3) > 0)) level.setBlock(floor, lining(d, r), 3);
            }
        }
        BlockPos at = Spots.surface(level, ground.getX(), ground.getZ()).above();
        BlockState here = level.getBlockState(at);
        if (!here.canBeReplaced() || !here.getFluidState().isEmpty()) return null;
        return at;
    }

    static boolean natural(BlockState s) {
        if (s.isAir() || s.hasBlockEntity() || s.is(Spots.AVOID) || !s.getFluidState().isEmpty()) return false;
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SNOW) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.COARSE_DIRT);
    }

    private static BlockState lining(double d, RandomSource r) {
        if (d < 0.5) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 2.4) {
            return switch (r.nextInt(4)) {
                case 0 -> Blocks.BLACKSTONE.defaultBlockState();
                case 1 -> Blocks.TUFF.defaultBlockState();
                default -> Blocks.COARSE_DIRT.defaultBlockState();
            };
        }
        return r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    // ---- while it lies there ------------------------------------------------------------------------------------------

    /** One director pass (every 10 ticks). Returns why it ended, or null while it goes on. */
    static @Nullable String update(ServerLevel level, AnomalyData data, Anomaly a, long now) {
        if (a.paid) return "opened";
        if (!a.landed) {
            // the light normally lands it; if it never got there (its chunk didn't tick), land it anyway
            if (now >= a.startedAt + FALL_TICKS + 20 && !impact(level, data, a) && level.hasChunkAt(a.pos)) return "no room for the star to land";
            return null;
        }
        if (now >= a.endsAt) return "the star burned out unopened";
        BlockPos at = a.target;
        boolean loaded = level.hasChunkAt(at);
        if (loaded && !level.getBlockState(at).is(AnomalyContent.FALLEN_STAR.get())) return "the star is gone";
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        if (owner != null && !a.discovered && owner.level() == level && owner.isAlive() && Spots.horizontal(owner.blockPosition(), at) <= Rifts.SEEN) {
            a.discovered = true;
            data.setDirty();
            AnomalyDirector.discovered(owner, a.kind);
        }
        switch (a.guardState) {
            case Anomaly.GUARD_NONE -> {
                Player near = level.getNearestPlayer(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, NOTICE, EntitySelector.NO_SPECTATORS);
                if (near instanceof ServerPlayer sp && level.isPositionEntityTicking(at)) riseGuard(level, data, a, sp, now);
            }
            case Anomaly.GUARD_UP -> {
                Entity g = a.guard == null ? null : level.getEntity(a.guard);
                if (g instanceof LivingEntity le && le.isAlive()) {
                    if (now >= a.guardUntil) {
                        fadeGuard(level, le);
                        a.guardState = Anomaly.GUARD_FADED;
                        cool(level, data, a, now);
                    }
                } else if (g == null && loaded && level.isPositionEntityTicking(at)) {
                    // it wandered off out of the loaded world, or something else took it: the star is free
                    a.guardState = Anomaly.GUARD_FADED;
                    cool(level, data, a, now);
                }
            }
            default -> {
                if (loaded && level.getBlockState(at).getValue(FallenStarBlock.SEALED)) cool(level, data, a, now);
            }
        }
        return null;
    }

    /** Someone came close: a guardian of the owner's rank rises from the light. On Peaceful the star just cools. */
    static void riseGuard(ServerLevel level, AnomalyData data, Anomaly a, ServerPlayer near, long now) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            a.guardState = Anomaly.GUARD_FADED;
            cool(level, data, a, now);
            return;
        }
        RandomSource r = level.getRandom();
        BlockPos at = Rifts.groundNear(level, a.target, 2, 4, r);
        if (at == null) at = a.target.above();
        Rank rank = rankOf(a);
        MobRole.Role role = rank.ordinal() >= Rank.D.ordinal() ? MobRole.Role.ELITE : MobRole.Role.NORMAL;
        LivingEntity mob = MobHooks.spawnThemed(level, a.theme, role, at, a.contentLevel, Set.of(), "");
        if (mob == null) {
            a.guardState = Anomaly.GUARD_FADED;
            cool(level, data, a, now);
            return;
        }
        mob.addTag(GUARD_TAG);
        mob.getPersistentData().putInt(GUARD_TAG, a.id);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GUARD_LIFE + 40, 0, false, false));
        if (mob instanceof Mob m) {
            m.setHomeTo(a.target, GUARD_RADIUS);
            if (!near.isCreative() && !near.isSpectator()) m.setTarget(near);
        }
        a.guard = mob.getUUID();
        a.guardState = Anomaly.GUARD_UP;
        a.guardUntil = now + GUARD_LIFE;
        a.endsAt = Math.max(a.endsAt, a.guardUntil + OPEN_GRACE);
        data.setDirty();
        level.playSound(null, mob.getX(), mob.getY() + 1, mob.getZ(), AnomalyContent.STAR_GUARD.get(), SoundSource.HOSTILE, 2.0F, 0.9F);
        level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 1.0, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.06);
        Signal.send(near, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The star's guardian wakes"))
                .body(Component.literal("Slay it for a richer star, or wait " + GUARD_LIFE / 20 + " s for it to fade"))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
        DirectorLog.log(near, "anomaly", "fallen star #" + a.id + ": a " + role.name().toLowerCase() + " guardian rose (" + a.theme + ", level "
                + a.contentLevel + ")");
    }

    /** The rank a star was opened at, from its content level. */
    static Rank rankOf(Anomaly a) {
        Rank best = Rank.E;
        for (Rank r : Rank.values()) if (r.contentLevel <= a.contentLevel) best = r;
        return best;
    }

    private static void fadeGuard(ServerLevel level, LivingEntity g) {
        level.sendParticles(ParticleTypes.END_ROD, g.getX(), g.getY() + 1.0, g.getZ(), 24, 0.3, 0.7, 0.3, 0.05);
        level.sendParticles(ParticleTypes.POOF, g.getX(), g.getY() + 0.6, g.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        g.discard();
    }

    /** The guardian is slain or gone: the meteor cools and may be opened. */
    static void cool(ServerLevel level, AnomalyData data, Anomaly a, long now) {
        BlockPos at = a.target;
        if (level.hasChunkAt(at)) {
            BlockState s = level.getBlockState(at);
            if (s.is(AnomalyContent.FALLEN_STAR.get()) && s.getValue(FallenStarBlock.SEALED)) {
                level.setBlock(at, s.setValue(FallenStarBlock.SEALED, false), 3);
                level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.02);
            }
        }
        a.endsAt = Math.max(a.endsAt, now + OPEN_GRACE);
        data.setDirty();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        if (owner != null && owner.level() == level && Spots.horizontal(owner.blockPosition(), at) <= Spots.MAX) {
            boolean slain = a.guardState == Anomaly.GUARD_SLAIN;
            Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(slain ? "The guardian falls" : "The star has cooled"))
                    .body(Component.literal("Open the fallen star · " + Spots.where(owner.blockPosition(), at)))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
            DirectorLog.log(owner, "anomaly", "fallen star #" + a.id + " cooled (" + (slain ? "guardian slain" : "no guardian left") + ")");
        }
    }

    // ---- opening ------------------------------------------------------------------------------------------------------

    /** Someone opened (used or broke) the cooled meteor at {@code pos}. Pays once; its loot table drops with the block. */
    static void opened(ServerLevel level, BlockPos pos, ServerPlayer p) {
        AnomalyData data = AnomalyData.get(level.getServer());
        for (Anomaly a : data.active()) {
            if (a.kind != AnomalyKind.FALLEN_STAR || !a.landed || a.paid || !a.target.equals(pos)) continue;
            a.paid = true;
            data.setDirty();
            reward(level, a, p);
            AnomalyDirector.end(level.getServer(), data, a, "opened by " + p.getGameProfile().name(), false);
            return;
        }
    }

    private static void reward(ServerLevel level, Anomaly a, ServerPlayer p) {
        Rank rank = Progression.rank(p);
        RandomSource r = p.getRandom();
        boolean slain = a.guardState == Anomaly.GUARD_SLAIN;
        AnomalyDirector.discovered(p, a.kind);
        int shards = shardsFor(rank, slain, r);
        Vec3 from = Vec3.atCenterOf(a.target).add(0, 0.5, 0);
        eject(level, from, p, new ItemStack(CoreApi.ESSENCE_SHARD.get(), shards));
        double chance = slain ? CORE_CHANCE_SLAIN : CORE_CHANCE;
        double roll = r.nextDouble();
        boolean core = false;
        if (roll < chance) {
            ItemStack c = PowerCoreHooks.roll(rank, r);
            if (!c.isEmpty()) {
                eject(level, from, p, c);
                core = true;
            }
        }
        double xp = xpFor(rank, slain);
        Progression.grantXp(p, xp, Progression.XpSource.ANOMALY);
        Progression.count(p, "awaken:stars_opened");
        p.getData(AnomalyContent.MEMORY).stars++;
        level.playSound(null, from.x, from.y, from.z, AnomalyContent.STAR_OPEN.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 30, 0.3, 0.4, 0.3, 0.08);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Star opened"))
                .body(Component.literal("+" + shards + " essence" + (core ? " · a Power Core" : "") + " · +" + Math.round(xp) + " XP"))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
        DirectorLog.log(p, "anomaly", String.format("fallen star #%d opened: %d shards, %d XP (%s × floor 1 of %s), core roll %.2f %s %.2f%s", a.id, shards,
                Math.round(xp), slain ? "0.3, guardian slain" : "0.2", rank.label, roll, roll < chance ? "<" : "≥", chance, core ? " → a Power Core" : ""));
        Playtest.log(p, "anomaly_closed", "kind", a.kind.id);
    }

    private static void eject(ServerLevel level, Vec3 from, ServerPlayer toward, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, stack);
        Vec3 dir = toward.position().subtract(from);
        double len = Math.max(0.001, dir.horizontalDistance());
        item.setDeltaMovement(dir.x / len * 0.15, 0.3, dir.z / len * 0.15);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // ---- ending -------------------------------------------------------------------------------------------------------

    /** Ended: the light goes, a guardian still standing fades, an unopened meteor comes down (now or when its chunk loads). */
    static void cleanup(ServerLevel level, AnomalyData data, Anomaly a, boolean removeBlocks) {
        if (a.entity != null && level.getEntity(a.entity) instanceof FallingStarEntity f) f.discard();
        if (a.guardState == Anomaly.GUARD_UP && a.guard != null && level.getEntity(a.guard) instanceof LivingEntity g && g.isAlive()) fadeGuard(level, g);
        if (!removeBlocks || !a.landed) return;
        BlockPos at = a.target;
        if (level.hasChunkAt(at)) {
            if (level.getBlockState(at).is(AnomalyContent.FALLEN_STAR.get())) {
                level.removeBlock(at, false);
                level.sendParticles(ParticleTypes.WHITE_ASH, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.4, 0.3, 0.4, 0.01);
            }
        } else {
            data.removals().add(new AnomalyData.Removal(level.dimension().identifier().toString(), at));
            data.setDirty();
        }
    }

    // ---- events -------------------------------------------------------------------------------------------------------

    /** A guardian died: its star cools on the next pass (blocks aren't changed inside the death event). */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!dead.entityTags().contains(GUARD_TAG) || !(dead.level() instanceof ServerLevel level)) return;
        AnomalyData data = AnomalyData.get(level.getServer());
        Anomaly a = data.find(dead.getPersistentData().getIntOr(GUARD_TAG, -1));
        if (a == null || a.kind != AnomalyKind.FALLEN_STAR || a.guardState != Anomaly.GUARD_UP || !dead.getUUID().equals(a.guard)) return;
        a.guardState = Anomaly.GUARD_SLAIN;
        data.setDirty();
        if (e.getSource().getEntity() instanceof ServerPlayer killer) {
            Progression.count(killer, "awaken:star_guardians_slain");
            DirectorLog.log(killer, "anomaly", "fallen star #" + a.id + ": guardian slain");
        }
    }

    /** A guardian whose star is gone (a restart, an unloaded chunk) never comes back into the world. */
    static void onJoin(EntityJoinLevelEvent e) {
        Entity en = e.getEntity();
        if (!en.entityTags().contains(GUARD_TAG) || !(e.getLevel() instanceof ServerLevel level)) return;
        Anomaly a = AnomalyData.get(level.getServer()).find(en.getPersistentData().getIntOr(GUARD_TAG, -1));
        if (a == null || a.kind != AnomalyKind.FALLEN_STAR || !en.getUUID().equals(a.guard)) e.setCanceled(true);
    }
}
