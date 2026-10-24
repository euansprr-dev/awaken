package dev.awaken.powers;

import dev.awaken.Awaken;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Do powers hit what they're supposed to? A line-up of different bodies (small, wide, tall, huge, knockback-proof, our
 * own creatures, a peaceful grazer and a farm animal) stands around the caster; the power is cast and every target is
 * checked for damage and movement. Ground Slam and Shockwave run in every suite; {@code -Dawaken.powerAudit=true}
 * (Gradle: {@code -PpowerAudit}) runs every damaging power and logs the matrix.
 */
final class PowerHitTests {
    private PowerHitTests() {}

    /** One body in the line-up: where it stands (relative to the caster, who faces +z) and whether it should be hurt. */
    record Spot(String name, String type, double x, double z, boolean foe, boolean fought) {}

    // Within 4.4 blocks of the caster (Ground Slam reaches 4.5); the big ones' centres sit past the edge on purpose.
    static final List<Spot> AROUND = List.of(
            new Spot("zombie", "minecraft:zombie", 0, 3, true, false),
            new Spot("spider", "minecraft:spider", -2.5, 2.5, true, false),
            new Spot("ravager", "minecraft:ravager", 3.6, 3.0, true, false),        // huge, knockback resistant, centre at 4.7
            new Spot("iron golem", "minecraft:iron_golem", 0, -4.6, true, true),   // neutral, struck by the player first
            new Spot("gloomstalker", "awaken:gloomstalker", -3, -2, true, false), // an overworld hunter
            new Spot("mossback", "awaken:mossback", 3, -1.5, false, false),       // a peaceful grazer: pushed, never hurt
            new Spot("cow", "minecraft:cow", -1.5, -3, false, false));            // a farm animal: pushed, never hurt

    // Ahead in a cone (Shockwave reaches 9 blocks, 30 degrees either side).
    static final List<Spot> AHEAD = List.of(
            new Spot("zombie", "minecraft:zombie", 0, 4, true, false),
            new Spot("ravager", "minecraft:ravager", 1.8, 7, true, false),
            new Spot("spider", "minecraft:spider", -2.2, 5, true, false),
            new Spot("cow", "minecraft:cow", 1.2, 3, false, false));

    static void register() {
        AwakenTests.registerIsolated("powers_hits_ground_slam_lineup", 200, h -> lineup(h, "ground_slam", AROUND, true));
        AwakenTests.registerIsolated("powers_hits_shockwave_lineup", 200, h -> lineup(h, "shockwave", AHEAD, true));
        if (!Boolean.getBoolean("awaken.powerAudit")) return;
        for (PowerType t : PowerRegistry.all()) {
            if (t.damage <= 0 || t.slot == PowerType.Slot.PASSIVE || t.mode == PowerType.Mode.PASSIVE) continue;
            AwakenTests.registerIsolated("powers_audit_" + t.path, 300, h -> lineup(h, t.path, AROUND, false));
        }
    }

    /** strict: every foe must be hurt and moved by a knockback power, every bystander moved and never hurt. */
    static void lineup(GameTestHelper h, String power, List<Spot> spots, boolean strict) {
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 11; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL); // creative players skip some branches; mobs still ignore a mock player
        Vec3 at = h.absoluteVec(new Vec3(0.5, 1, 0.5));
        p.snapTo(at.x, at.y, at.z, 0f, 0f); // yaw 0 faces +z
        Map<Spot, LivingEntity> bodies = new LinkedHashMap<>();
        for (Spot s : spots) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(s.type()));
            if (!(type.create(h.getLevel(), EntitySpawnReason.COMMAND) instanceof LivingEntity e)) continue;
            Vec3 pos = at.add(s.x(), 0, s.z());
            e.snapTo(pos.x, pos.y, pos.z, 180f, 0f);
            // standing still, but with physics: a body with no AI ignores knockback entirely
            if (e.getAttribute(Attributes.MOVEMENT_SPEED) != null) e.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
            if (e instanceof Mob m) m.setPersistenceRequired();
            h.getLevel().addFreshEntity(e);
            if (s.fought()) e.setLastHurtByPlayer(p, 200);
            bodies.put(s, e);
        }
        Map<Spot, Float> hp = new LinkedHashMap<>();
        Map<Spot, Vec3> where = new LinkedHashMap<>();
        PowerType type = PowerRegistry.get(power);
        int[] since = {-1};
        // a mock player isn't ticked by the server: drive the power's running part the way a player tick would, and
        // let go of a held power after a second, like releasing the key
        h.onEachTick(() -> {
            if (since[0] < 0 || since[0] > 60) return;
            since[0]++;
            Casting.tickPlayer(p);
            if (since[0] == 20 && type.mode == PowerType.Mode.CHANNEL) Casting.stop(p, type.id.toString(), Running.Why.RELEASED);
        });
        h.startSequence()
                // the bodies settle on the floor; they tick from the start (every test starts once its area entity-ticks,
                // AwakenTests: a body in a chunk that didn't yet was knocked back but never moved)
                .thenIdle(5)
                .thenExecute(() -> {
                    for (var e : bodies.entrySet()) {
                        hp.put(e.getKey(), e.getValue().getHealth());
                        where.put(e.getKey(), e.getValue().position());
                    }
                    Powers.learn(p, type.id, 0);
                    Powers.fillEnergy(p);
                    Powers.resetCooldowns(p);
                    PowerType.Result r = Casting.cast(p, type, 0, false, 0f, 0f);
                    if (strict) h.assertTrue(r.ok(), power + " should cast: " + r.reason());
                    if (!r.ok()) Awaken.LOG.info("[power audit] {}: did not cast ({})", power, r.reason());
                    since[0] = 0;
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    List<String> report = new ArrayList<>();
                    List<String> wrong = new ArrayList<>();
                    for (var e : bodies.entrySet()) {
                        Spot s = e.getKey();
                        LivingEntity b = e.getValue();
                        // hurt by the caster, not by a neighbour in the line-up (a hunter or a ravager may swing at it)
                        boolean hurt = (!b.isAlive() || b.getHealth() < hp.get(s) - 0.01f) && b.getLastHurtByPlayer() == p;
                        double moved = b.position().distanceTo(where.get(s));
                        report.add(s.name() + (hurt ? " hurt" : " -") + String.format(" moved %.1f", moved));
                        if (s.foe() && !hurt) wrong.add(s.name() + " was not hurt");
                        if (!s.foe() && hurt) wrong.add(s.name() + " was hurt (it's a bystander)");
                        if (strict && moved < 0.25) wrong.add(s.name() + " did not move");
                    }
                    Awaken.LOG.info("[power audit] {}: {}", power, String.join(", ", report));
                    if (strict) h.assertTrue(wrong.isEmpty(), power + ": " + String.join("; ", wrong));
                    bodies.values().forEach(LivingEntity::discard);
                    h.getLevel().getServer().getPlayerList().remove(p);
                })
                .thenSucceed();
    }
}
