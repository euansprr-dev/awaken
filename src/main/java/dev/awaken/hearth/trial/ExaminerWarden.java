package dev.awaken.hearth.trial;

import dev.awaken.core.api.Notices;
import dev.awaken.core.api.Strikes;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The second Proving's Warden (SPEC §47.6): the Examiner, for C. It doesn't take you anywhere: the bell stops and you're
 * standing where you were, until the room turns to glass round you and breaks, and you're in the Hall of Reflections. It
 * reads how you fight through its first phase and answers it from its second (the mirrors wake, and the Signal says what
 * it read); at a third of its health a ring of mirrors rises and the hall closes in. It leaves you your Path.
 */
final class ExaminerWarden extends Warden {
    static final ExaminerWarden WARDEN = new ExaminerWarden();
    static final String MOB = "awaken:proving_examiner";
    /** The room read round whoever touched the bell: this far each side, this far down and up from their feet. */
    static final int ROOM_R = 10, ROOM_DOWN = -2, ROOM_UP = 12;
    /** After you wake it's your room for this long; then it turns to glass a ring every {@link #GLASS_RING} ticks from you. */
    static final int GLASS_AFTER = 50, GLASS_RING = 2;
    /** The glass rings out to this far (the room's corners). */
    static final int GLASS_RINGS = (int) Math.ceil(ROOM_R * Math.sqrt(2));
    /** It steps out of the glass this long after the last of the room turned. */
    static final int COMES_AFTER_GLASS = 30;
    /** After everyone fell it steps out of the north mirror this long after you wake. */
    static final int RETURN_AFTER = 60;
    /** How far in front of whoever touched the bell it steps out. */
    static final int ARRIVE_AHEAD = 9;
    /** Its phases: the mirrors wake at two thirds of its health, the hall closes in at a third (its own phase lines). */
    static final float WAKE_AT = 0.66F, CLOSE_AT = 0.33F;
    /** What it read of you is said this long after the mirrors wake. */
    static final int READ_AFTER = 60;
    /** The ring rises a row every few ticks. */
    static final int RING_ROW_TICKS = 3;

    private ExaminerWarden() {
        super(MOB, Rank.C, Reflections.HALL);
    }

    @Override
    String asked() {
        return "Last time the bell didn't stop. Go when you're ready.";
    }

