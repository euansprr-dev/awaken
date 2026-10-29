package dev.awaken.core.api;

import dev.awaken.core.Forces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;

/**
 * Spawning AWAKEN mobs from any module. The mobs module installs the real bestiary with {@link #install}; until then
 * (and in tests) a fallback spawns scaled vanilla mobs so every caller keeps working.
 */
public final class MobHooks {
    private MobHooks() {}

    public interface Spawner {
        /** A themed mob for a gate/stratum/dream. theme = gate theme id (e.g. "awaken:bell_crypt"). */
        LivingEntity spawnThemed(ServerLevel level, String theme, MobRole.Role role, BlockPos pos, int contentLevel, Set<Forces.Tag> tags, String gateId);

        /** A named boss (e.g. "awaken:bell_warden"). */
        LivingEntity spawnBoss(ServerLevel level, String bossId, BlockPos pos, int contentLevel, String gateId);

        /**
         * A gate world's signature creature by id (e.g. "awaken:the_ringer", SPEC §38.8): a creature of the floor with a
         * body of its own, an elite to the gate (never a Lord), or a peaceful spectacle. Null when there's no such creature
         * yet, and the world makes do without it.
         */
        default LivingEntity spawnCreature(ServerLevel level, String creatureId, BlockPos pos, int contentLevel, String gateId) {
            return null;
        }

        /** Whether this is a peaceful overworld creature (ambient or grazing) that powers' waves shouldn't hurt unprovoked. */
        default boolean peaceful(net.minecraft.world.entity.Entity e) { return false; }

        /** The player a creature is bonded to (a bonded mount), if any: only they may ride or possess it. */
        default java.util.Optional<java.util.UUID> bondedTo(net.minecraft.world.entity.Entity e) { return java.util.Optional.empty(); }
    }

    private static Spawner spawner = new Fallback();

    public static void install(Spawner s) {
        spawner = s;
    }

    public static LivingEntity spawnThemed(ServerLevel level, String theme, MobRole.Role role, BlockPos pos, int contentLevel, Set<Forces.Tag> tags, String gateId) {
        return spawner.spawnThemed(level, theme, role, pos, contentLevel, tags, gateId);
    }

    public static LivingEntity spawnBoss(ServerLevel level, String bossId, BlockPos pos, int contentLevel, String gateId) {
        return spawner.spawnBoss(level, bossId, pos, contentLevel, gateId);
    }

    public static LivingEntity spawnCreature(ServerLevel level, String creatureId, BlockPos pos, int contentLevel, String gateId) {
        return spawner.spawnCreature(level, creatureId, pos, contentLevel, gateId);
    }

    public static boolean peaceful(net.minecraft.world.entity.Entity e) {
        return spawner.peaceful(e);
    }

    /** The player this creature is bonded to (a bonded mount), if any. */
    public static java.util.Optional<java.util.UUID> bondedTo(net.minecraft.world.entity.Entity e) {
        return spawner.bondedTo(e);
    }

