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
     * and clear from the forecourt; then the director: the call, the line (the rope given), the vow kept at the mast.
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
                TetherVow.Site site = new TetherVow.Site(box, rot);
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
            // the director: awake, no rope, in the forecourt of the last vault built
            dev.awaken.progression.Progression.awaken(p);
            h.assertTrue(TetherVow.eligible(p), "an awakened player without the rope is offered the vow");
            TetherVow.Site site = new TetherVow.Site(box, 3);
            long now = AscentServer.now(p);
            TetherVow.begin(p, site, now);
            TetherVow.tick(p, now + TetherVow.CALL_TICKS - 1);
            h.assertFalse(Ascent.has(p, Ascent.Tier.TETHER), "the call comes before the line");
            TetherVow.tick(p, now + TetherVow.CALL_TICKS);
            h.assertTrue(Ascent.has(p, Ascent.Tier.TETHER) && !Ascent.has(p, Ascent.Tier.LEAPER), "the line gives the rope (only)");
            BlockPos m = site.mast();
            p.snapTo(m.getX() + 0.5, box.minY() + 16, m.getZ() + 0.5, 0, 0);
            TetherVow.tick(p, now + TetherVow.CALL_TICKS + 40);
            h.assertTrue(!TetherVow.running(p) && (p.getData(AscentContent.MEMORY).flags() & AscentMemory.VOW_KEPT) != 0, "the vow is kept at the mast");
            h.assertFalse(TetherVow.eligible(p), "and never offered again");
        } finally {
            TetherVow.forget(p);
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
            h.assertTrue(WingCoreBlockEntity.reached(p, placed.core()) && Ascent.has(p, Ascent.Tier.WINGS), "the long way gets wings");
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
}
