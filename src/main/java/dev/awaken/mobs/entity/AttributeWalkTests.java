package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AttributeWalk;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateService;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Nothing of ours moves a body's attributes or effects inside vanilla's walk over its dirty attributes (SPEC §13 "Mobs"):
 * the walk answers max health with the health setter and scale with a size refresh, so those, and every listener they
 * reach ({@code EntityEvent.Size}: the forms' true size, the Gravity Wells' eyes), must neither make an attribute nor
 * touch an effect. The Thousandfoot's carcass crashed the server that way in a gate (12 Nov); this holds every kind to it.
 */
public final class AttributeWalkTests {
    private AttributeWalkTests() {}

    public static void register() {
        AwakenTests.register("mobs_attribute_walk_every_kind", 80, AttributeWalkTests::everyKind);
    }

    static void everyKind(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 2, 2.5));
        List<String> bad = new ArrayList<>();
        Set<EntityType<?>> seen = new HashSet<>();
        int checked = 0;
        // every kind as the gates and the land send it: its role, its scale, its first tick still to come
        for (MobDef d : Bestiary.all()) {
            MobRole.Role role = d.tier.isBoss() ? MobRole.Role.BOSS : d.tier.role;
            AwakenMob m = null;
            try {
                m = Mobs.spawn(level, d, at, 17, new MobRole(role, 17, d.theme.id, d.tier.isBoss() ? Optional.of(d.id) : Optional.empty(), ""), Set.of());
                if (m == null) continue; // a def with no type of its own (the bestiary tests hold those)
                seen.add(m.getType());
                check(level, m, d.id, bad);
                checked++;
            } catch (RuntimeException ex) {
                bad.add(d.id + ": " + ex);
            } finally {
                if (m != null) m.discard();
            }
        }
        // and any type of ours that makes one of our mobs the bestiary didn't
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (seen.contains(t) || !Awaken.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace())) continue;
            Entity e;
            try {
                e = t.create(level, EntitySpawnReason.COMMAND);
            } catch (RuntimeException notAlone) { // a type only its own system can make (none of the bestiary's)
                continue;
            }
            if (!(e instanceof AwakenMob m)) {
                if (e != null) e.discard();
                continue;
            }
            m.snapTo(at.x, at.y, at.z, 0F, 0F);
            level.addFreshEntity(m);
            check(level, m, BuiltInRegistries.ENTITY_TYPE.getKey(t).toString(), bad);
            checked++;
            m.discard();
        }
        for (String line : bad) Awaken.LOG.error("[mobs] attribute walk: {}", line);
        h.assertTrue(checked >= 100, "every kind was checked (" + checked + ")");
        h.assertTrue(bad.isEmpty(), bad.size() + " of " + checked + " kinds move their attributes or effects inside vanilla's walk, the first: "
                + (bad.isEmpty() ? "" : bad.getFirst()));
        h.succeed();
    }

    /** In a gate (the Wells' listener runs in gate levels only): its first tick's answers, then every answer. */
    private static void check(ServerLevel level, AwakenMob m, String id, List<String> bad) {
        if (!m.isAddedToLevel()) {
            bad.add(id + ": never joined the level");
            return;
        }
        ServerLevel was = GateService.gateLevel(level.getServer()); // another test's stand-in, or none on the test server
        GateService.useTestLevel(level);
        try {
            String moved = AttributeWalk.replay(m);
            if (moved == null) moved = AttributeWalk.everyAnswer(m);
            if (moved != null) bad.add(id + ": " + moved);
        } finally {
            GateService.useTestLevel(was);
        }
    }
}
