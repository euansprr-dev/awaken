package dev.awaken.living.hunt;

import dev.awaken.core.Later;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AttributeWalk;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateService;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/** A beaten beast's body in a gate (SPEC §41.4, §13 "Mobs"). */
public final class CarcassTests {
    private CarcassTests() {}

    public static void register() {
        AwakenTests.registerIsolated("hunt_beaten_beast_carcass_attributes", 260, CarcassTests::beatenBeastCarcass);
        AwakenTests.register("hunt_carcass_goes_off_the_overworld", 80, CarcassTests::goesOffTheOverworld);
    }

    /** What the watcher saw of the body the moment it was laid: its first tick's problem, or "" for none; null until then. */
    private static final class Seen {
        String problem;
        Mob body;
        int waited;
    }

    /**
     * The Thousandfoot crash (12 Nov, build 66e268471): Euan deflected the Hive's Warden, its stance broke and it knelt, he
     * cut it down, and three seconds later the server died on its carcass's first tick. The carcass is a fresh copy given
     * the dead's scale, so its first tick's walk over its dirty attributes refreshes its size, and the Gravity Wells' size
     * listener made the copy's gravity attribute in that walk: the set grew under it, and Lithium's tight set threw. The same
     * fight here; the body is caught as it is laid, before its first tick, and that tick's answers must move nothing.
     */
    static void beatenBeastCarcass(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int bx = -4; bx <= 8; bx++) for (int bz = -4; bz <= 10; bz++) {
            h.setBlock(new BlockPos(bx, 0, bz), Blocks.STONE);
            for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(bx, y, bz), Blocks.AIR);
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.markClientLoaded();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400); // a blow that slips the guard must not end the fight
        p.setHealth(400);
        Vec3 at = h.absoluteVec(new Vec3(2.5, 1, 0.5));
        p.snapTo(at.x, at.y, at.z, 0F, 0F); // facing +z, at the beast
        p.setYHeadRot(0F);
        p.setOnGround(true);
        // the Hive's Warden, as a Broodwarren's land sends it
        LivingEntity spawned = MobHooks.spawnNamed(level, "thousandfoot", h.absolutePos(new BlockPos(2, 1, 4)), 3, Set.of(), "");
        h.assertTrue(spawned instanceof AwakenMob, "the thousandfoot came, as " + spawned);
        AwakenMob beast = (AwakenMob) spawned;
        beast.setNoAi(true); // it strikes when told, so every blow is the one we deflect
        Vec3 b = h.absoluteVec(new Vec3(2.5, 1, 4.5));
        beast.snapTo(b.x, b.y, b.z, 180F, 0F);
        beast.setYHeadRot(180F);
        Seen seen = new Seen();
        AwakenTests.onEnd(h, () -> {
            if (seen.body != null) seen.body.discard();
            beast.discard();
            level.getServer().getPlayerList().remove(p);
        });
        h.startSequence().thenIdle(5).thenExecute(() -> {
            // its light blows on the raise, deflected until its stance breaks: it kneels
            for (int i = 0; i < 60 && !dev.awaken.arms.Arms.broken(beast); i++) {
                p.stopUsingItem();
                p.startUsingItem(InteractionHand.MAIN_HAND);
                float hp = p.getHealth();
                Strikes.deliver(beast, Strikes.Kind.LIGHT, () -> beast.doHurtTarget(level, p));
                h.assertTrue(p.getHealth() == hp, "its light blow on the raise is deflected (" + i + ", took " + (hp - p.getHealth()) + ")");
            }
            h.assertTrue(dev.awaken.arms.Arms.broken(beast) && beast.staggered(), "the deflects broke its stance and it kneels");
            // cut down while it kneels
            for (int i = 0; i < 40 && beast.isAlive(); i++) {
                beast.damageCooldownTime = 0;
                beast.hurtServer(level, level.damageSources().playerAttack(p), 1000F);
            }
            h.assertTrue(beast.isDeadOrDying(), "it is down");
            h.assertTrue(Quarry.state(beast).hunters.contains(p.getUUID()), "its hunter is remembered, so its body will lie");
            // queued after the carcass's own wait (Carcass.layWhenGone), so each tick it runs just after that one: it sees the
            // body in the tick it is laid, before its first tick
            AABB near = beast.getBoundingBox().inflate(8);
            Runnable[] watch = new Runnable[1];
            watch[0] = () -> {
                if (seen.problem != null) return;
                List<Mob> bodies = level.getEntitiesOfClass(Mob.class, near, Carcass::is);
                if (bodies.isEmpty()) {
                    if (++seen.waited < 200) Later.run(level.getServer(), 1, watch[0]);
                    else seen.problem = "no body was laid";
                    return;
                }
                seen.body = bodies.getFirst();
                try {
                    seen.problem = firstTick(level, seen.body);
                } catch (RuntimeException ex) { // Later would only log it: the test says what the tick threw
                    seen.problem = "the body's first tick threw " + ex;
                }
            };
            Later.run(level.getServer(), 1, watch[0]);
        }).thenWaitUntil(() -> h.assertTrue(seen.problem != null, "its body is laid (" + seen.waited + " ticks)"))
                .thenExecute(() -> h.assertTrue(seen.problem.isEmpty(), seen.problem))
                .thenIdle(20)
                .thenExecute(() -> {
                    Mob body = seen.body;
                    h.assertTrue(!body.isRemoved() && Carcass.is(body) && body.tickCount >= 20, "the body lies there, ticking (" + body.tickCount + " ticks)");
                })
                .thenSucceed();
    }

    /**
     * A body's minute is counted in the level it lies in (SPEC §41.4): one in a gate's realm or a stratum used to be looked
     * for in the overworld only, so it lay there until the land was reloaded. The GameTest server has no realms, so the
     * Nether stands in: a body whose minute is up goes at the next second's sweep, and its bones stay there as a sign.
     */
    static void goesOffTheOverworld(GameTestHelper h) {
        ServerLevel far = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(far != null, "the Nether exists");
        BlockPos at = new BlockPos(24000 + 8, 120, 8); // out where a realm's cell would be, held loaded for the test
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        far.setChunkForced(cx, cz, true);
        Mob[] body = new Mob[1];
        AwakenTests.onEnd(h, () -> {
            if (body[0] != null && !body[0].isRemoved()) body[0].discard();
            far.setChunkForced(cx, cz, false);
        });
        far.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
        far.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        far.setBlockAndUpdate(at.above(), Blocks.AIR.defaultBlockState());
        var cow = net.minecraft.world.entity.EntityTypes.COW.create(far, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        h.assertTrue(cow != null, "a cow to lay");
        cow.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        body[0] = Carcass.lay(far, cow, Set.of());
        h.assertTrue(body[0] != null && Carcass.is(body[0]) && body[0].level() == far, "its body lies in the Nether");
        body[0].setData(dev.awaken.living.LivingContent.CARCASS, far.getGameTime() - 1); // its minute is up
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(body[0].isRemoved(), "a body whose minute is up in another level goes within a second"))
                .thenExecute(() -> {
                    boolean bones = false;
                    for (dev.awaken.living.population.Spoor.Mark m : dev.awaken.living.population.Spoor.of(far).near(body[0].getX(), body[0].getZ(), 1)) {
                        if (m.kind() == dev.awaken.living.population.Spoor.Kind.KILL && m.species().equals(Carcass.HUNTER)) bones = true;
                    }
                    h.assertTrue(bones, "its bones stay where it lay, in its own level");
                })
                .thenSucceed();
    }

    /**
     * The body's first tick, in a gate (the Hive's land is a gate level): the walk over its dirty attributes must find its
     * scale there (the copy was given the dead's), and answering it must move nothing; then the tick itself. "" when fine.
     */
    private static String firstTick(ServerLevel level, Mob body) {
        if (body.tickCount != 0) return "the body had ticked before it was seen (" + body.tickCount + ")";
        boolean scale = false;
        for (AttributeInstance a : body.getAttributes().getAttributesToUpdate()) scale |= a.getAttribute().is(Attributes.SCALE);
        if (!scale) return "the body's scale is not waiting for its first tick, so this walk would not refresh its size";
        ServerLevel was = GateService.gateLevel(level.getServer()); // another test's stand-in, or none on the test server
        GateService.useTestLevel(level);
        try {
            String moved = AttributeWalk.replay(body);
            if (moved != null) return "the carcass's first tick, " + moved;
            body.tick(); // the tick itself, vanilla's walk and all, under the gate's rules
            return "";
        } finally {
            GateService.useTestLevel(was);
        }
    }
}
