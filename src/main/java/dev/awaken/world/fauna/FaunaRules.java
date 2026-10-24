package dev.awaken.world.fauna;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The spawn rules of the Awakened layer, run by vanilla's natural spawner for every candidate position (registered
 * as each creature's spawn placement predicate). Cheapest checks first; the per-role counts read the census, never
 * the level. In order:
 * <ol>
 *   <li>the {@code awaken:fauna_spawning} game rule, the overworld only, the ground and the creature's time of day;</li>
 *   <li>night hunters: vanilla's monster light rule (block light 0, dark sky), never in Peaceful;</li>
 *   <li>the nearest player within 128 blocks must have awakened; before that only ambient creatures show, rarely,
 *       as hints (at most three);</li>
 *   <li>nothing but ambient creatures within {@value #HEARTH_GUARD} blocks of any Hearth;</li>
 *   <li>at most {@link FaunaRole#capPerPlayer} of each role near that player (the category's own cap is 16);</li>
 *   <li>named beasts: player level 3+, 40+ blocks away, the beast registry's cooldown and spacing.</li>
 * </ol>
 */
public final class FaunaRules {
    private FaunaRules() {}

    /** Blocks around a Hearth anchor kept free of everything but ambient creatures (covers the tier 5 radius, 80). */
    public static final int HEARTH_GUARD = 80;
    /** Ambient hints near a player who hasn't awakened yet. */
    public static final int HINT_CAP = 3;
    /** How far from the nearest player a named beast must appear. */
    public static final int BEAST_MIN_DISTANCE = 40;

    /** Tests swap these inside an isolated batch (try/finally). */
    interface HearthCheck {
        boolean near(ServerLevel level, BlockPos pos);
    }

    static HearthCheck hearth = HearthHooks::inside;
    static @Nullable Long timeOverride;

    public static long dayTime(Level level) {
        Long t = timeOverride;
        return t != null ? t : Math.floorMod(level.getOverworldClockTime(), 24000L);
    }

    /** The spawn placement predicate shared by every overworld creature. */
    public static <T extends Mob> boolean check(EntityType<T> type, ServerLevelAccessor la, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        return verdict(type, la, reason, pos, random, null, true) == null;
    }

    /** As {@link #check}, judged against {@code forced} instead of the nearest player when given (GameTests). */
    static <T extends Mob> boolean check(EntityType<T> type, ServerLevelAccessor la, EntitySpawnReason reason, BlockPos pos, RandomSource random, @Nullable Player forced) {
        return verdict(type, la, reason, pos, random, forced, true) == null;
    }

    /**
     * Why a creature may not spawn here, or null when it may (the reasons are constants: nothing is allocated). With
     * {@code commit} false a beast's slot isn't reserved, so {@code /awaken fauna why} can ask without side effects.
     */
    static <T extends Mob> @Nullable String verdict(EntityType<T> type, ServerLevelAccessor la, EntitySpawnReason reason, BlockPos pos, RandomSource random,
                                                    @Nullable Player forced, boolean commit) {
        MobDef d = MobTypes.def(type);
        if (d == null || d.fauna == null) return "not an overworld creature";
        Habitat h = Habitat.of(d.id);
        boolean water = h.water();
        if (EntitySpawnReason.isSpawner(reason)) {
            if (water) return inWater(la, pos) ? null : "not in water";
            return h.window() != Habitat.Window.DARK || Monster.checkMonsterSpawnRules(type, la, reason, pos, random) ? null : "spawner too bright";
        }
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            if (water) return inWater(la, pos) ? null : "not in water";
            return Mob.checkMobSpawnRules(type, la, reason, pos, random) ? null : "no ground";
        }
        ServerLevel level = la.getLevel();
        if (!FaunaModule.spawningOn(level)) return "game rule awaken:fauna_spawning is off";
        if (level.dimension() != Level.OVERWORLD) return "not the overworld";
        if (water) {
            // P3: the Tidewater spawns in open water (two deep, three for the big ones), never in a puddle
            if (!inWater(la, pos)) return "not in water";
            if (d.width * d.scale > 1.0F && !la.getFluidState(pos.above(2)).is(net.minecraft.tags.FluidTags.WATER)) return "water too shallow";
        } else {
            if (!Mob.checkMobSpawnRules(type, la, reason, pos, random)) return "the block below can't be spawned on";
            if (!h.ground().accepts(la.getBlockState(pos.below()))) return "wrong ground";
            boolean surface = pos.getY() >= la.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            if (h.ground() == Habitat.Ground.CAVE) {
                // P3: the Hollows live under a roof, never on the surface
                if (surface || la.canSeeSky(pos)) return "not in a cave";
            } else if (!surface && !h.caves()) {
                return "under a roof (surface creature)";
            }
        }
        FaunaRole role = d.fauna;
        if (h.window() == Habitat.Window.DARK) {
            if (level.getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
            if (!Monster.isDarkEnoughToSpawn(la, pos, random)) return "too bright for a night hunter";
        } else if (!h.window().contains(dayTime(level))) {
            return "wrong time of day";
        }
        if (role == FaunaRole.BEAST && level.getDifficulty() == Difficulty.PEACEFUL) return "peaceful";
        Player p = forced != null ? forced : level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 128.0, false);
        if (p == null) return "no player within 128 blocks";
        if (p.isSpectator()) return "nearest player is a spectator";
        Profile prof = Progression.profile(p);
        boolean awakened = prof.awakened();
        int count = water && role != FaunaRole.BEAST ? FaunaCensus.countWater(level, role, p.getX(), p.getZ(), 128.0)
                : FaunaCensus.countNear(level, role, p.getX(), p.getZ(), 128.0);
        boolean nearHearth = role != FaunaRole.AMBIENT && awakened && hearth.near(level, pos);
        if (!allowed(role, h, awakened, prof.level(), nearHearth, count, random.nextFloat())) {
            if (!awakened) return role == FaunaRole.AMBIENT ? (count >= HINT_CAP ? "enough hints already" : "a hint, not this time") : "the player hasn't awakened";
            if (nearHearth) return "inside a Hearth radius";
            if (prof.level() < h.minPlayerLevel()) return "player level too low";
            return "role cap reached";
        }
        if (role == FaunaRole.BEAST) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < BEAST_MIN_DISTANCE * BEAST_MIN_DISTANCE) return "too close to the player for a beast";
            BeastRegistry reg = BeastRegistry.get(level.getServer());
            long now = level.getGameTime();
            if (!reg.roomFor(level.dimension().identifier().toString(), pos, now)) return "beast cooldown or another beast nearby";
            if (commit) reg.reserve(now);
        }
        return null;
    }

    /**
     * The player-facing part of the rules, as a pure function (the GameTests drive it directly): who may spawn near a
     * player who has or hasn't awakened, near a Hearth or not, with {@code count} of the role already around.
     */
    static boolean allowed(FaunaRole role, Habitat h, boolean awakened, int playerLevel, boolean nearHearth, int count, float roll) {
        if (!awakened) return role == FaunaRole.AMBIENT && count < HINT_CAP && roll < h.preAwakenChance();
        if (role != FaunaRole.AMBIENT && nearHearth) return false;
        if (playerLevel < h.minPlayerLevel()) return false;
        return count < (h.water() ? waterCap(role) : role.capPerPlayer);
    }

    /**
     * P3: the water creatures' own caps near one player (inside their own category's cap of 10): a school or two of
     * fish and a few jellies, three otters or steeds, and the named beasts' shared one.
     */
    public static int waterCap(FaunaRole role) {
        return switch (role) {
            case AMBIENT -> 7;
            case NEUTRAL -> 3;
            case HUNTER -> 0;
            case BEAST -> FaunaRole.BEAST.capPerPlayer;
        };
    }

    /** Water here and below (a fish never spawns in the top film of a puddle). */
    static boolean inWater(ServerLevelAccessor la, BlockPos pos) {
        return la.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER) && la.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)
                && !la.getBlockState(pos.above()).isRedstoneConductor(la, pos.above());
    }

    /** Players within this many blocks of a spawn are its group (SPEC "Together"). */
    static final double GROUP_RADIUS = 48;

    /**
     * The content level a creature spawning here gets: a step below the player's level (beasts at it), never above
     * their rank's gates. Overworld creatures stay gentler than the gates the player can enter. With several players
     * near (the group: within {@link #GROUP_RADIUS}), the lowest of their levels decides, so a friend who joined late is
     * never out-levelled beside a veteran; with nobody that close, the nearest player within 128 blocks.
     */
    public static int contentLevel(ServerLevel level, BlockPos pos, FaunaRole role) {
        if (role == FaunaRole.AMBIENT) return 0;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        int best = Integer.MAX_VALUE;
        for (Player p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(x, y, z) > GROUP_RADIUS * GROUP_RADIUS) continue;
            Profile prof = Progression.profile(p);
            if (!prof.awakened()) continue;
            best = Math.min(best, levelFor(prof, role));
        }
        if (best != Integer.MAX_VALUE) return best;
        Player p = level.getNearestPlayer(x, y, z, 128.0, false);
        return p == null ? 0 : levelFor(Progression.profile(p), role);
    }

    private static int levelFor(Profile prof, FaunaRole role) {
        int cap = prof.rank().contentLevel;
        return switch (role) {
            case AMBIENT -> 0;
            case BEAST -> Math.clamp(prof.level(), 0, cap + 2);
            default -> Math.clamp(prof.level() - 1, 0, cap);
        };
    }
}
