package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.kin.KinBeast;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every death ends (SPEC §47.12): each of our creatures (gate creatures, bosses, wild beasts, kin) killed by a player
 * drops what it drops and is gone within its own fall, and a hunted beast's carcass is laid only once its falling body
 * has gone (never two bodies, never one left standing).
 */
public final class DeathTests {
    private DeathTests() {}

    public static void register() {
        AwakenTests.registerIsolated("mobs_every_death", 300, DeathTests::everyDeath);
    }

    /** Bodies whose death ran its drops (a test-only ear on the event, put on once). */
    private static final Set<UUID> DROPPED = ConcurrentHashMap.newKeySet();
    private static boolean listening;

    private static void everyDeath(GameTestHelper h) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((LivingDropsEvent e) -> DROPPED.add(e.getEntity().getUUID()));
        }
        ServerLevel level = h.getLevel();
        var live = AwakenTests.survivalPlayer(h, "Felled", new BlockPos(0, 1, -2));
        ServerPlayer killer = live.player();
        List<LivingEntity> bodies = new ArrayList<>();
        Map<LivingEntity, Integer> fall = new HashMap<>();
        Map<EntityType<?>, Integer> dying = new HashMap<>();
        int i = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!dev.awaken.Awaken.MODID.equals(id.getNamespace())) continue;
            Vec3 at = h.absoluteVec(new Vec3(-30 + (i % 21) * 3, 1, -30 + (i / 21) * 3));
            MobDef def = Bestiary.get(id.getPath());
            LivingEntity e = null;
            if (def != null && MobTypes.type(def) == type) {
                e = Mobs.spawn(level, def, at, 5, new MobRole(def.tier.role, 5, def.theme.id, Optional.empty(), ""), Set.of());
            } else {
                Entity made = type.spawn(level, BlockPos.containing(at), EntitySpawnReason.COMMAND);
                if (made instanceof KinBeast k) e = k;
                else if (made != null) made.discard(); // not a creature you fight (a person, a shot, a statue)
            }
            if (e == null) continue;
            i++;
            bodies.add(e);
            // its fall: a beast's own clip, a kit boss's up to 80 ticks, else vanilla's second
            fall.put(e, e instanceof NamedBeast nb ? nb.deathTicks() : e instanceof AwakenBoss ? 80 : 20);
        }
        h.assertTrue(bodies.size() > 150, "every creature spawned (" + bodies.size() + ")");
        for (LivingEntity e : bodies) {
            // one that gets up once after its first death (the Rises trait) has: getting up is not its death
            if (e instanceof AwakenMob m) m.setFlag(AwakenMob.F_RISEN, true);
            e.setLastHurtByPlayer(killer, 100);
            e.hurtServer(level, level.damageSources().playerAttack(killer), 1.0E6F);
            if (e.isAlive()) e.kill(level); // one that turns a blow aside still dies
            if (e.isAlive()) {
                e.setHealth(0);
                e.die(level.damageSources().genericKill());
            }
            h.assertTrue(!e.isAlive(), BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) + " dies");
            dying.merge(e.getType(), 1, Integer::sum);
        }
        long start = level.getGameTime();
        Map<LivingEntity, Long> gone = new HashMap<>();
        h.onEachTick(() -> {
            long now = level.getGameTime();
            for (LivingEntity e : bodies) {
                if (gone.containsKey(e) || !e.isRemoved()) continue;
                gone.put(e, now - start);
                dying.merge(e.getType(), -1, Integer::sum);
            }
            // a carcass is never laid beside its own falling body
            for (Entity c : level.getEntities((Entity) null, h.getBounds().inflate(40), dev.awaken.core.api.LivingHooks::carcass)) {
                h.assertTrue(dying.getOrDefault(c.getType(), 0) <= 0 || !sameSpot(c, bodies, gone),
                        "a carcass of " + BuiltInRegistries.ENTITY_TYPE.getKey(c.getType()) + " was laid while its body still fell");
            }
            if (gone.size() < bodies.size() && now - start < 100) return;
            for (LivingEntity e : bodies) {
                String name = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
                Long took = gone.get(e);
                h.assertTrue(took != null, name + "'s body is gone (it was still there " + (now - start) + " ticks after it died)");
                h.assertTrue(took <= fall.get(e) + 2, name + "'s body is gone within its fall (" + fall.get(e) + " ticks; took " + took + ")");
                h.assertTrue(DROPPED.contains(e.getUUID()), name + " dropped what it drops");
            }
            live.leave();
            h.succeed();
        });
    }

    /** Whether a carcass lies where a body of its kind that hasn't gone yet fell. */
    private static boolean sameSpot(Entity carcass, List<LivingEntity> bodies, Map<LivingEntity, Long> gone) {
        for (LivingEntity e : bodies) {
            if (e.getType() == carcass.getType() && !gone.containsKey(e) && e.distanceToSqr(carcass) < 1) return true;
        }
        return false;
    }
}
