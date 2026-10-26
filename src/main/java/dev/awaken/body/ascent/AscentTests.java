package dev.awaken.body.ascent;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTests for the Ascent's physics (plain functions: {@link AscentRules}). Phase 0 holds the rope, the swing's
 * energy, the glide's sink and dive and the flight's speeds to their numbers; later phases add the server checks.
 */
final class AscentTests {
    private AscentTests() {}

    static void register() {
        AwakenTests.register("ascent_rope_keeps_length", 20, AscentTests::ropeLength);
        AwakenTests.register("ascent_swing_energy", 20, AscentTests::swingEnergy);
        AwakenTests.register("ascent_glide_numbers", 20, AscentTests::glideNumbers);
        AwakenTests.register("ascent_soar_speeds", 20, AscentTests::soarSpeeds);
        AwakenTests.register("ascent_tiers_and_arrival", 40, AscentTests::tiersAndArrival);
        AwakenTests.register("ascent_chains_and_mastery", 40, AscentTests::chainsAndMastery);
        AwakenTests.registerIsolated("ascent_no_kicks", 60, AscentTests::noKicks);
        AwakenTests.registerIsolated("ascent_latency", 60, AscentTests::latency);
        AwakenTests.registerIsolated("ascent_tether_vow", 200, AscentTests::tetherVow);
        AwakenTests.register("ascent_aether", 40, AscentTests::aether);
        AwakenTests.registerIsolated("ascent_sky_rig", 100, AscentTests::skyRig);
        AwakenTests.registerIsolated("ascent_tether_spire", 100, AscentTests::tetherSpire);
        AwakenTests.register("ascent_windway", 20, AscentTests::windway);
        AwakenTests.register("ascent_slow_beat", 20, AscentTests::slowBeat);
        AwakenTests.register("ascent_hero_crowns", 100, AscentTests::heroCrowns);
        AwakenTests.register("ascent_aerial_kill", 40, AscentTests::aerialKill);
        AwakenTests.register("ascent_sovereign", 40, AscentTests::sovereign);
    }

    /** A taut rope holds the hand on its sphere through a long swing, whatever the input. */
    private static void ropeLength(GameTestHelper h) {
        AscentRules.Kin k = new AscentRules.Kin().set(-12, -6, 3, 0.4, 0, -0.2);
        double len = Math.sqrt(12 * 12 + 6 * 6 + 3 * 3);
        double[] s = new double[3];
        double worst = 0;
        for (int t = 0; t < 600; t++) {
            AscentRules.tetherStep(k, 0, 0, 0, len, t * 0.7F, t % 40 < 20 ? 1 : 0, t % 60 < 30 ? 0.5 : -0.5, s);
            double r = Math.sqrt(k.x * k.x + k.y * k.y + k.z * k.z);
            worst = Math.max(worst, r - len);
        }
        h.assertTrue(worst < 1e-6, "the rope stretched by " + worst);
        h.succeed();
    }

    /** Without drag a swing is a pendulum: dropped from level it comes back to level (within 5% of its length). */
    private static void swingEnergy(GameTestHelper h) {
        double len = 15;
        AscentRules.Kin k = new AscentRules.Kin().set(-len, 0, 0, 0, 0, 0);
        double[] s = new double[3];
        double top = -len, bottomSpeed = 0;
        // drag is part of the rule; measure the integrator by undoing it each tick
        double undo = 1 / (1 - AscentRules.TETHER_DRAG);
        for (int t = 0; t < 400; t++) {
            k.vx *= undo;
            k.vy *= undo;
            k.vz *= undo;
            AscentRules.tetherStep(k, 0, 0, 0, len, 0, 0, 0, s);
            if (t > 60) top = Math.max(top, k.y);
            bottomSpeed = Math.max(bottomSpeed, k.speed());
        }
        double ideal = Math.sqrt(2 * AscentRules.G * len);
        h.assertTrue(top > -0.05 * len, "a swing from level came back only to " + top);
        h.assertTrue(Math.abs(bottomSpeed - ideal) < 0.05 * ideal, "bottom speed " + bottomSpeed + ", the pendulum's " + ideal);
        h.succeed();
    }

    /** A shallow glide settles near 1.1-1.25 blocks a tick, a dive reaches the boom, a climb trades speed for height. */
    private static void glideNumbers(GameTestHelper h) {
        AscentRules.Kin k = new AscentRules.Kin().set(0, 0, 0, 0, -0.6, 0.4);
        for (int t = 0; t < 400; t++) AscentRules.glideStep(k, 0, 6);
        h.assertTrue(k.speed() > 1.0 && k.speed() < 1.35, "a 6 degree glide holds " + k.speed());
        double ratio = Math.abs(k.vz / k.vy);
        h.assertTrue(ratio > 8, "glide ratio " + ratio);
        AscentRules.Kin d = new AscentRules.Kin().set(0, 0, 0, 0, -0.1, 1.0);
        int boomAt = -1;
        for (int t = 0; t < 200 && boomAt < 0; t++) {
            AscentRules.glideStep(d, 0, 60);
            if (d.speed() >= AscentRules.BOOM_SPEED) boomAt = t;
        }
        h.assertTrue(boomAt > 0 && -d.y < 60, "a 60 degree dive booms within 60 blocks (at tick " + boomAt + ", " + -d.y + " down)");
        // an updraft (moving air, 0.12 up) under open wings turns the sink into a climb
        AscentRules.Kin u = new AscentRules.Kin().set(0, 0, 0, 0, -0.1, 1.1);
        double[] lift = {0, Updrafts.LIFT, 0};
        double uy0 = u.y;
        for (int t = 0; t < 100; t++) {
            AscentRules.glideStep(u, 0, 6);
            AscentRules.air(u, lift, AscentRules.AIR_GLIDE);
        }
        h.assertTrue(u.y > uy0 + 8 && u.y < uy0 + 22, "a glide in a vent's heart climbs a thermal's 2-4 blocks a second ("
                + (u.y - uy0) + " in 5 s, " + u.speed() + " b/t)");
        // an updraft vent's column: full lift near the vent, less at the rim, fading out above FULL, none outside
        net.minecraft.core.BlockPos vent = new net.minecraft.core.BlockPos(0, 64, 0);
        double heart = Updrafts.liftOf(vent, 0.5, 70, 0.5), rim = Updrafts.liftOf(vent, 0.5 + Updrafts.RADIUS - 0.1, 70, 0.5);
        h.assertTrue(Math.abs(heart - Updrafts.LIFT) < 1e-9 && rim > heart * 0.6 && rim < heart * 0.7, "heart " + heart + ", rim " + rim);
        h.assertTrue(Updrafts.liftOf(vent, 0.5, 65 + Updrafts.HEIGHT - 2, 0.5) < heart * 0.25 && Updrafts.liftOf(vent, 0.5, 66 + Updrafts.HEIGHT, 0.5) == 0
                && Updrafts.liftOf(vent, 4.6, 70, 0.5) == 0, "the column ends at its top and its rim");
        Updrafts.add(h.getLevel(), vent);
        double[] air = new double[3];
        try {
            h.assertTrue(dev.awaken.core.api.AirHooks.sample(h.getLevel(), 0.5, 70, 0.5, air) && Math.abs(air[1] - Updrafts.LIFT) < 1e-9,
                    "AirHooks hears the vent: " + air[1]);
        } finally {
            Updrafts.remove(h.getLevel(), vent);
        }
        h.assertFalse(dev.awaken.core.api.AirHooks.sample(h.getLevel(), 0.5, 70, 0.5, air), "and not once it's gone");
        AscentRules.Kin c = new AscentRules.Kin().set(0, 0, 0, 0, 0, 2.0);
        double y0 = c.y;
        for (int t = 0; t < 30; t++) AscentRules.glideStep(c, 0, -40);
        h.assertTrue(c.y - y0 > 10 && c.speed() < 1.6, "pulling up from 2.0 climbed " + (c.y - y0) + " and kept " + c.speed());
        h.succeed();
    }

