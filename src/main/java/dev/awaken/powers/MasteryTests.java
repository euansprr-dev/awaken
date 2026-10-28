package dev.awaken.powers;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** GameTests for power mastery (growth by use), the restoreEnergy API and the versioned loadout. */
final class MasteryTests {
    private MasteryTests() {}

    static void register() {
        // A cast at the air pays nothing; the same cast landing on a zombie pays its whole cost; a miss mid-fight pays
        // half; a kill pays a bonus; casting beside a mob that never fought you still pays nothing.
        AwakenTests.register("powers_mastery_real_hits_not_air", 80, h -> {
            floor(h, 4);
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            PowerType slam = PowerRegistry.get("ground_slam");
            PowerType wall = PowerRegistry.get("wall");
            Powers.learn(sp, slam.id, 0);
            Powers.learn(sp, wall.id, 0);
            Loadout l = Powers.loadout(sp);
            String id = slam.id.toString();
            h.runAfterDelay(3, () -> {
                Powers.fillEnergy(sp);
                h.assertTrue(Casting.cast(sp, slam, 0, false, 0, 0).ok(), "slam at the air casts");
                h.assertTrue(l.mastery(id) == 0, "air pays nothing, got " + l.mastery(id));
                // a mob that never fought you, next to a wall raised at the air: still nothing
                Zombie idle = stillZombie(h, new BlockPos(0, 1, -3));
                h.assertTrue(Casting.cast(sp, wall, 0, false, 0, 0).ok(), "wall casts");
                h.assertTrue(l.mastery(wall.id.toString()) == 0, "powers beside an idle mob teach nothing");
                idle.discard();
                // a real hit pays the whole cost
                Zombie z = stillZombie(h, new BlockPos(2, 1, 0));
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                float hp = z.getHealth();
                h.assertTrue(Casting.cast(sp, slam, 0, false, 0, 0).ok(), "slam casts");
                h.assertTrue(z.getHealth() < hp, "and lands on the zombie");
                h.assertTrue(Math.abs(l.mastery(id) - slam.cost) < 1e-3, "a landed cast pays its cost (" + slam.cost + "), got " + l.mastery(id));
                // a miss in the same fight pays half
                z.discard();
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                h.assertTrue(Casting.cast(sp, slam, 0, false, 0, 0).ok(), "slam casts again");
                h.assertTrue(Math.abs(l.mastery(id) - slam.cost * (1 + Mastery.FIGHT_SHARE)) < 1e-3, "a miss in a fight pays half, got " + l.mastery(id));
                // a kill pays a bonus on top; energy older casts spent at the air is not claimed by this cast's hit
                Zombie weak = stillZombie(h, new BlockPos(-2, 1, 0));
                weak.setHealth(1f);
                float before = l.mastery(id);
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                h.assertTrue(Casting.cast(sp, slam, 0, false, 0, 0).ok(), "slam casts a third time");
                h.assertTrue(!weak.isAlive(), "the weak zombie died");
                float gained = l.mastery(id) - before;
                h.assertTrue(Math.abs(gained - (slam.cost + Mastery.KILL)) < 1e-3, "landed + a kill, nothing more: " + gained);
                h.succeed();
            });
        });

        // A full bar levels a power exactly like a duplicate core: same level, same damage and cooldown. A core keeps
        // the bar's share; the top level keeps no bar.
        AwakenTests.register("powers_mastery_levels_like_a_core", 40, h -> {
            ServerPlayer byCore = player(h, new BlockPos(0, 1, 0));
            ServerPlayer byUse = player(h, new BlockPos(1, 1, 0));
            PowerType beam = PowerRegistry.get("beam");
            String id = beam.id.toString();
            Powers.learn(byCore, beam.id, 0);
            Powers.learn(byUse, beam.id, 0);
            h.assertTrue(Powers.useCore(byCore, beam, 0), "a duplicate core levels up");
            h.assertTrue(Mastery.add(byUse, beam, Mastery.need(1)) == 1, "a full bar levels up once");
            Loadout a = Powers.loadout(byCore), b = Powers.loadout(byUse);
            h.assertTrue(a.get(id).level() == 2 && b.get(id).level() == 2, "both level 2");
            h.assertTrue(b.mastery(id) == 0, "the bar starts again");
            float dmgCore = Cast.of(byCore, beam).dmg(), dmgUse = Cast.of(byUse, beam).dmg();
            float levelOne = PowerMath.damage(beam.damage, dev.awaken.progression.Progression.profile(byUse).level(),
                    dev.awaken.progression.Progression.stat(byUse, beam.scaling), 1, 1f);
            // a level is +6% (SPEC 34.2)
            h.assertTrue(Math.abs(dmgCore - dmgUse) < 1e-4 && dmgUse > levelOne * 1.05f, "same, stronger damage: " + dmgCore + " vs " + dmgUse
                    + " (level 1: " + levelOne + ")");
            h.assertTrue(PowerMath.cooldown(beam.cooldown, 1f, a.get(id).level(), 0) == PowerMath.cooldown(beam.cooldown, 1f, b.get(id).level(), 0)
                    && PowerMath.cooldown(beam.cooldown, 1f, b.get(id).level(), 0) < beam.cooldown, "same, shorter cooldown");
            // half a bar, then a core: still half way to the next level
            Mastery.add(byUse, beam, Mastery.need(2) / 2);
            h.assertTrue(Math.abs(b.masteryProgress(id) - 0.5f) < 1e-3, "half way to 3");
            h.assertTrue(Powers.useCore(byUse, beam, 0) && b.get(id).level() == 3, "a core is still a shortcut");
            h.assertTrue(Math.abs(b.masteryProgress(id) - 0.5f) < 1e-3, "the bar keeps its share, " + b.masteryProgress(id));
            // overflow climbs several levels; the top keeps no bar and takes no more
            h.assertTrue(Mastery.add(byUse, beam, Mastery.need(3) + Mastery.need(4)) == 2 && b.get(id).level() == Loadout.MAX_LEVEL, "to the top");
            h.assertTrue(b.mastery(id) == 0 && b.masteryProgress(id) == 1f, "mastered: no bar, shown full");
            h.assertTrue(Mastery.add(byUse, beam, 1000) == 0, "nothing past the top");
            h.assertTrue(Mastery.need(Loadout.MAX_LEVEL) == 0 && Mastery.need(1) < Mastery.need(2) && Mastery.need(3) < Mastery.need(4), "rising costs");
            h.succeed();
        });

        // Mastery is saved (schema 2) and synced; a schema-1 loadout loads with empty bars and is written as schema 2.
        AwakenTests.register("powers_mastery_saved_and_versioned", 20, h -> {
            Loadout l = new Loadout();
            l.owned.put("awaken:beam", new Loadout.Owned("awaken:beam", 0, 2));
            l.bar("awaken:beam").points = 1234.5f;
            l.bar("awaken:ghost").points = 9f; // not owned: dropped on load
            var tag = (CompoundTag) Loadout.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow();
            Loadout q = Loadout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(Math.abs(q.mastery("awaken:beam") - 1234.5f) < 1e-3 && q.mastery("awaken:ghost") == 0, "bars round trip");
            h.assertTrue(tag.getInt("schema").orElse(0) == Loadout.SCHEMA && Loadout.SCHEMA >= 2, "written as the current schema");
            tag.remove("mastery");
            tag.putInt("schema", 1);
            Loadout old = Loadout.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(old.schema() == Loadout.SCHEMA && old.mastery("awaken:beam") == 0 && old.get("awaken:beam").level() == 2,
                    "a schema-1 save loads: level kept, no bar");
            var buf = new io.netty.buffer.UnpooledByteBufAllocator(false).buffer();
            Loadout.STREAM_CODEC.encode(buf, q);
            Loadout synced = Loadout.STREAM_CODEC.decode(buf);
            h.assertTrue(Math.abs(synced.mastery("awaken:beam") - 1234.5f) < 1e-3, "and the bar reaches the client");
            h.succeed();
        });

        // Powers.restoreEnergy gives energy back, never above the pool, and says how much it added.
        AwakenTests.register("powers_restore_energy", 20, h -> {
            ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
            Powers.fillEnergy(sp);
            float max = Powers.maxEnergy(sp);
            h.assertTrue(Casting.drainExtra(sp, 50), "drain 50");
            h.assertTrue(Math.abs(Powers.restoreEnergy(sp, 20) - 20) < 1e-3 && Math.abs(Powers.energy(sp) - (max - 30)) < 1e-3, "restores 20");
            h.assertTrue(Math.abs(Powers.restoreEnergy(sp, 1000) - 30) < 1e-3 && Math.abs(Powers.energy(sp) - max) < 1e-3, "capped at the pool");
            h.assertTrue(Powers.restoreEnergy(sp, -5) == 0, "nothing for a negative amount");
            long uses = dev.awaken.progression.Progression.profile(sp).counter("awaken:power_uses/energy");
            h.assertTrue(uses == 0 && Powers.loadout(sp).mastery.isEmpty(), "restored energy never counts as spent");
            h.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(rel));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        return sp;
    }

    private static void floor(GameTestHelper h, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static Zombie stillZombie(GameTestHelper h, BlockPos pos) {
        Zombie z = h.spawn(EntityTypes.ZOMBIE, pos);
        z.setNoAi(true);
        z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return z;
    }
}
