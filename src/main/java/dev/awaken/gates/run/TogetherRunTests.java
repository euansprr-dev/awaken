package dev.awaken.gates.run;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Set;

/**
 * GameTests for gates in a party (SPEC "Together"): two players in one run. Sealing never locks a party member out,
 * a kill pays everyone near it from their own budget with their own loot roll, rooms and the Lord grow with the party,
 * and "solo" means nobody else was inside.
 */
public final class TogetherRunTests {
    private TogetherRunTests() {}

    private static Room arenaOf(FloorLayout f) {
        for (Room r : f.rooms) if (r.type == Room.Type.ARENA && !r.spawnSpots.isEmpty() && !r.doors.isEmpty()) return r;
        return null;
    }

    private static void teardown(ServerLevel level, GateRecord g, ServerPlayer... ps) {
        for (int i = 1; i < ps.length; i++) {
            if (ps[i] != null && Runs.run(ps[i]).active()) dev.awaken.gates.Gates.forceExtract(ps[i], false);
        }
        GateLiveTests.teardown(level, ps.length > 0 ? ps[0] : null, g);
        for (ServerPlayer p : ps) if (p != null) level.getServer().getPlayerList().remove(p);
    }

    public static void register() {
        AwakenTests.registerIsolated("together_gates_sealing_brings_the_party", 120, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer a = null, b = null;
            try {
                GatePlan plan = GateLiveTests.plan(8181, Theme.BELL_CRYPT, Layout.ARENA_CHAIN, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                a = GateLiveTests.player(h);
                b = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "both entered the gate");
                h.assertTrue(dev.awaken.core.api.PartyHooks.sameParty(a, b), "one run, one party");
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room arena = arenaOf(f);
                h.assertTrue(arena != null, "an Arena Chain floor has an arena with doors");
                GateLiveTests.put(level, a, g, f, arena);
                // the friend waits a few blocks outside the arena's first door
                Room.Door door = arena.doors.get(0);
                BlockPos bar = Rooms.barsPositions(g, f, door).get(0);
                BlockPos outside = outsideOf(level, f, arena, bar, door);
                h.assertTrue(outside != null, "there is floor outside the arena door");
                b.teleportTo(level, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, Set.of(), 0F, 0F, true);
                h.assertFalse(Rooms.inRoom(level, b, f, arena), "the friend starts outside");
                Rooms.enter(level, a, g, f, arena, 0, 0);
                FloorState fs = g.floor(0, 1);
                h.assertTrue(fs.locked.contains(arena.id), "the arena seals");
                h.assertTrue(Rooms.inRoom(level, b, f, arena), "and the friend stepped through with them");

                // a friend who comes to the bars later is let through
                b.teleportTo(level, outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, Set.of(), 0F, 0F, true);
                h.assertFalse(Rooms.inRoom(level, b, f, arena), "out again");
                BlockPos near = bar.relative(outward(door));
                b.teleportTo(level, near.getX() + 0.5, near.getY(), near.getZ() + 0.5, Set.of(), 0F, 0F, true);
                h.assertTrue(Rooms.admitAtBars(level, b, g, f), "the bars let a party member through");
                h.assertTrue(Rooms.inRoom(level, b, f, arena), "inside with the fight");
            } finally {
                teardown(level, g, a, b);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("together_gates_kill_credits_the_party", 120, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer a = null, b = null, far = null;
            LivingEntity elite = null;
            try {
                GatePlan plan = GateLiveTests.plan(9191, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                a = GateLiveTests.player(h);
                b = GateLiveTests.player(h);
                far = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g) && Runs.enter(far, g), "three entered");
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room r = GateLiveTests.biggestCombat(f);
                GateLiveTests.put(level, a, g, f, r);
                GateLiveTests.put(level, b, g, f, r);
                far.teleportTo(level, a.getX() + 60, a.getY(), a.getZ(), Set.of(), 0F, 0F, true);
                elite = Rooms.spawn(level, g, f, r.id, MobRole.Role.ELITE, a.blockPosition());
                h.assertTrue(elite != null, "an elite takes form");
                int sa = Satchel.count(Runs.run(a).satchel), sb = Satchel.count(Runs.run(b).satchel), sf = Satchel.count(Runs.run(far).satchel);
                RunEvents.onGateMobDeath(level, g, elite, a, MobRole.of(elite));
                h.assertTrue(Runs.run(a).killXpFloor > 0 && Runs.run(b).killXpFloor > 0, "the killer and the friend beside them both get kill XP");
                h.assertTrue(Math.abs(Runs.run(a).killXpFloor - Runs.run(b).killXpFloor) < 1e-6, "the same share each");
                h.assertTrue(Runs.run(far).killXpFloor == 0, "a party member 60 blocks off doesn't");
                h.assertTrue(Satchel.count(Runs.run(a).satchel) > sa && Satchel.count(Runs.run(b).satchel) > sb,
                        "each has their own elite loot roll in their own Satchel");
                h.assertTrue(Satchel.count(Runs.run(far).satchel) == sf, "nothing for the far one");
                h.assertTrue(Runs.run(b).killXpFloor <= Runs.floorReward(b, g, 0, 1) * dev.awaken.progression.Curve.KILL_SHARE + 1e-6,
                        "shared kills never pay past a player's own floor budget");
            } finally {
                if (elite != null) elite.discard();
                teardown(level, g, a, b, far);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("together_gates_scale_with_the_party", 120, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer a = null, b = null;
            LivingEntity lordAlone = null, lordPair = null;
            try {
                int[] one = Rooms.partyCompany(6, 1, 1), two = Rooms.partyCompany(6, 1, 2), three = Rooms.partyCompany(6, 1, 3), four = Rooms.partyCompany(6, 1, 4);
                h.assertTrue(one[0] == 6 && one[1] == 1, "alone: as before");
                h.assertTrue(two[0] == 8 && two[1] == 1, "two: +35% monsters, " + two[0] + "/" + two[1]);
                h.assertTrue(three[0] == 10 && three[1] == 2, "three: +70% and one more elite, " + three[0] + "/" + three[1]);
                h.assertTrue(four[0] == 12 && four[1] == 2, "four: +105%, " + four[0] + "/" + four[1]);
                h.assertTrue(Rooms.partyCompany(0, 0, 4)[0] == 0 && Rooms.partyCompany(0, 0, 4)[1] == 0, "an empty room stays empty");

                GatePlan plan = GateLiveTests.plan(9292, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                a = GateLiveTests.player(h);
                b = GateLiveTests.player(h);
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room r = GateLiveTests.biggestCombat(f);
                h.assertTrue(Runs.enter(a, g), "one entered");
                h.assertTrue(Rooms.partyOnFloor(level, g, f) == 1, "a party of one on the floor");
                lordAlone = Rooms.spawn(level, g, f, r.id, MobRole.Role.BOSS, a.blockPosition());
                h.assertTrue(Runs.enter(b, g), "the friend came");
                h.assertTrue(Rooms.partyOnFloor(level, g, f) == 2, "two on the floor");
                lordPair = Rooms.spawn(level, g, f, r.id, MobRole.Role.BOSS, a.blockPosition());
                h.assertTrue(lordAlone != null && lordPair != null, "Lords take form");
                double ratio = lordPair.getAttributeValue(Attributes.MAX_HEALTH) / lordAlone.getAttributeValue(Attributes.MAX_HEALTH);
                h.assertTrue(Math.abs(ratio - 1.5) < 0.01, "the Lord has +50% health for the second player, x" + ratio);
            } finally {
                if (lordAlone != null) lordAlone.discard();
                if (lordPair != null) lordPair.discard();
                teardown(level, g, a, b);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("together_gates_solo_means_alone", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer a = null, b = null;
            try {
                GatePlan plan = GateLiveTests.plan(9393, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                a = GateLiveTests.player(h);
                b = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(a, g), "a entered alone");
                h.assertFalse(Runs.run(a).company, "alone so far: a solo run");
                h.assertTrue(Runs.enter(b, g), "b joins");
                h.assertTrue(Runs.run(a).company && Runs.run(b).company, "now neither run is solo");
                Runs.endRun(b, g, false);
                h.assertTrue(Runs.run(a).company, "and a's run stays not-solo after b left");
                Runs.endRun(a, g, false);
                h.assertTrue(Runs.enter(a, g), "a comes back alone");
                h.assertFalse(Runs.run(a).company, "a new run alone is solo again (the gate's party set still has two names)");
                h.assertTrue(g.party.size() == 2, "the all-time party set is still two");
            } finally {
                teardown(level, g, a, b);
            }
            h.succeed();
        });
    }

    /** A walkable block a few steps outside the arena, beyond the door holding {@code bar}. */
    private static BlockPos outsideOf(ServerLevel level, FloorLayout f, Room arena, BlockPos bar, Room.Door door) {
        var dir = outward(door);
        for (int step = 2; step <= 6; step++) {
            BlockPos p = bar.relative(dir, step);
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos at = p.above(dy);
                if (level.getBlockState(at.below()).isSolid() && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                        && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                        && !Rooms.inRoomAt(level, at, f, arena)) return at;
            }
        }
        return null;
    }

    /** Out of the room through this door (0 north -z, 1 east +x, 2 south +z, 3 west -x). */
    private static net.minecraft.core.Direction outward(Room.Door door) {
        return switch (door.side) {
            case 0 -> net.minecraft.core.Direction.NORTH;
            case 1 -> net.minecraft.core.Direction.EAST;
            case 2 -> net.minecraft.core.Direction.SOUTH;
            default -> net.minecraft.core.Direction.WEST;
        };
    }
}