    /**
     * The API: a grant brings every tier under it, once, and listeners hear each new one; arriving "by movement" needs
     * a third of the last stretch in a movement state and no block placed (a tower never counts).
     */
    private static void tiersAndArrival(GameTestHelper h) {
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        java.util.List<Ascent.Tier> heard = new java.util.ArrayList<>();
        Ascent.onGranted((pl, t) -> {
            if (pl == p) heard.add(t);
        });
        h.assertTrue(Ascent.highest(p) == null, "a Runner holds no tier");
        h.assertTrue(Ascent.grant(p, Ascent.Tier.WINGS, "test"), "wings granted");
        h.assertTrue(Ascent.has(p, Ascent.Tier.TETHER) && Ascent.has(p, Ascent.Tier.LEAPER) && !Ascent.has(p, Ascent.Tier.SKYBORN),
                "wings bring the tiers under them, not above");
        h.assertTrue(heard.equals(java.util.List.of(Ascent.Tier.TETHER, Ascent.Tier.LEAPER, Ascent.Tier.WINGS)), "heard each once: " + heard);
        h.assertFalse(Ascent.grant(p, Ascent.Tier.LEAPER, "test"), "nothing new");
        AscentMotion m = AscentServer.motion(p);
        for (int i = 0; i < 300; i++) m.mark(i % 2 == 0);
        h.assertTrue(Ascent.arrivedByMovement(p, 10), "half the time swinging is arriving by movement");
        m.placedAt = AscentServer.now(p);
        h.assertFalse(Ascent.arrivedByMovement(p, 10), "a block placed: a tower, not a route");
        m.placedAt = Long.MIN_VALUE / 2;
        for (int i = 0; i < 300; i++) m.mark(i % 5 == 0);
        h.assertFalse(Ascent.arrivedByMovement(p, 10), "a fifth of the time is a walk");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /**
     * Mastery levels at 25 n² points (capped at 10); a chain of three or more links pays style XP, at most 1.5% of a
     * level a minute, and the longest chain is kept; a record without mastery (schema 1) reads as none.
     */
    private static void chainsAndMastery(GameTestHelper h) {
        h.assertTrue(AscentMemory.level(24) == 0 && AscentMemory.level(25) == 1 && AscentMemory.level(2499) == 9
                && AscentMemory.level(99999) == 10, "levels at 25 n squared");
        AscentMemory old = AscentMemory.MAP_CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"schemaVersion\":1,\"tiers\":3}")).getOrThrow();
        h.assertTrue(old.tiers() == 3 && old.level(AscentMemory.Track.TETHER) == 0 && old.chainBest() == 0, "schema 1 reads: " + old);
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        for (int i = 0; i < 30; i++) AscentServer.link(p, AscentMemory.Track.TETHER, 2);
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        h.assertTrue(mem.points(AscentMemory.Track.TETHER) == 60 && mem.level(AscentMemory.Track.TETHER) == 1,
                "thirty links on the rope: " + mem.points(AscentMemory.Track.TETHER));
        AscentMotion m = AscentServer.motion(p);
        double before = dev.awaken.progression.Progression.profile(p).xp();
        int levelBefore = dev.awaken.progression.Progression.profile(p).level();
        AscentServer.payChain(p, m, AscentServer.now(p));
        double toNext = dev.awaken.progression.Curve.xpToNext(levelBefore);
        double paid = dev.awaken.progression.Progression.profile(p).xp() - before;
        h.assertTrue(dev.awaken.progression.Progression.profile(p).level() == levelBefore && paid > 0
                && paid <= toNext * AscentServer.STYLE_CAP_MINUTE * 1.6, "a 30-link chain paid " + paid + " of " + toNext);
        h.assertTrue(p.getData(AscentContent.MEMORY).chainBest() == 30, "the longest chain is kept");
        for (int i = 0; i < 25; i++) AscentServer.link(p, AscentMemory.Track.RUNNER, 1);
        double mid = dev.awaken.progression.Progression.profile(p).xp();
        AscentServer.payChain(p, m, AscentServer.now(p));
        h.assertTrue(dev.awaken.progression.Progression.profile(p).xp() - mid <= 1e-6 + toNext * 0.0001,
                "the minute's style budget is spent: " + (dev.awaken.progression.Progression.profile(p).xp() - mid));
        h.assertTrue(p.getData(AscentContent.MEMORY).chainBest() == 30, "a shorter chain keeps the record");
        // an old world's first load: B gives Wings, A and up Skyborn, nothing before B or before awakening; once only
        h.assertTrue(AscentServer.remembered(true, dev.awaken.progression.Rank.C) == null
                && AscentServer.remembered(true, dev.awaken.progression.Rank.B) == Ascent.Tier.WINGS
                && AscentServer.remembered(true, dev.awaken.progression.Rank.S) == Ascent.Tier.SKYBORN
                && AscentServer.remembered(false, dev.awaken.progression.Rank.A) == null, "the ranks' tiers");
        int first = AscentServer.settle(p);
        h.assertTrue(first == 0 && (p.getData(AscentContent.MEMORY).flags() & AscentMemory.SETTLED) != 0
                && AscentServer.settle(p) == 0, "an E-rank player is settled with nothing, once");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /** A survival player on a plain connection, so the server's real movement checks run on it (tests). */
    private record Flyer(net.minecraft.server.level.ServerPlayer p, net.minecraft.server.network.ServerGamePacketListenerImpl conn) {
        static Flyer at(GameTestHelper h, double x, double y, double z) {
            net.minecraft.server.level.ServerLevel level = h.getLevel();
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ascent-flyer"), false);
            net.minecraft.server.level.ServerPlayer p = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    cookie.gameProfile(), cookie.clientInformation()) {
                @Override
                public net.minecraft.world.level.GameType gameMode() {
                    return net.minecraft.world.level.GameType.SURVIVAL;
                }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
            var conn = p.connection;
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            conn.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            conn.teleport(x, y, z, 0, 0);
            conn.resetPosition(); // the check that runs on accepting a teleport measures from here
            for (int id = 0; id < 16; id++) {
                conn.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id, x, y, z, 0, 0));
            }
            conn.tick(); // the server takes the tick's starting point from where the body is
            h.assertTrue(p.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z)) < 1e-3, "the test's teleport was accepted");
            return new Flyer(p, conn);
        }

        /** One client tick's position; how far the server sent the body back from it (0: taken as sent). */
        double move(double x, double y, double z, boolean onGround) {
            conn.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(x, y, z, 0, 0, onGround, false));
            conn.handleClientTickEnd(net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.INSTANCE);
            return p.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
        }

        void remove() {
            p.level().getServer().getPlayerList().remove(p);
        }
    }

    /**
     * 0 kicks (the Phase 1 gate), through the server's real movement checks, which skip the singleplayer host (so tours
     * can't show it): a survival player flies an Ascent state for 15 s at 1.25, 2.25 and 3.2 blocks a tick (the top
     * speeds: flight cruise, boost, the cap), turning back and forth and rising and falling, one position a client tick
     * and, for the last third, in lag bursts of up to five client ticks at once. Not one move is sent back ("moved too
     * quickly" or "moved wrongly" snap the body to where it was) and the player stays connected. (The floating kick
     * can't fire here: only a dedicated server with allow-flight off has it. Every tick a state runs, AscentServer
     * resets the count before vanilla adds to it.)
     */
    private static void noKicks(GameTestHelper h) {
        net.minecraft.core.BlockPos base = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        // high over the test's own ground: real terrain where the test lands (CI's hills reached y 64) never in the way
        double x = base.getX() + 0.5, y = h.getLevel().getMaxY() - 40, z = base.getZ() + 0.5;
        Flyer f = Flyer.at(h, x, y, z);
        try {
            Ascent.grant(f.p, Ascent.Tier.SKYBORN, "test");
            AscentServer.onState(f.p, new AscentPayloads.State(AscentState.SOAR.ordinal(), 0, 0, 0, 0));
            h.assertTrue(AscentServer.motion(f.p).state == AscentState.SOAR, "the server took the flight");
            java.util.List<double[]> queued = new java.util.ArrayList<>();
            double dir = 1;
            for (int t = 0; t < 300; t++) {
                double speed = t < 80 ? 1.25 : t < 160 ? 2.25 : 3.2;
                if (t % 24 == 0) dir = -dir; // back and forth, so the flight stays over loaded ground
                double vy = 0.5 * Math.sin(t / 9.0);
                double h2 = Math.sqrt(Math.max(0, speed * speed - vy * vy));
                x += dir * h2;
                y += vy;
                queued.add(new double[]{x, y, z, t, speed});
                // the last third arrives in lag bursts: one to five client ticks' positions in one server tick
                int burst = t < 200 ? 1 : 1 + (t * 7) % 5;
                if (queued.size() < burst && t < 299) {
                    f.conn.tick();
                    continue;
                }
                for (double[] q : queued) {
                    double off = f.move(q[0], q[1], q[2], false);
                    h.assertTrue(off < 1e-3, String.format("sent back %.2f at tick %d, %.2f a tick, %d in the burst", off, (int) q[3], q[4], queued.size()));
                }
                queued.clear();
                f.conn.tick();
                h.assertFalse(f.p.hasDisconnected(), "kicked at tick " + t);
            }
        } finally {
            f.remove();
        }
        h.succeed();
    }

    /**
     * Latency (the Phase 1 gate's latency tours, as a test): a real swing (the rope's own physics from a block 20 up), a
     * release into the carry and a hard landing from high up, with every state message reaching the server 300 ms
     * (6 ticks) after the positions it belongs with. The server refuses nothing, sends no move back, takes no fall damage
     * from the landing, ends the state, and still counts the arrival as made by movement.
     */
    private static void latency(GameTestHelper h) {
        net.minecraft.core.BlockPos base = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        double x0 = base.getX() + 0.5, y0 = h.getLevel().getMaxY() - 40, z0 = base.getZ() + 0.5;
        net.minecraft.core.BlockPos block = net.minecraft.core.BlockPos.containing(x0, y0 + 22, z0 + 12);
        h.getLevel().setBlock(block, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        double ax = block.getX() + 0.5, ay = block.getY() - 0.05, az = block.getZ() + 0.5;
        Flyer f = Flyer.at(h, x0, y0, z0);
        int refusalsBefore = AscentServer.refusals;
        float health = f.p.getHealth();
        try {
            Ascent.grant(f.p, Ascent.Tier.TETHER, "test");
            final int lag = 6;
            java.util.List<Object[]> states = new java.util.ArrayList<>(); // {deliver at tick, state}
            AscentRules.Kin k = new AscentRules.Kin().set(x0, y0 + AscentRules.HAND_HEIGHT, z0, 0, 0.5, 0.1);
            double len = Math.sqrt((ax - k.x) * (ax - k.x) + (ay - k.y) * (ay - k.y) + (az - k.z) * (az - k.z));
            double[] scratch = new double[3];
            states.add(new Object[]{lag, AscentState.TETHER});
            int t = 0, released = -1, landed = -1;
            for (; t < 400 && landed < 0; t++) {
                double fx, fy, fz;
                boolean ground = false;
                if (released < 0) {
                    AscentRules.tetherStep(k, ax, ay, az, len, 0, 1, 0, scratch);
                    fx = k.x;
                    fy = k.y - AscentRules.HAND_HEIGHT;
                    fz = k.z;
                    if (t > 20 && k.vz > 0 && k.y > ay - len * 0.6) {
                        released = t;
                        k.y -= AscentRules.HAND_HEIGHT;
                        states.add(new Object[]{t + lag, AscentState.CARRY});
                    }
                } else {
                    AscentRules.carryStep(k, 0, 0, 0, scratch);
                    fx = k.x;
                    fy = k.y;
                    fz = k.z;
                    // the floor is 30 under the start: a long fall, landed hard
                    if (fy <= y0 - 30) {
                        fy = y0 - 30;
                        ground = true;
                        landed = t;
                        states.add(new Object[]{t + lag, AscentState.NONE});
                    }
                }
                double off = f.move(fx, fy, fz, ground);
                h.assertTrue(off < 1e-3, String.format("sent back %.2f at tick %d (%s)", off, t, released < 0 ? "swinging" : "flying"));
                deliver(f, states, t, ax, ay, az, len);
                f.conn.tick();
            }
            h.assertTrue(landed > 0 && released > 0, "the swing released (" + released + ") and landed (" + landed + ")");
            for (int end = t + lag + 1; t <= end; t++) {
                f.move(f.p.getX(), f.p.getY(), f.p.getZ(), true);
                deliver(f, states, t, ax, ay, az, len);
                f.conn.tick();
            }
            h.assertTrue(AscentServer.refusals == refusalsBefore, "the server refused " + (AscentServer.refusals - refusalsBefore) + " late states");
            h.assertTrue(AscentServer.motion(f.p).state == AscentState.NONE, "the late landing ended the state");
            h.assertTrue(f.p.getHealth() >= health, "a " + (y0 - 30 - f.p.getY()) + " landing hurt: " + health + " -> " + f.p.getHealth());
            h.assertTrue(Ascent.arrivedByMovement(f.p, 5), "the swing counts as arriving by movement");
            h.assertFalse(f.p.hasDisconnected(), "disconnected");
        } finally {
            f.remove();
            h.getLevel().setBlock(block, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        h.succeed();
    }

    /** Hands the server the state messages due by tick {@code t} (sent 6 ticks before). */
    private static void deliver(Flyer f, java.util.List<Object[]> states, int t, double ax, double ay, double az, double len) {
        for (var it = states.iterator(); it.hasNext(); ) {
            Object[] s = it.next();
            if ((int) s[0] > t) continue;
            AscentState st = (AscentState) s[1];
            AscentServer.onState(f.p, st.rope ? new AscentPayloads.State(st.ordinal(), ax, ay, az, (float) len)
                    : new AscentPayloads.State(st.ordinal(), 0, 0, 0, 0));
            it.remove();
        }
    }

    /**
     * The Tether Vow on a real vault built from its blueprint in each of the four turns: the turn is read back from the
     * pylons, the forecourt is where the blueprint says, the line's anchor on the pylon top is real to the server's check
     * and clear from the forecourt. Then the lock: under the door's rank (D at the least), or with the sealed door shut,
     * the vault only stirs and the door reads them; at D with the door open, the director: the call, the line, the mast.
     */
    private static void tetherVow(GameTestHelper h) {
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 1, 0)).above(120);
        var size = dev.awaken.above.ruin.RuinBuilder.size(dev.awaken.above.ruin.RuinKind.VAULT);
        var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(o.getX(), o.getY(), o.getZ(),
                o.getX() + size.x() - 1, o.getY() + size.y() - 1, o.getZ() + size.z() - 1);
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        try {
            for (int rot = 0; rot < 4; rot++) {
                TetherVow.Site site = new TetherVow.Site(box, rot, 1);
                wipe(level, box);
                dev.awaken.above.ruin.RuinBuilder.build(dev.awaken.above.ruin.RuinKind.VAULT, 4242L + rot, 1, new dev.awaken.above.ruin.RuinBuilder.Sink() {
                    @Override
                    public void set(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
                        level.setBlock(site.world(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }

                    @Override
                    public void fillDown(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state, int depth) {
                        level.setBlock(site.world(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }

                    @Override
                    public void chest(int x, int y, int z, net.minecraft.core.Direction facing,
                                      net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {
                        level.setBlock(site.world(x, y, z), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                });
                // dug into a hill: stone packed round the back corners above the roof (the first real spawn vault was)
                for (int x : new int[]{0, 1, 2, 3, 19, 20, 21, 22}) for (int z = 18; z <= 22; z++) for (int y = 9; y <= 16; y++) {
                    level.setBlock(site.world(x, y, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
                h.assertTrue(TetherVow.rotationOf(level, box) == rot, "turn " + rot + " read back as " + TetherVow.rotationOf(level, box));
                BlockPos fore = site.world(11, 1, 2);
                h.assertTrue(site.inForecourt(fore.getX() + 0.5, fore.getY(), fore.getZ() + 0.5)
                        && !site.inForecourt(site.world(11, 1, 8).getX() + 0.5, fore.getY(), site.world(11, 1, 8).getZ() + 0.5),
                        "the forecourt, not the antechamber, in turn " + rot);
                p.snapTo(fore.getX() + 0.5, fore.getY(), fore.getZ() + 0.5, 0, 0);
                double[] top = TetherVow.pylonTop(level, site, p.getX(), p.getZ());
                h.assertTrue(top != null && AscentServer.anchorReal(p, top[0], top[1], top[2]), "a real anchor on the pylon top in turn " + rot);
                h.assertTrue(AscentAnchors.clear(level, p, p.position().add(0, AscentRules.HAND_HEIGHT, 0), top[0], top[1], top[2]),
                        "the line to the pylon is clear in turn " + rot);
                h.assertTrue(TetherVow.atMast(site, site.mast().getX() + 0.5, box.minY() + 16, site.mast().getZ() + 1.5)
                        && !TetherVow.atMast(site, fore.getX() + 0.5, fore.getY(), fore.getZ() + 0.5), "the mast in turn " + rot);
            }
            // the lock (Euan, 2026-10-26): awake and without the rope, but E-rank, in the forecourt of a D-door vault
            dev.awaken.progression.Progression.awaken(p);
            h.assertTrue(TetherVow.eligible(p), "an awakened player without the rope may be offered the vow");
            TetherVow.Site site = new TetherVow.Site(box, 3, 1);
            BlockPos fore = site.world(11, 1, 2), door = site.door();
            p.snapTo(fore.getX() + 0.5, fore.getY(), fore.getZ() + 0.5, 0, 0);
            long now = AscentServer.now(p);
            h.assertTrue(site.needs() == dev.awaken.progression.Rank.D && !site.doorOpen(level), "a D door's line answers D, and its door is sealed");
            TetherVow.look(p, site, now);
            h.assertTrue(!TetherVow.running(p) && TetherVow.stirred(p) && !Ascent.has(p, Ascent.Tier.TETHER),
                    "under-ranked, the vault only stirs: no rope");
            // its sealed door reads them: shut, it says so, and it won't say it again at once on approach
            var core = level.getBlockState(door);
            h.assertTrue(core.is(dev.awaken.above.AboveBlocks.DOOR_CORE.get()) && !dev.awaken.above.block.DoorCoreBlock.canOpen(p, core),
                    "the sealed door is shut to E-rank");
            dev.awaken.above.block.DoorCoreBlock.forget(p.getUUID());
            h.assertTrue(dev.awaken.above.block.DoorCoreBlock.readOnApproach(p, level, door, core)
                    && !dev.awaken.above.block.DoorCoreBlock.readOnApproach(p, level, door, core), "the door reads them once, not every check");
            dev.awaken.above.block.DoorCoreBlock.tryOpen(p, level, door);
            h.assertFalse(site.doorOpen(level), "touched by E-rank, it stays shut");
            // D-rank: a C door's line still waits; the D door opens, and with it open the vault calls
            dev.awaken.progression.Progression.passReassessment(p);
            h.assertTrue(dev.awaken.progression.Progression.rank(p) == dev.awaken.progression.Rank.D, "D-rank");
            h.assertFalse(TetherVow.ready(p, new TetherVow.Site(box, 3, 2)), "a C door's line waits for C");
            TetherVow.look(p, site, now + 20);
            h.assertTrue(!TetherVow.running(p) && !Ascent.has(p, Ascent.Tier.TETHER), "at D with the door still sealed, the vault waits");
            dev.awaken.above.block.DoorCoreBlock.tryOpen(p, level, door);
            h.assertTrue(site.doorOpen(level), "D-rank opens a D door");
            TetherVow.look(p, site, now + 40);
            h.assertTrue(TetherVow.running(p), "at D, with the door open, the vault calls");
            now += 40;
            TetherVow.tick(p, now + TetherVow.CALL_TICKS - 1);
            h.assertFalse(Ascent.has(p, Ascent.Tier.TETHER), "the call comes before the line");
            TetherVow.tick(p, now + TetherVow.CALL_TICKS);
            h.assertTrue(Ascent.has(p, Ascent.Tier.TETHER) && !Ascent.has(p, Ascent.Tier.LEAPER), "the line gives the rope (only)");
            BlockPos m = site.mast();
            p.snapTo(m.getX() + 0.5, box.minY() + 16, m.getZ() + 0.5, 0, 0);
            // the mast keeps the vow only by the line (SPEC §17.25): a tower up to it waits
            dev.awaken.core.api.ArrivalHooks.Tracker arrival = dev.awaken.core.api.ArrivalHooks.installed();
            boolean[] line = {false};
            dev.awaken.core.api.ArrivalHooks.install(new dev.awaken.core.api.ArrivalHooks.Tracker() {
                @Override public boolean byLine(net.minecraft.server.level.ServerPlayer q) { return line[0]; }
            });
            try {
                TetherVow.tick(p, now + TetherVow.CALL_TICKS + 30);
                h.assertTrue(TetherVow.running(p), "at the mast without the line, the vow waits");
                line[0] = true;
                TetherVow.tick(p, now + TetherVow.CALL_TICKS + 40);
            } finally {
                dev.awaken.core.api.ArrivalHooks.install(arrival);
            }
            h.assertTrue(!TetherVow.running(p) && (p.getData(AscentContent.MEMORY).flags() & AscentMemory.VOW_KEPT) != 0, "the vow is kept at the mast");
            h.assertFalse(TetherVow.eligible(p), "and never offered again");
        } finally {
            TetherVow.forget(p);
            dev.awaken.above.block.DoorCoreBlock.forget(p.getUUID());
            wipe(level, box);
            level.getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    private static void wipe(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY() - 2, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            level.setBlock(q, air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
    }

    /**
     * Aether (SPEC §17.8): a minute of cruise, about 19 s of boost, 20 s to fill in the sun, an updraft filling it even
     * in flight, mastery trimming the drain; the server folds a flight it can't afford into a glide, and won't start one.
     */
    private static void aether(GameTestHelper h) {
        int cruise = ticksToEmpty(AscentRules.AETHER_CRUISE, 0), boost = ticksToEmpty(AscentRules.AETHER_BOOST, 0);
        h.assertTrue(Math.abs(cruise - 1200) <= 1 && Math.abs(boost - 375) <= 1, "cruise " + cruise + " ticks, boost " + boost);
        h.assertTrue(ticksToEmpty(AscentRules.AETHER_CRUISE, 10) > 1700, "ten Skyborn levels stretch a cruise past 85 s");
        double a = 0;
        int t = 0;
        while (a < 1 && t < 10000) {
            a = AscentRules.aetherStep(a, false, 0, true, false, 0);
            t++;
        }
        h.assertTrue(Math.abs(t - 400) <= 1, "fills in the sun in " + t + " ticks");
        h.assertTrue(AscentRules.aetherStep(0.5, true, AscentRules.AETHER_CRUISE, true, true, 0) > 0.5, "an updraft fills it in flight");
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        try {
            Ascent.grant(p, Ascent.Tier.SKYBORN, "test");
            AscentMotion m = AscentServer.motion(p);
            m.aether = AscentRules.AETHER_START - AscentServer.AETHER_TOLERANCE - 0.01;
            AscentServer.onState(p, new AscentPayloads.State(AscentState.SOAR.ordinal(), 0, 0, 0, 0));
            h.assertTrue(m.state != AscentState.SOAR, "no flight from a meter the server knows is empty (" + m.aether + ")");
            m.aether = 0.5;
            AscentServer.onState(p, new AscentPayloads.State(AscentState.SOAR.ordinal(), 0, 0, 0, 0));
            h.assertTrue(m.state == AscentState.SOAR, "flight with half a meter");
            m.aether = -AscentServer.AETHER_TOLERANCE - 0.01;
            AscentServer.aether(p, m);
            h.assertTrue(m.state == AscentState.GLIDE, "a flight past empty folds into a glide");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /**
     * A sky platform's ladder and core (SPEC §17.9): each ring a zip straight up from a hang under the one below (within
     * the rope's reach and its overhead slope), the first within reach of the ground, the last a zip onto the pier;
     * placed for real; the core gives wings to an arrival by movement and stays dark to a tower.
     */
    private static void skyRig(GameTestHelper h) {
        int ground = 70, deck = 276;
        var l = dev.awaken.above.ruin.SkyRig.layout(0, deck, 0, ground);
        var rings = l.rings();
        h.assertTrue(rings.size() >= 7, "a ladder of " + rings.size() + " rings");
        BlockPos bottom = rings.getLast();
        h.assertTrue(bottom.getY() - ground <= 30 && bottom.getY() - ground >= 20, "the first ring " + (bottom.getY() - ground) + " over the ground");
        for (int i = rings.size() - 1; i > 1; i--) {
            BlockPos lo = rings.get(i), hi = rings.get(i - 1);
            double handY = lo.getY() - AscentRules.CEILING_HANG, rise = hi.getY() - handY;
            double side = Math.hypot(hi.getX() - lo.getX(), hi.getZ() - lo.getZ());
            h.assertTrue(rise < AscentRules.TETHER_RANGE && rise > AscentRules.ZIP_UP_MIN_RISE && side < rise * AscentRules.ZIP_UP_SLOPE,
                    "ring " + i + " to " + (i - 1) + ": rise " + rise + ", side " + side);
        }
        // the last zip: standing on the perch (its eye 2.6 over the top ring) up to the pier end's side
        BlockPos top = rings.getFirst(), pierEnd = l.pier().getLast();
        h.assertTrue(l.perch().size() == 8, "a perch round the top ring");
        double rise = pierEnd.getY() + 0.5 - (top.getY() + 1 + 1.62), side = Math.hypot(pierEnd.getX() + 0.5 - (top.getX() + 0.5), pierEnd.getZ() + 1.05 - (top.getZ() + 0.5));
        h.assertTrue(rise > AscentRules.ZIP_UP_MIN_RISE && side < rise * AscentRules.ZIP_UP_SLOPE, "the last zip, onto the pier: rise " + rise + ", side " + side);
        // clear of the blueprint's islands (RuinBuilder.sky: on each side 19 out, up to 3 off the line, 3.8 wide and
        // 0.6 of noise; their tether chains run along that line back to the rim) in any turn of the blueprint
        java.util.List<BlockPos> rig = new java.util.ArrayList<>(rings);
        rig.addAll(l.perch());
        rig.addAll(l.pier());
        for (BlockPos q : rig) {
            for (int turn = 0; turn < 4; turn++) {
                for (int off = -3; off <= 3; off++) {
                    double ix = turn == 0 ? 19 : turn == 2 ? -19 : off, iz = turn == 1 ? 19 : turn == 3 ? -19 : off;
                    double clear = Math.hypot(q.getX() - ix, q.getZ() - iz) - 4.4;
                    boolean underTether = (turn % 2 == 0 ? Math.abs(q.getZ() - iz) : Math.abs(q.getX() - ix)) < 1.5
                            && (turn % 2 == 0 ? Math.abs(q.getX()) : Math.abs(q.getZ())) >= 10;
                    h.assertTrue(clear > 3 && !underTether, q.toShortString() + " is " + clear + " from an island's edge");
                }
            }
        }
        // placed for real, high over the test
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int cx = o.getX(), cz = o.getZ(), deckY = level.getMaxY() - 20;
        dev.awaken.above.ruin.SkyRig.rig(level, cx, deckY, cz);
        var placed = dev.awaken.above.ruin.SkyRig.layout(cx, deckY, cz, dev.awaken.above.ruin.SkyRig.groundUnder(level, cx, cz));
        h.assertTrue(level.getBlockState(placed.core()).is(AscentContent.WING_CORE.get())
                && level.getBlockState(placed.rings().getFirst()).is(AscentContent.ANCHOR_RING.get()), "the core and the rings are placed");
        // the core: by movement, wings; by a tower, nothing
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        var real = dev.awaken.core.api.ArrivalHooks.installed();
        try {
            boolean[] moved = {false};
            dev.awaken.core.api.ArrivalHooks.install(new dev.awaken.core.api.ArrivalHooks.Tracker() {
                @Override
                public boolean byMovement(net.minecraft.server.level.ServerPlayer pl) {
                    return moved[0];
                }
            });
            AscentMotion m = AscentServer.motion(p);
            for (int i = 0; i < 400; i++) m.mark(true);
            h.assertFalse(WingCoreBlockEntity.reached(p, placed.core()) || Ascent.has(p, Ascent.Tier.WINGS), "a tower gets nothing");
            moved[0] = true;
            h.assertFalse(WingCoreBlockEntity.reached(p, placed.core()) || Ascent.has(p, Ascent.Tier.WINGS), "the long way at E: the core waits for C");
            while (dev.awaken.progression.Progression.rank(p).ordinal() < WingCoreBlockEntity.RANK.ordinal()) dev.awaken.progression.Progression.passReassessment(p);
            h.assertTrue(WingCoreBlockEntity.reached(p, placed.core()) && Ascent.has(p, Ascent.Tier.WINGS), "the long way at C gets wings");
        } finally {
            dev.awaken.core.api.ArrivalHooks.install(real);
            level.getServer().getPlayerList().remove(p);
            for (BlockPos q : BlockPos.betweenClosed(cx - 1, placed.chainBottom() - 1, cz - 1, cx + 20, deckY + 2, cz + 20)) {
                if (!level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
        h.succeed();
    }

    /**
     * A Tether Spire (SPEC §17.11), built from its blueprint high over the test: every step of its ladder (the plaza to
     * the first platform, each platform to the next, the last to the crown's deck) is a zip straight up from the near
     * rim of the lower floor to the upper one's underside edge, and that edge can be vaulted over (a full block with room
     * on it, open beyond); the crown times a climb from the plaza, keeps the record, and answers no tower.
     */
    private static void tetherSpire(GameTestHelper h) {
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).atY(level.getMaxY() - 80);
        int size = dev.awaken.above.ruin.RuinBuilder.size(dev.awaken.above.ruin.RuinKind.SPIRE).y();
        try {
            dev.awaken.above.ruin.RuinBuilder.build(dev.awaken.above.ruin.RuinKind.SPIRE, 7L, 1, new dev.awaken.above.ruin.RuinBuilder.Sink() {
                @Override
                public void set(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
                    level.setBlock(o.offset(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }

                @Override
                public void fillDown(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state, int depth) {
                    level.setBlock(o.offset(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }

                @Override
                public void chest(int x, int y, int z, net.minecraft.core.Direction facing,
                                  net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {
                    level.setBlock(o.offset(x, y, z), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            });
            int C = dev.awaken.above.ruin.RuinBuilder.SPIRE_C, P = dev.awaken.above.ruin.RuinBuilder.SPIRE_PLAZA,
                    TOP = dev.awaken.above.ruin.RuinBuilder.SPIRE_CROWN;
            // the floors in climbing order: the plaza (any cell), each platform (its 3x3), the deck (5x5)
            java.util.List<int[]> floors = new java.util.ArrayList<>();
            floors.add(new int[]{C + 6, P, C, 0});
            for (int k = 0; k < dev.awaken.above.ruin.RuinBuilder.SPIRE_PETALS; k++) {
                int[] c = dev.awaken.above.ruin.RuinBuilder.spirePetal(k);
                floors.add(new int[]{c[0], c[1], c[2], 1});
            }
            floors.add(new int[]{C, TOP, C, 2});
            for (int i = 1; i < floors.size(); i++) {
                int[] lo = floors.get(i - 1), hi = floors.get(i);
                // the upper floor's rim cells; the lower floor's cell nearest each (where you'd stand to throw)
                double bestSide = Double.MAX_VALUE, rise = 0;
                for (int dx = -hi[3]; dx <= hi[3]; dx++) for (int dz = -hi[3]; dz <= hi[3]; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != hi[3] && hi[3] > 0) continue;
                    int tx = hi[0] + dx, tz = hi[2] + dz;
                    BlockPos t = o.offset(tx, hi[1], tz);
                    if (!level.getBlockState(t).isCollisionShapeFullBlock(level, t) || !level.getBlockState(t.above()).isAir()
                            || !level.getBlockState(t.above(2)).isAir()) continue;
                    for (int sx = -Math.max(1, lo[3]); sx <= Math.max(1, lo[3]); sx++) for (int sz = -Math.max(1, lo[3]); sz <= Math.max(1, lo[3]); sz++) {
                        double fx = lo[0] + sx + 0.5, fz = lo[2] + sz + 0.5;
                        // the edge of the target cell facing the stander, 0.15 in
                        double ex = Math.max(tx + 0.15, Math.min(tx + 0.85, fx)), ez = Math.max(tz + 0.15, Math.min(tz + 0.85, fz));
                        double side = Math.hypot(ex - fx, ez - fz);
                        if (side < bestSide) {
                            bestSide = side;
                            rise = hi[1] - (lo[1] + 1 + AscentRules.HAND_HEIGHT);
                        }
                    }
                }
                h.assertTrue(rise > AscentRules.ZIP_UP_MIN_RISE && bestSide < rise * AscentRules.ZIP_UP_SLOPE,
                        "step " + i + ": a zip straight up from the near rim (rise " + rise + ", side " + bestSide + ")");
            }
            // the crown: a climb from the plaza is timed and kept; a tower gets nothing
            BlockPos crown = o.offset(C, TOP + 1, C);
            h.assertTrue(level.getBlockEntity(crown) instanceof SpireCrownBlockEntity, "the crown is placed");
            SpireCrownBlockEntity be = (SpireCrownBlockEntity) level.getBlockEntity(crown);
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            var real = dev.awaken.core.api.ArrivalHooks.installed();
            try {
                boolean[] moved = {false};
                dev.awaken.core.api.ArrivalHooks.install(new dev.awaken.core.api.ArrivalHooks.Tracker() {
                    @Override
                    public boolean byMovement(net.minecraft.server.level.ServerPlayer pl) {
                        return moved[0];
                    }
                });
                AscentMotion m = AscentServer.motion(p);
                for (int i = 0; i < 400; i++) m.mark(true);
                long now = level.getGameTime();
                be.startedAgo(p, 600);
                be.reached(p, now);
                h.assertTrue(be.records().isEmpty(), "a tower gets nothing");
                moved[0] = true;
                int before = p.getData(AscentContent.MEMORY).points(AscentMemory.Track.TETHER);
                be.startedAgo(p, 600);
                be.reached(p, now + 200);
                // left the plaza 600 ticks before now, on the deck 200 after: 800
                h.assertTrue(be.records().size() == 1 && be.records().getFirst().ticks() == 800, "a climb from the plaza is timed and kept: " + be.records());
                h.assertTrue(p.getData(AscentContent.MEMORY).points(AscentMemory.Track.TETHER) >= before + 10, "the first climb is 10 Tether mastery");
            } finally {
                dev.awaken.core.api.ArrivalHooks.install(real);
                level.getServer().getPlayerList().remove(p);
            }
        } finally {
            for (BlockPos q : BlockPos.betweenClosed(o, o.offset(24, size, 24))) {
                if (!level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
        h.succeed();
    }

    private static int ticksToEmpty(double drain, int level) {
        double a = 1;
        int t = 0;
        while (a > 0 && t < 100000) {
            a = AscentRules.aetherStep(a, true, drain, false, false, level);
            t++;
        }
        return t;
    }

    /** Flight cruises near 1.25, boosts near 2.25, and hovers to a stop. */
    private static void soarSpeeds(GameTestHelper h) {
        double[] s = new double[3];
        AscentRules.Kin k = new AscentRules.Kin();
        for (int t = 0; t < 400; t++) AscentRules.soarStep(k, 0, 0, 1, 0, false, false, false, s);
        h.assertTrue(Math.abs(k.speed() - 1.25) < 0.1, "cruise " + k.speed());
        for (int t = 0; t < 400; t++) AscentRules.soarStep(k, 0, 0, 1, 0, false, false, true, s);
        h.assertTrue(Math.abs(k.speed() - 2.25) < 0.15, "boost " + k.speed());
        for (int t = 0; t < 80; t++) AscentRules.soarStep(k, 0, 0, 0, 0, false, false, false, s);
        h.assertTrue(k.speed() < 0.05, "hover left " + k.speed());
        h.succeed();
    }
    /**
     * Windways (SPEC §17.12): a lane's geometry between two platforms, and a glide off the first deck carried the whole
     * way and set down slowly over the next deck, looking along it, 15° under it or 15° over it; a look 45° away breaks
     * out of it.
     */
    private static void windway(GameTestHelper h) {
        net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(0, 0, 0), q = new net.minecraft.core.BlockPos(600, 0, 800);
        java.util.List<Windways.Way> ways = Windways.between(p, 276, q, 279);
        Windways.Way w = ways.get(0), back = ways.get(1);
        double startR = Math.hypot(w.ax() - 0.5, w.az() - 0.5), endR = Math.hypot(w.bx() - q.getX() - 0.5, w.bz() - q.getZ() - 0.5);
        h.assertTrue(Math.abs(startR - Math.hypot(Windways.OUT, Windways.LANE)) < 0.01 && w.ay() == 276 - Windways.BELOW
                        && w.by() == 279 + 1 + Windways.ABOVE && endR > 7 && endR < 11.6,
                "a lane starts past the islands under its deck and ends over the next deck's rim (" + startR + ", " + w.ay() + " to " + endR + ", " + w.by() + ")");
        double mx = (w.ax() + w.bx()) / 2, my = (w.ay() + w.by()) / 2, mz = (w.az() + w.bz()) / 2;
        h.assertTrue(back.off(mx, my, mz) > 2 * Windways.RADIUS, "the two ways' tubes are apart: " + back.off(mx, my, mz));
        for (float off : new float[]{0, 15, -15}) {
            double[] r = ride(w, off, 0);
            h.assertTrue(r[1] == 0 && r[2] >= 0 && r[0] < 20 * 45 && r[2] < 0.8,
                    "looking " + off + " under its way, carried " + (int) w.length() + " blocks in " + r[0] / 20 + " s and set down at " + r[2]
                            + " b/t (left it: " + (r[1] != 0) + ")");
        }
        double[] away = ride(w, 0, 45);
        h.assertTrue(away[1] != 0 && away[0] < 200, "a look 45° away breaks out (" + away[0] / 20 + " s)");
        // the server's lanes are its air (AirHooks)
        var old = Windways.serverWays();
        Windways.setServer(ways);
        double[] air = new double[3];
        try {
            double s = w.length() / 2;
            double ux = (w.bx() - w.ax()) / w.length(), uz = (w.bz() - w.az()) / w.length();
            h.assertTrue(dev.awaken.core.api.AirHooks.sample(h.getLevel(), w.ax() + ux * s, w.ay() + (w.by() - w.ay()) / 2, w.az() + uz * s, air)
                    && air[0] * ux + air[2] * uz > Windways.PUSH * 0.9, "AirHooks hears the lane: " + java.util.Arrays.toString(air));
        } finally {
            Windways.setServer(old);
        }
        h.succeed();
    }

    /** A glide dropped off a deck just before a lane's start: {ticks, left the tube early (1), speed out of its end, y}. */
    private static double[] ride(Windways.Way w, float pitchOff, float yawOff) {
        double len = w.length(), ux = (w.bx() - w.ax()) / len, uy = (w.by() - w.ay()) / len, uz = (w.bz() - w.az()) / len;
        float laneYaw = (float) Math.toDegrees(Math.atan2(-ux, uz)), lanePitch = (float) -Math.toDegrees(Math.asin(uy));
        AscentRules.Kin k = new AscentRules.Kin().set(w.ax() - ux * 2, w.ay() + 5, w.az() - uz * 2, ux * 0.5, -0.3, uz * 0.5);
        float[] line = new float[2];
        double[] air = new double[3];
        int t;
        for (t = 0; t < 20 * 60; t++) {
            float yaw = laneYaw + yawOff, pitch = lanePitch + pitchOff;
            if (Windways.holds(w, k.x, k.y, k.z) && Windways.steer(w, k.x, k.y, k.z, yaw, pitch, line)) {
                yaw = line[0];
                pitch = line[1];
            }
            AscentRules.glideStep(k, yaw, pitch);
            air[0] = air[1] = air[2] = 0;
            Windways.airOf(w, k.x, k.y, k.z, air);
            AscentRules.air(k, air, AscentRules.AIR_GLIDE);
            if (w.along(k.x, k.y, k.z) > len) return new double[]{t, 0, k.speed(), k.y};
            if (t > 40 && !Windways.holds(w, k.x, k.y, k.z)) return new double[]{t, 1, -1, k.y};
        }
        return new double[]{t, 0, -1, k.y};
    }
    /** The slow beat (SPEC §17.15): at 0.45 of time the carry flies the same arc, slower (within a few percent). */
    private static void slowBeat(GameTestHelper h) {
        AscentRules.Kin a = new AscentRules.Kin().set(0, 0, 0, 0.9, 0.1, 0), b = new AscentRules.Kin().set(0, 0, 0, 0.9, 0.1, 0);
        double[] scratch = new double[2];
        for (int t = 0; t < 9; t++) AscentRules.carryStep(a, 0, 0, 0, scratch);
        for (int t = 0; t < 20; t++) AscentRules.carryStepSlow(b, 0.45);
        double da = Math.hypot(a.x, a.y), err = Math.hypot(a.x - b.x, a.y - b.y);
        h.assertTrue(err < da * 0.06 && b.vy < a.vy + 0.05 && b.vy > a.vy - 0.05,
                "20 slow ticks fly 9 ticks' arc: " + String.format("(%.2f, %.2f) vs (%.2f, %.2f)", b.x, b.y, a.x, a.y));
        h.succeed();
    }
    /**
     * The hero trees' crowns (SPEC §17.16), from each tree's own blueprint over eight seeds: the crown on the highest
     * cell over the trunk with room to stand, a route of rings up the trunk (two or more on the Colossus, one or more on
     * the Lumen Spire), each ring in air with room to hang under it, the first within a throw of the ground, each next
     * one a zip from the last (6+ up, sideways under 0.45 of the rise).
     */
    private static void heroCrowns(GameTestHelper h) {
        StringBuilder sb = new StringBuilder();
        for (dev.awaken.terrain.hero.HeroTree tree : new dev.awaken.terrain.hero.HeroTree[]{dev.awaken.terrain.hero.HeroTree.ELDER_COLOSSUS,
                dev.awaken.terrain.hero.HeroTree.LUMEN_SPIRE}) {
            boolean colossus = tree == dev.awaken.terrain.hero.HeroTree.ELDER_COLOSSUS;
            for (long seed = 1; seed <= 8; seed++) {
                dev.awaken.terrain.hero.Blueprint bp = dev.awaken.terrain.hero.HeroWorldgen.blueprint(tree, seed * 7919L);
                HeroCrowns.Layout l = HeroCrowns.layout(tree, bp, seed);
                String id = tree.getSerializedName() + " #" + seed;
                BlockPos c = l.crown();
                h.assertTrue(bp.get(c.getX(), c.getY(), c.getZ()) == 0 && bp.get(c.getX(), c.getY() + 1, c.getZ()) == 0
                        && bp.get(c.getX(), c.getY() - 1, c.getZ()) != 0, id + ": the crown stands on the tree's top at " + c.toShortString());
                for (int x = -HeroCrowns.SUMMIT_REACH; x <= HeroCrowns.SUMMIT_REACH; x++) for (int z = -HeroCrowns.SUMMIT_REACH; z <= HeroCrowns.SUMMIT_REACH; z++) {
                    if (x * x + z * z <= HeroCrowns.SUMMIT_REACH * HeroCrowns.SUMMIT_REACH) {
                        h.assertTrue(bp.get(x, c.getY(), z) == 0 || x == c.getX() && z == c.getZ() || bp.get(x, c.getY(), z) == 0,
                                id + ": nothing over the trunk is higher than the crown's foot");
                    }
                }
                int need = colossus ? 2 : 1;
                h.assertTrue(l.rings().size() >= need, id + ": " + l.rings().size() + " rings up the trunk (want " + need + "+)");
                BlockPos last = null;
                for (BlockPos r : l.rings()) {
                    for (int dy = -2; dy <= 3; dy++) h.assertTrue(bp.get(r.getX(), r.getY() + dy, r.getZ()) == 0, id + ": room at the ring " + r.toShortString());
                    if (last == null) {
                        h.assertTrue(r.getY() >= 6 && r.getY() <= 30, id + ": the first ring is a throw from the ground (" + r.getY() + " up)");
                    } else {
                        double rise = r.getY() - last.getY(), side = Math.hypot(r.getX() - last.getX(), r.getZ() - last.getZ());
                        h.assertTrue(rise >= 6 && side < rise * AscentRules.ZIP_UP_SLOPE, String.format("%s: ring to ring a zip (%.0f up, %.1f across)", id, rise, side));
                    }
                    last = r;
                }
                sb.append(String.format("%s: crown %d up, %d rings to %d%n", id, c.getY(), l.rings().size(), last == null ? 0 : last.getY()));
            }
        }
        dev.awaken.Awaken.LOG.info("[ascent] hero crowns:\n{}", sb);
        h.succeed();
    }
    /** Aerial kills (SPEC §17.18): a hostile killed in flight gives Skyborn mastery; on the ground, none. */
    private static void aerialKill(GameTestHelper h) {
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        Ascent.grant(p, Ascent.Tier.SKYBORN, "test");
        int before = p.getData(AscentContent.MEMORY).points(AscentMemory.Track.SKYBORN);
        var ground = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        ground.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 1000F);
        int afterGround = p.getData(AscentContent.MEMORY).points(AscentMemory.Track.SKYBORN);
        AscentServer.motion(p).state = AscentState.SOAR;
        var air = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        air.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 1000F);
        int afterAir = p.getData(AscentContent.MEMORY).points(AscentMemory.Track.SKYBORN);
        AscentServer.motion(p).state = AscentState.NONE;
        h.assertTrue(afterGround == before && afterAir - afterGround == AscentServer.AERIAL_KILL,
                "a kill on the ground gives none, one in flight " + AscentServer.AERIAL_KILL + " (" + before + " -> " + afterGround + " -> " + afterAir + ")");
        h.succeed();
    }
    /**
     * The Sovereign (SPEC §17.19): refused without the tier or with the Aether low; taken, it strikes every hit
     * harder, its Judgement Fall throws and strikes everything near; it ends on its clock and the cooldown holds.
     */
    private static void sovereign(GameTestHelper h) {
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        AscentServer.motion(p).aether = 1;
        h.assertFalse(Sovereign.ascend(p), "no form without the tier");
        Ascent.grant(p, Ascent.Tier.SOVEREIGN, "test");
        AscentServer.motion(p).aether = 0.5;
        h.assertFalse(Sovereign.ascend(p), "nor with the Aether low");
        AscentServer.motion(p).aether = 1;
        h.assertTrue(Sovereign.ascend(p) && Sovereign.active(p), "the form, the Aether full");
        var target = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
        var hit = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(target,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().playerAttack(p), 4F));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(hit);
        h.assertTrue(Math.abs(hit.getAmount() - 4F * Sovereign.AMPLIFY) < 1e-4, "every hit lands harder: " + hit.getAmount());
        float before = target.getHealth();
        p.teleportTo(target.getX() + 2, target.getY(), target.getZ());
        h.assertTrue(Sovereign.judgement(p, 1.2F) && target.getHealth() < before, "the Judgement Fall strikes what's near ("
                + before + " -> " + target.getHealth() + ")");
        Sovereign.expireForTests(p);
        Sovereign.tick(p, AscentServer.now(p));
        h.assertFalse(Sovereign.active(p), "it ends on its clock");
        h.assertFalse(Sovereign.ascend(p), "and the sky gathers itself before the next");
        h.succeed();
    }
}
