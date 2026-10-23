package dev.awaken.descent.spawn;

import dev.awaken.core.Forces;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.Strata;
import dev.awaken.descent.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Natural-feeling spawning around players in the Descent, budgeted and capped (SPEC: at most 60 active combat entities;
 * the Descent keeps itself to 24 per depth). Mobs come from {@link MobHooks#spawnThemed} with the stratum theme.
 */
public final class DescentSpawner {
    private DescentSpawner() {}

    public static final int FLAG_DESCENT = 1, FLAG_LISTENER = 2, FLAG_RISEN = 4, FLAG_GUARDIAN = 8, FLAG_SENTRY = 16, FLAG_FINALE = 32;
    public static final int ZONE_CAP = 24;

    public static int perPlayerCap(int depth) {
        return depth <= 4 ? 6 : depth <= 8 ? 7 : 8;
    }

    public static String theme(int depth) {
        return "awaken:stratum_" + Strata.formOf(depth);
    }

    public static boolean isOurs(LivingEntity e) {
        return (e.getExistingData(DescentContent.MOB_FLAGS.get()).orElse(0) & FLAG_DESCENT) != 0;
    }

    public static boolean has(LivingEntity e, int flag) {
        return (e.getExistingData(DescentContent.MOB_FLAGS.get()).orElse(0) & flag) != 0;
    }

    public static void flag(LivingEntity e, int flags) {
        e.setData(DescentContent.MOB_FLAGS.get(), e.getData(DescentContent.MOB_FLAGS.get()) | flags | FLAG_DESCENT);
    }

    /** Mobs of a zone, kept as references; pruned as they die or unload. */
    public static final class Tracked {
        public final List<LivingEntity> mobs = new ArrayList<>();

        public void prune() {
            mobs.removeIf(m -> m.isRemoved() || !m.isAlive());
        }

        public int near(ServerPlayer p, double r) {
            int n = 0;
            for (LivingEntity m : mobs) if (m.level() == p.level() && m.distanceToSqr(p) < r * r) n++;
            return n;
        }
    }

    /** One spawn attempt near a player. Returns the mob or null. */
    public static LivingEntity trySpawn(ServerLevel level, ServerPlayer p, int depth, int rules, Tracked tracked, boolean listener) {
        RandomSource rnd = level.getRandom();
        BlockPos camp = Layout.world(depth, Layout.waystone(Strata.formOf(depth)));
        for (int attempt = 0; attempt < 6; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = 14 + rnd.nextDouble() * 16;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            int y0 = (int) Math.floor(p.getY()) + rnd.nextInt(17) - 8;
            BlockPos spot = findFloor(level, x, y0, z);
            if (spot == null) continue;
            if (spot.distSqr(camp) < 18 * 18) continue;
            if (spot.distSqr(p.blockPosition()) < 10 * 10) continue;
            MobRole.Role role = rnd.nextInt(10) == 0 ? MobRole.Role.ELITE : MobRole.Role.NORMAL;
            LivingEntity mob = MobHooks.spawnThemed(level, theme(depth), role, spot, Strata.contentLevel(depth), Set.of(), "");
            if (mob == null) return null;
            int flags = FLAG_DESCENT;
            if (listener || Strata.has(rules, Strata.RULE_LISTENERS)) flags |= FLAG_LISTENER;
            flag(mob, flags);
            tracked.mobs.add(mob);
            return mob;
        }
        return null;
    }

    /** First standing spot at or below (x, y, z): solid floor with two air blocks above, no fluid. */
    public static BlockPos findFloor(ServerLevel level, int x, int y, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 12; i++, p.move(0, -1, 0)) {
            if (p.getY() <= level.getMinY() + 1) return null;
            BlockState here = level.getBlockState(p), below = level.getBlockState(p.below()), above = level.getBlockState(p.above());
            if (here.isAir() && above.isAir() && below.isSolid() && below.getFluidState().isEmpty()) return p.immutable();
        }
        return null;
    }

    public static LivingEntity spawnRisen(ServerLevel level, BlockPos pos, int depth, Tracked tracked) {
        LivingEntity mob = MobHooks.spawnThemed(level, theme(depth), MobRole.Role.NORMAL, pos, Strata.contentLevel(depth),
                Set.of(Forces.Tag.ECHO), "");
        if (mob == null) return null;
        Forces.tag(mob, Forces.Tag.ECHO);
        flag(mob, FLAG_RISEN);
        tracked.mobs.add(mob);
        return mob;
    }

    public static LivingEntity spawnGuardian(ServerLevel level, int depth, Tracked tracked) {
        int form = Strata.formOf(depth);
        String id = Strata.def(form).guardian();
        if (id.isEmpty()) return null;
        BlockPos at = Layout.world(depth, Layout.lair(form));
        LivingEntity boss = MobHooks.spawnBoss(level, id, at, Strata.contentLevel(depth), "");
        if (boss == null) return null;
        flag(boss, FLAG_GUARDIAN);
        if (boss instanceof Mob m) m.setPersistenceRequired();
        tracked.mobs.add(boss);
        return boss;
    }
}
