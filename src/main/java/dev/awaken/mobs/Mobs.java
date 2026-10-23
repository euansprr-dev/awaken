package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Rosters;
import dev.awaken.mobs.def.Tier;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.BrokerBoss;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the mobs module (SPEC section 12). Most modules spawn through {@code MobHooks}; this adds direct
 * access to the bestiary, scaled spawns of any type, and the hooks the Veil module installs for Cord-Eaters.
 */
public final class Mobs {
    private Mobs() {}

    // ---- Veil hooks ------------------------------------------------------------------------------------------------

    /** How Cord-Eaters see spirits. The Veil module installs the real implementation with {@link #installSpiritHooks}. */
    public interface SpiritHooks {
        /** Is this player currently projected as a spirit? */
        boolean isSpirit(ServerPlayer player);

        /** A Cord-Eater bit this spirit's cord (snap back, Spirit Shock...). */
        void onCordBitten(ServerPlayer player, LivingEntity eater);
    }

    private static SpiritHooks spiritHooks = new SpiritHooks() {
        @Override
        public boolean isSpirit(ServerPlayer player) {
            return player.isSpectator() && player.entityTags().contains("awaken.spirit");
        }

        @Override
        public void onCordBitten(ServerPlayer player, LivingEntity eater) {
            Signal.toast(player, Component.translatable("awaken.mobs.cord_bitten"));
        }
    };

    public static void installSpiritHooks(SpiritHooks hooks) {
        spiritHooks = hooks;
    }

    public static SpiritHooks spiritHooks() {
        return spiritHooks;
    }

    // ---- bestiary --------------------------------------------------------------------------------------------------

    public static @Nullable MobDef def(String id) {
        return Bestiary.byAnyId(id);
    }

    public static Rosters.Roster roster(String themeId) {
        return Rosters.forTheme(themeId);
    }

    /** Every theme id the spawner answers to. */
    public static List<String> themeIds() {
        return Rosters.ids();
    }

    /** Every boss id {@code MobHooks.spawnBoss} knows ({@code awaken:<id>}), including nightmare template ids. */
    public static List<String> bossIds() {
        List<String> out = new ArrayList<>();
        for (MobDef d : Bestiary.all()) if (d.tier.isBoss() || d.tier == Tier.WARDEN) out.add(d.bossId());
        for (String f : Rosters.NIGHTMARE_FAMILIES) out.add("awaken:nightmare/" + f);
        return out;
    }

    public static boolean isAwakenMob(Entity e) {
        return e instanceof AwakenMob;
    }

    // ---- spawning ----------------------------------------------------------------------------------------------

    /**
     * SPEC section 12: spawns any mob type scaled to a content level. Our creatures get their full setup; vanilla
     * types are scaled the same way and tagged.
     */
    public static @Nullable LivingEntity spawnScaled(ServerLevel level, EntityType<?> type, BlockPos pos, int contentLevel, Set<Forces.Tag> modifiers) {
        MobDef d = MobTypes.def(type);
        if (d != null) {
            return spawn(level, d, Vec3.atBottomCenterOf(pos), contentLevel, new MobRole(d.tier.role, contentLevel, d.theme.id,
                    d.tier.isBoss() ? Optional.of(d.bossId()) : Optional.empty(), ""), modifiers);
        }
        Entity e = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
        MobScaling.apply(mob, contentLevel, new MobRole(MobRole.Role.NORMAL, contentLevel, "", Optional.empty(), ""), Tier.NORMAL);
        for (Forces.Tag t : modifiers) Forces.tag(mob, t);
        level.addFreshEntity(mob);
        return mob;
    }

