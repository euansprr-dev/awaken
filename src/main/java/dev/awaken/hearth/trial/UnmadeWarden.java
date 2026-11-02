package dev.awaken.hearth.trial;

import com.mojang.math.Transformation;
import dev.awaken.powers.FlyingBlocks;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The third Proving's Warden (SPEC §46.7): the Unmade, for B. The bell won't stop, and you're falling out of a night sky
 * onto the Broken Span, an old bridge over nothing. Once you're down its north arm comes apart: the stones lift, come
 * together over the bastion, and the Unmade drops out of them. At two thirds of its health the bastion's outer ring
 * cracks and gives way (inside the tuff ring stays); at a third the Span's lamps go out from the gatehouse in. Go over
 * the edge and you wake at the bridge's south end, the way out through the arch behind you. When it dies the Span mends.
 */
final class UnmadeWarden extends Warden {
    static final UnmadeWarden WARDEN = new UnmadeWarden();
    static final String MOB = "awaken:proving_unmade";
    /** The first time you wake this high over the bastion, a little south of its middle, falling slowly. */
    static final int WAKE_HIGH = 16, WAKE_SOUTH = 6, SLOW_FALL = 20 * 6;
    /** It starts to gather this long after someone stands on the bastion (or once you've waited {@link Provings#COME_AFTER}). */
    static final int GATHER_AFTER = 30;
    /** The north arm lifts this many rows a tick from its broken end in, these courses of it (the piers' feet stay). */
    static final int LIFT_ROWS = 2, LIFT_LO = -12, LIFT_HI = 4;
    /** A lifted stone rises, flies to the gathering and turns in it; the whole of it falls in on itself onto the bastion's
     * rim this long after the last row went, and it's there. */
    static final int RISE = 8, FLY = 22, GATHERED = 36, COLLAPSE = 8;
    /** Where the stones gather, from the bastion's middle at the floor. */
    static final double GATHER_Y = Span.GATHER_DY, GATHER_Z = -20;
    /** Its phases: the ring gives way at two thirds, the lamps go out at a third (its own phase lines). */
    static final float GIVE_AT = 0.66F, DARK_AT = 0.33F;
    /** The ring cracks for this long, then falls a sector every few ticks: north first, round both ways to the south. */
    static final int CRACK = 40, SECTOR_TICKS = 4, SECTORS = 8;
    static final int[] SECTOR_ORDER = {0, 1, 7, 2, 6, 3, 5, 4};
    /** The lamps go out a post every few ticks, from the gatehouse in; the bastion's last. */
    static final int LAMP_TICKS = 4;
    /** This far under the deck you've gone over the edge (nowhere you can stand is lower: the piers' stumps under the
     * lifted arm and the ledge left round the bastion's pier are deeper, and you'd be stuck on them). */
    static final int VOID = -5;
    /** How far from the bastion's middle it goes after you: the bastion and the near half of the south arm. */
    static final int HOME = 26;

    private UnmadeWarden() {
        super(MOB, Rank.B, Span.HALL);
    }

    @Override
    String asked() {
        return "The bell has taken you twice now. Go when you're ready.";
    }

