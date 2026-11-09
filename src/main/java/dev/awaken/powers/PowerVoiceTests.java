package dev.awaken.powers;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Powers in Full Voice (SPEC §50): one test per mechanism of the proof. Sounds and looks are Euan's to judge in game. */
final class PowerVoiceTests {
    private PowerVoiceTests() {}

    static void register() {
        AwakenTests.registerIsolated("powers_voice_haymaker_stance", 200, PowerVoiceTests::haymakerStance);
        AwakenTests.register("powers_voice_wave_ground", 20, PowerVoiceTests::waveGround);
        AwakenTests.registerIsolated("powers_voice_well_eats_shot", 100, PowerVoiceTests::wellEatsShot);
        AwakenTests.registerIsolated("powers_voice_tunnel_never_shuts_on_you", 400, PowerVoiceTests::tunnel);
        AwakenTests.register("powers_voice_bonus_cap", 20, h -> {
            h.assertTrue(PowerMath.capBonus(3f) == PowerMath.MAX_BONUS, "no bonus goes past the cap");
            h.assertTrue(PowerMath.capBonus(1.25f) == 1.25f, "a bonus under the cap is left alone");
            h.assertTrue(PowerMath.pressedAge(30, 150) == 27, "a hold is judged at the moment you pressed");
            h.assertTrue(PowerMath.pressedAge(30, 5000) == 24, "however bad the connection, only so much is forgiven");
            h.succeed();
        });
    }

    /** A held Haymaker lets go into a body's stance, and a held Railgun's weights are the same road. */
    private static void haymakerStance(GameTestHelper h) {
        floor(h, 6);
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType hay = PowerRegistry.get("haymaker");
        Powers.setLevel(sp, hay, 1, 0);
        Zombie z = zombie(h, new BlockPos(0, 1, 2));
        float hp = z.getHealth();
        h.assertTrue(Casting.cast(sp, hay, 0, false, 0f, 0f).ok(), "haymaker starts");
        h.onEachTick(() -> Casting.tickPlayer(sp)); // a mock player isn't ticked
        h.runAfterDelay(22, () -> Casting.release(sp, hay));
        h.succeedWhen(() -> {
            h.assertTrue(z.getHealth() < hp, "the punch landed");
            h.assertTrue(dev.awaken.arms.Arms.posture(z) > 0, "and it filled the body's stance");
        });
    }

    /** The wave goes over real ground: across the flat, up one block, not through a wall. */
    private static void waveGround(GameTestHelper h) {
        floor(h, 9);
        ServerLevel level = h.getLevel();
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
        for (int z = -2; z <= 2; z++) for (int x = 4; x <= 9; x++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos o = h.absolutePos(new BlockPos(0, 0, 0));
        double ox = o.getX() + 0.5, oz = o.getZ() + 0.5;
        int y = WaveSurface.start(level, ox, o.getY() + 1, oz);
        h.assertTrue(y == o.getY(), "the wave starts on the ground under you: " + y + " not " + o.getY());
        h.assertTrue(WaveSurface.reach(level, ox, y, oz, ox - 6, oz).reached(), "it crosses flat ground");
        WaveSurface.Reach up = WaveSurface.reach(level, ox, y, oz, ox + 6, oz);
        h.assertTrue(up.reached() && up.climb() >= 1, "it rides up a one-block step");
        h.assertTrue(!WaveSurface.reach(level, ox, y, oz, ox, oz + 6).reached(), "a wall stops it");
        h.succeed();
    }

    /** A shot that reaches the middle of the well is gone. */
    private static void wellEatsShot(GameTestHelper h) {
        floor(h, 8);
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 5; y++) h.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType well = PowerRegistry.get("gravity_well");
        Powers.setLevel(sp, well, 1, 0);
        h.assertTrue(Casting.cast(sp, well, 0, false, 0f, 0f).ok(), "the well opens");
        h.onEachTick(() -> Casting.tickPlayer(sp)); // a mock player isn't ticked
        Running running = PowerState.of(sp).running(well.id.toString());
        h.assertTrue(running != null, "the well is running");
        Vec3 centre = running.pos;
        Arrow arrow = net.minecraft.world.entity.EntityTypes.ARROW.create(h.getLevel(), EntitySpawnReason.COMMAND);
        arrow.setPos(centre.x, centre.y, centre.z - 0.5);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 0.2);
        h.getLevel().addFreshEntity(arrow);
        h.succeedWhen(() -> h.assertTrue(arrow.isRemoved(), "the well ate the arrow"));
    }

    /** A tunnel closes by itself, puts back every block, and waits for anyone still inside it. */
    private static void tunnel(GameTestHelper h) {
        floor(h, 6);
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 5; y++) for (int z = 2; z <= 9; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        ServerPlayer sp = player(h, new BlockPos(0, 1, 0));
        PowerType rail = PowerRegistry.get("railgun");
        Powers.setLevel(sp, rail, 5, 0);
        Cast c = new Cast(sp, rail, 0, 5, 0, false, 0f, 0f);
        Vec3 eye = sp.getEyePosition();
        Vec3 wall = new Vec3(eye.x, eye.y, h.absoluteVec(new Vec3(0, 0, 2)).z);
        Vec3 end = Bore.through(c, eye, new Vec3(0, 0, 1), wall, 48, 6);
        h.assertTrue(end.z > wall.z + 5, "the shot went on through the rock");
        BlockPos deep = BlockPos.containing(eye.x, eye.y, wall.z + 3.5);
        h.assertTrue(h.getLevel().getBlockState(deep).isAir(), "the line is open");
        h.assertTrue(TerrainLedger.of(h.getLevel()).size() > 0, "and every block is in the ledger");
        ServerPlayer inside = h.makeMockServerPlayerInLevel();
        inside.snapTo(deep.getX() + 0.5, deep.getY() - 1, deep.getZ() + 0.5, 0f, 0f);
        h.runAfterDelay(140, () -> {
            h.assertTrue(h.getLevel().getBlockState(deep).isAir(), "it does not shut on someone inside it");
            Vec3 out = h.absoluteVec(new Vec3(3.5, 1, 0.5));
            inside.snapTo(out.x, out.y, out.z, 0f, 0f);
            h.succeedWhen(() -> h.assertTrue(h.getLevel().getBlockState(deep).is(Blocks.STONE), "once they are out the rock comes back"));
        });
    }

    private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(rel));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f); // yaw 0 faces +z
        return sp;
    }

    private static Zombie zombie(GameTestHelper h, BlockPos rel) {
        Zombie z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, rel);
        z.setNoAi(true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500); // a full punch kills a plain zombie outright
        z.setHealth(500);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET)); // or the sun does the damage
        return z;
    }

    private static void floor(GameTestHelper h, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }
}
