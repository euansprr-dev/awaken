package dev.awaken.powers;

import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Awakened Body's family twists, lined up like {@link PowerHitTests}: a stand-in body (installed in place of the
 * body module's hooks for the test) says the caster is sliding along +z; the twist runs; every body in the line-up is
 * checked. Plus the Velocity Dash out of a slide and the chain refund's gate.
 */
final class MoveTwistTests {
    private MoveTwistTests() {}

    static void register() {
        AwakenTests.registerIsolated("powers_twist_slide_punch_lineup", 120, MoveTwistTests::slidePunch);
        AwakenTests.registerIsolated("powers_twist_smoke_trail_lineup", 120, MoveTwistTests::smokeTrail);
        AwakenTests.register("powers_twist_dash_out_and_chain_refund", 60, MoveTwistTests::dashOutAndRefund);
    }

    /** A body that slides along +z, its last move {@code ago} ticks back. */
    static MovementHooks.Access sliding(boolean slide, long lastMove) {
        return new MovementHooks.Access() {
            @Override public boolean sliding(Player p) { return slide; }
            @Override public long lastMoveTick(Player p) { return lastMove; }
            @Override public Vec3 slideDirection(Player p) { return slide ? new Vec3(0, 0, 1) : null; }
        };
    }

    record Spot(String name, String type, double x, double z, boolean foe, boolean hurt, boolean targets) {}

