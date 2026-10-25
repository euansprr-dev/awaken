package dev.awaken.living.camp;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Camps' GameTest (SPEC §21.8): built near a player, woken with a heart and waves, cleared by the heart, grown by days. */
public final class CampTests {
    private CampTests() {}

    public static void register() {
        AwakenTests.registerIsolated("living_camp", 300, h -> {
            ServerLevel level = h.getLevel();
            Camps cs = Camps.get(level.getServer());
            BlockPos base = h.absolutePos(new BlockPos(0, 1, 0));
            AwakenTests.clearAbove(level, base, 14, 0, 48); // plots are reused: an old floor overhead would be the "ground"
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) level.setBlock(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            long day = PlayDays.current(level.getServer());
            Camps.Camp c = cs.add("riftborn", "awaken:ember_mines", base, true, "", 1234L);
            Camps.Camp den = cs.add("hollow_den", "", base.offset(400, 0, 0), true, "", 99L);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                Progression.awaken(p);
                p.setPos(Vec3.atCenterOf(base.offset(3, 0, 3)));
                c.tier = 2;
                cs.build(level, c);
                h.assertTrue(!c.stood.isEmpty() && !c.ground.isEmpty(), "the camp is built into the world");
                h.assertTrue(c.stood.stream().anyMatch(l -> level.getBlockState(BlockPos.of(l)).is(Blocks.CAMPFIRE)), "with its fire");
                // awake: its heart and a first wave of 2 + tier
                cs.wake(level, c);
                List<UUID> b = List.copyOf(bodiesOf(cs, c));
                h.assertTrue(b.size() == 1 + Camps.waveSize(c), "heart and a wave: " + b.size());
                Entity heart = b.stream().map(level::getEntity).filter(e -> e != null && e.entityTags().contains(Camps.HEART)).findFirst().orElse(null);
                h.assertTrue(heart != null && CreatureTag.of(heart).kind() == CreatureTag.Kind.CAMP && CreatureTag.of(heart).elite(), "the heart is an elite of the camp");
                // the wave down, the next comes (tier 2: one more)
                for (UUID id : b) {
                    Entity e = level.getEntity(id);
                    if (e instanceof LivingEntity le && !e.entityTags().contains(Camps.HEART)) le.hurtServer(level, p.damageSources().playerAttack(p), 1.0E6F);
                }
                cs.run(level, day);
                h.assertTrue(bodiesOf(cs, c).size() > b.size(), "the next wave");
                // the heart falls: cleared, ash, paid
                BlockPos ground = BlockPos.of(c.ground.getFirst());
                ((LivingEntity) heart).hurtServer(level, p.damageSources().playerAttack(p), 1.0E6F);
                h.assertTrue(c.cleared(day), "cleared");
                h.assertTrue(level.getBlockState(ground).is(Blocks.COARSE_DIRT), "ash where it stood");
                h.assertTrue(Progression.profile(p).counter("awaken:camps_cleared") == 1, "paid and counted");
                // ignored with players around, a camp grows a tier every two play-days
                den.lastGrow = day - 2;
                p.setPos(Vec3.atCenterOf(den.pos()));
                Camps.onNewDay(level.getServer(), day);
                h.assertTrue(den.tier == 2, "the den grew: " + den.tier);
            } finally {
                cs.sleep(level, c);
                cs.camps.remove(c);
                cs.camps.remove(den);
            }
            h.succeed();
        });
    }

    private static List<UUID> bodiesOf(Camps cs, Camps.Camp c) {
        return cs.bodiesOf(c.id);
    }
}
