package dev.awaken.gates;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.block.RigBlocks;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.run.Interactions;
import dev.awaken.gates.run.RigDirector;
import dev.awaken.gates.run.RigWellsLive;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.GateTestAccess;
import dev.awaken.gates.world.RigCollapse;
import dev.awaken.gates.world.RigGauntlet;
import dev.awaken.gates.world.RigMirrors;
import dev.awaken.gates.world.RigRoom;
import dev.awaken.gates.world.RigRooms;
import dev.awaken.gates.world.RigWells;
import dev.awaken.gates.world.Room;
import dev.awaken.gates.world.RoomGeometry;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Gate Rig's GameTests (SPEC §17 "Gate Rig"), one per mechanism: each archetype's pure solvability check across
 * 1,000 seeds with its determinism (the same plan builds the same rig, version 3 builds none) and the server running
 * it for real in the test level; and the protection rules (no breaking what a gate painted, no placing in a gate).
 */
final class RigTests {
    private RigTests() {}

    static final int SEEDS = 1000;
    private static final Layout[] LAYOUTS = {Layout.LABYRINTH, Layout.ARENA_CHAIN, Layout.PUZZLE_VAULT, Layout.HUB_AND_SPOKES,
            Layout.STEALTH_HALLS, Layout.SPIRAL_TOWER, Layout.OPEN_CAVERN, Layout.SHAFT};

