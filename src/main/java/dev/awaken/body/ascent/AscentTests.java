package dev.awaken.body.ascent;

import dev.awaken.core.test.AwakenTests;
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

    /**
     * 0 kicks (the Phase 1 gate), through the server's real movement checks, which skip the singleplayer host (so tours
     * can't show it): a survival player on a plain connection flies an Ascent state for 15 s at 1.25, 2.25 and 3.2
     * blocks a tick (the top speeds: flight cruise, boost, the cap), turning back and forth and rising and falling, one
     * position a client tick and, for the last third, in lag bursts of up to five client ticks at once. Not one move
     * is sent back ("moved too quickly" or "moved wrongly" snap the body to where it was) and the player stays
     * connected. (The floating kick can't fire here: only a dedicated server with allow-flight off has it. Every tick
     * a state runs, AscentServer resets the count before vanilla adds to it.)
     */
    private static void noKicks(GameTestHelper h) {
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ascent-flyer"), false);
        net.minecraft.server.level.ServerPlayer p = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        try {
            var conn = p.connection;
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            conn.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
            net.minecraft.core.BlockPos base = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
            double x = base.getX() + 0.5, y = base.getY() + 60, z = base.getZ() + 0.5;
            conn.teleport(x, y, z, 0, 0);
            conn.resetPosition(); // the check that runs on accepting a teleport measures from here
            for (int id = 0; id < 16; id++) {
                conn.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id, x, y, z, 0, 0));
            }
            conn.tick(); // the server takes the tick's starting point from where the body is
            h.assertTrue(p.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z)) < 1e-3, "the test's teleport was accepted");
            Ascent.grant(p, Ascent.Tier.SKYBORN, "test");
            onStateForTest(p, AscentState.SOAR);
            h.assertTrue(motion(p).state == AscentState.SOAR, "the server took the flight");
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
                    conn.tick();
                    continue;
                }
                for (double[] q : queued) {
                    conn.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(q[0], q[1], q[2], 0, 0, false, false));
                    conn.handleClientTickEnd(net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.INSTANCE);
                    double off = p.position().distanceTo(new net.minecraft.world.phys.Vec3(q[0], q[1], q[2]));
                    h.assertTrue(off < 1e-3, String.format("sent back %.2f at tick %d, %.2f a tick, %d in the burst", off, (int) q[3], q[4], queued.size()));
                }
                queued.clear();
                conn.tick();
                h.assertFalse(p.hasDisconnected(), "kicked at tick " + t);
            }
        } finally {
            level.getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /** The server hearing a state from this player's client (tests). */
    private static void onStateForTest(net.minecraft.server.level.ServerPlayer p, AscentState s) {
        AscentServer.onState(p, new AscentPayloads.State(s.ordinal(), 0, 0, 0, 0));
    }

    private static AscentMotion motion(net.minecraft.server.level.ServerPlayer p) {
        return AscentServer.motion(p);
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
