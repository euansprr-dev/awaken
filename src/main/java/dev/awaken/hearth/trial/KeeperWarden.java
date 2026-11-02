package dev.awaken.hearth.trial;

import dev.awaken.core.api.Promises;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The fifth Proving's Warden (SPEC §47.9): the Keeper's Shadow, for S. The bell won't stop, and you wake at the top of a
 * stair going down into a round court of black stone: eight shut doors round it, each with a coloured light in its
 * keyhole, and the Court's own door, banded in gold, at its far side. The braziers light themselves one by one. Walk
 * into the court and your shadow spreads into a pool in front of you, and the Keeper's Shadow stands up out of it: the
 * last of the Keepers who went down to hold the Seed, the one left at the door, still testing whoever comes. At three
 * quarters of its health the eight doors sink into the floor; at half the dark comes in through them over the marble as
 * far as the inner ring of gold, and holds the feet of whoever stands on it; from a quarter the Court's door slides open a
 * little with each blow, and its light burns the dark back. Its death opens the door the rest of the way, and the dark
 * drains away: the Court's doors are yours.
 */
final class KeeperWarden extends Warden {
    static final KeeperWarden WARDEN = new KeeperWarden();
    static final String MOB = "awaken:proving_keepers_shadow";
    /** The braziers light one at a time, this long apart, from this long after you wake (the first time). */
    static final int LIGHT_FROM = 30, LIGHT_EVERY = 8;
    /** It comes once someone walks this far into the court (or after Provings.COME_AFTER). */
    static final double COME_R = 15;
    /** Your shadow spreads into a pool this far in front of you, this long before it stands up out of it. */
    static final double AHEAD = 4.5;
    static final int POOL = 40;
    /** Its phases: the doors sink at three quarters, the dark comes in at half, the Court's door opens from a quarter. */
    static final float DOORS_AT = 0.75F, DARK_AT = 0.5F, LAST_AT = 0.25F;
    /** The doors grind this long, then sink a row every few ticks; the dark comes a band every few ticks. */
    static final int GRIND = 30, SINK_EVERY = 4, SINK_ROWS = 8, DARK_EVERY = 3;
    /** On the dark your feet are held (slowed), checked this often. */
    static final int HOLD_EVERY = 10;
    /** The Court's door's steps open: three in the last quarter, the fourth at its death. */
    static final int DOOR_STEPS = 4;
    /** The hall is put back this many rows a tick after a reset; the dark drains a band every few ticks after its death. */
    static final int RESET_ROWS = 16, DRAIN_EVERY = 2;
    /** Under this you've gone over the stair's wall: fallen. */
    static final int VOID = -10;
    /** How far from the middle it goes after you: the court, its doors and the foot of the stair. */
    static final int HOME = 30;

    private KeeperWarden() {
        super(MOB, Rank.S, Threshold.HALL);
    }

    @Override
    String asked() {
        return "The bell has taken you four times now. It rings lower for this one. Go when you're ready.";
    }