    /** The one place AWAKEN creatures are created from code. */
    public static @Nullable AwakenMob spawn(ServerLevel level, MobDef def, Vec3 pos, int contentLevel, MobRole role, Set<Forces.Tag> tags) {
        EntityType<? extends AwakenMob> type = MobTypes.type(def);
        if (type == null) return null;
        AwakenMob mob = type.create(level, new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.MOB_SUMMONED, true));
        if (mob == null) return null;
        mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.setData(CoreApi.MOB_ROLE.get(), role);
        BlockPos bp = BlockPos.containing(pos);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(bp), EntitySpawnReason.MOB_SUMMONED, null);
        mob.configure(contentLevel, role, tags);
        // Only creatures that matter persist; ordinary ones follow vanilla despawning (gates and the Descent manage theirs).
        if (def.tier.isBoss() || role.role() == MobRole.Role.WARDEN || role.role() == MobRole.Role.BOSS || role.role() == MobRole.Role.BREAK
                || role.role() == MobRole.Role.NIGHTMARE || role.role() == MobRole.Role.ECHO || !role.gateId().isEmpty()) mob.setPersistenceRequired();
        if (def.tier.isBoss() || role.role() == MobRole.Role.WARDEN || role.role() == MobRole.Role.BOSS) mob.setHomeTo(bp, def.arena);
        level.addFreshEntity(mob);
        return mob;
    }

    /** A boss's or elite's summon: same level, normal role, no gate id (so gates don't count it as a floor spawn). */
    public static @Nullable AwakenMob spawnMinion(ServerLevel level, MobDef def, Vec3 pos, AwakenMob summoner) {
        MobRole r = summoner.role();
        AwakenMob m = spawn(level, def, pos, r.contentLevel(), new MobRole(MobRole.Role.NORMAL, r.contentLevel(), def.theme.id, Optional.empty(), ""), EnumSet.noneOf(Forces.Tag.class));
        if (m != null) {
            m.getPersistentData().putInt("awaken_summoner", summoner.getId());
            if (summoner.getTarget() != null) m.setTarget(summoner.getTarget());
        }
        return m;
    }

    /** Budget check for summons: at most twice the ability's count alive per summoner, and SPEC's 60-entity ceiling. */
    public static boolean minionRoom(ServerLevel level, AwakenMob summoner, int count) {
        List<AwakenMob> near = level.getEntitiesOfClass(AwakenMob.class, summoner.getBoundingBox().inflate(48), Entity::isAlive);
        if (near.size() + count > 40) return false;
        int mine = 0;
        for (AwakenMob m : near) if (m.getPersistentData().getIntOr("awaken_summoner", -1) == summoner.getId()) mine++;
        return mine + count <= Math.max(3, count * 2);
    }

    /** Hook for AwakenMob (keeps the drop logic in MobLoot). */
    public static void eliteCoreDrop(ServerLevel level, AwakenMob mob, DamageSource source) {
        MobLoot.eliteDrop(level, mob, mob.role(), source);
    }

    // ---- the Lantern Broker's duel ----------------------------------------------------------------------------------

    private record Offer(UUID broker, long expires) {}

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    public static void offerDuel(ServerPlayer player, BrokerBoss broker) {
        OFFERS.put(player.getUUID(), new Offer(broker.getUUID(), player.level().getGameTime() + 20 * 30));
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.translatable("awaken.mobs.broker.duel_title"))
                .body(Component.translatable("awaken.mobs.broker.duel_body"))
                .row(Component.translatable("awaken.mobs.broker.duel_stakes"), Component.translatable("awaken.mobs.broker.duel_stakes_value"))
                .footer(Component.translatable("awaken.mobs.broker.duel_footer"))
                .action("mobs:duel")
                .build());
        player.sendSystemMessage(Component.translatable("awaken.mobs.broker.duel_chat").withColor(broker.def().glow));
    }

    /** Accepts a pending duel offer. Returns false if there was none (or it expired). */
    public static boolean acceptDuel(ServerPlayer player) {
        Offer o = OFFERS.remove(player.getUUID());
        if (o == null || player.level().getGameTime() > o.expires()) return false;
        Entity e = player.level().getEntity(o.broker());
        if (!(e instanceof BrokerBoss broker) || !broker.isAlive() || broker.distanceToSqr(player) > 48 * 48) return false;
        broker.startDuel(player);
        return true;
    }

    static void onSignalAction(ServerPlayer player, String action, boolean accepted) {
        if (!"mobs:duel".equals(action)) return;
        if (accepted) {
            if (!acceptDuel(player)) player.sendOverlayMessage(Component.translatable("awaken.mobs.broker.no_offer"));
        } else {
            OFFERS.remove(player.getUUID());
        }
    }

    static void forget(Player player) {
        OFFERS.remove(player.getUUID());
    }

    static String log(String s) {
        Awaken.LOG.debug("[mobs] {}", s);
        return s;
    }
}