    /** The bell rings, the System flickers, and the bell stops: nothing seems to have happened. */
    @Override
    int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.6F);
        if (practice) return 40;
        dev.awaken.fault.Fault f = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.FAULT, "",
                List.of("READING · READING · READING", "[ERROR] THE READING IS LOOKING BACK"), "",
                "The bell stopped.", null, null, 0, true);
        dev.awaken.fault.Faults.play(m, f);
        return f.length();
    }

    // ---- the room -----------------------------------------------------------------------------------------------------

    /** The room as it stood when the bell was touched, and how far the run has got with it. */
    static final class Room {
        static final int S = 2 * ROOM_R + 1;
        final ResourceKey<Level> from;
        final BlockPos feet;
        /** Whoever touched the bell: where they stood in their feet's block, and which way they looked. */
        final Vec3 off;
        final float yaw, pitch;
        final BlockState[] cells = new BlockState[S * S * (ROOM_UP - ROOM_DOWN + 1)];
        boolean laid, shattered, told;
        long glassAt = -1, glassDone = -1, readAt = -1;
        int glassRing;

        Room(ServerLevel level, ServerPlayer leader) {
            from = level.dimension();
            feet = leader.blockPosition();
            off = leader.position().subtract(Vec3.atLowerCornerOf(feet));
            yaw = leader.getYRot();
            pitch = leader.getXRot();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int dy = ROOM_DOWN; dy <= ROOM_UP; dy++) {
                for (int dz = -ROOM_R; dz <= ROOM_R; dz++) {
                    for (int dx = -ROOM_R; dx <= ROOM_R; dx++) {
                        at.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                        cells[index(dx, dy, dz)] = copied(level.getBlockState(at), level, at);
                    }
                }
            }
        }

        static int index(int dx, int dy, int dz) {
            return ((dy - ROOM_DOWN) * S + (dz + ROOM_R)) * S + (dx + ROOM_R);
        }

        BlockState at(int dx, int dy, int dz) {
            return cells[index(dx, dy, dz)];
        }
    }

    /**
     * What of a block comes into the copy: the look of a room, nothing that acts. No water, lava or fire (what stood in
     * water comes dry), no portal; a vanilla block that keeps things comes empty, a game master's block, spawner, vault,
     * TNT, bed or anchor doesn't come; another mod's block comes only if it has nothing kept and you can't walk through it.
     */
    static BlockState copied(BlockState s, ServerLevel level, BlockPos p) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (s.isAir()) return air;
        Block b = s.getBlock();
        if (b instanceof LiquidBlock || b instanceof BaseFireBlock || b instanceof Portal) return air;
        if (b instanceof TntBlock || b instanceof BedBlock || b instanceof RespawnAnchorBlock) return air;
        if (!s.getFluidState().isEmpty()) {
            if (!s.hasProperty(BlockStateProperties.WATERLOGGED)) return air; // kelp, seagrass: water plants without their water
            s = s.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        var key = BuiltInRegistries.BLOCK.getKey(b);
        boolean vanilla = key != null && key.getNamespace().equals("minecraft");
        if (s.hasBlockEntity() && (!vanilla || b instanceof GameMasterBlock || b instanceof SpawnerBlock || b instanceof TrialSpawnerBlock
                || b instanceof VaultBlock)) return air;
        if (!vanilla && s.getCollisionShape(level, p).isEmpty()) return air; // a mod's gates, rings and portals stay behind
        return s;
    }

    @Override
    void before(Provings.Run run, ServerPlayer leader) {
        run.room = new Room((ServerLevel) leader.level(), leader);
    }

    private static @Nullable Room room(Provings.Run run) {
        return run.room instanceof Room r ? r : null;
    }

    /** Where whoever touched the bell stood, in the hall. */
    @Override
    BlockPos wakeAt(BlockPos centre) {
        return centre.above();
    }

    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    /** The room laid over the hall's middle, whoever touched the bell's feet where the hall's floor is stood on. */
    private static void lay(Provings.Run run, Room room) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = ROOM_DOWN; dy <= ROOM_UP; dy++) {
            for (int dz = -ROOM_R; dz <= ROOM_R; dz++) {
                for (int dx = -ROOM_R; dx <= ROOM_R; dx++) {
                    at.set(run.centre.getX() + dx, run.centre.getY() + 1 + dy, run.centre.getZ() + dz);
                    BlockState s = room.at(dx, dy, dz);
                    // the room's air keeps the hall's unseen light, so it stays lit when its lamps turn to glass
                    if (s.isAir() && Reflections.block(dx, 1 + dy, dz) instanceof BlockState h && h.is(Blocks.LIGHT)) s = h;
                    run.level.setBlock(at, s, QUIET);
                }
            }
        }
        room.laid = true;
        room.glassAt = run.level.getGameTime() + GLASS_AFTER;
    }

    /**
     * The first time, everyone stands where they stood in the room (whoever was outside it, where whoever touched the bell
     * did), looking the same way: nothing has happened. After a fall, inside the door, facing the middle.
     */
    @Override
    void wake(ServerPlayer m, Provings.Run run, boolean first) {
        Room room = room(run);
        if (first && room != null) {
            if (!room.laid) lay(run, room);
            Vec3 off = room.off;
            float yaw = room.yaw, pitch = room.pitch;
            if (m.level().dimension() == room.from) {
                Vec3 rel = m.position().subtract(Vec3.atLowerCornerOf(room.feet));
                if (Math.abs(rel.x) <= ROOM_R - 1 && Math.abs(rel.z) <= ROOM_R - 1 && rel.y >= ROOM_DOWN + 1 && rel.y <= ROOM_UP - 2) {
                    off = rel;
                    yaw = m.getYRot();
                    pitch = m.getXRot();
                }
            }
            Vec3 at = Vec3.atLowerCornerOf(run.centre.above()).add(off);
            m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
            m.resetFallDistance();
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(run.centre.offset(0, 1, Reflections.WAKE_Z));
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, 0F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    /**
     * The room turning to glass, a ring at a time out from whoever touched the bell: every block the glass of its colour;
     * a lamp leaves its light behind (an unseen light block), so the glass room doesn't go dark.
     */
    private static void glass(Provings.Run run, Room room, long now) {
        if (room.glassAt < 0 || now < room.glassAt || room.glassDone >= 0 || (now - room.glassAt) % GLASS_RING != 0) return;
        int ring = room.glassRing++;
        BlockPos at0 = run.centre.above();
        if (ring == 0) run.level.playSound(null, at0, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.5F, 0.5F);
        run.level.playSound(null, at0, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2F, 0.6F + ring * 0.08F);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dz = -ROOM_R; dz <= ROOM_R; dz++) {
            for (int dx = -ROOM_R; dx <= ROOM_R; dx++) {
                if ((int) Math.floor(Math.sqrt(dx * dx + dz * dz)) != ring) continue;
                for (int dy = ROOM_DOWN; dy <= ROOM_UP; dy++) {
                    BlockState was = room.at(dx, dy, dz);
                    if (was.isAir()) continue;
                    at.set(run.centre.getX() + dx, run.centre.getY() + 1 + dy, run.centre.getZ() + dz);
                    BlockState now2 = was.isCollisionShapeFullBlock(run.level, at) ? glassOf(was.getMapColor(run.level, at))
                            : was.getLightEmission() > 0 ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, was.getLightEmission())
                            : Blocks.AIR.defaultBlockState();
                    run.level.setBlock(at, now2, QUIET);
                }
            }
        }
        if (ring >= GLASS_RINGS) room.glassDone = now;
    }

    /** The stained glass nearest a block's colour (clear glass for what has none). */
    static BlockState glassOf(MapColor c) {
        if (c == MapColor.NONE) return Blocks.GLASS.defaultBlockState();
        int r = c.col >> 16 & 255, g = c.col >> 8 & 255, b = c.col & 255;
        DyeColor best = DyeColor.WHITE;
        int bestD = Integer.MAX_VALUE;
        for (DyeColor d : DyeColor.values()) {
            int col = d.getMapColor().col;
            int dr = (col >> 16 & 255) - r, dg = (col >> 8 & 255) - g, db = (col & 255) - b;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return Blocks.STAINED_GLASS.pick(best).defaultBlockState();
    }

    /** It steps out of the glass: the room breaks, and what was always round it is the hall. */
    private static void shatter(Provings.Run run, Room room, List<ServerPlayer> here) {
        room.shattered = true;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int bursts = 0;
        for (int dy = ROOM_UP; dy >= ROOM_DOWN; dy--) {
            for (int dz = -ROOM_R; dz <= ROOM_R; dz++) {
                for (int dx = -ROOM_R; dx <= ROOM_R; dx++) {
                    at.set(run.centre.getX() + dx, run.centre.getY() + 1 + dy, run.centre.getZ() + dz);
                    BlockState was = run.level.getBlockState(at);
                    BlockState want = Reflections.block(dx, 1 + dy, dz);
                    if (want == null) want = Blocks.AIR.defaultBlockState();
                    if (was == want) continue;
                    if (!was.isAir() && bursts < 48 && Math.floorMod(dx * 3 + dy * 7 + dz * 5, 11) == 0) {
                        bursts++;
                        run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                                8, 0.4, 0.4, 0.4, 0.08);
                    }
                    run.level.setBlock(at, want, Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos c = run.centre.above();
        for (int k = 0; k < 5; k++) {
            BlockPos p = k == 0 ? c : c.offset(k % 2 == 0 ? 7 : -7, 3, k < 3 ? 7 : -7);
            run.level.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 3F, 0.5F + 0.1F * k);
        }
        // whoever the hall's blocks came back round goes up out of them; whoever stood high in the room floats down
        for (ServerPlayer m : here) {
            if (m.level() != run.level) continue;
            if (!run.level.noCollision(m)) {
                boolean out = false;
                for (int up = 1; up <= 4 && !out; up++) {
                    if (run.level.noCollision(m, m.getBoundingBox().move(0, up, 0))) {
                        m.teleportTo(m.getX(), m.getY() + up, m.getZ());
                        out = true;
                    }
                }
                if (!out) WARDEN.wake(m, run, false);
            }
            if (m.getY() > run.centre.getY() + 2.5) m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, false, false));
            m.resetFallDistance();
        }
    }

    @Override
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {
        Room room = room(run);
        if (room != null && !room.shattered) return; // it's your room: nothing sounds wrong yet
        if (now < run.nextAmbient) return;
        // after everyone fell: a chime from one mirror and then another round the hall
        double a = run.level.getRandom().nextDouble() * Math.PI * 2;
        BlockPos at = run.centre.offset((int) (Reflections.WALL_R * Math.cos(a)), 8, (int) (Reflections.WALL_R * Math.sin(a)));
        run.level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3F, 0.5F + run.level.getRandom().nextFloat() * 0.3F);
        run.nextAmbient = now + 40 + run.level.getRandom().nextInt(40);
    }

    /** Out of the glass once the room has turned; after everyone fell, out of the north mirror a few seconds after you wake. */
    @Override
    boolean comes(Provings.Run run, List<ServerPlayer> here, long now) {
        Room room = room(run);
        if (room == null || room.shattered) return now - run.waitingSince >= RETURN_AFTER;
        return room.glassDone >= 0 && now >= room.glassDone + COMES_AFTER_GLASS || now - run.waitingSince >= Provings.COME_AFTER;
    }

    /** In front of whoever touched the bell, facing them; after everyone fell, before the north mirror, facing the door. */
    @Override
    Vec3 arrive(Provings.Run run) {
        Room room = room(run);
        if (room == null || room.shattered) return new Vec3(run.centre.getX() + 0.5, run.centre.getY() + 1, run.centre.getZ() + Reflections.RETURN_Z + 0.5);
        double rad = Math.toRadians(room.yaw);
        Vec3 from = Vec3.atLowerCornerOf(run.centre.above()).add(room.off);
        return new Vec3(from.x - Math.sin(rad) * ARRIVE_AHEAD, run.centre.getY() + 1, from.z + Math.cos(rad) * ARRIVE_AHEAD);
    }

    @Override
    float arriveYaw(Provings.Run run) {
        Room room = room(run);
        return room == null || room.shattered ? 0F : room.yaw + 180F;
    }

    @Override
    void came(Provings.Run run, LivingEntity warden, List<ServerPlayer> here) {
        Room room = room(run);
        if (room != null && !room.shattered) {
            shatter(run, room, here);
            return;
        }
        run.level.playSound(null, warden.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3F, 0.6F);
        run.level.playSound(null, warden.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3F, 0.6F);
    }

    // ---- the hall turning ---------------------------------------------------------------------------------------------

    /** Its second phase (at two thirds): the mirrors wake; a few seconds on the Signal says what it read. Its third (a third): the ring. */
    @Override
    void fight(Provings.Run run, LivingEntity warden, long now) {
        float f = warden.getHealth() / warden.getMaxHealth();
        Room room = room(run);
        if (run.turns == 0 && f <= WAKE_AT) {
            run.turns = 1;
            run.turnAt = now;
            run.turnRow = 0;
            if (room != null) room.readAt = now + READ_AFTER;
            run.level.playSound(null, run.centre.above(8), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4F, 0.6F);
        }
        if (run.turns == 1 && f <= CLOSE_AT) closeIn(run, warden, now);
        if (room != null && !room.told && room.readAt >= 0 && now >= room.readAt) {
            room.told = true;
            Component said = Component.literal(read(Strikes.habit(warden)));
            for (UUID u : run.members) {
                ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
                // CRITICAL: the director holds a paced line through a fight, and this one is for the fight
                if (m != null && m.level() == run.level) Signal.toast(m, said, Notices.Tier.CRITICAL, "proving_read");
            }
        }
    }

    /** What it read of you, said plainly (SPEC §47.6). */
    static String read(Strikes.Habit h) {
        return switch (h) {
            case GUARD -> "It's noticed you block. Now it swings the blows you can't turn aside.";
            case SLIP -> "It's noticed you dodge. Now it holds its swings and covers the ground you dodge to.";
            case DISTANCE -> "It's noticed you keep your distance. Now it closes in fast.";
            case POWERS -> "It's noticed you lean on your powers. Now it throws them back at you.";
            case NONE -> "It watched you fight and found nothing you lean on. It's still guessing.";
        };
    }

    /** Its third phase: a ring of mirrors rises round the middle. Whoever's outside it is brought in, and it stays inside. */
    private static void closeIn(Provings.Run run, LivingEntity warden, long now) {
        if (run.turnAt >= 0) wakeAll(run); // a blow big enough to skip the waking finishes it first
        run.turns = 2;
        run.turnAt = now;
        run.turnRow = 1;
        double in = Reflections.RING_R - 3;
        for (LivingEntity e : run.level.getEntitiesOfClass(LivingEntity.class, Reflections.HALL.box(run.centre))) {
            double lx = e.getX() - run.centre.getX() - 0.5, lz = e.getZ() - run.centre.getZ() - 0.5;
            double r = Math.sqrt(lx * lx + lz * lz);
            if (r < Reflections.RING_R - 1 || r > Reflections.WALL_R) continue;
            if (e instanceof Player p && (p.isSpectator() || !run.members.contains(p.getUUID()))) continue;
            e.teleportTo(run.centre.getX() + 0.5 + lx / r * in, run.centre.getY() + 1, run.centre.getZ() + 0.5 + lz / r * in);
        }
        if (warden instanceof Mob mob) mob.setHomeTo(run.centre.above(), Reflections.RING_R - 2);
        run.level.playSound(null, run.centre.above(4), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 4F, 0.4F);
    }

    @Override
    void step(Provings.Run run, long now) {
        Room room = room(run);
        if (room != null && !room.shattered) glass(run, room, now);
        if (run.turnAt < 0) return;
        if (run.turns == 1) {
            // the mirrors wake a bay a tick, round from the north
            int k = (int) (now - run.turnAt);
            if (k >= Reflections.BAYS) {
                run.turnAt = -1;
                return;
            }
            wakeBay(run, Math.floorMod(Reflections.BAYS / 2 + (k % 2 == 0 ? k / 2 : -(k + 1) / 2), Reflections.BAYS));
        } else if (run.turns == 2) {
            if ((now - run.turnAt) % RING_ROW_TICKS != 0) return;
            int dy = run.turnRow++;
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int[] c : ringCols()) {
                BlockState s = Reflections.ring(c[0], dy, c[1]);
                if (s == null) continue;
                at.set(run.centre.getX() + c[0], run.centre.getY() + dy, run.centre.getZ() + c[1]);
                run.level.setBlock(at, s, Block.UPDATE_CLIENTS);
            }
            if ((dy & 1) == 1) run.level.playSound(null, run.centre.above(dy), SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 4F, 0.5F);
            if (dy >= Reflections.RING_HI) run.turnAt = -1;
        }
    }

    private static void wakeAll(Provings.Run run) {
        for (int b = 0; b < Reflections.BAYS; b++) wakeBay(run, b);
        run.turnAt = -1;
    }

    /** One bay's mirror wakes: the glass goes white and the black behind it lights. */
    private static void wakeBay(Provings.Run run, int bay) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int[][] cols = bayCols()[bay];
        for (int[] c : cols) {
            for (int dy = Reflections.MIRROR_LO; dy <= Reflections.MIRROR_HI; dy++) {
                boolean face = Reflections.mirror(c[0], dy, c[1]);
                if (!face && !Reflections.behind(c[0], dy, c[1])) continue;
                at.set(run.centre.getX() + c[0], run.centre.getY() + dy, run.centre.getZ() + c[1]);
                run.level.setBlock(at, Reflections.woken(face), Block.UPDATE_CLIENTS);
            }
        }
        if (cols.length > 0) {
            int[] mid = cols[cols.length / 2];
            run.level.playSound(null, run.centre.offset(mid[0], 9, mid[1]), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3F, 0.7F + bay * 0.02F);
        }
    }

    private static int[][][] BAY_COLS;
    private static int[][] RING_COLS;

    /** The wall's columns by bay (built once). */
    private static int[][][] bayCols() {
        if (BAY_COLS == null) {
            List<List<int[]>> by = new ArrayList<>();
            for (int b = 0; b < Reflections.BAYS; b++) by.add(new ArrayList<>());
            for (int x = -Reflections.OUTER; x <= Reflections.OUTER; x++) {
                for (int z = -Reflections.OUTER; z <= Reflections.OUTER; z++) {
                    double r = Reflections.r(x, z);
                    if (r >= Reflections.WALL_R && r < Reflections.OUTER - 0.5) by.get(Reflections.bay(x, z)).add(new int[]{x, z});
                }
            }
            int[][][] out = new int[Reflections.BAYS][][];
            for (int b = 0; b < Reflections.BAYS; b++) out[b] = by.get(b).toArray(new int[0][]);
            BAY_COLS = out;
        }
        return BAY_COLS;
    }

    /** The rising ring's columns (built once). */
    private static int[][] ringCols() {
        if (RING_COLS == null) {
            List<int[]> cols = new ArrayList<>();
            for (int x = -Reflections.RING_R - 2; x <= Reflections.RING_R + 2; x++) {
                for (int z = -Reflections.RING_R - 2; z <= Reflections.RING_R + 2; z++) if (Reflections.ringAt(x, z)) cols.add(new int[]{x, z});
            }
            RING_COLS = cols.toArray(new int[0][]);
        }
        return RING_COLS;
    }

    /** Everyone fell: the mirrors sleep again, the ring goes back into the floor. */
    @Override
    void reset(Provings.Run run) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        if (run.turns >= 1) {
            for (int[][] cols : bayCols()) {
                for (int[] c : cols) {
                    for (int dy = Reflections.MIRROR_LO; dy <= Reflections.MIRROR_HI; dy++) put(run, at, c[0], dy, c[1]);
                }
            }
        }
        if (run.turns >= 2) {
            for (int[] c : ringCols()) for (int dy = 1; dy <= Reflections.RING_HI; dy++) put(run, at, c[0], dy, c[1]);
        }
        run.turns = 0;
        run.turnAt = -1;
        Room room = room(run);
        if (room != null) {
            room.readAt = -1;
            room.told = false;
        }
    }

    private static void put(Provings.Run run, BlockPos.MutableBlockPos at, int x, int dy, int z) {
        BlockState want = Reflections.block(x, dy, z);
        if (want == null) want = Blocks.AIR.defaultBlockState();
        at.set(run.centre.getX() + x, run.centre.getY() + dy, run.centre.getZ() + z);
        if (run.level.getBlockState(at) != want) run.level.setBlock(at, want, Block.UPDATE_CLIENTS);
    }

    @Override
    void died(Provings.Run run, long now) {
        if (run.turns == 0 || run.turnAt >= 0 && run.turns == 1) wakeAll(run); // the hall lights up all at once
        run.level.playSound(null, run.centre.above(6), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4F, 0.4F);
    }

    /** After its death: your Path, read from how you've lived (the Second Awakening); the hall rings. */
    @Override
    void outro(Provings.Run run, List<ServerPlayer> here, long t) {
        if (t == 50) {
            for (ServerPlayer m : here) {
                if (m.level() != run.level) continue;
                run.level.playSound(null, m.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.8F);
                if (!run.practice) Trials.secondAwakening(m);
            }
        }
        if (t == 90) run.level.playSound(null, run.centre.above(10), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 4F, 0.5F);
    }

    /** The stair's top: climbed out. */
    @Override
    boolean out(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
        return lz >= Reflections.LANDING_Z && Math.abs(lx) <= 2 && m.getY() >= run.centre.getY() + Reflections.LANDING_DY - 0.1;
    }

    @Override
    String firstFall() {
        return "You wake inside the door. The stair behind you climbs out.";
    }

    @Override
    String name() {
        return "The Examiner";
    }
}
