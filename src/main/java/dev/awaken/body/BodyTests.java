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
        AwakenTests.registerIsolated("body_mantle_ledges", 100, BodyTests::mantleLedges);
        AwakenTests.register("body_mantle_gate_walls", 400, BodyTests::mantleGateWalls);
        AwakenTests.registerIsolated("body_slide_gap_and_wall", 100, BodyTests::slideGapAndWall);
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

    /** A block and a slab (1.5) is a ledge; two blocks, a low ceiling, a fence, a wall, a plain step or a far one are not. */
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
            h.assertTrue(ledge(h, 6.5, 0.5, BodyRules.MANTLE_REACH) == null, "never onto a fence");
            h.assertTrue(ledge(h, 8.5, 0.5, BodyRules.MANTLE_REACH) == null, "never onto a wall");
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
     * Real gate floors (the generator's own blocks, several themes and layouts): from every spot a player can stand in
     * a room, in eight directions, every ledge the mantle accepts is inside a room (or a doorway's tunnel) and under
     * its ceiling. Players can't break gate interiors, so the mantle must never be a way out or onto a roof.
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
}