    static void register() {
        registerGauntlet();
        registerTrial();
        dev.awaken.gates.run.SkyTrialTests.register();
        AwakenTests.register("gates_rig_collapse_run", 1200, h -> {
            int rooms = sweep(h, RigRoom.Kind.COLLAPSE_RUN, (rig) -> {
                RigCollapse.Run run = rig.run;
                h.assertTrue(RigCollapse.solve(run, true) >= 6 && RigCollapse.solve(run, false) >= 6,
                        "a runner a little slower than a sprint makes every Collapse Run both ways (slack " + run.slack() + ")");
            });
            h.assertTrue(rooms >= SEEDS / 3, "Collapse Runs are common on their themes' floors: " + rooms + " in " + SEEDS + " gates");
            live(h, Theme.EMBER_MINES, RigRoom.Kind.COLLAPSE_RUN, (level, g, f, r, p) -> {
                RigCollapse.Run run = r.rig.run;
                double[] start = run.rowCentre(2);
                BlockPos o = GateService.world(g, f, 0, r.dy, 0);
                p.teleportTo(level, o.getX() + start[0], o.getY() + 0.05, o.getZ() + start[1], Set.of(), 0, 0, true);
                RigDirector.tickNow(level.getServer());
                RigState.Room st = RigDirector.state(g, f, r);
                h.assertTrue(st.started.containsKey(0), "stepping off the landing starts the run");
                // play the whole wave at once: every row the wave drops is gone, the landings stay
                long now = GateService.now(level.getServer());
                st.started.put(0, now - run.fallAt(run.rows() - 3) - 1);
                RigDirector.tickNow(level.getServer());
                RigRoom.Group row = firstWave(r.rig);
                RigRoom.Cell c = row.cells().getFirst();
                h.assertTrue(level.getBlockState(GateService.world(g, f, c.x(), r.dy + c.dy(), c.z())).isAir(), "a row of the causeway fell");
                h.assertTrue(st.applied.contains(row.id()), "the fall is saved in the floor's rig state");
                // the room empties: it knits back exactly as it was painted
                p.teleportTo(level, o.getX() + f.room(f.start).centerX(), o.getY() + 1, o.getZ() + f.room(f.start).centerZ(), Set.of(), 0, 0, true);
                RigDirector.tickNow(level.getServer());
                RigDirector.settleAll(level);
                h.assertTrue(level.getBlockState(GateService.world(g, f, c.x(), r.dy + c.dy(), c.z())) == c.rest(), "the causeway knits back");
                h.assertTrue(st.started.isEmpty() && st.applied.isEmpty(), "and the room is at rest again, applied " + st.applied);
                // the Collapse Timer's ceiling: a real block comes down where you stand
                Room big = GateLiveTests.biggestCombat(f);
                GateLiveTests.put(level, p, g, f, big);
                BlockPos head = p.blockPosition();
                int above = RoomGeometry.ceiling(big, head.getX() - o.getX(), head.getZ() - o.getZ());
                BlockPos ceil = GateService.world(g, f, head.getX() - o.getX(), big.dy + above, head.getZ() - o.getZ());
                h.assertFalse(level.getBlockState(ceil).isAir(), "a ceiling over you");
                h.assertTrue(RigDirector.caveInNow(level, g, f, big, p) > 0, "the cave-in drops blocks");
                h.assertTrue(level.getBlockState(ceil).isAir(), "the ceiling over you really came down");
                h.assertTrue(g.floor(f.area, f.floor).rig.caveWaves == 1, "the wave is counted on the floor");
            });
            h.succeed();
        });

        AwakenTests.register("gates_rig_mirror_hall", 1200, h -> {
            int[] chambers = {0};
            int rooms = sweep(h, RigRoom.Kind.MIRROR_HALL, (rig) -> {
                RigMirrors.Puzzle pz = rig.mirrors;
                h.assertTrue(pz.solvedBy(pz.solution()), "the mirrors' solution routes the light to the seal");
                h.assertFalse(pz.solvedBy(pz.starts()), "a hall never starts solved");
                h.assertTrue(pz.turns() >= 2, "at least two mirrors to turn, was " + pz.turns());
                if (pz.chamber() != null) chambers[0]++;
            });
            h.assertTrue(rooms >= SEEDS / 3, "Mirror Halls are common on their themes' floors: " + rooms);
            h.assertTrue(chambers[0] > 0, "some halls hide a pavilion for the spirit");
            live(h, Theme.MIRROR_PALACE, RigRoom.Kind.MIRROR_HALL, (level, g, f, r, p) -> {
                RigMirrors.Puzzle pz = r.rig.mirrors;
                GateLiveTests.put(level, p, g, f, r);
                // the painted hall: every mirror at its start, the way on barred
                RigMirrors.Mirror m0 = pz.mirrors().getFirst();
                BlockPos at = GateService.world(g, f, m0.x(), r.dy + RigMirrors.BEAM_DY, m0.z());
                h.assertTrue(level.getBlockState(at).is(GatesRegistry.RIG_MIRROR.get()), "a mirror stands on its pedestal");
                Interactions.turnMirror(p, at);
                BlockState turned = level.getBlockState(at);
                h.assertTrue(turned.getValue(RigBlocks.Mirror.TURN) == (m0.start() + 1) % 4 && turned.getValue(RigBlocks.Mirror.TURNING),
                        "using a mirror swings it a quarter (hidden while the client draws the swing)");
                RigDirector.settleAll(level);
                h.assertFalse(level.getBlockState(at).getValue(RigBlocks.Mirror.TURNING), "and it settles");
                RigRoom.Cell bar = r.rig.group(1).cells().getFirst();
                BlockPos barPos = GateService.world(g, f, bar.x(), r.dy + bar.dy(), bar.z());
                h.assertTrue(level.getBlockState(barPos).is(GatesRegistry.BARS.get()), "the way on is barred with light");
                for (RigMirrors.Mirror m : pz.mirrors()) {
                    level.setBlock(GateService.world(g, f, m.x(), r.dy + RigMirrors.BEAM_DY, m.z()), RigRooms.mirror(m.solution()), Block.UPDATE_CLIENTS);
                }
                RigDirector.tickNow(level.getServer());
                RigState.Room st = RigDirector.state(g, f, r);
                h.assertTrue(st.latched.contains(0), "the light reaches the seal: it latches");
                st.started.put(0, GateService.now(level.getServer()) - 40);
                RigDirector.tickNow(level.getServer());
                h.assertTrue(level.getBlockState(barPos).isAir(), "and the bars fall");
                BlockState seal = level.getBlockState(GateService.world(g, f, pz.sealX(), r.dy + RigMirrors.BEAM_DY, pz.sealZ()));
                h.assertTrue(seal.is(GatesRegistry.RIG_SEAL.get()) && seal.getValue(RigBlocks.Seal.LIT), "the seal burns");
            });
            h.succeed();
        });

        AwakenTests.register("gates_rig_gravity_wells", 1200, h -> {
            int rooms = sweep(h, RigRoom.Kind.GRAVITY_WELLS, (rig) -> {
                RigWells.Field w = rig.wells;
                h.assertTrue(RigWells.solvable(w), "the wells carry you over every chasm");
                h.assertFalse(RigWells.reachableWithoutWells(w), "no chasm can be jumped: the wells are the way");
            });
            h.assertTrue(rooms >= SEEDS / 4, "Gravity Wells are common on their themes' floors: " + rooms);
            live(h, Theme.SKY_ARCHIPELAGO, RigRoom.Kind.GRAVITY_WELLS, (level, g, f, r, p) -> {
                RigRoom.Box b = r.rig.wells.box(0);
                BlockPos o = GateService.world(g, f, 0, r.dy, 0);
                p.teleportTo(level, o.getX() + (b.x0() + b.x1()) / 2.0 + 0.5, o.getY() + 2, o.getZ() + (b.z0() + b.z1()) / 2.0 + 0.5, Set.of(), 0, 0, true);
                RigDirector.tickNow(level.getServer());
                h.assertTrue(RigWellsLive.upsideDown(p) && p.getAttributeValue(Attributes.GRAVITY) < 0, "in a well you fall up, gravity " + p.getAttributeValue(Attributes.GRAVITY));
                h.assertTrue(p.getEyeHeight() < 1.0F, "upside down, your eyes hang below the ceiling: eye height " + p.getEyeHeight());
                double[] ledge = r.rig.wells.alongX() ? new double[]{r.rig.wells.a0() + 0.5, r.centerZ()} : new double[]{r.centerX(), r.rig.wells.a0() + 0.5};
                p.teleportTo(level, o.getX() + ledge[0], o.getY() + 0.05, o.getZ() + ledge[1], Set.of(), 0, 0, true);
                RigDirector.tickNow(level.getServer());
                h.assertFalse(RigWellsLive.upsideDown(p), "out of the well, down is down again");
                h.assertTrue(p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) > 20, "and you land softly");
                h.assertTrue(p.getEyeHeight() > 1.4F, "eyes back where they were");
            });
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_rig_protection", 200, h -> {
            ServerLevel level = h.getLevel();
            GatePlan plan = GateLiveTests.plan(818283, Theme.MIRROR_PALACE, Layout.LABYRINTH, Twist.NONE, 0);
            GateService.testLevel = level;
            GateRecord g = null;
            AwakenTests.Live live = null;
            try {
                g = GateLiveTests.standUp(h, plan, 0, 1);
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room start = f.room(f.start);
                live = AwakenTests.survivalPlayer(h, "Rigger", BlockPos.ZERO);
                ServerPlayer p = live.player();
                // a block of the Kit's dressing, not of the palette: protected too
                BlockPos wall = kitBlock(level, g, f, plan.theme());
                h.assertTrue(wall != null, "the floor has dressing beyond its palette");
                BlockState painted = level.getBlockState(wall);
                BreakBlockEvent br = NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, wall, painted, p));
                h.assertTrue(br.isCanceled(), "no breaking what the gate painted: " + painted);
                BlockPos air = GateService.world(g, f, start.centerX() + 2, start.dy + 1, start.centerZ() + 2);
                level.setBlock(air, level.registryAccess().getOrThrow(GateEvents.BREAKABLE).get(0).value().defaultBlockState(), Block.UPDATE_CLIENTS);
                BreakBlockEvent own = NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, air, level.getBlockState(air), p));
                h.assertFalse(own.isCanceled(), "a power's construct can be broken");
                level.setBlock(air, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(air, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                var place = NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, air),
                        level.getBlockState(air.below()), p));
                h.assertTrue(place.isCanceled(), "no placing blocks inside a gate");
                level.setBlock(air, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            } finally {
                if (live != null) live.leave();
                GateLiveTests.teardown(level, null, g);
            }
            h.succeed();
        });
    }

    /**
     * The Gauntlet (content version 5): every course of 1,000 seeded gates is run by the check's runner with a margin, all
     * four obstacles turn up, version 4 builds the two themes without one and every other theme exactly as version 5;
     * then a course run for real: the clock starts off the landing, a ledge falls under you, a press lands on you and
     * throws you back, the pit throws you back, the far landing stops the clock and keeps the run as your ghost.
     */
    static void registerGauntlet() {
        AwakenTests.register("gates_rig_gauntlet", 1200, h -> {
            int[] seen = new int[4];
            int rooms = sweep(h, RigRoom.Kind.GAUNTLET, (rig) -> {
                RigGauntlet.Course c = rig.course;
                int[] s = RigGauntlet.solve(c);
                h.assertTrue(s[0] >= 3 && s[1] == c.par(), "a runner a little slower than a sprint runs every course (slack " + s[0] + ")");
                h.assertTrue(c.ledges() + c.presses() + c.runs() + c.gaps() >= 2, "a course has two obstacles at least");
                if (c.ledges() > 0) seen[0]++;
                if (c.presses() > 0) seen[1]++;
                if (c.runs() > 0) seen[2]++;
                if (c.gaps() > 0) seen[3]++;
            });
            // only the long rooms (two slots) hold a course worth a clock: about one gate in five
            h.assertTrue(rooms >= SEEDS / 8, "Gauntlets turn up on their themes' floors: " + rooms + " in " + SEEDS + " gates");
            h.assertTrue(seen[0] > 0 && seen[1] > 0 && seen[2] > 0 && seen[3] > 0, "ledges, presses, wall-runs and gaps all turn up: "
                    + java.util.Arrays.toString(seen));
            for (int i = 0; i < 60; i++) {
                Theme t = i % 3 == 0 ? Theme.CLOCKWORK_FOUNDRY : i % 3 == 1 ? Theme.FROST_MONASTERY : Theme.EMBER_MINES;
                GatePlan v5 = new GatePlan(0x6A11L + i * 104729L, GatePlan.CONTENT_VERSION, Rank.values()[1 + i % 5], t, Layout.LABYRINTH,
                        List.of(), Twist.NONE, 3, t.bossId(), Theme.HIVE, 0);
                GateLayout a = GateTestAccess.build(v5.rebuilt(4)), b = GateTestAccess.build(v5);
                for (int fi = 0; fi < a.main.size(); fi++) {
                    for (int ri = 0; ri < a.main.get(fi).rooms.size(); ri++) {
                        Room ra = a.main.get(fi).rooms.get(ri), rb = b.main.get(fi).rooms.get(ri);
                        if (t != Theme.EMBER_MINES) h.assertTrue(ra.rig == null, "version 4 has no Gauntlet");
                        else h.assertTrue((ra.rig == null) == (rb.rig == null) && (ra.rig == null || ra.rig.signature() == rb.rig.signature()),
                                "every other theme builds as version 4 did");
                    }
                }
            }
            live(h, Theme.CLOCKWORK_FOUNDRY, RigRoom.Kind.GAUNTLET, rig -> rig.course.ledges() > 0 && rig.course.presses() > 0, (level, g, f, r, p) -> {
                RigGauntlet.Course c = r.rig.course;
                BlockPos o = GateService.world(g, f, 0, r.dy, 0);
                Consumer<Integer> at = k -> {
                    double[] q = c.rowCentre(k);
                    p.teleportTo(level, o.getX() + q[0], o.getY() + 0.05, o.getZ() + q[1], Set.of(), 0, 0, true);
                    RigDirector.tickNow(level.getServer());
                };
                at.accept(0);
                at.accept(c.first());
                h.assertTrue(RigDirector.racing(g, f, r, p), "off the landing the clock starts");
                RigState.Room st = RigDirector.state(g, f, r);
                h.assertTrue(st.started.containsKey(0), "and the presses with it");
                // a ledge: stood on, it cracks; LEDGE_FALL later it falls
                RigRoom.Cell lc = r.rig.group(0).cells().getFirst();
                at.accept(c.rowAt(lc.x(), lc.z()));
                int ledgeTrack = 1;
                h.assertTrue(st.started.containsKey(ledgeTrack), "a ledge cracks when you land on it");
                st.started.put(ledgeTrack, GateService.now(level.getServer()) - RigGauntlet.LEDGE_FALL - 1);
                RigDirector.tickNow(level.getServer());
                h.assertTrue(level.getBlockState(GateService.world(g, f, lc.x(), r.dy + lc.dy(), lc.z())).isAir(), "and falls");
                // a press: stood under as it comes down, it lands and throws you back to the start (a fresh run: no ring yet)
                at.accept(0);
                at.accept(c.first());
                RigRoom.Group press = r.rig.group(c.ledges());
                RigRoom.Cell pc = press.cells().getFirst();
                p.teleportTo(level, o.getX() + pc.x() + 0.5, o.getY() + 0.05, o.getZ() + pc.z() + 0.5, Set.of(), 0, 0, true);
                st.started.put(0, GateService.now(level.getServer()) - c.pressAt(0) - 1);
                RigDirector.tickNow(level.getServer());
                h.assertTrue(st.applied.contains(press.id()), "the press comes down on its beat");
                p.teleportTo(level, o.getX() + pc.x() + 0.5, o.getY() + 0.05, o.getZ() + pc.z() + 0.5, Set.of(), 0, 0, true);
                RigDirector.settleAll(level);
                h.assertTrue(level.getBlockState(GateService.world(g, f, pc.x(), r.dy + pc.dy(), pc.z())).is(GatesRegistry.PRESS.get()), "it lands");
                double[] start = c.rowCentre(0);
                h.assertTrue(Math.abs(p.getX() - (o.getX() + start[0])) < 1 && Math.abs(p.getZ() - (o.getZ() + start[1])) < 1,
                        "and throws whoever was under it back to the start");
                // the pit: a few ticks in it and it throws you back too
                at.accept(0);
                at.accept(c.first());
                double[] mid = c.rowCentre(c.first() + 1);
                p.teleportTo(level, o.getX() + mid[0], o.getY() - 3, o.getZ() + mid[1], Set.of(), 0, 0, true);
                for (int i = 0; i < 4; i++) RigDirector.tickNow(level.getServer());
                h.assertTrue(p.getY() > o.getY() - 1, "the pit throws you back out");
                // the far landing stops the clock
                at.accept(0);
                at.accept(c.first());
                h.assertTrue(RigDirector.racing(g, f, r, p), "a new run starts off the landing");
                at.accept(c.last());
                h.assertFalse(RigDirector.racing(g, f, r, p), "the far landing stops the clock");
                h.assertTrue(dev.awaken.progression.Progression.profile(p).counter("awaken:gauntlet_runs") == 1, "a run counted");
                h.assertTrue(RigDirector.ghostTicks(g, f, r, p) >= 0, "and kept as your ghost on this course");
            });
            h.succeed();
        });
    }

    /**
     * The Leaper's Gauntlet (SPEC §17.10): its plan builds one floor whose hall is a straight course ending in a chasm no
     * sprint jump crosses (only the leap), the same every time and passed by the check's runner with the leap; run for
     * real, its edge gives the leap and its far landing opens the way out (the gate is done).
     */
    static void registerTrial() {
        AwakenTests.register("gates_leaper_trial", 600, h -> {
            GatePlan plan = Gates.leaperTrialPlan(0x1EAFL);
            h.assertTrue(plan.leaperTrial() && !plan.ritual() && plan.floors() == 1, "the trial is one floor of its own");
            FloorLayout f = GateLayout.of(plan).floor(0, 1);
            Room hall = null;
            for (Room r : f.rooms) if (r.rig != null) hall = r;
            h.assertTrue(f.rooms.size() == 3 && hall != null && hall.rig.kind == RigRoom.Kind.GAUNTLET && hall.rig.course.trial(),
                    "the antechamber, the course and the room past it");
            RigGauntlet.Course c = hall.rig.course;
            h.assertTrue(c.chasm() == RigGauntlet.LEAP && RigGauntlet.LEAP > 3 && c.presses() > 0 && c.ledges() > 0 && c.runs() > 0
                    && c.rings().length >= 2, "every obstacle, two rings, and a chasm wider than any sprint jump (" + c.chasm() + ")");
            h.assertTrue(RigGauntlet.solve(c)[0] >= 3, "the check's runner makes it with the leap");
            h.assertTrue(GateTestAccess.build(plan).main.getFirst().room(hall.id).rig.signature() == hall.rig.signature(), "the same every time");
            ServerLevel level = h.getLevel();
            GateService.testLevel = level;
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                g = GateLiveTests.standUp(h, plan, 0, 1);
                // as walking in would
                g.transition(GateState.ACTIVE);
                p = GateLiveTests.player(h);
                Room r = hall;
                ServerPlayer pl = p;
                GateRecord gr = g;
                BlockPos o = GateService.world(g, f, 0, r.dy, 0);
                Consumer<Integer> at = k -> {
                    double[] q = c.rowCentre(k);
                    pl.teleportTo(level, o.getX() + q[0], o.getY() + 0.05, o.getZ() + q[1], Set.of(), 0, 0, true);
                    RigDirector.tickNow(level.getServer());
                };
                at.accept(0);
                at.accept(c.first());
                h.assertFalse(dev.awaken.body.ascent.Ascent.has(p, dev.awaken.body.ascent.Ascent.Tier.LEAPER), "no leap yet");
                at.accept(c.edge());
                h.assertTrue(dev.awaken.body.ascent.Ascent.has(p, dev.awaken.body.ascent.Ascent.Tier.LEAPER), "at the chasm's edge the legs remember the air");
                at.accept(c.last());
                h.assertTrue(gr.bossDead && gr.state == GateState.CLEARED, "across, the trial is done");
                Room past = f.room(f.end);
                boolean portal = false;
                for (int x = past.x0(); x <= past.x1() && !portal; x++) for (int z = past.z0(); z <= past.z1() && !portal; z++) {
                    if (level.getBlockState(GateService.world(g, f, x, past.dy, z)).is(GatesRegistry.EXIT.get())) portal = true;
                }
                h.assertTrue(portal, "and the way out opens past the course");
            } finally {
                GateLiveTests.teardown(level, p, g);
                RigDirector.resetAll();
            }
            h.succeed();
        });
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /**
     * Builds {@link #SEEDS} plans on the kind's themes (all ranks D to S, every layout), runs {@code check} on every rig
     * room of the kind, and checks determinism: a second build gives the same rig in the same rooms, version 3 none.
     */
    private static int sweep(GameTestHelper h, RigRoom.Kind kind, Consumer<RigRoom> check) {
        List<Theme> themes = new ArrayList<>();
        for (Theme t : Theme.values()) if (RigRooms.kinds(t).contains(kind)) themes.add(t);
        int found = 0;
        for (int i = 0; i < SEEDS; i++) {
            Theme t = themes.get(i % themes.size());
            GatePlan plan = new GatePlan(0xA11CEL + i * 7919L + kind.ordinal() * 104729L, GatePlan.CONTENT_VERSION, Rank.values()[1 + i % 5], t,
                    LAYOUTS[(i / themes.size()) % LAYOUTS.length], List.of(), Twist.NONE, 2 + i % 3, t.bossId(), Theme.HIVE, 0);
            GateLayout a = GateTestAccess.build(plan), b = GateTestAccess.build(plan);
            for (int fi = 0; fi < a.main.size(); fi++) {
                FloorLayout fa = a.main.get(fi), fb = b.main.get(fi);
                for (int ri = 0; ri < fa.rooms.size(); ri++) {
                    Room ra = fa.rooms.get(ri), rb = fb.rooms.get(ri);
                    h.assertTrue((ra.rig == null) == (rb.rig == null) && (ra.rig == null || ra.rig.signature() == rb.rig.signature()),
                            "the same plan builds the same rig (seed " + plan.seed() + ")");
                    if (ra.rig == null) continue;
                    if (ra.rig.kind == kind) {
                        check.accept(ra.rig);
                        found++;
                    }
                }
            }
            if (i % 50 == 0) {
                for (FloorLayout f : GateTestAccess.build(plan.rebuilt(3)).main) {
                    for (Room r : f.rooms) h.assertTrue(r.rig == null, "version 3 gates have no rig rooms");
                }
            }
        }
        return found;
    }

    private interface LiveCheck {
        void run(ServerLevel level, GateRecord g, FloorLayout f, Room r, ServerPlayer p);
    }

    /** A gate of the theme whose first floor has a rig room of the kind, stood up in the test level with a player. */
    private static void live(GameTestHelper h, Theme theme, RigRoom.Kind kind, LiveCheck check) {
        live(h, theme, kind, rig -> true, check);
    }

    /** As above, the rig room also passing {@code want}. */
    private static void live(GameTestHelper h, Theme theme, RigRoom.Kind kind, java.util.function.Predicate<RigRoom> want, LiveCheck check) {
        GatePlan plan = null;
        for (int i = 0; i < 400 && plan == null; i++) {
            GatePlan p = GateLiveTests.plan(0x51600L + i * 31L, theme, Layout.LABYRINTH, Twist.NONE, 0);
            FloorLayout f = GateLayout.of(p).floor(0, 1);
            for (Room r : f.rooms) if (r.rig != null && r.rig.kind == kind && want.test(r.rig)) plan = p;
        }
        h.assertTrue(plan != null, "a " + theme + " gate with a " + kind + " on its first floor");
        ServerLevel level = h.getLevel();
        GateService.testLevel = level;
        GateRecord g = null;
        ServerPlayer p = null;
        try {
            g = GateLiveTests.standUp(h, plan, 0, 1);
            FloorLayout f = GateLayout.of(plan).floor(0, 1);
            Room r = null;
            for (Room o : f.rooms) if (o.rig != null && o.rig.kind == kind && want.test(o.rig)) r = o;
            p = GateLiveTests.player(h);
            check.run(level, g, f, r, p);
        } finally {
            GateLiveTests.teardown(level, p, g);
            RigDirector.resetAll();
        }
    }

    /** A painted block of the floor that isn't one of its theme's palette blocks (a chandelier, a capital, a sconce). */
    private static BlockPos kitBlock(ServerLevel level, GateRecord g, FloorLayout f, Theme t) {
        var p = t.palette();
        Set<Block> palette = new java.util.HashSet<>();
        for (BlockState s : List.of(p.fill(), p.floor(), p.floorAlt(), p.wall(), p.wallAlt(), p.pillar(), p.ceiling(), p.stair(), p.light(), p.hang(), p.carpet())) palette.add(s.getBlock());
        for (Room r : f.rooms) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    for (int dy = 0; dy < r.height; dy++) {
                        BlockPos q = GateService.world(g, f, x, r.dy + dy, z);
                        BlockState s = level.getBlockState(q);
                        if (!s.isAir() && !palette.contains(s.getBlock()) && !s.is(Blocks.LIGHT) && s.getFluidState().isEmpty()) return q;
                    }
                }
            }
        }
        return null;
    }

    private static RigRoom.Group firstWave(RigRoom rig) {
        for (RigRoom.Beat b : rig.track(0).beats()) if (b.cue() == RigRoom.Cue.APPLY && !rig.group(b.group()).cells().isEmpty()) return rig.group(b.group());
        throw new IllegalStateException("no falling row");
    }
}
