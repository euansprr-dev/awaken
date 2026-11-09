package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** A custom creature's body and the blocks (SPEC §39.12; Euan 9 Nov: "double check for all custom mobs that they can't like just go through walls"). */
public final class BodyInBlocksTests {
    private BodyInBlocksTests() {}

    /** Bodies with no collisions, each for a reason (SPEC §39.12). Anything else that loses its physics fails below. */
    private static final Set<String> GHOSTS = Set.of("echo_ghost", "hollow_follower", "the_seated_one", "training_ghost", "veil_spirit");
    private static final Set<String> NOCLIP = Set.of("cord_eater");

    public static void register() {
        // every creature of ours collides, unless it's on the list above
        AwakenTests.register("mob_bodies_have_physics", 20, h -> {
            ServerLevel level = h.getLevel();
            Set<String> ghosts = new TreeSet<>();
            int mobs = 0;
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!id.getNamespace().equals("awaken")) continue;
                Entity e = type.create(level, EntitySpawnReason.COMMAND);
                if (e == null) continue;
                if (e instanceof Mob) {
                    mobs++;
                    if (e.noPhysics) ghosts.add(id.getPath());
                }
                e.discard();
            }
            Set<String> noclip = new TreeSet<>();
            for (MobDef d : Bestiary.all()) if (d.has(Trait.NOCLIP)) noclip.add(d.id);
            h.assertTrue(mobs > 50, "found our creatures");
            h.assertTrue(ghosts.equals(new TreeSet<>(GHOSTS)), "bodies with no collisions: " + ghosts);
            h.assertTrue(noclip.equals(new TreeSet<>(NOCLIP)), "creatures that pass through walls: " + noclip);
            h.succeed();
        });

        // any creature of ours left inside a block walks out, not just the wild ones
        AwakenTests.register("mob_walks_out_of_a_block", 200, h -> {
            ServerLevel level = h.getLevel();
            AwakenMob m = plain(h, level, 0.0F, 0.95F);
            BlockPos at = h.absolutePos(new BlockPos(6, 1, 6));
            level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(at.above(), Blocks.STONE.defaultBlockState());
            m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            h.assertTrue(Unwedge.inside(m), "it starts inside the stone");
            h.succeedWhen(() -> {
                h.assertTrue(!Unwedge.inside(m), "it is still inside the stone");
                m.discard();
            });
        });

        // the one place creatures are made from code checks the body they actually grew
        AwakenTests.register("mob_spawn_fits_its_body", 20, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(6, 1, 6));
            for (BlockPos q : BlockPos.betweenClosed(at.offset(-5, -1, -5), at.offset(5, -1, 5))) level.setBlockAndUpdate(q, Blocks.STONE.defaultBlockState()); // a floor to step aside onto
            for (int y = 0; y < 6; y++) {
                level.setBlockAndUpdate(at.offset(1, y, 0), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(at.offset(-1, y, 0), Blocks.STONE.defaultBlockState());
            }
            for (MobDef d : Bestiary.all()) {
                if (d.flies() || d.tier.isBoss() || d.has(Trait.NOCLIP) || MobTypes.type(d) == null || d.width * d.scale < 1.2F || d.width * d.scale > 2.2F) continue;
                AwakenMob m = Mobs.spawn(level, d, new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), 5, new MobRole(d.tier.role, 5, d.theme.id, Optional.empty(), ""), Set.of());
                if (m == null) continue;
                boolean in = Unwedge.inside(m);
                String name = d.id;
                m.discard();
                h.assertTrue(!in, name + " was left inside the pillars beside its spot");
                h.succeed();
                return;
            }
            h.fail("no big walker in the bestiary");
        });
    }

    /** A walker of the base class, of no special kind. */
    private static AwakenMob plain(GameTestHelper h, ServerLevel level, float min, float max) {
        Vec3 open = h.absoluteVec(new Vec3(10.5, 1, 10.5));
        List<MobDef> defs = new ArrayList<>(Bestiary.all());
        for (MobDef d : defs) {
            if (d.flies() || d.tier.isBoss() || d.has(Trait.NOCLIP) || MobTypes.type(d) == null || d.width * d.scale < min || d.width * d.scale > max) continue;
            AwakenMob m = Mobs.spawn(level, d, open, 5, new MobRole(d.tier.role, 5, d.theme.id, Optional.empty(), ""), Set.of());
            if (m == null) continue;
            if (m.getClass() == AwakenMob.class) return m;
            m.discard();
        }
        throw new IllegalStateException("no plain walker in the bestiary");
    }
}
