package dev.awaken.body;

import dev.awaken.Awaken;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.GateTestAccess;
import dev.awaken.gates.world.Room;
import dev.awaken.gates.world.RoomGeometry;
import dev.awaken.powers.Powers;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GameTests for the Awakened Body (run headless with {@code tools/gradle.sh 1200 runGameTestServer}). The validation is
 * plain functions ({@link BodyRules}) and the server's own start checks ({@link BodyServer}), driven with mock players
 * the server doesn't tick; real gate floors come from the gate generator itself.
 */
final class BodyTests {
    private BodyTests() {}

    /**
     * The moves' optional full-body clips (Player Animation Library): every clip the body plays is in the generated file
     * with its Bendable Cuboids twin, held moves loop and the rest ease out, and only the twin bends (elbows forward,
     * knees back).
     */
    private static void clips(GameTestHelper h) {
        com.google.gson.JsonObject anims;
        try (java.io.InputStream in = BodyTests.class.getResourceAsStream("/" + BodyClips.FILE)) {
            h.assertTrue(in != null, "missing " + BodyClips.FILE + " (python3 tools/art/specs/body_moves.py build)");
            anims = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        for (Move m : Move.values()) if (!m.runner()) h.assertTrue(BodyClips.ALL.contains(BodyClips.of(m)), m + " has a clip");
        // the Runner's moves pick theirs by variant: every one is in the file
        for (String c : BodyClips.RUNNER) h.assertTrue(BodyClips.ALL.contains(c), c + " is listed");
        for (String base : BodyClips.ALL) {
            for (String name : new String[]{base, base + "_bent"}) {
                h.assertTrue(anims.has(name), name + " is missing from " + BodyClips.FILE);
                com.google.gson.JsonObject a = anims.getAsJsonObject(name);
                boolean loops = a.has("loop") && a.get("loop").getAsBoolean();
                h.assertTrue(loops == (base.equals(BodyClips.SLIDE) || base.equals(BodyClips.SPRINT) || BodyClips.loops(base)),
                        name + (loops ? " loops but ends" : " should loop"));
                if (!loops) h.assertTrue(a.has("player_animation_library"), name + " eases back to the vanilla pose (endTick)");
                for (var bone : a.getAsJsonObject("bones").entrySet()) {
                    com.google.gson.JsonObject b = bone.getValue().getAsJsonObject();
                    if (!b.has("bend")) continue;
                    h.assertTrue(name.endsWith("_bent") && bone.getKey().matches("(right|left)_(arm|leg)"), name + ": only the bent twin's limbs bend");
                    for (var key : b.getAsJsonObject("bend").entrySet()) {
                        float v = key.getValue().getAsJsonObject().getAsJsonArray("vector").get(0).getAsFloat();
                        h.assertTrue(bone.getKey().endsWith("arm") ? v <= 0 && v >= -150 : v >= 0 && v <= 150, name + " " + bone.getKey() + " bends the wrong way: " + v);
                    }
                }
            }
        }
        h.succeed();
    }

    static void register() {
        AwakenTests.register("body_clips", 20, BodyTests::clips);
        AwakenTests.register("body_action_camera", 20, BodyTests::actionCamera);
        AwakenTests.register("body_free_run", 20, BodyTests::freeRun);
        AwakenTests.registerIsolated("body_mantle_ledges", 100, BodyTests::mantleLedges);
        AwakenTests.registerIsolated("body_mantle_stair_tops", 100, BodyTests::mantleStairTops);
        AwakenTests.register("body_mantle_gate_walls", 400, BodyTests::mantleGateWalls);
        AwakenTests.registerIsolated("body_vault_rails", 100, BodyTests::vaultRails);
        AwakenTests.registerIsolated("body_slide_gap_and_wall", 100, BodyTests::slideGapAndWall);
        AwakenTests.registerIsolated("body_stair_slide", 100, BodyTests::stairSlide);
        AwakenTests.registerIsolated("body_land_slide", 100, BodyTests::landSlide);
        AwakenTests.register("body_soft_landing_damage", 60, h -> withoutKit(h, BodyTests::softLanding));
        AwakenTests.register("body_soft_landing_before_awakening", 60, BodyTests::softLandingRunner);
        AwakenTests.register("body_soft_landing_never_stacks", 100, BodyTests::softLandingNeverStacks);
        // the old rule (vanilla until the awakening, ranks unlock) holds with the Runner's Kit switched off
        AwakenTests.register("body_nothing_before_awakening", 60, h -> withoutKit(h, BodyTests::nothingBeforeAwakening));
        AwakenTests.register("body_unlocks_announced_once", 60, h -> withoutKit(h, BodyTests::announcedOnce));
        AwakenTests.register("body_runners_kit_from_the_start", 60, BodyTests::kitFromTheStart);
        AwakenTests.register("body_ranks_improve_moves", 20, BodyTests::ranksImprove);
        AwakenTests.register("body_tactical_sprint", 240, BodyTests::tacticalSprint);
        AwakenTests.registerIsolated("body_momentum_hit_bands", 100, BodyTests::momentumBands);
        AwakenTests.registerIsolated("body_slide_trips_small_foes", 100, BodyTests::slideTrips);
        RunnerTests.register(); // spike S3
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    static ServerPlayer player(GameTestHelper h, @Nullable Rank rank) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.markClientLoaded(); // otherwise a fresh player can't be hurt
        if (rank != null) {
            Progression.awaken(p);
            while (Progression.rank(p).ordinal() < rank.ordinal()) Progression.passReassessment(p);
        }
        return p;
    }

    static void at(GameTestHelper h, ServerPlayer p, double x, double y, double z, float yaw) {
        Vec3 v = h.absoluteVec(new Vec3(x, y, z));
        p.snapTo(v.x, v.y, v.z, yaw, 0F);
        p.setOnGround(true);
    }

    static void floor(GameTestHelper h, int x0, int x1, int z0, int z1, int clear) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= clear; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    static BodyRules.@Nullable Ledge ledge(GameTestHelper h, double x, double z, double reach) {
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        return BodyRules.findLedge(h.getLevel(), CollisionContext.empty(), v.x, v.y, v.z, 0F, reach);
    }

    static void done(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    // ---- mantle -----------------------------------------------------------------------------------------------------

    /**
     * A block and a slab (1.5) is a ledge; two blocks, a low ceiling, a plain step or a far one are not. A fence and a
     * wall are no ledge to mantle either: they're crossings (VaultRules, SPEC §17.27; body_vault_rails).
     */
    static void mantleLedges(GameTestHelper h) {
        floor(h, -1, 15, -1, 6, 5);
        BlockState slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        BlockState topSlab = slab.setValue(SlabBlock.TYPE, SlabType.TOP);
        // facing +z from z = 0.5, each lane's obstacle starts at z = 1
        h.setBlock(new BlockPos(0, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(0, 2, 1), slab);                       // 1.5: yes
        h.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);               // 2.0: too high
        h.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(4, 2, 1), slab);
        h.setBlock(new BlockPos(4, 3, 1), topSlab);                    // 1.5 with one block of room over it
        h.setBlock(new BlockPos(6, 1, 1), Blocks.OAK_FENCE);           // a fence (1.5 tall collision)
        h.setBlock(new BlockPos(8, 1, 1), Blocks.COBBLESTONE_WALL);    // a wall
        h.setBlock(new BlockPos(10, 1, 1), Blocks.STONE);              // one block: vanilla's jump
        h.setBlock(new BlockPos(12, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(12, 2, 2), slab);                      // 1.5 but out of reach
        h.setBlock(new BlockPos(14, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(14, 2, 1), slab);
        h.setBlock(new BlockPos(14, 3, 0), Blocks.STONE);              // 1.5 under a ceiling over the player's head
        h.startSequence().thenIdle(2).thenExecute(() -> {
            BodyRules.Ledge ok = ledge(h, 0.5, 0.5, BodyRules.MANTLE_REACH);
            h.assertTrue(ok != null, "a block topped with a slab (1.5) is a ledge");
            h.assertTrue(Math.abs(ok.height() - 1.5) < 1e-3, "it is 1.5 above the feet, was " + ok.height());
            h.assertTrue(ledge(h, 2.5, 0.5, BodyRules.MANTLE_REACH) == null, "a 2-block ledge is too high");
            h.assertTrue(ledge(h, 4.5, 0.5, BodyRules.MANTLE_REACH) == null, "one block of room over the ledge is not enough");
            h.assertTrue(ledge(h, 6.5, 0.5, BodyRules.MANTLE_REACH) == null, "a fence is no ledge to mantle (it's vaulted)");
            h.assertTrue(ledge(h, 8.5, 0.5, BodyRules.MANTLE_REACH) == null, "a wall is no ledge to mantle (it's vaulted or climbed onto)");
            h.assertTrue(ledge(h, 10.5, 0.5, BodyRules.MANTLE_REACH) == null, "a plain block step stays a vanilla jump");
            h.assertTrue(ledge(h, 12.5, 0.5, BodyRules.MANTLE_REACH) == null, "a ledge more than reach away is not grabbed");
            h.assertTrue(ledge(h, 14.5, 0.5, BodyRules.MANTLE_REACH) == null, "a ceiling over the head stops the climb");
            // the path ends exactly on the ledge
            double[] path = new double[3];
            Vec3 s = h.absoluteVec(new Vec3(0.5, 1, 0.5));
            BodyRules.mantlePath(s.x, s.y, s.z, ok, BodyRules.MANTLE_TICKS, path);
            h.assertTrue(Math.abs(path[1] - ok.y()) < 1e-6 && Math.abs(path[2] - ok.z()) < 1e-6, "the climb ends on the ledge");
            // the server: rank C climbs it, rank D can't yet, and a claimed ledge that isn't there is refused
            ServerPlayer c = player(h, Rank.C), d = player(h, Rank.D);
            at(h, c, 0.5, 1, 0.5, 0F);
            at(h, d, 0.5, 1, 0.5, 0F);
            h.assertTrue(BodyServer.startMantle(c, 0F, ok.x(), ok.y(), ok.z()), "rank C mantles the 1.5 ledge");
            h.assertTrue(Progression.profile(c).counter("awaken:body_mantle") == 1, "the climb is counted");
            // the Runner's Kit: every rank mantles (SPEC §16.3; without the kit, D had to wait for C)
            h.assertTrue(BodyServer.startMantle(d, 0F, ok.x(), ok.y(), ok.z()), "rank D mantles too");
            ServerPlayer c2 = player(h, Rank.C);
            at(h, c2, 2.5, 1, 0.5, 0F);
            h.assertFalse(BodyServer.startMantle(c2, 0F, ok.x() + 2, ok.y() + 0.5, ok.z()), "the server refuses a ledge that isn't there");
            done(h, c, d, c2);
        });
    }

    /**
     * SPEC §17.23 (Euan, Carillon: "I can't mantle up blocks with a stair on it"): a 1-block wall topped with a stair
     * is climbed by its low half (1.5) and stood on by its high one (2.0), facing away or sideways; a stair facing you
     * is a 2-block face, and an upside-down one a 2-block top: no. A slab-topped wall is climbed onto the slab, and the
     * server takes the stair ledge the client claims.
     */
    static void mantleStairTops(GameTestHelper h) {
        floor(h, -1, 15, -1, 6, 6);
        var stairs = net.minecraft.world.level.block.StairBlock.FACING;
        var half = net.minecraft.world.level.block.StairBlock.HALF;
        BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        // facing +z from z = 0.5, each lane's wall at z = 1: the stair's high back half is on its FACING side
        net.minecraft.core.Direction[] facings = {net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.NORTH};
        for (int i = 0; i < 4; i++) {
            h.setBlock(new BlockPos(2 * i, 1, 1), Blocks.STONE);
            h.setBlock(new BlockPos(2 * i, 2, 1), stair.setValue(stairs, facings[i]));
        }
        h.setBlock(new BlockPos(8, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(8, 2, 1), stair.setValue(stairs, net.minecraft.core.Direction.SOUTH).setValue(half, net.minecraft.world.level.block.state.properties.Half.TOP));
        h.setBlock(new BlockPos(10, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(10, 2, 1), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        h.startSequence().thenIdle(2).thenExecute(() -> {
            String[] names = {"facing away", "sideways (east)", "sideways (west)"};
            for (int i = 0; i < 3; i++) {
                BodyRules.Ledge l = ledge(h, 2 * i + 0.5, 0.5, BodyRules.MANTLE_REACH);
                h.assertTrue(l != null, "a wall topped with a stair " + names[i] + " is a ledge");
                h.assertTrue(Math.abs(l.height() - 2.0) < 1e-3, "landing on the stair's high half (" + names[i] + "), was " + l.height());
            }
            h.assertTrue(ledge(h, 6.5, 0.5, BodyRules.MANTLE_REACH) == null, "a stair facing you is a 2-block face");
            h.assertTrue(ledge(h, 8.5, 0.5, BodyRules.MANTLE_REACH) == null, "an upside-down stair on a block is a 2-block top");
            BodyRules.Ledge slab = ledge(h, 10.5, 0.5, BodyRules.MANTLE_REACH);
            h.assertTrue(slab != null && Math.abs(slab.height() - 1.5) < 1e-3, "a slab-topped wall: onto the slab");
            BodyRules.Ledge away = ledge(h, 0.5, 0.5, BodyRules.MANTLE_REACH);
            ServerPlayer p = player(h, Rank.C);
            at(h, p, 0.5, 1, 0.5, 0F);
            h.assertTrue(BodyServer.startMantle(p, 0F, away.x(), away.y(), away.z()), "the server takes the stair ledge");
            double[] path = new double[3];
            Vec3 s = h.absoluteVec(new Vec3(0.5, 1, 0.5));
            BodyRules.mantlePath(s.x, s.y, s.z, away, BodyRules.MANTLE_TICKS, path);
            h.assertTrue(Math.abs(path[1] - away.y()) < 1e-6, "and the climb ends on the high half");
            done(h, p);
        });
    }

    /**
     * Fences, gates and walls (SPEC §17.27; Euan, 2026-10-26: "make every fence and wall of every type mantleable"): a
     * fence, a closed gate and a nether brick fence are hopped walking and vaulted running, down on the floor beyond and
     * never on a post; a cobblestone wall walked at is climbed onto (the body over its middle) and vaulted running; a
     * fence on a block (a garden wall) is climbed over in one motion; a fence over a drop is climbed onto, never crossed
     * into the drop. Every path is clear of blocks tick by tick (the crawl box up and over, standing down beyond) with the
     * eye low over the rail. The server takes the client's crossing with the crawl box, and refuses a landing that isn't.
     */
    static void vaultRails(GameTestHelper h) {
        floor(h, -1, 17, -1, 7, 4);
        // lanes along +z, each obstacle on the row z = 2
        rail(h, -1, 1, 1, 2, Blocks.OAK_FENCE.defaultBlockState());                                       // lane 0: a fence
        h.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, net.minecraft.core.Direction.SOUTH)); // lane 3: a closed gate
        rail(h, 5, 7, 1, 2, Blocks.COBBLESTONE_WALL.defaultBlockState());                                // lane 6: a wall
        rail(h, 8, 10, 1, 2, Blocks.NETHER_BRICK_FENCE.defaultBlockState());                             // lane 9: a nether brick fence
        for (int x = 11; x <= 13; x++) h.setBlock(new BlockPos(x, 1, 2), Blocks.STONE_BRICKS);           // lane 12: a garden wall
        rail(h, 11, 13, 2, 2, Blocks.OAK_FENCE.defaultBlockState());
        rail(h, 14, 16, 1, 2, Blocks.SPRUCE_FENCE.defaultBlockState());                                   // lane 15: a fence over a drop
        for (int x = 14; x <= 16; x++) for (int z = 3; z <= 7; z++) for (int y = -3; y <= 0; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            double floorY = h.absoluteVec(new Vec3(0, 1, 0)).y, farFace = h.absoluteVec(new Vec3(0, 0, 2.625)).z;
            String[] names = {"a fence", "a closed gate", "a nether brick fence"};
            int[] lanes = {0, 3, 9};
            for (int i = 0; i < 3; i++) {
                VaultRules.Vault hop = cross(h, lanes[i] + 0.5, 1.5, false), run = cross(h, lanes[i] + 0.5, 0.8, true);
                h.assertTrue(hop != null && hop.kind() == VaultRules.HOP, names[i] + " walked at is hopped: " + hop);
                h.assertTrue(run != null && run.kind() == VaultRules.VAULT, names[i] + " run at is vaulted: " + run);
                h.assertTrue(Math.abs(run.top() - floorY - 1.5) < 1e-6 && run.tall(), names[i] + " is a 1.5 rail taller than its block");
                for (VaultRules.Vault v : new VaultRules.Vault[]{hop, run}) {
                    h.assertTrue(Math.abs(v.floor() - floorY) < 1e-6 && v.endZ() > farFace + 0.3,
                            names[i] + ": down on the floor beyond, never on the post (" + v.endZ() + ", " + v.floor() + ")");
                    String fault = pathFault(h, v);
                    h.assertTrue(fault == null, names[i] + ": " + fault);
                }
            }
            // a wall: walked at, up onto its coping with the body over its middle; run at, vaulted
            VaultRules.Vault onto = cross(h, 6.5, 1.5, false), wallRun = cross(h, 6.5, 0.8, true);
            double wallMid = h.absoluteVec(new Vec3(0, 0, 2.5)).z;
            h.assertTrue(onto != null && onto.kind() == VaultRules.ONTO && Math.abs(onto.floor() - floorY - 1.5) < 1e-6
                    && Math.abs(onto.endZ() - wallMid) < 0.1, "a wall walked at: up onto its middle " + onto);
            h.assertTrue(wallRun != null && wallRun.kind() == VaultRules.VAULT, "a wall run at is vaulted " + wallRun);
            h.assertTrue(pathFault(h, onto) == null && pathFault(h, wallRun) == null, "the wall's paths: " + pathFault(h, onto) + " / " + pathFault(h, wallRun));
            // a fence on a block (a garden wall): over it in one motion, walking or running
            VaultRules.Vault high = cross(h, 12.5, 1.3, false), highRun = cross(h, 12.5, 0.8, true);
            h.assertTrue(high != null && high.kind() == VaultRules.HIGH && Math.abs(high.floor() - floorY) < 1e-6, "a garden wall: climbed over " + high);
            h.assertTrue(highRun != null && highRun.kind() == VaultRules.HIGH && pathFault(h, highRun) == null,
                    "a garden wall run at: " + highRun + " " + (highRun == null ? "" : pathFault(h, highRun)));
            // a fence over a drop: up onto its rail and stop there, never over into the drop
            VaultRules.Vault perch = cross(h, 15.5, 1.5, false), perchRun = cross(h, 15.5, 0.8, true);
            h.assertTrue(perch != null && perch.kind() == VaultRules.ONTO && Math.abs(perch.floor() - floorY - 1.5) < 1e-6, "a fence over a drop: onto it " + perch);
            h.assertTrue(perchRun != null && perchRun.kind() == VaultRules.ONTO && pathFault(h, perchRun) == null, "running at it too " + perchRun);
            Awaken.LOG.info("[body] crossings: fence hop {}, fence vault {}, wall onto {}, garden wall {}, perch {}", plan(cross(h, 0.5, 1.5, false)),
                    plan(cross(h, 0.5, 0.8, true)), plan(onto), plan(highRun), plan(perchRun));
            // the server: the client's crossing, with the crawl box, a mantle to everyone else; a landing that isn't there
            ServerPlayer a = player(h, null), b = player(h, null);
            at(h, a, 0.5, 1, 0.8, 0F);
            at(h, b, 0.5, 1, 0.8, 0F);
            VaultRules.Vault v = cross(h, 0.5, 0.8, true);
            h.assertTrue(BodyServer.startVault(a, 0F, v.endX(), v.floor(), v.endZ(), 0.3F, true), "the server takes the vault");
            h.assertTrue(a.getForcedPose() == Pose.SWIMMING && Body.isMantling(a), "with the crawl box, a mantle to everyone else");
            BodyServer.onEnd(a, new dev.awaken.body.net.BodyPayloads.End(Move.VAULT.ordinal()));
            h.assertTrue(a.getForcedPose() == null && !Body.isMantling(a), "and stands again at its end");
            h.assertFalse(BodyServer.startVault(b, 0F, v.endX(), v.floor(), v.endZ() + 2, 0.3F, true), "a landing that isn't there is refused");
            done(h, a, b);
        });
    }

    private static String plan(VaultRules.@Nullable Vault v) {
        return v == null ? "none" : String.format("(up %d, over %d, down %d ticks; over at %.2f b/t)", v.rise(), v.over(), v.down(),
                (v.sB() - v.sA()) / v.over());
    }

    /** A rail along x from x0 to x1 at (y, z), its pieces joined as a builder's would be. */
    private static void rail(GameTestHelper h, int x0, int x1, int y, int z, BlockState s) {
        for (int x = x0; x <= x1; x++) h.setBlock(new BlockPos(x, y, z), s);
        ServerLevel level = h.getLevel();
        for (int x = x0; x <= x1; x++) {
            BlockPos a = h.absolutePos(new BlockPos(x, y, z));
            level.setBlock(a, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(a), level, a),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private static VaultRules.@Nullable Vault cross(GameTestHelper h, double x, double z, boolean run) {
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        return VaultRules.find(h.getLevel(), CollisionContext.empty(), v.x, v.y, v.z, 0F, run ? VaultRules.REACH_RUN : VaultRules.REACH_WALK, run, 0.3);
    }

    /** The first fault of a crossing's path, eight steps a tick (a box meeting a block, the eye high over the rail), or null. */
    static @Nullable String pathFault(GameTestHelper h, VaultRules.Vault v) {
        double[] p = new double[3];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double eye = -1e9;
        int up = v.rise() + v.over();
        for (int i = 0; i <= v.ticks() * 8; i++) {
            double k = i / 8.0;
            v.at(k, p);
            double tall = k <= up ? VaultRules.CRAWL : 1.8;
            if (BodyRules.blocked(h.getLevel(), CollisionContext.empty(), m, p[0] - 0.299, p[1] + 1e-3, p[2] - 0.299, p[0] + 0.299, p[1] + tall - 1e-3, p[2] + 0.299))
                return "the body meets a block at tick " + k;
            if (k >= v.rise() && k <= up) eye = Math.max(eye, p[1] + 0.4);
        }
        return eye > v.top() + 0.6 ? String.format("the eye %.2f over the rail's top", eye - v.top()) : null;
    }

    /**
     * Real gate floors (the generator's own blocks, several themes and layouts): from every spot a player can stand in
     * a room, in eight directions, every ledge the mantle accepts and every crossing of a fence or wall (SPEC §17.27)
     * ends inside a room (or a doorway's tunnel) and under its ceiling. Players can't break gate interiors, so neither
     * may ever be a way out or onto a roof.
     */
    static void mantleGateWalls(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Theme[] themes = {Theme.BELL_CRYPT, Theme.EMBER_MINES, Theme.FROST_MONASTERY, Theme.SKY_ARCHIPELAGO, Theme.CLOCKWORK_FOUNDRY};
        Layout[] layouts = {Layout.LABYRINTH, Layout.HUB_AND_SPOKES, Layout.SPIRAL_TOWER, Layout.OPEN_CAVERN, Layout.ARENA_CHAIN};
        int checked = 0, accepted = 0;
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < themes.length; i++) {
            Theme theme = themes[i];
            GatePlan plan = new GatePlan(9001L + i * 77L, GatePlan.CONTENT_VERSION, Rank.C, theme, layouts[i], List.of(), Twist.NONE, 2,
                    theme.bossId(), Theme.OVERGROWN_TEMPLE, 0);
            int cell = 90 + i;
            GateLayout l = GateTestAccess.build(plan);
            FloorLayout f = l.main.get(0);
            Map<Long, ProtoChunk> chunks = new HashMap<>();
            int ox = Cells.originX(cell) + f.originSlotX * 16, oz = Cells.originZ(cell) + f.originSlotZ * 16;
            for (int gx = -1; gx <= f.w; gx++) for (int gz = -1; gz <= f.h; gz++) {
                int cx = (Cells.originX(cell) >> 4) + f.originSlotX + gx, cz = (Cells.originZ(cell) >> 4) + f.originSlotZ + gz;
                ProtoChunk c = new ProtoChunk(new ChunkPos(cx, cz), UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
                GateTestAccess.fill(c, plan, cell);
                chunks.put(ChunkPos.pack(cx, cz), c);
            }
            BlockGetter view = view(chunks);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (Room r : f.rooms) {
                int floorY = f.baseY + r.dy;
                for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) {
                    int top = floorY + RoomGeometry.ceiling(r, x, z);
                    for (int y = floorY - 2; y < top - 1; y++) {
                        double px = ox + x + 0.5, pz = oz + z + 0.5;
                        if (!standable(view, m, ox + x, y, oz + z)) continue;
                        for (int a = 0; a < 8; a++) {
                            checked++;
                            BodyRules.Ledge ledge = BodyRules.findLedge(view, CollisionContext.empty(), px, y, pz, a * 45F, BodyRules.MANTLE_REACH);
                            // and every crossing of a fence, gate or wall (SPEC §17.27), running and walking
                            for (int run = 0; run < 2 && ledge == null; run++) {
                                VaultRules.Vault v = VaultRules.find(view, CollisionContext.empty(), px, y, pz, a * 45F,
                                        run == 1 ? VaultRules.REACH_RUN : VaultRules.REACH_WALK, run == 1, 0.3);
                                if (v != null) ledge = new BodyRules.Ledge(v.endX(), v.floor(), v.endZ(), v.floor() - y, 0);
                            }
                            if (ledge == null) continue;
                            accepted++;
                            String why = inside(f, ox, oz, ledge);
                            if (why != null && wrong.size() < 6) wrong.add(theme.name() + " room " + r.id + " from (" + x + "," + y + "," + z + ") yaw "
                                    + a * 45 + ": " + why);
                        }
                    }
                }
            }
        }
        h.assertTrue(checked > 2000, "enough standing spots were tried, " + checked);
        h.assertTrue(wrong.isEmpty(), "mantles that leave a room: " + String.join("; ", wrong));
        Awaken.LOG.info("[body] gate walls: {} mantle tries, {} accepted (all inside their rooms)", checked, accepted);
        h.succeed();
    }

    private static boolean standable(BlockGetter v, BlockPos.MutableBlockPos m, int x, int y, int z) {
        BlockState below = v.getBlockState(m.set(x, y - 1, z));
        if (below.getCollisionShape(v, m).isEmpty()) return false;
        return v.getBlockState(m.set(x, y, z)).getCollisionShape(v, m).isEmpty() && v.getBlockState(m.set(x, y + 1, z)).getCollisionShape(v, m).isEmpty();
    }

    /** Null when the ledge's spot is inside a room of the floor (or a doorway's tunnel) and below that room's ceiling. */
    private static @Nullable String inside(FloorLayout f, int ox, int oz, BodyRules.Ledge l) {
        int fx = (int) Math.floor(l.x()) - ox, fz = (int) Math.floor(l.z()) - oz;
        for (Room r : f.rooms) {
            boolean interior = fx >= r.x0() && fx <= r.x1() && fz >= r.z0() && fz <= r.z1();
            boolean tunnel = !interior && r.containsSlot(Math.floorDiv(fx, 16), Math.floorDiv(fz, 16)) && RoomGeometry.tunnel(r, fx, fz) != null;
            if (!interior && !tunnel) continue;
            int ceiling = f.baseY + r.dy + RoomGeometry.ceiling(r, fx, fz);
            if (l.y() + 1.8 > ceiling + 1e-6 && interior) return "lands at y " + l.y() + " above the ceiling at " + ceiling;
            return null;
        }
        return "lands outside every room at floor (" + fx + ", " + fz + "), y " + l.y();
    }

    private static BlockGetter view(Map<Long, ProtoChunk> chunks) {
        BlockState rock = Blocks.STONE.defaultBlockState();
        return new BlockGetter() {
            @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) {
                ProtoChunk c = chunks.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
                return c == null ? rock : c.getBlockState(pos);
            }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return 256; }
            @Override public int getMinY() { return 0; }
        };
    }

    // ---- slide ------------------------------------------------------------------------------------------------------

    /** Moves a body along +z the way a slide does: crawl height, the slide's speed each tick. Returns the distance. */
    static double slide(ServerPlayer p, double v0, boolean crawl) {
        if (crawl) {
            p.setForcedPose(Pose.SWIMMING);
            p.setPose(Pose.SWIMMING);
        }
        double z0 = p.getZ();
        for (int t = 0; t < BodyRules.SLIDE_TICKS; t++) {
            p.move(MoverType.SELF, new Vec3(0, 0, BodyRules.slideSpeed(t, v0)));
            if (p.horizontalCollision) break;
        }
        p.setForcedPose(null);
        return p.getZ() - z0;
    }

    /** The crawl-height slide passes under a 1-block gap a standing body can't, keeps going ~4 blocks, and stops at a wall. */
    static void slideGapAndWall(GameTestHelper h) {
        floor(h, -1, 7, -1, 12, 3);
        for (int z = 1; z <= 3; z++) {
            h.setBlock(new BlockPos(0, 2, z), Blocks.STONE); // lane 0: a 1-block gap (z 1..3)
            h.setBlock(new BlockPos(2, 2, z), Blocks.STONE); // lane 2: the same gap, walked into standing
        }
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(4, y, 3), Blocks.STONE); // lane 4: a wall at z 3
        h.startSequence().thenIdle(2).thenExecute(() -> {
            ServerPlayer a = player(h, Rank.D), b = player(h, Rank.D), c = player(h, Rank.D), d = player(h, Rank.D), e = player(h, Rank.D);
            at(h, a, 0.5, 1, 0.5, 0F);
            double through = slide(a, BodyRules.SLIDE_V0, true);
            h.assertTrue(through > 3.9 && !a.horizontalCollision, "the slide passes under the 1-block gap (" + String.format("%.2f", through) + " blocks)");
            at(h, b, 2.5, 1, 0.5, 0F);
            double standing = slide(b, BodyRules.SLIDE_V0, false);
            h.assertTrue(standing < 0.3, "a standing body is stopped by the same gap (" + String.format("%.2f", standing) + ")");
            at(h, c, 4.5, 1, 0.5, 0F);
            double wall = slide(c, BodyRules.SLIDE_V0, true);
            h.assertTrue(c.horizontalCollision && wall < 2.3, "a wall stops the slide (" + String.format("%.2f", wall) + ")");
            // out of a tactical sprint it goes further
            at(h, d, 6.5, 1, 0.5, 0F);
            double plain = BodyRules.slideDistance(BodyRules.SLIDE_V0), tac = BodyRules.slideDistance(BodyRules.SLIDE_V0_TACTICAL);
            h.assertTrue(Math.abs(plain - 4.0) < 0.25, "a slide covers about 4 blocks, " + plain);
            h.assertTrue(tac > plain + 0.7, "a slide out of a tactical sprint goes further, " + tac + " against " + plain);
            double moved = slide(d, BodyRules.slideEntry(0.3, true), true);
            h.assertTrue(Math.abs(moved - tac) < 0.05, "the tactical slide moves what the numbers say, " + moved);
            // the server: the slide gives the crawl box on its side too, and takes it back at the end
            at(h, e, 6.5, 1, 6.5, 0F);
            BodyServer.assist(e, Move.SLIDE, 40);
            h.assertTrue(BodyServer.startSlide(e, 0F, false), "the server accepts the slide");
            h.assertTrue(e.getForcedPose() == Pose.SWIMMING && e.getBbHeight() < 0.7, "with a crawl-height box");
            h.assertTrue(MovementHooks.sliding(e), "and powers can see it");
            BodyMotion m = BodyServer.motion(e);
            BodyServer.tick(e, m, m.slideUntil);
            h.assertTrue(e.getForcedPose() == null && !Body.isSliding(e), "the box is given back when it ends");
            h.assertFalse(BodyServer.startSlide(e, 0F, false), "and the cooldown holds");
            done(h, a, b, c, d, e);
        });
    }

    /**
     * SPEC §17.22: a slide started on the top of six stairs glides down them (a straight line through the noses, no
     * stall, no hop per step), faster than walking, the server keeping the slide going past its 14 ticks while it sees
     * the run, no fall at the bottom, then the run-out and the end. A way 90° off the fall line is not a run.
     */
    static void stairSlide(GameTestHelper h) {
        floor(h, -1, 3, -1, 16, 9);
        var stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE); // the top, feet at 7
            for (int k = 0; k < 6; k++) { // down toward +z: stair k's high back half at 7 - k
                for (int y = 1; y < 6 - k; y++) h.setBlock(new BlockPos(x, y, 3 + k), Blocks.STONE);
                h.setBlock(new BlockPos(x, 6 - k, 3 + k), stair);
            }
        }
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var level = h.getLevel();
            var ctx = CollisionContext.empty();
            BodyRules.StairProfile sp = new BodyRules.StairProfile();
            double[] fall = new double[2], out = new double[3];
            Vec3 across = h.absoluteVec(new Vec3(1.5, 7, 2.6));
            h.assertFalse(BodyRules.stairStart(level, ctx, sp, across.x, across.y, across.z, 1, 0, fall), "sliding across the stairs is no run");
            ServerPlayer p = player(h, Rank.D);
            at(h, p, 1.5, 7, 1.0, 0F);
            BodyServer.assist(p, Move.SLIDE, 40);
            h.assertTrue(BodyServer.startSlide(p, 0F, false), "the server accepts the slide");
            BodyMotion m = BodyServer.motion(p);
            long now = BodyServer.now(p);
            double[] way = {0, 1};
            double v = BodyRules.SLIDE_V0, z0 = p.getZ(), bottom = h.absoluteVec(new Vec3(0, 1, 0)).y;
            boolean stairs = false, serverSaw = false;
            int age = 0, onRun = 0, reached = -1;
            double lastDy = Double.NaN, worstJerk = 0, slowest = 9, shallowest = -9;
            int gliding = 0;
            StringBuilder path = new StringBuilder();
            for (int t = 1; t <= 60 && reached < 0; t++) {
                now++;
                age++;
                double y0 = p.getY(), zBefore = p.getZ();
                if (!stairs && BodyRules.stairStart(level, ctx, sp, p.getX(), p.getY(), p.getZ(), way[0], way[1], fall)) stairs = true;
                if (stairs) { // exactly the client's tick (ClientMoves.applyStairs)
                    BodyRules.stairBend(way, fall[0], fall[1]);
                    v = BodyRules.stairSpeed(v);
                    if (BodyRules.stairStep(level, ctx, sp, p.getX(), p.getY(), p.getZ(), way[0], way[1], v, out)) {
                        p.setPos(out[0], p.getY(), out[2]);
                        p.move(MoverType.SELF, new Vec3(0, out[1] - p.getY(), 0));
                        onRun++;
                        double dy = p.getY() - y0;
                        path.append(String.format(" %.2f/%.2f", p.getZ() - z0, dy));
                        // the glide, once it has tipped over the top (a real corner) and before the floor
                        if (gliding > 0 && !sp.last) {
                            if (gliding > 1) worstJerk = Math.max(worstJerk, Math.abs(dy - lastDy));
                            shallowest = Math.max(shallowest, dy);
                        }
                        if (dy < -0.05 || gliding > 0) gliding++;
                        if (onRun > 1) slowest = Math.min(slowest, p.getZ() - zBefore);
                        lastDy = dy;
                        if (sp.last) stairs = false;
                    } else {
                        stairs = false;
                    }
                } else {
                    v = BodyRules.slideSpeed(age, BodyRules.SLIDE_V0);
                    p.move(MoverType.SELF, new Vec3(way[0] * v, -0.08, way[1] * v));
                }
                BodyServer.tick(p, m, now);
                serverSaw |= MovementHooks.stairSliding(p);
                if (p.getY() <= bottom + 1e-3) reached = t;
            }
            h.assertTrue(reached > 0, "the slide reaches the bottom (feet at " + String.format("%.2f", p.getY() - bottom) + " over it)");
            double speed = (p.getZ() - z0) / reached;
            h.assertTrue(speed > 0.3, "faster than walking (0.22 a tick): " + String.format("%.2f", speed) + " over " + reached + " ticks");
            h.assertTrue(onRun >= 10 && slowest > 0.3, "no stall on the run (" + onRun + " ticks, slowest " + String.format("%.2f", slowest) + ")");
            h.assertTrue(gliding > 8 && shallowest < -0.25 && worstJerk < 0.05, "a straight glide, no hop per step (" + gliding
                    + " ticks, shallowest drop " + String.format("%.2f", shallowest) + ", worst change of drop " + String.format("%.3f", worstJerk) + "; z/dy" + path + ")");
            h.assertTrue(serverSaw && reached > BodyRules.SLIDE_TICKS + BodyRules.SLIDE_GRACE && Body.isSliding(p),
                    "the server keeps the slide going down the run (" + reached + " ticks)");
            h.assertTrue(p.fallDistance < 1, "and the ride is no fall (" + p.fallDistance + ")");
            Awaken.LOG.info("[body_stair_slide] bottom after {} ticks, {} b/t, glide {} ticks, worst change of drop {}, z/dy{}",
                    reached, String.format("%.2f", speed), gliding, String.format("%.3f", worstJerk), path);
            for (int t = 0; t < 30 && Body.isSliding(p); t++) BodyServer.tick(p, m, ++now);
            h.assertFalse(Body.isSliding(p), "after the run-out the slide ends");
            done(h, p);
        });
    }

    /**
     * SPEC §17.24: a sprint jump landed with crouch held slides from the landing, farther than a slide from a run; a
     * walk-speed landing doesn't; an edge with a drop ahead is an edge-sneak; the server takes the landing slide from a
     * body that sprinted a moment ago (the sprint flag can drop in the air) and refuses one that never ran.
     */
    static void landSlide(GameTestHelper h) {
        floor(h, -1, 7, -1, 12, 3);
        for (int z = 6; z <= 12; z++) h.setBlock(new BlockPos(6, 0, z), Blocks.AIR); // lane 6: the floor ends at z 6
        h.startSequence().thenIdle(2).thenExecute(() -> {
            double air = 0.32; // a sprint jump's horizontal speed as it lands (0.28 run + 0.2 kick, air drag over ~12 ticks)
            h.assertTrue(BodyRules.landSlideFast(air, true), "a sprint jump's landing slides");
            h.assertFalse(BodyRules.landSlideFast(0.1, true) || BodyRules.landSlideFast(0.22, false), "a walk's or a standing hop's doesn't");
            double v0 = BodyRules.landSlideEntry(air, false);
            ServerPlayer a = player(h, Rank.D), b = player(h, Rank.D), c = player(h, Rank.D), d = player(h, Rank.D);
            at(h, a, 0.5, 1, 0.5, 0F);
            at(h, b, 2.5, 1, 0.5, 0F);
            double landing = slide(a, v0, true), run = slide(b, BodyRules.SLIDE_V0, true);
            h.assertTrue(landing > run + 0.5, "the landing slide carries farther: " + String.format("%.2f against %.2f", landing, run));
            var ctx = CollisionContext.empty();
            Vec3 flat = h.absoluteVec(new Vec3(4.5, 1, 2.5)), edge = h.absoluteVec(new Vec3(6.5, 1, 5.8));
            h.assertTrue(BodyRules.groundAhead(h.getLevel(), ctx, flat.x, flat.y, flat.z, 0, 1), "ground ahead on the floor");
            h.assertFalse(BodyRules.groundAhead(h.getLevel(), ctx, edge.x, edge.y, edge.z, 0, 1), "landing at an edge with a drop ahead is an edge-sneak");
            // the server: no sprint flag now, but a run a moment ago (the jump); a body that never ran is refused
            at(h, c, 4.5, 1, 0.5, 0F);
            at(h, d, 4.5, 1, 8.5, 0F);
            long now = BodyServer.now(c);
            BodyServer.motion(c).lastSprint = now - 12;
            h.assertTrue(!c.isSprinting() && BodyServer.startSlide(c, 0F, false), "the server takes the landing slide of a sprint jump");
            h.assertFalse(BodyServer.startSlide(d, 0F, false), "and refuses one that never ran");
            done(h, a, b, c, d);
        });
    }

    /** Small hostile creatures in the slide's way stagger; a tall one and a farm animal don't. */
    static void slideTrips(GameTestHelper h) {
        floor(h, -2, 4, -2, 6, 3);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            ServerPlayer p = player(h, Rank.D);
            at(h, p, 0.5, 1, 0.5, 0F);
            Mob small = spawn(h, EntityTypes.SILVERFISH, 0.5, 1.1);
            Mob tall = spawn(h, EntityTypes.ZOMBIE, 1.2, 1.1);
            Mob pig = spawn(h, EntityTypes.PIG, -0.4, 1.0);
            BodyServer.assist(p, Move.SLIDE, 40);
            h.assertTrue(BodyServer.startSlide(p, 0F, false), "slide started");
            int n = BodyServer.trip(p, BodyServer.motion(p));
            h.assertTrue(n == 1, "exactly the small foe is tripped, " + n);
            h.assertTrue(small.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "it staggers");
            h.assertFalse(tall.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "a zombie keeps its feet");
            h.assertFalse(pig.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "a pig is no foe");
            h.assertTrue(BodyServer.trip(p, BodyServer.motion(p)) == 0, "once per slide");
            small.discard();
            tall.discard();
            pig.discard();
            done(h, p);
        });
    }

    static Mob spawn(GameTestHelper h, net.minecraft.world.entity.EntityType<? extends Mob> type, double x, double z) {
        Mob m = type.create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        m.snapTo(v.x, v.y, v.z, 180F, 0F);
        m.setNoAi(true);
        m.setPersistenceRequired();
        h.getLevel().addFreshEntity(m);
        return m;
    }

    // ---- soft landing -----------------------------------------------------------------------------------------------

    static float fall(ServerPlayer p, double distance) {
        p.setHealth(p.getMaxHealth());
        p.damageCooldownTime = 0;
        p.setInvulnerableTime(0);
        float before = p.getHealth();
        p.causeFallDamage(distance, 1.0F, p.damageSources().fall());
        return before - p.getHealth();
    }

    /** Vanilla at 6, 9, 12 blocks: 3, 6, 9. Rolling (sprinting or crouching): +3 safe, ×0.6 beyond: 0, 1, 3. */
    static void softLanding(GameTestHelper h) {
        ServerPlayer vanilla = player(h, null), standing = player(h, Rank.E), sprint = player(h, Rank.E), crouch = player(h, Rank.E);
        vanilla.setSprinting(true); // sprinting before the awakening is still vanilla
        sprint.setSprinting(true);
        crouch.setShiftKeyDown(true);
        double[] heights = {6, 9, 12};
        for (double d : heights) {
            int want = BodyRules.fallDamage(d, 3, 1);
            int rolled = BodyRules.fallDamage(d - BodyRules.ROLL_SAFE_EXTRA, 3, BodyRules.ROLL_DAMAGE);
            h.assertTrue(fall(vanilla, d) == want, "vanilla at " + d + ": " + want);
            h.assertTrue(fall(standing, d) == want, "awakened but not rolling at " + d + ": " + want);
            float s = fall(sprint, d), c = fall(crouch, d);
            h.assertTrue(s == rolled, "rolling (sprint) at " + d + ": " + rolled + ", was " + s);
            h.assertTrue(c == rolled, "rolling (crouch) at " + d + ": " + rolled + ", was " + c);
        }
        h.assertTrue(BodyRules.fallDamage(6, 3, 1) == 3 && BodyRules.fallDamage(3, 3, 0.6) == 0 && BodyRules.fallDamage(9 - 3, 3, 0.6) == 1
                && BodyRules.fallDamage(12 - 3, 3, 0.6) == 3, "the table: 6 → 3/0, 9 → 6/1, 12 → 9/3");
        h.assertTrue(Progression.profile(sprint).counter("awaken:body_roll") == 3, "each roll counted");
        done(h, vanilla, standing, sprint, crouch);
    }

    /** With the Runner's Kit a body rolls before the awakening too (sprinting into the fall). */
    static void softLandingRunner(GameTestHelper h) {
        ServerPlayer runner = player(h, null);
        runner.setSprinting(true);
        for (double d : new double[]{6, 9, 12}) {
            int rolled = BodyRules.fallDamage(d - BodyRules.ROLL_SAFE_EXTRA, 3, BodyRules.ROLL_DAMAGE);
            h.assertTrue(fall(runner, d) == rolled, "a Runner rolls at " + d + " before the awakening: " + rolled);
        }
        done(h, runner);
    }

    /** The roll never adds to a power's own landing, a mount's fall, an elytra landing or gate Low Gravity. */
    static void softLandingNeverStacks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer power = player(h, Rank.E), rider = player(h, Rank.E), glider = player(h, Rank.E), low = player(h, Rank.E);
        for (ServerPlayer p : new ServerPlayer[]{power, rider, glider, low}) p.setSprinting(true);
        // a Flight power's landing (Hover on, then off: its no-fall window cancels the fall before the roll sees it)
        Powers.fillEnergy(power);
        var src = level.getServer().createCommandSourceStack().withEntity(power).withLevel(level).withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(src, "awaken admin power cast hover");
        level.getServer().getCommands().performPrefixedCommand(src, "awaken admin power cast hover");
        // a mock player isn't ticked: one player tick lets the toggled-off Hover end (its no-fall window starts there)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(power));
        long rolls = Progression.profile(power).counter("awaken:body_roll");
        h.assertTrue(fall(power, 12) == 0, "a power's landing takes nothing");
        h.assertTrue(Progression.profile(power).counter("awaken:body_roll") == rolls, "and is no roll");
        // riding: the mount handles its passengers' falls
        var horse = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
        Vec3 v = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        horse.snapTo(v.x, v.y, v.z, 0F, 0F);
        level.addFreshEntity(horse);
        rider.startRiding(horse, true, false);
        h.assertTrue(rider.isPassenger(), "riding");
        h.assertTrue(fall(rider, 9) == BodyRules.fallDamage(9, 3, 1), "a rider's fall is vanilla's");
        rider.stopRiding();
        horse.discard();
        // an elytra landing
        glider.startFallFlying();
        h.assertTrue(fall(glider, 9) == BodyRules.fallDamage(9, 3, 1), "an elytra landing is vanilla's");
        // gate Low Gravity already gives +8 safe fall: the kinder rule wins, nothing adds up
        AttributeInstance safe = low.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        safe.addOrUpdateTransientModifier(new AttributeModifier(Awaken.id("test_low_gravity"), 8, AttributeModifier.Operation.ADD_VALUE));
        h.assertTrue(fall(low, 12) == BodyRules.fallDamage(12, 11, 1), "Low Gravity alone: 12 blocks → 1");
        safe.removeModifier(Awaken.id("test_low_gravity"));
        // a perk that already softens falls more than the roll keeps its own factor
        double[] r = BodyRules.roll(12, 0, 0.5);
        h.assertTrue(r != null && r[1] == 1.0, "a stronger perk is not multiplied again");
        double[] r2 = BodyRules.roll(12, 2, 1.0);
        h.assertTrue(r2 != null && Math.abs(r2[0] - 11) < 1e-9, "a smaller other safe fall is topped up to the roll's, not added");
        done(h, power, rider, glider, low);
    }

    // ---- awakening and unlocks --------------------------------------------------------------------------------------

    /** Before the awakening the body is vanilla: no move starts, no roll, no momentum, nothing announced. */
    /** Runs a test under the old rule (the Runner's Kit off), putting the switch back whatever happens. */
    static void withoutKit(GameTestHelper h, java.util.function.Consumer<GameTestHelper> test) {
        BodyConfig.kitOverride = false;
        try {
            test.accept(h);
        } finally {
            BodyConfig.kitOverride = null;
        }
    }

    /**
     * The Runner's Kit (SPEC §16.3): every move from the first second, before the awakening; the System only speaks
     * of them from the awakening on, once, all together.
     */
    static void kitFromTheStart(GameTestHelper h) {
        floor(h, -1, 2, -1, 2, 3);
        ServerPlayer p = player(h, null);
        at(h, p, 0.5, 1, 0.5, 0F);
        for (Move m : Move.values()) h.assertTrue(Body.unlocked(p, m), m + " is everyone's");
        p.setSprinting(true);
        BodyServer.assist(p, Move.SLIDE, 40);
        h.assertTrue(BodyServer.startSlide(p, 0F, false), "a slide before the awakening");
        h.assertFalse(BodyServer.announce(p), "the System is silent before the awakening");
        Progression.awaken(p);
        BodyMemory mem = Body.memory(p);
        for (Move m : Move.values()) {
            if (m.runner()) h.assertFalse(mem.announced(m), m + " is found, not told");
            else h.assertTrue(mem.announced(m), m + " announced at the awakening");
        }
        h.assertFalse(BodyServer.announce(p), "never twice");
        done(h, p);
    }

    /** Ranks improve the moves: longer runs at D, a fifth link at C, a faster shimmy and longer ledge jumps at B, shorter recharges. */
    static void ranksImprove(GameTestHelper h) {
        h.assertTrue(RunnerRules.runLength(0, Rank.E) == 35 && RunnerRules.runLength(0, Rank.D) == 40, "a run lasts 35, 40 from D");
        h.assertTrue(RunnerRules.links(Rank.D) == 4 && RunnerRules.links(Rank.C) == 5, "4 links, 5 from C");
        h.assertTrue(RunnerRules.shimmy(Rank.C) == RunnerRules.SHIMMY && Math.abs(RunnerRules.shimmy(Rank.B) * 20 - 2.2) < 1e-9, "shimmy 1.6, 2.2 b/s from B");
        h.assertTrue(RunnerRules.ledgeUp(Rank.B) == RunnerRules.LEDGE_UP + 1 && RunnerRules.ledgeSide(Rank.B) == RunnerRules.LEDGE_SIDE + 1, "ledge reach +1 from B");
        h.assertTrue(BodyRules.tacRecharge(Rank.E) == BodyRules.TAC_RECHARGE && BodyRules.tacRecharge(Rank.A) < BodyRules.tacRecharge(Rank.B)
                && BodyRules.tacRecharge(Rank.SOVEREIGN) == 30, "the tactical sprint recharges faster by rank, to 30");
        h.assertTrue(BodyRules.tacRecharge(Rank.E, 0.25) == 60 && BodyRules.tacRecharge(Rank.A, 0.25) == 30
                && BodyRules.tacRecharge(Rank.E, 0.9) == 40, "a recharge perk takes its share of the rank's, at most half, never under 30");
        h.assertTrue(BodyRules.slideCooldown(Rank.E) == BodyRules.SLIDE_COOLDOWN && BodyRules.slideCooldown(Rank.SOVEREIGN) == 10, "the slide's cooldown by rank, to 10");
        // Wall Stride: 80 ticks, grip 50 lifting +0.04, then a sink to -0.14
        h.assertTrue(RunnerRules.runLength(0, Rank.E, true) == 80 && RunnerRules.runGrip(0, true) == 50, "Wall Stride runs 80, grips 50");
        h.assertTrue(Math.abs(RunnerRules.runTarget(0, 80, 50, true) - 0.04) < 1e-9 && Math.abs(RunnerRules.runTarget(80, 80, 50, true) + 0.14) < 1e-9,
                "Wall Stride lifts +0.04, sinks to -0.14");
        h.succeed();
    }

    static void nothingBeforeAwakening(GameTestHelper h) {
        ServerPlayer p = player(h, null);
        p.setSprinting(true);
        for (Move m : Move.values()) {
            if (m.runner()) {
                h.assertFalse(Body.unlocked(p, m), m + " is off without the kit");
                continue;
            }
            h.assertFalse(Body.unlocked(p, m), m + " is locked");
            BodyServer.assist(p, m, 40); // even with the dev command's help
            boolean started = switch (m) {
                case TACTICAL_SPRINT -> BodyServer.startTactical(p);
                case SLIDE -> BodyServer.startSlide(p, 0F, false);
                case MANTLE -> BodyServer.startMantle(p, 0F, p.getX(), p.getY() + 1.5, p.getZ() + 1);
                case ROLL -> fall(p, 9) != BodyRules.fallDamage(9, 3, 1);
                default -> false;
            };
            h.assertFalse(started, m + " doesn't trigger");
        }
        h.assertTrue(BodyServer.momentumBonus(p, 1F) == 0, "no momentum hits");
        h.assertFalse(BodyServer.announce(p), "nothing to announce");
        h.assertTrue(Body.memory(p).isEmpty(), "and nothing remembered");
        done(h, p);
    }

    /** Each unlock is announced once, when it arrives; the memory survives a save and a load. */
    static void announcedOnce(GameTestHelper h) {
        ServerPlayer p = player(h, null);
        Progression.awaken(p); // the awakening's own hook announces
        BodyMemory mem = Body.memory(p);
        h.assertTrue(mem.announced(Move.ROLL) && mem.announced(Move.TACTICAL_SPRINT), "the awakening announced roll and sprint");
        h.assertFalse(mem.announced(Move.SLIDE), "not the slide yet");
        h.assertFalse(BodyServer.announce(p), "never twice");
        Progression.passReassessment(p);
        h.assertTrue(Body.memory(p).announced(Move.SLIDE) && !Body.memory(p).announced(Move.MANTLE), "D announced the slide");
        Progression.passReassessment(p);
        h.assertTrue(Body.memory(p).announced(Move.MANTLE), "C announced the mantle");
        h.assertFalse(BodyServer.announce(p), "and nothing is left");
        // codec round trip
        Tag tag = BodyMemory.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, Body.memory(p)).getOrThrow();
        BodyMemory back = BodyMemory.MAP_CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
        h.assertTrue(back.equals(Body.memory(p)) && back.schemaVersion() == BodyMemory.SCHEMA, "the memory round-trips with its schema");
        // a real save and load of the player
        HolderLookup.Provider reg = h.getLevel().registryAccess();
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, reg);
        p.saveWithoutId(out);
        CompoundTag saved = out.buildResult();
        p.setData(BodyContent.MEMORY, BodyMemory.EMPTY);
        p.load(TagValueInput.create(ProblemReporter.DISCARDING, reg, saved));
        h.assertTrue(Body.memory(p).equals(back), "the memory survives a save, was " + Body.memory(p));
        h.assertFalse(BodyServer.announce(p), "so a relog announces nothing again");
        done(h, p);
    }

    // ---- tactical sprint --------------------------------------------------------------------------------------------

    /** The speed modifier comes and goes, the recharge holds, riding refuses it, a cast ends it. */
    static void tacticalSprint(GameTestHelper h) {
        ServerPlayer p = player(h, Rank.E);
        p.setSprinting(true);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        double before = speed.getValue();
        float food = exhaustion(h, p);
        h.assertTrue(BodyServer.startTactical(p), "a sprinting awakened player double-taps into it");
        h.assertTrue(speed.hasModifier(BodyRules.TAC_ID), "the speed modifier is on");
        h.assertTrue(Math.abs(speed.getValue() / before - (1 + BodyRules.TAC_SPEED)) < 1e-6, "+25% speed, " + speed.getValue() / before);
        h.assertTrue(exhaustion(h, p) > food, "it costs hunger");
        BodyMotion m = BodyServer.motion(p);
        long end = m.tacUntil;
        BodyServer.tick(p, m, end - 1);
        h.assertTrue(speed.hasModifier(BodyRules.TAC_ID), "still on before 3 s");
        BodyServer.tick(p, m, end);
        h.assertFalse(speed.hasModifier(BodyRules.TAC_ID), "off after 3 s");
        h.assertFalse(BodyServer.startTactical(p), "recharging");
        // stopping the run ends it
        ServerPlayer q = player(h, Rank.E);
        q.setSprinting(true);
        h.assertTrue(BodyServer.startTactical(q), "second runner");
        q.setSprinting(false);
        BodyMotion mq = BodyServer.motion(q);
        long t = BodyServer.now(q);
        for (int i = 1; i <= 3; i++) BodyServer.tick(q, mq, t + i);
        h.assertFalse(q.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(BodyRules.TAC_ID), "stopping the run ends it");
        // a cast ends it at once (powers calls this before every cast)
        ServerPlayer c = player(h, Rank.E);
        c.setSprinting(true);
        h.assertTrue(BodyServer.startTactical(c), "third runner");
        MovementHooks.castStarting(c);
        h.assertFalse(c.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(BodyRules.TAC_ID), "a cast ends it");
        // riding: refused
        ServerPlayer r = player(h, Rank.E);
        r.setSprinting(true);
        var boat = EntityTypes.OAK_BOAT.create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 v = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        boat.snapTo(v.x, v.y, v.z, 0F, 0F);
        h.getLevel().addFreshEntity(boat);
        r.startRiding(boat, true, false);
        h.assertFalse(BodyServer.startTactical(r), "not while riding");
        r.stopRiding();
        boat.discard();
        // the sprint above was ended at its own 3 s mark (driven ahead of the clock), so the recharge runs from there
        h.startSequence().thenIdle(BodyRules.TAC_TICKS + BodyRules.TAC_RECHARGE + 2).thenExecute(() -> {
            h.assertTrue(BodyServer.startTactical(p), "ready again after the recharge");
            BodyServer.endTactical(p, BodyServer.motion(p), BodyServer.now(p), false);
            done(h, p, q, c, r);
        });
    }

    // ---- momentum hits ----------------------------------------------------------------------------------------------

    /** A full swing gains by horizontal speed: nothing at a walk, ~+10% at a sprint, ~+18% tactical, +30% from a slide. */
    static void momentumBands(GameTestHelper h) {
        floor(h, -2, 6, -2, 6, 3);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            ServerPlayer p = player(h, Rank.D);
            at(h, p, 0.5, 1, 0.5, 0F);
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);
            p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024); // a full swing every time
            double[][] bands = {{0.1, 0}, {0.28, BodyRules.momentum(0.28)}, {0.35, BodyRules.momentum(0.35)}, {0.45, BodyRules.MOMENTUM_MAX}};
            float walk = -1;
            for (double[] band : bands) {
                LivingEntity pig = target(h);
                p.setSprinting(band[0] > 0.2);
                p.setKnownMovement(new Vec3(0, 0, band[0]));
                float hp = pig.getHealth();
                p.attack(pig);
                float dealt = hp - pig.getHealth();
                if (walk < 0) walk = dealt;
                double want = walk * (1 + band[1]);
                h.assertTrue(Math.abs(dealt - want) < 0.05, "speed " + band[0] + ": " + want + ", dealt " + dealt);
                pig.discard();
            }
            h.assertTrue(Math.abs(walk - 10) < 0.01, "a walking swing is vanilla's 10, was " + walk);
            h.assertTrue(Math.abs(BodyRules.momentum(0.28) - 0.096) < 0.005 && BodyRules.momentum(0.1) == 0 && BodyRules.momentum(2) == BodyRules.MOMENTUM_MAX,
                    "bands: walk 0, sprint ~0.1, capped at 0.3");
            // sliding counts even without the sprint flag
            BodyMotion m = BodyServer.motion(p);
            m.slideUntil = BodyServer.now(p) + 10;
            p.setSprinting(false);
            p.setKnownMovement(new Vec3(0, 0, 0.45));
            LivingEntity pig = target(h);
            float hp = pig.getHealth();
            p.attack(pig);
            h.assertTrue(Math.abs(hp - pig.getHealth() - walk * (1 + BodyRules.MOMENTUM_MAX)) < 0.05, "a slide hit gains the full +30%");
            Vec3 kb = pig.getDeltaMovement();
            m.slideUntil = 0;
            pig.discard();
            // and knocks further than a walking hit
            p.setKnownMovement(new Vec3(0, 0, 0.1));
            LivingEntity pig2 = target(h);
            p.attack(pig2);
            Vec3 kb2 = pig2.getDeltaMovement();
            h.assertTrue(kb.horizontalDistance() > kb2.horizontalDistance() + 0.1, "a moving hit knocks further: " + kb.horizontalDistance() + " vs " + kb2.horizontalDistance());
            pig2.discard();
            // a weak swing (vanilla's cooldown) gains nothing
            h.assertTrue(BodyServer.momentumBonus(p, 0.5F) == 0, "a half-charged swing gets no momentum");
            done(h, p);
        });
    }

    /** FoodData keeps exhaustion to itself: read it the way the save does. */
    static float exhaustion(GameTestHelper h, ServerPlayer p) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        p.getFoodData().addAdditionalSaveData(out);
        return out.buildResult().getFloatOr("foodExhaustionLevel", 0F);
    }

    static LivingEntity target(GameTestHelper h) {
        var pig = EntityTypes.PIG.create(h.getLevel(), EntitySpawnReason.COMMAND);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        pig.setHealth(200);
        Vec3 v = h.absoluteVec(new Vec3(0.5, 1, 1.6));
        pig.snapTo(v.x, v.y, v.z, 180F, 0F);
        pig.setNoAi(true);
        h.getLevel().addFreshEntity(pig);
        return pig;
    }

    /**
     * The action camera's arm (SPEC §17.13): each pose's shape in order (the ground close, the rope wider, the wings and
     * flight widest; a slide low; a wall-run over to the open side), the trail capped, and the spring reaching a pose
     * without overshooting and coming home after.
     */
    private static void actionCamera(net.minecraft.gametest.framework.GameTestHelper h) {
        double[] g = new double[ActionRig.N], rope = new double[ActionRig.N], glide = new double[ActionRig.N], fly = new double[ActionRig.N];
        double[] slide = new double[ActionRig.N], wall = new double[ActionRig.N], fast = new double[ActionRig.N];
        ActionRig.target(ActionRig.Pose.GROUND, 0.13, g);
        ActionRig.target(ActionRig.Pose.ROPE, 0.8, rope);
        ActionRig.target(ActionRig.Pose.GLIDE, 1.5, glide);
        ActionRig.target(ActionRig.Pose.FLIGHT, 1.5, fly);
        ActionRig.target(ActionRig.Pose.SLIDE, 0.4, slide);
        ActionRig.target(ActionRig.Pose.WALL_RUN, 0.35, wall);
        ActionRig.target(ActionRig.Pose.FLIGHT, 6, fast);
        h.assertTrue(g[ActionRig.BACK] == 0 && rope[ActionRig.BACK] > 2 && glide[ActionRig.BACK] > rope[ActionRig.BACK]
                        && fly[ActionRig.BACK] > glide[ActionRig.BACK] && fly[ActionRig.CENTRE] > 0.5 && slide[ActionRig.UP] < -0.4
                        && wall[ActionRig.OPEN] == 1 && fast[ActionRig.BACK] == ActionRig.MAX_BACK,
                "the poses' shapes: ground " + g[0] + ", rope " + rope[0] + ", glide " + glide[0] + ", flight " + fly[0] + ", slide up "
                        + slide[1] + ", wall open " + wall[2] + ", fastest " + fast[0]);
        // over to the open side: a wall on the right puts the camera left of you (x is left), on the left, right
        double[] o = new double[3];
        ActionRig.shape(-0.75, 0.2, 4, 0, 0, 1, 0, true, o);
        double leftOf = o[0];
        ActionRig.shape(-0.75, 0.2, 4, 0, 0, 1, 0, false, o);
        h.assertTrue(leftOf > 0.75 && o[0] < -0.75, "the open side: " + leftOf + " / " + o[0]);
        ActionRig.Arm arm = new ActionRig.Arm();
        double peak = 0;
        int reach = -1;
        for (int t = 0; t < 60; t++) {
            arm.step(glide);
            peak = Math.max(peak, arm.at[ActionRig.BACK]);
            if (reach < 0 && Math.abs(arm.at[ActionRig.BACK] - glide[ActionRig.BACK]) < 0.05) reach = t;
        }
        h.assertTrue(reach > 5 && reach < 40 && peak <= glide[ActionRig.BACK] + 1e-6, "it trails out to the glide's arm in " + reach
                + " ticks, never past it (" + peak + ")");
        int home = -1;
        for (int t = 0; t < 60 && home < 0; t++) {
            arm.step(g);
            if (Math.abs(arm.at[ActionRig.BACK]) < 0.05 && arm.at[ActionRig.BACK] >= -1e-6) home = t;
        }
        h.assertTrue(home > 0 && home < 30, "and comes home in " + home + " ticks");
        h.succeed();
    }
    /**
     * The one-button free-run (SPEC §17.14): the ground ahead read right (a block to vault, a wall to jump at, a fence
     * not jumped at (it's crossed: VaultRules, SPEC §17.27), an edge to leap from, a one-block step down not), and jump
     * magnetism's bend capped and only toward what's nearly ahead.
     */
    private static void freeRun(GameTestHelper h) {
        // five lanes on a floor raised off the ground (an edge needs two blocks of air under it), all run toward +z
        for (int x = 0; x <= 8; x += 2) {
            for (int y = 0; y <= 7; y++) for (int z = 0; z <= 2; z++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            h.setBlock(new BlockPos(x, 3, 0), Blocks.STONE);
        }
        h.setBlock(new BlockPos(0, 3, 1), Blocks.STONE);
        h.setBlock(new BlockPos(0, 4, 1), Blocks.STONE);                         // 1.0: vault
        for (int y = 3; y <= 6; y++) h.setBlock(new BlockPos(2, y, 1), Blocks.STONE); // 3.0: a wall
        h.setBlock(new BlockPos(4, 3, 1), Blocks.STONE);
        h.setBlock(new BlockPos(4, 4, 1), Blocks.OAK_FENCE);                     // 1.5, a fence: not jumped at (crossed)
        h.setBlock(new BlockPos(8, 2, 1), Blocks.STONE);                         // a step down of one: no leap
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double[] out = new double[1];
        Vec3 a = h.absoluteVec(new Vec3(0.5, 4, 0.5)), b = h.absoluteVec(new Vec3(0.5, 4, 1.5));
        double fx = b.x - a.x, fz = b.z - a.z;
        int[] got = new int[5];
        for (int i = 0; i < 5; i++) {
            Vec3 f = h.absoluteVec(new Vec3(i * 2 + 0.5, 4, 0.5));
            got[i] = RunnerRules.ahead(h.getLevel(), CollisionContext.empty(), m, f.x, f.y, f.z, fx, fz, 0.28, out);
        }
        h.assertTrue(got[0] == RunnerRules.AHEAD_VAULT && got[1] == RunnerRules.AHEAD_WALL && got[2] == RunnerRules.AHEAD_NONE
                        && got[3] == RunnerRules.AHEAD_LEAP && got[4] == RunnerRules.AHEAD_NONE,
                "vault, wall, fence, edge, step down read as " + java.util.Arrays.toString(got));
        double[] v = new double[2];
        double turned = RunnerRules.bend(0, 0, 0, 0.3, 1, 4, RunnerRules.PULL_STEP, v);
        h.assertTrue(Math.abs(turned - RunnerRules.PULL_STEP) < 1e-9 && Math.abs(Math.hypot(v[0], v[1]) - 0.3) < 1e-9 && v[0] > 0,
                "a ledge 14° off bends the jump " + turned + "° its way, speed kept");
        h.assertTrue(RunnerRules.bend(0, 0, 0, 0.3, 4, 4, RunnerRules.PULL_STEP, v) == 0, "one 45° off doesn't pull");
        h.succeed();
    }
}