    @Override
    int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.5F);
        if (practice) return 40;
        dev.awaken.fault.Fault f = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.FAULT, "",
                List.of("MEASURING · MEASURING · MEASURING", "[ERROR] NOTHING UNDER YOU"), "",
                "The bell stopped.", null, null, 0, true);
        dev.awaken.fault.Faults.play(m, f);
        return f.length();
    }

    // ---- the gathering ------------------------------------------------------------------------------------------------

    /** What a run keeps of the Span: the gathering and its stones, and the mending after its death. */
    static final class Gather {
        long landedAt = -1, startAt = -1, liftedAt = -1, collapseAt = -1;
        /** The next row of the north arm to lift. */
        int row = Span.NORTH_END;
        final List<Stone> stones = new ArrayList<>();
        Hall.@Nullable Repair mend;
    }

    /** A stone of the Span in the air: a block display, where it lifted from, and its place in the gathering. */
    static final class Stone {
        final Display.BlockDisplay d;
        final Vec3 from;
        final long born;
        final double angle, radius, height, spin;
        final boolean falling;
        Vec3 at, vel;

        Stone(Display.BlockDisplay d, Vec3 from, long born, RandomSource rnd, boolean falling, Vec3 vel) {
            this.d = d;
            this.from = from;
            this.at = from;
            this.born = born;
            this.falling = falling;
            this.vel = vel;
            angle = rnd.nextDouble() * Math.PI * 2;
            radius = 1.2 + rnd.nextDouble() * 3.4;
            height = (rnd.nextDouble() - 0.5) * 6;
            spin = (0.06 + rnd.nextDouble() * 0.08) * (rnd.nextBoolean() ? 1 : -1);
        }
    }

    @Override
    void before(Provings.Run run, ServerPlayer leader) {
        run.room = new Gather();
    }

    private static Gather gather(Provings.Run run) {
        if (run.room instanceof Gather g) return g;
        Gather g = new Gather();
        run.room = g;
        return g;
    }

    /** On the south arm, facing the bastion (where you wake after a fall). */
    @Override
    BlockPos wakeAt(BlockPos centre) {
        return centre.offset(0, 1, Span.WAKE_Z);
    }

    /**
     * The first time, high over the bastion and falling slowly, looking north up the bridge (a party spread a little).
     * After a fall, at the south end of the bridge, facing the bastion.
     */
    @Override
    void wake(ServerPlayer m, Provings.Run run, boolean first) {
        if (first) {
            int i = Math.max(0, List.copyOf(run.members).indexOf(m.getUUID()));
            Vec3 at = new Vec3(run.centre.getX() + 0.5 + (i % 3 - 1) * 3, run.centre.getY() + WAKE_HIGH, run.centre.getZ() + WAKE_SOUTH + 0.5 + i / 3 * 3);
            m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, 30F, TeleportTransition.DO_NOTHING));
            m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL, 0, false, false));
            m.resetFallDistance();
            run.level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.2F, 0.5F);
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(wakeAt(run.centre));
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, 0F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    /** Whether a member stands on the bastion. */
    private static boolean onBastion(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ() - 0.5;
        double y = m.getY() - run.centre.getY();
        return y > 0.5 && y < 3 && lx * lx + lz * lz < (Span.BASTION_R - 1) * (Span.BASTION_R - 1);
    }

    /**
     * Before it comes: wind, and the north arm creaking. Someone stands on the bastion (or you've waited long enough) and
     * the arm comes apart, its stones lifting row by row from the broken end in and flying to the gathering.
     */
    @Override
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {
        Gather g = gather(run);
        if (g.startAt < 0) {
            if (g.landedAt < 0) {
                for (ServerPlayer m : here) if (m.level() == run.level && onBastion(run, m)) g.landedAt = now;
            }
            if (g.landedAt >= 0 && now >= g.landedAt + GATHER_AFTER || now - run.waitingSince >= Provings.COME_AFTER) {
                g.startAt = now;
                BlockPos at = run.centre.offset(0, 0, Span.NORTH_END + 6);
                run.level.playSound(null, at, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 4F, 0.4F);
                run.level.playSound(null, run.centre.offset(0, 2, Span.ARM_N - 4), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 4F, 0.5F);
                return;
            }
            if (now >= run.nextAmbient) {
                // the arm creaks, somewhere along it, and dust falls from under it
                RandomSource rnd = run.level.getRandom();
                int z = Span.NORTH_END + 6 + rnd.nextInt(Span.ARM_N - Span.NORTH_END - 6);
                BlockPos at = run.centre.offset(0, -1, z);
                run.level.playSound(null, at, SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS, 3F, 0.4F + rnd.nextFloat() * 0.2F);
                run.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE_BRICKS.defaultBlockState()),
                        at.getX() + 0.5, at.getY() - 0.2, at.getZ() + 0.5, 10, 3, 0.2, 1.5, 0);
                run.nextAmbient = now + 30 + rnd.nextInt(40);
            }
            return;
        }
        if (g.row <= Span.ARM_N - 1) {
            for (int n = 0; n < LIFT_ROWS && g.row <= Span.ARM_N - 1; n++) lift(run, g, g.row++, now);
            if (g.row > Span.ARM_N - 1) g.liftedAt = now;
            return;
        }
        if (g.collapseAt < 0 && now >= g.liftedAt + FLY + GATHERED) {
            g.collapseAt = now;
            run.level.playSound(null, run.centre.offset(0, (int) GATHER_Y, (int) GATHER_Z), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4F, 0.4F);
        } else if (g.collapseAt < 0 && (now - g.liftedAt) % 8 == 0) {
            // the stones knock together as they turn
            run.level.playSound(null, run.centre.offset(0, (int) GATHER_Y, (int) GATHER_Z), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 3F,
                    0.5F + run.level.getRandom().nextFloat() * 0.3F);
        }
    }

    private static final int QUIET = Block.UPDATE_CLIENTS;

    /** One row of the north arm lifts: its blocks go, a few of them fly as stones to the gathering. */
    private static void lift(Provings.Run run, Gather g, int z, long now) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        RandomSource rnd = run.level.getRandom();
        List<int[]> solid = new ArrayList<>();
        for (int x = -Span.RAIL - 1; x <= Span.RAIL + 1; x++) {
            if (Span.r(x, z) < Span.BASTION_R) continue;
            for (int dy = LIFT_LO; dy <= LIFT_HI; dy++) {
                at.set(run.centre.getX() + x, run.centre.getY() + dy, run.centre.getZ() + z);
                BlockState s = run.level.getBlockState(at);
                if (s.isAir()) continue;
                if (!s.is(Blocks.LIGHT) && s.isCollisionShapeFullBlock(run.level, at)) solid.add(new int[]{x, dy});
                run.level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
        int fly = Math.min(solid.size(), 1 + (z & 1));
        for (int k = 0; k < fly; k++) {
            int[] p = solid.get(rnd.nextInt(solid.size()));
            Vec3 from = new Vec3(run.centre.getX() + p[0] + 0.5, run.centre.getY() + p[1] + 0.5, run.centre.getZ() + z + 0.5);
            BlockState s = rnd.nextInt(4) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            Display.BlockDisplay d = stone(run.level, s, from, 1.0F + rnd.nextFloat() * 0.6F, rnd.nextFloat() * 1.2F);
            if (d != null) g.stones.add(new Stone(d, from, now, rnd, false, Vec3.ZERO));
        }
        double cx = run.centre.getX() + 0.5, cz = run.centre.getZ() + z + 0.5;
        run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), cx, run.centre.getY() - 1, cz,
                14, 3.5, 2, 0.4, 0.05);
        if ((z & 1) == 0) run.level.playSound(null, BlockPos.containing(cx, run.centre.getY(), cz), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 3F,
                0.45F + rnd.nextFloat() * 0.2F);
        if (Math.floorMod(z, 6) == 0) run.level.playSound(null, BlockPos.containing(cx, run.centre.getY(), cz), SoundEvents.DEEPSLATE_BRICKS_BREAK,
                SoundSource.BLOCKS, 3.5F, 0.5F);
    }

    /** A stone in the air: a block display centred on {@code at}, tilted so the stones don't look grid-locked. */
    private static Display.@Nullable BlockDisplay stone(ServerLevel level, BlockState state, Vec3 at, float size, float tilt) {
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
        Quaternionf rot = new Quaternionf().rotateXYZ(tilt, tilt * 1.7F, tilt * 0.6F);
        Vector3f half = new Vector3f(-size / 2F, -size / 2F, -size / 2F);
        rot.transform(half);
        Transformation t = new Transformation(half, rot, new Vector3f(size, size, size), new Quaternionf());
        tag.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE, t).getOrThrow());
        tag.putInt("teleport_duration", 3);
        tag.putFloat("view_range", 3.0F);
        Entity e = EntityType.loadEntityRecursive(EntityTypes.BLOCK_DISPLAY, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
            ent.snapTo(at.x, at.y, at.z, 0F, 0F);
            return ent;
        });
        if (!(e instanceof Display.BlockDisplay d)) return null;
        d.addTag(FlyingBlocks.TEMP_TAG); // never saved: gone if its chunk is ever loaded again
        level.addFreshEntity(d);
        return d;
    }

    /** Where a stone turns in the gathering. */
    private static Vec3 slot(Stone s, Vec3 gather, long now) {
        double a = s.angle + s.spin * now;
        return gather.add(Math.cos(a) * s.radius, s.height + Math.sin(now * 0.07 + s.angle) * 0.4, Math.sin(a) * s.radius);
    }

    /** The stones in the air: rising, flying to the gathering, turning in it, falling in on its landing; or just falling. */
    private static void fly(Provings.Run run, Gather g, long now) {
        if (g.stones.isEmpty()) return;
        Vec3 c = Vec3.atLowerCornerOf(run.centre).add(0.5, 0, 0.5);
        Vec3 gather = c.add(0, GATHER_Y, GATHER_Z), land = c.add(0, 3, Span.LAND_Z);
        int n = 0;
        for (Iterator<Stone> it = g.stones.iterator(); it.hasNext(); n++) {
            Stone s = it.next();
            if (s.d.isRemoved()) {
                it.remove();
                continue;
            }
            long t = now - s.born;
            Vec3 to;
            if (s.falling) {
                if (t > 50) {
                    s.d.discard();
                    it.remove();
                    continue;
                }
                s.vel = s.vel.add(0, -0.06, 0).scale(0.98);
                to = s.at.add(s.vel);
            } else {
                Vec3 up = s.from.add(0, Math.min(t, RISE) * 0.12, 0);
                if (t < RISE) {
                    to = up;
                } else {
                    double u = Math.min(1, (t - RISE) / (double) FLY);
                    double e = u * u * (3 - 2 * u);
                    to = up.lerp(slot(s, gather, now), e).add(0, Math.sin(u * Math.PI) * 3, 0);
                }
                if (g.collapseAt >= 0) {
                    double u = Math.min(1, (now - g.collapseAt) / (double) COLLAPSE);
                    to = to.lerp(land, u * u);
                }
            }
            s.at = to;
            // every other tick each (half of them a tick): the display's own interpolation fills the gap
            if (s.falling || ((n + now) & 1) == 0) s.d.setPos(to.x, to.y, to.z);
        }
    }

    private static void clearStones(Gather g) {
        for (Stone s : g.stones) s.d.discard();
        g.stones.clear();
    }

    /** It drops out of the gathered stones once they've fallen in on the bastion's rim. */
    @Override
    boolean comes(Provings.Run run, List<ServerPlayer> here, long now) {
        Gather g = gather(run);
        return g.collapseAt >= 0 && now >= g.collapseAt + COLLAPSE;
    }

    /** On the bastion's north rim, where the arm joined it, facing the door. */
    @Override
    Vec3 arrive(Provings.Run run) {
        return new Vec3(run.centre.getX() + 0.5, run.centre.getY() + 1, run.centre.getZ() + Span.LAND_Z + 0.5);
    }

    @Override
    float arriveYaw(Provings.Run run) {
        return 0F;
    }

    @Override
    int home() {
        return HOME;
    }

    /** It lands: the stones are its body now; dust and broken stone burst off the bastion. */
    @Override
    void came(Provings.Run run, LivingEntity warden, List<ServerPlayer> here) {
        clearStones(gather(run));
        double x = warden.getX(), y = warden.getY(), z = warden.getZ();
        run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), x, y + 1, z, 90, 3, 1.5, 3, 0.15);
        run.level.sendParticles(ParticleTypes.DUST_PLUME, x, y + 0.3, z, 40, 4, 0.3, 4, 0.05);
        run.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.5, z, 14, 3, 0.4, 3, 0.01);
        BlockPos at = warden.blockPosition();
        run.level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4F, 0.4F);
        run.level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3F, 0.6F);
        run.level.playSound(null, at, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.HOSTILE, 4F, 0.5F);
    }

    // ---- the hall turning ---------------------------------------------------------------------------------------------

    /** Its second phase (at two thirds): the ring cracks and gives way. Its third (a third): the lamps go out. */
    @Override
    void fight(Provings.Run run, LivingEntity warden, long now) {
        float f = warden.getHealth() / warden.getMaxHealth();
        if (run.turns == 0 && f <= GIVE_AT) {
            run.turns = 1;
            run.turnAt = now;
            run.turnRow = 0;
            crack(run);
        }
        if (run.turns == 1 && f <= DARK_AT) {
            if (run.turnAt >= 0) {
                // a blow big enough to skip the ring's fall finishes it first
                while (run.turnRow < SECTORS) fall(run, SECTOR_ORDER[run.turnRow++], now);
            }
            run.turns = 2;
            run.turnAt = now;
            run.turnRow = 0;
        }
    }

    @Override
    void step(Provings.Run run, long now) {
        Gather g = gather(run);
        fly(run, g, now);
        overTheEdge(run);
        if (run.turnAt < 0) return;
        long k = now - run.turnAt;
        if (run.turns == 1) {
            if (k < CRACK) {
                if (k % 5 == 0) crumble(run);
                return;
            }
            if ((k - CRACK) % SECTOR_TICKS != 0) return;
            fall(run, SECTOR_ORDER[run.turnRow++], now);
            if (run.turnRow >= SECTORS) run.turnAt = -1;
        } else if (run.turns == 2) {
            if (k % LAMP_TICKS != 0) return;
            int[][][] steps = lampSteps();
            int s = run.turnRow++;
            if (s < steps.length) {
                lampsOut(run, steps[s]);
            } else {
                dim(run);
                run.turnAt = -1;
            }
        }
    }

    /** Whoever goes over the edge has fallen; if it goes over, it climbs back onto the bastion. */
    private static void overTheEdge(Provings.Run run) {
        if (run.stage == Provings.Stage.OPENING) return;
        for (UUID u : List.copyOf(run.members)) {
            ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
            if (m == null || m.level() != run.level || m.isSpectator() || m.getY() >= run.centre.getY() + VOID) continue;
            Trials.Session s = Trials.SESSIONS.get(u);
            if (s != null) Provings.fell(m, s, run.level.damageSources().fellOutOfWorld());
        }
        LivingEntity w = Provings.warden(run);
        if (w != null && w.getY() < run.centre.getY() - 2) {
            double lx = w.getX() - run.centre.getX() - 0.5, lz = w.getZ() - run.centre.getZ() - 0.5;
            double r = Math.max(1, Math.sqrt(lx * lx + lz * lz)), in = Math.min(r, 8);
            w.teleportTo(run.centre.getX() + 0.5 + lx / r * in, run.centre.getY() + 1, run.centre.getZ() + 0.5 + lz / r * in);
            w.setDeltaMovement(Vec3.ZERO);
            w.resetFallDistance();
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), w.getX(), w.getY() + 1, w.getZ(),
                    40, 1.5, 1, 1.5, 0.1);
            run.level.playSound(null, w.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3F, 0.5F);
        }
    }

    /** The ring's floor cracks all at once: what's past the tuff seam is going. */
    private static void crack(Provings.Run run) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        for (int[] p : ringFloor()) {
            at.set(run.centre.getX() + p[0], run.centre.getY(), run.centre.getZ() + p[1]);
            if (!run.level.getBlockState(at).isAir()) run.level.setBlock(at, cracked, QUIET);
        }
        run.level.playSound(null, run.centre.above(2), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 4F, 0.5F);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            BlockPos p = run.centre.offset((int) (15 * Math.cos(a)), 1, (int) (15 * Math.sin(a)));
            run.level.playSound(null, p, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 3F, 0.5F + 0.05F * k);
        }
    }

    /** While it cracks: dust falls from under the ring and the stones grind. */
    private static void crumble(Provings.Run run) {
        RandomSource rnd = run.level.getRandom();
        for (int k = 0; k < 3; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = Span.FALL_R + rnd.nextDouble() * (Span.BASTION_R - Span.FALL_R);
            double x = run.centre.getX() + 0.5 + Math.cos(a) * r, z = run.centre.getZ() + 0.5 + Math.sin(a) * r;
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE_BRICKS.defaultBlockState()), x, run.centre.getY() - 0.2, z,
                    6, 1.2, 0.1, 1.2, 0);
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRACKED_STONE_BRICKS.defaultBlockState()), x, run.centre.getY() + 1.1, z,
                    4, 1, 0.05, 1, 0.02);
        }
        run.level.playSound(null, run.centre.offset(0, 0, 0), SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS, 3F, 0.4F + rnd.nextFloat() * 0.2F);
    }

    /** A sector of the ring goes: its blocks fall away into the dark, a few of them as stones. */
    private static void fall(Provings.Run run, int sector, long now) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        RandomSource rnd = run.level.getRandom();
        Gather g = gather(run);
        for (int[] p : sectorSpots()[sector]) {
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + p[2]);
            BlockState s = run.level.getBlockState(at);
            if (s.isAir()) continue;
            if (p[1] >= -1 && rnd.nextInt(8) == 0 && !s.is(Blocks.LIGHT)) {
                Vec3 from = Vec3.atCenterOf(at);
                Display.BlockDisplay d = stone(run.level, s, from, 1.0F, rnd.nextFloat() * 0.6F);
                if (d != null) g.stones.add(new Stone(d, from, now, rnd, true,
                        new Vec3((rnd.nextDouble() - 0.5) * 0.08, -0.02 - rnd.nextDouble() * 0.05, (rnd.nextDouble() - 0.5) * 0.08)));
            }
            run.level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
        }
        double a = sectorAngle(sector);
        double x = run.centre.getX() + 0.5 + Math.cos(a) * 15, z = run.centre.getZ() + 0.5 + Math.sin(a) * 15;
        run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), x, run.centre.getY() + 0.5, z,
                40, 3, 1, 3, 0.1);
        run.level.sendParticles(ParticleTypes.DUST_PLUME, x, run.centre.getY() + 0.2, z, 20, 3, 0.3, 3, 0.05);
        BlockPos p = BlockPos.containing(x, run.centre.getY(), z);
        run.level.playSound(null, p, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.5F, 0.5F);
        run.level.playSound(null, p, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 4F, 0.45F);
    }

    /** The eighth of the ring a spot is in: 0 the north, counted round through the east. */
    static int sector(int x, int z) {
        double a = Math.atan2(z, x) + Math.PI / 2;
        return Math.floorMod((int) Math.floor((a + Math.PI / 8) / (Math.PI / 4)), SECTORS);
    }

    private static double sectorAngle(int sector) {
        return sector * Math.PI / 4 - Math.PI / 2;
    }

    private static int[][][] SECTOR_SPOTS;
    private static int[][] RING_FLOOR;

    /** Each sector's blocks that fall, {x, dy, z} (built once). */
    private static int[][][] sectorSpots() {
        if (SECTOR_SPOTS == null) {
            List<List<int[]>> by = new ArrayList<>();
            for (int s = 0; s < SECTORS; s++) by.add(new ArrayList<>());
            int r = (int) Math.ceil(Span.BASTION_R) + 1;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (!Span.falls(x, z)) continue;
                    for (int dy = LIFT_LO; dy <= LIFT_HI; dy++) if (Span.block(x, dy, z) != null) by.get(sector(x, z)).add(new int[]{x, dy, z});
                }
            }
            int[][][] out = new int[SECTORS][][];
            for (int s = 0; s < SECTORS; s++) out[s] = by.get(s).toArray(new int[0][]);
            SECTOR_SPOTS = out;
        }
        return SECTOR_SPOTS;
    }

    /** The ring's floor, {x, z} (built once). */
    private static int[][] ringFloor() {
        if (RING_FLOOR == null) {
            List<int[]> out = new ArrayList<>();
            int r = (int) Math.ceil(Span.BASTION_R) + 1;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (Span.falls(x, z) && Span.block(x, 0, z) != null) out.add(new int[]{x, z});
            RING_FLOOR = out.toArray(new int[0][]);
        }
        return RING_FLOOR;
    }

    private static int[][][] LAMP_STEPS;

    /** The south arm's lamps and lights, a step for each z that has any, from the gatehouse in (built once). */
    private static int[][][] lampSteps() {
        if (LAMP_STEPS == null) {
            List<int[][]> steps = new ArrayList<>();
            for (int z = Span.GATE_N - 1; z >= (int) Span.BASTION_R; z--) {
                List<int[]> here = new ArrayList<>();
                for (int x = -Span.RAIL; x <= Span.RAIL; x++) {
                    for (int dy = 3; dy <= 4; dy++) {
                        BlockState s = Span.block(x, dy, z);
                        if (s != null && Span.lamp(s)) here.add(new int[]{x, dy, z});
                    }
                }
                if (!here.isEmpty()) steps.add(here.toArray(new int[0][]));
            }
            LAMP_STEPS = steps.toArray(new int[0][][]);
        }
        return LAMP_STEPS;
    }

    /** A post's lamps go out (and the unseen light by it). */
    private static void lampsOut(Provings.Run run, int[][] spots) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int[] p : spots) {
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + p[2]);
            BlockState s = run.level.getBlockState(at);
            if (!Span.lamp(s)) continue;
            run.level.setBlock(at, Blocks.AIR.defaultBlockState(), QUIET);
            if (s.is(Blocks.LANTERN)) {
                run.level.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.15, 0.2, 0.15, 0.01);
                run.level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.5F, 0.7F);
            }
        }
    }

    /** The last: the bastion's own light falls low. The Unmade's lamp and the stars still show. */
    private static void dim(Provings.Run run) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockState low = Span.lightOf(Span.DARK);
        int r = (int) Math.ceil(Span.BASTION_R);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                at.set(run.centre.getX() + x, run.centre.getY() + 3, run.centre.getZ() + z);
                if (run.level.getBlockState(at).is(Blocks.LIGHT)) run.level.setBlock(at, low, QUIET);
            }
        }
        run.level.playSound(null, run.centre.above(3), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3F, 0.4F);
    }

    /** Everyone fell: the stones go, the Span is whole again (its north arm, its ring and its lamps), and it can gather. */
    @Override
    void reset(Provings.Run run) {
        Gather g = gather(run);
        clearStones(g);
        new Hall.Repair(Span.HALL, run.level, run.centre, false).finish();
        run.room = new Gather();
        run.turns = 0;
        run.turnAt = -1;
    }

    @Override
    void died(Provings.Run run, long now) {
        run.turnAt = -1; // what was still turning stops where it is
        Entity w = run.boss != null ? run.level.getEntity(run.boss) : null;
        BlockPos at = w != null ? w.blockPosition() : run.centre.above();
        run.level.playSound(null, at, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.HOSTILE, 4F, 0.4F);
        run.level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.5F);
    }

    /** After its death: the stones settle, the Span mends from its far end and its lamps light again. */
    @Override
    void outro(Provings.Run run, List<ServerPlayer> here, long t) {
        Gather g = gather(run);
        if (t == 20) {
            for (ServerPlayer m : here) {
                if (m.level() == run.level) run.level.playSound(null, m.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
            }
        }
        if (t == 40) g.mend = new Hall.Repair(Span.HALL, run.level, run.centre, false);
        if (g.mend != null && !g.mend.done()) {
            g.mend.step(LIFT_ROWS);
            if (t % 6 == 0) {
                int z = (int) Math.min(Span.BACK, Span.NORTH_END + (t - 40) * LIFT_ROWS);
                run.level.playSound(null, run.centre.offset(0, 1, z), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 2.5F, 0.6F);
            }
        }
    }

    /** Through the gatehouse's arch: gone out. */
    @Override
    boolean out(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
        return lz >= Span.OUT_Z && Math.abs(lx) <= Span.DECK + 1 && m.getY() >= run.centre.getY() + 0.5;
    }

    @Override
    String firstFall() {
        return "You wake at the south end of the bridge. The arch behind you is the way out.";
    }

    @Override
    String name() {
        return "The Unmade";
    }
}