    /** Applies SPEC section 2 scaling to any living entity and tags its role. */
    public static void scale(LivingEntity e, int lv, MobRole role) {
        double hp = 1.0 + 0.075 * lv;
        double dmg = 1.0 + 0.04 * lv;
        double mult = switch (role.role()) {
            case ELITE -> 2.5;
            case WARDEN -> 5.0;
            case BREAK -> 8.0;
            case BOSS -> 14.0;
            default -> 1.0;
        };
        hp *= ascensionHealth(e, role);
        dmg *= ascensionDamage(e, role);
        // Permanent modifiers so scaled mobs keep their stats through a save and reload.
        permanent(e, Attributes.MAX_HEALTH, "level_health", hp * mult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        permanent(e, Attributes.ATTACK_DAMAGE, "level_damage", dmg * (role.role() == MobRole.Role.BOSS ? 1.6 : role.role() == MobRole.Role.BREAK ? 1.3 : 1.0) - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        permanent(e, Attributes.ARMOR, "level_armor", lv / 12.0, AttributeModifier.Operation.ADD_VALUE);
        e.setHealth(e.getMaxHealth());
        e.setData(CoreApi.MOB_ROLE.get(), role);
    }

    /** Persistent-data key: the Ascension tier a hostile creature was scaled for (the story evens it out per player). */
    public static final String ASCENSION_TAG = "awaken_ascension";
    /** Players within this many blocks decide the tier a new creature is scaled for. */
    public static final double ASCENSION_RANGE = 64;

    /**
     * The Ascension tier for a hostile role (0 for allies, spirits and when no story is installed), at most 10: the
     * tier the nearest player meets (the world's tier, never above their own: SPEC "Together"), or the world's tier when
     * nobody is near. The tier is remembered on the creature so hits to and from other players can be evened out.
     */
    public static int ascension(LivingEntity e, MobRole role) {
        if (role.role() == MobRole.Role.ECHO || role.role() == MobRole.Role.SPIRIT || role.role() == MobRole.Role.NONE) return 0;
        if (!(e.level() instanceof ServerLevel sl)) return 0;
        var near = sl.getNearestPlayer(e.getX(), e.getY(), e.getZ(), ASCENSION_RANGE, false);
        int tier = near instanceof net.minecraft.server.level.ServerPlayer sp ? StoryHooks.ascensionOf(sp)
                : Math.clamp(StoryHooks.ascension(sl.getServer()), 0, 10);
        e.getPersistentData().putInt(ASCENSION_TAG, tier);
        return tier;
    }

    /** The tier this creature was scaled for, or -1 if it never was (vanilla creatures, or scaled before the tag existed). */
    public static int scaledAscension(LivingEntity e) {
        return e.getPersistentData().getIntOr(ASCENSION_TAG, -1);
    }

    /** New Game+: each Ascension tier gives hostile creatures +30% health... */
    public static double ascensionHealth(LivingEntity e, MobRole role) {
        return 1.0 + 0.3 * ascension(e, role);
    }

    /** ...and +15% damage. Allies are left alone. */
    public static double ascensionDamage(LivingEntity e, MobRole role) {
        return 1.0 + 0.15 * ascension(e, role);
    }

    private static void permanent(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                  String id, double amount, AttributeModifier.Operation op) {
        var inst = e.getAttribute(attr);
        if (inst == null) return;
        var key = dev.awaken.Awaken.id(id);
        inst.removeModifier(key);
        inst.addPermanentModifier(new AttributeModifier(key, amount, op));
    }

    private static final class Fallback implements Spawner {
        @Override
        public LivingEntity spawnThemed(ServerLevel level, String theme, MobRole.Role role, BlockPos pos, int lv, Set<Forces.Tag> tags, String gateId) {
            EntityType<? extends Mob> type = switch (level.getRandom().nextInt(3)) {
                case 0 -> EntityTypes.SKELETON;
                case 1 -> EntityTypes.SPIDER;
                default -> EntityTypes.ZOMBIE;
            };
            return spawn(level, type, pos, lv, new MobRole(role, lv, theme, java.util.Optional.empty(), gateId), tags);
        }

        @Override
        public LivingEntity spawnBoss(ServerLevel level, String bossId, BlockPos pos, int lv, String gateId) {
            return spawn(level, EntityTypes.ZOMBIE, pos, lv, new MobRole(MobRole.Role.BOSS, lv, "", java.util.Optional.of(bossId), gateId), Set.of());
        }

        private static LivingEntity spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos, int lv, MobRole role, Set<Forces.Tag> tags) {
            Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (mob == null) return null;
            mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            scale(mob, lv, role);
            for (Forces.Tag t : tags) Forces.tag(mob, t);
            level.addFreshEntity(mob);
            return mob;
        }
    }
}