    /**
     * A test player that really is in survival: the helper's mock always answers "creative", and mobs never target a
     * creative player, so pursuit can't be tested with it.
     */
    static ServerPlayer survivor(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    static ServerPlayer caster(GameTestHelper h) {
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 8; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        ServerPlayer p = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.snapTo(at.x, at.y, at.z, 0f, 0f); // facing +z, the slide's way
        return p;
    }

    static Map<Spot, LivingEntity> lineUp(GameTestHelper h, ServerPlayer p, List<Spot> spots) {
        Map<Spot, LivingEntity> bodies = new LinkedHashMap<>();
        Vec3 at = p.position();
        for (Spot s : spots) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(s.type()));
            if (!(type.create(h.getLevel(), EntitySpawnReason.COMMAND) instanceof LivingEntity e)) continue;
            e.snapTo(at.x + s.x(), at.y, at.z + s.z(), 180f, 0f);
            if (e.getAttribute(Attributes.MOVEMENT_SPEED) != null) e.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
            if (e instanceof Mob m) {
                m.setPersistenceRequired();
                if (s.targets()) {
                    m.setNoAi(true); // keeps the target it is given
                    m.setTarget(p);
                }
            }
            h.getLevel().addFreshEntity(e);
            bodies.put(s, e);
        }
        return bodies;
    }

    /** A Kinetic cast mid-slide: foes ahead in the cone are hit and thrown; a bystander is pushed, never hurt; behind and aside, nothing. */
    static void slidePunch(GameTestHelper h) {
        ServerPlayer p = caster(h);
        List<Spot> spots = List.of(
                new Spot("zombie ahead", "minecraft:zombie", 0, 3, true, true, false),
                new Spot("spider ahead", "minecraft:spider", 1.4, 2.6, true, true, false),
                new Spot("cow ahead", "minecraft:cow", -1.0, 2.2, false, false, false),
                new Spot("zombie behind", "minecraft:zombie", 0, -3, true, false, false),
                new Spot("zombie aside", "minecraft:zombie", 3.6, 0.8, true, false, false));
        Map<Spot, LivingEntity> bodies = lineUp(h, p, spots);
        Map<Spot, Float> hp = new LinkedHashMap<>();
        Map<Spot, Vec3> where = new LinkedHashMap<>();
        MovementHooks.Access real = MovementHooks.installed();
        h.startSequence().thenIdle(5).thenExecute(() -> {
            for (var e : bodies.entrySet()) {
                hp.put(e.getKey(), e.getValue().getHealth());
                where.put(e.getKey(), e.getValue().position());
            }
            PowerType brace = PowerRegistry.get("awaken:brace"); // Kinetic, and hurts nothing by itself until it expires
            Powers.learn(p, brace.id, 0);
            Powers.fillEnergy(p);
            Powers.resetCooldowns(p);
            MovementHooks.install(sliding(true, Long.MIN_VALUE));
            try {
                PowerType.Result r = Casting.cast(p, brace, 0, false, 0f, 0f);
                h.assertTrue(r.ok(), "brace casts: " + r.reason());
            } finally {
                MovementHooks.install(real);
            }
        }).thenIdle(8).thenExecute(() -> {
            List<String> wrong = new ArrayList<>();
            for (var e : bodies.entrySet()) {
                Spot s = e.getKey();
                LivingEntity b = e.getValue();
                boolean hurt = b.getHealth() < hp.get(s) - 0.01f && b.getLastHurtByPlayer() == p;
                double moved = b.position().distanceTo(where.get(s));
                if (s.hurt() && !hurt) wrong.add(s.name() + " was not hurt");
                if (!s.hurt() && hurt) wrong.add(s.name() + " was hurt");
                if (s.hurt() && moved < 0.25) wrong.add(s.name() + " was not thrown");
                if (s.name().startsWith("cow") && moved < 0.15) wrong.add("the cow was not pushed");
            }
            h.assertTrue(wrong.isEmpty(), "slide-punch: " + String.join("; ", wrong));
            bodies.values().forEach(LivingEntity::discard);
            Casting.stop(p, "awaken:brace", Running.Why.CANCELLED);
            h.getLevel().getServer().getPlayerList().remove(p);
        }).thenSucceed();
    }

    /** A Shadow cast mid-slide: pursuers in the smoke lose the caster; one far from it keeps chasing; nobody is hurt. */
    static void smokeTrail(GameTestHelper h) {
        ServerPlayer p = caster(h);
        List<Spot> spots = List.of(
                new Spot("pursuer in the smoke", "minecraft:zombie", 0.4, 1.0, true, false, true),
                new Spot("pursuer behind the smoke", "minecraft:zombie", -1.0, -0.6, true, false, true),
                new Spot("pursuer far away", "minecraft:zombie", 6.0, 6.0, true, false, true),
                new Spot("cow", "minecraft:cow", 1.0, -1.0, false, false, false));
        Map<Spot, LivingEntity> bodies = lineUp(h, p, spots);
        MovementHooks.Access real = MovementHooks.installed();
        int[] age = {-1};
        h.onEachTick(() -> {
            if (age[0] < 0 || age[0] > 20) return;
            age[0]++;
            MovementHooks.install(sliding(true, Long.MIN_VALUE));
            try {
                Casting.tickPlayer(p); // a mock player isn't ticked: drive the trail's running part
            } finally {
                MovementHooks.install(real);
            }
        });
        h.startSequence().thenIdle(5).thenExecute(() -> {
            MovementHooks.install(sliding(true, Long.MIN_VALUE));
            try {
                MoveTwists.smokeTrail(Cast.of(p, PowerRegistry.get("awaken:cloak")));
            } finally {
                MovementHooks.install(real);
            }
            age[0] = 0;
        }).thenIdle(12).thenExecute(() -> {
            List<String> wrong = new ArrayList<>();
            for (var e : bodies.entrySet()) {
                Spot s = e.getKey();
                if (!(e.getValue() instanceof Mob m)) continue;
                boolean lost = m.getTarget() != p;
                boolean shouldLose = s.targets() && !s.name().endsWith("far away");
                if (s.targets() && shouldLose != lost) wrong.add(s.name() + (lost ? " lost the caster" : " kept chasing"));
                if (m.getHealth() < m.getMaxHealth()) wrong.add(s.name() + " was hurt");
            }
            h.assertTrue(wrong.isEmpty(), "smoke trail: " + String.join("; ", wrong));
            bodies.values().forEach(LivingEntity::discard);
            h.getLevel().getServer().getPlayerList().remove(p);
        }).thenSucceed();
    }

    /** A Velocity Dash out of a slide keeps its landing safe longer; a cast right after a move refunds a little, once a second. */
    static void dashOutAndRefund(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        PowerType dash = PowerRegistry.get("awaken:dash");
        Powers.learn(p, dash.id, 0);
        Powers.fillEnergy(p);
        Loadout l = Powers.loadout(p);
        l.energy = 50;
        MovementHooks.Access real = MovementHooks.installed();
        long now = Casting.now(p);
        float cost = Casting.costOf(p, dash, 0);
        try {
            MovementHooks.install(sliding(true, now - 5));
            h.assertTrue(Casting.cast(p, dash, 5, false, 0f, 0f).ok(), "dash casts");
            float refund = Math.max(MoveTwists.REFUND_MIN, Math.min(MoveTwists.REFUND_MAX, cost * MoveTwists.REFUND_SHARE));
            h.assertTrue(Math.abs(l.energy - (50 - cost + refund)) < 1e-3, "a chained cast refunds " + refund + ": energy " + l.energy);
            h.assertTrue(PowerState.of(p).has(PowerState.Buffs.NO_FALL, now + 35), "the dash out of a slide keeps its landing safe longer");
            Powers.resetCooldowns(p);
            float before = l.energy;
            h.assertTrue(Casting.cast(p, dash, 5, false, 0f, 0f).ok(), "dash again");
            h.assertTrue(Math.abs(l.energy - (before - cost)) < 1e-3, "no second refund within a second");
            MovementHooks.install(sliding(false, now - 40));
            Powers.resetCooldowns(p);
            before = l.energy;
            h.assertTrue(Casting.cast(p, dash, 5, false, 0f, 0f).ok(), "dash a third time");
            h.assertTrue(Math.abs(l.energy - (before - cost)) < 1e-3, "no refund long after a move");
        } finally {
            MovementHooks.install(real);
        }
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }
}
