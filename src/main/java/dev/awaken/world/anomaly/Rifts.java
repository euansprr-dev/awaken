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
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Rift tears. A crack opens 32–96 blocks from the player and spills monsters of the player's rank (a small group at
 * once, then a wave every 30 seconds while someone is within {@link #ENGAGE} blocks) for about two minutes. Breaking its
 * core takes a few hits and pays once: essence shards, {@code 0.3 × Curve.floorReward(rank, 1)} XP (what
 * tools/sim/pacing_sim.py assumes) and a Power Core chance. Ignored, it seals itself with nothing lost; the monsters it
 * let out are ordinary ones (no gate id) and despawn the vanilla way.
 */
final class Rifts {
    private Rifts() {}

    static final int LIFETIME = 2 * Pacing.MINUTE;
    static final int WAVE_EVERY = 30 * 20;
    /** At most one hit counts every this many ticks, so a burst power can't break the core in one go. */
    static final int HIT_COOLDOWN = 8;
    /** Waves only spill while a player is this close; nobody near, nothing piles up. */
    static final int ENGAGE = 48;
    /** Close enough to count as having seen it (the first-time discovery). */
    static final int SEEN = 24;
    /** Spilled monsters guard the tear: they won't chase further than this from it. */
    static final int GUARD_RADIUS = 16;
    static final double CORE_CHANCE = 0.12;
    static final String MOB_TAG = "awaken_anomaly";

    static int maxHits(Rank rank) {
        return 4 + Math.min(4, rank.ordinal());
    }

    static int cap(Rank rank, boolean night) {
        return 4 + Math.min(3, rank.ordinal()) + (night ? 1 : 0);
    }

    static double xpFor(Rank rank) {
        return 0.3 * Curve.floorReward(rank, 1);
    }

    static int shardsFor(Rank rank, RandomSource r) {
        return 2 + rank.ordinal() + r.nextInt(2 + rank.ordinal());
    }

    // ---- opening ------------------------------------------------------------------------------------------------------

    static Anomaly open(ServerLevel level, ServerPlayer owner, BlockPos ground, AnomalyData data, boolean first) {
        long now = level.getGameTime();
        Rank rank = Progression.rank(owner);
        Anomaly a = new Anomaly(data.allocateId(), AnomalyKind.RIFT_TEAR, owner.getUUID(), ground, now, now + LIFETIME);
        a.theme = pickTheme(level, ground, rank, owner.getRandom());
        a.contentLevel = Progression.contentLevel(rank);
        a.maxHits = maxHits(rank);
        a.hitsLeft = a.maxHits;
        a.nextWaveAt = now + WAVE_EVERY;
        data.add(a);
        spawnCore(level, a);
        boolean night = level.isDarkOutside();
        int group = first ? 2 : 2 + (night ? 1 : 0) + (rank.ordinal() >= Rank.C.ordinal() ? 1 : 0);
        spill(level, a, group, owner, MobRole.Role.NORMAL);
        BlockPos core = a.focus();
        level.playSound(null, core.getX() + 0.5, core.getY() + 1.0, core.getZ() + 0.5, AnomalyContent.RIFT_OPEN.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, core.getX() + 0.5, core.getY() + 1.5, core.getZ() + 0.5, 60, 0.4, 1.4, 0.4, 0.15);
        return a;
    }

    static @Nullable RiftEntity spawnCore(ServerLevel level, Anomaly a) {
        RiftEntity e = AnomalyContent.RIFT.get().create(level, EntitySpawnReason.TRIGGERED);
        if (e == null) return null;
        BlockPos core = a.focus();
        e.snapTo(core.getX() + 0.5, core.getY() + 0.25, core.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        e.setup(a.id, a.maxHits, a.hitsLeft);
        level.addFreshEntity(e);
        a.entity = e.getUUID();
        return e;
    }

    static @Nullable RiftEntity entity(ServerLevel level, Anomaly a) {
        if (a.entity == null) return null;
        return level.getEntity(a.entity) instanceof RiftEntity r && !r.isRemoved() ? r : null;
    }

    /** Whether this entity is the live core of an open rift (anything else discards itself). */
    static boolean owns(ServerLevel level, int anomalyId, UUID entity) {
        Anomaly a = AnomalyData.get(level.getServer()).find(anomalyId);
        return a != null && a.kind == AnomalyKind.RIFT_TEAR && !a.paid && entity.equals(a.entity);
    }

    // ---- while open ---------------------------------------------------------------------------------------------------

    /** One director pass (every 10 ticks). Returns why it ended, or null while it stays open. */
    static @Nullable String update(ServerLevel level, AnomalyData data, Anomaly a, long now) {
        if (a.paid) return "closed";
        if (now >= a.endsAt) return "sealed itself after its time (nothing lost)";
        BlockPos core = a.focus();
        boolean ticking = level.isPositionEntityTicking(core);
        if (ticking && entity(level, a) == null) {
            spawnCore(level, a); // its chunk was unloaded and came back
            data.setDirty();
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(a.owner);
        if (owner != null && !a.discovered && owner.level() == level && owner.isAlive()
                && Spots.horizontal(owner.blockPosition(), core) <= SEEN) {
            a.discovered = true;
            data.setDirty();
            AnomalyDirector.discovered(owner, a.kind);
        }
        if (now >= a.nextWaveAt) {
            a.nextWaveAt = now + WAVE_EVERY;
            data.setDirty();
            Player near = level.getNearestPlayer(core.getX() + 0.5, core.getY() + 1, core.getZ() + 0.5, ENGAGE, EntitySelector.NO_SPECTATORS);
            if (ticking && near instanceof ServerPlayer target) {
                prune(level, a);
                boolean night = level.isDarkOutside();
                int room = cap(Rank.values()[Math.clamp(rankOrdinal(a), 0, Rank.values().length - 1)], night) - a.mobs.size();
                RandomSource r = level.getRandom();
                int wave = 1 + (r.nextBoolean() ? 1 : 0) + (night && r.nextBoolean() ? 1 : 0);
                if (room > 0) spill(level, a, Math.min(room, wave), target, MobRole.Role.NORMAL);
            }
        }
        return null;
    }

    /** The rank a rift was opened at, from its content level. */
    private static int rankOrdinal(Anomaly a) {
        int best = 0;
        for (Rank r : Rank.values()) if (r.contentLevel <= a.contentLevel) best = r.ordinal();
        return best;
    }

    /** A strike on the core (from {@link RiftEntity#hurtServer}): the guards turn on the striker; at zero it closes. */
    static void strike(ServerLevel level, RiftEntity e, ServerPlayer by, int left) {
        AnomalyData data = AnomalyData.get(level.getServer());
        Anomaly a = data.find(e.anomalyId);
        float pitch = 0.9F + 0.5F * (1.0F - left / (float) Math.max(1, e.maxHits()));
        level.playSound(null, e.getX(), e.getY() + 0.8, e.getZ(), AnomalyContent.RIFT_HIT.get(), SoundSource.HOSTILE, 1.4F, pitch);
        level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 0.8, e.getZ(), 12, 0.3, 0.4, 0.3, 0.3);
        if (a == null || a.kind != AnomalyKind.RIFT_TEAR || a.paid) {
            e.beginClose();
            return;
        }
        a.hitsLeft = left;
        data.setDirty();
        alert(level, a, by);
        // halfway, the tear pushes back once: an elite from D-rank up, another normal at E
        if (!a.guardSpawned && left > 0 && left <= a.maxHits / 2) {
            a.guardSpawned = true;
            spill(level, a, 1, by, rankOrdinal(a) >= Rank.D.ordinal() ? MobRole.Role.ELITE : MobRole.Role.NORMAL);
            DirectorLog.log(by, "anomaly", "rift #" + a.id + " at half: one guard pushed out");
        }
        if (left <= 0) close(level, data, a, by);
    }

    /** Closes the rift for {@code by} and pays, once. Returns true if this call paid. */
    static boolean close(ServerLevel level, AnomalyData data, Anomaly a, ServerPlayer by) {
        if (a.paid) return false;
        a.paid = true;
        a.hitsLeft = 0;
        data.setDirty();
        RiftEntity e = entity(level, a);
        if (e != null) e.beginClose();
        BlockPos core = a.focus();
        level.playSound(null, core.getX() + 0.5, core.getY() + 1.0, core.getZ() + 0.5, AnomalyContent.RIFT_CLOSE.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, true, true, core.getX() + 0.5, core.getY() + 1.4, core.getZ() + 0.5, 40, 0.3, 1.2, 0.3, 0.12);
        reward(level, a, by);
        AnomalyDirector.end(level.getServer(), data, a, "closed by " + by.getGameProfile().name(), true);
        return true;
    }

    private static void reward(ServerLevel level, Anomaly a, ServerPlayer p) {
        Rank rank = Progression.rank(p);
        RandomSource r = p.getRandom();
        AnomalyDirector.discovered(p, a.kind); // closing one counts as meeting it
        int shards = shardsFor(rank, r);
        Vec3 from = Vec3.atCenterOf(a.focus()).add(0, 0.4, 0);
        eject(level, from, p, new ItemStack(CoreApi.ESSENCE_SHARD.get(), shards));
        boolean core = false;
        double roll = r.nextDouble();
        if (roll < CORE_CHANCE) {
            ItemStack c = PowerCoreHooks.roll(rank, r);
            if (!c.isEmpty()) {
                eject(level, from, p, c);
                core = true;
            }
        }
        double xp = xpFor(rank);
        Progression.grantXp(p, xp, Progression.XpSource.ANOMALY);
        Progression.count(p, "awaken:rifts_closed");
        AnomalyMemory mem = p.getData(AnomalyContent.MEMORY);
        mem.riftsClosed++;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Rift closed"))
                .body(Component.literal("+" + shards + " essence" + (core ? " · a Power Core" : "") + " · +" + Math.round(xp) + " XP"))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
        DirectorLog.log(p, "anomaly", String.format("rift #%d closed: %d shards, %d XP (0.3 × floor 1 of %s), core roll %.2f %s %.2f%s", a.id, shards,
                Math.round(xp), rank.label, roll, roll < CORE_CHANCE ? "<" : "≥", CORE_CHANCE, core ? " → a Power Core" : ""));
        Playtest.log(p, "anomaly_closed", "kind", a.kind.id);
    }

    /** Loot flies out of the collapsing core toward the player. */
    private static void eject(ServerLevel level, Vec3 from, ServerPlayer toward, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, stack);
        Vec3 dir = toward.position().subtract(from);
        double len = Math.max(0.001, dir.horizontalDistance());
        item.setDeltaMovement(dir.x / len * 0.18, 0.3, dir.z / len * 0.18);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /** The director ended it (closed, timed out, cleared): the tear collapses if it's loaded. Spilled monsters stay. */
    static void cleanup(ServerLevel level, Anomaly a) {
        RiftEntity e = entity(level, a);
        if (e != null) {
            if (!a.paid) level.playSound(null, e.getX(), e.getY() + 1.0, e.getZ(), AnomalyContent.RIFT_FADE.get(), SoundSource.HOSTILE, 2.0F, 1.0F);
            // the collapse plays out over its own ticks; where entities don't tick, it simply goes
            if (level.isPositionEntityTicking(e.blockPosition())) e.beginClose();
            else e.discard();
        }
    }

    // ---- monsters -----------------------------------------------------------------------------------------------------

    /** Spawns up to {@code n} monsters around the core. Returns how many came out. */
    static int spill(ServerLevel level, Anomaly a, int n, @Nullable ServerPlayer target, MobRole.Role role) {
        int made = 0;
        RandomSource r = level.getRandom();
        for (int i = 0; i < n; i++) {
            BlockPos at = groundNear(level, a.pos, 2, 4, r);
            if (at == null) continue;
            LivingEntity mob = MobHooks.spawnThemed(level, a.theme, role, at, a.contentLevel, Set.of(), "");
            if (mob == null) continue;
            mob.getPersistentData().putInt(MOB_TAG, a.id);
            if (mob instanceof Mob m) {
                m.setHomeTo(a.pos, GUARD_RADIUS);
                if (target != null && !target.isCreative() && !target.isSpectator() && target.distanceToSqr(m) < 24 * 24) m.setTarget(target);
            }
            a.mobs.add(mob.getUUID());
            a.spawned++;
            made++;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 14, 0.3, 0.6, 0.3, 0.08);
        }
        return made;
    }

    /** Everything the rift let out that is still near turns on whoever struck the core. */
    private static void alert(ServerLevel level, Anomaly a, ServerPlayer by) {
        if (by.isCreative() || by.isSpectator()) return;
        for (UUID id : a.mobs) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive() && m.getTarget() == null && m.distanceToSqr(by) < 32 * 32) m.setTarget(by);
        }
    }

    /** Forgets monsters that died or are no longer loaded (they're on their own now). */
    static void prune(ServerLevel level, Anomaly a) {
        a.mobs.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
    }

    static @Nullable BlockPos groundNear(ServerLevel level, BlockPos center, int min, int max, RandomSource r) {
        for (int i = 0; i < 8; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = min + r.nextInt(max - min + 1);
            int x = center.getX() + (int) Math.round(Math.cos(ang) * d), z = center.getZ() + (int) Math.round(Math.sin(ang) * d);
            if (!level.hasChunkAt(x, z)) continue;
            // stand on the ground near the tear's own height, not the column's top (an overhang or a canopy above
            // would otherwise put every spot out of reach)
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + 1 + dy, z);
                BlockState floor = level.getBlockState(feet.below()), at = level.getBlockState(feet), head = level.getBlockState(feet.above());
                if (!floor.is(BlockTags.BLOCKS_MOTION_NO_LEAVES) || !floor.getFluidState().isEmpty()) continue;
                if (!at.getFluidState().isEmpty() || at.is(BlockTags.BLOCKS_MOTION) || head.is(BlockTags.BLOCKS_MOTION)) continue;
                return feet;
            }
        }
        return null;
    }

    // ---- themes -------------------------------------------------------------------------------------------------------

    /** Gate theme ids (the mobs module's rosters) with the rank each belongs to. */
    private record ThemeRank(String id, Rank home) {}

    private static final List<ThemeRank> THEMES = List.of(
            new ThemeRank("awaken:bell_crypt", Rank.E), new ThemeRank("awaken:fungal_hollow", Rank.E),
            new ThemeRank("awaken:ember_mines", Rank.E), new ThemeRank("awaken:hive", Rank.E),
            new ThemeRank("awaken:overgrown_temple", Rank.E), new ThemeRank("awaken:drowned_archive", Rank.D),
            new ThemeRank("awaken:clockwork_foundry", Rank.D), new ThemeRank("awaken:glass_desert", Rank.D),
            new ThemeRank("awaken:lantern_market", Rank.D), new ThemeRank("awaken:frost_monastery", Rank.C),
            new ThemeRank("awaken:mirror_palace", Rank.C), new ThemeRank("awaken:precursor_lab", Rank.C),
            new ThemeRank("awaken:nightmare", Rank.C), new ThemeRank("awaken:sky_archipelago", Rank.B),
            new ThemeRank("awaken:abyssal_shrine", Rank.A));

    /** Themes fit for a rank: its own and the one below (an S-rank hunter still meets A-rank creatures). */
    static List<String> themesFor(Rank rank) {
        List<String> out = new ArrayList<>();
        int top = Math.min(rank.ordinal(), Rank.A.ordinal());
        for (ThemeRank t : THEMES) if (t.home().ordinal() <= top && t.home().ordinal() >= top - 1) out.add(t.id());
        return out;
    }

    /** What comes through depends a little on where it opens: a desert tear spills glass and ember, a forest one thorns. */
    static String pickTheme(ServerLevel level, BlockPos pos, Rank rank, RandomSource r) {
        List<String> fit = themesFor(rank);
        Holder<Biome> b = level.getBiome(pos);
        List<String> hint = new ArrayList<>();
        if (b.is(BiomeTags.HAS_DESERT_PYRAMID) || b.is(BiomeTags.IS_BADLANDS)) hint.addAll(List.of("awaken:glass_desert", "awaken:ember_mines"));
        else if (b.is(BiomeTags.SPAWNS_SNOW_FOXES) || b.is(BiomeTags.HAS_IGLOO) || b.is(BiomeTags.HAS_VILLAGE_SNOWY)) hint.addAll(List.of("awaken:frost_monastery", "awaken:bell_crypt"));
        else if (b.is(BiomeTags.IS_JUNGLE)) hint.addAll(List.of("awaken:overgrown_temple", "awaken:hive"));
        else if (b.is(BiomeTags.HAS_SWAMP_HUT)) hint.addAll(List.of("awaken:fungal_hollow", "awaken:drowned_archive"));
        else if (b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA)) hint.addAll(List.of("awaken:overgrown_temple", "awaken:fungal_hollow", "awaken:hive"));
        else if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL)) hint.addAll(List.of("awaken:clockwork_foundry", "awaken:ember_mines", "awaken:frost_monastery"));
        else if (b.is(BiomeTags.IS_SAVANNA)) hint.addAll(List.of("awaken:ember_mines", "awaken:lantern_market"));
        else hint.addAll(List.of("awaken:bell_crypt", "awaken:lantern_market"));
        hint.retainAll(fit);
        if (!hint.isEmpty() && r.nextInt(10) < 6) return hint.get(r.nextInt(hint.size()));
        return fit.get(r.nextInt(fit.size()));
    }
}
