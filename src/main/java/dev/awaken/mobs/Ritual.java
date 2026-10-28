package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * The prologue's ritual cast (SPEC §13 "The Ritual of the Bell"): the Seated One and the hall's twelve statues. Public
 * API for the story's ritual director (SPEC §12): spawn them, then drive their stages. Both are role RITUAL.
 */
public final class Ritual {
    private Ritual() {}

    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);

    /** Seated about 22 blocks tall; the box is its throne-side footprint, so culling and tracking see all of it. */
    public static final DeferredHolder<EntityType<?>, EntityType<SeatedOne>> SEATED_ONE = ENTITIES.registerEntityType("the_seated_one",
            SeatedOne::new, MobCategory.MISC, b -> b.sized(9.0F, 22.0F).eyeHeight(19.0F).clientTrackingRange(16).updateInterval(2)
                    .fireImmune().noSummon().noLootTable());

    public static final DeferredHolder<EntityType<?>, EntityType<RitualStatue>> STATUE = ENTITIES.registerEntityType("ritual_statue",
            RitualStatue::new, MobCategory.MISC, b -> b.sized(1.4F, 3.4F).eyeHeight(3.0F).clientTrackingRange(12).updateInterval(2)
                    .fireImmune().noSummon().noLootTable());

    static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(SEATED_ONE.get(), SeatedOne.attributes().build());
            e.put(STATUE.get(), RitualStatue.attributes().build());
        });
    }

    /** The exact distance-driven time used by the statue renderer's 1.6 second walk clip. */
    public static float walkSeconds(net.minecraft.world.entity.Entity e, float partial) {
        return e instanceof RitualStatue s ? s.walkAnimation.position(partial) * 0.55F : 0;
    }

    /** Client presentation: only actual moving WALK stages can make planted-foot sounds. */
    public static boolean walking(net.minecraft.world.entity.Entity e) {
        return e instanceof RitualStatue s && s.stage() == RitualStatue.WALK && s.walkAnimation.speed() > 0.005F
                && s.distanceToSqr(s.xo, s.getY(), s.zo) > 0.00001;
    }

    /** Whether a delayed weapon whoosh still belongs to the current wind-up. */
    public static boolean windingUp(net.minecraft.world.entity.Entity e) {
        return e instanceof RitualStatue s && s.stage() == RitualStatue.WINDUP;
    }

    /** The walk clip's planted foot at phase 0 (left) or 0.8 (right), in world coordinates. */
    public static Vec3 plantedFoot(net.minecraft.world.entity.Entity e, int foot) {
        // LivingEntityRenderer rotates by 180-bodyYaw, then flips model X/Y. Model +Z is behind the body.
        double yaw = Math.toRadians(e instanceof RitualStatue s ? s.yBodyRot : e.getYRot());
        double side = (foot & 1) == 0 ? 0.225 : -0.225;
        return e.position().add(Math.cos(yaw) * side + Math.sin(yaw) * 0.36, 0,
                Math.sin(yaw) * side - Math.cos(yaw) * 0.36);
    }

    public static @Nullable SeatedOne spawnSeated(ServerLevel level, Vec3 at, float yaw) {
        SeatedOne e = SEATED_ONE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (e == null) return null;
        e.snapTo(at.x, at.y, at.z, yaw, 0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        level.addFreshEntity(e);
        return e;
    }

    public static @Nullable RitualStatue spawnStatue(ServerLevel level, Vec3 at, float yaw) {
        RitualStatue e = STATUE.get().create(level, EntitySpawnReason.TRIGGERED);
        if (e == null) return null;
        e.snapTo(at.x, at.y, at.z, yaw, 0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        level.addFreshEntity(e);
        return e;
    }
}