    @Override
    int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.4F);
        if (practice) return 40;
        dev.awaken.fault.Fault f = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.FAULT, "",
                List.of("MEASURING · MEASURING · MEASURING", "[ERROR] THE FLOOR REMEMBERS YOU"), "",
                "The bell stopped.", null, null, 0, true);
        dev.awaken.fault.Faults.play(m, f);
        return f.length();
    }

    /** What a run keeps of the court: the braziers lit, your shadow's pool, the doors, the dark and the Court's door. */
    static final class Court {
        int lit;
        long poolAt = -1;
        Vec3 pool = Vec3.ZERO;
        float poolYaw;
        /** The doors: when they began to grind, how many rows they've sunk. */
        long sinkAt = -1;
        int sunk;
        /** The dark: when it was let in, how many bands have come. */
        long darkAt = -1;
        int dark;
        /** The Court's door: steps open, and the light's lane burned back to it. */
        int door;
        /** After its death, the dark draining back out (bands left), and a reset putting the hall back. */
        long drainAt = -1;
        int drained;
        Hall.@Nullable Repair reset;
    }

    @Override
    void before(Provings.Run run, ServerPlayer leader) {
        run.room = new Court();
    }

    static Court court(Provings.Run run) {
        if (run.room instanceof Court c) return c;
        Court c = new Court();
        run.room = c;
        return c;
    }

    /** On the landing, looking down the stair into the court. */
    @Override
    BlockPos wakeAt(BlockPos centre) {
        return centre.offset(0, Threshold.WAKE_DY, Threshold.WAKE_Z);
    }

    /** On the landing (a party side by side across it), looking down into the court; the first time its braziers are out. */
    @Override
    void wake(ServerPlayer m, Provings.Run run, boolean first) {
        int i = Math.max(0, List.copyOf(run.members).indexOf(m.getUUID())), row = Math.min(5, run.members.size());
        Vec3 at = new Vec3(run.centre.getX() + 0.5 + (i % 5 - (row - 1) / 2.0) * 0.9, run.centre.getY() + Threshold.WAKE_DY,
                run.centre.getZ() + Threshold.WAKE_Z + 0.5 + (i / 5) * 1.5);
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, first ? 16F : 6F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, first ? 0.45F : 0.6F);
        if (first && court(run).lit == 0) {
            for (int[] b : Threshold.BRAZIERS) run.level.setBlock(run.centre.offset(b[0], 3, b[1]), Threshold.fire(false), Threshold.QUIET);
        }
    }

    // ---- before it comes ------------------------------------------------------------------------------------------------

    /**
     * The first time the braziers light themselves one by one, from the Court's door round to the stair. Then, once someone
     * walks into the court (or after a while), their shadow spreads into a pool in front of them.
     */
    @Override
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {
        Court c = court(run);
        if (c.reset != null) return; // the hall still being put back
        if (c.lit < Threshold.BRAZIERS.length) {
            if (now >= run.waitingSince + LIGHT_FROM + (long) c.lit * LIGHT_EVERY) light(run, c.lit++);
            return;
        }
        if (c.poolAt < 0) {
            ServerPlayer who = null;
            for (ServerPlayer m : here) {
                if (m.level() != run.level || m.isSpectator()) continue;
                double dx = m.getX() - run.centre.getX() - 0.5, dz = m.getZ() - run.centre.getZ() - 0.5;
                if (Math.sqrt(dx * dx + dz * dz) < COME_R && m.getY() < run.centre.getY() + 4) who = m;
            }
            if (who == null && now - run.waitingSince >= Provings.COME_AFTER) {
                for (ServerPlayer m : here) if (m.level() == run.level && !m.isSpectator()) who = m;
            }
            if (who != null) pool(run, c, who, now);
            return;
        }
        // the pool spreading and darkening, something under it
        double k = Math.min(1, (now - c.poolAt) / (double) POOL);
        double rad = 0.4 + 1.6 * k;
        run.level.sendParticles(ParticleTypes.SQUID_INK, c.pool.x, c.pool.y + 0.05, c.pool.z, 3 + (int) (8 * k), rad * 0.6, 0.02, rad * 0.6, 0.01);
        run.level.sendParticles(ParticleTypes.SCULK_SOUL, c.pool.x, c.pool.y + 0.1, c.pool.z, 1 + (int) (3 * k), rad * 0.5, 0.05, rad * 0.5, 0.02);
        if (k > 0.5) run.level.sendParticles(ParticleTypes.LARGE_SMOKE, c.pool.x, c.pool.y + 0.2, c.pool.z, 2, rad * 0.4, 0.1, rad * 0.4, 0.01);
        if ((now - c.poolAt) % 12 == 0) run.level.playSound(null, BlockPos.containing(c.pool), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2F, 0.6F);
    }

    /** A brazier lights itself: soul fire, with a breath. */
    private static void light(Provings.Run run, int i) {
        int[] b = Threshold.BRAZIERS[LIGHT_ORDER[i]];
        BlockPos at = run.centre.offset(b[0], 3, b[1]);
        run.level.setBlock(at, Threshold.fire(true), Threshold.QUIET);
        run.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 14, 0.2, 0.3, 0.2, 0.04);
        run.level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.4F, 0.55F + i * 0.05F);
        run.level.playSound(null, at, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 2F, 0.7F);
    }

    /** From the Court's door's side round to the stair's. */
    private static final int[] LIGHT_ORDER = {4, 5, 3, 0, 2, 1};

    /** Your shadow spreads into a pool in front of you (inside the court's star), and it's coming up out of it. */
    private static void pool(Provings.Run run, Court c, ServerPlayer who, long now) {
        Vec3 look = who.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, -1);
        flat = flat.normalize();
        double ox = run.centre.getX() + 0.5, oz = run.centre.getZ() + 0.5;
        double x = who.getX() + flat.x * AHEAD - ox, z = who.getZ() + flat.z * AHEAD - oz, r = Math.sqrt(x * x + z * z);
        if (r > 14) {
            x *= 14 / r;
            z *= 14 / r;
        }
        c.pool = new Vec3(ox + x, run.centre.getY() + 1, oz + z);
        c.poolYaw = (float) Math.toDegrees(Math.atan2(-(who.getX() - c.pool.x), who.getZ() - c.pool.z));
        c.poolAt = now;
        BlockPos at = BlockPos.containing(c.pool);
        run.level.playSound(null, at, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.5F, 0.7F);
        run.level.playSound(null, at, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.HOSTILE, 2F, 0.5F);
        for (int[] b : Threshold.BRAZIERS) {
            BlockPos f = run.centre.offset(b[0], 3, b[1]);
            run.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, f.getX() + 0.5, f.getY() + 0.5, f.getZ() + 0.5, 6, 0.1, 0.2, 0.1, 0.01);
        }
    }

    @Override
    boolean comes(Provings.Run run, List<ServerPlayer> here, long now) {
        Court c = court(run);
        return c.poolAt >= 0 && now >= c.poolAt + POOL;
    }

    @Override
    Vec3 arrive(Provings.Run run) {
        return court(run).pool;
    }

    @Override
    float arriveYaw(Provings.Run run) {
        return court(run).poolYaw;
    }

    @Override
    int home() {
        return HOME;
    }

    /** It stands up out of the pool: the dark bursts off it, the braziers bow, the floor rings. */
    @Override
    void came(Provings.Run run, LivingEntity warden, List<ServerPlayer> here) {
        court(run).poolAt = -1;
        double x = warden.getX(), y = warden.getY(), z = warden.getZ();
        run.level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.2, z, 60, 1.6, 0.1, 1.6, 0.06);
        run.level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.5, z, 30, 1, 1.5, 1, 0.03);
        run.level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1, z, 20, 1.2, 1, 1.2, 0.04);
        BlockPos at = warden.blockPosition();
        run.level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3F, 0.4F);
        run.level.playSound(null, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2.5F, 0.5F);
        for (int[] b : Threshold.BRAZIERS) {
            BlockPos f = run.centre.offset(b[0], 3, b[1]);
            run.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, f.getX() + 0.5, f.getY() + 0.6, f.getZ() + 0.5, 10, 0.15, 0.4, 0.15, 0.05);
        }
    }

    // ---- the hall turning ---------------------------------------------------------------------------------------------

    /** At three quarters the doors sink; at half the dark comes in; from a quarter the Court's door opens a step with each blow. */
    @Override
    void fight(Provings.Run run, LivingEntity warden, long now) {
        Court c = court(run);
        float f = warden.getHealth() / warden.getMaxHealth();
        if (run.turns == 0 && f <= DOORS_AT) {
            run.turns = 1;
            c.sinkAt = now;
            for (int k = 0; k < 8; k++) {
                BlockPos at = doorAt(run, k, 4);
                run.level.playSound(null, at, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 2F, 0.4F);
            }
            run.level.playSound(null, run.centre.above(8), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 3F, 0.4F);
        }
        if (run.turns == 1 && f <= DARK_AT) {
            run.turns = 2;
            c.darkAt = now;
            run.level.playSound(null, run.centre.above(4), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 3F, 0.5F);
            run.level.playSound(null, run.centre.above(4), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3F, 0.5F);
        }
        if (run.turns == 2 && f <= LAST_AT) run.turns = 3;
        if (run.turns == 3) {
            int want = Math.max(1, Math.min(DOOR_STEPS - 1, 1 + (int) ((LAST_AT - f) / LAST_AT * (DOOR_STEPS - 1))));
            if (want > c.door) openTo(run, c, want);
        }
    }

    @Override
    void step(Provings.Run run, long now) {
        Court c = court(run);
        if (c.reset != null && c.reset.step(RESET_ROWS)) c.reset = null;
        // the doors grinding, then sinking a row at a time; their Sovereigns' lights ring out when they're down
        if (c.sinkAt >= 0 && c.sunk < SINK_ROWS) {
            long k = now - c.sinkAt;
            if (k < GRIND) {
                if (k % 6 == 0) grind(run);
            } else if ((k - GRIND) % SINK_EVERY == 0) {
                sink(run, ++c.sunk);
            }
        }
        // the dark comes in once the doors are down, a band at a time
        int[][][] bands = Threshold.darkBands();
        if (c.darkAt >= 0 && c.sunk >= SINK_ROWS && c.dark < bands.length && c.drainAt < 0 && (now - c.darkAt) % DARK_EVERY == 0) {
            spread(run, c, bands[c.dark++]);
        }
        if (c.drainAt >= 0) drain(run, c, now);
        if (run.stage == Provings.Stage.FIGHT) {
            if (c.dark > 0 && now % HOLD_EVERY == 0) hold(run);
            if (c.door > 0 && now % 4 == 0) lightThrough(run, c);
            overTheEdge(run);
        }
    }

    /** The doors: dust sifting from their heads, the stone grinding. */
    private static void grind(Provings.Run run) {
        RandomSource rnd = run.level.getRandom();
        for (int k = 0; k < 8; k++) {
            BlockPos at = doorAt(run, k, 8);
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DEEPSLATE.defaultBlockState()), at.getX() + 0.5, at.getY() + 0.5,
                    at.getZ() + 0.5, 4, 1.0, 0.1, 1.0, 0);
        }
        run.level.playSound(null, doorAt(run, rnd.nextInt(8), 4), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 2F, 0.35F + rnd.nextFloat() * 0.1F);
    }

    /** Where door k's opening is, {@code dy} up (its middle, in the wall's second course). */
    static BlockPos doorAt(Provings.Run run, int k, int dy) {
        double a = Math.toRadians(22.5 + 45 * k), u = Threshold.R + 1;
        return run.centre.offset((int) Math.round(u * Math.cos(a)), dy, (int) Math.round(u * Math.sin(a)));
    }

    /** The eight Sovereigns' notes, rung as their doors come down. */
    private static final float[] NOTES = {0.5F, 0.56F, 0.63F, 0.67F, 0.75F, 0.84F, 0.94F, 1.0F};

    /** The doors' leaves sink {@code s} rows into the floor. */
    private static void sink(Provings.Run run, int s) {
        int[][][] leaves = Threshold.leaves();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int k = 0; k < 8; k++) {
            for (int[] p : leaves[k]) {
                double av = p[3] / 10.0;
                int from = p[1] + s;
                BlockState want = from <= Threshold.doorTop(av) ? Threshold.leaf(k, av, from) : Blocks.AIR.defaultBlockState();
                at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + p[2]);
                run.level.setBlock(at, want, Threshold.QUIET);
            }
            BlockPos d = doorAt(run, k, 1);
            run.level.sendParticles(ParticleTypes.DUST_PLUME, d.getX() + 0.5, d.getY() + 0.2, d.getZ() + 0.5, 4, 1.2, 0.1, 1.2, 0.02);
            if (s == SINK_ROWS) run.level.playSound(null, doorAt(run, k, 4), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.5F, NOTES[k]);
        }
        if (s % 2 == 0) run.level.playSound(null, run.centre.above(3), SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 3F, 0.4F);
    }

    /** Whether a floor spot lies in the light's lane from the Court's door, {@code door} steps open. */
    static boolean lane(int x, int z, int door) {
        if (door <= 0) return false;
        return Math.abs(x) <= Math.min(3, door) && z < 0 && z <= -(Threshold.R - 6 * door) && Threshold.r(x, z) >= Threshold.LIGHT_R;
    }

    /** A band of the dark comes in over the floor (not where the Court's door's light lies). */
    private static void spread(Provings.Run run, Court c, int[][] band) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        RandomSource rnd = run.level.getRandom();
        int sounded = 0;
        for (int[] p : band) {
            if (lane(p[0], p[2], c.door)) continue;
            at.set(run.centre.getX() + p[0], run.centre.getY(), run.centre.getZ() + p[2]);
            run.level.setBlock(at, Threshold.DARK, Threshold.QUIET);
            if (rnd.nextInt(14) == 0) run.level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 1, 0.2, 0, 0.2, 0.02);
            if (sounded < 3 && rnd.nextInt(Math.max(1, band.length / 3)) == 0) {
                run.level.playSound(null, at, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 2F, 0.6F + rnd.nextFloat() * 0.3F);
                sounded++;
            }
        }
    }

    /** Whoever stands on the dark is held by it: their feet slow. */
    private static void hold(Provings.Run run) {
        for (UUID u : run.members) {
            ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
            if (m == null || m.level() != run.level || m.isSpectator() || !m.onGround()) continue;
            if (!run.level.getBlockState(m.getOnPos()).is(Blocks.SCULK)) continue;
            m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HOLD_EVERY + 6, 0, true, false, true));
            run.level.sendParticles(ParticleTypes.SCULK_SOUL, m.getX(), m.getY() + 0.1, m.getZ(), 2, 0.3, 0.05, 0.3, 0.01);
        }
    }

    /** The Court's door slides open to {@code s} steps; its light burns the dark back off a lane to the inner ring. */
    private static void openTo(Provings.Run run, Court c, int s) {
        c.door = s;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int z : new int[]{Threshold.LEAF_S, Threshold.LEAF_N}) {
            for (int x = -Threshold.GD_HALF; x <= Threshold.GD_HALF; x++) {
                for (int dy = 1; dy <= Threshold.gdTop(Math.abs(x)); dy++) {
                    BlockState want = Threshold.gdLeaf(x, dy, s);
                    at.set(run.centre.getX() + x, run.centre.getY() + dy, run.centre.getZ() + z);
                    run.level.setBlock(at, want == null ? Blocks.AIR.defaultBlockState() : want, Threshold.QUIET);
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -Threshold.R; z < 0; z++) {
                if (!lane(x, z, s)) continue;
                at.set(run.centre.getX() + x, run.centre.getY(), run.centre.getZ() + z);
                if (run.level.getBlockState(at).is(Blocks.SCULK)) run.level.setBlock(at, Threshold.floor(x, z, Threshold.r(x, z)), Threshold.QUIET);
            }
        }
        BlockPos door = run.centre.offset(0, 6, Threshold.LEAF_S);
        run.level.playSound(null, door, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 3F, 0.35F);
        run.level.playSound(null, door, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 3F, 0.3F);
        run.level.playSound(null, door, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3F, 0.4F + s * 0.08F);
        run.level.sendParticles(ParticleTypes.END_ROD, door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 20 * s, 0.4 * s, 5, 0.3, 0.06);
        run.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GILDED_BLACKSTONE.defaultBlockState()), door.getX() + 0.5,
                door.getY() + 8, door.getZ() + 0.5, 30, 4, 0.2, 0.3, 0);
    }

    /** Light coming through the Court's door, down its lane into the court. */
    private static void lightThrough(Provings.Run run, Court c) {
        double w = Math.min(3, c.door) * 0.5;
        double z0 = run.centre.getZ() + Threshold.LEAF_S + 0.5;
        run.level.sendParticles(ParticleTypes.END_ROD, run.centre.getX() + 0.5, run.centre.getY() + 3, z0 + 3, 3 * c.door, w, 2.5, 2.5, 0.01);
    }

    /** Whoever goes over the stair's wall into the dark has fallen (not whoever slips through the Court's door and down its steps). */
    private static void overTheEdge(Provings.Run run) {
        for (UUID u : List.copyOf(run.members)) {
            ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
            if (m == null || m.level() != run.level || m.isSpectator() || m.getY() >= run.centre.getY() + VOID) continue;
            if (m.getZ() - run.centre.getZ() < Threshold.LEAF_N) continue;
            Trials.Session s = Trials.SESSIONS.get(u);
            if (s != null) Provings.fell(m, s, run.level.damageSources().fellOutOfWorld());
        }
    }

    /** Everyone fell: the doors rise, the dark goes, the Court's door shuts (quickly), and it can come again. */
    @Override
    void reset(Provings.Run run) {
        Court c = court(run);
        c.reset = new Hall.Repair(Threshold.HALL, run.level, run.centre, false);
        c.poolAt = -1;
        c.sinkAt = -1;
        c.sunk = 0;
        c.darkAt = -1;
        c.dark = 0;
        c.door = 0;
        run.turns = 0;
        run.turnAt = -1;
    }

    @Override
    void died(Provings.Run run, long now) {
        Entity w = run.boss != null ? run.level.getEntity(run.boss) : null;
        BlockPos at = w != null ? w.blockPosition() : run.centre.above();
        run.level.playSound(null, at, SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 2.5F, 0.6F);
        run.level.playSound(null, at, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3F, 0.5F);
        run.level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3F, 0.35F);
    }

    /**
     * After its death: the Court's door opens the rest of the way and its light pours in; the Court is set before you
     * (the rank, given at the end, opens it); the dark drains back out through the doors.
     */
    @Override
    void outro(Provings.Run run, List<ServerPlayer> here, long t) {
        Court c = court(run);
        if (t == 10) {
            openTo(run, c, DOOR_STEPS);
            BlockPos door = run.centre.offset(0, 6, Threshold.LEAF_S);
            run.level.playSound(null, door, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 4F, 0.5F);
            run.level.playSound(null, door, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3F, 0.6F);
        }
        if (t > 10 && t % 3 == 0) lightThrough(run, c);
        if (t == 40 && !run.practice) {
            for (ServerPlayer m : here) {
                // the same lock the Descent's sealed way down makes; the rank given at the end answers it
                if (m.level() == run.level) Promises.seen(m, "stratum:9", null, Promises.Need.rank(Rank.S.label), "The Hollow Court, in the Descent");
            }
        }
        if (t == 50) {
            c.drainAt = run.level.getGameTime();
            c.drained = c.dark;
        }
    }

    /** The dark drains away, the last band in first, back out through the doors. */
    private static void drain(Provings.Run run, Court c, long now) {
        if (c.drained <= 0 || (now - c.drainAt) % DRAIN_EVERY != 0) return;
        int[][] band = Threshold.darkBands()[--c.drained];
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int[] p : band) {
            at.set(run.centre.getX() + p[0], run.centre.getY(), run.centre.getZ() + p[2]);
            BlockState want = Threshold.block(p[0], 0, p[2]);
            if (want != null && run.level.getBlockState(at).is(Blocks.SCULK)) run.level.setBlock(at, want, Threshold.QUIET);
        }
        if (c.drained % 3 == 0) run.level.playSound(null, run.centre.above(2), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.5F, 0.5F + c.drained / 30F);
    }

    /** Up the stair and through the arch behind the landing; or, once it's open, down the Court's steps. */
    @Override
    boolean out(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
        if (lz >= Threshold.OUT_Z && Math.abs(lx) <= 2.5 && m.getY() >= run.centre.getY() + Threshold.LANDING + 0.5) return true;
        return court(run).door >= DOOR_STEPS && lz <= Threshold.OUT_N && Math.abs(lx) <= 4.5;
    }

    @Override
    String firstFall() {
        return "You wake at the top of the stair. The arch behind you is the way out.";
    }

    @Override
    String name() {
        return "The Keeper's Shadow";
    }
}
