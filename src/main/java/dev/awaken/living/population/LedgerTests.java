package dev.awaken.living.population;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

/** The creature ledger's GameTest (SPEC §21.7): where populations are, their catch-up, bodies taken and given back. */
public final class LedgerTests {
    private LedgerTests() {}

    public static void register() {
        AwakenTests.registerIsolated("living_ledger", 300, h -> {
            ServerLevel level = h.getLevel();
            Ledger l = new Ledger();
            BlockPos c = h.absolutePos(new BlockPos(0, 1, 0));
            int x = c.getX(), z = c.getZ();
            Ledger.Pop herd = new Ledger.Pop(l.nextId++, "herd", "mossback", x, z, List.of(x - 6, z - 6, x + 6, z, x, z + 6), 6, 12, 10, 42L, -1);
            Ledger.Pop pack = new Ledger.Pop(l.nextId++, "pack", "gloomstalker", x + 200, z + 200, List.of(), 3, 5, 10, 7L, herd.id);
            Ledger.Cell cell = new Ledger.Cell(Ledger.key(Math.floorDiv(x, Ledger.CELL), Math.floorDiv(z, Ledger.CELL)), new java.util.ArrayList<>(List.of(herd, pack)));
            l.add(cell);
            // where they are is a pure function of the day and the time
            double[] noon = l.where(herd, 10, 6000), again = l.where(herd, 10, 6000), tomorrow = l.where(herd, 11, 6000);
            h.assertTrue(noon[0] == again[0] && noon[1] == again[1], "the same herd in the same place at the same time");
            h.assertTrue(noon[0] != tomorrow[0] || noon[1] != tomorrow[1], "a little further along tomorrow");
            double[] denDay = l.where(pack, 10, 6000), denNight = l.where(pack, 10, 18000);
            h.assertTrue(denDay[0] == pack.hx && denDay[1] == pack.hz, "a pack sleeps at its den by day");
            double[] herdNight = l.where(herd, 10, 18000);
            h.assertTrue(Math.hypot(denNight[0] - herdNight[0], denNight[1] - herdNight[1]) < 30, "and trails its herd by night");
            // catch-up: three nights of the pack hunting cost the herd three; the herd regrows one a day meanwhile
            l.catchUp(pack, 13);
            h.assertTrue(herd.count == 6, "regrow 3, hunted 3: " + herd.count);
            pack.count = 0;
            l.catchUp(herd, 15);
            h.assertTrue(herd.count == 10, "with its pack gone, a herd grows two a day: " + herd.count);
            // bodies: taken near a player, counted down when one dies, given back when nobody's near
            herd.day = 15;
            int made = l.materialize(level, herd, new double[]{x, z}, 15);
            h.assertTrue(made == 10, "ten bodies: " + made);
            List<UUID> bodies = List.copyOf(l.bodiesOf(herd.id));
            Entity one = level.getEntity(bodies.getFirst());
            h.assertTrue(one instanceof LivingEntity && one.entityTags().contains(Ledger.TAG), "a ledger body");
            ((LivingEntity) one).kill(level);
            onDeathIn(l, (LivingEntity) one);
            h.assertTrue(herd.count == 9, "one died: " + herd.count);
            l.releaseAll(level);
            h.assertTrue(bodies.stream().noneMatch(id -> level.getEntity(id) != null && level.getEntity(id).isAlive()), "bodies given back");
            h.assertTrue(herd.count == 9, "the count stays what the living were");
            h.assertTrue(Ledger.ledgered("mossback") && !Ledger.ledgered("gloomstalker"), "herds leave natural spawning, hunters don't");
            h.succeed();
        });
    }

    /** onDeath reads the world's ledger; the test's own ledger gets the same treatment. */
    private static void onDeathIn(Ledger l, LivingEntity dead) {
        l.died(dead);
    }
}
